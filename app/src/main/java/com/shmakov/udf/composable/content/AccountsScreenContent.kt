package com.shmakov.udf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.NavActionType

@Composable
fun AccountsScreenContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Green)
    ) {
        Text(
            text = "Accounts Screen",
        )

        for (i in 1..10) {
            Button(onClick = { navigateTo(i) }) {
                Text(text = "Go to Account $i")
            }
        }
    }
}

private fun navigateTo(id: Int) {
    appState = appState.copy(
        navState = appState.navState.copy(
            backStack = appState.navState.backStack + Account(id),
            lastNavActionType = NavActionType.Push,
        )
    )
}