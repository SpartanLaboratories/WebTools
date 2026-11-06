package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.FETCH_URL
import com.spartanlabs.testing.support.webtools.http.PermitRecordingBudget
import com.spartanlabs.testing.support.webtools.http.RecordingLog
import com.spartanlabs.testing.support.webtools.http.ThrowingSignalsReader
import com.spartanlabs.testing.support.webtools.http.ThrowingTransport
import com.spartanlabs.testing.support.webtools.http.UtilisationThrowingBudget
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.CallDispatch
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.Lane
import com.spartanlabs.webtools.http.RateSignals
import com.spartanlabs.webtools.http.RateSignalsReader
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `CallDispatch`, built directly from its parts: a signal reader that throws gives `RateSignals.None` with one `WARN` naming the exception's
 * class and the permit completed; a `utilisation()` that throws falls back to the server's `Retry-After`, or to zero, with one `WARN`; a transport
 * that throws an exception becomes `TransportFailure.Io` with the permit left spent; an `Error` from the transport or the reader propagates with the
 * permit left spent; `Sent.waited` is the time from the request to the grant on the injected time source.
 */
class CallDispatchComponentTest {

    // An `Error`, to prove propagation; compared by identity.
    private class Boom : Error()

    private val request = HttpRequest(HttpMethod.Get, FETCH_URL)

    private fun dispatchOn(
        clock: VirtualClock,
        transport: HttpTransport,
        budget: CallBudget,
        log: RecordingLog,
        reader: RateSignalsReader = RateSignalsReader.Standard,
    ): CallDispatch = CallDispatch(transport, budget, reader, clock.wallClock, clock.timeSource, log)

    private fun RecordingLog.warns(): List<String> = lines.filter { it.level == "WARN" }.map { it.rendered }

    @Test
    fun `a reader that throws gives RateSignals None, one WARN with the class name, and the permit is completed`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "ok"))
        val log = RecordingLog()
        budgetOn(clock, minuteWindow(10)).use { real ->
            val budget = PermitRecordingBudget(real)
            val dispatch = dispatchOn(clock, fake, budget, log, ThrowingSignalsReader(IllegalStateException("secret")))

            val sent = dispatch.send(request, 1, Lane.Interactive, Duration.ZERO).getOrThrow()

            assertSame(RateSignals.None, sent.signals)
            assertEquals(200, sent.response.status)
            assertEquals(listOf("rate signal reader failed: IllegalStateException"), log.warns())
            assertFalse(log.lines.any { "secret" in it.rendered }, log.lines.toString())
            assertEquals(listOf("complete(signals)", "complete()"), budget.calls)
        }
    }

    @Test
    fun `a utilisation that throws falls back to the server's value with one WARN`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(429, headers = HttpHeaders { add("Retry-After", "30") }))
        val log = RecordingLog()
        budgetOn(clock, minuteWindow(10)).use { real ->
            val dispatch = dispatchOn(clock, fake, UtilisationThrowingBudget(real, IllegalStateException("secret")), log)

            val sent = dispatch.send(request, 1, Lane.Interactive, Duration.ZERO).getOrThrow()
            val failure = dispatch.throttleOf(sent, "subject")

            assertEquals(429, failure.status)
            assertEquals(30.seconds, failure.retryAfter)
            assertEquals(
                listOf("budget utilisation failed: IllegalStateException", "throttled: subject=subject, status=429, retryAfter=30s"),
                log.warns(),
            )
            assertFalse(log.lines.any { "secret" in it.rendered }, log.lines.toString())
        }
    }

    @Test
    fun `a utilisation that throws with no Retry-After falls back to zero`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(429))
        val log = RecordingLog()
        budgetOn(clock, minuteWindow(10)).use { real ->
            val dispatch = dispatchOn(clock, fake, UtilisationThrowingBudget(real, IllegalStateException("secret")), log)

            val sent = dispatch.send(request, 1, Lane.Interactive, Duration.ZERO).getOrThrow()
            val failure = dispatch.throttleOf(sent, "subject")

            assertEquals(Duration.ZERO, failure.retryAfter)
            assertEquals(1, log.warns().count { it == "budget utilisation failed: IllegalStateException" })
        }
    }

    @Test
    fun `a transport that throws an exception becomes Io and the permit stays spent`() = runTest {
        val clock = virtualClock()
        val thrown = IllegalStateException()
        budgetOn(clock, minuteWindow(10)).use { real ->
            val budget = PermitRecordingBudget(real)
            val dispatch = dispatchOn(clock, ThrowingTransport(thrown), budget, RecordingLog())

            val failure = assertIs<TransportFailure.Io>(dispatch.send(request, 1, Lane.Interactive, Duration.ZERO).exceptionOrNull())

            assertSame(thrown, failure.cause)
            assertEquals(listOf("complete()"), budget.calls)
            assertEquals(1, real.utilisation().windows.single().used)
        }
    }

    @Test
    fun `an Error from the transport propagates and the permit stays spent`() = runTest {
        val clock = virtualClock()
        val boom = Boom()
        budgetOn(clock, minuteWindow(10)).use { real ->
            val budget = PermitRecordingBudget(real)
            val dispatch = dispatchOn(clock, ThrowingTransport(boom), budget, RecordingLog())

            val thrown = assertFailsWith<Boom> { dispatch.send(request, 1, Lane.Interactive, Duration.ZERO) }

            assertSame(boom, thrown)
            assertEquals(listOf("complete()"), budget.calls)
            assertEquals(1, real.utilisation().windows.single().used)
        }
    }

    @Test
    fun `an Error from the reader propagates`() = runTest {
        val clock = virtualClock()
        val boom = Boom()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "ok"))
        budgetOn(clock, minuteWindow(10)).use { real ->
            val budget = PermitRecordingBudget(real)
            val dispatch = dispatchOn(clock, fake, budget, RecordingLog(), ThrowingSignalsReader(boom))

            val thrown = assertFailsWith<Boom> { dispatch.send(request, 1, Lane.Interactive, Duration.ZERO) }

            assertSame(boom, thrown)
            assertEquals(listOf("complete()"), budget.calls)
            assertEquals(1, fake.calls.size)
        }
    }

    @Test
    fun `waited is measured from the request to the grant on the injected time source`() = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, respondWith(200, "ok"))
        clock.advanceBy(40.seconds)                              // 20 seconds before the minute window's next boundary
        budgetOn(clock, minuteWindow(1)).use { budget ->
            val dispatch = dispatchOn(clock, fake, budget, RecordingLog())

            val first = dispatch.send(request, 1, Lane.Interactive, Duration.ZERO).getOrThrow()
            assertEquals(Duration.ZERO, first.waited)

            val second = async { dispatch.send(request, 1, Lane.Interactive, 1.minutes) }
            clock.advanceBy(19.seconds)
            assertFalse(second.isCompleted)
            clock.advanceBy(1.seconds)
            assertTrue(second.isCompleted)
            assertEquals(20.seconds, second.await().getOrThrow().waited)
            assertEquals(2, fake.calls.size)
        }
    }
}
