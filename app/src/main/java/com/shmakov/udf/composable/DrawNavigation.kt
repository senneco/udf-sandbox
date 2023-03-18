package com.shmakov.udf.composable

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.shmakov.udf.composable.screen.AccountsScreen
import com.shmakov.udf.composable.screen.CardsScreen
import com.shmakov.udf.composable.screen.HomeScreen
import com.shmakov.udf.composable.screen.TransactionsScreen
import com.shmakov.udf.navigation.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedNavigation(navState: NavState, into: Destination?) {
    val rootDestination = navState.backStack.firstOrNull() ?: return

    lateinit var lastScreen: Screen
    var lastContentIndex = 0

    val targetDestination = navState.backStack
        .foldIndexed<Destination, Destination?>(null) { index, lastShownDestination, nextDestination ->
            val result = if (lastShownDestination == null) {
                nextDestination
            } else if (lastScreen.whereToShowChild(nextDestination) == into) {
                lastContentIndex = index

                nextDestination
            } else {
                lastShownDestination
            }

            lastScreen = getScreen(nextDestination)

            result
        } ?: rootDestination

    val finalEnter: AnimatedContentScope<Destination>.() -> EnterTransition = {
        when (navState.lastNavActionType) {
            NavActionType.Push -> appPushEnterTransition
            NavActionType.Pop -> appPopEnterTransition
            NavActionType.Replace -> appReplaceEnterTransition
        }
    }

    val finalExit: AnimatedContentScope<Destination>.() -> ExitTransition = {
        when (navState.lastNavActionType) {
            NavActionType.Push -> appPushExitTransition
            NavActionType.Pop -> appPopExitTransition
            NavActionType.Replace -> appReplaceExitTransition
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
        contentKey = { it }
    ) {
        val nestedNavState = navState.copy(
            backStack = navState.backStack.drop(lastContentIndex + 1)
        )

        getScreen(it).Content(nestedNavState)
    }

}

private fun getScreen(destination: Destination): Screen {
    val result = when (destination) {
        Home -> HomeScreen(destination)
        Accounts -> AccountsScreen(destination)
        Transactions -> TransactionsScreen(destination)
        Cards -> CardsScreen(destination)
        else -> null
    }

    return result!!
}

@OptIn(ExperimentalAnimationApi::class)
private val AnimatedContentScope<*>.appPushEnterTransition: EnterTransition
    get() {
        return slideIntoContainer(
            AnimatedContentScope.SlideDirection.Left,
            animationSpec = tween()
        )
    }

@OptIn(ExperimentalAnimationApi::class)
private val AnimatedContentScope<*>.appPushExitTransition: ExitTransition
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

private val appReplaceEnterTransition: EnterTransition
    get() {
        return fadeIn(
            animationSpec = tween()
        )
    }

private val appReplaceExitTransition: ExitTransition
    get() {
        return fadeOut(
            animationSpec = tween()
        )
    }