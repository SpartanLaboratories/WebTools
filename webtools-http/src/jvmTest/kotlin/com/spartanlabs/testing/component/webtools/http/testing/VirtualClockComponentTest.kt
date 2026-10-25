package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.webtools.http.WallClock
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `VirtualClock` in isolation: the readings at construction, the default start, the three
 * readings moving together under `advanceBy`, the exact-target rule, the virtual `delay` on `dispatcher`,
 * the inline `maintenanceExecutor`, the negative-duration refusal, `TestScope.virtualClock`, cross-thread
 * reads and `toString()`. No opt-in anywhere: the test is a consumer of the fixtures.
 */
class VirtualClockComponentTest {

    private val dayMillis = 24L * 60 * 60 * 1000

    private fun utcDayIndex(clock: WallClock): Long = Math.floorDiv(clock.nowEpochMillis(), dayMillis)

    @Test
    fun `a fresh clock has elapsed zero and its wall clock at the start`() {
        val clock = VirtualClock(TestCoroutineScheduler(), startEpochMillis = 1_000L)
        assertEquals(Duration.ZERO, clock.elapsed)
        assertEquals(1_000L, clock.wallClock.nowEpochMillis())
        assertEquals(1_000L, clock.startEpochMillis)
    }

    @Test
    fun `the default start is 2026-01-01T00 00 00Z`() {
        assertEquals(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), VirtualClock.DEFAULT_START_EPOCH_MILLIS)
        assertEquals(VirtualClock.DEFAULT_START_EPOCH_MILLIS, VirtualClock(TestCoroutineScheduler()).wallClock.nowEpochMillis())
    }

    @Test
    fun `advanceBy moves the time source the elapsed time and the wall clock together`() {
        val clock = VirtualClock(TestCoroutineScheduler())
        val mark = clock.timeSource.markNow()
        val dayBefore = utcDayIndex(clock.wallClock)

        clock.advanceBy(25.hours)

        assertEquals(25.hours, mark.elapsedNow())
        assertEquals(25.hours, clock.elapsed)
        assertEquals(VirtualClock.DEFAULT_START_EPOCH_MILLIS + 25.hours.inWholeMilliseconds, clock.wallClock.nowEpochMillis())
        assertEquals(dayBefore + 1, utcDayIndex(clock.wallClock))
    }

    @Test
    fun `advanceBy runs a task due exactly at the target and not one due later`() = runTest {
        val clock = virtualClock()
        var exact = false
        var later = false
        launch(clock.dispatcher) { delay(10.seconds); exact = true }
        launch(clock.dispatcher) { delay(10.seconds + 1.milliseconds); later = true }

        clock.advanceBy(10.seconds)

        assertTrue(exact, "the task due exactly at the target did not run")
        assertFalse(later, "a task due after the target ran")
    }

    @Test
    fun `advanceBy of zero runs the tasks due now`() = runTest {
        val clock = virtualClock()
        var ran = false
        launch(clock.dispatcher) { ran = true }
        assertFalse(ran)

        clock.advanceBy(Duration.ZERO)

        assertTrue(ran)
        assertEquals(Duration.ZERO, clock.elapsed)
    }

    @Test
    fun `a delay on the dispatcher is virtual and completes with advanceBy`() = runTest {
        val clock = virtualClock()
        var completedAt: Duration? = null
        launch(clock.dispatcher) { delay(1.hours); completedAt = clock.elapsed }

        clock.advanceBy(59.seconds)
        assertNull(completedAt)
        clock.advanceBy(1.hours)
        assertEquals(1.hours, completedAt)
    }

    @Test
    fun `the maintenance executor runs a task inline on the calling thread before execute returns`() {
        val clock = VirtualClock(TestCoroutineScheduler())
        val caller = Thread.currentThread()
        var ranOn: Thread? = null
        clock.maintenanceExecutor.execute { ranOn = Thread.currentThread() }
        assertSame(caller, ranOn)
    }

    @Test
    fun `a negative duration is refused and leaves the clock unchanged`() = runTest {
        val clock = virtualClock()
        clock.advanceBy(3.seconds)
        var ran = false
        launch(clock.dispatcher) { delay(1.seconds); ran = true }

        val failure = assertFailsWith<IllegalArgumentException> { clock.advanceBy((-1).seconds) }

        assertEquals("duration must not be negative", failure.message)
        assertEquals(3.seconds, clock.elapsed)
        assertFalse(ran)
    }

    @Test
    fun `virtualClock is built on the test scheduler and takes a custom start`() = runTest {
        val clock = virtualClock(startEpochMillis = 42L)
        assertEquals(42L, clock.wallClock.nowEpochMillis())
        assertEquals(42L, clock.startEpochMillis)

        // delay in the test body runs on runTest's scheduler; the clock moves it when it shares that scheduler.
        var bodyDelayDone = false
        launch { delay(2.seconds); bodyDelayDone = true }
        clock.advanceBy(2.seconds)
        assertTrue(bodyDelayDone, "the clock is not on the test's scheduler")
        assertEquals(2.seconds, clock.elapsed)
    }

    @Test
    fun `wall clock and elapsed are read from other threads while the test thread advances`() {
        val clock = VirtualClock(TestCoroutineScheduler())
        val stop = AtomicBoolean(false)
        val started = CountDownLatch(8)
        val problem = AtomicReference<Throwable?>(null)
        // Eight daemon readers assert that neither reading ever steps backwards while the test thread advances;
        // the latch starts them together so the advances overlap the reads. 8 x 2000 is enough contention to
        // surface a torn read, not a timing claim.
        val readers = (1..8).map {
            Thread {
                var lastWall = Long.MIN_VALUE
                var lastElapsed = Duration.ZERO
                started.countDown()
                try {
                    while (!stop.get()) {
                        val wall = clock.wallClock.nowEpochMillis()
                        val elapsed = clock.elapsed
                        check(wall >= lastWall) { "wall clock stepped back" }
                        check(elapsed >= lastElapsed) { "elapsed stepped back" }
                        lastWall = wall
                        lastElapsed = elapsed
                    }
                } catch (t: Throwable) {
                    problem.compareAndSet(null, t)
                }
            }.apply { isDaemon = true; start() }
        }
        started.await()
        repeat(2_000) { clock.advanceBy(1.milliseconds) }
        stop.set(true)
        readers.forEach { it.join() }

        assertNull(problem.get()?.let { it.message ?: it.toString() })
        assertEquals(2.seconds, clock.elapsed)
    }

    @Test
    fun `toString shows the start and the elapsed time`() {
        val clock = VirtualClock(TestCoroutineScheduler(), startEpochMillis = 0L)
        clock.advanceBy(5.seconds)
        assertEquals("VirtualClock(start=1970-01-01T00:00:00Z, elapsed=5s)", clock.toString())
        assertEquals("VirtualClock.wallClock", clock.wallClock.toString())
    }
}
