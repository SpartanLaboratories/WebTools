package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.fakeRequest
import com.spartanlabs.testing.support.webtools.http.fakeResponse
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.FailureEvent
import com.spartanlabs.webtools.http.HIDDEN_STATUS_HEADER
import com.spartanlabs.webtools.http.HopContext
import com.spartanlabs.webtools.http.HopOutcome
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.MappedFailure
import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.RefusalStage
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.TransportSettings
import com.spartanlabs.webtools.http.UrlGuard
import com.spartanlabs.webtools.http.plainFailure
import com.spartanlabs.webtools.http.settleResponse
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.fail

/**
 * Level 2 - `settleResponse` over hand-built responses and a guard over the `Untrusted` defaults: a status outside 100..599 is `Protocol`
 * before any byte of the body is read; a hidden status is restored before the decision and the private header never surfaces; a followable
 * redirect is `Redirect` and its body is never read; a redirect past the cap is `TooManyRedirects` with the cap; a refused target is
 * `RefusedByPolicy` at the `RedirectTarget` stage; a body past the cap is `BodyTooLarge` without the body mapper, declared or not; an
 * exception of the body read goes through the body mapper and its outcome is returned as is; `HEAD`, a `1xx`, a `204` and a `304` read no
 * body and yield an empty one whatever the declared length; a delivered response carries the status, the hop's URL, the converted headers,
 * the chain and the body, and a `POST` `3xx` is delivered, not followed.
 */
class HopResponseComponentTest {

    /** A body of [text] whose source counts every read; [broken] throws on the first read; [declared] is what `contentLength()` reports. */
    private class CountingBody(text: String, private val broken: Boolean = false, private val declared: Long = -1L) : ResponseBody() {
        var reads = 0
        private val source = object : Source {
            private val data = Buffer().writeUtf8(text)
            override fun read(sink: Buffer, byteCount: Long): Long {
                reads++
                if (broken) throw IOException("body broke")
                return data.read(sink, byteCount)
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {}
        }.buffer()

        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = declared
        override fun source(): BufferedSource = source
    }

    private val current = url("https://example.com/dir/page?q=1")
    private val settings = TransportSettings(TransportSettings.UntrustedDefaults) { maxResponseBytes = 4; maxRedirects = 3 }

    /** The body mapper of a case whose body is never read or never fails: being called fails the test. */
    private val neverMapped: (Throwable) -> MappedFailure = { fail("the body mapper was called for $it") }

    private fun context(method: HttpMethod = HttpMethod.Get, chain: List<RedirectHop> = emptyList(), guard: UrlGuard = untrustedGuard()): HopContext =
        HopContext(method, current, chain, settings, guard)

    /** A response of the hop itself (its request URL is [current], so a relative `Location` resolves against it) with a counting body. */
    private fun response(code: Int, body: CountingBody, headers: Headers = headersOf()): Response =
        Response.Builder().request(fakeRequest(current.toString())).protocol(Protocol.HTTP_1_1).code(code).message("").headers(headers).body(body).build()

    private fun HopOutcome.failedWith(): MappedFailure = assertIs<HopOutcome.Failed>(this).mapped

    private fun MappedFailure.decidedByTheTransport() {
        val event = assertIs<FailureEvent.Other>(event)
        assertNull(event.exceptionClass)
        assertNull(event.keptMessage)
    }

    @Test
    fun `a status outside 100 to 599 is Protocol before any byte of the body is read`() {
        listOf(600, 99, 999).forEach { code ->
            val body = CountingBody("payload")
            val mapped = settleResponse(context(), response(code, body), neverMapped).failedWith()
            assertIs<TransportFailure.Protocol>(mapped.failure, "$code")
            mapped.decidedByTheTransport()
            assertEquals(0, body.reads, "$code")
        }
        // The restored status is the one judged: the placeholder with a hidden 600 is a Protocol failure too.
        val body = CountingBody("payload")
        assertIs<TransportFailure.Protocol>(settleResponse(context(), response(599, body, headersOf(HIDDEN_STATUS_HEADER, "600")), neverMapped).failedWith().failure)
        assertEquals(0, body.reads)
    }

    @Test
    fun `a hidden status is restored before the decision and the private header never surfaces`() {
        val response = fakeResponse(599, headersOf(HIDDEN_STATUS_HEADER, "503", "Retry-After", "0"), "err", fakeRequest(current.toString()))
        val done = assertIs<HopOutcome.Done>(settleResponse(context(), response, neverMapped)).response
        assertEquals(503, done.status)
        assertEquals(listOf("Retry-After" to "0"), done.headers.entries().map { it.name to it.value })
        assertEquals("err", done.body().decodeToString())
        // A genuine 599 stays a 599 response.
        assertEquals(599, assertIs<HopOutcome.Done>(settleResponse(context(), fakeResponse(599), neverMapped)).response.status)
    }

