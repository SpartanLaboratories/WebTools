package com.spartanlabs.webtools.http

// The default Untrusted port set. A top-level value of this file, not state of the sealed base's companion: a policy
// instance held there would make the base's class initialiser construct a subclass (a class-initialisation cycle).
private val DEFAULT_UNTRUSTED_PORTS: Set<Int> = setOf(80, 443, 8080, 8443)

// Constant messages naming the property; none echoes a value.
private const val PINNED_HOST_MESSAGE = "pinnedHosts must hold only host names or IP literals, without a port, path or user-info."
private const val SUFFIX_MESSAGE =
    "extraDeniedHostSuffixes must hold only dotted host-name suffixes (no IP literal, no leading or trailing dot)."
private const val PORTS_EMPTY_MESSAGE = "allowedPorts must not be empty; use null to allow any port."
private const val PORTS_RANGE_MESSAGE = "allowedPorts must hold only ports from 1 to 65535."

/**
 * Which URLs and addresses a fetch may reach, and the [TransportSettings] that go with that choice. A closed pair:
 * [Trusted] for a known upstream, [Untrusted] for a URL a third party supplied.
 *
 * **Stable Core.** `sealed` because the closed pair is the contract; build one with [trusted] or [untrusted]. There is no
 * parameter that lowers `Untrusted`'s protection: to fetch from a private address use [Trusted]. The policy decides; a
 * transport enforces. A `when` over a policy needs no `else`.
 */
public sealed class OutboundUrlPolicy {
    /** The caps, timeouts and concurrency limits that go with this policy. */
    public abstract val settings: TransportSettings

    /**
     * The policy for a known upstream: it checks the scheme, optionally pins the host, and applies the downgrade rule. It
     * does no address filtering, has no name rule and no port rule, so a private address is fetchable.
     *
     * **Stable Core.** Equality is on every property. `toString()` prints the number of pinned hosts, never a host.
     */
    public class Trusted internal constructor(
        /** The hosts a hop may reach, canonical (lower-case; IPv6 without brackets); empty means any host. Exact match with `Url.host`. */
        public val pinnedHosts: Set<String>,
        /** Whether `http` is allowed; false means `https` only. */
        public val allowCleartext: Boolean,
        override val settings: TransportSettings,
    ) : OutboundUrlPolicy() {
        override fun equals(other: Any?): Boolean =
            other is Trusted && other.pinnedHosts == pinnedHosts && other.allowCleartext == allowCleartext && other.settings == settings

        override fun hashCode(): Int = (pinnedHosts.hashCode() * 31 + allowCleartext.hashCode()) * 31 + settings.hashCode()

        override fun toString(): String = "Trusted(pinnedHosts=${pinnedHosts.size}, allowCleartext=$allowCleartext, settings=$settings)"

        /**
         * Collects the properties of a [Trusted]. Not thread-safe; use it only inside the factory's lambda. A property holds
         * the caller's reference until the policy is built, which copies it.
         *
         * **Stable Core.**
         */
        public class Builder internal constructor(from: Trusted) {
            /**
             * The hosts to pin, as host names or IP literals (an IPv6 literal with or without brackets); empty means any host.
             * Canonicalised when the policy is built: lower-cased, IPv6 without brackets.
             */
            public var pinnedHosts: Set<String> = from.pinnedHosts

            /** Whether `http` is allowed; false (the default) means `https` only. */
            public var allowCleartext: Boolean = from.allowCleartext

            /** The settings as a whole value; the default is [TransportSettings.TrustedDefaults]. */
            public var settings: TransportSettings = from.settings

            /**
             * Changes [settings] with a block that starts from its current value. The order matters: `settings = X` followed
             * by `settings { }` modifies `X`.
             *
             * @param configure assigns the settings to change.
             * @throws IllegalArgumentException as [TransportSettings] does.
             */
            public fun settings(configure: TransportSettings.Builder.() -> Unit) {
                settings = TransportSettings(settings, configure)
            }

            internal fun build(): Trusted {
                val hosts = pinnedHosts.map { canonicalPinnedHost(it).getOrElse { throw IllegalArgumentException(PINNED_HOST_MESSAGE) } }
                return Trusted(hosts.toSet(), allowCleartext, settings)
            }
        }
    }

