package com.shmakov.udf

import com.shmakov.udf.navigation.NavActionType
import com.shmakov.udf.navigation.NavState

data class AppState(
    val navState: NavState,
    val lastNavActionType: NavActionType,
    val showInPlace: Boolean,
)
