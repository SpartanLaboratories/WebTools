# Issue #39 — origin screening on every client receive branch

- **Covers:** `SpartanLaboratories/WebTools#39` — *"Client only checks datagram origin on the
  reliable-channel receive branch"*.
- **Architecture:** none: a fix inside one component, `webtools-udp`'s `MultiConnectionUDPClient`. The
  check it extends is the reliable branch's `serverEndpoint` guard, designed in
  [`plan-reliable-channel-api.md`](../14-reliable-ordered-channel/plan-reliable-channel-api.md) (§12 B2).
- **Branch:** `fix/issue-39-client-datagram-origin-check` (exists, cut from `master` at `d68ec81`).
  This plan is committed in the same commit as the code it describes.
- **What this plans:** the client's listener checks every inbound datagram's source against
  `serverEndpoint` once, before it reads the tag, so the keepalive, probe, unreliable and unknown-tag
  branches drop a stranger's datagram as the reliable branch already does; and the tests and documents
  that say so.
- **Status:** planning only.
- **Target version:** `webtools-udp` `2.0.1` → `2.0.1a`. A bug fix that changes what a consumer's client
  accepts takes the trailing letter (R-25 (Versioning)). The number is set in the landing's own `build:` commit; the
  commits that carry this plan never edit `webtools-udp/build.gradle.kts`.

Paths under `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/` are abbreviated `main/`, and
paths under `webtools-udp/src/test/kotlin/com/spartanlabs/testing/` are abbreviated `test/`. Line numbers
are on baseline `d68ec81`. Commands run from the repository root in Git Bash with `./gradlew.bat`. Where
a step does not match the file as quoted, stop and report; do not improvise.

## 1. Requirements

`MultiConnectionUDPClient`'s listener (`receiveLoop`) must:

1. **Drop a datagram from any source other than `serverEndpoint`,** whatever its tag, an unknown or
   missing tag included. The source is the datagram's address and port. The drop logs one DEBUG line
   naming both addresses. For such a datagram nothing is delivered, answered, completed, counted or
   created: no handler runs, no `0x82` is sent for an `0x81`, no probe completes for an `0x82`, no
   reliable engine or retransmit tick is created for an `0xA0`/`0xA1`, and none of the WARNs that a
   malformed, unframed or non-zero-channel frame draws is logged.
2. **Handle a datagram from `serverEndpoint` exactly as before** on every branch, WARNs included, so
   legitimate traffic is undisturbed.
3. **Screen once, before the tag is read,** so a tag added to the switch later is covered without a
   check of its own.
4. Change no public API, persisted state or wire format.

Not in this fix:

- **The handshake reply.** `handshake()` (`main/MultiConnectionUDPClient.kt:305-309`) takes the first
  datagram that arrives, whatever its source. It is one blocking read, not a branch of the listener.
  Screening it means a drop-and-keep-waiting loop under the one deadline, and a server whose reply
  leaves from another address would then fail to connect at all, where today it only loses inbound
  traffic. That is a separate change; the documents below state the behaviour as it is.
- **The receive-buffer truncation WARN** (`:520-525`). It stays ahead of the screen: it acts on nothing
  and names the real source.
- **The server side,** whose branches already ignore a datagram from an unregistered origin (a keepalive
  has no effect beyond a TRACE line).
- **A looser or configurable match** (port only, host only, several endpoints). Any of them reopens the
  injection this fix closes.

## 2. Root cause

