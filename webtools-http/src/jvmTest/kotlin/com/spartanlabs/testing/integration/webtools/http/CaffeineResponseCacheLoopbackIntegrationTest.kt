package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.CacheKey
import com.spartanlabs.webtools.http.CachePolicy
import com.spartanlabs.webtools.http.CaffeineResponseCache
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.ResponseCache
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 3 - the cache on its production defaults (`TimeSource.Monotonic`, `Dispatchers.Default`, the common pool) over a real loopback exchange,
 * on real time under `runBlocking` within 30 s: 16 concurrent callers of one key whose loader runs the real transport against one delayed response
 * cost the server one request, all receive the same body and count as 15 hits and 1 miss, and a later call is a hit (the request count stays 1);
 * as a control, 8 concurrent callers on `ResponseCache.Disabled` cost 8 requests; a `500` the loader maps to `UpstreamError` is not stored (the next
 * call reaches the server again and a following `200` is stored; `failedLoads` is 1); `close()` while a delayed response is in flight ends every
 * waiting caller with `Closed("cache")` within the bound and cancels the transport call the loader had started.
 */
class CaffeineResponseCacheLoopbackIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer
    private lateinit var transport: OkHttpTransport
    private lateinit var cache: CaffeineResponseCache

    private val key = CacheKey<String>("players", listOf("id" to "1"))
    private val policy = CachePolicy(10.minutes)

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
        transport = fleet.track(LoopbackTransports.untrusted("agent"))
        cache = CaffeineResponseCache()
    }

    @AfterTest
    fun stop() {
        cache.close()
        fleet.close()
    }

    private fun request(): HttpRequest = fleet.get(server.webtoolsUrl("/players"))

    /** The loader of the plan: the real transport, its body as the value. */
    private fun bodyLoader(): suspend () -> Result<Lookup<String>> = { transport.execute(request()).map { Lookup.Found(it.body().decodeToString()) } }

    /** A loader that answers a `200` with its body and any other status with `UpstreamError`, as a facade would. */
    private fun statusLoader(): suspend () -> Result<Lookup<String>> = {
        transport.execute(request()).fold(
            onSuccess = { response: HttpResponse ->
                if (response.status == 200) Result.success(Lookup.Found(response.body().decodeToString()))
                else Result.failure(UpstreamFailure.UpstreamError(response.status, response.headers, response.body()))
            },
            onFailure = { Result.failure(it) },
        )
    }

    @Test
    fun `16 concurrent callers of one key cost one request and a later call is a hit`(): Unit = runBlocking {
        server.enqueue(MockResponse.Builder().headersDelay(500, TimeUnit.MILLISECONDS).body("roster").build())
        val results = withTimeout(30.seconds) { List(16) { async(Dispatchers.Default) { cache.getOrLoad(key, policy, bodyLoader()) } }.awaitAll() }
        results.forEach { assertEquals(Lookup.Found("roster"), it.getOrThrow()) }
        assertEquals(1, server.requestCount)
        assertEquals(15L, cache.stats().hits)
        assertEquals(1L, cache.stats().misses)
        assertEquals(Lookup.Found("roster"), withTimeout(30.seconds) { cache.getOrLoad(key, policy, bodyLoader()) }.getOrThrow())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `control - 8 concurrent callers on Disabled cost one request each`(): Unit = runBlocking {
        repeat(8) { server.enqueue(MockResponse(body = "roster")) }
        val results = withTimeout(30.seconds) { List(8) { async(Dispatchers.Default) { ResponseCache.Disabled.getOrLoad(key, policy, bodyLoader()) } }.awaitAll() }
        results.forEach { assertEquals(Lookup.Found("roster"), it.getOrThrow()) }
        assertEquals(8, server.requestCount)
    }

    @Test
    fun `a 500 mapped to UpstreamError is not stored and a following 200 is`(): Unit = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("down").build())
        server.enqueue(MockResponse(body = "roster"))
        val failure = assertIs<UpstreamFailure.UpstreamError>(withTimeout(30.seconds) { cache.getOrLoad(key, policy, statusLoader()) }.exceptionOrNull())
        assertEquals(500, failure.status)
        assertEquals(1, server.requestCount)
        assertEquals(Lookup.Found("roster"), withTimeout(30.seconds) { cache.getOrLoad(key, policy, statusLoader()) }.getOrThrow())
        assertEquals(2, server.requestCount)
        assertEquals(Lookup.Found("roster"), withTimeout(30.seconds) { cache.getOrLoad(key, policy, statusLoader()) }.getOrThrow())
        assertEquals(2, server.requestCount)
        assertEquals(1L, cache.stats().failedLoads)
    }

    @Test
    fun `close while a delayed response is in flight ends the waiters with Closed and cancels the transport call`(): Unit = runBlocking {
        server.enqueue(MockResponse.Builder().headersDelay(20, TimeUnit.SECONDS).body("late").build())
        val transportCancelled = CompletableDeferred<Unit>()
        val loader: suspend () -> Result<Lookup<String>> = {
            try {
                transport.execute(request()).map { Lookup.Found(it.body().decodeToString()) }
            } catch (cancelled: CancellationException) {
                transportCancelled.complete(Unit)
                throw cancelled
            }
        }
        val results = withTimeout(30.seconds) {
            val waiters = List(4) { async(Dispatchers.Default) { cache.getOrLoad(key, policy, loader) } }
            // The server saw the request: the loader's transport call is on the wire when close() runs.
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(10, TimeUnit.SECONDS) }, "the server never saw the request")
            cache.close()
            waiters.awaitAll()
        }
        results.forEach { result -> assertEquals("cache", assertIs<HttpFailure.Closed>(result.exceptionOrNull()).component) }
        withTimeout(30.seconds) { transportCancelled.await() }
        transport.close()
    }
}
