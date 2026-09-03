# WebTools

A small Kotlin/JVM library of internet I/O helpers: web page reading and scraping,
headless-browser screenshots, and a lightweight UDP connection layer.

Every fallible operation returns `kotlin.Result` rather than throwing, so callers decide how
to recover.

## Install

Published to Maven Central as `io.github.spartanlaboratories:WebTools`.

```kotlin
dependencies {
    implementation("io.github.spartanlaboratories:WebTools:2.0.0")
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
| `UDPConnection` | One named, dedicated UDP connection to a peer, backed by a `UDPSendReceiveServer`. |
| `MultiConnectionUDPServer` | Accepts handshakes from many clients on one common port and hands each its own `UDPConnection`. Abstract - subclass and implement `onClientConnect`. |

## UDP handshake protocol

`MultiConnectionUDPServer` speaks a two-message text protocol:

| Direction | Message | Sent to |
|-----------|---------|---------|
| client → server | `Iam <name>` | `COMMON_LISTEN_PORT` (`9998`) |
| server → client | `TXRXON <sendPort> <receivePort>` | the **source address and port** of the client's `Iam` datagram |

The reply is addressed to the datagram's UDP source, not to anything in the payload, so the
handshake completes even when the client is behind NAT. A retransmitted `Iam` from the same
source is answered with the same ports rather than allocating a new pair. Tokens after
`<name>` are ignored.

**Known limitation:** the per-client dedicated data channel (the `UDPConnection` the server
hands back) still binds a fixed port pair and the server may transmit on it before the
client has opened a matching NAT binding, so only the *handshake* is NAT-traversable today.
Full data-path traversal is tracked in
[issue #1](https://github.com/SpartanLaboratories/WebTools/issues/1); see
[`docs/issue-1-nat-traversal-plan.md`](docs/issue-1-nat-traversal-plan.md).

## Build & test

```sh
./gradlew build          # compile + full test suite
./gradlew test           # every test level
./gradlew componentTest  # Level 2 only - isolated component behaviour
./gradlew integrationTest # Level 3 only - real sockets / external interfaces
```

Tests are organised by the project's 5-level testing hierarchy under
`src/test/kotlin/com/spartanlabs/testing/<level>/` and tagged (`component`, `integration`)
so a level can be run or gated on its own.
