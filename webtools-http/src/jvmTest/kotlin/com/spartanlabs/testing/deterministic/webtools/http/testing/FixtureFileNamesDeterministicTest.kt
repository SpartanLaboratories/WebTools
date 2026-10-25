package com.spartanlabs.testing.deterministic.webtools.http.testing

import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.FixtureFileNames
import com.spartanlabs.webtools.http.testing.Redaction
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Level 4a - `FixtureFileNames` as input-to-output mappings: `slug`, the 8 hex against fixed SHA-256 vectors,
 * the generated name for a table of requests, the method part, group and name validation, key rotation,
 * query order, and `redactedQuery` rendering and sorting.
 */
class FixtureFileNamesDeterministicTest {

    private val redaction = Redaction()
    private val root: Path = Path.of("fixtures")

    private fun request(url: String, method: HttpMethod = HttpMethod.Get): HttpRequest = HttpRequest(method, Url.parse(url).getOrThrow())
    private fun name(url: String, method: HttpMethod = HttpMethod.Get): String =
        FixtureFileNames.path(root, "g", request(url, method), null, redaction).fileName.toString()

    @Test
    fun `slug lower-cases collapses runs trims cuts at sixty and defaults to root`() {
        assertEquals("api-players-1", FixtureFileNames.slug("/API/Players/1"))
        assertEquals("a-b-c", FixtureFileNames.slug("a!!b   c"))
        assertEquals("abc", FixtureFileNames.slug("---abc---"))
        assertEquals("root", FixtureFileNames.slug(""))
        assertEquals("root", FixtureFileNames.slug("/"))
        assertEquals("root", FixtureFileNames.slug("---"))
        assertEquals("caf-x", FixtureFileNames.slug("café/x"))   // U+00E9
        val long = "a".repeat(59) + "-" + "b".repeat(10)
        assertEquals("a".repeat(59), FixtureFileNames.slug(long))       // 60 cuts after the dash, which is then trimmed
        assertEquals(60, FixtureFileNames.slug("x".repeat(100)).length)
    }

    @Test
    fun `the eight hex digits are the first four bytes of SHA-256 over the request key`() {
        assertEquals("42951e7f", FixtureFileNames.hash(request("https://h/a"), redaction))                                  // sha256("GET /a?")
        assertEquals("65691731", FixtureFileNames.hash(request("https://h/api/players/1?limit=20&api_key=k"), redaction))   // sha256("GET /api/players/1?api_key=REDACTED&limit=20")
    }

    @Test
    fun `a request without a name gets method slug and hash and a named one gets its name`() {
        assertEquals("get-a-42951e7f.http", name("https://h/a"))
        assertEquals("get-api-players-1-65691731.http", name("https://h/api/players/1?limit=20&api_key=k"))
        assertEquals("post-root-" + FixtureFileNames.hash(request("https://h/", HttpMethod.Post), redaction) + ".http", name("https://h/", HttpMethod.Post))
        assertEquals(root.resolve("a").resolve("b").resolve("named.http"), FixtureFileNames.path(root, "a/b", request("https://h/x"), "named", redaction))
    }

    @Test
    fun `the method part is slugged and cut at sixteen`() {
        // A generated name is <method>-<slug>-<hash>.http; the surgery below strips "-<hash>" and then "-a" (the
        // slug of path /a) to isolate the method part.
        assertEquals("get", name("https://h/a").substringBefore('-'))
        assertEquals("m-search", name("https://h/a", HttpMethod.of("M-SEARCH").getOrThrow()).substringBeforeLast('-').substringBeforeLast('-'))
        assertEquals("a-b", name("https://h/a", HttpMethod.of("A*B").getOrThrow()).substringBeforeLast('-').substringBeforeLast('-'))
        val long = HttpMethod.of("X".repeat(30)).getOrThrow()
        assertEquals("x".repeat(16) + "-a-", name("https://h/a", long).substringBefore("-a-") + "-a-")
    }

    @Test
    fun `a group is one or more slash-separated token segments and never dot dot-dot or a trailing dot`() {
        listOf("opendota", "a/b", "v1.2", "a_b-c").forEach { FixtureFileNames.requireGroup(it) }
        listOf("", ".", "..", "a//b", "a/../b", "a b", "a\\b", "a.", "a/b.", "/a", "a/").forEach { group ->
            assertFailsWith<IllegalArgumentException>(group) { FixtureFileNames.requireGroup(group) }
        }
    }

    @Test
    fun `a nameFor result is one token segment and never dot dot-dot or a trailing dot`() {
        listOf("player-1", "v1.2", "A_b").forEach { FixtureFileNames.requireName(it) }
        listOf("", ".", "..", "a/b", "a b", "a\\b", "a.", "café").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { FixtureFileNames.requireName(name) }
        }
    }

    @Test
    fun `key rotation and query order give the same name and another query another`() {
        val one = name("https://h/p?api_key=first-key&limit=20")
        assertEquals(one, name("https://h/p?limit=20&api_key=second-key"))
        assertNotEquals(one, name("https://h/p?limit=21&api_key=second-key"))
        assertNotEquals(one, name("https://h/p?limit=20"))
        assertEquals(name("https://h/p"), name("https://h/p?"))   // the key always holds the `?`: no query and a bare `?` share a file
    }

    @Test
    fun `redactedQuery renders and sorts`() {
        assertNull(FixtureFileNames.redactedQuery(null, redaction))
        assertEquals("", FixtureFileNames.redactedQuery("", redaction))
        assertEquals("a=1&b=2", FixtureFileNames.redactedQuery("b=2&a=1", redaction))
        assertEquals("a=0&a=1", FixtureFileNames.redactedQuery("a=1&a=0", redaction))
        assertEquals("api_key=REDACTED&code=xyz&flag", FixtureFileNames.redactedQuery("flag&code=xyz&api_key=abc", redaction))
        assertEquals("api%5Fkey=REDACTED", FixtureFileNames.redactedQuery("api%5Fkey=abc", redaction))
        assertEquals("sort_key=name", FixtureFileNames.redactedQuery("sort_key=name", Redaction(keepQueryParams = setOf("sort_key"))))
        assertEquals("uid=HIDDEN", FixtureFileNames.redactedQuery("uid=42", Redaction(extraSensitiveQueryParams = setOf("uid"), placeholder = "HIDDEN")))
        assertEquals("request: GET https://h/p?api_key=REDACTED&limit=20", FixtureFileNames.comment(request("https://h/p?limit=20&api_key=k"), redaction))
        assertEquals("request: GET http://[::1]:8080/p", FixtureFileNames.comment(request("http://[::1]:8080/p"), redaction))
        assertEquals("request: GET https://h/p?", FixtureFileNames.comment(request("https://h/p?"), redaction))
    }
}
