package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.PermitRecordingBudget
import com.spartanlabs.testing.support.webtools.http.budgetOn
import com.spartanlabs.testing.support.webtools.http.clientOn
import com.spartanlabs.testing.support.webtools.http.minuteWindow
import com.spartanlabs.testing.support.webtools.http.respondWith
import com.spartanlabs.testing.support.webtools.http.stringEndpoint
import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.UpstreamClient
import com.spartanlabs.webtools.http.WindowedCallBudget
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.VirtualClock
import com.spartanlabs.webtools.http.testing.virtualClock
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - what `UpstreamClient` does with the permit of a call, over a recorded budget supplied to the client: an `InvalidRequest` from the
 * transport releases it (the unit is refunded); every other transport failure, a `Closed` transport included, leaves it spent with no signals; a
 * response completes it with the signals; a refused or closed budget comes back as a value and nothing is sent; a cancelled caller leaves the permit
 * spent and its cancellation propagates.
 */
class UpstreamClientPermitRulesComponentTest {

    // What a test needs to drive one client: the clock, the fake transport, the real budget, its recording wrapper and the client.
    private class Rig(
        val clock: VirtualClock,
        val fake: FakeHttpTransport,
        val real: WindowedCallBudget,
        val budget: PermitRecordingBudget,
        val client: UpstreamClient,
    ) {
        suspend fun used(): Int = real.utilisation().windows.single().used
    }

    // A client charging a recorded budget of `limit` a minute, with no cache; the fake answers every request with the steps.
    private fun rigged(vararg steps: Step, limit: Int = 10, block: suspend TestScope.(Rig) -> Unit): TestResult = runTest {
        val clock = virtualClock()
        val fake = FakeHttpTransport(clock.timeSource).on(RequestMatch.Any, *steps)
        budgetOn(clock, minuteWindow(limit)).use { real ->
            val recording = PermitRecordingBudget(real)
            clientOn(clock, fake) { budget(recording) }.use { client -> block(Rig(clock, fake, real, recording, client)) }
        }
    }

    @Test
    fun `InvalidRequest from the transport releases the permit`() {
        val invalid = TransportFailure.InvalidRequest("x")
        rigged(Step.fail(invalid)) { rig ->
            assertSame(invalid, rig.client.call(stringEndpoint()).exceptionOrNull())

            assertEquals(listOf("release()", "complete()"), rig.budget.calls)
            assertEquals(0, rig.used())                                          // the unit was refunded
        }
    }

    @Test
    fun `RefusedByPolicy from the transport leaves the permit spent`() {
        val refused = TransportFailure.RefusedByPolicy(RefusalReason.DeniedAddress)
        rigged(Step.fail(refused)) { rig ->
            assertSame(refused, rig.client.call(stringEndpoint()).exceptionOrNull())

            assertEquals(listOf("complete()"), rig.budget.calls)
            assertEquals(1, rig.used())
        }
    }

    @Test
    fun `Closed from the transport leaves the permit spent`() = rigged(respondWith(200, "ok")) { rig ->
        rig.fake.close()                                                         // `Closed` is not a `TransportFailure`: the fake returns it once closed

        val closed = assertIs<HttpFailure.Closed>(rig.client.call(stringEndpoint()).exceptionOrNull())

        assertEquals("transport", closed.component)
        assertEquals(listOf("complete()"), rig.budget.calls)
        assertEquals(1, rig.used())
    }

    @Test
    fun `each other transport failure leaves the permit spent with no signals`() = rigged(respondWith(200, "ok")) { rig ->
        val failures = listOf(
            TransportFailure.Timeout(),
            TransportFailure.Io(),
            TransportFailure.TlsFailed(),
            TransportFailure.Protocol(),
            TransportFailure.BodyTooLarge(1, null),
            TransportFailure.TooManyRedirects(5),
            TransportFailure.RetryRefused(),
            TransportFailure.DnsFailed("h"),
            TransportFailure.ConnectFailed(),
        )
        failures.forEachIndexed { index, failure ->
            rig.fake.on(RequestMatch.Any, Step.fail(failure))                    // the stub added last wins

            assertSame(failure, rig.client.call(stringEndpoint()).exceptionOrNull(), failure.toString())

            assertEquals(listOf("complete()"), rig.budget.permits.last().calls, failure.toString())
            assertEquals(index + 1, rig.budget.permits.size, failure.toString())
            assertEquals(index + 1, rig.used(), failure.toString())
        }
        assertEquals(Duration.ZERO, rig.real.utilisation().pausedFor)
    }

    @Test
    fun `a response completes the permit with signals`() = rigged(respondWith(200, "ok")) { rig ->
        assertEquals(Lookup.Found("ok"), rig.client.call(stringEndpoint()).getOrThrow())

        assertEquals(listOf("complete(signals)", "complete()"), rig.budget.calls)
        assertEquals(1, rig.used())
    }

    @Test
    fun `BudgetExhausted comes back as a value and nothing is sent`() = rigged(respondWith(200, "ok"), limit = 1) { rig ->
        rig.client.call(stringEndpoint()).getOrThrow()                            // the window's one unit

        val exhausted = assertIs<HttpFailure.BudgetExhausted>(rig.client.call(stringEndpoint()) { maxWait = Duration.ZERO }.exceptionOrNull())

        assertEquals(ExhaustedReason.Window, exhausted.reason)
        assertEquals(1, rig.fake.calls.size)                                     // only the first
        assertEquals(1, rig.budget.permits.size)                                 // no second permit
    }

    @Test
    fun `a closed budget returns Closed budget as a value and nothing is sent`() = rigged(respondWith(200, "ok")) { rig ->
        rig.real.close()

        val closed = assertIs<HttpFailure.Closed>(rig.client.call(stringEndpoint()).exceptionOrNull())

        assertEquals("budget", closed.component)
        assertTrue(rig.fake.calls.isEmpty())
        assertTrue(rig.budget.permits.isEmpty())
    }

    @Test
    fun `a cancelled caller leaves the permit spent and the cancellation propagates`() = rigged(Step.respond(200, delay = 10.seconds)) { rig ->
        val call = async { rig.client.call(stringEndpoint()) }
        rig.clock.advanceBy(1.seconds)                                           // the call holds its permit and waits in the fake
        assertEquals(1, rig.fake.calls.size)

        call.cancel()
        rig.clock.advanceBy(Duration.ZERO)

        assertTrue(call.isCancelled)
        assertEquals(listOf("complete()"), rig.budget.calls)
        assertEquals(1, rig.used())
    }
}
