package com.spartanlabs.testing.component.webtools.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.spartanlabs.testing.support.webtools.http.StubDns
import com.spartanlabs.testing.support.webtools.http.inet
import com.spartanlabs.testing.support.webtools.http.trustedGuard
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.webtools.http.EMPTY_ANSWER_MESSAGE
import com.spartanlabs.webtools.http.GuardedDns
import com.spartanlabs.webtools.http.MixedAnswers
import com.spartanlabs.webtools.http.POLICY_REFUSED_HOST_MESSAGE
import com.spartanlabs.webtools.http.PolicyRefusedUnknownHost
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.policyRefusal
import okhttp3.Dns
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - `GuardedDns` over a `StubDns` (no lookup ever happens: every address is built from bytes): an allowed answer
 * passes unchanged; `Reject` throws the marker wherever the denied address sits; `DropDenied` filters in order and never
 * returns an empty list; an empty delegate answer is a plain `UnknownHostException` with the constant message; delegate
 * exceptions pass through as the same instances; IPv6, mapped and NAT64 addresses are judged; the class overrides no
 * `newCall` and delegates nothing (reflection), and the inherited `newCall` routes through `lookup` (behaviour); the one
 * `DEBUG` line under `DropDenied` holds counts and classes and never an address; eight threads see consistent results.
 */
class GuardedDnsComponentTest {

    private val reject = untrustedGuard()
    private val drop = untrustedGuard { mixedAnswers = MixedAnswers.DropDenied }

    private fun guarded(guard: com.spartanlabs.webtools.http.UrlGuard, vararg addresses: String): GuardedDns =
        GuardedDns(StubDns.of(*addresses), guard)

    private fun refusedLookup(dns: GuardedDns): PolicyRefusedUnknownHost =
        assertFailsWith<PolicyRefusedUnknownHost> { dns.lookup("name.test") }

    @Test
    fun `an all-allowed answer is returned unchanged and in order`() {
        val answer = guarded(reject, "8.8.8.8", "2606:4700:4700::1111").lookup("name.test")
        assertEquals(listOf(inet("8.8.8.8"), inet("2606:4700:4700::1111")), answer)
    }

    @Test
    fun `Reject throws the marker wherever the denied address sits`() {
        listOf(guarded(reject, "8.8.8.8", "10.0.0.5"), guarded(reject, "10.0.0.5", "8.8.8.8")).forEach { dns ->
            val marker = refusedLookup(dns)
            assertEquals(RefusalReason.DeniedAddress, marker.refusal.reason)
            assertEquals("private-use", marker.refusal.rangeClass)
            assertEquals(POLICY_REFUSED_HOST_MESSAGE, marker.message)
        }
    }

    @Test
    fun `DropDenied returns the allowed addresses in order`() {
        assertEquals(listOf(inet("8.8.8.8"), inet("1.1.1.1")), guarded(drop, "10.0.0.5", "8.8.8.8", "127.0.0.1", "1.1.1.1").lookup("name.test"))
    }

    @Test
    fun `DropDenied with only denied addresses throws the first refusal and never returns an empty list`() {
        val marker = refusedLookup(guarded(drop, "10.0.0.5", "127.0.0.1"))
        assertEquals("private-use", marker.refusal.rangeClass)
    }

    @Test
    fun `an empty delegate answer is a plain UnknownHostException with the constant message`() {
        listOf(reject, drop).forEach { guard ->
            val failure = assertFailsWith<UnknownHostException> { GuardedDns(StubDns { emptyList() }, guard).lookup("name.test") }
            assertFalse(failure is PolicyRefusedUnknownHost)
            assertEquals(EMPTY_ANSWER_MESSAGE, failure.message)
            assertFalse(failure.message!!.contains("name.test"))
        }
    }

    @Test
    fun `delegate exceptions propagate as the same instances`() {
        val unknown = UnknownHostException("delegate")
        assertSame(unknown, assertFailsWith<UnknownHostException> { GuardedDns(StubDns { throw unknown }, reject).lookup("name.test") })
        val state = IllegalStateException("delegate")
        assertSame(state, assertFailsWith<IllegalStateException> { GuardedDns(StubDns { throw state }, reject).lookup("name.test") })
    }

    @Test
    fun `IPv6 mapped and NAT64 addresses are judged`() {
        assertEquals("not-global-unicast", refusedLookup(guarded(reject, "fd00::1")).refusal.rangeClass)
        assertEquals("loopback", refusedLookup(guarded(reject, "::ffff:127.0.0.1")).refusal.rangeClass)
        assertEquals("loopback", refusedLookup(guarded(reject, "64:ff9b::7f00:1")).refusal.rangeClass)
        assertEquals(listOf(inet("64:ff9b::808:808")), guarded(reject, "64:ff9b::808:808").lookup("name.test"))
    }

    // A delegate whose own newCall counts its calls: the guard must never forward to it.
    private class CountingNewCallDns(private val answer: List<InetAddress>) : Dns {
        val lookups = java.util.concurrent.atomic.AtomicInteger()
        val newCalls = java.util.concurrent.atomic.AtomicInteger()
        override fun lookup(hostname: String): List<InetAddress> { lookups.incrementAndGet(); return answer }
        override fun newCall(request: Dns.Request): Dns.Call { newCalls.incrementAndGet(); return super.newCall(request) }
    }

