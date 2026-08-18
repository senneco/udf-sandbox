package com.shmakov.udf.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationReducerContractTest {

    @Test
    fun `action factories materialize identity before reduction`() {
        val push = NavAction.push(EntryId("home"), Accounts)
        val anotherPush = NavAction.push(EntryId("home"), Accounts)
        val navigate = NavAction.navigateFrom(EntryId("home"), Transactions)
        val target = state(entry("signed-out", SignedOut))

        assertEquals(EntryId("home"), push.expectedTopId)
        assertEquals(Accounts, push.entry.route)
        assertTrue(push.entry.id.value.isNotBlank())
        assertNotEquals(push.entry.id, anotherPush.entry.id)
        assertEquals(EntryId("home"), navigate.sourceId)
        assertEquals(Transactions, navigate.entry.route)
        assertEquals(NavAction.Pop, NavAction.pop())
        assertEquals(
            NavAction.DismissModal(EntryId("modal")),
            NavAction.dismissModal(EntryId("modal")),
        )
        assertEquals(NavAction.ReplaceHistory(target), NavAction.replaceHistory(target))
        assertEquals(SignedOut, NavAction.resetTo(SignedOut).target.root.route)
    }

    @Test
    fun `push appends supplied content entry and reports exact intent`() {
        val initial = state(entry("home", Home))
        val accounts = entry("accounts", Accounts)

        val result = changed(
            NavReducer.reduce(
                initial,
                NavAction.Push(expectedTopId = initial.top.id, entry = accounts),
            ),
        )

        assertEquals(listOf(initial.root, accounts), result.state.entries)
        assertEquals(
            NavTransitionIntent.Pushed(
                fromEntryId = initial.root.id,
                addedEntryId = accounts.id,
            ),
            result.transition,
        )
    }

    @Test
    fun `push accepts modal content after modal and repeated routes with distinct IDs`() {
        val first = entry("account-1", Account(42))
        val initial = state(entry("home", Home), first)
        val second = entry("account-2", Account(42))
        val details = entry("details", AccountDetails(42))

        val withSecond = changed(
            NavReducer.reduce(
                initial,
                NavAction.Push(expectedTopId = first.id, entry = second),
            ),
        ).state
        val withDetails = changed(
            NavReducer.reduce(
                withSecond,
                NavAction.Push(expectedTopId = second.id, entry = details),
            ),
        ).state

        assertEquals(
            listOf(Home, Account(42), Account(42), AccountDetails(42)),
            withDetails.entries.map(BackStackEntry::route),
        )
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `push with missing or non-top source is a typed no-op`() {
        val initial = state(entry("home", Home), entry("accounts", Accounts))

        val missing = unchanged(
            NavReducer.reduce(
                initial,
                NavAction.Push(EntryId("stale"), entry("details", AccountDetails(1))),
            ),
        )
        val nonTop = unchanged(
            NavReducer.reduce(
                initial,
                NavAction.Push(initial.root.id, entry("details-2", AccountDetails(2))),
            ),
        )

        assertSame(initial, missing.state)
        assertEquals(
            NavUnchangedReason.EntryNotFound(EntryId("stale")),
            missing.reason,
        )
        assertSame(initial, nonTop.state)
        assertEquals(
            NavUnchangedReason.SourceIsNotTop(
                expectedTopId = initial.root.id,
                actualTopId = initial.top.id,
            ),
            nonTop.reason,
        )
    }

    @Test
    fun `push rejects an ID already present in history`() {
        val initial = state(entry("home", Home), entry("accounts", Accounts))

        val result = unchanged(
            NavReducer.reduce(
                initial,
                NavAction.Push(
                    expectedTopId = initial.top.id,
                    entry = entry("home", AccountDetails(1)),
                ),
            ),
        )

        assertSame(initial, result.state)
        assertEquals(
            NavUnchangedReason.EntryIdAlreadyExists(EntryId("home")),
            result.reason,
        )
    }

    @Test
    fun `push returns every invalid resulting-state problem without throwing`() {
        val initial = state(entry("home", Home))
        val invalidEntries = listOf(
            entry("  ", Accounts) to listOf(NavStateProblem.BlankEntryId(index = 1)),
            entry("missing-kind", MissingKindRoute) to listOf(
                NavStateProblem.MissingRouteKind(EntryId("missing-kind")),
            ),
            entry("ambiguous-kind", AmbiguousKindRoute) to listOf(
                NavStateProblem.AmbiguousRouteKind(EntryId("ambiguous-kind")),
            ),
        )

        invalidEntries.forEach { (invalidEntry, expectedProblems) ->
            val result = unchanged(
                NavReducer.reduce(
                    initial,
                    NavAction.Push(initial.top.id, invalidEntry),
                ),
            )

            assertSame(initial, result.state)
            assertEquals(
                NavUnchangedReason.InvalidResultingState(expectedProblems),
                result.reason,
            )
        }
    }

    @Test
    fun `pop removes exactly one content or modal top`() {
        val histories = listOf(
            state(entry("home", Home), entry("accounts", Accounts)),
            state(entry("home", Home), entry("account", Account(1))),
        )

        histories.forEach { initial ->
            val removed = initial.top
            val result = changed(NavReducer.reduce(initial, NavAction.Pop))

            assertEquals(listOf(initial.root), result.state.entries)
            assertEquals(
                NavTransitionIntent.Popped(
                    removedEntryId = removed.id,
                    revealedEntryId = initial.root.id,
                ),
                result.transition,
            )
        }
    }

    @Test
    fun `pop protects root and returns the same state instance`() {
        val initial = state(entry("home", Home))

        val result = unchanged(NavReducer.reduce(initial, NavAction.Pop))

        assertSame(initial, result.state)
        assertEquals(NavUnchangedReason.RootProtected, result.reason)
    }

    @Test
    fun `rapid pops deterministically stop at root`() {
        val initial = state(
            entry("home", Home),
            entry("accounts", Accounts),
            entry("account", Account(1)),
        )

        val reductions = generateSequence<NavReduction>(
            NavReducer.reduce(initial, NavAction.Pop),
        ) { previous -> NavReducer.reduce(previous.state, NavAction.Pop) }
            .take(5)
            .toList()

        assertEquals(listOf(Home), reductions.last().state.entries.map(BackStackEntry::route))
        assertTrue(reductions.take(2).all { it is NavReduction.Changed })
        assertTrue(reductions.drop(2).all { it is NavReduction.Unchanged })
        assertSame(reductions[1].state, reductions.last().state)
    }

    @Test
    fun `navigateFrom removes every descendant and reports their IDs in order`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(1))
        val initial = state(home, accounts, account)
        val transactions = entry("transactions", Transactions)

        val result = changed(
            NavReducer.reduce(
                initial,
                NavAction.NavigateFrom(home.id, transactions),
            ),
        )

        assertEquals(listOf(home, transactions), result.state.entries)
        assertEquals(
            NavTransitionIntent.BranchReplaced(
                sourceEntryId = home.id,
                removedEntryIds = listOf(accounts.id, account.id),
                addedEntryId = transactions.id,
            ),
            result.transition,
        )
    }

    @Test
    fun `navigateFrom top is a guarded push`() {
        val initial = state(entry("home", Home), entry("accounts", Accounts))
        val account = entry("account", Account(1))

        val result = changed(
            NavReducer.reduce(
                initial,
                NavAction.NavigateFrom(initial.top.id, account),
            ),
        )

        assertEquals(initial.entries + account, result.state.entries)
        assertEquals(
            NavTransitionIntent.Pushed(initial.top.id, account.id),
            result.transition,
        )
    }

    @Test
    fun `navigateFrom selects a duplicate route occurrence by ID`() {
        val firstAccounts = entry("accounts-1", Accounts)
        val secondAccounts = entry("accounts-2", Accounts)
        val initial = state(
            entry("home", Home),
            firstAccounts,
            entry("account", Account(1)),
            secondAccounts,
            entry("details", AccountDetails(1)),
        )
        val cards = entry("cards", Cards)

        val result = changed(
            NavReducer.reduce(
                initial,
                NavAction.NavigateFrom(secondAccounts.id, cards),
            ),
        )

        assertEquals(
            listOf(Home, Accounts, Account(1), Accounts, Cards),
            result.state.entries.map(BackStackEntry::route),
        )
        assertEquals(firstAccounts.id, result.state.entries[1].id)
        assertEquals(secondAccounts.id, result.state.entries[3].id)
    }

    @Test
    fun `navigateFrom permits a modal anchor`() {
        val modal = entry("account", Account(1))
        val initial = state(entry("home", Home), modal, entry("old-details", AccountDetails(1)))
        val newDetails = entry("new-details", AccountDetails(2))

        val result = changed(
            NavReducer.reduce(initial, NavAction.NavigateFrom(modal.id, newDetails)),
        )

        assertEquals(listOf(initial.root, modal, newDetails), result.state.entries)
    }

    @Test
    fun `navigateFrom missing source is a typed no-op`() {
        val initial = state(entry("home", Home), entry("accounts", Accounts))
        val staleId = EntryId("stale")

        val result = unchanged(
            NavReducer.reduce(
                initial,
                NavAction.NavigateFrom(staleId, entry("cards", Cards)),
            ),
        )

        assertSame(initial, result.state)
        assertEquals(NavUnchangedReason.EntryNotFound(staleId), result.reason)
    }

    @Test
    fun `navigateFrom rejects an ID present even in the removed suffix`() {
        val home = entry("home", Home)
        val suffixId = EntryId("accounts")
        val initial = state(home, BackStackEntry(suffixId, Accounts))

        val result = unchanged(
            NavReducer.reduce(
                initial,
                NavAction.NavigateFrom(
                    sourceId = home.id,
                    entry = BackStackEntry(suffixId, Transactions),
                ),
            ),
        )

        assertSame(initial, result.state)
        assertEquals(NavUnchangedReason.EntryIdAlreadyExists(suffixId), result.reason)
    }

    @Test
    fun `navigateFrom validates every replacement entry without throwing`() {
        val home = entry("home", Home)
        val initial = state(home, entry("accounts", Accounts))
        val invalidEntries = listOf(
            entry(" ", Transactions) to listOf(NavStateProblem.BlankEntryId(index = 1)),
            entry("missing-kind", MissingKindRoute) to listOf(
                NavStateProblem.MissingRouteKind(EntryId("missing-kind")),
            ),
            entry("ambiguous-kind", AmbiguousKindRoute) to listOf(
                NavStateProblem.AmbiguousRouteKind(EntryId("ambiguous-kind")),
            ),
        )

        invalidEntries.forEach { (invalidEntry, expectedProblems) ->
            val result = unchanged(
                NavReducer.reduce(
                    initial,
                    NavAction.NavigateFrom(home.id, invalidEntry),
                ),
            )

            assertSame(initial, result.state)
            assertEquals(
                NavUnchangedReason.InvalidResultingState(expectedProblems),
                result.reason,
            )
        }
    }

    @Test
    fun `dismissModal removes exactly a top modal by identity`() {
        val modal = entry("account", Account(1))
        val initial = state(entry("home", Home), entry("accounts", Accounts), modal)

        val result = changed(
            NavReducer.reduce(initial, NavAction.DismissModal(modal.id)),
        )

        assertEquals(initial.entries.dropLast(1), result.state.entries)
        assertEquals(NavTransitionIntent.ModalDismissed(modal.id), result.transition)
    }

    @Test
    fun `dismissModal removes only the addressed middle modal`() {
        val first = entry("account-1", Account(1))
        val second = entry("account-2", Account(2))
        val details = entry("details", AccountDetails(2))
        val initial = state(entry("home", Home), first, second, details)

        val result = changed(
            NavReducer.reduce(initial, NavAction.DismissModal(first.id)),
        )

        assertEquals(listOf(initial.root, second, details), result.state.entries)
        assertEquals(NavTransitionIntent.ModalDismissed(first.id), result.transition)
    }

    @Test
    fun `dismissModal distinguishes duplicate routes by entry ID`() {
        val first = entry("account-1", Account(42))
        val second = entry("account-2", Account(42))
        val initial = state(entry("home", Home), first, second)

        val result = changed(
            NavReducer.reduce(initial, NavAction.DismissModal(first.id)),
        )

        assertEquals(listOf(initial.root, second), result.state.entries)
    }

    @Test
    fun `dismissModal missing or content entry is a typed no-op`() {
        val initial = state(entry("home", Home), entry("accounts", Accounts))
        val stale = EntryId("stale")

        val missing = unchanged(
            NavReducer.reduce(initial, NavAction.DismissModal(stale)),
        )
        val content = unchanged(
            NavReducer.reduce(initial, NavAction.DismissModal(initial.top.id)),
        )

        assertSame(initial, missing.state)
        assertEquals(NavUnchangedReason.EntryNotFound(stale), missing.reason)
        assertSame(initial, content.state)
        assertEquals(
            NavUnchangedReason.EntryIsNotModal(initial.top.id),
            content.reason,
        )
    }

    @Test
    fun `repeated modal dismiss is idempotent`() {
        val modal = entry("account", Account(1))
        val initial = state(entry("home", Home), modal)
        val first = changed(
            NavReducer.reduce(initial, NavAction.DismissModal(modal.id)),
        )
        val second = unchanged(
            NavReducer.reduce(first.state, NavAction.DismissModal(modal.id)),
        )

        assertEquals(listOf(Home), first.state.entries.map(BackStackEntry::route))
        assertSame(first.state, second.state)
        assertEquals(NavUnchangedReason.EntryNotFound(modal.id), second.reason)
    }

    @Test
    fun `replaceHistory atomically swaps logout and deep-link histories`() {
        val initial = state(
            entry("home", Home),
            entry("accounts", Accounts),
            entry("account", Account(1)),
        )
        val logout = state(entry("signed-out", SignedOut))
        val deepLink = state(
            entry("deep-home", Home),
            entry("deep-accounts", Accounts),
            entry("deep-account", Account(42)),
            entry("deep-details", AccountDetails(42)),
        )

        listOf(logout, deepLink).forEach { target ->
            val result = changed(
                NavReducer.reduce(initial, NavAction.ReplaceHistory(target)),
            )

            assertSame(target, result.state)
            assertEquals(
                NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = initial.top.id,
                    targetTopEntryId = target.top.id,
                ),
                result.transition,
            )
        }
    }

    @Test
    fun `replaceHistory equal target is a typed no-op`() {
        val initial = state(entry("home", Home), entry("accounts", Accounts))
        val equalTarget = state(*initial.entries.toTypedArray())

        val result = unchanged(
            NavReducer.reduce(initial, NavAction.ReplaceHistory(equalTarget)),
        )

        assertSame(initial, result.state)
        assertEquals(NavUnchangedReason.AlreadyAtTarget, result.reason)
    }

    @Test
    fun `replaceHistory may preserve identity for the same semantic route`() {
        val sharedRoot = entry("shared-home", Home)
        val initial = state(sharedRoot, entry("old-accounts", Accounts))
        val target = state(sharedRoot, entry("new-transactions", Transactions))

        val result = changed(
            NavReducer.reduce(initial, NavAction.ReplaceHistory(target)),
        )

        assertSame(target, result.state)
        assertEquals(sharedRoot, result.state.root)
        assertEquals(
            NavTransitionIntent.HistoryReplaced(
                previousTopEntryId = initial.top.id,
                targetTopEntryId = target.top.id,
            ),
            result.transition,
        )
    }

    @Test
    fun `replaceHistory rejects rebinding a stable ID to another route`() {
        val sharedId = EntryId("shared")
        val initial = state(BackStackEntry(sharedId, Home))
        val target = state(BackStackEntry(sharedId, SignedOut))

        val result = unchanged(
            NavReducer.reduce(initial, NavAction.ReplaceHistory(target)),
        )

        assertSame(initial, result.state)
        assertEquals(
            NavUnchangedReason.EntryIdentityRebound(
                entryId = sharedId,
                previousRoute = Home,
                targetRoute = SignedOut,
            ),
            result.reason,
        )
    }

    @Test
    fun `the same materialized action sequence produces the same reductions`() {
        val initial = state(entry("home", Home))
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(1))
        val actions = listOf(
            NavAction.Push(initial.top.id, accounts),
            NavAction.NavigateFrom(accounts.id, account),
            NavAction.DismissModal(account.id),
            NavAction.Pop,
            NavAction.Pop,
        )

        val first = reduceAll(initial, actions)
        val second = reduceAll(initial, actions)

        assertEquals(first, second)
        assertEquals(listOf(Home), first.last().state.entries.map(BackStackEntry::route))
        assertTrue(first.last() is NavReduction.Unchanged)
    }

    @Test
    fun `replaying the same push action cannot duplicate its entry ID`() {
        val initial = state(entry("home", Home))
        val action = NavAction.Push(initial.top.id, entry("accounts", Accounts))
        val first = changed(NavReducer.reduce(initial, action))

        val replay = unchanged(
            NavReducer.reduce(
                first.state,
                action.copy(expectedTopId = first.state.top.id),
            ),
        )

        assertSame(first.state, replay.state)
        assertEquals(
            NavUnchangedReason.EntryIdAlreadyExists(action.entry.id),
            replay.reason,
        )
    }

    @Test
    fun `snapshot of reduced state contains no transition intent`() {
        val initial = state(entry("home", Home))
        val action = NavAction.Push(initial.top.id, entry("accounts", Accounts))
        val result = changed(NavReducer.reduce(initial, action))

        assertEquals(
            result.state.toSnapshot(DemoRouteCodec),
            state(entry("home", Home), entry("accounts", Accounts))
                .toSnapshot(DemoRouteCodec),
        )
    }

    private fun reduceAll(
        initial: NavState,
        actions: List<NavAction>,
    ): List<NavReduction> {
        var current = initial
        return actions.map { action ->
            NavReducer.reduce(current, action).also { current = it.state }
        }
    }

    private fun changed(result: NavReduction): NavReduction.Changed = when (result) {
        is NavReduction.Changed -> result
        is NavReduction.Unchanged -> throw AssertionError(
            "Expected state change, got ${result.reason}",
        )
    }

    private fun unchanged(result: NavReduction): NavReduction.Unchanged = when (result) {
        is NavReduction.Changed -> throw AssertionError(
            "Expected no-op, got ${result.transition}",
        )
        is NavReduction.Unchanged -> result
    }

    private fun state(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> throw AssertionError(
                "Invalid test fixture: ${result.problems}",
            )
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private object SignedOut : ContentRoute

    private object MissingKindRoute : Route

    private object AmbiguousKindRoute : ContentRoute, ModalRoute
}
