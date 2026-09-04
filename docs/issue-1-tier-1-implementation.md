# Issue #1 — Tier 1 full implementation plan

**Parent:** [`issue-1-nat-traversal-plan.md`](./issue-1-nat-traversal-plan.md)
**Scope of this document:** Tier 1 only — make the *handshake* and the *common broadcast
channel* address replies to the datagram's real source, so both work from behind NAT.
The per-client dedicated data channel (`UDPConnection` / `UDPSendReceiveServer`) is **not**
touched here; that is Tier 2.

**Status:** IMPLEMENTED on branch `fix/issue-1-handshake-datagram-source`. The
test-hierarchy prep (§3) landed first on `refactor/test-level-hierarchy`. The sections
below describe what was built; a few details shifted in the final code (noted inline).
**Release:** rides the in-progress `2.0.0` break (`build.gradle.kts` → `2.0.0a`). No further
version bump in these commits.

**As-built deltas from this plan:**
- `commonListenSocket` renamed to `commonSocket` (it now both sends and receives);
  removed `COMMON_SEND_PORT` (public) and `HANDSHAKE_ADDRESS_INDEX`.
- §1.5 retransmit de-dup: included.
- `pushToAddress` → `replyToOrigin`.

**Follow-up: testability refactor for full test-hierarchy compliance** (same branch,
separate commit). A `test-inspector` audit found the class was untestable below Level 3
because it binds a socket and starts a thread in its constructor. The handshake logic was
therefore extracted:
- `Connection` (new public interface) — `UDPConnection` implements it; lets the server's
  logic run against a socket-free fake. `onClientConnect` now takes `Connection`.
- `HandshakeProtocol` (new `internal object`) — pure: `parseHandshake`, `extraTokenCount`,
  `portPairFor`, `txrxonReply`. All the `HANDSHAKE_*` / `DEDICATED_PORT_BASE` constants moved
  here.
- `Registrations` + `Registration` (new `internal`) — the registration collection, no I/O.
- `HandshakeCoordinator` (new `internal class`) — the handshake state machine, with the
  socket, the `UDPConnection` factory, and `onClientConnect` injected as three collaborators.
- `MultiConnectionUDPServer` is now just: bind `commonSocket`, run the receive loop, wire the
  coordinator. It keeps `COMMON_LISTEN_PORT` and the buffer/timeout constants.

**Test matrix (as built):**

| Level | Package | Class | Covers |
|-------|---------|-------|--------|
| 1 gating | `testing.gating.webtools` | `HandshakeProtocolGatingTest` (4), `HandshakeCoordinatorGatingTest` (3) | fast socket-free smoke: parse / port-disjointness / reply-shape, and register / retransmit-no-reregister / `findByOrigin` |
| 2 component | `testing.component.webtools` | `HandshakeCoordinatorTest` (13), `RegistrationsTest` (5) | state machine (new-vs-dedup, port allocation, reply-failure path, order) and the fan-out methods (`actuateAll` / `broadcast` / `terminateAll` incl. partial-failure), plus the collection; socket-free via `FakeConnection` |
| 3 integration | `testing.integration.webtools` | `MultiConnectionUDPServerTest` (10) | end-to-end over real UDP: reply-to-source, bare `TXRXON`, payload-token-ignored, **retransmit dedup**, **multi-client disjoint ports**, `pushToAll`-to-origin, malformed / empty / unknown-verb / oversized datagrams, `stop` |
| 4a deterministic | `testing.deterministic.webtools` | `HandshakeProtocolTest` (15) | exhaustive input→output for every `HandshakeProtocol` function |
| 4c non-functional | `testing.nonfunctional.webtools` | `HandshakeNonFunctionalTest` (2) | retransmit-storm bounded allocation; reply target is never a payload-claimed address |
| 5 UAT | `testing.uat.webtools` | `MultiConnectionUDPServerUatTest` (2, `@Disabled`) | manual two-host cross-NAT procedure — see [`issue-1-tier-1-uat.md`](./issue-1-tier-1-uat.md) |

The `stop()` / `start()` / `pushToAll()` fan-out folds were moved onto `HandshakeCoordinator`
as `terminateAll()` / `actuateAll()` / `broadcast()` so the "terminate every connection even
if one fails" contract is unit-tested with failing fakes rather than only on the happy path.

Shared fixture: `testing.support.webtools.FakeConnection`. New Gradle tasks:
`gatingTest`, `deterministicTest`, `nonfunctionalTest`, `uatTest` (joining `componentTest` /
`integrationTest`); a `CommonUdpPortLock` build service serialises `test` and
`integrationTest` so parallel workers never collide on port 9998. `4b` has no task — no
end-to-end tests exist yet.

---

## 1. Design decisions

### 1.1 Learn the client endpoint from the datagram, never from the payload

