package com.shmakov.udf

import androidx.lifecycle.SavedStateHandle
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Card
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.ContentRoute
import com.shmakov.udf.navigation.DemoRouteCodec
import com.shmakov.udf.navigation.EncodedRoute
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavStateSnapshot
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.RouteCodec
import com.shmakov.udf.navigation.RouteCodecError
import com.shmakov.udf.navigation.RouteDecodeResult
import com.shmakov.udf.navigation.RouteEncodeResult
import com.shmakov.udf.navigation.SnapshotProblem
import com.shmakov.udf.navigation.TabId
import com.shmakov.udf.navigation.TabNavigationSnapshotProblem
import com.shmakov.udf.navigation.TabNavigationSnapshotResult
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabNavigationStateSnapshot
import com.shmakov.udf.navigation.TabNavigationStateProblem
import com.shmakov.udf.navigation.TabState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedStateHandleTabNavigationStorageContractTest {

    @Test
    fun `missing payload is distinct from rejection and does not write`() {
        val handle = SavedStateHandle()
        val storage = SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec)

        val result = storage.restore()

        assertTrue(result is TabNavigationRestoreResult.Missing)
        assertTrue(handle.keys().isEmpty())
    }

    @Test
    fun `injected codec saves one payload and a fresh handle restores the exact graph`() {
        val first = tab(
            "first",
            entry("first-root", ConsumerRoute("first-root")),
            entry("first-details", ConsumerRoute("first-details")),
        )
        val second = tab(
            "second",
            entry("second-root", ConsumerRoute("second-root")),
        )
        val original = graph(second.id, first, second)
        val firstHandle = SavedStateHandle(mapOf(UNRELATED_KEY to "keep"))
        val firstStorage = SavedStateHandleTabNavigationStorage(firstHandle, ConsumerCodec)

        assertSaved(firstStorage.save(original))

        val navigationKey = tabNavigationKey(firstHandle)
        val payload = savedPayload(firstHandle, navigationKey)
        assertEquals("keep", firstHandle.get<String>(UNRELATED_KEY))
        assertEquals("udf-tab-nav", payload.first())
        assertTrue((payload as List<*>).all { token -> token is String })

        val recreatedHandle = SavedStateHandle(
            mapOf(
                navigationKey to ArrayList(payload),
                UNRELATED_KEY to "keep",
            ),
        )
        val restored = restored(
            SavedStateHandleTabNavigationStorage(recreatedHandle, ConsumerCodec).restore(),
        )

        assertEquals(original, restored)
        assertEquals(second.id, restored.selectedTabId)
        assertEquals(listOf("first", "second"), restored.tabs.map { it.id.value })
        assertEquals(
            original.tabs.flatMap { tab -> tab.history.entries.map { it.id } },
            restored.tabs.flatMap { tab -> tab.history.entries.map { it.id } },
        )
        assertEquals("keep", recreatedHandle.get<String>(UNRELATED_KEY))
    }

    @Test
    fun `second save replaces the complete payload without aliasing the previous value`() {
        val firstGraph = graph(
            TabId("accounts"),
            tab("accounts", entry("accounts-root", Accounts)),
            tab("cards", entry("cards-root", Cards)),
        )
        val secondGraph = graph(
            TabId("cards"),
            tab(
                "accounts",
                entry("new-accounts-root", Accounts),
                entry("account-42", Account(accountId = 42)),
            ),
            tab(
                "cards",
                entry("new-cards-root", Cards),
                entry("card-7", Card(cardId = 7)),
            ),
        )
        val handle = SavedStateHandle()
        val storage = SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec)

        assertSaved(storage.save(firstGraph))
        val key = handle.keys().single()
        val firstOwnedPayload = checkNotNull(handle.get<ArrayList<String>>(key))
        val firstValue = ArrayList(firstOwnedPayload)

        assertSaved(storage.save(secondGraph))
        val secondOwnedPayload = checkNotNull(handle.get<ArrayList<String>>(key))

        assertNotSame(firstOwnedPayload, secondOwnedPayload)
        assertEquals(firstValue, firstOwnedPayload)
        assertEquals(secondGraph, restored(storage.restore()))
        assertEquals(setOf(key), handle.keys())
    }

    @Test
    fun `snapshot failure in a later tab clears only the stale graph payload`() {
        val valid = graph(
            TabId("accounts"),
            tab("accounts", entry("accounts-root", Accounts)),
            tab("cards", entry("cards-root", Cards)),
        )
        val handle = SavedStateHandle(mapOf(UNRELATED_KEY to "keep"))
        val storage = SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec)
        assertSaved(storage.save(valid))
        val key = tabNavigationKey(handle)
        val unsupportedEntry = entry("unsupported", UnsupportedContent)
        val unsupported = graph(
            TabId("accounts"),
            valid.tabs.first(),
            tab("cards", entry("cards-root-2", Cards), unsupportedEntry),
        )

        val problems = failed(storage.save(unsupported))

        val problem = problems.single() as TabNavigationSaveProblem.Snapshot
        assertEquals(
            TabNavigationSnapshotProblem.HistoryProblem(
                tabIndex = 1,
                tabId = "cards",
                problem = SnapshotProblem.RouteEncodeFailed(
                    index = 1,
                    entryId = unsupportedEntry.id,
                    error = RouteCodecError(
                        code = "unsupported_route",
                        message = "Route is not supported by DemoRouteCodec.",
                    ),
                ),
            ),
            problem.problem,
        )
        assertFalse(handle.contains(key))
        assertEquals("keep", handle.get<String>(UNRELATED_KEY))
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (problems as MutableList<TabNavigationSaveProblem>).clear()
        }
    }

    @Test
    fun `corrupt envelope is rejected clears only its key and then becomes missing`() {
        val handle = SavedStateHandle(mapOf(UNRELATED_KEY to "keep"))
        val storage = SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec)
        assertSaved(storage.save(demoGraph()))
        val key = tabNavigationKey(handle)
        handle[key] = arrayListOf("not-tab-nav", "1", "1", "accounts", "0")

        val problems = rejected(storage.restore())

        assertEquals(
            listOf(
                TabNavigationRestoreProblem.InvalidEnvelope(
                    TabNavigationSnapshotEnvelopeProblem.InvalidPayload(
                        code = "invalid_magic",
                        message = "Tab navigation payload has an unknown magic header.",
                    ),
                ),
            ),
            problems,
        )
        assertFalse(handle.contains(key))
        assertEquals("keep", handle.get<String>(UNRELATED_KEY))
        assertTrue(storage.restore() is TabNavigationRestoreResult.Missing)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (problems as MutableList<TabNavigationRestoreProblem>).clear()
        }
    }

    @Test
    fun `valid envelopes with invalid graph snapshots are rejected whole and cleared`() {
        val validSnapshot = snapshotSuccess(demoGraph().toSnapshot(DemoRouteCodec))
        val cases = listOf(
            InvalidGraphCase(
                snapshot = TabNavigationStateSnapshot(
                    version = TabNavigationStateSnapshot.CURRENT_VERSION + 1,
                    selectedTabId = validSnapshot.selectedTabId,
                    tabs = validSnapshot.tabs,
                ),
                expectedProblem = TabNavigationSnapshotProblem.UnsupportedVersion(
                    TabNavigationStateSnapshot.CURRENT_VERSION + 1,
                ),
            ),
            InvalidGraphCase(
                snapshot = TabNavigationStateSnapshot(
                    version = TabNavigationStateSnapshot.CURRENT_VERSION,
                    selectedTabId = "accounts",
                    tabs = listOf(
                        validSnapshot.tabs.first(),
                        snapshotTab(
                            "cards",
                            snapshotEntry("cards-root", "cards/v1"),
                            snapshotEntry("future", "future/v99"),
                        ),
                    ),
                ),
                expectedProblem = TabNavigationSnapshotProblem.HistoryProblem(
                    tabIndex = 1,
                    tabId = "cards",
                    problem = SnapshotProblem.RouteDecodeFailed(
                        index = 1,
                        entryId = "future",
                        routeType = "future/v99",
                        error = RouteCodecError(
                            code = "unknown_route_type",
                            message = "Unknown route type: future/v99",
                        ),
                    ),
                ),
            ),
            InvalidGraphCase(
                snapshot = TabNavigationStateSnapshot(
                    version = TabNavigationStateSnapshot.CURRENT_VERSION,
                    selectedTabId = "accounts",
                    tabs = listOf(
                        snapshotTab("accounts", snapshotEntry("shared", "accounts/v1")),
                        snapshotTab("cards", snapshotEntry("shared", "cards/v1")),
                    ),
                ),
                expectedProblem = TabNavigationSnapshotProblem.InvalidState(
                    TabNavigationStateProblem.DuplicateEntryId(
                        entryId = EntryId("shared"),
                        firstTabId = TabId("accounts"),
                        duplicateTabId = TabId("cards"),
                        firstTabIndex = 0,
                        duplicateTabIndex = 1,
                    ),
                ),
            ),
        )

        cases.forEach { case ->
            val handle = SavedStateHandle(mapOf(UNRELATED_KEY to "keep"))
            val storage = SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec)
            assertSaved(storage.save(demoGraph()))
            val key = tabNavigationKey(handle)
            handle[key] = TabNavigationSnapshotEnvelopeCodec.encode(case.snapshot)

            val problems = rejected(storage.restore())
            assertEquals(
                listOf(TabNavigationRestoreProblem.InvalidSnapshot(case.expectedProblem)),
                problems,
            )
            assertFalse(handle.contains(key))
            assertEquals("keep", handle.get<String>(UNRELATED_KEY))
        }
    }

    @Test
    fun `linear and tab storage use separate keys and envelope magic`() {
        val handle = SavedStateHandle()
        assertTrue(
            SavedNavigationStateStore(handle).save(NavState.startAt(Home))
                is NavigationSaveResult.Saved,
        )
        val tabStorage = SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec)
        assertSaved(tabStorage.save(demoGraph()))

        assertEquals(2, handle.keys().size)
        assertEquals(
            setOf("udf-nav", "udf-tab-nav"),
            handle.keys().map { key ->
                checkNotNull(handle.get<ArrayList<String>>(key)).first()
            }.toSet(),
        )
        assertEquals(demoGraph(), restored(tabStorage.restore()))
        assertTrue(SavedNavigationStateStore(handle).restore() is NavigationRestoreResult.Restored)
    }

    private fun demoGraph(): TabNavigationState = graph(
        TabId("cards"),
        tab(
            "accounts",
            entry("accounts-root", Accounts),
            entry("account-42", Account(accountId = 42)),
        ),
        tab(
            "cards",
            entry("cards-root", Cards),
            entry("card-7", Card(cardId = 7)),
        ),
    )

    private fun graph(
        selectedTabId: TabId,
        vararg tabs: TabState,
    ): TabNavigationState = TabNavigationState.create(selectedTabId, tabs.toList())

    private fun tab(
        id: String,
        vararg entries: BackStackEntry,
    ): TabState = TabState(TabId(id), navigation(*entries))

    private fun navigation(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> throw AssertionError(result.problems)
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private fun snapshotTab(
        id: String,
        vararg entries: NavStateSnapshot.Entry,
    ): TabNavigationStateSnapshot.Tab = TabNavigationStateSnapshot.Tab(
        id = id,
        history = NavStateSnapshot(
            version = NavStateSnapshot.CURRENT_VERSION,
            entries = entries.toList(),
        ),
    )

    private fun snapshotEntry(
        id: String,
        routeType: String,
        vararg arguments: Pair<String, String>,
    ): NavStateSnapshot.Entry = NavStateSnapshot.Entry(
        id = id,
        routeType = routeType,
        arguments = mapOf(*arguments),
    )

    private fun snapshotSuccess(
        result: TabNavigationSnapshotResult<TabNavigationStateSnapshot>,
    ): TabNavigationStateSnapshot = when (result) {
        is TabNavigationSnapshotResult.Success -> result.value
        is TabNavigationSnapshotResult.Failure -> throw AssertionError(result.problems)
    }

    private fun assertSaved(result: TabNavigationSaveResult) {
        when (result) {
            TabNavigationSaveResult.Saved -> Unit
            is TabNavigationSaveResult.Failed -> throw AssertionError(result.problems)
        }
    }

    private fun failed(result: TabNavigationSaveResult): List<TabNavigationSaveProblem> =
        when (result) {
            TabNavigationSaveResult.Saved -> throw AssertionError("Expected save failure")
            is TabNavigationSaveResult.Failed -> result.problems
        }

    private fun restored(result: TabNavigationRestoreResult): TabNavigationState = when (result) {
        TabNavigationRestoreResult.Missing -> throw AssertionError("Expected restored graph")
        is TabNavigationRestoreResult.Restored -> result.state
        is TabNavigationRestoreResult.Rejected -> throw AssertionError(result.problems)
    }

    private fun rejected(
        result: TabNavigationRestoreResult,
    ): List<TabNavigationRestoreProblem> = when (result) {
        TabNavigationRestoreResult.Missing -> throw AssertionError("Expected rejection")
        is TabNavigationRestoreResult.Restored -> throw AssertionError(result.state)
        is TabNavigationRestoreResult.Rejected -> result.problems
    }

    private fun tabNavigationKey(handle: SavedStateHandle): String =
        (handle.keys() - UNRELATED_KEY).single()

    private fun savedPayload(
        handle: SavedStateHandle,
        key: String,
    ): ArrayList<String> = checkNotNull(handle.get<ArrayList<String>>(key)).let(::ArrayList)

    private data class InvalidGraphCase(
        val snapshot: TabNavigationStateSnapshot,
        val expectedProblem: TabNavigationSnapshotProblem,
    )

    private data class ConsumerRoute(val slug: String) : ContentRoute

    private object UnsupportedContent : ContentRoute

    private object ConsumerCodec : RouteCodec {
        override fun encode(route: Route): RouteEncodeResult = when (route) {
            is ConsumerRoute -> RouteEncodeResult.Success(
                EncodedRoute("consumer/v1", mapOf("slug" to route.slug)),
            )
            else -> RouteEncodeResult.Failure(
                RouteCodecError("unsupported", "Unsupported consumer route."),
            )
        }

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = if (
            type == "consumer/v1" && arguments.keys == setOf("slug")
        ) {
            RouteDecodeResult.Success(ConsumerRoute(arguments.getValue("slug")))
        } else {
            RouteDecodeResult.Failure(
                RouteCodecError("invalid_consumer_route", "Invalid consumer route."),
            )
        }
    }

    private companion object {
        const val UNRELATED_KEY = "unrelated"
    }
}
