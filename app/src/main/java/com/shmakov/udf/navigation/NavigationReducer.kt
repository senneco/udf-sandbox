package com.shmakov.udf.navigation

import java.util.Collections

/** A fully materialized navigation operation consumed by [NavReducer]. */
sealed class NavAction {

    data class Push(
        val expectedTopId: EntryId,
        val entry: BackStackEntry,
    ) : NavAction()

    object Pop : NavAction()

    data class NavigateFrom(
        val sourceId: EntryId,
        val entry: BackStackEntry,
    ) : NavAction()

    data class DismissModal(
        val entryId: EntryId,
    ) : NavAction()

    data class ReplaceHistory(
        val target: NavState,
    ) : NavAction()

    companion object {
        /** Materializes a new entry before reduction. */
        @JvmStatic
        fun push(expectedTopId: EntryId, route: Route): Push =
            Push(expectedTopId, BackStackEntry.create(route))

        /** Uses a caller-supplied identity, which is useful for replay and tests. */
        @JvmStatic
        fun push(expectedTopId: EntryId, entry: BackStackEntry): Push =
            Push(expectedTopId, entry)

        /** Materializes a new entry before reduction. */
        @JvmStatic
        fun navigateFrom(sourceId: EntryId, route: Route): NavigateFrom =
            NavigateFrom(sourceId, BackStackEntry.create(route))

        /** Uses a caller-supplied identity, which is useful for replay and tests. */
        @JvmStatic
        fun navigateFrom(sourceId: EntryId, entry: BackStackEntry): NavigateFrom =
            NavigateFrom(sourceId, entry)

        @JvmStatic
        fun pop(): Pop = Pop

        @JvmStatic
        fun dismissModal(entryId: EntryId): DismissModal = DismissModal(entryId)

        @JvmStatic
        fun replaceHistory(target: NavState): ReplaceHistory = ReplaceHistory(target)

        /** Creates a fresh root entry before reduction and replaces the complete history. */
        @JvmStatic
        fun resetTo(rootRoute: ContentRoute): ReplaceHistory =
            ReplaceHistory(NavState.startAt(rootRoute))
    }
}

/** Semantic description of one applied navigation change. It is not durable state. */
sealed class NavTransitionIntent {

    data class Pushed(
        val fromEntryId: EntryId,
        val addedEntryId: EntryId,
    ) : NavTransitionIntent()

    data class Popped(
        val removedEntryId: EntryId,
        val revealedEntryId: EntryId,
    ) : NavTransitionIntent()

    class BranchReplaced(
        val sourceEntryId: EntryId,
        removedEntryIds: List<EntryId>,
        val addedEntryId: EntryId,
    ) : NavTransitionIntent() {
        val removedEntryIds: List<EntryId> = immutableListCopy(removedEntryIds)

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is BranchReplaced &&
                sourceEntryId == other.sourceEntryId &&
                removedEntryIds == other.removedEntryIds &&
                addedEntryId == other.addedEntryId

        override fun hashCode(): Int {
            var result = sourceEntryId.hashCode()
            result = 31 * result + removedEntryIds.hashCode()
            result = 31 * result + addedEntryId.hashCode()
            return result
        }

        override fun toString(): String =
            "BranchReplaced(sourceEntryId=$sourceEntryId, " +
                "removedEntryIds=$removedEntryIds, addedEntryId=$addedEntryId)"
    }

    data class ModalDismissed(
        val entryId: EntryId,
    ) : NavTransitionIntent()

    data class HistoryReplaced(
        val previousTopEntryId: EntryId,
        val targetTopEntryId: EntryId,
    ) : NavTransitionIntent()
}

/** The deterministic reason why a valid action left navigation state unchanged. */
sealed class NavUnchangedReason {

    data class EntryNotFound(
        val entryId: EntryId,
    ) : NavUnchangedReason()

    data class SourceIsNotTop(
        val expectedTopId: EntryId,
        val actualTopId: EntryId,
    ) : NavUnchangedReason()

    data class EntryIdAlreadyExists(
        val entryId: EntryId,
    ) : NavUnchangedReason()

    class InvalidResultingState(
        problems: List<NavStateProblem>,
    ) : NavUnchangedReason() {
        val problems: List<NavStateProblem> = immutableListCopy(problems)

        override fun equals(other: Any?): Boolean =
            this === other || other is InvalidResultingState && problems == other.problems

        override fun hashCode(): Int = problems.hashCode()

        override fun toString(): String = "InvalidResultingState(problems=$problems)"
    }

    object RootProtected : NavUnchangedReason()

    data class EntryIsNotModal(
        val entryId: EntryId,
    ) : NavUnchangedReason()

    object AlreadyAtTarget : NavUnchangedReason()

    data class EntryIdentityRebound(
        val entryId: EntryId,
        val previousRoute: Route,
        val targetRoute: Route,
    ) : NavUnchangedReason()
}

/** Result of reducing one [NavAction]. */
sealed class NavReduction {
    abstract val state: NavState

    data class Changed(
        override val state: NavState,
        val transition: NavTransitionIntent,
    ) : NavReduction()

    data class Unchanged(
        override val state: NavState,
        val reason: NavUnchangedReason,
    ) : NavReduction()
}

/** Pure, deterministic navigation-state reducer. */
object NavReducer {

