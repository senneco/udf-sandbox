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

@Composable
fun HomeScreenContent(
    destination: Destination,
    nestedNavState: NavState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Yellow)
    ) {
        Text(text = "Home Screen")

        Button(onClick = { navigateTo(Accounts) }) {
            Text(text = "Go to Accounts")
        }

        Button(
            onClick = { navigateTo(Transactions) },
        ) {
            Text(text = "Go to Transactions")
        }

        Button(onClick = { navigateTo(Cards) }) {
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

private fun navigateTo(
    destination: Destination,
) {
    val currentDestinationIndex = appState.navState.backStack.indexOf(destination)

    val navActionType = if (currentDestinationIndex == appState.navState.backStack.lastIndex) {
        NavActionType.Push
    } else {
        NavActionType.Replace
    }

    appState = appState.copy(
        navState = appState.navState.copy(
            backStack = appState.navState.backStack.take(currentDestinationIndex + 1) + destination,
            lastNavActionType = navActionType,
        )
    )
}