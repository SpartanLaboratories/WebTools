package com.spartanlabs.testing.deterministic.webtools.http.testing

import com.spartanlabs.webtools.http.testing.SensitiveNames
import com.spartanlabs.webtools.http.testing.asciiLowercase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level 4a - `SensitiveNames` as input-to-output mappings: the sensitive and the ordinary query names, the
 * keep and extra lists, the header names, the tokeniser's boundary cases and the ASCII-only folding.
 */
class SensitiveNamesDeterministicTest {

    private val sensitive = listOf(
        "api_key", "apiKey", "apikey", "KEY", "key", "access_token", "accessToken", "accesstoken", "client_secret",
        "clientSecret", "X-Amz-Signature", "X-Amz-Credential", "session_id", "sessionid", "x-api-key", "token",
        "refresh_token", "id_token", "password", "passwd", "pwd", "auth", "jwt",
    )
    private val ordinary = listOf(
        "keyword", "limit", "appid", "steamid", "lat", "lon", "design", "signal", "monkey", "country_code", "code",
        "q", "page", "per_page", "format", "language", "match_id", "account_id", "authenticate",
    )

    @Test
    fun `the twenty-three credential names are sensitive`() {
        assertEquals(23, sensitive.size)
        sensitive.forEach { assertTrue(SensitiveNames.isSensitiveName(it), it) }
    }

    @Test
    fun `the nineteen ordinary names are not sensitive`() {
        assertEquals(19, ordinary.size)
        ordinary.forEach { assertFalse(SensitiveNames.isSensitiveName(it), it) }
    }

    @Test
    fun `sort_key is sensitive until the keep list restores it and the extra list adds a name`() {
        assertTrue(SensitiveNames.isSensitiveQuery("sort_key", emptySet(), emptySet()))
        assertFalse(SensitiveNames.isSensitiveQuery("sort_key", emptySet(), setOf("sort_key")))
        assertFalse(SensitiveNames.isSensitiveQuery("SORT_KEY", emptySet(), setOf("sort_key")))
        assertTrue(SensitiveNames.isSensitiveQuery("uid", setOf("uid"), emptySet()))
        assertFalse(SensitiveNames.isSensitiveQuery("uid", emptySet(), emptySet()))
        assertFalse(SensitiveNames.isSensitiveQuery("uid", setOf("uid"), setOf("uid")))
    }

    @Test
    fun `digits split from letters so key2 and api_key2 are sensitive`() {
        assertTrue(SensitiveNames.isSensitiveName("key2"))
        assertTrue(SensitiveNames.isSensitiveName("api_key2"))
        assertEquals(listOf("key", "2"), SensitiveNames.tokens("key2"))
        assertEquals(listOf("api", "key", "2"), SensitiveNames.tokens("api_key2"))
    }

    @Test
    fun `the tokeniser splits on separators case changes acronym ends and digit boundaries`() {
        assertEquals(listOf("api", "key"), SensitiveNames.tokens("apiKey"))
        assertEquals(listOf("api", "key"), SensitiveNames.tokens("APIKey"))
        assertEquals(listOf("x", "amz", "signature"), SensitiveNames.tokens("X-Amz-Signature"))
        assertEquals(listOf("e", "tag"), SensitiveNames.tokens("ETag"))
        assertEquals(listOf("x", "rate", "limit", "remaining"), SensitiveNames.tokens("X-RateLimit-Remaining"))
        assertEquals(listOf("abc", "123", "def"), SensitiveNames.tokens("abc123def"))
        assertEquals(listOf("html", "5"), SensitiveNames.tokens("HTML5"))
        assertEquals(emptyList(), SensitiveNames.tokens(""))
        assertEquals(emptyList(), SensitiveNames.tokens("-_."))
        assertEquals(listOf("caf", "key"), SensitiveNames.tokens("caféKey"))   // U+00E9 is a separator
    }

    @Test
    fun `credential header names are sensitive and ordinary header names are not`() {
        listOf("X-Api-Key", "X-Session-Id", "Authorization", "Proxy-Authorization", "X-Auth-Token").forEach { assertTrue(SensitiveNames.isSensitiveName(it), it) }
        listOf("ETag", "X-RateLimit-Remaining", "Content-Type", "WWW-Authenticate", "Cache-Control", "Retry-After").forEach { assertFalse(SensitiveNames.isSensitiveName(it), it) }
    }

    @Test
    fun `asciiLowercase folds A to Z only`() {
        assertEquals("abc-xyz_09", asciiLowercase("ABC-xyz_09"))
        assertEquals("Kİ", asciiLowercase("Kİ"))   // U+212A KELVIN SIGN, U+0130 LATIN CAPITAL LETTER I WITH DOT ABOVE
        assertEquals("", asciiLowercase(""))
    }
}
