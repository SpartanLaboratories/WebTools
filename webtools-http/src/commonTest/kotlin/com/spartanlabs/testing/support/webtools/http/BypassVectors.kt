package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.UrlProblem

/**
 * The published SSRF bypass vectors, one data table so nothing is entered twice (`research/bypass-vectors.md` §3 and §4,
 * plus the parent research's F1 host-form catalogue and F10 advisory vectors, by tag). A vector is stage 1 (`Url.parse(text)`
 * must fail with [Vector.problem]) or stage 2 (`Url.parse(text)` succeeds, its `host` is [Vector.host], and an `Untrusted`
 * default guard's `checkHop(url, null)` answers [Vector.outcome], with [Vector.rangeClass] for `DeniedAddress`). An IPv6
 * vector is fed in the printed form unless the row is `spelled` as published. Source tags: PATT = PayloadsAllTheThings
 * `Server Side Request Forgery/README.md` at `3ac27901c711bdf3f5b65a7b1d1820a1f65bd09a`; PATT-cloud = the same commit's
 * `SSRF-Cloud-Instances.md`; HT = HackTricks `url-format-bypass.md` at `16689f85469c3a35946b6ad88e1357cb9ec5a776`;
 * HT-cloud = the same commit's `cloud-ssrf.md`; Orange = Orange Tsai, Black Hat USA 2017; WHATWG = URL Standard at
 * `845bafca22fb0c5a42c4bf384a9bdea525a2f10d`; advisories by CVE or GHSA id.
 */
internal object BypassVectors {
    enum class Outcome { HostForm, ReservedName, DeniedAddress, Port, Allowed }

    class Vector(
        val id: String, val source: String, val parent: String?, val text: String, val problem: UrlProblem?,
        val host: String?, val outcome: Outcome?, val rangeClass: String?,
    ) {
        override fun toString(): String = "$id <${UrlCorpus.visible(text)}>"
    }

