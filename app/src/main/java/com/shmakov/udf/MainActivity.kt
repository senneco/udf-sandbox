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
import com.shmakov.udf.navigation.NavActionType
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.requireValid
import com.shmakov.udf.ui.theme.UDFTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            BackHandler(enabled = appState.navState.entries.size > 1) {
                val currentState = appState
                val currentEntries = currentState.navState.entries

                if (currentEntries.size > 1) {
                    appState = currentState.copy(
                        navState = NavState.fromEntries(
                            currentEntries.dropLast(1),
                        ).requireValid(),
                        lastNavActionType = NavActionType.Pop,
                    )
                }
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

    @Composable
    private fun AppContent(appState: AppState) {
        AnimatedNavigation(
            navState = appState.navState,
            lastNavActionType = appState.lastNavActionType,
        )
    }
}
