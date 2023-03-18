package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.TransactionsScreenContent
import com.shmakov.udf.navigation.Destination
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.Screen

class TransactionsScreen(destination: Destination) : Screen(destination) {

    @Composable
    override fun Content(nestedNavState: NavState) {
        TransactionsScreenContent()
    }
}