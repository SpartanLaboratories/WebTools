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
import com.spartanlabs.webtools.http.CallSpec
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RULE_DOT_SEGMENT
import com.spartanlabs.webtools.http.RULE_EMPTY_SEGMENT
import com.spartanlabs.webtools.http.RULE_MAX_AGE
import com.spartanlabs.webtools.http.RULE_MAX_WAIT
import com.spartanlabs.webtools.http.RULE_QUERY_UNICODE
import com.spartanlabs.webtools.http.RULE_REQUEST
import com.spartanlabs.webtools.http.RULE_SEGMENT_UNICODE
import com.spartanlabs.webtools.http.RULE_SETTINGS
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.RequestBody
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
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - what `UpstreamClient.call` does with a call's settings before anything is spent, with a cache and a recorded budget: a dot, double-dot
 * or empty segment, an unpaired surrogate, a negative `maxWait` or `maxAge`, a header the transport controls, an invalid header name, an exception
 * thrown by the lambda and a body on a `GET` are each `InvalidRequest` with a constant rule, and cost nothing (no transport call, no permit, nothing
 * cached); an absolute URL, a double-slash host form or reserved characters passed as a segment are sent as one encoded segment on the base host
 * (the published bypass of an absolute URL passed as a path argument); an `InvalidRequest` is logged once at `DEBUG` with its rule and no value; of
 * two faults in one call the earlier check decides, in the order `maxWait`, `maxAge`, the URL, a controlled header, the request build, the policy.
 */
class UpstreamClientRequestComponentTest {

    // What a test needs to drive one client: the fake transport, the recorded budget and the client.
    private class Rig(val fake: FakeHttpTransport, val budget: PermitRecordingBudget, val client: UpstreamClient)

