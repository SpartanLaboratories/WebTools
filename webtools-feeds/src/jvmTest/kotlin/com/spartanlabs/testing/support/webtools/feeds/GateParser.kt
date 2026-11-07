package com.spartanlabs.testing.support.webtools.feeds

import com.spartanlabs.webtools.feeds.Feed
import com.spartanlabs.webtools.feeds.FeedInput
import com.spartanlabs.webtools.feeds.FeedParser
import com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A parser that blocks on a latch until the test [release]s it, and counts how many parses have begun, how many are in flight and the
 * most that ever were at once. Every count change signals waiters, so a test waits for a count with [awaitRunning] or [awaitBegun]
 * instead of sleeping. Blocking, so run it on a real dispatcher of several threads, never on a virtual clock's single thread.
 */
@OptIn(ExperimentalForInheritanceWebToolsApi::class)
class GateParser(private val feed: Feed = Feed { title = "gated" }) : FeedParser {
    private val gate = CountDownLatch(1)
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var inFlight = 0
    private var started = 0
    private var peak = 0

    /** How many parses are blocked on the gate right now. */
    val running: Int get() = lock.withLock { inFlight }

    /** How many parses have begun. */
    val begun: Int get() = lock.withLock { started }

    /** The most parses that were in flight at once. */
    val maxConcurrent: Int get() = lock.withLock { peak }

    /** Lets every blocked and every later parse through. */
    fun release() = gate.countDown()

    /** Waits until [count] parses are blocked on the gate, or [timeoutMillis] pass; true when they are. */
    fun awaitRunning(count: Int, timeoutMillis: Long = 10_000): Boolean = awaitCount(timeoutMillis) { inFlight >= count }

    /**
     * Waits until [count] parses have begun, or [timeoutMillis] pass; true when they have. A test proves that a parse does not
     * begin by asserting this returns false.
     */
    fun awaitBegun(count: Int, timeoutMillis: Long): Boolean = awaitCount(timeoutMillis) { started >= count }

    private fun awaitCount(timeoutMillis: Long, reached: () -> Boolean): Boolean = lock.withLock {
        var remaining = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!reached()) {
            if (remaining <= 0) return false
            remaining = changed.awaitNanos(remaining)
        }
        true
    }

    override fun parse(input: FeedInput): Result<Feed> {
        lock.withLock {
            started++
            inFlight++
            peak = maxOf(peak, inFlight)
            changed.signalAll()
        }
        try {
            check(gate.await(30, TimeUnit.SECONDS)) { "the gate was never released" }
            return Result.success(feed)
        } finally {
            lock.withLock {
                inFlight--
                changed.signalAll()
            }
        }
    }
}
