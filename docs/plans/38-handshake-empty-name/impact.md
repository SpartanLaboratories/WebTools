# Impact: malformed `Iam` refusal

- **Covers:** `SpartanLaboratories/WebTools#38`, *"Handshake parsing accepts an empty peer
  name on extra whitespace"*.
- **Design and plan:** [`architecture.md`](architecture.md) and [`plan.md`](plan.md). The
  evidence is in [`research/`](research/README.md).
- **Scope of this document:** what changes for whom, what in this repo moves with the fix,
  and what is left for later.

## 1. Wire behaviour, before and after

All lines arrive as unframed UTF-8 text. The server trims the text and splits it on a
single U+0020.

| Line | From | Before (`2.0.0` and earlier) | After (`2.0.0a`) |
|---|---|---|---|
| bare `Iam` | an origin with no registration | no reply; listener WARN with a stack trace | `REFUSED malformed`; one WARN (origin, defect, registered flag); nothing registered |
| bare `Iam` | a registered origin | no reply; listener WARN with a stack trace | `REFUSED malformed`; the registration is kept |
| `Iam  x` (empty name slot) | an origin with no registration | `admit("", …)`, registers the name `""`, `REGISTERED 2`, `onClientConnect` | `REFUSED malformed`; no `admit`, nothing registered |
| `Iam  x` | a registered origin | `REGISTERED 2` (retransmit) | `REFUSED malformed`; the registration is kept |
| `Iam bob  tok` (empty credential slot) | an origin with no registration | `admit("bob", …, "")`; `tok` is ignored as an extra token | `REFUSED malformed`; no `admit`, nothing registered |
| `Iam bob  tok` | a registered origin | `REGISTERED 2` (retransmit) | `REFUSED malformed`; the registration is kept |
| `Iam bob`, `Iam bob tok`, `Iam bob tok  x` | any origin | accepted (credential `""`, `tok`, `tok`) | unchanged |
| `Iam<TAB>bob` and any other line whose first token is not exactly `Iam` | any origin | WARN "sender may be pre-2.0", no reply | unchanged |
| `Iam bob<TAB>tok` (non-space whitespace inside a token) | any origin | registers the name `bob<TAB>tok` | unchanged: a known gap, #53 |

The reply `REFUSED malformed` is 17 bytes. Its reason is informative wire text, not a
contract.

## 2. Who sees the change

- **Server applications** (subclasses of `MultiConnectionUDPServer`):
  - `admit` never sees an empty `name`.
  - `admit` sees `credential` as `""` only when the client sent none.
  - `onClientConnect` never fires for an empty name.
  - No signature changes, and nothing to adopt.
- **Library clients** (`MultiConnectionUDPClient`):
  - **Before:** `handshake("")` timed out, and `handshake("", credential = "tok")`
    registered under the empty name.
  - **After:** both fail fast with `HandshakeRefusedException` and
    `reason == "malformed"`. So does a name or credential that begins with a space. Once
    #52 lands, these calls fail locally instead.
  - **Mid-session:** a client whose address draws a forged `REFUSED malformed` drops that
    unframed datagram with a WARN.
- **Third-party and hand-rolled clients:**
  - A malformed line is now refused rather than accepted or ignored.
  - A `REFUSED` can arrive mid-session if a malformed `Iam` is sent from, or forged as,
    their address. The server keeps them registered, so they should not take a stray
    `REFUSED` on an established session as a disconnect. The protocol doc says so.
