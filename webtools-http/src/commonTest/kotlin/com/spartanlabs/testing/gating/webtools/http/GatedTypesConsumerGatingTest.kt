package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.WallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// A function and a class whose default names a gated type: a consumer writes exactly this without any
// opt-in. Both must keep compiling in this source set, which has no opt-in.
private fun now(clock: WallClock = WallClock.System): WallClock = clock

private class UsesClock(val clock: WallClock = WallClock.System)

// Gated types in a constructor signature. A consumer holds and passes them; it cannot implement them here.
private class Ports(val transport: HttpTransport?, val clock: WallClock, val lastFailure: HttpFailure?)

/**
 * Level 1 - the permanent consumer of the gated types, compiled in `commonTest` **without any opt-in**.
 * It names `HttpTransport`, `WallClock`, `HttpFailure`, `TransportFailure` and `UpstreamFailure` in
 * signatures, uses `WallClock.System` as a default, constructs every public failure leaf and matches a
 * failure with a `when` that keeps an `else`. It cannot implement or subclass any gated type, which is
 * the point: compiling it proves the test source set is a consumer.
 */
class GatedTypesConsumerGatingTest {

    // Gated types in a function signature.
    private fun describe(transport: HttpTransport?, clock: WallClock, failure: HttpFailure): String =
        (if (transport == null) "no transport" else "a transport") + " at " + clock.nowEpochMillis() + ": " + failure.message

    @Test
    fun `the system clock is usable without the opt-in`() {
        assertTrue(WallClock.System.nowEpochMillis() > 0L)
        assertEquals("WallClock.System", WallClock.System.toString())
        assertSame(WallClock.System, now())
        assertSame(WallClock.System, UsesClock().clock)
        assertSame(WallClock.System, Ports(null, WallClock.System, null).clock)
    }

    @Test
    fun `every public leaf is constructible and belongs to its group`() {
        val cause = IllegalStateException("lower-level")
        val direct: List<HttpFailure> = listOf(
            HttpFailure.Closed("transport"),
            HttpFailure.BudgetExhausted(1.seconds, ExhaustedReason.Window, null),
            HttpFailure.DecodeFailure(),
            HttpFailure.DecodeFailure(cause),
        )
        val transport: List<HttpFailure> = listOf(
            TransportFailure.Timeout(), TransportFailure.Timeout(cause),
            TransportFailure.ConnectFailed(), TransportFailure.ConnectFailed(cause),
            TransportFailure.DnsFailed("h"), TransportFailure.DnsFailed("h", cause),
            TransportFailure.TlsFailed(), TransportFailure.TlsFailed(cause),
            TransportFailure.Protocol(), TransportFailure.Protocol(cause),
            TransportFailure.Io(), TransportFailure.Io(cause),
            TransportFailure.InvalidRequest("rule"), TransportFailure.InvalidRequest("rule", cause),
            TransportFailure.RefusedByPolicy(RefusalReason.Scheme), TransportFailure.RefusedByPolicy(RefusalReason.Scheme, cause),
            TransportFailure.BodyTooLarge(10L, null), TransportFailure.BodyTooLarge(10L, 20L, cause),
            TransportFailure.TooManyRedirects(5), TransportFailure.TooManyRedirects(5, cause),
            TransportFailure.RetryRefused(), TransportFailure.RetryRefused(cause),
        )
        val upstream: List<HttpFailure> = listOf(
            UpstreamFailure.UpstreamError(500, HttpHeaders(), ByteArray(0)),
        )
        direct.forEach { assertTrue(it !is TransportFailure && it !is UpstreamFailure, it.toString()) }
        transport.forEach { assertTrue(it is TransportFailure, it.toString()) }
        upstream.forEach { assertTrue(it is UpstreamFailure, it.toString()) }
        // 4 direct leaves (one with and one without a cause for DecodeFailure) + 22 transport (eleven leaves, each
        // with and without a cause) + 1 upstream (UpstreamError; RateLimited's constructor is internal).
        assertEquals(4 + 22 + 1, (direct + transport + upstream).size)
    }

    @Test
    fun `a when over a failure matches the groups and keeps an else`() {
        val failures: List<HttpFailure> = listOf(
            TransportFailure.Timeout(),
            UpstreamFailure.UpstreamError(503, HttpHeaders(), ByteArray(0)),
            HttpFailure.Closed("client"),
        )
        val groups = failures.map { failure ->
            when (failure) {
                is TransportFailure -> "transport"
                is UpstreamFailure -> "upstream"
                else -> "other"
            }
        }
        assertEquals(listOf("transport", "upstream", "other"), groups)
        assertTrue(describe(null, WallClock.System, failures[0]).endsWith("The request timed out"))
    }
}
