# Waiting, re-planning, cancelling and closing in `WindowedCallBudget` (kotlinx-coroutines 1.10.2, Kotlin 2.2.0)

## 1. Question

How should the waiters of a ledger guarded by a coroutine `Mutex` wait for their planned slot, be woken by a
re-plan, be refunded when cancelled, and be ended by a non-suspending, thread-safe `close()` that hands each
of them `Result.failure(HttpFailure.Closed("budget"))` as a value, using only common kotlinx-coroutines 1.10.2
and the Kotlin 2.2.0 common standard library (no atomicfu, no experimental API in production code), and what
runs under the lock and what after it. Architecture §13 (first bullet) lists this as not yet designed.

Requirements the answer meets (plain facts about the work):

- `close()` never suspends, never throws, is idempotent and is safe from any thread. After it, `acquire` and
  every waiter return `Closed("budget")` as a value, and no `CancellationException` reaches a waiter whose own
  coroutine is still active: the budget runs inside the response cache's load coroutine, where a
  cancellation thrown while that coroutine is active becomes an `IllegalStateException` failure for every
  caller (`webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/LoaderRunner.kt:26` and `:38`).
- After `close()`, `flush()`, `utilisation()`, `complete()` and `release()` keep working.
- A waiter's own cancellation is thrown as `CancellationException`, its reservation is refunded and the
  waiters behind it are re-planned; `complete()`/`release()` run under `NonCancellable` and work from a
  cancelled coroutine.
- Events are delivered outside the lock; an observer's exception is caught and logged; no write-ahead save
  before a restore succeeds; a failed restore is retried by a later `acquire` at a bounded rate.
- The budget owns no scope and no dispatcher; Level 2 runs on `VirtualClock` under `runTest` and asserts on
  completion state without joining.

## 2. Bottom line

- **Wait on a per-waiter conflated `Channel<Unit>` raced against the budget's close latch, under
  `withTimeoutOrNull(slot - now)`, and re-read the plan under the lock after every wake or timeout.**
  The race is `select<Unit> { closing.onJoin { }; waiter.wake.onReceive { } }`. Every piece is stable in
  1.10.2; the only experimental neighbour, `SelectBuilder.onTimeout`, is not used. A wake carries no data, so a
  lost or spurious wake is harmless; a signal sent between "read the plan" and "start waiting" is not lost
  because the channel is buffered.
- **`close()` is `closing.complete()` on a budget-owned `Job()` used as a latch** (completed normally, never
  cancelled). `Job` is thread-safe, `complete()` returns `true` for exactly one caller, and `isCompleted` is the
  closed flag: no `@Volatile`, no atomic, no lock. Waiters see the latch through `onJoin`, leave through the
  lock (removing themselves, no re-plan) and return `Closed` as a value. No `CancellationException` is involved,
  so nothing has to be told apart from the caller's own cancellation.
- **A waiter grants itself.** At its slot it takes the lock, checks `ensureActive()` before mutating,
  converts its reservation into a grant and returns. Nothing needs to run on behalf of a waiter, which is what
  lets the budget own no scope. A re-plan signals only waiters moved earlier or failed; a waiter moved later
  wakes at its old time, re-reads, and sleeps again.
- **Cancellation cleanup is `try/finally` around the wait loop** with `withContext(NonCancellable) {
  lock { if still Waiting: remove, re-plan } }`. A contended `Mutex.lock` inside `NonCancellable` does not
  throw and waits until the holder is done (measured); every lock holder is bounded by `storeTimeout`.
- **Under the lock: arithmetic, the restore's `load()` and the write-ahead `save()`, each bounded by
  `withTimeoutOrNull(storeTimeout)` and each placed before any ledger mutation in its section; after the lock:
  events.** Events are collected in a local list and emitted in a `finally` after unlock. Store calls convert
  a leaked inner `CancellationException` into a failure value with `currentCoroutineContext().ensureActive()`
  (the idiom `runLoader` already uses).
- **Thread-safe primitives available without an opt-in:** `Mutex`, `Job`/`CompletableJob`,
  `CompletableDeferred`, `Channel`, `MutableStateFlow` and `kotlin.concurrent.Volatile`. Not available:
  `kotlin.concurrent.atomics` (`@ExperimentalAtomicApi`, opt-in at `ERROR` level in 2.2.0). The recommended
  design needs only the `Mutex` and the close latch.
- A probe of the recommended shape (a one-window stand-in for the ledger, JVM, Kotlin 2.2.0, coroutines 1.10.2)
  passed all 33 checks; see F10.

## 3. Findings

Evidence labels: **documented** (source, KDoc or tests at the pinned tag), **common practice**, **measured**
(own probe, F10), **inference**.

### F1. `Mutex` gives the properties the lock sections rely on (documented, 1.10.2)

- `Mutex.lock` "is fair; suspended callers are resumed in first-in-first-out order", is cancellable with a
  prompt cancellation guarantee, "releases the lock if it was already acquired by this function before the
  `CancellationException` was thrown", and "does not check for cancellation when it is not suspended".
  So (a) a lock section either runs to its end or never starts; (b) an uncontended `lock()` in an already
  cancelled coroutine succeeds and runs its body, which is why the grant step must call `ensureActive()` itself
  (F5); (c) a contended `lock()` in a cancelled coroutine throws.
- It is "non-reentrant": an observer that calls `utilisation()` from `onEvent` suspends forever if events are
  emitted under the lock. This is the reason events leave the lock.
- Source: [`Mutex.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/sync/Mutex.kt)
  (`Mutex`, `Mutex.lock`, `withLock`); the class lives in `common`.

### F2. The wait primitive: a conflated channel plus a close latch (documented + measured)

The options, each judged against the requirements:

| Option | Stable in 1.10.2 | Wake on re-plan | Close reaches it | Tells wake from own cancellation | Verdict |
|---|---|---|---|---|---|
| Per-waiter `Channel<Unit>(CONFLATED)` + `withTimeoutOrNull(remaining)` + `select` with `closing.onJoin` | yes (every piece) | `trySend`, never suspends, always succeeds on a conflated channel; buffered, so no lost wake | `Job.complete()`; `onJoin` "never fails" | not needed: nothing is cancelled except by the caller, and `withTimeoutOrNull` rethrows the caller's cancellation | **chosen** |
| Per-waiter `CompletableDeferred` signalled by a re-plan, `await()` under `withTimeoutOrNull` | yes | one-shot: needs a new deferred per wake, replaced under the lock; a signal racing the replacement is lost unless the swap is under the same lock | only via a parent `Job` that is cancelled, so `await()` throws `CancellationException` into an active coroutine | only by `ensureActive()` after catching | works, but more moving parts and a cancellation-based close |
| Plain `delay(remaining)` then re-check | yes | none: a waiter moved earlier sleeps through its new slot (the "wasted slot" the stack-level findings measured) | none | n/a | rejected |
| `select { ...; onTimeout(d) {...} }` | **no**: `SelectBuilder.onTimeout` is `@ExperimentalCoroutinesApi` ("This is an experimental api") | yes | yes | n/a | rejected for the experimental clause only; the same shape with `withTimeoutOrNull` outside is stable |
| Budget-wide `MutableStateFlow<Long>` version bumped on every re-plan and on `close()` | yes | wakes every waiter on every re-plan (each takes the lock to re-read) | yes (bump) | not needed | valid fallback; O(waiters) lock sections per cancel |
| `@Volatile` flag plus per-waiter signal | yes | yes | `close()` cannot reach the signals: the waiter list is guarded by the lock it may not take | n/a | rejected |
| `Mutex.tryLock` in `close()` to sweep the waiters | yes | n/a | fails whenever the lock is held (during any store call), so `close()` would have to give up or spin | n/a | rejected |
| A budget-owned scope with a ticker/timer coroutine that grants | yes | yes | yes | n/a | rejected: the budget owns no scope or dispatcher |

- `Channel.CONFLATED`: "Sending to this channel never suspends, and `trySend` always succeeds" and the
  receiver "always gets the last element sent". A re-plan may therefore signal a waiter that is not waiting yet;
  the signal stays until consumed. Source: [`Channel.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/channels/Channel.kt) (`Channel.Factory.CONFLATED`).
