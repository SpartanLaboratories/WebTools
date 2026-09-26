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
 * -> switch on the [DatagramType] tag (byte 0) -> drop a keepalive/probe or strip
 * the `0x90` frame and hand the payload to the
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
 * **only once [startKeepAlive] has been called**; it sends `0x80` keepalive
 * datagrams on an idle-aware cadence and is shut by [stop].
 * See the scheduled-keepalive sequence diagram in docs/issue-12-scheduled-keepalive-plan.md §2.8.
 *
 * A fourth daemon thread (`mcupc-probe`, also a `ScheduledExecutorService`) exists
 * **only once [startProbe] has been called**; it sends `0x81` probe datagrams
 * (an 8-byte big-endian sequence) on a fixed cadence for the link-quality probe
 * and is shut by [stop].
 * See docs/issue-13-link-quality-probe-plan.md §2.9.
 *
 * A fifth daemon thread (`mcupc-retransmit`, also a `ScheduledExecutorService`)
 * exists **only once a reliable channel is opened** - either a
 * `channel(RELIABLE_ORDERED).send`/`actuate*` call or an inbound `0xA0`/`0xA1`;
 * it retransmits unacked `0xA0` frames and emits standalone `0xA1` acks, runs no
 * application code, and is shut by [stop].
 *
 * Boundary-Ring notes (mirrors [CommonChannel]'s):
 * - One UDP socket carries the handshake and the entire session.
 * - The JDK permits a concurrent [DatagramSocket.send] while a
 *   [DatagramSocket.receive] is in progress.
 * - [socket] is received on only by the listener thread, but sent on from any
 *   thread ([send], [sendKeepAlive], the `mcupc-keepalive` tick, the `mcupc-probe`
 *   tick, the `mcupc-retransmit` tick); the listener thread itself never sends
 *   application data - it answers an inbound `0x81` probe with an `0x82` reply inline.
 *
 * ### Construction side effects
 * Instantiating this class **binds an ephemeral OS UDP port** immediately - a
 * documented construction side effect, the same convention [CommonChannel] uses.
 *
 * ### Ordering contract (not enforced in code)
 * Call [handshake] once, then [start] (or [startBytes], or a [channel] `actuate*`)
 * once. Calling [handshake] itself before a successful prior call, or twice, is
 * undefined behaviour - matching the level of guarding [MultiConnectionUDPServer]
 * itself applies to its own lifecycle. The listener start itself, however, is now
 * **idempotent**: [start], [startBytes], and `channel(UNRELIABLE).actuate*` may
 * each be called any number of times, in any order, after [handshake] - the last
 * one to bind wins, and only the first one to run actually starts the listener
 * thread. A [handshake] that fails - including a refusal surfaced as a
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
 * undecoded, untrimmed copy of every datagram's payload. The session is framed
 * (`webtools-udp` `2.x`): [send] wraps the payload in an `0x90` unreliable
 * datagram and the listener strips the frame before delivery, so **any** payload
 * bytes ride intact - there is no reserved first byte and the pre-`2.0`
 * "lead with `0x00` / `>= 0x80`" rule is gone. Control datagrams (`0x80`
 * keepalive, `0x81`/`0x82` probe) are classified out by their [DatagramType] tag
 * and never delivered. An inbound datagram over 65507 bytes is delivered
 * truncated, not rejected; on send, a payload over the OS datagram limit fails
 * the `Result`. Keep frames under the path MTU (~1200 bytes) for real-network use.
 *
 * ### Reliable-ordered channel
 * `channel(DeliveryMode.RELIABLE_ORDERED)` sends acked, retransmitted, in-order
 * `0xA0`/`0xA1` traffic to the server - see [UdpChannel] and [DeliveryMode] for
 * the full contract, including the size cap ([reliableMaxMessageBytes]), the
 * in-flight-window backpressure ([ReliableWindowFullException]), and the
 * inherent head-of-line blocking. Lazily created on the first
 * `channel(RELIABLE_ORDERED)` call or the first inbound `0xA0`/`0xA1`, whichever
 * comes first; a send after [stop] fails with [IllegalStateException].
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
 * @param retransmit the timer seam backing the reliable-channel retransmit tick;
 * the public constructor supplies a real [PeriodicScheduler] (`mcupc-retransmit`),
 * tests inject a fake.
 * @param reliableMaxMessageBytes the reliable-channel application-payload cap, in
 * bytes; defaults to [UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES] (1024) and
 * must be in `1..`[UdpChannel.MAX_RELIABLE_MESSAGE_BYTES] (8192)
 * @throws java.net.SocketException if an ephemeral local port could not be bound
 * @throws IllegalArgumentException if [receiveBufferBytes] is outside 512..65507, or if
 * [reliableMaxMessageBytes] is outside 1..8192
 */
class MultiConnectionUDPClient internal constructor(
    private val serverAddress: InetAddress,
    private val serverPort: Int,
    private val receiveBufferBytes: Int,
    private val keepAlive: PeriodicSchedule,
    private val probe: PeriodicSchedule,
    private val retransmit: PeriodicSchedule,
    private val reliableMaxMessageBytes: Int,
) {
    /**
     * The public client constructor - unchanged three-parameter surface plus the
     * new [reliableMaxMessageBytes] knob. Delegates to the `internal` seam
     * constructor with real [PeriodicScheduler] instances (`mcupc-keepalive`,
     * `mcupc-probe`, `mcupc-retransmit`), whose executors/threads stay lazy until
     * the first [startKeepAlive] / [startProbe] / reliable channel use.
     */
    @JvmOverloads
    constructor(
        serverAddress: InetAddress,
        serverPort: Int = MultiConnectionUDPServer.COMMON_LISTEN_PORT,
        receiveBufferBytes: Int = MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
        reliableMaxMessageBytes: Int = UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES,
    ) : this(
        serverAddress, serverPort, receiveBufferBytes,
        PeriodicScheduler("mcupc-keepalive"), PeriodicScheduler("mcupc-probe"), PeriodicScheduler("mcupc-retransmit"),
        reliableMaxMessageBytes,
    )

    init {
        require(
            receiveBufferBytes in
                MultiConnectionUDPServer.MIN_RECEIVE_BUFFER_BYTES..MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES,
        ) {
            "receiveBufferBytes must be ${MultiConnectionUDPServer.MIN_RECEIVE_BUFFER_BYTES}.." +
                "${MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES}, was $receiveBufferBytes"
        }
        require(reliableMaxMessageBytes in 1..UdpChannel.MAX_RELIABLE_MESSAGE_BYTES) {
            "reliableMaxMessageBytes must be 1..${UdpChannel.MAX_RELIABLE_MESSAGE_BYTES}, was $reliableMaxMessageBytes"
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

    /**
     * The bound unreliable handler, or `null` if neither [start], [startBytes],
     * nor `channel(UNRELIABLE).actuate*` has been called yet. Already wraps
     * [dispatch] - set once by whichever of those binds first, replaced (not
     * accumulated) by a later call, matching [start]/[startBytes]'s existing
     * mutual-exclusion. Read by the listener thread, hence `@Volatile`.
     */
    @Volatile
    private var deliverUnreliable: ((bytes: ByteArray, text: String) -> Unit)? = null

    /**
     * The bound reliable-ordered handler, or `null` if
     * `channel(RELIABLE_ORDERED).actuate*` has never been called. Independent of
     * [deliverUnreliable] - the reliable and unreliable sequence spaces are
     * independent, so both may be bound at once. Read by the listener thread,
     * hence `@Volatile`.
     */
    @Volatile
    private var deliverReliable: ((bytes: ByteArray) -> Unit)? = null

    /**
     * This client's reliable-ordered engine, or `null` until one is first
     * needed - either an app-thread `channel(RELIABLE_ORDERED).send`/`actuate*`
     * call or an inbound `0xA0`/`0xA1` creates it lazily (mirrors
     * [HandshakeCoordinator]'s per-connection engine). Read by the listener,
     * app, and retransmit threads, hence `@Volatile`.
     */
    @Volatile
    private var reliableEngine: ReliableChannelEngine? = null

    /**
     * Set once by [stop]; checked by a reliable send so it fails its [Result]
     * rather than buffering into a closed socket and reporting success (design
     * §7.7 requires a failure after teardown).
     */
    @Volatile
    private var stopped = false

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
     * @return [Result.success] once the server has replied this build's exact
     * `REGISTERED 2`; or [Result.failure] holding a [HandshakeRefusedException] if
     * the server replied `REFUSED <reason>`; or an [IncompatibleProtocolException]
     * if the server's `REGISTERED` reply announces a different wire-protocol major
     * (a bare `REGISTERED` from a pre-`2.0` server, or `REGISTERED n`, `n != 2`);
     * or the failure that prevented it (including a timeout - the server never replied)
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
        // Ordering: refusal wins; then this build's exact REGISTERED 2; then a REGISTERED
        // line announcing a different wire major (a cross-major peer); then a generic reply.
        when {
            HandshakeWireFormat.isRefused(reply) ->
                Result.failure(HandshakeRefusedException(HandshakeWireFormat.refusalReason(reply)))
            HandshakeWireFormat.isRegistered(reply) -> Result.success(Unit)
            HandshakeWireFormat.registeredProtocolVersion(reply) != null -> Result.failure(
                IncompatibleProtocolException(
                    HandshakeWireFormat.registeredProtocolVersion(reply),
                    TransportWireFormat.FRAMED_PROTOCOL_VERSION,
                ),
            )
            else -> Result.failure(
                IllegalStateException("Expected '${HandshakeWireFormat.REGISTERED_REPLY}' but got '$reply'"),
            )
        }
    }.onFailure { log.error("Handshake with {}:{} failed", serverAddress, serverPort, it) }

    /**
     * Starts the background listener thread (if not already running): receives
     * datagrams on [socket] until [stop] is called, drops `0x80` keepalive and
     * `0x81`/`0x82` probe datagrams silently, and dispatches every `0x90` frame's
     * stripped payload (decoded to text) to [onMessage] on the single-threaded
     * dispatch executor (never on the listener thread itself), so a slow
     * [onMessage] cannot stall the read loop.
     *
     * Call after [handshake] has succeeded. Idempotent: calling this (or
     * [startBytes], or `channel(UNRELIABLE).actuate*`) again rebinds the
     * unreliable handler rather than starting a second listener thread.
     * @param onMessage invoked with the decoded text of every non-keepalive datagram;
     * runs on the dispatch executor, not the caller's thread, so it must return quickly.
     * An exception thrown by [onMessage] is caught, logged, and does not stop the
     * listener or later dispatches.
     * @return [Result.success] once the listener thread is running, or the failure
     * that prevented starting it
     */
    @Deprecated(
        "Use channel(DeliveryMode.UNRELIABLE).actuate(...) - see the 2.0 channel API",
        ReplaceWith("channel(DeliveryMode.UNRELIABLE).actuate(onMessage)", "com.spartanlabs.webtools.udp.DeliveryMode"),
    )
    fun start(onMessage: (message: String) -> Unit): Result<Unit> {
        deliverUnreliable = { _, text -> dispatch { onMessage(text) } }
        return ensureListening()
    }

    /**
     * Raw-bytes counterpart to [start]: dispatches an exact-length, undecoded,
     * untrimmed copy of every non-keepalive datagram to [onMessage]. Mutually
     * exclusive with [start] - last call wins - and idempotent in the same way:
     * calling this (or [start], or `channel(UNRELIABLE).actuate*`) again rebinds
     * the unreliable handler rather than starting a second listener thread. Call
     * after [handshake].
     *
     * Control datagrams (`0x80` keepalive, `0x81`/`0x82` probe) are classified out
     * by their [DatagramType] tag; every `0x90` frame's stripped payload is
     * delivered verbatim, so any payload bytes ride intact - there is no reserved
     * first byte. The delivered copy is at most 65507 bytes; a larger inbound
     * datagram is delivered truncated to that length, not rejected.
     * @param onMessage invoked with the raw body of every non-keepalive datagram;
     * runs on the dispatch executor, not the caller's thread. A thrown exception is
     * caught, logged, and does not stop the listener.
     * @return [Result.success] once the listener thread is running, or the failure
     * that prevented starting it
     */
    @Deprecated(
        "Use channel(DeliveryMode.UNRELIABLE).actuateBytes(...) - see the 2.0 channel API",
        ReplaceWith(
            "channel(DeliveryMode.UNRELIABLE).actuateBytes(onMessage)",
            "com.spartanlabs.webtools.udp.DeliveryMode",
        ),
    )
    fun startBytes(onMessage: (bytes: ByteArray) -> Unit): Result<Unit> {
        deliverUnreliable = { bytes, _ -> dispatch { onMessage(bytes) } }
        return ensureListening()
    }

    /**
     * The delivery-mode-scoped handle for this client's session with the server.
     * Each [DeliveryMode] has its own sequence space and its own inbound
     * handler - binding one never disturbs the other. Unlike the [Connection]
     * side, both modes are always fully supported here (there is no
     * [UnsupportedOperationException] case): the client always talks to exactly
     * one server.
     * @param mode which delivery guarantee to get a handle for
     * @return the [UdpChannel] for [mode]
     */
    fun channel(mode: DeliveryMode): UdpChannel = when (mode) {
        DeliveryMode.UNRELIABLE -> unreliableChannel
        DeliveryMode.RELIABLE_ORDERED -> reliableChannel
    }

    // Both handles target the private internals (sendUnreliable/sendReliable, the deliver fields,
    // ensureListening) rather than the deprecated public members above, so neither needs an
    // @Suppress and both survive the 3.0.0 demotion of those members untouched.
    private val unreliableChannel: UdpChannel by lazy {
        object : UdpChannel {
            override val mode = DeliveryMode.UNRELIABLE
            override fun send(bytes: ByteArray): Result<Unit> = sendUnreliable(bytes)
            override fun actuateBytes(onMessage: (ByteArray) -> Unit): Result<Unit> {
                deliverUnreliable = { bytes, _ -> dispatch { onMessage(bytes) } }
                return ensureListening()
            }
        }
    }

    private val reliableChannel: UdpChannel by lazy {
        object : UdpChannel {
            override val mode = DeliveryMode.RELIABLE_ORDERED
            override fun send(bytes: ByteArray): Result<Unit> = sendReliable(bytes)
            override fun actuateBytes(onMessage: (ByteArray) -> Unit): Result<Unit> {
                deliverReliable = { bytes -> dispatch { onMessage(bytes) } }
                return ensureListening()
            }
        }
    }

    /**
     * Hand delivery to the single-threaded executor so a slow handler never stalls
     * the listener thread. The inner runCatching keeps a throwing handler from
     * killing the dispatch thread.
     */
    private fun dispatch(block: () -> Unit) {
        dispatchExecutor.execute { runCatching(block).onFailure { log.warn("Message handler threw", it) } }
    }

    /**
     * Starts the listener thread if it is not already running; idempotent - a
     * second call while already listening is a no-op success. [start], [startBytes],
     * and both [channel] handles' `actuate*` all route through this, so whichever
     * binds first also starts the socket read loop.
     */
    @Synchronized
    private fun ensureListening(): Result<Unit> {
        if (listening) return Result.success(Unit)
        listening = true
        return runCatching {
            // Undo handshake()'s bounded wait - the session listener must block
            // indefinitely, not time out every idle interval.
            socket.soTimeout = 0
            listenerThread = Thread { receiveLoop() }.apply {
                name = "mcupc-listener"
                isDaemon = true
                start()
            }
        }.onFailure { cause ->
            listening = false
            log.error("Could not start the listener thread", cause)
        }
    }

    /**
     * This client's [ReliableChannelEngine], minting it and arming its
     * `mcupc-retransmit` tick on first use: created lazily on the *first* of an
     * app-thread `channel(RELIABLE_ORDERED).send`/`actuate*` call or an inbound
     * `0xA0`/`0xA1` (mirrors [HandshakeCoordinator.reliableEngineFor]).
     * `@Synchronized` so a listener-thread inbound and an app-thread send cannot
     * mint two engines.
     */
    @Synchronized
    private fun reliableEngine(): ReliableChannelEngine {
        reliableEngine?.let { return it }
        val engine = ReliableChannelEngine(sendRaw = ::sendDatagram)
        reliableEngine = engine
        retransmit.scheduleTick(serverEndpoint, ReliableChannelEngine.DEFAULT_RETRANSMIT_TICK_MILLIS) {
            engine.onRetransmitTick()
        }.onFailure { log.warn("Could not arm the retransmit tick", it) }
        log.info("Opened reliable-ordered channel to {}:{}", serverAddress, serverPort)
        return engine
    }

    /**
     * Offers [bytes] to the reliable-ordered channel, creating the engine and
     * arming its retransmit tick on first use.
     * @return success once accepted for reliable delivery (buffered and sequenced -
     * not necessarily already on the wire); failure with
     * [ReliableMessageTooLargeException] if [bytes] exceeds
     * [reliableMaxMessageBytes], [ReliableWindowFullException] if the in-flight
     * window is full, or [IllegalStateException] if [stop] has already run
     */
    private fun sendReliable(bytes: ByteArray): Result<Unit> {
        if (stopped) return Result.failure(IllegalStateException("client stopped"))
        if (bytes.size > reliableMaxMessageBytes) {
            log.warn("Reliable send of {} byte(s) exceeds the {}-byte cap", bytes.size, reliableMaxMessageBytes)
            return Result.failure(ReliableMessageTooLargeException(bytes.size, reliableMaxMessageBytes))
        }
        val engine = reliableEngine()
        return when (val outcome = engine.sendReliable(bytes)) {
            is ReliableChannelEngine.SendOutcome.Accepted -> Result.success(Unit)
            ReliableChannelEngine.SendOutcome.WindowFull -> {
                log.debug("Reliable in-flight window full")
                Result.failure(ReliableWindowFullException(engine.windowSize))
            }
        }
    }

    /**
     * Body of the listener thread: switch on the [DatagramType] tag and deliver the stripped
     * payload of a `0x90` on channel `0x00`; a `0x90`, `0xA0` or `0xA1` on any other channel is
     * dropped with a WARN, before any delivery, acknowledgement processing or engine creation.
     */
    private fun receiveLoop() {
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
                // to the dispatch executor would be a data race. Origin is captured here too -
                // packet itself must not escape this block, and the reliable branch below needs
                // it to screen against serverEndpoint (§12 B2).
                packet.data.copyOf(packet.length) to packet.socketAddress as InetSocketAddress
            }.onSuccess { (bytes, origin) ->
                // Byte 0 is the DatagramType tag - the session is fully framed post-handshake.
                when (DatagramType.ofTagByte(bytes.getOrNull(0))) {
                    DatagramType.KEEPALIVE -> log.trace("Dropped keepalive from server")

                    DatagramType.PROBE_PING ->
                        TransportWireFormat.probeSequenceOf(bytes)?.let { seq ->
                            sendDatagram(TransportWireFormat.probePongDatagram(seq))
                                .onFailure { log.debug("Could not answer a PING", it) }
                        } ?: log.warn("Malformed 0x81 probe from server, dropping")

                    DatagramType.PROBE_PONG ->
                        // A short/garbage 0x82: ignore rather than throw on the listener thread.
                        TransportWireFormat.probeSequenceOf(bytes)
                            ?.let { seq -> linkQualityTracker?.completeProbe(seq) }

                    DatagramType.UNRELIABLE -> {
                        // Decode just the stripped payload here - NOT some already-computed
                        // whole-datagram text - or a handler would see the tag/channel bytes as
                        // replacement characters (§3.12 of the Stage-3 plan).
                        val payload = TransportWireFormat.unreliablePayloadOf(bytes)
                        val channel = TransportWireFormat.unreliableChannelOf(bytes)
                        when {
                            payload == null -> log.warn("Malformed 0x90 datagram from server, dropping")

                            // Issue #40: this branch has no origin screen (#39), so the channel check
                            // applies to every 0x90. After the malformed check, before delivery.
                            // Duplicated verbatim at the other three receive-branch channel checks -
                            // HandshakeCoordinator.deliverData and its RELIABLE_DATA/RELIABLE_ACK
                            // branch, and this listener's reliable branch below - rather than
                            // extracted; see docs/issue-40-channel-byte-guard-architecture.md §6.
                            channel != null && channel != TransportWireFormat.DEFAULT_UNRELIABLE_CHANNEL ->
                                log.warn(
                                    "Unreliable datagram on unsupported channel 0x{} from {}; sender may be a newer 2.x, dropping",
                                    Integer.toHexString(channel.toInt() and 0xFF), origin,
                                )

                            else -> deliverUnreliable?.invoke(payload, String(payload, Charsets.UTF_8).trim())
                                ?: log.debug("No unreliable handler bound, dropping")
                        }
                    }

                    DatagramType.RELIABLE_DATA, DatagramType.RELIABLE_ACK -> {
                        // Client-side counterpart of the server's registrations.findByOrigin guard
                        // (§12 B2): the socket is unconnected, so a stray/spoofed reliable datagram
                        // must not mint the engine or reach the application handler.
                        val channel = ReliableWireFormat.reliableChannelOf(bytes)
                        when {
                            origin != serverEndpoint ->
                                log.debug(
                                    "Reliable datagram from unexpected origin {} (expected {}), dropped",
                                    origin, serverEndpoint,
                                )

                            // Issue #40: after origin screening, before engine creation/ack
                            // processing. Duplicated verbatim at the other three receive-branch
                            // channel checks - HandshakeCoordinator.deliverData and its
                            // RELIABLE_DATA/RELIABLE_ACK branch, and this listener's UNRELIABLE
                            // branch above - rather than extracted; see
                            // docs/issue-40-channel-byte-guard-architecture.md §6 for why.
                            channel != null && channel != ReliableWireFormat.DEFAULT_RELIABLE_CHANNEL ->
                                log.warn(
                                    "Reliable datagram on unsupported channel 0x{} from {}; sender may be a newer 2.x, dropping",
                                    Integer.toHexString(channel.toInt() and 0xFF), origin,
                                )

                            else -> {
                                val delivered = reliableEngine().onInboundDatagram(bytes) // never throws (Stage 2 contract)
                                val handler = deliverReliable
                                if (handler == null) {
                                    if (delivered.isNotEmpty()) {
                                        log.debug("No reliable handler bound, dropping {} payload(s)", delivered.size)
                                    }
                                } else {
                                    delivered.forEach(handler)
                                }
                            }
                        }
                    }

                    null -> {
                        val byte0 = bytes.getOrNull(0)?.toInt()?.and(0xFF)
                        if (byte0 != null && byte0 >= 0x80) {
                            log.debug("Unhandled datagram type 0x{} from server, dropping", Integer.toHexString(byte0))
                        } else {
                            log.warn("Unframed/unknown datagram from server (byte0={}), dropping", byte0)
                        }
                    }
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
    @Deprecated(
        "Use channel(DeliveryMode.UNRELIABLE).send(...) - see the 2.0 channel API",
        ReplaceWith("channel(DeliveryMode.UNRELIABLE).send(message)", "com.spartanlabs.webtools.udp.DeliveryMode"),
    )
    fun send(message: String): Result<Unit> = sendUnreliable(message.toByteArray(Charsets.UTF_8))

    /**
     * Sends [bytes] to the server as the payload of one `0x90` unreliable
     * application datagram (`[0x90][channel][bytes]`) over the shared socket. Safe
     * to call from any thread. [send]`(String)` is a UTF-8 wrapper over the same
     * private [sendUnreliable] this delegates to.
     *
     * Any payload bytes are carried intact - there is no reserved first byte. A
     * framed datagram above the OS datagram limit fails the returned [Result] (the
     * cause is logged) rather than being sent; for real-network use keep frames
     * under the path MTU (~1200 bytes) to avoid IP fragmentation.
     * @param bytes the raw application payload
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    @Deprecated(
        "Use channel(DeliveryMode.UNRELIABLE).send(...) - see the 2.0 channel API",
        ReplaceWith("channel(DeliveryMode.UNRELIABLE).send(bytes)", "com.spartanlabs.webtools.udp.DeliveryMode"),
    )
    fun send(bytes: ByteArray): Result<Unit> = sendUnreliable(bytes)

    /**
     * Frames [bytes] as an `0x90` unreliable datagram and puts it on the wire - the
     * single implementation of the unreliable send path that [send]`(String)`,
     * [send]`(ByteArray)`, and `channel(UNRELIABLE).send` all funnel through, so
     * deprecating the two public overloads above does not make either warn against
     * the other.
     */
    private fun sendUnreliable(bytes: ByteArray): Result<Unit> =
        sendDatagram(TransportWireFormat.unreliableDatagram(bytes))

    /**
     * Puts one already-formed datagram on the wire verbatim - no framing. The raw
     * socket primitive shared by [send] (which frames first) and every control
     * path (the keepalive, the probe tick, an inbound-`PING` reply), none of which
     * must be framed.
     * @param bytes the exact datagram bytes to send
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    private fun sendDatagram(bytes: ByteArray): Result<Unit> = runCatching {
        socket.send(DatagramPacket(bytes, bytes.size, serverAddress, serverPort))
        // Stamped only after a successful send: a failed send must not defer the next KA,
        // since nothing reached the wire. Every outbound datagram funnels through here -
        // send(String)/send(ByteArray) and sendKeepAlive() included - so any traffic defers a scheduled KA.
        lastOutboundAtNanos = System.nanoTime()
    }.onFailure { log.error("Could not send to {}:{}", serverAddress, serverPort, it) }

    /**
     * Sends one minimal `0x80` keepalive datagram, one-shot (mirrors
     * [Connection.keepAlive]). For a library-managed cadence use [startKeepAlive].
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    fun sendKeepAlive(): Result<Unit> = sendDatagram(TransportWireFormat.keepaliveDatagram())

    /**
     * Starts an opt-in, idle-aware background keepalive: every ~[intervalMillis] of
     * output silence this client sends one `0x80` keepalive on the shared socket to
     * hold its NAT mapping open, until [stopKeepAlive] or [stop]. Application sends
     * reset the idle timer, so a busy client sends no redundant keepalives.
     *
     * A convenience over [sendKeepAlive] - it removes the hand-rolled timer every
     * consumer otherwise writes. [sendKeepAlive] itself is unchanged and still owns
     * no timer. Calling this again replaces the schedule (last call wins). Backed by
     * one daemon thread (`mcupc-keepalive`) created on the first call.
     *
     * @param intervalMillis output-idle time before a keepalive is sent; must be
     * > 0. Defaults to [TransportWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS]
     * (20 s). A keepalive may go out up to one quarter-interval (max 5 s) late.
     * @return [Result.success] once the schedule is armed; [Result.failure] with an
     * [IllegalArgumentException] for a non-positive interval, or an
     * [IllegalStateException] if [stop] has already run.
     */
    @JvmOverloads
    fun startKeepAlive(intervalMillis: Long = TransportWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS): Result<Unit> =
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
     * one `0x81` probe (an 8-byte big-endian sequence) to the server, and each
     * matching `0x82` reply updates a smoothed RTT / jitter / packet-loss estimate
     * readable via [linkQuality]. Off until called; runs until [stopProbe] or
     * [stop]. Backed by one daemon thread (`mcupc-probe`) created on the first
     * call. Calling this again re-arms it (last call wins). Call after [handshake]
     * - probes sent before registration go unanswered and register as loss.
     *
     * The probe requires both ends on `webtools-udp` `2.0.0`+ (as does every
     * framed datagram) - a cross-major peer is rejected at the handshake.
     *
     * @param intervalMillis probe period; must be >=
     * [TransportWireFormat.MIN_PROBE_INTERVAL_MILLIS] (250 ms). Default
     * [TransportWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS] (1 s).
     * @return [Result.success] once armed; [Result.failure] with
     * [IllegalArgumentException] if [intervalMillis] is below the floor, or
     * [IllegalStateException] if [stop] has already run.
     */
    @JvmOverloads
    fun startProbe(intervalMillis: Long = TransportWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS): Result<Unit> {
        // Floor check duplicated verbatim in HandshakeCoordinator.scheduleProbe (Issue #34) rather
        // than extracted - see docs/issue-34-probe-cadence-architecture.md §6.1. It must run before
        // any state is touched: writing probeIntervalMillis first would let a rejected call switch
        // off loss aging on a probe that is already running. The floor lives on TransportWireFormat,
        // not PeriodicSchedule, because scheduleTick is shared with the 50 ms retransmit tick, and not
        // on KeepAlive, which held the old poll-division clamp this floor replaces but is
        // scheduling-cadence policy, not a probe-protocol constant.
        return runCatching {
            require(intervalMillis >= TransportWireFormat.MIN_PROBE_INTERVAL_MILLIS) {
                "intervalMillis must be >= ${TransportWireFormat.MIN_PROBE_INTERVAL_MILLIS}, was $intervalMillis"
            }
        }.flatMap {
            val tracker = linkQualityTracker ?: LinkQualityTracker().also { linkQualityTracker = it }
            tracker.probeIntervalMillis = intervalMillis
            probe.scheduleTick(serverEndpoint, intervalMillis) {
                tracker.sweep()
                sendDatagram(TransportWireFormat.probePingDatagram(tracker.beginProbe()))
                    .onFailure { log.warn("Scheduled probe send failed", it) }
            }
        }.onFailure { log.error("Could not start the link-quality probe", it) }
    }

    /**
     * Stops the probe started by [startProbe]. Idempotent; [stop] also does this.
     * The last [linkQuality] snapshot remains readable. A probe still unanswered
     * when the probe stopped is counted as lost once it passes the loss horizon,
     * so `packetLossRatio` can still settle for up to three probe intervals after
     * this call.
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
     * armed one), shuts the probe scheduler (if [startProbe] armed one), shuts the
     * retransmit scheduler and closes the reliable engine (if a reliable channel
     * was ever opened), closes the socket, and shuts the dispatch executor. A
     * reliable send after this returns fails with [IllegalStateException] rather
     * than buffering into a closed socket (design §7.7).
     * Every step runs even if an earlier one failed, so a partial failure never
     * leaks the bound port. Once called, this instance should be discarded.
     * @return [Result.success] if every step succeeded, or the first failure encountered
     */
    fun stop(): Result<Unit> {
        stopped = true
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
        // Shut the retransmit scheduler before the socket so a scheduled retransmit never races a close.
        // Independent runCatching from the engine close below (§12 B5): a throwing shutdown() must
        // not skip closing the reliable engine - every step here runs even if an earlier one failed.
        val retransmitStopped = runCatching { retransmit.shutdown() }
            .onFailure { log.warn("Could not cleanly shut the retransmit scheduler", it) }
        val reliableEngineClosed = runCatching { reliableEngine?.close() }.map { }
            .onFailure { log.warn("Could not cleanly close the reliable engine", it) }
        val socketClosed = runCatching { socket.close() }
            .onFailure { cause -> log.error("Could not close the client socket", cause) }
        val executorStopped = runCatching { dispatchExecutor.shutdownNow() }.map { }
            .onFailure { log.warn("Could not cleanly shut the dispatch executor", it) }
        return listenerJoined.flatMap { keepAliveStopped }.flatMap { probeStopped }
            .flatMap { retransmitStopped }.flatMap { reliableEngineClosed }.flatMap { socketClosed }.flatMap { executorStopped }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MultiConnectionUDPClient::class.java)
        private const val HANDSHAKE_TIMEOUT_MILLIS = 4000
        private const val LISTENER_JOIN_TIMEOUT_MILLIS = 1000L
    }
}
