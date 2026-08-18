package com.shmakov.udf.navigation

/**
 * A semantic navigation target. Entry identity is kept separately in [BackStackEntry].
 *
 * Application routes must implement exactly one of [ContentRoute] or [ModalRoute]. Direct
 * implementations and routes implementing both kinds are rejected by [NavState.fromEntries].
 */
interface Route

/** A route rendered as content in a navigation slot. */
interface ContentRoute : Route

/** A route presented as a modal layer. */
interface ModalRoute : Route

// Demo routes. Applications can declare their own ContentRoute and ModalRoute implementations.
object Home : ContentRoute

object Accounts : ContentRoute

data class Account(
    val accountId: Int,
) : ModalRoute

data class AccountDetails(
    val accountId: Int,
) : ContentRoute

object Transactions : ContentRoute

data class Transaction(
    val transactionId: Int,
) : ContentRoute

object Cards : ContentRoute

data class Card(
    val cardId: Int,
) : ContentRoute
