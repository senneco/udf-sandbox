package com.shmakov.udf

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Card
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.TabTransitionIntent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityDeepLinkRegressionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun closeScenario() {
        scenario?.close()
        scenario = null
    }

    @Test
    @Suppress("DEPRECATION")
    fun manifestResolvesCanonicalLinkAndDeclaresSingleTopHost() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val canonicalIntent = deepLinkIntent(CANONICAL_URI).setPackage(context.packageName)
        val wrongHostIntent = deepLinkIntent(
            "udf-sandbox://payments/42/details",
        ).setPackage(context.packageName)
        val uppercaseIntent = deepLinkIntent(
            "UDF-SANDBOX://ACCOUNTS/42/details",
        ).setPackage(context.packageName)

        val resolved = packageManager.resolveActivity(
            canonicalIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        val wrongHost = packageManager.resolveActivity(
            wrongHostIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        val uppercase = packageManager.resolveActivity(
            uppercaseIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        val resolvedActivity = resolved?.activityInfo
            ?: throw AssertionError("Canonical deep link did not resolve")
        val activityInfo = packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )

        assertEquals(MainActivity::class.java.name, resolvedActivity.name)
        assertEquals(null, wrongHost)
        assertEquals(null, uppercase)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, activityInfo.launchMode)
    }

    @Test
    fun coldLinkUsesOneOpenTabAndIsNotReplayedOnActivityRecreation() {
        val launched = ActivityScenario.launch<MainActivity>(deepLinkIntent(CANONICAL_URI))
        scenario = launched
        lateinit var originalActivity: MainActivity
        lateinit var originalViewModel: AppViewModel
        launched.onActivity { activity ->
            originalActivity = activity
            originalViewModel = ViewModelProvider(activity)[AppViewModel::class.java]
        }

        waitForRoutes(originalViewModel, expectedDeepLinkRoutes())
        val originalFrame = originalViewModel.frames.value
        val originalIds = originalFrame.state.allEntryIds()
        assertEquals(DemoTabGraph.accountsTabId, originalFrame.state.selectedTabId)
        assertEquals(1L, originalFrame.revision)
        assertTrue(originalFrame.transition is TabTransitionIntent.TabOpened)
        assertEquals(
            listOf(Cards, Card(1)),
            originalFrame.state[DemoTabGraph.cardsTabId]?.history?.entries
                ?.map(BackStackEntry::route),
        )
        waitUntilDisplayed(DETAILS_SCREEN)

        launched.recreate()

        lateinit var recreatedActivity: MainActivity
        lateinit var recreatedViewModel: AppViewModel
        launched.onActivity { activity ->
            recreatedActivity = activity
            recreatedViewModel = ViewModelProvider(activity)[AppViewModel::class.java]
        }
        assertNotSame(originalActivity, recreatedActivity)
        assertSame(originalViewModel, recreatedViewModel)
        assertEquals(originalIds, recreatedViewModel.frames.value.state.allEntryIds())
        assertEquals(1L, recreatedViewModel.frames.value.revision)
        assertTrue(recreatedViewModel.frames.value.transition is TabTransitionIntent.TabOpened)
        waitUntilDisplayed(DETAILS_SCREEN)

        pressBack(recreatedActivity)
        waitForRoutes(recreatedViewModel, listOf(Accounts, Account(42)))
        waitUntilDisplayed(ACCOUNT_MODAL_CONTENT)

        pressBack(recreatedActivity)
        waitForRoutes(recreatedViewModel, listOf(Accounts))
        waitUntilDisplayed(ACCOUNTS_SCREEN)
        waitUntilAbsent(ACCOUNT_MODAL_CONTENT)
    }

    @Test
    fun warmLinkUsesSameHostAndPreservesTheInactiveTabWithoutIntermediateSelect() {
        val launched = ActivityScenario.launch(MainActivity::class.java)
        scenario = launched
        lateinit var originalActivity: MainActivity
        lateinit var originalViewModel: AppViewModel
        launched.onActivity { activity ->
            originalActivity = activity
            originalViewModel = ViewModelProvider(activity)[AppViewModel::class.java]
            originalViewModel.dispatch(
                com.shmakov.udf.navigation.TabAction.select(DemoTabGraph.cardsTabId),
            )
        }
        val beforeFrame = originalViewModel.frames.value
        val cardsBefore = checkNotNull(beforeFrame.state[DemoTabGraph.cardsTabId])
        val oldAccountsIds = checkNotNull(beforeFrame.state[DemoTabGraph.accountsTabId])
            .history.entries.map(BackStackEntry::id)

        launched.onActivity { activity ->
            activity.startActivity(
                deepLinkIntent(CANONICAL_URI).apply {
                    setClass(activity, MainActivity::class.java)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
            )
        }

        waitForRoutes(originalViewModel, expectedDeepLinkRoutes())
        waitUntilDisplayed(DETAILS_SCREEN)
        lateinit var currentActivity: MainActivity
        lateinit var currentViewModel: AppViewModel
        launched.onActivity { activity ->
            currentActivity = activity
            currentViewModel = ViewModelProvider(activity)[AppViewModel::class.java]
        }

        val afterFrame = currentViewModel.frames.value
        assertSame(originalActivity, currentActivity)
        assertSame(originalViewModel, currentViewModel)
        assertEquals(beforeFrame.revision + 1L, afterFrame.revision)
        assertTrue(afterFrame.transition is TabTransitionIntent.TabOpened)
        assertEquals(DemoTabGraph.accountsTabId, afterFrame.state.selectedTabId)
        assertSame(cardsBefore, afterFrame.state[DemoTabGraph.cardsTabId])
        assertTrue(
            oldAccountsIds.toSet().intersect(
                checkNotNull(afterFrame.state[DemoTabGraph.accountsTabId])
                    .history.entries.map(BackStackEntry::id).toSet(),
            ).isEmpty(),
        )
        assertEquals(CANONICAL_URI, currentActivity.intent.dataString)

        // ActivityScenario on API 29 can miss DESTROYED after same-instance onNewIntent.
        composeRule.runOnUiThread(currentActivity::finishAndRemoveTask)
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            currentActivity.isDestroyed
        }
        scenario = null
    }

    private fun deepLinkIntent(uri: String): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(uri),
    ).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun expectedDeepLinkRoutes(): List<Route> =
        listOf(Accounts, Account(42), AccountDetails(42))

    private fun waitForRoutes(viewModel: AppViewModel, expected: List<Route>) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            viewModel.frames.value.state[DemoTabGraph.accountsTabId]
                ?.history?.entries?.map(BackStackEntry::route) == expected
        }
    }

    private fun pressBack(activity: MainActivity) {
        composeRule.runOnUiThread {
            activity.onBackPressedDispatcher.onBackPressed()
        }
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

    private fun com.shmakov.udf.navigation.TabNavigationState.allEntryIds() =
        tabs.flatMap { tab -> tab.history.entries.map(BackStackEntry::id) }

    private companion object {
        const val CANONICAL_URI = "udf-sandbox://accounts/42/details"
        const val DETAILS_SCREEN = "Account Details Screen #42"
        const val ACCOUNT_MODAL_CONTENT = "Go to details"
        const val ACCOUNTS_SCREEN = "Accounts Screen"
        const val UI_TIMEOUT_MILLIS = 5_000L
    }
}
