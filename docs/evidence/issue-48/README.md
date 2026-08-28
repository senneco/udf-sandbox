# Issue #48 — one-key SavedStateHandle storage for the complete tab graph

## Scope

This slice adds an internal Android storage boundary for the complete `TabNavigationState` without
adding an observable state owner. A flat outer envelope stores selected tab and ordered tabs; each
tab contains a length-prefixed complete payload from the existing strict leaf envelope codec.

`SavedStateHandleTabNavigationStorage` accepts one application `RouteCodec`, reads and replaces one
tab-specific `ArrayList<String>`, returns typed save/restore outcomes, and clears only its own stale
key on rejection. It does not dispatch actions, publish frames, choose a fallback, migrate the
linear payload, or persist transition metadata.

## TDD evidence

1. Exact outer wire/round-trip and missing-storage contracts were added before production.
2. The observed RED run failed at test compilation because the tab envelope codec/decode results,
   storage adapter, and typed restoration results did not exist.
3. A minimal safe implementation made the first two contracts pass.
4. Tests were expanded to 13 focused contracts covering deterministic wire, outer and nested
   corruption, unsafe counts, version independence, injected application codec, copied-handle
   restoration, atomic replacement, stale cleanup, invalid graph snapshots, immutable problems,
   and separate linear/tab keys.
5. The expanded focused suite and the forced full project gate passed.

## Verified contracts

- one flat `ArrayList<String>` contains graph header and ordered length-prefixed leaf payloads;
- equal snapshots encode deterministically and sorted route arguments reuse the leaf contract;
- decoded snapshots do not alias the caller-owned payload;
- wrong types, magic, versions, counts, lengths, truncation, overflow, and trailing tokens are
  typed all-or-nothing outer failures;
- huge outer, leaf entry, and leaf argument counts are rejected before unsafe allocation;
- structurally bounded leaf failures aggregate tab-major with raw tab ID and leaf diagnostics;
- graph-envelope, graph-snapshot, leaf-envelope, leaf-snapshot, and route versions stay distinct;
- one injected codec saves and restores arbitrary application routes across multiple tabs;
- a copied primitive payload in a fresh handle restores selected/order/histories/routes/exact IDs;
- a second save replaces the whole payload with a new owned `ArrayList` and no partial graph;
- save snapshot failure and restore rejection clear only the tab key and preserve unrelated keys;
- unsupported graph snapshot, bad route, and cross-tab ID collision reject the complete graph;
- adapter tests preserve the exact tab, entry, route, and cross-tab collision diagnostics;
- `Missing` is distinct from `Rejected`, and storage never writes or chooses fallback on missing;
- `SavedStateAccess` identifies only handle reads/writes; defensive codec-stage exceptions use a
  separate `Unexpected` problem so callers are not sent to the wrong subsystem;
- linear and tab storage keep separate keys and magic without changing either existing contract;
- returned save, restore, and envelope problem lists are runtime-unmodifiable.

## Final verification

```text
Focused tab envelope/storage API: 13/13

./gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL

JVM: 250 tests / 28 suites
Failures: 0
Errors: 0
Skipped: 0
```

No emulator or screenshot is attached because this slice changes no UI and adds no Activity or
ViewModel owner. A copied `SavedStateHandle` primitive proves the storage boundary only; lifecycle
recreation, fallback/rewrite, revision reset, and transition reset belong to the next child of #26.

Architecture review found no open P0–P2 issues. Test and external-consumer reviews each found one
P2: exact adapter diagnostics were initially asserted only by type, and non-handle exceptions were
mislabelled as saved-state access failures. Both findings were fixed before the final gate and sent
back for focused re-review; both re-reviews returned clean with no open P0–P2 issues.
