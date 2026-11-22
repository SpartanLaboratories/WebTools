# Final implementation: origin screening on every client receive branch (Issue #39)

The as-built record of `SpartanLaboratories/WebTools#39`. It states what was built, where the build departs from
[`plan.md`](plan.md) and why, the measurements that show it holds, and the acceptance of each requirement.

The fix corrects code that Issue #14 built, so the follow-ups and known gaps it leaves, what it made no longer
current and what it closed are in [`consequences.md`](../14-reliable-ordered-channel/consequences.md) of that issue's
folder and, for the statements in the channel-byte guard's documents, in
[`consequences.md`](../40-channel-byte-guard/consequences.md) of Issue #40's. This record does not repeat them.

The branch is `fix/issue-39-client-datagram-origin-check`, cut from `master` at `d68ec81`.

## 1. What was built

- **The origin screen.** In `MultiConnectionUDPClient.receiveLoop`
  (`webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/MultiConnectionUDPClient.kt`), `if (origin !=
  serverEndpoint)` now precedes the `when` on the datagram's tag. It logs one DEBUG line, `Datagram from unexpected
  origin {} (expected {}), dropped`, naming both addresses, and returns. A datagram from another source is dropped
  whatever its tag, an unknown, reserved or empty one included: nothing is delivered, answered, completed or created
  for it, and none of the malformed, unframed, reserved-tag or non-zero-channel lines is logged. The reliable arm's own
  origin check and the comment above it are removed, because the screen ahead of the switch makes them unreachable.
  A datagram from `serverEndpoint` takes the paths it took before.
- **What the screen does not cover.** The reply `handshake()` waits for is the first datagram to arrive, whatever its
  source. The receive-buffer truncation WARN, which a datagram that fills a lowered `receiveBufferBytes` draws, is
  logged before the screen and names the real source. The class KDoc and the protocol document state both
  (plan section 1, "Not in this fix").
- **The screen is not authentication.** It compares a source address and port that nothing authenticates, so a datagram
  forged with the server's own address and port gets through. The class KDoc says so.
- **Inline documentation.** The class KDoc has a "Datagram origin" section and a listener sentence that names the
  screen. The comment above the screen, the `0x90` arm's channel-check comment and `receiveLoop`'s KDoc say what the
  loop does first. The KDoc of the public constructor, `start`, `startBytes`, `handshake`, the `reliableEngine` field and
  `serverEndpoint` is corrected to match (D4, D5).
- **Tests.** The gating set gains `ClientOriginScreenGatingTest` (one test). The component set gains
  `MultiConnectionUDPClientOriginScreenTest` (ten tests). In `MultiConnectionUDPClientFramingTest`, the test that
  pinned the unscreened `0x90` behaviour asserts the DEBUG origin drop and the absence of the channel WARN instead. In
  `MultiConnectionUDPClientReliableTest`, the two foreign-origin tests wait on the shared drop line.
- **The reference documents.** `docs/webtools-udp-protocol.md` (header, §2, §5 `0x81`/`0x82`, `0x90` and "Channel
  byte", §8, §9, with every `MultiConnectionUDPClient.kt:N` reference recomputed against the final file) and
  `docs/webtools-udp-architecture.md` (§6, "Client differences, in prose") state the screen where they described the gap.
- **The version.** `webtools-udp` is `2.0.1a`, with its build-file comment. No public API, persisted state or wire
  format changed.

## 2. Deviations

Each departs from the section of [`plan.md`](plan.md) it names. No research or parent architecture stands behind these
sections, so the plan is the only basis, except where a deviation names another.

### D1. The new tests assert that the listener logged its drops

*Plan: section 4 ("Regression tests"), the shared procedure and the "Fails today on" column.* Each new test asserts,
after its own negative assertion, that the listener logged exactly one `Datagram from unexpected origin` DEBUG line for
each foreign datagram it sent: the ten component tests one, `ClientOriginScreenGatingTest` eight. Section 4 has the tests
poll for that count without asserting it, apart from the keepalive test, which asserts one drop event. Without the
assertion, a run whose listener never handled the foreign datagram would pass the negative assertions vacuously, and the
`0xA1` test would not fail against the unfixed code as section 4's table says it does: its no-engine assertions already
hold there, and a poll that times out is not a failure. The count is asserted after each test's own negative assertion,
so every other test still fails first on the assertion the table names.

### D2. Four more off-origin component tests, and a reserved tag in the gating smoke

*Plan: section 4, the table and the component list.* `MultiConnectionUDPClientOriginScreenTest` also covers a
reserved-tag datagram (`0xA2`), dropped at the origin screen with no `Unhandled datagram type` line; a malformed `0x81`
too short for a sequence, which draws no `Malformed 0x81` WARN; a malformed one-byte `0x90`, which draws no `Malformed
0x90` WARN; and an empty datagram, which draws no WARN. Each then sends from `peer` and asserts that the server's
datagram still draws its usual line. `ClientOriginScreenGatingTest` sends `0xA2` as an eighth foreign datagram and waits
for eight drops. Section 4 lists six component cases and seven gating datagrams; these cover the remaining arms of the
listener's switch, the reserved-tag and malformed-frame lines included, that the origin screen now precedes.

### D3. The existing foreign-`0xA0` reliable test waits on the drop line

*Plan: section 4, "The existing `an 0xA0 from a foreign origin mints no engine and delivers nothing` ... stays as it
is".* In `MultiConnectionUDPClientReliableTest`, that test replaces its fixed 300 ms sleep with a DEBUG capture that
polls for the `Datagram from unexpected origin` line and asserts exactly one, ahead of its negative assertions. With the
sleep, its negative assertions did not prove that the listener had handled the datagram.

### D4. The screen's comment and the class KDoc say what the check does, and that it is not authentication

*Plan: section 3, item 1 (the comment) and item 3 (the "Datagram origin" paragraph).* The plan's comment said that a
"stray or spoofed source" is never answered and that the screen means a datagram "never draws a WARN"; its KDoc
paragraph said the listener drops a datagram from any other source "whatever its tag, before reading it". The comment now
says a datagram "from any address other than the server's" never draws "the malformed, unframed or non-zero-channel
WARN", and keeps the plan's point that the check sits ahead of the switch so that it covers a tag added later. The KDoc
paragraph says the datagram is dropped "before reading its tag" and adds two sentences: the check filters by a source
address and port that nothing authenticates, so it is not authentication and a datagram forged with the server's own
address and port gets through; and a stray datagram that fills a lowered `receiveBufferBytes` still logs the truncation
WARN before it is dropped. The plan's wording overstated what an unauthenticated address check does, and "never draws a
WARN" was untrue of the truncation WARN, which section 1 of the plan leaves ahead of the screen.

### D5. More inline documentation of the touched class is corrected

*Plan: section 3, item 3, which lists five edits to inline documentation.* Each of the following described the listener's
delivery, or the creation of the reliable engine and its thread, in terms that the screen and the channel-byte guard had
made inaccurate or incomplete, or lacked tags that R-11 (Documentation levels) asks of a public declaration:

- the class KDoc's listener sentence also says what each branch does (a keepalive is dropped, an `0x81` is answered and
  an `0x82` completed inline, an `0xA0`/`0xA1` is passed to the reliable engine, a `0x90` is stripped and dispatched);
  its note on the `mcupc-retransmit` thread says a reliable channel is opened by the first reliable `send` or the first
  inbound `0xA0`/`0xA1` on channel `0x00` from the server, not by an `actuate*` call; and its reference to the "Ordering
  contract" says "above", where that section stands;
- the public constructor's KDoc carries `@param` for its four parameters and `@throws` for `SocketException` and
  `IllegalArgumentException`, and says its scheduler threads start lazily;
- `start` and `startBytes` say that the handler receives the payload of every channel-`0x00` `0x90` from the server
  endpoint, where they said "every non-keepalive datagram", and how a keepalive, an `0x81` and an `0x82` are handled;
- `handshake` says its reply is the first datagram to arrive on the socket, whatever its source;
- the `reliableEngine` field says that binding a handler alone creates no engine on the client, unlike the server;
- `serverEndpoint` says it is the only source the listener accepts, besides keying the keepalive, probe and retransmit
  schedules.

### D6. Reference-document wording beyond the plan's list

*Plan: section 3, `docs/webtools-udp-protocol.md` and `docs/webtools-udp-architecture.md`. These notes are the ones
[`plan-finalisation.md`](../14-reliable-ordered-channel/plan-finalisation.md) section 11 ("When #38 or #39 is fixed")
requires the fix to update.* Four more edits:

- §5 "Channel byte" says both receivers screen a datagram's origin "(the server's probe and data branches against the
  registrations, every client branch against `serverEndpoint`)". The plan's text said "every server branch against the
  registrations", but the server's keepalive branch checks no registration: a `0x80` from an unregistered origin is only
  traced.
- §8's receive-buffer row, client cell, reads "from `serverEndpoint`: delivered truncated, WARN; from any other source:
  the truncation WARN, then dropped at DEBUG". The truncation WARN precedes the screen, so the plain "delivered
  truncated, WARN" would say that a stranger's oversized datagram is delivered.
- The architecture document's §6 bullet "There is no liveness stamp and no handshake branch" says that a byte-0 `< 0x80`
  datagram *from `serverEndpoint`* is WARN-dropped, because one from any other source is now dropped at DEBUG by the
  screen.
- The protocol document's §2 sentence on the screen ends "so a server must reply from the address and port the client
  sent to", the requirement that plan section 5 expects the document to state, and it links
  [#110](https://github.com/SpartanLaboratories/WebTools/issues/110) where it says the reply `handshake()` waits for is
  not checked. The architecture document's §6 first client bullet links #110 the same way. The plan's text for both was
  written before #110 existed.

### D7. The protocol document's versioning table gains a row

*Plan: section 3, the `docs/webtools-udp-protocol.md` list, which has no §9 edit.* §9 gains a row for `2.0.0` to `2.0.1`
clients: only the `0xA0`/`0xA1` branch screens the source; a `0x81` from any source is answered, an `0x82` completes a
probe, a `0x90` is delivered, and an unframed datagram draws a WARN. The table is where the document records how an
earlier release behaves differently.

## 3. Measurements

Counts are from the last recorded run of each set, and none failed. The deterministic, integration, e2e, nonfunctional
and uat sets hold no test that this change adds or edits.

| Set | Before | After | Failed | Skipped |
|---|---|---|---|---|
| gating | 83 | 84 | 0 | 0 |
| component | 235 | 245 | 0 | 0 |
| integration | 115 | 115 | 0 | 0 |
| deterministic | 96 | 96 | 0 | 0 |
| e2e | 34 | 34 | 0 | 0 |
| nonfunctional | 65 | 65 | 0 | 0 |
| uat | 13 | 13 | 0 | 13 |

The new gating test runs in 1.3 s, and the ten new component tests in 10.8 s. The API-reference gate,
`./gradlew dokkaGeneratePublicationHtml`, ran on the final KDoc with no warning.

## 4. Acceptance

Of the requirements in [`plan.md`](plan.md), section 1:

1. **A datagram from any source other than `serverEndpoint` is dropped, whatever its tag: met.** The ten component
   tests, the existing foreign-`0xA0` test and the gating smoke each send datagrams from a foreign socket, assert the
   DEBUG line, one per datagram, and assert that what the branch did with such a datagram before did not happen: no
   `0x82` is sent for an `0x81`, no probe completes for an `0x82`, nothing is delivered for a `0x90` or an `0xA0`, no
   engine is created for an `0xA0` or `0xA1`, and no WARN or unhandled-type line is logged for an unframed,
   reserved-tag, empty, or malformed `0x81` or `0x90` datagram. A keepalive is covered by the absence of its "from
   server" TRACE line.
2. **A datagram from `serverEndpoint` is handled exactly as before: met.** Each of those tests ends by sending the
   server's datagram of the same kind and asserting that it is handled, and the integration, e2e and nonfunctional sets,
   which drive a real client against a real server or a fake peer, pass.
3. **The screen is made once, before the tag is read: met.** The reserved-tag test (a tag with no arm of its own) and the
   empty-datagram test show that the screen drops a datagram before the switch reaches its unknown-tag arm, which would
   otherwise log it.
4. **No public API, persisted state or wire format changes: met.** The source change is one private function and
   inline documentation; `MultiConnectionUDPClientConstructorGatingTest` still holds the constructor set; the client
   sends nothing new.

No assumption in section 5 of the plan failed in the build.
