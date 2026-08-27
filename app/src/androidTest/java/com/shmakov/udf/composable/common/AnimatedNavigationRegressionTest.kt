package com.shmakov.udf.composable.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shmakov.udf.NavigationRenderTarget
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.ContentPlacementDecision
import com.shmakov.udf.navigation.ContentRoute
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.ModalEntrance
import com.shmakov.udf.navigation.ModalRoute
import com.shmakov.udf.navigation.ModalScreen
import com.shmakov.udf.navigation.ModalScreenState
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavProjectionResult
import com.shmakov.udf.navigation.NavProjector
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.NavigationLayoutPolicy
import com.shmakov.udf.navigation.NavigationRenderTree
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.Screen
import com.shmakov.udf.navigation.Transactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AnimatedNavigationRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun outgoingExpandedBranchSurvivesUntilPushExitCompletes() {
        composeRule.mainClock.autoAdvance = false
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val details = entry("details", AccountDetails(accountId = 1))
        val currentTarget = mutableStateOf(
            target(
                revision = 0,
                navState = state(home, accounts, account),
                policy = expandedPane,
                transition = null,
            ),
        )

        composeRule.setContent {
            AnimatedNavigation(
                renderTarget = currentTarget.value,
                onNavigationAction = {},
                destinationCatalog = TaggedDestinationCatalog,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(contentTag(home.id), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(contentTag(accounts.id), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(modalTag(account.id), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(contentTag(details.id), useUnmergedTree = true)
            .assertDoesNotExist()

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 1,
                navState = state(home, accounts, account, details),
                policy = expandedPane,
                transition = NavTransitionIntent.Pushed(
                    fromEntryId = account.id,
                    addedEntryId = details.id,
                ),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(MID_ANIMATION_MILLIS)

        composeRule.onNodeWithTag(contentTag(home.id), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(contentTag(accounts.id), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(modalTag(account.id), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(contentTag(details.id), useUnmergedTree = true).assertExists()

        composeRule.mainClock.advanceTimeBy(AFTER_ANIMATION_MILLIS)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(contentTag(details.id), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(contentTag(home.id), useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(contentTag(accounts.id), useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(modalTag(account.id), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun stickyIntentDoesNotReplayForInitialOrSameRevisionLayoutProjection() {
        composeRule.mainClock.autoAdvance = false
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val navState = state(home, accounts)
        val stickyIntent = NavTransitionIntent.Pushed(home.id, accounts.id)
        val currentTarget = mutableStateOf(
            target(
                revision = 9,
                navState = navState,
                policy = singlePane,
                transition = stickyIntent,
            ),
        )

        composeRule.setContent {
            AnimatedNavigation(
                renderTarget = currentTarget.value,
                onNavigationAction = {},
                destinationCatalog = TaggedDestinationCatalog,
            )
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(contentTag(accounts.id), useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onNodeWithTag(contentTag(home.id), useUnmergedTree = true)
            .assertDoesNotExist()

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 9,
                navState = navState,
                policy = expandedPane,
                transition = stickyIntent,
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(MID_ANIMATION_MILLIS)

        composeRule.onAllNodesWithTag(contentTag(home.id), useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodesWithTag(contentTag(accounts.id), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun batchModalChangesRetainEveryExitAndSeparateRequestFromCompletion() {
        composeRule.mainClock.autoAdvance = false
        val home = entry("home", Home)
        val first = entry("account-1", Account(accountId = 1))
        val second = entry("account-2", Account(accountId = 2))
        val third = entry("account-3", Account(accountId = 3))
        val currentTarget = mutableStateOf(
            target(
                revision = 0,
                navState = state(home, first),
                policy = singlePane,
                transition = null,
            ),
        )
        val probe = ModalLifecycleProbe()
        val navigationActions = mutableListOf<NavAction>()

        composeRule.setContent {
            AnimatedNavigation(
                renderTarget = currentTarget.value,
                onNavigationAction = navigationActions::add,
                destinationCatalog = ProbedDestinationCatalog(probe),
            )
        }
        composeRule.waitForIdle()

        assertModalCount(first.id, 1)
        assertModalCount(second.id, 0)
        assertModalCount(third.id, 0)
        assertEquals(ModalScreenState.Shown, probe.targetState(first.id))
        assertEquals(ModalEntrance.Snap, probe.entrance(first.id))

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 1,
                navState = state(home, first, second, third),
                policy = singlePane,
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = first.id,
                    targetTopEntryId = third.id,
                ),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(first.id, 1)
        assertModalCount(second.id, 1)
        assertModalCount(third.id, 1)
        assertEquals(ModalScreenState.Shown, probe.targetState(first.id))
        assertEquals(ModalScreenState.Shown, probe.targetState(second.id))
        assertEquals(ModalScreenState.Shown, probe.targetState(third.id))
        assertEquals(ModalEntrance.Snap, probe.entrance(first.id))
        assertEquals(ModalEntrance.Animate, probe.entrance(second.id))
        assertEquals(ModalEntrance.Animate, probe.entrance(third.id))

        composeRule.runOnIdle {
            probe.requestDismiss(second.id)
        }
        assertEquals(listOf(NavAction.dismissModal(second.id)), navigationActions)
        navigationActions.clear()

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 2,
                navState = state(home, first),
                policy = singlePane,
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = third.id,
                    targetTopEntryId = first.id,
                ),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(first.id, 1)
        assertModalCount(second.id, 1)
        assertModalCount(third.id, 1)
        assertEquals(ModalScreenState.Shown, probe.targetState(first.id))
        assertEquals(ModalScreenState.Hidden, probe.targetState(second.id))
        assertEquals(ModalScreenState.Hidden, probe.targetState(third.id))
        composeRule.runOnIdle {
            probe.requestDismiss(second.id)
        }
        assertEquals(emptyList<NavAction>(), navigationActions)

        val secondExitFinished = probe.exitFinishedCallback(second.id)
        val thirdExitFinished = probe.exitFinishedCallback(third.id)
        composeRule.runOnIdle {
            secondExitFinished()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(first.id, 1)
        assertModalCount(second.id, 0)
        assertModalCount(third.id, 1)
        assertEquals(ModalScreenState.Hidden, probe.targetState(third.id))
        assertEquals(emptyList<NavAction>(), navigationActions)

        composeRule.runOnIdle {
            secondExitFinished()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertModalCount(second.id, 0)
        assertModalCount(third.id, 1)
        assertEquals(emptyList<NavAction>(), navigationActions)

        composeRule.runOnIdle {
            thirdExitFinished()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(first.id, 1)
        assertModalCount(second.id, 0)
        assertModalCount(third.id, 0)
        assertEquals(emptyList<NavAction>(), navigationActions)
    }

    @Test
    fun staleExitCompletionCannotReleaseReaddedOrNewlyExitingSameEntryId() {
        composeRule.mainClock.autoAdvance = false
        val home = entry("home-aba", Home)
        val modal = entry("account-aba", Account(accountId = 8))
        val currentTarget = mutableStateOf(
            target(
                revision = 100,
                navState = state(home, modal),
                policy = singlePane,
                transition = null,
            ),
        )
        val probe = ModalLifecycleProbe()
        val navigationActions = mutableListOf<NavAction>()

        composeRule.setContent {
            AnimatedNavigation(
                renderTarget = currentTarget.value,
                onNavigationAction = navigationActions::add,
                destinationCatalog = ProbedDestinationCatalog(probe),
            )
        }
        composeRule.waitForIdle()

        assertModalCount(modal.id, 1)
        assertEquals(ModalScreenState.Shown, probe.targetState(modal.id))

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 101,
                navState = state(home),
                policy = singlePane,
                transition = NavTransitionIntent.ModalDismissed(modal.id),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(modal.id, 1)
        assertEquals(ModalScreenState.Hidden, probe.targetState(modal.id))
        val firstExitFinished = probe.exitFinishedCallback(modal.id)

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 102,
                navState = state(home, modal),
                policy = singlePane,
                transition = NavTransitionIntent.Pushed(
                    fromEntryId = home.id,
                    addedEntryId = modal.id,
                ),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(modal.id, 1)
        assertEquals(ModalScreenState.Shown, probe.targetState(modal.id))

        composeRule.runOnIdle {
            firstExitFinished()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(modal.id, 1)
        assertEquals(ModalScreenState.Shown, probe.targetState(modal.id))
        assertEquals(emptyList<NavAction>(), navigationActions)

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 103,
                navState = state(home),
                policy = singlePane,
                transition = NavTransitionIntent.ModalDismissed(modal.id),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(modal.id, 1)
        assertEquals(ModalScreenState.Hidden, probe.targetState(modal.id))
        val secondExitFinished = probe.exitFinishedCallback(modal.id)

        composeRule.runOnIdle {
            firstExitFinished()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(modal.id, 1)
        assertEquals(ModalScreenState.Hidden, probe.targetState(modal.id))
        assertEquals(emptyList<NavAction>(), navigationActions)

        composeRule.runOnIdle {
            secondExitFinished()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(modal.id, 0)
        assertEquals(emptyList<NavAction>(), navigationActions)
    }

    @Test
    fun replacingShownMaterialSheetKeepsTheDistinctNewEntryExpandedAndInteractive() {
        composeRule.mainClock.autoAdvance = false
        val home = entry("material-home", Home)
        val first = entry("material-account-a", Account(accountId = 1))
        val second = entry("material-account-b", Account(accountId = 2))
        val currentTarget = mutableStateOf(
            target(
                revision = 200,
                navState = state(home, first),
                policy = singlePane,
                transition = null,
            ),
        )
        val probe = MaterialModalProbe()
        val navigationActions = mutableListOf<NavAction>()
        val catalog = MaterialBottomSheetDestinationCatalog(probe)
        val collapseAction = SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse)

        composeRule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        probe.rootHeightPx = coordinates.size.height.toFloat()
                    },
            ) {
                AnimatedNavigation(
                    renderTarget = currentTarget.value,
                    onNavigationAction = navigationActions::add,
                    destinationCatalog = catalog,
                )
            }
        }
        assertTrue(
            "First sheet never reached exact Expanded geometry",
            advanceFramesUntil {
                probe.isExactlyExpanded(first.id) &&
                    composeRule.onAllNodes(
                        collapseAction,
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes().isNotEmpty()
            },
        )
        composeRule.mainClock.advanceTimeByFrame()
        assertModalCount(first.id, 1)
        assertEquals(ModalEntrance.Snap, probe.entrances[first.id])

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 201,
                navState = state(home, second),
                policy = singlePane,
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = first.id,
                    targetTopEntryId = second.id,
                ),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        // The new desired sheet and old retained exit have independent Material states and IDs.
        assertModalCount(first.id, 1)
        assertModalCount(second.id, 1)
        assertEquals(ModalScreenState.Hidden, probe.targetState(first.id))
        assertEquals(ModalScreenState.Shown, probe.targetState(second.id))
        assertEquals(ModalEntrance.Snap, probe.entrances[first.id])
        assertEquals(ModalEntrance.Animate, probe.entrances[second.id])
        assertTrue(
            "Distinct replacement sheet unexpectedly skipped its Animate entrance",
            !probe.isExactlyExpanded(second.id),
        )
        assertEquals(emptyList<EntryId>(), probe.dismissRequests)
        assertEquals(emptyList<NavAction>(), navigationActions)

        assertTrue(
            "Old sheet did not finish or new exact-ID sheet did not reach Expanded",
            advanceFramesUntil {
                modalCount(first.id) == 0 &&
                    probe.isExactlyExpanded(second.id) &&
                    composeRule.onAllNodes(
                        collapseAction,
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes().isNotEmpty()
            },
        )
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(first.id, 0)
        assertModalCount(second.id, 1)
        assertEquals(listOf(first.id), probe.exitCompletions)
        assertEquals(emptyList<EntryId>(), probe.dismissRequests)
        assertEquals(emptyList<NavAction>(), navigationActions)

        composeRule.onNodeWithTag(modalTag(second.id), useUnmergedTree = true)
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(second.id), probe.interactions)
        }
        assertEquals(emptyList<NavAction>(), navigationActions)
        assertTrue(probe.isExactlyExpanded(second.id))

        // A real request from B still maps to B's exact durable action; A's completion emitted none.
        composeRule.onNodeWithTag(modalTag(second.id), useUnmergedTree = true)
            .performTouchInput {
                swipeDown()
            }
        assertTrue(
            "Distinct replacement sheet never emitted its exact dismiss request",
            advanceFramesUntil { probe.dismissRequests == listOf(second.id) },
        )
        composeRule.runOnIdle {
            assertEquals(listOf(second.id), probe.dismissRequests)
            assertEquals(listOf(NavAction.dismissModal(second.id)), navigationActions)
        }
        assertEquals(ModalScreenState.Shown, probe.targetState(second.id))
    }

    @Test
    fun nestedModalUsesItsExactOwnerSlotBounds() {
        val home = entry("owner-home", Home)
        val accounts = entry("owner-accounts", Accounts)
        val account = entry("owner-account", Account(accountId = 1))

        composeRule.setContent {
            Box(Modifier.size(OWNER_ROOT_SIZE)) {
                AnimatedNavigation(
                    renderTarget = target(
                        revision = 0,
                        navState = state(home, accounts, account),
                        policy = expandedPane,
                        transition = null,
                    ),
                    onNavigationAction = {},
                    destinationCatalog = OwnerGeometryDestinationCatalog,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(modalTag(account.id), useUnmergedTree = true)
            .assertLeftPositionInRootIsEqualTo(OWNER_OFFSET_X)
            .assertTopPositionInRootIsEqualTo(OWNER_OFFSET_Y)
    }

    @Test
    fun crossRootDeepOwnerRelocationStillRendersExactModalExit() {
        composeRule.mainClock.autoAdvance = false
        val oldRoot = entry("cross-root-old-root", Home)
        val newRoot = entry("cross-root-new-root", Transactions)
        val sharedParent = entry("cross-root-shared-parent", Accounts)
        val owner = entry("cross-root-modal-owner", Cards)
        val modal = entry("cross-root-exiting-modal", Account(accountId = 92))
        val oldPolicy = NavigationLayoutPolicy { request ->
            when (request.nextContent.id) {
                sharedParent.id -> ContentPlacementDecision.childOf(oldRoot.id)
                owner.id -> ContentPlacementDecision.childOf(sharedParent.id)
                else -> ContentPlacementDecision.root()
            }
        }
        val newPolicy = NavigationLayoutPolicy { request ->
            when (request.nextContent.id) {
                oldRoot.id -> ContentPlacementDecision.childOf(newRoot.id)
                sharedParent.id -> ContentPlacementDecision.childOf(oldRoot.id)
                owner.id -> ContentPlacementDecision.childOf(sharedParent.id)
                else -> ContentPlacementDecision.root()
            }
        }
        val probe = ModalLifecycleProbe()
        val currentTarget = mutableStateOf(
            target(
                revision = 0,
                navState = state(oldRoot, sharedParent, owner, modal),
                policy = oldPolicy,
                transition = null,
            ),
        )

        composeRule.setContent {
            AnimatedNavigation(
                renderTarget = currentTarget.value,
                onNavigationAction = {},
                destinationCatalog = ProbedDestinationCatalog(probe),
            )
        }
        composeRule.waitForIdle()
        assertModalCount(modal.id, 1)
        assertEquals(ModalScreenState.Shown, probe.targetState(modal.id))

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 1,
                // Every old content entry survives under a new root, but M is removed. The old
                // presentation must follow its exact owner into the latest physical slot.
                navState = state(newRoot, oldRoot, sharedParent, owner),
                policy = newPolicy,
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = modal.id,
                    targetTopEntryId = owner.id,
                ),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertModalCount(modal.id, 1)
        assertEquals(ModalScreenState.Hidden, probe.targetState(modal.id))
        val exactExitCompletion = probe.exitFinishedCallback(modal.id)

        composeRule.runOnIdle {
            exactExitCompletion()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertModalCount(modal.id, 0)
    }

    @Test
    fun bindingFailureGapCannotResurrectExitWhenItsOwnerReturns() {
        composeRule.mainClock.autoAdvance = false
        val home = entry("binding-gap-home", Home)
        val owner = entry("binding-gap-owner", Accounts)
        val modal = entry("binding-gap-modal", Account(accountId = 93))
        val replacement = entry("binding-gap-replacement", Transactions)
        val probe = ModalLifecycleProbe()
        val catalog = ToggleableProbedDestinationCatalog(probe)
        val currentTarget = mutableStateOf(
            target(
                revision = 0,
                navState = state(home, owner, modal),
                policy = expandedPane,
                transition = null,
            ),
        )

        composeRule.setContent {
            AnimatedNavigation(
                renderTarget = currentTarget.value,
                onNavigationAction = {},
                destinationCatalog = catalog,
            )
        }
        composeRule.waitForIdle()
        assertModalCount(modal.id, 1)
        assertEquals(ModalScreenState.Shown, probe.targetState(modal.id))

        composeRule.runOnIdle {
            catalog.rejectBindings = true
            currentTarget.value = target(
                revision = 1,
                navState = state(replacement),
                policy = singlePane,
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = modal.id,
                    targetTopEntryId = replacement.id,
                ),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertTrue("The replacement target must reach the binding failure", catalog.rejections > 0)

        composeRule.runOnIdle {
            catalog.rejectBindings = false
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 2,
                // The exact owner returns, but M does not. A stale retained exit from before the
                // binding gap must already have been pruned when B recovered.
                navState = state(home, owner),
                policy = expandedPane,
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = replacement.id,
                    targetTopEntryId = owner.id,
                ),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(AFTER_ANIMATION_MILLIS)
        composeRule.waitForIdle()
        assertEquals(
            "The pre-gap modal must never be rendered again as a stale exit",
            ModalScreenState.Shown,
            probe.targetState(modal.id),
        )
    }

    @Test
    fun disappearingNestedOwnerReleasesItsExactExitWithoutDurableAction() {
        composeRule.mainClock.autoAdvance = false
        val home = entry("dispose-home", Home)
        val accounts = entry("dispose-accounts", Accounts)
        val account = entry("dispose-account", Account(accountId = 1))
        val transactions = entry("dispose-transactions", Transactions)
        val ownerPolicy = NavigationLayoutPolicy {
            ContentPlacementDecision.childOf(home.id)
        }
        val currentTarget = mutableStateOf(
            target(
                revision = 0,
                navState = state(home, accounts, account),
                policy = ownerPolicy,
                transition = null,
            ),
        )
        val navigationActions = mutableListOf<NavAction>()

        composeRule.setContent {
            AnimatedNavigation(
                renderTarget = currentTarget.value,
                onNavigationAction = navigationActions::add,
                destinationCatalog = TaggedDestinationCatalog,
            )
        }
        composeRule.waitForIdle()
        assertModalCount(account.id, 1)

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 1,
                navState = state(home, accounts, account, transactions),
                policy = ownerPolicy,
                transition = NavTransitionIntent.BranchReplaced(
                    sourceEntryId = home.id,
                    removedEntryIds = listOf(accounts.id, account.id),
                    addedEntryId = transactions.id,
                ),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(MID_ANIMATION_MILLIS)

        assertModalCount(account.id, 1)

        composeRule.mainClock.advanceTimeBy(AFTER_ANIMATION_MILLIS)
        composeRule.waitForIdle()

        assertModalCount(account.id, 0)
        assertEquals(emptyList<NavAction>(), navigationActions)

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 2,
                navState = state(home, accounts),
                policy = ownerPolicy,
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = transactions.id,
                    targetTopEntryId = accounts.id,
                ),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(MID_ANIMATION_MILLIS)

        // If owner disposal did not complete the exact exit token, it would resurrect here.
        assertModalCount(account.id, 0)

        composeRule.mainClock.advanceTimeBy(AFTER_ANIMATION_MILLIS)
        composeRule.waitForIdle()
        assertModalCount(account.id, 0)
        assertEquals(emptyList<NavAction>(), navigationActions)
    }

    private fun assertModalCount(entryId: EntryId, expectedCount: Int) {
        composeRule.onAllNodesWithTag(modalTag(entryId), useUnmergedTree = true)
            .assertCountEquals(expectedCount)
    }

    private fun modalCount(entryId: EntryId): Int =
        composeRule.onAllNodesWithTag(modalTag(entryId), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    private fun advanceFramesUntil(predicate: () -> Boolean): Boolean {
        repeat(MAX_PREDICATE_FRAMES) {
            if (predicate()) return true
            composeRule.mainClock.advanceTimeByFrame()
        }
        return predicate()
    }

    private fun target(
        revision: Long,
        navState: NavState,
        policy: NavigationLayoutPolicy,
        transition: NavTransitionIntent?,
    ): NavigationRenderTarget = NavigationRenderTarget(
        navigationRevision = revision,
        historyEntryIds = navState.entries.map { entry -> entry.id },
        tree = project(navState, policy),
        transitionIntent = transition,
    )

    private fun project(
        navState: NavState,
        policy: NavigationLayoutPolicy,
    ): NavigationRenderTree = when (val result = NavProjector.project(navState, policy)) {
        is NavProjectionResult.Success -> result.tree
        is NavProjectionResult.Failure -> error("Invalid projection fixture: ${result.problem}")
    }

    private fun state(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> error("Invalid state fixture: ${result.problems}")
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private companion object {
        const val MID_ANIMATION_MILLIS = 150L
        const val AFTER_ANIMATION_MILLIS = 1_000L
        const val MAX_PREDICATE_FRAMES = 360
        val singlePane = NavigationLayoutPolicy {
            ContentPlacementDecision.root()
        }

        val expandedPane = NavigationLayoutPolicy { request ->
            if (request.currentContent.entry.route is Home) {
                ContentPlacementDecision.childOf(request.currentContent.entry.id)
            } else {
                ContentPlacementDecision.root()
            }
        }

        fun contentTag(entryId: EntryId): String = "content:${entryId.value}"

        fun modalTag(entryId: EntryId): String = "modal:${entryId.value}"
    }
}

private val OWNER_ROOT_SIZE = 300.dp
private val OWNER_OFFSET_X = 48.dp
private val OWNER_OFFSET_Y = 64.dp

private object TaggedDestinationCatalog : DestinationCatalog {
    override fun resolve(entry: BackStackEntry): DestinationBinding = when (entry.route) {
        is ContentRoute -> DestinationBinding.Content(TaggedContentScreen(entry))
        is ModalRoute -> DestinationBinding.Modal(TaggedModalScreen(entry))
        else -> DestinationBinding.Unsupported(entry)
    }
}

private class TaggedContentScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {
    @Composable
    override fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("content:${entry.id.value}"),
        ) {
            childContent()
        }
    }
}

private object OwnerGeometryDestinationCatalog : DestinationCatalog {
    override fun resolve(entry: BackStackEntry): DestinationBinding = when (entry.route) {
        is ContentRoute -> DestinationBinding.Content(OwnerGeometryContentScreen(entry))
        is ModalRoute -> DestinationBinding.Modal(TaggedModalScreen(entry))
        else -> DestinationBinding.Unsupported(entry)
    }
}

private class OwnerGeometryContentScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {
    @Composable
    override fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("content:${entry.id.value}"),
        ) {
            if (entry.route is Home) {
                Box(
                    Modifier
                        .offset(OWNER_OFFSET_X, OWNER_OFFSET_Y)
                        .size(120.dp),
                ) {
                    childContent()
                }
            } else {
                childContent()
            }
        }
    }
}

private class TaggedModalScreen(
    override val entry: BackStackEntry,
) : ModalScreen(entry) {
    @Composable
    override fun ModalContent(
        targetState: ModalScreenState,
        entrance: ModalEntrance,
        onDismissRequest: () -> Unit,
        onExitFinished: () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .testTag("modal:${entry.id.value}"),
        )
    }
}

private class MaterialBottomSheetDestinationCatalog(
    private val probe: MaterialModalProbe,
) : DestinationCatalog {
    override fun resolve(entry: BackStackEntry): DestinationBinding = when (entry.route) {
        is ContentRoute -> DestinationBinding.Content(TaggedContentScreen(entry))
        is ModalRoute -> DestinationBinding.Modal(MaterialBottomSheetModalScreen(entry, probe))
        else -> DestinationBinding.Unsupported(entry)
    }
}

private class MaterialBottomSheetModalScreen(
    override val entry: BackStackEntry,
    private val probe: MaterialModalProbe,
) : ModalScreen(entry) {
    @Composable
    override fun ModalContent(
        targetState: ModalScreenState,
        entrance: ModalEntrance,
        onDismissRequest: () -> Unit,
        onExitFinished: () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        SideEffect {
            probe.targetStates[entry.id] = targetState
            probe.entrances[entry.id] = entrance
        }
        BottomSheetLayout(
            targetState = targetState,
            entrance = entrance,
            onDismissRequest = {
                probe.dismissRequests += entry.id
                onDismissRequest()
            },
            onExitFinished = {
                probe.exitCompletions += entry.id
                onExitFinished()
            },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clickable { probe.interactions += entry.id }
                    .testTag("modal:${entry.id.value}")
                    .onGloballyPositioned { coordinates ->
                        probe.geometry[entry.id] = MaterialModalGeometry(
                            heightPx = coordinates.size.height,
                            topPx = coordinates.positionInRoot().y,
                        )
                    },
            )
        }
    }
}

private data class MaterialModalGeometry(
    val heightPx: Int,
    val topPx: Float,
)

private class MaterialModalProbe {
    var rootHeightPx: Float = 0f
    val targetStates = mutableMapOf<EntryId, ModalScreenState>()
    val entrances = mutableMapOf<EntryId, ModalEntrance>()
    val geometry = mutableMapOf<EntryId, MaterialModalGeometry>()
    val dismissRequests = mutableListOf<EntryId>()
    val exitCompletions = mutableListOf<EntryId>()
    val interactions = mutableListOf<EntryId>()

    fun targetState(entryId: EntryId): ModalScreenState =
        checkNotNull(targetStates[entryId]) { "No target state recorded for $entryId" }

    fun isExactlyExpanded(entryId: EntryId): Boolean {
        val current = geometry[entryId] ?: return false
        return rootHeightPx > 0f &&
            current.heightPx > 0 &&
            current.topPx >= 0f &&
            abs(current.topPx + current.heightPx - rootHeightPx) < 1f
    }
}

private class ProbedDestinationCatalog(
    private val probe: ModalLifecycleProbe,
) : DestinationCatalog {
    override fun resolve(entry: BackStackEntry): DestinationBinding = when (entry.route) {
        is ContentRoute -> DestinationBinding.Content(TaggedContentScreen(entry))
        is ModalRoute -> DestinationBinding.Modal(ProbedModalScreen(entry, probe))
        else -> DestinationBinding.Unsupported(entry)
    }
}

private class ToggleableProbedDestinationCatalog(
    probe: ModalLifecycleProbe,
) : DestinationCatalog {
    private val delegate = ProbedDestinationCatalog(probe)
    private val rejectBindingsState = mutableStateOf(false)

    var rejections: Int = 0
        private set

    var rejectBindings: Boolean
        get() = rejectBindingsState.value
        set(value) {
            rejectBindingsState.value = value
        }

    override fun resolve(entry: BackStackEntry): DestinationBinding =
        if (rejectBindings) {
            rejections += 1
            DestinationBinding.Unsupported(entry)
        } else {
            delegate.resolve(entry)
        }
}

private class ProbedModalScreen(
    override val entry: BackStackEntry,
    private val probe: ModalLifecycleProbe,
) : ModalScreen(entry) {
    @Composable
    override fun ModalContent(
        targetState: ModalScreenState,
        entrance: ModalEntrance,
        onDismissRequest: () -> Unit,
        onExitFinished: () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        SideEffect {
            probe.record(
                entryId = entry.id,
                targetState = targetState,
                entrance = entrance,
                onDismissRequest = onDismissRequest,
                onExitFinished = onExitFinished,
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .testTag("modal:${entry.id.value}"),
        )
    }
}

private class ModalLifecycleProbe {
    private val targetStates = mutableMapOf<EntryId, ModalScreenState>()
    private val entrances = mutableMapOf<EntryId, ModalEntrance>()
    private val dismissRequests = mutableMapOf<EntryId, () -> Unit>()
    private val exitFinishedCallbacks = mutableMapOf<EntryId, () -> Unit>()

    fun record(
        entryId: EntryId,
        targetState: ModalScreenState,
        entrance: ModalEntrance,
        onDismissRequest: () -> Unit,
        onExitFinished: () -> Unit,
    ) {
        targetStates[entryId] = targetState
        entrances[entryId] = entrance
        dismissRequests[entryId] = onDismissRequest
        exitFinishedCallbacks[entryId] = onExitFinished
    }

    fun targetState(entryId: EntryId): ModalScreenState =
        checkNotNull(targetStates[entryId]) { "No target state recorded for $entryId" }

    fun entrance(entryId: EntryId): ModalEntrance =
        checkNotNull(entrances[entryId]) { "No entrance recorded for $entryId" }

    fun requestDismiss(entryId: EntryId) {
        checkNotNull(dismissRequests[entryId]) { "No dismiss callback recorded for $entryId" }()
    }

    fun exitFinishedCallback(entryId: EntryId): () -> Unit =
        checkNotNull(exitFinishedCallbacks[entryId]) {
            "No exit-finished callback recorded for $entryId"
        }
}
