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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.shmakov.udf.NavigationContentMotion
import com.shmakov.udf.NavigationPresentationPlanner
import com.shmakov.udf.NavigationRenderTarget
import com.shmakov.udf.renderIdentity
import com.shmakov.udf.navigation.ContentSlotId
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.ModalScreenState
import com.shmakov.udf.navigation.NavAction
import java.util.concurrent.atomic.AtomicReference

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
        RenderContentSlot(
            branchState = branchState,
            contentSlot = branchState.tree.root,
            onNavigationAction = onNavigationAction,
        )
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
    onNavigationAction: (NavAction) -> Unit,
) {
    contentSlot.screen.Content(
        childContent = {
            RenderChildContent(
                branchState = branchState,
                ownerContentEntryId = contentSlot.slot.entry.id,
                onNavigationAction = onNavigationAction,
            )
        },
        onNavigationAction = onNavigationAction,
    )

    RenderModalLayers(
        modalLayers = branchState.tree.modalLayers.filter { layer ->
            layer.layer.ownerContentEntryId == contentSlot.slot.entry.id
        },
        onNavigationAction = onNavigationAction,
    )
}

@Composable
private fun RenderChildContent(
    branchState: BoundRenderState,
    ownerContentEntryId: EntryId,
    onNavigationAction: (NavAction) -> Unit,
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
                onNavigationAction = onNavigationAction,
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
    modalLayers: List<BoundModalLayer>,
    onNavigationAction: (NavAction) -> Unit,
) {
    // Retained-modal planning is intentionally left to #14. This preserves the legacy behavior
    // while sourcing every desired modal from the branch's own immutable projection.
    val rememberedModalLayers = remember {
        AtomicReference(emptyList<BoundModalLayer>())
    }
    val lastModalLayers = rememberedModalLayers.get()
    val allLayers = mutableListOf<BoundModalLayer>()
    var lastIndex = 0
    var lastNewIndex = modalLayers.size

    modalLayers.forEachIndexed { index, modalLayer ->
        val indexInLast = lastModalLayers.indexOfFirst {
            it.layer.entry.id == modalLayer.layer.entry.id
        }

        if (indexInLast != -1) {
            allLayers += lastModalLayers.subList(lastIndex, indexInLast)
            allLayers += modalLayer
            lastIndex = indexInLast + 1
        } else {
            lastNewIndex = index
            return@forEachIndexed
        }
    }

    allLayers += lastModalLayers.drop(lastIndex)
    allLayers += modalLayers.drop(lastNewIndex)

    allLayers.forEach { modalLayer ->
        val entryId = modalLayer.layer.entry.id
        key(entryId) {
            modalLayer.screen.ModalContent(
                targetState = if (modalLayers.any { it.layer.entry.id == entryId }) {
                    ModalScreenState.Shown
                } else {
                    ModalScreenState.Hidden
                },
                onHide = {
                    rememberedModalLayers.getAndUpdate { layers ->
                        layers.filterNot { it.layer.entry.id == entryId }
                    }
                    onNavigationAction(NavAction.dismissModal(entryId))
                },
                onNavigationAction = onNavigationAction,
            )
        }
    }

    rememberedModalLayers.set(modalLayers)
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
