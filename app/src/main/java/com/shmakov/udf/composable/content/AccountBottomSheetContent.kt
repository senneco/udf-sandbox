package com.shmakov.udf.composable.content

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import com.shmakov.udf.UdfApp
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.NavActionType

@Composable
fun ColumnScope.AccountBottomSheetContent(
    accountId: Int,
) {
    if (accountId < 9) {
        Button(
            onClick = {
                UdfApp.appState = UdfApp.appState.copy(
                    navState = UdfApp.appState.navState.copy(
                        backStack = UdfApp.appState.navState.backStack + Account(accountId = accountId + 1),
                        lastNavActionType = NavActionType.Push,
                    )
                )
            },
        ) {
            Text(text = "Go to Account #${accountId + 1}")
        }
    }

    Button(
        onClick = {
            UdfApp.appState = UdfApp.appState.copy(
                navState = UdfApp.appState.navState.copy(
                    backStack = UdfApp.appState.navState.backStack + AccountDetails(
                        accountId = accountId
                    ),
                    lastNavActionType = NavActionType.Push,
                )
            )
        },
        modifier = Modifier
            .align(CenterHorizontally)
    ) {
        Text(text = "Go to details")
    }
}