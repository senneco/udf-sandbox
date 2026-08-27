package com.shmakov.udf.deeplink

import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavStateProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDeepLinkContractTest {

    @Test
    fun `URI parser is framework-free and rejects missing or malformed input`() {
        assertEquals(
            DeepLinkUriParseResult.Rejected(DeepLinkUriParseProblem.MissingUri),
            DeepLinkUriParser.parse(null),
        )
        assertEquals(
            DeepLinkUriParseResult.Rejected(DeepLinkUriParseProblem.MissingUri),
            DeepLinkUriParser.parse(""),
        )
        assertEquals(
            DeepLinkUriParseResult.Rejected(DeepLinkUriParseProblem.MalformedUri),
            DeepLinkUriParser.parse("udf-sandbox://accounts/%"),
        )

        val parsed = parsed(
            DeepLinkUriParser.parse("UDF-SANDBOX://ACCOUNTS/00042/details"),
        )
        assertEquals("UDF-SANDBOX", parsed.scheme)
        assertEquals("ACCOUNTS", parsed.host)
        assertEquals("/00042/details", parsed.rawPath)
    }

    @Test
    fun `normalizer returns a typed target and canonical URI`() {
        val result = normalized(
            DemoDeepLinkNormalizer.normalize(
                parsed(
                    DeepLinkUriParser.parse(
                        "udf-sandbox://accounts/00042/details",
                    ),
                ),
            ),
        )

        assertEquals(AccountDetailsTarget(accountId = 42), result.target)
        assertEquals("udf-sandbox://accounts/42/details", result.canonicalUri)

        val largest = normalized(
            DemoDeepLinkNormalizer.normalize(
                parsed(
                    DeepLinkUriParser.parse(
                        "udf-sandbox://accounts/${Int.MAX_VALUE}/details",
                    ),
                ),
            ),
        )
        assertEquals(AccountDetailsTarget(Int.MAX_VALUE), largest.target)
    }

    @Test
    fun `foreign schemes and hosts are not handled`() {
        val cases = listOf(
            "https://accounts/42/details" to
                DemoDeepLinkNotHandledReason.UnsupportedScheme,
            "UDF-SANDBOX://accounts/42/details" to
                DemoDeepLinkNotHandledReason.UnsupportedScheme,
            "udf-sandbox://payments/42/details" to
                DemoDeepLinkNotHandledReason.UnsupportedHost,
            "udf-sandbox://ACCOUNTS/42/details" to
                DemoDeepLinkNotHandledReason.UnsupportedHost,
        )

        cases.forEach { (rawUri, expectedReason) ->
            val result = DemoDeepLinkNormalizer.normalize(
                parsed(DeepLinkUriParser.parse(rawUri)),
            )

            assertEquals(
                DemoDeepLinkNormalizationResult.NotHandled(expectedReason),
                result,
            )
        }
    }

    @Test
    fun `recognized links reject every ambiguous or unsafe shape without throwing`() {
        val cases = listOf(
            "udf-sandbox://accounts/details" to DemoDeepLinkProblem.MissingAccountId,
            "udf-sandbox://accounts/not-a-number/details" to
                DemoDeepLinkProblem.InvalidAccountId,
            "udf-sandbox://accounts/%34%32/details" to
                DemoDeepLinkProblem.InvalidAccountId,
            "udf-sandbox://accounts/0/details" to DemoDeepLinkProblem.NonPositiveAccountId,
            "udf-sandbox://accounts/-1/details" to DemoDeepLinkProblem.NonPositiveAccountId,
            "udf-sandbox://accounts/2147483648/details" to
                DemoDeepLinkProblem.InvalidAccountId,
            "udf-sandbox://accounts/42" to DemoDeepLinkProblem.UnsupportedPath,
            "udf-sandbox://accounts/42/details/extra" to
                DemoDeepLinkProblem.UnsupportedPath,
            "udf-sandbox://accounts/42/details/" to DemoDeepLinkProblem.UnsupportedPath,
            "udf-sandbox://accounts/42/DETAILS" to DemoDeepLinkProblem.UnsupportedPath,
            "udf-sandbox://accounts/42/details?source=test" to
                DemoDeepLinkProblem.QueryNotAllowed,
            "udf-sandbox://accounts/42/details#section" to
                DemoDeepLinkProblem.FragmentNotAllowed,
            "udf-sandbox://user@accounts/42/details" to
                DemoDeepLinkProblem.UserInfoNotAllowed,
            "udf-sandbox://accounts:8080/42/details" to
                DemoDeepLinkProblem.PortNotAllowed,
            "udf-sandbox:accounts/42/details" to
                DemoDeepLinkProblem.OpaqueUriNotAllowed,
        )

        cases.forEach { (rawUri, expectedProblem) ->
            val result = DemoDeepLinkNormalizer.normalize(
                parsed(DeepLinkUriParser.parse(rawUri)),
            )

            assertEquals(
                "Unexpected result for $rawUri",
                DemoDeepLinkNormalizationResult.Rejected(expectedProblem),
                result,
            )
        }
    }

    @Test
    fun `history factory creates complete logical route plan with injected IDs`() {
        val ids = ArrayDeque(
            listOf("deep-home", "deep-accounts", "deep-account-42", "deep-details-42"),
        )
        val result = AccountDetailsDeepLinkHistoryFactory.create(
            target = AccountDetailsTarget(accountId = 42),
            entryIdFactory = DeepLinkEntryIdFactory { EntryId(ids.removeFirst()) },
        )

        val state = valid(result)
        assertEquals(
            listOf(Home, Accounts, Account(42), AccountDetails(42)),
            state.entries.map(BackStackEntry::route),
        )
        assertEquals(
            listOf("deep-home", "deep-accounts", "deep-account-42", "deep-details-42"),
            state.entries.map { entry -> entry.id.value },
        )
    }

    @Test
    fun `repeated hydration materializes disjoint route occurrences`() {
        var nextId = 0
        val factory = DeepLinkEntryIdFactory {
            EntryId("generated-${nextId++}")
        }

        val first = valid(
            AccountDetailsDeepLinkHistoryFactory.create(AccountDetailsTarget(42), factory),
        )
        val second = valid(
            AccountDetailsDeepLinkHistoryFactory.create(AccountDetailsTarget(42), factory),
        )

        assertTrue(
            first.entries.map(BackStackEntry::id).toSet()
                .intersect(second.entries.map(BackStackEntry::id).toSet())
                .isEmpty(),
        )
    }

    @Test
    fun `hydration exposes the same invariant failures as NavState fromEntries`() {
        val duplicate = AccountDetailsDeepLinkHistoryFactory.create(
            target = AccountDetailsTarget(42),
            entryIdFactory = DeepLinkEntryIdFactory { EntryId("duplicate") },
        )
        val blank = AccountDetailsDeepLinkHistoryFactory.create(
            target = AccountDetailsTarget(42),
            entryIdFactory = DeepLinkEntryIdFactory { EntryId(" ") },
        )

        assertEquals(
            listOf(NavStateProblem.DuplicateEntryId(EntryId("duplicate"))),
            invalid(duplicate),
        )
        assertEquals(
            listOf(
                NavStateProblem.BlankEntryId(0),
                NavStateProblem.BlankEntryId(1),
                NavStateProblem.BlankEntryId(2),
                NavStateProblem.BlankEntryId(3),
                NavStateProblem.DuplicateEntryId(EntryId(" ")),
            ),
            invalid(blank),
        )
    }

    @Test
    fun `resolver composes parsing normalization and validated hydration`() {
        val ids = ArrayDeque(listOf("h", "a", "m", "d"))

        val result = DemoDeepLinkResolver.resolve(
            rawUri = "udf-sandbox://accounts/42/details",
            entryIdFactory = DeepLinkEntryIdFactory { EntryId(ids.removeFirst()) },
        )

        assertTrue(result is DemoDeepLinkResolution.Resolved)
        result as DemoDeepLinkResolution.Resolved
        assertEquals("udf-sandbox://accounts/42/details", result.deepLink.canonicalUri)
        assertEquals(listOf("h", "a", "m", "d"), result.navState.entries.map { it.id.value })
    }

    @Test
    fun `resolver returns typed structural failure from the shared state validator`() {
        val result = DemoDeepLinkResolver.resolve(
            rawUri = "udf-sandbox://accounts/42/details",
            entryIdFactory = DeepLinkEntryIdFactory { EntryId("duplicate") },
        )

        assertEquals(
            DemoDeepLinkResolution.Rejected(
                DemoDeepLinkResolutionProblem.InvalidNavigationState(
                    listOf(NavStateProblem.DuplicateEntryId(EntryId("duplicate"))),
                ),
            ),
            result,
        )
    }

    private fun parsed(result: DeepLinkUriParseResult): ParsedDeepLinkUri = when (result) {
        is DeepLinkUriParseResult.Parsed -> result.uri
        is DeepLinkUriParseResult.Rejected -> throw AssertionError(
            "Expected parsed URI, got ${result.problem}",
        )
    }

    private fun normalized(
        result: DemoDeepLinkNormalizationResult,
    ): NormalizedDemoDeepLink = when (result) {
        is DemoDeepLinkNormalizationResult.Normalized -> result.deepLink
        is DemoDeepLinkNormalizationResult.NotHandled -> throw AssertionError(
            "Expected normalized link, got ${result.reason}",
        )
        is DemoDeepLinkNormalizationResult.Rejected -> throw AssertionError(
            "Expected normalized link, got ${result.problem}",
        )
    }

    private fun valid(result: NavStateCreationResult) = when (result) {
        is NavStateCreationResult.Valid -> result.state
        is NavStateCreationResult.Invalid -> throw AssertionError(
            "Expected valid state, got ${result.problems}",
        )
    }

    private fun invalid(result: NavStateCreationResult): List<NavStateProblem> = when (result) {
        is NavStateCreationResult.Valid -> throw AssertionError(
            "Expected invalid state, got ${result.state}",
        )
        is NavStateCreationResult.Invalid -> result.problems
    }
}
