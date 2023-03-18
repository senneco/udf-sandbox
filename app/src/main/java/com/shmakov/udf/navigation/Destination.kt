package com.shmakov.udf.navigation

import java.util.concurrent.atomic.AtomicLong

abstract class Destination {
    val destinationId: Long = generateDestinationId()
}

private val lastIndex = AtomicLong(0)

fun generateDestinationId(): Long = lastIndex.getAndIncrement()

object Home : Destination()

object Accounts : Destination()

data class Account(val id: Long) : Destination()

object Transactions : Destination()
data class Transaction(val id: Long) : Destination()

object Cards : Destination()
data class Card(val id: Long) : Destination()