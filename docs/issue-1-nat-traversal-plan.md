# Resolution plan — Issue #1: Handshake replies break NAT'd clients

**Issue:** SpartanLaboratories/WebTools#1 —
*"Handshake replies to the client-claimed address, not the datagram source: breaks any NAT'd client"*

**Status:** planning only, no code written yet.
**Target release:** the in-progress 2.0.0 major break. Tier 1 ships as `2.0.0b`
(`build.gradle.kts` coordinates `io.github.spartanlaboratories:WebTools:2.0.0b`), so the
wire-protocol change needs no extra major bump beyond what 2.0.0 already implies.

---

## 1. Root cause

All references are to `src/main/kotlin/com/spartanlabs/webtools/`.

| # | Location | Problem |
|---|----------|---------|
| 1 | `MultiConnectionUDPServer.handshakeLoop()` (~line 65) | Receives the datagram but forwards only `String.split(' ')`; `packet.address` and `packet.port` are discarded. |
| 2 | `MultiConnectionUDPServer.handleHandshake()` line ~103 | Client address is taken from the **payload**: `InetAddress.getByName(message[HANDSHAKE_ADDRESS_INDEX]…)`. A NAT'd client can only self-report its private address (`General.resolveLocalAddress()` probes the local routing table), e.g. `192.168.1.x`, which is not routable from the server. |
| 3 | `MultiConnectionUDPServer` line ~38 + `pushToAddress()` line ~165 | The `TXRXON` reply is sent from a **separate** socket (`commonSendSocket`) to `address:COMMON_SEND_PORT` (9999). Even with a correct address this is a brand-new 5-tuple with no NAT mapping — inbound-blocked. |
| 4 | `addConnection()` line ~126 + `UDPConnection` / `UDPSendReceiveServer` | Each connection gets its own dedicated local port pair and its own `DatagramSocket`s. Every pair needs its own NAT hole on the client side. |
| 5 | `UDPConnection` construction + `MultiConnectionUDPServer` reply order | Server and the dedicated `UDPSendReceiveServer` transmit **first**, before the client has sent anything from the dedicated ports, so no client-side NAT mapping exists yet. |

Items 1–3 break the **handshake** for NAT'd clients (the filed issue).
Items 4–5 additionally break the **data path** even after the handshake is fixed.

The `ab53747` "step ports down by 2" fix is a patch on top of design #4 and becomes moot if #4 is removed.

---

## 2. Tier 1 — correctness fix — DONE

Implemented on branch `fix/issue-1-handshake-datagram-source`. See
[`issue-1-tier-1-implementation.md`](./issue-1-tier-1-implementation.md) for the detail.
Summary of what shipped: the server learns the client endpoint from the datagram source,
replies on the receiving socket, the protocol is now `Iam <name>` → bare `TXRXON`,
`commonSendSocket` / `COMMON_SEND_PORT` are gone, `pushToAll` targets each client's learned
origin, and a retransmitted `Iam` reuses its ports instead of allocating a new pair.

Goal (met): the handshake completes for any client that can send a UDP datagram to the
server, NAT'd or not. No reliance on client self-reported address.

1. **Thread the packet through.** Change `handshakeLoop()` to pass the `DatagramPacket`
   (or an extracted `InetSocketAddress` + token list) into `handleHandshake()`.
2. **Use the datagram source.** In `handleHandshake()`, derive the client endpoint from
   `packet.address` + `packet.port`. Ignore any address token in the payload.
3. **Reply on the receiving socket.** Send `TXRXON` back through `commonListenSocket`
   to `packet.address:packet.port`. This reuses the exact NAT mapping the client's `Iam`
   just created. Delete `commonSendSocket` and `COMMON_SEND_PORT`.
4. **Protocol clean break.** Handshake becomes `Iam <name>` (drop the `<address>` token).
   Remove `HANDSHAKE_ADDRESS_INDEX`. Document the new protocol in the class KDoc
   (Component Ring) and in a new `README.md` protocol section (Boundary Ring).
