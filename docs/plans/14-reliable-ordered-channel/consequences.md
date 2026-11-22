# Issue #14 — consequences

- **Covers:** `SpartanLaboratories/WebTools#14`, *"Design discussion: a reliable-ordered sub-channel over the UDP
  transport"*.
- **What this file holds:** this folder predates the convention that gives an issue a `consequences.md`, and has no
  `impact.md`. The file records, for each later fix to the code this issue built, the follow-ups and known gaps the fix
  leaves, what it made no longer current, and what it closed. It does not restate this issue's plans or its as-built
  record, [`final-implementation.md`](final-implementation.md), and the landed documents are not edited.

## 1. Issue #39: origin screening on every client receive branch

The fix is [#39](https://github.com/SpartanLaboratories/WebTools/issues/39), `webtools-udp` `2.0.1a`. Its plan is
[`plan.md`](../39-client-origin-screening/plan.md) and its as-built record
[`final-implementation.md`](../39-client-origin-screening/final-implementation.md). The client's listener now checks
every datagram's source against `serverEndpoint` once, before it reads the tag.

### Closed

- **The client screened a datagram's origin on the reliable branch alone.** The reliable channel's origin guard (B2)
  covered `0xA0`/`0xA1` and left the keepalive, probe and `0x90` branches as they were, noting that widening it to
  every branch was a separate change ([`plan-reliable-channel-api.md`](plan-reliable-channel-api.md) section 3.4, "Scope
  is the reliable branch only"). The finalisation documents filed that change as #39 and left it open
  ([`architecture-finalisation.md`](architecture-finalisation.md) section 11, [`plan-finalisation.md`](plan-finalisation.md)
  sections 10 and 11). #39 closes it.
- **The as-built notes #39's fix was to update.** [`plan-finalisation.md`](plan-finalisation.md) section 11, "When #38 or
  #39 is fixed", requires the fix to update the notes that stage wrote into the protocol and architecture documents.
  #39 does: [`docs/webtools-udp-protocol.md`](../../webtools-udp-protocol.md) and
  [`docs/webtools-udp-architecture.md`](../../webtools-udp-architecture.md) now describe the screen.

### No longer current

These statements stay as written, as the record of what was built then. They describe the client as screening the
reliable branch only, with #39 open; the code and the two live documents above describe the screen.

- [`plan-reliable-channel-api.md`](plan-reliable-channel-api.md): section 3.4 ("Scope is the reliable branch only") and
  section 12, B2.
- [`final-implementation.md`](final-implementation.md): B2, in "B — required fixes, owed in code" ("Reliable branch
  only").
- [`architecture-finalisation.md`](architecture-finalisation.md): B8 in sections 1.3 and 6; section 11 (the client's
  keepalive, probe and `0x90` branches accept datagrams from any source); the prose of sections 13 to 15 (R8 and the
  channel-byte order rule among it) that describes the client's origin screening as covering the reliable branch only,
  its `0x90` branch as unscreened, or #39 as open; and the `0x81` and "Origin screening" notes of Appendix A.1.
- [`plan-finalisation.md`](plan-finalisation.md): criterion 17 in section 1.2; the protocol-document text of section 3.1
  (the `0x81`/`0x82` paragraph, "Channel byte", and the malformed-input rows, with "Describes the origin-screening gap
  only — this stage does not fix it") and the architecture-document text of section 3.2; and the statements in sections
  7, 8, 9, 10 and 11 that #38 and #39 stay open.

### Follow-ups

- **The reply `handshake()` waits for is not screened.** It takes the first datagram that arrives, whatever its source,
  so a forged `REGISTERED 2` or `REFUSED` from a host that can reach the client's port during the wait can answer first.
  Tracked as [#110](https://github.com/SpartanLaboratories/WebTools/issues/110), opened by #39. Open. It is the "Not in
  this fix" item of [#39's plan](../39-client-origin-screening/plan.md#1-requirements), section 1.

### Known gaps

All three are open and left by #39.

- **The tests' log capture reads an unsynchronised list.** `captureLogsOf` hands a test logback's plain event list while
  listener threads append to it, so the polls and the `any` and `none` reads of every test that uses it, #39's new tests
  among them, can in principle throw `ConcurrentModificationException` or see a stale size. No failure has been seen.
  Tracked as [#111](https://github.com/SpartanLaboratories/WebTools/issues/111). The exposure predates #39; section 5 of
  [#39's plan](../39-client-origin-screening/plan.md#5-risks), "Test timing", records that the new tests share it.
- **The client's gating and component tests bind real loopback sockets.** `MultiConnectionUDPClient` creates its
  `DatagramSocket` itself and has no seam to supply a transport, so no test of its listener is socket-free; #39's tests
  follow the module's standing pattern. The datagram-transport seam that
  [#50](https://github.com/SpartanLaboratories/WebTools/issues/50) records would let a test supply a fake transport. No
  separate issue was filed.
- **The class KDoc of `MultiConnectionUDPClient` embeds no sequence diagram of its listener and dispatch flow.** It
  describes the threads in prose and points at the sequence diagrams in the plans for #3 and #12 (sections 2.2 and
  2.8). R-11 (Documentation levels) asks for an embedded diagram for a non-trivial multi-thread flow, and the module's
  Dokka build renders none. #39 adds no thread, lock or lifecycle and left this as it was.
