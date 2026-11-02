package com.spartanlabs.testing.component.webtools.http.testing

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the loopback factories with no server: `untrusted` refuses a denied address and a sensitive header; `untrusted` with a port set
 * refuses a foreign port (the relaxation exempts loopback hosts only); `trusted` with pinned hosts refuses another host with `HostNotPinned`,
 * not `Scheme`, which proves cleartext was forced on; `trusted` with cleartext already on keeps every property (the `INFO` line shows the
 * policy's own settings); a bad `userAgent` throws; the result is an `OkHttpTransport` the caller closes.
 */
class LoopbackTransportsComponentTest {

    private val transports = mutableListOf<OkHttpTransport>()
    private val resolver: (String) -> List<java.net.InetAddress> = { error("the resolver must not be reached") }

    private fun track(transport: OkHttpTransport): OkHttpTransport = transport.also { transports += it }

    private fun fetch(transport: OkHttpTransport, urlText: String, configure: HttpHeaders.Builder.() -> Unit = {}): Result<HttpResponse> =
        runBlocking { withTimeout(5.seconds) { transport.execute(HttpRequest(HttpMethod.Get, url(urlText)) { headers = HttpHeaders(configure) }) } }

    private fun reason(result: Result<HttpResponse>): RefusalReason = assertIs<TransportFailure.RefusedByPolicy>(result.exceptionOrNull()).reason

    @AfterTest
    fun close() {
        transports.forEach { it.close() }
    }

    @Test
    fun `untrusted refuses a denied address and a sensitive header`() {
        val transport = track(LoopbackTransports.untrusted("agent", resolve = resolver))
        assertEquals(RefusalReason.DeniedAddress, reason(fetch(transport, "http://10.0.0.5/")))
        assertEquals(RefusalReason.SensitiveHeader, reason(fetch(transport, "http://127.0.0.1:9/") { addSensitive("X-Api-Key", "k") }))
    }

    @Test
    fun `untrusted keeps the port rule for a host that is not loopback`() {
        val transport = track(LoopbackTransports.untrusted("agent", OutboundUrlPolicy.untrusted { allowedPorts = setOf(1) }, resolver))
        assertEquals(RefusalReason.Port, reason(fetch(transport, "http://example.test:9/")))
    }

    @Test
    fun `trusted forces cleartext on and keeps the pinned hosts`() {
        val pinned = OutboundUrlPolicy.trusted { pinnedHosts = setOf("a.test") }
        val transport = track(LoopbackTransports.trusted("agent", pinned, resolver))
        assertEquals(RefusalReason.HostNotPinned, reason(fetch(transport, "http://b.test/")))
    }

    @Test
    fun `trusted with cleartext already on keeps every property`() {
        val policy = OutboundUrlPolicy.trusted { allowCleartext = true; pinnedHosts = setOf("a.test"); settings { maxRedirects = 7 } }
        val (transport, events) = captureTransportLogs(Level.INFO) { track(LoopbackTransports.trusted("agent", policy, resolver)) }
        val created = events.single { it.formattedMessage.startsWith("created") }.formattedMessage
        assertTrue("policy=Trusted" in created, created)
        assertTrue("settings=${policy.settings}" in created, created)
        assertEquals(RefusalReason.HostNotPinned, reason(fetch(transport, "http://b.test/")))
    }

    @Test
    fun `a bad userAgent throws and the result is an OkHttpTransport`() {
        assertFailsWith<IllegalArgumentException> { LoopbackTransports.untrusted(" agent") }
        assertFailsWith<IllegalArgumentException> { LoopbackTransports.trusted("") }
        val transport: OkHttpTransport = track(LoopbackTransports.untrusted("agent", resolve = resolver))
        assertEquals("OkHttpTransport(policy=Untrusted, closed=false)", transport.toString())
    }
}
