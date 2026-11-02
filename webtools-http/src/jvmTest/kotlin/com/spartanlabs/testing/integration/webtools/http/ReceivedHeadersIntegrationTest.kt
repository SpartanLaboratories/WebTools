package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Level 3 - received headers on the wire: a NUL in a value arrives as SP; a UTF-8 value is delivered; duplicate `Set-Cookie` lines keep
 * their order; `Content-Length` and `Content-Type` are readable; an obs-fold continuation (an empty-name entry on the wire) is dropped while
 * its neighbours stay.
 */
class ReceivedHeadersIntegrationTest {

    private lateinit var fleet: LoopbackFleet
    private lateinit var server: MockWebServer
    private lateinit var transport: OkHttpTransport

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
        server = fleet.server()
        transport = fleet.track(LoopbackTransports.untrusted("agent"))
    }

    @AfterTest
    fun stop() = fleet.close()

    @Test
    fun `values are sanitised delivered in order and the common entries are readable`() {
        server.enqueue(
            MockResponse.Builder()
                .addHeaderLenient("X-Nul", "a\u0000b")
                .addHeaderLenient("X-Note", "café")
                .addHeader("Set-Cookie", "a=1")
                .addHeader("Set-Cookie", "b=2")
                .addHeader("Content-Type", "text/plain")
                .body("hello")
                .build(),
        )
        val response = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))).getOrThrow()
        assertEquals("a b", response.headers["X-Nul"])
        assertEquals("café", response.headers["X-Note"])
        assertEquals(listOf("a=1", "b=2"), response.headers.getAll("Set-Cookie"))
        assertEquals("5", response.headers["Content-Length"])
        assertEquals("text/plain", response.headers["Content-Type"])
    }

    @Test
    fun `an obs-fold continuation is dropped and its neighbours stay`() {
        server.enqueue(MockResponse.Builder().addHeader("X-Before", "1").addHeaderLenient("X-Fold", "a\r\n b").addHeader("X-After", "2").body("x").build())
        val response = fleet.fetch(transport, fleet.get(server.webtoolsUrl("/"))).getOrThrow()
        assertEquals("1", response.headers["X-Before"])
        assertEquals("a", response.headers["X-Fold"])
        assertEquals("2", response.headers["X-After"])
        assertFalse(response.headers.entries().any { it.name.isEmpty() })
    }
}
