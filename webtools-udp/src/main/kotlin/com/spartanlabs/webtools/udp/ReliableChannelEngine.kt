package com.spartanlabs.webtools.udp

import org.slf4j.LoggerFactory

/**
 * The reliable-ordered channel's orchestrator: wires [ReliableRetransmitBuffer]
 * (send side), [ReliableReorderBuffer] (receive side), and
 * [ReliableRtoEstimator] (RTO) together into the `sendReliable` /
 * `onInboundDatagram` / `onRetransmitTick` seam a future stage's socket
 * plumbing will drive. Entirely socket-free: outbound bytes go through the
 * injected [sendRaw], and this stage never creates a retransmit-tick executor
 * itself — [onRetransmitTick] is shaped as `() -> Unit` precisely so a later
 * stage can hand `engine::onRetransmitTick` straight to
 * `PeriodicSchedule.schedule(key, intervalMillis, tick)`.
 *
 * `@Synchronized` throughout, same discipline as [LinkQualityTracker]. The
 * engine is the sole entry point into its sub-buffers, so lock order is
 * always engine -> buffer, never reversed - no deadlock risk.
 *
 * @param sendRaw puts one already-framed datagram on the wire; its [Result]
 * is inspected but never propagated out of this engine - a failed send here
 * is logged and left for the next retransmit tick to retry
 * @param windowSize the fixed in-flight window, shared with the receive-side
 * reorder window (design doc OD-3 - unifying them is what structurally
 * prevents the stale-ring-slot bug [ReliableRetransmitBuffer] documents)
 * @param rtoFloorMillis the minimum RTO [ReliableRtoEstimator] will ever return
 * @param rtoCapMillis the maximum RTO/backoff ceiling, shared by the estimator
 * and by [ReliableRetransmitBuffer]'s per-message backoff
 * @param channel the reliable channel id every frame this engine emits carries
 * @param nanoClock monotonic time source; injectable for deterministic tests
 */
