package com.shmakov.udf.composable.common

import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.shmakov.udf.navigation.ModalScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetLayout(
    targetState: ModalScreenState,
    onDismissRequest: () -> Unit,
    onExitFinished: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentTargetState by rememberUpdatedState(targetState)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentOnExitFinished by rememberUpdatedState(onExitFinished)
    // Every accepted Shown -> Hidden lifecycle gets its own exactly-once completion guard.
    var exitCompletionReported by remember(targetState) { mutableStateOf(false) }

    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { nextValue ->
            if (
                nextValue == SheetValue.Hidden &&
                currentTargetState == ModalScreenState.Shown
            ) {
                currentOnDismissRequest()
                false
            } else {
                true
            }
        },
    )

    val requestDismiss: () -> Unit = {
        if (currentTargetState == ModalScreenState.Shown) {
            currentOnDismissRequest()
        }
    }
    val finishExitOnce: () -> Unit = {
        if (!exitCompletionReported) {
            exitCompletionReported = true
            currentOnExitFinished()
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState,
    )

    // A durable Hidden target can arrive while the sheet is already invisible (for example during
    // a rapid Shown -> Hidden cycle). Complete that exit after composition instead of leaving an
    // invisible modal layer mounted over the destination below it.
    if (targetState == ModalScreenState.Hidden && !bottomSheetState.isVisible) {
        LaunchedEffect(targetState, bottomSheetState) {
            finishExitOnce()
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            sheetContent = content,
            scaffoldState = scaffoldState,
            containerColor = Color.Unspecified,
        ) {
            Scrim(
                color = BottomSheetDefaults.ScrimColor,
                onDismissRequest = requestDismiss,
                visible = bottomSheetState.targetValue != SheetValue.Hidden,
            )
        }

        LaunchedEffect(targetState, bottomSheetState) {
            when (targetState) {
                ModalScreenState.Shown -> {
                    bottomSheetState.show()
                }

                ModalScreenState.Hidden -> {
                    bottomSheetState.hide()
                    finishExitOnce()
                }
            }
        }
    }
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
