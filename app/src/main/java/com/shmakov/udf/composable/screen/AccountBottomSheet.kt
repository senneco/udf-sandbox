package com.shmakov.udf.composable.screen

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.AccountBottomSheetContent
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.BottomSheet
import com.shmakov.udf.navigation.NavAction

class AccountBottomSheet(
    override val entry: BackStackEntry,
) : BottomSheet(entry) {

    @Composable
    override fun ColumnScope.Content(
        onNavigationAction: (NavAction) -> Unit,
    ) {
        val route = entry.route as Account

        AccountBottomSheetContent(
            accountId = route.accountId,
            onNextAccount = { nextAccountId ->
                onNavigationAction(
                    NavAction.push(
                        expectedTopId = entry.id,
                        route = Account(accountId = nextAccountId),
                    ),
                )
            },
            onDetailsRequested = {
                onNavigationAction(
                    NavAction.push(
                        expectedTopId = entry.id,
                        route = AccountDetails(accountId = route.accountId),
                    ),
                )
            },
        )
    }
}
