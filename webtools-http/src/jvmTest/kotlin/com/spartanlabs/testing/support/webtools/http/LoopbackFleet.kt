package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds

/**
 * The servers and transports of one Level 3 test class: starts `MockWebServer`s on `127.0.0.1`, tracks every transport it is handed, runs a
 * call on real time under a 30 s ceiling, and on `close()` closes the transports first, then the servers, each in reverse order and each
 * inside `runCatching`, so one failure leaks nothing. Every Level 3 class closes its fleet in `@AfterTest`.
 */
internal class LoopbackFleet : AutoCloseable {
    /** `127.0.0.1`, built from bytes so the JDK performs no lookup. */
    val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    private val servers = ArrayList<MockWebServer>()
    private val transports = ArrayList<HttpTransport>()

    /**
     * Starts a server on `127.0.0.1` and an ephemeral port; [configure] runs before the start (protocols, TLS, the dispatcher).
     *
     * @return the started server.
     */
    fun server(configure: MockWebServer.() -> Unit = {}): MockWebServer =
        MockWebServer().apply(configure).also { it.start(loopback, 0); servers += it }

    /** Tracks [transport] for `close()` and returns it. */
    fun <T : HttpTransport> track(transport: T): T = transport.also { transports += it }

    /** Runs one call on real time, bounded to 30 s. */
    fun fetch(transport: HttpTransport, request: HttpRequest): Result<HttpResponse> =
        runBlocking { withTimeout(30.seconds) { transport.execute(request) } }

    /** A `GET` of [url] with no headers. */
    fun get(url: Url): HttpRequest = HttpRequest(HttpMethod.Get, url)

    override fun close() {
        transports.asReversed().forEach { runCatching { it.close() } }
        servers.asReversed().forEach { runCatching { it.close() } }
    }
}
