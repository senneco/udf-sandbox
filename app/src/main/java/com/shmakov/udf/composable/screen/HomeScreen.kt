package com.shmakov.udf.composable.screen

import androidx.compose.runtime.Composable
import com.shmakov.udf.Destination
import com.shmakov.udf.HomeScreenContent
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.navigation.Screen

class HomeScreen(override val destination: Destination) : Screen {

    @Composable
    override fun whereToShowChild(): Long? {
        return if (appState.showInPlace) {
            destination.id
        } else {
            null
        }
    }

    @Composable
    override fun content() {
        HomeScreenContent(destination = destination)
    }
}