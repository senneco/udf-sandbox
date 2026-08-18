package com.shmakov.udf

import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.ContentPlacementDecision
import com.shmakov.udf.navigation.ContentSlot
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.ModalLayer
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavProjectionResult
import com.shmakov.udf.navigation.NavProjector
import com.shmakov.udf.navigation.NavReducer
import com.shmakov.udf.navigation.NavReduction
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.NavigationLayoutPolicy
import com.shmakov.udf.navigation.NavigationRenderTree
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.Transactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPresentationPlannerContractTest {

    @Test
    fun `initial presentation never animates a restored transition intent`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val target = target(
            revision = 7,
            tree = project(state(home, accounts), expandedPane),
            transition = NavTransitionIntent.Pushed(home.id, accounts.id),
        )

        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(previous = null, target = target),
        )
    }

    @Test
    fun `same revision layout reprojection does not replay sticky navigation intent`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val navState = state(home, accounts)
        val intent = NavTransitionIntent.Pushed(home.id, accounts.id)
        val previous = target(3, project(navState, singlePane), intent)
        val target = target(3, project(navState, expandedPane), intent)

        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(previous, target),
        )
    }

    @Test
    fun `revision gaps and stale targets suppress navigation motion`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val previous = target(4, project(state(home), expandedPane), null)
        val validPush = NavTransitionIntent.Pushed(home.id, accounts.id)
        val tree = project(state(home, accounts), expandedPane)

        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(
                previous,
                target(6, tree, validPush),
            ),
        )
        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(
                previous,
                target(3, tree, validPush),
            ),
        )
    }

    @Test
    fun `missing or structurally incompatible intent cannot animate a changed revision`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val previous = target(10, project(state(home), expandedPane), null)
        val targetTree = project(state(home, accounts), expandedPane)

        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(
                previous,
                target(11, targetTree, null),
            ),
        )
        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(
                previous,
                target(
                    11,
                    targetTree,
                    NavTransitionIntent.Pushed(
                        fromEntryId = EntryId("not-visible-before"),
                        addedEntryId = accounts.id,
                    ),
                ),
            ),
        )
        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(
                previous,
                target(
                    11,
                    targetTree,
                    NavTransitionIntent.Pushed(
                        fromEntryId = home.id,
                        addedEntryId = EntryId("not-visible-after"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `modal-only changes do not select content motion`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val withoutModal = project(state(home, accounts), expandedPane)
        val withModal = project(state(home, accounts, account), expandedPane)

        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(
                target(20, withoutModal, null),
                target(
                    21,
                    withModal,
                    NavTransitionIntent.Pushed(accounts.id, account.id),
                ),
            ),
        )
        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(
                target(21, withModal, null),
                target(
                    22,
                    withoutModal,
                    NavTransitionIntent.ModalDismissed(account.id),
                ),
            ),
        )
    }

    @Test
    fun `exact push and pop intents select directional content motion`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val homeTree = project(state(home), expandedPane)
        val accountsTree = project(state(home, accounts), expandedPane)

        assertEquals(
            NavigationContentMotion.Push,
            NavigationPresentationPlanner.contentMotion(
                target(30, homeTree, null),
                target(
                    31,
                    accountsTree,
                    NavTransitionIntent.Pushed(home.id, accounts.id),
                ),
            ),
        )
        assertEquals(
            NavigationContentMotion.Pop,
            NavigationPresentationPlanner.contentMotion(
                target(31, accountsTree, null),
                target(
                    32,
                    homeTree,
                    NavTransitionIntent.Popped(accounts.id, home.id),
                ),
            ),
        )
    }

    @Test
    fun `exact branch and history replacements select replace motion`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val transactions = entry("transactions", Transactions)
        val cards = entry("cards", Cards)
        val previousTree = project(state(home, accounts, account), expandedPane)
        val branchTree = project(state(home, transactions), expandedPane)
        val historyTree = project(state(cards), expandedPane)

        assertEquals(
            NavigationContentMotion.Replace,
            NavigationPresentationPlanner.contentMotion(
                target(40, previousTree, null),
                target(
                    41,
                    branchTree,
                    NavTransitionIntent.BranchReplaced(
                        sourceEntryId = home.id,
                        removedEntryIds = listOf(accounts.id, account.id),
                        addedEntryId = transactions.id,
                    ),
                ),
            ),
        )
        assertEquals(
            NavigationContentMotion.Replace,
            NavigationPresentationPlanner.contentMotion(
                target(41, branchTree, null),
                target(
                    42,
                    historyTree,
                    NavTransitionIntent.HistoryReplaced(
                        previousTopEntryId = transactions.id,
                        targetTopEntryId = cards.id,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `single pane branch replacement accepts an exact diff when retained source is hidden`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val transactions = entry("transactions", Transactions)
        val previousTree = project(state(home, accounts, account), singlePane)
        val targetTree = project(state(home, transactions), singlePane)

        assertEquals(
            NavigationContentMotion.Replace,
            NavigationPresentationPlanner.contentMotion(
                target(50, previousTree, null),
                target(
                    51,
                    targetTree,
                    NavTransitionIntent.BranchReplaced(
                        sourceEntryId = home.id,
                        removedEntryIds = listOf(accounts.id, account.id),
                        addedEntryId = transactions.id,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `expanded branch replacement allows retained source to be displaced by target root`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val transactions = entry("transactions", Transactions)
        val previousState = state(home, accounts, account)
        val reduction = NavReducer.reduce(
            previousState,
            NavAction.navigateFrom(accounts.id, transactions),
        ) as NavReduction.Changed
        val previousTree = project(previousState, expandedPane)
        val targetTree = project(reduction.state, expandedPane)

        assertEquals(listOf(home, accounts), previousTree.visibleContentEntries())
        assertEquals(listOf(account), previousTree.modalLayers.map { it.entry })
        assertEquals(transactions, targetTree.root.entry)
        assertTrue(targetTree.nestedSlots.isEmpty())
        assertEquals(
            NavTransitionIntent.BranchReplaced(
                sourceEntryId = accounts.id,
                removedEntryIds = listOf(account.id),
                addedEntryId = transactions.id,
            ),
            reduction.transition,
        )
        assertEquals(
            NavigationContentMotion.Replace,
            NavigationPresentationPlanner.contentMotion(
                target(55, previousTree, null),
                target(56, targetTree, reduction.transition),
            ),
        )
    }

    @Test
    fun `displaced branch rejects stale visible source and removed suffix`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val transactions = entry("transactions", Transactions)
        val previousTree = project(state(home, accounts, account), expandedPane)
        val targetTree = project(state(home, accounts, transactions), expandedPane)
        val incompatibleIntents = listOf(
            NavTransitionIntent.BranchReplaced(
                sourceEntryId = home.id,
                removedEntryIds = listOf(account.id),
                addedEntryId = transactions.id,
            ),
            NavTransitionIntent.BranchReplaced(
                sourceEntryId = accounts.id,
                removedEntryIds = listOf(home.id, account.id),
                addedEntryId = transactions.id,
            ),
            NavTransitionIntent.BranchReplaced(
                sourceEntryId = accounts.id,
                removedEntryIds = emptyList(),
                addedEntryId = transactions.id,
            ),
        )

        incompatibleIntents.forEach { intent ->
            assertEquals(
                "Expected no motion for $intent",
                NavigationContentMotion.None,
                NavigationPresentationPlanner.contentMotion(
                    target(56, previousTree, null),
                    target(57, targetTree, intent),
                ),
            )
        }
    }

    @Test
    fun `visible but stale intent IDs cannot masquerade as exact content changes`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val details = entry("details", AccountDetails(accountId = 1))
        val transactions = entry("transactions", Transactions)
        val replacementHome = entry("replacement-home", Home)
        val cards = entry("cards", Cards)
        val homeAccountsTree = project(state(home, accounts), expandedPane)
        val detailsTree = project(state(home, accounts, details), expandedPane)
        val branchTree = project(state(home, transactions), expandedPane)
        val historyTree = project(state(replacementHome, cards), expandedPane)

        // Home is visible, but Accounts is the logical/visible top that initiated this push.
        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(
                target(60, homeAccountsTree, null),
                target(61, detailsTree, NavTransitionIntent.Pushed(home.id, details.id)),
            ),
        )

        // Home is visible after the pop, but Accounts is the exact revealed top.
        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(
                target(61, detailsTree, null),
                target(62, homeAccountsTree, NavTransitionIntent.Popped(details.id, home.id)),
            ),
        )

        // A branch replacement must identify its retained anchor, not merely the diff entries.
        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(
                target(62, homeAccountsTree, null),
                target(
                    63,
                    branchTree,
                    NavTransitionIntent.BranchReplaced(
                        sourceEntryId = EntryId("stale-anchor"),
                        removedEntryIds = listOf(accounts.id),
                        addedEntryId = transactions.id,
                    ),
                ),
            ),
        )

        // History metadata names exact old/new tops, not any entries visible in either tree.
        assertEquals(
            NavigationContentMotion.None,
            NavigationPresentationPlanner.contentMotion(
                target(63, homeAccountsTree, null),
                target(
                    64,
                    historyTree,
                    NavTransitionIntent.HistoryReplaced(
                        previousTopEntryId = home.id,
                        targetTopEntryId = replacementHome.id,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `content render identity is exactly entry ID across equal routes and reprojection`() {
        val firstHome = entry("home-1", Home)
        val secondHome = entry("home-2", Home)
        val accounts = entry("accounts", Accounts)
        val equalRoutesTree = project(state(firstHome, secondHome), expandedPane)

        assertEquals(firstHome.id, equalRoutesTree.root.renderIdentity)
        assertEquals(secondHome.id, equalRoutesTree.nestedSlots.single().renderIdentity)
        assertNotEquals(
            equalRoutesTree.root.renderIdentity,
            equalRoutesTree.nestedSlots.single().renderIdentity,
        )

        val navState = state(firstHome, accounts)
        val single = project(navState, singlePane)
        val expanded = project(navState, expandedPane)
        assertEquals(accounts.id, single.root.renderIdentity)
        assertEquals(accounts.id, expanded.nestedSlots.single().renderIdentity)
    }

    @Test
    fun `outgoing expanded tree stays structurally independent from details target`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val details = entry("details", AccountDetails(accountId = 1))
        val previousTree = project(state(home, accounts, account), expandedPane)
        val targetTree = project(state(home, accounts, account, details), expandedPane)
        val previous = target(50, previousTree, null)
        val target = target(
            51,
            targetTree,
            NavTransitionIntent.Pushed(account.id, details.id),
        )

        assertNotSame(previous.tree, target.tree)
        assertEquals(home, previous.tree.root.entry)
        assertEquals(listOf(accounts), previous.tree.nestedSlots.map { it.entry })
        assertEquals(listOf(ModalLayer(account, accounts.id)), previous.tree.modalLayers)
        assertEquals(details, target.tree.root.entry)
        assertTrue(target.tree.nestedSlots.isEmpty())
        assertTrue(target.tree.modalLayers.isEmpty())
        assertEquals(
            NavigationContentMotion.Push,
            NavigationPresentationPlanner.contentMotion(previous, target),
        )

        // Reading the target must not collapse the outgoing tree retained by the transition host.
        target.tree.root
        assertEquals(home, previous.tree.root.entry)
        assertEquals(accounts, previous.tree.nestedSlots.single().entry)
        assertEquals(account, previous.tree.modalLayers.single().entry)
    }

    private fun target(
        revision: Long,
        tree: NavigationRenderTree,
        transition: NavTransitionIntent?,
    ): NavigationRenderTarget = NavigationRenderTarget(
        navigationRevision = revision,
        tree = tree,
        transitionIntent = transition,
    )

    private fun project(
        navState: NavState,
        policy: NavigationLayoutPolicy,
    ): NavigationRenderTree = when (val result = NavProjector.project(navState, policy)) {
        is NavProjectionResult.Success -> result.tree
        is NavProjectionResult.Failure -> error("Invalid projection fixture: ${result.problem}")
    }

    private fun state(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> error("Invalid state fixture: ${result.problems}")
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private fun NavigationRenderTree.visibleContentEntries(): List<BackStackEntry> = buildList {
        add(root.entry)
        nestedSlots.forEach { slot -> add(slot.entry) }
    }

    private companion object {
        val singlePane = NavigationLayoutPolicy {
            ContentPlacementDecision.root()
        }

        val expandedPane = NavigationLayoutPolicy { request ->
            val current = request.currentContent
            if (current.entry.route is Home) {
                ContentPlacementDecision.childOf(current.entry.id)
            } else {
                ContentPlacementDecision.root()
            }
        }
    }
}
