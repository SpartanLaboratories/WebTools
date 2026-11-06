package com.spartanlabs.testing.support.webtools.http

import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.UrlFetcherConfig
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.VirtualClock

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

/** A step answering [status] with [body] as UTF-8 and [headers]. */
internal fun respondWith(status: Int, body: String = "", headers: HttpHeaders = HttpHeaders()): Step = Step.respond(status, headers, body)

/**
 * The captured events of one facade's logger.
 *
 * @param events the events `captureTransportLogs` returned.
 * @param suffix the end of the logger's name: `UpstreamClient` or `UrlFetcher`.
 * @return the events whose logger name ends with [suffix], in order.
 */
internal fun facadeEvents(events: List<ILoggingEvent>, suffix: String): List<ILoggingEvent> = events.filter { it.loggerName.endsWith(suffix) }
