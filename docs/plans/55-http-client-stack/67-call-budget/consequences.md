# Issue #67 — `call-budget` (consequences)

- **Covers:** `SpartanLaboratories/WebTools#67`, *"webtools-http: `CallBudget` port, `WindowedCallBudget`, rate signals, the persistence and observer seams and
  their fakes"*. It is unit 8, `call-budget`, of [#55's architecture](../architecture.md) (§6.5, §6.9, §9, §11).
- **What this covers:** what the repository, its published artifacts, the sibling units and the neighbouring issues gain or lose with this unit, as built, and
  what it leaves behind. The file-level detail is in [`plan.md`](plan.md); the as-built record, with every measurement and deviation, is
  [`final-implementation.md`](final-implementation.md); the evidence is in [`research/README.md`](research/README.md) with its three findings files.
- **Status:** built at the code-landing commit `327164f`, with the deviations of [`final-implementation.md`](final-implementation.md) §2. None changes a
  sibling contract; D7 adds `flush()`'s zero row to the refinements of §5, and D8 adds an argument check to a published fixture (§1).
  [`../architecture.md`](../architecture.md) carries dated corrections (2026-11-05) for this unit at §6.5, in §7.4's row for the loader's call to `CallBudget`
  and at §13's two points.
- **Baseline:** `master` `8122af7`; branch `feat/issue-67-call-budget`, with the research commits `1ec2d92`, `f956731`, `8f5d057` and `3091e92`, the code
  commits `ff55cb8`, `ec776c7`, `c0a1c57` and `64a9a03`, then `327164f`, the code-landing commit.
