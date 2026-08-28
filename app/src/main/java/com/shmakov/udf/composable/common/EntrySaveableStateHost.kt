package com.shmakov.udf.composable.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.shmakov.udf.NavigationRenderTarget
import com.shmakov.udf.navigation.EntryId
import java.util.Collections

/**
 * Owns saveable UI state for exact navigation entry occurrences.
 *
 * A new occurrence must use a fresh [EntryId]. Reusing a removed ID while its outgoing content is
 * still composed is outside the entry-lifetime contract of this host.
 */
internal class EntrySaveableStateHost private constructor(
    private val saveableStateHolder: SaveableStateHolder,
    private val retainedEntryLedger: RetainedEntryLedger,
) {
    private val movableHosts = LinkedHashMap<String, MovableEntryHost>()

    /** Invokes the one movable composition owned by [entryId]. */
    @Composable
    fun Render(
        entryId: EntryId,
        content: @Composable () -> Unit,
    ) {
        Render(entryId, EntryRenderContent(content))
    }

    /** Invokes one already-stabilized content boundary owned by [entryId]. */
    @Composable
    fun Render(
        entryId: EntryId,
        content: EntryRenderContent,
    ) {
        val saveableKey = entryId.value
        val host = movableHosts.getOrPut(saveableKey) {
            MovableEntryHost(
                saveableKey = saveableKey,
                saveableStateHolder = saveableStateHolder,
                initiallyRetained = retainedEntryLedger.contains(saveableKey),
                onInactive = ::releaseIfRetired,
            )
        }
        host.content(content)
    }

    /** Commits the complete durable history after the corresponding render target was accepted. */
    fun accept(historyEntryIds: List<EntryId>) {
        val retainedKeys = LinkedHashSet<String>(historyEntryIds.size)
        historyEntryIds.forEach { entryId ->
            check(retainedKeys.add(entryId.value)) {
                "Duplicate retained entry ID '${entryId.value}'"
            }
        }

        retainedEntryLedger.keys().forEach { previousKey ->
            if (previousKey !in retainedKeys) {
                // For an active outgoing provider Compose keeps its live registry, but marks it as
                // non-saveable. Its eventual disposal therefore cannot resurrect a popped entry.
                saveableStateHolder.removeState(previousKey)
            }
        }

        movableHosts.values.toList().forEach { host ->
            host.isRetained = host.saveableKey in retainedKeys
            releaseIfRetired(host)
        }
        retainedEntryLedger.replaceWith(retainedKeys)
    }

    private fun releaseIfRetired(host: MovableEntryHost) {
        if (
            !host.isRetained &&
            !host.isActive &&
            movableHosts[host.saveableKey] === host
        ) {
            movableHosts.remove(host.saveableKey)
        }
    }

    companion object {
        @Composable
        fun remember(): EntrySaveableStateHost {
            val saveableStateHolder = rememberSaveableStateHolder()
            val ledger = rememberSaveable(saver = RetainedEntryLedgerSaver) {
                RetainedEntryLedger(emptyList())
            }
            return remember(saveableStateHolder, ledger) {
                EntrySaveableStateHost(saveableStateHolder, ledger)
            }
        }
    }
}

@Stable
internal class EntryRenderContent(
    val content: @Composable () -> Unit,
)

private class MovableEntryHost(
    val saveableKey: String,
    private val saveableStateHolder: SaveableStateHolder,
    initiallyRetained: Boolean,
    private val onInactive: (MovableEntryHost) -> Unit,
) {
    var isRetained: Boolean = initiallyRetained
    var isActive: Boolean = false
        private set

    val content: @Composable (EntryRenderContent) -> Unit =
        movableContentOf<EntryRenderContent> { renderContent ->
            saveableStateHolder.SaveableStateProvider(saveableKey) {
                DisposableEffect(this@MovableEntryHost) {
                    check(!isActive) {
                        "Entry saveable host '$saveableKey' was invoked more than once"
                    }
                    isActive = true
                    onDispose {
                        isActive = false
                        onInactive(this@MovableEntryHost)
                    }
                }
                renderContent.content()
            }
        }
}

