package com.spartanlabs.webtools.http

private const val WEIGHT_MESSAGE = "weight must be at least 1"

/**
 * What a load found: a value ([Found]) or a confirmed miss ([Missing]). A confirmed miss is a value, never `null` and never a failure.
 *
 * **Stable Core.** `sealed` because the closed pair is the contract; a `when` over a [Lookup] needs no `else`.
 *
 * @param V the type of the stored value.
 */
public sealed interface Lookup<out V : Any> {

    /**
     * A value the endpoint returned, stored for the policy's `ttl`.
     *
     * **Stable Core.** Equality is on [value] and [weight]. `toString()` prints the weight and never the value.
     *
     * @param value the decoded value.
     * @param weight how much of the cache's `maxWeight` the value uses: at least 1.
     * @throws IllegalArgumentException if [weight] is below 1 (Caffeine never size-evicts an entry of weight 0).
     */
    public class Found<out V : Any>(
        /** The decoded value. */
        public val value: V,
        /** The value's weight against the cache's `maxWeight`; at least 1. */
        public val weight: Int,
    ) : Lookup<V> {
        /**
         * A value of weight 1.
         *
         * @param value the decoded value.
         */
        public constructor(value: V) : this(value, 1)

        init {
            require(weight >= 1) { WEIGHT_MESSAGE }
        }

        override fun equals(other: Any?): Boolean = other is Found<*> && other.value == value && other.weight == weight
        override fun hashCode(): Int = 31 * value.hashCode() + weight
        override fun toString(): String = "Found(weight=$weight)"
    }

    /**
     * A confirmed miss (the endpoint declared that this answer means "nothing there"), stored for the policy's `missTtl`.
     *
     * **Stable Core.** `toString()` is `Missing`.
     */
    public object Missing : Lookup<Nothing> {
        override fun toString(): String = "Missing"
    }
}