- **Related docs:** [`plan.md`](plan.md) and [`final-implementation.md`](final-implementation.md); [`research/README.md`](research/README.md); the systems-level
  [`../consequences.md`](../consequences.md); the as-built records of the units this one builds on, [`../62-http-core/`](../62-http-core/final-implementation.md),
  [`../63-test-support/`](../63-test-support/final-implementation.md), [`../64-url-policy/`](../64-url-policy/final-implementation.md),
  [`../65-okhttp-transport/`](../65-okhttp-transport/final-implementation.md) and [`../66-response-cache/`](../66-response-cache/final-implementation.md).

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-http` (new, unreleased) | The main variants gain the call budget, all of it common code: the port `CallBudget` with `CallBudget.Unlimited`, `Permit` and `withPermit`; the vocabulary `Lane`, `RateWindow` (with its builder and factory), `RateSignals` (with its builder, factory and `None`), `WindowReading`, `BudgetUtilisation` and `WindowUtilisation`; `RetryAfter`; the seams `RateSignalsReader` (with `Standard`), `BudgetStore` (with `InMemoryBudgetStore`), `BudgetSnapshot`, `WindowSnapshot`, `BudgetObserver` (with `None`) and `BudgetEvent` with its seven leaves and the vocabularies `PauseSource` and `StoreOperation`; and the supplied `WindowedCallBudget` with its builder and factory. Implementing `CallBudget`, `Permit`, `RateSignalsReader`, `BudgetStore` or `BudgetObserver`, or subclassing `BudgetEvent`, needs the opt-in; everything else is Stable Core and usable without it. The ABI dump grows by exactly the classes of [`plan.md`](plan.md) §7.23, from 557 to 892 lines ([`final-implementation.md`](final-implementation.md) §1). The test-fixtures variant gains `RecordingBudgetObserver` and `FakeBudgetStore` (Stable Core, source compatibility only; `failLoads` and `failSaves` refuse a `times` below 1, D8) and no dependency. No dependency of either main variant changes: `kotlinx-coroutines-core` 1.10.2 was already the `api` dependency of `commonMain`. | `1.0.0`, unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
| `webtools-udp`, `webtools-scraping`, `webtools-browser` | Nothing: no source, build-script or test change. | unchanged |

**What a consumer of `webtools-http` 1.0.0 gets from this unit:**

- **Permits under a provider's own windows.** `WindowedCallBudget` counts in fixed windows aligned to the provider's wall-clock boundaries, holds each below the
  published limit by its margin, and charges a grant near a boundary to both neighbouring windows, so a few hundred milliseconds of skew between the two clocks
  never overspends a window.
- **Fair waiting that fails fast.** Calls on one lane are served first come first served; a call that would wait longer than its `maxWait` is refused at once with
  the exact wait; a cancelled wait is refunded and the waiters behind it move up; each lane's share is a floor held back for it, so an interactive call is not stuck
  behind a tracker backlog; `maxQueued` bounds each lane's queue.
- **Pauses from the server.** `complete(signals)` reads `Retry-After` (seconds or any of RFC 9110's three date forms, measured against the server's own `Date`),
  a window's `resetIn`, or the next boundary; the pause is the longest hint, never shortened, capped by `maxPause`, and covers every lane; a server's remaining
  count only ever raises the local count.
- **A hard daily stop that survives a restart.** A `hard` window is saved write-ahead through a `BudgetStore`, so while saves succeed a crash never
  under-counts the grants made; `flush()` writes the exact counts, a zero for a period whose grants were all released; a store that fails is retried at a
  bounded rate while the in-process stop holds. §8 lists the exceptions and the bound on over-counting.
- **A clean close.** The non-suspending `close()` ends every waiting call and every later `acquire` with `Closed("budget")` as a value; `flush()`, `utilisation()`
  and the permits already granted keep working. A call inside its admission section when `close()` runs may still be granted (§8).
- **Observability without secrets.** A `BudgetObserver` receives each event after the budget's lock is released; the budget's own logger writes creation,
  close, restores, pauses, hard stops, store failures and refusals, never a header value, a body, a credential or a store exception's message.
- **Clock behaviour that holds a bill.** The wall clock decides the windows, the monotonic source measures every wait; a backward step never lets a window be
  counted again from zero; a queue released by a forward jump is spread over the current and later windows.
- **Virtual time under test:** `timeSource` and `wallClock` are builder properties, so the published `VirtualClock` drives the budget's windows and waits together
  with `delay` and the response cache.

## 2. Blast radius in this repository

### Source

- **New files, all in `webtools-http`** ([`final-implementation.md`](final-implementation.md) §1 lists them, 81 in all):
  - `commonMain`: the port, the permit and `withPermit`, the vocabulary and the seams, `RetryAfter` with the `internal` HTTP-date parser, `WindowedCallBudget`, and
    the `internal` ledger it is built on (compiled for `js` by the check module like every common file);
  - the fixtures: `RecordingBudgetObserver` and `FakeBudgetStore`;
  - their tests in `commonTest` (the value types, the reader, the parser tables and the ledger, a seeded property test among them) and `jvmTest` (everything that
    suspends or uses `VirtualClock`, the `java.time` differential, the loopback reader test and the real-thread suites), in the level packages, with their
    support classes.
- **Changed files:**
  - `ExperimentalForInheritanceWebToolsApi.kt` and `WebToolsDsl.kt`: KDoc only; their lists gain the five gated ports with `BudgetEvent`, the two supplied
    budgets, and the three new builders;
  - `WebToolsDslComponentTest`: its list of builders gains the three new builders;
  - `VirtualClockThreeClocksComponentTest`: KDoc only;
  - `FixturesConsumerGatingTest`: two cases that use each new fixture as its gated port without the opt-in;
  - `webtools-http/api/webtools-http.api`: the new classes.
- **No caller yet.** Nothing in the repository acquires a permit; the call facades (#68) are the first.

### Build

- **No build file changes.**
- **`./gradlew build` does more work:** the new tests of `webtools-http` (Level 3 with one loopback server, and Level 4c on real threads, whose contention
  class alone runs for seconds of real time); the fixtures-publication check runs again, because `jvmTestFixtures` and `commonMain` are among its inputs; `apiCheck` against
  the regenerated dump; the check module's `js` compile of the new common files. [`final-implementation.md`](final-implementation.md) §1 counts the new test
  classes and tests by level.

### Documentation

- **`README.md`:** the `webtools-http` row of the artifacts table names the call budget, and the test-fixtures paragraph names the two new fixtures.
- **`CHANGELOG.md`:** two `### Added` entries under `## [Unreleased]`.
- **KDoc** on every new public declaration states its tier and contract.
- **[`../architecture.md`](../architecture.md)** carries dated corrections (2026-11-05) at §6.5, in §7.4's row for the loader's call to `CallBudget` and at §13's
  two points, for the points of §5.
