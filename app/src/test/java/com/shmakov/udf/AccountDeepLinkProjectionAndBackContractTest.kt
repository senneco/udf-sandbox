package com.shmakov.udf

import com.shmakov.udf.composable.common.DemoNavigationLayoutPolicies
import com.shmakov.udf.deeplink.AccountDetailsDeepLinkHistoryFactory
import com.shmakov.udf.deeplink.AccountDetailsTarget
import com.shmakov.udf.deeplink.DeepLinkEntryIdFactory
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.ContentSlot
import com.shmakov.udf.navigation.ContentSlotId
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.ModalLayer
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavProjector
import com.shmakov.udf.navigation.NavProjectionResult
import com.shmakov.udf.navigation.NavReducer
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeepLinkProjectionAndBackContractTest {

    @Test
    fun `one hydrated state has documented single and expanded projections at every Back step`() {
        val ids = ArrayDeque(listOf("home", "accounts", "account", "details"))
        var state = valid(
            AccountDetailsDeepLinkHistoryFactory.create(
                target = AccountDetailsTarget(42),
                entryIdFactory = DeepLinkEntryIdFactory { EntryId(ids.removeFirst()) },
            ),
        )
        val home = state.entries[0]
        val accounts = state.entries[1]
        val account = state.entries[2]
        val details = state.entries[3]

        assertEquals(listOf(Home, Accounts, Account(42), AccountDetails(42)), routes(state))
        assertProjection(
            state,
            singleRoot = details,
            expandedRoot = details,
        )

        val detailsBack = checkNotNull(AppBackActionPlanner.action(state))
        assertEquals(NavAction.Pop, detailsBack)
        state = NavReducer.reduce(state, detailsBack).state
        assertEquals(listOf(home, accounts, account), state.entries)
        assertProjection(
            state,
            singleRoot = accounts,
            expandedRoot = home,
            expandedNested = accounts,
            modal = ModalLayer(account, accounts.id),
        )

        val modalBack = checkNotNull(AppBackActionPlanner.action(state))
        assertEquals(NavAction.DismissModal(account.id), modalBack)
        state = NavReducer.reduce(state, modalBack).state
        assertEquals(listOf(home, accounts), state.entries)
        assertProjection(
            state,
            singleRoot = accounts,
            expandedRoot = home,
            expandedNested = accounts,
        )

        val accountsBack = checkNotNull(AppBackActionPlanner.action(state))
        assertEquals(NavAction.Pop, accountsBack)
        state = NavReducer.reduce(state, accountsBack).state
        assertEquals(listOf(home), state.entries)
        assertProjection(state, singleRoot = home, expandedRoot = home)
        assertNull(AppBackActionPlanner.action(state))
    }

    private fun assertProjection(
        state: NavState,
        singleRoot: BackStackEntry,
        expandedRoot: BackStackEntry,
        expandedNested: BackStackEntry? = null,
        modal: ModalLayer? = null,
    ) {
        val single = projected(state, DemoNavigationLayoutPolicies.singlePane)
        val expanded = projected(state, DemoNavigationLayoutPolicies.expandedPane)

        assertEquals(ContentSlot(ContentSlotId.Root, singleRoot), single.root)
        assertTrue(single.nestedSlots.isEmpty())
        assertEquals(listOfNotNull(modal), single.modalLayers)

        assertEquals(ContentSlot(ContentSlotId.Root, expandedRoot), expanded.root)
        assertEquals(
            listOfNotNull(
                expandedNested?.let { entry ->
                    ContentSlot(ContentSlotId.ChildOf(expandedRoot.id), entry)
                },
            ),
            expanded.nestedSlots,
        )
        assertEquals(listOfNotNull(modal), expanded.modalLayers)
    }

    private fun projected(
        state: NavState,
        policy: com.shmakov.udf.navigation.NavigationLayoutPolicy,
    ) = when (val result = NavProjector.project(state, policy)) {
        is NavProjectionResult.Success -> result.tree
        is NavProjectionResult.Failure -> throw AssertionError(
            "Expected projection, got ${result.problem}",
        )
    }

    private fun valid(result: NavStateCreationResult): NavState = when (result) {
        is NavStateCreationResult.Valid -> result.state
        is NavStateCreationResult.Invalid -> throw AssertionError(
            "Expected valid state, got ${result.problems}",
        )
    }

    private fun routes(state: NavState) = state.entries.map(BackStackEntry::route)
}
