package com.shmakov.udf

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
    initialState: AppState = demoAppState(),
) : ViewModel() {
    private val store = AppStore(initialState)

    val frames: StateFlow<AppStateFrame> = store.frames

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
