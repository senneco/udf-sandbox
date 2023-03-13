package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.Destination
import com.shmakov.udf.TransactionsScreenContent
import com.shmakov.udf.navigation.Screen

class TransactionsScreen(override val destination: Destination) : Screen {

    @Composable
    override fun whereToShowChild(): Long? {
        return null
    }

    @Composable
    override fun content() {
        TransactionsScreenContent(destination = destination)
    }
}