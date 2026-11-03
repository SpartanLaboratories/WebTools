package com.spartanlabs.webtools.http

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long a load's outcome is stored. [ttl] is for a value ([Lookup.Found]), [missTtl] for a confirmed miss ([Lookup.Missing]); both are measured from the
 * load's completion. A failure is never stored. Build one with the factory `CachePolicy(ttl) { missTtl = … }`.
 *
 * **Stable Core.** A [ttl] of [Duration.ZERO] coalesces loads in flight and stores nothing; [Duration.INFINITE] is allowed. Equality is on both durations;
 * `toString()` prints both.
 */
public class CachePolicy internal constructor(
    /** How long a [Lookup.Found] is stored; zero stores nothing (loads in flight still coalesce). Not negative. */
    public val ttl: Duration,
    /** How long a [Lookup.Missing] is stored; zero stores nothing. Not negative. */
    public val missTtl: Duration,
) {
    init {
        require(!ttl.isNegative()) { "ttl must not be negative" }
        require(!missTtl.isNegative()) { "missTtl must not be negative" }
    }

    override fun equals(other: Any?): Boolean = other is CachePolicy && other.ttl == ttl && other.missTtl == missTtl
    override fun hashCode(): Int = 31 * ttl.hashCode() + missTtl.hashCode()
    override fun toString(): String = "CachePolicy(ttl=$ttl, missTtl=$missTtl)"

    /**
     * Collects the optional properties of a [CachePolicy]. Not thread-safe; use it only inside the factory's lambda. **Stable Core.** A later `staleFor` is added here.
     */
    @WebToolsDsl
    public class Builder internal constructor() {
        /** How long a confirmed miss is stored; [DEFAULT_MISS_TTL] by default. Not negative. */
        public var missTtl: Duration = DEFAULT_MISS_TTL
    }

    /** The default of [Builder.missTtl]. */
    public companion object {
        /** 60 seconds. */
        public val DEFAULT_MISS_TTL: Duration = 60.seconds
    }
}

/**
 * Builds a [CachePolicy].
 *
 * **Stable Core.** `CachePolicy(10.minutes) { missTtl = 30.seconds }`; without the lambda, `missTtl` is [CachePolicy.DEFAULT_MISS_TTL].
 *
 * @param ttl how long a value is stored; not negative; zero stores nothing.
 * @param configure assigns the optional properties.
 * @return the policy.
 * @throws IllegalArgumentException if [ttl] or the configured `missTtl` is negative; the message names the property and never a value.
 */
public fun CachePolicy(ttl: Duration, configure: CachePolicy.Builder.() -> Unit = {}): CachePolicy =
    CachePolicy(ttl, CachePolicy.Builder().apply(configure).missTtl)
