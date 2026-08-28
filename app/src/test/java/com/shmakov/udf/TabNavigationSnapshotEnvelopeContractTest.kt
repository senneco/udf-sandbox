package com.shmakov.udf

import com.shmakov.udf.navigation.NavStateSnapshot
import com.shmakov.udf.navigation.DemoRouteCodec
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.SnapshotProblem
import com.shmakov.udf.navigation.TabNavigationSnapshotProblem
import com.shmakov.udf.navigation.TabNavigationSnapshotResult
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabNavigationStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedHashMap

class TabNavigationSnapshotEnvelopeContractTest {

    @Test
    fun `wire is one flat Bundle-safe value with exact ordered leaf payloads`() {
        val snapshot = TabNavigationStateSnapshot(
            version = TabNavigationStateSnapshot.CURRENT_VERSION,
            selectedTabId = "cards",
            tabs = listOf(
                snapshotTab(
                    "accounts",
                    snapshotEntry("accounts-root", "accounts/v1"),
                    snapshotEntry("account-42", "account/v1", "accountId" to "42"),
                ),
                snapshotTab(
                    "cards",
                    snapshotEntry("cards-root", "cards/v1"),
                    snapshotEntry("card-7", "card/v1", "cardId" to "7"),
                ),
            ),
        )

        val encoded = TabNavigationSnapshotEnvelopeCodec.encode(snapshot)

        assertEquals(ArrayList::class.java, encoded.javaClass)
        assertTrue((encoded as List<*>).all { token -> token is String })
        assertEquals(
            arrayListOf(
                "udf-tab-nav", "1", "1", "cards", "2",
                "accounts", "12",
                "udf-nav", "1", "1", "2",
                "accounts-root", "accounts/v1", "0",
                "account-42", "account/v1", "1", "accountId", "42",
                "cards", "12",
                "udf-nav", "1", "1", "2",
                "cards-root", "cards/v1", "0",
                "card-7", "card/v1", "1", "cardId", "7",
            ),
            encoded,
        )

        assertEquals(
            snapshot,
            decoded(TabNavigationSnapshotEnvelopeCodec.decode(ArrayList(encoded))),
        )
    }

    @Test
    fun `equal snapshots encode deterministically and decoded value never aliases payload`() {
        val reverseArguments = LinkedHashMap<String, String>().apply {
            put("zeta", "last")
            put("alpha", "first")
        }
        val sortedArguments = linkedMapOf("alpha" to "first", "zeta" to "last")
        val reverseSnapshot = oneTabSnapshot(
            snapshotEntry("entry", "consumer/v1", *reverseArguments.toList().toTypedArray()),
        )
        val sortedSnapshot = oneTabSnapshot(
            snapshotEntry("entry", "consumer/v1", *sortedArguments.toList().toTypedArray()),
        )

        val reversePayload = TabNavigationSnapshotEnvelopeCodec.encode(reverseSnapshot)
        val sortedPayload = TabNavigationSnapshotEnvelopeCodec.encode(sortedSnapshot)

        assertEquals(sortedPayload, reversePayload)
        assertEquals(
            arrayListOf(
                "udf-tab-nav", "1", "1", "accounts", "1",
                "accounts", "11",
                "udf-nav", "1", "1", "1",
                "entry", "consumer/v1", "2",
                "alpha", "first", "zeta", "last",
            ),
            reversePayload,
        )

        val decoded = decoded(TabNavigationSnapshotEnvelopeCodec.decode(reversePayload))
        reversePayload.clear()

        assertEquals(sortedSnapshot, decoded)
        assertEquals(
            mapOf("alpha" to "first", "zeta" to "last"),
            decoded.tabs.single().history.entries.single().arguments,
        )
    }

