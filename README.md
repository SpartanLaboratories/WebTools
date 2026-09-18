# WebTools

A small Kotlin/JVM collection of internet I/O helpers, published as **three
independent, single-concern artifacts** — depend only on the one you need:

| Artifact | Package | Purpose | Third-party runtime deps |
|---|---|---|---|
| `io.github.spartanlaboratories:webtools-udp` | `com.spartanlabs.webtools.udp` | Multi-client UDP connection layer + NAT-traversal handshake | none (slf4j-api only) |
| `io.github.spartanlaboratories:webtools-scraping` | `com.spartanlabs.webtools.scraping` | `Connector` — open/read a URL line by line, plus one-shot `get` / `skrape` / image `download` | skrapeit, jsoup, unirest |
| `io.github.spartanlaboratories:webtools-browser` | `com.spartanlabs.webtools.browser` | `WebViewer` — headless-browser screenshots | selenium-java |

Every fallible operation returns `kotlin.Result` rather than throwing, so callers decide how
to recover.

## Install

```kotlin
dependencies {
    implementation("io.github.spartanlaboratories:webtools-udp:2.0.0-alpha1")
    // and/or
    implementation("io.github.spartanlaboratories:webtools-scraping:1.0.0")
    implementation("io.github.spartanlaboratories:webtools-browser:1.0.0")
}
```

Requires JDK 11 or newer. Built with Kotlin 2.2.

