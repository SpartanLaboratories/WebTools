package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.UrlProblem

/**
 * The `Url` corpus: the inputs of the research (`research/url-parsing.md`, "The corpus"), each with its
 * expected canonical text or `UrlProblem`. Common code, so the Level 4a tables use it without OkHttp and
 * the `jvmTest` differential sees it.
 */
internal object UrlCorpus {

    /**
     * One input and what `Url.parse` must do with it. Exactly one of [canonical] and [problem] is set.
     *
     * @property input the text handed to `Url.parse`, untrimmed.
     * @property canonical the expected `toString()` when the input is accepted, else `null`.
     * @property problem the expected kind when the input is rejected, else `null`.
     */
    class Case(val input: String, val canonical: String?, val problem: UrlProblem?) {
        override fun toString(): String = "<" + visible(input) + ">"
    }

    /** The canary string placed inside inputs that must never be echoed. */
    const val CANARY: String = "SECRET-CANARY-7f3a"

    /** A 63-character label, the longest accepted. */
    val label63: String = "a".repeat(63)

    /** A 64-character label, rejected. */
    val label64: String = "a".repeat(64)

    /** A 253-character host without a trailing dot, the longest accepted. */
    val host253: String = "$label63.$label63.$label63." + "a".repeat(61)

    /** A 254-character host without a trailing dot, rejected. */
    val host254: String = "$label63.$label63.$label63." + "a".repeat(62)

    /** A 253-character host whose last character is the trailing dot (it counts), accepted. */
    val host253Dotted: String = "$label63.$label63.$label63." + "a".repeat(60) + "."

    /** A 254-character host whose last character is the trailing dot, rejected. */
    val host254Dotted: String = "$label63.$label63.$label63." + "a".repeat(61) + "."

    private fun ok(input: String, canonical: String = input): Case = Case(input, canonical, null)
    private fun bad(input: String, problem: UrlProblem): Case = Case(input, null, problem)

    //region accept
    /** Inputs `Url.parse` accepts, each with its canonical text (research "Must accept" plus plan §9.1's additions). */
    val accept: List<Case> = listOf(
        ok("https://api.opendota.com/api/matches/123?api_key=abc123"),
        ok("https://api.opendota.com/api/players/1/matches?limit=20&offset=0&win=1"),
        ok("HTTP://EXAMPLE.COM:80/a/./b/../c?x=1#frag", "http://example.com/a/c?x=1"),
        ok("https://example.com", "https://example.com/"),
        ok("https://example.com/?"),
        ok("https://example.com/p\u00E4th/\u65E5\u672C?q=\u00FC", "https://example.com/p%C3%A4th/%E6%97%A5%E6%9C%AC?q=%C3%BC"),
        ok("https://example.com/a%2Fb%2fc"),
        ok("https://[2001:db8::1]:8443/a"),
        ok("http://[::ffff:127.0.0.1]/", "http://127.0.0.1/"),
        ok("http://[0:0:0:0:0:0:0:1]:3000/health", "http://[::1]:3000/health"),
        ok("http://[2001:0:0:1:0:0:0:1]/", "http://[2001:0:0:1::1]/"),
        ok("http://127.1/"),
        ok("https://example.com:443/", "https://example.com/"),
        ok("https://example.com:0443/", "https://example.com/"),
        ok("http://h:/p", "http://h/p"),
        ok("  https://example.com/x \n", "https://example.com/x"),
        ok("https://example.com/?filter[name]=x&sort=-date"),
        ok("https://example.com/?b={c}|d"),
        ok("https://example.com/{x}|", "https://example.com/%7Bx%7D%7C"),
        ok("https://example.com/?q='x'", "https://example.com/?q=%27x%27"),
        ok("http://localhost./"),
        ok("https://exa_mple.com/"),
        ok("https://example.com/%zz"),
        ok("https://example.com/a/%2e%2E/b", "https://example.com/b"),
        ok("https://example.com/.%2e", "https://example.com/"),
        ok("https://example.com//a//b"),
        ok("https://example.com/#only-fragment", "https://example.com/"),
        // Added by the plan (§9.1).
        ok("http://[::]/"),
        ok("http://[1:2:3:4:5:6:7::]/", "http://[1:2:3:4:5:6:7:0]/"),
        ok("http://[::1.2.3.4]/", "http://[::102:304]/"),
        ok("https://h:80/"),
        ok("http://h:443/"),
        ok("http://h/p%e9"),
        ok("http://h/?a=b+c"),
        // Dot-segment folds the differential confirms.
        ok("http://h/../../a", "http://h/a"),
        ok("http://h/a/..", "http://h/"),
        ok("http://h/a/.", "http://h/a/"),
        ok("http://h/a/%2e./b", "http://h/b"),
    )
    //endregion

