package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 2 - `HttpHeaders` and its builder: add/get/getAll, set (replace, append at the end, new
 * spelling, flag kept), setSensitive, remove, addAll, the credential names, a failed call changing
 * nothing, a leaked builder, `entries()` as a copy, equality, hash, `toString()` and `contains`.
 */
class HttpHeadersComponentTest {

    @Test
    fun `get returns the last value and getAll every value in order, folding ASCII case`() {
        val headers = HttpHeaders { add("A", "1"); add("B", "2"); add("a", "3") }
        assertEquals("3", headers["A"])
        assertEquals(listOf("1", "3"), headers.getAll("a"))
        assertEquals(listOf("2"), headers.getAll("b"))
        assertEquals(emptyList(), headers.getAll("none"))
        assertNull(headers["none"])
        assertEquals(3, headers.size)
        assertFalse(headers.isEmpty())
        assertTrue(HttpHeaders().isEmpty())
    }

    @Test
    fun `set replaces every entry and appends at the end with the new spelling`() {
        val headers = HttpHeaders { add("A", "1"); add("B", "2"); add("a", "3"); set("A", "9") }
        assertEquals(listOf("B", "A"), headers.entries().map { it.name })
        assertEquals(listOf("2", "9"), headers.entries().map { it.value })
        assertEquals(listOf("9"), headers.getAll("a"))
    }

    @Test
    fun `set and setSensitive on an absent name append one entry`() {
        val plain = HttpHeaders { add("A", "1"); set("B", "2") }
        assertEquals(listOf("A", "B"), plain.entries().map { it.name })
        assertFalse(plain.entries()[1].isSensitive)
        val sensitive = HttpHeaders { add("A", "1"); setSensitive("X-K", "s") }
        assertEquals(listOf("A", "X-K"), sensitive.entries().map { it.name })
        assertTrue(sensitive.entries()[1].isSensitive)
        assertEquals("s", sensitive["x-k"])
    }

    @Test
    fun `set keeps a sensitive flag and setSensitive sets one`() {
        val kept = HttpHeaders { addSensitive("X-K", "s"); set("x-k", "t") }
        assertEquals(1, kept.size)
        assertTrue(kept.entries().single().isSensitive)
        assertEquals("x-k", kept.entries().single().name)
        val plain = HttpHeaders { add("X-K", "s"); set("X-K", "t") }
        assertFalse(plain.entries().single().isSensitive)
        val flagged = HttpHeaders { add("X-K", "s"); setSensitive("X-K", "t") }
        assertTrue(flagged.entries().single().isSensitive)
        assertEquals("t", flagged["X-K"])
    }

    @Test
    fun `remove removes every entry of the name and is a no-op otherwise`() {
        val headers = HttpHeaders { add("A", "1"); add("B", "2"); add("a", "3"); remove("a"); remove("none") }
        assertEquals(listOf("B"), headers.entries().map { it.name })
    }

    @Test
    fun `addAll keeps the flags`() {
        val source = HttpHeaders { add("A", "1"); addSensitive("X-K", "s") }
        val copy = HttpHeaders { add("Z", "0"); addAll(source) }
        assertEquals(listOf("Z", "A", "X-K"), copy.entries().map { it.name })
        assertEquals(listOf(false, false, true), copy.entries().map { it.isSensitive })
    }

    @Test
    fun `the credential names are sensitive without a flag, in any case`() {
        listOf("Authorization", "authorization", "PROXY-AUTHORIZATION", "Cookie", "set-cookie").forEach { name ->
            assertTrue(HttpHeaders { add(name, "v") }.entries().single().isSensitive, name)
        }
        assertFalse(HttpHeaders { add("X-Other", "v") }.entries().single().isSensitive)
        assertFalse(HttpHeaders { add("Cookie2", "v") }.entries().single().isSensitive)
    }

    @Test
    fun `a failed call changes nothing`() {
        val headers = HttpHeaders {
            add("A", "1")
            assertFailsWith<IllegalArgumentException> { add("", "x") }
            assertFailsWith<IllegalArgumentException> { add("a b", "x") }
            assertFailsWith<IllegalArgumentException> { add("B", "a\nb") }
            assertFailsWith<IllegalArgumentException> { set("A", "a\rb") }
            assertFailsWith<IllegalArgumentException> { setSensitive("A", "a\u0000b") }
        }
        assertEquals(listOf("A"), headers.entries().map { it.name })
        assertEquals("1", headers["A"])
    }

    @Test
    fun `a built value does not change when a leaked builder is used later`() {
        var leaked: HttpHeaders.Builder? = null
        val headers = HttpHeaders { add("A", "1"); leaked = this }
        leaked!!.add("B", "2")
        leaked!!.remove("A")
        assertEquals(listOf("A"), headers.entries().map { it.name })
    }

    @Test
    fun `entries is a copy`() {
        val headers = HttpHeaders { add("A", "1") }
        val entries = headers.entries()
        assertTrue(headers.entries() !== entries)
        assertEquals(headers.entries(), entries)
        assertEquals(1, headers.entries().size)
    }

    @Test
    fun `equality, hash and toString`() {
        val a = HttpHeaders { add("Accept", "x"); addSensitive("X-Api-Key", "k"); add("Authorization", "b") }
        val b = HttpHeaders { add("Accept", "x"); addSensitive("X-Api-Key", "k"); add("Authorization", "b") }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, HttpHeaders { add("Accept", "y"); addSensitive("X-Api-Key", "k"); add("Authorization", "b") })
        assertNotEquals(a, HttpHeaders { add("Accept", "x"); add("X-Api-Key", "k"); add("Authorization", "b") })
        assertNotEquals(a, HttpHeaders { add("accept", "x"); addSensitive("X-Api-Key", "k"); add("Authorization", "b") })
        assertEquals("HttpHeaders(Accept, X-Api-Key*, Authorization*)", a.toString())
        assertFalse(a.toString().contains("x") && a.toString().contains("k"))
        assertEquals("Entry(Accept)", a.entries()[0].toString())
        assertEquals("Entry(X-Api-Key, sensitive)", a.entries()[1].toString())
        assertEquals("HttpHeaders()", HttpHeaders().toString())
    }

    @Test
    fun `contains folds ASCII only`() {
        val headers = HttpHeaders { add("Content-Type", "x") }
        assertTrue("content-type" in headers)
        assertTrue("CONTENT-TYPE" in headers)
        assertFalse("Content-Length" in headers)
        assertFalse("" in headers)
    }
}
