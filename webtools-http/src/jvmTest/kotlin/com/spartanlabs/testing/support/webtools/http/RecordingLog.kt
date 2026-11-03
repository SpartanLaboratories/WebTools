package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [Log] that records every call as a [Line] (level, message template, arguments), so a test can assert what a function logged
 * and at which level without logback. [Line.rendered] substitutes the `{}` placeholders in order, the way slf4j prints them.
 */
internal class RecordingLog : Log {
    /** One recorded call. */
    class Line(val level: String, val message: String, val args: List<Any?>) {
        /** The message with each `{}` replaced by the next argument's `toString()`. */
        val rendered: String
            get() = args.fold(message) { text, arg -> text.replaceFirst("{}", arg.toString()) }

        override fun toString(): String = "$level $rendered"
    }

    /** Every call so far, in order; safe to read while logging runs. */
    val lines = CopyOnWriteArrayList<Line>()

    override fun debug(message: String, vararg args: Any?) { lines += Line("DEBUG", message, args.toList()) }
    override fun info(message: String, vararg args: Any?) { lines += Line("INFO", message, args.toList()) }
    override fun warn(message: String, vararg args: Any?) { lines += Line("WARN", message, args.toList()) }
}