    @Test
    fun `outer corruption and unsafe counts are typed all or nothing rejections`() {
        val cases = listOf(
            InvalidPayloadCase(7, "wrong_type"),
            InvalidPayloadCase(
                arrayListOf<Any>("udf-tab-nav", 1, "1", "accounts", "0"),
                "wrong_type",
            ),
            InvalidPayloadCase(arrayListOf("not-tab-nav", "1", "1", "accounts", "0"), "invalid_magic"),
            InvalidPayloadCase(arrayListOf("udf-tab-nav", "not-an-int", "1", "accounts", "0"), "invalid_envelope_version"),
            InvalidPayloadCase(arrayListOf("udf-tab-nav", "1", "not-an-int", "accounts", "0"), "invalid_snapshot_version"),
            InvalidPayloadCase(arrayListOf("udf-tab-nav", "1", "1"), "truncated"),
            InvalidPayloadCase(arrayListOf("udf-tab-nav", "1", "1", "accounts", "not-an-int"), "invalid_count"),
            InvalidPayloadCase(arrayListOf("udf-tab-nav", "1", "1", "accounts", "-1"), "invalid_count"),
            InvalidPayloadCase(
                arrayListOf("udf-tab-nav", "1", "1", "accounts", Int.MAX_VALUE.toString()),
                "truncated",
            ),
            InvalidPayloadCase(arrayListOf("udf-tab-nav", "1", "1", "accounts", "1"), "truncated"),
            InvalidPayloadCase(
                arrayListOf("udf-tab-nav", "1", "1", "accounts", "1", "accounts"),
                "truncated",
            ),
            InvalidPayloadCase(
                arrayListOf(
                    "udf-tab-nav", "1", "1", "accounts", "1", "accounts", "not-an-int",
                ),
                "invalid_count",
            ),
            InvalidPayloadCase(
                arrayListOf("udf-tab-nav", "1", "1", "accounts", "1", "accounts", "-1"),
                "invalid_count",
            ),
            InvalidPayloadCase(
                arrayListOf(
                    "udf-tab-nav", "1", "1", "accounts", "1", "accounts", "2147483648",
                ),
                "invalid_count",
            ),
            InvalidPayloadCase(
                arrayListOf(
                    "udf-tab-nav", "1", "1", "accounts", "1", "accounts",
                    Int.MAX_VALUE.toString(),
                ),
                "truncated",
            ),
            InvalidPayloadCase(
                arrayListOf("udf-tab-nav", "1", "1", "accounts", "0", "trailing"),
                "trailing_tokens",
            ),
        )

        cases.forEach { case ->
            val problems = rejected(TabNavigationSnapshotEnvelopeCodec.decode(case.value))
            val problem = problems.single()

            assertTrue(
                "Expected InvalidPayload for ${case.expectedCode}, got $problem",
                problem is TabNavigationSnapshotEnvelopeProblem.InvalidPayload,
            )
            problem as TabNavigationSnapshotEnvelopeProblem.InvalidPayload
            assertEquals(case.expectedCode, problem.code)
            assertTrue(problem.message.isNotBlank())
        }
    }

