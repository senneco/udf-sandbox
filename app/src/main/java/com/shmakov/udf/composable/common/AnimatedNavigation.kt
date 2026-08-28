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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.shmakov.udf.TabNavigationActionOrigin
import com.shmakov.udf.TabNavigationRenderModel
import com.shmakov.udf.TabPresentationContinuity
import com.shmakov.udf.renderIdentity
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.ContentSlotId
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.ModalEntrance
import com.shmakov.udf.navigation.ModalScreenState
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.Screen

/** Renders one atomically projected navigation revision. */
@Composable
internal fun AnimatedNavigation(
    renderTarget: NavigationRenderTarget,
    onNavigationAction: (NavAction) -> Unit,
    destinationCatalog: DestinationCatalog = DemoDestinationCatalog,
) {
    OriginAwareAnimatedNavigation(
        renderTarget = renderTarget,
        retainedEntryIds = renderTarget.historyEntryIds,
        actionOrigin = PhysicalNavigationActionOrigin.Linear,
        presentationContinuity = TabPresentationContinuity.Leaf,
        onNavigationAction = { _, action -> onNavigationAction(action) },
        destinationCatalog = destinationCatalog,
    )
}

/** Renders one selected tab leaf while retaining UI state for the complete tab graph. */
@Composable
internal fun AnimatedTabLeafNavigation(
    model: TabNavigationRenderModel,
    onNavigationAction: (TabNavigationActionOrigin, NavAction) -> Unit,
    destinationCatalog: DestinationCatalog = DemoDestinationCatalog,
) {
    OriginAwareAnimatedNavigation(
        renderTarget = model.selectedLeaf,
        retainedEntryIds = model.retainedEntryIds,
        actionOrigin = PhysicalNavigationActionOrigin.Tab(model.actionOrigin),
        presentationContinuity = model.presentationContinuity,
        onNavigationAction = { origin, action ->
            val tabOrigin = checkNotNull(origin as? PhysicalNavigationActionOrigin.Tab) {
                "Tab leaf renderer received a non-tab action origin"
            }
            onNavigationAction(tabOrigin.value, action)
        },
        destinationCatalog = destinationCatalog,
    )
}

@Composable
private fun OriginAwareAnimatedNavigation(
    renderTarget: NavigationRenderTarget,
    retainedEntryIds: List<EntryId>,
    actionOrigin: PhysicalNavigationActionOrigin,
    presentationContinuity: TabPresentationContinuity,
    onNavigationAction: (PhysicalNavigationActionOrigin, NavAction) -> Unit,
    destinationCatalog: DestinationCatalog,
) {
    require(retainedEntryIds.containsAll(renderTarget.historyEntryIds)) {
        "retainedEntryIds must contain every rendered history entry"
    }
    require(retainedEntryIds.toSet().size == retainedEntryIds.size) {
        "retainedEntryIds must not contain duplicates"
    }
    val entryStateHost = EntrySaveableStateHost.remember()
    // These holders intentionally survive a typed destination-binding failure. Only a completely
    // bound target is accepted below, so recovery can reuse the previous entry state and motion
    // baseline without treating the failed projection as rendered.
    val acceptedTargetHolder = remember { AcceptedNavigationTargetHolder() }
    val renderOwnershipHolder = remember { EntryRenderOwnershipHolder() }
    val modalPresentationHolder = remember { ModalPresentationHolder() }
    val physicalRendererEpoch = remember { PhysicalRendererEpoch() }
    when (val binding = DestinationTreeBinder.bind(renderTarget.tree, destinationCatalog)) {
        is DestinationTreeBindingResult.Success -> key(
            physicalRendererEpoch.value,
            destinationCatalog,
        ) {
            PrepareBoundNavigation(
                renderTarget = renderTarget,
                boundTree = binding.tree,
                entryStateHost = entryStateHost,
                acceptedTargetHolder = acceptedTargetHolder,
                renderOwnershipHolder = renderOwnershipHolder,
                modalPresentationHolder = modalPresentationHolder,
                physicalRendererEpoch = physicalRendererEpoch,
                retainedEntryIds = retainedEntryIds,
                actionOrigin = actionOrigin,
                presentationContinuity = presentationContinuity,
                onNavigationAction = onNavigationAction,
            )
        }

        is DestinationTreeBindingResult.Failure -> {
            DestinationBindingFailure(binding.problem)
            MarkPhysicalRendererGap(physicalRendererEpoch)
        }
    }
}

