package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.testing.support.webtools.http.BypassVectors
import com.spartanlabs.testing.support.webtools.http.IpLiteralGenerator
import com.spartanlabs.testing.support.webtools.http.UrlCorpus
import com.spartanlabs.webtools.http.HostRules
import com.spartanlabs.webtools.http.HostVerdict
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level 4a - OkHttp 5.5.0 sends a host that matches `VERIFY_AS_IP_ADDRESS` straight to `InetAddress.getByName` and never
 * to `Dns`; for every such host (the seeded literals of both families, the host forms of the parent research's F1
 * catalogue and every host of `BypassVectors` the regex matches) `HostRules.classify` is never a `Name`: it is a `Literal`
 * or a `Refused`, so the resolver guard can never be the only layer between such a host and a socket. The converse is not
 * asserted.
 */
class OkHttpLiteralRegexDeterministicTest {

    // okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/-HostnamesCommon.kt at tag parent-5.5.0: VERIFY_AS_IP_ADDRESS.
    private val verifyAsIpAddress = Regex("([0-9a-fA-F]*:[0-9a-fA-F:.]*)|([\\d.]+)")

    private val seed = 7
    private val count = 50_000

    // The host forms of the parent research's F1 table (brackets removed), as OkHttp's HttpUrl prints them where it changes them.
    private val f1HostForms: List<String> = listOf(
        "2130706433", "127.1", "127.0.1", "0", "0.0.0.0", "::", "0177.0.0.1", "0177.0.0.01", "08.0.0.1", "00000127.0.0.1",
        "017700000001", "127.0.0.1.", "0x7f000001", "0x7f.0.0.1", "0x7f.1", "::ffff:127.0.0.1", "::ffff:7f00:1",
        "0:0:0:0:0:ffff:7f00:1", "::127.0.0.1", "::7f00:1", "64:ff9b::7f00:1", "2002:7f00:1::", "fd00::1", "fe80::1", "fec0::1",
        "ff02::1", "169.254.169.254", "100.64.0.1", "10.0.0.5", "localhost", "LOCALHOST", "foo.localhost", "localhost.",
        "fe80::1%25eth0", "fe80::1%eth0", "127.0.0.1%00",
    )

    private fun neverAName(host: String, label: String) {
        if (!verifyAsIpAddress.matches(host)) return
        val verdict = HostRules.classify(host, emptySet())
        assertFalse(verdict is HostVerdict.Name, "$label <${UrlCorpus.visible(host)}>: a literal-shaped host reached the resolver guard")
    }

    @Test
    fun `seeded literals of both families that match the regex are never a Name`() {
        var matched = 0
        IpLiteralGenerator.ipv4(seed, count).forEachIndexed { index, host -> if (verifyAsIpAddress.matches(host)) matched++; neverAName(host, "seed $seed ipv4 #$index") }
        IpLiteralGenerator.ipv6(seed, count).forEachIndexed { index, host -> if (verifyAsIpAddress.matches(host)) matched++; neverAName(host, "seed $seed ipv6 #$index") }
        assertTrue(matched > 0, "seed $seed: no generated string matched the regex")
    }

    @Test
    fun `the F1 host forms and the bypass-vector hosts that match the regex are never a Name`() {
        f1HostForms.forEach { neverAName(it, "F1") }
        val hosts = BypassVectors.vectors.mapNotNull { it.host }.distinct()
        assertTrue(hosts.any { verifyAsIpAddress.matches(it) })
        hosts.forEach { neverAName(it, "BypassVectors") }
    }
}
