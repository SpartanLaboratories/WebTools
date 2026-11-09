package com.spartanlabs.webtools.http

/**
 * A reading of a cache's four counts. **Stable Core.**
 *
 * The four are read separately, not as one atomic snapshot. A cache that cannot count something reports zero.
 *
 * @param hits calls answered without starting a load: a stored entry, or a load already in flight that the call joined.
 * @param misses calls that started a load.
 * @param evictions stored entries the size bound removed. This is narrower than the `evictions` of Caffeine, Guava and Micrometer: expiry and invalidation are not counted.
 * @param failedLoads loads that ended in a failure or an `Error`, counted once per load, not once per waiter.
 * @throws IllegalArgumentException if a count is negative; the message names the property.
 */
public class CacheStats(
    /** Calls answered without starting a load: a stored entry, or a load already in flight that the call joined. */
    public val hits: Long,
    /** Calls that started a load. */
    public val misses: Long,
    /** Stored entries the size bound removed; expiry and invalidation are not counted. */
    public val evictions: Long,
    /** Loads that ended in a failure or an `Error`, counted once per load. */
    public val failedLoads: Long,
) {
    init {
        require(hits >= 0) { "hits must not be negative" }
        require(misses >= 0) { "misses must not be negative" }
        require(evictions >= 0) { "evictions must not be negative" }
        require(failedLoads >= 0) { "failedLoads must not be negative" }
    }

    /** Two [CacheStats] are equal when their [hits], [misses], [evictions] and [failedLoads] are equal. */
    override fun equals(other: Any?): Boolean =
        other is CacheStats && other.hits == hits && other.misses == misses && other.evictions == evictions && other.failedLoads == failedLoads

    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int = listOf(hits, misses, evictions, failedLoads).fold(0) { acc, n -> 31 * acc + n.hashCode() }
    /**
     * A diagnostic string for logs: [hits], [misses], [evictions] and [failedLoads]. Its format is not part of the API
     * and may change.
     */
    override fun toString(): String = "CacheStats(hits=$hits, misses=$misses, evictions=$evictions, failedLoads=$failedLoads)"

    /** The value of an empty cache. */
    public companion object {
        /** All four counts zero; what [ResponseCache.Disabled] reports. */
        public val Empty: CacheStats = CacheStats(0L, 0L, 0L, 0L)
    }
}
