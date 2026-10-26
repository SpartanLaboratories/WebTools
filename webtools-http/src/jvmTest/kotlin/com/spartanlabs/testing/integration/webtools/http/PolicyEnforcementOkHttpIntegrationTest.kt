package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.StubDns
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.GuardedSocketFactory
import com.spartanlabs.webtools.http.MixedAnswers
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.PolicyRefusedIo
import com.spartanlabs.webtools.http.PolicyRefusedUnknownHost
import com.spartanlabs.webtools.http.UrlGuard
import com.spartanlabs.webtools.http.enforcement
import com.spartanlabs.webtools.http.policyRefusal
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Level 3 - the enforcement bundle on a real `OkHttpClient` (fast fallback at its default, `retryOnConnectionFailure(false)`)
 * against a `MockWebServer` on `127.0.0.1`, with a counting `StubDns`: (a) the resolver guard under `Reject` surfaces the
 * marker at the top, unwrapped, with no request made; (b) `DropDenied` with the loopback relaxation reaches the server;
 * (c) the socket guard alone judges every attempt (three markers, no `ConnectException`); (d) the socket guard with the
 * relaxation connects after an instant refusal; (e) one lookup per new connection; (f) a `Trusted` bundle filters nothing.
 */
class PolicyEnforcementOkHttpIntegrationTest {

    private val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private lateinit var server: MockWebServer
    private val clients = mutableListOf<OkHttpClient>()

    @BeforeTest
    fun start() {
        server = MockWebServer()
        server.start(loopback, 0)
    }

    @AfterTest
    fun stop() {
        clients.forEach { it.dispatcher.executorService.shutdown(); it.connectionPool.evictAll() }
        server.close()
    }

    private fun request() = Request.Builder().url("http://stub.test:${server.port}/").build()

    private fun client(configure: OkHttpClient.Builder.() -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .apply(configure)
            .build()
            .also { clients += it }

    private fun bundled(guard: UrlGuard, stub: StubDns): OkHttpClient = client {
        val e = guard.enforcement(stub)
        dns(e.dns)
        e.socketFactory?.let { socketFactory(it) }
        e.proxy?.let { proxy(it) }
    }

    private fun socketGuardOnly(guard: UrlGuard, stub: StubDns): OkHttpClient = client {
        proxy(java.net.Proxy.NO_PROXY)
        socketFactory(GuardedSocketFactory(guard))
        dns(stub)
    }

    private fun all(throwable: Throwable): List<Throwable> {
        val out = ArrayList<Throwable>()
        fun walk(t: Throwable) {
            if (t in out) return
            out += t
            t.cause?.let { walk(it) }
            t.suppressed.forEach { walk(it) }
        }
        walk(throwable)
        return out
    }

    @Test
    fun `a - the resolver guard under Reject surfaces the marker at the top with no request made`() {
        val stub = StubDns.of("10.0.0.5", "10.0.0.6", "fd00::1")
        val client = bundled(untrustedGuard(), stub)
        val failure = assertFailsWith<IOException> { client.newCall(request()).execute() }
        val marker = assertIs<PolicyRefusedUnknownHost>(failure)
        assertEquals("private-use", marker.refusal.rangeClass)
        assertEquals(0, failure.suppressed.size)
        assertEquals(1, stub.calls.get())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `b - DropDenied with the loopback relaxation reaches the server`() {
        server.enqueue(MockResponse(body = "ok"))
        val client = bundled(untrustedGuard(GuardRelaxation.LoopbackForTests) { mixedAnswers = MixedAnswers.DropDenied }, StubDns.of("10.0.0.5", "127.0.0.1"))
        client.newCall(request()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("ok", response.body.string())
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `c - the socket guard alone judges every attempt and never produces a ConnectException`() {
        val client = socketGuardOnly(untrustedGuard(), StubDns.of("10.0.0.5", "10.0.0.6", "fd00::1"))
        val failure = assertFailsWith<IOException> { client.newCall(request()).execute() }
        assertNotNull(failure.policyRefusal(), failure.toString())
        val topAndSuppressed = listOf(failure) + failure.suppressed
        assertEquals(3, topAndSuppressed.count { it is PolicyRefusedIo }, all(failure).toString())
        assertTrue(all(failure).none { it is ConnectException }, all(failure).toString())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `d - the socket guard with the relaxation connects after an instant refusal`() {
        server.enqueue(MockResponse(body = "ok"))
        val client = socketGuardOnly(untrustedGuard(GuardRelaxation.LoopbackForTests), StubDns.of("10.0.0.5", "127.0.0.1"))
        val mark = TimeSource.Monotonic.markNow()
        client.newCall(request()).execute().use { response -> assertEquals(200, response.code) }
        val elapsed = mark.elapsedNow()
        assertEquals(1, server.requestCount)
        // What the 2 s bound catches: the denied 10.0.0.5 attempt being connected instead of refused. With fast fallback
        // OkHttp would race that attempt against 127.0.0.1 after its 250 ms stagger, so a connected-but-denied attempt would
        // not stall the call; what it would do is either reach the server (requestCount above shows it did not) or, had the
        // relaxation been applied to the socket guard wrongly, make the whole call fail. The bound guards against a refusal
        // that is not instant: a guard that does I/O before refusing (a reverse lookup, a probe) would push this past 2 s on
        // an unroutable address, well short of the 10 s connect timeout.
        assertTrue(elapsed.inWholeMilliseconds < 2_000, "took $elapsed")
    }

    @Test
    fun `e - the resolver is asked once per new connection`() {
        repeat(3) { server.enqueue(MockResponse(body = "ok")) }
        val stub = StubDns.of("127.0.0.1")
        val client = bundled(untrustedGuard(GuardRelaxation.LoopbackForTests), stub)
        repeat(2) { client.newCall(request()).execute().use { it.body.string() } }
        assertEquals(1, stub.calls.get())
        client.connectionPool.evictAll()
        client.newCall(request()).execute().use { it.body.string() }
        assertEquals(2, stub.calls.get())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `f - a Trusted bundle filters nothing`() {
        server.enqueue(MockResponse(body = "ok"))
        val client = bundled(UrlGuard(OutboundUrlPolicy.trusted { allowCleartext = true }), StubDns.of("127.0.0.1"))
        client.newCall(request()).execute().use { response -> assertEquals(200, response.code) }
        assertEquals(1, server.requestCount)
    }
}
