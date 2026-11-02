package com.spartanlabs.testing.integration.webtools.http

import com.spartanlabs.testing.support.webtools.http.LoopbackFleet
import com.spartanlabs.testing.support.webtools.http.TlsFixtures
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import mockwebserver3.MockResponse
import java.io.IOException
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 3 - `TlsFailed` on the wire: the HTTPS server's self-signed certificate against a transport with the system trust gives `TlsFailed`
 * with no cause and the server records no request; a raw listener that closes the socket on accept, hit by an `https` URL, ends at once with
 * `TlsFailed` or `Io`, by the JDK's exception for the terminated handshake (an `SSLHandshakeException` or a plain `SocketException`; the
 * latter on this Windows host), and keeps no cause. Never a plain server that stays silent: a TLS hello to it would hang until the timeout.
 */
class TlsFailureIntegrationTest {

    private lateinit var fleet: LoopbackFleet

    @BeforeTest
    fun start() {
        fleet = LoopbackFleet()
    }

    @AfterTest
    fun stop() = fleet.close()

    @Test
    fun `an untrusted certificate is TlsFailed with no cause and no request`() {
        val secure = TlsFixtures.httpsServer(fleet)
        secure.enqueue(MockResponse(body = "never"))
        val transport = fleet.track(LoopbackTransports.untrusted("agent"))
        val failure = assertIs<TransportFailure.TlsFailed>(fleet.fetch(transport, fleet.get(url("https://127.0.0.1:${secure.port}/"))).exceptionOrNull())
        assertNull(failure.cause)
        assertEquals(0, secure.requestCount)
    }

    @Test
    fun `a server that closes the socket on accept fails at once with TlsFailed or Io`() {
        // A raw listener, not a MockWebServer: it accepts and closes at once, so the client's TLS hello meets a closed peer.
        val listener = ServerSocket(0, 50, fleet.loopback)
        val acceptor = Thread {
            try {
                while (true) listener.accept().close()
            } catch (_: IOException) {
                // the listener was closed
            }
        }.apply { isDaemon = true; start() }
        try {
            val transport = fleet.track(LoopbackTransports.untrusted("agent"))
            val failure = assertIs<TransportFailure>(fleet.fetch(transport, fleet.get(url("https://127.0.0.1:${listener.localPort}/"))).exceptionOrNull())
            // The JDK's exception for a hello that meets a closed peer differs by platform: an SSLHandshakeException ("Remote host terminated
            // the handshake") maps to TlsFailed; a plain SocketException (Windows: "An established connection was aborted by the software in
            // your host machine", measured here) maps to Io. Either way the call ends at once, with no cause kept.
            assertTrue(failure is TransportFailure.TlsFailed || failure is TransportFailure.Io, failure.toString())
            assertNull(failure.cause)
        } finally {
            listener.close()
            acceptor.join(2000)
        }
    }
}
