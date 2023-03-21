package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.TransactionsScreenContent
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.Screen
import com.shmakov.udf.navigation.Transactions

class TransactionsScreen(
    override val destination: Transactions
) : Screen(destination) {

    @Composable
    override fun Content(nestedNavState: NavState) {
        TransactionsScreenContent()
    }
}