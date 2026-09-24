package com.spartanlabs.webtools.udp

import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * The heart of [MultiConnectionUDPServer], with the socket pulled out behind
 * injected collaborators so it can be tested with no real I/O. It is four things
 * at once:
 *
 * - the **handshake state machine** - a first `Iam` from a new origin is screened
 *   by [admit] before anything else; on [Admission.Refused] the server replies
 *   `REFUSED <reason>`, registers nothing, and does not fire [onRegistered].
 *   Otherwise it registers a [Connection] and replies `REGISTERED 2` (the
 *   accepted reply plus the framed-transport wire major); a retransmit from a
 *   known origin just re-sends it (no re-screening); a first `Iam` from a name
 *   that is already registered under a different, now-stale origin (e.g. after a
 *   NAT rebind) supersedes it - but only once [admit] has admitted the newcomer,
 *   so a refused name-spoof cannot evict the incumbent - the stale registration
 *   is terminated and removed before the new one is added;
 * - the **inbound-datagram router** - [accept] classifies every datagram on its
 *   [DatagramType] tag (byte 0): a `0x80` keepalive (dropped), a `0x81`/`0x82`
 *   probe, or a `0x90` unreliable frame whose stripped payload is handed to the
 *   dispatch executor for the bound handler. An unframed `Iam` (byte 0 `< 0x80`)
 *   runs the handshake state machine; any other unframed datagram is dropped with
 *   a WARN (a pre-`2.0` sender);
 * - the [ClientChannel] implementation the connections it mints delegate to for
 *   sending, binding, and deregistering;
 * - the **liveness tracker** - when constructed with a positive
 *   [idleTimeoutMillis] it stamps [Registration.lastInboundAt] and clears the
 *   [Registration.timedOut] latch on every inbound datagram, and
 *   [sweepIdleConnections] reports connections idle beyond the threshold via
 *   `onDisconnect(_, TIMEOUT)`. It also routes the
 *   `SUPERSEDED` / `TERMINATED` reasons through the same callback;
 * - the **keepalive scheduler seam** - [scheduleKeepAlive] arms an idle-aware
 *   `0x80` keepalive timer per connection (refreshing off
 *   [Registration.lastOutboundAt], which [send] stamps once any keepalive is
 *   armed), cancelled on [deregister] or a same-name supersede;
 * - the **link-quality probe seam** - [scheduleProbe] arms a periodic `0x81`
 *   probe timer per connection (an 8-byte big-endian sequence), folds each
 *   `0x82` reply into the connection's [Registration.linkQuality] estimator,
 *   answers an inbound `0x81` from a registered origin with an `0x82`, and is
 *   cancelled on [deregister] or a same-name supersede;
 * - the **reliable-ordered channel seam** - an inbound `0xA0`/`0xA1`, or a
 *   `sendReliable`/`bindReliable` call, lazily mints a per-connection
 *   [ReliableChannelEngine] and arms its `mcups-retransmit` tick
 *   ([reliableEngineFor]); delivered payloads reach [Registration.onReliable]
 *   via the dispatch executor, in order ([deliverReliable]); the engine is
 *   closed and its tick cancelled on [deregister] or a same-name supersede.
 *
 * @param newConnection builds the connection for a new client, given its name,
 * handshake origin, and the [ClientChannel] it should delegate to (always `this`)
 * @param sender sends raw bytes to a client endpoint; its [Result] is propagated
 * @param onRegistered invoked exactly once per newly registered client, after its
 * `REGISTERED 2` reply has been sent - never for a retransmitted handshake
 * @param admit the pre-accept screen for a first `Iam` from an unknown origin,
 * given the parsed name, origin, and opaque credential (`""` if none). Invoked
 * inline on the listener thread before any supersede or registration; expected to
 * return promptly. [Admission.Refused] short-circuits to a `REFUSED <reason>`
 * reply with nothing registered; a thrown exception drops the handshake.
 * @param dispatch hands a block to the server's single-threaded dispatch executor
 * @param onDisconnect invoked when a registered connection stops being
 * addressable (`TIMEOUT` / `SUPERSEDED` / `TERMINATED`); called inline, so it is
 * expected to marshal onto the dispatch executor itself
 * @param idleTimeoutMillis idle threshold in milliseconds; `0` (the default)
 * disables liveness tracking entirely - no per-datagram refresh, no sweep
 * @param keepAliveSchedule the timer seam backing [scheduleKeepAlive] /
 * [cancelKeepAlive]; a [PeriodicScheduler] in production, a fake in tests
 * @param probeSchedule the timer seam backing [scheduleProbe] / [cancelProbe]; a
 * separate [PeriodicScheduler] instance in production, a fake in tests
 * @param retransmitSchedule the timer seam backing the per-connection reliable
 * retransmit tick ([reliableEngineFor]); a separate [PeriodicScheduler] instance
 * in production, a fake in tests
 * @param reliableMaxMessageBytes the configured reliable-message-size cap
 * ([ClientChannel.reliableMaxMessageBytes])
 */