    @Test
    fun `the class forwards no newCall and delegates nothing`() {
        // The source overrides no newCall. The compiled class still declares one: Kotlin 2.2.0 writes a bridge for the
        // interface's JVM default method that calls the interface default (invokespecial), never a delegate. So the structure is
        // measured by behaviour: the inherited newCall reaches this guard's lookup and never the delegate's newCall.
        assertTrue(Dns::class.java.isAssignableFrom(GuardedDns::class.java))
        assertTrue(GuardedDns::class.java.declaredFields.none { it.name.contains("\$\$delegate") })
        val delegate = CountingNewCallDns(listOf(inet("8.8.8.8")))
        val collector = Collector()
        GuardedDns(delegate, reject).newCall(Dns.Request("name.test")).enqueue(collector)
        collector.await()
        assertNull(collector.failure)
        assertEquals(1, delegate.lookups.get())
        assertEquals(0, delegate.newCalls.get())
    }

    // The inherited newCall: collects the records (or the failure) of one request, awaited with a latch.
    private class Collector : Dns.Callback {
        val records = CopyOnWriteArrayList<Dns.Record>()
        @Volatile var failure: IOException? = null
        val done = CountDownLatch(1)

        override fun onRecords(call: Dns.Call, last: Boolean, records: List<Dns.Record>) {
            this.records += records
            if (last) done.countDown()
        }

        override fun onFailure(call: Dns.Call, e: IOException) {
            failure = e
            done.countDown()
        }

        fun await(): Collector {
            assertTrue(done.await(5, TimeUnit.SECONDS), "the callback did not complete within 5 s")
            return this
        }
    }

    @Test
    fun `the inherited newCall routes through lookup under DropDenied`() {
        val collector = Collector()
        guarded(drop, "8.8.8.8", "10.0.0.5").newCall(Dns.Request("name.test")).enqueue(collector)
        collector.await()
        assertNull(collector.failure)
        val addresses = collector.records.filterIsInstance<Dns.Record.IpAddress>().map { it.address }
        assertEquals(listOf(inet("8.8.8.8")), addresses)
        assertEquals(collector.records.size, addresses.size)
    }

    @Test
    fun `the inherited newCall surfaces a Reject refusal as a failure holding the marker`() {
        val collector = Collector()
        guarded(reject, "8.8.8.8", "10.0.0.5").newCall(Dns.Request("name.test")).enqueue(collector)
        collector.await()
        val failure = assertNotNull(collector.failure, "records: ${collector.records}")
        assertEquals("private-use", assertNotNull(failure.policyRefusal(), "$failure").rangeClass)
    }

    @Test
    fun `toString is the class name and a Trusted guard passes a private answer through`() {
        assertEquals("GuardedDns", guarded(reject).toString())
        assertEquals(listOf(inet("10.0.0.1"), inet("127.0.0.1")), guarded(trustedGuard(), "10.0.0.1", "127.0.0.1").lookup("name.test"))
    }

    // Captures the shared "com.spartanlabs.webtools.http.GuardedDns" logger: it relies on JUnit running test methods
    // sequentially (no parallel execution is enabled), since the level and the appender are process-wide for its duration.
    private fun <T> withDebugCapture(block: (ListAppender<ILoggingEvent>) -> T): T {
        val logbackLogger = LoggerFactory.getLogger("com.spartanlabs.webtools.http.GuardedDns") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previousLevel = logbackLogger.level
        logbackLogger.level = Level.DEBUG
        logbackLogger.addAppender(appender)
        try {
            return block(appender)
        } finally {
            logbackLogger.detachAppender(appender)
            logbackLogger.level = previousLevel
            appender.stop()
        }
    }

    @Test
    fun `one DEBUG line per lookup that dropped entries with counts and classes and never an address`() = withDebugCapture { appender ->
        guarded(drop, "10.0.0.5", "8.8.8.8", "127.0.0.1", "1.1.1.1").lookup("name.test")
        assertEquals(1, appender.list.size, appender.list.toString())
        val event = appender.list.single()
        assertEquals(Level.DEBUG, event.level)
        val message = event.formattedMessage
        assertTrue(message.contains("dropped 2 of 4"), message)
        assertTrue(message.contains("name.test"), message)
        assertTrue(message.contains("[loopback, private-use]"), message)
        listOf("10.0.0.5", "8.8.8.8", "127.0.0.1", "1.1.1.1").forEach { assertFalse(message.contains(it), message) }
    }

    @Test
    fun `nothing is logged when nothing is dropped or under Reject`() = withDebugCapture { appender ->
        guarded(drop, "8.8.8.8", "1.1.1.1").lookup("name.test")
        assertFailsWith<PolicyRefusedUnknownHost> { guarded(reject, "8.8.8.8", "10.0.0.5").lookup("name.test") }
        assertEquals(emptyList<ILoggingEvent>(), appender.list)
    }

    @Test
    fun `eight threads released together see consistent results`() {
        val dns = guarded(drop, "10.0.0.5", "8.8.8.8", "127.0.0.1", "1.1.1.1")
        val expected: List<InetAddress> = listOf(inet("8.8.8.8"), inet("1.1.1.1"))
        val start = CountDownLatch(1)
        val mismatches = CopyOnWriteArrayList<String>()
        val threads = (1..8).map {
            Thread {
                start.await()
                repeat(500) { val got = dns.lookup("name.test"); if (got != expected) mismatches += got.toString() }
            }.apply { start() }
        }
        start.countDown()
        threads.forEach { it.join(30_000) }
        assertTrue(threads.none { it.isAlive }, "a thread did not finish")
        assertEquals(emptyList<String>(), mismatches)
    }
}
