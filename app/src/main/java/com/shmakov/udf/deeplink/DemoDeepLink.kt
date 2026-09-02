package com.shmakov.udf.deeplink

import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.NavStateProblem
import com.shmakov.udf.navigation.Route
import java.util.UUID

internal sealed interface DemoDeepLinkTarget

internal data class AccountDetailsTarget(
    val accountId: Int,
) : DemoDeepLinkTarget

internal data class NormalizedDemoDeepLink(
    val target: DemoDeepLinkTarget,
    val canonicalUri: String,
)

internal enum class DemoDeepLinkNotHandledReason {
    UnsupportedScheme,
    UnsupportedHost,
}

internal enum class DemoDeepLinkProblem {
    OpaqueUriNotAllowed,
    UserInfoNotAllowed,
    PortNotAllowed,
    QueryNotAllowed,
    FragmentNotAllowed,
    MissingAccountId,
    InvalidAccountId,
    NonPositiveAccountId,
    UnsupportedPath,
}

internal sealed class DemoDeepLinkNormalizationResult {
    data class Normalized(
        val deepLink: NormalizedDemoDeepLink,
    ) : DemoDeepLinkNormalizationResult()

    data class NotHandled(
        val reason: DemoDeepLinkNotHandledReason,
    ) : DemoDeepLinkNormalizationResult()

    data class Rejected(
        val problem: DemoDeepLinkProblem,
    ) : DemoDeepLinkNormalizationResult()
}

/** Recognizes and canonicalizes the one intentionally supported demo link. */
internal object DemoDeepLinkNormalizer {
    private const val SCHEME = "udf-sandbox"
    private const val HOST = "accounts"
    private val accountDetailsPath = Regex("^/([^/]+)/details$")

    fun normalize(uri: ParsedDeepLinkUri): DemoDeepLinkNormalizationResult {
        if (uri.scheme != SCHEME) {
            return DemoDeepLinkNormalizationResult.NotHandled(
                DemoDeepLinkNotHandledReason.UnsupportedScheme,
            )
        }
        if (uri.isOpaque) return rejected(DemoDeepLinkProblem.OpaqueUriNotAllowed)
        if (uri.host != HOST) {
            return DemoDeepLinkNormalizationResult.NotHandled(
                DemoDeepLinkNotHandledReason.UnsupportedHost,
            )
        }
        if (uri.rawUserInfo != null) return rejected(DemoDeepLinkProblem.UserInfoNotAllowed)
        if (uri.port != -1) return rejected(DemoDeepLinkProblem.PortNotAllowed)
        if (uri.rawQuery != null) return rejected(DemoDeepLinkProblem.QueryNotAllowed)
        if (uri.rawFragment != null) return rejected(DemoDeepLinkProblem.FragmentNotAllowed)

        val match = accountDetailsPath.matchEntire(uri.rawPath)
        if (match == null) {
            val missingIdShapes = setOf("", "/", "/details", "//details")
            return if (uri.rawPath in missingIdShapes) {
                rejected(DemoDeepLinkProblem.MissingAccountId)
            } else {
                rejected(DemoDeepLinkProblem.UnsupportedPath)
            }
        }

        val rawAccountId = match.groupValues[1]
        val isNegativeDecimal = rawAccountId.startsWith("-") &&
            rawAccountId.length > 1 &&
            rawAccountId.drop(1).all(Char::isAsciiDigit)
        if (!rawAccountId.all(Char::isAsciiDigit) && !isNegativeDecimal) {
            return rejected(DemoDeepLinkProblem.InvalidAccountId)
        }

        val accountId = rawAccountId.toIntOrNull()
            ?: return rejected(DemoDeepLinkProblem.InvalidAccountId)
        if (accountId <= 0) return rejected(DemoDeepLinkProblem.NonPositiveAccountId)

        return DemoDeepLinkNormalizationResult.Normalized(
            NormalizedDemoDeepLink(
                target = AccountDetailsTarget(accountId),
                canonicalUri = "$SCHEME://$HOST/$accountId/details",
            ),
        )
    }

    private fun rejected(
        problem: DemoDeepLinkProblem,
    ): DemoDeepLinkNormalizationResult.Rejected =
        DemoDeepLinkNormalizationResult.Rejected(problem)
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

/** Creates one exact route identity; tests may inject a deterministic sequence. */
internal fun interface DeepLinkEntryIdFactory {
    fun create(): EntryId
}

internal object AccountDetailsDeepLinkHistoryFactory {

    fun create(
        target: AccountDetailsTarget,
        entryIdFactory: DeepLinkEntryIdFactory = FreshDeepLinkEntryIdFactory,
    ): NavStateCreationResult = createHistory(
        routes = listOf(
            Home,
            Accounts,
            Account(target.accountId),
            AccountDetails(target.accountId),
        ),
        entryIdFactory = entryIdFactory,
    )
}

/** Creates the complete leaf history used when the Accounts tab owns the deep link. */
internal object AccountDetailsTabDeepLinkHistoryFactory {

