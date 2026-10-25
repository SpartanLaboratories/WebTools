package com.spartanlabs.webtools.http

/**
 * The module's logging seam: slf4j on the JVM, a no-op on js. Each message takes `{}` placeholders for
 * [args]; a trailing [Throwable] among the arguments is the exception, as slf4j reads it.
 */
internal interface Log {
    fun debug(message: String, vararg args: Any?)
    fun info(message: String, vararg args: Any?)
    fun warn(message: String, vararg args: Any?)
}

/**
 * Returns the [Log] named [name]. The `actual` of each target supplies it (`Log.jvm.kt` on the JVM; the
 * check module's `JsActuals.kt` on js); a missing one is a compile error.
 */
internal expect fun logger(name: String): Log
