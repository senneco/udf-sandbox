# Issue #42 — immutable tab navigation graph model

## Scope

This slice adds only the pure Kotlin model for a stateful tab container. The existing single-stack
runtime, Compose renderer, persistence, animation, and visible demo UI are unchanged.

## TDD evidence

1. The initial focused run failed at test compilation because `TabId`, `TabState`,
   `TabNavigationState`, typed problems, and factories did not exist.
2. The minimal model made the original eight Kotlin contracts and one Java contract pass.
3. Consumer review exposed an awkward result-only happy path. Tests were changed first and failed
   against the old API (`fromTabs`, direct `create`, `history`, and indexed diagnostics absent).
4. The refined API and validation made the final focused suite pass: 10 Kotlin contracts and one
   Java contract, 11/11 green.

## Verified contracts

- explicit selected `TabId` and deterministic tab-bar order;
- independent exact `NavState` histories;
- defensive, runtime-unmodifiable collections;
- typed empty/blank/duplicate/missing-selection failures;
- graph-wide `EntryId` ownership with both conflicting tab positions;
- deterministic compound validation;
- structural equality including selection and tab order;
- direct `create` for known-valid application configuration;
- typed `fromTabs` for restoration and other caller-controlled data;
- Kotlin and Java consumption without demo-domain types in the core API.

## Final verification

```text
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL

JVM: 201 tests / 22 suites
Failures: 0
Errors: 0
Skipped: 0
```

Architecture, test, and external-consumer re-reviews reported no P0, P1, or P2 findings.

No device run or screenshot is attached because this slice changes no Android or visible UI
behavior. Device evidence belongs to the later Compose tab-container child of #26.
