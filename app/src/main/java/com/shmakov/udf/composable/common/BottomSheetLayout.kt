package com.shmakov.udf.composable.common

import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.shmakov.udf.navigation.ModalScreenState
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetLayout(
    targetState: ModalScreenState,
    onDismissRequest: () -> Unit,
    onExitFinished: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val phase = remember(targetState) {
        when (targetState) {
            ModalScreenState.Shown -> BottomSheetPhase.Desired(onDismissRequest)
            ModalScreenState.Hidden -> BottomSheetPhase.Exiting(onExitFinished)
        }
    }
    val currentPhase by rememberUpdatedState(phase)

    // BottomSheetScaffold is a standard (collapsed/expanded) sheet. Pairing it with a modal
    // Hidden/Expanded SheetState breaks its anchor-change and accessibility semantics.
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        confirmValueChange = { true },
        skipHiddenState = true,
    )

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState,
    )
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var sheetContentSize by remember { mutableStateOf(IntSize.Zero) }

    val requestDismiss: () -> Unit = remember(phase) {
        {
            if (phase is BottomSheetPhase.Desired) {
                phase.requestDismiss(currentPhase)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it },
    ) {
        BottomSheetScaffold(
            sheetContent = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { sheetContentSize = it },
                ) {
                    content()
                }
            },
            scaffoldState = scaffoldState,
            sheetPeekHeight = 0.dp,
            sheetSwipeEnabled = phase is BottomSheetPhase.Desired,
            containerColor = Color.Unspecified,
        ) {
            Scrim(
                color = BottomSheetDefaults.ScrimColor,
                onDismissRequest = requestDismiss,
                visible = bottomSheetState.currentValue != SheetValue.PartiallyExpanded ||
                    bottomSheetState.targetValue != SheetValue.PartiallyExpanded,
            )
        }

        LaunchedEffect(phase, bottomSheetState, containerSize, sheetContentSize) {
            suspend fun converge(target: BottomSheetMotionTarget) {
                convergeBottomSheet(
                    target = target,
                    snapshot = {
                        BottomSheetMotionSnapshot(
                            current = bottomSheetState.currentValue.toMotionTarget(),
                            target = bottomSheetState.targetValue.toMotionTarget(),
                        )
                    },
                    move = { requestedTarget ->
                        when (requestedTarget) {
                            BottomSheetMotionTarget.Expanded -> bottomSheetState.expand()
                            BottomSheetMotionTarget.Collapsed -> bottomSheetState.partialExpand()
                        }
                    },
                    awaitRetry = { withFrameNanos { } },
                )
            }

            when (val effectPhase = phase) {
                is BottomSheetPhase.Desired -> {
                    // The pinned Material version can retain an old Expanded offset after sheet
                    // geometry changes while keeping the semantic value Expanded. Moving through
                    // the collapsed anchor makes the next expansion resolve the new exact offset;
                    // gesture observation is deliberately not armed during this recovery.
                    converge(BottomSheetMotionTarget.Collapsed)

                    while (currentPhase === effectPhase) {
                        // A zero-height sheet or a resize can temporarily remove Expanded. Such
                        // anchor churn is recovery work, never a user dismiss request.
                        // Semantic anchor/value observation cannot reveal a stale physical offset
                        // when geometry changes. Geometry keys restart recovery, while frame
                        // polling bridges the interval between measurement and anchor installation.
                        while (!bottomSheetState.hasExpandedState) {
                            withFrameNanos { }
                        }
                        converge(BottomSheetMotionTarget.Expanded)
                        if (currentPhase !== effectPhase) return@LaunchedEffect

                        val observation = snapshotFlow {
                            BottomSheetObservation(
                                hasExpandedAnchor = bottomSheetState.hasExpandedState,
                                target = bottomSheetState.targetValue.toMotionTarget(),
                            )
                        }.first { observation ->
                            !observation.hasExpandedAnchor ||
                                observation.target == BottomSheetMotionTarget.Collapsed
                        }

                        if (!observation.hasExpandedAnchor) {
                            continue
                        }

                        effectPhase.requestDismiss(currentPhase)

                        // Give a synchronous durable acknowledgement one apply turn before an
                        // unacknowledged gesture is corrected back to Expanded.
                        withFrameNanos { }
                    }
                }

                is BottomSheetPhase.Exiting -> {
                    converge(BottomSheetMotionTarget.Collapsed)
                    effectPhase.completeExit(currentPhase)
                }
            }
        }
    }
}

private data class BottomSheetObservation(
    val hasExpandedAnchor: Boolean,
    val target: BottomSheetMotionTarget,
)

@OptIn(ExperimentalMaterial3Api::class)
private fun SheetValue.toMotionTarget(): BottomSheetMotionTarget = when (this) {
    SheetValue.Expanded -> BottomSheetMotionTarget.Expanded
    SheetValue.PartiallyExpanded,
    SheetValue.Hidden,
    -> BottomSheetMotionTarget.Collapsed
}

@Composable
private fun Scrim(
    color: Color,
    onDismissRequest: () -> Unit,
    visible: Boolean,
) {
    if (color.isSpecified) {
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = TweenSpec()
        )

        val dismissSheet = if (visible) {
            Modifier
                .pointerInput(onDismissRequest) {
                    detectTapGestures {
                        onDismissRequest()
                    }
                }
                .clearAndSetSemantics {}
        } else {
            Modifier
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .then(dismissSheet)
        ) {
            drawRect(color = color, alpha = alpha)
        }
    }
}
