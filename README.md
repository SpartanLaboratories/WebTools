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
    implementation("io.github.spartanlaboratories:webtools-udp:1.3.0")
    // and/or
    implementation("io.github.spartanlaboratories:webtools-scraping:1.0.0")
    implementation("io.github.spartanlaboratories:webtools-browser:1.0.0")
}
```

Requires JDK 11 or newer. Built with Kotlin 2.2.

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
| `MultiConnectionUDPServer` | Accepts handshakes from many clients on one common port and hands each its own `Connection`. Abstract — subclass and implement `onClientConnect`; optionally override `onClientDisconnect` and set `idleTimeoutMillis` for idle-connection detection. |
| `MultiConnectionUDPClient` | The client-side counterpart: one socket, one owned listener thread, one dispatch thread — performs the handshake, then delivers the rest of the session via callback. `send` and `startBytes` also accept/deliver raw `ByteArray` datagrams. |
| `Connection` | Interface for one named connection to a peer (`actuate` / `actuateBytes` / `push(String)` / `push(ByteArray)` / `terminate` / `keepAlive`). |
| `UDPConnection` | The production `Connection`: a socket-free handle to one multiplexed client of a `MultiConnectionUDPServer`; owns no socket. |
| `UDPSendReceiveServer` | A bound send/receive UDP socket pair with an async receive loop — receives datagrams up to 65507 bytes (configurable via `receiveBufferBytes`), truncating larger ones. |
| `HandshakeWireFormat` | The published verbs/tokens of the handshake protocol (`Iam`, `REGISTERED`, `KA`). |
| `resolveLocalAddress()` | Best-effort lookup of this machine's outward-facing local address. |

### `webtools-scraping`

| Type | Purpose |
|------|---------|
| `Connector` | Single-connection web client: open a URL and read it line by line; plus one-shot `get`, `skrape`, and image `download` helpers. |

### `webtools-browser`

| Type | Purpose |
|------|---------|
| `WebViewer` | Headless-Chrome screenshot utility — `screenshot(url)` returns a `Result<BufferedImage>`, `getPage(url)` a `Result<File>`. Driver provisioned by Selenium Manager. |

## UDP handshake protocol

`MultiConnectionUDPServer` multiplexes **all** client traffic over one socket
(`COMMON_LISTEN_PORT`, `9998`):

| Direction | Message | Sent to |
|-----------|---------|---------|
| client → server | `Iam <name>` | `COMMON_LISTEN_PORT` (`9998`) |
| server → client | `REGISTERED` (single token, no arguments) | the **source address and port** of the client's `Iam` datagram |
| client ↔ server | application data | port `9998`, from/to the same socket the client sent `Iam` from |
| client → server | `KA` (keepalive, ~20 s idle cadence) | port `9998`, from the same socket |

Every server → client datagram is addressed to the client's observed post-NAT source, never
to anything in a payload, so **the data path traverses NAT** and the server binds no
per-client ports (it is hostable behind a single-port container or L4 UDP load balancer). A
retransmitted `Iam` from the same source is answered with another `REGISTERED` and does not
re-register. Tokens after `<name>` are ignored.

A fresh `Iam` under a name that is already registered under a different
origin (e.g. after a NAT rebind) supersedes that stale registration; the old
origin is no longer addressed by `pushToAll`. `Connection.terminate()` fully
deregisters the connection, not just its message handler - e.g. an
application-level refusal discovered after the handshake already completed
should call it so the refused client is not addressed by future broadcasts.

The client must send the token `KA` on that socket every ~20 s of idle time to hold its NAT
mapping open; `Connection.keepAlive()` is the shared helper that sends one `KA` datagram
(the server drops inbound `KA` without dispatching it, though when idle detection is enabled
an inbound `KA` — like any inbound datagram — refreshes that client's server-side liveness
timestamp). A server-side `Connection.keepAlive()`
is server → client and refreshes cone NATs only — the authoritative keepalive is the
client's.

A server behind symmetric NAT still needs a rendezvous/relay (out of scope). Background:
[issue #1](https://github.com/SpartanLaboratories/WebTools/issues/1),
[`docs/issue-1-nat-traversal-plan.md`](docs/issue-1-nat-traversal-plan.md),
[`docs/issue-1-tier-2-plan.md`](docs/issue-1-tier-2-plan.md).

### Binary application payloads

Post-handshake application data is not limited to text. `Connection.push(ByteArray)`
(server → client), `MultiConnectionUDPClient.send(ByteArray)` (client → server), and
`MultiConnectionUDPServer.pushToAll(ByteArray)` put the payload on the wire **verbatim** —
no encoding, no trim. `Connection.actuateBytes` / `MultiConnectionUDPClient.startBytes` /
`MultiConnectionUDPServer.startBytes` register an inbound handler that receives an
**exact-length, undecoded, untrimmed** copy of each datagram. The text and bytes handlers
are mutually exclusive per connection — the last `actuate` / `actuateBytes` (or `start` /
`startBytes`) call wins.

Caveat: the `Iam` / `KA` classifier runs on the trimmed UTF-8 view of **every** inbound
datagram, in both directions, and cannot be turned off (a retransmitted `Iam` or a `KA`
legitimately arrives mid-session). A binary payload whose UTF-8 decode, after trimming
ASCII whitespace, is exactly `KA` or begins with `Iam ` is intercepted by the
control-traffic machinery and never delivered. Mitigation: lead every binary application
datagram with a byte that cannot start `Iam` / `KA` and is not ASCII whitespace — e.g.
`0x00`, or any byte `>= 0x80` used as a version/format tag.

Every receive path in this module — `MultiConnectionUDPServer`, `MultiConnectionUDPClient`,
and `UDPSendReceiveServer` — accepts datagrams up to **65507 bytes** (the maximum UDP
payload over IPv4) whole; a larger payload is truncated, not rejected. Each constructor
takes an optional `receiveBufferBytes` (512..65507, default 65507) to shrink the
per-instance receive buffer when datagrams are known to be small; a datagram larger than
the configured size is truncated and a WARN is logged. For real-network use keep frames
under the path MTU (**~1200 bytes**) to avoid IP fragmentation and the loss that comes with
it.

### Connection liveness

By default the server tracks no per-connection idle time and never signals that a client has
gone silent (a crashed / powered-off / out-of-range client stays addressable until an
explicit `Connection.terminate()` or a same-name supersede). Pass a positive
`idleTimeoutMillis` to opt in: a dedicated `mcups-liveness` daemon thread then reports any
connection with no inbound datagram (application data **or** `KA`) within the threshold via
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

### Client-side usage

`MultiConnectionUDPClient` performs the handshake and then owns a background
listener thread and dispatch thread for the rest of the session, so a consumer
never hand-rolls the receive loop or the "one socket for everything" invariant:

```kotlin
val client = MultiConnectionUDPClient(serverAddress)
client.handshake("alice").getOrThrow()
client.start { message -> /* handle inbound data; runs on client's dispatch thread */ }
client.send("hello")
client.sendKeepAlive()   // call on a ~20s idle cadence

// or, for a binary protocol:
client.startBytes { bytes -> /* exact datagram body, no decode, no trim */ }
client.send(byteArrayOf(0x00, /* version tag */ 0x01, 0x02, 0x03))
// ...
client.stop()
```

Inbound `KA` datagrams (a server may send one via `Connection.keepAlive()`) are dropped
automatically and never reach the `start` callback.

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
