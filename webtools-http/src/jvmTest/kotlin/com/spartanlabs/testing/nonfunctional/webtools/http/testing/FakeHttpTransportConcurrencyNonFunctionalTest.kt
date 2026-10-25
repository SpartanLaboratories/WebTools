package com.spartanlabs.testing.nonfunctional.webtools.http.testing

import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.Unmatched
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Level 4c - the fake under real concurrency (`Dispatchers.Default`, `runBlocking`): 200 callers released
 * together by one `CompletableDeferred` against a `429`-then-`200` script see exactly one `429`, the log holds
 * every call, and concurrent `on(...)` adders and callers, released the same way, leave a consistent log.
 * Counts are asserted, never durations; the generous `withTimeout` is a hang bound, so a lock-ordering
 * regression fails the build instead of hanging it.
 */
class FakeHttpTransportConcurrencyNonFunctionalTest {

    private val request = HttpRequest(HttpMethod.Get, Url.parse("https://h/a").getOrThrow())

    @Test
    fun `200 concurrent callers see one 429 and 199 200s with every call recorded`() = runBlocking {
        val transport = FakeHttpTransport().on(RequestMatch.get("/a"), Step.respond(429), Step.respond(200))
        // Every caller waits on the same gate, completed only after all are launched, so they contend for the lock together.
        val start = CompletableDeferred<Unit>()
        val callers = (1..200).map { async(Dispatchers.Default) { start.await(); transport.execute(request).getOrThrow().status } }
        start.complete(Unit)
        val statuses = withTimeout(2.minutes) { callers.awaitAll() }
        assertEquals(1, statuses.count { it == 429 })
        assertEquals(199, statuses.count { it == 200 })
        assertEquals(200, transport.calls.size)
        assertTrue(transport.calls.all { it.matched })
        assertTrue(transport.unmatchedCalls.isEmpty())
    }

    @Test
    fun `concurrent on and execute leave a consistent log`() = runBlocking {
        val transport = FakeHttpTransport(unmatched = Unmatched.Respond(Step.respond(404)))
        val start = CompletableDeferred<Unit>()
        val adders = (1..50).map { async(Dispatchers.Default) { start.await(); transport.on(RequestMatch.get("/a"), Step.respond(200)) } }
        val callers = (1..200).map { async(Dispatchers.Default) { start.await(); transport.execute(request).getOrThrow().status } }
        start.complete(Unit)
        val statuses = withTimeout(2.minutes) { adders.awaitAll(); callers.awaitAll() }
        assertTrue(statuses.all { it == 200 || it == 404 }, statuses.distinct().toString())
        assertEquals(200, statuses.size)
        val calls = transport.calls
        assertEquals(200, calls.size)
        assertEquals(calls.count { !it.matched }, transport.unmatchedCalls.size)
        assertEquals("FakeHttpTransport(stubs=50, calls=200, closed=false)", transport.toString())
        val timestamps = calls.map { it.receivedAt }
        assertEquals(timestamps, timestamps.sorted(), "the log is not in arrival order")
    }
}
