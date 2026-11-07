package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.webtools.feeds.sha256
import com.spartanlabs.webtools.feeds.toHex
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 4a - the pure-Kotlin SHA-256 against the RFC 6234 and FIPS 180-4 vectors and the padding boundary; `toHex` of a prefix.
 */
class Sha256DeterministicTest {

    private fun hex(text: String): String = sha256(text.encodeToByteArray()).toHex()

    @Test
    fun `the digest of abc`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex("abc"))
    }

    @Test
    fun `the digest of the empty message`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(""))
    }

    @Test
    fun `the 448-bit and 896-bit messages`() {
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"))
        assertEquals(
            "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1",
            hex("abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"),
        )
    }

    @Test
    fun `one million a`() {
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", sha256(ByteArray(1_000_000) { 'a'.code.toByte() }).toHex())
    }

    @Test
    fun `lengths 55, 56, 63, 64 and 65 around the padding boundary`() {
        val expected = mapOf(
            55 to "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            56 to "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            63 to "7d3e74a05d7db15bce4ad9ec0658ea98e3f06eeecf16b4c6fff2da457ddc2f34",
            64 to "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            65 to "635361c48bb9eab14198e76ea8ab7f1a41685d6ad62aa9146d301d4f17eb0ae0",
        )
        for ((length, digest) in expected) assertEquals(digest, hex("a".repeat(length)), "length $length")
    }

    @Test
    fun `toHex of a prefix`() {
        val bytes = byteArrayOf(0x01, 0xAB.toByte(), 0xFF.toByte(), 0x10)
        assertEquals("01ab", bytes.toHex(2))
        assertEquals("01abff10", bytes.toHex())
        assertEquals("", bytes.toHex(0))
    }
}
