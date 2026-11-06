package com.spartanlabs.testing.nonfunctional.webtools.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.http.BASE_URL
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.facadeEvents
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val SECRET_HEADER = "SECRET-HEADER-5a1c"
private const val SECRET_QUERY = "SECRET-QUERY-9e3d"
private const val SECRET_BODY = "SECRET-BODY-2b7f"
private const val SECRET_TOKEN = "SECRET-TOKEN-6d4e"
private const val SECRET_VALUE = "SECRET-VALUE-8c0a"

/**
 * Level 4c - secrecy of the call facades at `DEBUG`: a header value, a query value, a response body, a credential and a decoded value, each an
 * unmistakable sentinel string, go through `UpstreamClient` (a found value, a cache hit, a declared miss, a decode failure, an upstream error,
 * three kinds of invalid request, a policy refusal, a throttle, an exhausted budget and a close) and through `UrlFetcher` (two responses, three
 * kinds of invalid request, two policy refusals, a throttle, an exhausted budget and a close). Across every log event of the module (formatted
 * message, arguments, throwable), every failure's message and `toString()`, and the `toString()` of the client, the fetcher, the decoding
 * endpoint and the results, no sentinel appears. A failure's `cause` is the consumer's own exception and is not the facades' to redact. Real time under
 * `runBlocking` within 30 s, on the production clocks.
 */
class CallFacadesSecrecyNonFunctionalTest {

    private val sentinels = listOf(SECRET_HEADER, SECRET_QUERY, SECRET_BODY, SECRET_TOKEN, SECRET_VALUE)
    private val windows: List<RateWindow> = listOf(RateWindow("minute", 60, 1.minutes))

    // What the facades returned or printed, with a label for the failure message of an assertion.
    private val texts = ArrayList<Pair<String, String>>()

    // An argument may be null: the budget's own `refused` line passes a null window for a pause.
    private fun ILoggingEvent.texts(): List<String> =
        listOf(formattedMessage) + (argumentArray?.map { it?.toString() ?: "null" } ?: emptyList()) +
            (throwableProxy?.let { listOf(it.className, it.message.orEmpty()) } ?: emptyList())

    private fun assertClean(label: String, text: String) {
        sentinels.forEach { sentinel -> assertFalse(sentinel in text, "'$sentinel' leaked in $label: $text") }
    }

    // Records a result's own text: a value's `toString()`, or a failure's message and `toString()` (never its cause, which is the consumer's).
    private fun <T> record(label: String, result: Result<T>): Result<T> {
        result.fold(
            onSuccess = { texts += "$label value" to it.toString() },
            onFailure = { failure ->
                texts += "$label message" to failure.message.orEmpty()
                texts += "$label toString" to failure.toString()
            },
        )
        return result
    }

    private fun secretResponse(status: Int, vararg headers: Pair<String, String>): Step =
        Step.respond(status, HttpHeaders { add("X-Echo", SECRET_HEADER); headers.forEach { (name, value) -> add(name, value) } }, SECRET_BODY)

    private fun assertNothingLeaked(events: List<ILoggingEvent>) {
        assertTrue(events.isNotEmpty())
        events.forEach { event -> event.texts().forEach { assertClean("a ${event.level} event of ${event.loggerName}", it) } }
        assertTrue(texts.isNotEmpty())
        texts.forEach { (label, text) -> assertClean(label, text) }
    }

