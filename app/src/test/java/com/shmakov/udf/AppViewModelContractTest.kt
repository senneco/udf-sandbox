package com.shmakov.udf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Card
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.DemoRouteCodec
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.navigation.TabId
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabState
import com.shmakov.udf.navigation.TabTransitionIntent
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppViewModelContractTest {

    @Test
    fun `default ViewModel owns the complete two-tab demo graph`() {
        val viewModel = AppViewModel(SavedStateHandle())
        val lifecycleOwner: ViewModel = viewModel
        val exposedFrames: StateFlow<TabNavigationFrame> = viewModel.frames
        val frame = exposedFrames.value

        assertSame(viewModel, lifecycleOwner)
        assertEquals(DemoTabGraph.accountsTabId, frame.state.selectedTabId)
        assertEquals(
            listOf(DemoTabGraph.accountsTabId, DemoTabGraph.cardsTabId),
            frame.state.tabs.map(TabState::id),
        )
        assertEquals(
            listOf(Accounts, Account(1)),
            frame.state[DemoTabGraph.accountsTabId]?.history?.entries
                ?.map(BackStackEntry::route),
        )
        assertEquals(
            listOf(Cards, Card(1)),
            frame.state[DemoTabGraph.cardsTabId]?.history?.entries
                ?.map(BackStackEntry::route),
        )
        val ids = frame.state.allEntryIds()
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { id -> id.value.isNotBlank() })
        assertEquals(0L, frame.revision)
        assertNull(frame.transition)
        assertEquals(
            TabNavigationStartResult.StartedFresh(TabNavigationSaveResult.Saved),
            viewModel.startResult,
        )
    }

    @Test
    fun `ViewModel delegates typed actions and separate instances own separate stores`() {
        val initial = graph(
            selected = ACCOUNTS_TAB,
            tab(ACCOUNTS_TAB, entry("accounts", Accounts)),
            tab(CARDS_TAB, entry("cards", Cards)),
        )
        val first = viewModel(SavedStateHandle(), initial)
        val second = viewModel(SavedStateHandle(), initial)

        val result = first.dispatch(TabAction.select(CARDS_TAB))

        assertTrue(result is TabNavigationDispatchResult.Changed)
        result as TabNavigationDispatchResult.Changed
        assertSame(result.reduction.state, first.frames.value.state)
        assertEquals(1L, first.frames.value.revision)
        assertEquals(
            TabTransitionIntent.TabSelected(ACCOUNTS_TAB, CARDS_TAB),
            first.frames.value.transition,
        )
        assertSame(initial, second.frames.value.state)
        assertEquals(0L, second.frames.value.revision)
        assertNull(second.frames.value.transition)
    }

    @Test
    fun `valid saved graph restores exact selection histories and IDs without creating fallback`() {
        val restored = graph(
            selected = CARDS_TAB,
            tab(
                ACCOUNTS_TAB,
                entry("restored-accounts", Accounts),
                entry("restored-account", Account(42)),
            ),
            tab(
                CARDS_TAB,
                entry("restored-cards", Cards),
                entry("restored-card", Card(7)),
            ),
        )
        val handle = SavedStateHandle()
        assertEquals(
            TabNavigationSaveResult.Saved,
            SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec).save(restored),
        )
        var fallbackCalls = 0

        val viewModel = AppViewModel(
            savedStateHandle = handle,
            fallbackGraphFactory = TabNavigationStateFactory {
                fallbackCalls += 1
                throw AssertionError("Valid restoration must stay lazy")
            },
            routeCodec = DemoRouteCodec,
        )

        assertEquals(0, fallbackCalls)
        assertEquals(TabNavigationStartResult.Restored, viewModel.startResult)
        assertEquals(restored, viewModel.frames.value.state)
        assertEquals(0L, viewModel.frames.value.revision)
        assertNull(viewModel.frames.value.transition)
    }

    @Test
    fun `fresh owner from copied payload restores exact graph and resets process metadata`() {
        val firstHandle = SavedStateHandle()
        val first = viewModel(
            firstHandle,
            graph(
                selected = ACCOUNTS_TAB,
                tab(ACCOUNTS_TAB, entry("accounts", Accounts)),
                tab(CARDS_TAB, entry("cards", Cards)),
            ),
        )
        first.dispatch(TabAction.select(CARDS_TAB))
        val beforeFreshOwner = first.frames.value
        val key = firstHandle.keys().single()
        val copiedPayload = ArrayList(checkNotNull(firstHandle.get<ArrayList<String>>(key)))

        val freshOwner = AppViewModel(
            savedStateHandle = SavedStateHandle(mapOf(key to copiedPayload)),
            fallbackGraphFactory = TabNavigationStateFactory {
                throw AssertionError("Copied valid payload must win")
            },
            routeCodec = DemoRouteCodec,
        )

        assertNotSame(first, freshOwner)
        assertEquals(beforeFreshOwner.state, freshOwner.frames.value.state)
        assertEquals(beforeFreshOwner.state.allEntryIds(), freshOwner.frames.value.state.allEntryIds())
        assertEquals(0L, freshOwner.frames.value.revision)
        assertNull(freshOwner.frames.value.transition)
    }

    @Test
    fun `reset replaces the whole graph in one frame`() {
        val initial = graph(
            selected = CARDS_TAB,
            tab(ACCOUNTS_TAB, entry("old-accounts", Accounts)),
            tab(CARDS_TAB, entry("old-cards", Cards)),
        )
        val reset = graph(
            selected = ACCOUNTS_TAB,
            tab(
                ACCOUNTS_TAB,
                entry("new-accounts", Accounts),
                entry("new-account", Account(1)),
            ),
            tab(
                CARDS_TAB,
                entry("new-cards", Cards),
                entry("new-card", Card(1)),
            ),
        )
        val handle = SavedStateHandle()
        val viewModel = AppViewModel(
            savedStateHandle = handle,
            fallbackGraphFactory = TabNavigationStateFactory { initial },
            routeCodec = DemoRouteCodec,
        )
        val before = viewModel.frames.value

        val result = viewModel.replaceNavigationGraph(reset)

        assertTrue(result is TabNavigationDispatchResult.Changed)
        result as TabNavigationDispatchResult.Changed
        assertSame(reset, result.reduction.state)
        assertSame(reset, viewModel.frames.value.state)
        assertEquals(before.revision + 1L, viewModel.frames.value.revision)
        assertTrue(viewModel.frames.value.transition is TabTransitionIntent.GraphReplaced)
        assertEquals(reset, restored(handle))
    }

    private fun viewModel(
        handle: SavedStateHandle,
        state: TabNavigationState,
    ): AppViewModel = AppViewModel(
        savedStateHandle = handle,
        fallbackGraphFactory = TabNavigationStateFactory { state },
        routeCodec = DemoRouteCodec,
    )

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

    private fun TabNavigationState.allEntryIds(): List<EntryId> = tabs.flatMap { tab ->
        tab.history.entries.map(BackStackEntry::id)
    }

    private companion object {
        val ACCOUNTS_TAB = TabId("accounts")
        val CARDS_TAB = TabId("cards")
    }
}
