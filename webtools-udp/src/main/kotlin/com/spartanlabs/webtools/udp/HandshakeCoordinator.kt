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
 *   Otherwise it registers a [Connection] and replies with the single token
 *   `REGISTERED`; a retransmit from a known origin just re-sends `REGISTERED`
 *   (no re-screening); a first `Iam` from a name that is already registered under
 *   a different, now-stale origin (e.g. after a NAT rebind) supersedes it - but
 *   only once [admit] has admitted the newcomer, so a refused name-spoof cannot
 *   evict the incumbent - the stale registration is terminated and removed before
 *   the new one is added;
 * - the **inbound-datagram router** - [accept] classifies every datagram as a
 *   handshake, a keepalive (dropped), or application data (handed to the dispatch
 *   executor for the bound handler);
 * - the [ClientChannel] implementation the connections it mints delegate to for
 *   sending, binding, and deregistering;
 * - the **liveness tracker** - when constructed with a positive
 *   [idleTimeoutMillis] it stamps [Registration.lastInboundAt] and clears the
 *   [Registration.timedOut] latch on every inbound datagram, and
 *   [sweepIdleConnections] reports connections idle beyond the threshold via
 *   `onDisconnect(_, TIMEOUT)`. It also routes the
 *   `SUPERSEDED` / `TERMINATED` reasons through the same callback;
 * - the **keepalive scheduler seam** - [scheduleKeepAlive] arms an idle-aware
 *   `KA` timer per connection (refreshing off [Registration.lastOutboundAt],
 *   which [send] stamps once any keepalive is armed), cancelled on [deregister]
 *   or a same-name supersede;
 * - the **link-quality probe seam** - [scheduleProbe] arms a periodic `PING`
 *   timer per connection, folds each `PONG` into the connection's
 *   [Registration.linkQuality] estimator, answers an inbound `PING` from a
 *   registered origin with a `PONG`, and is cancelled on [deregister] or a
 *   same-name supersede.
 *
 * @param newConnection builds the connection for a new client, given its name,
 * handshake origin, and the [ClientChannel] it should delegate to (always `this`)
 * @param sender sends raw bytes to a client endpoint; its [Result] is propagated
 * @param onRegistered invoked exactly once per newly registered client, after its
 * `REGISTERED` reply has been sent - never for a retransmitted handshake
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
     * Classifies before acting: a bare `KA` keepalive is dropped (success, no
     * dispatch); an `Iam` runs the handshake state machine inline; anything else
     * is application data routed to the bound handler. When idle detection is
     * enabled, `accept` also stamps the origin's last-inbound time and clears any
     * prior TIMEOUT latch, before classifying.
     *
     * @param origin the datagram's post-NAT source - where any reply is addressed
     * @param bytes the exact-length datagram body, handed verbatim to a bound
     * bytes handler; classification still runs off [text]
     * @param text the trimmed datagram text
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

    private fun classify(origin: InetSocketAddress, bytes: ByteArray, text: String): Result<Unit> = when {
        HandshakeProtocol.isKeepAlive(text) ->
            Result.success(Unit).also { log.trace("Keepalive from {}", origin) }

        HandshakeProtocol.isProbeRequest(text) -> {
            val reg = registrations.findByOrigin(origin)
            if (reg == null) {
                Result.success(Unit).also { log.debug("PING from unregistered {}, dropped", origin) }
            } else {
                send(HandshakeWireFormat.probeReplyMessage(HandshakeWireFormat.probeToken(text)).toByteArray(Charsets.UTF_8), origin)
            }
        }

        HandshakeProtocol.isProbeReply(text) -> {
            // Non-numeric / bare PONG token: ignore rather than throw on the listener thread - an old or spoofed peer must not break the probe.
            HandshakeWireFormat.probeToken(text).toLongOrNull()
                ?.let { seq -> registrations.findByOrigin(origin)?.linkQuality?.completeProbe(seq) }
            Result.success(Unit)
        }

        HandshakeProtocol.isHandshake(text.split(' ')) -> handleHandshake(origin, text.split(' '))

        else -> deliverData(origin, bytes, text)
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
                send(REGISTERED_BYTES, origin)
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
                    if (notifyingDisconnects) onDisconnect(stale.connection, DisconnectReason.SUPERSEDED)
                    stale.connection.terminate()
                        .onFailure { log.warn("Failed to terminate superseded connection '{}'", name, it) }
                }
                val connection = newConnection(name, origin, this)
                registrations.add(Registration(connection))
                log.info("Registered connection '{}' for {}", name, origin)
                send(REGISTERED_BYTES, origin).map { onRegistered(connection) }
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
                send(HandshakeProtocol.KEEPALIVE_TOKEN.toByteArray(Charsets.UTF_8), peer)
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
        val reg = registrations.findByOrigin(peer)
            ?: return Result.failure(IllegalStateException("No registration for $peer"))
        val tracker = reg.linkQuality ?: LinkQualityTracker().also { reg.linkQuality = it }
        tracker.probeIntervalMillis = intervalMillis
        return probeSchedule.schedule(peer, intervalMillis) {
            // Re-fetch: a concurrent terminate()/supersede may have dropped the registration.
            val live = registrations.findByOrigin(peer)?.linkQuality ?: return@schedule
            live.sweep()
            send(HandshakeWireFormat.probeRequestMessage(live.beginProbe().toString()).toByteArray(Charsets.UTF_8), peer)
                .onFailure { log.warn("Scheduled probe to {} failed", peer, it) }
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
            log.info("Deregistered connection for {}", peer)
            if (notifyingDisconnects && reg != null) {
                onDisconnect(reg.connection, DisconnectReason.TERMINATED)
            }
        }
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
    fun actuateAllBytes(onMessage: (ByteArray) -> Unit): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { actuated, registration ->
            actuated.flatMap { registration.connection.actuateBytes(onMessage) }
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
    fun broadcast(bytes: ByteArray): Result<Unit> =
        registrations.snapshot().fold(Result.success(Unit)) { sent, registration ->
            sent.flatMap { send(bytes, registration.connection.peer) }
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
        private val REGISTERED_BYTES = HandshakeProtocol.REGISTERED_REPLY.toByteArray(Charsets.UTF_8)
    }
}
