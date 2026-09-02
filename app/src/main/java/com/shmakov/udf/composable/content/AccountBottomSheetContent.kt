package com.shmakov.udf.composable.content

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun ColumnScope.AccountBottomSheetContent(
    accountId: Int,
    onNextAccount: (Int) -> Unit,
    onDetailsRequested: () -> Unit,
) {
    var localValue by rememberSaveable { mutableStateOf(0) }

    Button(
        onClick = { localValue += 1 },
        modifier = Modifier.testTag(accountLocalStateTag(accountId)),
    ) {
        Text(text = "Account #$accountId local value: $localValue")
    }

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

internal fun accountLocalStateTag(accountId: Int): String =
    "account-local-state:$accountId"
