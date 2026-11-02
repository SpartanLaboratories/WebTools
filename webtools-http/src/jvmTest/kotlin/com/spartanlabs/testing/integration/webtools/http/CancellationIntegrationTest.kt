package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.EndlessBody
import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Level 3 - cancellation and `close()` on the wire: cancelling the caller during an endless throttled body stops the read at once (the
 * server's writer notices the closed socket, the join returns within a second, the server closes promptly); `close()` during a slow drip ends
 * the call with `Closed("transport")` and every later call is `Closed`; `close()` with queued calls ends every one of them with `Closed`, each
 * coroutine resumed once; a coroutine cancelled before `execute` runs throws `CancellationException`.
 */
class CancellationIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
    }

    @AfterTest
    fun stop() = fleet.close()

    private fun transport(configure: OutboundUrlPolicy.Untrusted.Builder.() -> Unit = {}): OkHttpTransport =
        fleet.track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { settings { callTimeout = 60.seconds; readTimeout = 30.seconds }; configure() }))

    @Test
    fun `cancelling the caller stops an endless body at once`() = runBlocking {
        val body = EndlessBody()
        server.enqueue(body.response { throttleBody(1024, 50, TimeUnit.MILLISECONDS) })
        val transport = transport()
        val call = async { transport.execute(fleet.get(server.webtoolsUrl("/"))) }
        assertTrue(withContext(Dispatchers.IO) { body.started.await(5, TimeUnit.SECONDS) }, "the server never started writing")
        call.cancel()
        val mark = TimeSource.Monotonic.markNow()
        call.join()
        assertTrue(mark.elapsedNow() < 1.seconds, "join took ${mark.elapsedNow()}")
        assertTrue(call.isCancelled)
        assertTrue(withContext(Dispatchers.IO) { body.stopped.await(5, TimeUnit.SECONDS) }, "the server's writer never stopped")
        val closing = TimeSource.Monotonic.markNow()
        server.close()
        assertTrue(closing.elapsedNow() < 1.seconds, "server.close() took ${closing.elapsedNow()}")
    }

    @Test
    fun `close during an in-flight call ends it with Closed and every later call is Closed`(): Unit = runBlocking {
        val body = EndlessBody()
        server.enqueue(body.response { throttleBody(1024, 50, TimeUnit.MILLISECONDS) })
        val transport = transport()
        val call = async { transport.execute(fleet.get(server.webtoolsUrl("/"))) }
        assertTrue(withContext(Dispatchers.IO) { body.started.await(5, TimeUnit.SECONDS) })
        transport.close()
        val failure = assertIs<HttpFailure.Closed>(withTimeout(10.seconds) { call.await() }.exceptionOrNull())
        assertEquals("transport", failure.component)
        assertIs<HttpFailure.Closed>(withTimeout(5.seconds) { transport.execute(fleet.get(server.webtoolsUrl("/"))) }.exceptionOrNull())
    }

    @Test
    fun `close with queued calls ends all of them with Closed each resumed once`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse.Builder().headersDelay(20, TimeUnit.SECONDS).body("late").build()) }
        val transport = transport { settings { maxConcurrentRequests = 1; maxConcurrentRequestsPerHost = 1 } }
        val resumed = AtomicInteger()
        val calls = (1..3).map { async { transport.execute(fleet.get(server.webtoolsUrl("/$it"))).also { resumed.incrementAndGet() } } }
        // Lets the three coroutines reach the dispatcher before close(): with one permit, the first call holds it (its headers are 20 s away)
        // and the other two sit queued, so close() has to end queued calls, not calls that never started.
        delay(500)
        transport.close()
        val results: List<Result<HttpResponse>> = withTimeout(10.seconds) { calls.awaitAll() }
        results.forEach { result -> assertEquals("transport", assertIs<HttpFailure.Closed>(result.exceptionOrNull()).component) }
        assertEquals(3, resumed.get())
    }

    @Test
    fun `a coroutine cancelled before execute runs throws CancellationException`() = runBlocking {
        val transport = transport()
        val call = async { transport.execute(fleet.get(server.webtoolsUrl("/"))) }
        call.cancel()
        assertFailsWith<CancellationException> { call.await() }
        assertEquals(0, server.requestCount)
        launch { }.join()
    }
}
