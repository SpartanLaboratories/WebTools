package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.testing.support.webtools.http.UrlInputGenerator
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlParseException
import com.spartanlabs.webtools.http.UrlProblem
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Level 4a - the `Url` versus `HttpUrl` differential (plan §9.1): the whole corpus plus 200,000 generated
 * inputs (seed 7). For every input, `Url.parse` is not wrapped in `try`, so a throw is a failure; a
 * `Url` failure is allowed (stricter); a `Url` success must agree with `HttpUrl` on scheme, host, port,
 * path and query, carry no user-info, print as `HttpUrl` prints without the fragment, re-parse to an equal
 * `Url`, and redact correctly. The coverage floors keep the run from going vacuous. The environment
 * variable `WEBTOOLS_DIFFERENTIAL_INPUTS` raises the count; the floors scale with it.
 */
class UrlOkHttpDifferentialDeterministicTest {

    private val seed = 7
    private val defaultInputs = 200_000
    private val inputs: Int = System.getenv("WEBTOOLS_DIFFERENTIAL_INPUTS")?.toIntOrNull() ?: defaultInputs

    private class Counts {
        var total = 0
        var accepted = 0
        var theirsThrew = 0
        var withBracket = 0
        var withDotSegment = 0
        var withQuery = 0
        var withPercent = 0
        var withNonAscii = 0
        val rejectedByKind = HashMap<UrlProblem, Int>()
    }

    private fun theirs(input: String, counts: Counts): HttpUrl? = try {
        input.toHttpUrlOrNull()
    } catch (t: Throwable) {
        counts.theirsThrew++
        null
    }

    private fun check(input: String, label: String, counts: Counts) {
        counts.total++
        val mine = Url.parse(input)
        val oracle = theirs(input, counts)
        val exception = mine.exceptionOrNull()
        if (exception != null) {
            val problem = (exception as UrlParseException).problem
            counts.rejectedByKind[problem] = (counts.rejectedByKind[problem] ?: 0) + 1
            return
        }
        val url = mine.getOrThrow()
        counts.accepted++
        assertNotNull(oracle, "$label: Url accepted ${UrlCorpus.visible(input)} as $url, HttpUrl rejected it")
        assertEquals(oracle.scheme, url.scheme, "$label: scheme of ${UrlCorpus.visible(input)}")
        assertEquals(oracle.host, url.host, "$label: host of ${UrlCorpus.visible(input)}")
        assertEquals(oracle.port, url.port, "$label: port of ${UrlCorpus.visible(input)}")
        assertEquals(oracle.encodedPath, url.encodedPath, "$label: path of ${UrlCorpus.visible(input)}")
        assertEquals(oracle.encodedQuery, url.encodedQuery, "$label: query of ${UrlCorpus.visible(input)}")
        assertEquals("", oracle.encodedUsername, "$label: user of ${UrlCorpus.visible(input)}")
        assertEquals("", oracle.encodedPassword, "$label: password of ${UrlCorpus.visible(input)}")
        assertEquals(oracle.newBuilder().fragment(null).build().toString(), url.toString(), "$label: text of ${UrlCorpus.visible(input)}")
        val again = Url.parse(url.toString()).getOrElse { fail("$label: canonical $url of ${UrlCorpus.visible(input)} was rejected: ${it.message}") }
        assertEquals(url, again, "$label: re-parse of ${UrlCorpus.visible(input)}")
        val query = url.encodedQuery
        if (query == null) assertEquals(url.toString(), url.redacted(), label)
        else {
            assertTrue(url.redacted().endsWith("?..."), label)
            assertEquals(url.toString().length - query.length + 3, url.redacted().length, label)
        }
        if ('[' in input) counts.withBracket++
        if (input.contains("/.") || input.contains("/%2e") || input.contains("/%2E")) counts.withDotSegment++
        if ('?' in input) counts.withQuery++
        if ('%' in input) counts.withPercent++
        if (input.any { it.code >= 0x80 }) counts.withNonAscii++
    }

    @Test
    fun `Url agrees with HttpUrl on the corpus and on the generated inputs`() {
        val counts = Counts()
        UrlCorpus.allInputs.forEachIndexed { index, input -> check(input, "corpus #$index", counts) }
        UrlInputGenerator.generate(seed, inputs).forEachIndexed { index, input -> check(input, "seed $seed input #$index", counts) }
        val scale = inputs.toDouble() / defaultInputs
        val summary = "inputs=${counts.total} accepted=${counts.accepted} httpUrlThrew=${counts.theirsThrew} " +
            "bracket=${counts.withBracket} dot=${counts.withDotSegment} query=${counts.withQuery} percent=${counts.withPercent} " +
            "nonAscii=${counts.withNonAscii} rejectedByKind=${counts.rejectedByKind.toSortedMap()}"
        println("UrlOkHttpDifferentialDeterministicTest seed=$seed $summary")
        // Plan §9.1 coverage floors, stated for 200,000 inputs and scaled linearly with WEBTOOLS_DIFFERENTIAL_INPUTS.
        assertTrue(counts.accepted >= inputs * 0.25, "fewer than 25% accepted: $summary")
        assertTrue(counts.withBracket >= 1_000 * scale, "too few accepted inputs with [: $summary")
        assertTrue(counts.withDotSegment >= 1_000 * scale, "too few accepted inputs with a dot segment: $summary")
        assertTrue(counts.withQuery >= 1_000 * scale, "too few accepted inputs with ?: $summary")
        assertTrue(counts.withPercent >= 1_000 * scale, "too few accepted inputs with a percent escape: $summary")
        assertTrue(counts.withNonAscii >= 1_000 * scale, "too few accepted inputs with non-ASCII: $summary")
        assertTrue(counts.theirsThrew >= 100 * scale, "too few inputs on which HttpUrl throws: $summary")
        (UrlProblem.entries - UrlProblem.DotSegment).forEach { kind ->
            assertTrue((counts.rejectedByKind[kind] ?: 0) >= 1, "no rejection of kind $kind: $summary")
        }
    }
}
