# Issue #1 — final implementation (as built)

**Covers:** `SpartanLaboratories/WebTools#1` — *"Handshake replies to the
client-claimed address, not the datagram source: breaks any NAT'd client"*.

## What was built

Issue #1 shipped in the two tiers that [`architecture.md`](architecture.md) lays out,
each through its own pull request into `master`:

| Tier | Plan | PR | Merged | Merge commit | Version |
|------|------|----|--------|--------------|---------|
| 1 — NAT-safe UDP handshake | [`plan-tier-1.md`](plan-tier-1.md) | [#2](https://github.com/SpartanLaboratories/WebTools/pull/2) | 2026-09-04 | `f137247` | `WebTools` `2.0.0b` |
| 2 — all client traffic multiplexed over the common UDP socket | [`plan-tier-2.md`](plan-tier-2.md) | [#4](https://github.com/SpartanLaboratories/WebTools/pull/4) | 2026-09-04 | `723da9e` | `WebTools` `2.0.0c` |

PR #2 also carries `1591a8e`, which adopted the 5-level test hierarchy for the existing
suite (the prep step in `plan-tier-1.md` §3).

The sections below are the as-built and deviation records moved here verbatim from
this folder's documents. Each heading names the source document and section; each
source keeps a pointer where its record was.

## architecture.md §2 Tier 1 — correctness fix — DONE

Implemented on branch `fix/issue-1-handshake-datagram-source`. See
[`plan-tier-1.md`](./plan-tier-1.md) for the detail.
Summary of what shipped: the server learns the client endpoint from the datagram source,
replies on the receiving socket, the protocol is now `Iam <name>` → bare `TXRXON`,
`commonSendSocket` / `COMMON_SEND_PORT` are gone, `pushToAll` targets each client's learned
origin, and a retransmitted `Iam` reuses its ports instead of allocating a new pair.

## plan-tier-1.md header, As-built deltas from this plan

**As-built deltas from this plan:**
- `commonListenSocket` renamed to `commonSocket` (it now both sends and receives);
  removed `COMMON_SEND_PORT` (public) and `HANDSHAKE_ADDRESS_INDEX`.
- §1.5 retransmit de-dup: included.
- `pushToAddress` → `replyToOrigin`.

## plan-tier-1.md header, Follow-up: testability refactor for full test-hierarchy compliance

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

## plan-tier-1.md header, Test matrix (as built)

**Test matrix (as built):**

| Level | Package | Class | Covers |
|-------|---------|-------|--------|
| 1 gating | `testing.gating.webtools` | `HandshakeProtocolGatingTest` (4), `HandshakeCoordinatorGatingTest` (3) | fast socket-free smoke: parse / port-disjointness / reply-shape, and register / retransmit-no-reregister / `findByOrigin` |
| 2 component | `testing.component.webtools` | `HandshakeCoordinatorTest` (13), `RegistrationsTest` (5) | state machine (new-vs-dedup, port allocation, reply-failure path, order) and the fan-out methods (`actuateAll` / `broadcast` / `terminateAll` incl. partial-failure), plus the collection; socket-free via `FakeConnection` |
| 3 integration | `testing.integration.webtools` | `MultiConnectionUDPServerTest` (10) | end-to-end over real UDP: reply-to-source, bare `TXRXON`, payload-token-ignored, **retransmit dedup**, **multi-client disjoint ports**, `pushToAll`-to-origin, malformed / empty / unknown-verb / oversized datagrams, `stop` |
| 4a deterministic | `testing.deterministic.webtools` | `HandshakeProtocolTest` (15) | exhaustive input→output for every `HandshakeProtocol` function |
| 4c non-functional | `testing.nonfunctional.webtools` | `HandshakeNonFunctionalTest` (2) | retransmit-storm bounded allocation; reply target is never a payload-claimed address |
| 5 UAT | `testing.uat.webtools` | `MultiConnectionUDPServerUatTest` (2, `@Disabled`) | manual two-host cross-NAT procedure — see [`uat-tier-1.md`](./uat-tier-1.md) |

The `stop()` / `start()` / `pushToAll()` fan-out folds were moved onto `HandshakeCoordinator`
as `terminateAll()` / `actuateAll()` / `broadcast()` so the "terminate every connection even
if one fails" contract is unit-tested with failing fakes rather than only on the happy path.

Shared fixture: `testing.support.webtools.FakeConnection`. New Gradle tasks:
`gatingTest`, `deterministicTest`, `nonfunctionalTest`, `uatTest` (joining `componentTest` /
`integrationTest`); a `CommonUdpPortLock` build service serialises `test` and
`integrationTest` so parallel workers never collide on port 9998. `4b` has no task — no
end-to-end tests exist yet.

## architecture.md §3 Tier 2 — real NAT traversal (choose one)

**Decision: Option B1 (single-socket multiplex) was chosen and implemented.** See
[`plan-tier-2.md`](./plan-tier-2.md) for the full design and
file-by-file breakdown. The handshake reply landed as the single token
`REGISTERED` (not the `OK`/token placeholder sketched below). Shipped as `2.0.0c`.
