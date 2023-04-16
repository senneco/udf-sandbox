package com.shmakov.udf.composable.content

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.composable.common.AnimatedNavigation
import com.shmakov.udf.navigation.*

@Composable
fun HomeScreenContent(
    currentDestination: Destination,
    nestedNavState: NavState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Yellow)
    ) {
        Text(text = "Home Screen")

        val configuration = LocalConfiguration.current

        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            Row {
                Buttons(currentDestination = currentDestination)
            }
        } else {
            Column {
                Buttons(currentDestination = currentDestination)
            }
        }

        if (nestedNavState.backStack.isNotEmpty()) {
            AnimatedNavigation(navState = nestedNavState, into = currentDestination)
        }
    }
}

@Composable
fun Buttons(
    currentDestination: Destination,
) {
    Button(onClick = { navigateTo(currentDestination, Accounts) }) {
        Text(text = "Go to Accounts")
    }

    Button(
        onClick = { navigateTo(currentDestination, Transactions) },
    ) {
        Text(text = "Go to Transactions")
    }

    Button(onClick = { navigateTo(currentDestination, Cards) }) {
        Text(text = "Go to Cards")
    }
}

// TODO: move to reducer
private fun navigateTo(
    currentDestination: Destination,
    targetDestination: Destination,
) {
    val currentDestinationIndex = appState.navState.backStack.indexOf(currentDestination)

    val navActionType = if (currentDestinationIndex == appState.navState.backStack.lastIndex) {
        NavActionType.Push
    } else {
        NavActionType.Replace
    }

    appState = appState.copy(
        navState = appState.navState.copy(
            backStack = appState.navState.backStack.take(currentDestinationIndex + 1) + targetDestination,
            lastNavActionType = navActionType,
        )
    )
}