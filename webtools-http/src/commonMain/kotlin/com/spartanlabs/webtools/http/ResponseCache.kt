package com.spartanlabs.webtools.http

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration

// `commonMain` cannot link a `jvmMain` type, so the KDoc below names `CaffeineResponseCache` in code font, not as a link.
/**
 * Stores the decoded answer of a load for a bounded time and serves it to any number of concurrent callers from one load; failures are never stored.
 *
 * The contract an implementation (the supplied JVM `CaffeineResponseCache`, [ResponseCache.Disabled], a consumer's own cache or a fake) meets:
 * 1. One load per key at a time. Concurrent calls for an equal [CacheKey] while a load is in flight join it and receive its outcome. A joining or hitting call's
 *    `policy` and `loader` are not used, so a shared load carries the first caller's loader.
 * 2. A [Lookup.Found] is stored for [CachePolicy.ttl] and a [Lookup.Missing] for [CachePolicy.missTtl], measured from the load's completion. A zero TTL
 *    coalesces loads in flight and stores nothing. A failure is never stored.
 * 3. `maxAge`: a stored entry whose age exceeds it is not served (an age at most `maxAge` is served, as RFC 9111's `max-age` request directive reads). The call loads,
 *    or joins a load already in flight, which counts as fresh. At most one reload per call.
 * 4. A loader's `Exception` becomes the call's failure, the same instance for every waiter. An `Error` is rethrown to every waiter. A cancellation the loader throws, or
 *    returns inside `Result.failure`, while the coroutine running the loader is still active (the caller's for [Disabled]; the cache's own, until [close], for a cache
 *    that loads in its own scope) becomes `Result.failure(IllegalStateException(…, cause))`; a `CancellationException` is never put inside a `Result`.
 * 5. A caller's own cancellation is thrown as `CancellationException` and cancels only that caller. A caller that is already cancelled gets it even on a hit.
 * 6. After [close], [getOrLoad] returns `Result.failure(HttpFailure.Closed("cache"))` without running the loader, and a call waiting on a load when [close] runs returns
 *    the same. [close] is idempotent, non-suspending and never throws. [Disabled], which stores nothing and is shared, ignores [close].
 * 7. After [invalidate], the next call for the key loads anew; a load in flight still completes for the calls already waiting and its result is not stored.
 *    [invalidateAll] is the same for every key. Both never throw and are no-ops after [close].
 * 8. [stats] is an immutable reading of four counts; the four are read separately, not as one atomic snapshot.
 * 9. Every member may be called concurrently from any thread.
 * 10. The key's type parameter is not part of its equality: one endpoint id serves one value type; a second type under the same id is a programming error.
 * 11. A negative `maxAge` is a programmer error: `IllegalArgumentException("maxAge must not be negative")`.
 *
 * A loader must not wait for a load of its own key. A supplied loader runs in the cache's own scope, not in the caller's; [Disabled] runs it on the caller's coroutine.
 *
 * **Experimental (implement).** Implementing or delegating to this interface needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; calling it and passing it are
 * Stable Core, and the supplied [Disabled] and the JVM's `CaffeineResponseCache` need no opt-in.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface ResponseCache : AutoCloseable {
    /**
     * Returns the stored answer for [key] if one is live and no older than [maxAge]; otherwise joins the load in flight for [key], or starts one with [loader].
     *
     * @param key the answer's identity.
     * @param policy how long the load's outcome is stored (used only by the call that starts the load).
     * @param maxAge the oldest stored answer this call accepts; [Duration.INFINITE] accepts any live entry; not negative.
     * @param loader produces the outcome: `Found`/`Missing` are stored, a failure is not.
     * @return `Result.success` of the loader's [Lookup]; `Result.failure` of the loader's failure; or `Result.failure` of [HttpFailure.Closed] (component `"cache"`) after [close].
     * @throws IllegalArgumentException if [maxAge] is negative.
     * @throws kotlinx.coroutines.CancellationException if the caller is cancelled.
     * @throws Error if the loader threw one (the same instance for every waiter).
     */
    public suspend fun <V : Any> getOrLoad(
        key: CacheKey<V>,
        policy: CachePolicy,
        maxAge: Duration,
        loader: suspend () -> Result<Lookup<V>>,
    ): Result<Lookup<V>>

    /**
     * [getOrLoad] accepting any live entry (`maxAge` is [Duration.INFINITE]).
     *
     * @param key the answer's identity.
     * @param policy how long the load's outcome is stored.
     * @param loader produces the outcome.
     * @return as the four-argument overload.
     * @throws kotlinx.coroutines.CancellationException if the caller is cancelled.
     * @throws Error if the loader threw one.
     */
    public suspend fun <V : Any> getOrLoad(
        key: CacheKey<V>,
        policy: CachePolicy,
        loader: suspend () -> Result<Lookup<V>>,
    ): Result<Lookup<V>> = getOrLoad(key, policy, Duration.INFINITE, loader)

    /**
     * Drops [key]'s stored or in-flight entry (contract point 7): the next call for [key] loads anew, and a load in flight still completes for the calls
     * already waiting without being stored. Never throws; a no-op after [close].
     *
     * @param key the key to drop.
     */
    public fun invalidate(key: CacheKey<*>)

    /**
     * Drops every stored or in-flight entry (contract point 7), as [invalidate] does for one key. Never throws; a no-op after [close].
     */
    public fun invalidateAll()

    /**
     * Reads the four counts (contract point 8).
     *
     * @return an immutable reading of the four counts, read separately, not atomically.
     */
    public fun stats(): CacheStats

    /** Releases the cache's resources. Idempotent, non-suspending, does not throw. The default does nothing. */
    override fun close() {}

    /**
     * The cache that stores nothing and shares nothing: every call runs its own loader on the caller's coroutine.
     *
     * **Stable Core.** It forfeits "N concurrent callers cost one load". Final, stateless and shared; usable without the opt-in. [stats] is always [CacheStats.Empty],
     * [invalidate] and [invalidateAll] do nothing, and [close] does nothing, so a client may close it safely. `toString()` is `ResponseCache.Disabled`.
     */
    public object Disabled : ResponseCache {
        override suspend fun <V : Any> getOrLoad(
            key: CacheKey<V>,
            policy: CachePolicy,
            maxAge: Duration,
            loader: suspend () -> Result<Lookup<V>>,
        ): Result<Lookup<V>> {
            require(!maxAge.isNegative()) { NEGATIVE_MAX_AGE_MESSAGE }
            currentCoroutineContext().ensureActive()
            return runLoader(loader)
        }

        override fun invalidate(key: CacheKey<*>) {}
        override fun invalidateAll() {}
        override fun stats(): CacheStats = CacheStats.Empty
        /** Returns the fixed text `ResponseCache.Disabled`, for diagnostics and logs. */
        override fun toString(): String = "ResponseCache.Disabled"
    }
}
