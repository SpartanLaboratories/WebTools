package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.StubDns
import com.spartanlabs.testing.support.webtools.http.trustedGuard
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.webtools.http.GuardedDns
import com.spartanlabs.webtools.http.GuardedSocketFactory
import com.spartanlabs.webtools.http.PolicyEnforcement
import com.spartanlabs.webtools.http.PolicyRefusedUnknownHost
import com.spartanlabs.webtools.http.enforcement
import okhttp3.OkHttpClient
import java.net.Proxy
import javax.net.SocketFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Level 2 - `UrlGuard.enforcement(baseDns)`: for `Untrusted` a `GuardedDns`, a `GuardedSocketFactory` and
 * `Proxy.NO_PROXY`, which an `OkHttpClient.Builder` takes as its `dns`, `socketFactory` and `proxy` (`build()` opens
 * nothing); for `Trusted` the base stub itself and two `null`s, so the built client keeps OkHttp's defaults. The
 * `Untrusted` bundle's `dns` judges a private answer.
 */
class PolicyEnforcementComponentTest {

    private fun apply(e: PolicyEnforcement): OkHttpClient {
        val builder = OkHttpClient.Builder().dns(e.dns)
        e.socketFactory?.let(builder::socketFactory)
        e.proxy?.let(builder::proxy)
        return builder.build()
    }

    @Test
    fun `the Untrusted bundle guards the resolver and the sockets and forbids a proxy`() {
        val e = untrustedGuard().enforcement(StubDns.of("8.8.8.8"))
        assertIs<GuardedDns>(e.dns)
        assertIs<GuardedSocketFactory>(e.socketFactory)
        assertSame(Proxy.NO_PROXY, e.proxy)
        val client = apply(e)
        assertSame(Proxy.NO_PROXY, client.proxy)
        assertSame(e.dns, client.dns)
        assertSame(e.socketFactory, client.socketFactory)
    }

    @Test
    fun `the Trusted bundle leaves OkHttp's defaults`() {
        val stub = StubDns.of("127.0.0.1")
        val e = trustedGuard().enforcement(stub)
        assertSame(stub, e.dns)
        assertNull(e.socketFactory)
        assertNull(e.proxy)
        val client = apply(e)
        assertNull(client.proxy)
        assertSame(SocketFactory.getDefault(), client.socketFactory)
        assertSame(stub, client.dns)
    }

    @Test
    fun `the Untrusted bundle's dns judges a private answer`() {
        val client = apply(untrustedGuard().enforcement(StubDns.of("10.0.0.5")))
        val marker = assertFailsWith<PolicyRefusedUnknownHost> { client.dns.lookup("name.test") }
        assertEquals("private-use", marker.refusal.rangeClass)
    }
}
