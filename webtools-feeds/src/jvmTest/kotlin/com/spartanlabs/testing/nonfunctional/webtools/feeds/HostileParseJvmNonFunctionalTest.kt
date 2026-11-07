package com.spartanlabs.testing.nonfunctional.webtools.feeds

import com.spartanlabs.testing.support.webtools.feeds.HostileParseJvm
import com.spartanlabs.testing.support.webtools.feeds.RomeParserFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 4c - the fixed-heap launcher itself, with cases that need no parser: a child that finishes exits 0 and its result lines are
 * read; a child that exhausts its heap exits with code 3; a child that outlives its budget is destroyed and reports no exit code.
 */
class HostileParseJvmNonFunctionalTest {

    @Test
    fun `a child that finishes exits 0 and its result lines are read`() {
        val outcome = HostileParseJvm.run(RomeParserFactory::class.java, "smoke", 1)
        assertEquals(0, outcome.exitCode, outcome.toString())
        assertEquals(listOf("smoke:ok"), outcome.lines)
        assertTrue(outcome.elapsed < 30.seconds)
    }

    @Test
    fun `a child that exhausts its heap exits with code 3`() {
        val outcome = HostileParseJvm.run(RomeParserFactory::class.java, "oom", 1, heapMegabytes = 64)
        assertEquals(HostileParseJvm.EXIT_OUT_OF_MEMORY, outcome.exitCode, outcome.toString())
    }

    @Test
    fun `a child that outlives its budget is destroyed and reports no exit code`() {
        val outcome = HostileParseJvm.run(RomeParserFactory::class.java, "hang", 1, budget = 2.seconds)
        assertNull(outcome.exitCode, outcome.toString())
        assertTrue(outcome.elapsed >= 2.seconds, outcome.elapsed.toString())
        assertTrue(outcome.elapsed < 20.seconds, outcome.elapsed.toString())
    }
}
