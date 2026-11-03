package com.spartanlabs.testing.support.webtools.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/** The parent of every logger of the module; capturing here sees the transport's and the guards' lines alike. */
private const val MODULE_LOGGER_NAME: String = "com.spartanlabs.webtools.http"

/**
 * Runs [block] with a logback `ListAppender` attached to the module's logger at [level], and restores the previous level and detaches the
 * appender in `finally`. The events are a snapshot taken after [block] returned.
 *
 * @param level the level to capture at; `DEBUG` by default.
 * @param block the code whose logging is captured.
 * @return the block's value and every event the module's loggers emitted while it ran.
 */
internal fun <T> captureTransportLogs(level: Level = Level.DEBUG, block: () -> T): Pair<T, List<ILoggingEvent>> {
    val logbackLogger = LoggerFactory.getLogger(MODULE_LOGGER_NAME) as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    val previousLevel = logbackLogger.level
    logbackLogger.level = level
    logbackLogger.addAppender(appender)
    try {
        val value = block()
        return value to appender.list.toList()
    } finally {
        logbackLogger.detachAppender(appender)
        logbackLogger.level = previousLevel
        appender.stop()
    }
}
