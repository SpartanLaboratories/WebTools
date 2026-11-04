package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.webtools.http.BudgetSnapshot
import com.spartanlabs.webtools.http.BudgetStore
import com.spartanlabs.webtools.http.WindowSnapshot
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `FakeBudgetStore`: a preloaded `initial` is returned by `load`; `save` replaces it and appears in `saves`; `loadCount` and
 * `saveAttemptCount` count every call, whatever it returned; `failLoads`/`failSaves` return the cause (or throw it with `thrown = true`) for the next
 * `times` calls and then heal, `heal()` clears them; a `times` below 1 is refused with `IllegalArgumentException` and `times = 1` fails exactly once; a
 * failed save stores nothing; `loadDelay` and `saveDelay` are virtual under `runTest`, and a call cancelled in its delay changes nothing; thread-safe under
 * concurrent use. No opt-in anywhere: the test is a consumer of the fixture.
 */
class FakeBudgetStoreComponentTest {

    private fun snapshot(used: Int): BudgetSnapshot = BudgetSnapshot(1_000L, listOf(WindowSnapshot("day", 0L, used)), 0L)

    @Test
    fun `a preloaded initial snapshot is what load returns and Empty is the default`() = runTest {
        val initial = snapshot(3)
        val store: BudgetStore = FakeBudgetStore(initial)
        assertSame(initial, store.load().getOrThrow())
        assertSame(BudgetSnapshot.Empty, FakeBudgetStore().load().getOrThrow())
    }

    @Test
    fun `save replaces the snapshot and appears in saves`() = runTest {
        val store = FakeBudgetStore()
        val first = snapshot(1)
        val second = snapshot(2)
        assertEquals(Result.success(Unit), store.save(first))
        assertSame(first, store.snapshot)
        assertSame(first, store.load().getOrThrow())
        store.save(second)
        assertSame(second, store.snapshot)
        assertEquals(listOf(first, second), store.saves)
    }

    @Test
    fun `loadCount and saveAttemptCount count every call whatever it returned`() = runTest {
        val store = FakeBudgetStore()
        store.failLoads(IllegalStateException("down"), times = 1)
        store.failSaves(IllegalStateException("down"), times = 1)
        assertTrue(store.load().isFailure)
        assertTrue(store.load().isSuccess)
        assertTrue(store.save(snapshot(1)).isFailure)
        assertTrue(store.save(snapshot(2)).isSuccess)
        assertEquals(2, store.loadCount)
        assertEquals(2, store.saveAttemptCount)
        assertEquals(listOf(snapshot(2)), store.saves, "a failed save was recorded")
    }

    @Test
    fun `failLoads returns the cause for the next times calls and then heals`() = runTest {
        val store = FakeBudgetStore(snapshot(5))
        val cause = IllegalStateException("down")
        store.failLoads(cause, times = 2)
        assertSame(cause, store.load().exceptionOrNull())
        assertSame(cause, store.load().exceptionOrNull())
        assertSame(store.snapshot, store.load().getOrThrow())
    }

    @Test
    fun `failLoads and failSaves throw the cause when thrown is true`() = runTest {
        val store = FakeBudgetStore()
        val cause = IllegalStateException("thrown")
        store.failLoads(cause, thrown = true, times = 1)
        store.failSaves(cause, thrown = true, times = 1)
        assertSame(cause, assertFailsWith<IllegalStateException> { store.load() })
        assertSame(cause, assertFailsWith<IllegalStateException> { store.save(snapshot(1)) })
        assertTrue(store.load().isSuccess)
        assertTrue(store.save(snapshot(1)).isSuccess)
    }

