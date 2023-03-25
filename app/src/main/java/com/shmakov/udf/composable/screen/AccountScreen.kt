package com.shmakov.udf.composable.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.navigation.*
import kotlinx.coroutines.launch

class AccountScreen(
    override val destination: Account
) : ModalScreen(destination) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content(
        nestedNavState: NavState,
        targetState: SheetValue,
        onHide: () -> Unit,
    ) {
        val coroutineScope = rememberCoroutineScope()
        val sheetState = rememberBottomSheetScaffoldState(

        )

        if (targetState == SheetValue.Hidden) {
            LaunchedEffect(SheetValue.Hidden) {
                coroutineScope.launch { sheetState.bottomSheetState.hide() }
            }
        }

        // May be it isn't required
        BottomSheetScaffold(
            scaffoldState = sheetState,
            sheetPeekHeight = 0.dp,
            sheetContent = {
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

                Button(onClick = {
                    appState = appState.copy(
                        navState = appState.navState.copy(
                            backStack = appState.navState.backStack + AccountDetails(
                                destination.id
                            ),
                            lastNavActionType = NavActionType.Push,
                        )
                    )
                }) {
                    Text(text = "Go to details")
                }
            },
            containerColor = Color.Gray.copy(alpha = 0.42f),
            modifier = Modifier.background(Color.Unspecified)
        ) {
            Box(
                modifier = Modifier
                    .background(color = Color.Gray.copy(alpha = 0.42f))
            )
        }


        /*ModalBottomSheet(
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

            Button(onClick = {
                appState = appState.copy(
                    navState = appState.navState.copy(
                        backStack = appState.navState.backStack + AccountDetails(
                            destination.id
                        ),
                        lastNavActionType = NavActionType.Push,
                    )
                )
            }) {
                Text(text = "Go to details")
            }
        }*/

        if (targetState == SheetValue.Expanded && sheetState.bottomSheetState.currentValue != targetState && sheetState.bottomSheetState.targetValue != targetState) {
            LaunchedEffect(SheetValue.Expanded) {
                coroutineScope.launch {
                    sheetState.bottomSheetState.expand()
                }
            }
        }
    }
}