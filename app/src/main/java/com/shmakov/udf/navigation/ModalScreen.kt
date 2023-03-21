package com.shmakov.udf.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable

abstract class ModalScreen(
    open val destination: Destination,
) {

    @Composable
    open fun whereToShowChild(
        whereShowCurrentDestination: Destination,
        childDestination: Destination,
    ): Destination {
        return AppRoot
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    abstract fun Content(
        nestedNavState: NavState,
        targetState: SheetValue,
    )
}