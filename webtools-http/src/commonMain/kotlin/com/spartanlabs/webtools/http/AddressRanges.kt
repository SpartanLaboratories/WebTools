package com.spartanlabs.webtools.http

/** The class of a denied address: a short kebab-case name used only in logs and tests. */
internal class DeniedRange(val rangeClass: String)

// Source of this table: the IANA IPv4 and IPv6 Special-Purpose Address Registries, "Last Updated 2025-10-09" (read
// 2026-10-25); multicast 224.0.0.0/4 from the IPv4 Multicast Address Space registry (RFC 5771); global unicast 2000::/3
// from the IPv6 Address Space registry (Last Updated 2025-10-23). Two rules are NOT registry rows and a refresh must not
// delete them: 168.63.129.16/32 (Azure platform endpoint) and the ISATAP interface-identifier rule.
// Refresh: fetch iana-ipv4-special-registry-1.csv and iana-ipv6-special-registry-1.csv; strip the footnote markers of the
// Address Block cells ("192.0.0.0/24 [2]", "2002::/16 [3]") and split the one cell that holds two prefixes
// ("192.0.0.170/32, 192.0.0.171/32"); a quoted Name cell contains commas, so parse the CSV properly. Diff against the rows
// below. Add any IPv4 row and any IPv6 row INSIDE 2000::/3; a row outside 2000::/3 needs no edit. Check that the IPv6
// Address Space registry still limits unicast allocation to 2000::/3. Then update the row lists of
// AddressRangesRowsDeterministicTest and AddressRangesControlsDeterministicTest, and the date above.

/** The address table: which 4- or 16-byte addresses the `Untrusted` policy denies. Pure, immutable, JVM-free. */
internal object AddressRanges {
    private class Row(val block: CidrBlock, val denied: DeniedRange)

    // A malformed literal in this table is a programmer error: getOrThrow fails the object's initialisation on the first use,
    // which the first test catches.
    private fun row(cidr: String, rangeClass: String): Row = Row(CidrBlock.parse(cidr).getOrThrow(), DeniedRange(rangeClass))

    // Most specific first, so a nested row logs its own class (a first-match table over the parent would log the parent's).
    private val V4_ROWS: List<Row> = listOf(
        row("0.0.0.0/32", "this-host"),
        row("192.0.0.8/32", "ipv4-dummy-address"),
        row("192.0.0.9/32", "pcp-anycast"),
        row("192.0.0.10/32", "turn-anycast"),
        row("192.0.0.170/32", "nat64-dns64-discovery"),
        row("192.0.0.171/32", "nat64-dns64-discovery"),
        row("192.0.0.0/29", "ipv4-service-continuity-prefix"),
        row("192.88.99.2/32", "6a44-relay-anycast"),
        row("255.255.255.255/32", "limited-broadcast"),
        row("0.0.0.0/8", "this-network"),
        row("10.0.0.0/8", "private-use"),
        row("100.64.0.0/10", "shared-address-space"),
        row("127.0.0.0/8", "loopback"),
        row("169.254.0.0/16", "link-local"),
        row("172.16.0.0/12", "private-use"),
        row("192.0.0.0/24", "ietf-protocol-assignments"),
        row("192.0.2.0/24", "documentation"),
        row("192.31.196.0/24", "as112-v4"),
        row("192.52.193.0/24", "amt"),
        row("192.88.99.0/24", "deprecated-6to4-relay-anycast"),
        row("192.168.0.0/16", "private-use"),
        row("192.175.48.0/24", "direct-delegation-as112"),
        row("198.18.0.0/15", "benchmarking"),
        row("198.51.100.0/24", "documentation"),
        row("203.0.113.0/24", "documentation"),
        row("240.0.0.0/4", "reserved"),
        // Not registry rows. Multicast: IPv4 Multicast Address Space registry (RFC 5771), adjacent to 240.0.0.0/4.
        row("224.0.0.0/4", "multicast"),
        // The Azure platform endpoint: a public address in no IANA row, port 80, listed by both SSRF catalogues. A refresh must keep it.
        row("168.63.129.16/32", "azure-wireserver"),
    )

