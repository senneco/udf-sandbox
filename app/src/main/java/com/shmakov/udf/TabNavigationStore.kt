package com.shmakov.udf

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import com.shmakov.udf.navigation.RouteCodec
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabReducer
import com.shmakov.udf.navigation.TabReduction
import com.shmakov.udf.navigation.TabTransitionIntent
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Creates one complete, known-valid fallback graph only when restoration cannot supply one. */
internal fun interface TabNavigationStateFactory {
    fun create(): TabNavigationState
}

/** One observable graph revision and the process-local transition that produced it. */
internal data class TabNavigationFrame(
    val state: TabNavigationState,
    val revision: Long,
    val transition: TabTransitionIntent?,
)

/** How the store selected and, when necessary, persisted its initial graph. */
internal sealed class TabNavigationStartResult {
    /** A valid complete payload won; no fallback was created and no payload was rewritten. */
    object Restored : TabNavigationStartResult()

    /** No payload existed, so one lazy fallback was created and [saveResult] records its save. */
    data class StartedFresh(
        val saveResult: TabNavigationSaveResult,
    ) : TabNavigationStartResult()

    /**
     * A rejected payload was cleared best-effort, then one lazy fallback was created and saved.
     */
    class Recovered(
        restoreProblems: List<TabNavigationRestoreProblem>,
        val fallbackSaveResult: TabNavigationSaveResult,
    ) : TabNavigationStartResult() {
        val restoreProblems: List<TabNavigationRestoreProblem> =
            immutableTabNavigationStoreListCopy(restoreProblems)

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is Recovered &&
                restoreProblems == other.restoreProblems &&
                fallbackSaveResult == other.fallbackSaveResult

        override fun hashCode(): Int =
            31 * restoreProblems.hashCode() + fallbackSaveResult.hashCode()

        override fun toString(): String =
            "Recovered(restoreProblems=$restoreProblems, " +
                "fallbackSaveResult=$fallbackSaveResult)"
    }
}

/** One reducer outcome plus persistence evidence when the graph changed. */
internal sealed class TabNavigationDispatchResult {
    /** The valid reduction was saved before publication; a failure does not roll it back. */
    data class Changed(
        val reduction: TabReduction.Changed,
        val saveResult: TabNavigationSaveResult,
    ) : TabNavigationDispatchResult()

    /** No save or frame publication occurred; the previously visible frame remains unchanged. */
    data class Unchanged(
        val reduction: TabReduction.Unchanged,
    ) : TabNavigationDispatchResult()
}

/**
 * The single process-local owner of a complete tab-navigation graph.
 *
 * An application normally keeps this store inside its own ViewModel. Construction and [dispatch]
 * are main-thread-only because the composed [SavedStateHandle] storage has that AndroidX contract.
 * A changed graph is saved synchronously before its frame becomes observable. A typed save failure
 * is returned to the caller but does not roll back a valid in-memory reducer result.
 */
internal class TabNavigationStore @MainThread constructor(
    savedStateHandle: SavedStateHandle,
    routeCodec: RouteCodec,
    fallbackStateFactory: TabNavigationStateFactory,
) {
    private var dispatchInProgress = false
    private val storage = SavedStateHandleTabNavigationStorage(savedStateHandle, routeCodec)
    private val startup = start(storage, fallbackStateFactory)

    val startResult: TabNavigationStartResult = startup.result

    private val mutableFrames = MutableStateFlow(
        TabNavigationFrame(
            state = startup.state,
            revision = 0L,
            transition = null,
        ),
    )

    val frames: StateFlow<TabNavigationFrame> = mutableFrames.asStateFlow()

    /**
     * Reduces the latest graph, saves a complete changed graph, then publishes one frame.
     *
     * An unchanged reduction performs no save and publishes nothing, preserving the exact frame
     * instance and its last transition metadata. Dispatch is not reentrant: reducers, codecs, and
     * persistence callbacks must return before another action can be dispatched.
     */
    @MainThread
    fun dispatch(action: TabAction): TabNavigationDispatchResult {
        check(!dispatchInProgress) { REENTRANT_DISPATCH_MESSAGE }
        dispatchInProgress = true
        return try {
            val currentFrame = mutableFrames.value
            when (val reduction = TabReducer.reduce(currentFrame.state, action)) {
                is TabReduction.Changed -> {
                    val saveResult = storage.save(reduction.state)
                    mutableFrames.value = TabNavigationFrame(
                        state = reduction.state,
                        revision = currentFrame.revision + 1L,
                        transition = reduction.transition,
                    )
                    TabNavigationDispatchResult.Changed(reduction, saveResult)
                }

                is TabReduction.Unchanged -> TabNavigationDispatchResult.Unchanged(reduction)
            }
        } finally {
            dispatchInProgress = false
        }
    }

    private companion object {
        const val REENTRANT_DISPATCH_MESSAGE =
            "Reentrant TabNavigationStore.dispatch is not supported."

        fun start(
            storage: SavedStateHandleTabNavigationStorage,
            fallbackStateFactory: TabNavigationStateFactory,
        ): Startup = when (val restoration = storage.restore()) {
            is TabNavigationRestoreResult.Restored -> Startup(
                state = restoration.state,
                result = TabNavigationStartResult.Restored,
            )

            TabNavigationRestoreResult.Missing -> {
                val fallback = fallbackStateFactory.create()
                Startup(
                    state = fallback,
                    result = TabNavigationStartResult.StartedFresh(storage.save(fallback)),
                )
            }

            is TabNavigationRestoreResult.Rejected -> {
                val fallback = fallbackStateFactory.create()
                Startup(
                    state = fallback,
                    result = TabNavigationStartResult.Recovered(
                        restoreProblems = restoration.problems,
                        fallbackSaveResult = storage.save(fallback),
                    ),
                )
            }
        }
    }
}

private data class Startup(
    val state: TabNavigationState,
    val result: TabNavigationStartResult,
)

private fun <T> immutableTabNavigationStoreListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))
