package com.spartanlabs.webtools.http

import kotlin.time.Duration

/**
 * One call an upstream offers: a stable [id], the function that decodes its answer, and how the answer is cached, charged and classified.
 * Build one with `Endpoint("heroes", ::decodeHeroes) { ttl = 10.minutes; misses(404) }`, once, and reuse it.
 *
 * **Stable Core.** The decoder is the **second argument**, never the trailing lambda (that is the configuration); [T] is inferred from it, and
 * `Endpoint<Hero>("id", decode)` also works. A miscall from another module (the decoder missing, or written as the trailing lambda) is reported as
 * "Cannot access … it is internal" against the constructor: the class has no public constructor. [T] is invariant.
 *
 * The [id] names the endpoint in the cache key and in logs. Two endpoints that share a cache must not share an id: the key's type parameter is a
 * phantom, so one id serves one value type. The decoder sees only the responses classified for decoding (a `2xx` that is not a declared miss and not a
 * throttle) and runs in the cache's load coroutine (on the caller's coroutine with `ResponseCache.Disabled` and for a non-`GET`), so it is CPU-bound work with no
 * blocking I/O; it must not capture per-call data, because a shared load runs the first caller's. It returns a `Result`: a failure that is an [HttpFailure] passes through, any other becomes
 * [HttpFailure.DecodeFailure]; a decoder that throws is treated the same, except an `Error`, which propagates.
 *
 * Equality is identity. `toString()` prints the id, name, ttl, missTtl, cost, the lane's name and the misses, never the decoder.
 *
 * @param T the type of the decoded value.
 * @throws IllegalArgumentException if the id or name is blank, a duration is negative, `cost` is below 1, or a miss status is outside 100..599 or is `429`; thrown through the factory function
 *   `Endpoint(id, decode) { … }`, the only way a consumer builds one. The message names the property and never a value.
 */
// The constructor takes the id, the decoder and every resolved setting (eight parameters), never a list a consumer's call could also
// satisfy: inside the module, which sees both the constructor and the factory, a same-shaped constructor would win over the factory.
public class Endpoint<T : Any> internal constructor(
    /** The endpoint's stable id: the cache key's first part and the name in logs. Not blank. */
    public val id: String,
    /** The decoder; see the class description. Never printed. */
    internal val decode: (HttpResponse) -> Result<T>,
    /** A display name for logs and messages; the [id] by default. Not blank. */
    public val name: String,
    /** How long a decoded value is stored; zero stores nothing (loads in flight still coalesce). Not negative; [Duration.INFINITE] is allowed. */
    public val ttl: Duration,
    /** How long a confirmed miss is stored. Not negative. */
    public val missTtl: Duration,
    /** The budget units one call of this endpoint charges. At least 1. */
    public val cost: Int,
    /** The lane the endpoint's calls are charged to by default. */
    public val lane: Lane,
    misses: Set<Int>,
) {
    /** The statuses that mean "nothing there" and become [Lookup.Missing]; a read-only copy. Each in 100..599 and none `429`. */
    public val misses: Set<Int> = misses.toSet()

    init {
        require(id.isNotBlank()) { MSG_ENDPOINT_ID }
        require(name.isNotBlank()) { MSG_ENDPOINT_NAME }
        require(!ttl.isNegative()) { MSG_ENDPOINT_TTL }
        require(!missTtl.isNegative()) { MSG_ENDPOINT_MISS_TTL }
        require(cost >= 1) { MSG_ENDPOINT_COST }
        require(this.misses.all { it in 100..599 && it != 429 }) { MSG_ENDPOINT_MISSES }
    }

    override fun toString(): String =
        "Endpoint(id=$id, name=$name, ttl=$ttl, missTtl=$missTtl, cost=$cost, lane=${lane.name}, misses=${this.misses.sorted()})"

    /**
     * Collects the settings of an [Endpoint]. Not thread-safe; use it only inside the factory's lambda. **Stable Core.** Further optional settings, when
     * they exist, are added here rather than to the factory's parameter list.
     */
    @WebToolsDsl
    public class Builder internal constructor(id: String) {
        /** A display name for logs; the endpoint's id by default. Not blank. */
        public var name: String = id

        /** How long a decoded value is stored; [Duration.ZERO] by default (in-flight loads coalesce, nothing is stored). Not negative. */
        public var ttl: Duration = Duration.ZERO

        private var explicitMissTtl: Duration? = null

        /**
         * How long a confirmed miss is stored. Unless a value is set, it is the smaller of [ttl] and [CachePolicy.DEFAULT_MISS_TTL] (60 s), read at the
         * moment of the read, so a miss is never stored longer than a value would be unless the consumer says so. Not negative.
         */
        public var missTtl: Duration
            get() = explicitMissTtl ?: minOf(ttl, CachePolicy.DEFAULT_MISS_TTL)
            set(value) { explicitMissTtl = value }

        /** The budget units one call charges; 1 by default. At least 1. */
        public var cost: Int = 1

        /** The lane calls are charged to by default; [Lane.Interactive] by default. */
        public var lane: Lane = Lane.Interactive

        private val missStatuses: MutableSet<Int> = LinkedHashSet()

        /**
         * Declares statuses that mean "nothing there": the call returns [Lookup.Missing], stored for `missTtl`. Repeated calls accumulate; none by default.
         * A status is in 100..599 and never `429` (a `429` is always a throttle).
         *
         * @param statuses the statuses to add.
         */
        public fun misses(vararg statuses: Int) {
            missStatuses.addAll(statuses.toList())
        }

        internal fun missSet(): Set<Int> = missStatuses.toSet()
    }
}

/**
 * Builds an [Endpoint]. **Stable Core.** `Endpoint("heroes", ::decodeHeroes) { ttl = 10.minutes; cost = 2; misses(404) }`.
 *
 * @param T the decoded value's type, inferred from [decode].
 * @param id the stable id: the cache key's first part and the name in logs; not blank.
 * @param decode turns a response classified for decoding into a value or a failure; see [Endpoint].
 * @param configure assigns the optional settings; empty by default.
 * @return the endpoint.
 * @throws IllegalArgumentException if the id or name is blank, a duration is negative, `cost` is below 1, or a miss status is outside 100..599 or is `429`;
 *   the message names the property and never a value.
 */
public fun <T : Any> Endpoint(id: String, decode: (HttpResponse) -> Result<T>, configure: Endpoint.Builder.() -> Unit = {}): Endpoint<T> {
    val builder = Endpoint.Builder(id).apply(configure)
    // The eight-argument call binds the constructor: this function takes at most three.
    return Endpoint(id, decode, builder.name, builder.ttl, builder.missTtl, builder.cost, builder.lane, builder.missSet())
}
