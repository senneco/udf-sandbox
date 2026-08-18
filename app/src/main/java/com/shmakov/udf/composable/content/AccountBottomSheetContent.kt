package com.shmakov.udf.composable.content

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier

@Composable
fun ColumnScope.AccountBottomSheetContent(
    accountId: Int,
    onNextAccount: (Int) -> Unit,
    onDetailsRequested: () -> Unit,
) {
    if (accountId < 9) {
        Button(
            onClick = { onNextAccount(accountId + 1) },
        ) {
            Text(text = "Go to Account #${accountId + 1}")
        }
    }

    Button(
        onClick = onDetailsRequested,
        modifier = Modifier
            .align(CenterHorizontally)
    ) {
        Text(text = "Go to details")
    }
}
