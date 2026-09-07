# Issue #9 — configurable receive buffer size + documented receive ceiling

## Header / Association

- **Covers:** `SpartanLaboratories/WebTools#9` — *"Receive buffer is hardcoded to
  1024 bytes; larger datagrams are silently truncated"* (label: `enhancement`).
  The issue asks for (a) a **configurable** receive buffer size, (b) a
  **documented** maximum application payload and truncation behaviour, and
  (c) *optionally* a fragment/reassembly helper.
- **Scope of this plan:** (a) + (b). The fragment/reassembly helper is **out of
  scope** — deferred to a GitHub Project, see §9.
- **Module:** `webtools-udp` only (`io.github.spartanlaboratories:webtools-udp`,
  package `com.spartanlabs.webtools.udp`). No other module is touched. No wire /
  protocol change.
- **Branch:** `feat/issue-9-receive-buffer` (off `master`).
- **Commit:** TBD — the implementation (first stage) and **this plan document**
  land in the same commit so `git log --follow docs/issue-9-receive-buffer-plan.md`
  binds the two.
- **PR:** TBD.
- **Status:** implemented on `feat/issue-9-receive-buffer`, uncommitted. Production
  changes (§3.1–§3.6), README, and the full test suite (§5) are in the working tree;
  all 171 `:webtools-udp:test` cases pass and the §2.3 `javap` signature check is
  done (every pre-1.2.0 constructor signature preserved). Awaiting the maintainer's
  go-ahead to commit / open the PR. Open decisions were **resolved by the
  maintainer** — see §8.
