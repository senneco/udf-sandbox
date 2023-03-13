package com.shmakov.udf

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.composable.AnimatedNavGraph
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    destination: Destination,
) {
    Timber.i("***-*** draw Home Screen")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Yellow)
    ) {
        Text(text = "Home Screen")

        Button(onClick = {
            appState = appState.copy(
                destination = destination.copy(
                    childDestination = Destination(
                        id = 1,
                        name = "Accounts",
                        childDestination = null,
                    )
                )
            )
        }) {
            Text(text = "Go to Accounts")
        }

        Button(onClick = {
            appState = appState.copy(
                destination = destination.copy(
                    childDestination = Destination(
                        id = 2,
                        name = "Transactions",
                        childDestination = null,
                    )
                )
            )
        }) {
            Text(text = "Go to Transactions")
        }

        Button(onClick = {
            appState = appState.copy(
                destination = destination.copy(
                    childDestination = Destination(
                        id = 3,
                        name = "Cards",
                        childDestination = null,
                    )
                )
            )
        }) {
            Text(text = "Go to Cards")
        }

        Checkbox(checked = appState.showInPlace, onCheckedChange = {
            appState = appState.copy(showInPlace = it)
        })

        if (appState.showInPlace && destination.childDestination != null) {
            AnimatedNavGraph(destination = destination.childDestination, into = destination.id)
        }
    }
}