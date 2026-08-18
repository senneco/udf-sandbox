package com.shmakov.udf.composable.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shmakov.udf.ModalExitCompletion
import com.shmakov.udf.ModalExitToken
import com.shmakov.udf.ModalPresentationPlanner
import com.shmakov.udf.ModalPresentationState
import com.shmakov.udf.NavigationContentMotion
import com.shmakov.udf.NavigationPresentationPlanner
import com.shmakov.udf.NavigationRenderTarget
import com.shmakov.udf.PresentedModalLayer
import com.shmakov.udf.renderIdentity
import com.shmakov.udf.navigation.ContentSlotId
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.ModalEntrance
import com.shmakov.udf.navigation.ModalScreenState
import com.shmakov.udf.navigation.NavAction

/** Renders one atomically projected navigation revision. */
@Composable
internal fun AnimatedNavigation(
    renderTarget: NavigationRenderTarget,
    onNavigationAction: (NavAction) -> Unit,
    destinationCatalog: DestinationCatalog = DemoDestinationCatalog,
) {
    val boundTree = when (
        val binding = DestinationTreeBinder.bind(renderTarget.tree, destinationCatalog)
    ) {
        is DestinationTreeBindingResult.Success -> binding.tree
        is DestinationTreeBindingResult.Failure -> {
            DestinationBindingFailure(binding.problem)
            return
        }
    }
    // This holder belongs only to the active renderer composition. It deliberately is not saveable
    // or store-owned: recreation starts at the current target without replaying process-local motion.
    val acceptedTargetHolder = remember { AcceptedNavigationTargetHolder() }
    val contentMotion = NavigationPresentationPlanner.contentMotion(
        previous = acceptedTargetHolder.target,
        target = renderTarget,
    )
    val targetState = BoundRenderState(
        renderTarget = renderTarget,
        tree = boundTree,
        contentMotion = contentMotion,
    )
    // Accept only a target whose complete destination tree composed successfully. A same-revision
    // layout reprojection then compares against this accepted target, not an animation's N-1 branch.
    SideEffect {
        acceptedTargetHolder.target = renderTarget
    }
    val transition = androidx.compose.animation.core.updateTransition(
        targetState = targetState,
        label = "NavigationRoot",
    )

    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { navigationContentTransform() },
        contentKey = { state -> state.tree.root.slot.renderIdentity },
    ) { branchState ->
        RenderNavigationBranch(
            branchState = branchState,
            onNavigationAction = onNavigationAction,
        )
    }
}

@Composable
private fun RenderNavigationBranch(
    branchState: BoundRenderState,
    onNavigationAction: (NavAction) -> Unit,
) {
    val desiredModalLayers = branchState.tree.modalLayers.map { layer -> layer.layer }
    val presentationHolder = remember {
        val initialState = ModalPresentationPlanner.start(
            navigationRevision = branchState.renderTarget.navigationRevision,
            desired = desiredModalLayers,
        )
        ModalPresentationHolder(
            AcceptedModalPresentation(
                state = initialState,
                layers = branchState.tree.modalLayers.map { layer ->
                    BoundPresentedModalLayer(
                        presentation = PresentedModalLayer.Desired(
                            layer = layer.layer,
                            entrance = ModalEntrance.Snap,
                        ),
                        screen = layer.screen,
                    )
                },
            ),
        )
    }
    val acceptedPresentation = presentationHolder.accepted
    val candidateState = ModalPresentationPlanner.reconcile(
        previous = acceptedPresentation.state,
        navigationRevision = branchState.renderTarget.navigationRevision,
        desired = desiredModalLayers,
    ).state
    val candidateLayers = when (
        val binding = DestinationTreeBinder.materializePresentedModalLayers(
            layers = candidateState.layers,
            desiredLayers = branchState.tree.modalLayers,
            acceptedLayers = acceptedPresentation.layers,
        )
    ) {
        is PresentedModalLayersBindingResult.Success -> binding.layers
        is PresentedModalLayersBindingResult.Failure -> {
            DestinationBindingFailure(binding.problem)
            return
        }
    }
    val candidatePresentation = AcceptedModalPresentation(
        state = candidateState,
        layers = candidateLayers,
    )

    SideEffect {
        presentationHolder.accept(candidatePresentation)
    }

    RenderContentSlot(
        branchState = branchState,
        contentSlot = branchState.tree.root,
        modalLayers = candidateLayers,
        onNavigationAction = onNavigationAction,
        onExitFinished = presentationHolder::completeExit,
    )
}

private data class AcceptedModalPresentation(
    val state: ModalPresentationState,
    val layers: List<BoundPresentedModalLayer>,
)

private class ModalPresentationHolder(
    initial: AcceptedModalPresentation,
) {
    var accepted: AcceptedModalPresentation by mutableStateOf(initial)
        private set

    fun accept(candidate: AcceptedModalPresentation) {
        if (accepted != candidate) {
            accepted = candidate
        }
    }

    fun completeExit(token: ModalExitToken) {
        val previous = accepted
        when (val completion = ModalPresentationPlanner.completeExit(previous.state, token)) {
            is ModalExitCompletion.Applied -> {
                val remainingLayers = previous.layers.filterNot { layer ->
                    val presentation = layer.presentation
                    presentation is PresentedModalLayer.Exiting &&
                        presentation.token == token
                }
                accepted = AcceptedModalPresentation(
                    state = completion.state,
                    layers = remainingLayers,
                )
            }

            is ModalExitCompletion.Unchanged -> Unit
        }
    }
}

private class AcceptedNavigationTargetHolder {
    var target: NavigationRenderTarget? = null
}

