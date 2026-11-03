# Caffeine 3.3.1 mechanics under the response-cache default: bound, statistics, invalidation, close

## Question

Which Caffeine 3.3.1 behaviours do `CaffeineResponseCache`'s size bound, statistics, invalidation and `close()` rest on, for an
`AsyncCache` whose futures the cache creates and completes itself? An answer must meet these requirements: the bound holds for every
stored entry whatever a loader reports; a failure is never stored and a failed load makes Caffeine log nothing; after
`invalidate(key)` the next call for that key receives nothing loaded before it; each statistic counts one well-defined event, the same
on every run whatever the maintenance timing; Java 11 API; Caffeine 3.3.1 as pinned; no Caffeine type in a public signature; no
`Scheduler`; the Level 2 tests run deterministically on a virtual clock with a same-thread executor.

All source references are to tag `v3.3.1` of `ben-manes/caffeine` (commit `aabb2423`, published 2026-10-13, the latest release on the
research date) under
`caffeine/src/main/java/com/github/benmanes/caffeine/cache/`, read in a shallow clone. Nothing was built or run; behaviour that only a
run would show is listed in *Gaps* with the unit test that would establish it. The earlier findings F1, F2, F7 and F9 of
[`../../research/response-cache.md`](../../research/response-cache.md) are built on, not repeated.

## Bottom line

- **`stats().evictionCount()` is not size-only and its value depends on maintenance timing, so do not report it.** It counts every
  `RemovalCause` whose `wasEvicted()` is true: `SIZE`, `EXPIRED` and `COLLECTED`. A failed entry, a zero-TTL entry, a TTL expiry, an
  expired entry replaced inside `get`, and an expired entry removed by `asMap().remove(key, future)` or `clear()` all count as `EXPIRED`
  evictions; only `EXPLICIT` and `REPLACED` do not. Count `evictions` in an `evictionListener` that increments a `LongAdder` when
  `cause == RemovalCause.SIZE`. On an `AsyncCache` the listener is wrapped to receive the unwrapped value, runs synchronously inside the
  atomic removal on the thread doing the maintenance, and is called only for completed futures. Do not call `recordStats()`: with a
  size-eviction listener and the cache's own hit, miss and failed-load counters nothing it records is reported, and it adds a second
  source of truth plus `System.nanoTime` reads around every load.
- **Weights: a weight of 0 is never size-evicted, a weight above `maximumWeight` is evicted after storage, and a negative weight throws
  inside Caffeine's completion callback and logs a warning.** In-flight futures weigh 0 by design. The cache must therefore compute the
  entry's weight itself, once, when it builds the entry: `1..maxWeight` for a stored `Found` or `Missing`; `0` for a failure and for any
  zero-lifetime entry (so it can neither displace a live entry nor be counted as a size eviction); and a `Found` whose reported weight
  exceeds `maxWeight` is given a zero lifetime, so it is delivered to its waiters and never stored.
- **`invalidate` and `invalidateAll` on an in-flight future remove the map entry and leave the future alone.** Use the async view's
  `asMap().remove(key)` and `asMap().clear()`; the `synchronous().asMap()` view joins a pending future and can block. The completion
  callback's `replace(key, future, future)` is a `computeIfPresent` guarded by value equality (identity for a `CompletableFuture`), so it
  never re-inserts, and a caller after the invalidation starts a second load. The `Cache.invalidateAll` Javadoc calls this "undefined";
  the source defines it, and a Level 2 test should pin it.
- **`close()` needs nothing from Caffeine.** With no `Scheduler` there is no pacer, no thread, no timer and no shutdown hook; the
  maintenance task holds the cache through a `WeakReference`. Calling `asMap().clear()` in `close()` is optional and only releases the
  held values early when the closed cache object stays reachable.
- **On completion the user's `expireAfterCreate` runs, once, with the ticker's current time**, not `expireAfterUpdate`. A returned `0`
  makes the entry expired at once; `Long.MAX_VALUE` is capped at about 150 years. `Expiry` and `Weigher` are `@NullMarked` (JSpecify), so
  Kotlin sees non-null parameters. The `BiFunction` form of `get` never uses its executor argument for a function that returns a fresh
  future; the per-key lock is held only while the function runs, so the function must do nothing but allocate the future.
- **Tests:** with `executor(Runnable::run)` every write runs maintenance inline, so `cleanUp()` then
  `policy().eviction().get().weightedSize()` is deterministic. Caffeine logs through `System.getLogger`, which the default
  `LoggerFinder` routes to `java.util.logging` when no other finder is installed; a root-logger `Handler` filtered on the logger name,
  with a positive control that provokes one warning first, observes it.

## Findings

### F1. What `evictionCount` counts (source; established by the code at the tag)

- `RemovalCause.wasEvicted()` is `false` for `EXPLICIT` and `REPLACED` and `true` for `COLLECTED`, `EXPIRED` and `SIZE`
  ([`RemovalCause`](https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/RemovalCause.java)).
  `ConcurrentStatsCounter.recordEviction(weight, cause)` increments `evictionCount` and adds the weight without looking at the cause
  ([`stats/ConcurrentStatsCounter`](https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/stats/ConcurrentStatsCounter.java)).
- `BoundedLocalCache` calls `recordEviction` on each of these paths, always inside or right after the atomic removal:
  - `evictEntry` (size eviction and maintenance-driven expiry);
  - `doComputeIfAbsent` when it finds an expired node and replaces it with a new mapping in the same call (cause `EXPIRED`, recorded
    and `notifyEviction`/`notifyRemoval` fired before the mapping function runs);
  - `remove(key)`, `remove(key, value)` and `removeNode` (used by `clear()`) when `hasExpired(...)` holds (cause `EXPIRED`);
  - `put`-style replacement of an expired node.
  So **one removal of a failed or zero-TTL entry is one counted "eviction"**, whichever of Caffeine's maintenance, the replacing `get`,
  or a waiter's `remove(key, future)` gets there first.
- `remove(key, value)` checks expiry **before** it compares the value, and returns `true` only for `EXPLICIT`:
  `else if (hasExpired(node, ...) && !isComputingAsync(ctx.oldValue)) { ctx.cause = RemovalCause.EXPIRED; } else if (node.containsValue(value))`
  and `return (removeCause == RemovalCause.EXPLICIT);`. Two consequences: a waiter's `asMap().remove(key, future)` on a lifetime-zero
  entry returns `false` yet removes it (it counted as an eviction); and `remove(key, someFuture)` removes an expired node even when the
  node holds a different future, which is harmless because that node is expired.
- Whether a failed entry is removed as `EXPIRED` or `EXPLICIT` depends on whether the waiter's `remove` ran before or after Caffeine's
  completion callback applied the zero lifetime (the callback-ordering race of F2 in the earlier research). So `evictionCount` is not
  reproducible run to run for a cache that stores failures with zero lifetime.
