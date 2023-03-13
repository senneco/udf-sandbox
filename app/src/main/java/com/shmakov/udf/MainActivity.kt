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
import com.shmakov.udf.composable.AnimatedNavGraph
import com.shmakov.udf.ui.theme.UDFTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            BackHandler(enabled = appState.destination.childDestination != null) {
                appState = appState.copy(
                    destination = appState.destination.copy(
                        childDestination = null
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
        AnimatedNavGraph(appState.destination, null)
    }
}