private class BoundRenderState(
    val renderTarget: NavigationRenderTarget,
    val tree: BoundNavigationRenderTree,
    val contentMotion: NavigationContentMotion,
) {
    override fun equals(other: Any?): Boolean =
        this === other || other is BoundRenderState && renderTarget == other.renderTarget

    override fun hashCode(): Int = renderTarget.hashCode()
}

@Composable
private fun RenderContentSlot(
    branchState: BoundRenderState,
    contentSlot: BoundContentSlot,
    modalLayers: List<BoundPresentedModalLayer>,
    onNavigationAction: (NavAction) -> Unit,
    onExitFinished: (ModalExitToken) -> Unit,
) {
    contentSlot.screen.Content(
        childContent = {
            RenderChildContent(
                branchState = branchState,
                ownerContentEntryId = contentSlot.slot.entry.id,
                modalLayers = modalLayers,
                onNavigationAction = onNavigationAction,
                onExitFinished = onExitFinished,
            )
        },
        onNavigationAction = onNavigationAction,
    )

    RenderModalLayers(
        modalLayers = modalLayers.filter { modalLayer ->
            modalLayer.presentation.layer.ownerContentEntryId == contentSlot.slot.entry.id
        },
        onNavigationAction = onNavigationAction,
        onExitFinished = onExitFinished,
    )
}

@Composable
private fun RenderChildContent(
    branchState: BoundRenderState,
    ownerContentEntryId: EntryId,
    modalLayers: List<BoundPresentedModalLayer>,
    onNavigationAction: (NavAction) -> Unit,
    onExitFinished: (ModalExitToken) -> Unit,
) {
    val transition = androidx.compose.animation.core.updateTransition(
        targetState = branchState,
        label = "NavigationChild:${ownerContentEntryId.value}",
    )

    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { navigationContentTransform() },
        contentKey = { state ->
            state.tree.childOf(ownerContentEntryId)?.slot?.renderIdentity
                ?: EmptyChildContentKey(ownerContentEntryId)
        },
    ) { childBranchState ->
        childBranchState.tree.childOf(ownerContentEntryId)?.let { childSlot ->
            RenderContentSlot(
                branchState = childBranchState,
                contentSlot = childSlot,
                modalLayers = modalLayers,
                onNavigationAction = onNavigationAction,
                onExitFinished = onExitFinished,
            )
        }
    }
}

private data class EmptyChildContentKey(
    val ownerContentEntryId: EntryId,
)

private fun BoundNavigationRenderTree.childOf(
    ownerContentEntryId: EntryId,
): BoundContentSlot? = nestedSlots.firstOrNull { contentSlot ->
    val slotId = contentSlot.slot.slotId
    slotId is ContentSlotId.ChildOf &&
        slotId.ownerContentEntryId == ownerContentEntryId
}

@Composable
private fun RenderModalLayers(
    modalLayers: List<BoundPresentedModalLayer>,
    onNavigationAction: (NavAction) -> Unit,
    onExitFinished: (ModalExitToken) -> Unit,
) {
    modalLayers.forEach { modalLayer ->
        val presentation = modalLayer.presentation
        val entryId = presentation.layer.entry.id
        val exitToken = (presentation as? PresentedModalLayer.Exiting)?.token
        key(entryId) {
            if (exitToken != null) {
                DisposableEffect(exitToken) {
                    onDispose {
                        onExitFinished(exitToken)
                    }
                }
            }
            modalLayer.screen.ModalContent(
                targetState = when (presentation) {
                    is PresentedModalLayer.Desired -> ModalScreenState.Shown
                    is PresentedModalLayer.Exiting -> ModalScreenState.Hidden
                },
                entrance = when (presentation) {
                    is PresentedModalLayer.Desired -> presentation.entrance
                    is PresentedModalLayer.Exiting -> ModalEntrance.Snap
                },
                onDismissRequest = {
                    if (presentation is PresentedModalLayer.Desired) {
                        onNavigationAction(NavAction.dismissModal(entryId))
                    }
                },
                onExitFinished = {
                    if (exitToken != null) {
                        onExitFinished(exitToken)
                    }
                },
                onNavigationAction = onNavigationAction,
            )
        }
    }
}

private fun AnimatedContentTransitionScope<BoundRenderState>.navigationContentTransform():
    ContentTransform = when (targetState.contentMotion) {
        NavigationContentMotion.None -> ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = ExitTransition.None,
        )

        NavigationContentMotion.Push -> ContentTransform(
            targetContentEnter = appPushEnterTransition,
            initialContentExit = appPushExitTransition,
        )

        NavigationContentMotion.Pop -> ContentTransform(
            targetContentEnter = appPopEnterTransition,
            initialContentExit = appPopExitTransition,
        )

        NavigationContentMotion.Replace -> ContentTransform(
            targetContentEnter = appReplaceEnterTransition,
            initialContentExit = appReplaceExitTransition,
        )
    }

private val AnimatedContentTransitionScope<*>.appPushEnterTransition: EnterTransition
    get() = slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(),
    )

private val AnimatedContentTransitionScope<*>.appPushExitTransition: ExitTransition
    get() = slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(),
    )

private val AnimatedContentTransitionScope<*>.appPopEnterTransition: EnterTransition
    get() = slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(),
    )

private val AnimatedContentTransitionScope<*>.appPopExitTransition: ExitTransition
    get() = slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(),
    )

private val appReplaceEnterTransition: EnterTransition
    get() = fadeIn(animationSpec = tween())

private val appReplaceExitTransition: ExitTransition
    get() = fadeOut(animationSpec = tween())
