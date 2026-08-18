package com.shmakov.udf.composable.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shmakov.udf.navigation.NavProjectionProblem

@Composable
internal fun NavigationProjectionFailure(problem: NavProjectionProblem) {
    Text(
        text = "Navigation projection failed: $problem",
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal fun DestinationBindingFailure(problem: DestinationTreeBindingProblem) {
    Text(
        text = "Destination binding failed: $problem",
        modifier = Modifier.fillMaxSize(),
    )
}
