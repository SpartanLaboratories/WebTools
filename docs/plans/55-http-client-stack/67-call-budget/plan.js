/* plan.js: plan.json as a script, written by plan/scripts/embed.js beside the board for its live view (plan/RULES.md §1). Do not edit: edit plan.json and re-embed. */
window.dashboardLive&&window.dashboardLive("plan",
{
  "schema": "plan-board/1",
  "repo": "SpartanLaboratories/WebTools",
  "issue": 67,
  "slug": "67-call-budget",
  "folder": "docs/plans/55-http-client-stack/67-call-budget",
  "kind": "issue",
  "title": "Call budget",
  "issueTitle": "webtools-http: `CallBudget` port, `WindowedCallBudget`, rate signals, the persistence and observer seams and their fakes",
  "function": "Nothing in `webtools-http` limits its calls yet: #62 built the `BudgetExhausted` failure and `ExhaustedReason` with nothing to return them, and the `WallClock` the budget reads; #63 built the `VirtualClock` its tests run on. OpenDota and Open-Meteo count in fixed wall-clock windows and count refused requests, and a greedy token bucket let 119 calls into one server minute against 60 (#55 architecture §2, §6.5). This run adds the `CallBudget` port, its permits and rate-signal vocabulary, the store and observer seams, `CallBudget.Unlimited` and the `WindowedCallBudget` default in `commonMain`, with two fakes in the fixtures and its tests. Unit 8 of #55: the call budget subsystem of `webtools-http`, additive, no version moved; reading signals from a real response is unit 9's.",
  "status": "built",
  "sized": "2026-11-03T19:38:03Z",
  "parent": {
    "issue": 55,
    "board": "../dashboard.html"
  },
  "fixOf": null,
  "branch": "feat/issue-67-call-budget",
  "baseline": {
    "ref": "master",
    "sha": "8122af7",
    "date": "2026-11-03"
  },
  "asBuilt": {
    "sha": "5f221ce",
    "merge": "5f221ce",
    "pr": 90,
    "date": "2026-11-05",
    "at": "2026-11-05T23:52:26Z",
    "commits": [
      {
        "sha": "1ec2d92",
        "at": "2026-11-03T21:18:07Z",
        "step": "research"
      },
      {
        "sha": "f956731",
        "at": "2026-11-03T21:39:26Z",
        "step": "research"
      },
      {
        "sha": "8f5d057",
        "at": "2026-11-03T21:54:21Z",
        "step": "research"
      },
      {
        "sha": "3091e92",
        "at": "2026-11-04T00:29:50Z",
        "step": "research"
      }
    ]
  },
  "systems": [
    {
      "slug": "webtools-http-budget",
      "name": "call budget",
      "slot": 5,
      "top": "webtools-http",
      "status": "built",
      "board": "docs/dashboards/webtools-http-budget/index.html"
    }
  ],
  "wip": {
    "step": "landed",
    "since": "2026-11-05T23:52:26Z",
    "updated": "2026-11-05T23:52:26Z",
    "detail": "Landed in PR #90.",
    "steps": [
      "sizing",
      "interview",
      "research",
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
    "current": [],
    "questions": [],
    "research": null,
    "interview": [],
    "plan": null,
    "stages": [
      {
        "id": "S1",
        "name": "commonMain vocabulary, Retry-After parser and seams",
        "state": "done",
        "rows": [
          "vocabulary",
          "signals-reader",
          "hard-stop",
          "observer",
          "component-tests",
          "deterministic-tests",
          "release"
        ]
      },
      {
        "id": "S2",
        "name": "CallBudget port, Permit, withPermit and the ledger with its Level 4a tables",
        "state": "done",
        "rows": [
          "port",
          "permit",
          "windowed",
          "lanes",
          "pauses",
          "hard-stop",
          "time",
          "component-tests",
          "deterministic-tests",
          "release"
        ]
      },
      {
        "id": "S3",
        "name": "fixtures: RecordingBudgetObserver and FakeBudgetStore",
        "state": "done",
        "rows": [
          "fixtures",
          "component-tests"
        ]
      },
      {
        "id": "S4",
        "name": "WindowedCallBudget with the Level 1 and Level 2 tests",
        "state": "done",
        "rows": [
          "port",
          "permit",
          "windowed",
          "lanes",
          "pauses",
          "hard-stop",
          "observer",
          "time",
          "close",
          "component-tests",
          "release"
        ]
      },
      {
        "id": "S5",
        "name": "Level 3 and Level 4c suites",
        "state": "done",
        "rows": [
          "integration-tests",
          "nonfunctional-tests"
        ]
      }
    ],
    "remediation": {
      "pass": 2,
      "route": "on-the-spot",
      "steps": [
        {
          "name": "Guard times in the fake store with its case and deviation; add the four coverage cases",
          "state": "done"
        }
      ]
    },
    "open": [
      {
        "text": "The plan's Level 4c re-plan cost row asserts that the queue's planned instants stay in lane order; no seam exposes a waiter's planned instant, so the assertion is not implemented (deviation D4). To settle: an internal test seam, or the assertion dropped as a known gap.",
        "raised": "implementation-stage S5",
        "ref": "final-implementation.md",
        "resolved": "2026-11-05T23:14:44Z"
      },
      {
        "text": "Procedure P3's mutation (the closing re-check at the top of admitLocked removed) is not detected by WindowedCallBudgetCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
        "raised": "implementation-stage S5",
        "ref": "final-implementation.md",
        "resolved": "2026-11-04T04:44:36Z"
      }
    ],
    "event": {
      "name": "landed",
      "at": "2026-11-05T23:52:26Z"
    },
    "history": {
      "sizing": [
        {
          "since": null,
          "ended": "2026-11-03T19:38:03Z",
          "endedBy": "sized",
          "to": "plan"
        }
      ],
      "plan": [
        {
          "since": "2026-11-03T19:38:03Z",
          "ended": "2026-11-03T19:54:16Z",
          "endedBy": "halted",
          "to": "plan",
          "event": {
            "name": "sized",
            "at": "2026-11-03T19:38:03Z"
          },
          "detail": "Unit 8 of the HTTP client stack: the CallBudget port, Permit, withPermit, Lane, RateWindow, RateSignals, WindowReading, RateSignalsReader with Standard, RetryAfter, BudgetStore with InMemoryBudgetStore, the snapshot types, BudgetObserver, BudgetEvent, BudgetUtilisation, CallBudget.Unlimited and WindowedCallBudget, all in commonMain; RecordingBudgetObserver and FakeBudgetStore in the test fixtures. Working tree clean at 8122af7; the run works in the main checkout. Next: the detailed implementation plan from architecture.md sections 6.5, 11 and 13.",
          "plan": {
            "state": "re-pass",
            "from": "parent",
            "since": "2026-11-03T19:38:03Z"
          },
          "held": true
        },
        {
          "since": "2026-11-03T21:13:42Z",
          "ended": "2026-11-03T21:15:02Z",
          "endedBy": "research-planning",
          "to": "research",
          "event": {
            "name": "resumed",
            "at": "2026-11-03T21:13:42Z"
          },
          "detail": "Decided: a failed store load at the restore fails open with a bounded retry, saves held back and an additive merge; maxQueued is a cap per lane; after close() everything but grants and waits keeps working, flush() still writes; the one-time restore runs on whichever of acquire, utilisation() and flush() comes first; BudgetEvent gains a Restored leaf beside the six. The nine working assumptions stand as stated. Research on the ledger's waiting and non-suspending close, the HTTP-date and Retry-After grammar, and the wall-clock time model follows.",
          "plan": {
            "state": "re-pass",
            "from": "parent",
            "since": "2026-11-03T19:38:03Z"
          }
        },
        {
          "since": "2026-11-04T00:27:31Z",
          "ended": "2026-11-04T01:18:42Z",
          "endedBy": "halted",
          "to": "plan",
          "event": {
            "name": "resumed",
            "at": "2026-11-04T00:27:31Z"
          },
          "detail": "Decided: when the wall clock steps back across a window boundary, the current cell gains the sum of the cells above it, capped at the limit, and the higher cells stay (a bounded false refusal is the accepted cost); a restore snapshot stamped beyond maxRestoreSkew keeps its window counts as cells at their own indices and only its stamp and pause are ignored. The research summary's open trade-offs are being closed with these decisions. The plan is drafted next.",
          "plan": {
            "state": "writing",
            "since": "2026-11-03T21:54:23Z"
          },
          "held": true
        },
        {
          "since": "2026-11-04T01:34:03Z",
          "ended": "2026-11-04T01:34:03Z",
          "endedBy": "plan-accepted",
          "to": "branch",
          "event": {
            "name": "resumed",
            "at": "2026-11-04T01:34:03Z"
          },
          "detail": "All five choices stand: the event fields, CostExceedsLimit covering a lane's capacity, the snapshot contents, Retry-After: 0 pausing nothing, and arrival order.",
          "plan": {
            "state": "writing",
            "since": "2026-11-03T21:54:23Z"
          }
        }
      ],
      "research": [
        {
          "since": "2026-11-03T21:15:02Z",
          "ended": "2026-11-03T21:54:23Z",
          "endedBy": "researched",
          "to": "plan",
          "event": {
            "name": "verification-started",
            "at": "2026-11-03T21:39:01Z"
          },
          "detail": "76 load-bearing claims re-opened at their sources: 17 from waits-and-close, 25 from retry-after-parsing, 19 from ledger-time, and 15 from the stack-level call-budget findings this unit relies on.",
          "research": {
            "phase": "complete",
            "drafted": 0,
            "since": {
              "planning": "2026-11-03T21:15:02Z",
              "research": "2026-11-03T21:17:41Z",
              "verification": "2026-11-03T21:39:01Z",
              "complete": "2026-11-03T21:54:23Z"
            }
          },
          "questions": [
            {
              "question": "waits-and-close",
              "rows": [],
              "findings": "research/waits-and-close.md",
              "state": "complete",
              "started": "2026-11-03T21:17:41Z",
              "returned": "2026-11-03T21:35:22Z"
            },
            {
              "question": "retry-after-parsing",
              "rows": [],
              "findings": "research/retry-after-parsing.md",
              "state": "complete",
              "started": "2026-11-03T21:17:41Z",
              "returned": "2026-11-03T21:38:21Z"
            },
            {
              "question": "ledger-time",
              "rows": [],
              "findings": "research/ledger-time.md",
              "state": "complete",
              "started": "2026-11-03T21:17:41Z",
              "returned": "2026-11-03T21:36:32Z"
            }
          ]
        }
      ],
      "branch": [
        {
          "since": "2026-11-04T01:34:03Z",
          "ended": "2026-11-04T01:35:42Z",
          "endedBy": "branch-cut",
          "to": "implementation",
          "event": {
            "name": "plan-accepted",
            "at": "2026-11-04T01:34:03Z"
          },
          "detail": "Full implementation plan for the CallBudget port and WindowedCallBudget: a pure synchronous ledger (cells, slot search, lane floors, the clock-step fold, restore and snapshots) under a concurrency shell of one Mutex, a close latch and a conflated channel per waiter; the two fixtures RecordingBudgetObserver and FakeBudgetStore. Interfaces pinned for the call facades (#68).",
          "branch": "feat/issue-67-call-budget"
        }
      ],
      "implementation": [
        {
          "since": "2026-11-04T01:35:42Z",
          "ended": "2026-11-04T04:00:38Z",
          "endedBy": "qa-pass",
          "to": "qa",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-04T03:30:51Z",
            "note": "S5 reported done; qa next"
          },
          "detail": "Procedure P3 (the closed-flag re-check at the top of admitLocked removed) was not detected by the close-race class in 5 of 5 runs: a parked call's first step returns Closed anyway, so the re-check saves one admission only; recorded for the known gaps. Procedure P4: re-plan of 1,000 waiters per lane averages 14 to 16 ms per round (bounds 2 s and 250 ms) on a 16-thread host. Module check: 1,204 jvm tests, 0 failed, 0 skipped.",
          "open": [
            {
              "text": "The plan's Level 4c re-plan cost row asserts that the queue's planned instants stay in lane order; no seam exposes a waiter's planned instant, so the assertion is not implemented (deviation D4). To settle: an internal test seam, or the assertion dropped as a known gap.",
              "raised": "implementation-stage S5",
              "ref": "final-implementation.md"
            },
            {
              "text": "Procedure P3's mutation (the closing re-check at the top of admitLocked removed) is not detected by WindowedCallBudgetCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
              "raised": "implementation-stage S5",
              "ref": "final-implementation.md"
            }
          ],
          "stages": [
            {
              "id": "S1",
              "name": "commonMain vocabulary, Retry-After parser and seams",
              "state": "done",
              "rows": [
                "vocabulary",
                "signals-reader",
                "hard-stop",
                "observer",
                "component-tests",
                "deterministic-tests",
                "release"
              ]
            },
            {
              "id": "S2",
              "name": "CallBudget port, Permit, withPermit and the ledger with its Level 4a tables",
              "state": "done",
              "rows": [
                "port",
                "permit",
                "windowed",
                "lanes",
                "pauses",
                "hard-stop",
                "time",
                "component-tests",
                "deterministic-tests",
                "release"
              ]
            },
            {
              "id": "S3",
              "name": "fixtures: RecordingBudgetObserver and FakeBudgetStore",
              "state": "done",
              "rows": [
                "fixtures",
                "component-tests"
              ]
            },
            {
              "id": "S4",
              "name": "WindowedCallBudget with the Level 1 and Level 2 tests",
              "state": "done",
              "rows": [
                "port",
                "permit",
                "windowed",
                "lanes",
                "pauses",
                "hard-stop",
                "observer",
                "time",
                "close",
                "component-tests",
                "release"
              ]
            },
            {
              "id": "S5",
              "name": "Level 3 and Level 4c suites",
              "state": "done",
              "rows": [
                "integration-tests",
                "nonfunctional-tests"
              ]
            }
          ]
        }
      ],
      "qa": [
        {
          "since": "2026-11-04T04:00:38Z",
          "ended": "2026-11-04T04:27:56Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-04T04:00:38Z",
            "note": "pass 1: running"
          },
          "detail": "The lane-order assertion of the re-plan cost test stays out: settled as deviation D4 now and as a known gap at the reconcile, so the non-functional-tests row's amended statement is rewritten then.",
          "open": [
            {
              "text": "The plan's Level 4c re-plan cost row asserts that the queue's planned instants stay in lane order; no seam exposes a waiter's planned instant, so the assertion is not implemented (deviation D4). To settle: an internal test seam, or the assertion dropped as a known gap.",
              "raised": "implementation-stage S5",
              "ref": "final-implementation.md"
            },
            {
              "text": "Procedure P3's mutation (the closing re-check at the top of admitLocked removed) is not detected by WindowedCallBudgetCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
              "raised": "implementation-stage S5",
              "ref": "final-implementation.md"
            }
          ],
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-04",
              "at": "2026-11-04T04:27:56Z",
              "findings": {
                "test": 2,
                "code": 1
              },
              "severity": {
                "minor": 2,
                "other": 1
              },
              "route": "remediation",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/67-call-budget/2f43cfc3/tests/index.html"
            }
          ]
        },
        {
          "since": "2026-11-04T04:44:36Z",
          "ended": "2026-11-04T05:16:24Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-04T04:44:36Z",
            "note": "pass 2: running"
          },
          "detail": "QA pass 2 running.",
          "open": [
            {
              "text": "The plan's Level 4c re-plan cost row asserts that the queue's planned instants stay in lane order; no seam exposes a waiter's planned instant, so the assertion is not implemented (deviation D4). To settle: an internal test seam, or the assertion dropped as a known gap.",
              "raised": "implementation-stage S5",
              "ref": "final-implementation.md"
            }
          ],
          "qa": [
            {
              "pass": 2,
              "verdict": "changes-requested",
              "date": "2026-11-04",
              "at": "2026-11-04T05:16:24Z",
              "findings": {
                "test": 4,
                "code": 1
              },
              "severity": {
                "minor": 5,
                "other": 0
              },
              "route": "on-the-spot",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/67-call-budget/cefdb082/tests/index.html"
            }
          ]
        }
      ],
      "remediation": [
        {
          "since": "2026-11-04T04:27:56Z",
          "ended": "2026-11-04T04:44:36Z",
          "endedBy": "qa-pass",
          "to": "qa",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-04T04:44:36Z",
            "note": "S5"
          },
          "detail": "The persisted-mark reproduction and the new deterministic row failed before the fix and pass after. P3's mutation is detected deterministically by the Level 2 close test's parked-call case (1 of 7 failed under the mutation), so the Level 4c class's blindness is no longer an open gap; the consequences record takes the outcome at the reconcile. P5 performed: tiers, contract points and never-a-value statements hold; three KDoc wordings flagged as unclear for the documentation review. componentTest 743 and deterministicTest 293 tests, 0 failed, 0 skipped; the ABI dump unchanged.",
          "open": [
            {
              "text": "The plan's Level 4c re-plan cost row asserts that the queue's planned instants stay in lane order; no seam exposes a waiter's planned instant, so the assertion is not implemented (deviation D4). To settle: an internal test seam, or the assertion dropped as a known gap.",
              "raised": "implementation-stage S5",
              "ref": "final-implementation.md"
            }
          ],
          "remediation": {
            "pass": 1,
            "route": "remediation",
            "steps": [
              {
                "name": "Fix the persisted mark after an exact flush with its Level 2 reproduction, add the two Level 2 edge cases, confirm P3 against the close test, record P5",
                "state": "done"
              },
              {
                "name": "qa pass 2",
                "state": "current"
              }
            ]
          },
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-04",
              "at": "2026-11-04T04:27:56Z",
              "findings": {
                "test": 2,
                "code": 1
              },
              "severity": {
                "minor": 2,
                "other": 1
              },
              "route": "remediation",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/67-call-budget/2f43cfc3/tests/index.html",
              "note": "One code finding (other), in the As built row the fix touches, `hard-stop`, whose verdict holds the write-ahead and the snapshots (plan.md R13, §6.7): in `BudgetLedger`'s `exactSnapshot` and `markPersisted`, after a write-ahead grant is released and `flush()` runs, the exact snapshot omits the emptied hard cell while its persisted mark keeps the write-ahead value, so up to `persistAhead - 1` later grants make no save and a crash under-counts the hard window, against the plan's never-under-count rule; the code is the plan's own, so the fix is also a deviation. Two minor test findings, in `component-tests`, each a Level 2 case to add: the write-ahead retry loop is exercised only for its first iteration (no slow successful save crossing a boundary, no three-attempt cap case), and the 1 ms re-plan deadline tolerance has no edge case. Suite: 1,204 passed, 0 failed, 0 skipped. For the documents: the Level 2 close test with free capacity appears to detect P3's mutation deterministically, to be confirmed with one run; P5 has no record yet."
            }
          ]
        },
        {
          "since": "2026-11-04T05:16:24Z",
          "ended": "2026-11-04T05:31:02Z",
          "endedBy": "document",
          "to": "document",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-04T05:31:02Z",
            "note": "1 of 1 remediation sub-steps done"
          },
          "detail": "The two fixtures now have consumer gating cases; the close test covers a waiter whose own write-ahead save spans close() (Closed once the save ends) and pins that a call whose save spans a close is still granted, the plan's letter; a store Error propagates and leaves the budget usable; a retried restore that merges counts re-plans the waiters. Observation for the reconcile: a store Error reaches the caller as a stack-trace-recovery copy of the thrown instance, since the store call runs inside withTimeoutOrNull; the contract 'an Error propagates' holds. gatingTest 32 and componentTest 748 tests, 0 failed, 0 skipped; the ABI dump unchanged.",
          "open": [
            {
              "text": "The plan's Level 4c re-plan cost row asserts that the queue's planned instants stay in lane order; no seam exposes a waiter's planned instant, so the assertion is not implemented (deviation D4). To settle: an internal test seam, or the assertion dropped as a known gap.",
              "raised": "implementation-stage S5",
              "ref": "final-implementation.md"
            }
          ],
          "remediation": {
            "pass": 2,
            "route": "on-the-spot",
            "steps": [
              {
                "name": "Guard times in the fake store with its case and deviation; add the four coverage cases",
                "state": "done"
              }
            ]
          },
          "qa": [
            {
              "pass": 2,
              "verdict": "changes-requested",
              "date": "2026-11-04",
              "at": "2026-11-04T05:16:24Z",
              "findings": {
                "test": 4,
                "code": 1
              },
              "severity": {
                "minor": 5,
                "other": 0
              },
              "route": "on-the-spot",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/67-call-budget/cefdb082/tests/index.html",
              "note": "Five minor findings and no other; the fixes are being made in place. Two in the As built row `fixtures`: the code finding, that `FakeBudgetStore`'s `failLoads` and `failSaves` with `times = 0` deliver one failure instead of none, because the count only decides when to disarm and the armed failure is still delivered; a guard settles it, with a case of its own, and as the code is the plan's listing (plan.md §7.17) the fix is also a deviation. And a test finding: `FixturesConsumerGatingTest` names neither `RecordingBudgetObserver` nor `FakeBudgetStore`. The other three are test findings in `component-tests`, each a case to add, resting on another row's verdict: for `close`, a waiter whose own write-ahead save spans `close()`; for `hard-stop`, a store `Error` propagating (plan.md §6.7) and a retried restore whose merge of counts re-plans queued waiters. Looked at with no finding: the D7 fix, a single added condition traced through every snapshot reader, and the restore band, which is plan.md §6.7's contract. Suite: 1,208 passed, 0 failed, 0 skipped."
            }
          ]
        }
      ],
      "document": [
        {
          "since": "2026-11-04T05:31:02Z",
          "ended": "2026-11-04T05:36:00Z",
          "endedBy": "run-restarted",
          "to": "document",
          "event": {
            "name": "document",
            "at": "2026-11-05T23:14:44Z",
            "note": "5 of 7 tasks done"
          },
          "detail": "As-built record final in the form of the earlier units: 81 new files, the ABI dump from 557 to 892 lines, nine deviations, all six acceptance items met, final run of record the module's check at 327164f (1,215 tests in 227 classes, 0 failed, 0 skipped). consequences.md: built at 327164f; the sibling contracts for the call facades as built; follow-ups include rethrowing a store Error's original instance and saving a hard window when a server reading or a clock step raises its count; known gaps include the lane-order assertion left out (D4), the Level 4c close-race class not detecting P3's mutation while the Level 2 close class does, a call whose store call spans close() still being granted, a grant after three outlasting saves proceeding unsaved, a raised count being saved only with the next grant or flush(), and written-ahead units given back staying in the stored count until flush(); every assumption's outcome is recorded (none failed). The parent consequences.md names units 1 to 8 as built and reads unit 8's open point as settled; architecture.md carries seven corrections dated 2026-11-05 at section 6.5, the section 7.4 row and section 13. research/README.md gained three did-not-hold notes.",
          "document": {
            "tasks": [
              {
                "id": "documentation-review",
                "name": "Documentation review",
                "state": "done"
              },
              {
                "id": "reconcile-documentation",
                "name": "Reconcile documentation",
                "state": "done"
              },
              {
                "id": "dokka-check",
                "name": "Dokka check (not applicable: WebTools does not apply Dokka)",
                "state": "done"
              },
              {
                "id": "reconcile-plan",
                "name": "Reconcile plan",
                "state": "done"
              },
              {
                "id": "readme-changelog",
                "name": "README and CHANGELOG",
                "state": "done"
              },
              {
                "id": "state-boards",
                "name": "State boards",
                "state": "current"
              },
              {
                "id": "document-landing",
                "name": "Document-landing commit",
                "state": "todo"
              }
            ]
          },
          "outage": "session ended; a new session took the run up"
        },
        {
          "since": "2026-11-05T22:43:07Z",
          "ended": "2026-11-05T23:50:52Z",
          "endedBy": "landing",
          "to": "landing",
          "event": {
            "name": "document",
            "at": "2026-11-05T23:46:22Z",
            "note": "7 of 7 tasks done"
          },
          "detail": "Document: 7 of 7 tasks done; every task done.",
          "document": {
            "tasks": [
              {
                "id": "documentation-review",
                "name": "Documentation review",
                "state": "done"
              },
              {
                "id": "reconcile-documentation",
                "name": "Reconcile documentation",
                "state": "done"
              },
              {
                "id": "dokka-check",
                "name": "Dokka check (not applicable: WebTools does not apply Dokka)",
                "state": "done"
              },
              {
                "id": "reconcile-plan",
                "name": "Reconcile plan",
                "state": "done"
              },
              {
                "id": "readme-changelog",
                "name": "README and CHANGELOG",
                "state": "done"
              },
              {
                "id": "state-boards",
                "name": "State boards",
                "state": "done"
              },
              {
                "id": "document-landing",
                "name": "Document-landing commit",
                "state": "done"
              }
            ]
          }
        }
      ],
      "landing": [
        {
          "since": "2026-11-05T23:50:52Z",
          "ended": "2026-11-05T23:52:10Z",
          "endedBy": "merge",
          "to": "merge",
          "event": {
            "name": "landing",
            "at": "2026-11-05T23:50:52Z",
            "note": "land accepted"
          },
          "detail": "Landing: the land accepted; the branch carries the code, the version bump, the reconciled documents and the state boards."
        }
      ],
      "merge": [
        {
          "since": "2026-11-05T23:52:10Z",
          "ended": "2026-11-05T23:52:26Z",
          "endedBy": "landed",
          "to": "landed",
          "event": {
            "name": "landed",
            "at": "2026-11-05T23:52:26Z"
          },
          "detail": "Landed in PR #90."
        }
      ]
    },
    "next": null,
    "qa": [
      {
        "pass": 1,
        "verdict": "changes-requested",
        "date": "2026-11-04",
        "at": "2026-11-04T04:27:56Z",
        "findings": {
          "test": 2,
          "code": 1
        },
        "severity": {
          "minor": 2,
          "other": 1
        },
        "route": "remediation",
        "report": "build/reports/tests/index.html",
        "reportCopy": "55-http-client-stack/67-call-budget/2f43cfc3/tests/index.html",
        "note": "One code finding (other), in the As built row the fix touches, `hard-stop`, whose verdict holds the write-ahead and the snapshots (plan.md R13, §6.7): in `BudgetLedger`'s `exactSnapshot` and `markPersisted`, after a write-ahead grant is released and `flush()` runs, the exact snapshot omits the emptied hard cell while its persisted mark keeps the write-ahead value, so up to `persistAhead - 1` later grants make no save and a crash under-counts the hard window, against the plan's never-under-count rule; the code is the plan's own, so the fix is also a deviation. Two minor test findings, in `component-tests`, each a Level 2 case to add: the write-ahead retry loop is exercised only for its first iteration (no slow successful save crossing a boundary, no three-attempt cap case), and the 1 ms re-plan deadline tolerance has no edge case. Suite: 1,204 passed, 0 failed, 0 skipped. For the documents: the Level 2 close test with free capacity appears to detect P3's mutation deterministically, to be confirmed with one run; P5 has no record yet."
      },
      {
        "pass": 2,
        "verdict": "changes-requested",
        "date": "2026-11-04",
        "at": "2026-11-04T05:16:24Z",
        "findings": {
          "test": 4,
          "code": 1
        },
        "severity": {
          "minor": 5,
          "other": 0
        },
        "route": "on-the-spot",
        "report": "build/reports/tests/index.html",
        "reportCopy": "55-http-client-stack/67-call-budget/cefdb082/tests/index.html",
        "note": "Five minor findings and no other; the fixes are being made in place. Two in the As built row `fixtures`: the code finding, that `FakeBudgetStore`'s `failLoads` and `failSaves` with `times = 0` deliver one failure instead of none, because the count only decides when to disarm and the armed failure is still delivered; a guard settles it, with a case of its own, and as the code is the plan's listing (plan.md §7.17) the fix is also a deviation. And a test finding: `FixturesConsumerGatingTest` names neither `RecordingBudgetObserver` nor `FakeBudgetStore`. The other three are test findings in `component-tests`, each a case to add, resting on another row's verdict: for `close`, a waiter whose own write-ahead save spans `close()`; for `hard-stop`, a store `Error` propagating (plan.md §6.7) and a retried restore whose merge of counts re-plans queued waiters. Looked at with no finding: the D7 fix, a single added condition traced through every snapshot reader, and the restore band, which is plan.md §6.7's contract. Suite: 1,208 passed, 0 failed, 0 skipped."
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
    "waits": [
      {
        "step": "plan",
        "since": "2026-11-03T19:54:16Z",
        "ended": "2026-11-03T21:13:42Z",
        "by": "halted"
      },
      {
        "step": "plan",
        "since": "2026-11-03T21:54:23Z",
        "ended": "2026-11-04T00:27:31Z",
        "by": "halted"
      },
      {
        "step": "plan",
        "since": "2026-11-04T01:18:42Z",
        "ended": "2026-11-04T01:34:03Z",
        "by": "halted"
      }
    ],
    "document": {
      "tasks": [
        {
          "id": "documentation-review",
          "name": "Documentation review",
          "state": "done"
        },
        {
          "id": "reconcile-documentation",
          "name": "Reconcile documentation",
          "state": "done"
        },
        {
          "id": "dokka-check",
          "name": "Dokka check (not applicable: WebTools does not apply Dokka)",
          "state": "done"
        },
        {
          "id": "reconcile-plan",
          "name": "Reconcile plan",
          "state": "done"
        },
        {
          "id": "readme-changelog",
          "name": "README and CHANGELOG",
          "state": "done"
        },
        {
          "id": "state-boards",
          "name": "State boards",
          "state": "done"
        },
        {
          "id": "document-landing",
          "name": "Document-landing commit",
          "state": "done"
        }
      ]
    },
    "outages": [
      {
        "step": "document",
        "since": "2026-11-04T05:36:00Z",
        "ended": "2026-11-05T22:43:07Z",
        "reason": "session ended; a new session took the run up"
      }
    ]
  },
  "milestones": [
    {
      "column": "gap",
      "label": "Research verified, in #55's run",
      "date": "2026-10-21",
      "ref": "../research/README.md",
      "note": "#55's questions and findings; question 7 and `call-budget.md` bear on this unit, whose 28 claims were re-opened at source and all hold. Written on this board at sized from the research README, `call-budget.md`, architecture §6.5 and §13 and this unit's row in §11, with what units 3 and 4 as built leave it"
    },
    {
      "column": "gap",
      "label": "Research verified, in this unit's run",
      "date": "2026-11-03",
      "ref": "research/README.md",
      "note": "three questions and three findings files: waiting and the non-suspending close, the HTTP-date and `Retry-After` grammar, and the ledger's time model. 76 load-bearing claims re-opened at source, 61 of these findings and 15 of #55's call budget findings: 75 hold, none is unclear, and the one failure is a wrong file path in a Go citation, whose fact holds at the right path and on which no recommendation rests. The Gap column, written at sized from #55's research and architecture, stands with no correction; these findings add their evidence to 10 rows. It left two choices to the plan, which decided them: a backward clock step across a window boundary copies the sum of the higher cells into the current cell, capped at the limit (the `time` row), and a snapshot stamped further ahead than `maxRestoreSkew` keeps its window counts, only its stamp and pause ignored (the `hard-stop` row). Not established: `select` with `onJoin` on `js`, which the build's `js` compile settles, and the Kotlin date parser, measured through its Java mirror"
    },
    {
      "column": "considered",
      "label": "Architecture signed off, in #55's run",
      "date": "2026-10-21",
      "ref": "../architecture.md",
      "note": "the options #55's architecture weighed for this unit: §4, §6.5, §9 and §10, with the research's findings and considerations behind them; this unit's own plan adds to them at plan acceptance, and settles the one left open (`close`)"
    },
    {
      "column": "considered",
      "label": "The unit's own plan adds to the options",
      "date": "2026-11-04",
      "ref": "plan.md",
      "note": "the options plan.md and this unit's research weigh beside the architecture's. It settles the one left open: `close()` is a completed `Job()` latch, and a closed flag with cancelled continuations is declined. It decides the two choices the research left: a backward clock step copies, and a far-ahead snapshot keeps its counts. Four of the architecture's options are adopted in part, each with its refinement accepted beside it: `close`'s contract, the clock pair, the write-ahead store and the one emission point. The plan also adds two suites #55's decomposition does not name, so the board gains two rows, `integration-tests` and `nonfunctional-tests`, each with its Gap and Considered written here"
    },
    {
      "column": "verdict",
      "label": "Plan accepted",
      "date": "2026-11-04",
      "ref": "plan.md",
      "note": "five implementation stages on `feat/issue-67-call-budget`, S1 to S5, then the README and CHANGELOG and the as-built record, in seven commits. The design is a pure, synchronous ledger under a shell of one `Mutex`, a close latch and a conflated channel per waiter, and makes #55's architecture §6.5 concrete in plan.md §6.11. No version moves"
    },
    {
      "column": "built",
      "label": "Merged",
      "date": "2026-11-05",
      "ref": "PR #90",
      "note": "the research 1ec2d92 to 3091e92; the vocabulary, `Retry-After` parser and seams ff55cb8, the port, `Permit`, `withPermit` and the ledger ec776c7, the two fixtures c0a1c57, `WindowedCallBudget` 64a9a03 and the Level 3 and Level 4c suites 327164f, the code-landing commit; the KDoc and test comments 4012f73, the README and changelog 19c689b, the as-built record and consequences affc5f1 and the boards d10110a and e682443; merged as 5f221ce. No version moved; `webtools-http` 1.0.0 stays unreleased"
    }
  ],
  "trail": [
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Sized; the plan starts"
    },
    {
      "date": "2026-11-03",
      "column": "considered",
      "label": "Gap and Considered filled from #55's research and architecture: 17 rows, the options with the verdicts the architecture gave",
      "ref": "../architecture.md#65-call-budget"
    },
    {
      "date": "2026-11-03",
      "column": "verdict",
      "label": "Halted at the plan: waiting on you (Answers to five interview questions on the budget's behaviour: a failed store load at the restore, the scope of maxQueued, the members usable after close(), which call runs the one-time restore, and whether a Restored event exists.)"
    },
    {
      "date": "2026-11-03",
      "column": "verdict",
      "label": "Resumed at the plan"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Research planning: 0 questions drafted"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Research started: `waits-and-close`, `retry-after-parsing`, `ledger-time`"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Research returned: `waits-and-close`",
      "ref": "research/waits-and-close.md"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Research returned: `ledger-time`",
      "ref": "research/ledger-time.md"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Research returned: `retry-after-parsing`",
      "ref": "research/retry-after-parsing.md"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Verification started: 3 findings"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Research verified; the plan next",
      "ref": "research/README.md"
    },
    {
      "date": "2026-11-03",
      "column": "verdict",
      "label": "Halted at the plan: waiting on you (Two choices the research raised: what happens to counts already charged at a higher window index when the wall clock steps back across a boundary, and what happens to a restore snapshot stamped further ahead than maxRestoreSkew.)"
    },
    {
      "date": "2026-11-04",
      "column": "verdict",
      "label": "Resumed at the plan"
    },
    {
      "date": "2026-11-04",
      "column": "verdict",
      "label": "Halted at the plan: waiting on you (Confirmation of five choices the drafted plan makes inside the settled design: the event fields, CostExceedsLimit covering a lane's capacity, the snapshot contents, Retry-After: 0, and arrival order.)"
    },
    {
      "date": "2026-11-04",
      "column": "verdict",
      "label": "Plan accepted",
      "ref": "plan.md"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "Branch `feat/issue-67-call-budget` cut; implementation starts at S1"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "S2 done; S3 current: fixtures: RecordingBudgetObserver and FakeBudgetStore"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "As built written for the 3 rows S1 and S2 finish, from the as-built record and the source: 2 as planned, 1 amended (`deterministic-tests`, two test deviations); the 9 rows S4 finishes say what is built and what is left; the test classes mapped to their rows and stages",
      "ref": "final-implementation.md"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "S4 done; S5 current: Level 3 and Level 4c suites"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "As built written for the 11 rows S3 and S4 finish, from the as-built record and the source: 10 as planned, 1 amended (`component-tests`, D3); `release` says what is built and what is left; the 25 test classes of the report mapped to their rows, the fixtures' two to S3",
      "ref": "final-implementation.md"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "Implementation done (5 stages); qa next"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "As built written for the 2 rows S5 finishes, from the as-built record and the source: `integration-tests` as planned, `nonfunctional-tests` amended (D4, D5, D6), its two open points shown open; `release` still says what is built and what is left; the 4 test classes of the report mapped to their rows",
      "ref": "final-implementation.md"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "QA pass 1: running"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "QA pass 1: changes requested (2 test, 1 code, 2 minor, 1 other)"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "QA pass 1's findings mapped to the As built rows the fixes touch: `hard-stop` (the persisted mark, plan.md R13, §6.7) and `component-tests` (the Level 2 reproduction and the two edge cases)",
      "ref": "plan.md#67-persistence-write-ahead-restore-flush"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "Remediation for qa pass 1: S5"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "Open point resolved: Procedure P3's mutation (the closing re-check at the top of admitLocked removed) is not detected by WindowedCallBudgetCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
      "ref": "final-implementation.md"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "QA pass 2: running"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "As built written for the fixes for qa pass 1, from the as-built record and the source: `hard-stop` amended (D7); `component-tests`, `deterministic-tests`, `close` and `nonfunctional-tests` brought up to the amended tests and Procedure P3's outcome; the four amended test classes mapped to the fixes",
      "ref": "final-implementation.md"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "QA pass 2: changes requested (4 test, 1 code, 5 minor)"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "QA pass 2's findings mapped to the As built rows the fixes touch: `fixtures` (the fake store's `times = 0`, the fixtures consumer gating class), `close` (a waiter's write-ahead save across `close()`), `hard-stop` (a store `Error`, a retried restore re-planning waiters) and `component-tests` (the three cases to add)",
      "ref": "plan.md#9-test-plan"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "Remediation for qa pass 2: 1 sub-step done; every sub-step done"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "Document: 0 of 7 tasks done"
    },
    {
      "date": "2026-11-04",
      "column": "built",
      "label": "As built written for the fixes for qa pass 2, from the as-built record and the source: `fixtures` amended (D8); `component-tests`, `close` and `hard-stop` brought up to the added cases",
      "ref": "final-implementation.md"
    },
    {
      "date": "2026-11-05",
      "column": "built",
      "label": "Document: Reconcile documentation current"
    },
    {
      "date": "2026-11-05",
      "column": "built",
      "label": "Document: Reconcile plan current"
    },
    {
      "date": "2026-11-05",
      "column": "built",
      "label": "Document: State boards current"
    },
    {
      "date": "2026-11-05",
      "column": "built",
      "label": "Open point resolved: The plan's Level 4c re-plan cost row asserts that the queue's planned instants stay in lane order; no seam exposes a waiter's planned instant, so the assertion is not implemented (deviation D4). To settle: an internal test seam, or the assertion dropped as a known gap.",
      "ref": "final-implementation.md"
    },
    {
      "date": "2026-11-05",
      "column": "built",
      "label": "Document: Document-landing commit current"
    },
    {
      "date": "2026-11-05",
      "column": "built",
      "label": "Document: every task done"
    },
    {
      "date": "2026-11-05",
      "column": "built",
      "label": "Landing: the land accepted"
    },
    {
      "date": "2026-11-05",
      "column": "built",
      "label": "Merge: merging it in PR #90",
      "ref": "PR #90"
    },
    {
      "date": "2026-11-05",
      "column": "built",
      "label": "Landed in PR #90, merge 5f221ce",
      "ref": "PR #90"
    }
  ],
  "rows": [
    {
      "id": "port",
      "group": "Code",
      "name": "The `CallBudget` port and `CallBudget.Unlimited`",
      "system": "webtools-http-budget",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "`webtools-http` has gated ports for the transport and the cache and none for a budget, so nothing grants a call before it is sent. The `BudgetExhausted` failure exists and nothing returns it, and the facades of unit 9 have no seam to call or an implementation to default to.",
        "evidence": [
          "common/HttpFailure.kt:63 — `HttpFailure.BudgetExhausted`, built by #62, with nothing returning it (baseline)",
          "common/ExhaustedReason.kt:12 — its reasons, the same",
          "webtools-http/api/webtools-http.api:116 — the dump's one budget entry at the baseline, that failure",
          "../architecture.md §7.2 — the call path: cache, then budget inside the loader, then transport"
        ],
        "source": "../research/README.md § Call budget; ../architecture.md §6.5",
        "link": "../research/README.md#call-budget"
      },
      "options": [
        {
          "id": "gated-port",
          "name": "A subclass-gated `CallBudget : AutoCloseable` with `acquire(cost, lane, maxWait): Result<Permit>` and `utilisation()`, plus the pass-through `CallBudget.Unlimited`",
          "verdict": "accepted",
          "why": "An interface is a narrower contract than an overridable class. A wait above the cap fails fast with `BudgetExhausted`, and the budget never runs a retry. Implementing it is Experimental; calling it and the supplied defaults are Stable Core.",
          "source": "../architecture.md §6.5, §9",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "blocking-api",
          "name": "A blocking, `CompletableFuture` or Java-friendly API",
          "verdict": "declined",
          "why": "A non-goal of the stack: `suspend`, `Result` and `Duration` already mangle JVM names.",
          "source": "../architecture.md §4; ../research/README.md § Conventions",
          "link": "../architecture.md#4-non-goals"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "`CallBudget : AutoCloseable` was built as a common interface gated by `@SubclassOptInRequired`. Procedure P1 showed the gate: implementing a gated seam with no opt-in failed to compile with the marker's message and compiled with it, and `CallBudgetConsumerGatingTest` names, passes and calls the types with no opt-in.",
            "state": "as-planned",
            "ref": "final-implementation.md#4-measurements",
            "excerpt": {
              "ref": "final-implementation.md#4-measurements",
              "heading": "4. Measurements",
              "paragraph": "Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other module's suite.",
              "hash": "73ab66970463"
            }
          },
          {
            "text": "It has `acquire(cost, lane, maxWait): Result<Permit>`, a suspending `utilisation()`, a `flush()` whose default body succeeds and a `close()` with an empty body, and no defaulted parameter.",
            "state": "as-planned"
          },
          {
            "text": "Its KDoc numbers the ten contract points, and `WindowedCallBudget` is the first budget that carries out the waiting, refusal, refund and closing ones; the gate's KDoc names it among what needs no opt-in.",
            "state": "as-planned"
          },
          {
            "text": "A wait above `maxWait` returned `BudgetExhausted` with the wait the call would have had and charged nothing, as `WindowedCallBudgetFifoComponentTest` holds; a `cost` below 1 and a negative `maxWait` throw.",
            "state": "as-planned"
          },
          {
            "text": "`CallBudget.Unlimited` is a nested object that grants at once, ignores signals and `close()`, succeeds at `flush()`, reports an empty utilisation and checks its arguments as the real budget does, held by 7 component tests.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One system, the call budget of `webtools-http`: a common port a consumer or a fake substitutes, an interface being a narrower contract than an overridable class.",
        "statements": [
          {
            "text": "`CallBudget : AutoCloseable` is a common interface gated by `@SubclassOptInRequired`: implementing it is Experimental, while calling it, passing it and naming it in a signature are Stable Core and need no opt-in.",
            "ref": "plan.md#r1-the-public-surface-and-its-tier",
            "excerpt": {
              "ref": "plan.md#r1-the-public-surface-and-its-tier",
              "heading": "R1. The public surface and its tier",
              "paragraph": "`webtools-http` gains these public declarations in `com.spartanlabs.webtools.http`, each with KDoc (`@param`, `@return`, `@throws`) that names its tier. Implementing or subclassing `CallBudget`, `Permit`, `RateSignalsReader`, `BudgetStore`, `BudgetObserver` or `BudgetEvent` is Experimental (behind `ExperimentalForInheritanceWebToolsApi`); everything else is Stable Core and usable without the opt-in: calling the ports, passing them, naming them in a signature, `CallBudget.Unlimited`, `WindowedCallBudget`, `InMemoryBudgetStore`, `RateSignalsReader.Standard`, `BudgetObserver.None`, `withPermit`, …",
              "hash": "e8c414c1d243"
            }
          },
          {
            "text": "It has `acquire(cost, lane, maxWait): Result<Permit>`, a suspending `utilisation()`, a `flush()` whose default body succeeds and a `close()` with an empty body, and no defaulted parameter.",
            "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
            "excerpt": {
              "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
              "heading": "R2. The `CallBudget` port and its contract",
              "paragraph": "No defaulted parameters. The contract, numbered in the KDoc as `HttpTransport`'s and `ResponseCache`'s are:",
              "hash": "6511503d7606"
            }
          },
          {
            "text": "Its KDoc numbers a ten-point contract: charged at the grant, fail fast, first come first served within a lane, cancellation, closing, permits, utilisation, flush, programmer errors and concurrency.",
            "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
            "excerpt": {
              "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
              "heading": "R2. The `CallBudget` port and its contract",
              "paragraph": "No defaulted parameters. The contract, numbered in the KDoc as `HttpTransport`'s and `ResponseCache`'s are:",
              "hash": "6511503d7606"
            }
          },
          {
            "text": "A wait above `maxWait` returns `BudgetExhausted` with the wait the call would have had and charges nothing; a `cost` below 1 and a negative `maxWait` throw.",
            "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
            "excerpt": {
              "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
              "heading": "R2. The `CallBudget` port and its contract",
              "paragraph": "No defaulted parameters. The contract, numbered in the KDoc as `HttpTransport`'s and `ResponseCache`'s are:",
              "hash": "6511503d7606"
            }
          },
          {
            "text": "`CallBudget.Unlimited` is a nested object that grants at once, ignores signals and `close()`, succeeds at `flush()`, reports an empty utilisation and checks its arguments as the real budget does.",
            "ref": "plan.md#r3-permit-withpermit-and-callbudgetunlimited",
            "excerpt": {
              "ref": "plan.md#r3-permit-withpermit-and-callbudgetunlimited",
              "heading": "R3. `Permit`, `withPermit` and `CallBudget.Unlimited`",
              "paragraph": "`Permit` is a `@SubclassOptInRequired` interface, not `AutoCloseable` (its completion is suspend): `val cost: Int`, `val lane: Lane`, `suspend fun complete()` (a body that calls `complete(RateSignals.None)`), `suspend fun complete(signals: RateSignals)` and `suspend fun release()`. `complete` means the request was sent; `release` means it never was (the units are refunded, and a `complete` after a `release`, or the reverse, is ignored). · `withPermit(cost, lane, maxWait, block)` is a plain (not `inline`) suspend extension on `CallBudget`: it acquires, runs `block(permit)` and completes the …",
              "hash": "565944325b9c"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write the port with its contract and `CallBudget.Unlimited`, and extend the opt-in marker's list of gated types.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/CallBudget.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/ExperimentalForInheritanceWebToolsApi.kt"
            ],
            "check": "`:webtools-http:componentTest`; Procedure P1",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S4",
            "what": "Write the two consumer classes that compile with no opt-in.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/http/CallBudgetConsumerGatingTest.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/http/WindowedCallBudgetConsumerGatingTest.kt"
            ],
            "check": "`:webtools-http:gatingTest`; Procedure P1",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 1 (gating) `CallBudgetConsumerGatingTest`, compiled with no opt-in, and `WindowedCallBudgetConsumerGatingTest` on production defaults; Level 2 (component) `CallBudgetUnlimitedComponentTest`; Procedure P1 shows once, by hand, that an implementer needs the opt-in"
        ],
        "risks": [
          "Stable Core from the first release: the names and `acquire`'s parameter order are hard to change after 1.0.0 (§10)",
          "Dropping `@SubclassOptInRequired` passes `apiCheck`, so the gate stays a review item until a compile-fail check exists (§9)"
        ],
        "source": "plan.md R1, R2, R3, §6.1",
        "link": "plan.md#r2-the-callbudget-port-and-its-contract"
      }
    },
    {
      "id": "permit",
      "group": "Code",
      "name": "`Permit` and `withPermit`",
      "system": "webtools-http-budget",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing carries a granted call to its end: no way to report what the server said once the response is in, or to hand back a grant that never sent. A granted permit already handed to a coroutine that is then cancelled stays charged unless it is released.",
        "evidence": [
          "../research/call-budget.md F4 — a job cancelled at the instant of delivery received `Result.success` and the unit stayed charged; `complete` from the `finally` of a cancelled coroutine still applied its signals under `NonCancellable`",
          "../research/call-budget.md F8 — a permit never completed is spent, with nothing to leak",
          "research/waits-and-close.md F5 — a cancellation that lands after the grant section's `ensureActive()` leaves the caller holding a permit it must complete or release",
          "research/waits-and-close.md F4 — `complete()` and `release()` from a cancelled coroutine: a contended `Mutex.lock` inside `NonCancellable` waits for its holder rather than throwing (measured)"
        ],
        "source": "research/README.md § Waiting, re-planning, refunds and `close()`; ../research/README.md § Call budget; ../architecture.md §6.5",
        "link": "../research/README.md#call-budget"
      },
      "options": [
        {
          "id": "spent-at-grant",
          "name": "A `Permit` charged at grant, with idempotent `complete()`, `complete(signals)` and `release()` under `NonCancellable`, and a plain `suspend fun withPermit` that completes it as spent in `finally`",
          "verdict": "accepted",
          "why": "The budget holds no lock or semaphore for a permit, so nothing leaks and no lease timeout is needed. One permit covers a logical call, redirects included.",
          "source": "../architecture.md §6.5",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "autocloseable",
          "name": "`Permit : AutoCloseable`",
          "verdict": "declined",
          "why": "`complete` and `release` are suspend and `close()` cannot be, and the safe default on an exception is spent with no signals, which `release()` must override.",
          "source": "../architecture.md §6.5; ../research/call-budget.md F8",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "holding-token",
          "name": "A token that holds a lock until completed, as Kord's `ExclusionRequestRateLimiter` does",
          "verdict": "declined",
          "why": "An uncompleted token keeps the lock, which is why Kord ships a helper to complete it in `finally`; a permit charged at grant has nothing to hold.",
          "source": "../research/call-budget.md F8"
        },
        {
          "id": "inline-helper",
          "name": "`withPermit` as a `suspend inline` function",
          "verdict": "declined",
          "why": "A public `inline` function freezes its body into callers and may call only public or `@PublishedApi` API.",
          "source": "../research/api-shape-and-failures.md F7"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "`Permit` was built as a gated interface, not `AutoCloseable`, with `cost`, `lane`, `complete()`, `complete(signals)` and `release()`.",
            "state": "as-planned"
          },
          {
            "text": "On the real budget `complete` applies the response's signals and `release` refunds the units and re-plans the waiters; a `complete` after a `release`, or the reverse, is ignored, as `WindowedCallBudgetRefundComponentTest` holds.",
            "state": "as-planned"
          },
          {
            "text": "All three are idempotent and run under `NonCancellable`; `WindowedCallBudgetCancellationComponentTest` shows them taking effect from a cancelled coroutine once the lock frees.",
            "state": "as-planned"
          },
          {
            "text": "`withPermit` is a plain suspend extension, not `inline`, that completes the permit in a `NonCancellable` `finally`, a no-op when the block completed or released it. An exception from the block propagates and a refused or closed acquire never runs the block, held on a real budget by the 10 tests of `WithPermitComponentTest`.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A permit is charged at its grant, so the budget holds nothing for it and a permit never completed is simply spent.",
        "statements": [
          {
            "text": "`Permit` is a gated interface, not `AutoCloseable` as its completion suspends, with `cost`, `lane`, `complete()`, `complete(signals)` and `release()`.",
            "ref": "plan.md#r3-permit-withpermit-and-callbudgetunlimited",
            "excerpt": {
              "ref": "plan.md#r3-permit-withpermit-and-callbudgetunlimited",
              "heading": "R3. `Permit`, `withPermit` and `CallBudget.Unlimited`",
              "paragraph": "`Permit` is a `@SubclassOptInRequired` interface, not `AutoCloseable` (its completion is suspend): `val cost: Int`, `val lane: Lane`, `suspend fun complete()` (a body that calls `complete(RateSignals.None)`), `suspend fun complete(signals: RateSignals)` and `suspend fun release()`. `complete` means the request was sent; `release` means it never was (the units are refunded, and a `complete` after a `release`, or the reverse, is ignored). · `withPermit(cost, lane, maxWait, block)` is a plain (not `inline`) suspend extension on `CallBudget`: it acquires, runs `block(permit)` and completes the …",
              "hash": "565944325b9c"
            }
          },
          {
            "text": "`complete` means the request was sent and `release` that it never was: the units are refunded, and a `complete` after a `release`, or the reverse, is ignored.",
            "ref": "plan.md#r3-permit-withpermit-and-callbudgetunlimited",
            "excerpt": {
              "ref": "plan.md#r3-permit-withpermit-and-callbudgetunlimited",
              "heading": "R3. `Permit`, `withPermit` and `CallBudget.Unlimited`",
              "paragraph": "`Permit` is a `@SubclassOptInRequired` interface, not `AutoCloseable` (its completion is suspend): `val cost: Int`, `val lane: Lane`, `suspend fun complete()` (a body that calls `complete(RateSignals.None)`), `suspend fun complete(signals: RateSignals)` and `suspend fun release()`. `complete` means the request was sent; `release` means it never was (the units are refunded, and a `complete` after a `release`, or the reverse, is ignored). · `withPermit(cost, lane, maxWait, block)` is a plain (not `inline`) suspend extension on `CallBudget`: it acquires, runs `block(permit)` and completes the …",
              "hash": "565944325b9c"
            }
          },
          {
            "text": "All three are idempotent and run under `NonCancellable`, so they take effect from a cancelled coroutine.",
            "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
            "excerpt": {
              "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
              "heading": "R2. The `CallBudget` port and its contract",
              "paragraph": "No defaulted parameters. The contract, numbered in the KDoc as `HttpTransport`'s and `ResponseCache`'s are:",
              "hash": "6511503d7606"
            }
          },
          {
            "text": "`withPermit` is a plain suspend extension, not `inline`: it completes the permit as spent in a `finally` unless the block completed or released it, an exception from the block propagates, and on a refused or closed acquire the block does not run.",
            "ref": "plan.md#r3-permit-withpermit-and-callbudgetunlimited",
            "excerpt": {
              "ref": "plan.md#r3-permit-withpermit-and-callbudgetunlimited",
              "heading": "R3. `Permit`, `withPermit` and `CallBudget.Unlimited`",
              "paragraph": "`Permit` is a `@SubclassOptInRequired` interface, not `AutoCloseable` (its completion is suspend): `val cost: Int`, `val lane: Lane`, `suspend fun complete()` (a body that calls `complete(RateSignals.None)`), `suspend fun complete(signals: RateSignals)` and `suspend fun release()`. `complete` means the request was sent; `release` means it never was (the units are refunded, and a `complete` after a `release`, or the reverse, is ignored). · `withPermit(cost, lane, maxWait, block)` is a plain (not `inline`) suspend extension on `CallBudget`: it acquires, runs `block(permit)` and completes the …",
              "hash": "565944325b9c"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write `Permit` and `withPermit` in common code.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/Permit.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WithPermit.kt"
            ],
            "check": "`:webtools-http:componentTest`; Procedure P1",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S4",
            "what": "Implement the permit in the budget's `NonCancellable` shell and test `withPermit` on the real budget.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowedCallBudget.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WithPermitComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `WithPermitComponentTest` and `WindowedCallBudgetCancellationComponentTest`"
        ],
        "risks": [
          "A cancellation after the last suspension point leaves the caller holding a permit it must complete or release, which `withPermit`'s `finally` does (§6.4)"
        ],
        "source": "plan.md R2, R3, §6.1",
        "link": "plan.md#r3-permit-withpermit-and-callbudgetunlimited"
      }
    },
    {
      "id": "vocabulary",
      "group": "Code",
      "name": "`Lane`, `RateWindow`, `RateSignals`, `WindowReading` and `BudgetUtilisation`",
      "system": "webtools-http-budget",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "No type names a provider's window, a lane, what a server reported or how far a budget has burned, so a consumer cannot describe a provider's limits or stretch its polling.",
        "evidence": [
          "../research/call-budget.md F1 — per-minute and per-day windows, with defaults that differ by provider and key",
          "../research/call-budget.md F3 — the margin for other processes behind one IP; `cost` an integer charged to every window",
          "../research/call-budget.md F9 — what a consumer needs from a utilisation reading"
        ],
        "source": "../research/README.md § Call budget; ../architecture.md §6.5",
        "link": "../research/README.md#call-budget"
      },
      "options": [
        {
          "id": "plain-values",
          "name": "Unmarked value types: `Lane(name)` a plain final class with name equality and the constants `Interactive` and `Tracker`; `RateWindow` by factory and builder with `limit = published - margin`; `RateSignals`, `WindowReading`, `BudgetUtilisation` and `WindowUtilisation`",
          "verdict": "accepted",
          "why": "A consumer can add a lane, and calling `complete` needs no opt-in. A type whose every parameter is its meaning keeps a public constructor; policy numbers stay builder properties.",
          "source": "../architecture.md §1, §6.5, §9",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "direct-markers",
          "name": "The opt-in marker directly on `RateSignals`, `WindowReading` and the snapshot types",
          "verdict": "declined",
          "why": "A directly marked type in `Permit.complete` or a constructor default forces the opt-in on every caller; only the interfaces a consumer implements carry the gate.",
          "source": "../research/README.md § Direct opt-in markers"
        },
        {
          "id": "inline-lane",
          "name": "`Lane` as an inline value class",
          "verdict": "declined",
          "why": "It is mangled in every function that takes it and boxed as a generic argument, and `Lane` is a `Map` key in the lane shares.",
          "source": "../research/api-shape-and-failures.md"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "`Lane(name)` was built as a plain final class with name equality, a name that may not be blank and the constants `Interactive` and `Tracker`.",
            "state": "as-planned"
          },
          {
            "text": "`RateWindow` was built with a factory and a `@WebToolsDsl` builder of `margin` and `hard`, with `limit` reading `published - margin` and fixed windows starting at multiples of its period since the Unix epoch.",
            "state": "as-planned"
          },
          {
            "text": "`RateSignals` was built the same way, with `RateSignals.None` on its companion; the ledger gives a signal that is not `throttled` no pause hint, and reconciles its readings regardless.",
            "state": "as-planned"
          },
          {
            "text": "`WindowReading` holds `remaining`, which may be negative, and an optional `resetIn`; `BudgetUtilisation` and `WindowUtilisation` have public constructors.",
            "state": "as-planned"
          },
          {
            "text": "None was built as a `data class`, and none carries the opt-in marker.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Plain common vocabulary with no platform content, so a consumer can name a provider's windows and read how far a budget has burned with no opt-in.",
        "statements": [
          {
            "text": "`Lane(name)` is a plain final class with name equality, a non-blank name and the constants `Interactive` and `Tracker`.",
            "ref": "plan.md#r4-the-vocabulary",
            "excerpt": {
              "ref": "plan.md#r4-the-vocabulary",
              "heading": "R4. The vocabulary",
              "paragraph": "`Lane(name)`: a plain final class, equality on the name, a non-blank name (`IllegalArgumentException`, message `name must not be blank`), `toString()` `Lane(<name>)`, constants `Lane.Interactive` and `Lane.Tracker`. · `RateWindow`: `internal constructor(name, published, period, margin, hard)` with public `val`s and `val limit = published - margin` (the enforced limit); the factory `RateWindow(name: String, published: Int, period: Duration, configure: RateWindow.Builder.() -> Unit = {})` and `@WebToolsDsl public class Builder internal constructor()` with `var margin: Int = 0` and `var hard: …",
              "hash": "8ee8aa00914a"
            }
          },
          {
            "text": "`RateWindow` is built by a factory and a `@WebToolsDsl` builder, with `limit = published - margin` and fixed windows starting at multiples of its period since the Unix epoch.",
            "ref": "plan.md#r4-the-vocabulary",
            "excerpt": {
              "ref": "plan.md#r4-the-vocabulary",
              "heading": "R4. The vocabulary",
              "paragraph": "`Lane(name)`: a plain final class, equality on the name, a non-blank name (`IllegalArgumentException`, message `name must not be blank`), `toString()` `Lane(<name>)`, constants `Lane.Interactive` and `Lane.Tracker`. · `RateWindow`: `internal constructor(name, published, period, margin, hard)` with public `val`s and `val limit = published - margin` (the enforced limit); the factory `RateWindow(name: String, published: Int, period: Duration, configure: RateWindow.Builder.() -> Unit = {})` and `@WebToolsDsl public class Builder internal constructor()` with `var margin: Int = 0` and `var hard: …",
              "hash": "8ee8aa00914a"
            }
          },
          {
            "text": "`RateSignals` is built the same way, with `RateSignals.None` on its companion; a signal that is not `throttled` never pauses the budget, and its readings still reconcile counts.",
            "ref": "plan.md#r4-the-vocabulary",
            "excerpt": {
              "ref": "plan.md#r4-the-vocabulary",
              "heading": "R4. The vocabulary",
              "paragraph": "`Lane(name)`: a plain final class, equality on the name, a non-blank name (`IllegalArgumentException`, message `name must not be blank`), `toString()` `Lane(<name>)`, constants `Lane.Interactive` and `Lane.Tracker`. · `RateWindow`: `internal constructor(name, published, period, margin, hard)` with public `val`s and `val limit = published - margin` (the enforced limit); the factory `RateWindow(name: String, published: Int, period: Duration, configure: RateWindow.Builder.() -> Unit = {})` and `@WebToolsDsl public class Builder internal constructor()` with `var margin: Int = 0` and `var hard: …",
              "hash": "8ee8aa00914a"
            }
          },
          {
            "text": "`WindowReading` holds `remaining`, which may be negative, and an optional `resetIn`; `BudgetUtilisation` and `WindowUtilisation` have public constructors, so an implementer of the port can build them.",
            "ref": "plan.md#r4-the-vocabulary",
            "excerpt": {
              "ref": "plan.md#r4-the-vocabulary",
              "heading": "R4. The vocabulary",
              "paragraph": "`Lane(name)`: a plain final class, equality on the name, a non-blank name (`IllegalArgumentException`, message `name must not be blank`), `toString()` `Lane(<name>)`, constants `Lane.Interactive` and `Lane.Tracker`. · `RateWindow`: `internal constructor(name, published, period, margin, hard)` with public `val`s and `val limit = published - margin` (the enforced limit); the factory `RateWindow(name: String, published: Int, period: Duration, configure: RateWindow.Builder.() -> Unit = {})` and `@WebToolsDsl public class Builder internal constructor()` with `var margin: Int = 0` and `var hard: …",
              "hash": "8ee8aa00914a"
            }
          },
          {
            "text": "None is a public `data class`, and none carries the opt-in marker: marking a value type would force the opt-in on every `complete`.",
            "ref": "plan.md#61-mechanism-per-type-architecture-69-and-9-for-this-units-types",
            "excerpt": {
              "ref": "plan.md#61-mechanism-per-type-architecture-69-and-9-for-this-units-types",
              "heading": "6.1 Mechanism per type (architecture §6.9 and §9, for this unit's types)",
              "paragraph": "",
              "hash": "19d2d3c115e0"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write the value types and extend the `@WebToolsDsl` list with the two new builders.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/Lane.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/RateWindow.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowReading.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/RateSignals.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/BudgetUtilisation.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowUtilisation.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WebToolsDsl.kt"
            ],
            "check": "`:webtools-http:componentTest`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `LaneComponentTest`, `RateWindowComponentTest`, `RateSignalsComponentTest` and `BudgetUtilisationComponentTest`; the changed `WebToolsDslComponentTest`"
        ],
        "risks": [
          "Stable Core from the first release: these names and fields are hard to change after 1.0.0 (§10)"
        ],
        "source": "plan.md R4, §6.1",
        "link": "plan.md#r4-the-vocabulary"
      }
    },
    {
      "id": "signals-reader",
      "group": "Code",
      "name": "`RateSignalsReader`, its `Standard` and `RetryAfter`",
      "system": "webtools-http-budget",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing turns a response into signals. RFC 9110 requires `Retry-After` as seconds or any of three HTTP-date forms, `java.time` is not available to common code, and the providers' own headers differ: OpenDota's `429`s are told apart by headers, Open-Meteo's only by the body.",
        "evidence": [
          "../research/call-budget.md F6 — the RFC rules, the parser in common Kotlin and what each provider's `429` carries",
          "../research/call-budget.md F6 — with the response's own `Date` the delta is between two server readings: a clock 10 minutes fast gave 1 m instead of 0 s",
          "research/retry-after-parsing.md F1 — RFC 9110's grammar, three date forms and `delay-seconds`",
          "research/retry-after-parsing.md F2, F3 — where the sources disagree: the letter case of a date, and a weekday that does not match it",
          "research/retry-after-parsing.md F6, F7 — every mature parser read uses a fixed two-digit-year pivot; only RFC 9110 states the sliding rule, which reduces to one line",
          "research/retry-after-parsing.md F9 — the delay between two server readings, and what a malformed `Date` changes",
          "research/retry-after-parsing.md section 4 — OkHttp 5.5.0's own `Retry-After` reader ignores dates and is unbounded on digits",
          "common/HttpSyntax.kt:23, :36 — `trimOws` and `asciiLower`, there for a date parser to use (baseline)"
        ],
        "source": "research/README.md § `Retry-After` and HTTP dates; ../research/README.md § Call budget; ../architecture.md §6.5",
        "link": "research/README.md#retry-after-and-http-dates"
      },
      "options": [
        {
          "id": "gated-reader",
          "name": "A plain gated `RateSignalsReader` with a `Standard` default (`429`, or `503` with `Retry-After`, no counts), a consumer supplying a per-provider reader, and `RetryAfter.parse` in common Kotlin, differential-tested against `java.time` in `jvmTest`",
          "verdict": "accepted",
          "why": "The default assumes no provider's headers. The parser matched `java.time` on 600,000 random instants with no mismatch, and the reader takes the whole response because one provider says it only in the body.",
          "source": "../architecture.md §6.5",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "ietf-reader",
          "name": "A generic reader of the IETF `RateLimit` header",
          "verdict": "declined",
          "why": "An Internet-Draft that has changed shape across revisions, and no provider researched sends it. A non-goal of the stack.",
          "source": "../architecture.md §4; ../research/call-budget.md F6",
          "link": "../architecture.md#4-non-goals"
        },
        {
          "id": "okhttp-reader",
          "name": "OkHttp 5.5.0's own `Retry-After` reader",
          "verdict": "declined",
          "why": "It ignores dates and is unbounded on digits, and its date fallbacks accept trailing text.",
          "source": "research/README.md § `Retry-After` and HTTP dates",
          "link": "research/README.md#retry-after-and-http-dates"
        },
        {
          "id": "case-sensitive-dates",
          "name": "HTTP-date names matched case-sensitively, as RFC 9110 writes them",
          "verdict": "declined",
          "why": "Refusing a usable `Retry-After` for its letter case drops a hint the provider meant; RFC 9111 §4.2 tells a cache to match case-insensitively, and every mature parser read ignores case.",
          "source": "research/README.md § `Retry-After` and HTTP dates",
          "link": "research/README.md#retry-after-and-http-dates"
        },
        {
          "id": "fixed-year-pivot",
          "name": "A fixed pivot for a two-digit year, as Go (69), curl (70) and CPython (68) have",
          "verdict": "declined",
          "why": "The reference-relative rule follows RFC 9110, and equals `java.time`'s reduced-year window.",
          "source": "research/README.md § `Retry-After` and HTTP dates",
          "link": "research/README.md#retry-after-and-http-dates"
        },
        {
          "id": "extra-date-shapes",
          "name": "Extra shapes: `UTC` or offset zones and a four-digit RFC 850 year",
          "verdict": "declined",
          "why": "No provider sends them, and accepting them later is additive.",
          "source": "research/README.md § `Retry-After` and HTTP dates",
          "link": "research/README.md#retry-after-and-http-dates"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "`RetryAfter.parse` takes `delay-seconds` and RFC 9110's three HTTP-date forms through one `internal` parser in common code, `HttpDate`, which also reads the response's `Date`.",
            "state": "as-planned"
          },
          {
            "text": "The parser takes the three liberties and no others: names and `GMT` in any ASCII case, SP and HTAB around the value trimmed, and a weekday that disagrees with the date ignored.",
            "state": "as-planned"
          },
          {
            "text": "The result is never negative and always finite: a date in the past is `Duration.ZERO`, and `delay-seconds` saturates at 253,402,300,799 s.",
            "state": "as-planned"
          },
          {
            "text": "A two-digit RFC 850 year resolves against the reference, as the greatest year with those digits not after the reference year plus 50.",
            "state": "as-planned"
          },
          {
            "text": "A failure is the `internal` `HttpDateParseException`, an `IllegalArgumentException` subclass with a constant message for each problem, which never echoes the value.",
            "state": "as-planned"
          },
          {
            "text": "`RateSignalsReader` is a plain gated interface; `Standard` reads a `429` as throttled, a `503` only with a `Retry-After` that parses and any other status as `RateSignals.None`, and reports no window readings.",
            "state": "as-planned"
          },
          {
            "text": "`Standard` takes the longest wait among the `Retry-After` lines that parse, and measures a date against the response's `Date` when that parses.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One grammar in common code keeps the `java.time` differential meaningful, and the default reader assumes no provider's headers.",
        "statements": [
          {
            "text": "`RetryAfter.parse` takes RFC 9110's `delay-seconds` and its three HTTP-date forms through one `internal` parser in common code, which also reads the response's `Date`.",
            "ref": "plan.md#68-retryafter-and-the-http-date-parser",
            "excerpt": {
              "ref": "plan.md#68-retryafter-and-the-http-date-parser",
              "heading": "6.8 `RetryAfter` and the HTTP-date parser",
              "paragraph": "One `internal` `HttpDate` parser serves `RetryAfter.parse` and the `Date` reading of `Standard`. Input is trimmed of leading and trailing SP and HTAB (`HttpSyntax.trimOws`) and of nothing else; digits are ASCII `0-9` (compared as `c in '0'..'9'`, never a character-class predicate); letters compare through `HttpSyntax.asciiLower`.",
              "hash": "b91ec4582e22"
            }
          },
          {
            "text": "It takes three liberties beyond RFC 9110's text: names and `GMT` match in any ASCII case, SP and HTAB around the value are trimmed, and a weekday that disagrees with the date is ignored.",
            "ref": "plan.md#68-retryafter-and-the-http-date-parser",
            "excerpt": {
              "ref": "plan.md#68-retryafter-and-the-http-date-parser",
              "heading": "6.8 `RetryAfter` and the HTTP-date parser",
              "paragraph": "One `internal` `HttpDate` parser serves `RetryAfter.parse` and the `Date` reading of `Standard`. Input is trimmed of leading and trailing SP and HTAB (`HttpSyntax.trimOws`) and of nothing else; digits are ASCII `0-9` (compared as `c in '0'..'9'`, never a character-class predicate); letters compare through `HttpSyntax.asciiLower`.",
              "hash": "b91ec4582e22"
            }
          },
          {
            "text": "The result is never negative and always finite: a date in the past is `Duration.ZERO`, and `delay-seconds` saturates at 253,402,300,799 s.",
            "ref": "plan.md#r5-retryafter",
            "excerpt": {
              "ref": "plan.md#r5-retryafter",
              "heading": "R5. `RetryAfter`",
              "paragraph": "`public object RetryAfter` with `parse(value: String, nowEpochMillis: Long): Result<Duration>` and `parse(value: String, nowEpochMillis: Long, serverDateEpochMillis: Long): Result<Duration>`. The grammar and every number are in §6.8; in short: RFC 9110's `delay-seconds` and its three HTTP-date forms, with three liberties (ASCII-case-insensitive names and `GMT`; SP/HTAB trimmed around the value; a weekday that disagrees with the date is ignored). The result is never negative (a date in the past is `Duration.ZERO`) and always finite (`delay-seconds` saturates at 253,402,300,799 s). A failure is …",
              "hash": "218131fd881b"
            }
          },
          {
            "text": "A two-digit RFC 850 year resolves against the reference, as the greatest year with those digits not after the reference year plus 50.",
            "ref": "plan.md#68-retryafter-and-the-http-date-parser",
            "excerpt": {
              "ref": "plan.md#68-retryafter-and-the-http-date-parser",
              "heading": "6.8 `RetryAfter` and the HTTP-date parser",
              "paragraph": "One `internal` `HttpDate` parser serves `RetryAfter.parse` and the `Date` reading of `Standard`. Input is trimmed of leading and trailing SP and HTAB (`HttpSyntax.trimOws`) and of nothing else; digits are ASCII `0-9` (compared as `c in '0'..'9'`, never a character-class predicate); letters compare through `HttpSyntax.asciiLower`.",
              "hash": "b91ec4582e22"
            }
          },
          {
            "text": "A failure is an `internal` `IllegalArgumentException` subclass with a constant message that never echoes the value.",
            "ref": "plan.md#r5-retryafter",
            "excerpt": {
              "ref": "plan.md#r5-retryafter",
              "heading": "R5. `RetryAfter`",
              "paragraph": "`public object RetryAfter` with `parse(value: String, nowEpochMillis: Long): Result<Duration>` and `parse(value: String, nowEpochMillis: Long, serverDateEpochMillis: Long): Result<Duration>`. The grammar and every number are in §6.8; in short: RFC 9110's `delay-seconds` and its three HTTP-date forms, with three liberties (ASCII-case-insensitive names and `GMT`; SP/HTAB trimmed around the value; a weekday that disagrees with the date is ignored). The result is never negative (a date in the past is `Duration.ZERO`) and always finite (`delay-seconds` saturates at 253,402,300,799 s). A failure is …",
              "hash": "218131fd881b"
            }
          },
          {
            "text": "`RateSignalsReader` is a plain gated interface that never throws for bad input; `Standard` reads a `429` as throttled, a `503` only with a `Retry-After` that parses and any other status as `RateSignals.None`, with no window readings.",
            "ref": "plan.md#r6-ratesignalsreader-and-standard",
            "excerpt": {
              "ref": "plan.md#r6-ratesignalsreader-and-standard",
              "heading": "R6. `RateSignalsReader` and `Standard`",
              "paragraph": "`RateSignalsReader` is a `@SubclassOptInRequired` plain interface (not a `fun interface`, which cannot carry the gate): `fun read(response: HttpResponse, nowEpochMillis: Long): RateSignals`, which never throws for bad input. `RateSignalsReader.Standard` (a nested `public object`):",
              "hash": "4e6d971b7935"
            }
          },
          {
            "text": "`Standard` takes the longest wait among the `Retry-After` lines that parse, and measures a date against the response's `Date` when that parses.",
            "ref": "plan.md#r6-ratesignalsreader-and-standard",
            "excerpt": {
              "ref": "plan.md#r6-ratesignalsreader-and-standard",
              "heading": "R6. `RateSignalsReader` and `Standard`",
              "paragraph": "`RateSignalsReader` is a `@SubclassOptInRequired` plain interface (not a `fun interface`, which cannot carry the gate): `fun read(response: HttpResponse, nowEpochMillis: Long): RateSignals`, which never throws for bad input. `RateSignalsReader.Standard` (a nested `public object`):",
              "hash": "4e6d971b7935"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write the parser, `RetryAfter` and the reader, with the date table and the `java.time` differential.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/HttpDate.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/RetryAfter.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/RateSignalsReader.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HttpDateJavaTimeDifferentialDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `RateSignalsReaderStandardComponentTest`; Level 4a (deterministic) `HttpDateDeterministicTest`, `RetryAfterDeterministicTest` and `HttpDateJavaTimeDifferentialDeterministicTest`; Level 3 (integration) `RateSignalsReaderLoopbackIntegrationTest`"
        ],
        "risks": [
          "The Kotlin parser has not been compiled: its Java mirror is what was measured, and the Level 4a table and the differential establish it (§3)",
          "`java.time`'s reduced-year window was measured on JDK 23.0.2 only: on another build JDK the differential names the first mismatch and the oracle base is adjusted, not the parser (§3)"
        ],
        "source": "plan.md R5, R6, §6.8",
        "link": "plan.md#68-retryafter-and-the-http-date-parser"
      }
    },
    {
      "id": "windowed",
      "group": "Code",
      "name": "`WindowedCallBudget`: the ledger and its reservations",
      "system": "webtools-http-budget",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "No limiter exists. The providers count in fixed wall-clock windows and count refused requests, so a limiter of the wrong shape overspends at their boundary: a greedy token bucket let 119 calls into one server minute against 60, and plain aligned windows overspent in 510 of 1,000 skewed trials.",
        "evidence": [
          "../research/call-budget.md F1 — OpenDota and Open-Meteo count in fixed, clock-aligned windows",
          "../research/call-budget.md F2 — the algorithms measured against a fixed-window server",
          "../research/call-budget.md F4 — FIFO, cancellation and the re-plan, measured on virtual time",
          "research/waits-and-close.md F2 — how a waiter waits: a plain `delay` cannot move it earlier, so a cancelled waiter's slot would be wasted",
          "research/waits-and-close.md F4 — a refund from a cancelled coroutine, under `NonCancellable` (measured)",
          "research/ledger-time.md F8 — resetting a cell when the window index changes overspent by up to two limits in the model"
        ],
        "source": "research/README.md § Waiting, re-planning, refunds and `close()`; research/README.md § The ledger's time model; ../research/README.md § Call budget; ../architecture.md §2, §6.5",
        "link": "../research/README.md#call-budget"
      },
      "options": [
        {
          "id": "guarded-windows",
          "name": "A final `WindowedCallBudget` by factory and builder: an integer ledger of windows aligned to the epoch, a 1 s boundary guard charging both adjacent windows, reservations planned ahead under a coroutine `Mutex`, strict FIFO per lane, awaited outside the lock, a cancellation refunding and re-planning the waiters behind it",
          "verdict": "accepted",
          "why": "Aligned windows with the guard overspent in 0 of 1,000 skewed trials, at a cost of about 2 % of grants. FIFO held in 600 randomized scripts, and the lock is held for arithmetic only.",
          "source": "../architecture.md §6.5",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "bucket",
          "name": "A token bucket, GCRA or Bucket4j as the default",
          "verdict": "declined",
          "why": "A greedy bucket doubles at every provider boundary, GCRA paces below what a clock-reset provider allows, and Bucket4j is JVM-only, needs Java 17 and offers no suspending FIFO with refunds, lanes or pauses. Later, it could be another implementation of the port, for multi-process counting.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "sliding-log",
          "name": "A sliding log",
          "verdict": "declined",
          "why": "As safe as the guard, but it idles after a burst through time a clock-reset provider has already refilled, stores a timestamp per grant, and cannot match a UTC-day bill.",
          "source": "../research/call-budget.md F2"
        },
        {
          "id": "backfill",
          "name": "Letting smaller calls pass a large one waiting in the same lane",
          "verdict": "declined",
          "why": "It uses the units a strict queue leaves free, and starves expensive calls.",
          "source": "../research/call-budget.md F3"
        },
        {
          "id": "policy-class",
          "name": "A flat constructor, or a `BudgetPolicy` class holding the numbers",
          "verdict": "declined",
          "why": "Policy numbers are builder properties with defaults, the stack's one rule for a configurable type, so adding one changes no existing signature.",
          "source": "../architecture.md §1; ../research/call-budget.md Considerations 4",
          "link": "../architecture.md#1-requirements"
        },
        {
          "id": "plain-delay",
          "name": "A plain `delay` to the waiter's slot, then a re-check",
          "verdict": "declined",
          "why": "It cannot move a waiter earlier, so a cancelled waiter's slot is wasted.",
          "source": "research/README.md § Waiting, re-planning, refunds and `close()`",
          "link": "research/README.md#waiting-re-planning-refunds-and-close"
        },
        {
          "id": "granting-ticker",
          "name": "A ticker in a budget-owned scope that grants for the waiters",
          "verdict": "declined",
          "why": "It contradicts a budget that owns no scope.",
          "source": "research/README.md § Waiting, re-planning, refunds and `close()`",
          "link": "research/README.md#waiting-re-planning-refunds-and-close"
        },
        {
          "id": "stale-grant",
          "name": "Granting a reservation at its slot whatever the wall clock has done since",
          "verdict": "declined",
          "why": "After a forward jump the queue would be granted at once against periods that have ended, overspending the current window.",
          "source": "plan.md §6.11",
          "link": "plan.md#611-where-this-plan-refines-the-parent-architecture"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "`WindowedCallBudget` was built as a final class with an `internal` constructor, the factory `WindowedCallBudget(windows) { }` and a `@WebToolsDsl` builder of 12 properties, each with its default.",
            "state": "as-planned"
          },
          {
            "text": "It splits into the pure, synchronous `BudgetLedger`, `WindowLedger` and `LedgerCell` and a concurrency shell around one `Mutex`.",
            "state": "as-planned"
          },
          {
            "text": "It owns no thread, scope, timer or dispatcher: a wait runs in the caller's coroutine and the waiter grants itself under the lock.",
            "state": "as-planned"
          },
          {
            "text": "Counts live in integer cells keyed by epoch-aligned window index, and a grant within the guard of a boundary, 1 s by default, is charged to both adjacent windows, as `WindowedCallBudgetGuardComponentTest` holds through the budget.",
            "state": "as-planned"
          },
          {
            "text": "A call that would wait is planned at the earliest slot behind its lane's last waiter, reserved on the cells of that slot under the lock and awaited outside it; `WindowedCallBudgetFifoComponentTest` holds the exact grant instants, none a millisecond early.",
            "state": "as-planned"
          },
          {
            "text": "A waiter selects on its conflated wake channel and the close latch, each wait sliced to at most one minute, and re-reads its plan under the lock after every wake. `select` over `Job.onJoin` and `ReceiveChannel.onReceive` compiled for `js`, so no fallback was needed.",
            "state": "as-planned",
            "ref": "final-implementation.md#4-measurements",
            "excerpt": {
              "ref": "final-implementation.md#4-measurements",
              "heading": "4. Measurements",
              "paragraph": "Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other module's suite.",
              "hash": "73ab66970463"
            }
          },
          {
            "text": "A cancelled wait is refunded and the waiters behind it are re-planned from a `NonCancellable` cleanup, and a waiter whose new wait exceeds its cap fails at once with `BudgetExhausted`.",
            "state": "as-planned"
          },
          {
            "text": "A reservation whose period the wall clock has left behind is never granted against it: the first lock section that reads the new wall re-plans every waiter in arrival order, as `WindowedCallBudgetClockStepComponentTest` holds for a forward jump with a queue and a waiter late across a boundary.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A pure, synchronous ledger keeps the arithmetic tabled with plain calls, and a thin shell around it keeps every wait in the caller's coroutine.",
        "statements": [
          {
            "text": "`WindowedCallBudget` is a final class with an `internal` constructor, a factory and a `@WebToolsDsl` builder, every policy number a property with a default.",
            "ref": "plan.md#r8-windowedcallbudget--construction",
            "excerpt": {
              "ref": "plan.md#r8-windowedcallbudget--construction",
              "heading": "R8. `WindowedCallBudget` — construction",
              "paragraph": "A final class with an `internal` constructor; the factory `public fun WindowedCallBudget(windows: List<RateWindow>, configure: WindowedCallBudget.Builder.() -> Unit = {}): WindowedCallBudget`; `@WebToolsDsl public class Builder internal constructor()` with",
              "hash": "4a9bb1439e8f"
            }
          },
          {
            "text": "It splits into a pure, synchronous `internal` ledger and a concurrency shell around one `Mutex`.",
            "ref": "plan.md#5-approach-and-staging",
            "excerpt": {
              "ref": "plan.md#5-approach-and-staging",
              "heading": "5. Approach and staging",
              "paragraph": "The port, the vocabulary, the parser, the seams and the default are plain common code with no platform content: the whole unit is `commonMain` except the two fixtures. The default is split into a pure, synchronous ledger (cells, the slot search, the pause, the restore, the snapshots; no coroutine, so Level 4a deterministic tables it on the JVM-free `commonTest`) and a concurrency shell (`WindowedCallBudget`: the lock, the waiters, the latch, the store calls, the events). The unit lands in stages, each leaving the build green (§11).",
              "hash": "c061e2a820c1"
            }
          },
          {
            "text": "It owns no thread, scope, timer or dispatcher: a wait runs in the caller's coroutine and a waiter grants itself.",
            "ref": "plan.md#69-error-handling-of-every-new-signature-thread-safety-and-resources",
            "excerpt": {
              "ref": "plan.md#69-error-handling-of-every-new-signature-thread-safety-and-resources",
              "heading": "6.9 Error handling of every new signature, thread safety and resources",
              "paragraph": "The `try`/`catch` sites where a `CancellationException` must be told from other throwables, or a `Throwable` converted to a value, are `storeCall` and the observer call (`catch (e: Exception)`, since `onEvent` is not suspend and a `CancellationException` out of it cannot be the caller's cancellation). Everything else uses `fold`, `flatMap`, `onSuccess`/`onFailure` and `catchNonFatal`. Cancellation is always thrown, never returned: the caller's own (from `ensureActive()` at the top of `acquire`, from a contended `lock`, from `withTimeoutOrNull` or `select` while waiting, or from a store call) …",
              "hash": "5392bbb63f0f"
            }
          },
          {
            "text": "Counts live in integer cells keyed by epoch-aligned window index, and a grant within the guard of a boundary, 1 s by default, is charged to both adjacent windows.",
            "ref": "plan.md#r9-windowedcallbudget--the-ledger",
            "excerpt": {
              "ref": "plan.md#r9-windowedcallbudget--the-ledger",
              "heading": "R9. `WindowedCallBudget` — the ledger",
              "paragraph": "An integer ledger of epoch-aligned fixed windows (§6.3 has the arithmetic):",
              "hash": "73ebe6799273"
            }
          },
          {
            "text": "A call that would wait is planned ahead under the lock, first come first served within its lane, charged to the cells of its slot and awaited outside the lock.",
            "ref": "plan.md#r9-windowedcallbudget--the-ledger",
            "excerpt": {
              "ref": "plan.md#r9-windowedcallbudget--the-ledger",
              "heading": "R9. `WindowedCallBudget` — the ledger",
              "paragraph": "An integer ledger of epoch-aligned fixed windows (§6.3 has the arithmetic):",
              "hash": "73ebe6799273"
            }
          },
          {
            "text": "A waiter waits on a conflated wake signal, each wait sliced to at most one minute, and re-reads its plan under the lock after every wake, so a lost, stale or double wake costs one lock section.",
            "ref": "plan.md#r10-windowedcallbudget--waiting-refunds-and-close",
            "excerpt": {
              "ref": "plan.md#r10-windowedcallbudget--waiting-refunds-and-close",
              "heading": "R10. `WindowedCallBudget` — waiting, refunds and `close()`",
              "paragraph": "A waiter owns a conflated `Channel<Unit>` wake signal and waits in `withTimeoutOrNull(min(remaining, 1 minute)) { select { closing.onJoin { }; wake.onReceive { } } }`. After every wake or timeout it re-reads its plan under the lock. A re-plan signals only waiters moved earlier or failed. A waiter grants itself under the lock, after `currentCoroutineContext().ensureActive()` and before any mutation. · A cancellation refunds the waiter's reservation and re-plans the waiters behind it, in `finally { withContext(NonCancellable) { lock { if still waiting: unlink, re-plan } } }`. A waiter whose new …",
              "hash": "b06dd085157b"
            }
          },
          {
            "text": "A cancelled wait is refunded and the waiters behind it are re-planned from a `NonCancellable` cleanup, and a waiter whose new wait exceeds its cap fails at once with `BudgetExhausted`.",
            "ref": "plan.md#r10-windowedcallbudget--waiting-refunds-and-close",
            "excerpt": {
              "ref": "plan.md#r10-windowedcallbudget--waiting-refunds-and-close",
              "heading": "R10. `WindowedCallBudget` — waiting, refunds and `close()`",
              "paragraph": "A waiter owns a conflated `Channel<Unit>` wake signal and waits in `withTimeoutOrNull(min(remaining, 1 minute)) { select { closing.onJoin { }; wake.onReceive { } } }`. After every wake or timeout it re-reads its plan under the lock. A re-plan signals only waiters moved earlier or failed. A waiter grants itself under the lock, after `currentCoroutineContext().ensureActive()` and before any mutation. · A cancellation refunds the waiter's reservation and re-plans the waiters behind it, in `finally { withContext(NonCancellable) { lock { if still waiting: unlink, re-plan } } }`. A waiter whose new …",
              "hash": "b06dd085157b"
            }
          },
          {
            "text": "A reservation whose period the wall clock has left behind is never granted against it: every waiter is re-planned from the current reading, in arrival order.",
            "ref": "plan.md#r12-windowedcallbudget--time",
            "excerpt": {
              "ref": "plan.md#r12-windowedcallbudget--time",
              "heading": "R12. `WindowedCallBudget` — time",
              "paragraph": "The wall clock decides where in the provider's calendar now is: window indices, starts and ends, the guard test, the snapshot's stamp, the `maxRestoreSkew` check and elapsed fractions. It is read fresh at every lock section and used as read (no anchor, no clamp). The monotonic `TimeSource` measures every duration: each `delay`, the `maxWait` deadline, a grant's measured wait, the in-process end of a pause, `storeTimeout` and `storeRetryInterval`; it is read through one mark taken at construction and made non-decreasing. Both are read together into one `internal` value, and again when a store …",
              "hash": "c2313992ed18"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write the pure ledger: the cells, the windows, the slot search and the plans, with their Level 4a tables and the seeded property test.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/LedgerCell.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowLedger.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/BudgetLedger.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/BudgetLedgerPlanDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S4",
            "what": "Write the shell: the builder and factory, `acquire`, the waiters, the re-plans and the refunds, on `VirtualClock`.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowedCallBudget.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetFifoComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4a (deterministic) `WindowArithmeticDeterministicTest`, `BudgetLedgerPlanDeterministicTest` and `BudgetLedgerPropertyDeterministicTest`; Level 2 (component) `WindowedCallBudgetConstructionComponentTest`, `WindowedCallBudgetFifoComponentTest`, `WindowedCallBudgetRefundComponentTest` and `WindowedCallBudgetGuardComponentTest`"
        ],
        "risks": [
          "Each parked waiter wakes at least once a minute and a re-plan is O(waiters): at `maxQueued` 1,000 on two lanes the Level 4c non-functional test bounds one round, and Assumption 2's optimisation applies if it exceeds the bound (§10)",
          "A slow store holds the lock, so every call queues behind it for up to `storeTimeout` (§10)"
        ],
        "source": "plan.md R8, R9, R10, §5, §6.3 to §6.5",
        "link": "plan.md#r9-windowedcallbudget--the-ledger"
      }
    },
    {
      "id": "lanes",
      "group": "Code",
      "name": "Lane shares",
      "system": "webtools-http-budget",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "With one queue, a background backlog decides what an interactive call waits: behind 600 queued tracker calls, all 16 interactive calls were refused, with waits of 595 s falling to 580 s.",
        "evidence": [
          "../research/call-budget.md F5 — the backlog measured with and without a share, starvation and rounding"
        ],
        "source": "../research/README.md § Call budget; ../architecture.md §6.5",
        "link": "../research/README.md#call-budget"
      },
      "options": [
        {
          "id": "held-floor",
          "name": "A lane's share as a floor held back per window, never lent: `laneShares`, default `Interactive` 20 %, rounded up to at least 1",
          "verdict": "accepted",
          "why": "With a 20 % share 12 of the 16 were granted at 0 ms and the rest refused with the real wait. The cost is the share itself: the tracker got 48 of 60 per minute beside an idle share.",
          "source": "../architecture.md §6.5",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "lend-reserve",
          "name": "Lending unused lane reserve",
          "verdict": "declined",
          "why": "In a plan-ahead ledger it drove interactive grants to zero (measured).",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "preemption",
          "name": "Preempting slots already planned for another lane",
          "verdict": "declined",
          "why": "A FIFO plan-ahead ledger cannot offer it.",
          "source": "../research/call-budget.md Considerations 3"
        },
        {
          "id": "shared-queue-bound",
          "name": "One `maxQueued` shared by every lane",
          "verdict": "declined",
          "why": "One lane's backlog would refuse another lane's calls.",
          "source": "plan.md §6.11",
          "link": "plan.md#611-where-this-plan-refines-the-parent-architecture"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "A lane's share converts to `ceil(share * limit)` reserved units per window, at least 1, held back as a floor and never lent, as `WindowedCallBudgetLaneFloorComponentTest` holds.",
            "state": "as-planned"
          },
          {
            "text": "A call fits a window cell only when `used + cost` plus the unused reserve of every other lane stays within the limit, and a lane may use the whole window itself.",
            "state": "as-planned"
          },
          {
            "text": "The shell checks capacity before each plan: a cost above the limit less the other lanes' reserves is refused at once with `CostExceedsLimit` and `Duration.INFINITE`.",
            "state": "as-planned"
          },
          {
            "text": "`maxQueued`, 1,000 by default, applies to each lane and counts only calls that would wait; a full lane refuses with `QueueFull` and the wait the call would have had.",
            "state": "as-planned"
          },
          {
            "text": "`laneShares` defaults to `Interactive` 0.2, and the factory refuses shares whose reserved units do not fit every window.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A floor held back per window keeps an interactive call out from behind a background backlog, at the cost of the share itself.",
        "statements": [
          {
            "text": "A lane's share converts to reserved units per window, `ceil(share * limit)` and at least 1 for a positive share, and the reserve is a floor held back for it, never lent.",
            "ref": "plan.md#r9-windowedcallbudget--the-ledger",
            "excerpt": {
              "ref": "plan.md#r9-windowedcallbudget--the-ledger",
              "heading": "R9. `WindowedCallBudget` — the ledger",
              "paragraph": "An integer ledger of epoch-aligned fixed windows (§6.3 has the arithmetic):",
              "hash": "73ebe6799273"
            }
          },
          {
            "text": "A call fits a window cell only when `used + cost` plus the unused reserve of every other lane stays within the limit, and a lane may use the whole window itself.",
            "ref": "plan.md#r9-windowedcallbudget--the-ledger",
            "excerpt": {
              "ref": "plan.md#r9-windowedcallbudget--the-ledger",
              "heading": "R9. `WindowedCallBudget` — the ledger",
              "paragraph": "An integer ledger of epoch-aligned fixed windows (§6.3 has the arithmetic):",
              "hash": "73ebe6799273"
            }
          },
          {
            "text": "A call whose cost exceeds the limit less the other lanes' reserves can never fit, and is refused with `CostExceedsLimit` and `Duration.INFINITE`.",
            "ref": "plan.md#r9-windowedcallbudget--the-ledger",
            "excerpt": {
              "ref": "plan.md#r9-windowedcallbudget--the-ledger",
              "heading": "R9. `WindowedCallBudget` — the ledger",
              "paragraph": "An integer ledger of epoch-aligned fixed windows (§6.3 has the arithmetic):",
              "hash": "73ebe6799273"
            }
          },
          {
            "text": "`maxQueued` applies to each lane and counts only calls that would wait; a full lane refuses with `QueueFull` and the wait the call would have had.",
            "ref": "plan.md#r9-windowedcallbudget--the-ledger",
            "excerpt": {
              "ref": "plan.md#r9-windowedcallbudget--the-ledger",
              "heading": "R9. `WindowedCallBudget` — the ledger",
              "paragraph": "An integer ledger of epoch-aligned fixed windows (§6.3 has the arithmetic):",
              "hash": "73ebe6799273"
            }
          },
          {
            "text": "`laneShares` defaults to `Interactive` 0.2, and at construction the reserved units of all lanes must fit every window.",
            "ref": "plan.md#r8-windowedcallbudget--construction",
            "excerpt": {
              "ref": "plan.md#r8-windowedcallbudget--construction",
              "heading": "R8. `WindowedCallBudget` — construction",
              "paragraph": "A final class with an `internal` constructor; the factory `public fun WindowedCallBudget(windows: List<RateWindow>, configure: WindowedCallBudget.Builder.() -> Unit = {}): WindowedCallBudget`; `@WebToolsDsl public class Builder internal constructor()` with",
              "hash": "4a9bb1439e8f"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write the share rounding and the fit test in the ledger.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/BudgetLedger.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/ShareRoundingDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S4",
            "what": "Validate the shares in the builder and hold the floors through the budget.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowedCallBudget.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetLaneFloorComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4a (deterministic) `ShareRoundingDeterministicTest`; Level 2 (component) `WindowedCallBudgetLaneFloorComponentTest`"
        ],
        "risks": [
          "The default `Interactive` 0.2 reserves the only unit of a window of limit 1, so any other lane's call there is `CostExceedsLimit`; the builder's KDoc and the construction test say so (§10)",
          "`maxQueued` bounds each lane, not the budget: a consumer with many lanes can queue many calls (§10)"
        ],
        "source": "plan.md R8, R9, §6.3",
        "link": "plan.md#r9-windowedcallbudget--the-ledger"
      }
    },
    {
      "id": "pauses",
      "group": "Code",
      "name": "Server signals: raising usage and pausing",
      "system": "webtools-http-budget",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing stops a budget once a server throttles it, and the providers count the refused requests too. Local usage cannot see other processes behind the same IP, so what the server reports has nowhere to land.",
        "evidence": [
          "../research/call-budget.md F1 — refused requests count",
          "../research/call-budget.md F6 — the reconciliation rules as probed, and GitHub's and Discord's terms on continuing while limited",
          "common/UpstreamFailure.kt:38 — `RateLimited`, which unit 9's facades return with its wait",
          "research/ledger-time.md §6.2, §6.3 — a pause outlives the process and is read against two clocks: a backward step must not lengthen it, and a resume from sleep must not leave it running"
        ],
        "source": "research/README.md § The ledger's time model; ../research/README.md § Call budget; ../architecture.md §6.5",
        "link": "../research/README.md#call-budget"
      },
      "options": [
        {
          "id": "max-of-hints",
          "name": "A server's remaining count only raises local usage; the pause is the maximum of every hint, never shortens, covers every lane and is capped by `maxPause`",
          "verdict": "accepted",
          "why": "400 randomized scripts found no grant inside a pause created by an earlier `429`, over 19,463 grants and 4,826 pauses. Waiters are re-planned behind the pause, and refused at once if the new wait exceeds their cap.",
          "source": "../architecture.md §6.5",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "shorter-reset",
          "name": "Following the shorter bucket reset, as Kord does",
          "verdict": "declined",
          "why": "Kord issue #1074: it ignored a `Retry-After` of about 600 s and retried every 10 s.",
          "source": "../research/call-budget.md F6"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "`complete(signals)` feeds each reading to the ledger, which only raises usage to `max(local, published - remaining)` for the cell the permit was charged to and ignores a rolled window or an unknown name, as `WindowedCallBudgetReconcileComponentTest` holds.",
            "state": "as-planned"
          },
          {
            "text": "A throttled signal pauses the budget until the maximum of `Retry-After` and each exhausted reading's `resetIn`, or with no such hint until the next boundary plus the guard; `WindowedCallBudgetPauseComponentTest` holds each hint and `Paused` carries the winning source.",
            "state": "as-planned"
          },
          {
            "text": "Each hint is capped by `maxPause`, 24 h by default, a later hint never shortens a pause, and the pause covers every lane.",
            "state": "as-planned"
          },
          {
            "text": "Waiters are re-planned behind a pause, and one whose new wait exceeds its cap fails at once with `BudgetExhausted` and the `Paused` reason.",
            "state": "as-planned"
          },
          {
            "text": "A pause carries an elapsed deadline and an epoch deadline and ends when either passes, as `WindowedCallBudgetClockPauseComponentTest` holds across wall-clock steps in both directions.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A server's word only ever makes the budget more careful: usage rises and a pause lengthens, and neither is ever undone by a later signal.",
        "statements": [
          {
            "text": "A reading's `remaining` only raises local usage, to `max(local, published - remaining)` for the cell the permit was charged to; a reading for a window that has rolled, or of an unknown name, is ignored.",
            "ref": "plan.md#r11-windowedcallbudget--signals-and-pauses",
            "excerpt": {
              "ref": "plan.md#r11-windowedcallbudget--signals-and-pauses",
              "heading": "R11. `WindowedCallBudget` — signals and pauses",
              "paragraph": "A reading's `remaining` only raises local usage: for the window cell the permit was charged to, usage becomes `max(local, published - remaining)`; a reading for a window that has since rolled is ignored; a larger `remaining` never lowers usage. A reading for an unknown window name is ignored. · A throttled signal pauses the budget until the maximum of every hint: `Retry-After`; every reading with `remaining <= 0` and a `resetIn`; and, when there is no such hint, the next boundary of the exhausted windows (those a reading names with `remaining <= 0` or whose current cell is full), or of the …",
              "hash": "03f364965f86"
            }
          },
          {
            "text": "A throttled signal pauses the budget until the maximum of `Retry-After` and each exhausted reading's `resetIn`; with no such hint, until the next boundary of the exhausted windows, or of the shortest window, plus the guard.",
            "ref": "plan.md#r11-windowedcallbudget--signals-and-pauses",
            "excerpt": {
              "ref": "plan.md#r11-windowedcallbudget--signals-and-pauses",
              "heading": "R11. `WindowedCallBudget` — signals and pauses",
              "paragraph": "A reading's `remaining` only raises local usage: for the window cell the permit was charged to, usage becomes `max(local, published - remaining)`; a reading for a window that has since rolled is ignored; a larger `remaining` never lowers usage. A reading for an unknown window name is ignored. · A throttled signal pauses the budget until the maximum of every hint: `Retry-After`; every reading with `remaining <= 0` and a `resetIn`; and, when there is no such hint, the next boundary of the exhausted windows (those a reading names with `remaining <= 0` or whose current cell is full), or of the …",
              "hash": "03f364965f86"
            }
          },
          {
            "text": "Each hint is capped by `maxPause`, a later hint never shortens a pause, and the pause covers every lane.",
            "ref": "plan.md#r11-windowedcallbudget--signals-and-pauses",
            "excerpt": {
              "ref": "plan.md#r11-windowedcallbudget--signals-and-pauses",
              "heading": "R11. `WindowedCallBudget` — signals and pauses",
              "paragraph": "A reading's `remaining` only raises local usage: for the window cell the permit was charged to, usage becomes `max(local, published - remaining)`; a reading for a window that has since rolled is ignored; a larger `remaining` never lowers usage. A reading for an unknown window name is ignored. · A throttled signal pauses the budget until the maximum of every hint: `Retry-After`; every reading with `remaining <= 0` and a `resetIn`; and, when there is no such hint, the next boundary of the exhausted windows (those a reading names with `remaining <= 0` or whose current cell is full), or of the …",
              "hash": "03f364965f86"
            }
          },
          {
            "text": "Waiters are re-planned behind a pause, and fail at once if their new wait exceeds their cap.",
            "ref": "plan.md#r11-windowedcallbudget--signals-and-pauses",
            "excerpt": {
              "ref": "plan.md#r11-windowedcallbudget--signals-and-pauses",
              "heading": "R11. `WindowedCallBudget` — signals and pauses",
              "paragraph": "A reading's `remaining` only raises local usage: for the window cell the permit was charged to, usage becomes `max(local, published - remaining)`; a reading for a window that has since rolled is ignored; a larger `remaining` never lowers usage. A reading for an unknown window name is ignored. · A throttled signal pauses the budget until the maximum of every hint: `Retry-After`; every reading with `remaining <= 0` and a `resetIn`; and, when there is no such hint, the next boundary of the exhausted windows (those a reading names with `remaining <= 0` or whose current cell is full), or of the …",
              "hash": "03f364965f86"
            }
          },
          {
            "text": "A pause carries an elapsed deadline and an epoch deadline, and ends when either passes.",
            "ref": "plan.md#r12-windowedcallbudget--time",
            "excerpt": {
              "ref": "plan.md#r12-windowedcallbudget--time",
              "heading": "R12. `WindowedCallBudget` — time",
              "paragraph": "The wall clock decides where in the provider's calendar now is: window indices, starts and ends, the guard test, the snapshot's stamp, the `maxRestoreSkew` check and elapsed fractions. It is read fresh at every lock section and used as read (no anchor, no clamp). The monotonic `TimeSource` measures every duration: each `delay`, the `maxWait` deadline, a grant's measured wait, the in-process end of a pause, `storeTimeout` and `storeRetryInterval`; it is read through one mark taken at construction and made non-decreasing. Both are read together into one `internal` value, and again when a store …",
              "hash": "c2313992ed18"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write the pause and the hints in the ledger.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/LedgerTime.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/BudgetLedger.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/PauseDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S4",
            "what": "Read signals in `complete(signals)` and re-plan the waiters behind a pause.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowedCallBudget.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetPauseComponentTest.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetReconcileComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4a (deterministic) `PauseDeterministicTest`; Level 2 (component) `WindowedCallBudgetPauseComponentTest`, `WindowedCallBudgetReconcileComponentTest` and `WindowedCallBudgetClockPauseComponentTest`"
        ],
        "risks": [
          "`Standard` uses a parseable `Date` as the reference whatever its distance from now; `maxPause` bounds the effect of a broken pair (§10)"
        ],
        "source": "plan.md R11, R12, §6.6",
        "link": "plan.md#r11-windowedcallbudget--signals-and-pauses"
      }
    },
    {
      "id": "hard-stop",
      "group": "Code",
      "name": "The hard daily stop, `BudgetStore`, `InMemoryBudgetStore` and the snapshots",
      "system": "webtools-http-budget",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "A daily bill outlives the process and a ledger in memory does not: a fresh budget granted straight away after the 6th call of a 5-per-day window was refused. That is the billing hole a restart opens. This unit's research left one choice to the plan, decided there: a snapshot stamped further ahead than `maxRestoreSkew` keeps its window counts as cells at their own indices, and only its stamp and pause are ignored.",
        "evidence": [
          "../research/call-budget.md F7 — the hole, the restore rules and the write-ahead high-water mark, crash-simulated",
          "research/waits-and-close.md F6 — a store call under the lock: a timeout's result can be dropped, so a timed-out save counts as not persisted, and a store that blocks a thread keeps the lock for as long as it blocks",
          "research/waits-and-close.md F9 — retrying a failed restore at a bounded rate",
          "research/README.md § The restore's merge — a late restore after a store outage has grants of its own to add to the stored count",
          "research/ledger-time.md §7.2; research/README.md § A backward clock step, and a snapshot stamped ahead — the snapshot stamped further ahead than `maxRestoreSkew` keeps its window counts; only its stamp and pause are ignored"
        ],
        "source": "research/README.md § Waiting, re-planning, refunds and `close()`; research/README.md § The ledger's time model; research/README.md § The restore's merge; research/README.md § A backward clock step, and a snapshot stamped ahead; ../research/README.md § Call budget; ../architecture.md §6.5",
        "link": "research/README.md#a-backward-clock-step-and-a-snapshot-stamped-ahead",
        "detail": "What each option costs. Ignoring the snapshot entirely: when the clock at start is behind, the stored day's count is lost and the bill is under-counted. Ignoring only its stamp and pause: a snapshot written by a clock that ran far ahead leaves a future cell that can over-count one far-off window, bounded by the cell cap. This column adopts neither; `research/README.md` § Open trade-offs holds the research's recommendation."
      },
      "options": [
        {
          "id": "write-ahead",
          "name": "A `hard` window refusing until its boundary, over a gated suspend `BudgetStore` of plain `BudgetSnapshot` values, written ahead in blocks of `persistAhead` and restored once at the first `acquire`, with `InMemoryBudgetStore` the default",
          "verdict": "partial",
          "why": "Adopted in part: the hard window, the gated store of plain snapshots, the blocks of `persistAhead` and the in-memory default are kept, as a crash can only over-count, by at most `persistAhead - 1`: no under-count in 800 randomized crashes, at about 0.1 writes per grant at 10. Not kept: a restore at the first `acquire` alone, `max(local, saved)` for a late restore, and ignoring a snapshot stamped further ahead than `maxRestoreSkew` whole.",
          "source": "../architecture.md §6.5; plan.md §6.11",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "sliding-day",
          "name": "A rolling 24 h window for the daily limit",
          "verdict": "declined",
          "why": "The bill is per UTC day: a rolling window refuses earlier than the bill requires and cannot be persisted as one integer.",
          "source": "../research/call-budget.md F2"
        },
        {
          "id": "write-ahead-counts-kept",
          "name": "The same store and write-ahead, restored at the first of `acquire`, `utilisation()` and `flush()`, merged as `max(saved + grantsSinceStart, reconciled)`, retried at a bounded rate when it fails, and keeping the window counts of a snapshot stamped further ahead than `maxRestoreSkew`, only its stamp and pause ignored",
          "verdict": "accepted",
          "why": "A restart never under-counts when the clock at start is behind, and a snapshot written by a clock far ahead leaves at most a far-off future cell, bounded by the cell cap. A late restore after a store outage adds the grants made since the start rather than losing them.",
          "source": "research/README.md § A backward clock step, and a snapshot stamped ahead; plan.md §6.7, §6.11",
          "link": "research/README.md#a-backward-clock-step-and-a-snapshot-stamped-ahead"
        },
        {
          "id": "skew-ignores-whole",
          "name": "Ignoring a snapshot stamped further ahead than `maxRestoreSkew` entirely, as #55's architecture §6.5 states",
          "verdict": "declined",
          "why": "It would lose the stored day's count when the clock at start is behind, a host starting from its hardware clock before it synchronises, and so reopen the billing hole.",
          "source": "research/README.md § A backward clock step, and a snapshot stamped ahead; plan.md §6.11",
          "link": "research/README.md#a-backward-clock-step-and-a-snapshot-stamped-ahead"
        },
        {
          "id": "refuse-store-down",
          "name": "Refusing grants while the store is down",
          "verdict": "declined",
          "why": "It would turn a store outage into a provider outage; the in-process stop holds, and the loss after a crash is bounded by the grants of one interval.",
          "source": "plan.md §10",
          "link": "plan.md#10-risks-and-edge-cases"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": "",
        "statements": [
          {
            "text": "A `hard` window is persisted write-ahead and raises `BudgetEvent.HardStop` once per window and index; a window that is not hard is never saved, as `WindowedCallBudgetHardWindowComponentTest` holds.",
            "state": "as-planned"
          },
          {
            "text": "`BudgetStore` is a plain gated suspend interface of `load` and `save` whose KDoc requires a cancellable implementation; `InMemoryBudgetStore` is the default and the worked example, and `BudgetSnapshot` and `WindowSnapshot` are plain values with no serializer.",
            "state": "as-planned"
          },
          {
            "text": "Before a grant passes the persisted mark the budget saves `used + cost + persistAhead - 1`, capped at the limit, under the lock and before the grant's mutation; a save that outlasts the period is written again for the cell then current, at most three times in all, and the grant proceeds. `exactSnapshot` also writes a row of zero for a hard cell whose persisted mark is above its count, so a `flush()` after every grant was released lowers the mark through that row and the next grant is written ahead again. `WindowedCallBudgetCrashComponentTest` shows a crash over-counting by at most `persistAhead - 1` and never under-counting, that case included.",
            "state": "amended",
            "ref": "final-implementation.md#d7-budgetledgerexactsnapshot-writes-a-row-of-zero-for-a-hard-cell-whose-persisted-mark-is-above-its-count",
            "excerpt": {
              "ref": "final-implementation.md#d7-budgetledgerexactsnapshot-writes-a-row-of-zero-for-a-hard-cell-whose-persisted-mark-is-above-its-count",
              "heading": "D7. `BudgetLedger.exactSnapshot` writes a row of zero for a hard cell whose persisted mark is above its count",
              "paragraph": "Plan: §7.14, `BudgetLedger.exactSnapshot` (\"the exact effective count of every hard window cell at or above the current index\"), whose listing writes a `WindowSnapshot` only for a cell with `effective > 0`. · As built: `exactSnapshot` also writes a cell whose `effective` is 0 when its `persisted` mark is above 0, as `WindowSnapshot(window, start, 0)`; the unchanged `markPersisted` then lowers the mark to 0 through that row. A cell with neither a count nor a mark is still omitted. No public signature and no line of the dump changes (`WindowSnapshot.used` already admits 0). · Why: the code …",
              "hash": "c14982d85be7"
            }
          },
          {
            "text": "A failed or timed-out save never advances the mark and defers the next attempt by `storeRetryInterval`, while the in-process stop holds, as `WindowedCallBudgetStoreFailureComponentTest` holds. An `Error` a store throws from `save` or `load` propagates to the caller, never wrapped and with no `StoreFailed` event, and leaves the budget usable; with assertions enabled it arrives as the stack-trace-recovery copy `withTimeoutOrNull` makes, whose `cause` is the thrown instance.",
            "state": "as-planned",
            "ref": "final-implementation.md#4-measurements",
            "excerpt": {
              "ref": "final-implementation.md#4-measurements",
              "heading": "4. Measurements",
              "paragraph": "Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other module's suite.",
              "hash": "73ab66970463"
            }
          },
          {
            "text": "The restore runs once, at the first of `acquire`, `utilisation()` and `flush()`: a stale window is dropped, the current index merges additively and a future index stays a cell of its own.",
            "state": "as-planned"
          },
          {
            "text": "A snapshot stamped further ahead than `maxRestoreSkew`, 5 minutes by default, keeps its window counts with its stamp and pause ignored, and `Restored` reports it as ignored for skew.",
            "state": "as-planned"
          },
          {
            "text": "A `load()` that fails leaves the budget granting from its in-process count, retried from a later `acquire` only after `storeRetryInterval`, and a retried restore that merges counts re-plans the waiters; `flush()` retries at once and returns the failure without saving while no restore has succeeded, as `WindowedCallBudgetRestoreComponentTest` holds.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "`BudgetLedger.exactSnapshot` also writes a hard cell whose count is 0 and whose persisted mark is above 0, as a row of zero, where the plan's listing writes only a cell with a count; `markPersisted`, unchanged, lowers the mark through that row.",
            "why": "The plan's listing broke §6.7's rule that a crash never under-counts: after a write-ahead grant was released, `flush()` omitted the cell while its mark stayed, so up to `persistAhead - 1` later grants saved nothing and a crash restored below the truth. The zero row keeps one code path and leaves the store and the ledger agreeing about which cells exist. `BudgetLedger` is internal, so the ABI dump is unchanged.",
            "when": "qa pass 1 fixes",
            "ref": "final-implementation.md#d7-budgetledgerexactsnapshot-writes-a-row-of-zero-for-a-hard-cell-whose-persisted-mark-is-above-its-count",
            "link": "../architecture.md#65-call-budget"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A hard window guards a bill, so a crash may only over-count, and a store outage costs a bounded risk rather than a provider outage.",
        "statements": [
          {
            "text": "A `hard` window is persisted write-ahead and raises `BudgetEvent.HardStop` once per window and index; a window that is not hard is never persisted.",
            "ref": "plan.md#r9-windowedcallbudget--the-ledger",
            "excerpt": {
              "ref": "plan.md#r9-windowedcallbudget--the-ledger",
              "heading": "R9. `WindowedCallBudget` — the ledger",
              "paragraph": "An integer ledger of epoch-aligned fixed windows (§6.3 has the arithmetic):",
              "hash": "73ebe6799273"
            }
          },
          {
            "text": "`BudgetStore` is a plain gated suspend interface of `load` and `save` whose KDoc requires a cancellable implementation; `InMemoryBudgetStore` is the default and the worked example, and `BudgetSnapshot` and `WindowSnapshot` are plain values with no serializer.",
            "ref": "plan.md#r7-the-seams-store-snapshots-observer-events",
            "excerpt": {
              "ref": "plan.md#r7-the-seams-store-snapshots-observer-events",
              "heading": "R7. The seams: store, snapshots, observer, events",
              "paragraph": "`BudgetStore`: `@SubclassOptInRequired` interface; `suspend fun load(): Result<BudgetSnapshot>` (returns `BudgetSnapshot.Empty` when nothing is stored, never a nullable) and `suspend fun save(snapshot: BudgetSnapshot): Result<Unit>`. The KDoc requires a cancellable implementation (the budget bounds each call with `withTimeoutOrNull`, which cannot interrupt blocking code) and states that a save replaces the stored snapshot. · `InMemoryBudgetStore`: a final class with a public no-argument constructor; thread-safe (one `Mutex`); `load()` returns the last saved snapshot or `BudgetSnapshot.Empty`; …",
              "hash": "07c20e83e665"
            }
          },
          {
            "text": "Before a grant passes the persisted mark the budget saves `used + cost + persistAhead - 1`, capped at the limit, under the lock and before the grant's mutation, so a crash over-counts by at most `persistAhead - 1`.",
            "ref": "plan.md#r13-windowedcallbudget--persistence",
            "excerpt": {
              "ref": "plan.md#r13-windowedcallbudget--persistence",
              "heading": "R13. `WindowedCallBudget` — persistence",
              "paragraph": "Write-ahead. Before a grant that would pass a hard window's persisted mark, the budget saves `used + cost + persistAhead - 1` (capped at the limit) for the cell, under the lock and before the grant's mutation, bounded by `storeTimeout`. A failed or timed-out save is logged and ignored, never advances the persisted mark, and defers the next save attempt by `storeRetryInterval`; the in-process stop holds meanwhile. No save is made before a restore has succeeded. · Restore. Runs once, under the lock, at the first of `acquire`, `utilisation()` and `flush()`. A snapshot window below the current …",
              "hash": "7e4652e15bfe"
            }
          },
          {
            "text": "A failed or timed-out save never advances the mark and defers the next attempt by `storeRetryInterval`; the in-process stop holds meanwhile.",
            "ref": "plan.md#r13-windowedcallbudget--persistence",
            "excerpt": {
              "ref": "plan.md#r13-windowedcallbudget--persistence",
              "heading": "R13. `WindowedCallBudget` — persistence",
              "paragraph": "Write-ahead. Before a grant that would pass a hard window's persisted mark, the budget saves `used + cost + persistAhead - 1` (capped at the limit) for the cell, under the lock and before the grant's mutation, bounded by `storeTimeout`. A failed or timed-out save is logged and ignored, never advances the persisted mark, and defers the next save attempt by `storeRetryInterval`; the in-process stop holds meanwhile. No save is made before a restore has succeeded. · Restore. Runs once, under the lock, at the first of `acquire`, `utilisation()` and `flush()`. A snapshot window below the current …",
              "hash": "7e4652e15bfe"
            }
          },
          {
            "text": "The restore runs once, at the first of `acquire`, `utilisation()` and `flush()`: a window below the current index is dropped, the current index merges as `max(saved + grantsSinceStart, reconciled)`, and a future index is kept as a cell of its own.",
            "ref": "plan.md#r13-windowedcallbudget--persistence",
            "excerpt": {
              "ref": "plan.md#r13-windowedcallbudget--persistence",
              "heading": "R13. `WindowedCallBudget` — persistence",
              "paragraph": "Write-ahead. Before a grant that would pass a hard window's persisted mark, the budget saves `used + cost + persistAhead - 1` (capped at the limit) for the cell, under the lock and before the grant's mutation, bounded by `storeTimeout`. A failed or timed-out save is logged and ignored, never advances the persisted mark, and defers the next save attempt by `storeRetryInterval`; the in-process stop holds meanwhile. No save is made before a restore has succeeded. · Restore. Runs once, under the lock, at the first of `acquire`, `utilisation()` and `flush()`. A snapshot window below the current …",
              "hash": "7e4652e15bfe"
            }
          },
          {
            "text": "A snapshot stamped further ahead than `maxRestoreSkew` keeps its window counts; only its stamp and pause are ignored, and `Restored` reports it as ignored for skew.",
            "ref": "plan.md#r13-windowedcallbudget--persistence",
            "excerpt": {
              "ref": "plan.md#r13-windowedcallbudget--persistence",
              "heading": "R13. `WindowedCallBudget` — persistence",
              "paragraph": "Write-ahead. Before a grant that would pass a hard window's persisted mark, the budget saves `used + cost + persistAhead - 1` (capped at the limit) for the cell, under the lock and before the grant's mutation, bounded by `storeTimeout`. A failed or timed-out save is logged and ignored, never advances the persisted mark, and defers the next save attempt by `storeRetryInterval`; the in-process stop holds meanwhile. No save is made before a restore has succeeded. · Restore. Runs once, under the lock, at the first of `acquire`, `utilisation()` and `flush()`. A snapshot window below the current …",
              "hash": "7e4652e15bfe"
            }
          },
          {
            "text": "A `load()` that fails leaves the budget granting from its in-process count, retried from a later `acquire` only after `storeRetryInterval`; `flush()` retries at once, and returns the failure without saving while no restore has succeeded.",
            "ref": "plan.md#r13-windowedcallbudget--persistence",
            "excerpt": {
              "ref": "plan.md#r13-windowedcallbudget--persistence",
              "heading": "R13. `WindowedCallBudget` — persistence",
              "paragraph": "Write-ahead. Before a grant that would pass a hard window's persisted mark, the budget saves `used + cost + persistAhead - 1` (capped at the limit) for the cell, under the lock and before the grant's mutation, bounded by `storeTimeout`. A failed or timed-out save is logged and ignored, never advances the persisted mark, and defers the next save attempt by `storeRetryInterval`; the in-process stop holds meanwhile. No save is made before a restore has succeeded. · Restore. Runs once, under the lock, at the first of `acquire`, `utilisation()` and `flush()`. A snapshot window below the current …",
              "hash": "7e4652e15bfe"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write the store seam, its in-memory default and the snapshot values.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/BudgetStore.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/InMemoryBudgetStore.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/BudgetSnapshot.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowSnapshot.kt"
            ],
            "check": "`:webtools-http:componentTest`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S2",
            "what": "Write the write-ahead, the restore and the snapshots in the ledger.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/BudgetLedger.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/BudgetLedgerSnapshotDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S4",
            "what": "Bound each store call under the lock and test the hard stop, the restore rules and a simulated crash.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/BudgetStoreFailure.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowedCallBudget.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetCrashComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `InMemoryBudgetStoreComponentTest`, `BudgetSnapshotComponentTest`, `WindowedCallBudgetHardWindowComponentTest`, `WindowedCallBudgetRestoreComponentTest`, `WindowedCallBudgetCrashComponentTest` and `WindowedCallBudgetStoreFailureComponentTest`; Level 4a (deterministic) `BudgetLedgerSnapshotDeterministicTest`"
        ],
        "risks": [
          "The lock is held across a store call for up to `storeTimeout`, and a blocking store cannot be timed out at all (§10)",
          "The write-ahead is not exact while the store is down: a crash in that interval restores a count below the truth, bounded by the grants of one interval (§10)"
        ],
        "source": "plan.md R7, R9, R13, §6.7, §6.11",
        "link": "plan.md#r13-windowedcallbudget--persistence"
      }
    },
    {
      "id": "observer",
      "group": "Code",
      "name": "`BudgetObserver`, `BudgetEvent` and the one emission point",
      "system": "webtools-http-budget",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "A pause, a hard stop, a refusal or a store failure would be invisible: common code has only the internal logging seam from #62, and a consumer has no hook to count them.",
        "evidence": [
          "common/Log.kt:17 — `internal expect fun logger`, the common logging seam (baseline)",
          "../research/call-budget.md F11 — the events the default must publish",
          "../research/README.md § Logging in common code — `BudgetObserver` stays a consumer hook, not the logging path",
          "research/waits-and-close.md F1 — `Mutex` is not reentrant, so where events are delivered decides whether an observer calling `utilisation()` from `onEvent` deadlocks"
        ],
        "source": "research/README.md § Waiting, re-planning, refunds and `close()`; ../research/README.md § Logging in common code; ../architecture.md §6.5",
        "link": "../architecture.md#65-call-budget"
      },
      "options": [
        {
          "id": "one-emission",
          "name": "One emission point that logs through the seam and forwards to a plain gated `BudgetObserver`, `None` the default, with the `BudgetEvent` leaves `Granted`, `Refused`, `Paused`, `HardStop`, `Reconciled` and `StoreFailed`",
          "verdict": "partial",
          "why": "Adopted in part: the one emission point and the observer are kept, as the log lines are there whatever a consumer installs and the observer sees the same events. Six leaves are not enough: the restore's outcome has no other carrier.",
          "source": "../architecture.md §6.5; plan.md §6.11",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "observer-logging",
          "name": "Logging through `BudgetObserver` only",
          "verdict": "declined",
          "why": "Silent unless every constructing path installs an observer.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "fun-interface",
          "name": "`BudgetObserver` as a `fun interface`",
          "verdict": "declined",
          "why": "It cannot carry the gate; there is one rule for every seam.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "one-emission-after-unlock",
          "name": "The same point with a seventh leaf, `Restored`, events collected under the lock and emitted after the unlock",
          "verdict": "accepted",
          "why": "`Mutex` is not reentrant: an observer that called back into the budget would deadlock if events were delivered under the lock, and the restore's outcome needs both the log line and the observer.",
          "source": "plan.md §6.11",
          "link": "plan.md#611-where-this-plan-refines-the-parent-architecture"
        },
        {
          "id": "sealed-events",
          "name": "`BudgetEvent` as a `sealed` root",
          "verdict": "declined",
          "why": "The library's set of events grows, so a consumer's `when` keeps an `else`.",
          "source": "plan.md §6.1, §6.11",
          "link": "plan.md#61-mechanism-per-type-architecture-69-and-9-for-this-units-types"
        },
        {
          "id": "enum-vocabulary",
          "name": "`PauseSource` and `StoreOperation` as enums or plain strings",
          "verdict": "declined",
          "why": "An enum makes every added constant a compile error in a consumer's exhaustive `when`, and a plain `String` lets a misspelt comparison compile.",
          "source": "plan.md §6.1",
          "link": "plan.md#61-mechanism-per-type-architecture-69-and-9-for-this-units-types"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "`emit` is the one emission point: it logs each event through the module's logging seam and forwards it to the plain gated `BudgetObserver`, `None` the default.",
            "state": "as-planned"
          },
          {
            "text": "`BudgetEvent` is an open abstract class gated like `HttpFailure`, never `sealed`, with the seven final leaves `Granted`, `Refused`, `Paused`, `HardStop`, `Reconciled`, `StoreFailed` and `Restored`.",
            "state": "as-planned"
          },
          {
            "text": "`Paused.source` is a `PauseSource` and `StoreFailed.operation` a `StoreOperation`, open constants classes like `ExhaustedReason`.",
            "state": "as-planned"
          },
          {
            "text": "Events are collected under the lock and emitted after the unlock, on the thread of the call that produced them; `WindowedCallBudgetObserverComponentTest` shows an observer calling back into the budget with no deadlock.",
            "state": "as-planned"
          },
          {
            "text": "The observer call catches `Exception`, a `CancellationException` included, logs its class name at `WARN`, and lets an `Error` through.",
            "state": "as-planned"
          },
          {
            "text": "`INFO` marks creation, `close()` and a restore, `WARN` a pause, a hard stop and a store failure, and `DEBUG` a refusal and a reconciliation, with no line for a grant; `WindowedCallBudgetLoggingComponentTest` holds the counts, and a store exception's message appears in no line.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One emission point keeps the log lines there whatever a consumer installs, and delivering after the unlock lets an observer call back into a non-reentrant lock.",
        "statements": [
          {
            "text": "One emission point logs through the module's logging seam and forwards to the plain gated `BudgetObserver`, `None` the default.",
            "ref": "plan.md#r14-events-and-logging",
            "excerpt": {
              "ref": "plan.md#r14-events-and-logging",
              "heading": "R14. Events and logging",
              "paragraph": "One emission point logs through the module's logging seam (`logger(BUDGET_LOGGER_NAME)`, `BUDGET_LOGGER_NAME = \"com.spartanlabs.webtools.http.WindowedCallBudget\"`) and forwards to the observer; events are collected under the lock and emitted after the unlock; the observer call catches `Exception` (a `CancellationException` included, since `onEvent` is not suspend) and logs it, and lets an `Error` through. Levels: `INFO` on creation and `close()` (settings numbers only) and on a restore; `WARN` on a pause (until, length, source), a hard stop (once per window and index) and a store failure; …",
              "hash": "8959d97f238c"
            }
          },
          {
            "text": "`BudgetEvent` is an open abstract class gated like `HttpFailure`, never `sealed`, with seven final leaves: `Granted`, `Refused`, `Paused`, `HardStop`, `Reconciled`, `StoreFailed` and `Restored`.",
            "ref": "plan.md#r7-the-seams-store-snapshots-observer-events",
            "excerpt": {
              "ref": "plan.md#r7-the-seams-store-snapshots-observer-events",
              "heading": "R7. The seams: store, snapshots, observer, events",
              "paragraph": "`BudgetStore`: `@SubclassOptInRequired` interface; `suspend fun load(): Result<BudgetSnapshot>` (returns `BudgetSnapshot.Empty` when nothing is stored, never a nullable) and `suspend fun save(snapshot: BudgetSnapshot): Result<Unit>`. The KDoc requires a cancellable implementation (the budget bounds each call with `withTimeoutOrNull`, which cannot interrupt blocking code) and states that a save replaces the stored snapshot. · `InMemoryBudgetStore`: a final class with a public no-argument constructor; thread-safe (one `Mutex`); `load()` returns the last saved snapshot or `BudgetSnapshot.Empty`; …",
              "hash": "07c20e83e665"
            }
          },
          {
            "text": "`Paused.source` is a `PauseSource` and `StoreFailed.operation` a `StoreOperation`, open constants classes like `ExhaustedReason`.",
            "ref": "plan.md#r7-the-seams-store-snapshots-observer-events",
            "excerpt": {
              "ref": "plan.md#r7-the-seams-store-snapshots-observer-events",
              "heading": "R7. The seams: store, snapshots, observer, events",
              "paragraph": "`BudgetStore`: `@SubclassOptInRequired` interface; `suspend fun load(): Result<BudgetSnapshot>` (returns `BudgetSnapshot.Empty` when nothing is stored, never a nullable) and `suspend fun save(snapshot: BudgetSnapshot): Result<Unit>`. The KDoc requires a cancellable implementation (the budget bounds each call with `withTimeoutOrNull`, which cannot interrupt blocking code) and states that a save replaces the stored snapshot. · `InMemoryBudgetStore`: a final class with a public no-argument constructor; thread-safe (one `Mutex`); `load()` returns the last saved snapshot or `BudgetSnapshot.Empty`; …",
              "hash": "07c20e83e665"
            }
          },
          {
            "text": "Events are collected under the lock and emitted after the unlock, on the thread of the call that produced them, so an observer may call back into the budget.",
            "ref": "plan.md#r14-events-and-logging",
            "excerpt": {
              "ref": "plan.md#r14-events-and-logging",
              "heading": "R14. Events and logging",
              "paragraph": "One emission point logs through the module's logging seam (`logger(BUDGET_LOGGER_NAME)`, `BUDGET_LOGGER_NAME = \"com.spartanlabs.webtools.http.WindowedCallBudget\"`) and forwards to the observer; events are collected under the lock and emitted after the unlock; the observer call catches `Exception` (a `CancellationException` included, since `onEvent` is not suspend) and logs it, and lets an `Error` through. Levels: `INFO` on creation and `close()` (settings numbers only) and on a restore; `WARN` on a pause (until, length, source), a hard stop (once per window and index) and a store failure; …",
              "hash": "8959d97f238c"
            }
          },
          {
            "text": "The observer call catches `Exception`, a `CancellationException` included, and logs it, and lets an `Error` through.",
            "ref": "plan.md#r14-events-and-logging",
            "excerpt": {
              "ref": "plan.md#r14-events-and-logging",
              "heading": "R14. Events and logging",
              "paragraph": "One emission point logs through the module's logging seam (`logger(BUDGET_LOGGER_NAME)`, `BUDGET_LOGGER_NAME = \"com.spartanlabs.webtools.http.WindowedCallBudget\"`) and forwards to the observer; events are collected under the lock and emitted after the unlock; the observer call catches `Exception` (a `CancellationException` included, since `onEvent` is not suspend) and logs it, and lets an `Error` through. Levels: `INFO` on creation and `close()` (settings numbers only) and on a restore; `WARN` on a pause (until, length, source), a hard stop (once per window and index) and a store failure; …",
              "hash": "8959d97f238c"
            }
          },
          {
            "text": "`INFO` on creation, `close()` and a restore, `WARN` on a pause, a hard stop and a store failure, `DEBUG` per refusal and reconciliation, no per-call line for a grant, and never a header value, a body, a credential, a URL query or a store exception's message.",
            "ref": "plan.md#r14-events-and-logging",
            "excerpt": {
              "ref": "plan.md#r14-events-and-logging",
              "heading": "R14. Events and logging",
              "paragraph": "One emission point logs through the module's logging seam (`logger(BUDGET_LOGGER_NAME)`, `BUDGET_LOGGER_NAME = \"com.spartanlabs.webtools.http.WindowedCallBudget\"`) and forwards to the observer; events are collected under the lock and emitted after the unlock; the observer call catches `Exception` (a `CancellationException` included, since `onEvent` is not suspend) and logs it, and lets an `Error` through. Levels: `INFO` on creation and `close()` (settings numbers only) and on a restore; `WARN` on a pause (until, length, source), a hard stop (once per window and index) and a store failure; …",
              "hash": "8959d97f238c"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write the observer seam, the events and their two vocabularies.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/BudgetObserver.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/BudgetEvent.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/PauseSource.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/StoreOperation.kt"
            ],
            "check": "`:webtools-http:componentTest`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S4",
            "what": "Write the emission point in the budget, with the log lines.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowedCallBudget.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetObserverComponentTest.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetLoggingComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `BudgetEventComponentTest`, `BudgetVocabularyComponentTest`, `WindowedCallBudgetObserverComponentTest` and `WindowedCallBudgetLoggingComponentTest`"
        ],
        "risks": [
          "A slow observer slows the call that produced the event, after the unlock, so it stalls no other caller; the KDoc says so (§10)"
        ],
        "source": "plan.md R7, R14, §6.10, §6.11",
        "link": "plan.md#r14-events-and-logging"
      }
    },
    {
      "id": "time",
      "group": "Code",
      "name": "Two clocks: `TimeSource` and `WallClock`",
      "system": "webtools-http-budget",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "A budget needs elapsed time for its waits and wall time for its UTC windows. `WallClock` and `VirtualClock` exist at the baseline, and no budget reads them. `WallClock` may step backwards. This unit's research left one choice to the plan, decided there: a backward step across a window boundary copies the sum of the cells above into the current cell, capped at the limit, and the higher cells stay.",
        "evidence": [
          "common/WallClock.kt:13 — the library-owned wall clock (baseline)",
          "webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/VirtualClock.kt:47, :60 — `timeSource` and `wallClock` (baseline)",
          "../research/call-budget.md F10 — the standard library's status and the one fake for all three",
          "common/WallClock.kt:9 — a reading may step backwards when the system clock is adjusted (baseline)",
          "research/ledger-time.md F1, F4 — a wall clock can step by any amount; a monotonic source does not step, and on Linux does not count a suspend",
          "research/ledger-time.md F3 — an injected `TimeSource` need not be monotonic",
          "research/ledger-time.md F5 — a wall clock read once and advanced by the monotonic source drifts at the oscillator's error, 4.3 s a day at 50 ppm, spending the 1 s guard in under six hours",
          "research/ledger-time.md F8, §7.1; research/README.md § A backward clock step, and a snapshot stamped ahead — a backward step across a window boundary copies, capped at the limit",
          "research/README.md § The ledger's time model, Known limit — a clock set forward by mistake and left uncorrected can under-count the provider's day, and no reading in common code tells it from a resume from sleep"
        ],
        "source": "research/README.md § The ledger's time model; research/README.md § A backward clock step, and a snapshot stamped ahead; ../research/README.md § Call budget; ../architecture.md §6.5",
        "link": "research/README.md#a-backward-clock-step-and-a-snapshot-stamped-ahead",
        "detail": "What each option costs. Copy (the current cell gains the sum of the cells above it, capped at the limit; the higher cells stay): when the step corrected a clock that ran ahead, up to one period of calls the provider would have allowed is refused. Move (the counts move down, the higher cells emptied): when the step was the error, up to one limit is under-counted. Keep only (each index keeps its own count): a large backward step grants a fresh window, an overspend of up to one limit. This column adopts none of them; `research/README.md` § Open trade-offs holds the research's recommendation."
      },
      "options": [
        {
          "id": "injected-pair",
          "name": "An injected `kotlin.time.TimeSource` for elapsed time and `WallClock` for wall time, the budget never reading the system clock directly",
          "verdict": "partial",
          "why": "Adopted in part: the pair is kept, shared with the cache ticker and, under `runTest`, with `delay`, so one virtual clock drives all three. It does not say how the two combine, or what a clock step does to a count.",
          "source": "../architecture.md §6.5",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "stdlib-clock",
          "name": "`kotlin.time.Clock`/`Instant` or kotlinx-datetime in signatures",
          "verdict": "declined",
          "why": "An error-level opt-in on every 2.2.x consumer, and the budget needs no calendar arithmetic.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "bare-lambda",
          "name": "A bare `() -> Long`, as `webtools-udp` has for its monotonic clock",
          "verdict": "declined",
          "why": "It loses the name and the KDoc contract.",
          "source": "../research/call-budget.md F10"
        },
        {
          "id": "wall-decides-copy-back",
          "name": "The same pair with the wall clock deciding and the monotonic source measuring, read together once per lock section, cells kept by index, and a backward step across a window boundary copying the sum of the cells above into the current cell, capped at the limit, the higher cells staying",
          "verdict": "accepted",
          "why": "Copying was the only variant with no overspend after both a one-hour and a one-year step in the model, so the provider's day is never under-counted, whichever reading was wrong. The bounded cost: at most one period of calls the provider would have allowed is refused.",
          "source": "research/README.md § A backward clock step, and a snapshot stamped ahead; plan.md §6.2, §6.11",
          "link": "research/README.md#a-backward-clock-step-and-a-snapshot-stamped-ahead"
        },
        {
          "id": "move-down",
          "name": "Moving the higher cells' counts down on a backward step",
          "verdict": "declined",
          "why": "It would under-count by up to one limit when the step itself was the error.",
          "source": "research/README.md § A backward clock step, and a snapshot stamped ahead",
          "link": "research/README.md#a-backward-clock-step-and-a-snapshot-stamped-ahead"
        },
        {
          "id": "keep-own-index",
          "name": "Keeping each index's own count with no fold",
          "verdict": "declined",
          "why": "A large backward step would grant a fresh window.",
          "source": "research/README.md § A backward clock step, and a snapshot stamped ahead",
          "link": "research/README.md#a-backward-clock-step-and-a-snapshot-stamped-ahead"
        },
        {
          "id": "reset-cells",
          "name": "Resetting a cell when the index changes",
          "verdict": "declined",
          "why": "It overspent by up to two limits in the model.",
          "source": "research/README.md § The ledger's time model",
          "link": "research/README.md#the-ledgers-time-model"
        },
        {
          "id": "anchor",
          "name": "An anchor: the wall clock read once and advanced by the monotonic source",
          "verdict": "declined",
          "why": "It drifts at the oscillator's error, 4.3 s a day at 50 ppm, spending the 1 s guard in under six hours, and stops across a suspend on Linux and macOS.",
          "source": "research/README.md § The ledger's time model",
          "link": "research/README.md#the-ledgers-time-model"
        },
        {
          "id": "clamp",
          "name": "A clamp: `max(wall, previous)`",
          "verdict": "declined",
          "why": "It freezes the ledger for as long as the clock was stepped back, so a large backward step becomes a hang.",
          "source": "research/README.md § The ledger's time model",
          "link": "research/README.md#the-ledgers-time-model"
        },
        {
          "id": "step-threshold",
          "name": "A threshold telling a slew from a step",
          "verdict": "declined",
          "why": "It would be a host-dependent number with two code paths, and the index-based rule needs none.",
          "source": "research/README.md § The ledger's time model",
          "link": "research/README.md#the-ledgers-time-model"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "The wall clock decides window indices, starts and ends, the guard test, the snapshot's stamp, the `maxRestoreSkew` check and elapsed fractions.",
            "state": "as-planned"
          },
          {
            "text": "The elapsed reading over the injected `TimeSource` measures the `maxWait` deadline, a grant's measured wait, the in-process end of a pause and `storeRetryInterval`; each wait and `storeTimeout` are `withTimeoutOrNull` bounds in the caller's coroutine, virtual under `runTest`.",
            "state": "as-planned"
          },
          {
            "text": "`LedgerClock` reads both together into one `internal` value at every lock section, and again when a store call returns inside one, with no anchor and no clamp.",
            "state": "as-planned"
          },
          {
            "text": "The budget reads only its `timeSource` and `wallClock`, never the system clock by itself; `TimeSource.Monotonic` and `WallClock.System` are their defaults.",
            "state": "as-planned"
          },
          {
            "text": "A backward step across a window boundary copies the higher cells' sum into the current cell, capped at the limit, once per decrease; `WindowedCallBudgetClockStepComponentTest` shows grants stopping at the limit and a step back of one year hanging no call.",
            "state": "as-planned"
          },
          {
            "text": "Cells are kept by index, never reset by a reading, pruned only below the previous index as the clock moves forward, and capped at 16 per window.",
            "state": "as-planned"
          },
          {
            "text": "A forward jump is trusted and each wait is sliced to at most one minute, so a parked waiter acted on a jump within the slice, in virtual time.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The wall clock is the only reading that tracks the provider's boundary, and the monotonic source the only one fit to measure a wait, so each does the one job.",
        "statements": [
          {
            "text": "The wall clock decides where in the provider's calendar now is: window indices, starts and ends, the guard test, the snapshot's stamp, the `maxRestoreSkew` check and elapsed fractions.",
            "ref": "plan.md#r12-windowedcallbudget--time",
            "excerpt": {
              "ref": "plan.md#r12-windowedcallbudget--time",
              "heading": "R12. `WindowedCallBudget` — time",
              "paragraph": "The wall clock decides where in the provider's calendar now is: window indices, starts and ends, the guard test, the snapshot's stamp, the `maxRestoreSkew` check and elapsed fractions. It is read fresh at every lock section and used as read (no anchor, no clamp). The monotonic `TimeSource` measures every duration: each `delay`, the `maxWait` deadline, a grant's measured wait, the in-process end of a pause, `storeTimeout` and `storeRetryInterval`; it is read through one mark taken at construction and made non-decreasing. Both are read together into one `internal` value, and again when a store …",
              "hash": "c2313992ed18"
            }
          },
          {
            "text": "The monotonic `TimeSource` measures every duration: each `delay`, the `maxWait` deadline, a grant's measured wait, the in-process end of a pause, `storeTimeout` and `storeRetryInterval`.",
            "ref": "plan.md#r12-windowedcallbudget--time",
            "excerpt": {
              "ref": "plan.md#r12-windowedcallbudget--time",
              "heading": "R12. `WindowedCallBudget` — time",
              "paragraph": "The wall clock decides where in the provider's calendar now is: window indices, starts and ends, the guard test, the snapshot's stamp, the `maxRestoreSkew` check and elapsed fractions. It is read fresh at every lock section and used as read (no anchor, no clamp). The monotonic `TimeSource` measures every duration: each `delay`, the `maxWait` deadline, a grant's measured wait, the in-process end of a pause, `storeTimeout` and `storeRetryInterval`; it is read through one mark taken at construction and made non-decreasing. Both are read together into one `internal` value, and again when a store …",
              "hash": "c2313992ed18"
            }
          },
          {
            "text": "Both are read together into one `internal` value at every lock section, and again when a store call returns inside one, with no anchor and no clamp.",
            "ref": "plan.md#r12-windowedcallbudget--time",
            "excerpt": {
              "ref": "plan.md#r12-windowedcallbudget--time",
              "heading": "R12. `WindowedCallBudget` — time",
              "paragraph": "The wall clock decides where in the provider's calendar now is: window indices, starts and ends, the guard test, the snapshot's stamp, the `maxRestoreSkew` check and elapsed fractions. It is read fresh at every lock section and used as read (no anchor, no clamp). The monotonic `TimeSource` measures every duration: each `delay`, the `maxWait` deadline, a grant's measured wait, the in-process end of a pause, `storeTimeout` and `storeRetryInterval`; it is read through one mark taken at construction and made non-decreasing. Both are read together into one `internal` value, and again when a store …",
              "hash": "c2313992ed18"
            }
          },
          {
            "text": "The budget reads only its `timeSource` and `wallClock`, never the system clock by itself.",
            "ref": "plan.md#r8-windowedcallbudget--construction",
            "excerpt": {
              "ref": "plan.md#r8-windowedcallbudget--construction",
              "heading": "R8. `WindowedCallBudget` — construction",
              "paragraph": "A final class with an `internal` constructor; the factory `public fun WindowedCallBudget(windows: List<RateWindow>, configure: WindowedCallBudget.Builder.() -> Unit = {}): WindowedCallBudget`; `@WebToolsDsl public class Builder internal constructor()` with",
              "hash": "4a9bb1439e8f"
            }
          },
          {
            "text": "A backward step across a window boundary copies, once per decrease: the current cell gains the sum of the cells above it, capped at the window's limit, and the higher cells stay.",
            "ref": "plan.md#r12-windowedcallbudget--time",
            "excerpt": {
              "ref": "plan.md#r12-windowedcallbudget--time",
              "heading": "R12. `WindowedCallBudget` — time",
              "paragraph": "The wall clock decides where in the provider's calendar now is: window indices, starts and ends, the guard test, the snapshot's stamp, the `maxRestoreSkew` check and elapsed fractions. It is read fresh at every lock section and used as read (no anchor, no clamp). The monotonic `TimeSource` measures every duration: each `delay`, the `maxWait` deadline, a grant's measured wait, the in-process end of a pause, `storeTimeout` and `storeRetryInterval`; it is read through one mark taken at construction and made non-decreasing. Both are read together into one `internal` value, and again when a store …",
              "hash": "c2313992ed18"
            }
          },
          {
            "text": "Cells are kept by index, never reset by a reading, pruned only below the previous index as the clock moves forward, and capped at 16 per window.",
            "ref": "plan.md#63-the-ledger-cells-the-slot-search-shares-the-fold",
            "excerpt": {
              "ref": "plan.md#63-the-ledger-cells-the-slot-search-shares-the-fold",
              "heading": "6.3 The ledger: cells, the slot search, shares, the fold",
              "paragraph": "Window arithmetic (`WindowLedger`, `periodMillis` the window's period, `guardMillis` the budget's guard): `indexAt(wall) = wall.floorDiv(periodMillis)`, `startOf(index) = index * periodMillis`. `chargeIndices(wall)` is `[k]` plus `k - 1` when `wall - startOf(k) < guardMillis` plus `k + 1` when `startOf(k + 1) - wall <= guardMillis` (with a guard of 0 neither applies; the guard is at most a quarter of the shortest period, so the two bands never meet). `nextCritical(wall)` is `startOf(k) + guardMillis` when `guardMillis > 0` and `wall - startOf(k) < guardMillis` (the end of the after-boundary …",
              "hash": "5db539e1d1a8"
            }
          },
          {
            "text": "A forward jump is trusted, and each wait is sliced to at most one minute, so a step, a jump or a suspend takes effect within a minute.",
            "ref": "plan.md#r12-windowedcallbudget--time",
            "excerpt": {
              "ref": "plan.md#r12-windowedcallbudget--time",
              "heading": "R12. `WindowedCallBudget` — time",
              "paragraph": "The wall clock decides where in the provider's calendar now is: window indices, starts and ends, the guard test, the snapshot's stamp, the `maxRestoreSkew` check and elapsed fractions. It is read fresh at every lock section and used as read (no anchor, no clamp). The monotonic `TimeSource` measures every duration: each `delay`, the `maxWait` deadline, a grant's measured wait, the in-process end of a pause, `storeTimeout` and `storeRetryInterval`; it is read through one mark taken at construction and made non-decreasing. Both are read together into one `internal` value, and again when a store …",
              "hash": "c2313992ed18"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write the clock reading and the fold in the ledger.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/LedgerTime.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowLedger.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/CellFoldDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S4",
            "what": "Run the clock-step cases through the budget, on a wall clock that can step.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowedCallBudget.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/support/webtools/http/SteppableWallClock.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetClockStepComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4a (deterministic) `CellFoldDeterministicTest`; Level 2 (component) `WindowedCallBudgetClockStepComponentTest`, `WindowedCallBudgetClockPauseComponentTest` and `WindowedCallBudgetThreeClocksComponentTest`"
        ],
        "risks": [
          "A clock set forward by mistake and left uncorrected can under-count the provider's day, as no reading in common code tells it from a resume from sleep (§10)",
          "The copy can refuse up to one period of calls the provider would have allowed, after a step that corrected a clock that ran ahead (§10)",
          "The tests step a virtual wall clock; a real clock step or suspend on a host is the one thing only a field run shows (§3)"
        ],
        "source": "plan.md R8, R12, §6.2, §6.3, §6.11",
        "link": "plan.md#r12-windowedcallbudget--time"
      }
    },
    {
      "id": "close",
      "group": "Code",
      "name": "`close()`, `flush()` and `HttpFailure.Closed(\"budget\")`",
      "system": "webtools-http-budget",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "The port's contract is fixed and its mechanism is not designed: a `close()` that does not suspend has to fail waiters parked behind a `Mutex`-based ledger, and how it resumes them is this unit's open point.",
        "evidence": [
          "../architecture.md §13 — the contract, and the mechanism left to this unit's plan, unprobed",
          "../consequences.md — the open point listed for unit 8",
          "common/HttpFailure.kt:43 — `HttpFailure.Closed(component)` (baseline)",
          "research/waits-and-close.md F3 — cancelling a waiter's signal throws `CancellationException` into an active coroutine, indistinguishable by type from the caller's own",
          "../66-response-cache/consequences.md §4 — the budget runs inside the cache's load coroutine, where such an exception reaches every caller of the load as a failure",
          "research/waits-and-close.md F2 — `Mutex.tryLock` in `close()` fails whenever a store call holds the lock"
        ],
        "source": "research/README.md § Waiting, re-planning, refunds and `close()`; ../architecture.md §6.5, §13",
        "link": "research/README.md#waiting-re-planning-refunds-and-close"
      },
      "options": [
        {
          "id": "close-and-flush",
          "name": "A non-suspend, idempotent `close()` that marks the budget closed and fails waiters with `Closed`, and a `suspend flush(): Result<Unit>` for the exact final persist, its default body succeeding",
          "verdict": "partial",
          "why": "Adopted in part: this contract is kept whole, as every long-lived component of the stack is a `kotlin.AutoCloseable` with a non-suspend `close()` and the budget adds `flush()` for what it must write at the end. It leaves open how a `Mutex`-based ledger resumes its waiters, which the accepted option settles.",
          "source": "../architecture.md §6.5, §13",
          "link": "../architecture.md#65-call-budget"
        },
        {
          "id": "suspend-close",
          "name": "A `suspend fun close()`, as the research proposed",
          "verdict": "declined",
          "why": "It would leave the budget the one component that is not `AutoCloseable`.",
          "source": "../research/README.md § Lifecycle and packages"
        },
        {
          "id": "closed-flag",
          "name": "How the ledger resumes its waiters: a closed flag checked under the lock, plus cancellation of the waiters' continuations",
          "verdict": "declined",
          "why": "The point #55's architecture §13 left open, settled by this unit's plan. Cancelling a waiter's signal throws a `CancellationException` into an active coroutine, indistinguishable by type from the caller's own, and in the cache's load coroutine it reaches every caller of the load as a failure; and `close()` cannot reach waiters whose list the lock guards.",
          "source": "research/waits-and-close.md F3; ../architecture.md §13",
          "link": "research/README.md#waiting-re-planning-refunds-and-close"
        },
        {
          "id": "job-latch",
          "name": "That contract through a completed `Job()` latch raced against each waiter's conflated wake signal, `isCompleted` the closed flag, every waiter returning `Closed` as a value",
          "verdict": "accepted",
          "why": "`Job` is thread-safe and `complete()` has one winner, so `close()` takes no lock and runs none of the budget's code on the closer's thread, with no atomic and no `@Volatile`. `Job.onJoin` selects at once for a completed job.",
          "source": "research/README.md § Waiting, re-planning, refunds and `close()`; plan.md §6.5, §6.11",
          "link": "research/README.md#waiting-re-planning-refunds-and-close"
        },
        {
          "id": "deferred-per-wake",
          "name": "A per-wake `CompletableDeferred`",
          "verdict": "declined",
          "why": "It is one-shot and, to close, would be cancelled, throwing a `CancellationException` into an active coroutine.",
          "source": "research/README.md § Waiting, re-planning, refunds and `close()`",
          "link": "research/README.md#waiting-re-planning-refunds-and-close"
        },
        {
          "id": "try-lock-sweep",
          "name": "`Mutex.tryLock` in `close()` to sweep the waiters",
          "verdict": "declined",
          "why": "It fails whenever a store call holds the lock.",
          "source": "research/README.md § Waiting, re-planning, refunds and `close()`",
          "link": "research/README.md#waiting-re-planning-refunds-and-close"
        },
        {
          "id": "state-flow-version",
          "name": "A budget-wide `MutableStateFlow` version bumped by every re-plan and by `close()`",
          "verdict": "declined",
          "why": "It works, but wakes every waiter on every re-plan. It stays the fallback if `select` with `onJoin` does not compile for `js`.",
          "source": "research/README.md § Waiting, re-planning, refunds and `close()`; plan.md §3",
          "link": "research/README.md#waiting-re-planning-refunds-and-close"
        },
        {
          "id": "close-writes",
          "name": "A `close()` that writes the exact hard-window counts itself",
          "verdict": "declined",
          "why": "`close()` is non-suspending and takes no lock, and the exact final write is a suspend call: `flush()`, which a client that owns its budget calls before `close()`.",
          "source": "plan.md §6.11",
          "link": "plan.md#611-where-this-plan-refines-the-parent-architecture"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "`close()` is `closing.complete()` on a budget-owned `Job()` latch, completed and never cancelled: idempotent, thread-safe, it never throws or suspends, and the winning call logs once at `INFO`.",
            "state": "as-planned"
          },
          {
            "text": "A waiter selects on the latch and its wake signal, leaves through the lock and returns `Result.failure(HttpFailure.Closed(\"budget\"))` as a value, as do a call parked on the lock and every later `acquire`. Procedure P3 removed the re-check at the top of `admitLocked`, and `WindowedCallBudgetCloseComponentTest`'s parked-call case, on a window with free capacity, failed with the call granted, 1 of the 7 tests it held then, so the re-check is covered on virtual time. The window between a waiter's section and its `select` no virtual-time test reaches; the plan leaves it to the Level 4c close-race class. A waiter whose own write-ahead save spans a `close()` returns `Closed` once the save ends, uncharged and with its reservation unlinked; a call whose write-ahead save spans one is still granted, as plan §7.16 reads, and the next `acquire` returns `Closed`.",
            "state": "as-planned",
            "ref": "final-implementation.md#4-measurements",
            "excerpt": {
              "ref": "final-implementation.md#4-measurements",
              "heading": "4. Measurements",
              "paragraph": "Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other module's suite.",
              "hash": "73ab66970463"
            }
          },
          {
            "text": "No `CancellationException` is involved: `WindowedCallBudgetCloseComponentTest` ends every waiter completed and none cancelled, so nothing reaches the response cache's loader as an `IllegalStateException`.",
            "state": "as-planned"
          },
          {
            "text": "`acquire` checks the caller's cancellation before the latch, so a caller already cancelled still gets its own `CancellationException` after `close()`, and never a permit.",
            "state": "as-planned"
          },
          {
            "text": "`close()` writes nothing; `flush()` writes the exact counts and, like `utilisation()` and `complete()` or `release()` on permits granted before the close, keeps working after it.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A completed latch needs no lock and throws nothing, so `close()` stays non-suspending and a waiter ends with a value inside the cache's load coroutine.",
        "statements": [
          {
            "text": "`close()` is `closing.complete()` on a budget-owned `Job()` latch, completed and never cancelled: idempotent, thread-safe, it never throws or suspends, and the winning call logs once.",
            "ref": "plan.md#r10-windowedcallbudget--waiting-refunds-and-close",
            "excerpt": {
              "ref": "plan.md#r10-windowedcallbudget--waiting-refunds-and-close",
              "heading": "R10. `WindowedCallBudget` — waiting, refunds and `close()`",
              "paragraph": "A waiter owns a conflated `Channel<Unit>` wake signal and waits in `withTimeoutOrNull(min(remaining, 1 minute)) { select { closing.onJoin { }; wake.onReceive { } } }`. After every wake or timeout it re-reads its plan under the lock. A re-plan signals only waiters moved earlier or failed. A waiter grants itself under the lock, after `currentCoroutineContext().ensureActive()` and before any mutation. · A cancellation refunds the waiter's reservation and re-plans the waiters behind it, in `finally { withContext(NonCancellable) { lock { if still waiting: unlink, re-plan } } }`. A waiter whose new …",
              "hash": "b06dd085157b"
            }
          },
          {
            "text": "A waiter races the latch against its wake signal with `select`, leaves through the lock and returns `Result.failure(HttpFailure.Closed(\"budget\"))` as a value, as do a call parked on the lock and every later `acquire`.",
            "ref": "plan.md#r10-windowedcallbudget--waiting-refunds-and-close",
            "excerpt": {
              "ref": "plan.md#r10-windowedcallbudget--waiting-refunds-and-close",
              "heading": "R10. `WindowedCallBudget` — waiting, refunds and `close()`",
              "paragraph": "A waiter owns a conflated `Channel<Unit>` wake signal and waits in `withTimeoutOrNull(min(remaining, 1 minute)) { select { closing.onJoin { }; wake.onReceive { } } }`. After every wake or timeout it re-reads its plan under the lock. A re-plan signals only waiters moved earlier or failed. A waiter grants itself under the lock, after `currentCoroutineContext().ensureActive()` and before any mutation. · A cancellation refunds the waiter's reservation and re-plans the waiters behind it, in `finally { withContext(NonCancellable) { lock { if still waiting: unlink, re-plan } } }`. A waiter whose new …",
              "hash": "b06dd085157b"
            }
          },
          {
            "text": "No `CancellationException` is involved, so nothing reaches the response cache's loader as an `IllegalStateException`.",
            "ref": "plan.md#65-waiting-re-planning-refunds-and-close",
            "excerpt": {
              "ref": "plan.md#65-waiting-re-planning-refunds-and-close",
              "heading": "6.5 Waiting, re-planning, refunds and `close()`",
              "paragraph": "The plan lives under the lock and every wake, timeout or spurious signal re-reads it, so a lost, stale or double wake costs one extra lock section and nothing else. A waiter moved later wakes at its old time, re-reads and sleeps again; only a waiter moved earlier or failed is signalled. A newly planned waiter is created with its plan and is never signalled by it. · Re-planning. `replan(fromSeq)` takes back the reservations of every waiting waiter with `seq >= fromSeq` and plans them again in arrival order, each from the ledger as it then stands and from its lane's previous slot; a waiter …",
              "hash": "4e0c07780068"
            }
          },
          {
            "text": "A caller already cancelled still gets its own `CancellationException` after `close()`, and never a permit.",
            "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
            "excerpt": {
              "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
              "heading": "R2. The `CallBudget` port and its contract",
              "paragraph": "No defaulted parameters. The contract, numbered in the KDoc as `HttpTransport`'s and `ResponseCache`'s are:",
              "hash": "6511503d7606"
            }
          },
          {
            "text": "`close()` writes nothing; `flush()` writes the exact counts and, like `utilisation()` and `complete()` or `release()` on permits granted before the close, keeps working after it.",
            "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
            "excerpt": {
              "ref": "plan.md#r2-the-callbudget-port-and-its-contract",
              "heading": "R2. The `CallBudget` port and its contract",
              "paragraph": "No defaulted parameters. The contract, numbered in the KDoc as `HttpTransport`'s and `ResponseCache`'s are:",
              "hash": "6511503d7606"
            }
          }
        ],
        "steps": [
          {
            "id": "S4",
            "what": "Write the latch, the wait's `select` and `flush()` in the budget.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WindowedCallBudget.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetCloseComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`; `:webtools-http-commoncheck:build` (Procedure P2)",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `WindowedCallBudgetCloseComponentTest` and `WindowedCallBudgetCancellationComponentTest`; Level 4c (non-functional) `WindowedCallBudgetCloseRaceNonFunctionalTest`, with Procedure P3 checking that it would notice the missing guard"
        ],
        "risks": [
          "`select` with `onJoin` and `onReceive` has not been compiled for `js`: the first compile settles it, and the fallback is a budget-wide `MutableStateFlow` version (§3)",
          "The close race has a window no deterministic seam reaches, probed by real threads, which cannot prove its absence (§3, §9)"
        ],
        "source": "plan.md R2, R10, §6.5, §6.11",
        "link": "plan.md#r10-windowedcallbudget--waiting-refunds-and-close"
      }
    },
    {
      "id": "fixtures",
      "group": "Tests",
      "name": "`RecordingBudgetObserver` and `FakeBudgetStore` in the published fixtures",
      "system": "webtools-http-budget",
      "where": "webtools-http/src/jvmTestFixtures/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "The fixtures hold a fake for the transport and the clocks and none for the budget's two seams, whose names #63 reserved for this unit.",
        "evidence": [
          "../63-test-support/plan.md §12 — the names reserved, and what unit 8 adds",
          "../architecture.md §6.7 — the fakes land with the budget unit, into this compilation"
        ],
        "source": "../architecture.md §6.7, §11",
        "link": "../architecture.md#67-published-test-support"
      },
      "options": [
        {
          "id": "two-fakes",
          "name": "The two fakes in the published fixtures compilation, each implementing a gated seam under its own `@OptIn`",
          "verdict": "accepted",
          "why": "They are also the worked examples of implementing those seams.",
          "source": "../architecture.md §6.7",
          "link": "../architecture.md#67-published-test-support"
        },
        {
          "id": "budget-fake",
          "name": "A budget fake in the published fixtures",
          "verdict": "declined",
          "why": "`WindowedCallBudget` on `VirtualClock` is the test double; a fake stays a follow-up if a consumer needs one beyond it.",
          "source": "plan.md §4, §13",
          "link": "plan.md#4-non-goals"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": "",
        "statements": [
          {
            "text": "`RecordingBudgetObserver` and `FakeBudgetStore` were built final and thread-safe in the published fixtures, each with its own `@OptIn` and its KDoc naming it the worked example of its seam; 8 and 11 Level 2 tests hold them, `FixturesConsumerGatingTest` holds each as its seam's type from consumer code, and the fixtures-publication check passes.",
            "state": "as-planned"
          },
          {
            "text": "`FakeBudgetStore` offers `failLoads` and `failSaves`, returned or thrown and for a set number of calls, `loadDelay` and `saveDelay` in virtual time, a preloaded `initial` snapshot, the recorded `saves`, `loadCount` and `saveAttemptCount`. Both failure scripts refuse a `times` below 1 with `IllegalArgumentException`, where the plan's listing had no check and a script of zero failures delivered one.",
            "state": "amended",
            "ref": "final-implementation.md#d8-fakebudgetstorefailloads-and-failsaves-refuse-a-times-below-1",
            "excerpt": {
              "ref": "final-implementation.md#d8-fakebudgetstorefailloads-and-failsaves-refuse-a-times-below-1",
              "heading": "D8. `FakeBudgetStore.failLoads` and `failSaves` refuse a `times` below 1",
              "paragraph": "Plan: §7.17, the `FakeBudgetStore` listing (`failLoads(cause, thrown = false, times = Int.MAX_VALUE)` and `failSaves(…)` arm a `Failure(cause, thrown, times)` with no check on `times`), with R16. · As built: both begin with `require(times >= 1) { TIMES_MESSAGE }` (`times must be at least 1`, a constant message, the module's rule for programmer errors), and their KDoc gains the `@throws IllegalArgumentException` line; `Int.MAX_VALUE` keeps its meaning of every call. Nothing else in the fixture changes; the fixtures are not in the ABI dump. · Why: the test review found that `times = 0` …",
              "hash": "c5ee383f8e79"
            }
          },
          {
            "text": "Their KDoc states Stable Core with source compatibility only, and no build file or dependency changed.",
            "state": "as-planned"
          },
          {
            "text": "No budget fake joined them: the tests run `WindowedCallBudget` on `VirtualClock`.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "`FakeBudgetStore.failLoads` and `failSaves` begin with `require(times >= 1)` and their KDoc gains the `@throws IllegalArgumentException` line, where the plan's listing arms a failure with no check on `times`; `Int.MAX_VALUE` keeps its meaning of every call.",
            "why": "With `times = 0` the listing delivered one failure instead of none: the count only decided when to disarm, and the armed failure was still returned for the current call. A script of zero failures is not a meaningful request (it is `heal()`), so the fixture refuses it, and a negative `times` the same way. The fixtures are not in the ABI dump, which is unchanged.",
            "when": "qa pass 2 fixes",
            "ref": "final-implementation.md#d8-fakebudgetstorefailloads-and-failsaves-refuse-a-times-below-1",
            "link": "../architecture.md#67-published-test-support"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "They give a consumer's tests the budget's two seams, and double as the worked examples of implementing them.",
        "statements": [
          {
            "text": "`RecordingBudgetObserver` and `FakeBudgetStore` are final and thread-safe, each with its own `@OptIn`, and are the worked examples of the two gated seams.",
            "ref": "plan.md#r16-fixtures",
            "excerpt": {
              "ref": "plan.md#r16-fixtures",
              "heading": "R16. Fixtures",
              "paragraph": "`RecordingBudgetObserver` and `FakeBudgetStore`: each `final`, thread-safe, with its own `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`, explicit visibility, KDoc, defaulted parameters allowed, no new dependency. `FakeBudgetStore` offers failure injection for load and save (returned or thrown), a virtual-time delay for each, a preloaded snapshot, the recorded saves and the load count. They are the worked examples of the two gated seams.",
              "hash": "32da4470e8a3"
            }
          },
          {
            "text": "`FakeBudgetStore` offers failure injection for load and save, returned or thrown, a virtual-time delay for each, a preloaded snapshot, the recorded saves and the load count.",
            "ref": "plan.md#r16-fixtures",
            "excerpt": {
              "ref": "plan.md#r16-fixtures",
              "heading": "R16. Fixtures",
              "paragraph": "`RecordingBudgetObserver` and `FakeBudgetStore`: each `final`, thread-safe, with its own `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`, explicit visibility, KDoc, defaulted parameters allowed, no new dependency. `FakeBudgetStore` offers failure injection for load and save (returned or thrown), a virtual-time delay for each, a preloaded snapshot, the recorded saves and the load count. They are the worked examples of the two gated seams.",
              "hash": "32da4470e8a3"
            }
          },
          {
            "text": "They are Stable Core with source compatibility only, and add no dependency.",
            "ref": "plan.md#r1-the-public-surface-and-its-tier",
            "excerpt": {
              "ref": "plan.md#r1-the-public-surface-and-its-tier",
              "heading": "R1. The public surface and its tier",
              "paragraph": "`webtools-http` gains these public declarations in `com.spartanlabs.webtools.http`, each with KDoc (`@param`, `@return`, `@throws`) that names its tier. Implementing or subclassing `CallBudget`, `Permit`, `RateSignalsReader`, `BudgetStore`, `BudgetObserver` or `BudgetEvent` is Experimental (behind `ExperimentalForInheritanceWebToolsApi`); everything else is Stable Core and usable without the opt-in: calling the ports, passing them, naming them in a signature, `CallBudget.Unlimited`, `WindowedCallBudget`, `InMemoryBudgetStore`, `RateSignalsReader.Standard`, `BudgetObserver.None`, `withPermit`, …",
              "hash": "e8c414c1d243"
            }
          },
          {
            "text": "No budget fake joins them: `WindowedCallBudget` on `VirtualClock` is the test double.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "Retries (never), the transport's dispatcher sizing (the budget publishes `maxBurst`; #68 sizes), multi-process counting (a snapshot store is last-writer-wins; an atomic shared counter would be a second `CallBudget` implementation). · Comparing the server's `Date` with the wall clock to detect a wrong forward clock (a follow-up, §13). · A file-backed or shared-counter `BudgetStore`; a serializer for `BudgetSnapshot`. · Calendar-month windows, per-window costs and fractional costs; dynamic header-declared rules (readings for unknown window names are ignored); the IETF `RateLimit` headers. · …",
              "hash": "3cccbf4a3ebd"
            }
          }
        ],
        "steps": [
          {
            "id": "S3",
            "what": "Write the two fixtures and their Level 2 component classes.",
            "files": [
              "webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/RecordingBudgetObserver.kt",
              "webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/FakeBudgetStore.kt"
            ],
            "check": "`:webtools-http:componentTest`; the fixtures-publication check in `:webtools-http:check`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `RecordingBudgetObserverComponentTest` and `FakeBudgetStoreComponentTest`"
        ],
        "risks": [
          "The change to `jvmTestFixtures` re-runs the fixtures-publication check, 44 to 52 s (§5)"
        ],
        "source": "plan.md R1, R16, §4",
        "link": "plan.md#r16-fixtures"
      }
    },
    {
      "id": "component-tests",
      "group": "Tests",
      "name": "Level 2 on `VirtualClock`, and the three-clocks proof with the real budget",
      "system": "webtools-http-budget",
      "where": "test source (new, `testing.component`)",
      "gap": {
        "kind": "missing",
        "summary": "No test covers a budget. The three-clocks proof runs on a Caffeine cache, the real cache and a UTC-minute index, with no budget in it.",
        "evidence": [
          "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/VirtualClockThreeClocksComponentTest.kt:21-24 — a Caffeine cache, a `delay` and a UTC-minute index (baseline)",
          "../consequences.md — unit 8 re-runs the three-clocks proof with the real budget",
          "../research/call-budget.md § Level-2 tests — what the probe already shows to be writable",
          "webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/VirtualClock.kt:94 — the published wall clock is the start plus the virtual elapsed time, so it never steps (baseline)",
          "research/ledger-time.md §6.6 — the Level 2 tests of a step need a wall clock that can step",
          "research/waits-and-close.md F10, §6.9 — what a one-window stand-in measured on `runTest` (33 of 33 checks on five runs), and what Level 2 asserts",
          "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/support/webtools/http/Waiter.kt — the existing helper for launched calls, typed to the cache's `getOrLoad` (baseline)"
        ],
        "source": "research/README.md § Waiting, re-planning, refunds and `close()`; research/README.md § The ledger's time model; ../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "virtual-clock",
          "name": "Level 2 on `VirtualClock`: FIFO, refunds and re-plans, lane floors, pauses from each hint, the hard stop, restore rules, write-ahead after a simulated crash, `complete` from a cancelled coroutine and the three clocks together",
          "verdict": "accepted",
          "why": "Deterministic, with no network and no real time: the research ran the same behaviours on fake time.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        },
        {
          "id": "steppable-published",
          "name": "`SteppableWallClock` in the published fixtures",
          "verdict": "declined",
          "why": "`VirtualClock.wallClock` cannot step, so the clock-step cases need it, but it stays in `jvmTest`'s support unless a consumer needs it.",
          "source": "research/README.md § The ledger's time model; consequences.md §7",
          "link": "research/README.md#the-ledgers-time-model"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": "",
        "statements": [
          {
            "text": "Level 2 holds the port and the budget on `VirtualClock`: S3, S4 and the fixes for qa passes 1 and 2 hold 21 classes and 160 tests over the fixtures, grant instants, refunds and re-plans, lane floors, pauses, the hard stop, the restore rules, a simulated crash and the clock steps, 31 classes and 229 tests with S1 and S2's; the module's `componentTest` ran 748 tests with none failed or skipped. The fixes for qa pass 1 added three cases: the persisted-mark reproduction in `WindowedCallBudgetCrashComponentTest`, a save that outlasts the period in `WindowedCallBudgetStoreFailureComponentTest`, and a re-plan 1 ms and 2 ms past a waiter's deadline in `WindowedCallBudgetRefundComponentTest`. The fixes for qa pass 2 added five: a refused `times` in `FakeBudgetStoreComponentTest`, a waiter and a call whose write-ahead save spans a `close()` in `WindowedCallBudgetCloseComponentTest`, a store `Error` in `WindowedCallBudgetStoreFailureComponentTest`, and a retried restore that re-plans the waiters in `WindowedCallBudgetRestoreComponentTest`. `WindowedCallBudgetCloseComponentTest` covers a call whose coroutine has not run at `close()` in place of a waiter between its section and its wait, and its parked-call case detects Procedure P3's mutation.",
            "state": "amended",
            "ref": "final-implementation.md#d3-windowedcallbudgetclosecomponenttest-covers-a-call-that-has-not-started-in-place-of-a-waiter-between-its-section-and-its-wait",
            "excerpt": {
              "ref": "final-implementation.md#d3-windowedcallbudgetclosecomponenttest-covers-a-call-that-has-not-started-in-place-of-a-waiter-between-its-section-and-its-wait",
              "heading": "D3. `WindowedCallBudgetCloseComponentTest` covers a call that has not started in place of a waiter \"between its section and its wait\"",
              "paragraph": "Plan: §9… `WindowedCallBudgetCloseComponentTest`: \"a waiter between its section and its wait also\" returns `Closed` once the lock frees. · As built: no case puts a waiter between the end of its lock section and the start of its `select`. In its place the class covers a call whose coroutine has not run when `close()` happens (`a call whose coroutine has not run when close happens returns Closed`), beside the parked-on-the-lock and sleeping-waiter cases the row also names. · Why: on the virtual scheduler a waiter leaves its section and enters `withTimeoutOrNull { select { … } }` with no …",
              "hash": "ec4408c0edf6"
            }
          },
          {
            "text": "A budget's interleavings are driven with `advanceBy` and asserted on state, never with `join()`; the fakes are hand-written, and each budget is closed with `use` or by the `close()` its case is about.",
            "state": "as-planned"
          },
          {
            "text": "`commonTest` holds the value types and the reader, and `jvmTest` everything that suspends or uses `VirtualClock`.",
            "state": "as-planned"
          },
          {
            "text": "`WindowedCallBudgetThreeClocksComponentTest` re-ran the three-clocks proof with the real budget beside a real `CaffeineResponseCache`, and `VirtualClockThreeClocksComponentTest` changed only in its KDoc.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "`WindowedCallBudgetCloseComponentTest` has no case for a waiter between its lock section and its `select`; it covers a call whose coroutine has not run when `close()` happens, beside the lock-parked and sleeping-waiter cases the plan's row names.",
            "why": "On the virtual scheduler a waiter goes from its section into its `select` with no suspension point, so no test can interleave `close()` there. The plan already records that window as out of reach of a deterministic seam and assigns it to the Level 4c close-race class and Procedure P3, and the sleeping-waiter case proves the mechanism the row relies on.",
            "when": "S4",
            "ref": "final-implementation.md#d3-windowedcallbudgetclosecomponenttest-covers-a-waiter-that-has-not-started-in-place-of-one-between-its-section-and-its-wait",
            "link": "research/waits-and-close.md#f3-close-as-a-latch-not-a-cancellation-documented--measured"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Virtual time makes every interleaving deterministic, with no network and no real clock.",
        "statements": [
          {
            "text": "Level 2 holds every point of the port and the budget on `VirtualClock`: exact grant instants, refunds and re-plans, lane floors, pauses from each hint, the hard stop, the restore rules, write-ahead after a simulated crash, `complete` from a cancelled coroutine and the clock steps.",
            "ref": "plan.md#level-2--component-componenttest",
            "excerpt": {
              "ref": "plan.md#level-2--component-componenttest",
              "heading": "Level 2 — component (`componentTest`)",
              "paragraph": "`commonTest` — the value types and the reader (`…/component/webtools/http/<Class>.kt`):",
              "hash": "5006fc2289e6"
            }
          },
          {
            "text": "Each interleaving is driven with `advanceBy` and asserted on its state, never with `join()`, so a hang fails at once; the fakes are hand-written and every budget is closed with `use`.",
            "ref": "plan.md#9-test-plan",
            "excerpt": {
              "ref": "plan.md#9-test-plan",
              "heading": "9. Test plan",
              "paragraph": "Conventions that bind every class below:",
              "hash": "b64de1817da9"
            }
          },
          {
            "text": "`commonTest` holds the value types and the reader, and `jvmTest` everything that suspends or uses `VirtualClock`.",
            "ref": "plan.md#9-test-plan",
            "excerpt": {
              "ref": "plan.md#9-test-plan",
              "heading": "9. Test plan",
              "paragraph": "Conventions that bind every class below:",
              "hash": "b64de1817da9"
            }
          },
          {
            "text": "`WindowedCallBudgetThreeClocksComponentTest` re-runs the three-clocks proof with the real budget beside the real cache, and `VirtualClockThreeClocksComponentTest` changes only its KDoc.",
            "ref": "plan.md#level-2--component-componenttest",
            "excerpt": {
              "ref": "plan.md#level-2--component-componenttest",
              "heading": "Level 2 — component (`componentTest`)",
              "paragraph": "`commonTest` — the value types and the reader (`…/component/webtools/http/<Class>.kt`):",
              "hash": "5006fc2289e6"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write the value types' and the reader's classes, and extend `WebToolsDslComponentTest`.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/InMemoryBudgetStoreComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S2",
            "what": "Write `CallBudgetUnlimitedComponentTest`.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CallBudgetUnlimitedComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S3",
            "what": "Write the two fixtures' classes.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S4",
            "what": "Write the budget's classes on `VirtualClock` and the test support they need.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/support/webtools/http/"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component): the 31 new classes of the test plan and the changed `WebToolsDslComponentTest`"
        ],
        "risks": [],
        "source": "plan.md §9",
        "link": "plan.md#level-2--component-componenttest"
      }
    },
    {
      "id": "integration-tests",
      "group": "Tests",
      "name": "Level 3: the reader against the real transport over loopback",
      "system": "webtools-http-budget",
      "where": "test source (new, `testing.integration`)",
      "gap": {
        "kind": "missing",
        "summary": "#55's decomposition names no Level 3 suite for this unit, which makes no network call of its own. One boundary is genuine: the transport hides a `503` and restores it, so a regression there would silently disable every pause on a `503`.",
        "evidence": [
          "plan.md §2 — how `Retry-After` reaches a response (#65)",
          "../65-okhttp-transport/consequences.md §4 — a `503`'s `Retry-After` reaches the response under its own name and value, and a `429`'s headers are untouched"
        ],
        "source": "plan.md §2, §9",
        "link": "plan.md#2-current-state"
      },
      "options": [
        {
          "id": "reader-loopback",
          "name": "One Level 3 class: `RateSignalsReader.Standard` reading a real `503` with `Retry-After` and a real `429` from the real transport over loopback",
          "verdict": "accepted",
          "why": "It holds the one boundary this unit has, with real time under `runBlocking` and no network beyond loopback.",
          "source": "plan.md §9",
          "link": "plan.md#level-3--integration-integrationtest"
        },
        {
          "id": "no-integration",
          "name": "No Level 3 suite, as the unit has no external interface of its own",
          "verdict": "declined",
          "why": "A regression at that boundary would silently disable every pause on a `503`.",
          "source": "plan.md §9",
          "link": "plan.md#level-3--integration-integrationtest"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "`RateSignalsReaderLoopbackIntegrationTest` held `Standard` against real `503`s and `429`s from the real transport over loopback, in six tests, all passing: `Retry-After` in seconds, a date-valued one measured against the response's own `Date` whatever the clock reads, two header lines giving the longer, and a `503` without the header and a returned `300` read as `None`.",
            "state": "as-planned",
            "ref": "final-implementation.md#4-measurements",
            "excerpt": {
              "ref": "final-implementation.md#4-measurements",
              "heading": "4. Measurements",
              "paragraph": "Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other module's suite.",
              "hash": "73ab66970463"
            }
          },
          {
            "text": "It ran on real time: each call goes through the loopback fleet's `fetch`, `runBlocking` with a 30 s `withTimeout`, never `runTest`.",
            "state": "as-planned"
          },
          {
            "text": "No Level 4b end-to-end class was added: no end-to-end flow exists until the call facades (#68).",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A plain unit test cannot show what the real transport leaves on a response.",
        "statements": [
          {
            "text": "`RateSignalsReaderLoopbackIntegrationTest` holds `Standard` against a real `503` with `Retry-After` and a real `429` from the real transport over loopback.",
            "ref": "plan.md#level-3--integration-integrationtest",
            "excerpt": {
              "ref": "plan.md#level-3--integration-integrationtest",
              "heading": "Level 3 — integration (`integrationTest`)",
              "paragraph": "The unit has no external interface of its own: it makes no network call, and reading signals from a real response in a pipeline is #68's. One boundary is genuine, because a regression there silently disables every pause on a `503`: the reader against responses the real transport produces.",
              "hash": "723856cb98df"
            }
          },
          {
            "text": "It runs on real time under `runBlocking` with a `withTimeout` bound, never `runTest`.",
            "ref": "plan.md#9-test-plan",
            "excerpt": {
              "ref": "plan.md#9-test-plan",
              "heading": "9. Test plan",
              "paragraph": "Conventions that bind every class below:",
              "hash": "b64de1817da9"
            }
          },
          {
            "text": "No Level 4b end-to-end class exists: no end-to-end flow exists until the call facades (#68).",
            "ref": "plan.md#level-4b-and-level-5-uat",
            "excerpt": {
              "ref": "plan.md#level-4b-and-level-5-uat",
              "heading": "Level 4b and Level 5 UAT",
              "paragraph": "Level 4b end-to-end: none. No end-to-end flow exists until the facades (#68); Level 3 integration above covers the reader over a real transport. · Level 5 UAT: a short manual pass (§9.2 P5): what a consumer reads — the KDoc of the new types and the regenerated dump. The IDE checks (completion of `WindowedCallBudget(windows) { }`, the opt-in error on an implementer of each gated type, the `@WebToolsDsl` scoping of `RateWindow(…) { }`) are left to the maintainer's next IntelliJ session, as for units 3 to 7.",
              "hash": "946613b7e657"
            }
          }
        ],
        "steps": [
          {
            "id": "S5",
            "what": "Write the loopback class.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/RateSignalsReaderLoopbackIntegrationTest.kt"
            ],
            "check": "`:webtools-http:integrationTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 3 (integration) `RateSignalsReaderLoopbackIntegrationTest`"
        ],
        "risks": [],
        "source": "plan.md §9",
        "link": "plan.md#level-3--integration-integrationtest"
      }
    },
    {
      "id": "deterministic-tests",
      "group": "Tests",
      "name": "Level 4a: the HTTP-date table, window arithmetic and a seeded property test",
      "system": "webtools-http-budget",
      "where": "test source (new, `testing.deterministic`)",
      "gap": {
        "kind": "missing",
        "summary": "The HTTP-date forms, the window arithmetic, the guard's two-cell charge and share rounding are pure input-to-output rules, and no table holds them.",
        "evidence": [
          "../research/call-budget.md F5 — rounding: a 3-unit window with a 20 % share reserves 1, a 7-unit window 2",
          "../research/call-budget.md F6 — the parser's differential against `java.time`, and the malformed inputs",
          "research/retry-after-parsing.md §6.7 — the rows of the table: the three forms, the two-digit-year rule, the leap second, the rejections",
          "research/retry-after-parsing.md F10 — `RFC_1123_DATE_TIME` accepts inputs the RFC grammar does not, so it cannot be the oracle",
          "research/retry-after-parsing.md F12 — 0 mismatches in about 2 million checks per seed over three seeds, on a Java mirror of the parser's sketch; the Kotlin itself was not compiled"
        ],
        "source": "research/README.md § `Retry-After` and HTTP dates; ../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "tables-and-property",
          "name": "Level 4a: the HTTP-date table against `java.time`, window arithmetic, the guard's two-cell charge, share rounding and a seeded property test",
          "verdict": "accepted",
          "why": "The rules are pure, input to output, and the research's scripts (300 seeds, two settings) already held the ledger's limits.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": "",
        "statements": [
          {
            "text": "`HttpDateDeterministicTest` and `RetryAfterDeterministicTest` hold the grammar over fixed references, and `HttpDateJavaTimeDifferentialDeterministicTest`, with a fixed seed, compared the parser against strict `java.time` formatters: they agreed on 200,000 dates per form and rejected 196,012 mutants on both sides.",
            "state": "as-planned"
          },
          {
            "text": "Tables hold the window arithmetic, the guard's two-cell charge, the share rounding, the fold, the pause, the slot search and the snapshots, all with plain calls on the coroutine-free ledger. A row the fixes for qa pass 1 added to `BudgetLedgerSnapshotDeterministicTest` holds that `exactSnapshot` writes a zero row for a cell whose persisted mark is above its count and that `markPersisted` lowers the mark; it failed on the plan's listing and passes with the change, and the module's `deterministicTest` ran 293 tests with none failed or skipped. `PauseDeterministicTest` steps a private time source and a fixed wall clock, as the JVM test support's `ManualTimeSource` is out of `commonTest`'s reach.",
            "state": "amended",
            "ref": "final-implementation.md#d1-pausedeterministictest-steps-a-time-source-of-its-own",
            "excerpt": {
              "ref": "final-implementation.md#d1-pausedeterministictest-steps-a-time-source-of-its-own",
              "heading": "D1. `PauseDeterministicTest` steps a time source of its own",
              "paragraph": "Plan: §9, Level 4a, `PauseDeterministicTest`: \"`LedgerClock` keeps the elapsed reading non-decreasing over a `ManualTimeSource` that steps back\", with §9's rule that pure arithmetic tests live in `commonTest`. · As built: the class is in `commonTest`, as the table places it, and steps a private `SteppingTimeSource` (a settable `now`; a mark reads `now` minus its value at the mark, the shape of `ManualTimeSource`) beside a private `WallClock` of its own. The behaviour the row locks down, a non-decreasing elapsed reading over a source that steps back, is tested as written. · Why: …",
              "hash": "d65201d60582"
            }
          },
          {
            "text": "`BudgetLedgerPropertyDeterministicTest` is seeded and found no provider-side overspend. It runs a fixed 3,000 steps per property, as common code cannot read the variable meant to raise the count.",
            "state": "amended",
            "ref": "final-implementation.md#d2-budgetledgerpropertydeterministictest-runs-a-fixed-3000-steps-per-property",
            "excerpt": {
              "ref": "final-implementation.md#d2-budgetledgerpropertydeterministictest-runs-a-fixed-3000-steps-per-property",
              "heading": "D2. `BudgetLedgerPropertyDeterministicTest` runs a fixed 3,000 steps per property",
              "paragraph": "Plan: §9, Level 4a, `BudgetLedgerPropertyDeterministicTest`: \"3,000 steps per property; `WEBTOOLS_PROPERTY_STEPS` raises the count\". · As built: the count is the constant 3,000 and no environment variable is read; the seed, `Random(7)`, and the two properties are the plan's. · Why: the class is in `commonTest`, as the table places it, and common Kotlin has no environment access (`System.getenv` is JVM API). The committed run is the one the plan specifies; only the knob to lengthen it is missing, and a JVM-side knob would need a `jvmTest` wrapper of the properties or an `expect`/`actual` in …",
              "hash": "c3bb445f3d56"
            }
          }
        ],
        "changes": [
          {
            "what": "`PauseDeterministicTest` steps a private `SteppingTimeSource` and a private fixed `WallClock`, where the plan's table names `ManualTimeSource`.",
            "why": "`ManualTimeSource` is a `jvmTest` support class that `commonTest` cannot see. Moving the test would break the plan's placement rule, and copying the support would duplicate a name. The behaviour the row locks down is tested as written.",
            "when": "S2",
            "ref": "final-implementation.md#d1-pausedeterministictest-steps-its-own-time-source-instead-of-the-jvm-test-supports-manualtimesource",
            "link": "research/ledger-time.md"
          },
          {
            "what": "`BudgetLedgerPropertyDeterministicTest` runs a constant 3,000 steps per property, and `WEBTOOLS_PROPERTY_STEPS` is not read.",
            "why": "The class is in `commonTest`, as the plan places it, and common Kotlin has no environment access. The seed and the step count are the plan's defaults; only the knob to lengthen the run is missing.",
            "when": "S2",
            "ref": "final-implementation.md#d2-budgetledgerpropertydeterministictest-runs-a-fixed-3000-steps-per-property",
            "link": "plan.md#9-test-plan"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "These rules are pure, input to output, so a table holds each of them and a seeded script holds the ledger's limits.",
        "statements": [
          {
            "text": "The HTTP-date and `Retry-After` tables hold the grammar over fixed references, and a `java.time` differential with a fixed seed compares the parser against strict formatters.",
            "ref": "plan.md#level-4a--deterministic-deterministictest",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic-deterministictest",
              "heading": "Level 4a — deterministic (`deterministicTest`)",
              "paragraph": "`commonTest` (`…/deterministic/webtools/http/<Class>.kt`), in the style of `HttpHeaderValueDeterministicTest`:",
              "hash": "da6932f3c6c9"
            }
          },
          {
            "text": "Tables hold the window arithmetic, the guard's two-cell charge, the share rounding, the fold, the pause, the slot search and the snapshots, all with plain calls on the coroutine-free ledger.",
            "ref": "plan.md#level-4a--deterministic-deterministictest",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic-deterministictest",
              "heading": "Level 4a — deterministic (`deterministicTest`)",
              "paragraph": "`commonTest` (`…/deterministic/webtools/http/<Class>.kt`), in the style of `HttpHeaderValueDeterministicTest`:",
              "hash": "da6932f3c6c9"
            }
          },
          {
            "text": "`BudgetLedgerPropertyDeterministicTest` is a seeded property test that finds no provider-side overspend.",
            "ref": "plan.md#level-4a--deterministic-deterministictest",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic-deterministictest",
              "heading": "Level 4a — deterministic (`deterministicTest`)",
              "paragraph": "`commonTest` (`…/deterministic/webtools/http/<Class>.kt`), in the style of `HttpHeaderValueDeterministicTest`:",
              "hash": "da6932f3c6c9"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write the date tables and the differential.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HttpDateDeterministicTest.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/RetryAfterDeterministicTest.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HttpDateJavaTimeDifferentialDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S2",
            "what": "Write the seven ledger classes and their support.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4a (deterministic): the 10 classes of the test plan"
        ],
        "risks": [],
        "source": "plan.md §9",
        "link": "plan.md#level-4a--deterministic-deterministictest"
      }
    },
    {
      "id": "nonfunctional-tests",
      "group": "Tests",
      "name": "Level 4c: the close race, the re-plan's cost and contention on real threads",
      "system": "webtools-http-budget",
      "where": "test source (new, `testing.nonfunctional`)",
      "gap": {
        "kind": "missing",
        "summary": "Virtual time shows no thread race and no cost: the design's close race was measured only on a one-window stand-in, and the cost per wake at `maxQueued` scale was not benchmarked.",
        "evidence": [
          "plan.md §3, assumption 2 — one cancel with 3,000 waiters measured at 1.2 to 1.7 ms, before this design's one lock section per woken waiter",
          "plan.md §3, assumption 4 — the close race has a window no deterministic seam reaches",
          "research/README.md § Waiting, re-planning, refunds and `close()`, What the build settles — the cost per wake was not benchmarked"
        ],
        "source": "plan.md §3, §9; research/README.md § Waiting, re-planning, refunds and `close()`",
        "link": "research/README.md#waiting-re-planning-refunds-and-close"
      },
      "options": [
        {
          "id": "real-threads",
          "name": "Three Level 4c classes on real threads: the close race, the re-plan's cost at `maxQueued` scale and contention with random cancels",
          "verdict": "accepted",
          "why": "They assert the absence of a bad outcome over many repetitions, and Procedure P3 checks that the race test would notice the guard being removed.",
          "source": "plan.md §9, §9.2",
          "link": "plan.md#level-4c--non-functional-nonfunctionaltest"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": "",
        "statements": [
          {
            "text": "`WindowedCallBudgetCloseRaceNonFunctionalTest` ran 20 rounds of 300 calls on real threads, with `close()` from a plain thread and 60 cancels from another; every call ended inside each round's 30 s bound and no waiter was left. A call may also end with a permit granted before the close, at most one per minute period the round touched, on a window built with empty lane shares, and a call cancelled before its coroutine ran counts as its own cancellation.",
            "state": "amended",
            "ref": "final-implementation.md#d5-windowedcallbudgetcloseracenonfunctionaltest-admits-a-permit-granted-before-the-close",
            "excerpt": {
              "ref": "final-implementation.md#d5-windowedcallbudgetcloseracenonfunctionaltest-admits-a-permit-granted-before-the-close",
              "heading": "D5. `WindowedCallBudgetCloseRaceNonFunctionalTest` admits a permit granted before the close",
              "paragraph": "Plan: §9, Level 4c, `WindowedCallBudgetCloseRaceNonFunctionalTest`: \"every call ends within a 30-second bound with `Closed(\"budget\")` or its own `CancellationException`, never anything else\". · As built: a round's calls may also end with a permit, at most one per minute period the round touched: the first call to fit the 1-per-minute window is granted before `close()` can refuse it (the row's own \"all but one wait\"), and a round that straddles a minute boundary on the production clock may grant one more at the boundary. The window is built with `laneShares = emptyMap()`, or every `Tracker` …",
              "hash": "893bdedd932b"
            }
          },
          {
            "text": "`WindowedCallBudgetReplanCostNonFunctionalTest` bounds 20 rounds of cancelling the head of 1,000 waiters on each of two lanes, inside 2 s per round and a 250 ms mean, and checks that no waiter is lost. It does not check the lane order of the queue's planned instants, which the plan's row asks for and no seam exposes: an open point, to be settled by an internal test seam or by dropping the assertion as a known gap.",
            "state": "amended",
            "ref": "final-implementation.md#d4-windowedcallbudgetreplancostnonfunctionaltest-does-not-check-that-the-planned-instants-stay-in-lane-order",
            "excerpt": {
              "ref": "final-implementation.md#d4-windowedcallbudgetreplancostnonfunctionaltest-does-not-check-that-the-planned-instants-stay-in-lane-order",
              "heading": "D4. `WindowedCallBudgetReplanCostNonFunctionalTest` does not check that the planned instants stay in lane order",
              "paragraph": "Plan: §9, Level 4c, `WindowedCallBudgetReplanCostNonFunctionalTest`: \"after the rounds the queue's instants are still in lane order and no waiter is lost\". · As built: the class checks that no waiter is lost (each cancelled call ended with its own cancellation; every other call still waits or holds a permit granted at a boundary; `utilisation().waiting` equals the number still running), not the order of the planned instants. To reach 1,000 waiters on each of two lanes and \"the head waiter\", it first fills the 10-per-minute window (8 `Tracker` and 2 `Interactive` grants, the split the default …",
              "hash": "e65b13efd9f9"
            }
          },
          {
            "text": "`WindowedCallBudgetContentionNonFunctionalTest` ran 400 coroutines on `Dispatchers.Default` against a 20-per-second window with a 100 ms guard and empty lane shares, 120 of them (30 %) cancelled at seeded offsets: inside a 60 s bound, grants stayed within the limit per whole-second period touched, and every uncancelled call was granted. An empty ledger is read as no planned units with nobody waiting.",
            "state": "amended",
            "ref": "final-implementation.md#d6-windowedcallbudgetcontentionnonfunctionaltest-reads-its-bounds-in-the-ledgers-own-terms",
            "excerpt": {
              "ref": "final-implementation.md#d6-windowedcallbudgetcontentionnonfunctionaltest-reads-its-bounds-in-the-ledgers-own-terms",
              "heading": "D6. `WindowedCallBudgetContentionNonFunctionalTest` reads its bounds in the ledger's own terms",
              "paragraph": "Plan: §9, Level 4c, `WindowedCallBudgetContentionNonFunctionalTest`: \"the grants never exceed `limit × (whole seconds spanned + 1)`\", \"the ledger and queue are empty afterwards\". · As built: the grant bound counts the whole-second periods the run touched, `end / 1000 − start / 1000 + 1` from the first call to the last completion, which is what a per-period limit allows (a 0.2 s run that straddles a boundary may grant two periods' worth). \"The ledger is empty\" is read as no planned units in any window and `waiting == 0`, since the current second's `used` is non-zero right after the last …",
              "hash": "d4bb34bbc88c"
            }
          },
          {
            "text": "Procedure P3 removed the close re-check at the top of `admitLocked` in a scratch run, and the race test did not notice in any of 5 runs, as a parked call's first step returns `Closed` anyway; the same mutation fails the parked-call case of the Level 2 `WindowedCallBudgetCloseComponentTest`, so only the production-clock race of this class cannot see it. Procedure P4 recorded a mean of 14.45 to 16.07 ms per round and a highest round of 37.1 ms over three runs on a 16-thread host, an order of magnitude inside the bounds, so Assumption 2's optimisation was not needed.",
            "state": "as-planned",
            "ref": "final-implementation.md#4-measurements",
            "excerpt": {
              "ref": "final-implementation.md#4-measurements",
              "heading": "4. Measurements",
              "paragraph": "Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other module's suite.",
              "hash": "73ab66970463"
            }
          }
        ],
        "changes": [
          {
            "what": "`WindowedCallBudgetReplanCostNonFunctionalTest` checks its timing bounds and that no waiter is lost, not that the queue's planned instants stay in lane order. Still an open point.",
            "why": "A waiter's plan is private to the budget, `utilisation()` reports counts only, and on real time the instants lie up to 200 minutes ahead, so they cannot be observed as grants within the bound either. The Level 2 FIFO and refund classes and the ledger's tables lock the ordering down on virtual time.",
            "when": "S5",
            "ref": "final-implementation.md#d4-windowedcallbudgetreplancostnonfunctionaltest-cannot-check-that-the-queues-instants-stay-in-lane-order",
            "link": "../research/call-budget.md"
          },
          {
            "what": "`WindowedCallBudgetCloseRaceNonFunctionalTest` also admits a permit granted before the close, at most one per minute period the round touched, builds its window with empty lane shares, and counts a call cancelled before its coroutine ran as its own cancellation.",
            "why": "The plan's letter leaves no outcome for the grant its own setup produces, and R8 requires empty shares on a window of limit 1, or every `Tracker` call would be refused with `CostExceedsLimit` instead of waiting. The bound on grants per period keeps the invariant the row means.",
            "when": "S5",
            "ref": "final-implementation.md#d5-windowedcallbudgetcloseracenonfunctionaltest-admits-a-permit-granted-before-the-close-as-an-outcome",
            "link": "research/waits-and-close.md#f3-close-as-a-latch-not-a-cancellation-documented--measured"
          },
          {
            "what": "`WindowedCallBudgetContentionNonFunctionalTest` bounds grants by the whole-second periods the run touched, reads an empty ledger as no planned units with nobody waiting, and uses empty lane shares, so the limit is the window's plain 20.",
            "why": "A run that straddles a second's boundary may legitimately grant two periods' worth, and the current second's `used` is necessarily non-zero right after the last grants, so the other readings would fail a correct budget or could not hold at all. Measured: 302, 306 and 303 of the 400 calls granted over 16 periods (bound 320).",
            "when": "S5",
            "ref": "final-implementation.md#d6-windowedcallbudgetcontentionnonfunctionaltest-reads-its-three-bounds-in-the-ledgers-own-terms",
            "link": "../research/call-budget.md"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Thread timing and cost show only on real threads.",
        "statements": [
          {
            "text": "`WindowedCallBudgetCloseRaceNonFunctionalTest` shows on real threads that no waiter hangs when `close()` races `acquire` and cancellation.",
            "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
            "excerpt": {
              "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
              "heading": "Level 4c — non-functional (`nonfunctionalTest`)",
              "paragraph": "",
              "hash": "4145c29543c1"
            }
          },
          {
            "text": "`WindowedCallBudgetReplanCostNonFunctionalTest` bounds a re-plan with 1,000 waiters on each of two lanes.",
            "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
            "excerpt": {
              "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
              "heading": "Level 4c — non-functional (`nonfunctionalTest`)",
              "paragraph": "",
              "hash": "4145c29543c1"
            }
          },
          {
            "text": "`WindowedCallBudgetContentionNonFunctionalTest` runs 400 coroutines on `Dispatchers.Default` against a 20-per-second window, 30 % cancelled at random.",
            "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
            "excerpt": {
              "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
              "heading": "Level 4c — non-functional (`nonfunctionalTest`)",
              "paragraph": "",
              "hash": "4145c29543c1"
            }
          },
          {
            "text": "Procedure P3 removes the close guard in a scratch run to see whether the race test notices, and Procedure P4 records the re-plan's numbers.",
            "ref": "plan.md#92-procedures-not-committed-tests",
            "excerpt": {
              "ref": "plan.md#92-procedures-not-committed-tests",
              "heading": "9.2 Procedures (not committed tests)",
              "paragraph": "Runs (from the repository root; on Windows `gradlew.bat`). Only `webtools-http`'s own tasks and the check module's `js` compile; no other module's suite, before or after:",
              "hash": "41f0f5396c8a"
            }
          }
        ],
        "steps": [
          {
            "id": "S5",
            "what": "Write the three classes and run Procedures P3 and P4.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/"
            ],
            "check": "`:webtools-http:nonfunctionalTest`; Procedures P3 and P4",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4c (non-functional) `WindowedCallBudgetCloseRaceNonFunctionalTest`, `WindowedCallBudgetReplanCostNonFunctionalTest` and `WindowedCallBudgetContentionNonFunctionalTest`"
        ],
        "risks": [
          "The race and contention suites depend on thread timing and need several cores to open the windows they probe: they cannot prove an absence (§9)"
        ],
        "source": "plan.md §9, §9.2",
        "link": "plan.md#level-4c--non-functional-nonfunctionaltest"
      }
    },
    {
      "id": "release",
      "group": "Release",
      "name": "The ABI dump and the version",
      "system": "webtools-http-budget",
      "where": "webtools-http/api/webtools-http.api",
      "gap": {
        "kind": "missing",
        "summary": "`webtools-http` is new and unreleased at `1.0.0`. Its committed ABI dump records no budget type beyond the failure.",
        "evidence": [
          "webtools-http/api/webtools-http.api:116 — the one budget entry at the baseline",
          "../architecture.md §11 — unit 8 moves no version"
        ],
        "source": "../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "no-version",
          "name": "No version moved: `webtools-http` stays unreleased at `1.0.0` until the stack is complete, and the dump grows with the budget",
          "verdict": "accepted",
          "why": "The units that build an unreleased module move no version.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "No version moved: `webtools-http` `1.0.0` stays unreleased and this landing is not a release candidate.",
            "state": "as-planned",
            "ref": "final-implementation.md#final-implementation-call-budget-issue-67",
            "excerpt": {
              "ref": "final-implementation.md#final-implementation-call-budget-issue-67",
              "heading": "Final implementation: `call-budget` (Issue #67)",
              "paragraph": "The as-built record of `SpartanLaboratories/WebTools#67`, unit 8 (`call-budget`) of #55's architecture. It states what was built, where the work departs from `plan.md` and why, and the measurements that show it holds. What the unit changes for the repository, its published artifacts and the sibling units, and the known gaps and follow-ups it leaves, are in `consequences.md`.",
              "hash": "3101548dff09"
            }
          },
          {
            "text": "The ABI dump grew from 557 to 892 lines by exactly the classes of §7.23, with no `internal` member or ledger type, and `apiCheck` passed in the module check.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape, with the deviations of §2. Paths are under `webtools-http/src/`.",
              "hash": "69e942aefac3"
            }
          },
          {
            "text": "No dependency of either main variant and no build file changed: `kotlinx-coroutines-core` 1.10.2 already was the `commonMain` `api` dependency.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape, with the deviations of §2. Paths are under `webtools-http/src/`.",
              "hash": "69e942aefac3"
            }
          },
          {
            "text": "`README.md`'s `webtools-http` row and fixtures paragraph, and two `### Added` entries under `CHANGELOG.md`'s `## [Unreleased]`, record the change, written after the code-landing commit.",
            "state": "as-planned",
            "ref": "final-implementation.md#final-implementation-call-budget-issue-67",
            "excerpt": {
              "ref": "final-implementation.md#final-implementation-call-budget-issue-67",
              "heading": "Final implementation: `call-budget` (Issue #67)",
              "paragraph": "The as-built record of `SpartanLaboratories/WebTools#67`, unit 8 (`call-budget`) of #55's architecture. It states what was built, where the work departs from `plan.md` and why, and the measurements that show it holds. What the unit changes for the repository, its published artifacts and the sibling units, and the known gaps and follow-ups it leaves, are in `consequences.md`.",
              "hash": "3101548dff09"
            }
          },
          {
            "text": "No other module's suite ran: `webtools-udp`, `webtools-scraping`, `webtools-browser`, `build-logic`, `webtools-http-commoncheck` and the root build files are unchanged from the base.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape, with the deviations of §2. Paths are under `webtools-http/src/`.",
              "hash": "69e942aefac3"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "An unreleased module's units move no version, so the only record of the published surface is the ABI dump.",
        "statements": [
          {
            "text": "No version moves: `webtools-http` `1.0.0` stays unreleased and this landing is not a release candidate.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-67-call-budget`. It exists and holds the research commits; the implementer confirms it rather than cutting it again. · Commits. Each leaves the build green (`apiCheck` included). The type and the split are the final call at commit time; subject lines end with `(Issue #67)`; no attribution trailers. · `feat: add the call budget's vocabulary, Retry-After parser and seams (Issue #67)` — this `plan.md` and the planned `consequences.md` of this folder; `Lane.kt`, `RateWindow.kt`, `WindowReading.kt`, `RateSignals.kt`, `HttpDate.kt`, `RetryAfter.kt`, `RateSignalsReader.kt`, …",
              "hash": "4e0e2cdc84f9"
            }
          },
          {
            "text": "The ABI dump is regenerated after each code stage and grows by exactly the classes of §7.23.",
            "ref": "plan.md#723-webtools-httpapiwebtools-httpapi-regenerated",
            "excerpt": {
              "ref": "plan.md#723-webtools-httpapiwebtools-httpapi-regenerated",
              "heading": "7.23 `webtools-http/api/webtools-http.api` (regenerated)",
              "paragraph": "Run `:webtools-http:apiDump` once per code stage and commit the result with it; review the diff by eye (the inline-class `Result` return and the `Duration` parameters mangle some names with a hash suffix; take the generated spelling). The dump gains exactly these classes (members: constructors, getters, `equals`/`hashCode`/`toString` where defined):",
              "hash": "9a10f6e053c4"
            }
          },
          {
            "text": "No dependency of either main variant changes, as `kotlinx-coroutines-core` 1.10.2 already is the `commonMain` `api` dependency, and no build file changes.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-67-call-budget`. It exists and holds the research commits; the implementer confirms it rather than cutting it again. · Commits. Each leaves the build green (`apiCheck` included). The type and the split are the final call at commit time; subject lines end with `(Issue #67)`; no attribution trailers. · `feat: add the call budget's vocabulary, Retry-After parser and seams (Issue #67)` — this `plan.md` and the planned `consequences.md` of this folder; `Lane.kt`, `RateWindow.kt`, `WindowReading.kt`, `RateSignals.kt`, `HttpDate.kt`, `RetryAfter.kt`, `RateSignalsReader.kt`, …",
              "hash": "4e0e2cdc84f9"
            }
          },
          {
            "text": "`README.md`'s `webtools-http` row and fixtures paragraph, and two `### Added` entries under `CHANGELOG.md`'s `## [Unreleased]`, record the change.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Audience-Reach rings touched.",
              "hash": "08fd4f4e819c"
            }
          },
          {
            "text": "No other module's suite runs: the other three are shown unaffected by the absence of any change to their source, build file or dependencies.",
            "ref": "plan.md#92-procedures-not-committed-tests",
            "excerpt": {
              "ref": "plan.md#92-procedures-not-committed-tests",
              "heading": "9.2 Procedures (not committed tests)",
              "paragraph": "Runs (from the repository root; on Windows `gradlew.bat`). Only `webtools-http`'s own tasks and the check module's `js` compile; no other module's suite, before or after:",
              "hash": "41f0f5396c8a"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Regenerate the dump with the vocabulary and the seams.",
            "files": [
              "webtools-http/api/webtools-http.api"
            ],
            "check": "`:webtools-http:apiCheck`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S2",
            "what": "Regenerate the dump with the port and `Permit`.",
            "files": [
              "webtools-http/api/webtools-http.api"
            ],
            "check": "`:webtools-http:apiCheck`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S4",
            "what": "Regenerate the dump with `WindowedCallBudget`.",
            "files": [
              "webtools-http/api/webtools-http.api"
            ],
            "check": "`:webtools-http:apiCheck`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S6",
            "what": "Update the README's row and paragraph and the CHANGELOG.",
            "files": [
              "README.md",
              "CHANGELOG.md"
            ],
            "check": "`:webtools-http:check`, once, at the end",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "`:webtools-http:apiCheck`, `:webtools-http:verifyJavaFloor`, `:webtools-http-commoncheck:build` and `:webtools-http:check`"
        ],
        "risks": [
          "Every public declaration but the gate on implementers is Stable Core and hard to change after 1.0.0; each is what #68 consumes (§10)"
        ],
        "source": "plan.md §7.23, §8, §11",
        "link": "plan.md#11-version-control"
      }
    },
    {
      "id": "real-signals",
      "group": "Left for later",
      "name": "Reading signals from a real response, the dispatcher's size and the closing order",
      "system": "webtools-http-budget",
      "where": "common/ and jvm/ (unit 9, #68)",
      "gap": {
        "kind": "missing",
        "summary": "The budget takes signals through `complete` and publishes `maxBurst`; it does not call a reader in a pipeline or size the transport's dispatcher, though OkHttp's queue is invisible to the wait cap and to the call timeout.",
        "evidence": [
          "../architecture.md §6.5 — does not own retries, the transport's dispatcher or multi-process counting",
          "../architecture.md §11 unit 9 — the `maxBurst` sizing and the cache, budget, transport closing order",
          "../research/call-budget.md Considerations 2 — burst against the dispatcher"
        ],
        "source": "../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "unit-9",
          "name": "Left to unit 9 (#68), the call facades",
          "verdict": "accepted",
          "why": "#55's decomposition gives them to the unit that composes the cache, the budget and the transport.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        },
        {
          "id": "spacing",
          "name": "A `minSpacing` between grants, or a `maxConcurrent` semaphore in the budget",
          "verdict": "declined",
          "why": "Interactive grants would need slots between planned tracker slots, which the ledger does not plan; a semaphore would need a lease timeout for permits never completed.",
          "source": "../research/call-budget.md Considerations 2"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "Nothing of acquiring inside the cache's loader, releasing or completing by failure kind, reading signals from a real response or sizing the dispatcher is built: nothing in the repository acquires a permit yet, and the call facades (#68) are the first callers.",
            "state": "as-planned",
            "ref": "consequences.md#3-adoption",
            "excerpt": {
              "ref": "consequences.md#3-adoption",
              "heading": "3. Adoption",
              "paragraph": "No existing class budgets calls, so nothing is moved onto the budget. Its first users are the call facades (#68). · `VirtualClockThreeClocksComponentTest` keeps its plain Caffeine cache. It proves the virtual clock apart from the stack's components; the new `WindowedCallBudgetThreeClocksComponentTest` proves the same with the real budget beside the real cache. · `WallClock`'s contract (\"its reading may step backwards\") has its first consumer that depends on it: the budget's clock rules are written against it.",
              "hash": "b65e3d4bb001"
            }
          },
          {
            "text": "`WindowedCallBudget.maxBurst`, the smallest window's enforced limit, is published as the most permits granted at one instant, for #68 to size the transport's concurrency.",
            "state": "as-planned",
            "ref": "consequences.md#4-sibling-units",
            "excerpt": {
              "ref": "consequences.md#4-sibling-units",
              "heading": "4. Sibling units",
              "paragraph": "`plan.md` §12 has the exact contracts, and the build holds them as written; the points below are as built. In short:",
              "hash": "72a80b0c9ec1"
            }
          },
          {
            "text": "`close()` is non-suspending and idempotent, second in the client's closing order; it writes nothing, so a client-owned budget's exact final write is a `flush()` before it, and a permit granted just after it returns is completed or released as usual.",
            "state": "as-planned",
            "ref": "consequences.md#4-sibling-units",
            "excerpt": {
              "ref": "consequences.md#4-sibling-units",
              "heading": "4. Sibling units",
              "paragraph": "`plan.md` §12 has the exact contracts, and the build holds them as written; the points below are as built. In short:",
              "hash": "72a80b0c9ec1"
            }
          },
          {
            "text": "`RateLimited.retryAfter = max(signals.retryAfter, pausedFor)` is not redundant as built, since the pause in force can be shorter or longer than `retryAfter`; which of the two the failure reports stays #68's to settle.",
            "state": "as-planned",
            "ref": "consequences.md#4-sibling-units",
            "excerpt": {
              "ref": "consequences.md#4-sibling-units",
              "heading": "4. Sibling units",
              "paragraph": "`plan.md` §12 has the exact contracts, and the build holds them as written; the points below are as built. In short:",
              "hash": "72a80b0c9ec1"
            }
          }
        ]
      },
      "verdict": {
        "kind": "leave",
        "defense": "These belong to the call facades, unit 9 (#68), which compose the cache, the budget and the transport.",
        "statements": [
          {
            "text": "Acquiring inside the cache's loader, releasing or completing by failure kind, reading signals from a real response and sizing the dispatcher are #68's, and nothing of them is built here.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "Retries (never), the transport's dispatcher sizing (the budget publishes `maxBurst`; #68 sizes), multi-process counting (a snapshot store is last-writer-wins; an atomic shared counter would be a second `CallBudget` implementation). · Comparing the server's `Date` with the wall clock to detect a wrong forward clock (a follow-up, §13). · A file-backed or shared-counter `BudgetStore`; a serializer for `BudgetSnapshot`. · Calendar-month windows, per-window costs and fractional costs; dynamic header-declared rules (readings for unknown window names are ignored); the IETF `RateLimit` headers. · …",
              "hash": "3cccbf4a3ebd"
            }
          },
          {
            "text": "The budget publishes `maxBurst`, the smallest window's enforced limit, as the most permits granted at one instant, for sizing the transport's concurrency.",
            "ref": "plan.md#12-interfaces-with-sibling-units",
            "excerpt": {
              "ref": "plan.md#12-interfaces-with-sibling-units",
              "heading": "12. Interfaces with sibling units",
              "paragraph": "Names this unit takes (a sibling must not reuse them).",
              "hash": "e0ee8fb87fc9"
            }
          },
          {
            "text": "`close()` stands second in the client's closing order, and a client that owns its budget calls `flush()` before it for the exact final write.",
            "ref": "plan.md#12-interfaces-with-sibling-units",
            "excerpt": {
              "ref": "plan.md#12-interfaces-with-sibling-units",
              "heading": "12. Interfaces with sibling units",
              "paragraph": "Names this unit takes (a sibling must not reuse them).",
              "hash": "e0ee8fb87fc9"
            }
          },
          {
            "text": "`RateLimited.retryAfter = max(signals.retryAfter, pausedFor)` is not redundant, and which of the two the failure reports is #68's to settle.",
            "ref": "plan.md#12-interfaces-with-sibling-units",
            "excerpt": {
              "ref": "plan.md#12-interfaces-with-sibling-units",
              "heading": "12. Interfaces with sibling units",
              "paragraph": "Names this unit takes (a sibling must not reuse them).",
              "hash": "e0ee8fb87fc9"
            }
          }
        ],
        "steps": [],
        "tests": [],
        "risks": [],
        "source": "plan.md §4, §6.11, §12",
        "link": "plan.md#12-interfaces-with-sibling-units"
      }
    },
    {
      "id": "not-built",
      "group": "Left for later",
      "name": "Multi-process counting, a file-backed store, calendar-month windows and per-window costs",
      "system": "webtools-http-budget",
      "where": "not built",
      "gap": {
        "kind": "missing",
        "summary": "A snapshot store is last-writer-wins, so a budget here counts one process; a store on disk, calendar months and costs that differ by window are not modelled either.",
        "evidence": [
          "../architecture.md §4 — all four are non-goals of the stack",
          "../research/call-budget.md F7 — counting across processes needs an atomic increment in shared state",
          "../research/call-budget.md F3 — fractional or per-window costs need scaled windows or a later extension"
        ],
        "source": "../architecture.md §4",
        "link": "../architecture.md#4-non-goals"
      },
      "options": [
        {
          "id": "non-goals",
          "name": "Left out: the port takes a Bucket4j- or Redis-backed budget later, and a store is the consumer's to write",
          "verdict": "accepted",
          "why": "BotTools has its own database, and a Bucket4j- or Redis-backed `CallBudget` is a later implementation of the port.",
          "source": "../architecture.md §4, §10; ../research/call-budget.md Considerations 5",
          "link": "../architecture.md#4-non-goals"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "Multi-process counting, a file-backed or shared-counter `BudgetStore` and a serializer for `BudgetSnapshot` are not built: a snapshot store is last-writer-wins, and the first two stand as a follow-up.",
            "state": "as-planned",
            "ref": "consequences.md#7-follow-ups-this-unit-leaves",
            "excerpt": {
              "ref": "consequences.md#7-follow-ups-this-unit-leaves",
              "heading": "7. Follow-ups this unit leaves",
              "paragraph": "Each is still open. `plan.md` §13 names the first eight and §4 holds them out of this unit; the last two come from the build, each against the plan section it would refine.",
              "hash": "ab03fe72d0e6"
            }
          },
          {
            "text": "Calendar-month windows and per-window and fractional costs are not modelled, each a follow-up, and header-declared rules are a known gap: a reading for a window name the budget does not have is ignored.",
            "state": "as-planned",
            "ref": "consequences.md#8-known-gaps",
            "excerpt": {
              "ref": "consequences.md#8-known-gaps",
              "heading": "8. Known gaps",
              "paragraph": "The close-race test does not see the loss of the admission's latch re-check. With the re-check at the top of `admitLocked` removed, `WindowedCallBudgetCloseRaceNonFunctionalTest` passed in 5 of 5 runs (P3, `final-implementation.md` §4): on its 1-per-minute window a call parked on the lock is queued once the window's unit is taken, and its first step section returns `Closed` anyway, so the mutation changes no outcome the class can see. The re-check is held on virtual time by `WindowedCallBudgetCloseComponentTest`, whose parked-call case fails without it. · The window between a waiter's section …",
              "hash": "c72293fbe887"
            }
          },
          {
            "text": "The five gated seams stay Experimental, moving to `@SupportedExtension` once BotTools' adapter has built against them, a follow-up recorded in `CHANGELOG.md` when it happens.",
            "state": "as-planned",
            "ref": "consequences.md#7-follow-ups-this-unit-leaves",
            "excerpt": {
              "ref": "consequences.md#7-follow-ups-this-unit-leaves",
              "heading": "7. Follow-ups this unit leaves",
              "paragraph": "Each is still open. `plan.md` §13 names the first eight and §4 holds them out of this unit; the last two come from the build, each against the plan section it would refine.",
              "hash": "ab03fe72d0e6"
            }
          }
        ]
      },
      "verdict": {
        "kind": "leave",
        "defense": "A snapshot store is last-writer-wins and the port takes another implementation later, so none of these is needed for the stack's first release.",
        "statements": [
          {
            "text": "Multi-process counting, a file-backed or shared-counter `BudgetStore` and a serializer for `BudgetSnapshot` are not built.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "Retries (never), the transport's dispatcher sizing (the budget publishes `maxBurst`; #68 sizes), multi-process counting (a snapshot store is last-writer-wins; an atomic shared counter would be a second `CallBudget` implementation). · Comparing the server's `Date` with the wall clock to detect a wrong forward clock (a follow-up, §13). · A file-backed or shared-counter `BudgetStore`; a serializer for `BudgetSnapshot`. · Calendar-month windows, per-window costs and fractional costs; dynamic header-declared rules (readings for unknown window names are ignored); the IETF `RateLimit` headers. · …",
              "hash": "3cccbf4a3ebd"
            }
          },
          {
            "text": "Calendar-month windows, per-window and fractional costs, and header-declared rules are not modelled; a reading for an unknown window name is ignored.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "Retries (never), the transport's dispatcher sizing (the budget publishes `maxBurst`; #68 sizes), multi-process counting (a snapshot store is last-writer-wins; an atomic shared counter would be a second `CallBudget` implementation). · Comparing the server's `Date` with the wall clock to detect a wrong forward clock (a follow-up, §13). · A file-backed or shared-counter `BudgetStore`; a serializer for `BudgetSnapshot`. · Calendar-month windows, per-window costs and fractional costs; dynamic header-declared rules (readings for unknown window names are ignored); the IETF `RateLimit` headers. · …",
              "hash": "3cccbf4a3ebd"
            }
          },
          {
            "text": "The seams stay Experimental until a consumer has built against them, and each of these stands as a follow-up.",
            "ref": "plan.md#13-sequencing-and-follow-ups",
            "excerpt": {
              "ref": "plan.md#13-sequencing-and-follow-ups",
              "heading": "13. Sequencing and follow-ups",
              "paragraph": "Order. Stage 1 (commit 1) → stage 2 (commit 2) → stage 3 (commit 3) → stage 4 (commit 4) → stage 5 (commit 5) → documentation (commit 6) → as-built reconciliation (commit 7). Stage 2 depends on stage 1 only; stage 3 on stage 1; stage 4 on stages 2 and 3; stage 5 on stage 4; commit 6 can be written any time after stage 4 but lands after the code. #66 has landed; this unit is independent of #69 and may land before or after it; #68 needs it.",
              "hash": "72e0bf336f4f"
            }
          }
        ],
        "steps": [],
        "tests": [],
        "risks": [],
        "source": "plan.md §4, §13",
        "link": "plan.md#13-sequencing-and-follow-ups"
      }
    }
  ],
  "impact": {
    "basis": "as-built",
    "summary": "`webtools-http`, new and unreleased, gains the call budget, all of it common code: the `CallBudget` port with `CallBudget.Unlimited`, `Permit` and `withPermit`, its vocabulary, `RetryAfter`, the store and observer seams, and the supplied `WindowedCallBudget`. Implementing a gated port or subclassing `BudgetEvent` needs the opt-in, and everything else is Stable Core. The fixtures gain two fakes. No dependency changes, the three existing artifacts do not, no version moves, and the call facades (#68) are its first callers.",
    "none": null,
    "release": {
      "bump": "none",
      "version": null,
      "note": "`webtools-http` 1.0.0 stays new and unreleased until #55's stack is complete; this landing is not a release candidate",
      "unreleased": true
    },
    "downstream": [
      {
        "who": "`io.github.spartanlaboratories:webtools-http` (new artifact)",
        "kind": "artifact",
        "system": "webtools-http-budget",
        "effect": "additive",
        "summary": "The main variants gain the call budget in common code, and the ABI dump grows by exactly the classes of plan.md §7.23, from 557 to 892 lines. The test-fixtures variant gains `RecordingBudgetObserver` and `FakeBudgetStore`, whose `failLoads` and `failSaves` refuse a `times` below 1, and no dependency. No dependency of either main variant changes, as `kotlinx-coroutines-core` 1.10.2 was already an `api` dependency.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Consumers of `webtools-http` 1.0.0, once it is released",
        "kind": "consumer",
        "system": "webtools-http-budget",
        "effect": "additive",
        "summary": "Permits under a provider's own windows with a boundary guard, fair waiting that fails fast, pauses from the server, a hard daily stop that survives a restart while saves succeed, with `flush()` writing the exact counts, a clean close that ends every waiting call with `Closed(\"budget\")`, an observer and logs without secrets, and clock behaviour that holds a bill. `timeSource` and `wallClock` are builder properties, so the published `VirtualClock` drives the budget under test. The known gaps an operator meets are in consequences.md §8.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "`webtools-udp`, `webtools-scraping`, `webtools-browser`",
        "kind": "artifact",
        "system": null,
        "effect": "none",
        "summary": "No source, build-script or test change, and no version moves.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Unit 7, the response cache (#66)",
        "kind": "system",
        "system": null,
        "effect": "none",
        "summary": "Nothing changes for it. Its loader rules hold: a refusal and `Closed` are values, the only `CancellationException` the budget throws is its caller's own, and a store's `Error` reaches the loader as an `Error`, which the cache rethrows to every caller of the load.",
        "link": "consequences.md#4-sibling-units"
      },
      {
        "who": "This repository's build",
        "kind": "repository",
        "system": "webtools-http-budget",
        "effect": "additive",
        "summary": "No build file changes. `./gradlew build` does more work: the new tests of `webtools-http`, the fixtures-publication check again, `apiCheck` against the regenerated dump and the check module's `js` compile of the new common files.",
        "link": "consequences.md#build"
      }
    ],
    "adaptation": [
      {
        "who": "Unit 9, the call facades (#68)",
        "system": "webtools-http-budget",
        "need": "required",
        "summary": "It calls `acquire` inside the cache's loader, where a refusal and a closed budget are values and a `cost` below 1 or a negative `maxWait` throw, so it validates a call's settings first; an `Error` the budget's store throws propagates out of `acquire`, possibly as a stack-trace-recovery copy whose `cause` is the original. It calls `permit.release()` for a request never sent and `complete(signals)` otherwise, a permit granted just after `close()` returns included, reads signals with `RateSignalsReader.Standard` or a per-provider reader, sizes the transport's concurrency from `maxBurst`, and closes second, after a `flush()` for the exact final write of a budget it owns.",
        "link": "consequences.md#4-sibling-units"
      },
      {
        "who": "An implementer of `CallBudget`, `Permit`, `RateSignalsReader`, `BudgetStore` or `BudgetObserver`, or a subclass of `BudgetEvent`",
        "system": "webtools-http-budget",
        "need": "optional",
        "summary": "It needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; calling the ports, passing them and using the supplied defaults need nothing. A `BudgetStore` must be cancellable, as the budget bounds each call with a timeout that cannot interrupt blocking code.",
        "link": "plan.md#r1-the-public-surface-and-its-tier"
      },
      {
        "who": "Unit 10, the feeds (#69)",
        "system": "webtools-http-budget",
        "need": "none",
        "summary": "Nothing directly: `UrlFetcher` takes a `CallBudget`, `Unlimited` by default.",
        "link": "consequences.md#4-sibling-units"
      },
      {
        "who": "Unit 11, the documentation (#70)",
        "system": "webtools-http-budget",
        "need": "none",
        "summary": "It documents the budget from this unit's KDoc, README row and CHANGELOG entries: the clock rules and their limit, what a failing store and a store's `Error` do, and the known gaps an operator meets.",
        "link": "consequences.md#4-sibling-units"
      }
    ],
    "api": [
      {
        "surface": "`CallBudget`",
        "system": "webtools-http-budget",
        "change": "added",
        "tier": "experimental",
        "summary": "The port: `acquire`, `utilisation`, `flush` and `close`. Implementing it is Experimental behind the opt-in; calling it, passing it and naming it in a signature are Stable Core.",
        "link": "plan.md#r2-the-callbudget-port-and-its-contract"
      },
      {
        "surface": "`CallBudget.Unlimited`",
        "system": "webtools-http-budget",
        "change": "added",
        "tier": "stable-core",
        "summary": "The pass-through that grants at once and ignores signals and `close()`.",
        "link": "plan.md#r3-permit-withpermit-and-callbudgetunlimited"
      },
      {
        "surface": "`Permit` and `withPermit`",
        "system": "webtools-http-budget",
        "change": "added",
        "tier": "experimental",
        "summary": "`complete`, `complete(signals)` and `release`, idempotent and under `NonCancellable`. Implementing `Permit` is Experimental; calling it and `withPermit` are Stable Core.",
        "link": "plan.md#r3-permit-withpermit-and-callbudgetunlimited"
      },
      {
        "surface": "`Lane`, `RateWindow`, `RateSignals`, `WindowReading`, `BudgetUtilisation`, `WindowUtilisation`",
        "system": "webtools-http-budget",
        "change": "added",
        "tier": "stable-core",
        "summary": "The vocabulary, unmarked, so calling `complete` needs no opt-in.",
        "link": "plan.md#r4-the-vocabulary"
      },
      {
        "surface": "`RetryAfter`",
        "system": "webtools-http-budget",
        "change": "added",
        "tier": "stable-core",
        "summary": "`parse` with and without a server date: seconds or any of RFC 9110's three date forms, never negative and always finite.",
        "link": "plan.md#r5-retryafter"
      },
      {
        "surface": "`RateSignalsReader` and `RateSignalsReader.Standard`",
        "system": "webtools-http-budget",
        "change": "added",
        "tier": "experimental",
        "summary": "Implementing the reader is Experimental; using it and `Standard` are Stable Core.",
        "link": "plan.md#r6-ratesignalsreader-and-standard"
      },
      {
        "surface": "`BudgetStore`, `InMemoryBudgetStore`, `BudgetSnapshot`, `WindowSnapshot`",
        "system": "webtools-http-budget",
        "change": "added",
        "tier": "experimental",
        "summary": "Implementing the store is Experimental; the in-memory default and the snapshot values are Stable Core.",
        "link": "plan.md#r7-the-seams-store-snapshots-observer-events"
      },
      {
        "surface": "`BudgetObserver`, `BudgetEvent`, `PauseSource`, `StoreOperation`",
        "system": "webtools-http-budget",
        "change": "added",
        "tier": "experimental",
        "summary": "Implementing the observer and subclassing `BudgetEvent` are Experimental; `BudgetObserver.None`, the seven leaves and the two vocabularies are Stable Core.",
        "link": "plan.md#r7-the-seams-store-snapshots-observer-events"
      },
      {
        "surface": "`WindowedCallBudget`",
        "system": "webtools-http-budget",
        "change": "added",
        "tier": "stable-core",
        "summary": "The supplied default, by factory and builder, with `maxBurst`.",
        "link": "plan.md#r8-windowedcallbudget--construction"
      },
      {
        "surface": "`RecordingBudgetObserver`, `FakeBudgetStore` (test fixtures)",
        "system": "webtools-http-budget",
        "change": "added",
        "tier": "stable-core",
        "summary": "Stable Core with source compatibility only.",
        "link": "plan.md#r16-fixtures"
      }
    ],
    "conversion": [],
    "changes": [
      {
        "entry": "downstream.0",
        "system": "webtools-http-budget",
        "what": "`FakeBudgetStore.failLoads` and `failSaves` in the published fixtures refuse a `times` below 1 with `IllegalArgumentException`, where the plan's listing armed any count.",
        "why": "`times = 0` delivered one failure instead of none; a script of zero failures is `heal()`, so the fixture refuses it rather than deliver one, and states the contract in its KDoc.",
        "when": "qa pass 2 fixes",
        "ref": "final-implementation.md#d8-fakebudgetstorefailloads-and-failsaves-refuse-a-times-below-1"
      },
      {
        "entry": "downstream.1",
        "system": "webtools-http-budget",
        "what": "The hard daily stop keeps a crash from under-counting only while saves succeed, and `flush()` also writes a zero row for a period whose grants were all released; a call inside its admission section when `close()` runs may still be granted.",
        "why": "The build found the limits of the planned write-ahead and close: a grant after three saves that each outlast their period, grants made while saves fail and a count a server reading or a clock step raised run unsaved, and the admission re-checks the latch only at the top of its section. The zero row (D7) keeps the bound for released grants; the rest are known gaps, two with follow-ups.",
        "when": "#67 landing",
        "ref": "consequences.md#8-known-gaps"
      },
      {
        "entry": "downstream.3",
        "system": null,
        "what": "A store's `Error` reaches the response cache's loader as an `Error`, which the cache rethrows to every caller of the load; nothing changes for the cache.",
        "why": "The build settled what a store's `Error` does: it propagates to the caller of the member that made the store call and is never a store failure.",
        "when": "#67 landing",
        "ref": "consequences.md#4-sibling-units"
      },
      {
        "entry": "adaptation.0",
        "system": "webtools-http-budget",
        "what": "The call facades also meet a store's `Error` propagating out of `acquire`, possibly as a stack-trace-recovery copy whose `cause` is the original, and complete or release a permit granted just after `close()` returns.",
        "why": "An `Error` crosses `withTimeoutOrNull`, so with stack-trace recovery on the caller receives a copy (a follow-up rethrows the instance); the admission's latch check at the top of its section lets a call whose store call spans a `close()` be granted.",
        "when": "#67 landing",
        "ref": "consequences.md#4-sibling-units"
      },
      {
        "entry": "adaptation.3",
        "system": "webtools-http-budget",
        "what": "The documentation unit also documents what a failing store and a store's `Error` do, and the known gaps an operator meets.",
        "why": "The known gaps the build leaves are an operator's to know, so the evergreen documentation carries them beside the clock rules.",
        "when": "#67 landing",
        "ref": "consequences.md#4-sibling-units"
      }
    ],
    "source": "consequences.md; final-implementation.md §1, §2",
    "link": "consequences.md#1-published-artifacts"
  },
  "tests": {
    "base": "3091e92",
    "updated": "2026-11-04T05:31:02Z",
    "added": [
      {
        "class": "LaneComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/LaneComponentTest.kt",
        "tests": 4,
        "rows": [
          "vocabulary",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "RateWindowComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/RateWindowComponentTest.kt",
        "tests": 9,
        "rows": [
          "vocabulary",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "RateSignalsComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/RateSignalsComponentTest.kt",
        "tests": 8,
        "rows": [
          "vocabulary",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "RateSignalsReaderStandardComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/RateSignalsReaderStandardComponentTest.kt",
        "tests": 11,
        "rows": [
          "signals-reader",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "BudgetSnapshotComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/BudgetSnapshotComponentTest.kt",
        "tests": 7,
        "rows": [
          "hard-stop",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "BudgetUtilisationComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/BudgetUtilisationComponentTest.kt",
        "tests": 6,
        "rows": [
          "vocabulary",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "BudgetEventComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/BudgetEventComponentTest.kt",
        "tests": 9,
        "rows": [
          "observer",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "BudgetVocabularyComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/BudgetVocabularyComponentTest.kt",
        "tests": 4,
        "rows": [
          "observer",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "InMemoryBudgetStoreComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/InMemoryBudgetStoreComponentTest.kt",
        "tests": 4,
        "rows": [
          "hard-stop",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "CallBudgetUnlimitedComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CallBudgetUnlimitedComponentTest.kt",
        "tests": 7,
        "rows": [
          "port",
          "permit",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "HttpDateDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HttpDateDeterministicTest.kt",
        "tests": 10,
        "rows": [
          "signals-reader",
          "deterministic-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "RetryAfterDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/RetryAfterDeterministicTest.kt",
        "tests": 8,
        "rows": [
          "signals-reader",
          "deterministic-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "HttpDateJavaTimeDifferentialDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HttpDateJavaTimeDifferentialDeterministicTest.kt",
        "tests": 3,
        "rows": [
          "signals-reader",
          "deterministic-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "WindowArithmeticDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/WindowArithmeticDeterministicTest.kt",
        "tests": 8,
        "rows": [
          "windowed",
          "time",
          "deterministic-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "ShareRoundingDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/ShareRoundingDeterministicTest.kt",
        "tests": 3,
        "rows": [
          "lanes",
          "deterministic-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "CellFoldDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/CellFoldDeterministicTest.kt",
        "tests": 9,
        "rows": [
          "time",
          "deterministic-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "PauseDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/PauseDeterministicTest.kt",
        "tests": 7,
        "rows": [
          "pauses",
          "time",
          "deterministic-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "BudgetLedgerPlanDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/BudgetLedgerPlanDeterministicTest.kt",
        "tests": 19,
        "rows": [
          "windowed",
          "lanes",
          "pauses",
          "hard-stop",
          "deterministic-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 19,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "BudgetLedgerSnapshotDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/BudgetLedgerSnapshotDeterministicTest.kt",
        "tests": 10,
        "rows": [
          "hard-stop",
          "deterministic-tests"
        ],
        "stage": "qa pass 1 fixes",
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-04T05:16:24Z"
        }
      },
      {
        "class": "BudgetLedgerPropertyDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/BudgetLedgerPropertyDeterministicTest.kt",
        "tests": 2,
        "rows": [
          "windowed",
          "lanes",
          "hard-stop",
          "deterministic-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T02:18:59Z"
        }
      },
      {
        "class": "RecordingBudgetObserverComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/RecordingBudgetObserverComponentTest.kt",
        "tests": 8,
        "rows": [
          "fixtures",
          "component-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "FakeBudgetStoreComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/FakeBudgetStoreComponentTest.kt",
        "tests": 11,
        "rows": [
          "fixtures",
          "component-tests"
        ],
        "stage": "qa pass 2 fixes",
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T05:31:02Z"
        }
      },
      {
        "class": "CallBudgetConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/http/CallBudgetConsumerGatingTest.kt",
        "tests": 5,
        "rows": [
          "port",
          "permit",
          "vocabulary",
          "signals-reader",
          "windowed",
          "hard-stop",
          "observer"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/http/WindowedCallBudgetConsumerGatingTest.kt",
        "tests": 2,
        "rows": [
          "port",
          "permit",
          "windowed",
          "close"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WithPermitComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WithPermitComponentTest.kt",
        "tests": 10,
        "rows": [
          "permit",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetConstructionComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetConstructionComponentTest.kt",
        "tests": 16,
        "rows": [
          "windowed",
          "lanes",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 16,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetFifoComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetFifoComponentTest.kt",
        "tests": 10,
        "rows": [
          "port",
          "windowed",
          "lanes",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetRefundComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetRefundComponentTest.kt",
        "tests": 9,
        "rows": [
          "permit",
          "windowed",
          "pauses",
          "component-tests"
        ],
        "stage": "qa pass 1 fixes",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-04T05:16:24Z"
        }
      },
      {
        "class": "WindowedCallBudgetLaneFloorComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetLaneFloorComponentTest.kt",
        "tests": 6,
        "rows": [
          "lanes",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetPauseComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetPauseComponentTest.kt",
        "tests": 11,
        "rows": [
          "pauses",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetReconcileComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetReconcileComponentTest.kt",
        "tests": 5,
        "rows": [
          "pauses",
          "hard-stop",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetGuardComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetGuardComponentTest.kt",
        "tests": 6,
        "rows": [
          "windowed",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetHardWindowComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetHardWindowComponentTest.kt",
        "tests": 5,
        "rows": [
          "hard-stop",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetRestoreComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetRestoreComponentTest.kt",
        "tests": 12,
        "rows": [
          "hard-stop",
          "close",
          "component-tests"
        ],
        "stage": "qa pass 2 fixes",
        "result": {
          "state": "passed",
          "passed": 12,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T05:31:02Z"
        }
      },
      {
        "class": "WindowedCallBudgetCrashComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetCrashComponentTest.kt",
        "tests": 4,
        "rows": [
          "hard-stop",
          "component-tests"
        ],
        "stage": "qa pass 1 fixes",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-04T05:16:24Z"
        }
      },
      {
        "class": "WindowedCallBudgetStoreFailureComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetStoreFailureComponentTest.kt",
        "tests": 8,
        "rows": [
          "hard-stop",
          "observer",
          "component-tests"
        ],
        "stage": "qa pass 2 fixes",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T05:31:02Z"
        }
      },
      {
        "class": "WindowedCallBudgetCloseComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetCloseComponentTest.kt",
        "tests": 9,
        "rows": [
          "close",
          "component-tests"
        ],
        "stage": "qa pass 2 fixes",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T05:31:02Z"
        }
      },
      {
        "class": "WindowedCallBudgetCancellationComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetCancellationComponentTest.kt",
        "tests": 3,
        "rows": [
          "permit",
          "windowed",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetObserverComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetObserverComponentTest.kt",
        "tests": 5,
        "rows": [
          "observer",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetLoggingComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetLoggingComponentTest.kt",
        "tests": 5,
        "rows": [
          "observer",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetThreeClocksComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetThreeClocksComponentTest.kt",
        "tests": 2,
        "rows": [
          "time",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetClockStepComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetClockStepComponentTest.kt",
        "tests": 9,
        "rows": [
          "windowed",
          "time",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "WindowedCallBudgetClockPauseComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WindowedCallBudgetClockPauseComponentTest.kt",
        "tests": 6,
        "rows": [
          "pauses",
          "hard-stop",
          "time",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:05:26Z"
        }
      },
      {
        "class": "RateSignalsReaderLoopbackIntegrationTest",
        "module": "webtools-http",
        "testLevel": "3",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/RateSignalsReaderLoopbackIntegrationTest.kt",
        "tests": 6,
        "rows": [
          "signals-reader",
          "integration-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-04T05:16:24Z"
        }
      },
      {
        "class": "WindowedCallBudgetCloseRaceNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/WindowedCallBudgetCloseRaceNonFunctionalTest.kt",
        "tests": 1,
        "rows": [
          "close",
          "nonfunctional-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-04T05:16:24Z"
        }
      },
      {
        "class": "WindowedCallBudgetReplanCostNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/WindowedCallBudgetReplanCostNonFunctionalTest.kt",
        "tests": 1,
        "rows": [
          "windowed",
          "nonfunctional-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-04T05:16:24Z"
        }
      },
      {
        "class": "WindowedCallBudgetContentionNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/WindowedCallBudgetContentionNonFunctionalTest.kt",
        "tests": 1,
        "rows": [
          "windowed",
          "nonfunctional-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-04T05:16:24Z"
        }
      }
    ],
    "amended": [
      {
        "class": "WebToolsDslComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WebToolsDslComponentTest.kt",
        "tests": 1,
        "rows": [
          "vocabulary",
          "windowed",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:01:57Z"
        }
      },
      {
        "class": "VirtualClockThreeClocksComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/VirtualClockThreeClocksComponentTest.kt",
        "tests": 2,
        "rows": [
          "time",
          "component-tests"
        ],
        "stage": "S4",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T03:02:01Z"
        }
      },
      {
        "class": "FixturesConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/http/testing/FixturesConsumerGatingTest.kt",
        "tests": 6,
        "rows": [
          "fixtures"
        ],
        "stage": "qa pass 2 fixes",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-04T05:31:02Z"
        }
      }
    ],
    "relevant": []
  },
  "roadmap": {
    "issue": 55,
    "title": "HTTP client stack for BotTools: suspend transport, outbound-URL guard, response cache, call budget and test fakes",
    "asOf": "2026-11-03",
    "items": [
      {
        "issue": 61,
        "title": "Build: move shared configuration into an included `build-logic` build with convention plugins and package-selected level tasks",
        "state": "CLOSED",
        "stateReason": "COMPLETED"
      },
      {
        "issue": 47,
        "title": "webtools-udp 2.0.0 ships Java 23 bytecode but README claims JDK 11+",
        "state": "CLOSED",
        "stateReason": "COMPLETED"
      },
      {
        "issue": 62,
        "title": "webtools-http: module skeleton, opt-in marker, failure model, request/response types, transport and wall-clock ports, logging seam and the check-only `js` compile",
        "state": "CLOSED",
        "stateReason": "COMPLETED"
      },
      {
        "issue": 63,
        "title": "webtools-http: published test-fixtures variant with the fake transport, virtual clock, `.http` fixtures, recorder and the publication check",
        "state": "CLOSED",
        "stateReason": "COMPLETED"
      },
      {
        "issue": 64,
        "title": "webtools-http: outbound-URL policies `Trusted`/`Untrusted`, `TransportSettings` and the common address classifier",
        "state": "CLOSED",
        "stateReason": "COMPLETED"
      },
      {
        "issue": 65,
        "title": "webtools-http: `OkHttpTransport` with manual redirects, the one-exchange guard, the resolver and socket guards, and the loopback test factories",
        "state": "CLOSED",
        "stateReason": "COMPLETED"
      },
      {
        "issue": 66,
        "title": "webtools-http: `ResponseCache` port and `CaffeineResponseCache` default",
        "state": "CLOSED",
        "stateReason": "COMPLETED"
      },
      {
        "issue": 67,
        "title": "webtools-http: `CallBudget` port, `WindowedCallBudget`, rate signals, the persistence and observer seams and their fakes",
        "state": "OPEN"
      },
      {
        "issue": 68,
        "title": "webtools-http: `UpstreamClient` and `UrlFetcher` composing cache, budget and transport",
        "state": "OPEN"
      },
      {
        "issue": 69,
        "title": "webtools-feeds: RSS/Atom parser port with the Rome default, hardening suite, and a conditional-GET fetcher over `UrlFetcher`",
        "state": "OPEN"
      },
      {
        "issue": 70,
        "title": "Docs: evergreen `docs/webtools-http-architecture.md` and the README's HTTP-stack and feeds sections",
        "state": "OPEN"
      }
    ]
  },
  "followUps": [],
  "documents": [
    {
      "label": "Issue #67",
      "path": "https://github.com/SpartanLaboratories/WebTools/issues/67",
      "note": "the unit as #55 filed it"
    },
    {
      "label": "#67 plan",
      "path": "plan.md",
      "note": "the implementer's spec: §1 the requirements, §5 the stages, §6 the design and §6.11 what it refines in #55's architecture, §9 the test plan, §12 the interfaces with the sibling units"
    },
    {
      "label": "#67 consequences",
      "path": "consequences.md",
      "note": "what the unit does to its surroundings and leaves behind: the published artifact, the blast radius, the sibling units, the follow-ups and the known gaps"
    },
    {
      "label": "#67 as-built record",
      "path": "final-implementation.md",
      "note": "every deviation from the plan as it was made; reconciled into the final record once implementation ends"
    },
    {
      "label": "#67 research",
      "path": "research/README.md",
      "note": "three questions, the verification and the summary the plan's design follows"
    },
    {
      "label": "#55 research",
      "path": "../research/README.md",
      "note": "the questions, verification and summary the Gap column rests on; question 7 bears on this unit"
    },
    {
      "label": "#55 call budget findings",
      "path": "../research/call-budget.md",
      "note": "F1 to F11, the measured algorithms, reservations, lanes, signals and crashes, and the considerations behind the options"
    },
    {
      "label": "#55 architecture",
      "path": "../architecture.md",
      "note": "§6.5 the budget's design, §10 the alternatives, §11 this unit's row, §13 the open point on `close()`"
    },
    {
      "label": "#55 consequences",
      "path": "../consequences.md",
      "note": "what the stack leaves each later unit, this one's open point and what it takes from units 3 and 4"
    },
    {
      "label": "#63 plan",
      "path": "../63-test-support/plan.md",
      "note": "§12: the fixture names reserved for this unit, and the three-clocks proof it re-runs"
    }
  ],
  "notes": {
    "paths": "`common/` abbreviates `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/`, as #55's architecture does. Line references marked baseline are to `8122af7`.",
    "origin": "This unit's architecture happened in #55's run, so the Gap and Considered columns were written at sized from #55's research README, its call budget findings and its architecture; the unit's own research adds its evidence to the Gap column, and its plan adds to Considered and writes the Verdict at plan acceptance.",
    "corrections": [
      {
        "what": "The `hard-stop` and `time` rows' Gap linked and cited research/README.md § Open trade-offs, a heading commit 3091e92 replaced. They now link and cite § A backward clock step, and a snapshot stamped ahead, which holds that content.",
        "when": "plan accepted, 2026-11-04"
      },
      {
        "what": "The same two rows' Gap, their evidence and the research milestone kept a choice each as left open. Both were decided before the plan was accepted, in line with plan.md R12, R13 and §6.11, so they now read as decided. Corrected in place.",
        "when": "plan accepted, 2026-11-04"
      },
      {
        "what": "Eleven as-built references named sections of final-implementation.md that its reconciliation renamed or folded in: the procedures P1 to P3 and the runs of record now point at §4 Measurements, and D1, D3, D4, D5 and D6 at their renamed headings. The statements are unchanged; the excerpts are copied again.",
        "when": "Document step, state boards, 2026-11-05"
      }
    ]
  },
  "issueBoards": {
    "47": "../47-java-23-bytecode/dashboard.html",
    "61": "../61-build-logic/dashboard.html",
    "62": "../62-http-core/dashboard.html",
    "63": "../63-test-support/dashboard.html",
    "64": "../64-url-policy/dashboard.html",
    "65": "../65-okhttp-transport/dashboard.html",
    "66": "../66-response-cache/dashboard.html",
    "68": "../68-call-facades/dashboard.html"
  },
  "links": {
    "workBoard": "../../index.html",
    "repoBoard": "../../../dashboards/index.html",
    "boards": {
      "webtools-http-budget": "../../../dashboards/webtools-http-budget/index.html"
    }
  }
}
);
