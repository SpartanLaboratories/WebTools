package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.Boom
import com.spartanlabs.webtools.http.LOADER_CANCELLED_MESSAGE
import com.spartanlabs.webtools.http.Lookup
import com.spartanlabs.webtools.http.runLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - the `internal` `runLoader`, under `runTest` (a `withTimeout` inside a loader expires on virtual time, never
 * the wall clock): a returned `Found`, `Missing` or failure passes through as the same instance; a thrown
 * `IllegalStateException` becomes that failure; a cancellation that escapes the loader while the runner's coroutine is
 * active (a `withTimeout` expiring, a thrown `CancellationException`, a returned `Result.failure(CancellationException())`)
 * becomes an `IllegalStateException` with the constant message and the original as its cause; when the runner's own
 * coroutine is cancelled the `CancellationException` is rethrown and nothing is returned; an `Error` propagates as the
 * same instance. The two own-cancellation tests launch the runner as its own coroutine, run the scheduler once and assert
 * on `isCompleted`, `isCancelled` and what was recorded, never `join()`, so a hang fails an assertion at once instead of
 * timing out.
 */
class LoaderRunnerComponentTest {

    @Test
    fun `a returned Found Missing or failure passes through as the same instance`() = runTest {
        val found = Lookup.Found("v")
        assertSame(found, runLoader<String> { Result.success(found) }.getOrNull())
        assertSame(Lookup.Missing, runLoader<String> { Result.success(Lookup.Missing) }.getOrNull())
        val failure = IllegalArgumentException("returned")
        assertSame(failure, runLoader<String> { Result.failure(failure) }.exceptionOrNull())
    }

    @Test
    fun `a thrown exception becomes that failure`() = runTest {
        val thrown = IllegalStateException("thrown")
        assertSame(thrown, runLoader<String> { throw thrown }.exceptionOrNull())
    }

    @Test
    fun `a timeout expiring inside the loader becomes the constant failure with the timeout as its cause`() = runTest {
        val result = runLoader<String> { withTimeout(10) { awaitCancellation() } }
        val failure = assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(LOADER_CANCELLED_MESSAGE, failure.message)
        assertIs<TimeoutCancellationException>(failure.cause)
        assertTrue(currentCoroutineContext().isActive, "the runner's coroutine was cancelled")
    }

    @Test
    fun `a thrown CancellationException while active becomes the constant failure with the original as its cause`() = runTest {
        val cancellation = CancellationException("x")
        val result = runLoader<String> { throw cancellation }
        val failure = assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(LOADER_CANCELLED_MESSAGE, failure.message)
        assertSame(cancellation, failure.cause)
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test
    fun `a returned CancellationException while active becomes the constant failure with the original as its cause`() = runTest {
        val cancellation = CancellationException("returned")
        val result = runLoader<String> { Result.failure(cancellation) }
        val failure = assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(LOADER_CANCELLED_MESSAGE, failure.message)
        assertSame(cancellation, failure.cause)
    }

    @Test
    fun `a cancellation is rethrown when the runner's own coroutine is cancelled by a throwing loader`() = runTest {
        var returned: Result<Lookup<String>>? = null
        var thrown: Throwable? = null
        val runner = launch {
            try {
                returned = runLoader {
                    currentCoroutineContext()[Job]!!.cancel()
                    throw CancellationException("own")
                }
            } catch (e: Throwable) {
                thrown = e
            }
        }
        // The loader never suspends, so one pass of the scheduler runs the runner to its end.
        testScheduler.runCurrent()
        assertTrue(runner.isCompleted, "the runner's coroutine is still running")
        assertNull(returned, "the runner returned a value although its coroutine was cancelled")
        assertIs<CancellationException>(thrown)
        assertTrue(runner.isCancelled)
    }

    @Test
    fun `a cancellation is rethrown when the runner's own coroutine is cancelled by a returning loader`() = runTest {
        var returned: Result<Lookup<String>>? = null
        var thrown: Throwable? = null
        val runner = launch {
            try {
                returned = runLoader {
                    currentCoroutineContext()[Job]!!.cancel()
                    Result.failure(CancellationException("own"))
                }
            } catch (e: Throwable) {
                thrown = e
            }
        }
        // The loader never suspends, so one pass of the scheduler runs the runner to its end.
        testScheduler.runCurrent()
        assertTrue(runner.isCompleted, "the runner's coroutine is still running")
        assertNull(returned, "the runner returned a value although its coroutine was cancelled")
        assertIs<CancellationException>(thrown)
        assertTrue(runner.isCancelled)
    }

    @Test
    fun `an Error propagates as the same instance`() = runTest {
        val boom = Boom()
        val thrown = assertFailsWith<Boom> { runLoader<String> { throw boom } }
        assertSame(boom, thrown)
    }
}
