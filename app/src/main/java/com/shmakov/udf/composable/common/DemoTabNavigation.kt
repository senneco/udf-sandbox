package com.shmakov.udf.composable.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.shmakov.udf.TabNavigationFrame
import com.shmakov.udf.TabNavigationRenderPlanner
import com.shmakov.udf.TabNavigationRenderProblem
import com.shmakov.udf.TabNavigationRenderResult
import com.shmakov.udf.navigation.NavigationLayoutPolicy
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.navigation.TabId
import java.util.Collections

/** Application-owned visuals for one tab; ordering and selection never live here. */
@Immutable
internal data class DemoTabUi(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

/** One ordered visual item derived from the durable graph and application UI configuration. */
@Immutable
internal data class DemoTabBarItem(
    val tabId: TabId,
    val ui: DemoTabUi,
)

/** Small stable boundary that lets leaf-only frame changes skip the Material tab bar. */
@Immutable
internal class DemoTabBarModel(
    items: List<DemoTabBarItem>,
    val selectedTabId: TabId,
) {
    val items: List<DemoTabBarItem> = Collections.unmodifiableList(ArrayList(items))

    init {
        require(this.items.isNotEmpty()) { "Tab bar items must not be empty" }
        require(this.items.map { item -> item.tabId }.toSet().size == this.items.size) {
            "Tab bar items must have unique tab IDs"
        }
        require(this.items.any { item -> item.tabId == selectedTabId }) {
            "Selected tab $selectedTabId is missing from tab bar items"
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is DemoTabBarModel &&
            items == other.items &&
            selectedTabId == other.selectedTabId

    override fun hashCode(): Int = 31 * items.hashCode() + selectedTabId.hashCode()
}

/** Components whose committed passes are observable only by regression tests. */
internal enum class DemoTabNavigationComponent {
    Shell,
    TabBar,
}

/** A committed-pass oracle; production uses the no-op default. */
internal fun interface DemoTabNavigationDiagnostics {
    fun onCommitted(component: DemoTabNavigationComponent)
}

internal val LocalDemoTabNavigationDiagnostics = staticCompositionLocalOf {
    DemoTabNavigationDiagnostics { }
}

/**
 * App-side stateful-tabs sample. The durable graph owns order, selection, and every leaf history.
 *
 * [tabUiById] is deliberately visuals-only. Adding an entry to this map does not add it to an old
 * restored graph: graph migration remains an explicit application-owned `ReplaceGraph` action.
 */
@Composable
internal fun DemoTabNavigation(
    frame: TabNavigationFrame,
    tabUiById: Map<TabId, DemoTabUi>,
    layoutPolicy: NavigationLayoutPolicy,
    destinationCatalog: DestinationCatalog,
    onAction: (TabAction) -> Unit,
) {
    when (
        val result = TabNavigationRenderPlanner.plan(
            frame = frame,
            configuredTabIds = tabUiById.keys,
            layoutPolicy = layoutPolicy,
        )
    ) {
        is TabNavigationRenderResult.Ready -> {
            val model = result.model
            val tabBarModel = DemoTabBarModel(
                items = model.orderedTabIds.map { tabId ->
                    DemoTabBarItem(
                        tabId = tabId,
                        ui = checkNotNull(tabUiById[tabId]) {
                            "Validated tab configuration disappeared for $tabId"
                        },
                    )
                },
                selectedTabId = model.selectedTabId,
            )
            val latestOnAction = rememberUpdatedState(onAction)
            val selectTab = remember {
                { tabId: TabId -> latestOnAction.value(TabAction.select(tabId)) }
            }
            DemoTabNavigationContent(
                model = model,
                tabBarModel = tabBarModel,
                destinationCatalog = destinationCatalog,
                onSelectTab = selectTab,
                onAction = onAction,
            )
        }

        is TabNavigationRenderResult.Rejected -> DemoTabNavigationFailure(result.problem)
    }
}

@Composable
private fun DemoTabNavigationContent(
    model: com.shmakov.udf.TabNavigationRenderModel,
    tabBarModel: DemoTabBarModel,
    destinationCatalog: DestinationCatalog,
    onSelectTab: (TabId) -> Unit,
    onAction: (TabAction) -> Unit,
) {
    val diagnostics = LocalDemoTabNavigationDiagnostics.current
    SideEffect { diagnostics.onCommitted(DemoTabNavigationComponent.Shell) }
    Column(modifier = Modifier.fillMaxSize().testTag(DEMO_TAB_SHELL_TAG)) {
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            AnimatedTabLeafNavigation(
                model = model,
                onNavigationAction = { origin, action ->
                    onAction(origin.wrap(action))
                },
                destinationCatalog = destinationCatalog,
            )
        }
        DemoTabBar(
            model = tabBarModel,
            onSelectTab = onSelectTab,
        )
    }
}

@Composable
private fun DemoTabBar(
    model: DemoTabBarModel,
    onSelectTab: (TabId) -> Unit,
) {
    val diagnostics = LocalDemoTabNavigationDiagnostics.current
    SideEffect { diagnostics.onCommitted(DemoTabNavigationComponent.TabBar) }
    NavigationBar(modifier = Modifier.testTag(DEMO_TAB_BAR_TAG)) {
        model.items.forEach { item ->
            NavigationBarItem(
                modifier = Modifier.testTag(demoTabItemTag(item.tabId)),
                selected = item.tabId == model.selectedTabId,
                onClick = { onSelectTab(item.tabId) },
                icon = {
                    Icon(
                        imageVector = item.ui.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(item.ui.labelRes)) },
            )
        }
    }
}

@Composable
private fun DemoTabNavigationFailure(problem: TabNavigationRenderProblem) {
    when (problem) {
        is TabNavigationRenderProblem.MissingTabConfiguration -> Text(
            text = "Missing tab UI configuration: ${problem.tabId.value}",
            modifier = Modifier.fillMaxSize().testTag(DEMO_TAB_FAILURE_TAG),
        )

        is TabNavigationRenderProblem.LeafProjectionFailed ->
            NavigationProjectionFailure(problem.problem)
    }
}

internal const val DEMO_TAB_SHELL_TAG = "demo-tab-shell"
internal const val DEMO_TAB_BAR_TAG = "demo-tab-bar"
internal const val DEMO_TAB_FAILURE_TAG = "demo-tab-failure"

internal fun demoTabItemTag(tabId: TabId): String = "demo-tab:${tabId.value}"
