package com.spartanlabs.testing.deterministic.webtools.buildlogic

import WebToolsBuild
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Level 4a - pure input-to-output checks over the Java floor constants of [WebToolsBuild]: the floor
 * itself, the task name, and the mapping from a Java release to its class-file major version.
 */
class WebToolsBuildJavaFloorDeterministicTest {

    @Test
    fun `the Java floor is 11 and its class-file major version is 55`() {
        assertEquals(11, WebToolsBuild.JAVA_FLOOR)
        assertEquals(55, WebToolsBuild.classFileMajor(WebToolsBuild.JAVA_FLOOR))
    }

    @Test
    fun `a Java release maps to its class-file major version`() {
        // The values the JVM specification gives: Java 8 is 52, 11 is 55, 17 is 61, 21 is 65, 23 is 67.
        mapOf(8 to 52, 11 to 55, 17 to 61, 21 to 65, 23 to 67).forEach { (release, major) ->
            assertEquals(major, WebToolsBuild.classFileMajor(release), "Java $release")
        }
    }

    @Test
    fun `the guard task is named verifyJavaFloor`() {
        assertEquals("verifyJavaFloor", WebToolsBuild.VERIFY_JAVA_FLOOR_TASK)
    }
}
