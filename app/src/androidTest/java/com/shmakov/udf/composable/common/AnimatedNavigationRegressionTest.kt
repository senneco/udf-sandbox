package com.shmakov.udf.composable.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shmakov.udf.NavigationRenderTarget
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.ContentPlacementDecision
import com.shmakov.udf.navigation.ContentRoute
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
                tree = project(state(home, accounts, account), expandedPane),
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
                tree = project(state(home, accounts, account, details), expandedPane),
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
                tree = project(navState, singlePane),
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
                tree = project(navState, expandedPane),
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

    private fun target(
        revision: Long,
        tree: NavigationRenderTree,
        transition: NavTransitionIntent?,
    ): NavigationRenderTarget = NavigationRenderTarget(
        navigationRevision = revision,
        tree = tree,
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

private class TaggedModalScreen(
    override val entry: BackStackEntry,
) : ModalScreen(entry) {
    @Composable
    override fun ModalContent(
        targetState: ModalScreenState,
        onHide: () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .testTag("modal:${entry.id.value}"),
        )
    }
}
