package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.RedirectHop
import com.spartanlabs.webtools.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Level 4a - `HttpResponse` statuses `99, 0, -1, 600, 999, 1000` refused and `100, 101, 199, 200, 204,
 * 299, 304, 399, 404, 499, 500, 599` accepted; `RedirectHop` `299, 400` refused and `300, 301, 302,
 * 303, 307, 308, 399` accepted.
 */
class StatusRangeDeterministicTest {

    private val url = Url.parse("http://h/").getOrThrow()

    @Test
    fun `a response status outside 100 to 599 is refused and one inside accepted`() {
        listOf(99, 0, -1, 600, 999, 1000).forEach { status ->
            assertEquals("Status code must be in 100..599", assertFailsWith<IllegalArgumentException> { HttpResponse(status, url) }.message, "$status")
        }
        listOf(100, 101, 199, 200, 204, 299, 304, 399, 404, 499, 500, 599).forEach { status ->
            assertEquals(status, HttpResponse(status, url).status)
        }
    }

    @Test
    fun `a redirect status outside 300 to 399 is refused and one inside accepted`() {
        listOf(299, 400).forEach { status ->
            assertEquals("Redirect status must be in 300..399", assertFailsWith<IllegalArgumentException> { RedirectHop(url, status) }.message, "$status")
        }
        listOf(300, 301, 302, 303, 307, 308, 399).forEach { status ->
            assertEquals(status, RedirectHop(url, status).status)
        }
    }
}
