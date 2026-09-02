package com.shmakov.udf

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.shmakov.udf.deeplink.DeepLinkEntryIdFactory
import com.shmakov.udf.deeplink.DemoDeepLinkNotHandledReason
import com.shmakov.udf.deeplink.DemoDeepLinkResolutionProblem
import com.shmakov.udf.deeplink.FreshDeepLinkEntryIdFactory
import com.shmakov.udf.deeplink.NormalizedDemoDeepLink
import com.shmakov.udf.navigation.DemoRouteCodec
import com.shmakov.udf.navigation.RouteCodec
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabUnchangedReason
import kotlinx.coroutines.flow.StateFlow

/** Activity-scoped lifecycle owner of the complete demo tab-navigation graph. */
internal class AppViewModel(
    savedStateHandle: SavedStateHandle,
    fallbackGraphFactory: TabNavigationStateFactory,
    routeCodec: RouteCodec,
    private val deepLinkEntryIdFactory: DeepLinkEntryIdFactory = FreshDeepLinkEntryIdFactory,
) : ViewModel() {
    constructor(savedStateHandle: SavedStateHandle) : this(
        savedStateHandle = savedStateHandle,
        fallbackGraphFactory = TabNavigationStateFactory(DemoTabGraph::initial),
        routeCodec = DemoRouteCodec,
    )

    private val store = TabNavigationStore(
        savedStateHandle = savedStateHandle,
        routeCodec = routeCodec,
        fallbackStateFactory = fallbackGraphFactory,
    )

    val startResult: TabNavigationStartResult = store.startResult
    val frames: StateFlow<TabNavigationFrame> = store.frames

    @MainThread
    fun dispatch(action: TabAction): TabNavigationDispatchResult = store.dispatch(action)

    /** Resolves a complete target leaf before dispatching one atomic [TabAction.OpenTab]. */
    @MainThread
    fun handleDeepLink(rawUri: String?): DeepLinkHandlingResult =
        when (
            val plan = DemoTabDeepLinkPlanner.plan(
                rawUri = rawUri,
                state = store.frames.value.state,
                accountsTabId = DemoTabGraph.accountsTabId,
                entryIdFactory = deepLinkEntryIdFactory,
            )
        ) {
            is DemoTabDeepLinkPlan.Planned -> when (
                val dispatchResult = store.dispatch(plan.action)
            ) {
                is TabNavigationDispatchResult.Changed -> DeepLinkHandlingResult.Applied(
                    deepLink = plan.deepLink,
                    action = plan.action,
                    dispatchResult = dispatchResult,
                )

                is TabNavigationDispatchResult.Unchanged -> DeepLinkHandlingResult.Unchanged(
                    deepLink = plan.deepLink,
                    action = plan.action,
                    dispatchResult = dispatchResult,
                )
            }

            is DemoTabDeepLinkPlan.NotHandled -> DeepLinkHandlingResult.NotHandled(plan.reason)
            is DemoTabDeepLinkPlan.Rejected -> DeepLinkHandlingResult.Rejected(plan.problem)
            is DemoTabDeepLinkPlan.ConfigurationRejected ->
                DeepLinkHandlingResult.ConfigurationRejected(plan.problem)
        }

    /** Publishes one caller-built complete graph, suitable for logout or full app reset. */
    @MainThread
    fun replaceNavigationGraph(target: TabNavigationState): TabNavigationDispatchResult =
        store.dispatch(TabAction.replaceGraph(target))
}

internal sealed class DeepLinkHandlingResult {
    data class Applied(
        val deepLink: NormalizedDemoDeepLink,
        val action: TabAction.OpenTab,
        val dispatchResult: TabNavigationDispatchResult.Changed,
    ) : DeepLinkHandlingResult()

    data class Unchanged(
        val deepLink: NormalizedDemoDeepLink,
        val action: TabAction.OpenTab,
        val dispatchResult: TabNavigationDispatchResult.Unchanged,
    ) : DeepLinkHandlingResult() {
        val reason: TabUnchangedReason
            get() = dispatchResult.reduction.reason
    }

    data class NotHandled(
        val reason: DemoDeepLinkNotHandledReason,
    ) : DeepLinkHandlingResult()

    data class Rejected(
        val problem: DemoDeepLinkResolutionProblem,
    ) : DeepLinkHandlingResult()

    data class ConfigurationRejected(
        val problem: DemoTabDeepLinkConfigurationProblem,
    ) : DeepLinkHandlingResult()
}
