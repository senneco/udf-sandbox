package com.shmakov.udf

import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.Transactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityRestorationRegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun contentHistorySurvivesActivityRecreationAndBackStillRevealsHome() {
        returnDefaultDemoHistoryToHome()

        composeRule.onNodeWithText(GO_TO_TRANSACTIONS).performClick()
        waitUntilDisplayed(TRANSACTIONS_SCREEN)

        composeRule.activityRule.scenario.recreate()

        waitUntilDisplayed(TRANSACTIONS_SCREEN)
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        waitUntilAbsent(TRANSACTIONS_SCREEN)
        waitUntilDisplayed(HOME_SCREEN)

        composeRule.onNodeWithText(HOME_SCREEN).assertIsDisplayed()
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

        // Durable navigation already removed the modal, while the old renderer still retains its
        // presentation for the exit animation. That process-local layer must not be recreated.
        composeRule.onNodeWithText(ACCOUNT_ONE_MODAL_CONTENT).assertExists()

        composeRule.activityRule.scenario.recreate()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(ACCOUNT_ONE_MODAL_CONTENT).assertDoesNotExist()
        composeRule.onNodeWithText(ACCOUNTS_SCREEN).assertIsDisplayed()
    }

    @Test
    fun parcelledPrimitivePayloadRestoresExactHistoryIntoFreshRevisionZeroFrame() {
        lateinit var expectedHistory: List<BackStackEntry>
        lateinit var navigationKey: String
        lateinit var sourcePayload: ArrayList<String>
        composeRule.runOnIdle {
            val sourceHandle = SavedStateHandle()
            val sourceViewModel = AppViewModel(
                savedStateHandle = sourceHandle,
                fallbackState = AppState(
                    navState = NavState.startAt(Home),
                    showInPlace = false,
                ),
            )
            val rootId = sourceViewModel.frames.value.appState.navState.root.id
            sourceViewModel.dispatch(
                NavAction.navigateFrom(
                    sourceId = rootId,
                    route = Transactions,
                ),
            )
            expectedHistory = sourceViewModel.frames.value.appState.navState.entries
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

        lateinit var restoredHistory: List<BackStackEntry>
        var restoredRevision = -1L
        var restoredTransition: NavTransitionIntent? = null
        composeRule.runOnIdle {
            val recreatedViewModel = AppViewModel(
                savedStateHandle = SavedStateHandle(
                    mapOf(navigationKey to ArrayList(copiedPayload)),
                ),
                fallbackState = AppState(
                    navState = NavState.startAt(Home),
                    showInPlace = true,
                ),
            )
            val restoredFrame = recreatedViewModel.frames.value
            restoredHistory = restoredFrame.appState.navState.entries
            restoredRevision = restoredFrame.navigationRevision
            restoredTransition = restoredFrame.navigationTransition
        }

        assertEquals(expectedHistory, restoredHistory)
        assertEquals(listOf(Home, Transactions), expectedHistory.map { entry -> entry.route })
        assertEquals(0L, restoredRevision)
        assertNull(restoredTransition)
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
        const val HOME_SCREEN = "Home Screen"
        const val GO_TO_TRANSACTIONS = "Go to Transactions"
        const val TRANSACTIONS_SCREEN = "Transactions Screen"
        const val ACCOUNTS_SCREEN = "Accounts Screen"
        const val ACCOUNT_ONE_MODAL_CONTENT = "Go to Account #2"
        const val UI_TIMEOUT_MILLIS = 5_000L
    }
}