internal sealed class PhysicalNavigationActionOrigin {
    object Linear : PhysicalNavigationActionOrigin()

    data class Tab(
        val value: TabNavigationActionOrigin,
    ) : PhysicalNavigationActionOrigin()
}

/**
 * Prepares one completely typed navigation tree before creating its physical animated subtree.
 * A binding failure therefore removes the whole subtree, including its transition ownership,
 * while the accepted lifecycle holders above survive for a later valid target.
 */
@Composable
private fun PrepareBoundNavigation(
    renderTarget: NavigationRenderTarget,
    boundTree: BoundNavigationRenderTree,
    entryStateHost: EntrySaveableStateHost,
    acceptedTargetHolder: AcceptedNavigationTargetHolder,
    renderOwnershipHolder: EntryRenderOwnershipHolder,
    modalPresentationHolder: ModalPresentationHolder,
    physicalRendererEpoch: PhysicalRendererEpoch,
    retainedEntryIds: List<EntryId>,
    actionOrigin: PhysicalNavigationActionOrigin,
    presentationContinuity: TabPresentationContinuity,
    onNavigationAction: (PhysicalNavigationActionOrigin, NavAction) -> Unit,
) {
    val previousModalPresentation = modalPresentationHolder.accepted
    val desiredModalLayers = boundTree.modalLayers.map { layer -> layer.layer }
    val candidateModalState = when {
        previousModalPresentation == null -> ModalPresentationPlanner.start(
            navigationRevision = renderTarget.navigationRevision,
            desired = desiredModalLayers,
        )

        presentationContinuity == TabPresentationContinuity.ResetContainer &&
            acceptedTargetHolder.target?.navigationRevision != renderTarget.navigationRevision ->
            ModalPresentationPlanner.snap(
                previous = previousModalPresentation.state,
                navigationRevision = renderTarget.navigationRevision,
                desired = desiredModalLayers,
            )

        else -> ModalPresentationPlanner.reconcile(
            previous = previousModalPresentation.state,
            navigationRevision = renderTarget.navigationRevision,
            desired = desiredModalLayers,
        ).state
    }
    when (
        val binding = DestinationTreeBinder.materializePresentedModalLayers(
            layers = candidateModalState.layers,
            desiredLayers = boundTree.modalLayers,
            acceptedLayers = previousModalPresentation?.layers.orEmpty(),
            desiredActionOrigin = actionOrigin,
        )
    ) {
        is PresentedModalLayersBindingResult.Success -> RenderMaterializedNavigation(
            renderTarget = renderTarget,
            boundTree = boundTree,
            candidateModalState = candidateModalState,
            candidateModalLayers = binding.layers,
            entryStateHost = entryStateHost,
            acceptedTargetHolder = acceptedTargetHolder,
            renderOwnershipHolder = renderOwnershipHolder,
            modalPresentationHolder = modalPresentationHolder,
            physicalRendererEpoch = physicalRendererEpoch,
            retainedEntryIds = retainedEntryIds,
            actionOrigin = actionOrigin,
            onNavigationAction = onNavigationAction,
        )

        is PresentedModalLayersBindingResult.Failure -> {
            DestinationBindingFailure(binding.problem)
            MarkPhysicalRendererGap(physicalRendererEpoch)
        }
    }
}

