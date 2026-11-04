package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

private const val LIMIT = 30

/**
 * Level 2 - the write-ahead after a simulated crash: for `persistAhead` in {1, 3, 10} and n = 1..25 grants on a hard day window of 30, a budget dropped
 * without `flush()` and a second budget over the same `FakeBudgetStore` restores a count at least n, at most `n + persistAhead - 1` and at most the limit,
 * and the second budget refuses once exactly `limit - restored` more grants were made; a clean `flush()` before the second budget restores exactly n; a
 * crash between `flush()` and further grants never under-counts; a `flush()` after every grant was released writes the count of zero and resets the
 * persisted mark, so the grants that follow it are written ahead again and a crash never under-counts them.
 */
class WindowedCallBudgetCrashComponentTest {

    private val persistAheads = listOf(1, 3, 10)

    @Test
    fun `a crash restores at least the grants and at most persistAhead minus one more`() = runTest {
        for (persistAhead in persistAheads) {
            for (n in 1..25) {
                val clock = virtualClock()
                val store = FakeBudgetStore()
                budgetOn(clock, dayWindow(LIMIT)) { this.store = store; this.persistAhead = persistAhead }.use { first ->
                    repeat(n) { first.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
                }                                                                       // dropped without flush(): the crash
                budgetOn(clock, dayWindow(LIMIT)) { this.store = store; this.persistAhead = persistAhead }.use { second ->
                    val restored = second.utilisation().windows.single().used
                    val label = "persistAhead=$persistAhead, n=$n"
                    assertTrue(restored >= n, "$label: restored $restored under-counts")
                    assertTrue(restored <= n + persistAhead - 1, "$label: restored $restored over-counts")
                    assertTrue(restored <= LIMIT, "$label: restored $restored above the limit")
                    repeat(LIMIT - restored) { assertTrue(second.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess, "$label: grant ${it + 1} after the restore refused") }
                    assertIs<HttpFailure.BudgetExhausted>(second.acquire(1, Lane.Tracker, Duration.ZERO).exceptionOrNull(), "$label: the limit was not enforced")
                }
            }
        }
    }

    @Test
    fun `a clean flush before the second budget restores exactly n`() = runTest {
        for (persistAhead in persistAheads) {
            for (n in listOf(1, 7, 25)) {
                val clock = virtualClock()
                val store = FakeBudgetStore()
                budgetOn(clock, dayWindow(LIMIT)) { this.store = store; this.persistAhead = persistAhead }.use { first ->
                    repeat(n) { first.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
                    assertEquals(Result.success(Unit), first.flush())
                }
                budgetOn(clock, dayWindow(LIMIT)) { this.store = store; this.persistAhead = persistAhead }.use { second ->
                    assertEquals(n, second.utilisation().windows.single().used, "persistAhead=$persistAhead, n=$n")
                }
            }
        }
    }

    @Test
    fun `a crash between flush and further grants never under-counts`() = runTest {
        for (persistAhead in persistAheads) {
            for (n in listOf(1, 4, 9, 12)) {
                val clock = virtualClock()
                val store = FakeBudgetStore()
                budgetOn(clock, dayWindow(LIMIT)) { this.store = store; this.persistAhead = persistAhead }.use { first ->
                    repeat(n) { first.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
                    assertEquals(Result.success(Unit), first.flush())
                    repeat(n) { first.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
                }
                budgetOn(clock, dayWindow(LIMIT)) { this.store = store; this.persistAhead = persistAhead }.use { second ->
                    val restored = second.utilisation().windows.single().used
                    assertTrue(restored >= 2 * n, "persistAhead=$persistAhead, n=$n: restored $restored under-counts")
                    assertTrue(restored <= 2 * n + persistAhead - 1, "persistAhead=$persistAhead, n=$n: restored $restored over-counts")
                }
            }
        }
    }

    @Test
    fun `a flush after every grant was released resets the persisted mark and a crash after it never under-counts`() = runTest {
        val persistAhead = 10
        val n = persistAhead - 1                                                        // fewer grants than one write-ahead block
        val clock = virtualClock()
        val store = FakeBudgetStore()
        budgetOn(clock, dayWindow(LIMIT)) { this.store = store; this.persistAhead = persistAhead }.use { first ->
            val permit = first.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()
            assertEquals(listOf(persistAhead), store.saves.map { it.windows.single().used })
            permit.release()
            assertEquals(Result.success(Unit), first.flush())
            assertEquals(0, store.snapshot.windows.single().used, "the flush did not write the count of zero")
            repeat(n) { first.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow() }
            assertEquals(listOf(persistAhead, 0, persistAhead), store.saves.map { it.windows.single().used }, "the first grant after the flush was not written ahead")
        }                                                                               // dropped without flush(): the crash
        budgetOn(clock, dayWindow(LIMIT)) { this.store = store; this.persistAhead = persistAhead }.use { second ->
            val restored = second.utilisation().windows.single().used
            assertTrue(restored >= n, "restored $restored under-counts the $n grants after the flush")
            assertTrue(restored <= n + persistAhead - 1, "restored $restored over-counts")
        }
    }
}
