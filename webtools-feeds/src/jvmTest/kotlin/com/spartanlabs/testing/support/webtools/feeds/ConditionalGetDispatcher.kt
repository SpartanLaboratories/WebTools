package com.spartanlabs.testing.support.webtools.feeds

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okio.Buffer

/**
 * The routes of the Level 3 class, served by a `MockWebServer`: `/rss` and `/atom` answer a `200` RSS 2.0 or Atom 1.0 body with
 * `ETag` and `Last-Modified` to a request without a matching `If-None-Match`, and a `304` carrying the same validators to a
 * matching one; `/moved` is a `301` to `/rss`; `/temp` a `302` to `/rss`; `/big304` a `304` declaring `Content-Length: 5000000`
 * and no body; `/gone` a `410`; `/throttled` a `429` with `Retry-After: 30`; `/huge` a `200` body of 2 MiB and one byte; `/page`
 * a `200` HTML page; `/doctype` a `200` feed body that carries a DOCTYPE; anything else a `404`.
 */
class ConditionalGetDispatcher : Dispatcher() {
    /** The entity tag the feed routes serve. */
    val etag: String = "\"feed-v1\""

    /** The `Last-Modified` the feed routes serve. */
    val lastModified: String = "Fri, 06 Nov 2026 04:25:27 GMT"

    /** The RSS body of `/rss`: two items. */
    val rss: String = rssBody(2, "Loopback RSS")

    /** The Atom body of `/atom`: one entry. */
    val atom: String = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><feed xmlns=\"http://www.w3.org/2005/Atom\"><title>Loopback Atom</title>" +
        "<link rel=\"alternate\" href=\"https://feeds.example.test/\"/><entry><id>urn:atom:1</id><title>Entry 1</title>" +
        "<link href=\"https://feeds.example.test/atom/1\"/><updated>2026-11-06T04:25:27Z</updated></entry></feed>"

    override fun dispatch(request: RecordedRequest): MockResponse =
        when (request.url.encodedPath) {
            "/rss" -> feed(request, rss, "application/rss+xml")
            "/atom" -> feed(request, atom, "application/atom+xml")
            "/moved" -> MockResponse.Builder().code(301).addHeader("Location", "/rss").build()
            "/temp" -> MockResponse.Builder().code(302).addHeader("Location", "/rss").build()
            "/big304" -> MockResponse.Builder().code(304).clearHeaders().addHeader("ETag", etag).addHeader("Content-Length", "5000000").build()
            "/gone" -> MockResponse.Builder().code(410).body("gone").build()
            "/throttled" -> MockResponse.Builder().code(429).addHeader("Retry-After", "30").body("slow down").build()
            "/huge" -> MockResponse.Builder().code(200).addHeader("Content-Type", "application/rss+xml").body(Buffer().write(ByteArray(2 * 1024 * 1024 + 1) { 0x61 })).build()
            "/page" -> MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<!DOCTYPE html><html><body>Not a feed</body></html>").build()
            "/doctype" -> MockResponse.Builder().code(200).addHeader("Content-Type", "application/rss+xml")
                .body("<?xml version=\"1.0\"?><!DOCTYPE rss SYSTEM \"http://127.0.0.1:1/x.dtd\">" + rss.substringAfter("?>")).build()
            else -> MockResponse.Builder().code(404).body("no such route").build()
        }

    private fun feed(request: RecordedRequest, body: String, contentType: String): MockResponse =
        if (request.headers["If-None-Match"] == etag) {
            MockResponse.Builder().code(304).clearHeaders().addHeader("ETag", etag).addHeader("Last-Modified", lastModified).build()
        } else {
            MockResponse.Builder().code(200).addHeader("Content-Type", contentType).addHeader("ETag", etag).addHeader("Last-Modified", lastModified).body(body).build()
        }
}
