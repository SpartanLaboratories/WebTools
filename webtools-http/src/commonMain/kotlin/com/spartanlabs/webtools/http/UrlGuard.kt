package com.spartanlabs.webtools.http

/**
 * A refusal: the coarse [reason] and, for `DeniedAddress` only, the matched range's [rangeClass] (for logs; never an
 * address or a range). Value equality; `toString()` prints the reason only.
 */
internal class GuardRefusal(val reason: RefusalReason, val rangeClass: String?) {
    init {
        require(rangeClass == null || reason == RefusalReason.DeniedAddress) { "A range class belongs to DeniedAddress only." }
    }

    override fun equals(other: Any?): Boolean = other is GuardRefusal && other.reason == reason && other.rangeClass == rangeClass
    override fun hashCode(): Int = reason.hashCode() * 31 + (rangeClass?.hashCode() ?: 0)
    override fun toString(): String = "GuardRefusal(${reason.name})"
}

/** The outcome of [UrlGuard.admitRedirect]. */
internal sealed class RedirectAdmission {
    /** The target parsed and passed every rule; follow [url]. */
    class Admitted(val url: Url) : RedirectAdmission()

    /** The target was refused for [refusal]. */
    class Refused(val refusal: GuardRefusal) : RedirectAdmission()
}

/** The outcome of [UrlGuard.judgeAnswer]. */
internal sealed class AnswerVerdict {
    /** Use the addresses at [indices], in answer order. Empty only for an empty answer. */
    class Allowed(val indices: List<Int>) : AnswerVerdict()

    /** The answer is refused for [refusal]. */
    class Refused(val refusal: GuardRefusal) : AnswerVerdict()
}

/**
 * Applies a policy to a hop, a request, a redirect target and a resolved address. Pure and immutable after construction: no
 * I/O, safe to share between threads, never logs.
 */
internal class UrlGuard(val policy: OutboundUrlPolicy, val relaxation: GuardRelaxation = GuardRelaxation.None) {

    /**
     * Judges one hop, in the order scheme, user-info, host form, IP literal, reserved name, port, pin, downgrade; the first
     * refusal wins. The user-info step is a no-op here because a [Url] never carries any.
     *
     * @param url the hop's URL.
     * @param previous the previous hop's URL, or `null` for the first hop.
     * @return the refusal, or `null` when the hop is allowed.
     */
    fun checkHop(url: Url, previous: Url?): GuardRefusal? = when (policy) {
        is OutboundUrlPolicy.Trusted -> checkTrustedHop(policy, url, previous)
        is OutboundUrlPolicy.Untrusted -> checkUntrustedHop(policy, url, previous)
    }

    /**
     * Judges a request before hop 1: [checkHop] of its URL, then, for `Untrusted`, any header with `isSensitive`.
     *
     * @return the refusal, or `null` when the request may be sent.
     */
    fun checkRequest(request: HttpRequest): GuardRefusal? =
        checkHop(request.url, null) ?: if (policy is OutboundUrlPolicy.Untrusted && request.headers.entries().any { it.isSensitive }) {
            GuardRefusal(RefusalReason.SensitiveHeader, null)
        } else {
            null
        }

    /**
     * Judges a redirect target the transport resolved.
     *
     * @param target the absolute target text, as `HttpUrl.resolve` printed it.
     * @param previous the hop that returned the redirect.
     * @return [RedirectAdmission.Admitted] with the parsed URL, or [RedirectAdmission.Refused]: a parse failure maps
     *   `UserInfo` to `UserInfo`, `Scheme` to `Scheme`, `Port` to `Port` and every other problem to `HostForm`.
     */
    fun admitRedirect(target: String, previous: Url): RedirectAdmission =
        Url.parse(target).fold(
            onSuccess = { url -> checkHop(url, previous)?.let { RedirectAdmission.Refused(it) } ?: RedirectAdmission.Admitted(url) },
            onFailure = { RedirectAdmission.Refused(GuardRefusal(parseRefusal(it), null)) },
        )

    /**
     * Judges one resolved or connected address.
     *
     * @param address the address bytes (4 or 16; any other length is denied).
     * @return `null` for `Trusted` (always) or an allowed address; otherwise `DeniedAddress` with the range class.
     */
    fun checkAddress(address: ByteArray): GuardRefusal? = when (policy) {
        is OutboundUrlPolicy.Trusted -> null
        is OutboundUrlPolicy.Untrusted -> checkUntrustedAddress(policy, address)
    }

