package com.spartanlabs.webtools.http

import okhttp3.Dns
import java.net.Proxy
import javax.net.SocketFactory

/** The three client settings one policy implies; a `null` means "leave OkHttp's default". Unit 6 applies them to its `OkHttpClient.Builder`. */
internal class PolicyEnforcement(val dns: Dns, val socketFactory: SocketFactory?, val proxy: Proxy?)

/**
 * The bundle for this guard's policy. `Untrusted`: the guarded `Dns` over [baseDns], the guarded socket factory and
 * `Proxy.NO_PROXY` (a proxy resolves names itself and bypasses both guards). `Trusted`: [baseDns], no socket factory and no
 * proxy, so OkHttp's default selection stays.
 *
 * @param baseDns `Dns.SYSTEM`, or a test stub.
 */
internal fun UrlGuard.enforcement(baseDns: Dns): PolicyEnforcement = when (policy) {
    is OutboundUrlPolicy.Untrusted -> PolicyEnforcement(GuardedDns(baseDns, this), GuardedSocketFactory(this), Proxy.NO_PROXY)
    is OutboundUrlPolicy.Trusted -> PolicyEnforcement(baseDns, null, null)
}
