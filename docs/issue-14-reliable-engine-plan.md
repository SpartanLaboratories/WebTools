# Issue #14, Stage 2 — internal reliable-ordered engine for `webtools-udp` 2.0.0

## Header / Association

- **Covers:** `SpartanLaboratories/WebTools#14` — *"Design discussion: a
  reliable-ordered sub-channel over the UDP transport"* (labels: `enhancement`,
  `question`). The maintainer resolved every design decision D1–D10 on
  2026-09-09 (design doc §13) and accepted the feature as the headline of
  `webtools-udp` **`2.0.0`**.
- **What this document is:** the **Stage 2** implementation plan of the staged
  `2.0.0-alphaN` series (design doc §12 / Stage-1 plan §11) — the internal
  reliable-ordered engine, entirely behind an `internal` surface, with **no
  public API and no socket wiring**. Stage 1 (the 1-byte datagram-type framing
  prefix) is merged; Stage 3 (public channel API + socket plumbing) and
  Stage 4 (docs + version finalisation) are not designed here — see §11.
- **Approved design:** `docs/issue-14-reliable-ordered-channel-design.md`
  §6–§9, §13. This plan turns those sections into a file-by-file change set
  and a 5-level test matrix for the reliable-engine slice specifically.
- **Baseline:** `webtools-udp` `2.0.0-alpha1` on `feat/2.0-framed-transport`
  (Stage 1 merged as `5628fd8`). Working tree clean at planning time.
- **Branch model:** the long-lived `2.x` integration branch
  `feat/2.0-framed-transport` (off `master`); this stage lands on a Stage-2
  branch `feat/issue-14-reliable-engine` off the integration branch, via the
  third PR of the series.
- **This plan document** is committed in the same commit as the first Stage-2
  implementation commit, so `git log --follow` binds plan to code.
- **Branch:** `feat/issue-14-reliable-engine` (off `feat/2.0-framed-transport`,
  deleted after merge).
- **Commits:** `Commit: b04238a` (feat) / `Commit: 3cfbc5f` (test) / `Commit: 6a1b064` (build).
- **PR:** `PR: #32`, merged into `feat/2.0-framed-transport` as `3519276`.
- **Status:** done — on `feat/2.0-framed-transport` as `webtools-udp` 2.0.0-alpha2,
  not published to Maven Central; Issue #14 stays open for Stages 3-4.
- **Target version:** `webtools-udp` `2.0.0-alpha1` → **`2.0.0-alpha2`** (§8).
  Purely additive at the wire level (`0xA0`/`0xA1` promoted from reserved to
  live tags) and purely additive at the source level (new `internal` types
  only); no public API changes this stage. The Maven Central **publish** of
  `2.0.0-alpha2` stays a separate maintainer-gated step, per the Stage-1 OD-3
  precedent.
- **Related docs:**
  - `docs/issue-14-reliable-ordered-channel-design.md` §6–§9, §11, §13 — the
    accepted design this plan implements.
  - `docs/issue-14-reliable-ordered-channel-plan.md` — the Stage-1 plan; the
    structural template for this document and the source of the "0xA0/0xA1
    reserved" wire contract this stage promotes to live.
  - `docs/issue-13-link-quality-probe-plan.md` — the RFC 6298 SRTT/RTTVAR
    estimator (`Rtt`, `LinkQualityTracker`) this stage's private
    `ReliableRtoEstimator` reuses for its pure math, without sharing state.

---

## 1. Context

### 1.1 Requirements / acceptance criteria

From the design doc §6–§9 and the §12 PR-series definition of Stage 2:

1. **Promote `0xA0`/`0xA1` from reserved to live `DatagramType` entries**
   (`RELIABLE_DATA` / `RELIABLE_ACK`), reserved by Stage 1 specifically for
   this purpose.
2. **A reliable-channel wire codec** (`ReliableWireFormat`): the 10-byte
   `0xA0` reliable-data header + payload, the 8-byte `0xA1` standalone-ack
   frame — both carrying a `uint16` seq/ack pair and a `uint32` selective-ack
   bitfield, big-endian, mirroring `TransportWireFormat`'s own probe-sequence
   codec.
3. **RFC 1982 serial-number arithmetic** (`SerialSequence`) over the 16-bit
   sequence space (design D4), so seq/ack comparison and window membership are
   correct across the `65535 -> 0` wraparound.
4. **A private per-channel RTO estimator** (`ReliableRtoEstimator`, design D3)
   built on the Issue #13 `Rtt` pure functions — never the shared
   `LinkQualityTracker` — with Karn's algorithm enforced by its caller.
5. **A send-side retransmit sequence buffer with a fixed in-flight window**
   (`ReliableRetransmitBuffer`), ring capacity unified with the window
   (design D6/OD-3) so a slot can never be overwritten while its occupant is
   still live.
6. **A bounded receive-side reorder buffer** (`ReliableReorderBuffer`): an
   in-order delivery cursor plus a bounded out-of-order store, so receive-side
   memory is bounded even under pathological reordering.
