package com.shmakov.udf.navigation

import java.util.Collections

/** A fully materialized operation on a [TabNavigationState]. */
sealed class TabAction {

    /** Selects an existing tab without changing any tab history. */
    data class Select(
        val tabId: TabId,
    ) : TabAction()

    /**
     * Applies a leaf [NavAction] only while its originating tab and exact top are still current.
     *
     * The two guards make even an otherwise unguarded [NavAction.Pop] safe against callbacks that
     * outlive a tab switch or a newer navigation state.
     */
    data class NavigateInSelectedTab(
        val expectedSelectedTabId: TabId,
        val expectedTopId: EntryId,
        val action: NavAction,
    ) : TabAction()

    /**
     * Atomically replaces one existing tab history and selects that tab.
     *
     * Every other tab is preserved. A missing tab, identity rebound, or graph collision leaves the
     * complete graph and selection unchanged.
     */
    data class OpenTab(
        val tabId: TabId,
        val targetHistory: NavState,
    ) : TabAction()

    /**
     * Atomically replaces the complete tab graph.
     *
     * A retained [EntryId] keeps its route. Full replacement may explicitly move the same
     * ID-and-route occurrence between tabs; its UI identity follows the occurrence.
     */
    data class ReplaceGraph(
        val target: TabNavigationState,
    ) : TabAction()

    /**
     * Applies Back only while the selected tab and its exact visible top still match.
     *
     * Back at a tab root returns [TabUnchangedReason.ActiveTabAtRoot]; this reducer never selects a
     * fallback tab or finishes an Android host.
     */
    data class Back(
        val expectedSelectedTabId: TabId,
        val expectedTopId: EntryId,
    ) : TabAction()

    companion object {
        @JvmStatic
        fun select(tabId: TabId): Select = Select(tabId)

        @JvmStatic
        fun navigateInSelectedTab(
            expectedSelectedTabId: TabId,
            expectedTopId: EntryId,
            action: NavAction,
        ): NavigateInSelectedTab = NavigateInSelectedTab(
            expectedSelectedTabId,
            expectedTopId,
            action,
        )

        /** Captures exact selected-tab guards for an immediately materialized leaf action. */
        @JvmStatic
        fun navigateInSelectedTab(
            state: TabNavigationState,
            action: NavAction,
        ): NavigateInSelectedTab = NavigateInSelectedTab(
            expectedSelectedTabId = state.selectedTabId,
            expectedTopId = state.selectedTab.history.top.id,
            action = action,
        )

        @JvmStatic
        fun openTab(tabId: TabId, targetHistory: NavState): OpenTab =
            OpenTab(tabId, targetHistory)

        @JvmStatic
        fun replaceGraph(target: TabNavigationState): ReplaceGraph = ReplaceGraph(target)

        /** Captures an exact Back action from the currently visible tab occurrence. */
        @JvmStatic
        fun back(state: TabNavigationState): Back = Back(
            expectedSelectedTabId = state.selectedTabId,
            expectedTopId = state.selectedTab.history.top.id,
        )

        @JvmStatic
        fun back(expectedSelectedTabId: TabId, expectedTopId: EntryId): Back =
            Back(expectedSelectedTabId, expectedTopId)
    }
}

/** Semantic description of one applied tab-graph change. It is not durable state. */
sealed class TabTransitionIntent {

    data class TabSelected(
        val previousTabId: TabId,
        val selectedTabId: TabId,
    ) : TabTransitionIntent()

    data class NavigationChanged(
        val tabId: TabId,
        val navigationTransition: NavTransitionIntent,
    ) : TabTransitionIntent()

    /**
     * One atomic target-history replacement and tab selection.
     *
     * [targetHistoryTransition] is measured inside the opened tab. It is `null` when that history
     * was already equal and only selection changed; it must not be interpreted as the cross-tab
     * visible transition described by [previousVisibleTopId] and [targetVisibleTopId].
     */
    data class TabOpened(
        val previousSelectedTabId: TabId,
        val openedTabId: TabId,
        val previousVisibleTopId: EntryId,
        val targetVisibleTopId: EntryId,
        val targetHistoryTransition: NavTransitionIntent?,
    ) : TabTransitionIntent()

