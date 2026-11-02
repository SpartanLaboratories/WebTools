package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.requiresRequestBody
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 4a - the body-required method list against OkHttp 5.5.0 itself: for each method, `requiresRequestBody(name)` equals
 * "`Request.Builder().url(...).method(name, null)` throws `IllegalArgumentException`".
 */
class BodyRequiredMethodDifferentialDeterministicTest {

    private val methods = listOf(
        "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE", "PROPFIND", "PROPPATCH", "REPORT", "QUERY",
        "MKCOL", "COPY", "MOVE", "LOCK", "UNLOCK", "SEARCH", "LINK", "PURGE",
    )

    private fun okHttpRequiresBody(name: String): Boolean =
        try {
            Request.Builder().url("http://h.test/").method(name, null)
            false
        } catch (_: IllegalArgumentException) {
            true
        }

    @Test
    fun `the list agrees with OkHttp for every method`() {
        methods.forEach { name -> assertEquals(okHttpRequiresBody(name), requiresRequestBody(name), name) }
    }

    @Test
    fun `the six are exactly the ones OkHttp requires a body for`() {
        assertEquals(setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT", "QUERY"), methods.filter(::okHttpRequiresBody).toSet())
    }
}
