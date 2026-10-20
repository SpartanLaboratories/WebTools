# What the `REFUSED` reply to a malformed `Iam` should say

Code facts are on master `85cfbbe` (the research branch differs from it only by Markdown). Paths under
`webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/` are abbreviated `main/`. This answers Question 3 of
[`README.md`](README.md) and builds on the routing in [`malformed-line-handling.md`](malformed-line-handling.md).

## 1. Question

What exact text should the server send as `REFUSED <reason>` to an unregistered origin whose `Iam` line is malformed
(bare `Iam`, empty name slot, present-but-empty credential slot), and how should the protocol document describe it?
Requirements: one short fixed reason, identical for all three shapes; built by the existing
`HandshakeWireFormat.refusedMessage(reason)`; no public constant or other public API; informative wire text, not a
contract; a library client still receives `HandshakeRefusedException(reason)` from `handshake()`; a registered origin is
never answered `REFUSED` (not this question).

## 2. Bottom line

- **Send `REFUSED malformed`** (17 bytes, reason `malformed`). One lower-case word, in the style of the repo's own
  `banned`, `invalid credential`, `server full`. It is the cross-protocol vocabulary for a rejected opening message
  (STUN 400 "Bad Request: The request was malformed", SIP 400 "malformed syntax") without borrowing HTTP's phrase, and it
  matches the word the server log and the protocol doc already use.
