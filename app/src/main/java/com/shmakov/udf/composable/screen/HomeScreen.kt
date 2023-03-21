package com.shmakov.udf.composable.screen

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.shmakov.udf.composable.content.HomeScreenContent
import com.shmakov.udf.navigation.Destination
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.Screen

class HomeScreen(destination: Destination) : Screen(destination) {

    @Composable
    override fun whereToShowChild(
        whereShowCurrentDestination: Destination?,
        childDestination: Destination
    ): Destination? {
        val configuration = LocalConfiguration.current

        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (isLandscape) {
            destination
        } else {
            whereShowCurrentDestination
        }
    }

    @Composable
    override fun Content(nestedNavState: NavState) {
        HomeScreenContent(destination, nestedNavState)
    }
}