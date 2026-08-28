package com.shmakov.udf.composable.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shmakov.udf.R
import com.shmakov.udf.TabNavigationFrame
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
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
import com.shmakov.udf.navigation.NavigationLayoutPolicy
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.Screen
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.navigation.TabId
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabReducer
import com.shmakov.udf.navigation.TabReduction
import com.shmakov.udf.navigation.TabState
import com.shmakov.udf.navigation.TabUnchangedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class DemoTabNavigationRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var controller: TabFixtureController
    private lateinit var destinationCatalog: TabProbeDestinationCatalog
    private lateinit var committedPasses: TabCommittedPassRegistry

    @Before
    fun setUp() {
        composeRule.mainClock.autoAdvance = false
        committedPasses = TabCommittedPassRegistry()
        destinationCatalog = TabProbeDestinationCatalog(committedPasses)
    }

    @Test
    fun switchABAPreservesIndependentHistoriesEntryIdsAndSaveableValues() {
        val aRoot = entry("aba-a-root", Home)
        val aLeaf = entry("aba-a-leaf", Accounts)
        val bRoot = entry("aba-b-root", Home)
        val bLeaf = entry("aba-b-leaf", Accounts)
        val initial = graph(
            selected = firstTab,
            tab(firstTab, aRoot, aLeaf),
            tab(secondTab, bRoot, bLeaf),
        )
        launch(initial, singlePane)

        increment(aLeaf.id, expectedValue = 1)
        select(secondTab)
        awaitValue(bLeaf.id, 0)
        increment(bLeaf.id, expectedValue = 1)
        increment(bLeaf.id, expectedValue = 2)

        select(firstTab)
        awaitValue(aLeaf.id, 1)
        composeRule.onNodeWithTag(contentTag(bLeaf.id), true).assertDoesNotExist()
        assertEquals(
            listOf(aRoot.id, aLeaf.id),
            currentTab(firstTab).history.entries.map { it.id },
        )
        assertEquals(
            listOf(bRoot.id, bLeaf.id),
            currentTab(secondTab).history.entries.map { it.id },
        )

        select(secondTab)
        awaitValue(bLeaf.id, 2)
        composeRule.onNodeWithTag(contentTag(aLeaf.id), true).assertDoesNotExist()
    }

    @Test
    fun journeyEvidenceReturnsToTheFirstNonTrivialHistoryWithItsValue() {
        val firstRoot = entry("evidence-first-root", Home)
        val firstLeaf = entry("evidence-first-accounts", Accounts)
        val secondRoot = entry("evidence-second-root", Home)
        val secondLeaf = entry("evidence-second-cards", Cards)
        launch(
            graph(
                selected = firstTab,
                tab(firstTab, firstRoot, firstLeaf),
                tab(secondTab, secondRoot, secondLeaf),
            ),
            nestedPane,
        )
        increment(firstLeaf.id, 1)
        select(secondTab)
        increment(secondLeaf.id, 1)
        increment(secondLeaf.id, 2)
        select(firstTab)
        awaitValue(firstLeaf.id, 1)
        composeRule.onNodeWithTag(demoTabItemTag(firstTab), true).assertIsSelected()
        composeRule.onNodeWithTag(demoTabItemTag(secondTab), true).assertIsNotSelected()

        val evidenceName = InstrumentationRegistry.getArguments()
            .getString("tabEvidenceFile")
            ?: return
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(demoTabItemTag(firstTab), true).assertIsSelected()
        composeRule.onNodeWithTag(demoTabItemTag(secondTab), true).assertIsNotSelected()
        composeRule.onNodeWithTag(valueTag(firstLeaf.id, 1), true)
            .assertTextEquals("1")
        val evidenceFile = checkNotNull(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        ).resolve(evidenceName)
        FileOutputStream(evidenceFile).use { output ->
            composeRule.onRoot(useUnmergedTree = true)
                .captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    @Test
    fun tabTapDispatchesExactSelectAndReselectRemainsReducerOwned() {
        val firstRoot = entry("tap-first", Home)
        val secondRoot = entry("tap-second", Home)
        launch(
            graph(
                selected = firstTab,
                tab(firstTab, firstRoot),
                tab(secondTab, secondRoot),
            ),
            singlePane,
        )
        val initialFrame = controller.frame.value

        composeRule.onNodeWithTag(demoTabItemTag(firstTab), true).performClick()

        assertEquals(TabAction.Select(firstTab), controller.lastAction)
        assertSame(initialFrame, controller.frame.value)
        assertEquals(
            TabUnchangedReason.AlreadySelected(firstTab),
            (controller.lastReduction as TabReduction.Unchanged).reason,
        )

        select(secondTab)
        assertEquals(TabAction.Select(secondTab), controller.lastAction)
        assertTrue(controller.lastReduction is TabReduction.Changed)
    }

    @Test
    fun outgoingPhysicalCallbackCannotMutateNewlySelectedTabOrReplacementGraph() {
        val aRoot = entry("stale-a-root", Home)
        val aLeaf = entry("stale-a-leaf", Accounts)
        val bRoot = entry("stale-b-root", Home)
        val initial = graph(
            selected = firstTab,
            tab(firstTab, aRoot, aLeaf),
            tab(secondTab, bRoot),
        )
        launch(initial, singlePane)
        val outgoingCallback = destinationCatalog.callback(aLeaf.id)

        select(secondTab)
        val switchedFrame = controller.frame.value
        composeRule.runOnIdle { outgoingCallback(NavAction.Pop) }

        val staleAfterSwitch = controller.lastReduction as TabReduction.Unchanged
        assertSame(switchedFrame, controller.frame.value)
        assertEquals(
            TabUnchangedReason.SelectedTabChanged(firstTab, secondTab),
            staleAfterSwitch.reason,
        )
        assertEquals(2, currentTab(firstTab).history.entries.size)

        val replacementRoot = entry("stale-replacement-root", Home)
        val replacement = graph(
            selected = secondTab,
            tab(secondTab, replacementRoot),
        )
        composeRule.runOnIdle { controller.dispatch(TabAction.replaceGraph(replacement)) }
        awaitContent(replacementRoot.id)
        val replacedFrame = controller.frame.value
        composeRule.runOnIdle { outgoingCallback(NavAction.Pop) }

        val staleAfterReplacement = controller.lastReduction as TabReduction.Unchanged
        assertSame(replacedFrame, controller.frame.value)
        assertEquals(
            TabUnchangedReason.TabNotFound(firstTab),
            staleAfterReplacement.reason,
        )
    }

    @Test
    fun outgoingPhysicalCallbackAfterSameTabReplaceGraphKeepsExactOldTopOrigin() {
        val oldRoot = entry("same-tab-stale-root", Home)
        val oldTop = entry("same-tab-stale-top", Accounts)
        val otherRoot = entry("same-tab-stale-other", Home)
        launch(
            graph(
                selected = firstTab,
                tab(firstTab, oldRoot, oldTop),
                tab(secondTab, otherRoot),
            ),
            singlePane,
        )
        val outgoingCallback = destinationCatalog.callback(oldTop.id)
        val replacementTop = entry("same-tab-replacement-top", Cards)
        val replacement = graph(
            selected = firstTab,
            tab(firstTab, oldRoot, replacementTop),
            tab(secondTab, otherRoot),
        )
        composeRule.runOnIdle { controller.dispatch(TabAction.replaceGraph(replacement)) }
        awaitContent(replacementTop.id)
        val replacedFrame = controller.frame.value

        composeRule.runOnIdle { outgoingCallback(NavAction.Pop) }

        assertEquals(
            TabAction.NavigateInSelectedTab(
                expectedSelectedTabId = firstTab,
                expectedTopId = oldTop.id,
                action = NavAction.Pop,
            ),
            controller.lastAction,
        )
        assertSame(replacedFrame, controller.frame.value)
        assertEquals(
            TabUnchangedReason.TopEntryChanged(
                tabId = firstTab,
                expectedTopId = oldTop.id,
                actualTopId = replacementTop.id,
            ),
            (controller.lastReduction as TabReduction.Unchanged).reason,
        )
    }

    @Test
    fun tabSwitchDropsOutgoingModalWithoutTurningItIntoAnExitPresentation() {
        val firstRoot = entry("modal-first-root", Home)
        val modal = entry("modal-first-sheet", Account(7))
        val secondRoot = entry("modal-second-root", Home)
        launch(
            graph(
                selected = firstTab,
                tab(firstTab, firstRoot, modal),
                tab(secondTab, secondRoot),
            ),
            singlePane,
        )
        composeRule.onNodeWithTag(modalTag(modal.id, ModalScreenState.Shown), true)
            .assertExists()

        composeRule.onNodeWithTag(demoTabItemTag(secondTab), true).performClick()

        var settled = false
        run settle@{
            repeat(MAX_APPLY_FRAMES) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
                composeRule.onNodeWithTag(modalTag(modal.id, ModalScreenState.Hidden), true)
                    .assertDoesNotExist()
                if (
                    hasContent(secondRoot.id) &&
                    composeRule.onAllNodesWithTag(
                        modalTag(modal.id, ModalScreenState.Shown),
                        true,
                    ).fetchSemanticsNodes().isEmpty()
                ) {
                    settled = true
                    return@settle
                }
            }
        }
        assertTrue("Tab switch did not dispose the outgoing modal branch", settled)
        val committedStates = destinationCatalog.modalCommittedStates(modal.id)
        assertTrue(
            "The initially shown modal never committed",
            committedStates.any { state -> state.targetState == ModalScreenState.Shown },
        )
        assertTrue(
            "Tab switch committed an outgoing Hidden modal: $committedStates",
            committedStates.none { state -> state.targetState == ModalScreenState.Hidden },
        )
    }

    @Test
    fun exitingModalCallbackKeepsThePhysicalModalTopOriginAfterDismiss() {
        val root = entry("exiting-origin-root", Home)
        val modal = entry("exiting-origin-modal", Account(8))
        val otherRoot = entry("exiting-origin-other", Home)
        launch(
            graph(
                selected = firstTab,
                tab(firstTab, root, modal),
                tab(secondTab, otherRoot),
            ),
            singlePane,
        )
        val shownCallback = destinationCatalog.modalCallback(
            entryId = modal.id,
            state = ModalScreenState.Shown,
        )

        composeRule.runOnIdle {
            shownCallback(NavAction.dismissModal(modal.id))
        }
        advanceFramesUntil("modal ${modal.id.value} exiting") {
            composeRule.onAllNodesWithTag(
                modalTag(modal.id, ModalScreenState.Hidden),
                true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        val exitingCallback = destinationCatalog.modalCallback(
            entryId = modal.id,
            state = ModalScreenState.Hidden,
        )
        val dismissedFrame = controller.frame.value

        composeRule.runOnIdle { exitingCallback(NavAction.Pop) }

        assertEquals(
            TabAction.NavigateInSelectedTab(
                expectedSelectedTabId = firstTab,
                expectedTopId = modal.id,
                action = NavAction.Pop,
            ),
            controller.lastAction,
        )
        assertSame(dismissedFrame, controller.frame.value)
        assertEquals(
            TabUnchangedReason.TopEntryChanged(
                tabId = firstTab,
                expectedTopId = modal.id,
                actualTopId = root.id,
            ),
            (controller.lastReduction as TabReduction.Unchanged).reason,
        )
    }

    @Test
    fun leafPushDoesNotCommitTabBarOrStableParentButCommitsIncomingLeaf() {
        val parent = entry("leaf-pass-parent", Home)
        val inactive = entry("leaf-pass-inactive", Home)
        launch(
            graph(
                selected = firstTab,
                tab(firstTab, parent),
                tab(secondTab, inactive),
            ),
            nestedPane,
        )
        val incoming = entry("leaf-pass-incoming", Accounts)
        val action = TabAction.navigateInSelectedTab(
            expectedSelectedTabId = firstTab,
            expectedTopId = parent.id,
            action = NavAction.push(parent.id, incoming),
        )
        val tabBarBaseline = committedPasses.component(DemoTabNavigationComponent.TabBar)
        val parentBaseline = committedPasses.entry(parent.id)
        val inactiveBaseline = committedPasses.entry(inactive.id)

        composeRule.runOnIdle { controller.dispatch(action) }
        awaitContent(incoming.id)

        assertEquals(action, controller.lastAction)
        assertTrue(controller.lastReduction is TabReduction.Changed)
        assertEquals(
            listOf(parent.id, incoming.id),
            currentTab(firstTab).history.entries.map { entry -> entry.id },
        )
        assertEquals(tabBarBaseline, committedPasses.component(DemoTabNavigationComponent.TabBar))
        assertEquals(parentBaseline, committedPasses.entry(parent.id))
        assertEquals(inactiveBaseline, committedPasses.entry(inactive.id))
        assertTrue(committedPasses.entry(incoming.id) > 0)
    }

    @Test
    fun sameTabPushKeepsOutgoingBranchWhileSwitchAndGraphReplacementSnap() {
        val firstRoot = entry("motion-first-root", Home)
        val secondRoot = entry("motion-second-root", Home)
        launch(
            graph(
                selected = firstTab,
                tab(firstTab, firstRoot),
                tab(secondTab, secondRoot),
            ),
            singlePane,
        )
        val firstLeaf = entry("motion-first-leaf", Accounts)
        composeRule.runOnIdle {
            controller.dispatch(
                TabAction.navigateInSelectedTab(
                    expectedSelectedTabId = firstTab,
                    expectedTopId = firstRoot.id,
                    action = NavAction.push(firstRoot.id, firstLeaf),
                ),
            )
        }
        val pushCoexistenceFrames = coexistenceFramesUntilOutgoingGone(
            description = "same-tab push",
            incomingEntryId = firstLeaf.id,
            outgoingEntryId = firstRoot.id,
            mustCoexistAfterFirstFrame = true,
        )

        composeRule.runOnIdle { controller.dispatch(TabAction.select(secondTab)) }
        val selectCoexistenceFrames = coexistenceFramesUntilOutgoingGone(
            description = "tab selection snap",
            incomingEntryId = secondRoot.id,
            outgoingEntryId = firstLeaf.id,
        )

        val replacementRoot = entry("motion-replacement-root", Cards)
        val replacement = graph(
            selected = secondTab,
            tab(firstTab, firstRoot, firstLeaf),
            tab(secondTab, replacementRoot),
        )
        composeRule.runOnIdle { controller.dispatch(TabAction.replaceGraph(replacement)) }
        val replaceCoexistenceFrames = coexistenceFramesUntilOutgoingGone(
            description = "graph replacement snap",
            incomingEntryId = replacementRoot.id,
            outgoingEntryId = secondRoot.id,
        )

        assertTrue(
            "Select must settle faster than Push: select=$selectCoexistenceFrames, " +
                "push=$pushCoexistenceFrames",
            selectCoexistenceFrames < pushCoexistenceFrames,
        )
        assertTrue(
            "ReplaceGraph must settle faster than Push: replace=$replaceCoexistenceFrames, " +
                "push=$pushCoexistenceFrames",
            replaceCoexistenceFrames < pushCoexistenceFrames,
        )
    }

    @Test
    fun nestedLocalChangeDoesNotCommitShellTabBarStableParentOrInactiveTab() {
        val parent = entry("passes-parent", Home)
        val nested = entry("passes-nested", Accounts)
        val inactive = entry("passes-inactive", Home)
        launch(
            graph(
                selected = firstTab,
                tab(firstTab, parent, nested),
                tab(secondTab, inactive),
            ),
            nestedPane,
        )

        val shellBaseline = committedPasses.component(DemoTabNavigationComponent.Shell)
        val tabBarBaseline = committedPasses.component(DemoTabNavigationComponent.TabBar)
        val parentBaseline = committedPasses.entry(parent.id)
        val nestedBaseline = committedPasses.entry(nested.id)
        val inactiveBaseline = committedPasses.entry(inactive.id)

        increment(nested.id, expectedValue = 1)

        assertEquals(shellBaseline, committedPasses.component(DemoTabNavigationComponent.Shell))
        assertEquals(tabBarBaseline, committedPasses.component(DemoTabNavigationComponent.TabBar))
        assertEquals(parentBaseline, committedPasses.entry(parent.id))
        assertEquals(inactiveBaseline, committedPasses.entry(inactive.id))
        assertTrue(committedPasses.entry(nested.id) > nestedBaseline)

        select(secondTab)
        composeRule.onNodeWithTag(demoTabItemTag(firstTab), true).assertIsNotSelected()
        composeRule.onNodeWithTag(demoTabItemTag(secondTab), true).assertIsSelected()
        assertTrue(
            committedPasses.component(DemoTabNavigationComponent.Shell) > shellBaseline,
        )
        assertTrue(
            committedPasses.component(DemoTabNavigationComponent.TabBar) > tabBarBaseline,
        )
    }

    @Test
    fun leafFrameWithChangingCallerLambdaSkipsTabBarWhileIncomingLeafCommits() {
        val root = entry("leaf-frame-root", Home)
        val inactive = entry("leaf-frame-inactive", Home)
        launch(
            initialState = graph(
                selected = firstTab,
                tab(firstTab, root),
                tab(secondTab, inactive),
            ),
            layoutPolicy = singlePane,
            stableDispatch = false,
        )
        val tabBarBaseline = committedPasses.component(DemoTabNavigationComponent.TabBar)
        val incoming = entry("leaf-frame-incoming", Accounts)

        composeRule.runOnIdle {
            controller.dispatch(
                TabAction.navigateInSelectedTab(
                    expectedSelectedTabId = firstTab,
                    expectedTopId = root.id,
                    action = NavAction.push(root.id, incoming),
                ),
            )
        }
        awaitContent(incoming.id)

        assertTrue("Incoming leaf never committed", committedPasses.entry(incoming.id) > 0)
        assertEquals(
            "Leaf frame recomposed the tab bar through a changing caller lambda",
            tabBarBaseline,
            committedPasses.component(DemoTabNavigationComponent.TabBar),
        )
    }

    @Test
    fun replaceGraphRetiresOnlyRemovedBucketsAndFreshOccurrenceStartsAtDefault() {
        val oldA = entry("cleanup-old-a", Home)
        val retainedB = entry("cleanup-retained-b", Home)
        launch(
            graph(
                selected = firstTab,
                tab(firstTab, oldA),
                tab(secondTab, retainedB),
            ),
            singlePane,
        )
        increment(oldA.id, 1)
        select(secondTab)
        increment(retainedB.id, 1)
        increment(retainedB.id, 2)

        val onlyB = graph(selected = secondTab, tab(secondTab, retainedB))
        composeRule.runOnIdle { controller.dispatch(TabAction.replaceGraph(onlyB)) }
        awaitValue(retainedB.id, 2)
        composeRule.onNodeWithTag(contentTag(oldA.id), true).assertDoesNotExist()

        // A real fresh occurrence must use a fresh ID. Reusing the retired ID is deliberately a
        // black-box probe that the graph-wide retention ledger removed its saveable bucket.
        val cleanupProbe = BackStackEntry(oldA.id, Home)
        val withFreshA = graph(
            selected = firstTab,
            tab(secondTab, retainedB),
            tab(firstTab, cleanupProbe),
        )
        composeRule.runOnIdle { controller.dispatch(TabAction.replaceGraph(withFreshA)) }
        awaitValue(cleanupProbe.id, 0)

        select(secondTab)
        awaitValue(retainedB.id, 2)
    }

    @Test
    fun missingTabUiFailsBeforeAnyTabItemOrDestinationComposable() {
        val root = entry("missing-ui-root", Home)
        val missing = entry("missing-ui-other", Home)
        controller = TabFixtureController(
            graph(
                selected = firstTab,
                tab(firstTab, root),
                tab(secondTab, missing),
            ),
        )
        composeRule.setContent {
            DemoTabNavigation(
                frame = controller.frame.value,
                tabUiById = mapOf(firstTab to tabUi),
                layoutPolicy = singlePane,
                destinationCatalog = destinationCatalog,
                onAction = controller::dispatch,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DEMO_TAB_FAILURE_TAG, true)
            .assertTextEquals("Missing tab UI configuration: second")
        composeRule.onNodeWithTag(DEMO_TAB_BAR_TAG, true).assertDoesNotExist()
        composeRule.onNodeWithTag(contentTag(root.id), true).assertDoesNotExist()
        assertEquals(0, destinationCatalog.resolutionCount())
    }

    private fun launch(
        initialState: TabNavigationState,
        layoutPolicy: NavigationLayoutPolicy,
        stableDispatch: Boolean = true,
    ) {
        controller = TabFixtureController(initialState)
        composeRule.setContent {
            val actionSink: (TabAction) -> Unit = if (stableDispatch) {
                remember(controller) {
                    { action: TabAction -> controller.dispatch(action) }
                }
            } else {
                { action -> controller.dispatch(action) }
            }
            CompositionLocalProvider(
                LocalDemoTabNavigationDiagnostics provides committedPasses,
            ) {
                DemoTabNavigation(
                    frame = controller.frame.value,
                    tabUiById = mapOf(firstTab to firstTabUi, secondTab to secondTabUi),
                    layoutPolicy = layoutPolicy,
                    destinationCatalog = destinationCatalog,
                    onAction = actionSink,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DEMO_TAB_SHELL_TAG, true).assertExists()
    }

    private fun select(tabId: TabId) {
        composeRule.onNodeWithTag(demoTabItemTag(tabId), true).performClick()
        advanceFramesUntil("selected tab ${tabId.value}") {
            controller.frame.value.state.selectedTabId == tabId &&
                hasContent(controller.frame.value.state.selectedTab.history.top.id)
        }
    }

    private fun increment(entryId: EntryId, expectedValue: Int) {
        composeRule.onNodeWithTag(incrementTag(entryId), true).performClick()
        awaitValue(entryId, expectedValue)
    }

    private fun awaitValue(entryId: EntryId, expectedValue: Int) {
        advanceFramesUntil("entry ${entryId.value} value $expectedValue") {
            composeRule.onAllNodesWithTag(valueTag(entryId, expectedValue), true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(valueTag(entryId, expectedValue), true)
            .assertTextEquals(expectedValue.toString())
    }

    private fun awaitContent(entryId: EntryId) {
        advanceFramesUntil("entry ${entryId.value} content") { hasContent(entryId) }
    }

    private fun currentTab(tabId: TabId): TabState =
        checkNotNull(controller.frame.value.state[tabId]) {
            "Expected tab ${tabId.value} in current fixture graph"
        }

    private fun coexistenceFramesUntilOutgoingGone(
        description: String,
        incomingEntryId: EntryId,
        outgoingEntryId: EntryId,
        mustCoexistAfterFirstFrame: Boolean = false,
    ): Int {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(contentTag(incomingEntryId), true).assertExists()
        if (mustCoexistAfterFirstFrame) {
            composeRule.onNodeWithTag(contentTag(outgoingEntryId), true).assertExists()
        }

        var frames = 1
        while (hasContent(outgoingEntryId) && frames < MAX_APPLY_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            frames += 1
        }
        if (hasContent(outgoingEntryId)) {
            throw AssertionError("Timed out waiting for $description outgoing content to disappear")
        }
        return frames
    }

    private fun hasContent(entryId: EntryId): Boolean =
        composeRule.onAllNodesWithTag(contentTag(entryId), true)
            .fetchSemanticsNodes().isNotEmpty()

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

    private fun graph(
        selected: TabId,
        vararg tabs: TabState,
    ): TabNavigationState = TabNavigationState.create(selected, tabs.toList())

    private fun tab(id: TabId, vararg entries: BackStackEntry): TabState = TabState(
        id = id,
        history = when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> error("Invalid fixture: ${result.problems}")
        },
    )

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private companion object {
        const val MAX_APPLY_FRAMES = 180

        val firstTab = TabId("first")
        val secondTab = TabId("second")
        val tabUi = DemoTabUi(R.string.demo_tab_accounts, Icons.Default.Home)
        val firstTabUi = DemoTabUi(R.string.demo_tab_accounts, Icons.Default.Home)
        val secondTabUi = DemoTabUi(R.string.demo_tab_cards, Icons.Default.Home)

        val singlePane = NavigationLayoutPolicy { ContentPlacementDecision.root() }
        val nestedPane = NavigationLayoutPolicy { request ->
            ContentPlacementDecision.childOf(request.currentContent.entry.id)
        }
    }
}

private class TabFixtureController(initialState: TabNavigationState) {
    val frame = mutableStateOf(TabNavigationFrame(initialState, revision = 0L, transition = null))
    var lastAction: TabAction? = null
        private set
    var lastReduction: TabReduction? = null
        private set

    fun dispatch(action: TabAction) {
        lastAction = action
        val current = frame.value
        when (val reduction = TabReducer.reduce(current.state, action)) {
            is TabReduction.Changed -> {
                lastReduction = reduction
                frame.value = TabNavigationFrame(
                    state = reduction.state,
                    revision = current.revision + 1L,
                    transition = reduction.transition,
                )
            }

            is TabReduction.Unchanged -> lastReduction = reduction
        }
    }
}

private class TabCommittedPassRegistry : DemoTabNavigationDiagnostics {
    private val componentPasses = ConcurrentHashMap<DemoTabNavigationComponent, AtomicInteger>()
    private val entryPasses = ConcurrentHashMap<EntryId, AtomicInteger>()

    override fun onCommitted(component: DemoTabNavigationComponent) {
        componentPasses.computeIfAbsent(component) { AtomicInteger() }.incrementAndGet()
    }

    fun record(entryId: EntryId) {
        entryPasses.computeIfAbsent(entryId) { AtomicInteger() }.incrementAndGet()
    }

    fun component(component: DemoTabNavigationComponent): Int =
        componentPasses[component]?.get() ?: 0

    fun entry(entryId: EntryId): Int = entryPasses[entryId]?.get() ?: 0
}

private class TabProbeDestinationCatalog(
    private val committedPasses: TabCommittedPassRegistry,
) : DestinationCatalog {
    private val callbacks = ConcurrentHashMap<EntryId, (NavAction) -> Unit>()
    private val modalCallbacks =
        ConcurrentHashMap<TabModalCallbackKey, (NavAction) -> Unit>()
    private val modalCommittedStates =
        ConcurrentHashMap<EntryId, CopyOnWriteArrayList<TabModalCommittedState>>()
    private val resolutions = AtomicInteger()

    override fun resolve(entry: BackStackEntry): DestinationBinding {
        resolutions.incrementAndGet()
        return when (entry.route) {
            is ContentRoute -> DestinationBinding.Content(
                screen = TabProbeScreen(entry, committedPasses, callbacks),
                parentInputsKey = Unit,
            )

            is ModalRoute -> DestinationBinding.Modal(
                TabProbeModalScreen(
                    entry = entry,
                    onCommitted = { state, entrance, callback ->
                        modalCommittedStates.computeIfAbsent(entry.id) {
                            CopyOnWriteArrayList()
                        }.add(TabModalCommittedState(state, entrance))
                        modalCallbacks[TabModalCallbackKey(entry.id, state)] = callback
                    },
                ),
            )
            else -> DestinationBinding.Unsupported(entry)
        }
    }

    fun callback(entryId: EntryId): (NavAction) -> Unit =
        checkNotNull(callbacks[entryId]) { "No callback captured for $entryId" }

    fun modalCallback(
        entryId: EntryId,
        state: ModalScreenState,
    ): (NavAction) -> Unit = checkNotNull(modalCallbacks[TabModalCallbackKey(entryId, state)]) {
        "No $state modal callback captured for $entryId"
    }

    fun modalCommittedStates(entryId: EntryId): List<TabModalCommittedState> =
        modalCommittedStates[entryId]?.toList().orEmpty()

    fun resolutionCount(): Int = resolutions.get()
}

private class TabProbeModalScreen(
    override val entry: BackStackEntry,
    private val onCommitted: (
        ModalScreenState,
        ModalEntrance,
        (NavAction) -> Unit,
    ) -> Unit,
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
            onCommitted(targetState, entrance, onNavigationAction)
        }
        Text(
            text = "${entry.id.value}:$targetState:$entrance",
            modifier = Modifier.testTag(modalTag(entry.id, targetState)),
        )
    }
}

private data class TabModalCallbackKey(
    val entryId: EntryId,
    val state: ModalScreenState,
)

private data class TabModalCommittedState(
    val targetState: ModalScreenState,
    val entrance: ModalEntrance,
)

private class TabProbeScreen(
    override val entry: BackStackEntry,
    private val committedPasses: TabCommittedPassRegistry,
    private val callbacks: ConcurrentHashMap<EntryId, (NavAction) -> Unit>,
) : Screen(entry) {
    @Composable
    override fun Content(
        childContent: @Composable () -> Unit,
        onNavigationAction: (NavAction) -> Unit,
    ) {
        var value by rememberSaveable { mutableStateOf(0) }
        SideEffect {
            committedPasses.record(entry.id)
            callbacks[entry.id] = onNavigationAction
        }
        Column(
            modifier = Modifier.fillMaxSize().testTag(contentTag(entry.id)),
        ) {
            Text(
                text = value.toString(),
                modifier = Modifier.testTag(valueTag(entry.id, value)),
            )
            Text("${entry.route.javaClass.simpleName} · ${entry.id.value}")
            Button(
                onClick = { value += 1 },
                modifier = Modifier.testTag(incrementTag(entry.id)),
            ) {
                Text("Increment")
            }
            Box(Modifier.fillMaxSize()) { childContent() }
        }
    }
}

private fun contentTag(entryId: EntryId): String = "tab-probe:content:${entryId.value}"

private fun incrementTag(entryId: EntryId): String = "tab-probe:increment:${entryId.value}"

private fun valueTag(entryId: EntryId, value: Int): String =
    "tab-probe:value:${entryId.value}:$value"

private fun modalTag(entryId: EntryId, state: ModalScreenState): String =
    "tab-probe:modal:${entryId.value}:$state"
