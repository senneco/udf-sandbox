package com.shmakov.udf.navigation

import androidx.compose.runtime.Composable

abstract class Screen(
    open val entry: BackStackEntry,
) {

    @Composable
    internal open fun whereToShowChild(
        currentSlot: RenderSlot,
        childEntry: BackStackEntry,
    ): RenderSlot {
        return RenderSlot.Root
    }

    @Composable
    abstract fun Content(
        nestedEntries: List<BackStackEntry>,
        navTransition: NavTransitionIntent?,
        onNavigationAction: (NavAction) -> Unit,
    )
}

internal sealed interface RenderSlot {
    object Root : RenderSlot

    data class Nested(val entryId: EntryId) : RenderSlot
}
