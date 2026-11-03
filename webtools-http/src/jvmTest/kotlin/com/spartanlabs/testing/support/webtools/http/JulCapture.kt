package com.spartanlabs.testing.support.webtools.http

import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Logger
import java.util.logging.LogRecord

/** The prefix of every Caffeine logger; Caffeine logs through `System.Logger`, which this JVM routes to `java.util.logging`. */
private const val CAFFEINE_LOGGER_PREFIX: String = "com.github.benmanes.caffeine"

/**
 * Runs [block] with a `Handler` on the root `java.util.logging` logger (`Logger.getLogger("")`, which the runtime holds strongly) collecting every
 * record whose logger name starts with `com.github.benmanes.caffeine`, and removes the handler in `finally`.
 *
 * @param block the code whose Caffeine logging is captured.
 * @return the block's value and the Caffeine records published while it ran.
 */
internal fun <T> captureCaffeineLogs(block: () -> T): Pair<T, List<LogRecord>> {
    val records = CopyOnWriteArrayList<LogRecord>()
    val handler = object : Handler() {
        override fun publish(record: LogRecord) {
            if (record.loggerName?.startsWith(CAFFEINE_LOGGER_PREFIX) == true) records += record
        }
        override fun flush() {}
        override fun close() {}
    }
    val root = Logger.getLogger("")
    root.addHandler(handler)
    try {
        return block() to records.toList()
    } finally {
        root.removeHandler(handler)
    }
}
