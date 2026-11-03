package com.spartanlabs.webtools.http

import com.github.benmanes.caffeine.cache.Expiry
import kotlin.time.Duration

/** What a finished load produced. Only [Loaded] can be stored. */
internal sealed interface CacheOutcome {
    /** The loader returned a [Lookup]: a `Found` or a `Missing`. */
    class Loaded(val lookup: Lookup<*>) : CacheOutcome

    /** The loader failed with [failure], which every waiter receives inside its `Result`; never a `CancellationException`. */
    class Failed(val failure: Throwable) : CacheOutcome

    /** The loader threw [error], an `Error` (or any non-`Exception`) kept by identity and rethrown to every waiter. */
    class Fatal(val error: Throwable) : CacheOutcome

    /** The cache closed before the load ended, or the load never started. */
    object Closed : CacheOutcome
}

/**
 * The value of the shared future: immutable, built once when the load ends.
 *
 * @param outcome what the load produced.
 * @param storedAt the ticker's reading when the load ended, in nanoseconds.
 * @param lifetimeNanos how long the entry lives; 0 means "not stored".
 * @param weight the entry's weight against the cache's bound; 0 when not stored.
 */
internal class CacheEntry(val outcome: CacheOutcome, val storedAt: Long, val lifetimeNanos: Long, val weight: Int) {
    /** Whether the load ended in a failure or an error (counted once per load in `failedLoads`). */
    val isFailure: Boolean get() = outcome is CacheOutcome.Failed || outcome is CacheOutcome.Fatal

    /** The entries the safety nets and `close()` complete a future with: none of them is ever stored. */
    companion object {
        /** The closed outcome. */
        fun closed(): CacheEntry = CacheEntry(CacheOutcome.Closed, 0L, 0L, 0)

        /** A failed outcome ended at [storedAt]. */
        fun failed(failure: Throwable, storedAt: Long): CacheEntry = CacheEntry(CacheOutcome.Failed(failure), storedAt, 0L, 0)

        /** A fatal outcome ended at [storedAt]. */
        fun fatal(error: Throwable, storedAt: Long): CacheEntry = CacheEntry(CacheOutcome.Fatal(error), storedAt, 0L, 0)
    }
}

/** Why a finished load was or was not stored; `note` is the DEBUG log's storage text. */
internal enum class Storage(val note: String) {
    Stored("stored"),
    ZeroTtl("not stored: zero TTL"),
    OverMaxWeight("not stored: over maxWeight"),
    NoResult("not stored: not a result"),
    Removed("not stored: removed before the load ended"),
}

/** The lifetime and weight a finished load is stored with, and why. */
internal class Residency(val lifetimeNanos: Long, val weight: Int, val storage: Storage)

/**
 * The lifetime and weight a finished load is stored with (§6.6 of the plan): pure.
 *
 * @param outcome the load's outcome.
 * @param policy the policy of the call that started the load.
 * @param maxWeight the cache's bound.
 * @return the residency; lifetime 0 and weight 0 mean "not stored".
 */
internal fun residencyOf(outcome: CacheOutcome, policy: CachePolicy, maxWeight: Long): Residency = when (outcome) {
    is CacheOutcome.Loaded -> when (val lookup = outcome.lookup) {
        is Lookup.Found<*> -> residencyFor(policy.ttl, lookup.weight, maxWeight)
        Lookup.Missing -> residencyFor(policy.missTtl, 1, maxWeight)
    }
    is CacheOutcome.Failed, is CacheOutcome.Fatal, CacheOutcome.Closed -> Residency(0L, 0, Storage.NoResult)
}

private fun residencyFor(ttl: Duration, weight: Int, maxWeight: Long): Residency {
    val lifetimeNanos = ttl.inWholeNanoseconds        // Duration.INFINITE → Long.MAX_VALUE
    return when {
        lifetimeNanos <= 0L -> Residency(0L, 0, Storage.ZeroTtl)
        weight > maxWeight -> Residency(0L, 0, Storage.OverMaxWeight)
        else -> Residency(lifetimeNanos, weight, Storage.Stored)
    }
}

/**
 * Whether a completed [entry] may be served to a call with [maxAge] at tick [nowNanos] (§6.6): pure.
 *
 * @param entry the completed entry.
 * @param nowNanos the ticker's reading now.
 * @param maxAge the oldest entry the call accepts.
 * @return `true` when its lifetime is positive and its age, clamped at zero, is at most [maxAge].
 */
internal fun isUsable(entry: CacheEntry, nowNanos: Long, maxAge: Duration): Boolean =
    entry.lifetimeNanos > 0L && (nowNanos - entry.storedAt).coerceAtLeast(0L) <= maxAge.inWholeNanoseconds

/**
 * The outcome's kind for the DEBUG log: a class name for a failure or an error, never a message.
 *
 * @param outcome the load's outcome.
 * @return `found`, `missing`, `failed (<class name>)`, `error (<class name>)` or `closed`.
 */
internal fun outcomeKind(outcome: CacheOutcome): String = when (outcome) {
    is CacheOutcome.Loaded -> if (outcome.lookup is Lookup.Found<*>) "found" else "missing"
    is CacheOutcome.Failed -> "failed (${outcome.failure.javaClass.name})"
    is CacheOutcome.Fatal -> "error (${outcome.error.javaClass.name})"
    CacheOutcome.Closed -> "closed"
}

/** Reads each entry's precomputed lifetime; an object, because `Expiry` has three abstract methods and is not a SAM. */
internal object CacheExpiry : Expiry<CacheKey<*>, CacheEntry> {
    override fun expireAfterCreate(key: CacheKey<*>, value: CacheEntry, currentTime: Long): Long = value.lifetimeNanos
    override fun expireAfterUpdate(key: CacheKey<*>, value: CacheEntry, currentTime: Long, currentDuration: Long): Long = value.lifetimeNanos
    override fun expireAfterRead(key: CacheKey<*>, value: CacheEntry, currentTime: Long, currentDuration: Long): Long = currentDuration
}