    @Test
    fun `no sentinel reaches a log event, a failure or a toString of the client`() {
        val fake = FakeHttpTransport()
            .on(RequestMatch.get("/v1/ok"), secretResponse(200))
            .on(RequestMatch.get("/v1/missing"), secretResponse(404))
            .on(RequestMatch.get("/v1/undecodable"), secretResponse(200))
            .on(RequestMatch.get("/v1/error"), secretResponse(500))
            .on(RequestMatch.get("/v1/throttled"), secretResponse(429, "Retry-After" to "30"))
        val decoding = Endpoint("decoding", { response -> Result.success(response.body().decodeToString().replace(SECRET_BODY, SECRET_VALUE)) }) {
            ttl = 10.minutes
            misses(404)
        }
        val failing = Endpoint<String>("failing", { Result.failure(IllegalStateException(SECRET_VALUE)) })

        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runBlocking {
                withTimeout(30.seconds) {
                    CaffeineResponseCache().use { responseCache ->
                        val client = UpstreamClient(fake, BASE_URL) {
                            headers = HttpHeaders { addSensitive("Authorization", "Bearer $SECRET_TOKEN"); add("X-Trace", SECRET_HEADER) }
                            cache(responseCache)
                            budget(windows)
                        }
                        try {
                            texts += "client" to client.toString()
                            texts += "endpoint" to decoding.toString()

                            val found = record("found", client.call(decoding) { path("ok"); query("token", SECRET_QUERY); headers { add("X-Call", SECRET_HEADER) } })
                            assertEquals(Lookup.Found(SECRET_VALUE), found.getOrThrow())            // the sentinels did flow through the call
                            record("hit", client.call(decoding) { path("ok"); query("token", SECRET_QUERY) })
                            assertSame(Lookup.Missing, record("missing", client.call(decoding) { path("missing"); query("token", SECRET_QUERY) }).getOrThrow())
                            assertIs<HttpFailure.DecodeFailure>(record("decode failure", client.call(failing) { path("undecodable"); query("token", SECRET_QUERY) }).exceptionOrNull())
                            assertIs<UpstreamFailure.UpstreamError>(record("upstream error", client.call(decoding) { path("error"); query("token", SECRET_QUERY) }).exceptionOrNull())

                            assertIs<TransportFailure.InvalidRequest>(record("dot segment", client.call(decoding) { path(".."); query("token", SECRET_QUERY) }).exceptionOrNull())
                            assertIs<TransportFailure.InvalidRequest>(record("controlled header", client.call(decoding) { headers { add("Host", SECRET_HEADER) } }).exceptionOrNull())
                            assertIs<TransportFailure.InvalidRequest>(record("thrown settings", client.call(decoding) { throw IllegalStateException(SECRET_VALUE) }).exceptionOrNull())

                            assertIs<UpstreamFailure.RateLimited>(record("throttle", client.call(decoding) { path("throttled"); query("token", SECRET_QUERY) }).exceptionOrNull())
                            // The throttle pauses the budget; a call of a new key (so not a cache hit) then reaches it and is exhausted.
                            assertIs<HttpFailure.BudgetExhausted>(record("exhausted", client.call(decoding) { path("ok"); query("token", "$SECRET_QUERY-2") }).exceptionOrNull())
                        } finally {
                            client.close()
                        }
                        assertIs<HttpFailure.Closed>(record("closed", client.call(decoding) { path("ok"); query("token", SECRET_QUERY) }).exceptionOrNull())
                        texts += "closed client" to client.toString()

                        // A policy refusal: under an Untrusted policy the credential is refused before anything is spent.
                        UpstreamClient(fake, BASE_URL) { policy = OutboundUrlPolicy.untrusted() }.use { untrusted ->
                            val refused = record(
                                "refused",
                                untrusted.call(decoding) { path("ok"); query("token", SECRET_QUERY); headers { addSensitive("Authorization", "Bearer $SECRET_TOKEN") } },
                            )
                            assertIs<TransportFailure.RefusedByPolicy>(refused.exceptionOrNull())
                            texts += "untrusted client" to untrusted.toString()
                        }
                    }
                }
            }
        }

