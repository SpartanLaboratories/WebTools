package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.FakeChain
import com.spartanlabs.testing.support.webtools.http.fakeRequest
import com.spartanlabs.testing.support.webtools.http.fakeResponse
import com.spartanlabs.webtools.http.ExchangeGuard
import com.spartanlabs.webtools.http.HIDDEN_STATUSES
import com.spartanlabs.webtools.http.HIDDEN_STATUS_HEADER
import com.spartanlabs.webtools.http.HopExchanges
import com.spartanlabs.webtools.http.PLACEHOLDER_STATUS
import com.spartanlabs.webtools.http.SECOND_EXCHANGE_MESSAGE
import com.spartanlabs.webtools.http.SecondExchangeRefused
import com.spartanlabs.webtools.http.hideForFollowUp
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - the exchange guard: `HopExchanges.enter()` is `true` once (also under 8 concurrent threads); the interceptor throws the
 * marker for a missing tag and for a second intercept with one tag, and passes a fresh tag; the marker is an `IOException` with the
 * constant message and no stack trace; `hideForFollowUp` hides `407`, `408`, `421` and `503` behind the placeholder with every other
 * header untouched, leaves other statuses alone, strips a forged private header, and never throws on a non-ASCII value.
 */
class ExchangeGuardComponentTest {

    private fun tagged(): Request = Request.Builder().url("http://fake.test/").tag(HopExchanges::class.java, HopExchanges()).build()

    private fun pairs(headers: Headers): List<Pair<String, String>> = (0 until headers.size).map { headers.name(it) to headers.value(it) }

    @Test
    fun `enter is true once and false after`() {
        val exchanges = HopExchanges()
        assertTrue(exchanges.enter())
        assertFalse(exchanges.enter())
        assertFalse(exchanges.enter())
    }

    @Test
    fun `exactly one of 8 concurrent enters is true`() {
        val exchanges = HopExchanges()
        val trues = AtomicInteger()
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..8).map { pool.submit { go.await(); if (exchanges.enter()) trues.incrementAndGet() } }
            go.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }
        assertEquals(1, trues.get())
    }

    @Test
    fun `a missing tag is refused before proceed`() {
        val chain = FakeChain(fakeRequest()) { fakeResponse(200) }
        assertFailsWith<SecondExchangeRefused> { ExchangeGuard.intercept(chain) }
        assertEquals(0, chain.proceeded.size)
    }

    @Test
    fun `a fresh tag passes once and the same tag is refused the second time`() {
        val request = tagged()
        val chain = FakeChain(request) { fakeResponse(200, headersOf("X-A", "1")) }
        val first = ExchangeGuard.intercept(chain)
        assertEquals(200, first.code)
        assertEquals("1", first.header("X-A"))
        assertSame(request, chain.proceeded.single())
        assertFailsWith<SecondExchangeRefused> { ExchangeGuard.intercept(chain) }
        assertEquals(1, chain.proceeded.size)
        // A copy of the request shares the tag, as OkHttp's re-sent requests do.
        assertFailsWith<SecondExchangeRefused> { ExchangeGuard.intercept(FakeChain(request.newBuilder().build()) { fakeResponse(200) }) }
    }

    @Test
    fun `the marker is an IOException with the constant message and no stack trace`() {
        val marker: Throwable = SecondExchangeRefused()
        assertTrue(marker is IOException)
        assertEquals(SECOND_EXCHANGE_MESSAGE, marker.message)
        assertEquals(0, marker.stackTrace.size)
    }

    @Test
    fun `the four hidden statuses get the placeholder and keep every other header in place`() {
        assertEquals(setOf(407, 408, 421, 503), HIDDEN_STATUSES)
        val headers = headersOf("Retry-After", "0", "X-First", "a", "Proxy-Authenticate", "Basic realm=\"r\"", "X-Last", "z")
        listOf(407, 408, 421, 503).forEach { status ->
            val hidden = hideForFollowUp(fakeResponse(status, headers))
            assertEquals(PLACEHOLDER_STATUS, hidden.code, "status $status")
            assertEquals(status.toString(), hidden.header(HIDDEN_STATUS_HEADER), "status $status")
            assertEquals(pairs(headers) + (HIDDEN_STATUS_HEADER to status.toString()), pairs(hidden.headers), "status $status")
        }
        val bare = hideForFollowUp(fakeResponse(503))
        assertEquals(599, bare.code)
        assertEquals(listOf(HIDDEN_STATUS_HEADER to "503"), pairs(bare.headers))
    }

    @Test
    fun `other statuses are returned unchanged`() {
        listOf(200, 301, 401, 429, 599).forEach { status ->
            val response = fakeResponse(status, headersOf("Retry-After", "0", "Location", "/x"))
            assertSame(response, hideForFollowUp(response), "status $status")
        }
    }

    @Test
    fun `a server-sent private header is stripped on a 200 and replaced on a 503`() {
        val forged200 = hideForFollowUp(fakeResponse(200, headersOf(HIDDEN_STATUS_HEADER, "407", "X-A", "1")))
        assertEquals(200, forged200.code)
        assertEquals(listOf("X-A" to "1"), pairs(forged200.headers))
        val forged503 = hideForFollowUp(fakeResponse(503, headersOf("x-webtools-hidden-status", "407", "Retry-After", "0")))
        assertEquals(599, forged503.code)
        assertEquals(listOf("Retry-After" to "0", HIDDEN_STATUS_HEADER to "503"), pairs(forged503.headers))
        assertEquals(listOf("503"), forged503.headers.values(HIDDEN_STATUS_HEADER))
    }

    @Test
    fun `a 503 with a non-ASCII header value does not throw and keeps the entry`() {
        val headers = Headers.Builder().addUnsafeNonAscii("X-Note", "café").add("Retry-After", "0").build()
        val hidden = hideForFollowUp(fakeResponse(503, headers))
        assertEquals(599, hidden.code)
        assertEquals(listOf("X-Note" to "café", "Retry-After" to "0", HIDDEN_STATUS_HEADER to "503"), pairs(hidden.headers))
    }
}
