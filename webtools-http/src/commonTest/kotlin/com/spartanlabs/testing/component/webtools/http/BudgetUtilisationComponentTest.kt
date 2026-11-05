package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.BudgetUtilisation
import com.spartanlabs.webtools.http.WindowUtilisation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `WindowUtilisation.remaining` is `max(0, limit - used - queued)` and `fraction` is `used / limit` (above 1.0
 * allowed); each rule is refused with the property in the message; `BudgetUtilisation` refuses a negative `pausedFor` and
 * `waiting`; equality and `toString()`.
 */
class BudgetUtilisationComponentTest {

    private fun window(used: Int = 40, queued: Int = 0, limit: Int = 50, published: Int = 60, elapsed: Double = 0.5, name: String = "minute") =
        WindowUtilisation(name, used, queued, limit, published, 0L, 60_000L, false, elapsed)

    @Test
    fun `remaining and fraction derive from the counts`() {
        assertEquals(10, window(used = 40, queued = 0).remaining)
        assertEquals(5, window(used = 40, queued = 5).remaining)
        assertEquals(0, window(used = 45, queued = 10).remaining)
        assertEquals(0.8, window(used = 40).fraction)
        assertEquals(1.2, window(used = 60).fraction)
        assertEquals(0.0, window(used = 0).fraction)
    }

    @Test
    fun `each rule of WindowUtilisation is refused with the property in the message`() {
        assertEquals("name must not be blank", assertFailsWith<IllegalArgumentException> { window(name = " ") }.message)
        assertEquals("used must not be negative", assertFailsWith<IllegalArgumentException> { window(used = -1) }.message)
        assertEquals("queued must not be negative", assertFailsWith<IllegalArgumentException> { window(queued = -1) }.message)
        assertEquals("limit must be at least 1", assertFailsWith<IllegalArgumentException> { window(limit = 0, published = 0) }.message)
        assertEquals("published must be at least limit", assertFailsWith<IllegalArgumentException> { window(limit = 50, published = 49) }.message)
        assertEquals("elapsedFraction must be between 0 and 1", assertFailsWith<IllegalArgumentException> { window(elapsed = -0.1) }.message)
        assertEquals("elapsedFraction must be between 0 and 1", assertFailsWith<IllegalArgumentException> { window(elapsed = 1.1) }.message)
        assertEquals("elapsedFraction must be between 0 and 1", assertFailsWith<IllegalArgumentException> { window(elapsed = Double.NaN) }.message)
        assertEquals(0.0, window(elapsed = 0.0).elapsedFraction)
        assertEquals(1.0, window(elapsed = 1.0).elapsedFraction)
        assertEquals(50, window(limit = 50, published = 50).published)
    }

    @Test
    fun `BudgetUtilisation refuses a negative pausedFor and waiting`() {
        assertEquals("pausedFor must not be negative", assertFailsWith<IllegalArgumentException> { BudgetUtilisation(emptyList(), (-1).seconds, 0) }.message)
        assertEquals("waiting must not be negative", assertFailsWith<IllegalArgumentException> { BudgetUtilisation(emptyList(), Duration.ZERO, -1) }.message)
    }

    @Test
    fun `windows is a read-only copy`() {
        val source = mutableListOf(window())
        val utilisation = BudgetUtilisation(source, Duration.ZERO, 0)
        source += window(name = "day")
        assertEquals(1, utilisation.windows.size)
    }

    @Test
    fun `equality is over every property`() {
        assertEquals(window(), window())
        assertEquals(window().hashCode(), window().hashCode())
        assertNotEquals(window(), window(used = 41))
        assertNotEquals(window(), window(queued = 1))
        assertNotEquals(window(), window(elapsed = 0.6))
        assertNotEquals(window(), window(name = "day"))
        assertNotEquals(window(), WindowUtilisation("minute", 40, 0, 50, 60, 0L, 60_000L, true, 0.5))
        assertNotEquals(window(), WindowUtilisation("minute", 40, 0, 50, 60, 1L, 60_000L, false, 0.5))
        val a = BudgetUtilisation(listOf(window()), 3.seconds, 2)
        assertEquals(a, BudgetUtilisation(listOf(window()), 3.seconds, 2))
        assertEquals(a.hashCode(), BudgetUtilisation(listOf(window()), 3.seconds, 2).hashCode())
        assertNotEquals(a, BudgetUtilisation(listOf(window()), 4.seconds, 2))
        assertNotEquals(a, BudgetUtilisation(listOf(window()), 3.seconds, 3))
        assertNotEquals(a, BudgetUtilisation(emptyList(), 3.seconds, 2))
        assertNotEquals<Any>(a, window())
    }

    @Test
    fun `toString prints the properties`() {
        assertEquals(
            "WindowUtilisation(name=minute, used=40, queued=0, limit=50, published=60, windowStart=0, resetsAt=60000, hard=false, elapsedFraction=0.5)",
            window().toString(),
        )
        assertEquals("BudgetUtilisation(windows=[], pausedFor=3s, waiting=2)", BudgetUtilisation(emptyList(), 3.seconds, 2).toString())
    }
}
