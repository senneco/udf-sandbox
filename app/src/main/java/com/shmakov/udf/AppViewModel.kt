package com.shmakov.udf

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.shmakov.udf.deeplink.DeepLinkEntryIdFactory
import com.shmakov.udf.deeplink.DemoDeepLinkNotHandledReason
import com.shmakov.udf.deeplink.DemoDeepLinkResolution
import com.shmakov.udf.deeplink.DemoDeepLinkResolutionProblem
import com.shmakov.udf.deeplink.DemoDeepLinkResolver
import com.shmakov.udf.deeplink.FreshDeepLinkEntryIdFactory
import com.shmakov.udf.deeplink.NormalizedDemoDeepLink
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavReduction
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavUnchangedReason
import kotlinx.coroutines.flow.StateFlow

/** Activity-scoped lifecycle owner for the demo application state. */
internal class AppViewModel(
    savedStateHandle: SavedStateHandle,
    fallbackState: AppState,
    private val deepLinkEntryIdFactory: DeepLinkEntryIdFactory = FreshDeepLinkEntryIdFactory,
) : ViewModel() {
    constructor(savedStateHandle: SavedStateHandle) : this(
        savedStateHandle = savedStateHandle,
        fallbackState = demoAppState(),
    )

    private val savedNavigationStateStore = SavedNavigationStateStore(savedStateHandle)
    private val initialState = when (val restoration = savedNavigationStateStore.restore()) {
        NavigationRestoreResult.Missing,
        is NavigationRestoreResult.Rejected,
        -> fallbackState

        is NavigationRestoreResult.Restored -> fallbackState.copy(
            navState = restoration.navState,
        )
    }
    private val store = AppStore(
        initialState = initialState,
        persistNavigationState = savedNavigationStateStore::save,
    )

    init {
        // Persist even a fresh fallback so its generated entry IDs survive the first recreation.
        savedNavigationStateStore.save(initialState.navState)
    }

    val frames: StateFlow<AppStateFrame> = store.frames

    @MainThread
    fun dispatch(action: NavAction): NavReduction = store.dispatch(action)

    /** Resolves a complete target state before dispatching one atomic history replacement. */
    @MainThread
    fun handleDeepLink(rawUri: String?): DeepLinkHandlingResult =
        when (
            val resolution = DemoDeepLinkResolver.resolve(
                rawUri = rawUri,
                entryIdFactory = deepLinkEntryIdFactory,
            )
        ) {
            is DemoDeepLinkResolution.Resolved -> when (
                val reduction = store.dispatch(NavAction.replaceHistory(resolution.navState))
            ) {
                is NavReduction.Changed -> DeepLinkHandlingResult.Applied(
                    deepLink = resolution.deepLink,
                    reduction = reduction,
                )
                is NavReduction.Unchanged -> DeepLinkHandlingResult.Unchanged(
                    deepLink = resolution.deepLink,
                    reason = reduction.reason,
                )
            }
            is DemoDeepLinkResolution.NotHandled -> DeepLinkHandlingResult.NotHandled(
                resolution.reason,
            )
            is DemoDeepLinkResolution.Rejected -> DeepLinkHandlingResult.Rejected(
                resolution.problem,
            )
        }
}

internal sealed class DeepLinkHandlingResult {
    data class Applied(
        val deepLink: NormalizedDemoDeepLink,
        val reduction: NavReduction.Changed,
    ) : DeepLinkHandlingResult()

    data class Unchanged(
        val deepLink: NormalizedDemoDeepLink,
        val reason: NavUnchangedReason,
    ) : DeepLinkHandlingResult()

    data class NotHandled(
        val reason: DemoDeepLinkNotHandledReason,
    ) : DeepLinkHandlingResult()

    data class Rejected(
        val problem: DemoDeepLinkResolutionProblem,
    ) : DeepLinkHandlingResult()
}

private fun demoAppState(): AppState = AppState(
    navState = NavState.history(
        root = Home,
        Accounts,
        Account(accountId = 1),
    ),
    showInPlace = false,
)
