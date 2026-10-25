package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.testing.support.webtools.http.UrlInputGenerator
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Level 4c - 50,000 inputs from `UrlInputGenerator` (seed 11): `Url.parse` never throws, and every
 * success re-parses from its `toString()` to an equal `Url`.
 */
class UrlParseGarbageNonFunctionalTest {

    private val seed = 11
    private val count = 50_000

    @Test
    fun `parse never throws and every success round-trips`() {
        var accepted = 0
        UrlInputGenerator.generate(seed, count).forEachIndexed { index, input ->
            val result = try {
                Url.parse(input)
            } catch (t: Throwable) {
                fail("seed $seed input #$index ${UrlCorpus.visible(input)} threw $t")
            }
            result.onSuccess { url ->
                accepted++
                val again = Url.parse(url.toString()).getOrElse {
                    fail("seed $seed input #$index ${UrlCorpus.visible(input)}: canonical ${url} was rejected: ${it.message}")
                }
                assertEquals(url, again, "seed $seed input #$index ${UrlCorpus.visible(input)}")
            }
        }
        assertTrue(accepted > 0, "seed $seed produced no accepted input")
    }
}
