package com.shmakov.udf.navigation

import java.util.Collections

/** Identifies one visible content slot independently of the route rendered in it. */
sealed class ContentSlotId {
    object Root : ContentSlotId()

    data class ChildOf(
        val ownerContentEntryId: EntryId,
    ) : ContentSlotId()

    companion object {
        @JvmStatic
        fun root(): ContentSlotId = Root

        @JvmStatic
        fun childOf(ownerContentEntryId: EntryId): ContentSlotId =
            ChildOf(ownerContentEntryId)
    }
}

/** One content entry and the slot in which it is currently visible. */
data class ContentSlot(
    val slotId: ContentSlotId,
    val entry: BackStackEntry,
)

/** One visible modal and the exact content occurrence that owns it. */
data class ModalLayer(
    val entry: BackStackEntry,
    val ownerContentEntryId: EntryId,
)

/** Immutable result of projecting a logical navigation history into visible UI roles. */
class NavigationRenderTree private constructor(
    val root: ContentSlot,
    nestedSlots: List<ContentSlot>,
    modalLayers: List<ModalLayer>,
) {
    val nestedSlots: List<ContentSlot> = projectionImmutableListCopy(nestedSlots)
    val modalLayers: List<ModalLayer> = projectionImmutableListCopy(modalLayers)

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is NavigationRenderTree &&
            root == other.root &&
            nestedSlots == other.nestedSlots &&
            modalLayers == other.modalLayers

    override fun hashCode(): Int {
        var result = root.hashCode()
        result = 31 * result + nestedSlots.hashCode()
        result = 31 * result + modalLayers.hashCode()
        return result
    }

    override fun toString(): String =
        "NavigationRenderTree(root=$root, nestedSlots=$nestedSlots, modalLayers=$modalLayers)"

    companion object {
        @JvmSynthetic
        internal fun create(
            root: ContentSlot,
            nestedSlots: List<ContentSlot>,
            modalLayers: List<ModalLayer>,
        ): NavigationRenderTree = NavigationRenderTree(root, nestedSlots, modalLayers)
    }
}

/** Context supplied when a non-root content entry needs a visible slot. */
class ContentPlacementRequest private constructor(
    contentPath: List<ContentSlot>,
    val currentContent: ContentSlot,
    val nextContent: BackStackEntry,
) {
    val contentPath: List<ContentSlot> = projectionImmutableListCopy(contentPath)

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is ContentPlacementRequest &&
            contentPath == other.contentPath &&
            currentContent == other.currentContent &&
            nextContent == other.nextContent

    override fun hashCode(): Int {
        var result = contentPath.hashCode()
        result = 31 * result + currentContent.hashCode()
        result = 31 * result + nextContent.hashCode()
        return result
    }

    override fun toString(): String =
        "ContentPlacementRequest(contentPath=$contentPath, " +
            "currentContent=$currentContent, nextContent=$nextContent)"

    companion object {
        @JvmSynthetic
        internal fun create(
            contentPath: List<ContentSlot>,
            currentContent: ContentSlot,
            nextContent: BackStackEntry,
        ): ContentPlacementRequest = ContentPlacementRequest(
            contentPath = contentPath,
            currentContent = currentContent,
            nextContent = nextContent,
        )
    }
}

/** Application-supplied, pure rule for placing content relative to the visible content path. */
fun interface NavigationLayoutPolicy {
    fun placeContent(request: ContentPlacementRequest): ContentPlacementDecision
}

/** Stable value description of why a layout policy cannot place an entry. */
data class LayoutPolicyError(
    val code: String,
    val message: String,
)

/** A layout policy's decision for one non-root content entry. */
sealed class ContentPlacementDecision {
    data class PlaceIn(
        val slotId: ContentSlotId,
    ) : ContentPlacementDecision()

    data class Reject(
        val error: LayoutPolicyError,
    ) : ContentPlacementDecision()

    companion object {
        @JvmStatic
        fun root(): PlaceIn = PlaceIn(ContentSlotId.Root)

        @JvmStatic
        fun childOf(ownerContentEntryId: EntryId): PlaceIn =
            PlaceIn(ContentSlotId.ChildOf(ownerContentEntryId))

        @JvmStatic
        fun inSlot(slotId: ContentSlotId): PlaceIn = PlaceIn(slotId)

        @JvmStatic
        fun reject(code: String, message: String): Reject =
            Reject(LayoutPolicyError(code, message))
    }
}

/** Contextual reason why a valid navigation state could not be projected. */
sealed class NavProjectionProblem {
    data class PolicyRejected(
        val index: Int,
        val entry: BackStackEntry,
        val error: LayoutPolicyError,
    ) : NavProjectionProblem()

