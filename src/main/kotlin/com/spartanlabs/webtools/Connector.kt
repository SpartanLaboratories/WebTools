package com.spartanlabs.webtools

import com.mashape.unirest.http.Unirest
import it.skrape.fetcher.HttpFetcher
import it.skrape.fetcher.response
import it.skrape.fetcher.skrape
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.EOFException
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.net.URLConnection
import javax.imageio.ImageIO

/**
 * A single-connection web client that reads a page's html line by line.
 *
 * A `Connector` holds at most one open connection at a time. Call [open] to
 * establish it, read through it with [next]/[hasNext] (or by iterating the
 * `Connector` itself), then release it with [close]. A second [open] call
 * blocks until the current connection is closed, so a `Connector` is safe to
 * share between threads but serializes them.
 *
 * Stateless one-shot helpers ([get], [skrape], [download]) do not use the
 * shared connection and can be called at any time.
 *
 * Every operation that can fail returns a [Result] rather than throwing or
 * returning `null`, so callers decide how to recover:
 * ```
 * val connector = Connector()
 * connector.open("https://example.com")
 *     .flatMap { connector.next() }
 *     .onSuccess { line -> println(line) }
 *     .onFailure { cause -> log.warn("could not read page", cause) }
 * connector.close()
 * ```
 */
class Connector {
    /** Reader over the currently-open [connection]'s input stream, or `null` when no connection is open. */
    private var reader: BufferedReader? = null

    /** The currently-open connection, or `null` when no connection is open. */
    private var connection: URLConnection? = null

    /**
     * Whether a connection is currently open and therefore whether a new [open]
     * call must wait. Written by [open] and [close] from arbitrary threads.
     */
    @Volatile
    private var isOpen = false

    /**
     * The most recent line read by [next] from the currently-open connection, or
     * `null` if nothing has been read since the last [open].
     */
    var currentLine: String? = null
        private set

    /** Creates a new Connector. */
    init {
        Unirest.setTimeouts(0, 0)
        log.info("Connector was created successfully")
    }

    /**
     * Opens a new connection with the given URL.
     *
     * If another [open] call is made before the current connection is closed with
     * [close] then the new connection will wait indefinitely until the current
     * connection closes. The connection is only marked open when every step
     * succeeds, so a failed [open] leaves this `Connector` reusable.
     *
     * @param urlName the url that you are trying to access
     * @return [Result.success] once the connection is open and ready to be read
     * from; [Result.failure] holding a [MalformedURLException] if [urlName] is not
     * a valid url, an [java.io.IOException] if the connection or its input stream
     * could not be opened, or an [InterruptedException] if the wait for the
     * previous connection to close was interrupted
     */
    @Synchronized
    infix fun open(urlName: String): Result<Unit> {
        log.info("Starting attempt to connect to url: {}", urlName)
        return waitForTurn()
            .flatMap { parseUrl(urlName) }
            .flatMap { url -> connect(url) }
            .flatMap { established -> readerFor(established).map { newReader -> established to newReader } }
            .map { (established, newReader) ->
                connection = established
                reader = newReader
                currentLine = null
                isOpen = true
                log.info("A connection to {} is open", urlName)
            }
            .onFailure { cause -> log.error("Failed to open a connection to {}: {}", urlName, cause.message, cause) }
    }

    /**
     * Blocks the calling thread while a previously-opened connection is still open.
     *
     * If a second connection were opened eagerly, the first one could close the
     * reader before the second was done using it.
     *
     * @return [Result.success] once no connection is open, or [Result.failure]
     * holding the [InterruptedException] if the wait was interrupted
     */
    private fun waitForTurn(): Result<Unit> =
        runCatching {
            while (isOpen) {
                log.trace("Waiting on the current connection to {} to close", connection?.url)
                Thread.sleep(TURN_POLL_INTERVAL_MILLIS)
            }
            log.debug("A connection is ready to be opened")
        }.onFailure { cause ->
            // Re-assert the interrupt so callers further up the stack can still observe it.
            if (cause is InterruptedException) Thread.currentThread().interrupt()
            log.error("Interrupted while waiting for the current connection to close")
        }

    /**
     * Parses a raw URL string into a [URL] instance.
     *
     * Normalises the several unrelated exception types the JDK can raise for bad
     * input into a single [MalformedURLException] so callers only have one failure
     * type to match on.
     *
     * @param urlName the url string to parse
     * @return the parsed [URL], or a [MalformedURLException] failure if [urlName]
     * is not a well-formed absolute url
     */
    private infix fun parseUrl(urlName: String): Result<URL> =
        runCatching { URI(urlName).toURL() }
            .onSuccess { url -> log.trace("URL formed successfully: {}", url) }
            .recoverCatching { cause ->
                log.error("Invalid URL provided: {}", urlName)
                throw MalformedURLException("Given value \"$urlName\" is not a valid url").apply { initCause(cause) }
            }

    /**
     * Opens a raw [URLConnection] to the given [url].
     * @param url the url to connect to
     * @return the opened connection, or an [java.io.IOException] failure if it could not be opened
     */
    private infix fun connect(url: URL): Result<URLConnection> {
        log.trace("Attempting to open connection")
        // Some websites reject the connection unless it looks like it came from a browser.
        System.setProperty("http.agent", BROWSER_USER_AGENT)
        return runCatching { url.openConnection() }
            .onSuccess { log.trace("Successfully opened a url connection") }
            .onFailure { cause -> log.error("A connection to the URL {} could not be opened", url, cause) }
    }

