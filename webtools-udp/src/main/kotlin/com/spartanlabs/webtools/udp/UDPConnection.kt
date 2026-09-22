package com.spartanlabs.webtools.udp

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * The production [Connection]: a socket-free handle to one multiplexed client of a
 * [MultiConnectionUDPServer].
 *
 * It owns no socket and no thread of its own. [push] and [keepAlive] address
 * [peer] over the server's single shared socket; [actuate] registers this
 * connection's message handler in the server's registry, while [terminate] now
 * fully deregisters it - not just unregisters the handler. Every fallible
 * operation returns a [Result]. A scheduled keepalive ([startKeepAlive]) runs on
 * the server's shared `mcups-keepalive` thread - still not one owned here - and is
 * cancelled by [terminate]. An opt-in link-quality probe ([startProbe]) likewise
 * runs on the server's shared `mcups-probe` thread and is cancelled by [terminate].
 *
 * Instances are minted only by [MultiConnectionUDPServer]'s internal factory -
 * the constructor is `internal`. Standalone bidirectional-socket users take
 * [UDPSendReceiveServer] directly.
 *
 * @property name a human-readable identifier for this connection, typically
 * supplied by the client during the handshake
 * @property peer the client's post-NAT endpoint, learned from its `Iam` datagram;
 * every datagram to this client is addressed here
 */
