package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.Boom
import com.spartanlabs.testing.support.webtools.http.key
import com.spartanlabs.webtools.http.CacheEntry
import com.spartanlabs.webtools.http.CacheExpiry
import com.spartanlabs.webtools.http.CacheOutcome
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.Residency
import com.spartanlabs.webtools.http.Storage
import com.spartanlabs.webtools.http.isUsable
import com.spartanlabs.webtools.http.outcomeKind
import com.spartanlabs.webtools.http.residencyOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Level 4a - the two pure rules of the cache entry and the log's outcome kind. The lifetime and weight table over `residencyOf`: a `Found` lives
 * its `ttl` and weighs its weight, up to and including `maxWeight`; one over `maxWeight` is not stored; a zero `ttl` is not stored; an infinite
 * `ttl` is `Long.MAX_VALUE`; a `Missing` lives its `missTtl` and weighs 1; a failed, fatal or closed outcome is not stored. The usability rule
 * over `isUsable`: lifetime 0 is never usable; an age of at most `maxAge` is, one nanosecond more is not; a clock that stepped back reads as age
 * zero; an infinite `maxAge` accepts any age. `outcomeKind` prints a class name and never a message. `CacheExpiry` reads the entry's precomputed
 * lifetime on create and update and leaves the current duration unchanged on read. `CacheEntry.isFailure` is true for a failed or fatal outcome
 * only, and the companion's `closed()`, `failed()` and `fatal()` build entries of lifetime 0 and weight 0 that carry their `storedAt`.
 */
class CacheEntryRulesDeterministicTest {

    private val maxWeight = 1_000L
    private val tenMinutes = CachePolicy(10.minutes)

    private fun assertResidency(lifetimeNanos: Long, weight: Int, storage: Storage, actual: Residency, label: String) {
        assertEquals(lifetimeNanos, actual.lifetimeNanos, "$label: lifetime")
        assertEquals(weight, actual.weight, "$label: weight")
        assertEquals(storage, actual.storage, "$label: storage")
    }

    private fun entry(storedAt: Long, lifetimeNanos: Long): CacheEntry =
        CacheEntry(CacheOutcome.Loaded(Lookup.Found("v")), storedAt, lifetimeNanos, 1)

    @Test
    fun `a Found lives its ttl and weighs its weight up to maxWeight`() {
        assertResidency(10.minutes.inWholeNanoseconds, 1, Storage.Stored, residencyOf(CacheOutcome.Loaded(Lookup.Found("v")), tenMinutes, maxWeight), "weight 1")
        assertResidency(10.minutes.inWholeNanoseconds, 1_000, Storage.Stored, residencyOf(CacheOutcome.Loaded(Lookup.Found("v", 1_000)), tenMinutes, maxWeight), "weight = maxWeight")
        assertResidency(0L, 0, Storage.OverMaxWeight, residencyOf(CacheOutcome.Loaded(Lookup.Found("v", 1_001)), tenMinutes, maxWeight), "weight = maxWeight + 1")
    }

    @Test
    fun `a zero ttl is not stored and an infinite ttl is the maximum lifetime`() {
        assertResidency(0L, 0, Storage.ZeroTtl, residencyOf(CacheOutcome.Loaded(Lookup.Found("v")), CachePolicy(Duration.ZERO), maxWeight), "zero ttl")
        assertResidency(Long.MAX_VALUE, 1, Storage.Stored, residencyOf(CacheOutcome.Loaded(Lookup.Found("v")), CachePolicy(Duration.INFINITE), maxWeight), "infinite ttl")
    }

    @Test
    fun `a Missing lives its missTtl and weighs 1`() {
        val thirtySeconds = CachePolicy(Duration.ZERO) { missTtl = 30.seconds }
        assertResidency(30.seconds.inWholeNanoseconds, 1, Storage.Stored, residencyOf(CacheOutcome.Loaded(Lookup.Missing), thirtySeconds, maxWeight), "missing")
        val zeroMiss = CachePolicy(10.minutes) { missTtl = Duration.ZERO }
        assertResidency(0L, 0, Storage.ZeroTtl, residencyOf(CacheOutcome.Loaded(Lookup.Missing), zeroMiss, maxWeight), "zero missTtl")
    }

    @Test
    fun `a failed fatal or closed outcome is not stored`() {
        assertResidency(0L, 0, Storage.NoResult, residencyOf(CacheOutcome.Failed(IllegalStateException("x")), tenMinutes, maxWeight), "failed")
        assertResidency(0L, 0, Storage.NoResult, residencyOf(CacheOutcome.Fatal(Boom()), tenMinutes, maxWeight), "fatal")
        assertResidency(0L, 0, Storage.NoResult, residencyOf(CacheOutcome.Closed, tenMinutes, maxWeight), "closed")
    }

