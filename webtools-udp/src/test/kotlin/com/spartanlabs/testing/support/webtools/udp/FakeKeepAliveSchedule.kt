package com.spartanlabs.testing.support.webtools.udp

import com.spartanlabs.webtools.udp.KeepAliveSchedule
import java.net.InetSocketAddress

/**
 * A socket-free, timer-free [KeepAliveSchedule] test fixture. Records every
 * [schedule] (keeping the `tick` so a test can invoke it synchronously),
 * [cancel], and [shutdown], and returns a configurable [Result] from [schedule].
 * Lets the coordinator / client keepalive logic be driven with no real executor.
 *
 * @param scheduleResult the [Result] every [schedule] call returns, unless
 * [failAfterShutdown] has kicked in
 * @param failAfterShutdown when `true`, a [schedule] call made after [shutdown]
 * fails with [IllegalStateException] - the real `KeepAliveScheduler`'s shut-down
 * latch, without a real executor
 */
internal class FakeKeepAliveSchedule(
    private val scheduleResult: Result<Unit> = Result.success(Unit),
    private val failAfterShutdown: Boolean = false,
) : KeepAliveSchedule {

    data class Scheduled(val key: InetSocketAddress, val intervalMillis: Long, val tick: () -> Unit)

    /** Every live [schedule], keyed by endpoint - a re-schedule for a key replaces the prior entry. */
    val scheduled = LinkedHashMap<InetSocketAddress, Scheduled>()

    /** Every [schedule] call in order, including replaced ones. */
    val scheduleCalls = mutableListOf<Scheduled>()

    val cancels = mutableListOf<InetSocketAddress>()
    var shutdownCalls = 0
        private set

    override fun schedule(key: InetSocketAddress, intervalMillis: Long, tick: () -> Unit): Result<Unit> {
        val entry = Scheduled(key, intervalMillis, tick)
        scheduleCalls += entry
        if (failAfterShutdown && shutdownCalls > 0) {
            return Result.failure(IllegalStateException("keepalive scheduler already shut down"))
        }
        if (scheduleResult.isSuccess) scheduled[key] = entry
        return scheduleResult
    }

    override fun cancel(key: InetSocketAddress) {
        cancels += key
        scheduled.remove(key)
    }

    override fun shutdown() {
        shutdownCalls++
        scheduled.clear()
    }

    /** Runs the recorded tick for [key], as the real scheduler's poll would. */
    fun tick(key: InetSocketAddress) {
        scheduled.getValue(key).tick()
    }
}
