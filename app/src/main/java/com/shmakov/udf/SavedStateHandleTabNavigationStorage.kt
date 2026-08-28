package com.shmakov.udf

import androidx.lifecycle.SavedStateHandle
import com.shmakov.udf.navigation.RouteCodec
import com.shmakov.udf.navigation.TabNavigationSnapshotProblem
import com.shmakov.udf.navigation.TabNavigationSnapshotResult
import com.shmakov.udf.navigation.TabNavigationState
import java.util.Collections

/** A complete tab graph snapshot could not be prepared for Android saved state. */
internal sealed class TabNavigationSaveProblem {
    data class Snapshot(
        val problem: TabNavigationSnapshotProblem,
    ) : TabNavigationSaveProblem()

    data class SavedStateAccess(
        val code: String,
        val message: String,
    ) : TabNavigationSaveProblem()

    data class Unexpected(
        val code: String,
        val message: String,
    ) : TabNavigationSaveProblem()
}

/** Result of atomically replacing the one saved tab-navigation payload. */
internal sealed class TabNavigationSaveResult {
    object Saved : TabNavigationSaveResult()

    class Failed(
        problems: List<TabNavigationSaveProblem>,
    ) : TabNavigationSaveResult() {
        val problems: List<TabNavigationSaveProblem> = immutableTabStorageListCopy(problems)

        override fun equals(other: Any?): Boolean =
            this === other || other is Failed && problems == other.problems

        override fun hashCode(): Int = problems.hashCode()

        override fun toString(): String = "Failed(problems=$problems)"
    }
}

/** A saved tab-navigation payload was present but could not be restored safely. */
internal sealed class TabNavigationRestoreProblem {
    data class InvalidEnvelope(
        val problem: TabNavigationSnapshotEnvelopeProblem,
    ) : TabNavigationRestoreProblem()

    data class InvalidSnapshot(
        val problem: TabNavigationSnapshotProblem,
    ) : TabNavigationRestoreProblem()

    data class SavedStateAccess(
        val code: String,
        val message: String,
    ) : TabNavigationRestoreProblem()

    data class Unexpected(
        val code: String,
        val message: String,
    ) : TabNavigationRestoreProblem()
}

/** Result of reading and validating the one saved tab-navigation payload. */
internal sealed class TabNavigationRestoreResult {
    object Missing : TabNavigationRestoreResult()

    data class Restored(
        val state: TabNavigationState,
    ) : TabNavigationRestoreResult()

    class Rejected(
        problems: List<TabNavigationRestoreProblem>,
    ) : TabNavigationRestoreResult() {
        val problems: List<TabNavigationRestoreProblem> =
            immutableTabStorageListCopy(problems)

        override fun equals(other: Any?): Boolean =
            this === other || other is Rejected && problems == other.problems

        override fun hashCode(): Int = problems.hashCode()

        override fun toString(): String = "Rejected(problems=$problems)"
    }
}

/**
 * One-key [SavedStateHandle] storage adapter for a complete [TabNavigationState].
 *
 * This class does not own observable state, choose a fallback graph, dispatch actions, or persist
 * transition metadata. The same application-owned [routeCodec] is used for save and restore.
 */
