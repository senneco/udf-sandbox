package com.shmakov.udf.navigation

interface Destination {

    interface Content : Destination

    interface Dialog : Destination

    interface BottomSheet : Destination
}

object AppRoot: Destination.Content

object Home : Destination.Content

object Accounts : Destination.Content

data class Account(val id: Long) : Destination.Content

object Transactions : Destination.Content

data class Transaction(val id: Long) : Destination.Content

object Cards : Destination.Content

data class Card(val id: Long) : Destination.Content