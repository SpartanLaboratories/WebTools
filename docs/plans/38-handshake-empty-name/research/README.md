# Research: handshake parsing accepts an empty peer name

[SpartanLaboratories/WebTools#38](https://github.com/SpartanLaboratories/WebTools/issues/38).
Related: [#52](https://github.com/SpartanLaboratories/WebTools/issues/52) (client-side
handshake validation), [#53](https://github.com/SpartanLaboratories/WebTools/issues/53)
(non-space whitespace in names and credentials),
[#49](https://github.com/SpartanLaboratories/WebTools/issues/49) (registration orphaned
when the `REGISTERED` send fails),
[#41](https://github.com/SpartanLaboratories/WebTools/issues/41) (rate-bounding the
per-datagram drop WARN logs).

## The work

The `webtools-udp` server reads an unframed handshake line by trimming the datagram's
UTF-8 text and splitting it on a single U+0020 (`HandshakeCoordinator.kt:227` on
`85cfbbe`). A line whose first token is exactly `Iam` is a handshake. This change makes
the server reject a malformed one.

- **Malformed** means the name slot is missing (a bare `Iam`) or empty, or the
  credential slot is present but empty. A run of spaces before or between the name and
  the credential produces the empty slot: `Iam  x` puts `""` in the name slot, and
  `Iam bob  tok` puts `""` in the credential slot. An absent credential (`Iam bob`) is
  not malformed; `admit()` receives it as `""`, as before.
- **Every malformed line is answered `REFUSED malformed`,** whatever the state of its
  origin. It registers nothing, never reaches `admit()`, never supersedes a same-name
  registration, and fires no `onClientConnect`. A library client's `handshake()` fails
  with `HandshakeRefusedException`.
- **A malformed line from an origin that already holds a registration leaves the
  registration as it is:** no teardown, no `admit()`, no supersede, no `onClientConnect`.
  The `REFUSED malformed` reply reaches a peer that is mid-session:
  - a library client's session listener drops that unframed datagram with a WARN;
  - a third-party client may take it as a disconnect while the server keeps the peer
    registered.

  The protocol doc states this.
- **A well-formed line from a registered origin** gets `REGISTERED 2` again, as a
  retransmit, without re-screening.
- **Tokens past the credential slot stay ignored,** empty ones included:
  `Iam bob tok  x` is accepted with credential `tok`.
- **Only the three malformed shapes draw a reply.** A line whose first space-separated
  token is not exactly `Iam` (for example `Iam<TAB>bob`) is still not a handshake. It
  stays silent, as does every other unrecognised datagram.
- Names and credentials that contain whitespace other than U+0020 (tab, CR/LF, NBSP)
  still register. #53 tracks that gap, together with the client-side checks in #52.
- No public API is added. The reason is informative wire text, not a contract.
- **Line references are refreshed:**
  - every out-of-date source line reference in `docs/webtools-udp-protocol.md`;
  - the one at `docs/webtools-udp-architecture.md:52`;
  - the one in the comment at `ChannelByteGuardIntegrationTest.kt:25`.

  The protocol doc describes the remaining whitespace gap as known and links #53.
- **The raw NUL and SOH bytes** in the string literal at `HandshakeCoordinatorTest.kt:577`
  become the escapes `"\u0000\u0001"`. The string is the same.
- **Unchanged:** the ordering behind #49 (register, then send `REGISTERED 2`), and an
  exception thrown by `onClientConnect` (#54).

## Questions

### 1. What does the change touch, and which line references in the protocol doc are out of date?

An inventory rather than a choice. For each site the answer gives a verdict: changes
with this work, stays as it is, or belongs to #52 or #53.

An answer must give, with `path:line` on master `85cfbbe`:

- every production call site of `HandshakeProtocol.parseHandshake`, `isHandshake` and
  `extraTokenCount`, every use of the `Handshake` class, and the path from the listener
  loop to `HandshakeCoordinator.handleHandshake`;
- every test, at every level of the repo's five-level test layout, that sends a bare
  `Iam` or an `Iam` with an empty slot, or asserts what happens to one: what it asserts
  today and whether this work changes it (two are known: `HandshakeCoordinatorTest`
  "a nameless Iam fails and registers nothing", and `MultiConnectionUDPServerTest.kt:248`,
  which expects no reply to a bare `Iam`);
- every KDoc, protocol-doc, architecture-doc and README passage that states how an `Iam`
  line is parsed, what `admit()` receives, when `REFUSED` is sent, or which handshake
  lines get no reply;
- every source line reference (`File.kt:N`) in `docs/webtools-udp-protocol.md`: what it
  is meant to point at, whether it still does, and the current range if not. References
  into a file this work edits are flagged for recomputation after the code change;
- the same check for `docs/webtools-udp-architecture.md` and `README.md`, reported
  separately;
- the intent recorded in `git log` / `git blame` for the single-space split, the
  parse-before-lookup order, and the rule that a retransmit from a registered origin is
  never re-screened;
- the adoption sweep: any other code, production or test, that tokenises handshake text
  the same way, and whether it should follow the new rule.

Evidence: the code and history on master `85cfbbe`, and `gh issue view` for #11, #38,
#49, #52 and #53.

Findings: [`codebase-blast-radius.md`](codebase-blast-radius.md).

### 2. How should the server represent and route a malformed `Iam` line?

Options worth comparing:

- **How `HandshakeProtocol` reports a malformed line:**
  - keep `Result<Handshake>` with an `IllegalArgumentException`, and have the
    coordinator treat every failure after `isHandshake` as malformed;
  - a dedicated internal exception type that carries the refusal reason;
  - a sealed parse outcome (valid name and credential, or malformed);
  - a separate pure validity check that runs before parsing.
- **How the credential slot is modelled,** so that an absent credential stays distinct
  from one that is present but empty. Today `getOrElse(2) { "" }` conflates the two.
- **What a registered origin's malformed line gets.** The options are:
  - no reply and a log line, which is how a bare `Iam` is handled today;
  - the same `REGISTERED 2` a well-formed retransmit gets (an `Iam` with an empty slot
    gets this today, because it parses);
  - the same `REFUSED` an unknown origin gets.
- **What `handleHandshake` returns for an unknown origin's malformed line.** Either the
  `REFUSED` send's `Result`, as the `admit()` refusal does, or a failure.
- **The log level and content of each outcome:** INFO as for an `admit()` refusal, WARN
  as for other malformed input, or DEBUG.

An answer must keep:

- `HandshakeProtocol` pure, `internal`, socket-free and exhaustively testable;
- `kotlin.Result` for expected failures, in line with the repo's paradigm: OOP for
  domain modelling, FP for data transformation, `Result` for expected failures;
- the public API unchanged;
- a malformed line away from `admit()`, the same-name supersede, registration and
  `onClientConnect`;
- a registered origin's registration untouched by a malformed line;
- the single-U+0020 split of the trimmed text, extra tokens ignored, and `Iam` as the
  exact first token.

It must also:

- fit `HandshakeCoordinator`'s existing conventions (the `admit()` refusal at
  `:256-271`; the drop paths that return `Result.success` with a log line), and the
  direction of #41;
- keep the raw line out of every log line, because it is untrusted and may carry CR/LF.

Evidence:

- the repo's code, and the refusal design in
  `docs/plans/11-handshake-refusal-and-credential/plan.md`;
- the Kotlin coding conventions and the `Result` design notes on exceptions versus
  typed outcomes;
- logging guidance on untrusted input (the OWASP Logging Cheat Sheet, CWE-117);
- how mature servers structure a validate-then-reply-or-drop step for a malformed
  first message.

Findings: [`malformed-line-handling.md`](malformed-line-handling.md).

### 3. What should the `REFUSED` reply to a malformed `Iam` say?

Options worth comparing:

- `REFUSED malformed handshake`;
- a shorter reason such as `REFUSED malformed`;
- a bare `REFUSED`;
- a reason that names the defective slot.

Each would be documented in the protocol doc either as informative text or as a
contract value.

An answer must keep:

- one short fixed reason, the same for a bare `Iam`, an empty name slot and an empty
  credential slot;
- the reply built by the existing `HandshakeWireFormat.refusedMessage`, with no new
  public constant or API.

It must also account for:

- **The recipient.** The reply goes to an unauthenticated origin that may be spoofed, so
  the size of the reply against the smallest trigger matters (a bare `Iam` is 3
  bytes). Compare it with what the server already sends unauthenticated origins:
  `REGISTERED 2` (12 bytes) to any `Iam <name>`, and `REFUSED <reason>` for an
  `admit()` refusal.
- **Its human readers.** The reason surfaces as `HandshakeRefusedException.reason` and
  in logs, and should read as a protocol-level rejection rather than an application
  policy decision.
- **The repo's existing reasons.** The README examples are `banned`,
  `invalid credential` and `server full`, and the `Admission.Refused` KDoc asks for a
  terse reason because it is echoed to an unauthenticated peer.

Evidence:

- anti-amplification guidance for unauthenticated UDP responders: RFC 9000 §8.1 for
  QUIC, RFC 7252 §11.3 for CoAP, RFC 9147 and RFC 6347 for the DTLS cookie exchange,
  and the CISA advisory on UDP-based amplification;
- how comparable text protocols word a rejection of a malformed opening message: IRC
  numerics, SMTP `501`, SIP `400`, STUN error code 400.

Findings: [`refusal-reply-text.md`](refusal-reply-text.md).

## Verification

### Outside-world claims

Each outside-world claim the findings rest on was checked against its source on
2026-10-19. That covers 46 items: 45 hold, 1 fails, and none is unclear.

| Claim | Source | Result |
|---|---|---|
| `split(Char)` keeps consecutive and trailing empty strings | [`kotlin.text.split`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/split.html); stdlib `Strings.kt` | holds |
| `trim()` removes leading and trailing whitespace only | [`kotlin.text.trim`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/trim.html) | holds |
| `Result` is a generic union of a value or any `Throwable` | [`kotlin.Result`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-result/) | holds |
| `Result.map` rethrows whatever its transform throws; `mapCatching` encapsulates it | [`kotlin.map`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/map.html) | holds |
| A `when` over a sealed type is checked for exhaustiveness | [Kotlin sealed classes](https://kotlinlang.org/docs/sealed-classes.html) | holds |
| `Result` is not meant for domain-specific errors. A sealed result type suits failures that are handled locally. Exceptions are costly to create. Exceptions are for preconditions and invariants | [KEEP-0127](https://github.com/Kotlin/KEEP/blob/main/proposals/stdlib/KEEP-0127-result.md) at `e688b25` (same path at `main`) | holds (4 quotes) |
| ripgrep treats a file that contains NUL as binary and skips it during recursive search; `-a` turns detection off | [ripgrep 14.1.1 `GUIDE.md`](https://github.com/BurntSushi/ripgrep/blob/14.1.1/GUIDE.md) | holds (3 quotes) |
| DTLS silently discards invalid records and keeps the association; answering them over UDP is NOT RECOMMENDED | [RFC 6347 §4.1.2.7](https://www.rfc-editor.org/rfc/rfc6347#section-4.1.2.7) | holds (2 quotes) |
| DTLS: a server can be used as an amplifier, a cookie defeats spoofed sources, and `HelloVerifyRequest` is kept small | [RFC 6347 §4.2.1, §3.2.1](https://www.rfc-editor.org/rfc/rfc6347#section-4.2.1) | holds (3 quotes) |
| WireGuard does not respond to an unauthorised client | [wireguard.com/protocol](https://www.wireguard.com/protocol/) | holds |
| ENet drops, without a reply, an invalid connect and a connect from an established peer | [ENet `v1.3.18` `protocol.c`](https://github.com/lsalzman/enet/blob/v1.3.18/protocol.c) `:306-308`, `:1132-1138`, `:1224-1228` | holds (3 items) |
| QUIC: no more than 3x the bytes received before address validation (MUST) | [RFC 9000 §8.1](https://www.rfc-editor.org/rfc/rfc9000#section-8.1) | holds |
| DTLS 1.3: 3x before the client address is verified (SHOULD) | [RFC 9147 §5.1](https://www.rfc-editor.org/rfc/rfc9147#section-5.1) | holds |
| CoAP: no large amplification factors for unauthenticated requests; the 10-byte request / 80-byte response example | [RFC 7252 §11.3](https://www.rfc-editor.org/rfc/rfc7252#section-11.3) | holds (3 quotes) |
| CISA: the bandwidth amplification factor (BAF) is defined on UDP payload bytes; its factor table; UDP does not validate source addresses; initial release 9 February 2014, last revised 18 December 2019 | [CISA TA14-017A](https://www.cisa.gov/news-events/alerts/2014/01/17/udp-based-amplification-attacks) | holds (4 items) |
| STUN 400 is "Bad Request: The request was malformed", and its reason phrase is diagnostic only | [RFC 8489 §14.8](https://www.rfc-editor.org/rfc/rfc8489#section-14.8); IANA STUN registry | holds (2 items) |
| SIP 400 wording; the reason phrase is for humans, not automata | [RFC 3261 §21.4.1, §7.2](https://www.rfc-editor.org/rfc/rfc3261#section-21.4.1) | holds (2 quotes) |
| HTTP clients should ignore the reason phrase | [RFC 9112 §4](https://www.rfc-editor.org/rfc/rfc9112#section-4) | holds |
| SMTP `501 Syntax error in command or arguments`, cited to §4.2.3 | [RFC 5321](https://www.rfc-editor.org/rfc/rfc5321) | **fails**: that wording is in §4.3.2. §4.2.3 reads "501 Syntax error in parameters or arguments" |
| SMTP clients act on the code, not the text; senders should not send bare codes | [RFC 5321 §4.2](https://www.rfc-editor.org/rfc/rfc5321#section-4.2) | holds |
| IRC `431 ERR_NONICKNAMEGIVEN` and `461 ERR_NEEDMOREPARAMS` | [RFC 2812 §5.2](https://www.rfc-editor.org/rfc/rfc2812#section-5.2); RFC 1459 §6.1 | holds (3 items) |
| A log message built from external input must neutralise special elements | [CWE-117](https://cwe.mitre.org/data/definitions/117.html) | holds |
| Sanitise CR/LF in logged event data; do not log access tokens; a validation failure is suspicious, not proof of an attack | [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html) | holds (3 quotes) |
| WARN is for situations that are unexpected but not necessarily wrong; INFO is for lifecycle events, kept to a minimum | [Apache Commons Logging guide](https://commons.apache.org/proper/commons-logging/guide.html) | holds (2 quotes; both stop before trailing sentences, and the meaning is unchanged) |

The failed claim is a section citation. The wording it quotes is real (RFC 5321 §4.3.2),
and §4.2.3 gives the same code a near-identical phrase, so the SMTP example still shows
a short, generic syntax-error phrase. No recommendation rests on it alone.

### Codebase facts

These facts were checked against the code on `85cfbbe`:

- **Six line references** from the inventory table. `COMMON_LISTEN_PORT` is at
  `MultiConnectionUDPServer.kt:548`. The client socket is at
  `MultiConnectionUDPClient.kt:200-201`. `keepaliveDatagram` / `unreliablePayloadOf` are
  at `TransportWireFormat.kt:81,150`. `onRetransmitTick` is at `ReliableChannelEngine.kt:156`.
  `actuateBytes` is at `UdpChannel.kt:41`. The client `0x81` branch is at
  `MultiConnectionUDPClient.kt:536`.
- **The editing hazard.** The raw NUL and SOH bytes in the string literal at
  `HandshakeCoordinatorTest.kt:577` are confirmed.
- **The two tests that expect silence** for a bare `Iam`:
  `HandshakeCoordinatorTest.kt:239-246` and `MultiConnectionUDPServerTest.kt:248`.
- **What an `Iam` with an empty slot gets from a registered origin today.** It parses,
  so it gets `REGISTERED 2` again (`HandshakeCoordinator.kt:243-254`).
- **Exceptions inside `accept` escape the listener.** `ResultExtensions.flatMap` is a
  plain `fold` that does not catch (`ResultExtensions.kt:19`), and
  `MultiConnectionUDPServer.receiveLoop` has no `try` (`:326-340`). So an exception
  thrown anywhere inside `HandshakeCoordinator.accept` ends the server's listener thread.
- **No rate limiting or address validation exists.** `webtools-udp/src/main` has no
  rate limiting, throttling, cookie or address-validation code. The only "backoff" in it
  is the reliable channel's per-message RTO backoff.

## Summary

### What the change touches

**Code.** The validity rule lives in `HandshakeProtocol.kt` and the routing in
`HandshakeCoordinator.kt`; both change in code and KDoc. Two more files change in KDoc
only, because their sentences describe refusal as coming only from `admit` and call every
other `Iam` well-formed:
- `MultiConnectionUDPServer.kt`: the class KDoc and the `admit` KDoc;
- `HandshakeWireFormat.kt`: the "`REFUSED` reply" section and `REFUSED_REPLY`.

Nothing else in `webtools-udp/src/main` recognises or tokenises an `Iam` line.
`isHandshake`, `extraTokenCount`, `handshakeMessage`, `CommonChannel` and the client stay
as they are ([inventory §3](codebase-blast-radius.md)).

**Tests.** Exactly two existing tests send a malformed line, and both now expect
`REFUSED`:
- the component test "a nameless Iam fails and registers nothing"
  (`HandshakeCoordinatorTest.kt:239-246`);
- the bare `Iam` in the integration no-reply list (`MultiConnectionUDPServerTest.kt:248`).

Two tests whose titles say "bare Iam" actually send `Iam alice`; they pin an absent
credential and stay. The parse tests (`HandshakeProtocolTest`,
`HandshakeProtocolGatingTest`) follow the new return type of `parseHandshake`.

New cases belong at levels 1, 2, 3 and 4a. Any level-4b case sends from a raw socket,
because `MultiConnectionUDPClient.handshake("")` fails locally once #52 lands. Component
tests pass pre-trimmed text, because only `CommonChannel` trims
([inventory §4, §10](codebase-blast-radius.md)).

**Docs.**
- Protocol doc §3 and §8 describe the rule. The as-built-quirk paragraph goes, and
  non-space whitespace inside a token stays a known gap, tracked in #53.
- 21 of the protocol doc's 37 source line references are out of date, because they were
  measured before #40 and the 2.0.0 KDoc pass. All of them are refreshed after the code
  and KDoc edits, from the anchors in [inventory §5](codebase-blast-radius.md).
- The architecture doc's handshake node and the README handshake sections get small
  wording updates.
- The architecture doc's stale `MultiConnectionUDPServer.kt:254` (at `:52`) is refreshed
  too, after the KDoc edits to that file.
- The comment at `ChannelByteGuardIntegrationTest.kt:25` gets the line range of the
  raw-socket peer in `FramingNonFunctionalTest.kt` (`:187-215` on `85cfbbe`).

**Editing hazards.**
- `HandshakeCoordinatorTest.kt:577` holds raw NUL and SOH bytes in a string literal. As a
  result, the ripgrep-based search skips the whole file (use `grep -a`).
  - The change replaces them with the escapes `"\u0000\u0001"`, which give the same
    string.
  - The replacement must be made with a byte-aware edit and checked with
    `grep -a -P '\x00'`.
  - Every other edit to the file keeps the bytes intact until that replacement lands.
- The docs are CRLF in the working tree.

### How a malformed line is represented and routed

**A sealed parse outcome.** `HandshakeProtocol.parseHandshake` returns a sealed outcome,
either a valid handshake or a malformed line with its defect (name missing, name empty,
credential empty), instead of `Result<Handshake>`. This fits the module's existing split:
- it already models domain outcomes as sealed types: `Admission`,
  `ReliableChannelEngine.SendOutcome`, `ReliableReorderBuffer.ReceiveOutcome` and
  `ReliableRetransmitBuffer.OfferResult`;
- it keeps `Result` for operational failures: a send, or a callback that throws.

KEEP-0127 reserves `Result` for generic failures and recommends a sealed type for a
failure that is handled locally. A malformed line is the path a hostile sender drives, so
no exception, with its stack trace, is built per datagram. The coordinator's `when` over
the outcome is checked for exhaustiveness
([findings §3.2, §5.A](malformed-line-handling.md)).

**The parse never throws.** An exception thrown anywhere inside
`HandshakeCoordinator.accept` ends the server's listener thread (see Verification). The
verb check is therefore part of the outcome, not a `require` precondition.

**The credential slot** is read with `getOrNull`. An absent credential is `null`, and
`Handshake` carries it as `""`, as `admit` documents. A credential slot that is present
but empty is malformed. `Handshake` keeps its shape.

**Routing.** The coordinator splits the trimmed text once and parses it, then branches:
- **malformed, from any origin:** reply `REFUSED malformed`.
  - It returns the send's `Result`, as the `admit` refusal does, so the listener adds no
    stack-trace WARN.
  - Nothing is registered, and there is no `admit`, no supersede and no
    `onClientConnect`.
  - An origin that already holds a registration keeps it exactly as it was, with no
    teardown.
- **valid, unknown origin:** today's `admit`, supersede, register, `REGISTERED 2` path,
  unchanged, including the register-before-send ordering that #49 concerns;
- **valid, registered origin:** `REGISTERED 2` again, never re-screened.

**Logging.** Each refused malformed line logs one WARN line carrying the origin, the
defect label, and whether the origin holds a registration (which is kept). It never logs
the line, its tokens, the name or the credential. The basis is CWE-117 and OWASP
(sanitise CR/LF, never log access tokens); under #53 a token can still carry CR/LF. The
message text is constant, so a per-reason rate bound (#41) can key on the defect. The
extra-token DEBUG line runs only for a valid line.

### What the refusal says

**The text.** The reply is `REFUSED malformed` (17 bytes), the same for all three shapes.
It is informative wire text, not a contract:
- a client treats any `REFUSED` as a refusal;
- an application's `admit` may return the same reason, so the text does not show which
  layer refused;
- the defect goes to the server log.

**Where it is built.** The bytes are built once, through
`HandshakeWireFormat.refusedMessage`, into an internal constant on `HandshakeProtocol`
beside `REGISTERED_DATAGRAM`. That avoids `refusedMessage` compiling a `Regex` on a path
that runs at a rate the sender chooses ([findings §6.1](refusal-reply-text.md)).

**Amplification.**
- A bare `Iam` (3 bytes) draws 17 bytes: a payload ratio of 5.7, or 1.45 on the wire with
  IPv4 and UDP headers.
- The worst case for a default server today is 2.4: `REGISTERED 2` in reply to `Iam a`,
  which also registers the origin. The README's own `invalid credential` refusal already
  reaches 5.2.
- No standard binds this protocol. QUIC's 3x limit is a MUST inside QUIC (RFC 9000 §8.1),
  DTLS 1.3's is a SHOULD (RFC 9147 §5.1), and CoAP's rule is qualitative (RFC 7252
  §11.3). The factors CISA lists as driving real attacks are orders of magnitude higher.

**Alternatives not used.**
- `REFUSED malformed handshake`: it repeats "handshake" inside the client's own exception
  message ("Server refused the handshake: …"), and it gives the 3-byte trigger a ratio of
  9.
- A reason that names the slot: that makes three replies instead of one, longer ones,
  and turns the reply into an oracle for a prober.
- A bare `REFUSED`: the reply carries a reason because the author of a hand-rolled client
  needs one (SMTP likewise advises against bare codes).

**A Boundary-Ring note for the protocol doc.**
- **Unverified sources.** The server answers some datagrams before it can know their
  source is genuine. The note lists the replies a forged source can draw, with their
  sizes and ratios. One of them is `REFUSED malformed` sent to the address of a client
  that is registered and mid-session.
- **That client's side of it.** A library client's session listener drops such a reply
  with a WARN, one per datagram until #41 bounds those logs. A third-party client may take
  it as a disconnect while the server keeps it registered.
- **No defences in the server.** There is no address validation and no reply rate
  limiting, so a deployment exposed to untrusted networks should rate-limit unregistered
  sources at its edge.

### Where the findings differ, and which view holds
- [malformed-line-handling.md §5.A](malformed-line-handling.md) sketches the verb check
  as a `require` precondition. A total parse holds instead, because an exception inside
  `accept` ends the listener thread.
- [malformed-line-handling.md C2](malformed-line-handling.md) asks whether a sealed
  outcome departs from the rule "`Result` for expected failures". It does not. The rule
  (`.aiassistant/rules/CLAUDE.md`) covers operational failures, and the module already
  uses sealed types for domain outcomes such as `Admission`.
- [refusal-reply-text.md C1](refusal-reply-text.md) weighs a bare `REFUSED`. The reply
  keeps its reason.
- [codebase-blast-radius.md §11 C1](codebase-blast-radius.md) and
  [malformed-line-handling.md C1](malformed-line-handling.md) recommend silence for a
  registered origin's malformed line, following DTLS, WireGuard and ENet. The design
  answers it `REFUSED malformed` instead, so every malformed line draws the same reply
  whatever the state of its origin, while the registration stays untouched.
  - **Costs the protocol doc states.** A forged source that knows a registered client's
    address can make the server send that client 17-byte unframed datagrams. A
    third-party client may also read `REFUSED` mid-session as a disconnect.
  - **Retransmits are unaffected.** A genuine retransmit repeats the registering line
    byte for byte, so it is well-formed and still gets `REGISTERED 2`.
  - **Behaviour changes for a registered origin.** The two empty-slot lines draw
    `REFUSED malformed` in place of `REGISTERED 2`. A bare `Iam` draws `REFUSED malformed`
    in place of nothing.

### Considerations

None.

### Known gaps
- The line numbers after the change are unknown until the code is written, so references
  into edited files are recomputed from the anchors in
  [inventory §5](codebase-blast-radius.md).
- Whether the edit tools round-trip the NUL and SOH bytes in `HandshakeCoordinatorTest.kt`
  is untested.
- No Kotlin or JVM server with a rule for a malformed first message from a registered peer
  was found. The comparable UDP systems (DTLS, WireGuard, ENet) discard silently, and
  none of them uses a text handshake.
- #41 has no design yet. Keying its rate bound on the defect label is an inference from
  its issue text.
