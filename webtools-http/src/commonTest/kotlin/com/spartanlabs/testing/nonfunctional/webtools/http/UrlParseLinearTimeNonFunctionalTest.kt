package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Level 4c - `Url.parse` is linear: inputs of 64k and 1M characters in five shapes (a long path of
 * `/..` segments, a long query, a long host label, a million `%`, a long run of `[`) each parse without
 * an exception, and the time for 16x the length is under 100x the time for 1x (a quadratic parse gives
 * about 256x), after a warm-up parse. A failure on a loaded host is rerun once before it is treated as a
 * defect (plan §10).
 */
class UrlParseLinearTimeNonFunctionalTest {

    private val small = 65_536
    private val large = small * 16

    private fun shapes(n: Int): Map<String, String> = mapOf(
        "dot segments" to "http://h" + "/..".repeat(n / 3),
        "long query" to "http://h/?" + "a".repeat(n),
        "long host label" to "http://" + "a".repeat(n) + "/",
        "percent signs" to "http://h/" + "%".repeat(n),
        "brackets" to "http://" + "[".repeat(n) + "/",
    )

    // The best of three runs, so one scheduling hiccup does not decide the ratio.
    private fun time(input: String): Duration = (1..3).minOf {
        val mark = TimeSource.Monotonic.markNow()
        Url.parse(input)
        mark.elapsedNow()
    }

    @Test
    fun `each shape parses without an exception at both sizes`() {
        (shapes(small).values + shapes(large).values).forEach { input -> Url.parse(input) }
    }

    @Test
    fun `sixteen times the length costs less than a hundred times the time`() {
        val smallShapes = shapes(small)
        val largeShapes = shapes(large)
        smallShapes.values.forEach { Url.parse(it) }   // warm-up
        smallShapes.keys.forEach { name ->
            val t1 = time(smallShapes.getValue(name))
            val t16 = time(largeShapes.getValue(name))
            assertTrue(t16 < t1 * 100, "$name: 1x took $t1, 16x took $t16")
        }
    }
}
