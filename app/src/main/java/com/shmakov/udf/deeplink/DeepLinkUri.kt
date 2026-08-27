package com.shmakov.udf.deeplink

import java.net.URI
import java.net.URISyntaxException

/** Framework-free URI data retained without path or query decoding. */
internal data class ParsedDeepLinkUri(
    val scheme: String?,
    val host: String?,
    val rawPath: String,
    val rawQuery: String?,
    val rawFragment: String?,
    val rawUserInfo: String?,
    val port: Int,
    val isOpaque: Boolean,
)

internal enum class DeepLinkUriParseProblem {
    MissingUri,
    MalformedUri,
}

internal sealed class DeepLinkUriParseResult {
    data class Parsed(val uri: ParsedDeepLinkUri) : DeepLinkUriParseResult()

    data class Rejected(val problem: DeepLinkUriParseProblem) : DeepLinkUriParseResult()
}

/** Parses transport syntax only. Destination recognition belongs to a separate normalizer. */
internal object DeepLinkUriParser {

    fun parse(rawUri: String?): DeepLinkUriParseResult {
        if (rawUri.isNullOrBlank()) {
            return DeepLinkUriParseResult.Rejected(DeepLinkUriParseProblem.MissingUri)
        }

        val uri = try {
            URI(rawUri)
        } catch (_: URISyntaxException) {
            return DeepLinkUriParseResult.Rejected(DeepLinkUriParseProblem.MalformedUri)
        }

        return DeepLinkUriParseResult.Parsed(
            ParsedDeepLinkUri(
                scheme = uri.scheme,
                host = uri.host,
                rawPath = uri.rawPath.orEmpty(),
                rawQuery = uri.rawQuery,
                rawFragment = uri.rawFragment,
                rawUserInfo = uri.rawUserInfo,
                port = uri.port,
                isOpaque = uri.isOpaque,
            ),
        )
    }
}
