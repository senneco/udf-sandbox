package com.shmakov.udf

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shmakov.udf.navigation.*
import timber.log.Timber
import timber.log.Timber.DebugTree

private data class AppFrame(
    val state: AppState,
    val navTransition: NavTransitionIntent?,
)

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
        private var frame by mutableStateOf(
            NavState.history(
                root = Home,
                Accounts,
                Account(accountId = 1),
            ).let { initialNavState ->
                AppFrame(
                    state = AppState(
                        navState = initialNavState,
                        showInPlace = false,
                    ),
                    navTransition = null,
                )
            },
        )

        val appState: AppState
            get() = frame.state

        val navTransition: NavTransitionIntent?
            get() = frame.navTransition

        @JvmStatic
        fun dispatchNavigation(action: NavAction): NavReduction {
            val reduction = NavReducer.reduce(frame.state.navState, action)
            if (reduction is NavReduction.Changed) {
                frame = AppFrame(
                    state = frame.state.copy(navState = reduction.state),
                    navTransition = reduction.transition,
                )
            }

            return reduction
        }
    }
}
