package com.shmakov.udf.composable.common

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shmakov.udf.NavigationRenderTarget
import com.shmakov.udf.navigation.Account
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
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.NavigationLayoutPolicy
import com.shmakov.udf.navigation.NavProjector
import com.shmakov.udf.navigation.NavProjectionResult
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.Screen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntrySaveableStateRegressionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var controller: EntryStateTestController
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @Before
    fun useDeterministicComposeClock() {
        composeRule.mainClock.autoAdvance = false
    }

    @After
    fun closeActivity() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun sameExactAccountsEntryMovesRootNestedRootWithoutResetOrDuplicateNode() {
        val home = entry("layout-home", Home)
        val accounts = entry("layout-accounts", Accounts)
        val history = state(home, accounts)
        launch(
            frame(
                navState = history,
                policy = singlePane,
                revision = 0L,
            ),
        )
        awaitEntryValue(accounts.id, expectedValue = 0)
        increment(accounts.id, expectedValue = 1)

        show(
            frame(
                navState = history,
                policy = expandedPane,
                revision = 0L,
            ),
        )
        awaitEntryValue(accounts.id, expectedValue = 1)

        show(
            frame(
                navState = history,
                policy = singlePane,
                revision = 0L,
            ),
        )
        awaitEntryValue(accounts.id, expectedValue = 1)
    }

    @Test
    fun entryRemainingInHiddenHistoryKeepsStateAcrossNavigateAwayAndBack() {
        val home = entry("history-home", Home)
        val accounts = entry("history-accounts", Accounts)
        val cards = entry("history-cards", Cards)
        launch(frame(state(home, accounts), singlePane, revision = 0L))
        awaitEntryValue(accounts.id, expectedValue = 0)
        increment(accounts.id, expectedValue = 1)

        show(
            frame(
                navState = state(home, accounts, cards),
                policy = singlePane,
                revision = 1L,
                transition = NavTransitionIntent.Pushed(accounts.id, cards.id),
            ),
        )
        awaitEntryValue(cards.id, expectedValue = 0)

        show(
            frame(
                navState = state(home, accounts),
                policy = singlePane,
                revision = 2L,
                transition = NavTransitionIntent.Popped(cards.id, accounts.id),
            ),
        )
        awaitEntryValue(accounts.id, expectedValue = 1)
    }

    @Test
    fun duplicateRoutesWithDifferentEntryIdsKeepIndependentState() {
        val home = entry("duplicates-home", Home)
        val first = entry("duplicates-accounts-first", Accounts)
        val second = entry("duplicates-accounts-second", Accounts)
        assertNotEquals(first.id, second.id)
        launch(frame(state(home, first), singlePane, revision = 0L))
        awaitEntryValue(first.id, expectedValue = 0)
        increment(first.id, expectedValue = 1)

        show(
            frame(
                navState = state(home, first, second),
                policy = singlePane,
                revision = 1L,
                transition = NavTransitionIntent.Pushed(first.id, second.id),
            ),
        )
        awaitEntryValue(second.id, expectedValue = 0)
        awaitEntryAbsent(first.id)
        increment(second.id, expectedValue = 1)
        increment(second.id, expectedValue = 2)

        show(
            frame(
                navState = state(home, first),
                policy = singlePane,
                revision = 2L,
                transition = NavTransitionIntent.Popped(second.id, first.id),
            ),
        )
        awaitEntryValue(first.id, expectedValue = 1)
    }

    @Test
    fun poppedRouteWithFreshEntryIdStartsFromDefaultState() {
        val home = entry("fresh-home", Home)
        val removed = entry("fresh-accounts-removed", Accounts)
        val fresh = entry("fresh-accounts-new", Accounts)
        assertNotEquals(removed.id, fresh.id)
        launch(frame(state(home, removed), singlePane, revision = 0L))
        awaitEntryValue(removed.id, expectedValue = 0)
        increment(removed.id, expectedValue = 1)

        show(
            frame(
                navState = state(home),
                policy = singlePane,
                revision = 1L,
                transition = NavTransitionIntent.Popped(removed.id, home.id),
            ),
        )
        awaitEntryValue(home.id, expectedValue = 0)
        awaitEntryAbsent(removed.id)

        show(
            frame(
                navState = state(home, fresh),
                policy = singlePane,
                revision = 2L,
                transition = NavTransitionIntent.Pushed(home.id, fresh.id),
            ),
        )
        awaitEntryValue(fresh.id, expectedValue = 0)
    }

    @Test
    fun visibleAndHiddenRetainedEntriesSurviveActivityScenarioRecreate() {
        val home = entry("recreate-home", Home)
        val accounts = entry("recreate-accounts", Accounts)
        val cards = entry("recreate-cards", Cards)
        launch(frame(state(home, accounts), singlePane, revision = 0L))
        awaitEntryValue(accounts.id, expectedValue = 0)
        increment(accounts.id, expectedValue = 1)

        show(
            frame(
                navState = state(home, accounts, cards),
                policy = singlePane,
                revision = 1L,
                transition = NavTransitionIntent.Pushed(accounts.id, cards.id),
            ),
        )
        awaitEntryValue(cards.id, expectedValue = 0)
        awaitEntryAbsent(accounts.id)
        increment(cards.id, expectedValue = 1)
        increment(cards.id, expectedValue = 2)

        recreateActivity()
        awaitEntryValue(cards.id, expectedValue = 2)

        show(
            frame(
                navState = state(home, accounts),
                policy = singlePane,
                revision = 2L,
                transition = NavTransitionIntent.Popped(cards.id, accounts.id),
            ),
        )
        awaitEntryValue(accounts.id, expectedValue = 1)
    }

    @Test
    fun poppedEntryDoesNotReturnThroughActivityScenarioRecreateOrFreshRoute() {
        val home = entry("recreate-pop-home", Home)
        val removed = entry("recreate-pop-accounts-removed", Accounts)
        val fresh = entry("recreate-pop-accounts-fresh", Accounts)
        launch(frame(state(home, removed), singlePane, revision = 0L))
        awaitEntryValue(removed.id, expectedValue = 0)
        increment(removed.id, expectedValue = 1)

        show(
            frame(
                navState = state(home),
                policy = singlePane,
                revision = 1L,
                transition = NavTransitionIntent.Popped(removed.id, home.id),
            ),
        )
        awaitEntryValue(home.id, expectedValue = 0)
        awaitEntryAbsent(removed.id)

        recreateActivity()
        awaitEntryValue(home.id, expectedValue = 0)

        show(
            frame(
                navState = state(home, fresh),
                policy = singlePane,
                revision = 2L,
                transition = NavTransitionIntent.Pushed(home.id, fresh.id),
            ),
        )
        awaitEntryValue(fresh.id, expectedValue = 0)
    }

    @Test
    fun poppedEntryStateIsRemovedBeforeRecreateAndSameIdCleanupProbe() {
        val home = entry("cleanup-home", Home)
        val removed = entry("cleanup-accounts", Accounts)
        launch(frame(state(home, removed), singlePane, revision = 0L))
        awaitEntryValue(removed.id, expectedValue = 0)
        increment(removed.id, expectedValue = 1)

        show(
            frame(
                navState = state(home),
                policy = singlePane,
                revision = 1L,
                transition = NavTransitionIntent.Popped(removed.id, home.id),
            ),
        )
        awaitEntryValue(home.id, expectedValue = 0)
        awaitEntryAbsent(removed.id)
        recreateActivity()
        awaitEntryValue(home.id, expectedValue = 0)

        // A real new occurrence must use a fresh ID. Reusing the retired ID here is deliberately
        // only a black-box probe that its SaveableStateHolder state was actually removed.
        val cleanupProbe = BackStackEntry(removed.id, Accounts)
        show(
            frame(
                navState = state(home, cleanupProbe),
                policy = singlePane,
                revision = 2L,
                transition = NavTransitionIntent.Pushed(home.id, cleanupProbe.id),
            ),
        )
        awaitEntryValue(cleanupProbe.id, expectedValue = 0)
    }

    @Test
    fun retainedModalKeepsStateAfterExactExitCompletionAndLaterContentPop() {
        val home = entry("modal-home", Home)
        val modal = entry("modal-account", Account(accountId = 42))
        val cards = entry("modal-cards", Cards)
        val modalProbe = EntryStateModalProbe()
        launch(
            initialFrame = frame(
                navState = state(home, modal),
                policy = expandedPane,
                revision = 0L,
            ),
            destinationCatalog = EntryStateDestinationCatalog(modalProbe),
        )
        awaitModalValue(modal.id, expectedValue = 0)
        incrementModal(modal.id, expectedValue = 1)

        show(
            frame(
                // The later content hides the modal projection, while modal.id remains in the
                // complete logical history supplied to NavigationRenderTarget.
                navState = state(home, modal, cards),
                policy = expandedPane,
                revision = 1L,
                transition = NavTransitionIntent.Pushed(modal.id, cards.id),
            ),
        )
        awaitEntryValue(cards.id, expectedValue = 0)
        awaitModalTargetState(modalProbe, modal.id, ModalScreenState.Hidden)
        val exactExitCompletion = modalProbe.exitFinishedCallback(modal.id)
        composeRule.runOnIdle {
            exactExitCompletion()
        }
        awaitModalAbsent(modal.id)

        show(
            frame(
                navState = state(home, modal),
                policy = expandedPane,
                revision = 2L,
                transition = NavTransitionIntent.Popped(cards.id, modal.id),
            ),
        )
        awaitModalValue(modal.id, expectedValue = 1)

        recreateActivity()
        awaitModalValue(modal.id, expectedValue = 1)
    }

    @Test
    fun rapidRootChurnOwnsModalOnceAndExactExitCannotResurrectIt() {
        val firstRoot = entry("modal-churn-root-first", Home)
        val secondRoot = entry("modal-churn-root-second", Cards)
        val modal = entry("modal-churn-account", Account(accountId = 73))
        val modalProbe = EntryStateModalProbe()
        val navigationActions = mutableListOf<NavAction>()
        launch(
            initialFrame = frame(
                navState = state(firstRoot, modal),
                policy = singlePane,
                revision = 0L,
            ),
            destinationCatalog = EntryStateDestinationCatalog(modalProbe),
            onNavigationAction = navigationActions::add,
        )
        awaitModalValue(modal.id, expectedValue = 0)
        assertModalCount(modal.id, expectedCount = 1)

        show(
            frame(
                navState = state(secondRoot, modal),
                policy = singlePane,
                revision = 1L,
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = modal.id,
                    targetTopEntryId = modal.id,
                ),
            ),
        )
        advanceOneApplicationFrame()
        assertModalCount(modal.id, expectedCount = 1)

        // Interrupt A -> B before its Replace tween settles, return to A's root identity, and
        // remove M. Every still-alive root branch competes here, but only one may own M.
        show(
            frame(
                navState = state(firstRoot),
                policy = singlePane,
                revision = 2L,
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = modal.id,
                    targetTopEntryId = firstRoot.id,
                ),
            ),
        )
        advanceOneApplicationFrame()
        assertModalCountAtMostOne(modal.id)
        advanceFramesUntil(
            description = "rapid root churn modal ${modal.id.value} exact Hidden phase",
        ) {
            assertModalCountAtMostOne(modal.id)
            modalProbe.targetStateOrNull(modal.id) == ModalScreenState.Hidden
        }

        val exactExitCompletion = modalProbe.exitFinishedCallback(modal.id)
        composeRule.runOnIdle {
            exactExitCompletion()
        }
        advanceFramesUntil(
            description = "rapid root churn modal ${modal.id.value} exact exit disposal",
        ) {
            assertModalCountAtMostOne(modal.id)
            modalCount(modal.id) == 0
        }
        repeat(POST_EXIT_STABILITY_FRAMES) {
            advanceOneApplicationFrame()
            assertModalCount(modal.id, expectedCount = 0)
        }
        assertEquals(emptyList<NavAction>(), navigationActions)
    }

    @Test
    fun deepNestedRelocationOwnsExactContentAndItsModalOnlyOnce() {
        val home = entry("deep-relocation-home", Home)
        val intermediate = entry("deep-relocation-accounts", Accounts)
        val relocated = entry("deep-relocation-cards", Cards)
        val modal = entry("deep-relocation-modal", Account(accountId = 91))
        val history = state(home, intermediate, relocated, modal)
        val directChildPolicy = NavigationLayoutPolicy { request ->
            when (request.nextContent.id) {
                intermediate.id,
                relocated.id -> ContentPlacementDecision.childOf(home.id)

                else -> ContentPlacementDecision.root()
            }
        }
        val deepChildPolicy = NavigationLayoutPolicy { request ->
            when (request.nextContent.id) {
                intermediate.id -> ContentPlacementDecision.childOf(home.id)
                relocated.id -> ContentPlacementDecision.childOf(intermediate.id)
                else -> ContentPlacementDecision.root()
            }
        }
        val modalProbe = EntryStateModalProbe()
        launch(
            initialFrame = frame(
                // A projects H -> X; placing X under H prunes the earlier Y slot.
                navState = history,
                policy = directChildPolicy,
                revision = 0L,
            ),
            destinationCatalog = EntryStateDestinationCatalog(modalProbe),
        )
        awaitEntryValue(relocated.id, expectedValue = 0)
        awaitModalValue(modal.id, expectedValue = 0)
        incrementModal(modal.id, expectedValue = 1)

        show(
            frame(
                // B projects H -> Y -> X from the same history/revision. During its first frame,
                // outgoing direct X and latest deep X overlap, while M remains desired by exact X.
                navState = history,
                policy = deepChildPolicy,
                revision = 0L,
            ),
        )
        advanceOneApplicationFrame()
        assertEntryValueNow(relocated.id, expectedValue = 0)
        assertModalValueNow(modal.id, expectedValue = 1)

        repeat(LAYOUT_SETTLE_FRAMES) {
            advanceOneApplicationFrame()
            assertEntryValueNow(relocated.id, expectedValue = 0)
            assertModalValueNow(modal.id, expectedValue = 1)
        }
        assertEntryValueNow(intermediate.id, expectedValue = 0)
    }

    @Test
    fun transientTypedBindingFailureDoesNotDiscardValidTargetEntryState() {
        val accounts = entry("binding-failure-accounts", Accounts)
        val unchangedTarget = frame(
            navState = state(accounts),
            policy = singlePane,
            revision = 0L,
        )
        val catalog = ToggleableFailureDestinationCatalog()
        launch(
            initialFrame = unchangedTarget,
            destinationCatalog = catalog,
        )
        awaitEntryValue(accounts.id, expectedValue = 0)
        increment(accounts.id, expectedValue = 1)

        composeRule.runOnIdle {
            catalog.rejectBindings = true
        }
        awaitEntryAbsent(accounts.id)

        composeRule.runOnIdle {
            catalog.rejectBindings = false
        }
        // Binding recovery does not accept a new navigation target and must reuse the exact
        // entry-scoped state that belonged to the unchanged valid target.
        awaitEntryValue(accounts.id, expectedValue = 1)
    }

    @Test
    fun interruptedRelocationThenRemovalInvokesExactEntryProviderOnlyOnce() {
        val accounts = entry("overlap-accounts", Accounts)
        val home = entry("overlap-home", Home)
        val cards = entry("overlap-cards", Cards)
        launch(
            frame(
                navState = state(accounts),
                policy = singlePane,
                revision = 0L,
            ),
        )
        awaitEntryValue(accounts.id, expectedValue = 0)
        increment(accounts.id, expectedValue = 1)

        show(
            frame(
                navState = state(home, accounts),
                policy = expandedPane,
                revision = 1L,
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = accounts.id,
                    targetTopEntryId = accounts.id,
                ),
            ),
        )
        advanceOneApplicationFrame()
        assertEntryValueNow(accounts.id, expectedValue = 1)

        // Interrupt the root Replace while both its old and new branches are still alive. X is
        // absent from C, but only one outgoing branch may own X's movable saveable provider.
        show(
            frame(
                navState = state(cards),
                policy = singlePane,
                revision = 2L,
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = accounts.id,
                    targetTopEntryId = cards.id,
                ),
            ),
        )
        advanceOneApplicationFrame()
        assertEntryValueNow(accounts.id, expectedValue = 1)
        assertEntryValueNow(cards.id, expectedValue = 0)

        awaitEntryAbsent(accounts.id)
        awaitEntryAbsent(home.id)
        awaitEntryValue(cards.id, expectedValue = 0)
    }

    private fun launch(
        initialFrame: EntryStateTestFrame,
        destinationCatalog: DestinationCatalog = EntryStateDestinationCatalog(),
        onNavigationAction: (NavAction) -> Unit = {},
    ) {
        check(scenario == null) { "Test activity was already launched" }
        controller = EntryStateTestController(
            initialFrame = initialFrame,
            destinationCatalog = destinationCatalog,
            onNavigationAction = onNavigationAction,
        )
        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { launched ->
            launched.installTestContent()
        }
    }

    private fun recreateActivity() {
        requireNotNull(scenario).apply {
            recreate()
            installTestContent()
        }
    }

    private fun ActivityScenario<ComponentActivity>.installTestContent() {
        onActivity { activity ->
            activity.setContent {
                EntryStateTestHost(
                    frame = controller.frame.value,
                    destinationCatalog = controller.destinationCatalog,
                    onNavigationAction = controller.onNavigationAction,
                )
            }
        }
    }

    private fun show(target: EntryStateTestFrame) {
        composeRule.runOnIdle {
            controller.frame.value = target
        }
    }

    private fun increment(
        entryId: EntryId,
        expectedValue: Int,
    ) {
        composeRule.onNodeWithTag(incrementTag(entryId), useUnmergedTree = true)
            .performClick()
        awaitEntryValue(entryId, expectedValue)
    }

    private fun incrementModal(
        entryId: EntryId,
        expectedValue: Int,
    ) {
        composeRule.onNodeWithTag(modalIncrementTag(entryId), useUnmergedTree = true)
            .performClick()
        awaitModalValue(entryId, expectedValue)
    }

    private fun awaitEntryValue(
        entryId: EntryId,
        expectedValue: Int,
    ) {
        advanceFramesUntil(
            description = "entry ${entryId.value} value $expectedValue with exactly one node",
        ) {
            composeRule.onAllNodesWithTag(
                screenTag(entryId),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size == 1 &&
                composeRule.onAllNodesWithTag(
                    valueTag(entryId, expectedValue),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag(
            valueTag(entryId, expectedValue),
            useUnmergedTree = true,
        ).assertTextEquals(expectedValue.toString())
    }

    private fun assertEntryValueNow(
        entryId: EntryId,
        expectedValue: Int,
    ) {
        composeRule.onAllNodesWithTag(
            screenTag(entryId),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onAllNodesWithTag(
            valueTag(entryId, expectedValue),
            useUnmergedTree = true,
        ).assertCountEquals(1)
    }

    private fun awaitEntryAbsent(entryId: EntryId) {
        advanceFramesUntil(
            description = "entry ${entryId.value} to leave composition",
        ) {
            composeRule.onAllNodesWithTag(
                screenTag(entryId),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun awaitModalValue(
        entryId: EntryId,
        expectedValue: Int,
    ) {
        advanceFramesUntil(
            description = "modal ${entryId.value} value $expectedValue with exactly one node",
        ) {
            composeRule.onAllNodesWithTag(
                modalScreenTag(entryId),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size == 1 &&
                composeRule.onAllNodesWithTag(
                    modalValueTag(entryId, expectedValue),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag(
            modalValueTag(entryId, expectedValue),
            useUnmergedTree = true,
        ).assertTextEquals(expectedValue.toString())
    }

    private fun awaitModalTargetState(
        probe: EntryStateModalProbe,
        entryId: EntryId,
        expectedState: ModalScreenState,
    ) {
        advanceFramesUntil(
            description = "modal ${entryId.value} target state $expectedState",
        ) {
            probe.targetStateOrNull(entryId) == expectedState
        }
    }

    private fun awaitModalAbsent(entryId: EntryId) {
        advanceFramesUntil(
            description = "modal ${entryId.value} to leave composition",
        ) {
            composeRule.onAllNodesWithTag(
                modalScreenTag(entryId),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun modalCount(entryId: EntryId): Int =
        composeRule.onAllNodesWithTag(
            modalScreenTag(entryId),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size

    private fun assertModalCount(
        entryId: EntryId,
        expectedCount: Int,
    ) {
        composeRule.onAllNodesWithTag(
            modalScreenTag(entryId),
            useUnmergedTree = true,
        ).assertCountEquals(expectedCount)
    }

    private fun assertModalValueNow(
        entryId: EntryId,
        expectedValue: Int,
    ) {
        assertModalCount(entryId, expectedCount = 1)
        composeRule.onAllNodesWithTag(
            modalValueTag(entryId, expectedValue),
            useUnmergedTree = true,
        ).assertCountEquals(1)
    }

    private fun assertModalCountAtMostOne(entryId: EntryId) {
        val actualCount = modalCount(entryId)
        if (actualCount > 1) {
            throw AssertionError(
                "Modal ${entryId.value} had $actualCount providers; expected at most one",
            )
        }
    }

    private inline fun advanceFramesUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        repeat(MAX_APPLY_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            if (condition()) return
        }
        throw AssertionError("Timed out waiting for $description")
    }

    private fun advanceOneApplicationFrame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private fun frame(
        navState: NavState,
        policy: NavigationLayoutPolicy,
        revision: Long,
        transition: NavTransitionIntent? = null,
    ): EntryStateTestFrame = EntryStateTestFrame(
        navState = navState,
        policy = policy,
        navigationRevision = revision,
        transition = transition,
    )

    private fun state(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> throw AssertionError(
                "Invalid entry-state fixture: ${result.problems}",
            )
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private companion object {
        const val MAX_APPLY_FRAMES = 180
        const val POST_EXIT_STABILITY_FRAMES = 8
        const val LAYOUT_SETTLE_FRAMES = 30
    }
}

private class EntryStateTestController(
    initialFrame: EntryStateTestFrame,
    val destinationCatalog: DestinationCatalog,
    val onNavigationAction: (NavAction) -> Unit,
) {
    val frame = mutableStateOf(initialFrame)
}

private data class EntryStateTestFrame(
    val navState: NavState,
    val policy: NavigationLayoutPolicy,
    val navigationRevision: Long,
    val transition: NavTransitionIntent?,
)

@Composable
private fun EntryStateTestHost(
    frame: EntryStateTestFrame,
    destinationCatalog: DestinationCatalog,
    onNavigationAction: (NavAction) -> Unit,
) {
    val tree = when (val projection = NavProjector.project(frame.navState, frame.policy)) {
        is NavProjectionResult.Success -> projection.tree
        is NavProjectionResult.Failure -> throw AssertionError(
            "Entry-state fixture projection failed: ${projection.problem}",
        )
    }
    AnimatedNavigation(
        renderTarget = NavigationRenderTarget(
            navigationRevision = frame.navigationRevision,
            historyEntryIds = frame.navState.entries.map { entry -> entry.id },
            tree = tree,
            transitionIntent = frame.transition,
        ),
        onNavigationAction = onNavigationAction,
        destinationCatalog = destinationCatalog,
    )
}

private class EntryStateDestinationCatalog(
    private val modalProbe: EntryStateModalProbe? = null,
) : DestinationCatalog {
    override fun resolve(entry: BackStackEntry): DestinationBinding = when (entry.route) {
        is ContentRoute -> DestinationBinding.Content(
            EntryStateScreen(entry),
            parentInputsKey = Unit,
        )
        is ModalRoute -> DestinationBinding.Modal(
            EntryStateModalScreen(
                entry = entry,
                probe = requireNotNull(modalProbe) {
                    "Modal fixture ${entry.id.value} requires an EntryStateModalProbe"
                },
            ),
        )

        else -> DestinationBinding.Unsupported(entry)
    }
}

private class ToggleableFailureDestinationCatalog : DestinationCatalog {
    private val validCatalog = EntryStateDestinationCatalog()

    var rejectBindings by mutableStateOf(false)

    override fun resolve(entry: BackStackEntry): DestinationBinding =
        if (rejectBindings) {
            DestinationBinding.Unsupported(entry)
        } else {
            validCatalog.resolve(entry)
        }
}

private class EntryStateScreen(
    override val entry: BackStackEntry,
) : Screen(entry) {
    @Composable
    override fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        var value by rememberSaveable { mutableStateOf(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(screenTag(entry.id)),
        ) {
            Text(
                text = value.toString(),
                modifier = Modifier.testTag(valueTag(entry.id, value)),
            )
            Button(
                onClick = { value += 1 },
                modifier = Modifier.testTag(incrementTag(entry.id)),
            ) {
                Text("Increment ${entry.id.value}")
            }
            Box(Modifier.fillMaxSize()) {
                childContent()
            }
        }
    }
}

private class EntryStateModalScreen(
    override val entry: BackStackEntry,
    private val probe: EntryStateModalProbe,
) : ModalScreen(entry) {
    @Composable
    override fun ModalContent(
        targetState: ModalScreenState,
        entrance: ModalEntrance,
        onDismissRequest: () -> Unit,
        onExitFinished: () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        var value by rememberSaveable { mutableStateOf(0) }
        SideEffect {
            probe.record(
                entryId = entry.id,
                targetState = targetState,
                onExitFinished = onExitFinished,
            )
        }
        Column(
            modifier = Modifier.testTag(modalScreenTag(entry.id)),
        ) {
            Text(
                text = value.toString(),
                modifier = Modifier.testTag(modalValueTag(entry.id, value)),
            )
            Button(
                onClick = { value += 1 },
                modifier = Modifier.testTag(modalIncrementTag(entry.id)),
            ) {
                Text("Increment modal ${entry.id.value}")
            }
        }
    }
}

private class EntryStateModalProbe {
    private val targetStates = mutableMapOf<EntryId, ModalScreenState>()
    private val exitFinishedCallbacks = mutableMapOf<EntryId, () -> Unit>()

    fun record(
        entryId: EntryId,
        targetState: ModalScreenState,
        onExitFinished: () -> Unit,
    ) {
        targetStates[entryId] = targetState
        exitFinishedCallbacks[entryId] = onExitFinished
    }

    fun targetStateOrNull(entryId: EntryId): ModalScreenState? = targetStates[entryId]

    fun exitFinishedCallback(entryId: EntryId): () -> Unit =
        checkNotNull(exitFinishedCallbacks[entryId]) {
            "No exit-finished callback recorded for ${entryId.value}"
        }
}

private val singlePane = NavigationLayoutPolicy {
    ContentPlacementDecision.root()
}

private val expandedPane = NavigationLayoutPolicy { request ->
    if (request.contentPath.first().entry.route == Home) {
        ContentPlacementDecision.childOf(request.contentPath.first().entry.id)
    } else {
        ContentPlacementDecision.root()
    }
}

private fun screenTag(entryId: EntryId): String = "entry-screen:${entryId.value}"

private fun incrementTag(entryId: EntryId): String = "entry-increment:${entryId.value}"

private fun valueTag(entryId: EntryId, value: Int): String =
    "entry-value:${entryId.value}:$value"

private fun modalScreenTag(entryId: EntryId): String = "modal-screen:${entryId.value}"

private fun modalIncrementTag(entryId: EntryId): String = "modal-increment:${entryId.value}"

private fun modalValueTag(entryId: EntryId, value: Int): String =
    "modal-value:${entryId.value}:$value"