internal class SavedStateHandleTabNavigationStorage(
    private val savedStateHandle: SavedStateHandle,
    private val routeCodec: RouteCodec,
) {
    fun save(state: TabNavigationState): TabNavigationSaveResult {
        val snapshot = try {
            state.toSnapshot(routeCodec)
        } catch (exception: Exception) {
            clearStalePayload()
            return saveUnexpectedFailure(SNAPSHOT_EXCEPTION_CODE, exception)
        }

        val payload = when (snapshot) {
            is TabNavigationSnapshotResult.Success -> try {
                TabNavigationSnapshotEnvelopeCodec.encode(snapshot.value)
            } catch (exception: Exception) {
                clearStalePayload()
                return saveUnexpectedFailure(ENVELOPE_ENCODE_EXCEPTION_CODE, exception)
            }
            is TabNavigationSnapshotResult.Failure -> {
                clearStalePayload()
                return TabNavigationSaveResult.Failed(
                    snapshot.problems.map(TabNavigationSaveProblem::Snapshot),
                )
            }
        }

        return try {
            savedStateHandle[TAB_NAVIGATION_STATE_KEY] = ArrayList(payload)
            TabNavigationSaveResult.Saved
        } catch (exception: Exception) {
            clearStalePayload()
            saveAccessFailure(SAVED_STATE_WRITE_EXCEPTION_CODE, exception)
        }
    }

    fun restore(): TabNavigationRestoreResult {
        val hasPayload = try {
            savedStateHandle.contains(TAB_NAVIGATION_STATE_KEY)
        } catch (exception: Exception) {
            clearStalePayload()
            return restoreAccessFailure(SAVED_STATE_READ_EXCEPTION_CODE, exception)
        }
        if (!hasPayload) return TabNavigationRestoreResult.Missing

        val rawPayload = try {
            savedStateHandle.get<Any?>(TAB_NAVIGATION_STATE_KEY)
        } catch (exception: Exception) {
            clearStalePayload()
            return restoreAccessFailure(SAVED_STATE_READ_EXCEPTION_CODE, exception)
        }
        val decoded = try {
            TabNavigationSnapshotEnvelopeCodec.decode(rawPayload)
        } catch (exception: Exception) {
            clearStalePayload()
            return restoreUnexpectedFailure(ENVELOPE_DECODE_EXCEPTION_CODE, exception)
        }
        val snapshot = when (decoded) {
            is TabNavigationSnapshotEnvelopeDecodeResult.Decoded -> decoded.snapshot
            is TabNavigationSnapshotEnvelopeDecodeResult.Rejected -> {
                clearStalePayload()
                return TabNavigationRestoreResult.Rejected(
                    decoded.problems.map(TabNavigationRestoreProblem::InvalidEnvelope),
                )
            }
        }

        val restored = try {
            TabNavigationState.restore(snapshot, routeCodec)
        } catch (exception: Exception) {
            clearStalePayload()
            return restoreUnexpectedFailure(SNAPSHOT_RESTORE_EXCEPTION_CODE, exception)
        }
        return when (restored) {
            is TabNavigationSnapshotResult.Success ->
                TabNavigationRestoreResult.Restored(restored.value)
            is TabNavigationSnapshotResult.Failure -> {
                clearStalePayload()
                TabNavigationRestoreResult.Rejected(
                    restored.problems.map(TabNavigationRestoreProblem::InvalidSnapshot),
                )
            }
        }
    }

    private fun clearStalePayload() {
        try {
            savedStateHandle.remove<Any?>(TAB_NAVIGATION_STATE_KEY)
        } catch (_: Exception) {
            // The original typed failure remains the useful result; cleanup is best effort.
        }
    }

    private fun saveAccessFailure(
        code: String,
        exception: Exception,
    ): TabNavigationSaveResult = TabNavigationSaveResult.Failed(
        listOf(
            TabNavigationSaveProblem.SavedStateAccess(
                code = code,
                message = exception.persistenceMessage(),
            ),
        ),
    )

    private fun saveUnexpectedFailure(
        code: String,
        exception: Exception,
    ): TabNavigationSaveResult = TabNavigationSaveResult.Failed(
        listOf(
            TabNavigationSaveProblem.Unexpected(
                code = code,
                message = exception.persistenceMessage(),
            ),
        ),
    )

    private fun restoreAccessFailure(
        code: String,
        exception: Exception,
    ): TabNavigationRestoreResult = TabNavigationRestoreResult.Rejected(
        listOf(
            TabNavigationRestoreProblem.SavedStateAccess(
                code = code,
                message = exception.persistenceMessage(),
            ),
        ),
    )

    private fun restoreUnexpectedFailure(
        code: String,
        exception: Exception,
    ): TabNavigationRestoreResult = TabNavigationRestoreResult.Rejected(
        listOf(
            TabNavigationRestoreProblem.Unexpected(
                code = code,
                message = exception.persistenceMessage(),
            ),
        ),
    )

    private fun Exception.persistenceMessage(): String = message ?: javaClass.name

    private companion object {
        const val TAB_NAVIGATION_STATE_KEY = "com.shmakov.udf.saved-tab-navigation-state"
        const val SNAPSHOT_EXCEPTION_CODE = "snapshot_exception"
        const val ENVELOPE_ENCODE_EXCEPTION_CODE = "envelope_encode_exception"
        const val ENVELOPE_DECODE_EXCEPTION_CODE = "envelope_decode_exception"
        const val SNAPSHOT_RESTORE_EXCEPTION_CODE = "snapshot_restore_exception"
        const val SAVED_STATE_READ_EXCEPTION_CODE = "saved_state_read_exception"
        const val SAVED_STATE_WRITE_EXCEPTION_CODE = "saved_state_write_exception"
    }
}

private fun <T> immutableTabStorageListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))
