# Final implementation: `call-budget` (Issue #67)

The as-built record of `SpartanLaboratories/WebTools#67`, unit 8 (`call-budget`) of [#55's architecture](../architecture.md). It states what was built,
where the work departs from [`plan.md`](plan.md) and why, and the measurements that show it holds. What the unit changes for the repository, its published
artifacts and the sibling units, and the known gaps and follow-ups it leaves, are in [`consequences.md`](consequences.md).

- **Status:** built through stage 5 of [`plan.md`](plan.md) §5, with the deviations of §2. Procedures P1 to P4 of plan §9.2 ran (§4); the Level 5 pass of P5
  is §5.
- **Branch and commits:** `feat/issue-67-call-budget`, cut from `master` at `8122af7`, with the research commits `1ec2d92`, `f956731`, `8f5d057` and
  `3091e92` and the code commits `ff55cb8` (stage 1, with this folder's `plan.md` and `consequences.md`), `ec776c7` (stage 2), `c0a1c57` (stage 3),
  `64a9a03` (stage 4) and `327164f` (stage 5), commits 1 to 5 of plan §11; `327164f` is the code-landing commit. The `README.md` and `CHANGELOG.md` delta of
  plan §8, the KDoc and comment wording of the documentation review (§5) and the reconciled documents are committed after it.
- **Architecture:** [`../architecture.md`](../architecture.md) carries dated corrections (2026-11-05) for this unit at §6.5, in §7.4's row for the loader's
  call to `CallBudget` and at the two points of §13 this unit settles; [`consequences.md`](consequences.md) §5 states each refinement.
- **Host of the measurements:** Windows 11, JDK 23.0.2, Gradle 9.7.1, Kotlin 2.2.0, kotlinx-coroutines 1.10.2, 24 cores (32 logical processors). Linux and
  macOS were not exercised.
- **Versions moved:** none. `webtools-http` 1.0.0 stays unreleased until #55's stack is complete, and this landing is not a release candidate.

## 1. What was built

Everything plan §7 lists, in the plan's shape, with the deviations of §2. Paths are under `webtools-http/src/`.

- **The public surface** (package `com.spartanlabs.webtools.http`, all of it in `commonMain`):
  - the port `CallBudget : AutoCloseable` with the abstract suspend `acquire(cost, lane, maxWait): Result<Permit>` and `utilisation(): BudgetUtilisation`, a
    `flush()` whose body succeeds, an empty `close()` and its nested `public object Unlimited`; `Permit` (`cost`, `lane`, a `complete()` whose body calls
    `complete(RateSignals.None)`, `complete(signals)` and `release()`); and the plain suspend extension `withPermit(cost, lane, maxWait, block)`;
  - the vocabulary `Lane` (with `Interactive` and `Tracker`), `RateWindow` with `RateWindow.Builder` and the factory `RateWindow(name, published, period) { }`,
    `RateSignals` with `RateSignals.Builder`, the factory `RateSignals { }` and `RateSignals.None`, `WindowReading`, `BudgetUtilisation` and
    `WindowUtilisation`, and `RetryAfter` with its two `parse` overloads;
  - the seams `RateSignalsReader` with `Standard`, `BudgetStore` with `InMemoryBudgetStore`, `BudgetSnapshot` (two constructors, `Empty`, `CURRENT_VERSION`
    1) and `WindowSnapshot`, `BudgetObserver` with `None`, the open root `BudgetEvent` with its seven final leaves (`Granted`, `Refused`, `Paused`, `HardStop`,
    `Reconciled`, `StoreFailed`, `Restored`), and the open constants classes `PauseSource` (`RetryAfter`, `ResetIn`, `Boundary`, `Snapshot`) and
    `StoreOperation` (`Load`, `Save`);
  - the final class `WindowedCallBudget` over an `internal` constructor, with `WindowedCallBudget.Builder` (the twelve properties of plan R8), `maxBurst` and
    the factory `WindowedCallBudget(windows) { }`.

  Implementing `CallBudget`, `Permit`, `RateSignalsReader`, `BudgetStore` or `BudgetObserver`, or subclassing `BudgetEvent`, needs the opt-in; everything
  else is Stable Core and usable without it. The ABI dump `webtools-http/api/webtools-http.api` grows from 557 to 892 lines by exactly the classes of plan
  §7.23 (§4, P2).
- **The parts** (`internal`, `commonMain`): `HttpDate.kt` (the HTTP-date parser, `HttpDateProblem`, `HttpDateParseException`, `httpDateFailure`);
  `LedgerTime.kt` (`LedgerNow`, `LedgerClock`, `Pause`, `ceilToMillis`); `LedgerCell.kt` and `WindowLedger.kt` (the cells, the window arithmetic, the fold and
  the cell cap); `BudgetLedger.kt` (the slot search, the shares, reservations and grants, signals and pauses, the restore and the snapshots, with D7);
  `BudgetStoreFailure.kt`; and the message and limit constants.
- **The fixtures** (`jvmTestFixtures`, package `com.spartanlabs.webtools.http.testing`): `RecordingBudgetObserver` and `FakeBudgetStore`, each with its own
  `@OptIn`, with D8's check on `times`; no new fixtures dependency.
- **Changed:** the KDoc lists of `ExperimentalForInheritanceWebToolsApi.kt` (gains the five gated ports and `BudgetEvent`, and `CallBudget.Unlimited` and
  `WindowedCallBudget` among the supplied implementations) and `WebToolsDsl.kt` (gains the three builders); `WebToolsDslComponentTest`, whose list gains the
  three builders; the KDoc of `VirtualClockThreeClocksComponentTest`; `FixturesConsumerGatingTest` (D9); the dump.
- **Files:** 81 new under `webtools-http/` (26 in `commonMain`, 2 in `jvmTestFixtures`, 18 test classes and 1 support file in `commonTest`, and 29 test
  classes and 5 support files in `jvmTest`, the support of plan §7.20); 6 changed (the dump, the two KDoc lists and the three tests above).
- **Unchanged:** `webtools-http/build.gradle.kts` (`kotlinx-coroutines-core` 1.10.2 was already the `commonMain` `api` dependency), `webtools-http-commoncheck`
  (no new `expect`), `webtools-udp`, `webtools-scraping`, `webtools-browser`, `build-logic` and the root build files: `git diff --stat 8122af7..327164f` is
  empty for each of them.

**The tests** are in the level packages of plan §9, with the additions of D9. Level 4b has none, as plan §9 says. The baseline is `8122af7`, as #66's run of
record measured it; the final counts are the final run of record (§4), each one checked against the result XML:

| Level | Classes, baseline → final | Tests, baseline → final |
|---|---|---|
| Level 1, gating | 9 → 11 | 23 → 32 |
| Level 2, component | 81 → 112 | 519 → 748 |
| Level 3, integration | 25 → 26 | 98 → 104 |
| Level 4a, deterministic | 46 → 56 | 214 → 293 |
| Level 4c, non-functional | 19 → 22 | 35 → 38 |

Of the 47 new test classes, 18 are in `commonTest` (8 Level 2 classes for the value types and the reader, 9 Level 4a classes for the parser and the ledger, and
`CallBudgetConsumerGatingTest`) and 29 in `jvmTest`. The eighteen `WindowedCallBudget*ComponentTest` classes hold 131 tests.

## 2. Deviations

Each entry names what differs from the plan, where, and why; it cites the plan section it departs from and links secondarily to what that section was based
on. D7 changes one production rule, the content of the exact snapshot `flush()` writes, and no public signature or line of the ABI dump; D8 adds an argument
check to a published fixture; the other seven change tests only. D7 comes from the code review, D8 from the test review, and D9 from both reviews.

### D1. `PauseDeterministicTest` steps a time source of its own

- **Plan:** §9, Level 4a, `PauseDeterministicTest`: "`LedgerClock` keeps the elapsed reading non-decreasing over a `ManualTimeSource` that steps back",
  with §9's rule that pure arithmetic tests live in `commonTest`.
- **As built:** the class is in `commonTest`, as the table places it, and steps a private `SteppingTimeSource` (a settable `now`; a mark reads `now` minus
  its value at the mark, the shape of `ManualTimeSource`) beside a private `WallClock` of its own. The behaviour the row locks down, a non-decreasing elapsed
  reading over a source that steps back, is tested as written.
- **Why:** `ManualTimeSource` is `jvmTest` support (`jvmTest/kotlin/com/spartanlabs/testing/support/webtools/http/ManualTimeSource.kt`), which `commonTest`
  cannot see. Moving the class to `jvmTest` would break the placement rule, and a copy of the support in `commonTest` would duplicate a name that three
  `jvmTest` classes use.
- **Based on:** §6.2 (an injected `TimeSource` need not be monotonic), from [`research/ledger-time.md`](research/ledger-time.md) F3 (Kotlin's `TimeSource`
  promises monotonicity only for `Monotonic`).

### D2. `BudgetLedgerPropertyDeterministicTest` runs a fixed 3,000 steps per property

- **Plan:** §9, Level 4a, `BudgetLedgerPropertyDeterministicTest`: "3,000 steps per property; `WEBTOOLS_PROPERTY_STEPS` raises the count".
- **As built:** the count is the constant 3,000 and no environment variable is read; the seed, `Random(7)`, and the two properties are the plan's.
- **Why:** the class is in `commonTest`, as the table places it, and common Kotlin has no environment access (`System.getenv` is JVM API). The committed run
  is the one the plan specifies; only the knob to lengthen it is missing, and a JVM-side knob would need a `jvmTest` wrapper of the properties or an
  `expect`/`actual` in test code, neither of which the plan provides.
- **Based on:** §9's conventions (everything on the ledger in `commonTest`; the knob modelled on the JVM differentials' `WEBTOOLS_DIFFERENTIAL_INPUTS`) and
  [`research/README.md`](research/README.md), "The work" (a seeded property test at Level 4a).

### D3. `WindowedCallBudgetCloseComponentTest` covers a call that has not started in place of a waiter "between its section and its wait"

- **Plan:** §9, Level 2, `WindowedCallBudgetCloseComponentTest`: "**a waiter between its section and its wait** also" returns `Closed` once the lock frees.
- **As built:** no case puts a waiter between the end of its lock section and the start of its `select`. In its place the class covers a call whose
  coroutine has not run when `close()` happens (`a call whose coroutine has not run when close happens returns Closed`), beside the parked-on-the-lock and
  sleeping-waiter cases the row also names.
- **Why:** on the virtual scheduler a waiter leaves its section and enters `withTimeoutOrNull { select { … } }` with no suspension point between the two,
  so no test can interleave `close()` there. Plan §3, Assumption 4, records that window as reachable by no deterministic seam and gives it to the Level 4c
  close-race class; the sleeping-waiter case proves the mechanism the row relies on, `Job.onJoin` selecting for a completed latch. The window stays open as a
  known gap ([`consequences.md`](consequences.md) §8).
- **Based on:** §6.5 (the close latch and the waiter's `select`), from [`research/waits-and-close.md`](research/waits-and-close.md) F2 and F3 (`Job.onJoin`
  "never fails" and selects at once for a completed job).

### D4. `WindowedCallBudgetReplanCostNonFunctionalTest` does not check that the planned instants stay in lane order

- **Plan:** §9, Level 4c, `WindowedCallBudgetReplanCostNonFunctionalTest`: "after the rounds the queue's instants are still in lane order and no waiter is
  lost".
- **As built:** the class checks that no waiter is lost (each cancelled call ended with its own cancellation; every other call still waits or holds a permit
  granted at a boundary; `utilisation().waiting` equals the number still running), not the order of the planned instants. To reach 1,000 waiters on each of
  two lanes and "the head waiter", it first fills the 10-per-minute window (8 `Tracker` and 2 `Interactive` grants, the split the default shares impose) and
  launches the waiters one at a time on a single-threaded dispatcher, so arrival order is launch order and round *k* cancels the earliest arrival still
  waiting. It prints the per-round times (P4, §4).
- **Why:** a waiter's planned instant is not observable: `WindowedCallBudget.Waiter.plan` is private, `utilisation()` reports counts only, and on real time
  the instants lie up to 200 minutes ahead, beyond any bound for observing them as grants. The order itself is held on virtual time by the Level 2 FIFO and
  refund classes and by the ledger's Level 4a tables (planned targets never decrease within a lane). No seam is added for it; the omission is a known gap
  ([`consequences.md`](consequences.md) §8).
- **Based on:** §6.5 (re-planning in arrival order from each lane's previous slot) and §3, Assumption 2, from the stack-level
  [`../research/call-budget.md`](../research/call-budget.md) probe (1.2 to 1.7 ms per cancel at 3,000 waiters).

### D5. `WindowedCallBudgetCloseRaceNonFunctionalTest` admits a permit granted before the close

- **Plan:** §9, Level 4c, `WindowedCallBudgetCloseRaceNonFunctionalTest`: "every call ends within a 30-second bound with `Closed("budget")` or its own
  `CancellationException`, never anything else".
- **As built:** a round's calls may also end with a permit, at most one per minute period the round touched: the first call to fit the 1-per-minute window
  is granted before `close()` can refuse it (the row's own "all but one wait"), and a round that straddles a minute boundary on the production clock may grant
  one more at the boundary. The window is built with `laneShares = emptyMap()`, or every `Tracker` call would be refused with `CostExceedsLimit` rather than
  wait. "Its own `CancellationException`" also covers a call cancelled before its coroutine first ran, which records nothing; the class accepts that outcome
  only for a call in the cancelled set whose job is cancelled.
- **Why:** the row's letter leaves no outcome for the grant its own setup produces; bounding the grants per period keeps the invariant it means, that nothing
  the latch should have refused is granted or waits after the close.
- **Based on:** §6.5 (the close latch; a call parked on the lock sees it in its first check), §6.3 (a window of limit 1 wants `laneShares = emptyMap()`) and
  §3, Assumption 4, from [`research/waits-and-close.md`](research/waits-and-close.md) F3.

### D6. `WindowedCallBudgetContentionNonFunctionalTest` reads its bounds in the ledger's own terms

- **Plan:** §9, Level 4c, `WindowedCallBudgetContentionNonFunctionalTest`: "the grants never exceed `limit × (whole seconds spanned + 1)`", "the ledger and
  queue are empty afterwards".
- **As built:** the grant bound counts the whole-second periods the run touched, `end / 1000 − start / 1000 + 1` from the first call to the last
  completion, which is what a per-period limit allows (a 0.2 s run that straddles a boundary may grant two periods' worth). "The ledger is empty" is read as
  no planned units in any window and `waiting == 0`, since the current second's `used` is non-zero right after the last grants. The window has
  `laneShares = emptyMap()`, so that `limit` is its plain 20 (the row names no lanes). The cancelled set and its offsets of up to 50 ms are drawn from
  `Random(67)`.
- **Why:** the other readings would fail a correct budget or could not hold at all.
- **Based on:** §6.3 (one cell per period; the guard's two-cell charge) and §6.4 (the grant), from the stack-level
  [`../research/call-budget.md`](../research/call-budget.md) F1 to F4.

### D7. `BudgetLedger.exactSnapshot` writes a row of zero for a hard cell whose persisted mark is above its count

- **Plan:** §7.14, `BudgetLedger.exactSnapshot` ("the exact effective count of every hard window cell at or above the current index"), whose listing writes a
  `WindowSnapshot` only for a cell with `effective > 0`.
- **As built:** `exactSnapshot` also writes a cell whose `effective` is 0 when its `persisted` mark is above 0, as `WindowSnapshot(window, start, 0)`; the
  unchanged `markPersisted` then lowers the mark to 0 through that row. A cell with neither a count nor a mark is still omitted. No public signature and no
  line of the dump changes (`WindowSnapshot.used` already admits 0).
- **Why:** the code review found that the plan's listing breaks §6.7's rule that "the persisted number is at least the true usage at every grant that made a
  save", so that a crash never under-counts. On a hard day window of 30 with `persistAhead` 10, one grant writes ahead `{day: 10}` and sets the mark to 10;
  `release()` takes `effective` to 0; `flush()` then omitted the cell, so the store held no day row while the mark stayed 10; the next nine grants found
  `effective + cost <= persisted` and saved nothing, and a crash restored 0 for a true usage of 9, an under-count of up to `persistAhead − 1` on the window
  that guards a bill. The zero row makes the stored snapshot state the truth and lets the existing `markPersisted` lower the mark, so the next grant is
  written ahead again; it keeps one code path and meets §6.7's own description of `flush()` ("`effective` of every hard window cell at or above the current
  index"). Resetting the omitted cells' marks inside `markPersisted` or `writeExact` instead would have left the store and the ledger disagreeing about which
  cells exist.
- **Locked down by:** `WindowedCallBudgetCrashComponentTest`, `a flush after every grant was released resets the persisted mark and a crash after it never
  under-counts` (a write-ahead grant, `release()`, a `flush()` that writes 0, nine grants of which the first is written ahead again, and a second budget over
  the same store that restores at least 9 and at most 18), and `BudgetLedgerSnapshotDeterministicTest`, `exactSnapshot writes a zero row for a cell whose
  persisted mark is above its count and markPersisted lowers the mark`. Both failed on the plan's listing at the step where the flush writes no row, and pass
  with the change.
- **Based on:** §6.7 (the write-ahead invariant) and the parent architecture's [§6.5 Call budget](../architecture.md#65-call-budget) ("persisted
  write-ahead in blocks of `persistAhead`, so a crash never under-counts"), from [`research/README.md`](research/README.md), question 1's requirements (a
  `hard` window's count is saved before the grant that would exceed the persisted mark) and [`research/waits-and-close.md`](research/waits-and-close.md)
  section 6 (the persisted mark advances only on a successful save).

### D8. `FakeBudgetStore.failLoads` and `failSaves` refuse a `times` below 1

- **Plan:** §7.17, the `FakeBudgetStore` listing (`failLoads(cause, thrown = false, times = Int.MAX_VALUE)` and `failSaves(…)` arm a
  `Failure(cause, thrown, times)` with no check on `times`), with R16.
- **As built:** both begin with `require(times >= 1) { TIMES_MESSAGE }` (`times must be at least 1`, a constant message, the module's rule for programmer
  errors), and their KDoc gains the `@throws IllegalArgumentException` line; `Int.MAX_VALUE` keeps its meaning of every call. Nothing else in the fixture
  changes; the fixtures are not in the ABI dump.
- **Why:** the test review found that `times = 0` delivered one failure instead of none: `consume` decrements first (`--remaining <= 0` is true at −1) and
  disarms the script, but still returns the armed failure for the current call. A script of zero failures is not a meaningful request (it is `heal()`), so the
  fixture refuses it rather than deliver one; a negative `times` is refused the same way.
- **Locked down by:** `FakeBudgetStoreComponentTest`, `a times below 1 is refused and times 1 fails exactly once` (0 and −1 refused on both methods with the
  message; a refused script arms nothing; `times = 1` fails exactly one load and one save and then heals).
- **Based on:** §7.17 (the fixtures are the worked examples of their seams and compile under `-Xexplicit-api=strict`, so their contract is stated rather
  than implied) and the parent architecture's [§6.7 Published test support](../architecture.md#67-published-test-support).

### D9. Five test classes lock down more than plan §9's tables, one of them a class the plan leaves unchanged

- **Plan:** §9's Level 2 rows for `WindowedCallBudgetRefundComponentTest`, `WindowedCallBudgetStoreFailureComponentTest`, `WindowedCallBudgetCloseComponentTest`
  and `WindowedCallBudgetRestoreComponentTest`; §7.19 and §11, which name `WebToolsDslComponentTest` and `VirtualClockThreeClocksComponentTest` as the only
  existing tests that change.
- **As built,** with no production code changed, eight cases beyond the tables:
  - `WindowedCallBudgetRefundComponentTest`: `a re-plan one millisecond past a waiter's deadline keeps it and two milliseconds past fails it at once` (§6.5's
    tolerance of 1 ms);
  - `WindowedCallBudgetStoreFailureComponentTest`: `a save that outlasts the period is written again for the cell then current at most three times and the
    grant proceeds` (§6.4's three attempts: on a one-second hard window whose saves each take a second, three saves, one per period, then the grant in the
    fourth period without a save of its own) and `a store that throws an Error propagates it and leaves the budget usable` (§6.7: from `save` and from `load`,
    the call throws the store's `Error`, no `StoreFailed` is raised, nothing is charged, and the next call is granted);
  - `WindowedCallBudgetCloseComponentTest`: `a waiter whose write-ahead save spans a close returns Closed once the save ends` (the latch re-check after the
    save in `stepLocked`) and `a call whose write-ahead save spans a close is still granted` (`admitLocked` re-checks the latch only at the top of its section,
    as §7.16's listing has it);
  - `WindowedCallBudgetRestoreComponentTest`: `a retried restore that merges counts re-plans the waiters` (§6.7: a restore that merges something re-plans
    every waiter);
  - `FixturesConsumerGatingTest` (`jvmTest`, Level 1): `the fake store is a BudgetStore whose load is Empty` and `the recording observer is a BudgetObserver
    that records one onEvent`, so a consumer without the opt-in uses each fixture as its gated port.
- **Why:** the reviews found rules of §6.4, §6.5, §6.7 and §7.16, and a use of the fixtures (R16), that no listed case reached; the tables of §9 are the
  minimum each class locks down. The `Error` case asserts the identity of what the call threw up to a copy, for the reason recorded in §4.
- **Based on:** §6.4, §6.5 and §6.7, from [`research/waits-and-close.md`](research/waits-and-close.md) F3 (the close latch) and F9 (the bounded retry of a
  failed restore), and, for the fixtures, the parent architecture's [§6.7 Published test support](../architecture.md#67-published-test-support).

## 3. Acceptance

The plan's acceptance items (§1), with the runs that show them (§4):

1. **Met.** A consumer without the opt-in compiles against the surface: `CallBudgetConsumerGatingTest` (`commonTest`: the five gated ports in signatures,
   the supplied objects as defaults, every value type and event leaf built, a `when` over `BudgetEvent` that keeps an `else`, `WindowedCallBudget(windows) { }`
   used as a `CallBudget`, `RetryAfter.parse` called) and `WindowedCallBudgetConsumerGatingTest` (`jvmTest`: `WindowedCallBudget(windows) { }` on production
   defaults and `CallBudget.Unlimited`, each through `acquire`, `withPermit`, `complete`, `utilisation()`, `flush()` and `close()`), with the two fixture cases
   of `FixturesConsumerGatingTest` (D9).
2. **Met.** An implementer needs the opt-in: P1, twelve compile errors with the marker's message without `@OptIn`, one per declaration per source set, and a
   clean compile with it.
3. **Met,** with D3. Every point of R2 and R9 to R14 holds for `WindowedCallBudget` on `VirtualClock`: the eighteen `WindowedCallBudget*ComponentTest`
   classes (131 tests), among them the exact grant instants after a cancellation (`…RefundComponentTest`), the pauses from each hint (`…PauseComponentTest`),
   the hard stop (`…HardWindowComponentTest`), the restore rules (`…RestoreComponentTest`), the write-ahead after a simulated crash (`…CrashComponentTest`),
   `complete` from a cancelled coroutine (`…CancellationComponentTest`), the clock-step cases (`…ClockStepComponentTest`, `…ClockPauseComponentTest`) and the
   three clocks together (`…ThreeClocksComponentTest`); with `WithPermitComponentTest` (10), `CallBudgetUnlimitedComponentTest` (7),
   `InMemoryBudgetStoreComponentTest` (4), the two fixtures' classes (19) and the eight `commonTest` classes of the value types and the reader (58). The one
   case of the tables that no deterministic test reaches, a waiter between its section and its wait, is D3; what the requirements leave open is in
   [`consequences.md`](consequences.md) §8.
4. **Met,** with D1 and D2. The HTTP-date table and the `java.time` differential agree (`HttpDateDeterministicTest`, `RetryAfterDeterministicTest` and
   `HttpDateJavaTimeDifferentialDeterministicTest`, 200,000 iterations per part at seed 7 on the oracle base the plan specifies); the window arithmetic, the
   guard's two-cell charge, the share rounding and the fold hold (`WindowArithmeticDeterministicTest`, `ShareRoundingDeterministicTest`,
   `CellFoldDeterministicTest`, `PauseDeterministicTest`, `BudgetLedgerPlanDeterministicTest`, `BudgetLedgerSnapshotDeterministicTest`); and the seeded
   property test finds no provider-side overspend and no write-ahead under-count (`BudgetLedgerPropertyDeterministicTest`). Ten classes, 79 tests.
5. **Met,** with D4, D5 and D6. `RateSignalsReaderLoopbackIntegrationTest` (6 tests) reads, from the real transport over loopback, a `503` with
   `Retry-After`, a `429` with and without it, a returned `300`, a date-valued `Retry-After` measured against the response's `Date`, and two `Retry-After`
   lines. `WindowedCallBudgetCloseRaceNonFunctionalTest` ends every call of its 20 rounds with a permit granted before the close (D5), `Closed("budget")` or its
   own cancellation, never a hang; P3 shows that it does not detect the loss of the admission's latch re-check, which the Level 2 close class does detect
   ([`consequences.md`](consequences.md) §8). `WindowedCallBudgetReplanCostNonFunctionalTest` keeps every round of a re-plan at 1,000 waiters per lane an order
   of magnitude inside its bounds and loses no waiter (P4), without checking the instants' lane order (D4). `WindowedCallBudgetContentionNonFunctionalTest`
   never grants more than the limit per touched period and grants every uncancelled call (D6).
6. **Met.** `:webtools-http:check` passed on the code-landing tree, `327164f`, on 2026-11-05 (exit 0): `jvmTest` with 1,215 tests in 227 classes, none
   failed, errored or skipped, `apiCheck` against the regenerated dump, `verifyJavaFloor` and the fixtures-publication check (§4). It had passed once before,
   at the end of stage 5. `:webtools-http-commoncheck:build` passed with the unit's common code in place (P2) and again after the code review's change to the
   ledger. No other module's source, build file or dependency changed (§1).

## 4. Measurements

Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other
module's suite.

- **P1, the gate (after stage 4).** Two scratch files, `webtools-http/src/jvmTest/kotlin/P1JvmScratch.kt` and
  `webtools-http/src/commonTest/kotlin/P1CommonScratch.kt`, each holding six top-level classes with every abstract member overridden: an implementer of
  `CallBudget`, of `Permit`, of `RateSignalsReader`, of `BudgetStore` and of `BudgetObserver`, and a subclass of `BudgetEvent`.
  - Without the opt-in, `:webtools-http:compileTestKotlinJvm` failed with 12 errors, one per declaration per source set, each reading *This class or
    interface requires opt-in to be implemented: Implementing or substituting this WebTools seam is Experimental and may change in a minor release. …* (the
    marker's message).
  - With `@OptIn(ExperimentalForInheritanceWebToolsApi::class)` on each class, the same task compiled cleanly.
  - Both scratch files were deleted afterwards; neither is committed.
- **P2, the `js` compile and the dump.** `:webtools-http-commoncheck:build` passed with `WindowedCallBudget.kt` in `commonMain`: `select` over `Job.onJoin` and
  `ReceiveChannel.onReceive`, `Mutex`, `Channel(CONFLATED)`, `withTimeoutOrNull` and `NonCancellable` all compile for `js`. `apiDump` after each code stage
  grew the dump from 557 to 892 lines, by exactly the classes of plan §7.23: stage 1 by 257 lines (the vocabulary, the seams, `RetryAfter`, the events and the
  two constants classes), stage 2 by 37 (`CallBudget` with `DefaultImpls` and `Unlimited`, `Permit` with `DefaultImpls`, `WithPermitKt`) and stage 4 by 41
  (`WindowedCallBudget`, `WindowedCallBudget$Builder`, `WindowedCallBudgetKt`). No `internal` declaration, ledger type or `BudgetStoreFailure` is in it, and
  `apiCheck` passed.
- **Stage 5.** `:webtools-http:integrationTest --rerun :webtools-http:nonfunctionalTest --rerun` passed: integration 26 classes, with
  `RateSignalsReaderLoopbackIntegrationTest`'s 6 tests; non-functional 22 classes, the three budget classes 1 test each; none failed, and
  `UrlParseLinearTimeNonFunctionalTest` ([WebTools#87](https://github.com/SpartanLaboratories/WebTools/issues/87)'s flake) passed. `:webtools-http:check` then
  passed once (20 actionable tasks, 5 executed): `apiCheck` passed, `verifyJavaFloor` and the fixtures-publication check were up to date from stage 4 (their
  inputs unchanged), and `jvmTest` ran 1,204 tests in 227 classes, none failed.
- **P3, the close-race mutation (stage 5).** `WindowedCallBudget.kt` was copied aside and the re-check at the top of `admitLocked`,
  `if (closing.isCompleted) return Admission.Settled(closedFailure())`, removed.
  - `WindowedCallBudgetCloseRaceNonFunctionalTest`, run five times (`:webtools-http:nonfunctionalTest --rerun --tests '*WindowedCallBudgetCloseRace*'`),
    **passed in 5 of 5 runs**, all 20 rounds green each time: it does not detect the mutation. With the re-check gone, a call parked on the lock when
    `close()` runs is admitted and, on the 1-per-minute window whose unit is already taken, queued; its first step section then finds the latch and returns
    `Closed("budget")` all the same. The mutation changes the outcome only for a call that would be granted at once after the close, which that window
    never offers once its first call is granted.
  - `WindowedCallBudgetCloseComponentTest`, run once with the same mutation after the code review (`:webtools-http:componentTest --rerun --tests
    '*WindowedCallBudgetCloseComponentTest*'`), **detects it**: 1 of its then 7 tests failed, `a call parked on the lock when close runs returns Closed once
    the lock frees` (*Expected value to be of type HttpFailure$Closed, actual <null>*: the parked call was granted). Its window, 10 per minute with one unit
    taken, has room, so without the re-check the parked call is granted at once after `close()`, which is exactly the outcome the mutation changes.
  - Each time the file was restored from the copy and verified identical with `cmp`, and the copy deleted. [`consequences.md`](consequences.md) §8 carries
    what the outcome leaves open.
- **P4, the re-plan numbers (stage 5).** `WindowedCallBudgetReplanCostNonFunctionalTest`, three times: 1,000 waiters per lane on a 10-per-minute window, 20
  rounds each cancelling the head waiter (bounds: 2 s per round, 250 ms on average):

  | Run | Per-round times (ms, in order) | Mean | Max |
  |---|---|---|---|
  | 1 | 29.8, 25.0, 15.1, 16.1, 17.8, 14.3, 12.7, 13.5, 18.2, 13.9, 13.2, 10.9, 14.0, 9.6, 11.8, 11.8, 12.1, 10.8, 10.0, 12.6 | 14.66 ms | 29.8 ms |
  | 2 | 4.0, 17.2, 19.0, 18.7, 15.5, 14.9, 15.0, 13.7, 16.0, 14.1, 14.2, 14.4, 14.1, 16.0, 14.3, 11.5, 13.6, 15.0, 14.2, 13.7 | 14.45 ms | 19.0 ms |
  | 3 | 3.6, 23.6, 13.8, 37.1, 19.2, 16.2, 16.0, 12.3, 14.2, 17.7, 14.9, 13.2, 14.6, 14.5, 14.8, 14.5, 15.6, 15.5, 16.5, 13.8 | 16.07 ms | 37.1 ms |

  A round covers the cancellation's dispatch, the head's withdrawal under the lock (its unlink and the re-plan of the 1,999 waiters behind it, each from its
  lane's previous slot) and the first poll of `utilisation()` that sees the count fall; the wakes of the waiters moved earlier run on afterwards. It measures
  more than the stack-level probe's 1.2 to 1.7 ms per cancel at 3,000 waiters, which timed the re-plan alone. Every round sits an order of magnitude inside
  its bound, so the lane-first optimisation of plan §3, Assumption 2, is not built. Queueing the 2,000 waiters took about 170 ms. In the same runs
  `WindowedCallBudgetContentionNonFunctionalTest` granted 302, 306 and 303 of its 400 calls over 16 whole-second periods (bound 320) in 15.1, 14.2 and 14.7 s.
- **After the code review (D7; four cases, D7's two among them).** `:webtools-http:componentTest --rerun :webtools-http:deterministicTest --rerun
  :webtools-http-commoncheck:build` passed: component 743 tests in 112 classes, deterministic 293 in 56, none failed, errored or skipped, and the check module
  builds. The amended classes ran their full counts: `WindowedCallBudgetCrashComponentTest` 4, `WindowedCallBudgetStoreFailureComponentTest` 7,
  `WindowedCallBudgetRefundComponentTest` 9 and `BudgetLedgerSnapshotDeterministicTest` 10, with `WindowedCallBudgetCloseComponentTest` 7, unchanged.
  `apiDump` then `apiCheck` passed, the dump unchanged (`BudgetLedger` is `internal`). A fresh run of the five level tasks then gave gating 30 tests,
  component 743, integration 104, deterministic 293 and non-functional 38: 1,208, none failed or skipped.
- **After the test review (D8; seven cases, D8's among them).** `:webtools-http:gatingTest --rerun :webtools-http:componentTest --rerun` passed: gating 32 tests in 11
  classes, component 748 in 112, none failed, errored or skipped. The amended classes ran their full counts: `FixturesConsumerGatingTest` 6,
  `FakeBudgetStoreComponentTest` 11, `WindowedCallBudgetCloseComponentTest` 9, `WindowedCallBudgetStoreFailureComponentTest` 8 and
  `WindowedCallBudgetRestoreComponentTest` 12. `apiDump` then `apiCheck` passed with the dump's SHA-1 (`03d0749a…`) unchanged: the fixtures are not in it, and
  nothing in `commonMain` changed.
  - The `Error` case shows that an `Error` a store throws reaches the caller of `acquire` through `withTimeoutOrNull`, and with assertions enabled (the test
    JVM's default) kotlinx.coroutines' stack-trace recovery re-creates it, so the caller receives a copy whose `cause` is the store's instance, not the instance
    itself. `CaffeineResponseCache` rethrows a loader's `Error` as the original, kept in a field. The case asserts the identity up to that copy; the behaviour is
    plan §6.7's ("an `Error` propagates"), and whether the budget should keep the instance is a follow-up ([`consequences.md`](consequences.md) §7).
- **The final run of record:** `:webtools-http:check` on the code-landing tree, `327164f`, as committed, before any edit of the documentation review; it
  ended at 2026-11-05T22:46:02Z, exit 0, with no warning printed.
  - `jvmTest` ran 1,215 tests in 227 classes, none failed, errored or skipped; by the result XML, gating 11 classes / 32 tests, component 112 / 748,
    integration 26 / 104, deterministic 56 / 293 and non-functional 22 / 38. WebTools#87's flake did not show.
  - `verifyJavaFloor` reported "java floor 11 (class-file major <= 55): 380 classes in 2 jar(s), 6 library variants in 2 metadata file(s), 3967 classes on
    the runtime classpath"; `apiCheck` passed; the fixtures-publication check of `build-logic` passed its 10 tests, with this unit's fixtures, D8 included.
  - In this run the re-plan rounds took 9.85 ms on average and 13.5 ms at most, and the contention class granted 304 of its 400 calls over 16 whole-second
    periods (bound 320) in 14.6 s.

## 5. Level 5 pass

Plan §9 (Level 4b and Level 5) and P5, read after the code review: the KDoc of every new public type in `commonMain` (`Lane`, `RateWindow` with its builder
and factory, `WindowReading`, `RateSignals` with its builder, factory and `None`, `PauseSource`, `StoreOperation`, `RetryAfter`, `RateSignalsReader` and
`Standard`, `BudgetSnapshot`, `WindowSnapshot`, `BudgetStore`, `InMemoryBudgetStore`, `BudgetObserver` and `None`, `BudgetEvent` and its seven leaves,
`BudgetUtilisation`, `WindowUtilisation`, `CallBudget` and `Unlimited`, `Permit`, `withPermit`, `WindowedCallBudget` with its builder and factory), the two
fixtures and the two extended lists, read from the source as the hover text a consumer sees, and the added section of the dump, read as the dump a consumer
diffs.

- **Tier.** Every type names its tier in its first KDoc paragraph. The six gated types say **Experimental (implement)** or **(subclass)**, name the `@OptIn`
  line, and say that calling, passing and matching are Stable Core; each supplied implementation (`Unlimited`, `Standard`, `InMemoryBudgetStore`,
  `BudgetObserver.None`, `WindowedCallBudget`) says it needs no opt-in. The marker's list and the `@WebToolsDsl` list name every new gated type and builder,
  consistent with plan §8's table.
- **Contract points.** `CallBudget`'s ten points read in order, and each member's KDoc cites the points it fulfils; `WindowedCallBudget`'s class KDoc covers
  the twelve topics plan §8 lists, in that order, and the clock model's limit ("a clock set forward by mistake and left uncorrected can under-count a hard
  window") reads correctly. `BudgetEvent` says its seven leaves are final, and there are seven. `WindowUtilisation`'s worked burn-ratio example reads as
  intended. Point 8, "`flush()` writes the exact counts", still describes D7's behaviour: a count of zero is an exact count.
- **"Never a value."** Stated where the plan asks for it: `RetryAfter` (a failure's message is constant and never echoes the value), the `RateWindow` and
  `WindowedCallBudget` factories (the message names the property and never a value), `BudgetEvent` (no leaf prints a header value, a body, a credential or a
  store exception's message) with `StoreFailed.toString()`, and the budget's logging paragraph. The other value types' `@throws` lines state the condition
  only; their `require` messages are constant in the sources.
- **Three wordings, settled in the documentation review's KDoc.** `RetryAfter.parse`'s three-argument overload said that `nowEpochMillis` is not read and is
  kept for the stack's signature; it now says that in that overload the server's `Date` replaces it and that both overloads take the same leading arguments.
  `WindowedCallBudget.Builder.persistAhead` said that a crash over-counts by at most `persistAhead − 1`, which holds only while saves succeed and no unit
  written ahead is given back; it now says that while saves succeed a crash never under-counts the grants made and over-counts by at most `persistAhead − 1`
  plus any units written ahead and then given back, and that grants made while the store is failing run unsaved. The class KDoc's persistence paragraph
  states the exceptions (a grant made after three saves that each outlasted their period proceeds unsaved; a count a server reading or a backward clock step
  raised is saved with the next grant or `flush()`, not when it is raised) and what a store's `Error` does. `WindowUtilisation.used` compressed its sources
  into "granted plus restored plus server-raised"; it now names each: the grants of this process, the count restored from the store, a rise a server reading
  forced and a count a backward wall-clock step copied in. The same review rewords `maxBurst` (a forward reference) and `BudgetStore`'s "once, lazily" (the
  budget loads until a load succeeds), adds a missing `@return`, comments on the magic numbers, one-line KDoc on `internal` declarations and a test's KDoc.
- **What the dump cannot show.** No public constructor on `RateWindow`, `RateSignals`, `WindowedCallBudget` or any builder appears (the factories are the
  only way in); `BudgetEvent` shows its `protected` constructor; `BudgetSnapshot` shows both public constructors and `CURRENT_VERSION`; `CallBudget` and
  `Permit` carry `DefaultImpls` for their default members, as the module's other interfaces with defaults do. The dump does not record
  `@SubclassOptInRequired`, so the gate is held only by P1 and review (architecture §13; [`consequences.md`](consequences.md) §8). A `Result`-returning or
  `Duration`-typed member appears under its mangled name (`acquire-E0qYoyE`, `flush-IoAF18A`), as a Java reader meets any such API.
- **Left to the maintainer:** the IDE checks of plan §9 (completion of `WindowedCallBudget(windows) { }`, the hover text of each factory and builder property,
  the rendering of `CallBudget`'s numbered list and of `WindowUtilisation`'s example, the opt-in error on an un-annotated implementer with its quick fix, the
  `@WebToolsDsl` scoping of `RateWindow(…) { }`); [`consequences.md`](consequences.md) §8 has their owner and trigger.