5. **Tests.** Update `MultiConnectionUDPServerTest`:
   - handshake sends `Iam testclient` (no address), asserts the `TXRXON` reply arrives on
     the *same socket the client sent from* (not a fixed 9999 listener).
   - assert `connectedClients[0].address` equals the loopback source, learned from the packet.

### Tier 1 limitation

After Tier 1 the dedicated `UDPConnection` still opens fresh sockets and speaks first
(problems #4/#5), so real bidirectional data across NAT still fails. Tier 1 alone is only
worthwhile as an incremental commit toward Tier 2.

---

## 3. Tier 2 — real NAT traversal (choose one)

**Decision: Option B1 (single-socket multiplex) was chosen and implemented.** See
[`issue-1-tier-2-plan.md`](./issue-1-tier-2-plan.md) for the full design and
file-by-file breakdown. The handshake reply landed as the single token
`REGISTERED` (not the `OK`/token placeholder sketched below). Shipped as `2.0.0c`.

### Option B1 — single-socket multiplex  *(chosen, implemented)*

- The server owns **one** `DatagramSocket` (the common port). All clients share it.
- Connections are keyed by source `(InetAddress, port)` in a map.
- `UDPConnection` no longer owns a `UDPSendReceiveServer`; it holds a back-reference to the
  server plus its peer `InetSocketAddress`, and `push()` delegates to the shared socket's
  `send()`. Inbound datagrams are dispatched by source key to the matching connection's
  `onMessage`.
- Handshake: client sends `Iam <name>` from a socket it will keep; server replies `OK` (or
  a token) on the same socket; that one mapping now carries all traffic both ways.
- `COMMON_SEND_PORT`, per-connection ports, `addConnection` port math, and the
  `ab53747` fix all disappear.
- `UDPSendReceiveServer` keeps its current shape for standalone use but is no longer used
  by `MultiConnectionUDPServer`.

**Cost:** largest refactor. `UDPConnection` public shape changes (`sendPort` / `receivePort`
properties go away). Dispatch-by-source-key is new code needing its own tests.
**Benefit:** eliminates problems #3, #4, #5 outright; one NAT mapping per client; server
never speaks before the client.

### Option B2 — port learning

- Keep the dedicated-port-pair model.
- After `TXRXON`, the client must send a priming datagram from **each** dedicated port to
  the server's dedicated receive port before any server→client traffic.
- Server records the observed `(address, port)` of that first packet and uses it as the
  send target for that connection. Server never transmits until the priming packet arrives.
- `UDPSendReceiveServer` gains a "learn target from first inbound packet" mode; `send()`
  before learning returns `Result.failure`.

**Cost:** still multiple NAT mappings per client; client-side changes are more intricate
(must prime every port, handle keepalives so mappings don't expire).
**Benefit:** smaller diff to `MultiConnectionUDPServer`'s structure than B1.

---

## 4. Cross-repo impact

The wire protocol changes in **both** tiers. Client code lives outside this repo
(MyGameTools / MyGameServer perform the `Iam` handshake). Any merge here must land in
lockstep with a matching client change, or bump/gate by version. List the concrete client
call sites before implementation.

---

## 5. Suggested sequencing

1. ~~Land Tier 1 as one commit (small, self-contained, testable on loopback).~~ **Done**
   (`fix/issue-1-handshake-datagram-source`), `README.md` created in the same change.
   Published to Maven Central as `2.0.0b`.
2. Coordinate the client-repo change (`Iam <name>` + parse bare `TXRXON` from the send
   socket) in `MyGameTools` / `MyGameServer`.
3. ~~Decide Tier 2 B1 vs B2.~~ **Done** — B1 (see `issue-1-tier-2-plan.md`).
4. ~~Land Tier 2 refactor + tests.~~ **Done** — `2.0.0c`, single-socket multiplex,
   reply token `REGISTERED`, `KA` keepalive, `e2e` test level added.
5. Tag 2.0.0 — **pending** downstream (`MyGameTools` / `MyGameServer`) integration
   on `2.0.0c` and cross-NAT UAT (`issue-1-tier-2-uat.md`) sign-off.
