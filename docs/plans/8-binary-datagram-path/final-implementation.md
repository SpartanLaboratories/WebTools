# Issue #8 — final implementation (as built)

**Covers:** `SpartanLaboratories/WebTools#8` — *"Expose a binary datagram path on
Connection / MultiConnectionUDPClient"*.

## What was built

One pull request into `master`, [#17](https://github.com/SpartanLaboratories/WebTools/pull/17), merged on 2026-09-06 as merge commit
`cbef98d`. It carries two commits: `5fe3f94` (the implementation, its tests and
[`plan.md`](plan.md)) and `175e92f` (`webtools-udp` bumped to `1.1.0`).

The section below is the as-built record moved here verbatim from [`plan.md`](plan.md).
Its heading names the source document and section; the source keeps a pointer where
the record was.

## plan.md §11 QA-remediation additions (post-plan)

Two QA passes were run on the implementation. The first surfaced 8
documentation findings (send-side payload-size contract missing on the byte
methods; `Connection.push(String)` KDoc stated an inverted contract;
non-interpolating `$VERB`/`$HANDSHAKE_VERB` in KDoc; inbound-truncation sentence
absent at the `*Bytes` call sites; four minors) — all fixed. A `.gitattributes`
(`*.kt`/`*.kts text eol=lf`) was added and two test files re-saved LF after an
editor had rewritten them CRLF.

The second pass returned 4 low-severity test-coverage-placement findings. The
plan had deferred two of them (§4's "leave to 4b" for the server socket path;
the manual UAT scenario, formerly listed here). **At the user's explicit
direction during QA, all four were closed**, superseding those deferrals:

- **L1 gating** — `HandshakeCoordinatorGatingTest` +2: a bound bytes handler
  gets the exact undecoded bytes; a payload trimming to `KA` is still dropped
  when a bytes handler is bound.
- **L2 component** — `RegistrationsTest` +1: `Registration.onBytes` defaults
  `null` and is settable independently of `onMessage` at that layer.
- **L3 integration** — `MultiConnectionUDPServerTest` +2 (`@Order(14)`/`(15)`,
  after every text case, before `stop`): a re-actuated bytes handler receives a
  client datagram byte-identical with no trim/decode; `pushToAll(ByteArray)` puts
  the payload on a real client socket verbatim. Isolated from the `PER_CLASS`
  ordered suite by using fresh per-test sockets and re-binding only the one
  connection under test — the shared server fixture is untouched.
- **L5 UAT** — `MultiConnectionUDPServerUatTest` +1 `@Disabled`: a NAT'd client
  round-trips a raw binary blob through an `actuateBytes` echo handler.
