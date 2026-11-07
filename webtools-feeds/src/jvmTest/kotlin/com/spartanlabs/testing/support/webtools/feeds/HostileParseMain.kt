package com.spartanlabs.testing.support.webtools.feeds

import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedFailure
import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.feeds.FeedParser
import com.spartanlabs.webtools.http.Url
import java.util.concurrent.CyclicBarrier
import kotlin.system.exitProcess

/**
 * The main class of the fixed-heap gate's child JVM. Arguments: the `ParserFactory` class name, the case name and the parse count. It
 * builds the parser through the factory with the default `FeedLimits`, generates the case's document itself, starts the parses on as
 * many threads released together by a barrier, and prints one line per parse: `ok:<entries>`, `NotAFeed:<reason>`, `Malformed`,
 * `Refused:<reason>`, `LimitExceeded:<limit>` or `thrown:<class>`. It exits with `0` when every parse returned a `Result`, `11` for
 * an escaped `StackOverflowError` and `12` for any other throwable; an out-of-memory error exits `3` through
 * `-XX:+ExitOnOutOfMemoryError`. The launcher's own cases need no parser: `smoke` prints one line, `oom` allocates until the heap is
 * exhausted, `hang` sleeps 60 s.
 */
object HostileParseMain {
    private val baseUrl: Url = Url.parse("https://feeds.example.test/feed.xml").getOrThrow()

    /** The result line of one parse. */
    fun lineOf(result: Result<Feed>): String {
        val feed = result.getOrNull()
        if (feed != null) return "ok:${feed.entries.size}"
        return when (val failure = result.exceptionOrNull()) {
            is FeedFailure.NotAFeed -> "NotAFeed:${failure.reason.name}"
            is FeedFailure.Malformed -> "Malformed"
            is FeedFailure.Refused -> "Refused:${failure.reason.name}"
            is FeedFailure.LimitExceeded -> "LimitExceeded:${failure.limit.name}"
            else -> "thrown:${failure?.let { it::class.java.name } ?: "null"}"
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val factoryName = args.getOrNull(0) ?: exitProcess(HostileParseJvm.EXIT_THROWN)
        val case = args.getOrNull(1) ?: exitProcess(HostileParseJvm.EXIT_THROWN)
        val parses = args.getOrNull(2)?.toIntOrNull() ?: 1
        when (case) {
            "smoke" -> { println("smoke:ok"); exitProcess(0) }
            "oom" -> exhaustHeap()
            "hang" -> { println("hang:sleeping"); Thread.sleep(60_000); exitProcess(0) }
        }
        val outcome = runCatching {
            val parser = (Class.forName(factoryName).getDeclaredConstructor().newInstance() as ParserFactory).create(FeedLimits.Defaults)
            val document = HostileDocuments.byName.getValue(case).first()
            parseConcurrently(parser, document, parses)
        }
        outcome.onFailure { failure ->
            System.out.println("thrown:${failure::class.java.name}")
            System.out.flush()
            exitProcess(if (failure is StackOverflowError) HostileParseJvm.EXIT_STACK_OVERFLOW else HostileParseJvm.EXIT_THROWN)
        }
        System.out.flush()
        exitProcess(0)
    }

    // Each thread parses the same document once; every result line is printed by the main thread after all have finished.
    private fun parseConcurrently(parser: FeedParser, document: ByteArray, parses: Int) {
        val barrier = CyclicBarrier(parses)
        val results = arrayOfNulls<Result<Feed>>(parses)
        val escaped = arrayOfNulls<Throwable>(parses)
        val threads = List(parses) { i ->
            Thread {
                try {
                    barrier.await()
                    results[i] = parser.parse(FeedInput(document, "application/rss+xml", baseUrl))
                } catch (t: Throwable) {
                    escaped[i] = t
                }
            }.apply { isDaemon = true; start() }
        }
        threads.forEach { it.join() }
        escaped.firstOrNull { it != null }?.let { throw it }
        for (r in results) println(lineOf(r!!))
    }

    private fun exhaustHeap() {
        val hoard = ArrayList<ByteArray>()
        while (true) hoard += ByteArray(8 * 1024 * 1024)
    }
}
