package com.spartanlabs.testing.support.webtools.http

import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import okio.BufferedSink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

/**
 * A body that never ends: `contentLength` is `-1` and `writeTo` writes chunk-framed 1 KiB chunks (`400\r\n`, 1024 bytes, `\r\n`, flush)
 * until the write throws because the client closed the socket. [started] is counted down after the first flush and [stopped] in a
 * `finally`, so a test can wait for the server to be writing and then for it to have noticed the close. Served with [response], which removes
 * the builder's `Content-Length` and declares `Transfer-Encoding: chunked`.
 */
internal class EndlessBody : MockResponseBody {
    /** Counted down after the first flushed chunk. */
    val started = CountDownLatch(1)

    /** Counted down when `writeTo` ends, however it ends. */
    val stopped = CountDownLatch(1)

    /** The bytes of chunk payload written so far. */
    val written = AtomicLong()

    override val contentLength: Long get() = -1

    override fun writeTo(sink: BufferedSink) {
        val chunk = ByteArray(1024)
        try {
            while (true) {
                sink.writeUtf8("400\r\n")
                sink.write(chunk)
                sink.writeUtf8("\r\n")
                sink.flush()
                written.addAndGet(chunk.size.toLong())
                started.countDown()
            }
        } finally {
            stopped.countDown()
        }
    }

    /**
     * The response that serves this body, chunk-framed; [configure] may add a throttle or a delay.
     *
     * @return the response.
     */
    fun response(configure: MockResponse.Builder.() -> Unit = {}): MockResponse =
        MockResponse.Builder().body(this).removeHeader("Content-Length").addHeader("Transfer-Encoding", "chunked").apply(configure).build()
}
