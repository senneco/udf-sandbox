package com.shmakov.udf

import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.ModalLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModalPresentationPlannerContractTest {

    @Test
    fun `start presents an immutable defensive copy in exact desired order`() {
        val desired = arrayListOf(layer("a"), layer("b"))

        val state = ModalPresentationPlanner.start(
            navigationRevision = 7,
            desired = desired,
        )
        desired.clear()

        assertEquals(7, state.acceptedNavigationRevision)
        assertEquals(
            listOf(
                PresentedModalLayer.Desired(layer("a")),
                PresentedModalLayer.Desired(layer("b")),
            ),
            state.layers,
        )
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (state.layers as MutableList<PresentedModalLayer>).clear()
        }
    }

    @Test
    fun `same revision and identical desired layers return the previous state instance`() {
        val desired = listOf(layer("a"), layer("b"))
        val previous = ModalPresentationPlanner.start(
            navigationRevision = 11,
            desired = desired,
        )

        val plan = ModalPresentationPlanner.reconcile(
            previous = previous,
            navigationRevision = 11,
            desired = desired,
        )

        assertTrue(plan is ModalPresentationPlan.Ready)
        assertSame(previous, plan.state)
    }

    @Test
    fun `batch addition from A to A B C preserves every desired layer in order`() {
        val a = layer("a")
        val b = layer("b")
        val c = layer("c")
        val previous = ModalPresentationPlanner.start(1, listOf(a))

        val state = readyState(
            ModalPresentationPlanner.reconcile(
                previous = previous,
                navigationRevision = 2,
                desired = listOf(a, b, c),
            ),
        )

        assertEquals(
            listOf(
                PresentedModalLayer.Desired(a),
                PresentedModalLayer.Desired(b),
                PresentedModalLayer.Desired(c),
            ),
            state.layers,
        )
        assertEquals(2, state.acceptedNavigationRevision)
    }

    @Test
    fun `batch removal retains every missing layer with independent ordered tokens`() {
        val a = layer("a")
        val b = layer("b")
        val c = layer("c")
        val previous = ModalPresentationPlanner.start(20, listOf(a, b, c))

        val state = readyState(
            ModalPresentationPlanner.reconcile(previous, 21, listOf(a)),
        )

        assertEquals(
            listOf(
                PresentedModalLayer.Desired(a),
                PresentedModalLayer.Exiting(b, ModalExitToken(b.entry.id, 1)),
                PresentedModalLayer.Exiting(c, ModalExitToken(c.entry.id, 2)),
            ),
            state.layers,
        )
    }

    @Test
    fun `middle removal retains the old layer in its deterministic relative position`() {
        val a = layer("a")
        val b = layer("b")
        val c = layer("c")
        val previous = ModalPresentationPlanner.start(30, listOf(a, b, c))

        val state = readyState(
            ModalPresentationPlanner.reconcile(previous, 31, listOf(a, c)),
        )

        assertEquals(
            listOf(
                PresentedModalLayer.Desired(a),
                PresentedModalLayer.Exiting(b, ModalExitToken(b.entry.id, 1)),
                PresentedModalLayer.Desired(c),
            ),
            state.layers,
        )
    }

    @Test
    fun `full replacement presents new desired layers before independent old exits`() {
        val a = layer("a")
        val b = layer("b")
        val c = layer("c")
        val d = layer("d")
        val previous = ModalPresentationPlanner.start(35, listOf(a, b))

        val state = readyState(
            ModalPresentationPlanner.reconcile(previous, 36, listOf(c, d)),
        )

        assertEquals(
            listOf(
                PresentedModalLayer.Desired(c),
                PresentedModalLayer.Desired(d),
                PresentedModalLayer.Exiting(a, ModalExitToken(a.entry.id, 1)),
                PresentedModalLayer.Exiting(b, ModalExitToken(b.entry.id, 2)),
            ),
            state.layers,
        )
        val aToken = exiting(state, a.entry.id).token
        val bToken = exiting(state, b.entry.id).token
        assertNotEquals(aToken, bToken)
    }

    @Test
    fun `existing exit token survives contiguous insertion and idempotent reconcile`() {
        val a = layer("a")
        val b = layer("b")
        val c = layer("c")
        val firstExitState = readyState(
            ModalPresentationPlanner.reconcile(
                previous = ModalPresentationPlanner.start(36, listOf(a, b)),
                navigationRevision = 37,
                desired = listOf(a),
            ),
        )
        val firstExit = exiting(firstExitState, b.entry.id)

        val withInsertedDesired = readyState(
            ModalPresentationPlanner.reconcile(
                previous = firstExitState,
                navigationRevision = 38,
                desired = listOf(a, c),
            ),
        )

        assertEquals(
            listOf(
                PresentedModalLayer.Desired(a),
                PresentedModalLayer.Desired(c),
                firstExit,
            ),
            withInsertedDesired.layers,
        )
        assertEquals(firstExit.token, exiting(withInsertedDesired, b.entry.id).token)

        val idempotent = ModalPresentationPlanner.reconcile(
            previous = withInsertedDesired,
            navigationRevision = 38,
            desired = listOf(a, c),
        )
        assertTrue(idempotent is ModalPresentationPlan.Ready)
        assertSame(withInsertedDesired, idempotent.state)
        assertEquals(firstExit.token, exiting(idempotent.state, b.entry.id).token)
    }

    @Test
    fun `same revision desired set change snaps exactly without retaining exits`() {
        val a = layer("a")
        val b = layer("b")
        val c = layer("c")
        val d = layer("d")
        val previous = ModalPresentationPlanner.start(39, listOf(a, b))

        val state = readyState(
            ModalPresentationPlanner.reconcile(
                previous = previous,
                navigationRevision = 39,
                desired = listOf(c, d),
            ),
        )

        assertEquals(39, state.acceptedNavigationRevision)
        assertEquals(
            listOf(
                PresentedModalLayer.Desired(c),
                PresentedModalLayer.Desired(d),
            ),
            state.layers,
        )
        assertTrue(state.layers.none { it is PresentedModalLayer.Exiting })
    }

    @Test
    fun `exact completion releases only its layer and duplicate completion is unchanged`() {
        val a = layer("a")
        val b = layer("b")
        val c = layer("c")
        val retained = readyState(
            ModalPresentationPlanner.reconcile(
                previous = ModalPresentationPlanner.start(40, listOf(a, b, c)),
                navigationRevision = 41,
                desired = listOf(a),
            ),
        )
        val bToken = exiting(retained, b.entry.id).token
        val cToken = exiting(retained, c.entry.id).token

        val afterC = ModalPresentationPlanner.completeExit(retained, cToken)
        assertTrue(afterC is ModalExitCompletion.Applied)
        assertEquals(cToken, afterC.token)
        assertEquals(
            listOf(
                PresentedModalLayer.Desired(a),
                PresentedModalLayer.Exiting(b, bToken),
            ),
            afterC.state.layers,
        )

        val duplicateC = ModalPresentationPlanner.completeExit(afterC.state, cToken)
        assertTrue(duplicateC is ModalExitCompletion.Unchanged)
        assertSame(afterC.state, duplicateC.state)
        assertEquals(cToken, duplicateC.token)

        val afterB = ModalPresentationPlanner.completeExit(duplicateC.state, bToken)
        assertTrue(afterB is ModalExitCompletion.Applied)
        assertEquals(listOf(PresentedModalLayer.Desired(a)), afterB.state.layers)
    }

    @Test
    fun `equal routes with different entry IDs exit independently`() {
        val first = layer(id = "account-first", accountId = 7)
        val second = layer(id = "account-second", accountId = 7)
        val previous = ModalPresentationPlanner.start(50, listOf(first, second))

        val retained = readyState(
            ModalPresentationPlanner.reconcile(previous, 51, listOf(second)),
        )
        val firstExit = exiting(retained, first.entry.id)

        assertEquals(
            listOf(
                firstExit,
                PresentedModalLayer.Desired(second),
            ),
            retained.layers,
        )

        val completion = ModalPresentationPlanner.completeExit(retained, firstExit.token)
        assertTrue(completion is ModalExitCompletion.Applied)
        assertEquals(
            listOf(PresentedModalLayer.Desired(second)),
            completion.state.layers,
        )
    }

    @Test
    fun `ABA removal re-add and removal assigns a fresh token and rejects stale completion`() {
        val firstLayer = layer(id = "account", ownerId = "owner-before", accountId = 3)
        val initial = ModalPresentationPlanner.start(60, listOf(firstLayer))
        val firstExitState = readyState(
            ModalPresentationPlanner.reconcile(initial, 61, emptyList()),
        )
        val firstToken = exiting(firstExitState, firstLayer.entry.id).token

        val refreshedLayer = layer(id = "account", ownerId = "owner-after", accountId = 3)
        val readded = readyState(
            ModalPresentationPlanner.reconcile(firstExitState, 62, listOf(refreshedLayer)),
        )
        assertEquals(
            listOf(PresentedModalLayer.Desired(refreshedLayer)),
            readded.layers,
        )

        val staleWhileDesired = ModalPresentationPlanner.completeExit(readded, firstToken)
        assertTrue(staleWhileDesired is ModalExitCompletion.Unchanged)
        assertSame(readded, staleWhileDesired.state)

        val secondExitState = readyState(
            ModalPresentationPlanner.reconcile(readded, 63, emptyList()),
        )
        val secondExit = exiting(secondExitState, refreshedLayer.entry.id)
        assertEquals(firstToken.entryId, secondExit.token.entryId)
        assertTrue(secondExit.token.generation > firstToken.generation)

        val staleDuringSecondExit = ModalPresentationPlanner.completeExit(
            secondExitState,
            firstToken,
        )
        assertTrue(staleDuringSecondExit is ModalExitCompletion.Unchanged)
        assertSame(secondExitState, staleDuringSecondExit.state)

        val exactCompletion = ModalPresentationPlanner.completeExit(
            staleDuringSecondExit.state,
            secondExit.token,
        )
        assertTrue(exactCompletion is ModalExitCompletion.Applied)
        assertTrue(exactCompletion.state.layers.isEmpty())
    }

    @Test
    fun `surviving ID reorder returns a typed fallback snapped to exact desired order`() {
        val a = layer("a")
        val b = layer("b")
        val c = layer("c")
        val previous = ModalPresentationPlanner.start(70, listOf(a, b, c))

        val plan = ModalPresentationPlanner.reconcile(
            previous = previous,
            navigationRevision = 71,
            desired = listOf(a, c, b),
        )

        assertTrue(plan is ModalPresentationPlan.Fallback)
        val fallback = plan as ModalPresentationPlan.Fallback
        assertEquals(
            ModalPresentationProblem.ReorderedEntryIds(
                previousDesiredOrder = listOf(a.entry.id, b.entry.id, c.entry.id),
                targetDesiredOrder = listOf(a.entry.id, c.entry.id, b.entry.id),
            ),
            fallback.problem,
        )
        assertEquals(
            listOf(
                PresentedModalLayer.Desired(a),
                PresentedModalLayer.Desired(c),
                PresentedModalLayer.Desired(b),
            ),
            fallback.state.layers,
        )
        assertEquals(71, fallback.state.acceptedNavigationRevision)
    }

    @Test
    fun `same revision owner refresh replaces desired layer without creating an exit`() {
        val before = layer(id = "account", ownerId = "owner-before", accountId = 9)
        val previous = ModalPresentationPlanner.start(80, listOf(before))
        val after = layer(id = "account", ownerId = "owner-after", accountId = 9)

        val state = readyState(
            ModalPresentationPlanner.reconcile(previous, 80, listOf(after)),
        )

        assertEquals(80, state.acceptedNavigationRevision)
        assertEquals(listOf(PresentedModalLayer.Desired(after)), state.layers)
    }

    @Test
    fun `revision gap and rollback snap to desired and never reuse issued generations`() {
        val a = layer("a")
        val b = layer("b")
        val c = layer("c")
        val initial = ModalPresentationPlanner.start(90, listOf(a, b))
        val retained = readyState(
            ModalPresentationPlanner.reconcile(initial, 91, listOf(a)),
        )
        val firstToken = exiting(retained, b.entry.id).token

        val afterGap = readyState(
            ModalPresentationPlanner.reconcile(retained, 93, listOf(a, c)),
        )
        assertEquals(
            listOf(
                PresentedModalLayer.Desired(a),
                PresentedModalLayer.Desired(c),
            ),
            afterGap.layers,
        )
        val staleAfterGap = ModalPresentationPlanner.completeExit(afterGap, firstToken)
        assertTrue(staleAfterGap is ModalExitCompletion.Unchanged)

        val secondRetained = readyState(
            ModalPresentationPlanner.reconcile(afterGap, 94, listOf(a)),
        )
        val secondToken = exiting(secondRetained, c.entry.id).token
        assertTrue(secondToken.generation > firstToken.generation)

        val afterRollback = readyState(
            ModalPresentationPlanner.reconcile(secondRetained, 92, listOf(b)),
        )
        assertEquals(92, afterRollback.acceptedNavigationRevision)
        assertEquals(listOf(PresentedModalLayer.Desired(b)), afterRollback.layers)
        val staleAfterRollback = ModalPresentationPlanner.completeExit(
            afterRollback,
            secondToken,
        )
        assertTrue(staleAfterRollback is ModalExitCompletion.Unchanged)

        val afterRollbackRemoval = readyState(
            ModalPresentationPlanner.reconcile(afterRollback, 93, emptyList()),
        )
        val thirdToken = exiting(afterRollbackRemoval, b.entry.id).token
        assertTrue(thirdToken.generation > secondToken.generation)
    }

    @Test
    fun `reconcile and completion never mutate their previous state`() {
        val a = layer("a")
        val b = layer("b")
        val previous = ModalPresentationPlanner.start(100, listOf(a, b))
        val previousLayers = previous.layers.toList()

        val retained = readyState(
            ModalPresentationPlanner.reconcile(previous, 101, listOf(a)),
        )

        assertEquals(100, previous.acceptedNavigationRevision)
        assertEquals(previousLayers, previous.layers)
        assertNotEquals(previous.layers, retained.layers)

        val retainedLayers = retained.layers.toList()
        val token = exiting(retained, b.entry.id).token
        val completion = ModalPresentationPlanner.completeExit(retained, token)

        assertTrue(completion is ModalExitCompletion.Applied)
        assertEquals(retainedLayers, retained.layers)
        assertEquals(101, retained.acceptedNavigationRevision)
    }

    private fun readyState(plan: ModalPresentationPlan): ModalPresentationState {
        assertTrue("Expected Ready but was $plan", plan is ModalPresentationPlan.Ready)
        return plan.state
    }

    private fun exiting(
        state: ModalPresentationState,
        entryId: EntryId,
    ): PresentedModalLayer.Exiting = state.layers
        .filterIsInstance<PresentedModalLayer.Exiting>()
        .single { it.layer.entry.id == entryId }

    private fun layer(
        id: String,
        ownerId: String = "owner",
        accountId: Int = id.hashCode(),
    ): ModalLayer = ModalLayer(
        entry = BackStackEntry(
            id = EntryId(id),
            route = Account(accountId),
        ),
        ownerContentEntryId = EntryId(ownerId),
    )
}
