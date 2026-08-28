package com.shmakov.udf.navigation

import androidx.compose.runtime.Composable

abstract class Screen(
    open val entry: BackStackEntry,
) {
    /**
     * Renders the content owned by this exact navigation entry.
     *
     * [childContent] is an independent renderer-owned slot. A screen places it but must not treat
     * child state as an input of its own content. Within one renderer composition, the first Screen
     * instance is retained while [entry], runtime type and the catalog binding's explicit parent
     * input key are unchanged: later instances with the same identity are not an update channel. A
     * child-only update therefore does not create another committed pass of this parent content.
     * Changing parent data must come from observable Compose state read inside this function, or
     * from a changed entry, screen type or explicit parent input key.
     */
    @Composable
    abstract fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    )
}
