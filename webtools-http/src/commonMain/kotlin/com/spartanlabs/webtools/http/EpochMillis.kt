package com.spartanlabs.webtools.http

/**
 * The platform's wall-clock reading in epoch milliseconds. The `actual` of each target supplies it
 * (`EpochMillis.jvm.kt` on the JVM; the check module's `JsActuals.kt` on js); a missing one is a compile
 * error.
 */
internal expect fun epochMillisNow(): Long
