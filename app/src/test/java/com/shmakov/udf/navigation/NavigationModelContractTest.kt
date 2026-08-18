package com.shmakov.udf.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationModelContractTest {

    @Test
    fun `demo route equality contains semantic data only`() {
        assertEquals(Home, Home)
        assertEquals(Accounts, Accounts)
        assertEquals(Account(accountId = 7), Account(accountId = 7))
        assertEquals(AccountDetails(accountId = 7), AccountDetails(accountId = 7))
        assertEquals(Transactions, Transactions)
        assertEquals(Transaction(transactionId = 7), Transaction(transactionId = 7))
        assertEquals(Cards, Cards)
        assertEquals(Card(cardId = 7), Card(cardId = 7))

        assertNotEquals(Account(accountId = 7), Account(accountId = 8))
        assertNotEquals(AccountDetails(accountId = 7), AccountDetails(accountId = 8))
        assertNotEquals(Transaction(transactionId = 7), Transaction(transactionId = 8))
        assertNotEquals(Card(cardId = 7), Card(cardId = 8))
    }

    @Test
    fun `route interfaces are open for application routes`() {
        val content: Route = ConsumerContentRoute(key = "inbox")
        val modal: Route = ConsumerModalRoute(key = "filters")

        assertTrue(content is ContentRoute)
        assertTrue(modal is ModalRoute)
    }

    @Test
    fun `duplicate routes remain distinct entries`() {
        val first = BackStackEntry(
            id = EntryId("account-details-1"),
            route = AccountDetails(accountId = 42),
        )
        val second = BackStackEntry(
            id = EntryId("account-details-2"),
            route = AccountDetails(accountId = 42),
        )

        assertEquals(first.route, second.route)
        assertNotEquals(first.id, second.id)
        assertNotEquals(first, second)
    }

    @Test
    fun `startAt generates identity and creates a root-only state`() {
        val state = NavState.startAt(Home)
        val anotherState = NavState.startAt(Home)

        assertEquals(Home, state.root.route)
        assertEquals(listOf(state.root), state.entries)
        assertEquals(state.root, state.top)
        assertTrue(state.root.id.value.isNotBlank())
        assertNotEquals(state.root.id, anotherState.root.id)
    }

    @Test
    fun `fromEntries preserves caller-controlled root top and entry order`() {
        val entries = listOf(
            BackStackEntry(EntryId("home"), Home),
            BackStackEntry(EntryId("accounts"), Accounts),
            BackStackEntry(EntryId("account-42"), Account(accountId = 42)),
        )

        val state = validState(NavState.fromEntries(entries))

        assertEquals(entries, state.entries)
        assertEquals(entries.first(), state.root)
        assertEquals(entries.last(), state.top)
    }

    @Test
    fun `states with equal histories are structurally equal`() {
        val entries = listOf(
            BackStackEntry(EntryId("home"), Home),
            BackStackEntry(EntryId("accounts"), Accounts),
        )

        val first = validState(NavState.fromEntries(entries))
        val second = validState(NavState.fromEntries(entries.toList()))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `history factory keeps repeated routes as distinct entries`() {
        val state = NavState.history(
            root = Home,
            AccountDetails(accountId = 42),
            AccountDetails(accountId = 42),
        )

        assertEquals(state.entries[1].route, state.entries[2].route)
        assertNotEquals(state.entries[1].id, state.entries[2].id)
    }

    @Test
    fun `state owns an unmodifiable defensive copy of entries`() {
        val source = mutableListOf(
            BackStackEntry(EntryId("home"), Home),
            BackStackEntry(EntryId("accounts"), Accounts),
        )
        val state = validState(NavState.fromEntries(source))

        source.clear()

        assertEquals(listOf(Home, Accounts), state.entries.map(BackStackEntry::route))
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (state.entries as MutableList<BackStackEntry>).add(
                BackStackEntry(EntryId("cards"), Cards),
            )
        }
    }

    @Test
    fun `empty history is rejected explicitly`() {
        val problems = invalidProblems(NavState.fromEntries(emptyList()))

        assertEquals(listOf(NavStateProblem.EmptyStack), problems)
    }

    @Test
    fun `blank entry ID is rejected by shared state validation`() {
        val problems = invalidProblems(
            NavState.fromEntries(
                listOf(
                    BackStackEntry(EntryId("home"), Home),
                    BackStackEntry(EntryId("  "), Accounts),
                ),
            ),
        )

        assertEquals(listOf(NavStateProblem.BlankEntryId(index = 1)), problems)
    }

    @Test
    fun `modal root is rejected explicitly`() {
        val modalRoot = BackStackEntry(
            id = EntryId("account-42"),
            route = Account(accountId = 42),
        )

        val problems = invalidProblems(NavState.fromEntries(listOf(modalRoot)))

        assertEquals(
            listOf(NavStateProblem.NonContentRoot(entryId = modalRoot.id)),
            problems,
        )
    }

    @Test
    fun `duplicate entry IDs are rejected explicitly`() {
        val duplicateId = EntryId("duplicate")
        val entries = listOf(
            BackStackEntry(duplicateId, Home),
            BackStackEntry(duplicateId, Accounts),
        )

        val problems = invalidProblems(NavState.fromEntries(entries))

        assertEquals(
            listOf(NavStateProblem.DuplicateEntryId(duplicateId)),
            problems,
        )
    }

    @Test
    fun `route without a kind is rejected by fromEntries`() {
        val invalidEntry = BackStackEntry(EntryId("missing-kind"), MissingKindRoute)

        val problems = invalidProblems(
            NavState.fromEntries(
                listOf(BackStackEntry(EntryId("home"), Home), invalidEntry),
            ),
        )

        assertEquals(
            listOf(NavStateProblem.MissingRouteKind(invalidEntry.id)),
            problems,
        )
    }

    @Test
    fun `route with both kinds is rejected by fromEntries`() {
        val invalidEntry = BackStackEntry(EntryId("ambiguous-kind"), AmbiguousKindRoute)

        val problems = invalidProblems(
            NavState.fromEntries(
                listOf(BackStackEntry(EntryId("home"), Home), invalidEntry),
            ),
        )

        assertEquals(
            listOf(NavStateProblem.AmbiguousRouteKind(invalidEntry.id)),
            problems,
        )
    }

    @Test
    fun `history rejects a route without a kind as programmer misuse`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NavState.history(Home, MissingKindRoute)
        }

        assertTrue(error.message.orEmpty().contains("MissingRouteKind"))
    }

    @Test
    fun `history rejects a route with both kinds as programmer misuse`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NavState.history(Home, AmbiguousKindRoute)
        }

        assertTrue(error.message.orEmpty().contains("AmbiguousRouteKind"))
    }

    private fun validState(result: NavStateCreationResult): NavState = when (result) {
        is NavStateCreationResult.Valid -> result.state
        is NavStateCreationResult.Invalid -> throw AssertionError(
            "Expected valid state, got ${result.problems}",
        )
    }

    private fun invalidProblems(
        result: NavStateCreationResult,
    ): List<NavStateProblem> = when (result) {
        is NavStateCreationResult.Valid -> throw AssertionError(
            "Expected invalid state, got ${result.state}",
        )
        is NavStateCreationResult.Invalid -> result.problems
    }

    private data class ConsumerContentRoute(val key: String) : ContentRoute

    private data class ConsumerModalRoute(val key: String) : ModalRoute

    private object MissingKindRoute : Route

    private object AmbiguousKindRoute : ContentRoute, ModalRoute
}