- **Not `REFUSED malformed handshake`.** It stutters in the client's own message (`Server refused the handshake:
  malformed handshake`, `HandshakeRefusedException.kt:11`) and, at 27 bytes, makes the 3-byte trigger a 9.0x payload
  amplifier, the highest ratio of anything the server sends an unverified origin. `REFUSED malformed` gives 5.7x on that
  trigger, within a hair of what the README's own `Admission.Refused("invalid credential")` already yields (5.2x), and
  2.8x on `Iam  x`.
- **Constant:** `private const val MALFORMED_REASON = "malformed"` plus an `internal val MALFORMED_REFUSAL_DATAGRAM:
  ByteArray` on the internal `HandshakeProtocol` object, next to `REGISTERED_DATAGRAM`
  (`main/HandshakeProtocol.kt:30`), built once with `HandshakeWireFormat.refusedMessage`. Precomputing is warranted here
  because this is the hostile hot path and `refusedMessage` compiles a `Regex` on every call (`main/HandshakeWireFormat.kt:118`).
- **The text is informative and cannot prove which layer refused.** An application's `admit()` can return `malformed`
  too, so a third-party author tells a protocol rejection from a policy refusal by the server log's defect label and by
  re-checking their own line, not from the reason. The protocol doc says so.
- **Document the reflection surface in the protocol doc** as a Boundary-Ring note: list the three replies an unverified
  origin can draw, their sizes, the smallest triggers and ratios, and that there is no address validation or reply rate
  limit. Draft wording is in section 6.
- A bare `REFUSED` is the only option at or below QUIC's 3x bound for the 3-byte trigger (2.3x). Because a reply carrying a
  reason is part of the settled behaviour, it is held in **Considerations C1**, not picked.

## 3. Findings

### 3.1 Amplification arithmetic (derived from the code's byte counts)

Payload bytes are UTF-8 text: `Iam` 3, `Iam x` 5, `Iam  x` 6, `REGISTERED 2` 12, `REFUSED` 7, `REFUSED malformed` 17,
`REFUSED malformed handshake` 27. "On wire" adds 28 bytes (20 IPv4 + 8 UDP, no options) to both directions. The ratio is
reply bytes divided by trigger bytes, one reply per trigger datagram (so packet-count amplification is 1.0 in every row).

| Trigger (payload / on wire) | Reply | Reply bytes (payload / on wire) | Ratio, payload | Ratio, on wire |
|---|---|---|---|---|
| bare `Iam` (3 / 31) | bare `REFUSED` | 7 / 35 | 2.33 | 1.13 |
| bare `Iam` (3 / 31) | `REFUSED malformed` | 17 / 45 | 5.67 | 1.45 |
| bare `Iam` (3 / 31) | `REFUSED malformed handshake` | 27 / 55 | 9.00 | 1.77 |
| `Iam  x` (6 / 34) | bare `REFUSED` | 7 / 35 | 1.17 | 1.03 |
| `Iam  x` (6 / 34) | `REFUSED malformed` | 17 / 45 | 2.83 | 1.32 |
| `Iam  x` (6 / 34) | `REFUSED malformed handshake` | 27 / 55 | 4.50 | 1.62 |
| **Already sent today** | | | | |
| `Iam x` (5 / 33), smallest well-formed | `REGISTERED 2` (also creates a registration) | 12 / 40 | 2.40 | 1.21 |
| `Iam x` | `REFUSED banned` (README example) | 14 / 42 | 2.80 | 1.27 |
| `Iam x` | `REFUSED server full` (README example) | 19 / 47 | 3.80 | 1.42 |
| `Iam x` | `REFUSED invalid credential` (README example) | 26 / 54 | 5.20 | 1.64 |

Reading it:

- The default server (`admit` returns `Admitted`, `main/MultiConnectionUDPServer.kt:385-386`) has a worst case today of
  2.4x. The reply text is the only thing that moves the worst case, because the trigger is fixed at 3 bytes.
- `REFUSED malformed` raises the default worst case to 5.7x, and only for a bare `Iam`; `Iam  x` stays at 2.8x, under 3x.
  `REFUSED malformed handshake` raises it to 9.0x for the bare trigger and 4.5x for `Iam  x`.
- A stateless malformed reply creates no registration. The 12-byte `REGISTERED 2` reply to the 5-byte trigger already
  does, which is a larger lever for a spoofer than the reply bytes.
- On-wire ratios stay below 2 for every row, because the 28-byte header dominates a 3-byte payload. The payload ratio is
  the standard measure (CISA defines BAF on payload bytes), and is the stricter one.

### 3.2 What the standards and advisory say

- **QUIC (consensus for a protocol that answers before validation).** RFC 9000 §8.1 makes it a MUST that a server
  not send more than three times the bytes it received before validating the client address, counting even datagrams it
  discards. Evidence tier: standard. RFC 9000, May 2021.
- **DTLS 1.3 repeats the 3x rule as a SHOULD** (RFC 9147 §5.1, April 2022) and treats a completed cookie exchange as the
  point where an address becomes valid. **DTLS 1.2** (RFC 6347 §4.2.1, January 2012) states the amplifier threat, answers
  it with the stateless `HelloVerifyRequest` cookie, and designs that reply to be small (§3.2.1) so it does not fragment.
  RFC 6347 does not give a numeric ratio. Evidence tier: standard, two independent specs plus one successor.
- **CoAP** (RFC 7252 §11.3, June 2014) says "large amplification factors SHOULD NOT be provided in the response if the
  request is not authenticated" and gives, as an example of an acceptable mitigation, cutting a 10-byte request's answer
  from 1016 bytes to 80 bytes (8x). "Large" is not quantified. Evidence tier: standard, qualitative.
- **CISA TA14-017A** (initial release February 9, 2014, last revised December 18, 2019) defines BAF as reply UDP payload
  bytes over request UDP payload bytes and lists protocols from mDNS (2 to 10) and NetBIOS/BitTorrent (3.8) through DNS
  (28 to 54) to NTP (556.9) and Memcached (10,000 to 51,000). Evidence tier: advisory. Context: factors under 10 are
  still listed as exploited vectors, but the factors that drive attacks are two orders of magnitude above 5.7.

What this means here. No standard sets a numeric limit that binds this protocol: QUIC's 3x is a MUST only inside QUIC,
and DTLS 1.3's is a SHOULD. Measured against them, a bare-`Iam` reply of any length above 9 bytes breaks 3x, and only a
bare `REFUSED` (7 bytes) fits. CoAP's own worked example accepts 8x as a mitigated outcome. The defensible position is:
keep the reply short and fixed, keep it near the ratio the server's own refusals already produce, and tell deployers
the surface exists. `REFUSED malformed` does that; `REFUSED malformed handshake` exceeds every number already in the
module for no gain in clarity.

### 3.3 How comparable protocols word a rejection of a malformed opening message

Evidence tier: common practice (four independent protocols), with a split on how specific the text is.

- **STUN** (RFC 8489 §14.8, February 2020): error 400, IANA phrase "Bad Request"; "The reason phrase is meant for
  diagnostic purposes and can be anything appropriate for the error code."
- **SIP** (RFC 3261 §21.4.1, June 2002): 400 Bad Request, "The request could not be understood due to malformed syntax.
  The Reason-Phrase SHOULD identify the syntax problem in more detail, for example, 'Missing Call-ID header field'."
  §7.2: "The Status-Code is intended for use by automata, whereas the Reason-Phrase is intended for the human user. A
  client is not required to examine or display the Reason-Phrase."
- **HTTP** (RFC 9112 §4, June 2022): "A client SHOULD ignore the reason-phrase content because it is not a reliable
  channel for information".
- **SMTP** (RFC 5321 §4.2.3, October 2008): 501 "Syntax error in command or arguments". §4.2: "An SMTP client MUST
  determine its actions only by the reply code, not by the text ... any text, including no text at all (although senders
  SHOULD NOT send bare codes), MUST be acceptable."
- **IRC** (RFC 2812 §5.2, April 2000; same numerics in RFC 1459): `431 ERR_NONICKNAMEGIVEN ":No nickname given"` and
  `461 ERR_NEEDMOREPARAMS "<command> :Not enough parameters"`. IRC names the defective slot, and runs over TCP where a
  reply cannot be redirected at a third party by a forged source.

Takeaways. The generic code-plus-short-phrase pattern ("Bad Request", "Syntax error") is the norm for the rejection
itself; slot-specific wording (IRC 431, SIP's suggested detail) exists where the transport is connection-oriented or the
peer is already authenticated. The reason text is informative everywhere (HTTP, SIP, STUN, SMTP all say a client must not
depend on it), which is the status this protocol doc gives the new reason. SMTP's "senders SHOULD NOT send bare codes"
is the one external argument for sending some reason rather than a bare `REFUSED`.

### 3.4 This repo's conventions

- Reasons in the README and KDoc are lower-case, one to two words: `banned`, `invalid credential`, `server full`
  (`README.md:227-229`). `Admission.Refused` asks for terse text "echoed to an unauthenticated peer"
  (`main/Admission.kt:17-18`).
- `refusedMessage` trims, collapses control and whitespace runs, and returns a bare `REFUSED` for a blank reason
  (`main/HandshakeWireFormat.kt:117-120`). `malformed` passes through unchanged.
- `HandshakeRefusedException` prefixes the reason with "Server refused the handshake: " (`main/HandshakeRefusedException.kt:11`),
  so a reason that repeats "handshake" reads badly, and a reason starting `invalid` would read as a sibling of the
  credential refusal.
- `HandshakeWireFormatTest` already pins `refusalReason("REFUSED") == ""` and the trimming of reasons
  (`HandshakeWireFormatTest.kt:56-58`); a bare reply needs no new client code, and a reasoned reply needs none either.
- The protocol doc already separates contract from informative values (§2 and §7); "informative, not contract" is an
  existing idiom there. The client does not branch on the text (`MultiConnectionUDPClient.kt:314-315`).

## 4. How comparable projects do it

| System | Reply to a malformed opening message from an unverified UDP peer | Source |
|---|---|---|
| QUIC | Anti-amplification cap of 3x received bytes before validation | RFC 9000 §8.1 |
| DTLS 1.2 | Stateless `HelloVerifyRequest`, designed to be small | RFC 6347 §3.2.1, §4.2.1 |
| DTLS 1.3 | Cookie exchange by default; 3x SHOULD before verification | RFC 9147 §5.1 |
| CoAP | Avoid large factors for unauthenticated requests; slice large answers | RFC 7252 §11.3 |
| STUN (UDP) | Error 400 with a diagnostic phrase; text "can be anything appropriate" | RFC 8489 §14.8 |
| SIP (UDP) | 400 with a short phrase; client not required to read it | RFC 3261 §7.2, §21.4.1 |
| This repo | `REFUSED malformed` (17 bytes), registered origin silent, no state | this document |

The comparison is of reply shape and size, not a code survey; none of these is a Kotlin library, and none is a
text-only handshake with a 3-byte trigger.

## 5. Pitfalls and regrets

- **A reason that names the slot** is a per-slot oracle for a probing sender, grows the reply (`REFUSED name missing`
  is 21 bytes, `REFUSED credential empty` 24; 7.0x and 8.0x on the bare trigger), and makes three replies where one
  was required. IRC and SIP are the models for it; both run where the cost is lower. The slot goes to the server log.
- **A reason that sounds like policy** (`invalid`, `not allowed`) makes a protocol rejection look like an `admit()`
  decision. `invalid credential` is already a README example.
- **Treating the text as an API.** HTTP, SIP, STUN and SMTP all warn clients off depending on phrase text; the
  library client already exposes only `reason`. If a consumer greps for `malformed`, the text then becomes de facto
  contract. The doc must say it is not.
- **Reasoning from the on-wire ratio only.** 1.45x looks harmless; CISA's BAF is on payload bytes, so use the payload
  ratio and treat the on-wire figure as context.
- **Assuming a reply bounds the abuse.** One reply per datagram means a spoofer's byte gain is a constant factor, but
  the pps is theirs to set. The only real control is rate limiting at the edge or a future address-validation exchange;
  neither is part of this change.
- **Rebuilding the datagram per call.** `refusedMessage` compiles a `Regex` per call. Cheap on the admit path, wasteful
  at an attacker-chosen rate.

## 6. Recommendation for this repo

### 6.1 Wire text and constant

Send exactly `REFUSED malformed` (UTF-8, no trailing space or newline). In `main/HandshakeProtocol.kt`, beside
`REGISTERED_DATAGRAM`:

```kotlin
/** The informative reason a malformed `Iam` from an unregistered origin is refused with. */
private const val MALFORMED_REASON = "malformed"