- When the removal is *noticed* also varies: a read of an expired entry only schedules maintenance (`getIfPresent` returns `null` and
  calls `scheduleDrainBuffers()`); the removal and the count happen at the next maintenance, replacing `get`, or `clear()`.
- `CacheStats`' own Javadoc: "When an entry is evicted from the cache, `evictionCount` is incremented and the weight added to
  `evictionWeight`." Guava's `CacheStats.evictionCount()`: "Returns the number of times an entry has been evicted. This count does not
  include manual invalidations." Micrometer's Caffeine binder publishes this as `cache.evictions` ("The number of times the cache was
  evicted.") and `cache.eviction.weight`. **The vocabulary consumers know therefore means "any automatic removal, expiry included"**; the
  port's `evictions` (size bound only) is narrower, so its KDoc must say so.

### F2. A size-only count: `evictionListener` (source + Javadoc)

- `Caffeine.evictionListener` Javadoc: "The cache will invoke this listener during the atomic operation to remove the entry." and "The
  `evictionListener` **must not** modify this cache and any exception thrown will *not* be propagated to the `Cache` user, only logged
  via a `Logger`." It is called for `wasEvicted()` causes only (`notifyEviction` is reached only for those); the wiki says the same
  ("This listener will only be notified when `RemovalCause.wasEvicted()` is true").
- On `buildAsync()` the builder wraps it: `getEvictionListener(async=true)` returns `new AsyncEvictionListener<>(castedListener)`, whose
  `onRemoval` does `V value = Async.getIfReady(future); if (value != null) delegate.onRemoval(key, value, cause);` with the comment
  "Must have been completed and be non-null to be eligible for eviction"
  ([`Async.AsyncEvictionListener`](https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/Async.java)).
  The listener therefore receives the **unwrapped `Entry`**, never a future, and is not called for an in-flight future.
- `evictEntry` calls `notifyEviction` inside `data.computeIfPresent(...)` under `synchronized (node)`, after the `weight == 0` and
  "no longer expired" resurrect checks, and then retires the node. So the listener runs exactly once per node actually removed with
  that cause, on the thread that runs the maintenance: a `Executor` thread (the common pool by default), the caller of `cleanUp()`, or,
  with `executor(Runnable::run)`, the thread whose write triggered the drain. A `LongAdder.increment()` is the whole cost.
- The alternative, `removalListener`, is rejected: `notifyRemoval` hands the task to the executor, and `AsyncRemovalListener` adds a
  `future.thenAccept` hop, so the count lags and, under a rejecting or slow executor, is observed late. It also fires for every cause.
- `evictionListener` cannot be combined with `weakKeys()` and `buildAsync()`; irrelevant here (strong keys).

### F3. `recordStats()` earns nothing here (source)