@Composable
private fun RenderMaterializedNavigation(
    renderTarget: NavigationRenderTarget,
    boundTree: BoundNavigationRenderTree,
    candidateModalState: ModalPresentationState,
    candidateModalLayers: List<BoundPresentedModalLayer>,
    entryStateHost: EntrySaveableStateHost,
    acceptedTargetHolder: AcceptedNavigationTargetHolder,
    renderOwnershipHolder: EntryRenderOwnershipHolder,
    modalPresentationHolder: ModalPresentationHolder,
    physicalRendererEpoch: PhysicalRendererEpoch,
    retainedEntryIds: List<EntryId>,
    actionOrigin: PhysicalNavigationActionOrigin,
    onNavigationAction: (PhysicalNavigationActionOrigin, NavAction) -> Unit,
) {
    // Accepted-target motion metadata belongs only to this renderer composition. Unlike entry UI
    // state, it is deliberately not saveable or store-owned, so recreation cannot replay motion.
    val contentMotion = NavigationPresentationPlanner.contentMotion(
        previous = acceptedTargetHolder.target,
        target = renderTarget,
    )
    val targetState = BoundRenderState(
        renderTarget = renderTarget,
        tree = boundTree,
        contentMotion = contentMotion,
        actionOrigin = actionOrigin,
    )
    val transition = androidx.compose.animation.core.updateTransition(
        targetState = targetState,
        label = "NavigationRoot",
    )
    val latestTarget = transition.targetState.renderTarget
    val renderOwnership = renderOwnershipHolder.prepare(
        latestTarget = latestTarget,
        physicalRendererEpoch = physicalRendererEpoch.value,
    )
    val candidateModalPresentation = AcceptedModalPresentation(
        state = candidateModalState,
        layers = candidateModalLayers,
    ).withoutUnreachableExits(renderOwnership)
    // Accept only a target whose complete destination tree composed successfully. A same-revision
    // layout reprojection then compares against this accepted target, not an animation's N-1 branch.
    SideEffect {
        acceptedTargetHolder.target = renderTarget
        renderOwnershipHolder.accept(
            candidate = renderOwnership,
            physicalRendererEpoch = physicalRendererEpoch.value,
        )
        modalPresentationHolder.accept(candidateModalPresentation)
        entryStateHost.accept(retainedEntryIds)
    }

    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { navigationContentTransform() },
        contentKey = { state -> state.tree.root.slot.renderIdentity },
    ) { branchState ->
        RenderNavigationBranch(
            branchState = branchState,
            modalLayers = candidateModalPresentation.layers,
            entryStateHost = entryStateHost,
            renderOwnership = renderOwnership,
            onNavigationAction = onNavigationAction,
            onExitFinished = modalPresentationHolder::completeExit,
        )
    }
}

@Composable
private fun RenderNavigationBranch(
    branchState: BoundRenderState,
    modalLayers: List<BoundPresentedModalLayer>,
    entryStateHost: EntrySaveableStateHost,
    renderOwnership: EntryRenderOwnership,
    onNavigationAction: (PhysicalNavigationActionOrigin, NavAction) -> Unit,
    onExitFinished: (ModalExitToken) -> Unit,
) {
    RenderContentSlot(
        branchState = branchState,
        contentSlot = branchState.tree.root,
        modalLayers = modalLayers,
        entryStateHost = entryStateHost,
        renderOwnership = renderOwnership,
        onNavigationAction = onNavigationAction,
        onExitFinished = onExitFinished,
    )
}

private data class AcceptedModalPresentation(
    val state: ModalPresentationState,
    val layers: List<BoundPresentedModalLayer>,
)

/**
 * Drops an exit whose exact content owner is no longer reachable in either live render target.
 * Surviving owners can carry this one global presentation across root and nested reparenting.
 */
