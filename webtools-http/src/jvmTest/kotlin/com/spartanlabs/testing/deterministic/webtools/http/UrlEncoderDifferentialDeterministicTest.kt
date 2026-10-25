package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.webtools.http.UrlParseException
import com.spartanlabs.webtools.http.UrlProblem
import com.spartanlabs.webtools.http.encodePathSegment
import com.spartanlabs.webtools.http.encodeQueryComponent
import okhttp3.HttpUrl
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Level 4a - the encoder differential (plan §9.1): 200,000 strings drawn from all 128 ASCII characters,
 * the Unicode pool, `.`, `..`, `%2e`, `%41` and concatenations. `encodePathSegment(s)` equals the single
 * segment `HttpUrl.Builder().scheme("http").host("h").addPathSegment(s).build().encodedPath` minus its
 * leading `/` (and fails instead for `.`, `..` and an unpaired surrogate); `encodeQueryComponent(s)` equals
 * the name part of `addQueryParameter(s, "v")` and the value part of `addQueryParameter("k", s)` (and fails
 * for an unpaired surrogate).
 */
class UrlEncoderDifferentialDeterministicTest {

    private val seed = 7
    private val inputs = 200_000

    private val pieces: List<String> = (0..127).map { it.toChar().toString() } + listOf(
        "é", "ß", "日", "本", "😀", "☃", "ſ", "İ", "Ｈ", "。", "\u212A", "\u00A0",   // U+212A KELVIN SIGN, U+00A0 NBSP, then U+2028 LINE SEPARATOR, U+3000 IDEOGRAPHIC SPACE, U+0085 NEL, U+200B ZWSP, U+FEFF BOM
        "\u2028", "\u3000", "\u0085", "\u200B", "\uFEFF", "\uD800", "\uDC00", "٣", ".", "..", "%2e", "%41", "%", "%zz",
    )

    private fun hasUnpairedSurrogate(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) i++
            else if (c.isHighSurrogate() || c.isLowSurrogate()) return true
            i++
        }
        return false
    }

    private fun builder(): HttpUrl.Builder = HttpUrl.Builder().scheme("http").host("h")

    private fun checkPath(s: String, label: String) {
        val result = encodePathSegment(s)
        when {
            s == "." || s == ".." -> {
                val exception = result.exceptionOrNull()
                assertIs<UrlParseException>(exception, label)
                assertEquals(UrlProblem.DotSegment, exception.problem, label)
            }
            hasUnpairedSurrogate(s) -> {
                val exception = result.exceptionOrNull()
                assertIs<UrlParseException>(exception, label)
                assertEquals(UrlProblem.NotUnicode, exception.problem, label)
            }
            else -> {
                val theirs = builder().addPathSegment(s).build().encodedPath.removePrefix("/")
                assertEquals(theirs, result.getOrThrow(), "$label: path segment ${UrlCorpus.visible(s)}")
            }
        }
    }

    private fun checkQuery(s: String, label: String) {
        val result = encodeQueryComponent(s)
        if (hasUnpairedSurrogate(s)) {
            val exception = result.exceptionOrNull()
            assertIs<UrlParseException>(exception, label)
            assertEquals(UrlProblem.NotUnicode, exception.problem, label)
            return
        }
        val mine = result.getOrThrow()
        val asName = builder().addQueryParameter(s, "v").build().encodedQuery!!.removeSuffix("=v")
        val asValue = builder().addQueryParameter("k", s).build().encodedQuery!!.removePrefix("k=")
        assertEquals(asName, mine, "$label: query name ${UrlCorpus.visible(s)}")
        assertEquals(asValue, mine, "$label: query value ${UrlCorpus.visible(s)}")
    }

    @Test
    fun `the encoders agree with HttpUrl Builder`() {
        pieces.forEachIndexed { index, piece ->
            checkPath(piece, "piece #$index")
            checkQuery(piece, "piece #$index")
        }
        val random = Random(seed)
        repeat(inputs) { index ->
            // 0 to 5 pieces per input.
            val s = buildString { repeat(random.nextInt(0, 6)) { append(pieces[random.nextInt(pieces.size)]) } }
            checkPath(s, "seed $seed input #$index")
            checkQuery(s, "seed $seed input #$index")
        }
    }
}
