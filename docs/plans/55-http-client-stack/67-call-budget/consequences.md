# Issue #67 — `call-budget` (consequences)

- **Covers:** `SpartanLaboratories/WebTools#67`, *"webtools-http: `CallBudget` port, `WindowedCallBudget`, rate signals, the persistence and observer seams and
  their fakes"*. It is unit 8, `call-budget`, of [#55's architecture](../architecture.md) (§6.5, §6.9, §9, §11).
- **What this covers:** what the repository, its published artifacts, the sibling units and the neighbouring issues gain or lose with this unit, and what it
  leaves behind. The file-level detail is in [`plan.md`](plan.md); the evidence is in [`research/README.md`](research/README.md) with its three findings files.
- **Status:** planned. Once the unit is built, this document is reconciled with the build, and the as-built record, with every measurement and deviation, is
  `final-implementation.md` beside it.
- **Baseline:** `master` `8122af7`; branch `feat/issue-67-call-budget`, with the research commits `1ec2d92`, `f956731`, `8f5d057` and `3091e92`.
- **Related docs:** [`plan.md`](plan.md); [`research/README.md`](research/README.md); the systems-level [`../consequences.md`](../consequences.md); the as-built records
  of the units this one builds on, [`../62-http-core/`](../62-http-core/final-implementation.md), [`../63-test-support/`](../63-test-support/final-implementation.md),
  [`../64-url-policy/`](../64-url-policy/final-implementation.md), [`../65-okhttp-transport/`](../65-okhttp-transport/final-implementation.md) and
  [`../66-response-cache/`](../66-response-cache/final-implementation.md).

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-http` (new, unreleased) | The main variants gain the call budget, all of it common code: the port `CallBudget` with `CallBudget.Unlimited`, `Permit` and `withPermit`; the vocabulary `Lane`, `RateWindow` (with its builder and factory), `RateSignals` (with its builder, factory and `None`), `WindowReading`, `BudgetUtilisation` and `WindowUtilisation`; `RetryAfter`; the seams `RateSignalsReader` (with `Standard`), `BudgetStore` (with `InMemoryBudgetStore`), `BudgetSnapshot`, `WindowSnapshot`, `BudgetObserver` (with `None`) and `BudgetEvent` with its seven leaves and the vocabularies `PauseSource` and `StoreOperation`; and the supplied `WindowedCallBudget` with its builder and factory. Implementing `CallBudget`, `Permit`, `RateSignalsReader`, `BudgetStore` or `BudgetObserver`, or subclassing `BudgetEvent`, needs the opt-in; everything else is Stable Core and usable without it. The ABI dump grows by exactly the classes of [`plan.md`](plan.md) §7.23. The test-fixtures variant gains `RecordingBudgetObserver` and `FakeBudgetStore` (Stable Core, source compatibility only) and no dependency. No dependency of either main variant changes: `kotlinx-coroutines-core` 1.10.2 was already the `api` dependency of `commonMain`. | `1.0.0`, unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
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
- **A hard daily stop that survives a restart.** A `hard` window is saved write-ahead through a `BudgetStore`, so a crash over-counts by at most
  `persistAhead - 1` and never under-counts; `flush()` writes the exact counts; a store that fails is retried at a bounded rate while the in-process stop holds.
- **A clean close.** The non-suspending `close()` ends every waiting call and every later `acquire` with `Closed("budget")` as a value; `flush()`, `utilisation()`
  and the permits already granted keep working.
- **Observability without secrets.** A `BudgetObserver` receives each event after the budget's lock is released; the budget's own logger writes creation,
  close, restores, pauses, hard stops, store failures and refusals, never a header value, a body, a credential or a store exception's message.
- **Clock behaviour that holds a bill.** The wall clock decides the windows, the monotonic source measures every wait; a backward step never lets a window be
  counted again from zero; a queue released by a forward jump is spread over the current and later windows.
- **Virtual time under test:** `timeSource` and `wallClock` are builder properties, so the published `VirtualClock` drives the budget's windows and waits together
  with `delay` and the response cache.

## 2. Blast radius in this repository

### Source

- **New files, all in `webtools-http`** ([`plan.md`](plan.md) §7 lists them):
  - `commonMain`: the port, the permit and `withPermit`, the vocabulary and the seams, `RetryAfter` with the `internal` HTTP-date parser, `WindowedCallBudget`, and
    the `internal` ledger it is built on (compiled for `js` by the check module like every common file);
  - the fixtures: `RecordingBudgetObserver` and `FakeBudgetStore`;
  - their tests in `commonTest` (the value types, the reader, the parser tables and the ledger, a seeded property test among them) and `jvmTest` (everything that
    suspends or uses `VirtualClock`, the `java.time` differential, the loopback reader test and the real-thread suites), in the level packages, with their
    support classes.
- **Changed files:**
  - `ExperimentalForInheritanceWebToolsApi.kt` and `WebToolsDsl.kt`: KDoc only, their lists gain the five gated ports with `BudgetEvent`, and the three new builders;
  - `WebToolsDslComponentTest`: its list of builders gains the three new builders;
  - `VirtualClockThreeClocksComponentTest`: KDoc only;
  - `webtools-http/api/webtools-http.api`: the new classes.
- **No caller yet.** Nothing in the repository acquires a permit; the call facades (#68) are the first.

### Build

- **No build file changes.**
- **`./gradlew build` does more work:** the new tests of `webtools-http` (Level 3 integration with one loopback server, and Level 4c non-functional on real
  threads); the fixtures-publication check runs again, because `jvmTestFixtures` and `commonMain` are among its inputs (44 to 52 s,
  [`../consequences.md`](../consequences.md) §2); `apiCheck` against the regenerated dump; the check module's `js` compile of the new common files.

### Documentation

- **`README.md`:** the `webtools-http` row of the artifacts table names the call budget, and the test-fixtures paragraph names the two new fixtures.
- **`CHANGELOG.md`:** two `### Added` entries under `## [Unreleased]`.
- **KDoc** on every new public declaration states its tier and contract.
- **[`../architecture.md`](../architecture.md)** gains dated corrections at §6.5, at §7.4's row for the loader's call to `CallBudget`, and at §13's two points once the
  unit is built (§5 below lists what they record).
