package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.AccountDetailsScreenContent
import com.shmakov.udf.navigation.*

class AccountDetailsScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {

    @Composable
    override fun Content(
        nestedEntries: List<BackStackEntry>,
        navTransition: NavTransitionIntent?,
    ) {
        val route = entry.route as AccountDetails

        AccountDetailsScreenContent(
            id = route.accountId,
        )
    }
}
