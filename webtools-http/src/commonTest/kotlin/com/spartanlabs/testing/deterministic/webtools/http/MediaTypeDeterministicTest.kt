package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HttpSyntax
import com.spartanlabs.webtools.http.RequestBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Level 4a - the accepted and refused rows of the research's content-type table, with plan Amendment 1
 * (`text/plain;;`, `text/plain ;`, `text/plain;` and `text/plain ; charset=x` accepted; `text/plain; `
 * refused at index 11), and the failing indices: the offending character, the start of trailing
 * whitespace, or the text length when the text ends too early.
 */
class MediaTypeDeterministicTest {

    @Test
    fun `the accepted rows return -1 and build a RequestBody`() {
        listOf(
            "text/plain", "application/json; charset=utf-8", "application/json;charset=UTF-8", "Text/HTML;Charset=\"utf-8\"",
            "multipart/form-data; boundary=----abc", "*/*", "application/vnd.api+json", "text/plain ; charset=x", "text/plain;",
            "text/plain; a=\"b c\"", "text/plain; a=\"q\\\"x\"", "text/plain;;", "text/plain ;", "text/plain;a=b;c=d", "text/plain; a=\"\"",
        ).forEach { text ->
            assertEquals(-1, HttpSyntax.mediaTypeProblemIndex(text), text)
            assertEquals(text, RequestBody(ByteArray(0), text).contentType)
        }
    }

    @Test
    fun `the refused rows report the failing index`() {
        val rows = mapOf(
            "" to 0,
            "json" to 4,
            "text/" to 5,
            "/plain" to 0,
            "text/plain charset=x" to 11,
            "text/plain; charset" to 19,
            "text/plain; charset=" to 20,
            "text/plain; a=b c=d" to 16,
            "text/plain, text/html" to 10,
            "text/plain; a=\"unterminated" to 27,
            "text/plain\r\nX: y" to 10,
            "téxt/plain" to 1,
            " text/plain" to 0,
            "text/plain " to 10,
            "text/plain; " to 11,
            "text/plain; a=\"é\"" to 15,
            "text/plain; a=\"\\é\"" to 16,
        )
        rows.forEach { (text, index) ->
            assertEquals(index, HttpSyntax.mediaTypeProblemIndex(text), text)
            val exception = assertFailsWith<IllegalArgumentException>(text) { RequestBody(ByteArray(0), text) }
            assertEquals("Content type is not an RFC 9110 media type (first offending character at index $index)", exception.message, text)
        }
    }
}
