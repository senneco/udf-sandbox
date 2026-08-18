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
    currentEntry: BackStackEntry,
    nestedEntries: List<BackStackEntry>,
    lastNavActionType: NavActionType,
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
                Buttons(currentEntry = currentEntry)
            }
        } else {
            Column {
                Buttons(currentEntry = currentEntry)
            }
        }

        if (nestedEntries.isNotEmpty()) {
            AnimatedNavigation(
                entries = nestedEntries,
                into = RenderSlot.Nested(currentEntry.id),
                lastNavActionType = lastNavActionType,
            )
        }
    }
}

@Composable
fun Buttons(
    currentEntry: BackStackEntry,
) {
    Button(onClick = { navigateTo(currentEntry, Accounts) }) {
        Text(text = "Go to Accounts")
    }

    Button(
        onClick = { navigateTo(currentEntry, Transactions) },
    ) {
        Text(text = "Go to Transactions")
    }

    Button(onClick = { navigateTo(currentEntry, Cards) }) {
        Text(text = "Go to Cards")
    }
}

// TODO: move to reducer
private fun navigateTo(
    currentEntry: BackStackEntry,
    targetRoute: ContentRoute,
) {
    val currentEntryIndex = appState.navState.entries.indexOfFirst {
        it.id == currentEntry.id
    }

    if (currentEntryIndex == -1) return

    val navActionType = if (currentEntryIndex == appState.navState.entries.lastIndex) {
        NavActionType.Push
    } else {
        NavActionType.Replace
    }

    appState = appState.copy(
        navState = NavState.fromEntries(
            appState.navState.entries.take(currentEntryIndex + 1) +
                BackStackEntry.create(targetRoute),
        ).requireValid(),
        lastNavActionType = navActionType,
    )
}
