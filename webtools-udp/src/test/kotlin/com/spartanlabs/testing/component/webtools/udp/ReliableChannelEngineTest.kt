package com.spartanlabs.testing.component.webtools.udp

import com.spartanlabs.testing.support.webtools.udp.captureLogsOf
import com.spartanlabs.testing.support.webtools.udp.hasWarnContaining
import com.spartanlabs.webtools.udp.DatagramType
import com.spartanlabs.webtools.udp.ReliableChannelEngine
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 2 - the reliable engine orchestrator in isolation: two engines wired via each other's
// onInboundDatagram (no socket, no real timer), driven with a controllable nanoClock and a
// recording sendRaw.
@Tag("component")
class ReliableChannelEngineTest {

    private var now = 1_000_000_000_000L
    private val clock = { now }
    private val ms = 1_000_000L

    private fun advance(millis: Long) {
        now += millis * ms
    }

    private class RecordingSend {
        val sent = mutableListOf<ByteArray>()

        /** Consumed by the next [fn] call, then reset to success - a one-shot failure injector. */
        var nextResult: Result<Unit> = Result.success(Unit)

        val fn: (ByteArray) -> Result<Unit> = { bytes ->
            sent += bytes
            val result = nextResult
            nextResult = Result.success(Unit)
            result
        }
    }

    private fun engine(send: RecordingSend, window: Int = 256) =
        ReliableChannelEngine(sendRaw = send.fn, windowSize = window, nanoClock = clock)

    @Test
    fun `a reliable send round-trips - the peer delivers it in order`() {
        val sendA = RecordingSend()
        val a = engine(sendA)
        val b = engine(RecordingSend())

        val outcome = assertIs<ReliableChannelEngine.SendOutcome.Accepted>(a.sendReliable(byteArrayOf(1, 2, 3)))
        assertEquals(0, outcome.seq)
        assertEquals(1, sendA.sent.size)

        val delivered = b.onInboundDatagram(sendA.sent.single())
        assertEquals(listOf(byteArrayOf(1, 2, 3).toList()), delivered.map { it.toList() })
    }

    @Test
    fun `a standalone ack goes out on the next retransmit tick when there is nothing to send back`() {
        val sendA = RecordingSend()
        val sendB = RecordingSend()
        val a = engine(sendA)
        val b = engine(sendB)

        a.sendReliable(byteArrayOf(9))
        b.onInboundDatagram(sendA.sent.single())
        sendB.sent.clear()

        b.onRetransmitTick()

        assertEquals(1, sendB.sent.size, "a standalone ack was sent")
        assertEquals(DatagramType.RELIABLE_ACK.tag, sendB.sent.single()[0])
    }

    @Test
    fun `no standalone ack is sent before anything has ever been received`() {
        val sendB = RecordingSend()
        val b = engine(sendB)

        b.onRetransmitTick()

        assertTrue(sendB.sent.isEmpty(), "nothing to ack yet - the sentinel means silence")
    }

    @Test
    fun `out-of-order delivery - messages arrive reordered and drain in seq order once the gap fills`() {
        val sendA = RecordingSend()
        val a = engine(sendA)
        val b = engine(RecordingSend())

        a.sendReliable(byteArrayOf(0))
        a.sendReliable(byteArrayOf(1))
        a.sendReliable(byteArrayOf(2))
        val (d0, d1, d2) = sendA.sent

        val fromD2 = b.onInboundDatagram(d2)
        val fromD1 = b.onInboundDatagram(d1)
        val fromD0 = b.onInboundDatagram(d0)

        assertTrue(fromD2.isEmpty() && fromD1.isEmpty(), "buffered ahead, not yet deliverable")
        assertEquals(listOf(0, 1, 2), fromD0.map { it[0].toInt() })
    }

    @Test
    fun `duplicate delivery is deduped - a re-delivered datagram yields nothing new`() {
        val sendA = RecordingSend()
        val a = engine(sendA)
        val b = engine(RecordingSend())

        a.sendReliable(byteArrayOf(0))
        val framed = sendA.sent.single()
        b.onInboundDatagram(framed)
        val redelivered = b.onInboundDatagram(framed)

        assertTrue(redelivered.isEmpty())
    }

    @Test
    fun `sendReliable returns WindowFull once windowSize messages are in flight`() {
        val a = engine(RecordingSend(), window = 2)
        a.sendReliable(byteArrayOf(1))
        a.sendReliable(byteArrayOf(2))

        assertEquals(ReliableChannelEngine.SendOutcome.WindowFull, a.sendReliable(byteArrayOf(3)))
    }

    @Test
    fun `a sendRaw failure is swallowed - the message is retried on the next retransmit tick`() {
        val send = RecordingSend().apply { nextResult = Result.failure(RuntimeException("boom")) }
        val a = engine(send)

        a.sendReliable(byteArrayOf(1))
        assertEquals(1, send.sent.size, "the failed send still reaches sendRaw once")

        advance(10_000) // well past the default 200 ms floor RTO
        a.onRetransmitTick()

        assertEquals(2, send.sent.size, "the retransmit tick retried it")
    }

    @Test
    fun `malformed or unrecognized input never throws and yields an empty list`() {
        val a = engine(RecordingSend())

        assertTrue(a.onInboundDatagram(ByteArray(0)).isEmpty())
        assertTrue(a.onInboundDatagram(byteArrayOf(0xA0.toByte())).isEmpty())
        assertTrue(a.onInboundDatagram(byteArrayOf(0xA1.toByte())).isEmpty())
        assertTrue(a.onInboundDatagram(byteArrayOf(0x42)).isEmpty())
    }

    @Test
    fun `a malformed 0xA0 datagram is WARN-logged and dropped`() {
        val a = engine(RecordingSend())

        captureLogsOf(ReliableChannelEngine::class.java) { events ->
            a.onInboundDatagram(byteArrayOf(0xA0.toByte()))
            assertTrue(events.hasWarnContaining("Malformed 0xA0"))
        }
    }
}
