package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.Lookup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * Level 2 - `Lookup`: `Found(v)` weighs 1; a weight below 1 is refused with the constant message, 1 and `Int.MAX_VALUE`
 * are accepted; equality and `hashCode` are over value and weight; `toString()` prints the weight and never the value;
 * `Missing.toString()` is `Missing`; a `Lookup<String>` is assignable to a `Lookup<Any>` (covariance).
 */
class LookupComponentTest {

    @Test
    fun `the one-argument Found weighs 1`() {
        val found = Lookup.Found("value")
        assertEquals("value", found.value)
        assertEquals(1, found.weight)
    }

    @Test
    fun `a weight below 1 is refused and 1 and the maximum are accepted`() {
        listOf(0, -1).forEach { weight ->
            assertEquals("weight must be at least 1", assertFailsWith<IllegalArgumentException> { Lookup.Found("v", weight) }.message)
        }
        assertEquals(1, Lookup.Found("v", 1).weight)
        assertEquals(Int.MAX_VALUE, Lookup.Found("v", Int.MAX_VALUE).weight)
    }

    @Test
    fun `equality and hashCode are over value and weight`() {
        assertEquals(Lookup.Found("v", 2), Lookup.Found("v", 2))
        assertEquals(Lookup.Found("v", 2).hashCode(), Lookup.Found("v", 2).hashCode())
        assertEquals(Lookup.Found("v"), Lookup.Found("v", 1))
        assertNotEquals(Lookup.Found("v", 2), Lookup.Found("v", 3))
        assertNotEquals(Lookup.Found("v", 2), Lookup.Found("w", 2))
        assertFalse(Lookup.Found("v").equals(null))
        assertFalse(Lookup.Found("v").equals(Lookup.Missing))
    }

    @Test
    fun `toString prints the weight and never the value`() {
        val found = Lookup.Found("SECRET-VALUE", 3)
        assertEquals("Found(weight=3)", found.toString())
        assertFalse("SECRET" in found.toString())
        assertEquals("Missing", Lookup.Missing.toString())
    }

    @Test
    fun `a lookup is covariant in its value type`() {
        val narrow: Lookup<String> = Lookup.Found("v")
        val wide: Lookup<Any> = narrow
        assertSame(narrow, wide)
        val missing: Lookup<String> = Lookup.Missing
        assertSame(Lookup.Missing, missing)
    }
}
