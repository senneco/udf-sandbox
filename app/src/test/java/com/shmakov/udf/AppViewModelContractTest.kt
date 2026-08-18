package com.shmakov.udf

import androidx.lifecycle.ViewModel
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavReduction
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.Route
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
        val viewModel = AppViewModel(initial)

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
        val first = AppViewModel(initial)
        val second = AppViewModel(initial)

        val accounts = entry("accounts", Accounts)
        first.dispatch(NavAction.Push(home.id, accounts))

        assertEquals(2, first.frames.value.appState.navState.entries.size)
        assertSame(initial, second.frames.value.appState)
        assertEquals(1, second.frames.value.appState.navState.entries.size)
        assertNull(second.frames.value.navigationTransition)
    }

    @Test
    fun `default view model publishes the demo state`() {
        val viewModel = AppViewModel()
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
}