    @Test
    fun `a times below 1 is refused and times 1 fails exactly once`() = runTest {
        val store = FakeBudgetStore()
        val cause = IllegalStateException("down")
        assertEquals("times must be at least 1", assertFailsWith<IllegalArgumentException> { store.failLoads(cause, times = 0) }.message)
        assertEquals("times must be at least 1", assertFailsWith<IllegalArgumentException> { store.failSaves(cause, times = 0) }.message)
        assertFailsWith<IllegalArgumentException> { store.failLoads(cause, times = -1) }
        assertFailsWith<IllegalArgumentException> { store.failSaves(cause, times = -1) }
        assertTrue(store.load().isSuccess, "a refused script armed a load failure")
        assertTrue(store.save(snapshot(1)).isSuccess, "a refused script armed a save failure")

        store.failLoads(cause, times = 1)
        store.failSaves(cause, times = 1)
        assertSame(cause, store.load().exceptionOrNull())
        assertSame(cause, store.save(snapshot(2)).exceptionOrNull())
        assertTrue(store.load().isSuccess, "times = 1 failed a second load")
        assertTrue(store.save(snapshot(3)).isSuccess, "times = 1 failed a second save")
        assertEquals(listOf(snapshot(1), snapshot(3)), store.saves)
    }

    @Test
    fun `a failure with the default times keeps failing until heal`() = runTest {
        val store = FakeBudgetStore()
        val cause = IllegalStateException("down")
        store.failLoads(cause)
        store.failSaves(cause)
        repeat(5) {
            assertSame(cause, store.load().exceptionOrNull())
            assertSame(cause, store.save(snapshot(it)).exceptionOrNull())
        }
        store.heal()
        assertTrue(store.load().isSuccess)
        assertTrue(store.save(snapshot(9)).isSuccess)
        assertEquals(listOf(snapshot(9)), store.saves)
    }

    @Test
    fun `a failed save stores nothing`() = runTest {
        val initial = snapshot(1)
        val store = FakeBudgetStore(initial)
        store.failSaves(IllegalStateException("down"), times = 1)
        store.save(snapshot(2))
        assertSame(initial, store.snapshot)
        assertEquals(emptyList<BudgetSnapshot>(), store.saves)
    }

    @Test
    fun `loadDelay and saveDelay are virtual and a call cancelled in its delay changes nothing`() = runTest {
        val store = FakeBudgetStore()
        store.loadDelay = 5.seconds
        store.saveDelay = 5.seconds
        var loaded: BudgetSnapshot? = null
        var saved = false
        val loading = launch { loaded = store.load().getOrThrow() }
        val saving = launch { store.save(snapshot(1)); saved = true }
        testScheduler.advanceTimeBy(4.seconds)
        testScheduler.runCurrent()
        assertNull(loaded)
        assertFalse(saved)
        testScheduler.advanceTimeBy(1.seconds)
        testScheduler.runCurrent()
        assertSame(BudgetSnapshot.Empty, loaded)
        assertTrue(saved)
        assertTrue(loading.isCompleted && saving.isCompleted)

        val cancelled = launch { store.save(snapshot(2)) }
        testScheduler.advanceTimeBy(2.seconds)
        testScheduler.runCurrent()
        cancelled.cancel()
        testScheduler.advanceTimeBy(10.seconds)
        testScheduler.runCurrent()
        assertTrue(cancelled.isCancelled)
        assertEquals(listOf(snapshot(1)), store.saves, "a save cancelled in its delay was stored")
        assertEquals(2, store.saveAttemptCount, "the cancelled save was not counted as an attempt")
    }

    @Test
    fun `is thread-safe under concurrent use`() = runTest {
        val store = FakeBudgetStore()
        val saved = (1..50).map { snapshot(it) }
        val loaded = withContext(Dispatchers.Default) {
            val savers = saved.map { s -> launch { store.save(s).getOrThrow() } }
            val loaders = (1..50).map { async { store.load().getOrThrow() } }
            savers.forEach { it.join() }
            loaders.awaitAll()
        }
        loaded.forEach { assertTrue(it === BudgetSnapshot.Empty || it in saved, "a load returned a snapshot that was never saved: $it") }
        assertEquals(50, store.saveAttemptCount)
        assertEquals(50, store.loadCount)
        assertEquals(50, store.saves.size)
        assertTrue(store.snapshot in saved)
    }

    @Test
    fun `toString prints the counts`() = runTest {
        val store = FakeBudgetStore()
        assertEquals("FakeBudgetStore(loads=0, saves=0)", store.toString())
        store.load()
        store.save(snapshot(1))
        assertEquals("FakeBudgetStore(loads=1, saves=1)", store.toString())
        assertEquals(Duration.ZERO, store.loadDelay)
    }
}
