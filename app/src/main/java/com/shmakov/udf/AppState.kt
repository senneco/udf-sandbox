package com.shmakov.udf

import com.shmakov.udf.navigation.NavState

data class AppState(
    val navState: NavState,
    val showInPlace: Boolean,
)
