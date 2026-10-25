package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpSyntax
import com.spartanlabs.webtools.http.catchNonFatal
import com.spartanlabs.webtools.http.flatMap
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * The `.http` fixture format: optional leading `# ...` provenance lines, a status line
 * (`HTTP/<d>[.<d>] <three digits>[ <reason>]`), `Name: value` header lines, one blank line, then every
 * remaining byte as the body, verbatim. LF or CRLF ends a head line; a lone CR is refused. Every refusal is a
 * [FixtureError] naming the origin and the line, never a header value or a body byte.
 */
internal object HttpMessageCodec {
    private const val LF: Byte = 0x0A
    private const val CR: Byte = 0x0D

    // A decoded head line with its 1-based number.
    private class Line(val number: Int, val text: String)

    // The head lines and the index of the first body byte.
    private class Head(val lines: List<Line>, val bodyStart: Int)

    // The status and the header lines that follow the status line.
    private class StatusLine(val status: Int, val headerLines: List<Line>)

    //region reading
    /** Parses [bytes] as a `.http` file; every failure is a [FixtureError] built for [origin]. */
    fun parse(bytes: ByteArray, origin: String): Result<Fixture> =
        readHead(bytes, origin).flatMap { head ->
            statusOf(head, origin).flatMap { statusLine ->
                headersOf(statusLine.headerLines, origin).map { headers ->
                    Fixture(statusLine.status, headers, bytes.copyOfRange(head.bodyStart, bytes.size))
                }
            }
        }

    private fun fail(origin: String, line: Int, what: String): Result<Nothing> =
        Result.failure(FixtureError("$origin, line $line: $what", null))

    // Walks the bytes line by line up to the first blank line (or the end). LF ends a line; one CR directly
    // before the LF is dropped; any other CR is refused. Each line is decoded as strict UTF-8.
    private fun readHead(bytes: ByteArray, origin: String): Result<Head> {
        if (bytes.isEmpty()) return Result.failure(FixtureError("$origin: the file is empty", null))
        val lines = ArrayList<Line>()
        var start = 0
        var number = 1
        while (start < bytes.size) {
            val lf = indexOf(bytes, LF, start)
            val end = if (lf < 0) bytes.size else lf
            val contentEnd = if (end > start && bytes[end - 1] == CR && lf >= 0) end - 1 else end
            if (indexOf(bytes, CR, start, contentEnd) >= 0) return fail(origin, number, "a carriage return must be followed by a line feed")
            if (contentEnd == start) {
                return if (lines.none { !it.text.startsWith("#") }) fail(origin, number, "a blank line before the status line")
                else Result.success(Head(lines, if (lf < 0) bytes.size else lf + 1))
            }
            val decoded = decodeUtf8(bytes, start, contentEnd)
                ?: return fail(origin, number, "the line is not valid UTF-8")
            lines.add(Line(number, decoded))
            if (lf < 0) break
            start = lf + 1
            number++
        }
        return Result.success(Head(lines, bytes.size))
    }

    private fun indexOf(bytes: ByteArray, wanted: Byte, from: Int, to: Int = bytes.size): Int {
        var i = from
        while (i < to) {
            if (bytes[i] == wanted) return i
            i++
        }
        return -1
    }

