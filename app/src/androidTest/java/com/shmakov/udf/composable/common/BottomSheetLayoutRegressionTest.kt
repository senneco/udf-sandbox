package com.shmakov.udf.composable.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shmakov.udf.navigation.ModalEntrance
import com.shmakov.udf.navigation.ModalScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class BottomSheetLayoutRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialShownSnapIsExactlyExpandedWithoutAdvancingTheAnimationClock() {
        composeRule.mainClock.autoAdvance = false
        var rootHeightPx = 0f
        var sheetHeightPx = 0
        var sheetTopPx = Float.POSITIVE_INFINITY
        var dismissRequestCount = 0
        var exitFinishedCount = 0

        composeRule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        rootHeightPx = coordinates.size.height.toFloat()
                    },
            ) {
                BottomSheetLayout(
                    targetState = ModalScreenState.Shown,
                    entrance = ModalEntrance.Snap,
                    onDismissRequest = { dismissRequestCount += 1 },
                    onExitFinished = { exitFinishedCount += 1 },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag(SHEET_TAG)
                            .onGloballyPositioned { coordinates ->
                                sheetHeightPx = coordinates.size.height
                                sheetTopPx = coordinates.positionInRoot().y
                            },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue(
            "Snap bootstrap was not exactly Expanded without clock advancement: " +
                "root=$rootHeightPx, height=$sheetHeightPx, top=$sheetTopPx",
            isExactlyExpanded(rootHeightPx, sheetHeightPx, sheetTopPx),
        )
        assertEquals(0, dismissRequestCount)
        assertEquals(0, exitFinishedCount)
    }

    @Test
    fun shownAnimateStartsCollapsedAndExpandsOnlyAsTheClockAdvances() {
        composeRule.mainClock.autoAdvance = false
        var rootHeightPx = 0f
        var sheetHeightPx = 0
        var sheetTopPx = Float.POSITIVE_INFINITY

        composeRule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        rootHeightPx = coordinates.size.height.toFloat()
                    },
            ) {
                BottomSheetLayout(
                    targetState = ModalScreenState.Shown,
                    entrance = ModalEntrance.Animate,
                    onDismissRequest = {},
                    onExitFinished = {},
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag(SHEET_TAG)
                            .onGloballyPositioned { coordinates ->
                                sheetHeightPx = coordinates.size.height
                                sheetTopPx = coordinates.positionInRoot().y
                            },
                    )
                }
            }
        }
        composeRule.onNodeWithTag(SHEET_TAG, useUnmergedTree = true).assertExists()

        assertTrue(rootHeightPx > 0f && sheetHeightPx > 0)
        assertTrue(
            "Animate entrance unexpectedly snapped directly to Expanded",
            !isExactlyExpanded(rootHeightPx, sheetHeightPx, sheetTopPx),
        )
        assertTrue(
            "Animate entrance never reached Expanded after clock advancement",
            advanceFramesUntil {
                isExactlyExpanded(rootHeightPx, sheetHeightPx, sheetTopPx)
            },
        )
    }

    @Test
    fun shownSnapReentryInterruptsExitAndIsExactlyExpandedWithoutSettlingFrames() {
        composeRule.mainClock.autoAdvance = false
        val presentation = mutableStateOf(
            BottomSheetTestPresentation(
                targetState = ModalScreenState.Shown,
                entrance = ModalEntrance.Animate,
            ),
        )
        var rootHeightPx = 0f
        var sheetHeightPx = 0
        var sheetTopPx = Float.POSITIVE_INFINITY
        var dismissRequestCount = 0
        var exitFinishedCount = 0

        composeRule.setContent {
            val current = presentation.value
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        rootHeightPx = coordinates.size.height.toFloat()
                    },
            ) {
                BottomSheetLayout(
                    targetState = current.targetState,
                    entrance = current.entrance,
                    onDismissRequest = { dismissRequestCount += 1 },
                    onExitFinished = { exitFinishedCount += 1 },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag(SHEET_TAG)
                            .onGloballyPositioned { coordinates ->
                                sheetHeightPx = coordinates.size.height
                                sheetTopPx = coordinates.positionInRoot().y
                            },
                    )
                }
            }
        }
        assertTrue(
            "Initial Animate presentation never reached exact Expanded geometry",
            advanceFramesUntil {
                isExactlyExpanded(rootHeightPx, sheetHeightPx, sheetTopPx)
            },
        )
        composeRule.waitForIdle()
        val acceptedExpandedTopPx = sheetTopPx

        composeRule.runOnIdle {
            presentation.value = BottomSheetTestPresentation(
                targetState = ModalScreenState.Hidden,
                entrance = ModalEntrance.Snap,
            )
        }
        // One frame applies Exiting; two more begin its collapse without reaching completion.
        repeat(3) {
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.waitForIdle()

        assertTrue(
            "Hidden presentation did not begin its exit before re-entry",
            hasDepartedFrom(
                acceptedExpandedTopPx = acceptedExpandedTopPx,
                sheetHeightPx = sheetHeightPx,
                sheetTopPx = sheetTopPx,
                rootHeightPx = rootHeightPx,
            ),
        )
        assertEquals(0, dismissRequestCount)
        assertEquals(0, exitFinishedCount)

        composeRule.runOnIdle {
            presentation.value = BottomSheetTestPresentation(
                targetState = ModalScreenState.Shown,
                entrance = ModalEntrance.Snap,
            )
        }
        // Apply the replacement composition only. Snap must not need animation/settling frames.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertTrue(
            "Shown+Snap re-entry was not exact Expanded after its application frame: " +
                "root=$rootHeightPx, height=$sheetHeightPx, top=$sheetTopPx",
            isExactlyExpanded(rootHeightPx, sheetHeightPx, sheetTopPx),
        )
        assertEquals(0, dismissRequestCount)
        assertEquals(0, exitFinishedCount)
    }

    @Test
    fun initialHiddenSheetCompletesExactlyOnceWithoutDismissRequest() {
        val recomposition = mutableStateOf(0)
        var dismissRequestCount = 0
        var exitFinishedCount = 0

        composeRule.setContent {
            @Suppress("UNUSED_EXPRESSION")
            recomposition.value
            BottomSheetLayout(
                targetState = ModalScreenState.Hidden,
                entrance = ModalEntrance.Snap,
                onDismissRequest = { dismissRequestCount += 1 },
                onExitFinished = { exitFinishedCount += 1 },
                content = {},
            )
        }
        composeRule.waitForIdle()

        assertEquals(0, dismissRequestCount)
        assertEquals(1, exitFinishedCount)

        composeRule.runOnIdle {
            recomposition.value += 1
        }
        composeRule.waitForIdle()

        assertEquals(0, dismissRequestCount)
        assertEquals(1, exitFinishedCount)
    }

    @Test
    fun shownThenHiddenBeforeFirstFrameCompletesInvisibleSheetOnce() {
        composeRule.mainClock.autoAdvance = false
        val targetState = mutableStateOf(ModalScreenState.Shown)
        val recomposition = mutableStateOf(0)
        var dismissRequestCount = 0
        var exitFinishedCount = 0

        composeRule.setContent {
            @Suppress("UNUSED_EXPRESSION")
            recomposition.value
            BottomSheetLayout(
                targetState = targetState.value,
                entrance = ModalEntrance.Animate,
                onDismissRequest = { dismissRequestCount += 1 },
                onExitFinished = { exitFinishedCount += 1 },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }
        }

        assertEquals(0, dismissRequestCount)
        assertEquals(0, exitFinishedCount)

        composeRule.runOnUiThread {
            targetState.value = ModalScreenState.Hidden
        }
        composeRule.mainClock.advanceTimeBy(SHEET_ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()

        assertEquals(0, dismissRequestCount)
        assertEquals(1, exitFinishedCount)

        composeRule.runOnIdle {
            recomposition.value += 1
        }
        composeRule.mainClock.advanceTimeBy(SHEET_ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()

        assertEquals(0, dismissRequestCount)
        assertEquals(1, exitFinishedCount)
    }

    @Test
    fun completedExitDoesNotSuppressNextRapidShownHiddenLifecycle() {
        composeRule.mainClock.autoAdvance = false
        val targetState = mutableStateOf(ModalScreenState.Hidden)
        var dismissRequestCount = 0
        var exitFinishedCount = 0

        composeRule.setContent {
            BottomSheetLayout(
                targetState = targetState.value,
                entrance = ModalEntrance.Animate,
                onDismissRequest = { dismissRequestCount += 1 },
                onExitFinished = { exitFinishedCount += 1 },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, exitFinishedCount)

        composeRule.runOnIdle {
            targetState.value = ModalScreenState.Shown
        }
        // Apply the Shown composition (and its phase-scoped guard) without settling the animation.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            targetState.value = ModalScreenState.Hidden
        }
        composeRule.mainClock.advanceTimeBy(SHEET_ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()

        assertEquals(0, dismissRequestCount)
        assertEquals(2, exitFinishedCount)

        composeRule.mainClock.advanceTimeBy(SHEET_ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()
        assertEquals(2, exitFinishedCount)
    }

    @Test
    fun scrimTapRequestsDurableHideOnceAndThenCompletesExitOnce() {
        composeRule.mainClock.autoAdvance = false
        val targetState = mutableStateOf(ModalScreenState.Shown)
        val mounted = mutableStateOf(true)
        var dismissRequestCount = 0
        var exitFinishedCount = 0

        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                if (mounted.value) {
                    BottomSheetLayout(
                        targetState = targetState.value,
                        entrance = ModalEntrance.Animate,
                        onDismissRequest = {
                            dismissRequestCount += 1
                            targetState.value = ModalScreenState.Hidden
                        },
                        onExitFinished = {
                            exitFinishedCount += 1
                            mounted.value = false
                        },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SHEET_ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()

        assertEquals(0, dismissRequestCount)
        assertEquals(0, exitFinishedCount)

        // The sheet is bottom-aligned and 160dp tall; the root's top-left is stable scrim space.
        composeRule.onRoot(useUnmergedTree = true).performTouchInput {
            click(Offset(x = 1f, y = 1f))
        }

        assertEquals(1, dismissRequestCount)
        assertEquals(0, exitFinishedCount)

        // The synchronous durable acknowledgement must install a Hidden phase before the old
        // Desired phase gets another corrective-expansion turn.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(1, dismissRequestCount)
        assertEquals(0, exitFinishedCount)

        composeRule.mainClock.advanceTimeUntil(MAX_VIRTUAL_TIME_MILLIS) {
            exitFinishedCount == 1
        }
        composeRule.waitForIdle()

        assertEquals(1, dismissRequestCount)
        assertEquals(1, exitFinishedCount)

        composeRule.mainClock.advanceTimeBy(SHEET_ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()
        assertEquals(1, dismissRequestCount)
        assertEquals(1, exitFinishedCount)
    }

    @Test
    fun hiddenDuringShowDoesNotCompleteBeforeItsOwnCollapseFinishes() {
        composeRule.mainClock.autoAdvance = false
        val targetState = mutableStateOf(ModalScreenState.Shown)
        var dismissRequestCount = 0
        var exitFinishedCount = 0

        composeRule.setContent {
            BottomSheetLayout(
                targetState = targetState.value,
                entrance = ModalEntrance.Animate,
                onDismissRequest = { dismissRequestCount += 1 },
                onExitFinished = { exitFinishedCount += 1 },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }
        }

        // Let the Shown phase start its expansion, but do not let it settle.
        repeat(3) {
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.waitForIdle()
        assertEquals(0, exitFinishedCount)

        composeRule.runOnUiThread {
            targetState.value = ModalScreenState.Hidden
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        // A stale collapsed snapshot from the canceled Shown mutation is not proof that the new
        // Hidden phase performed and finished its own collapse.
        assertEquals(0, dismissRequestCount)
        assertEquals(0, exitFinishedCount)

        composeRule.mainClock.advanceTimeUntil(MAX_VIRTUAL_TIME_MILLIS) {
            exitFinishedCount == 1
        }
        composeRule.waitForIdle()

        assertEquals(0, dismissRequestCount)
        assertEquals(1, exitFinishedCount)

        repeat(EXTRA_SETTLED_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.waitForIdle()
        assertEquals(1, exitFinishedCount)
    }

    @Test
    fun delayedAckScrimAndSwipeRequestDismissOncePerShownPhase() {
        composeRule.mainClock.autoAdvance = false
        val targetState = mutableStateOf(ModalScreenState.Shown)
        var dismissRequestCount = 0
        var exitFinishedCount = 0

        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                BottomSheetLayout(
                    targetState = targetState.value,
                    entrance = ModalEntrance.Animate,
                    onDismissRequest = { dismissRequestCount += 1 },
                    onExitFinished = { exitFinishedCount += 1 },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag(SHEET_TAG),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SHEET_ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()

        composeRule.onRoot(useUnmergedTree = true).performTouchInput {
            click(Offset(x = 1f, y = 1f))
        }
        assertEquals(1, dismissRequestCount)
        assertEquals(ModalScreenState.Shown, targetState.value)

        // Durable state deliberately acknowledges late. A swipe belongs to the same Shown phase
        // as the scrim tap and therefore cannot dispatch a second request.
        composeRule.onNodeWithTag(SHEET_TAG, useUnmergedTree = true).performTouchInput {
            swipeDown()
        }
        repeat(EXTRA_SETTLED_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.waitForIdle()

        assertEquals(1, dismissRequestCount)
        assertEquals(0, exitFinishedCount)
        assertEquals(ModalScreenState.Shown, targetState.value)

        composeRule.runOnUiThread {
            targetState.value = ModalScreenState.Hidden
        }
        composeRule.mainClock.advanceTimeUntil(MAX_VIRTUAL_TIME_MILLIS) {
            exitFinishedCount == 1
        }
        composeRule.waitForIdle()
        assertEquals(1, dismissRequestCount)
        assertEquals(1, exitFinishedCount)

        // A later Shown target is a new phase with a fresh request latch.
        composeRule.runOnUiThread {
            targetState.value = ModalScreenState.Shown
        }
        composeRule.mainClock.advanceTimeBy(SHEET_ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()
        composeRule.onRoot(useUnmergedTree = true).performTouchInput {
            click(Offset(x = 1f, y = 1f))
        }

        assertEquals(2, dismissRequestCount)
        assertEquals(1, exitFinishedCount)
    }

    @Test
    fun swipeAsTheOnlySourceRequestsOnceAndReconvergesWhenUnacknowledged() {
        composeRule.mainClock.autoAdvance = false
        var dismissRequestCount = 0
        var exitFinishedCount = 0
        val collapseAction = SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse)
        var rootHeightPx = 0f
        var sheetHeightPx = 0
        var sheetTopPx = Float.POSITIVE_INFINITY

        composeRule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        rootHeightPx = coordinates.size.height.toFloat()
                    },
            ) {
                BottomSheetLayout(
                    targetState = ModalScreenState.Shown,
                    entrance = ModalEntrance.Animate,
                    onDismissRequest = { dismissRequestCount += 1 },
                    onExitFinished = { exitFinishedCount += 1 },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag(SHEET_TAG)
                            .onGloballyPositioned { coordinates ->
                                sheetHeightPx = coordinates.size.height
                                sheetTopPx = coordinates.positionInRoot().y
                            },
                    )
                }
            }
        }
        val acceptedExpandedTopPx = awaitStableExpandedTop(
            description = "initial swipe sheet",
            collapseAction = collapseAction,
            rootHeightPx = { rootHeightPx },
            sheetHeightPx = { sheetHeightPx },
            sheetTopPx = { sheetTopPx },
        )

        composeRule.onNodeWithTag(SHEET_TAG, useUnmergedTree = true).performTouchInput {
            swipeDown()
        }
        assertTrue(
            "Swipe never departed from accepted Expanded geometry",
            advanceFramesUntil {
                hasDepartedFrom(acceptedExpandedTopPx, sheetHeightPx, sheetTopPx, rootHeightPx)
            },
        )
        assertTrue(advanceFramesUntil { dismissRequestCount == 1 })
        val restoredExpandedTopPx = awaitStableExpandedTop(
            description = "sheet after unacknowledged swipe",
            collapseAction = collapseAction,
            rootHeightPx = { rootHeightPx },
            sheetHeightPx = { sheetHeightPx },
            sheetTopPx = { sheetTopPx },
        )
        assertTrue(
            "Unacknowledged swipe returned to a different Expanded offset",
            abs(restoredExpandedTopPx - acceptedExpandedTopPx) < 1f,
        )

        assertEquals(1, dismissRequestCount)
        assertEquals(0, exitFinishedCount)

        composeRule.onNodeWithTag(SHEET_TAG, useUnmergedTree = true).performTouchInput {
            swipeDown()
        }
        assertTrue(
            "Repeated swipe never departed from accepted Expanded geometry",
            advanceFramesUntil {
                hasDepartedFrom(acceptedExpandedTopPx, sheetHeightPx, sheetTopPx, rootHeightPx)
            },
        )
        val repeatedRestoredTopPx = awaitStableExpandedTop(
            description = "sheet after repeated swipe",
            collapseAction = collapseAction,
            rootHeightPx = { rootHeightPx },
            sheetHeightPx = { sheetHeightPx },
            sheetTopPx = { sheetTopPx },
        )
        assertTrue(
            "Repeated swipe left the sheet outside exact accepted Expanded geometry",
            abs(repeatedRestoredTopPx - acceptedExpandedTopPx) < 1f,
        )
        assertEquals(1, dismissRequestCount)
        assertEquals(0, exitFinishedCount)
    }

    @Test
    fun accessibilityCollapseUsesTheSameOncePerPhaseRequestGate() {
        composeRule.mainClock.autoAdvance = false
        var dismissRequestCount = 0
        var exitFinishedCount = 0
        val collapseAction = SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse)
        var rootHeightPx = 0f
        var sheetHeightPx = 0
        var sheetTopPx = Float.POSITIVE_INFINITY

        composeRule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        rootHeightPx = coordinates.size.height.toFloat()
                    },
            ) {
                BottomSheetLayout(
                    targetState = ModalScreenState.Shown,
                    entrance = ModalEntrance.Animate,
                    onDismissRequest = { dismissRequestCount += 1 },
                    onExitFinished = { exitFinishedCount += 1 },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag(SHEET_TAG)
                            .onGloballyPositioned { coordinates ->
                                sheetHeightPx = coordinates.size.height
                                sheetTopPx = coordinates.positionInRoot().y
                            },
                    )
                }
            }
        }
        val acceptedExpandedTopPx = awaitStableExpandedTop(
            description = "initial accessibility sheet",
            collapseAction = collapseAction,
            rootHeightPx = { rootHeightPx },
            sheetHeightPx = { sheetHeightPx },
            sheetTopPx = { sheetTopPx },
        )

        composeRule.onAllNodes(
            collapseAction,
            useUnmergedTree = true,
        )[0].performSemanticsAction(SemanticsActions.Collapse)
        assertTrue(advanceFramesUntil { dismissRequestCount == 1 })
        val restoredExpandedTopPx = awaitStableExpandedTop(
            description = "sheet after unacknowledged accessibility collapse",
            collapseAction = collapseAction,
            rootHeightPx = { rootHeightPx },
            sheetHeightPx = { sheetHeightPx },
            sheetTopPx = { sheetTopPx },
        )
        assertTrue(
            "Accessibility collapse returned to a different Expanded offset",
            abs(restoredExpandedTopPx - acceptedExpandedTopPx) < 1f,
        )

        assertEquals(1, dismissRequestCount)
        assertEquals(0, exitFinishedCount)

        composeRule.onAllNodes(
            collapseAction,
            useUnmergedTree = true,
        )[0].performSemanticsAction(SemanticsActions.Collapse)
        composeRule.mainClock.advanceTimeByFrame()
        val repeatedRestoredTopPx = awaitStableExpandedTop(
            description = "sheet after repeated accessibility collapse",
            collapseAction = collapseAction,
            rootHeightPx = { rootHeightPx },
            sheetHeightPx = { sheetHeightPx },
            sheetTopPx = { sheetTopPx },
        )
        assertTrue(
            "Repeated accessibility collapse left stale sheet geometry",
            abs(repeatedRestoredTopPx - acceptedExpandedTopPx) < 1f,
        )
        assertEquals(1, dismissRequestCount)
        assertEquals(0, exitFinishedCount)
    }

    @Test
    fun resizeDuringHideEventuallyCompletesTheCurrentExitExactlyOnce() {
        composeRule.mainClock.autoAdvance = false
        val targetState = mutableStateOf(ModalScreenState.Shown)
        val containerHeight = mutableStateOf(520.dp)
        val recomposition = mutableStateOf(0)
        var dismissRequestCount = 0
        var exitFinishedCount = 0

        composeRule.setContent {
            @Suppress("UNUSED_EXPRESSION")
            recomposition.value
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(containerHeight.value),
            ) {
                BottomSheetLayout(
                    targetState = targetState.value,
                    entrance = ModalEntrance.Animate,
                    onDismissRequest = { dismissRequestCount += 1 },
                    onExitFinished = { exitFinishedCount += 1 },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SHEET_ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            targetState.value = ModalScreenState.Hidden
        }
        composeRule.mainClock.advanceTimeByFrame()

        // Recreate anchors while the collapse mutation is in flight. A canceled Material child
        // mutation must retry inside the same renderer phase instead of losing the completion.
        listOf(360.dp, 640.dp, 420.dp).forEach { height ->
            composeRule.runOnUiThread {
                containerHeight.value = height
            }
            composeRule.mainClock.advanceTimeByFrame()
        }

        composeRule.mainClock.advanceTimeUntil(MAX_VIRTUAL_TIME_MILLIS) {
            exitFinishedCount == 1
        }
        composeRule.waitForIdle()

        assertEquals(0, dismissRequestCount)
        assertEquals(1, exitFinishedCount)

        composeRule.runOnIdle {
            containerHeight.value = 500.dp
            recomposition.value += 1
        }
        repeat(EXTRA_SETTLED_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.waitForIdle()

        assertEquals(0, dismissRequestCount)
        assertEquals(1, exitFinishedCount)
    }

    @Test
    fun temporarySheetGeometryLossDoesNotLookLikeAUserDismiss() {
        composeRule.mainClock.autoAdvance = false
        val targetState = mutableStateOf(ModalScreenState.Shown)
        val sheetHeight = mutableStateOf(160.dp)
        var dismissRequestCount = 0
        var exitFinishedCount = 0
        var rootHeightPx = 0f
        var sheetHeightPx = 0
        var sheetTopPx = Float.POSITIVE_INFINITY

        composeRule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        rootHeightPx = coordinates.size.height.toFloat()
                    },
            ) {
                BottomSheetLayout(
                    targetState = targetState.value,
                    entrance = ModalEntrance.Animate,
                    onDismissRequest = { dismissRequestCount += 1 },
                    onExitFinished = { exitFinishedCount += 1 },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(sheetHeight.value)
                            .onGloballyPositioned { coordinates ->
                                sheetHeightPx = coordinates.size.height
                                sheetTopPx = coordinates.positionInRoot().y
                            },
                    )
                }
            }
        }
        assertTrue(
            "Sheet never reached Expanded: root=$rootHeightPx, height=$sheetHeightPx, top=$sheetTopPx",
            advanceFramesUntil {
                isContentFullyVisible(rootHeightPx, sheetHeightPx, sheetTopPx)
            },
        )
        composeRule.waitForIdle()
        val acceptedExpandedTopPx = sheetTopPx

        // Removing the sheet geometry is layout churn, not a user dismissal gesture.
        composeRule.runOnUiThread {
            sheetHeight.value = 0.dp
        }
        repeat(EXTRA_SETTLED_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.waitForIdle()

        assertEquals(0, dismissRequestCount)
        assertEquals(0, exitFinishedCount)
        assertEquals(ModalScreenState.Shown, targetState.value)

        composeRule.runOnUiThread {
            sheetHeight.value = 160.dp
        }
        // Consume the restoration composition before checking geometry so stale coordinates do
        // not satisfy the predicate.
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue(
            "Expanded anchor was not restored: " +
                "root=$rootHeightPx, height=$sheetHeightPx, " +
                "top=$sheetTopPx, expectedTop=$acceptedExpandedTopPx, " +
                "dismissRequests=$dismissRequestCount",
            advanceFramesUntil {
                isContentFullyVisible(rootHeightPx, sheetHeightPx, sheetTopPx) &&
                    abs(sheetTopPx - acceptedExpandedTopPx) < 1f
            },
        )
        composeRule.waitForIdle()

        assertEquals(0, dismissRequestCount)
        assertEquals(0, exitFinishedCount)

        // Once Expanded is accepted again, a real interaction still owns the one request slot.
        composeRule.onRoot(useUnmergedTree = true).performTouchInput {
            click(Offset(x = 1f, y = 1f))
        }
        assertEquals(1, dismissRequestCount)
        assertEquals(0, exitFinishedCount)
    }

    private companion object {
        const val SHEET_ANIMATION_SETTLE_MILLIS = 1_000L
        const val MAX_VIRTUAL_TIME_MILLIS = 5_000L
        const val EXTRA_SETTLED_FRAMES = 90
        const val MAX_PREDICATE_FRAMES = 360
        const val REQUIRED_STABLE_GEOMETRY_FRAMES = 3
        const val SHEET_TAG = "bottom-sheet-content"
    }

    private fun advanceFramesUntil(predicate: () -> Boolean): Boolean {
        repeat(MAX_PREDICATE_FRAMES) {
            if (predicate()) return true
            composeRule.mainClock.advanceTimeByFrame()
        }
        return predicate()
    }

    private fun awaitStableExpandedTop(
        description: String,
        collapseAction: SemanticsMatcher,
        rootHeightPx: () -> Float,
        sheetHeightPx: () -> Int,
        sheetTopPx: () -> Float,
    ): Float {
        var previousTopPx = Float.NaN
        var stableFrames = 0

        repeat(MAX_PREDICATE_FRAMES) {
            val rootHeight = rootHeightPx()
            val sheetHeight = sheetHeightPx()
            val sheetTop = sheetTopPx()
            val hasAcceptedExpandedSemantics = composeRule.onAllNodes(
                collapseAction,
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
            val isExpanded = hasAcceptedExpandedSemantics &&
                isContentFullyVisible(rootHeight, sheetHeight, sheetTop)

            if (isExpanded) {
                stableFrames = if (
                    !previousTopPx.isNaN() && abs(sheetTop - previousTopPx) < 0.5f
                ) {
                    stableFrames + 1
                } else {
                    1
                }
                previousTopPx = sheetTop
                if (stableFrames >= REQUIRED_STABLE_GEOMETRY_FRAMES) return sheetTop
            } else {
                previousTopPx = Float.NaN
                stableFrames = 0
            }

            composeRule.mainClock.advanceTimeByFrame()
        }

        throw AssertionError(
            "$description never reached stable accepted Expanded: " +
                "root=${rootHeightPx()}, height=${sheetHeightPx()}, top=${sheetTopPx()}",
        )
    }

    private fun hasDepartedFrom(
        acceptedExpandedTopPx: Float,
        sheetHeightPx: Int,
        sheetTopPx: Float,
        rootHeightPx: Float,
    ): Boolean =
        !isContentFullyVisible(rootHeightPx, sheetHeightPx, sheetTopPx) ||
            abs(sheetTopPx - acceptedExpandedTopPx) >= 1f

    private fun isContentFullyVisible(
        rootHeightPx: Float,
        contentHeightPx: Int,
        contentTopPx: Float,
    ): Boolean =
        rootHeightPx > 0f &&
            contentHeightPx > 0 &&
            contentTopPx >= 0f &&
            contentTopPx + contentHeightPx <= rootHeightPx + 1f

    private fun isExactlyExpanded(
        rootHeightPx: Float,
        contentHeightPx: Int,
        contentTopPx: Float,
    ): Boolean =
        isContentFullyVisible(rootHeightPx, contentHeightPx, contentTopPx) &&
            abs(contentTopPx + contentHeightPx - rootHeightPx) < 1f

}

private data class BottomSheetTestPresentation(
    val targetState: ModalScreenState,
    val entrance: ModalEntrance,
)
