# Issue #14 — reliable-ordered sub-channel over the UDP transport (design options)

## Header / Association

- **Covers:** `SpartanLaboratories/WebTools#14` — *"Design discussion: a
  reliable-ordered sub-channel over the UDP transport"* (labels: `enhancement`,
  `question`). Opened by the maintainer as an explicit **discussion / question**
  issue: *"not asking for an implementation yet, just a decision on whether
  WebTools takes this on."*
- **What this document is:** a **decision-support / design-options** document, not
  an implementation plan. It exists for the maintainer to read before giving a
  go / no-go. There is no file-by-file breakdown and no test matrix — those belong
  in the implementation plan that would follow a "go".
- **Branch:** landed via `docs/issue-14-reliable-channel-design` (off `master`,
  deleted after merge). If greenlit for implementation, work opens on a `2.x`
  integration branch (see §12) and this document moves onto it with the first stage.
- **Commit:** `6107d0d` (`docs:` add this design-options doc). **PR:**
  [#29](https://github.com/SpartanLaboratories/WebTools/pull/29).
- **Status:** **decision made (2026-09-09)** — the maintainer accepted the
  recommended option for every decision D1–D10 (§13). WebTools **takes this on** as
  the headline feature of **`webtools-udp` `2.0.0`**. Next step: an implementation
  plan (`docs/issue-14-reliable-ordered-channel-plan.md`) turning §5–§9 into a
  file-by-file design and a 5-level test matrix. No code written yet, no production
  file touched.
- **Current baseline:** `webtools-udp` `1.6.0` (Maven Central;
  `io.github.spartanlaboratories:webtools-udp`, package
  `com.spartanlabs.webtools.udp`). The reliability layer, if taken on, is the
  headline feature of **`webtools-udp` `2.0.0`** (see §4).
- **Related docs:**
  - `docs/issue-13-link-quality-probe-plan.md` — the RFC 6298 SRTT/RTTVAR
    estimator (`Rtt`, `LinkQualityTracker`) this design reuses for the RTO, and
    the opt-in / lazy-scheduler / additive-wire-token patterns.
  - `docs/issue-8-binary-datagram-path-plan.md` §2.4 — the classifier-collision
    limitation this design finally resolves with a framing byte, already named
    there as a "clean-break protocol revision" follow-up.
  - `docs/issue-12-scheduled-keepalive-plan.md`, `docs/issue-11-*` — the
    default-method additive-growth idiom on the published `Connection` interface.
  - `docs/split-into-three-modules-plan.md` — the 3-artifact split rationale
    (unrelated *dependencies*, not unrelated *concerns*), weighed in §4.
  - `docs/issue-1-tier-2-plan.md` §2.1 — the canonical inbound-datagram flow the
    new framed path slots into.

---

## 1. Context

### 1.1 The ask

GameTools' authoritative netcode needs **two delivery modes over one connection**:

| Mode | Traffic | Requirement |
|---|---|---|
| unreliable / newest-wins | per-tick world-state snapshots | never retransmit — a dropped snapshot is obsolete the instant the next is sent |
| reliable / ordered | discrete events & commands (ability cast, chat, spawn/despawn, inventory) | loss is not acceptable; in-order delivery |

This is the standard split ENet / LiteNetLib / Lidgren provide. It is
transport-generic and does not belong in a game library. The question the issue
poses is whether **WebTools** should provide it, and if so in what shape and at
what version.

### 1.2 What `webtools-udp` is today (read directly from `1.6.0`)

| Fact | Location |
|---|---|
| Pure unreliable UDP: no acks, no sequence numbers, no retransmission | whole module — there is no such code |
| The *only* ordering guarantee is that the single-threaded dispatch executor delivers, in arrival order, the datagrams that *do* arrive | `MultiConnectionUDPServer.kt:184-188` (`mcups-dispatch`), `MultiConnectionUDPClient.kt:166-168` (`mcupc-dispatch`) |
| Every inbound datagram is classified by a **trimmed-UTF-8 token match** — `Iam` / `REGISTERED` / `REFUSED` / `KA` / `PING` / `PONG` — before it can reach an app handler | `HandshakeCoordinator.classify` `HandshakeCoordinator.kt:128-151`; `MultiConnectionUDPClient.receiveLoop` `:322-336`; tokens in `HandshakeWireFormat.kt` |
| Binary app payloads must **dodge** that classifier by leading with a byte `>= 0x80` or `0x00` — a documented consumer burden, not enforced | `HandshakeWireFormat.kt:56-59`, `README.md:132-139` |
| An RFC 6298 SRTT/RTTVAR estimator already exists (`internal`), fed only by the PING/PONG probe | `Rtt.kt`, `LinkQualityTracker.kt` (added in `1.6.0`) |
| Receive ceiling is 65507 bytes; consumers are advised to keep frames `< ~1200` bytes (path MTU) to avoid IP fragmentation | `MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES`, `README.md:141-148` |
| Opt-in background work uses a lazily-created per-role daemon `ScheduledExecutorService` (`mcup{c,s}-keepalive`, `mcup{c,s}-probe`) — zero cost until a consumer opts in | `PeriodicScheduler`, wired in `MultiConnectionUDPServer.kt:196-206`, `MultiConnectionUDPClient.kt:143` |
| `Connection` is a **published interface**; it grows by **default-bodied** methods (never abstract ones), overridden in `UDPConnection` | `Connection.kt` (`startKeepAlive` / `startProbe` defaults) |
| A same-name reconnect from a new origin **supersedes** the stale registration — the old `Connection` is terminated and a fresh one minted; no per-connection state carries over | `HandshakeCoordinator.kt:194-206` |
| Teardown paths: `terminate()` → `TERMINATED`, supersede → `SUPERSEDED`, idle sweep → `TIMEOUT`, `stop()` → silent | `HandshakeCoordinator`, `DisconnectReason` |

So today the transport is a **thin demux layer over `DatagramSocket`**. A
reliable-ordered channel turns it into a **real protocol stack**. That framing —
that this is not a 1.x-shaped addition — drives every recommendation below.

---

## 2. Recommendation up front

1. **Take it on — yes.** Reliable-ordered UDP is transport-generic; there is **no
   maintained, correctly-shaped JVM library** for it (§3.3); GameTools already
   depends on `webtools-udp` and `webtools-udp` **owns the socket**, so a second
   transport dependency that also wants to own a socket is strictly worse (two
   sockets, two NAT mappings, two keepalive regimes). §3 lays out the
   alternatives fairly; option (a) wins on the merits.

2. **Ship it as `webtools-udp` `2.0.0`, not as a new `webtools-udp-reliable`
   artifact.** The reliability layer is the *core concern* of the UDP module, adds
   **no new dependency** (still slf4j-only), is deeply coupled to the existing
   classifier / socket / registration lifecycle / RTO estimator, and needs a
   **wire break** (a framing byte) regardless. A separate artifact would force a
   large new public SPI out of `webtools-udp` just to feed it. The 3-artifact
   split was about unrelated *dependencies* (Selenium, jsoup); this is not that.
   §4 compares the two options in full.

3. **Bundle the long-standing classifier-collision fix into the same major.**
   `2.0.0` introduces a **1-byte datagram-type prefix** on every *post-handshake*
   datagram. That is the clean break that (a) makes room for the reliable header,
   (b) retires the "lead with `0x80`" consumer burden from Issue #8, and (c) gives
   `2.0.0` a coherent story: *"webtools-udp gets a real framed wire protocol, with
   a reliable-ordered channel as the headline feature."*

4. **v1 is deliberately minimal** (§9): **one** reliable-ordered channel alongside
   the existing unreliable path; per-channel sequence space; selective-ack
   (32-bit ack bitfield) + RFC 6298 RTO from the Issue #13 estimator + timed
   retransmit with Karn's algorithm; a bounded receive-side reorder buffer; a
   **fixed in-flight window** (a bound, *not* congestion control); an **explicit
   message-size cap with a typed error instead of fragmentation**; and — stated as
   loudly as possible — **no congestion control in v1**. A real network under
   sustained load will demand it; that is the first post-v1 follow-up.

5. **No schedule pressure.** The interim option — GameTools runs app-level
   ack/retransmit over its low-rate event stream — is viable and stays supported.
   This can be done properly rather than fast.

---

## 3. The three options

### 3.1 Option (a) — WebTools takes it on  *(recommended)*

**For:**
- Transport-generic capability that every real-time UDP consumer needs; it does
  not belong in GameTools.
- `webtools-udp` already owns the socket, the NAT-traversal handshake, the
  registration lifecycle, the single-threaded ordered dispatch, and (since
  `1.6.0`) an RFC 6298 RTT estimator. The reliable channel is ~70% "wire it to
  machinery that already exists".
- One dependency for GameTools, not two transports fighting over a socket.
- Keeps the "one socket for everything" invariant (Issue #1) intact.

**Against:**
- It is real protocol-stack code with real correctness surface (sequence
  wraparound, ack edge cases under burst loss, reorder-buffer bounds, teardown of
  un-acked data). Non-trivial to test well.
- It is a **major version** and a **wire break** for post-handshake data.
- Ongoing maintenance burden on a small library.

**Why it wins:** the "against" list is the cost of the capability existing *at
all* — it does not get cheaper in GameTools or in a bolt-on library, and it gets
more expensive everywhere the socket is not already owned.

### 3.2 Option (b) — GameTools lives with unreliable-only + app-level reliability

GameTools implements ack/retransmit itself over its (low-rate, bursty) event
stream, on top of `webtools-udp`'s binary datagram path.

**For:**
- Zero WebTools change; no major-version churn.
- The event stream is low-rate — a naive "resend every unacked message every N ms
  until acked" is genuinely adequate at that volume and is maybe ~150 lines.
- It is the correct **interim** answer and de-risks the schedule.

**Against:**
- Puts transport logic in a game library — exactly what the issue says is wrong.
- Every *other* future WebTools consumer that needs reliability re-solves it.
- The app-level layer cannot see RTT (the estimator is `internal`), cannot
  piggyback acks efficiently on the transport's own datagrams, and cannot
  coexist cleanly with the classifier.
- Two half-solutions (GameTools' and the next consumer's) instead of one.

**Verdict:** correct as a **bridge**, wrong as the **destination**. The
recommendation keeps (b) explicitly supported (and worth a short README pattern —
Open Decision D10) until `2.0.0` lands.

### 3.3 Option (c) — adopt an existing library

Survey of what exists on / near the JVM:

| Library | Status | Shape | Verdict for this use |
|---|---|---|---|
| **KryoNet** (`EsotericSoftware/kryonet`) | Minimally maintained; last tag `2.22.0-RC1`. | TCP + UDP client/server, **but the UDP path has no reliability layer at all** — *"KryoNet does not currently implement any extra features for UDP, such as reliability or flow control"* ([jitpack](https://jitpack.io/p/crykn/kryonet), [repo](https://github.com/EsotericSoftware/kryonet)). Bundles Kryo serialization (opinionated). | **No** — does not provide the feature; would still need building. |
| **JGN** (Java Game Networking) | **Dead** (~2007, SourceForge). | Had reliable UDP + message system. | **No** — unmaintained, JDK-4-era. |
| **Aeron** (`aeron-io/aeron`) | **Actively, heavily maintained.** | *"Efficient reliable UDP unicast, UDP multicast, and IPC message transport"* — but built around a separate **media-driver** process / shared-memory model, publication/subscription streams, datacenter low-latency messaging. No NAT traversal, no "unreliable newest-wins alongside reliable on one connection", large footprint, its own thread architecture. ([repo](https://github.com/aeron-io/aeron), [Scott Logic assessment](https://blog.scottlogic.com/2020/02/28/is-aeron-a-good-choice-for-a-messaging-solution.html)) | **No** — wrong domain and wrong shape for NAT'd internet game clients; far too heavy. |
| **Netty** | Actively maintained. | A datagram channel and an event loop. **Not** a reliable-UDP library — you hand-roll acks/retransmit/ordering on top. | **No** — that is precisely the work in question, now with a much bigger dependency and no NAT handshake. |
| **LiteNetLib / ENet / Lidgren** | LiteNetLib actively maintained; ENet/Lidgren mature. | The right *design*, wrong *platform* — C# / C. LiteNetLib's `DeliveryMethod` enum (`ReliableOrdered`, `ReliableUnordered`, `ReliableSequenced`, `Sequenced`, `Unreliable`), multi-channel model, 3-byte reliable / 1-byte unreliable headers, and automatic fragmentation are the reference to borrow from. ([docs](https://revenantx.github.io/LiteNetLib/api/LiteNetLib.DeliveryMethod.html)) | **No** on the JVM — but the best API model to imitate. |
| **ice4j** (Jitsi) | Maintained. | ICE / STUN / TURN NAT traversal — not a delivery-reliability layer. | **No** — orthogonal concern. |
| **SCTP** (JDK `com.sun.nio.sctp`, lksctp) | JDK API present. | A genuine reliable-ordered multi-stream protocol. But JDK SCTP is **Linux-only**, not UDP-encapsulated by default (needs `sctp` IP protocol, blocked by most NATs/middleboxes). | **No** — undeployable to internet game clients. |

**Conclusion:** there is no maintained JVM library that is both *correctly shaped*
(NAT-friendly, one connection, reliable + unreliable side by side, lightweight)
and *actually provides reliability*. Adopting one means adopting Aeron's or
Netty's weight and *still* building the channel model on top. Building a minimal,
well-scoped layer inside `webtools-udp` — which already has the socket, the
handshake, and the RTT estimator — is less total code and less dependency risk.

---

## 4. Where it lives: `webtools-udp` `2.0.0`  vs  a new `webtools-udp-reliable` artifact

| Dimension | `webtools-udp` `2.0.0` (recommended) | new `webtools-udp-reliable` artifact |
|---|---|---|
| **Dependency story** | No new dependency — still slf4j-only. | No new *third-party* dependency, but a hard dependency on `webtools-udp` + a large new **public SPI** out of it. |
| **Coupling reality** | Reliable header, retransmit timer, and ack processing live *inside* the listener/classifier/`HandshakeCoordinator`/`PeriodicScheduler` machinery they need. Natural fit. | The classifier, the raw datagram send/receive seam, the registration lifecycle, the teardown callbacks, and the `internal` `Rtt`/`LinkQualityTracker` would all have to become **public extension points**. That surface is itself a permanent compat burden — larger than the feature. |
| **Wire break** | `webtools-udp` needs the framing byte **regardless** (the classifier must learn "this datagram is framed app data" whether or not the reliable code ships here). So `webtools-udp` takes a major version either way. | Does not avoid the `webtools-udp` wire break; just splits the feature across two artifacts and two release cadences. |
| **Split-plan precedent** | The 3-way split isolated unrelated **dependencies** (Selenium, jsoup, unirest). Reliable UDP pulls in nothing and *is* the module's core concern. Precedent does **not** argue for a 4th artifact. | Would be the first artifact split on concern-granularity rather than dependency-granularity — a new pattern for the repo. |
| **Consumer weight** | Reliable machinery is **opt-in** (no thread, no allocation until a reliable channel is opened) — same as keepalive/probe. A consumer that only wants unreliable pays for a slightly larger jar and nothing else. | Lets a size-sensitive consumer depend on a certified-minimal core. Marginal benefit for a small slf4j-only Kotlin jar. |
| **Clean-break policy** | `2.0.0` is the honest signal: the transport model changed. One coordinated major. | Two version lines to reason about; `webtools-udp` *still* goes to `2.0.0`. |
| **Maven Central** | One more `2.x` line on an existing coordinate. | A new coordinate to register, sign, document, and keep in lockstep. |

**Recommendation: `webtools-udp` `2.0.0`.** Keep the option to *extract*
`webtools-udp-reliable` later open — but only if a real need appears for the core
to stay minimal (e.g. a safety-certified consumer). Do not split preemptively;
splitting is cheap to do later and expensive to undo.

Per the recorded versioning convention (letter suffix = bugfix only; an addition
bumps the third number) this is unambiguously a new **major**: the wire format
changes and the transport contract changes.

---

## 5. Channel API shape

`Connection` and `MultiConnectionUDPClient` today expose `push` / `actuate`
(text) and `push(ByteArray)` / `actuateBytes` / `startBytes` (binary). The
reliable channel has to sit beside those. Three shapes:

### Option A — a channel handle: `connection.channel(RELIABLE_ORDERED)`

```kotlin
enum class DeliveryMode { UNRELIABLE, RELIABLE_ORDERED }

interface UdpChannel {
    /** Enqueue [bytes] for delivery under this channel's guarantees. */
    fun send(bytes: ByteArray): Result<Unit>
    /** Bind the inbound handler for this channel's traffic. */
    fun actuate(onMessage: (ByteArray) -> Unit): Result<Unit>
}

interface Connection {
    // ...existing members unchanged...
    /** The channel handle for [mode]; each mode has its own sequence space. */
    fun channel(mode: DeliveryMode): UdpChannel
}
```

- **For:** this is the durable, LiteNetLib-shaped end state. When multiple
  reliable channels or an unreliable-sequenced mode land later, they slot in with
  no further break. The maintainer floated exactly this in the issue.
- **Against:** a new published interface (`UdpChannel`) and a new published enum;
  `channel()` must be a default method on `Connection` returning something even
  for a fake; more surface for a v1 that has exactly one channel of each kind.
  `send`'s `Result<Unit>` is a weak delivery signal (see §7 on backpressure).

### Option B — typed methods beside the existing ones

```kotlin
interface Connection {
    // ...existing...
    /** Reliable-ordered send. [Result.failure] if the in-flight window is full or the connection is down. */
    fun sendReliable(bytes: ByteArray): Result<Unit> =
        Result.failure(UnsupportedOperationException("This Connection has no reliable channel"))
    /** Bind the reliable-ordered inbound handler; independent of [actuate]/[actuateBytes]. */
    fun actuateReliable(onMessage: (bytes: ByteArray) -> Unit): Result<Unit> =
        Result.failure(UnsupportedOperationException("This Connection has no reliable channel"))
}
```

Mirror on the client as `sendReliable` / `startReliable`. Exactly the
default-bodied additive-growth idiom Issues #11/#12/#13 established.

- **For:** smallest possible v1 surface; identical in style to
  `actuateBytes`/`startBytes`; no new published types; trivial to fake.
- **Against:** if multiple reliable channels ever land, `sendReliable(bytes,
  channelId)` overloads get awkward and a later move to Option A is a second
  clean break.

### Option C — a channel-id parameter: `connection.push(bytes, channel = 2)`

- **Against:** overloads `push` again (already `String`/`ByteArray`), buries the
  delivery-mode semantics in an int, and gives no place to hang
  `actuateReliable`. Rejected — not carried forward.

**Recommendation → Open Decision D2.** Lean **Option A** (the handle) because the
clean-break policy makes a *second* break to reach the durable shape expensive,
and the handle is where LiteNetLib / ENet / Lidgren all converged. Accept the
extra `UdpChannel` + `DeliveryMode` surface as the price of not breaking twice.
Option B is a defensible "smallest v1" choice if the maintainer would rather not
publish the channel abstraction until multi-channel is real.

---

## 6. Wire design

### 6.1 The framing byte (the `2.0.0` break)

Today the classifier runs a trimmed-UTF-8 token match on **every** datagram.
`2.0.0` changes this: **the handshake stays text** (`Iam` / `REGISTERED` /
`REFUSED` bootstrap the connection and negotiate nothing), and **every
post-`REGISTERED` datagram carries a 1-byte type tag as byte 0**:

| Tag | Meaning | Rest of datagram |
|---|---|---|
| `0x80` | control — keepalive | (empty) — replaces text `KA` |
| `0x81` | control — probe request | `PING` seq (see Issue #13) |
| `0x82` | control — probe reply | `PONG` seq |
| `0x90` | **unreliable** app data | `[channel:1]` + payload — replaces the "lead with `0x80`" convention |
| `0xA0` | **reliable-ordered** app data | reliable header (§6.2) + payload |
| `0xA1` | **reliable ack-only** | reliable header (§6.2), no payload |

Tag values are all `>= 0x80`, so they cannot collide with the ASCII first byte of
`Iam` (`0x49`), `KA` (`0x4B`), `PING`/`PONG` (`0x50`). An unframed
datagram from a `1.x` peer is simply not understood by a `2.0` peer — documented as
"both ends must be `2.0+`". The handshake being text means the *bootstrap* still
works for classifying a first `Iam` from an unknown origin.

**Net effect on Issue #8's limitation:** the classifier no longer inspects
application payloads at all — it dispatches on byte 0. The
"binary-payload-that-trims-to-`KA`" footgun is **gone** in `2.0`. That is a
deliberate, advertised benefit of the major.

### 6.2 Reliable header layout

After the `0xA0` / `0xA1` tag byte:

```
 byte 0        : tag (0xA0 reliable data | 0xA1 ack-only)
 byte 1        : channel id            (v1: always 0x00; reserved for multi-channel)
 bytes 2-3     : sequence number       (uint16, this datagram's seq; absent for 0xA1)
 bytes 4-5     : ack                    (uint16, highest in-order seq received from peer)
 bytes 6-9     : ack bitfield          (uint32; bit n set => (ack - n - 1) also received)
 bytes 10..    : payload               (one application message; absent for 0xA1)
```

- **Reliable data overhead: 10 bytes** (`0xA0`). **Ack-only overhead: 8 bytes**
  (`0xA1`, no seq, no payload).
- **Unreliable framed overhead: 2 bytes** (`0x90` + channel), up from 0 today —
  but the old 0-byte case required a wasted lead byte anyway, so real change is
  ~1 byte.
- Against the size budget (Issue #9: 65507 receive ceiling; `~1200`-byte
  path-MTU advisory): 10 bytes is `< 1%` of a safe frame. Non-issue.

**Sequence width — Open Decision D4.** `uint16` (Gaffer / LiteNetLib style, with
RFC 1982 serial-number arithmetic for wraparound) is recommended for v1: the
reliable channel carries a low-rate discrete-event stream with a small in-flight
window, so 65 536 distinct in-flight sequence numbers is enormous headroom. A
`uint32` is the alternative if the maintainer wants to never think about wrap.

### 6.3 Acks

- **Piggybacked:** every `0xA0` datagram in either direction carries the current
  `(ack, ack bitfield)` for the reverse direction — free on any two-way traffic.
- **Standalone (`0xA1`):** when the reliable channel has received data but has
  nothing to send back, a short-delay timer (a few tens of ms, or coalesced to
  the retransmit tick) emits an ack-only datagram. The event stream is bursty and
  often one-directional, so the standalone ack path is **not** optional.
- The 32-bit bitfield gives each ack ~32× redundancy (Glenn Fiedler,
  [*Reliable Ordered Messages*](https://gafferongames.com/post/reliable_ordered_messages/)),
  which combined with retransmit makes perfect acks unnecessary.

### 6.4 Coexistence with the current classifier

The listener's dispatch becomes:

```
byte0 >= 0x80 ?
  ├─ 0x80/0x81/0x82 → existing control handling (keepalive / probe)
  ├─ 0x90           → unreliable app data → existing actuate/actuateBytes path
  ├─ 0xA0 / 0xA1    → reliable engine (ack processing on listener thread;
  │                    in-order delivery on the dispatch executor)
  └─ else           → drop (unknown tag), WARN
byte0 < 0x80 → text path: Iam / REGISTERED / REFUSED handshake bootstrap only
```

Reliable **delivery** still goes through the single-threaded dispatch executor,
so the "per-connection order is preserved" contract holds and a slow reliable
handler cannot stall the socket.

---

## 7. The reliability mechanism

### 7.1 Sequence spaces

One reliable channel ⇒ **two** independent sequence spaces per connection
(client→server, server→client). The unreliable path keeps its current
no-sequence behaviour (an unreliable-sequenced / newest-wins-with-discard mode is
explicitly **deferred**, §9).

### 7.2 Send side — retransmit queue

- A **sequence buffer** (rolling array indexed by `seq % capacity`, per
  *Reliable Ordered Messages*) of unacked messages, each holding: the payload,
  first-sent time, last-sent time, retransmit count.
- On every send opportunity (a new `sendReliable`, a piggyback, or a retransmit
  tick) any message whose `now - lastSent >= RTO` is re-sent.
- On an incoming ack that covers a message: remove it, and — **only if it was
  never retransmitted** (Karn's algorithm) — feed `now - firstSent` as an RTT
  sample to the estimator.

### 7.3 RTO — from the Issue #13 estimator

`RTO = SRTT + max(clockGranularity, K · RTTVAR)`, `K = 4`, per
[RFC 6298 §2](https://www.rfc-editor.org/rfc/rfc6298). Reuse `Rtt.smoothedRttMillis`
/ `Rtt.smoothedRttVarianceMillis` (the exact functions `LinkQualityTracker`
already uses).

- **Floor:** RFC 6298 mandates 1 s; real-time games use far less. Recommend a
  **configurable floor, default ~200 ms**, documented as a deliberate deviation.
- **Cap:** an upper bound (e.g. 5 s) so a black-holed link does not push the RTO
  to absurdity.
- **Backoff:** double the RTO for a message each time *that message* times out
  again (RFC 6298 §5.5); reset to the estimator value when a fresh (non-retransmitted)
  ack arrives.
- **Estimator sharing — Open Decision D3.** The reliable channel can either (a)
  keep its **own** `Rtt`-based RTO estimator instance, or (b) feed and read the
  connection's shared `LinkQualityTracker` so RTT is unified across the probe and
  the reliable channel. Recommend **(a)** for v1 — a private estimator seeded from
  data acks, no promotion of `1.6.0` internals — with (b) as a later refinement
  once the reliable channel wants to publish its own stats.

### 7.4 Receive side — bounded reorder buffer

- A **delivery cursor** (next in-order seq to hand up) plus a bounded
  reorder buffer (window `W`, default e.g. 256, configurable) holding
  out-of-order messages within `[cursor, cursor + W)`.
- On receipt: record for acking (update `ack` / bitfield); if `seq == cursor`,
  deliver it and drain any now-contiguous buffered successors; if `seq` is ahead
  within the window, buffer it; if `seq < cursor` (duplicate) or `>= cursor + W`
  (too far ahead — receiver stalled), **drop it** but still ack what is in range.
  The sender retransmits the dropped one once the window lets it through.
- Bounded buffer ⇒ bounded receive-side memory even under pathological reordering.

### 7.5 In-flight window — a bound, not congestion control

- A **fixed maximum number of unacked messages** (default e.g. 256,
  configurable). When full, `sendReliable` returns
  `Result.failure(ReliableWindowFullException)` — an explicit backpressure signal
  the application acts on (drop, coalesce, or retry later). **Open Decision D6**
  covers the alternative (a bounded internal queue that absorbs short bursts
  before failing).
- This is **not** congestion control: there is no congestion window, no pacing,
  no send-rate reduction on loss beyond per-message RTO backoff. It exists purely
  so a stalled or black-holed peer cannot make the send queue grow without limit.

### 7.6 No congestion control in v1 — stated plainly

v1 has **no** cwnd, **no** pacing, **no** AIMD, **no** loss-triggered rate
reduction. Under sustained loss the reliable channel will retransmit at
RTO-backoff cadence up to the in-flight window, then fail sends. On a real
congested internet path this is inadequate and **a real network will demand
proper congestion control** — a TCP-friendly / AIMD or QUIC-style controller.
That is the **first** post-v1 follow-up (§14). It is called out here so the
maintainer signs off on shipping without it (Open Decision D9), on the strength
of GameTools' reliable traffic being a low-rate discrete-event stream.

### 7.7 Teardown of un-acked data

- `terminate()` / `stop()` / supersede / idle-`TIMEOUT`-then-app-`terminate()`:
  un-acked reliable data is **discarded**. No lingering, no TIME_WAIT-equivalent.
  A best-effort final flush attempt is optional (Open Decision, minor).
- `sendReliable` after teardown → `Result.failure`.
- **Supersede (NAT rebind) resets the channel.** The current supersede path mints
  a fresh `Connection`; reliable sequence state does **not** carry across it.
  Reliable delivery is guaranteed only within one registration's lifetime.
  Whether a reliable channel should *survive* a rebind (via a resume token — Issue
  #10 territory) is **deferred** (Open Decision D7).
- Retransmit traffic is outbound and does **not** refresh the peer's inbound
  liveness timestamp — so a reliable channel retransmitting into a black hole
  still trips the idle sweep, which is correct.

### 7.8 Flow — the ack / retransmit loop

```mermaid
sequenceDiagram
    participant App as sender app
    participant Eng as reliable engine
    participant RT as mcup?-retransmit
    participant Net as UDP socket
    participant Peer as peer reliable engine

    App->>Eng: sendReliable(msg)
    alt in-flight window full
        Eng-->>App: Result.failure(WindowFull)
    else space available
        Eng->>Eng: seq = nextSeq++; buffer(seq, msg, now)
        Eng->>Net: 0xA0 [ch, seq, ack, ackbits] + msg
        Net->>Peer: datagram
        Peer->>Peer: within window? buffer / deliver in order
        Peer->>Net: 0xA0/0xA1 [ack=highest-in-order, ackbits]
        Net->>Eng: ack datagram
        Eng->>Eng: drop acked; if never-retransmitted -> RTT sample -> Rtt EWMA
    end

    loop every retransmit tick
        RT->>Eng: for each unacked msg where now-lastSent >= RTO
        Eng->>Net: 0xA0 re-send (RTO doubled for that msg; no RTT sample on ack)
    end

    Note over RT: retransmit executor is lazily created on the first<br/>reliable channel, mirroring mcup?-keepalive / mcup?-probe
```

---

## 8. Size-limit / fragmentation interaction

**v1 caps reliable message size; it does not fragment.**

- **Hard ceiling:** a reliable message's payload cannot exceed
  `configuredFrameCap - 10` (reliable header). The absolute max is
  `65507 - 10 - 28 (IP/UDP)` but any frame over the path MTU is guaranteed to IP-
  fragment, and one lost IP fragment drops the whole datagram — which the reliable
  layer then has to retransmit *in full*, amplifying loss badly.
- **Recommended default cap: 1024 bytes** of application payload (comfortably
  under the `~1200`-byte path-MTU advisory once headers are counted), with a
  **configurable hard max of ~8 KB** a consumer can opt into knowing it risks
  fragmentation. **Open Decision D5.**
- Oversize send → `Result.failure(ReliableMessageTooLargeException(size, cap))`.
  Typed, per the Result-based error-handling rule.
- **Why defer fragmentation:** fragmentation on top of reliability + reorder + no
  congestion control is a large correctness surface (per-fragment vs per-message
  acks, reassembly-buffer bounds, fragment-loss amplification). LiteNetLib does it
  — as a mature library with years of soak testing. GameTools' discrete events
  (ability cast, chat line, inventory delta, spawn record) fit in `< 1 KB`.
  Fragmentation waits for a demonstrated need (§14).

---

## 9. v1 scope boundary

### In

- **One** reliable-ordered channel per connection, both directions, alongside the
  existing unreliable path.
- Per-channel (per-direction) `uint16` sequence space with RFC 1982 arithmetic.
- Selective ack: `ack` + 32-bit ack bitfield; piggybacked + standalone (`0xA1`).
- RTO from the RFC 6298 estimator (Issue #13 `Rtt`), configurable floor/cap,
  per-message exponential backoff, Karn's algorithm.
- Bounded retransmit (send) buffer and bounded reorder (receive) buffer.
- Fixed in-flight window with `Result.failure` backpressure.
- Explicit reliable-message size cap with a typed oversize error.
- The 1-byte datagram-type framing prefix on all post-handshake datagrams (the
  `2.0.0` wire break) — which also retires the Issue #8 "lead byte" burden.
- Opt-in: no retransmit thread, no channel state, no allocation until a reliable
  channel is opened (mirrors keepalive/probe).
- Full 5-level test coverage (detail in the implementation plan).

### Out (deferred — see §14)

- Fragmentation of oversize reliable messages.
- Congestion control of any kind.
- Multiple reliable channels / arbitrary channel ids.
- Unreliable-sequenced / newest-wins-with-discard mode.
- Reliable-channel survival across a NAT-rebind supersede (session resume).
- Ordered-but-unreliable, priority scheduling, partial reliability, per-message
  TTL / drop-if-late.

---

## 10. Documentation impact (Audience-Reach rings)

| Ring | Touched? | What must move with the change |
|---|---|---|
| **Inner core** | yes | Retransmit-loop / RTO-backoff / Karn / sequence-buffer comments; why the reorder buffer drops beyond the window; the framing-byte dispatch in the listener. |
| **Component ring** (KDoc) | yes | New KDoc on the channel API (§5), the reliable send/actuate members, the typed exceptions, the size-cap config, any new public value types (`DeliveryMode`, `UdpChannel`, or the reliable stats type). |
| **Boundary ring** (protocol) | **yes — major.** | The framed wire protocol is a new boundary contract: the datagram-type tag table, the reliable header layout, the ack scheme, the "both ends must be `2.0+`" rule. `HandshakeWireFormat` (or a new `TransportWireFormat`) KDoc, the README protocol section (rewritten from "handshake protocol" to "transport protocol"), and a protocol reference in `docs/`. |
| **Architectural outer layer** | yes | The transport is now a protocol stack. A new architecture section + the §7.8 sequence diagram; the new lazily-created `mcup{c,s}-retransmit` daemon thread documented alongside the existing keepalive/probe threads. |
| **README** | yes — substantial | New "Reliable-ordered channel" section; the `2.0.0` version bump across install snippet and component table; the framing-change / migration note; the retired "lead with `0x80`" guidance replaced. Same change as the implementation, per the README-currency rule. |

---

## 11. Risks & edge cases

- **Wire break (the headline risk).** Post-handshake data is not `1.x`-compatible.
  Mitigation: handshake stays text so bootstrap is unaffected; `2.0` requires both
  ends; per standing guidance, downstream adoption is the consumer's concern and
  not a gate on this issue.
- **Burst-loss ack edge cases.** Old sequence-buffer entries surviving a wrap can
  produce false acks (documented in *Reliable Ordered Messages*). Mitigation:
  clear buffer entries between the previous and new highest insert seq; soak-test
  under heavy synthetic loss (Level 4c).
- **Sequence wraparound.** RFC 1982 serial arithmetic everywhere seq is compared;
  a dedicated deterministic (Level 4a) test around the `uint16` boundary.
- **Duplicate / replay.** Delivery cursor + bitfield dedupe; a duplicate below the
  cursor is acked-and-dropped, never re-delivered.
- **Idle reverse path.** Acks would never flow without the standalone-`0xA1`
  timer; that timer is in scope, not optional.
- **Head-of-line blocking.** Reliable-ordered inherently HOL-blocks. Document
  clearly: snapshots use the unreliable path *because* of this; do not put bulky
  or latency-critical data on the reliable channel.
- **Dispatch-executor coupling.** Reliable delivery must go through
  `mcup{c,s}-dispatch` to preserve per-connection ordering; ack processing runs on
  the listener thread; the retransmit tick runs on its own lazy executor. Three
  threads touch per-channel state → it needs the same `@Synchronized`
  discipline `LinkQualityTracker` uses.
- **Interaction with idle detection (Issue #10).** A connection retransmitting
  into a black hole still trips the idle sweep (retransmits are outbound, don't
  refresh inbound liveness) — correct behaviour, but worth an explicit test.
- **Interaction with supersede (Issue #1 / NAT rebind).** Channel state is per
  registration; a rebind silently resets it. Must be documented; session resume
  is deferred (D7).
- **Performance.** Per reliable message: one sequence-buffer slot until acked,
  RTO-paced retransmits, one timer wakeup cadence. All window-bounded. Negligible
  for a low-rate event stream; would need review before anyone runs a high-rate
  reliable stream (which v1 does not target).
- **Cross-repo impact:** not raised here, per standing instruction — downstream
  projects handle their own adoption.
- **Pre-existing working-tree changes:** none — the tree is clean at the time of
  writing.

---

## 12. Version-control approach (if greenlit)

- **Branch model:** a long-lived `2.x` integration branch (e.g.
  `feat/2.0-framed-transport`) off `master`, with the work landing as a **series
  of PRs** into it, then one squash-or-merge of the integration branch to
  `master` when `2.0.0` is cut. A single monolithic PR would be too large to
  review well.
- **Suggested PR series** (each green at all 5 test levels before the next):
  1. **Framing prefix.** Introduce the 1-byte datagram-type tag; migrate the
     unreliable / binary / keepalive / probe paths onto it; rework the listener
     dispatch; retire the "lead byte `>= 0x80`" convention. Publishes as
     `2.0.0-alpha1`. This is a coherent, independently-testable break that
     delivers the Issue #8 classifier fix on its own.
  2. **Reliable engine (internal).** Sequence spaces, ack bitfield, retransmit
     sequence buffer, reorder buffer, in-flight window, RTO from `Rtt` — all
     behind an `internal` surface, tested via component/deterministic levels.
  3. **Public reliable API.** The channel handle or typed methods (D2), the
     retransmit `PeriodicScheduler` wiring, the size cap + typed exceptions,
     `UDPConnection` / `MultiConnectionUDPClient` / server plumbing.
  4. **Docs + version.** README rewrite, protocol reference doc, architecture
     section + diagram; `webtools-udp` → `2.0.0`.
- **This design doc** moves onto the integration branch with PR 1 and is promoted
  to (or paired with) the implementation plan, so `git log --follow` binds design
  to code.
- **Commit trailers:** follow the repo convention — **no AI attribution**
  (no `Co-Authored-By` / `Claude-Session` / "Generated with" lines), per the
  recorded project standing instruction.
- **Do not** fold any unrelated working-tree change into these commits (none
  pending today).

---

## 13. Decisions (resolved 2026-09-09 — maintainer accepted every recommendation)

| # | Decision | Resolution |
|---|---|---|
| **D1** | **Take it on, and in what form?** | **RESOLVED — yes, as `webtools-udp` `2.0.0`**, not a new `webtools-udp-reliable` artifact (§2, §4). Later extraction stays an option; no preemptive split. |
| **D2** | **API shape:** channel handle `connection.channel(RELIABLE_ORDERED)` (Option A) vs typed `sendReliable`/`actuateReliable` methods (Option B). | **RESOLVED — Option A**, the channel handle. Accept the extra `UdpChannel` + `DeliveryMode` surface; the clean-break policy makes a second break to reach the durable multi-channel shape too costly. |
| **D3** | **RTT estimator:** private RTO estimator for the reliable channel vs feed/read the shared `LinkQualityTracker`. | **RESOLVED — private** estimator using the `Rtt` pure functions for v1; unify later if the reliable channel needs to publish stats. |
| **D4** | **Sequence number width:** `uint16` (+ RFC 1982 arithmetic) vs `uint32`. | **RESOLVED — `uint16`** with RFC 1982 serial-number arithmetic. |
| **D5** | **Reliable message size cap:** default and opt-in hard max. | **RESOLVED — default 1024 B**; configurable hard max **~8 KB**; oversize → typed failure. |
| **D6** | **In-flight window full:** immediate `Result.failure` backpressure vs a bounded internal queue that absorbs short bursts first. | **RESOLVED — immediate `Result.failure`** (`ReliableWindowFullException`). A small bounded queue stays a permitted refinement if testing shows it is needed. |
| **D7** | **Does a reliable channel survive a NAT-rebind supersede** (session resume token)? | **RESOLVED — no for v1**: fresh registration = fresh channel. Revisited with Issue #10 session-resume work. |
| **D8** | **Release path:** staged `2.0.0-alphaN` series on an integration branch vs one `2.0.0` PR. | **RESOLVED — staged `2.0.0-alphaN` series** (§12). |
| **D9** | **Ship v1 with no congestion control?** | **RESOLVED — yes, ship without it**. The in-flight window + RTO backoff bound the damage; congestion control is follow-up #1. |
| **D10** | **Interim guidance:** a documented app-level ack/retransmit pattern for consumers who need reliability before `2.0.0`? | **RESOLVED — yes**, a short README snippet / note. |

---

## 14. Sequencing & follow-ups

**Immediate:** D1–D10 are resolved (above). Next step is an implementation plan
(`docs/issue-14-reliable-ordered-channel-plan.md`) turning §5–§9 into a
file-by-file design and a 5-level test matrix, then the staged `2.0.0-alphaN`
series of §12.

**Deliberately left for after v1**, roughly in priority order:

1. **Congestion control** — a TCP-friendly / AIMD or QUIC-style controller. The
   first thing a real congested path will need.
2. **Fragmentation** of oversize reliable messages (per-message acks over
   fragments, bounded reassembly).
3. **Multiple reliable channels** — promote the v1 channel-0 into an arbitrary
   channel id; this is where Option A (D2) pays off.
4. **Unreliable-sequenced** / newest-wins-with-discard mode for the snapshot path
   (sequence the unreliable datagrams, drop stale on arrival).
5. **Reliable-channel session resume** across a NAT rebind (with Issue #10).
6. **Priority / partial reliability** — pick the N most important queued messages
   per send opportunity (the Gaffer prioritization model), per-message TTL.
