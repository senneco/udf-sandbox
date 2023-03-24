package com.shmakov.udf.composable

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.shmakov.udf.composable.screen.*
import com.shmakov.udf.navigation.*
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
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
    ) {
        val nestedNavState = navState.copy(
            backStack = navState.backStack.drop(lastContentIndex + 1)
        )

        getContentScreen(it).Content(nestedNavState)

        val modalDestinations = navState.backStack
            .drop(lastContentIndex)
            .filterIsInstance<Destination.Modal>()

        val rememberedModalDestinations =
            remember { AtomicReference(emptyList<Destination.Modal>()) }

        val itemsToRemove = rememberedModalDestinations.get() - modalDestinations

        itemsToRemove.forEach { itemToRemove ->
            val screen = getModalScreen(itemToRemove)
            key(itemToRemove) {

                screen.Content(
                    targetState = SheetValue.Hidden,
                    nestedNavState = NavState(
                        emptyList(), NavActionType.Pop
                    ),
                    onHide = {
                        rememberedModalDestinations.getAndUpdate { items ->
                            items - itemToRemove
                        }
                    },
                )
            }
        }

        val itemsToAdd = modalDestinations - rememberedModalDestinations.get()

        itemsToAdd.forEach { itemToAdd ->
            val screen = getModalScreen(itemToAdd)
            key(itemToAdd) {

                screen.Content(
                    targetState = SheetValue.Expanded,
                    nestedNavState = NavState(
                        emptyList(), NavActionType.Push
                    ),
                    onHide = {
                        rememberedModalDestinations.getAndUpdate { items ->
                            items - itemToAdd
                        }
                    },
                )
            }
        }

        // TODO: remove redundant items
        rememberedModalDestinations.set(modalDestinations)
    }
}

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

private fun getModalScreen(destination: Destination): ModalScreen {
    val result = when (destination) {
        is Account -> AccountScreen(destination)
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