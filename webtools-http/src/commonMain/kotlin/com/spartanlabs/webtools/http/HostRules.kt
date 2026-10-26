package com.spartanlabs.webtools.http

/** What [HostRules.classify] says about a host. */
internal sealed class HostVerdict {
    /**
     * The host is a strict IP literal; judge [address] with the address table.
     *
     * @property address the literal's bytes: 4 for a dotted quad, 16 for IPv6 (a mapped literal is not collapsed).
     */
    class Literal(val address: ByteArray) : HostVerdict()

    /** A name no host rule fires on; the resolver guard judges what it resolves to. */
    object Name : HostVerdict()

    /**
     * The host is refused for [reason].
     *
     * @property reason `HostForm` or `ReservedName`.
     */
    class Refused(val reason: RefusalReason) : HostVerdict()
}

/** The host rules of the `Untrusted` policy, as pure functions over strings. */
internal object HostRules {
    // localhost names are loopback by RFC 6761; .local is mDNS; .internal and home.arpa are reserved for private use;
    // localdomain is the default search domain glibc gives a host with no domain in resolv.conf, so "host.localdomain" names
    // a loopback or LAN host on such a machine. These rules are fail-fast and diagnostic: the resolver guard denies whatever
    // they resolve to.
    private val RESERVED: List<String> = listOf("localhost", "local", "internal", "home.arpa", "localdomain")

    /**
     * Classifies [host]; total on any string. Letters are folded ASCII-only first. In order: empty or a character outside
     * `a-z 0-9 - _ . :` is `HostForm`; with `:` a strict IPv6 literal or `HostForm`; digits and dots only a strict dotted
     * quad or `HostForm`; an empty label (after removing one trailing dot) is `HostForm`; a last label that is all digits or
     * `0x` plus zero or more hex digits is `HostForm`; a single label, a reserved name or subdomain, or a policy suffix or
     * subdomain is `ReservedName`; anything else is a [HostVerdict.Name].
     *
     * @param host the host text; `Url.host`, or any string a test feeds directly.
     * @param extraSuffixes the policy's canonical suffixes.
     * @return the verdict.
     */
    fun classify(host: String, extraSuffixes: Set<String>): HostVerdict {
        val text = buildString(host.length) { host.forEach { append(HttpSyntax.asciiLower(it)) } }
        if (text.isEmpty() || !text.all { isHostChar(it) }) return refused(RefusalReason.HostForm)
        if (':' in text) return StrictIp.parseV6(text)?.let { HostVerdict.Literal(it) } ?: refused(RefusalReason.HostForm)
        if (text.all { it == '.' || it in '0'..'9' }) {
            return StrictIp.parseV4(text)?.let { HostVerdict.Literal(it) } ?: refused(RefusalReason.HostForm)
        }
        val name = text.removeSuffix(".")
        val labels = name.split('.')
        if (labels.any { it.isEmpty() }) return refused(RefusalReason.HostForm)
        if (endsInNumber(labels.last())) return refused(RefusalReason.HostForm)
        val reserved = labels.size == 1 || RESERVED.any { matchesSuffix(name, it) } || extraSuffixes.any { matchesSuffix(name, it) }
        return if (reserved) refused(RefusalReason.ReservedName) else HostVerdict.Name
    }

    /**
     * True when [name] is [suffix] or ends with `.` plus [suffix] (a label boundary).
     *
     * @param name a canonical host name without its trailing dot.
     * @param suffix a canonical suffix (a reserved name or a policy suffix).
     * @return whether [suffix] is [name] or a parent domain of it.
     */
    fun matchesSuffix(name: String, suffix: String): Boolean = name == suffix || name.endsWith(".$suffix")

    private fun refused(reason: RefusalReason): HostVerdict = HostVerdict.Refused(reason)

    private fun isHostChar(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == ':'

    // The WHATWG "ends in a number" test: all ASCII digits, or "0x" followed by zero or more hex digits (zero counts).
    private fun endsInNumber(label: String): Boolean =
        label.all { it in '0'..'9' } || (label.startsWith("0x") && label.drop(2).all { it in '0'..'9' || it in 'a'..'f' })
}