    @Test
    fun `a lifetime of zero is never usable`() {
        val dead = entry(storedAt = 1_000L, lifetimeNanos = 0L)
        assertFalse(isUsable(dead, 1_000L, Duration.ZERO))
        assertFalse(isUsable(dead, 1_000L, Duration.INFINITE))
        assertFalse(isUsable(dead, 500L, 10.minutes))
    }

    @Test
    fun `an age of at most maxAge is usable and one nanosecond more is not`() {
        val live = entry(storedAt = 1_000L, lifetimeNanos = 1L)
        assertTrue(isUsable(live, 1_000L, Duration.ZERO), "age 0 with ZERO")
        assertFalse(isUsable(live, 1_001L, Duration.ZERO), "age 1 ns with ZERO")
        val fiveMinutes = 5.minutes
        assertTrue(isUsable(live, 1_000L + fiveMinutes.inWholeNanoseconds, fiveMinutes), "age = maxAge")
        assertFalse(isUsable(live, 1_000L + fiveMinutes.inWholeNanoseconds + 1L, fiveMinutes), "age = maxAge + 1 ns")
        assertTrue(isUsable(live, 1_000L + fiveMinutes.inWholeNanoseconds, fiveMinutes + 1.nanoseconds), "age = maxAge - 1 ns")
    }

    @Test
    fun `a clock that stepped back reads as age zero and an infinite maxAge accepts any age`() {
        val live = entry(storedAt = 1_000L, lifetimeNanos = 1L)
        assertTrue(isUsable(live, 999L, Duration.ZERO), "nowNanos before storedAt is clamped")
        assertTrue(isUsable(live, Long.MIN_VALUE / 2, Duration.ZERO))
        assertTrue(isUsable(live, Long.MAX_VALUE / 2, Duration.INFINITE))
        assertTrue(isUsable(entry(storedAt = 0L, lifetimeNanos = Long.MAX_VALUE), Long.MAX_VALUE, Duration.INFINITE))
    }

    @Test
    fun `outcomeKind names the kind and a class and never a message`() {
        assertEquals("found", outcomeKind(CacheOutcome.Loaded(Lookup.Found("v"))))
        assertEquals("missing", outcomeKind(CacheOutcome.Loaded(Lookup.Missing)))
        assertEquals("failed (java.lang.IllegalStateException)", outcomeKind(CacheOutcome.Failed(IllegalStateException("SECRET-MESSAGE"))))
        assertEquals("error (${Boom::class.java.name})", outcomeKind(CacheOutcome.Fatal(Boom())))
        assertEquals("closed", outcomeKind(CacheOutcome.Closed))
    }

    @Test
    fun `CacheExpiry reads the precomputed lifetime on create and update and keeps the current duration on read`() {
        val now = 1_000L
        val lifetimes = listOf(0L, 10.minutes.inWholeNanoseconds, Long.MAX_VALUE)
        val currentDurations = listOf(0L, 1L, 5.minutes.inWholeNanoseconds)
        lifetimes.forEach { lifetime ->
            val stored = entry(storedAt = now, lifetimeNanos = lifetime)
            assertEquals(lifetime, CacheExpiry.expireAfterCreate(key(), stored, now), "create, lifetime $lifetime")
            currentDurations.forEach { current ->
                assertEquals(lifetime, CacheExpiry.expireAfterUpdate(key(), stored, now, current), "update, lifetime $lifetime, current $current")
                assertEquals(current, CacheExpiry.expireAfterRead(key(), stored, now, current), "read, lifetime $lifetime, current $current")
            }
        }
    }

    @Test
    fun `isFailure is true for a failed or fatal outcome only and the companion factories are never stored`() {
        val failure = IllegalStateException("x")
        val boom = Boom()
        val loaded = CacheEntry(CacheOutcome.Loaded(Lookup.Found("v")), 7L, 1L, 1)
        assertFalse(loaded.isFailure, "loaded")

        val failed = CacheEntry.failed(failure, 42L)
        assertTrue(failed.isFailure, "failed")
        assertSame(failure, (failed.outcome as CacheOutcome.Failed).failure)
        assertEquals(42L, failed.storedAt, "failed: storedAt")
        assertEquals(0L, failed.lifetimeNanos, "failed: lifetime")
        assertEquals(0, failed.weight, "failed: weight")

        val fatal = CacheEntry.fatal(boom, 43L)
        assertTrue(fatal.isFailure, "fatal")
        assertSame(boom, (fatal.outcome as CacheOutcome.Fatal).error)
        assertEquals(43L, fatal.storedAt, "fatal: storedAt")
        assertEquals(0L, fatal.lifetimeNanos, "fatal: lifetime")
        assertEquals(0, fatal.weight, "fatal: weight")

        val closed = CacheEntry.closed()
        assertFalse(closed.isFailure, "closed")
        assertSame(CacheOutcome.Closed, closed.outcome)
        assertEquals(0L, closed.storedAt, "closed: storedAt")
        assertEquals(0L, closed.lifetimeNanos, "closed: lifetime")
        assertEquals(0, closed.weight, "closed: weight")
    }
}
