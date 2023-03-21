package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.AccountsScreenContent
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.Screen

class AccountsScreen(
    override val destination: Accounts
) : Screen(destination) {

    @Composable
    override fun Content(nestedNavState: NavState) {
        AccountsScreenContent()
    }
}