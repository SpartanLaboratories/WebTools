package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.clientOn
import com.spartanlabs.testing.support.webtools.http.facadeEvents
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.RULE_DOT_SEGMENT
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Level 2 - what `UpstreamClient` logs on its own logger, captured at `DEBUG`: one `INFO` line on creation (the redacted base URL, the kinds, the
 * ownership and `defaultMaxWait`) and one on `close()` however often it is called; one `DEBUG` line per call (`loaded=true` with the wait, status
 * and size when this call's loader ran, `loaded=false` and dashes for a cache hit, `outcome=Missing` for a declared miss, dashes for a call whose
 * transport failed); `budget=custom` for a budget of an anonymous class; one `WARN` per throttle; one `DEBUG` per `InvalidRequest` (and no
 * per-call line for it) and per `DecodeFailure`; no line carries a header value, a query string, a body, a credential or a decoded value.
 */
class UpstreamClientLoggingComponentTest {

    // Runs `block` on a virtual clock and returns what the client's logger emitted.
    private fun captured(block: suspend TestScope.(VirtualClock) -> Unit): List<ILoggingEvent> =
        facadeEvents(captureTransportLogs { runTest { block(virtualClock()) } }.second, "UpstreamClient")

    private fun List<ILoggingEvent>.at(level: Level): List<String> = filter { it.level == level }.map { it.formattedMessage }

    @Test
    fun `creation logs one INFO line with the redacted base URL, kinds, ownership and defaultMaxWait`() {
        val events = captured { clock -> clientOn(clock, FakeHttpTransport(clock.timeSource)) }

        assertEquals(
            listOf("created: baseUrl=https://api.example.test/v1/, policy=Trusted, cache=Disabled, budget=Unlimited, owned=[], defaultMaxWait=5s"),
            events.at(Level.INFO),
        )
    }

