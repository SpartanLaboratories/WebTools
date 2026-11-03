package com.spartanlabs.webtools.http

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener
import com.github.benmanes.caffeine.cache.Ticker
import com.github.benmanes.caffeine.cache.Weigher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.TimeSource

/** The name of the cache's logger. */
internal const val CACHE_LOGGER_NAME: String = "com.spartanlabs.webtools.http.CaffeineResponseCache"

private val cacheLog: Log = logger(CACHE_LOGGER_NAME)

private const val LOAD_SCOPE_NAME = "webtools-http-cache-load"
private const val CACHE_COMPONENT = "cache"
private const val MAX_WEIGHT_MESSAGE = "maxWeight must be at least 1"

/**
 * The supplied [ResponseCache]: Caffeine 3 under the port's contract, built with the factory `CaffeineResponseCache { }` and usable without any
 * opt-in (it is a supplied default, not a seam); `final`, with every policy number a property of its [Builder].
 *
 * **Stable Core.**
 *
 * - **One load per key at a time.** The first call for a key starts the load; every call for an equal key while it is in flight joins it and
 *   receives its outcome, whatever `policy` and `loader` the joining call passed (the first caller's govern). A call that finds a live stored
 *   entry is a hit and runs nothing.
 * - **Lifetimes.** A [Lookup.Found] is stored for the policy's `ttl` and a [Lookup.Missing] for its `missTtl`, each measured from the load's
 *   completion, not its start. A zero TTL coalesces the loads in flight and stores nothing; [Duration.INFINITE] stores until evicted.
 * - **The weight bound.** `maxWeight` bounds the total weight of the stored entries: a `Found` weighs what it reports (1 by the one-argument
 *   constructor), a `Missing` weighs 1, and the least recently used entries are evicted when the bound is reached (counted in
 *   [CacheStats.evictions]). A `Found` heavier than `maxWeight` is delivered to its waiters and never stored, so the bound holds at every instant.
 * - **`maxAge`.** A stored entry older than the call's `maxAge` is not served: the call reloads, or joins the load already in flight, which counts
 *   as fresh. [Duration.ZERO] serves only an entry stored at the same tick.
 * - **Failures are never stored.** A loader's `Result.failure` or thrown `Exception` is the call's failure, the same instance for every waiter; a
 *   loader's `Error` is rethrown to every waiter as the same instance; a loader's own cancellation (a `withTimeout` expiring inside it, a thrown or
 *   returned `CancellationException`) is `Result.failure(IllegalStateException(…, cause))`, never a `CancellationException` and never `Closed`. The
 *   next call for the key loads anew.
 * - **Three execution contexts.** A caller's coroutine only awaits; a loader runs in the cache's own scope on `loadDispatcher`, so the caller's
 *   dispatcher, `CoroutineName` and other context elements do not reach it, and a caller's cancellation cancels only that caller; Caffeine's
 *   maintenance runs on `maintenanceExecutor`, which the cache never shuts down and which must outlive the cache's last write. The cache owns no
 *   thread, timer or pool. A loader must not wait for a load of its own key.
 * - **`close()`** is idempotent, non-suspending and never throws: it cancels the loads in flight, ends every waiting call with
 *   `Result.failure(HttpFailure.Closed("cache"))` without waiting for a loader to unwind, and drops every stored entry. Every later [getOrLoad]
 *   returns the same failure without running its loader; [invalidate], [invalidateAll] and [stats] keep working.
 * - **Thread safety.** Every member may be called concurrently from any thread and any number of coroutines.
 * - **Logging**, through the logger `com.spartanlabs.webtools.http.CaffeineResponseCache`: `INFO` once on creation (`maxWeight`) and once on
 *   `close()`; `DEBUG` once per load end with the key's id and parameter names, the outcome kind and whether it was stored; `WARN` once for a load
 *   coroutine that failed outside the loader (a `loadDispatcher` that rejects the dispatch), with the exception's class name. Never a value, a
 *   parameter value, a header, a credential or a message text; nothing per hit or join.
 * - **The builder's properties:** `timeSource` (the clock of every age and lifetime), `maxWeight`, `loadDispatcher` and `maintenanceExecutor`,
 *   each with a production default, so one virtual clock drives the cache under test.
 */
