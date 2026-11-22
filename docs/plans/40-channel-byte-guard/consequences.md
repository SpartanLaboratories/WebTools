# Issue #40 — consequences

- **Covers:** `SpartanLaboratories/WebTools#40`, *"webtools-udp 2.0: receivers must drop non-zero channel-byte frames
  before the Stable Core freeze"*.
- **What this file holds:** this folder predates the convention that gives an issue a `consequences.md`, and has no
  `impact.md`. The file records what later fixes to the code this issue built made no longer current in its documents,
  and what they closed. It does not restate [`architecture.md`](architecture.md) or [`plan.md`](plan.md), and the landed
  documents are not edited. The follow-ups and known gaps those fixes leave are in the `consequences.md` of the issue
  that built the code they correct: for #39, that of
  [Issue #14](../14-reliable-ordered-channel/consequences.md#1-issue-39-origin-screening-on-every-client-receive-branch).

## 1. Issue #39: origin screening on every client receive branch

The fix is [#39](https://github.com/SpartanLaboratories/WebTools/issues/39), `webtools-udp` `2.0.1a`. Its plan is
[`plan.md`](../39-client-origin-screening/plan.md) and its as-built record
[`final-implementation.md`](../39-client-origin-screening/final-implementation.md). The client's listener now checks
every datagram's source against `serverEndpoint` once, before it reads the tag, the `0x90` branch included.

### Closed

- **#39 was out of scope here.** [`architecture.md`](architecture.md) section 1.1 (point 4) and [`plan.md`](plan.md)
  section 1.2 (criterion 4) left the client `0x90` branch's missing origin screening to #39, so the channel check
  applied to every `0x90` on that path. #39 closes it: the origin screen precedes the channel check on the `0x90` branch
  as it does on the reliable one.

### No longer current

These statements stay as written, as the record of what was built then. They describe the client's `0x90` branch as
having no origin screening, a stranger as able to reach its channel check, and its WARN as naming an unscreened sender.

- [`architecture.md`](architecture.md): section 1.1 (point 4); section 1.2 (D9: the WARN names the datagram's actual
  origin because the client's `0x90` path is unscreened); section 2 (the fourth finding: the client `0x90` path is the
  exception, where the new WARN joins stranger-reachable ones); section 3.2 (the client `0x90` diagram, "no origin
  screening"); section 10 (the WARN risk, "On the client `0x90` path, which has no origin screening"); and the "Shared
  risks that remain" of its cross-plan alignment.
- [`plan.md`](plan.md): section 1.2 (criterion 4); section 2 (the client diagram); section 3.4 (the comment it specifies,
  "this branch has no origin screen (#39)", and the note that the logged origin is the datagram's actual, unscreened
  sender); section 5 (the test table and section 5.2, where the test `a channel-0x01 0x90 from a foreign socket is
  dropped with a WARN naming the actual sender - pins the no-origin-screen behaviour (issue 39)` says "#39's own fix will
  change what this test asserts"; #39 rewrote it to assert the DEBUG origin drop and the absence of the channel WARN);
  section 6 (the WARN risk); and section 10 (the statement that #39 is out of scope and filed separately).

On the client, the channel WARN, like the malformed-frame and unframed WARNs, is now logged only for a datagram from
`serverEndpoint`; the receive-buffer truncation WARN is the one WARN still logged ahead of the screen. The module-wide
follow-up candidate of [`architecture.md`](architecture.md) section 11.1, bounding per-datagram drop logging, is not
closed: #39 changes no server code.
