package com.spartanlabs.webtools.http

// java.lang.System: WallClock.System is nested, so it is not a name of this package.
internal actual fun epochMillisNow(): Long = System.currentTimeMillis()
