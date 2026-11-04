package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.Lane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * Level 2 - `Lane`: a blank name is refused with the property's message, equality and hash are on the name (case-sensitive),
 * `Interactive` and `Tracker` equal a lane built with the same name, and `toString()` is `Lane(<name>)`.
 */
class LaneComponentTest {

    @Test
    fun `a blank name is refused with the property in the message`() {
        listOf("", "  ", "\t").forEach { name ->
            assertEquals("name must not be blank", assertFailsWith<IllegalArgumentException> { Lane(name) }.message, "name '$name'")
        }
    }

    @Test
    fun `equality and hash are on the name, case-sensitively`() {
        assertEquals(Lane("a"), Lane("a"))
        assertEquals(Lane("a").hashCode(), Lane("a").hashCode())
        assertEquals("a".hashCode(), Lane("a").hashCode())
        assertNotEquals(Lane("a"), Lane("A"))
        assertNotEquals(Lane("a"), Lane("b"))
        assertNotEquals<Any>(Lane("a"), "a")
    }

    @Test
    fun `the two named lanes equal lanes built with the same name`() {
        assertEquals(Lane("Interactive"), Lane.Interactive)
        assertEquals(Lane("Tracker"), Lane.Tracker)
        assertEquals("Interactive", Lane.Interactive.name)
        assertEquals("Tracker", Lane.Tracker.name)
        assertNotEquals(Lane.Interactive, Lane.Tracker)
    }

    @Test
    fun `toString prints the name`() {
        assertEquals("Lane(Interactive)", Lane.Interactive.toString())
        assertEquals("Lane(x y)", Lane("x y").toString())
    }
}