internal class HandshakeCoordinator(
    private val newConnection: (name: String, peer: InetSocketAddress, channel: ClientChannel) -> Connection,
    private val sender: (bytes: ByteArray, to: InetSocketAddress) -> Result<Unit>,
    private val onRegistered: (Connection) -> Unit,
    private val admit: (name: String, peer: InetSocketAddress, credential: String) -> Admission,
    private val dispatch: (block: () -> Unit) -> Unit,
    private val onDisconnect: (connection: Connection, reason: DisconnectReason) -> Unit,
    private val idleTimeoutMillis: Long,
    private val keepAliveSchedule: PeriodicSchedule,
    private val probeSchedule: PeriodicSchedule,
    private val retransmitSchedule: PeriodicSchedule,
    override val reliableMaxMessageBytes: Int,
) : ClientChannel {
    private val registrations = Registrations()

    private val livenessTracked = idleTimeoutMillis > 0L

    /**
     * Flipped `true` the first time [scheduleKeepAlive] runs and never flipped
     * back. While `false`, [send] does no extra per-datagram work.
     */
    @Volatile
    private var keepAliveTracked = false

    /** Cleared by [stopNotifying] so `stop()` teardown fires no disconnect callbacks. */
    @Volatile
    private var notifyingDisconnects = true

    /** Suppresses every disconnect callback from here on - used by `stop()` teardown. */
    fun stopNotifying() {
        notifyingDisconnects = false
    }

    /** How many clients have completed the handshake. */
    val size: Int get() = registrations.size

    /**
     * A stable snapshot of every completed handshake, oldest first.
     * @return an immutable copy, safe to iterate while new handshakes arrive
     */
    fun snapshot(): List<Registration> = registrations.snapshot()

    /**
     * The single entry point the listener loop calls for every inbound datagram.
     * Classifies before acting on the [DatagramType] tag (byte 0): a `0x80`
     * keepalive is dropped (success, no dispatch); a `0x81`/`0x82` probe is
     * answered / folded; a `0x90` frame's stripped payload is routed to the bound
     * handler; an unframed `Iam` runs the handshake state machine inline; anything
     * else is dropped with a WARN. When idle detection is enabled, `accept` also
     * stamps the origin's last-inbound time and clears any prior TIMEOUT latch,
     * before classifying.
     *
     * @param origin the datagram's post-NAT source - where any reply is addressed
     * @param bytes the exact-length datagram body; the tag switch works off
     * `bytes[0]` and, for a `0x90` frame, the stripped payload is what reaches a
     * bound bytes handler
     * @param text the trimmed datagram text - now consulted only for the unframed
     * `Iam` handshake-bootstrap branch
     * @return [Result.success] if handled or harmlessly ignored, or [Result.failure]
     * if a recognised handshake was malformed or its reply could not be delivered
     */
    fun accept(origin: InetSocketAddress, bytes: ByteArray, text: String): Result<Unit> {
        if (livenessTracked) registrations.findByOrigin(origin)?.let {
            // nanoTime(), not currentTimeMillis(): a monotonic clock so an NTP/DST/manual
            // wall-clock step cannot fabricate a timeout or mask a real one.
            it.lastInboundAt = System.nanoTime() // stamp receipt; sibling of the un-latch below
            it.timedOut = false // inbound traffic un-latches a previously-notified peer
        }
        return classify(origin, bytes, text)
    }

    private fun classify(origin: InetSocketAddress, bytes: ByteArray, text: String): Result<Unit> {
        // Byte 0 is authoritative: control vs. application is a tag switch, never a token match.
        val byte0 = bytes.getOrNull(0)?.toInt()?.and(0xFF)
        return when (DatagramType.ofTagByte(bytes.getOrNull(0))) {
            DatagramType.KEEPALIVE ->
                Result.success(Unit).also { log.trace("Keepalive from {}", origin) }

            DatagramType.PROBE_PING -> {
                val reg = registrations.findByOrigin(origin)
                val seq = TransportWireFormat.probeSequenceOf(bytes)
                when {
                    reg == null ->
                        Result.success(Unit).also { log.debug("PING from unregistered {}, dropped", origin) }

                    seq == null ->
                        Result.success(Unit).also { log.warn("Malformed 0x81 probe from {}, dropping", origin) }

                    else -> send(TransportWireFormat.probePongDatagram(seq), origin)
                }
            }

            DatagramType.PROBE_PONG -> {
                // A short/garbage 0x82: ignore rather than throw on the listener thread.
                TransportWireFormat.probeSequenceOf(bytes)
                    ?.let { seq -> registrations.findByOrigin(origin)?.linkQuality?.completeProbe(seq) }
                Result.success(Unit)
            }

            DatagramType.UNRELIABLE -> {
                val payload = TransportWireFormat.unreliablePayloadOf(bytes)
                if (payload == null) {
                    Result.success(Unit).also { log.warn("Malformed 0x90 datagram from {}, dropping", origin) }
                } else {
                    deliverData(origin, payload, String(payload, Charsets.UTF_8).trim())
                }
            }

            DatagramType.RELIABLE_DATA, DatagramType.RELIABLE_ACK -> {
                val reg = registrations.findByOrigin(origin)
                if (reg == null) {
                    Result.success(Unit).also { log.debug("Reliable datagram from unregistered {}, dropped", origin) }
                } else {
                    val delivered = reliableEngineFor(reg).onInboundDatagram(bytes) // never throws (Stage 2 contract)
                    deliverReliable(reg, delivered)
                }
            }

            // ofTagByte(null) for a reserved same-major tag (0x83+/0xA2-0xAF) or an unframed byte 0.
            null -> when {
                byte0 != null && byte0 >= 0x80 ->
                    Result.success(Unit).also {
                        log.warn("Unhandled datagram type 0x{} from {}, dropping", Integer.toHexString(byte0), origin)
                    }

                HandshakeProtocol.isHandshake(text.split(' ')) -> handleHandshake(origin, text.split(' '))

                else -> Result.success(Unit).also {
                    log.warn("Unframed datagram from {}; sender may be pre-2.0, dropping", origin)
                }
            }
        }
    }

    /**
     * Two-argument [accept] overload retained for callers that only have the
     * datagram text - delegates with `bytes` derived from a UTF-8 encode of [text].
     */
    fun accept(origin: InetSocketAddress, text: String): Result<Unit> =
        accept(origin, text.toByteArray(Charsets.UTF_8), text)

    private fun handleHandshake(origin: InetSocketAddress, tokens: List<String>): Result<Unit> =
        HandshakeProtocol.parseHandshake(tokens).flatMap { (name, credential) ->
            val extra = HandshakeProtocol.extraTokenCount(tokens)
            if (extra > 0) log.debug("Ignoring {} extra handshake token(s)", extra)

            registrations.findByOrigin(origin)?.let {
                // Retransmitted Iam from a known origin - repeat the token it already earned.
                // No re-screening: admit() runs once, at first registration - re-running a
                // non-deterministic admit() (e.g. a capacity gate) on a stray retransmit could
                // refuse an already-admitted client and desync the two sides.
                log.info("Repeating handshake reply for already-registered origin {}", origin)
                send(HandshakeProtocol.REGISTERED_DATAGRAM, origin)
            } ?: run {
                // Screen the newcomer BEFORE supersede / registration / reply. Because this runs
                // ahead of findByName(name), a Refused newcomer never reaches the supersede code,
                // so an attacker spoofing an existing name from a fresh origin cannot evict the
                // legitimate incumbent.
                val admission = runCatching { admit(name, origin, credential) }.getOrElse { cause ->
                    log.warn("admit(...) threw for '{}' from {} - dropping the handshake", name, origin, cause)
                    return@flatMap Result.failure(cause)
                }
                if (admission is Admission.Refused) {
                    log.info("Refused handshake for '{}' from {}: {}", name, origin, admission.reason)
                    // A REFUSED datagram that went out is a successful server action, not a failure.
                    return@flatMap send(
                        HandshakeWireFormat.refusedMessage(admission.reason).toByteArray(Charsets.UTF_8),
                        origin,
                    )
                }

                // A same-name registration under a different origin is a stale entry (e.g. a NAT
                // rebind), not a distinct client - supersede it directly against Registrations,
                // rather than relying solely on stale.connection.terminate(), so pruning stays
                // correct even for fakes whose terminate() doesn't reach back into this registry.
                registrations.findByName(name)?.let { stale ->
                    log.info("Superseding stale registration for '{}': {} -> {}", name, stale.origin, origin)
                    registrations.removeByOrigin(stale.origin)
                    keepAliveSchedule.cancel(stale.origin)
                    probeSchedule.cancel(stale.origin)
                    retransmitSchedule.cancel(stale.origin)
                    stale.reliable?.close() // D7: a supersede resets the reliable channel - fresh engine, fresh sequence space
                    if (notifyingDisconnects) onDisconnect(stale.connection, DisconnectReason.SUPERSEDED)
                    stale.connection.terminate()
                        .onFailure { log.warn("Failed to terminate superseded connection '{}'", name, it) }
                }
                val connection = newConnection(name, origin, this)
                registrations.add(Registration(connection))
                log.info("Registered connection '{}' for {}", name, origin)
                send(HandshakeProtocol.REGISTERED_DATAGRAM, origin).map { onRegistered(connection) }
            }
        }

    private fun deliverData(origin: InetSocketAddress, bytes: ByteArray, text: String): Result<Unit> {
        val registration = registrations.findByOrigin(origin)
            ?: return Result.success(Unit).also { log.debug("Dropped datagram from unregistered {}", origin) }
        // A bytes handler, if bound, wins over a text handler - the two are mutually
        // exclusive in practice (bind/bindBytes null the other) but check bytes first.
        val bytesHandler = registration.onBytes
        val textHandler = registration.onMessage
        // Hand delivery to the single-threaded executor so a slow handler never stalls the
        // listener thread (and therefore the handshake). The inner runCatching keeps a
        // throwing handler from killing the dispatch thread.
        return when {
            bytesHandler != null -> runCatching {
                dispatch { runCatching { bytesHandler(bytes) }.onFailure { log.warn("Handler for {} threw", origin, it) } }
            }

            textHandler != null -> runCatching {
                dispatch { runCatching { textHandler(text) }.onFailure { log.warn("Handler for {} threw", origin, it) } }
            }

            else -> Result.success(Unit).also { log.debug("No handler bound for {}, dropping", origin) }
        }
    }

    /**
     * Returns [reg]'s [ReliableChannelEngine], minting one and arming its
     * `mcups-retransmit` tick on first use (§3.3): created lazily on the
     * *first* of an app-thread `sendReliable`/`bindReliable` or an inbound
     * `0xA0`/`0xA1` for [reg] - trigger (2) is not optional, or a peer whose
     * application never opens the channel would never ack, wedging the
     * sender's window. `@Synchronized` so a listener-thread inbound and an
     * app-thread send cannot mint two engines for one peer.
     */
    @Synchronized
    private fun reliableEngineFor(reg: Registration): ReliableChannelEngine {
        reg.reliable?.let { return it }
        val engine = ReliableChannelEngine(sendRaw = { send(it, reg.origin) })
        reg.reliable = engine
        retransmitSchedule.scheduleTick(reg.origin, ReliableChannelEngine.DEFAULT_RETRANSMIT_TICK_MILLIS) {
            engine.onRetransmitTick()
        }.onFailure { log.warn("Could not arm the retransmit tick for {}", reg.origin, it) }
        log.info("Opened reliable-ordered channel for {}", reg.origin)
        return engine
    }

    /**
     * Mirrors [deliverData] for the reliable plane: hands each of [payloads] to
     * the dispatch executor, in order, wrapped in `runCatching` so a throwing
     * handler cannot kill `mcups-dispatch`. DEBUG-logs and drops when
     * [Registration.onReliable] is unbound - the right shape for "the peer is
     * using a feature I am not listening to" (§3.3).
     */
    private fun deliverReliable(reg: Registration, payloads: List<ByteArray>): Result<Unit> {
        val handler = reg.onReliable
        if (handler == null) {
            if (payloads.isNotEmpty()) {
                log.debug("No reliable handler bound for {}, dropping {} payload(s)", reg.origin, payloads.size)
            }
            return Result.success(Unit)
        }
        return payloads.fold(Result.success(Unit)) { delivered, payload ->
            delivered.flatMap {
                runCatching {
                    dispatch {
                        runCatching { handler(payload) }.onFailure { log.warn("Reliable handler for {} threw", reg.origin, it) }
                    }
                }
            }
        }
    }

    // --- ClientChannel ---

    override fun send(bytes: ByteArray, to: InetSocketAddress): Result<Unit> {
        // Only pay the origin scan once a keepalive is armed anywhere (default-cost-zero,
        // mirroring livenessTracked); keepAliveTracked never flips back to false.
        if (keepAliveTracked) registrations.findByOrigin(to)?.let { it.lastOutboundAt = System.nanoTime() }
        return sender(bytes, to)
    }

    override fun scheduleKeepAlive(peer: InetSocketAddress, intervalMillis: Long): Result<Unit> {
        keepAliveTracked = true
        return keepAliveSchedule.schedule(peer, intervalMillis) {
            val reg = registrations.findByOrigin(peer) ?: return@schedule
            if (KeepAlive.isDue(reg.lastOutboundAt, System.nanoTime(), intervalMillis)) {
                send(TransportWireFormat.keepaliveDatagram(), peer)
                    .onFailure { log.warn("Scheduled keepalive to {} failed", peer, it) }
            }
        }
    }

    override fun cancelKeepAlive(peer: InetSocketAddress): Result<Unit> =
        runCatching { keepAliveSchedule.cancel(peer) }

    /** Shuts the keepalive scheduler; called by `MultiConnectionUDPServer.stop()`. */
    fun shutKeepAlive() {
        keepAliveSchedule.shutdown()
    }

    override fun scheduleProbe(peer: InetSocketAddress, intervalMillis: Long): Result<Unit> {
        // Floor check duplicated verbatim in MultiConnectionUDPClient.startProbe (Issue #34) rather
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
            val reg = registrations.findByOrigin(peer)
                ?: return@flatMap Result.failure(IllegalStateException("No registration for $peer"))
            val tracker = reg.linkQuality ?: LinkQualityTracker().also { reg.linkQuality = it }
            tracker.probeIntervalMillis = intervalMillis
            probeSchedule.scheduleTick(peer, intervalMillis) {
                // Re-fetch: a concurrent terminate()/supersede may have dropped the registration.
                val live = registrations.findByOrigin(peer)?.linkQuality ?: return@scheduleTick
                live.sweep()
                send(TransportWireFormat.probePingDatagram(live.beginProbe()), peer)
                    .onFailure { log.warn("Scheduled probe to {} failed", peer, it) }
            }
        }
    }

    override fun cancelProbe(peer: InetSocketAddress): Result<Unit> =
        runCatching { probeSchedule.cancel(peer) }

    override fun linkQualityOf(peer: InetSocketAddress): LinkQuality? =
        registrations.findByOrigin(peer)?.linkQuality?.snapshot()

    /** Shuts the probe scheduler; called by `MultiConnectionUDPServer.stop()`. */
    fun shutProbe() {
        probeSchedule.shutdown()
    }

    /** Shuts the retransmit scheduler; called by `MultiConnectionUDPServer.stop()`. */
    fun shutRetransmit() {
        retransmitSchedule.shutdown()
    }

    override fun bind(peer: InetSocketAddress, onMessage: (String) -> Unit) {
        registrations.findByOrigin(peer)?.let {
            it.onMessage = onMessage
            it.onBytes = null
        }
    }

    override fun bindBytes(peer: InetSocketAddress, onMessage: (ByteArray) -> Unit) {
        registrations.findByOrigin(peer)?.let {
            it.onBytes = onMessage
            it.onMessage = null
        }
    }

    override fun deregister(peer: InetSocketAddress) {
        val reg = registrations.findByOrigin(peer)
        if (registrations.removeByOrigin(peer)) {
            keepAliveSchedule.cancel(peer)
            probeSchedule.cancel(peer)
            retransmitSchedule.cancel(peer)
            reg?.reliable?.close() // design §7.7: teardown discards un-acked reliable data
            log.info("Deregistered connection for {}", peer)
            if (notifyingDisconnects && reg != null) {
                onDisconnect(reg.connection, DisconnectReason.TERMINATED)
            }
        }
    }

    override fun sendReliable(peer: InetSocketAddress, bytes: ByteArray): Result<Unit> {
        val reg = registrations.findByOrigin(peer)
            ?: return Result.failure(IllegalStateException("No registration for $peer"))
        if (bytes.size > reliableMaxMessageBytes) {
            log.warn(
                "Reliable send to {} of {} byte(s) exceeds the {}-byte cap",
                peer, bytes.size, reliableMaxMessageBytes,
            )
            return Result.failure(ReliableMessageTooLargeException(bytes.size, reliableMaxMessageBytes))
        }
        val engine = reliableEngineFor(reg)
        return when (val outcome = engine.sendReliable(bytes)) {
            is ReliableChannelEngine.SendOutcome.Accepted -> Result.success(Unit)
            ReliableChannelEngine.SendOutcome.WindowFull -> {
                log.debug("Reliable in-flight window full for {}", peer)
                Result.failure(ReliableWindowFullException(engine.windowSize))
            }
        }
    }

    override fun bindReliable(peer: InetSocketAddress, onMessage: (ByteArray) -> Unit): Result<Unit> {
        val reg = registrations.findByOrigin(peer)
            ?: return Result.failure(IllegalStateException("No registration for $peer"))
        reg.onReliable = onMessage
        reliableEngineFor(reg) // arm the tick even for the receive-only case (§3.3)
        return Result.success(Unit)
    }

    /**
     * Reports every registered connection that has been idle beyond
     * [idleTimeoutMillis] via `onDisconnect(_, TIMEOUT)`, at most once per idle
     * period (the [Registration.timedOut] latch). Notify-only: an overdue
     * registration is left in place. Safe to call repeatedly from the scheduled
     * liveness sweep; throw-safe per callback.
     */
    fun sweepIdleConnections() {
        val now = System.nanoTime()
        registrations.snapshot().forEach { reg ->
            if (reg.timedOut) return@forEach
            if (!Liveness.isOverdue(reg.lastInboundAt, now, idleTimeoutMillis)) return@forEach
            // A concurrent terminate()/supersede may have removed it since the snapshot.
            if (registrations.findByOrigin(reg.origin) == null) return@forEach
            reg.timedOut = true
            log.info(
                "Connection '{}' ({}) has been idle beyond {} ms - notifying",
                reg.connection.name, reg.origin, idleTimeoutMillis,
            )
            runCatching { onDisconnect(reg.connection, DisconnectReason.TIMEOUT) }
                .onFailure { log.warn("onDisconnect(TIMEOUT) failed for '{}'", reg.connection.name, it) }
        }
    }

    /**
     * Binds [onMessage] as the handler on every registered connection, stopping at
     * the first failure. No socket is started.
     * @param onMessage the callback each connection invokes for every datagram it receives
     * @return [Result.success] if every connection was actuated, or the first failure
     */
    @Suppress("DEPRECATION") // calls the now-deprecated Connection.actuate; backs the still-current MultiConnectionUDPServer.start
    fun actuateAll(onMessage: (message: String) -> Unit): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { actuated, registration ->
            actuated.flatMap { registration.connection.actuate(onMessage) }
        }

    /**
     * Binds [onMessage] as the raw-bytes handler on every registered connection,
     * stopping at the first failure. Mirror of [actuateAll] for the binary path.
     * @param onMessage the callback each connection invokes with an exact-length
     * copy of every datagram it receives
     * @return [Result.success] if every connection was actuated, or the first failure
     */
    @Suppress("DEPRECATION") // calls the now-deprecated Connection.actuateBytes; backs the still-current MultiConnectionUDPServer.startBytes
    fun actuateAllBytes(onMessage: (ByteArray) -> Unit): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { actuated, registration ->
            actuated.flatMap { registration.connection.actuateBytes(onMessage) }
        }

    /**
     * Binds [onMessage] as the reliable-ordered handler on every registered
     * connection. Unlike [actuateAll], **every** connection is attempted even if
     * an earlier one failed - a single peer's full in-flight window (or a
     * not-yet-open channel) is routine and must not stop the rest from being
     * bound; only the reported [Result] short-circuits to the first failure.
     * @param onMessage the callback each connection invokes with the exact-length
     * payload of every reliable message it receives, once, in send order
     * @return [Result.success] if every connection was actuated, or the first failure
     */
    fun actuateAllReliable(onMessage: (ByteArray) -> Unit): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { actuated, registration ->
            val outcome = bindReliable(registration.origin, onMessage)
            actuated.flatMap { outcome }
        }

    /**
     * Sends a message to every registered client's endpoint, stopping at the first failure.
     * @param message the text to send to every client
     * @return [Result.success] if the message reached every client, or the first failure
     */
    fun broadcast(message: String): Result<Unit> = broadcast(message.toByteArray(Charsets.UTF_8))

    /**
     * Sends [bytes] verbatim to every registered client's endpoint, stopping at
     * the first failure. Raw-bytes mirror of [broadcast]`(String)`. A payload
     * above the OS datagram limit fails the [Result] for that client (cause
     * logged) rather than being sent.
     * @param bytes the raw payload to send to every client
     * @return [Result.success] if the datagram reached every client, or the first failure
     */
    fun broadcast(bytes: ByteArray): Result<Unit> {
        // Frame once, then fan out - the shared send seam must not double-frame.
        val datagram = TransportWireFormat.unreliableDatagram(bytes)
        return registrations.snapshot().fold(Result.success(Unit)) { sent, registration ->
            sent.flatMap { send(datagram, registration.connection.peer) }
        }
    }

    /**
     * Sends [bytes] reliably to every registered client. Unlike [broadcast],
     * which short-circuits at the first failure, **every** peer is attempted
     * even if an earlier one failed - a single peer's full in-flight window is
     * routine and must not stop the broadcast from reaching everyone else; only
     * the reported [Result] is the first failure (mirrors [terminateAll]'s fold).
     * @param bytes the raw payload to send reliably to every client
     * @return [Result.success] if every send was accepted, or the first failure
     */
    fun broadcastReliable(bytes: ByteArray): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { sent, registration ->
            val outcome = sendReliable(registration.origin, bytes)
            sent.flatMap { outcome }
        }

    /**
     * Terminates every registered connection. Every connection is terminated even
     * if an earlier one failed; only the reported [Result] short-circuits to the
     * first failure.
     * @return [Result.success] if every connection terminated cleanly, or the first failure
     */
    fun terminateAll(): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { terminated, registration ->
            registration.connection.terminate().let { outcome -> terminated.flatMap { outcome } }
        }

    private companion object {
        private val log = LoggerFactory.getLogger(HandshakeCoordinator::class.java)
    }
}
