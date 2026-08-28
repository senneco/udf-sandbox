package com.shmakov.udf

import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.ContentPlacementDecision
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavProjectionProblem
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.NavigationLayoutPolicy
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.navigation.TabId
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabReducer
import com.shmakov.udf.navigation.TabReduction
import com.shmakov.udf.navigation.TabState
import com.shmakov.udf.navigation.TabTransitionIntent
import com.shmakov.udf.navigation.TabUnchangedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TabNavigationRenderPlannerContractTest {

    @Test
    fun selectedLeafAndGraphWideRetentionFollowExactStateOrder() {
        val homeRoot = entry("home-root", Home)
        val homeAccounts = entry("home-accounts", Accounts)
        val profileRoot = entry("profile-root", Home)
        val state = graph(
            selected = profileTab,
            tab(homeTab, homeRoot, homeAccounts),
            tab(profileTab, profileRoot),
        )

        val result = plan(frame(state))

        val ready = result as TabNavigationRenderResult.Ready
        assertEquals(listOf(homeTab, profileTab), ready.model.orderedTabIds)
        assertEquals(profileTab, ready.model.selectedTabId)
        assertEquals(listOf(profileRoot.id), ready.model.selectedLeaf.historyEntryIds)
        assertEquals(
            listOf(homeRoot.id, homeAccounts.id, profileRoot.id),
            ready.model.retainedEntryIds,
        )
        assertEquals(
            TabNavigationActionOrigin(profileTab, profileRoot.id),
            ready.model.actionOrigin,
        )
        assertEquals(
            TabPresentationContinuity.ResetContainer,
            ready.model.presentationContinuity,
        )
    }

    @Test
    fun missingVisualIsRejectedBeforeLayoutProjectionWithExactTabId() {
        var layoutCalls = 0
        val countingPolicy = NavigationLayoutPolicy {
            layoutCalls += 1
            ContentPlacementDecision.root()
        }
        val state = graph(
            selected = homeTab,
            tab(homeTab, entry("missing-home", Home)),
            tab(profileTab, entry("missing-profile", Home)),
        )

        val result = TabNavigationRenderPlanner.plan(
            frame = frame(state),
            configuredTabIds = setOf(homeTab),
            layoutPolicy = countingPolicy,
        )

        assertEquals(
            TabNavigationRenderResult.Rejected(
                TabNavigationRenderProblem.MissingTabConfiguration(profileTab),
            ),
            result,
        )
        assertEquals(0, layoutCalls)
    }

    @Test
    fun extraVisualConfigurationDoesNotAddOrReorderPersistedTabs() {
        val state = graph(
            selected = profileTab,
            tab(homeTab, entry("extra-home", Home)),
            tab(profileTab, entry("extra-profile", Home)),
        )

        val ready = TabNavigationRenderPlanner.plan(
            frame = frame(state),
            configuredTabIds = linkedSetOf(extraTab, profileTab, homeTab),
            layoutPolicy = singlePane,
        ) as TabNavigationRenderResult.Ready

        assertEquals(listOf(homeTab, profileTab), ready.model.orderedTabIds)
        assertEquals(profileTab, ready.model.selectedTabId)
    }

    @Test
    fun selectedLeafProjectionFailureIsTypedWithOwningTab() {
        val root = entry("failure-root", Home)
        val accounts = entry("failure-accounts", Accounts)
        val state = graph(selected = homeTab, tab(homeTab, root, accounts))
        val rejectingPolicy = NavigationLayoutPolicy {
            ContentPlacementDecision.reject("sample_rejected", "No nested content")
        }

        val result = TabNavigationRenderPlanner.plan(
            frame = frame(state),
            configuredTabIds = setOf(homeTab),
            layoutPolicy = rejectingPolicy,
        )

        val rejected = result as TabNavigationRenderResult.Rejected
        val problem = rejected.problem as TabNavigationRenderProblem.LeafProjectionFailed
        assertEquals(homeTab, problem.tabId)
        assertEquals(
            NavProjectionProblem.PolicyRejected(
                index = 1,
                entry = accounts,
                error = com.shmakov.udf.navigation.LayoutPolicyError(
                    code = "sample_rejected",
                    message = "No nested content",
                ),
            ),
            problem.problem,
        )
    }

    @Test
    fun selectedTabNavigationForwardsExactLeafIntent() {
        val root = entry("leaf-root", Home)
        val accounts = entry("leaf-accounts", Accounts)
        val leafIntent = NavTransitionIntent.Pushed(root.id, accounts.id)
        val state = graph(selected = homeTab, tab(homeTab, root, accounts))

        val ready = plan(
            frame(
                state = state,
                revision = 7L,
                transition = TabTransitionIntent.NavigationChanged(homeTab, leafIntent),
            ),
        ) as TabNavigationRenderResult.Ready

        assertSame(leafIntent, ready.model.selectedLeaf.transitionIntent)
        assertEquals(7L, ready.model.selectedLeaf.navigationRevision)
        assertEquals(
            TabPresentationContinuity.Leaf,
            ready.model.presentationContinuity,
        )
    }

    @Test
    fun selectedLeafMotionRequiresAnExactContiguousRendererBaseline() {
        val root = entry("motion-root", Home)
        val accounts = entry("motion-accounts", Accounts)
        val previousState = graph(selected = homeTab, tab(homeTab, root))
        val targetState = graph(selected = homeTab, tab(homeTab, root, accounts))
        val intent = NavTransitionIntent.Pushed(root.id, accounts.id)
        val previous = (plan(frame(previousState, revision = 4L))
            as TabNavigationRenderResult.Ready).model.selectedLeaf
        val contiguous = (plan(
            frame(
                targetState,
                revision = 5L,
                transition = TabTransitionIntent.NavigationChanged(homeTab, intent),
            ),
        ) as TabNavigationRenderResult.Ready).model.selectedLeaf
        val gapped = (plan(
            frame(
                targetState,
                revision = 6L,
                transition = TabTransitionIntent.NavigationChanged(homeTab, intent),
            ),
        ) as TabNavigationRenderResult.Ready).model.selectedLeaf

        assertEquals(
            NavigationContentMotion.Push,
            NavigationPresentationPlanner.contentMotion(previous, contiguous),
        )
        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(previous, gapped),
        )
        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(null, contiguous),
        )
    }

    @Test
    fun transitionForAnotherTabNeverLeaksIntoSelectedLeaf() {
        val state = graph(
            selected = profileTab,
            tab(homeTab, entry("other-home", Home)),
            tab(profileTab, entry("other-profile", Home)),
        )
        val leafIntent = NavTransitionIntent.HistoryReplaced(
            previousTopEntryId = EntryId("old-home"),
            targetTopEntryId = state.requireTab(homeTab).history.top.id,
        )

        val ready = plan(
            frame(
                state = state,
                revision = 2L,
                transition = TabTransitionIntent.NavigationChanged(homeTab, leafIntent),
            ),
        ) as TabNavigationRenderResult.Ready

        assertEquals(null, ready.model.selectedLeaf.transitionIntent)
        assertEquals(
            TabPresentationContinuity.ResetContainer,
            ready.model.presentationContinuity,
        )
    }

    @Test
    fun sameTabOpenForwardsLeafIntentButCrossTabOpenSnaps() {
        val homeRoot = entry("open-home", Home)
        val homeAccounts = entry("open-accounts", Accounts)
        val profileRoot = entry("open-profile", Home)
        val state = graph(
            selected = homeTab,
            tab(homeTab, homeRoot, homeAccounts),
            tab(profileTab, profileRoot),
        )
        val leafIntent = NavTransitionIntent.Pushed(homeRoot.id, homeAccounts.id)

        val sameTab = plan(
            frame(
                state,
                revision = 3L,
                transition = TabTransitionIntent.TabOpened(
                    previousSelectedTabId = homeTab,
                    openedTabId = homeTab,
                    previousVisibleTopId = homeRoot.id,
                    targetVisibleTopId = homeAccounts.id,
                    targetHistoryTransition = leafIntent,
                ),
            ),
        ) as TabNavigationRenderResult.Ready
        assertSame(leafIntent, sameTab.model.selectedLeaf.transitionIntent)
        assertEquals(TabPresentationContinuity.Leaf, sameTab.model.presentationContinuity)

        val crossTabState = graph(
            selected = profileTab,
            tab(homeTab, homeRoot, homeAccounts),
            tab(profileTab, profileRoot),
        )
        val crossTab = plan(
            frame(
                crossTabState,
                revision = 4L,
                transition = TabTransitionIntent.TabOpened(
                    previousSelectedTabId = homeTab,
                    openedTabId = profileTab,
                    previousVisibleTopId = homeAccounts.id,
                    targetVisibleTopId = profileRoot.id,
                    targetHistoryTransition = NavTransitionIntent.HistoryReplaced(
                        homeAccounts.id,
                        profileRoot.id,
                    ),
                ),
            ),
        ) as TabNavigationRenderResult.Ready
        assertEquals(null, crossTab.model.selectedLeaf.transitionIntent)
        assertEquals(
            TabPresentationContinuity.ResetContainer,
            crossTab.model.presentationContinuity,
        )
    }

    @Test
    fun tabSelectionAndGraphReplacementAlwaysSnapLeafRenderer() {
        val home = entry("snap-home", Home)
        val profile = entry("snap-profile", Home)
        val state = graph(
            selected = profileTab,
            tab(homeTab, home),
            tab(profileTab, profile),
        )
        val transitions = listOf(
            TabTransitionIntent.TabSelected(homeTab, profileTab),
            TabTransitionIntent.GraphReplaced(
                previousSelectedTabId = homeTab,
                selectedTabId = profileTab,
                previousVisibleTopId = home.id,
                targetVisibleTopId = profile.id,
            ),
        )

        transitions.forEach { transition ->
            val ready = plan(frame(state, revision = 1L, transition = transition))
                as TabNavigationRenderResult.Ready
            assertEquals(null, ready.model.selectedLeaf.transitionIntent)
            assertEquals(
                TabPresentationContinuity.ResetContainer,
                ready.model.presentationContinuity,
            )
        }
    }

    @Test
    fun renderModelRejectsRetentionThatOmitsSelectedHistory() {
        val root = entry("retention-root", Home)
        val selectedLeaf = (plan(frame(graph(homeTab, tab(homeTab, root))))
            as TabNavigationRenderResult.Ready).model.selectedLeaf

        val failure = assertThrows(IllegalArgumentException::class.java) {
            TabNavigationRenderModel(
                orderedTabIds = listOf(homeTab),
                selectedTabId = homeTab,
                selectedLeaf = selectedLeaf,
                retainedEntryIds = listOf(EntryId("different")),
                actionOrigin = TabNavigationActionOrigin(homeTab, root.id),
                presentationContinuity = TabPresentationContinuity.Leaf,
            )
        }

        assertEquals(
            "retainedEntryIds must contain every selected leaf history entry",
            failure.message,
        )
    }

    @Test
    fun renderModelCollectionsAreDefensiveAndRuntimeUnmodifiable() {
        val root = entry("defensive-root", Home)
        val ordered = arrayListOf(homeTab)
        val retained = arrayListOf(root.id)
        val selectedLeaf = (plan(frame(graph(homeTab, tab(homeTab, root))))
            as TabNavigationRenderResult.Ready).model.selectedLeaf
        val model = TabNavigationRenderModel(
            orderedTabIds = ordered,
            selectedTabId = homeTab,
            selectedLeaf = selectedLeaf,
            retainedEntryIds = retained,
            actionOrigin = TabNavigationActionOrigin(homeTab, root.id),
            presentationContinuity = TabPresentationContinuity.Leaf,
        )

        ordered += profileTab
        retained += EntryId("later")

        assertEquals(listOf(homeTab), model.orderedTabIds)
        assertEquals(listOf(root.id), model.retainedEntryIds)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (model.retainedEntryIds as MutableList<EntryId>).add(EntryId("mutate"))
        }
    }

    @Test
    fun physicalBranchOriginMaterializesExactGuardedAction() {
        val top = entry("origin-top", Home)
        val origin = TabNavigationActionOrigin(homeTab, top.id)

        val action = origin.wrap(NavAction.Pop)

        assertEquals(
            TabAction.NavigateInSelectedTab(
                expectedSelectedTabId = homeTab,
                expectedTopId = top.id,
                action = NavAction.Pop,
            ),
            action,
        )
    }

    @Test
    fun outgoingBranchActionAfterSwitchCannotMutateIncomingTab() {
        val homeRoot = entry("stale-home", Home)
        val homeAccounts = entry("stale-accounts", Accounts)
        val profileRoot = entry("stale-profile", Home)
        val before = graph(
            selected = homeTab,
            tab(homeTab, homeRoot, homeAccounts),
            tab(profileTab, profileRoot),
        )
        val outgoingOrigin = (plan(frame(before)) as TabNavigationRenderResult.Ready)
            .model.actionOrigin
        val switched = (TabReducer.reduce(before, TabAction.select(profileTab))
            as TabReduction.Changed).state

        val staleReduction = TabReducer.reduce(switched, outgoingOrigin.wrap(NavAction.Pop))

        val unchanged = staleReduction as TabReduction.Unchanged
        assertSame(switched, unchanged.state)
        assertEquals(
            TabUnchangedReason.SelectedTabChanged(homeTab, profileTab),
            unchanged.reason,
        )
        assertEquals(2, switched.requireTab(homeTab).history.entries.size)
    }

    private fun plan(frame: TabNavigationFrame): TabNavigationRenderResult =
        TabNavigationRenderPlanner.plan(
            frame = frame,
            configuredTabIds = setOf(homeTab, profileTab),
            layoutPolicy = singlePane,
        )

    private fun frame(
        state: TabNavigationState,
        revision: Long = 0L,
        transition: TabTransitionIntent? = null,
    ): TabNavigationFrame = TabNavigationFrame(state, revision, transition)

    private fun graph(
        selected: TabId,
        vararg tabs: TabState,
    ): TabNavigationState = TabNavigationState.create(selected, tabs.toList())

    private fun TabNavigationState.requireTab(tabId: TabId): TabState =
        checkNotNull(this[tabId]) { "Expected tab ${tabId.value} in fixture graph" }

    private fun tab(id: TabId, vararg entries: BackStackEntry): TabState =
        TabState(
            id,
            (NavState.fromEntries(entries.toList()) as NavStateCreationResult.Valid).state,
        )

    private fun entry(id: String, route: com.shmakov.udf.navigation.Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private companion object {
        val homeTab = TabId("home")
        val profileTab = TabId("profile")
        val extraTab = TabId("extra")

        val singlePane = NavigationLayoutPolicy { ContentPlacementDecision.root() }
    }
}
