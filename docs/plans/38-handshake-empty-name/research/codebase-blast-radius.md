# Codebase blast radius for the malformed-`Iam` refusal

Inventory taken on master `85cfbbe` (the research branch differs from it only by `research/README.md`;
`git status` was clean). All `path:line` references are to that commit unless marked "after the change".
Paths under `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/` are abbreviated `main/`, and paths
under `webtools-udp/src/test/kotlin/com/spartanlabs/testing/` are abbreviated `test/`.

## 1. Question

What does the change touch (production files, tests at each of the five levels, KDoc blocks, docs), which
`File.kt:N` references in `docs/webtools-udp-protocol.md` are out of date and what are their correct ranges,
and which neighbouring code follows the new rule versus stays as it is?

The work: the server reads an unframed handshake by trimming the datagram text and splitting it on a single
U+0020. An `Iam` line is malformed when the name slot is missing or `""`, or when the credential slot is
present but `""`. A malformed line from an origin with no registration gets one fixed-text `REFUSED <reason>`
and nothing else happens. A malformed line from a registered origin is never answered `REFUSED`, so the
registered-origin lookup runs before the malformed check. Tokens past the credential slot stay ignored;
`Iam<TAB>bob` stays unrecognised; no public API changes; the register-before-send ordering (#49) is untouched.

## 2. Bottom line

- **Production edits are small.** `HandshakeProtocol.kt` and `HandshakeCoordinator.kt` change in code. Two
  public-facing files, `MultiConnectionUDPServer.kt` and `HandshakeWireFormat.kt`, change in KDoc only (the
  sentences "default admits every well-formed `Iam`" and "`REFUSED` is sent when `admit` refuses" become
  incomplete). Nothing else in `main/` recognises or tokenises an `Iam` line.
- **Only two existing tests send a malformed line.** `test/component/.../HandshakeCoordinatorTest.kt:238-246`
  and `test/integration/.../MultiConnectionUDPServerTest.kt:248` (the `"Iam"` element). Both flip. Two more
  tests (`HandshakeProtocolTest.kt:40-43,55-58`, `HandshakeProtocolGatingTest.kt:27-30`) feed `listOf("Iam")` to
  `parseHandshake`; they stay or change depending only on whether `parseHandshake` keeps returning
  `Result<Handshake>` with an `IllegalArgumentException`.
- **21 of the 37 line references in the protocol doc are out of date (or partly so), 16 are correct.** The doc's
  references were measured on the pre-#40 tree (`bc6f8b1`); the channel-byte guard (`cb55d5f`) and the Stage 4
  KDoc commit (`101de90`) then shifted `HandshakeCoordinator`, `MultiConnectionUDPClient`,
  `MultiConnectionUDPServer`, `ReliableChannelEngine`, `TransportWireFormat` and `UdpChannel`. §5 gives the
  correct current range for each. All references into `HandshakeCoordinator.kt`, `HandshakeProtocol.kt`,
  `HandshakeWireFormat.kt` and `MultiConnectionUDPServer.kt` must be recomputed after the code and KDoc edits.
- **Two references use a shorthand a `File.kt:N` sweep misses:** `` (`:91-99`) `` at protocol-doc line 81 and
  `` (`:99-113`) `` at line 187. They are counted in the 37.
- **Editing hazard confirmed.** `test/component/.../HandshakeCoordinatorTest.kt:577` holds raw NUL (0x00) and
  SOH (0x01) bytes in a string literal. Recursive `rg` and the `Grep` tool silently return nothing for the whole
  file; `grep` needs `-a`. No other tracked source file has one. `README.md` and both docs are CRLF in the working
  tree (index LF).
- **One call is still open** (Considerations C1): what a registered origin gets for a malformed line. The
  inventory reports what changes under each option; the recommendation is "no reply, log line".

## 3. Production code

### 3.1 Path from the socket to the handshake

| Step | Location (`85cfbbe`) |
|---|---|
| Listener loop | `main/MultiConnectionUDPServer.kt:326` (`receiveLoop`), `:330` `coordinator.accept(inbound.origin, inbound.bytes, inbound.text)`; a `Result.failure` lands in `.onFailure` at `:331-338`, which logs `WARN "Failed to handle incoming datagram: {}"` **with the exception** (`:336`), i.e. a stack trace |
| Decode and trim | `main/CommonChannel.kt:61-80`; trim at `:78` (`String(bytes, UTF_8).trim()`) |
| Entry | `main/HandshakeCoordinator.kt:146` `accept(origin, bytes, text)`: liveness stamp `:147-152`, then `classify` `:156` |
| Tag switch | `:159` `DatagramType.ofTagByte(bytes.getOrNull(0))`; `null` branch `:221`; `byte0 >= 0x80` reserved-tag drop `:222-225` |
| Handshake recognition | `:227` `HandshakeProtocol.isHandshake(text.split(' ')) -> handleHandshake(origin, text.split(' '))` (the split runs twice) |
| Handshake body | `:243-293` `handleHandshake`: `parseHandshake` `:244`, `extraTokenCount` + DEBUG `:245-246`, `findByOrigin` `:248`, `admit` `:260`, `Refused` reply `:264-271`, same-name supersede `:277-287`, register + `REGISTERED 2` + `onRegistered` `:288-291` |
| Non-`Iam` unframed text | `:229-231` WARN "Unframed datagram from {}; sender may be pre-2.0, dropping" |
| Two-argument overload | `:240-241` `accept(origin, text)`: tests only; production has the single call at `MultiConnectionUDPServer.kt:330`. It **does not trim** (see Pitfall P2) |

Today's order inside `handleHandshake` is parse, then origin lookup. A line that fails to parse never reaches
the lookup, which is why a bare `Iam` from a registered origin gets nothing while `Iam  x` gets `REGISTERED 2`.

### 3.2 Call sites of the `HandshakeProtocol` API

| Symbol | Production | Tests |
|---|---|---|
| `HandshakeProtocol.parseHandshake` | `main/HandshakeCoordinator.kt:244` (only caller) | `test/deterministic/.../HandshakeProtocolTest.kt:20,27,35,42,47,52,57`; `test/gating/.../HandshakeProtocolGatingTest.kt:17,22,29` |
| `HandshakeProtocol.isHandshake` | `main/HandshakeCoordinator.kt:227` (only caller) | `HandshakeProtocolTest.kt:96-102`; `HandshakeProtocolGatingTest.kt:40-42` |
| `HandshakeProtocol.extraTokenCount` | `main/HandshakeCoordinator.kt:245` (only caller) | `HandshakeProtocolTest.kt:37,64-66,71,76-77`; `test/component/.../HandshakeCoordinatorTest.kt:137` |
| `Handshake` (`internal data class`) | declared `main/HandshakeProtocol.kt:86`; constructed `:58`; return type `:55`; destructured `main/HandshakeCoordinator.kt:244` | constructed `HandshakeProtocolTest.kt:3,20,26,34`; read via `.name` / `.credential` `HandshakeProtocolGatingTest.kt:17,22-24` |
| `HandshakeProtocol.VERB`, `MIN_TOKENS`, `NAME_INDEX`, `CREDENTIAL_INDEX` | `main/HandshakeProtocol.kt:21,33,36,39,56-58,69,77` (private or internal to the object) | none |

`parseHandshake` conflates "credential absent" and "credential present but empty" at `main/HandshakeProtocol.kt:58`
(`tokens.getOrElse(CREDENTIAL_INDEX) { "" }`). The new rule needs them apart; `tokens.getOrNull(CREDENTIAL_INDEX)`
distinguishes them without any new type (idiomatic Kotlin; the representation is Question 2's call).

### 3.3 Files and verdicts

| File | Verdict | Why |
|---|---|---|
| `main/HandshakeProtocol.kt` | **changes (code + KDoc)** | the validity rule lives here (pure, `internal`); KDoc `:3-18`, `:32-39` (`MIN_TOKENS` comment "verb plus the name"), `:41-59`, `:80-86` |
| `main/HandshakeCoordinator.kt` | **changes (code + KDoc + inline comments)** | order becomes lookup-first and a malformed branch; KDoc `:11-20`, `:58-62` (`@param admit`), `:141-145` (`@return` says a malformed recognised handshake is a `Result.failure`); inline comments `:248-252`, `:256-259` |
| `main/MultiConnectionUDPServer.kt` | **KDoc only** | `:15-23` (refusal only via `admit`), `:43-54` ("default admits every well-formed `Iam`"), `:355-383` (`admit` KDoc: "well-formed", `@param name`, `@param credential` "empty string if the client sent `Iam <name>`", "Called only for a first `Iam` from an unknown origin") |
| `main/HandshakeWireFormat.kt` | **KDoc only** | `:35-39` ("may answer an `Iam` with `REFUSED <reason>` ... see `admit`"), `:52-57` (`REFUSED_REPLY`: "when `admit` returns `Refused`"); `:14-22` stays true ("An absent credential is delivered to `admit` as the empty string") |
| `main/Admission.kt` | stays | `:13-18` describes `Refused` as `admit`'s result; still accurate. The reason text for a malformed line is not an `Admission` |
| `main/HandshakeRefusedException.kt` | stays | `:3-10` "Distinct from a timeout ... and from a malformed reply" is reply-side |
| `main/CommonChannel.kt` | stays | `:78` trim and `:75-77` comment unchanged |
| `main/MultiConnectionUDPClient.kt` | stays (belongs to #52) | `handshake()` KDoc `:276-297` and `:302` `handshakeMessage(...)` |
| `main/HandshakeWireFormat.handshakeMessage` (`:59-73`) | stays (belongs to #52 / #53) | builds `Iam ` + name unvalidated; its KDoc "must not contain whitespace" is the client-side contract |
| `main/DatagramType.kt`, `Connection.kt`, `UDPConnection.kt`, `DisconnectReason.kt`, `Registrations.kt`, `UDPSendReceiveServer.kt` | stay | mention `Iam` only in prose (peer origin, tag-space table) or not at all |
| `webtools-udp/build.gradle.kts:92` (`version = "2.0.0"`) | not inventoried | version policy is a planning call; no `CHANGELOG` file exists in the repo |

## 4. Tests

### 4.1 Tests that send a bare `Iam` or an `Iam` with an empty slot, or assert what happens to one

Levels are by package segment and `@Tag`; the Gradle tasks in root `build.gradle.kts:44-62` select on the tag.

| `path:line` | Level (package / `@Tag`) | Sends | Asserts today | Verdict |
|---|---|---|---|---|
| `test/component/webtools/udp/HandshakeCoordinatorTest.kt:238-246` "a nameless Iam fails and registers nothing" | 2 / `testing.component` / `component` | `accept(originA, "Iam")` | `isFailure`; `coordinator.size == 0`; `sent.isEmpty()` | **changes.** Expect `sent == [REFUSED <reason> to originA]`; `size == 0`; `created`, `admitCalls`, `registeredNames` empty. The `isFailure` assertion is replaced by whatever `handleHandshake` returns for an unknown origin's refusal (Question 2). Rename: it no longer "fails" |
| `test/integration/webtools/udp/MultiConnectionUDPServerTest.kt:245-259` (`@Order(11)`), element `"Iam"` at `:248` | 3 / `testing.integration` / `integration` | bare `"Iam"` from a fresh socket via `assertNoReplyTo` (`:94-102`), next to `""`, `"   "`, `"HELLO world"` | `SocketTimeoutException` within 500 ms (`NO_REPLY_TIMEOUT_MILLIS`, `:380`) | **changes.** Drop `"Iam"` from the list (the other three stay silent); add an exchange through `handshakeFrom` (`:89-92`) expecting `REFUSED <reason>` (receive buffer 1024 B, `:73`). A refused line adds no connection, so later `@Order` tests that count `connectedClients` are unaffected |
| `test/deterministic/webtools/udp/HandshakeProtocolTest.kt:40-43` "parseHandshake rejects a verb-only line" | 4a / `testing.deterministic` / `deterministic` | `parseHandshake(listOf("Iam"))` | `isFailure` | stays if `parseHandshake` still returns `Result.failure` for a missing name slot; changes only if the parse outcome type changes (Question 2) |
| `HandshakeProtocolTest.kt:55-58` "parseHandshake failure carries an IllegalArgumentException" | 4a | `parseHandshake(listOf("Iam"))` | `assertIs<IllegalArgumentException>(exceptionOrNull())` | stays only if the failure type stays `IllegalArgumentException` (Question 2) |
| `HandshakeProtocolTest.kt:45-48`, `:75-78` | 4a | `emptyList()`; `extraTokenCount(listOf("Iam"))` | failure; `0` | stays (not malformed-slot cases) |
| `HandshakeProtocolTest.kt:94-103` "isHandshake truth table" | 4a | `listOf("Iam")` among others | `isHandshake(listOf("Iam")) == true` (`:97`) | **stays**: a bare `Iam` is still a handshake line; it is malformed, not unrecognised. Add `listOf("Iam", "")` (true) and `listOf("Iam\tbob")` (false) |
| `test/gating/webtools/udp/HandshakeProtocolGatingTest.kt:27-30` "a nameless Iam is rejected" | 1 / `testing.gating` / `gating` | `parseHandshake(listOf("Iam"))` | `isFailure` | same rule as `HandshakeProtocolTest.kt:40-43` |
| `test/component/.../HandshakeCoordinatorTest.kt:150-157` "admit receives an empty credential for a bare Iam" | 2 | `"Iam alice"` (name present, credential **absent**) | `admit` gets `("alice", originA, "")` | **stays.** The title says "bare Iam" but the line is not malformed; it is the pin that an absent credential is not malformed |
| `test/integration/.../MultiConnectionUDPServerAdmissionTest.kt:93-100` "the default branch sees an empty credential for a bare Iam" | 3 | `"Iam alice"` | `REGISTERED 2`; `seenCredentials.contains("")` | **stays** (same wording trap) |
| `HandshakeCoordinatorTest.kt:129-139` "the credential slot is claimed and only tokens past it are ignored" | 2 | `"Iam carol 10.0.0.9 junk"` | registers `carol`, credential `10.0.0.9`, `extraTokenCount == 1` | **stays**; natural neighbour for the `Iam bob tok  x` case |
| `test/integration/.../MultiConnectionUDPClientProbeTest.kt:64` | 3 | (fake peer that replies) | replies `REGISTERED 2` to `text == "Iam"` or `startsWith("Iam ")` | **stays**: a stand-in server for client tests, not a parser under test |
| `test/integration/.../MultiConnectionUDPServerTest.kt:250` | 3 | `"Iam " + "z".repeat(4096)` | well-formed; server stays responsive | stays |

No existing test sends a malformed line **from a registered origin**; the registered-origin behaviour has no
current coverage to flip. The other ~50 `accept(origin, "Iam <name>")` calls and every `handshake("<name>")`
call use a non-empty name and no trailing space, so they are unaffected (`grep` for `"Iam…  …"`, trailing-space
and `\t`/`\n` literals returned nothing). The `uat` package holds only a comment mentioning `Iam`.

### 4.2 Where new cases naturally belong

| Level | Existing class | New cases (not written here) |
|---|---|---|
| 1 gating | `test/gating/.../HandshakeCoordinatorGatingTest.kt` (`@Tag("gating")`; `coordinator(replies, ...)` helper `:33-49`, refusal test `:51-63`) | one smoke: malformed `Iam` from an unknown origin gets `REFUSED`, `size == 0` |
| 1 gating | `test/gating/.../HandshakeProtocolGatingTest.kt` | one empty-name case beside `:27-30` |
| 2 component | `test/component/.../HandshakeCoordinatorTest.kt` (fakes: `recordingAdmit` `:57-62`, `admitCalls`, `sent`, `created`, `registeredNames`, `disconnects`) | bare `Iam`; empty name (`Iam  x`); empty credential (`Iam bob  tok`); `Iam bob tok  x` accepted with credential `tok`; absent credential accepted; malformed line does not supersede a same-name registration (`disconnects` empty); `admit` and `onRegistered` never called; reply addressed only to the origin; registered origin with each of the three lines (registration untouched, no `REFUSED`; reply per Consideration C1); log content via `captureLogsOf(HandshakeCoordinator::class.java)` (`test/support/.../LogCapture.kt:23`) asserting the raw line is absent |
| 2 component | `test/component/.../FramingComponentTest.kt:179-191` (existing "unframed hello ... pre-2.0 WARN" test) | `Iam<TAB>bob` is not a handshake: takes the unframed WARN path, registers nothing |
| 3 integration | `test/integration/.../MultiConnectionUDPServerTest.kt:245-259` (flip) and `MultiConnectionUDPServerAdmissionTest.kt` (raw-socket `exchange`, `:55-62`; `GuardedServer.seenCredentials`) | the flipped bare-`Iam` exchange; `admit` is never consulted for a malformed line |
| 4a deterministic | `test/deterministic/.../HandshakeProtocolTest.kt` | truth table of the validity rule over token lists, including `["Iam","bob","tok","","x"]` accepted and `["Iam\tbob"]` unrecognised |
| 4b e2e (optional) | `test/e2e/.../MultiConnectionUDPHandshakeRefusalE2ETest.kt` (its tests all use `MultiConnectionUDPClient`; no raw-socket helper yet) | raw `DatagramSocket` send of a malformed line and a `REFUSED` read. It cannot use `MultiConnectionUDPClient.handshake("", ...)` because #52 will make that fail locally. `MultiConnectionUDPServerE2ETest.kt:44-94` already has a private raw `Client` (`send`, `recv`) if reuse is preferred |
| 4c nonfunctional (optional) | `test/nonfunctional/.../MultiConnectionUDPServerAdmissionNonFunctionalTest.kt:103-125` ("a burst of refusals leaves no registrations") and `HandshakeNonFunctionalTest.kt:62-85` | a malformed-line burst leaves no registrations and the server still accepts a good client |

## 5. Line references in `docs/webtools-udp-protocol.md`

37 references (35 written `File.kt:N`, plus the two shorthand ones at lines 81 and 187), shown as 39 rows because
references 4 and 22 are split into their two ranges (4a/4b, 22a/22b). "Edited" is the file's status under §3.3
(code or KDoc edits, which shift lines). For an edited file the current range is what the
reference is **today**; it must be recomputed after the change, from the anchor in the last column. Not-edited
files can be fixed now and stay fixed.

Verdict key: OK = correct on `85cfbbe`; STALE = points at the wrong lines.

| # | Doc line | Reference | Prose says it shows | On `85cfbbe` | Correct current range | File edited by this work? |
|---|---|---|---|---|---|---|
| 1 | 28 | `TransportWireFormat.kt:78-149` | big-endian integer codecs (keepalive, probe, unreliable framing) | STALE (+3 from the Stage 4 KDoc commit) | `81-152` (`keepaliveDatagram` to `unreliablePayloadOf`) | no |
| 2 | 29 | `ReliableWireFormat.kt:13-29` | the `0xA0`/`0xA1` byte layouts | OK | `13-29` | no |
| 3 | 40 | `MultiConnectionUDPServer.kt:540` | `COMMON_LISTEN_PORT = 9998` | STALE | `548` | **yes (KDoc)**: recompute after; anchor `const val COMMON_LISTEN_PORT` |
| 4a | 42 | `MultiConnectionUDPClient.kt:16-29` | "one socket for everything" KDoc | OK | `16-29` | no |
| 4b | 42 | `MultiConnectionUDPClient.kt:194-195` | the single `DatagramSocket` field | STALE | `200-201` | no |
| 5 | 44 | `CommonChannel.kt:71,89-91` | origin from packet source; `send` to origin | OK | `71`, `89-91` | no |
| 6 | 66 | `HandshakeWireFormat.kt:14-22,59-73` | credential-slot KDoc; `handshakeMessage` | OK | `14-22`, `59-73` | **yes (KDoc)**: recompute after; anchors `### The optional credential slot`, `fun handshakeMessage` |
| 7 | 68 | `CommonChannel.kt:78` | `.trim()` of the datagram text | OK | `78` | no |
| 8 | 69 | `HandshakeCoordinator.kt:202` | `text.split(' ')` | STALE | `227` | **yes**: recompute after; anchor `isHandshake(text.split(' '))` |
| 9 | 72 | `HandshakeProtocol.kt:55-69` | `parseHandshake` and `extraTokenCount` | OK | `55-69` | **yes**: recompute after; anchors `fun parseHandshake`, end of `fun extraTokenCount` |
| 10 | 72 | `HandshakeCoordinator.kt:218-221` | extra tokens ignored at DEBUG | STALE | `243-246` | **yes**: anchor `log.debug("Ignoring {} extra handshake token(s)"` |
| 11 | 80 | `HandshakeWireFormat.kt:79` | `registeredMessage()` | OK | `79` | **yes (KDoc)**: recompute after; anchor `fun registeredMessage` |
| 12 | 81 | `:91-99` (shorthand, same file) | `registeredProtocolVersion` | OK | `91-99` | **yes (KDoc)**: anchor `fun registeredProtocolVersion` |
| 13 | 86 | `HandshakeWireFormat.kt:117-120` | `refusedMessage` | OK | `117-120` | **yes (KDoc)**: anchor `fun refusedMessage` |
| 14 | 88 | `HandshakeCoordinator.kt:218-267` | the whole server handshake handling | STALE | `243-292` (function is `243-293`) | **yes**: recompute after; anchor `private fun handleHandshake` to its closing `}` |
| 15 | 98 | `MultiConnectionUDPClient.kt:790` | default timeout 4000 ms | STALE | `835` (`HANDSHAKE_TIMEOUT_MILLIS`) | no |
| 16 | 119 | `DatagramType.kt:9-27` | the tag catalogue table | OK (brackets the table, which is `14-26`) | `9-27` | no |
| 17 | 134 | `KeepAlive.kt:10-44` | poll clamp and `isDue` | OK | `10-44` | no |
| 18 | 137 | `HandshakeCoordinator.kt:138-145` | liveness stamp before classification | STALE | `146-153` (`accept` through `return classify`) | **yes**: anchor `fun accept(origin, bytes, text)` |
| 19 | 152 | `HandshakeCoordinator.kt:155-166` | `0x81` answered only from a registered origin | STALE | `163-174` | **yes**: anchor `DatagramType.PROBE_PING -> {` |
| 20 | 153 | `MultiConnectionUDPClient.kt:524-528` | client answers every valid `0x81` inline | STALE | `536-540` | no |
| 21 | 157 | `Rtt.kt:11-18` | RTT constants and loss horizon | OK | `11-18` | no |
| 22a | 170 | `HandshakeCoordinator.kt:176-183` | `0x90` branch (trim in the classifier) | STALE | `184-191` | **yes**: anchor `DatagramType.UNRELIABLE -> {` |
| 22b | 170 | `HandshakeCoordinator.kt:270-291` | `deliverData`, text handler | STALE (grew with the channel guard) | `301-335` | **yes**: anchor `private fun deliverData` |
| 23 | 170 | `MultiConnectionUDPClient.kt:535-542` | client `0x90` branch, text trimmed | STALE (branch grew with the channel guard) | `547-571`; the trim is `568` | no |
| 24 | 172 | `HandshakeCoordinator.kt:271-272` | `0x90` from an unregistered origin, DEBUG | STALE | `302-303` | **yes**: anchor `val registration = registrations.findByOrigin(origin)` in `deliverData` |
| 25 | 186 | `ReliableChannelEngine.kt:99-104` | malformed `0xA0` WARN, never throws | STALE (+5 from #40 KDoc) | `104-109` | no |
| 26 | 187 | `:99-113` (shorthand, same file) | ack processing before delivery on inbound `0xA0` | STALE | `104-118` | no |
| 27 | 190 | `HandshakeCoordinator.kt:185-189` | `0xA0`/`0xA1` from an unregistered origin, DEBUG, no engine | STALE | `193-198` (branch header through the `reg == null` drop) | **yes**: anchor `DatagramType.RELIABLE_DATA, DatagramType.RELIABLE_ACK -> {` |
| 28 | 204 | `ReliableChannelEngine.kt:150-165` | standalone `0xA1` on the tick | STALE | `155-170` (`onRetransmitTick`) | no |
| 29 | 245 | `MultiConnectionUDPClient.kt:485` | `IllegalStateException("client stopped")` | STALE | `493` | no |
| 30 | 247 | `HandshakeCoordinator.kt:440-441` | server reliable send to an unregistered peer | STALE | `486-487` | **yes**: anchor `override fun sendReliable(peer` |
| 31 | 258 | `ReliableChannelEngine.kt:71-85` | first transmission inside `send`; failed send swallowed | OK | `71-85` | no |
| 32 | 261 | `ReliableRetransmitBuffer.kt:73-101` | `onAck` | OK | `73-101` | no |
| 33 | 268 | `ReliableRetransmitBuffer.kt:103-136` | `dueForRetransmit` | OK | `103-136` | no |
| 34 | 268 | `ReliableChannelEngine.kt:150-165` | tick behaviour | STALE | `155-170` | no |
| 35 | 275 | `ReliableRtoEstimator.kt:39-59` | `onRttSample`, `currentRtoMillis` | OK | `39-59` | no |
| 36 | 278 | `UdpChannel.kt:39-50` | `actuateBytes` / `actuate` (trim) | STALE (+2) | `41-52` | no |
| 37 | 352 | `HandshakeWireFormat.kt:29-33` | the cross-version table | OK | `29-33` | **yes (KDoc)**: recompute after; anchor `### The \`REGISTERED <version>\` reply` table |

Totals: of the 37 references, 16 are fully correct and 21 are stale or partly stale (11 of the 21 point into
files this work does not edit; 10 point into edited files). Six correct references point into edited files
(rows 6, 9, 11, 12, 13, 37) and must be re-checked after the change. Cause (from `git blame` and `git show`): every
reference was written in `3b89a1a` against the tree at `bc6f8b1` (the Issue #34 fix, 2026-09-24):
`HandshakeCoordinator.kt:202` and `MultiConnectionUDPServer.kt:540` both match `bc6f8b1` exactly. Afterwards
`cb55d5f` (Issue #40; changed 86 lines of `HandshakeCoordinator`, 73 of the client, 5 of `ReliableChannelEngine`,
and `ReliableWireFormat`) and `101de90` (Stage 4 KDoc: `DatagramType`, `MultiConnectionUDPClient`,
`MultiConnectionUDPServer`, `ReliableWireFormat`, `TransportWireFormat`, `UdpChannel`) shifted lines. Files with no change since `bc6f8b1`: `CommonChannel`, `KeepAlive`, `HandshakeProtocol`,
`HandshakeWireFormat`, `ReliableRetransmitBuffer`, `ReliableRtoEstimator`, `Rtt`.

Recompute procedure after the code and KDoc edits: finish the `main/` edits first, then regenerate each "edited"
row from its anchor (`grep -n` on the anchor, then read the span), and update the doc last. The doc passages
that also change in prose are §3 (`:58-101`) and §8 (`:315-342`, row `:321`); line numbers inside the doc
itself are not referenced from elsewhere (no anchors or `§` links point at it).

### 5.1 Other line references (reported only; not refreshed by this work)

| File:line | Reference | Verdict on `85cfbbe` | Correct range |
|---|---|---|---|
| `docs/webtools-udp-architecture.md:52` | `MultiConnectionUDPServer.kt:254` | STALE. Intended the server's `HandshakeCoordinator` field (it was `253-254` on `bc6f8b1`); `:254` is now `retransmitScheduler` | the `private val coordinator = HandshakeCoordinator(` line, `260` today (shifts again if `MultiConnectionUDPServer.kt` KDoc grows) |
| `docs/webtools-udp-architecture.md:52` | `Registrations.kt:50,87` | OK (`class Registration`, `class Registrations`) | `50`, `87` |
| `docs/webtools-udp-architecture.md:266` | `UDPSendReceiveServer.kt:30-38` | OK (the "No DeliveryMode / UdpChannel surface" KDoc) | `30-38` |
| `README.md` | none | no `File.kt:N` references | n/a |
| `test/integration/.../ChannelByteGuardIntegrationTest.kt:25` (comment) | `FramingNonFunctionalTest.kt:140-149` | STALE and misaimed: `:140-149` is inside the stranger-origin guard test (probe schedule fake, `bindBytes`), not a raw-server shape; the client-side raw-peer fuzz is `FramingNonFunctionalTest.kt:187-215` | `187-215` if the intent is "raw-server shape for the client side" |

`main/` source comments contain no `File.kt:N` references (they cite plan sections such as
`docs/plans/40-channel-byte-guard/architecture.md §6`).

## 6. Prose passages that state how an `Iam` line is parsed, what `admit()` gets, when `REFUSED` is sent, or which lines get no reply

| Location | What it says today | Verdict |
|---|---|---|
| `docs/webtools-udp-protocol.md:62-66` | name "must be non-empty and whitespace-free"; credential optional, whitespace-free | update: the server now enforces non-empty; whitespace beyond U+0020 is a known gap (#53) |
| `:68-72` | trim + single U+0020 split; `parseHandshake` "requires `tokens[0] == "Iam"` and at least two tokens"; extra tokens ignored at DEBUG | update: the slot rule |
| `:74-78` | "As-built quirk ... `Iam  x` registers name `""` ... Described here only — this stage does not fix it" | **rewrite**: remove the quirk; describe the new rule; describe the remaining whitespace gap as known and link #53 |
| `:85-86` | `REFUSED <reason>` or bare `REFUSED` | add the protocol-level refusal and its fixed text (informative, not contract) |
| `:88-95` | server handshake handling: registered origin repeats `REGISTERED 2`; new origin runs `admit` first | update: malformed branch and the lookup-before-malformed order |
| `:97-101` | client `handshake()` outcomes | stays (a refused malformed line is still `REFUSED` -> `HandshakeRefusedException`) |
| `:320` | "Byte 0 `< 0x80` and the text does not start with the `Iam` token" -> WARN pre-2.0, dropped | stays (tighten to "first space-separated token is not exactly `Iam`") |
| `:321` | "`Iam` with no name token (a bare `Iam`) ... parse failure, logged at WARN by the listener ('Failed to handle incoming datagram'), nothing registered, no reply" | **rewrite** and add rows for an empty name slot, an empty credential slot, and the registered-origin case |
| `docs/webtools-udp-architecture.md:112` | handshake node: "handshake state machine: admit, supersede, register, reply REGISTERED 2 or REFUSED" | small wording update (registered origin first; malformed -> `REFUSED`) |
| `docs/webtools-udp-architecture.md:45` | `HandshakeProtocol` row: "`Iam` parsing" | stays |
| `docs/webtools-udp-architecture.md:61,88-89` | listener thread runs "the handshake including `admit`/`onClientConnect`"; `REGISTERED`/`REFUSED` reply thread | stays |
| `README.md:104-108` | message table (`Iam <name>`, `Iam <name> <credential>`, `REFUSED <reason>`) | update: one row or note for the malformed-line refusal |
| `README.md:123-127` | retransmit repeats `REGISTERED`; "`<credential>` (`tokens[2]`, optional) ... any token after it is ignored" | update: empty slot |
| `README.md:129-135` | `admit` -> `Refused` replies `REFUSED`; `admit` only for a first `Iam` from an unknown origin | update: a malformed line never reaches `admit` |
| `README.md:217-250` ("Handshake screening & credentials") | "By default the server accepts every well-formed `Iam`" (`:219`); absent credential arrives as the empty string (`:244-246`) | update `:219` to define "well-formed"; `:244-246` stays |
| `main/` KDoc | see §3.3 | per §3.3 |

## 7. Intent recorded in history

1. **Single-U+0020 split.** `String.split(' ')` entered with the multi-connection server (`4b38589`, 2026-08-25)
   and became the shared classifier in `05cde63` (Issue #1 Tier 2; `docs/plans/1-nat-traversal/plan-tier-2.md:398`).
   `docs/plans/11-handshake-refusal-and-credential/plan.md` states the invariant: "The entire wire format is
   space-split (`text.split(' ')`, and `handshakeMessage`'s KDoc already requires a whitespace-free `<name>`)", and rejects a
   length-delimited credential because it "breaks the 'everything is space-split' invariant the whole format
   relies on". So the single-space split is a deliberate wire-format simplicity choice; the empty tokens it
   yields were never specified. Issue #38 offers "split on runs of whitespace" as one fix; the work keeps the
   single-space split and rejects empty slots instead.
2. **Parse-before-lookup.** `751d0cb` (2026-09-03, Tier 1 test hardening) introduced `HandshakeCoordinator.handle`
   as `parseHandshake(tokens).flatMap { ... findByOrigin ... }`, with the KDoc "A well-formed `Iam` from an origin
   that is already registered re-sends its existing reply", and in the same commit the integration test
   `assertNoReplyTo("Iam")`. The order is not argued in any plan: parsing first yields the name that the
   new-origin branch needs, and the lookup only needs the origin. `f4fed5a` (#11) kept the shape and placed
   `admit` inside the `?: run` branch. The retransmit branch therefore only ever answered **well-formed**
   lines, and a bare `Iam` got silence as a side effect of failing to parse. Note the lookup ignores `name`
   entirely: `Iam mallory` from alice's registered origin already gets `REGISTERED 2`.
3. **A retransmit is never re-screened.** Origin: Tier 1 plan §1.5 "De-duplicate retransmitted handshakes"
   (`docs/plans/1-nat-traversal/plan-tier-1.md:81`), to avoid allocating a second port pair. `f4fed5a` added the
   rule for `admit`: inline comment `HandshakeCoordinator.kt:250-252` ("re-running a non-deterministic `admit()` (e.g. a
   capacity gate) on a stray retransmit could refuse an already-admitted client and desync the two sides"),
   plan #11 §2.3 "Re-run `admit` on every retransmit" rejected for the same reason
   (`docs/plans/11-handshake-refusal-and-credential/plan.md`, alternatives table). It guards against
   **re-running `admit()`**, not against re-validating the line's shape; a shape check is deterministic, so it
   does not reopen the desync the rule protects against. The requirement still fixes the order, because a
   `REFUSED` to a registered client would desync it.

## 8. Adoption sweep: other code that recognises or tokenises `Iam` lines

| Site | Does | Verdict |
|---|---|---|
| `main/HandshakeCoordinator.kt:227` | splits the text twice (once for `isHandshake`, once for `handleHandshake`) | follows the new rule (the split itself is unchanged; hoisting it into one `val` is optional tidiness) |
| `main/HandshakeProtocol.kt:55-77` | `parseHandshake`, `extraTokenCount`, `isHandshake` | `parseHandshake` changes; `extraTokenCount` and `isHandshake` stay (`isHandshake` stays verb-only so `Iam<TAB>bob` remains unrecognised) |
| `main/HandshakeWireFormat.kt:72-73` `handshakeMessage` | builds the line; `handshakeMessage("")` returns `"Iam "` (a bare `Iam` after the server's trim) | stays; client-side validation is #52 |
| `main/HandshakeWireFormat.kt:91-99` `registeredProtocolVersion` | tokenises the **reply** (`rest.contains(' ')`) | stays (reply parsing, different rule) |
| `main/MultiConnectionUDPClient.kt:302` | `handshakeMessage(name, credential)` inside `runCatching` | stays (#52) |
| `main/CommonChannel.kt:78` | `.trim()` | stays |
| `main/UDPSendReceiveServer.kt` | no handshake | stays |
| log lines that print the name: `main/HandshakeCoordinator.kt:261,265,278,290` | `'{}'` with the name | belong to #53 (CR/LF in a name reaches INFO logs unescaped). The new malformed-line log must print neither the line nor its tokens |
| `test/integration/.../MultiConnectionUDPClientProbeTest.kt:64` | test responder, `text == "Iam" \|\| startsWith("Iam ")` | stays |
| `test/integration/.../MultiConnectionUDPProtocolVersionTest.kt:35-45` `answerOnce` | replies to one datagram without tokenising | stays |
| `test/deterministic/.../HandshakeWireFormatTest.kt:19-28` | `handshakeMessage("bob"[, "tok"])` | stays |

No other production or test code tokenises an `Iam` line.

## 9. Editing hazards

- **NUL and SOH in `test/component/webtools/udp/HandshakeCoordinatorTest.kt:577`.** The line is
  `assertTrue(coordinator.accept(originA, byteArrayOf(0x00, 0x01), "<NUL><SOH>").isSuccess)`; the string literal holds
  raw bytes 0x00 0x01. First NUL at byte offset 22142 of 42081. Observed on this machine:
  `file` reports `data`; `git ls-files --eol` reports `i/-text w/-text`; `git diff` still treats the file as text
  (`--numstat` shows `1097 0`; consistent with git probing only the first 8000 bytes for NUL, which this file's
  first NUL, at 22142, is past); recursive `rg` prints **nothing** for the file
  (all 62 `Iam alice` matches vanish) and explicit `rg file` prints "binary file matches"; `grep` prints "Binary file
  ... matches" and needs `-a`. Anything that rewrites the file must keep bytes 0x00 and 0x01 and must not
  normalise them; verify with `grep -a -P '\x00' <file>` and a byte-for-byte `git diff` review limited to the
  intended hunks.
- **No other tracked source has a raw control byte.** The only other tracked text file is the historical
  `docs/plans/11-handshake-refusal-and-credential/plan.md:696` (a raw NUL inside a sentence about reason
  sanitisation). It is not edited by this work.
- **Line endings.** `core.autocrlf=true` here. `git ls-files --eol` shows the working-tree copies of `README.md`,
  `docs/webtools-udp-protocol.md`, `docs/webtools-udp-architecture.md` (and 47 other tracked files) as CRLF while
  the index is LF; `*.kt` is forced to LF by `.gitattributes`. An edit tool that writes LF into a CRLF file produces
  a whole-file diff on some setups; check `git diff --stat` after editing a doc.
- **Non-ASCII text.** The docs and KDoc contain `—`, `→`, `α`, `≥`. Use a UTF-8-safe edit path (PowerShell 5.1
  default encodings corrupt them). No file has a BOM.
- **Binary-skip search.** Because of the NUL file, any "find all callers" sweep through the `Grep` tool misses
  `HandshakeCoordinatorTest.kt`. This inventory searched it with `grep -a`.

## 10. Pitfalls

- **P1. "bare Iam" names two different things in the tests.** `HandshakeCoordinatorTest.kt:151` and
  `MultiConnectionUDPServerAdmissionTest.kt:94` use "bare `Iam`" for `Iam <name>` with no credential. Do not
  flip them; they pin that an absent credential is accepted.
- **P2. Component tests bypass the trim.** The server trims in `CommonChannel.kt:78`; `HandshakeCoordinator.accept(origin, text)`
  (`:240`) and the `bytes, text` form do not. Kotlin's `split(' ')` keeps a trailing empty string for text ending
  in the delimiter, so a component test that passes `"Iam bob "` sees an empty credential slot (malformed), while the
  same bytes on the wire are trimmed to `Iam bob` (accepted). Test inputs must be pre-trimmed, or the trim case
  must be tested at level 3.
- **P3. A bare `Iam` is also what `handshake("")` sends** (`Iam ` trims to `Iam`), and `handshake("", credential="tok")`
  sends `Iam  tok` (empty name). After #52 neither can be produced by the library client, so no end-to-end test
  through `MultiConnectionUDPClient` can reach the new branch; use a raw socket.
- **P4. Return value drives the listener log.** A `Result.failure` out of `handleHandshake` is logged at WARN with a
  stack trace by `MultiConnectionUDPServer.kt:336`. Today a bare `Iam` takes that path. If the malformed
  branch returns `Result.failure`, every refused line also WARNs with a stack trace and the `REFUSED` reply is still
  sent; if it returns the `send` result (as the `admit` refusal does, `:267-270`) it does not. (Question 2.)
- **P5. Liveness.** `accept` stamps `lastInboundAt` for any datagram from a registered origin before classification
  (`:147-152`), including a malformed `Iam`. Unchanged, but a spoofed malformed line keeps a registered origin's
  idle timer fresh.
- **P6. The `extraTokenCount` DEBUG log sits before the lookup** (`:245-246`). With the lookup first, decide whether it
  should log for malformed lines at all.

## 11. Considerations

### C1. What a registered origin gets for a malformed `Iam`

Both options leave the registration untouched, never send `REFUSED`, and run the lookup before the malformed check.

| Input from a registered origin | Today | Option A: no reply + log line | Option B: `REGISTERED 2` |
|---|---|---|---|
| bare `Iam` | parse fails before the lookup; nothing sent; listener WARN with stack trace (`MultiConnectionUDPServer.kt:336`) | nothing sent; the log line comes from the coordinator (level per Question 2) | **changes**: now `REGISTERED 2` (12 bytes for a 3-byte datagram) |
| `Iam  x` (empty name) | parses; lookup hit; INFO "Repeating handshake reply" + `REGISTERED 2` | **changes**: nothing sent | unchanged |
| `Iam bob  tok` (empty credential) | parses (name `bob`, credential `""`); `REGISTERED 2` | **changes**: nothing sent | unchanged |

- **Option A** keeps "a registered origin is repeated `REGISTERED 2` only for a well-formed `Iam`", which is the
  contract documented since `751d0cb`; keeps today's silence for a bare `Iam`; changes behaviour only for the two
  empty-slot lines, which are exactly the lines being declared malformed. It needs the malformed check to run for
  registered origins too (parse, then lookup, then branch), plus one drop log line. A rate bound on that log is
  #41's concern.
- **Option B** is the simplest code (a registered origin gets `REGISTERED 2` before any parsing), but it gives a new
  reply to a previously silent input and lets any `Iam`-led datagram, malformed or not, draw a reply from a
  registered origin.
- Tests: neither option flips an existing test (none sends a malformed line from a registered origin). Either
  adds three level-2 cases (one per malformed shape) asserting registration intact, `size` unchanged, no
  `REFUSED`, and either `sent.isEmpty()` (A) or `sent == [REGISTERED 2]` (B).
- Docs: protocol doc §3 and the `:321` row, README `:123-127`, and the `accept` / class KDoc each state the choice.
- **Recommendation: Option A.**

### C2. KDoc edits to two public files

`MultiConnectionUDPServer.kt` (`:15-23`, `:43-54`, `:355-383`) and `HandshakeWireFormat.kt` (`:35-39`, `:52-57`)
carry sentences that become incomplete (refusal only through `admit`; "well-formed"). Editing them is
documentation of enforced behaviour, not an API change, but it shifts rows 3, 6, 11, 12, 13 and 37 of §5.
Options: edit both (recommended; the Level-2 KDoc standard and the public `admit` contract should describe the
refusal), or leave both and accept that `admit`'s KDoc under-describes which lines reach it. If the `admit` KDoc
gains "`name` is never empty", that is a new documented guarantee consumers will rely on; the recommendation is
to state it, because the enforcement is what the work delivers.

## 12. Recommendation for this repo

Scope the change as: code in `HandshakeProtocol.kt` and `HandshakeCoordinator.kt`; KDoc in those two plus
`MultiConnectionUDPServer.kt` and `HandshakeWireFormat.kt`; the living docs (`docs/webtools-udp-protocol.md` §3 and
§8, the architecture handshake node `:112`, the README handshake sections `:104-135` and `:217-250`); the two
flipped tests; new cases at levels 1, 2, 3 (the flip plus one `admit`-never-consulted case) and 4a, with an optional
raw-socket 4b case. Leave `MultiConnectionUDPClient`, `handshakeMessage`, `CommonChannel`, `isHandshake`,
`extraTokenCount` and all non-`Iam` code alone. Refresh all 21 stale protocol-doc references in one pass at the end,
after the `main/` edits, using §5's anchors; the 11 stale ones in unedited files can be corrected independently
of the code. Report (do not fix) the architecture-doc `:52` reference and the `ChannelByteGuardIntegrationTest.kt:25`
comment reference. Do not take the obvious shortcut of "split on `\s+`" from the issue's second option: it would
erase the empty tokens by redefining the wire format, silently turn `Iam bob  tok` into a different valid line, and
contradict the single-space invariant above.

## 13. Lens notes

- **Best systems.** Not applicable to an inventory; the refusal semantics are Questions 2 and 3.
- **Conventions.** Tests live per level under `testing.<level>` with the `@Tag` the Gradle task selects
  (`build.gradle.kts:44-62`); test classes carry a one-line level comment; the protocol doc cites code as
  `File.kt:N` (symbol-only for `Rtt.kt`, line 275) and is evergreen (the #14 finalisation plan records that the
  #38 fix "must update the as-built notes"); KDoc follows the Level-2 standard.
- **Idiomatic Kotlin.** `Result` stays the expected-failure channel; `tokens.getOrNull(CREDENTIAL_INDEX)` separates absent
  from empty at `HandshakeProtocol.kt:58`; `HandshakeProtocol` stays a pure `internal object`.

## 14. Known gaps

- No build or test was run (read-only research); every line range was read from the files, and the history-based
  ones were checked against `bc6f8b1` with `git show`.
- The post-change line numbers for the "edited" rows cannot be known until the code is written; §5 gives anchors,
  not final numbers.
- Return type, reason text, log level and the `Handshake` shape are Questions 2 and 3; the verdicts on
  `HandshakeProtocolTest.kt:40-43,55-58` and `HandshakeProtocolGatingTest.kt:27-30` depend on Question 2.
- Version bump and release-note handling are not inventoried (`webtools-udp/build.gradle.kts:92`, no `CHANGELOG`).
- Whether the repo's `Edit`/`Write` tooling round-trips the NUL/SOH bytes in `HandshakeCoordinatorTest.kt` was not
  tested (editing is out of scope for this research).

## 15. Load-bearing claims

1. **Kotlin's `split(Char)` keeps consecutive and trailing empty strings.** The empty name / credential slots,
   and the P2 trailing-space behaviour, depend on it. Quote: "It also implies that for char sequences ending with one of delimiters, the resulting list will end with an empty string." and the sample `"a,,b,,,c".split(',')  // [a, , b, , , c]`.
   Source: <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/split.html> (Kotlin stdlib, `CharSequence.split(vararg delimiters: Char, ignoreCase, limit)`, read 2026-10-19).
2. **`String.trim()` strips leading and trailing whitespace only.** The wire text is trimmed at its ends before the
   split, so interior whitespace other than U+0020 survives (the #53 gap) and a trailing space on the wire never
   becomes an empty slot. Quote: "Returns a string having leading and trailing whitespace removed."
   Source: <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/trim.html> (Kotlin stdlib, read 2026-10-19).
3. **ripgrep skips a file containing a NUL byte during recursive search.** The editing hazard (and the `Grep` tool,
   which is ripgrep-based) depends on it. Quote: "a file is considered "binary" if and only if it contains a `NUL` byte somewhere in its contents." and "This default mode **only applies to files searched by ripgrep as a result of recursive directory traversal**."
   Source: <https://github.com/BurntSushi/ripgrep/blob/14.1.1/GUIDE.md> (ripgrep 14.1.1, the version installed here; read 2026-10-19). The `-a` escape: "Text mode completely disables all binary detection and searches all files as if they were text."
