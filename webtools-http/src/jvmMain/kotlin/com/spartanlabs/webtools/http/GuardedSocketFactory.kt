package com.spartanlabs.webtools.http

import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import javax.net.SocketFactory

/** The message of the `SocketException` the four connecting `createSocket` overloads throw; OkHttp 5.5.0 never calls them. */
internal const val NOT_A_GUARDED_ENTRY_POINT_MESSAGE: String =
    "This socket factory only creates unconnected sockets; connect through the guarded socket."

/**
 * A [SocketFactory] whose sockets judge every connect. OkHttp 5.5.0 calls only `createSocket()`; the four abstract overloads
 * refuse, so a future OkHttp that calls one fails loudly instead of connecting unjudged. Not an `SSLSocketFactory` (OkHttp
 * rejects one), and it has no delegate: it must create the socket itself to choose the socket implementation.
 *
 * @property guard the guard each created [GuardedSocket] judges its connects with.
 */
internal class GuardedSocketFactory(private val guard: UrlGuard) : SocketFactory() {
    /** @return a new, unconnected [GuardedSocket]. */
    override fun createSocket(): Socket = GuardedSocket(guard)

    /** Refused: a connecting overload OkHttp 5.5.0 never calls. @throws SocketException always, with [NOT_A_GUARDED_ENTRY_POINT_MESSAGE]. */
    override fun createSocket(host: String?, port: Int): Socket = refuse()

    /** Refused: a connecting overload OkHttp 5.5.0 never calls. @throws SocketException always, with [NOT_A_GUARDED_ENTRY_POINT_MESSAGE]. */
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = refuse()

    /** Refused: a connecting overload OkHttp 5.5.0 never calls. @throws SocketException always, with [NOT_A_GUARDED_ENTRY_POINT_MESSAGE]. */
    override fun createSocket(host: InetAddress?, port: Int): Socket = refuse()

    /** Refused: a connecting overload OkHttp 5.5.0 never calls. @throws SocketException always, with [NOT_A_GUARDED_ENTRY_POINT_MESSAGE]. */
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = refuse()

    private fun refuse(): Nothing = throw SocketException(NOT_A_GUARDED_ENTRY_POINT_MESSAGE)

    override fun toString(): String = "GuardedSocketFactory"
}
