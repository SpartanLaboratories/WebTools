package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpSyntax
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level 4a - the header differential against OkHttp 5.5.0: a value is sendable iff
 * `Request.Builder().url("http://h/").addHeader("X", v)` does not throw, and then `header("X")` equals
 * the stored value, over the U+0000..U+FFFF sweep; every name `HttpSyntax.isToken` accepts is accepted by
 * `Request.Builder.addHeader`; and the documented gap is pinned (OkHttp accepts `:path`, `a(b`, `a/b`,
 * `a"b`; the types refuse them).
 */
class HeaderOkHttpDifferentialDeterministicTest {

    private fun okHttpAccepts(name: String, value: String): String? = try {
        Request.Builder().url("http://h/").addHeader(name, value).build().header(name)
    } catch (e: IllegalArgumentException) {
        null
    }

    @Test
    fun `sendable iff OkHttp accepts the value, and the stored values agree`() {
        for (code in 0..0xFFFF) {
            val c = code.toChar()
            val value = "a" + c + "b"
            val label = "character U+${code.toString(16).padStart(4, '0')}"
            val theirs = okHttpAccepts("X", value)
            assertEquals(theirs != null, HttpSyntax.isSendableValue(value), label)
            if (theirs != null) assertEquals(theirs, HttpHeaders { add("X", value) }["X"], label)
        }
    }

    @Test
    fun `every name the types accept is accepted by OkHttp`() {
        for (code in 0..0xFFFF) {
            val name = code.toChar().toString()
            if (HttpSyntax.isToken(name)) {
                assertEquals("v", okHttpAccepts(name, "v"), "name U+${code.toString(16).padStart(4, '0')}")
            }
        }
        listOf("X-Test", "A_b-c.d~e", "!#\$%&'*+-.^_`|~").forEach { assertEquals("v", okHttpAccepts(it, "v"), it) }
    }

    @Test
    fun `the documented gap where OkHttp accepts names the types refuse`() {
        listOf(":path", "a(b", "a/b", "a\"b").forEach { name ->
            assertEquals("v", okHttpAccepts(name, "v"), name)
            assertFalse(HttpSyntax.isToken(name), name)
            assertTrue(runCatching { HttpHeaders { add(name, "v") } }.isFailure, name)
        }
    }
}
