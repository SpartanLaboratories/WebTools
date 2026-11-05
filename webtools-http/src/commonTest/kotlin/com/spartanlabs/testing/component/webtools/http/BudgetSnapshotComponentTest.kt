package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.BudgetSnapshot
import com.spartanlabs.webtools.http.WindowSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Level 2 - `BudgetSnapshot` and `WindowSnapshot`: `Empty` is version 1, stamp 0, no windows, no pause; both constructors;
 * `windows` is a read-only copy; version 0 and a negative pause refused with the property in the message; a blank window name and
 * a negative `used` refused; equality and `toString()` (counts only, never an encoding).
 */
class BudgetSnapshotComponentTest {

    private val day = WindowSnapshot("day", 86_400_000L, 7)

    @Test
    fun `Empty is version 1 with nothing in it`() {
        assertEquals(1, BudgetSnapshot.CURRENT_VERSION)
        assertEquals(1, BudgetSnapshot.Empty.version)
        assertEquals(0L, BudgetSnapshot.Empty.takenAtEpochMillis)
        assertTrue(BudgetSnapshot.Empty.windows.isEmpty())
        assertEquals(0L, BudgetSnapshot.Empty.pausedUntilEpochMillis)
    }

    @Test
    fun `both constructors build the same snapshot`() {
        val full = BudgetSnapshot(1, 1_000L, listOf(day), 2_000L)
        val short = BudgetSnapshot(1_000L, listOf(day), 2_000L)
        assertEquals(full, short)
        assertEquals(BudgetSnapshot.CURRENT_VERSION, short.version)
        assertEquals(1_000L, short.takenAtEpochMillis)
        assertEquals(listOf(day), short.windows)
        assertEquals(2_000L, short.pausedUntilEpochMillis)
        assertEquals(2, BudgetSnapshot(2, 0L, emptyList(), 0L).version)
    }

    @Test
    fun `windows is a read-only copy`() {
        val source = mutableListOf(day)
        val snapshot = BudgetSnapshot(0L, source, 0L)
        source += WindowSnapshot("minute", 60_000L, 1)
        assertEquals(listOf(day), snapshot.windows)
    }

    @Test
    fun `version 0 and a negative pause are refused with the property in the message`() {
        assertEquals("version must be at least 1", assertFailsWith<IllegalArgumentException> { BudgetSnapshot(0, 0L, emptyList(), 0L) }.message)
        assertEquals("pausedUntilEpochMillis must not be negative", assertFailsWith<IllegalArgumentException> { BudgetSnapshot(0L, emptyList(), -1L) }.message)
    }

    @Test
    fun `WindowSnapshot refuses a blank name and a negative used`() {
        assertEquals("window must not be blank", assertFailsWith<IllegalArgumentException> { WindowSnapshot(" ", 0L, 1) }.message)
        assertEquals("used must not be negative", assertFailsWith<IllegalArgumentException> { WindowSnapshot("day", 0L, -1) }.message)
        assertEquals(0, WindowSnapshot("day", 0L, 0).used)
    }

    @Test
    fun `equality is on every property`() {
        assertEquals(day, WindowSnapshot("day", 86_400_000L, 7))
        assertEquals(day.hashCode(), WindowSnapshot("day", 86_400_000L, 7).hashCode())
        assertNotEquals(day, WindowSnapshot("day", 86_400_000L, 8))
        assertNotEquals(day, WindowSnapshot("day", 0L, 7))
        assertNotEquals(day, WindowSnapshot("x", 86_400_000L, 7))
        val a = BudgetSnapshot(1_000L, listOf(day), 2_000L)
        assertEquals(a.hashCode(), BudgetSnapshot(1_000L, listOf(day), 2_000L).hashCode())
        assertNotEquals(a, BudgetSnapshot(1_001L, listOf(day), 2_000L))
        assertNotEquals(a, BudgetSnapshot(1_000L, emptyList(), 2_000L))
        assertNotEquals(a, BudgetSnapshot(1_000L, listOf(day), 2_001L))
        assertNotEquals(a, BudgetSnapshot(2, 1_000L, listOf(day), 2_000L))
        assertNotEquals<Any>(a, day)
    }

    @Test
    fun `toString prints the counts and never an encoding`() {
        val text = BudgetSnapshot(1_000L, listOf(day, WindowSnapshot("secret-window", 0L, 3)), 2_000L).toString()
        assertEquals("BudgetSnapshot(version=1, takenAt=1000, windows=2, pausedUntil=2000)", text)
        assertFalse("secret-window" in text)
        assertEquals("WindowSnapshot(window=day, windowStart=86400000, used=7)", day.toString())
    }
}