private fun AcceptedModalPresentation.withoutUnreachableExits(
    renderOwnership: EntryRenderOwnership,
): AcceptedModalPresentation {
    var prunedState = state
    val removedTokens = LinkedHashSet<ModalExitToken>()
    layers.forEach { layer ->
        val presentation = layer.presentation as? PresentedModalLayer.Exiting
            ?: return@forEach
        if (!renderOwnership.hasPhysicalOwner(presentation.layer.ownerContentEntryId)) {
            when (val completion = ModalPresentationPlanner.completeExit(
                previous = prunedState,
                token = presentation.token,
            )) {
                is ModalExitCompletion.Applied -> {
                    prunedState = completion.state
                    removedTokens += presentation.token
                }

                is ModalExitCompletion.Unchanged -> Unit
            }
        }
    }
    if (removedTokens.isEmpty()) return this

    return AcceptedModalPresentation(
        state = prunedState,
        layers = layers.filterNot { layer ->
            val presentation = layer.presentation
            presentation is PresentedModalLayer.Exiting &&
                presentation.token in removedTokens
        },
    )
}

private class ModalPresentationHolder {
    var accepted: AcceptedModalPresentation? by mutableStateOf(null)
        private set

    fun accept(candidate: AcceptedModalPresentation) {
        // Catalogs materialize fresh Screen objects on recomposition. Presentation state is the
        // semantic identity; replacing an equal state only because its binding object is fresh
        // would invalidate this holder forever.
        if (accepted?.state != candidate.state) {
            accepted = candidate
        }
    }

