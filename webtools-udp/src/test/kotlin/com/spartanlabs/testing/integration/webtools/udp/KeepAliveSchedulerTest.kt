package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.KeepAliveScheduler
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 3 - KeepAliveScheduler driving a real ScheduledExecutorService (no socket).
@Tag("integration")
class KeepAliveSchedulerTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val keyA = InetSocketAddress(loopback, 56001)
    private val keyB = InetSocketAddress(loopback, 56002)
    private val threadName = "test-keepalive-${System.nanoTime()}"
    private var scheduler: KeepAliveScheduler? = null

    @AfterTest
    fun cleanup() {
        scheduler?.shutdown()
    }

    private fun newScheduler() = KeepAliveScheduler(threadName).also { scheduler = it }

    private fun await(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    @Test
    fun `a scheduled tick fires repeatedly at roughly the poll cadence`() {
        val ticks = AtomicInteger()
        assertTrue(newScheduler().schedule(keyA, 1_000L) { ticks.incrementAndGet() }.isSuccess)

        // poll = 250 ms; expect several ticks within ~1.5 s.
        assertTrue(await(2_000L) { ticks.get() >= 3 }, "saw ${ticks.get()} ticks")
    }

    @Test
    fun `cancel stops one key's ticks while another key continues`() {
        val scheduler = newScheduler()
        val a = AtomicInteger()
        val b = AtomicInteger()
        scheduler.schedule(keyA, 1_000L) { a.incrementAndGet() }
        scheduler.schedule(keyB, 1_000L) { b.incrementAndGet() }
        assertTrue(await(2_000L) { a.get() >= 2 && b.get() >= 2 })

        scheduler.cancel(keyA)
        val aFrozen = a.get()
        Thread.sleep(800)
        val bBefore = b.get()
        assertTrue(a.get() <= aFrozen + 1, "keyA ticks stopped")
        assertTrue(await(1_500L) { b.get() > bBefore }, "keyB still ticking")
    }

    @Test
    fun `a second schedule for the same key replaces the first - one tick stream`() {
        val scheduler = newScheduler()
        val first = AtomicInteger()
        val second = AtomicInteger()
        scheduler.schedule(keyA, 1_000L) { first.incrementAndGet() }
        assertTrue(await(1_500L) { first.get() >= 1 })
        scheduler.schedule(keyA, 1_000L) { second.incrementAndGet() }

        val firstFrozen = first.get()
        assertTrue(await(1_500L) { second.get() >= 2 })
        assertTrue(first.get() <= firstFrozen + 1, "the replaced tick stopped")
    }

    @Test
    fun `shutdown stops all ticks and leaves no live thread of the configured name`() {
        val scheduler = newScheduler()
        val ticks = AtomicInteger()
        scheduler.schedule(keyA, 1_000L) { ticks.incrementAndGet() }
        assertTrue(await(1_500L) { ticks.get() >= 1 })

        scheduler.shutdown()
        val frozen = ticks.get()
        Thread.sleep(600)
        assertTrue(ticks.get() <= frozen + 1, "ticks stopped after shutdown")
        assertNull(
            Thread.getAllStackTraces().keys.firstOrNull { it.name == threadName && it.isAlive },
            "no live keepalive thread after shutdown",
        )
    }

    @Test
    fun `schedule after shutdown fails its Result`() {
        val scheduler = newScheduler()
        scheduler.shutdown()

        val result = scheduler.schedule(keyA, 1_000L) { }

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `a tick that throws does not cancel the repeating task`() {
        val ticks = AtomicInteger()
        newScheduler().schedule(keyA, 1_000L) {
            ticks.incrementAndGet()
            error("hostile tick")
        }

        assertTrue(await(2_500L) { ticks.get() >= 3 }, "throwing tick still fires later, saw ${ticks.get()}")
    }
}