/** Plain mutable ledger: destinations never observe it as Compose snapshot state. */
private class RetainedEntryLedger(
    initialKeys: Collection<String>,
) {
    private val retainedKeys = LinkedHashSet(initialKeys)

    fun contains(key: String): Boolean = key in retainedKeys

    fun keys(): Set<String> = Collections.unmodifiableSet(LinkedHashSet(retainedKeys))

    fun replaceWith(keys: Collection<String>) {
        retainedKeys.clear()
        retainedKeys.addAll(keys)
    }

    fun save(): ArrayList<String> = ArrayList(retainedKeys)
}

private val RetainedEntryLedgerSaver: Saver<RetainedEntryLedger, ArrayList<String>> = Saver(
    save = { ledger -> ledger.save() },
    restore = { restoredKeys -> RetainedEntryLedger(restoredKeys) },
)

/** Retains the previous accepted target only inside one uninterrupted physical renderer epoch. */
internal class EntryRenderOwnershipHolder {
    private var acceptedOwnership: EntryRenderOwnership? = null
    private var acceptedPhysicalRendererEpoch: Long = 0L

    fun prepare(
        latestTarget: NavigationRenderTarget,
        physicalRendererEpoch: Long,
    ): EntryRenderOwnership {
        val accepted = acceptedOwnership
        if (acceptedPhysicalRendererEpoch != physicalRendererEpoch) {
            return EntryRenderOwnership(
                latestTarget = latestTarget,
                outgoingTarget = null,
            )
        }
        return if (accepted?.latestTarget == latestTarget) {
            accepted
        } else {
            EntryRenderOwnership(
                latestTarget = latestTarget,
                outgoingTarget = accepted?.latestTarget,
            )
        }
    }

    fun accept(
        candidate: EntryRenderOwnership,
        physicalRendererEpoch: Long,
    ) {
        acceptedOwnership = candidate
        acceptedPhysicalRendererEpoch = physicalRendererEpoch
    }
}

/** Selects one exact branch occurrence for every projected entry ID. */
internal class EntryRenderOwnership(
    val latestTarget: NavigationRenderTarget,
    val outgoingTarget: NavigationRenderTarget?,
) {
    private val latestVisibleContentEntryIds: Set<EntryId> =
        latestTarget.visibleContentEntryIds()
    private val outgoingVisibleContentEntryIds: Set<EntryId> =
        outgoingTarget?.visibleContentEntryIds().orEmpty()

    fun ownsContent(
        branchTarget: NavigationRenderTarget,
        entryId: EntryId,
    ): Boolean = if (entryId in latestVisibleContentEntryIds) {
        branchTarget == latestTarget
    } else {
        branchTarget == outgoingTarget && entryId in outgoingVisibleContentEntryIds
    }

    fun ownsModalSlot(
        physicalBranchTarget: NavigationRenderTarget,
        ownerContentEntryId: EntryId,
    ): Boolean {
        val physicalOwner = when {
            ownerContentEntryId in latestVisibleContentEntryIds -> latestTarget
            ownerContentEntryId in outgoingVisibleContentEntryIds -> outgoingTarget
            else -> null
        }
        return physicalBranchTarget == physicalOwner
    }

    fun hasPhysicalOwner(ownerContentEntryId: EntryId): Boolean =
        ownerContentEntryId in latestVisibleContentEntryIds ||
            ownerContentEntryId in outgoingVisibleContentEntryIds

}

private fun NavigationRenderTarget.visibleContentEntryIds(): Set<EntryId> = buildSet {
    add(tree.root.entry.id)
    tree.nestedSlots.forEach { slot -> add(slot.entry.id) }
}
