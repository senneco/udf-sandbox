package com.shmakov.udf

import androidx.lifecycle.SavedStateHandle
import com.shmakov.udf.deeplink.DeepLinkEntryIdFactory
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Card
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.DemoRouteCodec
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.TabId
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabNavigationStateProblem
import com.shmakov.udf.navigation.TabState
import com.shmakov.udf.navigation.TabTransitionIntent
import com.shmakov.udf.navigation.TabUnchangedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppViewModelDeepLinkContractTest {

    @Test
    fun `valid link atomically opens Accounts tab and preserves inactive history`() {
        val cards = tab(
            CARDS_TAB,
            entry("cards", Cards),
            entry("card-7", Card(7)),
        )
        val initial = graph(
            selected = CARDS_TAB,
            tab(ACCOUNTS_TAB, entry("old-accounts", Accounts)),
            cards,
        )
        val expectedIds = listOf("deep-accounts", "deep-account", "deep-details")
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle, initial, sequentialFactory(expectedIds))

        val handling = viewModel.handleDeepLink("udf-sandbox://accounts/42/details")

        assertTrue(handling is DeepLinkHandlingResult.Applied)
        handling as DeepLinkHandlingResult.Applied
        assertEquals(ACCOUNTS_TAB, handling.action.tabId)
        assertSame(handling.dispatchResult.reduction.state, viewModel.frames.value.state)
        assertEquals(1L, viewModel.frames.value.revision)
        assertEquals(
            listOf(Accounts, Account(42), AccountDetails(42)),
            viewModel.frames.value.state[ACCOUNTS_TAB]?.history?.entries
                ?.map(BackStackEntry::route),
        )
        assertEquals(
            expectedIds,
            viewModel.frames.value.state[ACCOUNTS_TAB]?.history?.entries
                ?.map { entry -> entry.id.value },
        )
        assertSame(cards, viewModel.frames.value.state[CARDS_TAB])
        assertTrue(viewModel.frames.value.transition is TabTransitionIntent.TabOpened)
        assertEquals(viewModel.frames.value.state, restored(handle))
    }

    @Test
    fun `repeated link creates disjoint occurrences and one revision per OpenTab`() {
        val ids = (0..5).map { index -> "deep-$index" }
        val initial = graph(
            selected = ACCOUNTS_TAB,
            tab(ACCOUNTS_TAB, entry("accounts", Accounts)),
            tab(CARDS_TAB, entry("cards", Cards)),
        )
        val viewModel = viewModel(SavedStateHandle(), initial, sequentialFactory(ids))

        viewModel.handleDeepLink("udf-sandbox://accounts/42/details")
        val first = viewModel.frames.value
        viewModel.handleDeepLink("udf-sandbox://accounts/42/details")
        val second = viewModel.frames.value

        assertEquals(1L, first.revision)
        assertEquals(2L, second.revision)
        assertTrue(first.transition is TabTransitionIntent.TabOpened)
        assertTrue(second.transition is TabTransitionIntent.TabOpened)
        val firstIds = checkNotNull(first.state[ACCOUNTS_TAB])
            .history.entries.map(BackStackEntry::id)
        val secondIds = checkNotNull(second.state[ACCOUNTS_TAB])
            .history.entries.map(BackStackEntry::id)
        assertTrue(firstIds.toSet().intersect(secondIds.toSet()).isEmpty())
    }

    @Test
    fun `invalid missing and foreign links preserve exact frame and payload`() {
        val handle = SavedStateHandle()
        val initial = graph(
            selected = ACCOUNTS_TAB,
            tab(ACCOUNTS_TAB, entry("accounts", Accounts)),
            tab(CARDS_TAB, entry("cards", Cards)),
        )
        val viewModel = viewModel(
            handle,
            initial,
            DeepLinkEntryIdFactory {
                throw AssertionError("Invalid links must not create entry IDs")
            },
        )
        val originalFrame = viewModel.frames.value
        val key = handle.keys().single()
        val originalPayload = ArrayList(checkNotNull(handle.get<ArrayList<String>>(key)))

        listOf(
            null,
            "udf-sandbox://accounts/details",
            "udf-sandbox://accounts/2147483648/details",
            "https://accounts/42/details",
        ).forEach { rawUri ->
            assertTrue(viewModel.handleDeepLink(rawUri) !is DeepLinkHandlingResult.Applied)
            assertSame(originalFrame, viewModel.frames.value)
            assertEquals(originalPayload, handle.get<ArrayList<String>>(key))
        }
    }

    @Test
    fun `graph collision reports typed reducer no-op without claiming link applied`() {
        val sharedId = EntryId("shared")
        val initial = graph(
            selected = CARDS_TAB,
            tab(ACCOUNTS_TAB, entry("accounts", Accounts)),
            tab(CARDS_TAB, BackStackEntry(sharedId, Cards)),
        )
        val handle = SavedStateHandle()
        val viewModel = viewModel(
            handle,
            initial,
            sequentialFactory(listOf("shared", "account", "details")),
        )
        val originalFrame = viewModel.frames.value

        val result = viewModel.handleDeepLink("udf-sandbox://accounts/42/details")

        assertTrue(result is DeepLinkHandlingResult.Unchanged)
        result as DeepLinkHandlingResult.Unchanged
        val reason = result.dispatchResult.reduction.reason
        assertTrue(reason is TabUnchangedReason.InvalidResultingGraph)
        reason as TabUnchangedReason.InvalidResultingGraph
        assertEquals(
            listOf(
                TabNavigationStateProblem.DuplicateEntryId(
                    entryId = sharedId,
                    firstTabId = ACCOUNTS_TAB,
                    duplicateTabId = CARDS_TAB,
                    firstTabIndex = 0,
                    duplicateTabIndex = 1,
                ),
            ),
            reason.problems,
        )
        assertSame(originalFrame, viewModel.frames.value)
        assertEquals(initial, restored(handle))
    }

    @Test
    fun `fresh owner restores deep-linked graph with zero revision and no transition`() {
        val firstHandle = SavedStateHandle()
        val first = viewModel(
            firstHandle,
            graph(
                selected = CARDS_TAB,
                tab(ACCOUNTS_TAB, entry("accounts", Accounts)),
                tab(CARDS_TAB, entry("cards", Cards)),
            ),
            sequentialFactory(listOf("deep-a", "deep-m", "deep-d")),
        )
        first.handleDeepLink("udf-sandbox://accounts/42/details")
        val linked = first.frames.value.state
        val key = firstHandle.keys().single()
        val payload = ArrayList(checkNotNull(firstHandle.get<ArrayList<String>>(key)))

        val freshOwner = AppViewModel(
            savedStateHandle = SavedStateHandle(mapOf(key to payload)),
            fallbackGraphFactory = TabNavigationStateFactory {
                throw AssertionError("Restored graph must win")
            },
            routeCodec = DemoRouteCodec,
        )

        assertEquals(linked, freshOwner.frames.value.state)
        assertEquals(0L, freshOwner.frames.value.revision)
        assertNull(freshOwner.frames.value.transition)
    }

    private fun viewModel(
        handle: SavedStateHandle,
        initial: TabNavigationState,
        ids: DeepLinkEntryIdFactory = DeepLinkEntryIdFactory {
            throw AssertionError("Unexpected deep-link ID request")
        },
    ): AppViewModel = AppViewModel(
        savedStateHandle = handle,
        fallbackGraphFactory = TabNavigationStateFactory { initial },
        routeCodec = DemoRouteCodec,
        deepLinkEntryIdFactory = ids,
    )

    private fun sequentialFactory(ids: List<String>): DeepLinkEntryIdFactory {
        val remaining = ArrayDeque(ids)
        return DeepLinkEntryIdFactory { EntryId(remaining.removeFirst()) }
    }

    private fun restored(handle: SavedStateHandle): TabNavigationState = when (
        val result = SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec).restore()
    ) {
        TabNavigationRestoreResult.Missing -> throw AssertionError("Expected saved graph")
        is TabNavigationRestoreResult.Restored -> result.state
        is TabNavigationRestoreResult.Rejected -> throw AssertionError(result.problems)
    }

    private fun graph(selected: TabId, vararg tabs: TabState): TabNavigationState =
        TabNavigationState.create(selected, tabs.toList())

    private fun tab(id: TabId, vararg entries: BackStackEntry): TabState =
        TabState(id, navigation(*entries))

    private fun navigation(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> throw AssertionError(result.problems)
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private companion object {
        val ACCOUNTS_TAB = TabId("accounts")
        val CARDS_TAB = TabId("cards")
    }
}
