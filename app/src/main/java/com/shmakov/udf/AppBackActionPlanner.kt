package com.shmakov.udf

import com.shmakov.udf.navigation.ModalRoute
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavState

/** Plans one host Back action against the exact navigation state visible to the host. */
internal object AppBackActionPlanner {

    fun action(navState: NavState): NavAction? = when {
        navState.entries.size == 1 -> null
        navState.top.route is ModalRoute -> NavAction.dismissModal(navState.top.id)
        else -> NavAction.Pop
    }
}
