package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.firstInChain
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Level 4a - `Throwable.firstInChain` with an arbitrary selector (the message of a throwable whose message starts with `hit`): the first
 * match is returned and a chain without one gives `null`; a cycle through the cause chain, the suppressed lists or both gives `null` without
 * looping; the walk stops after 100 throwables (the 100th is found, the 101st is not, and a longer chain is read exactly 100 times); the cause
 * is searched before the suppressed, a cause's subtree before the top's suppressed, and suppressed in order; a selector that throws
 * propagates.
 */
class ThrowableSearchDeterministicTest {

    private val select: (Throwable) -> String? = { it.message?.takeIf { message -> message.startsWith("hit") } }

    private fun chain(depth: Int, bottom: Throwable): Throwable = (1..depth).fold(bottom) { inner, _ -> IOException("wrap", inner) }

    @Test
    fun `the first match is returned and a chain without one gives null`() {
        assertEquals("hit top", IOException("hit top", IOException("hit cause")).firstInChain(select))
        assertEquals("hit cause", IOException("miss", IOException("hit cause")).firstInChain(select))
        assertEquals("hit deep", IOException("miss", IOException("miss", IOException("hit deep"))).firstInChain(select))
        assertNull(IOException("miss", IOException("miss too")).firstInChain(select))
        assertNull(IOException("miss").firstInChain(select))
    }

    @Test
    fun `a cycle in the cause chain or the suppressed lists gives null without looping`() {
        val x = IOException("x")
        val y = IOException("y")
        x.initCause(y)
        y.initCause(x)
        assertNull(x.firstInChain(select))
        val p = IOException("p")
        val q = IOException("q")
        p.addSuppressed(q)
        q.addSuppressed(p)
        assertNull(p.firstInChain(select))
        // A cycle that runs down a cause and back up through a suppressed list.
        val m = IOException("m")
        val n = IOException("n", m)
        m.addSuppressed(n)
        assertNull(m.firstInChain(select))
        // The cycle does not hide a match that sits before it closes.
        val r = IOException("r")
        val s = IOException("hit s", r)
        r.initCause(s)
        assertEquals("hit s", r.firstInChain(select))
    }

    // The two depths straddle the search's bound of 100 throwables (a private constant of the production file).
    @Test
    fun `the walk is bounded to 100 throwables`() {
        assertEquals("hit", chain(99, IOException("hit")).firstInChain(select))      // the 100th throwable
        assertNull(chain(100, IOException("hit")).firstInChain(select))              // the 101st
        var calls = 0
        assertNull(chain(500, IOException("miss")).firstInChain<String> { calls++; null })
        assertEquals(100, calls)
    }

    @Test
    fun `the cause is searched before the suppressed and the suppressed in order`() {
        val causeFirst = IOException("miss", IOException("hit cause")).apply { addSuppressed(IOException("hit suppressed")) }
        assertEquals("hit cause", causeFirst.firstInChain(select))
        val inOrder = IOException("miss").apply { addSuppressed(IOException("hit first")); addSuppressed(IOException("hit second")) }
        assertEquals("hit first", inOrder.firstInChain(select))
        // Depth first: the cause's own suppressed come before the top's suppressed.
        val cause = IOException("miss cause").apply { addSuppressed(IOException("hit under the cause")) }
        val deep = IOException("miss top", cause).apply { addSuppressed(IOException("hit beside the top")) }
        assertEquals("hit under the cause", deep.firstInChain(select))
        val visited = mutableListOf<String>()
        assertNull(deep.firstInChain<String> { visited += it.message!!; null })
        assertEquals(listOf("miss top", "miss cause", "hit under the cause", "hit beside the top"), visited)
    }

    @Test
    fun `a selector that throws propagates`() {
        val onTheFirst = assertFailsWith<IllegalStateException> { IOException("x").firstInChain<String> { throw IllegalStateException("selector broke") } }
        assertEquals("selector broke", onTheFirst.message)
        val onTheSecond = assertFailsWith<IllegalStateException> {
            IOException("miss", IOException("x")).firstInChain<String> { if (it.message == "x") throw IllegalStateException("selector broke later") else null }
        }
        assertEquals("selector broke later", onTheSecond.message)
    }
}
