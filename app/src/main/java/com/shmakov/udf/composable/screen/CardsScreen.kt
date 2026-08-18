package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.CardsScreenContent
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.NavActionType
import com.shmakov.udf.navigation.Screen

class CardsScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {

    @Composable
    override fun Content(
        nestedEntries: List<BackStackEntry>,
        lastNavActionType: NavActionType,
    ) {
        CardsScreenContent()
    }
}
