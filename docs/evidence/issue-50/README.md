# Issue #50 — single-owner store for the complete tab graph

## Scope

This slice composes the existing reducer and one-key storage into one internal application-facing
`TabNavigationStore`. An application owns its lifetime through its own `ViewModel`; the store does
not inherit from `ViewModel` or choose tab, Back, deep-link, animation, retry, or UI policy.

The store accepts one `SavedStateHandle`, one application `RouteCodec`, and a lazy Java-friendly
fallback factory. It exposes read-only frames, a typed startup result, and typed dispatch results
that keep persistence failures visible. Constructor and dispatch are `@MainThread`, matching the
AndroidX Lifecycle 2.6.1 `SavedStateHandle` access contract.

## TDD evidence

1. Eleven startup, dispatch, restoration, ordering, and API contracts were written before the
   production type existed.
2. The observed RED run failed at `compileDebugUnitTestKotlin` on the missing
   `TabNavigationStore`, `TabNavigationStateFactory`, `TabNavigationFrame`,
   `TabNavigationStartResult`, and `TabNavigationDispatchResult` symbols.
3. The minimal one-owner implementation made all eleven initial focused contracts pass.
4. An architecture pre-review found that the first draft mixed worker-thread concurrency with
   `SavedStateHandle`'s `@MainThread` contract. The issue and tests were corrected before production:
   rapid exact-guard sequencing remains supported, while cross-thread dispatch is explicitly out
   of scope. Save-before-publish is proven synchronously from a codec callback.
5. Review expanded the matrix with rejected-plus-failed-recovery, stale callback after ReplaceGraph,
   non-reentrant codec dispatch, and a Java compile-smoke. The focused total is fourteen contracts.

## Verified contracts

- Missing creates one exact lazy fallback instance and immediately attempts one canonical save;
- Restored uses the exact whole graph, never invokes the fallback factory, and does not rewrite the
  already valid deterministic payload;
- Rejected reports an owned runtime-unmodifiable exact problem list, creates one whole fallback,
  and rewrites without mixing payload fragments;
- startup save failure remains visible while the exact fallback stays usable in memory;
- initial and restored frames start at revision `0` with no transition metadata;
- Changed returns the exact reducer result and save result, saves before publication, publishes one
  frame, and increments revision exactly once;
- typed save failure does not roll back a valid in-memory Changed result and triggers best-effort
  stale cleanup through the storage boundary;
- Unchanged performs no save and preserves exact frame, state, revision, transition, and payload
  identity;
- reentrant dispatch from an application codec is rejected before reduction and cannot overwrite
  the outer changed frame or duplicate its revision;
- leaf navigation in two tabs preserves both independent histories and exact selected tab;
- ReplaceGraph persists and publishes one complete graph with no intermediate observable frame;
- a copied primitive payload restores exact selection, order, routes, histories, and IDs through an
  injected consumer codec while process-local metadata resets;
- the outward flow is read-only and no demo codec or application policy is embedded in the store.

## Verification

```text
Focused TabNavigationStore API: 14/14 (13 Kotlin + 1 Java)

./gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL

JVM: 264 tests / 30 suites
Failures: 0
Errors: 0
Skipped: 0
```

Architecture, test, and external-consumer reviews returned with no open P0–P2 findings. Review
found and closed three important gaps before the final gate: Android main-thread ownership versus
worker concurrency, reentrant codec dispatch during synchronous save, and incomplete visibility or
Java proof for typed startup/save outcomes.

No emulator or screenshot is attached because this slice changes no UI. The fresh-handle JVM
contract proves owner restart over the storage boundary, not Activity recreation or real Android
process death. Those remain part of the later #26 renderer/lifecycle integration.
