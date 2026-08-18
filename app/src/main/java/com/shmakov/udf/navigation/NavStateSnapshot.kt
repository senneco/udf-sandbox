package com.shmakov.udf.navigation

import java.util.Collections
import java.util.LinkedHashMap

/** Primitive, transport-agnostic representation of a [NavState]. */
data class NavStateSnapshot(
    val version: Int,
    val entries: List<Entry>,
) {
    data class Entry(
        val id: String,
        val routeType: String,
        val arguments: Map<String, String>,
    )

    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}

/** Stable route discriminator and primitive arguments produced by a [RouteCodec]. */
data class EncodedRoute(
    val type: String,
    val arguments: Map<String, String>,
)

/** Application-defined codec failure that can cross the navigation-core boundary safely. */
data class RouteCodecError(
    val code: String,
    val message: String,
)

/**
 * Maps application-owned [Route] implementations to stable primitive data and back.
 *
 * Type keys should be explicit and versioned, for example `profile/v1`; class names are not a
 * stable persistence format. Snapshot failures add the affected entry index and identity.
 */
interface RouteCodec {
    fun encode(route: Route): RouteEncodeResult

    fun decode(
        type: String,
        arguments: Map<String, String>,
    ): RouteDecodeResult
}

/** Explicit result returned by [RouteCodec.encode]. */
sealed class RouteEncodeResult {
    data class Success(val encodedRoute: EncodedRoute) : RouteEncodeResult()

    data class Failure(val error: RouteCodecError) : RouteEncodeResult()
}

/** Explicit result returned by [RouteCodec.decode]. */
sealed class RouteDecodeResult {
    data class Success(val route: Route) : RouteDecodeResult()

    data class Failure(val error: RouteCodecError) : RouteDecodeResult()
}

/** Result of encoding or restoring a navigation snapshot. */
sealed class SnapshotResult<out T> {
    data class Success<T>(val value: T) : SnapshotResult<T>()

    data class Failure(val problems: List<SnapshotProblem>) : SnapshotResult<Nothing>()
}

/** A contextual snapshot failure; codec failures include the affected entry location. */
sealed class SnapshotProblem {
    data class UnsupportedVersion(val version: Int) : SnapshotProblem()

    data class RouteEncodeFailed(
        val index: Int,
        val entryId: EntryId,
        val error: RouteCodecError,
    ) : SnapshotProblem()

    data class RouteDecodeFailed(
        val index: Int,
        val entryId: String,
        val routeType: String,
        val error: RouteCodecError,
    ) : SnapshotProblem()

    data class InvalidState(val problem: NavStateProblem) : SnapshotProblem()
}

/** Stable codec for the routes used by the demo application. */
object DemoRouteCodec : RouteCodec {
    override fun encode(route: Route): RouteEncodeResult = when (route) {
        Home -> encoded(HOME_TYPE)
        Accounts -> encoded(ACCOUNTS_TYPE)
        is Account -> encoded(ACCOUNT_TYPE, ACCOUNT_ID_ARGUMENT to route.accountId.toString())
        is AccountDetails -> encoded(
            ACCOUNT_DETAILS_TYPE,
            ACCOUNT_ID_ARGUMENT to route.accountId.toString(),
        )
        Transactions -> encoded(TRANSACTIONS_TYPE)
        is Transaction -> encoded(
            TRANSACTION_TYPE,
            TRANSACTION_ID_ARGUMENT to route.transactionId.toString(),
        )
        Cards -> encoded(CARDS_TYPE)
        is Card -> encoded(CARD_TYPE, CARD_ID_ARGUMENT to route.cardId.toString())
        else -> RouteEncodeResult.Failure(
            RouteCodecError(
                code = UNSUPPORTED_ROUTE_CODE,
                message = "Route is not supported by DemoRouteCodec.",
            ),
        )
    }

    override fun decode(
        type: String,
        arguments: Map<String, String>,
    ): RouteDecodeResult = when (type) {
        HOME_TYPE -> decodeWithoutArguments(type, arguments, Home)
        ACCOUNTS_TYPE -> decodeWithoutArguments(type, arguments, Accounts)
        ACCOUNT_TYPE -> decodeIntArgument(
            type = type,
            arguments = arguments,
            argumentName = ACCOUNT_ID_ARGUMENT,
            createRoute = ::Account,
        )
        ACCOUNT_DETAILS_TYPE -> decodeIntArgument(
            type = type,
            arguments = arguments,
            argumentName = ACCOUNT_ID_ARGUMENT,
            createRoute = ::AccountDetails,
        )
        TRANSACTIONS_TYPE -> decodeWithoutArguments(type, arguments, Transactions)
        TRANSACTION_TYPE -> decodeIntArgument(
            type = type,
            arguments = arguments,
            argumentName = TRANSACTION_ID_ARGUMENT,
            createRoute = ::Transaction,
        )
        CARDS_TYPE -> decodeWithoutArguments(type, arguments, Cards)
        CARD_TYPE -> decodeIntArgument(
            type = type,
            arguments = arguments,
            argumentName = CARD_ID_ARGUMENT,
            createRoute = ::Card,
        )
        else -> RouteDecodeResult.Failure(
            RouteCodecError(
                code = UNKNOWN_ROUTE_TYPE_CODE,
                message = "Unknown route type: $type",
            ),
        )
    }

    private fun encoded(
        type: String,
        vararg arguments: Pair<String, String>,
    ): RouteEncodeResult = RouteEncodeResult.Success(
        EncodedRoute(
            type = type,
            arguments = mapOf(*arguments),
        ),
    )

    private fun decodeWithoutArguments(
        type: String,
        arguments: Map<String, String>,
        route: Route,
    ): RouteDecodeResult = if (arguments.isEmpty()) {
        RouteDecodeResult.Success(route)
    } else {
        malformedRoute(type)
    }

