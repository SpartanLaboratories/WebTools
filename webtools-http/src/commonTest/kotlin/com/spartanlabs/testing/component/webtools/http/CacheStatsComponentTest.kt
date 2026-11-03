package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.CacheStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * Level 2 - `CacheStats`: each negative count is refused with a message naming the property; `Empty` is four zeros and
 * equals `CacheStats(0, 0, 0, 0)`; equality, `hashCode` and `toString` cover the four counts; distinct counts are unequal.
 */
class CacheStatsComponentTest {

    @Test
    fun `each negative count is refused with a message naming the property`() {
        assertEquals("hits must not be negative", assertFailsWith<IllegalArgumentException> { CacheStats(-1L, 0L, 0L, 0L) }.message)
        assertEquals("misses must not be negative", assertFailsWith<IllegalArgumentException> { CacheStats(0L, -1L, 0L, 0L) }.message)
        assertEquals("evictions must not be negative", assertFailsWith<IllegalArgumentException> { CacheStats(0L, 0L, -1L, 0L) }.message)
        assertEquals("failedLoads must not be negative", assertFailsWith<IllegalArgumentException> { CacheStats(0L, 0L, 0L, -1L) }.message)
    }

    @Test
    fun `Empty is four zeros`() {
        assertEquals(0L, CacheStats.Empty.hits)
        assertEquals(0L, CacheStats.Empty.misses)
        assertEquals(0L, CacheStats.Empty.evictions)
        assertEquals(0L, CacheStats.Empty.failedLoads)
        assertEquals(CacheStats(0L, 0L, 0L, 0L), CacheStats.Empty)
        assertEquals(CacheStats(0L, 0L, 0L, 0L).hashCode(), CacheStats.Empty.hashCode())
    }

    @Test
    fun `equality hashCode and toString cover the four counts`() {
        val stats = CacheStats(4L, 3L, 2L, 1L)
        assertEquals(CacheStats(4L, 3L, 2L, 1L), stats)
        assertEquals(CacheStats(4L, 3L, 2L, 1L).hashCode(), stats.hashCode())
        assertEquals("CacheStats(hits=4, misses=3, evictions=2, failedLoads=1)", stats.toString())
        assertFalse(stats.equals(null))
        assertFalse(stats.equals("CacheStats(hits=4, misses=3, evictions=2, failedLoads=1)"))
    }

    @Test
    fun `distinct counts are unequal`() {
        val stats = CacheStats(4L, 3L, 2L, 1L)
        assertNotEquals(stats, CacheStats(5L, 3L, 2L, 1L))
        assertNotEquals(stats, CacheStats(4L, 4L, 2L, 1L))
        assertNotEquals(stats, CacheStats(4L, 3L, 3L, 1L))
        assertNotEquals(stats, CacheStats(4L, 3L, 2L, 2L))
        assertNotEquals(stats, CacheStats(1L, 2L, 3L, 4L))
    }
}
