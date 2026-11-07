package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.webtools.feeds.DIGEST_PREFIX
import com.spartanlabs.webtools.feeds.digestId
import com.spartanlabs.webtools.feeds.sha256
import com.spartanlabs.webtools.feeds.toHex
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Level 4a - the pure-Kotlin SHA-256 against the JDK's `MessageDigest`: every message length 0 to 2048 and 1,000 seeded random
 * messages agree; `digestId` equals an id recomputed with `MessageDigest` over the specification's message for 1,000 seeded
 * random entries.
 */
class Sha256DifferentialDeterministicTest {

    private fun jdk(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    @Test
    fun `sha256 equals MessageDigest for every length 0 to 2048 and for 1,000 seeded random messages`() {
        val random = Random(2026)
        for (length in 0..2048) {
            val message = ByteArray(length) { random.nextInt(256).toByte() }
            assertContentEquals(jdk(message), sha256(message), "length $length")
        }
        repeat(1_000) { i ->
            val message = ByteArray(random.nextInt(0, 5_000)) { random.nextInt(256).toByte() }
            assertContentEquals(jdk(message), sha256(message), "random message $i")
        }
    }

    @Test
    fun `digestId equals an id recomputed with MessageDigest for 1,000 seeded random entries`() {
        val random = Random(20261106)
        val alphabet = " \t\nabcXYZ09é中😀"
        fun text(): String? = if (random.nextInt(4) == 0) null else buildString { repeat(random.nextInt(0, 60)) { append(alphabet[random.nextInt(alphabet.length)]) } }
        repeat(1_000) { i ->
            val title = text()
            val summary = text()
            val content = text()
            val enclosure = text()
            val published = if (random.nextBoolean()) random.nextLong(-5_000_000_000_000L, 5_000_000_000_000L) else null
            assertEquals(reference(title, summary, content, enclosure, published), digestId(title, summary, content, enclosure, published), "entry $i")
        }
    }

    // The specification's message, built independently of the module's code: domain, then "\n" + tag + length + ":" + value.
    private fun reference(title: String?, summary: String?, content: String?, enclosure: String?, published: Long?): String {
        fun field(v: String?): String? {
            if (v == null) return null
            val t = v.trim { it == ' ' || it == '\t' || it == '\n' || it == '\u000C' || it == '\r' }
            if (t.isEmpty()) return null
            var end = minOf(t.length, 4_096)
            if (end > 0 && end < t.length && t[end - 1].isHighSurrogate()) end--
            val cut = t.substring(0, end)
            if (cut.isEmpty()) return null
            return buildString {
                for ((i, c) in cut.withIndex()) {
                    val ok = when {
                        c.isHighSurrogate() -> i + 1 < cut.length && cut[i + 1].isLowSurrogate()
                        c.isLowSurrogate() -> i > 0 && cut[i - 1].isHighSurrogate()
                        else -> true
                    }
                    append(if (ok) c else '�')
                }
            }
        }
        val textField = field(title)?.let { "t" to it } ?: (field(summary) ?: field(content))?.let { "b" to it } ?: field(enclosure)?.let { "e" to it }
        val message = buildString {
            append("webtools-feeds/entry-id/1")
            if (textField != null) append("\n").append(textField.first).append(textField.second.length).append(":").append(textField.second)
            if (published != null) {
                val seconds = Math.floorDiv(published, 1000L).toString()
                append("\n").append("d").append(seconds.length).append(":").append(seconds)
            }
        }
        return DIGEST_PREFIX + jdk(message.toByteArray(Charsets.UTF_8)).toHex(16)
    }
}
