package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.testing.VirtualClock
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/**
 * A cache driven by [clock]: its `timeSource`, `loadDispatcher` and `maintenanceExecutor` are the clock's, then [configure] runs.
 *
 * @param clock the virtual clock of the test.
 * @param configure the other builder properties to set (for example `maxWeight`).
 * @return the cache; close it with `use`.
 */
internal fun cacheOn(clock: VirtualClock, configure: CaffeineResponseCache.Builder.() -> Unit = {}): CaffeineResponseCache =
    CaffeineResponseCache {
        timeSource = clock.timeSource
        loadDispatcher = clock.dispatcher
        maintenanceExecutor = clock.maintenanceExecutor
        configure()
    }

/**
 * A key of `String` values.
 *
 * @param id the endpoint id; `endpoint` by default.
 * @param params the parameters, in any order.
 * @return the key.
 */
internal fun key(id: String = "endpoint", vararg params: Pair<String, String>): CacheKey<String> = CacheKey(id, params.toList())

/**
 * Counts the loaders a test hands a cache and records their events, so a test asserts how many loads ran and in which order. Every loader it
 * builds counts its start, waits [Duration] `after` on the running dispatcher (virtual under a test scheduler), records its end and produces its outcome.
 */
internal class LoadLog {
    private val starts = AtomicInteger()

    /** Every loader event so far, in order: `start <label>` then `end <label>`. Safe to read while loads run. */
    val events = CopyOnWriteArrayList<String>()

    /** How many loaders started. */
    val loads: Int get() = starts.get()

    /** A loader returning `Found(value)` (weight 1) after [after]. */
    fun loader(value: String, after: Duration = Duration.ZERO): suspend () -> Result<Lookup<String>> =
        producing(value, after) { Result.success(Lookup.Found(value)) }

    /** A loader returning `Found(value, weight)` after [after]. */
    fun heavy(value: String, weight: Int, after: Duration = Duration.ZERO): suspend () -> Result<Lookup<String>> =
        producing(value, after) { Result.success(Lookup.Found(value, weight)) }

    /** A loader returning `Missing` after [after]. */
    fun missing(after: Duration = Duration.ZERO): suspend () -> Result<Lookup<String>> =
        producing("missing", after) { Result.success(Lookup.Missing) }

    /** A loader returning `Result.failure(failure)` after [after]. */
    fun failing(failure: Throwable, after: Duration = Duration.ZERO): suspend () -> Result<Lookup<String>> =
        producing("failing", after) { Result.failure(failure) }

    /** A loader throwing [throwable] after [after]. */
    fun throwing(throwable: Throwable, after: Duration = Duration.ZERO): suspend () -> Result<Lookup<String>> =
        producing("throwing", after) { throw throwable }

    private fun producing(label: String, after: Duration, produce: () -> Result<Lookup<String>>): suspend () -> Result<Lookup<String>> = {
        starts.incrementAndGet()
        events += "start $label"
        if (after.isPositive()) delay(after)
        events += "end $label"
        produce()
    }
}
