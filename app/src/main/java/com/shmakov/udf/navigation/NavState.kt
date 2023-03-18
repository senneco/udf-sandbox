package com.shmakov.udf.navigation

data class NavState(
    val backStack: List<Destination>,
    val lastNavActionType: NavActionType,
)