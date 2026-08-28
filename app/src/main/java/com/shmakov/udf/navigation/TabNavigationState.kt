package com.shmakov.udf.navigation

import java.util.Collections

/** Stable, application-defined identity of one tab in a navigation graph. */
data class TabId(val value: String)

/** One tab and the independent leaf navigation history currently owned by it. */
data class TabState(
    val id: TabId,
    val history: NavState,
)

/** A structural invariant violation found while creating a [TabNavigationState]. */
sealed class TabNavigationStateProblem {
    object EmptyTabs : TabNavigationStateProblem()

    data class BlankTabId(val index: Int) : TabNavigationStateProblem()

    data class DuplicateTabId(
        val tabId: TabId,
        val firstIndex: Int,
        val duplicateIndex: Int,
    ) : TabNavigationStateProblem()

    data class SelectedTabNotFound(val selectedTabId: TabId) : TabNavigationStateProblem()

    /**
     * One route-occurrence identity cannot belong to two tabs in the same graph.
     *
     * Keeping [EntryId] graph-wide makes entry ownership, renderer keys, and saveable-state keys
     * describe the same occurrence without adding a second hidden identity scheme.
     */
    data class DuplicateEntryId(
        val entryId: EntryId,
        val firstTabId: TabId,
        val duplicateTabId: TabId,
        val firstTabIndex: Int,
        val duplicateTabIndex: Int,
    ) : TabNavigationStateProblem()
}

/** Result of validating caller-controlled tabs with [TabNavigationState.fromTabs]. */
sealed class TabNavigationStateCreationResult {
    data class Valid(val state: TabNavigationState) : TabNavigationStateCreationResult()

    data class Invalid(
        val problems: List<TabNavigationStateProblem>,
    ) : TabNavigationStateCreationResult()
}

/**
 * Immutable state of one tab container and all independent navigation histories it owns.
 *
 * [tabs] is ordered deliberately: application UI can render a deterministic tab bar without a
 * separate ordering convention. Labels and icons remain application configuration, not durable
 * navigation state.
 */
class TabNavigationState private constructor(
    val selectedTabId: TabId,
    tabs: List<TabState>,
) {
    /** A defensive, runtime-unmodifiable view in deterministic tab-bar order. */
    val tabs: List<TabState> = immutableTabListCopy(tabs)

    /** The selected tab. Its presence is guaranteed by [create]. */
    val selectedTab: TabState
        get() = checkNotNull(this[selectedTabId]) {
            "TabNavigationState invariant broken: selected tab $selectedTabId is missing"
        }

    /** Returns the tab with [tabId], or `null` when it does not belong to this graph. */
    operator fun get(tabId: TabId): TabState? = tabs.firstOrNull { it.id == tabId }

    /** Encodes the selected tab and every ordered leaf history without Android or UI data. */
    fun toSnapshot(codec: RouteCodec): TabNavigationSnapshotResult<TabNavigationStateSnapshot> =
        snapshotTabNavigationState(this, codec)

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is TabNavigationState &&
            selectedTabId == other.selectedTabId &&
            tabs == other.tabs

    override fun hashCode(): Int = 31 * selectedTabId.hashCode() + tabs.hashCode()

    override fun toString(): String =
        "TabNavigationState(selectedTabId=$selectedTabId, tabs=$tabs)"

    companion object {
        /** Creates a known-valid application graph, throwing on programmer configuration errors. */
        @JvmStatic
        fun create(
            selectedTabId: TabId,
            tabs: List<TabState>,
        ): TabNavigationState = when (val result = fromTabs(selectedTabId, tabs)) {
            is TabNavigationStateCreationResult.Valid -> result.state
            is TabNavigationStateCreationResult.Invalid -> throw IllegalArgumentException(
                "Invalid tab navigation state: ${result.problems.joinToString()}",
            )
        }

        /** Validates caller-controlled tabs supplied by restoration, decoding, or deep links. */
        @JvmStatic
        fun fromTabs(
            selectedTabId: TabId,
            tabs: List<TabState>,
        ): TabNavigationStateCreationResult {
            val tabsCopy = ArrayList(tabs)
            val problems = validateTabNavigationState(selectedTabId, tabsCopy)
            return if (problems.isEmpty()) {
                TabNavigationStateCreationResult.Valid(
                    TabNavigationState(selectedTabId, tabsCopy),
                )
            } else {
                TabNavigationStateCreationResult.Invalid(
                    immutableProblemListCopy(problems),
                )
            }
        }

        /** Restores the complete graph and reuses every leaf and graph invariant validator. */
        @JvmStatic
        fun restore(
            snapshot: TabNavigationStateSnapshot,
            codec: RouteCodec,
        ): TabNavigationSnapshotResult<TabNavigationState> =
            restoreTabNavigationState(snapshot, codec)
    }
}

private fun validateTabNavigationState(
    selectedTabId: TabId,
    tabs: List<TabState>,
): List<TabNavigationStateProblem> {
    if (tabs.isEmpty()) return listOf(TabNavigationStateProblem.EmptyTabs)

    val problems = mutableListOf<TabNavigationStateProblem>()

    tabs.forEachIndexed { index, tab ->
        if (tab.id.value.isBlank()) {
            problems += TabNavigationStateProblem.BlankTabId(index)
        }
    }

    val firstIndexByTabId = mutableMapOf<TabId, Int>()
    val reportedDuplicateTabIds = mutableSetOf<TabId>()
    tabs.forEachIndexed { index, tab ->
        val firstIndex = firstIndexByTabId.putIfAbsent(tab.id, index)
        if (firstIndex != null && reportedDuplicateTabIds.add(tab.id)) {
            problems += TabNavigationStateProblem.DuplicateTabId(
                tabId = tab.id,
                firstIndex = firstIndex,
                duplicateIndex = index,
            )
        }
    }

    if (tabs.none { it.id == selectedTabId }) {
        problems += TabNavigationStateProblem.SelectedTabNotFound(selectedTabId)
    }

    val ownerByEntryId = mutableMapOf<EntryId, EntryOwner>()
    tabs.forEachIndexed { tabIndex, tab ->
        tab.history.entries.forEach { entry ->
            val owner = EntryOwner(tab.id, tabIndex)
            val firstOwner = ownerByEntryId.putIfAbsent(entry.id, owner)
            if (firstOwner != null && firstOwner.tabIndex != tabIndex) {
                problems += TabNavigationStateProblem.DuplicateEntryId(
                    entryId = entry.id,
                    firstTabId = firstOwner.tabId,
                    duplicateTabId = tab.id,
                    firstTabIndex = firstOwner.tabIndex,
                    duplicateTabIndex = tabIndex,
                )
            }
        }
    }

    return problems
}

private data class EntryOwner(
    val tabId: TabId,
    val tabIndex: Int,
)

private fun immutableTabListCopy(source: Collection<TabState>): List<TabState> =
    Collections.unmodifiableList(ArrayList(source))

private fun immutableProblemListCopy(
    source: Collection<TabNavigationStateProblem>,
): List<TabNavigationStateProblem> = Collections.unmodifiableList(ArrayList(source))