    // Inside 2000::/3 only; the nested rows of 2001::/23 first.
    private val V6_ROWS: List<Row> = listOf(
        row("2001::/32", "teredo"),
        row("2001:1::1/128", "pcp-anycast"),
        row("2001:1::2/128", "turn-anycast"),
        row("2001:1::3/128", "dns-sd-srp-anycast"),
        row("2001:2::/48", "benchmarking"),
        row("2001:3::/32", "amt"),
        row("2001:4:112::/48", "as112-v6"),
        row("2001:10::/28", "deprecated-orchid"),
        row("2001:20::/28", "orchidv2"),
        row("2001:30::/28", "drone-remote-id-det"),
        row("2001::/23", "ietf-protocol-assignments"),
        row("2001:db8::/32", "documentation"),
        row("2002::/16", "6to4"),
        row("2620:4f:8000::/48", "direct-delegation-as112"),
        row("3fff::/20", "documentation"),
    )

    private val UNUSUAL_LENGTH = DeniedRange("unusual-length")
    private val NOT_GLOBAL_UNICAST = DeniedRange("not-global-unicast")
    private val ISATAP = DeniedRange("isatap")

    /**
     * Judges one address.
     *
     * @param address 4 bytes (IPv4) or 16 bytes (IPv6); any other length is denied (`unusual-length`): a custom `InetAddress`
     *   or a corrupt array fails closed.
     * @return `null` when allowed, otherwise the matched row's class. Never throws.
     */
    fun deny(address: ByteArray): DeniedRange? = when (address.size) {
        4 -> denyV4(address)
        16 -> denyV6(address)
        else -> UNUSUAL_LENGTH
    }

    /** True for a 16-byte IPv4-mapped address `::ffff:0:0/96`. */
    fun isMapped(a: ByteArray): Boolean =
        a.size == 16 && (0..9).all { a[it].toInt() == 0 } && (a[10].toInt() and 0xFF) == 0xFF && (a[11].toInt() and 0xFF) == 0xFF

    /** True for a 16-byte address in the NAT64 well-known prefix `64:ff9b::/96`. */
    fun isNat64WellKnown(a: ByteArray): Boolean =
        a.size == 16 && a[0].toInt() == 0 && a[1].toInt() == 0x64 && (a[2].toInt() and 0xFF) == 0xFF &&
            (a[3].toInt() and 0xFF) == 0x9B && (4..11).all { a[it].toInt() == 0 }

    /** The IPv4 address embedded in a mapped or NAT64 well-known address (its low 4 bytes), otherwise `null`. */
    fun embeddedV4(a: ByteArray): ByteArray? = if (isMapped(a) || isNat64WellKnown(a)) a.copyOfRange(12, 16) else null

    private fun denyV4(a: ByteArray): DeniedRange? = V4_ROWS.firstOrNull { it.block.contains(a) }?.denied

    private fun denyV6(a: ByteArray): DeniedRange? {
        embeddedV4(a)?.let { return denyV4(it) }
        if ((a[0].toInt() and 0xE0) != 0x20) return NOT_GLOBAL_UNICAST
        V6_ROWS.firstOrNull { it.block.contains(a) }?.let { return it.denied }
        return if (isIsatap(a)) ISATAP else null
    }

    // An ISATAP interface identifier (RFC 5214): groups 4 and 5 (bytes 8..11) are 0000:5efe or 0200:5efe, the mask ignoring the
    // universal/local and group bits. It embeds an IPv4 address a host with an ISATAP interface tunnels to.
    private fun isIsatap(a: ByteArray): Boolean {
        val group4 = ((a[8].toInt() and 0xFF) shl 8) or (a[9].toInt() and 0xFF)
        val group5 = ((a[10].toInt() and 0xFF) shl 8) or (a[11].toInt() and 0xFF)
        return (group4 and 0xFCFF) == 0 && group5 == 0x5EFE
    }
}