- `Job.onJoin`: "This clause never fails, even if the job completes exceptionally." `select`, `Job.onJoin` and
  `ReceiveChannel.onReceive` carry no experimental annotation; `select` itself is documented as cancellable with a
  "prompt cancellation guarantee". Source: [`Job.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/Job.kt), [`Select.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/selects/Select.kt), [`OnTimeout.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/selects/OnTimeout.kt).
- `withTimeoutOrNull` returns `null` only for its own `TimeoutCancellationException`; "otherwise propagate it
  upstream" (the caller's cancellation, an outer timeout). It returns `null` at once, without suspending or
  checking cancellation, when the timeout is not positive, so the loop must never wait on a non-positive
  remainder (the grant branch handles it first). `Duration.toDelayMillis` rounds a sub-millisecond remainder
  **up**, so a wait is never zero-length. Source: [`Timeout.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/Timeout.kt), [`Delay.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/Delay.kt).
- Under `runTest`: `withTimeoutOrNull` calls `context.delay.invokeOnTimeout`, and `TestDispatcher` is
  `CoroutineDispatcher(), Delay, DelayWithTimeoutDiagnostics` with `invokeOnTimeout` registering a scheduler
  event, so the wait is virtual whenever the caller runs on the `VirtualClock` dispatcher (measured, F10). On a
  caller dispatcher that is not a `Delay` (for example `Dispatchers.Unconfined`), `delay` falls back to
  `DefaultDelay`; this affects real time only. Source: [`TestDispatcher.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestDispatcher.kt).
- Why re-reading makes the design robust (inference, confirmed by the probe): the plan lives under the lock and
  every wake, timeout or spurious signal re-reads it. A wake lost to the "asynchronous timeout" (F6), a stale
  wake after a later move, or a double signal changes nothing but one extra lock section.
- Cost per wake: one lock section, one `TimeoutCoroutine`, one `select` instance. Not benchmarked (Gaps).

### F3. `close()` as a latch, not a cancellation (documented + measured)

- `CompletableJob.complete()`: "The result is `true` if this job was completed as a result of this invocation
  and `false` otherwise (if it was already completed)." `Job`'s functions "are thread-safe and can be safely
  invoked from concurrent coroutines without external synchronization." Eight racing threads: exactly one
  `complete()` returned `true` (measured, S8). Source: [`CompletableJob.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/CompletableJob.kt), [`Job.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/Job.kt).
- A `Job()` with no children is `complete` at once; `isCompleted` is then `true` and `isCancelled` stays
  `false`. A waiter that registers `onJoin` after `close()` ran is selected immediately, so a call "between the
  lock and its wait" cannot miss the close (measured, S3b and S9).
- Why not cancel: a per-waiter signal that is a child of a budget `Job` which `close()` cancels makes `await()`
  throw `CancellationException` into an active coroutine, indistinguishable by type from the caller's own, and
  forces the `ensureActive()` classification onto the hot path. A normally completed latch needs none (inference
  from `Deferred.await`'s documented two cancellation cases, already cited in
  `66-response-cache/research/load-lifecycle.md` F4).
- `invokeOnCompletion` is a possible fan-out (`closing.invokeOnCompletion { wake.trySend(Unit) }`) but its KDoc
  says an installed handler "should not throw any exceptions. If it does, they will get caught, wrapped into
  `CompletionHandlerException`, and rethrown", and for a non-coroutine `Job` that rethrow would surface from
  `close()`. `select` with `onJoin` keeps consumer code off the closer's thread, so `close()` runs no code of ours.
- `close()` needs no catch: it runs `closing.complete()` and, for the caller that won, one log line.

### F4. Cleanup from a cancelled coroutine: `withContext(NonCancellable) { lock }` (documented + measured)

- `withContext` begins with `newContext.ensureActive()` on the *new* context, and `NonCancellable` is "a
  non-cancelable job that is always active", so the call proceeds in a cancelled coroutine, and the same
  dispatcher means no re-dispatch (`UndispatchedCoroutine`, no new scheduler event under `runTest`). The
  coroutines guide: "in the rare case when you need to suspend in a cancelled coroutine you can wrap the
  corresponding code in `withContext(NonCancellable) {...}`".
- Measured (S6, S4): in a cancelled coroutine an uncontended `withLock` runs; a contended `withLock` throws
  `CancellationException`; a contended `withLock` inside `withContext(NonCancellable)` waited and then ran, and
  the cancelled waiter stayed incomplete (cleanup pending) until the holder released at virtual 15 s, then ended
  cancelled. It cannot hang unless the holder never releases: every holder is a short arithmetic section or a
  store call bounded by `storeTimeout`.
- `finally { withContext(NonCancellable) { lock } }` must be outside any `withLock` of the same coroutine
  (the mutex is not reentrant). The wait loop's lock sections end before the `finally` runs, because
  `withLock` unlocks in its own `finally`.
- `complete()` and `release()` use the same shell. Idempotency lives under the lock: the permit's state is a
  plain field read and written inside the critical section, so no atomic is needed and a second concurrent
  caller sees the first one's result.

### F5. The granted-but-undelivered window (documented + measured)

- The waiter, not another coroutine, moves itself from `Waiting` to `Granted`, inside its own lock section and
  with no suspension point between the transition and `return`. Cancelled while parked on the lock, it gets
  `CancellationException` before the section starts (F1c) and is still `Waiting` for the cleanup: refunded and
  the queue re-planned (measured, S4). Cancelled before it resumed from the timer, the prompt cancellation
  guarantee of `withTimeoutOrNull`/`select` throws.