`webtools-udp 2.0.0` (currently a pre-release `2.0.0-alphaN` series on the `2.x` integration
branch) is a **wire break** for everything after the handshake — see [Migrating to
2.0](#migrating-to-20). Both ends of a connection must be on `2.0.0`+; a `1.x` ↔ `2.x` pairing
fails the handshake cleanly instead of exchanging data either side can parse.

### Migrating from `io.github.spartanlaboratories:WebTools` (≤ 2.0.1a)

The combined `WebTools` artifact is retired at its final version `2.0.1a` and receives no
further releases. To move off it:

1. Replace the `WebTools` dependency with whichever of the three modules you actually use.
2. Update imports: `com.spartanlabs.webtools.Foo` → `com.spartanlabs.webtools.{udp,scraping,browser}.Foo`.
3. `WebTools` carried an unused `api` dependency on `GeneralTools`; if you relied on it
   transitively, depend on it directly now.
4. `webtools-browser` no longer ships webdriver binaries — [Selenium
   Manager](https://www.selenium.dev/documentation/selenium_manager/) resolves the driver at
   runtime (needs a local Chrome/Chromium install and, on first run, network access).

## Components

### `webtools-udp`

| Type | Purpose |
|------|---------|
| `MultiConnectionUDPServer` | Accepts handshakes from many clients on one common port and hands each its own `Connection`. Abstract — subclass and implement `onClientConnect`; optionally override `admit` to screen/refuse handshakes and validate an opaque credential, override `onClientDisconnect`, and set `idleTimeoutMillis` for idle-connection detection. |
| `MultiConnectionUDPClient` | The client-side counterpart: one socket, one owned listener thread, one dispatch thread — performs the handshake, then delivers the rest of the session via callback. `send` and `startBytes` also accept/deliver raw `ByteArray` datagrams. `startKeepAlive` / `stopKeepAlive` run an opt-in, library-timed keepalive; `startProbe` / `stopProbe` / `linkQuality` run an opt-in RTT / packet-loss probe. |
| `Connection` | Interface for one named connection to a peer (`actuate` / `actuateBytes` / `push(String)` / `push(ByteArray)` / `terminate` / `keepAlive` / `startKeepAlive` / `stopKeepAlive` / `startProbe` / `stopProbe` / `linkQuality`). |
| `UDPConnection` | The production `Connection`: a socket-free handle to one multiplexed client of a `MultiConnectionUDPServer`; owns no socket. |
| `LinkQuality` | An immutable snapshot from the opt-in probe: smoothed `rttMillis`, `rttVarianceMillis` (jitter proxy), windowed `packetLossRatio`, and `probesSent` / `probesDelivered`. |
| `UDPSendReceiveServer` | A bound send/receive UDP socket pair with an async receive loop — receives datagrams up to 65507 bytes (configurable via `receiveBufferBytes`), truncating larger ones. |
| `HandshakeWireFormat` | The handshake-bootstrap wire format: `Iam <name> [<credential>]`, the accepted `REGISTERED <version>` reply, `REFUSED <reason>`. Everything after the handshake lives in `TransportWireFormat`. |
| `DatagramType` | The 1-byte tag carried as byte 0 of every post-`REGISTERED` datagram (`KEEPALIVE` `0x80`, `PROBE_PING` `0x81`, `PROBE_PONG` `0x82`, `UNRELIABLE` `0x90`); the listener switches on it before touching a payload. |
| `TransportWireFormat` | Builds/parses the framed post-handshake datagrams: `keepaliveDatagram`, `probePingDatagram` / `probePongDatagram` / `probeSequenceOf`, `unreliableDatagram` / `unreliablePayloadOf` / `unreliableChannelOf`. |
| `Admission` / `HandshakeRefusedException` | `admit`'s return type (`Admitted` / `Refused(reason)`); the typed failure a refused client's `handshake` surfaces. |
| `IncompatibleProtocolException` | Typed `handshake()` failure when the server's `REGISTERED` reply announces a different wire-protocol major than this build speaks (e.g. a `1.x` server). |
| `resolveLocalAddress()` | Best-effort lookup of this machine's outward-facing local address. |

### `webtools-scraping`

| Type | Purpose |
|------|---------|
| `Connector` | Single-connection web client: open a URL and read it line by line; plus one-shot `get`, `skrape`, and image `download` helpers. |

### `webtools-browser`

| Type | Purpose |
|------|---------|
| `WebViewer` | Headless-Chrome screenshot utility — `screenshot(url)` returns a `Result<BufferedImage>`, `getPage(url)` a `Result<File>`. Driver provisioned by Selenium Manager. |

## UDP transport protocol

`MultiConnectionUDPServer` multiplexes **all** client traffic over one socket
(`COMMON_LISTEN_PORT`, `9998`). The handshake bootstrap stays plain UTF-8 text; every datagram
after it is **framed** — byte 0 is a `DatagramType` tag the listener switches on before it ever
looks at a payload:

| Direction | Message | Sent to |
|-----------|---------|---------|
| client → server | `Iam <name>` | `COMMON_LISTEN_PORT` (`9998`) |
| client → server | `Iam <name> <credential>` (optional opaque, whitespace-free token) | `COMMON_LISTEN_PORT` (`9998`) |
| server → client | `REGISTERED <version>` (e.g. `REGISTERED 2`) | the **source address and port** of the client's `Iam` datagram |
| server → client | `REFUSED <reason>` (handshake refused; bare `REFUSED` if no reason) | the **source address and port** of the client's `Iam` datagram |
| client ↔ server | `0x90 <channel> <payload…>` — application data (`UNRELIABLE`) | port `9998`, from/to the same socket the client sent `Iam` from |
| client → server | `0x80` — keepalive (`KEEPALIVE`, ~20 s idle cadence) | port `9998`, from the same socket |
| client ↔ server | `0x81 <seq>` / `0x82 <seq>` — link-quality probe (`PROBE_PING` / `PROBE_PONG`; transport-consumed) | port `9998`, same socket |

Byte 0 of a post-handshake datagram is always `>= 0x80` and the listener never falls back to a
text match, so **any** payload bytes ride an `0x90` frame intact — see [Binary application
payloads](#binary-application-payloads). The server's accepted reply carries the wire-protocol
major it speaks (`REGISTERED 2` for `2.x`); a client on a different major fails `handshake()`
with a typed `IncompatibleProtocolException` rather than exchanging framed data the other side
can't parse — see [Migrating to 2.0](#migrating-to-20).

Every server → client datagram is addressed to the client's observed post-NAT source, never
to anything in a payload, so **the data path traverses NAT** and the server binds no
per-client ports (it is hostable behind a single-port container or L4 UDP load balancer). A
retransmitted `Iam` from the same source is answered with another `REGISTERED <version>` and
does not re-register. `<credential>` (`tokens[2]`, optional) is passed to `admit` verbatim; any token
after it is ignored.

Before a new handshake is accepted the server calls `admit(name, peer, credential)` →
`Admission`; returning `Admission.Refused(reason)` replies `REFUSED <reason>` to the client
(surfaced client-side as a typed `HandshakeRefusedException`), registers nothing, and fires
no `onClientConnect`. This replaces the old "call `Connection.terminate()` after the fact"
workaround for a refuse-at-connect decision. `admit` is only consulted for a first `Iam`
from an unknown origin, and runs *before* any same-name supersede, so a refused newcomer
cannot evict a legitimate incumbent.

A fresh, admitted `Iam` under a name that is already registered under a different
origin (e.g. after a NAT rebind) supersedes that stale registration; the old
origin is no longer addressed by `pushToAll`. `Connection.terminate()` fully
deregisters the connection, not just its message handler - e.g. an
application-level refusal discovered *after* the handshake already completed
should call it so the refused client is not addressed by future broadcasts.

The client must send an `0x80` keepalive datagram on that socket every ~20 s of idle time to
hold its NAT mapping open. The client may either call `MultiConnectionUDPClient.sendKeepAlive()`
on its own timer, or call `MultiConnectionUDPClient.startKeepAlive()` once and let the library
time it (idle-aware — application traffic defers the next keepalive). `Connection.keepAlive()`
is the server-side one-shot equivalent and `Connection.startKeepAlive()` its library-timed form.
The server drops an inbound `0x80` without dispatching it, though when idle detection is enabled
an inbound `0x80` — like any inbound datagram — refreshes that client's server-side liveness
timestamp. A server-side keepalive is server → client and refreshes cone NATs only — the
authoritative keepalive is the client's. See [Scheduled keepalive](#scheduled-keepalive).

A server behind symmetric NAT still needs a rendezvous/relay (out of scope). Background:
[issue #1](https://github.com/SpartanLaboratories/WebTools/issues/1),
[`docs/issue-1-nat-traversal-plan.md`](docs/issue-1-nat-traversal-plan.md),
[`docs/issue-1-tier-2-plan.md`](docs/issue-1-tier-2-plan.md).

### Binary application payloads

Post-handshake application data is not limited to text. `Connection.push(ByteArray)`
(server → client), `MultiConnectionUDPClient.send(ByteArray)` (client → server), and
`MultiConnectionUDPServer.pushToAll(ByteArray)` frame the payload as an `0x90` unreliable
datagram and put it on the wire **verbatim** — no encoding, no trim, no reserved first byte.
`Connection.actuateBytes` / `MultiConnectionUDPClient.startBytes` /
`MultiConnectionUDPServer.startBytes` register an inbound handler that receives an
**exact-length, undecoded, untrimmed** copy of the payload (the frame's `0x90` tag and channel
byte already stripped). The text and bytes handlers are mutually exclusive per connection —
the last `actuate` / `actuateBytes` (or `start` / `startBytes`) call wins.

Because control traffic (`Iam`, keepalive, probe) and application data are separated by the
`DatagramType` tag rather than by matching text, **any** payload bytes survive intact — a
binary blob that happens to decode-and-trim to `KA` or that starts with `0x81` is delivered to
`start` / `actuate` exactly as sent, with no lead-byte workaround needed.

Every receive path in this module — `MultiConnectionUDPServer`, `MultiConnectionUDPClient`,
and `UDPSendReceiveServer` — accepts datagrams up to **65507 bytes** (the maximum UDP
payload over IPv4) whole; a larger payload is truncated, not rejected. Each constructor
takes an optional `receiveBufferBytes` (512..65507, default 65507) to shrink the
per-instance receive buffer when datagrams are known to be small; a datagram larger than
the configured size is truncated and a WARN is logged. For real-network use keep frames
under the path MTU (**~1200 bytes**) to avoid IP fragmentation and the loss that comes with
it — application payloads carry 2 bytes of framing overhead (`0x90` + channel).

### Connection liveness

By default the server tracks no per-connection idle time and never signals that a client has
gone silent (a crashed / powered-off / out-of-range client stays addressable until an
explicit `Connection.terminate()` or a same-name supersede). Pass a positive
`idleTimeoutMillis` to opt in: a dedicated `mcups-liveness` daemon thread then reports any
connection with no inbound datagram (application data **or** a keepalive) within the threshold via
`onClientDisconnect(connection, reason)`:

```kotlin
val server = object : MultiConnectionUDPServer(idleTimeoutMillis = 60_000) {
    override fun onClientConnect(connection: Connection) { /* ... */ }
    override fun onClientDisconnect(connection: Connection, reason: DisconnectReason) {
        when (reason) {
            DisconnectReason.TIMEOUT    -> startReconnectGraceWindow(connection) // do NOT terminate
            DisconnectReason.SUPERSEDED -> rebindSession(connection)
            DisconnectReason.TERMINATED -> releaseSession(connection)
        }
    }
}
```

`onClientDisconnect` runs on the dispatch thread (`mcups-dispatch`), not the caller's. It is
**notify-only**: `TIMEOUT` does not remove the registration or call `terminate()` — the
connection stays addressable by `pushToAll` so the application can run its own grace window
and call `terminate()` when ready (which then produces a second `TERMINATED` call). A
same-name supersede fires `SUPERSEDED`. `stop()` teardown fires nothing. With the default
`idleTimeoutMillis = 0` there is no extra thread and no per-datagram cost.

### Handshake screening & credentials

By default the server accepts every well-formed `Iam`. Override
`admit(name, peer, credential): Admission` to screen a handshake before it is registered —
validate the credential, enforce a capacity gate, reject a banned name:

```kotlin
val server = object : MultiConnectionUDPServer() {
    override fun admit(name: String, peer: InetSocketAddress, credential: String): Admission =
        when {
            isBanned(name)              -> Admission.Refused("banned")
            !tokenValid(name, credential) -> Admission.Refused("invalid credential")
            connections.size >= capacity  -> Admission.Refused("server full")
            else                          -> Admission.Admitted
        }
    override fun onClientConnect(connection: Connection) { /* ... */ }
}
```

```kotlin
val client = MultiConnectionUDPClient(serverAddress)
client.handshake("alice", credential = base64UrlToken).fold(
    onSuccess = { client.start { msg -> /* ... */ } },
    onFailure = { ex -> if (ex is HandshakeRefusedException) showError(ex.reason) },
)
```

The credential is **one whitespace-free token**, opaque to the library (base64url-encode a
structured or binary value), passed to `admit` verbatim; an absent credential arrives as the
empty string. It rides **in cleartext** — the library gives you a channel, not
confidentiality. Back-compat is total in every direction: an old client (`Iam <name>`)
against a new server registers with an empty credential and the default `admit`; a new
client (`Iam <name> <cred>`) against an old server still registers (the old server ignores
the trailing token — so auth is *not* enforced until both ends are on `1.4.0`+). `admit`
runs inline on the listener thread, so keep it fast and local.

### Scheduled keepalive

Both sides have an **opt-in, idle-aware** library-managed keepalive so a consumer never
hand-rolls the `~20 s` idle timer around the one-shot primitive:

```kotlin
// client
client.startKeepAlive()          // default 20 s; or startKeepAlive(intervalMillis)
client.stopKeepAlive()           // also done by client.stop()

// server, per connection
override fun onClientConnect(connection: Connection) {
    connection.startKeepAlive()   // server → client keepalive; also stopped by terminate() / stop()
}
```

It is idle-aware: an `0x80` keepalive goes out only after `intervalMillis` of **output** silence
on that path, so application traffic defers it (a scheduled keepalive may lag up to one
quarter-interval, max 5 s, past the interval). Each side lazily creates **one** daemon
`ScheduledExecutorService` (`mcupc-keepalive` / `mcups-keepalive`) on the first `startKeepAlive`
call and shuts it in `stop()` — a consumer that never opts in pays for no thread. The one-shot
`sendKeepAlive()` / `keepAlive()` primitives are unchanged and still own no timer; both emit the
same `0x80` datagram whether sent one-shot or scheduled. Server → client keepalives refresh
only endpoint-independent (cone) NAT mappings; the authoritative keepalive is still the
client's own.

### Link quality (RTT & packet loss)

An **opt-in** transport-level probe on either side measures round-trip time and packet loss
without the consumer hand-rolling an application ping. Off by default:

```kotlin
// client
client.startProbe()              // default 1 s; or startProbe(intervalMillis)
client.stopProbe()               // also done by client.stop()
val q = client.linkQuality()     // null until the first probe reply comes back
// q.rttMillis, q.rttVarianceMillis (jitter proxy), q.packetLossRatio, q.probesSent, q.probesDelivered

// server, per connection
override fun onClientConnect(connection: Connection) {
    connection.startProbe()      // server → client probe; also stopped by terminate() / stop()
}
```

Each armed side sends one tiny `0x81` probe (an 8-byte big-endian sequence) per interval and
the peer echoes it back as `0x82`; the RTT is computed against the prober's own monotonic clock
(EWMA-smoothed, RFC 6298 SRTT/RTTVAR). The **responder always answers an inbound `0x81`** (a
client unconditionally, a server for a registered origin), so the peer can measure even if this
side never opted in. `0x81` / `0x82` are consumed by the transport and never reach `start` /
`actuate`. Each side lazily creates **one** daemon `ScheduledExecutorService`
(`mcupc-probe` / `mcups-probe`) on the first `startProbe` and shuts it in `stop()`.
`packetLossRatio` is **lagging and windowed** (it needs a few seconds to first resolve and
reacts over tens of seconds) — a link-health gauge, not a fast congestion trigger. The probe
requires both ends on `1.6.0`+ (and, on the framed `2.x` transport, both ends on `2.0.0`+);
against an incompatible peer no `0x82` comes back and `packetLossRatio` climbs toward `1.0`.

### Client-side usage

`MultiConnectionUDPClient` performs the handshake and then owns a background
listener thread and dispatch thread for the rest of the session, so a consumer
never hand-rolls the receive loop or the "one socket for everything" invariant:

```kotlin
val client = MultiConnectionUDPClient(serverAddress)
client.handshake("alice").getOrThrow()
client.start { message -> /* handle inbound data; runs on client's dispatch thread */ }
client.send("hello")
client.startKeepAlive()   // library times it; no caller timer needed
// client.sendKeepAlive() is still available for callers who want to drive the cadence themselves

// or, for a binary protocol:
client.startBytes { bytes -> /* exact datagram body, no decode, no trim */ }
client.send(byteArrayOf(0x01, 0x02, 0x03))   // any bytes, no reserved first byte needed
// ...
client.stop()
```

Inbound keepalive datagrams (a server may send one via `Connection.keepAlive()`) are dropped
automatically and never reach the `start` callback.

### Migrating to 2.0

`webtools-udp` `2.0.0` breaks the post-handshake wire format: every datagram after the
handshake now leads with a 1-byte `DatagramType` tag (§ [UDP transport
protocol](#udp-transport-protocol)). **Both ends of a connection must be on `2.0.0`+** — a
`1.x` peer's unframed keepalive/probe/application datagrams are dropped as unrecognized, and a
`2.x` client against a `1.x` server fails `handshake()` with a typed
`IncompatibleProtocolException` rather than exchanging data neither side can parse correctly.

- **Removed from `HandshakeWireFormat`:** `KEEPALIVE_TOKEN`, `DEFAULT_KEEPALIVE_INTERVAL_MILLIS`,
  `isKeepAlive`, `PROBE_REQUEST_VERB`, `PROBE_REPLY_VERB`, `DEFAULT_PROBE_INTERVAL_MILLIS`,
  `probeRequestMessage`, `probeReplyMessage`, `isProbeRequest`, `isProbeReply`, `probeToken`.
  Their role moves to `TransportWireFormat` in binary form.
- **`HandshakeWireFormat.isRegistered`** now requires the exact `REGISTERED 2` reply, not a
  bare `REGISTERED`; `registeredMessage()` / `registeredProtocolVersion(reply)` are new.
- **The Issue #8 lead-byte workaround is retired.** Because control and application traffic
  are separated by the `DatagramType` tag, there is no longer a reserved first byte to avoid —
  any binary payload, including one that used to collide with `KA`/`PING`/`PONG`, rides an
  `0x90` frame intact. See [Binary application payloads](#binary-application-payloads).

## Build & test

The repo is a Gradle multi-module build; the three modules are `:webtools-udp`,
`:webtools-scraping`, `:webtools-browser`.

```sh
./gradlew build                    # compile + full test suite, every module
./gradlew :webtools-udp:build      # one module only
./gradlew test                     # every test level, one JVM per module
./gradlew gatingTest               # Level 1  - fast pre-commit checks (all modules)
./gradlew componentTest            # Level 2  - isolated component behaviour
./gradlew integrationTest          # Level 3  - real sockets / external interfaces
./gradlew deterministicTest        # Level 4a - pure input->output mappings
./gradlew e2eTest                  # Level 4b - full-stack flows
./gradlew nonfunctionalTest        # Level 4c - robustness / security properties
./gradlew uatTest                  # Level 5  - manual acceptance (mostly @Disabled)
```

Each module organises its tests by the project's 5-level testing hierarchy under
`<module>/src/test/kotlin/com/spartanlabs/testing/<level>/webtools/<module>/` and JUnit-tags
them, so any level can be run or gated on its own. Shared fixtures live under
`testing/support/`.
