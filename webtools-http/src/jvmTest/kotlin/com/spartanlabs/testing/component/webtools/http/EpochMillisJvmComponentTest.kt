package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.epochMillisNow
import kotlin.test.Test
import kotlin.test.assertTrue

/** Level 2 - the JVM actual `epochMillisNow()` is within 1 s of `System.currentTimeMillis()` read before and after. */
class EpochMillisJvmComponentTest {

    @Test
    fun `the reading is between two system clock reads, within a second`() {
        val before = System.currentTimeMillis()
        val reading = epochMillisNow()
        val after = System.currentTimeMillis()
        assertTrue(reading >= before - 1_000L && reading <= after + 1_000L, "reading $reading is not within 1 s of $before..$after")
    }
}
