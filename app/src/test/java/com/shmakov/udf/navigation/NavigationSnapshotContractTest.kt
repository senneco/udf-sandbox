package com.shmakov.udf.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationSnapshotContractTest {

    @Test
    fun `snapshot wire format is stable for every demo route and exact entry ID`() {
        val entries = demoEntries()
        val original = validState(NavState.fromEntries(entries))

        val snapshot = snapshotSuccess(original.toSnapshot(DemoRouteCodec))

        assertEquals(
            NavStateSnapshot(
                version = NavStateSnapshot.CURRENT_VERSION,
                entries = listOf(
                    snapshotEntry("home", "home/v1"),
                    snapshotEntry("accounts", "accounts/v1"),
                    snapshotEntry("account-42", "account/v1", "accountId" to "42"),
                    snapshotEntry(
                        "account-details-42",
                        "account-details/v1",
                        "accountId" to "42",
                    ),
                    snapshotEntry("transactions", "transactions/v1"),
                    snapshotEntry(
                        "transaction-77",
                        "transaction/v1",
                        "transactionId" to "77",
                    ),
                    snapshotEntry("cards", "cards/v1"),
                    snapshotEntry("card-9", "card/v1", "cardId" to "9"),
                ),
            ),
            snapshot,
        )
    }

    @Test
    fun `snapshot round trip preserves every demo route order and exact entry IDs`() {
        val entries = demoEntries()
        val original = validState(NavState.fromEntries(entries))

        val snapshot = snapshotSuccess(original.toSnapshot(DemoRouteCodec))
        val restored = restoreSuccess(NavState.restore(snapshot, DemoRouteCodec))

        assertEquals(original, restored)
        assertEquals(entries, restored.entries)
    }

    @Test
    fun `a consumer codec can round trip an application-defined route`() {
        val entry = BackStackEntry(
            id = EntryId("consumer-home"),
            route = ConsumerRoute(slug = "welcome"),
        )
        val original = validState(NavState.fromEntries(listOf(entry)))

        val snapshot = snapshotSuccess(original.toSnapshot(ConsumerRouteCodec))
        val restored = restoreSuccess(NavState.restore(snapshot, ConsumerRouteCodec))

        assertEquals(original, restored)
    }

    @Test
    fun `unsupported snapshot version is a typed failure`() {
        val snapshot = validDemoSnapshot().copy(
            version = NavStateSnapshot.CURRENT_VERSION + 1,
        )

        val problems = restoreFailure(NavState.restore(snapshot, DemoRouteCodec))

        assertEquals(
            listOf(SnapshotProblem.UnsupportedVersion(snapshot.version)),
            problems,
        )
    }

    @Test
    fun `blank entry ID is mapped from shared state validation`() {
        val snapshot = validDemoSnapshot().let { valid ->
            valid.copy(
                entries = valid.entries.mapIndexed { index, entry ->
                    if (index == 1) entry.copy(id = "  ") else entry
                },
            )
        }

        val problems = restoreFailure(NavState.restore(snapshot, DemoRouteCodec))

        assertEquals(
            listOf(
                SnapshotProblem.InvalidState(
                    NavStateProblem.BlankEntryId(index = 1),
                ),
            ),
            problems,
        )
    }

    @Test
    fun `unknown route failure contains snapshot entry context`() {
        val snapshot = validDemoSnapshot().let { valid ->
            valid.copy(
                entries = valid.entries.mapIndexed { index, entry ->
                    if (index == 1) {
                        entry.copy(routeType = "future-route", arguments = emptyMap())
                    } else {
                        entry
                    }
                },
            )
        }

        val problem = restoreFailure(
            NavState.restore(snapshot, DemoRouteCodec),
        ).single() as SnapshotProblem.RouteDecodeFailed

        assertEquals(1, problem.index)
        assertEquals("accounts", problem.entryId)
        assertEquals("future-route", problem.routeType)
        assertEquals("unknown_route_type", problem.error.code)
    }

    @Test
    fun `malformed account ID failure contains snapshot entry context`() {
        val snapshot = NavStateSnapshot(
            version = NavStateSnapshot.CURRENT_VERSION,
            entries = listOf(
                snapshotEntry("home", "home/v1"),
                snapshotEntry(
                    "account",
                    "account/v1",
                    "accountId" to "not-an-int",
                ),
            ),
        )

        val problem = restoreFailure(
            NavState.restore(snapshot, DemoRouteCodec),
        ).single() as SnapshotProblem.RouteDecodeFailed

        assertEquals(1, problem.index)
        assertEquals("account", problem.entryId)
        assertEquals("account/v1", problem.routeType)
        assertEquals("malformed_route_arguments", problem.error.code)
    }

    @Test
    fun `unsupported application route encoding contains state entry context`() {
        val entry = BackStackEntry(
            id = EntryId("consumer-home"),
            route = ConsumerRoute(slug = "welcome"),
        )
        val state = validState(NavState.fromEntries(listOf(entry)))

        val problem = snapshotFailure(
            state.toSnapshot(DemoRouteCodec),
        ).single() as SnapshotProblem.RouteEncodeFailed

        assertEquals(0, problem.index)
        assertEquals(entry.id, problem.entryId)
        assertEquals("unsupported_route", problem.error.code)
    }

    @Test
    fun `explicit consumer encode error is preserved with entry context`() {
        val entry = BackStackEntry(EntryId("home"), Home)
        val state = validState(NavState.fromEntries(listOf(entry)))

        val problems = snapshotFailure(state.toSnapshot(EncodeFailureCodec))

        assertEquals(
            listOf(
                SnapshotProblem.RouteEncodeFailed(
                    index = 0,
                    entryId = entry.id,
                    error = EXPLICIT_CODEC_ERROR,
                ),
            ),
            problems,
        )
    }

    @Test
    fun `explicit consumer decode error is preserved with snapshot entry context`() {
        val snapshot = NavStateSnapshot(
            version = NavStateSnapshot.CURRENT_VERSION,
            entries = listOf(snapshotEntry("home", "consumer/v1")),
        )

        val problems = restoreFailure(NavState.restore(snapshot, DecodeFailureCodec))

        assertEquals(
            listOf(
                SnapshotProblem.RouteDecodeFailed(
                    index = 0,
                    entryId = "home",
                    routeType = "consumer/v1",
                    error = EXPLICIT_CODEC_ERROR,
                ),
            ),
            problems,
        )
    }

    @Test
    fun `encode codec exception is converted to a typed contextual failure`() {
        val entry = BackStackEntry(EntryId("home"), Home)
        val state = validState(NavState.fromEntries(listOf(entry)))

        val problem = snapshotFailure(
            state.toSnapshot(ThrowingEncodeCodec),
        ).single() as SnapshotProblem.RouteEncodeFailed

        assertEquals(0, problem.index)
        assertEquals(entry.id, problem.entryId)
        assertEquals(RouteCodecError("codec_exception", "encode boom"), problem.error)
    }

    @Test
    fun `decode codec exception is converted to a typed contextual failure`() {
        val snapshot = NavStateSnapshot(
            version = NavStateSnapshot.CURRENT_VERSION,
            entries = listOf(snapshotEntry("home", "home/v1")),
        )

        val problem = restoreFailure(
            NavState.restore(snapshot, ThrowingDecodeCodec),
        ).single() as SnapshotProblem.RouteDecodeFailed

        assertEquals(0, problem.index)
        assertEquals("home", problem.entryId)
        assertEquals("home/v1", problem.routeType)
        assertEquals(RouteCodecError("codec_exception", "decode boom"), problem.error)
    }

    @Test
    fun `restoration reuses state validation for duplicate entry IDs`() {
        val duplicateId = "duplicate"
        val snapshot = validDemoSnapshot().let { valid ->
            valid.copy(
                entries = valid.entries.map { entry -> entry.copy(id = duplicateId) },
            )
        }

        val problems = restoreFailure(NavState.restore(snapshot, DemoRouteCodec))

        assertEquals(
            listOf(
                SnapshotProblem.InvalidState(
                    NavStateProblem.DuplicateEntryId(EntryId(duplicateId)),
                ),
            ),
            problems,
        )
    }

    @Test
    fun `restored empty history is rejected by shared state validation`() {
        val snapshot = NavStateSnapshot(
            version = NavStateSnapshot.CURRENT_VERSION,
            entries = emptyList(),
        )

        val problems = restoreFailure(NavState.restore(snapshot, DemoRouteCodec))

        assertEquals(
            listOf(SnapshotProblem.InvalidState(NavStateProblem.EmptyStack)),
            problems,
        )
    }

    @Test
    fun `restored modal root is rejected by shared state validation`() {
        val snapshot = NavStateSnapshot(
            version = NavStateSnapshot.CURRENT_VERSION,
            entries = listOf(
                snapshotEntry("account-42", "account/v1", "accountId" to "42"),
            ),
        )

        val problems = restoreFailure(NavState.restore(snapshot, DemoRouteCodec))

        assertEquals(
            listOf(
                SnapshotProblem.InvalidState(
                    NavStateProblem.NonContentRoot(EntryId("account-42")),
                ),
            ),
            problems,
        )
    }

    @Test
    fun `restored route without a kind is rejected by shared state validation`() {
        val snapshot = routeKindSnapshot(childType = "missing-kind/v1")

        val problems = restoreFailure(NavState.restore(snapshot, RouteKindCodec))

        assertEquals(
            listOf(
                SnapshotProblem.InvalidState(
                    NavStateProblem.MissingRouteKind(EntryId("child")),
                ),
            ),
            problems,
        )
    }

    @Test
    fun `restored route with both kinds is rejected by shared state validation`() {
        val snapshot = routeKindSnapshot(childType = "ambiguous-kind/v1")

        val problems = restoreFailure(NavState.restore(snapshot, RouteKindCodec))

        assertEquals(
            listOf(
                SnapshotProblem.InvalidState(
                    NavStateProblem.AmbiguousRouteKind(EntryId("child")),
                ),
            ),
            problems,
        )
    }

    private fun demoEntries(): List<BackStackEntry> = listOf(
        BackStackEntry(EntryId("home"), Home),
        BackStackEntry(EntryId("accounts"), Accounts),
        BackStackEntry(EntryId("account-42"), Account(accountId = 42)),
        BackStackEntry(EntryId("account-details-42"), AccountDetails(accountId = 42)),
        BackStackEntry(EntryId("transactions"), Transactions),
        BackStackEntry(EntryId("transaction-77"), Transaction(transactionId = 77)),
        BackStackEntry(EntryId("cards"), Cards),
        BackStackEntry(EntryId("card-9"), Card(cardId = 9)),
    )

    private fun validDemoSnapshot(): NavStateSnapshot {
        val state = validState(
            NavState.fromEntries(
                listOf(
                    BackStackEntry(EntryId("home"), Home),
                    BackStackEntry(EntryId("accounts"), Accounts),
                ),
            ),
        )
        return snapshotSuccess(state.toSnapshot(DemoRouteCodec))
    }

    private fun routeKindSnapshot(childType: String): NavStateSnapshot = NavStateSnapshot(
        version = NavStateSnapshot.CURRENT_VERSION,
        entries = listOf(
            snapshotEntry("home", "home/v1"),
            snapshotEntry("child", childType),
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

    private fun validState(result: NavStateCreationResult): NavState = when (result) {
        is NavStateCreationResult.Valid -> result.state
        is NavStateCreationResult.Invalid -> throw AssertionError(
            "Expected valid state, got ${result.problems}",
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

    private fun snapshotFailure(
        result: SnapshotResult<NavStateSnapshot>,
    ): List<SnapshotProblem> = when (result) {
        is SnapshotResult.Success -> throw AssertionError(
            "Expected snapshot failure, got ${result.value}",
        )
        is SnapshotResult.Failure -> result.problems
    }

    private fun restoreSuccess(result: SnapshotResult<NavState>): NavState = when (result) {
        is SnapshotResult.Success -> result.value
        is SnapshotResult.Failure -> throw AssertionError(
            "Expected restored state, got ${result.problems}",
        )
    }

    private fun restoreFailure(result: SnapshotResult<NavState>): List<SnapshotProblem> =
        when (result) {
            is SnapshotResult.Success -> throw AssertionError(
                "Expected restore failure, got ${result.value}",
            )
            is SnapshotResult.Failure -> result.problems
        }

    private data class ConsumerRoute(val slug: String) : ContentRoute

    private object MissingKindRoute : Route

    private object AmbiguousKindRoute : ContentRoute, ModalRoute

    private object ConsumerRouteCodec : RouteCodec {
        override fun encode(route: Route): RouteEncodeResult = when (route) {
            is ConsumerRoute -> RouteEncodeResult.Success(
                EncodedRoute(
                    type = "consumer/v1",
                    arguments = mapOf("slug" to route.slug),
                ),
            )
            else -> RouteEncodeResult.Failure(
                RouteCodecError("unsupported_route", "Unsupported consumer route."),
            )
        }

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = if (
            type == "consumer/v1" &&
            arguments.keys == setOf("slug")
        ) {
            RouteDecodeResult.Success(ConsumerRoute(arguments.getValue("slug")))
        } else {
            RouteDecodeResult.Failure(
                RouteCodecError("invalid_consumer_route", "Invalid consumer route."),
            )
        }
    }

    private object RouteKindCodec : RouteCodec {
        override fun encode(route: Route): RouteEncodeResult = RouteEncodeResult.Failure(
            RouteCodecError("not_used", "Encoding is not used by this test codec."),
        )

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = when (type) {
            "home/v1" -> RouteDecodeResult.Success(Home)
            "missing-kind/v1" -> RouteDecodeResult.Success(MissingKindRoute)
            "ambiguous-kind/v1" -> RouteDecodeResult.Success(AmbiguousKindRoute)
            else -> RouteDecodeResult.Failure(
                RouteCodecError("unknown_route_type", "Unknown test route type."),
            )
        }
    }

    private object EncodeFailureCodec : RouteCodec {
        override fun encode(route: Route): RouteEncodeResult =
            RouteEncodeResult.Failure(EXPLICIT_CODEC_ERROR)

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = RouteDecodeResult.Success(Home)
    }

    private object DecodeFailureCodec : RouteCodec {
        override fun encode(route: Route): RouteEncodeResult = RouteEncodeResult.Success(
            EncodedRoute("unused/v1", emptyMap()),
        )

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = RouteDecodeResult.Failure(EXPLICIT_CODEC_ERROR)
    }

    private object ThrowingEncodeCodec : RouteCodec {
        override fun encode(route: Route): RouteEncodeResult =
            throw IllegalStateException("encode boom")

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = RouteDecodeResult.Success(Home)
    }

    private object ThrowingDecodeCodec : RouteCodec {
        override fun encode(route: Route): RouteEncodeResult = RouteEncodeResult.Success(
            EncodedRoute("unused/v1", emptyMap()),
        )

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = throw IllegalStateException("decode boom")
    }

    private companion object {
        val EXPLICIT_CODEC_ERROR = RouteCodecError(
            code = "consumer_failure",
            message = "Consumer codec rejected the route.",
        )
    }
}
