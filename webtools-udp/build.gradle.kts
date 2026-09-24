// webtools-udp - a multi-client UDP connection layer with a NAT-traversal handshake.
// Runtime dependencies: slf4j-api only (inherited from the root build).

// Explicit per-module version, overriding the root allprojects { version = "1.0.0" }.
// 1.1.0: non-breaking addition of the raw binary datagram send/receive path (Issue #8).
// 1.2.0: configurable receiveBufferBytes on all three receive types (default 65507);
// UDPSendReceiveServer no longer truncates at 1024; documented receive ceiling everywhere (Issue #9).
// 1.3.0: opt-in idle-connection detection - per-connection lastInboundAt,
// idleTimeoutMillis ctor param, and the open onClientDisconnect(conn, reason)
// hook on the dispatch executor (Issue #10).
// 1.4.0: refusable handshake + opaque credential channel - Iam <name> <credential>,
// the open admit(name, peer, credential): Admission hook, and the REFUSED <reason>
// reply surfaced as HandshakeRefusedException (Issue #11).
// 1.5.0: opt-in idle-aware scheduled keepalive - MultiConnectionUDPClient.startKeepAlive /
// stopKeepAlive and Connection.startKeepAlive / stopKeepAlive, each backed by a lazily-created
// daemon ScheduledExecutorService; the one-shot sendKeepAlive() / keepAlive() primitives
// unchanged; no wire change (Issue #12).
// 1.6.0: opt-in per-connection link-quality probe - MultiConnectionUDPClient.startProbe /
// stopProbe / linkQuality and Connection.startProbe / stopProbe / linkQuality, a periodic
// transport-level PING/PONG round trip yielding a smoothed RTT, RTT-variance, and windowed
// packet-loss ratio (LinkQuality); off by default; one lazily-created daemon
// ScheduledExecutorService per side. New wire tokens PING/PONG, additive (Issue #13).
// 2.0.0-alpha1: framed transport wire break (Issue #14, Stage 1 of the 2.0 series) -
// every post-REGISTERED datagram leads with a 1-byte DatagramType tag (0x80 keepalive,
// 0x81/0x82 probe PING/PONG with an 8-byte sequence, 0x90 unreliable data + channel byte);
// handshake stays text; REGISTERED reply gains a protocol-version token (REGISTERED 2) so a
// cross-major peer fails handshake() cleanly (IncompatibleProtocolException). Removes the
// HandshakeWireFormat KA/PING/PONG token API and the Issue #8 "lead byte >= 0x80" burden.
// 0xA0/0xA1 reserved for the Stage-2 reliable engine.
// 2.0.0-alpha2: internal reliable-ordered engine (Issue #14, Stage 2 of the 2.0 series) -
// 0xA0/0xA1 promoted from reserved to live DatagramType entries (RELIABLE_DATA / RELIABLE_ACK);
// new internal reliable header codec (ReliableWireFormat), RFC 1982 uint16 serial-number
// arithmetic (SerialSequence), a private per-channel RTO estimator on the Issue #13 Rtt pure
// functions with Karn's algorithm (ReliableRtoEstimator), a rolling outbound retransmit
// sequence buffer with the fixed in-flight window (ReliableRetransmitBuffer), a bounded
// inbound reorder buffer (ReliableReorderBuffer), and the orchestrating ReliableChannelEngine -
// all internal, socket-free, and unwired. No new production capability in HandshakeCoordinator /
// MultiConnectionUDPClient; the two newly-live tags still WARN-drop exactly as a reserved tag
// did in alpha1 - only enough of a touch to keep both files compiling against the wider
// DatagramType enum. Public channel API + real socket wiring is Stage 3.
// 2.0.0-alpha3: the public reliable channel API (Issue #14, Stage 3 of the 2.0 series) -
// DeliveryMode (UNRELIABLE / RELIABLE_ORDERED), UdpChannel (send/actuate/actuateBytes) and
// Connection.channel(mode) / MultiConnectionUDPClient.channel(mode); two typed failures,
// ReliableWindowFullException and ReliableMessageTooLargeException, sharing the open
// ReliableSendFailure supertype; a message-size cap (reliableMaxMessageBytes, default 1024,
// max 8192) on both server and client constructors. The Stage-2 ReliableChannelEngine is
// wired live into HandshakeCoordinator / UDPConnection / MultiConnectionUDPClient /
// MultiConnectionUDPServer - the 0xA0/0xA1 frames now go out on the wire - behind a new
// lazily-created mcup{c,s}-retransmit PeriodicScheduler thread per side (a new internal
// PeriodicSchedule.scheduleTick seam, since the existing schedule() cannot express a
// sub-250ms cadence). Eight pre-existing members ship deprecated (WARNING) with a working
// ReplaceWith, fully functional: Connection.push(String|ByteArray)/actuate/actuateBytes and
// MultiConnectionUDPClient.send(String|ByteArray)/start/startBytes. Server-wide
// MultiConnectionUDPServer.start/startBytes/pushToAll are not deprecated (no per-Connection
// replacement) and gain reliable siblings startReliable/pushToAllReliable. No new public
// signature is removed; additive at both the API and wire level.
// 2.0.0-alpha4: link-quality probe cadence fix (Issue #34) - HandshakeCoordinator.scheduleProbe
// and MultiConnectionUDPClient.startProbe move from PeriodicSchedule.schedule (poll-divided,
// intervalMillis/4 clamped to 250..5000ms, with no due-ness check in the probe tick) to
// scheduleTick (exact cadence, the Stage-3 seam), so the probe now fires once per configured
// interval - previously too slow below 250 ms, exactly 4x too fast at the 1 s default, and more
// than 4x too fast above 20 s. New public TransportWireFormat.MIN_PROBE_INTERVAL_MILLIS = 250L;
// both entry points now reject an intervalMillis below it with IllegalArgumentException rather
// than silently clamping. The interval is now validated before any state is touched, which also
// fixes a rejected startProbe (e.g. 0 after a successful arm) overwriting the running probe's
// interval and permanently disabling its loss detection. LinkQualityTracker.snapshot() now sweeps
// before reporting, so linkQuality() reflects loss as of the call; a probe still unanswered at
// stopProbe() therefore settles to lost within three intervals instead of staying uncounted.
// No wire change; a behavioural correction plus a narrowed, enforced input range on an
// unpublished alpha.
version = "2.0.0-alpha4"

// Serialises the test tasks that bind the fixed common UDP port (9998) - `test`,
// `integrationTest`, `e2eTest`, and `nonfunctionalTest` - so Gradle never runs two of
// them in parallel workers and hits a BindException. Level tasks that touch no socket
// are unaffected. Only this module binds the port, so the lock lives here, not in the
// root build.
abstract class CommonUdpPortLock : BuildService<BuildServiceParameters.None>

val commonUdpPortLock =
    gradle.sharedServices.registerIfAbsent("commonUdpPortLock", CommonUdpPortLock::class) {
        maxParallelUsages = 1
    }

listOf("test", "integrationTest", "e2eTest", "nonfunctionalTest").forEach { name ->
    tasks.named<Test>(name) { usesService(commonUdpPortLock) }
}

mavenPublishing {
    coordinates(group.toString(), "webtools-udp", version.toString())
    pom {
        name.set("WebTools UDP")
        description.set("A multi-client UDP connection layer with a NAT-traversal handshake.")
    }
}
