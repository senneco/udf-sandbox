package com.shmakov.udf.composable.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope

/** One renderer-local target phase for a single bottom-sheet entry. */
internal sealed class BottomSheetPhase {

    /** A visible phase accepts at most one surface dismiss request. */
    class Desired(
        private val onDismissRequest: () -> Unit,
    ) : BottomSheetPhase() {
        private var requestSent = false

        fun requestDismiss(currentPhase: BottomSheetPhase): Boolean {
            if (this !== currentPhase || requestSent) return false

            requestSent = true
            onDismissRequest()
            return true
        }
    }

    /** An exiting phase owns the exact completion callback captured for that exit. */
    class Exiting(
        private val onExitFinished: () -> Unit,
    ) : BottomSheetPhase() {
        private var completionSent = false

        fun completeExit(currentPhase: BottomSheetPhase): Boolean {
            if (this !== currentPhase || completionSent) return false

            completionSent = true
            onExitFinished()
            return true
        }
    }
}

/** Material-independent anchors used by the bottom-sheet motion boundary. */
internal enum class BottomSheetMotionTarget {
    Collapsed,
    Expanded,
}

/** The semantic current and requested anchors observed at one instant. */
internal data class BottomSheetMotionSnapshot(
    val current: BottomSheetMotionTarget,
    val target: BottomSheetMotionTarget,
) {
    fun isSettledAt(expected: BottomSheetMotionTarget): Boolean =
        current == expected && target == expected
}

/**
 * Reconciles one phase with its Material boundary.
 *
 * Every mutation attempt runs in its own supervised child. Material may cancel that child when
 * anchors or a competing gesture change, in which case the owning effect stays alive and retries.
 * Canceling the owning effect propagates cancellation and can never report convergence, including
 * when structured concurrency must first wait for a non-cooperative child to return.
 */
internal suspend fun convergeBottomSheet(
    target: BottomSheetMotionTarget,
    snapshot: () -> BottomSheetMotionSnapshot,
    move: suspend (BottomSheetMotionTarget) -> Unit,
    awaitRetry: suspend () -> Unit,
) {
    var attemptedCurrentPhase = false

    while (true) {
        currentCoroutineContext().ensureActive()

        if (attemptedCurrentPhase && snapshot().isSettledAt(target)) return

        val attemptCompleted = supervisorScope {
            val attempt = async(start = CoroutineStart.UNDISPATCHED) {
                move(target)
            }
            try {
                attempt.await()
                true
            } catch (cancellation: CancellationException) {
                currentCoroutineContext().ensureActive()
                false
            }
        }

        currentCoroutineContext().ensureActive()
        attemptedCurrentPhase = true

        if (attemptCompleted && snapshot().isSettledAt(target)) return
        awaitRetry()
    }
}
