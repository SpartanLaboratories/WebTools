# The response cache port and its Caffeine default

## Question

How should the response cache port behave in `commonMain` of `webtools-http`, and how does its Caffeine 3.3.1 default realise it on Kotlin 2.2.0, kotlinx-coroutines 1.10.2 and Java 11 bytecode?

An answer has to settle: the port's shape and key/value typing; what is stored (decoded values or bytes); how a confirmed miss is known and represented; the Caffeine configuration (`AsyncCache`, `Expiry`, `Ticker`, bound, executor, `Scheduler`, stats, listeners); coalescing and cancellation semantics with a library-owned scope; `maxAge`; key canonicalisation and credential hygiene; and whether non-JVM targets need a default.

Requirements the answer must meet:
- One async cache per client; a time to live per endpoint; a size bound.
- Failures are not cached; confirmed misses are cached briefly.
- Coalesced loads run in a library-owned scope and are awaited with `asDeferred().await()`.
- A `maxAge` override serves callers that need freshness.
- The key is the endpoint and its canonical parameters, never the caller's tenant.
- The facade layers cache, then budget, then transport; the budget is acquired inside the load, so a hit costs nothing and N concurrent callers for one key cost one permit.
- The port is valid common Kotlin, with no Caffeine type in any public signature.
- Failures travel as `kotlin.Result`; no nullable results; cancellation propagates.
- Policy numbers are constructor parameters with defaults.
- Level-2 tests run on fake time with no network.
- One injected monotonic time source is shared with the budget clock and coroutine `delay`; this document recommends the minimal interface the cache needs.

