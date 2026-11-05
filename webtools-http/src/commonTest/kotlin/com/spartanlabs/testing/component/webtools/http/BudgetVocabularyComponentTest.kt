package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.PauseSource
import com.spartanlabs.webtools.http.StoreOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * Level 2 - `PauseSource` has its four constants and `StoreOperation` its two, each named as spelled; equality is by name, a
 * constant is `===` to itself, the hash agrees with the name's and `toString()` is the name; neither vocabulary equals the other,
 * `ExhaustedReason` or a `String`; a `when` over each keeps an `else`.
 */
class BudgetVocabularyComponentTest {

    private val sources: List<PauseSource> = listOf(PauseSource.RetryAfter, PauseSource.ResetIn, PauseSource.Boundary, PauseSource.Snapshot)
    private val operations: List<StoreOperation> = listOf(StoreOperation.Load, StoreOperation.Save)

    @Test
    fun `the constants have their names and are distinct`() {
        assertEquals(listOf("RetryAfter", "ResetIn", "Boundary", "Snapshot"), sources.map { it.name })
        assertEquals(listOf("Load", "Save"), operations.map { it.name })
        assertEquals(4, sources.toSet().size)
        assertEquals(2, operations.toSet().size)
    }

    @Test
    fun `equality is by name, identity holds for a constant, hash and toString agree`() {
        assertSame(PauseSource.Boundary, PauseSource.Boundary)
        assertEquals(PauseSource.Boundary, PauseSource.Boundary)
        assertEquals("Boundary".hashCode(), PauseSource.Boundary.hashCode())
        assertEquals("Boundary", PauseSource.Boundary.toString())
        assertNotEquals(PauseSource.Boundary, PauseSource.Snapshot)
        assertSame(StoreOperation.Save, StoreOperation.Save)
        assertEquals("Save".hashCode(), StoreOperation.Save.hashCode())
        assertEquals("Save", StoreOperation.Save.toString())
        assertNotEquals(StoreOperation.Save, StoreOperation.Load)
    }

    @Test
    fun `neither vocabulary equals the other, an ExhaustedReason or a String`() {
        assertNotEquals<Any>(PauseSource.RetryAfter, StoreOperation.Load)
        assertNotEquals<Any>(PauseSource.RetryAfter, ExhaustedReason.Window)
        assertNotEquals<Any>(StoreOperation.Load, ExhaustedReason.Window)
        assertNotEquals<Any>(PauseSource.RetryAfter, "RetryAfter")
        assertNotEquals<Any>(StoreOperation.Load, "Load")
    }

    @Test
    fun `a when over each vocabulary keeps an else`() {
        val described = sources.map { source ->
            when (source) {
                PauseSource.RetryAfter -> "server"
                PauseSource.Snapshot -> "store"
                else -> "other"
            }
        }
        assertEquals(listOf("server", "other", "other", "store"), described)
        val saved = operations.map { operation ->
            when (operation) {
                StoreOperation.Save -> true
                else -> false
            }
        }
        assertEquals(listOf(false, true), saved)
    }
}
