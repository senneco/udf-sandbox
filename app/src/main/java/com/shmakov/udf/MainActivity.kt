package com.shmakov.udf

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shmakov.udf.composable.common.AnimatedNavigation
import com.shmakov.udf.composable.common.DemoNavigationLayoutPolicies
import com.shmakov.udf.composable.common.NavigationProjectionFailure
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavProjector
import com.shmakov.udf.navigation.NavProjectionResult
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.ui.theme.UDFTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val frame by appViewModel.frames.collectAsStateWithLifecycle()

            BackHandler(enabled = frame.appState.navState.entries.size > 1) {
                appViewModel.dispatch(NavAction.Pop)
            }

            UDFTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(
                        appState = frame.appState,
                        navigationRevision = frame.navigationRevision,
                        navTransition = frame.navigationTransition,
                        onNavigationAction = { action -> appViewModel.dispatch(action) },
                    )
                }
            }
        }
    }

    @Composable
    private fun AppContent(
        appState: AppState,
        navigationRevision: Long,
        navTransition: NavTransitionIntent?,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        val configuration = LocalConfiguration.current
        val layoutPolicy = if (
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        ) {
            DemoNavigationLayoutPolicies.expandedPane
        } else {
            DemoNavigationLayoutPolicies.singlePane
        }

        when (val projection = NavProjector.project(appState.navState, layoutPolicy)) {
            is NavProjectionResult.Success -> AnimatedNavigation(
                renderTarget = NavigationRenderTarget(
                    navigationRevision = navigationRevision,
                    tree = projection.tree,
                    transitionIntent = navTransition,
                ),
                onNavigationAction = onNavigationAction,
            )

            is NavProjectionResult.Failure -> NavigationProjectionFailure(projection.problem)
        }
    }
}
