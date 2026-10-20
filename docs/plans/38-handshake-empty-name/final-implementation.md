# Issue #38 — as built

- **Covers:** `SpartanLaboratories/WebTools#38`, *"Handshake parsing accepts an empty peer
  name on extra whitespace"*.
- **Plan:** [`plan.md`](plan.md), unit `malformed-iam-refusal`, Stages A–D.
  **Architecture:** [`architecture.md`](architecture.md). **Impact:** [`impact.md`](impact.md).
  **Research:** [`research/README.md`](research/README.md).
- **Branch:** `fix/issue-38-handshake-empty-name`, from `master` `85cfbbe`.
- **Release:** `webtools-udp` `2.0.0a`, the bugfix after `2.0.0`, set in its own `build:`
  commit after the commits below.
- **PR:** see the plan's header.

## 1. Commits

| # | SHA | Contents |
|---|---|---|
| research | `77b3786`, `7b58916`, `fbf9dc9`, `b6446de`, `31aab26`, `e8c2929`, `9b1fff7` | the research questions, three findings files, verification, summary |
| 1 | `56a2121` | A-1, A-2: test housekeeping (the NUL/SOH escapes in `HandshakeCoordinatorTest.kt`, the comment range in `ChannelByteGuardIntegrationTest.kt`), with `plan.md`, `architecture.md`, `impact.md` |
| 2 | `1605bdd` | A-3..A-7: the parse outcome, the routing, KDoc, tests |
| 3 | `d3807b0` | C-1..C-4, D-1..D-3: production KDoc contracts, the `#49` marker, `//region`s, with the updated plan documents |
| 4 | `c6be0f2` | C-T1..C-T8, D-4: tests at levels 1, 2, 3, 4a, 4b and 4c |
| 5 | `16d6849` | Stage B with C-D, C-B, D-5, D-6: the protocol doc, the architecture doc, the README |

## 2. What was built

### Behaviour

**What counts as malformed.** The server trims an unframed datagram's text and splits it on
a single U+0020. A line whose first token is exactly `Iam` is a handshake. It is malformed
when:
- its name slot is missing (a bare `Iam`), or
- its name slot is empty (`Iam  x`), or
- its credential slot is present but empty (`Iam bob  tok`).

An absent credential (`Iam bob`) is not malformed.

**Every malformed line, from any origin, is answered `REFUSED malformed`** (17 bytes). The
reason is informative wire text, not a contract. A malformed line:
- never reaches `admit`;
- never supersedes a same-name registration;
- registers nothing;
- fires no `onClientConnect`.

**From an origin that already holds a registration,** that registration is not torn down,
superseded or re-screened, and no `onClientDisconnect` fires. Like every inbound datagram
from a registered origin, the line still refreshes the registration's idle-detection stamp
when `idleTimeoutMillis` is positive. A peer that is mid-session can therefore receive the
unframed `REFUSED`:
- a library client's session listener drops it with a WARN;
- a third-party client may read it as a disconnect.

The protocol doc states this trade-off.

**Well-formed lines behave as before.** A registered origin gets `REGISTERED 2` again, with
no re-screening. An unknown origin goes through `admit`, supersede, register, `REGISTERED 2`
and `onClientConnect`, including the register-before-send ordering that #49 concerns.

**Every other unframed datagram stays as silent as before,** `Iam<TAB>bob` included: a WARN
("sender may be pre-2.0"), and no reply.

**Logging.** Each refused malformed line logs one WARN with a fixed template:
`Refusing malformed handshake from {} ({}); origin registered: {}`. It carries the origin,
the defect label and the registered flag. It never carries the line, its tokens, the name or
the credential, and no exception object is attached. The listener's stack-trace WARN that a
bare `Iam` drew before is gone.

**No new throw path.** The parse is total, and the routing is an exhaustive `when`.

### Code

**`HandshakeProtocol.kt`:**
- a sealed `HandshakeParse` (`NotAHandshake`, `Malformed(defect)`, `Valid(handshake)`);
- a `HandshakeDefect` enum with three fixed, log-safe labels;
- a total `parseHandshake` that checks the verb through `isHandshake`, then the name, then
  the credential, which it reads with `getOrNull`;
- `MALFORMED_REFUSAL_DATAGRAM`, built once from `HandshakeWireFormat.refusedMessage("malformed")`.

`Handshake`, `isHandshake` and `extraTokenCount` are unchanged.

**`HandshakeCoordinator.kt`:**
- `classify` splits the text once and calls `routeUnframed`, which hands a malformed line to
  `refuseMalformed` and a valid one to `handleHandshake`;
- `admitAndRegister` is the previous body, moved unchanged, with a
  `FIXME(#49): registers before the REGISTERED 2 send; a failed send orphans the
  registration` at the registration line;
- six `//region`s: Inbound routing, Handshake state machine, Delivery to bound handlers,
  ClientChannel and scheduler shutdown, Liveness, Server-wide fan-out;
