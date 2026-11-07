package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.ChainFixtures
import com.spartanlabs.webtools.feeds.permanentMoveOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Level 4a - `permanentMoveOf`: the chain table (none, `A-301->B`, `A-308->B-302->C`, `A-302->B-301->C`, `A-301->B-301->C-307->D`,
 * `http` to `https` on one host); a target equal to the requested URL is null, and the answer ignores the final status.
 */
class PermanentMoveDeterministicTest {

    @Test
    fun `the chain table`() {
        assertEquals(6, ChainFixtures.rows.size)
        for (row in ChainFixtures.rows) assertEquals(row.expectedMove, permanentMoveOf(row.requested, row.response), row.name)
    }

    @Test
    fun `a target equal to the requested URL is null, and the answer ignores the final status`() {
        val a = ChainFixtures.a
        val b = ChainFixtures.b
        assertNull(permanentMoveOf(a, ChainFixtures.chain(a, 200, a to 301)), "a 301 that lands on the requested URL")
        assertEquals(b, permanentMoveOf(a, ChainFixtures.chain(a, 200, a to 301, b to 302)), "the run's target is B although the chain ends back at the requested URL")
        assertEquals(b, permanentMoveOf(a, ChainFixtures.chain(b, 304, a to 301)), "a 304 at the end")
        assertEquals(b, permanentMoveOf(a, ChainFixtures.chain(b, 404, a to 301)), "an error at the end")
        assertEquals(b, permanentMoveOf(a, ChainFixtures.chain(b, 200, a to 308)))
        assertNull(permanentMoveOf(a, ChainFixtures.chain(b, 200, a to 307)))
        assertNull(permanentMoveOf(a, ChainFixtures.chain(b, 200, a to 303)))
    }
}
