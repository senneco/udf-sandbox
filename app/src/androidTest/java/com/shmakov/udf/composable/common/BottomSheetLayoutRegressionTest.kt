package com.shmakov.udf.composable.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shmakov.udf.navigation.ModalScreenState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomSheetLayoutRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

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

        composeRule.mainClock.advanceTimeBy(SHEET_ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()

        assertEquals(1, dismissRequestCount)
        assertEquals(1, exitFinishedCount)

        composeRule.mainClock.advanceTimeBy(SHEET_ANIMATION_SETTLE_MILLIS)
        composeRule.waitForIdle()
        assertEquals(1, dismissRequestCount)
        assertEquals(1, exitFinishedCount)
    }

    private companion object {
        const val SHEET_ANIMATION_SETTLE_MILLIS = 1_000L
    }
}
