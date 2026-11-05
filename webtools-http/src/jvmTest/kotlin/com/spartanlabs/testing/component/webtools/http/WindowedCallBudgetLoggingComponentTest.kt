package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.captureTransportLogs
import com.spartanlabs.testing.support.webtools.http.dayWindow
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.webtools.http.BUDGET_LOGGER_NAME
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.WindowReading
import com.spartanlabs.webtools.http.testing.FakeBudgetStore
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val SECRET_MESSAGE = "SECRET-STORE-MESSAGE-3c9f"
private const val CREDENTIAL_LANE = "Bearer-SECRET-TOKEN-8a1d"

/**
 * Level 2 - what `WindowedCallBudget` logs, captured at `DEBUG` on the module's logger: exactly one `INFO` on creation (with the counts) and one on
 * `close()` even when called twice; one `INFO` per restore; one `WARN` per `Paused` with source, length and until; one `WARN` per `HardStop` (once per
 * window and period, not per refused call); one `WARN` per store failure with the operation and the cause's class name; one `DEBUG` per `Refused` (lane,
 * reason, window, wait) and per `Reconciled`; no line for a grant; the logger is the budget's; at `INFO` no `DEBUG` line appears; a store exception's
 * message never appears, and a lane name (the only caller-supplied text) appears only in the `Refused` line.
 */
class WindowedCallBudgetLoggingComponentTest {

    private fun List<ILoggingEvent>.at(level: Level): List<String> = filter { it.level == level }.map { it.formattedMessage }

    private fun captured(level: Level = Level.DEBUG): List<ILoggingEvent> = captureTransportLogs(level) {
        runTest {
            val clock = virtualClock()
            val store = FakeBudgetStore()
            store.failSaves(IllegalStateException(SECRET_MESSAGE))
            val credentialLane = Lane(CREDENTIAL_LANE)
            budgetOn(clock, minuteWindow(50, margin = 10), dayWindow(2)) { this.store = store; persistAhead = 1; maxQueued = 7 }.use { budget ->
                val first = budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()         // restore, a failed write-ahead save, a grant
                budget.acquire(1, Lane.Tracker, Duration.ZERO).getOrThrow()                     // the day is full: a hard stop
                budget.acquire(1, credentialLane, Duration.ZERO)                                // refused
                budget.acquire(1, credentialLane, Duration.ZERO)                                // refused again: no second hard stop
                first.complete(RateSignals { throttled = true; retryAfter = 30.seconds; reading("minute", WindowReading(40)) })
                budget.close()
                budget.close()
            }
        }
    }.second

    @Test
    fun `one INFO on creation with the counts one on close and one per restore`() {
        val events = captured()
        val infos = events.at(Level.INFO)
        assertEquals(3, infos.size, infos.toString())
        assertTrue(infos[0].startsWith("created:") && "windows=2" in infos[0] && "maxBurst=2" in infos[0] && "maxQueued=7" in infos[0] && "persistAhead=1" in infos[0], infos[0])
        assertTrue(infos[1].startsWith("restored:") && "windows=0" in infos[1] && "dropped=0" in infos[1] && "ignoredForSkew=false" in infos[1], infos[1])
        assertEquals("closed", infos[2])
        events.forEach { assertEquals(BUDGET_LOGGER_NAME, it.loggerName) }
    }

    @Test
    fun `one WARN per pause hard stop and store failure`() {
        val warnings = captured().at(Level.WARN)
        assertEquals(3, warnings.size, warnings.toString())
        val storeLine = warnings.single { it.startsWith("store") }
        assertTrue("Save" in storeLine && "IllegalStateException" in storeLine, storeLine)
        val stopLine = warnings.single { it.startsWith("hard stop") }
        assertTrue("window=day" in stopLine && "resetsAtEpochMillis=" in stopLine, stopLine)
        val pauseLine = warnings.single { it.startsWith("paused") }
        assertTrue("source=RetryAfter" in pauseLine && "length=30s" in pauseLine && "untilEpochMillis=" in pauseLine, pauseLine)
    }

    @Test
    fun `one DEBUG per refusal and per reconciliation and none for a grant`() {
        val debugs = captured().at(Level.DEBUG)
        assertEquals(3, debugs.size, debugs.toString())
        val refusals = debugs.filter { it.startsWith("refused") }
        assertEquals(2, refusals.size)
        refusals.forEach { assertTrue("lane=$CREDENTIAL_LANE" in it && "reason=Window" in it && "window=day" in it && "wait=" in it, it) }
        val reconciled = debugs.single { it.startsWith("reconciled") }
        assertTrue("window=minute" in reconciled && "serverUsed=20" in reconciled && "raisedBy=18" in reconciled, reconciled)
        assertTrue(captured().none { "granted" in it.formattedMessage.lowercase() && "refused" !in it.formattedMessage }, "a grant was logged")
    }

    @Test
    fun `at INFO no DEBUG line appears`() {
        val events = captured(Level.INFO)
        assertTrue(events.at(Level.DEBUG).isEmpty())
        assertEquals(3, events.at(Level.INFO).size)
        assertEquals(3, events.at(Level.WARN).size)
    }

    @Test
    fun `caller-supplied text appears only where listed and a store message never`() {
        val events = captured()
        events.forEach { event ->
            assertFalse(SECRET_MESSAGE in event.formattedMessage, event.formattedMessage)
            assertFalse(SECRET_MESSAGE in (event.throwableProxy?.message ?: ""), "a store exception was attached to a log line")
            if (CREDENTIAL_LANE in event.formattedMessage) assertTrue(event.formattedMessage.startsWith("refused"), event.formattedMessage)
        }
    }
}
