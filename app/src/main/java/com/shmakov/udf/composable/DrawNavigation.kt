package com.shmakov.udf.composable

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.shmakov.udf.Destination
import com.shmakov.udf.composable.screen.AccountsScreen
import com.shmakov.udf.composable.screen.CardsScreen
import com.shmakov.udf.composable.screen.HomeScreen
import com.shmakov.udf.composable.screen.TransactionsScreen
import com.shmakov.udf.navigation.Screen
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedNavGraph(destination: Destination, into: Long?) {
    val lastPath = remember {
        AtomicLong(destination.id)
    }

    var isPop = lastPath.get() != destination.id

    var screen = getScreen(destination)

    var targetDestination = destination

    var nextDestination = destination
    while (nextDestination.childDestination != null) {
        nextDestination = nextDestination.childDestination!!

        if (nextDestination.id == lastPath.get()) {
            isPop = false
        }

        if (screen.whereToShowChild() == into) {
            screen = getScreen(nextDestination)
            targetDestination = nextDestination
        }
    }

    lastPath.set(screen.destination.id)

    val finalEnter: AnimatedContentScope<Destination>.() -> EnterTransition = {
        if (isPop) {
            appPopEnterTransition
        } else {
            appEnterTransition
        }
    }

    val finalExit: AnimatedContentScope<Destination>.() -> ExitTransition = {
        if (isPop) {
            appPopExitTransition
        } else {
            appExitTransition
        }
    }

    val transition = updateTransition(targetState = targetDestination, label = "AnimatedContent")

    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            ContentTransform(
                finalEnter(),
                finalExit(),
            )
        },
        contentKey = { it.id }
    ) {
        getScreen(it).content()
    }

}

private fun getScreen(destination: Destination): Screen {
    val result = when (destination.name) {
        "Home" -> HomeScreen(destination)
        "Accounts" -> AccountsScreen(destination)
        "Transactions" -> TransactionsScreen(destination)
        "Cards" -> CardsScreen(destination)
        else -> null
    }

    return result!!
}

@OptIn(ExperimentalAnimationApi::class)
private val AnimatedContentScope<*>.appEnterTransition: EnterTransition
    get() {
        return slideIntoContainer(
            AnimatedContentScope.SlideDirection.Left,
            animationSpec = tween()
        )
    }

@OptIn(ExperimentalAnimationApi::class)
private val AnimatedContentScope<*>.appExitTransition: ExitTransition
    get() {
        return slideOutOfContainer(
            AnimatedContentScope.SlideDirection.Left,
            animationSpec = tween()
        )
    }

@OptIn(ExperimentalAnimationApi::class)
private val AnimatedContentScope<*>.appPopEnterTransition: EnterTransition
    get() {
        return slideIntoContainer(
            AnimatedContentScope.SlideDirection.Right,
            animationSpec = tween()
        )
    }

@OptIn(ExperimentalAnimationApi::class)
private val AnimatedContentScope<*>.appPopExitTransition: ExitTransition
    get() {
        return slideOutOfContainer(
            AnimatedContentScope.SlideDirection.Right,
            animationSpec = tween()
        )
    }
