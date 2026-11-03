/* plan.js: plan.json as a script, written by plan/scripts/embed.js beside the board for its live view (plan/RULES.md §1). Do not edit: edit plan.json and re-embed. */
window.dashboardLive&&window.dashboardLive("plan",
{
  "schema": "plan-board/1",
  "repo": "SpartanLaboratories/WebTools",
  "issue": 66,
  "slug": "66-response-cache",
  "folder": "docs/plans/55-http-client-stack/66-response-cache",
  "kind": "issue",
  "title": "Response cache",
  "issueTitle": "webtools-http: `ResponseCache` port and `CaffeineResponseCache` default",
  "function": "Nothing in `webtools-http` caches yet: #62 built the `Closed` failure and the gated `HttpTransport` port the cache's seam follows, #63 the `VirtualClock`, whose three-clocks proof runs on a stand-in Caffeine cache, and Caffeine 3.3.1 is declared on `jvmMain` with no main source using it (#55 architecture §6.4). This run adds the `ResponseCache` port, `CacheKey`, `CachePolicy`, `Lookup`, `CacheStats` and `ResponseCache.Disabled` in `commonMain` and the `CaffeineResponseCache` default in `jvmMain`, with its tests. Unit 7 of #55: the response cache subsystem of `webtools-http`, additive, no version moved; the loader's content is unit 9's.",
  "status": "building",
  "sized": "2026-11-03T02:17:16Z",
  "parent": {
    "issue": 55,
    "board": "../dashboard.html"
  },
  "fixOf": null,
  "branch": "feat/issue-66-response-cache",
  "baseline": {
    "ref": "master",
    "sha": "e8f0a88",
    "date": "2026-11-03"
  },
  "asBuilt": null,
  "systems": [
    {
      "slug": "webtools-http-cache",
      "name": "response cache",
      "slot": 5,
      "top": "webtools-http",
      "status": "built",
      "board": "docs/dashboards/webtools-http/index.html"
    }
  ],
  "wip": {
    "step": "document",
    "since": "2026-11-03T18:33:16Z",
    "updated": "2026-11-03T19:26:02Z",
    "detail": "Document: 6 of 6 tasks done; every task done.",
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
    "current": [
      "built"
    ],
    "questions": [],
    "research": null,
    "interview": [],
    "plan": null,
    "stages": [
      {
        "id": "S1",
        "name": "commonMain: Lookup, CacheKey, CachePolicy, CacheStats, LoaderRunner, ResponseCache and Disabled",
        "state": "done",
        "rows": [
          "port",
          "cache-key",
          "policy-lookup",
          "disabled",
          "failure-value",
          "component-tests",
          "deterministic-tests",
          "release"
        ]
      },
      {
        "id": "S2",
        "name": "jvmMain: CacheEntry and CaffeineResponseCache with the Level 1, 2 and 4a tests",
        "state": "done",
        "rows": [
          "caffeine-default",
          "shared-load",
          "failure-value",
          "max-age",
          "close",
          "component-tests",
          "deterministic-tests",
          "release"
        ]
      },
      {
        "id": "S3",
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
          "name": "Log the load's end only when its completion wins; record the change",
          "state": "done"
        }
      ]
    },
    "open": [
      {
        "text": "Procedure P4's mutation (the closed-flag re-check in startLoad removed) is not detected by CaffeineResponseCacheCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
        "raised": "implementation-stage S3",
        "ref": "final-implementation.md",
        "resolved": "2026-11-03T18:57:00Z"
      }
    ],
    "event": {
      "name": "state-boards",
      "at": "2026-11-03T19:26:02Z",
      "note": "the state boards; 6 of 6 tasks done"
    },
    "history": {
      "sizing": [
        {
          "since": null,
          "ended": "2026-11-03T02:17:16Z",
          "endedBy": "sized",
          "to": "plan"
        }
      ],
      "plan": [
        {
          "since": "2026-11-03T02:17:16Z",
          "ended": "2026-11-03T02:32:02Z",
          "endedBy": "research-planning",
          "to": "research",
          "event": {
            "name": "sized",
            "at": "2026-11-03T02:17:16Z"
          },
          "detail": "Unit 7 of the HTTP client stack: the ResponseCache port, CacheKey, CachePolicy, Lookup, CacheStats and ResponseCache.Disabled in commonMain, and the CaffeineResponseCache default in jvmMain. Working tree clean at e8f0a88; the run works in the main checkout. Next: the detailed implementation plan from architecture.md section 6.4 and section 11.",
          "plan": {
            "state": "re-pass",
            "from": "parent",
            "since": "2026-11-03T02:17:16Z"
          }
        },
        {
          "since": "2026-11-03T03:27:44Z",
          "ended": "2026-11-03T03:27:44Z",
          "endedBy": "plan-accepted",
          "to": "branch",
          "event": {
            "name": "resumed",
            "at": "2026-11-03T03:27:44Z"
          },
          "detail": "All four choices stand as drafted: the CacheStats fields, invalidate during an in-flight load, maxAge = ZERO served by age, Found weight at least 1.",
          "plan": {
            "state": "writing",
            "since": "2026-11-03T03:23:54Z"
          }
        }
      ],
      "research": [
        {
          "since": "2026-11-03T02:32:02Z",
          "ended": "2026-11-03T03:23:54Z",
          "endedBy": "researched",
          "to": "plan",
          "event": {
            "name": "verification-started",
            "at": "2026-11-03T02:49:21Z"
          },
          "detail": "Research: verifying 2 findings.",
          "research": {
            "phase": "complete",
            "drafted": 0,
            "since": {
              "planning": "2026-11-03T02:32:02Z",
              "research": "2026-11-03T02:34:16Z",
              "verification": "2026-11-03T02:49:21Z",
              "complete": "2026-11-03T03:23:54Z"
            }
          },
          "questions": [
            {
              "question": "caffeine-mechanics",
              "rows": [
                "caffeine-default",
                "policy-lookup",
                "failure-value",
                "close",
                "nonfunctional-tests"
              ],
              "findings": "research/caffeine-mechanics.md",
              "state": "complete",
              "started": "2026-11-03T02:34:16Z",
              "returned": "2026-11-03T02:48:03Z"
            },
            {
              "question": "load-lifecycle",
              "rows": [
                "shared-load",
                "failure-value",
                "close",
                "component-tests"
              ],
              "findings": "research/load-lifecycle.md",
              "state": "complete",
              "started": "2026-11-03T02:34:16Z",
              "returned": "2026-11-03T02:47:19Z"
            }
          ]
        }
      ],
      "branch": [
        {
          "since": "2026-11-03T03:27:44Z",
          "ended": "2026-11-03T03:28:38Z",
          "endedBy": "branch-cut",
          "to": "implementation",
          "event": {
            "name": "plan-accepted",
            "at": "2026-11-03T03:27:44Z"
          },
          "detail": "Full implementation plan for the ResponseCache port and the CaffeineResponseCache default: each load runs in the cache's own supervisor scope, every outcome reaches waiters as a value, waiters use asDeferred().await(), close() releases waiters, own counters instead of recordStats(). Interfaces pinned for the call facades (#68).",
          "branch": "feat/issue-66-response-cache"
        }
      ],
      "implementation": [
        {
          "since": "2026-11-03T03:28:38Z",
          "ended": "2026-11-03T04:23:52Z",
          "endedBy": "qa-pass",
          "to": "qa",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-03T04:20:35Z",
            "note": "S3 reported done; qa next"
          },
          "detail": "Five new classes. Procedure P3 (both lifetime-0 removals left out) was detected by the failure-identity test 5 of 5 runs. Procedure P4 (the closed-flag re-check in startLoad left out) was not detected in 5 runs: once the load scope is cancelled a launch completes without running its body and the completion handler settles the future as closed, so the re-check alone opens no hang; recorded for the known gaps. Module check: 883 jvm tests, 0 failed, 0 skipped. Correction to the previous event: componentTest ran 121 tests of the new and amended classes, not 139.",
          "open": [
            {
              "text": "Procedure P4's mutation (the closed-flag re-check in startLoad removed) is not detected by CaffeineResponseCacheCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
              "raised": "implementation-stage S3",
              "ref": "final-implementation.md"
            }
          ],
          "stages": [
            {
              "id": "S1",
              "name": "commonMain: Lookup, CacheKey, CachePolicy, CacheStats, LoaderRunner, ResponseCache and Disabled",
              "state": "done",
              "rows": [
                "port",
                "cache-key",
                "policy-lookup",
                "disabled",
                "failure-value",
                "component-tests",
                "deterministic-tests",
                "release"
              ]
            },
            {
              "id": "S2",
              "name": "jvmMain: CacheEntry and CaffeineResponseCache with the Level 1, 2 and 4a tests",
              "state": "done",
              "rows": [
                "caffeine-default",
                "shared-load",
                "failure-value",
                "max-age",
                "close",
                "component-tests",
                "deterministic-tests",
                "release"
              ]
            },
            {
              "id": "S3",
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
          "since": "2026-11-03T04:23:52Z",
          "ended": "2026-11-03T04:48:46Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-03T04:20:35Z",
            "note": "S3 reported done; qa next"
          },
          "open": [
            {
              "text": "Procedure P4's mutation (the closed-flag re-check in startLoad removed) is not detected by CaffeineResponseCacheCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
              "raised": "implementation-stage S3",
              "ref": "final-implementation.md"
            }
          ],
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-03",
              "at": "2026-11-03T04:48:46Z",
              "findings": {
                "test": 7,
                "code": 0
              },
              "severity": {
                "minor": 5,
                "other": 2
              },
              "route": "remediation",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/66-response-cache/2f43cfc3/tests/index.html"
            }
          ]
        },
        {
          "since": "2026-11-03T04:58:24Z",
          "ended": "2026-11-03T05:12:41Z",
          "endedBy": "halted",
          "to": "qa",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-03T04:58:24Z",
            "note": "pass 2: running"
          },
          "detail": "QA pass 2 running.",
          "open": [
            {
              "text": "Procedure P4's mutation (the closed-flag re-check in startLoad removed) is not detected by CaffeineResponseCacheCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
              "raised": "implementation-stage S3",
              "ref": "final-implementation.md"
            }
          ],
          "qa": [
            {
              "pass": 2,
              "verdict": "running",
              "date": "2026-11-03",
              "at": "2026-11-03T04:58:24Z",
              "findings": {}
            }
          ],
          "held": true
        },
        {
          "since": "2026-11-03T17:59:54Z",
          "ended": "2026-11-03T18:24:12Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-03T17:59:54Z",
            "note": "pass 2: running"
          },
          "detail": "QA pass 2 running.",
          "open": [
            {
              "text": "Procedure P4's mutation (the closed-flag re-check in startLoad removed) is not detected by CaffeineResponseCacheCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
              "raised": "implementation-stage S3",
              "ref": "final-implementation.md"
            }
          ],
          "qa": [
            {
              "pass": 2,
              "verdict": "changes-requested",
              "date": "2026-11-03",
              "at": "2026-11-03T18:24:12Z",
              "findings": {
                "test": 0,
                "code": 1
              },
              "severity": {
                "minor": 1,
                "other": 0
              },
              "route": "on-the-spot",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/66-response-cache/cefdb082/tests/index.html"
            }
          ]
        }
      ],
      "remediation": [
        {
          "since": "2026-11-03T04:48:46Z",
          "ended": "2026-11-03T04:58:24Z",
          "endedBy": "implementation-stage",
          "to": "qa",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-03T04:48:46Z",
            "note": "pass 1: changes-requested"
          },
          "detail": "Code review PASS: the implementation is the plan's design as written, the ABI diff is additions only, all six acceptance criteria met. Test findings: two uncovered defensive branches in CaffeineResponseCache (a foreign cancellation of the shared future read as closed; an Error thrown by the load dispatcher mapped to fatal), each wanting one Level 2 method; six runBlocking methods in LoaderRunnerComponentTest to move under runTest; one test name to reword; two Level 4a table rows for CacheExpiry and CacheEntry.isFailure; the P5 procedure and an acceptance mapping missing from the as-built record. Suite 883 passed, 0 failed, 0 skipped.",
          "open": [
            {
              "text": "Procedure P4's mutation (the closed-flag re-check in startLoad removed) is not detected by CaffeineResponseCacheCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
              "raised": "implementation-stage S3",
              "ref": "final-implementation.md"
            }
          ],
          "remediation": {
            "pass": 1,
            "route": "remediation",
            "steps": [
              {
                "name": "Add the two Level 2 methods, move LoaderRunnerComponentTest under runTest, rename the logging test, add the two 4a rows, record P5 and the acceptance mapping",
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
              "date": "2026-11-03",
              "at": "2026-11-03T04:48:46Z",
              "findings": {
                "test": 7,
                "code": 0
              },
              "severity": {
                "minor": 5,
                "other": 2
              },
              "route": "remediation",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/66-response-cache/2f43cfc3/tests/index.html",
              "note": "Code review passed: the implementation is the plan's design as written, the ABI diff is additions only, and all six acceptance criteria are met. Seven test findings, in the As built rows the fixes touch: two uncovered defensive branches in CaffeineResponseCache, each wanting one Level 2 method (`close`: a foreign cancellation of the shared future read as closed; `failure-value`: an Error thrown by the load dispatcher mapped to fatal); six runBlocking methods in LoaderRunnerComponentTest to move under runTest and one test name to reword (`component-tests`); two Level 4a table rows for CacheExpiry and CacheEntry.isFailure (`deterministic-tests`); and the P5 procedure and an acceptance mapping missing from the as-built record (`release`, whose verdict names P5). Suite: 883 passed, 0 failed, 0 skipped."
            }
          ]
        },
        {
          "since": "2026-11-03T18:24:12Z",
          "ended": "2026-11-03T18:33:16Z",
          "endedBy": "document",
          "to": "document",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-03T18:33:16Z",
            "note": "S3"
          },
          "detail": "Two Level 2 tests pin the rule in CaffeineResponseCacheLoggingComponentTest (close mid-flight: one closed line, none when the loader finishes; invalidate mid-flight: one found line noted removed, the next call reloads). componentTest 519 tests, 0 failed, 0 skipped; apiCheck passes, the ABI dump unchanged.",
          "open": [
            {
              "text": "Procedure P4's mutation (the closed-flag re-check in startLoad removed) is not detected by CaffeineResponseCacheCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
              "raised": "implementation-stage S3",
              "ref": "final-implementation.md"
            }
          ],
          "remediation": {
            "pass": 2,
            "route": "on-the-spot",
            "steps": [
              {
                "name": "Log the load's end only when its completion wins; record the change",
                "state": "done"
              }
            ]
          },
          "qa": [
            {
              "pass": 2,
              "verdict": "changes-requested",
              "date": "2026-11-03",
              "at": "2026-11-03T18:24:12Z",
              "findings": {
                "test": 0,
                "code": 1
              },
              "severity": {
                "minor": 1,
                "other": 0
              },
              "route": "on-the-spot",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/66-response-cache/cefdb082/tests/index.html",
              "note": "Test audit: compliant at every level, and all seven remediations from pass 1 hold. Code review: one minor finding, in the As built row the fix touches (`caffeine-default`, whose verdict logs `DEBUG` once per load end): the load body logs its \"load ended\" line before knowing whether its completion won, so a result a mid-flight `close()` or `invalidate(key)` discards still reads `stored`, and after `close()` a second line follows, against the once-per-load-end rule (plan.md R12, §6.9). The fix logs the line only when the completion wins. Suite: 887 passed, 0 failed, 0 skipped."
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
        "date": "2026-11-03",
        "at": "2026-11-03T04:48:46Z",
        "findings": {
          "test": 7,
          "code": 0
        },
        "severity": {
          "minor": 5,
          "other": 2
        },
        "route": "remediation",
        "report": "build/reports/tests/index.html",
        "reportCopy": "55-http-client-stack/66-response-cache/2f43cfc3/tests/index.html",
        "note": "Code review passed: the implementation is the plan's design as written, the ABI diff is additions only, and all six acceptance criteria are met. Seven test findings, in the As built rows the fixes touch: two uncovered defensive branches in CaffeineResponseCache, each wanting one Level 2 method (`close`: a foreign cancellation of the shared future read as closed; `failure-value`: an Error thrown by the load dispatcher mapped to fatal); six runBlocking methods in LoaderRunnerComponentTest to move under runTest and one test name to reword (`component-tests`); two Level 4a table rows for CacheExpiry and CacheEntry.isFailure (`deterministic-tests`); and the P5 procedure and an acceptance mapping missing from the as-built record (`release`, whose verdict names P5). Suite: 883 passed, 0 failed, 0 skipped."
      },
      {
        "pass": 2,
        "verdict": "changes-requested",
        "date": "2026-11-03",
        "at": "2026-11-03T18:24:12Z",
        "findings": {
          "test": 0,
          "code": 1
        },
        "severity": {
          "minor": 1,
          "other": 0
        },
        "route": "on-the-spot",
        "report": "build/reports/tests/index.html",
        "reportCopy": "55-http-client-stack/66-response-cache/cefdb082/tests/index.html",
        "note": "Test audit: compliant at every level, and all seven remediations from pass 1 hold. Code review: one minor finding, in the As built row the fix touches (`caffeine-default`, whose verdict logs `DEBUG` once per load end): the load body logs its \"load ended\" line before knowing whether its completion won, so a result a mid-flight `close()` or `invalidate(key)` discards still reads `stored`, and after `close()` a second line follows, against the once-per-load-end rule (plan.md R12, §6.9). The fix logs the line only when the completion wins. Suite: 887 passed, 0 failed, 0 skipped."
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
        "since": "2026-11-03T03:23:54Z",
        "ended": "2026-11-03T03:27:44Z",
        "by": "halted"
      },
      {
        "step": "qa",
        "since": "2026-11-03T05:12:41Z",
        "ended": "2026-11-03T17:59:54Z",
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
        }
      ]
    }
  },
  "milestones": [
    {
      "column": "gap",
      "label": "Research verified, in #55's run",
      "date": "2026-10-21",
      "ref": "../research/README.md",
      "note": "#55's questions and findings; question 6 and `response-cache.md` bear on this unit, whose 30 claims were re-opened at source and all hold. Written on this board at sized from the research README, `response-cache.md`, architecture §6.4 and this unit's row in §11, with what units 3 and 4 as built leave it"
    },
    {
      "column": "gap",
      "label": "Research verified, in this unit's run",
      "date": "2026-11-03",
      "ref": "research/README.md",
      "note": "two questions and two findings files; claims 1 to 24 and 1 to 25 of the findings and 21 of #55's response cache findings re-opened at source: all hold, none unclear. Caffeine 3.3.1 is still the latest release. The Gap column, written at sized from #55's research and architecture, stands with one correction (`failure-value`); this unit's findings add their evidence to 9 rows, and correct #55's research on ending a load, on the statistics and on weights"
    },
    {
      "column": "considered",
      "label": "Architecture signed off, in #55's run",
      "date": "2026-10-21",
      "ref": "../architecture.md",
      "note": "the options #55's architecture weighed for this unit: §6.4, §9, §10 and §12, with the research's considerations behind them; this unit's own plan adds to them at plan acceptance"
    },
    {
      "column": "considered",
      "label": "The unit's own plan adds to the options",
      "date": "2026-11-03",
      "ref": "plan.md",
      "note": "the options plan.md and this unit's research weigh beside the architecture's: own counters and a size-only listener in place of `recordStats()` (the architecture's default is adopted in part), a `close()` that sweeps the map itself (its `invokeOnCompletion` shape adopted in part), a weight of at least 1, a value over the bound never stored or clamped, `async`, `ATOMIC` and `UNDISPATCHED` for the load, an `Error` as an exceptional completion, a new failure leaf, and `Duration.ZERO` as a reload. The plan also adds a Level 3 suite #55's decomposition does not name, so the board gains a row for it, `integration-tests`"
    },
    {
      "column": "verdict",
      "label": "Plan accepted",
      "date": "2026-11-03",
      "ref": "plan.md",
      "note": "three implementation stages on `feat/issue-66-response-cache`, S1 to S3, then the documents, in five commits; the design makes #55's architecture §6.4 concrete in plan.md §6.11: four outcomes carried by value, own counters, a sweep at `close()`, a weight of at least 1, a value over the bound delivered and never stored, `Duration.ZERO` served by age. No version moves"
    }
  ],
  "trail": [
    {
      "date": "2026-10-22",
      "column": "gap",
      "label": "Issue #66 opened as unit 7 of #55, blocked by #62 and #63",
      "ref": "#66"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "#63 landed through PR #84 as 4fcc6e0: the last blocker is closed",
      "ref": "4fcc6e0"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Run started: sized as the response cache subsystem of `webtools-http`; the plan starts as a re-pass of unit 7 against #55's architecture §6.4 and §11, with units 3 and 4 as built",
      "ref": "../architecture.md"
    },
    {
      "date": "2026-11-03",
      "column": "considered",
      "label": "Gap and Considered filled from #55's research and architecture: 14 rows, the options with the verdicts the architecture gave",
      "ref": "../architecture.md"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Research planning: 0 questions drafted"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Research started: `caffeine-mechanics`, `load-lifecycle`"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Research returned: `load-lifecycle`",
      "ref": "research/load-lifecycle.md"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Research returned: `caffeine-mechanics`",
      "ref": "research/caffeine-mechanics.md"
    },
    {
      "date": "2026-11-03",
      "column": "gap",
      "label": "Verification started: 2 findings"
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
      "label": "Halted at the plan: waiting on you (Confirmation of four implementation-level choices the drafted plan settles: the CacheStats fields, invalidate during an in-flight load, maxAge = ZERO semantics, and the minimum Found weight.)"
    },
    {
      "date": "2026-11-03",
      "column": "verdict",
      "label": "Resumed at the plan"
    },
    {
      "date": "2026-11-03",
      "column": "verdict",
      "label": "Plan accepted",
      "ref": "plan.md"
    },
    {
      "date": "2026-11-03",
      "column": "verdict",
      "label": "Considered and Verdict written from the plan: 15 rows, 19 options added and 3 adopted in part; the Consequences from `consequences.md`",
      "ref": "plan.md"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Branch `feat/issue-66-response-cache` cut; implementation starts"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "S2 done; S3 current: Level 3 and Level 4c suites"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "As built written for the 11 rows S1 and S2 finish, from the as-built record and the source: 10 as planned, 1 amended (`component-tests`, two test deviations); the test classes mapped to their rows",
      "ref": "final-implementation.md"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Implementation done (3 stages); qa next"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "As built written for the 2 rows S3 finishes, from the as-built record and the source: 2 as planned, no new deviation; the four Level 4c classes mapped to their rows; Procedure P4's result stays open",
      "ref": "final-implementation.md"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "QA pass 1: changes requested (7 test, 5 minor, 2 other)"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "QA pass 1's findings mapped to the As built rows the fixes touch: `close`, `failure-value`, `component-tests`, `deterministic-tests`, `release`; each class's result on the Tests tab from the pass's report: 35 classes, 150 tests, all passed"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Remediation for qa pass 1: S3"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "QA pass 2: running"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Halted at qa: waiting on you (The session was stopped during qa pass 2; the pass is re-run from the start when the session resumes. The working tree is unchanged.)"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Resumed at qa"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "QA pass 2: changes requested (1 code, 1 minor)"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Remediation for qa pass 2: S3"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Document: 0 of 6 tasks done"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Document: Reconcile documentation current"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Document: Reconcile documentation, Reconcile plan in parallel"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Document: Reconcile plan current"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Document: README and CHANGELOG current"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Open point resolved: Procedure P4's mutation (the closed-flag re-check in startLoad removed) is not detected by CaffeineResponseCacheCloseRaceNonFunctionalTest; to be settled as a known gap or a test change.",
      "ref": "final-implementation.md"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Document: State boards current"
    },
    {
      "date": "2026-11-03",
      "column": "built",
      "label": "Document: every task done"
    }
  ],
  "rows": [
    {
      "id": "port",
      "group": "Code",
      "name": "The `ResponseCache` port: `getOrLoad`, `invalidate`, `invalidateAll`, `stats` and `close`",
      "system": "webtools-http-cache",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "`webtools-http` has a gated port for the transport and none for a cache, so nothing can serve one decoded value per key to many concurrent callers from one load, and the facades of unit 9 have no seam to layer a cache on.",
        "evidence": [
          "common/HttpTransport.kt:29-30 — the subclass-gated transport port, the only port on the call path at the baseline",
          "webtools-http/api/webtools-http.api — the dump at the baseline, no cache type",
          "../research/response-cache.md Bottom line — the port's shape, compiled for `jvm()` and `js(IR)`",
          "research/caffeine-mechanics.md F5 — Caffeine's Javadoc calls `invalidateAll` undefined for an entry being loaded; its source removes the entry, leaves the future alone and never re-inserts it, the next caller starts a second load, and `synchronous().asMap()` joins a pending future and can block",
          "research/README.md § Codebase blast radius — nothing in the repository caches HTTP responses or calls a cache; the call facades (#68) are the first users"
        ],
        "source": "../research/README.md § Response cache; ../architecture.md §6.4",
        "link": "../research/README.md#response-cache"
      },
      "options": [
        {
          "id": "generic-port",
          "name": "One generic, subclass-gated `ResponseCache : AutoCloseable` with a single suspend `getOrLoad(key, policy, maxAge, loader): Result<Lookup<V>>`, no default on `maxAge` and an overload without it",
          "verdict": "accepted",
          "why": "One cache per client, with no Caffeine type in a public signature. Implementing it is Experimental, like the transport's port; calling it and passing it are not.",
          "source": "../architecture.md §6.4, §9",
          "link": "../architecture.md#64-response-cache"
        },
        {
          "id": "typed-per-endpoint",
          "name": "A typed cache per endpoint",
          "verdict": "declined",
          "why": "It multiplies the size bound and the maintenance state, and cannot give one bound per client.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "defaulted-max-age",
          "name": "A default value on the `maxAge` parameter of `getOrLoad`",
          "verdict": "declined",
          "why": "A Stable Core function takes no defaulted parameter, and an overload with a body costs an implementer one method less.",
          "source": "plan.md §6.11",
          "link": "plan.md#611-where-this-plan-refines-the-parent-architecture"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One system, the response cache of `webtools-http`: a common port a consumer or a fake substitutes, an interface being a narrower contract than an overridable class.",
        "statements": [
          {
            "text": "`ResponseCache : AutoCloseable` is a common interface gated by `@SubclassOptInRequired`: implementing it is Experimental, while calling it, passing it and naming it in a signature are Stable Core and need no opt-in.",
            "ref": "plan.md#r1-the-public-surface-and-its-tier",
            "excerpt": {
              "ref": "plan.md#r1-the-public-surface-and-its-tier",
              "heading": "R1. The public surface and its tier",
              "paragraph": "`webtools-http` gains these public declarations in `com.spartanlabs.webtools.http`, each with KDoc (`@param`, `@return`, `@throws`) that names its tier. Implementing `ResponseCache` is Experimental (behind `ExperimentalForInheritanceWebToolsApi`); everything else is Stable Core and usable without the opt-in: calling the port, passing it, naming it in a signature, `ResponseCache.Disabled`, `CaffeineResponseCache`, and every value type.",
              "hash": "be71d16a783b"
            }
          },
          {
            "text": "It has `getOrLoad` with and without `maxAge`, `invalidate`, `invalidateAll`, `stats` and a `close` with an empty body, and no defaulted parameter: the overload without `maxAge` has a body that passes `Duration.INFINITE`.",
            "ref": "plan.md#r2-the-port-and-its-contract",
            "excerpt": {
              "ref": "plan.md#r2-the-port-and-its-contract",
              "heading": "R2. The port and its contract",
              "paragraph": "No defaulted parameters. The contract, numbered in the KDoc as `HttpTransport`'s is:",
              "hash": "db10c584982f"
            }
          },
          {
            "text": "Its KDoc numbers an eleven-point contract, from one load per key at a time to the validity of `maxAge`, as `HttpTransport`'s does.",
            "ref": "plan.md#r2-the-port-and-its-contract",
            "excerpt": {
              "ref": "plan.md#r2-the-port-and-its-contract",
              "heading": "R2. The port and its contract",
              "paragraph": "No defaulted parameters. The contract, numbered in the KDoc as `HttpTransport`'s is:",
              "hash": "db10c584982f"
            }
          },
          {
            "text": "After `invalidate(key)` the next call loads anew; a load in flight still completes for the calls already waiting and its result is not stored, and `invalidateAll()` is the same for every key.",
            "ref": "plan.md#r2-the-port-and-its-contract",
            "excerpt": {
              "ref": "plan.md#r2-the-port-and-its-contract",
              "heading": "R2. The port and its contract",
              "paragraph": "No defaulted parameters. The contract, numbered in the KDoc as `HttpTransport`'s is:",
              "hash": "db10c584982f"
            }
          },
          {
            "text": "No Caffeine, `CompletableFuture` or coroutine-internal type appears in a public signature.",
            "ref": "plan.md#r1-the-public-surface-and-its-tier",
            "excerpt": {
              "ref": "plan.md#r1-the-public-surface-and-its-tier",
              "heading": "R1. The public surface and its tier",
              "paragraph": "`webtools-http` gains these public declarations in `com.spartanlabs.webtools.http`, each with KDoc (`@param`, `@return`, `@throws`) that names its tier. Implementing `ResponseCache` is Experimental (behind `ExperimentalForInheritanceWebToolsApi`); everything else is Stable Core and usable without the opt-in: calling the port, passing it, naming it in a signature, `ResponseCache.Disabled`, `CaffeineResponseCache`, and every value type.",
              "hash": "be71d16a783b"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write the port with its contract in common code and extend the opt-in marker's list of gated types.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/ResponseCache.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/ExperimentalForInheritanceWebToolsApi.kt"
            ],
            "check": "Procedures P1 and P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 1 (gating) `ResponseCacheConsumerGatingTest`, compiled with no opt-in; Procedure P1 shows once, by hand, that an implementer needs it"
        ],
        "risks": [
          "Stable Core from the first release: the names and the four-argument `getOrLoad`'s parameter order are hard to change after 1.0.0 (§10)",
          "Dropping `@SubclassOptInRequired` passes `apiCheck`, so the gate stays a review item until a compile-fail check exists (§9)"
        ],
        "source": "plan.md R1, R2, §6.1, §6.11",
        "link": "plan.md#r2-the-port-and-its-contract"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`ResponseCache : AutoCloseable` was built in common code under `@SubclassOptInRequired`: Procedure P1 showed an implementer without the opt-in failing to compile, once per source set, and compiling with it, and `ResponseCacheConsumerGatingTest` compiles with no opt-in.",
            "state": "as-planned",
            "ref": "final-implementation.md#4-measurements",
            "excerpt": {
              "ref": "final-implementation.md#4-measurements",
              "heading": "4. Measurements",
              "paragraph": "Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other module's suite.",
              "hash": "6abc2ca5c490"
            }
          },
          {
            "text": "It has `getOrLoad` with and without `maxAge`, `invalidate`, `invalidateAll`, `stats` and a `close` with an empty body, and no defaulted parameter: the overload without `maxAge` has a body that passes `Duration.INFINITE`.",
            "state": "as-planned"
          },
          {
            "text": "Its KDoc numbers the eleven points, from one load per key at a time to a negative `maxAge` being a programmer error.",
            "state": "as-planned"
          },
          {
            "text": "Point 7 of the contract says that after `invalidate(key)` the next call loads anew, that a load in flight still completes for the calls already waiting and is not stored, and that `invalidateAll()` is the same for every key.",
            "state": "as-planned"
          },
          {
            "text": "The ABI dump names no Caffeine type and no `CompletableFuture`, and no coroutine-internal type: `CoroutineDispatcher`, the builder's `loadDispatcher`, is the only kotlinx type in it.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "cache-key",
      "group": "Code",
      "name": "`CacheKey<V>`: the endpoint and its canonical parameters",
      "system": "webtools-http-cache",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing names a cached value. A `Url` will not do: its equality is on bytes, so encoding differences would split one value, and a query-string credential would become part of the key.",
        "evidence": [
          "../consequences.md §8 — a `Url` is not the cache key: its equality is on bytes, and the key is the facade's canonical parameters",
          "../research/response-cache.md F12 — Ktor keys by the full `Url`; Apollo appends values with no separator, so `(\"T\", \"a\", \"b\")` and `(\"T\", \"ab\")` collide"
        ],
        "source": "../research/README.md § Response cache; ../architecture.md §6.4",
        "link": "../research/README.md#response-cache"
      },
      "options": [
        {
          "id": "structured-key",
          "name": "A phantom-typed `CacheKey<V>(endpointId, params)`: equality over the id and the parameters sorted by name with a stable sort, `toString` printing parameter names only, never a tenant, a header, a credential or the decoder",
          "verdict": "accepted",
          "why": "No URL text is involved, so encoding differences go. It never merges two different parameter lists, and at worst costs one extra call; a log line never carries a value.",
          "source": "../architecture.md §6.4",
          "link": "../architecture.md#64-response-cache"
        },
        {
          "id": "url-key",
          "name": "The request's `Url` as the key (Ktor's `HttpCache`)",
          "verdict": "declined",
          "why": "A query-string credential becomes part of the key, and the equality is on bytes.",
          "source": "../research/response-cache.md F12; ../consequences.md §8",
          "link": "../research/README.md#response-cache"
        },
        {
          "id": "joined-key",
          "name": "A key built by appending its values into one string (Apollo Kotlin)",
          "verdict": "declined",
          "why": "Two different parameter lists can make the same key, which gives a wrong answer rather than an extra call.",
          "source": "../research/response-cache.md F12",
          "link": "../research/README.md#response-cache"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "The key is plain common vocabulary with no platform content and no URL text, so its equality is a pure rule.",
        "statements": [
          {
            "text": "`CacheKey<V : Any>` has public constructors with and without `params`, neither defaulted, and refuses a blank `endpointId`.",
            "ref": "plan.md#r4-cachekeyv--any",
            "excerpt": {
              "ref": "plan.md#r4-cachekeyv--any",
              "heading": "R4. `CacheKey<V : Any>`",
              "paragraph": "Public constructors `CacheKey(endpointId: String, params: List<Pair<String, String>>)` and `CacheKey(endpointId: String)` (no defaults).",
              "hash": "841b59539c50"
            }
          },
          {
            "text": "`params` is the caller's list sorted by name with a stable sort and held as a read-only copy, so repeated names keep their order; names, values and the id are case-sensitive, with no Unicode normalisation.",
            "ref": "plan.md#r4-cachekeyv--any",
            "excerpt": {
              "ref": "plan.md#r4-cachekeyv--any",
              "heading": "R4. `CacheKey<V : Any>`",
              "paragraph": "Public constructors `CacheKey(endpointId: String, params: List<Pair<String, String>>)` and `CacheKey(endpointId: String)` (no defaults).",
              "hash": "841b59539c50"
            }
          },
          {
            "text": "Equality and the hash cover `endpointId` and `params`; `V` is outside them, so one endpoint id serves one value type.",
            "ref": "plan.md#r4-cachekeyv--any",
            "excerpt": {
              "ref": "plan.md#r4-cachekeyv--any",
              "heading": "R4. `CacheKey<V : Any>`",
              "paragraph": "Public constructors `CacheKey(endpointId: String, params: List<Pair<String, String>>)` and `CacheKey(endpointId: String)` (no defaults).",
              "hash": "841b59539c50"
            }
          },
          {
            "text": "`toString()` prints parameter names only and never a value, and the KDoc says never to put a tenant, a header, a credential or the decoder in a key.",
            "ref": "plan.md#r4-cachekeyv--any",
            "excerpt": {
              "ref": "plan.md#r4-cachekeyv--any",
              "heading": "R4. `CacheKey<V : Any>`",
              "paragraph": "Public constructors `CacheKey(endpointId: String, params: List<Pair<String, String>>)` and `CacheKey(endpointId: String)` (no defaults).",
              "hash": "841b59539c50"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write `CacheKey` in common code.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/CacheKey.kt"
            ],
            "check": "Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `CacheKeyComponentTest`; Level 4a (deterministic) `CacheKeyCanonicalFormDeterministicTest`"
        ],
        "risks": [
          "A second value type under one endpoint id is a programming error that surfaces as a `ClassCastException` at the consumer's use (§6.8)"
        ],
        "source": "plan.md R4",
        "link": "plan.md#r4-cachekeyv--any"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`CacheKey<V : Any>` has public constructors with and without `params`, neither defaulted, and refuses a blank `endpointId` with `IllegalArgumentException`.",
            "state": "as-planned"
          },
          {
            "text": "`params` is the caller's list copied and sorted by name with a stable sort, so repeated names keep their order; names, values and the id are case-sensitive, with no Unicode normalisation.",
            "state": "as-planned"
          },
          {
            "text": "Equality and the hash cover `endpointId` and `params`; `V` is a phantom outside them, so one endpoint id serves one value type.",
            "state": "as-planned"
          },
          {
            "text": "`toString()` prints the id and the parameter names and never a value, and the KDoc says never to put a tenant, a header, a credential or the decoder in a key.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "policy-lookup",
      "group": "Code",
      "name": "`CachePolicy`, `Lookup` and `CacheStats`",
      "system": "webtools-http-cache",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing models how long a value lives or a confirmed miss, and a caller could not tell a hit from a joined load: Caffeine counts callers that join an in-flight load as hits.",
        "evidence": [
          "../research/response-cache.md F11 — a miss as a value, with `missTtl` measured at 30 s and 60 s",
          "../research/response-cache.md F9 — 50 coalesced callers and one later call read 50 hits and 1 miss, so the cache keeps its own counter of loads",
          "../research/response-cache.md F2 — a failure completed as a value leaves Caffeine's `loadFailureCount` at 0, so the cache keeps its own failed-load counter",
          "research/caffeine-mechanics.md F1 — `evictionCount` counts every automatic removal, expiry included, so each failed or zero-lifetime entry counts as an eviction and the count changes from run to run; #55's research had expected the size evictions from it",
          "research/caffeine-mechanics.md F3 — Caffeine's hit and miss counts see every `get`, so a call that re-reads a stale entry counts twice",
          "research/caffeine-mechanics.md F4 — a weight of 0 is never selected for size eviction and a negative one throws inside Caffeine's completion callback, so a reported weight can defeat the bound; #55's research had allowed 0"
        ],
        "source": "../research/README.md § Response cache; ../architecture.md §6.4",
        "link": "../research/README.md#response-cache"
      },
      "options": [
        {
          "id": "value-model",
          "name": "`CachePolicy(ttl, missTtl = 60 s)` by factory and builder, a sealed `Lookup` of `Found(value, weight)` and `Missing`, and `CacheStats`",
          "verdict": "accepted",
          "why": "A confirmed miss is a value, never null and never a failure, which keeps \"failures are never stored\" absolute. The builder lets `staleFor` be added later without changing the method.",
          "source": "../architecture.md §6.4",
          "link": "../architecture.md#64-response-cache"
        },
        {
          "id": "cached-failure",
          "name": "A confirmed miss as a null, or as a cached failure",
          "verdict": "declined",
          "why": "A cached failure needs a predicate over `Throwable`s inside the cache, and the stack's results are never nullable.",
          "source": "../research/response-cache.md F11",
          "link": "../research/README.md#response-cache"
        },
        {
          "id": "entry-count",
          "name": "An entry count only, with no weight on `Found`",
          "verdict": "declined",
          "why": "Simpler, but one large document could not count for more, and adding `weight` later changes a public constructor. A default weight of 1 behaves as an entry count anyway.",
          "source": "../research/response-cache.md Considerations 2",
          "link": "../research/README.md#response-cache"
        },
        {
          "id": "weight-zero",
          "name": "A `Found` weight of 0, as #55's research's sketch allowed",
          "verdict": "declined",
          "why": "Caffeine skips an entry of weight 0 when it evicts for size, so that entry would sit outside the bound.",
          "source": "plan.md R6, §6.11; research/README.md § Weights and the bound",
          "link": "plan.md#r6-lookupout-v--any"
        },
        {
          "id": "joined-gauges",
          "name": "A separate count of joined calls, and size and weight gauges, in `CacheStats`",
          "verdict": "declined",
          "why": "The four counts are this unit's scope, and each of these can be added later through a new constructor of the class.",
          "source": "plan.md §4, §13",
          "link": "plan.md#4-non-goals"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "These are the values the port speaks in: final common classes with explicit members, which keep the ABI independent of generated ones.",
        "statements": [
          {
            "text": "`CachePolicy` is built by the factory `CachePolicy(ttl) { }` over an `internal` constructor: `missTtl` defaults to 60 seconds, neither may be negative, `Duration.INFINITE` is allowed, and the builder is where a later `staleFor` goes.",
            "ref": "plan.md#r5-cachepolicy",
            "excerpt": {
              "ref": "plan.md#r5-cachepolicy",
              "heading": "R5. `CachePolicy`",
              "paragraph": "`internal constructor(ttl: Duration, missTtl: Duration)` with public `val ttl` and `val missTtl`; the factory `public fun CachePolicy(ttl: Duration, configure: CachePolicy.Builder.() -> Unit = {}): CachePolicy` (the trailing builder lambda with a `{}` default is this module's established factory shape: `HttpHeaders`, `HttpRequest`, `OutboundUrlPolicy.trusted`); `@WebToolsDsl public class Builder internal constructor()` with `var missTtl: Duration` defaulting to `CachePolicy.DEFAULT_MISS_TTL` (60 seconds, a public companion value). `ttl` and `missTtl` must not be negative …",
              "hash": "9b198a9dd761"
            }
          },
          {
            "text": "`Lookup` is a sealed interface of `Found(value, weight)` and `Missing`, with no `data` class; `Found(value)` weighs 1, and `toString()` never prints the value.",
            "ref": "plan.md#r6-lookupout-v--any",
            "excerpt": {
              "ref": "plan.md#r6-lookupout-v--any",
              "heading": "R6. `Lookup<out V : Any>`",
              "paragraph": "A `sealed interface` (the closed pair is the contract; `when` over it needs no `else`).",
              "hash": "cdf267050c2a"
            }
          },
          {
            "text": "A `Found`'s weight is at least 1, because Caffeine never size-evicts an entry of weight 0.",
            "ref": "plan.md#r6-lookupout-v--any",
            "excerpt": {
              "ref": "plan.md#r6-lookupout-v--any",
              "heading": "R6. `Lookup<out V : Any>`",
              "paragraph": "A `sealed interface` (the closed pair is the contract; `when` over it needs no `else`).",
              "hash": "cdf267050c2a"
            }
          },
          {
            "text": "`CacheStats` has a public constructor and four non-negative counts: `hits` counts a stored entry or a joined load, `misses` a call that started a load, `evictions` the size bound's removals only, and `failedLoads` each failed load once.",
            "ref": "plan.md#r7-cachestats",
            "excerpt": {
              "ref": "plan.md#r7-cachestats",
              "heading": "R7. `CacheStats`",
              "paragraph": "Public constructor `CacheStats(hits: Long, misses: Long, evictions: Long, failedLoads: Long)` (an implementer of the port must be able to build one), each non-negative (`IllegalArgumentException`, message `<property> must not be negative`); explicit `equals`, `hashCode`, `toString`; `CacheStats.Empty` (all zero) on its companion. The KDoc defines each count:",
              "hash": "f074d320c887"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write the three value types in common code and extend the DSL marker's list with `CachePolicy.Builder`.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/Lookup.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/CachePolicy.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/CacheStats.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WebToolsDsl.kt"
            ],
            "check": "Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `CachePolicyComponentTest`, `LookupComponentTest` and `CacheStatsComponentTest`, and `CachePolicy.Builder` joins `WebToolsDslComponentTest`"
        ],
        "risks": [
          "The `CacheStats` constructor and `Found`'s weight rule are hard to change after 1.0.0 (§10)",
          "`maxWeight` bounds reported weights, not bytes, so a loader that under-reports weight defeats the bound (§10)"
        ],
        "source": "plan.md R5, R6, R7, §6.1",
        "link": "plan.md#r6-lookupout-v--any"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`CachePolicy` is built by the factory `CachePolicy(ttl) { }` over an `internal` constructor: `missTtl` defaults to 60 seconds, neither may be negative, `Duration.INFINITE` is allowed, and the `@WebToolsDsl` builder's KDoc says that further optional properties, when they exist, are added there.",
            "state": "as-planned"
          },
          {
            "text": "`Lookup` is a sealed interface of `Found(value, weight)` and the object `Missing`, with no `data` class; `Found(value)` weighs 1, and `toString()` prints the weight and never the value.",
            "state": "as-planned"
          },
          {
            "text": "`Found` refuses a weight below 1 with `IllegalArgumentException`, because Caffeine never size-evicts an entry of weight 0.",
            "state": "as-planned"
          },
          {
            "text": "`CacheStats` has a public constructor and four non-negative counts: `hits` a stored entry or a joined load, `misses` a call that started a load, `evictions` the size bound's removals only, and `failedLoads` each failed load once; `CacheStats.Empty` is all zeros.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "disabled",
      "group": "Code",
      "name": "`ResponseCache.Disabled`, the pass-through",
      "system": "webtools-http-cache",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Common code would have no implementation to construct and a consumer no explicit way to opt out; the Caffeine default is JVM only and nothing consumes a non-JVM target today.",
        "evidence": [
          "../research/response-cache.md F13 — the port and a pass-through compiled for `jvm()` and `js(IR)`; cache4k's coalescing needs `atomicfu`, an expect/actual map and a keyed `Mutex` registry"
        ],
        "source": "../research/README.md § Response cache; ../architecture.md §6.4",
        "link": "../research/README.md#response-cache"
      },
      "options": [
        {
          "id": "pass-through",
          "name": "A pass-through in common code: no storage, no sharing, the only implementation constructible there",
          "verdict": "accepted",
          "why": "Cheap, and the explicit opt-out. It forfeits N callers costing one permit, which its KDoc states.",
          "source": "../architecture.md §6.4",
          "link": "../architecture.md#64-response-cache"
        },
        {
          "id": "common-coalescing",
          "name": "A coalescing default in common code, as cache4k does",
          "verdict": "declined",
          "why": "It needs `atomicfu`, an expect/actual concurrent map and a keyed lock registry, and cache4k carries an open linearizability issue; nothing consumes a non-JVM target.",
          "source": "../research/response-cache.md F13",
          "link": "../research/README.md#response-cache"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "The only implementation common code can construct: stateless and shared, it gives a consumer an explicit way to opt out.",
        "statements": [
          {
            "text": "`ResponseCache.Disabled` is a nested public object, usable without the opt-in.",
            "ref": "plan.md#r3-responsecachedisabled",
            "excerpt": {
              "ref": "plan.md#r3-responsecachedisabled",
              "heading": "R3. `ResponseCache.Disabled`",
              "paragraph": "A nested `public object`, as `WallClock.System` is. `getOrLoad` checks `maxAge` (R2 point 11), calls `currentCoroutineContext().ensureActive()`, then runs the loader on the caller's coroutine through the shared internal loader runner: no storage and no sharing, so every call runs its own loader and the loader sees the caller's context. `invalidate` and `invalidateAll` do nothing; `stats()` is always `CacheStats.Empty`; `close()` does nothing (the default); `toString()` is `ResponseCache.Disabled`. Its KDoc says it forfeits \"N concurrent callers cost one load\". It is the only implementation …",
              "hash": "69ba4b7b8b16"
            }
          },
          {
            "text": "Each call checks `maxAge` and the caller's cancellation, then runs its own loader on the caller's coroutine, with no storage and no sharing, and its KDoc says it forfeits N concurrent callers costing one load.",
            "ref": "plan.md#r3-responsecachedisabled",
            "excerpt": {
              "ref": "plan.md#r3-responsecachedisabled",
              "heading": "R3. `ResponseCache.Disabled`",
              "paragraph": "A nested `public object`, as `WallClock.System` is. `getOrLoad` checks `maxAge` (R2 point 11), calls `currentCoroutineContext().ensureActive()`, then runs the loader on the caller's coroutine through the shared internal loader runner: no storage and no sharing, so every call runs its own loader and the loader sees the caller's context. `invalidate` and `invalidateAll` do nothing; `stats()` is always `CacheStats.Empty`; `close()` does nothing (the default); `toString()` is `ResponseCache.Disabled`. Its KDoc says it forfeits \"N concurrent callers cost one load\". It is the only implementation …",
              "hash": "69ba4b7b8b16"
            }
          },
          {
            "text": "`stats()` is always `CacheStats.Empty`, and `invalidate`, `invalidateAll` and `close()` do nothing, so a client may close the shared object safely.",
            "ref": "plan.md#r3-responsecachedisabled",
            "excerpt": {
              "ref": "plan.md#r3-responsecachedisabled",
              "heading": "R3. `ResponseCache.Disabled`",
              "paragraph": "A nested `public object`, as `WallClock.System` is. `getOrLoad` checks `maxAge` (R2 point 11), calls `currentCoroutineContext().ensureActive()`, then runs the loader on the caller's coroutine through the shared internal loader runner: no storage and no sharing, so every call runs its own loader and the loader sees the caller's context. `invalidate` and `invalidateAll` do nothing; `stats()` is always `CacheStats.Empty`; `close()` does nothing (the default); `toString()` is `ResponseCache.Disabled`. Its KDoc says it forfeits \"N concurrent callers cost one load\". It is the only implementation …",
              "hash": "69ba4b7b8b16"
            }
          },
          {
            "text": "It runs loaders through `runLoader`, an `internal` common function it shares with the Caffeine default, and logs nothing.",
            "ref": "plan.md#62-the-shared-loader-runner-commonmain-internal",
            "excerpt": {
              "ref": "plan.md#62-the-shared-loader-runner-commonmain-internal",
              "heading": "6.2 The shared loader runner (`commonMain`, `internal`)",
              "paragraph": "`Disabled` and the Caffeine default run a loader the same way, so one function does it:",
              "hash": "4d7687c335c5"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write the shared runner, then `Disabled` nested in the port.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/LoaderRunner.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/ResponseCache.kt"
            ],
            "check": "Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `LoaderRunnerComponentTest` and `DisabledResponseCacheComponentTest`"
        ],
        "risks": [],
        "source": "plan.md R3, §6.2, §6.11",
        "link": "plan.md#r3-responsecachedisabled"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`ResponseCache.Disabled` is a nested public object, usable without the opt-in.",
            "state": "as-planned"
          },
          {
            "text": "Each call checks `maxAge`, then the caller's cancellation, then runs its own loader on the caller's coroutine, with no storage and no sharing, and its KDoc says it forfeits N concurrent callers costing one load.",
            "state": "as-planned"
          },
          {
            "text": "`stats()` is always `CacheStats.Empty`, and `invalidate`, `invalidateAll` and `close()` do nothing, so a client may close the shared object safely.",
            "state": "as-planned"
          },
          {
            "text": "It runs loaders through `runLoader`, an `internal` common function in `LoaderRunner.kt` that the Caffeine default shares, and logs nothing.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "caffeine-default",
      "group": "Code",
      "name": "`CaffeineResponseCache`: the factory, the builder and the `AsyncCache` behind them",
      "system": "webtools-http-cache",
      "where": "jvm/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Caffeine 3.3.1 is on `jvmMain` and no main source uses it. Its `AsyncCache.get` gives single-flight, and its `Expiry` and weigher see the value once the future completes, but nothing wires them to the injected time source or to a size bound.",
        "evidence": [
          "webtools-http/build.gradle.kts:55 — `caffeine:3.3.1` on `jvmMain`, used by no main source (baseline)",
          "../research/response-cache.md F1 — single-flight; the TTL runs from completion, and an in-flight future weighs nothing",
          "../research/response-cache.md F8 — a `Ticker` from one mark on the injected `TimeSource`, measured on fake time",
          "../research/response-cache.md F9 — the executor, no `Scheduler`, the bound and the stats, measured",
          "research/caffeine-mechanics.md F4 — an entry over `maximumWeight` is stored, readable until the next drain, then counted as a size eviction",
          "research/caffeine-mechanics.md F7 — the mapping function holds the bin lock while it runs, and the `BiFunction` form never uses its executor for a fresh future",
          "research/caffeine-mechanics.md F8 — on completion `expireAfterCreate` runs once with the ticker's reading; 0 expires the entry at once and `Long.MAX_VALUE` is capped at about 150 years"
        ],
        "source": "../research/README.md § Response cache; ../architecture.md §6.4",
        "link": "../research/README.md#response-cache"
      },
      "options": [
        {
          "id": "final-default",
          "name": "A final `CaffeineResponseCache`, by factory and builder over `timeSource`, `maxWeight` (1,000), `loadDispatcher` and `maintenanceExecutor`: an `AsyncCache` with a variable `Expiry`, a `Ticker` from one mark, `maximumWeight` with a weigher and `recordStats()`, no `Scheduler`, no listeners",
          "verdict": "partial",
          "why": "Adopted in part: the final class, the factory and builder over the four properties, the `AsyncCache` with a variable `Expiry`, the ticker from one mark, the weight bound and no `Scheduler` are kept. `recordStats()` and \"no listeners\" are not, because Caffeine's statistics do not count what `CacheStats` means.",
          "source": "../architecture.md §6.4, §9; plan.md §6.7, §6.11",
          "link": "../architecture.md#64-response-cache"
        },
        {
          "id": "aedile",
          "name": "Wrapping Aedile",
          "verdict": "declined",
          "why": "It pins Caffeine 3.2.4 against the required 3.3.1, exposes `AsyncCache` and runs the load in the first caller's context.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "scheduler",
          "name": "A real-time `Scheduler` to reclaim expired entries",
          "verdict": "declined",
          "why": "It would schedule against the real clock while the ticker is fake. Expired entries are never served, and the weight bound caps the memory they hold until the next write.",
          "source": "../research/response-cache.md F9",
          "link": "../research/README.md#response-cache"
        },
        {
          "id": "own-counters",
          "name": "The same default with no `recordStats()`: four `LongAdder`s the cache owns, and one `evictionListener` that counts size removals only",
          "verdict": "accepted",
          "why": "Caffeine's `evictionCount` counts every automatic removal and changes with callback order, its hit and miss counts see every `get`, its `loadFailureCount` stays 0 when failures complete normally, and `recordStats()` reads `System.nanoTime` around every load.",
          "source": "plan.md §6.7, §6.11; research/caffeine-mechanics.md F1 to F3",
          "link": "plan.md#67-statistics"
        },
        {
          "id": "removal-listener",
          "name": "A `removalListener` to count evictions",
          "verdict": "declined",
          "why": "It runs asynchronously and for every cause, where the `evictionListener` runs inside the atomic removal.",
          "source": "research/README.md § Options that do not fit",
          "link": "research/README.md#options-that-do-not-fit"
        },
        {
          "id": "store-oversized",
          "name": "Storing a value heavier than `maxWeight` until the next drain",
          "verdict": "declined",
          "why": "It would be readable until the drain, counted as a size eviction, and over the bound at that instant.",
          "source": "plan.md §6.6; research/caffeine-mechanics.md F4",
          "link": "plan.md#66-the-entry-the-lifetime-and-weight-rule-and-the-usability-rule"
        },
        {
          "id": "clamp-weight",
          "name": "Clamping an oversized weight down to `maxWeight`",
          "verdict": "declined",
          "why": "One such entry would empty the cache.",
          "source": "plan.md §6.6",
          "link": "plan.md#66-the-entry-the-lifetime-and-weight-rule-and-the-usability-rule"
        },
        {
          "id": "zero-max-weight",
          "name": "A `maxWeight` of 0",
          "verdict": "declined",
          "why": "A zero TTL already coalesces loads without storing anything.",
          "source": "plan.md R8; research/README.md § Options that do not fit",
          "link": "research/README.md#options-that-do-not-fit"
        },
        {
          "id": "sync-view",
          "name": "`synchronous().asMap()` for `invalidate` and `invalidateAll`",
          "verdict": "declined",
          "why": "That view joins a pending future and can block; the async view removes the entry and leaves its future alone.",
          "source": "plan.md §6.5; research/caffeine-mechanics.md F5",
          "link": "plan.md#65-invalidation-and-close"
        },
        {
          "id": "expiry-creating",
          "name": "`Expiry.creating` for the variable lifetime",
          "verdict": "declined",
          "why": "It is a `java.time.Duration` detour for a value the entry already holds in nanoseconds.",
          "source": "research/README.md § Options that do not fit",
          "link": "research/README.md#options-that-do-not-fit"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "The one part that needs Caffeine: a supplied default named for its technology, final, with every policy number a builder property.",
        "statements": [
          {
            "text": "`CaffeineResponseCache` is a final class with an `internal` constructor, built by `CaffeineResponseCache { }` over a `@WebToolsDsl` builder of `timeSource`, `maxWeight` (at least 1, 1,000 by default), `loadDispatcher` and `maintenanceExecutor`; it is Stable Core and needs no opt-in.",
            "ref": "plan.md#r8-caffeineresponsecache--construction",
            "excerpt": {
              "ref": "plan.md#r8-caffeineresponsecache--construction",
              "heading": "R8. `CaffeineResponseCache` — construction",
              "paragraph": "A final class `CaffeineResponseCache internal constructor(timeSource: TimeSource, maxWeight: Long, loadDispatcher: CoroutineDispatcher, maintenanceExecutor: java.util.concurrent.Executor) : ResponseCache`; the factory `public fun CaffeineResponseCache(configure: CaffeineResponseCache.Builder.() -> Unit = {}): CaffeineResponseCache`; `@WebToolsDsl public class Builder internal constructor()` with",
              "hash": "c7b4b609653d"
            }
          },
          {
            "text": "Its store is an `AsyncCache` with a ticker from one mark on the injected time source, the given executor, a weight bound and a variable `Expiry`, with no `recordStats()`, no `Scheduler` and no `removalListener`.",
            "ref": "plan.md#r9-caffeineresponsecache--storage-and-expiry",
            "excerpt": {
              "ref": "plan.md#r9-caffeineresponsecache--storage-and-expiry",
              "heading": "R9. `CaffeineResponseCache` — storage and expiry",
              "paragraph": "An `AsyncCache<CacheKey<*>, CacheEntry>` from `Caffeine.newBuilder().ticker { origin.elapsedNow().inWholeNanoseconds }` (one `markNow()` on the injected `TimeSource` at construction) `.executor(maintenanceExecutor).maximumWeight(maxWeight).weigher { _, e -> e.weight }.expireAfter(<Expiry>) .evictionListener { _, _, cause -> if (cause == RemovalCause.SIZE) evictions.increment() }.buildAsync()`. No `recordStats()`, no `Scheduler`, no `removalListener`. The four counters are `LongAdder`s the cache owns.",
              "hash": "732558b14ac5"
            }
          },
          {
            "text": "It keeps four `LongAdder`s of its own, `evictions` counted by one `evictionListener` that increments only for a size removal.",
            "ref": "plan.md#67-statistics",
            "excerpt": {
              "ref": "plan.md#67-statistics",
              "heading": "6.7 Statistics",
              "paragraph": "`recordStats()` is off. Caffeine's `evictionCount` counts every automatic removal (`SIZE`, `EXPIRED`, `COLLECTED`), so each failed or zero-lifetime entry would count as an eviction, and whether it does depends on callback order; its hit and miss counts see every `get`, so a `maxAge` re-read would count one call twice; its `loadFailureCount` stays 0 because failures complete the future normally; and it reads `System.nanoTime` around every load. The cache keeps four `LongAdder`s:",
              "hash": "eab31d246aab"
            }
          },
          {
            "text": "An entry's lifetime and weight are computed once when its load ends: a `Found` lives `ttl` and weighs its weight, a `Missing` lives `missTtl` and weighs 1, and a failed, fatal or closed outcome or a zero TTL lives 0 and weighs 0.",
            "ref": "plan.md#66-the-entry-the-lifetime-and-weight-rule-and-the-usability-rule",
            "excerpt": {
              "ref": "plan.md#66-the-entry-the-lifetime-and-weight-rule-and-the-usability-rule",
              "heading": "6.6 The entry, the lifetime and weight rule, and the usability rule",
              "paragraph": "`CacheEntry(outcome, storedAt, lifetimeNanos, weight)` is immutable and built once when a load ends. Two pure functions carry the rules:",
              "hash": "50bd3c8cdf1e"
            }
          },
          {
            "text": "A value heavier than `maxWeight` is delivered to its waiters and never stored, so the bound holds at every instant.",
            "ref": "plan.md#66-the-entry-the-lifetime-and-weight-rule-and-the-usability-rule",
            "excerpt": {
              "ref": "plan.md#66-the-entry-the-lifetime-and-weight-rule-and-the-usability-rule",
              "heading": "6.6 The entry, the lifetime and weight rule, and the usability rule",
              "paragraph": "`CacheEntry(outcome, storedAt, lifetimeNanos, weight)` is immutable and built once when a load ends. Two pure functions carry the rules:",
              "hash": "50bd3c8cdf1e"
            }
          },
          {
            "text": "`invalidate` and `invalidateAll` act on the async view: they remove a stored or in-flight entry and never touch its future, and the late completion is never re-inserted.",
            "ref": "plan.md#65-invalidation-and-close",
            "excerpt": {
              "ref": "plan.md#65-invalidation-and-close",
              "heading": "6.5 Invalidation and `close()`",
              "paragraph": "`invalidate(key)` is `cache.asMap().remove(key)` and `invalidateAll()` is `cache.asMap().clear()`, on the async view. Both remove a stored or in-flight entry and never touch its future: the calls already waiting still receive that load's outcome, the late completion is never re-inserted (Caffeine's completion step is a `replace` guarded by the future's identity), and the next call starts a new load. `synchronous().asMap()` is never used outside tests: it joins a pending future and can block. · `close()`: `closed.compareAndSet(false, true)` or return; cancel the scope; complete every future in …",
              "hash": "f354380b69ac"
            }
          },
          {
            "text": "One logger writes `INFO` on creation and close, `DEBUG` once per load end and `WARN` once for a load coroutine that failed outside its body, and never a value, a parameter value, a header, a credential or a message text.",
            "ref": "plan.md#r12-logging",
            "excerpt": {
              "ref": "plan.md#r12-logging",
              "heading": "R12. Logging",
              "paragraph": "Through `logger(CACHE_LOGGER_NAME)`, `CACHE_LOGGER_NAME = \"com.spartanlabs.webtools.http.CaffeineResponseCache\"`: `INFO` once on creation (`maxWeight`, numbers only) and once on `close()`; `DEBUG` once per load end — the key's `toString()` (id and parameter names), the outcome kind (found, missing, failed with the failure's class name, error with the class name, closed) and whether it was stored or why not (zero TTL, over `maxWeight`, not a result); `WARN` once for a load coroutine that failed outside its total body (a `loadDispatcher` that rejects the dispatch), with the exception's class …",
              "hash": "98286c2e64dc"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write the entry with its rules and `Expiry`, then the cache with its builder and factory, and extend the DSL marker's list.",
            "files": [
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/CacheEntry.kt",
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/CaffeineResponseCache.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/WebToolsDsl.kt"
            ],
            "check": "Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 1 (gating) `CaffeineResponseCacheConsumerGatingTest`, on production defaults with no opt-in",
          "Level 2 (component) the `CaffeineResponseCache` classes for hits, miss TTL, zero TTL, weight, statistics, invalidation, construction and logging",
          "Level 4a (deterministic) `CacheEntryRulesDeterministicTest`"
        ],
        "risks": [
          "A `TimeSource` that is not monotonic can lengthen lifetimes; ages are clamped at zero (§10)",
          "`Dispatchers.Default` runs a blocking loader on a compute thread, so a consumer with one sets `loadDispatcher` (§10)",
          "The default `maxWeight` of 1,000 is a judgement with no throughput measurement behind it (§10)"
        ],
        "source": "plan.md R8, R9, R12, §6.5, §6.6, §6.7, §6.9",
        "link": "plan.md#r9-caffeineresponsecache--storage-and-expiry"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`CaffeineResponseCache` is a final class with an `internal` constructor, built by `CaffeineResponseCache { }` over a `@WebToolsDsl` builder of `timeSource`, `maxWeight` (at least 1, 1,000 by default), `loadDispatcher` and `maintenanceExecutor`; its KDoc marks it Stable Core and it needs no opt-in.",
            "state": "as-planned"
          },
          {
            "text": "Its store is an `AsyncCache` with a ticker from one mark on the injected time source, `maintenanceExecutor`, a weight bound and a variable `Expiry`, with no `recordStats()`, no `Scheduler` and no `removalListener`.",
            "state": "as-planned"
          },
          {
            "text": "It keeps four `LongAdder`s of its own, `evictions` counted by one `evictionListener` that increments only for a size removal.",
            "state": "as-planned"
          },
          {
            "text": "An entry's lifetime and weight are computed once when its load ends, by the pure `residencyOf`: a `Found` lives `ttl` and weighs its weight, a `Missing` lives `missTtl` and weighs 1, and a failed, fatal or closed outcome or a zero TTL lives 0 and weighs 0.",
            "state": "as-planned"
          },
          {
            "text": "A value heavier than `maxWeight` is delivered to its waiters and never stored, with lifetime 0 and weight 0, so the bound holds at every instant.",
            "state": "as-planned"
          },
          {
            "text": "`invalidate` and `invalidateAll` act on the async view: they remove a stored or in-flight entry and never touch its future, and the late completion is not stored.",
            "state": "as-planned"
          },
          {
            "text": "One logger writes `INFO` on creation and close, `DEBUG` once per load end and `WARN` once for a load coroutine that failed outside its body, with names and class names only, never a value, a parameter value, a header, a credential or a message text.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "shared-load",
      "group": "Code",
      "name": "The shared load: its scope, its start and how waiters wait",
      "system": "webtools-http-cache",
      "where": "jvm/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "A load shared by many callers must outlive any one of them. `CompletionStage.await()` cancels the shared future for everyone when one waiter is cancelled, and a load started inside the mapping function can run inline under Caffeine's per-key lock.",
        "evidence": [
          "../research/response-cache.md F3 — `await()` against `asDeferred().await()`, measured with two and three waiters",
          "../research/response-cache.md F4 — two waiters cancelled at 100 ms of a 400 ms load: it completed and landed",
          "../research/response-cache.md F6 — `AsyncCache.get`'s contract on the mapping function; an eager dispatcher runs the loader inside it",
          "research/load-lifecycle.md F1 — under a supervisor, `launch` reports a thrown `Error` to the thread's uncaught-exception handler and `async` holds it silently for nobody",
          "research/load-lifecycle.md F2 — `CoroutineStart.DEFAULT` skips a load cancelled before it starts; `ATOMIC` runs a loader after its scope is cancelled and `UNDISPATCHED` runs its first segment on the caller's thread",
          "research/load-lifecycle.md F4 — `asDeferred().await()` never cancels the shared future; a cancelled waiter leaves one callback and one `CompletableDeferred` on it until the load ends",
          "research/load-lifecycle.md F6 — a `launch` on a rejecting dispatcher throws after the future is in the cache, and its waiters hang unless something else completes it"
        ],
        "source": "../research/README.md § Response cache; ../architecture.md §6.4",
        "link": "../research/README.md#response-cache"
      },
      "options": [
        {
          "id": "owned-scope",
          "name": "Loads run in a cache-owned `CoroutineScope(SupervisorJob() + loadDispatcher)`, started after the mapping function returns; waiters use `asDeferred().await()`; a load nobody waits for still completes and lands",
          "verdict": "accepted",
          "why": "One waiter's cancellation leaves the load and the other waiters intact (measured: 50 callers, 1 load), and the order no longer depends on the dispatcher. A shared load carries the first caller's lane and wait cap, a stated cost.",
          "source": "../architecture.md §6.4, §12",
          "link": "../architecture.md#64-response-cache"
        },
        {
          "id": "stage-await",
          "name": "Waiting with `CompletionStage.await()`",
          "verdict": "declined",
          "why": "Cancelling one waiter cancels the shared future for everyone (measured).",
          "source": "../research/response-cache.md F3",
          "link": "../research/README.md#response-cache"
        },
        {
          "id": "store5-cancel",
          "name": "Cancelling the shared load when the last waiter leaves (Store5)",
          "verdict": "declined",
          "why": "A ref-counted multicaster with its own race class; the wasted work is bounded by `maxWait` plus the call timeout for one load per key.",
          "wins": "If abandoned reservations pile up.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "load-async",
          "name": "`async` for the load",
          "verdict": "declined",
          "why": "Under a supervisor it holds an unawaited failure silently, and the shared future already is the result channel.",
          "source": "plan.md §6.4; research/load-lifecycle.md F1",
          "link": "plan.md#64-one-load-start-body-safety-nets"
        },
        {
          "id": "atomic-undispatched",
          "name": "`CoroutineStart.ATOMIC` or `UNDISPATCHED` for the load's start",
          "verdict": "declined",
          "why": "`ATOMIC` would run a loader after `close()`, and `UNDISPATCHED` runs its first segment on the caller's thread.",
          "source": "plan.md §6.4; research/load-lifecycle.md F2",
          "link": "plan.md#64-one-load-start-body-safety-nets"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "A load shared by many callers belongs to the cache, not to the caller that happened to start it.",
        "statements": [
          {
            "text": "The mapping function only allocates a future and sets a flag, and the call whose mapping ran starts the load after `get` returns.",
            "ref": "plan.md#63-one-call-caffeineresponsecachegetorload",
            "excerpt": {
              "ref": "plan.md#63-one-call-caffeineresponsecachegetorload",
              "heading": "6.3 One call: `CaffeineResponseCache.getOrLoad`",
              "paragraph": "Rules the diagram fixes:",
              "hash": "630a72c66b3b"
            }
          },
          {
            "text": "A load is a `launch` with the default start in a cache-owned scope of `SupervisorJob` and `loadDispatcher` with no parent, so a loader runs in the cache's context and not the caller's.",
            "ref": "plan.md#r11-caffeineresponsecache--a-load-invalidate-close",
            "excerpt": {
              "ref": "plan.md#r11-caffeineresponsecache--a-load-invalidate-close",
              "heading": "R11. `CaffeineResponseCache` — a load, `invalidate`, `close`",
              "paragraph": "A load is `scope.launch` (default start) in a cache-owned `CoroutineScope(SupervisorJob() + loadDispatcher + CoroutineName(…) + <handler>)` with no parent, where `<handler>` is a `CoroutineExceptionHandler` that logs a load coroutine's failure once at `WARN` (§6.4). Its body is total: it never throws except to end cancelled when `close()` cancelled the scope, and completes the shared future normally with one of four outcomes. `invalidate(key)` is `cache.asMap().remove(key)` and `invalidateAll()` is `cache.asMap().clear()` on the async view. `close()` sets the closed flag, cancels the scope, …",
              "hash": "e43833e7d9ce"
            }
          },
          {
            "text": "The scope's `CoroutineExceptionHandler` logs a failure outside the load's body once at `WARN`, with the exception's class name only, so it reaches neither the thread's handler nor a test library's collector.",
            "ref": "plan.md#64-one-load-start-body-safety-nets",
            "excerpt": {
              "ref": "plan.md#64-one-load-start-body-safety-nets",
              "heading": "6.4 One load: start, body, safety nets",
              "paragraph": "The shared future is the only result channel, and it is completed normally with a value in every case:",
              "hash": "c2c2b9583bca"
            }
          },
          {
            "text": "Every waiter awaits `future.asDeferred().await()`, so a waiter's cancellation cancels only that waiter, and a load nobody waits for any more still completes and is stored.",
            "ref": "plan.md#64-one-load-start-body-safety-nets",
            "excerpt": {
              "ref": "plan.md#64-one-load-start-body-safety-nets",
              "heading": "6.4 One load: start, body, safety nets",
              "paragraph": "The shared future is the only result channel, and it is completed normally with a value in every case:",
              "hash": "c2c2b9583bca"
            }
          },
          {
            "text": "A shared load carries the first caller's loader and `policy`; a call counts one hit or one miss at its final round, and joining a load counts as a hit.",
            "ref": "plan.md#63-one-call-caffeineresponsecachegetorload",
            "excerpt": {
              "ref": "plan.md#63-one-call-caffeineresponsecachegetorload",
              "heading": "6.3 One call: `CaffeineResponseCache.getOrLoad`",
              "paragraph": "Rules the diagram fixes:",
              "hash": "630a72c66b3b"
            }
          },
          {
            "text": "`getOrLoad` calls `ensureActive()` first, so a caller already cancelled gets its `CancellationException` even on a hit, which never suspends.",
            "ref": "plan.md#611-where-this-plan-refines-the-parent-architecture",
            "excerpt": {
              "ref": "plan.md#611-where-this-plan-refines-the-parent-architecture",
              "heading": "6.11 Where this plan refines the parent architecture",
              "paragraph": "None changes the unit's scope, a seam or a tier.",
              "hash": "840946284806"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write `getOrLoad`, the load's start and its body in the cache.",
            "files": [
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/CaffeineResponseCache.kt"
            ],
            "check": "`CaffeineResponseCacheCoalescingComponentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `CaffeineResponseCacheCoalescingComponentTest` and `CaffeineResponseCacheWaiterCancellationComponentTest`"
        ],
        "risks": [
          "A joiner with a tighter deadline than the first caller's wraps its own `getOrLoad` in `withTimeoutOrNull`, which cancels only that waiter (§10)",
          "A cancelled waiter leaves a small residue on the shared future until the load ends (§10)",
          "A loader that waits for a load of its own key waits for itself: documented, not guarded (§10)"
        ],
        "source": "plan.md R10, R11, §6.3, §6.4, §6.11",
        "link": "plan.md#64-one-load-start-body-safety-nets"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "The mapping function only allocates a future and sets a flag, and the call whose mapping ran starts the load after `get` returns.",
            "state": "as-planned"
          },
          {
            "text": "A load is a `launch` with the default start in a cache-owned scope of `SupervisorJob` and `loadDispatcher` with no parent, so a loader runs in the cache's context and not the caller's.",
            "state": "as-planned"
          },
          {
            "text": "The scope's `CoroutineExceptionHandler` logs a failure outside the load's body once at `WARN`, with the exception's class name only.",
            "state": "as-planned"
          },
          {
            "text": "Every waiter awaits `future.asDeferred().await()`, so a waiter's cancellation cancels only that waiter, and a load nobody waits for any more still completes and is stored.",
            "state": "as-planned"
          },
          {
            "text": "A shared load carries the first caller's loader and `policy`; a call counts one hit or one miss once its final round has settled, and joining a load counts as a hit.",
            "state": "as-planned"
          },
          {
            "text": "`getOrLoad` calls `ensureActive()` straight after the `maxAge` check, so a caller already cancelled gets its `CancellationException` even on a hit.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "failure-value",
      "group": "Code",
      "name": "Failures as zero-lifetime values, removed by each waiter",
      "system": "webtools-http-cache",
      "where": "common/ and jvm/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Caffeine logs a `WARNING` with a stack trace for an exceptionally completed future, unless its error is a cancellation or a timeout, and removes it in a callback a waiter can outrun: 45 of 2,000 immediate retries got the previous failure.",
        "evidence": [
          "../research/response-cache.md F2 — the log line, the removal's ordering and the 45 of 2,000 and 0 of 2,000 measurements",
          "common/NonFatal.kt:15 — `catchNonFatal`, which cancellation and an `Error` pass through",
          "research/caffeine-mechanics.md F11 — Caffeine's completion callback registers first and runs last on JDK 11's dependents stack, which opens the window a retry joins the failure through",
          "research/caffeine-mechanics.md F10 — Caffeine's `System.Logger` output reaches `java.util.logging` on this module's test classpath, and asserting that a failed load logs nothing passes vacuously without a positive control",
          "research/load-lifecycle.md F3 — a `CancellationException` in a `Result.failure` is thrown into a caller that is still active, where it reads as that caller's own cancellation",
          "research/load-lifecycle.md F4, F5 — stack-trace recovery under assertions can re-create an `Error` that travels as an exceptional completion, and one that escapes the load's body fails the test at the end of `runTest` whatever its waiters do"
        ],
        "source": "../research/README.md § Response cache; ../architecture.md §6.4",
        "link": "../research/README.md#response-cache"
      },
      "options": [
        {
          "id": "failure-as-value",
          "name": "The future completes normally with a zero-lifetime failed entry that each waiter removes by identity before returning",
          "verdict": "accepted",
          "why": "No Caffeine `WARNING` and no joinable failed future (measured: 0 of 2,000). The explicit removal is the guarantee and the zero lifetime the safety net.",
          "source": "../architecture.md §6.4",
          "link": "../architecture.md#64-response-cache"
        },
        {
          "id": "exceptional",
          "name": "`scope.future { }` with exceptional completion",
          "verdict": "declined",
          "why": "It logs a `WARNING` per failure and leaves a joinable failed future.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "error-exceptional",
          "name": "Completing the future exceptionally on an `Error`, as #55's research's `startLoad` sketch does",
          "verdict": "declined",
          "why": "Caffeine would log a `WARNING`, `launch` would report the `Error` a second time, and stack-trace recovery under assertions could re-create it.",
          "source": "plan.md §6.4, §6.11; research/README.md § Where the sources disagree",
          "link": "research/README.md#where-the-sources-disagree-and-which-view-holds"
        },
        {
          "id": "failure-leaf",
          "name": "A new `HttpFailure` leaf for a loader's own cancellation",
          "verdict": "declined",
          "why": "It would extend a failure model this unit does not own; a JDK type with the original as its `cause` keeps every caller safe, and a typed leaf stays additive later.",
          "source": "plan.md §4, §6.2; research/README.md § Options that do not fit",
          "link": "plan.md#62-the-shared-loader-runner-commonmain-internal"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "Every outcome reaches a waiter as a value, so Caffeine never sees an exceptional completion and no failure can be joined or stored.",
        "statements": [
          {
            "text": "The load's body is total and completes the shared future normally in every case, with one of four outcomes: loaded, failed, fatal or closed.",
            "ref": "plan.md#64-one-load-start-body-safety-nets",
            "excerpt": {
              "ref": "plan.md#64-one-load-start-body-safety-nets",
              "heading": "6.4 One load: start, body, safety nets",
              "paragraph": "The shared future is the only result channel, and it is completed normally with a value in every case:",
              "hash": "c2c2b9583bca"
            }
          },
          {
            "text": "A failed, fatal or closed entry lives 0, each waiter removes it by identity before returning, and a later call treats a completed zero-lifetime entry as absent, so a failure is never served to a call that did not wait for it.",
            "ref": "plan.md#63-one-call-caffeineresponsecachegetorload",
            "excerpt": {
              "ref": "plan.md#63-one-call-caffeineresponsecachegetorload",
              "heading": "6.3 One call: `CaffeineResponseCache.getOrLoad`",
              "paragraph": "Rules the diagram fixes:",
              "hash": "630a72c66b3b"
            }
          },
          {
            "text": "A failure reaches every waiter of its load as the same instance, and `failedLoads` counts it once per load.",
            "ref": "plan.md#63-one-call-caffeineresponsecachegetorload",
            "excerpt": {
              "ref": "plan.md#63-one-call-caffeineresponsecachegetorload",
              "heading": "6.3 One call: `CaffeineResponseCache.getOrLoad`",
              "paragraph": "Rules the diagram fixes:",
              "hash": "630a72c66b3b"
            }
          },
          {
            "text": "A loader's `Error` is carried by value and rethrown to every waiter as the same instance, never as an exceptional completion.",
            "ref": "plan.md#64-one-load-start-body-safety-nets",
            "excerpt": {
              "ref": "plan.md#64-one-load-start-body-safety-nets",
              "heading": "6.4 One load: start, body, safety nets",
              "paragraph": "The shared future is the only result channel, and it is completed normally with a value in every case:",
              "hash": "c2c2b9583bca"
            }
          },
          {
            "text": "A cancellation a loader throws or returns while its coroutine is still active becomes an `IllegalStateException` failure with the original as its `cause`, so a `CancellationException` is never put inside a `Result`.",
            "ref": "plan.md#62-the-shared-loader-runner-commonmain-internal",
            "excerpt": {
              "ref": "plan.md#62-the-shared-loader-runner-commonmain-internal",
              "heading": "6.2 The shared loader runner (`commonMain`, `internal`)",
              "paragraph": "`Disabled` and the Caffeine default run a loader the same way, so one function does it:",
              "hash": "4d7687c335c5"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Convert a loader's outcomes in the shared runner.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/LoaderRunner.kt"
            ],
            "check": "`LoaderRunnerComponentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S2",
            "what": "Write the four outcomes, the total body and the waiter's removal by identity.",
            "files": [
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/CacheEntry.kt",
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/CaffeineResponseCache.kt"
            ],
            "check": "`CaffeineResponseCacheFailureComponentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) the `CaffeineResponseCache` classes for a failure, a fatal `Error`, a loader's own cancellation and a rejected load",
          "Level 2 (component) `CaffeineResponseCacheCaffeineLogComponentTest`, a positive control first, then no Caffeine log record for any of them"
        ],
        "risks": [
          "An `Error` with no waiter left is counted in `failedLoads` and reported nowhere else, the accepted cost of never using `launch`'s uncaught-exception path (§10)",
          "That Caffeine never cancels a future it holds is assumed, and a waiter's guard covers it (§3)"
        ],
        "source": "plan.md §6.2, §6.3, §6.4, §6.11",
        "link": "plan.md#64-one-load-start-body-safety-nets"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "The load's body turns every end of a loader into one of four outcomes, loaded, failed, fatal or closed, and completes the shared future normally, so Caffeine never sees an exceptional completion; a body the cache's own cancellation ends is completed as closed by a safety net.",
            "state": "as-planned"
          },
          {
            "text": "A failed, fatal or closed entry lives 0, each waiter removes it by identity before returning, and a later call treats a completed zero-lifetime entry as absent, so a failure is never served to a call that did not wait for it.",
            "state": "as-planned"
          },
          {
            "text": "A failure reaches every waiter of its load as the same instance, and `failedLoads` counts it once per load.",
            "state": "as-planned"
          },
          {
            "text": "A loader's `Error` is carried by value in the entry and rethrown to every waiter as the same instance, never as an exceptional completion.",
            "state": "as-planned"
          },
          {
            "text": "`runLoader` turns a cancellation a loader throws or returns while its coroutine is still active into an `IllegalStateException` failure with the original as its `cause`, so a `CancellationException` is never put inside a `Result`.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "max-age",
      "group": "Code",
      "name": "`maxAge`: serving a caller that needs freshness",
      "system": "webtools-http-cache",
      "where": "jvm/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "A caller that needs a fresher value than the endpoint's TTL has no way to ask, and the obvious way to force a reload defeats the coalescing that makes the cache worth having.",
        "evidence": [
          "../research/response-cache.md F7 — the three options measured with 50 concurrent callers on a stale entry"
        ],
        "source": "../research/README.md § Response cache; ../architecture.md §6.4",
        "link": "../research/README.md#response-cache"
      },
      "options": [
        {
          "id": "compare-remove",
          "name": "An age check on read with a compare-and-remove",
          "verdict": "accepted",
          "why": "All the stale callers try to remove the same future: one succeeds and the rest rejoin the new load, so 50 callers cost 1 load. The age is read from the injected time source, so it is tested on fake time.",
          "source": "../architecture.md §6.4",
          "link": "../architecture.md#64-response-cache"
        },
        {
          "id": "invalidate-first",
          "name": "`invalidate` before `get`",
          "verdict": "declined",
          "why": "10 to 12 loads for 50 stale callers against 1, and it orphans a load already in flight.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "refresh-after-write",
          "name": "Caffeine's `refreshAfterWrite`",
          "verdict": "declined",
          "why": "An `AsyncCache` refuses it when built, and it is stale-while-revalidate, the wrong contract for a caller that asked for freshness.",
          "source": "../research/response-cache.md F7",
          "link": "../research/README.md#response-cache"
        },
        {
          "id": "zero-reloads",
          "name": "`maxAge = Duration.ZERO` as an unconditional reload",
          "verdict": "declined",
          "why": "One rule serves every `maxAge`: an age of at most `maxAge` is served, as RFC 9111's `max-age` request directive reads, and an endpoint that must never serve a stored entry gives a zero `ttl`.",
          "source": "plan.md R2, §6.6, §10",
          "link": "plan.md#10-risks-and-edge-cases"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One age rule, a pure function on the injected time source, serves every `maxAge` and keeps N stale callers to one load.",
        "statements": [
          {
            "text": "A stored entry of age at most `maxAge` is served, as RFC 9111's `max-age` request directive reads; an older one is not, and the call loads, or joins a load in flight, which counts as fresh.",
            "ref": "plan.md#r2-the-port-and-its-contract",
            "excerpt": {
              "ref": "plan.md#r2-the-port-and-its-contract",
              "heading": "R2. The port and its contract",
              "paragraph": "No defaulted parameters. The contract, numbered in the KDoc as `HttpTransport`'s is:",
              "hash": "db10c584982f"
            }
          },
          {
            "text": "A refused entry is removed by identity with `asMap().remove(key, future)` and the call looks again in its first round only, so it reloads at most once and N callers on one stale entry cost one load.",
            "ref": "plan.md#63-one-call-caffeineresponsecachegetorload",
            "excerpt": {
              "ref": "plan.md#63-one-call-caffeineresponsecachegetorload",
              "heading": "6.3 One call: `CaffeineResponseCache.getOrLoad`",
              "paragraph": "Rules the diagram fixes:",
              "hash": "630a72c66b3b"
            }
          },
          {
            "text": "`Duration.ZERO` follows the same rule: it serves only an entry stored at the same tick, so in practice it reloads, and an endpoint that must never serve a stored entry gives a zero `ttl`.",
            "ref": "plan.md#66-the-entry-the-lifetime-and-weight-rule-and-the-usability-rule",
            "excerpt": {
              "ref": "plan.md#66-the-entry-the-lifetime-and-weight-rule-and-the-usability-rule",
              "heading": "6.6 The entry, the lifetime and weight rule, and the usability rule",
              "paragraph": "`CacheEntry(outcome, storedAt, lifetimeNanos, weight)` is immutable and built once when a load ends. Two pure functions carry the rules:",
              "hash": "50bd3c8cdf1e"
            }
          },
          {
            "text": "The age is read from the injected time source and clamped at zero, so a clock that steps back cannot produce a negative age.",
            "ref": "plan.md#66-the-entry-the-lifetime-and-weight-rule-and-the-usability-rule",
            "excerpt": {
              "ref": "plan.md#66-the-entry-the-lifetime-and-weight-rule-and-the-usability-rule",
              "heading": "6.6 The entry, the lifetime and weight rule, and the usability rule",
              "paragraph": "`CacheEntry(outcome, storedAt, lifetimeNanos, weight)` is immutable and built once when a load ends. Two pure functions carry the rules:",
              "hash": "50bd3c8cdf1e"
            }
          },
          {
            "text": "A negative `maxAge` throws `IllegalArgumentException` before anything else, on a miss, on a hit and after `close()`.",
            "ref": "plan.md#r2-the-port-and-its-contract",
            "excerpt": {
              "ref": "plan.md#r2-the-port-and-its-contract",
              "heading": "R2. The port and its contract",
              "paragraph": "No defaulted parameters. The contract, numbered in the KDoc as `HttpTransport`'s is:",
              "hash": "db10c584982f"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write the usability rule and the call's two rounds.",
            "files": [
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/CacheEntry.kt",
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/CaffeineResponseCache.kt"
            ],
            "check": "`CaffeineResponseCacheMaxAgeComponentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `CaffeineResponseCacheMaxAgeComponentTest`: 50 stale callers cost one load; Level 4a (deterministic) `CacheEntryRulesDeterministicTest` tables the rule's edges"
        ],
        "risks": [
          "`maxAge = Duration.ZERO` reads like \"always reload\" and is not, so the KDoc says what it does (§10)"
        ],
        "source": "plan.md R2, R10, §6.3, §6.6",
        "link": "plan.md#63-one-call-caffeineresponsecachegetorload"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "A stored entry of age at most `maxAge` is served and an older one is not: the call loads, or joins a load in flight, which counts as fresh.",
            "state": "as-planned"
          },
          {
            "text": "A refused entry is removed by identity with `asMap().remove(key, future)` and the call looks again once, so it reloads at most once, and 50 callers on one stale entry cost one load in `CaffeineResponseCacheMaxAgeComponentTest`.",
            "state": "as-planned"
          },
          {
            "text": "`Duration.ZERO` follows the same rule: it serves only an entry stored at the same tick, and a zero `ttl` is what stores nothing.",
            "state": "as-planned"
          },
          {
            "text": "The age is read from the ticker on the injected time source and clamped at zero, so a clock that steps back cannot produce a negative age.",
            "state": "as-planned"
          },
          {
            "text": "A negative `maxAge` throws `IllegalArgumentException` on the first line of `getOrLoad`, tested on a miss, on a hit and after `close()`.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "close",
      "group": "Code",
      "name": "`close()` and `HttpFailure.Closed(\"cache\")`",
      "system": "webtools-http-cache",
      "where": "jvm/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Cancelling the scope reaches every waiter as a bare `CancellationException` while the waiter itself is still active, so a waiter inside a parent `launch` ends silently. `HttpFailure.Closed` exists and nothing in a cache returns it.",
        "evidence": [
          "../research/response-cache.md F5 — three waiters at close, measured with and without the conversion",
          "common/HttpFailure.kt:43 — `HttpFailure.Closed(component)` (baseline)",
          "../62-http-core/impact.md §4 — `Closed(component)` as units 7 and 8 take it",
          "research/load-lifecycle.md F3 — by its cause alone a loader's own `withTimeout` cannot be told from a scope cancelled at close, so the shape #55's research recommended would close every waiter while the cache stays open",
          "research/load-lifecycle.md F6 — a job completes later than it is cancelled, so a loader that ignores cancellation holds its waiters until it returns if only the job's completion releases them",
          "research/caffeine-mechanics.md F6 — with no `Scheduler` Caffeine holds no thread, timer or shutdown hook, and its stored values live as long as the closed object stays reachable"
        ],
        "source": "../research/README.md § Response cache; ../architecture.md §6.4",
        "link": "../research/README.md#response-cache"
      },
      "options": [
        {
          "id": "closed-entry",
          "name": "`close()` cancels the scope and completes every waiter with `Closed`, through `invokeOnCompletion` on the load",
          "verdict": "partial",
          "why": "Adopted in part: `close()` cancels the scope, every waiter gets `Closed` as a value, and `invokeOnCompletion` stays as the last completer. It no longer maps every cancellation to `Closed`, because a loader's own `withTimeout` would then close every waiter while the cache stays open, and the release no longer waits for a loader to unwind.",
          "source": "../architecture.md §6.4; ../research/response-cache.md F5; plan.md §6.4, §6.11",
          "link": "../architecture.md#64-response-cache"
        },
        {
          "id": "catch-in-waiter",
          "name": "Catching `CancellationException` in each waiter and telling its own cancellation from the load's with `ensureActive`",
          "verdict": "partial",
          "why": "Adopted in part, as a guard only: a waiter keeps one `catch` that calls `ensureActive()` and otherwise reads the closed outcome, in case a shared future is ever cancelled. The cache's own closing path still completes every future with a value, so no exception arises on it.",
          "source": "../research/response-cache.md F5; plan.md §3, §6.8",
          "link": "../research/README.md#response-cache"
        },
        {
          "id": "bare-cancellation",
          "name": "Letting the scope's cancellation reach the waiters",
          "verdict": "declined",
          "why": "It looks like normal cancellation, so the waiter's parent ends silently (measured).",
          "source": "../research/response-cache.md F5",
          "link": "../research/README.md#response-cache"
        },
        {
          "id": "sweep-close",
          "name": "`close()` also completes every future in the map itself and clears the map, with the starter's re-check of the closed flag and `invokeOnCompletion` behind it as independent completers",
          "verdict": "accepted",
          "why": "A waiter's release must not wait for a loader to unwind, a loader may ignore cancellation, and the stored values are released at `close()`.",
          "source": "plan.md §6.4, §6.5, §6.11; research/load-lifecycle.md F3, F6",
          "link": "plan.md#611-where-this-plan-refines-the-parent-architecture"
        },
        {
          "id": "on-cancelling",
          "name": "`invokeOnCompletion(onCancelling = true)`, which fires at cancellation",
          "verdict": "declined",
          "why": "It is internal API of kotlinx-coroutines.",
          "source": "plan.md §6.4; research/load-lifecycle.md F2",
          "link": "plan.md#64-one-load-start-body-safety-nets"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "A closed cache is a value in the failure model, and a waiter's release depends on no loader's cooperation.",
        "statements": [
          {
            "text": "`close()` sets the closed flag, cancels the scope, completes every future still in the map with the closed outcome and clears the map; it is idempotent, non-suspending and never throws.",
            "ref": "plan.md#65-invalidation-and-close",
            "excerpt": {
              "ref": "plan.md#65-invalidation-and-close",
              "heading": "6.5 Invalidation and `close()`",
              "paragraph": "`invalidate(key)` is `cache.asMap().remove(key)` and `invalidateAll()` is `cache.asMap().clear()`, on the async view. Both remove a stored or in-flight entry and never touch its future: the calls already waiting still receive that load's outcome, the late completion is never re-inserted (Caffeine's completion step is a `replace` guarded by the future's identity), and the next call starts a new load. `synchronous().asMap()` is never used outside tests: it joins a pending future and can block. · `close()`: `closed.compareAndSet(false, true)` or return; cancel the scope; complete every future in …",
              "hash": "f354380b69ac"
            }
          },
          {
            "text": "Three independent completers keep a waiter from hanging: the load's body, `close()`'s sweep with the starter's re-check of the flag, and `invokeOnCompletion` as the last; `CompletableFuture.complete` is first-wins, so the extras are harmless.",
            "ref": "plan.md#64-one-load-start-body-safety-nets",
            "excerpt": {
              "ref": "plan.md#64-one-load-start-body-safety-nets",
              "heading": "6.4 One load: start, body, safety nets",
              "paragraph": "The shared future is the only result channel, and it is completed normally with a value in every case:",
              "hash": "c2c2b9583bca"
            }
          },
          {
            "text": "After `close()`, `getOrLoad` returns `HttpFailure.Closed(\"cache\")` without running the loader, and `invalidate`, `invalidateAll` and `stats()` keep working on the empty map.",
            "ref": "plan.md#65-invalidation-and-close",
            "excerpt": {
              "ref": "plan.md#65-invalidation-and-close",
              "heading": "6.5 Invalidation and `close()`",
              "paragraph": "`invalidate(key)` is `cache.asMap().remove(key)` and `invalidateAll()` is `cache.asMap().clear()`, on the async view. Both remove a stored or in-flight entry and never touch its future: the calls already waiting still receive that load's outcome, the late completion is never re-inserted (Caffeine's completion step is a `replace` guarded by the future's identity), and the next call starts a new load. `synchronous().asMap()` is never used outside tests: it joins a pending future and can block. · `close()`: `closed.compareAndSet(false, true)` or return; cancel the scope; complete every future in …",
              "hash": "f354380b69ac"
            }
          },
          {
            "text": "The cache's own cancellation never reaches a caller as a `CancellationException`: the only one a waiter throws is its own.",
            "ref": "plan.md#68-error-handling-of-every-new-or-changed-signature",
            "excerpt": {
              "ref": "plan.md#68-error-handling-of-every-new-or-changed-signature",
              "heading": "6.8 Error handling of every new or changed signature",
              "paragraph": "The four `try`/`catch` sites (`runLoader`, `awaitEntry`, the `launch` in `startLoad`, and the body of `load`) are the places where a `CancellationException` must be told from other throwables, or a `Throwable` converted to a value; `catchNonFatal` cannot express them because it rethrows cancellation. Everything else uses `fold`, `flatMap` and `catchNonFatal`.",
              "hash": "648c944ac1c3"
            }
          },
          {
            "text": "The cache owns a `SupervisorJob` scope and no thread, timer or pool, and it never shuts the `maintenanceExecutor` down.",
            "ref": "plan.md#610-thread-safety-ownership-and-resources",
            "excerpt": {
              "ref": "plan.md#610-thread-safety-ownership-and-resources",
              "heading": "6.10 Thread safety, ownership and resources",
              "paragraph": "All mutable state is an atomic or Caffeine's own: `closed: AtomicBoolean`, four `LongAdder`s, the `AsyncCache`. The entry and the key are immutable. `Disabled` is stateless. Every member may be called from any thread and from many coroutines. · Three execution contexts: the callers' coroutines (they only await), the cache's load coroutines on `loadDispatcher`, and Caffeine's maintenance tasks on `maintenanceExecutor`. A loader runs in the cache's scope, not in a caller's: the caller's dispatcher, `CoroutineName` and context elements do not reach it. `Disabled` is the exception: it runs the …",
              "hash": "25f7dc361fe2"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write `close()`, its sweep and the completers in the cache.",
            "files": [
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/CaffeineResponseCache.kt"
            ],
            "check": "`CaffeineResponseCacheCloseComponentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `CaffeineResponseCacheCloseComponentTest`, with a load in flight, a loader that ignores cancellation and a close before the load starts; Level 4c (non-functional) `CaffeineResponseCacheCloseRaceNonFunctionalTest`"
        ],
        "risks": [
          "The close-versus-start window rests on the insert-then-check and on weakly consistent iteration, and only the probabilistic Level 4c test probes it (§3)"
        ],
        "source": "plan.md R11, §6.4, §6.5, §6.10, §6.11",
        "link": "plan.md#65-invalidation-and-close"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`close()` sets the closed flag, cancels the scope, completes every future still in the map with the closed outcome and clears the map; it is idempotent and non-suspending, and each step runs under `catchNonFatal`.",
            "state": "as-planned"
          },
          {
            "text": "Three independent completers keep a waiter from hanging: the load's body, `close()`'s sweep with the starter's re-check of the flag, and `invokeOnCompletion` as the last; `CompletableFuture.complete` is first-wins, so the extras do nothing.",
            "state": "as-planned"
          },
          {
            "text": "After `close()`, `getOrLoad` returns `HttpFailure.Closed(\"cache\")` without running the loader, and `invalidate`, `invalidateAll` and `stats()` keep working on the empty map.",
            "state": "as-planned"
          },
          {
            "text": "A waiter that sees a cancellation it does not own reads it as the closed outcome, after `ensureActive()`, so the only `CancellationException` a waiter throws is its own.",
            "state": "as-planned"
          },
          {
            "text": "The cache owns a `SupervisorJob` scope and no thread, timer or pool, and it never shuts `maintenanceExecutor` down.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "component-tests",
      "group": "Tests",
      "name": "Level 2 on `VirtualClock`, and the three-clocks proof with the real cache",
      "system": "webtools-http-cache",
      "where": "commonTest and jvmTest (new, `testing.component`)",
      "gap": {
        "kind": "missing",
        "summary": "No test covers a cache, and the three-clocks proof runs on a Caffeine cache standing in for the one this unit builds.",
        "evidence": [
          "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/VirtualClockThreeClocksComponentTest.kt:24 — \"Caffeine stands in for the cache a later unit builds\" (baseline)",
          "webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/VirtualClock.kt:47, :66, :73 — `timeSource`, `dispatcher` and `maintenanceExecutor` (baseline)",
          "../63-test-support/impact.md §4 — this unit passes those three to `CaffeineResponseCache` and re-runs the proof",
          "research/load-lifecycle.md F7 — `runTest` 1.10.2 drains the whole shared scheduler before it collects unhandled exceptions, the cache's own queued coroutines and pending `delay`s included, and `runCurrent()` also runs what the tasks it runs enqueue",
          "research/caffeine-mechanics.md F9 — with a same-thread executor every write drains maintenance inline, so `cleanUp()` then the eviction policy's `weightedSize()` is exact",
          "research/README.md § Codebase blast radius — `VirtualClockThreeClocksComponentTest` keeps its plain Caffeine cache, proving the clock apart from the response cache; only its KDoc changes"
        ],
        "source": "../architecture.md §11; ../consequences.md §8",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "virtual-clock",
          "name": "Level 2 on `VirtualClock`: hits, expiry, miss TTL, `maxAge`, coalescing, one waiter's cancellation and close, with the three-clocks proof re-run on the real cache",
          "verdict": "accepted",
          "why": "Deterministic, with no network and no real time: the research ran the same behaviours on fake time, and the default dispatcher is a constructor parameter for it.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        },
        {
          "id": "cache-fake",
          "name": "A cache fake in the published fixtures",
          "verdict": "declined",
          "why": "`CaffeineResponseCache` on `VirtualClock` is the test double, so the fixtures do not change; a fake stays a follow-up if a consumer needs one beyond it.",
          "source": "plan.md §4, §13",
          "link": "plan.md#4-non-goals"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "Virtual time makes every interleaving deterministic, with no network and no real clock.",
        "statements": [
          {
            "text": "Level 2 holds every contract point of the port for `CaffeineResponseCache` on `VirtualClock`, each interleaving driven with `advanceBy` and asserted on its state, never with `join()`, so a hang fails at once.",
            "ref": "plan.md#9-test-plan",
            "excerpt": {
              "ref": "plan.md#9-test-plan",
              "heading": "9. Test plan",
              "paragraph": "Conventions that bind every class below:",
              "hash": "c7e1a4b00893"
            }
          },
          {
            "text": "`commonTest` holds the value types and `jvmTest` everything that suspends, with hand-written fakes and every cache closed with `use`.",
            "ref": "plan.md#9-test-plan",
            "excerpt": {
              "ref": "plan.md#9-test-plan",
              "heading": "9. Test plan",
              "paragraph": "Conventions that bind every class below:",
              "hash": "c7e1a4b00893"
            }
          },
          {
            "text": "`CaffeineResponseCacheThreeClocksComponentTest` re-runs the three-clocks proof with the real cache, and `VirtualClockThreeClocksComponentTest` keeps its plain Caffeine cache and changes only its KDoc.",
            "ref": "plan.md#level-2--component-componenttest",
            "excerpt": {
              "ref": "plan.md#level-2--component-componenttest",
              "heading": "Level 2 — component (`componentTest`)",
              "paragraph": "`commonTest` — the value types:",
              "hash": "ae61e693191f"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write the value types' tests, the runner's and `Disabled`'s.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S2",
            "what": "Write the cache's classes on `VirtualClock` and the test support they need.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/support/webtools/http/"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component): the 24 new classes of the test plan and the changed `WebToolsDslComponentTest`"
        ],
        "risks": [],
        "source": "plan.md §9",
        "link": "plan.md#level-2--component-componenttest"
      },
      "asBuilt": {
        "state": "amended",
        "statements": [
          {
            "text": "The eighteen `CaffeineResponseCache` classes, 84 tests, hold the port's contract on `VirtualClock`, each interleaving driven with `advanceBy` and asserted on its state through a `Waiter` recording the launched call, never with `join()`; the `maxAge` age edge steps by one millisecond, the clock's finest step.",
            "state": "amended",
            "ref": "final-implementation.md#2-deviations",
            "excerpt": {
              "ref": "final-implementation.md#2-deviations",
              "heading": "2. Deviations",
              "paragraph": "Each entry names what differs from the plan, where, and why; it cites the plan section it departs from and links secondarily to what that section was based on. D4 changes one production rule, the load-end log line, and no public signature or line of the ABI dump; the other four change tests and their support only. D3 comes from the test review and D4 from the code review.",
              "hash": "4e953e51a2f2"
            }
          },
          {
            "text": "`commonTest` holds the value types and `jvmTest` everything that suspends, with hand-written fakes; every cache is closed, with `use`, or directly in the three tests whose subject is `close()` itself.",
            "state": "as-planned"
          },
          {
            "text": "`CaffeineResponseCacheThreeClocksComponentTest` re-ran the three-clocks proof with the real cache and its control on the monotonic clock, and `VirtualClockThreeClocksComponentTest` kept its plain Caffeine cache, its diff the KDoc alone.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "`Waiter.kt` joined the test support, with `Waiter`, the recorder, and `CoroutineScope.call(...)`, the launcher; the plan lists `CacheTestSupport.kt`, `ParkingDispatcher.kt`, `JulCapture.kt` and `Boom.kt` only.",
            "why": "The Level 2 convention asserts on the completion state and the recorded result, never with `join()`, and recording a launched `getOrLoad` call's result or throwable needs the same few lines in each class, so they live once. No design decision or research finding is affected; the names the test support takes gain these two.",
            "when": "S2",
            "ref": "final-implementation.md#deviations",
            "link": "plan.md#712-test-support-new-files-under-webtools-httpsrcjvmtestkotlincomspartanlabstestingsupportwebtoolshttp"
          },
          {
            "what": "The `maxAge` age edge in `CaffeineResponseCacheMaxAgeComponentTest` is one millisecond over, not one nanosecond.",
            "why": "`VirtualClock` moves time through `TestCoroutineScheduler.advanceTimeBy`, which truncates its `Duration` to whole milliseconds, so the clock cannot step by a nanosecond. The exact nanosecond edge is tested on the pure function by `CacheEntryRulesDeterministicTest`; the rule itself is unchanged.",
            "when": "S2",
            "ref": "final-implementation.md#deviations",
            "link": "../architecture.md#67-published-test-support"
          }
        ]
      }
    },
    {
      "id": "integration-tests",
      "group": "Tests",
      "name": "Level 3: one real request for many callers, on the production defaults",
      "system": "webtools-http-cache",
      "where": "jvmTest (new, `testing.integration`)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing shows that concurrent callers of one key cost one real HTTP request on the cache's production defaults: the Level 2 tests run on virtual time, and #55's decomposition names no Level 3 suite for this unit.",
        "evidence": [
          "../architecture.md §11 unit 7 — Level 2, Level 4a and Level 4c, and no Level 3",
          "webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/ — `LoopbackTransports.untrusted` and `MockWebServer.webtoolsUrl`, from #65, the real transport against a loopback server",
          "plan.md §1 Acceptance 4 — N concurrent callers of one key cost one real request, and a later call is a hit"
        ],
        "source": "plan.md §1, §9; ../architecture.md §11",
        "link": "plan.md#level-3--integration-integrationtest"
      },
      "options": [
        {
          "id": "loopback-suite",
          "name": "One Level 3 class over a real loopback exchange, on the production `TimeSource`, dispatcher and maintenance executor, with `ResponseCache.Disabled` as its control",
          "verdict": "accepted",
          "why": "It counts the requests a server really received, with real threads and real time, which virtual time cannot show.",
          "source": "plan.md §9",
          "link": "plan.md#level-3--integration-integrationtest"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "It is the one test that counts what a server received, on the defaults a consumer gets.",
        "statements": [
          {
            "text": "`CaffeineResponseCacheLoopbackIntegrationTest` runs the production defaults over a real loopback exchange, under `runBlocking` with a `withTimeout` bound and never `runTest`.",
            "ref": "plan.md#level-3--integration-integrationtest",
            "excerpt": {
              "ref": "plan.md#level-3--integration-integrationtest",
              "heading": "Level 3 — integration (`integrationTest`)",
              "paragraph": "",
              "hash": "dadff2c806d9"
            }
          },
          {
            "text": "16 concurrent callers of one key cost one request at the server and a later call is a hit, while a control of 8 callers on `ResponseCache.Disabled` costs 8.",
            "ref": "plan.md#level-3--integration-integrationtest",
            "excerpt": {
              "ref": "plan.md#level-3--integration-integrationtest",
              "heading": "Level 3 — integration (`integrationTest`)",
              "paragraph": "",
              "hash": "dadff2c806d9"
            }
          },
          {
            "text": "A `500` the loader maps to a failure is not stored, and `close()` during a delayed response returns `Closed(\"cache\")` to the waiting callers within the bound.",
            "ref": "plan.md#level-3--integration-integrationtest",
            "excerpt": {
              "ref": "plan.md#level-3--integration-integrationtest",
              "heading": "Level 3 — integration (`integrationTest`)",
              "paragraph": "",
              "hash": "dadff2c806d9"
            }
          },
          {
            "text": "No Level 4b suite is written, because no end-to-end flow exists until the call facades (#68).",
            "ref": "plan.md#level-4b-and-level-5",
            "excerpt": {
              "ref": "plan.md#level-4b-and-level-5",
              "heading": "Level 4b and Level 5",
              "paragraph": "Level 4b: none. No end-to-end flow exists until the facades (#68); Level 3 above covers the cache over a real transport. · Level 5: a short manual pass (§9.2 P5): what a consumer reads — the KDoc of the new types and the regenerated dump. The IDE checks (completion of `CaffeineResponseCache { }`, the opt-in error on an implementer, the `@WebToolsDsl` scoping of `CachePolicy { }`) are left to the maintainer's next IntelliJ session, as for units 3 to 6.",
              "hash": "6fc3cc8f5ead"
            }
          }
        ],
        "steps": [
          {
            "id": "S3",
            "what": "Write the Level 3 class over the fixtures' loopback transport.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/CaffeineResponseCacheLoopbackIntegrationTest.kt"
            ],
            "check": "`:webtools-http:integrationTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 3 (integration) `CaffeineResponseCacheLoopbackIntegrationTest`"
        ],
        "risks": [],
        "source": "plan.md §1, §9",
        "link": "plan.md#level-3--integration-integrationtest"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`CaffeineResponseCacheLoopbackIntegrationTest`, 4 tests in a green `integrationTest` run of 98 tests in 25 classes, ran the production defaults over a real loopback exchange, under `runBlocking` with a 30-second `withTimeout` and never `runTest`.",
            "state": "as-planned",
            "ref": "final-implementation.md#4-measurements",
            "excerpt": {
              "ref": "final-implementation.md#4-measurements",
              "heading": "4. Measurements",
              "paragraph": "Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other module's suite.",
              "hash": "6abc2ca5c490"
            }
          },
          {
            "text": "16 concurrent callers of one key cost one request at the server, counted 15 hits and 1 miss, and a later call was a hit, while the control of 8 callers on `ResponseCache.Disabled` cost 8 requests.",
            "state": "as-planned"
          },
          {
            "text": "A `500` the loader mapped to `UpstreamError` was not stored: the next call reached the server, a following `200` was stored and `failedLoads` was 1. `close()` during a delayed response returned `Closed(\"cache\")` to the 4 waiting callers within the bound and cancelled the transport call.",
            "state": "as-planned"
          },
          {
            "text": "No Level 4b suite was written.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "deterministic-tests",
      "group": "Tests",
      "name": "Level 4a: key canonicalisation",
      "system": "webtools-http-cache",
      "where": "test source (new, `testing.deterministic`)",
      "gap": {
        "kind": "missing",
        "summary": "The key's equality, its stable sort and what its `toString` prints are pure input-to-output rules, and no table holds them.",
        "evidence": [
          "../research/response-cache.md F12 — repeated names keep the caller's order; `toString` prints names only"
        ],
        "source": "../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "key-tables",
          "name": "Level 4a tables over `CacheKey`",
          "verdict": "accepted",
          "why": "The rules are pure, input to output, with no clock and no cache.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "These rules are pure, input to output, with no clock and no cache.",
        "statements": [
          {
            "text": "`CacheKeyCanonicalFormDeterministicTest` tables the key's equality, stable sort, case sensitivity and names-only `toString()`, against a hand-written stable reference over 200 seeded random lists.",
            "ref": "plan.md#level-4a--deterministic-deterministictest",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic-deterministictest",
              "heading": "Level 4a — deterministic (`deterministicTest`)",
              "paragraph": "",
              "hash": "6a4f3f02545d"
            }
          },
          {
            "text": "`CacheEntryRulesDeterministicTest` tables the lifetime and weight rule and the usability rule over their pure functions, the age edges and a clock that stepped back included.",
            "ref": "plan.md#level-4a--deterministic-deterministictest",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic-deterministictest",
              "heading": "Level 4a — deterministic (`deterministicTest`)",
              "paragraph": "",
              "hash": "6a4f3f02545d"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write the key's table.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/CacheKeyCanonicalFormDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S2",
            "what": "Write the entry's table.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/CacheEntryRulesDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4a (deterministic) `CacheKeyCanonicalFormDeterministicTest` and `CacheEntryRulesDeterministicTest`"
        ],
        "risks": [],
        "source": "plan.md §9",
        "link": "plan.md#level-4a--deterministic-deterministictest"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`CacheKeyCanonicalFormDeterministicTest`, 9 tests, tables the key's equality, stable sort, case sensitivity and names-only `toString()`, against a hand-written stable reference over 200 seeded random lists.",
            "state": "as-planned"
          },
          {
            "text": "`CacheEntryRulesDeterministicTest`, 10 tests, tables the lifetime and weight rule and the usability rule over `residencyOf` and `isUsable`, the age edges to the nanosecond and a clock that stepped back included.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "nonfunctional-tests",
      "group": "Tests",
      "name": "Level 4c: the size bound and the 2,000-retry failure-identity check",
      "system": "webtools-http-cache",
      "where": "jvmTest (new, `testing.nonfunctional`)",
      "gap": {
        "kind": "missing",
        "summary": "The bound and the failure identity only show under load: size eviction is eventual, and the race that joined a retry to the previous failure appeared in 45 of 2,000 trials.",
        "evidence": [
          "../research/response-cache.md F2 — the 2,000-retry measurement",
          "../research/response-cache.md F9 — size eviction settles only after maintenance, and is deterministic on one thread",
          "research/caffeine-mechanics.md F9 — only the common pool reschedules unfinished maintenance at once, so on another real executor a burst can stay over the bound until later activity or a `cleanUp()`",
          "research/caffeine-mechanics.md F11 — the race takes 8 to 32 joiners per key, immediate retries and fresh keys on real threads; the count on a given machine is not guaranteed and the parameters are untuned"
        ],
        "source": "../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "load-checks",
          "name": "Level 4c checks of the size bound and of 2,000 immediate retries after a failed load",
          "verdict": "accepted",
          "why": "They hold the two properties the design exists for at the scale that exposed them.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "The bound, the failure identity, the secrecy and the close race only show at scale and on real threads.",
        "statements": [
          {
            "text": "`CaffeineResponseCacheSizeBoundNonFunctionalTest` has 8 workers insert 20,000 keys of weights 1 to 5 under a `maxWeight` of 100 on real threads, and once `cleanUp()` is stable the weighted size is at most the bound.",
            "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
            "excerpt": {
              "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
              "heading": "Level 4c — non-functional (`nonfunctionalTest`)",
              "paragraph": "",
              "hash": "4db855ef5cf2"
            }
          },
          {
            "text": "`CaffeineResponseCacheFailureIdentityNonFunctionalTest` runs at least 2,000 retries, with 8 to 32 joiners per fresh key, and no retry receives the failure it just received.",
            "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
            "excerpt": {
              "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
              "heading": "Level 4c — non-functional (`nonfunctionalTest`)",
              "paragraph": "",
              "hash": "4db855ef5cf2"
            }
          },
          {
            "text": "`CaffeineResponseCacheSecrecyNonFunctionalTest` passes sentinels through every outcome at `DEBUG`, and none appears in a log event or a `toString()`.",
            "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
            "excerpt": {
              "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
              "heading": "Level 4c — non-functional (`nonfunctionalTest`)",
              "paragraph": "",
              "hash": "4db855ef5cf2"
            }
          },
          {
            "text": "`CaffeineResponseCacheCloseRaceNonFunctionalTest` races `close()` against 16 calling threads, and every call returns a value or `Closed(\"cache\")` within a bound.",
            "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
            "excerpt": {
              "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
              "heading": "Level 4c — non-functional (`nonfunctionalTest`)",
              "paragraph": "",
              "hash": "4db855ef5cf2"
            }
          },
          {
            "text": "Procedures P3 and P4 remove the guarded lines temporarily and record whether the failure-identity and close-race tests detect it.",
            "ref": "plan.md#92-procedures-not-committed-tests",
            "excerpt": {
              "ref": "plan.md#92-procedures-not-committed-tests",
              "heading": "9.2 Procedures (not committed tests)",
              "paragraph": "Runs (from the repository root; on Windows `gradlew.bat`). Only `webtools-http`'s own tasks and the check module's `js` compile; no other module's suite, before or after:",
              "hash": "bbac79d3a51a"
            }
          }
        ],
        "steps": [
          {
            "id": "S3",
            "what": "Write the four Level 4c classes, then run the two mutation procedures.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/"
            ],
            "check": "Procedures P3 and P4",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4c (non-functional): the four classes, on real time under `runBlocking` with a `withTimeout` bound"
        ],
        "risks": [
          "The failure-identity race needs at least two cores: on one the test passes without having exercised it (§3)",
          "Both race checks assert the absence of a bad outcome over many repetitions and cannot prove it (§9)"
        ],
        "source": "plan.md §9, §9.2",
        "link": "plan.md#level-4c--non-functional-nonfunctionaltest"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "In a green `nonfunctionalTest` run of 35 tests in 19 classes, `CaffeineResponseCacheSizeBoundNonFunctionalTest` had 8 workers insert 20,000 keys of seeded weights 1 to 5 under a `maxWeight` of 100 on real threads, and once `cleanUp()` read stable the weighted size was within the bound, with evictions counted.",
            "state": "as-planned",
            "ref": "final-implementation.md#4-measurements",
            "excerpt": {
              "ref": "final-implementation.md#4-measurements",
              "heading": "4. Measurements",
              "paragraph": "Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other module's suite.",
              "hash": "6abc2ca5c490"
            }
          },
          {
            "text": "`CaffeineResponseCacheFailureIdentityNonFunctionalTest` ran at least 2,000 retries, with 8 to 32 joiners per fresh key, and no retry received the failure it had just received; its failure message records the host's core count.",
            "state": "as-planned"
          },
          {
            "text": "`CaffeineResponseCacheSecrecyNonFunctionalTest` passed sentinels through a found, a missing, a failed, a fatal and a closed load at `DEBUG`, and none appeared in a log event or in the `toString()` of the key, the policy, the `Found`, the `CacheStats`, the cache or `Disabled`, while the id and the parameter names appeared in the load lines.",
            "state": "as-planned"
          },
          {
            "text": "`CaffeineResponseCacheCloseRaceNonFunctionalTest` raced `close()` against 16 callers on `Dispatchers.Default` over 300 rounds on fresh caches, and every call returned a value or `Closed(\"cache\")` within each round's 10-second bound, never a `CancellationException`.",
            "state": "as-planned"
          },
          {
            "text": "Procedure P3 left out both lifetime-0 removals and the failure-identity test failed in 5 of 5 runs on a 32-core host, each in round 0. Procedure P4 left out the closed-flag re-check in `startLoad` and the close-race test passed in 5 of 5 runs, so it did not detect the mutation; the as-built record reads this as the cancelled scope's `launch` completing without its body and `invokeOnCompletion` settling the future as closed, a reading not instrumented. Both lines were restored byte for byte. P4's result stands as a known gap, in `consequences.md` §8.",
            "state": "as-planned",
            "ref": "final-implementation.md#4-measurements",
            "excerpt": {
              "ref": "final-implementation.md#4-measurements",
              "heading": "4. Measurements",
              "paragraph": "Procedure results and runs, in the order they ran. Only `webtools-http`'s own tasks and the check module's `js` compile were run (plan §9.2), no other module's suite.",
              "hash": "6abc2ca5c490"
            }
          }
        ]
      }
    },
    {
      "id": "release",
      "group": "Release",
      "name": "The ABI dump and the version",
      "system": "webtools-http-cache",
      "where": "webtools-http/api/webtools-http.api",
      "gap": {
        "kind": "missing",
        "summary": "`webtools-http` is new and unreleased at `1.0.0`. Its committed ABI dump records no cache type.",
        "evidence": [
          "webtools-http/api/webtools-http.api — the dump at the baseline, no cache type",
          "../architecture.md §11 — unit 7 moves no version",
          "research/README.md § Codebase blast radius — Caffeine is already declared, so no build line changes; the dump and two KDoc lists do, and the fixtures-publication check runs again because `jvmMain` is among its inputs"
        ],
        "source": "../architecture.md §9, §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "no-version",
          "name": "No version moved: `webtools-http` stays unreleased at `1.0.0` until the stack is complete, and the dump grows with the cache",
          "verdict": "accepted",
          "why": "The units that build an unreleased module move no version.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "No version moves: `webtools-http` is new and unreleased, and this landing is not a release candidate.",
        "statements": [
          {
            "text": "No version moves: `webtools-http` stays unreleased at `1.0.0`, no existing module moves, and no build line changes, as `caffeine` 3.3.1 is already on `jvmMain`.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-66-response-cache`. It exists and holds the four research commits; the implementer confirms it rather than cutting it again. · Commits. Each leaves the build green (`apiCheck` included). The type and the split are the final call at commit time; subject lines end with `(Issue #66)`; no attribution trailers. · `feat: add the response cache port, its value types and ResponseCache.Disabled (Issue #66)` — this `plan.md` and the planned `consequences.md` of this folder; `Lookup.kt`, `CacheKey.kt`, `CachePolicy.kt`, `CacheStats.kt`, `LoaderRunner.kt`, `ResponseCache.kt`; the KDoc …",
              "hash": "cdfea87ddc2b"
            }
          },
          {
            "text": "The ABI dump, regenerated at each code stage, grows by exactly the listed classes and holds nothing `internal` and no Caffeine type.",
            "ref": "plan.md#716-webtools-httpapiwebtools-httpapi-regenerated",
            "excerpt": {
              "ref": "plan.md#716-webtools-httpapiwebtools-httpapi-regenerated",
              "heading": "7.16 `webtools-http/api/webtools-http.api` (regenerated)",
              "paragraph": "Run `:webtools-http:apiDump` once per code stage and commit the result with it; review the diff by eye. The dump gains exactly these classes and members (the inline-class `Result` return and the `Duration` parameters mangle some names with a hash suffix; take the generated spelling):",
              "hash": "1013176c5bea"
            }
          },
          {
            "text": "`README.md` names the response cache in the `webtools-http` row, and `CHANGELOG.md` takes one `### Added` entry under `## [Unreleased]`.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Audience-Reach rings touched.",
              "hash": "e7cdc5ab019b"
            }
          },
          {
            "text": "The parent architecture gets dated corrections at §6.4 and its §7.4 row, written in the as-built reconciliation.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Audience-Reach rings touched.",
              "hash": "e7cdc5ab019b"
            }
          },
          {
            "text": "Five commits on `feat/issue-66-response-cache`, each leaving the build green, the first carrying the plan.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-66-response-cache`. It exists and holds the four research commits; the implementer confirms it rather than cutting it again. · Commits. Each leaves the build green (`apiCheck` included). The type and the split are the final call at commit time; subject lines end with `(Issue #66)`; no attribution trailers. · `feat: add the response cache port, its value types and ResponseCache.Disabled (Issue #66)` — this `plan.md` and the planned `consequences.md` of this folder; `Lookup.kt`, `CacheKey.kt`, `CachePolicy.kt`, `CacheStats.kt`, `LoaderRunner.kt`, `ResponseCache.kt`; the KDoc …",
              "hash": "cdfea87ddc2b"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Regenerate the ABI dump for the common types.",
            "files": [
              "webtools-http/api/webtools-http.api"
            ],
            "check": "Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S2",
            "what": "Regenerate the ABI dump for the Caffeine default.",
            "files": [
              "webtools-http/api/webtools-http.api"
            ],
            "check": "Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S4",
            "what": "Update `README.md` and `CHANGELOG.md`.",
            "files": [
              "README.md",
              "CHANGELOG.md"
            ],
            "check": "§8",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "S5",
            "what": "Reconcile the as-built record and the consequences, and write the architecture's dated corrections.",
            "files": [
              "docs/plans/55-http-client-stack/architecture.md"
            ],
            "check": "§11",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 5 (user acceptance), manual: Procedure P5, the KDoc and the dump read as a consumer sees them",
          "`:webtools-http:check`, `apiCheck`, `verifyJavaFloor` and `:webtools-http-commoncheck:build`, and no other module's suite"
        ],
        "risks": [
          "`Result` in a public suspend interface method gives a mangled name and a `DefaultImpls` class in the dump, as `HttpTransport` does: nothing new in kind (§10)"
        ],
        "source": "plan.md §7.16, §8, §11",
        "link": "plan.md#11-version-control"
      },
      "asBuilt": {
        "state": "pending",
        "summary": ""
      }
    },
    {
      "id": "loader-content",
      "group": "Left for later",
      "name": "The loader's content: the budget, the transport, the decode and what is a miss",
      "system": "webtools-http-cache",
      "where": "common/ (unit 9, #68)",
      "gap": {
        "kind": "missing",
        "summary": "The port takes a loader and does not own what runs inside it: the budget, the transport, the status check, the decode and which responses are a confirmed miss.",
        "evidence": [
          "../architecture.md §6.4 — does not own what is a miss, the budget or raw bodies",
          "../architecture.md §7.2 — the call path: cache, then budget inside the loader, then transport"
        ],
        "source": "../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "unit-9",
          "name": "Left to unit 9 (#68), the call facades, with each endpoint declaring its own misses",
          "verdict": "accepted",
          "why": "#55's decomposition gives the loader to the unit that composes the cache, the budget and the transport, and a `404` from a wrong base URL stays a visible failure.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        },
        {
          "id": "default-404",
          "name": "`404` as a confirmed miss by default",
          "verdict": "declined",
          "why": "It matches the common case, but hides a misconfigured host for `missTtl`.",
          "source": "../research/response-cache.md Considerations 3",
          "link": "../research/README.md#response-cache"
        }
      ],
      "verdict": {
        "kind": "leave",
        "defense": "The loader belongs to the call facades, unit 9 (#68), which compose the cache, the budget and the transport.",
        "statements": [
          {
            "text": "Key assembly, the loader, the classification of misses and the use of `stats()` as `cacheStats()` are #68's, and nothing of them is built here.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "Stale-while-revalidate (`staleFor`): the `CachePolicy` builder is where it goes later, additively. · A cache default for non-JVM targets beyond `ResponseCache.Disabled`. · A separate count of joined calls, or size and weight gauges, in `CacheStats` (each additive later through a new constructor of the class). · A typed failure leaf for a loader's own cancellation (the failure model belongs to #62; adding a leaf is additive in a feature release). Until then the failure is an `IllegalStateException` with the original as its `cause`. · Invalidating every key of one endpoint. · A cache fake in …",
              "hash": "80095dba09df"
            }
          },
          {
            "text": "The port pins what the facades rely on: a loader returns failures as values, runs in the cache's scope on `loadDispatcher` and must not wait for a load of its own key.",
            "ref": "plan.md#12-interfaces-with-sibling-units",
            "excerpt": {
              "ref": "plan.md#12-interfaces-with-sibling-units",
              "heading": "12. Interfaces with sibling units",
              "paragraph": "Names this unit takes (a sibling must not reuse them).",
              "hash": "1365e2764924"
            }
          },
          {
            "text": "A negative `maxAge` throws, so the facade checks `CallSpec.maxAge` first and returns `InvalidRequest` before anything is spent.",
            "ref": "plan.md#12-interfaces-with-sibling-units",
            "excerpt": {
              "ref": "plan.md#12-interfaces-with-sibling-units",
              "heading": "12. Interfaces with sibling units",
              "paragraph": "Names this unit takes (a sibling must not reuse them).",
              "hash": "1365e2764924"
            }
          }
        ],
        "steps": [],
        "tests": [],
        "risks": [],
        "source": "plan.md §4, §12",
        "link": "plan.md#12-interfaces-with-sibling-units"
      },
      "asBuilt": {
        "state": "pending",
        "summary": ""
      }
    }
  ],
  "impact": {
    "basis": "plan",
    "summary": "`webtools-http`, new and unreleased, gains its response cache: the `ResponseCache` port, implementing which needs the opt-in, with its values and the pass-through `ResponseCache.Disabled` in common code, and the final `CaffeineResponseCache` on the JVM, all Stable Core otherwise. Concurrent callers of one key cost one load, what is stored is bounded by weight, failures are never stored, and `close()` releases every waiter. No dependency of either variant changes, the three existing artifacts do not, and no version moves.",
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
        "system": "webtools-http-cache",
        "effect": "additive",
        "summary": "The main variants gain the port, its pass-through and its values in common code, and `CaffeineResponseCache` on the JVM, and the ABI dump grows by exactly these. No dependency of either variant changes, as Caffeine 3.3.1 was already a runtime dependency, and the test-fixtures variant does not change.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Consumers of `webtools-http` 1.0.0, once it is released",
        "kind": "consumer",
        "system": "webtools-http-cache",
        "effect": "additive",
        "summary": "One load for any number of concurrent callers of one key, a store bounded by weight with per-entry lifetimes, a per-call `maxAge`, failures never stored, a `close()` that ends every waiting call with `Closed(\"cache\")`, statistics with one meaning each, an explicit opt-out in common code, and a cache that the published `VirtualClock` can drive under test.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Consumers of `webtools-udp`, `webtools-scraping` and `webtools-browser`",
        "kind": "consumer",
        "system": null,
        "effect": "none",
        "summary": "Nothing changes: no source, build-script or test change, and no version moves.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Repository builds",
        "kind": "repository",
        "system": null,
        "effect": "additive",
        "summary": "No build file changes, and `./gradlew build` does more work: the new tests, Level 3 with one loopback server and Level 4c on real threads, the fixtures-publication check again, `apiCheck` against the regenerated dump and the `js` compile of the new common files.",
        "link": "consequences.md#build"
      },
      {
        "who": "The later units of #55: #67, #68, #69 and #70",
        "kind": "system",
        "system": "webtools-http-cache",
        "effect": "additive",
        "summary": "The call facades (#68) are the first callers and build on the contract the plan pins for them; the docs (#70) document the cache from its KDoc, README row and CHANGELOG entry; the call budget (#67) and the feeds (#69) take nothing from it.",
        "link": "consequences.md#4-sibling-units"
      },
      {
        "who": "#58, CI and repository practices",
        "kind": "repository",
        "system": null,
        "effect": "additive",
        "summary": "Its CI would be the first place these tests run on every change, and the Level 4c failure-identity check needs at least two cores to open the window it guards. This unit's IDE checks join those still open from #62 to #65.",
        "link": "consequences.md#6-neighbouring-issues"
      }
    ],
    "adaptation": [
      {
        "who": "Unit 9, the call facades (#68)",
        "system": "webtools-http-cache",
        "need": "required",
        "summary": "It builds each key from the endpoint's id and canonical parameters, never a tenant, a header or a credential; checks `CallSpec.maxAge` first, as a negative one throws; returns failures as values from its loader; builds `CaffeineResponseCache` from the client's clock and dispatcher and closes it first of the three components it owns; and returns `stats()` as `cacheStats()`.",
        "link": "plan.md#12-interfaces-with-sibling-units"
      },
      {
        "who": "An implementer of `ResponseCache`",
        "system": "webtools-http-cache",
        "need": "optional",
        "summary": "Implementing the port needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; calling it, passing it and using `ResponseCache.Disabled` or `CaffeineResponseCache` need nothing.",
        "link": "plan.md#r1-the-public-surface-and-its-tier"
      },
      {
        "who": "Existing code in the repository",
        "system": null,
        "need": "none",
        "summary": "No class caches HTTP responses, so nothing moves onto the cache, and `VirtualClockThreeClocksComponentTest` keeps its plain Caffeine cache to prove the clock apart from it.",
        "link": "consequences.md#3-adoption"
      }
    ],
    "api": [
      {
        "surface": "`ResponseCache`",
        "system": "webtools-http-cache",
        "change": "added",
        "tier": "experimental",
        "summary": "The port: `getOrLoad` with and without `maxAge`, `invalidate`, `invalidateAll`, `stats` and `close`. Implementing it is Experimental behind the opt-in; calling it, passing it and naming it in a signature are Stable Core.",
        "link": "plan.md#r2-the-port-and-its-contract"
      },
      {
        "surface": "`ResponseCache.Disabled`",
        "system": "webtools-http-cache",
        "change": "added",
        "tier": "stable-core",
        "summary": "The pass-through that runs every loader and stores and shares nothing, the only implementation constructible in common code.",
        "link": "plan.md#r3-responsecachedisabled"
      },
      {
        "surface": "`CacheKey`",
        "system": "webtools-http-cache",
        "change": "added",
        "tier": "stable-core",
        "summary": "An endpoint id and its parameters, sorted by name with a stable sort; `toString()` prints names only.",
        "link": "plan.md#r4-cachekeyv--any"
      },
      {
        "surface": "`CachePolicy`, `CachePolicy.Builder` and the factory `CachePolicy(ttl) { }`",
        "system": "webtools-http-cache",
        "change": "added",
        "tier": "stable-core",
        "summary": "A value's `ttl` and a confirmed miss's `missTtl`, 60 seconds by default.",
        "link": "plan.md#r5-cachepolicy"
      },
      {
        "surface": "`Lookup`, `Lookup.Found` and `Lookup.Missing`",
        "system": "webtools-http-cache",
        "change": "added",
        "tier": "stable-core",
        "summary": "A closed pair: a value with a weight of at least 1, or a confirmed miss.",
        "link": "plan.md#r6-lookupout-v--any"
      },
      {
        "surface": "`CacheStats`",
        "system": "webtools-http-cache",
        "change": "added",
        "tier": "stable-core",
        "summary": "Four counts: `hits` with joins included, `misses`, `evictions` for the size bound's removals only, and `failedLoads`.",
        "link": "plan.md#r7-cachestats"
      },
      {
        "surface": "`CaffeineResponseCache`, its builder and the factory `CaffeineResponseCache { }`",
        "system": "webtools-http-cache",
        "change": "added",
        "tier": "stable-core",
        "summary": "The supplied JVM cache, final and usable without the opt-in, over `timeSource`, `maxWeight`, `loadDispatcher` and `maintenanceExecutor`.",
        "link": "plan.md#r8-caffeineresponsecache--construction"
      }
    ],
    "conversion": [],
    "changes": [],
    "source": "consequences.md; plan.md §1, §7.16, §12",
    "link": "consequences.md#1-published-artifacts"
  },
  "tests": {
    "base": "826200e",
    "updated": "2026-11-03T18:33:16Z",
    "added": [
      {
        "class": "ResponseCacheConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/http/ResponseCacheConsumerGatingTest.kt",
        "tests": 3,
        "rows": [
          "port"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/http/CaffeineResponseCacheConsumerGatingTest.kt",
        "tests": 1,
        "rows": [
          "caffeine-default"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CacheKeyComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/CacheKeyComponentTest.kt",
        "tests": 5,
        "rows": [
          "cache-key",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CachePolicyComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/CachePolicyComponentTest.kt",
        "tests": 6,
        "rows": [
          "policy-lookup",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "LookupComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/LookupComponentTest.kt",
        "tests": 5,
        "rows": [
          "policy-lookup",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CacheStatsComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/CacheStatsComponentTest.kt",
        "tests": 4,
        "rows": [
          "policy-lookup",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "LoaderRunnerComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/LoaderRunnerComponentTest.kt",
        "tests": 8,
        "rows": [
          "disabled",
          "component-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-03T18:24:12Z"
        }
      },
      {
        "class": "DisabledResponseCacheComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/DisabledResponseCacheComponentTest.kt",
        "tests": 10,
        "rows": [
          "disabled",
          "component-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheHitsComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheHitsComponentTest.kt",
        "tests": 6,
        "rows": [
          "caffeine-default",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheMissTtlComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheMissTtlComponentTest.kt",
        "tests": 5,
        "rows": [
          "caffeine-default",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheZeroTtlComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheZeroTtlComponentTest.kt",
        "tests": 3,
        "rows": [
          "caffeine-default",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheMaxAgeComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheMaxAgeComponentTest.kt",
        "tests": 8,
        "rows": [
          "max-age",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheCoalescingComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheCoalescingComponentTest.kt",
        "tests": 5,
        "rows": [
          "shared-load",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheWaiterCancellationComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheWaiterCancellationComponentTest.kt",
        "tests": 6,
        "rows": [
          "shared-load",
          "component-tests",
          "close"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-03T18:24:12Z"
        }
      },
      {
        "class": "CaffeineResponseCacheFailureComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheFailureComponentTest.kt",
        "tests": 6,
        "rows": [
          "failure-value",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheFatalErrorComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheFatalErrorComponentTest.kt",
        "tests": 2,
        "rows": [
          "failure-value",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheLoaderCancellationComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheLoaderCancellationComponentTest.kt",
        "tests": 1,
        "rows": [
          "failure-value",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheCloseComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheCloseComponentTest.kt",
        "tests": 6,
        "rows": [
          "close",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-03T18:33:16Z"
        }
      },
      {
        "class": "CaffeineResponseCacheInvalidationComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheInvalidationComponentTest.kt",
        "tests": 5,
        "rows": [
          "caffeine-default",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-03T18:33:16Z"
        }
      },
      {
        "class": "CaffeineResponseCacheWeightComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheWeightComponentTest.kt",
        "tests": 8,
        "rows": [
          "caffeine-default",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheStatsComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheStatsComponentTest.kt",
        "tests": 5,
        "rows": [
          "caffeine-default",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheRejectedLoadComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheRejectedLoadComponentTest.kt",
        "tests": 2,
        "rows": [
          "failure-value",
          "component-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-03T18:24:12Z"
        }
      },
      {
        "class": "CaffeineResponseCacheConstructionComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheConstructionComponentTest.kt",
        "tests": 6,
        "rows": [
          "caffeine-default",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CaffeineResponseCacheLoggingComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheLoggingComponentTest.kt",
        "tests": 6,
        "rows": [
          "caffeine-default",
          "component-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-03T18:33:16Z"
        }
      },
      {
        "class": "CaffeineResponseCacheCaffeineLogComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheCaffeineLogComponentTest.kt",
        "tests": 2,
        "rows": [
          "failure-value",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-03T18:33:16Z"
        }
      },
      {
        "class": "CaffeineResponseCacheThreeClocksComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CaffeineResponseCacheThreeClocksComponentTest.kt",
        "tests": 2,
        "rows": [
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CacheKeyCanonicalFormDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/CacheKeyCanonicalFormDeterministicTest.kt",
        "tests": 9,
        "rows": [
          "cache-key",
          "deterministic-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "CacheEntryRulesDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/CacheEntryRulesDeterministicTest.kt",
        "tests": 10,
        "rows": [
          "caffeine-default",
          "max-age",
          "deterministic-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-03T18:24:12Z"
        }
      },
      {
        "class": "CaffeineResponseCacheLoopbackIntegrationTest",
        "module": "webtools-http",
        "testLevel": "3",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/CaffeineResponseCacheLoopbackIntegrationTest.kt",
        "tests": 4,
        "rows": [
          "integration-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-03T18:24:12Z"
        }
      },
      {
        "class": "CaffeineResponseCacheSizeBoundNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/CaffeineResponseCacheSizeBoundNonFunctionalTest.kt",
        "tests": 1,
        "rows": [
          "caffeine-default",
          "nonfunctional-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-03T18:24:12Z"
        }
      },
      {
        "class": "CaffeineResponseCacheFailureIdentityNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/CaffeineResponseCacheFailureIdentityNonFunctionalTest.kt",
        "tests": 1,
        "rows": [
          "failure-value",
          "nonfunctional-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-03T18:24:12Z"
        }
      },
      {
        "class": "CaffeineResponseCacheSecrecyNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/CaffeineResponseCacheSecrecyNonFunctionalTest.kt",
        "tests": 1,
        "rows": [
          "caffeine-default",
          "cache-key",
          "policy-lookup",
          "nonfunctional-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-03T18:24:12Z"
        }
      },
      {
        "class": "CaffeineResponseCacheCloseRaceNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/CaffeineResponseCacheCloseRaceNonFunctionalTest.kt",
        "tests": 1,
        "rows": [
          "close",
          "nonfunctional-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-11-03T18:24:12Z"
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
          "policy-lookup",
          "caffeine-default",
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
        }
      },
      {
        "class": "VirtualClockThreeClocksComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/VirtualClockThreeClocksComponentTest.kt",
        "tests": 2,
        "rows": [
          "component-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-03T04:48:46Z"
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
        "state": "OPEN"
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
      "label": "Issue #66",
      "path": "https://github.com/SpartanLaboratories/WebTools/issues/66",
      "note": "the unit as #55 filed it"
    },
    {
      "label": "#66 plan",
      "path": "plan.md",
      "note": "the implementer's spec: §1 the requirements, §5 the stages, §6 the design and §6.11 what it refines in #55's architecture, §9 the test plan, §12 the interfaces with the sibling units"
    },
    {
      "label": "#66 consequences",
      "path": "consequences.md",
      "note": "what the unit does to its surroundings and leaves behind: the published artifact, the blast radius, the sibling units, the follow-ups and the known gaps"
    },
    {
      "label": "#66 as-built record",
      "path": "final-implementation.md",
      "note": "what was built, the procedures run and every deviation from the plan; reconciled once implementation ends"
    },
    {
      "label": "#66 research",
      "path": "research/README.md",
      "note": "this unit's two questions, the verification and the summary; each question links its findings file"
    },
    {
      "label": "#55 research",
      "path": "../research/README.md",
      "note": "the questions, verification and summary the Gap column rests on; question 6 bears on this unit"
    },
    {
      "label": "#55 response cache findings",
      "path": "../research/response-cache.md",
      "note": "F1 to F14, the measured failure, cancellation and `maxAge` behaviour, and the considerations behind the options"
    },
    {
      "label": "#55 architecture",
      "path": "../architecture.md",
      "note": "§6.4 the response cache, §7.2 the call path, §9 the tiers, §10 alternatives, §11 unit 7, §12 the shared load's first caller"
    },
    {
      "label": "#55 consequences",
      "path": "../consequences.md",
      "note": "§8 what the later units build on: units 1 to 6 as built"
    },
    {
      "label": "#63 impact",
      "path": "../63-test-support/impact.md",
      "note": "§4 what this unit passes `CaffeineResponseCache` from the fixtures, and the proof it re-runs"
    },
    {
      "label": "#62 impact",
      "path": "../62-http-core/impact.md",
      "note": "§4 what the common core gives this unit"
    },
    {
      "label": "#55 tracking board",
      "path": "../dashboard.html",
      "note": "the eleven units in landing order"
    }
  ],
  "notes": {
    "paths": "`common/` abbreviates `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/` and `jvm/` its `jvmMain`, as #55's architecture does. Line references marked baseline are to `e8f0a88`.",
    "origin": "This unit's architecture happened in #55's run, so the Gap and Considered columns were written at sized from #55's research README, its response cache findings and its architecture, with what units 3 and 4 as built leave it; the unit's own research adds its evidence to the Gap column, and its plan adds to Considered and writes the Verdict at plan acceptance.",
    "corrections": [
      {
        "what": "The `failure-value` row's Gap said Caffeine logs a `WARNING` for every exceptionally completed future. Its `handleCompletion` leaves out a `CancellationException` and a `TimeoutException` (#55's research/response-cache.md F2, this unit's research/caffeine-mechanics.md F10). Corrected in place; the gap stands.",
        "when": "researched, 2026-11-03"
      },
      {
        "what": "The `failure-value` row's location said `jvm/` only and the `component-tests` row's `jvmTest` only: a loader's own cancellation is converted in the shared runner in `common/`, and the value types' Level 2 classes live in `commonTest` (plan.md §6.2, §9). Corrected in place.",
        "when": "plan accepted, 2026-11-03"
      },
      {
        "what": "Implementation read to qa pass 1's verdict at `2026-11-03T04:48:46Z` and qa · 1st pass read no time, as the pass's start was never signalled. Implementation ended and the pass started at `2026-11-03T04:23:52Z`, the time of the qa step's own progress note. Corrected in place: the implementation visit's end and the pass's start.",
        "when": "Timeline corrected, 2026-11-03"
      },
      {
        "what": "Five As built statements referred to sections the reconciled `final-implementation.md` no longer has (`#procedures-run`, `#deviations`, `#runs-stage-3`); they now refer to `#4-measurements` and `#2-deviations`. The `component-tests` row said 80 tests and the `deterministic-tests` row 8, where the final run of record counts 84 and 10, and the `nonfunctional-tests` row called P4's result an open point, settled as a known gap in `consequences.md` §8; the `policy-lookup` row said the builder's KDoc keeps the place for a later `staleFor`, which the reconciled KDoc no longer names. Corrected in place.",
        "when": "reconciled, 2026-11-03"
      }
    ]
  },
  "issueBoards": {
    "47": "../47-java-23-bytecode/dashboard.html",
    "61": "../61-build-logic/dashboard.html",
    "62": "../62-http-core/dashboard.html",
    "63": "../63-test-support/dashboard.html",
    "64": "../64-url-policy/dashboard.html",
    "65": "../65-okhttp-transport/dashboard.html"
  },
  "links": {
    "workBoard": "../../index.html",
    "repoBoard": "../../../dashboards/index.html",
    "boards": {
      "webtools-http-cache": "../../../dashboards/webtools-http/index.html"
    }
  }
}
);