    /**
     * The policy for a URL a third party supplied. Per hop it refuses a lenient or malformed host form, a reserved name, a
     * denied IP literal and a port outside [allowedPorts]; for every resolved address and every connect it refuses a denied
     * address; it never uses a proxy; and it refuses a request that carries a sensitive header (`Authorization`,
     * `Proxy-Authorization`, `Cookie`, `Set-Cookie` or any header added as sensitive).
     *
     * **Stable Core.** Every parameter can only add a denial. Equality is on every property. `toString()` prints the port
     * set and the number of denied ranges and suffixes, never their values.
     */
    public class Untrusted internal constructor(
        /** The ports a hop may use; `null` means any port. The default is 80, 443, 8080 and 8443. */
        public val allowedPorts: Set<Int>?,
        /**
         * Address blocks denied in addition to the built-in table. An IPv4 block also matches the IPv4 address embedded in
         * an IPv4-mapped or NAT64 well-known (`64:ff9b::/96`) address; an IPv6 block matches IPv6 addresses. A deployment
         * behind its own NAT64 prefix, or with an internal service on a public address, adds it here: neither is
         * knowable to a library.
         */
        public val extraDeniedRanges: Set<CidrBlock>,
        /** Host-name suffixes refused as a reserved name, canonical (lower-case): the name itself and its subdomains. */
        public val extraDeniedHostSuffixes: Set<String>,
        /** How a resolver answer with both allowed and denied addresses is treated; `Reject` by default. */
        public val mixedAnswers: MixedAnswers,
        override val settings: TransportSettings,
    ) : OutboundUrlPolicy() {
        override fun equals(other: Any?): Boolean =
            other is Untrusted && other.allowedPorts == allowedPorts && other.extraDeniedRanges == extraDeniedRanges &&
                other.extraDeniedHostSuffixes == extraDeniedHostSuffixes && other.mixedAnswers == mixedAnswers &&
                other.settings == settings

        override fun hashCode(): Int {
            var hash = allowedPorts.hashCode()
            hash = 31 * hash + extraDeniedRanges.hashCode()
            hash = 31 * hash + extraDeniedHostSuffixes.hashCode()
            hash = 31 * hash + mixedAnswers.hashCode()
            hash = 31 * hash + settings.hashCode()
            return hash
        }

        override fun toString(): String =
            "Untrusted(allowedPorts=${allowedPorts?.sorted() ?: "any"}, extraDeniedRanges=${extraDeniedRanges.size}, " +
                "extraDeniedHostSuffixes=${extraDeniedHostSuffixes.size}, mixedAnswers=$mixedAnswers, settings=$settings)"

        /**
         * Collects the properties of an [Untrusted]. Not thread-safe; use it only inside the factory's lambda. Extend a set
         * with `+=` (`extraDeniedRanges += block`).
         *
         * **Stable Core.**
         */
        public class Builder internal constructor(from: Untrusted) {
            /** The allowed ports: `null` for any port, otherwise a non-empty set of ports 1..65535. The default is 80, 443, 8080, 8443. */
            public var allowedPorts: Set<Int>? = from.allowedPorts

            /** Address blocks denied in addition to the built-in table; empty by default. See [Untrusted.extraDeniedRanges]. */
            public var extraDeniedRanges: Set<CidrBlock> = from.extraDeniedRanges

            /**
             * Host-name suffixes to refuse as reserved names (`corp.example` refuses `corp.example` and `a.corp.example`);
             * empty by default. Canonicalised when the policy is built: lower-case, no leading or trailing dot, not an IP literal.
             */
            public var extraDeniedHostSuffixes: Set<String> = from.extraDeniedHostSuffixes

            /** How a mixed resolver answer is treated; [MixedAnswers.Reject] by default. */
            public var mixedAnswers: MixedAnswers = from.mixedAnswers

            /** The settings as a whole value; the default is [TransportSettings.UntrustedDefaults]. */
            public var settings: TransportSettings = from.settings

            /**
             * Changes [settings] with a block that starts from its current value; the order matters, as in [Trusted.Builder.settings].
             *
             * @param configure assigns the settings to change.
             * @throws IllegalArgumentException as [TransportSettings] does.
             */
            public fun settings(configure: TransportSettings.Builder.() -> Unit) {
                settings = TransportSettings(settings, configure)
            }

            internal fun build(): Untrusted {
                val ports = allowedPorts?.let { checkedPorts(it) }
                val suffixes = extraDeniedHostSuffixes.map { canonicalHostSuffix(it).getOrElse { throw IllegalArgumentException(SUFFIX_MESSAGE) } }
                return Untrusted(ports, extraDeniedRanges.toSet(), suffixes.toSet(), mixedAnswers, settings)
            }
        }
    }