Evidence labels: **documented** (official documentation, a specification, or source/Javadoc at a pinned tag), **observed practice** (what a mature library visibly does), **single source** (one author's opinion), **measured** (run by me in throwaway probes outside the repository: Caffeine 3.3.1, kotlinx-coroutines and `-test` 1.10.2, Kotlin 2.2.0, Gradle 9.7.1, JDK 23 on Windows; a final compile with `-Xjdk-release=11`). Measured results come from those probes and have no source to re-open; the outside-world claims they rest on are in *Load-bearing claims*.

## Bottom line

- **Port.** `ResponseCache : AutoCloseable` with one suspend method, `getOrLoad(key: CacheKey<V>, policy: CachePolicy, maxAge: Duration? = null, loader: suspend () -> Result<Lookup<V>>): Result<Lookup<V>>`, plus `invalidate`, `invalidateAll`, `stats` and `close`. The key is a phantom-typed value (`CacheKey<V>`), the store inside is erased, and the only unchecked cast sits in the default. `Lookup` is a sealed `Found(value, weight)` / `Missing`, so a confirmed miss is a value, not a null and not a failure. The port, the key, the policy and `Lookup` compiled unchanged for `jvm()` and `js(IR)` under Kotlin 2.2.0 with `explicitApi()` (measured). The only time dependency is `kotlin.time.TimeSource`, stable since Kotlin 1.9.
- **Store decoded values.** The load (budget, transport, status check, decode) runs inside the cache's loader, so a decode failure is a `Result.failure` and is never stored. Raw bytes are not stored by default, because the transport cap (5 MiB by default) times the entry count is the memory bound, against a few kilobytes for a trimmed decoded value. An endpoint that wants bytes decodes to `HttpResponse` itself; the port is generic over `V`.
- **Failures are values inside the future, not exceptional completions.** Completing the future exceptionally makes Caffeine log a `WARNING` with a stack trace through `System.Logger` for every failed load, and leaves a window in which the failed future is still joinable (45 of 2,000 immediate retries got the previous failure; measured). The recommended load completes the future normally with an internal `Entry` whose outcome is `Failed` and whose `Expiry` is zero, and every waiter conditionally removes that future (`asMap().remove(key, future)`) before returning (0 of 2,000; measured).
- **Coalescing and cancellation** (all measured): 50 concurrent callers cost 1 load; one waiter cancelling leaves the load and the other waiters intact; with `CompletionStage.await()` the same cancellation kills the shared future for everyone; if every waiter cancels, the load still runs to completion, spends its permit and lands in the cache; `close()` cancels in-flight loads and completes every waiter with a `Closed` failure (without the conversion a live waiter sees a bare `CancellationException`, which looks like its own cancellation). Run-to-completion is recommended over cancelling when the last waiter leaves (see Consideration 1).
- **`maxAge` is an age check on read with a compare-and-remove**, not `invalidate` before `get`. Under 50 concurrent callers on a 2-minute-old entry with `maxAge = 1.minutes`: invalidate-before-get ran 10 to 12 loads (three runs), the age check ran 1. `refreshAfterWrite` is not available on an `AsyncCache` at all (`buildAsync()` throws "refreshAfterWrite requires a LoadingCache") and is stale-while-revalidate, not a freshness guarantee.
- **Caffeine wiring.** `AsyncCache` with a variable `Expiry` reading the TTL from the entry; a `Ticker` derived from the injected `TimeSource`; `maximumWeight` with a weigher (weight 1 per entry by default, so it behaves as an entry bound); `recordStats()`; no `Scheduler`; no listeners; `Caffeine`'s own default executor in production and a same-thread executor in tests. Loads run in `CoroutineScope(SupervisorJob() + loadDispatcher)` owned by the cache and cancelled by `close()`; callers never see the caller context in the load.
- **No binding requirement fails.** One requirement is met at a stated cost: "Coalesced loads run in a library-owned scope" means the load does not inherit the first caller's coroutine context (lane, tracing elements), and a shared load carries the *first* caller's budget lane and wait cap; callers with a tighter deadline wrap their own wait in `withTimeout`, which is safe here because a waiter's cancellation does not cancel the load (measured).

## Findings

### F1. `AsyncCache.get` gives single-flight and drops failed futures; `Expiry` sees the value once the future completes (documented + measured)

- Caffeine 3.3.1 `AsyncCache.get` Javadoc: "The entire method invocation is performed atomically, so the function is applied at most once per key. If the asynchronous computation fails, the entry will be automatically removed from this cache."
- `Async.AsyncExpiry` returns `ASYNC_EXPIRY` (about 220 years) for an in-flight future and asks the delegate once the future holds a value; `LocalAsyncCache.handleCompletion` then calls `cache().replace(...)` with the comment "update the weight and expiration timestamps".
- Measured with a manual `AtomicLong` ticker: an in-flight future was never expired (the `Expiry` was not consulted); `expireAfterCreate` was called once, with the **value**, on completion; the TTL ran **from completion** (a future completed at t=5 s with a 10 s TTL was present at t=14 s and absent at t=16 s). The TTL therefore does not include load time, which is the right behaviour for a slow upstream.
- The weight behaves the same way: `Async.AsyncWeigher` weighs an in-flight future as zero, then the real weight is applied at completion. Measured: five entries of weight 40 against `maximumWeight(100)` settled at 2 entries.

### F2. Failed futures are removed, but not quietly, and not instantly (documented + measured)

- `LocalAsyncCache.handleCompletion` logs `logger.log(Level.WARNING, "Exception thrown during asynchronous load", error)` for any exceptional completion that is not a `CancellationException` or `TimeoutException`; `logger` is `System.getLogger(...)`. Measured: every exceptionally completed future (in the first prototype, which used `scope.future { }` and threw the typed failure) printed a `WARNING` with a stack trace to the default Java logging handler. A typed failure model that returns `RateLimited` or `UpstreamStatus` through `Result` would trip this on every refused call.
- The removal happens in a `whenComplete` callback registered when the entry was created. `CompletableFuture` runs dependents most-recently-registered first, so a waiter's callback (registered later) can run, and the waiter can come back and call the cache again, before the removal runs. Measured: immediate retry after a failed load received the previous failure in 45 of 2,000 trials (39 in a first run) when the waiter did not remove the entry itself, and 0 of 2,000 when each waiter called `asMap().remove(key, future)` (a documented `ConcurrentMap` operation, comparing the future by identity) before returning.
- The same race applied to a fatal `Error` thrown by the loader: with the exceptional-completion variant the next call joined the dead future and rethrew the error until the waiter removed the entry itself.
- The design that follows from this: complete the future normally with an `Entry(outcome = Failed(throwable), ttl = 0)`. `BoundedLocalCache.hasExpired` treats `now - variableTime >= 0` as expired, so a zero duration is expired from the moment the `Expiry` is applied (measured: a failed entry was never served after the waiters returned). Caffeine's Javadoc does not state the zero-duration case; the explicit removal is therefore the guarantee and the zero TTL the safety net.
- Side effects of the value variant (measured): Caffeine's own `loadFailureCount` stays 0 because the future completes with a value, and an entry removed by `Expiry` counts as an eviction. The cache keeps its own failed-load counter if it reports one.

### F3. `CompletionStage.await()` cancels the shared future; `asDeferred().await()` does not (documented + measured)

- kotlinx-coroutines 1.10.2: "This method is intended to be used with one-shot futures, so on coroutine cancellation the [CompletableFuture] that corresponds to this [CompletionStage] ... is cancelled. If cancelling the given stage is undesired, `stage.asDeferred().await()` should be used instead."
- `asDeferred()`'s own Javadoc says the future "is cancelled when the resulting deferred is cancelled". Awaiting a `Deferred` does not cancel it when the awaiting coroutine is cancelled, so the pattern is safe as long as the library never holds and cancels the `Deferred` itself (it creates one per call and drops it).
- Measured (library-owned scope, `Dispatchers.Default`): with `CompletionStage.await()`, cancelling one of two waiters failed the other with `CancellationException` and left the shared future cancelled; with `asDeferred().await()` the other waiter received the value and the future stayed alive. Through the cache: three waiters, the first (the one that started the load) cancelled, the other two received the value, one load ran.

### F4. A load that nobody waits for any more still completes and populates the cache (measured; design choice)

- Measured: two waiters, both cancelled at 100 ms of a 400 ms load: the loader ran to completion (one "permit" counted), and the next caller found the value in the cache without a second permit.
- Caffeine cannot reference-count waiters. The alternative is to cancel the load when the last waiter leaves, which is what Store5 does (F10) and is a source of races (Consideration 1).

### F5. Closing: waiters see a bare `CancellationException` unless converted (measured)

- Cancelling the library scope cancels the `future { }` coroutine, the shared future completes with a `CancellationException`, and `asDeferred().await()` rethrows it in every waiter. Measured: the waiters' own `isActive` was `true` when it arrived. A waiter that lets it propagate ends silently inside any parent `launch` (it looks like normal cancellation).
- Two ways to handle it: catch `CancellationException` in the awaiter and call `currentCoroutineContext().ensureActive()` (rethrows if the waiter itself is cancelled; otherwise the load was cancelled, so return `Result.failure(Closed)`), or avoid the exception altogether. The recommended structure does the second: the cache creates the `CompletableFuture<Entry>` itself inside the Caffeine mapping function, starts the load with `scope.launch` afterwards, and registers `job.invokeOnCompletion { cause -> ... }` that completes the future with a `Closed` entry on cancellation (and exceptionally only for a fatal `Error`). Measured: three waiters not themselves cancelled got `Result.failure(Closed)`; the loader coroutine saw its cancellation; a call after `close()` returned `Closed` immediately.

### F6. Start the load outside the Caffeine mapping function (documented + measured)

- `AsyncCache.get`'s Javadoc warns that the mapping function "must not modify this cache during the computation", and the computation holds a per-key lock. `scope.future { }` with an eager dispatcher (`Dispatchers.Unconfined`, `UnconfinedTestDispatcher`) runs the loader body inline inside that lock.
- Measured: a loader that calls the cache again for a different key worked in both variants (no deadlock). This probe did not reach a same-bin or same-key re-entry, so it demonstrates no failure; the argument is the Javadoc contract. Starting the load after `cache.get` returns (only for the call that created the entry) makes the order independent of the dispatcher and removes the question.
- It also gives control of the completion path (F2, F5): the future is always completed with a value unless the load hits a fatal `Error`.
- If `close()` cancels the scope between the cache's closed check and the `launch`, the new coroutine starts cancelled and `invokeOnCompletion` is relied on to complete the future so no waiter hangs. That interleaving was not provoked (Known gaps); the in-flight-at-close case was (F5).

### F7. `maxAge`: an age check on read with a compare-and-remove (measured; RFC-aligned)

Three options, measured:
- **`invalidate` before `get`.** 50 concurrent callers on a 2-minute-old entry, each with `maxAge = 1.minutes`: **11 loads** (runs gave 12, 10 and 11). Each caller invalidates the entry the previous caller just loaded. It also races with coalescing: a caller that invalidates while another caller's load is in flight orphans that load, and a second load for the same key starts (2 loads in the probe).
- **Age check with compare-and-remove.** `cache.get(key, ...)`; if the future is complete and `now - entry.storedAt > maxAge`, call `cache.asMap().remove(key, thatFuture)` and loop (at most twice). Concurrent stale-observers all try to remove the same future; one succeeds, the rest see a no-op and rejoin the new load. **1 load for 50 callers.** An in-flight future counts as fresh (its data cannot be older than the load's duration, bounded by the call timeout); `maxAge = Duration.ZERO` served an entry stamped at the same clock reading (`age <= maxAge`), reloaded an entry 1 ms older, and joined an in-flight load. A plain caller after a `maxAge` reload sees the refreshed entry.
- **`refreshAfterWrite`.** `Caffeine.buildAsync()` calls `requireNonLoadingCache()`, which throws `IllegalStateException("refreshAfterWrite requires a LoadingCache")`. It is also stale-while-revalidate ("the old value is returned") and swallows refresh exceptions, which is the wrong contract for a caller that asked for freshness.
- The age is read from the injected `TimeSource` at completion, so `maxAge` is tested on fake time. A custom `TimeSource` may return negative elapsed time (documented for non-monotonic sources), so clamp ages at zero.
- RFC 9111 gives the same semantics to a client: the `max-age` request directive "indicates that the client prefers a response whose age is less than or equal to the specified number of seconds." The parameter is named accordingly.
- A `maxAge` greater than the endpoint TTL has no effect, since the entry expires first.

### F8. Time: `kotlin.time.TimeSource` is the minimal interface; Caffeine's `Ticker` is derived from it (documented + measured)

- `TimeSource` is "Since Kotlin 1.9"; its only operation is `markNow()`. `TimeSource.Monotonic` is the production source; `kotlin.time.TestTimeSource` ("programmatically updatable readings") and `TestScope.testTimeSource` (kotlinx-coroutines-test 1.10.2, `@ExperimentalCoroutinesApi`, "The test scheduler as a TimeSource") are the fakes.
- The cache needs two readings from it: Caffeine's `Ticker` (a nanosecond `Long`) and the entry's `storedAt` stamp. Both come from one mark taken at construction: `val origin = timeSource.markNow()`, `ticker = Ticker { origin.elapsedNow().inWholeNanoseconds }`. Nothing wall-clock is needed (the budget's UTC-day windows are a separate need).
- Measured on Kotlin 2.2.0: `TimeSource`, `TestTimeSource` and `Duration` compile with no opt-in. Under `runTest`, with `CaffeineResponseCache(timeSource = testTimeSource, loadDispatcher = StandardTestDispatcher(testScheduler))`: a loader's `delay(5.seconds)` consumed 5,000 ms of virtual time and no real time; a `Found` entry was a hit at completion + 9 min 50 s and expired at + 10 min 10 s (10-minute TTL); a `Missing` entry was a hit at + 20 s and reloaded at + 40 s (30 s miss TTL); ten concurrent callers cost one load under both `StandardTestDispatcher` and `UnconfinedTestDispatcher`.
- Caffeine reads the ticker on its own threads and during maintenance; `Caffeine.ticker` Javadoc: "Specifies a nanosecond-precision time source for use in determining when entries should be expired or refreshed. By default, System#nanoTime is used."

### F9. Executor, scheduler, bound and stats (documented + measured)

- **Executor.** Caffeine's executor runs maintenance, removal notifications and async computations; the default is `ForkJoinPool.commonPool()`; its Javadoc says "A test may instead prefer to configure the cache to execute tasks directly on the same thread." Measured with `maximumWeight(10)` and 20 entries of weight 1: with the common pool the size read immediately afterwards was 11 to 12 and `cleanUp()` could still leave 11 until maintenance finished; with a same-thread executor the size settled at 10 after `cleanUp()` and the eviction count was 10. Size eviction is therefore eventual: the Javadoc says the cache "may evict an entry before this limit is exceeded or temporarily exceed the threshold while evicting".
- A size test on fake time is deterministic only on one thread: with a test dispatcher and a same-thread executor completion callbacks run on the test thread. With `Dispatchers.Default` loads, the weight update happens on the completing thread after the waiter may have resumed, and an immediate size read was racy (one run read 20 entries before `cleanUp()`); after the callbacks settled the bound held (2 of 2 runs).
- **Scheduler.** "By default, Scheduler#disabledScheduler() is used." Without one, expired entries are never served (checked on every read) but stay in memory until the next write-triggered maintenance. Measured: five entries expired by fake time still counted 5 until one write, after which the count was 1. A real-time `Scheduler` would schedule against the real clock while the ticker is fake, so none is configured. The `maximumWeight` bound caps the memory held by unreclaimed expired entries.
- **Stats.** `recordStats()` costs bookkeeping on every operation (Javadoc), small at this scale. Measured: with 50 coalesced callers and one later call, `hitCount` was 50 and `missCount` 1: callers that join an in-flight load count as hits, so "hits" is not "answered without a load", and the cache keeps its own counter for loads started.
- **Listeners.** None are needed: nothing in a cached value holds a resource to release, and size evictions are in the stats.
- **Expiry clamp.** `AsyncExpiry` caps a delegate's duration at `MAXIMUM_EXPIRY`, so `Duration.INFINITE` (`inWholeNanoseconds` is `Long.MAX_VALUE`) is accepted.

### F10. What is stored: decoded values, with the load inside the cache (observed practice + measured)

- BotTools' research recommends caching trimmed decoded values; the stack's own facts agree: the transport cap is 5 MiB by default for `Trusted` (`http-transport.md:291`), so a bound of 1,000 entries over raw bodies is a 5 GB worst case, against kilobytes for decoded values.
- The load order inside `getOrLoad` is budget, transport, status check, decode, so only a successful decode is stored and a decode failure is a `Result.failure` (never cached; the permit has been spent, the budget's `complete` still runs). The facade needs no second cache layer for raw bytes.
- The generic `V` leaves the choice to the endpoint: decoding to `HttpResponse` stores bytes, headers and URL; the feeds module can keep `ETag`/`Last-Modified` with the entry that way. The cache offers no way to read an *expired* entry, so a conditional GET revalidation needs the validators held by the caller (feeds research), not by the cache.
- Memory bound: weight 1 per `Found` by default (so `maxWeight` is an entry count); a loader that knows the raw size may report a weight. An async entry's weight is applied at completion (F1).

### F11. Confirmed misses are a value; the classification belongs to the endpoint (documented + observed practice)

- HTTP itself caches negative answers: RFC 9110 lists 404, 405 and 410 among the status codes that "can be reused by a cache with heuristic expiration". Guava's `CacheLoader.load` documents "the value associated with key; must not be null", the reason Guava users model absence with `Optional` or a sentinel; `Lookup.Missing` is that sentinel, typed.
- Representation: `Lookup.Found(value, weight)` or `Lookup.Missing`, with a `missTtl` in the `CachePolicy` (tens of seconds to a few minutes; measured working at 30 s and at the 60 s proposed default). A failure that is cached would need a predicate over `Throwable`s inside the cache; a value needs none and keeps "failures are not cached" absolute.
- How a miss is known is not the cache's business: the facade's loader returns `Missing` when the endpoint declares the response a miss. Two sources: an endpoint-declared status set (typically `404`, optionally `410`) checked before decode, and the decode function reporting an empty result (a `200` carrying `null` for the object). Both fit `Result<Lookup<V>>`.
- `Missing` is not a failure for the budget: the permit is spent either way; caching it for `missTtl` is what stops repeated guesses from spending more.

### F12. Keys: structured, decoded, no tenant, no credentials (documented + observed practice + measured)

- Practice: HTTP's cache key is method plus target URI (RFC 9111 §2: "composed from, at a minimum, the request method and target URI"). Ktor 3.6.0's `HttpCache` keys storage by the full `Url` plus `Vary` values, so a query-string credential is part of its key. Apollo Kotlin 4.4.1's `CacheKey(typename, values)` builds a string by appending the values with no separator (`values.forEach { append(it) }`), so `("T", "a", "b")` and `("T", "ab")` are the same key.
- Recommended: a structured key, `endpointId: String` plus `params: List<Pair<String, String>>` of decoded names and values, sorted by name with a stable sort (Kotlin's `sortedBy` "is stable"), so repeated names keep the caller's order. This removes encoding differences (`%20` against `+`), because no URL text is involved. It never merges two different parameter lists; it can only miss on equivalent-but-reordered repeated values, which costs one extra call, never a wrong answer. Names, values and the endpoint id are case-sensitive. No Unicode normalisation (not available in common code); a caller wanting normalised values normalises them first.
- `equals`/`hashCode` are explicit over `endpointId` and `params` (`List` equality is common), the hash is computed once, and `toString` prints the endpoint id and parameter **names** only, so a log line never carries values.
- **No tenant and no credential.** The facade builds the key from the endpoint id and the declared parameters. A credential travels as a sensitive header or as an endpoint-level request decoration (`http-transport.md`, F10 of that file), never as a key parameter. An endpoint whose response depends on the credential (a private per-user resource) must not use a shared cache entry: RFC 9111 §3.5 states a shared cache "MUST NOT use a cached response to a request with an Authorization header field ... to satisfy any subsequent request" unless the response allows it. The mechanism is a `ttl` of `Duration.ZERO` (coalescing only, no storage; same mechanism as the zero TTL of F2) or an explicit scope parameter in the key; the facade research decides which. The key carries a phantom `V` for type safety only; `V` is not part of equality (Ktor's `AttributeKey` does record its type in a `data class`; here ids are unique per client, and a duplicate id with a different `V` is a programming error).

### F13. Non-JVM targets: the port is enough now (measured + observed practice)

- Measured: the port, key, policy, `Lookup`, `CacheStats` and a pass-through implementation compiled for `jvm()` and `js(IR) { nodejs() }` with Kotlin Gradle plugin 2.2.0, `explicitApi()` and coroutines 1.10.2. They use only `Duration`, `Result`, `List`, `Pair`, `AutoCloseable` (`kotlin.AutoCloseable` is common since Kotlin 2.0, `http-transport.md:151`) and suspend lambdas. A check-only `js` module would validate the port as it stands.
- A common coalescing default is not free. cache4k (the KMP cache) needs `atomicfu`, an expect/actual `ConcurrentMutableMap`, and a keyed `Mutex` registry (`KeyedSynchronizer`); its issue tracker has an open "Fix linearizability issues surfaced by Lincheck" (#47) and a performance report (#28). Nothing consumes a non-JVM target today.
- A trivial common `ResponseCache.Disabled` (the loader runs on every call, no sharing, no storage) is cheap, is the only implementation constructible in common code, and is the explicit opt-out (it forfeits "N callers cost one permit", which its KDoc states).

### F14. Logging (documented: repository rule and prior research)

- `commonMain` logs nothing; `slf4j-api` is an `implementation` dependency of `jvmMain` only (`kmp-build-topology.md:157` and `:238`). The port reports through `Result` and `stats()`. The Caffeine default logs lifecycle (INFO on create and close), load completions at DEBUG with the key's `toString()` (endpoint id and parameter names), and nothing per hit. Caffeine's own diagnostics go through `System.Logger`, not slf4j; this design avoids the one frequent source (F2).

## How comparable projects do it

| Project (version) | Shape | Stampede / cancellation | Negative caching and staleness | Survived or regretted |
|---|---|---|---|---|
| **Aedile** `com.sksamuel.aedile:aedile-core` 3.0.4 (2026-05-03; Caffeine 3.2.4, coroutines 1.10.2) | `class Cache<K : Any, V>(AsyncCache<K, V>)` with `suspend fun get(key, compute: suspend (K) -> V): V`; `fun underlying(): AsyncCache<K, V>` returns the Caffeine type | Per call `CoroutineScope(coroutineContext.minusKey(Job) + SupervisorJob())` (the **first caller's** context minus its Job) and `scope.future { }`, then `.thenApply { it }.await()` so each caller cancels a private dependent future | none (`getOrNull` with a nullable compute) | 2.0.0 "Fixed coroutine cancellation propagation #30" (a throwing compute surfaced as `JobCancellationException`); #57, opened 2026-04-07 and fixed by PR #67 on 2026-04-16 (in 3.0.4): the compute ran in the caller's scope and `CompletableFuture.await()` cancelled the shared future, so one cancelled caller failed every other waiter. The Caffeine type in `underlying()` is the leak this repository forbids. Its loader inherits the first caller's context elements, which here would be a tenant. |
| **cache4k** 0.14.0 (2025-01-14; `main` 0.15.0-SNAPSHOT) | `interface Cache<in Key : Any, Value : Any>` with `suspend fun get(key, loader: suspend () -> Value): Value`, `Cache.Builder.timeSource(TimeSource)` (default `TimeSource.Monotonic`), `expireAfterWrite`, `maximumCacheSize` | Keyed `Mutex`; the loader "is executed on the caller's coroutine context"; waiters block, then each re-checks. If the first loader throws, the mutex is released and the next waiter runs **its own** loader | none; a null return means absent | The common-code `TimeSource` injection is the part to copy (and `FakeTimeSource`). The per-waiter retry on failure is a stampede on a failing upstream: N waiters, N sequential permits. Issue #14 "Cache is not synchronised by key" led to the mutex registry. |
| **Store5** (`5.1.0-beta01`, 2026-09-08) | `Store<Key, Output>` over a `Fetcher`, `MemoryPolicy`, `StoreReadRequest.cached/fresh`, `Validator`; builder `.scope(CoroutineScope)` | `FetcherController` keeps "one and only 1 fetcher for a given Key", ref-counted: when the last downstream releases, the multicaster closes and the fetch stops | `Fetcher` errors are `FetcherResult.Error` and are not stored; `fresh` skips all caches | A library-owned scope that is injectable (the builder documents that Store "will open a global scope for management of shared responses") matches this design. Ref-counting is the other answer to "all waiters cancel" and carries its own bugs: "Fix multicaster race that leaves a new downstream without a producer" (#740, alpha10). The whole stack (Flow, source of truth, converters) is far larger than this need. |
| **Ktor** 3.6.0 `HttpCache` | A client plugin keyed by `Url` + `Vary`; `CacheStorage` (suspend `store`/`find`/`findAll`/`remove`), `Unlimited` by default | No in-flight sharing (no match for in-flight handling in `HttpCache.kt`): N concurrent identical requests are N network calls | Follows `Cache-Control` and validators | The old non-suspending abstract class `HttpCacheStorage` is `@Deprecated(level = ERROR)` in favour of the suspend `CacheStorage` interface (make the port suspend from the start). The `Unlimited` default is a memory regret this design avoids by always bounding. Keys include the whole URL, query included. |
| **Apollo Kotlin** 4.4.1 and `apollo-kotlin-normalized-cache` 1.0.9 | `CacheKey(key: String)`, normalized records, `CacheResolver` | n/a (GraphQL document model) | Expiration design: a received date stored with each field; "if its age is greater than the configured max age, it will result in a cache miss", with a `maxStale` allowance | The stored-timestamp age check on read is the pattern chosen for `maxAge`. The string `CacheKey` built by unseparated concatenation is the collision to avoid. |
| **OkHttp** 5.5.0 disk cache | `okhttp3.Cache` class | none for concurrent identical requests | HTTP semantics from response headers | Rejected: its README lists "Cache is not an interface with alternative implementations." It is also driven by response headers, not by endpoint policy. |
| **Guava** 33.4.8 `LoadingCache` | `CacheLoader.load` "must not be null", `maximumWeight`/weigher, `refreshAfterWrite` | Concurrent `get`s for one key "simply wait" for the loading thread | A load that throws surfaces as `ExecutionException`/`UncheckedExecutionException`; absence needs `Optional` or a sentinel (`load` may not return null) | The ancestor of the Caffeine API. The null rule is why `Lookup.Missing` exists; the blocking, thread-bound loader is why Caffeine's `AsyncCache` and not Guava is the base. |
| **HTTP caches** (RFC 9111, RFC 5861) | `max-age` request directive; `stale-while-revalidate` | "collapse requests" is a named cache behaviour | 404/410 are heuristically cacheable | The vocabulary the port borrows (`maxAge`, a negative TTL, and the later stale-while-revalidate extension). |

Stale-while-revalidate (not required): RFC 5861 "allows a cache to immediately return a stale response while it revalidates it in the background", and Guava/Caffeine offer it through `refreshAfterWrite` on loading caches only. With this design it is additive without touching the port's method: the `Entry` already holds `storedAt` and the TTL, so an `Expiry` of `ttl + staleFor` plus a "serve while a single background reload runs" step in the read path adds it, and `CachePolicy` (a class, not loose parameters) is where the new knob lives.

## Pitfalls and regrets

1. **`CompletionStage.await()` on a shared future.** Cancels it for every waiter (F3). Aedile lived with this from 2.0.0 until 3.0.4.
2. **Running the load in the first caller's scope or context.** Cancelling the first caller fails everyone (Aedile #57), and the context carries caller state (here: the tenant). Measured: the library-scope load did not see the caller's `CoroutineName`.
3. **Exceptional completion for domain failures.** Caffeine logs a `WARNING` with a stack trace per failure and leaves a window in which the failed future is joinable (F2: 45 of 2,000 retries).
4. **`CancellationException` reaching a live waiter.** Arrives with `isActive == true` when the library scope is closed; looks like the waiter's own cancellation to a parent (F5).
5. **`invalidate` before `get` for `maxAge`.** A reload stampede and an orphaned in-flight load (F7).
6. **One loader per waiter after a failure** (cache4k's mutex design) or **a shared `SupervisorJob`-less scope.** The first turns one failing upstream call into N permits; with a plain `Job` instead of `SupervisorJob`, one failing load would cancel the scope and every other load.
7. **Priority inversion through a shared load.** The load is built from the first caller's loader, so it uses that caller's lane and `maxWait`. An interactive caller joining a background poll's load waits as long as that load takes. The interactive caller bounds its own wait with `withTimeout` around `getOrLoad`; that cancels only the waiter (F3, measured). The facade owns that deadline.
8. **Unbounded default storage.** Ktor's `Unlimited`. The default here is always bounded.
9. **String-concatenated or URL-text keys.** Collisions (Apollo) and credentials in the key (Ktor, if a query-string key is used).
10. **Reading size right after a write.** Caffeine's eviction and weight application are eventual (F9); tests call `cleanUp()` (internal to the default) after the completion callbacks, and run on one thread.
11. **A real-time `Scheduler` beside a fake ticker.** It would schedule on the real clock (F9). Leave it off.
12. **`Result` mangling.** `getOrLoad` compiles to `getOrLoad-_Lh1JCU` (it returns `Result` and takes a nullable `Duration`; measured with `javap`). Kotlin callers are unaffected; the port is an Experimental seam implemented from Kotlin. The prior transport research recorded the same for `execute` (`http-transport.md:152`).

## Recommendation for this repository

Build `ResponseCache` as a small port in `commonMain` and a `CaffeineResponseCache` in `jvmMain`, with the shape below. Reject the obvious alternatives: a typed cache per endpoint (it multiplies the size bound and the maintenance state and cannot give one bound per client); wrapping Aedile (it pins Caffeine 3.2.4 against the required 3.3.1, exposes `AsyncCache`, and still has the first-caller-context behaviour); `scope.future { }` with exceptional completion for failures (F2); and invalidate-before-get for `maxAge` (F7).

### Common port (`commonMain`, package `com.spartanlabs.webtools.http`; names provisional)

```kotlin
// Experimental seam: the single opt-in marker applies to implementing/substituting.
// The supplied CaffeineResponseCache is usable without opting in.
public interface ResponseCache : AutoCloseable {
    public suspend fun <V : Any> getOrLoad(
        key: CacheKey<V>,
        policy: CachePolicy,
        maxAge: Duration? = null,                       // serve only an entry no older than this; null = any live entry
        loader: suspend () -> Result<Lookup<V>>,        // failure => not stored; Found/Missing => stored for ttl/missTtl
    ): Result<Lookup<V>>                                // never throws except cancellation and fatal Errors
    public fun invalidate(key: CacheKey<*>)
    public fun invalidateAll()
    public fun stats(): CacheStats                      // hits (joins count as hits), misses, evictions, failedLoads
    override fun close()                                // idempotent; cancels in-flight loads; later calls return Closed
    public companion object { public val Disabled: ResponseCache }  // pass-through: no storage, no sharing
}

public class CacheKey<V : Any> private constructor(public val endpointId: String, public val params: List<Pair<String, String>>) {
    public companion object { public operator fun <V : Any> invoke(endpointId: String, params: List<Pair<String, String>> = emptyList()): CacheKey<V> }
    // equals/hashCode over endpointId + params (sorted by name, stable); toString = id + parameter names only
}

public class CachePolicy(
    public val ttl: Duration,                           // per endpoint; ZERO = coalesce in-flight loads, store nothing
    public val missTtl: Duration = 60.seconds,          // confirmed misses
)

public sealed interface Lookup<out V> {
    public class Found<out V>(public val value: V, public val weight: Int = 1) : Lookup<V>   // weight >= 0
    public data object Missing : Lookup<Nothing>
}

public class CacheStats(public val hits: Long, public val misses: Long, public val evictions: Long, public val failedLoads: Long)
```

- Policy numbers are parameters: `ttl` (per endpoint, supplied by the endpoint descriptor), `missTtl` (default 60 s), `maxAge` (per call). `CachePolicy` is a class so a later `staleFor` (stale-while-revalidate) is an addition, not a change to the method.
- The `Closed` failure returned after `close()` is the failure model's `Closed` cause (`http-transport.md:249` lists it for the transport); the cache needs no failure type of its own.
- Only `Exception` from the loader is converted to `Result.failure`; cancellation propagates; a fatal `Error` completes the future exceptionally, is rethrown in every waiter, and the entry is removed.
- Facade use (layering only): `cache.getOrLoad(CacheKey(endpoint.id, canonicalParams), endpoint.cachePolicy, maxAge) { budget.acquire(...).flatMap { permit -> transport.execute(request) ... status check ... decode -> Lookup } }`. The budget runs inside the loader, after a miss; a hit never reaches it. Refusals before the cache (`Untrusted` policy) store nothing (`codebase-blast-radius.md:198`).

### Caffeine default (`jvmMain`)

```kotlin
public class CaffeineResponseCache(
    timeSource: TimeSource = TimeSource.Monotonic,            // the one injected time source; fixtures pass testTimeSource
    maxWeight: Long = 1_000,                                  // entries when every weight is 1
    loadDispatcher: CoroutineDispatcher = Dispatchers.Default,// fixtures pass StandardTestDispatcher(testScheduler)
    maintenanceExecutor: java.util.concurrent.Executor = ForkJoinPool.commonPool(), // fixtures pass Executor { it.run() }
) : ResponseCache
```

Wiring, all exercised in the probe except where noted:
1. `Caffeine.newBuilder().ticker(Ticker { origin.elapsedNow().inWholeNanoseconds }).executor(maintenanceExecutor).maximumWeight(maxWeight).weigher { _, e -> e.weight }.expireAfter(expiry).recordStats().buildAsync<CacheKey<*>, Entry>()`. No `Scheduler`, no listeners. `Entry` is internal: `outcome` (`Ok(lookup)` or `Failed(throwable)`), `storedAtNanos`, `ttlNanos`, `weight`. The `Expiry` returns `entry.ttlNanos` for create and update and `currentDuration` for read; `ttlNanos` is `ttl` for `Found`, `missTtl` for `Missing`, `0` for `Failed`.
2. `getOrLoad`: if closed, return `Closed`. Loop at most twice: `var created = false; val f = cache.get(key) { _, _ -> created = true; CompletableFuture<Entry>() }`; if `created`, `startLoad(f, ...)`. If `maxAge` is set and `f` is complete and `now - storedAt > maxAge`, `cache.asMap().remove(key, f)` and loop. Otherwise `f.asDeferred().await()`. If the outcome is `Failed`, `cache.asMap().remove(key, f)` and return `Result.failure`. If the await throws a non-cancellation `Throwable`, remove the entry and rethrow.
3. `startLoad`: `scope.launch { f.complete(load(...)) }`, then `job.invokeOnCompletion { cause -> if (cause == null || cause is CancellationException) f.complete(closedEntry) else f.completeExceptionally(cause) }`. `load` calls the loader, catches `Exception` into `Failed`, stamps `storedAt` from the injected source, and picks the TTL.
4. Scope: `CoroutineScope(SupervisorJob() + loadDispatcher + CoroutineName(...))`, no parent; created by the cache, cancelled by `close()`. The component that constructs a `CaffeineResponseCache` closes it; a facade that builds its default cache closes it from its own `close()`, and a cache the consumer passes in remains the consumer's to close.
5. Level 2: `CaffeineResponseCache(testTimeSource, StandardTestDispatcher(testScheduler), Executor { it.run() })` inside `runTest` gives deterministic hits, expiry, `maxAge`, miss TTL and coalescing with no network and no real time (F8).

### Reasoning in brief

Caffeine's `AsyncCache.get` already provides atomic single-flight, the `Expiry` hook that sees the finished value, and a weigher that understands in-flight futures. What it does badly for this use is failure handling (log noise, a joinable window) and cancellation (`CompletionStage.await()`), so those two are taken out of Caffeine's hands: failures are values with a zero lifetime removed by the waiters, and the load is started and completed by the cache with a normal value. `maxAge` as an age check with a compare-and-remove gives one load per stale entry under any concurrency, which no invalidate-then-get can. Decoded values keep the bound small and make "a decode failure is never stored" true by construction.

## Considerations

### 1. When every waiter cancels, should the shared load stop?

- **Option A, run to completion (recommended).** The load finishes, spends its permit (measured), and the result lands in the cache for the next caller. The wasted work is bounded by the budget's `maxWait` plus the call timeout, for one load per key.
- **Option B, cancel when the last waiter leaves** (Store5's ref-counted multicaster). Saves a budget reservation if the load is still queued for the budget, and a request already on the wire is already counted by the server (OpenDota counts refused calls). Costs a waiter count that must be updated atomically with joining, a retry path for a newcomer who joins just as the load is cancelled, and the result is not cached. Store5 shipped a fix for exactly that race in 2026.
- **Recommendation: A.** Revisit B only if the budget's queue turns out to hold many abandoned reservations; the loader could then be handed a "waiters remain" signal additively.

### 2. Size bound: entry count only, or weight-aware?

- **Option A, weight on `Found` (recommended).** `maxWeight` with weight 1 per entry by default behaves as an entry count; a loader that knows the raw size reports it. Costs one defaulted constructor parameter on `Found`.
- **Option B, entry count only (`maximumSize`).** Simpler; a single large modelled document (OpenDota's full match) has no way to count for more, and adding `weight` to `Found` later changes a public constructor.
- **Recommendation: A.** The default behaves as B, and the escape hatch exists from `1.0.0`.

### 3. Which statuses are confirmed misses by default?

- **Option A, none; each endpoint declares its set** (recommended). A `404` from a wrong base URL is then a failure and visible, not a cached "no such account".
- **Option B, `404` by default.** Matches the common case (an id in the path) and RFC 9110's heuristic cacheability of 404/410, but hides a misconfigured host for `missTtl`.
- **Recommendation: A.** This is a facade decision (endpoint descriptor); the cache only provides `Lookup.Missing` and `missTtl`.

## Known gaps

- **No throughput or latency measurement.** The probe counted loads, outcomes and sizes; it did not benchmark the `getOrLoad` path, the `maxAge` loop or contention at high concurrency. The default `maxWeight = 1_000`, `missTtl = 60 s` and the 5 MiB-based memory estimate are judgement.
- **Exceptional-completion variant measured only on the first prototype.** Its failure identity and log noise were measured; whether kotlinx-coroutines stack-trace recovery copies a typed failure thrown through `await()` with assertions on was not established, because the probe's wrapper exceptions carried no stack trace. The recommended design avoids the path for ordinary failures; a fatal `Error` still travels it.
- **Same-bin re-entry in the mapping function** was not provoked (F6); the argument rests on the Javadoc contract.
- **`close()` racing a `launch`.** A load started on a scope cancelled an instant earlier relies on `invokeOnCompletion` firing for a coroutine that never ran; not provoked, only the in-flight-at-close case was measured.
- **Zero TTL.** Caffeine's Javadoc does not state that a zero `Expiry` duration expires immediately; this is read from `BoundedLocalCache.hasExpired` and measured for failures. A zero `ttl` for a `Found` entry (coalescing only) rests on the same mechanism and was not separately measured.
- **Non-JVM.** Compilation for `js(IR)` only, of the port types; no behaviour was run on a non-JVM target, and no native or wasm target was tried.
- **Budget interplay.** A "permit" in the probe is a counter incremented in the loader. Refund-on-cancel and the budget's `maxWait` inside a coalesced load were not exercised; they depend on the budget research.
- **Custom `TimeSource`.** The age clamp for a non-monotonic source is stated from the documentation, not probed.
- **Apollo's expiration design** was read from a design document and the existence of its tests (`StaleTest.kt`), not run.
- **Ktor's absence of in-flight sharing** is from reading `HttpCache.kt`, not from a test; a search of that file found no in-flight handling.
- **Host.** One Windows machine, JDK 23 (compiled and checked at major version 55 with `-Xjdk-release=11`, not run on a JDK 11), Gradle 9.7.1.
- **Not decided here:** the name and mechanics of the single opt-in marker, the failure hierarchy's `Closed` type, the facade's endpoint descriptor and its lane/`maxWait` handling for coalesced callers, and whether a check-only `js` module or a published `js` target enforces common code.

## Load-bearing claims

Caffeine 3.3.1 (tag `v3.3.1`, `aabb24230296101f179e425b71bb64d62ded3189`; `<release>3.3.1</release>`, `lastUpdated` 20261013031251, Maven Central, read 2026-10-21), Java 11 bytecode.

1. **Single-flight and failure removal.** "The entire method invocation is performed atomically, so the function is applied at most once per key. If the asynchronous computation fails, the entry will be automatically removed from this cache." — `AsyncCache.get`, <https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/AsyncCache.java>.
2. **`Expiry` consults the value only after completion.** "long duration = delegate.expireAfterCreate(key, value, currentTime);" and "return ASYNC_EXPIRY;", with "static final long ASYNC_EXPIRY = (Long.MAX_VALUE >> 1) + (Long.MAX_VALUE >> 2); // 220 years" — `Async.AsyncExpiry`, <https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/Async.java>. On completion: "// update the weight and expiration timestamps" followed by "cache().replace(key, castedFuture, castedFuture," — `LocalAsyncCache.handleCompletion`, <https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/LocalAsyncCache.java>.
3. **Exceptional completion is logged at WARNING.** "logger.log(Level.WARNING, "Exception thrown during asynchronous load", error);" guarded by "if ((error != null) && !(error instanceof CancellationException)" and "&& !(error instanceof TimeoutException)) {"; "Logger logger = System.getLogger(LocalAsyncCache.class.getName());" — `LocalAsyncCache`, same URL as claim 2.
4. **Removal on failure is a callback.** "cache().remove(key, valueFuture);" inside "valueFuture.whenComplete((value, error) -> {" — `LocalAsyncCache.handleCompletion`, same URL.
5. **Expiry test.** "(expiresVariable() && (now - node.getVariableTime() >= 0));" — `BoundedLocalCache.hasExpired`, <https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/BoundedLocalCache.java>.
6. **Executor.** "A test may instead prefer to configure the cache to execute tasks directly on the same thread." and "By default, {@link ForkJoinPool#commonPool()} is used." — `Caffeine.executor`, <https://github.com/ben-manes/caffeine/blob/v3.3.1/caffeine/src/main/java/com/github/benmanes/caffeine/cache/Caffeine.java>.
7. **Scheduler off by default.** "By default, {@link Scheduler#disabledScheduler()} is used." — `Caffeine.scheduler`, same URL.
8. **Ticker.** "Specifies a nanosecond-precision time source for use in determining when entries should be expired or refreshed. By default, {@link System#nanoTime} is used." — `Caffeine.ticker`, same URL.
9. **Weight bound is approximate and eventual.** "Note that the cache <b>may evict an entry before this limit is exceeded or temporarily exceed the threshold while evicting</b>." and "As eviction is scheduled on the configured {@link #executor}, tests may instead prefer to configure the cache to execute tasks directly on the same thread." — `Caffeine.maximumWeight`, same URL.
10. **`refreshAfterWrite` needs a loading cache.** `requireState(refreshAfterWriteNanos == UNSET_INT, "refreshAfterWrite requires a LoadingCache");` in `requireNonLoadingCache()`, which `buildAsync()` calls — `Caffeine`, same URL. And "Otherwise, the old value is returned." / "all exceptions thrown during refresh will be logged and then swallowed" — `Caffeine.refreshAfterWrite`, same URL.
11. **Stats cost.** "Note that recording statistics requires bookkeeping to be performed with each operation, and thus imposes a performance penalty on cache operations." — `Caffeine.recordStats`, same URL.
12. **Mapping function contract.** "mappingFunction must not modify this cache during the computation." — `AsyncCache.get`, same URL as claim 1 (rendered in the source as "{@code mappingFunction} <b>must not</b> modify this cache during the computation").
13. **Java 11 and dependencies.** `"org.gradle.jvm.version": 11` — <https://repo1.maven.org/maven2/com/github/ben-manes/caffeine/caffeine/3.3.1/caffeine-3.3.1.module>; compile dependencies `jspecify` 1.0.1 and `error_prone_annotations` 2.50.0 — <https://repo1.maven.org/maven2/com/github/ben-manes/caffeine/caffeine/3.3.1/caffeine-3.3.1.pom>.

kotlinx-coroutines 1.10.2 (tag `1.10.2`) and Kotlin 2.2:

14. **`await` cancels the future.** "This method is intended to be used with one-shot futures, so on coroutine cancellation the [CompletableFuture] that corresponds to this [CompletionStage] (see [CompletionStage.toCompletableFuture]) is cancelled. If cancelling the given stage is undesired, `stage.asDeferred().await()` should be used instead." — `CompletionStage.await`, <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/jvm/src/future/Future.kt>.
15. **`asDeferred` cancels the future when the deferred is cancelled.** "The [CompletableFuture] that corresponds to this [CompletionStage] (see [CompletionStage.toCompletableFuture]) is cancelled when the resulting deferred is cancelled." — `CompletionStage.asDeferred`, same URL.
16. **`future` coroutine is cancelled when the future completes.** "The running coroutine is cancelled when the resulting future is cancelled or otherwise completed." — `CoroutineScope.future`, same URL.
17. **Test time source.** "The [test scheduler][TestScope.testScheduler] as a [TimeSource]." with `@ExperimentalCoroutinesApi` and "public val TestScope.testTimeSource: TimeSource.WithComparableMarks get() = testScheduler.timeSource" — <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestScope.kt>.
18. **`TimeSource` is stable and may be non-monotonic.** "In general, TimeSource may not be monotonic, which may result in a negative duration returned from TimeMark.elapsedNow for a TimeMark obtained from a particular TimeSource. The monotonicity is guaranteed for TimeSource.Monotonic." and "Since Kotlin 1.9" — <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-time-source/>, read 2026-10-21, Kotlin 2.2. `TestTimeSource`: "A time source that has programmatically updatable readings. It is useful as a predictable source of time in tests." and "Since Kotlin 1.9" — <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-test-time-source/>.
19. **Stable sort.** "The sort is stable. It means that elements for which selector returned equal values preserve their order relative to each other after sorting." — `sortedBy`, <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.collections/sorted-by.html>, Since Kotlin 1.0.

Comparable libraries:

20. **Aedile 3.0.4.** `val scope = CoroutineScope(coroutineContext.minusKey(Job) + SupervisorJob())` and `.thenApply { it }` and `fun underlying(): AsyncCache<K, V> = cache` — `Cache.kt`, <https://github.com/sksamuel/aedile/blob/v3.0.4/aedile-core/src/main/kotlin/com/sksamuel/aedile/core/Cache.kt>. "Fixed coroutine cancellation propagation #30" — <https://github.com/sksamuel/aedile/blob/v3.0.4/changelog.md>. PR #67 body: "`CompletableFuture.await()` cancels the future on coroutine cancellation" / "a cancelled caller still calls `.cancel(false)` on the **shared** Caffeine future directly via `await()`, taking down all other waiters." — <https://github.com/sksamuel/aedile/pull/67>, merged 2026-04-16; issue #57 opened 2026-04-07. POM: Caffeine `3.2.4`, `kotlinx-coroutines-core-jvm` `1.10.2` — <https://repo1.maven.org/maven2/com/sksamuel/aedile/aedile-core/3.0.4/aedile-core-3.0.4.pom>.
21. **cache4k.** "Concurrent calls from multiple threads using the same key will be blocked. Assuming the 1st call successfully computes a new value, none of the loader from the other calls will be executed and the cached value computed by the first loader will be returned for those calls." and "Note that loader is executed on the caller's coroutine context." — <https://github.com/ReactiveCircus/cache4k/blob/6865253f8c7794a03bab192a3b8a0cb868c2ecd7/README.md> (`main` @ `6865253`, 2026-10-14; latest release 0.14.0, 2025-01-14). "Specifies a [TimeSource] to be used for expiry checks. If not specified, [TimeSource.Monotonic] will be used." — `Cache.kt` at the same commit. Open issues: #47 "Fix linearizability issues surfaced by Lincheck", #28 "0.9.0 introduces massive performance issues" — <https://github.com/ReactiveCircus/cache4k/issues>.
22. **Store5.** "This class maintains one and only 1 fetcher for a given [Key]." and `onRelease = { _: Key, multicaster: Multicaster<StoreReadResponse<Network>> ->` / `multicaster.close()` — `FetcherController.kt`; "[Store] will open a global scope for management of shared responses, if instead you'd like to control the scope that sharing/multicasting happens in you can pass a @param [scope]" — `StoreBuilder.kt`; "Fix multicaster race that leaves a new downstream without a producer" — `CHANGELOG.md` (5.1.0-alpha10, 2026-07-13). All at <https://github.com/MobileNativeFoundation/Store/tree/74a5988ff1c998055455b42509763e933a66da79> (`main`, 5.1.0-beta01, 2026-09-08).
23. **Ktor 3.6.0.** `@Deprecated("Use new [CacheStorage] instead.", level = DeprecationLevel.ERROR)` on `HttpCacheStorage`; `public suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData?` on `CacheStorage`; "[CacheStorage.Unlimited] by default." — <https://github.com/ktorio/ktor/blob/d96280165fc9890217196ed0dffd45228e35987d/ktor-client/ktor-client-core/common/src/io/ktor/client/plugins/cache/storage/HttpCacheStorage.kt> and `.../cache/HttpCache.kt` (commit `d962801`, `VERSION` 3.6.0). `public data class AttributeKey<T : Any>` with `name` and `type` — `ktor-utils/common/src/io/ktor/util/Attributes.kt`, same commit.
24. **Apollo.** `values.forEach {` / `append(it)` in `CacheKey(typename, values)` and "The key must be globally unique." — <https://github.com/apollographql/apollo-kotlin/blob/v4.4.1/libraries/apollo-normalized-cache-api/src/commonMain/kotlin/com/apollographql/apollo/cache/normalized/api/CacheKey.kt>, tag `v4.4.1`. "When a field is resolved, if its age is greater than the configured max age, it will result in a cache miss." — <https://github.com/apollographql/apollo-kotlin-normalized-cache/blob/v1.0.9/design-docs/Expiration.md>, tag `v1.0.9`.
25. **OkHttp.** "Cache is not an interface with alternative implementations." — <https://github.com/square/okhttp/blob/parent-5.5.0/README.md>, tag `parent-5.5.0`.
26. **Guava 33.4.8.** "If another call to {@link #get} or {@link #getUnchecked} is currently loading the value for {@code key}, simply waits for that thread to finish and returns its loaded value." — `LoadingCache.get`, <https://github.com/google/guava/blob/v33.4.8/guava/src/com/google/common/cache/LoadingCache.java>. "@return the value associated with {@code key}; <b>must not be null</b>" — `CacheLoader.load`, <https://github.com/google/guava/blob/v33.4.8/guava/src/com/google/common/cache/CacheLoader.java>.

Specifications:

27. **Cache key.** "The "cache key" is the information a cache uses to choose a response and is composed from, at a minimum, the request method and target URI used to retrieve the stored response" — RFC 9111 §2, <https://www.rfc-editor.org/rfc/rfc9111.txt>. **Collapsing.** "This enables a cache to "collapse requests" -- or combine multiple incoming requests into a single forward request upon a cache miss" — same, §2. **Authenticated requests.** "A shared cache MUST NOT use a cached response to a request with an Authorization header field (Section 11.6.2 of [HTTP]) to satisfy any subsequent request unless the response contains a Cache-Control field with a response directive (Section 5.2.2) that allows it to be stored by a shared cache" — §3.5. **`max-age` request directive.** "The max-age request directive indicates that the client prefers a response whose age is less than or equal to the specified number of seconds." — §5.2.1.1.
28. **Negative answers are cacheable.** "Responses with status codes that are defined as heuristically cacheable (e.g., 200, 203, 204, 206, 300, 301, 308, 404, 405, 410, 414, and 501 in this specification) can be reused by a cache with heuristic expiration" — RFC 9110 §15, <https://www.rfc-editor.org/rfc/rfc9110.txt>.
29. **Stale-while-revalidate.** "The stale-while-revalidate HTTP Cache-Control extension allows a cache to immediately return a stale response while it revalidates it in the background, thereby hiding latency (both in the network and on the server) from clients." — RFC 5861 §1, <https://www.rfc-editor.org/rfc/rfc5861.txt>.
30. **`AutoCloseable` is a common declaration.** "A resource that can be closed or released." with "Since Kotlin 2.0" and an `actual typealias AutoCloseable = java.lang.AutoCloseable` on the JVM — <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-auto-closeable/>, read 2026-10-21, Kotlin 2.2.
