# How the server represents and routes a malformed `Iam` line

Code facts are on master `85cfbbe` (the research branch differs from it only by Markdown files). Paths under
`webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/` are abbreviated `main/`. This answers Question 2 of
[`README.md`](README.md); the inventory it builds on is [`codebase-blast-radius.md`](codebase-blast-radius.md).

## 1. Question

How should `HandshakeProtocol` report a malformed `Iam` line, how should `HandshakeCoordinator` route it, and what
should each outcome return and log, under these requirements:

- A line is malformed when, after the trimmed text is split on a single U+0020, the name slot is missing or `""`, or
  the credential slot is present but `""`. Nothing else is malformed. Tokens past the credential slot are ignored,
  empty ones included. `isHandshake` stays verb-only.
- An origin with no registration gets `REFUSED <one fixed reason>`; nothing is registered, `admit()` is not called,
  no same-name supersede happens, `onClientConnect` does not fire.
- An origin that already holds a registration is never answered `REFUSED`, and its registration is untouched, so the
  registered-origin lookup runs before the malformed-line check.
- `HandshakeProtocol` stays a pure, `internal`, socket-free object. No public API is added or changed. The
  register-before-send ordering is unchanged. The raw line never reaches a log.

## 2. Bottom line

- **A (representation): a sealed parse outcome (option iii).** `parseHandshake(tokens): HandshakeParse` returns
  `Valid(handshake)` or `Malformed(defect)`, where `defect` is a three-entry enum (`NAME_MISSING`, `NAME_EMPTY`,
  `CREDENTIAL_EMPTY`) with a fixed log label. The repo already models domain outcomes this way (`Admission`,
  `ReliableChannelEngine.SendOutcome`, `ReliableReorderBuffer.ReceiveOutcome`, `ReliableRetransmitBuffer.OfferResult`),
  and the Kotlin `Result` design notes say `Result` is "not designed to represent domain-specific error
  conditions". Options (i) and (iv) are rejected; (ii) is the fallback if zero churn in the existing protocol tests
  outweighs the points below.
- **B (credential slot):** read it with `tokens.getOrNull(2)`; `null` is absent, `""` is malformed. `Handshake` stays
  `Handshake(name: String, credential: String)` unchanged, because once `""` is rejected at the slot, `""` in a
  `Handshake` can only mean "absent", which is what `admit()` documents.
- **C (registered origin): Option 1, no reply and a WARN log line, for all three shapes.** A genuine retransmit is
  byte-identical to the line that registered the origin, so it is always well-formed; a malformed line from a
  registered origin is therefore never a retransmit, and silence loses nothing. Silence is also what DTLS, WireGuard
  and ENet do with an invalid message on an established or unauthenticated UDP association. Option 3 needs a second,
  laxer validity rule and still answers an accept to lines the server calls malformed.
- **D (unknown origin): return the `REFUSED` send's `Result`**, exactly as the `admit()` refusal does
  (`main/HandshakeCoordinator.kt:264-270`). Returning a failure would add a WARN with a stack trace at
  `main/MultiConnectionUDPServer.kt:336` on top of the log line the coordinator writes itself.
- **E (logs): WARN, one line per outcome, no exception, fields = origin and the defect label only.** Never the line,
  the name or the credential. Keep the message text constant and put the defect label in an argument, so #41's
  planned per-reason rate bound can key on it. Log the `extraTokenCount` DEBUG line only for a `Valid` line.
- **F:** split once (`val tokens = text.split(' ')`) inside the `null ->` branch, after the reserved-tag check. The
  coordinator relies on the documented "`text` is trimmed" contract and does not trim again; component-test inputs
  are written pre-trimmed.

## 3. Findings

### 3.1 What the code does today (repo facts)

- `main/HandshakeProtocol.kt:55-59`: `parseHandshake` is `runCatching { require(verb); require(size >= 2); Handshake(tokens[1], tokens.getOrElse(2) { "" }) }`.
  A bare `Iam` is a `Result.failure(IllegalArgumentException)`; `Iam  x` parses to `Handshake("", "x")`;
  `Iam bob  tok` parses to `Handshake("bob", "")`, indistinguishable from `Iam bob`.
- `main/HandshakeCoordinator.kt:227`: `isHandshake(text.split(' ')) -> handleHandshake(origin, text.split(' '))`, two splits.
- `main/HandshakeCoordinator.kt:243-293`: parse, then the `extraTokenCount` DEBUG, then `findByOrigin`; a hit repeats
  `REGISTERED 2` at INFO with no `admit`; a miss runs `admit`, the `Refused` branch (INFO log, returns `send(...)`'s
  `Result`), supersede, register, send, `onRegistered`.
