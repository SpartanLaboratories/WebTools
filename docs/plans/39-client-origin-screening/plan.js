/* plan.js: plan.json as a script, written by plan/scripts/embed.js beside the board for its live view (plan/RULES.md §1). Do not edit: edit plan.json and re-embed. */
window.dashboardLive&&window.dashboardLive("plan",
{
  "schema": "plan-board/1",
  "repo": "SpartanLaboratories/WebTools",
  "issue": 39,
  "slug": "39-client-origin-screening",
  "folder": "docs/plans/39-client-origin-screening",
  "kind": "issue",
  "title": "Client origin screening",
  "issueTitle": "Client only checks datagram origin on the reliable-channel receive branch",
  "function": "The `webtools-udp` client screens an inbound datagram's origin against `serverEndpoint` on its reliable receive branch alone, so its keepalive, `PROBE_PING`, `PROBE_PONG` and `0x90` branches act on a datagram from any source as if the server had sent it. The issue asks for the same origin check on every client receive branch, with tests per branch that an off-origin datagram is dropped and legitimate traffic is not disturbed. The run is sized as a contained fix to the client's receive loop, with no public API, state or wire-format change.",
  "status": "building",
  "sized": "2026-11-22T02:15:29Z",
  "parent": null,
  "fixOf": null,
  "branch": "fix/issue-39-client-datagram-origin-check",
  "baseline": {
    "ref": "master",
    "sha": "d68ec81e82828911f373bc4940da34580786aaee",
    "date": "2026-11-16"
  },
  "asBuilt": null,
  "systems": [
    {
      "slug": "webtools-udp",
      "name": "webtools-udp",
      "slot": 1,
      "top": "webtools-udp",
      "status": "built",
      "board": "docs/dashboards/webtools-udp/index.html"
    }
  ],
  "wip": {
    "step": "document",
    "since": "2026-11-22T03:26:12Z",
    "updated": "2026-11-22T04:31:51Z",
    "detail": "Document: 7 of 7 tasks done; every task done.",
    "steps": [
      "sizing",
      "plan",
      "branch",
      "implementation",
      "qa",
      "remediation",
      "document",
      "landing",
      "merge",
      "landed"
    ],
    "current": [
      "built"
    ],
    "questions": [],
    "research": null,
    "interview": [],
    "plan": null,
    "stages": [
      {
        "id": "s1",
        "name": "Regression tests, failing first",
        "state": "done",
        "rows": [
          "off-origin-tests"
        ],
        "started": "2026-11-22T02:45:14Z",
        "finished": "2026-11-22T02:50:39Z",
        "report": "The regression tests are written and fail against the unfixed client listener as predicted: the new gating test fails because the unreliable handler receives the foreign socket's 0x90; the six new component tests fail on the keepalive's missing unexpected-origin line, the 0x82 sent back for a foreign 0x81, linkQuality() completed by a foreign 0x82, the foreign 0x90 delivered, the missing shared drop line for a foreign 0xA1, and the unframed WARN drawn by a foreign datagram; the rewritten framing test fails on the missing DEBUG drop line, and the reliable test whose expected message moved to the shared wording fails on that wording."
      },
      {
        "id": "s2",
        "name": "Origin screen in the client listener",
        "state": "done",
        "rows": [
          "client-receive-origin"
        ],
        "started": "2026-11-22T02:50:39Z",
        "finished": "2026-11-22T02:52:21Z",
        "report": "MultiConnectionUDPClient's listener now checks every datagram's source against the server endpoint once, before it reads the tag, and drops a stranger's datagram with one DEBUG line naming both addresses; the reliable branch's own check is gone, and the class KDoc gains a Datagram origin section. The new gating test and the targeted component tests (the new origin-screen class, the framing and reliable classes) all pass: 1 gating test and 27 component tests, none failed."
      },
      {
        "id": "s3",
        "name": "Protocol and architecture documents",
        "state": "done",
        "rows": [
          "client-receive-origin"
        ],
        "started": "2026-11-22T02:52:21Z",
        "finished": "2026-11-22T02:53:45Z",
        "report": "The wire-protocol document now states that the client checks every datagram's source against the server endpoint before reading its tag, in its transport section, the 0x81/0x82 and 0x90 entries, the channel-byte section and the error-handling table, applies as of 2.0.1a, and no longer describes the gap; every MultiConnectionUDPClient.kt line reference in it was recomputed against the changed source. The architecture document's client differences say the same."
      },
      {
        "id": "s4",
        "name": "Module test sets and API reference",
        "state": "done",
        "rows": [
          "client-receive-origin",
          "off-origin-tests"
        ],
        "started": "2026-11-22T02:53:45Z",
        "finished": "2026-11-22T03:00:02Z",
        "report": "webtools-udp's gating, component, integration, e2e and nonfunctional tests all pass with the origin screen in place: 84 gating, 241 component, 115 integration, 34 e2e and 65 nonfunctional tests, none failed or skipped. The API-reference build (dokkaGeneratePublicationHtml) succeeds with the new class KDoc."
      }
    ],
    "remediation": {
      "pass": 1,
      "route": "on-the-spot",
      "steps": [
        {
          "name": "Off-origin component cases for a reserved tag, a malformed 0x81, a malformed 0x90 and an empty datagram, and 0xA2 in the gating test",
          "state": "done",
          "finished": "2026-11-22T03:25:47Z",
          "report": "The three small items from review pass 1 are made: the origin-screen component class now has ten cases and the gating smoke test sends eight foreign datagrams; the older off-origin 0xA0 test waits on the drop line; the protocol document lists the earlier clients' behaviour in section 9. The gating and component sets pass (84 and 245 tests) and the API reference builds."
        },
        {
          "name": "The older off-origin 0xA0 component test waits on the drop line instead of a fixed sleep",
          "state": "done",
          "finished": "2026-11-22T03:25:47Z",
          "report": "The three small items from review pass 1 are made: the origin-screen component class now has ten cases and the gating smoke test sends eight foreign datagrams; the older off-origin 0xA0 test waits on the drop line; the protocol document lists the earlier clients' behaviour in section 9. The gating and component sets pass (84 and 245 tests) and the API reference builds."
        },
        {
          "name": "Protocol document section 9 row for 2.0.0 to 2.0.1 clients",
          "state": "done",
          "finished": "2026-11-22T03:25:47Z",
          "report": "The three small items from review pass 1 are made: the origin-screen component class now has ten cases and the gating smoke test sends eight foreign datagrams; the older off-origin 0xA0 test waits on the drop line; the protocol document lists the earlier clients' behaviour in section 9. The gating and component sets pass (84 and 245 tests) and the API reference builds."
        }
      ]
    },
    "open": [],
    "event": {
      "name": "document",
      "at": "2026-11-22T04:31:51Z",
      "note": "7 of 7 tasks done"
    },
    "history": {
      "sizing": [
        {
          "since": null,
          "ended": "2026-11-22T02:15:29Z",
          "endedBy": "sized",
          "to": "plan"
        }
      ],
      "plan": [
        {
          "since": "2026-11-22T02:15:29Z",
          "ended": "2026-11-22T02:41:58Z",
          "endedBy": "plan-accepted",
          "to": "branch",
          "event": {
            "name": "sized",
            "at": "2026-11-22T02:15:29Z"
          },
          "detail": "Sized as a small fix to the client's receive loop in webtools-udp: no public API, state or wire-format change.",
          "plan": {
            "state": "writing",
            "since": "2026-11-22T02:15:29Z"
          }
        }
      ],
      "branch": [
        {
          "since": "2026-11-22T02:41:58Z",
          "ended": "2026-11-22T02:45:14Z",
          "endedBy": "branch-cut",
          "to": "implementation",
          "event": {
            "name": "plan-accepted",
            "at": "2026-11-22T02:41:58Z"
          },
          "detail": "The plan was accepted: one source check ahead of the client listener's tag switch covers every receive branch, with per-branch regression tests written to fail first.",
          "branch": "fix/issue-39-client-datagram-origin-check"
        }
      ],
      "implementation": [
        {
          "since": "2026-11-22T02:45:14Z",
          "ended": "2026-11-22T03:00:55Z",
          "endedBy": "qa-pass",
          "to": "qa",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-22T03:00:02Z",
            "note": "s4 reported done; Quality Review next"
          },
          "detail": "webtools-udp's gating, component, integration, e2e and nonfunctional tests all pass with the origin screen in place: 84 gating, 241 component, 115 integration, 34 e2e and 65 nonfunctional tests, none failed or skipped. The API-reference build (dokkaGeneratePublicationHtml) succeeds with the new class KDoc.",
          "stages": [
            {
              "id": "s1",
              "name": "Regression tests, failing first",
              "state": "done",
              "rows": [
                "off-origin-tests"
              ],
              "started": "2026-11-22T02:45:14Z",
              "finished": "2026-11-22T02:50:39Z",
              "report": "The regression tests are written and fail against the unfixed client listener as predicted: the new gating test fails because the unreliable handler receives the foreign socket's 0x90; the six new component tests fail on the keepalive's missing unexpected-origin line, the 0x82 sent back for a foreign 0x81, linkQuality() completed by a foreign 0x82, the foreign 0x90 delivered, the missing shared drop line for a foreign 0xA1, and the unframed WARN drawn by a foreign datagram; the rewritten framing test fails on the missing DEBUG drop line, and the reliable test whose expected message moved to the shared wording fails on that wording."
            },
            {
              "id": "s2",
              "name": "Origin screen in the client listener",
              "state": "done",
              "rows": [
                "client-receive-origin"
              ],
              "started": "2026-11-22T02:50:39Z",
              "finished": "2026-11-22T02:52:21Z",
              "report": "MultiConnectionUDPClient's listener now checks every datagram's source against the server endpoint once, before it reads the tag, and drops a stranger's datagram with one DEBUG line naming both addresses; the reliable branch's own check is gone, and the class KDoc gains a Datagram origin section. The new gating test and the targeted component tests (the new origin-screen class, the framing and reliable classes) all pass: 1 gating test and 27 component tests, none failed."
            },
            {
              "id": "s3",
              "name": "Protocol and architecture documents",
              "state": "done",
              "rows": [
                "client-receive-origin"
              ],
              "started": "2026-11-22T02:52:21Z",
              "finished": "2026-11-22T02:53:45Z",
              "report": "The wire-protocol document now states that the client checks every datagram's source against the server endpoint before reading its tag, in its transport section, the 0x81/0x82 and 0x90 entries, the channel-byte section and the error-handling table, applies as of 2.0.1a, and no longer describes the gap; every MultiConnectionUDPClient.kt line reference in it was recomputed against the changed source. The architecture document's client differences say the same."
            },
            {
              "id": "s4",
              "name": "Module test sets and API reference",
              "state": "done",
              "rows": [
                "client-receive-origin",
                "off-origin-tests"
              ],
              "started": "2026-11-22T02:53:45Z",
              "finished": "2026-11-22T03:00:02Z",
              "report": "webtools-udp's gating, component, integration, e2e and nonfunctional tests all pass with the origin screen in place: 84 gating, 241 component, 115 integration, 34 e2e and 65 nonfunctional tests, none failed or skipped. The API-reference build (dokkaGeneratePublicationHtml) succeeds with the new class KDoc."
            }
          ]
        }
      ],
      "qa": [
        {
          "since": "2026-11-22T03:00:55Z",
          "ended": "2026-11-22T03:21:12Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-22T03:00:55Z",
            "note": "Quality Review 1: running"
          },
          "detail": "Quality Review 1 running.",
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-22",
              "at": "2026-11-22T03:21:12Z",
              "findings": {
                "documentation": 0,
                "test": 3,
                "code": 1
              },
              "severity": {
                "minor": 3,
                "other": 1
              },
              "route": "on-the-spot",
              "report": "build/reports/tests/index.html",
              "reportCopy": "39-client-origin-screening/ca070e8d/tests/index.html",
              "note": "The fix is correct, and its regression tests fail before it for the right reasons. Three minor items are fixed on the spot: component cases for an off-origin reserved tag, a malformed `0x81`, a malformed `0x90` and an empty datagram, and a wait on the drop line in place of a fixed sleep in the older off-origin `0xA0` test (Off-origin receive tests); and a §9 row in the protocol document for clients before this release (Client receive-loop origin screening). The fourth item, component tests on real loopback sockets, is the module's standing pattern: changing it needs a socket-free receive seam, so it is not changed here."
            }
          ]
        }
      ],
      "remediation": [
        {
          "since": "2026-11-22T03:21:12Z",
          "ended": "2026-11-22T03:26:12Z",
          "endedBy": "document",
          "to": "document",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-22T03:25:47Z",
            "note": "3 of 3 sub-steps done"
          },
          "detail": "The three small items from review pass 1 are made: the origin-screen component class now has ten cases and the gating smoke test sends eight foreign datagrams; the older off-origin 0xA0 test waits on the drop line; the protocol document lists the earlier clients' behaviour in section 9. The gating and component sets pass (84 and 245 tests) and the API reference builds.",
          "remediation": {
            "pass": 1,
            "route": "on-the-spot",
            "steps": [
              {
                "name": "Off-origin component cases for a reserved tag, a malformed 0x81, a malformed 0x90 and an empty datagram, and 0xA2 in the gating test",
                "state": "done",
                "finished": "2026-11-22T03:25:47Z",
                "report": "The three small items from review pass 1 are made: the origin-screen component class now has ten cases and the gating smoke test sends eight foreign datagrams; the older off-origin 0xA0 test waits on the drop line; the protocol document lists the earlier clients' behaviour in section 9. The gating and component sets pass (84 and 245 tests) and the API reference builds."
              },
              {
                "name": "The older off-origin 0xA0 component test waits on the drop line instead of a fixed sleep",
                "state": "done",
                "finished": "2026-11-22T03:25:47Z",
                "report": "The three small items from review pass 1 are made: the origin-screen component class now has ten cases and the gating smoke test sends eight foreign datagrams; the older off-origin 0xA0 test waits on the drop line; the protocol document lists the earlier clients' behaviour in section 9. The gating and component sets pass (84 and 245 tests) and the API reference builds."
              },
              {
                "name": "Protocol document section 9 row for 2.0.0 to 2.0.1 clients",
                "state": "done",
                "finished": "2026-11-22T03:25:47Z",
                "report": "The three small items from review pass 1 are made: the origin-screen component class now has ten cases and the gating smoke test sends eight foreign datagrams; the older off-origin 0xA0 test waits on the drop line; the protocol document lists the earlier clients' behaviour in section 9. The gating and component sets pass (84 and 245 tests) and the API reference builds."
              }
            ]
          },
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-22",
              "at": "2026-11-22T03:21:12Z",
              "findings": {
                "documentation": 0,
                "test": 3,
                "code": 1
              },
              "severity": {
                "minor": 3,
                "other": 1
              },
              "route": "on-the-spot",
              "report": "build/reports/tests/index.html",
              "reportCopy": "39-client-origin-screening/ca070e8d/tests/index.html",
              "note": "The fix is correct, and its regression tests fail before it for the right reasons. Three minor items are fixed on the spot: component cases for an off-origin reserved tag, a malformed `0x81`, a malformed `0x90` and an empty datagram, and a wait on the drop line in place of a fixed sleep in the older off-origin `0xA0` test (Off-origin receive tests); and a §9 row in the protocol document for clients before this release (Client receive-loop origin screening). The fourth item, component tests on real loopback sockets, is the module's standing pattern: changing it needs a socket-free receive seam, so it is not changed here."
            }
          ]
        }
      ]
    },
    "next": null,
    "qa": [
      {
        "pass": 1,
        "verdict": "changes-requested",
        "date": "2026-11-22",
        "at": "2026-11-22T03:21:12Z",
        "findings": {
          "documentation": 0,
          "test": 3,
          "code": 1
        },
        "severity": {
          "minor": 3,
          "other": 1
        },
        "route": "on-the-spot",
        "report": "build/reports/tests/index.html",
        "reportCopy": "39-client-origin-screening/ca070e8d/tests/index.html",
        "note": "The fix is correct, and its regression tests fail before it for the right reasons. Three minor items are fixed on the spot: component cases for an off-origin reserved tag, a malformed `0x81`, a malformed `0x90` and an empty datagram, and a wait on the drop line in place of a fixed sleep in the older off-origin `0xA0` test (Off-origin receive tests); and a §9 row in the protocol document for clients before this release (Client receive-loop origin screening). The fourth item, component tests on real loopback sockets, is the module's standing pattern: changing it needs a socket-free receive seam, so it is not changed here."
      }
    ],
    "qaMax": 3,
    "waitingOn": null,
    "resume": null,
    "blockers": {
      "waitsOn": [],
      "blocks": []
    },
    "landing": null,
    "document": {
      "tasks": [
        {
          "id": "documentation-review",
          "name": "Documentation review",
          "state": "done",
          "started": "2026-11-22T03:31:34Z",
          "finished": "2026-11-22T03:39:05Z",
          "report": "The documentation check found older KDoc in the client that still describes delivery before the origin screen (start, startBytes, handshake, the reliable engine), wording in the new comment that overstates what an address check guarantees, and three statements in the protocol and architecture documents to narrow; no API-reference finding. They are being applied."
        },
        {
          "id": "reconcile-documentation",
          "name": "Reconcile documentation",
          "state": "done",
          "started": "2026-11-22T03:39:05Z",
          "finished": "2026-11-22T03:44:50Z",
          "report": "The client's KDoc and the protocol and architecture documents now describe the origin screen as built, with the protocol document's line references recomputed; the API reference is being generated."
        },
        {
          "id": "dokka-check",
          "name": "API reference check",
          "state": "done",
          "started": "2026-11-22T03:44:50Z",
          "finished": "2026-11-22T03:45:32Z",
          "report": "The API reference generates with no warning; the plan documents are being brought in line with the build."
        },
        {
          "id": "reconcile-plan",
          "name": "Reconcile plan",
          "state": "done",
          "started": "2026-11-22T03:45:32Z",
          "finished": "2026-11-22T04:03:13Z",
          "report": "The as-built record is final, with seven deviations; the earlier reliable-channel and channel-byte plans record the gap as closed, the unscreened handshake reply as follow-up #110, and the log-capture race (#111) and real-socket client tests as known gaps. The README, site and CHANGELOG are being updated for 2.0.1a."
        },
        {
          "id": "readme-changelog",
          "name": "README and CHANGELOG",
          "state": "done",
          "started": "2026-11-22T04:03:13Z",
          "finished": "2026-11-22T04:04:30Z",
          "report": "The CHANGELOG has its 2.0.1a entry, and the README and project site install lines name webtools-udp 2.0.1a with a note on how Gradle orders it; the state boards are being built."
        },
        {
          "id": "state-boards",
          "name": "State boards",
          "state": "done",
          "started": "2026-11-22T04:04:30Z",
          "finished": "2026-11-22T04:22:03Z"
        },
        {
          "id": "document-landing",
          "name": "Document commit",
          "state": "done",
          "started": "2026-11-22T04:22:03Z",
          "finished": "2026-11-22T04:31:51Z"
        }
      ]
    }
  },
  "milestones": [
    {
      "column": "gap",
      "label": "Gap written from the issue",
      "date": "2026-11-22",
      "ref": "#39",
      "note": "the Rows and their Gap written from the issue and the code at the baseline; this run's line has no research step"
    },
    {
      "column": "considered",
      "label": "Options weighed",
      "date": "2026-11-22",
      "ref": "plan.md",
      "note": "§1 scopes the handshake's reply out, §3 weighs one screen against a check per branch, §4 one new test class against additions to four"
    },
    {
      "column": "verdict",
      "label": "Plan accepted",
      "date": "2026-11-22",
      "ref": "plan.md",
      "note": "one source check ahead of the listener's tag switch, with per-branch regression tests written to fail first"
    }
  ],
  "trail": [
    {
      "date": "2026-11-22",
      "column": "gap",
      "label": "Sized; the plan starts"
    },
    {
      "date": "2026-11-22",
      "column": "verdict",
      "label": "Plan accepted",
      "ref": "plan.md"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Branch `fix/issue-39-client-datagram-origin-check` cut; implementation starts at s1"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "s1 done; s2 current: Origin screen in the client listener"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "s2 done; s3 current: Protocol and architecture documents"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "s3 done; s4 current: Module test sets and API reference"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Implementation done (4 stages); Quality Review next"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Quality Review 1: running"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Quality Review 1: changes requested, 4 findings (3 minor, 1 other; 3 test, 1 code)"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Correction: 3 sub-steps done; every sub-step done"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Document: 0 of 7 tasks done"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Document: Documentation review current"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Document: Reconcile documentation current"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Document: API reference check current"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Document: Reconcile plan current"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Document: README and CHANGELOG current"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Document: State boards current"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Document: Document commit current"
    },
    {
      "date": "2026-11-22",
      "column": "built",
      "label": "Document: every task done"
    }
  ],
  "rows": [
    {
      "id": "client-receive-origin",
      "group": "Code",
      "name": "Client receive-loop origin screening",
      "system": "webtools-udp",
      "where": "main/MultiConnectionUDPClient.kt",
      "gap": {
        "kind": "missing",
        "summary": "Only the client's reliable receive branch drops a datagram whose origin is not `serverEndpoint`. The keepalive, `PROBE_PING`, `PROBE_PONG` and `0x90` branches check no origin: a `PROBE_PING` from any sender is answered with a `PROBE_PONG` to the server, a `PROBE_PONG` completes a probe in the link-quality tracker, a `0x90` reaches the unreliable handler, and a keepalive is logged as from the server. Anyone who can send UDP to the client's bound port can inject that traffic.",
        "detail": "The client's socket is unconnected, so every datagram sent to its port reaches the receive loop, which captures each one's origin before dispatching on byte 0. The `0x90` branch's channel check carries a comment that the branch has no origin screen, and the reliable-channel plan's finalisation records the client's screening as covering the reliable branch only.",
        "evidence": [
          "`main/MultiConnectionUDPClient.kt:534-568` — the keepalive, `PROBE_PING`, `PROBE_PONG` and `0x90` branches, none checking the origin",
          "`main/MultiConnectionUDPClient.kt:579` — `origin != serverEndpoint`, on the reliable branch only",
          "`main/MultiConnectionUDPClient.kt:556` — the `0x90` branch's comment: no origin screen (#39)",
          "`docs/plans/14-reliable-ordered-channel/architecture-finalisation.md:1234` — client origin screening on the reliable branch only"
        ],
        "source": "#39"
      },
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "text": "The client's listener checked every inbound datagram's source against `serverEndpoint` once, before reading the tag, so one check covered the keepalive, `PROBE_PING`, `PROBE_PONG`, `0x90`, reliable and unknown-tag branches; the module's gating, component, integration, e2e and nonfunctional tests all passed with it in place.",
            "state": "as-planned"
          },
          {
            "text": "A datagram from any other source was dropped with one DEBUG line naming both addresses, with nothing delivered, answered, completed or created for it; the server's datagrams were handled as before, and the class KDoc gained a Datagram origin section stating the contract.",
            "state": "as-planned"
          },
          {
            "text": "The reliable branch's own origin check and the comment above it were removed; its channel-byte check was left unchanged.",
            "state": "as-planned"
          },
          {
            "text": "The reply the handshake waits for was left unscreened; the KDoc, the wire-protocol document (as of `2.0.1a`, with its line references recomputed) and the architecture document's client differences state it as the one read not checked, and the protocol document's versioning table (§9) also records that a client before `2.0.1a` screens the source on the `0xA0`/`0xA1` branch alone.",
            "state": "amended",
            "ref": "final-implementation.md#d7-the-protocol-documents-versioning-table-gains-a-row",
            "excerpt": {
              "ref": "final-implementation.md#d7-the-protocol-documents-versioning-table-gains-a-row",
              "heading": "D7. The protocol document's versioning table gains a row",
              "paragraph": "*Plan: section 3, the `docs/webtools-udp-protocol.md` list, which has no §9 edit.* §9 gains a row for `2.0.0` to `2.0.1` clients: only the `0xA0`/`0xA1` branch screens the source; a `0x81` from any source is answered, an `0x82` completes a probe, a `0x90` is delivered, and an unframed datagram draws a WARN. The table is where the document records how an earlier release behaves differently.",
              "hash": "1f3722c0a8ae"
            }
          }
        ],
        "changes": [
          {
            "what": "The protocol document's versioning table (§9) gained a row: a client before `2.0.1a` screens the source on the `0xA0`/`0xA1` branch alone.",
            "why": "The plan listed no §9 edit, but that table is where the document records how an earlier release behaves differently (Quality Review 1, fixed on the spot).",
            "when": "Quality Review 1 · on the spot",
            "ref": "f6d9630"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "statements": [
          {
            "text": "The client's listener checks every inbound datagram's source, address and port, against `serverEndpoint` once, before it reads the tag, so the keepalive, `PROBE_PING`, `PROBE_PONG`, `0x90`, reliable and unknown-tag branches are all covered by one check.",
            "ref": "plan.md#3-fix",
            "excerpt": {
              "ref": "plan.md#3-fix",
              "heading": "3. Fix",
              "paragraph": "",
              "hash": "48ce19b8f3c7"
            }
          },
          {
            "text": "A datagram from any other source is dropped with one DEBUG line naming both addresses, and nothing is delivered, answered, completed or created for it; a datagram from `serverEndpoint` is handled exactly as before.",
            "ref": "plan.md#1-requirements",
            "excerpt": {
              "ref": "plan.md#1-requirements",
              "heading": "1. Requirements",
              "paragraph": "`MultiConnectionUDPClient`'s listener (`receiveLoop`) must:",
              "hash": "79c70614e480"
            }
          },
          {
            "text": "The reliable branch's own origin arm and the comment above it are removed, because the check ahead of the switch makes them unreachable; its channel-byte check is unchanged.",
            "ref": "plan.md#3-fix",
            "excerpt": {
              "ref": "plan.md#3-fix",
              "heading": "3. Fix",
              "paragraph": "",
              "hash": "48ce19b8f3c7"
            }
          },
          {
            "text": "The reply the handshake waits for stays unscreened and is left to a separate change; the protocol and architecture documents state it as it is.",
            "ref": "plan.md#1-requirements",
            "excerpt": {
              "ref": "plan.md#1-requirements",
              "heading": "1. Requirements",
              "paragraph": "`MultiConnectionUDPClient`'s listener (`receiveLoop`) must:",
              "hash": "79c70614e480"
            }
          }
        ],
        "defense": "A bug fix inside one class of one system, `webtools-udp`'s `MultiConnectionUDPClient`. One check ahead of the tag switch covers every branch by construction, an unknown tag and any tag added later included, using the comparison the reliable arm already made (`InetSocketAddress` equality, address and port). An off-origin datagram is an expected input, answered by a drop, not an error; no signature, visibility, field, persisted state or wire format changes.",
        "steps": [
          {
            "id": "3.1",
            "what": "Screen first: in `receiveLoop`'s `onSuccess`, before the `when` on the tag, return early with one DEBUG line naming both addresses when `origin != serverEndpoint`.",
            "files": [
              "main/MultiConnectionUDPClient.kt"
            ],
            "check": "the new gating and component tests of §4 fail on the unfixed code and pass after it",
            "link": "plan.md#3-fix"
          },
          {
            "id": "3.2",
            "what": "Remove the reliable arm's own origin check and the comment above `val channel`; the `when` keeps its channel arm and its `else`.",
            "files": [
              "main/MultiConnectionUDPClient.kt"
            ],
            "check": "`MultiConnectionUDPClientReliableTest` passes with the shared drop message",
            "link": "plan.md#3-fix"
          },
          {
            "id": "3.3",
            "what": "Re-aim the inline comments and the KDoc: the listener sentence, the boundary notes, `receiveLoop`'s KDoc, and a new Datagram origin paragraph stating the contract.",
            "files": [
              "main/MultiConnectionUDPClient.kt"
            ],
            "check": "`./gradlew.bat dokkaGeneratePublicationHtml`: every KDoc link stays valid",
            "link": "plan.md#3-fix"
          },
          {
            "id": "3.4",
            "what": "Record the screening in the protocol document (§2, §5, §8) and the architecture document's §6, recompute every `MultiConnectionUDPClient.kt` line reference against the final file, and add the `2.0.1a` Fixed entry to the CHANGELOG.",
            "files": [
              "docs/webtools-udp-protocol.md",
              "docs/webtools-udp-architecture.md",
              "CHANGELOG.md"
            ],
            "check": "each line reference found with `grep -n` on the code it names, never by arithmetic",
            "link": "plan.md#docswebtools-udp-protocolmd-the-as-built-notes-about-this-gap"
          }
        ],
        "tests": [
          "gating — `ClientOriginScreenGatingTest` (new): an off-origin datagram of every tag in one pass, then the server's, which alone is acted on",
          "component — `MultiConnectionUDPClientOriginScreenTest` (new): one test per branch, the stranger's datagram dropped and the server's still handled",
          "integration, e2e and nonfunctional — the module's other sets that drive the listener, run after the fix"
        ],
        "risks": [
          "A server whose replies leave from another address or port than the client sent to (a multi-homed wildcard bind, a NAT rebind mid-session) now loses every inbound datagram, as it already lost its reliable ones; the handshake still succeeds, and the DEBUG line naming both addresses is how it shows",
          "A flood of off-origin datagrams logs one DEBUG line each, parameterised and without payload, off at the usual INFO level",
          "Nothing a consumer can reach changes: `receiveLoop` is private, and only the KDoc and the behaviour for a stranger's datagram do"
        ],
        "source": "plan.md §1, §2, §3 and §5",
        "link": "plan.md#3-fix"
      },
      "options": [
        {
          "id": "single-screen",
          "name": "One screen ahead of the tag switch",
          "summary": "One `origin != serverEndpoint` check with an early return, placed before the `when` on the tag.",
          "wins": "Covers every branch by construction, an unknown tag and any tag added later included.",
          "verdict": "accepted",
          "link": "plan.md#3-fix",
          "source": "plan.md §3"
        },
        {
          "id": "per-branch",
          "name": "A check in each branch's arm",
          "why": "It repeats the check in five arms, and nothing makes the next tag added to the switch carry one. The channel-byte checks are repeated because each reads its own tag's bytes; the origin check reads none.",
          "verdict": "declined",
          "link": "plan.md#3-fix",
          "summary": "An `origin != serverEndpoint` arm in each branch of the switch, as the reliable arm has today.",
          "source": "plan.md §3"
        },
        {
          "id": "screen-handshake-reply",
          "name": "Also screen the reply the handshake waits for",
          "why": "The handshake is one blocking read, not a branch of the listener. Screening it needs a drop-and-keep-waiting loop under one deadline, and a server whose reply leaves from another address would then fail to connect at all instead of only losing inbound traffic. It is its own change.",
          "verdict": "deferred",
          "link": "plan.md#1-requirements",
          "summary": "Turn `handshake()`'s one blocking read into a drop-and-keep-waiting loop under the one deadline.",
          "source": "plan.md §1"
        }
      ]
    },
    {
      "id": "off-origin-tests",
      "group": "Tests",
      "name": "Off-origin receive tests",
      "system": "webtools-udp",
      "where": "test/component/.../MultiConnectionUDPClientFramingTest.kt",
      "gap": {
        "kind": "missing",
        "summary": "No test shows an off-origin datagram dropped on the client's keepalive, probe or `0x90` branches. The reliable branch has its off-origin tests; for `0x90`, one component test pins the unscreened behaviour instead, asserting that a frame from a foreign socket is handled with a WARN naming that socket, and says the fix will change what it asserts. The keepalive and probe branches have no off-origin test at all.",
        "evidence": [
          "`test/component/.../MultiConnectionUDPClientFramingTest.kt:157` — the test pinning the `0x90` branch's no-origin-screen behaviour",
          "`test/component/.../MultiConnectionUDPClientReliableTest.kt:175`, `:284` — the reliable branch's off-origin tests"
        ],
        "source": "#39"
      },
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "changes": [
          {
            "what": "The new tests assert that the listener logged its drops: the ping, pong, `0x90`, `0xA1` and unframed tests of `MultiConnectionUDPClientOriginScreenTest` each assert, after their own negative assertion, exactly one `Datagram from unexpected origin` DEBUG line, and `ClientOriginScreenGatingTest` asserts the count of its drops after its own assertions; the plan had them poll for that count without asserting it, the keepalive test aside.",
            "why": "Without it, a run whose listener never handled the foreign datagram would pass the negative assertions vacuously, and the `0xA1` test would not fail against the unfixed code as the plan's table says; asserted after each test's own negative assertion, every other test still fails first on the assertion the table names.",
            "when": "s1 · Regression tests, failing first",
            "ref": "f6d9630",
            "link": "plan.md#4-regression-tests"
          },
          {
            "what": "Four more off-origin component tests (a reserved-tag `0xA2`, a malformed `0x81`, a malformed one-byte `0x90`, an empty datagram), each followed by the server's datagram drawing its usual line, and `0xA2` as an eighth foreign datagram in the gating test.",
            "why": "The plan listed six component cases and seven gating datagrams; these cover the listener switch's remaining arms, the reserved-tag and malformed-frame lines included, that the origin screen now precedes (Quality Review 1, fixed on the spot).",
            "when": "Quality Review 1 · on the spot",
            "ref": "f6d9630",
            "link": "plan.md#4-regression-tests"
          },
          {
            "what": "The reliable class's older foreign-`0xA0` test waits on the `Datagram from unexpected origin` line and asserts exactly one, ahead of its negative assertions, in place of a fixed 300 ms sleep.",
            "why": "The plan left the test as it was; with the sleep, its negative assertions did not prove the listener had handled the datagram (Quality Review 1, fixed on the spot).",
            "when": "Quality Review 1 · on the spot",
            "ref": "f6d9630",
            "link": "plan.md#4-regression-tests"
          }
        ],
        "statements": [
          {
            "text": "`MultiConnectionUDPClientOriginScreenTest` was written with one test per branch (keepalive, `PROBE_PING`, `PROBE_PONG`, `0x90`, `0xA1`, unframed), each failing first against the unfixed listener and passing after the fix, and four more for the switch's remaining arms: a reserved tag (`0xA2`), a malformed `0x81`, a malformed `0x90` and an empty datagram, each dropped with no WARN while the server's datagram still draws its usual line; ten tests in all, each also asserting, after its own negative assertion, exactly one drop line logged.",
            "state": "amended",
            "ref": "final-implementation.md#2-deviations",
            "excerpt": {
              "ref": "final-implementation.md#2-deviations",
              "heading": "2. Deviations",
              "paragraph": "Each departs from the section of `plan.md` it names. No research or parent architecture stands behind these sections, so the plan is the only basis, except where a deviation names another.",
              "hash": "e45a4e12782d"
            }
          },
          {
            "text": "`ClientOriginScreenGatingTest` was written to send an off-origin datagram of every tag, `0xA2` included (eight in all), then the server's, and to assert that only the server's is acted on, after waiting for the eight drop lines; it failed first and passes after the fix.",
            "state": "amended",
            "ref": "final-implementation.md#2-deviations",
            "excerpt": {
              "ref": "final-implementation.md#2-deviations",
              "heading": "2. Deviations",
              "paragraph": "Each departs from the section of `plan.md` it names. No research or parent architecture stands behind these sections, so the plan is the only basis, except where a deviation names another.",
              "hash": "e45a4e12782d"
            }
          },
          {
            "text": "The `0x90` test in `MultiConnectionUDPClientFramingTest` was rewritten in place to assert the DEBUG drop line, and the off-origin test in `MultiConnectionUDPClientReliableTest` moved to the shared drop wording; the reliable class's older foreign-`0xA0` test also waits on the drop line and asserts exactly one, in place of its fixed 300 ms sleep. Both classes pass (7 and 14 tests).",
            "state": "amended",
            "ref": "final-implementation.md#d3-the-existing-foreign-0xa0-reliable-test-waits-on-the-drop-line",
            "excerpt": {
              "ref": "final-implementation.md#d3-the-existing-foreign-0xa0-reliable-test-waits-on-the-drop-line",
              "heading": "D3. The existing foreign-`0xA0` reliable test waits on the drop line",
              "paragraph": "*Plan: section 4, \"The existing `an 0xA0 from a foreign origin mints no engine and delivers nothing` ... stays as it is\".* In `MultiConnectionUDPClientReliableTest`, that test replaces its fixed 300 ms sleep with a DEBUG capture that polls for the `Datagram from unexpected origin` line and asserts exactly one, ahead of its negative assertions. With the sleep, its negative assertions did not prove that the listener had handled the datagram.",
              "hash": "20d428275d65"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "statements": [
          {
            "text": "A new component test class drives the real client over loopback sockets and shows, branch by branch (keepalive, `PROBE_PING`, `PROBE_PONG`, `0x90`, `0xA1`, unframed), that an off-origin datagram is dropped while the server's datagram of the same kind is still handled.",
            "ref": "plan.md#4-regression-tests",
            "excerpt": {
              "ref": "plan.md#4-regression-tests",
              "heading": "4. Regression tests",
              "paragraph": "Write the tests first and run them against the unfixed code; each must fail on the assertion named below. No other module depends on `webtools-udp`, so no other module's suite runs.",
              "hash": "33a5603b516f"
            }
          },
          {
            "text": "One gating test sends an off-origin datagram of every tag in one pass, then a server datagram, and asserts that only the server's is acted on.",
            "ref": "plan.md#4-regression-tests",
            "excerpt": {
              "ref": "plan.md#4-regression-tests",
              "heading": "4. Regression tests",
              "paragraph": "Write the tests first and run them against the unfixed code; each must fail on the assertion named below. No other module depends on `webtools-udp`, so no other module's suite runs.",
              "hash": "33a5603b516f"
            }
          },
          {
            "text": "The component test that pinned the unscreened `0x90` behaviour is rewritten in place to assert the DEBUG origin drop, and the reliable branch's off-origin test follows the one shared message.",
            "ref": "plan.md#4-regression-tests",
            "excerpt": {
              "ref": "plan.md#4-regression-tests",
              "heading": "4. Regression tests",
              "paragraph": "Write the tests first and run them against the unfixed code; each must fail on the assertion named below. No other module depends on `webtools-udp`, so no other module's suite runs.",
              "hash": "33a5603b516f"
            }
          }
        ],
        "defense": "The tests are written first and run against the unfixed code, each failing on a named assertion. They wait on the listener's own origin-drop lines, not on sleeps, so each negative assertion runs after the listener has provably handled the stranger's datagram. One new class holds every branch's off-origin case as one list, and the existing client classes keep their own subjects.",
        "steps": [
          {
            "id": "4.1",
            "what": "Write the gating smoke test: seven off-origin datagrams, one of every tag, then one server `0x90`, which alone is acted on.",
            "files": [
              "test/gating/webtools/udp/ClientOriginScreenGatingTest.kt"
            ],
            "check": "fails today on the unreliable handler holding `[1]` before `[9]`; passes after the fix",
            "link": "plan.md#4-regression-tests"
          },
          {
            "id": "4.2",
            "what": "Write `MultiConnectionUDPClientOriginScreenTest`, one test per branch (keepalive, `PROBE_PING`, `PROBE_PONG`, `0x90`, `0xA1`, unframed).",
            "files": [
              "test/component/webtools/udp/MultiConnectionUDPClientOriginScreenTest.kt"
            ],
            "check": "each fails today on the assertion §4's table names; all pass after the fix",
            "link": "plan.md#4-regression-tests"
          },
          {
            "id": "4.3",
            "what": "Flip the channel-`0x01` `0x90` test in the framing class to the DEBUG origin drop, and move the reliable class's message fragment to the shared one.",
            "files": [
              "test/component/webtools/udp/MultiConnectionUDPClientFramingTest.kt",
              "test/component/webtools/udp/MultiConnectionUDPClientReliableTest.kt"
            ],
            "check": "both classes pass after the fix",
            "link": "plan.md#4-regression-tests"
          }
        ],
        "tests": [
          "gating — `ClientOriginScreenGatingTest` (new)",
          "component — `MultiConnectionUDPClientOriginScreenTest` (new); `MultiConnectionUDPClientFramingTest` and `MultiConnectionUDPClientReliableTest` (edited)"
        ],
        "risks": [
          "Timing: the tests wait on the listener's log lines, polling to a 2 s deadline, and read the events list while the listener appends, as the sibling tests do"
        ],
        "source": "plan.md §4 and §5",
        "link": "plan.md#4-regression-tests"
      },
      "options": [
        {
          "id": "new-class-and-gating-smoke",
          "name": "A new per-branch component class and one gating smoke test",
          "summary": "The off-origin cases of every branch read as one list in one class; the gating test is the fast pre-commit check over all of them at once.",
          "verdict": "accepted",
          "link": "plan.md#4-regression-tests",
          "wins": "Each existing client class keeps its own subject: framing, keepalive, probe, reliable.",
          "source": "plan.md §4"
        },
        {
          "id": "extend-existing-classes",
          "name": "Add each branch's test to the existing client component class that matches it",
          "why": "It spreads one behaviour over four classes that each keep their own subject (framing, keepalive, probe, reliable), and leaves no single list of the branches' off-origin cases.",
          "verdict": "declined",
          "link": "plan.md#4-regression-tests",
          "summary": "Each branch's off-origin case goes into the framing, keepalive, probe or reliable class that already tests that branch.",
          "source": "plan.md §4"
        }
      ]
    }
  ],
  "impact": {
    "basis": "plan",
    "summary": "A bug fix with no public API change: the `webtools-udp` client drops a datagram from any source other than its server endpoint on every receive branch, where it screened the reliable branch alone. A server that replies from the address and port the client sent to sees no difference; one whose replies leave from another address loses its inbound keepalive, probe and unreliable datagrams too, as it already lost its reliable ones. It ships as `webtools-udp` `2.0.1a`.",
    "none": null,
    "release": {
      "bump": "fix",
      "version": "webtools-udp 2.0.1a",
      "note": "the lettered bug-fix release after `2.0.1`, set in the landing's own `build:` commit"
    },
    "downstream": [
      {
        "who": "Applications using the client (`MultiConnectionUDPClient`)",
        "kind": "consumer",
        "system": "webtools-udp",
        "effect": "behaviour",
        "summary": "A datagram from a source other than the server endpoint is no longer delivered, answered, counted in `linkQuality()` or logged as from the server: it is dropped with one DEBUG line naming both addresses. Traffic from the server is handled as before.",
        "link": "plan.md#1-requirements"
      },
      {
        "who": "Deployments whose server replies from another address or port",
        "kind": "operator",
        "system": "webtools-udp",
        "effect": "behaviour",
        "summary": "A multi-homed wildcard bind or a NAT rebind mid-session now loses every inbound datagram after a handshake that still succeeds; the DEBUG line names both addresses.",
        "link": "plan.md#5-risks"
      },
      {
        "who": "`io.github.spartanlaboratories:webtools-udp`",
        "kind": "artifact",
        "system": "webtools-udp",
        "effect": "behaviour",
        "summary": "No public API, persisted state or wire-format change; the fix ships as `2.0.1a`.",
        "link": "plan.md#changelogmd"
      }
    ],
    "adaptation": [
      {
        "who": "Applications using the client",
        "system": "webtools-udp",
        "need": "none",
        "summary": "No signature changes, and nothing to adopt.",
        "link": "plan.md#1-requirements"
      },
      {
        "who": "Servers the client talks to",
        "system": "webtools-udp",
        "need": "required",
        "summary": "A server must reply from the address and port the client sent to. Every supported deployment already does, and the reliable channel has depended on it since `2.0.0`; the class KDoc, the protocol document and the CHANGELOG state it.",
        "link": "plan.md#5-risks"
      }
    ],
    "api": [
      {
        "surface": "`MultiConnectionUDPClient` (class KDoc)",
        "system": "webtools-udp",
        "change": "changed",
        "tier": null,
        "summary": "A new Datagram origin paragraph states that the listener accepts a datagram only from the server's address and port, and that the reply the handshake waits for is the one read not checked. No declaration changes.",
        "link": "plan.md#3-fix"
      }
    ],
    "conversion": [],
    "changes": [],
    "source": "plan.md §1, §3 and §5",
    "link": "plan.md#3-fix"
  },
  "tests": {
    "updated": "2026-11-22T03:25:47Z",
    "added": [
      {
        "class": "com.spartanlabs.testing.gating.webtools.udp.ClientOriginScreenGatingTest",
        "module": "webtools-udp",
        "testSet": "gating",
        "path": "webtools-udp/src/test/kotlin/com/spartanlabs/testing/gating/webtools/udp/ClientOriginScreenGatingTest.kt",
        "tests": 1,
        "rows": [
          "off-origin-tests"
        ],
        "stage": "s1",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-22T03:25:47Z"
        }
      },
      {
        "class": "com.spartanlabs.testing.component.webtools.udp.MultiConnectionUDPClientOriginScreenTest",
        "module": "webtools-udp",
        "testSet": "component",
        "path": "webtools-udp/src/test/kotlin/com/spartanlabs/testing/component/webtools/udp/MultiConnectionUDPClientOriginScreenTest.kt",
        "tests": 10,
        "rows": [
          "off-origin-tests"
        ],
        "stage": "s1",
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-22T03:25:47Z"
        }
      }
    ],
    "amended": [
      {
        "class": "MultiConnectionUDPClientFramingTest",
        "module": "webtools-udp",
        "testSet": "component",
        "path": "webtools-udp/src/test/kotlin/com/spartanlabs/testing/component/webtools/udp/MultiConnectionUDPClientFramingTest.kt",
        "tests": 7,
        "rows": [
          "off-origin-tests"
        ],
        "stage": "s1",
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-22T03:25:47Z"
        }
      },
      {
        "class": "com.spartanlabs.testing.component.webtools.udp.MultiConnectionUDPClientReliableTest",
        "module": "webtools-udp",
        "testSet": "component",
        "path": "webtools-udp/src/test/kotlin/com/spartanlabs/testing/component/webtools/udp/MultiConnectionUDPClientReliableTest.kt",
        "tests": 14,
        "rows": [
          "off-origin-tests"
        ],
        "stage": "s1",
        "result": {
          "state": "passed",
          "passed": 14,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-22T03:25:47Z"
        }
      }
    ],
    "relevant": [],
    "other": {
      "byModule": {
        "webtools-udp": {
          "gating": {
            "classes": 20,
            "tests": 84
          },
          "component": {
            "classes": 22,
            "tests": 245
          },
          "integration": {
            "classes": 20,
            "tests": 115
          },
          "e2e": {
            "classes": 11,
            "tests": 34
          },
          "nonfunctional": {
            "classes": 11,
            "tests": 65
          }
        }
      }
    }
  },
  "roadmap": null,
  "followUps": [],
  "documents": [
    {
      "label": "Issue #39",
      "path": "https://github.com/SpartanLaboratories/WebTools/issues/39",
      "note": "the report: the client screens a datagram's origin on its reliable receive branch only"
    },
    {
      "label": "plan.md",
      "path": "plan.md",
      "note": "requirements, root cause, the fix (§3), the regression tests written to fail first (§4), risks"
    },
    {
      "label": "final-implementation.md",
      "path": "final-implementation.md",
      "note": "the as-built record, written as the work lands: the deviations from the plan"
    },
    {
      "label": "webtools-udp-protocol.md",
      "path": "../../webtools-udp-protocol.md",
      "note": "the wire protocol, recording the client's origin screen as of `2.0.1a`"
    },
    {
      "label": "webtools-udp-architecture.md",
      "path": "../../webtools-udp-architecture.md",
      "note": "the architecture, its client differences recording the same"
    }
  ],
  "notes": {
    "corrections": [
      {
        "what": "The sizing was recorded at 2026-11-22 02:18:32 UTC: The run was sized at 02:15:29 by its own clock; the first request for it was refused for its shape and resent, so the event arrived late. It is recorded at 02:15:29 UTC; the run's start and the visits it bounds (plan, sizing) move with it.",
        "when": "2026-11-22",
        "retime": {
          "event": "sized",
          "from": "2026-11-22T02:18:32Z",
          "to": "2026-11-22T02:15:29Z"
        }
      },
      {
        "what": "Four As-built statements cited final-implementation.md#deviations, a section the final record no longer names: its deviations stand under \"2. Deviations\", as D1 to D7. The client-receive-origin Row's fourth statement now cites D7, the versioning-table row it records; the off-origin-tests Row's first two statements cite \"2. Deviations\", as each rests on D1 and D2, and its third cites D3. The off-origin-tests Row's first statement also said that only five of the ten component tests assert one drop line logged; the final record (D1) has all ten assert it, and the statement now says so.",
        "when": "2026-11-22"
      }
    ]
  },
  "links": {
    "workBoard": "../index.html",
    "repoBoard": "../../dashboards/index.html",
    "boards": {
      "webtools-udp": "../../dashboards/webtools-udp/index.html"
    }
  }
}
);
