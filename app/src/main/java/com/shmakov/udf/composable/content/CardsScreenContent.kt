package com.shmakov.udf.composable.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

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
    var localValue by rememberSaveable { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Blue),
    ) {
        Text(text = "Card Screen #$cardId")
        Button(
            onClick = { localValue += 1 },
            modifier = Modifier.testTag(cardLocalStateTag(cardId)),
        ) {
            Text(text = "Card #$cardId local value: $localValue")
        }
    }
}

internal fun cardLocalStateTag(cardId: Int): String = "card-local-state:$cardId"
