package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.catchNonFatal
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Level 2 - the `internal` `catchNonFatal`, under `runBlocking`: a value becomes a success; an
 * `IOException` becomes a failure holding that instance; a `CancellationException` is rethrown; a
 * timeout around a suspend call inside the helper throws `TimeoutCancellationException` (cancellation is
 * not swallowed); an `AssertionError` propagates.
 */
class CatchNonFatalComponentTest {

    @Test
    fun `a value becomes a success`() = runBlocking {
        assertEquals(Result.success(42), catchNonFatal { 42 })
    }

    @Test
    fun `an exception becomes a failure holding the same instance`() = runBlocking {
        val failure = IOException("io")
        assertSame(failure, catchNonFatal<Int> { throw failure }.exceptionOrNull())
    }

    @Test
    fun `a CancellationException is rethrown`() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val thrown = assertFailsWith<CancellationException> { catchNonFatal<Int> { throw cancellation } }
        assertSame(cancellation, thrown)
    }

    @Test
    fun `a timeout around a suspend call inside the helper is not swallowed`() {
        assertFailsWith<TimeoutCancellationException> {
            runBlocking {
                withTimeout(10) { catchNonFatal { delay(1_000) } }
            }
        }
    }

    @Test
    fun `an Error propagates`() = runBlocking {
        val error = AssertionError("fatal")
        val thrown = assertFailsWith<AssertionError> { catchNonFatal<Int> { throw error } }
        assertSame(error, thrown)
    }
}
