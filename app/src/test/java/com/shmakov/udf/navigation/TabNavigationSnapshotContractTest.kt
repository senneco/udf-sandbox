package com.shmakov.udf.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TabNavigationSnapshotContractTest {

    @Test
    fun `wire shape and round trip preserve selected tab ordered histories and exact IDs`() {
        val accounts = tab(
            "accounts",
            entry("accounts-root", Accounts),
            entry("account-42", Account(accountId = 42)),
        )
        val cards = tab(
            "cards",
            entry("cards-root", Cards),
            entry("card-7", Card(cardId = 7)),
        )
        val original = TabNavigationState.create(cards.id, listOf(accounts, cards))

        val snapshot = snapshotSuccess(original.toSnapshot(DemoRouteCodec))
        val repeatedSnapshot = snapshotSuccess(original.toSnapshot(DemoRouteCodec))

        assertEquals(TabNavigationStateSnapshot.CURRENT_VERSION, snapshot.version)
        assertEquals("cards", snapshot.selectedTabId)
        assertEquals(
            listOf(
                TabNavigationStateSnapshot.Tab(
                    id = "accounts",
                    history = NavStateSnapshot(
                        version = NavStateSnapshot.CURRENT_VERSION,
                        entries = listOf(
                            snapshotEntry("accounts-root", "accounts/v1"),
                            snapshotEntry("account-42", "account/v1", "accountId" to "42"),
                        ),
                    ),
                ),
                TabNavigationStateSnapshot.Tab(
                    id = "cards",
                    history = NavStateSnapshot(
                        version = NavStateSnapshot.CURRENT_VERSION,
                        entries = listOf(
                            snapshotEntry("cards-root", "cards/v1"),
                            snapshotEntry("card-7", "card/v1", "cardId" to "7"),
                        ),
                    ),
                ),
            ),
            snapshot.tabs,
        )
        assertEquals(snapshot, repeatedSnapshot)

        val restored = restoreSuccess(
            TabNavigationState.restore(snapshot, DemoRouteCodec),
        )

        assertEquals(original, restored)
        assertNotSame(original, restored)
        original.tabs.zip(restored.tabs).forEach { (originalTab, restoredTab) ->
            assertNotSame(originalTab.history, restoredTab.history)
        }
        assertEquals(
            original.tabs.map { tab -> tab.id to tab.history.entries.map { it.id } },
            restored.tabs.map { tab -> tab.id to tab.history.entries.map { it.id } },
        )
    }

    @Test
    fun `unsupported outer version fails before codec is called`() {
        val codec = RecordingCodec()
        val snapshot = TabNavigationStateSnapshot(
            version = TabNavigationStateSnapshot.CURRENT_VERSION + 1,
            selectedTabId = "accounts",
            tabs = listOf(
                TabNavigationStateSnapshot.Tab(
                    id = "accounts",
                    history = leafSnapshot("accounts-root", "accounts/v1"),
                ),
            ),
        )

        val problems = snapshotFailure(TabNavigationState.restore(snapshot, codec))

        assertEquals(
            listOf(TabNavigationSnapshotProblem.UnsupportedVersion(snapshot.version)),
            problems,
        )
        assertEquals(0, codec.decodeCalls)
    }

    @Test
    fun `nested decode failure keeps exact tab and entry context`() {
        val snapshot = TabNavigationStateSnapshot(
            version = TabNavigationStateSnapshot.CURRENT_VERSION,
            selectedTabId = "cards",
            tabs = listOf(
                TabNavigationStateSnapshot.Tab(
                    id = "accounts",
                    history = leafSnapshot("accounts-root", "accounts/v1"),
                ),
                TabNavigationStateSnapshot.Tab(
                    id = "cards",
                    history = NavStateSnapshot(
                        version = NavStateSnapshot.CURRENT_VERSION,
                        entries = listOf(
                            snapshotEntry("cards-root", "cards/v1"),
                            snapshotEntry("broken-card", "card/broken"),
                        ),
                    ),
                ),
            ),
        )

        val problem = snapshotFailure(
            TabNavigationState.restore(snapshot, DemoRouteCodec),
        ).single()

        assertEquals(
            TabNavigationSnapshotProblem.HistoryProblem(
                tabIndex = 1,
                tabId = "cards",
                problem = SnapshotProblem.RouteDecodeFailed(
                    index = 1,
                    entryId = "broken-card",
                    routeType = "card/broken",
                    error = RouteCodecError(
                        code = "unknown_route_type",
                        message = "Unknown route type: card/broken",
                    ),
                ),
            ),
            problem,
        )
    }

    @Test
    fun `cross-tab duplicate entry ID is rejected by shared graph validation`() {
        val sharedId = "shared"
        val snapshot = TabNavigationStateSnapshot(
            version = TabNavigationStateSnapshot.CURRENT_VERSION,
            selectedTabId = "accounts",
            tabs = listOf(
                TabNavigationStateSnapshot.Tab(
                    id = "accounts",
                    history = leafSnapshot(sharedId, "accounts/v1"),
                ),
                TabNavigationStateSnapshot.Tab(
                    id = "cards",
                    history = leafSnapshot(sharedId, "cards/v1"),
                ),
            ),
        )

        val problems = snapshotFailure(
            TabNavigationState.restore(snapshot, DemoRouteCodec),
        )

        assertEquals(
            listOf(
                TabNavigationSnapshotProblem.InvalidState(
                    TabNavigationStateProblem.DuplicateEntryId(
                        entryId = EntryId(sharedId),
                        firstTabId = TabId("accounts"),
                        duplicateTabId = TabId("cards"),
                        firstTabIndex = 0,
                        duplicateTabIndex = 1,
                    ),
                ),
            ),
            problems,
        )
    }

    @Test
    fun `application codec round trips routes in multiple arbitrary tabs`() {
        val first = tab(
            "first",
            entry("first-root", ConsumerRoute("first-root")),
            entry("first-details", ConsumerRoute("first-details")),
        )
        val second = tab(
            "second",
            entry("second-root", ConsumerRoute("second-root")),
        )
        val original = TabNavigationState.create(second.id, listOf(first, second))

        val snapshot = snapshotSuccess(original.toSnapshot(ConsumerCodec))
        val restored = restoreSuccess(TabNavigationState.restore(snapshot, ConsumerCodec))

        assertEquals(listOf("first", "second"), snapshot.tabs.map { it.id })
        assertEquals(
            listOf("first-root", "first-details", "second-root"),
            snapshot.tabs.flatMap { tab -> tab.history.entries.map { it.arguments["slug"] } },
        )
        assertEquals(original, restored)
        assertEquals(
            original.tabs.flatMap { tab -> tab.history.entries.map { it.id } },
            restored.tabs.flatMap { tab -> tab.history.entries.map { it.id } },
        )
    }

    @Test
    fun `multiple encode failures are tab-major then entry-major with exact context`() {
        val original = TabNavigationState.create(
            selectedTabId = TabId("accounts"),
            tabs = listOf(
                tab(
                    "accounts",
                    entry("accounts-root", ConsumerRoute("accounts-root")),
                    entry("accounts-bad", UnsupportedConsumerRoute("accounts-bad")),
                ),
                tab("cards", entry("cards-root", ConsumerRoute("cards-root"))),
                tab(
                    "support",
                    entry("support-bad-root", UnsupportedConsumerRoute("support-root")),
                    entry("support-good", ConsumerRoute("support-good")),
                    entry("support-bad-details", UnsupportedConsumerRoute("support-details")),
                ),
            ),
        )

        val problems = snapshotFailure(original.toSnapshot(ConsumerCodec))

        assertEquals(
            listOf(
                encodeProblem(0, "accounts", 1, "accounts-bad", "accounts-bad"),
                encodeProblem(2, "support", 0, "support-bad-root", "support-root"),
                encodeProblem(2, "support", 2, "support-bad-details", "support-details"),
            ),
            problems,
        )
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (problems as MutableList<TabNavigationSnapshotProblem>).clear()
        }
    }

    @Test
    fun `multiple decode failures are tab-major then entry-major with exact context`() {
        val snapshot = TabNavigationStateSnapshot(
            version = TabNavigationStateSnapshot.CURRENT_VERSION,
            selectedTabId = "accounts",
            tabs = listOf(
                snapshotTab(
                    "accounts",
                    snapshotEntry("accounts-root", "consumer/v1", "slug" to "accounts-root"),
                    snapshotEntry("accounts-bad", "bad/accounts"),
                ),
                snapshotTab(
                    "support",
                    snapshotEntry("support-bad-root", "bad/support-root"),
                    snapshotEntry("support-good", "consumer/v1", "slug" to "support-good"),
                    snapshotEntry("support-bad-details", "bad/support-details"),
                ),
            ),
        )

        val problems = snapshotFailure(TabNavigationState.restore(snapshot, ConsumerCodec))

        assertEquals(
            listOf(
                decodeProblem(0, "accounts", 1, "accounts-bad", "bad/accounts"),
                decodeProblem(1, "support", 0, "support-bad-root", "bad/support-root"),
                decodeProblem(1, "support", 2, "support-bad-details", "bad/support-details"),
            ),
            problems,
        )
    }

    @Test
    fun `codec exceptions keep exact tab and leaf context`() {
        val graph = TabNavigationState.create(
            selectedTabId = TabId("accounts"),
            tabs = listOf(tab("accounts", entry("accounts-root", Accounts))),
        )
        val encodeProblems = snapshotFailure(graph.toSnapshot(ThrowingEncodeCodec))
        val decodeSnapshot = TabNavigationStateSnapshot(
            version = TabNavigationStateSnapshot.CURRENT_VERSION,
            selectedTabId = "accounts",
            tabs = listOf(snapshotTab("accounts", snapshotEntry("accounts-root", "accounts/v1"))),
        )
        val decodeProblems = snapshotFailure(
            TabNavigationState.restore(decodeSnapshot, ThrowingDecodeCodec),
        )

        assertEquals(
            listOf(
                TabNavigationSnapshotProblem.HistoryProblem(
                    tabIndex = 0,
                    tabId = "accounts",
                    problem = SnapshotProblem.RouteEncodeFailed(
                        index = 0,
                        entryId = EntryId("accounts-root"),
                        error = RouteCodecError("codec_exception", "encode boom"),
                    ),
                ),
            ),
            encodeProblems,
        )
        assertEquals(
            listOf(
                TabNavigationSnapshotProblem.HistoryProblem(
                    tabIndex = 0,
                    tabId = "accounts",
                    problem = SnapshotProblem.RouteDecodeFailed(
                        index = 0,
                        entryId = "accounts-root",
                        routeType = "accounts/v1",
                        error = RouteCodecError("codec_exception", "decode boom"),
                    ),
                ),
            ),
            decodeProblems,
        )
    }

    @Test
    fun `nested version and leaf state failures keep ordered tab context`() {
        val snapshot = TabNavigationStateSnapshot(
            version = TabNavigationStateSnapshot.CURRENT_VERSION,
            selectedTabId = "accounts",
            tabs = listOf(
                TabNavigationStateSnapshot.Tab(
                    id = "accounts",
                    history = NavStateSnapshot(
                        version = NavStateSnapshot.CURRENT_VERSION + 1,
                        entries = listOf(snapshotEntry("accounts-root", "accounts/v1")),
                    ),
                ),
                TabNavigationStateSnapshot.Tab(
                    id = "cards",
                    history = NavStateSnapshot(
                        version = NavStateSnapshot.CURRENT_VERSION,
                        entries = emptyList(),
                    ),
                ),
                snapshotTab("blank", snapshotEntry(" ", "home/v1")),
                snapshotTab(
                    "duplicate",
                    snapshotEntry("duplicate-entry", "home/v1"),
                    snapshotEntry("duplicate-entry", "accounts/v1"),
                ),
            ),
        )

        val problems = snapshotFailure(TabNavigationState.restore(snapshot, DemoRouteCodec))

        assertEquals(
            listOf(
                tabProblem(0, "accounts", SnapshotProblem.UnsupportedVersion(2)),
                tabProblem(
                    1,
                    "cards",
                    SnapshotProblem.InvalidState(NavStateProblem.EmptyStack),
                ),
                tabProblem(
                    2,
                    "blank",
                    SnapshotProblem.InvalidState(NavStateProblem.BlankEntryId(index = 0)),
                ),
                tabProblem(
                    3,
                    "duplicate",
                    SnapshotProblem.InvalidState(
                        NavStateProblem.DuplicateEntryId(EntryId("duplicate-entry")),
                    ),
                ),
            ),
            problems,
        )
    }

    @Test
    fun `empty blank duplicate and missing selection snapshots reuse graph validation`() {
        val validLeaf = leafSnapshot("root", "home/v1")
        val cases = listOf(
            CorruptGraphCase(
                snapshot = TabNavigationStateSnapshot(
                    version = TabNavigationStateSnapshot.CURRENT_VERSION,
                    selectedTabId = "missing",
                    tabs = emptyList(),
                ),
                expected = TabNavigationStateProblem.EmptyTabs,
            ),
            CorruptGraphCase(
                snapshot = TabNavigationStateSnapshot(
                    version = TabNavigationStateSnapshot.CURRENT_VERSION,
                    selectedTabId = " ",
                    tabs = listOf(TabNavigationStateSnapshot.Tab(" ", validLeaf)),
                ),
                expected = TabNavigationStateProblem.BlankTabId(index = 0),
            ),
            CorruptGraphCase(
                snapshot = TabNavigationStateSnapshot(
                    version = TabNavigationStateSnapshot.CURRENT_VERSION,
                    selectedTabId = "duplicate",
                    tabs = listOf(
                        TabNavigationStateSnapshot.Tab("duplicate", leafSnapshot("first", "home/v1")),
                        TabNavigationStateSnapshot.Tab("duplicate", leafSnapshot("second", "home/v1")),
                    ),
                ),
                expected = TabNavigationStateProblem.DuplicateTabId(
                    tabId = TabId("duplicate"),
                    firstIndex = 0,
                    duplicateIndex = 1,
                ),
            ),
            CorruptGraphCase(
                snapshot = TabNavigationStateSnapshot(
                    version = TabNavigationStateSnapshot.CURRENT_VERSION,
                    selectedTabId = "missing",
                    tabs = listOf(TabNavigationStateSnapshot.Tab("accounts", validLeaf)),
                ),
                expected = TabNavigationStateProblem.SelectedTabNotFound(TabId("missing")),
            ),
        )

        cases.forEach { case ->
            assertEquals(
                listOf(TabNavigationSnapshotProblem.InvalidState(case.expected)),
                snapshotFailure(TabNavigationState.restore(case.snapshot, DemoRouteCodec)),
            )
        }
    }

    @Test
    fun `compound graph corruption preserves deterministic model problem order`() {
        val sharedId = "shared"
        val snapshot = TabNavigationStateSnapshot(
            version = TabNavigationStateSnapshot.CURRENT_VERSION,
            selectedTabId = "missing",
            tabs = listOf(
                TabNavigationStateSnapshot.Tab(" ", leafSnapshot(sharedId, "accounts/v1")),
                TabNavigationStateSnapshot.Tab(" ", leafSnapshot(sharedId, "cards/v1")),
            ),
        )

        val problems = snapshotFailure(TabNavigationState.restore(snapshot, DemoRouteCodec))

        assertEquals(
            listOf(
                TabNavigationSnapshotProblem.InvalidState(
                    TabNavigationStateProblem.BlankTabId(index = 0),
                ),
                TabNavigationSnapshotProblem.InvalidState(
                    TabNavigationStateProblem.BlankTabId(index = 1),
                ),
                TabNavigationSnapshotProblem.InvalidState(
                    TabNavigationStateProblem.DuplicateTabId(TabId(" "), 0, 1),
                ),
                TabNavigationSnapshotProblem.InvalidState(
                    TabNavigationStateProblem.SelectedTabNotFound(TabId("missing")),
                ),
                TabNavigationSnapshotProblem.InvalidState(
                    TabNavigationStateProblem.DuplicateEntryId(
                        entryId = EntryId(sharedId),
                        firstTabId = TabId(" "),
                        duplicateTabId = TabId(" "),
                        firstTabIndex = 0,
                        duplicateTabIndex = 1,
                    ),
                ),
            ),
            problems,
        )
    }

    @Test
    fun `child failures precede and suppress graph validation phase`() {
        val snapshot = TabNavigationStateSnapshot(
            version = TabNavigationStateSnapshot.CURRENT_VERSION,
            selectedTabId = "missing",
            tabs = listOf(
                snapshotTab("duplicate", snapshotEntry("broken", "unknown/v1")),
                snapshotTab("duplicate", snapshotEntry("valid", "home/v1")),
            ),
        )

        val problems = snapshotFailure(TabNavigationState.restore(snapshot, DemoRouteCodec))

        assertEquals(
            listOf(
                TabNavigationSnapshotProblem.HistoryProblem(
                    tabIndex = 0,
                    tabId = "duplicate",
                    problem = SnapshotProblem.RouteDecodeFailed(
                        index = 0,
                        entryId = "broken",
                        routeType = "unknown/v1",
                        error = RouteCodecError(
                            code = "unknown_route_type",
                            message = "Unknown route type: unknown/v1",
                        ),
                    ),
                ),
            ),
            problems,
        )
    }

    @Test
    fun `snapshot deeply owns immutable lists maps and structural order`() {
        val arguments = linkedMapOf("slug" to "root")
        val entries = mutableListOf(
            NavStateSnapshot.Entry("first-root", "consumer/v1", arguments),
        )
        val sourceTabs = mutableListOf(
            TabNavigationStateSnapshot.Tab(
                "first",
                NavStateSnapshot(NavStateSnapshot.CURRENT_VERSION, entries),
            ),
            snapshotTab("second", snapshotEntry("second-root", "home/v1")),
        )
        val snapshot = TabNavigationStateSnapshot(
            TabNavigationStateSnapshot.CURRENT_VERSION,
            "first",
            sourceTabs,
        )
        val equalSnapshot = TabNavigationStateSnapshot(
            TabNavigationStateSnapshot.CURRENT_VERSION,
            "first",
            listOf(
                snapshotTab(
                    "first",
                    snapshotEntry("first-root", "consumer/v1", "slug" to "root"),
                ),
                snapshotTab("second", snapshotEntry("second-root", "home/v1")),
            ),
        )
        val reversed = TabNavigationStateSnapshot(
            TabNavigationStateSnapshot.CURRENT_VERSION,
            "first",
            equalSnapshot.tabs.reversed(),
        )

        sourceTabs.clear()
        entries.clear()
        arguments.clear()

        assertEquals(equalSnapshot, snapshot)
        assertEquals(equalSnapshot.hashCode(), snapshot.hashCode())
        assertNotEquals(snapshot, reversed)
        assertEquals("root", snapshot.tabs.first().history.entries.single().arguments["slug"])
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (snapshot.tabs as MutableList<TabNavigationStateSnapshot.Tab>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (snapshot.tabs.first().history.entries as MutableList<NavStateSnapshot.Entry>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (snapshot.tabs.first().history.entries.single().arguments as MutableMap<String, String>)["x"] = "y"
        }
    }

    @Test
    fun `snapshot equality contains durable graph state but no transition metadata`() {
        val accounts = tab("accounts", entry("accounts-root", Accounts))
        val cards = tab("cards", entry("cards-root", Cards))
        val initial = TabNavigationState.create(accounts.id, listOf(accounts, cards))
        val changed = TabReducer.reduce(initial, TabAction.select(cards.id)) as TabReduction.Changed
        val durableEquivalent = TabNavigationState.create(cards.id, listOf(accounts, cards))

        val changedSnapshot = snapshotSuccess(changed.state.toSnapshot(DemoRouteCodec))
        val durableSnapshot = snapshotSuccess(durableEquivalent.toSnapshot(DemoRouteCodec))
        assertEquals(durableSnapshot, changedSnapshot)
    }

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

    private fun leafSnapshot(
        entryId: String,
        routeType: String,
    ): NavStateSnapshot = NavStateSnapshot(
        version = NavStateSnapshot.CURRENT_VERSION,
        entries = listOf(snapshotEntry(entryId, routeType)),
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

    private fun tabProblem(
        tabIndex: Int,
        tabId: String,
        problem: SnapshotProblem,
    ): TabNavigationSnapshotProblem = TabNavigationSnapshotProblem.HistoryProblem(
        tabIndex = tabIndex,
        tabId = tabId,
        problem = problem,
    )

    private fun encodeProblem(
        tabIndex: Int,
        tabId: String,
        entryIndex: Int,
        entryId: String,
        slug: String,
    ): TabNavigationSnapshotProblem = tabProblem(
        tabIndex = tabIndex,
        tabId = tabId,
        problem = SnapshotProblem.RouteEncodeFailed(
            index = entryIndex,
            entryId = EntryId(entryId),
            error = RouteCodecError(
                code = "unsupported_consumer_route",
                message = "Unsupported consumer route: $slug",
            ),
        ),
    )

    private fun decodeProblem(
        tabIndex: Int,
        tabId: String,
        entryIndex: Int,
        entryId: String,
        routeType: String,
    ): TabNavigationSnapshotProblem = tabProblem(
        tabIndex = tabIndex,
        tabId = tabId,
        problem = SnapshotProblem.RouteDecodeFailed(
            index = entryIndex,
            entryId = entryId,
            routeType = routeType,
            error = RouteCodecError(
                code = "unknown_consumer_route",
                message = "Unknown consumer route type: $routeType",
            ),
        ),
    )

    private fun snapshotSuccess(
        result: TabNavigationSnapshotResult<TabNavigationStateSnapshot>,
    ): TabNavigationStateSnapshot = when (result) {
        is TabNavigationSnapshotResult.Success -> result.value
        is TabNavigationSnapshotResult.Failure -> throw AssertionError(result.problems)
    }

    private fun restoreSuccess(
        result: TabNavigationSnapshotResult<TabNavigationState>,
    ): TabNavigationState = when (result) {
        is TabNavigationSnapshotResult.Success -> result.value
        is TabNavigationSnapshotResult.Failure -> throw AssertionError(result.problems)
    }

    private fun snapshotFailure(
        result: TabNavigationSnapshotResult<*>,
    ): List<TabNavigationSnapshotProblem> =
        when (result) {
            is TabNavigationSnapshotResult.Success ->
                throw AssertionError("Expected failure, got ${result.value}")
            is TabNavigationSnapshotResult.Failure -> result.problems
        }

    private class RecordingCodec : RouteCodec {
        var decodeCalls: Int = 0

        override fun encode(route: Route): RouteEncodeResult {
            throw AssertionError("encode must not be called")
        }

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult {
            decodeCalls += 1
            return RouteDecodeResult.Success(Accounts)
        }
    }

    private data class CorruptGraphCase(
        val snapshot: TabNavigationStateSnapshot,
        val expected: TabNavigationStateProblem,
    )

    private data class ConsumerRoute(val slug: String) : ContentRoute

    private data class UnsupportedConsumerRoute(val slug: String) : ContentRoute

    private object ConsumerCodec : RouteCodec {
        override fun encode(route: Route): RouteEncodeResult = when (route) {
            is ConsumerRoute -> RouteEncodeResult.Success(
                EncodedRoute(
                    type = "consumer/v1",
                    arguments = mapOf("slug" to route.slug),
                ),
            )
            is UnsupportedConsumerRoute -> RouteEncodeResult.Failure(
                RouteCodecError(
                    code = "unsupported_consumer_route",
                    message = "Unsupported consumer route: ${route.slug}",
                ),
            )
            else -> RouteEncodeResult.Failure(
                RouteCodecError(
                    code = "unsupported_consumer_route",
                    message = "Unsupported consumer route: $route",
                ),
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
                RouteCodecError(
                    code = "unknown_consumer_route",
                    message = "Unknown consumer route type: $type",
                ),
            )
        }
    }

    private object ThrowingEncodeCodec : RouteCodec {
        override fun encode(route: Route): RouteEncodeResult =
            throw IllegalStateException("encode boom")

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = throw AssertionError("decode is not used")
    }

    private object ThrowingDecodeCodec : RouteCodec {
        override fun encode(route: Route): RouteEncodeResult =
            throw AssertionError("encode is not used")

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = throw IllegalStateException("decode boom")
    }
}
