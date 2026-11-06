package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.BASE_URL
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.ClientParts
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.Owned
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.UpstreamClientConfig
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val ROUNDS = 50
private const val CALLERS = 16

/**
 * Level 4c - nothing hangs when `close()` races the calls of an `UpstreamClient` that owns its cache, budget and transport, on real time under
 * `runBlocking` with each round bounded to 30 s. Over 50 rounds, 16 calls of a slow fake transport start together while `close()` runs from
 * another thread at a seeded random point: every call returns a `Result`, a value or a `Closed` of the client, the cache, the budget or the
 * transport, never a thrown cancellation, and the round's thread pool terminates. With the budget's one unit taken, 16 calls waiting for a permit
 * without limit all end with `Closed("budget")` or `Closed("cache")` when `close()` runs. Probabilistic: it exercises the windows between a call's
 * closed check, its load and its permit, which no deterministic seam reaches.
 */
class UpstreamClientCloseRaceNonFunctionalTest {

    private val everything = Owned(cache = true, budget = true, transport = true)

    // A client that owns the three components, built the way an owning factory builds one.
    private fun owningClient(fake: FakeHttpTransport, cache: CaffeineResponseCache, budget: WindowedCallBudget): UpstreamClient =
        UpstreamClient(ClientParts(UpstreamClientConfig(BASE_URL).resolve(), fake, cache, budget, everything))

    @Volatile
    private var sink = 0

    // Spins for `iterations` volatile writes, so the closer lands at a different point of the callers' progress on each round.
    private fun spin(iterations: Int) {
        repeat(iterations) { sink++ }
    }

    private fun closedComponentOf(failure: Throwable, context: String): String =
        (failure as? HttpFailure.Closed ?: fail("$context: $failure")).component

    @Test
    fun `close racing in-flight calls ends every call with a value and hangs nothing`(): Unit = runBlocking {
        // Fixed seed (the issue number) so a failing round is reproducible.
        val random = Random(68)
        val endpoint = stringEndpoint { ttl = 10.minutes }
        repeat(ROUNDS) { round ->
            val executor = Executors.newFixedThreadPool(8)
            val pool = executor.asCoroutineDispatcher()
            val fake = FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = "x", delay = 5.milliseconds))
            val client = owningClient(
                fake,
                CaffeineResponseCache { loadDispatcher = pool },
                WindowedCallBudget(listOf(RateWindow("minute", 100, 1.minutes))),
            )
            try {
                val gate = CompletableDeferred<Unit>()
                val pauseBeforeClose = random.nextInt(0, 8).milliseconds          // before, during or after the calls' 5 ms in flight
                val spinBeforeClose = random.nextInt(0, 20_000)
                // The callers and the closer are children of the bound, so a hang cancels them and fails the round instead of waiting on runBlocking.
                val results = withTimeout(30.seconds) {
                    val callers = List(CALLERS) { index ->
                        async(pool) {
                            gate.await()
                            try {
                                client.call(endpoint) { query("k", (index % 4).toString()) }
                            } catch (cancelled: CancellationException) {
                                currentCoroutineContext().ensureActive()      // the bound's own cancellation passes; anything else is the client's
                                fail("round $round, caller $index: call threw a CancellationException: $cancelled")
                            }
                        }
                    }
                    val closer = launch(Dispatchers.Default) {                // another thread than the callers'
                        gate.await()
                        delay(pauseBeforeClose)
                        spin(spinBeforeClose)
                        client.close()
                    }
                    gate.complete(Unit)
                    closer.join()
                    callers.awaitAll()
                }
                results.forEachIndexed { index, result ->
                    result.fold(
                        onSuccess = { assertEquals(Lookup.Found("x"), it, "round $round, caller $index") },
                        onFailure = { failure ->
                            val component = closedComponentOf(failure, "round $round, caller $index")
                            assertTrue(component in setOf("client", "cache", "budget", "transport"), "round $round, caller $index: Closed($component)")
                        },
                    )
                }
                assertTrue(fake.isClosed, "round $round: the owned transport was not closed")
                executor.shutdown()
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "round $round: the round's thread pool did not terminate")
            } finally {
                // A round that fails still leaves nothing open and no thread behind.
                client.close()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `close racing calls waiting for a permit ends them with Closed`(): Unit = runBlocking {
        val fake = FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = "x"))
        // One unit a day and no lane floor: the first call takes it and every later call waits for the next day.
        val budget = WindowedCallBudget(listOf(RateWindow("day", 1, 1.days))) { laneShares = emptyMap() }
        val client = owningClient(fake, CaffeineResponseCache(), budget)
        val endpoint = stringEndpoint()
        try {
            withTimeout(30.seconds) {
                assertEquals(Lookup.Found("x"), client.call(endpoint) { query("k", "first") }.getOrThrow())

                val waiting = List(CALLERS) { index ->
                    async(Dispatchers.Default) { client.call(endpoint) { query("k", index.toString()); maxWait = Duration.INFINITE } }
                }
                while (client.utilisation().waiting < CALLERS) delay(5.milliseconds)      // every call is queued for a permit

                client.close()

                waiting.awaitAll().forEachIndexed { index, result ->
                    val failure = result.exceptionOrNull() ?: fail("caller $index: expected a failure, got ${result.getOrNull()}")
                    val component = closedComponentOf(failure, "caller $index")
                    assertTrue(component in setOf("budget", "cache"), "caller $index: Closed($component)")
                }
            }
            assertEquals(1, fake.calls.size)                               // only the first call was sent
        } finally {
            client.close()                                                 // ends every waiting call if an assertion failed before the close
        }
    }
}
