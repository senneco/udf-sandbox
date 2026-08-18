package com.shmakov.udf.navigation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.common.BottomSheetLayout

abstract class BottomSheet(
    entry: BackStackEntry,
) : ModalScreen(entry) {

    @Composable
    override fun ModalContent(
        targetState: ModalScreenState,
        entrance: ModalEntrance,
        onDismissRequest: () -> Unit,
        onExitFinished: () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        BottomSheetLayout(
            targetState = targetState,
            entrance = entrance,
            onDismissRequest = onDismissRequest,
            onExitFinished = onExitFinished,
        ) {
            Content(onNavigationAction = onNavigationAction)
        }
    }

    @Composable
    abstract fun ColumnScope.Content(
        onNavigationAction: (NavAction) -> Unit,
    )
}
