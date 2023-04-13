package com.shmakov.udf.navigation

import androidx.compose.runtime.Composable

abstract class ModalScreen(
    open val destination: Destination,
) {

    // TODO: can we show child right in this modal screen?
    // TODO: should children always be shown in AppRoot, or in something else??
    @Composable
    open fun whereToShowChild(
        whereShowCurrentDestination: Destination,
        childDestination: Destination,
    ): Destination {
        return AppRoot
    }

    @Composable
    abstract fun Content(
        // TODO: is nestedNavState really required here?
        nestedNavState: NavState,
        targetState: ModalScreenState,
        onHide: () -> Unit,
    )
}

enum class ModalScreenState {
    Hidden,
    Shown,
}