package com.spartanlabs.webtools.http

import okhttp3.Dns
import java.net.Proxy
import javax.net.SocketFactory

/**
 * The three client settings one policy implies; a `null` means "leave OkHttp's default". The OkHttp transport (#65)
 * applies them to its `OkHttpClient.Builder`.
 *
 * **Wiring.** `.dns(dns)` always; `.socketFactory(socketFactory)` and `.proxy(proxy)` only when non-null. Preconditions on
 * the client: `retryOnConnectionFailure(false)`, and fast fallback left at its default. The guards act in this order for
 * one call: the transport's [UrlGuard.checkHop] judges the URL before any `Call` (an IP-literal host is judged there and
 * never reaches `Dns`: OkHttp resolves a literal itself); [GuardedDns] judges every address a name resolves to; [GuardedSocket]
 * judges every connect, last. A consumer-supplied proxy other than `Proxy.NO_PROXY` routes around both guards, because the
 * proxy resolves the name and makes the connection; `Untrusted` therefore pins `Proxy.NO_PROXY`.
 *
 * @property dns the resolver the client uses: a [GuardedDns] over the base resolver for `Untrusted`, the base resolver itself for `Trusted`.
 * @property socketFactory the [GuardedSocketFactory] for `Untrusted`; `null` for `Trusted`, which keeps OkHttp's default factory.
 * @property proxy `Proxy.NO_PROXY` for `Untrusted`; `null` for `Trusted`, which keeps OkHttp's default proxy selection.
 */
internal class PolicyEnforcement(val dns: Dns, val socketFactory: SocketFactory?, val proxy: Proxy?)

/**
 * The bundle for this guard's policy. `Untrusted`: the guarded `Dns` over [baseDns], the guarded socket factory and
 * `Proxy.NO_PROXY` (a proxy resolves names itself and bypasses both guards). `Trusted`: [baseDns], no socket factory and no
 * proxy, so OkHttp's default selection stays.
 *
 * @param baseDns `Dns.SYSTEM`, or a test stub.
 * @return the three settings, applied as [PolicyEnforcement] describes.
 */
internal fun UrlGuard.enforcement(baseDns: Dns): PolicyEnforcement = when (policy) {
    is OutboundUrlPolicy.Untrusted -> PolicyEnforcement(GuardedDns(baseDns, this), GuardedSocketFactory(this), Proxy.NO_PROXY)
    is OutboundUrlPolicy.Trusted -> PolicyEnforcement(baseDns, null, null)
}
