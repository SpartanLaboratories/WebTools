package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.readCappedBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 2 - the capped read over an okio source: a declared length above the cap fails without a read; exactly the cap succeeds;
 * cap + 1 fails with `declared = null`, or with the declared length when one at or below the cap was given; an empty body succeeds;
 * a source that throws returns its exception; a source that throws only after the cap was exceeded still gives `BodyTooLarge`; a
 * 64 MiB source under a 1 KiB cap is read for at most the cap plus one segment.
 */
class CappedBodyComponentTest {

    /** A source of [total] zero bytes that counts what was read and throws [failAfter] bytes in, when set. */
    private class CountingSource(private val total: Long, private val failAfter: Long? = null) : Source {
        var read = 0L
        override fun read(sink: Buffer, byteCount: Long): Long {
            if (failAfter != null && read >= failAfter) throw IOException("source broke")
            if (read >= total) return -1
            val n = minOf(byteCount, total - read, failAfter?.let { it - read } ?: byteCount)
            sink.write(ByteArray(n.toInt()))
            read += n
            return n
        }
        override fun timeout(): Timeout = Timeout.NONE
        override fun close() {}
    }

    private fun bytes(n: Int): Buffer = Buffer().write(ByteArray(n) { (it % 251).toByte() })

    @Test
    fun `a declared length above the cap fails without a read`() {
        val source = CountingSource(10)
        val failure = assertIs<TransportFailure.BodyTooLarge>(readCappedBody(source.buffer(), 5_000_000L, 1024).exceptionOrNull())
        assertEquals(1024L, failure.limit)
        assertEquals(5_000_000L, failure.declared)
        assertEquals(0L, source.read)
    }

    @Test
    fun `exactly the cap succeeds with the cap's bytes`() {
        val expected = bytes(1024).readByteArray()
        assertContentEquals(expected, readCappedBody(Buffer().write(expected), null, 1024).getOrThrow())
        assertContentEquals(expected, readCappedBody(Buffer().write(expected), 1024, 1024).getOrThrow())
    }

    @Test
    fun `cap plus one fails with no declared length or with the declared one`() {
        val undeclared = assertIs<TransportFailure.BodyTooLarge>(readCappedBody(bytes(1025), null, 1024).exceptionOrNull())
        assertEquals(1024L, undeclared.limit)
        assertNull(undeclared.declared)
        val lying = assertIs<TransportFailure.BodyTooLarge>(readCappedBody(bytes(1025), 1000, 1024).exceptionOrNull())
        assertEquals(1000L, lying.declared)
    }

    @Test
    fun `an empty body succeeds`() {
        assertEquals(0, readCappedBody(Buffer(), null, 1024).getOrThrow().size)
        assertEquals(0, readCappedBody(Buffer(), 0, 1024).getOrThrow().size)
    }

    @Test
    fun `a source that throws returns its exception and not BodyTooLarge`() {
        val broken = CountingSource(100, failAfter = 10)
        val failure = readCappedBody(broken.buffer(), null, 1024).exceptionOrNull()
        assertIs<IOException>(failure)
        assertEquals("source broke", failure.message)
    }

    @Test
    fun `a source that throws after the cap was exceeded still gives BodyTooLarge`() {
        val source = CountingSource(10_000, failAfter = 1025)
        assertIs<TransportFailure.BodyTooLarge>(readCappedBody(source.buffer(), null, 1024).exceptionOrNull())
    }

    @Test
    fun `a 64 MiB source under a 1 KiB cap is read for at most the cap plus one segment`() {
        val source = CountingSource(64L * 1024 * 1024)
        assertIs<TransportFailure.BodyTooLarge>(readCappedBody(source.buffer(), null, 1024).exceptionOrNull())
        assertTrue(source.read <= 1024 + 8192, "read ${source.read} bytes")   // the cap plus one okio segment
    }

    @Test
    fun `the limit is the failure's limit`() {
        val failure = assertIs<TransportFailure.BodyTooLarge>(readCappedBody(bytes(3), null, 2).exceptionOrNull())
        assertEquals(2L, failure.limit)
    }
}