public class CaffeineResponseCache internal constructor(
    timeSource: TimeSource,
    private val maxWeight: Long,
    loadDispatcher: CoroutineDispatcher,
    maintenanceExecutor: Executor,
) : ResponseCache {
    init {
        require(maxWeight >= 1) { MAX_WEIGHT_MESSAGE }
    }

    private val origin = timeSource.markNow()
    private val ticker = Ticker { origin.elapsedNow().inWholeNanoseconds }
    private val closed = AtomicBoolean(false)
    private val hits = LongAdder()
    private val misses = LongAdder()
    private val evictions = LongAdder()
    private val failedLoads = LongAdder()

    // No parent. The load body is total, so the handler sees only a coroutine that failed outside it (a rejected dispatch, or a defect): it reports that once,
    // through the cache's logger, instead of the global handlers (the thread's handler, or a test library's collector that would fail a later test).
    private val loadFailureHandler = CoroutineExceptionHandler { _, failure ->
        cacheLog.warn("a load coroutine failed outside its body: {}", failure.javaClass.name)
    }
    private val scope = CoroutineScope(SupervisorJob() + loadDispatcher + CoroutineName(LOAD_SCOPE_NAME) + loadFailureHandler)

    // No recordStats() (its counts see every get and count expiry as an eviction) and no Scheduler (no thread, timer or shutdown hook): the cache
    // keeps its own four counters, and Caffeine's maintenance runs on maintenanceExecutor, which the cache never shuts down.
    private val cache: AsyncCache<CacheKey<*>, CacheEntry> = Caffeine.newBuilder()
        .ticker(ticker)
        .executor(maintenanceExecutor)
        .maximumWeight(maxWeight)
        .weigher(Weigher<CacheKey<*>, CacheEntry> { _, entry -> entry.weight })
        .expireAfter(CacheExpiry)
        // Size removals only; runs inside Caffeine's atomic removal and must not touch the cache.
        .evictionListener(RemovalListener<CacheKey<*>, CacheEntry> { _, _, cause -> if (cause == RemovalCause.SIZE) evictions.increment() })
        .buildAsync()

    init {
        cacheLog.info("created: maxWeight={}", maxWeight)
    }

    //region the call
    override suspend fun <V : Any> getOrLoad(
        key: CacheKey<V>,
        policy: CachePolicy,
        maxAge: Duration,
        loader: suspend () -> Result<Lookup<V>>,
    ): Result<Lookup<V>> {
        require(!maxAge.isNegative()) { NEGATIVE_MAX_AGE_MESSAGE }
        currentCoroutineContext().ensureActive()
        if (closed.get()) return closedFailure()
        val claim = claim(key, maxAge)
        if (claim.startsLoad) {
            misses.increment()
            startLoad(claim.future, key, policy, loader)
        } else {
            hits.increment()
        }
        val entry = awaitEntry(claim.future)
        // A completed zero-lifetime entry (a failure, a zero-TTL value, an oversized value) is treated as absent, whatever order Caffeine's completion
        // callbacks run in: removed by identity, and the return value is not trusted because another call may already have replaced the entry.
        if (entry.lifetimeNanos == 0L) cache.asMap().remove(key, claim.future)
        return deliver(entry)
    }

    private class Claim(val future: CompletableFuture<CacheEntry>, val startsLoad: Boolean)

    // At most two rounds. A completed entry this call may not be served is removed by identity and looked up again, once; an in-flight future is always joined.
    private fun claim(key: CacheKey<*>, maxAge: Duration): Claim {
        val first = claimOnce(key)
        if (first.startsLoad || !isRefused(first.future, maxAge)) return first
        cache.asMap().remove(key, first.future)
        return claimOnce(key)
    }

    // The mapping function runs under the key's bin lock: it only allocates and sets a flag. It never starts a coroutine, runs the loader or touches the cache.
    private fun claimOnce(key: CacheKey<*>): Claim {
        var created = false
        val future: CompletableFuture<CacheEntry> = cache.get(key) { _, _ ->
            created = true
            CompletableFuture<CacheEntry>()
        }
        return Claim(future, created)
    }

    private fun isRefused(future: CompletableFuture<CacheEntry>, maxAge: Duration): Boolean {
        val entry = if (future.isDone && !future.isCompletedExceptionally) future.getNow(null) else null
        return entry != null && !isUsable(entry, ticker.read(), maxAge)
    }

    // A waiter's own cancellation cancels only the waiter (asDeferred().await() does not cancel the shared future). Any other CancellationException is not the
    // caller's: after ensureActive() it reads as the closed outcome. This try/catch is the one place the cache tells the two apart.
    private suspend fun awaitEntry(future: CompletableFuture<CacheEntry>): CacheEntry =
        try {
            future.asDeferred().await()
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            CacheEntry.closed()
        }

    // The one unchecked cast: the key's phantom type says the stored Lookup is a Lookup<V> (contract point 10).
    @Suppress("UNCHECKED_CAST")
    private fun <V : Any> deliver(entry: CacheEntry): Result<Lookup<V>> = when (val outcome = entry.outcome) {
        is CacheOutcome.Loaded -> Result.success(outcome.lookup as Lookup<V>)
        is CacheOutcome.Failed -> Result.failure(outcome.failure)
        is CacheOutcome.Fatal -> throw outcome.error
        CacheOutcome.Closed -> closedFailure()
    }

    private fun <V : Any> closedFailure(): Result<Lookup<V>> = Result.failure(HttpFailure.Closed(CACHE_COMPONENT))
    //endregion

    //region the load
    private fun <V : Any> startLoad(
        future: CompletableFuture<CacheEntry>,
        key: CacheKey<V>,
        policy: CachePolicy,
        loader: suspend () -> Result<Lookup<V>>,
    ) {
        // Insert-then-check: close() sets the flag and then sweeps the map; this call inserted and now reads the flag, so one of the two sees the other.
        if (closed.get()) return settle(key, future, CacheEntry.closed())
        val job: Job = try {
            scope.launch {
                val ended = load(key, policy, loader)
                // Whoever completes the shared future logs the load's end: a body whose result close() already settled logs nothing.
                if (future.complete(ended.entry)) cacheLog.debug("load ended: key={}, outcome={}, storage={}", key, outcomeKind(ended.entry.outcome), storageNote(ended, key, future))
            }
        } catch (refused: Throwable) {
            // launch itself threw (a dispatcher that rejects): the body will never run, so complete the future here, by value.
            return settle(key, future, entryOfThrowable(refused))
        }
        // The last completer. complete() is first-wins, so this is a no-op after the body or close() has completed the future.
        job.invokeOnCompletion { cause ->
            settle(key, future, if (cause == null || cause is CancellationException) CacheEntry.closed() else entryOfThrowable(cause))
        }
    }

    // An Exception is a failed outcome (a Result.failure for every waiter); anything else is fatal and rethrown. Used only by the safety nets: the body never reaches it.
    private fun entryOfThrowable(t: Throwable): CacheEntry =
        if (t is Exception) CacheEntry.failed(t, ticker.read()) else CacheEntry.fatal(t, ticker.read())

    // What the body produced: the entry, and why it is or is not stored (the DEBUG line's storage note, which the entry alone does not determine).
    private class Ended(val entry: CacheEntry, val storage: Storage)

    // Read after the body's complete() won. Caffeine's completion callback has run on the completing thread by then, and it re-inserts a result only by
    // identity (replace(key, future, future)): a storable entry whose future invalidate() removed mid-flight is discarded, so its note says so.
    private fun storageNote(ended: Ended, key: CacheKey<*>, future: CompletableFuture<CacheEntry>): String =
        if (ended.storage == Storage.Stored && cache.asMap()[key] !== future) Storage.Removed.note else ended.storage.note

    // The body never throws except to end cancelled (close() cancelled the scope). Everything else is a value: the shared future is completed normally in
    // every case, so Caffeine never sees an exceptional completion (and logs no WARNING), launch's uncaught-exception path is never used by a load, and
    // each waiter rethrows the original Error from a field instead of a copy stack-trace recovery re-created. The body counts a failed load; the
    // completion that wins logs the load's end.
    private suspend fun <V : Any> load(key: CacheKey<V>, policy: CachePolicy, loader: suspend () -> Result<Lookup<V>>): Ended {
        val outcome: CacheOutcome = try {
            runLoader(loader).fold(onSuccess = { CacheOutcome.Loaded(it) }, onFailure = { CacheOutcome.Failed(it) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (fatal: Throwable) {
            CacheOutcome.Fatal(fatal)
        }
        val residency = residencyOf(outcome, policy, maxWeight)
        val entry = CacheEntry(outcome, ticker.read(), residency.lifetimeNanos, residency.weight)
        if (entry.isFailure) failedLoads.increment()
        return Ended(entry, residency.storage)
    }

    // A safety net completes the future; when it wins it counts a failed or fatal entry and logs the load's end.
    private fun settle(key: CacheKey<*>, future: CompletableFuture<CacheEntry>, entry: CacheEntry) {
        if (!future.complete(entry)) return
        if (entry.isFailure) failedLoads.increment()
        cacheLog.debug("load ended: key={}, outcome={}, storage={}", key, outcomeKind(entry.outcome), Storage.NoResult.note)
    }
    //endregion

    override fun invalidate(key: CacheKey<*>) {
        cache.asMap().remove(key)          // the async view: never synchronous().asMap(), which joins a pending future
    }

    override fun invalidateAll() {
        cache.asMap().clear()
    }

    override fun stats(): CacheStats = CacheStats(hits.sum(), misses.sum(), evictions.sum(), failedLoads.sum())

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        listOf<Pair<String, () -> Unit>>(
            "cancelScope" to { scope.cancel() },
            "completeWaiters" to { cache.asMap().forEach { (key, future) -> settle(key, future, CacheEntry.closed()) } },
            "clear" to { cache.asMap().clear() },
        ).forEach { (step, action) ->
            catchNonFatal(action).onFailure { cacheLog.debug("close step {} failed: {}", step, it.javaClass.name) }
        }
        cacheLog.info("closed")
    }

    override fun toString(): String = "CaffeineResponseCache(maxWeight=$maxWeight, closed=${closed.get()})"

    //region internal test seams
    /** Runs Caffeine's pending maintenance now (Level 2 and 4c: makes the size bound and the eviction count exact). */
    internal fun cleanUp(): Unit = cache.synchronous().cleanUp()

    /** The policy's weighted size of the stored entries, for the bound tests. */
    internal fun weightedSize(): Long = cache.synchronous().policy().eviction().map { it.weightedSize().orElse(0L) }.orElse(0L)

    /** The future currently held for [key], or `null` (Level 2: whether an entry is stored, in flight or never completed exceptionally). */
    internal fun futureOf(key: CacheKey<*>): CompletableFuture<CacheEntry>? = cache.asMap()[key]
    //endregion

    /** The builder's default. */
    public companion object {
        /** The default of [Builder.maxWeight]: 1,000, which is 1,000 entries when every weight is 1. */
        public const val DEFAULT_MAX_WEIGHT: Long = 1_000L
    }

    /**
     * Collects the properties of a [CaffeineResponseCache]. Not thread-safe; use it only inside the factory's lambda. **Stable Core.**
     */
    @WebToolsDsl
    public class Builder internal constructor() {
        /** The clock of every age and lifetime; one mark is taken at construction. [TimeSource.Monotonic] by default. A source that is not monotonic can lengthen lifetimes; ages are clamped at zero. */
        public var timeSource: TimeSource = TimeSource.Monotonic

        /** The bound on the total weight of stored entries: each `Found` weighs what it reports (1 by default), each `Missing` weighs 1. At least 1. [DEFAULT_MAX_WEIGHT] by default. */
        public var maxWeight: Long = DEFAULT_MAX_WEIGHT

        /** Where loads run. [Dispatchers.Default] by default. It should dispatch: an unconfined dispatcher runs a load's first segment on the calling thread. */
        public var loadDispatcher: CoroutineDispatcher = Dispatchers.Default

        /** Where Caffeine's maintenance runs. [ForkJoinPool.commonPool] by default. The cache never shuts it down, and it must outlive the cache's last write. */
        public var maintenanceExecutor: Executor = ForkJoinPool.commonPool()
    }
}

/**
 * Builds a [CaffeineResponseCache]. **Stable Core.** `CaffeineResponseCache { maxWeight = 5_000 }`; without the lambda every property has its default.
 *
 * @param configure assigns the properties to change.
 * @return the cache; the caller closes it.
 * @throws IllegalArgumentException if `maxWeight` is below 1; the message names the property and never a value.
 */
public fun CaffeineResponseCache(configure: CaffeineResponseCache.Builder.() -> Unit = {}): CaffeineResponseCache {
    val builder = CaffeineResponseCache.Builder().apply(configure)
    return CaffeineResponseCache(builder.timeSource, builder.maxWeight, builder.loadDispatcher, builder.maintenanceExecutor)
}