    val vectors: List<Vector> = buildList {
        fun refused(id: String, source: String, parent: String?, problem: UrlProblem, vararg texts: String) =
            texts.forEach { add(Vector(id, source, parent, it, problem, null, null, null)) }
        fun hostUrl(host: String) = "http://" + (if (':' in host) "[$host]" else host) + "/"
        fun hosts(id: String, source: String, parent: String?, outcome: Outcome, rangeClass: String?, vararg hosts: String) =
            hosts.forEach { add(Vector(id, source, parent, hostUrl(it), null, it, outcome, rangeClass)) }
        fun spelled(id: String, source: String, parent: String?, outcome: Outcome, rangeClass: String?, published: String, printed: String) =
            add(Vector(id, source, parent, hostUrl(published), null, printed, outcome, rangeClass))
        fun urls(id: String, source: String, parent: String?, outcome: Outcome, rangeClass: String?, host: String, vararg texts: String) =
            texts.forEach { add(Vector(id, source, parent, it, null, host, outcome, rangeClass)) }

        //region stage 1: Url.parse refuses (bypass-vectors.md 3.1)
        refused("P01", "Orange; PATT", null, UrlProblem.UserInfo, "http://foo@evil.com:80@google.com/", "http://foo@127.0.0.1:11211@google.com:80/")
        refused("P02", "Orange; PATT", null, UrlProblem.WhitespaceOrControl, "http://foo@127.0.0.1 @google.com/", "http://1.1.1.1 &@2.2.2.2# @3.3.3.3/")
        refused("P03", "Orange", null, UrlProblem.Port, "http://127.0.0.1:11211:80/")
        refused("P04", "PATT", null, UrlProblem.Backslash, "http://127.1.1.1:80\\@127.2.2.2:80/", "http://127.1.1.1:80\\@@127.2.2.2:80/",
            "http://127.1.1.1:80:\\@@127.2.2.2:80/", "http://127.1.1.1:80#\\@127.2.2.2:80/")
        refused("P05", "HT", null, UrlProblem.Backslash, "http://169.254.1698.254\\@attacker.com", "http://localhost:\\@google.com/../",
            "https://attacker.com\\{domain}/", "https://example.com\\[@attacker.com")
        refused("P06", "PATT; HT", "F1", UrlProblem.Scheme, "http:127.0.0.1/", "https:attacker.com", "https:/attacker.com", "http:@0/",
            "//attacker.com", "/attacker.com", "#attacker.com", "@attacker.com", "%0D%0A/attacker.com")
        refused("P07", "HT; PATT", null, UrlProblem.Backslash, "http:/\\/\\attacker.com", "https:/\\attacker.com", "\\\\/\\/attacker.com/", "file://\\/\\/etc/passwd")
        refused("P08", "PATT", null, UrlProblem.Scheme, "file:///etc/passwd", "gopher://127.0.0.1:6379/_PING", "dict://127.0.0.1:11211/stats",
            "ldap://127.0.0.1/", "sftp://127.0.0.1/", "tftp://127.0.0.1/x", "netdoc:///etc/passwd", "jar:http://127.0.0.1!/", "0://evil.com:80;http://google.com:80/")
        refused("P09", "Orange", null, UrlProblem.WhitespaceOrControl, "http://127.0.0.1\tfoo.google.com")
        refused("P10", "Orange", null, UrlProblem.HostEncoding, "http://127.0.0.1%09foo.google.com", "http://127.0.0.1%2509foo.google.com")
        refused("P11", "Orange", null, UrlProblem.WhitespaceOrControl, "http://127.0.0.1\r\nSLAVEOF orange.tw 6379\r\n:6379/",
            "https://127.0.0.1\r\nSET foo 0 60 5\r\n:443/", "http://0\r\n SLAVEOF orange.tw 6379\r\n :80")
        refused("P12", "Orange", null, UrlProblem.WhitespaceOrControl, "http://127.0.0.1:25/%0D%0AHELO orange.tw%0D%0AMAIL FROM...")
        urls("P12b", "Orange", null, Outcome.DeniedAddress, "loopback", "127.0.0.1", "http://127.0.0.1:25/%0D%0AHELO%20orange.tw%0D%0AMAIL%20FROM:%3Cx@y.com%3E")
        refused("P13", "Orange", null, UrlProblem.Backslash, "http://or\\097nge.tw/")
        // "‍" is U+200D ZERO WIDTH JOINER (invisible, so escaped); the visible non-ASCII text stays literal.
        refused("P14", "HT; PATT; Orange", null, UrlProblem.HostEncoding, "http://ⓔⓧⓐⓜⓟⓛⓔ.ⓒⓞⓜ/", "http://①②⑦.⓪.⓪.⓪/", "http://127。0。0。1/",
            "http://attacker。com/", "http://ⒶⓉⓉⒶⒸⓀⒺⓡ.Ⓒⓞⓜ/", "http://a‍b.example/", "http://wordpreß.com/")
        refused("P15", "HT; GHSA-v5mv-p594-2x33", "F1", UrlProblem.HostEncoding, "http://%31%32%37.0.0.1/", "http://127%2E0%2E0%2E1/",
            "http://127%E3%80%820%E3%80%820%E3%80%821/", "http://attacker%00.com/", "http://attacker%E3%80%82com/", "http://localhost%2e/", "http://127.0.0.%31/")
        refused("P16", "GHSA-v5mv-p594-2x33", null, UrlProblem.UserInfo, "http://blocked.example.com@127.0.0.1/")
        refused("P17", "HT", null, UrlProblem.Port, "http://localhost:+11211aaa/", "http://localhost:00011211aaaa/", "http://example.com:+80/",
            "http://example.com:0/", "http://example.com:65536/", "http://example.com:80:80/")
        refused("P18", "HT; GHSA-vmxc-h2x2-jmf3", "F1", UrlProblem.Host, "http://[fe80::1%25eth0]/", "http://[fe80::a9ff:fe00:1%25en0]/",
            "http://[fe80::1%eth0]/", "http://[fd00:ec2::254%251]/")
        refused("P19", "GHSA-f65p-4m7j-42xc; WHATWG", null, UrlProblem.Host, "http://[::not-valid]/", "http://[fc00::not-hex]/",
            "http://[fe80::not-hex]/", "http://[::1/", "http://[::ffff:0177.0.0.1]/", "http://[::ffff:127.1]/", "http://[1:2:3:4:5:6:7::8]/")
        //endregion

        //region stage 2: the port rule (3.1 P20)
        listOf(6379, 11211, 25, 22, 2375, 2379).forEach {
            add(Vector("P20", "Orange; PATT; PATT-cloud", null, "http://example.com:$it/", null, "example.com", Outcome.Port, null))
        }
        //endregion

        //region stage 2: HostForm (3.2)
        hosts("H01", "PATT; HT; GHSA-r46f-3rpw-hxrv", "F1", Outcome.HostForm, null, "2130706433", "3232235521", "3232235777", "2852039166", "0")
        hosts("H02", "PATT; HT; CVE-2024-29415; node-ip#150", "F1", Outcome.HostForm, null, "127.1", "127.0.1", "127.1.1", "169.254.43518")
        hosts("H03", "HT; CVE-2021-28918; CVE-2021-29418; CVE-2021-29921; CVE-2021-33571; CVE-2026-69192", "F1,F10", Outcome.HostForm, null,
            "0177.0.0.1", "0177.0000.0000.0001", "00000177.00000000.00000000.00000001", "012.1.2.3", "0127.0.0.1", "0127.0.0.01", "012.0.0.1", "031.5.5.5", "01.2.3.4")
        hosts("H04", "HT; CVE-2024-29415; CVE-2020-28360", "F1,F10", Outcome.HostForm, null,
            "017700000001", "01200034567", "127.000000000000.1", "127.000000000000000.1", "127.000.000.001", "127.0.0.01", "00000127.0.0.1",
            "08.0.0.1", "0000.0000.0000.0000", "0000.0000")
        hosts("H05", "HT; PATT; CVE-2023-42282", "F1,F10", Outcome.HostForm, null,
            "0x7f.1", "0x7f000001", "0xc0a80101", "0xa9fea9fe", "0xc0a80014", "0x7f.0x00.0x00.0x01", "0x0000007f.0x00000000.0x00000000.0x00000001")
        spelled("H05", "HT (published upper-case)", "F1,F10", Outcome.HostForm, null, "0xA9.254.0251.0376", "0xa9.254.0251.0376")
        hosts("H06", "PATT; node-ip#150", null, Outcome.HostForm, null, "o177.0.0.1", "0o177.0.0.1", "q177.0.0.1", "127.00.0x1", "127.0.0x0.1")
        hosts("H07", "WHATWG", "F1", Outcome.HostForm, null, "127.0.0.1.", "127.0.0.0x", "127.0.0.09", "0x", "1.2.3.4.5", "256.1.1.1", "999.999.999.999", "1.1")
        hosts("H08", "WHATWG", "F1", Outcome.HostForm, null, "example.0x7f", "a.b.1", "localhost.1", "x.0x")
        //endregion

        //region stage 2: DeniedAddress (3.3); the class is the most specific table row's
        hosts("D01", "PATT; HT", "F1", Outcome.DeniedAddress, "loopback", "127.0.0.1", "127.127.127.127", "127.0.1.3", "127.0.0.0")
        hosts("D02", "PATT; HT; GHSA-j5h6-vqc3-phqh", "F1", Outcome.DeniedAddress, "this-host", "0.0.0.0")
        hosts("D03", "PATT; CVE-2020-28360", "F1", Outcome.DeniedAddress, "private-use", "10.0.0.5", "172.16.0.1", "192.168.1.1", "192.168.0.1")
        hosts("D04", "PATT-cloud; HT-cloud", "F1", Outcome.DeniedAddress, "link-local", "169.254.169.254", "169.254.170.2", "169.254.170.23")
        hosts("D05", "PATT-cloud; Bifrost", "F1", Outcome.DeniedAddress, "shared-address-space", "100.64.0.1", "100.100.100.200")
        hosts("D06", "PATT-cloud (Oracle); CPython 113171", null, Outcome.DeniedAddress, "ietf-protocol-assignments", "192.0.0.192")
        hosts("D06", "PATT-cloud", null, Outcome.DeniedAddress, "ipv4-dummy-address", "192.0.0.8")
        hosts("D06", "PATT-cloud", null, Outcome.DeniedAddress, "pcp-anycast", "192.0.0.9")
        hosts("D06", "PATT-cloud", null, Outcome.DeniedAddress, "turn-anycast", "192.0.0.10")
        hosts("D06", "PATT-cloud", null, Outcome.DeniedAddress, "nat64-dns64-discovery", "192.0.0.170", "192.0.0.171")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "this-network", "0.0.0.7", "0.255.255.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "shared-address-space", "100.127.255.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "as112-v4", "192.31.196.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "amt", "192.52.193.0")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "deprecated-6to4-relay-anycast", "192.88.99.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "direct-delegation-as112", "192.175.48.1")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "benchmarking", "198.19.255.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "documentation", "198.51.100.0", "203.0.113.255")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "reserved", "240.0.0.1")
        hosts("D07", "private-ip 840664c", "F10", Outcome.DeniedAddress, "limited-broadcast", "255.255.255.255")
        hosts("D08", "CVE-2025-8020; GHSA-p4hc-9pjh-55c8", null, Outcome.DeniedAddress, "multicast", "224.0.0.1", "239.255.255.255")
        hosts("D09", "Fedify", null, Outcome.DeniedAddress, "benchmarking", "198.18.0.1")
        hosts("D09", "Fedify", null, Outcome.DeniedAddress, "documentation", "192.0.2.1")
        spelled("D10", "PATT; HT", "F1,F10", Outcome.DeniedAddress, "loopback", "::ffff:127.0.0.1", "127.0.0.1")
        spelled("D10", "PATT; HT", "F1,F10", Outcome.DeniedAddress, "loopback", "::ffff:7f00:1", "127.0.0.1")
        spelled("D10", "PATT; HT", "F1,F10", Outcome.DeniedAddress, "loopback", "0:0:0:0:0:ffff:7f00:1", "127.0.0.1")
        spelled("D10", "CVE-2024-29415", "F1,F10", Outcome.DeniedAddress, "loopback", "::fFFf:127.0.0.1", "127.0.0.1")
        spelled("D10", "PATT-cloud", "F1,F10", Outcome.DeniedAddress, "link-local", "::ffff:a9fe:a9fe", "169.254.169.254")
        spelled("D10", "HT", "F1,F10", Outcome.DeniedAddress, "this-host", "::ffff:0:0", "0.0.0.0")
        spelled("D10", "HT", "F1,F10", Outcome.DeniedAddress, "private-use", "::ffff:a00:1", "10.0.0.1")
        spelled("D11", "PATT; HT", "F1,F10", Outcome.DeniedAddress, "not-global-unicast", "0000::1", "::1")
        spelled("D11", "HT", "F1,F10", Outcome.DeniedAddress, "not-global-unicast", "0:0:0:0:0:0:0:1", "::1")
        spelled("D11", "CVE-2024-29415", "F1,F10", Outcome.DeniedAddress, "not-global-unicast", "000:0:0000::01", "::1")
        spelled("D11", "PATT", "F1,F10", Outcome.DeniedAddress, "not-global-unicast", "0:0:0:0:0:0:0:0", "::")
        hosts("D11", "PATT", "F1,F10", Outcome.DeniedAddress, "not-global-unicast", "::1", "::")
        spelled("D12", "Symfony; pydantic-ai; Cloudreve", "F1", Outcome.DeniedAddress, "not-global-unicast", "::127.0.0.1", "::7f00:1")
        hosts("D12", "Symfony; pydantic-ai; Cloudreve", "F1", Outcome.DeniedAddress, "not-global-unicast", "::7f00:1")
        spelled("D13", "pydantic-ai CVE-2026-48782", null, Outcome.DeniedAddress, "not-global-unicast", "::ffff:0:127.0.0.1", "::ffff:0:7f00:1")
        spelled("D14", "CC-Tweaked; Open WebUI; Dozzle; Symfony", "F1", Outcome.DeniedAddress, "loopback", "64:ff9b::127.0.0.1", "64:ff9b::7f00:1")
        hosts("D14", "AVideo CVE-2026-82648", "F1", Outcome.DeniedAddress, "loopback", "64:ff9b::7f00:1")
        hosts("D14", "Dozzle", "F1", Outcome.DeniedAddress, "link-local", "64:ff9b::a9fe:a9fe")
        spelled("D14", "Symfony", "F1", Outcome.DeniedAddress, "private-use", "64:ff9b::c0a8:0101", "64:ff9b::c0a8:101")
        hosts("D15", "ToolHive; Symfony; pydantic-ai", null, Outcome.DeniedAddress, "not-global-unicast", "64:ff9b:1::7f00:1", "64:ff9b:1::a00:1")
        hosts("D16", "Symfony; Dozzle; Bifrost; Weblate", "F1", Outcome.DeniedAddress, "6to4", "2002:7f00:1::", "2002:a9fe:a9fe::", "2002:5db8:d822::")
        hosts("D17", "Symfony; Dozzle", null, Outcome.DeniedAddress, "teredo", "2001::1", "2001:0:4136:e378:8000:63bf:3fff:fdd2")
        hosts("D18", "HT; HT-cloud; openclaw GHSA-h97f-6pqj-q452", "F1", Outcome.DeniedAddress, "not-global-unicast",
            "fd00::1", "fd00:ec2::254", "fc00::1", "fe80::1", "fec0::1", "ff02::1", "ff05::1:3")
        hosts("D19", "Python 3.13 notes; openclaw", null, Outcome.DeniedAddress, "documentation", "2001:db8::1", "3fff::1")
        hosts("D19", "openclaw", null, Outcome.DeniedAddress, "benchmarking", "2001:2::1")
        hosts("D19", "openclaw", null, Outcome.DeniedAddress, "direct-delegation-as112", "2620:4f:8000::1")
        //endregion

