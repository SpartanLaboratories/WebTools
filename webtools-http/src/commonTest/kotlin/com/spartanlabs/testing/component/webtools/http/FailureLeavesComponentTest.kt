package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - every failure leaf: its exact constant message, its constructors with and without a cause,
 * its fields, its `is` relations, and the `require`s at construction.
 */
class FailureLeavesComponentTest {

    private val cause = IllegalStateException("lower-level")

    @Test
    fun `the direct leaves carry their constant messages`() {
        assertEquals("The transport is closed", HttpFailure.Closed("transport").message)
        assertEquals("The call budget is exhausted: Window, wait 2s", HttpFailure.BudgetExhausted(2.seconds, ExhaustedReason.Window, null).message)
        assertEquals("The call budget is exhausted: Paused, wait 2s, window w", HttpFailure.BudgetExhausted(2.seconds, ExhaustedReason.Paused, "w").message)
        assertEquals("The response body could not be decoded", HttpFailure.DecodeFailure().message)
    }

    @Test
    fun `the transport leaves carry their constant messages`() {
        assertEquals("The request timed out", TransportFailure.Timeout().message)
        assertEquals("The connection could not be established", TransportFailure.ConnectFailed().message)
        assertEquals("The host name could not be resolved: h", TransportFailure.DnsFailed("h").message)
        assertEquals("The TLS handshake failed", TransportFailure.TlsFailed().message)
        assertEquals("The server's response broke the HTTP protocol", TransportFailure.Protocol().message)
        assertEquals("An I/O error interrupted the exchange", TransportFailure.Io().message)
        assertEquals("The request is invalid: r", TransportFailure.InvalidRequest("r").message)
        assertEquals("The request was refused by the URL policy: Scheme", TransportFailure.RefusedByPolicy(RefusalReason.Scheme).message)
        assertEquals("The response body is larger than the limit of 10 bytes", TransportFailure.BodyTooLarge(10L, null).message)
        assertEquals("The response body is larger than the limit of 10 bytes (declared 20 bytes)", TransportFailure.BodyTooLarge(10L, 20L).message)
        assertEquals("The request was redirected more than 5 times", TransportFailure.TooManyRedirects(5).message)
        assertEquals("The transport refused to send the request a second time", TransportFailure.RetryRefused().message)
    }

    @Test
    fun `the upstream leaves carry their constant messages`() {
        assertEquals("The server rate-limited the call (status 429); retry in 3s", UpstreamFailure.RateLimited(429, 3.seconds).message)
        assertEquals("The server answered with status 500", UpstreamFailure.UpstreamError(500, HttpHeaders(), ByteArray(0)).message)
    }

    @Test
    fun `the cause is kept by identity and null without one`() {
        val withCause: List<Throwable> = listOf(
            HttpFailure.DecodeFailure(cause), TransportFailure.Timeout(cause), TransportFailure.ConnectFailed(cause),
            TransportFailure.DnsFailed("h", cause), TransportFailure.TlsFailed(cause), TransportFailure.Protocol(cause),
            TransportFailure.Io(cause), TransportFailure.InvalidRequest("r", cause), TransportFailure.RefusedByPolicy(RefusalReason.Port, cause),
            TransportFailure.BodyTooLarge(1L, null, cause), TransportFailure.TooManyRedirects(1, cause), TransportFailure.RetryRefused(cause),
        )
        withCause.forEach { assertSame(cause, it.cause, it.toString()) }
        val withoutCause: List<Throwable> = listOf(
            HttpFailure.Closed("c"), HttpFailure.BudgetExhausted(1.seconds, ExhaustedReason.QueueFull, null), HttpFailure.DecodeFailure(),
            TransportFailure.Timeout(), TransportFailure.ConnectFailed(), TransportFailure.DnsFailed("h"), TransportFailure.TlsFailed(),
            TransportFailure.Protocol(), TransportFailure.Io(), TransportFailure.InvalidRequest("r"), TransportFailure.RefusedByPolicy(RefusalReason.Port),
            TransportFailure.BodyTooLarge(1L, null), TransportFailure.TooManyRedirects(1), TransportFailure.RetryRefused(),
            UpstreamFailure.RateLimited(429, 1.seconds), UpstreamFailure.UpstreamError(500, HttpHeaders(), ByteArray(0)),
        )
        withoutCause.forEach { assertNull(it.cause, it.toString()) }
    }

