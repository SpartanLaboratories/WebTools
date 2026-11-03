package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.ResponseCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * One `getOrLoad` call launched as its own coroutine, with what it produced recorded, so a Level 2 test drives it with the virtual clock and asserts
 * on [isCompleted], [isCancelled], [result] and [thrown] without ever joining it (a hang then fails an assertion at once instead of timing out).
 * Built by [call]. A `Throwable` the call threw (a caller's `CancellationException`, a loader's `Error`) is caught inside the coroutine and kept in
 * [thrown] as the instance, so a test may compare it by identity.
 */
internal class Waiter<V : Any> {
    /** The call's coroutine. */
    lateinit var job: Job

    /** The call's returned `Result`, or `null` while it runs or if it threw. */
    var result: Result<Lookup<V>>? = null

    /** What the call threw, or `null`. */
    var thrown: Throwable? = null

    /** Whether the coroutine has ended, by a return, a throw or a cancellation. */
    val isCompleted: Boolean get() = job.isCompleted

    /** Whether the coroutine ended cancelled. */
    val isCancelled: Boolean get() = job.isCancelled

    /** The returned `Lookup`, or `null`. */
    val lookup: Lookup<V>? get() = result?.getOrNull()

    /** The returned `Found`'s value, or `null`. */
    val value: V? get() = (lookup as? Lookup.Found<V>)?.value

    /** The returned failure, or `null`. */
    val failure: Throwable? get() = result?.exceptionOrNull()

    /** Cancels the call's coroutine. */
    fun cancel(): Unit = job.cancel()
}

/**
 * Launches `cache.getOrLoad(key, policy, maxAge, loader)` in this scope and records its outcome.
 *
 * @param cache the cache under test.
 * @param key the key.
 * @param policy the policy.
 * @param maxAge the call's `maxAge`; `INFINITE` by default.
 * @param cancelledFirst whether the coroutine cancels itself before calling, so the call sees an already-cancelled caller.
 * @param loader the loader.
 * @return the waiter; drive the clock, then read it.
 */
internal fun <V : Any> CoroutineScope.call(
    cache: ResponseCache,
    key: CacheKey<V>,
    policy: CachePolicy,
    maxAge: Duration = Duration.INFINITE,
    cancelledFirst: Boolean = false,
    loader: suspend () -> Result<Lookup<V>>,
): Waiter<V> {
    val waiter = Waiter<V>()
    waiter.job = launch {
        if (cancelledFirst) cancel()
        try {
            waiter.result = cache.getOrLoad(key, policy, maxAge, loader)
        } catch (t: Throwable) {
            waiter.thrown = t
        }
    }
    return waiter
}
