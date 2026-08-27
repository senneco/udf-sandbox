package com.shmakov.udf

import androidx.lifecycle.SavedStateHandle
import com.shmakov.udf.deeplink.DeepLinkEntryIdFactory
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavReduction
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.NavUnchangedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppViewModelDeepLinkContractTest {

    @Test
    fun `valid deep link atomically replaces history through the store boundary`() {
        val initial = AppState(NavState.startAt(Home), showInPlace = true)
        val oldTopId = initial.navState.top.id
        val handle = SavedStateHandle()
        val expectedIds = listOf("deep-home", "deep-accounts", "deep-account", "deep-details")
        val viewModel = AppViewModel(
            savedStateHandle = handle,
            fallbackState = initial,
            deepLinkEntryIdFactory = sequentialFactory(expectedIds),
        )

        val handling = viewModel.handleDeepLink("udf-sandbox://accounts/42/details")

        assertTrue(handling is DeepLinkHandlingResult.Applied)
        handling as DeepLinkHandlingResult.Applied
        val appliedReduction: NavReduction.Changed = handling.reduction
        val frame = viewModel.frames.value
        assertSame(appliedReduction.state, frame.appState.navState)
        assertEquals(1L, frame.navigationRevision)
        assertEquals(
            listOf(Home, Accounts, Account(42), AccountDetails(42)),
            frame.appState.navState.entries.map(BackStackEntry::route),
        )
        assertEquals(expectedIds, frame.appState.navState.entries.map { it.id.value })
        assertEquals(
            NavTransitionIntent.HistoryReplaced(oldTopId, EntryId("deep-details")),
            frame.navigationTransition,
        )
        assertTrue(frame.appState.showInPlace)
        assertEquals(frame.appState.navState, restored(handle))
    }

    @Test
    fun `repeated link creates fresh IDs and one frame per complete replacement`() {
        val ids = (0..7).map { index -> "deep-$index" }
        val viewModel = AppViewModel(
            savedStateHandle = SavedStateHandle(),
            fallbackState = AppState(NavState.startAt(Home), showInPlace = false),
            deepLinkEntryIdFactory = sequentialFactory(ids),
        )

        viewModel.handleDeepLink("udf-sandbox://accounts/42/details")
        val first = viewModel.frames.value
        viewModel.handleDeepLink("udf-sandbox://accounts/42/details")
        val second = viewModel.frames.value

        assertEquals(1L, first.navigationRevision)
        assertEquals(2L, second.navigationRevision)
        assertTrue(
            first.appState.navState.entries.map(BackStackEntry::id).toSet()
                .intersect(second.appState.navState.entries.map(BackStackEntry::id).toSet())
                .isEmpty(),
        )
        assertEquals(
            NavTransitionIntent.HistoryReplaced(
                previousTopEntryId = first.appState.navState.top.id,
                targetTopEntryId = second.appState.navState.top.id,
            ),
            second.navigationTransition,
        )
    }

    @Test
    fun `invalid missing and foreign links preserve the exact frame and saved state`() {
        val handle = SavedStateHandle()
        val viewModel = AppViewModel(
            savedStateHandle = handle,
            fallbackState = AppState(NavState.startAt(Home), showInPlace = false),
            deepLinkEntryIdFactory = DeepLinkEntryIdFactory {
                throw AssertionError("Invalid links must not materialize entry IDs")
            },
        )
        val originalFrame = viewModel.frames.value
        val originalPayload = savedPayload(handle)
        val cases = listOf(
            null,
            "udf-sandbox://accounts/details",
            "udf-sandbox://accounts/2147483648/details",
            "https://accounts/42/details",
        )

        cases.forEach { rawUri ->
            val result = viewModel.handleDeepLink(rawUri)

            assertTrue(result !is DeepLinkHandlingResult.Applied)
            assertSame(originalFrame, viewModel.frames.value)
            assertEquals(originalPayload, savedPayload(handle))
        }
    }

    @Test
    fun `restored hydrated history keeps exact IDs and clears process local transition`() {
        val firstHandle = SavedStateHandle()
        val first = AppViewModel(
            savedStateHandle = firstHandle,
            fallbackState = AppState(NavState.startAt(Home), showInPlace = true),
            deepLinkEntryIdFactory = sequentialFactory(listOf("h", "a", "m", "d")),
        )
        first.handleDeepLink("udf-sandbox://accounts/42/details")
        val hydrated = first.frames.value.appState.navState
        val key = firstHandle.keys().single()
        val copiedPayload = ArrayList(checkNotNull(firstHandle.get<ArrayList<String>>(key)))

        val recreated = AppViewModel(
            savedStateHandle = SavedStateHandle(mapOf(key to copiedPayload)),
            fallbackState = AppState(NavState.startAt(Home), showInPlace = false),
        )
        val restoredFrame = recreated.frames.value

        assertEquals(hydrated.entries, restoredFrame.appState.navState.entries)
        assertEquals(0L, restoredFrame.navigationRevision)
        assertNull(restoredFrame.navigationTransition)
    }

    @Test
    fun `recognized link reports reducer rejection without claiming it was applied`() {
        val sharedId = EntryId("shared")
        val initial = AppState(
            navState = validState(BackStackEntry(sharedId, Accounts)),
            showInPlace = false,
        )
        val handle = SavedStateHandle()
        val viewModel = AppViewModel(
            savedStateHandle = handle,
            fallbackState = initial,
            deepLinkEntryIdFactory = sequentialFactory(
                listOf("shared", "accounts", "account", "details"),
            ),
        )
        val originalFrame = viewModel.frames.value

        val result = viewModel.handleDeepLink("udf-sandbox://accounts/42/details")

        assertTrue(result is DeepLinkHandlingResult.Unchanged)
        result as DeepLinkHandlingResult.Unchanged
        assertEquals(
            NavUnchangedReason.EntryIdentityRebound(
                entryId = sharedId,
                previousRoute = Accounts,
                targetRoute = Home,
            ),
            result.reason,
        )
        assertSame(originalFrame, viewModel.frames.value)
        assertEquals(initial.navState, restored(handle))
    }

    private fun sequentialFactory(ids: List<String>): DeepLinkEntryIdFactory {
        val remaining = ArrayDeque(ids)
        return DeepLinkEntryIdFactory { EntryId(remaining.removeFirst()) }
    }

    private fun restored(handle: SavedStateHandle): NavState =
        when (val result = SavedNavigationStateStore(handle).restore()) {
            is NavigationRestoreResult.Restored -> result.navState
            NavigationRestoreResult.Missing -> throw AssertionError("Expected restored state")
            is NavigationRestoreResult.Rejected -> throw AssertionError(
                "Expected restored state, got ${result.problems}",
            )
        }

    private fun validState(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid ->
                throw AssertionError("Invalid test state: ${result.problems}")
        }

    private fun savedPayload(handle: SavedStateHandle): ArrayList<String> {
        val key = handle.keys().single()
        return ArrayList(checkNotNull(handle.get<ArrayList<String>>(key)))
    }
}
