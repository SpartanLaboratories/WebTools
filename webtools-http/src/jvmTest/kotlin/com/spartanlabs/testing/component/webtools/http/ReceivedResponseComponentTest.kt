package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.fakeResponse
import com.spartanlabs.webtools.http.headerPairs
import okhttp3.Headers.Companion.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 2 - the received response over an `okhttp3.Response`: `headerPairs` keeps order and duplicates.
 */
class ReceivedResponseComponentTest {

    @Test
    fun `headerPairs keeps order and duplicates`() {
        val response = fakeResponse(200, headersOf("X-A", "1", "X-B", "2", "X-A", "1"))
        assertEquals(listOf("X-A" to "1", "X-B" to "2", "X-A" to "1"), response.headerPairs())
    }
}