`receiveLoop` captures every datagram's origin (`main/MultiConnectionUDPClient.kt:530`) but compares it
with `serverEndpoint` (`:223`) only inside the reliable arm (`:579-583`). The socket is unconnected, so
any host that can reach its bound port reaches the other arms. The reliable guard was added as a
reliable-only check (a988834, the reliable channel's public API); the keepalive, probe and `0x90` arms
date from the framing change (0009996) and were left without one, which is the gap #39 records.

What a stranger's datagram does today:

| Tag | Effect today (`main/MultiConnectionUDPClient.kt`) |
|---|---|
| `0x80` keepalive (`:534`) | a TRACE line saying it came from the server; no other effect |
| `0x81` ping (`:536-540`) | the client sends an `0x82` to the server: an unsolicited datagram a stranger can trigger at will |
| `0x82` pong (`:542-545`) | `completeProbe(seq)`: a forged sample in `linkQuality()`; probe sequences count up from 0, so they are guessable |
| `0x90` (`:547-571`) | the payload reaches the unreliable handler |
| unknown or unframed (`:611-618`) | a WARN or DEBUG naming "server" for a stranger's datagram |
| `0xA0`/`0xA1` (`:573-609`) | already screened (`:579-583`) |

## 3. Fix

### `main/MultiConnectionUDPClient.kt` (no signature, visibility or field changes)

1. **Screen first.** In `receiveLoop`'s `.onSuccess { (bytes, origin) -> … }` (`:531`), before the `when`
   (`:533`), insert:

   ```kotlin
   // The socket is unconnected, so any host that can reach its port can send to it. Every datagram
   // is checked against the one server endpoint before its tag is read, so a stray or spoofed
   // source is never answered (0x81), never completes a probe (0x82), never reaches the
   // application (0x90), never mints the reliable engine (0xA0/0xA1) and never draws a WARN
   // (a malformed, unframed or non-zero-channel frame). One check ahead of the switch, not one
   // per branch, so a tag added to the switch later is screened without a check of its own.
   if (origin != serverEndpoint) {
       log.debug("Datagram from unexpected origin {} (expected {}), dropped", origin, serverEndpoint)
       return@onSuccess
   }
   ```

   `Result.onSuccess` is inline, so `return@onSuccess` leaves the lambda and the chained `.onFailure` is
   unaffected. Nothing here throws or returns a `Result`: an off-origin datagram is an expected input and
   the answer is a drop. `serverEndpoint` is an immutable `val` that the listener thread only reads.
   The event is one DEBUG line through the file's `log`, parameterised, naming both addresses and no
   payload, at the level the reliable arm's own line and the server's unregistered-origin drops use.
2. **Remove the reliable arm's own check.** Delete the arm `origin != serverEndpoint -> log.debug(…)`
   (`:579-583`) and the comment above `val channel` (`:574-576`), whose point moves into the screen's
   comment. The `when` keeps its channel arm and its `else`.
3. **Re-aim the inline documentation** (comments and KDoc):
   - `:526-529`: "the reliable branch below needs it to screen against serverEndpoint (§12 B2)" becomes
     "the screen below needs it to check the source against serverEndpoint".
   - `:556-561`, the `0x90` arm's channel-check comment: drop "this branch has no origin screen (#39), so
     the channel check applies to every 0x90."; the rest, which says the check is duplicated, stays true.
     The reliable arm's channel-check comment (`:585-590`) needs no edit.
   - Class KDoc, listener sentence (`:33-36`): "`receive()` -> drop a datagram whose source is not the
     server endpoint -> switch on the [DatagramType] tag (byte 0) -> drop a keepalive/probe or strip the
     `0x90` frame and hand the payload to the dispatch executor."
   - Class KDoc, boundary notes (`:69`): the listener "answers an inbound `0x81` probe from the server
     endpoint with an `0x82` reply inline".
   - Class KDoc, new paragraph under its own heading (the contract a consumer needs):

     ```
      * ### Datagram origin
      * The socket is unconnected, so the listener accepts a datagram only from the server's address and
      * port (the constructor's `serverAddress` and `serverPort`) and drops one from any other source at
      * DEBUG, whatever its tag, before reading it. A server must therefore reply from the address and
      * port the client sent to. The one read that does not check is the reply [handshake] waits for.
     ```

     `serverAddress` and `serverPort` are in code font, not links, because they are private.
   - `receiveLoop`'s KDoc (`:508-512`): "Body of the listener thread: drop a datagram whose source is
     not [serverEndpoint], then switch on the [DatagramType] tag and …" (the rest unchanged).
   - Every `[…]` link stays valid: the API-reference gate fails on a broken one.

**One screen, not one per branch.** A check in each arm would be five copies, and nothing would make the
next tag added to the switch carry one. The channel-byte checks are repeated in their arms because each
reads its own tag's bytes; this check reads none of them. `origin != serverEndpoint` is
`InetSocketAddress` equality (address and port), the comparison the reliable arm already made.

### `docs/webtools-udp-protocol.md` (the as-built notes about this gap)

- Header, line 3: "as of `2.0.0a`" becomes "as of `2.0.1a`".
- §2 "Transport & addressing", after "(`MultiConnectionUDPClient.kt:16-29,200-201`).": add "That socket is
  unconnected, so the client's listener checks every datagram's source against the server endpoint before
  it reads the tag (`MultiConnectionUDPClient.kt:<screen>`); the one read it does not check is the reply
  `handshake()` waits for."
- §5 `0x81`/`0x82` (`:228-234`): replace "The server answers a valid `0x81` only from a registered origin"
  through "described here only." with "Each side answers a valid `0x81` only from its peer of record: the
  server from a registered origin, the client from `serverEndpoint`. A `0x81` from any other source is
  dropped at DEBUG with no reply (`HandshakeCoordinator.kt:182-193`;
  `MultiConnectionUDPClient.kt:<screen>`), and a `0x82` from any other source completes no probe."
- §5 `0x90` (`:245-250`): after "Server: from an unregistered origin → DEBUG, dropped
  (`HandshakeCoordinator.kt:409-410`)." add "Client: from a source other than `serverEndpoint` → DEBUG,
  dropped."
- §5 "Channel byte" (`:293-298`): replace "Where the receiver screens a datagram's origin" through
  "whatever its source." with "Both receivers screen a datagram's origin first (every server branch
  against the registrations, every client branch against `serverEndpoint`), and the channel check comes
  after that screening, so a datagram from an unknown source is still dropped at DEBUG whatever its
  channel byte."
