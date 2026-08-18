package com.shmakov.udf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.composable.common.AnimatedNavigation
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.ui.theme.UDFTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val currentAppState = appState
            val currentNavTransition = UdfApp.navTransition

            BackHandler(enabled = currentAppState.navState.entries.size > 1) {
                UdfApp.dispatchNavigation(NavAction.Pop)
            }


            UDFTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(
                        appState = currentAppState,
                        navTransition = currentNavTransition,
                    )
                }
            }
        }
    }

    @Composable
    private fun AppContent(
        appState: AppState,
        navTransition: NavTransitionIntent?,
    ) {
        AnimatedNavigation(
            navState = appState.navState,
            navTransition = navTransition,
        )
    }
}