/** The exact refusal datagram for a malformed `Iam`: `REFUSED malformed` as UTF-8 bytes. */
val MALFORMED_REFUSAL_DATAGRAM: ByteArray =
    HandshakeWireFormat.refusedMessage(MALFORMED_REASON).toByteArray(Charsets.UTF_8)
```

The coordinator then calls `send(HandshakeProtocol.MALFORMED_REFUSAL_DATAGRAM, origin)`, mirroring
`send(HandshakeProtocol.REGISTERED_DATAGRAM, origin)`. Reasoning:

- `HandshakeProtocol` is already the `internal`, socket-free owner of wire bytes the server sends
  (`REGISTERED_DATAGRAM`), so the bytes are assertable at level 4a without a socket
  (`String(MALFORMED_REFUSAL_DATAGRAM) == "REFUSED malformed"`, `isRefused` true, `refusalReason == "malformed"`).
  A `private const val` in `HandshakeCoordinator` would force that test through a socket-fake send.
- A precomputed `ByteArray` is warranted for the same reason as `REGISTERED_DATAGRAM` and more: the malformed path is the
  one a hostile sender controls the rate of. Like `REGISTERED_DATAGRAM`, the array is shared and must only be read; the
  object is `internal`, so no consumer can mutate it.
- The builder stays the single public `HandshakeWireFormat.refusedMessage`; no public constant is added.

### 6.2 Protocol-doc wording

**§3, replacing the "As-built quirk" paragraph** (the defect it describes is fixed; the whitespace gap that remains is
#53's):

> A line is **malformed** when the name slot is missing (a bare `Iam`) or empty (`Iam  x`), or when the credential slot
> is present but empty (`Iam bob  tok`); an absent credential (`Iam bob`) is not malformed. The server answers a
> malformed line from an origin that holds no registration with `REFUSED malformed`, the same text for all three
> shapes, and registers nothing, runs no `admit`, and fires no connect callback. A malformed line from a registered
> origin gets no reply and leaves its registration unchanged. The reason `malformed` is **informative, not contract**:
> a client treats any `REFUSED` as a refusal and must not branch on the text, and an application's `admit` may return
> the same text, so the text alone does not show which layer refused. Which slot was defective is recorded in the
> server log only.

**§3, new paragraph, Boundary-Ring note on side effects** (best placed after the `Refusal:` paragraph; §2's "Transport &
addressing" is the alternative home):

> **Replies to unverified origins.** The server answers a first datagram before it can know the sender's source address
> is genuine, and UDP does not authenticate source addresses, so a forged source makes the server send the reply to a
> third party. An unregistered origin can draw three replies: `REGISTERED 2` (12 bytes) to any well-formed
> `Iam <name>`, which also registers the origin; `REFUSED <reason>` when `admit` refuses, of a length the application
> chooses; and `REFUSED malformed` (17 bytes) to a malformed line, which registers nothing. Each trigger datagram draws
> at most one reply. In UDP payload bytes, the smallest well-formed trigger (`Iam a`, 5 bytes) draws 12, a ratio of 2.4;
> the smallest malformed trigger (a bare `Iam`, 3 bytes) draws 17, a ratio of 5.7. The server performs no
> address-validation exchange and no reply rate limiting; a deployment reachable from untrusted networks should
> rate-limit unregistered sources at the network edge.

**§8, table.** Replace the existing bare-`Iam` row and add the empty-slot rows:

| Condition | Server | Client |
|---|---|---|
| `Iam` with a missing or empty name slot, or a present-but-empty credential slot, from an unregistered origin | WARN (defect label only), nothing registered, no `admit`, reply `REFUSED malformed` (informative text) | `HandshakeRefusedException`, `reason == "malformed"` *(the client never receives `Iam`; this is the reply it sees)* |
| The same malformed `Iam` from a registered origin | WARN (defect label only), dropped, registration kept, no reply | *(not applicable)* |

The client column of the first row describes what a handshaking client sees, so it can stay if the table's "Client"
heading is read as "client behaviour"; if not, use "*(not applicable)*" and leave the exception to §3.

The WARN-with-stack-trace wording of the current row ("Failed to handle incoming datagram") goes away with the
sealed-outcome routing in `malformed-line-handling.md`.

### 6.3 Why not the obvious alternatives

- **Keep the working text `REFUSED malformed handshake`.** It repeats "handshake" inside the client's own "refused the
  handshake" message, and costs 10 more bytes on a trigger that is 3 bytes, giving the highest ratio the server has.
  The extra word adds no information a reader of `handshake()`'s exception does not already have.
- **Bare `REFUSED`.** See C1: it wins on amplification and loses on a hand-rolled client's first debugging step.

## 7. Rejected alternatives

| Alternative | Wire bytes | Ratio on bare `Iam` | Why rejected |
|---|---|---|---|
| `REFUSED malformed handshake` | 27 | 9.0 | Stutters with the exception prefix; highest ratio the server would send; no extra information. |
| `REFUSED name missing` / `name empty` / `credential empty` (per-slot) | 21 to 24 | 7.0 to 8.0 | Three replies where one is required; reveals the parser's slot logic to an unauthenticated prober; larger; IRC 431/461 and SIP's suggested detail suit connection-oriented or authenticated peers. The slot goes to the server log. |
| `REFUSED bad request` | 19 | 6.3 | Borrows HTTP/SIP/STUN vocabulary for a protocol with no "request/response" terms of its own; longer; the repo's terms are "handshake" and "malformed". |
| `REFUSED syntax error` | 20 | 6.7 | SMTP wording; longer; "error" suggests the server failed. |
| `REFUSED invalid handshake` | 24 | 8.0 | `invalid ...` is the family the README uses for the `admit` credential refusal; it would read as an application decision. |
| `REFUSED protocol` or a coded token such as `REFUSED 400` | 15 / 11 | 5.0 / 3.7 | Not self-explanatory; a number invites clients to branch on it, making an informative value de facto contract. |
| bare `REFUSED` | 7 | 2.33 | Held in C1. |

## Considerations

### C1. Send a reason at all, or a bare `REFUSED`

The settled behaviour is that the reply carries a reason; this records the alternative the evidence supports.

| Option | Bytes | Ratio on bare `Iam` / `Iam  x` | Client sees | For | Against |
|---|---|---|---|---|---|
| **`REFUSED malformed`** (recommended) | 17 | 5.67 / 2.83 | `reason == "malformed"` | Self-explanatory in the exception message and log; fits the repo's reason style; SMTP advises against bare codes; ratio sits within 10 percent of an existing README example (5.2x). | Exceeds 3x on the bare trigger (QUIC MUST, DTLS 1.3 SHOULD, neither binding here); raises the default worst case from 2.4x to 5.7x. |
| bare `REFUSED` | 7 | 2.33 / 1.17 | `reason == ""` | The only option under 3x; leaves the default worst case at about today's 2.4x; zero new text to maintain. | Indistinguishable from `Admission.Refused("")`; the hand-rolled-client author, who is exactly who sends a malformed line, sees "Server refused the handshake" with no cause and must reach for the server log; contradicts the settled reasoned reply. |

Recommendation: send `REFUSED malformed`. The reply's purpose is fast, legible failure for a client that sent a
malformed line, and a library client cannot send one once #52 lands, so the audience is third-party and hand-rolled
clients, who most need the word. The amplification cost is a 3-byte-trigger corner, one reply per datagram, no state, and a ratio
inside the range `admit()` refusals already produce. If a deployer later wants the strict 3x bound, the remedy is a rate
limit or an address-validation step, not a bare reply. This needs confirmation only if the 3x bound is to be treated as a
requirement for the module.

## Known gaps

- No source gives a numeric rule that binds a custom text protocol; the 3x figures are QUIC (MUST) and DTLS 1.3 (SHOULD),
  applied here by analogy. CoAP's SHOULD NOT has no number.
- The ratios are derived from the byte counts above, not measured; they assume the 28-byte IPv4+UDP header with no IP
  options and ignore link-layer framing (Ethernet minimum frame size would narrow the on-wire ratios further; not
  sourced here).
- "Raises the default worst case" assumes the default `admit` (`Admitted`) and that `Iam a` is the smallest well-formed
  trigger. A one-character name is allowed by the parse (`main/HandshakeProtocol.kt:55-59`); no length minimum was found.
- The doc draft says the server performs "no address-validation exchange and no reply rate limiting". A search of
  `HandshakeCoordinator.kt` and `MultiConnectionUDPServer.kt` for rate, limit, throttle and cookie found none, but this
  was a keyword search, not a full audit; confirm before the sentence is committed.
- Whether any existing consumer greps the old behaviour of a bare `Iam` (no reply) is outside this question.
- The §8 client-column wording for the new row is a drafting choice; the table's existing rows use "*(not applicable)*"
  where the client never sees the datagram.
- `HandshakeWireFormat.refusedMessage`'s Regex compile-per-call cost is inferred from `Regex(String)` construction in
  Kotlin; not benchmarked.

## 8. Load-bearing claims

1. **QUIC 3x limit before address validation.** "Prior to validating the client address, servers MUST NOT send more than
   three times as many bytes as the number of bytes they have received. This limits the magnitude of any amplification
   attack that can be mounted using spoofed source addresses." Source:
   [RFC 9000 §8.1](https://www.rfc-editor.org/rfc/rfc9000#section-8.1), May 2021.
2. **DTLS 1.3 3x SHOULD.** "A server SHOULD limit the amount of data it sends toward a client address to three times the
   amount of data sent by the client before it verifies that the client is able to receive data at that address."
   Source: [RFC 9147 §5.1](https://www.rfc-editor.org/rfc/rfc9147#section-5.1), April 2022.
3. **DTLS 1.2 names the amplifier risk and counters it with a cookie.** "An attacker can use the server as an amplifier by
   sending connection initiation messages with a forged source of the victim." and "This mechanism forces the
   attacker/client to be able to receive the cookie, which makes DoS attacks with spoofed IP addresses difficult."
   Source: [RFC 6347 §4.2.1](https://www.rfc-editor.org/rfc/rfc6347#section-4.2.1), January 2012.
4. **DTLS 1.2 designs its pre-validation reply to be small.** "The HelloVerifyRequest is designed to be small enough that
   it will not itself be fragmented, thus avoiding concerns about interleaving multiple HelloVerifyRequests." Source:
   [RFC 6347 §3.2.1](https://www.rfc-editor.org/rfc/rfc6347#section-3.2.1), January 2012.
5. **CoAP on unauthenticated amplification.** "An attacker need only place the IP address of the victim in the source
   address of a suitable request packet to generate a larger packet directed at the victim." and "Therefore, large
   amplification factors SHOULD NOT be provided in the response if the request is not authenticated." and "For example,
   for a 1000-byte resource, a 10-byte request might result in an 80-byte response (with a 64-byte block) instead of a
   1016-byte response, considerably reducing the amplification provided." Source:
   [RFC 7252 §11.3](https://www.rfc-editor.org/rfc/rfc7252#section-11.3), June 2014.
6. **CISA defines BAF on UDP payload bytes.** "The potential effect of an amplification attack can be measured by BAF,
   which can be calculated as the number of UDP payload bytes that an amplifier sends to answer a request, compared to
   the number of UDP payload bytes of the request." Source:
   [CISA TA14-017A](https://www.cisa.gov/news-events/alerts/2014/01/17/udp-based-amplification-attacks), initial release
   February 9, 2014, last revised December 18, 2019.
7. **CISA's factors.** Table rows "DNS | 28 to 54", "NTP | 556.9", "NetBIOS | 3.8", "BitTorrent | 3.8",
   "Multicast DNS (mDNS) | 2 to 10", "Memcached | 10,000 to 51,000"; and the overview: "By design, UDP is a
   connection-less protocol that does not validate source Internet Protocol (IP) addresses." Same source and dates.
8. **STUN 400 phrase and status of the text.** "400  Bad Request: The request was malformed." and "The reason phrase is
   meant for diagnostic purposes and can be anything appropriate for the error code." The IANA STUN Error Codes registry
   lists `400` as "Bad Request". Sources: [RFC 8489 §14.8](https://www.rfc-editor.org/rfc/rfc8489#section-14.8),
   February 2020; [IANA STUN parameters](https://www.iana.org/assignments/stun-parameters/stun-parameters.xhtml), read 2026-10-19.
9. **SIP 400 and the status of the phrase.** "The request could not be understood due to malformed syntax. The
   Reason-Phrase SHOULD identify the syntax problem in more detail, for example, 'Missing Call-ID header field'." and
   "The Status-Code is intended for use by automata, whereas the Reason-Phrase is intended for the human user. A client
   is not required to examine or display the Reason-Phrase." Sources:
   [RFC 3261 §21.4.1](https://www.rfc-editor.org/rfc/rfc3261#section-21.4.1) and
   [§7.2](https://www.rfc-editor.org/rfc/rfc3261#section-7.2), June 2002.
10. **HTTP reason phrase is not reliable.** "A client SHOULD ignore the reason-phrase content because it is not a
    reliable channel for information (it might be translated for a given locale, overwritten by intermediaries, or
    discarded when the message is forwarded via other versions of HTTP)." Source:
    [RFC 9112 §4](https://www.rfc-editor.org/rfc/rfc9112#section-4), June 2022.
11. **SMTP 501 and the status of reply text.** "501  Syntax error in command or arguments." and "An SMTP client MUST
    determine its actions only by the reply code, not by the text (except for the "change of address" 251 and 551 and,
    if necessary, 220, 221, and 421 replies); in the general case, any text, including no text at all (although senders
    SHOULD NOT send bare codes), MUST be acceptable." Source:
    [RFC 5321 §4.2.3 and §4.2](https://www.rfc-editor.org/rfc/rfc5321#section-4.2.3), October 2008.
12. **IRC numerics for a missing parameter.** `431    ERR_NONICKNAMEGIVEN  ":No nickname given"` and
    `461    ERR_NEEDMOREPARAMS  "<command> :Not enough parameters"`, the latter "Returned by the server by numerous
    commands to indicate to the client that it didn't supply enough parameters." Sources:
    [RFC 2812 §5.2](https://www.rfc-editor.org/rfc/rfc2812#section-5.2), April 2000 (same numerics in
    [RFC 1459 §6.1](https://www.rfc-editor.org/rfc/rfc1459#section-6.1), May 1993).