- §8 table, client cells: `0x90` from an unregistered origin → "from a source other than `serverEndpoint`:
  DEBUG, dropped"; `0xA0`/`0xA1` from an unregistered origin → "from a source other than `serverEndpoint`:
  DEBUG, dropped, no engine created"; the channel-byte row → "after the `serverEndpoint` check: WARN,
  dropped; no delivery, no ack processing, no engine created"; "Datagram from an origin other than the peer
  of record" → "every datagram, of any tag, is checked first: from a source other than `serverEndpoint` it is
  dropped at DEBUG, and nothing is delivered, answered, completed or created for it; the one exception is
  the reply `handshake()` waits for, which takes the first datagram to arrive". Delete the line "Describes
  the origin-screening gap only — this stage does not fix it." and every link to #39.
- Recompute every `MultiConnectionUDPClient.kt:N` reference in this document against the final source
  file. They stand at this document's lines 43 (`16-29,200-201`), 108 and 400 (`611-618`), 165 (`835`),
  248 (`547-571`) and 323 (`493`), besides the two new `<screen>` ones above. Find each target with
  `grep -n` on the code it names, never by arithmetic.

### `docs/webtools-udp-architecture.md` (§6, "Client differences, in prose")

Replace the bullets from "There is no per-origin registration table" through "The client's reserved-tag
drop is DEBUG, not WARN." with:

```
- There is no per-origin registration table: the client has one peer of record. **Every** datagram, of
  any tag, is first checked against `serverEndpoint` (`origin == serverEndpoint`); one from any other
  source is dropped at DEBUG before its tag is read, so nothing is delivered, answered, completed or
  created for it. The one read that is not checked is the reply `handshake()` waits for.
- The channel-byte check runs on both data branches, after that origin check, so a non-server source is
  dropped at DEBUG whatever its channel byte.
- The client answers a valid `0x81` from `serverEndpoint`.
- The client's reserved-tag drop is DEBUG, not WARN.
```

### `CHANGELOG.md`

Under `## [2.0.1a] - <landing date>` (above `## [2.0.1]`; under `## [Unreleased]` if the landing moves no
version), `### Fixed`:

