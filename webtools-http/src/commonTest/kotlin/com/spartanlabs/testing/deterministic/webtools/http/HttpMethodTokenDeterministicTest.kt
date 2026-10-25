package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Level 4a - the accepted and refused rows of the research's method table for `HttpMethod.of`. */
class HttpMethodTokenDeterministicTest {

    @Test
    fun `every token name is accepted by of`() {
        assertSame(HttpMethod.Get, HttpMethod.of("GET").getOrThrow())
        listOf("get", "PATCH", "M-SEARCH", "PROPFIND", "X_Y", "a.b", "~", "123", "CONNECT", "TRACE").forEach { name ->
            assertEquals(name, HttpMethod.of(name).getOrThrow().name, name)
        }
        assertTrue(HttpMethod.of("get").getOrThrow() != HttpMethod.Get)
    }

    @Test
    fun `a non-token name is refused with the constant message`() {
        listOf("", "A B", "A\r\nB", "GÉT", "X(Y", "GET/", "A,B", "a:b", " GET", "GET ").forEach { name ->
            val exception = HttpMethod.of(name).exceptionOrNull()
            assertIs<IllegalArgumentException>(exception, name)
            assertEquals("Method is not an RFC 9110 token", exception.message, name)
        }
    }
}