    /** The factories. */
    public companion object {
        /**
         * Builds a [Trusted] policy from the defaults (no pins, `https` only, [TransportSettings.TrustedDefaults]) and [configure].
         *
         * @param configure assigns the properties to change; empty by default, for the defaults.
         * @return the policy.
         * @throws IllegalArgumentException if a pinned host is not a host name or IP literal, or a `settings { }` block breaks a settings rule.
         */
        public fun trusted(configure: Trusted.Builder.() -> Unit = {}): Trusted =
            trusted(Trusted(emptySet(), false, TransportSettings.TrustedDefaults), configure)

        /**
         * Builds a [Trusted] policy that starts from [from].
         *
         * @param from the policy to start from; never modified.
         * @param configure assigns the properties to change.
         * @return the new policy.
         * @throws IllegalArgumentException as the other overload does.
         */
        public fun trusted(from: Trusted, configure: Trusted.Builder.() -> Unit): Trusted =
            Trusted.Builder(from).apply(configure).build()

        /**
         * Builds an [Untrusted] policy from the defaults (ports 80, 443, 8080, 8443; no extra ranges or suffixes;
         * [MixedAnswers.Reject]; [TransportSettings.UntrustedDefaults]) and [configure].
         *
         * @param configure assigns the properties to change; empty by default, for the defaults.
         * @return the policy.
         * @throws IllegalArgumentException if `allowedPorts` is empty or holds a port outside 1..65535, a suffix is not a dotted host-name suffix, or a `settings { }` block breaks a settings rule.
         */
        public fun untrusted(configure: Untrusted.Builder.() -> Unit = {}): Untrusted =
            untrusted(Untrusted(DEFAULT_UNTRUSTED_PORTS, emptySet(), emptySet(), MixedAnswers.Reject, TransportSettings.UntrustedDefaults), configure)

        /**
         * Builds an [Untrusted] policy that starts from [from].
         *
         * @param from the policy to start from; never modified.
         * @param configure assigns the properties to change.
         * @return the new policy.
         * @throws IllegalArgumentException as the other overload does.
         */
        public fun untrusted(from: Untrusted, configure: Untrusted.Builder.() -> Unit): Untrusted =
            Untrusted.Builder(from).apply(configure).build()
    }
}

private fun checkedPorts(ports: Set<Int>): Set<Int> {
    require(ports.isNotEmpty()) { PORTS_EMPTY_MESSAGE }
    require(ports.all { it in 1..65535 }) { PORTS_RANGE_MESSAGE }
    return ports.toSet()
}

// A pinned host as canonicalHost reads it. An entry holding ':' is an IPv6 literal, with or without brackets (a lone
// bracket is refused); anything else is a name. The result is what Url.host would print for the same host.
internal fun canonicalPinnedHost(entry: String): Result<String> {
    val opens = entry.startsWith('[')
    if (opens != entry.endsWith(']')) return Result.failure(IllegalArgumentException(PINNED_HOST_MESSAGE))
    val inner = if (opens) entry.substring(1, entry.length - 1) else entry
    return when {
        ':' in inner -> canonicalHost("[$inner]")
        opens -> Result.failure(IllegalArgumentException(PINNED_HOST_MESSAGE))
        else -> canonicalHost(inner)
    }
}

// A suffix is a dotted name: refused when empty, when it holds ':', '[' or ']', starts or ends with '.', is digits and dots
// only, or fails canonicalHost. A single label ("corp") is allowed.
internal fun canonicalHostSuffix(entry: String): Result<String> {
    val refused = entry.isEmpty() || entry.any { it == ':' || it == '[' || it == ']' } || entry.startsWith('.') ||
        entry.endsWith('.') || entry.all { it == '.' || it in '0'..'9' }
    return if (refused) Result.failure(IllegalArgumentException(SUFFIX_MESSAGE)) else canonicalHost(entry)
}

// Derives a policy with other settings through the from factories; the call facades use it to raise concurrency to the
// budget's burst. The parameter is copied so the lambdas' `settings` is the builder's property.
internal fun OutboundUrlPolicy.withSettings(settings: TransportSettings): OutboundUrlPolicy {
    val replacement = settings
    return when (this) {
        is OutboundUrlPolicy.Trusted -> OutboundUrlPolicy.trusted(this) { this.settings = replacement }
        is OutboundUrlPolicy.Untrusted -> OutboundUrlPolicy.untrusted(this) { this.settings = replacement }
    }
}
