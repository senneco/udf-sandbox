package com.shmakov.udf

import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavProjectionProblem
import com.shmakov.udf.navigation.NavProjectionResult
import com.shmakov.udf.navigation.NavProjector
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.NavigationLayoutPolicy
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.navigation.TabId
import com.shmakov.udf.navigation.TabTransitionIntent
import java.util.Collections

/** Exact tab and leaf occurrence that owned a physical renderer callback. */
internal data class TabNavigationActionOrigin(
    val tabId: TabId,
    val topEntryId: EntryId,
) {
    /** Keeps even an outgoing branch guarded after a tab switch or graph replacement. */
    fun wrap(action: NavAction): TabAction.NavigateInSelectedTab =
        TabAction.navigateInSelectedTab(
            expectedSelectedTabId = tabId,
            expectedTopId = topEntryId,
            action = action,
        )
}

/** Complete pure input for one app-side tab renderer pass. */
internal class TabNavigationRenderModel(
    orderedTabIds: List<TabId>,
    val selectedTabId: TabId,
    val selectedLeaf: NavigationRenderTarget,
    retainedEntryIds: List<EntryId>,
    val actionOrigin: TabNavigationActionOrigin,
    val presentationContinuity: TabPresentationContinuity,
) {
    /** Exact tab-bar order owned by durable state, never by the visual catalog. */
    val orderedTabIds: List<TabId> = immutableRenderingListCopy(orderedTabIds)

    /** All graph occurrences in tab/history order, including inactive tabs. */
    val retainedEntryIds: List<EntryId> = immutableRenderingListCopy(retainedEntryIds)

    init {
        require(this.orderedTabIds.isNotEmpty()) { "orderedTabIds must not be empty" }
        require(this.orderedTabIds.toSet().size == this.orderedTabIds.size) {
            "orderedTabIds must not contain duplicates"
        }
        require(selectedTabId in this.orderedTabIds) {
            "selectedTabId $selectedTabId is missing from orderedTabIds"
        }
        require(this.retainedEntryIds.isNotEmpty()) { "retainedEntryIds must not be empty" }
        require(this.retainedEntryIds.toSet().size == this.retainedEntryIds.size) {
            "retainedEntryIds must not contain duplicates"
        }
        require(this.retainedEntryIds.containsAll(selectedLeaf.historyEntryIds)) {
            "retainedEntryIds must contain every selected leaf history entry"
        }
        require(actionOrigin.tabId == selectedTabId) {
            "action origin tab ${actionOrigin.tabId} must match selected tab $selectedTabId"
        }
        require(actionOrigin.topEntryId == selectedLeaf.historyEntryIds.last()) {
            "action origin top ${actionOrigin.topEntryId} must match selected leaf top " +
                selectedLeaf.historyEntryIds.last()
        }
    }
}

/** Whether renderer-local leaf/modal presentation may continue across this graph revision. */
internal enum class TabPresentationContinuity {
    Leaf,
    ResetContainer,
}

/** Why a complete tab renderer input could not be built before composition. */
internal sealed class TabNavigationRenderProblem {
    data class MissingTabConfiguration(
        val tabId: TabId,
    ) : TabNavigationRenderProblem()

    data class LeafProjectionFailed(
        val tabId: TabId,
        val problem: NavProjectionProblem,
    ) : TabNavigationRenderProblem()
}

/** Atomic pure outcome from [TabNavigationRenderPlanner]. */
internal sealed class TabNavigationRenderResult {
    data class Ready(
        val model: TabNavigationRenderModel,
    ) : TabNavigationRenderResult()

    data class Rejected(
        val problem: TabNavigationRenderProblem,
    ) : TabNavigationRenderResult()
}

/** Projects one complete tab graph into one visible leaf plus graph-wide UI-state retention. */
internal object TabNavigationRenderPlanner {

    fun plan(
        frame: TabNavigationFrame,
        configuredTabIds: Set<TabId>,
        layoutPolicy: NavigationLayoutPolicy,
    ): TabNavigationRenderResult {
        val orderedTabIds = frame.state.tabs.map { tab -> tab.id }
        orderedTabIds.firstOrNull { tabId -> tabId !in configuredTabIds }?.let { missing ->
            return TabNavigationRenderResult.Rejected(
                TabNavigationRenderProblem.MissingTabConfiguration(missing),
            )
        }

        val selectedTab = frame.state.selectedTab
        val projection = when (
            val result = NavProjector.project(selectedTab.history, layoutPolicy)
        ) {
            is NavProjectionResult.Success -> result.tree
            is NavProjectionResult.Failure -> return TabNavigationRenderResult.Rejected(
                TabNavigationRenderProblem.LeafProjectionFailed(
                    tabId = selectedTab.id,
                    problem = result.problem,
                ),
            )
        }
        val selectedHistoryIds = selectedTab.history.entries.map { entry -> entry.id }

        return TabNavigationRenderResult.Ready(
            TabNavigationRenderModel(
                orderedTabIds = orderedTabIds,
                selectedTabId = selectedTab.id,
                selectedLeaf = NavigationRenderTarget(
                    navigationRevision = frame.revision,
                    historyEntryIds = selectedHistoryIds,
                    tree = projection,
                    transitionIntent = frame.selectedLeafTransition(),
                ),
                retainedEntryIds = frame.state.tabs.flatMap { tab ->
                    tab.history.entries.map { entry -> entry.id }
                },
                actionOrigin = TabNavigationActionOrigin(
                    tabId = selectedTab.id,
                    topEntryId = selectedTab.history.top.id,
                ),
                presentationContinuity = frame.presentationContinuity(),
            ),
        )
    }
}

private fun TabNavigationFrame.selectedLeafTransition(): NavTransitionIntent? =
    when (val intent = transition) {
        is TabTransitionIntent.NavigationChanged ->
            intent.navigationTransition.takeIf { intent.tabId == state.selectedTabId }

        is TabTransitionIntent.TabOpened -> intent.targetHistoryTransition.takeIf {
            intent.previousSelectedTabId == intent.openedTabId &&
                intent.openedTabId == state.selectedTabId
        }

        is TabTransitionIntent.TabSelected,
        is TabTransitionIntent.GraphReplaced,
        null -> null
    }

private fun TabNavigationFrame.presentationContinuity(): TabPresentationContinuity =
    when (val intent = transition) {
        is TabTransitionIntent.NavigationChanged -> if (intent.tabId == state.selectedTabId) {
            TabPresentationContinuity.Leaf
        } else {
            TabPresentationContinuity.ResetContainer
        }

        is TabTransitionIntent.TabOpened -> if (
            intent.previousSelectedTabId == intent.openedTabId &&
            intent.openedTabId == state.selectedTabId
        ) {
            TabPresentationContinuity.Leaf
        } else {
            TabPresentationContinuity.ResetContainer
        }

        is TabTransitionIntent.TabSelected,
        is TabTransitionIntent.GraphReplaced,
        null -> TabPresentationContinuity.ResetContainer
    }

private fun <T> immutableRenderingListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))
