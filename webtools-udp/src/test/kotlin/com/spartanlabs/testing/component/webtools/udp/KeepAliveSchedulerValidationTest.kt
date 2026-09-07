package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.webtools.udp.KeepAliveScheduler
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 2 - the socket-free, timer-free validation paths of KeepAliveScheduler.schedule.
// Its real-timer behaviour (ticks, cancel, replace, shutdown, thread lifecycle) is at Level 3.
@Tag("component")
class KeepAliveSchedulerValidationTest {

    private val key = InetSocketAddress(InetAddress.getLoopbackAddress(), 55000)

    @Test
    fun `schedule rejects a non-positive interval with IllegalArgumentException`() {
        val scheduler = KeepAliveScheduler("test-keepalive")
        try {
            listOf(0L, -1L).forEach { bad ->
                val result = scheduler.schedule(key, bad) { }
                assertTrue(result.isFailure, "interval $bad must fail")
                assertIs<IllegalArgumentException>(result.exceptionOrNull())
            }
        } finally {
            scheduler.shutdown()
        }
    }

    @Test
    fun `schedule after shutdown fails with IllegalStateException`() {
        val scheduler = KeepAliveScheduler("test-keepalive")
        scheduler.shutdown()

        val result = scheduler.schedule(key, 300L) { }

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }
}