        //region design gaps closed by two rows that are not registry rows (4)
        urls("G1", "HT-cloud; Microsoft Learn", null, Outcome.DeniedAddress, "azure-wireserver", "168.63.129.16", "http://168.63.129.16/?comp=versions")
        hosts("G2", "openclaw GHSA-8cp7-rp8r-mg77; pydantic-ai GHSA-cg7w-rg45-pc59", null, Outcome.DeniedAddress, "isatap",
            "2a01:4f8:1:1:0:5efe:7f00:1", "2a01:4f8:1:1:200:5efe:7f00:1")
        //endregion

        //region stage 2: ReservedName (3.4)
        hosts("R01", "PATT; HT", "F1", Outcome.ReservedName, null, "localhost", "localhost.", "foo.localhost")
        spelled("R01", "PATT", "F1", Outcome.ReservedName, null, "PayloadsAllTheThings.localhost", "payloadsallthethings.localhost")
        hosts("R02", "PATT", null, Outcome.ReservedName, null, "ip6-localhost", "ip6-loopback")
        hosts("R03", "PATT-cloud; HT-cloud", null, Outcome.ReservedName, null, "metadata", "rancher-metadata", "metadata.google.internal")
        hosts("R04", "the rule's own members", null, Outcome.ReservedName, null, "printer.local", "router.home.arpa", "host.localdomain", "intranet")
        //endregion

