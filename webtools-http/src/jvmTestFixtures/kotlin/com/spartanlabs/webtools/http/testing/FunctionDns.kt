package com.spartanlabs.webtools.http.testing

import okhttp3.Dns
import java.net.InetAddress

/**
 * An `okhttp3.Dns` over a function. Exceptions from [resolve] propagate unchanged (an `UnknownHostException` is a DNS failure). It overrides
 * `lookup` only, so the guard layered on top inherits OkHttp's record-based `newCall`, as `GuardedDns` does.
 *
 * @property resolve answers a host name with its addresses.
 */
internal class FunctionDns(private val resolve: (String) -> List<InetAddress>) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = resolve(hostname)

    override fun toString(): String = "FunctionDns"
}
