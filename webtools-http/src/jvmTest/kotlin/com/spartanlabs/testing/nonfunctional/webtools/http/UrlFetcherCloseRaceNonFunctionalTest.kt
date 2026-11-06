package com.spartanlabs.testing.nonfunctional.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.CloseLog
import com.spartanlabs.testing.support.webtools.http.FETCH_URL
import com.spartanlabs.testing.support.webtools.http.LoggedTransport
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.facadeEvents
import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.FetcherParts
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.UrlFetcherConfig
import com.spartanlabs.webtools.http.UrlGuard
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val ROUNDS = 50
private const val CALLERS = 16
private const val CLOSERS = 32

/**
 * Level 4c - nothing hangs when `close()` races the fetches of a `UrlFetcher` that owns its transport, and `close()` is safe from any thread, on
 * real time under `runBlocking` with each round bounded to 30 s. Over 50 rounds, 16 fetches of a slow fake transport start together while
 * `close()` runs from another thread at a seeded random point: every fetch returns a `Result`, the response or a `Closed` of the fetcher or the
 * transport, never a thrown cancellation, and the round's thread pool terminates. Over 50 more rounds, 32 concurrent callers on
 * `Dispatchers.Default` call `close()` at once while 16 others fetch and read `toString()`: no call throws, the transport is closed exactly once
 * and `closed` is logged exactly once. Probabilistic: it
 * exercises the window between a fetch's closed check and its send, and the one caller that wins the closed flag, which no deterministic seam
 * reaches.
 */
class UrlFetcherCloseRaceNonFunctionalTest {

    private val request = HttpRequest(HttpMethod.Get, FETCH_URL)

    // A fetcher that owns its transport, built the way an owning factory builds one; the budget stays the caller's.
    private fun owningFetcher(transport: HttpTransport, budget: CallBudget): UrlFetcher {
        // A local under another name, so the configuration's `budget` and the parameter are told apart at a glance.
        val supplied = budget
        val resolved = UrlFetcherConfig().apply { this.budget = supplied }.resolve()
        return UrlFetcher(FetcherParts(transport, UrlGuard(OutboundUrlPolicy.untrusted()), resolved, ownsTransport = true))
    }

    @Volatile
    private var sink = 0

    // Spins for `iterations` volatile writes, so the closer lands at a different point of the fetches' progress on each round.
    private fun spin(iterations: Int) {
        repeat(iterations) { sink++ }
    }

    // A fetch that raced a close ends with the response, or with a `Closed` of the fetcher or of its transport.
    private fun assertFetchedOrClosed(result: Result<HttpResponse>, context: String) {
        result.fold(
            onSuccess = { assertEquals(200, it.status, context) },
            onFailure = { failure ->
                val closed = failure as? HttpFailure.Closed ?: fail("$context: $failure")
                assertTrue(closed.component in setOf("fetcher", "transport"), "$context: Closed(${closed.component})")
            },
        )
    }

    @Test
    fun `close racing in-flight fetches ends every fetch with a value and hangs nothing`(): Unit = runBlocking {
        // Fixed seed (the issue number) so a failing round is reproducible.
        val random = Random(68)
        repeat(ROUNDS) { round ->
            // A pool of the round's own, so its termination below proves no fetch of the round is left running; 8 threads for 16 callers, so some
            // fetches start while others are in flight.
            val executor = Executors.newFixedThreadPool(8)
            val pool = executor.asCoroutineDispatcher()
            val fake = FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = "x", delay = 5.milliseconds))
            val budget = WindowedCallBudget(listOf(RateWindow("minute", 100, 1.minutes)))
            val fetcher = owningFetcher(fake, budget)
            try {
                val gate = CompletableDeferred<Unit>()
                val pauseBeforeClose = random.nextInt(0, 8).milliseconds          // before, during or after the fetches' 5 ms in flight
                val spinBeforeClose = random.nextInt(0, 20_000)
                // The callers and the closer are children of the bound, so a hang cancels them and fails the round instead of waiting on runBlocking.
                val results = withTimeout(30.seconds) {
                    val callers = List(CALLERS) { index ->
                        async(pool) {
                            gate.await()
                            try {
                                fetcher.fetch(request)
                            } catch (cancelled: CancellationException) {
                                currentCoroutineContext().ensureActive()      // the bound's own cancellation passes; anything else is the fetcher's
                                fail("round $round, caller $index: fetch threw a CancellationException: $cancelled")
                            }
                        }
                    }
                    val closer = launch(Dispatchers.Default) {                // another thread than the callers'
                        gate.await()
                        delay(pauseBeforeClose)
                        spin(spinBeforeClose)
                        fetcher.close()
                    }
                    gate.complete(Unit)
                    closer.join()
                    callers.awaitAll()
                }
                results.forEachIndexed { index, result -> assertFetchedOrClosed(result, "round $round, caller $index") }
                assertTrue(fake.isClosed, "round $round: the owned transport was not closed")
                // A fetch after the close reaches nothing.
                val after = fake.calls.size
                assertEquals("fetcher", (fetcher.fetch(request).exceptionOrNull() as? HttpFailure.Closed)?.component, "round $round")
                assertEquals(after, fake.calls.size, "round $round")
                executor.shutdown()
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "round $round: the round's thread pool did not terminate")
            } finally {
                // A round that fails still leaves nothing open and no thread behind.
                fetcher.close()
                budget.close()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `close from many threads at once closes the transport once and never throws`() {
        val closeLogs = ArrayList<CloseLog>()
        val events = captureTransportLogs(Level.INFO) {
            runBlocking {
                repeat(ROUNDS) { round ->
                    val log = CloseLog().also { closeLogs += it }
                    val fake = FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = "x"))
                    val fetcher = owningFetcher(LoggedTransport(fake, log), CallBudget.Unlimited)
                    try {
                        withTimeout(30.seconds) {
                            val gate = CompletableDeferred<Unit>()
                            // Every member at once, from many threads: close(), fetch() and toString().
                            val closers = List(CLOSERS) { async(Dispatchers.Default) { gate.await(); fetcher.close() } }
                            val callers = List(CALLERS) { async(Dispatchers.Default) { gate.await(); fetcher.toString(); fetcher.fetch(request) } }
                            gate.complete(Unit)
                            closers.awaitAll()                                 // a close that threw fails here
                            callers.awaitAll().forEachIndexed { index, result -> assertFetchedOrClosed(result, "round $round, caller $index") }
                        }
                        assertTrue("closed=true" in fetcher.toString(), fetcher.toString())
                    } finally {
                        fetcher.close()
                    }
                }
            }
        }.second

        // One caller of `close()` wins each round: the transport is closed once and `closed` is logged once.
        closeLogs.forEachIndexed { round, log -> assertEquals(listOf("transport"), log.names, "round $round") }
        assertEquals(ROUNDS, facadeEvents(events, "UrlFetcher").count { it.formattedMessage == "closed" })
        assertEquals(ROUNDS, facadeEvents(events, "UrlFetcher").count { it.formattedMessage.startsWith("created") })
    }
}
