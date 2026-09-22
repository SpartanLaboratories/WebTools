package com.spartanlabs.testing.support.webtools.udp

import com.spartanlabs.webtools.udp.ClientChannel
import com.spartanlabs.webtools.udp.LinkQuality
import com.spartanlabs.webtools.udp.UdpChannel
import java.net.InetSocketAddress

/**
 * A socket-free [ClientChannel] test fixture. Records every [send] (as the decoded
 * UTF-8 string plus its target, and separately as the raw bytes), every [bind] /
 * [bindBytes] / [deregister] / [sendReliable] / [bindReliable], and lets a test
 * invoke a bound handler directly. Backs the socket-free
 * [com.spartanlabs.webtools.udp.UDPConnection] tests.
 */
internal class FakeClientChannel(
    private val sendResult: Result<Unit> = Result.success(Unit),
    private val scheduleKeepAliveResult: Result<Unit> = Result.success(Unit),
    private val cancelKeepAliveResult: Result<Unit> = Result.success(Unit),
    private val scheduleProbeResult: Result<Unit> = Result.success(Unit),
    private val cancelProbeResult: Result<Unit> = Result.success(Unit),
    private val sendReliableResult: Result<Unit> = Result.success(Unit),
    private val bindReliableResult: Result<Unit> = Result.success(Unit),
    override val reliableMaxMessageBytes: Int = UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
    var linkQuality: LinkQuality? = null,
) : ClientChannel {

    data class Sent(val text: String, val to: InetSocketAddress)

    /** Every [scheduleKeepAlive] call, as `(peer, intervalMillis)`, in order. */
    val keepAliveSchedules = mutableListOf<Pair<InetSocketAddress, Long>>()

    /** Every [cancelKeepAlive] call's peer, in order. */
    val keepAliveCancels = mutableListOf<InetSocketAddress>()

    /** Every [scheduleProbe] call, as `(peer, intervalMillis)`, in order. */
    val probeSchedules = mutableListOf<Pair<InetSocketAddress, Long>>()

    /** Every [cancelProbe] call's peer, in order. */
    val probeCancels = mutableListOf<InetSocketAddress>()

    /** Every [linkQualityOf] call's peer, in order. */
    val linkQualityQueries = mutableListOf<InetSocketAddress>()

    /** Every [send], decoded as UTF-8 text plus its target. */
    val sent = mutableListOf<Sent>()

    /** Every [send] as its raw bytes plus target - recorded for *all* sends, text ones included. */
    val sentBytes = mutableListOf<Pair<ByteArray, InetSocketAddress>>()
    val bound = mutableMapOf<InetSocketAddress, (String) -> Unit>()
    val boundBytes = mutableMapOf<InetSocketAddress, (ByteArray) -> Unit>()
    val deregistered = mutableListOf<InetSocketAddress>()

    /** Every [sendReliable] call, as `(bytes, peer)`, in order. */
    val reliableSent = mutableListOf<Pair<ByteArray, InetSocketAddress>>()

    /** Every peer bound via [bindReliable], last call wins. */
    val boundReliable = mutableMapOf<InetSocketAddress, (ByteArray) -> Unit>()

    override fun send(bytes: ByteArray, to: InetSocketAddress): Result<Unit> {
        sent += Sent(String(bytes, Charsets.UTF_8), to)
        sentBytes += bytes to to
        return sendResult
    }

    override fun bind(peer: InetSocketAddress, onMessage: (String) -> Unit) {
        bound[peer] = onMessage
        boundBytes.remove(peer)
    }

    override fun bindBytes(peer: InetSocketAddress, onMessage: (ByteArray) -> Unit) {
        boundBytes[peer] = onMessage
        bound.remove(peer)
    }

    override fun deregister(peer: InetSocketAddress) {
        deregistered += peer
        bound.remove(peer)
        boundBytes.remove(peer)
    }

    override fun scheduleKeepAlive(peer: InetSocketAddress, intervalMillis: Long): Result<Unit> {
        keepAliveSchedules += peer to intervalMillis
        return scheduleKeepAliveResult
    }

    override fun cancelKeepAlive(peer: InetSocketAddress): Result<Unit> {
        keepAliveCancels += peer
        return cancelKeepAliveResult
    }

    override fun scheduleProbe(peer: InetSocketAddress, intervalMillis: Long): Result<Unit> {
        probeSchedules += peer to intervalMillis
        return scheduleProbeResult
    }

    override fun cancelProbe(peer: InetSocketAddress): Result<Unit> {
        probeCancels += peer
        return cancelProbeResult
    }

    override fun linkQualityOf(peer: InetSocketAddress): LinkQuality? {
        linkQualityQueries += peer
        return linkQuality
    }

    override fun sendReliable(peer: InetSocketAddress, bytes: ByteArray): Result<Unit> {
        reliableSent += bytes to peer
        return sendReliableResult
    }

    override fun bindReliable(peer: InetSocketAddress, onMessage: (ByteArray) -> Unit): Result<Unit> {
        boundReliable[peer] = onMessage
        return bindReliableResult
    }

    /** Invokes the text handler bound for [peer] with [message], as the server would. */
    fun deliver(peer: InetSocketAddress, message: String) {
        bound.getValue(peer)(message)
    }

    /** Invokes the bytes handler bound for [peer] with [bytes], as the server would. */
    fun deliverBytes(peer: InetSocketAddress, bytes: ByteArray) {
        boundBytes.getValue(peer)(bytes)
    }

    /** Invokes the reliable handler bound for [peer] with [bytes], as the server would. */
    fun deliverReliable(peer: InetSocketAddress, bytes: ByteArray) {
        boundReliable.getValue(peer)(bytes)
    }
}
