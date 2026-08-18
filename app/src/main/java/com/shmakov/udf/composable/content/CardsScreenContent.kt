package com.shmakov.udf.composable.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun CardsScreenContent() {
    Text(
        text = "Cards Screen", modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Blue)
    )
}

@Composable
fun CardScreenContent(cardId: Int) {
    Text(
        text = "Card Screen #$cardId",
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Blue),
    )
}
