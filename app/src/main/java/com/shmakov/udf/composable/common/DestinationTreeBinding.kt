package com.shmakov.udf.composable.common

import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.ContentSlot
import com.shmakov.udf.navigation.ModalLayer
import com.shmakov.udf.navigation.ModalScreen
import com.shmakov.udf.navigation.NavigationRenderTree
import com.shmakov.udf.navigation.Screen
import java.util.Collections

/** Resolves one semantic entry to its demo rendering boundary. */
internal fun interface DestinationCatalog {
    fun resolve(entry: BackStackEntry): DestinationBinding
}

/** Catalog result for one entry. Unsupported routes stay values rather than crashes. */
internal sealed class DestinationBinding {
    data class Content(
        val screen: Screen,
    ) : DestinationBinding()

    data class Modal(
        val screen: ModalScreen,
    ) : DestinationBinding()

    data class Unsupported(
        val entry: BackStackEntry,
    ) : DestinationBinding()
}

internal enum class DestinationKind {
    Content,
    Modal,
}

internal data class DestinationCatalogError(
    val code: String,
    val message: String,
)

/** Contextual reason why an entire projection could not be bound before composition. */
internal sealed class DestinationTreeBindingProblem {
    data class Unsupported(
        val entry: BackStackEntry,
    ) : DestinationTreeBindingProblem()

    data class KindMismatch(
        val entry: BackStackEntry,
        val expectedKind: DestinationKind,
        val actualKind: DestinationKind,
    ) : DestinationTreeBindingProblem()

    data class CatalogFailed(
        val entry: BackStackEntry,
        val expectedKind: DestinationKind,
        val error: DestinationCatalogError,
    ) : DestinationTreeBindingProblem()
}

internal data class BoundContentSlot(
    val slot: ContentSlot,
    val screen: Screen,
)

internal data class BoundModalLayer(
    val layer: ModalLayer,
    val screen: ModalScreen,
)

/** Immutable projection whose every destination was resolved before a composable is called. */
internal class BoundNavigationRenderTree private constructor(
    val root: BoundContentSlot,
    nestedSlots: List<BoundContentSlot>,
    modalLayers: List<BoundModalLayer>,
) {
    val nestedSlots: List<BoundContentSlot> = bindingImmutableListCopy(nestedSlots)
    val modalLayers: List<BoundModalLayer> = bindingImmutableListCopy(modalLayers)

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is BoundNavigationRenderTree &&
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
        "BoundNavigationRenderTree(root=$root, nestedSlots=$nestedSlots, " +
            "modalLayers=$modalLayers)"

    companion object {
        fun create(
            root: BoundContentSlot,
            nestedSlots: List<BoundContentSlot>,
            modalLayers: List<BoundModalLayer>,
        ): BoundNavigationRenderTree = BoundNavigationRenderTree(root, nestedSlots, modalLayers)
    }
}

internal sealed class DestinationTreeBindingResult {
    data class Success(
        val tree: BoundNavigationRenderTree,
    ) : DestinationTreeBindingResult()

    data class Failure(
        val problem: DestinationTreeBindingProblem,
    ) : DestinationTreeBindingResult()
}

/** Atomically resolves a complete projected tree or returns one typed failure. */
internal object DestinationTreeBinder {

    fun bind(
        tree: NavigationRenderTree,
        catalog: DestinationCatalog,
    ): DestinationTreeBindingResult {
        val root = when (val result = catalog.resolveContent(tree.root.entry)) {
            is BindingOutcome.Resolved -> BoundContentSlot(tree.root, result.value)
            is BindingOutcome.Failed -> return DestinationTreeBindingResult.Failure(result.problem)
        }

        val nestedSlots = ArrayList<BoundContentSlot>(tree.nestedSlots.size)
        tree.nestedSlots.forEach { slot ->
            when (val result = catalog.resolveContent(slot.entry)) {
                is BindingOutcome.Resolved -> nestedSlots += BoundContentSlot(slot, result.value)
                is BindingOutcome.Failed -> {
                    return DestinationTreeBindingResult.Failure(result.problem)
                }
            }
        }

        val modalLayers = ArrayList<BoundModalLayer>(tree.modalLayers.size)
        tree.modalLayers.forEach { layer ->
            when (val result = catalog.resolveModal(layer.entry)) {
                is BindingOutcome.Resolved -> modalLayers += BoundModalLayer(layer, result.value)
                is BindingOutcome.Failed -> {
                    return DestinationTreeBindingResult.Failure(result.problem)
                }
            }
        }

        return DestinationTreeBindingResult.Success(
            BoundNavigationRenderTree.create(
                root = root,
                nestedSlots = nestedSlots,
                modalLayers = modalLayers,
            ),
        )
    }
}

