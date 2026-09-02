package com.shmakov.udf

import com.shmakov.udf.deeplink.AccountDetailsTabDeepLinkHistoryFactory
import com.shmakov.udf.deeplink.AccountDetailsTarget
import com.shmakov.udf.deeplink.DeepLinkEntryIdFactory
import com.shmakov.udf.deeplink.DemoDeepLinkNotHandledReason
import com.shmakov.udf.deeplink.DemoDeepLinkResolutionProblem
import com.shmakov.udf.deeplink.DemoDeepLinkTargetResolution
import com.shmakov.udf.deeplink.DemoDeepLinkTargetResolver
import com.shmakov.udf.deeplink.FreshDeepLinkEntryIdFactory
import com.shmakov.udf.deeplink.NormalizedDemoDeepLink
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.navigation.TabId
import com.shmakov.udf.navigation.TabNavigationState

/** An application-level Back requirement that does not belong in the generic tab reducer. */
internal sealed class DemoTabBackConfigurationProblem {
    data class MissingPrimaryTab(
        val primaryTabId: TabId,
    ) : DemoTabBackConfigurationProblem()
}

/** An application-level deep-link requirement that does not belong in the generic tab reducer. */
internal sealed class DemoTabDeepLinkConfigurationProblem {
    data class MissingDeepLinkTab(
        val tabId: TabId,
    ) : DemoTabDeepLinkConfigurationProblem()
}

/** Immutable Back plan captured by the currently committed Activity composition. */
internal sealed class DemoTabBackPlan {
    data class Dispatch(
        val expectedRevision: Long,
        val action: TabAction,
    ) : DemoTabBackPlan()

    data class FinishHost(
        val expectedRevision: Long,
    ) : DemoTabBackPlan()

    data class ConfigurationRejected(
        val expectedRevision: Long,
        val problem: DemoTabBackConfigurationProblem,
    ) : DemoTabBackPlan()
}

/** A Back effect that is safe to perform against the latest observable frame. */
internal sealed class DemoTabBackExecution {
    data class Dispatch(val action: TabAction) : DemoTabBackExecution()

    object FinishHost : DemoTabBackExecution()

    data class ConfigurationRejected(
        val problem: DemoTabBackConfigurationProblem,
    ) : DemoTabBackExecution()

    data class Stale(
        val expectedRevision: Long,
        val actualRevision: Long,
    ) : DemoTabBackExecution()
}

/** Pure Android Back policy layered above the reusable tab reducer. */
internal object DemoTabBackPlanner {

    fun plan(
        frame: TabNavigationFrame,
        primaryTabId: TabId,
    ): DemoTabBackPlan {
        val state = frame.state
        if (state.selectedTab.history.entries.size > 1) {
            return DemoTabBackPlan.Dispatch(
                expectedRevision = frame.revision,
                action = TabAction.back(state),
            )
        }
        if (state.selectedTabId == primaryTabId) {
            return DemoTabBackPlan.FinishHost(frame.revision)
        }
        if (state[primaryTabId] == null) {
            return DemoTabBackPlan.ConfigurationRejected(
                expectedRevision = frame.revision,
                problem = DemoTabBackConfigurationProblem.MissingPrimaryTab(primaryTabId),
            )
        }
        return DemoTabBackPlan.Dispatch(
            expectedRevision = frame.revision,
            action = TabAction.select(primaryTabId),
        )
    }

    /** Rejects callbacks captured before any newer graph frame, including host finish callbacks. */
    fun resolve(
        plan: DemoTabBackPlan,
        currentRevision: Long,
    ): DemoTabBackExecution = when (plan) {
        is DemoTabBackPlan.Dispatch -> if (plan.expectedRevision == currentRevision) {
            DemoTabBackExecution.Dispatch(plan.action)
        } else {
            DemoTabBackExecution.Stale(plan.expectedRevision, currentRevision)
        }

        is DemoTabBackPlan.FinishHost -> if (plan.expectedRevision == currentRevision) {
            DemoTabBackExecution.FinishHost
        } else {
            DemoTabBackExecution.Stale(plan.expectedRevision, currentRevision)
        }

        is DemoTabBackPlan.ConfigurationRejected -> if (
            plan.expectedRevision == currentRevision
        ) {
            DemoTabBackExecution.ConfigurationRejected(plan.problem)
        } else {
            DemoTabBackExecution.Stale(plan.expectedRevision, currentRevision)
        }
    }
}

/** A complete app-owned deep-link plan before it reaches the generic reducer/store. */
internal sealed class DemoTabDeepLinkPlan {
    data class Planned(
        val deepLink: NormalizedDemoDeepLink,
        val action: TabAction.OpenTab,
    ) : DemoTabDeepLinkPlan()

    data class NotHandled(
        val reason: DemoDeepLinkNotHandledReason,
    ) : DemoTabDeepLinkPlan()

    data class Rejected(
        val problem: DemoDeepLinkResolutionProblem,
    ) : DemoTabDeepLinkPlan()

    data class ConfigurationRejected(
        val problem: DemoTabDeepLinkConfigurationProblem,
    ) : DemoTabDeepLinkPlan()
}

/** Pure application deep-link policy that materializes one complete target-tab action. */
internal object DemoTabDeepLinkPlanner {

    fun plan(
        rawUri: String?,
        state: TabNavigationState,
        accountsTabId: TabId,
        entryIdFactory: DeepLinkEntryIdFactory = FreshDeepLinkEntryIdFactory,
    ): DemoTabDeepLinkPlan {
        val deepLink = when (val target = DemoDeepLinkTargetResolver.resolve(rawUri)) {
            is DemoDeepLinkTargetResolution.Resolved -> target.deepLink
            is DemoDeepLinkTargetResolution.NotHandled -> {
                return DemoTabDeepLinkPlan.NotHandled(target.reason)
            }
            is DemoDeepLinkTargetResolution.Rejected -> {
                return DemoTabDeepLinkPlan.Rejected(target.problem)
            }
        }

        if (state[accountsTabId] == null) {
            return DemoTabDeepLinkPlan.ConfigurationRejected(
                DemoTabDeepLinkConfigurationProblem.MissingDeepLinkTab(accountsTabId),
            )
        }

        val history = when (val target = deepLink.target) {
            is AccountDetailsTarget -> AccountDetailsTabDeepLinkHistoryFactory.create(
                target = target,
                entryIdFactory = entryIdFactory,
            )
        }
        return when (history) {
            is NavStateCreationResult.Valid -> DemoTabDeepLinkPlan.Planned(
                deepLink = deepLink,
                action = TabAction.openTab(accountsTabId, history.state),
            )

            is NavStateCreationResult.Invalid -> DemoTabDeepLinkPlan.Rejected(
                DemoDeepLinkResolutionProblem.InvalidNavigationState(history.problems),
            )
        }
    }
}
