package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.BudgetSnapshot
import com.spartanlabs.webtools.http.InMemoryBudgetStore
import com.spartanlabs.webtools.http.WindowSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - `InMemoryBudgetStore`: `load()` on a new store is `Empty`; `save` then `load` returns the same snapshot instance; a
 * later save replaces it; 50 concurrent saves and loads under `Dispatchers.Default` end with one of the saved snapshots (no torn
 * state); `toString()`.
 */
class InMemoryBudgetStoreComponentTest {

    private fun snapshot(used: Int): BudgetSnapshot = BudgetSnapshot(1_000L, listOf(WindowSnapshot("day", 0L, used)), 0L)

    @Test
    fun `a new store loads Empty`() = runTest {
        assertSame(BudgetSnapshot.Empty, InMemoryBudgetStore().load().getOrThrow())
    }

    @Test
    fun `save then load returns the same instance and a later save replaces it`() = runTest {
        val store = InMemoryBudgetStore()
        val first = snapshot(1)
        assertEquals(Result.success(Unit), store.save(first))
        assertSame(first, store.load().getOrThrow())
        val second = snapshot(2)
        store.save(second)
        assertSame(second, store.load().getOrThrow())
    }

    @Test
    fun `concurrent saves and loads end with one of the saved snapshots`() = runTest {
        val store = InMemoryBudgetStore()
        val saved = (1..50).map { snapshot(it) }
        val loaded = withContext(Dispatchers.Default) {
            val savers = saved.map { s -> launch { store.save(s).getOrThrow() } }
            val loaders = (1..50).map { async { store.load().getOrThrow() } }
            savers.forEach { it.join() }
            loaders.awaitAll()
        }
        loaded.forEach { assertTrue(it === BudgetSnapshot.Empty || it in saved, "a load returned a snapshot that was never saved: $it") }
        assertTrue(store.load().getOrThrow() in saved)
    }

    @Test
    fun `toString names the class`() {
        assertEquals("InMemoryBudgetStore", InMemoryBudgetStore().toString())
    }
}
