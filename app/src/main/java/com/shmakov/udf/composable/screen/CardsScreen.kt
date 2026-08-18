package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.CardScreenContent
import com.shmakov.udf.composable.content.CardsScreenContent
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Card
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.Screen

class CardsScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {

    @Composable
    override fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        CardsScreenContent()
    }
}

class CardScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {

    @Composable
    override fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        val route = entry.route as Card
        CardScreenContent(cardId = route.cardId)
    }
}