        //region stage 2: names the resolver guard judges; no earlier rule fires (3.5) and controls (3.6)
        hosts("N01", "PATT; HT", null, Outcome.Allowed, null, "localtest.me", "localh.st", "bugbounty.dod.network", "mail.ebc.apple.com", "spoofed.burpcollaborator.net")
        hosts("N02", "PATT; HT; CVE-2026-55526", null, Outcome.Allowed, null, "127.0.0.1.nip.io", "169.254.169.254.nip.io", "company.127.0.0.1.nip.io",
            "customer1.app.localhost.my.company.127.0.0.1.nip.io", "1ynrnhl.xip.io")
        hosts("N03", "PATT", null, Outcome.Allowed, null, "make-1.2.3.4-rebind-169.254-169.254-rr.1u.ms")
        hosts("N04", "PATT-cloud", null, Outcome.Allowed, null, "metadata.packet.net")
        urls("N05", "Orange", null, Outcome.Allowed, null, "google.com", "http://google.com#@evil.com/")
        hosts("N06", "CVE-2026-25534", null, Outcome.Allowed, null, "evil_host.example")
        hosts("C01", "controls", null, Outcome.Allowed, null, "93.184.216.34", "1.1.1.1", "8.8.8.8", "64:ff9b::5db8:d822", "2606:4700:4700::1111")
        spelled("C01", "controls", null, Outcome.Allowed, null, "::ffff:93.184.216.34", "93.184.216.34")
        listOf(80, 443, 8080, 8443).forEach {
            add(Vector("C02", "controls", null, "http://example.com:$it/", null, "example.com", Outcome.Allowed, null))
        }
        //endregion
    }
}
