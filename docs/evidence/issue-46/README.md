# Issue #46 — versioned snapshot of the complete tab graph

## Scope

This pure Kotlin slice adds a primitive snapshot for `TabNavigationState`: selected tab, exact
ordered tab IDs, every independent `NavStateSnapshot`, and every exact entry ID. It reuses the
application `RouteCodec`, leaf validation, and whole-graph validation. A dedicated
`TabNavigationSnapshotResult`/`TabNavigationSnapshotProblem` keeps graph-only diagnostics out of
the sealed leaf snapshot API.

It does not add a transport envelope, `SavedStateHandle`, store ownership, Compose integration, or
device behavior. The successful round trip below is therefore not Activity recreation or Android
process-death evidence.

## TDD evidence

1. Kotlin wire-shape/round-trip/corruption contracts and a Java API smoke test were added first.
2. The observed RED run failed at test compilation because `TabNavigationStateSnapshot`,
   `toSnapshot`, static `restore`, `TabHistoryProblem`, and `InvalidTabNavigationState` did not
   exist.
3. The minimal production API made the first five contracts pass.
4. The matrix was expanded to 16 focused contracts covering application codecs, deterministic
   multi-failure ordering, nested leaf failures, graph corruption, phase precedence, deep
   defensive copies, transition exclusion, exceptions, and Java null returns.
5. External-consumer review found that adding tab variants to sealed leaf `SnapshotProblem` would
   break exhaustive leaf consumers. Tests were changed first and failed because the dedicated tab
   result/problem boundary did not exist; the refined API then restored GREEN.
6. A brittle private-field reflection assertion was removed after test review; exact wire shape and
   durable-state equality retain the meaningful contract.
7. The expanded focused suite and the forced full project gate passed.

## Verified contracts

- exact selected tab, tab order/IDs, leaf order/routes/arguments, and every `EntryId` round trip;
- one application-owned codec works for arbitrary routes across multiple arbitrary tabs;
- outer, leaf, and route payload versions are independent, with outer-version fail-fast;
- encode and restore are all-or-nothing and never generate replacement IDs or partial graphs;
- failures aggregate in deterministic tab-major/entry-major order;
- nested codec/version/state failures retain raw tab index/ID and complete leaf context;
- child restoration failures precede and suppress whole-graph validation;
- empty/blank/duplicate/missing/cross-tab corruption reuses existing typed validators;
- caller-owned tab, entry, and argument collections are deeply copied and runtime-unmodifiable;
- independently built equal snapshots compare structurally, while tab order remains significant;
- the snapshot surface contains no transition, animation, presentation, revision, label, or icon;
- Kotlin and Java use `state.toSnapshot(codec)` and static
  `TabNavigationState.restore(snapshot, codec)` without companion syntax;
- Java codec `null` results remain typed beneath tab and entry context.

## Final verification

```text
Focused tab snapshot API: 16/16

./gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL

JVM: 237 tests / 26 suites
Failures: 0
Errors: 0
Skipped: 0
```

Architecture, test, and external-consumer re-reviews reported no remaining P0, P1, or P2
findings after the result/problem boundary refinement.

No emulator or screenshot is attached because this slice has no Android integration or visible UI
behavior. Whole-graph lifecycle and device evidence belongs to the following children of #26.
