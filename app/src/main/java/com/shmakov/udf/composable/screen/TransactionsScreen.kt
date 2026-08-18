package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.TransactionScreenContent
import com.shmakov.udf.composable.content.TransactionsScreenContent
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.Screen
import com.shmakov.udf.navigation.Transaction

class TransactionsScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {

    @Composable
    override fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        TransactionsScreenContent()
    }
}

class TransactionScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {

    @Composable
    override fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        val route = entry.route as Transaction
        TransactionScreenContent(transactionId = route.transactionId)
    }
}