    //region reject
    // Every isUrlSpace code point: SP, HTAB, LF, NUL, VT (U+000B), DEL, NEL (U+0085), NBSP (U+00A0), OGHAM SPACE
    // MARK (U+1680), EN QUAD..HAIR SPACE (U+2000..U+200A), LINE SEPARATOR (U+2028), PARAGRAPH SEPARATOR (U+2029),
    // NARROW NBSP (U+202F), MEDIUM MATHEMATICAL SPACE (U+205F), IDEOGRAPHIC SPACE (U+3000).
    private val whitespaceInside: List<Char> =
        listOf(' ', '\t', '\n', '\u0000', '\u000B', '\u007F', '\u0085', '\u00A0', '\u1680') +
            ('\u2000'..'\u200A').toList() + listOf('\u2028', '\u2029', '\u202F', '\u205F', '\u3000')
    // At either end: the same minus SP, HTAB and LF, which the trim removes.
    private val whitespaceAtEnds: List<Char> = whitespaceInside - setOf(' ', '\t', '\n')

    // Three positions after the authority: path, query, fragment.
    private fun inPathQueryFragment(c: Char, problem: UrlProblem): List<Case> = listOf(
        bad("http://h/a${c}b", problem),
        bad("http://h/?a${c}b", problem),
        bad("http://h/#a${c}b", problem),
    )

    /** Inputs `Url.parse` rejects, each with its kind: the first failing step in plan §6.5's order. */
    val reject: List<Case> = buildList {
        // Empty
        add(bad("", UrlProblem.Empty))
        add(bad("   ", UrlProblem.Empty))
        add(bad("\t\n", UrlProblem.Empty))
        // Scheme
        listOf(
            "example.com/x", "//example.com", "ftp://h/", "file:///etc/passwd", "javascript:alert(1)", "http:/h", "http:h",
            "http\u017F://h/", "https\u212A://h/",
        ).forEach { add(bad(it, UrlProblem.Scheme)) }
        // Host
        listOf(
            "http://", "http:///h", "http://a..b/", "http://.a/", "http://a!b/", "http://a\"b/", "http://a,b/",
            "http://$label64/", "http://$host254/", "http://[::1/", "http://[1:2:3:4:5:6:7::8]/", "http://[::1]x/",
            "http://[g::1]/", "http://[]/", "http://[1.2.3.4]/", "http://[::1.2.3.256]/", "http://[12345::]/",
            "http://[fe80::1%25eth0]/",
        ).forEach { add(bad(it, UrlProblem.Host)) }
        // UserInfo
        listOf("http://u@h/", "http://u:p@h/", "http://@h/", "http://:@h/", "http://h@/", "http://a@b@c/")
            .forEach { add(bad(it, UrlProblem.UserInfo)) }
        // Backslash: in the authority, path, query and fragment.
        add(bad("http://h\\x/", UrlProblem.Backslash))
        addAll(inPathQueryFragment('\\', UrlProblem.Backslash))
        // WhitespaceOrControl: inside the path, query and fragment, and at either end for what is not trimmed.
        whitespaceInside.forEach { addAll(inPathQueryFragment(it, UrlProblem.WhitespaceOrControl)) }
        whitespaceAtEnds.forEach {
            add(bad("${it}http://h/", UrlProblem.WhitespaceOrControl))
            add(bad("http://h/$it", UrlProblem.WhitespaceOrControl))
        }
        // NotUnicode: a lone high and a lone low surrogate.
        addAll(inPathQueryFragment('\uD800', UrlProblem.NotUnicode))
        addAll(inPathQueryFragment('\uDC00', UrlProblem.NotUnicode))
        // HostEncoding
        listOf(
            "http://m\u00FCnchen.de/", "http://%31%32%37.0.0.1/", "http://127.0.0.1%09.evil.example/", "http://a%2eb/",
            "http://\uFF4Cocalhost/", "http://127.0.0.1\u3002evil.example/", "http://\uFF11\uFF12\uFF17.0.0.1/",
        ).forEach { add(bad(it, UrlProblem.HostEncoding)) }
        // HostIdn
        listOf("http://xn--n3h.net/", "http://XN--N3H.net/", "http://xn--abc-/", "http://a.xn--b.c/")
            .forEach { add(bad(it, UrlProblem.HostIdn)) }
        // Port
        listOf(
            "http://h:0/", "http://h:65536/", "http://h:+80/", "http://h:-1/", "http://h:8o/", "http://h:80:80/",
            "http://h:\uFF18\uFF10/", "http://h:99999999999/", "http://::1/",
        ).forEach { add(bad(it, UrlProblem.Port)) }
    }
    //endregion

