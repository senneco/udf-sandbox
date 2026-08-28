package com.shmakov.udf.navigation

import java.util.Collections
import java.util.LinkedHashMap

/**
 * Primitive, transport-agnostic representation of a complete [TabNavigationState].
 *
 * The outer version describes only this tab-container schema. Each [Tab.history] keeps its own
 * [NavStateSnapshot.version], while application route payload versions remain part of route type
 * keys. This DTO contains no labels, icons, transition intent, animation, revision, or Compose
 * state. A transport and lifecycle owner are still required for process-death restoration.
 */
class TabNavigationStateSnapshot(
    val version: Int,
    val selectedTabId: String,
    tabs: List<Tab>,
) {
    /** A defensive, runtime-unmodifiable copy in exact tab-bar order. */
    val tabs: List<Tab> = immutableSnapshotTabListCopy(tabs)

    /** Stable tab identity and its complete independent leaf history. */
    data class Tab(
        val id: String,
        val history: NavStateSnapshot,
    )

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is TabNavigationStateSnapshot &&
            version == other.version &&
            selectedTabId == other.selectedTabId &&
            tabs == other.tabs

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + selectedTabId.hashCode()
        result = 31 * result + tabs.hashCode()
        return result
    }

    override fun toString(): String =
        "TabNavigationStateSnapshot(version=$version, selectedTabId=$selectedTabId, tabs=$tabs)"

    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}

/** All-or-nothing result of encoding or restoring a complete tab navigation snapshot. */
sealed class TabNavigationSnapshotResult<out T> {
    data class Success<T>(val value: T) : TabNavigationSnapshotResult<T>()

    data class Failure(
        val problems: List<TabNavigationSnapshotProblem>,
    ) : TabNavigationSnapshotResult<Nothing>()
}

/**
 * A contextual tab snapshot failure.
 *
 * This family is deliberately non-sealed for consumers: application code should keep a fallback
 * branch so future version or migration diagnostics can be added without breaking source.
 */
abstract class TabNavigationSnapshotProblem private constructor() {
    /** The outer tab-container schema version is not supported. */
    data class UnsupportedVersion(val version: Int) : TabNavigationSnapshotProblem()

    /** A leaf [NavStateSnapshot] problem enriched with its exact tab location. */
    data class HistoryProblem(
        val tabIndex: Int,
        val tabId: String,
        val problem: SnapshotProblem,
    ) : TabNavigationSnapshotProblem()

    /** Restored leaf histories violate a whole-[TabNavigationState] invariant. */
    data class InvalidState(
        val problem: TabNavigationStateProblem,
    ) : TabNavigationSnapshotProblem()
}

@JvmSynthetic
internal fun snapshotTabNavigationState(
    state: TabNavigationState,
    codec: RouteCodec,
): TabNavigationSnapshotResult<TabNavigationStateSnapshot> {
    val problems = mutableListOf<TabNavigationSnapshotProblem>()
    val snapshotTabs = mutableListOf<TabNavigationStateSnapshot.Tab>()

    state.tabs.forEachIndexed { tabIndex, tab ->
        when (val result = tab.history.toSnapshot(codec)) {
            is SnapshotResult.Success -> snapshotTabs += TabNavigationStateSnapshot.Tab(
                id = tab.id.value,
                history = result.value,
            )
            is SnapshotResult.Failure -> problems += result.problems.map { problem ->
                TabNavigationSnapshotProblem.HistoryProblem(
                    tabIndex = tabIndex,
                    tabId = tab.id.value,
                    problem = problem,
                )
            }
        }
    }

    return if (problems.isEmpty()) {
        TabNavigationSnapshotResult.Success(
            TabNavigationStateSnapshot(
                version = TabNavigationStateSnapshot.CURRENT_VERSION,
                selectedTabId = state.selectedTabId.value,
                tabs = snapshotTabs,
            ),
        )
    } else {
        TabNavigationSnapshotResult.Failure(immutableTabSnapshotProblemListCopy(problems))
    }
}

@JvmSynthetic
internal fun restoreTabNavigationState(
    snapshot: TabNavigationStateSnapshot,
    codec: RouteCodec,
): TabNavigationSnapshotResult<TabNavigationState> {
    if (snapshot.version != TabNavigationStateSnapshot.CURRENT_VERSION) {
        return TabNavigationSnapshotResult.Failure(
            listOf(TabNavigationSnapshotProblem.UnsupportedVersion(snapshot.version)),
        )
    }

    val problems = mutableListOf<TabNavigationSnapshotProblem>()
    val restoredTabs = mutableListOf<TabState>()
    val snapshotTabs = ArrayList(snapshot.tabs)

    snapshotTabs.forEachIndexed { tabIndex, snapshotTab ->
        when (val result = NavState.restore(snapshotTab.history, codec)) {
            is SnapshotResult.Success -> restoredTabs += TabState(
                id = TabId(snapshotTab.id),
                history = result.value,
            )
            is SnapshotResult.Failure -> problems += result.problems.map { problem ->
                TabNavigationSnapshotProblem.HistoryProblem(
                    tabIndex = tabIndex,
                    tabId = snapshotTab.id,
                    problem = problem,
                )
            }
        }
    }

    if (problems.isNotEmpty()) {
        return TabNavigationSnapshotResult.Failure(
            immutableTabSnapshotProblemListCopy(problems),
        )
    }

    return when (
        val result = TabNavigationState.fromTabs(
            selectedTabId = TabId(snapshot.selectedTabId),
            tabs = restoredTabs,
        )
    ) {
        is TabNavigationStateCreationResult.Valid ->
            TabNavigationSnapshotResult.Success(result.state)
        is TabNavigationStateCreationResult.Invalid -> TabNavigationSnapshotResult.Failure(
            immutableTabSnapshotProblemListCopy(
                result.problems.map(TabNavigationSnapshotProblem::InvalidState),
            ),
        )
    }
}

private fun immutableSnapshotTabListCopy(
    source: Collection<TabNavigationStateSnapshot.Tab>,
): List<TabNavigationStateSnapshot.Tab> = Collections.unmodifiableList(
    source.mapTo(ArrayList(source.size)) { tab ->
        TabNavigationStateSnapshot.Tab(
            id = tab.id,
            history = immutableNavStateSnapshotCopy(tab.history),
        )
    },
)

private fun immutableNavStateSnapshotCopy(snapshot: NavStateSnapshot): NavStateSnapshot =
    NavStateSnapshot(
        version = snapshot.version,
        entries = Collections.unmodifiableList(
            snapshot.entries.mapTo(ArrayList(snapshot.entries.size)) { entry ->
                NavStateSnapshot.Entry(
                    id = entry.id,
                    routeType = entry.routeType,
                    arguments = Collections.unmodifiableMap(LinkedHashMap(entry.arguments)),
                )
            },
        ),
    )

private fun immutableTabSnapshotProblemListCopy(
    source: Collection<TabNavigationSnapshotProblem>,
): List<TabNavigationSnapshotProblem> = Collections.unmodifiableList(ArrayList(source))
