package com.shmakov.udf.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class TabNavigationReducerJavaApiTest {

    @Test
    public void staticActionFactoriesAndReducerAreUsableWithoutCompanionSyntax() {
        TabId accountsId = new TabId("accounts");
        TabId cardsId = new TabId("cards");
        TabState accounts = tab(accountsId, "accounts-root", Accounts.INSTANCE);
        TabState cards = tab(cardsId, "cards-root", Cards.INSTANCE);
        TabNavigationState graph = TabNavigationState.create(
                accountsId,
                Arrays.asList(accounts, cards)
        );

        TabAction.Select select = TabAction.select(cardsId);
        TabAction.NavigateInSelectedTab navigate = TabAction.navigateInSelectedTab(
                graph,
                NavAction.pop()
        );
        TabAction.OpenTab open = TabAction.openTab(cardsId, cards.getHistory());
        TabAction.ReplaceGraph replace = TabAction.replaceGraph(graph);
        TabAction.Back backFromGraph = TabAction.back(graph);
        TabAction.Back exactBack = TabAction.back(
                accountsId,
                accounts.getHistory().getTop().getId()
        );

        TabReduction reduction = TabReducer.reduce(graph, select);

        assertTrue(reduction instanceof TabReduction.Changed);
        assertEquals(cardsId, reduction.getState().getSelectedTabId());
        assertEquals(accountsId, navigate.getExpectedSelectedTabId());
        assertEquals(cardsId, open.getTabId());
        assertEquals(graph, replace.getTarget());
        assertEquals(exactBack, backFromGraph);
    }

    private static TabState tab(TabId id, String entryId, ContentRoute route) {
        NavStateCreationResult result = NavState.fromEntries(
                Collections.singletonList(
                        new BackStackEntry(new EntryId(entryId), route)
                )
        );
        assertTrue(result instanceof NavStateCreationResult.Valid);
        return new TabState(id, ((NavStateCreationResult.Valid) result).getState());
    }
}
