package com.shmakov.udf.composable.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.shmakov.udf.UdfApp
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.NavAction

@Composable
fun AccountsScreenContent(currentEntry: BackStackEntry) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Green)
    ) {
        Text(
            text = "Accounts Screen",
        )

        for (i in 1..10) {
            Button(onClick = { navigateTo(currentEntry, i) }) {
                Text(text = "Go to Account $i")
            }
        }
    }
}

private fun navigateTo(currentEntry: BackStackEntry, id: Int) {
    UdfApp.dispatchNavigation(
        NavAction.push(
            expectedTopId = currentEntry.id,
            route = Account(accountId = id),
        ),
    )
}
