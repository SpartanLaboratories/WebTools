package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.ChildJvm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Level 3 - the JVM exits with a transport in it: a child JVM that makes one call and closes the transport exits with code 0 within 20 s,
 * and so does one that never closes it (the dispatcher's threads are daemon threads). A child that is still alive after 20 s fails the test
 * with a message that the transport kept the JVM alive.
 */
class JvmExitIntegrationTest {

    private fun assertExits(mode: String) {
        val outcome = ChildJvm.run(mode)
        assertTrue(outcome.exited, "the transport kept the JVM alive: the '$mode' child was still running after ${outcome.elapsed}")
        assertEquals(0, outcome.exitCode, "the '$mode' child exited with ${outcome.exitCode} after ${outcome.elapsed}")
    }

    @Test
    fun `a JVM that closes the transport exits`() = assertExits("close")

    @Test
    fun `a JVM that never closes the transport exits too`() = assertExits("noclose")
}