- `main/MultiConnectionUDPServer.kt:331-337`: any `Result.failure` out of `accept` is logged at WARN with the
  exception, so with a failing parse a bare `Iam` produces a stack trace per datagram today.
- Drop paths in `classify` return `Result.success(Unit)` plus a log line (`:167-171`, `:186-187`, `:197-198`,
  `:222-231`), and a malformed frame from a registered origin is WARN (`:171`, "Malformed 0x81 probe").
- `main/ResultExtensions.kt:19`: `flatMap` does not catch; an exception thrown inside `accept` escapes to
  `receiveLoop` (`main/MultiConnectionUDPServer.kt:330`), which has no `try`.

### 3.2 Which constructs fit (evidence)

**Result is for generic failures; sealed types are for outcomes a caller branches on locally.** Tier: standard-library
design document (primary). The Kotlin `Result` KEEP states that `Result` "is not designed to represent
domain-specific error conditions", labels `fun findUserByName(name: String): Result<User>` as `// ERROR`, and shows a
`sealed class FindUserResult` with a `MalformedName` case as the shape for failures that "need different
business-specific handling code". It also says exceptions are "very expensive _to create_" and that their metadata
is "useless if exception is to be consumed by some business-logic to make some business-decision based simple on the
presence of exception". Source: [KEEP-0127](https://github.com/Kotlin/KEEP/blob/main/proposals/stdlib/KEEP-0127-result.md),
`main` at commit `e688b25`, read 2026-10-19. Two consequences here: the coordinator branches on the malformed case
locally and three ways (registered, unknown, valid), and the malformed case is the hot path of a hostile sender, so
allocating a stack-trace-bearing exception per datagram is the wrong cost model.

