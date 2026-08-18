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
import com.shmakov.udf.composable.screen.AccountBottomSheet
import com.shmakov.udf.composable.screen.AccountDetailsScreen
import com.shmakov.udf.composable.screen.AccountsScreen
import com.shmakov.udf.composable.screen.CardsScreen
import com.shmakov.udf.composable.screen.HomeScreen
import com.shmakov.udf.composable.screen.TransactionsScreen
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.ContentRoute
import com.shmakov.udf.navigation.Home
import com.shmakov.udf.navigation.ModalRoute
import com.shmakov.udf.navigation.ModalScreen
import com.shmakov.udf.navigation.ModalScreenState
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavTransitionIntent
import com.shmakov.udf.navigation.RenderSlot
import com.shmakov.udf.navigation.Screen
import com.shmakov.udf.navigation.Transactions
import java.util.concurrent.atomic.AtomicReference

@Composable
fun AnimatedNavigation(
    navState: NavState,
    navTransition: NavTransitionIntent?,
    onNavigationAction: (NavAction) -> Unit,
) {
    AnimatedNavigation(
        entries = navState.entries,
        into = RenderSlot.Root,
        navTransition = navTransition,
        onNavigationAction = onNavigationAction,
    )
}

@Composable
internal fun AnimatedNavigation(
    entries: List<BackStackEntry>,
    into: RenderSlot,
    navTransition: NavTransitionIntent?,
    onNavigationAction: (NavAction) -> Unit,
) {
    val rootEntry = entries.firstOrNull() ?: return

    lateinit var lastScreen: Screen
    var lastContentIndex = 0
    var childSlot = into

    val targetEntry = entries
        .foldIndexed<BackStackEntry, BackStackEntry?>(null) { index, lastShownEntry, nextEntry ->
            val result = if (lastShownEntry == null) {
                nextEntry
            } else if (nextEntry.route is ContentRoute) {
                childSlot = lastScreen.whereToShowChild(
                    currentSlot = childSlot,
                    childEntry = nextEntry,
                )
                if (childSlot == into) {
                    lastContentIndex = index

                    nextEntry
                } else {
                    lastShownEntry
                }
            } else {
                lastShownEntry
            }

            // TODO: make one interface for content and modal screens
            lastScreen = if (nextEntry.route is ContentRoute) {
                getContentScreen(nextEntry)
            } else {
                lastScreen
            }

            result
        } ?: rootEntry

    val finalEnter: AnimatedContentScope<BackStackEntry>.() -> EnterTransition = {
        when (navTransition) {
            is NavTransitionIntent.Pushed -> appPushEnterTransition
            is NavTransitionIntent.Popped,
            is NavTransitionIntent.ModalDismissed -> appPopEnterTransition
            is NavTransitionIntent.BranchReplaced,
            is NavTransitionIntent.HistoryReplaced,
            null -> appReplaceEnterTransition
        }
    }

    val finalExit: AnimatedContentScope<BackStackEntry>.() -> ExitTransition = {
        when (navTransition) {
            is NavTransitionIntent.Pushed -> appPushExitTransition
            is NavTransitionIntent.Popped,
            is NavTransitionIntent.ModalDismissed -> appPopExitTransition
            is NavTransitionIntent.BranchReplaced,
            is NavTransitionIntent.HistoryReplaced,
            null -> appReplaceExitTransition
        }
    }

    val transition = updateTransition(targetState = targetEntry, label = "AnimatedContent")

    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            ContentTransform(
                finalEnter(),
                finalExit(),
            )
        },
        contentKey = { it.id }
    ) { entry ->
        val nestedEntries = entries.drop(lastContentIndex + 1)

        getContentScreen(entry).Content(
            nestedEntries = nestedEntries,
            navTransition = navTransition,
            onNavigationAction = onNavigationAction,
        )

        val modalEntries = entries
            .dropWhile { it.id != entry.id }
            .drop(1)
            .takeWhile { it.route is ModalRoute }

        val rememberedModalEntries =
            remember { AtomicReference(emptyList<BackStackEntry>()) }

        val lastModalEntries = rememberedModalEntries.get()

        val allEntries = mutableListOf<BackStackEntry>()

        var lastIndex = 0

        var lastNewIndex = modalEntries.size

        modalEntries.forEachIndexed { index, modalEntry ->
            val indexInLast = lastModalEntries.indexOfFirst { it.id == modalEntry.id }

            if (indexInLast != -1) {
                allEntries += lastModalEntries.subList(lastIndex, indexInLast)
                allEntries += modalEntry
                lastIndex = indexInLast + 1
            } else {
                lastNewIndex = index
                return@forEachIndexed
            }
        }

        allEntries += lastModalEntries.drop(lastIndex)
        allEntries += modalEntries.drop(lastNewIndex)

        allEntries.forEach { item ->
            key(item.id) {
                val screen = getModalScreen(item)

                screen.ModalContent(
                    targetState = if (modalEntries.any { it.id == item.id }) {
                        ModalScreenState.Shown
                    } else {
                        ModalScreenState.Hidden
                    },
                    onHide = {
                        rememberedModalEntries.getAndUpdate { items ->
                            items.filterNot { it.id == item.id }
                        }

                        onNavigationAction(NavAction.dismissModal(item.id))
                    },
                    onNavigationAction = onNavigationAction,
                )
            }
        }

        rememberedModalEntries.set(modalEntries)
    }
}

// TODO: create separated solution
private fun getContentScreen(entry: BackStackEntry): Screen {
    val result = when (entry.route) {
        is Home -> HomeScreen(entry)
        is Accounts -> AccountsScreen(entry)
        is Transactions -> TransactionsScreen(entry)
        is Cards -> CardsScreen(entry)
        is AccountDetails -> AccountDetailsScreen(entry)
        else -> null
    }

    return result!!
}

// TODO: create separated solution
private fun getModalScreen(entry: BackStackEntry): ModalScreen {
    val result = when (entry.route) {
        is Account -> AccountBottomSheet(entry)
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
