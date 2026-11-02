package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.UrlGuard
import java.net.InetAddress

/**
 * The real [OkHttpTransport] over a loopback test server (a `mockwebserver3.MockWebServer` started on `127.0.0.1`), for a consumer's
 * Level 3 tests. Each factory takes the whole policy, so its ports, extra ranges and mixed-answer rule are live, and an optional resolver
 * function in place of the system resolver.
 *
 * **Stable Core, source compatibility only.** A literal-IP URL ([webtoolsUrl]) never reaches the resolver; a stub answer for a name is built
 * with `InetAddress.getByAddress(bytes)`, never `getByName("name")`, so the JDK performs no lookup. The caller closes the result. This is the
 * only way to reach a loopback address with `Untrusted` rules live; it is not a production switch, and nothing here is reachable from the main
 * artifact.
 */
public object LoopbackTransports {
    /**
     * The real transport under an `Untrusted` policy with every rule live except the loopback ones: the host `localhost`, the addresses
     * `127.0.0.0/8` and `::1`, and the port rule for exactly those hosts are exempt; `extraDeniedRanges`, the policy's suffixes, the
     * sensitive-header rule and the downgrade rule stay live.
     *
     * @param userAgent the `User-Agent` sent on every hop, as the public factory takes it.
     * @param policy the policy; the defaults when omitted.
     * @param resolve answers a host name with addresses; the system resolver by default. Its exceptions reach the transport unchanged.
     * @return the transport; the caller closes it.
     * @throws IllegalArgumentException for a `userAgent` the public factory refuses.
     */
    public fun untrusted(
        userAgent: String,
        policy: OutboundUrlPolicy.Untrusted = OutboundUrlPolicy.untrusted(),
        resolve: (host: String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    ): OkHttpTransport = OkHttpTransport(userAgent, UrlGuard(policy, GuardRelaxation.LoopbackForTests), FunctionDns(resolve), null)

    /**
     * The real transport under a `Trusted` policy with cleartext forced on (loopback servers speak `http`) and every other property of
     * [policy] kept; no relaxation (`Trusted` has no loopback rule to relax).
     *
     * @param userAgent the `User-Agent` sent on every hop, as the public factory takes it.
     * @param policy the policy; the defaults when omitted.
     * @param resolve answers a host name with addresses; the system resolver by default. Its exceptions reach the transport unchanged.
     * @return the transport; the caller closes it.
     * @throws IllegalArgumentException for a `userAgent` the public factory refuses.
     */
    public fun trusted(
        userAgent: String,
        policy: OutboundUrlPolicy.Trusted = OutboundUrlPolicy.trusted(),
        resolve: (host: String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    ): OkHttpTransport {
        val effective = if (policy.allowCleartext) policy else OutboundUrlPolicy.trusted(policy) { allowCleartext = true }
        return OkHttpTransport(userAgent, UrlGuard(effective, GuardRelaxation.None), FunctionDns(resolve), null)
    }
}