    @JvmStatic
    fun reduce(state: NavState, action: NavAction): NavReduction = when (action) {
        is NavAction.Push -> push(state, action)
        NavAction.Pop -> pop(state)
        is NavAction.NavigateFrom -> navigateFrom(state, action)
        is NavAction.DismissModal -> dismissModal(state, action)
        is NavAction.ReplaceHistory -> replaceHistory(state, action)
    }

    private fun push(state: NavState, action: NavAction.Push): NavReduction {
        val expectedTopIndex = state.entries.indexOfFirst { it.id == action.expectedTopId }
        if (expectedTopIndex == -1) {
            return state.unchanged(NavUnchangedReason.EntryNotFound(action.expectedTopId))
        }
        if (expectedTopIndex != state.entries.lastIndex) {
            return state.unchanged(
                NavUnchangedReason.SourceIsNotTop(
                    expectedTopId = action.expectedTopId,
                    actualTopId = state.top.id,
                ),
            )
        }
        if (state.contains(action.entry.id)) {
            return state.unchanged(NavUnchangedReason.EntryIdAlreadyExists(action.entry.id))
        }

        return reduceEntries(
            previousState = state,
            entries = state.entries + action.entry,
            transition = NavTransitionIntent.Pushed(
                fromEntryId = action.expectedTopId,
                addedEntryId = action.entry.id,
            ),
        )
    }

    private fun pop(state: NavState): NavReduction {
        if (state.entries.size == 1) {
            return state.unchanged(NavUnchangedReason.RootProtected)
        }

        val removedEntry = state.top
        val remainingEntries = state.entries.dropLast(1)
        return reduceEntries(
            previousState = state,
            entries = remainingEntries,
            transition = NavTransitionIntent.Popped(
                removedEntryId = removedEntry.id,
                revealedEntryId = remainingEntries.last().id,
            ),
        )
    }

    private fun navigateFrom(
        state: NavState,
        action: NavAction.NavigateFrom,
    ): NavReduction {
        val sourceIndex = state.entries.indexOfFirst { it.id == action.sourceId }
        if (sourceIndex == -1) {
            return state.unchanged(NavUnchangedReason.EntryNotFound(action.sourceId))
        }
        if (state.contains(action.entry.id)) {
            return state.unchanged(NavUnchangedReason.EntryIdAlreadyExists(action.entry.id))
        }

        val removedEntries = state.entries.drop(sourceIndex + 1)
        val transition = if (removedEntries.isEmpty()) {
            NavTransitionIntent.Pushed(
                fromEntryId = action.sourceId,
                addedEntryId = action.entry.id,
            )
        } else {
            NavTransitionIntent.BranchReplaced(
                sourceEntryId = action.sourceId,
                removedEntryIds = removedEntries.map { it.id },
                addedEntryId = action.entry.id,
            )
        }

        return reduceEntries(
            previousState = state,
            entries = state.entries.take(sourceIndex + 1) + action.entry,
            transition = transition,
        )
    }

    private fun dismissModal(
        state: NavState,
        action: NavAction.DismissModal,
    ): NavReduction {
        val modalIndex = state.entries.indexOfFirst { it.id == action.entryId }
        if (modalIndex == -1) {
            return state.unchanged(NavUnchangedReason.EntryNotFound(action.entryId))
        }
        if (state.entries[modalIndex].route !is ModalRoute) {
            return state.unchanged(NavUnchangedReason.EntryIsNotModal(action.entryId))
        }

        return reduceEntries(
            previousState = state,
            entries = state.entries.filterIndexed { index, _ -> index != modalIndex },
            transition = NavTransitionIntent.ModalDismissed(action.entryId),
        )
    }

    private fun replaceHistory(
        state: NavState,
        action: NavAction.ReplaceHistory,
    ): NavReduction {
        if (state == action.target) {
            return state.unchanged(NavUnchangedReason.AlreadyAtTarget)
        }

        val previousEntriesById = state.entries.associateBy { it.id }
        action.target.entries.forEach { targetEntry ->
            val previousEntry = previousEntriesById[targetEntry.id] ?: return@forEach
            if (previousEntry.route != targetEntry.route) {
                return state.unchanged(
                    NavUnchangedReason.EntryIdentityRebound(
                        entryId = targetEntry.id,
                        previousRoute = previousEntry.route,
                        targetRoute = targetEntry.route,
                    ),
                )
            }
        }

        return NavReduction.Changed(
            state = action.target,
            transition = NavTransitionIntent.HistoryReplaced(
                previousTopEntryId = state.top.id,
                targetTopEntryId = action.target.top.id,
            ),
        )
    }

    private fun reduceEntries(
        previousState: NavState,
        entries: List<BackStackEntry>,
        transition: NavTransitionIntent,
    ): NavReduction = when (val result = NavState.fromEntries(entries)) {
        is NavStateCreationResult.Valid -> NavReduction.Changed(
            state = result.state,
            transition = transition,
        )
        is NavStateCreationResult.Invalid -> previousState.unchanged(
            NavUnchangedReason.InvalidResultingState(result.problems),
        )
    }

    private fun NavState.contains(entryId: EntryId): Boolean =
        entries.any { it.id == entryId }

    private fun NavState.unchanged(reason: NavUnchangedReason): NavReduction.Unchanged =
        NavReduction.Unchanged(state = this, reason = reason)
}

private fun <T> immutableListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))
