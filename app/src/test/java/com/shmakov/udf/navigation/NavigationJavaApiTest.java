package com.shmakov.udf.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public final class NavigationJavaApiTest {

    @Test
    public void staticFactoriesAndReducerAreUsableWithoutKotlinCompanionSyntax() {
        BackStackEntry root = new BackStackEntry(new EntryId("home"), Home.INSTANCE);
        NavStateCreationResult created = NavState.fromEntries(Collections.singletonList(root));
        assertTrue(created instanceof NavStateCreationResult.Valid);
        NavState state = ((NavStateCreationResult.Valid) created).getState();

        NavAction.Push push = NavAction.push(state.getTop().getId(), Accounts.INSTANCE);
        NavAction.NavigateFrom navigate = NavAction.navigateFrom(
                state.getRoot().getId(),
                Transactions.INSTANCE
        );
        NavAction.DismissModal dismiss = NavAction.dismissModal(new EntryId("modal"));
        NavAction.ReplaceHistory replace = NavAction.replaceHistory(state);
        NavAction.ReplaceHistory reset = NavAction.resetTo(Home.INSTANCE);

        NavReduction reduction = NavReducer.reduce(state, push);

        assertTrue(reduction instanceof NavReduction.Changed);
        assertEquals(Accounts.INSTANCE, reduction.getState().getTop().getRoute());
        assertSame(NavAction.Pop.INSTANCE, NavAction.pop());
        assertEquals(state.getRoot().getId(), navigate.getSourceId());
        assertEquals(new EntryId("modal"), dismiss.getEntryId());
        assertSame(state, replace.getTarget());
        assertEquals(Home.INSTANCE, reset.getTarget().getRoot().getRoute());
    }
}
