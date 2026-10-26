package com.spartanlabs.webtools.http

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

private val log: Log = logger("com.spartanlabs.webtools.http.GuardedDns")
internal const val EMPTY_ANSWER_MESSAGE: String = "The resolver returned no addresses."

/**
 * An [okhttp3.Dns] that judges every address a name resolves to. It overrides `lookup` only and inherits `newCall`, whose
 * default routes record resolution through `lookup`; it never uses `by delegate` and never forwards `newCall`. Delegate
 * exceptions pass through; an empty delegate answer becomes an `UnknownHostException` with a constant message; it never
 * returns an empty list (OkHttp's message for one holds the host).
 */
internal class GuardedDns(private val delegate: Dns, private val guard: UrlGuard) : Dns {
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
