package com.shmakov.udf

import com.shmakov.udf.navigation.NavStateSnapshot
import java.util.Collections
import java.util.LinkedHashMap

/** A malformed or unsupported primitive navigation persistence envelope. */
internal sealed class NavigationSnapshotEnvelopeProblem {
    data class InvalidPayload(
        val code: String,
        val message: String,
    ) : NavigationSnapshotEnvelopeProblem()

    data class UnsupportedEnvelopeVersion(
        val version: Int,
    ) : NavigationSnapshotEnvelopeProblem()
}

/** Result of decoding the Android saved-state representation of a navigation snapshot. */
internal sealed class NavigationSnapshotEnvelopeDecodeResult {
    data class Decoded(
        val snapshot: NavStateSnapshot,
    ) : NavigationSnapshotEnvelopeDecodeResult()

    data class Rejected(
        val problems: List<NavigationSnapshotEnvelopeProblem>,
    ) : NavigationSnapshotEnvelopeDecodeResult()
}

/**
 * Converts [NavStateSnapshot] to one Bundle-safe primitive value.
 *
 * The wire format contains only strings and explicit counts. Argument keys are sorted so equal
 * snapshots always produce equal payloads regardless of their source map implementation.
 */
internal object NavigationSnapshotEnvelopeCodec {
    fun encode(snapshot: NavStateSnapshot): ArrayList<String> = arrayListOf<String>().apply {
        add(MAGIC)
        add(CURRENT_ENVELOPE_VERSION.toString())
        add(snapshot.version.toString())
        add(snapshot.entries.size.toString())

        snapshot.entries.forEach { entry ->
            add(entry.id)
            add(entry.routeType)
            add(entry.arguments.size.toString())
            entry.arguments.toSortedMap().forEach { (key, value) ->
                add(key)
                add(value)
            }
        }
    }

    fun decode(value: Any?): NavigationSnapshotEnvelopeDecodeResult {
        val rawTokens = value as? ArrayList<*>
            ?: return invalid(
                code = WRONG_TYPE_CODE,
                message = "Navigation payload must be an ArrayList of strings.",
            )
        val tokens = try {
            ArrayList<String>(rawTokens.size).apply {
                rawTokens.forEach { token ->
                    add(
                        token as? String
                            ?: return invalid(
                                code = WRONG_TYPE_CODE,
                                message = "Every navigation payload token must be a string.",
                            ),
                    )
                }
            }
        } catch (exception: Exception) {
            return invalid(
                code = INVALID_PAYLOAD_CODE,
                message = exception.message ?: "Navigation payload could not be read.",
            )
        }
        var cursor = 0

        fun nextToken(): String? = tokens.getOrNull(cursor)?.also { cursor += 1 }
        fun truncated(): NavigationSnapshotEnvelopeDecodeResult = invalid(
            code = TRUNCATED_CODE,
            message = "Navigation payload ended before its declared data was complete.",
        )

        val magic = nextToken() ?: return truncated()
        if (magic != MAGIC) {
            return invalid(
                code = INVALID_MAGIC_CODE,
                message = "Navigation payload has an unknown magic header.",
            )
        }

        val envelopeVersionToken = nextToken() ?: return truncated()
        val envelopeVersion = envelopeVersionToken.toIntOrNull()
            ?: return invalid(
                code = INVALID_ENVELOPE_VERSION_CODE,
                message = "Navigation envelope version is not an integer.",
            )
        if (envelopeVersion != CURRENT_ENVELOPE_VERSION) {
            return NavigationSnapshotEnvelopeDecodeResult.Rejected(
                listOf(
                    NavigationSnapshotEnvelopeProblem.UnsupportedEnvelopeVersion(
                        version = envelopeVersion,
                    ),
                ),
            )
        }

        val snapshotVersionToken = nextToken() ?: return truncated()
        val snapshotVersion = snapshotVersionToken.toIntOrNull()
            ?: return invalid(
                code = INVALID_SNAPSHOT_VERSION_CODE,
                message = "Navigation snapshot version is not an integer.",
            )

        val entryCountToken = nextToken() ?: return truncated()
        val entryCount = entryCountToken.toNonNegativeCountOrNull()
            ?: return invalidCount("entry")
        if (entryCount > (tokens.size - cursor) / MIN_ENTRY_TOKEN_COUNT) {
            return truncated()
        }

        val entries = ArrayList<NavStateSnapshot.Entry>(entryCount)
        repeat(entryCount) {
            val id = nextToken() ?: return truncated()
            val routeType = nextToken() ?: return truncated()
            val argumentCountToken = nextToken() ?: return truncated()
            val argumentCount = argumentCountToken.toNonNegativeCountOrNull()
                ?: return invalidCount("argument")
            if (argumentCount > (tokens.size - cursor) / TOKENS_PER_ARGUMENT) {
                return truncated()
            }

            val arguments = LinkedHashMap<String, String>(argumentCount)
            repeat(argumentCount) {
                val key = nextToken() ?: return truncated()
                val argumentValue = nextToken() ?: return truncated()
                if (arguments.put(key, argumentValue) != null) {
                    return invalid(
                        code = DUPLICATE_ARGUMENT_KEY_CODE,
                        message = "Navigation route arguments contain a duplicate key.",
                    )
                }
            }

            entries += NavStateSnapshot.Entry(
                id = id,
                routeType = routeType,
                arguments = Collections.unmodifiableMap(LinkedHashMap(arguments)),
            )
        }

        if (cursor != tokens.size) {
            return invalid(
                code = TRAILING_TOKENS_CODE,
                message = "Navigation payload contains data after its declared entries.",
            )
        }

        return NavigationSnapshotEnvelopeDecodeResult.Decoded(
            NavStateSnapshot(
                version = snapshotVersion,
                entries = Collections.unmodifiableList(ArrayList(entries)),
            ),
        )
    }

    private fun String.toNonNegativeCountOrNull(): Int? =
        toIntOrNull()?.takeIf { count -> count >= 0 }

    private fun invalidCount(subject: String): NavigationSnapshotEnvelopeDecodeResult = invalid(
        code = INVALID_COUNT_CODE,
        message = "Navigation $subject count must be a non-negative integer.",
    )

    private fun invalid(
        code: String,
        message: String,
    ): NavigationSnapshotEnvelopeDecodeResult =
        NavigationSnapshotEnvelopeDecodeResult.Rejected(
            listOf(NavigationSnapshotEnvelopeProblem.InvalidPayload(code, message)),
        )

    private const val MAGIC = "udf-nav"
    private const val CURRENT_ENVELOPE_VERSION = 1
    private const val MIN_ENTRY_TOKEN_COUNT = 3
    private const val TOKENS_PER_ARGUMENT = 2

    private const val WRONG_TYPE_CODE = "wrong_type"
    private const val INVALID_PAYLOAD_CODE = "invalid_payload"
    private const val INVALID_MAGIC_CODE = "invalid_magic"
    private const val INVALID_ENVELOPE_VERSION_CODE = "invalid_envelope_version"
    private const val INVALID_SNAPSHOT_VERSION_CODE = "invalid_snapshot_version"
    private const val INVALID_COUNT_CODE = "invalid_count"
    private const val TRUNCATED_CODE = "truncated"
    private const val DUPLICATE_ARGUMENT_KEY_CODE = "duplicate_argument_key"
    private const val TRAILING_TOKENS_CODE = "trailing_tokens"
}
