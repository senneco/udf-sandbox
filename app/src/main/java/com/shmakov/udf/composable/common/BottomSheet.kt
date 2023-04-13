package com.shmakov.udf.composable

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.*
import androidx.compose.runtime.*
import com.shmakov.udf.navigation.ModalScreenState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BottomSheet(
    targetState: ModalScreenState,
    onHide: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shown = remember { AtomicBoolean(false) }

    val state = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
    )

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        snapshotFlow { state.currentValue }
            // Drop first hidden state
            .drop(1)
            .collect {
                if (it == ModalBottomSheetValue.Hidden) {
                    onHide()
                }
            }
    }

    if (targetState == ModalScreenState.Hidden && shown.get() && !state.isVisible) {
        onHide()

        return
    }

    ModalBottomSheetLayout(
        sheetState = state,
        sheetContent = content,
    ) {}


    if (state.currentValue != ModalBottomSheetValue.Hidden && targetState == ModalScreenState.Hidden) {
        LaunchedEffect(ModalScreenState.Hidden) {
            scope.launch { state.hide() }
        }
    }

    if (!shown.getAndSet(true)) {
        LaunchedEffect(ModalScreenState.Shown) {
            scope.launch {
                state.show()
            }
        }
    }
}