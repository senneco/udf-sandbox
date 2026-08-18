package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.HomeScreenContent
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.ContentRoute
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.Screen

class HomeScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {

    @Composable
    override fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        HomeScreenContent(
            childContent = childContent,
            onDestinationSelected = { destination: ContentRoute ->
                onNavigationAction(
                    NavAction.navigateFrom(
                        sourceId = entry.id,
                        route = destination,
                    ),
                )
            },
        )
    }
}
