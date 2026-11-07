package com.spartanlabs.testing.support.webtools.feeds

/** Builders of byte documents for the tests: each returns a fresh array. */
object ByteDocuments {

    /** [text] as UTF-8. */
    fun utf8(text: String): ByteArray = text.encodeToByteArray()

    /** [text] as UTF-16 little-endian, with a byte-order mark when [mark] holds. */
    fun utf16le(text: String, mark: Boolean = true): ByteArray = utf16(text, littleEndian = true, mark = mark)

    /** [text] as UTF-16 big-endian, with a byte-order mark when [mark] holds. */
    fun utf16be(text: String, mark: Boolean = true): ByteArray = utf16(text, littleEndian = false, mark = mark)

    /** [text] as ISO-8859-1: every char must be at or below U+00FF. */
    fun latin1(text: String): ByteArray {
        require(text.all { it.code <= 0xFF }) { "latin1 text must hold only characters at or below U+00FF" }
        return ByteArray(text.length) { text[it].code.toByte() }
    }

    /** The given byte values, each in 0..255. */
    fun bytes(vararg values: Int): ByteArray {
        require(values.all { it in 0..255 }) { "a byte value must be in 0..255" }
        return ByteArray(values.size) { values[it].toByte() }
    }

    /** [unit] repeated [count] times. */
    fun repeatBytes(unit: ByteArray, count: Int): ByteArray {
        val out = ByteArray(unit.size * count)
        for (i in 0 until count) unit.copyInto(out, i * unit.size)
        return out
    }

    private fun utf16(text: String, littleEndian: Boolean, mark: Boolean): ByteArray {
        val units = (if (mark) "﻿" else "") + text
        val out = ByteArray(units.length * 2)
        for (i in units.indices) {
            val code = units[i].code
            val high = (code ushr 8).toByte()
            val low = (code and 0xFF).toByte()
            if (littleEndian) { out[2 * i] = low; out[2 * i + 1] = high } else { out[2 * i] = high; out[2 * i + 1] = low }
        }
        return out
    }
}
