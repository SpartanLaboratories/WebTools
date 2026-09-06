package com.spartanlabs.testing.support.webtools.udp

import com.spartanlabs.webtools.udp.ClientChannel
import java.net.InetSocketAddress

/**
 * A socket-free [ClientChannel] test fixture. Records every [send] (as the decoded
 * UTF-8 string plus its target, and separately as the raw bytes), every [bind] /
 * [bindBytes] / [deregister], and lets a test invoke a bound handler directly. Backs
 * the socket-free [com.spartanlabs.webtools.udp.UDPConnection] tests.
 */
internal class FakeClientChannel(
    private val sendResult: Result<Unit> = Result.success(Unit),
) : ClientChannel {

    data class Sent(val text: String, val to: InetSocketAddress)

    /** Every [send], decoded as UTF-8 text plus its target. */
    val sent = mutableListOf<Sent>()

    /** Every [send] as its raw bytes plus target - recorded for *all* sends, text ones included. */
    val sentBytes = mutableListOf<Pair<ByteArray, InetSocketAddress>>()
    val bound = mutableMapOf<InetSocketAddress, (String) -> Unit>()
    val boundBytes = mutableMapOf<InetSocketAddress, (ByteArray) -> Unit>()
    val deregistered = mutableListOf<InetSocketAddress>()

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

    /** Invokes the text handler bound for [peer] with [message], as the server would. */
    fun deliver(peer: InetSocketAddress, message: String) {
        bound.getValue(peer)(message)
    }

    /** Invokes the bytes handler bound for [peer] with [bytes], as the server would. */
    fun deliverBytes(peer: InetSocketAddress, bytes: ByteArray) {
        boundBytes.getValue(peer)(bytes)
    }
}
