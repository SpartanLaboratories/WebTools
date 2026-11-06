package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.PermitRecordingBudget
import com.spartanlabs.testing.support.webtools.http.assertNothingSpent
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.clientOn
import com.spartanlabs.testing.support.webtools.http.facadeEvents
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Level 2 - the pre-check of `UpstreamClient.call`, with a cache and a recorded budget: under an `Untrusted` policy a sensitive per-call header is
 * `RefusedByPolicy(SensitiveHeader)` and costs nothing (no transport call, no permit, nothing cached), every time it is tried; the refusal is logged
 * once at `WARN` with the endpoint id, the reason name and the redacted URL, and no header value; under the default `Trusted` policy the same call
 * is sent with its sensitive header.
 */
class UpstreamClientPolicyRefusalComponentTest {

    // What a test needs to drive one client: the fake transport, the recorded budget and the client.
    private class Rig(val fake: FakeHttpTransport, val budget: PermitRecordingBudget, val client: UpstreamClient)

    // A client with a cache on the clock and a recorded budget, under `policy` (the client's default when null); the fake answers every request with a 200.
    private fun rigged(policy: OutboundUrlPolicy? = OutboundUrlPolicy.untrusted(), block: suspend TestScope.(Rig) -> Unit): TestResult = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "ok"))
        // A local under another name, so the configuration's `policy` and the parameter are told apart at a glance.
        val chosen = policy
        cacheOn(clock).use { responseCache ->
            budgetOn(clock, minuteWindow(10)).use { real ->
                val recording = PermitRecordingBudget(real)
                clientOn(clock, fake) {
                    cache(responseCache)
                    budget(recording)
                    if (chosen != null) this.policy = chosen
                }.use { client -> block(Rig(fake, recording, client)) }
            }
        }
    }

    private fun refused(result: Result<*>): TransportFailure.RefusedByPolicy = assertIs<TransportFailure.RefusedByPolicy>(result.exceptionOrNull())

    @Test
    fun `a sensitive per-call header under an Untrusted policy is RefusedByPolicy and costs nothing`() = rigged { rig ->
        val failure = refused(rig.client.call(stringEndpoint { ttl = 10.minutes }) { headers { addSensitive("X-Key", "v") } })

        assertEquals(RefusalReason.SensitiveHeader, failure.reason)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `a second identical call is refused again and nothing is cached`() = rigged { rig ->
        val endpoint = stringEndpoint { ttl = 10.minutes }

        repeat(2) { assertEquals(RefusalReason.SensitiveHeader, refused(rig.client.call(endpoint) { headers { addSensitive("X-Key", "v") } }).reason) }

        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `the refusal is logged once at WARN with the reason name and a redacted URL`() {
        val events = captureTransportLogs {
            rigged { rig ->
                refused(rig.client.call(stringEndpoint()) { path("p"); query("token", "SECRET-QUERY"); headers { addSensitive("X-Key", "SECRET-HEADER") } })
            }
        }.second

        val lines = facadeEvents(events, "UpstreamClient")
        assertEquals(
            listOf("refused by policy: endpoint=hero, reason=SensitiveHeader, rangeClass=-, url=https://api.example.test/v1/p?..."),
            lines.filter { it.level == Level.WARN }.map { it.formattedMessage },
        )
        assertFalse(lines.any { "SECRET" in it.formattedMessage }, lines.map { it.formattedMessage }.toString())
        // A refused call never reaches the cache step, so it has no per-call line.
        assertFalse(lines.any { it.formattedMessage.startsWith("call:") }, lines.map { it.formattedMessage }.toString())
    }

    @Test
    fun `a Trusted default policy lets a sensitive per-call header through`() = rigged(policy = null) { rig ->
        val lookup = rig.client.call(stringEndpoint()) { headers { addSensitive("X-Key", "v") } }.getOrThrow()

        assertEquals(Lookup.Found("ok"), lookup)
        val entry = rig.fake.calls.single().request.headers.entries().single { it.name == "X-Key" }
        assertEquals("v", entry.value)
        assertTrue(entry.isSensitive)
    }
}
