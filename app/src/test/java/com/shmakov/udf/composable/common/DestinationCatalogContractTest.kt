package com.shmakov.udf.composable.common

import com.shmakov.udf.ModalExitToken
import com.shmakov.udf.PresentedModalLayer
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Card
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.ContentPlacementDecision
import com.shmakov.udf.navigation.ContentRoute
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.ModalEntrance
import com.shmakov.udf.navigation.ModalLayer
import com.shmakov.udf.navigation.ModalScreen
import com.shmakov.udf.navigation.NavProjectionResult
import com.shmakov.udf.navigation.NavProjector
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavigationLayoutPolicy
import com.shmakov.udf.navigation.NavigationRenderTree
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.Transaction
import com.shmakov.udf.navigation.Transactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationCatalogContractTest {

    @Test
    fun `demo catalog exhaustively binds every declared route with exact entry identity`() {
        val contentEntries = listOf(
            entry("home", Home),
            entry("accounts", Accounts),
            entry("account-details", AccountDetails(accountId = 1)),
            entry("transactions", Transactions),
            entry("transaction", Transaction(transactionId = 2)),
            entry("cards", Cards),
            entry("card", Card(cardId = 3)),
        )
        val modalEntry = entry("account", Account(accountId = 4))

        contentEntries.forEach { expectedEntry ->
            val binding = DemoDestinationCatalog.resolve(expectedEntry)

            assertTrue(
                "Expected Content for ${expectedEntry.route}, got $binding",
                binding is DestinationBinding.Content,
            )
            assertSame(expectedEntry, (binding as DestinationBinding.Content).screen.entry)
            assertEquals(expectedEntry.id, binding.screen.entry.id)
        }

        val modalBinding = DemoDestinationCatalog.resolve(modalEntry)
        assertTrue("Expected Modal for Account, got $modalBinding", modalBinding is DestinationBinding.Modal)
        assertSame(modalEntry, (modalBinding as DestinationBinding.Modal).screen.entry)
        assertEquals(modalEntry.id, modalBinding.screen.entry.id)
    }

    @Test
    fun `unknown application route is an explicit unsupported value`() {
        val custom = entry("custom", CustomContent)

        assertEquals(
            DestinationBinding.Unsupported(custom),
            DemoDestinationCatalog.resolve(custom),
        )
    }

    @Test
    fun `tree binder preserves slot ownership ordering and exact entry IDs`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val source = project(state(home, accounts, account), expandedPane)

        val result = DestinationTreeBinder.bind(source, DemoDestinationCatalog)
        assertTrue("Expected Success, got $result", result is DestinationTreeBindingResult.Success)
        val bound = (result as DestinationTreeBindingResult.Success).tree

        assertEquals(source.root, bound.root.slot)
        assertSame(home, bound.root.screen.entry)
        assertEquals(home.id, bound.root.screen.entry.id)
        assertEquals(source.nestedSlots, bound.nestedSlots.map { it.slot })
        assertSame(accounts, bound.nestedSlots.single().screen.entry)
        assertEquals(accounts.id, bound.nestedSlots.single().screen.entry.id)
        assertEquals(source.modalLayers, bound.modalLayers.map { it.layer })
        assertSame(account, bound.modalLayers.single().screen.entry)
        assertEquals(account.id, bound.modalLayers.single().screen.entry.id)
    }

    @Test
    fun `unsupported destination fails atomically without a partial bound tree`() {
        val custom = entry("custom", CustomContent)
        val source = project(state(custom), singlePane)

        assertEquals(
            DestinationTreeBindingResult.Failure(
                DestinationTreeBindingProblem.Unsupported(custom),
            ),
            DestinationTreeBinder.bind(source, DemoDestinationCatalog),
        )
    }

    @Test
    fun `catalog kind mismatch is a typed failure instead of an unsafe cast`() {
        val home = entry("home", Home)
        val modal = entry("account", Account(accountId = 1))
        val modalScreen = (DemoDestinationCatalog.resolve(modal) as DestinationBinding.Modal).screen
        val wrongCatalog = DestinationCatalog {
            DestinationBinding.Modal(modalScreen)
        }

        assertEquals(
            DestinationTreeBindingResult.Failure(
                DestinationTreeBindingProblem.KindMismatch(
                    entry = home,
                    expectedKind = DestinationKind.Content,
                    actualKind = DestinationKind.Modal,
                ),
            ),
            DestinationTreeBinder.bind(project(state(home), singlePane), wrongCatalog),
        )
    }

    @Test
    fun `catalog exception is a contextual value failure`() {
        val home = entry("home", Home)
        val throwingCatalog = DestinationCatalog {
            throw IllegalStateException("boom")
        }

        val result = DestinationTreeBinder.bind(
            project(state(home), singlePane),
            throwingCatalog,
        )
        assertTrue("Expected Failure, got $result", result is DestinationTreeBindingResult.Failure)
        val problem = (result as DestinationTreeBindingResult.Failure).problem
        assertTrue("Expected CatalogFailed, got $problem", problem is DestinationTreeBindingProblem.CatalogFailed)
        problem as DestinationTreeBindingProblem.CatalogFailed
        assertEquals(home, problem.entry)
        assertEquals(DestinationKind.Content, problem.expectedKind)
        assertEquals("catalog_exception", problem.error.code)
        assertTrue(problem.error.message.contains("IllegalStateException"))
        assertTrue(problem.error.message.contains("boom"))
    }

    @Test
    fun `bound tree collections are runtime unmodifiable`() {
        val home = entry("home", Home)
        val accounts = entry("accounts", Accounts)
        val account = entry("account", Account(accountId = 1))
        val result = DestinationTreeBinder.bind(
            project(state(home, accounts, account), expandedPane),
            DemoDestinationCatalog,
        ) as DestinationTreeBindingResult.Success

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (result.tree.nestedSlots as MutableList<BoundContentSlot>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (result.tree.modalLayers as MutableList<BoundModalLayer>).clear()
        }
    }

    @Test
    fun `presentation materializer uses current desired screens and prior exiting screens in plan order`() {
        val a = modalLayer("a", accountId = 1)
        val b = modalLayer("b", accountId = 2)
        val c = modalLayer("c", accountId = 3)
        val d = modalLayer("d", accountId = 4)
        val previousAScreen = modalScreen(a)
        val previousBScreen = modalScreen(b)
        val previousCScreen = modalScreen(c)
        val currentAScreen = modalScreen(a)
        val currentCScreen = modalScreen(c)
        val currentDScreen = modalScreen(d)
        val aExit = PresentedModalLayer.Exiting(
            layer = a,
            token = ModalExitToken(a.entry.id, generation = 1),
        )
        val bExit = PresentedModalLayer.Exiting(
            layer = b,
            token = ModalExitToken(b.entry.id, generation = 2),
        )
        val desiredC = PresentedModalLayer.Desired(c, ModalEntrance.Snap)
        val desiredD = PresentedModalLayer.Desired(d, ModalEntrance.Snap)

        val result = DestinationTreeBinder.materializePresentedModalLayers(
            layers = listOf(desiredC, desiredD, aExit, bExit),
            desiredLayers = listOf(
                BoundModalLayer(c, currentCScreen),
                BoundModalLayer(d, currentDScreen),
                BoundModalLayer(a, currentAScreen),
            ),
            acceptedLayers = listOf(
                BoundPresentedModalLayer(
                    PresentedModalLayer.Desired(a, ModalEntrance.Snap),
                    previousAScreen,
                ),
                BoundPresentedModalLayer(
                    PresentedModalLayer.Desired(b, ModalEntrance.Snap),
                    previousBScreen,
                ),
                BoundPresentedModalLayer(
                    PresentedModalLayer.Desired(c, ModalEntrance.Snap),
                    previousCScreen,
                ),
            ),
        )

        assertTrue("Expected Success, got $result", result is PresentedModalLayersBindingResult.Success)
        val layers = (result as PresentedModalLayersBindingResult.Success).layers
        assertEquals(listOf(desiredC, desiredD, aExit, bExit), layers.map { it.presentation })
        assertSame(currentCScreen, layers[0].screen)
        assertSame(currentDScreen, layers[1].screen)
        assertSame(previousAScreen, layers[2].screen)
        assertSame(previousBScreen, layers[3].screen)
    }

    @Test
    fun `missing current binding for a desired presentation is a typed failure`() {
        val desired = modalLayer("desired", accountId = 5)
        val acceptedScreen = modalScreen(desired)

        assertEquals(
            PresentedModalLayersBindingResult.Failure(
                DestinationTreeBindingProblem.MissingPresentationBinding(desired.entry),
            ),
            DestinationTreeBinder.materializePresentedModalLayers(
                layers = listOf(
                    PresentedModalLayer.Desired(desired, ModalEntrance.Snap),
                ),
                desiredLayers = emptyList(),
                acceptedLayers = listOf(
                    BoundPresentedModalLayer(
                        PresentedModalLayer.Desired(desired, ModalEntrance.Snap),
                        acceptedScreen,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `missing accepted binding for an exiting presentation is a typed failure`() {
        val exiting = modalLayer("exiting", accountId = 6)
        val currentScreen = modalScreen(exiting)
        val presentation = PresentedModalLayer.Exiting(
            layer = exiting,
            token = ModalExitToken(exiting.entry.id, generation = 1),
        )

        assertEquals(
            PresentedModalLayersBindingResult.Failure(
                DestinationTreeBindingProblem.MissingPresentationBinding(exiting.entry),
            ),
            DestinationTreeBinder.materializePresentedModalLayers(
                layers = listOf(presentation),
                desiredLayers = listOf(BoundModalLayer(exiting, currentScreen)),
                acceptedLayers = emptyList(),
            ),
        )
    }

    @Test
    fun `materialized presentation result is a defensive runtime unmodifiable copy`() {
        val desired = modalLayer("desired", accountId = 7)
        val presentation = PresentedModalLayer.Desired(desired, ModalEntrance.Snap)
        val sourcePresentations = arrayListOf<PresentedModalLayer>(presentation)
        val sourceDesired = arrayListOf(BoundModalLayer(desired, modalScreen(desired)))

        val result = DestinationTreeBinder.materializePresentedModalLayers(
            layers = sourcePresentations,
            desiredLayers = sourceDesired,
            acceptedLayers = emptyList(),
        ) as PresentedModalLayersBindingResult.Success
        sourcePresentations.clear()
        sourceDesired.clear()

        assertEquals(listOf(presentation), result.layers.map { it.presentation })
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (result.layers as MutableList<BoundPresentedModalLayer>).clear()
        }
    }

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

    private fun modalLayer(
        id: String,
        accountId: Int,
        ownerId: String = "owner",
    ): ModalLayer = ModalLayer(
        entry = entry(id, Account(accountId)),
        ownerContentEntryId = EntryId(ownerId),
    )

    private fun modalScreen(layer: ModalLayer): ModalScreen =
        (DemoDestinationCatalog.resolve(layer.entry) as DestinationBinding.Modal).screen

    private object CustomContent : ContentRoute

    private companion object {
        val singlePane = NavigationLayoutPolicy {
            ContentPlacementDecision.root()
        }

        val expandedPane = NavigationLayoutPolicy { request ->
            if (request.currentContent.entry.route is Home) {
                ContentPlacementDecision.childOf(request.currentContent.entry.id)
            } else {
                ContentPlacementDecision.root()
            }
        }
    }
}