- the KDoc states:
  - the registration guarantee above;
  - `accept`'s three failure sources: an undeliverable reply, a throwing `admit`, and a
    handler that cannot be handed to `dispatch`;
  - the two-argument `accept`'s trimmed-text precondition.

**`MultiConnectionUDPServer.kt` and `HandshakeWireFormat.kt`:** KDoc only, with no
signature changes. A malformed `Iam` is refused before `admit`. Refusal by `admit` is from
`webtools-udp` `1.4.0`, and the malformed refusal is from `webtools-udp` `2.0.0a`. Every
datagram from a registered client refreshes its liveness stamp.

### Tests

| Level | `85cfbbe` | Final | Added or changed |
|---|---|---|---|
| 1 gating | 77 | 83 | every parse outcome (valid, each defect, not a handshake); the unknown- and registered-origin malformed cases; the non-`Iam` drop sends nothing |
| 2 component | 223 | 235 | the three shapes from unknown and registered origins; extra tokens; the send failure; the WARN content; the extra-token DEBUG; `Iam<TAB>bob` stays unframed; a mis-described test renamed, plus a `deliverData` no-handler test |
| 3 integration | 110 | 115 | a bare `Iam` gets `REFUSED malformed`; all three shapes over a real socket; a registered socket's malformed lines; a trailing space trimmed; an empty extra token; a retry after a malformed refusal |
| 4a deterministic | 89 | 96 | the parse truth table, totality, the verb check before any slot check, the reply bytes, the #53 gap pinned |
| 4b e2e | 33 | 34 | a library client with an empty name gets a typed `malformed` refusal, pinned until #52 |
| 4c nonfunctional | 62 | 65 | a malformed burst; a burst that includes a registered socket (one reply per datagram, the peer still reachable through the registry); a hostile CR/LF line stays out of the log |
| 5 uat | 13 | 13 | unchanged; skipped, as before |

All runs pass.

### Documents

**Protocol doc:**
- §3 states:
  - the rule and the registered-origin trade-off;
  - the informative reason;
  - the #53 gap;
  - the replies an unverified source can draw: the handshake replies, plus the transport's
    `0x82` and `0xA1`;
  - when a library client sends a malformed line.
- §8 has the malformed rows.
- §9 has a row for servers before `2.0.0a`, with its source cited.
- The scope line names `2.0.0a`.
- Every source line reference into an edited file was recomputed from the final sources.

**Architecture doc:** the `:52` reference and the handshake node.

**README:** the handshake sections. The install snippet moves to `2.0.0a` with the version
bump.

## 3. How the plan grew during implementation

The plan was carried out in four stages. Stages A and B are the original plan (§7–§10).
Stages C (§17) and D (§18) were added after review of the change set.

### Stage C: after the first review

The first review returned changes requested: 10 documentation findings and 8 test findings.

**Documentation:**
1. **The registration guarantee.** "The registration is left exactly as it is" was false
   with idle detection on, because `accept` refreshes a registered origin's liveness stamp
   for every datagram. The KDoc and the protocol doc now state the precise guarantee.
2. **Release labels.** The KDoc said a `1.4.0`+ server refuses malformed lines. Every
   release up to `2.0.0` registers the empty-slot shapes and leaves a bare `Iam` unanswered.
   - The labels are now split: refusal by `admit` is `1.4.0`, the malformed refusal is
     `2.0.0a`.
   - The protocol doc gains a §9 row and a scoped "Applies to" line.
   - "Identical to pre-`1.4.0` behaviour" is now limited to well-formed lines.
3. **`accept`'s failure sources.** Its `@return` omitted dispatch hand-off rejection.
4. **The two-argument `accept`** gained `@param` and `@return`.
5. **The protocol doc's list of library-client calls that send a malformed line** was wrong
   in both directions. It missed `handshake("bob ", credential = "tok")` and
   `handshake("a  b")`, and included the valid `credential = " "`. It now states the rule,
   with examples.
6. **"A registered source can draw two replies"** read as exhaustive. It is now scoped to
   handshake replies, and lists the transport's own replies too.
7. **The `admitAndRegister` KDoc** narrated the move and called the #49 ordering
   "deliberate". It now names #49 as a known defect, and a `FIXME(#49)` marks the line.
8. **A bare `Thread.sleep(200)`** in a 4c test became a named constant with a comment.
9. **The reply-bytes KDoc** cited `refusedMessage`'s internals. That rationale moved to a
   `//` comment.
10. **`HandshakeCoordinator.kt`** gained six `//region`s.

**Tests:**
1. Gating cases for the `CREDENTIAL_EMPTY` and `NotAHandshake` arms.
2. A gating case for a registered origin's malformed line, and the non-`Iam` drop asserting
   that nothing was sent.
3. A component test named for `deliverData` never reached it: byte `0x00` is unframed. It
   was renamed and strengthened, and a real no-handler `deliverData` test was added.
