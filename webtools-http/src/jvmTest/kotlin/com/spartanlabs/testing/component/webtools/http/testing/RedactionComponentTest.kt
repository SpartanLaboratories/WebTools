package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.webtools.http.testing.Redaction
import com.spartanlabs.webtools.http.testing.SecretScrubber
import com.spartanlabs.webtools.http.testing.SecretSurvived
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - `Redaction` and the internal `SecretScrubber`: the defaults, every constructor refusal,
 * `toString()`, the ASCII-only folding of the keep and extra lists, and the scrubber's replacement and
 * detection forms.
 */
class RedactionComponentTest {

    @Test
    fun `a default Redaction uses REDACTED a six-character minimum and no lists`() {
        val redaction = Redaction()
        assertEquals("REDACTED", redaction.placeholder)
        assertEquals("REDACTED", Redaction.DEFAULT_PLACEHOLDER)
        assertEquals(6, Redaction.MIN_SECRET_LENGTH)
        assertEquals("Redaction(keep=0, extra=0, secrets=0)", redaction.toString())
    }

    @Test
    fun `a secret shorter than six characters is refused and six is accepted`() {
        assertFailsWith<IllegalArgumentException> { Redaction(secrets = listOf("12345")) }
        assertEquals("Redaction(keep=0, extra=0, secrets=1)", Redaction(secrets = listOf("123456")).toString())
    }

    @Test
    fun `an ill-formed secret is refused`() {
        assertFailsWith<IllegalArgumentException> { Redaction(secrets = listOf("abc\uD800def")) }   // unpaired high surrogate
    }

    @Test
    fun `a bad placeholder and a secret overlapping the placeholder are refused`() {
        assertFailsWith<IllegalArgumentException> { Redaction(placeholder = "") }
        assertFailsWith<IllegalArgumentException> { Redaction(placeholder = "a\rb") }
        assertFailsWith<IllegalArgumentException> { Redaction(placeholder = "a\nb") }
        assertFailsWith<IllegalArgumentException> { Redaction(placeholder = "a\u0000b") }
        assertFailsWith<IllegalArgumentException> { Redaction(secrets = listOf("REDACTED-1")) }
        assertFailsWith<IllegalArgumentException> { Redaction(secrets = listOf("EDACTE")) }
    }

    @Test
    fun `toString prints counts and no secret`() {
        val redaction = Redaction(keepQueryParams = setOf("a", "b"), extraSensitiveQueryParams = setOf("c"), secrets = listOf("SECRET-CANARY-7f3a"))
        assertEquals("Redaction(keep=2, extra=1, secrets=1)", redaction.toString())
        assertFalse("CANARY" in redaction.toString())
    }

    @Test
    fun `the keep and extra lists compare ASCII case-insensitively only`() {
        assertFalse(Redaction(keepQueryParams = setOf("SORT_KEY")).isSensitiveQuery("sort_key"))
        assertTrue(Redaction(extraSensitiveQueryParams = setOf("UID")).isSensitiveQuery("uid"))
        assertTrue(Redaction().isSensitiveQuery("sort_key"))
        // U+212A KELVIN SIGN: an ASCII fold leaves it alone, so "Key" is not "key".
        assertTrue(Redaction(keepQueryParams = setOf("Key")).isSensitiveQuery("key"))
        assertFalse(Redaction(extraSensitiveQueryParams = setOf("Kid")).isSensitiveQuery("kid"))
    }

    @Test
    fun `the scrubber replaces the raw query-encoded and URLEncoder forms longest first`() {
        val scrubber = SecretScrubber(listOf("p@ss w0rd!"), "REDACTED")
        val input = "raw=p@ss w0rd! query=p%40ss%20w0rd%21 form=p%40ss+w0rd%21 end".encodeToByteArray()
        assertEquals("raw=REDACTED query=REDACTED form=REDACTED end", scrubber.scrub(input).getOrThrow().decodeToString())
    }

    @Test
    fun `binary body bytes survive untouched and no secrets returns the input`() {
        val binary = byteArrayOf(0, 1, 0xFF.toByte(), 0x25, 0x34, 0x31, 0x0A)
        assertContentEquals(binary, SecretScrubber(listOf("abcdef"), "REDACTED").scrub(binary).getOrThrow())
        val input = "nothing to do".encodeToByteArray()
        assertSame(input, SecretScrubber(emptyList(), "REDACTED").scrub(input).getOrThrow())
    }

    @Test
    fun `a lower-case-hex percent-escape of a secret is detected and the scrub fails`() {
        val scrubber = SecretScrubber(listOf("abc/def"), "REDACTED")
        assertEquals("x=REDACTED", scrubber.scrub("x=abc%2Fdef".encodeToByteArray()).getOrThrow().decodeToString())
        val survived = scrubber.scrub("x=abc%2fdef".encodeToByteArray())
        assertIs<SecretSurvived>(survived.exceptionOrNull())
        assertFalse("abc" in survived.exceptionOrNull()!!.message!!)
        val fullEscape = scrubber.scrub("%61%62%63%2f%64%65%66".encodeToByteArray())
        assertIs<SecretSurvived>(fullEscape.exceptionOrNull())
    }

    @Test
    fun `the full percent-escape in upper-case hex is detected`() {
        val survived = SecretScrubber(listOf("abc/def"), "REDACTED").scrub("x %61%62%63%2F%64%65%66 y".encodeToByteArray())
        assertIs<SecretSurvived>(survived.exceptionOrNull())
    }

    @Test
    fun `the form-encoded form is replaced and its lower-hex variant is detected`() {
        val scrubber = SecretScrubber(listOf("ab cd/ef"), "REDACTED")
        assertEquals("x REDACTED y", scrubber.scrub("x ab+cd%2Fef y".encodeToByteArray()).getOrThrow().decodeToString())
        assertIs<SecretSurvived>(scrubber.scrub("x ab+cd%2fef y".encodeToByteArray()).exceptionOrNull())
    }

    @Test
    fun `an echo escaped only in part is neither replaced nor detected`() {
        val input = "x SECRET%2dCANARY y".encodeToByteArray()
        val result = SecretScrubber(listOf("SECRET-CANARY"), "REDACTED").scrub(input)
        assertTrue(result.isSuccess, result.toString())
        assertContentEquals(input, result.getOrThrow())
    }

    @Test
    fun `a secret whose form contains another secret's form is replaced whole, longest first`() {
        val scrubber = SecretScrubber(listOf("abcdef", "abcdefgh"), "REDACTED")
        assertEquals("x REDACTED y", scrubber.scrub("x abcdefgh y".encodeToByteArray()).getOrThrow().decodeToString())
    }
}
