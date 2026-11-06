package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.MSG_DEFAULT_MAX_WAIT
import com.spartanlabs.webtools.http.RateSignalsReader
import com.spartanlabs.webtools.http.UpstreamClientConfig
import com.spartanlabs.webtools.http.UrlFetcherConfig
import com.spartanlabs.webtools.http.WallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Level 2 - `UrlFetcherConfig`, driven through its `internal` `resolve()`: the defaults (`CallBudget.Unlimited`, `RateSignalsReader.Standard`,
 * `WallClock.System`, `TimeSource.Monotonic`, a five-second wait); `DEFAULT_MAX_WAIT` is the client's five seconds; a negative `defaultMaxWait` is refused with the
 * constant message and zero is accepted.
 */
class UrlFetcherConfigComponentTest {

    @Test
    fun `the defaults are Unlimited, Standard, System, Monotonic and five seconds`() {
        val resolved = UrlFetcherConfig().resolve()
        assertSame(CallBudget.Unlimited, resolved.budget)
        assertSame(RateSignalsReader.Standard, resolved.rateSignals)
        assertSame(WallClock.System, resolved.wallClock)
        assertSame(TimeSource.Monotonic, resolved.timeSource)
        assertEquals(5.seconds, resolved.defaultMaxWait)
    }

    @Test
    fun `DEFAULT_MAX_WAIT equals the client's`() {
        assertEquals(UpstreamClientConfig.DEFAULT_MAX_WAIT, UrlFetcherConfig.DEFAULT_MAX_WAIT)
        assertEquals(5.seconds, UrlFetcherConfig.DEFAULT_MAX_WAIT)
        assertEquals(UrlFetcherConfig.DEFAULT_MAX_WAIT, UrlFetcherConfig().defaultMaxWait)
    }

    @Test
    fun `a negative defaultMaxWait is refused`() {
        val failure = assertFailsWith<IllegalArgumentException> { UrlFetcherConfig().apply { defaultMaxWait = (-1).seconds }.resolve() }
        assertEquals(MSG_DEFAULT_MAX_WAIT, failure.message)
        assertEquals(Duration.ZERO, UrlFetcherConfig().apply { defaultMaxWait = Duration.ZERO }.resolve().defaultMaxWait)
        assertEquals(Duration.INFINITE, UrlFetcherConfig().apply { defaultMaxWait = Duration.INFINITE }.resolve().defaultMaxWait)
    }
}
