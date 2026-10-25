// webtools-udp — C4 model as code (Structurizr DSL).
// Source of truth for the structure shown on docs/dashboards/webtools-udp/. The page's C4 tab is rendered from this file.
// As-is at WebTools master 7de8207 (2026-10-25), webtools-udp 2.0.0a. The module's source is unchanged since 806ce8e; #61 moved its build file onto the webtools.kotlin-jvm-library convention plugin, and #47 compiles it to the Java 11 floor. Code is authoritative on any conflict.
workspace "WebTools" "Kotlin/JVM internet I/O helpers, published as three independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        dev  = person "Game developer" "Builds a game server or client on the library: subclasses MultiConnectionUDPServer, drives MultiConnectionUDPClient."
        peer = softwareSystem "Peer endpoint" "The other side of a UDP session — another server or client process built on webtools-udp." "External"

        webtools = softwareSystem "WebTools" "Three Kotlin/JVM library modules published to Maven Central under io.github.spartanlaboratories." {
            scraping = container "webtools-scraping" "Connector: open a URL and read it line by line, plus one-shot get, skrape and image download helpers." "Kotlin/JVM library · 1.0.0a"
            browser  = container "webtools-browser"  "WebViewer: renders a URL in headless Chrome and returns the screenshot." "Kotlin/JVM library · 1.0.0a"
            gradleBuild    = container "Gradle build" "Builds, tests and publishes every module; the included build-logic build holds the convention plugins the modules apply, at the Java 11 floor. Publishes nothing of its own." "Gradle 9.7.1 · Kotlin 2.2.0 · Java floor 11" "Internal"
            udp      = container "webtools-udp" "Multi-connection UDP transport: text handshake onto one common port, then framed datagrams over unreliable and reliable-ordered channels, with opt-in keepalive, link-quality probe and idle detection." "Kotlin/JVM library · 2.0.0a · Java 11 bytecode · wire protocol 2" {

                // ---- public surface ----
                server      = component "MultiConnectionUDPServer" "Binds the common port 9998; owns the listener and dispatch threads and three lazily created schedulers; exposes start* / pushToAll* / stop; subclass hooks onClientConnect, admit, onClientDisconnect." "abstract class" "Public"
                client      = component "MultiConnectionUDPClient" "One socket; handshake(); channel(mode); keepalive and probe conveniences; at most one reliable engine and one link-quality tracker." "class" "Public"
                connection  = component "UDPConnection" "One named peer as the server sees it: channel(mode), terminate, keepalive and probe control. Owns no socket — delegates to ClientChannel." "class · implements Connection" "Public"
                channel     = component "UdpChannel" "The delivery-mode-scoped send/bind view returned by channel(mode); DeliveryMode is UNRELIABLE or RELIABLE_ORDERED." "interface · 3 implementations" "Public"
                wire        = component "Wire formats" "HandshakeWireFormat (UTF-8 text), TransportWireFormat (framed control + unreliable), ReliableWireFormat (0xA0 / 0xA1), DatagramType tag-byte enum." "codecs" "Public"
                sendreceive = component "UDPSendReceiveServer" "Standalone two-socket primitive outside the connection stack: no handshake, no Connection, no DeliveryMode — and never will have one." "class" "Public,Standalone"

                // ---- internals ----
                coordinator = component "HandshakeCoordinator" "The server's handshake state machine and tag-byte router; implements ClientChannel; owns Registrations." "internal class" "Internal"
                registry    = component "Registrations" "Copy-on-write registry of connected clients: one Registration per peer with handlers, liveness stamps and per-peer state." "internal class" "Internal"
                engine      = component "ReliableChannelEngine" "One reliable plane per connection: send, ack processing, retransmit tick, reorder, close. Parts: RetransmitBuffer, ReorderBuffer, RtoEstimator, SerialSequence." "internal · @Synchronized · lock order engine → buffer" "Internal"
                scheduler   = component "PeriodicScheduler" "Per-endpoint repeating-task seam: schedule (poll-divided, keepalive) and scheduleTick (exact cadence, probe and retransmit), each on one lazily created daemon executor." "internal class" "Internal"
                quality     = component "LinkQualityTracker" "The probe's RTT and loss estimator (Rtt maths); snapshot exposed as LinkQuality." "internal class" "Internal"
                timing      = component "Timing rules" "KeepAlive, Liveness and HandshakeProtocol — pure timing and parsing rules." "internal" "Internal"

                // ---- planned, not built ----
                core        = component "Protocol core" "A multiplatform protocol core and a datagram-transport seam for a future web client, proposed in an open issue. Not built." "proposed" "Planned"
            }
        }

        // People and systems
        dev -> webtools.udp "Builds a game server or client on"
        dev -> webtools.udp.server "Subclasses; implements onClientConnect"
        dev -> webtools.udp.client "Constructs; handshake(); channel(mode)"
        dev -> webtools.udp.connection "Calls channel(mode).send / actuate on each peer's Connection"
        webtools.udp.coordinator -> dev "Delivers payloads to the bound handlers on mcups-dispatch"

        // Ownership inside the server
        webtools.udp.server -> webtools.udp.coordinator "Owns; every datagram is routed through it"
        webtools.udp.coordinator -> webtools.udp.registry "Owns"
        webtools.udp.registry -> webtools.udp.engine "Owns 0..1 per Registration"
        webtools.udp.registry -> webtools.udp.quality "Owns 0..1 per Registration"
        webtools.udp.connection -> webtools.udp.coordinator "Delegates to, via ClientChannel"
        webtools.udp.coordinator -> webtools.udp.engine "Creates on first use; sends and feeds inbound datagrams"
        webtools.udp.engine -> webtools.udp.coordinator "Returns deliverable payloads, in order"

        // Ownership inside the client
        webtools.udp.client -> webtools.udp.engine "Owns 0..1"
        webtools.udp.client -> webtools.udp.quality "Owns 0..1"

        // Shared seams
        webtools.udp.connection -> webtools.udp.channel "Returns from channel(mode)"
        webtools.udp.client -> webtools.udp.channel "Returns from channel(mode)"
        webtools.udp.server -> webtools.udp.scheduler "Owns 3, lazily"
        webtools.udp.client -> webtools.udp.scheduler "Owns 3, lazily"
        webtools.udp.scheduler -> webtools.udp.engine "Drives onRetransmitTick every 50 ms"
        webtools.udp.coordinator -> webtools.udp.wire "Encodes and decodes with"
        webtools.udp.client -> webtools.udp.wire "Encodes and decodes with"
        webtools.udp.engine -> webtools.udp.wire "ReliableWireFormat"
        webtools.udp.coordinator -> webtools.udp.timing "Applies"

        // The wire
        webtools.udp.server -> peer "UDP datagrams, port 9998"
        webtools.udp.client -> peer "UDP datagrams"
        webtools.udp.engine -> peer "0xA0 / 0xA1 frames"
        peer -> webtools.udp.server "Datagrams arrive on mcups-listener"

        // The build
        webtools.gradleBuild -> webtools.udp "Configures: webtools.kotlin-jvm-library (Kotlin/JVM at the Java 11 floor, verifyJavaFloor, test dependencies, level tasks, publishing)"

        // Planned
        webtools.udp.core -> webtools.udp.wire "Would absorb (planned)"
    }

    views {
        systemContext webtools "Context" "WebTools and who touches it." {
            include *
            autoLayout lr
        }

        container webtools "Containers" "The three published modules and the Gradle build that configures them." {
            include *
            autoLayout lr
        }

        component webtools.udp "Components" "webtools-udp as built at master 7de8207; the planned protocol core is drawn dashed." {
            include *
            autoLayout tb
        }

        dynamic webtools.udp "ReliableSend" "A reliable-ordered send and receive, server side, as built." {
            dev -> webtools.udp.connection "channel(RELIABLE_ORDERED).send(bytes)"
            webtools.udp.connection -> webtools.udp.coordinator "send, via ClientChannel"
            webtools.udp.coordinator -> webtools.udp.engine "reliableEngineFor(reg) — created on first use, arms scheduleTick(50 ms); sendReliable(bytes)"
            webtools.udp.engine -> peer "0xA0 seq · ack · ackBits · payload — on the caller's thread; success = accepted, not delivered"
            peer -> webtools.udp.server "0xA0 or 0xA1, received on mcups-listener"
            webtools.udp.server -> webtools.udp.coordinator "accept · tag switch · findByOrigin · channel-byte check"
            webtools.udp.coordinator -> webtools.udp.engine "onInboundDatagram: acks first (Karn), then the reorder buffer"
            webtools.udp.engine -> webtools.udp.coordinator "in-order payloads"
            webtools.udp.coordinator -> dev "each payload, in order, on mcups-dispatch"
            webtools.udp.scheduler -> webtools.udp.engine "onRetransmitTick every 50 ms: resend due 0xA0 (≤ 32, RTO doubled up to 5 s) or a standalone 0xA1; idle channel is silent"
            autoLayout lr
        }

        styles {
            element "Person"          { shape person  background #08427b color #ffffff }
            element "Software System" { background #1168bd color #ffffff }
            element "External"        { background #999999 color #ffffff }
            element "Container"       { background #438dd5 color #ffffff }
            element "Component"       { background #85bbf0 color #000000 }
            element "Internal"        { background #c6dcf5 color #000000 border solid }
            element "Standalone"      { background #eeeeee color #000000 border dotted }
            element "Planned"         { background #ffffff color #666666 border dashed opacity 60 }
        }
    }
}
