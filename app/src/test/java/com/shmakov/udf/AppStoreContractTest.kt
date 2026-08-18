package com.shmakov.udf

import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.ContentRoute
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavReduction
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.NavUnchangedReason
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.Transactions
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStoreContractTest {

    @Test
    fun `initial frame exposes the supplied immutable app state`() {
        val initial = appState(entry("home", Home), showInPlace = true)
        val store = AppStore(initial)

        val exposedFrames: StateFlow<AppStateFrame> = store.frames
        val frame = exposedFrames.value

        assertSame(initial, frame.appState)
        assertTrue(frame.appState.showInPlace)
        assertNull(frame.navigationTransition)
        assertEquals(0L, frame.navigationRevision)
        assertFalse(exposedFrames is MutableStateFlow<*>)
    }

    @Test
    fun `changed reduction publishes state and transition in one frame`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val store = AppStore(appState(home))

        val reduction = changed(
            store.dispatch(NavAction.Push(home.id, accounts)),
        )
        val frame = store.frames.value

        assertSame(reduction.state, frame.appState.navState)
        assertEquals(listOf(home, accounts), frame.appState.navState.entries)
        assertEquals(reduction.transition, frame.navigationTransition)
        assertEquals(
            NavTransitionIntent.Pushed(home.id, accounts.id),
            frame.navigationTransition,
        )
        assertEquals(1L, frame.navigationRevision)
    }

    @Test
    fun `unchanged reduction keeps the exact observable frame`() {
        val store = AppStore(appState(entry("home", Home)))
        val before = store.frames.value

        val reduction = unchanged(store.dispatch(NavAction.Pop))

        assertEquals(NavUnchangedReason.RootProtected, reduction.reason)
        assertSame(before.appState.navState, reduction.state)
        assertSame(before, store.frames.value)
        assertEquals(0L, store.frames.value.navigationRevision)
    }

    @Test
    fun `navigation changes preserve unrelated app state`() {
        val home = entry("home", Home)
        val store = AppStore(appState(home, showInPlace = true))

        changed(
            store.dispatch(
                NavAction.Push(home.id, entry("accounts", Accounts)),
            ),
        )

        assertTrue(store.frames.value.appState.showInPlace)
    }

    @Test
    fun `representative sequence always reduces the latest committed state`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(42))
        val store = AppStore(appState(home, showInPlace = true))

        val pushAccounts = changed(
            store.dispatch(NavAction.Push(home.id, accounts)),
        )
        assertEquals(pushAccounts.transition, store.frames.value.navigationTransition)
        assertEquals(1L, store.frames.value.navigationRevision)

        val pushAccount = changed(
            store.dispatch(NavAction.Push(accounts.id, account)),
        )
        assertEquals(
            listOf(home, accounts, account),
            store.frames.value.appState.navState.entries,
        )
        assertEquals(pushAccount.transition, store.frames.value.navigationTransition)
        assertEquals(2L, store.frames.value.navigationRevision)

        val dismissAccount = changed(
            store.dispatch(NavAction.DismissModal(account.id)),
        )
        assertEquals(
            listOf(home, accounts),
            store.frames.value.appState.navState.entries,
        )
        assertEquals(dismissAccount.transition, store.frames.value.navigationTransition)
        assertEquals(3L, store.frames.value.navigationRevision)

        val popAccounts = changed(store.dispatch(NavAction.Pop))
        assertEquals(listOf(home), store.frames.value.appState.navState.entries)
        assertEquals(popAccounts.transition, store.frames.value.navigationTransition)
        assertEquals(4L, store.frames.value.navigationRevision)
        assertTrue(store.frames.value.appState.showInPlace)
    }

    @Test
    fun `stale guarded push is a no-op and does not replace transition metadata`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val store = AppStore(appState(home))
        val staleAction = NavAction.Push(
            expectedTopId = home.id,
            entry = entry("stale-details", AccountDetails(7)),
        )

        changed(store.dispatch(NavAction.Push(home.id, accounts)))
        val beforeStaleAction = store.frames.value
        val result = unchanged(store.dispatch(staleAction))

        assertEquals(
            NavUnchangedReason.SourceIsNotTop(home.id, accounts.id),
            result.reason,
        )
        assertSame(beforeStaleAction, store.frames.value)
        assertEquals(
            NavTransitionIntent.Pushed(home.id, accounts.id),
            store.frames.value.navigationTransition,
        )
    }

    @Test
    fun `stale branch callback after logout cannot replace the new history`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val store = AppStore(appState(home, accounts))
        val staleAction = NavAction.NavigateFrom(
            sourceId = home.id,
            entry = entry("cards", Cards),
        )
        val signedOut = navState(entry("signed-out", SignedOut))

        changed(store.dispatch(NavAction.ReplaceHistory(signedOut)))
        val logoutFrame = store.frames.value
        val result = unchanged(store.dispatch(staleAction))

        assertEquals(NavUnchangedReason.EntryNotFound(home.id), result.reason)
        assertSame(logoutFrame, store.frames.value)
        assertSame(signedOut, store.frames.value.appState.navState)
    }

    @Test
    fun `stale modal completion addresses identity and leaves duplicate route occurrence`() {
        val home = entry("home", Home)
        val first = entry("account-1", Account(42))
        val second = entry("account-2", Account(42))
        val store = AppStore(appState(home, first, second))

        changed(store.dispatch(NavAction.DismissModal(first.id)))
        assertEquals(
            listOf(home, second),
            store.frames.value.appState.navState.entries,
        )

        val afterDismiss = store.frames.value
        val staleCompletion = unchanged(
            store.dispatch(NavAction.DismissModal(first.id)),
        )

        assertEquals(NavUnchangedReason.EntryNotFound(first.id), staleCompletion.reason)
        assertSame(afterDismiss, store.frames.value)
        assertEquals(second.id, store.frames.value.appState.navState.top.id)
    }

    @Test
    fun `replace history publishes one atomic frame`() {
        val home = entry("home", Home)
        val store = AppStore(
            appState(
                home,
                entry("accounts", Accounts),
                entry("account", Account(1)),
                showInPlace = true,
            ),
        )
        val target = navState(
            entry("deep-home", Home),
            entry("transactions", Transactions),
        )

        runBlocking {
            val observedFrames = async(start = CoroutineStart.UNDISPATCHED) {
                store.frames.take(2).toList()
            }

            val reduction = changed(
                store.dispatch(NavAction.ReplaceHistory(target)),
            )
            val frames = withTimeout(2_000) { observedFrames.await() }

            assertEquals(2, frames.size)
            assertEquals(3, frames.first().appState.navState.entries.size)
            assertSame(target, frames.last().appState.navState)
            assertTrue(frames.last().appState.showInPlace)
            assertEquals(reduction.transition, frames.last().navigationTransition)
            assertEquals(
                NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = EntryId("account"),
                    targetTopEntryId = EntryId("transactions"),
                ),
                frames.last().navigationTransition,
            )
        }
    }

    @Test
    fun `independent stores never share mutable state`() {
        val home = entry("home", Home)
        val initial = appState(home)
        val first = AppStore(initial)
        val second = AppStore(initial)

        changed(
            first.dispatch(
                NavAction.Push(home.id, entry("accounts", Accounts)),
            ),
        )

        assertEquals(2, first.frames.value.appState.navState.entries.size)
        assertSame(initial, second.frames.value.appState)
        assertEquals(1, second.frames.value.appState.navState.entries.size)
        assertNull(second.frames.value.navigationTransition)
    }

    @Test
    fun `concurrent guarded pushes serialize and commit exactly one child`() {
        val home = entry("home", Home)
        val store = AppStore(appState(home))
        val actions = (1..32).map { index ->
            NavAction.Push(
                expectedTopId = home.id,
                entry = entry("accounts-$index", Accounts),
            )
        }

        val reductions = dispatchConcurrently(store, actions)
        val changes = reductions.filterIsInstance<NavReduction.Changed>()
        val unchanged = reductions.filterIsInstance<NavReduction.Unchanged>()

        assertEquals(1, changes.size)
        assertEquals(actions.size - 1, unchanged.size)
        assertTrue(
            unchanged.all { result ->
                result.reason == NavUnchangedReason.SourceIsNotTop(
                    expectedTopId = home.id,
                    actualTopId = store.frames.value.appState.navState.top.id,
                )
            },
        )
        assertEquals(2, store.frames.value.appState.navState.entries.size)
        assertTrue(
            actions.any { action ->
                action.entry.id == store.frames.value.appState.navState.top.id
            },
        )
        assertEquals(changes.single().transition, store.frames.value.navigationTransition)
    }

    @Test
    fun `concurrent pops drain the history once and stop at root`() {
        val home = entry("home", Home)
        val children = (1..32).map { index -> entry("entry-$index", Accounts) }
        val store = AppStore(
            appState(
                home,
                *children.toTypedArray(),
                showInPlace = true,
            ),
        )

        val reductions = dispatchConcurrently(
            store = store,
            actions = List(64) { NavAction.Pop },
        )

        assertEquals(32, reductions.count { it is NavReduction.Changed })
        assertEquals(
            32,
            reductions.count { result ->
                result is NavReduction.Unchanged &&
                    result.reason == NavUnchangedReason.RootProtected
            },
        )
        assertEquals(listOf(home), store.frames.value.appState.navState.entries)
        assertTrue(store.frames.value.appState.showInPlace)
        assertEquals(
            NavTransitionIntent.Popped(
                removedEntryId = children.first().id,
                revealedEntryId = home.id,
            ),
            store.frames.value.navigationTransition,
        )
    }

    @Test
    fun `changed navigation is persisted under the dispatch lock before frame publication`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val initial = appState(home)
        val persistenceStarted = CountDownLatch(1)
        val allowPersistenceToFinish = CountDownLatch(1)
        val persistedStates = Collections.synchronizedList(mutableListOf<NavState>())
        val store = AppStore(
            initialState = initial,
            persistNavigationState = { state ->
                persistenceStarted.countDown()
                assertTrue(allowPersistenceToFinish.await(5, TimeUnit.SECONDS))
                persistedStates += state
                NavigationSaveResult.Saved
            },
        )
        val initialFrame = store.frames.value
        val executor = Executors.newSingleThreadExecutor()

        try {
            val dispatch = executor.submit<NavReduction> {
                store.dispatch(NavAction.Push(home.id, accounts))
            }

            assertTrue(persistenceStarted.await(5, TimeUnit.SECONDS))
            assertSame(initialFrame, store.frames.value)
            assertTrue(persistedStates.isEmpty())

            allowPersistenceToFinish.countDown()
            val reduction = changed(dispatch.get(5, TimeUnit.SECONDS))

            assertEquals(listOf(reduction.state), persistedStates)
            assertSame(reduction.state, store.frames.value.appState.navState)
            assertEquals(1L, store.frames.value.navigationRevision)
            assertEquals(reduction.transition, store.frames.value.navigationTransition)
        } finally {
            allowPersistenceToFinish.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `persistence failure does not suppress a changed frame revision or intent`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val persistedStates = mutableListOf<NavState>()
        val persistenceFailure = NavigationSaveResult.Failed(
            listOf(
                NavigationSaveProblem.SavedStateAccess(
                    code = "forced_failure",
                    message = "Persistence is unavailable in this test.",
                ),
            ),
        )
        val store = AppStore(
            initialState = appState(home),
            persistNavigationState = { state ->
                persistedStates += state
                persistenceFailure
            },
        )

        val reduction = changed(
            store.dispatch(NavAction.Push(home.id, accounts)),
        )
        val frame = store.frames.value

        assertEquals(listOf(reduction.state), persistedStates)
        assertSame(reduction.state, frame.appState.navState)
        assertEquals(listOf(home, accounts), frame.appState.navState.entries)
        assertEquals(1L, frame.navigationRevision)
        assertEquals(
            NavTransitionIntent.Pushed(home.id, accounts.id),
            frame.navigationTransition,
        )
        assertEquals(reduction.transition, frame.navigationTransition)
    }

    @Test
    fun `unchanged navigation neither persists nor replaces the observable frame`() {
        var persistenceCalls = 0
        val store = AppStore(
            initialState = appState(entry("home", Home)),
            persistNavigationState = {
                persistenceCalls += 1
                NavigationSaveResult.Saved
            },
        )
        val before = store.frames.value

        val reduction = unchanged(store.dispatch(NavAction.Pop))

        assertEquals(NavUnchangedReason.RootProtected, reduction.reason)
        assertEquals(0, persistenceCalls)
        assertSame(before, store.frames.value)
    }

    @Test
    fun `concurrent changes leave the last persisted navigation equal to the latest frame`() {
        val home = entry("home", Home)
        val children = (1..16).map { index -> entry("entry-$index", Accounts) }
        val persistedStates = Collections.synchronizedList(mutableListOf<NavState>())
        val store = AppStore(
            initialState = appState(home, *children.toTypedArray()),
            persistNavigationState = { state ->
                persistedStates += state
                NavigationSaveResult.Saved
            },
        )

        val reductions = dispatchConcurrently(
            store = store,
            actions = List(32) { NavAction.Pop },
        )

        assertEquals(16, reductions.count { it is NavReduction.Changed })
        assertEquals(16, persistedStates.size)
        assertEquals(16L, store.frames.value.navigationRevision)
        assertSame(persistedStates.last(), store.frames.value.appState.navState)
        assertEquals(listOf(home), persistedStates.last().entries)
    }

    private fun dispatchConcurrently(
        store: AppStore,
        actions: List<NavAction>,
    ): List<NavReduction> {
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        return try {
            val futures = actions.map { action ->
                executor.submit<NavReduction> {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    store.dispatch(action)
                }
            }

            start.countDown()
            futures.map { future -> future.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
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

    private fun appState(
        vararg entries: BackStackEntry,
        showInPlace: Boolean = false,
    ): AppState = AppState(
        navState = navState(*entries),
        showInPlace = showInPlace,
    )

    private fun navState(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> throw AssertionError(
                "Invalid test fixture: ${result.problems}",
            )
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private object SignedOut : ContentRoute
}
