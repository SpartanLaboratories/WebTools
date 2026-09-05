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
    implementation("io.github.spartanlaboratories:webtools-udp:1.0.0")
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
| `MultiConnectionUDPServer` | Accepts handshakes from many clients on one common port and hands each its own `Connection`. Abstract — subclass and implement `onClientConnect`. |
| `MultiConnectionUDPClient` | The client-side counterpart: one socket, one owned listener thread, one dispatch thread — performs the handshake, then delivers the rest of the session via callback. |
| `Connection` | Interface for one named connection to a peer (`actuate` / `push` / `terminate` / `keepAlive`). |
| `UDPConnection` | The production `Connection`: a socket-free handle to one multiplexed client of a `MultiConnectionUDPServer`; owns no socket. |
| `UDPSendReceiveServer` | A bound send/receive UDP socket pair with an async receive loop. |
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
(the server drops inbound `KA` without dispatching it). A server-side `Connection.keepAlive()`
is server → client and refreshes cone NATs only — the authoritative keepalive is the
client's.

A server behind symmetric NAT still needs a rendezvous/relay (out of scope). Background:
[issue #1](https://github.com/SpartanLaboratories/WebTools/issues/1),
[`docs/issue-1-nat-traversal-plan.md`](docs/issue-1-nat-traversal-plan.md),
[`docs/issue-1-tier-2-plan.md`](docs/issue-1-tier-2-plan.md).

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
