package com.spartanlabs.testing.deterministic.webtools.feeds

import com.spartanlabs.webtools.feeds.StatusVerdict
import com.spartanlabs.webtools.feeds.statusVerdictOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 4a - the status verdict table: statuses 100 to 599 map to `Parse` only for `200` and `203`; `304` is `NotModified` only
 * when the request was conditional, and `Upstream` otherwise.
 */
class ResponseVerdictDeterministicTest {

    @Test
    fun `statuses 100 to 599 map to Parse only for 200 and 203`() {
        for (status in 100..599) {
            val expected = when (status) {
                200, 203 -> StatusVerdict.Parse
                304 -> StatusVerdict.NotModified
                else -> StatusVerdict.Upstream
            }
            assertEquals(expected, statusVerdictOf(status, conditional = true), "status $status")
        }
        for (status in listOf(201, 202, 204, 205, 206, 226, 300, 301, 302, 307, 308, 400, 404, 410, 429, 500, 503)) {
            assertEquals(StatusVerdict.Upstream, statusVerdictOf(status, conditional = false), "status $status")
        }
    }

    @Test
    fun `304 is NotModified only when the request was conditional`() {
        assertEquals(StatusVerdict.NotModified, statusVerdictOf(304, conditional = true))
        assertEquals(StatusVerdict.Upstream, statusVerdictOf(304, conditional = false))
        assertEquals(StatusVerdict.Parse, statusVerdictOf(200, conditional = false))
        assertEquals(StatusVerdict.Parse, statusVerdictOf(203, conditional = false))
    }
}
