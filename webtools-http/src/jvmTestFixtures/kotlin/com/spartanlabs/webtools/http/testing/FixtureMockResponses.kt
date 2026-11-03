package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpSyntax
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.QueueDispatcher
import okio.Buffer

private const val CHUNK_BYTES: Int = 8192

/**
 * Writes this fixture as a `mockwebserver3.MockResponse` that cannot hang or corrupt the exchange. The status line is `HTTP/1.1 <status>
 * <reason>` with the fixture codec's reason phrase (none for an uncommon status); every header is written as given, in order, except
 * `Content-Length` and `Transfer-Encoding`, which the framing decides: a `204` or `205` gets no body and no `Content-Length`; a `304`, or any
 * response to a `HEAD`, gets no body and keeps the declared length (for `HEAD`, the body size when none was declared); a fixture carrying
 * `Transfer-Encoding` is framed as chunked; every other fixture gets its body and `Content-Length` from the bytes. `Content-Length` is written
 * after the other headers. A fixture carrying `Content-Encoding: gzip` must hold encoded bytes: a mismatch fails the exchange fast with an
 * `IOException`, never hangs.
 *
 * **Stable Core, source compatibility only.**
 *
 * @param method the request method the response answers; `GET` by default. Only a response to `HEAD` is written differently.
 * @return the response, ready to enqueue.
 * @throws IllegalArgumentException for a `1xx` fixture (never the final response of an exchange), or a `204`/`205` fixture with a body.
 */
public fun Fixture.toMockResponse(method: HttpMethod = HttpMethod.Get): MockResponse {
    require(status !in 100..199) { "A 1xx fixture cannot be the final response of an exchange." }
    require((status != 204 && status != 205) || bodySize == 0) { "A 204 or 205 fixture must have an empty body." }
    val bytes = body()
    val chunked = "Transfer-Encoding" in headers
    val declared = headers["Content-Length"]                           // the fixture's own text, kept verbatim for 304 and HEAD
    val builder = MockResponse.Builder().clearHeaders()                   // the default Content-Length: 0 must go
        .status("HTTP/1.1 $status ${HttpMessageCodec.reasonPhrase(status).orEmpty()}")   // always three parts: MockResponse.message reads the third
    headers.entries()
        .filterNot { HttpSyntax.equalsName(it.name, "Content-Length") || HttpSyntax.equalsName(it.name, "Transfer-Encoding") }
        .forEach { builder.addHeaderLenient(it.name, it.value) }          // lenient: a fixture's UTF-8 value round-trips; names are tokens
    when {
        status == 204 || status == 205 -> Unit                           // no body, no Content-Length (OkHttp rejects a non-zero one)
        status == 304 || method == HttpMethod.Head ->                    // no body; the declared length is kept, or derived from the body for HEAD
            (declared ?: bytes.size.toString().takeIf { method == HttpMethod.Head })?.let { builder.addHeaderLenient("Content-Length", it) }
        chunked -> builder.chunkedBody(Buffer().write(bytes), CHUNK_BYTES)   // a fixture that had Transfer-Encoding is framed as chunked
        else -> builder.body(Buffer().write(bytes))                       // sets Content-Length from the bytes (0 for an empty body)
    }
    return builder.build()
}

/**
 * Queues [fixture], written by [toMockResponse], on this server's default queue dispatcher.
 *
 * **Stable Core, source compatibility only.** A server whose dispatcher was replaced (a scripted `mockwebserver3.Dispatcher`) is refused: the
 * custom dispatcher decides its own responses, and it may call `toMockResponse` itself.
 *
 * @param fixture the response to queue.
 * @param method the request method it answers; `GET` by default.
 * @throws IllegalArgumentException as [toMockResponse] does.
 * @throws IllegalStateException if the server's dispatcher is not the default `QueueDispatcher`.
 */
public fun MockWebServer.enqueue(fixture: Fixture, method: HttpMethod = HttpMethod.Get): Unit {
    check(dispatcher is QueueDispatcher) { "enqueue(fixture) needs the default queue dispatcher; this server has a custom one." }
    enqueue(fixture.toMockResponse(method))
}
