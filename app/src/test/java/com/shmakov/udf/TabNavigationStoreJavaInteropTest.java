package com.shmakov.udf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.SavedStateHandle;
import com.shmakov.udf.navigation.Accounts;
import com.shmakov.udf.navigation.Cards;
import com.shmakov.udf.navigation.DemoRouteCodec;
import com.shmakov.udf.navigation.NavState;
import com.shmakov.udf.navigation.TabAction;
import com.shmakov.udf.navigation.TabId;
import com.shmakov.udf.navigation.TabNavigationState;
import com.shmakov.udf.navigation.TabState;
import java.util.Arrays;
import org.junit.Test;

public final class TabNavigationStoreJavaInteropTest {

    @Test
    public void javaConsumerCanConstructObserveAndDispatchTypedOutcomes() {
        TabId accountsId = new TabId("accounts");
        TabId cardsId = new TabId("cards");
        TabNavigationState initial = TabNavigationState.create(
                accountsId,
                Arrays.asList(
                        new TabState(accountsId, NavState.startAt(Accounts.INSTANCE)),
                        new TabState(cardsId, NavState.startAt(Cards.INSTANCE))));
        TabNavigationStateFactory factory = () -> initial;

        TabNavigationStore store = new TabNavigationStore(
                new SavedStateHandle(),
                DemoRouteCodec.INSTANCE,
                factory);

        assertTrue(store.getStartResult() instanceof TabNavigationStartResult.StartedFresh);
        assertSame(initial, store.getFrames().getValue().getState());

        TabNavigationDispatchResult changed = store.dispatch(TabAction.select(cardsId));
        assertTrue(changed instanceof TabNavigationDispatchResult.Changed);
        TabNavigationDispatchResult.Changed typedChanged =
                (TabNavigationDispatchResult.Changed) changed;
        assertSame(typedChanged.getReduction().getState(), store.getFrames().getValue().getState());
        assertSame(TabNavigationSaveResult.Saved.INSTANCE, typedChanged.getSaveResult());
        assertEquals(1L, store.getFrames().getValue().getRevision());

        TabNavigationFrame frameAfterChange = store.getFrames().getValue();
        TabNavigationDispatchResult unchanged = store.dispatch(TabAction.select(cardsId));

        assertTrue(unchanged instanceof TabNavigationDispatchResult.Unchanged);
        assertSame(frameAfterChange, store.getFrames().getValue());
    }
}