    fun create(
        target: AccountDetailsTarget,
        entryIdFactory: DeepLinkEntryIdFactory = FreshDeepLinkEntryIdFactory,
    ): NavStateCreationResult = createHistory(
        routes = listOf(
            Accounts,
            Account(target.accountId),
            AccountDetails(target.accountId),
        ),
        entryIdFactory = entryIdFactory,
    )
}

private fun createHistory(
    routes: List<Route>,
    entryIdFactory: DeepLinkEntryIdFactory,
): NavStateCreationResult {
    val entries = routes.map { route ->
        BackStackEntry(
            id = entryIdFactory.create(),
            route = route,
        )
    }

    // Every hydration path deliberately shares the structural validation boundary used by
    // snapshot restoration instead of constructing a privileged NavState.
    return NavState.fromEntries(entries)
}

internal object FreshDeepLinkEntryIdFactory : DeepLinkEntryIdFactory {
    override fun create(): EntryId = EntryId(UUID.randomUUID().toString())
}

internal sealed class DemoDeepLinkResolutionProblem {
    data class UriParsing(
        val problem: DeepLinkUriParseProblem,
    ) : DemoDeepLinkResolutionProblem()

    data class Normalization(
        val problem: DemoDeepLinkProblem,
    ) : DemoDeepLinkResolutionProblem()

    data class InvalidNavigationState(
        val problems: List<NavStateProblem>,
    ) : DemoDeepLinkResolutionProblem()
}

internal sealed class DemoDeepLinkResolution {
    data class Resolved(
        val deepLink: NormalizedDemoDeepLink,
        val navState: NavState,
    ) : DemoDeepLinkResolution()

    data class NotHandled(
        val reason: DemoDeepLinkNotHandledReason,
    ) : DemoDeepLinkResolution()

    data class Rejected(
        val problem: DemoDeepLinkResolutionProblem,
    ) : DemoDeepLinkResolution()
}

/** Parse-and-normalize result shared by linear and tab-specific history factories. */
internal sealed class DemoDeepLinkTargetResolution {
    data class Resolved(
        val deepLink: NormalizedDemoDeepLink,
    ) : DemoDeepLinkTargetResolution()

    data class NotHandled(
        val reason: DemoDeepLinkNotHandledReason,
    ) : DemoDeepLinkTargetResolution()

    data class Rejected(
        val problem: DemoDeepLinkResolutionProblem,
    ) : DemoDeepLinkTargetResolution()
}

/** Pure URI parse and normalization boundary; history shape remains an application decision. */
internal object DemoDeepLinkTargetResolver {

    fun resolve(rawUri: String?): DemoDeepLinkTargetResolution {
        val parsedUri = when (val parsing = DeepLinkUriParser.parse(rawUri)) {
            is DeepLinkUriParseResult.Parsed -> parsing.uri
            is DeepLinkUriParseResult.Rejected -> return DemoDeepLinkTargetResolution.Rejected(
                DemoDeepLinkResolutionProblem.UriParsing(parsing.problem),
            )
        }

        return when (val normalization = DemoDeepLinkNormalizer.normalize(parsedUri)) {
            is DemoDeepLinkNormalizationResult.Normalized ->
                DemoDeepLinkTargetResolution.Resolved(normalization.deepLink)

            is DemoDeepLinkNormalizationResult.NotHandled ->
                DemoDeepLinkTargetResolution.NotHandled(normalization.reason)

            is DemoDeepLinkNormalizationResult.Rejected ->
                DemoDeepLinkTargetResolution.Rejected(
                    DemoDeepLinkResolutionProblem.Normalization(normalization.problem),
                )
        }
    }
}

/** Pure parse -> normalize -> hydrate pipeline used by every Android entry point. */
internal object DemoDeepLinkResolver {

    fun resolve(
        rawUri: String?,
        entryIdFactory: DeepLinkEntryIdFactory = FreshDeepLinkEntryIdFactory,
    ): DemoDeepLinkResolution {
        val normalized = when (val target = DemoDeepLinkTargetResolver.resolve(rawUri)) {
            is DemoDeepLinkTargetResolution.Resolved -> target.deepLink
            is DemoDeepLinkTargetResolution.NotHandled -> {
                return DemoDeepLinkResolution.NotHandled(target.reason)
            }
            is DemoDeepLinkTargetResolution.Rejected -> {
                return DemoDeepLinkResolution.Rejected(target.problem)
            }
        }

        val hydration = when (val target = normalized.target) {
            is AccountDetailsTarget -> AccountDetailsDeepLinkHistoryFactory.create(
                target = target,
                entryIdFactory = entryIdFactory,
            )
        }
        return when (hydration) {
            is NavStateCreationResult.Valid -> DemoDeepLinkResolution.Resolved(
                deepLink = normalized,
                navState = hydration.state,
            )
            is NavStateCreationResult.Invalid -> DemoDeepLinkResolution.Rejected(
                DemoDeepLinkResolutionProblem.InvalidNavigationState(hydration.problems),
            )
        }
    }
}
