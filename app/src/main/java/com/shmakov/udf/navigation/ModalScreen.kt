package com.shmakov.udf.navigation

import androidx.compose.runtime.Composable

abstract class ModalScreen(
    open val entry: BackStackEntry,
) {

    /**
     * Renders one desired or retained modal presentation.
     *
     * [onDismissRequest] asks the state owner to remove this exact entry; it is not an animation
     * completion signal. [onExitFinished] is scoped to this retained presentation and means it is
     * safe for the renderer to release it. The latter may be called without a visible animation
     * when the presentation is already hidden.
     */
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
