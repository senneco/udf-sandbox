package com.shmakov.udf

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavActionType
import com.shmakov.udf.navigation.NavState
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
                navState = NavState(
                    listOf(Home),
                    lastNavActionType = NavActionType.Replace,
                ),
                showInPlace = false,
            )
        )
    }
}