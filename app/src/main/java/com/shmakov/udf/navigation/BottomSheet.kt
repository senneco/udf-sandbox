package com.shmakov.udf.navigation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.common.BottomSheetLayout

abstract class BottomSheet(
    destination: Destination,
) : ModalScreen(destination) {

    @Composable
    override fun ModalContent(
        targetState: ModalScreenState,
        onHide: () -> Unit
    ) {
        BottomSheetLayout(
            targetState = targetState,
            onHide = onHide,
        ) {
            Content()
        }
    }

    @Composable
    abstract fun ColumnScope.Content()
}