package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * Level 2 - `RedirectHop`: statuses 299 and 400 refused, 300 and 399 accepted; value equality and
 * hash; `toString()` hides a query canary.
 */
class RedirectHopComponentTest {

    private val url = Url.parse("https://h/p?token=SECRET-CANARY").getOrThrow()

    @Test
    fun `the status must be in 300 to 399`() {
        assertEquals("Redirect status must be in 300..399", assertFailsWith<IllegalArgumentException> { RedirectHop(url, 299) }.message)
        assertFailsWith<IllegalArgumentException> { RedirectHop(url, 400) }
        assertEquals(300, RedirectHop(url, 300).status)
        assertEquals(399, RedirectHop(url, 399).status)
    }

    @Test
    fun `equality and hash are on url and status`() {
        assertEquals(RedirectHop(url, 301), RedirectHop(Url.parse(url.toString()).getOrThrow(), 301))
        assertEquals(RedirectHop(url, 301).hashCode(), RedirectHop(url, 301).hashCode())
        assertNotEquals(RedirectHop(url, 301), RedirectHop(url, 302))
        assertNotEquals(RedirectHop(url, 301), RedirectHop(Url.parse("https://h/q").getOrThrow(), 301))
        assertNotEquals<Any>(RedirectHop(url, 301), url)
    }

    @Test
    fun `toString uses the redacted URL`() {
        val hop = RedirectHop(url, 302)
        assertEquals("RedirectHop(https://h/p?..., 302)", hop.toString())
        assertFalse(hop.toString().contains("SECRET-CANARY"))
    }
}
