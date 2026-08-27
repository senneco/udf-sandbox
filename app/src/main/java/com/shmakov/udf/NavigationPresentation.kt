package com.shmakov.udf

import com.shmakov.udf.navigation.ContentSlot
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.NavigationRenderTree
import java.util.Collections

/** One atomic projection revision supplied to the Compose renderer. */
internal class NavigationRenderTarget(
    val navigationRevision: Long,
    historyEntryIds: List<EntryId>,
    val tree: NavigationRenderTree,
    val transitionIntent: NavTransitionIntent?,
) {
    val historyEntryIds: List<EntryId> =
        Collections.unmodifiableList(ArrayList(historyEntryIds))

    init {
        require(this.historyEntryIds.isNotEmpty()) {
            "historyEntryIds must not be empty"
        }

        val seenEntryIds = mutableSetOf<EntryId>()
        this.historyEntryIds.forEachIndexed { index, entryId ->
            require(entryId.value.isNotBlank()) {
                "historyEntryIds[$index] must not be blank"
            }
            require(seenEntryIds.add(entryId)) {
                "historyEntryIds contains duplicate entry ID '${entryId.value}'"
            }
        }

        val historyEntryIdSet = seenEntryIds
        val seenVisibleEntryIds = mutableSetOf<EntryId>()
        tree.orderedVisibleEntryIds().forEachIndexed { index, visibleEntryId ->
            require(visibleEntryId in historyEntryIdSet) {
                "Visible tree entry ID '${visibleEntryId.value}' is missing from historyEntryIds"
            }
            require(seenVisibleEntryIds.add(visibleEntryId)) {
                "Visible tree contains duplicate entry ID '${visibleEntryId.value}' " +
                    "at ordered index $index"
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is NavigationRenderTarget &&
            navigationRevision == other.navigationRevision &&
            historyEntryIds == other.historyEntryIds &&
            tree == other.tree &&
            transitionIntent == other.transitionIntent

    override fun hashCode(): Int {
        var result = navigationRevision.hashCode()
        result = 31 * result + historyEntryIds.hashCode()
        result = 31 * result + tree.hashCode()
        result = 31 * result + (transitionIntent?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "NavigationRenderTarget(navigationRevision=$navigationRevision, " +
            "historyEntryIds=$historyEntryIds, tree=$tree, " +
            "transitionIntent=$transitionIntent)"
}

/** Content motion selected only for a validated, contiguous navigation change. */
internal enum class NavigationContentMotion {
    None,
    Push,
    Pop,
    Replace,
}

/** Stable render identity for one visible content occurrence. */
internal val ContentSlot.renderIdentity: EntryId
    get() = entry.id

/**
 * Classifies presentation motion without retaining a previous projection outside Compose.
 *
 * A transition intent is process-local metadata, so it is trusted only when the renderer already
 * has the immediately preceding revision and the intent names entries visible in the corresponding
 * previous and target trees.
 */
internal object NavigationPresentationPlanner {

    fun contentMotion(
        previous: NavigationRenderTarget?,
        target: NavigationRenderTarget,
    ): NavigationContentMotion {
        previous ?: return NavigationContentMotion.None
        if (!target.isImmediatelyAfter(previous)) return NavigationContentMotion.None

        val previousContentIds = previous.tree.visibleContentEntryIds()
        val targetContentIds = target.tree.visibleContentEntryIds()
        if (previousContentIds == targetContentIds) return NavigationContentMotion.None

        val previousVisibleIds = previous.tree.orderedVisibleEntryIds()
        val targetVisibleIds = target.tree.orderedVisibleEntryIds()

        return when (val intent = target.transitionIntent) {
            is NavTransitionIntent.Pushed -> if (
                intent.fromEntryId == previous.tree.visibleTopEntryId() &&
                intent.addedEntryId == target.tree.visibleContentTopEntryId() &&
                intent.addedEntryId !in previousVisibleIds
            ) {
                NavigationContentMotion.Push
            } else {
                NavigationContentMotion.None
            }

            is NavTransitionIntent.Popped -> if (
                intent.removedEntryId == previous.tree.visibleContentTopEntryId() &&
                intent.removedEntryId !in targetVisibleIds &&
                intent.revealedEntryId == target.tree.visibleTopEntryId()
            ) {
                NavigationContentMotion.Pop
            } else {
                NavigationContentMotion.None
            }

            is NavTransitionIntent.BranchReplaced -> {
                val removedIds = intent.removedEntryIds.toSet()
                val retainedVisibleAnchor = previousContentIds
                    .zip(targetContentIds)
                    .takeWhile { (previousId, targetId) -> previousId == targetId }
                    .lastOrNull()
                    ?.first
                val sourceVisibleIndex = previousVisibleIds.indexOf(intent.sourceEntryId)
                val sourceMatchesRetainedAnchor = retainedVisibleAnchor == null ||
                    intent.sourceEntryId == retainedVisibleAnchor
                // A new root placement may hide the retained prefix through a visible source. Only
                // the visible suffix after that source must therefore be named as logically removed.
                val expectedRemovedVisibleIds = when {
                    !sourceMatchesRetainedAnchor -> null
                    sourceVisibleIndex >= 0 -> previousVisibleIds
                        .drop(sourceVisibleIndex + 1)
                        .toSet()
                    else -> previousVisibleIds.toSet()
                }
                if (
                    expectedRemovedVisibleIds != null &&
                    intent.addedEntryId == target.tree.visibleContentTopEntryId() &&
                    intent.addedEntryId == target.tree.visibleTopEntryId() &&
                    intent.addedEntryId !in previousVisibleIds &&
                    removedIds.intersect(previousVisibleIds.toSet()) == expectedRemovedVisibleIds &&
                    removedIds.none { it in targetVisibleIds }
                ) {
                    NavigationContentMotion.Replace
                } else {
                    NavigationContentMotion.None
                }
            }

            is NavTransitionIntent.HistoryReplaced -> if (
                intent.previousTopEntryId == previous.tree.visibleTopEntryId() &&
                intent.targetTopEntryId == target.tree.visibleTopEntryId()
            ) {
                NavigationContentMotion.Replace
            } else {
                NavigationContentMotion.None
            }

            is NavTransitionIntent.ModalDismissed,
            null -> NavigationContentMotion.None
        }
    }

    private fun NavigationRenderTarget.isImmediatelyAfter(
        previous: NavigationRenderTarget,
    ): Boolean =
        previous.navigationRevision != Long.MAX_VALUE &&
            navigationRevision == previous.navigationRevision + 1L
}

private fun NavigationRenderTree.visibleContentEntryIds(): List<EntryId> = buildList {
    add(root.renderIdentity)
    nestedSlots.forEach { slot -> add(slot.renderIdentity) }
}

private fun NavigationRenderTree.orderedVisibleEntryIds(): List<EntryId> = buildList {
    addAll(visibleContentEntryIds())
    modalLayers.forEach { layer -> add(layer.entry.id) }
}

private fun NavigationRenderTree.visibleContentTopEntryId(): EntryId =
    nestedSlots.lastOrNull()?.renderIdentity ?: root.renderIdentity

private fun NavigationRenderTree.visibleTopEntryId(): EntryId =
    modalLayers.lastOrNull()?.entry?.id ?: visibleContentTopEntryId()
