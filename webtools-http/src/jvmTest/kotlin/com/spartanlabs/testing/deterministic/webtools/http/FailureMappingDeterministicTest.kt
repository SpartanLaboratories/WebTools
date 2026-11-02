package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.FailureContext
import com.spartanlabs.webtools.http.FailureEvent
import com.spartanlabs.webtools.http.GuardRefusal
import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.MappedFailure
import com.spartanlabs.webtools.http.Phase
import com.spartanlabs.webtools.http.PolicyRefusedIo
import com.spartanlabs.webtools.http.PolicyRefusedUnknownHost
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.RefusalStage
import com.spartanlabs.webtools.http.SecondExchangeRefused
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.mapFailure
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 4a - every row of the failure mapping over synthetic exceptions and flags: the markers anywhere in the chain (the call
 * timeout's wrapper included), the row order, `DnsFailed` with the hop's host, the two `Timeout` rows and their `cause` rule,
 * `ConnectFailed` without OkHttp's wrapper, the TLS rows by phase and scheme, `Protocol`, OkHttp's `canceled due to` wrapper, the `Io`
 * catch-alls, and that every message is constant and no cause survives but `Timeout`, `DnsFailed` and `ConnectFailed`.
 */
class FailureMappingDeterministicTest {

    private val canary = "SECRET-CANARY-10.0.0.5"

    private fun ctx(host: String = "h.test", https: Boolean = false, closed: Boolean = false, canceled: Boolean = false, phase: Phase = Phase.Exchange) =
        FailureContext(host, https, closed, canceled, phase)

    private fun map(e: Throwable, ctx: FailureContext = ctx()): MappedFailure = mapFailure(e, ctx)

    private val refusal = GuardRefusal(RefusalReason.DeniedAddress, "private-use")

    private fun wrappedInTimeout(marker: Throwable): Throwable = InterruptedIOException("timeout").apply { initCause(marker) }

    @Test
    fun `a policy marker anywhere in the chain is RefusedByPolicy with its reason and range class`() {
        val placements = listOf<Throwable>(
            PolicyRefusedUnknownHost(refusal),
            IOException(canary, PolicyRefusedIo(refusal)),
            IOException(canary).apply { addSuppressed(PolicyRefusedIo(refusal)) },
            wrappedInTimeout(PolicyRefusedUnknownHost(refusal)),
        )
        placements.forEach { e ->
            val mapped = map(e, ctx(canceled = true))
            val leaf = assertIs<TransportFailure.RefusedByPolicy>(mapped.failure, e.toString())
            assertEquals(RefusalReason.DeniedAddress, leaf.reason)
            assertNull(leaf.cause)
            val event = assertIs<FailureEvent.PolicyRefusal>(mapped.event)
            assertSame(refusal, event.refusal)
            assertEquals(RefusalStage.Connection, event.stage)
        }
    }

    @Test
    fun `a SecondExchangeRefused anywhere in the chain is RetryRefused`() {
        val placements = listOf<Throwable>(
            SecondExchangeRefused(),
            IOException(canary, SecondExchangeRefused()),
            IOException(canary).apply { addSuppressed(SecondExchangeRefused()) },
            wrappedInTimeout(SecondExchangeRefused()),
        )
        placements.forEach { e ->
            val mapped = map(e, ctx(canceled = true))
            assertIs<TransportFailure.RetryRefused>(mapped.failure, e.toString())
            assertNull(mapped.failure.cause)
            assertSame(FailureEvent.ExchangeRepeat, mapped.event)
        }
    }

    @Test
    fun `a policy marker beats closed and closed beats a timeout`() {
        assertIs<TransportFailure.RefusedByPolicy>(map(PolicyRefusedIo(refusal), ctx(closed = true)).failure)
        val closed = map(SocketTimeoutException("Read timed out"), ctx(closed = true))
        val leaf = assertIs<HttpFailure.Closed>(closed.failure)
        assertEquals("transport", leaf.component)
        assertSame(FailureEvent.Silent, closed.event)
        assertIs<HttpFailure.Closed>(map(InterruptedIOException("executor rejected"), ctx(closed = true)).failure)
    }

    @Test
    fun `UnknownHostException is DnsFailed with the hop's host and the cause only when it has none itself`() {
        val bare = UnknownHostException(canary)
        val mapped = map(bare, ctx(host = "nonexistent.test"))
        val leaf = assertIs<TransportFailure.DnsFailed>(mapped.failure)
        assertEquals("nonexistent.test", leaf.host)
        assertSame(bare, leaf.cause)
        assertEquals(FailureEvent.Other::class, mapped.event::class)
        assertEquals(canary, (mapped.event as FailureEvent.Other).keptMessage)
        val caused = UnknownHostException(canary).apply { initCause(IOException("deeper")) }
        assertNull(assertIs<TransportFailure.DnsFailed>(map(caused).failure).cause)
    }

    @Test
    fun `SocketTimeoutException is a kept Timeout whether or not the call was cancelled`() {
        listOf(false, true).forEach { canceled ->
            val e = SocketTimeoutException("Read timed out")
            val mapped = map(e, ctx(canceled = canceled))
            assertSame(e, assertIs<TransportFailure.Timeout>(mapped.failure).cause)
            assertEquals("Read timed out", (mapped.event as FailureEvent.Other).keptMessage)
        }
    }

    @Test
    fun `the call timeout's InterruptedIOException is Timeout and keeps only a benign cause`() {
        val bare = InterruptedIOException("timeout")
        assertSame(bare, assertIs<TransportFailure.Timeout>(map(bare, ctx(canceled = true)).failure).cause)
        val socket = InterruptedIOException("timeout").apply { initCause(SocketException("Socket closed")) }
        assertSame(socket, assertIs<TransportFailure.Timeout>(map(socket, ctx(canceled = true)).failure).cause)
        val connect = InterruptedIOException("timeout").apply { initCause(ConnectException("Failed to connect to $canary")) }
        assertNull(assertIs<TransportFailure.Timeout>(map(connect, ctx(canceled = true)).failure).cause)
        val chain = InterruptedIOException("timeout").apply { initCause(IOException("a", IOException("b"))) }
        assertNull(assertIs<TransportFailure.Timeout>(map(chain, ctx(canceled = true)).failure).cause)
        val socketWithCause = InterruptedIOException("timeout").apply { initCause(SocketException("x").apply { initCause(IOException("y")) }) }
        assertNull(assertIs<TransportFailure.Timeout>(map(socketWithCause, ctx(canceled = true)).failure).cause)
        assertNull((map(bare, ctx(canceled = true)).event as FailureEvent.Other).keptMessage)
        assertIs<TransportFailure.Io>(map(InterruptedIOException("timeout"), ctx(canceled = false)).failure)
    }

    @Test
    fun `ConnectFailed keeps the JDK exception and never OkHttp's wrapper`() {
        val wrapper = ConnectException("Failed to connect to $canary")
        val bareWrapper = assertIs<TransportFailure.ConnectFailed>(map(wrapper).failure)
        assertNull(bareWrapper.cause)
        val jdk = ConnectException("Connection refused")
        val wrapped = ConnectException("Failed to connect to $canary").apply { initCause(jdk) }
        val mapped = map(wrapped)
        assertSame(jdk, assertIs<TransportFailure.ConnectFailed>(mapped.failure).cause)
        assertEquals("Connection refused", (mapped.event as FailureEvent.Other).keptMessage)
        val noRoute = NoRouteToHostException("No route to host")
        assertSame(noRoute, assertIs<TransportFailure.ConnectFailed>(map(noRoute).failure).cause)
        val unreachable = PortUnreachableException("ICMP Port Unreachable")
        assertSame(unreachable, assertIs<TransportFailure.ConnectFailed>(map(unreachable).failure).cause)
    }

    @Test
    fun `an SSLException is TlsFailed in the exchange and Io in the body`() {
        listOf<Throwable>(SSLHandshakeException(canary), SSLPeerUnverifiedException(canary)).forEach { e ->
            val tls = map(e, ctx(phase = Phase.Exchange))
            assertIs<TransportFailure.TlsFailed>(tls.failure, e.toString())
            assertNull(tls.failure.cause)
            assertNull((tls.event as FailureEvent.Other).keptMessage)
            assertIs<TransportFailure.Io>(map(e, ctx(phase = Phase.Body)).failure, e.toString())
        }
    }

    @Test
    fun `UnknownServiceException is TlsFailed for https and Io for http`() {
        assertIs<TransportFailure.TlsFailed>(map(UnknownServiceException(canary), ctx(https = true)).failure)
        assertIs<TransportFailure.Io>(map(UnknownServiceException(canary), ctx(https = false)).failure)
    }

    @Test
    fun `ProtocolException is Protocol without a cause`() {
        val mapped = map(ProtocolException("Unexpected status line: $canary"))
        assertIs<TransportFailure.Protocol>(mapped.failure)
        assertNull(mapped.failure.cause)
        assertEquals("java.net.ProtocolException", (mapped.event as FailureEvent.Other).exceptionClass)
        assertNull((mapped.event as FailureEvent.Other).keptMessage)
    }

    @Test
    fun `OkHttp's canceled-due-to wrapper is Protocol for a negative status and Io otherwise`() {
        assertIs<TransportFailure.Protocol>(map(IOException("canceled due to x", IllegalStateException("code < 0: -12"))).failure)
        assertIs<TransportFailure.Io>(map(IOException("canceled due to x", IllegalStateException("something else"))).failure)
        assertIs<TransportFailure.Io>(map(IOException("canceled due to x", RuntimeException("boom"))).failure)
        assertIs<TransportFailure.Io>(map(EOFException("x").apply { initCause(IllegalStateException("code < 0: -12")) }).failure)
        assertIs<TransportFailure.Io>(map(IOException("x", IOException("y"))).failure)
    }

    @Test
    fun `the other IOExceptions and any Exception are Io without a cause`() {
        listOf<Throwable>(
            EOFException("\\n not found: limit=0"), SocketException("Connection reset"), IOException("stream was reset: PROTOCOL_ERROR"),
            InterruptedIOException("executor rejected"), RuntimeException(canary), IllegalStateException(canary),
        ).forEach { e ->
            val mapped = map(e)
            assertIs<TransportFailure.Io>(mapped.failure, e.toString())
            assertNull(mapped.failure.cause, e.toString())
        }
    }

    @Test
    fun `every message is constant and no exception text reaches a failure message`() {
        val inputs = listOf<Throwable>(
            UnknownHostException(canary), SocketTimeoutException(canary), InterruptedIOException(canary), SSLHandshakeException(canary),
            UnknownServiceException(canary), ConnectException(canary), NoRouteToHostException(canary), ProtocolException(canary),
            IOException(canary, IllegalStateException(canary)), EOFException(canary), RuntimeException(canary), SecondExchangeRefused(),
            PolicyRefusedIo(refusal),
        )
        inputs.forEach { e ->
            val failure = map(e, ctx(canceled = true)).failure
            assertFalse(canary in failure.message!!, failure.message!!)
            val keepsCause = failure is TransportFailure.Timeout || failure is TransportFailure.DnsFailed || failure is TransportFailure.ConnectFailed
            assertTrue(keepsCause || failure.cause == null, "${failure.javaClass.simpleName} kept a cause for $e")
        }
    }
}
