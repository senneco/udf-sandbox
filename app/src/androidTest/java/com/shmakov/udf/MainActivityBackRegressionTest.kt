package com.shmakov.udf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityBackRegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun repeatedHostBackBeforeRecompositionDismissesExactModalOnly() {
        returnDefaultDemoHistoryToHome()

        composeRule.onNodeWithText(HOME_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText(GO_TO_ACCOUNTS).performClick()
        waitUntilDisplayed(ACCOUNTS_SCREEN)

        composeRule.onNodeWithText(GO_TO_ACCOUNT_ONE).performClick()
        waitUntilDisplayed(ACCOUNT_ONE_MODAL_CONTENT)
        composeRule.onNodeWithText(ACCOUNT_ONE_MODAL_CONTENT).assertIsDisplayed()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        waitUntilAbsent(ACCOUNT_ONE_MODAL_CONTENT)
        waitUntilDisplayed(ACCOUNTS_SCREEN)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(ACCOUNT_ONE_MODAL_CONTENT).assertDoesNotExist()
        composeRule.onNodeWithText(ACCOUNTS_SCREEN).assertIsDisplayed()
    }

    /** The demo fixture intentionally starts at Home -> Accounts -> Account 1. */
    private fun returnDefaultDemoHistoryToHome() {
        waitUntilDisplayed(ACCOUNT_ONE_MODAL_CONTENT)
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        waitUntilAbsent(ACCOUNT_ONE_MODAL_CONTENT)
        waitUntilDisplayed(ACCOUNTS_SCREEN)

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        waitUntilAbsent(ACCOUNTS_SCREEN)
        waitUntilDisplayed(HOME_SCREEN)
        composeRule.waitForIdle()
    }

    private fun waitUntilDisplayed(text: String) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun waitUntilAbsent(text: String) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private companion object {
        const val HOME_SCREEN = "Home Screen"
        const val GO_TO_ACCOUNTS = "Go to Accounts"
        const val ACCOUNTS_SCREEN = "Accounts Screen"
        const val GO_TO_ACCOUNT_ONE = "Go to Account 1"
        const val ACCOUNT_ONE_MODAL_CONTENT = "Go to Account #2"
        const val UI_TIMEOUT_MILLIS = 5_000L
    }
}