private sealed class BindingOutcome<out T> {
    data class Resolved<T>(val value: T) : BindingOutcome<T>()

    data class Failed(
        val problem: DestinationTreeBindingProblem,
    ) : BindingOutcome<Nothing>()
}

private fun DestinationCatalog.resolveContent(
    entry: BackStackEntry,
): BindingOutcome<Screen> = when (val result = resolveSafely(entry, DestinationKind.Content)) {
    is SafeCatalogResult.Resolved -> when (val binding = result.binding) {
        is DestinationBinding.Content -> BindingOutcome.Resolved(binding.screen)
        is DestinationBinding.Modal -> BindingOutcome.Failed(
            DestinationTreeBindingProblem.KindMismatch(
                entry = entry,
                expectedKind = DestinationKind.Content,
                actualKind = DestinationKind.Modal,
            ),
        )
        is DestinationBinding.Unsupported -> BindingOutcome.Failed(
            DestinationTreeBindingProblem.Unsupported(binding.entry),
        )
    }
    is SafeCatalogResult.Failed -> BindingOutcome.Failed(result.problem)
}

private fun DestinationCatalog.resolveModal(
    entry: BackStackEntry,
): BindingOutcome<ModalScreen> = when (val result = resolveSafely(entry, DestinationKind.Modal)) {
    is SafeCatalogResult.Resolved -> when (val binding = result.binding) {
        is DestinationBinding.Content -> BindingOutcome.Failed(
            DestinationTreeBindingProblem.KindMismatch(
                entry = entry,
                expectedKind = DestinationKind.Modal,
                actualKind = DestinationKind.Content,
            ),
        )
        is DestinationBinding.Modal -> BindingOutcome.Resolved(binding.screen)
        is DestinationBinding.Unsupported -> BindingOutcome.Failed(
            DestinationTreeBindingProblem.Unsupported(binding.entry),
        )
    }
    is SafeCatalogResult.Failed -> BindingOutcome.Failed(result.problem)
}

private sealed class SafeCatalogResult {
    data class Resolved(
        val binding: DestinationBinding,
    ) : SafeCatalogResult()

    data class Failed(
        val problem: DestinationTreeBindingProblem.CatalogFailed,
    ) : SafeCatalogResult()
}

private fun DestinationCatalog.resolveSafely(
    entry: BackStackEntry,
    expectedKind: DestinationKind,
): SafeCatalogResult {
    val binding: DestinationBinding? = try {
        resolve(entry)
    } catch (exception: Exception) {
        return SafeCatalogResult.Failed(
            DestinationTreeBindingProblem.CatalogFailed(
                entry = entry,
                expectedKind = expectedKind,
                error = DestinationCatalogError(
                    code = CATALOG_EXCEPTION_CODE,
                    message = exception.asCatalogFailureMessage(),
                ),
            ),
        )
    }

    return if (binding == null) {
        SafeCatalogResult.Failed(
            DestinationTreeBindingProblem.CatalogFailed(
                entry = entry,
                expectedKind = expectedKind,
                error = DestinationCatalogError(
                    code = NULL_BINDING_CODE,
                    message = "Destination catalog returned null",
                ),
            ),
        )
    } else {
        SafeCatalogResult.Resolved(binding)
    }
}

private const val CATALOG_EXCEPTION_CODE = "catalog_exception"
private const val NULL_BINDING_CODE = "null_binding"

private fun Exception.asCatalogFailureMessage(): String = buildString {
    append(this@asCatalogFailureMessage.javaClass.name)
    this@asCatalogFailureMessage.message?.let { message ->
        append(": ")
        append(message)
    }
}

private fun <T> bindingImmutableListCopy(source: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(source))
