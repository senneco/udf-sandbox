package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.CardsScreenContent
import com.shmakov.udf.navigation.Destination
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.Screen

class CardsScreen(destination: Destination) : Screen(destination) {

    @Composable
    override fun Content(nestedNavState: NavState) {
        CardsScreenContent()
    }
}