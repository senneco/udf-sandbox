package com.shmakov.udf

import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.Card
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.TabId
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabState

/** Application-owned tab identities and the complete fresh demo graph. */
internal object DemoTabGraph {
    val accountsTabId = TabId("accounts")
    val cardsTabId = TabId("cards")
    val primaryTabId = accountsTabId

    /**
     * Creates fresh route occurrences for both independent, non-empty tab histories.
     *
     * This is the missing/rejected-payload fallback only. Logout or another full reset builds its
     * own application-specific target and passes it to one `ReplaceGraph` action.
     */
    fun initial(): TabNavigationState = TabNavigationState.create(
        selectedTabId = primaryTabId,
        tabs = listOf(
            TabState(
                id = accountsTabId,
                history = NavState.history(Accounts, Account(accountId = 1)),
            ),
            TabState(
                id = cardsTabId,
                history = NavState.history(Cards, Card(cardId = 1)),
            ),
        ),
    )
}
