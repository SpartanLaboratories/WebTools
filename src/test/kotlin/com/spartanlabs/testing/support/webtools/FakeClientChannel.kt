package com.spartanlabs.testing.support.webtools

import com.spartanlabs.webtools.ClientChannel
import java.net.InetSocketAddress

/**
 * A socket-free [ClientChannel] test fixture. Records every [send] (as the decoded
 * UTF-8 string plus its target), every [bind] / [unbind], and lets a test invoke a
 * bound handler directly. Backs the socket-free [com.spartanlabs.webtools.UDPConnection]
 * tests.
 */
internal class FakeClientChannel(
    private val sendResult: Result<Unit> = Result.success(Unit),
) : ClientChannel {

    data class Sent(val text: String, val to: InetSocketAddress)

    val sent = mutableListOf<Sent>()
    val bound = mutableMapOf<InetSocketAddress, (String) -> Unit>()
    val unbound = mutableListOf<InetSocketAddress>()

    override fun send(bytes: ByteArray, to: InetSocketAddress): Result<Unit> {
        sent += Sent(String(bytes, Charsets.UTF_8), to)
        return sendResult
    }

    override fun bind(peer: InetSocketAddress, onMessage: (String) -> Unit) {
        bound[peer] = onMessage
    }

    override fun unbind(peer: InetSocketAddress) {
        unbound += peer
        bound.remove(peer)
    }

    /** Invokes the handler bound for [peer] with [message], as the server would. */
    fun deliver(peer: InetSocketAddress, message: String) {
        bound.getValue(peer)(message)
    }
}
