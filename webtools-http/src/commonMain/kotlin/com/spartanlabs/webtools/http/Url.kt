package com.spartanlabs.webtools.http

/**
 * A canonical `http` or `https` URL. [parse] accepts text and rewrites it exactly as OkHttp 5.5.0's
 * `HttpUrl` does when the host is ASCII: it percent-encodes with `HttpUrl`'s sets (non-ASCII path and
 * query text as UTF-8 in upper-case hex), resolves `.` and `..` (the `%2e` forms included), drops the
 * default port, gives an empty path `/`, trims TAB, LF, FF, CR and SP at the ends, drops a `#fragment`,
 * keeps an existing `%XX` and a malformed `%` as typed, keeps a bare `?` as an empty query, prints IPv6
 * in RFC 5952 form and an IPv4-mapped address as dotted IPv4, and keeps digits-and-dots hosts and a
 * trailing dot as written. It is only ever stricter than `HttpUrl`: it refuses a backslash, whitespace or
 * a control character anywhere in the text, an unpaired surrogate, any scheme but `http` and `https` and
 * any separator but `://`, user-info, a host that is non-ASCII, percent-encoded, outside `a-z 0-9 - _ .`,
 * outside the length rules or holding an `xn--` label, a malformed IPv6 literal (a zone included), and a
 * port that is signed, non-numeric, `0` or above 65535. Building a URL from parts is done as text, then
 * parsed; there is no builder.
 *
 * **Stable Core.** Equality and hash are on the canonical text, so `%2f` and `%2F`, `127.1` and
 * `127.0.0.1`, `h` and `h.` are different URLs. [toString] is the full canonical URL, **query included**;
 * use [redacted] for logs and messages.
 */
public class Url private constructor(
    /** The scheme, `http` or `https`. */
    public val scheme: String,
    /** The canonical host: lower-case ASCII text, or an IPv6 literal without brackets (`':' in host`). */
    public val host: String,
    /** The effective port, 1..65535: the written port, or the scheme's default. */
    public val port: Int,
    /** The canonical path, `/` at least; dot segments resolved, `HttpUrl`'s path encode set applied. */
    public val encodedPath: String,
    /** The canonical query without its `?`: `""` for a bare `?`, `null` when there is none. Not split into pairs; `+` stays `+`. */
    public val encodedQuery: String?,
) {
    // Everything before the query, canonical; computed once.
    private val withoutQuery: String = buildString {
        append(scheme).append("://")
        if (':' in host) append('[').append(host).append(']') else append(host)
        if (port != defaultPortOf(scheme)) append(':').append(port)
        append(encodedPath)
    }
    private val text: String = if (encodedQuery == null) withoutQuery else "$withoutQuery?$encodedQuery"

    /**
     * Returns the URL for logs and messages: the query, which may carry credentials, is replaced.
     *
     * @return [toString] with `?...` in place of the query when there is one (a bare `?` included); not
     *   meant to be parsed back. The path is still shown.
     */
    public fun redacted(): String = if (encodedQuery == null) withoutQuery else "$withoutQuery?..."

    /** The full canonical URL, **query included**; use [redacted] for logs and messages. */
    override fun toString(): String = text

    override fun equals(other: Any?): Boolean = other is Url && other.text == text
    override fun hashCode(): Int = text.hashCode()

    /** The parser. */
    public companion object {
        /**
         * Parses [text] by the rules in the class description; `parse(url.toString())` yields an equal
         * [Url].
         *
         * @param text the URL text, as typed; TAB, LF, FF, CR and SP are trimmed at the ends.
         * @return the canonical URL, or a failure holding an `IllegalArgumentException` with one constant
         *   message per kind of problem; the message never echoes [text].
         */
        public fun parse(text: String): Result<Url> =
            parseUrlText(text).map { Url(it.scheme, it.host, it.port, it.encodedPath, it.encodedQuery) }
    }
}
