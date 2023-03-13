package com.shmakov.udf


data class Destination(
    val id: Long,
    val name: String,
    val childDestination: Destination?
)