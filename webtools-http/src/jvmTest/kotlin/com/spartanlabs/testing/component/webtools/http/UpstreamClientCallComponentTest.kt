package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.BASE_URL
import com.spartanlabs.testing.support.webtools.http.clientOn
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.RequestBody
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level 2 - a call of `UpstreamClient` on production defaults over the fake transport: a `GET` is decoded into `Found` and the transport receives
 * the base path, the segments and the query; one trailing slash of the base path is dropped when segments follow and the base is requested as it is
 * without segments; the default headers reach the transport with the per-call ones after them; a `POST` carries its body and reaches the transport
 * on every call.
 */
class UpstreamClientCallComponentTest {

    @Test
    fun `a GET is decoded into Found and the transport sees the base path, the segments and the query`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.get("/v1/players/7", "region" to "eu"), Step.respond(200, body = "Ada"))
        clientOn(clock, fake).use { client ->
            val lookup = client.call(stringEndpoint()) { path("players", "7"); query("region", "eu") }.getOrThrow()

            assertEquals(Lookup.Found("Ada"), lookup)
            val request = fake.calls.single().request
            assertEquals("https://api.example.test/v1/players/7?region=eu", request.url.toString())
            assertEquals(HttpMethod.Get, request.method)
            assertEquals(BASE_URL, client.baseUrl)
        }
    }

    @Test
    fun `a trailing slash on the base path is dropped when segments follow`() = runTest {
        val clock = virtualClock()
        listOf("https://api.example.test/v1/", "https://api.example.test/v1").forEach { base ->
            val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.get("/v1/players"), respondWith(200, "ok"))
            UpstreamClient(fake, Url.parse(base).getOrThrow()) { timeSource = clock.timeSource; wallClock = clock.wallClock }.use { client ->
                assertEquals(Lookup.Found("ok"), client.call(stringEndpoint()) { path("players") }.getOrThrow(), "base $base")
                assertEquals("/v1/players", fake.calls.single().request.url.encodedPath, "base $base")
            }
        }
    }

    @Test
    fun `a base path with no segments is requested as it is`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "ok"))
        clientOn(clock, fake).use { client ->
            client.call(stringEndpoint()).getOrThrow()
            client.call(stringEndpoint()) { query("a", "1") }.getOrThrow()

            assertEquals(listOf("https://api.example.test/v1/", "https://api.example.test/v1/?a=1"), fake.calls.map { it.request.url.toString() })
        }
    }

    @Test
    fun `default headers reach the transport and a per-call header is added`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "ok"))
        val defaults = HttpHeaders { add("Accept", "application/json"); addSensitive("Authorization", "t") }
        clientOn(clock, fake) { headers = defaults }.use { client ->
            client.call(stringEndpoint()) { headers { add("X-Trace", "1") } }.getOrThrow()

            val entries = fake.calls.single().request.headers.entries()
            assertEquals(listOf("Accept" to "application/json", "Authorization" to "t", "X-Trace" to "1"), entries.map { it.name to it.value })
            assertTrue(entries.single { it.name == "Authorization" }.isSensitive)
            assertFalse(entries.single { it.name == "X-Trace" }.isSensitive)
        }
    }

    @Test
    fun `a POST carries its body and reaches the transport on every call`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.request(HttpMethod.Post, "/v1/items"), respondWith(200, "made"))
        clientOn(clock, fake).use { client ->
            repeat(2) {
                val lookup = client.call(stringEndpoint()) {
                    path("items")
                    method = HttpMethod.Post
                    body = RequestBody("abc".encodeToByteArray(), "text/plain")
                }.getOrThrow()
                assertEquals(Lookup.Found("made"), lookup)
            }

            assertEquals(2, fake.calls.size)
            fake.calls.forEach { call ->
                assertEquals(HttpMethod.Post, call.request.method)
                assertEquals(3, call.request.body?.size)
                assertEquals("text/plain", call.request.body?.contentType)
            }
        }
    }
}
