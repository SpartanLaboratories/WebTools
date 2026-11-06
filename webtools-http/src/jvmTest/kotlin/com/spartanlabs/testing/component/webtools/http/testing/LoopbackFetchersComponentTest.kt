package com.spartanlabs.testing.component.webtools.http.testing

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.MSG_DEFAULT_MAX_WAIT
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.testing.LoopbackFetchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the loopback fetchers with no server and no socket: the untrusted one refuses a private address that is not loopback and keeps the
 * sensitive-header rule live for a loopback URL (the relaxation exempts the loopback rules only); the trusted one forces cleartext on while its
 * pinned hosts stay live (`HostNotPinned`, not `Scheme`) and keeps a policy that already allows cleartext as it is; a negative `defaultMaxWait`
 * is refused by each factory before any transport is built; closing the fetcher closes the transport it owns, once, and a fetch afterwards is
 * `Closed("fetcher")`.
 */
class LoopbackFetchersComponentTest {

    private val fetchers = mutableListOf<UrlFetcher>()
    private val resolver: (String) -> List<java.net.InetAddress> = { error("the resolver must not be reached") }

    private fun fetch(fetcher: UrlFetcher, urlText: String, configure: HttpHeaders.Builder.() -> Unit = {}): Result<HttpResponse> =
        runBlocking { withTimeout(5.seconds) { fetcher.fetch(HttpRequest(HttpMethod.Get, url(urlText)) { headers = HttpHeaders(configure) }) } }

    @AfterTest
    fun closeAll() = fetchers.forEach { it.close() }

    @Test
    fun `the untrusted fetcher refuses a private non-loopback address before any socket`() {
        val fetcher = LoopbackFetchers.untrusted("agent", resolve = resolver).also { fetchers += it }

        val refused = assertIs<TransportFailure.RefusedByPolicy>(fetch(fetcher, "http://10.0.0.5/").exceptionOrNull())

        assertEquals(RefusalReason.DeniedAddress, refused.reason)
    }

    @Test
    fun `the untrusted fetcher keeps the sensitive-header rule live`() {
        val fetcher = LoopbackFetchers.untrusted("agent", resolve = resolver).also { fetchers += it }

        val refused = assertIs<TransportFailure.RefusedByPolicy>(fetch(fetcher, "http://127.0.0.1:8080/") { addSensitive("X-Key", "v") }.exceptionOrNull())

        assertEquals(RefusalReason.SensitiveHeader, refused.reason)
    }

    @Test
    fun `the trusted fetcher with pinned hosts refuses another host with HostNotPinned`() {
        val pinned = OutboundUrlPolicy.trusted { pinnedHosts = setOf("a.example.test") }             // `https` only as given
        val fetcher = LoopbackFetchers.trusted("agent", pinned, resolver).also { fetchers += it }

        val refused = assertIs<TransportFailure.RefusedByPolicy>(fetch(fetcher, "http://b.example.test/").exceptionOrNull())

        // Not `Scheme`: cleartext was forced on in the pre-check, and the pin is still live.
        assertEquals(RefusalReason.HostNotPinned, refused.reason)
    }

    @Test
    fun `the trusted fetcher keeps a policy that already allows cleartext`() {
        val policy = OutboundUrlPolicy.trusted {
            allowCleartext = true
            pinnedHosts = setOf("a.example.test")
            settings { maxRedirects = 3 }
        }
        val events = captureTransportLogs(Level.INFO) {
            val fetcher = LoopbackFetchers.trusted("agent", policy, resolver).also { fetchers += it }
            val refused = assertIs<TransportFailure.RefusedByPolicy>(fetch(fetcher, "http://b.example.test/").exceptionOrNull())
            assertEquals(RefusalReason.HostNotPinned, refused.reason)
            assertTrue("policy=Trusted" in fetcher.toString() && "ownsTransport=true" in fetcher.toString(), fetcher.toString())
        }.second

        // The transport was built with the policy's own settings.
        val created = events.single { it.loggerName.endsWith("OkHttpTransport") && it.formattedMessage.startsWith("created") }
        assertEquals("created: policy=Trusted, settings=${policy.settings}", created.formattedMessage)
    }

    @Test
    fun `a negative defaultMaxWait is refused by each factory before any transport is built`() {
        val events = captureTransportLogs(Level.INFO) {
            // Should a factory ever return, what it built is closed before the assertion fails.
            val untrusted = assertFailsWith<IllegalArgumentException> {
                LoopbackFetchers.untrusted("agent", resolve = resolver) { defaultMaxWait = (-1).seconds }.close()
            }
            assertEquals(MSG_DEFAULT_MAX_WAIT, untrusted.message)
            val trusted = assertFailsWith<IllegalArgumentException> {
                LoopbackFetchers.trusted("agent", resolve = resolver) { defaultMaxWait = (-1).seconds }.close()
            }
            assertEquals(MSG_DEFAULT_MAX_WAIT, trusted.message)
        }.second

        assertFalse(events.any { it.formattedMessage.startsWith("created") }, events.map { it.loggerName + " " + it.formattedMessage }.toString())
    }

    @Test
    fun `closing the fetcher closes the transport it owns`() {
        val events = captureTransportLogs(Level.INFO) {
            val fetcher = LoopbackFetchers.untrusted("agent", resolve = resolver)
            assertTrue("ownsTransport=true" in fetcher.toString(), fetcher.toString())

            fetcher.close()
            fetcher.close()

            val closed = assertIs<HttpFailure.Closed>(fetch(fetcher, "http://127.0.0.1:8080/").exceptionOrNull())
            assertEquals("fetcher", closed.component)
        }.second

        val closedBy = events.filter { it.formattedMessage == "closed" }.map { it.loggerName.substringAfterLast('.') }
        assertEquals(listOf("OkHttpTransport", "UrlFetcher"), closedBy)
    }
}
