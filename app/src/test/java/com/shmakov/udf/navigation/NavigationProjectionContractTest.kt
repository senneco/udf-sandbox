package com.shmakov.udf.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationProjectionContractTest {

    @Test
    fun `projector always places the validated root without consulting policy`() {
        val home = entry("home", Home)

        val result = NavProjector.project(
            state(home),
            NavigationLayoutPolicy {
                error("Policy must not be called for the root")
            },
        )

        val tree = result.requireSuccess()
        assertEquals(ContentSlot(ContentSlotId.Root, home), tree.root)
        assertTrue(tree.nestedSlots.isEmpty())
        assertTrue(tree.modalLayers.isEmpty())
    }

    @Test
    fun `single pane projects every reference content into root`() {
        val cases = listOf(
            listOf(entry("home", Home)) to "home",
            listOf(entry("home", Home), entry("accounts", Accounts)) to "accounts",
            listOf(
                entry("home", Home),
                entry("accounts", Accounts),
                entry("details", AccountDetails(accountId = 1)),
            ) to "details",
        )

        cases.forEach { (history, expectedRootId) ->
            val tree = NavProjector.project(state(*history.toTypedArray()), singlePane)
                .requireSuccess()

            assertEquals(EntryId(expectedRootId), tree.root.entry.id)
            assertEquals(ContentSlotId.Root, tree.root.slotId)
            assertTrue(tree.nestedSlots.isEmpty())
        }
    }

    @Test
    fun `canonical histories have table-driven expectations for every layout policy`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val details = entry("details", AccountDetails(accountId = 1))

        data class ExpectedTree(
            val root: ContentSlot,
            val nestedSlots: List<ContentSlot> = emptyList(),
            val modalLayers: List<ModalLayer> = emptyList(),
        )

        data class Case(
            val history: NavState,
            val single: ExpectedTree,
            val expanded: ExpectedTree,
        )

        val rootHome = ContentSlot(ContentSlotId.Root, home)
        val rootAccounts = ContentSlot(ContentSlotId.Root, accounts)
        val rootDetails = ContentSlot(ContentSlotId.Root, details)
        val nestedAccounts = ContentSlot(ContentSlotId.ChildOf(home.id), accounts)
        val accountLayer = ModalLayer(account, accounts.id)
        val cases = listOf(
            Case(
                history = state(home),
                single = ExpectedTree(rootHome),
                expanded = ExpectedTree(rootHome),
            ),
            Case(
                history = state(home, accounts),
                single = ExpectedTree(rootAccounts),
                expanded = ExpectedTree(rootHome, nestedSlots = listOf(nestedAccounts)),
            ),
            Case(
                history = state(home, accounts, account),
                single = ExpectedTree(rootAccounts, modalLayers = listOf(accountLayer)),
                expanded = ExpectedTree(
                    root = rootHome,
                    nestedSlots = listOf(nestedAccounts),
                    modalLayers = listOf(accountLayer),
                ),
            ),
            Case(
                history = state(home, accounts, account, details),
                single = ExpectedTree(rootDetails),
                expanded = ExpectedTree(rootDetails),
            ),
        )

        cases.forEach { case ->
            listOf(
                singlePane to case.single,
                expandedPane to case.expanded,
            ).forEach { (policy, expected) ->
                val actual = NavProjector.project(case.history, policy).requireSuccess()

                assertEquals(expected.root, actual.root)
                assertEquals(expected.nestedSlots, actual.nestedSlots)
                assertEquals(expected.modalLayers, actual.modalLayers)
            }
        }
    }

    @Test
    fun `expanded policy keeps Home and places its immediate child in a nested slot`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)

        val tree = NavProjector.project(state(home, accounts), expandedPane)
            .requireSuccess()

        assertEquals(ContentSlot(ContentSlotId.Root, home), tree.root)
        assertEquals(
            listOf(ContentSlot(ContentSlotId.ChildOf(home.id), accounts)),
            tree.nestedSlots,
        )
    }

    @Test
    fun `expanded policy resets deeper content to root after Accounts`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val details = entry("details", AccountDetails(accountId = 1))

        val tree = NavProjector.project(
            state(home, accounts, account, details),
            expandedPane,
        ).requireSuccess()

        assertEquals(ContentSlot(ContentSlotId.Root, details), tree.root)
        assertTrue(tree.nestedSlots.isEmpty())
        assertTrue(tree.modalLayers.isEmpty())
    }

    @Test
    fun `root modal is visible and owned by exact root entry`() {
        val home = entry("home", Home)
        val account = entry("account", Account(accountId = 1))

        val tree = NavProjector.project(state(home, account), expandedPane)
            .requireSuccess()

        assertEquals(ContentSlot(ContentSlotId.Root, home), tree.root)
        assertEquals(listOf(ModalLayer(account, home.id)), tree.modalLayers)
    }

    @Test
    fun `account sheet belongs to Accounts in both layouts`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val navState = state(home, accounts, account)

        val single = NavProjector.project(navState, singlePane).requireSuccess()
        val expanded = NavProjector.project(navState, expandedPane).requireSuccess()

        assertEquals(accounts, single.root.entry)
        assertEquals(home, expanded.root.entry)
        assertEquals(accounts, expanded.nestedSlots.single().entry)
        assertEquals(listOf(ModalLayer(account, accounts.id)), single.modalLayers)
        assertEquals(single.modalLayers, expanded.modalLayers)
    }

    @Test
    fun `stacked equal modal routes preserve independent IDs and order`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val first = entry("account-1", Account(accountId = 7))
        val second = entry("account-2", Account(accountId = 7))

        val tree = NavProjector.project(
            state(home, accounts, first, second),
            expandedPane,
        ).requireSuccess()

        assertNotEquals(first.id, second.id)
        assertEquals(
            listOf(
                ModalLayer(first, accounts.id),
                ModalLayer(second, accounts.id),
            ),
            tree.modalLayers,
        )
    }

    @Test
    fun `a later content hides the previous modal chain`() {
        val home = entry("home", Home)
        val account = entry("account", Account(accountId = 1))
        val accounts = entry("accounts", Accounts)

        val tree = NavProjector.project(state(home, account, accounts), expandedPane)
            .requireSuccess()

        assertEquals(home, tree.root.entry)
        assertEquals(accounts, tree.nestedSlots.single().entry)
        assertTrue(tree.modalLayers.isEmpty())
    }

    @Test
    fun `only modals after the latest content are visible`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val oldModal = entry("old-modal", Account(accountId = 1))
        val details = entry("details", AccountDetails(accountId = 1))
        val currentModal = entry("current-modal", Account(accountId = 2))

        val tree = NavProjector.project(
            state(home, accounts, oldModal, details, currentModal),
            expandedPane,
        ).requireSuccess()

        assertEquals(details, tree.root.entry)
        assertEquals(listOf(ModalLayer(currentModal, details.id)), tree.modalLayers)
    }

    @Test
    fun `Back reprojects hidden sheet before dismissing it`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val details = entry("details", AccountDetails(accountId = 1))
        val detailsState = state(home, accounts, account, details)

        val detailsTree = NavProjector.project(detailsState, expandedPane).requireSuccess()
        assertEquals(details, detailsTree.root.entry)
        assertTrue(detailsTree.modalLayers.isEmpty())

        val sheetState = NavReducer.reduce(detailsState, NavAction.Pop).state
        val sheetTree = NavProjector.project(sheetState, expandedPane).requireSuccess()
        assertEquals(home, sheetTree.root.entry)
        assertEquals(accounts, sheetTree.nestedSlots.single().entry)
        assertEquals(listOf(ModalLayer(account, accounts.id)), sheetTree.modalLayers)

        val accountsState = NavReducer.reduce(sheetState, NavAction.Pop).state
        val accountsTree = NavProjector.project(accountsState, expandedPane).requireSuccess()
        assertEquals(home, accountsTree.root.entry)
        assertEquals(accounts, accountsTree.nestedSlots.single().entry)
        assertTrue(accountsTree.modalLayers.isEmpty())

        val homeState = NavReducer.reduce(accountsState, NavAction.Pop).state
        val homeTree = NavProjector.project(homeState, expandedPane).requireSuccess()
        assertEquals(home, homeTree.root.entry)
        assertTrue(homeTree.nestedSlots.isEmpty())
    }

    @Test
    fun `recursive pane hosts produce an ordered visible content path`() {
        val firstHome = entry("home-1", Home)
        val secondHome = entry("home-2", Home)
        val accounts = entry("accounts", Accounts)

        val tree = NavProjector.project(
            state(firstHome, secondHome, accounts),
            expandedPane,
        ).requireSuccess()

        assertEquals(firstHome, tree.root.entry)
        assertEquals(
            listOf(
                ContentSlot(ContentSlotId.ChildOf(firstHome.id), secondHome),
                ContentSlot(ContentSlotId.ChildOf(secondHome.id), accounts),
            ),
            tree.nestedSlots,
        )
    }

    @Test
    fun `placing into an earlier visible owner replaces its child and prunes descendants`() {
        val firstHome = entry("home-1", Home)
        val secondHome = entry("home-2", Home)
        val accounts = entry("accounts", Accounts)
        val details = entry("details", AccountDetails(accountId = 1))
        val policy = NavigationLayoutPolicy { request ->
            when (request.nextContent.id) {
                firstHome.id -> ContentPlacementDecision.root()
                secondHome.id -> ContentPlacementDecision.childOf(firstHome.id)
                accounts.id -> ContentPlacementDecision.childOf(secondHome.id)
                else -> ContentPlacementDecision.childOf(firstHome.id)
            }
        }

        val tree = NavProjector.project(
            state(firstHome, secondHome, accounts, details),
            policy,
        ).requireSuccess()

        assertEquals(firstHome, tree.root.entry)
        assertEquals(
            listOf(ContentSlot(ContentSlotId.ChildOf(firstHome.id), details)),
            tree.nestedSlots,
        )
    }

    @Test
    fun `same state and policy produce structurally equal trees`() {
        val navState = state(
            entry("home", Home),
            entry("accounts", Accounts),
            entry("account", Account(accountId = 1)),
        )

        val first = NavProjector.project(navState, expandedPane).requireSuccess()
        val second = NavProjector.project(navState, expandedPane).requireSuccess()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.toString(), second.toString())
    }

    @Test
    fun `switching policy never changes logical state or entry IDs`() {
        val navState = state(
            entry("home", Home),
            entry("accounts", Accounts),
            entry("account", Account(accountId = 1)),
        )
        val entriesBefore = navState.entries.toList()

        val single = NavProjector.project(navState, singlePane).requireSuccess()
        val expanded = NavProjector.project(navState, expandedPane).requireSuccess()

        assertEquals(entriesBefore, navState.entries)
        assertEquals(entriesBefore.map { it.id }, navState.entries.map { it.id })
        assertNotEquals(single, expanded)
    }

    @Test
    fun `equal content routes with different IDs stay distinct in a nested path`() {
        val first = entry("home-1", Home)
        val second = entry("home-2", Home)

        val tree = NavProjector.project(state(first, second), expandedPane)
            .requireSuccess()

        assertEquals(first, tree.root.entry)
        assertEquals(second, tree.nestedSlots.single().entry)
        assertNotEquals(tree.root.entry.id, tree.nestedSlots.single().entry.id)
    }

    @Test
    fun `policy sees an immutable defensive content path`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        var pathSeen: List<ContentSlot>? = null
        val policy = NavigationLayoutPolicy { request ->
            pathSeen = request.contentPath
            assertThrows(UnsupportedOperationException::class.java) {
                @Suppress("UNCHECKED_CAST")
                (request.contentPath as MutableList<ContentSlot>).clear()
            }
            ContentPlacementDecision.childOf(request.currentContent.entry.id)
        }

        val tree = NavProjector.project(state(home, accounts), policy).requireSuccess()

        assertEquals(listOf(tree.root), pathSeen)
        assertEquals(accounts, tree.nestedSlots.single().entry)
    }

    @Test
    fun `tree constructor and exposed lists make defensive unmodifiable copies`() {
        val root = ContentSlot(ContentSlotId.Root, entry("home", Home))
        val child = ContentSlot(
            ContentSlotId.ChildOf(root.entry.id),
            entry("accounts", Accounts),
        )
        val modal = ModalLayer(entry("account", Account(accountId = 1)), child.entry.id)
        val nestedSource = mutableListOf(child)
        val modalSource = mutableListOf(modal)

        val tree = NavigationRenderTree.create(root, nestedSource, modalSource)
        nestedSource.clear()
        modalSource.clear()

        assertEquals(listOf(child), tree.nestedSlots)
        assertEquals(listOf(modal), tree.modalLayers)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (tree.nestedSlots as MutableList<ContentSlot>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (tree.modalLayers as MutableList<ModalLayer>).clear()
        }
    }

    @Test
    fun `explicit policy rejection is a contextual atomic failure`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val error = LayoutPolicyError("unsupported_destination", "Accounts is disabled")
        val policy = NavigationLayoutPolicy { request ->
            if (request.nextContent.id == accounts.id) {
                ContentPlacementDecision.Reject(error)
            } else {
                ContentPlacementDecision.root()
            }
        }

        val failure = NavProjector.project(state(home, accounts), policy).requireFailure()

        assertEquals(
            NavProjectionProblem.PolicyRejected(
                index = 1,
                entry = accounts,
                error = error,
            ),
            failure.problem,
        )
    }

    @Test
    fun `a child of an invisible owner is an atomic failure`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val missingOwner = EntryId("missing")
        val policy = NavigationLayoutPolicy {
            ContentPlacementDecision.childOf(missingOwner)
        }

        val failure = NavProjector.project(state(home, accounts), policy).requireFailure()

        assertEquals(
            NavProjectionProblem.InvalidSlotOwner(
                index = 1,
                entry = accounts,
                ownerContentEntryId = missingOwner,
            ),
            failure.problem,
        )
    }

    @Test
    fun `policy exception becomes a value error without leaking Throwable`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)

        val failure = NavProjector.project(
            state(home, accounts),
            NavigationLayoutPolicy { throw IllegalStateException("boom") },
        ).requireFailure()

        val problem = failure.problem as NavProjectionProblem.PolicyFailed
        assertEquals(1, problem.index)
        assertEquals(accounts, problem.entry)
        assertEquals("policy_exception", problem.error.code)
        assertTrue(problem.error.message.contains("IllegalStateException"))
        assertTrue(problem.error.message.contains("boom"))
    }

    @Test
    fun `a failure after modal and nested work exposes no partial tree`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val details = entry("details", AccountDetails(accountId = 1))
        val policy = NavigationLayoutPolicy { request ->
            when (request.nextContent.id) {
                home.id -> ContentPlacementDecision.root()
                accounts.id -> ContentPlacementDecision.childOf(home.id)
                else -> ContentPlacementDecision.reject("unsupported_destination", "details")
            }
        }

        val result = NavProjector.project(state(home, accounts, account, details), policy)

        assertTrue(result is NavProjectionResult.Failure)
        val problem = (result as NavProjectionResult.Failure).problem
        assertEquals(
            NavProjectionProblem.PolicyRejected(
                index = 3,
                entry = details,
                error = LayoutPolicyError("unsupported_destination", "details"),
            ),
            problem,
        )
    }

    @Test
    fun `policy is called only for content entries`() {
        val home = entry("home", Home)
        val firstModal = entry("modal-1", Account(accountId = 1))
        val accounts = entry("accounts", Accounts)
        val secondModal = entry("modal-2", Account(accountId = 2))
        val calls = mutableListOf<EntryId>()

        val result = NavProjector.project(
            state(home, firstModal, accounts, secondModal),
            NavigationLayoutPolicy { request ->
                calls += request.nextContent.id
                ContentPlacementDecision.root()
            },
        )

        result.requireSuccess()
        assertEquals(listOf(accounts.id), calls)
    }

    @Test
    fun `modal-first and other malformed histories are rejected before projection`() {
        val empty = NavState.fromEntries(emptyList())
        val modalFirst = NavState.fromEntries(
            listOf(entry("account", Account(accountId = 1))),
        )
        val duplicate = entry("same", Home).let { root ->
            NavState.fromEntries(listOf(root, entry("same", Accounts)))
        }

        assertEquals(
            listOf(NavStateProblem.EmptyStack),
            (empty as NavStateCreationResult.Invalid).problems,
        )
        assertEquals(
            listOf(NavStateProblem.NonContentRoot(EntryId("account"))),
            (modalFirst as NavStateCreationResult.Invalid).problems,
        )
        assertTrue(
            (duplicate as NavStateCreationResult.Invalid).problems
                .contains(NavStateProblem.DuplicateEntryId(EntryId("same"))),
        )
    }

    private fun NavProjectionResult.requireSuccess(): NavigationRenderTree {
        assertTrue("Expected Success, got $this", this is NavProjectionResult.Success)
        return (this as NavProjectionResult.Success).tree
    }

    private fun NavProjectionResult.requireFailure(): NavProjectionResult.Failure {
        assertTrue("Expected Failure, got $this", this is NavProjectionResult.Failure)
        return this as NavProjectionResult.Failure
    }

    private fun state(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> error("Invalid fixture: ${result.problems}")
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

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
