package com.shmakov.udf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.composable.AnimatedNavigation
import com.shmakov.udf.navigation.NavActionType
import com.shmakov.udf.ui.theme.UDFTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            BackHandler(enabled = appState.navState.backStack.size > 1) {
                appState = appState.copy(
                    navState = appState.navState.copy(
                        backStack = appState.navState.backStack.dropLast(1),
                        lastNavActionType = NavActionType.Pop,
                    )
                )
            }


            UDFTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(appState)
                }
            }
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    private fun AppContent(appState: AppState) {
        AnimatedNavigation(appState.navState, null)
    }
}