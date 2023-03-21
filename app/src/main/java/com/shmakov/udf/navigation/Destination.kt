package com.shmakov.udf.navigation

interface Destination {

    interface Content : Destination

    interface Modal : Destination
}

object AppRoot : Destination.Content

object Home : Destination.Content

object Accounts : Destination.Content

data class Account(val id: Int) : Destination.Modal

object Transactions : Destination.Content

data class Transaction(val id: Int) : Destination.Content

object Cards : Destination.Content

data class Card(val id: Int) : Destination.Content