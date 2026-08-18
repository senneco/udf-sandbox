package com.shmakov.udf.composable.content

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
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.NavActionType
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.requireValid

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
        navState = NavState.fromEntries(
            appState.navState.entries + BackStackEntry.create(Account(accountId = id)),
        ).requireValid(),
        lastNavActionType = NavActionType.Push,
    )
}