**Exhaustive `when` is the compile-time guarantee that matters.** Tier: language documentation. The bug being fixed is
a routing omission (a case reached `admit` that should not). With a sealed result the compiler forces every
call site to handle `Malformed`. Source: [Kotlin sealed classes](https://kotlinlang.org/docs/sealed-classes.html), read 2026-10-19.

**The repo already uses both tools, split by what is being modelled.** `Result<Unit>` carries I/O and
callback failures (`send`, `admit` throwing, `terminate`); sealed interfaces carry domain outcomes (`Admission`,
`SendOutcome`, `ReceiveOutcome`, `OfferResult`). A parsed handshake line is a domain classification, so it
belongs with the second group. Tier: this codebase (`main/Admission.kt`, `main/ReliableChannelEngine.kt:45`,
`main/ReliableReorderBuffer.kt:25`, `main/ReliableRetransmitBuffer.kt:26`).

### 3.3 Malformed message handling in mature UDP stacks (lens 1)

Tier: common practice, three independent systems.

- **DTLS (RFC 6347 §4.1.2.7):** "In general, invalid records SHOULD be silently discarded, thus preserving the
  association; however, an error MAY be logged for diagnostic purposes." Generating a response "is NOT RECOMMENDED"
  over UDP because "UDP forgery is so easy". This is the closest statement of the rule for a registered origin: an
  invalid message must not disturb, or be answered on, an established association.
- **WireGuard:** "the server does not even respond at all to an unauthorized client; it is silent and invisible."
  Silence towards unauthenticated senders, and no state created for them.
- **ENet `v1.3.18`, `enet_protocol_handle_incoming_commands` / `enet_protocol_handle_connect`:** an invalid connect
  from an unknown peer makes `enet_protocol_handle_connect` return `NULL`, which jumps to `commandError` with no
  reply; a connect command on an already-established peer (`if (peer != NULL) goto commandError;`) is dropped the
  same way. Neither case answers, and the established peer's state is untouched.

The repo's unknown-origin behaviour (a short `REFUSED` reply) deliberately differs from these for the pre-association
case, so that a library client's `handshake()` fails fast with `HandshakeRefusedException` instead of timing out.
That is a choice about the unknown origin only. For the registered origin all three systems agree on silence, which
is what Option 1 below does.

### 3.4 Logging untrusted input (lens 2)

Tier: established consensus (OWASP and CWE agree).

- CWE-117: "The product constructs a log message from external input, but it does not neutralize or incorrectly
  neutralizes special elements when the message is written to a log file."
- OWASP Logging Cheat Sheet: "Perform sanitization on all event data to prevent log injection attacks e.g. carriage
  return (CR), line feed (LF) and delimiter characters"; access tokens "should usually not be recorded directly in the
  logs"; validation failures are to be logged but "Treat these as suspicious events, not proof of an attack:
  unexpected input can also result from misuse or client defects."
- The credential slot is an opaque access token by design (`main/HandshakeWireFormat.kt:14-22`), and #53 means a
  name or credential token may carry CR/LF. The cheapest sanitisation is to not log any token. The log line carries a
  fixed-vocabulary label for the defect and the `InetSocketAddress` origin (derived from the socket, not the payload).
- Level norms: Apache Commons Logging describes WARN as "'almost' errors, other runtime situations that are
  undesirable or unexpected ... but not necessarily 'wrong'" and INFO as "Interesting runtime events (startup/shutdown)
  ... be conservative". A malformed client message is an unexpected situation, not a lifecycle event. This repo's own
  convention agrees: every other malformed-datagram drop is WARN, and an `admit()` refusal is INFO because it is an
  operator policy working as configured.

## 4. How comparable projects do it

| Project | Malformed message, no association | Malformed message, established peer | Source |
|---|---|---|---|
| DTLS (RFC 6347) | silently discarded, optional diagnostic log | silently discarded, "preserving the association" | RFC 6347 §4.1.2.7 |
| WireGuard | silent | n/a (handshake is authenticated before any state) | wireguard.com/protocol |
| ENet `v1.3.18` | `enet_protocol_handle_connect` returns `NULL`, no reply | a connect on a connected peer is dropped (`goto commandError`) | `protocol.c` at tag `v1.3.18` |
| This repo (target) | `REFUSED <fixed reason>`, nothing registered | silence, WARN, registration untouched | n/a |

No comparable library structures the check as a separate pre-pass: each classifies in one step into "valid, act" or
"invalid, drop" (ENet's `handle_connect` both validates and builds the peer, returning `NULL` on any defect).

## 5. Recommendations by sub-question

### A. How `HandshakeProtocol` reports a malformed line

Recommended shape (internal, in `HandshakeProtocol.kt`):

```kotlin
internal sealed interface HandshakeParse {
    data class Valid(val handshake: Handshake) : HandshakeParse
    data class Malformed(val defect: HandshakeDefect) : HandshakeParse
}

/** Which slot of an `Iam` line is defective. [label] is fixed text, safe to log. */
internal enum class HandshakeDefect(val label: String) {
    NAME_MISSING("name missing"),
    NAME_EMPTY("name empty"),
    CREDENTIAL_EMPTY("credential empty"),
}

fun parseHandshake(tokens: List<String>): HandshakeParse {
    require(isHandshake(tokens)) { "Not an $VERB line" }      // precondition: caller matched the verb
    val name = tokens.getOrNull(NAME_INDEX) ?: return Malformed(NAME_MISSING)
    if (name.isEmpty()) return Malformed(NAME_EMPTY)
    val credential = tokens.getOrNull(CREDENTIAL_INDEX)       // null = absent
    if (credential != null && credential.isEmpty()) return Malformed(CREDENTIAL_EMPTY)
    return Valid(Handshake(name, credential ?: ""))
}
```

`isHandshake` and `extraTokenCount` are unchanged. The three defects are tested in the order name, then credential,
so `Iam  x y` reports `NAME_EMPTY`. Tokens at index 3 and beyond are never inspected, which gives
`Iam bob tok  x` -> `Valid(Handshake("bob", "tok"))`.

| Option | Verdict | Why |
|---|---|---|
| (i) keep `Result<Handshake>`, treat every post-`isHandshake` failure as malformed | **reject** | `runCatching` catches every `Throwable`, so an unrelated bug inside the parse would be answered as `REFUSED malformed` instead of surfacing. It carries no defect for the log (the coordinator would have to parse the exception message). It builds an exception with a stack trace for every malformed datagram, at an attacker-chosen rate. It misuses `Result` for a locally-branched domain case (KEEP). |
| (ii) `MalformedHandshakeException : IllegalArgumentException(defect)` | **fallback** | Fixes the "any failure" conflation and carries the defect, and the existing tests stay green (`HandshakeProtocolTest.kt:40-43,55-58` and `HandshakeProtocolGatingTest.kt:27-30` still hold because the type is an `IllegalArgumentException`). It still allocates a stack trace per hostile datagram (unless `fillInStackTrace` is overridden, which is a workaround for using the wrong construct), the coordinator needs `exceptionOrNull() as? ...` and an `else` for failures it does not know, and nothing makes the compiler check that every case is routed. |
| **(iii) sealed outcome** | **recommend** | No allocation of throwable machinery, exhaustive `when` at the one call site, the defect is data, #53 can add enum entries without a signature change, and it matches `Admission` and the other sealed outcomes in this module. |
| (iv) separate validity check before parsing | **reject** | Validates and parses in two places with two copies of the slot logic ("validate, don't parse"). `parseHandshake` would still contain `getOrElse(2) { "" }` and a `Result` that can no longer fail, forcing `getOrThrow()` in the coordinator. A caller can forget the check. |

Test churn for (iii), against (i)/(ii) at zero:

- Mechanical: the five `parseHandshake(...).getOrThrow()` reads (`HandshakeProtocolTest.kt:20,27,35`,
  `HandshakeProtocolGatingTest.kt:17,22`) become `assertIs<HandshakeParse.Valid>(...).handshake`.
- Semantic: `HandshakeProtocolTest.kt:40-43` and `HandshakeProtocolGatingTest.kt:27-30` (bare `Iam`) assert
  `Malformed(NAME_MISSING)`; `HandshakeProtocolTest.kt:55-58` (the `IllegalArgumentException` type) is removed;
  the empty-list and non-`Iam` tests (`:45-53`) assert `assertFailsWith<IllegalArgumentException>` because the
  verb is now a documented precondition.
- New (level 4a): a truth table over token lists, including the three defects, `["Iam","bob","tok","","x"]` valid with
  credential `tok`, and `["Iam\tbob"]` failing the precondition (and `isHandshake` false).

The precondition (`require`) mirrors the current KDoc ("The caller is expected to have already matched [VERB]") and
is a programming-error check, which is what exceptions are for (KEEP). Its one caller passes the same list that
`isHandshake` just accepted, so it cannot fire in production. If the planner prefers a total function, add a
third `NotAHandshake` object to the sealed type instead; it costs one more `when` arm and no semantics. A thrown
exception there would escape `receiveLoop` (no `try`; `flatMap` does not catch), which is why the precondition must
stay tied to the single gated call.

### B. Modelling the credential slot

`tokens.getOrNull(CREDENTIAL_INDEX)` separates absent (`null`) from present-but-empty (`""`) at the point of reading.
`Handshake` does not change. Reasoning: `Handshake` is only ever built for a `Valid` line, and a `Valid` line has no
present-but-empty credential, so inside a `Handshake` the value `""` is unambiguous and means "no credential
supplied", exactly the contract `admit()` documents (`main/MultiConnectionUDPServer.kt` `admit` KDoc,
`main/HandshakeWireFormat.kt:18-19`). The conflation that `getOrElse(2) { "" }` caused no longer exists, because the
only place the two states differ (the slot read) now decides before the object is built.

Rejected: a `String?` credential (changes `admit`'s public signature and every test that builds
`Handshake("bob", "")`); a `Credential` value class or `Absent`/`Present` sum (a type whose only consumer
immediately maps it back to `String`; no second use). Only change to the class: its KDoc property text, to say
`""` means absent and that an empty present slot is `Malformed`.

### C. Registered origin: see Considerations C1 (recommendation: Option 1)

### D. Unknown-origin return value

Return the `send` result, as the `admit()` refusal does. The existing comment at `:266` ("A REFUSED datagram that
went out is a successful server action, not a failure") is the repo's stated rule for this exact situation. Reasons:

- A failure return makes `MultiConnectionUDPServer.kt:336` WARN with a stack trace in addition to the coordinator's own
  line: two WARNs, one with a stack trace, for one hostile datagram.
- `Result.failure` out of `accept` then means only "an I/O or callback failure occurred" (reply could not be sent,
  `admit` threw). The `accept` KDoc `@return` at `:143-144` ("... or `Result.failure` if a recognised handshake was
  malformed ...") must be rewritten to say so.
- A registered-origin drop (Option 1) returns `Result.success(Unit)` with a log line, like every other drop path.

### E. Logging

| Outcome | Level | Fields | Notes |
|---|---|---|---|
| Unknown origin, malformed, `REFUSED` sent | WARN | origin, `defect.label` | one line, no exception object |
| Registered origin, malformed, dropped (Option 1) | WARN | origin, `defect.label` | say "registration kept"; same level as the malformed-probe-from-a-registered-origin WARN at `:171` |
| `extraTokenCount` DEBUG | DEBUG | count | only when the line is `Valid` |

Level reasoning: a malformed `Iam` is unexpected client input, which this class logs at WARN everywhere else
(`:171`, `:187`, `:224`, `:230`); a bare `Iam` is already WARN today (via the listener). INFO would match the `admit()`
refusal but that refusal is a configured policy outcome, not malformed input; DEBUG would hide a client bug (an
empty name) from an operator who sees only `HandshakeRefusedException` on the client. An INFO line is a defensible
alternative if the aim is not to add to #41's surface, but #41's own text says it will cover every per-datagram drop
reason, so WARN joins that set rather than evading it.

Shape, so #41's "first occurrence immediately, then a suppressed-count summary" can key on the defect:

```kotlin
log.warn("Refusing malformed handshake from {} ({})", origin, line.defect.label)
log.warn("Dropping malformed handshake from registered origin {} ({}); registration kept", origin, line.defect.label)
```

Never log `tokens`, `text`, the name or the credential here (CWE-117, OWASP; #53 means they may hold CR/LF).
The existing name-bearing lines (`:261,265,278,290`) are #53's concern and are not copied. `origin` is safe: it is
rendered from the socket's address, not from payload bytes.

`extraTokenCount` (P6): keep the DEBUG line before the origin branch, guarded by `line is Valid`. This preserves
today's behaviour for every well-formed line (retransmits included) and avoids a misleading count on a malformed line
(`Iam bob  tok` has `tokens.size == 4`, so `extraTokenCount` would report 1 when the real defect is the empty slot).

### F. Shape points

Coordinator control flow (parse once; branch on the lookup first, then on the outcome):

```kotlin
null -> if (byte0 != null && byte0 >= 0x80) { /* reserved-tag drop, unchanged */ } else {
    val tokens = text.split(' ')                              // one split
    if (HandshakeProtocol.isHandshake(tokens)) handleHandshake(origin, tokens)
    else Result.success(Unit).also { log.warn("Unframed datagram from {}; sender may be pre-2.0, dropping", origin) }
}

private fun handleHandshake(origin: InetSocketAddress, tokens: List<String>): Result<Unit> {
    val line = HandshakeProtocol.parseHandshake(tokens)       // pure; says nothing about routing
    if (line is HandshakeParse.Valid) HandshakeProtocol.extraTokenCount(tokens).takeIf { it > 0 }
        ?.let { log.debug("Ignoring {} extra handshake token(s)", it) }

    // The registered-origin lookup comes BEFORE the malformed branch: a registered origin never gets REFUSED.
    if (registrations.findByOrigin(origin) != null) return when (line) {
        is HandshakeParse.Valid -> { log.info("Repeating handshake reply for already-registered origin {}", origin)
                                     send(HandshakeProtocol.REGISTERED_DATAGRAM, origin) }   // no re-screening, as today
        is HandshakeParse.Malformed -> Result.success(Unit).also { log.warn(/* drop line */) }
    }
    return when (line) {
        is HandshakeParse.Malformed -> { log.warn(/* refuse line */); send(refusedDatagram(MALFORMED_REASON), origin) }
        is HandshakeParse.Valid -> admitAndRegister(origin, line.handshake)   // body of today's `?: run { ... }`
    }
}
```

- Extracting the `?: run { ... }` body (`:255-292`) into `admitAndRegister(origin, handshake)` removes the
  `return@flatMap` gymnastics and is optional tidiness; the `admit` throw/refuse/supersede/register logic inside it is
  unchanged, including the #49 ordering.
- `MALFORMED_REASON` is a `private const val` in the coordinator, passed to the existing
  `HandshakeWireFormat.refusedMessage`; no public constant (wording is Question 3).
- Parsing before the lookup is safe: `parseHandshake` is pure and cheap. The malformed *check* (the `Malformed` arm)
  is what runs after the lookup, which is what the requirement fixes. A reader may expect the strict literal order
  (lookup, then parse); a one-line comment at the lookup states the intent. Parsing inside each arm is equivalent but
  duplicates the call.
- **The double split** costs two allocations of up to a full datagram's worth of tokens per hostile datagram (the
  integration test sends a 4096-character token); one split halves it. `when` cannot bind a value from its
  conditions, so the hoist needs the nested `else` shown. Placing the split after the reserved-tag check means
  a datagram with a tag `>= 0x80` is never split.
- **Trim contract (P2):** rely on it. `accept`'s KDoc states `text` is trimmed (`:141`) and `CommonChannel.kt:78`
  is the one place that trims; a second `trim()` in the coordinator would hide a violated contract and would make the
  coordinator accept lines the real wire path never delivers. Consequence for tests: component tests feed
  pre-trimmed text, and the "trailing space on the wire is accepted" case (`Iam bob ` -> trimmed `Iam bob`, valid) is
  a level-3 raw-socket test, not a level-2 one. The two-argument `accept(origin, text)` overload (`:240`) does not
  trim; making it mirror production would remove the trap for test authors but is a test-seam change outside this
  work, so it is left alone and documented in its KDoc.

## 6. Pitfalls and regrets

- **`Result` as a domain enum.** The KEEP marks `Result<User>` for a locally-handled "not found" as an error; the
  current `parseHandshake` is that shape, and its cost showed up as the listener WARN with a stack trace for a
  routine malformed datagram.
- **"Any failure is malformed"** (option i) turns a bug in the parse into a client-visible `REFUSED`.
- **Reply to a registered origin on malformed input** gives a spoofer who knows a registered `ip:port` a reflector
  and probe oracle, and it contradicts DTLS's rationale. RFC 6347 notes that answering invalid records over UDP is
  "extremely susceptible to denial-of-service (DoS) attacks because UDP forgery is so easy".
- **Logging the line or tokens** is CWE-117 and, for the credential, logs an access token.
- **A new WARN per malformed datagram** is another flood source until #41 lands; the per-defect label is the key for
  its rate bound. P5 also stays: a spoofed malformed line still refreshes a registered origin's idle timer, because
  `accept` stamps liveness before classification (`:147-152`); this change does not alter that.
- **Component tests that pass untrimmed text** see an empty trailing slot (P2); write inputs pre-trimmed.

## 7. Recommendation for this repo

Adopt the sealed outcome in `HandshakeProtocol`, keep `Handshake` unchanged, read the credential with `getOrNull`,
and route in the coordinator as sketched in F: one split, parse, lookup, then branch. An unknown origin's malformed
line gets `REFUSED` and the send's `Result`; a registered origin's gets silence plus a WARN and `Result.success`.
Nothing about the single-space split, `isHandshake`, `extraTokenCount`, `admit`, the supersede, or #49 changes.
The obvious option to reject is (i), keeping `Result<Handshake>` and treating every failure as malformed: it is the
smallest diff, but it turns parse bugs into protocol replies, creates a stack trace per hostile datagram and leaves no
place for the defect label the log line needs. Option 3 for the registered origin is rejected for the reasons in C1.

## Considerations

### C1. What a registered origin gets for a malformed line

All three options keep the registration, never send `REFUSED`, and run the lookup before the malformed branch.

| Input from a registered origin | Today | Option 1: silence + WARN | Option 2: `REGISTERED 2` | Option 3: today's behaviour |
|---|---|---|---|---|
| bare `Iam` | silence + listener WARN with stack trace | silence + coordinator WARN, `success` | **changes**: `REGISTERED 2` (3 B -> 12 B) | silence + listener WARN with stack trace |
| `Iam  x` | `REGISTERED 2` | **changes**: silence + WARN | `REGISTERED 2` | `REGISTERED 2` |
| `Iam bob  tok` | `REGISTERED 2` | **changes**: silence + WARN | `REGISTERED 2` | `REGISTERED 2` |

Code shape per option:

- **Option 1:** the `Malformed` arm in the registered branch returns `Result.success(Unit)` after a WARN (sketched in F).
- **Option 2:** the registered branch needs no parse at all: `findByOrigin(origin)?.let { send(REGISTERED) }` runs first,
  and the parse and malformed branch apply only to unknown origins. Smallest code.
- **Option 3:** needs today's lenient rule next to the strict one. Either `HandshakeProtocol` exposes a second predicate
  ("at least one token after the verb") and the coordinator applies it to registered origins, or the coordinator
  special-cases `defect == NAME_MISSING` to return a failure and treats `NAME_EMPTY` and `CREDENTIAL_EMPTY` as
  valid retransmits. Either way the lenient rule exists only to preserve an accident (the parse happened to succeed
  for the empty-slot shapes and fail for the bare verb), and the bare-`Iam` case keeps a `Result.failure` path and its
  stack-trace WARN.

Comparison:

- **Protocol coherence.** Option 1 gives the registered peer one rule: well-formed -> `REGISTERED 2`, anything else
  -> nothing. Option 2 answers `REGISTERED 2` (an accept) to lines the server defines as malformed; the issue being
  fixed is precisely that such a line was accepted, and the accept for a registered peer would say so again.
  Option 3 does the same for two of the three shapes and silence for the third, so the contract is "a registered peer
  gets an accept unless it sent a bare `Iam`", which cannot be explained from the wire format.
- **Never re-screen a retransmit.** The rule exists to stop `admit()` (a non-deterministic policy, e.g. a capacity gate)
  being re-run on a stray retransmit, which could refuse an already-admitted client and desync the two sides
  (`main/HandshakeCoordinator.kt:249-252`;
  `docs/plans/11-handshake-refusal-and-credential/plan.md`, alternatives table, "Re-run `admit` on every
  retransmit"). A shape check is deterministic, and a genuine retransmit is byte-identical to the line that
  registered the origin, so it always passes the check the original passed. Option 1 therefore cannot drop a genuine
  retransmit, and does not reopen the desync. (This holds as long as the rule does not change between a registration
  and its retransmit; registrations are in memory only.)
- **Reflection and probing exposure.** Option 1: zero bytes out. Option 2: 12 bytes for a 3-byte trigger (4x) to an
  attacker-chosen registered origin, at line rate, and a probe oracle that confirms an `ip:port` is registered;
  `Iam x` already draws 12 bytes for 5, so the marginal gain is small but nonzero. Option 3: same as Option 2 for two shapes.
  RFC 6347 and WireGuard both choose silence for exactly this reason.
- **Simplicity.** Option 2 is the least code; Option 1 is one extra `when` arm; Option 3 is the most (a second rule).
- **Test and doc cost.** Options 1 and 2 each add three level-2 cases (one per shape) and one protocol-doc row
  ("registered origin: malformed line -> silence" or "-> `REGISTERED 2`"). Option 3 adds the same three cases plus a
  doc passage explaining why one shape is treated differently. None flips an existing test; none sends a malformed
  line from a registered origin today.
- **Behaviour change visible to a consumer.** Option 1 changes the two empty-slot shapes from `REGISTERED 2` to silence
  for a registered origin. A library client cannot produce them after #52, and a genuine retransmit never
  has them, so the only senders affected are hand-rolled clients that change their line after registering.

**Recommendation: Option 1.** It is the only option under which the server's contract with a registered peer is one
sentence, it follows the DTLS/WireGuard/ENet practice for UDP, it has no reflection cost, and by the retransmit
argument above it cannot affect a genuine client. It also removes the stack-trace WARN a bare `Iam` draws today.

### C2. Representation: sealed outcome versus `Result` with a typed exception

The project paradigm names `Result` for expected failures. This research recommends a sealed outcome for the parse
because the parse result is branched on locally and is the hostile-input hot path, the Kotlin KEEP reserves `Result`
for generic failures, and the module already has sealed domain outcomes. Option (ii) (a typed
`IllegalArgumentException` subclass inside `Result<Handshake>`) is the fallback if keeping all existing parse tests
unchanged and `Result`-everywhere uniformity outweigh those points; it costs a stack trace per malformed datagram and
loses exhaustive checking. Recommendation: the sealed outcome. This needs confirmation because it departs from the
literal paradigm wording.

## 8. Load-bearing claims

1. **`Result` is not for domain-specific errors; use a sealed type when failures are handled locally.** Quote: "The
   `Result` class is not designed to represent domain-specific error conditions." and "If there is a business need to
   distinguish different failures and process these different failures in distinct ways on each invocation site, then
   the following kind of signature shall be considered: `sealed class FindUserResult { ... data class MalformedName(val name: String) ...}`".
   Source: [KEEP-0127 "Error-handling style and exceptions"](https://github.com/Kotlin/KEEP/blob/main/proposals/stdlib/KEEP-0127-result.md),
   `main` at `e688b2587fd5cba8266ed59f2c075e8e4792f4d2`, read 2026-10-19; `Result` since Kotlin 1.3.
2. **Exceptions are costly to create and the wrong tool for branching on.** Quote: "Exceptions are also very expensive
   _to create_, but relatively cheap to _throw_, because they carry a lot of additional metadata ... but all that
   metadata is useless if exception is to be consumed by some business-logic to make some business-decision based
   simple on the presence of exception. Use nullable types or domain-specific classes to represent failures that need
   specific handling." Same source and date.
3. **Exceptions are for precondition and invariant checks.** Quote: "This includes several broad areas - logic and
   programming errors like index bounds problems and various checks for internal invariants and preconditions".
   Same source and date.
4. **`Result` is a generic encapsulation of success or an arbitrary `Throwable`.** Quote: "A discriminated union that
   encapsulates a successful outcome with a value of type T or a failure with an arbitrary Throwable exception."
   Source: [kotlin.Result API](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-result/), since Kotlin 1.3, read 2026-10-19.
5. **A `when` over a sealed type is compiler-checked exhaustive.** Quote: "The `when` expression, used with a sealed
   class, allows the Kotlin compiler to check exhaustively that all possible cases are covered. In such cases, you
   don't need to add an `else` clause". Source: [Kotlin sealed classes](https://kotlinlang.org/docs/sealed-classes.html), read 2026-10-19.
6. **DTLS silently discards invalid records and advises against answering them over UDP.** Quote: "In general, invalid
   records SHOULD be silently discarded, thus preserving the association; however, an error MAY be logged for
   diagnostic purposes." and "Note that if DTLS is run over UDP, then any implementation which does this will be
   extremely susceptible to denial-of-service (DoS) attacks because UDP forgery is so easy. Thus, this practice is
   NOT RECOMMENDED for such transports."
   Source: [RFC 6347 §4.1.2.7](https://www.rfc-editor.org/rfc/rfc6347#section-4.1.2.7), January 2012, read 2026-10-19.
7. **WireGuard does not respond to unauthenticated senders.** Quote: "In fact, the server does not even respond at
   all to an unauthorized client; it is silent and invisible."
   Source: [wireguard.com/protocol](https://www.wireguard.com/protocol/), read 2026-10-19 (page is unversioned).
8. **ENet drops an invalid connect from an unknown peer and a connect on an established peer without a reply.** Quote
   (code): `case ENET_PROTOCOL_COMMAND_CONNECT: if (peer != NULL) goto commandError; peer = enet_protocol_handle_connect (host, header, command); if (peer == NULL) goto commandError;`
   and, in `enet_protocol_handle_connect`, `if (channelCount < ENET_PROTOCOL_MINIMUM_CHANNEL_COUNT || channelCount > ENET_PROTOCOL_MAXIMUM_CHANNEL_COUNT) return NULL;`.
   Source: [`protocol.c` at `v1.3.18`](https://github.com/lsalzman/enet/blob/v1.3.18/protocol.c) (tag object `2662c0de09e36f2a2030ccc2c528a3e4c9e8138a`), read 2026-10-19.
9. **Untrusted input in a log line must be sanitised; CR/LF is the named case.** Quote (CWE): "The product constructs
   a log message from external input, but it does not neutralize or incorrectly neutralizes special elements when
   the message is written to a log file." Source: [CWE-117](https://cwe.mitre.org/data/definitions/117.html), read 2026-10-19.
   Quote (OWASP): "Perform sanitization on all event data to prevent log injection attacks e.g. carriage return (CR),
   line feed (LF) and delimiter characters (and optionally to remove sensitive data)". Source:
   [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html), read 2026-10-19 (living document).
10. **Access tokens do not belong in logs, and validation failures are suspicious but not proof of attack.** Quote:
    "The following should usually not be recorded directly in the logs, but instead should be removed, masked,
    sanitized, hashed, or encrypted: ... Access tokens" and "Treat these as suspicious events, not proof of an attack:
    unexpected input can also result from misuse or client defects." Same OWASP source and date.
11. **WARN is for unexpected-but-not-wrong runtime situations; INFO is for lifecycle events kept to a minimum.** Quote:
    "Use of deprecated APIs, poor use of API, 'almost' errors, other runtime situations that are undesirable or
    unexpected, but not necessarily "wrong"." and "Interesting runtime events (startup/shutdown). Expect these to be
    immediately visible on a console, so be conservative and keep to a minimum."
    Source: [Apache Commons Logging guide, "Message Priorities/Levels"](https://commons.apache.org/proper/commons-logging/guide.html), read 2026-10-19 (the WebFetch text paraphrases the punctuation; a verifier should compare the two level descriptions).
12. **`String.split(Char)` keeps empty tokens, including a trailing one.** Carried from `codebase-blast-radius.md` §15.1.
    Quote: "It also implies that for char sequences ending with one of delimiters, the resulting list will end with an
    empty string." Source: [kotlin.text.split](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/split.html), read 2026-10-19.

## 9. Known gaps

- No build or test run, by design; the Kotlin sketches are unchecked and are shapes, not tested code.
- The refusal reason wording (`MALFORMED_REASON`) is Question 3's.
- No comparable open-source Kotlin/JVM UDP server with a registered-peer malformed-first-message rule was found; the
  corroboration for Option 1 is DTLS (a spec), WireGuard (a protocol description) and ENet (C code), which share the
  silent-discard stance but are not text-handshake protocols. TCP text protocols (SMTP, IRC) answer malformed
  commands on an established connection, but the connection there is the association and reply cost is not
  forgeable, so they were not treated as comparable and are not cited.
- WARN versus INFO for the unknown-origin refusal is a judgement from this class's own convention and the Commons
  Logging descriptions; no source states a level for "malformed handshake from an unauthenticated client".
- #41 has no design yet; the claim that a defect label is the right rate-bound key is an inference from its issue text
  ("first occurrence of a given drop reason ... then suppress further identical-reason logs").
- Commons Logging quotes (claim 11) came through a summarising fetch; their ellipses and punctuation should be
  checked against the page.
- Whether the `Edit`/`Write` tooling round-trips the NUL/SOH bytes in `HandshakeCoordinatorTest.kt:577` is not tested
  (carried from the inventory); it matters when the new component tests are added to that file.
