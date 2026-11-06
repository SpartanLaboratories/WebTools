package com.spartanlabs.testing.component.webtools.http.testing

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.testing.LoopbackFetchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the loopback fetchers with no server and no socket: the untrusted one refuses a private address that is not loopback and keeps the
 * sensitive-header rule live for a loopback URL (the relaxation exempts the loopback rules only); closing the fetcher closes the transport it owns,
 * once, and a fetch afterwards is `Closed("fetcher")`.
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
