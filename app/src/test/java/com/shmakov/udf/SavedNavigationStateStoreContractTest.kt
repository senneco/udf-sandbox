package com.shmakov.udf

import androidx.lifecycle.SavedStateHandle
import com.shmakov.udf.navigation.ContentRoute
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.SnapshotProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedNavigationStateStoreContractTest {

    @Test
    fun `snapshot failure for unsupported route removes a previously valid payload`() {
        val handle = SavedStateHandle()
        val store = SavedNavigationStateStore(handle)
        assertTrue(store.save(NavState.startAt(Home)) is NavigationSaveResult.Saved)
        val navigationKey = handle.keys().single()
        assertTrue(handle.contains(navigationKey))

        val unsupportedState = NavState.history(Home, UnsupportedContent)
        val unsupportedEntry = unsupportedState.entries[1]
        val result = store.save(unsupportedState)

        assertTrue("Expected typed save failure, got $result", result is NavigationSaveResult.Failed)
        result as NavigationSaveResult.Failed
        val saveProblem = result.problems.single()
        assertTrue(
            "Expected snapshot problem, got $saveProblem",
            saveProblem is NavigationSaveProblem.Snapshot,
        )
        saveProblem as NavigationSaveProblem.Snapshot
        val snapshotProblem = saveProblem.problem
        assertTrue(
            "Expected contextual route encode failure, got $snapshotProblem",
            snapshotProblem is SnapshotProblem.RouteEncodeFailed,
        )
        snapshotProblem as SnapshotProblem.RouteEncodeFailed

        assertEquals(1, snapshotProblem.index)
        assertEquals(unsupportedEntry.id, snapshotProblem.entryId)
        assertEquals("unsupported_route", snapshotProblem.error.code)
        assertFalse(handle.contains(navigationKey))
        assertTrue(handle.keys().isEmpty())
    }

    private object UnsupportedContent : ContentRoute
}
