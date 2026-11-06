package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.CallSpec
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RequestBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `CallSpec`: a new spec is seeded from the endpoint's lane, the client's wait and the client's default headers; `path` and `query`
 * append in call order and `query` keeps repeated names; `headers` edits one builder that starts as the default headers (`set` replaces, `remove`
 * drops, a sensitive flag is kept); an invalid header name throws `IllegalArgumentException` with no value in its message; every setter replaces
 * its seeded value.
 */
class CallSpecComponentTest {

    private val defaults: HttpHeaders = HttpHeaders {
        add("Accept", "a")
        addSensitive("Authorization", "old")
    }

    private fun spec(headers: HttpHeaders = HttpHeaders()): CallSpec = CallSpec(Lane.Tracker, 3.seconds, headers)

    @Test
    fun `a new spec is seeded from the endpoint, the client and the default headers`() {
        val spec = spec(defaults)
        assertEquals(HttpMethod.Get, spec.method)
        assertNull(spec.body)
        assertEquals(Lane.Tracker, spec.lane)
        assertEquals(3.seconds, spec.maxWait)
        assertEquals(Duration.INFINITE, spec.maxAge)
        assertTrue(spec.pathSegments().isEmpty())
        assertTrue(spec.queryPairs().isEmpty())
        assertEquals(defaults, spec.requestHeaders())
    }

    @Test
    fun `path appends segments in call order`() {
        val spec = spec()
        spec.path("a")
        spec.path("b", "c")
        assertEquals(listOf("a", "b", "c"), spec.pathSegments())
    }

    @Test
    fun `query appends pairs in order and keeps repeated names`() {
        val spec = spec()
        spec.query("x", "1")
        spec.query("y", "2")
        spec.query("x", "3")
        assertEquals(listOf("x" to "1", "y" to "2", "x" to "3"), spec.queryPairs())
    }

    @Test
    fun `headers starts as the client's default headers`() {
        val headers = spec(defaults).requestHeaders()
        assertEquals(listOf("Accept", "Authorization"), headers.entries().map { it.name })
        assertEquals("a", headers["Accept"])
        assertEquals("old", headers["Authorization"])
        assertFalse(headers.entries().single { it.name == "Accept" }.isSensitive)
        assertTrue(headers.entries().single { it.name == "Authorization" }.isSensitive)
    }

    @Test
    fun `several headers blocks edit one builder`() {
        val spec = spec()
        spec.headers { add("A", "1") }
        spec.headers { add("B", "2") }
        assertEquals(listOf("A" to "1", "B" to "2"), spec.requestHeaders().entries().map { it.name to it.value })
    }

    @Test
    fun `set replaces, remove drops and a sensitive flag is kept`() {
        val spec = spec(defaults)
        spec.headers {
            set("Authorization", "new")
            remove("Accept")
            setSensitive("X", "v")
        }
        val entries = spec.requestHeaders().entries()
        assertEquals(listOf("Authorization" to "new", "X" to "v"), entries.map { it.name to it.value })
        assertTrue(entries.single { it.name == "Authorization" }.isSensitive)
        assertTrue(entries.single { it.name == "X" }.isSensitive)
        assertFalse("Accept" in spec.requestHeaders())
    }

    @Test
    fun `an invalid header name inside headers throws IllegalArgumentException`() {
        val spec = spec(defaults)
        assertFailsWith<IllegalArgumentException> { spec.headers { add("bad name", "v") } }
        val message = assertFailsWith<IllegalArgumentException> { spec.headers { add("bad name", "SECRET") } }.message!!
        assertFalse("SECRET" in message, message)
        // A failing edit changes nothing.
        assertEquals(defaults, spec.requestHeaders())
    }

    @Test
    fun `the setters replace the seeded values`() {
        val body = RequestBody("abc".encodeToByteArray(), "text/plain")
        val spec = spec()
        spec.lane = Lane.Interactive
        spec.maxWait = 1.minutes
        spec.maxAge = 10.seconds
        spec.method = HttpMethod.Post
        spec.body = body
        assertEquals(Lane.Interactive, spec.lane)
        assertEquals(1.minutes, spec.maxWait)
        assertEquals(10.seconds, spec.maxAge)
        assertEquals(HttpMethod.Post, spec.method)
        assertSame(body, spec.body)
    }
}
