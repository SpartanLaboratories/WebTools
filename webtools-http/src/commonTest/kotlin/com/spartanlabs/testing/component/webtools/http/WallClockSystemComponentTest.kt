package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.WallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - `WallClock.System`: reads after 2024-01-01, two reads non-decreasing and within 5 s,
 * `toString()`, and the object is a `WallClock` singleton.
 */
class WallClockSystemComponentTest {

    @Test
    fun `the reading is after 2024-01-01`() {
        assertTrue(WallClock.System.nowEpochMillis() > 1_704_067_200_000L)   // 2024-01-01T00:00:00Z in epoch milliseconds
    }

    @Test
    fun `two reads are non-decreasing and close`() {
        val first = WallClock.System.nowEpochMillis()
        val second = WallClock.System.nowEpochMillis()
        assertTrue(second >= first, "second read $second is before the first $first")
        assertTrue(second - first < 5_000L, "the two reads are ${second - first} ms apart")
    }

    @Test
    fun `toString names the object`() {
        assertEquals("WallClock.System", WallClock.System.toString())
    }

    @Test
    fun `the object is a WallClock singleton`() {
        val clock: WallClock = WallClock.System
        assertSame(WallClock.System, clock)
        assertSame<Any>(WallClock.System, WallClock.System)
    }
}