    @Test
    fun `a call logs one DEBUG line with the endpoint id, method, loaded, lane, cost and outcome`() {
        val events = captured { clock ->
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "Ada"))
            clientOn(clock, fake).use { client -> client.call(stringEndpoint()).getOrThrow() }
        }

        assertEquals(
            listOf("call: endpoint=hero, method=GET, loaded=true, lane=Interactive, cost=1, waitedMs=0, status=200, bytes=3, elapsedMs=0, outcome=Found"),
            events.at(Level.DEBUG),
        )
    }

    @Test
    fun `a cache hit logs loaded=false and no status`() {
        val events = captured { clock ->
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "Ada"))
            cacheOn(clock).use { responseCache ->
                clientOn(clock, fake) { cache(responseCache) }.use { client ->
                    val endpoint = stringEndpoint { ttl = 10.minutes }
                    client.call(endpoint).getOrThrow()
                    client.call(endpoint).getOrThrow()
                }
            }
        }

        assertEquals(
            listOf(
                "call: endpoint=hero, method=GET, loaded=true, lane=Interactive, cost=1, waitedMs=0, status=200, bytes=3, elapsedMs=0, outcome=Found",
                "call: endpoint=hero, method=GET, loaded=false, lane=Interactive, cost=1, waitedMs=-, status=-, bytes=-, elapsedMs=0, outcome=Found",
            ),
            events.at(Level.DEBUG),
        )
    }

    @Test
    fun `a throttle logs one WARN with the endpoint id, status and retryAfter`() {
        val events = captured { clock ->
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.respond(429, HttpHeaders { add("Retry-After", "30") }))
            clientOn(clock, fake) { budget(listOf(RateWindow("minute", 60, 1.minutes))) }.use { client -> client.call(stringEndpoint()) }
        }

        assertEquals(listOf("throttled: subject=hero, status=429, retryAfter=30s"), events.at(Level.WARN))
        assertEquals(
            listOf("call: endpoint=hero, method=GET, loaded=true, lane=Interactive, cost=1, waitedMs=0, status=429, bytes=0, elapsedMs=0, outcome=RateLimited"),
            events.at(Level.DEBUG),
        )
    }

    @Test
    fun `an InvalidRequest logs one DEBUG with the rule`() {
        val events = captured { clock ->
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "Ada"))
            clientOn(clock, fake).use { client -> client.call(stringEndpoint()) { path("..") } }
        }

        assertEquals(listOf("invalid request: endpoint=hero, rule=$RULE_DOT_SEGMENT"), events.at(Level.DEBUG))
        assertFalse(events.any { it.formattedMessage.startsWith("call:") })
    }

    @Test
    fun `a DecodeFailure logs one DEBUG with the cause's class name`() {
        val events = captured { clock ->
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "Ada"))
            val endpoint = Endpoint<String>("hero", { Result.failure(IllegalStateException("secret")) })
            clientOn(clock, fake).use { client -> client.call(endpoint) }
        }

        assertEquals(
            listOf(
                "decode failed: endpoint=hero, cause=IllegalStateException",
                "call: endpoint=hero, method=GET, loaded=true, lane=Interactive, cost=1, waitedMs=0, status=200, bytes=3, elapsedMs=0, outcome=DecodeFailure",
            ),
            events.at(Level.DEBUG),
        )
        assertFalse(events.any { "secret" in it.formattedMessage })
    }

    @Test
    fun `a declared miss logs outcome Missing with its status`() {
        val events = captured { clock ->
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(404))
            clientOn(clock, fake).use { client -> client.call(stringEndpoint { misses(404) }).getOrThrow() }
        }

        assertEquals(
            listOf("call: endpoint=hero, method=GET, loaded=true, lane=Interactive, cost=1, waitedMs=0, status=404, bytes=0, elapsedMs=0, outcome=Missing"),
            events.at(Level.DEBUG),
        )
    }

    @Test
    fun `a call whose transport fails logs dashes for the wait, the status and the size`() {
        val events = captured { clock ->
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, Step.fail(TransportFailure.Timeout()))
            clientOn(clock, fake).use { client -> client.call(stringEndpoint()) }
        }

        // The loader ran (loaded=true) but nothing came back: no response to take a wait, a status or a size from.
        assertEquals(
            listOf("call: endpoint=hero, method=GET, loaded=true, lane=Interactive, cost=1, waitedMs=-, status=-, bytes=-, elapsedMs=0, outcome=Timeout"),
            events.at(Level.DEBUG),
        )
        assertTrue(events.at(Level.WARN).isEmpty(), events.at(Level.WARN).toString())
    }

    @Test
    @OptIn(ExperimentalForInheritanceWebToolsApi::class)
    fun `a client over an anonymous budget logs its kind as custom`() {
        var text = ""
        val events = captured { clock ->
            val anonymous = object : CallBudget by CallBudget.Unlimited {}
            clientOn(clock, FakeHttpTransport(clock.timeSource)) { budget(anonymous) }.use { client -> text = client.toString() }
        }

        assertEquals(
            "created: baseUrl=https://api.example.test/v1/, policy=Trusted, cache=Disabled, budget=custom, owned=[], defaultMaxWait=5s",
            events.at(Level.INFO).first(),
        )
        assertTrue("budget=custom" in text, text)
    }

    @Test
    fun `close logs one INFO closed`() {
        val events = captured { clock ->
            val client = clientOn(clock, FakeHttpTransport(clock.timeSource))
            client.close()
            client.close()
        }

        assertEquals(1, events.at(Level.INFO).count { it == "closed" })
    }

    @Test
    fun `no line carries a header value, a query string, a body, a credential or a decoded value`() {
        val events = captured { clock ->
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "SECRET-BODY"))
            val endpoint = Endpoint("hero", { response -> Result.success(response.body().decodeToString().replace("BODY", "VALUE")) }) { ttl = 10.minutes }
            cacheOn(clock).use { responseCache ->
                clientOn(clock, fake) {
                    cache(responseCache)
                    headers = HttpHeaders { addSensitive("Authorization", "SECRET-HEADER") }
                }.use { client ->
                    repeat(2) {
                        assertEquals(Lookup.Found("SECRET-VALUE"), client.call(endpoint) { query("token", "SECRET-QUERY") }.getOrThrow())
                    }
                    client.call(endpoint) { path(".."); query("token", "SECRET-QUERY") }
                }
            }
        }

        assertTrue(events.size >= 5, events.map { it.formattedMessage }.toString())          // created, two calls, an invalid request, closed
        listOf("SECRET-HEADER", "SECRET-QUERY", "SECRET-BODY", "SECRET-VALUE", "SECRET").forEach { secret ->
            assertFalse(events.any { secret in it.formattedMessage }, events.map { it.formattedMessage }.toString())
        }
    }
}
