package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.RequestMatch
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level 2 - `RequestMatch` in isolation: the factories `get`, `request`, `path`, `header`, the combinator
 * `and` and the value `Any`; exact path comparison; method mismatch; the query-pair semantics (order and
 * extras ignored, repeated names, `+` and `%XX` decoding, a name without `=`, an empty expected list); and
 * header matching (ASCII-insensitive name, exact value, every entry of a repeated name).
 */
class RequestMatchComponentTest {

    private fun request(url: String, method: HttpMethod = HttpMethod.Get, headers: HttpHeaders = HttpHeaders()): HttpRequest =
        HttpRequest(method, Url.parse(url).getOrThrow()) { this.headers = headers }

    @Test
    fun `get matches the method and the exact path`() {
        val match = RequestMatch.get("/api/players/1")
        assertTrue(match.matches(request("https://h/api/players/1")))
        assertTrue(match.matches(request("https://h/api/players/1?anything=1")))
        assertFalse(match.matches(request("https://h/api/players/1/")))
        assertFalse(match.matches(request("https://h/api/players/10")))
        assertFalse(match.matches(request("https://h/api/players/1", HttpMethod.Post)))
    }

    @Test
    fun `request matches a given method and path matches any method`() {
        assertTrue(RequestMatch.request(HttpMethod.Post, "/x").matches(request("https://h/x", HttpMethod.Post)))
        assertFalse(RequestMatch.request(HttpMethod.Post, "/x").matches(request("https://h/x", HttpMethod.Put)))
        assertTrue(RequestMatch.path("/x").matches(request("https://h/x", HttpMethod.Delete)))
        assertTrue(RequestMatch.path("/x").matches(request("https://h/x", HttpMethod.Get)))
    }

    @Test
    fun `the path is compared exactly with the encoded path`() {
        assertTrue(RequestMatch.path("/a%20b").matches(request("https://h/a%20b")))
        assertFalse(RequestMatch.path("/a b").matches(request("https://h/a%20b")))
        assertFalse(RequestMatch.path("/A").matches(request("https://h/a")))
    }

    @Test
    fun `query pairs ignore order and extra pairs and match any value of a repeated name`() {
        val match = RequestMatch.path("/q", "a" to "1", "b" to "2")
        assertTrue(match.matches(request("https://h/q?a=1&b=2")))
        assertTrue(match.matches(request("https://h/q?b=2&a=1")))
        assertTrue(match.matches(request("https://h/q?b=2&c=3&a=1")))
        assertFalse(match.matches(request("https://h/q?a=1")))
        assertFalse(match.matches(request("https://h/q?a=1&b=3")))
        assertTrue(RequestMatch.path("/q", "a" to "2").matches(request("https://h/q?a=1&a=2")))
    }

    @Test
    fun `query values are decoded as OkHttp decodes them`() {
        assertTrue(RequestMatch.path("/q", "name" to "a b").matches(request("https://h/q?name=a+b")))
        assertTrue(RequestMatch.path("/q", "name" to "a b").matches(request("https://h/q?name=a%20b")))
        assertTrue(RequestMatch.path("/q", "n" to "café").matches(request("https://h/q?n=caf%C3%A9")))   // U+00E9
        assertTrue(RequestMatch.path("/q", "n" to "café").matches(request("https://h/q?n=café")))
        assertTrue(RequestMatch.path("/q", "a b" to "1").matches(request("https://h/q?a+b=1")))
    }

    @Test
    fun `a name without an equals sign never matches an expected pair`() {
        assertFalse(RequestMatch.path("/q", "flag" to "").matches(request("https://h/q?flag")))
        assertTrue(RequestMatch.path("/q", "flag" to "").matches(request("https://h/q?flag=")))
    }

    @Test
    fun `an empty expected query matches any query or none`() {
        assertTrue(RequestMatch.path("/q").matches(request("https://h/q")))
        assertTrue(RequestMatch.path("/q").matches(request("https://h/q?")))
        assertTrue(RequestMatch.path("/q").matches(request("https://h/q?a=1&b")))
    }

    @Test
    fun `header ignores the name's ASCII case compares the value exactly and sees every entry`() {
        val headers = HttpHeaders { add("Accept", "text/html"); add("accept", "application/json") }
        val req = request("https://h/", headers = headers)
        assertTrue(RequestMatch.header("ACCEPT", "application/json").matches(req))
        assertTrue(RequestMatch.header("accept", "text/html").matches(req))
        assertFalse(RequestMatch.header("Accept", "TEXT/HTML").matches(req))
        assertFalse(RequestMatch.header("Accept", "text/plain").matches(req))
        assertFalse(RequestMatch.header("X-Other", "x").matches(req))
    }

    @Test
    fun `and combines and Any accepts everything`() {
        val req = request("https://h/x?k=1", HttpMethod.Post, HttpHeaders { add("If-None-Match", "\"v1\"") })
        assertTrue(RequestMatch.Any.matches(req))
        assertTrue((RequestMatch.path("/x", "k" to "1") and RequestMatch.header("If-None-Match", "\"v1\"")).matches(req))
        assertFalse((RequestMatch.path("/x") and RequestMatch.header("If-None-Match", "\"v2\"")).matches(req))
        assertFalse((RequestMatch.Any and RequestMatch { false }).matches(req))
    }
}
