package com.shmakov.udf.navigation

import androidx.compose.runtime.Composable

abstract class ModalScreen(
    open val entry: BackStackEntry,
) {

    /**
     * Renders one desired or retained modal presentation.
     *
     * [entrance] applies only while [targetState] is [ModalScreenState.Shown]. An exiting
     * presentation must converge to Hidden regardless of its entrance value.
     *
     * [onDismissRequest] asks the state owner to remove this exact entry; it is not an animation
     * completion signal. [onExitFinished] is scoped to this retained presentation and means it is
     * safe for the renderer to release it. The latter may be called without a visible animation
     * when the presentation is already hidden.
     */
    @Composable
    abstract fun ModalContent(
        targetState: ModalScreenState,
        entrance: ModalEntrance,
        onDismissRequest: () -> Unit,
        onExitFinished: () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    )
}

/**
 * Selects whether a desired modal enters immediately or through its presentation animation.
 *
 * This is process-local render metadata, not durable navigation state, and must not be serialized
 * into [NavState] or its snapshot. Recomposition preserves the already accepted entrance instead
 * of replaying [Animate]. [Snap] must render the final shown state immediately, including when it
 * replaces unfinished entrance or exit motion for the same retained modal presentation.
 */
enum class ModalEntrance {
    /** Render the desired modal directly in its final presented state. */
    Snap,

    /** Run the modal's normal entrance animation from its hidden state. */
    Animate,
}

enum class ModalScreenState {
    Hidden,
    Shown,
}
