package com.shmakov.udf

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavReduction
import com.shmakov.udf.navigation.NavState
import kotlinx.coroutines.flow.StateFlow

/** Activity-scoped lifecycle owner for the demo application state. */
internal class AppViewModel(
    savedStateHandle: SavedStateHandle,
    fallbackState: AppState,
) : ViewModel() {
    constructor(savedStateHandle: SavedStateHandle) : this(
        savedStateHandle = savedStateHandle,
        fallbackState = demoAppState(),
    )

    private val savedNavigationStateStore = SavedNavigationStateStore(savedStateHandle)
    private val initialState = when (val restoration = savedNavigationStateStore.restore()) {
        NavigationRestoreResult.Missing,
        is NavigationRestoreResult.Rejected,
        -> fallbackState

        is NavigationRestoreResult.Restored -> fallbackState.copy(
            navState = restoration.navState,
        )
    }
    private val store = AppStore(
        initialState = initialState,
        persistNavigationState = savedNavigationStateStore::save,
    )

    init {
        // Persist even a fresh fallback so its generated entry IDs survive the first recreation.
        savedNavigationStateStore.save(initialState.navState)
    }

    val frames: StateFlow<AppStateFrame> = store.frames

    @MainThread
    fun dispatch(action: NavAction): NavReduction = store.dispatch(action)
}

private fun demoAppState(): AppState = AppState(
    navState = NavState.history(
        root = Home,
        Accounts,
        Account(accountId = 1),
    ),
    showInPlace = false,
)
