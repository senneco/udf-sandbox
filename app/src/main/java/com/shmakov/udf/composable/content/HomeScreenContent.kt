package com.shmakov.udf.composable.content

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.ContentRoute
import com.shmakov.udf.navigation.Transactions

@Composable
fun HomeScreenContent(
    childContent: @Composable () -> Unit,
    onDestinationSelected: (ContentRoute) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Yellow)
    ) {
        Text(text = "Home Screen")

        val configuration = LocalConfiguration.current

        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            Row {
                Buttons(onDestinationSelected = onDestinationSelected)
            }
        } else {
            Column {
                Buttons(onDestinationSelected = onDestinationSelected)
            }
        }

        childContent()
    }
}

@Composable
fun Buttons(
    onDestinationSelected: (ContentRoute) -> Unit,
) {
    Button(onClick = { onDestinationSelected(Accounts) }) {
        Text(text = "Go to Accounts")
    }

    Button(
        onClick = { onDestinationSelected(Transactions) },
    ) {
        Text(text = "Go to Transactions")
    }

    Button(onClick = { onDestinationSelected(Cards) }) {
        Text(text = "Go to Cards")
    }
}
