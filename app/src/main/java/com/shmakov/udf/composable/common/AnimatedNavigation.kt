package com.shmakov.udf.composable.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.shmakov.udf.UdfApp.Companion.appState
import com.shmakov.udf.composable.screen.AccountBottomSheet
import com.shmakov.udf.composable.screen.AccountDetailsScreen
import com.shmakov.udf.composable.screen.AccountsScreen
import com.shmakov.udf.composable.screen.CardsScreen
import com.shmakov.udf.composable.screen.HomeScreen
import com.shmakov.udf.composable.screen.TransactionsScreen
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.Destination
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.ModalScreen
import com.shmakov.udf.navigation.ModalScreenState
import com.shmakov.udf.navigation.NavActionType
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.Screen
import com.shmakov.udf.navigation.Transactions
import java.util.concurrent.atomic.AtomicReference

@Composable
fun AnimatedNavigation(navState: NavState, into: Destination) {
    val rootDestination = navState.backStack.firstOrNull() ?: return

    lateinit var lastScreen: Screen
    var lastContentIndex = 0
    var childDestination = into

    val targetDestination = navState.backStack
        .foldIndexed<Destination, Destination?>(null) { index, lastShownDestination, nextDestination ->
            val result = if (lastShownDestination == null) {
                nextDestination
            } else if (nextDestination is Destination.Content) {
                childDestination = lastScreen.whereToShowChild(
                    whereShowCurrentDestination = childDestination,
                    childDestination = nextDestination,
                )
                if (childDestination == into) {
                    lastContentIndex = index

                    nextDestination
                } else {
                    lastShownDestination
                }
            } else {
                lastShownDestination
            }

            // TODO: make one interface for content and modal screens
            lastScreen = if (nextDestination is Destination.Content) {
                getContentScreen(nextDestination)
            } else {
                lastScreen
            }

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
    ) { destination ->
        val nestedNavState = navState.copy(
            backStack = navState.backStack.drop(lastContentIndex + 1)
        )

        getContentScreen(destination).Content(nestedNavState)

        val modalDestinations = navState.backStack
            .dropWhile { it != destination }
            .drop(1)
            .takeWhile { it is Destination.Modal }
            .filterIsInstance<Destination.Modal>()

        val rememberedModalDestinations =
            remember { AtomicReference(emptyList<Destination.Modal>()) }

        val lastModalDestinations = rememberedModalDestinations.get()

        val allDestinations = mutableListOf<Destination.Modal>()

        var lastIndex = 0

        var lastNewIndex = modalDestinations.size

        modalDestinations.forEachIndexed { index, modalDestination ->
            val indexInLast = lastModalDestinations.indexOf(modalDestination)

            if (indexInLast != -1) {
                allDestinations += lastModalDestinations.subList(lastIndex, indexInLast)
                allDestinations += modalDestination
                lastIndex = indexInLast + 1
            } else {
                lastNewIndex = index
                return@forEachIndexed
            }
        }

        allDestinations += lastModalDestinations.drop(lastIndex)
        allDestinations += modalDestinations.drop(lastNewIndex)

        allDestinations.forEach { item ->
            key(item) {
                val screen = getModalScreen(item)

                screen.ModalContent(
                    targetState = if (item in modalDestinations) {
                        ModalScreenState.Shown
                    } else {
                        ModalScreenState.Hidden
                    },
                    onHide = {
                        rememberedModalDestinations.getAndUpdate { items ->
                            items - item
                        }

                        appState = appState.copy(
                            navState = appState.navState
                                .copy(
                                    backStack = appState.navState
                                        .backStack
                                        .filter {
                                            it != item
                                        },
                                ),
                        )
                    },
                )
            }
        }

        rememberedModalDestinations.set(modalDestinations)
    }
}

// TODO: create separated solution
private fun getContentScreen(destination: Destination): Screen {
    val result = when (destination) {
        is Home -> HomeScreen(destination)
        is Accounts -> AccountsScreen(destination)
        is Transactions -> TransactionsScreen(destination)
        is Cards -> CardsScreen(destination)
        is AccountDetails -> AccountDetailsScreen(destination)
        else -> null
    }

    return result!!
}

// TODO: create separated solution
private fun getModalScreen(destination: Destination): ModalScreen {
    val result = when (destination) {
        is Account -> AccountBottomSheet(destination)
        else -> null
    }

    return result!!
}

private val AnimatedContentTransitionScope<*>.appPushEnterTransition: EnterTransition
    get() {
        return slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween()
        )
    }

private val AnimatedContentTransitionScope<*>.appPushExitTransition: ExitTransition
    get() {
        return slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween()
        )
    }

private val AnimatedContentTransitionScope<*>.appPopEnterTransition: EnterTransition
    get() {
        return slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween()
        )
    }

private val AnimatedContentTransitionScope<*>.appPopExitTransition: ExitTransition
    get() {
        return slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.Right,
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