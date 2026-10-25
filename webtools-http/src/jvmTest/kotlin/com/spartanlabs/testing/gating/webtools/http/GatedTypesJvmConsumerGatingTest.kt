package com.spartanlabs.testing.gating.webtools.http

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

// The same consumer shapes as the commonTest class, in jvmTest: this source set has no opt-in either.
private fun jvmNow(clock: WallClock = WallClock.System): WallClock = clock

private class JvmUsesClock(val clock: WallClock = WallClock.System)

/**
 * Level 1 - the permanent consumer of the gated types in `jvmTest`, compiled **without any opt-in**:
 * gated types in signatures, `WallClock.System` as a default, the public failure leaves, and the
 * `internal` `RateLimited` constructor (visible to this module's tests) in a `when` with an `else`.
 * Compiling it proves `jvmTest` is a consumer too.
 */
class GatedTypesJvmConsumerGatingTest {

    // Gated types in a function signature.
    private fun transportName(transport: HttpTransport?): String = if (transport == null) "none" else "some"

    @Test
    fun `the system clock is the default without the opt-in`() {
        assertSame(WallClock.System, jvmNow())
        assertSame(WallClock.System, JvmUsesClock().clock)
        assertTrue(WallClock.System.nowEpochMillis() > 0L)
        assertEquals("none", transportName(null))
    }

    @Test
    fun `the public leaves and the internal RateLimited match in a when with an else`() {
        val failures: List<HttpFailure> = listOf(
            TransportFailure.RefusedByPolicy(RefusalReason.Port),
            UpstreamFailure.RateLimited(429, 2.seconds),
            UpstreamFailure.UpstreamError(502, HttpHeaders(), byteArrayOf(1, 2, 3)),
            HttpFailure.DecodeFailure(IllegalStateException("x")),
        )
        val kinds = failures.map { failure ->
            when (failure) {
                is TransportFailure -> "transport"
                is UpstreamFailure.RateLimited -> "rate-limited ${failure.status} ${failure.retryAfter}"
                is UpstreamFailure -> "upstream ${failure.status}"
                else -> "other"
            }
        }
        assertEquals(listOf("transport", "rate-limited 429 2s", "upstream 502", "other"), kinds)
    }
}
