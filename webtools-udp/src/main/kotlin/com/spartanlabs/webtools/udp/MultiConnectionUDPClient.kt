package com.spartanlabs.webtools.udp

import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The client-side counterpart to [MultiConnectionUDPServer]: opens the `Iam`
 * handshake, then owns the entire subsequent session, over **one** socket.
 *
 * ### The "one socket for everything" invariant
 * [MultiConnectionUDPServer] replies to a client's `Iam` datagram, and addresses
 * every later datagram, straight back to the source address and port the `Iam`
 * arrived from - because that is the 5-tuple whose NAT mapping is actually open
 * (issue #1's NAT fix). This class binds exactly one [DatagramSocket] in its
 * constructor and uses it for the handshake send, the handshake reply, and every
 * later send and receive, so that invariant cannot accidentally be violated by
 * opening a second socket to listen for the reply.
 *
 * Do **not** build this on [UDPSendReceiveServer] - that type is a *two*-socket
 * primitive (`sendSocket` plus a separately-bound `listenSocket`); reusing it
 * here would silently reintroduce the exact bug issue #1 fixed, since the
 * handshake reply would arrive on a different local port than the one this
 * client's NAT mapping was opened from.
 *
 * ### Concurrency
 * Mirrors [MultiConnectionUDPServer]'s own listener-thread + dispatch-executor
 * split: one background daemon listener thread only *demultiplexes* - `receive()`
 * -> classify (`KA` vs. data) -> drop the keepalive or hand the payload to the
 * dispatch executor. The dispatch executor is a single daemon thread
 * (`mcupc-dispatch`) that invokes the caller's [onMessage] callback passed to
 * [start], so a slow callback cannot stall the socket read loop.
 *
 * [handshake] itself remains a **blocking** one-shot call, mirroring the
 * server's own handshake state machine running synchronously rather than
 * through a dispatch executor - only steady-state message delivery, after
 * [start], is asynchronous.
 *
 * A third daemon thread (`mcupc-keepalive`, a `ScheduledExecutorService`) exists
 * **only once [startKeepAlive] has been called**; it sends `KA` datagrams on an
 * idle-aware cadence and is shut by [stop].
 * See the scheduled-keepalive sequence diagram in docs/issue-12-scheduled-keepalive-plan.md §2.8.
 *
 * A fourth daemon thread (`mcupc-probe`, also a `ScheduledExecutorService`) exists
 * **only once [startProbe] has been called**; it sends `PING <seq>` datagrams on a
 * fixed cadence for the link-quality probe and is shut by [stop].
 * See docs/issue-13-link-quality-probe-plan.md §2.9.
 *
 * Boundary-Ring notes (mirrors [CommonChannel]'s):
 * - One UDP socket carries the handshake and the entire session.
 * - The JDK permits a concurrent [DatagramSocket.send] while a
 *   [DatagramSocket.receive] is in progress.
 * - [socket] is received on only by the listener thread, but sent on from any
 *   thread ([send], [sendKeepAlive], the `mcupc-keepalive` tick, the `mcupc-probe`
 *   tick); the listener thread itself never sends application data - it answers an
 *   inbound `PING` with a `PONG` inline.
 *
 * ### Construction side effects
 * Instantiating this class **binds an ephemeral OS UDP port** immediately - a
 * documented construction side effect, the same convention [CommonChannel] uses.
 *
 * ### Ordering contract (not enforced in code)
 * Call [handshake] once, then [start] once. Calling [start] before a successful
 * [handshake], or calling either twice, is undefined behaviour - matching the
 * level of guarding [MultiConnectionUDPServer] itself applies to its own
 * lifecycle. A [handshake] that fails - including a refusal surfaced as a
 * [HandshakeRefusedException] - leaves the client safe to [stop] and discard.
 *
 * This class does **not** implement [AutoCloseable], matching
 * [MultiConnectionUDPServer]: lifecycle is [start]/[stop], not `use { }`.
 *
 * ### Concurrent-call contract
 * [stop] is idempotent: calling it more than once, from any thread, is safe and
 * every call returns [Result.success] (the underlying socket-close and
 * executor-shutdown are themselves idempotent/non-throwing). [stop] may also be
 * called concurrently with a blocked [handshake] or a running [start] listener -
 * closing [socket] is what unblocks a listener parked in `receive()`. A [send]
 * racing a concurrent [stop] does not throw: it either completes normally or
 * observes the now-closed socket and returns [Result.failure]. [start] and
 * [handshake] themselves are not safe to race against each other or against a
 * second concurrent call to either - see the "Ordering contract" below.
 * [startKeepAlive] / [stopKeepAlive] are safe from any thread and may race [stop]:
 * a [startKeepAlive] after [stop] fails its [Result]; an in-flight keepalive tick
 * either sends or observes the closed socket and fails its own [Result].
 * [startProbe] / [stopProbe] carry the same contract: safe from any thread, and a
 * [startProbe] after [stop] fails its [Result].
 *
 * See the sequence diagram in docs/issue-3-public-client-handshake-plan.md §2.2
 * for the canonical handshake/listener/dispatch flow.
 *
 * ### Binary application payloads
 * Application data may be raw bytes: [send] takes a `ByteArray` and [startBytes]
 * arms the listener with a raw-bytes handler that receives an exact-length,
 * undecoded, untrimmed copy of every datagram. The `KA` classifier still runs on
 * the trimmed UTF-8 view of every inbound datagram, so a binary payload that
 * decodes/trims to `KA` is dropped; lead binary application datagrams with a byte
 * that is not ASCII whitespace and cannot start `KA` (e.g. `0x00`, or `>= 0x80`).
 * An inbound datagram over 65507 bytes is delivered truncated, not rejected; on
 * send, a payload over the OS datagram limit fails the `Result`. Keep frames
 * under the path MTU (~1200 bytes) for real-network use.
 *
 * @param serverAddress the server's address to hand shake with and send to
 * @param serverPort the server's common listen port; defaults to
 * [MultiConnectionUDPServer.COMMON_LISTEN_PORT]
 * @param receiveBufferBytes size of the datagram receive buffer, 512..65507;
 * defaults to 65507 so no well-formed datagram is truncated. A datagram larger
 * than this is delivered truncated, not rejected (a WARN is logged).
 * @param keepAlive the scheduled-keepalive timer seam backing [startKeepAlive] /
 * [stopKeepAlive]; the public constructor supplies a real [PeriodicScheduler]
 * (`mcupc-keepalive`), tests inject a fake. Mirrors
 * [HandshakeCoordinator]'s injected `keepAliveSchedule`.
 * @param probe the link-quality probe timer seam backing [startProbe] /
 * [stopProbe]; the public constructor supplies a real [PeriodicScheduler]
 * (`mcupc-probe`), tests inject a fake.
 * @throws java.net.SocketException if an ephemeral local port could not be bound
 * @throws IllegalArgumentException if [receiveBufferBytes] is outside 512..65507
 */
class MultiConnectionUDPClient internal constructor(
    private val serverAddress: InetAddress,
    private val serverPort: Int,
    private val receiveBufferBytes: Int,
    private val keepAlive: PeriodicSchedule,
    private val probe: PeriodicSchedule,
) {
    /**
     * The public client constructor - unchanged three-parameter surface. Delegates
     * to the `internal` seam constructor with real [PeriodicScheduler] instances
     * (`mcupc-keepalive`, `mcupc-probe`), whose executors/threads stay lazy until
     * the first [startKeepAlive] / [startProbe].
     */
    @JvmOverloads
    constructor(
        serverAddress: InetAddress,
        serverPort: Int = MultiConnectionUDPServer.COMMON_LISTEN_PORT,
        receiveBufferBytes: Int = MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
    ) : this(
        serverAddress, serverPort, receiveBufferBytes,
        PeriodicScheduler("mcupc-keepalive"), PeriodicScheduler("mcupc-probe"),
    )

    init {
        require(
            receiveBufferBytes in
                MultiConnectionUDPServer.MIN_RECEIVE_BUFFER_BYTES..MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES,
        ) {
            "receiveBufferBytes must be ${MultiConnectionUDPServer.MIN_RECEIVE_BUFFER_BYTES}.." +
                "${MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES}, was $receiveBufferBytes"
        }
    }

    /** The one socket used for the handshake and the entire session afterward. */
    private val socket = DatagramSocket()

    /** Guard flag for the listener loop, cleared by [stop]. */
    @Volatile
    private var listening = false

    /** Background thread that services [socket] once [start] is called. */
    private var listenerThread: Thread? = null

    /** Single daemon thread that runs the [start] callback, off the listener thread. */
    private val dispatchExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "mcupc-dispatch").apply { isDaemon = true } }

    /**
     * Monotonic `nanoTime` of the last datagram this client put on the wire (data
     * or `KA`); read by the keepalive tick, written by [send], hence `@Volatile`.
     * Only ever used as a `nanoTime` difference.
     */
    @Volatile
    private var lastOutboundAtNanos: Long = System.nanoTime()

    /** The fixed server endpoint - the keepalive and probe schedules' key. */
    private val serverEndpoint = InetSocketAddress(serverAddress, serverPort)

    /**
     * The link-quality estimator, created lazily on the first [startProbe]; `null`
     * => no probe ever armed => zero cost. Written on the first [startProbe] and
     * read by the listener thread and by consumer calls to [linkQuality], hence
     * `@Volatile`.
     */
    @Volatile
    private var linkQualityTracker: LinkQualityTracker? = null

    /** The local port [socket] is bound to - the same port every datagram, in both directions, uses. */
    val localPort: Int get() = socket.localPort

    /**
     * Sends `Iam <name>` (or `Iam <name> <credential>`) and blocks (up to
     * [timeoutMillis]) for the server's reply, on this same socket. One-shot;
     * call once, before [start].
     *
     * This call is one-shot: a [HandshakeRefusedException] raised for a bad
     * credential will recur on a retry with the same credential; a refusal for
     * capacity ([Admission.Refused]) may not.
     * @param name this client's chosen name
     * @param timeoutMillis how long to wait for the server's reply before failing
     * @param credential an opaque token forwarded verbatim in the `Iam` line to
     * the server's [MultiConnectionUDPServer.admit]; the default (empty string)
     * sends the plain `Iam <name>`. Must be whitespace-free (base64url-encode a
     * structured or binary credential).
     * @return [Result.success] once the server has replied `REGISTERED`; or
     * [Result.failure] holding a [HandshakeRefusedException] if the server replied
     * `REFUSED <reason>`; or the failure that prevented it (including a timeout -
     * the server never replied)
     */
    @JvmOverloads
    fun handshake(
        name: String,
        timeoutMillis: Int = HANDSHAKE_TIMEOUT_MILLIS,
        credential: String = "",
    ): Result<Unit> = runCatching {
        val payload = HandshakeWireFormat.handshakeMessage(name, credential).toByteArray(Charsets.UTF_8)
        socket.send(DatagramPacket(payload, payload.size, serverAddress, serverPort))

        socket.soTimeout = timeoutMillis
        val buffer = ByteArray(receiveBufferBytes)
        val reply = DatagramPacket(buffer, buffer.size)
        socket.receive(reply) // throws SocketTimeoutException if the server never answers
        String(reply.data, 0, reply.length, Charsets.UTF_8).trim()
    }.flatMap { reply ->
        when {
            HandshakeWireFormat.isRegistered(reply) -> Result.success(Unit)
            HandshakeWireFormat.isRefused(reply) ->
                Result.failure(HandshakeRefusedException(HandshakeWireFormat.refusalReason(reply)))
            else -> Result.failure(
                IllegalStateException("Expected '${HandshakeWireFormat.REGISTERED_REPLY}' but got '$reply'"),
            )
        }
    }.onFailure { log.error("Handshake with {}:{} failed", serverAddress, serverPort, it) }

    /**
     * Starts the background listener thread: receives datagrams on [socket] until
     * [stop] is called, drops bare `KA` keepalives silently, and dispatches every
     * other datagram's decoded text to [onMessage] on the single-threaded dispatch
     * executor (never on the listener thread itself), so a slow [onMessage] cannot
     * stall the read loop.
     *
     * Call after [handshake] has succeeded. Resets the socket's read timeout (set
     * by [handshake]) back to block indefinitely, since the session listener must
     * not spuriously time out.
     * @param onMessage invoked with the decoded text of every non-keepalive datagram;
     * runs on the dispatch executor, not the caller's thread, so it must return quickly.
     * An exception thrown by [onMessage] is caught, logged, and does not stop the
     * listener or later dispatches.
     * @return [Result.success] once the listener thread is running, or the failure
     * that prevented starting it
     */
    fun start(onMessage: (message: String) -> Unit): Result<Unit> =
        startWith { _, text -> dispatch { onMessage(text) } }

    /**
     * Raw-bytes counterpart to [start]: dispatches an exact-length, undecoded,
     * untrimmed copy of every non-keepalive datagram to [onMessage]. Mutually
     * exclusive with [start] - last call wins - and carries the same one-shot
     * ordering contract (call after [handshake], call once).
     *
     * The `KA` classifier still runs on the trimmed UTF-8 view of every inbound
     * datagram, so a binary payload that decodes/trims to `KA` is dropped and
     * never delivered; lead binary application datagrams with a byte that is not
     * ASCII whitespace and cannot start `KA` (e.g. `0x00`, or any byte `>= 0x80`).
     * The delivered copy is at most 65507 bytes; a larger inbound datagram is
     * delivered truncated to that length, not rejected.
     * @param onMessage invoked with the raw body of every non-keepalive datagram;
     * runs on the dispatch executor, not the caller's thread. A thrown exception is
     * caught, logged, and does not stop the listener.
     * @return [Result.success] once the listener thread is running, or the failure
     * that prevented starting it
     */
    fun startBytes(onMessage: (bytes: ByteArray) -> Unit): Result<Unit> =
        startWith { bytes, _ -> dispatch { onMessage(bytes) } }

    /**
     * Hand delivery to the single-threaded executor so a slow handler never stalls
     * the listener thread. The inner runCatching keeps a throwing handler from
     * killing the dispatch thread.
     */
    private fun dispatch(block: () -> Unit) {
        dispatchExecutor.execute { runCatching(block).onFailure { log.warn("Message handler threw", it) } }
    }

    private fun startWith(deliver: (bytes: ByteArray, text: String) -> Unit): Result<Unit> {
        listening = true
        return runCatching {
            // Undo handshake()'s bounded wait - the session listener must block
            // indefinitely, not time out every idle interval.
            socket.soTimeout = 0
            listenerThread = Thread { receiveLoop(deliver) }.apply {
                name = "mcupc-listener"
                isDaemon = true
                start()
            }
        }.onFailure { cause ->
            listening = false
            log.error("Could not start the listener thread", cause)
        }
    }

    /** Body of the listener thread: classify-and-drop `KA`, dispatch everything else. */
    private fun receiveLoop(deliver: (bytes: ByteArray, text: String) -> Unit) {
        val buffer = ByteArray(receiveBufferBytes)
        while (listening) {
            runCatching {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                // Dead at the default 65507 buffer; fires only for a lowered buffer.
                if (packet.length == buffer.size && buffer.size < MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES) {
                    log.warn(
                        "Datagram from {} filled the {}-byte receive buffer and may have been truncated",
                        packet.socketAddress, buffer.size,
                    )
                }
                // Exact-length copy: the next receive() reuses buffer, so a slice handed
                // to the dispatch executor would be a data race.
                val bytes = packet.data.copyOf(packet.length)
                bytes to String(bytes, Charsets.UTF_8).trim()
            }.onSuccess { (bytes, text) ->
                when {
                    HandshakeWireFormat.isKeepAlive(text) -> log.trace("Dropped keepalive from server")

                    HandshakeWireFormat.isProbeRequest(text) ->
                        send(HandshakeWireFormat.probeReplyMessage(HandshakeWireFormat.probeToken(text)))
                            .onFailure { log.debug("Could not answer a PING", it) }

                    HandshakeWireFormat.isProbeReply(text) ->
                        // Non-numeric / bare PONG token: ignore rather than throw on the listener thread - an old or spoofed peer must not break the probe.
                        HandshakeWireFormat.probeToken(text).toLongOrNull()
                            ?.let { seq -> linkQualityTracker?.completeProbe(seq) }

                    else -> deliver(bytes, text)
                }
            }.onFailure { cause ->
                if (cause is SocketException) {
                    log.debug("Client socket was closed, stopping listener")
                    listening = false
                } else {
                    log.warn("Failed to handle incoming datagram: {}", cause.message, cause)
                }
            }
        }
    }

    /**
     * Sends [message] to the server over the shared socket. Safe to call from any
     * thread, including while [receiveLoop] is blocked in a receive on another.
     * @param message the text to send
     * @return [Result.success] if the message was sent, or the failure that prevented it
     */
    fun send(message: String): Result<Unit> = send(message.toByteArray(Charsets.UTF_8))

    /**
     * Sends [bytes] to the server as one raw datagram over the shared socket - the
     * payload is placed on the wire verbatim, no encoding, no trim. Safe to call
     * from any thread. [send]`(String)` is a UTF-8 wrapper over this.
     *
     * A payload above the OS datagram limit fails the returned [Result] (the
     * cause is logged) rather than being sent; for real-network use keep frames
     * under the path MTU (~1200 bytes) to avoid IP fragmentation.
     * @param bytes the raw datagram payload
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    fun send(bytes: ByteArray): Result<Unit> = runCatching {
        socket.send(DatagramPacket(bytes, bytes.size, serverAddress, serverPort))
        // Stamped only after a successful send: a failed send must not defer the next KA,
        // since nothing reached the wire. Every outbound datagram funnels through here -
        // send(String) and sendKeepAlive() included - so any traffic defers a scheduled KA.
        lastOutboundAtNanos = System.nanoTime()
    }.onFailure { log.error("Could not send to {}:{}", serverAddress, serverPort, it) }

    /**
     * Sends one minimal `KA` keepalive datagram, one-shot (mirrors
     * [Connection.keepAlive]). For a library-managed cadence use [startKeepAlive].
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    fun sendKeepAlive(): Result<Unit> = send(HandshakeWireFormat.KEEPALIVE_TOKEN)

    /**
     * Starts an opt-in, idle-aware background keepalive: every ~[intervalMillis] of
     * output silence this client sends one `KA` on the shared socket to hold its NAT
     * mapping open, until [stopKeepAlive] or [stop]. Application sends reset the
     * idle timer, so a busy client sends no redundant keepalives.
     *
     * A convenience over [sendKeepAlive] - it removes the hand-rolled timer every
     * consumer otherwise writes. [sendKeepAlive] itself is unchanged and still owns
     * no timer. Calling this again replaces the schedule (last call wins). Backed by
     * one daemon thread (`mcupc-keepalive`) created on the first call.
     *
     * @param intervalMillis output-idle time before a keepalive is sent; must be
     * > 0. Defaults to [HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS]
     * (20 s). A keepalive may go out up to one quarter-interval (max 5 s) late.
     * @return [Result.success] once the schedule is armed; [Result.failure] with an
     * [IllegalArgumentException] for a non-positive interval, or an
     * [IllegalStateException] if [stop] has already run.
     */
    @JvmOverloads
    fun startKeepAlive(intervalMillis: Long = HandshakeWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS): Result<Unit> =
        keepAlive.schedule(serverEndpoint, intervalMillis) {
            if (KeepAlive.isDue(lastOutboundAtNanos, System.nanoTime(), intervalMillis)) {
                sendKeepAlive().onFailure { log.warn("Scheduled keepalive send failed", it) }
            }
        }.onFailure { log.error("Could not start the scheduled keepalive", it) }

    /**
     * Stops the background keepalive started by [startKeepAlive]. Idempotent and safe
     * to call even if [startKeepAlive] was never called. [stop] also does this.
     * @return [Result.success] once the schedule is cancelled
     */
    fun stopKeepAlive(): Result<Unit> = runCatching { keepAlive.cancel(serverEndpoint) }

    /**
     * Starts an opt-in link-quality probe: every [intervalMillis] this client sends
     * one `PING <seq>` to the server, and each matching `PONG` updates a smoothed
     * RTT / jitter / packet-loss estimate readable via [linkQuality]. Off until
     * called; runs until [stopProbe] or [stop]. Backed by one daemon thread
     * (`mcupc-probe`) created on the first call. Calling this again re-arms it (last
     * call wins). Call after [handshake] - probes sent before registration go
     * unanswered and register as loss.
     *
     * The probe requires both ends on `1.6.0`+: a pre-`1.6.0` server does not
     * answer `PING`, so `packetLossRatio` climbs toward `1.0` and a consumer that
     * opted in against such a server should not have.
     *
     * @param intervalMillis probe period; must be > 0. Default
     * [HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS] (1 s).
     * @return [Result.success] once armed; [Result.failure] with
     * [IllegalArgumentException] for a non-positive interval or [IllegalStateException]
     * if [stop] has already run.
     */
    @JvmOverloads
    fun startProbe(intervalMillis: Long = HandshakeWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS): Result<Unit> {
        val tracker = linkQualityTracker ?: LinkQualityTracker().also { linkQualityTracker = it }
        tracker.probeIntervalMillis = intervalMillis
        return probe.schedule(serverEndpoint, intervalMillis) {
            tracker.sweep()
            send(HandshakeWireFormat.probeRequestMessage(tracker.beginProbe().toString()))
                .onFailure { log.warn("Scheduled probe send failed", it) }
        }.onFailure { log.error("Could not start the link-quality probe", it) }
    }

    /**
     * Stops the probe started by [startProbe]. Idempotent; [stop] also does this.
     * The last [linkQuality] snapshot remains readable.
     * @return [Result.success] once the schedule is cancelled
     */
    fun stopProbe(): Result<Unit> = runCatching { probe.cancel(serverEndpoint) }

    /**
     * The latest link-quality snapshot, or `null` until the first `PONG` has come
     * back.
     * @return the current [LinkQuality], or `null`
     */
    fun linkQuality(): LinkQuality? = linkQualityTracker?.snapshot()

    /**
     * Stops the listener thread, shuts the keepalive scheduler (if [startKeepAlive]
     * armed one), shuts the probe scheduler (if [startProbe] armed one), closes the
     * socket, and shuts the dispatch executor.
     * Every step runs even if an earlier one failed, so a partial failure never
     * leaks the bound port. Once called, this instance should be discarded.
     * @return [Result.success] if every step succeeded, or the first failure encountered
     */
    fun stop(): Result<Unit> {
        listening = false
        // This join is expected to time out: the listener is blocked in socket.receive()
        // until the close() call below raises the SocketException that lets the loop
        // observe listening == false, so close() always runs unconditionally afterward
        // rather than being skipped when the join above doesn't complete cleanly.
        val listenerJoined = runCatching { listenerThread?.join(LISTENER_JOIN_TIMEOUT_MILLIS) }
            .map { }
            .onFailure { cause ->
                if (cause is InterruptedException) Thread.currentThread().interrupt()
                log.warn("Interrupted while waiting for the listener thread to stop")
            }
        // Shut the keepalive scheduler before the socket so a scheduled KA never races a close.
        val keepAliveStopped = runCatching { keepAlive.shutdown() }
            .onFailure { log.warn("Could not cleanly shut the keepalive scheduler", it) }
        // Shut the probe scheduler before the socket so a scheduled PING never races a close.
        val probeStopped = runCatching { probe.shutdown() }
            .onFailure { log.warn("Could not cleanly shut the probe scheduler", it) }
        val socketClosed = runCatching { socket.close() }
            .onFailure { cause -> log.error("Could not close the client socket", cause) }
        val executorStopped = runCatching { dispatchExecutor.shutdownNow() }.map { }
            .onFailure { log.warn("Could not cleanly shut the dispatch executor", it) }
        return listenerJoined.flatMap { keepAliveStopped }.flatMap { probeStopped }
            .flatMap { socketClosed }.flatMap { executorStopped }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MultiConnectionUDPClient::class.java)
        private const val HANDSHAKE_TIMEOUT_MILLIS = 4000
        private const val LISTENER_JOIN_TIMEOUT_MILLIS = 1000L
    }
}
