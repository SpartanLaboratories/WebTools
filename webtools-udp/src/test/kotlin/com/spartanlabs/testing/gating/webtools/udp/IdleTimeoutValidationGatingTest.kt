package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.Connection
import com.spartanlabs.webtools.udp.MultiConnectionUDPServer
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Level 1 - the idleTimeoutMillis constructor guard. The require() runs in the first init
// block, before any socket property initialiser (same fail-fast ordering as the
// receiveBufferBytes guard), so every rejection case here binds nothing - socket-free,
// outside the module's commonUdpPortLock. The accepting cases (0 / positive) bind the
// common port and are exercised at Level 3 (MultiConnectionUDPServerLivenessTest).
@Tag("gating")
class IdleTimeoutValidationGatingTest {

    private class SizedServer(idleTimeoutMillis: Long) :
        MultiConnectionUDPServer(MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES, idleTimeoutMillis) {
        override fun onClientConnect(connection: Connection) = Unit
    }

    @Test
    fun `a negative idleTimeoutMillis is rejected before any socket is bound`() {
        listOf(-1L, Long.MIN_VALUE).forEach { value ->
            assertFailsWith<IllegalArgumentException>("idleTimeoutMillis $value must be rejected") {
                SizedServer(value)
            }
        }
    }

    @Test
    fun `the JvmOverloads constructor set is exactly empty, int, and int-long`() {
        val signatures = MultiConnectionUDPServer::class.java.declaredConstructors
            // Kotlin emits a synthetic (int, long, int, DefaultConstructorMarker) ctor for defaults - skip it.
            .filterNot { ctor -> ctor.parameterTypes.any { it.name.endsWith("DefaultConstructorMarker") } }
            .map { ctor -> ctor.parameterTypes.map { it.name } }
            .toSet()

        assertTrue(emptyList<String>() in signatures, "no-arg constructor missing; had $signatures")
        assertTrue(listOf("int") in signatures, "(int) constructor missing; had $signatures")
        assertTrue(listOf("int", "long") in signatures, "(int, long) constructor missing; had $signatures")
        assertTrue(signatures.size == 3, "unexpected extra constructors: $signatures")
    }
}
