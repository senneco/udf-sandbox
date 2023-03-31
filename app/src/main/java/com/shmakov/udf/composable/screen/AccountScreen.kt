package com.shmakov.udf.composable.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.navigation.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AccountScreen(
    override val destination: Account
) : ModalScreen(destination) {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
    @Composable
    override fun Content(
        nestedNavState: NavState,
        targetState: SheetValue,
        onHide: () -> Unit,
    ) {
        val shown = remember { AtomicBoolean(false) }

        val state = rememberModalBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
        )
        val scope = rememberCoroutineScope()

        if (shown.get() && !state.isVisible) {
            onHide()

            return
        }

        ModalBottomSheetLayout(
            sheetState = state,
            sheetContent = {
                Text(
                    text = "Account #${destination.id}",
                    modifier = Modifier.padding(16.dp),
                )

                if (destination.id < 9) {
                    Button(onClick = {
                        appState = appState.copy(
                            navState = appState.navState.copy(
                                backStack = appState.navState.backStack + Account(destination.id + 1),
                                lastNavActionType = NavActionType.Push,
                            )
                        )
                    }) {
                        Text(text = "Go to Account #${destination.id + 1}")
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
            }
        ) {}


        if (state.currentValue != ModalBottomSheetValue.Hidden && targetState == SheetValue.Hidden) {
            LaunchedEffect(SheetValue.Hidden) {
                scope.launch { state.hide() }
            }
        }

        if (!shown.getAndSet(true)) {
            LaunchedEffect(SheetValue.Expanded) {
                scope.launch {
                    state.show()
                }
            }
        }
    }
}