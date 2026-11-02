package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HIDDEN_STATUS_HEADER
import com.spartanlabs.webtools.http.receiveHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Level 4a - the `receiveHeaders` table: an entry whose name is empty, has a space, starts with `:` or is non-ASCII is dropped and counted;
 * values pass `sanitizeReceivedValue`; order, spelling and duplicates are kept; `Retry-After` passes like any entry; the private header
 * never surfaces in any case spelling.
 */
class ReceiveHeadersDeterministicTest {

    @Test
    fun `a name that is not a token is dropped and counted`() {
        val received = receiveHeaders(listOf("" to "a", "X-Ok" to "1", "a b" to "c", ":authority" to "h", "café" to "x", "X-Also" to "2"))
        assertEquals(4, received.dropped)
        assertEquals(listOf("X-Ok" to "1", "X-Also" to "2"), received.headers.entries().map { it.name to it.value })
    }

    @Test
    fun `values are sanitised`() {
        val received = receiveHeaders(listOf("X-A" to "\r\n a\u0000b\r\n", "X-B" to "\tplain  "))
        assertEquals(0, received.dropped)
        assertEquals(listOf("a b", "plain"), received.headers.entries().map { it.value })
    }

    @Test
    fun `order spelling and duplicates are kept and Retry-After passes through`() {
        val pairs = listOf("set-cookie" to "a=1", "Retry-After" to "0", "Set-Cookie" to "b=2", "X-Dup" to "1", "X-Dup" to "1")
        val received = receiveHeaders(pairs)
        assertEquals(pairs, received.headers.entries().map { it.name to it.value })
        assertEquals("0", received.headers["Retry-After"])
    }

    @Test
    fun `the private header never surfaces in any case spelling`() {
        val received = receiveHeaders(listOf(HIDDEN_STATUS_HEADER to "503", "x-webtools-hidden-status" to "407", "X-WEBTOOLS-HIDDEN-STATUS" to "421", "X-A" to "1"))
        assertEquals(0, received.dropped)
        assertEquals(listOf("X-A" to "1"), received.headers.entries().map { it.name to it.value })
        assertFalse(HIDDEN_STATUS_HEADER in received.headers)
    }
}
