package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.BASE_URL
import com.spartanlabs.testing.support.webtools.http.CloseLog
import com.spartanlabs.testing.support.webtools.http.LoggedBudget
import com.spartanlabs.testing.support.webtools.http.LoggedCache
import com.spartanlabs.testing.support.webtools.http.LoggedTransport
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.cacheOn
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.clientOn
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.testing.support.webtools.http.facadeEvents
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.ClientParts
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.Owned
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.UpstreamClientConfig
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - the lifecycle of `UpstreamClient`, built through the `internal` parts so each component is a logged delegate and the ownership flags are
 * stated: `close()` closes the owned components in the order cache, budget, transport, never one the client was handed, each once however often it
 * is called, and a step that throws is logged at `DEBUG` while the others still run; a call after `close()` returns `Closed("client")` and reaches
 * nothing, while `utilisation()`, `cacheStats()` and `flush()` keep working; `flush()` writes an owned budget's exact counts to its store, before
 * and after `close()`; a call in flight when `close()` runs ends with a component's `Closed`; `toString()` names the kinds and the ownership and
 * never a header value.
 */
class UpstreamClientLifecycleComponentTest {

    // The parts of one client and the order in which it closed them.
    private class Rig(
        val clock: VirtualClock,
        val fake: FakeHttpTransport,
        val cache: CaffeineResponseCache,
        val budget: WindowedCallBudget,
        val log: CloseLog,
        val client: UpstreamClient,
    )

