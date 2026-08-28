package com.shmakov.udf

import com.shmakov.udf.navigation.TabNavigationStateSnapshot
import java.util.Collections

/** A malformed or unsupported primitive tab-navigation persistence envelope. */
internal sealed class TabNavigationSnapshotEnvelopeProblem {
    data class InvalidPayload(
        val code: String,
        val message: String,
    ) : TabNavigationSnapshotEnvelopeProblem()

    data class UnsupportedEnvelopeVersion(
        val version: Int,
    ) : TabNavigationSnapshotEnvelopeProblem()

    data class HistoryEnvelopeProblem(
        val tabIndex: Int,
        val tabId: String,
        val problem: NavigationSnapshotEnvelopeProblem,
    ) : TabNavigationSnapshotEnvelopeProblem()
}

/** Result of decoding the one-value Android representation of a tab graph snapshot. */
internal sealed class TabNavigationSnapshotEnvelopeDecodeResult {
    data class Decoded(
        val snapshot: TabNavigationStateSnapshot,
    ) : TabNavigationSnapshotEnvelopeDecodeResult()

    class Rejected(
        problems: List<TabNavigationSnapshotEnvelopeProblem>,
    ) : TabNavigationSnapshotEnvelopeDecodeResult() {
        val problems: List<TabNavigationSnapshotEnvelopeProblem> =
            immutableTabEnvelopeListCopy(problems)

        override fun equals(other: Any?): Boolean =
            this === other || other is Rejected && problems == other.problems

        override fun hashCode(): Int = problems.hashCode()

        override fun toString(): String = "Rejected(problems=$problems)"
    }
}

/**
 * Converts a complete [TabNavigationStateSnapshot] to one flat Bundle-safe primitive value.
 *
 * Every history is a length-prefixed complete [NavigationSnapshotEnvelopeCodec] payload. Outer,
 * leaf-envelope, graph-snapshot, leaf-snapshot, and route payload versions can therefore evolve
 * independently without duplicating the strict leaf parser.
 */
internal object TabNavigationSnapshotEnvelopeCodec {
    fun encode(snapshot: TabNavigationStateSnapshot): ArrayList<String> =
        arrayListOf<String>().apply {
            add(MAGIC)
            add(CURRENT_ENVELOPE_VERSION.toString())
            add(snapshot.version.toString())
            add(snapshot.selectedTabId)
            add(snapshot.tabs.size.toString())

            snapshot.tabs.forEach { tab ->
                val historyPayload = NavigationSnapshotEnvelopeCodec.encode(tab.history)
                add(tab.id)
                add(historyPayload.size.toString())
                addAll(historyPayload)
            }
        }

