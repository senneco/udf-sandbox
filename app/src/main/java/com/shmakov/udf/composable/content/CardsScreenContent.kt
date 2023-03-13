package com.shmakov.udf

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import timber.log.Timber

@Composable
fun CardsScreenContent(
    destination: Destination,
) {
    Timber.i("***-*** draw Cards Screen")

    Text(
        text = "Cards Screen", modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Blue)
    )
}