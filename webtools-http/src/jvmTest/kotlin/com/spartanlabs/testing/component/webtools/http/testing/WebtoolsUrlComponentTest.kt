package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.webtools.http.testing.loopbackUrlText
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import mockwebserver3.MockWebServer
import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Level 2 - `MockWebServer.webtoolsUrl(path)`: an unstarted server is refused with an `IllegalStateException`; a path that does not start
 * with `/` is refused with an `IllegalArgumentException` before the server is consulted; a started `127.0.0.1` server's URL is the text
 * `loopbackUrlText` writes for its address and port. The non-loopback refusal has no deterministic set-up on every host and is not tested
 * here: the text is covered by the Level 4a `LoopbackUrlTextDeterministicTest`, and the refusal by the fixture's own `check`.
 */
class WebtoolsUrlComponentTest {

    private val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val servers = mutableListOf<MockWebServer>()

    private fun unstarted(): MockWebServer = MockWebServer().also { servers += it }

    private fun started(): MockWebServer = unstarted().apply { start(loopback, 0) }

    @AfterTest
    fun close() {
        servers.forEach { it.close() }
    }

    @Test
    fun `an unstarted server is refused`() {
        assertFailsWith<IllegalStateException> { unstarted().webtoolsUrl() }
    }

    @Test
    fun `a path that does not start with a slash is refused`() {
        val server = started()
        listOf("x", "").forEach { path ->
            val failure = assertFailsWith<IllegalArgumentException>(path) { server.webtoolsUrl(path) }
            assertEquals("path must start with '/'", failure.message)
        }
    }

    @Test
    fun `a started loopback server's URL is the loopbackUrlText of its address and port`() {
        val server = started()
        val address = server.socketAddress.address
        assertEquals(loopbackUrlText(address.hostAddress, server.port, "/"), server.webtoolsUrl().toString())
        assertEquals(loopbackUrlText(address.hostAddress, server.port, "/a?b=1"), server.webtoolsUrl("/a?b=1").toString())
        assertEquals("http://127.0.0.1:${server.port}/a?b=1", server.webtoolsUrl("/a?b=1").toString())
    }
}
