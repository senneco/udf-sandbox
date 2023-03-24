package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.AccountDetailsScreenContent
import com.shmakov.udf.navigation.*

class AccountDetailsScreen(
    override val destination: AccountDetails
) : Screen(destination) {

    @Composable
    override fun Content(nestedNavState: NavState) {
        AccountDetailsScreenContent(
            id = destination.id,
        )
    }
}