package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import com.spartanlabs.webtools.http.logger
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - the slf4j actual of the logging seam, against a logback `ListAppender` on a uniquely named
 * logger raised to `DEBUG`: `debug`, `info` and `warn` reach slf4j at their level with the logger name;
 * `{}` placeholders are substituted; a trailing `Throwable` is the event's throwable; at `INFO` a
 * `debug` call produces no event. The appender and the level are restored in `finally`.
 */
class Slf4jLogComponentTest {

    private fun <T> withCapture(name: String, level: Level, block: (ListAppender<ILoggingEvent>) -> T): T {
        val logbackLogger = LoggerFactory.getLogger(name) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previousLevel = logbackLogger.level
        logbackLogger.level = level
        logbackLogger.addAppender(appender)
        try {
            return block(appender)
        } finally {
            logbackLogger.detachAppender(appender)
            logbackLogger.level = previousLevel
            appender.stop()
        }
    }

    @Test
    fun `debug info and warn reach slf4j at their level with the logger name`() {
        val name = "com.spartanlabs.webtools.http.test.levels"
        withCapture(name, Level.DEBUG) { appender ->
            val log = logger(name)
            log.debug("d")
            log.info("i")
            log.warn("w")
            assertEquals(listOf(Level.DEBUG, Level.INFO, Level.WARN), appender.list.map { it.level })
            assertEquals(listOf("d", "i", "w"), appender.list.map { it.formattedMessage })
            assertTrue(appender.list.all { it.loggerName == name })
        }
    }

    @Test
    fun `placeholders are substituted and a trailing Throwable is the event's throwable`() {
        val name = "com.spartanlabs.webtools.http.test.placeholders"
        val failure = IllegalStateException("boom")
        withCapture(name, Level.DEBUG) { appender ->
            logger(name).info("{} of {}", 1, "two")
            logger(name).warn("failed after {} tries", 3, failure)
            assertEquals("1 of two", appender.list[0].formattedMessage)
            assertEquals("failed after 3 tries", appender.list[1].formattedMessage)
            assertSame(failure, (appender.list[1].throwableProxy as ThrowableProxy).throwable)
        }
    }

    @Test
    fun `at INFO a debug call produces no event`() {
        val name = "com.spartanlabs.webtools.http.test.info-level"
        withCapture(name, Level.INFO) { appender ->
            logger(name).debug("hidden")
            logger(name).info("shown")
            assertEquals(listOf("shown"), appender.list.map { it.formattedMessage })
        }
    }
}
