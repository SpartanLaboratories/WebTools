package com.spartanlabs.webtools.http

import kotlin.js.Date

// The js target exists only to compile commonMain; nothing runs it, so logging is a no-op.
private object NoOpLog : Log {
    override fun debug(message: String, vararg args: Any?) = Unit
    override fun info(message: String, vararg args: Any?) = Unit
    override fun warn(message: String, vararg args: Any?) = Unit
}

internal actual fun logger(name: String): Log = NoOpLog

// Date.now() is a Double of epoch milliseconds, exact as a Long.
internal actual fun epochMillisNow(): Long = Date.now().toLong()
