package com.spartanlabs.testing.support.webtools.feeds

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.spartanlabs.webtools.feeds.FeedFetcher
import com.spartanlabs.webtools.feeds.FeedFetcherConfig
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlFetcher
import com.spartanlabs.webtools.http.UrlFetcherConfig
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.VirtualClock
import org.slf4j.LoggerFactory

/** The URL the fetcher tests fetch: `https` on a public-looking host, which an `Untrusted` policy allows (the pre-check refuses `localhost` and IP literals). */
val FEED_URL: Url = Url.parse("https://feeds.example.test/rss").getOrThrow()

/** The parent of this module's loggers. */
const val MODULE_LOGGER_NAME: String = "com.spartanlabs.webtools.feeds"

/**
 * A feed fetcher over [transport], built by the common factories and driven by [clock]: the `UrlFetcher`'s `timeSource` and `wallClock`
 * are the clock's, the parses run on the clock's dispatcher, then [configure] runs. The caller closes the returned fetcher; the
 * `UrlFetcher` it composes is closed with it here because this helper built it.
 *
 * @param clock the virtual clock of the test.
 * @param transport the transport, normally a `FakeHttpTransport`.
 * @param policy the policy the pre-check applies; `Untrusted` with its defaults when omitted.
 * @param urlConfigure the `UrlFetcher`'s other configuration (for example `budget`); nothing by default.
 * @param configure the other configuration to set (for example `parser`).
 * @return the fetcher; close it with `use`.
 */
fun fetcherOn(
    clock: VirtualClock,
    transport: HttpTransport,
    policy: OutboundUrlPolicy = OutboundUrlPolicy.untrusted(),
    urlConfigure: UrlFetcherConfig.() -> Unit = {},
    configure: FeedFetcherConfig.() -> Unit = {},
): FeedFetcher {
    val urlFetcher = UrlFetcher(transport, policy) { timeSource = clock.timeSource; wallClock = clock.wallClock; urlConfigure() }
    return FeedFetcher(urlFetcher) {
        parseDispatcher = clock.dispatcher
        configure()
    }
}

/**
 * An RSS 2.0 document with [entries] items, each with a guid `urn:item:<n>`, a title and a link on `https://feeds.example.test/`.
 *
 * @param entries how many items.
 * @param title the channel title.
 * @return the document text, UTF-8 when encoded.
 */
fun rssBody(entries: Int, title: String = "Fixture"): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>").append(title).append("</title>")
    for (i in 1..entries) append("<item><guid isPermaLink=\"false\">urn:item:").append(i).append("</guid><title>Item ").append(i).append("</title><link>https://feeds.example.test/items/").append(i).append("</link></item>")
    append("</channel></rss>")
}

/** A step answering [status] with [body] as UTF-8 and [headers]. */
fun respondWith(status: Int, body: String = "", headers: HttpHeaders = HttpHeaders()): Step = Step.respond(status, headers, body)

/** The headers of a `200` that carries both validators. */
fun validatorHeaders(etag: String? = "\"v1\"", lastModified: String? = "Fri, 06 Nov 2026 04:25:27 GMT"): HttpHeaders = HttpHeaders {
    add("Content-Type", "application/rss+xml")
    etag?.let { add("ETag", it) }
    lastModified?.let { add("Last-Modified", it) }
}

/**
 * Runs [block] with a logback `ListAppender` attached to this module's logger at [level], and restores the previous level and
 * detaches the appender in `finally`. The events are a snapshot taken after [block] returned. Inline, so [block] may suspend when
 * the caller does.
 *
 * @param level the level to capture at; `DEBUG` by default.
 * @param block the code whose logging is captured.
 * @return the block's value and every event the module's loggers emitted while it ran.
 */
inline fun <T> captureFeedLogs(level: Level = Level.DEBUG, block: () -> T): Pair<T, List<ILoggingEvent>> {
    val logbackLogger = LoggerFactory.getLogger(MODULE_LOGGER_NAME) as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    val previousLevel = logbackLogger.level
    logbackLogger.level = level
    logbackLogger.addAppender(appender)
    try {
        val value = block()
        return value to appender.list.toList()
    } finally {
        logbackLogger.detachAppender(appender)
        logbackLogger.level = previousLevel
        appender.stop()
    }
}

/** The events of the logger `com.spartanlabs.webtools.feeds.FeedFetcher`, in order. */
fun feedEvents(events: List<ILoggingEvent>): List<ILoggingEvent> = events.filter { it.loggerName == "$MODULE_LOGGER_NAME.FeedFetcher" }
