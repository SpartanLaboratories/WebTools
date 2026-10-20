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
- A malformed line from an origin with no registration is answered `REFUSED <reason>`,
  with one short fixed reason. It registers nothing, never reaches `admit()`, never
  supersedes a same-name registration, and fires no `onClientConnect`. A library
  client's `handshake()` fails with `HandshakeRefusedException`.
- A malformed line from an origin that already holds a registration is never answered
  `REFUSED`, and the registration is left as it is. The registered-origin lookup
  therefore runs before the malformed-line check.
- Tokens past the credential slot stay ignored, empty ones included: `Iam bob tok  x`
  is accepted with credential `tok`.
- A line whose first space-separated token is not exactly `Iam` (for example
  `Iam<TAB>bob`) is still not a handshake.
- Names and credentials that contain whitespace other than U+0020 (tab, CR/LF, NBSP)
  still register. #53 tracks that gap, together with the client-side checks in #52.
- No public API is added. The reason is informative wire text, not a contract.
- Every out-of-date source line reference in `docs/webtools-udp-protocol.md` is
  refreshed. The protocol doc describes the remaining whitespace gap as known and links
  #53.
- The ordering behind #49 (register, then send `REGISTERED 2`) is unchanged.

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
- **What a registered origin's malformed line gets.** Either no reply and a log line,
  which is how a bare `Iam` is handled today, or the same `REGISTERED 2` a well-formed
  retransmit gets. An `Iam` with an empty slot gets `REGISTERED 2` today, because it
  parses.
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
- the rule that `REFUSED` only goes to an origin with no registration, with a
  registered origin's registration untouched;
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
2026-10-19. That covers 54 items: 53 hold, 1 fails, and none is unclear.

| Claim | Source | Result |
|---|---|---|
| `split(Char)` keeps consecutive and trailing empty strings | [`kotlin.text.split`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/split.html); stdlib `Strings.kt` | holds |
| `trim()` removes leading and trailing whitespace only | [`kotlin.text.trim`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/trim.html) | holds |
| `Result` is a generic union of a value or any `Throwable` | [`kotlin.Result`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-result/) | holds |
| `Result.map` rethrows whatever its transform throws; `mapCatching` encapsulates it | [`kotlin.map`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/map.html) | holds |
| A `when` over a sealed type is checked for exhaustiveness | [Kotlin sealed classes](https://kotlinlang.org/docs/sealed-classes.html) | holds |
| `Result` is not meant for domain-specific errors. A sealed result type suits failures that are handled locally. Exceptions are costly to create. Exceptions are for preconditions and invariants | [KEEP-0127](https://github.com/Kotlin/KEEP/blob/main/proposals/stdlib/KEEP-0127-result.md) at `e688b25` (same path at `main`) | holds (4 quotes) |
| ripgrep treats a file that contains NUL as binary and skips it during recursive search; `-a` turns detection off | [ripgrep 14.1.1 `GUIDE.md`](https://github.com/BurntSushi/ripgrep/blob/14.1.1/GUIDE.md) | holds (3 quotes) |
| DTLS silently discards invalid records and keeps the association; answering them over UDP is NOT RECOMMENDED | [RFC 6347 §4.1.2.7](https://www.rfc-editor.org/rfc/rfc6347#section-4.1.2.7) | holds |
| DTLS: a server can be used as an amplifier, a cookie defeats spoofed sources, and `HelloVerifyRequest` is kept small | [RFC 6347 §4.2.1, §3.2.1](https://www.rfc-editor.org/rfc/rfc6347#section-4.2.1) | holds (3 quotes) |
| WireGuard does not respond to an unauthorised client | [wireguard.com/protocol](https://www.wireguard.com/protocol/) | holds |
| ENet drops, without a reply, an invalid connect and a connect from an established peer | [ENet `v1.3.18` `protocol.c`](https://github.com/lsalzman/enet/blob/v1.3.18/protocol.c) `:306-308`, `:1132-1138`, `:1224-1228` | holds (3 items) |
| QUIC: no more than 3x the bytes received before address validation (MUST) | [RFC 9000 §8.1](https://www.rfc-editor.org/rfc/rfc9000#section-8.1) | holds |
| DTLS 1.3: 3x before the client address is verified (SHOULD) | [RFC 9147 §5.1](https://www.rfc-editor.org/rfc/rfc9147#section-5.1) | holds |
| CoAP: no large amplification factors for unauthenticated requests; the 10-byte request / 80-byte response example | [RFC 7252 §11.3](https://www.rfc-editor.org/rfc/rfc7252#section-11.3) | holds (3 quotes) |
| CISA: the bandwidth amplification factor (BAF) is defined on UDP payload bytes; its factor table; UDP does not validate source addresses; initial release 9 February 2014, last revised 18 December 2019 | [CISA TA14-017A](https://www.cisa.gov/news-events/alerts/2014/01/17/udp-based-amplification-attacks) | holds (4 items) |
| STUN 400 is "Bad Request: The request was malformed", and its reason phrase is diagnostic only | [RFC 8489 §14.8](https://www.rfc-editor.org/rfc/rfc8489#section-14.8); IANA STUN registry | holds (3 items) |
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
