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

        if (targetState == SheetValue.Hidden && shown.get() && !state.isVisible) {
            onHide()

            return
        }

        ModalBottomSheetLayout(
            sheetState = state,
            sheetContent = {
                Text(
                    text = "Account #${destination.accountId}",
                    modifier = Modifier.padding(16.dp),
                )

                if (destination.accountId < 9) {
                    Button(onClick = {
                        appState = appState.copy(
                            navState = appState.navState.copy(
                                backStack = appState.navState.backStack + Account(accountId = destination.accountId + 1),
                                lastNavActionType = NavActionType.Push,
                            )
                        )
                    }) {
                        Text(text = "Go to Account #${destination.accountId + 1}")
                    }
                }

                Button(onClick = {
                    appState = appState.copy(
                        navState = appState.navState.copy(
                            backStack = appState.navState.backStack + AccountDetails(
                                accountId = destination.accountId
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