    // A client with a cache on the clock and a recorded budget, under `policy` (the client's default when null); the fake answers every request with a 200.
    private fun rigged(policy: OutboundUrlPolicy? = null, block: suspend TestScope.(Rig) -> Unit): TestResult = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "ok"))
        cacheOn(clock).use { responseCache ->
            budgetOn(clock, minuteWindow(10)).use { real ->
                val recording = PermitRecordingBudget(real)
                clientOn(clock, fake) {
                    cache(responseCache)
                    budget(recording)
                    if (policy != null) this.policy = policy
                }.use { client -> block(Rig(fake, recording, client)) }
            }
        }
    }

    private fun invalid(result: Result<*>): TransportFailure.InvalidRequest = assertIs<TransportFailure.InvalidRequest>(result.exceptionOrNull())

    @Test
    fun `a dot segment is InvalidRequest`() = rigged { rig ->
        assertEquals(RULE_DOT_SEGMENT, invalid(rig.client.call(stringEndpoint()) { path("a", ".") }).rule)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `a double-dot segment is InvalidRequest`() = rigged { rig ->
        assertEquals(RULE_DOT_SEGMENT, invalid(rig.client.call(stringEndpoint()) { path("..") }).rule)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `an empty segment is InvalidRequest`() = rigged { rig ->
        assertEquals(RULE_EMPTY_SEGMENT, invalid(rig.client.call(stringEndpoint()) { path("a", "") }).rule)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `an unpaired surrogate in a segment is InvalidRequest`() = rigged { rig ->
        assertEquals(RULE_SEGMENT_UNICODE, invalid(rig.client.call(stringEndpoint()) { path("\uD800") }).rule)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `an unpaired surrogate in a query name or value is InvalidRequest`() = rigged { rig ->
        assertEquals(RULE_QUERY_UNICODE, invalid(rig.client.call(stringEndpoint()) { query("\uD800", "v") }).rule)
        assertEquals(RULE_QUERY_UNICODE, invalid(rig.client.call(stringEndpoint()) { query("n", "\uDC00") }).rule)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `a negative maxWait is InvalidRequest`() = rigged { rig ->
        // The budget would have thrown IllegalArgumentException; the call never reaches it.
        assertEquals(RULE_MAX_WAIT, invalid(rig.client.call(stringEndpoint()) { maxWait = (-1).seconds }).rule)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `a negative maxAge is InvalidRequest`() = rigged { rig ->
        // The cache would have thrown IllegalArgumentException; the call never reaches it.
        assertEquals(RULE_MAX_AGE, invalid(rig.client.call(stringEndpoint()) { maxAge = (-1).seconds }).rule)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `a header the transport controls is InvalidRequest`() = rigged { rig ->
        assertEquals(
            "header Host is controlled by the transport",
            invalid(rig.client.call(stringEndpoint()) { headers { add("Host", "evil") } }).rule,
        )
        assertEquals(
            "header User-Agent is controlled by the transport",
            invalid(rig.client.call(stringEndpoint()) { headers { set("user-agent", "mine") } }).rule,
        )
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `an invalid header name inside headers is InvalidRequest`() = rigged { rig ->
        val failure = invalid(rig.client.call(stringEndpoint()) { headers { add("bad name", "v") } })

        assertEquals(RULE_SETTINGS, failure.rule)
        assertIs<IllegalArgumentException>(failure.cause)
        assertFalse("bad name" in failure.rule, failure.rule)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `an exception thrown by the lambda is InvalidRequest`() = rigged { rig ->
        val thrown = IllegalStateException("secret")

        val failure = invalid(rig.client.call(stringEndpoint()) { throw thrown })

        assertEquals(RULE_SETTINGS, failure.rule)
        assertSame(thrown, failure.cause)
        assertFalse("secret" in failure.message!!, failure.message)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `a body on a GET is InvalidRequest`() = rigged { rig ->
        val failure = invalid(rig.client.call(stringEndpoint()) { body = RequestBody("abc".encodeToByteArray(), "text/plain") })

        assertEquals(RULE_REQUEST, failure.rule)
        assertIs<IllegalArgumentException>(failure.cause)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }

    @Test
    fun `an absolute URL as a segment stays one encoded segment on the base host`() = rigged { rig ->
        assertEquals(Lookup.Found("ok"), rig.client.call(stringEndpoint()) { path("https://evil.example.test/x") }.getOrThrow())

        val url = rig.fake.calls.single().request.url
        assertEquals("https://api.example.test/v1/https:%2F%2Fevil.example.test%2Fx", url.toString())
        assertEquals("api.example.test", url.host)
    }

    @Test
    fun `a double-slash host form as a segment stays one encoded segment`() = rigged { rig ->
        assertEquals(Lookup.Found("ok"), rig.client.call(stringEndpoint()) { path("//evil.example.test") }.getOrThrow())

        val url = rig.fake.calls.single().request.url
        assertEquals("https://api.example.test/v1/%2F%2Fevil.example.test", url.toString())
        assertEquals("api.example.test", url.host)
    }

    @Test
    fun `a segment with a slash, a question mark and a percent is encoded`() = rigged { rig ->
        assertEquals(Lookup.Found("ok"), rig.client.call(stringEndpoint()) { path("a/b?c%d") }.getOrThrow())

        val url = rig.fake.calls.single().request.url
        assertEquals("/v1/a%2Fb%3Fc%25d", url.encodedPath)
        assertEquals(null, url.encodedQuery)
    }

    @Test
    fun `an InvalidRequest is logged once at DEBUG with its rule and no value`() {
        val events = captureTransportLogs {
            rigged { rig ->
                invalid(rig.client.call(stringEndpoint()) { path("a", "."); query("token", "SECRET") })
                assertNothingSpent(rig.fake, rig.budget, rig.client)
            }
        }.second

        val lines = facadeEvents(events, "UpstreamClient")
        assertEquals(
            listOf("invalid request: endpoint=hero, rule=$RULE_DOT_SEGMENT"),
            lines.filter { it.level == Level.DEBUG }.map { it.formattedMessage },
        )
        assertFalse(lines.any { "SECRET" in it.formattedMessage }, lines.map { it.formattedMessage }.toString())
    }

    // One row of the order table: a call with two faults, and the rule of the fault whose check runs first.
    private class OrderRow(val label: String, val rule: String, val configure: CallSpec.() -> Unit)

    @Test
    fun `the checks before anything is spent run in a fixed order`() = rigged(OutboundUrlPolicy.untrusted()) { rig ->
        val payload = RequestBody("abc".encodeToByteArray(), "text/plain")
        val rows = listOf(
            OrderRow("maxWait before maxAge", RULE_MAX_WAIT) { maxWait = (-1).seconds; maxAge = (-1).seconds },
            OrderRow("the limits before the URL", RULE_MAX_AGE) { maxAge = (-1).seconds; path("..") },
            OrderRow("the URL before a controlled header", RULE_DOT_SEGMENT) { path(".."); headers { add("Host", "evil") } },
            OrderRow("a controlled header before the request build", "header Host is controlled by the transport") {
                headers { add("Host", "evil") }
                body = payload
            },
            OrderRow("an invalid request before the policy's refusal", RULE_REQUEST) { body = payload; headers { addSensitive("X-Key", "v") } },
        )
        rows.forEach { row -> assertEquals(row.rule, invalid(rig.client.call(stringEndpoint(), row.configure)).rule, row.label) }

        // The last row's second fault alone is the policy's refusal, so that row shows the order and not a refusal that never happens.
        val refused = assertIs<TransportFailure.RefusedByPolicy>(rig.client.call(stringEndpoint()) { headers { addSensitive("X-Key", "v") } }.exceptionOrNull())
        assertEquals(RefusalReason.SensitiveHeader, refused.reason)
        assertNothingSpent(rig.fake, rig.budget, rig.client)
    }
}
