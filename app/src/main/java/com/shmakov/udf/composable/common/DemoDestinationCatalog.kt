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
        is Home -> DestinationBinding.Content(HomeScreen(entry), parentInputsKey = Unit)
        is Accounts -> DestinationBinding.Content(AccountsScreen(entry), parentInputsKey = Unit)
        is Transactions -> DestinationBinding.Content(
            TransactionsScreen(entry),
            parentInputsKey = Unit,
        )
        is Transaction -> DestinationBinding.Content(TransactionScreen(entry), parentInputsKey = Unit)
        is Cards -> DestinationBinding.Content(CardsScreen(entry), parentInputsKey = Unit)
        is Card -> DestinationBinding.Content(CardScreen(entry), parentInputsKey = Unit)
        is AccountDetails -> DestinationBinding.Content(
            AccountDetailsScreen(entry),
            parentInputsKey = Unit,
        )
        is Account -> DestinationBinding.Modal(AccountBottomSheet(entry))
        else -> DestinationBinding.Unsupported(entry)
    }
}
