package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.FakeCall
import com.spartanlabs.testing.support.webtools.http.fakeRequest
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.FailureEvent
import com.spartanlabs.webtools.http.HopOutcome
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.MappedFailure
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.awaitHop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the bridge against a hand-written `Call`: a delivered response resumes with the handler's outcome and is closed; a delivered
 * failure resumes with the failure handler's outcome; the cancellation handler is registered before `enqueue` (a fake that cancels the
 * coroutine inside `enqueue` sees exactly one `cancel()`); cancelling the caller cancels the call once, and a later `onResponse` closes the
 * response, skips the handler and resumes harmlessly; a handler that throws an `Error` resumes the caller with `Io`, closes the response and
 * lets the `Error` reach the deliverer; a returned `Timeout` outcome has `cause == null` and is the mapper's own instance under `-ea`.
 */
class CallBridgeComponentTest {

    private class TestError : Error("a test error")

    /** A body whose source records whether it was closed. */
    private class ClosableBody : ResponseBody() {
        val closed = AtomicBoolean()
        private val source: BufferedSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = -1
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() { closed.set(true) }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = 0
        override fun source(): BufferedSource = source
    }

    private fun response(body: ClosableBody): Response =
        Response.Builder().request(fakeRequest()).protocol(Protocol.HTTP_1_1).code(200).message("").body(body).build()

    private val done = HopOutcome.Done(HttpResponse(200, url("http://fake.test/"), HttpHeaders(), emptyList(), ByteArray(0)))
    private fun failed(failure: TransportFailure) = HopOutcome.Failed(MappedFailure(failure, FailureEvent.Other(null, null)))

    @Test
    fun `a delivered response resumes with the handler's outcome and the response is closed`() = runBlocking {
        val call = FakeCall()
        val body = ClosableBody()
        val outcome = async { awaitHop(call, { done }, { failed(TransportFailure.Io()) }) }
        yield()
        assertEquals(listOf("enqueue"), call.events)
        call.deliverResponse(response(body))
        assertSame(done, withTimeout(5.seconds) { outcome.await() })
        assertTrue(body.closed.get())
    }

    @Test
    fun `a delivered failure resumes with the failure handler's outcome`() = runBlocking {
        val call = FakeCall()
        val expected = failed(TransportFailure.ConnectFailed())
        val outcome = async { awaitHop(call, { done }, { e -> assertEquals("refused", e.message); expected }) }
        yield()
        call.deliverFailure(IOException("refused"))
        assertSame(expected, withTimeout(5.seconds) { outcome.await() })
    }

    @Test
    fun `the cancellation handler is registered before enqueue`() = runBlocking {
        lateinit var job: Job
        val call = FakeCall(onEnqueue = { job.cancel() })
        job = launch { awaitHop(call, { done }, { failed(TransportFailure.Io()) }) }
        withTimeout(5.seconds) { job.join() }
        assertTrue(job.isCancelled)
        assertEquals(listOf("enqueue", "cancel"), call.events)
        assertEquals(1, call.cancelCount)
    }

    @Test
    fun `cancelling the caller cancels the call once and a late response is closed without running the handler`() = runBlocking {
        val call = FakeCall()
        val handled = AtomicBoolean()
        var thrown: Throwable? = null
        val job = launch {
            try {
                awaitHop(call, { handled.set(true); done }, { failed(TransportFailure.Io()) })
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        yield()
        job.cancel()
        withTimeout(5.seconds) { job.join() }
        assertIs<CancellationException>(thrown)
        assertEquals(1, call.cancelCount)
        val body = ClosableBody()
        call.deliverResponse(response(body))
        assertFalse(handled.get())
        assertTrue(body.closed.get())
    }

    @Test
    fun `a handler that throws an Error resumes the caller with Io closes the response and rethrows to the deliverer`() = runBlocking {
        val call = FakeCall()
        val body = ClosableBody()
        val outcome = async { awaitHop(call, { throw TestError() }, { failed(TransportFailure.Io()) }) }
        yield()
        assertFailsWith<TestError> { call.deliverResponse(response(body)) }
        val failed = assertIs<HopOutcome.Failed>(withTimeout(5.seconds) { outcome.await() })
        assertIs<TransportFailure.Io>(failed.mapped.failure)
        assertTrue(body.closed.get())
    }

    @Test
    fun `a returned Timeout outcome is the mapper's own instance with no cause under -ea`() = runBlocking {
        assertTrue(OkHttpTransport::class.java.desiredAssertionStatus(), "the test JVM runs without -ea; the guard needs assertions on")
        val timeout = TransportFailure.Timeout()
        val expected = failed(timeout)
        val call = FakeCall()
        val outcome = async { awaitHop(call, { done }, { expected }) }
        yield()
        call.deliverFailure(IOException("timeout"))
        val resumed = assertIs<HopOutcome.Failed>(withTimeout(5.seconds) { outcome.await() })
        assertSame(timeout, resumed.mapped.failure)
        assertNull(resumed.mapped.failure.cause)
    }
}
