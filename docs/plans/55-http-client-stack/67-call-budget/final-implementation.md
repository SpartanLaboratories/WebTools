# As-built: `call-budget` (Issue #67)

The as-built record of [`plan.md`](plan.md). It is written while the stages land and reconciled into the final record once
implementation ends; until then it holds the deviations from the plan, each logged at the moment it was made.

## Deviations

### D1. `PauseDeterministicTest` steps its own time source instead of the JVM test support's `ManualTimeSource`

- **Plan section:** §9, Level 4a table, row `PauseDeterministicTest` ("`LedgerClock` keeps the elapsed reading non-decreasing over a
  `ManualTimeSource` that steps back"), with §9's placement rule (pure arithmetic tests live in `commonTest`).
- **What differs:** the class is in `commonTest` as the table places it, and it steps a private `SteppingTimeSource` of its own (the
  same shape as `ManualTimeSource`: a settable `now`, marks that read `now` minus the value at the mark) and a private fixed
  `WallClock`.
- **Why:** `ManualTimeSource` is a `jvmTest` support class (`webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/support/webtools/http/ManualTimeSource.kt`),
  so `commonTest` cannot see it. Moving the class to `jvmTest` would break the placement rule; a copy of the support into
  `commonTest` would duplicate a name the stage-4 tests use from `jvmTest`. The behaviour the row locks down (the non-decreasing
  elapsed reading over a source that steps back) is tested exactly as written.
- **Based on:** plan §6.2 (the time model: "an injected `TimeSource` need not be monotonic"), from
  [`research/ledger-time.md`](research/ledger-time.md) F1 to F5.

### D2. `BudgetLedgerPropertyDeterministicTest` runs a fixed 3,000 steps per property

- **Plan section:** §9, Level 4a table, row `BudgetLedgerPropertyDeterministicTest` ("3,000 steps per property; `WEBTOOLS_PROPERTY_STEPS`
  raises the count").
- **What differs:** the step count is the constant 3,000; the environment variable is not read.
- **Why:** the class is in `commonTest`, as the table places it, and common Kotlin has no environment access (`System.getenv` is JVM
  API, which `commonTest` cannot name). The seed and the step count are the plan's defaults, so the committed run is the one the
  plan specifies; only the knob to lengthen it is missing. A JVM-side knob would need either a `jvmTest` wrapper of the property or
  an `expect`/`actual` in test code, neither of which the plan provides.
- **Based on:** plan §9's conventions (a `commonTest` placement for everything on the ledger; the shape of the knob taken from the
  JVM differential tests, `WEBTOOLS_DIFFERENTIAL_INPUTS`).

### D3. `WindowedCallBudgetCloseComponentTest` covers a waiter that has not started in place of one "between its section and its wait"

- **Plan section:** §9, Level 2 table, row `WindowedCallBudgetCloseComponentTest` ("**a waiter between its section and its wait** also
  [returns `Closed` once the lock frees]").
- **What differs:** no case puts a waiter between the end of its lock section and the start of its `select`: the class covers, in its place,
  a call whose coroutine has not run at all when `close()` happens (`a call whose coroutine has not run when close happens returns Closed`),
  beside the parked-on-the-lock case and the sleeping-waiter case the row also names.
- **Why:** on the virtual scheduler a waiter leaves its section and enters `withTimeoutOrNull { select { … } }` with no suspension point
  between the two, so no test can interleave `close()` there; the plan itself records that window as reachable by no deterministic seam
  (§3, Assumption 4) and assigns it to the Level 4c close-race class and procedure P3 of a later stage. The sleeping-waiter case still
  proves the mechanism the row relies on (`Job.onJoin` selecting for a completed latch).
- **Based on:** plan §6.5 (the close latch and the waiter's `select`), from [`research/waits-and-close.md`](research/waits-and-close.md)
  F1 to F3 (`Job.onJoin` "never fails" and selects at once for a completed job).

### D4. `WindowedCallBudgetReplanCostNonFunctionalTest` cannot check that the queue's instants stay in lane order

- **Plan section:** §9, Level 4c table, row `WindowedCallBudgetReplanCostNonFunctionalTest` ("after the rounds the queue's instants are still in
  lane order and no waiter is lost").
- **What differs:** the class checks that no waiter is lost (each cancelled call ended with its own cancellation; every other call still waits
  or holds a permit granted at a boundary; `utilisation().waiting` equals the number still running) but not the order of the planned instants.
  To reach "1,000 waiters on each of two lanes" and "the head waiter" it first fills the 10-per-minute window (8 `Tracker` and 2 `Interactive`
  grants, the split the default shares impose) and launches the waiters one at a time on a single-threaded dispatcher, so the arrival order
  is the launch order and round *k* cancels the earliest arrival still waiting. The measured numbers are printed and recorded under P4.
- **Why:** a waiter's planned instant is not observable: `WindowedCallBudget.Waiter.plan` is private to the budget, `utilisation()` reports
  counts only, and on real time the instants lie up to 200 minutes ahead, so they cannot be observed as grants within the bound either. The
  ordering itself is locked down on virtual time by the Level 2 FIFO and refund classes and by the ledger's deterministic tables (plans never
  decrease within a lane).
- **Based on:** plan §6.5 (re-planning in arrival order from each lane's previous slot) and §3 Assumption 2, from the stack-level
  [`../research/call-budget.md`](../research/call-budget.md) probe (1.2 to 1.7 ms per cancel at 3,000 waiters).

### D5. `WindowedCallBudgetCloseRaceNonFunctionalTest` admits a permit granted before the close as an outcome

- **Plan section:** §9, Level 4c table, row `WindowedCallBudgetCloseRaceNonFunctionalTest` ("every call ends within a 30-second bound with
  `Closed("budget")` or its own `CancellationException`, never anything else").
- **What differs:** a round's calls may also end with a permit, at most one per minute period the round touched (the row's own "all but one
  wait": the first call to fit the 1-per-minute window is granted before `close()` can refuse it, and a round that straddles a minute boundary
  on the production clock may grant one more at the boundary). The window is built with `laneShares = emptyMap()`, as R8 says a window of
  limit 1 must be, or every `Tracker` call would be refused with `CostExceedsLimit` instead of waiting. "Its own `CancellationException`" also
  covers a call cancelled before its coroutine first ran: such a call records nothing, so the class accepts a recorded-nothing outcome only for
  a call in the cancelled set whose job is cancelled.
- **Why:** the plan's letter leaves no outcome for the grant its own setup produces; the bound on grants per period keeps the invariant it means
  (nothing is granted or waits after the close that the latch should have refused).
- **Based on:** plan §6.5 (the close latch; a call parked on the lock sees it in its first check) and §3 Assumption 4, from
  [`research/waits-and-close.md`](research/waits-and-close.md) F1 to F3.

### D6. `WindowedCallBudgetContentionNonFunctionalTest` reads its three bounds in the ledger's own terms

- **Plan section:** §9, Level 4c table, row `WindowedCallBudgetContentionNonFunctionalTest` ("the grants never exceed `limit × (whole seconds
  spanned + 1)`", "the ledger and queue are empty afterwards").
- **What differs:** the grant bound counts the whole-second periods the run touched, `end / 1000 − start / 1000 + 1` from the first call to
  the last completion, which is what a per-period limit allows (a 0.2 s run that straddles a boundary may legitimately grant two periods'
  worth); "the ledger is empty" is read as no planned units in any window and `waiting == 0`, since the current second's `used` is
  necessarily non-zero right after the last grants; `laneShares = emptyMap()` so that `limit` is the window's plain 20 (the row names no
  lanes). The cancelled set and its offsets of up to 50 ms are drawn from `Random(67)`.
- **Why:** the alternative readings would fail a correct budget or could not hold at all.
- **Based on:** plan §6.3 (one cell per period; the guard's two-cell charge) and §6.4 (the grant), from the stack-level
  [`../research/call-budget.md`](../research/call-budget.md) F1 to F4.

### D7. `BudgetLedger.exactSnapshot` writes a row of zero for a hard cell whose persisted mark is above its count

- **Plan section:** §7.14, `BudgetLedger.exactSnapshot` ("the exact effective count of every hard window cell at or above the current index"), whose
  listing writes a `WindowSnapshot` only for a cell with `effective > 0`.
- **What differs:** `exactSnapshot` also writes a cell whose `effective` is 0 when its `persisted` mark is above 0, as `WindowSnapshot(window, start, 0)`;
  `markPersisted` is unchanged and, through that row, lowers the mark to 0. A cell with neither a count nor a mark is still omitted. Nothing else in the
  ledger changed, and no public signature or KDoc did (`WindowSnapshot.used` already admits 0; the dump is unchanged).
- **Why:** the code review found that the plan's listing breaks §6.7's rule ("the persisted number is at least the true usage at every grant that made a
  save, and a crash restores to at most `persistAhead − 1` above the truth"). On a hard day window of 30 with `persistAhead` 10: one grant writes ahead
  `{day: 10}` and sets `persisted = 10`; `release()` takes `effective` to 0; `flush()` then omitted the cell, so the store held no day row while the
  cell's mark stayed 10; the next nine grants found `effective + cost <= persisted` and saved nothing; a crash restored 0 for a true usage of 9, an
  under-count of up to `persistAhead − 1` on the window that guards a bill. Writing the zero row makes the stored snapshot state the truth and lets the
  existing `markPersisted` path lower the mark, so the next grant is written ahead again; this keeps one code path and conforms to §6.7's own description
  of `flush()` ("`effective` of every hard window cell at or above the current index"). The alternative, a reset of the omitted cells' marks inside
  `markPersisted` or `writeExact`, would have left the store and the ledger disagreeing about which cells exist.
- **Locked down by:** `WindowedCallBudgetCrashComponentTest` ("a flush after every grant was released resets the persisted mark and a crash after it never
  under-counts": write-ahead grant, `release()`, `flush()` writes 0, nine grants, the first of them written ahead again, a second budget over the same
  store restores at least 9 and at most 18) and `BudgetLedgerSnapshotDeterministicTest` ("exactSnapshot writes a zero row for a cell whose persisted mark
  is above its count and markPersisted lowers the mark"). Both failed on the plan's listing at the step where the flush writes no row, and pass with the
  change.
- **Based on:** plan §6.7 (the write-ahead invariant) and the parent architecture's
  [§6.5 Call budget](../architecture.md#65-call-budget) ("persisted write-ahead in blocks of `persistAhead`, so a crash never under-counts"), from
  [`research/README.md`](research/README.md)'s summary of the write-ahead ("a `hard` window's count is saved before the grant that would exceed the
  persisted mark") and [`research/waits-and-close.md`](research/waits-and-close.md) (the store calls under the lock; the mark advances only on success).

### D8. `FakeBudgetStore.failLoads` and `failSaves` refuse a `times` below 1

- **Plan section:** §7.17, the `FakeBudgetStore` listing (`failLoads(cause, thrown = false, times = Int.MAX_VALUE)` and `failSaves(…)` arm a
  `Failure(cause, thrown, times)` with no check on `times`), with R16 (the fixtures).
- **What differs:** both methods begin with `require(times >= 1) { "times must be at least 1" }` (a constant message, the module's rule for
  programmer errors) and their KDoc gains the `@throws IllegalArgumentException` line; `Int.MAX_VALUE` keeps its meaning of "every one". Nothing
  else in the fixture changed; the fixtures are not in the API dump, which is unchanged.
- **Why:** the test review found that `times = 0` delivered one failure instead of none: `consume` decrements first (`--remaining <= 0` is true
  at −1), disarms the script, but still returns the armed failure for the current call. A script of zero failures is not a meaningful request
  (it is `heal()`), so the fixture refuses it rather than silently delivering one; a negative `times` is refused the same way.
- **Locked down by:** `FakeBudgetStoreComponentTest` ("a times below 1 is refused and times 1 fails exactly once": 0 and −1 refused on both
  methods with the message, a refused script arms nothing, `times = 1` fails exactly one load and one save and then heals).
- **Based on:** plan §7.17 (the fixtures are "the worked example of the seam" and compile under `-Xexplicit-api=strict`, so their contract is
  stated rather than implied) and the parent architecture's [§6.7 Published test support](../architecture.md#67-published-test-support).

## Procedures

### P1 — the opt-in gate, by hand (plan §9.2)

Run after stage 4, with the stages 1 to 4 sources in place. Two scratch files, `webtools-http/src/jvmTest/kotlin/P1JvmScratch.kt` and
`webtools-http/src/commonTest/kotlin/P1CommonScratch.kt`, each held six top-level classes with every abstract member overridden: an
implementer of `CallBudget`, of `Permit`, of `RateSignalsReader`, of `BudgetStore`, of `BudgetObserver`, and a subclass of `BudgetEvent`.

- **Without `@OptIn`:** `:webtools-http:compileTestKotlinJvm` failed with 12 errors, exactly one per declaration per source set, each
  reading `This class or interface requires opt-in to be implemented: Implementing or substituting this WebTools seam is Experimental and
  may change in a minor release. …` (the marker's message).
- **With `@OptIn(ExperimentalForInheritanceWebToolsApi::class)` on each class:** the same task compiled cleanly.
- Both scratch files were deleted afterwards; nothing of them is in the tree.

### P2 — the `js` compile and the dump (plan §9.2)

- `:webtools-http-commoncheck:build` passes with `WindowedCallBudget.kt` in `commonMain`: `select` over `Job.onJoin` and
  `ReceiveChannel.onReceive`, `Mutex`, `Channel(CONFLATED)`, `withTimeoutOrNull` and `NonCancellable` all compile for `js`, which settles
  plan §3 Assumption 1 (no fallback to a `MutableStateFlow` version was needed).
- `:webtools-http:apiDump` then `:webtools-http:apiCheck` pass. The stage-4 delta of `webtools-http/api/webtools-http.api` is exactly the
  three classes §7.23 lists for the stage, `WindowedCallBudget`, `WindowedCallBudget$Builder` and `WindowedCallBudgetKt` (41 lines); no
  `internal` member, no ledger type and no `BudgetStoreFailure` appear.

### P3 — the close-race mutation (plan §9.2)

Run after stage 5, on a 16-thread host. `WindowedCallBudget.kt` was copied aside, the re-check `if (closing.isCompleted) return
Admission.Settled(closedFailure())` at the top of `admitLocked` was removed, and `WindowedCallBudgetCloseRaceNonFunctionalTest` was run five
times (`:webtools-http:nonfunctionalTest --rerun --tests '*WindowedCallBudgetCloseRace*'`).

- **Outcome: not detected, 0 of 5 runs** (every run `tests="1" failures="0"`, all 20 rounds green each time).
- **Why the test cannot see it:** with the re-check gone, a call parked on the lock when `close()` runs proceeds through its admission and,
  on a window of 1 per minute whose unit is already taken, is queued; its first `stepLocked` section then finds the latch and returns
  `Closed("budget")` all the same. The mutation changes the outcome only for a call that would be granted at once after the close, which the
  1-per-minute window never offers once its first call is granted. The re-check therefore saves one admission (a restore attempt and a slot
  search) rather than deciding the outcome; the latch check in `stepLocked` and the one at the top of `acquire` carry the contract.
- The file was restored from the copy and verified identical with `cmp`; the copy was deleted. The plan asks for this outcome to be noted in
  `consequences.md`; its known gaps do not carry it yet.
- **The Level 2 class detects the same mutation deterministically** (confirmed once after the code review, with the same copy-mutate-restore steps and
  `:webtools-http:componentTest --rerun --tests '*WindowedCallBudgetCloseComponentTest*'`): `WindowedCallBudgetCloseComponentTest` ran its 7 tests with
  1 failure, the case "a call parked on the lock when close runs returns Closed once the lock frees" (`Expected value to be of type HttpFailure$Closed,
  actual <null>`: the parked call was granted). Its window, 10 per minute with one unit taken, has free capacity, so without the re-check the parked
  call is granted at once after `close()` instead of returning `Closed`, which is exactly the outcome the mutation changes. The re-check is therefore
  covered on virtual time; only the production-clock race of the Level 4c class cannot see it. The file was again restored by copy, verified identical
  with `cmp`, and the copy deleted.

### P4 — the re-plan numbers (plan §9.2, Assumption 2)

`WindowedCallBudgetReplanCostNonFunctionalTest` three times on a 16-thread host, 1,000 waiters per lane on a 10-per-minute window, 20 rounds each
cancelling the head waiter (bounds: 2 s per round, 250 ms mean):

| Run | Per-round times (ms, in order) | Mean | Max |
|---|---|---|---|
| 1 | 29.8, 25.0, 15.1, 16.1, 17.8, 14.3, 12.7, 13.5, 18.2, 13.9, 13.2, 10.9, 14.0, 9.6, 11.8, 11.8, 12.1, 10.8, 10.0, 12.6 | 14.66 ms | 29.8 ms |
| 2 | 4.0, 17.2, 19.0, 18.7, 15.5, 14.9, 15.0, 13.7, 16.0, 14.1, 14.2, 14.4, 14.1, 16.0, 14.3, 11.5, 13.6, 15.0, 14.2, 13.7 | 14.45 ms | 19.0 ms |
| 3 | 3.6, 23.6, 13.8, 37.1, 19.2, 16.2, 16.0, 12.3, 14.2, 17.7, 14.9, 13.2, 14.6, 14.5, 14.8, 14.5, 15.6, 15.5, 16.5, 13.8 | 16.07 ms | 37.1 ms |

A round's time covers the cancellation's dispatch, the head's withdrawal under the lock (unlink and the re-plan of the 1,999 waiters behind
it, each from its lane's previous slot) and the first poll of `utilisation()` that sees the count fall; the wakes of the waiters moved earlier
run on afterwards. Every round sits an order of magnitude inside its bound, so Assumption 2's optimisation (re-planning the lane's waiters first)
is not needed. Queueing the 2,000 waiters took about 170 ms. For the record, `WindowedCallBudgetContentionNonFunctionalTest` granted 302, 306 and
303 of its 400 calls over 16 whole-second periods (bound 320) in 15.1, 14.2 and 14.7 s.

### P5 — Level 5 UAT, the KDoc and the dump read as a consumer (plan §9.2)

Read after the code review: the KDoc of every new public type in `commonMain` (`Lane`, `RateWindow` and its builder and factory, `WindowReading`,
`RateSignals` and its builder, factory and `None`, `PauseSource`, `StoreOperation`, `RetryAfter`, `RateSignalsReader` and `Standard`, `BudgetSnapshot`,
`WindowSnapshot`, `BudgetStore`, `InMemoryBudgetStore`, `BudgetObserver` and `None`, `BudgetEvent` and its seven leaves, `BudgetUtilisation`,
`WindowUtilisation`, `CallBudget` and `Unlimited`, `Permit`, `withPermit`, `WindowedCallBudget` and its builder and factory), the two fixtures, the two
extended lists in `ExperimentalForInheritanceWebToolsApi` and `WebToolsDsl`, and the added section of `webtools-http/api/webtools-http.api`.

- **Tiers.** Every type names its tier in its first KDoc paragraph. The six gated types (`CallBudget`, `Permit`, `RateSignalsReader`, `BudgetStore`,
  `BudgetObserver`, `BudgetEvent`) say "Experimental (implement)" or "(subclass)", name the `@OptIn` line, and say that calling, passing and matching
  are Stable Core; each supplied implementation (`Unlimited`, `Standard`, `InMemoryBudgetStore`, `BudgetObserver.None`, `WindowedCallBudget`) says it
  needs no opt-in. The marker's own list and the `@WebToolsDsl` list name every new gated type and builder. Consistent with §8's table.
- **Contract points.** `CallBudget`'s ten points read in order and each member's KDoc cites the points it fulfils; `WindowedCallBudget`'s class KDoc
  covers the twelve topics §8 lists in that order, and the clock-model limit ("a clock set forward by mistake and left uncorrected can under-count a
  hard window") reads correctly. `BudgetEvent` says "the seven leaves are final" and there are seven. `WindowUtilisation`'s worked burn-ratio example
  reads as intended. After D7, point 8 ("`flush()` writes the exact counts") still describes the behaviour: a count of zero is an exact count.
- **"Never a value" statements.** Present and accurate where the plan asks for them: `RetryAfter` ("a failure's message is constant and never echoes
  the value"), the `RateWindow` and `WindowedCallBudget` factories ("the message names the property and never a value"), `BudgetEvent` ("no leaf prints
  a header value, a body, a credential or a store exception's message") and `StoreFailed.toString()`, and the budget's logging paragraph. The other
  value types' `@throws` lines state the condition only; their `require` messages are constant (checked in the sources), so the statement is true but
  unwritten there.
- **The dump.** No public constructor on `RateWindow`, `RateSignals`, `WindowedCallBudget` or any builder (the factories are the only way in);
  `BudgetEvent` shows its `protected` constructor; `BudgetSnapshot` shows both public constructors and `CURRENT_VERSION`; `CallBudget` and `Permit`
  carry `DefaultImpls` classes for their default members, as the module's existing interfaces with defaults do. No `internal` member, ledger type or
  `BudgetStoreFailure` appears.
- **Unclear or worth a maintainer's look (no change made):** `RetryAfter.parse(value, nowEpochMillis, serverDateEpochMillis)` states that
  `nowEpochMillis` is not read and is kept for the stack's signature, which a consumer may find odd on first reading though it is honest;
  `WindowedCallBudget.Builder.persistAhead` says "a crash over-counts by at most `persistAhead − 1`", which holds while the store is healthy, and the
  under-count a down store allows is stated only in the class KDoc's persistence paragraph and plan §10; `WindowUtilisation.used`'s "granted plus
  restored plus server-raised" compresses three sources into one phrase.
- **Left to the maintainer's next IntelliJ session**, as the plan says: the hover text of each factory and builder property, the rendering of the
  `CallBudget` numbered list and the `WindowUtilisation` example, and the `@OptIn` quick-fix the IDE offers on an un-annotated implementer.

## Runs

Stage 5, from the repository root (`webtools-http`'s tasks only; no other module's suite):

- `./gradlew :webtools-http:integrationTest --rerun :webtools-http:nonfunctionalTest --rerun` — **green**: 26 integration report files
  (`RateSignalsReaderLoopbackIntegrationTest` 6 tests, 0 failures) and 22 non-functional report files (`WindowedCallBudgetCloseRaceNonFunctionalTest`,
  `WindowedCallBudgetContentionNonFunctionalTest`, `WindowedCallBudgetReplanCostNonFunctionalTest` 1 test each, 0 failures), every other class in both
  tasks 0 failures; `UrlParseLinearTimeNonFunctionalTest` passed in this run.
- `./gradlew :webtools-http:check` (once, at the end of stage 5) — **green**, `BUILD SUCCESSFUL`, 20 actionable tasks (5 executed, 15 up to date):
  `apiCheck` passed, `verifyJavaFloor` and `:build-logic:fixturesPublicationCheck` were up to date (their inputs unchanged since stage 4), and
  `jvmTest` ran the whole JVM suite: 227 report files, 1,204 tests, 0 failures (`UrlParseLinearTimeNonFunctionalTest` included).

After the code review (D7 and the four added test cases), from the repository root:

- `./gradlew :webtools-http:componentTest --rerun :webtools-http:deterministicTest --rerun :webtools-http-commoncheck:build` — **green**: `componentTest`
  112 report files, 743 tests, 0 failures, 0 errors, 0 skipped; `deterministicTest` 56 report files, 293 tests, 0 failures, 0 errors, 0 skipped; the `js`
  check module builds. The amended classes ran their full counts: `WindowedCallBudgetCrashComponentTest` 4, `WindowedCallBudgetStoreFailureComponentTest` 7,
  `WindowedCallBudgetRefundComponentTest` 9, `BudgetLedgerSnapshotDeterministicTest` 10 (and `WindowedCallBudgetCloseComponentTest` 7, unchanged).
- `./gradlew :webtools-http:apiDump` then `./gradlew :webtools-http:apiCheck` — **green**; the dump's SHA-1 is identical before and after the dump
  (`BudgetLedger` is `internal`, so D7 changes nothing public).

After the test review (D8 and the five added cases), from the repository root:

- `./gradlew :webtools-http:gatingTest --rerun :webtools-http:componentTest --rerun` — **green**: `gatingTest` 11 report files, 32 tests, 0 failures, 0 errors,
  0 skipped; `componentTest` 112 report files, 748 tests, 0 failures, 0 errors, 0 skipped. The amended classes ran their full counts:
  `FixturesConsumerGatingTest` 6 (two new: the fake store held as a `BudgetStore`, the recording observer held as a `BudgetObserver`),
  `FakeBudgetStoreComponentTest` 11 (D8's case), `WindowedCallBudgetCloseComponentTest` 9 (a waiter whose write-ahead save spans a `close()`, and a call whose
  write-ahead save spans one), `WindowedCallBudgetStoreFailureComponentTest` 8 (a store that throws an `Error`), `WindowedCallBudgetRestoreComponentTest` 12 (a
  retried restore that merges counts re-plans the waiters).
- `./gradlew :webtools-http:apiDump` then `./gradlew :webtools-http:apiCheck` — **green**; the dump's SHA-1 (`03d0749a…`) is identical before and after the
  dump (the fixtures are not in it, and nothing in `commonMain` changed).
- One observation from the `Error` case, for the record: an `Error` a store throws reaches the caller of `acquire` through `withTimeoutOrNull`, and with
  assertions enabled (the test JVM's default) kotlinx.coroutines' stack-trace recovery re-creates it, so the caller receives a copy whose `cause` is the
  store's instance, not the instance itself; `CaffeineResponseCache` avoids this for a loader's `Error` by rethrowing the original from a field. The case
  asserts the identity modulo that copy. The budget's behaviour is the plan's (§6.7: "an `Error` propagates"); whether it should mirror the cache is noted
  as a follow-up candidate, not changed here.
