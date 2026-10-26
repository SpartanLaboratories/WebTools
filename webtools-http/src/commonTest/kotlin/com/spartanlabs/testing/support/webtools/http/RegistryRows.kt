package com.spartanlabs.testing.support.webtools.http

/** The registry rows the address table must deny, with the class the table reports for each row's first and last address. */
internal object RegistryRows {
    /** A block, its first and last address and the class expected for each (a nested row's edge may belong to a more specific row). */
    class Row(val block: String, val first: String, val firstClass: String, val last: String, val lastClass: String)

    private fun same(block: String, first: String, last: String, rangeClass: String) = Row(block, first, rangeClass, last, rangeClass)

    /** The 26 IPv4 registry prefixes (25 rows), then the two rows that are not registry rows. */
    val ipv4: List<Row> = listOf(
        Row("0.0.0.0/8", "0.0.0.0", "this-host", "0.255.255.255", "this-network"),
        same("0.0.0.0/32", "0.0.0.0", "0.0.0.0", "this-host"),
        same("10.0.0.0/8", "10.0.0.0", "10.255.255.255", "private-use"),
        same("100.64.0.0/10", "100.64.0.0", "100.127.255.255", "shared-address-space"),
        same("127.0.0.0/8", "127.0.0.0", "127.255.255.255", "loopback"),
        same("169.254.0.0/16", "169.254.0.0", "169.254.255.255", "link-local"),
        same("172.16.0.0/12", "172.16.0.0", "172.31.255.255", "private-use"),
        Row("192.0.0.0/24", "192.0.0.0", "ipv4-service-continuity-prefix", "192.0.0.255", "ietf-protocol-assignments"),
        same("192.0.0.0/29", "192.0.0.0", "192.0.0.7", "ipv4-service-continuity-prefix"),
        same("192.0.0.8/32", "192.0.0.8", "192.0.0.8", "ipv4-dummy-address"),
        same("192.0.0.9/32", "192.0.0.9", "192.0.0.9", "pcp-anycast"),
        same("192.0.0.10/32", "192.0.0.10", "192.0.0.10", "turn-anycast"),
        same("192.0.0.170/32", "192.0.0.170", "192.0.0.170", "nat64-dns64-discovery"),
        same("192.0.0.171/32", "192.0.0.171", "192.0.0.171", "nat64-dns64-discovery"),
        same("192.0.2.0/24", "192.0.2.0", "192.0.2.255", "documentation"),
        same("192.31.196.0/24", "192.31.196.0", "192.31.196.255", "as112-v4"),
        same("192.52.193.0/24", "192.52.193.0", "192.52.193.255", "amt"),
        same("192.88.99.0/24", "192.88.99.0", "192.88.99.255", "deprecated-6to4-relay-anycast"),
        same("192.88.99.2/32", "192.88.99.2", "192.88.99.2", "6a44-relay-anycast"),
        same("192.168.0.0/16", "192.168.0.0", "192.168.255.255", "private-use"),
        same("192.175.48.0/24", "192.175.48.0", "192.175.48.255", "direct-delegation-as112"),
        same("198.18.0.0/15", "198.18.0.0", "198.19.255.255", "benchmarking"),
        same("198.51.100.0/24", "198.51.100.0", "198.51.100.255", "documentation"),
        same("203.0.113.0/24", "203.0.113.0", "203.0.113.255", "documentation"),
        Row("240.0.0.0/4", "240.0.0.0", "reserved", "255.255.255.255", "limited-broadcast"),
        same("255.255.255.255/32", "255.255.255.255", "255.255.255.255", "limited-broadcast"),
        same("224.0.0.0/4", "224.0.0.0", "239.255.255.255", "multicast"),
        same("168.63.129.16/32", "168.63.129.16", "168.63.129.16", "azure-wireserver"),
    )

    private const val NGU = "not-global-unicast"

    /** The 25 IPv6 registry rows, in the registry's order. The first and last addresses are in RFC 5952 form. */
    val ipv6: List<Row> = listOf(
        same("::1/128", "::1", "::1", NGU),
        same("::/128", "::", "::", NGU),
        Row("::ffff:0:0/96", "::ffff:0:0", "this-host", "::ffff:ffff:ffff", "limited-broadcast"),
        Row("64:ff9b::/96", "64:ff9b::", "this-host", "64:ff9b::ffff:ffff", "limited-broadcast"),
        same("64:ff9b:1::/48", "64:ff9b:1::", "64:ff9b:1:ffff:ffff:ffff:ffff:ffff", NGU),
        same("100::/64", "100::", "100::ffff:ffff:ffff:ffff", NGU),
        same("100:0:0:1::/64", "100:0:0:1::", "100::1:ffff:ffff:ffff:ffff", NGU),
        Row("2001::/23", "2001::", "teredo", "2001:1ff:ffff:ffff:ffff:ffff:ffff:ffff", "ietf-protocol-assignments"),
        same("2001::/32", "2001::", "2001:0:ffff:ffff:ffff:ffff:ffff:ffff", "teredo"),
        same("2001:1::1/128", "2001:1::1", "2001:1::1", "pcp-anycast"),
        same("2001:1::2/128", "2001:1::2", "2001:1::2", "turn-anycast"),
        same("2001:1::3/128", "2001:1::3", "2001:1::3", "dns-sd-srp-anycast"),
        same("2001:2::/48", "2001:2::", "2001:2:0:ffff:ffff:ffff:ffff:ffff", "benchmarking"),
        same("2001:3::/32", "2001:3::", "2001:3:ffff:ffff:ffff:ffff:ffff:ffff", "amt"),
        same("2001:4:112::/48", "2001:4:112::", "2001:4:112:ffff:ffff:ffff:ffff:ffff", "as112-v6"),
        same("2001:10::/28", "2001:10::", "2001:1f:ffff:ffff:ffff:ffff:ffff:ffff", "deprecated-orchid"),
        same("2001:20::/28", "2001:20::", "2001:2f:ffff:ffff:ffff:ffff:ffff:ffff", "orchidv2"),
        same("2001:30::/28", "2001:30::", "2001:3f:ffff:ffff:ffff:ffff:ffff:ffff", "drone-remote-id-det"),
        same("2001:db8::/32", "2001:db8::", "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff", "documentation"),
        same("2002::/16", "2002::", "2002:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "6to4"),
        same("2620:4f:8000::/48", "2620:4f:8000::", "2620:4f:8000:ffff:ffff:ffff:ffff:ffff", "direct-delegation-as112"),
        same("3fff::/20", "3fff::", "3fff:fff:ffff:ffff:ffff:ffff:ffff:ffff", "documentation"),
        same("5f00::/16", "5f00::", "5f00:ffff:ffff:ffff:ffff:ffff:ffff:ffff", NGU),
        same("fc00::/7", "fc00::", "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", NGU),
        same("fe80::/10", "fe80::", "febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff", NGU),
    )
}
