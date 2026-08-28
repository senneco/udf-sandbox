# Issue #44 — typed tab reducer and exact Back

## Scope

This slice adds pure Kotlin actions, reduction outcomes, transition metadata, and exact Back
semantics on top of `TabNavigationState`. It does not integrate the graph into `AppStore`,
persistence, Compose, or Android Back dispatch yet.

## TDD evidence

1. The complete Kotlin and Java contract suite was added before production types. Its first run
   failed at compilation because `TabAction`, `TabReducer`, `TabReduction`, transition values, and
   unchanged reasons did not exist.
2. A minimal generic reducer made 19 Kotlin contracts and one Java contract pass.
3. Review changed the tests first to require selected-tab naming, exact guard ergonomics, precise
   transition coordinates, complete collision diagnostics, deterministic tab order, and guard
   precedence. That run failed against the first API.
4. The refined implementation made the final focused suite pass: 20/20.

## Verified contracts

- tab switching preserves every independent exact history and tab order;
- reselect and unknown tab selection are typed no-ops;
- leaf actions delegate to `NavReducer` only after selected-tab and exact-top guards;
- stale outgoing-tab and stale same-tab callbacks cannot mutate newer state;
- inactive-tab `EntryId` ownership remains protected after delegated navigation;
- `OpenTab` atomically replaces one history and selects its tab;
- `ReplaceGraph` atomically returns the exact target and rejects route identity rebound;
- same `EntryId` and route can be explicitly reparented only through full graph replacement;
- Back exact-pops content, exact-dismisses modal UI, is replay-safe, and exposes root fallthrough;
- an arbitrary fourth application tab requires no reducer branch;
- Kotlin and Java have static, boolean-free action factories;
- all unchanged problem collections are defensive and runtime-unmodifiable.

## Final verification

```text
Focused reducer API: 20/20

./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL

./gradlew testDebugUnitTest --rerun-tasks
BUILD SUCCESSFUL

JVM: 221 tests / 24 suites
Failures: 0
Errors: 0
Skipped: 0
```

The forced full JVM rerun prevents Gradle from treating earlier filtered test outputs as the full
suite. Architecture, test, and external-consumer re-reviews reported no remaining P0, P1, or P2
findings.

No emulator or screenshot is attached because this slice changes no Android integration or visible
UI behavior. Device evidence belongs to the later tab-container integration child of #26.
