package com.shmakov.udf

import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shmakov.udf.composable.common.demoTabItemTag
import com.shmakov.udf.composable.content.accountLocalStateTag
import com.shmakov.udf.composable.content.cardLocalStateTag
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Card
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.DemoRouteCodec
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.navigation.TabId
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityRestorationRegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityABARetainsBothHistoriesExactIdsAndEntryLocalSaveableState() {
        waitUntilDisplayed(ACCOUNT_ONE_MODAL_CONTENT)
        val originalViewModel = viewModel()
        val initialHistories = originalViewModel.frames.value.state.historyEntriesByTab()

        increment(accountLocalStateTag(1), expectedText = "Account #1 local value: 1")
        selectTab(DemoTabGraph.cardsTabId)
        waitUntilDisplayed(CARD_ONE_SCREEN)
        increment(cardLocalStateTag(1), expectedText = "Card #1 local value: 1")
        increment(cardLocalStateTag(1), expectedText = "Card #1 local value: 2")

        selectTab(DemoTabGraph.accountsTabId)
        waitUntilDisplayed(ACCOUNT_ONE_MODAL_CONTENT)
        composeRule.onNodeWithTag(accountLocalStateTag(1))
            .assertTextEquals("Account #1 local value: 1")
        assertEquals(initialHistories, originalViewModel.frames.value.state.historyEntriesByTab())
        val beforeRecreation = originalViewModel.frames.value
        val originalActivity = composeRule.activity

        composeRule.activityRule.scenario.recreate()

        val recreatedActivity = composeRule.activity
        val recreatedViewModel = viewModel()
        assertNotSame(originalActivity, recreatedActivity)
        assertSame(originalViewModel, recreatedViewModel)
        assertEquals(beforeRecreation, recreatedViewModel.frames.value)
        waitUntilDisplayed(ACCOUNT_ONE_MODAL_CONTENT)
        composeRule.onNodeWithTag(accountLocalStateTag(1))
            .assertTextEquals("Account #1 local value: 1")

        selectTab(DemoTabGraph.cardsTabId)
        waitUntilDisplayed(CARD_ONE_SCREEN)
        composeRule.onNodeWithTag(cardLocalStateTag(1))
            .assertTextEquals("Card #1 local value: 2")
        assertEquals(initialHistories, recreatedViewModel.frames.value.state.historyEntriesByTab())
    }

    @Test
    fun rendererRetainedModalIsNotRestoredAcrossActivityRecreation() {
        waitUntilDisplayed(ACCOUNT_ONE_MODAL_CONTENT)
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        // Durable history already removed the modal while its old renderer still owns exit UI.
        composeRule.onNodeWithText(ACCOUNT_ONE_MODAL_CONTENT).assertExists()

        composeRule.activityRule.scenario.recreate()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(ACCOUNT_ONE_MODAL_CONTENT).assertDoesNotExist()
        composeRule.onNodeWithText(ACCOUNTS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(demoTabItemTag(DemoTabGraph.accountsTabId), true)
            .assertIsSelected()
    }

    @Test
    fun parcelledGraphRestoresEveryTabIntoFreshRevisionZeroOwner() {
        val initial = graph(
            selected = ACCOUNTS_TAB,
            tab(
                ACCOUNTS_TAB,
                BackStackEntry(EntryId("accounts"), Accounts),
                BackStackEntry(EntryId("account-5"), Account(5)),
            ),
            tab(
                CARDS_TAB,
                BackStackEntry(EntryId("cards"), Cards),
                BackStackEntry(EntryId("card-7"), Card(7)),
            ),
        )
        lateinit var expectedState: TabNavigationState
        lateinit var navigationKey: String
        lateinit var sourcePayload: ArrayList<String>
        composeRule.runOnIdle {
            val sourceHandle = SavedStateHandle()
            val sourceViewModel = AppViewModel(
                savedStateHandle = sourceHandle,
                fallbackGraphFactory = TabNavigationStateFactory { initial },
                routeCodec = DemoRouteCodec,
            )
            sourceViewModel.dispatch(TabAction.select(CARDS_TAB))
            expectedState = sourceViewModel.frames.value.state
            navigationKey = sourceHandle.keys().single()
            sourcePayload = ArrayList(
                checkNotNull(sourceHandle.get<ArrayList<String>>(navigationKey)),
            )
        }

        val bundlePayload = ArrayList(sourcePayload)
        val bundle = Bundle().apply {
            putStringArrayList(navigationKey, bundlePayload)
        }
        val restoredBundle = parcelRoundTrip(bundle)
        val copiedPayload = checkNotNull(restoredBundle.getStringArrayList(navigationKey))
        assertEquals(sourcePayload, copiedPayload)
        assertNotSame(bundlePayload, copiedPayload)

        lateinit var restoredFrame: TabNavigationFrame
        composeRule.runOnIdle {
            val freshOwner = AppViewModel(
                savedStateHandle = SavedStateHandle(
                    mapOf(navigationKey to ArrayList(copiedPayload)),
                ),
                fallbackGraphFactory = TabNavigationStateFactory {
                    throw AssertionError("Valid parcelled graph must win")
                },
                routeCodec = DemoRouteCodec,
            )
            restoredFrame = freshOwner.frames.value
        }

        assertEquals(expectedState, restoredFrame.state)
        assertEquals(expectedState.historyEntriesByTab(), restoredFrame.state.historyEntriesByTab())
        assertEquals(CARDS_TAB, restoredFrame.state.selectedTabId)
        assertEquals(0L, restoredFrame.revision)
        assertNull(restoredFrame.transition)
    }

    private fun viewModel(): AppViewModel =
        ViewModelProvider(composeRule.activity)[AppViewModel::class.java]

    private fun selectTab(tabId: TabId) {
        composeRule.onNodeWithTag(demoTabItemTag(tabId), true).performClick()
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            viewModel().frames.value.state.selectedTabId == tabId
        }
        composeRule.onNodeWithTag(demoTabItemTag(tabId), true).assertIsSelected()
        val other = if (tabId == DemoTabGraph.accountsTabId) {
            DemoTabGraph.cardsTabId
        } else {
            DemoTabGraph.accountsTabId
        }
        composeRule.onNodeWithTag(demoTabItemTag(other), true).assertIsNotSelected()
    }

    private fun increment(tag: String, expectedText: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(expectedText).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertTextEquals(expectedText)
    }

    private fun waitUntilDisplayed(text: String) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun TabNavigationState.historyEntriesByTab(): Map<TabId, List<BackStackEntry>> =
        tabs.associate { tab -> tab.id to tab.history.entries }

    private fun graph(selected: TabId, vararg tabs: TabState): TabNavigationState =
        TabNavigationState.create(selected, tabs.toList())

    private fun tab(id: TabId, vararg entries: BackStackEntry): TabState =
        TabState(
            id = id,
            history = when (val result = NavState.fromEntries(entries.toList())) {
                is NavStateCreationResult.Valid -> result.state
                is NavStateCreationResult.Invalid -> throw AssertionError(result.problems)
            },
        )

    private fun parcelRoundTrip(source: Bundle): Bundle {
        val parcel = Parcel.obtain()
        return try {
            source.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Bundle.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private companion object {
        val ACCOUNTS_TAB = TabId("accounts")
        val CARDS_TAB = TabId("cards")
        const val ACCOUNT_ONE_MODAL_CONTENT = "Go to Account #2"
        const val ACCOUNTS_SCREEN = "Accounts Screen"
        const val CARD_ONE_SCREEN = "Card Screen #1"
        const val UI_TIMEOUT_MILLIS = 5_000L
    }
}