    // A client built as an owning factory builds one, over logged delegates; the cache and the budget are closed again when the test ends.
    private fun rigged(owned: Owned, step: Step = respondWith(200, "ok"), budgetFailure: Exception? = null, block: suspend TestScope.(Rig) -> Unit): TestResult = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, step)
        val log = CloseLog()
        val resolved = UpstreamClientConfig(BASE_URL).apply { timeSource = clock.timeSource; wallClock = clock.wallClock; dispatcher = clock.dispatcher }.resolve()
        cacheOn(clock).use { cache ->
            budgetOn(clock, minuteWindow(10)).use { budget ->
                val parts = ClientParts(resolved, LoggedTransport(fake, log), LoggedCache(cache, log), LoggedBudget(budget, log, budgetFailure), owned)
                UpstreamClient(parts).use { client -> block(Rig(clock, fake, cache, budget, log, client)) }
            }
        }
    }

    private val everything = Owned(cache = true, budget = true, transport = true)

    @Test
    fun `close closes the owned components in the order cache, budget, transport`() = rigged(everything) { rig ->
        rig.client.close()

        assertEquals(listOf("cache", "budget", "transport"), rig.log.names)
        assertTrue(rig.fake.isClosed)
    }

    @Test
    fun `close never closes a component the client was handed`() = rigged(Owned.None) { rig ->
        rig.client.close()

        assertTrue(rig.log.names.isEmpty())
        // The supplied components still work.
        assertFalse(rig.fake.isClosed)
        assertTrue(rig.budget.acquire(1, Lane.Tracker, Duration.ZERO).isSuccess)
        val stored = rig.cache.getOrLoad(CacheKey<String>("k"), CachePolicy(1.minutes)) { Result.success(Lookup.Found("v")) }
        assertEquals(Lookup.Found("v"), stored.getOrThrow())
    }

    @Test
    fun `close is idempotent and closes each owned component once`() {
        var names: List<String> = emptyList()
        val events = captureTransportLogs {
            rigged(everything) { rig ->
                repeat(3) { rig.client.close() }
                names = rig.log.names
            }
        }.second

        assertEquals(listOf("cache", "budget", "transport"), names)
        val closed = facadeEvents(events, "UpstreamClient").filter { it.level == Level.INFO && it.formattedMessage == "closed" }
        assertEquals(1, closed.size)
    }

    @Test
    fun `a close step that throws is skipped and the others still run`() {
        var names: List<String> = emptyList()
        val events = captureTransportLogs {
            rigged(everything, budgetFailure = IllegalStateException("secret")) { rig ->
                rig.client.close()                                                // returns normally
                names = rig.log.names
                assertTrue(rig.fake.isClosed)                                     // the transport still closed
            }
        }.second

        assertEquals(listOf("cache", "budget", "transport"), names)
        val lines = facadeEvents(events, "UpstreamClient")
        assertEquals(listOf("close step budget failed: IllegalStateException"), lines.filter { it.level == Level.DEBUG }.map { it.formattedMessage })
        assertEquals(1, lines.count { it.level == Level.INFO && it.formattedMessage == "closed" })
        assertFalse(lines.any { "secret" in it.formattedMessage })
    }

    @Test
    fun `a call after close returns Closed client and reaches nothing`() = rigged(Owned.None) { rig ->
        rig.client.close()

        val closed = assertIs<HttpFailure.Closed>(rig.client.call(stringEndpoint()).exceptionOrNull())

        assertEquals("client", closed.component)
        assertTrue(rig.fake.calls.isEmpty())
        assertEquals(CacheStats.Empty, rig.client.cacheStats())
    }

    @Test
    fun `utilisation, cacheStats and flush keep working after close`() = rigged(everything) { rig ->
        rig.client.call(stringEndpoint { ttl = 10.minutes }).getOrThrow()
        rig.client.close()

        assertEquals(1, rig.client.utilisation().windows.single().used)
        assertEquals(1L, rig.client.cacheStats().misses)
        assertTrue(rig.client.flush().isSuccess)
    }

    @Test
    fun `flush reaches the owned budget's store before close`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "ok"))
        val store = FakeBudgetStore()
        clientOn(clock, fake) { budget(listOf(dayWindow(100))) { this.store = store } }.use { client ->
            client.call(stringEndpoint()).getOrThrow()

            assertTrue(client.flush().isSuccess)

            assertTrue(store.saves.isNotEmpty())
            assertEquals(1, store.saves.last().windows.single().used)          // the exact count, not a write-ahead block
        }
    }

    @Test
    fun `flush also works after close`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "ok"))
        val store = FakeBudgetStore()
        val client = clientOn(clock, fake) { budget(listOf(dayWindow(100))) { this.store = store } }
        client.call(stringEndpoint()).getOrThrow()
        client.close()
        val before = store.saves.size

        assertTrue(client.flush().isSuccess)

        assertTrue(store.saves.size > before)
        assertEquals(1, store.saves.last().windows.single().used)
    }

    @Test
    fun `a call in flight when close runs ends with a Closed from a component`() = rigged(everything, step = Step.respond(200, delay = 10.seconds)) { rig ->
        val call = async { rig.client.call(stringEndpoint()) }
        rig.clock.advanceBy(1.seconds)                                           // the load holds its permit and waits in the fake
        assertEquals(1, rig.fake.calls.size)

        rig.client.close()
        rig.clock.advanceBy(Duration.ZERO)

        assertTrue(call.isCompleted)                                             // nothing hangs
        val closed = assertIs<HttpFailure.Closed>(call.await().exceptionOrNull())
        assertEquals("cache", closed.component)                                  // the cache closes first
    }

    @Test
    fun `toString names the kinds and the ownership and never a header value`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource)

        val plain = clientOn(clock, fake) { headers = HttpHeaders { addSensitive("Authorization", "SECRET") } }
        assertEquals(
            "UpstreamClient(baseUrl=https://api.example.test/v1/, policy=Trusted, cache=Disabled, budget=Unlimited, owned=[], closed=false)",
            plain.toString(),
        )
        plain.close()
        assertTrue("closed=true" in plain.toString(), plain.toString())
        assertFalse("SECRET" in plain.toString(), plain.toString())

        cacheOn(clock).use { responseCache ->
            clientOn(clock, fake) { cache(responseCache); budget(listOf(RateWindow("minute", 60, 1.minutes))) }.use { owning ->
                assertEquals(
                    "UpstreamClient(baseUrl=https://api.example.test/v1/, policy=Trusted, cache=CaffeineResponseCache, budget=WindowedCallBudget, owned=[budget], closed=false)",
                    owning.toString(),
                )
            }
        }
    }
}
