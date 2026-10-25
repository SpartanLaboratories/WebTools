/* plan.js: plan.json as a script, written by plan/scripts/embed.js beside the page for its live view (plan/RULES.md §1). Do not edit: edit plan.json and re-embed. */
window.dashboardLive&&window.dashboardLive("plan",
{
  "schema": "plan-board/1",
  "repo": "SpartanLaboratories/WebTools",
  "issue": 38,
  "slug": "38-handshake-empty-name",
  "folder": "docs/plans/38-handshake-empty-name",
  "kind": "issue",
  "level": null,
  "title": "Malformed Iam refusal",
  "issueTitle": "Handshake parsing accepts an empty peer name on extra whitespace",
  "function": "The `webtools-udp` server trims an unframed handshake line and splits it on a single space, so `Iam  x` registered a peer named `\"\"` and a bare `Iam` died in a stack-trace WARN. This plan makes the server refuse every malformed `Iam` line with `REFUSED malformed`, from any origin, before `admit` and without touching a registration, and moves the KDoc, tests and living documents that said otherwise. A contained bugfix in one module, shipped as `webtools-udp` `2.0.0a`.",
  "status": "built",
  "parent": null,
  "branch": "fix/issue-38-handshake-empty-name",
  "baseline": {
    "ref": "master",
    "sha": "85cfbbe",
    "date": "2026-10-19"
  },
  "asBuilt": {
    "ref": "master",
    "sha": "ca57a57",
    "date": "2026-10-20",
    "pr": 59,
    "merge": "806ce8e",
    "version": "webtools-udp 2.0.0a"
  },
  "systems": [
    {
      "slug": "webtools-udp",
      "name": "webtools-udp",
      "slot": 1,
      "top": "webtools-udp",
      "status": "built",
      "page": null
    }
  ],
  "wip": {
    "step": "landed",
    "since": "2026-10-20",
    "updated": "2026-10-20",
    "detail": "Merged through PR #59 as 806ce8e; the board was written after the fact as the worked example.",
    "steps": [
      "research",
      "architecture",
      "plan",
      "branch",
      "implementation",
      "qa",
      "remediation",
      "landing",
      "landed"
    ],
    "current": [],
    "qa": [
      {
        "pass": 1,
        "verdict": "changes-requested",
        "findings": {
          "documentation": 10,
          "test": 8
        },
        "note": "Stage C added to the plan"
      },
      {
        "pass": 2,
        "verdict": "changes-requested",
        "findings": {
          "documentation": 4,
          "test": 1
        },
        "note": "5 LOW items; Stage D added; no third review run, by the maintainer's decision"
      }
    ],
    "qaMax": null,
    "waitingOn": null,
    "resume": null,
    "blockers": {
      "waitsOn": [],
      "blocks": []
    },
    "landing": {
      "pr": 59,
      "merge": "806ce8e",
      "version": "webtools-udp 2.0.0a",
      "date": "2026-10-20"
    }
  },
  "milestones": [
    {
      "column": "gap",
      "label": "Research",
      "date": "2026-10-19",
      "ref": "research/README.md",
      "note": "three questions, three findings files, verification and a summary in seven commits, 77b3786 to 9b1fff7"
    },
    {
      "column": "considered",
      "label": "Architecture",
      "date": "2026-10-20",
      "ref": "architecture.md",
      "note": "§9 alternatives, §10 decomposition into one unit in two stages"
    },
    {
      "column": "verdict",
      "label": "Plan accepted",
      "date": "2026-10-20",
      "ref": "56a2121",
      "note": "Commit 1 carried plan.md, architecture.md and impact.md with the test housekeeping"
    },
    {
      "column": "built",
      "label": "Merged",
      "date": "2026-10-20",
      "ref": "PR #59",
      "note": "Stages A to D in five commits, then the 2.0.0a bump ca57a57; merged as 806ce8e"
    }
  ],
  "trail": [
    {
      "date": "2026-09-24",
      "column": "gap",
      "label": "Issue #38 opened: `Iam  x` registers a peer named `\"\"`; proposes rejecting empty tokens or splitting on `\\s+`",
      "ref": "#38"
    },
    {
      "date": "2026-10-19",
      "column": "gap",
      "label": "Baseline: master at the merge of #51",
      "ref": "85cfbbe"
    },
    {
      "date": "2026-10-19",
      "column": "gap",
      "label": "Research: blast radius, malformed-line handling, refusal reply text; verification; summary aligned with the settled design",
      "ref": "9b1fff7"
    },
    {
      "date": "2026-10-20",
      "column": "verdict",
      "label": "Commit 1: A-1 and A-2 test housekeeping, with the plan documents",
      "ref": "56a2121"
    },
    {
      "date": "2026-10-20",
      "column": "built",
      "label": "Commit 2: Stage A code, KDoc and tests",
      "ref": "1605bdd"
    },
    {
      "date": "2026-10-20",
      "column": "built",
      "label": "First review: changes requested, 10 documentation and 8 test findings; Stage C added to the plan",
      "ref": ""
    },
    {
      "date": "2026-10-20",
      "column": "built",
      "label": "Second review: 5 LOW items, 4 documentation and 1 test; Stage D added",
      "ref": ""
    },
    {
      "date": "2026-10-20",
      "column": "built",
      "label": "Commits 3 to 5: contracts and regions, the remaining tests, then Stage B's documents with every reference recomputed",
      "ref": "16d6849"
    },
    {
      "date": "2026-10-20",
      "column": "built",
      "label": "Version bump to `webtools-udp` `2.0.0a`; PR #59 opened and merged",
      "ref": "ca57a57"
    }
  ],
  "rows": [
    {
      "id": "validity-rule",
      "group": "Code",
      "name": "Handshake line validity rule",
      "system": "webtools-udp",
      "where": "main/HandshakeProtocol.kt",
      "gap": {
        "kind": "bug",
        "summary": "`parseHandshake` is `runCatching { require(verb); require(size >= 2); Handshake(tokens[1], tokens.getOrElse(2) { \"\" }) }`: `Iam  x` parses to a handshake named `\"\"`, `Iam bob  tok` to one indistinguishable from `Iam bob`, and a bare `Iam` is a `Result.failure` carrying a stack trace. Absent and present-but-empty slots are conflated, and the handshake's only throw path sits here.",
        "detail": "The split on a single U+0020 keeps consecutive empty strings, so a run of spaces produces an empty slot. `Result` is the module's type for operational failures; its domain outcomes (`Admission`, `SendOutcome`, `ReceiveOutcome`, `OfferResult`) are sealed types.",
        "evidence": [
          "main/HandshakeProtocol.kt:55-59 — `parseHandshake` with `getOrElse(2) { \"\" }`",
          "main/HandshakeProtocol.kt:68-69, :77 — `extraTokenCount`; verb-only `isHandshake`",
          "research/malformed-line-handling.md §3.2, §5.A — the sealed-outcome finding and the totality argument"
        ],
        "source": "research/README.md",
        "link": "research/README.md#how-a-malformed-line-is-represented-and-routed"
      },
      "options": [
        {
          "id": "sealed-outcome",
          "name": "A sealed `HandshakeParse` outcome with a `HandshakeDefect` enum",
          "summary": "A total `parseHandshake(tokens): HandshakeParse` with three arms, `NotAHandshake`, `Malformed(defect)` and `Valid(handshake)`; the credential slot read with `getOrNull`, so absent (`null`, carried as `\"\"`) and present-but-empty (malformed) are told apart.",
          "verdict": "accepted",
          "why": "Fits the module's idiom for domain outcomes, gives the coordinator a compiler-checked exhaustive `when`, carries the defect for the log, and allocates no exception on the path a hostile sender drives. KEEP-0127 reserves `Result` for generic failures.",
          "source": "architecture.md §5.1",
          "link": "architecture.md#9-alternatives-considered"
        },
        {
          "id": "keep-result",
          "name": "Keep `Result<Handshake>` and treat every post-recognition failure as malformed",
          "verdict": "declined",
          "why": "`runCatching` catches every `Throwable`, so an unrelated bug inside the parse would be answered `REFUSED malformed` instead of surfacing; it carries no defect for the log; it builds a stack-trace-bearing exception per hostile datagram.",
          "wins": "Only if zero churn in the two existing parse tests mattered more than those points.",
          "source": "architecture.md §9"
        },
        {
          "id": "typed-exception",
          "name": "A typed `MalformedHandshakeException` inside `Result`",
          "verdict": "declined",
          "why": "The stack-trace cost stays and exhaustiveness is lost: the coordinator would need `exceptionOrNull() as?` plus an `else` for failures it does not know.",
          "wins": "If the project paradigm forbade sealed outcomes, which it does not (`Admission`, `SendOutcome`).",
          "source": "architecture.md §9"
        },
        {
          "id": "precheck",
          "name": "A separate validity check before parsing",
          "verdict": "declined",
          "why": "Two copies of the slot logic, a `Result` that can no longer fail, and a check a caller can forget.",
          "source": "architecture.md §9"
        },
        {
          "id": "split-whitespace",
          "name": "Split on `\\s+`, the issue's second proposal",
          "verdict": "declined",
          "why": "Redefines the wire format and silently turns `Iam bob  tok` into a different valid line. The single-U+0020 split is the contract.",
          "source": "architecture.md §1.1, §9"
        },
        {
          "id": "credential-type",
          "name": "A `String?` credential or a `Credential` sum type in `Handshake`",
          "verdict": "declined",
          "why": "The only consumer maps it straight back to `String` for `admit`, and `\"\"` inside a `Handshake` is unambiguous once the slot read rejects present-but-empty.",
          "source": "architecture.md §9"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "`HandshakeProtocol` gains a sealed `HandshakeParse`, a three-entry `HandshakeDefect` enum with fixed log-safe labels, a total `parseHandshake` that checks the verb through `isHandshake`, then the name, then the credential, and `MALFORMED_REFUSAL_DATAGRAM` built once beside `REGISTERED_DATAGRAM`. `Handshake`, `isHandshake` and `extraTokenCount` keep their signatures; `MIN_TOKENS` goes.",
        "defense": "One definition of recognition (`isHandshake` feeds the `NotAHandshake` arm), one definition of validity, no throw path left in the handshake, and the name slot checked before the credential slot so `Iam  x y` reports the name defect. The sealed type is closed on purpose: the routing's exhaustive `when` is what guarantees that a future arm, such as #53's, is routed.",
        "steps": [
          {
            "id": "A-3",
            "what": "Replace the whole of `main/HandshakeProtocol.kt`: the sealed outcome, the enum, the total parse, the precomputed refusal bytes.",
            "files": [
              "main/HandshakeProtocol.kt"
            ],
            "check": "`./gradlew :webtools-udp:compileKotlin`; `grep -rn \"getOrThrow\\|isFailure\"` on the two parse tests prints nothing",
            "link": "plan.md#a-3-mainhandshakeprotocolkt--replace-the-whole-file"
          }
        ],
        "tests": [
          "Level 1 — `HandshakeProtocolGatingTest`, replaced: every outcome",
          "Level 4a — `HandshakeProtocolTest`, replaced: the truth table, totality, the reply bytes, the #53 gap pinned"
        ],
        "risks": [
          "Nothing public changes: `HandshakeParse`, `HandshakeDefect` and the constant are `internal` inside an `internal object`"
        ],
        "source": "plan.md §5, §7 A-3, §8 T-1 and T-7",
        "link": "plan.md#a-3-mainhandshakeprotocolkt--replace-the-whole-file"
      },
      "asBuilt": {
        "state": "amended",
        "summary": "Built as decided in Commit 2: the sealed outcome, the enum, the total parse with `getOrNull`, and the refusal bytes built once. Two review rounds reworded its KDoc.",
        "changes": [
          {
            "what": "The reply-bytes KDoc no longer cites `refusedMessage`'s internals; that rationale moved to a `//` comment.",
            "why": "A KDoc states a contract, not another function's implementation (first review, documentation finding 9).",
            "when": "Stage C · first review",
            "ref": "d3807b0"
          },
          {
            "what": "The KDoc calls the two replies fixed-text replies whose bytes never come from the application, not replies sent \"without consulting the application\".",
            "why": "A new origin's `REGISTERED 2` follows `admit`, so the old sentence was false (second review, item 1).",
            "when": "Stage D · second review",
            "ref": "d3807b0"
          }
        ],
        "files": [
          "main/HandshakeProtocol.kt"
        ],
        "tests": "gating 83 (+6) and deterministic 96 (+7) cover every parse outcome, totality, the verb check before any slot check, the reply bytes and the pinned #53 gap",
        "commits": [
          "1605bdd",
          "d3807b0"
        ],
        "verified": "All levels pass; `grep -rn getOrThrow` on the two parse tests prints nothing.",
        "link": "final-implementation.md#code"
      }
    },
    {
      "id": "routing",
      "group": "Code",
      "name": "Unframed-datagram routing",
      "system": "webtools-udp",
      "where": "main/HandshakeCoordinator.kt",
      "gap": {
        "kind": "bug",
        "summary": "`classify` splits the trimmed text twice (`isHandshake(text.split(' ')) -> handleHandshake(origin, text.split(' '))`) and `handleHandshake` parses first and routes a `Result`: a bare `Iam` ends in the listener's stack-trace WARN with no reply; `Iam  x` and `Iam bob  tok` get `REGISTERED 2` from a registered origin and reach `admit` from an unknown one, registering the name `\"\"`.",
        "detail": "An exception anywhere inside `accept` ends the `mcups-listener` thread (`flatMap` is a plain `fold`; `receiveLoop` has no `try`), so every new path has to be exception-free. The liveness stamp for a registered origin runs before classification and is out of scope.",
        "evidence": [
          "main/HandshakeCoordinator.kt:220-232 — the `null`-tag branch; two splits at :227",
          "main/HandshakeCoordinator.kt:243-293 — `handleHandshake`: parse, extra-token DEBUG, `findByOrigin`, `admit`, supersede, register",
          "main/MultiConnectionUDPServer.kt:326-340 — `receiveLoop` logs a `Result.failure` with the exception at :336",
          "main/ResultExtensions.kt:19-20 — `flatMap` does not catch"
        ],
        "source": "research/README.md",
        "link": "research/README.md#how-a-malformed-line-is-represented-and-routed"
      },
      "options": [
        {
          "id": "one-parse-four-way",
          "name": "One split, one parse, an exhaustive `when` that routes four ways",
          "summary": "`NotAHandshake` → today's \"sender may be pre-2.0\" WARN; `Malformed` → one WARN and `REFUSED malformed` to any origin, returning the send's `Result`; `Valid` → the extra-token DEBUG, then today's registered-origin retransmit or the untouched `admit`, supersede, register path.",
          "verdict": "accepted",
          "why": "One token list per hostile datagram instead of two, no exception allocation, and the compiler checks that every outcome is routed. The malformed arm returns the send's `Result` exactly as the `admit` refusal does, so the listener adds no stack-trace WARN.",
          "source": "architecture.md §5.2",
          "link": "architecture.md#9-alternatives-considered"
        },
        {
          "id": "silence-registered",
          "name": "Silence, or `REGISTERED 2`, for a registered origin's malformed line",
          "summary": "What the research recommended, following DTLS, WireGuard and ENet, which discard a bad first message silently.",
          "verdict": "declined",
          "why": "The registered peer and the unknown peer would follow different rules for the same bytes, and `REGISTERED 2` would answer an accept to a line the server defines as malformed. One reply for every malformed line keeps the rule one sentence; the reflection and client-WARN costs are stated in the protocol doc.",
          "wins": "If a strict 3x anti-amplification bound were adopted as a module requirement; it is not.",
          "source": "architecture.md §9; research/README.md § Where the findings differ"
        },
        {
          "id": "require-gate",
          "name": "Keep `isHandshake` as the gate in `classify` and a `require` on the verb inside `parseHandshake`",
          "verdict": "declined",
          "why": "An exception inside `accept` ends the listener thread; a total parse with a `NotAHandshake` arm costs one `when` arm and removes the only throw path.",
          "wins": "Only once #54's guard existed, and even then the arm is cheaper.",
          "source": "architecture.md §9"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "`classify`'s `null` branch splits once and calls a new `routeUnframed`, an exhaustive `when` over `HandshakeParse`. `refuseMalformed` handles the malformed arm with one WARN (`Refusing malformed handshake from {} ({}); origin registered: {}`: the origin, the defect label, the flag) and `send(MALFORMED_REFUSAL_DATAGRAM, origin)`. `handleHandshake` keeps the valid line's lookup and retransmit; `admitAndRegister` is today's body moved verbatim, #49 ordering included.",
        "defense": "The malformed arm never reaches `admit`, never looks up by name, never supersedes, registers nothing and fires no `onRegistered`; its registration lookup is read-only, for the log field. The WARN never carries the line, its tokens, the name or the credential, and attaches no exception (CWE-117; the OWASP logging guidance). `accept`'s `@return` narrows to: failure only when a reply could not be delivered or `admit` threw.",
        "steps": [
          {
            "id": "A-4",
            "what": "Five edits to `main/HandshakeCoordinator.kt`, top to bottom: the single split in `classify`, `routeUnframed`, `refuseMalformed`, the narrowed `handleHandshake`, `admitAndRegister`.",
            "files": [
              "main/HandshakeCoordinator.kt"
            ],
            "check": "`compileKotlin`; the component tests assert the WARN text through `LogCapture`",
            "link": "plan.md#a-4-mainhandshakecoordinatorkt--five-edits-top-to-bottom"
          }
        ],
        "tests": [
          "Level 1 — `HandshakeCoordinatorGatingTest`, one new test",
          "Level 2 — `HandshakeCoordinatorTest`: the three shapes from unknown and registered origins, `Iam bob tok  x`, no supersede, no `admit`, no `onRegistered`, the reply addressed to the origin only, the log content",
          "Level 3 — the `MultiConnectionUDPServerTest` flip; `MultiConnectionUDPServerAdmissionTest`: `admit` never consulted, the trailing space trimmed",
          "Level 4c — a malformed burst leaves the listener alive"
        ],
        "risks": [
          "Reflection: a forged source that knows a registered client's `ip:port` can make the server send it 17-byte datagrams at the sender's rate; payload ratio 5.7 on a 3-byte trigger, inside the range `admit` refusals already produce; no state created; stated in the protocol doc with #57",
          "Log noise until #41 lands: one WARN per malformed datagram, without the stack trace a bare `Iam` cost before",
          "A spoofed malformed line still refreshes a registered origin's idle timer: pre-existing, the stamp runs before classification"
        ],
        "source": "plan.md §5, §7 A-4, §12",
        "link": "plan.md#a-4-mainhandshakecoordinatorkt--five-edits-top-to-bottom"
      },
      "asBuilt": {
        "state": "amended",
        "summary": "Built as decided in Commit 2. The first review found the KDoc overstated the registration guarantee and understated `accept`'s failures; Commit 3 fixed both, marked the #49 ordering and added the module's first `//region`s.",
        "changes": [
          {
            "what": "The registration guarantee reads precisely: not torn down, superseded or re-screened, no `onClientDisconnect`, but the liveness stamp still refreshed when idle detection is on.",
            "why": "\"The registration is left exactly as it is\" was false with idle detection on; the research (blast-radius pitfall P5) had the caveat and the design dropped it (first review, finding 1).",
            "when": "Stage C · first review",
            "ref": "d3807b0"
          },
          {
            "what": "`accept`'s `@return` names three failure sources, an undeliverable reply, a throwing `admit` and a handler that cannot be handed to `dispatch`; the two-argument `accept` gained `@param`, `@return` and its trimmed-text precondition.",
            "why": "The planned `@return` omitted dispatch hand-off rejection (first review, findings 3 and 4).",
            "when": "Stage C · first review",
            "ref": "d3807b0"
          },
          {
            "what": "`admitAndRegister` carries `FIXME(#49)` at the registration line and its KDoc names #49 as a known defect.",
            "why": "The planned KDoc narrated the move and called the ordering \"deliberate\" (first review, finding 7).",
            "when": "Stage C · first review",
            "ref": "d3807b0"
          },
          {
            "what": "Six `//region`s: Inbound routing, Handshake state machine, Delivery to bound handlers, ClientChannel and scheduler shutdown, Liveness, Server-wide fan-out.",
            "why": "First review, finding 10; the repository's first regions.",
            "when": "Stage C · first review",
            "ref": "d3807b0"
          }
        ],
        "files": [
          "main/HandshakeCoordinator.kt"
        ],
        "tests": "component 235 (+12), integration 115 (+5), nonfunctional 65 (+3): the shapes from both origins, the send failure, the WARN content, a retry after a refusal, bursts with and without a registered socket",
        "commits": [
          "1605bdd",
          "d3807b0"
        ],
        "verified": "A hostile CR/LF line stays out of the log (4c); the listener survives a malformed burst and the registration count is unchanged.",
        "link": "final-implementation.md#stage-c-after-the-first-review"
      }
    },
    {
      "id": "refusal-reply",
      "group": "Code",
      "name": "The refusal reply",
      "system": "webtools-udp",
      "where": "main/HandshakeProtocol.kt · main/HandshakeWireFormat.kt",
      "gap": {
        "kind": "missing",
        "summary": "No reply exists for a malformed line: a bare `Iam` draws nothing and the empty-slot shapes draw an accept. The one reply builder, `HandshakeWireFormat.refusedMessage`, trims and collapses its reason and compiles a `Regex` on every call, the wrong cost on a path the sender paces.",
        "evidence": [
          "main/HandshakeWireFormat.kt:117-120 — `refusedMessage` compiles a `Regex` per call at :118",
          "research/refusal-reply-text.md §6.1, §7 — the text and the amplification arithmetic"
        ],
        "source": "research/README.md",
        "link": "research/README.md#what-the-refusal-says"
      },
      "options": [
        {
          "id": "refused-malformed",
          "name": "`REFUSED malformed`: one fixed reason, bytes built once",
          "summary": "17 bytes, the same for all three shapes, built once through `refusedMessage(\"malformed\")` into an `internal val` beside `REGISTERED_DATAGRAM`; the defect goes to the server log only.",
          "verdict": "accepted",
          "why": "A hand-rolled client's author needs a cause; one reason for three shapes gives a prober no oracle; precomputing avoids a `Regex` per hostile datagram. Payload ratio 5.7 on the 3-byte trigger (1.45 on the wire), in the range `admit` refusals already produce: the README's own `invalid credential` example is 5.2.",
          "source": "architecture.md §5.1, §9; research/refusal-reply-text.md",
          "link": "architecture.md#9-alternatives-considered"
        },
        {
          "id": "malformed-handshake",
          "name": "`REFUSED malformed handshake`",
          "verdict": "declined",
          "why": "Repeats \"handshake\" inside the client's own exception message (\"Server refused the handshake: …\") and gives the 3-byte trigger a ratio of 9.",
          "source": "architecture.md §9"
        },
        {
          "id": "per-slot-reason",
          "name": "A reason that names the slot",
          "verdict": "declined",
          "why": "Three replies instead of one, longer, and an oracle for a prober.",
          "source": "architecture.md §9"
        },
        {
          "id": "bare-refused",
          "name": "A bare `REFUSED`",
          "verdict": "declined",
          "why": "Leaves the hand-rolled-client author, who is exactly who sends a malformed line, with no cause; SMTP likewise advises against bare codes.",
          "source": "architecture.md §9; research/refusal-reply-text.md C1"
        },
        {
          "id": "configurable-reason",
          "name": "A parameterised or configurable reason text",
          "verdict": "declined",
          "why": "The reason is informative wire text a third-party client must not branch on, and an application's `admit` may return the same word; making it configurable invites the branching the protocol doc warns against.",
          "source": "architecture.md §8"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "`MALFORMED_REFUSAL_DATAGRAM`, an `internal val ByteArray` on `HandshakeProtocol`, built once from `HandshakeWireFormat.refusedMessage(\"malformed\")`; the reason string is a `private const val`. `HandshakeWireFormat` itself is not edited in code.",
        "defense": "The reply verb and its handling are already Stable Core (`REFUSED_REPLY`, `isRefused`, `refusalReason`); only the reason text is new and it sits outside the contract, so no consumer can build on it. The shared array is read-only, like `REGISTERED_DATAGRAM`.",
        "steps": [
          {
            "id": "A-3 (part)",
            "what": "Add the constant and the private reason beside `REGISTERED_DATAGRAM`.",
            "files": [
              "main/HandshakeProtocol.kt"
            ],
            "check": "Level 4a asserts the bytes equal `REFUSED malformed`",
            "link": "plan.md#a-3-mainhandshakeprotocolkt--replace-the-whole-file"
          }
        ],
        "tests": [
          "Level 4a — the reply bytes equal `REFUSED malformed`",
          "Level 3 — a bare `Iam` over a real socket gets `REFUSED malformed`"
        ],
        "risks": [
          "If the release label or the reason ever changed, every KDoc and protocol-doc mention changes with it"
        ],
        "source": "plan.md §5, §7 A-3",
        "link": "plan.md#5-design"
      },
      "asBuilt": {
        "state": "as-planned",
        "summary": "Built as decided in Commit 2: 17 bytes, built once, the reason informative. Protocol doc §3 says the reason is not a contract.",
        "files": [
          "main/HandshakeProtocol.kt"
        ],
        "commits": [
          "1605bdd"
        ],
        "verified": "4a asserts the bytes; 3 and 4b observe the reply over loopback.",
        "link": "final-implementation.md#behaviour"
      }
    },
    {
      "id": "public-kdoc",
      "group": "Code",
      "name": "Public KDoc contracts",
      "system": "webtools-udp",
      "where": "main/MultiConnectionUDPServer.kt · main/HandshakeWireFormat.kt",
      "gap": {
        "kind": "debt",
        "summary": "The class and `admit` KDoc of `MultiConnectionUDPServer` and the `REFUSED` section of `HandshakeWireFormat` say that `REFUSED` comes only from `admit` and that the default admits every well-formed `Iam`. After the fix both sentences are false, and `admit`'s input domain (never an empty name; credential `\"\"` only when absent) is stated nowhere.",
        "evidence": [
          "main/MultiConnectionUDPServer.kt:15-23, :43-54, :354-386 — class and `admit` KDoc",
          "main/HandshakeWireFormat.kt:35-39, :52-57 — the `REFUSED` reply section and `REFUSED_REPLY`"
        ],
        "source": "architecture.md §5.3",
        "link": "architecture.md#53-public-kdoc-no-behaviour-change"
      },
      "options": [
        {
          "id": "restate",
          "name": "Restate both KDocs: refused before `admit`, whatever the origin's state; `admit` never sees an empty name",
          "verdict": "accepted",
          "why": "Documents enforced behaviour; it narrows `admit`'s input domain and is additive for every existing override. Stable Core wording, no signature change. No alternative was weighed: a reader of the public API must not learn the rule from the protocol doc alone.",
          "source": "architecture.md §5.3, §8",
          "link": "architecture.md#53-public-kdoc-no-behaviour-change"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "KDoc only, no code: `MultiConnectionUDPServer` (class KDoc, `admit`) and `HandshakeWireFormat` (the `REFUSED` reply section, `REFUSED_REPLY`) state that a malformed `Iam` is refused `REFUSED malformed` before `admit` and whatever the origin's state, that `admit` never sees an empty `name` and sees `credential` as `\"\"` only when absent, and that the reason text is informative.",
        "defense": "The `admit` KDoc is where an implementer of a server looks; the protocol doc is where a client author looks. Both must say the same thing.",
        "steps": [
          {
            "id": "A-5",
            "what": "`main/MultiConnectionUDPServer.kt`, KDoc only.",
            "files": [
              "main/MultiConnectionUDPServer.kt"
            ],
            "link": "plan.md#a-5-mainmulticonnectionudpserverkt--kdoc-only-no-code"
          },
          {
            "id": "A-6",
            "what": "`main/HandshakeWireFormat.kt`, KDoc only.",
            "files": [
              "main/HandshakeWireFormat.kt"
            ],
            "link": "plan.md#a-6-mainhandshakewireformatkt--kdoc-only-no-code"
          }
        ],
        "tests": [
          "none: documentation"
        ],
        "risks": [],
        "source": "plan.md §7 A-5, A-6",
        "link": "plan.md#a-5-mainmulticonnectionudpserverkt--kdoc-only-no-code"
      },
      "asBuilt": {
        "state": "amended",
        "summary": "Written in Commit 2 and corrected in Commit 3: the planned text got the release history wrong and the liveness wording loose.",
        "changes": [
          {
            "what": "Release labels split and qualified: refusal by `admit` is `webtools-udp` `1.4.0`, the malformed refusal is `webtools-udp` `2.0.0a`.",
            "why": "The planned KDoc said a `1.4.0`+ server refuses malformed lines, while every release up to `2.0.0` registers the empty-slot shapes; and `2.0.0a` was also a version of the retired combined artifact, so the artifact is named (first review finding 2; second review item 2).",
            "when": "Stages C and D · both reviews",
            "ref": "d3807b0"
          },
          {
            "what": "The liveness text says any datagram from a registered client refreshes its stamp.",
            "why": "It read as if only application data and keepalives did (second review, item 3).",
            "when": "Stage D · second review",
            "ref": "d3807b0"
          },
          {
            "what": "\"Identical to pre-`1.4.0` behaviour\" is limited to well-formed lines.",
            "why": "First review, finding 2.",
            "when": "Stage C · first review",
            "ref": "d3807b0"
          }
        ],
        "files": [
          "main/MultiConnectionUDPServer.kt",
          "main/HandshakeWireFormat.kt"
        ],
        "commits": [
          "1605bdd",
          "d3807b0"
        ],
        "verified": "None of the superseded phrases remains in the sources (final-implementation.md §6).",
        "link": "final-implementation.md#stage-c-after-the-first-review"
      }
    },
    {
      "id": "tests",
      "group": "Tests",
      "name": "Test coverage, levels 1 to 4c",
      "system": "webtools-udp",
      "where": "test/gating · component · integration · deterministic · e2e · nonfunctional",
      "gap": {
        "kind": "missing",
        "summary": "Two tests assert today's silence for a bare `Iam` (`HandshakeCoordinatorTest` \"a nameless Iam fails and registers nothing\"; `MultiConnectionUDPServerTest:248`), two read `parseHandshake` as a `Result` and assert its failure type, and nothing covers the empty-slot shapes, a registered origin's malformed line, the log content or a malformed burst.",
        "detail": "Baseline counts on 85cfbbe: gating 77, component 223, deterministic 89, integration 110, nonfunctional 62, e2e 33, uat 13.",
        "evidence": [
          "test/component/.../HandshakeCoordinatorTest.kt:238-246",
          "test/integration/.../MultiConnectionUDPServerTest.kt:245-259",
          "test/deterministic/.../HandshakeProtocolTest.kt:40-43, :55-58; test/gating/.../HandshakeProtocolGatingTest.kt:27-30"
        ],
        "source": "architecture.md §10",
        "link": "architecture.md#10-decomposition"
      },
      "options": [
        {
          "id": "levels-1-4c",
          "name": "Flip the two silence tests; new cases at levels 1, 2, 3, 4a and 4c",
          "summary": "Nine test steps, T-1 to T-9, with expected counts after Stage A: gating 80, component 234, deterministic 95, integration 114, nonfunctional 63.",
          "verdict": "accepted",
          "why": "Each level pins a different claim: the parse table (4a), the routing and the log contract (2), the wire (3), listener survival under a burst (4c).",
          "source": "plan.md §8",
          "link": "plan.md#8-tests--stage-a-a-7"
        },
        {
          "id": "e2e-4b",
          "name": "A level-4b case through the library client",
          "summary": "A library client with an empty name gets a typed `malformed` refusal.",
          "verdict": "deferred",
          "why": "Optional in the plan: once #52 makes `handshake(\"\")` fail locally the case stops reaching the server, so every socket-level test of the new branch sends from a raw `DatagramSocket` instead.",
          "source": "plan.md §8 T-9"
        },
        {
          "id": "uat-l5",
          "name": "A level-5 UAT step",
          "verdict": "declined",
          "why": "The change's observable surface is the wire reply and the server log, which the level-3, 4b and 4c tests exercise end to end over real loopback sockets.",
          "source": "plan.md §8 T-9; confirmed by the maintainer at the first review"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "T-1 to T-8: replace `HandshakeProtocolGatingTest` and `HandshakeProtocolTest`, one new gating test on the coordinator, the component cases for both origins with `LogCapture` on the WARN, `FramingComponentTest` for `Iam<TAB>bob`, the integration flip and four admission tests, and a 4c burst mirroring `MultiConnectionUDPServerAdmissionNonFunctionalTest:104`.",
        "defense": "Every acceptance clause in the plan has a test at the level that can observe it; the burst test proves requirement 8 (no throw path) instead of arguing it.",
        "steps": [
          {
            "id": "A-7 · T-1..T-9",
            "what": "Add and flip the tests after A-3 to A-6 compile.",
            "files": [
              "test/gating/webtools/udp/HandshakeProtocolGatingTest.kt",
              "test/gating/webtools/udp/HandshakeCoordinatorGatingTest.kt",
              "test/component/webtools/udp/HandshakeCoordinatorTest.kt",
              "test/component/webtools/udp/FramingComponentTest.kt",
              "test/integration/webtools/udp/MultiConnectionUDPServerTest.kt",
              "test/integration/webtools/udp/MultiConnectionUDPServerAdmissionTest.kt",
              "test/deterministic/webtools/udp/HandshakeProtocolTest.kt",
              "test/nonfunctional/webtools/udp/MultiConnectionUDPServerAdmissionNonFunctionalTest.kt"
            ],
            "check": "the §8.1 level runs end BUILD SUCCESSFUL with the expected counts",
            "link": "plan.md#8-tests--stage-a-a-7"
          }
        ],
        "tests": [
          "The steps are the tests"
        ],
        "risks": [
          "The socket-binding tasks are serialised by `commonUdpPortLock`: run them in one Gradle invocation, not in parallel shells"
        ],
        "source": "plan.md §8, §8.1",
        "link": "plan.md#8-tests--stage-a-a-7"
      },
      "asBuilt": {
        "state": "amended",
        "summary": "Stage A's tests landed in Commit 2; the first review asked for eight more and the second corrected one probe. Final counts against 85cfbbe: gating 83 (+6), component 235 (+12), integration 115 (+5), deterministic 96 (+7), e2e 34 (+1), nonfunctional 65 (+3), uat 13 (unchanged, skipped). All pass.",
        "changes": [
          {
            "what": "Gating cases for the `CREDENTIAL_EMPTY` and `NotAHandshake` arms, a registered origin's malformed line, and the non-`Iam` drop asserting nothing was sent.",
            "why": "First review, test findings 1 and 2.",
            "when": "Stage C · first review",
            "ref": "c6be0f2"
          },
          {
            "what": "A component test named for `deliverData` never reached it (byte `0x00` is unframed): renamed and strengthened, plus a real no-handler `deliverData` test.",
            "why": "First review, test finding 3.",
            "when": "Stage C · first review",
            "ref": "c6be0f2"
          },
          {
            "what": "Integration: a retry on the same socket after a malformed refusal, and an empty-credential line from a registered socket. Deterministic: the verb check runs before any slot check; one test renamed after its behaviour.",
            "why": "First review, test findings 4 to 6.",
            "when": "Stage C · first review",
            "ref": "c6be0f2"
          },
          {
            "what": "The deferred 4b e2e test was added: a library client with an empty name gets a typed `malformed` refusal, pinned until #52.",
            "why": "Maintainer decision at the first review: it pins the client-visible behaviour and flips when #52 makes the call fail locally.",
            "when": "Stage C · first review",
            "ref": "c6be0f2",
            "settles": {
              "option": "e2e-4b",
              "verdict": "accepted"
            }
          },
          {
            "what": "Two 4c tests: a burst that includes a registered socket, and a hostile CR/LF line that must stay out of the log.",
            "why": "First review, test finding 8.",
            "when": "Stage C · first review",
            "ref": "c6be0f2"
          },
          {
            "what": "A bare `Thread.sleep(200)` in a 4c test became the named constant `CONNECT_SETTLE_MILLIS` with a comment.",
            "why": "First review, documentation finding 8.",
            "when": "Stage C · first review",
            "ref": "c6be0f2"
          },
          {
            "what": "The registered-socket burst test probes the peer with `pushToAll`, which consults the registry, and asserts that no `onClientDisconnect` fired and that only the registering line reached `admit`.",
            "why": "It probed through the captured `Connection`, which never consults the registry (second review, item 5).",
            "when": "Stage D · second review",
            "ref": "c6be0f2"
          }
        ],
        "commits": [
          "1605bdd",
          "c6be0f2"
        ],
        "verified": "All automated levels pass; the plan's expected counts after Stage C match the final tree.",
        "link": "final-implementation.md#tests"
      }
    },
    {
      "id": "test-housekeeping",
      "group": "Tests",
      "name": "Test housekeeping",
      "system": "webtools-udp",
      "where": "test/component/.../HandshakeCoordinatorTest.kt:577 · test/integration/.../ChannelByteGuardIntegrationTest.kt:25",
      "gap": {
        "kind": "debt",
        "summary": "A string literal at `HandshakeCoordinatorTest.kt:577` holds raw NUL and SOH bytes, so ripgrep (and the `Grep` tool) skips the whole file and a tool that normalises them corrupts the test. The comment at `ChannelByteGuardIntegrationTest.kt:25` points at a stale line range of the raw-socket peer in `FramingNonFunctionalTest.kt` (`:187-215` on 85cfbbe).",
        "evidence": [
          "test/component/.../HandshakeCoordinatorTest.kt:577",
          "test/integration/.../ChannelByteGuardIntegrationTest.kt:25",
          "research/codebase-blast-radius.md — editing hazards"
        ],
        "source": "architecture.md §1.10, §11",
        "link": "architecture.md#1-requirements"
      },
      "options": [
        {
          "id": "escapes-first",
          "name": "Replace the bytes with the escapes `\"\\u0000\\u0001\"` as the first edit to the file; refresh the comment",
          "verdict": "accepted",
          "why": "The same string, a searchable file, and no later edit can corrupt the bytes because they are gone before any other edit lands. Verified with `grep -a -c -P '\\x00'` returning 0 and a hunk-limited `git diff`. No alternative was weighed.",
          "source": "architecture.md §10 Stage A housekeeping",
          "link": "architecture.md#10-decomposition"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "A-1 and A-2 land first, in their own commit with the plan documents.",
        "defense": "Ordering is the whole point: every later edit to `HandshakeCoordinatorTest.kt` is safe only once the bytes are escapes.",
        "steps": [
          {
            "id": "A-1",
            "what": "Escape the NUL and SOH bytes with a byte-aware edit.",
            "files": [
              "test/component/webtools/udp/HandshakeCoordinatorTest.kt"
            ],
            "check": "`grep -a -c -P '\\x00' …HandshakeCoordinatorTest.kt` prints 0",
            "link": "plan.md#a-1-testcomponentwebtoolsudphandshakecoordinatortestkt--escape-the-nulsoh-bytes-first-edit-to-this-file"
          },
          {
            "id": "A-2",
            "what": "Refresh the comment's line range.",
            "files": [
              "test/integration/webtools/udp/ChannelByteGuardIntegrationTest.kt"
            ],
            "link": "plan.md#a-2-testintegrationwebtoolsudpchannelbyteguardintegrationtestkt25--comment"
          }
        ],
        "tests": [
          "The component suite still passes: the string is unchanged"
        ],
        "risks": [],
        "source": "plan.md §7 A-1, A-2",
        "link": "plan.md#a-1-testcomponentwebtoolsudphandshakecoordinatortestkt--escape-the-nulsoh-bytes-first-edit-to-this-file"
      },
      "asBuilt": {
        "state": "as-planned",
        "summary": "Commit 1 (56a2121), as the first edit to the file.",
        "files": [
          "test/component/webtools/udp/HandshakeCoordinatorTest.kt",
          "test/integration/webtools/udp/ChannelByteGuardIntegrationTest.kt"
        ],
        "commits": [
          "56a2121"
        ],
        "verified": "`grep -a -c -P '\\x00'` prints 0; component 223 still passed before Stage A's code.",
        "link": "final-implementation.md#1-commits"
      }
    },
    {
      "id": "protocol-doc",
      "group": "Documents",
      "name": "Protocol document",
      "system": "webtools-udp",
      "where": "docs/webtools-udp-protocol.md §3, §8, §9",
      "gap": {
        "kind": "debt",
        "summary": "§3 carries an \"as-built quirk\" paragraph describing the defect, §8 has the bare-`Iam` row as silence, and 21 of the document's 37 source line references are stale, measured before #40 and the 2.0.0 KDoc pass.",
        "evidence": [
          "docs/webtools-udp-protocol.md:58-101 — §3; the quirk paragraph at :74-78",
          "docs/webtools-udp-protocol.md:315-342 — §8; the bare-`Iam` row at :321",
          "research/codebase-blast-radius.md §5 — anchors for every reference"
        ],
        "source": "research/README.md",
        "link": "research/README.md#what-the-change-touches"
      },
      "options": [
        {
          "id": "stage-b-last",
          "name": "A separate Stage B after the code is final, recomputing every reference from the inventory's anchors",
          "verdict": "accepted",
          "why": "References into the four edited production files can only be computed after Stage A's code and KDoc are final, and a separate commit lets the documentation pass be reviewed on its own.",
          "source": "architecture.md §10, §11 Ordering constraint",
          "link": "architecture.md#10-decomposition"
        },
        {
          "id": "docs-with-code",
          "name": "Edit the documents in the same commit as the code",
          "verdict": "declined",
          "why": "Line numbers would be guessed, not computed.",
          "source": "architecture.md §10"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "§3: the rule, the registered-origin trade-off, the known #53 gap, the \"informative, not contract\" status of the reason, and a Boundary-Ring note on the replies an unverified source can draw (sizes, ratios, no address validation, no rate limit, #57). §8: replace the bare-`Iam` row, add the empty-slot and registered-origin rows, tighten the non-`Iam` row. Recheck all 37 references and refresh the 21 stale ones.",
        "defense": "The protocol doc is the wire contract's home; a third-party client author reads it, not the KDoc.",
        "steps": [
          {
            "id": "B-0..B-7",
            "what": "Pre-checks (CRLF working tree, LF index), the fixed references, the computed references, §3 prose, §8 rows, then the reference check script.",
            "files": [
              "docs/webtools-udp-protocol.md"
            ],
            "check": "every `File.kt:N` reference prints the first and last line its prose cites",
            "link": "plan.md#10-file-by-file-changes--stage-b-living-documents"
          }
        ],
        "tests": [
          "B-7 reference check"
        ],
        "risks": [
          "The document is CRLF in the working tree with an LF index: an editor that writes LF produces a whole-file diff; `git diff --stat` after each edit",
          "Non-ASCII (`—`, `→`, `§`) must survive the edit path"
        ],
        "source": "plan.md §10",
        "link": "plan.md#10-file-by-file-changes--stage-b-living-documents"
      },
      "asBuilt": {
        "state": "amended",
        "summary": "Stage B landed last, in Commit 5 (16d6849), after Stages C and D were final, with three corrections the reviews asked for and every reference recomputed from the final sources.",
        "changes": [
          {
            "what": "§9 gains a row for servers before 2.0.0a, with its source cited; the scope line names 2.0.0a.",
            "why": "First review finding 2; the second review's item 4 extended the citation to row 4.",
            "when": "Stages C and D",
            "ref": "16d6849"
          },
          {
            "what": "The list of library-client calls that send a malformed line states the rule, with examples.",
            "why": "It was wrong in both directions: it missed `handshake(\"bob \", credential = \"tok\")` and `handshake(\"a  b\")` and included the valid `credential = \" \"` (first review, finding 5).",
            "when": "Stage C · first review",
            "ref": "16d6849"
          },
          {
            "what": "\"A registered source can draw two replies\" is scoped to handshake replies and lists the transport's own `0x82` and `0xA1`.",
            "why": "It read as exhaustive (first review, finding 6).",
            "when": "Stage C · first review",
            "ref": "16d6849"
          }
        ],
        "files": [
          "docs/webtools-udp-protocol.md"
        ],
        "commits": [
          "16d6849"
        ],
        "verified": "The C-7 reference check and D-7's checks were re-run on the final tree; the file keeps CRLF.",
        "link": "final-implementation.md#documents"
      }
    },
    {
      "id": "architecture-doc-readme",
      "group": "Documents",
      "name": "Architecture document and README",
      "system": "webtools-udp",
      "where": "docs/webtools-udp-architecture.md · README.md",
      "gap": {
        "kind": "debt",
        "summary": "The architecture doc's `:52` reference points at `MultiConnectionUDPServer.kt:254`, which moved, and its handshake node describes the old routing. The README's handshake sections (`:103-135`, `:217-251`) say nothing about a malformed line.",
        "evidence": [
          "docs/webtools-udp-architecture.md:52, :111-112",
          "README.md:103-135, :217-251"
        ],
        "source": "architecture.md §1.9",
        "link": "architecture.md#1-requirements"
      },
      "options": [
        {
          "id": "reword",
          "name": "Refresh the reference and reword the node; update the README's message table, the retransmit and credential paragraph, the `admit` paragraph and \"Handshake screening & credentials\"",
          "verdict": "accepted",
          "why": "Both are living documents that describe the handshake; the README defines well-formed and links the rule rather than repeating it. No alternative was weighed.",
          "source": "architecture.md §10 Stage B",
          "link": "architecture.md#10-decomposition"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "B-5 and B-6 in Stage B.",
        "defense": "Small wording changes; the README gains a definition of well-formed, not a second copy of the rule.",
        "steps": [
          {
            "id": "B-5",
            "what": "Architecture doc: the `:52` reference and the handshake node.",
            "files": [
              "docs/webtools-udp-architecture.md"
            ],
            "link": "plan.md#b-5-architecture-doc-docswebtools-udp-architecturemd"
          },
          {
            "id": "B-6",
            "what": "README: the four handshake passages.",
            "files": [
              "README.md"
            ],
            "link": "plan.md#b-6-readme-readmemd"
          }
        ],
        "tests": [],
        "risks": [],
        "source": "plan.md §10 B-5, B-6",
        "link": "plan.md#b-5-architecture-doc-docswebtools-udp-architecturemd"
      },
      "asBuilt": {
        "state": "as-planned",
        "summary": "Commit 5 (16d6849). The architecture doc's reference reads `MultiConnectionUDPServer.kt:277`, the `coordinator` field. The README's install snippet moved to 2.0.0a with the version bump.",
        "files": [
          "docs/webtools-udp-architecture.md",
          "README.md"
        ],
        "commits": [
          "16d6849",
          "ca57a57"
        ],
        "verified": "Read by hand when the as-built record was written.",
        "link": "final-implementation.md#documents"
      }
    },
    {
      "id": "release",
      "group": "Release",
      "name": "Release: webtools-udp 2.0.0a",
      "system": "webtools-udp",
      "where": "webtools-udp/build.gradle.kts:92",
      "gap": {
        "kind": "missing",
        "summary": "The fix changes wire behaviour, so it needs a release; `webtools-udp` is at `2.0.0`. The module's convention for a bugfix is a letter suffix, and the plan's commits must not edit the build file.",
        "evidence": [
          "webtools-udp/build.gradle.kts:92 on 85cfbbe — `version = \"2.0.0\"`"
        ],
        "source": "architecture.md §1.7",
        "link": "architecture.md#1-requirements"
      },
      "options": [
        {
          "id": "own-commit",
          "name": "The release manager bumps to `2.0.0a` in its own `build:` commit after the plan's commits",
          "verdict": "accepted",
          "why": "Keeps the plan's commits free of the build file and leaves the pick to the release manager at landing; the README's install snippet moves with it.",
          "source": "plan.md header; §14 step 6",
          "link": "plan.md#14-sequencing--follow-ups"
        },
        {
          "id": "in-plan",
          "name": "Bump inside the plan's commits",
          "verdict": "declined",
          "why": "The plan's commits do not edit `webtools-udp/build.gradle.kts`; the version is the release manager's call.",
          "source": "plan.md § Target version"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "`webtools-udp` `2.0.0a`, set after Commit 5.",
        "defense": "A letter for a fix, the third number for an addition: the module's stated convention.",
        "steps": [
          {
            "id": "release",
            "what": "Bump the version and the README install snippet.",
            "files": [
              "webtools-udp/build.gradle.kts",
              "README.md"
            ],
            "link": "plan.md#14-sequencing--follow-ups"
          }
        ],
        "tests": [],
        "risks": [],
        "source": "plan.md §14",
        "link": "plan.md#14-sequencing--follow-ups"
      },
      "asBuilt": {
        "state": "as-planned",
        "summary": "ca57a57, then PR #59 opened and merged the same evening (806ce8e). The registry records `webtools-udp` 2.0.0a; Maven Central still carries 2.0.0 until the release is published.",
        "files": [
          "webtools-udp/build.gradle.kts",
          "README.md"
        ],
        "commits": [
          "ca57a57"
        ],
        "verified": "docs/dashboards/systems.json: version 2.0.0a, published 2.0.0.",
        "link": "final-implementation.md#1-commits"
      }
    },
    {
      "id": "whitespace-in-tokens",
      "group": "Left for later",
      "name": "Non-space whitespace inside a token (#53)",
      "system": "webtools-udp",
      "where": "main/HandshakeProtocol.kt",
      "gap": {
        "kind": "risk",
        "summary": "`Iam bob<TAB>tok` survives the trim as one token and registers the name `bob<TAB>tok`; a tab, CR/LF or NBSP inside a name or credential reaches the registry and the logs.",
        "evidence": [
          "research/README.md § Known gaps",
          "#53's issue text"
        ],
        "source": "architecture.md §1.6",
        "link": "architecture.md#4-non-goals"
      },
      "options": [
        {
          "id": "leave-to-53",
          "name": "Leave it to #53, document it as a known gap, pin it with a test",
          "verdict": "accepted",
          "why": "#53 settles it together with #52's client-side checks; it widens `HandshakeDefect` and adds checks inside `parseHandshake`, reusing this change's `Malformed` arm, reply constant and log template without change. #53 inherits the rule that every malformed line is refused whatever the origin.",
          "source": "architecture.md §4, §7",
          "link": "architecture.md#4-non-goals"
        },
        {
          "id": "fix-here",
          "name": "Widen the validity rule now to reject non-space whitespace",
          "verdict": "declined",
          "why": "Out of this issue's scope; the client-side half (#52) is not designed yet, and a rule settled in two places would drift.",
          "source": "architecture.md §4"
        }
      ],
      "verdict": {
        "kind": "leave",
        "summary": "Not changed. The protocol doc describes the gap as known and links #53; a 4a test pins today's behaviour and flips when #53 lands.",
        "defense": "A gap a reader can find in the protocol doc, and a test that fails the day it is fixed, is the cheapest honest handling.",
        "steps": [
          {
            "id": "T-7 (part)",
            "what": "`HandshakeProtocolTest`: a tab inside a name is still `Valid`, known gap tracked as #53.",
            "files": [
              "test/deterministic/webtools/udp/HandshakeProtocolTest.kt"
            ],
            "link": "plan.md#t-7-level-4a--testdeterministicwebtoolsudphandshakeprotocoltestkt-replace-the-whole-file"
          }
        ],
        "tests": [
          "Level 4a — the pinning test"
        ],
        "risks": [],
        "source": "plan.md §4, §12",
        "link": "plan.md#4-non-goals"
      },
      "asBuilt": {
        "state": "as-planned",
        "summary": "Left as planned: the 4a test `parseHandshake accepts non-space whitespace inside a name - known gap tracked as 53` pins it, protocol doc §3 links #53, and a comment on #53 records that it inherits this change's rule.",
        "commits": [
          "1605bdd"
        ],
        "verified": "The pinning test passes on the final tree.",
        "link": "final-implementation.md#8-follow-ups"
      }
    },
    {
      "id": "client-validation",
      "group": "Left for later",
      "name": "Client-side handshake validation (#52)",
      "system": "webtools-udp",
      "where": "main/HandshakeWireFormat.kt:72-73 · main/MultiConnectionUDPClient.kt:302",
      "gap": {
        "kind": "risk",
        "summary": "`handshakeMessage(\"\")` builds `Iam ` unvalidated and `MultiConnectionUDPClient.handshake` sends it inside `runCatching`; a library client can still put a malformed line on the wire and learns of it only from the server.",
        "evidence": [
          "main/HandshakeWireFormat.kt:72-73",
          "main/MultiConnectionUDPClient.kt:302",
          "architecture.md §7 — adoption verdicts"
        ],
        "source": "architecture.md §4",
        "link": "architecture.md#4-non-goals"
      },
      "options": [
        {
          "id": "leave-to-52",
          "name": "Leave the client to #52; test the new server branch from raw sockets",
          "verdict": "accepted",
          "why": "#52 owns the client; once it lands the library client cannot send a malformed line, which is why every socket-level test of the new branch sends from a `DatagramSocket`.",
          "source": "architecture.md §4, §7",
          "link": "architecture.md#4-non-goals"
        },
        {
          "id": "validate-now",
          "name": "Add the client-side checks in this change",
          "verdict": "declined",
          "why": "The issue is the server's rule; #52 is filed and its design will settle both the message builder and `handshake()`.",
          "source": "architecture.md §4"
        }
      ],
      "verdict": {
        "kind": "leave",
        "summary": "Not changed. A library client with an empty name now fails fast with `HandshakeRefusedException`, reason `malformed`; #52 will move that failure local.",
        "defense": "The protocol doc lists the library-client calls that send a malformed line, so the behaviour is documented until #52 removes it.",
        "steps": [],
        "tests": [],
        "risks": [],
        "source": "plan.md §4",
        "link": "plan.md#4-non-goals"
      },
      "asBuilt": {
        "state": "amended",
        "summary": "Left as planned, and pinned harder than planned: the first review added the 4b e2e test that fails fast with a typed `malformed` refusal and flips when #52 lands.",
        "changes": [
          {
            "what": "The 4b e2e test `a nameless handshake fails fast with a typed malformed refusal and registers nothing`.",
            "why": "Maintainer decision at the first review.",
            "when": "Stage C · first review",
            "ref": "c6be0f2"
          }
        ],
        "commits": [
          "c6be0f2"
        ],
        "verified": "e2e 34 (+1) passes.",
        "link": "final-implementation.md#4-decisions-taken-during-the-reviews"
      }
    }
  ],
  "roadmap": null,
  "followUps": [
    {
      "issue": 52,
      "note": "Client-side handshake validation. Makes the library client unable to send a malformed line; why the socket-level tests here use a raw socket. The 4b test pins today's behaviour and flips when #52 lands."
    },
    {
      "issue": 53,
      "note": "Non-space whitespace in names and credentials. Adds `HandshakeDefect` entries and checks inside `parseHandshake`, and replaces the protocol doc's known-gap paragraph. Inherits the rule that every malformed line is refused whatever its origin."
    },
    {
      "issue": 54,
      "note": "A throwing `onClientConnect` ends the listener thread. Orthogonal: this change adds no throw path; #54's guard lands in `receiveLoop` and around `onRegistered`."
    },
    {
      "issue": 41,
      "note": "Rate-bounding the per-datagram WARN logs. Covers the new malformed WARN and the client's unframed-drop WARN for a reflected `REFUSED`; the fixed template and defect label are its key."
    },
    {
      "issue": 49,
      "note": "Registration orphaned when the `REGISTERED` send fails. Its fix lands inside the moved, unchanged admit-and-register path, now marked `FIXME(#49)`."
    },
    {
      "issue": 56,
      "note": "A registered origin's `Iam` gets `REGISTERED 2` even when it names a different client. Pre-existing and unchanged; the protocol doc states it."
    },
    {
      "issue": 57,
      "note": "Replies to unverified source addresses, with no rate limit. Unchanged by this fix, which adds one more reply (17 bytes) to the set; the protocol doc advises edge rate limiting."
    }
  ],
  "documents": [
    {
      "label": "Issue #38",
      "path": "https://github.com/SpartanLaboratories/WebTools/issues/38",
      "note": "the report, with the two proposals the research weighed"
    },
    {
      "label": "PR #59",
      "path": "https://github.com/SpartanLaboratories/WebTools/pull/59",
      "note": "the merged change, Stages A to D and the version bump"
    },
    {
      "label": "Research",
      "path": "research/README.md",
      "note": "the three questions, verification and summary; findings in `codebase-blast-radius.md`, `malformed-line-handling.md`, `refusal-reply-text.md`"
    },
    {
      "label": "Architecture",
      "path": "architecture.md",
      "note": "requirements, current state, system inventory, alternatives (§9), decomposition (§10), risks"
    },
    {
      "label": "plan.md",
      "path": "plan.md",
      "note": "the implementer's spec: every step with its file, anchor, text and check, Stages A to D"
    },
    {
      "label": "final-implementation.md",
      "path": "final-implementation.md",
      "note": "the as-built record: commits, what was built, how the plan grew, the review decisions"
    },
    {
      "label": "Impact",
      "path": "impact.md",
      "note": "wire behaviour before and after, who sees the change, blast radius, adoption verdicts, follow-ups"
    },
    {
      "label": "Protocol reference",
      "path": "../../webtools-udp-protocol.md",
      "note": "the living wire reference; §3, §8 and §9 describe the rule"
    }
  ],
  "notes": {
    "paths": "Paths under `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/` are abbreviated `main/`; paths under `webtools-udp/src/test/kotlin/com/spartanlabs/testing/` are abbreviated `test/`. Line references are on the baseline 85cfbbe unless a change names a later commit.",
    "retrofit": "This board was written after the fact, as the worked example for plan boards, from the issue's research, architecture, plan, impact and as-built documents; its dates come from the commits and the pull request. Its qa passes are the two reviews of the change set. A board kept from the start of a run is updated at every step transition, and its WIP strip shows where the run is.",
    "corrections": [
      {
        "what": "The release row's system is `webtools-udp`: `webtools-udp/build.gradle.kts` sits under that module's registry paths, not the Gradle build's.",
        "when": "re-templating, 2026-10-22"
      },
      {
        "what": "The tests row's as-built register gains the first review's documentation finding 8 (the `Thread.sleep` constant), which the register had missed; the deferred 4b option is marked as settled by the first review's decision.",
        "when": "re-templating, 2026-10-22"
      }
    ]
  },
  "impact": {
    "basis": "as-built",
    "summary": "A bugfix with no API change: a malformed `Iam` is now answered `REFUSED malformed` instead of being accepted or ignored. Server applications and clients that send well-formed handshakes see no difference; a library client's malformed handshake now fails fast. It ships as the bugfix release `webtools-udp` `2.0.0a`.",
    "none": null,
    "release": {
      "bump": "fix",
      "version": "webtools-udp 2.0.0a",
      "note": "the bugfix release after `2.0.0`, set in its own `build:` commit"
    },
    "downstream": [
      {
        "who": "Server applications (subclasses of `MultiConnectionUDPServer`)",
        "kind": "consumer",
        "system": "webtools-udp",
        "effect": "behaviour",
        "summary": "`admit` never sees an empty `name`, and sees `credential` as `\"\"` only when the client sent none; `onClientConnect` never fires for an empty name. No signature changes.",
        "link": "impact.md#2-who-sees-the-change"
      },
      {
        "who": "Library clients (`MultiConnectionUDPClient`)",
        "kind": "consumer",
        "system": "webtools-udp",
        "effect": "behaviour",
        "summary": "`handshake(\"\")` used to time out, and `handshake(\"\", credential = \"tok\")` registered under the empty name; both now fail fast with `HandshakeRefusedException` and `reason == \"malformed\"`. Once #52 lands, these calls fail locally instead.",
        "link": "impact.md#2-who-sees-the-change"
      },
      {
        "who": "Third-party and hand-rolled clients",
        "kind": "consumer",
        "system": null,
        "effect": "behaviour",
        "summary": "A malformed line is now refused rather than accepted or ignored, and a `REFUSED` can arrive mid-session when a malformed `Iam` is sent from, or forged as, their address; the server keeps them registered.",
        "link": "impact.md#2-who-sees-the-change"
      },
      {
        "who": "Operators",
        "kind": "operator",
        "system": "webtools-udp",
        "effect": "behaviour",
        "summary": "One WARN per malformed line, carrying the origin, a fixed defect label and the registered flag, never the line; the stack-trace WARN a bare `Iam` drew is gone. The server has no reply rate limiting (#57).",
        "link": "impact.md#2-who-sees-the-change"
      },
      {
        "who": "`io.github.spartanlaboratories:webtools-udp`",
        "kind": "artifact",
        "system": "webtools-udp",
        "effect": "none",
        "summary": "No API change. A consumer whose clients send well-formed handshakes sees no difference on the wire; the fix ships as `2.0.0a`.",
        "link": "impact.md#5-downstream-consumers"
      }
    ],
    "adaptation": [
      {
        "who": "Server applications",
        "system": "webtools-udp",
        "need": "none",
        "summary": "No signature changes, and nothing to adopt.",
        "link": "impact.md#2-who-sees-the-change"
      },
      {
        "who": "Third-party and hand-rolled clients",
        "system": null,
        "need": "optional",
        "summary": "They should not take a stray `REFUSED` on an established session as a disconnect: the server keeps them registered. The protocol doc says so.",
        "link": "impact.md#2-who-sees-the-change"
      },
      {
        "who": "Deployments exposed to untrusted networks",
        "system": null,
        "need": "optional",
        "summary": "Rate-limit unregistered sources at the edge: a bare `Iam` draws a payload ratio of 5.7, and the server has no address validation or reply rate limiting (#57).",
        "link": "impact.md#2-who-sees-the-change"
      }
    ],
    "api": [
      {
        "surface": "`MultiConnectionUDPServer.admit` (KDoc)",
        "system": "webtools-udp",
        "change": "changed",
        "tier": "stable-core",
        "summary": "The KDoc states that `name` is never empty and that `credential` is `\"\"` only when absent: enforced behaviour documented, additive for every existing override. No signature change.",
        "link": "architecture.md#8-extension--stability"
      },
      {
        "surface": "`REFUSED` reply to a malformed `Iam` (wire)",
        "system": "webtools-udp",
        "change": "added",
        "tier": "stable-core",
        "summary": "The protocol doc gains the behaviour; the reply verb and its handling (`REFUSED_REPLY`, `isRefused`, `refusalReason`) were already Stable Core. The reason text `malformed` is informative and outside the contract.",
        "link": "architecture.md#8-extension--stability"
      },
      {
        "surface": "`HandshakeParse`, `HandshakeDefect`, `MALFORMED_REFUSAL_DATAGRAM`",
        "system": "webtools-udp",
        "change": "added",
        "tier": "internal",
        "summary": "Internal, inside an `internal object`: no new public surface, so no stability tier is assigned.",
        "link": "architecture.md#8-extension--stability"
      }
    ],
    "conversion": [
      {
        "what": "Move to `webtools-udp` `2.0.0a`; a server application changes no code.",
        "link": "impact.md#5-downstream-consumers",
        "compat": "notable"
      },
      {
        "what": "In a third-party client, treat a `REFUSED` on an established session as non-fatal, as the protocol doc's §3 describes.",
        "link": "impact.md#2-who-sees-the-change"
      },
      {
        "what": "Where the server faces untrusted networks, rate-limit unregistered sources at the edge until #57 lands.",
        "link": "impact.md#2-who-sees-the-change"
      }
    ],
    "changes": [],
    "source": "impact.md",
    "link": "impact.md#2-who-sees-the-change"
  }
}
);
