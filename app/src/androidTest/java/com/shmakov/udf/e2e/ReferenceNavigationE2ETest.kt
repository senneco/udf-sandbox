package com.shmakov.udf.e2e

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shmakov.udf.MainActivity
import com.shmakov.udf.composable.common.BOTTOM_SHEET_SCRIM_TEST_TAG
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Black-box reference journeys through the real Activity host.
 *
 * Exact entry IDs, reducer revisions, retained layers and saveable buckets stay covered by their
 * focused contract tests. This suite deliberately changes state only through UI semantics,
 * gestures, Android Back and configuration changes.
 */
@RunWith(AndroidJUnit4::class)
class ReferenceNavigationE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val robot by lazy { NavigationE2ERobot(composeRule) }

    @Before
    fun startFromPortraitAccountsRoot() {
        robot.forcePortrait()
        robot.returnDefaultFixtureToAccountsRoot()
    }

    @Test
    fun accountsSheetDetailsAndBackTraverseSelectedTabHistory() {
        robot.openAccountOneSheet()
        robot.tap(GO_TO_DETAILS)
        robot.awaitVisible(ACCOUNT_ONE_DETAILS)

        robot.back()
        robot.awaitAbsent(ACCOUNT_ONE_DETAILS)
        robot.awaitVisible(GO_TO_ACCOUNT_TWO)

        robot.back()
        robot.awaitAbsent(GO_TO_ACCOUNT_TWO)
        robot.awaitVisible(ACCOUNTS_SCREEN)

    }

    @Test
    fun sameSelectedTabHistorySurvivesPortraitAndLandscape() {
        robot.openAccountOneSheet()

        robot.awaitVisible(ACCOUNTS_SCREEN)
        robot.awaitVisible(GO_TO_ACCOUNT_TWO)

        robot.forceLandscape()
        robot.awaitVisible(ACCOUNTS_SCREEN)
        robot.awaitVisible(GO_TO_ACCOUNT_TWO)

        robot.forcePortrait()
        robot.awaitVisible(ACCOUNTS_SCREEN)
        robot.awaitVisible(GO_TO_ACCOUNT_TWO)
    }

    @Test
    fun rapidBackOnStackedSheetsDismissesOnlyCapturedTopSheet() {
        robot.openAccountOneSheet()
        robot.tap(GO_TO_ACCOUNT_TWO)
        robot.awaitVisible(GO_TO_ACCOUNT_THREE)

        robot.rapidBack(count = 2)
        robot.awaitAbsent(GO_TO_ACCOUNT_THREE)
        robot.awaitVisible(GO_TO_ACCOUNT_TWO)

        robot.back()
        robot.awaitAbsent(GO_TO_ACCOUNT_TWO)
        robot.awaitVisible(ACCOUNTS_SCREEN)
    }

    @Test
    fun scrimDismissesSheetThroughTheApplicationBoundary() {
        robot.openAccountOneSheet()

        robot.tapScrim()

        robot.awaitAbsent(GO_TO_ACCOUNT_TWO)
        robot.awaitVisible(ACCOUNTS_SCREEN)
    }

    @Test
    fun swipeDismissesSheetThroughTheApplicationBoundary() {
        robot.openAccountOneSheet()

        robot.swipeSheetDown()

        robot.awaitAbsent(GO_TO_ACCOUNT_TWO)
        robot.awaitVisible(ACCOUNTS_SCREEN)
    }
}

private class NavigationE2ERobot(
    private val rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
) {
    fun returnDefaultFixtureToAccountsRoot() {
        awaitVisible(GO_TO_ACCOUNT_TWO)
        back()
        awaitAbsent(GO_TO_ACCOUNT_TWO)
        awaitVisible(ACCOUNTS_SCREEN)
    }

    fun openAccountOneSheet() {
        awaitVisible(ACCOUNTS_SCREEN)
        tap(GO_TO_ACCOUNT_ONE)
        awaitVisible(GO_TO_ACCOUNT_TWO)
    }

    fun tap(text: String) {
        awaitVisible(text)
        rule.onNodeWithText(text).performClick()
    }

    fun back() {
        rapidBack(count = 1)
    }

    fun rapidBack(count: Int) {
        require(count > 0) { "Back count must be positive" }
        rule.runOnUiThread {
            repeat(count) {
                rule.activity.onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    fun tapScrim() {
        val collapseAction = SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse)
        rule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            rule.onAllNodes(hasTestTag(BOTTOM_SHEET_SCRIM_TEST_TAG), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(collapseAction, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        val scrim = rule.onNodeWithTag(
            BOTTOM_SHEET_SCRIM_TEST_TAG,
            useUnmergedTree = true,
        )
        val scrimBounds = scrim.fetchSemanticsNode().boundsInRoot
        val sheetTop = rule.onAllNodes(collapseAction, useUnmergedTree = true)[0]
            .fetchSemanticsNode().boundsInRoot.top
        val exposedScrimHeight = sheetTop - scrimBounds.top
        check(exposedScrimHeight > 1f) {
            "Sheet covers the whole scrim: scrim=$scrimBounds, sheetTop=$sheetTop"
        }

        scrim.performTouchInput {
            click(Offset(scrimBounds.width / 2f, exposedScrimHeight / 2f))
        }
    }

    fun swipeSheetDown() {
        val collapseAction = SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse)
        rule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            rule.onAllNodes(collapseAction, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(collapseAction, useUnmergedTree = true)[0]
            .performTouchInput { swipeDown() }
    }

    fun forcePortrait() {
        forceOrientation(
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            expectedConfigurationOrientation = Configuration.ORIENTATION_PORTRAIT,
        )
    }

    fun forceLandscape() {
        forceOrientation(
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            expectedConfigurationOrientation = Configuration.ORIENTATION_LANDSCAPE,
        )
    }

    fun awaitVisible(text: String) {
        rule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun awaitAbsent(text: String) {
        rule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithText(text).assertDoesNotExist()
    }

    private fun forceOrientation(
        requestedOrientation: Int,
        expectedConfigurationOrientation: Int,
    ) {
        rule.runOnUiThread {
            rule.activity.requestedOrientation = requestedOrientation
        }
        rule.waitUntil(timeoutMillis = ORIENTATION_TIMEOUT_MILLIS) {
            rule.activity.resources.configuration.orientation == expectedConfigurationOrientation
        }
        rule.waitForIdle()
    }

    private fun hasTestTag(tag: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.TestTag,
            tag,
        )
}

private const val ACCOUNTS_SCREEN = "Accounts Screen"
private const val GO_TO_ACCOUNT_ONE = "Go to Account 1"
private const val GO_TO_ACCOUNT_TWO = "Go to Account #2"
private const val GO_TO_ACCOUNT_THREE = "Go to Account #3"
private const val GO_TO_DETAILS = "Go to details"
private const val ACCOUNT_ONE_DETAILS = "Account Details Screen #1"
private const val UI_TIMEOUT_MILLIS = 5_000L
private const val ORIENTATION_TIMEOUT_MILLIS = 10_000L
