package com.shmakov.udf.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class TabNavigationSnapshotJavaApiTest {

    @Test
    public void snapshotAndStaticRestoreNeedNoCompanionSyntax() {
        TabId accountsId = new TabId("accounts");
        TabState accounts = new TabState(
                accountsId,
                validNavigation(new BackStackEntry(new EntryId("accounts-root"), Accounts.INSTANCE))
        );
        TabState cards = new TabState(
                new TabId("cards"),
                validNavigation(new BackStackEntry(new EntryId("cards-root"), Cards.INSTANCE))
        );
        TabNavigationState state = TabNavigationState.create(
                accountsId,
                Arrays.asList(accounts, cards)
        );

        TabNavigationSnapshotResult<TabNavigationStateSnapshot> encoded =
                state.toSnapshot(DemoRouteCodec.INSTANCE);
        assertTrue(encoded instanceof TabNavigationSnapshotResult.Success);
        TabNavigationStateSnapshot snapshot =
                ((TabNavigationSnapshotResult.Success<TabNavigationStateSnapshot>) encoded)
                        .getValue();

        TabNavigationSnapshotResult<TabNavigationState> decoded =
                TabNavigationState.restore(snapshot, DemoRouteCodec.INSTANCE);

        assertEquals(TabNavigationStateSnapshot.CURRENT_VERSION, snapshot.getVersion());
        assertTrue(decoded instanceof TabNavigationSnapshotResult.Success);
        assertEquals(
                state,
                ((TabNavigationSnapshotResult.Success<TabNavigationState>) decoded).getValue()
        );
    }

    @Test
    public void nullJavaCodecResultsKeepTabAndEntryContext() {
        TabId accountsId = new TabId("accounts");
        BackStackEntry root = new BackStackEntry(new EntryId("accounts-root"), Accounts.INSTANCE);
        TabNavigationState state = TabNavigationState.create(
                accountsId,
                Collections.singletonList(new TabState(accountsId, validNavigation(root)))
        );
        RouteCodec nullCodec = new RouteCodec() {
            @Override
            public RouteEncodeResult encode(Route route) {
                return null;
            }

            @Override
            public RouteDecodeResult decode(String type, java.util.Map<String, String> arguments) {
                return null;
            }
        };

        TabNavigationSnapshotResult<TabNavigationStateSnapshot> encoded =
                state.toSnapshot(nullCodec);
        assertTrue(encoded instanceof TabNavigationSnapshotResult.Failure);
        assertEquals(
                Collections.singletonList(
                        new TabNavigationSnapshotProblem.HistoryProblem(
                                0,
                                "accounts",
                                new SnapshotProblem.RouteEncodeFailed(
                                        0,
                                        root.getId(),
                                        nullCodecError()
                                )
                        )
                ),
                ((TabNavigationSnapshotResult.Failure) encoded).getProblems()
        );

        NavStateSnapshot leaf = new NavStateSnapshot(
                NavStateSnapshot.CURRENT_VERSION,
                Collections.singletonList(
                        new NavStateSnapshot.Entry(
                                "accounts-root",
                                "accounts/v1",
                                Collections.emptyMap()
                        )
                )
        );
        TabNavigationStateSnapshot snapshot = new TabNavigationStateSnapshot(
                TabNavigationStateSnapshot.CURRENT_VERSION,
                "accounts",
                Collections.singletonList(
                        new TabNavigationStateSnapshot.Tab("accounts", leaf)
                )
        );
        TabNavigationSnapshotResult<TabNavigationState> decoded =
                TabNavigationState.restore(snapshot, nullCodec);

        assertTrue(decoded instanceof TabNavigationSnapshotResult.Failure);
        assertEquals(
                Collections.singletonList(
                        new TabNavigationSnapshotProblem.HistoryProblem(
                                0,
                                "accounts",
                                new SnapshotProblem.RouteDecodeFailed(
                                        0,
                                        "accounts-root",
                                        "accounts/v1",
                                        nullCodecError()
                                )
                        )
                ),
                ((TabNavigationSnapshotResult.Failure) decoded).getProblems()
        );
    }

    private static NavState validNavigation(BackStackEntry root) {
        NavStateCreationResult created = NavState.fromEntries(Collections.singletonList(root));
        assertTrue(created instanceof NavStateCreationResult.Valid);
        return ((NavStateCreationResult.Valid) created).getState();
    }

    private static RouteCodecError nullCodecError() {
        return new RouteCodecError("codec_exception", "Route codec returned null.");
    }
}
