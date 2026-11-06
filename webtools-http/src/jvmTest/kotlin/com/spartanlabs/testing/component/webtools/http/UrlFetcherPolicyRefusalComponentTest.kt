package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.FETCH_URL
import com.spartanlabs.testing.support.webtools.http.PermitRecordingBudget
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.facadeEvents
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 2 - the pre-check of `UrlFetcher.fetch` over the fake transport: under an `Untrusted` policy a private IP literal, a disallowed port and a
 * sensitive header are each `RefusedByPolicy` with their reason, and a `Trusted` `https`-only policy refuses cleartext; a refusal takes no permit
 * and makes no transport call; it is logged once at `WARN` with the reason name, the range class and the redacted URL.
 */
class UrlFetcherPolicyRefusalComponentTest {

    private fun get(url: String, headers: HttpHeaders = HttpHeaders()): HttpRequest {
        val requestHeaders = headers                  // the builder's own `headers` would shadow the parameter inside its lambda
        return HttpRequest(HttpMethod.Get, Url.parse(url).getOrThrow()) { this.headers = requestHeaders }
    }

    private fun reasonOf(result: Result<*>): RefusalReason = assertIs<TransportFailure.RefusedByPolicy>(result.exceptionOrNull()).reason

    @Test
    fun `a private IP literal is RefusedByPolicy with DeniedAddress and costs nothing`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            UrlFetcher(fake, OutboundUrlPolicy.untrusted()) { budget = recording; timeSource = clock.timeSource; wallClock = clock.wallClock }.use { fetcher ->
                assertEquals(RefusalReason.DeniedAddress, reasonOf(fetcher.fetch(get("http://10.0.0.5/"))))
                assertTrue(fake.calls.isEmpty())
                assertTrue(recording.permits.isEmpty())
                assertEquals(0, real.utilisation().windows.single().used)
            }
        }
    }

    @Test
    fun `a disallowed port is refused`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            UrlFetcher(fake, OutboundUrlPolicy.untrusted()) { budget = recording; timeSource = clock.timeSource; wallClock = clock.wallClock }.use { fetcher ->
                assertEquals(RefusalReason.Port, reasonOf(fetcher.fetch(get("https://files.example.test:9999/"))))
                assertTrue(fake.calls.isEmpty())
                assertTrue(recording.permits.isEmpty())
            }
        }
    }

    @Test
    fun `a sensitive header is refused`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            UrlFetcher(fake, OutboundUrlPolicy.untrusted()) { budget = recording; timeSource = clock.timeSource; wallClock = clock.wallClock }.use { fetcher ->
                val request = get(FETCH_URL.toString(), HttpHeaders { addSensitive("X-Key", "v") })
                assertEquals(RefusalReason.SensitiveHeader, reasonOf(fetcher.fetch(request)))
                assertTrue(fake.calls.isEmpty())
                assertTrue(recording.permits.isEmpty())

                // The same URL without the header is fetched.
                assertEquals(200, fetcher.fetch(get(FETCH_URL.toString())).getOrThrow().status)
                assertEquals(1, fake.calls.size)
            }
        }
    }

    @Test
    fun `a Trusted https-only policy refuses cleartext`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val recording = PermitRecordingBudget(real)
            UrlFetcher(fake, OutboundUrlPolicy.trusted()) { budget = recording; timeSource = clock.timeSource; wallClock = clock.wallClock }.use { fetcher ->
                assertEquals(RefusalReason.Scheme, reasonOf(fetcher.fetch(get("http://files.example.test/"))))
                assertTrue(fake.calls.isEmpty())
                assertTrue(recording.permits.isEmpty())
            }
        }
    }

    @Test
    fun `the refusal is logged once at WARN with the reason, the range class and a redacted URL`() {
        val events = captureTransportLogs {
            runTest {
                val clock = virtualClock()
                val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200))
                UrlFetcher(fake, OutboundUrlPolicy.untrusted()) { timeSource = clock.timeSource; wallClock = clock.wallClock }.use { fetcher ->
                    fetcher.fetch(get("http://10.0.0.5/"))
                    fetcher.fetch(get("http://10.0.0.5/x?token=SECRET"))
                    fetcher.fetch(get("https://files.example.test:9999/"))
                }
            }
        }.second

        val lines = facadeEvents(events, "UrlFetcher")
        assertEquals(
            listOf(
                "refused by policy: reason=DeniedAddress, rangeClass=private-use, url=http://10.0.0.5/",
                "refused by policy: reason=DeniedAddress, rangeClass=private-use, url=http://10.0.0.5/x?...",
                "refused by policy: reason=Port, rangeClass=-, url=https://files.example.test:9999/",
            ),
            lines.filter { it.level == Level.WARN }.map { it.formattedMessage },
        )
        assertFalse(lines.any { "SECRET" in it.formattedMessage }, lines.map { it.formattedMessage }.toString())
        // A refused fetch never reaches the loader, so it has no per-fetch line.
        assertFalse(lines.any { it.formattedMessage.startsWith("fetch:") })
    }
}