    data class GraphReplaced(
        val previousSelectedTabId: TabId,
        val selectedTabId: TabId,
        val previousVisibleTopId: EntryId,
        val targetVisibleTopId: EntryId,
    ) : TabTransitionIntent()
}

/** The deterministic reason why a valid [TabAction] left the graph unchanged. */
sealed class TabUnchangedReason {

    data class TabNotFound(val tabId: TabId) : TabUnchangedReason()

    data class AlreadySelected(val tabId: TabId) : TabUnchangedReason()

    data class SelectedTabChanged(
        val expectedSelectedTabId: TabId,
        val actualSelectedTabId: TabId,
    ) : TabUnchangedReason()

    data class TopEntryChanged(
        val tabId: TabId,
        val expectedTopId: EntryId,
        val actualTopId: EntryId,
    ) : TabUnchangedReason()

    data class NavigationUnchanged(
        val tabId: TabId,
        val reason: NavUnchangedReason,
    ) : TabUnchangedReason()

    class InvalidResultingGraph(
        problems: List<TabNavigationStateProblem>,
    ) : TabUnchangedReason() {
        val problems: List<TabNavigationStateProblem> = immutableProblemListCopy(problems)

        override fun equals(other: Any?): Boolean =
            this === other || other is InvalidResultingGraph && problems == other.problems

        override fun hashCode(): Int = problems.hashCode()

        override fun toString(): String = "InvalidResultingGraph(problems=$problems)"
    }

    data class EntryIdentityRebound(
        val entryId: EntryId,
        val previousTabId: TabId,
        val previousRoute: Route,
        val targetTabId: TabId,
        val targetRoute: Route,
    ) : TabUnchangedReason()

    data class ActiveTabAtRoot(
        val tabId: TabId,
        val rootEntryId: EntryId,
    ) : TabUnchangedReason()

    object AlreadyAtTarget : TabUnchangedReason()
}

/** Result of reducing one [TabAction]. */
sealed class TabReduction {
    abstract val state: TabNavigationState

    data class Changed(
        override val state: TabNavigationState,
        val transition: TabTransitionIntent,
    ) : TabReduction()

    data class Unchanged(
        override val state: TabNavigationState,
        val reason: TabUnchangedReason,
    ) : TabReduction()
}

/** Pure, deterministic reducer for a tab container and its independent leaf histories. */
object TabReducer {

    @JvmStatic
    fun reduce(state: TabNavigationState, action: TabAction): TabReduction = when (action) {
        is TabAction.Select -> select(state, action)
        is TabAction.NavigateInSelectedTab -> navigateInSelectedTab(state, action)
        is TabAction.OpenTab -> openTab(state, action)
        is TabAction.ReplaceGraph -> replaceGraph(state, action)
        is TabAction.Back -> back(state, action)
    }

    private fun select(
        state: TabNavigationState,
        action: TabAction.Select,
    ): TabReduction {
        if (state[action.tabId] == null) {
            return state.unchanged(TabUnchangedReason.TabNotFound(action.tabId))
        }
        if (state.selectedTabId == action.tabId) {
            return state.unchanged(TabUnchangedReason.AlreadySelected(action.tabId))
        }

        return TabReduction.Changed(
            state = TabNavigationState.create(action.tabId, state.tabs),
            transition = TabTransitionIntent.TabSelected(
                previousTabId = state.selectedTabId,
                selectedTabId = action.tabId,
            ),
        )
    }