- Adding `currentCoroutineContext().ensureActive()` as the first statement of the grant section (inside the
  lock, before any mutation) closes the window where an uncontended `lock()` would otherwise grant a permit to
  an already cancelled coroutine (F1b). What remains is a cancellation arriving after that check on another
  thread: the caller owns the permit then and `withPermit`'s `finally` completes it as spent, the behaviour the
  stack-level findings measured (`../../research/call-budget.md` F4, F8).

### F6. Store calls under the lock: virtual-time behaviour and the result-drop hazard (documented + measured)

- `withTimeoutOrNull(2.seconds) { delay(5.seconds) }` inside `withLock` under `runTest`: the timeout fired at
  exactly 2000 ms of virtual time, the lock was released, the next acquirer was served, and the test did not
  wait in real time (measured, S5). A holder cancelled inside the critical section releases the mutex (S5).
- "The timeout event in `withTimeout` is asynchronous with respect to the code running in its block and may
  happen at any time, even right before the return from inside of the timeout block" (coroutines guide). A
  `load()` that completed but whose result was dropped must be treated as a failure and retried; a `save()`
  that completed but reported a timeout must be treated as not persisted (conservative: the mark is not
  advanced, the next save repeats it). Ktor's CIO engine hit exactly this and assigns the result inside the
  block (`Endpoint.connectWithTimeout`, F8).
- `withTimeoutOrNull` cannot interrupt blocking code: "Coroutine cancellation is cooperative." A `BudgetStore`
  whose `load`/`save` block a thread keeps the lock for as long as it blocks; the port's KDoc must say that a
  store implementation is cancellable.
- A store that leaks a `TimeoutCancellationException` (its own inner `withTimeout`) is not ours, so
  `withTimeoutOrNull` rethrows it; unclassified it would reach an active caller. The `catch (e:
  CancellationException) { currentCoroutineContext().ensureActive(); failure }` shape turned it into a failure
  value in an active caller and still propagated the caller's own cancellation (measured, S7); it is the idiom
  of `runLoader` (`LoaderRunner.kt:38`) and of the cache's `awaitEntry`
  (`jvmMain/.../CaffeineResponseCache.kt:166-171`).

### F7. Thread-safe primitives in common code on Kotlin 2.2.0 (documented)

| Primitive | Opt-in in 2.2.0 / 1.10.2 | Thread-safe | Use here |
|---|---|---|---|
| `Mutex` | none | yes, fair, non-reentrant | the ledger lock |
| `Job()` / `CompletableJob`, `CompletableDeferred` | none (the `CompletableDeferred` interface is subclass-gated, which does not affect use) | yes; single winner for `complete` | the close latch; `isCompleted` as the flag |
| `Channel(CONFLATED)` | none | yes | per-waiter wake |
| `MutableStateFlow.compareAndSet`, `update` | none | "All methods of state flow are thread-safe" | not needed; the fallback broadcast in F2 |
| `kotlin.concurrent.Volatile` | none: `@SinceKotlin("1.9")`, no opt-in marker; "has effect in Kotlin/JVM and Kotlin/Native" | volatile field only; reads-modify-writes are not atomic | not needed; usable for a flag set once |
| `kotlin.concurrent.atomics.AtomicInt`, `AtomicReference`, ... | **`@ExperimentalAtomicApi`**, `@SinceKotlin("2.1")`, `@RequiresOptIn(level = ERROR)`, with the warning that a library "might become binary incompatible" | yes | excluded |
| `Mutex.tryLock` | none | yes, but may fail | not for `close()` |

Sources: [`Volatile.kt`](https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/concurrent/Volatile.kt),
[`ExperimentalAtomicApi.kt`](https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/concurrent/atomics/ExperimentalAtomicApi.kt),
[`StateFlow.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/flow/StateFlow.kt).
The design keeps every mutable ledger field under the `Mutex`, which on the JVM gives the `synchronized`-style
happens-before the `Mutex` KDoc states ("An unlock operation on a Mutex happens-before every subsequent
successful lock on that Mutex"), so the only cross-thread datum is the latch.

### F8. How comparable projects wait and shut down (common practice, read at pinned tags)

See section 4.

### F9. Retrying a failed restore at a bounded rate (inference, from F1-F6 and the injected `TimeSource`)

- State under the lock: `NotAttempted | Restored | Failed(at: TimeMark)`. A later `acquire` in `Failed` state
  retries only when `at.elapsedNow() >= restoreRetryInterval`; between attempts it skips the restore at the
  cost of a field read. The mark comes from the injected monotonic `TimeSource`, so under `VirtualClock` the
  interval is virtual and a test steps over it with `advanceBy`.
- Because the attempt runs under the lock, the retry is single-flight: concurrent acquirers queue behind one
  `load()`. A down store therefore costs the queue at most one `storeTimeout` per interval, not one per call.
- `utilisation()` and `flush()` make the first attempt (and `flush()` an unconditional one, section 6.8);
  only `acquire` retries on the gate.
- The write-ahead `save` has the same exposure: a failed save leaves the persisted mark unadvanced, so the
  next grant would try again, each costing up to `storeTimeout` under the lock. Gate it the same way (Considerations).

### F10. Probe (measured)

A throwaway JVM project outside the repository: Kotlin 2.2.0, kotlinx-coroutines-core and -test 1.10.2, Gradle
9.7.1, JDK 23, `runTest` with the test's own `testScheduler.timeSource` as the `TimeSource`. `MiniBudget` is a
one-lane, one-window stand-in for the ledger (one grant per `spacing`) carrying exactly the concurrency shape
recommended in section 6: `Waiter` with a conflated channel, a `Mutex`, a `Job()` latch, `select { closing.onJoin;
wake.onReceive }` under `withTimeoutOrNull`, self-grant, `ensureActive()` in the grant section, `NonCancellable`
cleanup. Result: 33 of 33 checks passed on each of five runs after the one fix noted under S2. What was checked:

- S1 FIFO plus cancel: A granted at once; B, C, D planned at 10, 20, 30 s; B cancelled at 1 s; C granted at exactly
  10 s and D at exactly 20 s (both moved up, no wasted slot); nothing granted early (9.999 s); queue empty.
- S2 pause moves a waiter later: no signal sent; the old timer fired at 10 s, the waiter re-read, slept again and
  was granted at 25 s. S2b: a pause beyond a waiter's cap failed it at once with a value at unchanged virtual time.
  **A defect found and fixed in the probe:** the first version let the initial plan signal the waiter itself
  (`slot` started at "infinity", so the first plan looked like a move earlier) and cost one spurious lock section
  per call; the fix is a `planned` flag so only an already-waiting waiter moved earlier is signalled.
- S3 close: three waiters ended with the failure as a value, none cancelled, queue emptied by the waiters
  themselves, `close()` twice harmless, later `acquire` returned `Closed`, a caller cancelled before calling still
  got `CancellationException` after `close()`. S3b: a call parked on the mutex while `close()` ran returned `Closed`.
- S4 cancel while queued on the lock: cleanup waited for the lock (waiter incomplete at 10 s, ended cancelled at
  15 s), the waiter behind it was re-planned and granted at 15 s.
- S5 `withTimeoutOrNull` inside `withLock`: virtual, exact; cancelled holder releases the mutex.
- S6 `withLock` from a cancelled coroutine: uncontended ran, contended threw, contended inside `NonCancellable`
  waited then ran.
- S7 leaked inner timeout became a failure in an active caller; the caller's own cancellation still propagated.
- S8 eight threads racing `Job().complete()`: one winner, 100 `invokeOnCompletion` handlers ran once each.
- S9 real threads: 300 waiters on `Dispatchers.Default`, `close()` from a plain thread while 60 were being
  cancelled: 240 `Closed` values, 60 `CancellationException`, none other, no hang, queue empty. This race is
  loose (most cancels land before the close); it is a smoke test, not a proof.

Not measured: the cost per wake, any `js` run (see Gaps), contention at thousands of waiters.

## 4. How comparable projects do it

- **kotlinx-coroutines `Mutex` 1.10.2** ([`Mutex.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/sync/Mutex.kt)):
  the lock is the queue, fair FIFO, cancellable with a prompt cancellation guarantee that returns the lock if it
  was acquired before the exception; no `close`. The budget uses it only for the ledger and builds the timed,
  signalled wait on top.