7. **An orchestrator** (`ReliableChannelEngine`) wiring the above three
   together behind `sendReliable` / `onInboundDatagram` / `onRetransmitTick`,
   shaped so a later stage can hand `engine::onRetransmitTick` straight to a
   `PeriodicSchedule` — this stage never creates that executor itself.
8. **Zero public API surface, zero socket wiring.** Every new type is
   `internal`. `UDPConnection`, `MultiConnectionUDPClient`'s public surface,
   `MultiConnectionUDPServer`, and the `Connection` interface are untouched
   apart from the minimal compile-preservation patch below.
9. **Minimal, capability-free patch to keep the build green.** Promoting
   `0xA0`/`0xA1` to live entries makes `HandshakeCoordinator.classify`'s
   exhaustive `when` over `DatagramType` non-exhaustive; `MultiConnectionUDPClient
   .receiveLoop`'s equivalent `when` (statement position) still compiles but
   would silently swallow a diagnostic log line for the newly-live tags. Both
   need a one-branch patch that WARN/DEBUG-drops the tag exactly as a reserved
   tag did in `alpha1` — adding no new production behaviour.
10. **Out of Stage 2 (Stage 3):** the public `DeliveryMode`/`UdpChannel` API,
    the reliable message-size cap and its typed exceptions, the retransmit
    executor's real wiring into `UDPConnection`/`MultiConnectionUDPClient`/
    `MultiConnectionUDPServer`. Congestion control is out of scope permanently
    for v1 (D9).

### 1.2 The exhaustive-`when` compile-break finding

Not a defect in Stage 1 — a direct, foreseeable consequence of it. Stage 1
deliberately reserved `0xA0`/`0xA1` in the `DatagramType` value space
specifically so Stage 2 could promote them with "no further wire break"
(Stage-1 plan §3.1). What it did *not* anticipate is that
`HandshakeCoordinator.classify`'s dispatch is an exhaustive `return when
(DatagramType.ofTagByte(...)) { ... }` with no `else` branch
(`HandshakeCoordinator.kt:138`, confirmed against the merged Stage-1 code) —
so the moment `DatagramType` gains two new enum entries, that `when` stops
compiling. `MultiConnectionUDPClient.receiveLoop`'s equivalent `when`
(`MultiConnectionUDPClient.kt:337`) is in **statement** position, not
expression position, so it remains syntactically exhaustive-optional and still
compiles — but silently drops the existing debug-log branch for the two new
tags if left unpatched, which would be a behavioural regression (today's WARN
for a reserved tag would silently vanish for `0xA0`/`0xA1` specifically).

**Resolution:** §4.9 adds one minimal branch to each `when`, identical in
observable behaviour to what a still-reserved tag (`0x83`+, `0xA2`–`0xAF`)
already gets — a WARN (server) / DEBUG (client) drop, nothing more. This is
the *only* reason any file outside the new `internal` types is touched this
stage.

### 1.3 The resolved ack-semantics ambiguity

The design doc's §6.2/§6.3 describes the `ack` field's prose as "highest
in-order seq received" but defines the accompanying bitfield as "bit `n` set
⇒ `(ack − n − 1)` also received" — a definition that is only meaningful if
`ack` is **not** strictly in-order (if it were, everything below it would
already be implied by "in-order," and the bitfield would be redundant). This
is the classic Gaffer/Fiedler selective-ack scheme the design doc cites by
name (*Reliable Ordered Messages*), and that scheme's `ack` is explicitly the
highest sequence received **at all**, gaps allowed.

**Resolved before implementation:** `ack` = the highest sequence received at
all (gaps allowed, not necessarily in order); in-order delivery is tracked
**separately**, by the reorder buffer's own delivery cursor. This governs
`ReliableReorderBuffer.ackAndBitfield()` (§4.7) and
`ReliableRetransmitBuffer.onAck` (§4.6): an ack covers an exact match on `ack`
itself, plus every set bit `n`'s implied seq `ack − n − 1`, independent of
whether those seqs were contiguous.

---

## 2. Prior art & best practices

Reused directly from the accepted design doc (§3, §7), which already surveyed
the reliable-UDP landscape:

- **Gaffer/Fiedler selective-ack + sequence buffer** (*Reliable Ordered
  Messages*, `gafferongames.com`) — the `(ack, 32-bit bitfield)` scheme and
  the ring-indexed sequence buffer this stage's `ReliableRetransmitBuffer` /
  `ReliableReorderBuffer` implement directly, including the documented
  stale-ring-slot false-ack failure mode this stage's ring/window unification
  structurally avoids (§4.6).
- **RFC 6298** (TCP's RTO computation) — `SRTT + K·RTTVAR`, `K = 4`, reused
  verbatim as `Rtt.rtoMillis` (§4.2), plus Karn's algorithm (skip the RTT
  sample for a retransmitted message) and RFC 6298 §5.5 per-message backoff
  (double the RTO for *that* message on repeated timeout).
- **RFC 1982** (serial number arithmetic) — the wraparound-safe comparison
  this stage's `SerialSequence` implements for the 16-bit seq/ack space
  (design D4), including the documented antipodal case.

---

## 3. Design

This stage's design is the accepted design doc §6.2 (wire layout), §6.3
(acks), §7.1–§7.5 (the reliability mechanism), and §7.8 (the ack/retransmit
flow), narrowed to exactly what an `internal`, socket-free engine needs. No
design decision was left open for this plan to make: D3, D4, D6, and OD-2/OD-3
(§10) were all resolved before implementation began.

### 3.1 Reliable header layout

```
0xA0 reliable data - 10-byte header + payload:
 byte 0        : tag (0xA0)
 byte 1        : channel id            (this stage: always 0x00)
 bytes 2-3     : seq        (uint16, big-endian)
 bytes 4-5     : ack        (uint16, big-endian)
 bytes 6-9     : ack bitfield (uint32, big-endian; bit n set => (ack - n - 1) also received)
 bytes 10..    : payload

