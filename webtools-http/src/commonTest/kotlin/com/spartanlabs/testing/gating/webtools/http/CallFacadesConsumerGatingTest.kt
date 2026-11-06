package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.CallSpec
import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.FetchSpec
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.ResponseCache
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.UpstreamClientConfig
import com.spartanlabs.webtools.http.UpstreamFailure
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.UrlFetcherConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// A named decoder, as a consumer writes one: the second argument of the endpoint factory.
private fun decode(response: HttpResponse): Result<String> = Result.success(response.body().decodeToString())

// The facades in a function signature and in a class and its constructor. A consumer holds and passes them without any opt-in.
private fun describe(client: UpstreamClient?, fetcher: UrlFetcher?): String = "client=${client != null}, fetcher=${fetcher != null}"

private class UsesFacades(val client: UpstreamClient? = null, val fetcher: UrlFetcher? = null, val endpoint: Endpoint<String>)

// The four configuration lambdas a consumer keeps and reuses.
private class Settings(
    val client: UpstreamClientConfig.() -> Unit,
    val fetcher: UrlFetcherConfig.() -> Unit,
    val call: CallSpec.() -> Unit,
    val fetch: FetchSpec.() -> Unit,
)

/**
 * Level 1 - the permanent consumer of the call facades' types, compiled in `commonTest` **without any opt-in**. It names `UpstreamClient`,
 * `UrlFetcher` and `Endpoint` in a function, a class and a constructor signature, builds an `Endpoint` with its factory, writes a reusable
 * configuration lambda for each of `UpstreamClientConfig`, `UrlFetcherConfig`, `CallSpec` and `FetchSpec`, and matches a call's result: a `when`
 * over `Lookup` with no `else` and a `when` over the failures with one. None of the facades' types is gated, which is the point.
 */
class CallFacadesConsumerGatingTest {

    private fun show(lookup: Lookup<String>): String = when (lookup) {
        is Lookup.Found -> "found ${lookup.value}"
        Lookup.Missing -> "missing"
    }

    // New failure types may be added, so a consumer keeps an else.
    private fun advise(failure: Throwable): String = when (failure) {
        is UpstreamFailure.RateLimited -> "wait ${failure.retryAfter}"
        is HttpFailure.BudgetExhausted -> "wait ${failure.wait}"
        is UpstreamFailure.UpstreamError -> "status ${failure.status}"
        else -> "report"
    }

    @Test
    fun `Endpoint is built by its factory without any opt-in`() {
        val player: Endpoint<String> = Endpoint("player", ::decode) { ttl = 10.minutes; misses(404) }
        val plain = Endpoint<String>("a", ::decode)

        assertEquals("player", player.id)
        assertEquals(10.minutes, player.ttl)
        assertEquals(setOf(404), player.misses)
        assertEquals("a", plain.id)
        assertEquals(Duration.ZERO, plain.ttl)

        val holder = UsesFacades(endpoint = player)
        assertNull(holder.client)
        assertNull(holder.fetcher)
        assertEquals("client=false, fetcher=false", describe(holder.client, holder.fetcher))
    }

    @Test
    fun `a configuration lambda for each facade compiles without an opt-in`() {
        // Compiling these four lambdas is the check: a consumer can write and keep each without any opt-in. They are applied to a real client and a
        // real fetcher in the JVM gating class, where a transport exists.
        Settings(
            client = {
                defaultMaxWait = 2.seconds
                headers = HttpHeaders { addSensitive("Authorization", "Bearer token") }
                cache(ResponseCache.Disabled)
                budget(CallBudget.Unlimited)
                budget(listOf(RateWindow("minute", 60, 1.minutes)))
            },
            fetcher = {
                budget = CallBudget.Unlimited
                defaultMaxWait = 2.seconds
            },
            call = {
                path("players", "7")
                query("region", "eu")
                headers { add("X-Trace", "1") }
                method = HttpMethod.Get
                lane = Lane.Tracker
                maxWait = maxWait * 2
                maxAge = 1.minutes
            },
            fetch = {
                cost = 2
                lane = Lane.Tracker
                maxWait = 1.seconds
            },
        )

        assertEquals(5.seconds, UpstreamClientConfig.DEFAULT_MAX_WAIT)
        assertEquals(5.seconds, UrlFetcherConfig.DEFAULT_MAX_WAIT)
    }

    @Test
    fun `a call's result is matched with an else`() {
        assertEquals("found Ada", show(Lookup.Found("Ada")))
        assertEquals("missing", show(Lookup.Missing))

        assertEquals("status 404", advise(UpstreamFailure.UpstreamError(404, HttpHeaders(), ByteArray(0))))
        assertEquals("wait 30s", advise(HttpFailure.BudgetExhausted(30.seconds, ExhaustedReason.Paused, null)))
        assertEquals("report", advise(HttpFailure.Closed("client")))
    }
}
