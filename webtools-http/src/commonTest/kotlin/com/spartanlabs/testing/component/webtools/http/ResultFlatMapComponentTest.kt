package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.flatMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

/**
 * Level 2 - the `internal` `Result.flatMap`: a success chains; a failure short-circuits without calling
 * the transform; the failure's exception instance passes through unchanged.
 */
class ResultFlatMapComponentTest {

    @Test
    fun `a success chains into the transform's result`() {
        assertEquals(Result.success(4), Result.success(2).flatMap { Result.success(it * 2) })
        val failure = IllegalStateException("inner")
        assertSame(failure, Result.success(2).flatMap<Int, Int> { Result.failure(failure) }.exceptionOrNull())
    }

    @Test
    fun `a failure short-circuits and the transform is not called`() {
        val failure = IllegalStateException("outer")
        var called = false
        val result = Result.failure<Int>(failure).flatMap { called = true; Result.success(it * 2) }
        assertFalse(called)
        assertSame(failure, result.exceptionOrNull())
    }
}
