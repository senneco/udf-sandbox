package com.shmakov.udf

import androidx.lifecycle.SavedStateHandle
import com.shmakov.udf.navigation.DemoRouteCodec
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.SnapshotProblem
import com.shmakov.udf.navigation.SnapshotResult
import java.util.Collections

/** A navigation snapshot could not be prepared for Android saved state. */
internal sealed class NavigationSaveProblem {
    data class Snapshot(
        val problem: SnapshotProblem,
    ) : NavigationSaveProblem()

    data class SavedStateAccess(
        val code: String,
        val message: String,
    ) : NavigationSaveProblem()
}

/** Result of synchronously replacing the one saved navigation payload. */
internal sealed class NavigationSaveResult {
    object Saved : NavigationSaveResult()

    class Failed(
        problems: List<NavigationSaveProblem>,
    ) : NavigationSaveResult() {
        val problems: List<NavigationSaveProblem> = immutablePersistenceListCopy(problems)

        override fun equals(other: Any?): Boolean =
            this === other || other is Failed && problems == other.problems

        override fun hashCode(): Int = problems.hashCode()

        override fun toString(): String = "Failed(problems=$problems)"
    }
}

/** A saved navigation payload was present but could not be restored safely. */
internal sealed class NavigationRestoreProblem {
    data class InvalidEnvelope(
        val problem: NavigationSnapshotEnvelopeProblem,
    ) : NavigationRestoreProblem()

    data class InvalidSnapshot(
        val problem: SnapshotProblem,
    ) : NavigationRestoreProblem()

    data class SavedStateAccess(
        val code: String,
        val message: String,
    ) : NavigationRestoreProblem()
}

/** Result of reading and validating the saved navigation payload. */
internal sealed class NavigationRestoreResult {
    object Missing : NavigationRestoreResult()

    data class Restored(
        val navState: NavState,
    ) : NavigationRestoreResult()

    class Rejected(
        problems: List<NavigationRestoreProblem>,
    ) : NavigationRestoreResult() {
        val problems: List<NavigationRestoreProblem> = immutablePersistenceListCopy(problems)

        override fun equals(other: Any?): Boolean =
            this === other || other is Rejected && problems == other.problems

        override fun hashCode(): Int = problems.hashCode()

        override fun toString(): String = "Rejected(problems=$problems)"
    }
}

