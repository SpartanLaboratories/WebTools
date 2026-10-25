package com.spartanlabs.webtools.http

import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal actual fun logger(name: String): Log = Slf4jLog(LoggerFactory.getLogger(name))

// Delegates to slf4j. A trailing Throwable among args is read as the exception, as slf4j reads any call.
private class Slf4jLog(private val delegate: Logger) : Log {
    override fun debug(message: String, vararg args: Any?) = delegate.debug(message, *args)
    override fun info(message: String, vararg args: Any?) = delegate.info(message, *args)
    override fun warn(message: String, vararg args: Any?) = delegate.warn(message, *args)
}
