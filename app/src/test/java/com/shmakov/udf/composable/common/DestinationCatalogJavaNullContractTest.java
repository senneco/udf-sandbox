package com.shmakov.udf.composable.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.shmakov.udf.navigation.ContentPlacementDecision;
import com.shmakov.udf.navigation.Home;
import com.shmakov.udf.navigation.NavProjectionResult;
import com.shmakov.udf.navigation.NavProjector;
import com.shmakov.udf.navigation.NavState;
import com.shmakov.udf.navigation.NavigationLayoutPolicy;
import com.shmakov.udf.navigation.NavigationRenderTree;
import org.junit.Test;

public final class DestinationCatalogJavaNullContractTest {

    @Test
    public void javaNullBindingBecomesTypedContextualFailure() {
        NavState navState = NavState.startAt(Home.INSTANCE);
        NavigationLayoutPolicy policy = request -> ContentPlacementDecision.root();
        NavProjectionResult projection = NavProjector.project(navState, policy);
        assertTrue(projection instanceof NavProjectionResult.Success);
        NavigationRenderTree tree = ((NavProjectionResult.Success) projection).getTree();

        DestinationCatalog javaCatalog = entry -> null;
        DestinationTreeBindingResult result =
                DestinationTreeBinder.INSTANCE.bind(tree, javaCatalog);

        assertTrue(result instanceof DestinationTreeBindingResult.Failure);
        DestinationTreeBindingProblem problem =
                ((DestinationTreeBindingResult.Failure) result).getProblem();
        assertTrue(problem instanceof DestinationTreeBindingProblem.CatalogFailed);
        DestinationTreeBindingProblem.CatalogFailed failure =
                (DestinationTreeBindingProblem.CatalogFailed) problem;
        assertEquals(navState.getRoot(), failure.getEntry());
        assertEquals(DestinationKind.Content, failure.getExpectedKind());
        assertEquals("null_binding", failure.getError().getCode());
    }
}
