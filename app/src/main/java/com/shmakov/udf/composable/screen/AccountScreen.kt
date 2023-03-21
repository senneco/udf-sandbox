package com.shmakov.udf.composable.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.navigation.*
import kotlinx.coroutines.launch

class AccountScreen(
    override val destination: Account
) : ModalScreen(destination) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content(nestedNavState: NavState, targetState: SheetValue) {
        val coroutineScope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )

        if (targetState == SheetValue.Hidden) {
            if (sheetState.currentValue == targetState) {
                // TODO: if not return, then same bottom sheet will be shown
                return
            }

            LaunchedEffect(SheetValue.Hidden) {
                coroutineScope.launch { sheetState.expand() }
            }
        } else {
            LaunchedEffect(SheetValue.Expanded) {
                coroutineScope.launch { sheetState.hide() }
            }
        }

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
                            backStack = appState.navState.backStack.dropLast(1) + Account(2),
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