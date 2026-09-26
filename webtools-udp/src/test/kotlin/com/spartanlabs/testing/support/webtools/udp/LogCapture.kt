package com.spartanlabs.testing.support.webtools.udp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/**
 * Attaches a logback [ListAppender] to the logger of [type] for the duration of
 * [block], so a test can assert on what the class under test logged.
 *
 * The appender is always detached again, even if [block] throws. `logback-test.xml` roots at
 * INFO, so a DEBUG (or lower) event is otherwise invisible to a test; pass [level] to lower
 * that logger's own level for the duration of [block] - the previous level (possibly `null`,
 * meaning "inherit from root") is always restored afterward, even if [block] throws.
 *
 * @param type the class whose logger to capture (its logger name is `type.name`)
 * @param level if non-`null`, this logger's own level for the duration of [block] only
 * @param block run with the live list of captured events in scope
 * @return whatever [block] returns
 */
internal fun <T> captureLogsOf(type: Class<*>, level: Level? = null, block: (events: List<ILoggingEvent>) -> T): T {
    val logger = LoggerFactory.getLogger(type) as Logger
    val previousLevel = logger.level
    if (level != null) logger.level = level
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(appender)
    try {
        return block(appender.list)
    } finally {
        logger.detachAppender(appender)
        appender.stop()
        if (level != null) logger.level = previousLevel
    }
}

/** True if any captured event at or above [Level.WARN] contains [fragment] in its formatted message. */
internal fun List<ILoggingEvent>.hasWarnContaining(fragment: String): Boolean =
    any { it.level.isGreaterOrEqual(Level.WARN) && it.formattedMessage.contains(fragment) }

/** True if any captured event is at exactly [level] (not "at or above") and contains [fragment]. */
internal fun List<ILoggingEvent>.hasEventAt(level: Level, fragment: String): Boolean =
    any { it.level == level && it.formattedMessage.contains(fragment) }
