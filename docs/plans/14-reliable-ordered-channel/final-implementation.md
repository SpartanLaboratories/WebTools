# Issue #14 — final implementation (as built)

**Covers:** `SpartanLaboratories/WebTools#14` — *"Design discussion: a
reliable-ordered sub-channel over the UDP transport"*.

## What was built

`webtools-udp` `2.0.0`, built in four stages on the integration branch
`feat/2.0-framed-transport` and merged to `master` by [#46](https://github.com/SpartanLaboratories/WebTools/pull/46) on 2026-09-26 as
merge commit `1f2c86c`.

| Stage | Plan | PR into `feat/2.0-framed-transport` | Merged | Merge commit | Version |
|-------|------|-------------------------------------|--------|--------------|---------|
| 1 — framing prefix | [`plan-framing-prefix.md`](plan-framing-prefix.md) | [#30](https://github.com/SpartanLaboratories/WebTools/pull/30) | 2026-09-18 | `5628fd8` | `2.0.0-alpha1` |
| 2 — internal reliable-ordered engine | [`plan-reliable-engine.md`](plan-reliable-engine.md) | [#32](https://github.com/SpartanLaboratories/WebTools/pull/32) | 2026-09-18 | `3519276` | `2.0.0-alpha2` |
| 3 — public reliable channel API | [`plan-reliable-channel-api.md`](plan-reliable-channel-api.md) | [#35](https://github.com/SpartanLaboratories/WebTools/pull/35) | 2026-09-22 | `55564ae` | `2.0.0-alpha3` |
| 4 — finalisation (docs + version) | [`plan-finalisation.md`](plan-finalisation.md), designed in [`architecture-finalisation.md`](architecture-finalisation.md) | [#44](https://github.com/SpartanLaboratories/WebTools/pull/44) | 2026-09-26 | `5fb8e61` | `2.0.0` |

The design record, [`architecture.md`](architecture.md), reached `master` first through
[#29](https://github.com/SpartanLaboratories/WebTools/pull/29) (`aaa4f66`, 2026-09-10). PRs [#31](https://github.com/SpartanLaboratories/WebTools/pull/31), [#33](https://github.com/SpartanLaboratories/WebTools/pull/33), [#36](https://github.com/SpartanLaboratories/WebTools/pull/36)
and [#45](https://github.com/SpartanLaboratories/WebTools/pull/45) backfilled the stage plans' headers with their commit and PR SHAs.
Between Stages 3 and 4 the integration branch also took the Issue #34 probe-cadence fix
([#37](https://github.com/SpartanLaboratories/WebTools/pull/37), `2.0.0-alpha4`) and the Issue #40 channel-byte guard ([#42](https://github.com/SpartanLaboratories/WebTools/pull/42),
`2.0.0-alpha5`); both are planned in their own folders,
[`34-probe-cadence/`](../34-probe-cadence/) and
[`40-channel-byte-guard/`](../40-channel-byte-guard/).

The sections below are the as-built and deviation records moved here verbatim from
this folder's documents. Each heading names the source document and section; each
source keeps a pointer where its record was.

## architecture.md header, Status

  > **As built (Stage 4, 2026-09-26):** implemented as `webtools-udp` `2.0.0`
  > through Issue #14's four staged PRs (#30, #32, #35 and the Stage-4 PR), the Issue #34
  > probe-cadence fix (#37), and the Issue #40 channel-byte guard (#42). The as-built
  > wire protocol and architecture are documented in
  > [webtools-udp-protocol.md](../../webtools-udp-protocol.md) and
  > [webtools-udp-architecture.md](../../webtools-udp-architecture.md), which are
  > authoritative wherever this design record differs from them. Known
  > divergences are annotated in place below: §6.2, §6.3, §7.3, §7.4/§7.5,
  > §7.8, §11, §12, §14 and D10.

## architecture.md §3.2 Option (b) — GameTools lives with unreliable-only + app-level reliability

> **As built (Stage 4, 2026-09-26):** D10 is superseded. `webtools-udp` `2.0.0`
> ships the reliable-ordered channel this design describes, so the interim
> app-level pattern was never added to the README and will not be (maintainer
> decision, Stage 4).

## architecture.md §6.2 Reliable header layout

> **As built (Stage 4, 2026-09-26):** `ack` is the highest sequence received **at
> all** from the peer, gaps allowed — never "highest in-order"; the bitfield
> meaning is as above. See [webtools-udp-protocol.md](../../webtools-udp-protocol.md)
> for the authoritative ack/ackBits contract.

## architecture.md §6.3 Acks

  > **As built (Stage 4, 2026-09-26):** there is no separate ack-delay timer. The
  > standalone `0xA1` is coalesced onto the 50 ms retransmit tick and sent only
  > when an `0xA0` has arrived since this side last acknowledged (a delayed-ack
  > guard, RFC 1122 §4.2.3.2 style) — see
  > [webtools-udp-protocol.md](../../webtools-udp-protocol.md).

## architecture.md §7.3 RTO — from the Issue #13 estimator

  > **As built (Stage 4, 2026-09-26):** the shipped RTO has no clock-granularity
  > term (`RTO = SRTT + 4·RTTVAR`, clamped to `[200, 5000]` ms), and the 200 ms
  > floor and 5000 ms cap are internal tuning, not configurable (Stage 3 kept
  > only the message-size cap public). See the tuning-values table in
  > [webtools-udp-protocol.md](../../webtools-udp-protocol.md).

## architecture.md §7.5 In-flight window — a bound, not congestion control

> **As built (Stage 4, 2026-09-26):** the window of §7.4 and §7.5 (256) is one
> shared value — the in-flight cap and the receive reorder window — and is
> internal tuning, not configurable. See the tuning-values table in
> [webtools-udp-protocol.md](../../webtools-udp-protocol.md).

## architecture.md §7.8 Flow — the ack / retransmit loop

> **As built (Stage 4, 2026-09-26):** this diagram is superseded by the as-built
> sequence diagram in [webtools-udp-architecture.md](../../webtools-udp-architecture.md),
> which reflects what shipped: the public entry point is
> `channel(RELIABLE_ORDERED).send`, not `sendReliable`, and the diagram adds the
> size check, `ackPending`, the listener/dispatch split, the 32-per-tick
> retransmit cap, and the client/server asymmetry in engine creation. The
> diagram's "ack=highest-in-order" is also superseded: `ack` is the highest
> sequence received at all, gaps allowed.

## architecture.md §11 Risks & edge cases

  > **As built (Stage 4, 2026-09-26):** prevented structurally rather than by this
  > mitigation — the retransmit ring's capacity *is* the in-flight window, so a
  > slot can never be reused while its occupant is still unacked.

## architecture.md §12 Version-control approach (if greenlit)

  > **As built (Stage 4, 2026-09-26):** every Issue #14 stage PR, and the final
  > `feat/2.0-framed-transport` → `master` merge, uses a **merge commit**, never
  > a squash — the stage plans cite the stage commits by SHA.

## architecture.md §13 Decisions, D10

**As built (Stage 4, 2026-09-26):** superseded by the shipped `2.0.0` reliable channel; no README content (maintainer decision, Stage 4).

## architecture.md §14 Sequencing & follow-ups

> **As built (Stage 4, 2026-09-26):** the "Immediate" item is complete — the
> implementation plans and the staged `2.0.0-alphaN` series were delivered in
> four stages and closed out as `2.0.0`. The numbered follow-ups below remain
> open and unscheduled. The "multiple reliable channels" follow-up can reuse
> the reserved channel byte: `2.x` receivers drop a non-zero channel, so
> using it needs both ends to support it (see
> [webtools-udp-protocol.md](../../webtools-udp-protocol.md)).

## plan-reliable-channel-api.md §12 Verification pass (2026-09-20) — what changed in this plan, and what is still owed in code

A verification pass read the Stage-3 working tree against this plan
section-by-section. Everything it raised was put to the maintainer and resolved
on **2026-09-20**. Two kinds of outcome, and the difference matters:

- **A-items — the plan was wrong, the code was right.** The implementation had
  made a better call than the plan, or reality had one more constraint than the
  plan anticipated. These are corrections *to this document*; nothing is owed in
  code, except where noted.
- **B-items — the plan was right, the code is not there yet.** Each is now
  written into the section that specifies it and is **binding**: the stage is
  not done until all five are in.

Cross-references below point at the section that now carries the specification.
That section, not this table, is the authority.

### A — plan corrected to match the implementation

| # | What this plan used to say | What is true, and now written here |
|---|---|---|
| **A1** | The `ReliableSendFailure` open-hierarchy guarantee locked by `ReliableSendFailure::class.isSealed`. | `kotlin-stdlib`'s minimal `KClass` does not implement `isSealed`, so that form needs `kotlin("reflect")` on the test classpath. **Resolved: lock it at compile time instead** — a `private` subclass of `ReliableSendFailure` declared in the gating test file, which stops compiling if the type is ever sealed. Stronger *and* free. **§4.17** now states that no `dependencies { }` block and no `kotlin("reflect")` is added; **§6** and **§10 OD-2** carry the mechanism. *(This one does owe a code delta — see the hand-off list.)* |
| **A2** | §4's file-by-file list did not mention the design document at all. | Stage 3 does edit it: one dated **"Superseded 2026-09-18"** block under §5 Option A, recording that the handler member ships as `actuateBytes` + `actuate(String)`. Now **§4.20**, with the rule that a superseded design is annotated in place, never rewritten, and that nothing else in that document is touched. Added to commit 1's file list (**§9**) and to the documentation-impact table (**§5**). |
| **A3** | "None of the 27 old-path test files is refactored", with no note that any existing assertion stops being true. | One does: `FramingComponentTest`'s reserved-tag case used `0xA0` *because* it was reserved, and this stage makes it live. It is **repointed to `0xA2`** — still reserved per `DatagramType` (`0xA2`–`0xAF`, reliable-channel control) — never deleted, since the reserved-tag behaviour still needs cover. **§3.8** and a **§6** row. |
| **A4** | `UDPConnection`'s four overrides need `@Deprecated` **plus** `@Suppress("OVERRIDE_DEPRECATION")`, "forced". | Not forced, and not needed. The diagnostic is *"overrides a deprecated member but is not marked as deprecated itself"*, so an override carrying its own `@Deprecated` never trips it; adding the suppression would silence a warning that does not fire. `FakeConnection` **does** need it, because it stays undeprecated on purpose — that contrast is now the stated rule. **§3.11** (both rows), **§4.8**, **§4.15**. The one real suppression in `UDPConnection` is `@Suppress("DEPRECATION")` on `push(String)`, for its *body*'s call to `push(bytes)`. |
| **A5** | §4.13 listed three engine changes, none of them a visibility change. | Wiring the engine forces two: `private companion object` → `companion object` (so `DEFAULT_RETRANSMIT_TICK_MILLIS` / `MAX_RETRANSMITS_PER_TICK` are reachable from the two callers, with the sentinels re-marked `private`), and `private val windowSize` → `val windowSize` (so a caller can report a truthful `inFlight`). Neither widens anything past the module — `ReliableChannelEngine` is `internal`, so **OD-5 stands**. **§4.13**, now five changes. |
| **A6** | §3.3 implied both sides mint the engine on *any* `channel(RELIABLE_ORDERED)` call, `send` or `actuate`. | They differ, and the difference is right. The server's `bindReliable` mints eagerly; the client's reliable `actuateBytes` binds and starts the listener but mints **nothing** until the first send or first inbound `0xA0`/`0xA1`. The client is safe because it has one peer and the same call arms the listener, so the inbound trigger cannot be missed — and a receive-only client then allocates nothing at all. **Endorsed, not to be made symmetric.** §3.3 (asymmetry table), §4.9, §4.11. |
| **A7** | A new gating class for `scheduleTick`'s validation, "or folded into the existing validation test". | It went into the existing `PeriodicSchedulerValidationTest`, which lives in the **component** tier beside the identical `schedule` cases. **Accepted as the right home** — `PeriodicScheduler` owns a real executor, and splitting one seam's two methods across two tiers would only hide them. No gating class is added. **§6**. |

### B — required fixes, owed in code

| # | Fix | Specified in |
|---|---|---|
| **B2** | **Origin guard on the client's reliable receive branch.** Compare the inbound datagram's `socketAddress` with `serverEndpoint` and DEBUG-drop anything else *before* `reliableEngine()` is touched, mirroring the server's `findByOrigin` guard. The client's socket is unconnected; without this, one stray or spoofed `0xA0` mints the engine, its buffers and the retransmit thread, and can reach the application's reliable handler. Requires `receiveLoop`'s `runCatching` to yield the origin alongside the byte copy. Reliable branch only. | **§3.4**, §4.11, §3.10 (alternatives), §7, §6 |
| **B3** | **Bind the engine to a `val` in `HandshakeCoordinator.sendReliable`'s window-full path** and report `engine.windowSize`, instead of re-reading `reg.reliable?.windowSize ?: 0` — a concurrent deregister or supersede can null that field between the send and the failure, making the one exception whose job is to report in-flight count report `0`. Matches what the client already does. | **§4.9**, §6 |
| **B5** | **Split `MultiConnectionUDPClient.stop()`'s `runCatching { retransmit.shutdown(); reliableEngine?.close() }` into two independent `runCatching` steps**, so a throwing `shutdown()` cannot skip the engine close — the method documents that *every* step runs regardless, and every other step already honours that. | **§4.11**, §3.3, §6 |
| **B6** | **Drop the unused `connection` parameter from `ReliableConnectionChannel`** (two parameters: `clientChannel`, `peer`) and from its construction site in `UDPConnection`. | **§4.8** |
| **B7** | **Add a constructor-arity lock for `MultiConnectionUDPClient`'s public constructor**, mirroring the server's in `IdleTimeoutValidationGatingTest`: the four `@JvmOverloads` spellings present, the seven-parameter `internal` seam constructor the only other one, set size exactly 5, `DefaultConstructorMarker` bridges filtered. Plain `java.lang.Class` reflection — no `kotlin-reflect`. | **§6** |

**B1 and B4** were also raised by the pass and were deliberately left as
**observations only** — reviewed, no action in Stage 3, nothing owed in code or
in this plan.

### D — QA remediation pass (2026-09-20, later the same day)

A QA gate (`test-inspector`) read **§6's matrix** against the test tree and
found two defects **in the matrix, not in the design**. Both are fixed in the
rows above. Neither changes a decision, a signature, or a behaviour; D1 owes
test code, D2 owes nothing.

| # | What QA found | What changed here |
|---|---|---|
| **D1** | **The two OD-1 server-wide reliable conveniences had no test allocated at any level.** `MultiConnectionUDPServer.startReliable` / `pushToAllReliable` are specified in §4.10, backed by `HandshakeCoordinator.actuateAllReliable` / `broadcastReliable` (`HandshakeCoordinator.kt:513`, `:551`) and already shipped (`MultiConnectionUDPServer.kt:441`, `:482`) — but §6 gave them no row, and a **binary-tolerant** sweep of the whole test tree returns **zero** hits for either name. Their unreliable siblings `start` / `startBytes` / `pushToAll` have **component + integration + e2e + UAT** cover. The gap matters more than a missing row: these two are the only members in the stage with a **non-short-circuiting fold**, and no per-connection test can reach it. | **Two §6 rows**, then a third closing a follow-on gap. A new **E2E** file `MultiConnectionUDPReliableBroadcastE2ETest.kt` (real server + three real clients) covering binding, broadcast, and the one-peer-window-full fold case; and a **second** `@Disabled`, comment-only **UAT** script covering the pair over a real lossy path, in the form the file's Issue #10 / #12 scripts already use for `pushToAll`. §4.10 now carries a pointer back at both. **A new e2e file rather than more methods on `MultiConnectionUDPReliableE2ETest`**: the harness genuinely differs (a three-client roster with one deliberately silent peer, against that file's one client per test), per-file harnesses are this tree's established idiom, and a file named for the functions is what the next sweep will look for. **A third QA pass then found the integration tier itself still missing** — real sockets, no fakes, the tier the unreliable siblings already had via `MultiConnectionUDPServerTest.kt`'s "pushToAll reaches every registered client socket" test — and it is closed by the new happy-path-only `MultiConnectionUDPReliableBroadcastTest.kt`. |
| **D2** | **The §6 nonfunctional row named the wrong file for the B3 guarantee.** It claimed `ReliableChannelNonFunctionalTest`'s window-full burst asserts the `ReliableWindowFullException`'s `inFlight` equals the engine's real window size. It does not — that case asserts the success count and the failure's *type* only (`ReliableChannelNonFunctionalTest.kt:49`). The assertion exists; it lives elsewhere. | **Corrected across three rows.** B3's *value* half is asserted at **component** level (`HandshakeCoordinatorReliableTest.kt:130-144` — `assertEquals(windowSize, failure.inFlight, …)`) and its *field* half at **gating** level (`ReliableChannelExceptionsGatingTest.kt:19-24`); both rows now say so, and the nonfunctional row now states plainly that it does **not** cover B3. **B3 itself is unaffected and is already implemented** — `HandshakeCoordinator.sendReliable` binds `val engine = reliableEngineFor(reg)` and reports `engine.windowSize` (`HandshakeCoordinator.kt:436-441`). |

### Still open — not decided, not actioned here

Two items the pass surfaced are with the maintainer and are **not** part of the
§10 decision set, because they are not yet resolved. Neither blocks the B-items
above; both should be settled before `2.0.0` is cut:

- **C1 — the stability tier of the new public surface.** Which of Stable Core /
  Supported Extension / Experimental `DeliveryMode`, `UdpChannel`,
  `ReliableSendFailure` and its two subclasses each sit in, and therefore what
  may change in a minor.
- **C2 — the design doc's D10 README snippet.** D10 resolved "yes, a short
  README snippet / note" for consumers needing reliability before `2.0.0`;
  whether it still ships, and where, is undecided.