    private fun navigateInSelectedTab(
        state: TabNavigationState,
        action: TabAction.NavigateInSelectedTab,
    ): TabReduction {
        val tab = state.guardedTab(action.expectedSelectedTabId, action.expectedTopId)
            ?: return state.guardFailure(action.expectedSelectedTabId, action.expectedTopId)

        return applyLeafReduction(
            state = state,
            tab = tab,
            reduction = NavReducer.reduce(tab.history, action.action),
        )
    }

    private fun openTab(
        state: TabNavigationState,
        action: TabAction.OpenTab,
    ): TabReduction {
        val tab = state[action.tabId]
            ?: return state.unchanged(TabUnchangedReason.TabNotFound(action.tabId))

        if (state.selectedTabId == action.tabId && tab.history == action.targetHistory) {
            return state.unchanged(TabUnchangedReason.AlreadyAtTarget)
        }

        val previousVisibleTopId = state.selectedTab.history.top.id
        return when (
            val leafReduction = NavReducer.reduce(
                tab.history,
                NavAction.ReplaceHistory(action.targetHistory),
            )
        ) {
            is NavReduction.Unchanged -> {
                if (leafReduction.reason != NavUnchangedReason.AlreadyAtTarget) {
                    state.unchanged(
                        TabUnchangedReason.NavigationUnchanged(action.tabId, leafReduction.reason),
                    )
                } else {
                    val selectedState = TabNavigationState.create(action.tabId, state.tabs)
                    TabReduction.Changed(
                        state = selectedState,
                        transition = TabTransitionIntent.TabOpened(
                            previousSelectedTabId = state.selectedTabId,
                            openedTabId = action.tabId,
                            previousVisibleTopId = previousVisibleTopId,
                            targetVisibleTopId = selectedState.selectedTab.history.top.id,
                            targetHistoryTransition = null,
                        ),
                    )
                }
            }

            is NavReduction.Changed -> {
                val candidate = state.withHistory(
                    tabId = action.tabId,
                    history = leafReduction.state,
                    selectedTabId = action.tabId,
                )
                when (candidate) {
                    is TabNavigationStateCreationResult.Invalid -> state.unchanged(
                        TabUnchangedReason.InvalidResultingGraph(candidate.problems),
                    )

                    is TabNavigationStateCreationResult.Valid -> TabReduction.Changed(
                        state = candidate.state,
                        transition = TabTransitionIntent.TabOpened(
                            previousSelectedTabId = state.selectedTabId,
                            openedTabId = action.tabId,
                            previousVisibleTopId = previousVisibleTopId,
                            targetVisibleTopId = candidate.state.selectedTab.history.top.id,
                            targetHistoryTransition = leafReduction.transition,
                        ),
                    )
                }
            }
        }
    }

    private fun replaceGraph(
        state: TabNavigationState,
        action: TabAction.ReplaceGraph,
    ): TabReduction {
        if (state == action.target) {
            return state.unchanged(TabUnchangedReason.AlreadyAtTarget)
        }

        val previousOccurrences = state.ownedEntriesById()
        action.target.tabs.forEach { targetTab ->
            targetTab.history.entries.forEach entryLoop@{ targetEntry ->
                val previous = previousOccurrences[targetEntry.id] ?: return@entryLoop
                if (previous.entry.route != targetEntry.route) {
                    return state.unchanged(
                        TabUnchangedReason.EntryIdentityRebound(
                            entryId = targetEntry.id,
                            previousTabId = previous.tabId,
                            previousRoute = previous.entry.route,
                            targetTabId = targetTab.id,
                            targetRoute = targetEntry.route,
                        ),
                    )
                }
            }
        }

        return TabReduction.Changed(
            state = action.target,
            transition = TabTransitionIntent.GraphReplaced(
                previousSelectedTabId = state.selectedTabId,
                selectedTabId = action.target.selectedTabId,
                previousVisibleTopId = state.selectedTab.history.top.id,
                targetVisibleTopId = action.target.selectedTab.history.top.id,
            ),
        )
    }

