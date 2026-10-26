package com.spartanlabs.webtools.http

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress

private val UNRESOLVED = GuardRefusal(RefusalReason.DeniedAddress, "unresolved")

/**
 * A [Socket] that judges the target of every connect. It extends `Socket(Proxy.NO_PROXY)`, which installs the platform socket
 * implementation directly: `new Socket()` installs a `SocksSocketImpl` that consults the JVM-wide SOCKS settings, which
 * `proxy(Proxy.NO_PROXY)` on an `OkHttpClient` does not close. Overrides only `connect(SocketAddress, Int)`
 * (`connect(SocketAddress)` calls it with 0). A non-`InetSocketAddress` or unresolved target is refused (a SOCKS
 * implementation would hand a host name to a proxy to resolve), a denied address is refused, both before any I/O. Stateless
 * beyond `super`, so fast fallback's concurrent attempts, and a loser being closed mid-connect, add no hazard.
 */
internal class GuardedSocket(private val guard: UrlGuard) : Socket(Proxy.NO_PROXY) {
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
