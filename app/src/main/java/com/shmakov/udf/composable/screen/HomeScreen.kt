package com.shmakov.udf.composable.screen

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.shmakov.udf.composable.content.HomeScreenContent
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.NavActionType
import com.shmakov.udf.navigation.RenderSlot
import com.shmakov.udf.navigation.Screen

class HomeScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {

    @Composable
    override fun whereToShowChild(
        currentSlot: RenderSlot,
        childEntry: BackStackEntry,
    ): RenderSlot {
        val configuration = LocalConfiguration.current

        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (isLandscape) {
            RenderSlot.Nested(entry.id)
        } else {
            currentSlot
        }
    }

    @Composable
    override fun Content(
        nestedEntries: List<BackStackEntry>,
        lastNavActionType: NavActionType,
    ) {
        HomeScreenContent(
            currentEntry = entry,
            nestedEntries = nestedEntries,
            lastNavActionType = lastNavActionType,
        )
    }
}