        assertNothingLeaked(events)
        // The scenario did log what it was meant to: the per-call lines, the two warnings and the debug lines of the failures it created.
        val lines = facadeEvents(events, "UpstreamClient").map { it.formattedMessage }
        assertEquals(7, lines.count { it.startsWith("call:") }, lines.toString())      // every call that reached the cache step
        assertEquals(1, lines.count { it.startsWith("throttled:") }, lines.toString())
        assertEquals(1, lines.count { it.startsWith("refused by policy:") }, lines.toString())
        assertEquals(3, lines.count { it.startsWith("invalid request:") }, lines.toString())
        assertEquals(1, lines.count { it.startsWith("decode failed:") }, lines.toString())
    }

    @Test
    fun `no sentinel reaches a log event, a failure or a toString of the fetcher`() {
        val fake = FakeHttpTransport()
            .on(RequestMatch.get("/ok"), secretResponse(200))
            .on(RequestMatch.get("/gone"), secretResponse(404))
            .on(RequestMatch.get("/throttled"), secretResponse(429, "Retry-After" to "30"))

        fun get(urlText: String, configure: HttpHeaders.Builder.() -> Unit = {}): HttpRequest =
            HttpRequest(HttpMethod.Get, Url.parse(urlText).getOrThrow()) { headers = HttpHeaders(configure) }

        val plain = get("https://files.example.test/ok?token=$SECRET_QUERY") { add("X-Trace", SECRET_HEADER) }

        val (_, events) = captureTransportLogs(Level.DEBUG) {
            runBlocking {
                withTimeout(30.seconds) {
                    WindowedCallBudget(windows).use { supplied ->
                        val fetcher = UrlFetcher(fake, OutboundUrlPolicy.untrusted()) { budget = supplied }
                        try {
                            texts += "fetcher" to fetcher.toString()

                            assertIs<TransportFailure.InvalidRequest>(record("cost", fetcher.fetch(plain) { cost = 0 }).exceptionOrNull())
                            val controlled = get("https://files.example.test/ok?token=$SECRET_QUERY") { add("User-Agent", SECRET_HEADER) }
                            assertIs<TransportFailure.InvalidRequest>(record("controlled header", fetcher.fetch(controlled)).exceptionOrNull())
                            assertIs<TransportFailure.InvalidRequest>(record("thrown settings", fetcher.fetch(plain) { throw IllegalStateException(SECRET_VALUE) }).exceptionOrNull())

                            val credential = get("https://files.example.test/ok?token=$SECRET_QUERY") { addSensitive("Authorization", "Bearer $SECRET_TOKEN") }
                            assertIs<TransportFailure.RefusedByPolicy>(record("sensitive header", fetcher.fetch(credential)).exceptionOrNull())
                            assertIs<TransportFailure.RefusedByPolicy>(record("denied address", fetcher.fetch(get("http://10.0.0.5/a?token=$SECRET_QUERY"))).exceptionOrNull())

                            val fetched = record("response", fetcher.fetch(plain)).getOrThrow()
                            assertEquals(SECRET_BODY, fetched.body().decodeToString())              // the sentinels did flow through the fetch
                            assertEquals(404, record("not found", fetcher.fetch(get("https://files.example.test/gone?token=$SECRET_QUERY"))).getOrThrow().status)

                            assertIs<UpstreamFailure.RateLimited>(record("throttle", fetcher.fetch(get("https://files.example.test/throttled?token=$SECRET_QUERY"))).exceptionOrNull())
                            // The throttle pauses the budget, so the next fetch is exhausted.
                            assertIs<HttpFailure.BudgetExhausted>(record("exhausted", fetcher.fetch(plain)).exceptionOrNull())
                        } finally {
                            fetcher.close()
                        }
                        assertIs<HttpFailure.Closed>(record("closed", fetcher.fetch(plain)).exceptionOrNull())
                        texts += "closed fetcher" to fetcher.toString()
                    }
                }
            }
        }

        assertNothingLeaked(events)
        // The scenario did log what it was meant to: the per-fetch lines, the warnings and the debug lines of the failures it created.
        val lines = facadeEvents(events, "UrlFetcher").map { it.formattedMessage }
        assertEquals(4, lines.count { it.startsWith("fetch:") }, lines.toString())
        assertEquals(1, lines.count { it.startsWith("throttled:") }, lines.toString())
        assertEquals(2, lines.count { it.startsWith("refused by policy:") }, lines.toString())
        assertEquals(3, lines.count { it.startsWith("invalid request:") }, lines.toString())
    }
}