    data class InvalidSlotOwner(
        val index: Int,
        val entry: BackStackEntry,
        val ownerContentEntryId: EntryId,
    ) : NavProjectionProblem()

    data class PolicyFailed(
        val index: Int,
        val entry: BackStackEntry,
        val error: LayoutPolicyError,
    ) : NavProjectionProblem()
}

/** Atomic success or failure from [NavProjector.project]. */
sealed class NavProjectionResult {
    data class Success(
        val tree: NavigationRenderTree,
    ) : NavProjectionResult()

    data class Failure(
        val problem: NavProjectionProblem,
    ) : NavProjectionResult()
}

/** Pure projection from the validated logical history into a visible render tree. */
object NavProjector {

    @JvmStatic
    fun project(
        navState: NavState,
        policy: NavigationLayoutPolicy,
    ): NavProjectionResult {
        val contentPath = mutableListOf(
            ContentSlot(
                slotId = ContentSlotId.Root,
                entry = navState.root,
            ),
        )
        val modalLayers = mutableListOf<ModalLayer>()

        navState.entries.forEachIndexed { index, entry ->
            if (index == 0) return@forEachIndexed

            when (entry.route) {
                is ContentRoute -> {
                    val request = ContentPlacementRequest.create(
                        contentPath = contentPath,
                        currentContent = contentPath.last(),
                        nextContent = entry,
                    )
                    val decision: ContentPlacementDecision? = try {
                        policy.placeContent(request)
                    } catch (exception: Exception) {
                        return NavProjectionResult.Failure(
                            NavProjectionProblem.PolicyFailed(
                                index = index,
                                entry = entry,
                                error = LayoutPolicyError(
                                    code = POLICY_EXCEPTION_CODE,
                                    message = exception.asPolicyFailureMessage(),
                                ),
                            ),
                        )
                    }

                    when (decision) {
                        null -> return NavProjectionResult.Failure(
                            NavProjectionProblem.PolicyFailed(
                                index = index,
                                entry = entry,
                                error = LayoutPolicyError(
                                    code = NULL_DECISION_CODE,
                                    message = "Navigation layout policy returned null",
                                ),
                            ),
                        )

                        is ContentPlacementDecision.Reject -> {
                            return NavProjectionResult.Failure(
                                NavProjectionProblem.PolicyRejected(
                                    index = index,
                                    entry = entry,
                                    error = decision.error,
                                ),
                            )
                        }

                        is ContentPlacementDecision.PlaceIn -> {
                            val placementFailure = placeContent(
                                contentPath = contentPath,
                                index = index,
                                entry = entry,
                                slotId = decision.slotId,
                            )
                            if (placementFailure != null) return placementFailure

                            // A content entry later in history hides every preceding modal chain.
                            modalLayers.clear()
                        }
                    }
                }

                is ModalRoute -> {
                    modalLayers += ModalLayer(
                        entry = entry,
                        ownerContentEntryId = contentPath.last().entry.id,
                    )
                }
            }
        }

        return NavProjectionResult.Success(
            NavigationRenderTree.create(
                root = contentPath.first(),
                nestedSlots = contentPath.drop(1),
                modalLayers = modalLayers,
            ),
        )
    }

    private fun placeContent(
        contentPath: MutableList<ContentSlot>,
        index: Int,
        entry: BackStackEntry,
        slotId: ContentSlotId,
    ): NavProjectionResult.Failure? = when (slotId) {
        ContentSlotId.Root -> {
            contentPath.clear()
            contentPath += ContentSlot(ContentSlotId.Root, entry)
            null
        }

        is ContentSlotId.ChildOf -> {
            val ownerIndex = contentPath.indexOfFirst {
                it.entry.id == slotId.ownerContentEntryId
            }
            if (ownerIndex == -1) {
                NavProjectionResult.Failure(
                    NavProjectionProblem.InvalidSlotOwner(
                        index = index,
                        entry = entry,
                        ownerContentEntryId = slotId.ownerContentEntryId,
                    ),
                )
            } else {
                while (contentPath.lastIndex > ownerIndex) {
                    contentPath.removeAt(contentPath.lastIndex)
                }
                contentPath += ContentSlot(slotId, entry)
                null
            }
        }
    }
}

private const val POLICY_EXCEPTION_CODE = "policy_exception"
private const val NULL_DECISION_CODE = "null_decision"

private fun Exception.asPolicyFailureMessage(): String = buildString {
    append(this@asPolicyFailureMessage.javaClass.name)
    this@asPolicyFailureMessage.message?.let { message ->
        append(": ")
        append(message)
    }
}

private fun <T> projectionImmutableListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))