    //region confusion
    /** The host-spoofing, slash, scheme and `xn--` confusion strings of plan §9.1, accepted or rejected as §6.5 says. */
    val confusion: List<Case> = buildList {
        add(bad("http://evil.example\\@127.0.0.1:80/", UrlProblem.Backslash))
        add(bad("http://127.0.0.1:80\\@evil.example/", UrlProblem.Backslash))
        listOf(
            "http://127.0.0.1\u3002evil.example/", "http://\uFF11\uFF12\uFF17.0.0.1/", "http://127.0.0.1%09.evil.example/",
            "http://localhost%2e/", "http://%31%32%37.0.0.1/", "http://\uFF4C\uFF4F\uFF43\uFF41\uFF4C\uFF48\uFF4F\uFF53\uFF54/",
            "http://\u24DBocalhost/",
        ).forEach { add(bad(it, UrlProblem.HostEncoding)) }
        listOf("http://127.1/", "http://0x7f.1/", "http://169.254.169.254/", "http://localhost./").forEach { add(ok(it)) }
        add(ok("http://127.0.0.1#@evil.example/", "http://127.0.0.1/"))
        add(ok("http://evil.example#@127.0.0.1/", "http://evil.example/"))
        add(ok("http://google.com#@evil.com/", "http://google.com/"))
        add(ok("http://evil.example?@127.0.0.1/", "http://evil.example/?@127.0.0.1/"))
        add(ok("http://[::ffff:127.0.0.1]/", "http://127.0.0.1/"))
        add(ok("http://[::ffff:7f00:1]/", "http://127.0.0.1/"))
        add(ok("http://[::ffff:a9fe:a9fe]/", "http://169.254.169.254/"))
        add(bad("http://foo@evil.com:80@google.com/", UrlProblem.UserInfo))
        add(bad("http://example.com\uFF0F@127.0.0.1/", UrlProblem.UserInfo))
        add(bad("http://[::1]%25eth0/", UrlProblem.Host))
        listOf("/%2e%2e/", ":0", ":65536", ":80:80").forEach { add(bad(it, UrlProblem.Scheme)) }
        listOf("http://h:0/", "http://h:65536/", "http://h:80:80/").forEach { add(bad(it, UrlProblem.Port)) }
        // The slash rows of research F2: only "://" is a separator; a backslash is caught by the scan first.
        listOf("http:a.com", "http:/a.com", "http:", "httpx://a.com/", "http+x://a.com/", "/x")
            .forEach { add(bad(it, UrlProblem.Scheme)) }
        add(bad("http:\\\\a.com", UrlProblem.Backslash))
        add(bad("http:\\a.com", UrlProblem.Backslash))
        // An empty authority after "://" fails at the host step, not the scheme step (§6.5 order).
        listOf("http:///a.com", "http:////a.com", "http://").forEach { add(bad(it, UrlProblem.Host)) }
        add(bad("http:///", UrlProblem.Host))
        // The scheme rows of research F2.
        add(ok("HTTP://EXAMPLE.COM/", "http://example.com/"))
        add(ok("HtTpS://Example.Com/", "https://example.com/"))
        // The xn-- rows of research F5: every label starting xn-- is HostIdn, whatever HttpUrl makes of it.
        listOf(
            "xn--n3h.net", "XN--N3H.net", "xn--bcher-kva.example", "xn--ls8h.la", "xn--abc-", "xn--n3h-", "abc.xn--abc-.com",
            "xn--a-", "xn--a", "xn--", "xn--zz", "xn--ab-c", "xn--0.com", "xn---",
        ).forEach { add(bad("http://$it/", UrlProblem.HostIdn)) }
    }
    //endregion

    /** Inputs on which `HttpUrl` throws (seven groups, `::`, one or more groups); `Url` rejects each. */
    val crash: List<String> = listOf(
        "http://[1:2:3:4:5:6:7::8]/",
        "http://[1:2:3:4:5:6:7::8:9]/",
        "http://[a:b:c:d:e:f:0::1]/",
        "http://[1:2:3:4:5:6:7::1.2.3.4]/",
        "http://[0:0:0:0:0:0:0::1]/",
        "http://[FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF::FFFF]/",
    )

    /** Every input of every list, for the mutation generator. */
    val allInputs: List<String> = (accept + reject + confusion).map { it.input } + crash

    /**
     * Makes an input printable for a failure message.
     *
     * @param text any input, control characters and lone surrogates included.
     * @return [text] with every character outside U+0020..U+007E written as `\uXXXX`.
     */
    fun visible(text: String): String = buildString {
        for (c in text) {
            val code = c.code
            if (code in 0x20..0x7E) append(c) else append("\\u").append(code.toString(16).padStart(4, '0'))
        }
    }
}
