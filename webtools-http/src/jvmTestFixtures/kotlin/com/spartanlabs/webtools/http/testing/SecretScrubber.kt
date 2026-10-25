package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.encodeQueryComponent
import java.net.URLEncoder

/**
 * Replaces every form of each secret in a serialised fixture by the placeholder, then searches the result
 * for any form that could still identify the secret. The forms replaced are the raw UTF-8 text,
 * `encodeQueryComponent(secret)` and `URLEncoder.encode(secret)`, longest first; the forms detected add the
 * lower-case-hex variants of the two encodings and the full percent-escape of every byte in either hex case.
 * A plain byte scan: fixtures are small.
 */
internal class SecretScrubber(secrets: List<String>, private val placeholder: String) {
    private val replace: List<ByteArray> =
        secrets.flatMap(::forms).distinct().sortedByDescending { it.length }.map { it.toByteArray(Charsets.UTF_8) }
    private val detect: List<ByteArray> =
        (secrets.flatMap(::forms) + secrets.flatMap(::lowerHexForms) + secrets.flatMap(::fullEscapes)).distinct().map { it.toByteArray(Charsets.UTF_8) }
    private val replacement: ByteArray = placeholder.toByteArray(Charsets.UTF_8)

    /**
     * Scrubs [bytes].
     *
     * @return the bytes with every form replaced by the placeholder, or a failure holding [SecretSurvived] when a
     *   detectable form remains. With no secrets, the input itself.
     */
    fun scrub(bytes: ByteArray): Result<ByteArray> {
        val replaced = replace.fold(bytes) { current, needle -> current.replaceAll(needle, replacement) }
        return if (detect.none { replaced.contains(it) }) Result.success(replaced) else Result.failure(SecretSurvived())
    }

    // Redaction has checked that encodeQueryComponent succeeds for every secret.
    private fun forms(secret: String): List<String> =
        listOf(secret, encodeQueryComponent(secret).getOrThrow(), URLEncoder.encode(secret, Charsets.UTF_8))

    private fun lowerHexForms(secret: String): List<String> = forms(secret).drop(1).map(::lowerHexEscapes)

    // Every %XX escape with its two hex digits lower-cased; nothing else changes.
    private fun lowerHexEscapes(encoded: String): String = buildString(encoded.length) {
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            if (c == '%' && i + 2 < encoded.length) {
                append('%').append(asciiLowercase(encoded.substring(i + 1, i + 3)))
                i += 3
            } else {
                append(c)
                i++
            }
        }
    }

    private fun fullEscapes(secret: String): List<String> {
        val bytes = secret.toByteArray(Charsets.UTF_8)
        val upper = bytes.joinToString("") { "%" + "0123456789ABCDEF"[(it.toInt() and 0xFF) shr 4] + "0123456789ABCDEF"[it.toInt() and 0x0F] }
        return listOf(upper, asciiLowercase(upper))
    }
}

/** A secret form remained after scrubbing; the file is not written. The message never holds the secret. */
internal class SecretSurvived : Exception("a secret survived scrubbing")

private fun ByteArray.indexOf(needle: ByteArray, from: Int): Int {
    if (needle.isEmpty()) return -1
    var i = from
    while (i <= size - needle.size) {
        var j = 0
        while (j < needle.size && this[i + j] == needle[j]) j++
        if (j == needle.size) return i
        i++
    }
    return -1
}

private fun ByteArray.contains(needle: ByteArray): Boolean = indexOf(needle, 0) >= 0

private fun ByteArray.replaceAll(needle: ByteArray, replacement: ByteArray): ByteArray {
    var at = indexOf(needle, 0)
    if (at < 0) return this
    val out = java.io.ByteArrayOutputStream(size)
    var from = 0
    while (at >= 0) {
        out.write(this, from, at - from)
        out.write(replacement, 0, replacement.size)
        from = at + needle.size
        at = indexOf(needle, from)
    }
    out.write(this, from, size - from)
    return out.toByteArray()
}
