package com.shmakov.udf.composable.content

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.shmakov.udf.UdfApp
import com.shmakov.udf.composable.BottomSheet
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.ModalScreenState
import com.shmakov.udf.navigation.NavActionType

@Composable
fun AccountBottomSheetContent(
    accountId: Int,
    targetState: ModalScreenState,
    onHide: () -> Unit,
) {
    // TODO: may be move this to screen level? But then need to think --
    //  is it required for some state and hide in other places??
    BottomSheet(
        targetState = targetState,
        onHide = onHide,
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
        ) {
            Text(text = "Go to details")
        }
    }
}