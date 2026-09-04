# Issue #1 Tier 1 — Level 5 UAT procedure

**Covers:** SpartanLaboratories/WebTools#1 (Tier 1) — the handshake reply now
follows the datagram source instead of the client-claimed address.
**Executable scaffold:** `src/test/kotlin/com/spartanlabs/testing/uat/webtools/MultiConnectionUDPServerUatTest.kt`
(`@Tag("uat")`, `@Disabled` — these steps are manual).

Tier 1 cannot be acceptance-tested on one machine: loopback never exercises a NAT.
The automated suite proves the mechanism (reply returns to the datagram's source
port, payload address ignored); this procedure proves the outcome a user cares
about — *a real client behind a home router can connect to a cloud server*.

## Prerequisites

- **Host S (server):** a machine with a public, routable IPv4 address and inbound
  UDP `9998` open (a cloud VM is fine). Running a `MultiConnectionUDPServer`
  subclass whose `onClientConnect` logs the connection.
- **Host C (client):** a machine on a home / office network behind NAT, so its
  local address is in `10/8`, `172.16/12`, or `192.168/16`. Not on the same LAN
  as S, not on a VPN back to S.

## Section 1 — Handshake completes from behind NAT  *(must pass)*

1. On S, start the server. Confirm the log shows it listening on `9998`.
2. On C, from a single UDP socket, send the bytes `Iam uatclient` to
   `<public-ip-of-S>:9998`, then block on `receive()` on that same socket with a
   2-second timeout.
3. Record: what C receives, and what S logs.

| | Result | Verdict |
|---|--------|---------|
| C receives `TXRXON <sendPort> <receivePort>` within 2 s, and S logs a new connection for C's **public** (post-NAT) address | ✅ | **PASS** |
| C times out with no datagram | ❌ | **FAIL** — this is the pre-Tier-1 behaviour; the fix did not take |
| S logs a connection for a `192.168.*` / `10.*` address | ❌ | **FAIL** — the server is still trusting the payload |

4. Repeat step 2 twice more on the same socket without restarting. Each retransmit
   must return the **same** `TXRXON` ports and S must **not** log additional
   connections (retransmit de-duplication).

## Section 2 — Dedicated data channel does NOT traverse NAT yet  *(expected to fail — documents the limitation)*

1. After a successful Section 1 handshake, have C actuate its dedicated
   `sendPort` / `receivePort` and send a data message to S on them; have S push a
   message back.
2. Expected: **FAIL** — C's NAT has no binding for the dedicated ports and S
   speaks first. This is the known Tier 1 limitation
   ([`issue-1-nat-traversal-plan.md`](./issue-1-nat-traversal-plan.md) Tier 2).
3. Record the observed behaviour so Tier 2 has a concrete before/after.

## Sign-off

| Field | Value |
|-------|-------|
| Date / evaluator | |
| Host S address / OS | |
| Host C network (ISP / router) | |
| Section 1 verdict | |
| Section 1 retransmit verdict | |
| Section 2 observed behaviour | |
| Release-readiness call | |
