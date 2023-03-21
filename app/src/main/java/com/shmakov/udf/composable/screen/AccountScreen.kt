package com.shmakov.udf.composable.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.NavActionType
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.Screen
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

class AccountScreen(
    override val destination: Account
) : Screen(destination) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content(nestedNavState: NavState) {
        val scope = rememberCoroutineScope()
        val scaffoldState = rememberBottomSheetScaffoldState()

        // TODO: collapsed sheet has PartiallyExpanded state - check in next releases
        BackHandler(enabled = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
            scope.launch {
                // TODO: check in new releases - is hide works (not there isn't any UI changes)
                scaffoldState.bottomSheetState.hide()
            }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetContent = @Composable {
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
            },
            sheetPeekHeight = 0.dp,
            containerColor = Color.Unspecified,
            contentColor = Color.Unspecified,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Color.Red.copy(alpha = 0.5f))
            )
        }

        LaunchedEffect(1) {
            scope.launch { scaffoldState.bottomSheetState.expand() }
        }

        LaunchedEffect(2) {
            snapshotFlow { scaffoldState.bottomSheetState.currentValue }
                .onEach { Timber.i("***-*** $it") }
                // TODO: collapsed sheet has PartiallyExpanded state - check in next releases
                .filter { sheetValue -> sheetValue == SheetValue.PartiallyExpanded }
                .drop(1)
                .collect {
                    appState = appState.copy(
                        navState = appState.navState.copy(
                            backStack = appState.navState.backStack.dropLast(1),
                            lastNavActionType = NavActionType.Pop,
                        )
                    )
                }
        }
    }
}