- **[`../consequences.md`](../consequences.md)**, the systems-level document, gains one line for this unit, linking to §4.
- **No other `docs/` change.** The evergreen `docs/webtools-http-architecture.md` is #70's.

## 3. Adoption

- **No existing class budgets calls**, so nothing is moved onto the budget. Its first users are the call facades (#68).
- **`VirtualClockThreeClocksComponentTest` keeps its plain Caffeine cache.** It proves the virtual clock apart from the stack's components; the new
  `WindowedCallBudgetThreeClocksComponentTest` proves the same with the real budget beside the real cache.
- **`WallClock`'s contract** ("its reading may step backwards") gets its first consumer that depends on it: the budget's clock rules are written against it.

## 4. Sibling units

[`plan.md`](plan.md) §12 has the exact contracts. In short:

- **Unit 9, `call-facades` (#68):**
  - it calls `acquire(cost, lane, maxWait): Result<Permit>` inside the response cache's loader; a refusal is `Result.failure(HttpFailure.BudgetExhausted(wait,
    reason, window))` and a closed budget `Result.failure(HttpFailure.Closed("budget"))`, both values; a `cost` below 1 and a negative `maxWait` throw, so the
    facade validates a call's settings before anything is spent;
  - for a request that was never sent it calls `permit.release()`, otherwise `permit.complete(signals)`; both are idempotent and `NonCancellable`, so a loader's
    `finally` may call them from a cancelled coroutine; `withPermit(cost, lane, maxWait, block)` completes a permit as spent unless the block completed or
    released it;
  - it reads signals with `RateSignalsReader.Standard.read(response, wallClock.nowEpochMillis())` (a `429` is throttled; a `503` only with a parseable
    `Retry-After`; no readings) or a consumer's per-provider reader;
  - after `complete(signals)`, `utilisation().pausedFor` is the pause in force: at least `min(retryAfter, maxPause)` minus the time elapsed, more when another hint
    or an earlier pause is longer, and less than `retryAfter` when `retryAfter` exceeds `maxPause`; `RateLimited.retryAfter = max(signals.retryAfter, pausedFor)` is
    therefore not redundant, and which of the two the failure reports is #68's to settle;
  - `WindowedCallBudget.maxBurst`, the smallest window's enforced limit, is the most permits granted at one instant, for sizing the transport's concurrency;
  - the owning factory builds `WindowedCallBudget(windows) { timeSource = …; wallClock = …; store = …; observer = … }` from the client's clocks;
    `CallBudget.Unlimited` is the default of both facades and ignores signals and `close()`;
  - `close()` is non-suspending and idempotent, second in the client's closing order (cache, budget, transport); it writes nothing, so the exact final write of a
    client-owned budget is a `flush()` before `close()`; a budget a consumer passes in is never closed by the client;
  - the budget logs on its own logger, `com.spartanlabs.webtools.http.WindowedCallBudget`; the facade's own per-call line is in addition.
- **Unit 7, `response-cache` (#66):** nothing from this unit. Its loader rules hold: a refusal and `Closed` are values, and the only `CancellationException` the
  budget throws is its caller's own (a cache's `close()` cancelling a wait in progress is that caller's cancellation, which refunds the wait).
- **Unit 10, `feeds` (#69):** nothing directly (`UrlFetcher` takes a `CallBudget`, `Unlimited` by default).
- **Unit 11, `http-docs` (#70):** it documents the budget from this unit's KDoc, README row and CHANGELOG entries, including the clock rules and their limit for
  operators.

## 5. Where the design refines the parent documents

None of these changes the unit's scope, a seam or its tier; each makes [`../architecture.md`](../architecture.md) §6.5 concrete against the code and the sources, and
each becomes a dated correction there once built. [`plan.md`](plan.md) §6.11 gives the reasons.

- **A backward clock step copies:** on a decrease of the wall index across a boundary, the current cell gains the sum of the cells above it, capped at the limit,
  and the higher cells stay. The architecture is silent on clock steps.
- **A snapshot stamped further ahead than `maxRestoreSkew`** has its stamp and pause ignored but keeps its window counts, where §6.5 ignores it entirely.
- **A reservation whose period the wall clock has left behind** is re-planned rather than granted against that period.
- **The restore** runs at the first of `acquire`, `utilisation()` and `flush()` (§6.5: the first `acquire`); a late restore adds the grants made since the start
  to the stored count (§6.5: `max(local, saved)`, which it equals when nothing was granted before the restore); a store that fails is retried at a bounded rate,
  with no write-ahead save before a restore succeeds and a failed save deferring the next.
- **Builder properties:** `storeTimeout` (2 s) and `storeRetryInterval` (30 s) join §6.5's list; `maxQueued` applies to each lane.
- **Events:** a seventh leaf, `Restored`; `BudgetEvent` an open root gated like `HttpFailure`; `Paused.source` a `PauseSource` and `StoreFailed.operation` a
  `StoreOperation`; events delivered after the ledger's lock is released.
- **The port:** `utilisation()` suspends; `close()` writes nothing and `flush()` works after it.
- **Signals:** `RateSignalsReader.Standard` takes the longest of several `Retry-After` lines and measures a date against the response's `Date`; `RetryAfter` is one
  `internal` HTTP-date parser with RFC 9110's grammar, three liberties and a saturated `delay-seconds`.
- **Time:** the wall clock decides and the monotonic source measures, both read once per lock section, with no anchor and no clamp; each wait is sliced to at most
  one minute.
- **The open points of §13:** the non-suspending `close()` of a `Mutex`-based ledger is a completed `Job()` latch raced against each waiter's wake signal, which
  settles this unit's row of [`../consequences.md`](../consequences.md) §7; and `max(signals.retryAfter, pausedFor)` is not redundant (§4 above), which answers half
  of unit 9's row.

## 6. Neighbouring issues

- **#58** (CI and repository practices): its CI would be the first place these tests run on every change. The Level 4c non-functional close-race and contention
  suites need several cores to open the windows they probe.
- **The IDE checks of #62 to #66** are still open; this unit's (completion of `WindowedCallBudget(windows) { }`, the opt-in error on an implementer of each of the
  five gated ports and on a subclass of `BudgetEvent`, the `@WebToolsDsl` scoping of the three builders) join them, at the next IntelliJ session.

## 7. Follow-ups this unit leaves

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

## 8. Known gaps

These hold for the unit as planned; the reconciliation after the build adds what the build leaves open.

- **A forward clock step that is wrong** (a clock set forward by mistake and left uncorrected, or a clock behind at start and stepped forward after grants were
  made) can under-count a hard window: no reading in common code tells it from a resume from sleep. The follow-up of §7 would detect it.
- **The copy rule for a backward step** is this design's own; a model and the unit's tests support it, no comparable project implements it, and a real clock step
  or suspend on a host is not exercised (the tests step a virtual wall clock).
- **While the store is down**, grants are not saved ahead (a failed save defers the next attempt by `storeRetryInterval`); a crash in that interval restores a count
  below the truth, by at most the grants of one interval. The in-process stop holds meanwhile.
- **A snapshot of an unknown version is dropped whole:** a library downgraded after a newer one wrote the store restores none of its counts.
- **A blocking store** holds the ledger's lock for as long as it blocks; `withTimeoutOrNull` cannot interrupt it, so the port requires a cancellable
  implementation.
- **Event order:** events of concurrent calls may reach the observer interleaved; each event describes itself.
- **Several processes** behind one address, or sharing one store, are invisible to a local budget; a snapshot store is last-writer-wins.
- **Provider rules declared in headers** (windows a server names at run time) are not modelled; readings for window names the budget does not have are ignored.
- **The `js` target compiles and runs nothing,** so `TimeSource.Monotonic` on `js` is not exercised; the plan's first `js` compile settles whether `select` with
  `onJoin` compiles there ([`plan.md`](plan.md) §3, assumption 1).
- **Cost at scale:** the cost per wake and of a re-plan at `maxQueued` waiters per lane is bounded by the Level 4c non-functional suite, not benchmarked across
  hosts.
- **Judgement defaults, not measurements:** the guard (1 s), `maxPause`, `maxQueued`, `persistAhead`, `maxRestoreSkew`, `storeTimeout`, `storeRetryInterval`, the
  one-minute wake slice and the cap of 16 cells per window.
