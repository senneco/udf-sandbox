package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.AccountsScreenContent
import com.shmakov.udf.Destination
import com.shmakov.udf.navigation.Screen

class AccountsScreen(override val destination: Destination) : Screen {

    @Composable
    override fun whereToShowChild(): Long? {
        return null
    }

    @Composable
    override fun content() {
        AccountsScreenContent(destination = destination)
    }
}