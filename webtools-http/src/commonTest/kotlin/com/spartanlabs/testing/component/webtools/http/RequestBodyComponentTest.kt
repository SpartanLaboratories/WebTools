package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.RequestBody
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Level 2 - `RequestBody`: bytes copied in and out, `size`, `contentType` null, valid and invalid
 * (the message holds the index, not the text), identity equality and `toString()`.
 */
class RequestBodyComponentTest {

    @Test
    fun `bytes are copied in and read back as a fresh copy`() {
        val source = byteArrayOf(1, 2, 3)
        val body = RequestBody(source, null)
        source[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), body.bytes())
        body.bytes()[1] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), body.bytes())
        assertEquals(3, body.size)
        assertEquals(0, RequestBody(ByteArray(0), null).size)
    }

    @Test
    fun `the content type is null or a media type`() {
        assertNull(RequestBody(ByteArray(0), null).contentType)
        assertEquals("application/json; charset=utf-8", RequestBody(ByteArray(0), "application/json; charset=utf-8").contentType)
        val exception = assertFailsWith<IllegalArgumentException> { RequestBody(ByteArray(0), "text/plain SECRET") }
        assertEquals("Content type is not an RFC 9110 media type (first offending character at index 11)", exception.message)
        assertFalse(exception.message!!.contains("SECRET"))
    }

    @Test
    fun `equality is identity`() {
        val a = RequestBody(byteArrayOf(1), "text/plain")
        val b = RequestBody(byteArrayOf(1), "text/plain")
        assertNotEquals(a, b)
        assertEquals(a, a)
    }

    @Test
    fun `toString shows the size and whether a content type is set`() {
        assertEquals("RequestBody(size=12, hasContentType=true)", RequestBody(ByteArray(12), "text/plain").toString())
        assertEquals("RequestBody(size=0, hasContentType=false)", RequestBody(ByteArray(0), null).toString())
    }
}
