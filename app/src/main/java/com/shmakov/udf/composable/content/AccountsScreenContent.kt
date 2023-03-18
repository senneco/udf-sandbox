package com.shmakov.udf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import timber.log.Timber

@Composable
fun AccountsScreenContent() {
    Timber.i("***-*** draw Accounts Screen")

    Text(
        text = "Accounts Screen",
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Green)
    )
}