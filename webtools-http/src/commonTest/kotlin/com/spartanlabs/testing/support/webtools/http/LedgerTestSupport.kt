package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.BudgetLedger
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.LedgerNow
import com.spartanlabs.webtools.http.RateWindow
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** One minute in milliseconds. */
internal const val MINUTE_MS: Long = 60_000L

/** One day in milliseconds. */
internal const val DAY_MS: Long = 86_400_000L

/** A ledger over [windows] with the given lane [shares], guard and `maxPause` (a day by default). */
internal fun ledgerOf(vararg windows: RateWindow, shares: Map<Lane, Double> = emptyMap(), guardMillis: Long = 0L, maxPauseMillis: Long = DAY_MS): BudgetLedger =
    BudgetLedger(windows.toList(), shares, guardMillis, maxPauseMillis)

/** A clock reading at [wallMillis] on the wall axis and [elapsedMillis] on the monotonic axis. */
internal fun nowAt(wallMillis: Long, elapsedMillis: Long = 0L): LedgerNow = LedgerNow(wallMillis, elapsedMillis.milliseconds)

/** A one-minute window whose enforced limit is [limit] (its published count is `limit + margin`). */
internal fun minute(limit: Int, name: String = "minute", margin: Int = 0, hard: Boolean = false): RateWindow =
    RateWindow(name, limit + margin, 1.minutes) { this.margin = margin; this.hard = hard }

/** A one-day window of [limit] calls, hard by default. */
internal fun day(limit: Int, name: String = "day", hard: Boolean = true): RateWindow = RateWindow(name, limit, 1.days) { this.hard = hard }
