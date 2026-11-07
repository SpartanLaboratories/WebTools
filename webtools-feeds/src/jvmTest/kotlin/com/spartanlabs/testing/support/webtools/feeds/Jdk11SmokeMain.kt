package com.spartanlabs.testing.support.webtools.feeds

import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedLimits
import com.spartanlabs.webtools.http.Url
import kotlin.system.exitProcess

/**
 * The main class of the JDK 11 smoke run: built through the `ParserFactory` named by its first argument (the other arguments are
 * ignored), it parses a valid RSS 2.0 document with two items, a valid Atom 1.0 document with one entry, an RSS document nested
 * 257 levels and a document carrying a DOCTYPE, and prints one result line per parse in [HostileParseMain]'s format: `ok:2`,
 * `ok:1`, `LimitExceeded:Depth`, `Refused:Doctype`. The only guard against the JDK 11 property-name fault, which the build JDK
 * cannot show. Exits `0` after printing, `12` on an escaped throwable.
 */
object Jdk11SmokeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val factoryName = args.getOrNull(0) ?: exitProcess(HostileParseJvm.EXIT_THROWN)
        val outcome = runCatching {
            val parser = (Class.forName(factoryName).getDeclaredConstructor().newInstance() as ParserFactory).create(FeedLimits.Defaults)
            val base = Url.parse("https://feeds.example.test/feed.xml").getOrThrow()
            val documents = listOf(
                HostileDocuments.items(2),
                ("<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>t</title><entry><id>urn:e</id><title>e</title></entry></feed>").encodeToByteArray(),
                HostileDocuments.rssNested(257),
                "<!DOCTYPE rss SYSTEM \"http://127.0.0.1:1/x.dtd\"><rss version=\"2.0\"><channel><title>t</title></channel></rss>".encodeToByteArray(),
            )
            for (document in documents) println(HostileParseMain.lineOf(parser.parse(FeedInput(document, "application/rss+xml", base))))
        }
        outcome.onFailure { println("thrown:${it::class.java.name}"); System.out.flush(); exitProcess(HostileParseJvm.EXIT_THROWN) }
        System.out.flush()
        exitProcess(0)
    }
}
