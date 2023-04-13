package com.shmakov.udf.navigation

private var nextId = 0L

sealed class Destination {

    // TODO: need to optimize this, becuase it isn't clear - when to override or not this this field
    open val destinationId = nextId++

    open class Content : Destination()

    open class Modal : Destination()
}

object AppRoot : Destination.Content()

object Home : Destination.Content()

object Accounts : Destination.Content()

data class Account(
    override val destinationId: Long = nextId++,
    val accountId: Int
) : Destination.Modal()

data class AccountDetails(
    override val destinationId: Long = nextId++,
    val accountId: Int,
) : Destination.Content()

object Transactions : Destination.Content()

data class Transaction(
    override val destinationId: Long = nextId++,
    val transactionId: Int,
) : Destination.Content()

object Cards : Destination.Content()

data class Card(
    override val destinationId: Long = nextId++,
    val cardId: Int,
) : Destination.Content()