- **[`../consequences.md`](../consequences.md)**, the systems-level document, gains one line for this unit, linking to §4.
- **No other `docs/` change.** The evergreen `docs/webtools-http-architecture.md` is #70's.

## 3. Adoption

- **No existing class budgets calls**, so nothing is moved onto the budget. Its first users are the call facades (#68).
- **`VirtualClockThreeClocksComponentTest` keeps its plain Caffeine cache.** It proves the virtual clock apart from the stack's components; the new
  `WindowedCallBudgetThreeClocksComponentTest` proves the same with the real budget beside the real cache.
- **`WallClock`'s contract** ("its reading may step backwards") has its first consumer that depends on it: the budget's clock rules are written against it.

## 4. Sibling units

[`plan.md`](plan.md) §12 has the exact contracts, and the build holds them as written; the points below are as built. In short:

- **Unit 9, `call-facades` (#68):**
  - it calls `acquire(cost: Int, lane: Lane, maxWait: Duration): Result<Permit>` (a `suspend` member, abstract on the port) inside the response cache's
    loader; a refusal is `Result.failure(HttpFailure.BudgetExhausted(wait, reason, window))` and a closed budget `Result.failure(HttpFailure.Closed("budget"))`,
    both values; a `cost` below 1 (`cost must be at least 1`) and a negative `maxWait` (`maxWait must not be negative`) throw `IllegalArgumentException`, so the
    facade validates a call's settings before anything is spent; the only `CancellationException` the budget throws is its caller's own;
  - for a request that was never sent it calls `permit.release()`, otherwise `permit.complete(signals)`; both are idempotent and `NonCancellable`, so a loader's
    `finally` may call them from a cancelled coroutine; `withPermit(cost, lane, maxWait, block)` completes a permit as spent unless the block completed or
    released it;
  - an `Error` the budget's store throws is not a store failure: it propagates to the caller of the member that made the store call (`acquire`, `utilisation()`
    or `flush()`), possibly as a stack-trace-recovery copy whose `cause` is the original, raises no `StoreFailed` and grants nothing; the lock is released and
    the budget stays usable. Inside the cache's loader it is the loader's `Error`, which the cache rethrows to every caller of that load. An `Error` an observer
    throws propagates out of the call that produced the event, after the lock is released;
  - it reads signals with `RateSignalsReader.Standard.read(response, wallClock.nowEpochMillis())` (a `429` is throttled; a `503` only with a parseable
    `Retry-After`; no readings), which `RateSignalsReaderLoopbackIntegrationTest` shows against the real transport, or with a consumer's per-provider reader;
  - after `complete(signals)`, `utilisation().pausedFor` is the pause in force: at least `min(retryAfter, maxPause)` minus the time elapsed, more when another hint
    or an earlier pause is longer, and less than `retryAfter` when `retryAfter` exceeds `maxPause`; `RateLimited.retryAfter = max(signals.retryAfter, pausedFor)` is
    therefore not redundant, and which of the two the failure reports is #68's to settle;
  - `WindowedCallBudget.maxBurst`, the smallest window's enforced limit, is the most permits granted at one instant, for sizing the transport's concurrency;
  - the owning factory builds `WindowedCallBudget(windows) { timeSource = …; wallClock = …; store = …; observer = … }` from the client's clocks;
    `CallBudget.Unlimited` is the default of both facades and ignores signals and `close()`;
  - `close()` is non-suspending and idempotent, second in the client's closing order (cache, budget, transport); it writes nothing, so the exact final write of a
    client-owned budget is a `flush()` before `close()`; a budget a consumer passes in is never closed by the client. A call inside its admission section when
    `close()` runs may still be granted (§8), so a permit granted just after `close()` returns is completed or released as usual;
  - the budget logs on its own logger, `com.spartanlabs.webtools.http.WindowedCallBudget`; the facade's own per-call line is in addition.
- **Unit 7, `response-cache` (#66):** nothing from this unit. Its loader rules hold: a refusal and `Closed` are values, and the only `CancellationException` the
  budget throws is its caller's own (a cache's `close()` cancelling a wait in progress is that caller's cancellation, which refunds the wait); a store's `Error`
  reaches the loader as an `Error`, which the cache rethrows to every caller of the load.
- **Unit 10, `feeds` (#69):** nothing directly (`UrlFetcher` takes a `CallBudget`, `Unlimited` by default).
- **Unit 11, `http-docs` (#70):** it documents the budget from this unit's KDoc, README row and CHANGELOG entries: the clock rules and their limit, what a
  failing store and a store's `Error` do, and the known gaps of §8 an operator meets.

## 5. Where the design refines the parent documents

None of these changes the unit's scope, a seam or its tier; each makes [`../architecture.md`](../architecture.md) §6.5 concrete against the code as built, and
each is a dated correction (2026-11-05) there, at §6.5, in §7.4's row or at §13. [`plan.md`](plan.md) §6.11 gives the reasons.

- **A backward clock step copies:** on a decrease of the wall index across a boundary, the current cell gains the sum of the cells above it, capped at the
  limit, once per decrease, and the higher cells stay. The architecture is silent on clock steps.
- **A forward jump re-plans:** a waiting plan whose reserved period the wall clock has left behind is re-planned from the current reading, in arrival order, so
  the waiters a jump released fill the current and later windows instead of being granted at once against periods that have ended.
- **A snapshot stamped further ahead than `maxRestoreSkew`** has its stamp and pause ignored but keeps its window counts, where §6.5 ignores it entirely.
- **The restore** runs at the first of `acquire`, `utilisation()` and `flush()` (§6.5: the first `acquire`); it adds the grants made since the start to the
  stored count (§6.5: `max(local, saved)`, which it equals when nothing was granted before the restore); a store that fails is retried at most once per
  `storeRetryInterval` while grants continue from the in-process count, with no write-ahead save before a restore succeeds and a failed save deferring the next.
- **`flush()` writes a zero row** for a hard period whose persisted mark is above its count, so the mark comes down and the next grant is written ahead again
  ([`final-implementation.md`](final-implementation.md) D7).
- **Builder properties:** `storeTimeout` (2 s) and `storeRetryInterval` (30 s) join §6.5's list; `maxQueued` applies to each lane and counts only calls that
  would wait.
- **Events:** a seventh leaf, `Restored`; `BudgetEvent` an open root gated like `HttpFailure`; `Paused.source` a `PauseSource` and `StoreFailed.operation` a
  `StoreOperation`; events delivered after the ledger's lock is released.
- **The port:** `utilisation()` suspends; `close()` completes a latch and writes nothing, and `flush()`, `utilisation()` and the permits' `complete` and
  `release` work after it.
- **The loader's call** (§7.4's row): a refusal is `BudgetExhausted(wait, reason, window)` and a closed budget `Closed("budget")`, both values; a `cost` below
  1 and a negative `maxWait` throw `IllegalArgumentException`; the only cancellation thrown is the caller's own; an `Error` the budget's store throws
  propagates (§4).
- **What a crash keeps** (§6.5's "a crash never under-counts"): the grants made while saves succeed, save for a grant made after three saves that each
  outlasted their period; grants made while saves fail run unsaved, and a count a server reading or a backward clock step raised is saved with the next grant
  or `flush()` (§8).
- **Signals:** `RateSignalsReader.Standard` takes the longest of several `Retry-After` lines and measures a date against the response's `Date`; `RetryAfter` is one
  `internal` HTTP-date parser with RFC 9110's grammar, three liberties and a saturated `delay-seconds`.
- **Time:** the wall clock decides and the monotonic source measures, both read once per lock section, with no anchor and no clamp; each wait is sliced to at
  most one minute.
- **The open points of §13:** the non-suspending `close()` of a `Mutex`-based ledger is a completed `Job()` latch raced against each waiter's wake signal, which
  settles this unit's row of [`../consequences.md`](../consequences.md) §7; and `max(signals.retryAfter, pausedFor)` is not redundant (§4 above), which answers half
  of unit 9's row.

## 6. Neighbouring issues

- **#58** (CI and repository practices): its CI would be the first place these tests run on every change. The Level 4c close-race and contention suites need
  several cores to open the windows they probe.
- **[WebTools#87](https://github.com/SpartanLaboratories/WebTools/issues/87)**, the `UrlParseLinearTimeNonFunctionalTest` flake under concurrent level tasks:
  three more Level 4c classes now run beside it; it did not show in this unit's runs.
- **The IDE checks of #62 to #66** are still open; this unit's join them, at the next IntelliJ session (§8).

## 7. Follow-ups this unit leaves

Each is still open. [`plan.md`](plan.md) §13 names the first eight and §4 holds them out of this unit; the last two come from the build, each against the plan
section it would refine.

- **A check of the wall clock against the server's `Date`**, which `RateSignalsReader.Standard` already reads, to detect a standing skew larger than the guard
  (a clock set forward by mistake), and log or react.
- **A file-backed `BudgetStore`, and a store with an atomic shared counter** so several processes can count one provider (a second `CallBudget` implementation or a
  store with atomic increment).
- **Calendar-month windows** (a boundary function per window generalises the ledger additively).
- **Per-window and fractional costs** (a provider's rate cost differing from its billing cost; weighted calls).
- **Logging a detected clock step**, with no new event leaf.
- **The one-minute wake slice as a builder property**, if a consumer needs it tuned.
- **A budget fake in the published fixtures**, and `SteppableWallClock` among them, if a consumer needs either beyond `WindowedCallBudget` on `VirtualClock`.
- **Seam graduation:** `CallBudget`, `Permit`, `RateSignalsReader`, `BudgetStore` and `BudgetObserver` move from Experimental to `@SupportedExtension` in a feature
  release once BotTools' adapter has built against them; the promotion is recorded in `CHANGELOG.md`.
- **Save a hard window when a server reading or a clock step raises its count** (plan §6.6 and §6.7): a raise of a hard cell's count is saved with the next
  grant or `flush()` today, not when it is raised (§8).
- **Rethrow a store's `Error` as the instance it threw** (plan §6.7): an `Error` a store throws crosses `withTimeoutOrNull`, so with kotlinx.coroutines'
  stack-trace recovery on (assertions enabled, the test JVM's default) the caller receives a copy whose `cause` is the store's instance;
  `CaffeineResponseCache` keeps a loader's `Error` in a field and rethrows the original. The budget's behaviour is §6.7's ("an `Error` propagates"), pinned up
  to the copy by `WindowedCallBudgetStoreFailureComponentTest` ([`final-implementation.md`](final-implementation.md) §4).

## 8. Known gaps

- **The close-race test does not see the loss of the admission's latch re-check.** With the re-check at the top of `admitLocked` removed,
  `WindowedCallBudgetCloseRaceNonFunctionalTest` passed in 5 of 5 runs (P3, [`final-implementation.md`](final-implementation.md) §4): on its 1-per-minute window a
  call parked on the lock is queued once the window's unit is taken, and its first step section returns `Closed` anyway, so the mutation changes no outcome
  the class can see. The re-check is held on virtual time by `WindowedCallBudgetCloseComponentTest`, whose parked-call case fails without it.
- **The window between a waiter's section and its wait** has no deterministic seam ([`plan.md`](plan.md) §3, Assumption 4;
  [`final-implementation.md`](final-implementation.md) D3). It rests on `Job.onJoin` selecting at once for a completed job, read from the coroutine library's
  contract ([`research/waits-and-close.md`](research/waits-and-close.md) F3); only the probabilistic close-race test probes it, and no run of it hung.
- **The re-plan cost test does not check lane order.** `WindowedCallBudgetReplanCostNonFunctionalTest` shows that no waiter is lost on real threads, not that the
  planned instants stay in lane order: a waiter's plan is private and `utilisation()` reports counts only ([`final-implementation.md`](final-implementation.md)
  D4). Lane order is held on virtual time by the Level 2 FIFO and refund classes and the ledger's Level 4a tables; no seam is added for it.
- **A call inside its admission section when `close()` runs may still be granted.** `admitLocked` checks the latch at the top of its section only; after a store
  call in that section (the restore's `load()` or a write-ahead `save()`) it decides again with a fresh clock but not a fresh latch, so a call whose store call
  spans a `close()` and that fits is granted after `close()` has returned. `WindowedCallBudgetCloseComponentTest` pins the write-ahead case (`a call whose
  write-ahead save spans a close is still granted`); the restore case is read from the code, not run. A waiter re-checks the latch after its save and returns
  `Closed`, and a call that would wait is queued and returns `Closed` at its first step.
- **A grant made after three write-ahead saves proceeds unsaved.** A section makes at most three write-ahead saves; when each ends in a new period of a hard
  window (a store whose saves outlast the period), the grant after the third is made without a save of its own, and a crash before the next save under-counts
  that period by its cost. `WindowedCallBudgetStoreFailureComponentTest` pins it on a one-second hard window whose saves take a second each
  ([`final-implementation.md`](final-implementation.md) D9); only a hard window whose period is close to the store's latency meets it.
- **While saves fail, grants run unsaved.** A failed save defers the next attempt by `storeRetryInterval`, and every grant made beyond the persisted mark while
  saves fail is unsaved: a crash then restores a count below the truth by up to as many grants. The in-process stop holds meanwhile.
- **A successful `flush()` does not reopen the write-ahead.** After a failed write-ahead save, write-ahead saves stay off until `storeRetryInterval` has passed
  even when a `flush()` in between succeeded, so the grants made after that flush and before the interval ends are not saved ahead. Read from the code, not run.
- **A count a server reading or a backward clock step raises is saved only with the next grant or `flush()`.** A reading raises a hard cell's floor
  (`BudgetLedger.reconcile`) and a backward step its base (`WindowLedger.advance`), but a save is due only at a grant: `walNeeded` compares a grant's
  `effective + cost` with the persisted mark, and the next grant's `writeAheadSnapshot` writes `max(persisted, effective)` for the other cells. When a server
  reports a hard window exhausted, no further grant is made in that period, so nothing saves the raised count unless the consumer calls `flush()`; a crash then
  restores the lower stored count, and the restarted budget grants into a period the server reported exhausted until the server throttles again and its
  reading raises the count again. A copy that fills the period is lost the same way: a restarted budget has no earlier reading to fold from, so the period
  restores its own stored count. Read from the code, not run; §7 has the follow-up.
- **The bound on over-counting holds only without give-backs.** Units written ahead and then given back, by a `release()`, by a call cancelled after its save
  or by a waiter that returns `Closed` after its save, stay in the stored count until a `flush()` writes the exact counts or later grants pass it, so a crash in
  between over-counts by those units more than the `persistAhead − 1` of [`plan.md`](plan.md) §6.7. The code is plan §7.14's; §6.7's bound holds as stated
  only when nothing written ahead is given back, and the error is on the safe side for a window that guards a bill. The D7 case of
  `WindowedCallBudgetCrashComponentTest` shows a stored 10 standing for a true count of 0 between its `release()` and its `flush()`; no test crashes there.
- **An observer's `Error` can leave a queued call's reservation behind.** Events are emitted after the lock is released; when an observer throws an `Error`
  while `acquire` emits the events of the section that queued the call, the `Error` propagates before the call starts to wait, so nothing withdraws it: the
  waiter keeps its planned units, counts in `utilisation().waiting` and towards its lane's `maxQueued`, and the calls behind it plan around it, until a re-plan
  after its `maxWait` deadline drops it; with `Duration.INFINITE` it stays for the budget's lifetime. A permit granted in such a section is charged and never
  delivered, which contract point 1 ("a permit never completed is simply spent") covers. Read from the code, not run.
- **A forward clock step that is wrong** (a clock set forward by mistake and left uncorrected, or a clock behind at start and stepped forward after grants were
  made) can under-count a hard window: no reading in common code tells it from a resume from sleep. The first follow-up of §7 would detect it.
- **Real clock behaviour is not exercised.** The copy rule for a backward step is this design's own; a model and the unit's tests support it and no comparable
  project implements it. The tests step a virtual wall clock: a real clock step, a suspend and a resume on a host are shown only by a field run
  ([`plan.md`](plan.md) §3, Assumption 9).
- **A snapshot of an unknown version is dropped whole:** a library downgraded after a newer one wrote the store restores none of its counts.
- **A slow store holds the ledger's lock.** Each store call holds it for up to `storeTimeout`, and one lock section makes at most one load and three saves; a
  blocking store holds it for as long as it blocks, since `withTimeoutOrNull` cannot interrupt it, so the port requires a cancellable implementation.
- **Event order:** events of concurrent calls may reach the observer interleaved; each event describes itself.
- **Several processes** behind one address, or sharing one store, are invisible to a local budget; a snapshot store is last-writer-wins.
- **Provider rules declared in headers** (windows a server names at run time) are not modelled; readings for window names the budget does not have are ignored.
- **The `js` target compiles and runs nothing.** The common code compiles for `js`, `select` with `onJoin` included (P2), but nothing runs there, so
  `TimeSource.Monotonic` on `js` is not exercised.
- **Cost at scale is measured on one host.** The re-plan rounds at 1,000 waiters per lane and the contention run are numbers from the build host
  ([`final-implementation.md`](final-implementation.md) §4), not benchmarks across hosts, against bounds set generously.
- **The gate on implementers has no automated check.** The ABI dump does not record `@SubclassOptInRequired`, so dropping it from one of the five ports or
  `BudgetEvent` passes `apiCheck`; P1 showed the gate once, by hand (architecture §13; [`../consequences.md`](../consequences.md) §7).
- **Judgement defaults, not measurements:** the guard (1 s), `maxPause`, `maxQueued`, `persistAhead`, `maxRestoreSkew`, `storeTimeout`, `storeRetryInterval`, the
  one-minute wake slice, the three write-ahead saves per section and the cap of 16 cells per window.
- **Measured only on the build host,** Windows 11 with JDK 23.0.2; Linux, macOS and other JDKs wait for #58's CI.
- **IDE behaviour is untested:** completion of `WindowedCallBudget(windows) { }`, the hover text of the factories and builder properties, the opt-in error on an
  implementer of each of the five gated ports and on a subclass of `BudgetEvent` with its quick fix, and the `@WebToolsDsl` scoping of the three builders
  ([`final-implementation.md`](final-implementation.md) §5). Owner: the maintainer; trigger: the next IntelliJ session, together with the open IDE items of #62
  to #66 ([`../66-response-cache/consequences.md`](../66-response-cache/consequences.md) §8).

## 9. The plan's assumptions, as built

No assumption of [`plan.md`](plan.md) §3 failed. As far as this build shows:

1. **`select` with `Job.onJoin` and `ReceiveChannel.onReceive` compiles for `js`:** held. `:webtools-http-commoncheck:build` passes with `WindowedCallBudget.kt` in
   `commonMain` (P2), so the `MutableStateFlow` fallback is not built.
2. **The re-plan stays cheap at `maxQueued` scale:** held on the build host. Every round of cancelling the head of 1,000 waiters per lane stayed an order of
   magnitude inside its bounds, in P4's three runs and in the final run of record ([`final-implementation.md`](final-implementation.md) §4 has the numbers), so
   the lane-first optimisation is not built. Lane order on real threads is not checked (§8).
3. **`runTest` 1.10.2 drains the shared scheduler at its end:** only 1.10.2 ran. The Level 2 classes close the budgets they build, with `use` or, where
   `close()` or construction is what they test, directly, and the component task passed with no test hung, failed or skipped.
4. **The close race has a window that no deterministic seam reaches:** held ([`final-implementation.md`](final-implementation.md) D3). No run of
   `WindowedCallBudgetCloseRaceNonFunctionalTest` hung, so the periodic latch check the assumption names for a hang is not built; P3 shows that the class does
   not detect the loss of the admission's re-check, which the Level 2 close class does (§8).
5. **The one-minute wait slice is a judgement:** unchanged; the slice is an `internal` constant, and a builder property for it stays a follow-up (§7).
6. **`java.time`'s reduced-year window is identical on the build JDK:** held. The differential passes on JDK 23.0.2 with the oracle base the plan specifies,
   `clamp(referenceYear, 50, 9949) − 49`, so no base was adjusted.
7. **The Kotlin code of `HttpDate` was not compiled:** it compiles, and the Level 4a table (`HttpDateDeterministicTest`, `RetryAfterDeterministicTest`) and the
   differential (three parts of 200,000 iterations at seed 7, with its coverage floors) pass.
8. **No JUnit parallel execution is configured:** the repository has no `junit-platform.properties`.
9. **The budget's behaviour across a real suspend and a real clock step is not testable here:** still so; §8 carries it.