    /**
     * Creates a [BufferedReader] over the given connection's input stream.
     * @param established the connection to read from
     * @return the reader, or an [java.io.IOException] failure if the input stream could not be opened
     */
    private infix fun readerFor(established: URLConnection): Result<BufferedReader> =
        runCatching { BufferedReader(InputStreamReader(established.getInputStream())) }
            .onSuccess { log.trace("A reader was successfully created") }
            .onFailure { cause ->
                log.error("Could not create a reader for the url connection {}", established.url, cause)
            }

    /**
     * Closes the currently established connection. Must be called before another
     * connection can be established.
     *
     * The connection is released even if closing the underlying reader fails, so a
     * failed close never leaves this `Connector` permanently blocked in [open].
     *
     * @return [Result.success] if the reader closed cleanly (or was already closed),
     * or [Result.failure] holding the [java.io.IOException] it raised
     */
    fun close(): Result<Unit> =
        runCatching { reader?.close() }
            .map { }
            .also {
                // Released unconditionally: a reader we failed to close is still a reader
                // we will never read from again, and holding isOpen would deadlock open().
                reader = null
                connection = null
                currentLine = null
                isOpen = false
            }
            .onSuccess { log.debug("Connection closed and released") }
            .onFailure { cause -> log.error("An error occurred while trying to close the reader", cause) }

    /**
     * Downloads and decodes an image from the given URL.
     * @param imageUrl the url of the image to download
     * @return the downloaded image, or [Result.failure] if [imageUrl] is invalid,
     * unreachable, or does not decode to a known image format
     */
    infix fun download(imageUrl: String): Result<BufferedImage> =
        parseUrl(imageUrl)
            .flatMap { url ->
                runCatching {
                    Jsoup.connect(url.toString()).ignoreContentType(true).execute().bodyStream().use { stream ->
                        requireNotNull(ImageIO.read(stream)) { "No registered image reader could decode $url" }
                    }
                }
            }
            .onSuccess { log.info("Downloaded an image from {}", imageUrl) }
            .onFailure { cause -> log.error("Could not download an image from {}", imageUrl, cause) }

    /**
     * Skips the specified number of lines in the html data of the currently-open
     * connection, stopping at the first line that could not be read.
     *
     * @param lines the number of lines that you want skipped; values `<= 0` skip nothing
     * @return [Result.success] if every line was skipped, or the failure from the
     * first line that could not be read
     */
    fun next(lines: Int): Result<Unit> =
        (1..lines).fold(Result.success(Unit)) { skipped, _ -> skipped.flatMap { next().map { } } }
            .onFailure { cause -> log.warn("Could not skip {} line(s): {}", lines, cause.message) }

    /**
     * Goes to the next line in the html data of the currently-open connection.
     *
     * @return the next line, or [Result.failure] holding an [IllegalArgumentException]
     * if no connection is open, an [EOFException] if the stream is exhausted, or an
     * [java.io.IOException] if the read itself failed
     */
    operator fun next(): Result<String> =
        runCatching {
            val openReader = requireNotNull(reader) { "No connection is open - call open(url) first" }
            openReader.readLine() ?: throw EOFException("No more lines left to read")
        }
            .onSuccess { line ->
                currentLine = line
                log.trace("Read a line of {} character(s)", line.length)
            }
            .onFailure { cause -> log.debug("next() could not read a line: {}", cause.message) }

    /**
     * Checks whether another line is available to read from the current connection.
     *
     * Deliberately returns a plain [Boolean] rather than a `Result` to satisfy
     * Kotlin's iterator convention; a missing or unreadable reader simply reports
     * `false`, and [next] surfaces the underlying cause.
     *
     * @return `true` if [next] is expected to succeed
     */
    operator fun hasNext(): Boolean = runCatching { reader?.ready() ?: false }.getOrDefault(false)

    /**
     * Allows a [Connector] to be iterated directly, e.g. in a `for` loop over its lines.
     * Because [next] returns a [Result], the loop variable is a `Result<String>`.
     */
    operator fun iterator() = this

    /**
     * Performs a GET request on a given URL.
     * @param url the url that you want to send a GET request to
     * @return the body of the response, or [Result.failure] if [url] is invalid,
     * the request failed, or the response had no body
     */
    infix fun get(url: String): Result<String> =
        parseUrl(url)
            .flatMap { valid ->
                runCatching {
                    requireNotNull(Unirest.get(valid.toString()).asString().body) {
                        "GET $valid returned no body"
                    }
                }
            }
            .onSuccess { log.info("GET {} succeeded", url) }
            .onFailure { cause -> log.error("GET {} failed: {}", url, cause.message, cause) }

    /**
     * Reads the given URL using the Skrape library.
     * @param url the url to scrape
     * @return the response body of the scraped page, or [Result.failure] if [url]
     * is invalid or the page could not be fetched
     */
    infix fun skrape(url: String): Result<String> =
        parseUrl(url)
            .flatMap { valid -> runCatching { scrapeResponseBody(valid.toString()) } }
            .onSuccess { log.info("Scraped {}", url) }
            .onFailure { cause -> log.error("Could not scrape {}: {}", url, cause.message, cause) }

    /**
     * Wrapper around the skrape library's `skrape` call.
     * @param target the url to fetch
     * @return the raw response body of the fetched page
     */
    private fun scrapeResponseBody(target: String): String = skrape(HttpFetcher) {
        request { url = target }
        response { responseBody }
    }

    companion object {
        /** Shared slf4j logger for all [Connector] instances. */
        private val log = LoggerFactory.getLogger(Connector::class.java)

        /** How long [waitForTurn] sleeps between checks of [isOpen]. */
        private const val TURN_POLL_INTERVAL_MILLIS = 100L

        /** Sent as `http.agent` so servers that reject non-browser clients still respond. */
        private const val BROWSER_USER_AGENT =
            "Mozilla/4.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/25.0"
    }
}