> - `webtools-udp`: the client accepts a datagram only from the server endpoint it handshook with, on
>   every receive branch. 2.0.1 screened the source on the reliable channel's branch alone, so a datagram
>   from any other address was acted on as if the server had sent it: an `0x81` probe was answered, an
>   `0x82` completed an in-flight probe in the link-quality estimate, and an `0x90` reached the unreliable
>   handler. Such a datagram is now dropped before its tag is read, with one DEBUG line naming both
>   addresses. A server whose replies leave from another address than the one the client sent to loses its
>   inbound keepalive, probe and unreliable datagrams to this, as it already lost its reliable ones. The
>   reply the handshake waits for is not screened. No public API or wire change (Issue #39).

### Not changed

`README.md` (a fix that leaves the external shape as it was needs no change; its install snippet follows
the version bump like any other), the other `main/` files, `webtools-udp/build.gradle.kts` (the version
step), the earlier plan documents (history, not edited here), and the generated state boards under
`docs/dashboards/`, which list this issue as open and are regenerated, never hand-edited.

## 4. Regression tests

Write the tests first and run them against the unfixed code; each must fail on the assertion named below.
No other module depends on `webtools-udp`, so no other module's suite runs.

All the new tests use two loopback sockets besides the client: `peer`, the socket the client targets (its
`serverEndpoint`), and `foreign`, a second socket the client does not target. They synchronise on the
listener, not on sleeps: inside `captureLogsOf(MultiConnectionUDPClient::class.java, Level.DEBUG) { events -> … }`
(`Level.TRACE` in the keepalive test) the test polls, at 20 ms steps to a 2 s deadline, until the number
of DEBUG events containing `Datagram from unexpected origin` equals the number of datagrams `foreign` has
sent. Every such datagram logs exactly one such line, so reaching the count proves the listener has
handled them all; the test then asserts that nothing else happened, sends the same kind of datagram from
`peer`, and asserts it is handled normally. Each new class carries copies of its siblings' helpers:
`fakePeer`, `newClient` (the internal seam constructor with `FakePeriodicSchedule`s, as
`MultiConnectionUDPClientFramingTest.newClient` has it), `nextBytes` and `@AfterTest cleanup()`. A handler
collects into a `CopyOnWriteArrayList`, because the dispatch thread appends while the test thread reads.

| Test | Set | File | Fails today on |
|---|---|---|---|
| `a datagram of every tag from a foreign socket is dropped - the server's datagram that follows is the only one acted on` | gating | `test/gating/webtools/udp/ClientOriginScreenGatingTest.kt` (new) | the unreliable handler holding `[1]` before `[9]`; `linkQuality()` non-null; a stray `0x82` at `peer` |
| `an off-origin 0x80 keepalive is dropped at DEBUG naming both origins - the server's keepalive still reaches the keepalive drop` | component | `test/component/webtools/udp/MultiConnectionUDPClientOriginScreenTest.kt` (new) | no `Datagram from unexpected origin` line, and a `Dropped keepalive from server` line for the stranger |
| `an off-origin 0x81 PROBE_PING draws no 0x82 reply - the server's PING is still answered` | component | same file | `peer.nextBytes(300)` is a `0x82` for sequence 7, not `null` |
| `an off-origin 0x82 PROBE_PONG completes no probe - the server's PONG still does` | component | same file | `linkQuality()` is non-null after the stranger's pong |
| `an off-origin 0x90 is not delivered - the server's 0x90 still is` | component | same file | `received` holds `[1]` before the server's frame |
| `an off-origin 0xA1 mints no reliable engine - the server's 0xA1 still does` | component | same file | the wait for the shared DEBUG line (the reliable arm logs its own wording today); its no-engine assertions already hold |
| `an off-origin unframed datagram is dropped at DEBUG without the unframed WARN - the server's still draws it` | component | same file | a WARN `Unframed/unknown datagram from server` for the stranger |
| `a channel-0x01 0x90 from a foreign socket keeps the DEBUG unexpected-origin drop, with no channel WARN` | component | `test/component/webtools/udp/MultiConnectionUDPClientFramingTest.kt:156-185` (existing test, flipped in place) | no DEBUG line, and a channel WARN that names the stranger |

The existing `an 0xA0 from a foreign origin mints no engine and delivers nothing` in
`test/component/webtools/udp/MultiConnectionUDPClientReliableTest.kt:175` covers the `0xA0` half of the
reliable arm and stays as it is.

**Gating test.** A fast smoke over every branch in one pass, mirroring the existing gating tests that bind
a real client to ephemeral loopback ports (no common-port lock is needed). It binds both handlers
(`channel(UNRELIABLE).actuateBytes` into `received`, `channel(RELIABLE_ORDERED).actuateBytes` into
`reliableReceived`), arms `startProbe(5_000L)` on the fake probe schedule, ticks it once and reads the live
probe sequence from `peer.nextBytes(2_000)` with `TransportWireFormat.probeSequenceOf`. From `foreign` it
sends, in this order: `keepaliveDatagram()`, `probePingDatagram(99)`, `probePongDatagram(<live sequence>)`,
`unreliableDatagram(byteArrayOf(1))`, `ReliableWireFormat.reliableDataDatagram(seq = 0, ack = 0xFFFF,
ackBitfield = 0, payload = byteArrayOf(2))`, `ReliableWireFormat.reliableAckDatagram(ack = 0, ackBitfield
= 0)` and `"hi".toByteArray()`: seven datagrams, so it waits for seven origin drops. Then `peer` sends
`unreliableDatagram(byteArrayOf(9))`. Asserts: `received` is exactly `[[9]]`, `reliableReceived` is empty,
`client.linkQuality()` is `null`, the fake retransmit schedule's `scheduleCalls` is empty, and
`peer.nextBytes(200)` is `null`.

**Component tests.** `MultiConnectionUDPClientOriginScreenTest` is one class rather than additions to the
four existing client classes: the off-origin cases of every branch read as one list, and each existing
class keeps its own subject (framing, keepalive, probe, reliable). Per test, after the listener is started
with `channel(UNRELIABLE).actuateBytes` (the `0xA1` test uses `channel(RELIABLE_ORDERED).actuateBytes`):

- **Keepalive.** `foreign` sends `keepaliveDatagram()`. Assert one origin-drop event whose message contains
  both `foreign.localPort` and `peer.localPort`, and no event containing `Dropped keepalive from server`.
  Then `peer` sends it and a TRACE event `Dropped keepalive from server` appears.
- **Ping.** `foreign` sends `probePingDatagram(7)`; assert `peer.nextBytes(300)` is `null`. Then `peer`
  sends `probePingDatagram(8)` and `peer.nextBytes(2_000)` equals `probePongDatagram(8)`.
- **Pong.** `startProbe(5_000L)`, tick the fake probe schedule once, read the sequence from the ping at
  `peer`. `foreign` sends `probePongDatagram(seq)`; assert `client.linkQuality()` is `null`. Then `peer`
  sends it and `linkQuality()` becomes non-null within 2 s.
- **`0x90`.** `foreign` sends `unreliableDatagram(byteArrayOf(1))`; assert the handler's list is empty.
  Then `peer` sends `unreliableDatagram(byteArrayOf(2))` and the list becomes `[[2]]`.
- **`0xA1`.** `foreign` sends `reliableAckDatagram(ack = 0, ackBitfield = 0)`; assert the fake retransmit
  schedule's `scheduleCalls` is empty. Then `peer` sends it and `scheduleCalls` becomes non-empty.
- **Unframed.** `foreign` sends `"REGISTERED 2".toByteArray()`; assert no event at or above WARN. Then
  `peer` sends it and a WARN containing `Unframed/unknown datagram from server` appears.

**Edits to existing tests.**

- `MultiConnectionUDPClientFramingTest.kt:156-185` is rewritten in place to the last row of the table: the
  client's `0x90` arm no longer has "no origin screen", so the test that pinned it now mirrors the
  reliable counterpart (`MultiConnectionUDPClientReliableTest.kt:284-310`). Capture at `Level.DEBUG`, send
  the channel-`0x01` frame from `foreign`, wait for `events.hasEventAt(Level.DEBUG, "Datagram from
  unexpected origin")`, assert it, assert `events.none { it.formattedMessage.contains("on unsupported
  channel") }` and that `received` is empty; delete the "pins today's behaviour" comment. Add the imports
  `ch.qos.logback.classic.Level` and `com.spartanlabs.testing.support.webtools.udp.hasEventAt`.
- `MultiConnectionUDPClientReliableTest.kt:300` and `:306`: the fragment `"Reliable datagram from
  unexpected origin"` becomes `"Datagram from unexpected origin"`, the shared message. The test's name
  stays accurate.

The new test headers name their set ("Component set - …", "Gating set - …"), not a number.

**Commands.**

```
./gradlew.bat :webtools-udp:gatingTest --tests 'com.spartanlabs.testing.gating.webtools.udp.ClientOriginScreenGatingTest'
./gradlew.bat :webtools-udp:componentTest --tests 'com.spartanlabs.testing.component.webtools.udp.MultiConnectionUDPClientOriginScreenTest' --tests 'com.spartanlabs.testing.component.webtools.udp.MultiConnectionUDPClientFramingTest' --tests 'com.spartanlabs.testing.component.webtools.udp.MultiConnectionUDPClientReliableTest'
```

Run before the fix (expect the failures named above) and after it (all pass). After the fix also run the
module's sets that drive the client's listener, `./gradlew.bat :webtools-udp:gatingTest
:webtools-udp:componentTest :webtools-udp:integrationTest :webtools-udp:e2eTest
:webtools-udp:nonfunctionalTest`, and the API-reference gate CI runs, `./gradlew.bat
dokkaGeneratePublicationHtml`, because the class KDoc changes. The deterministic and uat sets do not
exercise the listener and are not run. Every existing test that feeds a client sends from the socket the
client targets (a fake `peer`, a raw server socket, the server's common-port socket, or the probe relay,
which replies from the socket the client targets), so none is expected to change beyond the two above.

## 5. Risks

- **A reply whose source differs from where the client sent.** A multi-homed host bound to the wildcard
  address whose routing picks another source address, or a server whose NAT mapping rebinds mid-session,
  now loses every inbound datagram, keepalive, probe and unreliable alike, as it already lost its reliable
  ones. The handshake still succeeds, so such a client connects and then hears nothing; the DEBUG line
  names both addresses, which is how it shows. The client cannot learn a second valid endpoint, and a
  looser match reopens the injection, so there is no client-side mitigation. The KDoc paragraph, the
  protocol document and the CHANGELOG state the requirement.
- **Log volume.** A flood of off-origin datagrams logs one DEBUG line each (parameterised, no payload),
  which the reliable arm already did and which is off at the usual INFO level.
- **Callers.** `receiveLoop` is private and started only by `ensureListening` (`:443-459`). The other
  readers of `serverEndpoint` (the keepalive, probe and retransmit schedule keys) are untouched. No
  consumer-reachable declaration changes (R-132 (Library Design — Open-Ended by Default)); only KDoc and
  behaviour for a stranger's datagram do.
- **Assumption.** Every supported deployment replies from the address and port the client sent to
  (loopback, a LAN, a NAT or container port mapping that translates both directions, an L4 balancer that
  presents one address). The server sends every datagram from one socket bound to its common port
  (`main/CommonChannel.kt:50,89-91`), and the reliable arm has depended on this since `2.0.0`. If it is
  false for a deployment, that deployment sees the first risk above.
- **Test timing.** The new tests wait on the listener's own log lines, not on sleeps. The negative
  assertions run after the listener has provably handled the stranger's datagram, so they do not depend
  on a delay; the events list is read while the listener appends, as in the sibling tests.

## 6. Version control

- **Branch:** `fix/issue-39-client-datagram-origin-check`, as above.
- **Commits, in order:**
  1. `fix: drop a client datagram whose source is not the server endpoint on every receive branch (Issue #39)`
     — `main/MultiConnectionUDPClient.kt` (the screen, the removed arm, comments and KDoc), the two new test
     files, the two edited test files, and this plan (`docs/plans/39-client-origin-screening/plan.md`).
  2. `docs: record the client origin screening in the protocol and architecture documents (Issue #39)`
     — `docs/webtools-udp-protocol.md`, `docs/webtools-udp-architecture.md` and the CHANGELOG entry above,
     once the landing's version is known.
  3. `build: bump webtools-udp to 2.0.1a (Issue #39)` — the version and its build-file comment. The README
     install snippet moves only when the bumped version is published, as for any bump.
- The untracked board files already in this folder (`plan.json`, `plan.js`, `dashboard.html`) belong to
  none of these commits.
