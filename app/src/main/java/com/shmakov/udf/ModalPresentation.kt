package com.shmakov.udf

import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.ModalLayer
import java.util.Collections

/** Identifies one process-local exit cycle for one exact modal occurrence. */
internal data class ModalExitToken(
    val entryId: EntryId,
    val generation: Long,
)

/** One modal layer that is either still desired or retained only for its exit animation. */
internal sealed class PresentedModalLayer {
    abstract val layer: ModalLayer

    data class Desired(
        override val layer: ModalLayer,
    ) : PresentedModalLayer()

    data class Exiting(
        override val layer: ModalLayer,
        val token: ModalExitToken,
    ) : PresentedModalLayer()
}

/** Immutable renderer-local modal presentation state. */
internal class ModalPresentationState private constructor(
    val acceptedNavigationRevision: Long,
    layers: List<PresentedModalLayer>,
    internal val lastIssuedExitGeneration: Long,
) {
    val layers: List<PresentedModalLayer> = modalImmutableListCopy(layers)

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is ModalPresentationState &&
            acceptedNavigationRevision == other.acceptedNavigationRevision &&
            layers == other.layers &&
            lastIssuedExitGeneration == other.lastIssuedExitGeneration

    override fun hashCode(): Int {
        var result = acceptedNavigationRevision.hashCode()
        result = 31 * result + layers.hashCode()
        result = 31 * result + lastIssuedExitGeneration.hashCode()
        return result
    }

    override fun toString(): String =
        "ModalPresentationState(acceptedNavigationRevision=$acceptedNavigationRevision, " +
            "layers=$layers, lastIssuedExitGeneration=$lastIssuedExitGeneration)"

    companion object {
        internal fun create(
            acceptedNavigationRevision: Long,
            layers: List<PresentedModalLayer>,
            lastIssuedExitGeneration: Long,
        ): ModalPresentationState = ModalPresentationState(
            acceptedNavigationRevision = acceptedNavigationRevision,
            layers = layers,
            lastIssuedExitGeneration = lastIssuedExitGeneration,
        )
    }
}

/** Safe fallback reason from an otherwise valid modal presentation update. */
internal sealed class ModalPresentationProblem {
    class ReorderedEntryIds(
        previousDesiredOrder: List<EntryId>,
        targetDesiredOrder: List<EntryId>,
    ) : ModalPresentationProblem() {
        val previousDesiredOrder: List<EntryId> =
            modalImmutableListCopy(previousDesiredOrder)
        val targetDesiredOrder: List<EntryId> = modalImmutableListCopy(targetDesiredOrder)

        override fun equals(other: Any?): Boolean =
            this === other ||
                other is ReorderedEntryIds &&
                previousDesiredOrder == other.previousDesiredOrder &&
                targetDesiredOrder == other.targetDesiredOrder

        override fun hashCode(): Int {
            var result = previousDesiredOrder.hashCode()
            result = 31 * result + targetDesiredOrder.hashCode()
            return result
        }

        override fun toString(): String =
            "ReorderedEntryIds(previousDesiredOrder=$previousDesiredOrder, " +
                "targetDesiredOrder=$targetDesiredOrder)"
    }
}

/** Result of reconciling one accepted presentation state with a projected modal target. */
internal sealed class ModalPresentationPlan {
    abstract val state: ModalPresentationState

    data class Ready(
        override val state: ModalPresentationState,
    ) : ModalPresentationPlan()

    data class Fallback(
        override val state: ModalPresentationState,
        val problem: ModalPresentationProblem,
    ) : ModalPresentationPlan()
}

/** Exact result of releasing one retained modal exit cycle. */
internal sealed class ModalExitCompletion {
    abstract val state: ModalPresentationState
    abstract val token: ModalExitToken

    data class Applied(
        override val state: ModalPresentationState,
        override val token: ModalExitToken,
    ) : ModalExitCompletion()

    data class Unchanged(
        override val state: ModalPresentationState,
        override val token: ModalExitToken,
    ) : ModalExitCompletion()
}

