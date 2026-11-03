package com.spartanlabs.testing.gating.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.enqueue
import com.spartanlabs.webtools.http.testing.toMockResponse
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Level 1 - the loopback fixtures as a consumer uses them, compiled **without any opt-in**: `LoopbackTransports.untrusted("agent")`,
 * `trusted("agent") { listOf(address) }` (the trailing lambda is the resolver), `MockWebServer.webtoolsUrl()`, `Fixture.toMockResponse()` and
 * `MockWebServer.enqueue(fixture)` compile and run against a started server.
 */
class LoopbackTransportsConsumerGatingTest {

    private val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private lateinit var server: MockWebServer
    private val transports = mutableListOf<HttpTransport>()

    @BeforeTest
    fun start() {
        server = MockWebServer()
        server.start(loopback, 0)
    }

    @AfterTest
    fun stop() {
        transports.forEach { it.close() }
        server.close()
    }

    @Test
    fun `the loopback factories and the fixture helpers run against a started server`() = runBlocking {
        val fixture = Fixture(200, HttpHeaders { add("Content-Type", "text/plain") }, "hello".encodeToByteArray())
        server.enqueue(fixture)
        server.enqueue(fixture.toMockResponse())
        val untrusted: HttpTransport = LoopbackTransports.untrusted("agent").also { transports += it }
        val trusted: HttpTransport = LoopbackTransports.trusted("agent") { listOf(loopback) }.also { transports += it }
        val request = HttpRequest(HttpMethod.Get, server.webtoolsUrl("/hello"))
        listOf(untrusted, trusted).forEach { transport ->
            val response = withTimeout(10.seconds) { transport.execute(request) }.getOrThrow()
            assertEquals(200, response.status)
            assertEquals("text/plain", response.headers["Content-Type"])
            assertEquals("hello", response.body().decodeToString())
        }
        assertEquals(2, server.requestCount)
    }
}