`handshakeLoop()` currently discards `packet.address` / `packet.port` and
`handleHandshake()` trusts a self-reported address token. A NAT'd client can only report a
private address. Fix: capture `InetSocketAddress(packet.address, packet.port)` at receive
time and treat it as the sole source of truth for where to reach that client on the common
channel.

### 1.2 Reply on the socket that received the handshake

Today the `TXRXON` reply leaves a *separate* ephemeral socket (`commonSendSocket`) aimed at
`address:9999` — a fresh 5-tuple with no NAT mapping. Fix: send the reply from
`commonListenSocket` (the socket bound to `COMMON_LISTEN_PORT` that just did the `receive()`)
straight back to the datagram's source `InetSocketAddress`. That reuses the exact NAT
binding the client's `Iam` just punched.

Concurrency: `commonListenSocket.send()` will now be called from the listener thread (reply
path) and from arbitrary caller threads (`pushToAll`) while the listener thread is blocked
in `commonListenSocket.receive()`. The JDK `DatagramSocket` permits concurrent `send` /
`receive` and treats each `send` as one atomic datagram, so a single shared socket is
correct here. This gets a Boundary-Ring KDoc note.

### 1.3 Wire-protocol clean break (no shims — per `api-evolution-clean-break`)

| Direction | Before | After |
|-----------|--------|-------|
| client → server (port 9998) | `Iam <name> <address>` | `Iam <name>` |
| server → client | `<address> TXRXON <sendPort> <receivePort>` (sent to fixed port 9999) | `TXRXON <sendPort> <receivePort>` (sent to the `Iam` datagram's source addr:port) |
| `pushToAll` broadcast | sent to `<clientAddr>:9999` | sent to each client's learned handshake origin |

A trailing address token on an inbound `Iam` is now *ignored* (logged at debug), not
required — so a not-yet-migrated client that still sends `Iam name addr` still registers.
What genuinely breaks: any client that **listens on a fixed port 9999** for the reply /
broadcasts. It must instead read from the socket it sent the `Iam` from. That is the
intended semantic change and the reason this is a `2.0.0` item.

### 1.4 Confine the change to `MultiConnectionUDPServer`

`UDPConnection`'s constructor stays exactly as it is (`name, address, sendPort,
receivePort`). The extra piece of state Tier 1 needs — the handshake's *source port* — is
held by the server in a small private holder, not pushed onto `UDPConnection`:

```kotlin
/**
 * A registered client: its dedicated [connection] plus the exact [origin]
 * (address + source port) its `Iam` datagram arrived from. Every common-channel
 * datagram to this client is addressed to [origin] so it rides the NAT binding
 * the handshake opened.
 */
private class Registration(val connection: UDPConnection, val origin: InetSocketAddress)
```

`UDPConnection.address` is still populated (now from `origin.address` — the real routable
source IP, already an improvement for Tier 2's data path even though Tier 2 fixes the ports).
The bigger `UDPConnection` reshape belongs to Tier 2 / Option B1.

### 1.5 De-duplicate retransmitted handshakes (optional, recommended)

If an `Iam` arrives from an `InetSocketAddress` that already has a `Registration`, re-send
that registration's existing `TXRXON` instead of allocating a second dedicated port pair.
~5 lines, removes a port-exhaustion foot-gun on client retransmit, fits the "handshake
correctness" theme. Flagged optional so it can be dropped if you'd rather keep the diff
minimal.

---

## 2. File-by-file changes

### 2.1 `src/main/kotlin/com/spartanlabs/webtools/MultiConnectionUDPServer.kt`

**Fields**
- Delete `commonSendSocket`.
- Replace `connections: CopyOnWriteArrayList<UDPConnection>` with
  `registrations: CopyOnWriteArrayList<Registration>`.
- Add the private `Registration` class (§1.4).

**`handshakeLoop()`**
- After `commonListenSocket.receive(packet)`, build
  `val origin = InetSocketAddress(packet.address, packet.port)`.
- Change the `runCatching { … }` result from `List<String>` to `origin to text.split(' ')`.
- `.flatMap { (origin, tokens) -> handleHandshake(origin, tokens) }`.
- `SocketException` / socket-closed handling unchanged.

**`handleHandshake(origin: InetSocketAddress, message: List<String>): Result<Unit>`**
- New first parameter `origin`.
- `require(message.size >= HANDSHAKE_MIN_TOKENS) { "Expected '$HANDSHAKE_VERB <name>' but got ${message.size} token(s)" }`.
- Name = `message[HANDSHAKE_NAME_INDEX]`. If `message.size > HANDSHAKE_MIN_TOKENS`,
  `log.debug("Ignoring {} extra handshake token(s)", …)`.
- Drop the `InetAddress.getByName(...)` call entirely.
- (Optional §1.5) if `registrations.any { it.origin == origin }`, reply with the existing
  ports and return without allocating.
- `val connection = addConnection(name, origin)`.
- `replyToOrigin("$HANDSHAKE_REPLY_VERB ${connection.sendPort} ${connection.receivePort}", origin)`
  `.map { onClientConnect(connection) }`.
- Unrecognised-verb branch unchanged.

**`addConnection(name: String, origin: InetSocketAddress): UDPConnection`**
- Signature takes `origin` instead of `address: InetAddress`.
- `val portOffset = registrations.size * 2 + 2` (unchanged math; commit `ab53747` preserved).
- `UDPConnection(name, origin.address, DEDICATED_PORT_BASE - portOffset, DEDICATED_PORT_BASE - portOffset - 1)`
  then `registrations.add(Registration(it, origin))`.

**`pushToAddress` → `replyToOrigin(message: String, origin: InetSocketAddress): Result<Unit>`**
- `commonListenSocket.send(DatagramPacket(payload, payload.size, origin))`.
- Boundary-Ring KDoc: sent via the receive socket to the datagram source; safe to call
  concurrently with the listener loop.

**`start(...)`**
- `registrations.fold(Result.success(Unit)) { started, reg -> started.flatMap { reg.connection.actuate(onClientMessage) } }`.

**`pushToAll(message)`**
- `registrations.fold(Result.success(Unit)) { pushed, reg -> pushed.flatMap { replyToOrigin(message, reg.origin) } }`.
- KDoc: "addressed to each client's learned handshake origin".

**`stop()`**
- `registrations.fold(...) { it.connection.terminate() }`.
- `socketsClosed` block closes only `commonListenSocket` (drop `commonSendSocket.close()`).
- Return chain otherwise unchanged.

**Companion**
- Remove `COMMON_SEND_PORT` (public) and `HANDSHAKE_ADDRESS_INDEX` (private).
- Add `private const val DEDICATED_PORT_BASE = 9999` (keeps existing dedicated-port values).
- Add `private const val HANDSHAKE_MIN_TOKENS = 2`.
- Add `private const val HANDSHAKE_REPLY_VERB = "TXRXON"`.
- Keep `COMMON_LISTEN_PORT = 9998`, `HANDSHAKE_VERB`, `HANDSHAKE_NAME_INDEX`,
  `RECEIVE_BUFFER_BYTES`, `LISTENER_JOIN_TIMEOUT_MILLIS`.

**Class KDoc** — rewrite the protocol paragraph: `Iam <name>` in; `TXRXON <sendPort>
<receivePort>` replied to the datagram's source address/port; note the handshake is
NAT-safe and that the dedicated data channel's NAT behaviour is tracked in issue #1 Tier 2.
Add Level-1 inline comments on: why the reply uses the listen socket (NAT binding), why the
trailing address token is ignored.

### 2.2 `src/test/kotlin/com/spartanlabs/testing/integration/webtools/MultiConnectionUDPServerTest.kt`

Keep `@TestInstance(PER_CLASS)` + `@TestMethodOrder`. Single shared server on 9998/9999.
Rewrite so the "client" is a single `DatagramSocket()` on an ephemeral port that both sends
the `Iam` and receives the reply — the reply arriving on that same socket *is* the proof of
the fix.

- **`@Order(1)` `pushToAll succeeds when there are no connections`** — unchanged.
- **`@Order(2)` handshake registers + gets a bare `TXRXON`** — rewrite:
  - send `"Iam testclient"` (no address) to `COMMON_LISTEN_PORT` from `client`;
  - `client.receive(...)` (same socket) with `soTimeout = 5000`;
  - assert `reply.startsWith("TXRXON")`, `reply.split(' ').size == 3`, `!reply.contains("/")`;
  - assert `connectedClients` grew by 1, name `testclient`, `.address == localAddress`.
- **`@Order(3)` payload address token is ignored** — new:
  - send `"Iam liarclient 203.0.113.7"` (TEST-NET-3, bogus);
  - receive a reply at all;
  - assert the new connection's `.address` is the loopback/local source, **not** `203.0.113.7`.
- **`@Order(4)` `pushToAll` reaches a client at its learned origin** — new:
  - register `"Iam pushclient"`, consume its `TXRXON`;
  - `server.pushToAll("broadcast-1")` → `isSuccess`;
  - `client.receive(...)` gets `"broadcast-1"` on the same socket.
- **`@Order(5)` malformed `Iam` with no name is rejected** — new:
  - record `connectedClients.size`, send `"Iam"`, sleep;
  - assert size unchanged and (soTimeout) no reply; a good handshake immediately after still
    registers → listener survived.
- **`@Order(10)` `stop` terminates + closes the listen socket** — keep, but the post-stop
  probe sends `"Iam clientAfterStop"` from an ephemeral socket and expects
  `SocketTimeoutException` on its own `receive()`.

Remove every reference to `MultiConnectionUDPServer.COMMON_SEND_PORT`.

### 2.3 `README.md` (new — repo currently has none)

Required by the global "keep READMEs current" rule because the wire protocol changes.
Focused, not exhaustive:
- What WebTools is; Maven coordinates `io.github.spartanlaboratories:WebTools` + Gradle snippet.
- Requirements: JDK version, Kotlin 2.2.
- Components: `Connector`, `WebViewer`, `UDPSendReceiveServer`, `UDPConnection`,
  `MultiConnectionUDPServer`, `resolveLocalAddress` — one line each.
- **UDP handshake protocol** section:
  - client → `COMMON_LISTEN_PORT` (9998): `Iam <name>`
  - server → datagram source addr:port: `TXRXON <sendPort> <receivePort>`
  - the reply is addressed to the UDP source, so the handshake works from behind NAT;
  - **Known limitation:** the per-client dedicated data channel still binds fixed ports and
    the server speaks first — full data-path NAT traversal is issue #1 / Tier 2.
- Convention: every fallible operation returns `kotlin.Result`.
- Build & test: `./gradlew build`, `./gradlew test`.

### 2.4 `docs/issue-1-nat-traversal-plan.md`

Mark the Tier 1 section done / link to this doc and the commit.

### 2.5 Not touched

`UDPConnection.kt`, `UDPConnectionTest.kt`, `UDPSendReceiveServer.kt`,
`UDPSendReceiveServerTest.kt`, `Connector.kt`, `WebViewer.kt`. `build.gradle.kts` already
carries the level-scoped test tasks from the §3 prep commit; Tier 1 does not change it.

---

## 3. Test package hierarchy — DONE (prep commit)

Chosen: **Option B**. Landed in commit *"test: adopt the 5-level testing hierarchy for the
existing suite"* on branch `refactor/test-level-hierarchy`:

- `ConnectorTest` → `com.spartanlabs.testing.component.webtools` (`@Tag("component")`, Level 2)
- `UDPConnectionTest`, `UDPSendReceiveServerTest`, `MultiConnectionUDPServerTest`
  → `com.spartanlabs.testing.integration.webtools` (`@Tag("integration")`, Level 3)
- `build.gradle.kts` gains `componentTest` / `integrationTest` tasks that filter by tag;
  `test` still runs every level.

Tier 1's test edits (§2.2) apply on top of the moved file.

---

## 4. Verification

- `./gradlew test` — all tests green (Level 1–3 in one run today).
- `./gradlew build` — `com.vanniktech.maven.publish` config still resolves; version stays `2.0.0a`.
- The loopback tests mechanically prove the core of the fix: the reply returns to the
  datagram's **source port**, not a fixed 9999, and the payload address token no longer
  influences `connection.address`.
- True cross-NAT verification needs two hosts and can't be automated in CI here — call this
  out in the issue comment rather than claiming it's covered.

---

## 5. Version control

Per the confirmed rules: branch off `master` first, commit only on your say-so, no push / no
PR unless asked, README in the same commit as the protocol change, commit trailers
`Co-Authored-By: Claude Sonnet 5 …` + `Claude-Session: …`.

Proposed sequence:
1. *(if Option B chosen)* branch `test/level-3-integration-package`, commit
   `test: move socket tests into testing.integration package`.
2. branch `fix/issue-1-handshake-datagram-source`, commit
   `fix: reply to handshake datagram source, not client-claimed address`
   — `MultiConnectionUDPServer.kt`, `MultiConnectionUDPServerTest.kt`, `README.md`,
   `docs/*` update.
3. Stop. On your approval to share: prompt you to (a) comment on issue #1 that Tier 1
   landed and Tier 2 is still open, (b) file/track the matching client change in
   MyGameTools / MyGameServer (`Iam <name>` + parse bare `TXRXON` from the send socket).

---

## 6. Risks & edge cases

- **Breaking:** public `MultiConnectionUDPServer.COMMON_SEND_PORT` removed; wire protocol
  changed. Acceptable under `2.0.0`. Client repos must update in lockstep.
- **Symmetric NAT:** handshake still works — the reply reuses the same 5-tuple the client
  sent on. (The data path does not; Tier 2.)
- **Same NAT, multiple clients:** distinct source ports → distinct `Registration`s. Fine.
- **Handshake retransmit:** without §1.5 it still allocates a second port pair per
  duplicate `Iam` (pre-existing behaviour). §1.5 fixes it; if deferred, note it in the
  issue as remaining Tier 2 cleanup.
- **`pushToAll` timing:** a broadcast sent before a client has completed its handshake
  simply isn't delivered to that client — unchanged from today.
