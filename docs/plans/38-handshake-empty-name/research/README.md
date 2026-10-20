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
