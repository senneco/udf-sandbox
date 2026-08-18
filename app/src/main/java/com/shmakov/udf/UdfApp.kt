package com.shmakov.udf

import android.app.Application
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

}