internal class ReliableChannelEngine(
    private val sendRaw: (ByteArray) -> Result<Unit>,
    private val windowSize: Int = 256,
    private val rtoFloorMillis: Long = 200L,
    private val rtoCapMillis: Long = 5_000L,
    private val channel: Byte = ReliableWireFormat.DEFAULT_RELIABLE_CHANNEL,
    private val nanoClock: () -> Long = System::nanoTime,
) {
    /** The outcome of [sendReliable]. */
    sealed interface SendOutcome {
        /** The message was buffered and put on the wire (or will be, on the next retransmit tick). */
        data class Accepted(val seq: Int) : SendOutcome

        /** The in-flight window is full; the caller should back off. */
        data object WindowFull : SendOutcome
    }

    private val retransmitBuffer = ReliableRetransmitBuffer(windowSize, nanoClock)
    private val reorderBuffer = ReliableReorderBuffer(windowSize)
    private val rtoEstimator = ReliableRtoEstimator(rtoFloorMillis, rtoCapMillis)

    /**
     * Buffers [payload] for reliable delivery and, on acceptance, sends a
     * fresh `0xA0` frame immediately, piggybacking the current ack/bitfield
     * for the reverse direction.
     * @param payload the application message to send reliably
     * @return [SendOutcome.Accepted] with the assigned seq, or [SendOutcome.WindowFull]
     * if [windowSize] messages are already in flight
     */
    @Synchronized
    fun sendReliable(payload: ByteArray): SendOutcome =
        when (val offer = retransmitBuffer.offer(payload, rtoEstimator.currentRtoMillis(), nanoClock())) {
            is ReliableRetransmitBuffer.OfferResult.WindowFull -> SendOutcome.WindowFull
            is ReliableRetransmitBuffer.OfferResult.Accepted -> {
                val (ack, ackBitfield) = reorderBuffer.ackAndBitfield()
                sendRaw(ReliableWireFormat.reliableDataDatagram(offer.seq, ack, ackBitfield, payload, channel))
                    .onFailure {
                        // Already buffered - swallow and let the next onRetransmitTick retry it.
                        log.warn("Send of reliable seq {} failed, retrying on the next retransmit tick", offer.seq, it)
                    }
                SendOutcome.Accepted(offer.seq)
            }
        }

    /**
     * Feeds one inbound `0xA0`/`0xA1` datagram to the engine: ack/bitfield
     * processing always runs first (forwarding never-retransmitted samples to
     * the RTO estimator, per Karn's algorithm), then, for `0xA0`, the payload
     * is handed to the reorder buffer. Never throws - a malformed or
     * unrecognized [datagram] is WARN-logged and yields an empty list.
     * @param datagram one received datagram, tag byte included
     * @return every payload newly deliverable in order as of this call, oldest first
     */
    @Synchronized
    fun onInboundDatagram(datagram: ByteArray): List<ByteArray> =
        when (DatagramType.ofTagByte(datagram.getOrNull(0))) {
            DatagramType.RELIABLE_DATA -> {
                val header = ReliableWireFormat.reliableDataHeaderOf(datagram)
                val payload = ReliableWireFormat.reliablePayloadOf(datagram)
                if (header == null || payload == null) {
                    log.warn("Malformed 0xA0 reliable-data datagram, dropping")
                    emptyList()
                } else {
                    applyAck(header.ack, header.ackBitfield)
                    val outcome = reorderBuffer.onReceive(header.seq, payload)
                    (outcome as? ReliableReorderBuffer.ReceiveOutcome.Delivered)?.messages ?: emptyList()
                }
            }

            DatagramType.RELIABLE_ACK -> {
                val header = ReliableWireFormat.reliableAckHeaderOf(datagram)
                if (header == null) {
                    log.warn("Malformed 0xA1 reliable-ack datagram, dropping")
                } else {
                    applyAck(header.ack, header.ackBitfield)
                }
                emptyList()
            }

            else -> {
                log.warn("Unrecognized or malformed datagram fed to the reliable engine, dropping")
                emptyList()
            }
        }

    /** Removes every entry `(ack, ackBitfield)` covers, feeding never-retransmitted samples to the RTO estimator. */
    private fun applyAck(ack: Int, ackBitfield: Int) {
        retransmitBuffer.onAck(ack, ackBitfield, nanoClock())
            .forEach { sample -> rtoEstimator.onRttSample(sample.rttMillis) }
    }

    /**
     * Resends every in-flight entry whose RTO has elapsed, each piggybacking
     * the current ack/bitfield. If nothing was due and there is inbound data
     * to acknowledge, sends one standalone `0xA1` instead - the standalone-ack
     * timer coalesced onto the retransmit tick (design doc OD-2), rather than
     * a dedicated faster ack-delay timer. Shaped as `() -> Unit` so a later
     * stage can hand this straight to a [PeriodicSchedule]; this stage never
     * creates that executor itself.
     */
    @Synchronized
    fun onRetransmitTick() {
        val due = retransmitBuffer.dueForRetransmit(nanoClock(), rtoCapMillis)
        val (ack, ackBitfield) = reorderBuffer.ackAndBitfield()
        if (due.isNotEmpty()) {
            due.forEach { entry ->
                sendRaw(ReliableWireFormat.reliableDataDatagram(entry.seq, ack, ackBitfield, entry.payload, channel))
                    .onFailure { log.warn("Retransmit of reliable seq {} failed", entry.seq, it) }
            }
        } else if (ack != SENTINEL_ACK || ackBitfield != SENTINEL_ACK_BITFIELD) {
            sendRaw(ReliableWireFormat.reliableAckDatagram(ack, ackBitfield, channel))
                .onFailure { log.warn("Standalone reliable ack send failed", it) }
        }
    }

    /** Discards every in-flight and buffered message; per design doc §7.7, teardown drops un-acked data. */
    @Synchronized
    fun close() {
        retransmitBuffer.clear()
        reorderBuffer.clear()
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ReliableChannelEngine::class.java)

        // ReliableReorderBuffer.ackAndBitfield()'s documented pre-first-receipt sentinel.
        const val SENTINEL_ACK = 0xFFFF
        const val SENTINEL_ACK_BITFIELD = 0
    }
}
