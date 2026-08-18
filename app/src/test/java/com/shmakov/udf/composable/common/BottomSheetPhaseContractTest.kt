package com.shmakov.udf.composable.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BottomSheetPhaseContractTest {

    @Test
    fun `mixed dismiss sources emit once for the exact desired phase`() {
        var dismissRequests = 0
        val phase = BottomSheetPhase.Desired(
            onDismissRequest = { dismissRequests += 1 },
        )

        // Scrim, swipe and accessibility collapse all enter the same phase gate.
        phase.requestDismiss(currentPhase = phase)
        phase.requestDismiss(currentPhase = phase)
        phase.requestDismiss(currentPhase = phase)

        assertEquals(1, dismissRequests)
    }

    @Test
    fun `a new desired phase resets the request gate and rejects the stale phase`() {
        val requests = mutableListOf<String>()
        val first = BottomSheetPhase.Desired(
            onDismissRequest = { requests += "first" },
        )
        first.requestDismiss(currentPhase = first)

        val second = BottomSheetPhase.Desired(
            onDismissRequest = { requests += "second" },
        )
        first.requestDismiss(currentPhase = second)
        second.requestDismiss(currentPhase = second)
        second.requestDismiss(currentPhase = second)

        assertEquals(listOf("first", "second"), requests)
    }

    @Test
    fun `an exiting phase captures its callback and completes only while current`() {
        val completions = mutableListOf<String>()
        val first = BottomSheetPhase.Exiting(
            onExitFinished = { completions += "first" },
        )
        val second = BottomSheetPhase.Exiting(
            onExitFinished = { completions += "second" },
        )

        first.completeExit(currentPhase = second)
        second.completeExit(currentPhase = second)
        second.completeExit(currentPhase = second)
        first.completeExit(currentPhase = second)

        assertEquals(listOf("second"), completions)
    }

    @Test
    fun `a new phase always performs one move then settles without retry`() = runBlocking {
        var attempts = 0
        var retries = 0

        convergeBottomSheet(
            target = BottomSheetMotionTarget.Collapsed,
            snapshot = {
                BottomSheetMotionSnapshot(
                    current = BottomSheetMotionTarget.Collapsed,
                    target = BottomSheetMotionTarget.Collapsed,
                )
            },
            move = { requestedTarget ->
                assertEquals(BottomSheetMotionTarget.Collapsed, requestedTarget)
                attempts += 1
            },
            awaitRetry = { retries += 1 },
        )

        assertEquals(1, attempts)
        assertEquals(0, retries)
    }

    @Test
    fun `a canceled child mutation retries without canceling its parent`() = runBlocking {
        var attempts = 0
        var retries = 0
        var snapshot = BottomSheetMotionSnapshot(
            current = BottomSheetMotionTarget.Expanded,
            target = BottomSheetMotionTarget.Expanded,
        )

        convergeBottomSheet(
            target = BottomSheetMotionTarget.Collapsed,
            snapshot = { snapshot },
            move = { requestedTarget ->
                assertEquals(BottomSheetMotionTarget.Collapsed, requestedTarget)
                attempts += 1
                if (attempts == 1) {
                    // Material anchor churn cancels its mutation child, not the owning effect.
                    currentCoroutineContext().cancel(
                        CancellationException("anchor mutation replaced"),
                    )
                } else {
                    snapshot = BottomSheetMotionSnapshot(
                        current = BottomSheetMotionTarget.Collapsed,
                        target = BottomSheetMotionTarget.Collapsed,
                    )
                }
            },
            awaitRetry = { retries += 1 },
        )

        assertEquals(2, attempts)
        assertEquals(1, retries)
    }

    @Test
    fun `canceling the parent effect stops motion without retry or completion`() = runBlocking {
        val moveStarted = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()
        var retries = 0
        var converged = false

        val effect = launch(start = CoroutineStart.UNDISPATCHED) {
            convergeBottomSheet(
                target = BottomSheetMotionTarget.Collapsed,
                snapshot = {
                    BottomSheetMotionSnapshot(
                        current = BottomSheetMotionTarget.Expanded,
                        target = BottomSheetMotionTarget.Expanded,
                    )
                },
                move = {
                    moveStarted.complete(Unit)
                    neverCompletes.await()
                },
                awaitRetry = { retries += 1 },
            )
            converged = true
        }

        moveStarted.await()
        effect.cancel()
        effect.join()

        assertFalse(converged)
        assertEquals(0, retries)
    }

    @Test
    fun `a non cooperative stale child return cannot converge a canceled phase`() = runBlocking {
        val moveStarted = CompletableDeferred<Unit>()
        val releaseStaleMove = CompletableDeferred<Unit>()
        var snapshot = BottomSheetMotionSnapshot(
            current = BottomSheetMotionTarget.Expanded,
            target = BottomSheetMotionTarget.Expanded,
        )
        var converged = false

        val oldPhaseEffect = launch(start = CoroutineStart.UNDISPATCHED) {
            convergeBottomSheet(
                target = BottomSheetMotionTarget.Collapsed,
                snapshot = { snapshot },
                move = {
                    moveStarted.complete(Unit)
                    withContext(NonCancellable) {
                        releaseStaleMove.await()
                        snapshot = BottomSheetMotionSnapshot(
                            current = BottomSheetMotionTarget.Collapsed,
                            target = BottomSheetMotionTarget.Collapsed,
                        )
                    }
                },
                awaitRetry = {},
            )
            converged = true
        }

        moveStarted.await()
        oldPhaseEffect.cancel()
        releaseStaleMove.complete(Unit)
        oldPhaseEffect.join()

        assertFalse(converged)
    }
}
