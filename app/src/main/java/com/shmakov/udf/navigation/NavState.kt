package com.shmakov.udf.navigation

import java.util.Collections
import java.util.UUID

/** Serializable identity of one concrete route occurrence. */
data class EntryId(val value: String)

/** One route occurrence in the logical navigation history. */
data class BackStackEntry(
    val id: EntryId,
    val route: Route,
) {
    companion object {
        /** Creates a new occurrence of [route] at the non-deterministic application boundary. */
        @JvmStatic
        fun create(route: Route): BackStackEntry = BackStackEntry(
            id = EntryId(UUID.randomUUID().toString()),
            route = route,
        )
    }
}

/** A structural invariant violation found while creating or restoring [NavState]. */
sealed class NavStateProblem {
    object EmptyStack : NavStateProblem()

    data class BlankEntryId(val index: Int) : NavStateProblem()

    data class MissingRouteKind(val entryId: EntryId) : NavStateProblem()

    data class AmbiguousRouteKind(val entryId: EntryId) : NavStateProblem()

    data class NonContentRoot(val entryId: EntryId) : NavStateProblem()

    data class DuplicateEntryId(val entryId: EntryId) : NavStateProblem()
}

/** Result of validating caller-controlled entries with [NavState.fromEntries]. */
sealed class NavStateCreationResult {
    data class Valid(val state: NavState) : NavStateCreationResult()

    data class Invalid(val problems: List<NavStateProblem>) : NavStateCreationResult()
}

/**
 * Immutable, non-empty logical navigation history.
 *
 * Use [startAt] or [history] when the library should generate entry IDs. Use [fromEntries] when
 * restoring a snapshot, hydrating a deep link, or otherwise supplying stable IDs explicitly.
 */
class NavState private constructor(entries: List<BackStackEntry>) {
    /** A defensive, runtime-unmodifiable view of the complete logical history. */
    val entries: List<BackStackEntry> = immutableListCopy(entries)

    val root: BackStackEntry
        get() = entries.first()

    val top: BackStackEntry
        get() = entries.last()

    /** Encodes this state without Android, Compose, or animation data. */
    fun toSnapshot(codec: RouteCodec): SnapshotResult<NavStateSnapshot> =
        snapshotNavState(this, codec)

    override fun equals(other: Any?): Boolean =
        this === other || other is NavState && entries == other.entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "NavState(entries=$entries)"

    companion object {
        /** Starts a new history and owns generation of the root entry identity. */
        @JvmStatic
        fun startAt(rootRoute: ContentRoute): NavState = history(rootRoute)

        /** Builds a generated history, throwing when a caller supplies an invalid route kind. */
        @JvmStatic
        fun history(
            root: ContentRoute,
            vararg routes: Route,
        ): NavState {
            val entryIds = mutableSetOf<EntryId>()
            val entries = buildList {
                listOf<Route>(root, *routes).forEach { route ->
                    var entry: BackStackEntry
                    do {
                        entry = BackStackEntry.create(route)
                    } while (!entryIds.add(entry.id))
                    add(entry)
                }
            }

            return when (val result = fromEntries(entries)) {
                is NavStateCreationResult.Valid -> result.state
                is NavStateCreationResult.Invalid -> throw IllegalArgumentException(
                    "Invalid navigation history: ${result.problems.joinToString()}",
                )
            }
        }

        /** Validates caller-controlled identities and returns every detected state problem. */
        @JvmStatic
        fun fromEntries(entries: List<BackStackEntry>): NavStateCreationResult {
            val entriesCopy = ArrayList(entries)
            val problems = validateEntries(entriesCopy)
            return if (problems.isEmpty()) {
                NavStateCreationResult.Valid(NavState(entriesCopy))
            } else {
                NavStateCreationResult.Invalid(immutableListCopy(problems))
            }
        }

        /** Restores a snapshot through [codec] and the same validation used by [fromEntries]. */
        @JvmStatic
        fun restore(
            snapshot: NavStateSnapshot,
            codec: RouteCodec,
        ): SnapshotResult<NavState> = restoreNavState(snapshot, codec)
    }
}

private fun validateEntries(entries: List<BackStackEntry>): List<NavStateProblem> {
    if (entries.isEmpty()) return listOf(NavStateProblem.EmptyStack)

    val problems = mutableListOf<NavStateProblem>()

    entries.forEachIndexed { index, entry ->
        if (entry.id.value.isBlank()) {
            problems += NavStateProblem.BlankEntryId(index)
        }

        val isContent = entry.route is ContentRoute
        val isModal = entry.route is ModalRoute
        when {
            !isContent && !isModal -> problems += NavStateProblem.MissingRouteKind(entry.id)
            isContent && isModal -> problems += NavStateProblem.AmbiguousRouteKind(entry.id)
        }
    }

    val root = entries.first()
    if (root.route is ModalRoute && root.route !is ContentRoute) {
        problems += NavStateProblem.NonContentRoot(root.id)
    }

    val seenIds = mutableSetOf<EntryId>()
    val reportedDuplicateIds = mutableSetOf<EntryId>()
    entries.forEach { entry ->
        if (!seenIds.add(entry.id) && reportedDuplicateIds.add(entry.id)) {
            problems += NavStateProblem.DuplicateEntryId(entry.id)
        }
    }

    return problems
}

/** Temporary Kotlin-only adapter for runtime call sites migrating to typed failures. */
@JvmSynthetic
internal fun NavStateCreationResult.requireValid(): NavState = when (this) {
    is NavStateCreationResult.Valid -> state
    is NavStateCreationResult.Invalid -> throw IllegalArgumentException(
        "Invalid navigation state: ${problems.joinToString()}",
    )
}

private fun <T> immutableListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))
