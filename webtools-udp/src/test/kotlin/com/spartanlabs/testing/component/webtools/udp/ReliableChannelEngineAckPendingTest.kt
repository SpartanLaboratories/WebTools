package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.webtools.udp.DatagramType
import com.spartanlabs.webtools.udp.ReliableChannelEngine
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Level 2 - the §3.6 delayed-ack guard (RFC 1122 4.2.3.2), extending the Stage-2 engine
// coverage: the idle-channel-emits-nothing regression this stage is built to prevent
// (§1.3(b) of the Stage-3 plan) - an open reliable channel must not silently defeat Issue #10
// idle detection by emitting a standalone ack on every tick regardless of new inbound data.
@Tag("component")
class ReliableChannelEngineAckPendingTest {

    private var now = 1_000_000_000_000L
    private val clock = { now }
    private val ms = 1_000_000L

    private class RecordingSend {
        val sent = mutableListOf<ByteArray>()
        val fn: (ByteArray) -> Result<Unit> = { bytes -> sent += bytes; Result.success(Unit) }
    }

    private fun engine(send: RecordingSend) = ReliableChannelEngine(sendRaw = send.fn, nanoClock = clock)

    @Test
    fun `no standalone ack on a tick when nothing arrived since the last ack`() {
        val sendA = RecordingSend()
        val sendB = RecordingSend()
        val a = engine(sendA)
        val b = engine(sendB)
        a.sendReliable(byteArrayOf(1))
        b.onInboundDatagram(sendA.sent.single())
        sendB.sent.clear()
        b.onRetransmitTick() // consumes the pending ack from the receipt above

        sendB.sent.clear()
        b.onRetransmitTick()
        b.onRetransmitTick()

        assertTrue(sendB.sent.isEmpty(), "an idle channel's tick must do nothing at all")
    }

    @Test
    fun `exactly one standalone ack after a fresh 0xA0`() {
        val sendA = RecordingSend()
        val sendB = RecordingSend()
        val a = engine(sendA)
        val b = engine(sendB)
        a.sendReliable(byteArrayOf(1))

        b.onInboundDatagram(sendA.sent.single())
        sendB.sent.clear()
        b.onRetransmitTick()
        b.onRetransmitTick() // a second tick with nothing new must not re-ack

        assertEquals(1, sendB.sent.size)
        assertEquals(DatagramType.RELIABLE_ACK.tag, sendB.sent.single()[0])
    }

    @Test
    fun `a duplicate 0xA0 re-arms the ack - the wedge case this guard exists to prevent`() {
        val sendA = RecordingSend()
        val sendB = RecordingSend()
        val a = engine(sendA)
        val b = engine(sendB)
        a.sendReliable(byteArrayOf(1))
        val framed = sendA.sent.single()
        b.onInboundDatagram(framed)
        sendB.sent.clear()
        b.onRetransmitTick() // first ack sent and consumed

        sendB.sent.clear()
        b.onInboundDatagram(framed) // a duplicate - as if our first ack was lost and the peer retransmitted
        b.onRetransmitTick()

        assertEquals(1, sendB.sent.size, "a duplicate 0xA0 must re-arm the ack, or the peer retransmits forever")
        assertEquals(DatagramType.RELIABLE_ACK.tag, sendB.sent.single()[0])
    }

    @Test
    fun `a piggybacked ack on a sendReliable clears the pending flag`() {
        val sendA = RecordingSend()
        val sendB = RecordingSend()
        val a = engine(sendA)
        val b = engine(sendB)
        a.sendReliable(byteArrayOf(1))
        b.onInboundDatagram(sendA.sent.single())

        // b has something to ack; instead of waiting for a tick, b itself sends reliably,
        // piggybacking the ack on its own 0xA0 - the pending flag must clear from that alone.
        sendB.sent.clear()
        b.sendReliable(byteArrayOf(9))
        sendB.sent.clear()
        b.onRetransmitTick()

        assertTrue(sendB.sent.isEmpty(), "the ack already rode out on the piggybacked 0xA0")
    }

    @Test
    fun `close clears the pending ack flag`() {
        val sendA = RecordingSend()
        val sendB = RecordingSend()
        val a = engine(sendA)
        val b = engine(sendB)
        a.sendReliable(byteArrayOf(1))
        b.onInboundDatagram(sendA.sent.single())

        b.close()
        sendB.sent.clear()
        b.onRetransmitTick()

        assertTrue(sendB.sent.isEmpty(), "close() must clear ackPending along with the buffers")
    }
}
