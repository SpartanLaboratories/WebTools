package com.spartanlabs.testing.gating.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.LoopbackFetchers
import com.spartanlabs.webtools.http.testing.enqueue
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
 * Level 1 - the loopback fetchers as a consumer uses them, compiled **without any opt-in**: `LoopbackFetchers.untrusted("agent")` and
 * `LoopbackFetchers.trusted("agent", resolve = { listOf(address) })` (the resolver is named, because the trailing lambda of these factories is the
 * fetcher's configuration) each fetch from a started `MockWebServer`.
 */
class LoopbackFetchersConsumerGatingTest {

    private val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val fixture = Fixture(200, HttpHeaders { add("Content-Type", "text/plain") }, "hello".encodeToByteArray())
    private lateinit var server: MockWebServer
    private val fetchers = mutableListOf<UrlFetcher>()

    @BeforeTest
    fun start() {
        server = MockWebServer()
        server.start(loopback, 0)
    }

    @AfterTest
    fun stop() {
        fetchers.forEach { it.close() }
        server.close()
    }

    @Test
    fun `the untrusted loopback fetcher fetches from a started server`() {
        server.enqueue(fixture)
        val fetcher = LoopbackFetchers.untrusted("agent").also { fetchers += it }

        val response = runBlocking { withTimeout(10.seconds) { fetcher.fetch(HttpRequest(HttpMethod.Get, server.webtoolsUrl("/hello"))) } }.getOrThrow()

        assertEquals(200, response.status)
        assertEquals("hello", response.body().decodeToString())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the trusted loopback fetcher fetches over cleartext`() {
        server.enqueue(fixture)
        val fetcher = LoopbackFetchers.trusted("agent", resolve = { listOf(loopback) }).also { fetchers += it }

        val response = runBlocking { withTimeout(10.seconds) { fetcher.fetch(HttpRequest(HttpMethod.Get, server.webtoolsUrl("/hello"))) } }.getOrThrow()

        assertEquals(200, response.status)
        assertEquals(1, server.requestCount)
    }
}
