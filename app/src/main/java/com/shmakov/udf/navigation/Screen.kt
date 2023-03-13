package com.shmakov.udf.navigation

import androidx.compose.runtime.Composable
import com.shmakov.udf.Destination

interface Screen {

    val destination: Destination

    @Composable
    fun whereToShowChild(): Long?

    @Composable
    fun content()
}