package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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
 * Boundary-Ring notes (mirrors [CommonChannel]'s):
 * - One UDP socket carries the handshake and the entire session.
 * - The JDK permits a concurrent [DatagramSocket.send] while a
 *   [DatagramSocket.receive] is in progress.
 * - [socket] is received on only by the listener thread, but sent on from any
 *   thread ([send], [sendKeepAlive]); the listener thread itself never sends.
 *
 * ### Construction side effects
 * Instantiating this class **binds an ephemeral OS UDP port** immediately - a
 * documented construction side effect, the same convention [CommonChannel] uses.
 *
 * ### Ordering contract (not enforced in code)
 * Call [handshake] once, then [start] once. Calling [start] before a successful
 * [handshake], or calling either twice, is undefined behaviour - matching the
 * level of guarding [MultiConnectionUDPServer] itself applies to its own
 * lifecycle.
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
 *
 * See the sequence diagram in docs/issue-3-public-client-handshake-plan.md §2.2
 * for the canonical handshake/listener/dispatch flow.
 *
 * @param serverAddress the server's address to hand shake with and send to
 * @param serverPort the server's common listen port; defaults to
 * [MultiConnectionUDPServer.COMMON_LISTEN_PORT]
 * @throws java.net.SocketException if an ephemeral local port could not be bound
 */
class MultiConnectionUDPClient(
    private val serverAddress: InetAddress,
    private val serverPort: Int = MultiConnectionUDPServer.COMMON_LISTEN_PORT,
) {
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

    /** The local port [socket] is bound to - the same port every datagram, in both directions, uses. */
    val localPort: Int get() = socket.localPort

    /**
     * Sends `Iam <name>` and blocks (up to [timeoutMillis]) for the server's
     * `REGISTERED` reply, on this same socket. One-shot; call once, before [start].
     * @param name this client's chosen name
     * @param timeoutMillis how long to wait for the server's reply before failing
     * @return [Result.success] once the server has replied `REGISTERED`, or the
     * failure that prevented it (including a timeout - the server never replied)
     */
    fun handshake(name: String, timeoutMillis: Int = HANDSHAKE_TIMEOUT_MILLIS): Result<Unit> = runCatching {
        val payload = HandshakeWireFormat.handshakeMessage(name).toByteArray(Charsets.UTF_8)
        socket.send(DatagramPacket(payload, payload.size, serverAddress, serverPort))

        socket.soTimeout = timeoutMillis
        val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
        val reply = DatagramPacket(buffer, buffer.size)
        socket.receive(reply) // throws SocketTimeoutException if the server never answers
        String(reply.data, 0, reply.length, Charsets.UTF_8).trim()
    }.flatMap { reply ->
        if (HandshakeWireFormat.isRegistered(reply)) {
            Result.success(Unit)
        } else {
            Result.failure(
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
    fun start(onMessage: (message: String) -> Unit): Result<Unit> {
        listening = true
        return runCatching {
            // Undo handshake()'s bounded wait - the session listener must block
            // indefinitely, not time out every idle interval.
            socket.soTimeout = 0
            listenerThread = Thread { receiveLoop(onMessage) }.apply {
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
    private fun receiveLoop(onMessage: (String) -> Unit) {
        val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
        while (listening) {
            runCatching {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
            }.onSuccess { text ->
                if (HandshakeWireFormat.isKeepAlive(text)) {
                    log.trace("Dropped keepalive from server")
                } else {
                    // Hand delivery to the single-threaded executor so a slow onMessage
                    // never stalls the listener thread. The inner runCatching keeps a
                    // throwing handler from killing the dispatch thread.
                    dispatchExecutor.execute {
                        runCatching { onMessage(text) }.onFailure { log.warn("Message handler threw", it) }
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
    fun send(message: String): Result<Unit> = runCatching {
        val payload = message.toByteArray(Charsets.UTF_8)
        socket.send(DatagramPacket(payload, payload.size, serverAddress, serverPort))
    }.onFailure { log.error("Could not send to {}:{}", serverAddress, serverPort, it) }

    /**
     * Sends one minimal `KA` keepalive datagram, one-shot (mirrors
     * [Connection.keepAlive]).
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    fun sendKeepAlive(): Result<Unit> = send(HandshakeWireFormat.KEEPALIVE_TOKEN)

    /**
     * Stops the listener thread, closes the socket, and shuts the dispatch executor.
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
        val socketClosed = runCatching { socket.close() }
            .onFailure { cause -> log.error("Could not close the client socket", cause) }
        val executorStopped = runCatching { dispatchExecutor.shutdownNow() }.map { }
            .onFailure { log.warn("Could not cleanly shut the dispatch executor", it) }
        return listenerJoined.flatMap { socketClosed }.flatMap { executorStopped }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MultiConnectionUDPClient::class.java)
        private const val HANDSHAKE_TIMEOUT_MILLIS = 4000
        private const val RECEIVE_BUFFER_BYTES = 1024
        private const val LISTENER_JOIN_TIMEOUT_MILLIS = 1000L
    }
}
