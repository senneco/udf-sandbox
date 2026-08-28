package com.shmakov.udf.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TabNavigationReducerContractTest {

    @Test
    fun `select changes only selected tab and preserves every exact history`() {
        val accounts = tab(
            ACCOUNTS,
            entry("accounts-root", Accounts),
            entry("accounts-details", AccountDetails(accountId = 1)),
        )
        val cards = tab(
            CARDS,
            entry("cards-root", Cards),
            entry("card-details", Card(cardId = 7)),
        )
        val initial = graph(ACCOUNTS, accounts, cards)

        val result = changed(TabReducer.reduce(initial, TabAction.select(CARDS)))

        assertEquals(CARDS, result.state.selectedTabId)
        assertEquals(listOf(accounts, cards), result.state.tabs)
        assertSame(accounts, result.state[ACCOUNTS])
        assertSame(accounts.history, result.state[ACCOUNTS]?.history)
        assertSame(cards, result.state[CARDS])
        assertSame(cards.history, result.state[CARDS]?.history)
        assertEquals(
            TabTransitionIntent.TabSelected(
                previousTabId = ACCOUNTS,
                selectedTabId = CARDS,
            ),
            result.transition,
        )
    }

    @Test
    fun `reselect and unknown tab are typed no-ops`() {
        val initial = graph(
            ACCOUNTS,
            tab(ACCOUNTS, entry("accounts-root", Accounts)),
            tab(CARDS, entry("cards-root", Cards)),
        )

        val reselect = unchanged(TabReducer.reduce(initial, TabAction.select(ACCOUNTS)))
        val unknown = unchanged(TabReducer.reduce(initial, TabAction.select(TabId("unknown"))))

        assertSame(initial, reselect.state)
        assertEquals(TabUnchangedReason.AlreadySelected(ACCOUNTS), reselect.reason)
        assertSame(initial, unknown.state)
        assertEquals(
            TabUnchangedReason.TabNotFound(TabId("unknown")),
            unknown.reason,
        )
    }

    @Test
    fun `navigation in selected tab delegates to leaf reducer and preserves inactive tabs`() {
        val accounts = tab(ACCOUNTS, entry("accounts-root", Accounts))
        val cards = tab(CARDS, entry("cards-root", Cards))
        val initial = graph(ACCOUNTS, accounts, cards)
        val details = entry("account-details", AccountDetails(accountId = 1))
        val leafAction = NavAction.Push(accounts.history.top.id, details)
        val expectedLeaf = changed(NavReducer.reduce(accounts.history, leafAction))

        val result = changed(
            TabReducer.reduce(
                initial,
                TabAction.navigateInSelectedTab(
                    expectedSelectedTabId = ACCOUNTS,
                    expectedTopId = accounts.history.top.id,
                    action = leafAction,
                ),
            ),
        )

        assertEquals(expectedLeaf.state, result.state[ACCOUNTS]?.history)
        assertNotSame(accounts, result.state[ACCOUNTS])
        assertSame(cards, result.state[CARDS])
        assertSame(cards.history, result.state[CARDS]?.history)
        assertEquals(ACCOUNTS, result.state.selectedTabId)
        assertEquals(
            TabTransitionIntent.NavigationChanged(
                tabId = ACCOUNTS,
                navigationTransition = expectedLeaf.transition,
            ),
            result.transition,
        )
    }

    @Test
    fun `leaf unchanged reason remains visible with exact original graph`() {
        val duplicateId = EntryId("accounts-root")
        val accounts = tab(ACCOUNTS, entry(duplicateId, Accounts))
        val initial = graph(
            ACCOUNTS,
            accounts,
            tab(CARDS, entry("cards-root", Cards)),
        )
        val leafAction = NavAction.Push(
            expectedTopId = accounts.history.top.id,
            entry = entry(duplicateId, AccountDetails(accountId = 1)),
        )

        val result = unchanged(
            TabReducer.reduce(
                initial,
                TabAction.navigateInSelectedTab(
                    expectedSelectedTabId = ACCOUNTS,
                    expectedTopId = accounts.history.top.id,
                    action = leafAction,
                ),
            ),
        )

        assertSame(initial, result.state)
        assertEquals(
            TabUnchangedReason.NavigationUnchanged(
                tabId = ACCOUNTS,
                reason = NavUnchangedReason.EntryIdAlreadyExists(duplicateId),
            ),
            result.reason,
        )
    }

    @Test
    fun `callback from no longer selected tab cannot mutate either history`() {
        val accounts = tab(
            ACCOUNTS,
            entry("accounts-root", Accounts),
            entry("accounts-details", AccountDetails(accountId = 1)),
        )
        val cards = tab(
            CARDS,
            entry("cards-root", Cards),
            entry("card-details", Card(cardId = 7)),
        )
        val selectedCards = graph(CARDS, accounts, cards)

        val result = unchanged(
            TabReducer.reduce(
                selectedCards,
                TabAction.navigateInSelectedTab(
                    expectedSelectedTabId = ACCOUNTS,
                    expectedTopId = EntryId("stale-accounts-top"),
                    action = NavAction.Pop,
                ),
            ),
        )

        assertSame(selectedCards, result.state)
        assertEquals(
            TabUnchangedReason.SelectedTabChanged(
                expectedSelectedTabId = ACCOUNTS,
                actualSelectedTabId = CARDS,
            ),
            result.reason,
        )
        assertSame(accounts, result.state[ACCOUNTS])
        assertSame(cards, result.state[CARDS])

        val unknown = unchanged(
            TabReducer.reduce(
                selectedCards,
                TabAction.navigateInSelectedTab(
                    expectedSelectedTabId = TabId("unknown"),
                    expectedTopId = EntryId("stale-unknown-top"),
                    action = NavAction.Pop,
                ),
            ),
        )
        assertSame(selectedCards, unknown.state)
        assertEquals(
            TabUnchangedReason.TabNotFound(TabId("unknown")),
            unknown.reason,
        )
    }

    @Test
    fun `callback with stale top cannot pop a replacement top in same tab`() {
        val accounts = tab(
            ACCOUNTS,
            entry("accounts-root", Accounts),
            entry("new-top", AccountDetails(accountId = 2)),
        )
        val initial = graph(
            ACCOUNTS,
            accounts,
            tab(CARDS, entry("cards-root", Cards)),
        )

        val result = unchanged(
            TabReducer.reduce(
                initial,
                TabAction.navigateInSelectedTab(
                    expectedSelectedTabId = ACCOUNTS,
                    expectedTopId = EntryId("old-top"),
                    action = NavAction.Pop,
                ),
            ),
        )

        assertSame(initial, result.state)
        assertEquals(
            TabUnchangedReason.TopEntryChanged(
                tabId = ACCOUNTS,
                expectedTopId = EntryId("old-top"),
                actualTopId = accounts.history.top.id,
            ),
            result.reason,
        )
    }

    @Test
    fun `delegated navigation cannot claim entry identity owned by inactive tab`() {
        val sharedId = EntryId("cards-root")
        val cards = tab(CARDS, entry(sharedId, Cards))
        val accounts = tab(ACCOUNTS, entry("accounts-root", Accounts))
        val initial = graph(ACCOUNTS, cards, accounts)
        val leafAction = NavAction.Push(
            expectedTopId = accounts.history.top.id,
            entry = entry(sharedId, AccountDetails(accountId = 1)),
        )
        assertTrue(NavReducer.reduce(accounts.history, leafAction) is NavReduction.Changed)

        val result = unchanged(
            TabReducer.reduce(
                initial,
                TabAction.navigateInSelectedTab(
                    expectedSelectedTabId = ACCOUNTS,
                    expectedTopId = accounts.history.top.id,
                    action = leafAction,
                ),
            ),
        )

        val problem = TabNavigationStateProblem.DuplicateEntryId(
            entryId = sharedId,
            firstTabId = CARDS,
            duplicateTabId = ACCOUNTS,
            firstTabIndex = 0,
            duplicateTabIndex = 1,
        )
        assertSame(initial, result.state)
        assertEquals(
            TabUnchangedReason.InvalidResultingGraph(listOf(problem)),
            result.reason,
        )
    }

    @Test
    fun `open equal inactive history selects tab without rebuilding histories`() {
        val accounts = tab(ACCOUNTS, entry("accounts-root", Accounts))
        val cards = tab(
            CARDS,
            entry("cards-root", Cards),
            entry("card-details", Card(cardId = 7)),
        )
        val initial = graph(ACCOUNTS, accounts, cards)

        val result = changed(
            TabReducer.reduce(initial, TabAction.openTab(CARDS, cards.history)),
        )

        assertEquals(CARDS, result.state.selectedTabId)
        assertSame(accounts, result.state[ACCOUNTS])
        assertSame(cards, result.state[CARDS])
        assertEquals(
            TabTransitionIntent.TabOpened(
                previousSelectedTabId = ACCOUNTS,
                openedTabId = CARDS,
                previousVisibleTopId = accounts.history.top.id,
                targetVisibleTopId = cards.history.top.id,
                targetHistoryTransition = null,
            ),
            result.transition,
        )
    }

    @Test
    fun `open atomically replaces target history and selects its tab`() {
        val accounts = tab(ACCOUNTS, entry("accounts-root", Accounts))
        val cards = tab(CARDS, entry("cards-root", Cards))
        val initial = graph(ACCOUNTS, accounts, cards)
        val targetHistory = history(
            entry("cards-root", Cards),
            entry("card-7", Card(cardId = 7)),
        )

        val result = changed(
            TabReducer.reduce(initial, TabAction.openTab(CARDS, targetHistory)),
        )

        assertEquals(CARDS, result.state.selectedTabId)
        assertSame(accounts, result.state[ACCOUNTS])
        assertSame(targetHistory, result.state[CARDS]?.history)
        assertEquals(
            TabTransitionIntent.TabOpened(
                previousSelectedTabId = ACCOUNTS,
                openedTabId = CARDS,
                previousVisibleTopId = accounts.history.top.id,
                targetVisibleTopId = targetHistory.top.id,
                targetHistoryTransition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = cards.history.top.id,
                    targetTopEntryId = targetHistory.top.id,
                ),
            ),
            result.transition,
        )
    }

    @Test
    fun `open no-op and failures never change selection or history`() {
        val accounts = tab(ACCOUNTS, entry("accounts-root", Accounts))
        val cards = tab(CARDS, entry("cards-root", Cards))
        val initial = graph(ACCOUNTS, accounts, cards)

        val alreadyOpen = unchanged(
            TabReducer.reduce(initial, TabAction.openTab(ACCOUNTS, accounts.history)),
        )
        val unknown = unchanged(
            TabReducer.reduce(initial, TabAction.openTab(TabId("unknown"), cards.history)),
        )
        val reboundTarget = history(entry("cards-root", Transactions))
        val rebound = unchanged(
            TabReducer.reduce(initial, TabAction.openTab(CARDS, reboundTarget)),
        )
        val collidingTarget = history(
            entry("cards-root", Cards),
            entry("accounts-root", AccountDetails(accountId = 1)),
        )
        val collision = unchanged(
            TabReducer.reduce(initial, TabAction.openTab(CARDS, collidingTarget)),
        )

        assertSame(initial, alreadyOpen.state)
        assertEquals(TabUnchangedReason.AlreadyAtTarget, alreadyOpen.reason)
        assertSame(initial, unknown.state)
        assertEquals(TabUnchangedReason.TabNotFound(TabId("unknown")), unknown.reason)
        assertSame(initial, rebound.state)
        assertEquals(
            TabUnchangedReason.NavigationUnchanged(
                tabId = CARDS,
                reason = NavUnchangedReason.EntryIdentityRebound(
                    entryId = EntryId("cards-root"),
                    previousRoute = Cards,
                    targetRoute = Transactions,
                ),
            ),
            rebound.reason,
        )
        assertSame(initial, collision.state)
        assertEquals(
            TabUnchangedReason.InvalidResultingGraph(
                listOf(
                    TabNavigationStateProblem.DuplicateEntryId(
                        entryId = EntryId("accounts-root"),
                        firstTabId = ACCOUNTS,
                        duplicateTabId = CARDS,
                        firstTabIndex = 0,
                        duplicateTabIndex = 1,
                    ),
                ),
            ),
            collision.reason,
        )
    }

    @Test
    fun `replace graph is atomic exact and protects retained route identity`() {
        val initial = graph(
            ACCOUNTS,
            tab(ACCOUNTS, entry("accounts-root", Accounts)),
            tab(CARDS, entry("cards-root", Cards)),
        )
        val target = graph(
            CARDS,
            tab(ACCOUNTS, entry("new-accounts", Accounts)),
            tab(CARDS, entry("new-cards", Cards)),
        )

        val changed = changed(TabReducer.reduce(initial, TabAction.replaceGraph(target)))
        val equalTarget = graph(initial.selectedTabId, *initial.tabs.toTypedArray())
        val equal = unchanged(
            TabReducer.reduce(initial, TabAction.replaceGraph(equalTarget)),
        )
        val reboundTarget = graph(
            ACCOUNTS,
            tab(ACCOUNTS, entry("accounts-root", Transactions)),
            tab(CARDS, entry("cards-root", Cards)),
        )
        val rebound = unchanged(
            TabReducer.reduce(initial, TabAction.replaceGraph(reboundTarget)),
        )

        assertSame(target, changed.state)
        assertEquals(
            TabTransitionIntent.GraphReplaced(
                previousSelectedTabId = ACCOUNTS,
                selectedTabId = CARDS,
                previousVisibleTopId = EntryId("accounts-root"),
                targetVisibleTopId = EntryId("new-cards"),
            ),
            changed.transition,
        )
        assertSame(initial, equal.state)
        assertEquals(TabUnchangedReason.AlreadyAtTarget, equal.reason)
        assertSame(initial, rebound.state)
        assertEquals(
            TabUnchangedReason.EntryIdentityRebound(
                entryId = EntryId("accounts-root"),
                previousTabId = ACCOUNTS,
                previousRoute = Accounts,
                targetTabId = ACCOUNTS,
                targetRoute = Transactions,
            ),
            rebound.reason,
        )
    }

    @Test
    fun `replace graph may explicitly reparent same route occurrence`() {
        val sharedId = EntryId("shared")
        val initial = graph(
            ACCOUNTS,
            tab(ACCOUNTS, entry(sharedId, Accounts)),
            tab(CARDS, entry("cards-root", Cards)),
        )
        val target = graph(
            CARDS,
            tab(ACCOUNTS, entry("new-accounts", Home)),
            tab(CARDS, entry(sharedId, Accounts)),
        )

        val result = changed(TabReducer.reduce(initial, TabAction.replaceGraph(target)))

        assertSame(target, result.state)
        assertEquals(sharedId, result.state[CARDS]?.history?.root?.id)
    }

    @Test
    fun `Back pops exact content top only in selected tab`() {
        val accounts = tab(
            ACCOUNTS,
            entry("accounts-root", Accounts),
            entry("details", AccountDetails(accountId = 1)),
        )
        val cards = tab(CARDS, entry("cards-root", Cards))
        val initial = graph(ACCOUNTS, accounts, cards)

        val result = changed(TabReducer.reduce(initial, TabAction.back(initial)))

        assertEquals(listOf(accounts.history.root), result.state[ACCOUNTS]?.history?.entries)
        assertSame(cards, result.state[CARDS])
        assertEquals(
            TabTransitionIntent.NavigationChanged(
                tabId = ACCOUNTS,
                navigationTransition = NavTransitionIntent.Popped(
                    removedEntryId = EntryId("details"),
                    revealedEntryId = EntryId("accounts-root"),
                ),
            ),
            result.transition,
        )
    }

    @Test
    fun `modal Back is exact and replay cannot pop underlying content`() {
        val accounts = tab(
            ACCOUNTS,
            entry("accounts-root", Accounts),
            entry("account-modal", Account(accountId = 1)),
        )
        val initial = graph(
            ACCOUNTS,
            accounts,
            tab(CARDS, entry("cards-root", Cards)),
        )
        val captured = TabAction.back(initial)

        val first = changed(TabReducer.reduce(initial, captured))
        val replay = unchanged(TabReducer.reduce(first.state, captured))

        assertEquals(listOf(accounts.history.root), first.state[ACCOUNTS]?.history?.entries)
        assertEquals(
            TabTransitionIntent.NavigationChanged(
                tabId = ACCOUNTS,
                navigationTransition = NavTransitionIntent.ModalDismissed(EntryId("account-modal")),
            ),
            first.transition,
        )
        assertSame(first.state, replay.state)
        assertEquals(
            TabUnchangedReason.TopEntryChanged(
                tabId = ACCOUNTS,
                expectedTopId = EntryId("account-modal"),
                actualTopId = EntryId("accounts-root"),
            ),
            replay.reason,
        )
    }

    @Test
    fun `Back at active root is explicit host fallthrough`() {
        val accounts = tab(ACCOUNTS, entry("accounts-root", Accounts))
        val cards = tab(
            CARDS,
            entry("cards-root", Cards),
            entry("card-details", Card(cardId = 7)),
        )
        val initial = graph(ACCOUNTS, accounts, cards)

        val result = unchanged(TabReducer.reduce(initial, TabAction.back(initial)))

        assertSame(initial, result.state)
        assertEquals(
            TabUnchangedReason.ActiveTabAtRoot(
                tabId = ACCOUNTS,
                rootEntryId = accounts.history.root.id,
            ),
            result.reason,
        )
        assertSame(cards, result.state[CARDS])
    }

    @Test
    fun `captured Back cannot pop replacement top in same tab`() {
        val initial = graph(
            ACCOUNTS,
            tab(
                ACCOUNTS,
                entry("accounts-root", Accounts),
                entry("old-top", AccountDetails(accountId = 1)),
            ),
            tab(CARDS, entry("cards-root", Cards)),
        )
        val captured = TabAction.back(initial)
        val replacement = graph(
            ACCOUNTS,
            tab(
                ACCOUNTS,
                entry("accounts-root", Accounts),
                entry("new-top", AccountDetails(accountId = 2)),
            ),
            initial[CARDS]!!,
        )

        val result = unchanged(TabReducer.reduce(replacement, captured))

        assertSame(replacement, result.state)
        assertEquals(
            TabUnchangedReason.TopEntryChanged(
                tabId = ACCOUNTS,
                expectedTopId = EntryId("old-top"),
                actualTopId = EntryId("new-top"),
            ),
            result.reason,
        )
    }

    @Test
    fun `arbitrary fourth tab needs no reducer branch`() {
        val supportId = TabId("support")
        val accounts = tab(ACCOUNTS, entry("accounts-root", Accounts))
        val cards = tab(CARDS, entry("cards-root", Cards))
        val profile = tab(TabId("profile"), entry("profile-root", Home))
        val support = tab(supportId, entry("support-root", SupportRoot))
        val initial = graph(ACCOUNTS, accounts, cards, profile, support)

        val selected = changed(TabReducer.reduce(initial, TabAction.select(supportId)))
        val pushed = changed(
            TabReducer.reduce(
                selected.state,
                TabAction.navigateInSelectedTab(
                    expectedSelectedTabId = supportId,
                    expectedTopId = support.history.top.id,
                    action = NavAction.Push(
                        support.history.top.id,
                        entry("support-details", SupportDetails(topic = "state")),
                    ),
                ),
            ),
        )
        val backed = changed(TabReducer.reduce(pushed.state, TabAction.back(pushed.state)))

        assertEquals(support.history.entries, backed.state[supportId]?.history?.entries)
        assertSame(accounts, backed.state[ACCOUNTS])
        assertSame(cards, backed.state[CARDS])
        assertSame(profile, backed.state[profile.id])
        assertEquals(listOf(accounts, cards, profile, backed.state[supportId]), backed.state.tabs)
    }

    @Test
    fun `invalid resulting graph reason owns unmodifiable defensive problems`() {
        val source = mutableListOf<TabNavigationStateProblem>(
            TabNavigationStateProblem.EmptyTabs,
        )
        val reason = TabUnchangedReason.InvalidResultingGraph(source)

        source.clear()

        assertEquals(listOf(TabNavigationStateProblem.EmptyTabs), reason.problems)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (reason.problems as MutableList<TabNavigationStateProblem>).add(
                TabNavigationStateProblem.SelectedTabNotFound(ACCOUNTS),
            )
        }
    }

    @Test
    fun `action factories materialize explicit public values`() {
        val graph = graph(
            ACCOUNTS,
            tab(ACCOUNTS, entry("accounts-root", Accounts)),
            tab(CARDS, entry("cards-root", Cards)),
        )
        val leaf = NavAction.Pop

        assertEquals(TabAction.Select(CARDS), TabAction.select(CARDS))
        assertEquals(
            TabAction.NavigateInSelectedTab(ACCOUNTS, EntryId("accounts-root"), leaf),
            TabAction.navigateInSelectedTab(ACCOUNTS, EntryId("accounts-root"), leaf),
        )
        assertEquals(
            TabAction.NavigateInSelectedTab(ACCOUNTS, EntryId("accounts-root"), leaf),
            TabAction.navigateInSelectedTab(graph, leaf),
        )
        assertEquals(
            TabAction.OpenTab(CARDS, graph[CARDS]!!.history),
            TabAction.openTab(CARDS, graph[CARDS]!!.history),
        )
        assertEquals(TabAction.ReplaceGraph(graph), TabAction.replaceGraph(graph))
        assertEquals(
            TabAction.Back(ACCOUNTS, EntryId("accounts-root")),
            TabAction.back(ACCOUNTS, EntryId("accounts-root")),
        )
        assertEquals(
            TabAction.Back(ACCOUNTS, EntryId("accounts-root")),
            TabAction.back(graph),
        )
    }

    private fun changed(result: TabReduction): TabReduction.Changed {
        assertTrue("Expected Changed, got $result", result is TabReduction.Changed)
        return result as TabReduction.Changed
    }

    private fun unchanged(result: TabReduction): TabReduction.Unchanged {
        assertTrue("Expected Unchanged, got $result", result is TabReduction.Unchanged)
        return result as TabReduction.Unchanged
    }

    private fun changed(result: NavReduction): NavReduction.Changed {
        assertTrue("Expected Changed, got $result", result is NavReduction.Changed)
        return result as NavReduction.Changed
    }

    private fun graph(selectedTabId: TabId, vararg tabs: TabState): TabNavigationState =
        TabNavigationState.create(selectedTabId, tabs.toList())

    private fun tab(id: TabId, vararg entries: BackStackEntry): TabState =
        TabState(id, history(*entries))

    private fun history(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> throw AssertionError(result.problems)
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        entry(EntryId(id), route)

    private fun entry(id: EntryId, route: Route): BackStackEntry =
        BackStackEntry(id, route)

    private data class SupportDetails(val topic: String) : ContentRoute

    private object SupportRoot : ContentRoute

    private companion object {
        val ACCOUNTS = TabId("accounts")
        val CARDS = TabId("cards")
    }
}
