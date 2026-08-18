package com.shmakov.udf.navigation

import androidx.compose.runtime.Composable

abstract class Screen(
    open val entry: BackStackEntry,
) {
    @Composable
    abstract fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    )
}
