package com.shmakov.udf.navigation

import androidx.compose.runtime.Composable

abstract class ModalScreen(
    open val destination: Destination,
) {

    @Composable
    abstract fun ModalContent(
        targetState: ModalScreenState,
        onHide: () -> Unit,
    )
}

enum class ModalScreenState {
    Hidden,
    Shown,
}