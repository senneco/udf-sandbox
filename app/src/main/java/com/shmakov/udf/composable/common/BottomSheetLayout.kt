package com.shmakov.udf.composable.common

import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.shmakov.udf.navigation.ModalScreenState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetLayout(
    targetState: ModalScreenState,
    onHide: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()

    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    val animateToDismiss: () -> Unit = {
        scope.launch { bottomSheetState.hide() }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState,
    )

    //Little, but useful optimization
    if (targetState == ModalScreenState.Hidden && !bottomSheetState.isVisible) {
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeight = constraints.maxHeight

        BottomSheetScaffold(
            sheetContent = content,
            scaffoldState = scaffoldState,
            containerColor = Color.Unspecified,
        ) {
            Scrim(
                color = BottomSheetDefaults.ScrimColor,
                onDismissRequest = animateToDismiss,
                visible = bottomSheetState.targetValue != SheetValue.Hidden,
            )
        }


        if (bottomSheetState.currentValue != SheetValue.Hidden && targetState == ModalScreenState.Hidden) {
            LaunchedEffect(Unit) {
                scope.launch { bottomSheetState.hide() }
            }
        }

        LaunchedEffect(Unit) {
            scope.launch {
                bottomSheetState.show()
            }

            // We can't watch only on hidden state, because if we hide bottom sheet while it's in
            // showing animation, then current state is already Hidden and we can't determine, when
            // hide animation will be completed
            snapshotFlow { bottomSheetState.requireOffset() }
                .drop(1)
                .collect { offset ->
                    if (offset == fullHeight.toFloat()) {
                        onHide()
                    }
                }
        }
    }
}

@Composable
private fun Scrim(
    color: Color,
    onDismissRequest: () -> Unit,
    visible: Boolean
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