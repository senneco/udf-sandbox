package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.AccountsScreenContent
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.Screen

class AccountsScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {

    @Composable
    override fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        AccountsScreenContent(
            onAccountSelected = { accountId ->
                onNavigationAction(
                    NavAction.push(
                        expectedTopId = entry.id,
                        route = Account(accountId = accountId),
                    ),
                )
            },
        )
    }
}