    /**
     * Judges a whole resolver answer. `Reject` refuses it at its first denied address; `DropDenied` keeps the allowed
     * indices in order and refuses (with the first refusal) when none is left; `Trusted` allows every index. An empty list is
     * `Allowed(emptyList())`: not the guard's to judge.
     */
    fun judgeAnswer(addresses: List<ByteArray>): AnswerVerdict {
        if (addresses.isEmpty()) return AnswerVerdict.Allowed(emptyList())
        val refusals = addresses.map { checkAddress(it) }
        val allowed = refusals.indices.filter { refusals[it] == null }
        val first = refusals.firstOrNull { it != null }
        return when {
            first == null -> AnswerVerdict.Allowed(addresses.indices.toList())
            policy is OutboundUrlPolicy.Untrusted && policy.mixedAnswers == MixedAnswers.DropDenied && allowed.isNotEmpty() ->
                AnswerVerdict.Allowed(allowed)
            else -> AnswerVerdict.Refused(first)
        }
    }

    //region Trusted
    private fun checkTrustedHop(policy: OutboundUrlPolicy.Trusted, url: Url, previous: Url?): GuardRefusal? = when {
        url.scheme == "http" && !policy.allowCleartext -> GuardRefusal(RefusalReason.Scheme, null)
        policy.pinnedHosts.isNotEmpty() && url.host !in policy.pinnedHosts -> GuardRefusal(RefusalReason.HostNotPinned, null)
        else -> checkDowngrade(policy, url, previous)
    }
    //endregion

    //region Untrusted
    private fun checkUntrustedHop(policy: OutboundUrlPolicy.Untrusted, url: Url, previous: Url?): GuardRefusal? {
        // The relaxation exempts the host exactly "localhost" from the reserved-name rule, unless a policy suffix still matches it.
        val exemptLocalhost = relaxation.allowLoopback && url.host == LOCALHOST &&
            policy.extraDeniedHostSuffixes.none { HostRules.matchesSuffix(LOCALHOST, it) }
        val verdict = if (exemptLocalhost) HostVerdict.Name else HostRules.classify(url.host, policy.extraDeniedHostSuffixes)
        val byHost = when (verdict) {
            is HostVerdict.Refused -> GuardRefusal(verdict.reason, null)
            is HostVerdict.Literal -> checkUntrustedAddress(policy, verdict.address)
            is HostVerdict.Name -> null
        }
        return byHost ?: checkPort(policy, url) ?: checkDowngrade(policy, url, previous)
    }

    private fun checkPort(policy: OutboundUrlPolicy.Untrusted, url: Url): GuardRefusal? {
        val ports = policy.allowedPorts ?: return null
        if (url.port in ports) return null
        return if (relaxation.allowLoopback && url.host in LOOPBACK_HOSTS) null else GuardRefusal(RefusalReason.Port, null)
    }

    private fun checkUntrustedAddress(policy: OutboundUrlPolicy.Untrusted, address: ByteArray): GuardRefusal? {
        // The relaxation exempts the table for loopback only; extraDeniedRanges stay live.
        val byTable = if (relaxation.allowLoopback && isLoopback(address)) null else AddressRanges.deny(address)
        if (byTable != null) return GuardRefusal(RefusalReason.DeniedAddress, byTable.rangeClass)
        return if (policy.extraDeniedRanges.any { matchesExtra(it, address) }) GuardRefusal(RefusalReason.DeniedAddress, EXTRA_CLASS) else null
    }

    // 127.0.0.0/8, ::1, and a mapped address whose embedded IPv4 is in 127.0.0.0/8. NAT64 is not exempt.
    private fun isLoopback(a: ByteArray): Boolean = when {
        a.size == 4 -> a[0].toInt() == 127
        a.size == 16 && AddressRanges.isMapped(a) -> a[12].toInt() == 127
        a.size == 16 -> (0..14).all { a[it].toInt() == 0 } && a[15].toInt() == 1
        else -> false
    }

    // A block matches an address of its family; an IPv4 block also matches the IPv4 embedded in a mapped or NAT64 address.
    private fun matchesExtra(block: CidrBlock, address: ByteArray): Boolean =
        block.contains(address) || (block.isIpv4 && AddressRanges.embeddedV4(address)?.let { block.contains(it) } == true)
    //endregion

    private fun checkDowngrade(policy: OutboundUrlPolicy, url: Url, previous: Url?): GuardRefusal? =
        if (previous?.scheme == "https" && url.scheme == "http" && !policy.settings.allowHttpsToHttp) {
            GuardRefusal(RefusalReason.InsecureRedirect, null)
        } else {
            null
        }

    private fun parseRefusal(failure: Throwable): RefusalReason = when ((failure as? UrlParseException)?.problem) {
        UrlProblem.UserInfo -> RefusalReason.UserInfo
        UrlProblem.Scheme -> RefusalReason.Scheme
        UrlProblem.Port -> RefusalReason.Port
        else -> RefusalReason.HostForm
    }

    private companion object {
        const val LOCALHOST = "localhost"
        const val EXTRA_CLASS = "extra-denied-range"
        val LOOPBACK_HOSTS: Set<String> = setOf("localhost", "127.0.0.1", "::1")
    }
}
