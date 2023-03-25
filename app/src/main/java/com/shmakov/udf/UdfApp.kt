package com.shmakov.udf

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shmakov.udf.navigation.*
import timber.log.Timber
import timber.log.Timber.DebugTree

class UdfApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(object: DebugTree() {

            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                super.log(priority, "Timber:UDF", message, t)
            }
        })
    }

    companion object {
        var appState by mutableStateOf(
            AppState(
                navState = NavState(
                    listOf(Home, Accounts, Account(1)),
                    lastNavActionType = NavActionType.Replace,
                ),
                showInPlace = false,
            )
        )
    }
}