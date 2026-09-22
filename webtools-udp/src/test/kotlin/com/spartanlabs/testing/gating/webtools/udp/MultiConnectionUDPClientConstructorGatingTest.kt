package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.MultiConnectionUDPClient
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue

// Level 1 - mirrors IdleTimeoutValidationGatingTest's server constructor-arity lock, for the
// client. Plain java.lang.Class reflection only (no kotlin-reflect, §12 A1): a later "tidy-up"
// that changes MultiConnectionUDPClient's public or internal-seam constructor arity cannot pass
// silently.
@Tag("gating")
class MultiConnectionUDPClientConstructorGatingTest {

    @Test
    fun `the declared constructor set is exactly the four JvmOverloads arities plus the internal seam constructor`() {
        val signatures = MultiConnectionUDPClient::class.java.declaredConstructors
            // Kotlin emits a synthetic (..., int, DefaultConstructorMarker) bridge ctor for
            // defaults - skip it, same as the server's lock.
            .filterNot { ctor -> ctor.parameterTypes.any { it.name.endsWith("DefaultConstructorMarker") } }
            .map { ctor -> ctor.parameterTypes.map { it.name } }
            .toSet()

        assertTrue(
            listOf("java.net.InetAddress") in signatures,
            "(InetAddress) constructor missing; had $signatures",
        )
        assertTrue(
            listOf("java.net.InetAddress", "int") in signatures,
            "(InetAddress, int) constructor missing; had $signatures",
        )
        assertTrue(
            listOf("java.net.InetAddress", "int", "int") in signatures,
            "(InetAddress, int, int) constructor missing; had $signatures",
        )
        assertTrue(
            listOf("java.net.InetAddress", "int", "int", "int") in signatures,
            "(InetAddress, int, int, int) constructor missing (reliableMaxMessageBytes, Issue #14 Stage 3); had $signatures",
        )
        assertTrue(
            listOf(
                "java.net.InetAddress", "int", "int",
                "com.spartanlabs.webtools.udp.PeriodicSchedule",
                "com.spartanlabs.webtools.udp.PeriodicSchedule",
                "com.spartanlabs.webtools.udp.PeriodicSchedule",
                "int",
            ) in signatures,
            "the internal seam constructor (three PeriodicSchedule params) is missing; had $signatures",
        )
        assertTrue(signatures.size == 5, "unexpected extra constructors: $signatures")
    }
}
