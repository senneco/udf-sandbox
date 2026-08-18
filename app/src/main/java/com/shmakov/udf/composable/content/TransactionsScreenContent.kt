package com.shmakov.udf.composable.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun TransactionsScreenContent() {
    Text(
        text = "Transactions Screen",
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red)
    )
}

@Composable
fun TransactionScreenContent(transactionId: Int) {
    Text(
        text = "Transaction Screen #$transactionId",
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red),
    )
}
