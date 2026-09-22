package com.spartanlabs.testing.integration.webtools.udp

import com.spartanlabs.webtools.udp.PeriodicScheduler
import org.junit.jupiter.api.Tag
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 3 - the §1.3(a) regression guard: scheduleTick must deliver the EXACT cadence asked
// for, not KeepAlive.pollIntervalMillis's quarter-division (which would silently coarsen a
// 50 ms reliable-retransmit tick to 250 ms). schedule()'s own (unchanged) cadence is checked
// alongside it so a future refactor cannot conflate the two.
@Tag("integration")
class PeriodicSchedulerTickTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val key = InetSocketAddress(loopback, 56501)
    private val threadName = "test-scheduletick-${System.nanoTime()}"
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
            Thread.sleep(5)
        }
        return condition()
    }

    @Test
    fun `scheduleTick at 50ms fires at roughly 50ms, not the 250ms schedule() poll division`() {
        val ticks = AtomicInteger()
        assertTrue(newScheduler().scheduleTick(key, 50L) { ticks.incrementAndGet() }.isSuccess)

        // At a true 50 ms cadence, 300 ms should see ~6 ticks; schedule()'s 250 ms poll
        // division would see at most ~1-2 in the same window - the regression this locks.
        assertTrue(await(600L) { ticks.get() >= 5 }, "saw only ${ticks.get()} ticks in 600ms at a 50ms scheduleTick")
    }

    @Test
    fun `cancel stops a scheduleTick the same way it stops schedule`() {
        val scheduler = newScheduler()
        val ticks = AtomicInteger()
        scheduler.scheduleTick(key, 50L) { ticks.incrementAndGet() }
        assertTrue(await(500L) { ticks.get() >= 3 })

        scheduler.cancel(key)
        val frozen = ticks.get()
        Thread.sleep(300)
        assertTrue(ticks.get() <= frozen + 1, "ticks stopped after cancel")
    }

    @Test
    fun `shutdown leaves no live scheduleTick thread`() {
        val scheduler = newScheduler()
        val ticks = AtomicInteger()
        scheduler.scheduleTick(key, 50L) { ticks.incrementAndGet() }
        assertTrue(await(500L) { ticks.get() >= 1 })

        scheduler.shutdown()
        val frozen = ticks.get()
        Thread.sleep(300)
        assertTrue(ticks.get() <= frozen + 1, "ticks stopped after shutdown")
        assertNull(
            Thread.getAllStackTraces().keys.firstOrNull { it.name == threadName && it.isAlive },
            "no live scheduleTick thread after shutdown",
        )
    }

    @Test
    fun `schedule's own cadence is unchanged by the addition of scheduleTick`() {
        val ticks = AtomicInteger()
        assertTrue(newScheduler().schedule(key, 1_000L) { ticks.incrementAndGet() }.isSuccess)

        // schedule(1000ms) polls at KeepAlive.pollIntervalMillis(1000) == 250ms.
        assertTrue(await(2_000L) { ticks.get() >= 3 }, "saw ${ticks.get()} ticks")
    }
}
