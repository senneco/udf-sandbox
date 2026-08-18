package com.shmakov.udf

import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavReducer
import com.shmakov.udf.navigation.NavReduction
import com.shmakov.udf.navigation.NavTransitionIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One observable application-state revision and the transient navigation intent that produced it.
 *
 * [navigationTransition] is render metadata. It is deliberately kept outside durable [AppState]
 * and is replaced only when a navigation action changes state.
 */
internal data class AppStateFrame(
    val appState: AppState,
    val navigationTransition: NavTransitionIntent?,
)

/** A linearizable owner of application state transitions. */
internal class AppStore(initialState: AppState) {
    private val dispatchLock = Any()

    private val mutableFrames = MutableStateFlow(
        AppStateFrame(
            appState = initialState,
            navigationTransition = null,
        ),
    )

    val frames: StateFlow<AppStateFrame> = mutableFrames.asStateFlow()

    /**
     * Applies [action] to the latest committed state.
     *
     * A private lock makes the read-reduce-publish operation atomic for callers from any thread.
     * An unchanged reduction deliberately publishes no new frame.
     */
    fun dispatch(action: NavAction): NavReduction = synchronized(dispatchLock) {
        val currentFrame = mutableFrames.value
        val reduction = NavReducer.reduce(currentFrame.appState.navState, action)

        if (reduction is NavReduction.Changed) {
            mutableFrames.value = AppStateFrame(
                appState = currentFrame.appState.copy(navState = reduction.state),
                navigationTransition = reduction.transition,
            )
        }

        reduction
    }
}
