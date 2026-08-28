package com.shmakov.udf.composable.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shmakov.udf.NavigationRenderTarget
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.ContentPlacementDecision
import com.shmakov.udf.navigation.ContentRoute
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class NestedRecompositionIsolationRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var committedPasses: CommittedContentPassRegistry
    private lateinit var catalog: FreshProbedDestinationCatalog
    private lateinit var currentTarget: androidx.compose.runtime.MutableState<NavigationRenderTarget>
    private lateinit var bindingRefreshTrigger: MutableState<Int>

    @Before
    fun setUp() {
        composeRule.mainClock.autoAdvance = false
        committedPasses = CommittedContentPassRegistry()
        catalog = FreshProbedDestinationCatalog(committedPasses)
    }

    @Test
    fun nestedLocalStateChangeDoesNotRecomposeStableParentContent() {
        val home = entry("local-home", Home)
        val accounts = entry("local-accounts", Accounts)
        launch(target(revision = 0L, navState = state(home, accounts)))

        val parentBaseline = committedPasses.committedPassCount(home.id)
        val nestedBaseline = committedPasses.committedPassCount(accounts.id)

        composeRule.onNodeWithTag(incrementTag(accounts.id), useUnmergedTree = true)
            .performClick()
        advanceFramesUntil(
            description = "nested local value to change",
            afterEachFrame = {},
            condition = { hasValue(accounts.id, expectedValue = 1) },
        )

        composeRule.onNodeWithTag(valueTag(accounts.id, value = 1), useUnmergedTree = true)
            .assertTextEquals("1")
        assertEquals(parentBaseline, committedPasses.committedPassCount(home.id))
        assertTrue(
            "Nested content did not commit after its local state changed",
            committedPasses.committedPassCount(accounts.id) > nestedBaseline,
        )
    }

    @Test
    fun nestedDestinationReplacementDoesNotRecomposeStableParentContent() {
        val home = entry("replacement-home", Home)
        val accounts = entry("replacement-accounts", Accounts)
        val transactions = entry("replacement-transactions", Transactions)
        launch(target(revision = 0L, navState = state(home, accounts)))

        val parentBaseline = committedPasses.committedPassCount(home.id)
        val parentBindingBaseline = catalog.resolutionCount(home.id)

        composeRule.runOnIdle {
            currentTarget.value = target(
                revision = 1L,
                navState = state(home, transactions),
                transition = NavTransitionIntent.HistoryReplaced(
                    previousTopEntryId = accounts.id,
                    targetTopEntryId = transactions.id,
                ),
            )
        }
        var sawOutgoingAndIncomingTogether = false
        advanceFramesUntil(
            description = "nested replacement to settle",
            afterEachFrame = {
                assertEquals(
                    "Stable parent committed during nested replacement",
                    parentBaseline,
                    committedPasses.committedPassCount(home.id),
                )
                val accountsVisible = hasContent(accounts.id)
                val transactionsVisible = hasContent(transactions.id)
                sawOutgoingAndIncomingTogether = sawOutgoingAndIncomingTogether ||
                    accountsVisible && transactionsVisible
            },
            condition = {
                !hasContent(accounts.id) && hasContent(transactions.id)
            },
        )

        composeRule.onNodeWithTag(contentTag(accounts.id), useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(contentTag(transactions.id), useUnmergedTree = true)
            .assertExists()
        assertTrue(
            "Nested replacement never exposed outgoing and incoming content together",
            sawOutgoingAndIncomingTogether,
        )
        assertTrue(
            "Incoming nested content never committed",
            committedPasses.committedPassCount(transactions.id) > 0,
        )
        assertTrue(
            "Catalog did not create a fresh parent Screen binding for the new target",
            catalog.resolutionCount(home.id) > parentBindingBaseline,
        )
        assertEquals(
            "Stable parent committed after nested replacement settled",
            parentBaseline,
            committedPasses.committedPassCount(home.id),
        )
    }

    @Test
    fun parentLocalStateChangeIsObservedByProbe() {
        val home = entry("control-home", Home)
        val accounts = entry("control-accounts", Accounts)
        launch(target(revision = 0L, navState = state(home, accounts)))

        val parentBaseline = committedPasses.committedPassCount(home.id)

        composeRule.onNodeWithTag(incrementTag(home.id), useUnmergedTree = true)
            .performClick()
        advanceFramesUntil(
            description = "parent local value to change",
            afterEachFrame = {},
            condition = { hasValue(home.id, expectedValue = 1) },
        )

        composeRule.onNodeWithTag(valueTag(home.id, value = 1), useUnmergedTree = true)
            .assertTextEquals("1")
        assertTrue(
            "Positive control did not observe a committed parent content pass",
            committedPasses.committedPassCount(home.id) > parentBaseline,
        )
    }

    @Test
    fun observableParentInputChangeRecomposesRetainedParentContent() {
        val home = entry("observable-home", Home)
        val accounts = entry("observable-accounts", Accounts)
        launch(target(revision = 0L, navState = state(home, accounts)))

        val parentBaseline = committedPasses.committedPassCount(home.id)

        composeRule.runOnIdle {
            catalog.updateObservableInput(home.id, value = 1)
        }
        advanceFramesUntil(
            description = "observable parent input to change",
            afterEachFrame = {},
            condition = { hasObservableInput(home.id, expectedValue = 1) },
        )

        composeRule.onNodeWithTag(observableInputTag(home.id, value = 1), true)
            .assertTextEquals("Input 1")
        assertTrue(
            "Observable parent input did not commit retained parent content",
            committedPasses.committedPassCount(home.id) > parentBaseline,
        )
    }

    @Test
    fun explicitParentInputsKeyChangeRecomposesParentContent() {
        val home = entry("explicit-home", Home)
        val accounts = entry("explicit-accounts", Accounts)
        val navState = state(home, accounts)
        launch(target(revision = 0L, navState = navState))

        val parentBaseline = committedPasses.committedPassCount(home.id)

        composeRule.runOnIdle {
            catalog.updateExplicitInput(home.id, value = 1)
            bindingRefreshTrigger.value += 1
        }
        advanceFramesUntil(
            description = "explicit parent input key to change",
            afterEachFrame = {},
            condition = { hasExplicitInput(home.id, expectedValue = 1) },
        )

        composeRule.onNodeWithTag(explicitInputTag(home.id, value = 1), true)
            .assertTextEquals("Explicit 1")
        assertTrue(
            "Explicit parent input key did not commit fresh parent content",
            committedPasses.committedPassCount(home.id) > parentBaseline,
        )
    }

    private fun launch(initialTarget: NavigationRenderTarget) {
        currentTarget = mutableStateOf(initialTarget)
        bindingRefreshTrigger = mutableStateOf(0)
        composeRule.setContent {
            val bindingRefresh = bindingRefreshTrigger.value
            val stableUntilRefreshActionSink: (NavAction) -> Unit = remember(bindingRefresh) {
                { _ -> check(bindingRefresh >= 0) }
            }
            AnimatedNavigation(
                renderTarget = currentTarget.value,
                onNavigationAction = stableUntilRefreshActionSink,
                destinationCatalog = catalog,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(contentTag(initialTarget.tree.root.entry.id), true)
            .assertExists()
        initialTarget.tree.nestedSlots.forEach { slot ->
            composeRule.onNodeWithTag(contentTag(slot.entry.id), true).assertExists()
        }
    }

    private fun hasContent(entryId: EntryId): Boolean =
        composeRule.onAllNodesWithTag(
            contentTag(entryId),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    private fun hasValue(entryId: EntryId, expectedValue: Int): Boolean =
        composeRule.onAllNodesWithTag(
            valueTag(entryId, expectedValue),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    private fun hasObservableInput(entryId: EntryId, expectedValue: Int): Boolean =
        composeRule.onAllNodesWithTag(
            observableInputTag(entryId, expectedValue),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    private fun hasExplicitInput(entryId: EntryId, expectedValue: Int): Boolean =
        composeRule.onAllNodesWithTag(
            explicitInputTag(entryId, expectedValue),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    private inline fun advanceFramesUntil(
        description: String,
        afterEachFrame: () -> Unit,
        condition: () -> Boolean,
    ) {
        repeat(MAX_APPLY_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            afterEachFrame()
            if (condition()) return
        }
        throw AssertionError("Timed out waiting for $description")
    }

    private fun target(
        revision: Long,
        navState: NavState,
        transition: NavTransitionIntent? = null,
    ): NavigationRenderTarget = NavigationRenderTarget(
        navigationRevision = revision,
        historyEntryIds = navState.entries.map { entry -> entry.id },
        tree = project(navState),
        transitionIntent = transition,
    )

    private fun project(navState: NavState): NavigationRenderTree =
        when (val result = NavProjector.project(navState, expandedPane)) {
            is NavProjectionResult.Success -> result.tree
            is NavProjectionResult.Failure -> error(
                "Invalid recomposition fixture: ${result.problem}",
            )
        }

    private fun state(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> error(
                "Invalid recomposition fixture: ${result.problems}",
            )
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private companion object {
        const val MAX_APPLY_FRAMES = 180

        val expandedPane = NavigationLayoutPolicy { request ->
            if (request.currentContent.entry.route is Home) {
                ContentPlacementDecision.childOf(request.currentContent.entry.id)
            } else {
                ContentPlacementDecision.root()
            }
        }

    }
}

private fun contentTag(entryId: EntryId): String = "probe:content:${entryId.value}"

private fun incrementTag(entryId: EntryId): String = "probe:increment:${entryId.value}"

private fun valueTag(entryId: EntryId, value: Int): String =
    "probe:value:${entryId.value}:$value"

private fun observableInputTag(entryId: EntryId, value: Int): String =
    "probe:input:${entryId.value}:$value"

private fun explicitInputTag(entryId: EntryId, value: Int): String =
    "probe:explicit-input:${entryId.value}:$value"

/** Records only successfully applied content passes; it is deliberately not Compose state. */
private class CommittedContentPassRegistry {
    private val committedPasses = ConcurrentHashMap<EntryId, AtomicInteger>()

    fun recordCommitted(entryId: EntryId) {
        committedPasses.computeIfAbsent(entryId) { AtomicInteger() }.incrementAndGet()
    }

    fun committedPassCount(entryId: EntryId): Int = committedPasses[entryId]?.get() ?: 0
}

/** Mirrors the production catalog by returning a fresh Screen object on every bind. */
private class FreshProbedDestinationCatalog(
    private val committedPasses: CommittedContentPassRegistry,
) : DestinationCatalog {
    private val resolutions = ConcurrentHashMap<EntryId, AtomicInteger>()
    private val observableInputs = ConcurrentHashMap<EntryId, MutableState<Int>>()
    private val explicitInputs = ConcurrentHashMap<EntryId, Int>()

    override fun resolve(entry: BackStackEntry): DestinationBinding {
        resolutions.computeIfAbsent(entry.id) { AtomicInteger() }.incrementAndGet()
        return when (entry.route) {
            is ContentRoute -> {
                val explicitInput = explicitInput(entry.id)
                DestinationBinding.Content(
                    screen = CommittedPassScreen(
                        entry = entry,
                        committedPasses = committedPasses,
                        observableInput = observableInput(entry.id),
                        explicitInput = explicitInput,
                    ),
                    parentInputsKey = explicitInput,
                )
            }

            else -> DestinationBinding.Unsupported(entry)
        }
    }

    fun resolutionCount(entryId: EntryId): Int = resolutions[entryId]?.get() ?: 0

    fun updateObservableInput(entryId: EntryId, value: Int) {
        observableInput(entryId).value = value
    }

    fun updateExplicitInput(entryId: EntryId, value: Int) {
        explicitInputs[entryId] = value
    }

    private fun observableInput(entryId: EntryId): MutableState<Int> =
        observableInputs.computeIfAbsent(entryId) { mutableStateOf(0) }

    private fun explicitInput(entryId: EntryId): Int = explicitInputs[entryId] ?: 0
}

private class CommittedPassScreen(
    override val entry: BackStackEntry,
    private val committedPasses: CommittedContentPassRegistry,
    private val observableInput: State<Int>,
    private val explicitInput: Int,
) : Screen(entry) {
    @Composable
    override fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        var value by rememberSaveable { mutableStateOf(0) }
        val input = observableInput.value
        SideEffect {
            committedPasses.recordCommitted(entry.id)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(contentTag(entry.id)),
        ) {
            Text(
                text = value.toString(),
                modifier = Modifier.testTag(valueTag(entry.id, value)),
            )
            Text(
                text = "Input $input",
                modifier = Modifier.testTag(observableInputTag(entry.id, input)),
            )
            Text(
                text = "Explicit $explicitInput",
                modifier = Modifier.testTag(explicitInputTag(entry.id, explicitInput)),
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
