package com.shmakov.udf.composable.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.composable.AnimatedNavigation
import com.shmakov.udf.navigation.*
import timber.log.Timber

@Composable
fun HomeScreenContent(
    destination: Destination,
    nestedNavState: NavState,
) {
    Timber.i("***-*** draw Home Screen")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Yellow)
    ) {
        Text(text = "Home Screen")

        Button(onClick = {
            appState = appState.copy(
                navState = appState.navState.copy(
                    backStack = appState.navState.backStack.takeWhile { it != destination } + destination + Accounts,
                    lastNavActionType = NavActionType.Push,
                )
            )
        }) {
            Text(text = "Go to Accounts")
        }

        Button(onClick = {
            appState = appState.copy(
                navState = appState.navState.copy(
                    backStack = appState.navState.backStack.takeWhile { it != destination } + destination + Transactions,
                    lastNavActionType = NavActionType.Push,
                )
            )
        }) {
            Text(text = "Go to Transactions")
        }

        Button(onClick = {
            appState = appState.copy(
                navState = appState.navState.copy(
                    backStack = appState.navState.backStack.takeWhile { it != destination } + destination + Cards,
                    lastNavActionType = NavActionType.Push,
                )
            )
        }) {
            Text(text = "Go to Cards")
        }

        Checkbox(checked = appState.showInPlace, onCheckedChange = {
            appState = appState.copy(showInPlace = it)
        })

        if (nestedNavState.backStack.isNotEmpty()) {
            AnimatedNavigation(navState = nestedNavState, into = destination)
        }
    }
}