/** Pure state transitions for desired and renderer-retained modal layers. */
internal object ModalPresentationPlanner {

    fun start(
        navigationRevision: Long,
        desired: List<ModalLayer>,
    ): ModalPresentationState {
        desired.requireUniqueModalEntryIds()
        return ModalPresentationState.create(
            acceptedNavigationRevision = navigationRevision,
            layers = desired.map(PresentedModalLayer::Desired),
            lastIssuedExitGeneration = 0L,
        )
    }

    fun reconcile(
        previous: ModalPresentationState,
        navigationRevision: Long,
        desired: List<ModalLayer>,
    ): ModalPresentationPlan {
        desired.requireUniqueModalEntryIds()

        val previousDesiredOrder = previous.layers
            .filterIsInstance<PresentedModalLayer.Desired>()
            .map { presentation -> presentation.layer.entry.id }
        val targetDesiredOrder = desired.map { layer -> layer.entry.id }

        if (navigationRevision == previous.acceptedNavigationRevision) {
            return reconcileSameRevision(
                previous = previous,
                desired = desired,
                previousDesiredOrder = previousDesiredOrder,
                targetDesiredOrder = targetDesiredOrder,
            )
        }

        if (!navigationRevision.isImmediatelyAfter(previous.acceptedNavigationRevision)) {
            return ModalPresentationPlan.Ready(
                previous.snapTo(
                    navigationRevision = navigationRevision,
                    desired = desired,
                ),
            )
        }

        if (survivingOrderChanged(previousDesiredOrder, targetDesiredOrder)) {
            return ModalPresentationPlan.Fallback(
                state = previous.snapTo(
                    navigationRevision = navigationRevision,
                    desired = desired,
                ),
                problem = ModalPresentationProblem.ReorderedEntryIds(
                    previousDesiredOrder = previousDesiredOrder,
                    targetDesiredOrder = targetDesiredOrder,
                ),
            )
        }

        val desiredById = desired.associateBy { layer -> layer.entry.id }
        var lastIssuedGeneration = previous.lastIssuedExitGeneration
        val exitingById = LinkedHashMap<EntryId, PresentedModalLayer.Exiting>()

        previous.layers.forEach { presentation ->
            val entryId = presentation.layer.entry.id
            if (entryId !in desiredById) {
                val exiting = when (presentation) {
                    is PresentedModalLayer.Desired -> {
                        lastIssuedGeneration = lastIssuedGeneration.nextExitGeneration()
                        PresentedModalLayer.Exiting(
                            layer = presentation.layer,
                            token = ModalExitToken(
                                entryId = entryId,
                                generation = lastIssuedGeneration,
                            ),
                        )
                    }

                    is PresentedModalLayer.Exiting -> presentation
                }
                exitingById[entryId] = exiting
            }
        }

        val layers = mergeDesiredAndExiting(
            previousOrder = previous.layers.map { presentation -> presentation.layer.entry.id },
            desired = desired,
            exitingById = exitingById,
        )
        val state = ModalPresentationState.create(
            acceptedNavigationRevision = navigationRevision,
            layers = layers,
            lastIssuedExitGeneration = lastIssuedGeneration,
        )
        return ModalPresentationPlan.Ready(state)
    }

    fun completeExit(
        previous: ModalPresentationState,
        token: ModalExitToken,
    ): ModalExitCompletion {
        val exitingIndex = previous.layers.indexOfFirst { presentation ->
            presentation is PresentedModalLayer.Exiting && presentation.token == token
        }
        if (exitingIndex == -1) {
            return ModalExitCompletion.Unchanged(
                state = previous,
                token = token,
            )
        }

        val remainingLayers = ArrayList(previous.layers)
        remainingLayers.removeAt(exitingIndex)
        return ModalExitCompletion.Applied(
            state = ModalPresentationState.create(
                acceptedNavigationRevision = previous.acceptedNavigationRevision,
                layers = remainingLayers,
                lastIssuedExitGeneration = previous.lastIssuedExitGeneration,
            ),
            token = token,
        )
    }

