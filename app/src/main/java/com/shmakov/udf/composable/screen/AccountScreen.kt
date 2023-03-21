package com.shmakov.udf.composable.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.NavActionType
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.Screen

class AccountScreen(
    override val destination: Account
) : Screen(destination) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content(nestedNavState: NavState) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )

        ModalBottomSheet(
            onDismissRequest = {
                appState = appState.copy(
                    navState = appState.navState
                        .copy(
                            backStack = appState.navState
                                .backStack
                                .filter { it != destination },
                            lastNavActionType = NavActionType.Pop,
                        )
                )
            },
            sheetState = sheetState,
        ) {
            Text(
                text = "Account #${destination.id}",
                modifier = Modifier.padding(16.dp),
            )

            if (destination.id == 1) {
                Button(onClick = {
                    appState = appState.copy(
                        navState = appState.navState.copy(
                            backStack = appState.navState.backStack + Account(2),
                            lastNavActionType = NavActionType.Push,
                        )
                    )
                }) {
                    Text(text = "Go to next")
                }
            }
        }

        LaunchedEffect(Unit) {
            sheetState.expand()
        }
    }
}