package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.WindowReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `RateSignals` and `WindowReading`: `None` is empty; the builder collects `throttled`, `retryAfter` and the readings
 * (a second reading of one name replaces the first); `readings` is a read-only copy; a negative `retryAfter` and a blank reading
 * name are refused; equality and hash; `toString()` prints window names and no count; `WindowReading` with and without `resetIn`,
 * a negative `resetIn` refused, a negative `remaining` accepted.
 */
class RateSignalsComponentTest {

    @Test
    fun `None is not throttled and carries nothing`() {
        assertFalse(RateSignals.None.throttled)
        assertNull(RateSignals.None.retryAfter)
        assertTrue(RateSignals.None.readings.isEmpty())
        assertEquals(RateSignals.None, RateSignals { })
    }

    @Test
    fun `the builder collects the properties and a second reading of one name replaces the first`() {
        val signals = RateSignals {
            throttled = true
            retryAfter = 30.seconds
            reading("minute", WindowReading(5))
            reading("day", WindowReading(0, 10.seconds))
            reading("minute", WindowReading(3))
        }
        assertTrue(signals.throttled)
        assertEquals(30.seconds, signals.retryAfter)
        assertEquals(mapOf("minute" to WindowReading(3), "day" to WindowReading(0, 10.seconds)), signals.readings)
    }

    @Test
    fun `readings is a read-only copy of the builder's map`() {
        lateinit var builder: RateSignals.Builder
        val signals = RateSignals { builder = this; reading("a", WindowReading(1)) }
        builder.reading("b", WindowReading(2))
        assertEquals(setOf("a"), signals.readings.keys)
    }

    @Test
    fun `a negative retryAfter and a blank reading name are refused`() {
        assertEquals("retryAfter must not be negative", assertFailsWith<IllegalArgumentException> { RateSignals { retryAfter = (-1).seconds } }.message)
        assertEquals("a reading's window name must not be blank", assertFailsWith<IllegalArgumentException> { RateSignals { reading(" ", WindowReading(1)) } }.message)
        assertEquals("a reading's window name must not be blank", assertFailsWith<IllegalArgumentException> { RateSignals { reading("", WindowReading(1)) } }.message)
    }

    @Test
    fun `equality and hash are on the three properties`() {
        val a = RateSignals { throttled = true; retryAfter = 1.seconds; reading("w", WindowReading(1)) }
        val same = RateSignals { throttled = true; retryAfter = 1.seconds; reading("w", WindowReading(1)) }
        assertEquals(a, same)
        assertEquals(a.hashCode(), same.hashCode())
        assertNotEquals(a, RateSignals { throttled = false; retryAfter = 1.seconds; reading("w", WindowReading(1)) })
        assertNotEquals(a, RateSignals { throttled = true; retryAfter = 2.seconds; reading("w", WindowReading(1)) })
        assertNotEquals(a, RateSignals { throttled = true; retryAfter = 1.seconds; reading("w", WindowReading(2)) })
        assertNotEquals(a, RateSignals { throttled = true; retryAfter = 1.seconds })
        assertNotEquals<Any>(a, "w")
    }

    @Test
    fun `toString prints the window names and never a count`() {
        val text = RateSignals { throttled = true; retryAfter = 30.seconds; reading("minute", WindowReading(12345, 67890.seconds)) }.toString()
        assertEquals("RateSignals(throttled=true, retryAfter=30s, windows=[minute])", text)
        assertFalse("12345" in text)
        assertFalse("67890" in text)
    }

    @Test
    fun `a WindowReading with and without resetIn`() {
        assertEquals(5, WindowReading(5).remaining)
        assertNull(WindowReading(5).resetIn)
        assertEquals(10.seconds, WindowReading(5, 10.seconds).resetIn)
        assertEquals(WindowReading(5), WindowReading(5, null))
        assertEquals(WindowReading(5, 10.seconds), WindowReading(5, 10.seconds))
        assertEquals(WindowReading(5, 10.seconds).hashCode(), WindowReading(5, 10.seconds).hashCode())
        assertNotEquals(WindowReading(5), WindowReading(5, 10.seconds))
        assertNotEquals(WindowReading(5), WindowReading(6))
        assertEquals("WindowReading(remaining=5, resetIn=10s)", WindowReading(5, 10.seconds).toString())
        assertEquals("WindowReading(remaining=5, resetIn=null)", WindowReading(5).toString())
    }

    @Test
    fun `a negative resetIn is refused and a negative remaining accepted`() {
        assertEquals("resetIn must not be negative", assertFailsWith<IllegalArgumentException> { WindowReading(1, (-1).seconds) }.message)
        assertEquals(-3, WindowReading(-3).remaining)
        assertEquals(-3, WindowReading(-3, 1.seconds).remaining)
    }
}
