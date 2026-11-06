package com.spartanlabs.testing.support.webtools.http

import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.webtools.http.CacheStats
import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.UpstreamClientConfig
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.UrlFetcherConfig
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.VirtualClock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The base URL the client tests call against: `https`, a public-looking host and a base path with a trailing slash. */
internal val BASE_URL: Url = Url.parse("https://api.example.test/v1/").getOrThrow()

/** The URL the fetcher tests fetch: `https` on a public-looking host, which an `Untrusted` policy allows. */
internal val FETCH_URL: Url = Url.parse("https://files.example.test/a").getOrThrow()

/**
 * An endpoint whose decoder reads the body as UTF-8 text.
 *
 * @param id the endpoint id; `hero` by default.
 * @param configure the endpoint's optional settings.
 * @return the endpoint.
 */
internal fun stringEndpoint(id: String = "hero", configure: Endpoint.Builder.() -> Unit = {}): Endpoint<String> =
    Endpoint(id, { response -> Result.success(response.body().decodeToString()) }, configure)

/**
 * A fetcher over [transport] built by the common factory and driven by [clock]: its `timeSource` and `wallClock` are the clock's, then [configure] runs.
 *
 * @param clock the virtual clock of the test.
 * @param transport the transport, normally a `FakeHttpTransport`; the fetcher never closes it.
 * @param policy the policy the pre-check applies; `Untrusted` with its defaults when omitted.
 * @param configure the other configuration to set (for example `budget`).
 * @return the fetcher; close it with `use`.
 * @throws IllegalArgumentException if [configure] sets a negative `defaultMaxWait`.
 */
internal fun fetcherOn(
    clock: VirtualClock,
    transport: HttpTransport,
    policy: OutboundUrlPolicy = OutboundUrlPolicy.untrusted(),
    configure: UrlFetcherConfig.() -> Unit = {},
): UrlFetcher =
    UrlFetcher(transport, policy) {
        timeSource = clock.timeSource
        wallClock = clock.wallClock
        configure()
    }

/**
 * A step answering [status] with [body] as UTF-8 and [headers].
 *
 * @param status the response status.
 * @param body the response body, encoded as UTF-8; empty by default.
 * @param headers the response headers; none by default.
 * @return the step.
 */
internal fun respondWith(status: Int, body: String = "", headers: HttpHeaders = HttpHeaders()): Step = Step.respond(status, headers, body)

/**
 * The captured events of one facade's logger.
 *
 * @param events the events `captureTransportLogs` returned.
 * @param suffix the end of the logger's name: `UpstreamClient` or `UrlFetcher`.
 * @return the events whose logger name ends with [suffix], in order.
 */
internal fun facadeEvents(events: List<ILoggingEvent>, suffix: String): List<ILoggingEvent> = events.filter { it.loggerName.endsWith(suffix) }

/**
 * A client over [transport] at [BASE_URL] built by the common factory and driven by [clock]: its `timeSource`, `wallClock` and `dispatcher` are the
 * clock's, then [configure] runs, so one virtual clock drives a budget built from windows, the signal reader and the per-call figures.
 *
 * @param clock the virtual clock of the test.
 * @param transport the transport, normally a `FakeHttpTransport`; the client never closes it.
 * @param configure the other configuration to set (for example `cache(...)`, `budget(...)`, `policy`).
 * @return the client; close it with `use`.
 * @throws IllegalArgumentException for a configuration the common factory refuses, for example an invalid window list.
 */
internal fun clientOn(clock: VirtualClock, transport: HttpTransport, configure: UpstreamClientConfig.() -> Unit = {}): UpstreamClient =
    UpstreamClient(transport, BASE_URL) {
        timeSource = clock.timeSource
        wallClock = clock.wallClock
        dispatcher = clock.dispatcher
        configure()
    }

/**
 * Asserts that a refused call cost nothing: the transport received no call, the budget granted no permit and the client's cache counted nothing.
 *
 * @param fake the client's transport.
 * @param budget the recording budget the client charges.
 * @param client the client under test.
 */
internal fun assertNothingSpent(fake: FakeHttpTransport, budget: PermitRecordingBudget, client: UpstreamClient) {
    assertTrue(fake.calls.isEmpty(), "the transport received ${fake.calls.size} call(s)")
    assertTrue(budget.permits.isEmpty(), "the budget granted ${budget.permits.size} permit(s)")
    assertEquals(CacheStats.Empty, client.cacheStats())
}