4. Integration cases for a retry on the same socket after a malformed refusal, and for an
   empty-credential line from a registered socket.
5. A deterministic test that the verb check runs before any slot check, which nothing had
   pinned.
6. A deterministic test renamed after its behaviour.
7. The 4b e2e test (see §4).
8. Two 4c tests: the burst including a registered socket, and the hostile CR/LF line.

**Commit order.** Stage C also reordered the remaining commits. The source fixes (Commits 3
and 4) landed before the documents (Commit 5), so that every document line reference was
computed from the final sources (C-B).

### Stage D: after the second review

The second review returned changes requested with 5 LOW items: 4 documentation, 1 test.
1. **`HandshakeProtocol`'s KDoc** said its two fixed replies go out "without consulting the
   application". But a new origin's `REGISTERED 2` follows `admit`. It now says they are
   fixed-text replies, whose bytes never come from the application.
2. **The release labels did not name the artifact,** and `2.0.0a` was also a version of the
   retired combined `io.github.spartanlaboratories:WebTools` artifact. The first mention in
   each KDoc block now reads `webtools-udp` `2.0.0a`, and the neighbouring `1.4.0` labels
   are qualified the same way.
3. **The liveness text** read as if only application data and keepalives refresh the stamp.
   It now says any datagram does.
4. **§9's source citation** covered rows 1–3 only. It now cites row 4's source as well.
5. **The registered-socket burst test** probed the peer through the captured `Connection`,
   which never consults the registry. It now:
   - probes with `pushToAll`, which reaches only peers in the registry;
   - asserts that no `onClientDisconnect` fired;
   - asserts that only the registering line reached `admit`.

## 4. Decisions taken during the reviews

These are the maintainer's decisions, each with its reason:
- **Level 4b: add the e2e test.** The original plan made 4b optional. The test pins that a
  library client with an empty name fails fast with a typed `malformed` refusal, and it
  flips when #52 makes that call fail locally.
- **Level 5: no UAT step.** The change's observable surface is the wire reply and the
  server log, which the level-3, 4b and 4c tests exercise end to end over real loopback
  sockets.
- **Test name `deliverData with no handler bound drops a 0x90 frame with no reply` kept.**
  Naming the function a test exercises is acceptable in a test name.
- **No third review run.** Stage D was wording and one test probe, all LOW.
- **Not in scope:**
  - `MultiConnectionUDPClientFramingTest` is tagged component but uses real sockets. It was
    neither filed nor changed.
  - The second review's optional notes were not taken: the "when idle detection is on"
    wording, the missing-versus-empty slot phrasing, `Level.TRACE` capture, and older text
    outside the change.

## 5. Deviations from the plan

**None.** The implementer recorded no deviation in any of Stages A–D. Each stage was
applied as written.

One of the plan's own checks gave a false alarm. D-7 expected
``grep -c 'webtools-udp` `2.0.0a`' HandshakeWireFormat.kt`` to return 2, and it returned 1.
D-2a's text wraps that label across `HandshakeWireFormat.kt:21-22`, so a line-based grep
cannot see it. The text is as planned, and the result was accepted as a false alarm.

## 6. Verification

- **Tests:** the counts in §2, with every automated level passing.
- **Line references.** The C-7 reference check and D-7's checks were re-run on the
  final tree when this record was written:
  - every `File.kt:N` reference into an edited file prints the first and last lines its
    prose describes;
  - the shorthand references were read by hand: `HandshakeWireFormat.kt` `:110-118` and
    `:41-44`, and `ReliableChannelEngine.kt` `:104-118`;
  - the architecture doc's `MultiConnectionUDPServer.kt:277` is the `coordinator` field.
- **Superseded wording:** none of the corrected phrases remains in the sources or the
  protocol doc.
- **Line endings:** the three living documents keep CRLF line endings in the working tree.

## 7. Research that didn't hold

**None.** Implementation proved no research claim wrong.

One design statement needed correcting after implementation began, but it was a wording
lapse in the design documents, not a research error. The statement was that a malformed
line leaves a registered origin's registration "exactly as it is" (Stage C, documentation
item 1). The research had it right: `research/codebase-blast-radius.md` pitfall P5 records
that `accept` refreshes a registered origin's liveness stamp before classification. The
caveat was dropped between research and design.

The one claim that failed verification, an SMTP section citation, was caught before design
and shaped nothing.

## 8. Follow-ups

See [`impact.md`](impact.md) §6:
- **#41:** rate-bounding the drop WARNs, including the new malformed WARN.
- **#49:** the registration orphaned on a failed `REGISTERED` send, now marked `FIXME(#49)`.
- **#52:** client-side validation. The 4b test flips with it.
- **#53:** non-space whitespace in tokens. A 4a test pins the gap and flips with it.
- **#54:** a throwing `onClientConnect`.
- **#56:** the retransmit lookup ignores the name.
- **#57:** replies to unverified sources, with no rate limit.
