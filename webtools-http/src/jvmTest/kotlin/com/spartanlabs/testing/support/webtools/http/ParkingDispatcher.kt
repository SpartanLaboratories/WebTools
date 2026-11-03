package com.spartanlabs.testing.support.webtools.http

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

/**
 * A dispatcher that parks every dispatched task until [runParked] is called, so a test holds a load between its dispatch and its first line. It
 * is not a `Delay`, so a loader dispatched on it must not `delay`.
 */
internal class ParkingDispatcher : CoroutineDispatcher() {
    private val parked = CopyOnWriteArrayList<Runnable>()

    /** How many tasks are parked now. */
    val parkedCount: Int get() = parked.size

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        parked += block
    }

    /** Runs every task parked so far, in dispatch order, on the calling thread; a task dispatched while running is parked for the next call. */
    fun runParked() {
        val due = parked.toList()
        parked.removeAll(due)
        due.forEach { it.run() }
    }
}
