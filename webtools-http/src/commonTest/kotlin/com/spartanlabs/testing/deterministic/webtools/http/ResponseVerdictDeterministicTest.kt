package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.FetchVerdict
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.Verdict
import com.spartanlabs.webtools.http.fetchVerdictOf
import com.spartanlabs.webtools.http.retryAfterOf
import com.spartanlabs.webtools.http.verdictOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Level 4a - the response verdict tables: the client's `verdictOf` over status, the reader's throttle flag and the declared miss statuses, in the
 * order throttle, miss, `2xx` decode, rejected; a `429` is a throttle whatever the reader says; the fetcher's `fetchVerdictOf` delivers every
 * response but a throttle; `retryAfterOf` is the longer of the server's wait and the budget's pause.
 */
class ResponseVerdictDeterministicTest {

    private val statuses = listOf(100, 199, 200, 204, 299, 300, 304, 399, 400, 404, 429, 500, 503, 599)
    private val missSets: List<Set<Int>> = listOf(emptySet(), setOf(404), setOf(204), setOf(304))

    @Test
    fun `the client's verdict table over status, throttled and misses`() {
        statuses.forEach { status ->
            listOf(false, true).forEach { throttled ->
                missSets.forEach { misses ->
                    val expected = when {
                        throttled -> Verdict.Throttled
                        status == 429 -> Verdict.Throttled
                        status in misses -> Verdict.Missing
                        status in 200..299 -> Verdict.Decode
                        else -> Verdict.Rejected
                    }
                    assertEquals(expected, verdictOf(status, throttled, misses), "status $status, throttled $throttled, misses $misses")
                }
            }
        }

        // The rows the order decides, written out.
        assertEquals(Verdict.Decode, verdictOf(200, false, emptySet()))
        assertEquals(Verdict.Decode, verdictOf(204, false, emptySet()))
        assertEquals(Verdict.Missing, verdictOf(204, false, setOf(204)))          // a miss is checked before the decode
        assertEquals(Verdict.Missing, verdictOf(404, false, setOf(404)))
        assertEquals(Verdict.Rejected, verdictOf(404, false, emptySet()))
        assertEquals(Verdict.Throttled, verdictOf(404, true, setOf(404)))         // a throttle is checked before the miss
        assertEquals(Verdict.Missing, verdictOf(304, false, setOf(304)))
        assertEquals(Verdict.Rejected, verdictOf(304, false, emptySet()))
        assertEquals(Verdict.Rejected, verdictOf(100, false, emptySet()))
        assertEquals(Verdict.Rejected, verdictOf(503, false, emptySet()))
        assertEquals(Verdict.Throttled, verdictOf(503, true, emptySet()))
        assertEquals(Verdict.Throttled, verdictOf(200, true, emptySet()))
    }

    @Test
    fun `a 429 is always throttled even when the reader says not`() {
        assertEquals(Verdict.Throttled, verdictOf(429, false, setOf(404)))
        assertEquals(Verdict.Throttled, verdictOf(429, false, emptySet()))
        assertEquals(FetchVerdict.Throttled, fetchVerdictOf(429, false))
    }

    @Test
    fun `the fetcher's verdict is Throttled only for a throttle`() {
        statuses.forEach { status ->
            listOf(false, true).forEach { throttled ->
                val expected = if (throttled || status == 429) FetchVerdict.Throttled else FetchVerdict.Delivered
                assertEquals(expected, fetchVerdictOf(status, throttled), "status $status, throttled $throttled")
            }
        }
        assertEquals(FetchVerdict.Delivered, fetchVerdictOf(304, false))
        assertEquals(FetchVerdict.Delivered, fetchVerdictOf(404, false))
        assertEquals(FetchVerdict.Delivered, fetchVerdictOf(503, false))
        assertEquals(FetchVerdict.Throttled, fetchVerdictOf(503, true))
    }

    @Test
    fun `retryAfter is the longer of the server's wait and the pause`() {
        val table: List<Triple<Duration?, Duration, Duration>> = listOf(
            Triple(null, Duration.ZERO, Duration.ZERO),
            Triple(30.seconds, Duration.ZERO, 30.seconds),
            Triple(null, 61.seconds, 61.seconds),
            Triple(30.seconds, 100.seconds, 100.seconds),
            Triple(2.hours, 1.hours, 2.hours),
        )
        table.forEach { (server, pausedFor, expected) ->
            val signals = RateSignals { throttled = true; retryAfter = server }
            assertEquals(expected, retryAfterOf(signals, pausedFor), "Retry-After $server, paused for $pausedFor")
        }
        // A signal that is not a throttle and names no wait leaves the pause alone.
        assertEquals(5.seconds, retryAfterOf(RateSignals.None, 5.seconds))
    }
}