    fun completeExit(token: ModalExitToken) {
        val previous = accepted ?: return
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

/** Forces a fresh physical transition after one or more consecutive binding failures. */
private class PhysicalRendererEpoch {
    var value: Long = 0L
        private set

    fun advance() {
        value += 1L
    }
}

@Composable
private fun MarkPhysicalRendererGap(epoch: PhysicalRendererEpoch) {
    // A DisposableEffect enters only once for a consecutive failure interval. Advancing plain,
    // non-snapshot state here avoids both composition-time mutation and an invalidation loop.
    DisposableEffect(epoch) {
        epoch.advance()
        onDispose { }
    }
}

private class BoundRenderState(
    val renderTarget: NavigationRenderTarget,
    val tree: BoundNavigationRenderTree,
    val contentMotion: NavigationContentMotion,
    val actionOrigin: PhysicalNavigationActionOrigin,
) {
    private val contentIdentity = BoundContentIdentity(
        root = tree.root.screenContentIdentity(),
        nested = tree.nestedSlots.map { slot -> slot.screenContentIdentity() },
    )

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is BoundRenderState &&
            renderTarget == other.renderTarget &&
            contentIdentity == other.contentIdentity &&
            actionOrigin == other.actionOrigin

    override fun hashCode(): Int {
        var result = renderTarget.hashCode()
        result = 31 * result + contentIdentity.hashCode()
        result = 31 * result + actionOrigin.hashCode()
        return result
    }
}

private data class BoundContentIdentity(
    val root: ScreenContentIdentity,
    val nested: List<ScreenContentIdentity>,
)

@Composable
private fun RenderContentSlot(
    branchState: BoundRenderState,
    contentSlot: BoundContentSlot,
    modalLayers: List<BoundPresentedModalLayer>,
    entryStateHost: EntrySaveableStateHost,
    renderOwnership: EntryRenderOwnership,
    onNavigationAction: (PhysicalNavigationActionOrigin, NavAction) -> Unit,
    onExitFinished: (ModalExitToken) -> Unit,
) {
    val entryId = contentSlot.slot.entry.id
    val latestChildContent = rememberUpdatedState<@Composable () -> Unit> {
        RenderChildContent(
            branchState = branchState,
            ownerContentEntryId = entryId,
            modalLayers = modalLayers,
            entryStateHost = entryStateHost,
            renderOwnership = renderOwnership,
            onNavigationAction = onNavigationAction,
            onExitFinished = onExitFinished,
        )
    }
    val latestNavigationAction = rememberUpdatedState(onNavigationAction)
    // The physical content identity belongs to the entry, not to the current top-of-stack guard.
    // A stable parent's callback must see the newest origin without restarting its Content, while
    // an outgoing transition branch keeps a separate State and therefore its historical origin.
    val latestActionOrigin = rememberUpdatedState(branchState.actionOrigin)
    val contentIdentity = contentSlot.screenContentIdentity()
    val screenContent = remember(contentIdentity) {
        EntryRenderContent {
            RenderScreenContent(
                screen = contentSlot.screen,
                latestChildContent = latestChildContent,
                latestNavigationAction = latestNavigationAction,
                latestActionOrigin = latestActionOrigin,
            )
        }
    }
    if (renderOwnership.ownsContent(branchState.renderTarget, entryId)) {
        entryStateHost.Render(entryId, screenContent)
    }

    RenderModalLayers(
        physicalBranchTarget = branchState.renderTarget,
        modalLayers = modalLayers.filter { modalLayer ->
            modalLayer.presentation.layer.ownerContentEntryId == entryId
        },
        entryStateHost = entryStateHost,
        renderOwnership = renderOwnership,
        onNavigationAction = onNavigationAction,
        onExitFinished = onExitFinished,
    )
}

/** Model-defined inputs whose change must create a fresh destination content composition. */
private data class ScreenContentIdentity(
    val entry: BackStackEntry,
    val screenClass: Class<out Screen>,
    val parentInputsKey: Any?,
)

private fun BoundContentSlot.screenContentIdentity(): ScreenContentIdentity =
    ScreenContentIdentity(
        entry = slot.entry,
        screenClass = screen.javaClass,
        parentInputsKey = parentInputsKey,
    )

/** Keeps the non-restartable virtual Screen call behind one entry-local restart scope. */
@Composable
private fun RenderScreenContent(
    screen: Screen,
    latestChildContent: State<@Composable () -> Unit>,
    latestNavigationAction: State<(PhysicalNavigationActionOrigin, NavAction) -> Unit>,
    latestActionOrigin: State<PhysicalNavigationActionOrigin>,
) {
    screen.Content(
        childContent = {
            RenderLatestChildContent(latestChildContent)
        },
        onNavigationAction = { action ->
            latestNavigationAction.value(latestActionOrigin.value, action)
        },
    )
}

/** Reads the changing renderer-owned child slot in its own restartable Compose scope. */
@Composable
private fun RenderLatestChildContent(
    latestChildContent: State<@Composable () -> Unit>,
) {
    latestChildContent.value()
}

@Composable
private fun RenderChildContent(
    branchState: BoundRenderState,
    ownerContentEntryId: EntryId,
    modalLayers: List<BoundPresentedModalLayer>,
    entryStateHost: EntrySaveableStateHost,
    renderOwnership: EntryRenderOwnership,
    onNavigationAction: (PhysicalNavigationActionOrigin, NavAction) -> Unit,
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
                entryStateHost = entryStateHost,
                renderOwnership = renderOwnership,
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
    physicalBranchTarget: NavigationRenderTarget,
    modalLayers: List<BoundPresentedModalLayer>,
    entryStateHost: EntrySaveableStateHost,
    renderOwnership: EntryRenderOwnership,
    onNavigationAction: (PhysicalNavigationActionOrigin, NavAction) -> Unit,
    onExitFinished: (ModalExitToken) -> Unit,
) {
    modalLayers.forEach { modalLayer ->
        val presentation = modalLayer.presentation
        val actionOrigin = modalLayer.actionOrigin
        val entryId = presentation.layer.entry.id
        val exitToken = (presentation as? PresentedModalLayer.Exiting)?.token
        if (
            renderOwnership.ownsModalSlot(
                physicalBranchTarget = physicalBranchTarget,
                ownerContentEntryId = presentation.layer.ownerContentEntryId,
            )
        ) {
            entryStateHost.Render(entryId) {
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
                                onNavigationAction(
                                    actionOrigin,
                                    NavAction.dismissModal(entryId),
                                )
                            }
                        },
                        onExitFinished = {
                            if (exitToken != null) {
                                onExitFinished(exitToken)
                            }
                        },
                        onNavigationAction = { action ->
                            onNavigationAction(actionOrigin, action)
                        },
                    )
                }
            }
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
