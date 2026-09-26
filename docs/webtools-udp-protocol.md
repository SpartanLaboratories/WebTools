# webtools-udp wire protocol

Applies to `webtools-udp` `2.x` — wire protocol version `2`. This is a
descriptive reference, not an RFC-2119 normative specification: it states
what the code does, including its as-built quirks, and marks tuning values
as informative where they are not part of the contract. Code
(`DatagramType`, `TransportWireFormat`, `ReliableWireFormat`) is authoritative
on any conflict with this document. The threads, locks and lifecycles behind
these bytes are in [webtools-udp-architecture.md](webtools-udp-architecture.md).

History (decision records, not current fact):
[design](issue-14-reliable-ordered-channel-design.md) ·
[Stage 1 plan](issue-14-reliable-ordered-channel-plan.md) ·
[Stage 2 plan](issue-14-reliable-engine-plan.md) ·
[Stage 3 plan](issue-14-reliable-channel-api-plan.md) ·
[Stage 4 plan](issue-14-finalisation-plan.md) ·
[Issue #34 probe-cadence fix](issue-34-probe-cadence-architecture.md).

## 1. Scope & audience

This document is the complete, self-contained wire reference for
`webtools-udp` `2.x`; a reader should never need the design doc or the
source to understand the wire. Code wins on any conflict with what is
written here.

## 2. Conventions, transport & size limits

All multi-byte integers are big-endian (`TransportWireFormat.kt:78-149`,
`ReliableWireFormat.kt:13-29`). Byte-layout tables use `Offset | Size |
Field | Type | Meaning`; there are no ASCII bit diagrams, because every
field in this protocol is byte-aligned.

"Contract" vs "informative" — contract values (tags, layouts, byte order,
the version token, the 250 ms probe floor, the 1024/8192 message cap) are
stated plainly; tuning values (window, RTO floor/cap, retransmit tick/cap)
carry an explicit *Informative* marker and live only in §7.

**Transport & addressing.** Plain UDP. The server binds one common port,
`9998` (`MultiConnectionUDPServer.COMMON_LISTEN_PORT`,
`MultiConnectionUDPServer.kt:540`), and serves every client on it. The
client uses exactly one socket for the handshake and the whole session
(`MultiConnectionUDPClient.kt:16-29,194-195`). Every server datagram is
addressed to the client's **observed post-NAT source** address and port,
never to anything carried in a payload (`CommonChannel.kt:71,89-91`) —
which is what lets the session traverse NAT.

**Size limits.** One application message is one datagram — there is no
fragmentation at any layer of this protocol. The largest UDP payload over
IPv4 is 65507 bytes (`MultiConnectionUDPServer.MAX_UDP_PAYLOAD_BYTES`); every
receiver's buffer is `receiveBufferBytes`, 512..65507, default 65507, and a
datagram that fills a smaller buffer is delivered **truncated**, with a WARN.
Keep datagrams under the ~1200-byte path MTU for real-network use
(documentation advice, not enforced). Framing overhead on application data:
2 bytes for unreliable (`0x90` + channel), 10 bytes for reliable (`0xA0`
header). Control datagrams are small and fixed-size: a keepalive is 1 byte,
a probe 9, a standalone ack 8.

## 3. Handshake

Plain UTF-8 text, unframed.

A client sends `Iam <name>` or `Iam <name> <credential>`, tokens separated
by a single space, to the server's common port from the socket it will use
for the session. `name` must be non-empty and whitespace-free; `credential`
is optional, whitespace-free, opaque to the library, and travels in
cleartext (`HandshakeWireFormat.kt:14-22,59-73`).

The server trims the datagram text (`CommonChannel.kt:78`) and splits on a
single U+0020 (`HandshakeCoordinator.kt:202`). `parseHandshake` requires
`tokens[0] == "Iam"` and at least two tokens; `name = tokens[1]`;
`credential = tokens[2]` or `""`; extra tokens ignored at DEBUG
(`HandshakeProtocol.kt:55-69`, `HandshakeCoordinator.kt:218-221`).

**As-built quirk (current behaviour, not a guarantee):** consecutive spaces
produce empty tokens, so `Iam  x` registers name `""` with credential `x`;
the name is not validated beyond presence. This is an open defect, tracked
as [SpartanLaboratories/WebTools#38](https://github.com/SpartanLaboratories/WebTools/issues/38).
Described here only — this stage does not fix it.

Accepted reply: `REGISTERED 2` (`HandshakeWireFormat.kt:79`).
`registeredProtocolVersion` (`:91-99`) is strict: a bare `REGISTERED` → `1`;
exactly one canonical decimal token → that number; `02`, `2 3`, `x`, or a
trailing space → `null`. `isRegistered` holds only when that version is `2`.

Refusal: `REFUSED <reason>`, or a bare `REFUSED`; the reason is trimmed and
control/whitespace runs collapsed to one space (`HandshakeWireFormat.kt:117-120`).

Server handshake handling (`HandshakeCoordinator.kt:218-267`): an `Iam` from
an already-registered origin gets `REGISTERED 2` again with no `admit`; an
`Iam` from a new origin runs `admit` inline first — `Refused` replies
`REFUSED <reason>` and registers nothing; `Admitted` supersedes any
same-name registration under another origin (cancelling its schedules,
closing its engine, firing `onClientDisconnect(SUPERSEDED)`, `terminate()`ing
it), then registers the new origin, replies `REGISTERED 2`, and fires
`onClientConnect` inline.

Client `handshake()` is one-shot and blocking, default timeout 4000 ms
(`MultiConnectionUDPClient.kt:790`), no built-in retry. `REFUSED` →
`HandshakeRefusedException`; `REGISTERED 2` → success; bare `REGISTERED`, or
`REGISTERED n` with `n != 2` → `IncompatibleProtocolException(remote, local=2)`;
anything else → `IllegalStateException`.

## 4. Tag catalogue (byte 0)

| Byte 0        | Entry           | Plane    |
|---------------|-----------------|----------|
| `0x00`–`0x7F` | *(none)*        | handshake text only — not a valid post-handshake tag |
| `0x80`        | `KEEPALIVE`     | control  |
| `0x81`        | `PROBE_PING`    | control  |
| `0x82`        | `PROBE_PONG`    | control  |
| `0x83`–`0x8F` | *reserved*      | control — future transport control (MTU probe, graceful close, …) |
| `0x90`        | `UNRELIABLE`    | app data |
| `0x91`–`0x9F` | *reserved*      | app data — future unreliable variants (unreliable-sequenced, newest-wins) |
| `0xA0`        | `RELIABLE_DATA` | app data |
| `0xA1`        | `RELIABLE_ACK`  | app data |
| `0xA2`–`0xAF` | *reserved*      | app data — reliable-channel control (SACK ranges, window updates, channel open/close) |
| `0xB0`–`0xFF` | *reserved*      | unallocated |

Source: `DatagramType.kt:9-27`. Every live tag is `>= 0x80`, so it cannot
collide with the ASCII first byte of `Iam` (`0x49`) or
`REGISTERED`/`REFUSED` (`0x52`).

## 5. Per-datagram sections

### `0x80` KEEPALIVE

| Offset | Size | Field | Type | Meaning |
|---|---|---|---|---|
| 0 | 1 | tag | u8 | `0x80` |

No body. Either side may send it — the client's is authoritative for NAT
purposes; a server → client keepalive refreshes only cone NATs. Idle-aware:
sent once output has been idle for at least the interval (default 20 000 ms),
polled at a quarter of the interval clamped to 250–5000 ms (`KeepAlive.kt:10-44`)
— so it can fire up to one poll late. The receiver drops it at TRACE. With
idle detection on, **any** inbound datagram from a registered origin stamps
its liveness before classification (`HandshakeCoordinator.kt:138-145`).

### `0x81` PROBE_PING / `0x82` PROBE_PONG

| Offset | Size | Field | Type | Meaning |
|---|---|---|---|---|
| 0 | 1 | tag | u8 | `0x81` (ping) or `0x82` (pong) |
| 1 | 8 | seq | u64 (big-endian) | the prober's sequence number; a `0x82` echoes the `0x81`'s value verbatim |

9 bytes total, either direction. Opt-in; runs at an **exact** cadence
(`scheduleTick`, since Issue #34) — `intervalMillis` must be `>=
TransportWireFormat.MIN_PROBE_INTERVAL_MILLIS` (250 ms; a lower value is
**rejected** with `IllegalArgumentException`, not clamped); default 1000 ms.
The server answers a valid `0x81` only from a registered origin — an
unregistered one is dropped at DEBUG with no reply
(`HandshakeCoordinator.kt:155-166`); **the client answers every valid `0x81`
inline, with no origin check** (`MultiConnectionUDPClient.kt:524-528`) — part
of the client origin-screening gap tracked as
[#39](https://github.com/SpartanLaboratories/WebTools/issues/39); described
here only. RTT estimator constants (informative tuning, §7): loss horizon 3
intervals (`Rtt.LOSS_HORIZON_INTERVALS`); EWMA α = 1/8, β = 1/4 (`Rtt.kt:11-18`).

### `0x90` UNRELIABLE

| Offset | Size | Field | Type | Meaning |
|---|---|---|---|---|
| 0 | 1 | tag | u8 | `0x90` |
| 1 | 1 | channel | u8 | always `0x00` in `2.x`; any other value is dropped — see "Channel byte" below |
| 2 | variable (>= 0) | payload | bytes | application payload, verbatim |

Minimum size 2 (an empty payload is allowed). Malformed (< 2 bytes): WARN,
dropped, on both sides. The payload goes to the bytes handler verbatim, or
the text handler UTF-8-decoded and `.trim()`ed
(`HandshakeCoordinator.kt:176-183,270-291`; `MultiConnectionUDPClient.kt:535-542`).
Server: from an unregistered origin → DEBUG, dropped
(`HandshakeCoordinator.kt:271-272`).

### `0xA0` RELIABLE_DATA

| Offset | Size | Field | Type | Meaning |
|---|---|---|---|---|
| 0 | 1 | tag | u8 | `0xA0` |
| 1 | 1 | channel | u8 | always `0x00` in `2.x`; any other value is dropped — see "Channel byte" below |
| 2 | 2 | seq | u16 (big-endian) | this datagram's sequence number (RFC 1982 serial arithmetic) |
| 4 | 2 | ack | u16 (big-endian) | the highest sequence received **at all** from the peer — gaps allowed, never "highest in-order" |
| 6 | 4 | ackBits | u32 (big-endian) | bit *n* set ⇒ `ack − n − 1` was also received (delivered or buffered) |
| 10 | variable (>= 0) | payload | bytes | one application message |

Minimum size 10. Malformed (< 10 bytes): engine WARN, dropped; **never
throws** (`ReliableChannelEngine.kt:99-104`). Ack processing runs **before**
delivery on every inbound `0xA0` (`:99-113`). Every well-formed inbound `0xA0`
sets `ackPending`, including duplicates and out-of-window ones. Server: from
an unregistered origin → DEBUG, dropped, **no engine created**
(`HandshakeCoordinator.kt:185-189`).

### `0xA1` RELIABLE_ACK

| Offset | Size | Field | Type | Meaning |
|---|---|---|---|---|
| 0 | 1 | tag | u8 | `0xA1` |
| 1 | 1 | channel | u8 | always `0x00` in `2.x`; any other value is dropped — see "Channel byte" below |
| 2 | 2 | ack | u16 (big-endian) | the highest sequence received **at all** from the peer — gaps allowed |
| 4 | 4 | ackBits | u32 (big-endian) | bit *n* set ⇒ `ack − n − 1` was also received |

**Exactly** 8 bytes, no payload. Malformed (!= 8 bytes): engine WARN, dropped.
Sent on a retransmit tick only if nothing was due for retransmit, `ackPending`
is set, and the pair is not the sentinel `(0xFFFF, 0)` (before anything has
been received) (`ReliableChannelEngine.kt:150-165`).

### Channel byte

The channel byte (byte 1 of every `0x90`, `0xA0` and `0xA1` datagram) is
always `0x00` in `2.x`; wire protocol 2 has one unreliable and one reliable
channel. A receiver drops any such datagram whose channel byte is not `0x00`
and logs it at WARN; the log line names the channel byte and the datagram's
origin. It takes no other action: nothing is delivered, no acknowledgement
in it is processed, and no reliable channel is created for it.

Where the receiver screens a datagram's origin — every server branch, and
the client's reliable branch — the check comes after that screening, so a
datagram from an unknown source is still dropped at DEBUG whatever its
channel byte. The client's `0x90` branch does no origin screening (an open
defect, see [#39](https://github.com/SpartanLaboratories/WebTools/issues/39)),
so there the check applies to every `0x90`, whatever its source.

A future multi-channel feature must not send a non-zero channel to a peer
that has not signalled support for it — a `2.x` peer without it drops that
traffic.

Senders always emit `0x00` (`TransportWireFormat.DEFAULT_UNRELIABLE_CHANNEL`,
`ReliableWireFormat.DEFAULT_RELIABLE_CHANNEL`).

## 6. Reliable-channel behaviour

**Contract.** Sequence numbers are per direction, 16-bit, RFC 1982 serial
arithmetic, first seq `0`; the ack/ackBits semantics above (repeated once
more here for a reader who jumped straight to this section: **highest
sequence received at all, gaps allowed** — never "highest in-order");
piggybacked acks on every `0xA0`; a standalone `0xA1` per the rule above;
oversize send fails before the engine ever sequences, buffers, or transmits,
with `ReliableMessageTooLargeException` (`reliableMaxMessageBytes`, default
1024, max 8192, validated `1..8192` in both constructors); a full window
fails immediately with `ReliableWindowFullException(inFlight)`, no absorb
queue; receive-side `seq == cursor` delivers and drains buffered successors,
ahead-within-window buffers, beyond-window drops (sender retransmits),
duplicate drops (still re-acked); teardown (`terminate()`, a supersede,
`stop()` on either side) closes the engine and discards every in-flight or
buffered message; a client reliable send after `stop()` →
`IllegalStateException("client stopped")` (`MultiConnectionUDPClient.kt:485`);
a server reliable send to an unregistered peer → `IllegalStateException`
(`HandshakeCoordinator.kt:440-441`); idle `TIMEOUT` only notifies, the
engine stays intact; retransmits never refresh the peer's inbound liveness.
There is no disconnect datagram in wire protocol 2: teardown on one side
sends the peer nothing, and un-acked reliable data is simply discarded.

**Retransmission and timing (mechanism; the numbers themselves are §7's
informative values — referenced here, not restated as contract):**

- The first transmission of an accepted message happens immediately, inside
  the `send` call. A send that fails at the socket is not reported to the
  caller — the message is already buffered and the next retransmit tick
  retries it (`ReliableChannelEngine.kt:71-85`).
- An acknowledgement removes a buffered message when `ack` equals its seq
  exactly, or when bit *n* of `ackBits` is set for seq `ack − n − 1`
  (`ReliableRetransmitBuffer.kt:73-101`).
- A message is due for retransmission once its own retransmission timeout
  (RTO) has elapsed since it was last sent; each retransmission doubles that
  message's own RTO, up to the RTO cap. A retransmit tick resends at most a
  fixed number of due messages; if nothing is due and an inbound `0xA0` has
  arrived since this side last acked, the tick sends one standalone `0xA1`
  instead; otherwise an idle channel sends nothing at all
  (`ReliableRetransmitBuffer.kt:103-136`, `ReliableChannelEngine.kt:150-165`).
- The RTO follows RFC 6298: a per-connection estimator (independent of the
  link-quality probe's) seeds SRTT = the first sample and RTTVAR = half of
  it, then applies the α = 1/8, β = 1/4 EWMA; RTO = SRTT + 4·RTTVAR, clamped
  to [floor, cap], with **no** clock-granularity term; before the first
  sample the RTO is the floor. Karn's algorithm applies: a message that was
  retransmitted yields no RTT sample when acked
  (`ReliableRtoEstimator.kt:39-59`, `Rtt.kt`).
- Delivered payloads reach the reliable handler in order, on the side's
  dispatch thread: bytes verbatim via `actuateBytes`, or UTF-8-decoded and
  `.trim()`ed via `actuate` (`UdpChannel.kt:39-50`). With no reliable handler
  bound, delivered payloads are dropped at DEBUG — but they are still acked.
- Either side's engine exists only once that side's reliable channel is in
  use — see the Lifecycles section of
  [webtools-udp-architecture.md](webtools-udp-architecture.md) for the exact
  creation rules rather than restating them here. It is also created on an
  inbound reliable datagram on channel `0x00`, so a peer that never opened
  the channel still acknowledges. A non-zero-channel frame never creates one;
  it is dropped first (see "Channel byte").

## 7. Tuning values (informative, not contract)

| Value | Default | Why informative |
|---|---|---|
| In-flight / reorder window | 256 | shared bound between send and receive sides; a maintainer may retune without a major |
| RTO floor | 200 ms | internal estimator parameter |
| RTO cap | 5000 ms | internal estimator parameter |
| Retransmit tick | 50 ms | internal scheduling cadence |
| Max retransmits per tick | 32 | internal scheduling cadence |
| Keepalive poll clamp | 250–5000 ms | internal scheduling cadence |
| Probe loss horizon | 3 probe intervals | estimator internals |
| RTT EWMA constants | α = 1/8, β = 1/4 | estimator internals |

**Not tuning, but not wire contract either:** the default keepalive interval
(20 000 ms, `TransportWireFormat.DEFAULT_KEEPALIVE_INTERVAL_MILLIS`) and
default probe interval (1000 ms,
`TransportWireFormat.DEFAULT_PROBE_INTERVAL_MILLIS`) are public API
constants, governed by the README's API-stability note like any other
public member. On the wire, a peer may send keepalives and probes at any
cadence it chooses.

**Contract**, restated so a reader does not have to cross-reference: the tag
values, the wire layouts and byte order above, `FRAMED_PROTOCOL_VERSION = 2`,
`TransportWireFormat.MIN_PROBE_INTERVAL_MILLIS = 250`,
`UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES = 1024`, and
`UdpChannel.MAX_RELIABLE_MESSAGE_BYTES = 8192`.

## 8. Malformed / unexpected input

| Condition | Server | Client |
|---|---|---|
| Reserved tag (`0x83`–`0x8F`, `0x91`–`0x9F`, `0xA2`–`0xAF`, `0xB0`–`0xFF`) | WARN, dropped | DEBUG, dropped |
| Byte 0 `< 0x80` and the text does not start with the `Iam` token | WARN ("sender may be pre-2.0"), dropped | WARN, dropped |
| `Iam` with no name token (a bare `Iam`) | handshake parse failure, logged at WARN by the listener ("Failed to handle incoming datagram"), nothing registered, no reply | *(not applicable — the client never receives `Iam`)* |
| `0x81` malformed (not exactly 9 bytes) | WARN, dropped | WARN, dropped |
| `0x82` malformed | silently ignored | silently ignored |
| `0x90` malformed (< 2 bytes) | WARN, dropped | WARN, dropped |
| `0xA0` malformed (< 10 bytes) | engine WARN, dropped; never throws | engine WARN, dropped; never throws |
| `0xA1` malformed (`!= 8` bytes) | engine WARN, dropped | engine WARN, dropped |
| `0x90` from an unregistered origin | DEBUG, dropped | *(client has no per-origin registration)* |
| `0xA0`/`0xA1` from an unregistered origin | DEBUG, dropped, no engine created | *(as above)* |
| `0x90`/`0xA0`/`0xA1` whose channel byte is not `0x00` | after origin screening: WARN, dropped; no delivery, no ack processing, no engine created | reliable: after the `serverEndpoint` check, WARN, dropped, no engine created; `0x90`: every such datagram (no origin screening, [#39](https://github.com/SpartanLaboratories/WebTools/issues/39)), WARN, dropped |
| Datagram from an origin other than the peer of record | *(server addresses per-registration; not applicable)* | only the **reliable** (`0xA0`/`0xA1`) branch checks `origin == serverEndpoint`; `0x80`/`0x81`/`0x82`/`0x90` are processed regardless of source — an open defect, tracked as [#39](https://github.com/SpartanLaboratories/WebTools/issues/39) |
| Datagram larger than the configured receive buffer | delivered truncated, WARN | delivered truncated, WARN |

Describes the origin-screening gap only — this stage does not fix it.

Which drop wins when a datagram is **both truncated and on a non-zero
channel**:
- **`0x90`:** the length check comes first. A `0x90` under 2 bytes has no
  channel byte, so it is a malformed drop.
- **`0xA0`/`0xA1`:** the channel check comes first. It reads byte 1 on its
  own, so a truncated frame with a non-zero byte 1 gets the channel WARN and
  never reaches the engine. Only a truncated frame on channel `0x00` (or one
  too short to have a byte 1) gets the engine's malformed WARN.

## 9. Versioning & cross-version behaviour

| Client | Server | Outcome |
|---|---|---|
| `2.0` | `2.0` | success |
| `2.0` | `1.x` | client `handshake()` fails with `IncompatibleProtocolException(remote=1, local=2)` |
| `1.x` | `2.0` | client fails with `IllegalStateException`, but **the server has already registered it and fired `onClientConnect`** — that transient registration is removed only by idle detection, a supersede, or `terminate()` |

Source: `HandshakeWireFormat.kt:29-33`.

`REGISTERED 2` names wire protocol version `2`, which every `2.x` release
speaks. Changing the layout or meaning of any existing datagram is a new
wire-protocol version, and so a new library major. A later `2.x` minor may
allocate a new datagram type from a reserved range (see *Reserved ranges &
extension rules*); an older `2.x` peer drops that datagram (see *Malformed /
unexpected input*), so a feature built on a new type works only when both
ends support it.

## 10. Reserved ranges & extension rules

Reserved bands: `0x83`–`0x8F` (future transport control), `0x91`–`0x9F`
(future unreliable variants), `0xA2`–`0xAF` (future reliable-channel
control), `0xB0`–`0xFF` (unallocated). A receiver never dispatches a
reserved tag (see *Malformed / unexpected input*). Allocating a reserved tag
changes the wire protocol, and this document is updated in the same change
that allocates it.

## 11. Glossary

- **Datagram-type tag** — byte 0 of every post-handshake datagram; selects
  which codec and handling path applies (§4).
- **Reliable channel** — the acked, retransmitted, in-order delivery plane
  carried by `0xA0`/`0xA1`.
- **In-flight window** — the sender-side bound on unacked messages; full
  means backpressure, not congestion control.
- **Reorder buffer** — the receiver-side bound holding out-of-order messages
  ahead of the delivery cursor, within the window.
- **RTO** — retransmission timeout; how long the sender waits before
  resending an unacked message.
- **Karn's algorithm** — a retransmitted message contributes no RTT sample,
  so the RTO estimator is not skewed by ambiguous round trips.
- **Piggybacked ack** — the `(ack, ackBits)` pair carried on every outbound
  `0xA0`, free on any two-way traffic.
- **Standalone ack** — an `0xA1` sent purely to acknowledge, when there is
  nothing else to send.
- **Sentinel ack pair** — `(0xFFFF, 0)`, meaning "nothing received yet";
  never sent as a standalone ack.
- **Supersede** — a fresh `Iam` under an already-registered name from a
  different origin, replacing the old registration and resetting its
  reliable channel.
- **Idle sweep** — the periodic check (opt-in) that reports a connection
  with no recent inbound datagram via `onClientDisconnect(TIMEOUT)`.
