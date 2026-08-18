package com.shmakov.udf.navigation

import androidx.compose.runtime.Composable

abstract class ModalScreen(
    open val entry: BackStackEntry,
) {

    @Composable
    abstract fun ModalContent(
        targetState: ModalScreenState,
        onDismissRequest: () -> Unit,
        onExitFinished: () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    )
}

enum class ModalScreenState {
    Hidden,
    Shown,
}
