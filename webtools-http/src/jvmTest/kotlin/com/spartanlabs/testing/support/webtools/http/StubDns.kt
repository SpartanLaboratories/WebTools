package com.spartanlabs.testing.support.webtools.http

import okhttp3.Dns
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** An address for [text] built from bytes, so the JDK performs no lookup. A 16-byte IPv4-mapped literal collapses to an `Inet4Address`, as the JDK does. */
internal fun inet(text: String): InetAddress = InetAddress.getByAddress(addr(text))

/** A resolver that answers from [answer], counts its calls and records the names asked. Safe for concurrent use. */
internal class StubDns(private val answer: (String) -> List<InetAddress>) : Dns {
    val calls = AtomicInteger()
    val hostnames = CopyOnWriteArrayList<String>()

    override fun lookup(hostname: String): List<InetAddress> {
        calls.incrementAndGet()
        hostnames += hostname
        return answer(hostname)
    }

    companion object {
        /** A stub that answers the same [addresses] for every name. */
        fun of(vararg addresses: String): StubDns = StubDns { addresses.map { inet(it) } }
    }
}