    // Strict: a malformed or unmappable sequence is null, never a replacement character.
    private fun decodeUtf8(bytes: ByteArray, from: Int, to: Int): String? =
        catchNonFatal {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, from, to - from))
                .toString()
        }.getOrNull()

    // The first line that does not start with '#' is the status line; the lines after it are the headers.
    private fun statusOf(head: Head, origin: String): Result<StatusLine> {
        val index = head.lines.indexOfFirst { !it.text.startsWith("#") }
        if (index < 0) return fail(origin, head.lines.size + 1, "no status line")
        val line = head.lines[index]
        val status = parseStatusLine(line.text, origin, line.number)
        return status.map { StatusLine(it, head.lines.subList(index + 1, head.lines.size)) }
    }

    // HTTP/<d>[.<d>] <three digits>[ <reason>]; the reason is ignored.
    private fun parseStatusLine(text: String, origin: String, number: Int): Result<Int> {
        val expected = "expected a status line (HTTP/1.1 200 OK)"
        if (!text.startsWith("HTTP/")) return fail(origin, number, expected)
        var i = "HTTP/".length
        if (i >= text.length || !text[i].isAsciiDigit()) return fail(origin, number, expected)
        i++
        if (i < text.length && text[i] == '.') {
            i++
            if (i >= text.length || !text[i].isAsciiDigit()) return fail(origin, number, expected)
            i++
        }
        if (i >= text.length || text[i] != ' ') return fail(origin, number, expected)
        i++
        val codeEnd = text.indexOf(' ', i).let { if (it < 0) text.length else it }
        val code = text.substring(i, codeEnd)
        if (code.length != 3 || !code.all { it.isAsciiDigit() }) return fail(origin, number, "the status code is not three digits")
        val status = code.toInt()
        if (status !in 100..599) return fail(origin, number, "the status code is outside 100..599")
        return Result.success(status)
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    // Name: value per line; the name a token, the value trimmed of OWS and free of NUL (CR and LF cannot
    // reach here). Checked before the builder sees them, so the builder cannot throw.
    private fun headersOf(lines: List<Line>, origin: String): Result<HttpHeaders> {
        val entries = ArrayList<Pair<String, String>>(lines.size)
        for (line in lines) {
            val text = line.text
            if (text[0] == ' ' || text[0] == '\t') return fail(origin, line.number, "a folded header line is not supported")
            val colon = text.indexOf(':')
            if (colon < 0) return fail(origin, line.number, "not a header line (expected Name: value)")
            val name = text.substring(0, colon)
            val bad = if (name.isEmpty()) 0 else HttpSyntax.firstNonTokenIndex(name)
            if (bad >= 0) return fail(origin, line.number, "the header name is not an RFC 9110 token (first offending character at index $bad)")
            val value = HttpSyntax.trimOws(text.substring(colon + 1))
            if (!HttpSyntax.isFieldValue(value)) return fail(origin, line.number, "a header value must not contain NUL")
            entries.add(name to value)
        }
        return Result.success(HttpHeaders { entries.forEach { (name, value) -> add(name, value) } })
    }
    //endregion

    //region writing
    /**
     * Serialises a fixture: `# <comment>\n` lines, `HTTP/1.1 <status>[ <reason>]\n`, `Name: value\n` per
     * entry in order, a blank line, then [body] verbatim. The caller has checked that no comment holds CR or LF.
     */
    fun write(status: Int, headers: HttpHeaders, body: ByteArray, comments: List<String>): ByteArray {
        val head = buildString {
            comments.forEach { append("# ").append(it).append('\n') }
            append("HTTP/1.1 ").append(status)
            reasonPhrase(status)?.let { append(' ').append(it) }
            append('\n')
            headers.entries().forEach { append(it.name).append(": ").append(it.value).append('\n') }
            append('\n')
        }
        val out = ByteArrayOutputStream(head.length + body.size)
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(body)
        return out.toByteArray()
    }

    /** The RFC 9110 reason phrase of a common status, or null for any other. */
    fun reasonPhrase(status: Int): String? = REASONS[status]

    private val REASONS: Map<Int, String> = mapOf(
        100 to "Continue", 101 to "Switching Protocols",
        200 to "OK", 201 to "Created", 202 to "Accepted", 204 to "No Content", 206 to "Partial Content",
        301 to "Moved Permanently", 302 to "Found", 303 to "See Other", 304 to "Not Modified",
        307 to "Temporary Redirect", 308 to "Permanent Redirect",
        400 to "Bad Request", 401 to "Unauthorized", 403 to "Forbidden", 404 to "Not Found",
        405 to "Method Not Allowed", 408 to "Request Timeout", 409 to "Conflict", 410 to "Gone",
        412 to "Precondition Failed", 413 to "Content Too Large", 414 to "URI Too Long",
        415 to "Unsupported Media Type", 416 to "Range Not Satisfiable", 422 to "Unprocessable Content",
        429 to "Too Many Requests",
        500 to "Internal Server Error", 501 to "Not Implemented", 502 to "Bad Gateway",
        503 to "Service Unavailable", 504 to "Gateway Timeout",
    )
    //endregion
}
