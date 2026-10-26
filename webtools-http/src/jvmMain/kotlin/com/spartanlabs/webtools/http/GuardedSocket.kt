package com.spartanlabs.webtools.http

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress

// An unresolved or non-Inet target has no address to judge, so it is refused as a denied address with the pseudo-class
// "unresolved": the only reason that carries a range class, so the transport's one log line can name the cause; the
// refusal is as closed as a denied address (a SOCKS implementation would hand the name to a proxy to resolve).
private val UNRESOLVED = GuardRefusal(RefusalReason.DeniedAddress, "unresolved")

/**
 * A [Socket] that judges the target of every connect. It extends `Socket(Proxy.NO_PROXY)`, which installs the platform socket
 * implementation directly: `new Socket()` installs a `SocksSocketImpl` that consults the JVM-wide SOCKS settings, which
 * `proxy(Proxy.NO_PROXY)` on an `OkHttpClient` does not close. Overrides only `connect(SocketAddress, Int)`
 * (`connect(SocketAddress)` calls it with 0). A non-`InetSocketAddress` or unresolved target is refused (a SOCKS
 * implementation would hand a host name to a proxy to resolve), a denied address is refused, both before any I/O.
 *
 * `connect` runs on whichever thread OkHttp connects on: the caller's for `execute()`, a `Dispatcher` thread for
 * `enqueue()`, a fast-fallback connect thread when routes race. After it throws, [PolicyRefusedIo] propagates as that
 * attempt's connect failure with no socket opened: OkHttp moves to the call's next route, and when every route is refused
 * the call fails with the first marker on top and the others in `suppressed` (with `retryOnConnectionFailure(false)`
 * nothing is retried).
 *
 * Thread safety: the guard is immutable and the class holds no state beyond `super`, so fast fallback's concurrent
 * attempts, and a loser being closed by another thread while it is inside `connect`, add no hazard beyond `Socket`'s own.
 *
 * @property guard the guard whose policy judges the target's address ([UrlGuard.checkAddress]).
 */
internal class GuardedSocket(private val guard: UrlGuard) : Socket(Proxy.NO_PROXY) {
    /**
     * Judges [endpoint], then connects.
     *
     * @param endpoint the target; must be a resolved `InetSocketAddress`.
     * @param timeout the connect timeout in milliseconds, 0 for none, as `Socket.connect` reads it.
     * @throws PolicyRefusedIo before any I/O when the target is not a resolved `InetSocketAddress` or its address is denied.
     * @throws IOException whatever `Socket.connect` throws for an allowed target.
     */
    @Throws(IOException::class)
    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        val refusal = refusalFor(endpoint)
        if (refusal != null) throw PolicyRefusedIo(refusal)
        super.connect(endpoint, timeout)
    }

    private fun refusalFor(endpoint: SocketAddress?): GuardRefusal? {
        if (endpoint !is InetSocketAddress || endpoint.isUnresolved) return UNRESOLVED
        val address = endpoint.address ?: return UNRESOLVED
        return guard.checkAddress(address.address)
    }
}