- **Kord 0.17.0, `IntervalRateLimiter`** ([`TimeSourceIntervalRateLimiter.consumeUnderLock`](https://github.com/kordlib/kord/blob/0.17.0/common/src/commonMain/kotlin/ratelimit/TimeSourceIntervalRateLimiter.kt),
  [`AbstractIntervalRateLimiter.consume`](https://github.com/kordlib/kord/blob/0.17.0/common/src/commonMain/kotlin/ratelimit/AbstractIntervalRateLimiter.kt)):
  `mutex.withLock { consumeUnderLock() }` with `delay(interval - elapsed)` inside the lock. Waiters wait *on the
  lock*, so there are no reservations, no early wake, no cap and no close; a cancelled waiter just leaves the
  queue. Fine for a throttle, not for planned slots, caps or lanes. Common code, `TimeSource`-driven, as here.
- **Kord 0.17.0, REST `AbstractRateLimiter.Bucket`** ([`AbstractRateLimiter.kt`](https://github.com/kordlib/kord/blob/0.17.0/rest/src/commonMain/kotlin/ratelimit/AbstractRateLimiter.kt)):
  `suspend fun awaitAndLock() { mutex.lock(); ...; reset.value.await() }`, unlocked only by `RequestToken.complete`.
  A waiter cancelled during the `delay` has no `finally { unlock() }` on that path, and
  `ExclusionRequestRateLimiter.await` does the same with `sequentialLock` (reading of the code; I did not
  execute it). That is the shape that fails under cancellation: a lock held across a cancellable wait with the
  release owned by a later call. It also has no `close()`. The budget holds no lock for a permit
  (`../../research/call-budget.md` F8).
- **resilience4j-kotlin 2.4.0** ([`RateLimiter.awaitPermission`](https://github.com/resilience4j/resilience4j/blob/v2.4.0/resilience4j-kotlin/src/main/kotlin/io/github/resilience4j/kotlin/ratelimiter/RateLimiter.kt)):
  `val waitTimeNs = reservePermission(); when { waitTimeNs > 0 -> delay(TimeUnit.NANOSECONDS.toMillis(waitTimeNs)) ... }`.
  Reserve, then plain `delay`: a cancelled waiter's reservation stays charged and nobody behind it moves up
  (`AtomicRateLimiter` in 2.4.0 has no cancel-reservation operation; its waits are `parkNanos` loops). This is
  the "refund only" and "no re-plan" case that the stack-level findings measured as wasting slots.
- **Ktor 3.6.1 CIO** ([`ConnectionFactory.connect`](https://github.com/ktorio/ktor/blob/3.6.1/ktor-client/ktor-client-cio/common/src/io/ktor/client/engine/cio/ConnectionFactory.kt),
  [`Endpoint.connectWithTimeout`](https://github.com/ktorio/ktor/blob/3.6.1/ktor-client/ktor-client-cio/common/src/io/ktor/client/engine/cio/Endpoint.kt)):
  kotlinx `Semaphore` permits released in `catch (cause: Throwable)` ("a failure or cancellation"); and the
  comment "`withTimeout` drops the block's result when it is cancelled right as the connect completes, and the
  connected socket would leak together with its connection permits", fixed by assigning the socket inside the
  block and releasing it in the handler. The same race, in the same library's own words, as F6 and F5.
- **Arrow Resilience 2.3.0 `CircuitBreaker`** ([`CircuitBreaker.kt`](https://github.com/arrow-kt/arrow/blob/2.3.0/arrow-libs/resilience/arrow-resilience/src/commonMain/kotlin/arrow/resilience/CircuitBreaker.kt)):
  a state machine on `arrow.atomic.Atomic` with `compareAndSet`; it refuses fast with `ExecutionRejected`
  (`protectEither`) and keeps no waiters, so it has nothing to cancel or close. Relevant only as the "refusal is a
  value" convention and as the reminder that a multiplatform CAS needs a dependency (`arrow-atomic`) or the
  experimental stdlib atomics.
- **kotlinx-coroutines `Channel`** ([`Channel.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/channels/Channel.kt)):
  the library's own non-suspending, idempotent, any-thread `close()` that ends receivers; `receiveCatching`
  reports it as a value. The latch is the same contract expressed on a `Job`.
- **This module:** `CaffeineResponseCache.close()` (`jvmMain/.../CaffeineResponseCache.kt:259`) uses an
  `AtomicBoolean` plus a sweep that completes waiters as values and `ensureActive()` before the closed check
  (`:123`); `CallBridge.settle` resumes a continuation exactly once with a value from a `finally`
  (`jvmMain/.../CallBridge.kt:61`). The budget lives in `commonMain`, which has neither `AtomicBoolean` nor a
  sweepable registry under a non-takeable lock, hence the latch.

## 5. Pitfalls and regrets

1. **A lock held across a cancellable wait with a later release** (Kord's REST buckets). The wait here happens
   after the lock; the lock is held only for arithmetic and bounded store calls.
2. **Reserve-then-`delay` without refund or re-plan** (resilience4j-kotlin): every cancelled waiter wastes its
   slot and the queue behind it does not move up.
3. **Throwing `CancellationException` for a close.** A cancelled signal makes the waiter throw into a coroutine
   that is still active; in the cache's load coroutine this becomes `IllegalStateException` for every caller
   (`LoaderRunner.kt:26-38`).
4. **Relying on `Mutex.lock` to notice cancellation.** It does not when uncontended (F1); put
   `ensureActive()` in the grant section.
5. **Waiting on a non-positive remainder.** `withTimeoutOrNull(<= 0)` returns `null` immediately and checks
   nothing; a loop that sleeps on `slot - now <= 0` spins without a cancellation point. Grant first.
6. **A newly planned waiter signalling itself** (found in the probe): one wasted lock section per call.
7. **The result-drop race of `withTimeout`** (Ktor 3.6.1, coroutines guide): never treat a store call that ended
   in a timeout as successful, and never commit a ledger change on a result that may have been dropped.
8. **Mutating, then suspending, inside a lock section.** A cancellation at the suspension point leaves a
   half-applied change. Suspend first (load, save), mutate after, with no suspension point between the last
   check and the end of the section.
9. **Holding the lock across a store that never returns.** `withTimeoutOrNull` cannot interrupt blocking code;
   the port must say a store is cancellable. A store that is down but responsive costs one `storeTimeout` per
   attempt, so gate attempts (F9).
10. **Emitting events under the lock.** Deadlocks an observer that calls back into the budget (non-reentrant
    `Mutex`) and lets a slow observer stall every acquirer. Events from different concurrent calls can reach the
    observer in a different order than the ledger changes; state this in the observer's KDoc.
11. **Catching `Exception` around an observer is not the module's `catchNonFatal`.** `catchNonFatal` rethrows a
    `CancellationException`; `onEvent` is not a suspend function, so a `CancellationException` out of it cannot
    be the caller's cancellation and must be logged like any other exception. An `Error` still propagates.
12. **Flushing counts before a restore succeeded.** `flush()` writes the in-process counts; if the restore failed,
    the stored counts may be higher and would be overwritten, under-counting a billing day after the next
    restart. Retry the restore inside `flush()` and fail the `Result` instead of saving (Considerations).
13. **Waking everyone on every re-plan.** Signalling only waiters moved earlier (or failed) keeps a cancel at
    O(waiters) re-plan work and O(moved) wakes.

## 6. Recommendation for this repository

Use the conflated-channel-plus-close-latch design (F2, F3): the ledger and every mutable field behind one
`Mutex`; each waiter a record with a conflated `Channel<Unit>` and plan fields that are read and written only
under the lock; `close()` a `Job().complete()`; each waiter grants itself under the lock after
`ensureActive()`; cleanup in a `finally` under `NonCancellable`. Reject the cancellation-based close
(`ensureActive()` classification on the hot path, a cancelled signal that looks like the caller's) and the plain
`delay` (cannot move a waiter earlier). No new dependency, no experimental API, and the file compiles as common
code.

### 6.1 State and records (inside `WindowedCallBudget`, all in `commonMain`)

```kotlin
private val ledgerLock = Mutex()                       // guards EVERY mutable field below
private val closing: CompletableJob = Job()            // close latch: completed normally, never cancelled
private var restore: RestoreState = RestoreState.NotAttempted   // guarded; Restored | Failed(at: TimeMark)
private val queues: Map<Lane, ArrayDeque<Waiter>>       // guarded; one FIFO per lane, maxQueued each

private enum class WaiterState { Waiting, Granted, Failed, Gone }

private class Waiter(val cost: Int, val lane: Lane, val maxWait: Duration) {
    val wake = Channel<Unit>(Channel.CONFLATED)         // "re-read your plan"; sticky, lossless, value-free
    // Everything below: read and written only inside ledgerLock.
    var planned = false                                 // false until the first plan, so a first plan never signals
    var slot: TimeMark? = null                          // planned grant time (clock model: see ledger-time findings)
    var state = WaiterState.Waiting
    var failure: HttpFailure? = null                    // BudgetExhausted set by a re-plan, state = Failed
}

private sealed interface Step {
    class Done(val result: Result<Permit>) : Step
    class Sleep(val wait: Duration) : Step              // strictly positive
}
```

### 6.2 `acquire`: fast path, planning under the lock, the wait loop outside it

```kotlin
override suspend fun acquire(cost: Int, lane: Lane, maxWait: Duration): Result<Permit> {
    require(cost >= 1) { ... }; require(!maxWait.isNegative()) { ... }       // programmer errors throw
    currentCoroutineContext().ensureActive()           // a cancelled caller gets CancellationException, even after close()
    if (closing.isCompleted) return closed()
    val waiter = Waiter(cost, lane, maxWait)
    val events = ArrayList<BudgetEvent>(2)
    val early: Result<Permit>? = try {
        ledgerLock.withLock {
            currentCoroutineContext().ensureActive()
            if (closing.isCompleted) return@withLock closed()
            ensureRestored(events, force = false)      // load() under storeTimeout; failure -> StoreFailed + gate; suspends BEFORE any mutation
            admit(waiter, events)                      // fits now: persistAhead (suspends) then charge, Result.success(permit)
                                                       // over cap / QueueFull / CostExceedsLimit: Result.failure(BudgetExhausted)
                                                       // else enqueue + replan(events); null = enqueued
        }
    } finally { emit(events) }
    return early ?: awaitSlot(waiter)
}

private suspend fun awaitSlot(w: Waiter): Result<Permit> {
    var settled = false
    try {
        while (true) {
            val events = ArrayList<BudgetEvent>(2)
            val step: Step = try {
                ledgerLock.withLock {
                    when {
                        w.state == WaiterState.Failed -> Step.Done(Result.failure(w.failure!!))      // set by a re-plan, already unlinked
                        closing.isCompleted -> { unlink(w); w.state = WaiterState.Gone; Step.Done(closed()) }   // no re-plan: everyone is leaving
                        else -> {
                            currentCoroutineContext().ensureActive()      // before any mutation: a cancelled caller is not granted
                            val remaining = remainingOf(w)               // slot - now, from the ledger's clock model
                            if (!remaining.isPositive()) Step.Done(Result.success(grantLocked(w, events)))   // persistAhead may suspend, THEN charge
                            else Step.Sleep(remaining)
                        }
                    }
                }
            } finally { emit(events) }
            when (step) {
                is Step.Done -> { settled = true; return step.result }
                is Step.Sleep -> withTimeoutOrNull(step.wait) {        // returns null on timeout; the caller's own cancellation propagates
                    select<Unit> { closing.onJoin { }; w.wake.onReceive { } }   // close is listed first: it wins a tie
                }                                                      // the result is ignored: every wake re-reads the plan
            }
        }
    } finally {
        if (!settled) withdraw(w)                                      // CancellationException or any throwable: refund + re-plan
    }
}
```

### 6.3 The re-plan wake-up (under the lock; never suspends)

```kotlin
private fun replan(lane: Lane, events: MutableList<BudgetEvent>) {
    // recompute slots in queue order from the ledger; for each waiter w:
    //   newWait > w.maxWait  -> w.state = Failed; w.failure = BudgetExhausted(...); unlink(w); w.wake.trySend(Unit)
    //   else if (w.planned && newSlot is earlier than w.slot) w.wake.trySend(Unit)   // only a move EARLIER needs a signal
    //   w.slot = newSlot; w.planned = true
    // A move LATER sends nothing: the old timer fires, the waiter re-reads, finds the later slot and sleeps again.
}
```

### 6.4 Cancellation and refund

```kotlin
private suspend fun withdraw(w: Waiter) = withContext(NonCancellable) {
    val events = ArrayList<BudgetEvent>(1)
    try {
        ledgerLock.withLock {
            if (w.state == WaiterState.Waiting) {              // idempotent: a Granted/Failed/Gone waiter is left alone
                unlink(w); w.state = WaiterState.Gone
                if (!closing.isCompleted) replan(w.lane, events)
            }
        }
    } finally { emit(events) }
}
```

The caller's `CancellationException` then propagates from `finally`. `withdraw` is never called while the same
coroutine holds `ledgerLock`.

### 6.5 `close()`

```kotlin
override fun close() {
    if (closing.complete()) log.info("closed")             // thread-safe, idempotent, never suspends; no lock; no handler of ours runs
}
```

Nothing else is needed: waiters learn of it through `onJoin`, and the fast path through `closing.isCompleted`.
A call still queued on the lock when `close()` ran takes the lock, sees the latch in its first check and returns
`closed()`; a waiter between its lock section and its wait selects `onJoin` at once because the job is already
complete. `closed()` is `Result.failure(HttpFailure.Closed("budget"))`.

### 6.6 `complete()` / `release()`

```kotlin
private inner class WindowedPermit(override val cost: Int, override val lane: Lane, private val charge: Charge) : Permit {
    private var state = PermitState.Open                    // guarded by ledgerLock: idempotency without an atomic
    override suspend fun complete() = complete(RateSignals.None)
    override suspend fun complete(signals: RateSignals) = settle { if (state == Open) { state = Completed; applySignals(signals, events) } }
    override suspend fun release() = settle { if (state == Open) { state = Released; refund(charge); replan(lane, events) } }

    private suspend inline fun settle(crossinline body: () -> Unit) = withContext(NonCancellable) {
        val events = ArrayList<BudgetEvent>(2)
        try { ledgerLock.withLock { body() } } finally { emit(events) }
    }
}
```

`applySignals` re-plans only if it changed something (none for empty signals). The mutex has no closed state, so
`complete`/`release`/`flush`/`utilisation` work after `close()`; after `close()` there are no waiters, so the
re-plan is a no-op.

### 6.7 Where events and store calls run

| Step | Where | Notes |
|---|---|---|
| Plan, grant, refund, re-plan, restore merge, ledger reads | under `ledgerLock`, synchronous | no suspension point between the last check and the mutation |
| `store.load()` (restore) | under `ledgerLock`, `withTimeoutOrNull(storeTimeout)` through `storeCall` | one flight; suspends before the merge; failure -> `StoreFailed`, `restore = Failed(now)`, grants continue from the in-process count |
| `store.save(...)` (write-ahead, `flush`) | under `ledgerLock`, same wrapper | write-ahead only when `restore is Restored` and the grant would pass the persisted mark; mark advances only on success |
| `emit(events)` (log, then `observer.onEvent`) | after unlock, in the `finally` of the call that produced them | `try { observer.onEvent(e) } catch (t: Exception) { log.warn(...) }` catches a `CancellationException` too (not a suspend call) |

```kotlin
private suspend fun <T : Any> storeCall(block: suspend () -> T): Result<T> = try {
    val value = withTimeoutOrNull(storeTimeout) { block() }          // null = timed out, or the result was dropped: a failure
    if (value == null) Result.failure(StoreTimeoutException()) else Result.success(value)
} catch (e: CancellationException) {
    currentCoroutineContext().ensureActive()                         // the caller's own cancellation propagates
    Result.failure(IllegalStateException(STORE_CANCELLED_MESSAGE, e))   // an inner cancellation the store leaked is a store failure
} catch (e: Exception) {
    Result.failure(e)
}
```

(`save` returns `Unit`, which is non-null, so `null` can only mean a timeout.)

### 6.8 The bounded restore retry

```kotlin
// under ledgerLock; true when the stored counts are merged in
private suspend fun ensureRestored(events: MutableList<BudgetEvent>, force: Boolean): Boolean {
    when (val s = restore) {
        RestoreState.Restored -> return true
        is RestoreState.Failed -> if (!force && s.at.elapsedNow() < restoreRetryInterval) return false
        RestoreState.NotAttempted -> {}
    }
    storeCall { store.load() }
        .onSuccess { snapshot -> mergeAdditively(snapshot, events); restore = RestoreState.Restored }   // Restored event
        .onFailure { restore = RestoreState.Failed(timeSource.markNow()); events += StoreFailed(...) }
    return restore == RestoreState.Restored
}
```

`acquire` calls it with `force = false` (gated retry); `utilisation()` and `flush()` call it for the first attempt
(`flush()` with `force = true`, and it returns `Result.failure` without saving if the restore still fails).
`restoreRetryInterval` is a constant in this recommendation (Considerations).

### 6.9 Level 2 tests on `VirtualClock`

Files under `com.spartanlabs.testing.component.webtools.http`, one class per behaviour group, using a launched
call that records its `Result`/`Throwable` and is asserted on by completion state, never joined (a sibling of
`Waiter`, for `CallBudget`). Every test drains with `clock.advanceBy(Duration.ZERO)`; `FakeBudgetStore` with a
`delay` is the lock holder wherever a test needs the lock held (a hard window's write-ahead save), so no test hook
is added to the budget.

1. **Re-plan earlier.** Window of 2 per minute; two granted, three waiting; cancel the first waiter at 10 s;
   assert the next two complete at exactly the new slots and not 1 ms before (probe S1).
2. **Pause later.** A `complete` with a retry-after moves a waiter; assert it completes at the pause end and not at
   its old slot, with no signal expected (S2).
3. **Over-cap after re-plan.** Pause beyond a waiter's `maxWait`: it fails at once with `BudgetExhausted`, virtual
   time unchanged (S2b).
4. **Close ends waiters with values.** Several waiters; `close()` twice; after `advanceBy(ZERO)` every call
   `isCompleted`, none `isCancelled`, each `exceptionOrNull() is HttpFailure.Closed` with `component == "budget"`,
   `utilisation().waiting == 0`; a later `acquire` is `Closed`; a call cancelled before it started still throws
   `CancellationException` after `close()` (S3).
5. **Close while a call is parked on the lock.** Hold the lock with a delayed save; start an `acquire`; `close()`;
   release the lock: the call returns `Closed` (S3b). Also close between lock sections (a waiter already planned).
6. **Cancel while queued on the lock.** The waiter's slot arrives while a save holds the lock; cancel it; assert it
   is not complete until the lock frees, then `isCancelled`, and that the waiter behind it is granted at the
   re-planned time (S4).
7. **`complete`/`release` from a cancelled coroutine.** `launch { try { delay(..) } finally { permit.complete(signals) } }`
   cancelled while the lock is held: after release the signals are applied, usage and pause are as the server
   said, and no exception reached the coroutine; both calls idempotent, and after `close()`.
8. **After `close()`.** `flush()` writes exact counts (counts include permits completed or released after the
   close), `utilisation()` reads.
9. **Events outside the lock.** An observer that calls `utilisation()` from `onEvent` does not hang; an observer that
   throws (a `RuntimeException`, and a `CancellationException`) does not fail the grant and is logged.
10. **Store timeout is virtual.** `FakeBudgetStore(delay = 5.seconds)`, `storeTimeout = 2.seconds`: a write-ahead
    grant proceeds at exactly 2 s with `StoreFailed`; a store that leaks a `TimeoutCancellationException` yields
    `StoreFailed`, never a `CancellationException` to the caller (S5, S7).
11. **Restore gate.** `load()` fails: `StoreFailed`, grants proceed, no `save` was made; a second `acquire` inside the
    interval makes no `load`; after `advanceBy(interval)` the next `acquire` loads again; success merges
    additively and write-ahead resumes. Concurrent first calls (`acquire`, `acquire`, `utilisation`) make one `load`.
12. **Cancelled caller never receives a permit.** An already cancelled call, and a cancel at the grant instant (a
    waiter parked on the lock when its slot arrives), end with `CancellationException` and an unchanged ledger.
13. **`maxQueued` per lane.** Fill one lane to its cap; the next call on that lane gets `QueueFull`, the other lane
    still queues.
14. **Level 4c smoke (real threads, not Level 2):** the S9 shape, many waiters on `Dispatchers.Default`, `close()`
    from a plain thread racing cancels: every outcome is `Closed` or `CancellationException`, queue empty, bounded
    by a real timeout.

### 6.10 Considerations (open trade-offs with a recommendation)

- **Restore retry interval: constant or builder option.** Option A: an internal constant (30 s). Option B: a
  `restoreRetryInterval` builder option, default 30 s. The library's own rule is to parameterise policy a
  consumer might disagree with, but the builder's option list is fixed. Recommend A until a consumer needs it
  (additive to open later); document the number in the KDoc.
- **Gate failed saves too.** Without a gate, a down-but-responsive store adds `storeTimeout` to every grant that
  passes the persisted mark. Recommend the same monotonic gate for the write-ahead save (skip the save, keep the
  in-process stop, retry after the interval); the persisted mark then lags, which the write-ahead margin already
  tolerates only if the lag is bounded, so the in-process count must keep the hard window refusing exactly at its
  limit (it does: the in-process stop holds).
- **`flush()` before a successful restore.** Option A: save local counts anyway (may under-write a larger stored
  count). Option B: retry the restore, then fail the `Result` without saving. Recommend B.
- **Event order across concurrent calls** is not the ledger order (events leave the lock). Option: a second
  non-reentrant mutex for delivery would deadlock an observer that acquires. Recommend documenting "events of one
  call arrive in order; events of concurrent calls may interleave" and keeping each event self-describing
  (window start, counts) so an observer need not rely on order.
- **Cancelled-at-the-instant-of-grant.** The recommended `ensureActive()` in the grant section leaves a cancellation
  on another thread after the check as the caller's to handle with `release()` or `withPermit`. A post-grant
  `ensureActive()` plus an internal un-grant would narrow it further but adds a second owner for the permit;
  not recommended.

## 7. Gaps

- **No `js` run.** Every type used is declared in kotlinx-coroutines' `common` sources
  (`Mutex`, `Job`, `Channel`, `select`, `withTimeoutOrNull`, `NonCancellable`), and the stack-level findings
  compiled Mutex, Channel, `delay`, `TimeSource` and `Result` for `js(IR)`
  (`../../research/call-budget.md` F11), but `select` with `onJoin`/`onReceive` and the latch were not compiled
  for `js` here. Test: add the budget to `webtools-http-commoncheck` and run its `compileKotlinJs`.
- **Cost per wake and contention at scale not measured.** The stack-level probe measured the re-plan at
  1.2-1.7 ms for 3,000 waiters; this design adds one lock section per woken waiter. Test: a Level 4c test with
  `maxQueued` waiters per lane on `Dispatchers.Default`, one cancel per round, timing the round.
- **The S9 race is loose.** Most cancels landed before the close. Test: a stress loop that cancels from a second
  thread at randomised offsets around `close()`, repeated thousands of times, asserting the same invariants.
- **Event interleaving across concurrent calls** is stated, not measured; the observer contract must say so.
- **`close()` running no code of ours** rests on `select`/`onJoin` not exposing consumer handlers to the closer's
  thread; read from the API contract, not from `JobSupport` internals. Test: an observer-free `close()` under a
  `CoroutineExceptionHandler`-instrumented `runTest` with many waiters registers no uncaught exception.
- **Restore interval default (30 s)** is a judgement, not derived from a provider's behaviour.
- **A blocking `BudgetStore`** (JDBC, a file write on the caller's thread) cannot be timed out by
  `withTimeoutOrNull`; the budget then stalls for as long as the store blocks. Mitigation is documentation and a
  store that moves its blocking call to `Dispatchers.IO` itself; no test in this research.
- **Ktor channels and pools beyond CIO's `ConnectionFactory`/`Endpoint`** were not surveyed; Kord's REST code was
  read, not executed.

## 8. Load-bearing claims

1. **`Mutex.lock` is fair, cancellable with a prompt cancellation guarantee, returns the lock if cancelled after
   acquiring, and does not check cancellation when not suspended.** "This function is fair; suspended callers are
   resumed in first-in-first-out order." / "This function releases the lock if it was already acquired by this
   function before the [CancellationException] was thrown." / "Note that this function does not check for
   cancellation when it is not suspended." —
   [`Mutex.kt` at 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/sync/Mutex.kt).
2. **`Mutex` is non-reentrant and has `synchronized`-like memory semantics.** "It is **non-reentrant**, that is
   invoking [lock] even from the same thread/coroutine that currently holds the lock still suspends the invoker."
   / "An unlock operation on a [Mutex] happens-before every subsequent successful lock on that [Mutex]." — same
   file and version.
3. **`NonCancellable` and `withContext` let a cancelled coroutine suspend in cleanup.** "A non-cancelable job that
   is always [active][Job.isActive]. It is designed for [withContext] function to prevent cancellation of code
   blocks that need to be executed without cancellation." —
   [`NonCancellable.kt` at 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/NonCancellable.kt);
   "in the rare case when you need to suspend in a cancelled coroutine you can wrap the corresponding code in
   `withContext(NonCancellable) {...}`" —
   [cancellation guide at 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/docs/topics/cancellation-and-timeouts.md).
4. **`Job` is thread-safe and `CompletableJob.complete()` has a single winner.** "All functions on this interface
   and on all interfaces derived from it are **thread-safe** and can be safely invoked from concurrent coroutines
   without external synchronization." —
   [`Job.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/Job.kt);
   "The result is `true` if this job was completed as a result of this invocation and `false` otherwise (if it was
   already completed). Subsequent invocations of this function have no effect and always produce `false`." —
   [`CompletableJob.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/CompletableJob.kt).
5. **`Job.onJoin` never fails, so a close latch completed normally never raises in a select.** "Clause for [select]
   expression of [join] suspending function that selects when the job is complete. This clause never fails, even if
   the job completes exceptionally." — `Job.kt`, 1.10.2.
6. **A conflated channel never suspends the sender and `trySend` always succeeds.** "[Sending][send] to this
   channel never suspends, and [trySend] always succeeds." / "so that the receiver always gets the last element
   sent" —
   [`Channel.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/channels/Channel.kt),
   `Channel.Factory.CONFLATED`, 1.10.2.
7. **`select` is stable but its `onTimeout` clause is experimental.** "**Note: This is an experimental api.** It may
   be replaced with light-weight timer/timeout channels in the future." (on `@ExperimentalCoroutinesApi
   SelectBuilder.onTimeout`) —
   [`OnTimeout.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/selects/OnTimeout.kt);
   `select` itself carries no experimental annotation and is documented as "cancellable ... prompt cancellation
   guarantee" in [`Select.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/selects/Select.kt).
8. **`withTimeoutOrNull` returns `null` only for its own timeout, immediately for a non-positive timeout, and a
   sub-millisecond duration rounds up.** Code comment: "Return null if it's our exception, otherwise propagate it
   upstream (e.g. in case of nested withTimeouts)"; `if (timeMillis <= 0L) return null` —
   [`Timeout.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/Timeout.kt);
   "Durations which have a nanosecond component less than a single millisecond will be rounded up to the next
   largest millisecond." —
   [`Delay.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/Delay.kt).
9. **The timeout is asynchronous with its block and can drop a result.** "The timeout event in [withTimeout] is
   asynchronous with respect to the code running in its block and may happen at any time, even right before the
   return from inside of the timeout block." — cancellation guide at 1.10.2 (link in claim 3). And "Coroutine
   cancellation is _cooperative_. A coroutine code has to cooperate to be cancellable." — same guide.
10. **`TestDispatcher` is a `Delay` with a virtual `invokeOnTimeout`, so `withTimeoutOrNull` is virtual.**
    `public abstract class TestDispatcher internal constructor() : CoroutineDispatcher(), Delay,
    DelayWithTimeoutDiagnostics` and `override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context:
    CoroutineContext): DisposableHandle = scheduler.registerEvent(this, timeMillis, block, context) { false }` —
    [`TestDispatcher.kt` at 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestDispatcher.kt).
11. **`invokeOnCompletion` handlers must not throw, and for a non-coroutine `Job` a throw is rethrown.** "Installed
    [handler] should not throw any exceptions. If it does, they will get caught, wrapped into
    [CompletionHandlerException], and rethrown, potentially causing crash of unrelated code." — `Job.kt`, 1.10.2.
12. **`kotlin.concurrent.Volatile` is stable in 2.2.0; the stdlib atomics are experimental.** `@SinceKotlin("1.9")
    public expect annotation class Volatile()` with "This annotation has effect in Kotlin/JVM and Kotlin/Native." —
    [`Volatile.kt` at v2.2.0](https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/concurrent/Volatile.kt);
    "This annotation marks the experimental Kotlin Atomics API." with `@RequiresOptIn(level =
    RequiresOptIn.Level.ERROR)` and `@SinceKotlin("2.1")` —
    [`ExperimentalAtomicApi.kt` at v2.2.0](https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/concurrent/atomics/ExperimentalAtomicApi.kt).
13. **`MutableStateFlow` is thread-safe.** "All methods of state flow are **thread-safe** and can be safely invoked
    from concurrent coroutines without external synchronization." —
    [`StateFlow.kt` at 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/flow/StateFlow.kt).
14. **Ktor 3.6.1's CIO engine documents the result-drop race and the permit release on cancellation.** "`withTimeout`
    drops the block's result when it is cancelled right as the connect completes, and the connected socket would
    leak together with its connection permits. A socket that connected but can't be handed back is closed and
    released." —
    [`Endpoint.kt` at 3.6.1](https://github.com/ktorio/ktor/blob/3.6.1/ktor-client/ktor-client-cio/common/src/io/ktor/client/engine/cio/Endpoint.kt)
    (`connectWithTimeout`).
15. **Kord 0.17.0 holds a `Mutex` across a `delay` and releases it only from a later `complete`.**
    `suspend fun awaitAndLock() { mutex.lock(); logger.trace { ... }; reset.value.await() }` and `fun unlock() =
    mutex.unlock()` —
    [`AbstractRateLimiter.kt` at 0.17.0](https://github.com/kordlib/kord/blob/0.17.0/rest/src/commonMain/kotlin/ratelimit/AbstractRateLimiter.kt).
    Kord's `consume()` is `mutex.withLock { consumeUnderLock() }` with `delay(duration)` inside —
    [`AbstractIntervalRateLimiter.kt`](https://github.com/kordlib/kord/blob/0.17.0/common/src/commonMain/kotlin/ratelimit/AbstractIntervalRateLimiter.kt),
    [`TimeSourceIntervalRateLimiter.kt`](https://github.com/kordlib/kord/blob/0.17.0/common/src/commonMain/kotlin/ratelimit/TimeSourceIntervalRateLimiter.kt).
    (The leak on cancellation is my reading of this code, not a documented statement.)
16. **resilience4j-kotlin 2.4.0 reserves a permit and then plain-`delay`s.** `val waitTimeNs = reservePermission()
    when { waitTimeNs > 0 -> delay(TimeUnit.NANOSECONDS.toMillis(waitTimeNs)) ...` —
    [`RateLimiter.kt` at v2.4.0](https://github.com/resilience4j/resilience4j/blob/v2.4.0/resilience4j-kotlin/src/main/kotlin/io/github/resilience4j/kotlin/ratelimiter/RateLimiter.kt).
17. **Arrow Resilience 2.3.0's `CircuitBreaker` is a CAS state machine that refuses fast and keeps no waiters.**
    "All requests short-circuit/fail-fast with `ExecutionRejected`" and `import arrow.atomic.Atomic` —
    [`CircuitBreaker.kt` at 2.3.0](https://github.com/arrow-kt/arrow/blob/2.3.0/arrow-libs/resilience/arrow-resilience/src/commonMain/kotlin/arrow/resilience/CircuitBreaker.kt).
