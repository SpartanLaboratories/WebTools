package com.spartanlabs.testing.support.webtools.http

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

/**
 * A server dispatcher that holds each request until [expected] requests have arrived, or until [timeout] has passed since that request arrived,
 * and then answers `200` with the body `ok`. It records the greatest number of requests it held at once ([maxHeld]), which is how many the client
 * had in flight together. Once [expected] requests have arrived, every held request is let go and later ones pass straight through. Safe from the
 * server's threads (each connection is served on its own).
 *
 * @param expected how many requests must arrive before any is answered.
 * @param timeout the longest one request is held.
 */
internal class ConcurrencyGateDispatcher(private val expected: Int, private val timeout: Duration) : Dispatcher() {
    private val lock = ReentrantLock()
    private val allArrived = lock.newCondition()
    private var arrived = 0
    private var held = 0
    private var greatest = 0

    /** The greatest number of requests held at once so far. */
    val maxHeld: Int get() = lock.withLock { greatest }

    /** How many requests have arrived so far. */
    val arrivals: Int get() = lock.withLock { arrived }

    override fun dispatch(request: RecordedRequest): MockResponse {
        lock.withLock {
            arrived++
            held++
            if (held > greatest) greatest = held
            if (arrived >= expected) {
                allArrived.signalAll()
            } else {
                var remaining = timeout.inWholeNanoseconds
                while (arrived < expected && remaining > 0) remaining = allArrived.awaitNanos(remaining)
            }
            held--
        }
        return MockResponse.Builder().code(200).body("ok").build()
    }
}
