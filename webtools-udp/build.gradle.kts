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
version = "1.5.0"

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
