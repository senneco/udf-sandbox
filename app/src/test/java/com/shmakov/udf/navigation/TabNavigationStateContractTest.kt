package com.shmakov.udf.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TabNavigationStateContractTest {

    @Test
    fun `valid graph exposes selected tab and preserves every independent exact history`() {
        val accountsNavigation = navigation(
            BackStackEntry(EntryId("accounts"), Accounts),
            BackStackEntry(EntryId("account-1"), Account(accountId = 1)),
            BackStackEntry(EntryId("account-details-1"), AccountDetails(accountId = 1)),
        )
        val cardsNavigation = navigation(
            BackStackEntry(EntryId("cards"), Cards),
            BackStackEntry(EntryId("card-7"), Card(cardId = 7)),
        )
        val accounts = TabState(TabId("accounts"), accountsNavigation)
        val cards = TabState(TabId("cards"), cardsNavigation)

        val graph = TabNavigationState.create(
            selectedTabId = accounts.id,
            tabs = listOf(accounts, cards),
        )

        assertEquals(accounts.id, graph.selectedTabId)
        assertSame(accounts, graph.selectedTab)
        assertEquals(listOf(accounts, cards), graph.tabs)
        assertSame(accountsNavigation, graph[accounts.id]?.history)
        assertSame(cardsNavigation, graph[cards.id]?.history)
        assertEquals(null, graph[TabId("profile")])
    }

    @Test
    fun `graphs with equal ordered tabs are structurally equal`() {
        val firstTabs = listOf(
            TabState(TabId("accounts"), navigation(entry("accounts", Accounts))),
            TabState(TabId("cards"), navigation(entry("cards", Cards))),
        )
        val equalTabs = listOf(
            TabState(TabId("accounts"), navigation(entry("accounts", Accounts))),
            TabState(TabId("cards"), navigation(entry("cards", Cards))),
        )

        val first = TabNavigationState.create(firstTabs.first().id, firstTabs)
        val second = TabNavigationState.create(equalTabs.first().id, equalTabs)
        val otherSelection = TabNavigationState.create(equalTabs.last().id, equalTabs)
        val otherOrder = TabNavigationState.create(
            equalTabs.first().id,
            equalTabs.reversed(),
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, otherSelection)
        assertNotEquals(first, otherOrder)
        assertEquals(
            "TabNavigationState(selectedTabId=TabId(value=accounts), tabs=$firstTabs)",
            first.toString(),
        )
    }

    @Test
    fun `empty tab graph is rejected explicitly`() {
        val problems = invalidProblems(
            TabNavigationState.fromTabs(
                selectedTabId = TabId("accounts"),
                tabs = emptyList(),
            ),
        )

        assertEquals(listOf(TabNavigationStateProblem.EmptyTabs), problems)
    }

    @Test
    fun `blank tab ID reports its position`() {
        val tabs = listOf(
            TabState(TabId("accounts"), navigation(entry("accounts", Accounts))),
            TabState(TabId("  "), navigation(entry("cards", Cards))),
        )

        val problems = invalidProblems(TabNavigationState.fromTabs(tabs.first().id, tabs))

        assertEquals(
            listOf(TabNavigationStateProblem.BlankTabId(index = 1)),
            problems,
        )
    }

    @Test
    fun `duplicate tab ID is rejected explicitly`() {
        val duplicateId = TabId("accounts")
        val tabs = listOf(
            TabState(duplicateId, navigation(entry("accounts", Accounts))),
            TabState(duplicateId, navigation(entry("cards", Cards))),
        )

        val problems = invalidProblems(TabNavigationState.fromTabs(duplicateId, tabs))

        assertEquals(
            listOf(
                TabNavigationStateProblem.DuplicateTabId(
                    tabId = duplicateId,
                    firstIndex = 0,
                    duplicateIndex = 1,
                ),
            ),
            problems,
        )
    }

    @Test
    fun `selected tab must belong to graph`() {
        val tabs = listOf(
            TabState(TabId("accounts"), navigation(entry("accounts", Accounts))),
            TabState(TabId("cards"), navigation(entry("cards", Cards))),
        )
        val missingId = TabId("profile")

        val problems = invalidProblems(TabNavigationState.fromTabs(missingId, tabs))

        assertEquals(
            listOf(TabNavigationStateProblem.SelectedTabNotFound(missingId)),
            problems,
        )
    }

    @Test
    fun `entry identity is unique across the whole tab graph`() {
        val duplicateEntryId = EntryId("shared-leaf")
        val accountsId = TabId("accounts")
        val cardsId = TabId("cards")
        val tabs = listOf(
            TabState(
                accountsId,
                navigation(
                    entry("accounts-root", Accounts),
                    entry(duplicateEntryId, Account(accountId = 1)),
                ),
            ),
            TabState(
                cardsId,
                navigation(
                    entry("cards-root", Cards),
                    entry(duplicateEntryId, Card(cardId = 7)),
                ),
            ),
        )

        val problems = invalidProblems(TabNavigationState.fromTabs(accountsId, tabs))

        assertEquals(
            listOf(
                TabNavigationStateProblem.DuplicateEntryId(
                    entryId = duplicateEntryId,
                    firstTabId = accountsId,
                    duplicateTabId = cardsId,
                    firstTabIndex = 0,
                    duplicateTabIndex = 1,
                ),
            ),
            problems,
        )
    }

    @Test
    fun `graph owns a runtime-unmodifiable defensive copy of ordered tabs`() {
        val accounts = TabState(
            TabId("accounts"),
            navigation(entry("accounts", Accounts)),
        )
        val cards = TabState(
            TabId("cards"),
            navigation(entry("cards", Cards)),
        )
        val source = mutableListOf(accounts, cards)
        val graph = TabNavigationState.create(accounts.id, source)

        source.clear()

        assertEquals(listOf(accounts, cards), graph.tabs)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (graph.tabs as MutableList<TabState>).add(
                TabState(TabId("profile"), navigation(entry("profile", Home))),
            )
        }
    }

    @Test
    fun `create reports invalid known configuration as programmer misuse`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            TabNavigationState.create(TabId("missing"), emptyList())
        }

        assertTrue(error.message.orEmpty().contains("EmptyTabs"))
    }

    @Test
    fun `compound validation reports every problem in deterministic order`() {
        val blankId = TabId(" ")
        val sharedEntryId = EntryId("shared")
        val tabs = listOf(
            TabState(blankId, navigation(entry(sharedEntryId, Accounts))),
            TabState(blankId, navigation(entry(sharedEntryId, Cards))),
        )
        val missingSelected = TabId("missing")

        val problems = invalidProblems(
            TabNavigationState.fromTabs(missingSelected, tabs),
        )

        assertEquals(
            listOf(
                TabNavigationStateProblem.BlankTabId(index = 0),
                TabNavigationStateProblem.BlankTabId(index = 1),
                TabNavigationStateProblem.DuplicateTabId(
                    tabId = blankId,
                    firstIndex = 0,
                    duplicateIndex = 1,
                ),
                TabNavigationStateProblem.SelectedTabNotFound(missingSelected),
                TabNavigationStateProblem.DuplicateEntryId(
                    entryId = sharedEntryId,
                    firstTabId = blankId,
                    duplicateTabId = blankId,
                    firstTabIndex = 0,
                    duplicateTabIndex = 1,
                ),
            ),
            problems,
        )
    }

    private fun invalidProblems(
        result: TabNavigationStateCreationResult,
    ): List<TabNavigationStateProblem> = when (result) {
        is TabNavigationStateCreationResult.Valid -> throw AssertionError(
            "Expected invalid graph, got ${result.state}",
        )
        is TabNavigationStateCreationResult.Invalid -> result.problems
    }

    private fun navigation(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> throw AssertionError(result.problems)
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        entry(EntryId(id), route)

    private fun entry(id: EntryId, route: Route): BackStackEntry =
        BackStackEntry(id, route)
}
