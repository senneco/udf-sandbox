package com.shmakov.udf.navigation

import androidx.compose.runtime.Composable

abstract class Screen(
    val destination: Destination,
) {

    @Composable
    open fun whereToShowChild(
        whereShowCurrentDestination: Destination?,
        childDestination: Destination,
    ): Destination? {
        return null
    }

    @Composable
    abstract fun Content(nestedNavState: NavState)
}