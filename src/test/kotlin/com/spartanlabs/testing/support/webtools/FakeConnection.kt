package com.spartanlabs.testing.support.webtools

import com.spartanlabs.webtools.Connection
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * A socket-free [Connection] test fixture: it records the calls made to it and
 * returns configurable [Result]s, so handshake and registration logic can be
 * exercised with no real I/O. Shared across the component and non-functional levels.
 */
internal class FakeConnection(
    override val name: String,
    override val peer: InetSocketAddress =
        InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
    private val actuateResult: Result<Unit> = Result.success(Unit),
    private val terminateResult: Result<Unit> = Result.success(Unit),
    private val pushResult: Result<Unit> = Result.success(Unit),
    private val keepAliveResult: Result<Unit> = Result.success(Unit),
) : Connection {

    var actuateCalls = 0
        private set

    var terminateCalls = 0
        private set

    var keepAliveCalls = 0
        private set

    /** The last handler passed to [actuate], or `null` if never actuated. */
    var lastOnMessage: ((String) -> Unit)? = null
        private set

    val pushed = mutableListOf<String>()

    override fun actuate(onMessage: (message: String) -> Unit): Result<Unit> {
        actuateCalls++
        lastOnMessage = onMessage
        return actuateResult
    }

    override fun terminate(): Result<Unit> {
        terminateCalls++
        return terminateResult
    }

    override fun push(message: String): Result<Unit> {
        pushed += message
        return pushResult
    }

    override fun keepAlive(): Result<Unit> {
        keepAliveCalls++
        return keepAliveResult
    }
}
