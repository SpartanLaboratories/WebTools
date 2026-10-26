package com.spartanlabs.testing.support.webtools.http

import okhttp3.Dns
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * An address built from bytes, so the JDK performs no lookup. A 16-byte IPv4-mapped literal collapses to an `Inet4Address`,
 * as the JDK does.
 *
 * @param text a strict dotted quad, or an IPv6 literal when it holds `:`.
 * @return the address; its `hostName` is unset, so printing it triggers no reverse lookup either.
 * @throws IllegalStateException when [text] is not a strict literal (a mistake in the test's own data).
 */
internal fun inet(text: String): InetAddress = InetAddress.getByAddress(addr(text))

/**
 * A resolver that answers from [answer], counts its calls and records the names asked. Safe for concurrent use.
 *
 * @property answer the answer for a name; it may throw, and the exception reaches the caller unchanged.
 */
internal class StubDns(private val answer: (String) -> List<InetAddress>) : Dns {
    /** How many times [lookup] was called; safe to read while lookups run. */
    val calls = AtomicInteger()

    /** The names asked, in call order; safe to read while lookups run. */
    val hostnames = CopyOnWriteArrayList<String>()

    override fun lookup(hostname: String): List<InetAddress> {
        calls.incrementAndGet()
        hostnames += hostname
        return answer(hostname)
    }

    companion object {
        /**
         * A stub that answers the same addresses for every name.
         *
         * @param addresses strict literals, as [inet] reads them, in answer order.
         * @return the stub.
         */
        fun of(vararg addresses: String): StubDns = StubDns { addresses.map { inet(it) } }
    }
}
