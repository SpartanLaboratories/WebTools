package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * One datagram received on the common socket, already decoded.
 *
 * @property origin the post-NAT source address and port the datagram arrived from
 * @property text the trimmed UTF-8 text of the datagram body
 */
internal data class Inbound(val origin: InetSocketAddress, val text: String)

/**
 * Wraps the single [DatagramSocket] [MultiConnectionUDPServer] uses for **all**
 * client traffic: the `Iam` handshake, the `REGISTERED` reply, `pushToAll`
 * broadcasts, per-client bidirectional data, and keepalives.
 *
 * Boundary-Ring notes:
 * - A single UDP socket carries every client. Datagrams are demultiplexed by
 *   their source [InetSocketAddress] (the client's post-NAT endpoint).
 * - Every outbound datagram is addressed to a caller-supplied post-NAT endpoint,
 *   never to anything in a payload, so it rides the NAT mapping the client opened.
 * - The JDK permits a concurrent [DatagramSocket.send] while a
 *   [DatagramSocket.receive] is in progress, and each [send] is one atomic
 *   datagram. [receive] is expected to be called from a single listener thread;
 *   [send] is safe from any thread.
 *
 * ### Construction side effects
 * Instantiation **binds the OS UDP port [port] immediately**.
 *
 * @param port the local UDP port to bind
 * @throws java.net.SocketException (typically [java.net.BindException]) if [port]
 * is unavailable
 */
internal class CommonChannel(port: Int) {
    private val socket = DatagramSocket(port)

    /** The local port the socket is bound to. */
    val localPort: Int get() = socket.localPort

    /**
     * Blocks until a datagram arrives, then decodes it.
     * @param buffer a caller-owned, reused receive buffer
     * @return the decoded [Inbound], or [Result.failure] on a
     * [java.net.SocketException] (socket closed) or a decode failure
     */
    fun receive(buffer: ByteArray): Result<Inbound> = runCatching {
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        val origin = InetSocketAddress(packet.address, packet.port)
        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
        Inbound(origin, text)
    }.onFailure { log.trace("receive() failed: {}", it.message) }

    /**
     * Sends [bytes] as one atomic datagram to [to]. Safe to call concurrently
     * with [receive].
     * @param bytes the datagram payload
     * @param to the client endpoint to address
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    fun send(bytes: ByteArray, to: InetSocketAddress): Result<Unit> = runCatching {
        socket.send(DatagramPacket(bytes, bytes.size, to))
    }.onFailure { log.error("Could not send datagram to {}", to, it) }

    /**
     * Closes the underlying socket, releasing the port.
     * @return [Result.success] if the socket closed, or the failure that prevented it
     */
    fun closeResult(): Result<Unit> = runCatching { socket.close() }
        .onFailure { log.error("Could not close the common socket", it) }

    private companion object {
        private val log = LoggerFactory.getLogger(CommonChannel::class.java)
    }
}
