package com.shmakov.udf.navigation

import androidx.compose.runtime.Composable

abstract class Screen(
    open val destination: Destination,
) {

    @Composable
    open fun whereToShowChild(
        whereShowCurrentDestination: Destination,
        childDestination: Destination,
    ): Destination {
        return AppRoot
    }

    @Composable
    abstract fun Content(nestedNavState: NavState)
}