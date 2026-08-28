package com.shmakov.udf.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class TabNavigationJavaApiTest {

    @Test
    public void graphFactoryAndLookupAreUsableWithoutKotlinCompanionSyntax() {
        TabId accountsId = new TabId("accounts");
        NavState accountsNavigation = validNavigation(
                new BackStackEntry(new EntryId("accounts-root"), Accounts.INSTANCE)
        );
        TabState accounts = new TabState(accountsId, accountsNavigation);
        TabState cards = new TabState(
                new TabId("cards"),
                validNavigation(new BackStackEntry(new EntryId("cards-root"), Cards.INSTANCE))
        );

        TabNavigationState state = TabNavigationState.create(
                accountsId,
                Arrays.asList(accounts, cards)
        );
        TabNavigationStateCreationResult validated = TabNavigationState.fromTabs(
                accountsId,
                Arrays.asList(accounts, cards)
        );

        assertTrue(validated instanceof TabNavigationStateCreationResult.Valid);
        assertEquals(accountsId, state.getSelectedTabId());
        assertSame(accounts, state.getSelectedTab());
        assertSame(accountsNavigation, state.get(accountsId).getHistory());
        assertEquals(Arrays.asList(accounts, cards), state.getTabs());
    }

    private static NavState validNavigation(BackStackEntry root) {
        NavStateCreationResult created = NavState.fromEntries(Collections.singletonList(root));
        assertTrue(created instanceof NavStateCreationResult.Valid);
        return ((NavStateCreationResult.Valid) created).getState();
    }
}