    @Test
    fun `the fields are exposed`() {
        assertEquals("transport", HttpFailure.Closed("transport").component)
        HttpFailure.BudgetExhausted(2.seconds, ExhaustedReason.CostExceedsLimit, "w").let {
            assertEquals(2.seconds, it.wait)
            assertEquals(ExhaustedReason.CostExceedsLimit, it.reason)
            assertEquals("w", it.window)
        }
        assertEquals("h", TransportFailure.DnsFailed("h").host)
        assertEquals("r", TransportFailure.InvalidRequest("r").rule)
        assertEquals(RefusalReason.DeniedAddress, TransportFailure.RefusedByPolicy(RefusalReason.DeniedAddress).reason)
        TransportFailure.BodyTooLarge(10L, 20L).let {
            assertEquals(10L, it.limit)
            assertEquals(20L, it.declared)
        }
        assertEquals(5, TransportFailure.TooManyRedirects(5).limit)
        UpstreamFailure.RateLimited(429, 3.seconds).let {
            assertEquals(429, it.status)
            assertEquals(3.seconds, it.retryAfter)
        }
        assertEquals(503, UpstreamFailure.UpstreamError(503, HttpHeaders(), ByteArray(0)).status)
    }

    @Test
    fun `leaves are in their group and every group is an HttpFailure`() {
        val transport: List<HttpFailure> = listOf(TransportFailure.Timeout(), TransportFailure.DnsFailed("h"), TransportFailure.BodyTooLarge(1L, null))
        val upstream: List<HttpFailure> = listOf(UpstreamFailure.RateLimited(429, 1.seconds), UpstreamFailure.UpstreamError(500, HttpHeaders(), ByteArray(0)))
        val direct: List<HttpFailure> = listOf(HttpFailure.Closed("c"), HttpFailure.BudgetExhausted(1.seconds, ExhaustedReason.Window, null), HttpFailure.DecodeFailure())
        transport.forEach { assertTrue(it is TransportFailure && it !is UpstreamFailure, it.toString()) }
        upstream.forEach { assertTrue(it is UpstreamFailure && it !is TransportFailure, it.toString()) }
        direct.forEach { assertFalse(it is TransportFailure || it is UpstreamFailure, it.toString()) }
    }

    @Test
    fun `the requires at construction throw IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { HttpFailure.Closed(" ") }
        assertFailsWith<IllegalArgumentException> { TransportFailure.DnsFailed("") }
        assertFailsWith<IllegalArgumentException> { TransportFailure.InvalidRequest(" ") }
        assertFailsWith<IllegalArgumentException> { TransportFailure.BodyTooLarge(-1L, null) }
        assertFailsWith<IllegalArgumentException> { TransportFailure.BodyTooLarge(1L, -1L) }
        assertFailsWith<IllegalArgumentException> { TransportFailure.TooManyRedirects(-1) }
        assertFailsWith<IllegalArgumentException> { HttpFailure.BudgetExhausted((-1).seconds, ExhaustedReason.Window, null) }
        assertFailsWith<IllegalArgumentException> { HttpFailure.BudgetExhausted(1.seconds, ExhaustedReason.Window, " ") }
        assertFailsWith<IllegalArgumentException> { UpstreamFailure.RateLimited(429, (-1).seconds) }
        assertFailsWith<IllegalArgumentException> { UpstreamFailure.RateLimited(99, 1.seconds) }
        assertFailsWith<IllegalArgumentException> { UpstreamFailure.UpstreamError(600, HttpHeaders(), ByteArray(0)) }
        assertEquals("Status code must be in 100..599", assertFailsWith<IllegalArgumentException> { UpstreamFailure.UpstreamError(0, HttpHeaders(), ByteArray(0)) }.message)
    }
}