class UDPConnection internal constructor(
    override val name: String,
    override val peer: InetSocketAddress,
    private val clientChannel: ClientChannel,
) : Connection {

    init {
        log.debug("Created UDPConnection '{}' for {}", name, peer)
    }

    override fun channel(mode: DeliveryMode): UdpChannel = when (mode) {
        DeliveryMode.UNRELIABLE -> unreliableChannel
        DeliveryMode.RELIABLE_ORDERED -> reliableChannel
    }

    // `by lazy` keeps design §9's "no allocation until a reliable channel is opened" literally
    // true, and makes the handle stable across calls so a consumer may hold it.
    private val unreliableChannel: UdpChannel by lazy { UnreliableConnectionChannel(this) }
    private val reliableChannel: UdpChannel by lazy { ReliableConnectionChannel(clientChannel, peer) }

    @Deprecated(
        "Use channel(DeliveryMode.UNRELIABLE).actuate(...) - see the 2.0 channel API",
        ReplaceWith("channel(DeliveryMode.UNRELIABLE).actuate(onMessage)", "com.spartanlabs.webtools.udp.DeliveryMode"),
    )
    override fun actuate(onMessage: (message: String) -> Unit): Result<Unit> =
        runCatching { clientChannel.bind(peer, onMessage) }
            .onFailure { log.error("Could not actuate connection '{}'", name, it) }

    @Deprecated(
        "Use channel(DeliveryMode.UNRELIABLE).actuateBytes(...) - see the 2.0 channel API",
        ReplaceWith(
            "channel(DeliveryMode.UNRELIABLE).actuateBytes(onMessage)",
            "com.spartanlabs.webtools.udp.DeliveryMode",
        ),
    )
    override fun actuateBytes(onMessage: (ByteArray) -> Unit): Result<Unit> =
        runCatching { clientChannel.bindBytes(peer, onMessage) }
            .onFailure { log.error("Could not actuate (bytes) connection '{}'", name, it) }

    override fun terminate(): Result<Unit> =
        runCatching { clientChannel.deregister(peer) }
            .onFailure { log.error("Could not terminate connection '{}'", name, it) }

    @Deprecated(
        "Use channel(DeliveryMode.UNRELIABLE).send(...) - see the 2.0 channel API",
        ReplaceWith("channel(DeliveryMode.UNRELIABLE).send(message)", "com.spartanlabs.webtools.udp.DeliveryMode"),
    )
    @Suppress("DEPRECATION") // the body calls the now-deprecated push(bytes) below
    override fun push(message: String): Result<Unit> =
        push(message.toByteArray(Charsets.UTF_8))

    /**
     * Frames [bytes] as an `0x90` unreliable datagram (`[0x90][channel][payload]`)
     * and sends it to [peer]. Any payload bytes are delivered to the peer intact -
     * there is no reserved first byte.
     */
    @Deprecated(
        "Use channel(DeliveryMode.UNRELIABLE).send(...) - see the 2.0 channel API",
        ReplaceWith("channel(DeliveryMode.UNRELIABLE).send(bytes)", "com.spartanlabs.webtools.udp.DeliveryMode"),
    )
    override fun push(bytes: ByteArray): Result<Unit> =
        clientChannel.send(TransportWireFormat.unreliableDatagram(bytes), peer)
            .onFailure { log.error("Connection '{}' could not push a message", name, it) }

    /** Sends a bare `0x80` keepalive datagram to [peer]. */
    override fun keepAlive(): Result<Unit> =
        clientChannel.send(KEEPALIVE_DATAGRAM, peer)
            .onFailure { log.error("Connection '{}' could not send a keepalive", name, it) }

    /**
     * Unlike the [Connection] default, this production implementation **does**
     * support a scheduled keepalive: it delegates to the server's shared
     * `mcups-keepalive` executor via [ClientChannel], which sends an idle-aware `KA`
     * to [peer] on the given cadence. A non-positive [intervalMillis] surfaces as
     * the [ClientChannel]'s [IllegalArgumentException] failure. The schedule is
     * cancelled by [stopKeepAlive] and by [terminate].
     */
    override fun startKeepAlive(intervalMillis: Long): Result<Unit> =
        clientChannel.scheduleKeepAlive(peer, intervalMillis)
            .onFailure { log.error("Connection '{}' could not start a scheduled keepalive", name, it) }

    /**
     * Unlike the [Connection] default no-op, this production implementation cancels
     * the keepalive armed by [startKeepAlive] on the server's shared
     * `mcups-keepalive` executor via [ClientChannel]. Idempotent; [terminate] also
     * does this.
     */
    override fun stopKeepAlive(): Result<Unit> =
        clientChannel.cancelKeepAlive(peer)
            .onFailure { log.error("Connection '{}' could not stop its scheduled keepalive", name, it) }

    /**
     * Unlike the [Connection] default, this production implementation **does**
     * support a link-quality probe: it delegates to the server's shared
     * `mcups-probe` executor via [ClientChannel], which sends a periodic `PING` to
     * [peer] and folds each `PONG` into a per-connection estimator. A non-positive
     * [intervalMillis] or an unregistered [peer] surfaces as the [ClientChannel]'s
     * failure. The schedule is cancelled by [stopProbe] and by [terminate].
     */
    override fun startProbe(intervalMillis: Long): Result<Unit> =
        clientChannel.scheduleProbe(peer, intervalMillis)
            .onFailure { log.error("Connection '{}' could not start a link-quality probe", name, it) }

    /**
     * Unlike the [Connection] default no-op, this production implementation cancels
     * the probe armed by [startProbe] on the server's shared `mcups-probe`
     * executor via [ClientChannel]. Idempotent; [terminate] also does this.
     */
    override fun stopProbe(): Result<Unit> =
        clientChannel.cancelProbe(peer)
            .onFailure { log.error("Connection '{}' could not stop its probe", name, it) }

    /** The latest link-quality snapshot for this connection, delegated to [ClientChannel]. */
    override fun linkQuality(): LinkQuality? = clientChannel.linkQualityOf(peer)

    private companion object {
        private val log = LoggerFactory.getLogger(UDPConnection::class.java)
        private val KEEPALIVE_DATAGRAM = TransportWireFormat.keepaliveDatagram()
    }
}

/**
 * [UdpChannel] over one [UDPConnection]'s reliable-ordered plane; all work
 * delegates to [ClientChannel].
 */
internal class ReliableConnectionChannel(
    private val clientChannel: ClientChannel,
    private val peer: InetSocketAddress,
) : UdpChannel {
    override val mode = DeliveryMode.RELIABLE_ORDERED

    override fun send(bytes: ByteArray): Result<Unit> = clientChannel.sendReliable(peer, bytes)

    override fun actuateBytes(onMessage: (ByteArray) -> Unit): Result<Unit> =
        clientChannel.bindReliable(peer, onMessage)
}
