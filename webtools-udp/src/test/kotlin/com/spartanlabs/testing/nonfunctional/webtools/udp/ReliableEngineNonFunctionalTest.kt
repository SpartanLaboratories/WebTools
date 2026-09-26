package com.spartanlabs.testing.nonfunctional.webtools.udp

import com.spartanlabs.webtools.udp.ReliableChannelEngine
import com.spartanlabs.webtools.udp.ReliableReorderBuffer
import com.spartanlabs.webtools.udp.ReliableWireFormat
import org.junit.jupiter.api.Tag
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 4c - robustness of the internal reliable engine (Issue #14, Stage 2): eventual
// exactly-once in-order delivery under burst loss, bounded receive-side memory under
// pathological reordering, no exception from any malformed/garbage input, and frame overhead
// against the ~1200-byte path-MTU advisory. Entirely socket-free and fake-clocked.
@Tag("nonfunctional")
class ReliableEngineNonFunctionalTest {

    private companion object {
        const val PATH_MTU_ADVISORY_BYTES = 1200
    }

    // --- frame overhead vs the path-MTU advisory ---

    @Test
    fun `reliable-data overhead is 10 bytes, ack-only overhead is 8 bytes - negligible against the ~1200-byte advisory`() {
        val dataOverhead = ReliableWireFormat.reliableDataDatagram(0, 0, 0, ByteArray(0)).size
        val ackOverhead = ReliableWireFormat.reliableAckDatagram(0, 0).size
        assertEquals(10, dataOverhead)
        assertEquals(8, ackOverhead)
        assertTrue(dataOverhead < 0.01 * PATH_MTU_ADVISORY_BYTES, "negligible (<1%) against the path-MTU advisory")
    }

    // --- burst-loss soak: thousands of messages, seeded-lossy in-memory relay, fake clock ---

    @Test
    fun `burst-loss soak - thousands of messages eventually arrive exactly once, in order`() {
        var now = 0L
        val clock = { now }
        val nanosPerMilli = 1_000_000L
        val random = Random(20260917)
        val lossRate = 0.3
        val totalMessages = 3_000
        val windowSize = 64

        val delivered = mutableListOf<ByteArray>()
        lateinit var a: ReliableChannelEngine
        lateinit var b: ReliableChannelEngine

        a = ReliableChannelEngine(
            sendRaw = { bytes -> if (random.nextDouble() >= lossRate) delivered += b.onInboundDatagram(bytes); Result.success(Unit) },
            windowSize = windowSize,
            rtoFloorMillis = 10L,
            rtoCapMillis = 500L,
            nanoClock = clock,
        )
        b = ReliableChannelEngine(
            sendRaw = { bytes -> if (random.nextDouble() >= lossRate) a.onInboundDatagram(bytes); Result.success(Unit) },
            windowSize = windowSize,
            rtoFloorMillis = 10L,
            rtoCapMillis = 500L,
            nanoClock = clock,
        )

        fun tick() {
            now += 20 * nanosPerMilli
            a.onRetransmitTick()
            b.onRetransmitTick()
        }

        var nextToSend = 0
        var iterations = 0
        val maxIterations = totalMessages * 200
        while ((nextToSend < totalMessages || delivered.size < totalMessages) && iterations < maxIterations) {
            iterations++
            if (nextToSend < totalMessages) {
                val outcome = a.sendReliable(byteArrayOf((nextToSend and 0xFF).toByte(), (nextToSend shr 8).toByte()))
                if (outcome is ReliableChannelEngine.SendOutcome.Accepted) nextToSend++
            }
            tick()
        }

        assertEquals(totalMessages, delivered.size, "every message eventually arrived despite burst loss")
        for (i in 0 until totalMessages) {
            val expected = byteArrayOf((i and 0xFF).toByte(), (i shr 8).toByte())
            assertContentEquals(expected, delivered[i], "message $i out of order or corrupted")
        }
    }

    // --- bounded receive-side memory under pathological reordering ---

    @Test
    fun `the reorder buffer never grows past windowSize entries, however many far-ahead messages arrive`() {
        val windowSize = 16
        val buffer = ReliableReorderBuffer(windowSize)

        // seq 0 (the cursor) never arrives - every buffer-able offset 1..windowSize-1 fills once,
        // then thousands more, ever further ahead, must all bounce off as OutOfWindow.
        var buffered = 0
        var outOfWindow = 0
        for (seq in 1..(windowSize - 1)) {
            when (buffer.onReceive(seq, byteArrayOf(seq.toByte()))) {
                is ReliableReorderBuffer.ReceiveOutcome.Buffered -> buffered++
                else -> error("expected Buffered for seq $seq")
            }
        }
        for (seq in windowSize until windowSize + 10_000) {
            assertIs<ReliableReorderBuffer.ReceiveOutcome.OutOfWindow>(buffer.onReceive(seq, byteArrayOf(1)))
            outOfWindow++
        }

        assertEquals(windowSize - 1, buffered)
        assertEquals(10_000, outOfWindow)
    }

    // --- malformed / garbage input never throws ---

    @Test
    fun `100k random garbage datagrams fed to the engine never throw`() {
        val engine = ReliableChannelEngine(sendRaw = { Result.success(Unit) })
        val random = Random(7)

        repeat(100_000) {
            val bytes = ByteArray(random.nextInt(0, 32)) { random.nextInt(0, 256).toByte() }
            val result = runCatching { engine.onInboundDatagram(bytes) }
            assertTrue(result.isSuccess, "onInboundDatagram must never throw, input was ${bytes.toList()}")
        }
    }

    @Test
    fun `every truncation length of an 0xA0 or 0xA1 datagram never throws and never delivers`() {
        val engine = ReliableChannelEngine(sendRaw = { Result.success(Unit) })

        for (tag in listOf(0xA0.toByte(), 0xA1.toByte())) {
            for (len in 0..12) {
                val truncated = ByteArray(len) { if (it == 0) tag else it.toByte() }
                val result = runCatching { engine.onInboundDatagram(truncated) }
                assertTrue(result.isSuccess, "tag 0x${tag.toString(16)} length $len must not throw")
                assertTrue(result.getOrThrow().isEmpty(), "a truncated frame must never deliver a payload")
            }
        }
    }
}
