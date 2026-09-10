package com.spartanlabs.webtools.udp

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The seam [HandshakeCoordinator] / [MultiConnectionUDPClient] need onto a
 * periodic per-endpoint task, so both can be unit-tested against a socket-free
 * fake. Backs both the scheduled keepalive and the link-quality probe.
 */
internal interface PeriodicSchedule {
    /**
     * Arms (or re-arms) a repeating poll for [key]. The last call for a given
     * [key] wins - any existing schedule for it is cancelled first.
     * @param key the endpoint the task is for
     * @param intervalMillis the task interval; must be > 0
     * @param tick invoked on each poll; must not throw (it is wrapped defensively)
     * @return [Result.success] once armed; [Result.failure] with an
     * [IllegalArgumentException] for a non-positive interval, or an
     * [IllegalStateException] if [shutdown] has already run
     */
    fun schedule(key: InetSocketAddress, intervalMillis: Long, tick: () -> Unit): Result<Unit>

    /**
     * Cancels the poll for [key], if any. Idempotent - a no-op if none is armed.
     * @param key the endpoint whose poll to cancel
     */
    fun cancel(key: InetSocketAddress)

    /** Cancels every poll and tears the executor down. Idempotent. */
    fun shutdown()
}

/**
 * A single daemon [ScheduledExecutorService] plus a per-[key][InetSocketAddress]
 * [ScheduledFuture] map. The executor is created on the first [schedule] call and
 * torn down by [shutdown] - a consumer that never opts into a periodic task pays
 * for no thread. The key is the peer endpoint on the server side; the fixed
 * server endpoint on the client side.
 *
 * @param threadName the daemon thread's name (e.g. `mcupc-keepalive`,
 * `mcupc-probe`, `mcups-keepalive`, `mcups-probe`)
 */
internal class PeriodicScheduler(private val threadName: String) : PeriodicSchedule {
    private val futures = ConcurrentHashMap<InetSocketAddress, ScheduledFuture<*>>()

    @Volatile
    private var executor: ScheduledExecutorService? = null

    // shutDown latch: a schedule() after shutdown() fails its Result rather than
    // resurrecting the executor (relevant when startProbe/startKeepAlive races stop()).
    @Volatile
    private var shutDown = false

    @Synchronized
    override fun schedule(key: InetSocketAddress, intervalMillis: Long, tick: () -> Unit): Result<Unit> =
        runCatching {
            require(intervalMillis > 0L) { "intervalMillis must be > 0, was $intervalMillis" }
            check(!shutDown) { "periodic scheduler already shut down" }
            cancelInternal(key) // last call wins - replace any existing schedule for this key
            val poll = KeepAlive.pollIntervalMillis(intervalMillis)
            // runCatching in the task body is load-bearing: scheduleWithFixedDelay
            // permanently cancels a repeating task the first time it throws.
            futures[key] = executor().scheduleWithFixedDelay(
                { runCatching(tick).onFailure { log.warn("Periodic tick for {} threw", key, it) } },
                poll, poll, TimeUnit.MILLISECONDS,
            )
        }

    override fun cancel(key: InetSocketAddress) = cancelInternal(key)

    private fun cancelInternal(key: InetSocketAddress) {
        futures.remove(key)?.cancel(false)
    }

    @Synchronized
    override fun shutdown() {
        shutDown = true
        futures.values.forEach { it.cancel(false) }
        futures.clear()
        executor?.shutdownNow()
        executor = null
    }

    @Synchronized
    private fun executor(): ScheduledExecutorService =
        executor ?: Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, threadName).apply { isDaemon = true }
        }.also { executor = it }

    private companion object {
        private val log = LoggerFactory.getLogger(PeriodicScheduler::class.java)
    }
}