/** Android lifecycle boundary for the transport-agnostic navigation snapshot. */
internal class SavedNavigationStateStore(
    private val savedStateHandle: SavedStateHandle,
) {
    fun save(navState: NavState): NavigationSaveResult {
        val snapshot = try {
            navState.toSnapshot(DemoRouteCodec)
        } catch (exception: Exception) {
            clearStalePayload()
            return saveAccessFailure(
                code = SNAPSHOT_EXCEPTION_CODE,
                exception = exception,
            )
        }

        val encoded = when (snapshot) {
            is SnapshotResult.Success -> try {
                NavigationSnapshotEnvelopeCodec.encode(snapshot.value)
            } catch (exception: Exception) {
                clearStalePayload()
                return saveAccessFailure(
                    code = ENVELOPE_ENCODE_EXCEPTION_CODE,
                    exception = exception,
                )
            }

            is SnapshotResult.Failure -> {
                clearStalePayload()
                return NavigationSaveResult.Failed(
                    immutablePersistenceListCopy(
                        snapshot.problems.map(NavigationSaveProblem::Snapshot),
                    ),
                )
            }
        }

        return try {
            // A new ArrayList prevents later mutation of the encoder result from aliasing the
            // value owned by SavedStateHandle. It is also one Bundle-safe atomic map value.
            savedStateHandle[NAVIGATION_STATE_KEY] = ArrayList(encoded)
            NavigationSaveResult.Saved
        } catch (exception: Exception) {
            clearStalePayload()
            saveAccessFailure(
                code = SAVED_STATE_WRITE_EXCEPTION_CODE,
                exception = exception,
            )
        }
    }

    fun restore(): NavigationRestoreResult {
        val hasPayload = try {
            savedStateHandle.contains(NAVIGATION_STATE_KEY)
        } catch (exception: Exception) {
            clearStalePayload()
            return restoreAccessFailure(
                code = SAVED_STATE_READ_EXCEPTION_CODE,
                exception = exception,
            )
        }
        if (!hasPayload) return NavigationRestoreResult.Missing

        val rawPayload = try {
            // Reading as Any keeps a corrupt value's caller-side generic cast outside this path.
            savedStateHandle.get<Any?>(NAVIGATION_STATE_KEY)
        } catch (exception: Exception) {
            clearStalePayload()
            return restoreAccessFailure(
                code = SAVED_STATE_READ_EXCEPTION_CODE,
                exception = exception,
            )
        }

        val decoded = try {
            NavigationSnapshotEnvelopeCodec.decode(rawPayload)
        } catch (exception: Exception) {
            clearStalePayload()
            return restoreAccessFailure(
                code = ENVELOPE_DECODE_EXCEPTION_CODE,
                exception = exception,
            )
        }
        val snapshot = when (decoded) {
            is NavigationSnapshotEnvelopeDecodeResult.Decoded -> decoded.snapshot
            is NavigationSnapshotEnvelopeDecodeResult.Rejected -> {
                clearStalePayload()
                return NavigationRestoreResult.Rejected(
                    immutablePersistenceListCopy(
                        decoded.problems.map(NavigationRestoreProblem::InvalidEnvelope),
                    ),
                )
            }
        }

        val restored = try {
            NavState.restore(snapshot, DemoRouteCodec)
        } catch (exception: Exception) {
            clearStalePayload()
            return restoreAccessFailure(
                code = SNAPSHOT_RESTORE_EXCEPTION_CODE,
                exception = exception,
            )
        }
        return when (restored) {
            is SnapshotResult.Success -> NavigationRestoreResult.Restored(restored.value)
            is SnapshotResult.Failure -> {
                clearStalePayload()
                NavigationRestoreResult.Rejected(
                    immutablePersistenceListCopy(
                        restored.problems.map(NavigationRestoreProblem::InvalidSnapshot),
                    ),
                )
            }
        }
    }

    private fun clearStalePayload() {
        try {
            savedStateHandle.remove<Any?>(NAVIGATION_STATE_KEY)
        } catch (_: Exception) {
            // The original typed failure remains the useful result; cleanup is best effort.
        }
    }

    private fun saveAccessFailure(
        code: String,
        exception: Exception,
    ): NavigationSaveResult = NavigationSaveResult.Failed(
        listOf(
            NavigationSaveProblem.SavedStateAccess(
                code = code,
                message = exception.persistenceMessage(),
            ),
        ),
    )

    private fun restoreAccessFailure(
        code: String,
        exception: Exception,
    ): NavigationRestoreResult = NavigationRestoreResult.Rejected(
        listOf(
            NavigationRestoreProblem.SavedStateAccess(
                code = code,
                message = exception.persistenceMessage(),
            ),
        ),
    )

    private fun Exception.persistenceMessage(): String = message ?: javaClass.name

    private companion object {
        const val NAVIGATION_STATE_KEY = "com.shmakov.udf.saved-navigation-state"
        const val SNAPSHOT_EXCEPTION_CODE = "snapshot_exception"
        const val ENVELOPE_ENCODE_EXCEPTION_CODE = "envelope_encode_exception"
        const val ENVELOPE_DECODE_EXCEPTION_CODE = "envelope_decode_exception"
        const val SNAPSHOT_RESTORE_EXCEPTION_CODE = "snapshot_restore_exception"
        const val SAVED_STATE_READ_EXCEPTION_CODE = "saved_state_read_exception"
        const val SAVED_STATE_WRITE_EXCEPTION_CODE = "saved_state_write_exception"
    }
}

private fun <T> immutablePersistenceListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))
