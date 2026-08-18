package com.shmakov.udf

import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Card
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.DemoRouteCodec
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavStateSnapshot
import com.shmakov.udf.navigation.SnapshotResult
import com.shmakov.udf.navigation.Transaction
import com.shmakov.udf.navigation.Transactions
import java.util.LinkedHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSnapshotEnvelopeContractTest {

    @Test
    fun `envelope wire is Bundle safe and preserves every demo route argument and exact ID`() {
        val snapshot = snapshotSuccess(demoState().toSnapshot(DemoRouteCodec))

        val encoded = NavigationSnapshotEnvelopeCodec.encode(snapshot)

        assertEquals(ArrayList::class.java, encoded.javaClass)
        assertTrue((encoded as List<*>).all { token -> token is String })
        assertEquals(
            arrayListOf(
                "udf-nav", "1", "1", "8",
                "home", "home/v1", "0",
                "accounts", "accounts/v1", "0",
                "account-42", "account/v1", "1", "accountId", "42",
                "account-details-42", "account-details/v1", "1", "accountId", "42",
                "transactions", "transactions/v1", "0",
                "transaction-77", "transaction/v1", "1", "transactionId", "77",
                "cards", "cards/v1", "0",
                "card-9", "card/v1", "1", "cardId", "9",
            ),
            encoded,
        )

        val decoded = decoded(NavigationSnapshotEnvelopeCodec.decode(ArrayList(encoded)))

        assertEquals(snapshot, decoded)
        assertEquals(demoState(), restoreSuccess(NavState.restore(decoded, DemoRouteCodec)))
    }

    @Test
    fun `envelope orders arguments deterministically and does not alias caller collections`() {
        val arguments = LinkedHashMap<String, String>().apply {
            put("zeta", "last")
            put("alpha", "first")
        }
        val entries = mutableListOf(
            NavStateSnapshot.Entry(
                id = "entry",
                routeType = "consumer/v1",
                arguments = arguments,
            ),
        )
        val snapshot = NavStateSnapshot(version = 1, entries = entries)

        val encoded = NavigationSnapshotEnvelopeCodec.encode(snapshot)
        entries.clear()
        arguments.clear()

        assertEquals(
            arrayListOf(
                "udf-nav", "1", "1", "1",
                "entry", "consumer/v1", "2",
                "alpha", "first",
                "zeta", "last",
            ),
            encoded,
        )

        val decoded = decoded(NavigationSnapshotEnvelopeCodec.decode(encoded))
        encoded.clear()

        assertEquals(1, decoded.entries.size)
        assertEquals(
            mapOf("alpha" to "first", "zeta" to "last"),
            decoded.entries.single().arguments,
        )
    }

    @Test
    fun `malformed envelope variants are typed all or nothing rejections`() {
        val invalidPayloads = listOf(
            InvalidPayloadCase(
                value = 7,
                expectedCode = "wrong_type",
            ),
            InvalidPayloadCase(
                value = arrayListOf("not-udf-nav", "1", "1", "0"),
                expectedCode = "invalid_magic",
            ),
            InvalidPayloadCase(
                value = arrayListOf("udf-nav", "not-an-int", "1", "0"),
                expectedCode = "invalid_envelope_version",
            ),
            InvalidPayloadCase(
                value = arrayListOf("udf-nav", "1", "not-an-int", "0"),
                expectedCode = "invalid_snapshot_version",
            ),
            InvalidPayloadCase(
                value = arrayListOf("udf-nav", "1", "1", "-1"),
                expectedCode = "invalid_count",
            ),
            InvalidPayloadCase(
                value = arrayListOf("udf-nav", "1", "1", "1", "entry-only"),
                expectedCode = "truncated",
            ),
            InvalidPayloadCase(
                value = arrayListOf(
                    "udf-nav", "1", "1", "1",
                    "entry", "consumer/v1", "2",
                    "key", "one", "key", "two",
                ),
                expectedCode = "duplicate_argument_key",
            ),
            InvalidPayloadCase(
                value = arrayListOf("udf-nav", "1", "1", "0", "trailing"),
                expectedCode = "trailing_tokens",
            ),
        )

        invalidPayloads.forEach { case ->
            val problems = rejected(NavigationSnapshotEnvelopeCodec.decode(case.value))
            val problem = problems.single()

            assertTrue(
                "Expected InvalidPayload for ${case.expectedCode}, got $problem",
                problem is NavigationSnapshotEnvelopeProblem.InvalidPayload,
            )
            problem as NavigationSnapshotEnvelopeProblem.InvalidPayload
            assertEquals(case.expectedCode, problem.code)
            assertTrue(problem.message.isNotBlank())
        }

        assertEquals(
            listOf(
                NavigationSnapshotEnvelopeProblem.UnsupportedEnvelopeVersion(version = 2),
            ),
            rejected(
                NavigationSnapshotEnvelopeCodec.decode(
                    arrayListOf("udf-nav", "2", "1", "0"),
                ),
            ),
        )
    }

    private fun demoState(): NavState = validState(
        NavState.fromEntries(
            listOf(
                entry("home", Home),
                entry("accounts", Accounts),
                entry("account-42", Account(accountId = 42)),
                entry("account-details-42", AccountDetails(accountId = 42)),
                entry("transactions", Transactions),
                entry("transaction-77", Transaction(transactionId = 77)),
                entry("cards", Cards),
                entry("card-9", Card(cardId = 9)),
            ),
        ),
    )

    private fun entry(
        id: String,
        route: com.shmakov.udf.navigation.Route,
    ): BackStackEntry = BackStackEntry(EntryId(id), route)

    private fun decoded(
        result: NavigationSnapshotEnvelopeDecodeResult,
    ): NavStateSnapshot = when (result) {
        is NavigationSnapshotEnvelopeDecodeResult.Decoded -> result.snapshot
        is NavigationSnapshotEnvelopeDecodeResult.Rejected -> throw AssertionError(
            "Expected decoded envelope, got ${result.problems}",
        )
    }

    private fun rejected(
        result: NavigationSnapshotEnvelopeDecodeResult,
    ): List<NavigationSnapshotEnvelopeProblem> = when (result) {
        is NavigationSnapshotEnvelopeDecodeResult.Decoded -> throw AssertionError(
            "Expected rejected envelope, got ${result.snapshot}",
        )
        is NavigationSnapshotEnvelopeDecodeResult.Rejected -> result.problems
    }

    private fun validState(result: NavStateCreationResult): NavState = when (result) {
        is NavStateCreationResult.Valid -> result.state
        is NavStateCreationResult.Invalid -> throw AssertionError(
            "Invalid test fixture: ${result.problems}",
        )
    }

    private fun snapshotSuccess(
        result: SnapshotResult<NavStateSnapshot>,
    ): NavStateSnapshot = when (result) {
        is SnapshotResult.Success -> result.value
        is SnapshotResult.Failure -> throw AssertionError(
            "Expected snapshot, got ${result.problems}",
        )
    }

    private fun restoreSuccess(result: SnapshotResult<NavState>): NavState = when (result) {
        is SnapshotResult.Success -> result.value
        is SnapshotResult.Failure -> throw AssertionError(
            "Expected restored state, got ${result.problems}",
        )
    }

    private data class InvalidPayloadCase(
        val value: Any?,
        val expectedCode: String,
    )
}
