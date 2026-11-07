package com.spartanlabs.testing.support.webtools.feeds

/** The link-rule vectors: the RFC 3986 section 5.4 tables, the absolute rows and the rows that must give null. */
object LinkVectors {

    /** The base of the RFC 3986 section 5.4 examples. */
    const val RFC_BASE: String = "http://a/b/c/d;p?q"

    /** The base the module's own rows resolve against. */
    const val MODULE_BASE: String = "https://example.org/blog/index.xml?x=1#frag"

    /** RFC 3986 section 5.4.1, normal examples: reference to expected result; `null` where the rule refuses. */
    val normal: List<Pair<String, String?>> = listOf(
        "g:h" to null,
        "g" to "http://a/b/c/g",
        "./g" to "http://a/b/c/g",
        "g/" to "http://a/b/c/g/",
        "/g" to "http://a/g",
        "//g" to "http://g",
        "?y" to "http://a/b/c/d;p?y",
        "g?y" to "http://a/b/c/g?y",
        "#s" to "http://a/b/c/d;p?q#s",
        "g#s" to "http://a/b/c/g#s",
        "g?y#s" to "http://a/b/c/g?y#s",
        ";x" to "http://a/b/c/;x",
        "g;x" to "http://a/b/c/g;x",
        "g;x?y#s" to "http://a/b/c/g;x?y#s",
        "" to null,
        "." to "http://a/b/c/",
        "./" to "http://a/b/c/",
        ".." to "http://a/b/",
        "../" to "http://a/b/",
        "../g" to "http://a/b/g",
        "../.." to "http://a/",
        "../../" to "http://a/",
        "../../g" to "http://a/g",
    )

    /** RFC 3986 section 5.4.2, abnormal examples (strict): reference to expected result; `null` where the rule refuses. */
    val abnormal: List<Pair<String, String?>> = listOf(
        "../../../g" to "http://a/g",
        "../../../../g" to "http://a/g",
        "/./g" to "http://a/g",
        "/../g" to "http://a/g",
        "g." to "http://a/b/c/g.",
        ".g" to "http://a/b/c/.g",
        "g.." to "http://a/b/c/g..",
        "..g" to "http://a/b/c/..g",
        "./../g" to "http://a/b/g",
        "./g/." to "http://a/b/c/g/",
        "g/./h" to "http://a/b/c/g/h",
        "g/../h" to "http://a/b/c/h",
        "g;x=1/./y" to "http://a/b/c/g;x=1/y",
        "g;x=1/../y" to "http://a/b/c/y",
        "g?y/./x" to "http://a/b/c/g?y/./x",
        "g?y/../x" to "http://a/b/c/g?y/../x",
        "g#s/./x" to "http://a/b/c/g#s/./x",
        "g#s/../x" to "http://a/b/c/g#s/../x",
        "http:g" to null,
    )

    /** Absolute inputs that are kept: input to the expected output (everything but the scheme's case as written). */
    val absoluteKept: List<Pair<String, String>> = listOf(
        "https://example.org/a#top" to "https://example.org/a#top",
        "  HTTPS://Example.ORG/A  " to "https://Example.ORG/A",
        "http://x.test" to "http://x.test",
        "http://x.test/%2f/%2F" to "http://x.test/%2f/%2F",
        "http://münchen.example/ü?ö#ä" to "http://münchen.example/ü?ö#ä",
        "http://[2001:db8::1]:8080/x" to "http://[2001:db8::1]:8080/x",
        "http://x.test:65535/" to "http://x.test:65535/",
    )

    /** Inputs that give null whatever the base: unsafe schemes, user-info, forbidden characters and malformed authorities. */
    val refused: List<String> = listOf(
        "javascript:alert(1)", "JaVaScRiPt:alert(1)", " javascript:alert(1)", "java\nscript:alert(1)",
        "data:text/html,x", "file:///etc/passwd", "mailto:a@b.test", "ftp://x.test/", "irc://x.test/", "vbscript:x",
        "http://user:pw@x.test/x", "http://x.test@evil.test/",
        "http:\\\\x.test\\x", "http://x.test/a b", "http://x.test/a\tb", "ht\ntp://x.test/", "http://x.test/\u0085",
        "localhost:8080/x", ":foo", "http://", "https:///x", "http://:80/x", "http://x y/", "http://a%41.test/", "http://[::1/x",
        "http://exa<mple.test/", "http://x.test:65536/", "http://x.test:80a/",
    )
}
