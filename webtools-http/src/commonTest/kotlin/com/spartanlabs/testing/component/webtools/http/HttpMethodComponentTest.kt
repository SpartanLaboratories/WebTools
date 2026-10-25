package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - `HttpMethod`: the seven constants and `of` returning the same instances, `of("get")`
 * distinct, equality, hash, `toString()`, `permitsBody`, the failure's constant message, and
 * `CONNECT` and `TRACE` accepted by `of`.
 */
class HttpMethodComponentTest {

    @Test
    fun `the seven constants are returned by of for their exact names`() {
        assertSame(HttpMethod.Get, HttpMethod.of("GET").getOrThrow())
        assertSame(HttpMethod.Head, HttpMethod.of("HEAD").getOrThrow())
        assertSame(HttpMethod.Post, HttpMethod.of("POST").getOrThrow())
        assertSame(HttpMethod.Put, HttpMethod.of("PUT").getOrThrow())
        assertSame(HttpMethod.Patch, HttpMethod.of("PATCH").getOrThrow())
        assertSame(HttpMethod.Delete, HttpMethod.of("DELETE").getOrThrow())
        assertSame(HttpMethod.Options, HttpMethod.of("OPTIONS").getOrThrow())
    }

    @Test
    fun `of is case-sensitive`() {
        val lower = HttpMethod.of("get").getOrThrow()
        assertNotEquals(HttpMethod.Get, lower)
        assertEquals("get", lower.name)
    }

    @Test
    fun `equality and hash are by name and toString is the name`() {
        val a = HttpMethod.of("M-SEARCH").getOrThrow()
        val b = HttpMethod.of("M-SEARCH").getOrThrow()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals("M-SEARCH", a.toString())
        assertEquals("GET", HttpMethod.Get.toString())
        assertNotEquals<Any>(HttpMethod.Get, "GET")
    }

    @Test
    fun `permitsBody is false for GET HEAD and TRACE only`() {
        assertFalse(HttpMethod.Get.permitsBody)
        assertFalse(HttpMethod.Head.permitsBody)
        assertFalse(HttpMethod.of("TRACE").getOrThrow().permitsBody)
        listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete, HttpMethod.Options, HttpMethod.of("PROPFIND").getOrThrow())
            .forEach { assertTrue(it.permitsBody, it.name) }
    }

    @Test
    fun `the failure is an IllegalArgumentException with the constant message`() {
        val exception = HttpMethod.of("A B").exceptionOrNull()
        assertIs<IllegalArgumentException>(exception)
        assertEquals("Method is not an RFC 9110 token", exception.message)
        assertEquals("Method is not an RFC 9110 token", HttpMethod.of("").exceptionOrNull()?.message)
    }

    @Test
    fun `CONNECT and TRACE are accepted by of`() {
        assertEquals("CONNECT", HttpMethod.of("CONNECT").getOrThrow().name)
        assertEquals("TRACE", HttpMethod.of("TRACE").getOrThrow().name)
    }
}