- **Target version:** `webtools-udp` `1.1.0` → **`1.2.0`** (see §7).
- **Related:**
  - `docs/issue-8-binary-datagram-path-plan.md` — **§2.5 / §9 D2 already raised
    `RECEIVE_BUFFER_BYTES` to 65507** on `MultiConnectionUDPServer` and
    `MultiConnectionUDPClient`; that shipped in `1.1.0` (PR #17).
  - `docs/issue-3-public-client-handshake-plan.md` — `MultiConnectionUDPClient`
    shape / handshake.
  - `docs/split-into-three-modules-plan.md` — per-module versioning.
  - GitHub Project **"webtools-udp: large-payload framing"** —
    <https://github.com/orgs/SpartanLaboratories/projects/5> — holds the deferred
    fragment/reassembly design (6 draft issues).

---

## 1. Context

### 1.1 What the issue reported vs. what is true in the tree today

The issue was filed against a tree where `RECEIVE_BUFFER_BYTES = 1024` in **all
three** receive paths. **Issue #8 has since changed that.** Current source:

| Class | Constant | Value today | Source |
|---|---|---|---|
| `MultiConnectionUDPServer` | `RECEIVE_BUFFER_BYTES` (private const) | **65507** | `MultiConnectionUDPServer.kt:240` |
| `MultiConnectionUDPClient` | `RECEIVE_BUFFER_BYTES` (private const) | **65507** | `MultiConnectionUDPClient.kt:301` (used by `receiveLoop` `:210` and `handshake` `:127`) |
| `UDPSendReceiveServer` | `RECEIVE_BUFFER_BYTES` (private const) | **1024** — unchanged | `UDPSendReceiveServer.kt:165` (used by `receiveLoop` `:91`) |

65507 is the maximum UDP payload over IPv4 (65535 − 8 UDP header − 20 IP header),
so a well-formed datagram is **never** truncated on receive by the two `Multi*`
classes. Delivery is `packet.data.copyOf(packet.length)`
(`CommonChannel.kt:67`, `MultiConnectionUDPClient.kt:217`), so per-datagram cost
still tracks the real payload; the only fixed cost is one ~64 KiB backing array
per listener thread.

The README already documents the 65507 ceiling and the "keep frames under
~1200 bytes" MTU guidance (`README.md:122-124`), and the `Multi*` class KDoc
already states "an inbound datagram over 65507 bytes is delivered truncated, not
rejected" (`MultiConnectionUDPServer.kt:40-43`, `MultiConnectionUDPClient.kt:84-86`).

### 1.2 Root cause of the residual defect

`UDPSendReceiveServer.receiveLoop` (`UDPSendReceiveServer.kt:91`) still reads into
`ByteArray(1024)`. A datagram larger than 1024 bytes handed to this class is
truncated to 1024 by `DatagramPacket` with no error
(`UDPSendReceiveServer.kt:95-99`) — the exact bug the issue describes, still live
in this one class. `UDPSendReceiveServer` is a published type (`README.md:51`):
"A bound send/receive UDP socket pair with an async receive loop."

`UDPSendReceiveServer` is text-only — `receiveLoop` does
`String(packet.data, 0, packet.length, UTF_8).trim()`. It was left out of #8
because #8's binary path was scoped to `Connection` / the `Multi*` surface.

### 1.3 Requirements / acceptance criteria

1. No receive path in `webtools-udp` silently truncates a well-formed UDP
   datagram at its **default** configuration.
2. The effective maximum application payload (65507 bytes) and the
   truncate-above-it behaviour are documented on **every** public receive type,
   including `UDPSendReceiveServer`, and in the README.
3. All three public receive types (`MultiConnectionUDPServer`,
   `MultiConnectionUDPClient`, `UDPSendReceiveServer`) take an optional
   `receiveBufferBytes` constructor parameter, default **65507**, validated at
   construction, applied via `@JvmOverloads` so no existing JVM constructor
   signature is removed (no binary break, stays 1.2.0).
4. Sizing the buffer *below* a received datagram no longer truncates *silently*:
   a WARN is logged.
5. The fragment/reassembly helper is recorded as deferred (GitHub Project 5),
   not designed here.

---

## 2. Design

### 2.1 Shape of the change

- **`UDPSendReceiveServer`** — bring to parity with the `Multi*` classes: default
  receive buffer becomes 65507 (was 1024). Document the ceiling + truncation on
  its KDoc; generalise the README note to cover all three types.
- **All three classes** — add a trailing `receiveBufferBytes: Int` constructor
  parameter, defaulting to `DEFAULT_RECEIVE_BUFFER_BYTES = 65507`, validated to
  `MIN_RECEIVE_BUFFER_BYTES..MAX_UDP_PAYLOAD_BYTES` (see §2.4) at construction via
  a `require`, applied with `@JvmOverloads` (see §2.3). The per-class private
  `RECEIVE_BUFFER_BYTES` consts are removed; each `receiveLoop` (and the client's
  `handshake`) reads `ByteArray(receiveBufferBytes)`.
- **Truncation visibility** — in each receive path, when a received datagram
  exactly fills the buffer *and* the buffer is smaller than the 65507 max, log a
  WARN ("datagram from … filled the N-byte receive buffer and may have been
  truncated"). At the default 65507 this branch is unreachable, so default users
  never see a spurious warning; it fires only when a consumer has opted into a
  smaller buffer. This converts "silent" truncation into "logged" truncation —
  the literal fix for the issue's title.

The parameter is **opt-in for a smaller buffer only**. Its purpose is to let a
consumer shrink the per-instance ~64 KiB backing array when it knows its datagrams
are small; it can never raise the ceiling above the UDP maximum.

### 2.2 Decisions locked in by the maintainer

| # | Decision |
|---|---|
| **D1** | **Add** `receiveBufferBytes` to `MultiConnectionUDPServer`, `MultiConnectionUDPClient`, and `UDPSendReceiveServer`, with `@JvmOverloads` so existing JVM constructor signatures stay binary-compatible. |
| **D2** | The core-only fix **is** an accepted resolution of #9. Close #9 with a comment explaining the `Multi*` classes were already fixed by #8 and this change extends the guarantee + documentation + the new parameter to `UDPSendReceiveServer`. |
| **D3** | Default `receiveBufferBytes` = **65507** (IPv4 UDP max). Opt-in for a *smaller* buffer only. Validate: reject `< MIN_RECEIVE_BUFFER_BYTES` and `> 65507`. |
| **D4** | Fragment/reassembly is **not** a single follow-up issue — it is the GitHub Project *"webtools-udp: large-payload framing"* (<https://github.com/orgs/SpartanLaboratories/projects/5>), 6 draft issues. Reference it in §9; do not design it here. |
| **D5** | `@JvmOverloads`, stay at **1.2.0** (`1.1.0` → `1.2.0`). Verify generated constructor signatures with `javap` before tagging. |

### 2.3 Binary-compatibility analysis (`@JvmOverloads`)

WebTools is on Maven Central; an incompatible public signature change would mean a
**major** bump. `@JvmOverloads` keeps the addition compatible:

| Class | Constructor today (JVM) | Naive add (`param = default`, no `@JvmOverloads`) | With `@JvmOverloads` |
|---|---|---|---|
| `MultiConnectionUDPClient` | `<init>(InetAddress, int)` (+ Kotlin default-arg synthetic for `serverPort`) | primary becomes `<init>(InetAddress, int, int)` — old `<init>(InetAddress, int)` **gone** → `NoSuchMethodError` for precompiled callers | generates `<init>(InetAddress)`, `<init>(InetAddress, int)`, `<init>(InetAddress, int, int)` — **old signatures preserved** |
| `MultiConnectionUDPServer` | implicit `<init>()` (abstract, no primary-ctor parens; subclasses call `super()`) | primary becomes `<init>(int)` — compiled subclasses' `super()` targets a missing `<init>()` | `@JvmOverloads` on `protected constructor(receiveBufferBytes: Int = DEFAULT)` generates `<init>()` and `<init>(int)` — `super()` still resolves |
| `UDPSendReceiveServer` | `<init>(InetAddress, int, int)` (all three params required) | primary becomes `<init>(InetAddress, int, int, int)` — old 3-arg **gone** | generates 3-arg and 4-arg — **old signature preserved** |

**Verification step (before tag):** run `javap` on the built
`MultiConnectionUDPClient.class`, `MultiConnectionUDPServer.class`, and
`UDPSendReceiveServer.class` and confirm every pre-1.2.0 constructor signature is
still present.

Kotlin source compatibility holds for recompiled consumers regardless
(`MultiConnectionUDPClient(addr)`, `class Foo : MultiConnectionUDPServer()`,
`UDPSendReceiveServer(a, p, l)` all still compile because every new param has a
default).

### 2.4 Bounds — default 65507, floor 512

Two new public constants on the `MultiConnectionUDPServer` companion (alongside
`COMMON_LISTEN_PORT`):

```kotlin
/** Maximum UDP payload over IPv4 (65535 − 8 UDP − 20 IP): the largest a
 *  receiveBufferBytes may be, and the size at which no datagram is ever truncated. */
const val MAX_UDP_PAYLOAD_BYTES = 65507

/** Smallest permitted receiveBufferBytes. Comfortably holds every handshake /
 *  keepalive token and a small application payload with headroom; a buffer below
 *  this is almost certainly a misconfiguration. */
const val MIN_RECEIVE_BUFFER_BYTES = 512

/** Default receiveBufferBytes — equal to [MAX_UDP_PAYLOAD_BYTES], so by default
 *  no well-formed datagram is ever truncated on receive. */
const val DEFAULT_RECEIVE_BUFFER_BYTES = MAX_UDP_PAYLOAD_BYTES
```

Rationale for **default = 65507**: adopting a smaller default (the issue floated
`>=1200`) would *regress* #8's "library never truncates a well-formed datagram by
default" guarantee for datagrams in the 1201–65507 range. The parameter only lets
a consumer opt *into* a smaller buffer.

Rationale for **floor = 512**: the handshake reply (`REGISTERED`, 10 bytes) and
keepalive (`KA`) are tiny, so the functional floor is far lower, but a buffer
under 512 bytes has no plausible legitimate use and silently narrows what the
channel can carry — `require` rejects it at construction with a clear message.

`require(receiveBufferBytes in MIN_RECEIVE_BUFFER_BYTES..MAX_UDP_PAYLOAD_BYTES)`
throws `IllegalArgumentException` at construction.

### 2.5 Construction ordering

`MultiConnectionUDPClient` binds `DatagramSocket()` in a property initialiser;
`MultiConnectionUDPServer` binds `CommonChannel(COMMON_LISTEN_PORT)` (port 9998)
in a property initialiser; `UDPSendReceiveServer` binds two sockets in property
initialisers. The `require(...)` guard must run **before** those binds, so a bad
size fails fast without leaking a bound port (or, for the server, port 9998).
Place the guard in an `init { }` block declared as the **first** member of the
class, above the socket properties.

---

## 3. File-by-file changes

### 3.1 `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/MultiConnectionUDPServer.kt`

- **Class declaration (`:68`):** `abstract class MultiConnectionUDPServer {` →
  ```kotlin
  abstract class MultiConnectionUDPServer @JvmOverloads protected constructor(
      private val receiveBufferBytes: Int = DEFAULT_RECEIVE_BUFFER_BYTES,
  ) {
      init {
          require(receiveBufferBytes in MIN_RECEIVE_BUFFER_BYTES..MAX_UDP_PAYLOAD_BYTES) {
              "receiveBufferBytes must be $MIN_RECEIVE_BUFFER_BYTES..$MAX_UDP_PAYLOAD_BYTES, was $receiveBufferBytes"
          }
      }
  ```
  The `init` block must be the first class member, before
  `private val commonChannel = CommonChannel(COMMON_LISTEN_PORT)` (`:77`).
- **`receiveLoop` (`:114-128`):** `ByteArray(RECEIVE_BUFFER_BYTES)` →
  `ByteArray(receiveBufferBytes)`. Truncation-visibility WARN is emitted from
  `CommonChannel.receive` (§3.4), which already has `packet` and `buffer` in
  scope — keep it there rather than reaching into `Inbound`.
- **Companion (`:223-244`):** delete `private const val RECEIVE_BUFFER_BYTES`;
  add the three public constants from §2.4 (`MAX_UDP_PAYLOAD_BYTES`,
  `MIN_RECEIVE_BUFFER_BYTES`, `DEFAULT_RECEIVE_BUFFER_BYTES`) with KDoc.
- **Class KDoc (`:44-51` "Construction side effects"):** add that the receive
  buffer size is a constructor parameter defaulting to 65507, and that a datagram
  larger than the configured size is delivered truncated (not rejected); a WARN
  is logged when a datagram fills a sub-max buffer. The existing "over 65507
  bytes is delivered truncated" wording (`:40-43`) stays accurate for the
  default.

### 3.2 `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/MultiConnectionUDPClient.kt`

- **Constructor (`:93-96`):**
  ```kotlin
  class MultiConnectionUDPClient @JvmOverloads constructor(
      private val serverAddress: InetAddress,
      private val serverPort: Int = MultiConnectionUDPServer.COMMON_LISTEN_PORT,
      private val receiveBufferBytes: Int = MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
  ) {
      init {
          require(
              receiveBufferBytes in
                  MultiConnectionUDPServer.MIN_RECEIVE_BUFFER_BYTES..MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES,
          ) { "receiveBufferBytes must be ${MultiConnectionUDPServer.MIN_RECEIVE_BUFFER_BYTES}..${MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES}, was $receiveBufferBytes" }
      }
  ```
  The `init` block must precede `private val socket = DatagramSocket()` (`:98`).
- **`handshake` (`:127`)** and **`receiveLoop` (`:210`):**
  `ByteArray(RECEIVE_BUFFER_BYTES)` → `ByteArray(receiveBufferBytes)`.
- **`receiveLoop` (`:209-234`):** after `socket.receive(packet)`, add:
  ```kotlin
  if (packet.length == buffer.size && buffer.size < MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES) {
      log.warn(
          "Datagram from {} filled the {}-byte receive buffer and may have been truncated",
          packet.socketAddress, buffer.size,
      )
  }
  ```
  (inline comment: dead at the default 65507; fires only for a lowered buffer).
- **Class KDoc (`:88-92`):** add `@param receiveBufferBytes size of the datagram
  receive buffer, 512..65507; defaults to 65507 so no well-formed datagram is
  truncated. A datagram larger than this is delivered truncated, not rejected
  (a WARN is logged).`
- **Companion (`:295-301`):** delete `private const val RECEIVE_BUFFER_BYTES`
  (and its KDoc).

### 3.3 `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/UDPSendReceiveServer.kt`

- **Constructor (`:22-26`):**
  ```kotlin
  class UDPSendReceiveServer @JvmOverloads constructor(
      private val targetAddress: InetAddress,
      private val sendPort: Int,
      private val listenPort: Int,
      private val receiveBufferBytes: Int = MultiConnectionUDPServer.DEFAULT_RECEIVE_BUFFER_BYTES,
  ) : AutoCloseable {
      init {
          require(
              receiveBufferBytes in
                  MultiConnectionUDPServer.MIN_RECEIVE_BUFFER_BYTES..MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES,
          ) { "receiveBufferBytes must be ${MultiConnectionUDPServer.MIN_RECEIVE_BUFFER_BYTES}..${MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES}, was $receiveBufferBytes" }
      }
  ```
  The `init` block must precede `private val sendSocket` / `listenSocket`
  (`:29-32`).
- **`receiveLoop` (`:90-113`):** `ByteArray(RECEIVE_BUFFER_BYTES)` →
  `ByteArray(receiveBufferBytes)`; after `listenSocket.receive(packet)` add the
  same truncation-visibility WARN as §3.2 (now reachable when a small buffer is
  configured; at the default 65507 it never fires — so `UDPSendReceiveServer`
  gets the "no silent truncation at default" guarantee that #8 gave the other two
  classes).
- **Companion (`:160-169`):** delete `private const val RECEIVE_BUFFER_BYTES` and
  its KDoc.
- **Class KDoc (`:9-21`):** add a **Boundary-Ring** paragraph:
  > ### Maximum payload
  > A received datagram is decoded whole up to **65507 bytes** (the maximum UDP
  > payload over IPv4, and the default `receiveBufferBytes`). A datagram larger
  > than the configured buffer is delivered **truncated, not rejected** (a WARN
  > is logged). For real-network use keep frames under the path MTU
  > (**~1200 bytes**) to avoid IP fragmentation and the loss it brings.
  Add `@param receiveBufferBytes size of the datagram receive buffer, 512..65507;
  defaults to 65507.`

*No change to `send`, `startListening`, `shutDown`, `close`, or the two-socket
model. `UDPSendReceiveServer` stays text-only — this plan does not add a binary
receive path to it.*

### 3.4 `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/CommonChannel.kt`

- **`receive` (`:61-70`):** after `socket.receive(packet)`, add:
  ```kotlin
  if (packet.length == buffer.size && buffer.size < MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES) {
      log.warn(
          "Datagram from {} filled the {}-byte receive buffer and may have been truncated",
          packet.socketAddress, buffer.size,
      )
  }
  ```
  `CommonChannel` is `internal`; no public API impact. This covers the server's
  receive path (which flows through `CommonChannel.receive`).

### 3.5 `README.md`

- **`### webtools-udp` table, `UDPSendReceiveServer` row (`:51`):** append
  "— receives datagrams up to 65507 bytes (configurable via `receiveBufferBytes`),
  truncating larger ones."
- **Datagram-size note (`:122-124`), currently under "Binary application
  payloads":** generalise so it covers all three receive types and mentions the
  parameter, e.g.:
  > Every receive path in this module — `MultiConnectionUDPServer`,
  > `MultiConnectionUDPClient`, and `UDPSendReceiveServer` — accepts datagrams up
  > to **65507 bytes** (the maximum UDP payload over IPv4) whole; a larger
  > payload is truncated, not rejected. Each constructor takes an optional
  > `receiveBufferBytes` (512..65507, default 65507) to shrink the per-instance
  > receive buffer when datagrams are known to be small; a datagram larger than
  > the configured size is truncated and a WARN is logged.
  Keep the existing "~1200 bytes / IP fragmentation" sentence.
- **Dependency-coordinate snippet (`:19`):** bump `webtools-udp:1.1.0` →
  `webtools-udp:1.2.0`.

### 3.6 `webtools-udp/build.gradle.kts`

- **`version` (`:6`):** `"1.1.0"` → `"1.2.0"`.
- **Version comment (`:5`):** add `// 1.2.0: configurable receiveBufferBytes on
  all three receive types (default 65507); UDPSendReceiveServer no longer
  truncates at 1024; documented receive ceiling everywhere (Issue #9).`

### 3.7 `docs/issue-9-receive-buffer-plan.md`

- This document. Committed in the same commit as §3.1–§3.4 (the first
  implementation stage). Backfill the `Commit:` / `PR:` header fields with real
  references once they exist (as was done for #8 in commit `e27d8de`).

---

## 4. Documentation impact (Audience-Reach rings)

| Ring | Touched? | What moves |
|---|---|---|
| **Inner core** (in-editor) | yes | Inline comments on the truncation-visibility check (why it's dead at the default). |
| **Component ring** (KDoc) | yes | `@param receiveBufferBytes` on all three constructors; KDoc on the new public `MAX_UDP_PAYLOAD_BYTES` / `MIN_RECEIVE_BUFFER_BYTES` / `DEFAULT_RECEIVE_BUFFER_BYTES`; `UDPSendReceiveServer` class KDoc gains the maximum-payload paragraph; deleted-const KDoc removed. |
| **Boundary ring** (protocol/serialization) | yes | The "effective maximum application payload = 65507, truncated above it, WARN on a sub-max buffer" statement on `UDPSendReceiveServer` KDoc and the README UDP section — the issue's explicit ask (b). No wire-format change. |
| **Architectural outer layer** | no | No new module, port, protocol, or topology change. |
| **README** | yes | Per the README-currency rule: `UDPSendReceiveServer` now accepts larger datagrams (changed external shape); three published constructors gain an argument; dependency-coordinate version bump. |

---

## 5. Test plan (5-Level hierarchy)

Package root mirrors production `com.spartanlabs.webtools.udp` →
`com.spartanlabs.testing.<level>.webtools.udp`. One class per file. Each class
carries the `@Tag` for its level (bound to the per-level Gradle task in the root
build).

### Level 1 — `testing.gating`

- **New:** `webtools-udp/src/test/kotlin/com/spartanlabs/testing/gating/webtools/udp/ReceiveBufferValidationGatingTest.kt`
  — the `require` guard rejects `0`, `-1`, `511`, `65508`, `Int.MAX_VALUE` with
  `IllegalArgumentException` **before any socket is bound**, for all three
  constructors; and accepts `512`, `1200`, `65507` (constructing, then
  immediately `stop()` / `close()`). Because the guard precedes the socket binds
  (§2.5), the rejection path binds nothing and is deterministic and fast. The
  accepting cases bind — for `MultiConnectionUDPServer` that is port 9998, so this
  class must be covered by the module's `commonUdpPortLock` (it runs under the
  `test` / `gatingTest` tasks, which already use the service). If binding 9998 in
  a gating test is undesirable, split the *accepting* server case into the
  Level-3 class below and keep only the rejection cases (no bind) here.

### Level 2 — `testing.component`

- No new isolated-logic unit under test — the change is a constant, a constructor
  guard (covered at Level 1), and a buffer-size wiring. Nothing to add.

### Level 3 — `testing.integration`

- **`UDPSendReceiveServerTest.kt` (extend):**
  - **New:** `receives a datagram larger than 1024 bytes intact` — start a
    receiver, send ~4 KiB of UTF-8 text, assert the delivered string equals the
    sent string (length + content). **Core regression test** for the issue's live
    defect (§1.2); must fail against the pre-change tree.
  - **New:** `receives a ~60 KiB datagram intact` — locks the documented 65507
    ceiling for this type at the default.
  - **New:** `a configured 512-byte buffer truncates a larger datagram and logs a
    warning` — construct `UDPSendReceiveServer(..., receiveBufferBytes = 512)`,
    send 2 KiB, assert the `onMessage` string's UTF-8 length ≤ 512 and that a
    WARN containing "may have been truncated" was captured by a logback
    `ListAppender`.
  - **New:** `a datagram exactly the configured buffer size arrives whole`.
- **`MultiConnectionUDPClientTest.kt` (extend):** **New:** construct with
  `receiveBufferBytes = 2048`, drive a >2 KiB datagram from the fake peer, assert
  truncation to 2048 and a logged WARN — confirms the parameter reaches the
  client `receiveLoop`.
- **`MultiConnectionUDPServerTest.kt` (extend):** **New:** a test subclass
  constructed with `receiveBufferBytes = 2048`; send a >2 KiB datagram to port
  9998, assert the delivered payload is truncated to 2048 and a WARN is logged —
  confirms the parameter reaches `CommonChannel.receive` via the server.
- **`CommonChannelTest.kt` (extend):** **New:** `receive logs a warning when the
  datagram fills a sub-max buffer` — call `receive` with a small caller buffer,
  send a larger datagram, assert the WARN; and `receive does not warn at a 65507
  buffer`.

### Level 4a — `testing.deterministic`

- N/A — no new pure input→output function (validation is an inline `require`, not
  a standalone function).

### Level 4b — `testing.e2e`

- **`MultiConnectionUDPClientServerE2ETest.kt` (extend):** **New:**
  `a large world-state-style payload round-trips client↔server intact` — real
  `MultiConnectionUDPServer` subclass + real `MultiConnectionUDPClient` over
  loopback, push an ~8 KiB payload each direction through the actual
  handshake/session, assert byte-exact receipt at the default buffer. Mirrors the
  issue's motivating case (per-tick deltas >1 KB).

### Level 4c — `testing.nonfunctional`

- **`MultiConnectionUDPClientNonFunctionalTest.kt` (extend):** the existing
  `an 8 KiB binary payload round-trips whole via startBytes` guards the default.
  **New:** `a ~60 KiB payload round-trips whole via startBytes` — locks the
  documented 65507 maximum against regression.
- Per-instance memory delta (1 KiB → 64 KiB backing array, or the reverse when
  tuned down) is not meaningfully assertable in a unit test — see non-automatable
  below.

### Level 5 — `testing.uat`

- **`MultiConnectionUDPServerUatTest.kt`:** add a checklist item to the (manual /
  `@Disabled`) evaluation: "Operator confirms world-state delta frames larger
  than 1 KB are received intact end to end over a real network path, and that
  frames near/above the path MTU show the expected fragmentation/loss trade-off."

### Cannot be automated

- Real multi-hop IP fragmentation and the packet loss it causes for datagrams
  between ~1200 and 65507 bytes — loopback never fragments or drops, so the
  "keep under MTU" guidance can only be validated in the field (Level 5).
- Per-instance memory footprint impact at consumer scale.

---

## 6. Risks & edge cases

- **`UDPSendReceiveServer` behaviour change.** Any consumer relying on
  truncation-at-1024 (none plausible) now receives full datagrams. Each instance's
  default backing buffer grows 1 KiB → 64 KiB; a consumer that creates many
  instances sees a real memory delta and can now opt back down via
  `receiveBufferBytes`. Same trade-off #8 accepted for the `Multi*` classes.
  Noted in the changelog line.
- **Large-string allocation.** `UDPSendReceiveServer.receiveLoop` allocates
  `String(data, 0, length, UTF_8).trim()` per datagram — now potentially up to
  64 KiB. No correctness issue; GC pressure only under a high-rate large-datagram
  workload, which the "keep under MTU" guidance already discourages.
- **`@JvmOverloads` correctness is load-bearing.** If the generated constructor
  signatures omit a pre-existing one, precompiled consumers break at runtime.
  Mitigation: the §2.3 `javap` verification step before the release is tagged.
- **Abstract-class constructor change** (`MultiConnectionUDPServer`) is the
  riskiest edit. Kotlin `: MultiConnectionUDPServer()` subclasses stay
  source-compatible; `@JvmOverloads` keeps `<init>()` for compiled subclasses. A
  subclass with its own secondary constructors chaining `: super()` is still fine
  (the generated no-arg is preserved). Call it out in the PR description.
- **Foot-gun.** A configured buffer below a real datagram size reintroduces
  truncation. Mitigated by: default 65507, `MIN_RECEIVE_BUFFER_BYTES` floor, the
  WARN log, and explicit KDoc/README wording.
- **Construction ordering.** The `require` guard must precede socket binds (§2.5)
  or a rejected size leaks a bound port (server: port 9998). Covered by the
  Level-1 test asserting no bind on rejection.
- **Concurrency / performance.** The receive buffer is per-listener-thread,
  single-reader; `receiveBufferBytes` is an immutable field. No new shared state,
  no new lock, no change to any send path. Kernel receive cost is unchanged
  (copies actual bytes only); delivery is `copyOf(length)`.
- **Cross-repo impact.** None. No wire-format, protocol, or shared-type change —
  the buffer is a purely receive-side local concern. Downstream adoption is the
  consuming repo's concern per standing project guidance and is not a gate on
  closing #9.

---

## 7. Version

**`webtools-udp` `1.1.0` → `1.2.0`** (D5).

- Additive constructor parameter on three published types, kept
  **binary-compatible** via `@JvmOverloads` (§2.3), plus an additive capability
  change on `UDPSendReceiveServer` (receives up to 65507 now, was 1024). The
  versioning convention maps an addition to a **third-number bump** — not a
  letter suffix (bugfix-only), not `2.0.0` (no breaking change).
- Mirrors #8's two-commit shape: implementation commit(s), then a
  `build: bump webtools-udp to 1.2.0` commit (cf. `5fe3f94` then `175e92f`).
- **Before tagging:** run `javap` on the three built constructor `.class` files
  and confirm every pre-1.2.0 signature is still present.

---

## 8. Decisions (resolved)

All open decisions were resolved by the maintainer — see the table in §2.2
(D1–D5). Summary:

- **D1 / D5:** add `receiveBufferBytes` to all three receive types with
  `@JvmOverloads`; stay at 1.2.0; verify signatures with `javap`.
- **D2:** core-only is an accepted resolution; close #9 with an explanatory
  comment.
- **D3:** default 65507; validate `512..65507`; opt-in for a *smaller* buffer
  only.
- **D4:** fragment/reassembly deferred to GitHub Project 5 (see §9).

No decisions remain open.

---

## 9. Sequencing & follow-ups

1. **Stage 1 — `UDPSendReceiveServer` parity + docs.** §3.3 (minus the parameter
   for now if splitting finely — otherwise include it), §3.5 README, plus this
   plan doc, in **one commit**. Simplest: do §3.1–§3.5 as one implementation
   commit.
2. **Stage 2 — parameter across the other two + `CommonChannel`.** §3.1, §3.2,
   §3.4 with the public constants. (Can be folded into Stage 1 — one coherent
   change — at the executor's discretion.)
3. **Stage 3 — tests.** §5, all levels. Separate commit. Run the `javap`
   signature check here.
4. **Stage 4 — version bump.** §3.6. Separate commit.
5. **After merge:** backfill this doc's `Commit:` / `PR:` header fields. Comment
   on #9 (per D2) explaining the #8 relationship and the new parameter, then
   close it.

**Deliberately deferred (not designed here):**

- **Fragment/reassembly and large-payload framing** — owned by the GitHub Project
  *"webtools-udp: large-payload framing"*
  (<https://github.com/orgs/SpartanLaboratories/projects/5>), which holds 6 draft
  issues covering the design. This plan deliberately does not touch framing; it
  only makes the single-datagram ceiling explicit, documented, and (downward-)
  tunable.
- A 1-byte datagram-type tag separating control from application traffic (already
  a follow-up in `docs/issue-8-binary-datagram-path-plan.md` §10) — a natural
  host for a fragment header; in scope for Project 5, not here.
- A binary (raw `ByteArray`) receive path on `UDPSendReceiveServer` — out of
  scope; that type stays text-only here.

---

## 10. Version control

- **Branch:** `feat/issue-9-receive-buffer` off `master`.
- **Commit sequence** (each a coherent unit):
  1. `feat: make the webtools-udp receive buffer size configurable and document the receive ceiling (Issue #9)` — §3.1–§3.5 (all production changes + README) **plus this plan document**.
  2. `test: cover configurable receive buffer size and large-datagram receipt (Issue #9)` — §5, all levels; includes the `javap` signature verification.
  3. `build: bump webtools-udp to 1.2.0 for the configurable receive buffer (Issue #9)` — §3.6.
- The plan document rides in commit 1 (the first implementation stage) so
  `git log --follow` permanently binds plan to implementation.
- **Working tree is clean** as of planning — no unrelated pre-existing changes to
  carve into a separate commit.
- **Commit trailer** on every commit:
  ```
  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_0141bPYnAyZiGZejNrLoaUd2
  ```
- **PR description** ends with:
  `🤖 Generated with [Claude Code](https://claude.com/claude-code)`
- Do not commit or push until the maintainer asks; open the PR against `master`.
