package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.MINUTE_MS
import com.spartanlabs.testing.support.webtools.http.minute
import com.spartanlabs.webtools.http.MAX_CELLS_PER_WINDOW
import com.spartanlabs.webtools.http.WindowLedger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 4a - `WindowLedger.advance`: forward moves prune cells below the previous index but never one holding planned units; a
 * backward step across a boundary copies the sum of the cells above into the current cell, capped at the limit, once per decrease,
 * leaves the higher cells in place and returns `true` only when something was copied; a step within one index copies nothing; a
 * step back of a year; the cap of 16 cells drops the farthest above the current index first and spares planned cells; a guard
 * neighbour cell enters the sum as ordinary content; a floor above the limit leaves no room.
 */
class CellFoldDeterministicTest {

    private val yearOfMinutes = 365L * 24 * 60

    private fun window(limit: Int = 10): WindowLedger = WindowLedger(minute(limit), 0L, emptyMap())

    @Test
    fun `forward moves prune cells below the previous index but never a planned one`() {
        val w = window()
        assertFalse(w.advance(0))
        w.cellOrCreate(0).granted = 3
        w.cellOrCreate(1).planned = 1
        w.cellOrCreate(2).granted = 1
        assertFalse(w.advance(3 * MINUTE_MS))
        assertNull(w.cell(0))
        assertNotNull(w.cell(1))
        assertNotNull(w.cell(2))
        assertEquals(listOf(1L, 2L), w.cellIndices())
        assertFalse(w.advance(4 * MINUTE_MS))
        assertNull(w.cell(2))
        assertNotNull(w.cell(1))
    }

    @Test
    fun `a backward step across a boundary copies the cells above, once per decrease, and keeps them`() {
        val w = window()
        w.advance(0)
        w.cellOrCreate(0).granted = 2
        w.advance(MINUTE_MS)                                                            // cell 0 is k - 1: kept
        w.cellOrCreate(1).granted = 4
        assertTrue(w.advance(0))
        assertEquals(6, w.cell(0)!!.effective)
        assertEquals(4, w.cell(0)!!.base)
        assertEquals(2, w.cell(0)!!.granted)
        assertEquals(4, w.cell(1)!!.effective)
        assertFalse(w.advance(0))
        assertFalse(w.advance(30_000))
        assertEquals(6, w.cell(0)!!.effective)
        assertTrue(w.advance(-MINUTE_MS))
        assertEquals(10, w.cell(-1)!!.effective)                                        // 6 + 4, capped at the limit
        assertEquals(6, w.cell(0)!!.effective)
        assertEquals(4, w.cell(1)!!.effective)
    }

    @Test
    fun `a cell two periods back was pruned, so the copy lands in a fresh cell`() {
        val w = window()
        w.advance(0)
        w.cellOrCreate(0).granted = 2
        w.advance(MINUTE_MS)
        w.cellOrCreate(1).granted = 4
        w.advance(2 * MINUTE_MS)                                                        // prunes cell 0
        assertNull(w.cell(0))
        w.cellOrCreate(2).granted = 3
        assertTrue(w.advance(0))
        assertEquals(7, w.cell(0)!!.effective)
        assertEquals(7, w.cell(0)!!.base)
        assertEquals(0, w.cell(0)!!.granted)
    }

    @Test
    fun `the copy is capped at the limit and returns false when there is no room or nothing above`() {
        val w = window()
        w.advance(0)
        w.cellOrCreate(0).granted = 8
        w.advance(MINUTE_MS)
        w.cellOrCreate(1).granted = 9
        assertTrue(w.advance(0))
        assertEquals(10, w.cell(0)!!.effective)
        assertEquals(2, w.cell(0)!!.base)
        val full = window()
        full.advance(0)
        full.cellOrCreate(0).granted = 10
        full.advance(MINUTE_MS)
        full.cellOrCreate(1).granted = 1
        assertFalse(full.advance(0))
        assertEquals(10, full.cell(0)!!.effective)
        assertEquals(0, full.cell(0)!!.base)
        val empty = window()
        empty.advance(2 * MINUTE_MS)
        assertFalse(empty.advance(0))
        assertNull(empty.cell(0))
    }

    @Test
    fun `a backward step within one index copies nothing`() {
        val w = window()
        w.advance(MINUTE_MS + 30_000)
        w.cellOrCreate(1).granted = 4
        w.cellOrCreate(2).granted = 2
        assertFalse(w.advance(MINUTE_MS + 10_000))
        assertEquals(4, w.cell(1)!!.effective)
        assertEquals(0, w.cell(1)!!.base)
    }

    @Test
    fun `a step back of a year copies the whole year's content, capped`() {
        val w = window()
        w.advance(yearOfMinutes * MINUTE_MS)
        w.cellOrCreate(yearOfMinutes).granted = 6
        w.cellOrCreate(yearOfMinutes + 1).granted = 6
        assertTrue(w.advance(0))
        assertEquals(10, w.cell(0)!!.effective)
        assertEquals(6, w.cell(yearOfMinutes)!!.effective)
        assertEquals(6, w.cell(yearOfMinutes + 1)!!.effective)
    }

    @Test
    fun `the cell cap drops the farthest future cell first and spares planned cells`() {
        val w = window()
        for (i in 0L until 20L) w.cellOrCreate(i).granted = 1
        w.cell(19)!!.planned = 1
        w.cell(18)!!.planned = 1
        assertFalse(w.advance(0))
        assertEquals(MAX_CELLS_PER_WINDOW, w.cellIndices().size)
        assertEquals((0L..13L).toList() + listOf(18L, 19L), w.cellIndices())
        assertEquals(16, MAX_CELLS_PER_WINDOW)
    }

    @Test
    fun `a guard neighbour cell enters the sum as ordinary content`() {
        val guarded = WindowLedger(minute(10), 1_000L, emptyMap())
        guarded.advance(0)
        guarded.cellOrCreate(0).granted = 1
        guarded.advance(MINUTE_MS)
        guarded.chargeIndices(2 * MINUTE_MS - 500).forEach { guarded.cellOrCreate(it).granted += 1 }   // charges cells 1 and 2
        assertEquals(1, guarded.cell(2)!!.effective)
        assertTrue(guarded.advance(0))
        assertEquals(3, guarded.cell(0)!!.effective)
    }

    @Test
    fun `a floor above the limit leaves no room so the fold adds nothing`() {
        val w = window()
        w.advance(0)
        w.cellOrCreate(0).floor = 12
        w.advance(MINUTE_MS)
        w.cellOrCreate(1).granted = 3
        assertFalse(w.advance(0))
        assertEquals(12, w.cell(0)!!.effective)                                         // unchanged: the floor already exceeds the limit
        assertEquals(3, w.cell(1)!!.effective)
    }
}