0xA1 standalone ack - exactly 8 bytes, no seq, no payload:
 byte 0        : tag (0xA1)
 byte 1        : channel id
 bytes 2-3     : ack        (uint16, big-endian)
 bytes 4-7     : ack bitfield (uint32, big-endian)
```

Reliable-data overhead is 10 bytes; ack-only overhead is 8 bytes — both
`< 1%` of the ~1200-byte path-MTU advisory (design §6.2).

### 3.2 The RFC 1982 sequence space

One reliable channel ⇒ two independent 16-bit sequence spaces per connection
(one per direction). `SerialSequence` provides `wrap`, `add`, `compare`,
`lessThan`, and `inWindow` — every seq/ack comparison in the engine goes
through this object, never raw integer comparison, so ordering stays correct
across the `65535 -> 0` boundary. The antipodal case (`|a − b| == 32768`) is
RFC-1982-undefined; `SerialSequence.compare` resolves it deterministically
(not by throwing) but arbitrarily — documented, not defended against, since
every window this module uses (default 256) is a few hundred at most, far
below the 32768 antipodal distance.

A related, initially-unhandled edge case surfaced during implementation and
testing, not in the design doc: **`cursor == 0` is ambiguous** between "a
fresh reorder buffer that has never delivered anything" and "a buffer that has
genuinely wrapped all the way around the 16-bit space." Naive
`SerialSequence.lessThan(candidate, cursor)` reads every not-yet-sent seq near
the top of the space (e.g. `65534`) as "before" a pristine `cursor == 0`,
which corrupted the documented `(0xFFFF, 0)` pre-first-receipt sentinel into
`(0xFFFF, -1)` (every bit spuriously set) — caught by
`ReliableReorderBufferTest`'s sentinel and `ReliableChannelEngineTest`'s
standalone-ack tests during implementation. **Fix:** `ReliableReorderBuffer`
tracks a `deliveredCount: Long` (how many messages have actually been
delivered, ever) and only treats a candidate seq as "already delivered" if it
is both behind `cursor` *and* within `deliveredCount` steps of it — so a
never-sent seq near the wraparound boundary cannot masquerade as delivered
history before any traffic has flowed.

### 3.3 RTO — a private estimator, Karn's algorithm, per-message backoff

`ReliableRtoEstimator` wraps the Issue #13 `Rtt` pure functions
(`smoothedRttMillis`, `smoothedRttVarianceMillis`, and the new `rtoMillis`) in
its own `@Synchronized` state, seeded on the first sample per RFC 6298's
first-measurement rule (`SRTT = sample`, `RTTVAR = sample / 2`), floored
before any sample exists. This is deliberately **not** the shared
`LinkQualityTracker` (design D3, resolved: private for v1) — the reliable
channel's RTT state is its own, so a channel with no active probe still gets
a working RTO, and the probe's RTT estimate is never polluted by retransmit
noise.

Karn's algorithm is enforced by the *caller*, `ReliableRetransmitBuffer.onAck`:
an acked entry that was **never** retransmitted yields an RTT sample
(`now - firstSentAtNanos`); a retransmitted entry is still removed from the
buffer (it *was* delivered) but produces no sample, since its RTT is
ambiguous — the ack could be for the original or the retransmit.

Per-message backoff (RFC 6298 §5.5) lives on each buffer entry independently:
`ReliableRetransmitBuffer.dueForRetransmit` doubles *that entry's* own
`rtoMillis` (capped at `rtoCapMillis`) every time it fires again, without
touching the shared SRTT/RTTVAR estimate — a black-holed single message backs
off on its own; the estimator's baseline RTO for *new* sends is unaffected.

### 3.4 Send side — the retransmit buffer and the in-flight window

`ReliableRetransmitBuffer.offer` assigns the next seq (via `SerialSequence`)
and puts the message in flight, refusing (`OfferResult.WindowFull`) once
`windowSize` messages are already outstanding. The ring capacity **is** the
window size — not a separate, larger allocation — which is what makes the
classic stale-ring-slot false-ack bug (design §11, *Reliable Ordered
Messages*) structurally unreachable here: a slot can never be reused while its
occupant is still live, because `offer` refuses before that could happen.
There is no separate defensive "clear entries between the previous and new
highest insert seq" pass, because the invariant is enforced at the point of
insertion rather than patched after the fact.

`onAck(ack, ackBitfield)` removes every entry the pair covers under the §1.3
ack semantics (exact match on `ack`, plus each set bit `n`'s `ack − n − 1`)
and returns one `AckedSample` per removed entry that was never retransmitted.

### 3.5 Receive side — the bounded reorder buffer

`ReliableReorderBuffer.onReceive` derives, never double-stores, "received"
status: a seq counts as received if it is provably already delivered
(§3.2's `deliveredCount`-guarded check) or if the ring slot it maps to still
holds exactly that seq. That slot-equality check is what makes a stale/reused
ring slot read as "not received" — a safe under-report the sender's retransmit
recovers from — rather than a false positive that would corrupt the ack.

An in-order arrival (`seq == cursor`) is delivered immediately and the cursor
drains any now-contiguous buffered successors in the same call. An
out-of-order arrival within the window is buffered. A duplicate (already
delivered or already buffered) is dropped with no state change. A seq beyond
`[cursor, cursor + windowSize)` is `OutOfWindow` — dropped, relying on the
sender's own RTO-driven retransmit once the window admits it, exactly per
design §7.4.

`ackAndBitfield()` derives `(ack, ackBitfield)` fresh on every call from
`cursor` and the ring's current contents — never stored separately — and
always returns a value: before the first `onReceive`, `cursor == 0` and the
ring is empty, which (after the §3.2 fix) naturally yields the documented
sentinel `(0xFFFF, 0)` with no special-cased branch.

### 3.6 The orchestrator

`ReliableChannelEngine` is the sole entry point into the two buffers and the
estimator — lock order is always engine → buffer, never reversed, so there is
no deadlock risk despite three logical callers (an app thread via
`sendReliable`, a listener thread via `onInboundDatagram`, a retransmit-tick
thread via `onRetransmitTick`) touching shared state, mirroring
`LinkQualityTracker`'s `@Synchronized` discipline throughout.

- `sendReliable`: offers into the retransmit buffer at the estimator's current
  RTO; on acceptance, sends a fresh `0xA0` frame immediately, piggybacking the
  current ack/bitfield for the reverse direction. A `sendRaw` failure here is
  logged and swallowed — the entry is already buffered, so the next
  retransmit tick retries it; the caller does not need to distinguish "sent"
  from "buffered, will retry."
- `onInboundDatagram`: ack/bitfield processing (forwarding never-retransmitted
  samples to the RTO estimator) always runs first, regardless of tag, then
  `RELIABLE_DATA` additionally feeds the payload to the reorder buffer.
  Malformed or unrecognized input is WARN-logged and yields an empty list —
  never throws.
- `onRetransmitTick`: resends every due entry, piggybacking the current ack;
  if nothing was due and there is something to acknowledge (the ack/bitfield
  pair is not the pre-first-receipt sentinel), sends one standalone `0xA1`
  instead — the standalone-ack timer coalesced onto the retransmit tick
  (§10 OD-2), rather than a second, faster ack-delay timer. Shaped as
  `() -> Unit` precisely so a later stage can hand `engine::onRetransmitTick`
  straight to `PeriodicSchedule.schedule(key, intervalMillis, tick)` — this
  stage never creates that executor itself, and no such call was added to
  `HandshakeCoordinator` or `MultiConnectionUDPClient`.
- `close`: discards every in-flight and buffered message (design §7.7 —
  teardown drops un-acked reliable data, no lingering, no TIME_WAIT
  equivalent).

### 3.7 Alternatives considered

| Option | Rejected because |
|---|---|
| **Extend `TransportWireFormat` with the reliable codec** instead of a separate `ReliableWireFormat` | Would couple Stage 3's dispatch-wiring diff to a file `TransportWireFormat` currently only documents *listener* behaviour for reserved tags on; the `Rtt.kt`-beside-`LinkQualityTracker.kt` precedent favours a sibling object instead. |
| **Share the connection's `LinkQualityTracker` for reliable-channel RTT** (design D3 option b) | Couples two independently-evolving estimators and promotes probe-only internals; a private estimator is simpler for v1 and can be unified later if the reliable channel needs to publish its own stats. |
| **A separately-configurable send window and receive window** (design §7.5 vs §7.4) | Splits into two knobs for no v1 benefit and reopens the stale-ring-slot bug class the unified window/ring capacity structurally closes (§3.4). Kept as OD-3, revisit only if real tuning needs it. |
| **A dedicated, faster standalone-ack timer** distinct from the retransmit tick | A second `PeriodicSchedule` role to design and wire for marginal latency benefit on an idle reverse path; coalescing onto the existing tick (OD-2) is simpler and sufficient for v1. |
| **`uint32` sequence numbers** (design D4 alternative) | Removes the wraparound question entirely, but 65536 in-flight identifiers is enormous headroom for a windowed, low-rate discrete-event stream; the `uint16` + RFC 1982 arithmetic keeps the header 2 bytes narrower per field. |

---

## 4. File-by-file changes

All paths under `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/`
unless noted.

### 4.1 `DatagramType.kt` (existing, modified — stays public)

`RELIABLE_DATA(0xA0.toByte())` / `RELIABLE_ACK(0xA1.toByte())` added as live
entries, right after `UNRELIABLE`. The class KDoc's value-space table splits
the old `0xA0`–`0xAF reserved` row into `0xA0 RELIABLE_DATA` / `0xA1
RELIABLE_ACK` / `0xA2`–`0xAF reserved`, mirroring how Stage 1 documented
`0x90`. `ofTagByte`'s KDoc reserved-range list updated to match.

### 4.2 `Rtt.kt` (existing, modified — stays `internal object`)

New pure function, reusing the pure-math home rather than inventing a second
one: `fun rtoMillis(srttMillis: Double, rttVarMillis: Double, floorMillis:
Long, capMillis: Long): Double = (srttMillis + 4.0 * rttVarMillis)
.coerceIn(floorMillis.toDouble(), capMillis.toDouble())` — RFC 6298 §2,
`K = 4`, no separate clock-granularity term (the configurable floor already
covers "RTO too small," including deliberately going below the RFC's 1 s
default). `coerceIn`'s own contract makes `floorMillis > capMillis` throw
`IllegalArgumentException` rather than silently resolving to a nonsensical
range — locked as an explicit deterministic-test case (§6), not smoothed
over.

### 4.3 `SerialSequence.kt` (new, `internal object`)

RFC 1982 arithmetic over the 16-bit space: `wrap`, `add`, `compare`,
`lessThan`, `inWindow`. Stateless. See §3.2 for the antipodal-case and
`cursor == 0` ambiguity notes.

### 4.4 `ReliableWireFormat.kt` (new, `internal object`)

The reliable header/ack codec (§3.1): `reliableDataDatagram`,
`reliableAckDatagram`, `reliableDataHeaderOf`, `reliablePayloadOf`,
`reliableAckHeaderOf`, plus the `ReliableDataHeader`/`ReliableAckHeader` data
classes and the `DEFAULT_RELIABLE_CHANNEL` constant. Big-endian encode/decode
mirrors `TransportWireFormat`'s probe-sequence codec exactly, at 2-/4-byte
widths instead of 8. Stateless. One KDoc cross-reference line added to
`TransportWireFormat.kt`'s class doc pointing here for the `0xA0`/`0xA1`
frames its own value-space table reserves — no code change there.

### 4.5 `ReliableRtoEstimator.kt` (new, `internal class`)

`onRttSample` / `currentRtoMillis`, `@Synchronized` throughout, built on
`Rtt`'s pure functions (§3.3). Private per connection's reliable channel —
never shared with `LinkQualityTracker`.

### 4.6 `ReliableRetransmitBuffer.kt` (new, `internal class`)

`offer` / `onAck` / `dueForRetransmit` / `clear`, `@Synchronized` throughout
(§3.4). Ring capacity unified with `windowSize`.

### 4.7 `ReliableReorderBuffer.kt` (new, `internal class`)

`onReceive` / `ackAndBitfield` / `clear`, `@Synchronized` throughout (§3.5),
including the `deliveredCount` guard from §3.2.

### 4.8 `ReliableChannelEngine.kt` (new, `internal class`)

The orchestrator: `sendReliable` / `onInboundDatagram` / `onRetransmitTick` /
`close` (§3.6).

### 4.9 Minimal compile/behaviour-preservation patch (required, not optional)

- `HandshakeCoordinator.classify` (`HandshakeCoordinator.kt`): one new branch,
  right after `UNRELIABLE`, handling `DatagramType.RELIABLE_DATA,
  DatagramType.RELIABLE_ACK` identically to a still-reserved tag — a WARN
  ("Unhandled datagram type 0x{}...") and `Result.success(Unit)`. Verified by
  `FramingComponentTest`'s existing "a reserved `0xA0` inbound is dropped with
  a WARN" test, which keeps passing unmodified (the WARN text and behaviour
  are byte-identical; only the code path that produces it changed, from the
  `null` fallback to an explicit branch).
- `MultiConnectionUDPClient.receiveLoop` (`MultiConnectionUDPClient.kt`):
  mirrored for symmetry and to avoid silently losing the existing debug-log
  branch — `DatagramType.RELIABLE_DATA, DatagramType.RELIABLE_ACK ->
  log.debug("Unhandled datagram type 0x{} from server, dropping", ...)`.

No other production file references `DatagramType`, and no `PeriodicSchedule
.schedule(...)` call was added to either file — confirmed by inspecting the
diff to these two files, which is otherwise a one-branch addition each.

### 4.10 `webtools-udp/build.gradle.kts`

Version-comment block appended after the `2.0.0-alpha1` block, describing the
Stage-2 addition (§9). The `version = "2.0.0-alpha1"` line itself is
**unchanged** by this plan/implementation — the bump to `2.0.0-alpha2` is a
separate follow-up step, not part of this stage's scope as directed.

### 4.11 `docs/issue-14-reliable-engine-plan.md`

This document.

---

## 5. Documentation impact (Audience-Reach rings)

| Ring | Touched? | What moves with this change |
|---|---|---|
| **Inner core** (in-editor) | yes | Comments: why `ReliableWireFormat` is a sibling object rather than an extension of `TransportWireFormat`; the ring/window-unification rationale in `ReliableRetransmitBuffer`; the derived-not-stored "received" status and the `deliveredCount` wraparound guard in `ReliableReorderBuffer`; the lock-order (engine → buffer) note in `ReliableChannelEngine`; the one-branch compile-preservation patches in `HandshakeCoordinator`/`MultiConnectionUDPClient` explaining they add no capability. |
| **Component ring** (KDoc) | yes | New Level-2 KDoc on every public/internal member of `SerialSequence`, `ReliableWireFormat`, `ReliableRtoEstimator`, `ReliableRetransmitBuffer`, `ReliableReorderBuffer`, `ReliableChannelEngine` — signatures, `@param`/`@return`, and the behavioural contracts (ack semantics, Karn's algorithm, RFC 1982 antipodal case, the sentinel ack value). `DatagramType`'s KDoc table updated for the two newly-live entries. |
| **Boundary ring** (protocol) | **no new boundary surface this stage.** | The `0xA0`/`0xA1` wire layout is documented (§3.1) but not yet exercised by any socket-facing code — Stage 3 is where this becomes a live boundary contract a peer actually exchanges. This stage's engine is exercised only by its own tests. |
| **Architectural outer layer** | no | No new thread, no new lifecycle, no socket wiring. The engine is inert until Stage 3 wires it in. |
| **README** | no | Per the README-currency rule: a bug fix or internal change with no external shape change needs no README edit. This stage adds zero public API and zero production behaviour beyond the two WARN/DEBUG-preserving branches (§4.9), so nothing user-facing changed. |

---

## 6. Test plan (5-level hierarchy)

Package `com.spartanlabs.testing.<level>.webtools.udp`, one class per file,
`@Tag("<level>")`. Stateful classes (a mutable clock/cursor/ring) get no
gating/deterministic file, matching `LinkQualityTracker`'s precedent —
component-only for anything with mutable state.

| Level | File | Covers |
|---|---|---|
| **Gating** | `DatagramTypeGatingTest.kt` (updated) | the six live tags incl. `RELIABLE_DATA`/`RELIABLE_ACK`; `ofTagByte` resolves them; a still-reserved tag (`0xA2`) stays `null` |
| **Gating** | `RttGatingTest.kt` (updated) | `rtoMillis` floor- and cap-dominated smoke cases |
| **Gating** | `SerialSequenceGatingTest.kt` (new) | basic `compare`/`add`/`inWindow` sanity |
| **Gating** | `ReliableWireFormatGatingTest.kt` (new) | data/ack datagram tag bytes + a basic header round-trip |
| **Component** | `ReliableRtoEstimatorTest.kt` (new) | pre-sample floor, first-sample RFC 6298 seeding, EWMA convergence, floor/cap clamping |
| **Component** | `ReliableRetransmitBufferTest.kt` (new, fake clock) | seq assignment, `WindowFull`, exact-match + bitfield-covered ack removal, **Karn's algorithm**, retransmit-due + per-entry backoff (incl. cap), `clear()` |
| **Component** | `ReliableReorderBufferTest.kt` (new) | in-order deliver+drain, buffered-ahead, duplicate-of-delivered, duplicate-of-buffered, out-of-window, sentinel + populated `ackAndBitfield()`, `clear()` |
| **Component** | `ReliableChannelEngineTest.kt` (new, fake clock + recording `sendRaw`, `LogCapture`) | two engines wired via each other's `onInboundDatagram`: round trip, **standalone-ack-on-next-tick**, no premature standalone ack pre-first-receipt, out-of-order delivery, duplicate dedupe, `WindowFull`, retry-after-send-failure, malformed input never throws + is WARN-logged |
| **Integration** | `ReliableChannelEngineRetransmitSchedulingTest.kt` (new, real `PeriodicScheduler`) | `engine::onRetransmitTick` fires on real wall-clock cadence; `shutdown()` leaves no live thread of the configured name — no socket, no production wiring touched |
| **Deterministic** | `SerialSequenceTest.kt` (new) | wraparound at the `uint16` boundary (headline case, incl. an ordered walk across it), antipodal-case documented non-throwing behaviour, `inWindow` boundary + wraparound cases |
| **Deterministic** | `ReliableWireFormatTest.kt` (new) | boundary `seq`/`ack`/bitfield round-trips incl. the uint16 boundaries and a full 32-bit bitfield, big-endian byte-order assertion, exact 10-/8-byte overhead, every truncation length → `null` |
| **Deterministic** | `RttTest.kt` (updated) | `rtoMillis` table: formula-, floor-, and cap-dominated, exact clamp boundaries, `floor > cap` throws `IllegalArgumentException` |
| **Deterministic** | `DatagramTypeTest.kt` (updated — not originally listed, required by §1.2's own file-by-file change) | the exhaustive byte-to-entry mapping now includes `0xA0 RELIABLE_DATA`/`0xA1 RELIABLE_ACK` as live, not reserved |
| **E2E** | *(none)* | explicitly deferred to Stage 3 — no socket wiring exists yet to exercise |
| **Nonfunctional** | `ReliableEngineNonFunctionalTest.kt` (new) | frame-overhead check (10/8 bytes vs the path-MTU advisory), **burst-loss soak** (thousands of messages, seeded-lossy in-memory relay, fake clock, asserts eventual exactly-once in-order delivery), **bounded reorder-buffer memory** under thousands of pathologically-reordered far-ahead messages, 100k random garbage datagrams and every truncation length of `0xA0`/`0xA1` never throw |
| **UAT** | *(none)* | same reasoning as E2E — nothing socket-facing to evaluate yet |

Risk-scenario coverage (design doc §11, tied to concrete tests — see §7):
wraparound → `SerialSequenceTest`; duplicate/replay → `ReliableReorderBufferTest`
+ the nonfunctional soak; standalone-ack timer → `ReliableChannelEngineTest`;
Karn's algorithm → `ReliableRetransmitBufferTest`; burst-loss + bounded memory
→ `ReliableEngineNonFunctionalTest`.

---

## 7. Risks & edge cases

Tied to the accepted design doc's §11, with this stage's concrete mitigation:

- **Burst-loss ack edge cases** (old sequence-buffer entries surviving a wrap
  producing false acks). **Mitigated structurally**, not defensively: the
  retransmit buffer's ring capacity is unified with the in-flight window
  (§3.4), so a slot can never be reused while its occupant is still live —
  there is no wrap-survival case to produce a false ack from. Soak-tested
  under seeded synthetic loss in `ReliableEngineNonFunctionalTest`.
- **Sequence wraparound.** RFC 1982 arithmetic everywhere a seq is compared
  (`SerialSequence`); `SerialSequenceTest` targets the `uint16` boundary
  directly, including an ordered walk across it. The related `cursor == 0`
  ambiguity found during implementation (§3.2) is fixed with the
  `deliveredCount` guard and locked by the reorder-buffer sentinel tests.
- **Duplicate / replay.** The reorder buffer's delivery cursor plus the
  slot-equality "received" check dedupe both an already-delivered and an
  already-buffered duplicate; covered by `ReliableReorderBufferTest` and
  exercised at scale by the soak test (a lossy relay naturally produces
  retransmit-driven duplicates).
- **Idle reverse path — the standalone-ack timer is not optional.** Covered
  by `ReliableChannelEngineTest`'s standalone-ack-on-next-tick case and the
  companion "no premature ack before any receipt" case (the latter is exactly
  what caught the §3.2 sentinel bug during implementation).
- **Head-of-line blocking.** Inherent to reliable-ordered delivery; this
  stage does not mitigate it (nor does the design ask it to) — documented in
  the class KDoc as a reason application snapshots should stay on the
  unreliable path. No code path in this stage's engine offers an escape
  hatch; that is a Stage-3/application concern.
- **Dispatch-executor / threading coupling.** Not yet applicable — this
  stage's engine has no dispatch executor of its own; `onInboundDatagram`
  runs synchronously wherever the (not-yet-existing) Stage-3 caller invokes
  it. The `@Synchronized`, engine-is-sole-entry-point discipline (§3.6) is in
  place ahead of that wiring so Stage 3 does not have to retrofit it.
- **Interaction with idle detection / supersede.** Not yet applicable —
  the engine has no connection-lifecycle awareness this stage; `close()`
  exists and discards un-acked state (design §7.7) but nothing calls it yet.
  Stage 3's job.
- **Performance.** Bounded per design: one sequence-buffer slot per in-flight
  message, RTO-paced retransmits, one bounded ring on the receive side. The
  nonfunctional soak exercises thousands of messages under 30% synthetic loss
  and asserts convergence within a generous iteration bound, without
  asserting a specific latency number (no wall clock is real in this stage's
  tests).
- **Zero production capability added.** The only production-file touches
  outside the new `internal` types are the two WARN/DEBUG-preserving
  branches (§4.9) — confirmed by inspecting the diff and by
  `FramingComponentTest`'s unmodified reserved-tag WARN test still passing.
- **Pre-existing working-tree changes:** none — the tree was clean at
  planning time (Stage-1 merge was the prior commit on this branch).

---

## 8. Version

**`webtools-udp` `2.0.0-alpha1` → `2.0.0-alpha2`.**

- Purely additive: two new live enum entries, one new pure function on an
  existing `internal` object, six new `internal` types, and two
  behaviour-preserving one-branch patches. No public API changed, no public
  API removed, no wire behaviour changed for any datagram a Stage-1 ↔ Stage-1
  session ever produces.
- **`-alpha2`** marks the second pre-release of the staged series on
  `feat/2.0-framed-transport` (design §12 D8); the `2.0.0` final is cut when
  the integration branch merges to `master` after Stage 4.
- The `version` line bump itself is **not** part of this stage's implementation
  commit set — it is a separate follow-up step, per the explicit scope given
  for this stage.
- Maven Central publish of `2.0.0-alpha2` stays a separate, explicitly
  maintainer-gated step (Stage-1 OD-3 precedent) — not part of this stage.

---

## 9. Version control

- **Integration branch:** `feat/2.0-framed-transport` (already exists, holds
  Stage 1).
- **Stage-2 branch:** `feat/issue-14-reliable-engine`, off
  `feat/2.0-framed-transport`.
- **Commit sequence** (mirrors the Stage-1 idiom — feat / test / build):
  1. `feat: add the internal reliable-ordered engine to webtools-udp (Issue #14, Stage 2)`
     — all new files (§4.3–§4.8), the `DatagramType.kt`/`Rtt.kt` changes
     (§4.1–§4.2), the §4.9 compile-preservation patch, the `build.gradle.kts`
     version-comment block (§4.10, not the version line), and this plan
     document (§4.11).
  2. `test: cover the internal reliable-ordered engine across all levels (Issue #14, Stage 2)`
     — all new/updated test files (§6).
  3. `build: bump webtools-udp to 2.0.0-alpha2 for the internal reliable engine (Issue #14, Stage 2)`
     — the `version` line only — **left for a later step**, not part of this
     implementation pass.
- **Commit trailer:** none — no `Co-Authored-By` / `Claude-Session` /
  "Generated with" line on commits, per the recorded project standing
  instruction.
- Do not commit, push, or open the PR until asked. Backfill this doc's
  `Commit:` / `PR:` fields once they exist, matching the #8–#13 / Stage-1
  convention.

---

## 10. Decisions

Carried over from the plan accepted before implementation — both already
resolved, not reopened here:

- **OD-2 — standalone-ack timing.** Coalesced onto the retransmit tick
  (simplest for Stage 2/3 v1, no second `PeriodicSchedule` role to design
  around) rather than a dedicated faster ack-delay timer. Revisit only if
  real usage shows the added latency on an idle reverse path matters.
- **OD-3 — window sizing.** The send-side in-flight window and the
  receive-side reorder window share one `windowSize` value (default 256), not
  independently configurable. Unifying them is what structurally prevents the
  stale-ring-slot bug (§3.4/§7). Split later only if real tuning needs it.

Also resolved during implementation, not left open (see §1.3, §3.2):

- **Ack semantics.** `ack` = highest sequence received at all (gaps allowed),
  not strictly in-order; in-order delivery is a separate concern owned by the
  reorder buffer's cursor.
- **The `cursor == 0` wraparound ambiguity** (found during implementation,
  not in the original design doc). Resolved with the `deliveredCount` guard
  in `ReliableReorderBuffer` (§3.2) — a locally-consistent fix within the
  already-resolved RFC 1982 design, not a new open decision requiring
  maintainer sign-off.

---

## 11. Sequencing & follow-ups

**This stage (PR 2 of the design §12 series):** the internal reliable engine
only — no public API, no socket wiring. Green at every applicable test level
(gating, component, integration, deterministic, nonfunctional; e2e/uat
explicitly deferred) before Stage 3 opens.

**Next — Stage 3 (public channel API), its own `docs/issue-14-*` plan:**
`DeliveryMode` + `UdpChannel` (design D2 Option A); `connection.channel
(RELIABLE_ORDERED)` / `channel(UNRELIABLE)`; the reliable message-size cap
(default 1024 B, hard max ~8 KB — design D5) and its typed errors
(`ReliableWindowFullException`, `ReliableMessageTooLargeException`);
`UDPConnection` / `MultiConnectionUDPClient` / `MultiConnectionUDPServer`
plumbing wiring `ReliableChannelEngine` in and creating the lazily-created
`mcup{c,s}-retransmit` `PeriodicScheduler` role `onRetransmitTick` was shaped
for. Publishes `2.0.0-alpha3`.

**Then — Stage 4 (docs + version finalisation):** README "reliable-ordered
channel" section; a `docs/` protocol reference; the architecture section +
the design §7.8 ack/retransmit sequence diagram; `webtools-udp` → `2.0.0`;
merge `feat/2.0-framed-transport` → `master`; close Issue #14.

**Deferred past `2.0.0` (design §14):** congestion control (follow-up #1),
fragmentation of oversize reliable messages, multiple reliable channels,
unreliable-sequenced mode, reliable-channel session resume across a NAT
rebind, priority / partial reliability.
