package com.shmakov.udf

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import timber.log.Timber
import timber.log.Timber.DebugTree

class UdfApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(DebugTree())
    }

    companion object {
        var appState by mutableStateOf(
            AppState(
                destination = Destination(
                    id = 0,
                    name = "Home",
                    childDestination = null,
                ),
                showInPlace = false,
            )
        )
    }
}