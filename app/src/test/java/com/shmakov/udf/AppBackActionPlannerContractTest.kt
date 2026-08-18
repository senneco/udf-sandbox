package com.shmakov.udf

import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavReducer
import com.shmakov.udf.navigation.NavReduction
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavUnchangedReason
import com.shmakov.udf.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackActionPlannerContractTest {

    @Test
    fun `root-only history has no host Back action`() {
        val initial = state(entry("home", Home))

        assertNull(AppBackActionPlanner.action(initial))
    }

    @Test
    fun `content top plans Pop`() {
        val initial = state(
            entry("home", Home),
            entry("accounts", Accounts),
        )

        assertSame(NavAction.Pop, AppBackActionPlanner.action(initial))
    }

    @Test
    fun `modal top plans exact-ID DismissModal`() {
        val modal = entry("account-modal", Account(42))
        val initial = state(entry("home", Home), modal)

        assertEquals(
            NavAction.DismissModal(modal.id),
            AppBackActionPlanner.action(initial),
        )
    }

    @Test
    fun `replaying captured modal Back action cannot pop underlying content`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val modal = entry("account-modal", Account(42))
        val initial = state(home, accounts, modal)
        val capturedAction = dismissAction(AppBackActionPlanner.action(initial))

        val first = changed(NavReducer.reduce(initial, capturedAction))
        val replay = unchanged(NavReducer.reduce(first.state, capturedAction))

        assertEquals(listOf(home, accounts), first.state.entries)
        assertSame(first.state, replay.state)
        assertEquals(NavUnchangedReason.EntryNotFound(modal.id), replay.reason)
        assertEquals(listOf(home, accounts), replay.state.entries)
    }

    @Test
    fun `modal below a content top still plans Pop`() {
        val initial = state(
            entry("home", Home),
            entry("account-modal", Account(42)),
            entry("details", AccountDetails(42)),
        )

        assertSame(NavAction.Pop, AppBackActionPlanner.action(initial))
    }

    private fun dismissAction(action: NavAction?): NavAction.DismissModal {
        assertTrue("Expected DismissModal, got $action", action is NavAction.DismissModal)
        return action as NavAction.DismissModal
    }

    private fun changed(result: NavReduction): NavReduction.Changed = when (result) {
        is NavReduction.Changed -> result
        is NavReduction.Unchanged -> throw AssertionError(
            "Expected state change, got ${result.reason}",
        )
    }

    private fun unchanged(result: NavReduction): NavReduction.Unchanged = when (result) {
        is NavReduction.Changed -> throw AssertionError(
            "Expected no-op, got ${result.transition}",
        )
        is NavReduction.Unchanged -> result
    }

    private fun state(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> throw AssertionError(
                "Invalid test fixture: ${result.problems}",
            )
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)
}
