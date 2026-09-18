package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.PeriodicScheduler
import com.spartanlabs.webtools.udp.ReliableChannelEngine
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 3 - engine::onRetransmitTick handed to a real PeriodicScheduler (a real
// ScheduledExecutorService, real wall-clock cadence). No socket, no production wiring - this
// stage never creates this executor itself; that wiring is Stage 3.
@Tag("integration")
class ReliableChannelEngineRetransmitSchedulingTest {

    private val loopback = InetAddress.getLoopbackAddress()
    private val key = InetSocketAddress(loopback, 57001)
    private val threadName = "test-reliable-retransmit-${System.nanoTime()}"
    private var scheduler: PeriodicScheduler? = null

    @AfterTest
    fun cleanup() {
        scheduler?.shutdown()
    }

    private fun newScheduler() = PeriodicScheduler(threadName).also { scheduler = it }

    private fun await(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    @Test
    fun `engine onRetransmitTick fires repeatedly on a real periodic schedule`() {
        val ticks = AtomicInteger()
        val engine = ReliableChannelEngine(sendRaw = { Result.success(Unit) })
        // onRetransmitTick itself is a no-op with nothing in flight and nothing received -
        // wrap it so the test can observe that the scheduler actually drove it.
        assertTrue(
            newScheduler().schedule(key, 1_000L) {
                engine.onRetransmitTick()
                ticks.incrementAndGet()
            }.isSuccess,
        )

        assertTrue(await(2_000L) { ticks.get() >= 3 }, "saw ${ticks.get()} ticks")
    }

    @Test
    fun `shutdown stops the schedule and leaves no live thread of the configured name`() {
        val ticks = AtomicInteger()
        val engine = ReliableChannelEngine(sendRaw = { Result.success(Unit) })
        val scheduler = newScheduler()
        scheduler.schedule(key, 1_000L) { engine.onRetransmitTick(); ticks.incrementAndGet() }
        assertTrue(await(1_500L) { ticks.get() >= 1 })

        scheduler.shutdown()
        val frozen = ticks.get()
        Thread.sleep(600)
        assertTrue(ticks.get() <= frozen + 1, "ticks stopped after shutdown")
        assertNull(
            Thread.getAllStackTraces().keys.firstOrNull { it.name == threadName && it.isAlive },
            "no live periodic thread after shutdown",
        )
    }
}
