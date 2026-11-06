package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.FETCH_URL
import com.spartanlabs.testing.support.webtools.http.PermitRecordingBudget
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.facadeEvents
import com.spartanlabs.testing.support.webtools.http.fetcherOn
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.RULE_COST
import com.spartanlabs.webtools.http.RULE_MAX_WAIT
import com.spartanlabs.webtools.http.RULE_SETTINGS
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - what `UrlFetcher.fetch` refuses before anything is spent, and how it waits: a cost below 1, a negative `maxWait`, a header the transport
 * controls and an exception thrown by the lambda are each `InvalidRequest` with a constant rule, no permit and no transport call; a wait longer
 * than `maxWait` comes back as `BudgetExhausted`; the fetcher's `defaultMaxWait` applies when the spec sets none; concurrent fetches of one URL
 * are not coalesced; an `InvalidRequest` is logged once at `DEBUG` on the fetcher's logger with its rule and no header value or query.
 */
class UrlFetcherRequestComponentTest {

    private val request = HttpRequest(HttpMethod.Get, FETCH_URL)

    private fun ruleOf(result: Result<*>): String = assertIs<TransportFailure.InvalidRequest>(result.exceptionOrNull()).rule

    @Test
    fun `a cost below 1 is InvalidRequest and nothing is spent`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            fetcherOn(clock, fake) { budget = recording }.use { fetcher ->
                assertEquals(RULE_COST, ruleOf(fetcher.fetch(request) { cost = 0 }))
                assertEquals(RULE_COST, ruleOf(fetcher.fetch(request) { cost = -1 }))
                assertTrue(fake.calls.isEmpty())
                assertTrue(recording.permits.isEmpty())
            }
        }
    }

    @Test
    fun `a negative maxWait is InvalidRequest`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            fetcherOn(clock, fake) { budget = recording }.use { fetcher ->
                assertEquals(RULE_MAX_WAIT, ruleOf(fetcher.fetch(request) { maxWait = (-1).seconds }))
                // The cost is checked first.
                assertEquals(RULE_COST, ruleOf(fetcher.fetch(request) { cost = 0; maxWait = (-1).seconds }))
                assertTrue(fake.calls.isEmpty())
                assertTrue(recording.permits.isEmpty())
            }
        }
    }

    @Test
    fun `a header the transport controls is InvalidRequest`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
        val controlled = HttpRequest(HttpMethod.Get, FETCH_URL) { headers = HttpHeaders { add("Accept", "text/plain"); add("User-Agent", "mine") } }
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            fetcherOn(clock, fake) { budget = recording }.use { fetcher ->
                assertEquals("header User-Agent is controlled by the transport", ruleOf(fetcher.fetch(controlled)))
                assertTrue(fake.calls.isEmpty())
                assertTrue(recording.permits.isEmpty())
            }
        }
    }

    @Test
    fun `an exception thrown by the lambda is InvalidRequest`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
        val thrown = IllegalStateException("secret")
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            fetcherOn(clock, fake) { budget = recording }.use { fetcher ->
                val failure = assertIs<TransportFailure.InvalidRequest>(fetcher.fetch(request) { throw thrown }.exceptionOrNull())

                assertEquals(RULE_SETTINGS, failure.rule)
                assertSame(thrown, failure.cause)
                assertFalse("secret" in failure.message!!, failure.message)
                assertTrue(fake.calls.isEmpty())
                assertTrue(recording.permits.isEmpty())
            }
        }
    }

    @Test
    fun `BudgetExhausted comes back when the wait exceeds maxWait`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
        budgetOn(clock, minuteWindow(1)).use { real ->
            val recording = PermitRecordingBudget(real)
            fetcherOn(clock, fake) { budget = recording }.use { fetcher ->
                assertTrue(fetcher.fetch(request).isSuccess)                  // the window's one unit

                val exhausted = assertIs<HttpFailure.BudgetExhausted>(fetcher.fetch(request) { maxWait = Duration.ZERO }.exceptionOrNull())
                assertEquals(ExhaustedReason.Window, exhausted.reason)
                assertEquals(1, fake.calls.size)
                assertEquals(1, recording.permits.size)
            }
        }
    }

    @Test
    fun `defaultMaxWait is used when the spec does not set maxWait`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
        clock.advanceBy(57.seconds)                                       // 3 seconds before the minute window's next boundary
        budgetOn(clock, minuteWindow(1)).use { shared ->
            fetcherOn(clock, fake) { budget = shared; defaultMaxWait = 1.seconds }.use { impatient ->
                assertTrue(impatient.fetch(request).isSuccess)            // the window's one unit

                val exhausted = assertIs<HttpFailure.BudgetExhausted>(impatient.fetch(request).exceptionOrNull())
                assertEquals(3.seconds, exhausted.wait)
                assertEquals(1, fake.calls.size)
            }
            fetcherOn(clock, fake) { budget = shared; defaultMaxWait = 5.seconds }.use { patient ->
                val waiting = async { patient.fetch(request) }
                clock.advanceBy(Duration.ZERO)
                assertFalse(waiting.isCompleted)
                clock.advanceBy(3.seconds)
                assertTrue(waiting.isCompleted)
                assertEquals(200, waiting.await().getOrThrow().status)
                assertEquals(2, fake.calls.size)
            }
        }
    }

    @Test
    fun `concurrent fetches of one URL are not coalesced`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(200, delay = 1.seconds))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            fetcherOn(clock, fake) { budget = recording }.use { fetcher ->
                val fetches = List(4) { async { fetcher.fetch(request) } }
                clock.advanceBy(2.seconds)

                assertTrue(fetches.all { it.isCompleted })
                fetches.forEach { assertEquals(200, it.await().getOrThrow().status) }
                assertEquals(4, fake.calls.size)
                assertEquals(4, recording.permits.size)
            }
        }
    }

    @Test
    fun `an InvalidRequest is logged once at DEBUG with its rule and no value`() {
        val events = captureTransportLogs {
            runTest {
                val clock = virtualClock()
                val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
                val url = Url.parse("https://files.example.test/a?token=SECRET-QUERY").getOrThrow()
                val controlled = HttpRequest(HttpMethod.Get, url) { headers = HttpHeaders { add("User-Agent", "SECRET-HEADER") } }
                fetcherOn(clock, fake).use { fetcher ->
                    assertEquals(RULE_COST, ruleOf(fetcher.fetch(HttpRequest(HttpMethod.Get, url)) { cost = 0 }))
                    assertEquals("header User-Agent is controlled by the transport", ruleOf(fetcher.fetch(controlled)))
                    assertTrue(fake.calls.isEmpty())
                }
            }
        }.second

        val lines = facadeEvents(events, "UrlFetcher")
        assertEquals(
            listOf("invalid request: rule=$RULE_COST", "invalid request: rule=header User-Agent is controlled by the transport"),
            lines.filter { it.level == Level.DEBUG }.map { it.formattedMessage },
        )
        // Only the creation and close lines beside them: an invalid request has no per-fetch line and no warning.
        assertEquals(4, lines.size, lines.map { it.formattedMessage }.toString())
        assertFalse(lines.any { "SECRET" in it.formattedMessage }, lines.map { it.formattedMessage }.toString())
    }
}
