package com.shmakov.udf

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shmakov.udf.composable.common.DemoDestinationCatalog
import com.shmakov.udf.composable.common.DemoNavigationLayoutPolicies
import com.shmakov.udf.composable.common.DemoTabNavigation
import com.shmakov.udf.composable.common.DemoTabUi
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.ui.theme.UDFTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A recreated Activity keeps its already hydrated ViewModel. Replaying the original launch
        // intent would create fresh entry IDs and overwrite the user's restored tab histories.
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            val frame by appViewModel.frames.collectAsStateWithLifecycle()
            val backPlan = DemoTabBackPlanner.plan(
                frame = frame,
                primaryTabId = DemoTabGraph.primaryTabId,
            )

            BackHandler {
                when (
                    val execution = DemoTabBackPlanner.resolve(
                        plan = backPlan,
                        currentRevision = appViewModel.frames.value.revision,
                    )
                ) {
                    is DemoTabBackExecution.Dispatch -> appViewModel.dispatch(execution.action)
                    DemoTabBackExecution.FinishHost -> finish()
                    is DemoTabBackExecution.ConfigurationRejected -> Log.e(
                        LOG_TAG,
                        "Ignoring Back after invalid tab configuration: ${execution.problem}",
                    )
                    is DemoTabBackExecution.Stale -> Unit
                }
            }

            UDFTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppContent(
                        frame = frame,
                        onNavigationAction = remember(appViewModel) {
                            { action: TabAction -> appViewModel.dispatch(action) }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            appViewModel.handleDeepLink(intent.dataString)
        }
    }

    @Composable
    private fun AppContent(
        frame: TabNavigationFrame,
        onNavigationAction: (TabAction) -> Unit,
    ) {
        val configuration = LocalConfiguration.current
        val layoutPolicy = if (
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        ) {
            DemoNavigationLayoutPolicies.expandedPane
        } else {
            DemoNavigationLayoutPolicies.singlePane
        }

        DemoTabNavigation(
            frame = frame,
            tabUiById = demoTabUi,
            layoutPolicy = layoutPolicy,
            destinationCatalog = DemoDestinationCatalog,
            onAction = onNavigationAction,
        )
    }

    private companion object {
        const val LOG_TAG = "UdfNavigation"

        val demoTabUi = mapOf(
            DemoTabGraph.accountsTabId to DemoTabUi(
                labelRes = R.string.demo_tab_accounts,
                icon = Icons.Default.AccountCircle,
            ),
            DemoTabGraph.cardsTabId to DemoTabUi(
                labelRes = R.string.demo_tab_cards,
                icon = Icons.Default.Star,
            ),
        )
    }
}
