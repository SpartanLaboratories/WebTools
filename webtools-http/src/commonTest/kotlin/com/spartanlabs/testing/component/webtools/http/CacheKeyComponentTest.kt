package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.CacheKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Level 2 - `CacheKey`: a blank id is refused with the constant message; the one-argument constructor has no parameters;
 * the key copies the caller's list and never aliases it; `toString()` prints the id and the parameter names (a repeated name
 * repeated) and never a value; `equals` with `null` and with a non-key is `false`.
 */
class CacheKeyComponentTest {

    @Test
    fun `a blank endpoint id is refused with the constant message`() {
        listOf("", "  ").forEach { id ->
            val exception = assertFailsWith<IllegalArgumentException> { CacheKey<String>(id, emptyList()) }
            assertEquals("endpointId must not be blank", exception.message)
            assertEquals("endpointId must not be blank", assertFailsWith<IllegalArgumentException> { CacheKey<String>(id) }.message)
        }
    }

    @Test
    fun `the one-argument constructor has no parameters`() {
        val key = CacheKey<String>("endpoint")
        assertEquals("endpoint", key.endpointId)
        assertEquals(emptyList(), key.params)
    }

    @Test
    fun `the key copies the caller's list and never aliases it`() {
        val callers = mutableListOf("b" to "2", "a" to "1")
        val key = CacheKey<String>("endpoint", callers)
        assertNotSame<List<Pair<String, String>>>(callers, key.params)
        callers.add("c" to "3")
        callers[0] = "z" to "26"
        assertEquals(listOf("a" to "1", "b" to "2"), key.params)
        assertEquals(key, CacheKey<String>("endpoint", listOf("a" to "1", "b" to "2")))
    }

    @Test
    fun `toString prints the id and the parameter names only`() {
        val key = CacheKey<String>("players", listOf("region" to "SECRET-REGION", "id" to "SECRET-ID", "region" to "SECRET-TWO"))
        assertEquals("CacheKey(endpointId=players, params=[id, region, region])", key.toString())
        assertFalse("SECRET" in key.toString())
        assertEquals("CacheKey(endpointId=players, params=[])", CacheKey<String>("players").toString())
    }

    @Test
    fun `equals is false for null and for a non-key`() {
        val key = CacheKey<String>("endpoint", listOf("a" to "1"))
        assertFalse(key.equals(null))
        assertFalse(key.equals("CacheKey(endpointId=endpoint, params=[a])"))
        assertTrue(key.equals(key))
    }
}
