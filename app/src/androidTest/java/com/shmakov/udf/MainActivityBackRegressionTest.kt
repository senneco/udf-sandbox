package com.shmakov.udf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shmakov.udf.composable.common.demoTabItemTag
import com.shmakov.udf.navigation.BackStackEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityBackRegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rapidBackCapturedAtInnerEntryChangesOnlyThatExactHistoryOnce() {
        waitUntilDisplayed(ACCOUNT_ONE_MODAL_CONTENT)
        val viewModel = viewModel()
        val before = viewModel.frames.value
        val cardsBefore = checkNotNull(before.state[DemoTabGraph.cardsTabId])

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        waitUntilAbsent(ACCOUNT_ONE_MODAL_CONTENT)
        waitUntilDisplayed(ACCOUNTS_SCREEN)
        val after = viewModel.frames.value
        assertEquals(before.revision + 1L, after.revision)
        assertEquals(
            listOf(ACCOUNTS_SCREEN_ROUTE),
            checkNotNull(after.state[DemoTabGraph.accountsTabId])
                .history.entries.map(BackStackEntry::route),
        )
        assertEquals(cardsBefore, after.state[DemoTabGraph.cardsTabId])
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun BackDrainsSelectedHistoryThenSelectsPrimaryThenFinishesHost() {
        waitUntilDisplayed(ACCOUNT_ONE_MODAL_CONTENT)
        selectTab(DemoTabGraph.cardsTabId)
        waitUntilDisplayed(CARD_ONE_SCREEN)

        pressBack()
        waitUntilDisplayed(CARDS_SCREEN)
        assertSelected(DemoTabGraph.cardsTabId)

        pressBack()
        waitUntilDisplayed(ACCOUNT_ONE_MODAL_CONTENT)
        assertSelected(DemoTabGraph.accountsTabId)

        // Primary history is still independent and non-trivial after returning from Cards.
        pressBack()
        waitUntilDisplayed(ACCOUNTS_SCREEN)
        assertSelected(DemoTabGraph.accountsTabId)

        val activity = composeRule.activity
        composeRule.runOnUiThread {
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            activity.isFinishing || activity.isDestroyed
        }
    }

    @Test
    fun repeatedRootBackUsesOneRevisionGuardedSelectAndNeverFinishesStalePlan() {
        waitUntilDisplayed(ACCOUNT_ONE_MODAL_CONTENT)
        selectTab(DemoTabGraph.cardsTabId)
        waitUntilDisplayed(CARD_ONE_SCREEN)
        pressBack()
        waitUntilDisplayed(CARDS_SCREEN)
        val viewModel = viewModel()
        val before = viewModel.frames.value

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        waitUntilDisplayed(ACCOUNT_ONE_MODAL_CONTENT)
        val after = viewModel.frames.value
        assertEquals(before.revision + 1L, after.revision)
        assertEquals(DemoTabGraph.accountsTabId, after.state.selectedTabId)
        assertFalse(composeRule.activity.isFinishing)
    }

    private fun viewModel(): AppViewModel =
        ViewModelProvider(composeRule.activity)[AppViewModel::class.java]

    private fun selectTab(tabId: com.shmakov.udf.navigation.TabId) {
        composeRule.onNodeWithTag(demoTabItemTag(tabId), true).performClick()
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            viewModel().frames.value.state.selectedTabId == tabId
        }
    }

    private fun assertSelected(tabId: com.shmakov.udf.navigation.TabId) {
        composeRule.onNodeWithTag(demoTabItemTag(tabId), true).assertIsSelected()
        val other = if (tabId == DemoTabGraph.accountsTabId) {
            DemoTabGraph.cardsTabId
        } else {
            DemoTabGraph.accountsTabId
        }
        composeRule.onNodeWithTag(demoTabItemTag(other), true).assertIsNotSelected()
    }

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
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
        val ACCOUNTS_SCREEN_ROUTE = com.shmakov.udf.navigation.Accounts
        const val ACCOUNTS_SCREEN = "Accounts Screen"
        const val CARDS_SCREEN = "Cards Screen"
        const val CARD_ONE_SCREEN = "Card Screen #1"
        const val ACCOUNT_ONE_MODAL_CONTENT = "Go to Account #2"
        const val UI_TIMEOUT_MILLIS = 5_000L
    }
}