    private fun reconcileSameRevision(
        previous: ModalPresentationState,
        desired: List<ModalLayer>,
        previousDesiredOrder: List<EntryId>,
        targetDesiredOrder: List<EntryId>,
    ): ModalPresentationPlan {
        if (previousDesiredOrder != targetDesiredOrder) {
            return ModalPresentationPlan.Ready(
                previous.snapTo(
                    navigationRevision = previous.acceptedNavigationRevision,
                    desired = desired,
                ),
            )
        }

        val desiredById = desired.associateBy { layer -> layer.entry.id }
        val refreshedLayers = previous.layers.map { presentation ->
            when (presentation) {
                is PresentedModalLayer.Desired -> PresentedModalLayer.Desired(
                    desiredById.getValue(presentation.layer.entry.id),
                )

                is PresentedModalLayer.Exiting -> presentation
            }
        }
        if (refreshedLayers == previous.layers) {
            return ModalPresentationPlan.Ready(previous)
        }

        return ModalPresentationPlan.Ready(
            ModalPresentationState.create(
                acceptedNavigationRevision = previous.acceptedNavigationRevision,
                layers = refreshedLayers,
                lastIssuedExitGeneration = previous.lastIssuedExitGeneration,
            ),
        )
    }

    private fun ModalPresentationState.snapTo(
        navigationRevision: Long,
        desired: List<ModalLayer>,
    ): ModalPresentationState = ModalPresentationState.create(
        acceptedNavigationRevision = navigationRevision,
        layers = desired.map(PresentedModalLayer::Desired),
        lastIssuedExitGeneration = lastIssuedExitGeneration,
    )

    private fun Long.isImmediatelyAfter(previous: Long): Boolean =
        previous != Long.MAX_VALUE && this == previous + 1L
}

private fun survivingOrderChanged(
    previousDesiredOrder: List<EntryId>,
    targetDesiredOrder: List<EntryId>,
): Boolean {
    val targetIds = targetDesiredOrder.toSet()
    val previousIds = previousDesiredOrder.toSet()
    val previousSurvivors = previousDesiredOrder.filter { entryId -> entryId in targetIds }
    val targetSurvivors = targetDesiredOrder.filter { entryId -> entryId in previousIds }
    return previousSurvivors != targetSurvivors
}

private fun mergeDesiredAndExiting(
    previousOrder: List<EntryId>,
    desired: List<ModalLayer>,
    exitingById: Map<EntryId, PresentedModalLayer.Exiting>,
): List<PresentedModalLayer> {
    if (exitingById.isEmpty()) {
        return desired.map(PresentedModalLayer::Desired)
    }

    val desiredIds = desired.mapTo(LinkedHashSet()) { layer -> layer.entry.id }
    val exitsBeforeAnchor = LinkedHashMap<EntryId, MutableList<PresentedModalLayer.Exiting>>()
    val trailingExits = mutableListOf<PresentedModalLayer.Exiting>()

    previousOrder.forEachIndexed { index, entryId ->
        val exiting = exitingById[entryId] ?: return@forEachIndexed
        val nextDesiredAnchor = previousOrder
            .subList(index + 1, previousOrder.size)
            .firstOrNull { candidate -> candidate in desiredIds }
        if (nextDesiredAnchor == null) {
            trailingExits += exiting
        } else {
            exitsBeforeAnchor.getOrPut(nextDesiredAnchor) { mutableListOf() } += exiting
        }
    }

    return buildList {
        desired.forEach { layer ->
            val entryId = layer.entry.id
            addAll(exitsBeforeAnchor[entryId].orEmpty())
            add(PresentedModalLayer.Desired(layer))
        }
        addAll(trailingExits)
    }
}

private fun List<ModalLayer>.requireUniqueModalEntryIds() {
    require(size == map { layer -> layer.entry.id }.toSet().size) {
        "Desired modal layers must have unique entry IDs"
    }
}

private fun Long.nextExitGeneration(): Long {
    check(this < Long.MAX_VALUE) { "Modal exit generation space exhausted" }
    return this + 1L
}

private fun <T> modalImmutableListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))
