package com.shmakov.udf

import androidx.lifecycle.SavedStateHandle
import com.shmakov.udf.navigation.Account
import com.shmakov.udf.navigation.AccountDetails
import com.shmakov.udf.navigation.Accounts
import com.shmakov.udf.navigation.BackStackEntry
import com.shmakov.udf.navigation.Card
import com.shmakov.udf.navigation.Cards
import com.shmakov.udf.navigation.ContentRoute
import com.shmakov.udf.navigation.DemoRouteCodec
import com.shmakov.udf.navigation.EncodedRoute
import com.shmakov.udf.navigation.EntryId
import com.shmakov.udf.navigation.NavAction
import com.shmakov.udf.navigation.NavState
import com.shmakov.udf.navigation.NavStateCreationResult
import com.shmakov.udf.navigation.Route
import com.shmakov.udf.navigation.RouteCodec
import com.shmakov.udf.navigation.RouteCodecError
import com.shmakov.udf.navigation.RouteDecodeResult
import com.shmakov.udf.navigation.RouteEncodeResult
import com.shmakov.udf.navigation.SnapshotProblem
import com.shmakov.udf.navigation.TabAction
import com.shmakov.udf.navigation.TabId
import com.shmakov.udf.navigation.TabNavigationSnapshotProblem
import com.shmakov.udf.navigation.TabNavigationState
import com.shmakov.udf.navigation.TabReduction
import com.shmakov.udf.navigation.TabState
import com.shmakov.udf.navigation.TabTransitionIntent
import com.shmakov.udf.navigation.TabUnchangedReason
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TabNavigationStoreContractTest {

    @Test
    fun `missing payload lazily creates exact fallback and saves one canonical graph`() {
        val fallback = demoGraph(selectedTabId = ACCOUNTS_TAB)
        val handle = SavedStateHandle(mapOf(UNRELATED_KEY to "keep"))
        var factoryCalls = 0

        val store = TabNavigationStore(
            savedStateHandle = handle,
            routeCodec = DemoRouteCodec,
            fallbackStateFactory = TabNavigationStateFactory {
                factoryCalls += 1
                fallback
            },
        )

        val exposedFrames: StateFlow<TabNavigationFrame> = store.frames
        val frame = exposedFrames.value

        assertEquals(1, factoryCalls)
        assertSame(fallback, frame.state)
        assertEquals(0L, frame.revision)
        assertNull(frame.transition)
        assertFalse(exposedFrames is MutableStateFlow<*>)
        assertEquals(
            TabNavigationStartResult.StartedFresh(TabNavigationSaveResult.Saved),
            store.startResult,
        )
        assertEquals(fallback, restored(handle, DemoRouteCodec))
        assertEquals("keep", handle.get<String>(UNRELATED_KEY))
    }

    @Test
    fun `recovered result owns defensive runtime unmodifiable restore problems`() {
        val problem = TabNavigationRestoreProblem.InvalidEnvelope(
            TabNavigationSnapshotEnvelopeProblem.InvalidPayload(
                code = "invalid_magic",
                message = "Tab navigation payload has an unknown magic header.",
            ),
        )
        val source = mutableListOf<TabNavigationRestoreProblem>(problem)
        val result = TabNavigationStartResult.Recovered(
            restoreProblems = source,
            fallbackSaveResult = TabNavigationSaveResult.Saved,
        )

        source.clear()

        assertEquals(listOf(problem), result.restoreProblems)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (result.restoreProblems as MutableList<TabNavigationRestoreProblem>).clear()
        }
    }

    @Test
    fun `valid payload restores exact graph without constructing fallback or rewriting payload`() {
        val restoredGraph = demoGraph(selectedTabId = CARDS_TAB)
        val handle = SavedStateHandle()
        val storage = SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec)
        assertSaved(storage.save(restoredGraph))
        val key = navigationKey(handle)
        val payloadBeforeOwner = checkNotNull(handle.get<ArrayList<String>>(key))
        var factoryCalls = 0

        val store = TabNavigationStore(
            savedStateHandle = handle,
            routeCodec = DemoRouteCodec,
            fallbackStateFactory = TabNavigationStateFactory {
                factoryCalls += 1
                demoGraph(selectedTabId = ACCOUNTS_TAB, idPrefix = "unused")
            },
        )

        assertEquals(0, factoryCalls)
        assertEquals(TabNavigationStartResult.Restored, store.startResult)
        assertEquals(restoredGraph, store.frames.value.state)
        assertEquals(0L, store.frames.value.revision)
        assertNull(store.frames.value.transition)
        assertSame(payloadBeforeOwner, handle.get<ArrayList<String>>(key))
    }

    @Test
    fun `rejected payload reports exact problems and rewrites one whole fallback graph`() {
        val fallback = demoGraph(selectedTabId = ACCOUNTS_TAB, idPrefix = "fallback")
        val handle = SavedStateHandle(mapOf(UNRELATED_KEY to "keep"))
        val storage = SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec)
        assertSaved(storage.save(demoGraph(selectedTabId = CARDS_TAB, idPrefix = "stale")))
        val key = navigationKey(handle)
        handle[key] = arrayListOf("not-tab-nav", "1", "1", "accounts", "0")
        val restoreProblems = listOf(
            TabNavigationRestoreProblem.InvalidEnvelope(
                TabNavigationSnapshotEnvelopeProblem.InvalidPayload(
                    code = "invalid_magic",
                    message = "Tab navigation payload has an unknown magic header.",
                ),
            ),
        )
        var factoryCalls = 0

        val store = TabNavigationStore(
            savedStateHandle = handle,
            routeCodec = DemoRouteCodec,
            fallbackStateFactory = TabNavigationStateFactory {
                factoryCalls += 1
                fallback
            },
        )

        assertEquals(1, factoryCalls)
        assertEquals(
            TabNavigationStartResult.Recovered(
                restoreProblems = restoreProblems,
                fallbackSaveResult = TabNavigationSaveResult.Saved,
            ),
            store.startResult,
        )
        assertSame(fallback, store.frames.value.state)
        assertEquals(0L, store.frames.value.revision)
        assertNull(store.frames.value.transition)
        assertEquals(fallback, restored(handle, DemoRouteCodec))
        assertEquals(
            fallback.tabs.flatMap { tab -> tab.history.entries.map { it.id } },
            store.frames.value.state.tabs.flatMap { tab -> tab.history.entries.map { it.id } },
        )
        assertEquals("keep", handle.get<String>(UNRELATED_KEY))
    }

    @Test
    fun `startup save failure remains visible while exact fallback stays usable in memory`() {
        val unsupportedEntry = entry("unsupported-root", UnsupportedContent)
        val fallback = graph(
            selectedTabId = TabId("unsupported"),
            tab("unsupported", unsupportedEntry),
        )
        val handle = SavedStateHandle(mapOf(UNRELATED_KEY to "keep"))
        val expectedFailure = TabNavigationSaveResult.Failed(
            listOf(
                TabNavigationSaveProblem.Snapshot(
                    TabNavigationSnapshotProblem.HistoryProblem(
                        tabIndex = 0,
                        tabId = "unsupported",
                        problem = SnapshotProblem.RouteEncodeFailed(
                            index = 0,
                            entryId = unsupportedEntry.id,
                            error = RouteCodecError(
                                code = "unsupported_route",
                                message = "Route is not supported by DemoRouteCodec.",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val store = TabNavigationStore(
            savedStateHandle = handle,
            routeCodec = DemoRouteCodec,
            fallbackStateFactory = TabNavigationStateFactory { fallback },
        )

        assertEquals(TabNavigationStartResult.StartedFresh(expectedFailure), store.startResult)
        assertSame(fallback, store.frames.value.state)
        assertEquals(0L, store.frames.value.revision)
        assertNull(store.frames.value.transition)
        assertEquals(setOf(UNRELATED_KEY), handle.keys())
        assertEquals("keep", handle.get<String>(UNRELATED_KEY))
    }

    @Test
    fun `rejected payload reports both restore problems and failed fallback save`() {
        val unsupportedEntry = entry("unsupported-root", UnsupportedContent)
        val fallback = graph(
            selectedTabId = TabId("unsupported"),
            tab("unsupported", unsupportedEntry),
        )
        val handle = SavedStateHandle(mapOf(UNRELATED_KEY to "keep"))
        val storage = SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec)
        assertSaved(storage.save(demoGraph(selectedTabId = ACCOUNTS_TAB)))
        val key = navigationKey(handle)
        handle[key] = arrayListOf("not-tab-nav", "1", "1", "accounts", "0")
        val restoreProblems = listOf(
            TabNavigationRestoreProblem.InvalidEnvelope(
                TabNavigationSnapshotEnvelopeProblem.InvalidPayload(
                    code = "invalid_magic",
                    message = "Tab navigation payload has an unknown magic header.",
                ),
            ),
        )
        val saveFailure = TabNavigationSaveResult.Failed(
            listOf(
                TabNavigationSaveProblem.Snapshot(
                    TabNavigationSnapshotProblem.HistoryProblem(
                        tabIndex = 0,
                        tabId = "unsupported",
                        problem = SnapshotProblem.RouteEncodeFailed(
                            index = 0,
                            entryId = unsupportedEntry.id,
                            error = RouteCodecError(
                                code = "unsupported_route",
                                message = "Route is not supported by DemoRouteCodec.",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val store = TabNavigationStore(
            savedStateHandle = handle,
            routeCodec = DemoRouteCodec,
            fallbackStateFactory = TabNavigationStateFactory { fallback },
        )

        assertEquals(
            TabNavigationStartResult.Recovered(restoreProblems, saveFailure),
            store.startResult,
        )
        assertSame(fallback, store.frames.value.state)
        assertEquals(0L, store.frames.value.revision)
        assertNull(store.frames.value.transition)
        assertFalse(handle.contains(key))
        assertEquals("keep", handle.get<String>(UNRELATED_KEY))
    }

    @Test
    fun `changed dispatch publishes exact reduction and stale replay preserves frame and payload identity`() {
        val initial = demoGraph(selectedTabId = ACCOUNTS_TAB)
        val handle = SavedStateHandle()
        val store = store(handle, DemoRouteCodec, initial)
        val accountsRootId = checkNotNull(initial[ACCOUNTS_TAB]).history.top.id
        val details = entry("account-details", AccountDetails(accountId = 42))
        val action = TabAction.navigateInSelectedTab(
            expectedSelectedTabId = ACCOUNTS_TAB,
            expectedTopId = accountsRootId,
            action = NavAction.Push(accountsRootId, details),
        )

        val changed = changed(store.dispatch(action))
        val changedFrame = store.frames.value
        val key = navigationKey(handle)
        val payloadAfterChange = checkNotNull(handle.get<ArrayList<String>>(key))

        assertSame(changed.reduction.state, changedFrame.state)
        assertEquals(1L, changedFrame.revision)
        assertEquals(changed.reduction.transition, changedFrame.transition)
        assertEquals(TabNavigationSaveResult.Saved, changed.saveResult)
        assertEquals(changedFrame.state, restored(handle, DemoRouteCodec))

        val replay = unchanged(store.dispatch(action))

        assertEquals(
            TabUnchangedReason.TopEntryChanged(
                tabId = ACCOUNTS_TAB,
                expectedTopId = accountsRootId,
                actualTopId = details.id,
            ),
            replay.reduction.reason,
        )
        assertSame(changed.reduction.state, replay.reduction.state)
        assertSame(changedFrame, store.frames.value)
        assertSame(payloadAfterChange, handle.get<ArrayList<String>>(key))
        assertEquals(1L, store.frames.value.revision)
        assertEquals(changed.reduction.transition, store.frames.value.transition)
    }

    @Test
    fun `leaf navigation tab switch and other leaf navigation preserve both histories`() {
        val initial = demoGraph(selectedTabId = ACCOUNTS_TAB)
        val handle = SavedStateHandle()
        val store = store(handle, DemoRouteCodec, initial)
        val account = entry("account-42", Account(accountId = 42))
        val accountsRoot = checkNotNull(initial[ACCOUNTS_TAB]).history.top

        val accountChange = changed(
            store.dispatch(
                TabAction.navigateInSelectedTab(
                    initial,
                    NavAction.Push(accountsRoot.id, account),
                ),
            ),
        )
        val accountFrame = store.frames.value
        val selectionChange = changed(store.dispatch(TabAction.select(CARDS_TAB)))
        val selectionFrame = store.frames.value
        val selectedCards = selectionChange.reduction.state
        val cardsRoot = checkNotNull(selectedCards[CARDS_TAB]).history.top
        val card = entry("card-7", Card(cardId = 7))
        val cardChange = changed(
            store.dispatch(
                TabAction.navigateInSelectedTab(
                    selectedCards,
                    NavAction.Push(cardsRoot.id, card),
                ),
            ),
        )
        val finalFrame = store.frames.value

        assertEquals(1L, accountFrame.revision)
        assertSame(accountChange.reduction.state, accountFrame.state)
        assertEquals(accountChange.reduction.transition, accountFrame.transition)
        assertEquals(2L, selectionFrame.revision)
        assertSame(selectionChange.reduction.state, selectionFrame.state)
        assertEquals(selectionChange.reduction.transition, selectionFrame.transition)
        assertEquals(3L, finalFrame.revision)
        assertEquals(CARDS_TAB, finalFrame.state.selectedTabId)
        assertEquals(
            listOf(accountsRoot, account),
            checkNotNull(finalFrame.state[ACCOUNTS_TAB]).history.entries,
        )
        assertEquals(
            listOf(cardsRoot, card),
            checkNotNull(finalFrame.state[CARDS_TAB]).history.entries,
        )
        assertSame(cardChange.reduction.state, finalFrame.state)
        assertEquals(cardChange.reduction.transition, finalFrame.transition)
        assertEquals(finalFrame.state, restored(handle, DemoRouteCodec))
    }

    @Test
    fun `replace graph performs one full save and publishes no intermediate graph`() = runBlocking {
        val initial = demoGraph(selectedTabId = ACCOUNTS_TAB)
        val codec = RecordingRouteCodec(DemoRouteCodec)
        val handle = SavedStateHandle()
        val store = store(handle, codec, initial)
        codec.clearEncodedRoutes()
        val target = graph(
            selectedTabId = CARDS_TAB,
            tab(
                "accounts",
                entry("signed-out-home", Accounts),
            ),
            tab(
                "cards",
                entry("hydrated-cards", Cards),
                entry("hydrated-card-9", Card(cardId = 9)),
            ),
        )
        val initialTop = initial.selectedTab.history.top
        val staleAction = TabAction.navigateInSelectedTab(
            initial,
            NavAction.Push(initialTop.id, entry("stale", Account(accountId = 99))),
        )
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            store.frames.take(2).toList()
        }

        val result = changed(store.dispatch(TabAction.replaceGraph(target)))
        val frames = withTimeout(2_000) { observed.await() }

        assertEquals(2, frames.size)
        assertSame(initial, frames.first().state)
        assertSame(target, frames.last().state)
        assertSame(target, result.reduction.state)
        assertEquals(1L, frames.last().revision)
        assertEquals(result.reduction.transition, frames.last().transition)
        assertEquals(
            target.tabs.flatMap { tab -> tab.history.entries.map { it.route } },
            codec.encodedRoutes(),
        )
        assertEquals(target, restored(handle, DemoRouteCodec))

        val replacedFrame = store.frames.value
        val key = navigationKey(handle)
        val replacedPayload = checkNotNull(handle.get<ArrayList<String>>(key))
        val encodedRoutes = codec.encodedRoutes()
        val stale = unchanged(store.dispatch(staleAction))

        assertEquals(
            TabUnchangedReason.SelectedTabChanged(
                expectedSelectedTabId = ACCOUNTS_TAB,
                actualSelectedTabId = CARDS_TAB,
            ),
            stale.reduction.reason,
        )
        assertSame(replacedFrame, store.frames.value)
        assertSame(replacedPayload, handle.get<ArrayList<String>>(key))
        assertEquals(encodedRoutes, codec.encodedRoutes())
    }

    @Test
    fun `dispatch save failure is returned and does not suppress valid changed frame`() {
        val initial = demoGraph(selectedTabId = ACCOUNTS_TAB)
        val handle = SavedStateHandle(mapOf(UNRELATED_KEY to "keep"))
        val store = store(handle, DemoRouteCodec, initial)
        val key = navigationKey(handle)
        val accountsRoot = checkNotNull(initial[ACCOUNTS_TAB]).history.top
        val unsupported = entry("unsupported-child", UnsupportedContent)
        val expectedFailure = TabNavigationSaveResult.Failed(
            listOf(
                TabNavigationSaveProblem.Snapshot(
                    TabNavigationSnapshotProblem.HistoryProblem(
                        tabIndex = 0,
                        tabId = ACCOUNTS_TAB.value,
                        problem = SnapshotProblem.RouteEncodeFailed(
                            index = 1,
                            entryId = unsupported.id,
                            error = RouteCodecError(
                                code = "unsupported_route",
                                message = "Route is not supported by DemoRouteCodec.",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = changed(
            store.dispatch(
                TabAction.navigateInSelectedTab(
                    initial,
                    NavAction.Push(accountsRoot.id, unsupported),
                ),
            ),
        )

        assertEquals(expectedFailure, result.saveResult)
        assertSame(result.reduction.state, store.frames.value.state)
        assertEquals(1L, store.frames.value.revision)
        assertEquals(result.reduction.transition, store.frames.value.transition)
        assertEquals(
            listOf(accountsRoot, unsupported),
            checkNotNull(store.frames.value.state[ACCOUNTS_TAB]).history.entries,
        )
        assertFalse(handle.contains(key))
        assertEquals("keep", handle.get<String>(UNRELATED_KEY))
    }

    @Test
    fun `injected codec survives fresh owner restore and resets process local metadata`() {
        val initial = graph(
            selectedTabId = FIRST_TAB,
            tab("first", entry("first-root", ConsumerRoute("first/root"))),
            tab("second", entry("second-root", ConsumerRoute("second/root"))),
        )
        val firstHandle = SavedStateHandle()
        val first = store(firstHandle, ConsumerCodec, initial)
        changed(first.dispatch(TabAction.select(SECOND_TAB)))
        val selectedSecond = first.frames.value.state
        val secondRoot = checkNotNull(selectedSecond[SECOND_TAB]).history.top
        val secondDetails = entry("second-details", ConsumerRoute("second/details"))
        changed(
            first.dispatch(
                TabAction.navigateInSelectedTab(
                    selectedSecond,
                    NavAction.Push(secondRoot.id, secondDetails),
                ),
            ),
        )
        val beforeFreshOwner = first.frames.value
        assertEquals(2L, beforeFreshOwner.revision)
        assertTrue(beforeFreshOwner.transition != null)
        val key = navigationKey(firstHandle)
        val copiedPayload = ArrayList(checkNotNull(firstHandle.get<ArrayList<String>>(key)))
        val freshHandle = SavedStateHandle(mapOf(key to copiedPayload))
        var freshFactoryCalls = 0

        val fresh = TabNavigationStore(
            savedStateHandle = freshHandle,
            routeCodec = ConsumerCodec,
            fallbackStateFactory = TabNavigationStateFactory {
                freshFactoryCalls += 1
                graph(
                    selectedTabId = FIRST_TAB,
                    tab("first", entry("unused", ConsumerRoute("unused"))),
                )
            },
        )
        val restoredFrame = fresh.frames.value

        assertEquals(0, freshFactoryCalls)
        assertEquals(TabNavigationStartResult.Restored, fresh.startResult)
        assertEquals(beforeFreshOwner.state, restoredFrame.state)
        assertEquals(SECOND_TAB, restoredFrame.state.selectedTabId)
        assertEquals(listOf(FIRST_TAB, SECOND_TAB), restoredFrame.state.tabs.map { it.id })
        assertEquals(
            beforeFreshOwner.state.tabs.flatMap { tab -> tab.history.entries.map { it.id } },
            restoredFrame.state.tabs.flatMap { tab -> tab.history.entries.map { it.id } },
        )
        assertEquals(
            beforeFreshOwner.state.tabs.flatMap { tab -> tab.history.entries.map { it.route } },
            restoredFrame.state.tabs.flatMap { tab -> tab.history.entries.map { it.route } },
        )
        assertEquals(0L, restoredFrame.revision)
        assertNull(restoredFrame.transition)
        assertSame(copiedPayload, freshHandle.get<ArrayList<String>>(key))

        val firstFreshChange = changed(fresh.dispatch(TabAction.select(FIRST_TAB)))

        assertEquals(1L, fresh.frames.value.revision)
        assertEquals(
            TabTransitionIntent.TabSelected(SECOND_TAB, FIRST_TAB),
            fresh.frames.value.transition,
        )
        assertEquals(firstFreshChange.reduction.transition, fresh.frames.value.transition)
        assertEquals(fresh.frames.value.state, restored(freshHandle, ConsumerCodec))
    }

    @Test
    fun `changed save synchronously observes previous frame before publication`() {
        val initial = demoGraph(selectedTabId = ACCOUNTS_TAB)
        val handle = SavedStateHandle()
        assertSaved(SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec).save(initial))
        val codec = ObservingRouteCodec(DemoRouteCodec)
        lateinit var store: TabNavigationStore
        store = TabNavigationStore(
            savedStateHandle = handle,
            routeCodec = codec,
            fallbackStateFactory = TabNavigationStateFactory {
                throw AssertionError("Fallback must stay lazy after a valid restore.")
            },
        )
        val before = store.frames.value
        val observedFrames = mutableListOf<TabNavigationFrame>()
        codec.onEncode = { observedFrames += store.frames.value }

        val result = changed(store.dispatch(TabAction.select(CARDS_TAB)))

        assertTrue(observedFrames.isNotEmpty())
        assertTrue(observedFrames.all { frame -> frame === before })
        assertEquals(1L, store.frames.value.revision)
        assertSame(result.reduction.state, store.frames.value.state)
        assertEquals(result.reduction.transition, store.frames.value.transition)
        assertEquals(store.frames.value.state, restored(handle, DemoRouteCodec))
    }

    @Test
    fun `reentrant codec dispatch cannot overwrite the outer changed frame`() {
        val initial = demoGraph(selectedTabId = ACCOUNTS_TAB)
        val handle = SavedStateHandle()
        assertSaved(SavedStateHandleTabNavigationStorage(handle, DemoRouteCodec).save(initial))
        val key = navigationKey(handle)
        val accountsRoot = initial.selectedTab.history.top
        val nestedAction = TabAction.navigateInSelectedTab(
            initial,
            NavAction.Push(accountsRoot.id, entry("nested", Account(accountId = 99))),
        )
        lateinit var store: TabNavigationStore
        val codec = ReentrantRouteCodec(DemoRouteCodec) { store.dispatch(nestedAction) }
        store = TabNavigationStore(
            savedStateHandle = handle,
            routeCodec = codec,
            fallbackStateFactory = TabNavigationStateFactory {
                throw AssertionError("Fallback must stay lazy after a valid restore.")
            },
        )
        val expectedSaveFailure = TabNavigationSaveResult.Failed(
            listOf(
                TabNavigationSaveProblem.Snapshot(
                    TabNavigationSnapshotProblem.HistoryProblem(
                        tabIndex = 0,
                        tabId = ACCOUNTS_TAB.value,
                        problem = SnapshotProblem.RouteEncodeFailed(
                            index = 0,
                            entryId = accountsRoot.id,
                            error = RouteCodecError(
                                code = "codec_exception",
                                message = "Reentrant TabNavigationStore.dispatch is not supported.",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = changed(store.dispatch(TabAction.select(CARDS_TAB)))

        assertEquals(expectedSaveFailure, result.saveResult)
        assertSame(result.reduction.state, store.frames.value.state)
        assertEquals(CARDS_TAB, store.frames.value.state.selectedTabId)
        assertEquals(listOf(accountsRoot), store.frames.value.state[ACCOUNTS_TAB]?.history?.entries)
        assertEquals(1L, store.frames.value.revision)
        assertEquals(result.reduction.transition, store.frames.value.transition)
        assertFalse(handle.contains(key))

        val followUp = changed(store.dispatch(TabAction.select(ACCOUNTS_TAB)))

        assertEquals(TabNavigationSaveResult.Saved, followUp.saveResult)
        assertSame(followUp.reduction.state, store.frames.value.state)
        assertEquals(ACCOUNTS_TAB, store.frames.value.state.selectedTabId)
        assertEquals(2L, store.frames.value.revision)
        assertEquals(followUp.reduction.transition, store.frames.value.transition)
        assertEquals(store.frames.value.state, restored(handle, DemoRouteCodec))
    }

    private fun store(
        handle: SavedStateHandle,
        codec: RouteCodec,
        fallback: TabNavigationState,
    ): TabNavigationStore = TabNavigationStore(
        savedStateHandle = handle,
        routeCodec = codec,
        fallbackStateFactory = TabNavigationStateFactory { fallback },
    ).also { store ->
        assertEquals(
            TabNavigationStartResult.StartedFresh(TabNavigationSaveResult.Saved),
            store.startResult,
        )
    }

    private fun changed(
        result: TabNavigationDispatchResult,
    ): TabNavigationDispatchResult.Changed = when (result) {
        is TabNavigationDispatchResult.Changed -> result
        is TabNavigationDispatchResult.Unchanged -> throw AssertionError(
            "Expected state change, got ${result.reduction.reason}",
        )
    }

    private fun unchanged(
        result: TabNavigationDispatchResult,
    ): TabNavigationDispatchResult.Unchanged = when (result) {
        is TabNavigationDispatchResult.Changed -> throw AssertionError(
            "Expected no-op, got ${result.reduction.transition}",
        )
        is TabNavigationDispatchResult.Unchanged -> result
    }

    private fun demoGraph(
        selectedTabId: TabId,
        idPrefix: String = "demo",
    ): TabNavigationState = graph(
        selectedTabId,
        tab("accounts", entry("$idPrefix-accounts-root", Accounts)),
        tab("cards", entry("$idPrefix-cards-root", Cards)),
    )

    private fun graph(
        selectedTabId: TabId,
        vararg tabs: TabState,
    ): TabNavigationState = TabNavigationState.create(selectedTabId, tabs.toList())

    private fun tab(
        id: String,
        vararg entries: BackStackEntry,
    ): TabState = TabState(TabId(id), navigation(*entries))

    private fun navigation(vararg entries: BackStackEntry): NavState =
        when (val result = NavState.fromEntries(entries.toList())) {
            is NavStateCreationResult.Valid -> result.state
            is NavStateCreationResult.Invalid -> throw AssertionError(result.problems)
        }

    private fun entry(id: String, route: Route): BackStackEntry =
        BackStackEntry(EntryId(id), route)

    private fun navigationKey(handle: SavedStateHandle): String =
        (handle.keys() - UNRELATED_KEY).single()

    private fun restored(
        handle: SavedStateHandle,
        codec: RouteCodec,
    ): TabNavigationState = when (
        val result = SavedStateHandleTabNavigationStorage(handle, codec).restore()
    ) {
        TabNavigationRestoreResult.Missing -> throw AssertionError("Expected restored graph")
        is TabNavigationRestoreResult.Restored -> result.state
        is TabNavigationRestoreResult.Rejected -> throw AssertionError(result.problems)
    }

    private fun assertSaved(result: TabNavigationSaveResult) {
        if (result !is TabNavigationSaveResult.Saved) {
            result as TabNavigationSaveResult.Failed
            throw AssertionError(result.problems)
        }
    }

    private data class ConsumerRoute(val slug: String) : ContentRoute

    private object UnsupportedContent : ContentRoute

    private object ConsumerCodec : RouteCodec {
        override fun encode(route: Route): RouteEncodeResult = when (route) {
            is ConsumerRoute -> RouteEncodeResult.Success(
                EncodedRoute(
                    type = "consumer/v1",
                    arguments = mapOf("slug" to route.slug),
                ),
            )
            else -> RouteEncodeResult.Failure(
                RouteCodecError("unsupported", "Unsupported consumer route."),
            )
        }

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = if (
            type == "consumer/v1" && arguments.keys == setOf("slug")
        ) {
            RouteDecodeResult.Success(ConsumerRoute(arguments.getValue("slug")))
        } else {
            RouteDecodeResult.Failure(
                RouteCodecError("invalid_consumer_route", "Invalid consumer route."),
            )
        }
    }

    private class RecordingRouteCodec(
        private val delegate: RouteCodec,
    ) : RouteCodec {
        private val encoded = mutableListOf<Route>()

        override fun encode(route: Route): RouteEncodeResult {
            encoded += route
            return delegate.encode(route)
        }

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = delegate.decode(type, arguments)

        fun clearEncodedRoutes() {
            encoded.clear()
        }

        fun encodedRoutes(): List<Route> = ArrayList(encoded)
    }

    private class ObservingRouteCodec(
        private val delegate: RouteCodec,
    ) : RouteCodec {
        var onEncode: (() -> Unit)? = null

        override fun encode(route: Route): RouteEncodeResult {
            onEncode?.invoke()
            return delegate.encode(route)
        }

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = delegate.decode(type, arguments)
    }

    private class ReentrantRouteCodec(
        private val delegate: RouteCodec,
        private val onFirstEncode: () -> Unit,
    ) : RouteCodec {
        private var firstEncode = true

        override fun encode(route: Route): RouteEncodeResult {
            if (firstEncode) {
                firstEncode = false
                onFirstEncode()
            }
            return delegate.encode(route)
        }

        override fun decode(
            type: String,
            arguments: Map<String, String>,
        ): RouteDecodeResult = delegate.decode(type, arguments)
    }

    private companion object {
        val ACCOUNTS_TAB = TabId("accounts")
        val CARDS_TAB = TabId("cards")
        val FIRST_TAB = TabId("first")
        val SECOND_TAB = TabId("second")
        const val UNRELATED_KEY = "unrelated"
    }
}
