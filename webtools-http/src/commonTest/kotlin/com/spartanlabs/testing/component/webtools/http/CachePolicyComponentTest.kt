package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.CachePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `CachePolicy`: `missTtl` defaults to `DEFAULT_MISS_TTL` (60 seconds) and the builder reads that default and
 * overrides it; zero and `Duration.INFINITE` are accepted for both durations; a negative `ttl` or `missTtl` (minus infinity
 * included) is refused with a message that names the property and holds no value; equality, `hashCode` and `toString` cover
 * both durations; the factory builds a policy equal to another built with the same values.
 */
class CachePolicyComponentTest {

    @Test
    fun `missTtl defaults to sixty seconds`() {
        val policy = CachePolicy(1.minutes)
        assertEquals(1.minutes, policy.ttl)
        assertEquals(CachePolicy.DEFAULT_MISS_TTL, policy.missTtl)
        assertEquals(60.seconds, CachePolicy.DEFAULT_MISS_TTL)
    }

    @Test
    fun `the builder reads its own default and overrides it`() {
        var seen: Duration? = null
        val policy = CachePolicy(1.minutes) {
            seen = missTtl
            missTtl = 5.seconds
        }
        assertEquals(CachePolicy.DEFAULT_MISS_TTL, seen)
        assertEquals(5.seconds, policy.missTtl)
    }

    @Test
    fun `zero and infinity are accepted for both durations`() {
        assertEquals(Duration.ZERO, CachePolicy(Duration.ZERO) { missTtl = Duration.ZERO }.ttl)
        assertEquals(Duration.ZERO, CachePolicy(Duration.ZERO) { missTtl = Duration.ZERO }.missTtl)
        assertEquals(Duration.INFINITE, CachePolicy(Duration.INFINITE) { missTtl = Duration.INFINITE }.ttl)
        assertEquals(Duration.INFINITE, CachePolicy(Duration.INFINITE) { missTtl = Duration.INFINITE }.missTtl)
    }

    @Test
    fun `a negative duration is refused with a message naming the property and no value`() {
        listOf((-1).seconds, -Duration.INFINITE).forEach { negative ->
            assertEquals("ttl must not be negative", assertFailsWith<IllegalArgumentException> { CachePolicy(negative) }.message)
            assertEquals("missTtl must not be negative", assertFailsWith<IllegalArgumentException> { CachePolicy(1.minutes) { missTtl = negative } }.message)
        }
    }

    @Test
    fun `equality hashCode and toString cover both durations`() {
        val policy = CachePolicy(10.minutes) { missTtl = 30.seconds }
        val same = CachePolicy(10.minutes) { missTtl = 30.seconds }
        assertEquals(policy, same)
        assertEquals(policy.hashCode(), same.hashCode())
        assertNotEquals(policy, CachePolicy(11.minutes) { missTtl = 30.seconds })
        assertNotEquals(policy, CachePolicy(10.minutes) { missTtl = 31.seconds })
        assertEquals("CachePolicy(ttl=10m, missTtl=30s)", policy.toString())
        assertFalse(policy.equals(null))
        assertFalse(policy.equals("CachePolicy(ttl=10m, missTtl=30s)"))
    }

    @Test
    fun `the factory result equals a policy built with the same values and differs from one built with other values`() {
        assertEquals(CachePolicy(2.minutes), CachePolicy(2.minutes) { missTtl = 60.seconds })
        assertNotEquals(CachePolicy(2.minutes), CachePolicy(2.minutes) { missTtl = 59.seconds })
        assertNotEquals(CachePolicy(2.minutes), CachePolicy(3.minutes))
    }
}
