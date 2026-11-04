package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.VirtualClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * A budget driven by [clock]: its `timeSource` and `wallClock` are the clock's, `laneShares` is empty and `boundaryGuard` zero (so the arithmetic of a
 * test is the plain one unless it asks for lanes or a guard), then [configure] runs.
 *
 * @param clock the virtual clock of the test.
 * @param windows the budget's windows.
 * @param configure the other builder properties to set.
 * @return the budget; close it with `use`.
 */
internal fun budgetOn(clock: VirtualClock, vararg windows: RateWindow, configure: WindowedCallBudget.Builder.() -> Unit = {}): WindowedCallBudget =
    WindowedCallBudget(windows.toList()) {
        timeSource = clock.timeSource
        wallClock = clock.wallClock
        laneShares = emptyMap()
        boundaryGuard = Duration.ZERO
        configure()
    }

/** A one-minute window whose enforced limit is [limit] (its published count is `limit + margin`). */
internal fun minuteWindow(limit: Int, name: String = "minute", margin: Int = 0, hard: Boolean = false): RateWindow =
    RateWindow(name, limit + margin, 1.minutes) { this.margin = margin; this.hard = hard }

/** A one-day window of [limit] calls, hard by default. */
internal fun dayWindow(limit: Int, name: String = "day", hard: Boolean = true): RateWindow = RateWindow(name, limit, 1.days) { this.hard = hard }
