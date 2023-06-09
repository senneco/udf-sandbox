package com.shmakov.udf.composable.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import timber.log.Timber

@Composable
fun CardsScreenContent() {
    Text(
        text = "Cards Screen", modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Blue)
    )
}