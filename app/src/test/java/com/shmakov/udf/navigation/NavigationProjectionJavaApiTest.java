package com.shmakov.udf.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public final class NavigationProjectionJavaApiTest {

    @Test
    public void projectorAndPlacementFactoriesNeedNoKotlinCompanionSyntax() {
        BackStackEntry home = new BackStackEntry(new EntryId("home"), Home.INSTANCE);
        BackStackEntry accounts = new BackStackEntry(new EntryId("accounts"), Accounts.INSTANCE);
        NavStateCreationResult created = NavState.fromEntries(java.util.Arrays.asList(home, accounts));
        NavState state = ((NavStateCreationResult.Valid) created).getState();

        NavigationLayoutPolicy policy = request -> {
            assertEquals(Collections.singletonList(
                    new ContentSlot(ContentSlotId.root(), home)
            ), request.getContentPath());
            assertEquals(home, request.getCurrentContent().getEntry());
            assertEquals(accounts, request.getNextContent());
            return ContentPlacementDecision.root();
        };

        NavProjectionResult result = NavProjector.project(state, policy);

        assertTrue(result instanceof NavProjectionResult.Success);
        NavigationRenderTree tree = ((NavProjectionResult.Success) result).getTree();
        assertEquals(ContentSlotId.root(), tree.getRoot().getSlotId());
        assertEquals(accounts, tree.getRoot().getEntry());
        assertTrue(tree.getNestedSlots().isEmpty());
        assertTrue(tree.getModalLayers().isEmpty());

        assertEquals(
                new ContentPlacementDecision.PlaceIn(ContentSlotId.childOf(home.getId())),
                ContentPlacementDecision.childOf(home.getId())
        );
        assertEquals(
                new ContentPlacementDecision.PlaceIn(ContentSlotId.root()),
                ContentPlacementDecision.inSlot(ContentSlotId.root())
        );
        assertEquals(
                new ContentPlacementDecision.Reject(
                        new LayoutPolicyError("unsupported", "not available")
                ),
                ContentPlacementDecision.reject("unsupported", "not available")
        );
    }

    @Test
    public void JavaNullPolicyResultBecomesTypedFailure() {
        BackStackEntry home = new BackStackEntry(new EntryId("home"), Home.INSTANCE);
        BackStackEntry accounts = new BackStackEntry(new EntryId("accounts"), Accounts.INSTANCE);
        NavStateCreationResult created = NavState.fromEntries(java.util.Arrays.asList(home, accounts));
        NavState state = ((NavStateCreationResult.Valid) created).getState();

        NavProjectionResult result = NavProjector.project(state, request -> null);

        assertTrue(result instanceof NavProjectionResult.Failure);
        NavProjectionProblem problem = ((NavProjectionResult.Failure) result).getProblem();
        assertTrue(problem instanceof NavProjectionProblem.PolicyFailed);
        NavProjectionProblem.PolicyFailed failed = (NavProjectionProblem.PolicyFailed) problem;
        assertEquals(1, failed.getIndex());
        assertEquals(accounts, failed.getEntry());
        assertEquals("null_decision", failed.getError().getCode());
    }
}
