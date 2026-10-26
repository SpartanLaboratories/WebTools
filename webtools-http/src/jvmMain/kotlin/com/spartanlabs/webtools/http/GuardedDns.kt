package com.spartanlabs.webtools.http

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

private val log: Log = logger("com.spartanlabs.webtools.http.GuardedDns")

/** The message of the plain `UnknownHostException` for an empty delegate answer; OkHttp's own message for one holds the host. */
internal const val EMPTY_ANSWER_MESSAGE: String = "The resolver returned no addresses."

/**
 * An [okhttp3.Dns] that judges every address a name resolves to. It overrides `lookup` only and inherits `newCall`, whose
 * default routes record resolution through `lookup`; it never uses `by delegate` and never forwards `newCall`. Delegate
 * exceptions pass through; an empty delegate answer becomes an `UnknownHostException` with a constant message; it never
 * returns an empty list (OkHttp's message for one holds the host).
 *
 * `lookup` runs on whichever thread drives route selection: the caller's for `execute()`, a `Dispatcher` thread for
 * `enqueue()`, the `Dns.Call`'s thread through the inherited bridge. The class is stateless beyond its two references and
 * safe to share between calls and clients. Under `DropDenied` it writes one `DEBUG` line per lookup that dropped addresses
 * (the host, the counts and the range classes; never an address); otherwise it logs nothing. After it throws, the call fails
 * with the marker on top, unwrapped: no further route is tried and no socket is opened. An IP-literal host never reaches
 * this class: OkHttp resolves a literal itself, so the literal is judged by [UrlGuard.checkHop] before any call.
 *
 * @property delegate the resolver that answers; `Dns.SYSTEM` in production, a stub in tests.
 * @property guard the guard whose policy judges each address ([UrlGuard.judgeAnswer]).
 */
internal class GuardedDns(private val delegate: Dns, private val guard: UrlGuard) : Dns {
    /**
     * Resolves [hostname] through the delegate and judges the answer.
     *
     * @param hostname the name OkHttp resolves; never an IP literal.
     * @return the delegate's addresses, whole under `Reject`, the allowed ones in answer order under `DropDenied`; never empty.
     * @throws PolicyRefusedUnknownHost when the answer is refused (any denied address under `Reject`; none left under `DropDenied`).
     * @throws UnknownHostException when the delegate answers with no address (constant message), or whatever the delegate throws.
     */
    override fun lookup(hostname: String): List<InetAddress> {
        val answer = delegate.lookup(hostname)
        if (answer.isEmpty()) throw UnknownHostException(EMPTY_ANSWER_MESSAGE)
        val addresses = answer.map { it.address }
        return when (val verdict = guard.judgeAnswer(addresses)) {
            is AnswerVerdict.Refused -> throw PolicyRefusedUnknownHost(verdict.refusal)
            is AnswerVerdict.Allowed -> {
                if (verdict.indices.size < answer.size) logDropped(hostname, addresses, verdict.indices)
                verdict.indices.map { answer[it] }
            }
        }
    }

    // One DEBUG line: the host name, the counts and the distinct range classes; never an address.
    private fun logDropped(hostname: String, addresses: List<ByteArray>, kept: List<Int>) {
        val keptSet = kept.toSet()
        val classes = addresses.indices.filter { it !in keptSet }
            .mapNotNull { guard.checkAddress(addresses[it])?.rangeClass }.distinct().sorted()
        log.debug("dropped {} of {} resolved addresses for {} (range classes: {})", addresses.size - kept.size, addresses.size, hostname, classes)
    }

    override fun toString(): String = "GuardedDns"
}