- The port's statistics are defined per call: *hit* = a call that took an existing future (in flight or complete); *miss* = a call whose
  mapping function ran; *failed load* = one load that ended in `Failed`; *eviction* = a size eviction. Caffeine's `hitCount` and
  `missCount` already follow the first two for a single `get` ("Cache lookups that reuse a value loaded by a concurrent lookup increment
  `hitCount`" in `CacheStats`; `statsAware` calls `recordMisses(1)` when the mapping function is applied), **but** the cache's `get`
  is called more than once per `getOrLoad` when `maxAge` finds a stale entry or a waiter loses the failure race, so Caffeine counts
  one call twice. Counting in the cache, at the `get` the call finally uses, avoids that.
- `loadFailureCount` stays `0` because the future completes normally (F2 of the earlier research); `evictionCount` is F1.
  `recordStats()` also makes `statsTicker()` return `Ticker.systemTicker()` (generated `statsTicker` method, and the invariant asserted in
  `LocalCacheSubject.checkStats`), so each load reads `System.nanoTime` twice even though the cache's expiry ticker is the virtual one.
  Without `recordStats()` the counter is `StatsCounter.disabledStatsCounter()`. Recommended: leave it off.

### F4. Weights (source; Caffeine's own tests agree)

- **Weight 0 is never selected for size eviction.** `evictEntry`: `else if (ctx.cause == RemovalCause.SIZE) { int weight = node.getWeight(); if (weight == 0) { ctx.resurrect = true; return node; } }`;
  `evictFromMain`: "Skip over entries with zero weight"; `evictFromWindow` only transfers nodes with `getPolicyWeight() != 0`. This is the
  mechanism that protects in-flight futures: `Async.AsyncWeigher` "returns `0` to indicate that the entry should not be evicted due to a
  size constraint". Caffeine's `EvictionTest.maximumSize_decrease_min` expects a zero-weighted cache to keep its full size after
  `setMaximum(0)`. Consequence: **a loader-reported weight of 0 would make the entry immune to the bound**, so any number of them
  would be stored (until their TTL).
- **A weight above `maximumWeight` is stored, then evicted as `SIZE` at the next drain.** `AddTask.run`:
  `if (weight > maximum()) { evictEntry(node, RemovalCause.SIZE, ...); }`; `UpdateTask.run` (the task queued by the async completion's
  `replace`, since the weight goes from 0 to `w`): `if (node.getPolicyWeight() > maximum()) { evictEntry(node, RemovalCause.SIZE, ...); }`
  (window and probation branches). `evictFromMain` also has "Evict immediately if the candidate's weight exceeds the maximum".
  `EvictionTest.evict_zero_async` pins the async case: with a maximum of 0, an in-flight future weighs 0, then the completed value is
  evicted with cause `SIZE` and the (eviction) notification carries the value. So an oversized entry is **readable between completion and
  the drain** and **is counted as a size eviction** by the listener of F2.
- **Weigher contract.** `Weigher.weigh`: "`@return` the weight of the entry; must be non-negative". `Caffeine.weigher` wraps the
  user's weigher in `Weigher.boundedWeigher`, whose `BoundedWeigher.weigh` does `requireArgument(weight >= 0, "weight cannot be negative: %s", weight)`
  and throws `IllegalArgumentException`. On the async completion path the weigher is called by `replace(...)` **outside** the compute
  block, so the exception reaches `handleCompletion`'s `catch (Throwable t)`, which logs `WARNING "Exception thrown during asynchronous
  load"`, records a load failure and removes the entry. A negative reported weight would therefore produce exactly the warning the
  design must never produce; `EvictionTest.put_negativeWeight` shows the synchronous form throwing.
- **`int` range.** `weightedSize` and `maximum` are `long`; each node's `policyWeight` and the `UpdateTask` difference are `int`
  (`weight - oldWeight` cannot overflow for two non-negative ints). No overflow arises from `Int.MAX_VALUE` weights, but one such entry
  exceeds any realistic `maxWeight` and takes the oversize path.
- **In-flight future:** weight `0` (`AsyncWeigher.weigh`: `(value == null) ? 0 : delegate.weigh(key, value)`), so an entry that is loading
  is never a size victim and never contributes weight until `handleCompletion`'s `replace` applies the real one. The Eviction wiki:
  "weights are computed at entry creation and update time, are static thereafter".
- `maximumWeight(0)` is legal and documented ("elements will be evicted immediately after being loaded into the cache"); `maximumWeight`
  requires a weigher and a weigher requires `maximumWeight` (`requireWeightWithWeigher` in `buildAsync`).

### F5. `invalidate`, `invalidateAll`, `clear` on an in-flight future (source; the Javadoc calls it undefined)

- `Cache.invalidateAll()` Javadoc: "The behavior of this operation is undefined for an entry that is being loaded (or reloaded) and is
  otherwise not present." The async paths are defined by the source:
  - `AsyncCache.asMap().remove(key)` and `.remove(key, value)` go straight to `BoundedLocalCache.remove`. For an in-flight future
    `hasExpired(...) && !isComputingAsync(...)` is false (its variable time is `now + ASYNC_EXPIRY`), so the cause is `EXPLICIT`: the node
    is retired and removed from the map; the **future is not cancelled or completed**.
  - `asMap().clear()` and `synchronous().invalidateAll()` both call `BoundedLocalCache.clear()`, which removes every node through
    `removeNode` (`EXPLICIT`, or `EXPIRED` for expired ones) and again never touches a future.
  - `synchronous().invalidate(key)` is `asyncCache().cache().remove(key)` and does not wait. **`synchronous().asMap().remove(key)` does
    wait**: `AsMapView.remove` returns `Async.getWhenSuccessful(oldValueFuture)`, which calls `future.join()`. The `AsyncCache.synchronous()`
    Javadoc says so: "If a modification is made to a mapping that is currently loading, the operation blocks until the computation
    completes." On a same-thread executor that is a self-deadlock.
- **The completion does not re-insert.** `handleCompletion` registers a `whenComplete` that, for a ready value whose entry was created
  deferred, calls `cache().replace(key, castedFuture, castedFuture, /*shouldDiscardRefresh=*/ false, /*quietly=*/ true)` ("update the
  weight and expiration timestamps"). `BoundedLocalCache.replace` is `data.computeIfPresent(...)`: an absent node is a no-op, and a node
  holding a different value is left as it is (`else if (!n.containsValue(oldValue)) { ctx.oldValue = null; return n; }`;
  `CompletableFuture` does not override `equals`, so this is identity). A late completion therefore neither re-inserts the invalidated
  future nor overwrites a newer one.
- **Callers already holding the future** receive the entry the cache's own load completes it with; Caffeine does nothing to it. A caller
  after the invalidation finds no node, `computeIfAbsent` runs the mapping function, and a second load starts, in parallel with the
  first. The first load's result is never stored. So "the next call receives nothing loaded before the invalidation" holds, at the price of
  one extra concurrent load, which is also what a consumer who invalidated would expect.

### F6. `close()`: nothing to release on Caffeine's side (source)

- No `Scheduler` means `BoundedLocalCache.pacer()` returns `null` (`Scheduler.disabledScheduler()` is the documented default); a search of
  the cache package for `new Thread`, `Executors.`, `ScheduledThreadPool`, `java.util.Timer`, `addShutdownHook`, `Cleaner` and
  `finalize` finds none. The only work Caffeine puts on an executor is the drain task, and `PerformCleanupTask` is
  `WeakReference<BoundedLocalCache<?, ?>> implements Runnable`: it does not keep a dropped cache alive.
- The `Executor` is the caller's. If it is shut down, `scheduleDrainBuffers` catches the rejection, logs
  `WARNING "Exception thrown when submitting maintenance task"` and runs the maintenance on the calling thread. So a consumer that
  closes its own `maintenanceExecutor` before the cache sees warnings and caller-thread maintenance, not an exception. This belongs in
  the `maintenanceExecutor` KDoc (the cache never shuts it down, and it must outlive the cache's last write).
- Dropping entries is optional: entries (decoded response values) are held by the cache instance, so they live as long as the closed
  `CaffeineResponseCache` is reachable. `asMap().clear()` releases them at once; it removes in-flight nodes without touching their
  futures (F5), so it composes with a scope cancellation that completes each future with `Closed`. It emits `EXPIRED`
  evictions for any expired residue, which the size-only listener ignores.

### F7. The mapping function of `get(key, BiFunction)` (Javadoc + source)

- `LocalAsyncCache.get(key, mappingFunction, recordStats)`: `getIfPresent(key, false)` first; if present, return it (a hit when stats are
  on). Otherwise `cache().computeIfAbsent(key, k -> { castedResult = mappingFunction.apply(key, cache().executor()); deferred[0] = !Async.isReady(castedResult); ... return requireNonNull(castedResult); }, recordStats, false)`,
  then `handleCompletion(...)` runs **before `get` returns**, so Caffeine's `whenComplete` is registered on the future before the cache
  starts its load.
- The executor argument is simply `cache().executor()`; it is used only by a function that passes it on (the `Function` overload does,
  through `supplyAsync`). A function that returns `CompletableFuture<Entry>()` does not use it.
- The function runs inside `data.compute(keyRef, ...)`: the `ConcurrentHashMap` bin lock is held for the duration, and for an expired
  node also `synchronized (n)`. `Cache.asMap()` Javadoc: "Some attempted update operations by other threads may be blocked while
  computation is in progress. The mapping function must not modify this cache during the computation." Hold time is the cost of
  `CompletableFuture()` plus the cache's own counter increment.
- **If the function throws:** `AsyncCache.get` Javadoc: "`@throws RuntimeException or Error` if the mappingFunction does when constructing
  the future, in which case the mapping is left unestablished". For a fresh key nothing changes; for an expired node the node is retired
  and removed (cause `EXPIRED`, counted, listener called) before the exception is rethrown. A `null` return throws `NullPointerException`.
- **An expired node is replaced in the same call.** `doComputeIfAbsent` sees `hasExpired(n, now) && !isComputingAsync(oldValue)`, records
  `EXPIRED`, calls `notifyEviction`, applies the mapping function and reuses the node (`n.setValue(ctx.newValue, ...)`). So once a
  zero-lifetime or TTL-expired entry has had its expiry applied, the next `get` starts a fresh load by itself; the waiter-side removal is
  needed only for the window before Caffeine's completion callback has applied the zero lifetime (the node still carries
  `ASYNC_EXPIRY`, so a complete future is served as live).

### F8. `Expiry` on the async path (source; signatures from the tag)

- On the cache's own completion, `replace(..., quietly=true)` takes `expireAfterUpdate(n, key, newValue, expiry(), now)` because
  `isExpirationDeferred(n, now)` (`variableTime - now > MAXIMUM_EXPIRY`) holds for a node created in flight. That calls
  `AsyncExpiry.expireAfterUpdate(key, future, currentTime, currentDuration)` with `currentDuration` about `ASYNC_EXPIRY`
  (`(Long.MAX_VALUE >> 1) + (Long.MAX_VALUE >> 2)`, about 220 years), and `AsyncExpiry` answers
  `(currentDuration > MAXIMUM_EXPIRY) ? delegate.expireAfterCreate(key, value, currentTime) : delegate.expireAfterUpdate(...)`. **So the
  user's `expireAfterCreate(key, entry, currentTime)` runs once, with the ticker's reading at the replace;** the user's
  `expireAfterUpdate` runs only for a real replacement of an already-completed value, which this cache never does.
  `expireAfterRead` runs on later reads with the true remaining duration; returning `currentDuration` leaves the lifetime unchanged
  (`tryExpireAfterRead` skips an in-flight node, where `currentDuration > MAXIMUM_EXPIRY`).
- **Zero:** `BoundedLocalCache.expireAfterUpdate` computes `Math.max(0L, expiry.expireAfterUpdate(...))` and `expiresAt(now, 0)` is `now`;
  `hasExpired` is `... | (expiresVariable() && (now - node.getVariableTime() >= 0))`, true immediately. A negative return is also clamped
  to 0 by that `Math.max`. The Javadoc of `Expiry` does not state the zero case; the source does.
- **`Long.MAX_VALUE`:** `AsyncExpiry` returns `Math.min(duration, MAXIMUM_EXPIRY)` where `MAXIMUM_EXPIRY = (Long.MAX_VALUE >> 1); // 150 years`,
  and for an async cache `expiresAt` adds the duration to `now` without a second clamp. `Duration.INFINITE.inWholeNanoseconds`
  (`Long.MAX_VALUE`) is therefore accepted and behaves as 150 years. A completed entry with that duration does not look "in flight"
  afterwards (`variableTime - now'` only shrinks, so it never exceeds `MAXIMUM_EXPIRY`).
- **Signatures (v3.3.1):** `@NullMarked public interface Expiry<K, V>` with `long expireAfterCreate(K key, V value, long currentTime)`,
  `long expireAfterUpdate(K key, V value, long currentTime, long currentDuration)`, `long expireAfterRead(K key, V value, long currentTime, long currentDuration)`;
  the package also has `Expiry.creating(BiFunction<K, V, java.time.Duration>)`, `writing` and `accessing`. `@NullMarked public interface Weigher<K, V>`
  (`@FunctionalInterface`, `int weigh(K key, V value)`) and `@NullMarked @FunctionalInterface public interface RemovalListener<K, V>` with
  `void onRemoval(@Nullable K key, @Nullable V value, RemovalCause cause)`. `Caffeine.expireAfter(Expiry<? super K1, ? super V1>)`,
  `weigher(Weigher<? super K1, ? super V1>)`, `evictionListener(RemovalListener<? super K1, ? super V1>)` and
  `buildAsync(): AsyncCache<K1, V1>` take the **value** type (here `Entry`), not the future.
- **Kotlin.** The artifact's POM lists `org.jspecify:jspecify:1.0.1` with `compile` scope, so Gradle puts the annotations on the compile
  classpath. Kotlin docs: "By default, the Kotlin compiler reports nullability mismatches for JSpecify annotations as errors", and
  `@NullMarked` types are "treated as regular nullable and non-nullable types rather than platform types". So a Kotlin
  `object : Expiry<CacheKey<*>, Entry>` overrides with non-null `key`/`value` and `Long` parameters, a `Weigher` is an ordinary SAM
  lambda, and a `RemovalListener` lambda's `key`/`value` are nullable (`K?`/`V?`). `Expiry` has three abstract methods, so it is not a
  SAM; write an object. `Expiry.creating` takes a `java.time.Duration`, not `kotlin.time.Duration`; a hand-written object that returns
  the entry's precomputed nanosecond lifetime avoids the conversion and the saturation helper.

### F9. Bounded and eventual: what a test can rely on (Javadoc + source + wiki)

- `maximumWeight` Javadoc: the cache "may evict an entry before this limit is exceeded or temporarily exceed the threshold while
  evicting". Maintenance is amortized: `drainWriteBuffer` processes at most `WRITE_BUFFER_MAX = 128 * ceilingPowerOfTwo(NCPU)` tasks and
  then flags `PROCESSING_TO_REQUIRED`; `rescheduleCleanUpIfIncomplete` reschedules at once **only for `ForkJoinPool.commonPool()`**
  ("An immediate scheduling cannot be performed on a custom executor because it may use a caller-runs policy ... Otherwise, it will be
  handled due to other cache activity"). So on a custom real executor, a burst can leave the cache over `maximumWeight` until later
  activity or a `cleanUp()`.
- Order inside `maintenance`: `drainReadBuffer`, `drainWriteBuffer` (applies the `AddTask`/`UpdateTask` weights), `expireEntries`, then
  `evictEntries`. After a quiescent `cleanUp()`, `weightedSize() <= maximum` except for the amortization case above.
- `Caffeine.executor` Javadoc: "A test may instead prefer to configure the cache to execute tasks directly on the same thread." With
  `executor(Runnable::run)`, `scheduleDrainBuffers` runs the drain inline inside the write (the eviction lock is reentrant), so size
  evictions, their listener and the weights are settled before the writing call returns; `cleanUp()` makes it explicit
  (wiki Testing: "use `Cache.cleanUp()` to trigger one immediately when tests depend on eviction having occurred").
- Observation points on the async cache: `synchronous().cleanUp()`; `synchronous().estimatedSize()` (the node count, including expired
  nodes awaiting removal and weight-0 in-flight nodes; Javadoc: "pending removal due to expiration ... can be mitigated by performing a
  `cleanUp()` first"); `synchronous().policy().eviction().get().weightedSize()` (an `OptionalLong`, `max(0, weightedSizeAcquire)`);
  `eviction.weightOf(key)` (an `OptionalInt` that is empty for an expired or in-flight node, otherwise the stored weight, which lets a test
  assert the weight clamp); `eviction.getMaximum()`.

### F10. Observing Caffeine's log output on JDK 11 to 23 (Javadoc + source; the handler recipe is reasoned, not run)

- Caffeine logs through `System.getLogger(LocalAsyncCache.class.getName())`, `Async` and `BoundedLocalCache` the same way. The
  `System.LoggerFinder` Javadoc: "The system default `LoggerFinder` implementation uses `java.util.logging` as the backend framework
  when the `java.logging` module is present. It returns a logger instance that will route log messages to a `java.util.logging.Logger`."
  The module's test classpath has `slf4j-api` and `logback-classic` and no `slf4j-jdk-platform-logging`, and logback registers no
  `LoggerFinder`, so Caffeine's warnings go to JUL. Caffeine's own build uses the opposite route: its `slf4j-test` bundle is
  `["slf4j-jdk-platform-logging", "slf4j-jul-bridge", "slf4j-test"]`, which sends `System.Logger` to SLF4J and captures it with
  `slf4j-test`; that adds dependencies this module does not have.
- Recipe: in a test, add a `java.util.logging.Handler` to the **root** logger (`Logger.getLogger("")`, always strongly held by the
  `LogManager`), collect `LogRecord`s whose `loggerName` starts with `com.github.benmanes.caffeine`, and remove the handler afterwards.
  A handler on the named Caffeine logger is fragile because `LogManager` holds loggers weakly. The default level passes `WARNING`
  (`System.Logger.Level.WARNING`/`ERROR` map to `WARNING`/`SEVERE`).
- **Add a positive control in the same JVM**: before asserting "a failed load logs nothing", complete a future exceptionally inside a
  throw-away `Caffeine.newBuilder().buildAsync<String, String>()` cache's `get` and assert the handler saw
  `"Exception thrown during asynchronous load"`. Without it a re-routed `LoggerFinder` (a consumer's classpath, or a future dependency)
  turns the negative assertion vacuous.
- A structural assertion needs no logging at all and should accompany it: the future the cache hands to Caffeine is never
  `isCompletedExceptionally` for an expected failure (`handleCompletion` logs only when `value == null` and the error is neither
  `CancellationException` nor `TimeoutException`).

### F11. Why the 2,000-retry race appears, and how to provoke it (JDK source + Caffeine source; the numbers are the earlier probe's)

- `handleCompletion` registers Caffeine's `whenComplete` **first**, inside `get`; every waiter registers later. `CompletableFuture.postComplete`
  pops the dependents stack from its head (`STACK.compareAndSet(f, h, t = h.next)`; `pushStack` pushes at the head), so **dependents run
  most-recently-registered first and Caffeine's callback runs last**. The class Javadoc does not promise an order; this is JDK 11.0.25
  implementation behaviour.
- A waiter's callback resumes a coroutine. Under a multi-threaded dispatcher the resumed coroutine can reach the next `getOrLoad` while
  the completing thread is still running other waiters' callbacks, before Caffeine's `replace` has applied the zero lifetime. In that
  window the node holds a complete future and still has `variableTime = now + ASYNC_EXPIRY`, so `computeIfAbsent` returns it as live.
  Only the waiter-side `remove(key, future)` closes the window.
- To provoke it on real threads: several waiters per load (the window is the time to run the other waiters' callbacks, so 8 to 32 joiners
  per key widen it), each retrying immediately on `Failed`, on `Dispatchers.Default` (or a multi-thread pool) with at least two cores, in
  rounds over fresh keys, with the failing loader completing as fast as possible. The earlier probe saw 45 of 2,000 immediate retries
  join the failed future without the removal and 0 with it; whether the same count appears on a given machine is not guaranteed, so the
  4c test asserts zero and the Level 2 test below makes the cause deterministic.
- **Candidate deterministic reproduction at Level 2 (unverified):** waiters resumed by `Dispatchers.Unconfined`/`UnconfinedTestDispatcher`
  continue inline inside their `whenComplete` callback, which runs before Caffeine's; an immediate retry from there should meet the
  complete failed future. A test that disables the waiter-side `remove` (a package-private seam or a copy of the loop) and expects the
  retry to join the failure would pin the mechanism. It needs one run to confirm.

## How comparable projects do it

- **Caffeine itself (v3.3.1)** exposes exactly the pieces above: `RemovalCause`, `RemovalListener`, `Policy.Eviction`, and an
  `evictionListener` for synchronous, size-aware accounting. Its own tests (`EvictionTest.evict_zero_async`, `put_asyncWeight`,
  `maximumSize_decrease_min`, `put_negativeWeight`, `evict_evictionListener_failure`) pin the async weight lifecycle, the zero-weight
  immunity and the listener-failure warning. [`EvictionTest`](https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/test/java/com/github/benmanes/caffeine/cache/EvictionTest.java).
- **Guava `CacheStats`** (33.0.0): `hitCount`, `missCount`, `loadSuccessCount`, `loadExceptionCount`, `evictionCount` ("This count does not
  include manual invalidations"), where "eviction" includes expiry. [Javadoc](https://guava.dev/releases/33.0.0-jre/api/docs/com/google/common/cache/CacheStats.html).
- **Micrometer (v1.14.0)** `CacheMeterBinder`: `cache.gets{result=hit|miss}`, `cache.puts`, `cache.evictions`; `CaffeineCacheMetrics` adds
  `cache.eviction.weight` ("This total does not include manual invalidations") and reads Caffeine's `stats().evictionCount()`. A consumer
  who binds `ResponseCache.stats()` to Micrometer will name the field `cache.evictions`; the port's narrower size-only meaning should be
  stated in the field's KDoc so it is not read as Guava's. [`CaffeineCacheMetrics`](https://github.com/micrometer-metrics/micrometer/blob/v1.14.0/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/cache/CaffeineCacheMetrics.java).
- **Caffeine's wiki** ([Testing](https://github.com/ben-manes/caffeine/wiki/Testing), [Removal](https://github.com/ben-manes/caffeine/wiki/Removal),
  [Eviction](https://github.com/ben-manes/caffeine/wiki/Eviction)): same-thread executor, `Ticker`, `cleanUp()`; eviction versus removal
  listener; static weights.

## Pitfalls & regrets

- **Reporting `stats().evictionCount()` as the port's `evictions`.** It silently includes every failed and zero-TTL entry, drifts with
  callback ordering, and changes if a `Scheduler` is added later. Use the listener.
- **Letting a loader's weight reach the weigher unchecked.** `0` makes the entry unbounded; a negative value logs a warning and drops the
  entry; a huge value stores briefly and evicts. All three come from one value the consumer controls.
- **Giving a failure weight 1.** The failed entry could win admission against a live entry (it is a candidate in the window) and force a
  size eviction of an innocent entry, which the listener would then count. Weight 0 keeps it out of the policy's victim selection, and the
  waiters' removal plus the zero lifetime remove it.
- **Using `synchronous().asMap()` for removal.** It joins an in-flight future. Use the async view for every mutation; use `synchronous()`
  only for `cleanUp`, `estimatedSize`, `policy` in tests.
- **Trusting `remove(key, future)`'s return value.** It is `false` for an expired node that was removed. Treat it as best-effort removal and
  never as "I won the race".
- **Doing anything but allocating in the mapping function.** The bin lock is held; a loader started there, a counter that blocks, or a
  re-entrant `getOrLoad` is the documented violation.
- **Passing a `maintenanceExecutor` that rejects.** Caffeine does not fail; it logs `WARNING` and does the maintenance on the writer.
- **A test that asserts "no warning" without a positive control** passes vacuously when the logging route changes.
- **Assuming a custom executor drains a burst.** Only the common pool reschedules incomplete maintenance; in a 4c test call `cleanUp()` in a
  loop until `weightedSize()` is stable before asserting the bound.

## Recommendation for this repo

Build `CaffeineResponseCache` on `Caffeine.newBuilder().ticker(...).executor(maintenanceExecutor).maximumWeight(maxWeight).weigher(...)
.expireAfter(...).evictionListener(...).buildAsync<CacheKey<*>, Entry>()`, with **no `recordStats()`**, and keep four counters of its own.
The reasoning: every Caffeine statistic that could stand in for a port field either counts a different event (`evictionCount` counts
expiry and failure removals, and varies with callback order) or double-counts the cache's own retry loops, while the mechanisms for the
exact events (a mapping function that runs once, a `SIZE`-filtered eviction listener, the cache's own completion point) are cheap and
deterministic on a same-thread executor.

Concrete shape (Kotlin, not compiled):

- **Entry.** `Entry(outcome, storedAt, lifetimeNanos, weight)` built once when the load ends. `lifetimeNanos`: `ttl` for `Found`, `missTtl`
  for `Missing`, `0` for `Failed`, `0` for a `ttl` of zero and `0` for a `Found` whose reported weight exceeds `maxWeight`.
  `weight`: `0` whenever `lifetimeNanos == 0`; else `reported.coerceAtLeast(1)` for `Found` (never above `maxWeight`, by the previous
  rule) and `1` for `Missing`. The weigher is `Weigher<CacheKey<*>, Entry> { _, e -> e.weight }`: it cannot throw and never sees a negative.
- **Expiry.** `object : Expiry<CacheKey<*>, Entry> { create -> value.lifetimeNanos; update -> value.lifetimeNanos; read -> currentDuration }`.
  `Duration.INFINITE` needs no special case (capped at 150 years by `AsyncExpiry`).
- **Mapping.** `val future = cache.get(key) { _, _ -> started = true; CompletableFuture<Entry>() }`, a body that only allocates and sets
  a local flag; `if (started)` the call counts a miss and starts the load after `get` returns; otherwise it counts a hit. Count at the final
  `get` of the call, so a `maxAge` retry or a lost failure race is one hit or one miss, not two.
- **Failed and zero-lifetime entries.** The waiter removes by identity with `asMap().remove(key, future)` whenever `entry.lifetimeNanos == 0`,
  before returning (this also closes the F11 window for `ttl == 0`, where a late caller would otherwise be served a just-completed
  value as live). `failedLoads` is incremented once by the load that produces `Failed`, not per waiter.
- **Evictions.** `evictionListener { _, _, cause -> if (cause == RemovalCause.SIZE) sizeEvictions.increment() }`; the listener never touches the
  cache.
- **Invalidation.** `invalidate(key)` is `cache.asMap().remove(key)`; `invalidateAll()` is `cache.asMap().clear()`. Nothing uses
  `synchronous()` outside tests.
- **`close()`.** Set the closed flag, cancel the load scope, complete every waiter with `Closed("cache")`, then `cache.asMap().clear()`.
  Do not shut down the executor, do not call `cleanUp()`. KDoc on `maintenanceExecutor`: the cache never shuts it down and it must
  outlive the cache's last write.
- **Tests.**
  - Level 2: build with `executor(clock.maintenanceExecutor)`; after loads call `synchronous().cleanUp()`, assert
    `policy().eviction().get().weightedSize().asLong <= maxWeight`, `weightOf(key)` for the clamp, and `stats().evictions` equal to the
    inserted-minus-resident count; weights `0`, negative, `maxWeight` and `maxWeight + 1`; invalidation of an in-flight key (a second load
    starts, the first result is not stored, the first call's waiters still get their value); `clear()` while loading; a handler-based log
    assertion with its positive control; a failed load whose `isCompletedExceptionally` is false.
  - Level 4c: real threads, `Dispatchers.Default`, common-pool executor; loop `cleanUp()` until `weightedSize()` is stable, then assert the
    bound; the retry test of F11 with 8 to 32 joiners per key, asserting zero retries that receive a previous failure.

Rejected: reporting Caffeine's `evictionCount` (wrong event), `removalListener` (asynchronous, every cause), `recordStats()` (a second
source of truth for no reported field), clamping an oversize weight down to `maxWeight` (one entry would evict the whole cache), and
`Expiry.creating` (a `java.time.Duration` detour for a value that is already nanoseconds).

### Considerations

- **Oversized `Found` (`weight > maxWeight`).** Options: (a) store with the reported weight and let Caffeine evict it as `SIZE` at the next
  drain, so it is counted as a size eviction and readable until then; (b) give it a zero lifetime so it is delivered to its waiters and
  never stored, not counted. Recommended: (b), because the bound then holds at every instant for every stored entry and the evictions
  counter keeps meaning "the bound removed a resident entry". Cost: an oversize load is invisible in `stats()`; a separate counter can
  be added later without changing the port's existing fields.
- **`maxWeight == 0`.** Caffeine accepts it and evicts every entry at its first drain. The builder can either reject it
  (`require(maxWeight >= 1)`) or document it as "cache nothing, coalesce only". Recommended: reject, because `ttl == 0` already is the
  documented way to coalesce without storing.

## Load-bearing claims

1. **`wasEvicted()` is true for `EXPIRED`, `SIZE` and `COLLECTED` and false for `EXPLICIT` and `REPLACED`.** "Returns `true` if there was
   an automatic removal due to eviction (the cause is neither `EXPLICIT` nor `REPLACED`)." Caffeine `RemovalCause.java` at
   [v3.3.1](https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/RemovalCause.java).
2. **Expiry-caused removals are counted as evictions.** `BoundedLocalCache.doComputeIfAbsent`: "`if (ctx.cause != null) { statsCounter().recordEviction(ctx.oldWeight, ctx.cause);`"
   after `actualCause = RemovalCause.EXPIRED`; and `remove(Object key, Object value)`: "`if (removeCause.wasEvicted()) { statsCounter().recordEviction(ctx.oldWeight, removeCause); }`".
   [`BoundedLocalCache.java` v3.3.1](https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/BoundedLocalCache.java).
3. **`remove(key, value)` tests expiry before the value, and returns true only for `EXPLICIT`.** "`} else if (hasExpired(node, expirationTicker().read()) && !isComputingAsync(ctx.oldValue)) {`
   `ctx.cause = RemovalCause.EXPIRED;` `} else if (node.containsValue(value)) {`" and "`return (removeCause == RemovalCause.EXPLICIT);`". Same file, v3.3.1.
4. **`CacheStats` and the eviction vocabulary.** "When an entry is evicted from the cache, `evictionCount` is incremented and the weight added
   to `evictionWeight`." and "Cache lookups that reuse a value loaded by a concurrent lookup increment `hitCount`."
   [`stats/CacheStats.java` v3.3.1](https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/stats/CacheStats.java).
   Guava: "Returns the number of times an entry has been evicted. This count does not include manual invalidations."
   [`CacheStats` 33.0.0](https://guava.dev/releases/33.0.0-jre/api/docs/com/google/common/cache/CacheStats.html).
   Micrometer: `FunctionCounter.builder("cache.eviction.weight", cache, c -> c.stats().evictionWeight())` with description "The sum of weights
   of evicted entries. This total does not include manual invalidations."
   [`CaffeineCacheMetrics` v1.14.0](https://github.com/micrometer-metrics/micrometer/blob/v1.14.0/micrometer-core/src/main/java/io/micrometer/core/instrument/binder/cache/CaffeineCacheMetrics.java).
5. **The eviction listener is synchronous with the removal and must not touch the cache.** "The cache will invoke this listener during the
   atomic operation to remove the entry." and "The `evictionListener` **must not** modify this cache and any exception thrown will *not* be
   propagated to the `Cache` user, only logged via a `Logger`." `Caffeine.java` [v3.3.1](https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/Caffeine.java).
6. **On an async cache the eviction listener receives the value, only when the future is ready.** "`// Must have been completed and be non-null to be eligible for eviction`
   `V value = Async.getIfReady(future);` `if (value != null) {` `delegate.onRemoval(key, value, cause);`". `Async.java`
   [v3.3.1](https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/Async.java).
7. **Weight 0 is never size-evicted.** "`} else if (ctx.cause == RemovalCause.SIZE) {` `int weight = node.getWeight();` `if (weight == 0) {` `ctx.resurrect = true;`"
   in `evictEntry`, and "`// Skip over entries with zero weight`" in `evictFromMain`. `BoundedLocalCache.java` v3.3.1.
8. **An in-flight future weighs 0 by design.** "When the value is being loaded this weigher returns `0` to indicate that the entry should not
   be evicted due to a size constraint." `Async.AsyncWeigher` Javadoc, `Async.java` v3.3.1.
9. **An oversized entry is evicted as `SIZE` when its weight is applied.** "`if (weight > maximum()) {` `evictEntry(node, RemovalCause.SIZE, expirationTicker().read());`"
   (`AddTask.run`) and "`if (node.getPolicyWeight() > maximum()) {` `evictEntry(node, RemovalCause.SIZE, expirationTicker().read());`" (`UpdateTask.run`).
   `BoundedLocalCache.java` v3.3.1. Test: `EvictionTest.evict_zero_async` (`assertThat(context).notifications().withCause(SIZE)`).
10. **A negative weight throws.** "`@return` the weight of the entry; must be non-negative" (`Weigher.java`) and "`requireArgument(weight >= 0, "weight cannot be negative: %s", weight);`"
    (`BoundedWeigher`), both v3.3.1; `Caffeine.weigher` wraps with `Weigher.boundedWeigher`. The async completion path logs
    "`logger.log(Level.WARNING, "Exception thrown during asynchronous load", t);`" in `LocalAsyncCache.handleCompletion`'s `catch (Throwable t)`.
11. **The completion callback never re-inserts.** "`// update the weight and expiration timestamps`
    `cache().replace(key, castedFuture, castedFuture,` `/* shouldDiscardRefresh= */ false, /* quietly= */ true);`" (`LocalAsyncCache.handleCompletion`)
    and, in `BoundedLocalCache.replace`, "`Node<K, V> node = data.computeIfPresent(nodeFactory.newLookupKey(key), (k, n) -> {`" and
    "`} else if (!n.containsValue(oldValue)) {`". v3.3.1.
12. **`invalidateAll` on a loading entry is documented as undefined; `synchronous()` may block.** "The behavior of this operation is undefined
    for an entry that is being loaded (or reloaded) and is otherwise not present." (`Cache.java`) and "If a modification is made to a mapping
    that is currently loading, the operation blocks until the computation completes." (`AsyncCache.synchronous()`), plus `AsMapView.remove`:
    "`CompletableFuture<V> oldValueFuture = delegate.remove(key);` `return Async.getWhenSuccessful(oldValueFuture);`" (`LocalAsyncCache.java`). v3.3.1.
13. **No scheduler, no thread, weakly held maintenance.** "By default, `Scheduler#disabledScheduler()` is used." (`Caffeine.scheduler`); `protected @Nullable Pacer pacer() { return null; }`
    (`BoundedLocalCache`); `static final class PerformCleanupTask extends WeakReference<BoundedLocalCache<?, ?>> implements Runnable`. v3.3.1.
14. **A rejected maintenance task is run on the caller with a warning.** "`logger.log(Level.WARNING, "Exception thrown when submitting maintenance task", t);`
    `maintenance(/* ignored */ null);`" (`scheduleDrainBuffers`); and "An immediate scheduling cannot be performed on a custom executor because it
    may use a caller-runs policy." / "Otherwise, it will be handled due to other cache activity." (`rescheduleCleanUpIfIncomplete`). v3.3.1.
15. **The mapping function's contract.** "`@throws RuntimeException or Error` if the mappingFunction does when constructing the future, in which
    case the mapping is left unestablished" and "`mappingFunction` **must not** modify this cache during the computation." (`AsyncCache.get`);
    "Some attempted update operations by other threads may be blocked while computation is in progress." (`AsyncCache.asMap()`). v3.3.1.
16. **An expired node is replaced by a new mapping in the same call.** `doComputeIfAbsent`: "`} else if (hasExpired(n, ctx.now = expirationTicker().read())`
    `&& !isComputingAsync(ctx.oldValue)) {` `actualCause = RemovalCause.EXPIRED;`" followed by `notifyEviction(ctx.nodeKey, ctx.oldValue, actualCause);`
    and `ctx.newValue = mappingFunction.apply(key);`. v3.3.1.
17. **On completion the user's `expireAfterCreate` runs.** "`long duration = (currentDuration > MAXIMUM_EXPIRY)`
    `? delegate.expireAfterCreate(key, value, currentTime)` `: delegate.expireAfterUpdate(key, value, currentTime, currentDuration);`
    `return Math.min(duration, MAXIMUM_EXPIRY);`" (`Async.AsyncExpiry.expireAfterUpdate`); "`return expiresVariable() && ((node.getVariableTime() - now) > MAXIMUM_EXPIRY);`"
    (`isExpirationDeferred`); "`static final long MAXIMUM_EXPIRY = (Long.MAX_VALUE >> 1); // 150 years`". v3.3.1.
18. **Zero means expired at once.** "`long duration = Math.max(0L, expiry.expireAfterUpdate(key, value, now, currentDuration));` `return expiresAt(now, duration);`"
    and "`| (expiresVariable() && (now - node.getVariableTime() >= 0));`" (`hasExpired`). v3.3.1.
19. **Nullability annotations.** `@NullMarked public interface Expiry<K, V>`, `@NullMarked @FunctionalInterface public interface Weigher<K, V>`,
    `void onRemoval(@Nullable K key, @Nullable V value, RemovalCause cause);` (`RemovalListener`); POM:
    "`<artifactId>jspecify</artifactId>` ... `<version>1.0.1</version>` `<scope>compile</scope>`"
    ([caffeine-3.3.1.pom](https://repo1.maven.org/maven2/com/github/ben-manes/caffeine/caffeine/3.3.1/caffeine-3.3.1.pom)). Kotlin:
    "By default, the Kotlin compiler reports nullability mismatches for JSpecify annotations as errors."
    ([Kotlin: Java interop](https://kotlinlang.org/docs/java-interop.html), read 2026-11-03).
20. **Size eviction is eventual; a same-thread executor and `cleanUp()` settle it.** "the cache **may evict an entry before this limit is
    exceeded or temporarily exceed the threshold while evicting**" (`Caffeine.maximumWeight`); "A test may instead prefer to configure the
    cache to execute tasks directly on the same thread." (`Caffeine.executor`); "As the removal of expired entries is performed during a
    maintenance cycle, use `Cache.cleanUp()` to trigger one immediately when tests depend on eviction having occurred."
    ([wiki Testing](https://github.com/ben-manes/caffeine/wiki/Testing)). v3.3.1.
21. **`recordStats()` selects the system ticker for statistics.** "`if (cache.isRecordingStats()) {` `assertThat(cache.statsTicker()).isSameInstanceAs(Ticker.systemTicker());`"
    (`LocalCacheSubject.checkStats`, test fixtures) and the generated `statsTicker()` method "`return $T.systemTicker()`" (`javaPoet/.../local/AddStats.java`). v3.3.1.
22. **Default `LoggerFinder` routes to JUL.** "The system default `LoggerFinder` implementation uses `java.util.logging` as the backend framework
    when the `java.logging` module is present. It returns a logger instance that will route log messages to a `java.util.logging.Logger`."
    ([`System.LoggerFinder`, JDK 11](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/System.LoggerFinder.html), read 2026-11-03).
    Caffeine logs via "`static final Logger logger = System.getLogger(Async.class.getName());`" and `Logger logger = System.getLogger(LocalAsyncCache.class.getName());`.
23. **Dependents run most-recently-registered first (implementation behaviour).** `CompletableFuture.postComplete`: "Pops and tries to trigger
    all reachable dependents." with `STACK.compareAndSet(f, h, t = h.next)` on the stack head
    ([jdk11u `jdk-11.0.25-ga`](https://github.com/openjdk/jdk11u/blob/jdk-11.0.25-ga/src/java.base/share/classes/java/util/concurrent/CompletableFuture.java)).
    Order is not part of the class Javadoc.
24. **3.3.1 is the current release.** GitHub releases of `ben-manes/caffeine`: `3.3.1 Latest v3.3.1 2026-10-13T03:04:00Z`, preceded by `3.3.0` (2026-09-21);
    the 3.3.1 notes list "Fixed various minor issues found using AI audits". Read 2026-11-03.

## Gaps

- **Nothing was compiled or run.** The Kotlin shapes (the `get(key) { _, _ -> ... }` overload resolution between the `Function` and
  `BiFunction` forms, a `RemovalListener` SAM lambda against `@Nullable` parameters under strict JSpecify mode, an `object : Expiry<...>`) are
  reasoned from the signatures and the Kotlin docs; the first compile of `CaffeineResponseCache` settles them.
- **Behaviour the source defines and no test of ours pins yet** (each is a Level 2 test with the same-thread executor): (1) `invalidate`
  of an in-flight key starts a second load and the late completion neither re-inserts nor overwrites; (2) `clear()` during loads leaves
  the futures completing and waiters served; (3) a `Found` with weight `0`, negative, `maxWeight` and `maxWeight + 1` ends with the
  stored weights and zero-lifetime treatment recommended; (4) the `SIZE`-only listener count equals inserted minus resident after
  `cleanUp()` while failed and zero-TTL entries add nothing; (5) `asMap().remove(key, future)` returns `false` and removes a
  lifetime-zero entry.
- **The JUL handler recipe was not run.** That a root-logger handler sees `System.Logger` output from Caffeine on the module's test
  classpath, and at which `Logger` name and level, is reasoned from the `LoggerFinder` Javadoc; the positive-control test in F10 is what
  establishes it, on JDK 11 and on the newest JDK in CI.
- **The Level 2 deterministic reproduction of the waiter-first ordering (F11, last bullet) is a hypothesis.** It depends on how
  `asDeferred()` resumes under an unconfined dispatcher, which is a sibling question's subject, and on the completion thread running the
  waiter's callback before Caffeine's; confirm with one run. The 4c provocation parameters (joiners per key, rounds) are untuned.
- **`CompletableFuture` dependent order** is observed from JDK 11.0.25 source only; a newer JDK's order was not checked. The design does
  not rely on the order for correctness (the waiter-side removal is unconditional), only the 4c test's sensitivity does.
- **Whether `getIfPresent` on a lifetime-zero entry between completion and the callback ever counts as an `EXPIRED` eviction** was not
  traced to a single path; it does not matter to the design, because the listener filters on `SIZE`.
- **The exact moment `Policy.Eviction.weightedSize()` includes a just-completed entry** (after `replace` queues the `UpdateTask`, before the
  drain) was not needed and not traced; tests call `cleanUp()` first.
- The test classpath (slf4j 2.0.13, logback) is taken from the build files, which show `logback-classic:1.5.6` in the
  convention plugins and `slf4j-api:2.0.13` in the module, and no `slf4j-jdk-platform-logging`. No `System.LoggerFinder` provider on the
  classpath was verified by listing the resolved test runtime classpath.
