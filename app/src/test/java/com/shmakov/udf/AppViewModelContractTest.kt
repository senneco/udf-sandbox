package com.shmakov.udf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.ContentPlacementDecision
import com.shmakov.udf.navigation.DemoRouteCodec
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavProjector
import com.shmakov.udf.navigation.NavReduction
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavStateSnapshot
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.NavigationLayoutPolicy
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.SnapshotProblem
import com.shmakov.udf.navigation.SnapshotResult
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppViewModelContractTest {

    @Test
    fun `view model is a lifecycle owner that delegates observable state and dispatch`() {
        val home = entry("home", Home)
        val initial = appState(home, showInPlace = true)
        val viewModel = AppViewModel(SavedStateHandle(), initial)

        val lifecycleViewModel: ViewModel = viewModel
        val exposedFrames: StateFlow<AppStateFrame> = viewModel.frames

        assertSame(viewModel, lifecycleViewModel)
        assertSame(initial, exposedFrames.value.appState)

        val accounts = entry("accounts", Accounts)
        val reduction = viewModel.dispatch(NavAction.Push(home.id, accounts))
        assertTrue(reduction is NavReduction.Changed)
        reduction as NavReduction.Changed

        assertSame(reduction.state, viewModel.frames.value.appState.navState)
        assertEquals(reduction.transition, viewModel.frames.value.navigationTransition)
        assertTrue(viewModel.frames.value.appState.showInPlace)
    }

    @Test
    fun `view model instances own independent stores`() {
        val home = entry("home", Home)
        val initial = appState(home)
        val first = AppViewModel(SavedStateHandle(), initial)
        val second = AppViewModel(SavedStateHandle(), initial)

        val accounts = entry("accounts", Accounts)
        first.dispatch(NavAction.Push(home.id, accounts))

        assertEquals(2, first.frames.value.appState.navState.entries.size)
        assertSame(initial, second.frames.value.appState)
        assertEquals(1, second.frames.value.appState.navState.entries.size)
        assertNull(second.frames.value.navigationTransition)
    }

    @Test
    fun `default view model publishes the demo state`() {
        val viewModel = AppViewModel(SavedStateHandle())
        val frame = viewModel.frames.value

        assertEquals(
            listOf(Home, Accounts, Account(1)),
            frame.appState.navState.entries.map(BackStackEntry::route),
        )
        assertEquals(
            frame.appState.navState.entries.size,
            frame.appState.navState.entries.map(BackStackEntry::id).toSet().size,
        )
        assertTrue(
            frame.appState.navState.entries.all { entry -> entry.id.value.isNotBlank() },
        )
        assertFalse(frame.appState.showInPlace)
        assertNull(frame.navigationTransition)
    }

    @Test
    fun `missing saved navigation uses exact fallback and saves it immediately`() {
        val fallback = appState(
            entry("fallback-home", Home),
            entry("fallback-accounts", Accounts),
            showInPlace = true,
        )
        val handle = SavedStateHandle()

        val viewModel = AppViewModel(handle, fallback)
        val frame = viewModel.frames.value

        assertEquals(fallback, frame.appState)
        assertEquals(0L, frame.navigationRevision)
        assertNull(frame.navigationTransition)
        assertEquals(setOf(navigationKey(handle)), handle.keys())
        assertEquals(expectedPayload(fallback.navState), savedPayload(handle))
        assertEquals(
            fallback.navState,
            restored(SavedNavigationStateStore(handle).restore()),
        )
    }

    @Test
    fun `valid saved navigation restores exact history and resets process local metadata`() {
        val restoredState = navState(
            entry("restored-home", Home),
            entry("restored-accounts", Accounts),
            entry("restored-account-42", Account(accountId = 42)),
        )
        val handle = SavedStateHandle()
        assertSaved(SavedNavigationStateStore(handle).save(restoredState))
        val fallback = appState(
            entry("different-fallback", Home),
            showInPlace = true,
        )

        val viewModel = AppViewModel(handle, fallback)
        val frame = viewModel.frames.value

        assertEquals(restoredState.entries, frame.appState.navState.entries)
        assertTrue(frame.appState.showInPlace)
        assertEquals(0L, frame.navigationRevision)
        assertNull(frame.navigationTransition)
        assertEquals(expectedPayload(restoredState), savedPayload(handle))
    }

    @Test
    fun `malformed saved navigation is typed rejection and view model rewrites full fallback`() {
        val fallback = appState(
            entry("fallback-home", Home),
            entry("fallback-accounts", Accounts),
        )
        val handle = handleWithNavigationKey()
        handle[navigationKey(handle)] = arrayListOf(
            "not-udf-nav", "1", "1", "0",
        )

        val problems = rejected(SavedNavigationStateStore(handle).restore())
        val problem = problems.single()

        assertTrue(problem is NavigationRestoreProblem.InvalidEnvelope)
        problem as NavigationRestoreProblem.InvalidEnvelope
        val envelopeProblem = problem.problem
        assertTrue(
            envelopeProblem is NavigationSnapshotEnvelopeProblem.InvalidPayload,
        )
        envelopeProblem as NavigationSnapshotEnvelopeProblem.InvalidPayload
        assertEquals("invalid_magic", envelopeProblem.code)

        val viewModel = AppViewModel(handle, fallback)

        assertEquals(fallback, viewModel.frames.value.appState)
        assertEquals(0L, viewModel.frames.value.navigationRevision)
        assertNull(viewModel.frames.value.navigationTransition)
        assertEquals(expectedPayload(fallback.navState), savedPayload(handle))
    }

    @Test
    fun `obsolete unknown route and invalid history are typed rejections with fallback rewrite`() {
        val fallback = appState(entry("fallback-home", Home), showInPlace = true)
        val cases = listOf(
            RejectedSnapshotCase(
                snapshot = NavStateSnapshot(
                    version = NavStateSnapshot.CURRENT_VERSION + 1,
                    entries = listOf(snapshotEntry("home", "home/v1")),
                ),
                expectedProblemType = SnapshotProblem.UnsupportedVersion::class.java,
            ),
            RejectedSnapshotCase(
                snapshot = NavStateSnapshot(
                    version = NavStateSnapshot.CURRENT_VERSION,
                    entries = listOf(
                        snapshotEntry("home", "home/v1"),
                        snapshotEntry("future", "future/v99"),
                    ),
                ),
                expectedProblemType = SnapshotProblem.RouteDecodeFailed::class.java,
            ),
            RejectedSnapshotCase(
                snapshot = NavStateSnapshot(
                    version = NavStateSnapshot.CURRENT_VERSION,
                    entries = listOf(
                        snapshotEntry("duplicate", "home/v1"),
                        snapshotEntry("duplicate", "accounts/v1"),
                    ),
                ),
                expectedProblemType = SnapshotProblem.InvalidState::class.java,
            ),
        )

        cases.forEach { case ->
            val handle = handleWithNavigationKey()
            handle[navigationKey(handle)] =
                NavigationSnapshotEnvelopeCodec.encode(case.snapshot)

            val problems = rejected(SavedNavigationStateStore(handle).restore())
            val problem = problems.single()
            assertTrue(problem is NavigationRestoreProblem.InvalidSnapshot)
            problem as NavigationRestoreProblem.InvalidSnapshot
            assertTrue(
                "Expected ${case.expectedProblemType}, got ${problem.problem}",
                case.expectedProblemType.isInstance(problem.problem),
            )

            val viewModel = AppViewModel(handle, fallback)

            assertEquals(fallback, viewModel.frames.value.appState)
            assertEquals(0L, viewModel.frames.value.navigationRevision)
            assertNull(viewModel.frames.value.navigationTransition)
            assertEquals(expectedPayload(fallback.navState), savedPayload(handle))
        }
    }

    @Test
    fun `new view model from copied primitive payload restores exact IDs and projections`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account-42", Account(accountId = 42))
        val firstHandle = SavedStateHandle()
        val first = AppViewModel(firstHandle, appState(home))

        changed(first.dispatch(NavAction.Push(home.id, accounts)))
        changed(first.dispatch(NavAction.Push(accounts.id, account)))
        val beforeProcessDeath = first.frames.value.appState.navState
        val key = navigationKey(firstHandle)
        val copiedPayload = ArrayList(savedPayload(firstHandle))
        val recreatedHandle = SavedStateHandle(mapOf(key to copiedPayload))
        val recreated = AppViewModel(
            recreatedHandle,
            appState(entry("unrelated-fallback", Home), showInPlace = true),
        )
        val restoredFrame = recreated.frames.value

        assertEquals(beforeProcessDeath.entries, restoredFrame.appState.navState.entries)
        assertTrue(restoredFrame.appState.showInPlace)
        assertEquals(0L, restoredFrame.navigationRevision)
        assertNull(restoredFrame.navigationTransition)
        projectionPolicies().forEach { policy ->
            assertEquals(
                NavProjector.project(beforeProcessDeath, policy),
                NavProjector.project(restoredFrame.appState.navState, policy),
            )
        }

        val details = entry("account-details-42", AccountDetails(accountId = 42))
        val firstFreshReduction = changed(
            recreated.dispatch(NavAction.Push(account.id, details)),
        )

        assertEquals(1L, recreated.frames.value.navigationRevision)
        assertEquals(
            NavTransitionIntent.Pushed(account.id, details.id),
            recreated.frames.value.navigationTransition,
        )
        assertEquals(firstFreshReduction.state, recreated.frames.value.appState.navState)
        assertEquals(
            firstFreshReduction.state,
            restored(SavedNavigationStateStore(recreatedHandle).restore()),
        )
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

    private fun expectedPayload(state: NavState): ArrayList<String> =
        NavigationSnapshotEnvelopeCodec.encode(
            snapshotSuccess(state.toSnapshot(DemoRouteCodec)),
        )

    private fun savedPayload(handle: SavedStateHandle): ArrayList<String> =
        handle.get<ArrayList<String>>(navigationKey(handle))?.let(::ArrayList)
            ?: throw AssertionError("Saved navigation payload is absent")

    private fun navigationKey(handle: SavedStateHandle): String {
        assertEquals(1, handle.keys().size)
        return handle.keys().single()
    }

    private fun handleWithNavigationKey(): SavedStateHandle = SavedStateHandle().also { handle ->
        assertSaved(
            SavedNavigationStateStore(handle).save(
                navState(entry("seed-home", Home)),
            ),
        )
    }

    private fun restored(result: NavigationRestoreResult): NavState = when (result) {
        NavigationRestoreResult.Missing -> throw AssertionError("Expected restored state, got missing")
        is NavigationRestoreResult.Restored -> result.navState
        is NavigationRestoreResult.Rejected -> throw AssertionError(
            "Expected restored state, got ${result.problems}",
        )
    }

    private fun rejected(
        result: NavigationRestoreResult,
    ): List<NavigationRestoreProblem> = when (result) {
        NavigationRestoreResult.Missing -> throw AssertionError("Expected rejection, got missing")
        is NavigationRestoreResult.Restored -> throw AssertionError(
            "Expected rejection, got ${result.navState}",
        )
        is NavigationRestoreResult.Rejected -> result.problems
    }

    private fun assertSaved(result: NavigationSaveResult) {
        when (result) {
            NavigationSaveResult.Saved -> Unit
            is NavigationSaveResult.Failed -> throw AssertionError(
                "Expected saved navigation, got ${result.problems}",
            )
        }
    }

    private fun changed(result: NavReduction): NavReduction.Changed = when (result) {
        is NavReduction.Changed -> result
        is NavReduction.Unchanged -> throw AssertionError(
            "Expected changed navigation, got ${result.reason}",
        )
    }

    private fun snapshotSuccess(
        result: SnapshotResult<NavStateSnapshot>,
    ): NavStateSnapshot = when (result) {
        is SnapshotResult.Success -> result.value
        is SnapshotResult.Failure -> throw AssertionError(
            "Expected snapshot, got ${result.problems}",
        )
    }

    private fun snapshotEntry(
        id: String,
        routeType: String,
        vararg arguments: Pair<String, String>,
    ): NavStateSnapshot.Entry = NavStateSnapshot.Entry(
        id = id,
        routeType = routeType,
        arguments = mapOf(*arguments),
    )

    private fun projectionPolicies(): List<NavigationLayoutPolicy> = listOf(
        NavigationLayoutPolicy { ContentPlacementDecision.root() },
        NavigationLayoutPolicy { request ->
            if (request.currentContent.entry.route is Home) {
                ContentPlacementDecision.childOf(request.currentContent.entry.id)
            } else {
                ContentPlacementDecision.root()
            }
        },
    )

    private data class RejectedSnapshotCase(
        val snapshot: NavStateSnapshot,
        val expectedProblemType: Class<out SnapshotProblem>,
    )
}
