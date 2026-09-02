package com.shmakov.udf

import com.shmakov.udf.deeplink.DeepLinkEntryIdFactory
import com.shmakov.udf.deeplink.DemoDeepLinkProblem
import com.shmakov.udf.deeplink.DemoDeepLinkResolutionProblem
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.navigation.TabId
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTabNavigationCoordinatorContractTest {

    @Test
    fun `Back inside selected history captures exact revision tab and top`() {
        val accountsRoot = entry("accounts-root", Accounts)
        val account = entry("account-42", Account(42))
        val state = graph(
            selected = ACCOUNTS_TAB,
            tab(ACCOUNTS_TAB, accountsRoot, account),
            tab(CARDS_TAB, entry("cards-root", Cards)),
        )
        val frame = TabNavigationFrame(state, revision = 7L, transition = null)

        val plan = DemoTabBackPlanner.plan(frame, ACCOUNTS_TAB)

        assertEquals(
            DemoTabBackPlan.Dispatch(
                expectedRevision = 7L,
                action = TabAction.back(
                    expectedSelectedTabId = ACCOUNTS_TAB,
                    expectedTopId = account.id,
                ),
            ),
            plan,
        )
        assertEquals(
            DemoTabBackExecution.Dispatch((plan as DemoTabBackPlan.Dispatch).action),
            DemoTabBackPlanner.resolve(plan, currentRevision = 7L),
        )
    }

    @Test
    fun `Back at non-primary root selects primary without touching histories`() {
        val accounts = tab(ACCOUNTS_TAB, entry("accounts-root", Accounts))
        val cards = tab(CARDS_TAB, entry("cards-root", Cards))
        val state = graph(selected = CARDS_TAB, accounts, cards)

        val plan = DemoTabBackPlanner.plan(
            TabNavigationFrame(state, revision = 3L, transition = null),
            primaryTabId = ACCOUNTS_TAB,
        )

        assertEquals(
            DemoTabBackPlan.Dispatch(
                expectedRevision = 3L,
                action = TabAction.select(ACCOUNTS_TAB),
            ),
            plan,
        )
        assertSame(accounts.history, state[ACCOUNTS_TAB]?.history)
        assertSame(cards.history, state[CARDS_TAB]?.history)
    }

    @Test
    fun `Back at primary root delegates host finish`() {
        val state = graph(
            selected = ACCOUNTS_TAB,
            tab(ACCOUNTS_TAB, entry("accounts-root", Accounts)),
            tab(CARDS_TAB, entry("cards-root", Cards)),
        )

        assertEquals(
            DemoTabBackPlan.FinishHost(expectedRevision = 11L),
            DemoTabBackPlanner.plan(
                TabNavigationFrame(state, revision = 11L, transition = null),
                primaryTabId = ACCOUNTS_TAB,
            ),
        )
        assertEquals(
            DemoTabBackExecution.FinishHost,
            DemoTabBackPlanner.resolve(
                DemoTabBackPlan.FinishHost(expectedRevision = 11L),
                currentRevision = 11L,
            ),
        )
    }

    @Test
    fun `missing primary is an explicit application configuration problem`() {
        val state = graph(
            selected = CARDS_TAB,
            tab(CARDS_TAB, entry("cards-root", Cards)),
        )

        val plan = DemoTabBackPlan.ConfigurationRejected(
            expectedRevision = 1L,
            problem = DemoTabBackConfigurationProblem.MissingPrimaryTab(ACCOUNTS_TAB),
        )
        assertEquals(
            plan,
            DemoTabBackPlanner.plan(
                TabNavigationFrame(state, revision = 1L, transition = null),
                primaryTabId = ACCOUNTS_TAB,
            ),
        )
        assertEquals(
            DemoTabBackExecution.ConfigurationRejected(plan.problem),
            DemoTabBackPlanner.resolve(plan, currentRevision = 1L),
        )
    }

    @Test
    fun `stale Back plan cannot dispatch or finish after any newer frame`() {
        val action = TabAction.select(ACCOUNTS_TAB)
        val dispatch = DemoTabBackPlan.Dispatch(expectedRevision = 4L, action = action)
        val finish = DemoTabBackPlan.FinishHost(expectedRevision = 4L)
        val rejected = DemoTabBackPlan.ConfigurationRejected(
            expectedRevision = 4L,
            problem = DemoTabBackConfigurationProblem.MissingPrimaryTab(ACCOUNTS_TAB),
        )

        assertEquals(
            DemoTabBackExecution.Stale(expectedRevision = 4L, actualRevision = 5L),
            DemoTabBackPlanner.resolve(dispatch, currentRevision = 5L),
        )
        assertEquals(
            DemoTabBackExecution.Stale(expectedRevision = 4L, actualRevision = 5L),
            DemoTabBackPlanner.resolve(finish, currentRevision = 5L),
        )
        assertEquals(
            DemoTabBackExecution.Stale(expectedRevision = 4L, actualRevision = 5L),
            DemoTabBackPlanner.resolve(rejected, currentRevision = 5L),
        )
    }

    @Test
    fun `valid account link plans one atomic target-tab history with fresh IDs`() {
        val ids = ArrayDeque(listOf("accounts", "account-42", "details-42"))

        val result = DemoTabDeepLinkPlanner.plan(
            rawUri = "udf-sandbox://accounts/42/details",
            state = graph(
                selected = ACCOUNTS_TAB,
                tab(ACCOUNTS_TAB, entry("old-accounts", Accounts)),
                tab(CARDS_TAB, entry("cards", Cards)),
            ),
            accountsTabId = ACCOUNTS_TAB,
            entryIdFactory = DeepLinkEntryIdFactory { EntryId(ids.removeFirst()) },
        )

        assertTrue(result is DemoTabDeepLinkPlan.Planned)
        result as DemoTabDeepLinkPlan.Planned
        assertEquals("udf-sandbox://accounts/42/details", result.deepLink.canonicalUri)
        assertEquals(ACCOUNTS_TAB, result.action.tabId)
        assertEquals(
            listOf(Accounts, Account(42), AccountDetails(42)),
            result.action.targetHistory.entries.map(BackStackEntry::route),
        )
        assertEquals(
            listOf("accounts", "account-42", "details-42"),
            result.action.targetHistory.entries.map { entry -> entry.id.value },
        )
    }

    @Test
    fun `invalid and foreign links never materialize a tab action or entry IDs`() {
        val idFactory = DeepLinkEntryIdFactory {
            throw AssertionError("Rejected links must not create entry IDs")
        }

        assertEquals(
            DemoTabDeepLinkPlan.Rejected(
                DemoDeepLinkResolutionProblem.Normalization(
                    DemoDeepLinkProblem.MissingAccountId,
                ),
            ),
            DemoTabDeepLinkPlanner.plan(
                rawUri = "udf-sandbox://accounts/details",
                state = DemoTabGraph.initial(),
                accountsTabId = ACCOUNTS_TAB,
                entryIdFactory = idFactory,
            ),
        )
        assertTrue(
            DemoTabDeepLinkPlanner.plan(
                rawUri = "https://accounts/42/details",
                state = DemoTabGraph.initial(),
                accountsTabId = ACCOUNTS_TAB,
                entryIdFactory = idFactory,
            ) is DemoTabDeepLinkPlan.NotHandled,
        )
    }

    @Test
    fun `recognized link rejects missing target tab before creating entry IDs`() {
        val cardsOnly = graph(
            selected = CARDS_TAB,
            tab(CARDS_TAB, entry("cards", Cards)),
        )

        assertEquals(
            DemoTabDeepLinkPlan.ConfigurationRejected(
                DemoTabDeepLinkConfigurationProblem.MissingDeepLinkTab(ACCOUNTS_TAB),
            ),
            DemoTabDeepLinkPlanner.plan(
                rawUri = "udf-sandbox://accounts/42/details",
                state = cardsOnly,
                accountsTabId = ACCOUNTS_TAB,
                entryIdFactory = DeepLinkEntryIdFactory {
                    throw AssertionError("Missing target tab must reject before hydration")
                },
            ),
        )
    }

    private fun graph(
        selected: TabId,
        vararg tabs: TabState,
    ): TabNavigationState = TabNavigationState.create(selected, tabs.toList())

    private fun tab(id: TabId, vararg entries: BackStackEntry): TabState =
        TabState(id, navigation(*entries))

    private fun navigation(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> throw AssertionError(result.problems)
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private companion object {
        val ACCOUNTS_TAB = TabId("accounts")
        val CARDS_TAB = TabId("cards")
    }
}