    private fun decodeIntArgument(
        type: String,
        arguments: Map<String, String>,
        argumentName: String,
        createRoute: (Int) -> Route,
    ): RouteDecodeResult {
        val value = arguments[argumentName]?.toIntOrNull()
        return if (value != null && arguments.keys == setOf(argumentName)) {
            RouteDecodeResult.Success(createRoute(value))
        } else {
            malformedRoute(type)
        }
    }

    private fun malformedRoute(type: String): RouteDecodeResult =
        RouteDecodeResult.Failure(
            RouteCodecError(
                code = MALFORMED_ROUTE_ARGUMENTS_CODE,
                message = "Malformed arguments for route type: $type",
            ),
        )

    private const val HOME_TYPE = "home/v1"
    private const val ACCOUNTS_TYPE = "accounts/v1"
    private const val ACCOUNT_TYPE = "account/v1"
    private const val ACCOUNT_DETAILS_TYPE = "account-details/v1"
    private const val TRANSACTIONS_TYPE = "transactions/v1"
    private const val TRANSACTION_TYPE = "transaction/v1"
    private const val CARDS_TYPE = "cards/v1"
    private const val CARD_TYPE = "card/v1"

    private const val ACCOUNT_ID_ARGUMENT = "accountId"
    private const val TRANSACTION_ID_ARGUMENT = "transactionId"
    private const val CARD_ID_ARGUMENT = "cardId"

    private const val UNSUPPORTED_ROUTE_CODE = "unsupported_route"
    private const val UNKNOWN_ROUTE_TYPE_CODE = "unknown_route_type"
    private const val MALFORMED_ROUTE_ARGUMENTS_CODE = "malformed_route_arguments"
}

@JvmSynthetic
internal fun snapshotNavState(
    state: NavState,
    codec: RouteCodec,
): SnapshotResult<NavStateSnapshot> {
    val problems = mutableListOf<SnapshotProblem>()
    val snapshotEntries = mutableListOf<NavStateSnapshot.Entry>()

    state.entries.forEachIndexed { index, entry ->
        val result: RouteEncodeResult? = try {
            codec.encode(entry.route)
        } catch (exception: Exception) {
            RouteEncodeResult.Failure(exception.toCodecError())
        }

        when (result) {
            is RouteEncodeResult.Success -> snapshotEntries += NavStateSnapshot.Entry(
                id = entry.id.value,
                routeType = result.encodedRoute.type,
                arguments = immutableMapCopy(result.encodedRoute.arguments),
            )
            is RouteEncodeResult.Failure -> problems += SnapshotProblem.RouteEncodeFailed(
                index = index,
                entryId = entry.id,
                error = result.error,
            )
            null -> problems += SnapshotProblem.RouteEncodeFailed(
                index = index,
                entryId = entry.id,
                error = nullCodecResultError(),
            )
        }
    }

    return if (problems.isEmpty()) {
        SnapshotResult.Success(
            NavStateSnapshot(
                version = NavStateSnapshot.CURRENT_VERSION,
                entries = immutableListCopy(snapshotEntries),
            ),
        )
    } else {
        SnapshotResult.Failure(immutableListCopy(problems))
    }
}

@JvmSynthetic
internal fun restoreNavState(
    snapshot: NavStateSnapshot,
    codec: RouteCodec,
): SnapshotResult<NavState> {
    if (snapshot.version != NavStateSnapshot.CURRENT_VERSION) {
        return SnapshotResult.Failure(
            listOf(SnapshotProblem.UnsupportedVersion(snapshot.version)),
        )
    }

    val problems = mutableListOf<SnapshotProblem>()
    val entries = mutableListOf<BackStackEntry>()
    val snapshotEntries = ArrayList(snapshot.entries)

    snapshotEntries.forEachIndexed { index, snapshotEntry ->
        val result: RouteDecodeResult? = try {
            codec.decode(
                type = snapshotEntry.routeType,
                arguments = immutableMapCopy(snapshotEntry.arguments),
            )
        } catch (exception: Exception) {
            RouteDecodeResult.Failure(exception.toCodecError())
        }

        when (result) {
            is RouteDecodeResult.Success -> entries += BackStackEntry(
                id = EntryId(snapshotEntry.id),
                route = result.route,
            )
            is RouteDecodeResult.Failure -> problems += SnapshotProblem.RouteDecodeFailed(
                index = index,
                entryId = snapshotEntry.id,
                routeType = snapshotEntry.routeType,
                error = result.error,
            )
            null -> problems += SnapshotProblem.RouteDecodeFailed(
                index = index,
                entryId = snapshotEntry.id,
                routeType = snapshotEntry.routeType,
                error = nullCodecResultError(),
            )
        }
    }

    if (problems.isNotEmpty()) {
        return SnapshotResult.Failure(immutableListCopy(problems))
    }

    return when (val stateResult = NavState.fromEntries(entries)) {
        is NavStateCreationResult.Valid -> SnapshotResult.Success(stateResult.state)
        is NavStateCreationResult.Invalid -> SnapshotResult.Failure(
            immutableListCopy(stateResult.problems.map(SnapshotProblem::InvalidState)),
        )
    }
}

private fun Exception.toCodecError(): RouteCodecError = RouteCodecError(
    code = "codec_exception",
    message = message ?: javaClass.name,
)

private fun nullCodecResultError(): RouteCodecError = RouteCodecError(
    code = "codec_exception",
    message = "Route codec returned null.",
)

private fun <T> immutableListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))

private fun immutableMapCopy(source: Map<String, String>): Map<String, String> =
    Collections.unmodifiableMap(LinkedHashMap(source))
