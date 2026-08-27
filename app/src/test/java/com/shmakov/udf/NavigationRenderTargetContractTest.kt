package com.shmakov.udf

import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.ContentSlot
import com.shmakov.udf.navigation.ContentSlotId
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.ModalLayer
import com.shmakov.udf.navigation.NavigationRenderTree
import com.shmakov.udf.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRenderTargetContractTest {

    @Test
    fun `history entry IDs are ordered defensive and runtime unmodifiable`() {
        val root = entry("root", Home)
        val hidden = entry("hidden", Accounts)
        val source = arrayListOf(hidden.id, root.id)
        val target = target(
            historyEntryIds = source,
            tree = tree(root),
        )

        source.reverse()
        source += EntryId("late-mutation")

        assertEquals(listOf(hidden.id, root.id), target.historyEntryIds)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (target.historyEntryIds as MutableList<EntryId>) += EntryId("external-mutation")
        }
        assertEquals(listOf(hidden.id, root.id), target.historyEntryIds)
    }

    @Test
    fun `empty blank and duplicate history entry IDs fail fast with context`() {
        val root = entry("root", Home)
        val tree = tree(root)

        assertInvalidHistory(
            historyEntryIds = emptyList(),
            tree = tree,
            expectedMessageParts = arrayOf("historyEntryIds", "empty"),
        )
        assertInvalidHistory(
            historyEntryIds = listOf(root.id, EntryId("  ")),
            tree = tree,
            expectedMessageParts = arrayOf("historyEntryIds", "blank", "1"),
        )
        assertInvalidHistory(
            historyEntryIds = listOf(root.id, root.id),
            tree = tree,
            expectedMessageParts = arrayOf("historyEntryIds", "duplicate", root.id.value),
        )
    }

    @Test
    fun `every visible tree entry ID must belong to history`() {
        val root = entry("visible-root", Home)
        val nested = entry("visible-nested", Accounts)
        val modal = entry("visible-modal", Account(accountId = 1))
        val tree = tree(root, nested, modal)
        val allVisibleIds = listOf(root.id, nested.id, modal.id)

        allVisibleIds.forEach { missingId ->
            assertInvalidHistory(
                historyEntryIds = allVisibleIds.filterNot { it == missingId },
                tree = tree,
                expectedMessageParts = arrayOf(
                    "visible",
                    missingId.value,
                    "historyEntryIds",
                ),
            )
        }
    }

    @Test
    fun `duplicate visible entry IDs across root nested and modal fail fast with index`() {
        val repeatedId = EntryId("repeated-visible")
        val root = BackStackEntry(repeatedId, Home)
        val nestedWithRootId = BackStackEntry(repeatedId, Accounts)
        val modalWithRootId = BackStackEntry(repeatedId, Account(accountId = 9))
        val malformedTrees = listOf(
            NavigationRenderTree.create(
                root = ContentSlot(ContentSlotId.Root, root),
                nestedSlots = listOf(
                    ContentSlot(ContentSlotId.ChildOf(root.id), nestedWithRootId),
                ),
                modalLayers = emptyList(),
            ),
            NavigationRenderTree.create(
                root = ContentSlot(ContentSlotId.Root, root),
                nestedSlots = emptyList(),
                modalLayers = listOf(ModalLayer(modalWithRootId, root.id)),
            ),
        )

        malformedTrees.forEach { malformedTree ->
            assertInvalidHistory(
                // Membership alone is valid and unique; the malformed tree repeats the ID.
                historyEntryIds = listOf(repeatedId),
                tree = malformedTree,
                expectedMessageParts = arrayOf(
                    "visible",
                    "duplicate",
                    repeatedId.value,
                    "1",
                ),
            )
        }
    }

    @Test
    fun `target equality and hash code include ordered history entry IDs`() {
        val root = entry("root", Home)
        val firstHidden = EntryId("first-hidden")
        val secondHidden = EntryId("second-hidden")
        val tree = tree(root)
        val first = target(listOf(firstHidden, secondHidden, root.id), tree)
        val equal = target(arrayListOf(firstHidden, secondHidden, root.id), tree)
        val reordered = target(listOf(secondHidden, firstHidden, root.id), tree)
        val differentHiddenEntry = target(
            listOf(EntryId("other-hidden"), secondHidden, root.id),
            tree,
        )

        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertNotEquals(first, reordered)
        assertNotEquals(first, differentHiddenEntry)
    }

    private fun assertInvalidHistory(
        historyEntryIds: List<EntryId>,
        tree: NavigationRenderTree,
        expectedMessageParts: Array<String>,
    ) {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            target(historyEntryIds, tree)
        }
        val message = exception.message.orEmpty()
        expectedMessageParts.forEach { expectedPart ->
            assertTrue(
                "Expected failure message '$message' to contain '$expectedPart'",
                message.contains(expectedPart, ignoreCase = true),
            )
        }
    }

    private fun target(
        historyEntryIds: List<EntryId>,
        tree: NavigationRenderTree,
    ): NavigationRenderTarget = NavigationRenderTarget(
        navigationRevision = 7L,
        historyEntryIds = historyEntryIds,
        tree = tree,
        transitionIntent = null,
    )

    private fun tree(
        root: BackStackEntry,
        nested: BackStackEntry? = null,
        modal: BackStackEntry? = null,
    ): NavigationRenderTree = NavigationRenderTree.create(
        root = ContentSlot(ContentSlotId.Root, root),
        nestedSlots = nested?.let { entry ->
            listOf(ContentSlot(ContentSlotId.ChildOf(root.id), entry))
        }.orEmpty(),
        modalLayers = modal?.let { entry ->
            listOf(ModalLayer(entry, nested?.id ?: root.id))
        }.orEmpty(),
    )

    private fun entry(
        id: String,
        route: Route,
    ): BackStackEntry = BackStackEntry(EntryId(id), route)
}
