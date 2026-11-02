package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.webtools.http.newDispatcherExecutor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 2 - the dispatcher's executor: a task runs on a daemon thread named `webtools-http-dispatcher-1`, a concurrent one on `-2`; a
 * runnable that throws ends its thread and the handler logs only the throwable's class name at `DEBUG`; after `shutdown()` a new task is
 * rejected; the pool's shape lets idle threads exit (no core threads, a 60 s keep-alive).
 */
class DispatcherExecutorComponentTest {

    @Test
    fun `tasks run on daemon threads named in sequence`() {
        val executor = newDispatcherExecutor()
        try {
            val hold = CountDownLatch(1)
            val names = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Boolean>>()
            val first = executor.submit { names += Thread.currentThread().name to Thread.currentThread().isDaemon; hold.await() }
            val second = executor.submit { names += Thread.currentThread().name to Thread.currentThread().isDaemon }
            second.get(5, TimeUnit.SECONDS)
            hold.countDown()
            first.get(5, TimeUnit.SECONDS)
            assertEquals(setOf("webtools-http-dispatcher-1", "webtools-http-dispatcher-2"), names.map { it.first }.toSet())
            assertTrue(names.all { it.second })
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `a runnable that throws ends its thread and the handler logs the class name only`() {
        val executor = newDispatcherExecutor()
        try {
            val (_, events) = captureTransportLogs(Level.DEBUG) {
                val done = CountDownLatch(1)
                executor.execute {
                    try {
                        throw IllegalStateException("SECRET-MESSAGE")
                    } finally {
                        done.countDown()
                    }
                }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                // The handler runs after the runnable returns; give the thread a moment to reach it.
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (System.nanoTime() < deadline && Thread.getAllStackTraces().keys.any { it.name == "webtools-http-dispatcher-1" && it.isAlive }) Thread.yield()
            }
            val handled = events.filter { "ended with" in it.formattedMessage }
            assertEquals(1, handled.size, events.map { it.formattedMessage }.toString())
            assertEquals(Level.DEBUG, handled.single().level)
            assertTrue("java.lang.IllegalStateException" in handled.single().formattedMessage)
            assertFalse("SECRET-MESSAGE" in handled.single().formattedMessage)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `after shutdown a new task is rejected`() {
        val executor = newDispatcherExecutor()
        executor.shutdown()
        assertFailsWith<RejectedExecutionException> { executor.execute { } }
        assertTrue(executor.isShutdown)
    }

    @Test
    fun `the pool has no core threads and a 60 s keep-alive so idle threads exit`() {
        val executor = assertIs<ThreadPoolExecutor>(newDispatcherExecutor())
        try {
            assertEquals(0, executor.corePoolSize)
            assertEquals(Int.MAX_VALUE, executor.maximumPoolSize)
            assertEquals(60L, executor.getKeepAliveTime(TimeUnit.SECONDS))
        } finally {
            executor.shutdown()
        }
    }
}
