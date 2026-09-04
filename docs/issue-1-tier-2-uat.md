# Issue #1 Tier 2 — Level 5 UAT procedure

**Covers:** SpartanLaboratories/WebTools#1 (Tier 2) — all client traffic is now
multiplexed over the single common socket (`9998`); the data path traverses NAT.
**Executable scaffold:** `src/test/kotlin/com/spartanlabs/testing/uat/webtools/MultiConnectionUDPServerUatTest.kt`
(`@Tag("uat")`, `@Disabled` — these steps are manual).

Tier 2 cannot be acceptance-tested on one machine: loopback never exercises a NAT.
The automated suite (Levels 3 and 4b) proves the mechanism — inbound demux by
source key, server→client datagrams addressed to the observed post-NAT source,
one bound port for the whole session. This procedure proves the outcome a user
cares about: *a real client behind a home router exchanges data both ways with a
cloud server after only sending `Iam`.*

## Prerequisites

- **Host S (server):** a machine with a public, routable IPv4 address and inbound
  UDP `9998` open (a cloud VM is fine). Running a `MultiConnectionUDPServer`
  subclass whose `onClientConnect` actuates the connection with an echo handler
  and calls `connection.keepAlive()` on a ~20 s schedule.
- **Host C (client):** a machine on a home / office network behind NAT (local
  address in `10/8`, `172.16/12`, or `192.168/16`). Not on the same LAN as S, not
  on a VPN back to S. C sends its own `KA` datagram every ~20 s of idle time from
  its handshake socket.

## Section 1 — Handshake completes from behind NAT  *(regression of Tier 1, must pass)*

1. On S, start the server; confirm it logs listening on `9998`.
2. On C, from a single UDP socket, send `Iam uatclient` to `<public-ip-of-S>:9998`
   and block on `receive()` on that same socket (2-second timeout).
3. Record what C receives and what S logs.

| Result | Verdict |
|--------|---------|
| C receives the single token `REGISTERED` within 2 s, and S logs a new connection for C's **public** (post-NAT) address | **PASS** |
| C times out | **FAIL** |
| S logs a connection for a `192.168.*` / `10.*` address | **FAIL** — server trusting the payload |

## Section 2 — Bidirectional data over the single port  *(must pass — the Tier 2 deliverable)*

1. After Section 1, from the **same** socket that sent `Iam`, C sends
   `hello-from-C` to `<public-ip-of-S>:9998`.
2. S's handler logs it and `push`es `hello-from-S` back.
3. C blocks on `receive()` on that same socket (2-second timeout).

| Result | Verdict |
|--------|---------|
| S logs `hello-from-C` from C's post-NAT address **and** C receives `hello-from-S` within 2 s | **PASS** |
| S logs `hello-from-C` but C never receives `hello-from-S` | **PARTIAL** — client→server only; server→client dropped by NAT |
| S never logs `hello-from-C` | **PARTIAL** — server→client only |
| neither direction works | **FAIL** |

## Section 3 — Broadcast

1. With C still registered, call `server.pushToAll("broadcast-1")` on S.
2. PASS: C receives `broadcast-1` on its socket within 2 s.

## Section 4 — Mapping longevity

Run for 5 minutes. Record each sub-case:

- **(a)** C sends `KA` every 20 s, S otherwise idle — send a data message at the
  4-minute mark. PASS: it still flows both ways.
- **(b)** S calls `Connection.keepAlive()` only, C fully idle — record whether C's
  router keeps the mapping (expected: yes for cone NAT, no for symmetric /
  port-restricted).
- **(c)** deliberate 60 s of total silence both ways, then a data message —
  record whether the mapping survived.

## Section 5 — Two clients behind one NAT

1. From the same Host C, open two UDP sockets (distinct source ports); each sends
   `Iam c1` / `Iam c2`.
2. PASS: both register with distinct post-NAT source ports and exchange data with
   no cross-talk (c1 never sees c2's messages).

## Sign-off

| Field | Value |
|-------|-------|
| Date / evaluator | |
| Host S address / OS | |
| Host C ISP / router | |
| Section 1 verdict | |
| Section 2 verdict | |
| Section 3 verdict | |
| Section 4 (a) / (b) / (c) | |
| Section 5 verdict | |
| Observed NAT type / mapping timeout | |
| Release-readiness call for the bare `2.0.0` tag | |
