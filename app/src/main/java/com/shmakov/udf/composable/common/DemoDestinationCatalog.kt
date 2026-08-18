package com.shmakov.udf.composable.common

import com.shmakov.udf.composable.screen.AccountBottomSheet
import com.shmakov.udf.composable.screen.AccountDetailsScreen
import com.shmakov.udf.composable.screen.AccountsScreen
import com.shmakov.udf.composable.screen.CardScreen
import com.shmakov.udf.composable.screen.CardsScreen
import com.shmakov.udf.composable.screen.HomeScreen
import com.shmakov.udf.composable.screen.TransactionScreen
import com.shmakov.udf.composable.screen.TransactionsScreen
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Card
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.Transaction
import com.shmakov.udf.navigation.Transactions

/** Complete renderer catalog for the routes declared by the demo application. */
internal object DemoDestinationCatalog : DestinationCatalog {
    override fun resolve(entry: BackStackEntry): DestinationBinding = when (entry.route) {
        is Home -> DestinationBinding.Content(HomeScreen(entry))
        is Accounts -> DestinationBinding.Content(AccountsScreen(entry))
        is Transactions -> DestinationBinding.Content(TransactionsScreen(entry))
        is Transaction -> DestinationBinding.Content(TransactionScreen(entry))
        is Cards -> DestinationBinding.Content(CardsScreen(entry))
        is Card -> DestinationBinding.Content(CardScreen(entry))
        is AccountDetails -> DestinationBinding.Content(AccountDetailsScreen(entry))
        is Account -> DestinationBinding.Modal(AccountBottomSheet(entry))
        else -> DestinationBinding.Unsupported(entry)
    }
}