    fun decode(value: Any?): TabNavigationSnapshotEnvelopeDecodeResult {
        val rawTokens = value as? ArrayList<*>
            ?: return invalid(
                code = WRONG_TYPE_CODE,
                message = "Tab navigation payload must be an ArrayList of strings.",
            )
        val tokens = try {
            ArrayList<String>(rawTokens.size).apply {
                rawTokens.forEach { token ->
                    add(
                        token as? String
                            ?: return invalid(
                                code = WRONG_TYPE_CODE,
                                message = "Every tab navigation payload token must be a string.",
                            ),
                    )
                }
            }
        } catch (exception: Exception) {
            return invalid(
                code = INVALID_PAYLOAD_CODE,
                message = exception.message ?: "Tab navigation payload could not be read.",
            )
        }
        var cursor = 0

        fun nextToken(): String? = tokens.getOrNull(cursor)?.also { cursor += 1 }
        fun truncated(): TabNavigationSnapshotEnvelopeDecodeResult = invalid(
            code = TRUNCATED_CODE,
            message = "Tab navigation payload ended before its declared data was complete.",
        )

        val magic = nextToken() ?: return truncated()
        if (magic != MAGIC) {
            return invalid(
                code = INVALID_MAGIC_CODE,
                message = "Tab navigation payload has an unknown magic header.",
            )
        }

        val envelopeVersionToken = nextToken() ?: return truncated()
        val envelopeVersion = envelopeVersionToken.toIntOrNull()
            ?: return invalid(
                code = INVALID_ENVELOPE_VERSION_CODE,
                message = "Tab navigation envelope version is not an integer.",
            )
        if (envelopeVersion != CURRENT_ENVELOPE_VERSION) {
            return TabNavigationSnapshotEnvelopeDecodeResult.Rejected(
                listOf(
                    TabNavigationSnapshotEnvelopeProblem.UnsupportedEnvelopeVersion(
                        version = envelopeVersion,
                    ),
                ),
            )
        }

        val snapshotVersionToken = nextToken() ?: return truncated()
        val snapshotVersion = snapshotVersionToken.toIntOrNull()
            ?: return invalid(
                code = INVALID_SNAPSHOT_VERSION_CODE,
                message = "Tab navigation snapshot version is not an integer.",
            )
        val selectedTabId = nextToken() ?: return truncated()
        val tabCountToken = nextToken() ?: return truncated()
        val tabCount = tabCountToken.toNonNegativeCountOrNull()
            ?: return invalidCount("tab")
        if (tabCount > (tokens.size - cursor) / MIN_TAB_HEADER_TOKEN_COUNT) {
            return truncated()
        }

        val problems = mutableListOf<TabNavigationSnapshotEnvelopeProblem>()
        val tabs = ArrayList<TabNavigationStateSnapshot.Tab>(tabCount)
        repeat(tabCount) { tabIndex ->
            val tabId = nextToken() ?: return truncated()
            val historyTokenCountToken = nextToken() ?: return truncated()
            val historyTokenCount = historyTokenCountToken.toNonNegativeCountOrNull()
                ?: return invalidCount("history token")
            val remainingTokenCount = tokens.size - cursor
            if (historyTokenCount > remainingTokenCount) return truncated()

            val historyPayload = ArrayList(tokens.subList(cursor, cursor + historyTokenCount))
            cursor += historyTokenCount
            when (val history = NavigationSnapshotEnvelopeCodec.decode(historyPayload)) {
                is NavigationSnapshotEnvelopeDecodeResult.Decoded ->
                    tabs += TabNavigationStateSnapshot.Tab(
                        id = tabId,
                        history = history.snapshot,
                    )
                is NavigationSnapshotEnvelopeDecodeResult.Rejected ->
                    problems += history.problems.map { problem ->
                        TabNavigationSnapshotEnvelopeProblem.HistoryEnvelopeProblem(
                            tabIndex = tabIndex,
                            tabId = tabId,
                            problem = problem,
                        )
                    }
            }
        }

        if (cursor != tokens.size) {
            return invalid(
                code = TRAILING_TOKENS_CODE,
                message = "Tab navigation payload contains data after its declared tabs.",
            )
        }
        if (problems.isNotEmpty()) {
            return TabNavigationSnapshotEnvelopeDecodeResult.Rejected(problems)
        }

        return TabNavigationSnapshotEnvelopeDecodeResult.Decoded(
            TabNavigationStateSnapshot(
                version = snapshotVersion,
                selectedTabId = selectedTabId,
                tabs = tabs,
            ),
        )
    }

    private fun String.toNonNegativeCountOrNull(): Int? =
        toIntOrNull()?.takeIf { count -> count >= 0 }

    private fun invalidCount(subject: String): TabNavigationSnapshotEnvelopeDecodeResult =
        invalid(
            code = INVALID_COUNT_CODE,
            message = "Tab navigation $subject count must be a non-negative integer.",
        )

    private fun invalid(
        code: String,
        message: String,
    ): TabNavigationSnapshotEnvelopeDecodeResult =
        TabNavigationSnapshotEnvelopeDecodeResult.Rejected(
            listOf(TabNavigationSnapshotEnvelopeProblem.InvalidPayload(code, message)),
        )

    private const val MAGIC = "udf-tab-nav"
    private const val CURRENT_ENVELOPE_VERSION = 1
    private const val MIN_TAB_HEADER_TOKEN_COUNT = 2

    private const val WRONG_TYPE_CODE = "wrong_type"
    private const val INVALID_PAYLOAD_CODE = "invalid_payload"
    private const val INVALID_MAGIC_CODE = "invalid_magic"
    private const val INVALID_ENVELOPE_VERSION_CODE = "invalid_envelope_version"
    private const val INVALID_SNAPSHOT_VERSION_CODE = "invalid_snapshot_version"
    private const val INVALID_COUNT_CODE = "invalid_count"
    private const val TRUNCATED_CODE = "truncated"
    private const val TRAILING_TOKENS_CODE = "trailing_tokens"
}

private fun <T> immutableTabEnvelopeListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))
