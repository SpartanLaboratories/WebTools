package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.RateWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `RateWindow`: the factory with and without its lambda, `limit = published - margin`, each rule refused with a
 * message that names the property and no value, equality over every property, `toString()`, and the builder's defaults.
 */
class RateWindowComponentTest {

    private fun messageOf(block: () -> RateWindow): String? = assertFailsWith<IllegalArgumentException> { block() }.message

    @Test
    fun `the factory without the lambda uses the builder's defaults`() {
        val window = RateWindow("minute", 60, 1.minutes)
        assertEquals("minute", window.name)
        assertEquals(60, window.published)
        assertEquals(1.minutes, window.period)
        assertEquals(0, window.margin)
        assertFalse(window.hard)
        assertEquals(60, window.limit)
    }

    @Test
    fun `the factory with the lambda assigns margin and hard`() {
        val window = RateWindow("day", 3_000, 1.days) { margin = 100; hard = true }
        assertEquals(100, window.margin)
        assertTrue(window.hard)
        assertEquals(2_900, window.limit)
    }

    @Test
    fun `published below 1 is refused`() {
        assertEquals("published must be at least 1", messageOf { RateWindow("w", 0, 1.minutes) })
        assertEquals("published must be at least 1", messageOf { RateWindow("w", -1, 1.minutes) })
        assertEquals(1, RateWindow("w", 1, 1.minutes).limit)
    }

    @Test
    fun `margin outside 0 until published is refused`() {
        assertEquals("margin must be at least 0 and below published", messageOf { RateWindow("w", 10, 1.minutes) { margin = -1 } })
        assertEquals("margin must be at least 0 and below published", messageOf { RateWindow("w", 10, 1.minutes) { margin = 10 } })
        assertEquals(1, RateWindow("w", 10, 1.minutes) { margin = 9 }.limit)
    }

    @Test
    fun `a period that is not a positive whole number of milliseconds is refused`() {
        val message = "period must be a positive whole number of milliseconds"
        assertEquals(message, messageOf { RateWindow("w", 10, Duration.ZERO) })
        assertEquals(message, messageOf { RateWindow("w", 10, (-1).seconds) })
        assertEquals(message, messageOf { RateWindow("w", 10, Duration.INFINITE) })
        assertEquals(message, messageOf { RateWindow("w", 10, 1500.microseconds) })
        assertEquals(1.milliseconds, RateWindow("w", 10, 1.milliseconds).period)
    }

    @Test
    fun `a blank name is refused`() {
        assertEquals("name must not be blank", messageOf { RateWindow("", 10, 1.minutes) })
        assertEquals("name must not be blank", messageOf { RateWindow("  ", 10, 1.minutes) })
    }

    @Test
    fun `equality is over every property`() {
        val a = RateWindow("w", 10, 1.minutes) { margin = 2; hard = true }
        assertEquals(a, RateWindow("w", 10, 1.minutes) { margin = 2; hard = true })
        assertEquals(a.hashCode(), RateWindow("w", 10, 1.minutes) { margin = 2; hard = true }.hashCode())
        assertNotEquals(a, RateWindow("x", 10, 1.minutes) { margin = 2; hard = true })
        assertNotEquals(a, RateWindow("w", 11, 1.minutes) { margin = 2; hard = true })
        assertNotEquals(a, RateWindow("w", 10, 2.minutes) { margin = 2; hard = true })
        assertNotEquals(a, RateWindow("w", 10, 1.minutes) { margin = 3; hard = true })
        assertNotEquals(a, RateWindow("w", 10, 1.minutes) { margin = 2; hard = false })
        assertNotEquals<Any>(a, "w")
    }

    @Test
    fun `toString prints every property`() {
        assertEquals("RateWindow(name=w, published=10, period=1m, margin=2, hard=true)", RateWindow("w", 10, 1.minutes) { margin = 2; hard = true }.toString())
    }

    @Test
    fun `the builder reads its own defaults`() {
        var margin = -1
        var hard = true
        RateWindow("w", 10, 1.minutes) { margin = this.margin; hard = this.hard }
        assertEquals(0, margin)
        assertFalse(hard)
    }
}
