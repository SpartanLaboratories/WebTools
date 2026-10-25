package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.UpstreamFailure
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Level 2 - `UpstreamError`: headers retained, the body copied in and out, `bodySize`, and no canary
 * from a header value or the body in `message`, `toString()` or `cause`.
 */
class UpstreamErrorComponentTest {

    private val canary = "SECRET-CANARY-7f3a"
    private val headers = HttpHeaders { add("X-Token", canary); add("Content-Type", "text/plain") }

    @Test
    fun `headers are retained`() {
        val failure = UpstreamFailure.UpstreamError(502, headers, ByteArray(0))
        assertEquals(headers, failure.headers)
        assertEquals(canary, failure.headers["X-Token"])
    }

    @Test
    fun `the body is copied in and read back as a fresh copy`() {
        val source = byteArrayOf(1, 2, 3)
        val failure = UpstreamFailure.UpstreamError(500, HttpHeaders(), source)
        source[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), failure.body())
        val read = failure.body()
        read[1] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), failure.body())
        assertEquals(3, failure.bodySize)
    }

    @Test
    fun `neither message nor toString nor cause holds a canary`() {
        val failure = UpstreamFailure.UpstreamError(500, headers, canary.encodeToByteArray())
        assertFalse(failure.message!!.contains(canary))
        assertFalse(failure.toString().contains(canary))
        assertNull(failure.cause)
        assertEquals("The server answered with status 500", failure.message)
    }
}