    private fun back(
        state: TabNavigationState,
        action: TabAction.Back,
    ): TabReduction {
        val tab = state.guardedTab(action.expectedSelectedTabId, action.expectedTopId)
            ?: return state.guardFailure(action.expectedSelectedTabId, action.expectedTopId)

        if (tab.history.entries.size == 1) {
            return state.unchanged(
                TabUnchangedReason.ActiveTabAtRoot(
                    tabId = tab.id,
                    rootEntryId = tab.history.root.id,
                ),
            )
        }

        val leafAction = if (tab.history.top.route is ModalRoute) {
            NavAction.DismissModal(tab.history.top.id)
        } else {
            NavAction.Pop
        }
        return applyLeafReduction(
            state = state,
            tab = tab,
            reduction = NavReducer.reduce(tab.history, leafAction),
        )
    }

    private fun applyLeafReduction(
        state: TabNavigationState,
        tab: TabState,
        reduction: NavReduction,
    ): TabReduction = when (reduction) {
        is NavReduction.Unchanged -> state.unchanged(
            TabUnchangedReason.NavigationUnchanged(tab.id, reduction.reason),
        )

        is NavReduction.Changed -> when (
            val candidate = state.withHistory(
                tabId = tab.id,
                history = reduction.state,
                selectedTabId = state.selectedTabId,
            )
        ) {
            is TabNavigationStateCreationResult.Invalid -> state.unchanged(
                TabUnchangedReason.InvalidResultingGraph(candidate.problems),
            )

            is TabNavigationStateCreationResult.Valid -> TabReduction.Changed(
                state = candidate.state,
                transition = TabTransitionIntent.NavigationChanged(
                    tabId = tab.id,
                    navigationTransition = reduction.transition,
                ),
            )
        }
    }

    private fun TabNavigationState.guardedTab(
        expectedSelectedTabId: TabId,
        expectedTopId: EntryId,
    ): TabState? {
        val tab = this[expectedSelectedTabId] ?: return null
        if (selectedTabId != expectedSelectedTabId) return null
        if (tab.history.top.id != expectedTopId) return null
        return tab
    }

    private fun TabNavigationState.guardFailure(
        expectedSelectedTabId: TabId,
        expectedTopId: EntryId,
    ): TabReduction.Unchanged {
        val tab = this[expectedSelectedTabId]
            ?: return unchanged(TabUnchangedReason.TabNotFound(expectedSelectedTabId))
        if (selectedTabId != expectedSelectedTabId) {
            return unchanged(
                TabUnchangedReason.SelectedTabChanged(
                    expectedSelectedTabId = expectedSelectedTabId,
                    actualSelectedTabId = selectedTabId,
                ),
            )
        }
        return unchanged(
            TabUnchangedReason.TopEntryChanged(
                tabId = expectedSelectedTabId,
                expectedTopId = expectedTopId,
                actualTopId = tab.history.top.id,
            ),
        )
    }

    private fun TabNavigationState.withHistory(
        tabId: TabId,
        history: NavState,
        selectedTabId: TabId,
    ): TabNavigationStateCreationResult = TabNavigationState.fromTabs(
        selectedTabId = selectedTabId,
        tabs = tabs.map { tab ->
            if (tab.id == tabId) TabState(tab.id, history) else tab
        },
    )

    private fun TabNavigationState.ownedEntriesById(): Map<EntryId, OwnedEntry> = buildMap {
        tabs.forEach { tab ->
            tab.history.entries.forEach { entry ->
                put(entry.id, OwnedEntry(tab.id, entry))
            }
        }
    }

    private fun TabNavigationState.unchanged(
        reason: TabUnchangedReason,
    ): TabReduction.Unchanged = TabReduction.Unchanged(this, reason)
}

private data class OwnedEntry(
    val tabId: TabId,
    val entry: BackStackEntry,
)

private fun immutableProblemListCopy(
    source: Collection<TabNavigationStateProblem>,
): List<TabNavigationStateProblem> = Collections.unmodifiableList(ArrayList(source))
