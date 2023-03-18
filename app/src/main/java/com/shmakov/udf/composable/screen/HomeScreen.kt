package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.composable.content.HomeScreenContent
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.navigation.Destination
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.Screen

class HomeScreen(destination: Destination) : Screen(destination) {

    @Composable
    override fun whereToShowChild(childDestination: Destination): Destination? {
        return if (appState.showInPlace) {
            destination
        } else {
            // There should be parent container of home screen! Not the null!
            null
        }
    }

    @Composable
    override fun Content(nestedNavState: NavState) {
        HomeScreenContent(destination, nestedNavState)
    }
}