- **Operators:**
  - **Logs:** one WARN per malformed line. It carries the origin, a fixed defect label and
    the registered flag, and never the line or its tokens. The stack-trace WARN that a bare
    `Iam` drew before is gone.
  - **Reflection:** the protocol doc's new note on replies to unverified origins gives the
    reply sizes and ratios. A bare `Iam` draws a payload ratio of 5.7 (1.45 on the wire).
    The server has no address validation and no reply rate limiting
    ([#57](https://github.com/SpartanLaboratories/WebTools/issues/57)), so a deployment
    exposed to untrusted networks should rate-limit unregistered sources at its edge.

## 3. Blast radius in this repo

| Area | What moves |
|---|---|
| Production code | `HandshakeProtocol.kt`: the sealed parse outcome, the defect enum, the reply bytes. `HandshakeCoordinator.kt`: one split, one parse, the routing. |
| Public KDoc | `MultiConnectionUDPServer.kt` (class, `admit`) and `HandshakeWireFormat.kt` (credential slot, the `REFUSED` reply, `REFUSED_REPLY`). Documentation only; no signature changes. |
| Tests | Two tests flip from silence to `REFUSED malformed`. New cases at levels 1, 2, 3, 4a, 4b and 4c. `HandshakeProtocolTest` and `HandshakeProtocolGatingTest` follow the sealed outcome. The 4b case pins the library client's empty-name refusal until #52 rejects it locally. |
| Test hygiene | The raw NUL and SOH bytes in `HandshakeCoordinatorTest.kt:577` become `"\u0000\u0001"`, so ripgrep-based search reaches the file again. The comment at `ChannelByteGuardIntegrationTest.kt:25` points at the raw-socket peer's current lines. |
| Protocol doc | §3 (the rule, the registered-origin trade-off, the #53 gap, replies to unverified origins) and §8 (the malformed rows). Every out-of-date source line reference is refreshed, 21 of 37 on `85cfbbe`. |
| Architecture doc | The line reference at `:52` and the handshake node in the inbound-dispatch flowchart. |
| README | The message table, the retransmit and credential paragraph, the `admit` paragraph, and "Handshake screening & credentials". |
| Build | Unchanged by the plan. The release manager picks the bugfix version bump at landing. |

## 4. Adoption verdicts

Every site that recognises or tokenises an `Iam` line:

| Site | Verdict |
|---|---|
| `HandshakeCoordinator.kt:227` (the split, twice) | In scope: one split, one parse, routing on the outcome. |
| `HandshakeProtocol.parseHandshake` | In scope: a total sealed outcome, with the credential read by `getOrNull`. |
| `HandshakeProtocol.isHandshake`, `extraTokenCount` | Unchanged. `isHandshake` is also the definition of the not-a-handshake outcome. |
| `HandshakeWireFormat.handshakeMessage`, `MultiConnectionUDPClient.handshake` | Follow-up #52 (client-side validation). |
| `HandshakeWireFormat.registeredProtocolVersion` | Unchanged: it parses the reply, not the `Iam` line. |
| Log lines that print the name (`HandshakeCoordinator.kt:261,265,278,290`) | Follow-up #53 (a name can still carry CR/LF). The new malformed WARN prints no token. |
| Test responders (`MultiConnectionUDPClientProbeTest.kt:64`, `MultiConnectionUDPProtocolVersionTest.kt:35-45`) | Unchanged: they are stand-in servers for client tests. |

## 5. Downstream consumers

Consumers of `io.github.spartanlaboratories:webtools-udp` get no API change, and a consumer
whose clients send well-formed handshakes sees no difference on the wire. The fix ships as
the bugfix release `2.0.0a`.

## 6. Follow-ups

| Item | Relation to this change |
|---|---|
| [#52](https://github.com/SpartanLaboratories/WebTools/issues/52): client-side handshake validation | Makes the library client unable to send a malformed line; why the socket-level tests here use a raw socket. |
| [#53](https://github.com/SpartanLaboratories/WebTools/issues/53): non-space whitespace in names and credentials | Adds `HandshakeDefect` entries and checks inside `parseHandshake`, and replaces the protocol doc's known-gap paragraph. It inherits this change's rule that every malformed line is refused whatever its origin, as [a comment on the issue](https://github.com/SpartanLaboratories/WebTools/issues/53#issuecomment-6297556949) records. |
| [#54](https://github.com/SpartanLaboratories/WebTools/issues/54): a throwing `onClientConnect` ends the listener thread | Orthogonal. This change adds no throw path; #54's guard lands in `receiveLoop` and around `onRegistered`. |
| [#41](https://github.com/SpartanLaboratories/WebTools/issues/41): rate-bounding the per-datagram WARN logs | Covers the new malformed WARN on the server, and the client's unframed-drop WARN for a reflected `REFUSED`. The fixed message template and defect label are its key. |
| [#49](https://github.com/SpartanLaboratories/WebTools/issues/49): registration orphaned when the `REGISTERED` send fails | Its fix lands inside the moved, unchanged admit-and-register path. |
| [#56](https://github.com/SpartanLaboratories/WebTools/issues/56): a registered origin's `Iam` gets `REGISTERED 2` even when it names a different client | Pre-existing and unchanged. The retransmit lookup is by origin, so `Iam mallory` from `alice`'s registered origin gets `REGISTERED 2` and the name change is ignored. The protocol doc states this and links #56. |
| [#57](https://github.com/SpartanLaboratories/WebTools/issues/57): replies to unverified source addresses, with no rate limit | Unchanged by this fix, which adds one more reply (`REFUSED malformed`, 17 bytes) to the set an unverified source can draw. The protocol doc's note on replies to unverified origins states the gap, advises edge rate limiting, and links #57. |
