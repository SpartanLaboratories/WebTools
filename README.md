# WebTools

A small Kotlin/JVM library of internet I/O helpers: web page reading and scraping,
headless-browser screenshots, and a lightweight UDP connection layer.

Every fallible operation returns `kotlin.Result` rather than throwing, so callers decide how
to recover.

## Install

Published to Maven Central as `io.github.spartanlaboratories:WebTools`.

```kotlin
dependencies {
    implementation("io.github.spartanlaboratories:WebTools:2.0.0c")
}
```

Requires JDK 11 or newer. Built with Kotlin 2.2.

## Components

| Type | Purpose |
|------|---------|
| `Connector` | Single-connection web client: open a URL and read it line by line; plus one-shot `get`, `skrape`, and image `download` helpers. |
| `WebViewer` | Headless-Chrome screenshot utility - `screenshot(url)` returns a `BufferedImage`. |
| `resolveLocalAddress()` | Best-effort lookup of this machine's outward-facing local address. |
| `UDPSendReceiveServer` | A bound send/receive UDP socket pair with an async receive loop. |
| `Connection` | Interface for one named connection to a peer (`actuate` / `push` / `terminate` / `keepAlive`). |
| `UDPConnection` | The production `Connection`: a socket-free handle to one multiplexed client of a `MultiConnectionUDPServer`; owns no socket. |
| `MultiConnectionUDPServer` | Accepts handshakes from many clients on one common port and hands each its own `Connection`. Abstract - subclass and implement `onClientConnect`. |

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

The client must send the token `KA` on that socket every ~20 s of idle time to hold its NAT
mapping open; `Connection.keepAlive()` is the shared helper that sends one `KA` datagram
(the server drops inbound `KA` without dispatching it). A server-side `Connection.keepAlive()`
is server → client and refreshes cone NATs only — the authoritative keepalive is the
client's.

A server behind symmetric NAT still needs a rendezvous/relay (out of scope). Background:
[issue #1](https://github.com/SpartanLaboratories/WebTools/issues/1),
[`docs/issue-1-nat-traversal-plan.md`](docs/issue-1-nat-traversal-plan.md),
[`docs/issue-1-tier-2-plan.md`](docs/issue-1-tier-2-plan.md).

## Build & test

```sh
./gradlew build              # compile + full test suite
./gradlew test               # every test level, one JVM
./gradlew gatingTest         # Level 1  - fast pre-commit checks
./gradlew componentTest      # Level 2  - isolated component behaviour
./gradlew integrationTest    # Level 3  - real sockets / external interfaces
./gradlew deterministicTest  # Level 4a - pure input->output mappings
./gradlew e2eTest            # Level 4b - full-stack flows
./gradlew nonfunctionalTest  # Level 4c - robustness / security properties
./gradlew uatTest            # Level 5  - manual acceptance (mostly @Disabled)
```

Tests are organised by the project's 5-level testing hierarchy under
`src/test/kotlin/com/spartanlabs/testing/<level>/` and JUnit-tagged, so any level can be run
or gated on its own. Shared fixtures live under `testing/support/`.
