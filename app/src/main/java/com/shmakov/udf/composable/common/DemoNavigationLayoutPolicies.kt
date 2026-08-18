package com.shmakov.udf.composable.common

import com.shmakov.udf.navigation.ContentPlacementDecision
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.NavigationLayoutPolicy

internal object DemoNavigationLayoutPolicies {
    val singlePane = NavigationLayoutPolicy {
        ContentPlacementDecision.root()
    }

    val expandedPane = NavigationLayoutPolicy { request ->
        if (request.currentContent.entry.route is Home) {
            ContentPlacementDecision.childOf(request.currentContent.entry.id)
        } else {
            ContentPlacementDecision.root()
        }
    }
}
