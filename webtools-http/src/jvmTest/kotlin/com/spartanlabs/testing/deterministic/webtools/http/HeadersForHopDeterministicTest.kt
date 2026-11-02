package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.headersForHop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Level 4a - what a followed hop carries: a different host, a different scheme on one host, a different port and a subdomain remove
 * every `isSensitive` entry (the flagged `X-Api-Key`, `authorization` in any case, `Proxy-Authorization`, `Cookie`, `Set-Cookie`) and keep
 * the rest in order (`If-None-Match`, `If-Modified-Since`, `If-Range`, `Range`, `Accept`, a duplicated custom header); the same host
 * with the default port spelled out returns the same instance; `A -> B -> A` applied twice keeps the entries removed.
 */
class HeadersForHopDeterministicTest {

    private val from = url("https://a.example/p")
    private val headers = HttpHeaders {
        add("If-None-Match", "\"v1\"")
        addSensitive("X-Api-Key", "k")
        add("If-Modified-Since", "Sat, 01 Nov 2025 00:00:00 GMT")
        add("authorization", "Bearer t")
        add("If-Range", "\"v1\"")
        add("Proxy-Authorization", "Basic x")
        add("Range", "bytes=0-9")
        add("Cookie", "a=1")
        add("Accept", "*/*")
        add("Set-Cookie", "b=2")
        add("X-Trace", "1")
        add("X-Trace", "2")
    }
    private val kept = listOf("If-None-Match", "If-Modified-Since", "If-Range", "Range", "Accept", "X-Trace", "X-Trace")

    @Test
    fun `a changed host scheme port or subdomain removes every sensitive entry and keeps the rest in order`() {
        listOf("https://b.example/x", "http://a.example/x", "https://a.example:8443/x", "https://www.a.example/x").forEach { target ->
            val next = headersForHop(headers, from, url(target))
            assertEquals(kept, next.entries().map { it.name }, target)
            assertEquals(listOf("1", "2"), next.getAll("X-Trace"), target)
        }
    }

    @Test
    fun `the same origin with the default port spelled out returns the same instance`() {
        assertSame(headers, headersForHop(headers, from, url("https://a.example:443/other?q=1")))
        assertSame(headers, headersForHop(headers, from, from))
    }

    @Test
    fun `A to B to A keeps the entries removed`() {
        val b = url("https://b.example/x")
        val atB = headersForHop(headers, from, b)
        val backAtA = headersForHop(atB, b, from)
        assertEquals(kept, backAtA.entries().map { it.name })
        assertSame(atB, headersForHop(atB, b, url("https://b.example/y")))
    }
}