    @Test
    fun `valid segments aggregate nested leaf failures in tab major order with raw IDs`() {
        val invalidMagicLeaf = arrayListOf("not-udf-nav", "1", "1", "0")
        val unsupportedLeafEnvelope = arrayListOf("udf-nav", "2", "1", "0")
        val payload = tabPayload(
            selectedTabId = "accounts",
            tabs = listOf(
                "accounts" to invalidMagicLeaf,
                "cards" to unsupportedLeafEnvelope,
            ),
        )

        val problems = rejected(TabNavigationSnapshotEnvelopeCodec.decode(payload))

        assertEquals(
            listOf(
                TabNavigationSnapshotEnvelopeProblem.HistoryEnvelopeProblem(
                    tabIndex = 0,
                    tabId = "accounts",
                    problem = NavigationSnapshotEnvelopeProblem.InvalidPayload(
                        code = "invalid_magic",
                        message = "Navigation payload has an unknown magic header.",
                    ),
                ),
                TabNavigationSnapshotEnvelopeProblem.HistoryEnvelopeProblem(
                    tabIndex = 1,
                    tabId = "cards",
                    problem = NavigationSnapshotEnvelopeProblem.UnsupportedEnvelopeVersion(2),
                ),
            ),
            problems,
        )
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (problems as MutableList<TabNavigationSnapshotEnvelopeProblem>).clear()
        }
    }

    @Test
    fun `nested leaf unsafe counts stay bounded and contextual`() {
        val hugeEntryCount = arrayListOf(
            "udf-nav", "1", "1", Int.MAX_VALUE.toString(),
        )
        val hugeArgumentCount = arrayListOf(
            "udf-nav", "1", "1", "1",
            "entry", "consumer/v1", Int.MAX_VALUE.toString(),
        )
        val payload = tabPayload(
            selectedTabId = "accounts",
            tabs = listOf(
                "accounts" to hugeEntryCount,
                "cards" to hugeArgumentCount,
            ),
        )

        val problems = rejected(TabNavigationSnapshotEnvelopeCodec.decode(payload))

        assertEquals(
            listOf(
                TabNavigationSnapshotEnvelopeProblem.HistoryEnvelopeProblem(
                    tabIndex = 0,
                    tabId = "accounts",
                    problem = NavigationSnapshotEnvelopeProblem.InvalidPayload(
                        code = "truncated",
                        message = "Navigation payload ended before its declared data was complete.",
                    ),
                ),
                TabNavigationSnapshotEnvelopeProblem.HistoryEnvelopeProblem(
                    tabIndex = 1,
                    tabId = "cards",
                    problem = NavigationSnapshotEnvelopeProblem.InvalidPayload(
                        code = "truncated",
                        message = "Navigation payload ended before its declared data was complete.",
                    ),
                ),
            ),
            problems,
        )
    }

    @Test
    fun `outer graph and nested leaf versions remain independent`() {
        val validLeaf = NavigationSnapshotEnvelopeCodec.encode(
            NavStateSnapshot(
                version = NavStateSnapshot.CURRENT_VERSION,
                entries = listOf(snapshotEntry("accounts-root", "accounts/v1")),
            ),
        )
        val unsupportedOuterEnvelope = tabPayload(
            envelopeVersion = 2,
            selectedTabId = "accounts",
            tabs = listOf("accounts" to validLeaf),
        )
        assertEquals(
            listOf(TabNavigationSnapshotEnvelopeProblem.UnsupportedEnvelopeVersion(2)),
            rejected(TabNavigationSnapshotEnvelopeCodec.decode(unsupportedOuterEnvelope)),
        )

        val unsupportedGraphSnapshot = tabPayload(
            graphSnapshotVersion = TabNavigationStateSnapshot.CURRENT_VERSION + 1,
            selectedTabId = "accounts",
            tabs = listOf("accounts" to validLeaf),
        )
        val decodedGraphSnapshot = decoded(
            TabNavigationSnapshotEnvelopeCodec.decode(unsupportedGraphSnapshot),
        )
        assertEquals(
            listOf(
                TabNavigationSnapshotProblem.UnsupportedVersion(
                    TabNavigationStateSnapshot.CURRENT_VERSION + 1,
                ),
            ),
            graphProblems(TabNavigationState.restore(decodedGraphSnapshot, DemoRouteCodec)),
        )

        val unsupportedLeafEnvelope = tabPayload(
            selectedTabId = "accounts",
            tabs = listOf("accounts" to arrayListOf("udf-nav", "2", "1", "0")),
        )
        assertEquals(
            listOf(
                TabNavigationSnapshotEnvelopeProblem.HistoryEnvelopeProblem(
                    tabIndex = 0,
                    tabId = "accounts",
                    problem = NavigationSnapshotEnvelopeProblem.UnsupportedEnvelopeVersion(2),
                ),
            ),
            rejected(TabNavigationSnapshotEnvelopeCodec.decode(unsupportedLeafEnvelope)),
        )

        val leafSnapshotVersion = NavStateSnapshot.CURRENT_VERSION + 1
        val unsupportedLeafSnapshot = tabPayload(
            selectedTabId = "accounts",
            tabs = listOf(
                "accounts" to NavigationSnapshotEnvelopeCodec.encode(
                    NavStateSnapshot(
                        version = leafSnapshotVersion,
                        entries = listOf(snapshotEntry("accounts-root", "accounts/v1")),
                    ),
                ),
            ),
        )
        val decodedLeafSnapshot = decoded(
            TabNavigationSnapshotEnvelopeCodec.decode(unsupportedLeafSnapshot),
        )
        assertEquals(
            listOf(
                TabNavigationSnapshotProblem.HistoryProblem(
                    tabIndex = 0,
                    tabId = "accounts",
                    problem = SnapshotProblem.UnsupportedVersion(leafSnapshotVersion),
                ),
            ),
            graphProblems(TabNavigationState.restore(decodedLeafSnapshot, DemoRouteCodec)),
        )
    }

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

    private fun oneTabSnapshot(
        vararg entries: NavStateSnapshot.Entry,
    ): TabNavigationStateSnapshot = TabNavigationStateSnapshot(
        version = TabNavigationStateSnapshot.CURRENT_VERSION,
        selectedTabId = "accounts",
        tabs = listOf(snapshotTab("accounts", *entries)),
    )

    private fun tabPayload(
        envelopeVersion: Int = 1,
        graphSnapshotVersion: Int = TabNavigationStateSnapshot.CURRENT_VERSION,
        selectedTabId: String,
        tabs: List<Pair<String, ArrayList<String>>>,
    ): ArrayList<String> = arrayListOf<String>().apply {
        add("udf-tab-nav")
        add(envelopeVersion.toString())
        add(graphSnapshotVersion.toString())
        add(selectedTabId)
        add(tabs.size.toString())
        tabs.forEach { (tabId, leafPayload) ->
            add(tabId)
            add(leafPayload.size.toString())
            addAll(leafPayload)
        }
    }

    private fun decoded(
        result: TabNavigationSnapshotEnvelopeDecodeResult,
    ): TabNavigationStateSnapshot = when (result) {
        is TabNavigationSnapshotEnvelopeDecodeResult.Decoded -> result.snapshot
        is TabNavigationSnapshotEnvelopeDecodeResult.Rejected -> throw AssertionError(
            "Expected decoded envelope, got ${result.problems}",
        )
    }

    private fun rejected(
        result: TabNavigationSnapshotEnvelopeDecodeResult,
    ): List<TabNavigationSnapshotEnvelopeProblem> = when (result) {
        is TabNavigationSnapshotEnvelopeDecodeResult.Decoded -> throw AssertionError(
            "Expected rejected envelope, got ${result.snapshot}",
        )
        is TabNavigationSnapshotEnvelopeDecodeResult.Rejected -> result.problems
    }

    private fun graphProblems(
        result: TabNavigationSnapshotResult<TabNavigationState>,
    ): List<TabNavigationSnapshotProblem> = when (result) {
        is TabNavigationSnapshotResult.Success -> throw AssertionError(
            "Expected graph rejection, got ${result.value}",
        )
        is TabNavigationSnapshotResult.Failure -> result.problems
    }

    private data class InvalidPayloadCase(
        val value: Any?,
        val expectedCode: String,
    )
}