    @Test
    fun `a followable redirect is Redirect and its body is never read`() {
        listOf(HttpMethod.Get, HttpMethod.Head).forEach { method ->
            val body = CountingBody("payload")
            val redirect = assertIs<HopOutcome.Redirect>(settleResponse(context(method), response(302, body, headersOf("Location", "/x")), neverMapped), method.name)
            assertEquals(302, redirect.status)
            assertEquals("https://example.com/x", redirect.next.toString())
            assertEquals(0, body.reads, method.name)
        }
    }

    @Test
    fun `a redirect past the cap is TooManyRedirects with the cap`() {
        val chain = List(settings.maxRedirects) { RedirectHop(current, 302) }
        val body = CountingBody("payload")
        val mapped = settleResponse(context(chain = chain), response(302, body, headersOf("Location", "/x")), neverMapped).failedWith()
        assertEquals(3, assertIs<TransportFailure.TooManyRedirects>(mapped.failure).limit)
        mapped.decidedByTheTransport()
        assertEquals(0, body.reads)
        // One hop short of the cap still follows.
        assertIs<HopOutcome.Redirect>(settleResponse(context(chain = chain.dropLast(1)), response(302, CountingBody(""), headersOf("Location", "/x")), neverMapped))
    }

    @Test
    fun `a refused redirect target is RefusedByPolicy at the RedirectTarget stage`() {
        val body = CountingBody("payload")
        val mapped = settleResponse(context(), response(302, body, headersOf("Location", "http://10.0.0.5/")), neverMapped).failedWith()
        assertEquals(RefusalReason.DeniedAddress, assertIs<TransportFailure.RefusedByPolicy>(mapped.failure).reason)
        val event = assertIs<FailureEvent.PolicyRefusal>(mapped.event)
        assertEquals(RefusalStage.RedirectTarget, event.stage)
        assertEquals(RefusalReason.DeniedAddress, event.refusal.reason)
        assertEquals(0, body.reads)
    }

    @Test
    fun `a body past the cap is BodyTooLarge without the body mapper whether the length is declared or not`() {
        val declared = settleResponse(context(), fakeResponse(200, body = "12345"), neverMapped).failedWith()
        val declaredFailure = assertIs<TransportFailure.BodyTooLarge>(declared.failure)
        assertEquals(4L, declaredFailure.limit)
        assertEquals(5L, declaredFailure.declared)
        declared.decidedByTheTransport()
        val undeclared = settleResponse(context(), response(200, CountingBody("12345")), neverMapped).failedWith()
        val undeclaredFailure = assertIs<TransportFailure.BodyTooLarge>(undeclared.failure)
        assertEquals(4L, undeclaredFailure.limit)
        assertNull(undeclaredFailure.declared)
        undeclared.decidedByTheTransport()
    }

    @Test
    fun `an exception of the body read goes through the body mapper and its outcome is returned as is`() {
        val mapped = plainFailure(TransportFailure.Io())
        var received: Throwable? = null
        val outcome = settleResponse(context(), response(200, CountingBody("payload", broken = true))) { e -> received = e; mapped }
        assertSame(mapped, outcome.failedWith())
        assertEquals("body broke", assertIs<IOException>(received).message)
    }

    @Test
    fun `HEAD a 1xx a 204 and a 304 read no body and yield an empty one whatever the declared length`() {
        val cases = listOf(HttpMethod.Head to 200, HttpMethod.Head to 404, HttpMethod.Get to 100, HttpMethod.Get to 101, HttpMethod.Get to 199, HttpMethod.Get to 204, HttpMethod.Get to 304)
        cases.forEach { (method, status) ->
            val body = CountingBody("payload", declared = 5_000_000L)       // far above the cap, which is never consulted
            val done = assertIs<HopOutcome.Done>(settleResponse(context(method), response(status, body, headersOf("ETag", "\"v1\"")), neverMapped), "$method $status")
            assertEquals(status, done.response.status)
            assertEquals(0, done.response.bodySize, "$method $status")
            assertEquals("\"v1\"", done.response.headers["ETag"])
            assertEquals(0, body.reads, "$method $status")
        }
    }

    @Test
    fun `a delivered response carries the status the hop URL the converted headers the chain and the body`() {
        val chain = listOf(RedirectHop(url("https://example.com/"), 301))
        val headers = headersOf("X-A", " a ", "Set-Cookie", "x=1", "Set-Cookie", "y=2")
        val done = assertIs<HopOutcome.Done>(settleResponse(context(HttpMethod.Post, chain), fakeResponse(404, headers, "nf"), neverMapped)).response
        assertEquals(404, done.status)
        assertEquals(current, done.url)
        assertEquals(listOf("X-A" to "a", "Set-Cookie" to "x=1", "Set-Cookie" to "y=2"), done.headers.entries().map { it.name to it.value })
        assertEquals(chain, done.redirectChain)
        assertEquals("nf", done.body().decodeToString())
    }

    @Test
    fun `a POST 3xx is delivered not followed`() {
        val done = assertIs<HopOutcome.Done>(settleResponse(context(HttpMethod.Post), fakeResponse(302, headersOf("Location", "/x"), "see"), neverMapped)).response
        assertEquals(302, done.status)
        assertEquals("/x", done.headers["Location"])
        assertEquals("see", done.body().decodeToString())
    }
}
