/* plan.js: plan.json as a script, written by plan/scripts/embed.js beside the board for its live view (plan/RULES.md §1). Do not edit: edit plan.json and re-embed. */
window.dashboardLive&&window.dashboardLive("plan",
{
  "schema": "plan-board/1",
  "repo": "SpartanLaboratories/WebTools",
  "issue": 68,
  "slug": "68-call-facades",
  "folder": "docs/plans/55-http-client-stack/68-call-facades",
  "kind": "issue",
  "title": "Call facades",
  "issueTitle": "webtools-http: `UpstreamClient` and `UrlFetcher` composing cache, budget and transport",
  "function": "Nothing in `webtools-http` composes its parts yet: the transport (#65), the response cache (#66) and the call budget (#67) each stand alone, and the order they run in decides what a policy refusal, a throttle or a decode failure costs a caller (#55 architecture §6.6, §7.2). This run adds the two facades that compose them. `UpstreamClient` serves an `Endpoint<T>` of one trusted upstream through the cache, then the budget inside the cache's loader, then the transport, so a hit costs nothing and coalesced callers share one permit; `UrlFetcher` serves administrator-supplied URLs with a policy pre-check that costs nothing, and no cache. With them come `CallSpec` and `FetchSpec`, the configs, the common factories, and the JVM owning factories that build, size and close what they own; Level 2 component tests on the fake transport, a Level 3 integration call over loopback and the README how-to. Unit 9 of #55: the call facades subsystem of `webtools-http`, additive, no version moved; the feeds module is unit 10's.",
  "status": "building",
  "sized": "2026-11-05T23:56:38Z",
  "parent": {
    "issue": 55,
    "board": "../dashboard.html"
  },
  "fixOf": null,
  "branch": "feat/issue-68-call-facades",
  "baseline": {
    "ref": "master",
    "sha": "5f221ce",
    "date": "2026-11-05"
  },
  "asBuilt": null,
  "systems": [
    {
      "slug": "webtools-http-facades",
      "name": "call facades",
      "slot": 5,
      "top": "webtools-http",
      "status": "built",
      "board": "docs/dashboards/webtools-http-facades/index.html"
    }
  ],
  "wip": {
    "step": "document",
    "since": "2026-11-06T03:09:31Z",
    "updated": "2026-11-06T04:17:26Z",
    "detail": "Document: 7 of 7 tasks done; every task done.",
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
        "id": "stage-1",
        "name": "The values: Endpoint, CallSpec, FetchSpec",
        "state": "done",
        "rows": [
          "endpoint",
          "call-spec",
          "component-tests",
          "deterministic-tests",
          "release"
        ]
      },
      {
        "id": "stage-2",
        "name": "The shared call internals and UrlFetcher",
        "state": "done",
        "rows": [
          "call-spec",
          "upstream-client",
          "permit-settlement",
          "rate-limited",
          "url-fetcher",
          "factories",
          "lifecycle",
          "logging",
          "component-tests",
          "deterministic-tests",
          "release"
        ]
      },
      {
        "id": "stage-3",
        "name": "UpstreamClient",
        "state": "done",
        "rows": [
          "upstream-client",
          "permit-settlement",
          "rate-limited",
          "url-fetcher",
          "factories",
          "lifecycle",
          "logging",
          "component-tests",
          "release"
        ]
      },
      {
        "id": "stage-4",
        "name": "The JVM owning factories, transport sizing and LoopbackFetchers",
        "state": "done",
        "rows": [
          "owning-factories",
          "lifecycle",
          "loopback-fetchers",
          "gating-tests",
          "component-tests",
          "integration-tests",
          "deterministic-tests",
          "release"
        ]
      },
      {
        "id": "stage-5",
        "name": "Non-functional suites, README how-to and CHANGELOG",
        "state": "done",
        "rows": [
          "nonfunctional-tests",
          "readme",
          "release"
        ]
      }
    ],
    "remediation": {
      "pass": 1,
      "route": "on-the-spot",
      "steps": [
        {
          "name": "Record the wording and test departures in the as-built record",
          "state": "done"
        },
        {
          "name": "Cleanup on the failing path in three test classes",
          "state": "done"
        },
        {
          "name": "Stronger and added tests in existing classes",
          "state": "done"
        },
        {
          "name": "Close-race and secrecy suites for the facades at the non-functional level",
          "state": "done"
        }
      ]
    },
    "open": [],
    "event": {
      "name": "document",
      "at": "2026-11-06T04:17:26Z",
      "note": "7 of 7 tasks done"
    },
    "history": {
      "sizing": [
        {
          "since": null,
          "ended": "2026-11-05T23:56:38Z",
          "endedBy": "sized",
          "to": "plan"
        }
      ],
      "plan": [
        {
          "since": "2026-11-05T23:56:38Z",
          "ended": "2026-11-06T00:13:19Z",
          "endedBy": "research-planning",
          "to": "research",
          "event": {
            "name": "sized",
            "at": "2026-11-05T23:56:38Z"
          },
          "detail": "Unit 9 of the HTTP client stack: Endpoint, CallSpec, FetchSpec, UpstreamClient and UrlFetcher with their configs and factories, composing the landed cache, budget and transport. Additive public API in webtools-http only; no version moves. Next: the detailed plan against the code as it is at 5f221ce.",
          "plan": {
            "state": "re-pass",
            "from": "parent",
            "since": "2026-11-05T23:56:38Z"
          }
        },
        {
          "since": "2026-11-06T01:10:26Z",
          "ended": "2026-11-06T01:10:35Z",
          "endedBy": "plan-accepted",
          "to": "branch",
          "event": {
            "name": "researched",
            "at": "2026-11-06T01:10:26Z"
          },
          "detail": "Three questions answered and verified: how Endpoint is built (a same-named generic factory over an internal constructor, measured by a compile probe on jvm and js), what delay a rate-limited failure reports (the longer of the server's Retry-After and the budget's pause), and the default wait cap (5 seconds). 35 of 35 load-bearing claims hold, 4 of them only in part with no change to a recommendation.",
          "plan": {
            "state": "writing",
            "since": "2026-11-06T01:10:26Z"
          }
        }
      ],
      "research": [
        {
          "since": "2026-11-06T00:13:19Z",
          "ended": "2026-11-06T01:10:26Z",
          "endedBy": "researched",
          "to": "plan",
          "event": {
            "name": "verification-started",
            "at": "2026-11-06T00:27:03Z"
          },
          "detail": "Research: verifying 3 findings.",
          "research": {
            "phase": "complete",
            "drafted": 0,
            "since": {
              "planning": "2026-11-06T00:13:19Z",
              "research": "2026-11-06T00:14:06Z",
              "verification": "2026-11-06T00:27:03Z",
              "complete": "2026-11-06T01:10:26Z"
            }
          },
          "questions": [
            {
              "question": "endpoint-factory-resolution",
              "rows": [
                "endpoint"
              ],
              "findings": "research/endpoint-factory-resolution.md",
              "state": "complete",
              "started": "2026-11-06T00:14:06Z",
              "returned": "2026-11-06T00:26:41Z"
            },
            {
              "question": "rate-limited-retry-after",
              "rows": [
                "rate-limited"
              ],
              "findings": "research/rate-limited-retry-after.md",
              "state": "complete",
              "started": "2026-11-06T00:14:06Z",
              "returned": "2026-11-06T00:22:40Z"
            },
            {
              "question": "default-wait-cap",
              "rows": [
                "factories",
                "call-spec"
              ],
              "findings": "research/default-wait-cap.md",
              "state": "complete",
              "started": "2026-11-06T00:14:06Z",
              "returned": "2026-11-06T00:22:40Z"
            }
          ]
        }
      ],
      "branch": [
        {
          "since": "2026-11-06T01:10:35Z",
          "ended": "2026-11-06T01:10:47Z",
          "endedBy": "branch-cut",
          "to": "implementation",
          "event": {
            "name": "plan-accepted",
            "at": "2026-11-06T01:10:35Z"
          },
          "detail": "Two final classes in common code over pure functions. UpstreamClient runs the policy check, then the cache, then a permit taken inside the cache's loader, then the transport. UrlFetcher runs the policy check, the budget and the transport, and returns every status as a response except a throttle. The JVM owning factories build the budget, the transport sized to the budget's burst, and the cache, and close them cache, budget, transport. Permit after a transport failure: only an invalid request is refunded; every other failure stays spent. A rate-limited failure reports the longer of the server's Retry-After and the budget's pause. 34 new test classes with 214 tests are planned; no version moves.",
          "branch": "feat/issue-68-call-facades"
        }
      ],
      "implementation": [
        {
          "since": "2026-11-06T01:10:47Z",
          "ended": "2026-11-06T02:20:48Z",
          "endedBy": "qa-pass",
          "to": "qa",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-06T02:20:20Z",
            "note": "stage-5 reported done; qa next"
          },
          "detail": "Stage 5 built with no deviation: the two non-functional suites (concurrent calls and a close racing calls), the README how-to and the CHANGELOG entries. The manual pass read every new type's KDoc and ran the README's snippets against a loopback server: all compile and behave as the section says. The module's final check passes: 1,431 tests in 261 classes, none failed or skipped, which is the planned 1,429 plus the two tests of D5. The module's diff touches webtools-http, the README, the CHANGELOG and the plan folder only.",
          "stages": [
            {
              "id": "stage-1",
              "name": "The values: Endpoint, CallSpec, FetchSpec",
              "state": "done",
              "rows": [
                "endpoint",
                "call-spec",
                "component-tests",
                "deterministic-tests",
                "release"
              ]
            },
            {
              "id": "stage-2",
              "name": "The shared call internals and UrlFetcher",
              "state": "done",
              "rows": [
                "call-spec",
                "upstream-client",
                "permit-settlement",
                "rate-limited",
                "url-fetcher",
                "factories",
                "lifecycle",
                "logging",
                "component-tests",
                "deterministic-tests",
                "release"
              ]
            },
            {
              "id": "stage-3",
              "name": "UpstreamClient",
              "state": "done",
              "rows": [
                "upstream-client",
                "permit-settlement",
                "rate-limited",
                "url-fetcher",
                "factories",
                "lifecycle",
                "logging",
                "component-tests",
                "release"
              ]
            },
            {
              "id": "stage-4",
              "name": "The JVM owning factories, transport sizing and LoopbackFetchers",
              "state": "done",
              "rows": [
                "owning-factories",
                "lifecycle",
                "loopback-fetchers",
                "gating-tests",
                "component-tests",
                "integration-tests",
                "deterministic-tests",
                "release"
              ]
            },
            {
              "id": "stage-5",
              "name": "Non-functional suites, README how-to and CHANGELOG",
              "state": "done",
              "rows": [
                "nonfunctional-tests",
                "readme",
                "release"
              ]
            }
          ]
        }
      ],
      "qa": [
        {
          "since": "2026-11-06T02:20:48Z",
          "ended": "2026-11-06T02:52:19Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-06T02:20:48Z",
            "note": "Quality Review 1: running"
          },
          "detail": "Quality Review 1 running.",
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-06",
              "at": "2026-11-06T02:52:19Z",
              "findings": {
                "test": 16,
                "code": 2
              },
              "severity": {
                "minor": 18,
                "other": 0
              },
              "route": "on-the-spot"
            }
          ]
        }
      ],
      "remediation": [
        {
          "since": "2026-11-06T02:52:19Z",
          "ended": "2026-11-06T03:09:31Z",
          "endedBy": "document",
          "to": "document",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-06T03:09:04Z",
            "note": "4 of 4 remediation sub-steps done"
          },
          "detail": "All 18 fixed with no change to production code, the fixtures, the ABI dump, the README or the CHANGELOG. New deviations: D6 the README names the client's signal reader where the planned text named the budget's; D7 the CHANGELOG says which policy each owning fetcher factory builds its transport under; D8 the decode-failure test asserts two debug lines, one per load; D9 tests and assertions beyond the plan's list in thirteen classes (twelve more tests); D10 a third non-functional class, a close racing fetches on the fetcher; D11 a fourth non-functional class, secrecy across both facades. The module's check passes: 1,447 tests in 263 classes, none failed or skipped.",
          "remediation": {
            "pass": 1,
            "route": "on-the-spot",
            "steps": [
              {
                "name": "Record the wording and test departures in the as-built record",
                "state": "done"
              },
              {
                "name": "Cleanup on the failing path in three test classes",
                "state": "done"
              },
              {
                "name": "Stronger and added tests in existing classes",
                "state": "done"
              },
              {
                "name": "Close-race and secrecy suites for the facades at the non-functional level",
                "state": "done"
              }
            ]
          },
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-06",
              "at": "2026-11-06T02:52:19Z",
              "findings": {
                "test": 16,
                "code": 2
              },
              "severity": {
                "minor": 18,
                "other": 0
              },
              "route": "on-the-spot",
              "note": "No test failed and no logic defect was found: the permit rules, the retry delay, the cache interplay, the lifecycle, the owning factories, secrecy and the ABI read clean, and the five recorded deviations are accurate. Eighteen minor findings, being fixed in place with no second pass. Sixteen test findings, second-order, in the test rows and in `logging`, `url-fetcher`, `owning-factories` and `loopback-fetchers`: log branches and check orderings the plan did not list; the public owning fetcher factory never carrying a real request; cleanup on a failing path missing in three test classes; message texts pinned only against their own constants; and no close-race or secrecy suite for the fetcher at Level 4c non-functional. Two code findings, in `readme` and `release`: two sentences of the README and the CHANGELOG that are more accurate than the planned text, to be recorded as deviations. Suite: 1,431 passed, 0 failed, 0 skipped in webtools-http's 261 classes, every test level run; apiCheck passed. webtools-udp, webtools-scraping and webtools-browser are untouched and were not run."
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
        "date": "2026-11-06",
        "at": "2026-11-06T02:52:19Z",
        "findings": {
          "test": 16,
          "code": 2
        },
        "severity": {
          "minor": 18,
          "other": 0
        },
        "route": "on-the-spot",
        "note": "No test failed and no logic defect was found: the permit rules, the retry delay, the cache interplay, the lifecycle, the owning factories, secrecy and the ABI read clean, and the five recorded deviations are accurate. Eighteen minor findings, being fixed in place with no second pass. Sixteen test findings, second-order, in the test rows and in `logging`, `url-fetcher`, `owning-factories` and `loopback-fetchers`: log branches and check orderings the plan did not list; the public owning fetcher factory never carrying a real request; cleanup on a failing path missing in three test classes; message texts pinned only against their own constants; and no close-race or secrecy suite for the fetcher at Level 4c non-functional. Two code findings, in `readme` and `release`: two sentences of the README and the CHANGELOG that are more accurate than the planned text, to be recorded as deviations. Suite: 1,431 passed, 0 failed, 0 skipped in webtools-http's 261 classes, every test level run; apiCheck passed. webtools-udp, webtools-scraping and webtools-browser are untouched and were not run."
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
          "state": "done"
        },
        {
          "id": "reconcile-documentation",
          "name": "Reconcile documentation",
          "state": "done"
        },
        {
          "id": "dokka-check",
          "name": "Dokka check",
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
  },
  "milestones": [
    {
      "column": "gap",
      "label": "Research verified, in #55's run",
      "date": "2026-10-21",
      "ref": "../research/README.md",
      "note": "#55's questions and findings; question 9 and `api-shape-and-failures.md` bear on this unit. Written on this board at sized from the research README, architecture §6.6, §7.2 and this unit's row in §11, with what units 3 to 8 as built leave it (#55 consequences §8, and the sibling contracts of #65, #66 and #67)"
    },
    {
      "column": "gap",
      "label": "Research verified, in this unit's run",
      "date": "2026-11-06",
      "ref": "research/README.md",
      "note": "three questions and three findings files: building `Endpoint<T>`, what a rate-limited failure reports, and the default wait cap. 35 load-bearing claims re-opened at source, 33 of these findings and 2 of #55's transport findings: all hold, 4 of them in part, none fails and none is unclear, and no recommendation changes. The Gap column, written at sized from #55's research and architecture, stands with no correction; these findings add their evidence to 5 rows, `endpoint`, `rate-limited` and `factories`, and `permit-settlement` and `owning-factories` from the transport's own table and #55's transport findings. Where the sources disagree, the probe on Kotlin 2.2.0 shows explicit type arguments reaching a companion `invoke`, which #55's research held they could not; it removes an objection without changing the choice"
    },
    {
      "column": "considered",
      "label": "Architecture signed off, in #55's run",
      "date": "2026-10-21",
      "ref": "../architecture.md",
      "note": "the options #55's architecture weighed for this unit: §6.6, §9, §10 and §13, with the API-shape and call budget findings' considerations behind them; this unit's own plan adds to them at plan acceptance and settles the three points left open (the permit after a sent-then-failed request, the delay `RateLimited` reports, and the generic `Endpoint<T>` factory)"
    },
    {
      "column": "considered",
      "label": "The unit's own plan adds to the options",
      "date": "2026-11-06",
      "ref": "plan.md",
      "note": "the options plan.md and this unit's research weigh beside the architecture's. It settles the two points left open, each taken in part: the permit after a transport failure is released for `InvalidRequest` only, every other failure leaving it spent, as a failure carries no hop number; and `RateLimited.retryAfter` is the longer of the server's `Retry-After` and the budget's pause. The generic `Endpoint<T>` factory is measured to resolve from another module, with the companion `invoke`, a public constructor and `out T` declined. The plan also adds four parts #55's decomposition does not name, so the board gains four rows, `loopback-fetchers`, `gating-tests`, `deterministic-tests` and `nonfunctional-tests`, each with its Gap and Considered written here"
    },
    {
      "column": "verdict",
      "label": "Plan accepted",
      "date": "2026-11-06",
      "ref": "plan.md",
      "note": "five implementation stages on `feat/issue-68-call-facades`, then the README and CHANGELOG and the as-built record, in seven commits. The facades are a composition of the landed ports, every new type final and Stable Core with no seam added, and §6.11 makes #55's architecture §6.6 concrete in twelve refinements. 34 new test classes, 214 tests, from Level 1 gating to Level 4c non-functional. No version moves"
    }
  ],
  "trail": [
    {
      "date": "2026-11-05",
      "column": "gap",
      "label": "Sized; the plan starts"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research planning: 0 questions drafted"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research started: `endpoint-factory-resolution`, `rate-limited-retry-after`, `default-wait-cap`"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research returned: `rate-limited-retry-after`",
      "ref": "research/rate-limited-retry-after.md"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research returned: `default-wait-cap`",
      "ref": "research/default-wait-cap.md"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research returned: `endpoint-factory-resolution`",
      "ref": "research/endpoint-factory-resolution.md"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Verification started: 3 findings"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research verified; the plan next",
      "ref": "research/README.md"
    },
    {
      "date": "2026-11-06",
      "column": "verdict",
      "label": "Plan accepted",
      "ref": "plan.md"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Branch `feat/issue-68-call-facades` cut; implementation starts at stage-1"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "stage-1 done; stage-2 current: The shared call internals and UrlFetcher"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "stage-2 done; stage-3 current: UpstreamClient"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "stage-3 done; stage-4 current: The JVM owning factories, transport sizing and LoopbackFetchers"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "stage-4 done; stage-5 current: Non-functional suites, README how-to and CHANGELOG"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Implementation done (5 stages); qa next"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Quality Review 1: running"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Quality Review 1: changes requested (16 test, 2 code, 18 minor)"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Remediation for Quality Review 1: 4 sub-steps done; every sub-step done"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Document: Documentation review current"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Document: Documentation review, Reconcile plan in parallel"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Document: Reconcile documentation, Reconcile plan in parallel"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Document: Reconcile documentation current"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Document: State boards current"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Document: Document-landing commit current"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Document: every task done"
    }
  ],
  "rows": [
    {
      "id": "endpoint",
      "group": "Code",
      "name": "`Endpoint<T>` and its builder",
      "system": "webtools-http-facades",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "`webtools-http` has no way to describe a call to an upstream: nothing names an endpoint, its lifetimes, its cost and lane, or which statuses are a confirmed miss. The cache stores decoded values under `CacheKey(id, params)`, which cannot see a decoder, so the decode has nowhere safe to live.",
        "evidence": [
          "common/CacheKey.kt:16 — the key is an id and parameters, with no decoder in it (baseline)",
          "common/Lookup.kt — `Found` and `Missing`, the shape a call returns (baseline)",
          "../research/api-shape-and-failures.md F10 — cache identity makes the decoder part of the endpoint",
          "../architecture.md §12 — the generic `Endpoint<T>` factory-over-class resolution is argued from non-generic precedents and checked in this unit's plan",
          "research/endpoint-factory-resolution.md F1, F3 — measured on Kotlin 2.2.0 with a separate consumer module: every consumer form binds the generic factory, and an invisible constructor never wins against it",
          "research/endpoint-factory-resolution.md F9 — the ABI dump records the factory and its `$default`, no `<init>`, and pins neither the `T : Any` bound nor a variance"
        ],
        "source": "../research/README.md § Public API shape and failure model; ../architecture.md §6.6; research/README.md § Building `Endpoint<T>`",
        "link": "../architecture.md#66-call-facades"
      },
      "options": [
        {
          "id": "with-decode",
          "name": "A final `Endpoint<T>` built by `Endpoint(id, decode, configure)` over an `internal` constructor, the decode travelling with it; a builder for `name`, `ttl`, `missTtl`, `cost`, `lane` and `misses(statuses)`",
          "verdict": "accepted",
          "why": "The cache key cannot see the decoder, so binding it to the endpoint keeps a shared entry consistent. Stable Core. The generic factory's resolution is this unit's plan to check (§12).",
          "source": "../architecture.md §6.6, §9; ../research/api-shape-and-failures.md F10, Considerations 4",
          "link": "../architecture.md#66-call-facades"
        },
        {
          "id": "decoder-per-call",
          "name": "A decoder per call",
          "verdict": "declined",
          "why": "Two calls with the same key and different decoders would share whatever the first decoder produced; the contract would become \"pass the same decoder every time\".",
          "source": "../architecture.md §10; ../research/api-shape-and-failures.md Considerations 4",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "companion-invoke",
          "name": "A companion `operator fun invoke` in place of the top-level factory",
          "verdict": "declined",
          "why": "It resolves and infers too, explicit type arguments included, but adds a `Companion` class and field to the ABI and breaks the module's uniform shape.",
          "source": "research/README.md § Building `Endpoint<T>`; research/endpoint-factory-resolution.md F4, F5",
          "link": "research/README.md#building-endpointt"
        },
        {
          "id": "public-constructor",
          "name": "A public constructor `Endpoint(id, decode)`, the optional settings on a separate builder",
          "verdict": "declined",
          "why": "The clearest diagnostics, but its parameter list becomes binary API, so a later setting is a break.",
          "source": "research/README.md § Building `Endpoint<T>`",
          "link": "research/README.md#building-endpointt"
        },
        {
          "id": "covariant",
          "name": "`Endpoint<out T>` in 1.0.0",
          "verdict": "declined",
          "why": "Nothing needs it yet: adding `out` later is source- and binary-compatible, removing it is not.",
          "source": "plan.md §4; research/endpoint-factory-resolution.md F10",
          "link": "plan.md#4-non-goals"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`Endpoint<T : Any>` was built as a final class with an `internal` eight-parameter constructor, public read-only `id`, `name`, `ttl`, `missTtl`, `cost`, `lane` and a copied `misses` set, and an `internal` decoder fixed at construction; it overrides no `equals`, so equality is identity.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape, with the deviations of §2. Paths are under `webtools-http/src/`.",
              "hash": "79fd0ee5c5bc"
            }
          },
          {
            "text": "The top-level `Endpoint(id, decode, configure)` builds it: procedure P1 found every consumer form in `EndpointComponentTest` binding the factory and typed `Endpoint<String>`, and only the eight-argument call binding the constructor.",
            "state": "as-planned",
            "ref": "final-implementation.md#p1-the-endpoint-resolution-and-abi-measurement",
            "excerpt": {
              "ref": "final-implementation.md#p1-the-endpoint-resolution-and-abi-measurement",
              "heading": "P1. The `Endpoint` resolution and ABI measurement",
              "paragraph": "`grep -n \"Endpoint\" webtools-http/api/webtools-http.api` after `apiDump` shows `EndpointKt` with `Endpoint` and `Endpoint$default`, and the class `Endpoint` with its getters and `toString` and no `<init>`:",
              "hash": "460b36a8a881"
            }
          },
          {
            "text": "`Endpoint.Builder` carries `@WebToolsDsl` and defaults `name` to the id, `ttl` to zero, `missTtl` to the smaller of `ttl` and the cache's 60 s default until one is set, `cost` to 1 and `lane` to `Interactive`; `misses(…)` accumulates.",
            "state": "as-planned"
          },
          {
            "text": "Building refuses a blank id or name, a negative `ttl` or `missTtl`, a `cost` below 1 and a miss status outside 100 to 599 or equal to 429 with a constant message naming the property; `EndpointDeterministicTest` holds that no message echoes the value.",
            "state": "as-planned"
          },
          {
            "text": "The ABI dump shows `EndpointKt` with the factory and its default, and no `<init>` for `Endpoint`, `Endpoint$Builder`, `CallSpec` or `FetchSpec`; the KDoc states that `T` is invariant, and the signature carries the bound; no test can hold either, so a change to them would pass `apiCheck` and every test, a known gap.",
            "state": "as-planned",
            "ref": "final-implementation.md#p1-the-endpoint-resolution-and-abi-measurement",
            "excerpt": {
              "ref": "final-implementation.md#p1-the-endpoint-resolution-and-abi-measurement",
              "heading": "P1. The `Endpoint` resolution and ABI measurement",
              "paragraph": "`grep -n \"Endpoint\" webtools-http/api/webtools-http.api` after `apiDump` shows `EndpointKt` with `Endpoint` and `Endpoint$default`, and the class `Endpoint` with its getters and `toString` and no `<init>`:",
              "hash": "460b36a8a881"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One system, the call facades of `webtools-http`: the module's factory over an `internal` constructor made generic, measured to resolve from another module, so a consumer meets one shape everywhere.",
        "statements": [
          {
            "text": "`Endpoint<T : Any>` is a final class, invariant in `T`, with an `internal` eight-parameter constructor and public read-only `id`, `name`, `ttl`, `missTtl`, `cost`, `lane` and `misses`; the decoder is `internal` and fixed at construction, and equality is identity.",
            "ref": "plan.md#r2-endpoint",
            "excerpt": {
              "ref": "plan.md#r2-endpoint",
              "heading": "R2. `Endpoint`",
              "paragraph": "`Endpoint<T : Any>` is a final class with an `internal` constructor `(id, decode, name, ttl, missTtl, cost, lane, misses)` and public read-only properties `id`, `name`, `ttl`, `missTtl`, `cost`, `lane` and `misses: Set<Int>`. The decoder is an `internal val decode: (HttpResponse) -> Result<T>`. Equality is identity. `toString()` prints the id, name, ttl, missTtl, cost, the lane's name and the misses, never the decoder. · It is built only by `fun <T : Any> Endpoint(id: String, decode: (HttpResponse) -> Result<T>, configure: Endpoint.Builder.() -> Unit = {}): Endpoint<T>`. Every consumer form …",
              "hash": "e1eff45cebb3"
            }
          },
          {
            "text": "It is built only by the top-level `Endpoint(id, decode, configure)`, which every consumer form binds, `T` inferred from the decoder; a decoder of `Result<Hero?>` is refused by the bound.",
            "ref": "plan.md#r2-endpoint",
            "excerpt": {
              "ref": "plan.md#r2-endpoint",
              "heading": "R2. `Endpoint`",
              "paragraph": "`Endpoint<T : Any>` is a final class with an `internal` constructor `(id, decode, name, ttl, missTtl, cost, lane, misses)` and public read-only properties `id`, `name`, `ttl`, `missTtl`, `cost`, `lane` and `misses: Set<Int>`. The decoder is an `internal val decode: (HttpResponse) -> Result<T>`. Equality is identity. `toString()` prints the id, name, ttl, missTtl, cost, the lane's name and the misses, never the decoder. · It is built only by `fun <T : Any> Endpoint(id: String, decode: (HttpResponse) -> Result<T>, configure: Endpoint.Builder.() -> Unit = {}): Endpoint<T>`. Every consumer form …",
              "hash": "e1eff45cebb3"
            }
          },
          {
            "text": "The non-generic `@WebToolsDsl` builder defaults `name` to the id, `ttl` to zero, `missTtl` to the smaller of `ttl` and 60 s read live, `cost` to 1 and `lane` to `Interactive`; `misses(…)` accumulates.",
            "ref": "plan.md#r2-endpoint",
            "excerpt": {
              "ref": "plan.md#r2-endpoint",
              "heading": "R2. `Endpoint`",
              "paragraph": "`Endpoint<T : Any>` is a final class with an `internal` constructor `(id, decode, name, ttl, missTtl, cost, lane, misses)` and public read-only properties `id`, `name`, `ttl`, `missTtl`, `cost`, `lane` and `misses: Set<Int>`. The decoder is an `internal val decode: (HttpResponse) -> Result<T>`. Equality is identity. `toString()` prints the id, name, ttl, missTtl, cost, the lane's name and the misses, never the decoder. · It is built only by `fun <T : Any> Endpoint(id: String, decode: (HttpResponse) -> Result<T>, configure: Endpoint.Builder.() -> Unit = {}): Endpoint<T>`. Every consumer form …",
              "hash": "e1eff45cebb3"
            }
          },
          {
            "text": "Building refuses a blank id or name, a negative lifetime, a `cost` below 1 and a miss status outside 100 to 599 or equal to `429`, with a constant message that names the property and never a value.",
            "ref": "plan.md#r2-endpoint",
            "excerpt": {
              "ref": "plan.md#r2-endpoint",
              "heading": "R2. `Endpoint`",
              "paragraph": "`Endpoint<T : Any>` is a final class with an `internal` constructor `(id, decode, name, ttl, missTtl, cost, lane, misses)` and public read-only properties `id`, `name`, `ttl`, `missTtl`, `cost`, `lane` and `misses: Set<Int>`. The decoder is an `internal val decode: (HttpResponse) -> Result<T>`. Equality is identity. `toString()` prints the id, name, ttl, missTtl, cost, the lane's name and the misses, never the decoder. · It is built only by `fun <T : Any> Endpoint(id: String, decode: (HttpResponse) -> Result<T>, configure: Endpoint.Builder.() -> Unit = {}): Endpoint<T>`. Every consumer form …",
              "hash": "e1eff45cebb3"
            }
          },
          {
            "text": "Procedure P1 measures the resolution again in this module and the ABI dump shows `EndpointKt` and no `<init>`; the KDoc carries the bound and the invariance the dump cannot pin.",
            "ref": "plan.md#93-procedures-not-committed-tests",
            "excerpt": {
              "ref": "plan.md#93-procedures-not-committed-tests",
              "heading": "9.3 Procedures (not committed tests)",
              "paragraph": "Runs (from the repository root; on Windows `gradlew.bat`). Only `webtools-http`'s own tasks and the check module's `js` compile; no other module's suite, before or after:",
              "hash": "3abe87b1459f"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Write `Endpoint`, its builder and factory with the facades' constant texts, then measure the factory's resolution and ABI.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/Endpoint.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/FacadeMessages.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/EndpointComponentTest.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/EndpointDeterministicTest.kt"
            ],
            "check": "`:webtools-http:componentTest`, `deterministicTest`; Procedures P1 and P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `EndpointComponentTest`, each consumer form of the factory; Level 4a (deterministic) `EndpointDeterministicTest`, the validation and `missTtl` tables; Level 1 (gating) `CallFacadesConsumerGatingTest`, built with no opt-in"
        ],
        "risks": [
          "A consumer's miscall is reported against the `internal` constructor, not as the misplaced decoder; the KDoc and the README show the call's shape (§9.2)",
          "Two endpoints sharing a cache with one id and different value types share a key; the KDoc requires one id per endpoint (§10)"
        ],
        "source": "plan.md R2, §6.1, §6.11",
        "link": "plan.md#r2-endpoint"
      }
    },
    {
      "id": "call-spec",
      "group": "Code",
      "name": "`CallSpec` and `FetchSpec`: the per-call settings and the request they build",
      "system": "webtools-http-facades",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing turns a call's path segments, query pairs, headers, method, body, lane, wait cap and `maxAge` into a request. The parts below reject bad input by throwing or failing late: the budget throws on a `cost` below 1 or a negative `maxWait`, and the cache on a negative `maxAge`, so a call must be checked before anything is spent.",
        "evidence": [
          "common/UrlEncoding.kt:94 — `encodePathSegment`, `internal`, for the facade (baseline)",
          "common/UrlEncoding.kt:101 — `encodeQueryComponent`, the same",
          "common/TransportFailure.kt:128 — `InvalidRequest`, the failure a bad call returns",
          "../67-call-budget/consequences.md §4 — `acquire` throws `IllegalArgumentException` for a bad `cost` or `maxWait`",
          "../66-response-cache/consequences.md §4 — a negative `maxAge` throws `IllegalArgumentException`",
          "../consequences.md §8 — URLs assembled as text and parsed again; an empty path segment and a failing developer-written header or body are `InvalidRequest` before a permit is spent; vector T09, an absolute URL in a path argument, is this unit's test"
        ],
        "source": "../consequences.md §8; ../architecture.md §6.6",
        "link": "../consequences.md#8-what-the-later-units-build-on-units-1-to-9-as-built"
      },
      "options": [
        {
          "id": "validate-first",
          "name": "Per-call builders, `CallSpec` (path, query, headers, `method`, body, `lane`, `maxWait`, `maxAge`) and `FetchSpec` (`cost`, `lane`, `maxWait`), whose violations return `InvalidRequest` before anything is spent",
          "verdict": "accepted",
          "why": "A refused call costs no permit and no request; a developer-written value is checked once, in the facade, before the parts that throw on it.",
          "source": "../architecture.md §6.6; ../research/api-shape-and-failures.md Considerations 6",
          "link": "../architecture.md#66-call-facades"
        },
        {
          "id": "per-call-deadline",
          "name": "A per-call deadline on `CallSpec`",
          "verdict": "declined",
          "why": "Not in 1.0.0: a coalesced caller with a tighter deadline wraps its own call in `withTimeoutOrNull`, and a deadline parameter is additive later.",
          "source": "../architecture.md §12; ../research/api-shape-and-failures.md § The facade",
          "link": "../architecture.md#12-risks-at-the-systems-level"
        },
        {
          "id": "url-builder",
          "name": "Assembling the URL with OkHttp's `HttpUrl.Builder`",
          "verdict": "declined",
          "why": "Two parsers would decide the result. Text assembled and parsed once by the module's `Url` makes the URL the policy judges exactly the URL the transport receives, and refusing an empty segment keeps `x//y` from meaning `x/y`.",
          "source": "plan.md §6.3",
          "link": "plan.md#63-request-assembly-and-the-cache-key"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "text": "`CallSpec` was built as a `@WebToolsDsl` class with an `internal` constructor, seeded with the lane, the wait and the default headers it is given, method `GET`, no body and `maxAge` infinite; `path(…)` and `query(…)` append in call order, every `headers { }` block edits one builder seeded with the default headers, and it has no cost.",
            "state": "as-planned"
          },
          {
            "text": "`FetchSpec` was built holding `cost` 1, lane `Interactive` and the wait it is given, which `UrlFetcher.fetch` seeds with the fetcher's `defaultMaxWait`.",
            "state": "as-planned"
          },
          {
            "text": "The call-time checks return `InvalidRequest` with a constant rule and never throw: a throwing lambda, a negative wait or age (and for a fetch a cost below 1), a controlled header, an unbuildable URL or an unbuildable request; the fetcher runs its share before anything is spent, and the client's share runs in its call.",
            "state": "as-planned"
          },
          {
            "text": "`assembleUrl` joins the base text, the encoded segments and the encoded pairs and parses the text once; an empty, dot or unpaired-surrogate segment fails with its own rule, an absolute URL or a `//host` passed as a segment stays one segment on the base host (vector T09), and its KDoc states the assembly rules where the plan's listing cited a plan section.",
            "state": "amended",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
            "excerpt": {
              "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
              "heading": "D3. The internal KDoc states its rule instead of citing a plan section",
              "paragraph": "Departs from: the code blocks of plan §7.4 (`assembleUrl`), §7.5 (`verdictOf`, `fetchVerdictOf`, `retryAfterOf`), §7.7 (`CallDispatch`, `send`), §7.10 (`UpstreamClientConfig.resolve`) and §7.12 (`sizedFor`), whose KDoc cites \"(§6.3)\", \"(R12)\", \"(R13)\", \"(R14)\", \"(§6.5)\", \"R7\" and \"§6.10\", and the line comments of §7.11 that read \"R8 steps 2 to 6\", \"R8 step 7\" and \"R8 step 8\". · As built: each of those comments states the rule itself (the assembly steps and their failure rules, the classification order, what settles a permit, the construction checks in their order, what each private step of a …",
              "hash": "64959130e060"
            }
          },
          {
            "text": "`assembleUrl` returns the key parameters: the parsed URL's path, base path included, under the reserved name `/`, then the encoded pairs in call order, so no header, body, lane, `maxAge`, method or credential can enter them; pairing them with the endpoint id is the client's call.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "The KDoc of `assembleUrl` states the assembly steps and their failure rules itself, where the plan's listing read \"see the rules there\" with a plan section; no executable line differs.",
            "why": "A plan section number means nothing to a reader of the source, who does not have the plan beside it.",
            "when": "stage 2",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One system: a call's settings are checked once, in the facade, so the parts that throw on a bad value never see one.",
        "statements": [
          {
            "text": "`CallSpec` is the receiver of `call`'s lambda, seeded so a read gives the effective value: method `GET`, no body, the endpoint's lane, the client's `defaultMaxWait` and `maxAge` infinite, with `path(…)`, `query(…)` and `headers { }`; it has no cost, which is the endpoint's.",
            "ref": "plan.md#r3-callspec-and-fetchspec",
            "excerpt": {
              "ref": "plan.md#r3-callspec-and-fetchspec",
              "heading": "R3. `CallSpec` and `FetchSpec`",
              "paragraph": "`CallSpec` is the receiver of `UpstreamClient.call`'s lambda, seeded so that reading a property inside the lambda gives the effective value: `var method = HttpMethod.Get`, `var body: RequestBody? = null`, `var lane` = the endpoint's lane, `var maxWait` = the client's `defaultMaxWait`, `var maxAge = Duration.INFINITE`. `path(vararg segments)` appends raw segments, `query(name, value)` appends one raw pair (repeated names allowed, order kept), `headers { }` edits the request's headers through an `HttpHeaders.Builder` seeded with the client's default headers (several blocks edit the same …",
              "hash": "ac18b53f1428"
            }
          },
          {
            "text": "`FetchSpec` holds `cost` 1, lane `Interactive` and the fetcher's `defaultMaxWait`.",
            "ref": "plan.md#r3-callspec-and-fetchspec",
            "excerpt": {
              "ref": "plan.md#r3-callspec-and-fetchspec",
              "heading": "R3. `CallSpec` and `FetchSpec`",
              "paragraph": "`CallSpec` is the receiver of `UpstreamClient.call`'s lambda, seeded so that reading a property inside the lambda gives the effective value: `var method = HttpMethod.Get`, `var body: RequestBody? = null`, `var lane` = the endpoint's lane, `var maxWait` = the client's `defaultMaxWait`, `var maxAge = Duration.INFINITE`. `path(vararg segments)` appends raw segments, `query(name, value)` appends one raw pair (repeated names allowed, order kept), `headers { }` edits the request's headers through an `HttpHeaders.Builder` seeded with the client's default headers (several blocks edit the same …",
              "hash": "ac18b53f1428"
            }
          },
          {
            "text": "A spec is checked at call time and never by throwing out of the facade: a lambda that throws, a negative wait or age, an unbuildable URL, a controlled header or an unbuildable request each return `InvalidRequest` before anything is spent.",
            "ref": "plan.md#r8-the-call-pipeline-of-upstreamclientcall",
            "excerpt": {
              "ref": "plan.md#r8-the-call-pipeline-of-upstreamclientcall",
              "heading": "R8. The call pipeline of `UpstreamClient.call`",
              "paragraph": "In this order, and nothing is spent (no permit, no transport call, no cache entry) before step 8:",
              "hash": "1859956cd728"
            }
          },
          {
            "text": "The URL is assembled as text from the encoded segments and pairs and parsed once; an empty, dot or unpaired-surrogate segment is refused, and an absolute URL passed as a segment stays one segment on the base host (vector T09).",
            "ref": "plan.md#r9-request-assembly",
            "excerpt": {
              "ref": "plan.md#r9-request-assembly",
              "heading": "R9. Request assembly",
              "paragraph": "The URL is `base text` + `/` + the encoded segments joined by `/` + `?` + the encoded pairs joined by `&`, then `Url.parse`; never `HttpUrl.Builder`. The base text is `baseUrl.toString()` (it has no query), with one trailing `/` dropped when segments follow. Each segment goes through `encodePathSegment` (`.`, `..` and an unpaired surrogate fail) and an empty segment is refused; each query name and value through `encodeQueryComponent`. An absolute URL or a `//host` passed as a segment is encoded into one segment on the base host (vector T09).",
              "hash": "987f4b464b7a"
            }
          },
          {
            "text": "The cache key is the endpoint id, the full request path under the reserved name `/` and the encoded pairs in call order; never a header, the body, the lane, `maxAge`, the method or a credential.",
            "ref": "plan.md#r10-the-cache-key",
            "excerpt": {
              "ref": "plan.md#r10-the-cache-key",
              "heading": "R10. The cache key",
              "paragraph": "`CacheKey<T>(endpoint.id, listOf(\"/\" to url.encodedPath) + encodedQueryPairs)`: the full request path (base path included) under the reserved name `/` (an encoded query name never contains `/`), then the encoded query pairs in call order (`CacheKey` sorts by name, stably). Never a header, the body, the lane, `maxAge`, the method or a credential.",
              "hash": "1acbf356492f"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Write `CallSpec` and `FetchSpec` with their Level 2 classes.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/CallSpec.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/FetchSpec.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/CallSpecComponentTest.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/FetchSpecComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-2",
            "what": "Write the pure request assembly with its Level 4a table.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/RequestAssembly.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/RequestAssemblyDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `CallSpecComponentTest`, `FetchSpecComponentTest` and `UpstreamClientRequestComponentTest`; Level 4a (deterministic) `RequestAssemblyDeterministicTest`, vector T09 among its tables"
        ],
        "risks": [
          "A shared load runs the first caller's request, so a per-call header that changes the answer on a cached endpoint would hand one caller's answer to another; the KDoc says so (§10)"
        ],
        "source": "plan.md R3, R8 to R10, §6.3",
        "link": "plan.md#63-request-assembly-and-the-cache-key"
      }
    },
    {
      "id": "upstream-client",
      "group": "Code",
      "name": "`UpstreamClient` and its call pipeline",
      "system": "webtools-http-facades",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "The cache, the budget and the transport do not compose themselves. Taken in the wrong order a cache hit spends a permit, coalesced callers each spend one, or a failure is stored; and nothing classifies a response into `Found`, `Missing`, `DecodeFailure`, `RateLimited` or `UpstreamError`.",
        "evidence": [
          "common/ResponseCache.kt:50 — `getOrLoad` with a loader, the seam the budget runs inside (baseline)",
          "common/WithPermit.kt:23 — `withPermit`, the same for the transport",
          "common/HttpFailure.kt:86 — `DecodeFailure`, which nothing returns yet",
          "../66-response-cache/consequences.md §4 — the loader runs in the cache's scope; failures are values, never stored",
          "../architecture.md §7.2 — cache, then budget inside the loader, then transport"
        ],
        "source": "../architecture.md §6.6, §7.2",
        "link": "../architecture.md#72-the-call-path-cache-then-budget-inside-the-loader-then-transport"
      },
      "options": [
        {
          "id": "fixed-order",
          "name": "One fixed order: cache, then budget inside the cache's loader, then transport; a non-`GET` skips the cache; a `2xx` decoded, a miss status `Missing`, anything else `UpstreamError`",
          "verdict": "accepted",
          "why": "A hit costs nothing, N coalesced callers cost one permit, failures are never stored and `Missing` is stored for `missTtl`. A final class composing ports: tests substitute the transport, the level at which a fake is useful.",
          "source": "../architecture.md §6.6, §7.2; ../research/api-shape-and-failures.md F9",
          "link": "../architecture.md#66-call-facades"
        },
        {
          "id": "one-facade",
          "name": "One facade class with an optional base URL and both methods",
          "verdict": "declined",
          "why": "Two types encode the invariant that an untrusted URL cannot reach `call` and `fetch` cannot be handed an endpoint's cache.",
          "source": "../architecture.md §10; ../research/api-shape-and-failures.md Considerations 3",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "typed-cache",
          "name": "A typed cache per endpoint",
          "verdict": "declined",
          "why": "It multiplies the bounds and cannot give one bound per client.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "cancel-shared-load",
          "name": "Cancelling the shared load when its last waiter leaves",
          "verdict": "declined",
          "why": "A ref-counted multicaster with its own race class; running to completion wastes at most `maxWait` plus the call timeout for one load per key.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "decode-before-miss",
          "name": "Decoding a `2xx` before the declared misses, in the order #55's architecture §6.6 listed",
          "verdict": "declined",
          "why": "A `204` declared a miss would reach a decoder not written for an empty body; checking the misses first keeps a declared miss a miss.",
          "source": "plan.md §6.6, §6.11",
          "link": "plan.md#66-classification-and-retryafter"
        },
        {
          "id": "fetcher-only-precheck",
          "name": "The policy pre-check in `UrlFetcher` only, as #55's architecture §6.6 gave it",
          "verdict": "declined",
          "why": "The client checks its own policy too, before the cache, so a per-call sensitive header under an `Untrusted` policy is refused at no cost and caches nothing.",
          "source": "plan.md §6.4, §6.11",
          "link": "plan.md#611-where-this-plan-refines-the-parent-architecture"
        }
      ],
      "asBuilt": {
        "state": "deviated",
        "summary": null,
        "changes": [
          {
            "what": "The KDoc of `verdictOf` states the classification order itself, where the plan's listing cited a requirement number; no executable line differs.",
            "why": "A requirement number of the plan means nothing to a reader of the source, who does not have the plan beside it.",
            "when": "stage 2",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section"
          },
          {
            "what": "The line comments of the client's private call steps state what each step does, where the plan's listing cited the pipeline's step numbers; no executable line differs.",
            "why": "A requirement's step number means nothing to a reader of the source, who does not have the plan beside it.",
            "when": "stage 3",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section"
          },
          {
            "what": "A call can also return a `Result.failure` holding an `IllegalStateException`: a cancellation that is not the caller's, thrown inside a part the consumer supplies (a `withTimeout` expiring in the decoder, the reader, a transport or a budget), escapes the load and the cache's loader rule returns it as that failure; a non-`GET` takes the same rule.",
            "why": "The plan's error-handling requirement says every failure is an `HttpFailure`, while its table of `call`'s failures lists this one; the build follows the table, the response cache's rule as built, and passes the cache's result through as it does every other failure. The cache's own Level 2 component tests pin the rule; no test of this unit drives it through `call`.",
            "when": "stage 3; recorded at the plan's reconciliation",
            "ref": "final-implementation.md#d12-a-call-can-return-an-illegalstateexception",
            "link": "../66-response-cache/consequences.md#4-sibling-units"
          }
        ],
        "statements": [
          {
            "text": "`UpstreamClient` was built as a final `AutoCloseable` with an `internal` constructor, `baseUrl`, `call`, `utilisation()`, `cacheStats()`, `flush()`, `close()` and `toString()`; the common factory `UpstreamClient(transport, baseUrl) { }` builds it, and the ABI dump shows no `<init>`.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2-the-js-compile-and-the-dump",
            "excerpt": {
              "ref": "final-implementation.md#p2-the-js-compile-and-the-dump",
              "heading": "P2. The `js` compile and the dump",
              "paragraph": "`:webtools-http-commoncheck:build` passes after each of the four code commits (`Channel`, `Job()` and `KClass.simpleName` compile for `js`), and `apiDump` followed, in a separate invocation, by `apiCheck` and `verifyJavaFloor` passes after each. The dump grows by exactly plan §7.16, with no line removed:",
              "hash": "6de2ab4d3d7d"
            }
          },
          {
            "text": "`call` returns at once, spending nothing, for a closed client (`Closed(\"client\")`), a bad spec, limit, URL, header or request (`InvalidRequest`, logged once at `DEBUG`) and a policy refusal (logged once at `WARN`, never cached); the private steps' comments state what each does where the plan's listing cited the pipeline's step numbers.",
            "state": "amended",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
            "excerpt": {
              "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
              "heading": "D3. The internal KDoc states its rule instead of citing a plan section",
              "paragraph": "Departs from: the code blocks of plan §7.4 (`assembleUrl`), §7.5 (`verdictOf`, `fetchVerdictOf`, `retryAfterOf`), §7.7 (`CallDispatch`, `send`), §7.10 (`UpstreamClientConfig.resolve`) and §7.12 (`sizedFor`), whose KDoc cites \"(§6.3)\", \"(R12)\", \"(R13)\", \"(R14)\", \"(§6.5)\", \"R7\" and \"§6.10\", and the line comments of §7.11 that read \"R8 steps 2 to 6\", \"R8 step 7\" and \"R8 step 8\". · As built: each of those comments states the rule itself (the assembly steps and their failure rules, the classification order, what settles a permit, the construction checks in their order, what each private step of a …",
              "hash": "64959130e060"
            }
          },
          {
            "text": "A `GET` goes through `getOrLoad` with the shared dispatch inside the cache's loader, so a hit costs nothing and N coalesced callers cost one permit and one request; any other method runs the loader on the caller's coroutine and bypasses the cache.",
            "state": "as-planned"
          },
          {
            "text": "A response is classified by the shared `verdictOf`: a throttle is `RateLimited`, a declared miss status `Missing` (stored for `missTtl`), a `2xx` decoded into `Found` (stored for `ttl`), a decoder's failure `DecodeFailure` and anything else `UpstreamError`; no failure is stored, and the verdict function's KDoc states the order where the plan's listing cited a requirement.",
            "state": "amended",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
            "excerpt": {
              "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
              "heading": "D3. The internal KDoc states its rule instead of citing a plan section",
              "paragraph": "Departs from: the code blocks of plan §7.4 (`assembleUrl`), §7.5 (`verdictOf`, `fetchVerdictOf`, `retryAfterOf`), §7.7 (`CallDispatch`, `send`), §7.10 (`UpstreamClientConfig.resolve`) and §7.12 (`sizedFor`), whose KDoc cites \"(§6.3)\", \"(R12)\", \"(R13)\", \"(R14)\", \"(§6.5)\", \"R7\" and \"§6.10\", and the line comments of §7.11 that read \"R8 steps 2 to 6\", \"R8 step 7\" and \"R8 step 8\". · As built: each of those comments states the rule itself (the assembly steps and their failure rules, the classification order, what settles a permit, the construction checks in their order, what each private step of a …",
              "hash": "64959130e060"
            }
          },
          {
            "text": "No expected failure is thrown, and every failure the facade and its own parts create is a `Result.failure` holding an `HttpFailure`, a decoder's own passed through unchanged; only the caller's cancellation and an `Error` propagate. A `CancellationException` that is not the caller's, escaping a load from a part the consumer supplies, comes back as a `Result.failure` holding an `IllegalStateException` with the escaped exception as its cause, by the response cache's loader rule, which the plan's table of `call`'s failures lists and its error-handling requirement does not.",
            "state": "deviated",
            "ref": "final-implementation.md#d12-a-call-can-return-an-illegalstateexception",
            "excerpt": {
              "ref": "final-implementation.md#d12-a-call-can-return-an-illegalstateexception",
              "heading": "D12. A call can return an `IllegalStateException`",
              "paragraph": "Departs from: plan R18, \"Every failure of a call is a `Result.failure` holding an `HttpFailure`\". · As built: `UpstreamClient.call` also returns a `Result.failure` holding an `IllegalStateException`, with a constant message and the escaped exception as its `cause`, when a `CancellationException` that is not the caller's escapes the load: one thrown inside a part the consumer supplies (the decoder, the reader, a transport or a budget), such as a `withTimeout` expiring there. The call's loader runs under the cache's loader rule (`LoaderRunner.kt`), which rethrows a cancellation only when the …",
              "hash": "b76c376f829c"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One system: a final class composing the landed ports in one fixed order, adding only the request assembly, the permit rules, the classification and the closing order.",
        "statements": [
          {
            "text": "`UpstreamClient` is a final `AutoCloseable` with `baseUrl`, `call`, `utilisation()`, `cacheStats()`, `flush()` and `close()`, built over a transport by `UpstreamClient(transport, baseUrl) { }`.",
            "ref": "plan.md#r5-upstreamclient",
            "excerpt": {
              "ref": "plan.md#r5-upstreamclient",
              "heading": "R5. `UpstreamClient`",
              "paragraph": "`public class UpstreamClient internal constructor(...) : AutoCloseable` with `val baseUrl: Url`, `suspend fun <T : Any> call(endpoint, configure = {}): Result<Lookup<T>>`, `suspend fun utilisation(): BudgetUtilisation`, `fun cacheStats(): CacheStats`, `suspend fun flush(): Result<Unit>`, `override fun close()` and `toString()`. · The common factory `UpstreamClient(transport: HttpTransport, baseUrl: Url, configure: UpstreamClientConfig.() -> Unit = {})` never closes the transport; without `cache(...)` it uses `ResponseCache.Disabled`, without a budget `CallBudget.Unlimited`; a …",
              "hash": "ef50401da9cf"
            }
          },
          {
            "text": "A call spends nothing before the cache step: a closed client, a bad spec, URL or header, and a policy refusal return at once, the refusal logged once at `WARN`.",
            "ref": "plan.md#r8-the-call-pipeline-of-upstreamclientcall",
            "excerpt": {
              "ref": "plan.md#r8-the-call-pipeline-of-upstreamclientcall",
              "heading": "R8. The call pipeline of `UpstreamClient.call`",
              "paragraph": "In this order, and nothing is spent (no permit, no transport call, no cache entry) before step 8:",
              "hash": "1859956cd728"
            }
          },
          {
            "text": "A `GET` goes through `getOrLoad` with the budget inside the cache's loader, so a hit costs nothing and coalesced callers share one load and one permit; any other method runs the loader on the caller's coroutine.",
            "ref": "plan.md#64-the-call-pipeline-of-upstreamclientcall",
            "excerpt": {
              "ref": "plan.md#64-the-call-pipeline-of-upstreamclientcall",
              "heading": "6.4 The call pipeline of `UpstreamClient.call`",
              "paragraph": "The guard runs before the cache: a refusal must cost nothing and cache nothing, and it depends on the request (the headers), which the key does not see. A coalesced caller therefore passes its own pre-check, then joins a load whose request is the first caller's.",
              "hash": "3f579b10a770"
            }
          },
          {
            "text": "A response is classified in order: a throttle is `RateLimited`, a declared miss status `Missing`, a `2xx` decoded (a decoder's failure `DecodeFailure`), anything else `UpstreamError`; failures are never stored, `Missing` is stored for `missTtl` and `Found` for `ttl`.",
            "ref": "plan.md#r12-classification-in-upstreamclient-in-this-order",
            "excerpt": {
              "ref": "plan.md#r12-classification-in-upstreamclient-in-this-order",
              "heading": "R12. Classification in `UpstreamClient`, in this order",
              "paragraph": "a throttle (`signals.throttled` or status `429`) → `UpstreamFailure.RateLimited(status, retryAfter)` (R14), logged once at `WARN`, no retry; · a status in `endpoint.misses` → `Lookup.Missing` (stored for `missTtl`); · a `2xx` → `endpoint.decode(response)` inside `catchNonFatal`, flattened: success → `Lookup.Found(value)` (weight 1); a returned or thrown `HttpFailure` passes through unchanged; any other returned or thrown non-fatal exception → `HttpFailure.DecodeFailure(cause)`; an `Error` propagates; · anything else (a `1xx`, a `3xx` the transport returned, a `4xx` or `5xx` not throttled) → …",
              "hash": "ba5c19d1ab76"
            }
          },
          {
            "text": "No expected failure is thrown: every failure is a `Result.failure` holding an `HttpFailure`, and only the caller's cancellation and an `Error` propagate.",
            "ref": "plan.md#r18-error-handling",
            "excerpt": {
              "ref": "plan.md#r18-error-handling",
              "heading": "R18. Error handling",
              "paragraph": "No expected failure is thrown. Every failure of a call is a `Result.failure` holding an `HttpFailure`. A construction refusal is `IllegalArgumentException` (a programmer error). The only exceptions a call throws are the caller's `CancellationException` and an `Error` (the decoder's, the budget store's, the transport's, the reader's). §6.9 lists each signature.",
              "hash": "5fd5feb2d36b"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-2",
            "what": "Write the pure response verdicts with their Level 4a table.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/ResponseVerdict.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/ResponseVerdictDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-3",
            "what": "Write `UpstreamClient` and its common factory, with the client's Level 2 classes on the fake transport and the virtual clock.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UpstreamClient.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientCallComponentTest.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientCachingComponentTest.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientClassificationComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `UpstreamClientCallComponentTest`, `UpstreamClientCachingComponentTest`, `UpstreamClientClassificationComponentTest`, `UpstreamClientRequestComponentTest` and `UpstreamClientPolicyRefusalComponentTest`; Level 4a (deterministic) `ResponseVerdictDeterministicTest`; Level 3 (integration) `UpstreamClientLoopbackIntegrationTest`"
        ],
        "risks": [
          "A decoder runs on the cache's dispatcher, so one that blocks starves it; the KDoc says CPU only (§10)",
          "A `304` or a `3xx` the client receives is an `UpstreamError`, as the client sends no conditional headers (§10)"
        ],
        "source": "plan.md R5, R8, R12, R18, §6.2, §6.4, §6.6",
        "link": "plan.md#64-the-call-pipeline-of-upstreamclientcall"
      }
    },
    {
      "id": "permit-settlement",
      "group": "Code",
      "name": "The permit after a transport failure",
      "system": "webtools-http-facades",
      "where": "common/ (new)",
      "gap": {
        "kind": "risk",
        "summary": "The architecture releases the permit for `RefusedByPolicy`, `InvalidRequest` and `Closed` as failures before anything was sent. As built, only `InvalidRequest` always comes before sending: `RefusedByPolicy` can follow a sent hop and `Closed` can end a call whose request reached the server, so releasing on them would under-count what the upstream saw.",
        "evidence": [
          "../65-okhttp-transport/consequences.md §4 — what each failure implies about what was sent, against #65's plan §12 table",
          "../architecture.md §6.6 — the dated correction of 2026-11-02",
          "common/Permit.kt:28 — `complete(signals)`, and `:31` `release()`, both idempotent (baseline)",
          "research/README.md § Settling a permit after a transport failure — #65's table read failure by failure: only `InvalidRequest` proves that nothing was sent, and a failure carries no hop number",
          "../research/call-budget.md F1 — providers count refused requests"
        ],
        "source": "../65-okhttp-transport/consequences.md §4; ../architecture.md §6.6",
        "link": "../65-okhttp-transport/consequences.md#4-sibling-units"
      },
      "options": [
        {
          "id": "by-what-was-sent",
          "name": "Release the permit only for a failure that sent nothing; complete it as spent, with no signals, for any other failure",
          "verdict": "accepted",
          "why": "The budget counts what the upstream saw: a request it never received is refunded, one it may have received is charged.",
          "source": "../architecture.md §6.6",
          "link": "../architecture.md#66-call-facades"
        },
        {
          "id": "three-before-send",
          "name": "Release for every `RefusedByPolicy`, `InvalidRequest` and `Closed`, as §6.6 first wrote",
          "verdict": "declined",
          "why": "Exact only for `InvalidRequest` as the transport was built: a refused redirect target or a later hop's refusal, and a close mid-call, can follow a request the server received.",
          "source": "../65-okhttp-transport/consequences.md §4, §5",
          "link": "../65-okhttp-transport/consequences.md#5-where-the-design-refines-the-parent-documents"
        },
        {
          "id": "per-failure-table",
          "name": "How `RefusedByPolicy` and `Closed` are settled, failure by failure, against the transport's table of what was written",
          "verdict": "partial",
          "why": "Settled by this unit's plan against #65's plan §12, taken in part: the table is read failure by failure; a refund for a `RefusedByPolicy` or a `Closed` that sent nothing is not taken, as a failure carries no hop number and either can follow a request the server received, so both leave the permit spent.",
          "source": "plan.md §6.5; research/README.md § Settling a permit after a transport failure",
          "link": "plan.md#65-the-loader-and-the-permit-rules"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "changes": [
          {
            "what": "The KDoc of `CallDispatch` and `send` states what settles a permit itself, where the plan's listing cited a plan section; no executable line differs.",
            "why": "A plan section number means nothing to a reader of the source, who does not have the plan beside it.",
            "when": "stage 2",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section"
          }
        ],
        "statements": [
          {
            "text": "Only an `InvalidRequest` from the transport releases the permit, in `CallDispatch.send`, for the client as for the fetcher; `UpstreamClientPermitRulesComponentTest` holds it.",
            "state": "as-planned"
          },
          {
            "text": "Every other failure, `RefusedByPolicy` and `Closed` from the transport included, leaves the permit spent with no signals; a cancelled caller leaves it spent and the cancellation propagates, and `BudgetExhausted` or a closed budget comes back as a value with nothing sent.",
            "state": "as-planned"
          },
          {
            "text": "A response's signals are read with the configured reader and handed to `complete` before the response is classified; a reader that throws gives no signals and one `WARN`.",
            "state": "as-planned"
          },
          {
            "text": "`CallDispatch.send` is the one place that touches a permit, shared by both facades, and a transport that throws becomes `Io`; its KDoc states what settles a permit where the plan's listing cited a plan section.",
            "state": "amended",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
            "excerpt": {
              "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
              "heading": "D3. The internal KDoc states its rule instead of citing a plan section",
              "paragraph": "Departs from: the code blocks of plan §7.4 (`assembleUrl`), §7.5 (`verdictOf`, `fetchVerdictOf`, `retryAfterOf`), §7.7 (`CallDispatch`, `send`), §7.10 (`UpstreamClientConfig.resolve`) and §7.12 (`sizedFor`), whose KDoc cites \"(§6.3)\", \"(R12)\", \"(R13)\", \"(R14)\", \"(§6.5)\", \"R7\" and \"§6.10\", and the line comments of §7.11 that read \"R8 steps 2 to 6\", \"R8 step 7\" and \"R8 step 8\". · As built: each of those comments states the rule itself (the assembly steps and their failure rules, the classification order, what settles a permit, the construction checks in their order, what each private step of a …",
              "hash": "64959130e060"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The budget counts what the upstream saw, and a window that guards a bill is never under-counted: at most one permit over per such failure, on the safe side.",
        "statements": [
          {
            "text": "Only `InvalidRequest`, which is always returned before anything is sent, releases the permit.",
            "ref": "plan.md#r11-the-loader-and-the-permit-rules",
            "excerpt": {
              "ref": "plan.md#r11-the-loader-and-the-permit-rules",
              "heading": "R11. The loader and the permit rules",
              "paragraph": "`budget.withPermit(endpoint.cost, lane, maxWait) { permit -> … }`; `BudgetExhausted` and `Closed(\"budget\")` come back as values and nothing is sent. Inside, `transport.execute(request)`: · `InvalidRequest` → `permit.release()` (the only failure that proves nothing was sent); · every other failure (`RefusedByPolicy`, `Closed`, `DnsFailed`, `ConnectFailed`, `Timeout`, `Io`, `TlsFailed`, `Protocol`, `BodyTooLarge`, `TooManyRedirects`, `RetryRefused`, any other `HttpFailure`) leaves the permit spent with no signals (`withPermit`'s `finally`); · a response → `signals = rateSignals.read(response, …",
              "hash": "949532870498"
            }
          },
          {
            "text": "Every other failure, `RefusedByPolicy` and `Closed` included, leaves the permit spent with no signals through `withPermit`'s `finally`; a cancellation or an `Error` does too, and propagates.",
            "ref": "plan.md#r11-the-loader-and-the-permit-rules",
            "excerpt": {
              "ref": "plan.md#r11-the-loader-and-the-permit-rules",
              "heading": "R11. The loader and the permit rules",
              "paragraph": "`budget.withPermit(endpoint.cost, lane, maxWait) { permit -> … }`; `BudgetExhausted` and `Closed(\"budget\")` come back as values and nothing is sent. Inside, `transport.execute(request)`: · `InvalidRequest` → `permit.release()` (the only failure that proves nothing was sent); · every other failure (`RefusedByPolicy`, `Closed`, `DnsFailed`, `ConnectFailed`, `Timeout`, `Io`, `TlsFailed`, `Protocol`, `BodyTooLarge`, `TooManyRedirects`, `RetryRefused`, any other `HttpFailure`) leaves the permit spent with no signals (`withPermit`'s `finally`); · a response → `signals = rateSignals.read(response, …",
              "hash": "949532870498"
            }
          },
          {
            "text": "A response's signals are read with the configured reader and handed to `complete` before the response is classified; a reader that throws gives no signals and one `WARN`.",
            "ref": "plan.md#r11-the-loader-and-the-permit-rules",
            "excerpt": {
              "ref": "plan.md#r11-the-loader-and-the-permit-rules",
              "heading": "R11. The loader and the permit rules",
              "paragraph": "`budget.withPermit(endpoint.cost, lane, maxWait) { permit -> … }`; `BudgetExhausted` and `Closed(\"budget\")` come back as values and nothing is sent. Inside, `transport.execute(request)`: · `InvalidRequest` → `permit.release()` (the only failure that proves nothing was sent); · every other failure (`RefusedByPolicy`, `Closed`, `DnsFailed`, `ConnectFailed`, `Timeout`, `Io`, `TlsFailed`, `Protocol`, `BodyTooLarge`, `TooManyRedirects`, `RetryRefused`, any other `HttpFailure`) leaves the permit spent with no signals (`withPermit`'s `finally`); · a response → `signals = rateSignals.read(response, …",
              "hash": "949532870498"
            }
          },
          {
            "text": "`CallDispatch.send` is the one place that touches a permit, shared by both facades; a transport that throws instead of returning a `Result` becomes `Io`.",
            "ref": "plan.md#65-the-loader-and-the-permit-rules",
            "excerpt": {
              "ref": "plan.md#65-the-loader-and-the-permit-rules",
              "heading": "6.5 The loader and the permit rules",
              "paragraph": "`CallDispatch.send(request, cost, lane, maxWait): Result<Sent>` is the one place that touches a permit, shared by both facades:",
              "hash": "3f4a4647ce33"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-2",
            "what": "Write the shared dispatch, the one place that touches a permit, with its Level 2 class.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/CallDispatch.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CallDispatchComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-3",
            "what": "Run the client's loader through it, its permit rules held by their own Level 2 class.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientPermitRulesComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `CallDispatchComponentTest`, `UpstreamClientPermitRulesComponentTest` and `UrlFetcherResponseComponentTest`, each reading the permit's record from `PermitRecordingBudget`"
        ],
        "risks": [
          "A first-hop `RefusedByPolicy`, `DnsFailed` or `ConnectFailed` is charged though no byte was sent, so a hard daily window can reach its stop a few calls early under a flapping network (§10)"
        ],
        "source": "plan.md R11, §6.5, §6.11",
        "link": "plan.md#65-the-loader-and-the-permit-rules"
      }
    },
    {
      "id": "rate-limited",
      "group": "Code",
      "name": "Rate signals and `RateLimited`",
      "system": "webtools-http-facades",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "The budget pauses on signals handed to `complete`, but nothing reads them from a real response in a pipeline, and nothing returns `RateLimited`, whose constructor is `internal` and waits for the facades. After `complete(signals)` the budget's `pausedFor` can differ from the response's `Retry-After` either way, so which one the failure reports is open.",
        "evidence": [
          "common/UpstreamFailure.kt:38 — `RateLimited` with an `internal` constructor (baseline)",
          "common/RateSignalsReader.kt:33 — `RateSignalsReader.Standard`: a `429` throttled, a `503` only with a parseable `Retry-After`",
          "../67-call-budget/consequences.md §4 — `pausedFor` after `complete`; `max(signals.retryAfter, pausedFor)` is not redundant",
          "../architecture.md §13 — the dated correction of 2026-11-05: which of the two the failure reports is this unit's",
          "research/rate-limited-retry-after.md F3, F4 — server-facing clients report the server's number, client-side limiters their own wait, and where both meet the longer wins",
          "research/rate-limited-retry-after.md F6, F7 — in this stack the pause and the server's ask differ both ways: longer with another hint or an earlier pause, shorter when `Retry-After` exceeds `maxPause`, and zero under `CallBudget.Unlimited`"
        ],
        "source": "../67-call-budget/consequences.md §4; ../architecture.md §13; research/README.md § What a rate-limited failure reports",
        "link": "../architecture.md#13-known-gaps"
      },
      "options": [
        {
          "id": "rate-limited-no-retry",
          "name": "Read the signals with the configured reader, `complete` the permit with them, and return a throttle or a `429` as `RateLimited`, with no retry",
          "verdict": "accepted",
          "why": "The caller and the budget agree on the pause, and a poller does not parse `Retry-After` itself.",
          "source": "../architecture.md §6.6; ../research/api-shape-and-failures.md Considerations 5",
          "link": "../architecture.md#66-call-facades"
        },
        {
          "id": "status-as-response",
          "name": "Return every status, `429` included, as a response, and leave its meaning to the caller",
          "verdict": "declined",
          "why": "The budget has already paused, so the caller would have to rediscover a pause the stack knows.",
          "source": "../research/api-shape-and-failures.md Considerations 5",
          "link": "../research/api-shape-and-failures.md#considerations"
        },
        {
          "id": "retry-after-value",
          "name": "What `RateLimited.retryAfter` reports: the response's `Retry-After`, the budget's `pausedFor`, or their maximum",
          "verdict": "partial",
          "why": "Settled by this unit's plan, taken in part: their maximum is taken, read once after `complete(signals)` in the loader that creates the failure; the response's value alone and the pause alone are not, each declined beside it.",
          "source": "plan.md R14, §6.6; research/README.md § What a rate-limited failure reports",
          "link": "plan.md#r14-ratelimitedretryafter"
        },
        {
          "id": "server-value-alone",
          "name": "`retryAfter` the server's `Retry-After` alone, zero when absent",
          "verdict": "declined",
          "why": "It sends a caller into `BudgetExhausted(Paused)` while a longer pause is in force, and reports zero for a `429` without `Retry-After` under a windowed budget.",
          "source": "research/README.md § What a rate-limited failure reports",
          "link": "research/README.md#what-a-rate-limited-failure-reports"
        },
        {
          "id": "pause-alone",
          "name": "`retryAfter` the budget's `pausedFor` alone",
          "verdict": "declined",
          "why": "It drops a `Retry-After` above `maxPause`, and is empty for any budget that does not pause.",
          "source": "research/README.md § What a rate-limited failure reports",
          "link": "research/README.md#what-a-rate-limited-failure-reports"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "changes": [
          {
            "what": "The KDoc of `retryAfterOf` states the rule itself, where the plan's listing cited a requirement number; no executable line differs.",
            "why": "A requirement number of the plan means nothing to a reader of the source, who does not have the plan beside it.",
            "when": "stage 2",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section"
          }
        ],
        "statements": [
          {
            "text": "A throttle, a `429` or a reader's throttled signal (such as a `503` with `Retry-After`), becomes `RateLimited(status, retryAfter)`, logged once at `WARN`; the throttled request is sent exactly once and never retried.",
            "state": "as-planned"
          },
          {
            "text": "`retryAfter` is the longer of the server's `Retry-After`, zero when absent, and the budget's pause, read once after `complete(signals)` inside the loader, so two coalesced callers receive one `RateLimited` instance; the KDoc of `retryAfterOf` states the rule where the plan's listing cited a requirement.",
            "state": "amended",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
            "excerpt": {
              "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
              "heading": "D3. The internal KDoc states its rule instead of citing a plan section",
              "paragraph": "Departs from: the code blocks of plan §7.4 (`assembleUrl`), §7.5 (`verdictOf`, `fetchVerdictOf`, `retryAfterOf`), §7.7 (`CallDispatch`, `send`), §7.10 (`UpstreamClientConfig.resolve`) and §7.12 (`sizedFor`), whose KDoc cites \"(§6.3)\", \"(R12)\", \"(R13)\", \"(R14)\", \"(§6.5)\", \"R7\" and \"§6.10\", and the line comments of §7.11 that read \"R8 steps 2 to 6\", \"R8 step 7\" and \"R8 step 8\". · As built: each of those comments states the rule itself (the assembly steps and their failure rules, the classification order, what settles a permit, the construction checks in their order, what each private step of a …",
              "hash": "64959130e060"
            }
          },
          {
            "text": "A `utilisation()` that throws falls back to the server's value, or zero, with one `WARN`.",
            "state": "as-planned"
          },
          {
            "text": "`RateLimited`'s KDoc says how long to wait before calling again; its shape is unchanged.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The caller and the budget agree on the wait: waiting this long and then calling is neither refused by the budget's pause nor sent back to a server that asked for more time.",
        "statements": [
          {
            "text": "A throttle, a reader's throttled signal or a `429`, becomes `RateLimited(status, retryAfter)`, logged once at `WARN` and never retried.",
            "ref": "plan.md#r12-classification-in-upstreamclient-in-this-order",
            "excerpt": {
              "ref": "plan.md#r12-classification-in-upstreamclient-in-this-order",
              "heading": "R12. Classification in `UpstreamClient`, in this order",
              "paragraph": "a throttle (`signals.throttled` or status `429`) → `UpstreamFailure.RateLimited(status, retryAfter)` (R14), logged once at `WARN`, no retry; · a status in `endpoint.misses` → `Lookup.Missing` (stored for `missTtl`); · a `2xx` → `endpoint.decode(response)` inside `catchNonFatal`, flattened: success → `Lookup.Found(value)` (weight 1); a returned or thrown `HttpFailure` passes through unchanged; any other returned or thrown non-fatal exception → `HttpFailure.DecodeFailure(cause)`; an `Error` propagates; · anything else (a `1xx`, a `3xx` the transport returned, a `4xx` or `5xx` not throttled) → …",
              "hash": "ba5c19d1ab76"
            }
          },
          {
            "text": "`retryAfter` is the longer of the server's `Retry-After`, zero when absent, and the budget's `pausedFor`, read once after `complete(signals)` in the loader, so every coalesced caller receives one instance.",
            "ref": "plan.md#r14-ratelimitedretryafter",
            "excerpt": {
              "ref": "plan.md#r14-ratelimitedretryafter",
              "heading": "R14. `RateLimited.retryAfter`",
              "paragraph": "`retryAfter = maxOf(signals.retryAfter ?: Duration.ZERO, budget.utilisation().pausedFor)`, the utilisation read once, after `permit.complete(signals)`, inside the loader that creates the failure (so every coalesced waiter receives the same instance). A `utilisation()` that throws a non-fatal exception falls back to the server's value (or zero) with one `WARN`. The KDoc of `RateLimited` says so (R20).",
              "hash": "83af2c0b5c9c"
            }
          },
          {
            "text": "A `utilisation()` that throws falls back to the server's value, or zero, with one `WARN`.",
            "ref": "plan.md#r14-ratelimitedretryafter",
            "excerpt": {
              "ref": "plan.md#r14-ratelimitedretryafter",
              "heading": "R14. `RateLimited.retryAfter`",
              "paragraph": "`retryAfter = maxOf(signals.retryAfter ?: Duration.ZERO, budget.utilisation().pausedFor)`, the utilisation read once, after `permit.complete(signals)`, inside the loader that creates the failure (so every coalesced waiter receives the same instance). A `utilisation()` that throws a non-fatal exception falls back to the server's value (or zero) with one `WARN`. The KDoc of `RateLimited` says so (R20).",
              "hash": "83af2c0b5c9c"
            }
          },
          {
            "text": "`RateLimited`'s KDoc is reworded to how long to wait before calling again; its shape does not change.",
            "ref": "plan.md#79-cmupstreamfailurekt-changed-kdoc-only-stage-2",
            "excerpt": {
              "ref": "plan.md#79-cmupstreamfailurekt-changed-kdoc-only-stage-2",
              "heading": "7.9 `<cm>UpstreamFailure.kt` (changed, KDoc only, stage 2)",
              "paragraph": "Replace the KDoc of `RateLimited` (class comment, `@param retryAfter` and the property comment) and the class-level one-liner of `UpstreamFailure`:",
              "hash": "a0c1e316e809"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-2",
            "what": "Write the throttle and the `retryAfter` rule in the shared dispatch and the pure verdicts, and reword `RateLimited`'s KDoc.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/CallDispatch.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/ResponseVerdict.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UpstreamFailure.kt"
            ],
            "check": "`:webtools-http:deterministicTest`, `componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-3",
            "what": "The client's throttle path, held by its own Level 2 class.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientThrottleComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4a (deterministic) `ResponseVerdictDeterministicTest`, the `retryAfter` table; Level 2 (component) `UpstreamClientThrottleComponentTest`; Level 3 (integration) a `429` pausing the real budget over loopback"
        ],
        "risks": [
          "`retryAfter` may exceed the budget's longest pause, up to what the server asked; a consumer caps its own wait (consequences.md §8)"
        ],
        "source": "plan.md R12, R14, §6.5, §6.6",
        "link": "plan.md#66-classification-and-retryafter"
      }
    },
    {
      "id": "url-fetcher",
      "group": "Code",
      "name": "`UrlFetcher` and its policy pre-check",
      "system": "webtools-http-facades",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "An administrator-supplied URL has no front door: it would reach the transport without a pre-check, so a refused URL would cost a permit, and there is no raw fetch that charges the budget and reads its signals without a decode or a cache.",
        "evidence": [
          "common/UrlGuard.kt:82 — `checkRequest`, `internal`, the pre-check for the facade (baseline)",
          "../consequences.md §8 — a refusal costs no permit and no call, and the facade logs it once",
          "../architecture.md §6.6 — no endpoint, no decode, no cache: an unbounded key space, and a conditional GET is its cache"
        ],
        "source": "../architecture.md §6.6; ../consequences.md §8",
        "link": "../architecture.md#66-call-facades"
      },
      "options": [
        {
          "id": "raw-fetch",
          "name": "A final `UrlFetcher` with `fetch(request, configure)`: the policy check first, then budget, transport, signals and `complete`; every status a response but a throttle; no cache",
          "verdict": "accepted",
          "why": "A refusal reaches neither budget nor transport; an administrator-supplied URL population is an unbounded key space, so its cache is the caller's conditional GET.",
          "source": "../architecture.md §6.6; ../research/api-shape-and-failures.md § The facade",
          "link": "../architecture.md#66-call-facades"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "state": "amended",
            "ref": "final-implementation.md#d10-a-close-race-class-for-urlfetcher",
            "text": "`UrlFetcher` was built as a final `AutoCloseable` with an `internal` constructor, `fetch(request) { }` returning `Result<HttpResponse>`, `close()` and `toString()`; the common factory `UrlFetcher(transport, policy) { }` builds it over a transport it never closes, and the ABI dump shows no `<init>`; a Level 4c non-functional class races its `close()` against in-flight fetches and many closers.",
            "excerpt": {
              "ref": "final-implementation.md#d10-a-close-race-class-for-urlfetcher",
              "heading": "D10. A close-race class for `UrlFetcher`",
              "paragraph": "Departs from: plan §9, Level 4c, which lists two classes, both for `UpstreamClient`. · As built: `UrlFetcherCloseRaceNonFunctionalTest` (2 tests), shaped like `UpstreamClientCloseRaceNonFunctionalTest`. `close racing in-flight fetches ends every fetch with a value and hangs nothing`: over 50 rounds, 16 fetches of a slow fake start together while `close()` runs from another thread at a seeded random point; each returns the response or a `Closed` of the fetcher or the transport, never a thrown cancellation; a fetch after the close reaches nothing; the round's thread pool terminates. `close from …",
              "hash": "6beb98bd8ea1"
            }
          },
          {
            "state": "amended",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
            "text": "`fetch` checks, in order, a closed fetcher, the spec's lambda, the cost and the wait, a controlled header and the policy's pre-check, each returned with no transport call and no permit taken; a table of fetches with two faults each holds the order.",
            "excerpt": {
              "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
              "heading": "D9. Tests and assertions beyond plan §9's list",
              "paragraph": "Departs from: plan §9, which lists each class's tests and what each asserts, and §9.1's totals. · As built (the class's test count, with the plan's where it differs): · `CallFacadesConsumerGatingTest` (3): the four configuration lambdas are compiled and nothing is asserted on them, since a consumer without a transport cannot apply them. · `UpstreamClientConsumerGatingTest` (3): the lambdas are applied. The common factory takes a configuration lambda and the call sets every per-call setting; `baseUrl`, `utilisation()`, `cacheStats()` and `flush()` are each read; the owning factory takes …",
              "hash": "ba0863ea352b"
            }
          },
          {
            "text": "Then the shared dispatch and `fetchVerdictOf`: a throttle is `RateLimited`, and every other status, a `304`, a `404`, a `500` and a returned `3xx` included, is the response, with no cache and no decode; the verdict function's KDoc states its rule where the plan's listing cited a requirement.",
            "state": "amended",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
            "excerpt": {
              "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
              "heading": "D3. The internal KDoc states its rule instead of citing a plan section",
              "paragraph": "Departs from: the code blocks of plan §7.4 (`assembleUrl`), §7.5 (`verdictOf`, `fetchVerdictOf`, `retryAfterOf`), §7.7 (`CallDispatch`, `send`), §7.10 (`UpstreamClientConfig.resolve`) and §7.12 (`sizedFor`), whose KDoc cites \"(§6.3)\", \"(R12)\", \"(R13)\", \"(R14)\", \"(§6.5)\", \"R7\" and \"§6.10\", and the line comments of §7.11 that read \"R8 steps 2 to 6\", \"R8 step 7\" and \"R8 step 8\". · As built: each of those comments states the rule itself (the assembly steps and their failure rules, the classification order, what settles a permit, the construction checks in their order, what each private step of a …",
              "hash": "64959130e060"
            }
          },
          {
            "text": "`UrlFetcherConfig` was built as a `@WebToolsDsl` class defaulting to a caller-owned `CallBudget.Unlimited` the fetcher never closes, `RateSignalsReader.Standard`, `WallClock.System`, `TimeSource.Monotonic` and a `defaultMaxWait` of 5 s, public as `UrlFetcherConfig.DEFAULT_MAX_WAIT`, which since stage 3 refers to the client's constant, as planned.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "The KDoc of `fetchVerdictOf` states the fetcher's classification itself, where the plan's listing cited a requirement number; no executable line differs.",
            "why": "A requirement number of the plan means nothing to a reader of the source, who does not have the plan beside it.",
            "when": "stage 2",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section"
          },
          {
            "what": "`UrlFetcherRequestComponentTest` holds a test the plan did not list: a table of fetches with two faults each, asserting the earlier check's rule and that nothing was spent (the limits before a controlled header, an invalid request before the policy's refusal).",
            "why": "Single-fault tests cannot show the order of the checks the requirement gives.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list"
          },
          {
            "what": "A Level 4c non-functional close-race class for the fetcher, beside the client's.",
            "why": "The fetcher has its own closed flag and closing path, which the client's class does not exercise.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d10-a-close-race-class-for-urlfetcher"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One system: the front door for administrator-supplied URLs, an unbounded key space whose cache is the caller's conditional GET.",
        "statements": [
          {
            "text": "`UrlFetcher` is a final `AutoCloseable` with `fetch(request) { }` returning `Result<HttpResponse>`, built over a caller-owned transport by `UrlFetcher(transport, policy) { }`.",
            "ref": "plan.md#r6-urlfetcherconfig-and-urlfetcher",
            "excerpt": {
              "ref": "plan.md#r6-urlfetcherconfig-and-urlfetcher",
              "heading": "R6. `UrlFetcherConfig` and `UrlFetcher`",
              "paragraph": "`UrlFetcherConfig`: `var budget: CallBudget = CallBudget.Unlimited` (caller-owned; the fetcher owns no budget), `var rateSignals`, `var wallClock`, `var timeSource`, `var defaultMaxWait = DEFAULT_MAX_WAIT` (`UrlFetcherConfig.DEFAULT_MAX_WAIT`, equal to the client's 5 seconds). · `public class UrlFetcher internal constructor(...) : AutoCloseable` with `suspend fun fetch(request: HttpRequest, configure: FetchSpec.() -> Unit = {}): Result<HttpResponse>`, `override fun close()` and `toString()`. · Common factory `UrlFetcher(transport, policy, configure)` (the transport is the caller's; `policy` …",
              "hash": "6354e3b9cb66"
            }
          },
          {
            "text": "A fetch checks, in order, a closed fetcher, the spec, the cost and the wait, a controlled header and the policy's pre-check, so a refusal reaches neither the budget nor the transport.",
            "ref": "plan.md#r13-urlfetcherfetch-in-order",
            "excerpt": {
              "ref": "plan.md#r13-urlfetcherfetch-in-order",
              "heading": "R13. `UrlFetcher.fetch`, in order",
              "paragraph": "Closed → `Closed(\"fetcher\")`; `FetchSpec` applied inside `catchNonFatal` → `InvalidRequest`; `cost` below 1 or a negative `maxWait` → `InvalidRequest`; a controlled header in `request.headers` → `InvalidRequest`; the pre-check `guard.checkRequest(request)` → `RefusedByPolicy` (logged once at `WARN`; no permit, no call); the loader of R11; a throttle → `RateLimited` exactly as R14; every other status, a `304` and a returned `3xx` included, is the response.",
              "hash": "d244545f6b95"
            }
          },
          {
            "text": "Then the shared loader and throttle: a throttle is `RateLimited`, and every other status, a `304` and a returned `3xx` included, is the response; no cache, no decode.",
            "ref": "plan.md#r13-urlfetcherfetch-in-order",
            "excerpt": {
              "ref": "plan.md#r13-urlfetcherfetch-in-order",
              "heading": "R13. `UrlFetcher.fetch`, in order",
              "paragraph": "Closed → `Closed(\"fetcher\")`; `FetchSpec` applied inside `catchNonFatal` → `InvalidRequest`; `cost` below 1 or a negative `maxWait` → `InvalidRequest`; a controlled header in `request.headers` → `InvalidRequest`; the pre-check `guard.checkRequest(request)` → `RefusedByPolicy` (logged once at `WARN`; no permit, no call); the loader of R11; a throttle → `RateLimited` exactly as R14; every other status, a `304` and a returned `3xx` included, is the response.",
              "hash": "d244545f6b95"
            }
          },
          {
            "text": "`UrlFetcherConfig` holds a caller-owned budget, `Unlimited` by default, the signal reader, the clocks and a `defaultMaxWait` of 5 s.",
            "ref": "plan.md#r6-urlfetcherconfig-and-urlfetcher",
            "excerpt": {
              "ref": "plan.md#r6-urlfetcherconfig-and-urlfetcher",
              "heading": "R6. `UrlFetcherConfig` and `UrlFetcher`",
              "paragraph": "`UrlFetcherConfig`: `var budget: CallBudget = CallBudget.Unlimited` (caller-owned; the fetcher owns no budget), `var rateSignals`, `var wallClock`, `var timeSource`, `var defaultMaxWait = DEFAULT_MAX_WAIT` (`UrlFetcherConfig.DEFAULT_MAX_WAIT`, equal to the client's 5 seconds). · `public class UrlFetcher internal constructor(...) : AutoCloseable` with `suspend fun fetch(request: HttpRequest, configure: FetchSpec.() -> Unit = {}): Result<HttpResponse>`, `override fun close()` and `toString()`. · Common factory `UrlFetcher(transport, policy, configure)` (the transport is the caller's; `policy` …",
              "hash": "6354e3b9cb66"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-2",
            "what": "Write `UrlFetcherConfig`, `UrlFetcher` and its common factory with the fetcher's Level 2 classes.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UrlFetcherConfig.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UrlFetcher.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UrlFetcherResponseComponentTest.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UrlFetcherPolicyRefusalComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `UrlFetcherResponseComponentTest`, `UrlFetcherRequestComponentTest`, `UrlFetcherPolicyRefusalComponentTest` and `UrlFetcherConfigComponentTest`; Level 3 (integration) `UrlFetcherLoopbackIntegrationTest`"
        ],
        "risks": [
          "The pre-check applies the fetcher's policy to a fake transport's URLs too, so a test over the fake uses a public-looking host (§12)"
        ],
        "source": "plan.md R6, R13, §6.7",
        "link": "plan.md#r13-urlfetcherfetch-in-order"
      }
    },
    {
      "id": "factories",
      "group": "Code",
      "name": "The common factories and their configs",
      "system": "webtools-http-facades",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing builds a facade from a transport. The defaults it needs exist apart: the pass-through cache and budget, the standard signal reader, the system wall clock and the policies, with no config that gathers them or pins a client to its base URL's host.",
        "evidence": [
          "common/ResponseCache.kt:102 — `ResponseCache.Disabled` (baseline)",
          "common/CallBudget.kt:89 — `CallBudget.Unlimited`",
          "common/OutboundUrlPolicy.kt — `trusted { pinnedHosts }`, the client's default policy",
          "../architecture.md §9 — configurable types by factory and builder over an `internal` constructor, no defaulted parameters on public constructors",
          "research/default-wait-cap.md F1 — the one comparable with a documented finite default, Resilience4j's rate limiter, waits up to 5 s",
          "research/default-wait-cap.md F7 — `Duration.INFINITE` would void the bounds an abandoned shared load and the budget's re-plan rely on"
        ],
        "source": "../architecture.md §6.6, §9; research/README.md § The default wait cap",
        "link": "../architecture.md#9-extension--stability"
      },
      "options": [
        {
          "id": "builder-factories",
          "name": "`UpstreamClient(transport, baseUrl) { }` and `UrlFetcher(transport, policy) { }` with `UpstreamClientConfig` and `UrlFetcherConfig` builders: policy pinned to the base URL's host, clocks, dispatcher, reader, wait cap, default headers, and a caller-owned or a built cache and budget",
          "verdict": "accepted",
          "why": "Additive by construction and binary-safe for Kotlin callers; every number a builder property with today's default. Without a cache the common factory uses `ResponseCache.Disabled`, without a budget `CallBudget.Unlimited`.",
          "source": "../architecture.md §6.6, §9; ../research/api-shape-and-failures.md Considerations 1",
          "link": "../architecture.md#66-call-facades"
        },
        {
          "id": "flat-constructors",
          "name": "Flat constructors with defaulted parameters and hidden deprecated overloads",
          "verdict": "declined",
          "why": "A permanent overload per addition, kept correct by hand; `@IntroducedAt` needs Kotlin 2.3.",
          "source": "../architecture.md §10; ../research/api-shape-and-failures.md Considerations 1",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "zero-wait",
          "name": "A default wait cap of zero",
          "verdict": "declined",
          "why": "It leaves the budget's queue unused by default and fails waits that would clear in a moment.",
          "source": "research/README.md § The default wait cap",
          "link": "research/README.md#the-default-wait-cap"
        },
        {
          "id": "infinite-wait",
          "name": "A default wait cap of `Duration.INFINITE`",
          "verdict": "declined",
          "why": "It voids two bounds the design relies on, an abandoned shared load's and the budget's re-plan's, and would park a daily-window call until UTC midnight.",
          "source": "research/README.md § The default wait cap; research/default-wait-cap.md F7",
          "link": "research/default-wait-cap.md#f7-infinite-voids-the-bounds-the-stacks-design-relies-on-measured"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "changes": [
          {
            "what": "Stage 2 initialised `UrlFetcherConfig.DEFAULT_MAX_WAIT` with its own `5.seconds` literal instead of `UpstreamClientConfig.DEFAULT_MAX_WAIT`; stage 3 restored the planned reference, so as built nothing of it remains but the order in which the two stages landed it.",
            "why": "Each stage compiles and passes on its own, and a stage 2 source cannot name a type stage 3 builds.",
            "when": "stage 2; restored in stage 3",
            "ref": "final-implementation.md#d1-the-fetchers-commit-names-no-type-of-the-clients"
          },
          {
            "what": "The KDoc of `UpstreamClientConfig`'s construction checks states them in their order, where the plan's listing cited a requirement and a design section; no executable line differs.",
            "why": "A requirement or section number of the plan means nothing to a reader of the source, who does not have the plan beside it.",
            "when": "stage 3",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section"
          }
        ],
        "statements": [
          {
            "text": "`UpstreamClientConfig` was built with an `internal` constructor and holds the policy, by default `Trusted` and pinned to the base URL's host, the two clocks, the dispatcher, the signal reader, `defaultMaxWait`, the default headers, and `cache(…)`, `budget(…)` or `budget(windows) { }`, a windowed budget built after the lambda with the client's clocks and owned by the client; the base URL's host is read into a local before the pinned policy's lambda, the remedy the plan named for the DSL marker.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2-the-js-compile-and-the-dump",
            "excerpt": {
              "ref": "final-implementation.md#p2-the-js-compile-and-the-dump",
              "heading": "P2. The `js` compile and the dump",
              "paragraph": "`:webtools-http-commoncheck:build` passes after each of the four code commits (`Channel`, `Job()` and `KClass.simpleName` compile for `js`), and `apiDump` followed, in a separate invocation, by `apiCheck` and `verifyJavaFloor` passes after each. The dump grows by exactly plan §7.16, with no line removed:",
              "hash": "6de2ab4d3d7d"
            }
          },
          {
            "text": "Without `cache(…)` the common client factory uses `ResponseCache.Disabled`, without a budget `CallBudget.Unlimited`, and it never closes the transport.",
            "state": "as-planned"
          },
          {
            "text": "`defaultMaxWait` is 5 s in both facades: `UpstreamClientConfig.DEFAULT_MAX_WAIT`, and `UrlFetcherConfig.DEFAULT_MAX_WAIT` refers to it again, as planned; the KDoc gives a call's worst-case latency as that plus the transport's call timeout.",
            "state": "as-planned",
            "ref": "final-implementation.md#d1-the-fetchers-commit-names-no-type-of-the-clients",
            "excerpt": {
              "ref": "final-implementation.md#d1-the-fetchers-commit-names-no-type-of-the-clients",
              "heading": "D1. The fetcher's commit names no type of the client's",
              "paragraph": "Departs from: plan §7.8 (`UrlFetcherConfig.Companion.DEFAULT_MAX_WAIT`), §7.15 (`FacadeTestSupport.kt`), §9 (`UrlFetcherConfigComponentTest`, its test `DEFAULT_MAX_WAIT equals the client's`) and §11 (commit 2), which put in the fetcher's commit three things that name `UpstreamClientConfig` or `UpstreamClient`, types §5 and §11 build in the next commit. · As built: the fetcher's commit (`0985772`) initialises `UrlFetcherConfig.DEFAULT_MAX_WAIT` with the literal `5.seconds`, asserts only that value in the test, and leaves `clientOn` and `assertNothingSpent` out of `FacadeTestSupport.kt`; the …",
              "hash": "a6ac1dca2cba"
            }
          },
          {
            "text": "The client factory refuses, in order, a negative wait, a base URL with a query or one the policy refuses (naming the reason), a controlled default header and a sensitive one under `Untrusted`, and a refused window list, with a constant message that never echoes a URL or a value; the fetcher factory refuses a negative wait; the KDoc of the checks states them in order where the plan's listing cited a requirement.",
            "state": "amended",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
            "excerpt": {
              "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
              "heading": "D3. The internal KDoc states its rule instead of citing a plan section",
              "paragraph": "Departs from: the code blocks of plan §7.4 (`assembleUrl`), §7.5 (`verdictOf`, `fetchVerdictOf`, `retryAfterOf`), §7.7 (`CallDispatch`, `send`), §7.10 (`UpstreamClientConfig.resolve`) and §7.12 (`sizedFor`), whose KDoc cites \"(§6.3)\", \"(R12)\", \"(R13)\", \"(R14)\", \"(§6.5)\", \"R7\" and \"§6.10\", and the line comments of §7.11 that read \"R8 steps 2 to 6\", \"R8 step 7\" and \"R8 step 8\". · As built: each of those comments states the rule itself (the assembly steps and their failure rules, the classification order, what settles a permit, the construction checks in their order, what each private step of a …",
              "hash": "64959130e060"
            }
          },
          {
            "text": "`UpstreamClientConfig` and `UrlFetcherConfig` carry `@WebToolsDsl`, and the marker's KDoc list and `WebToolsDslComponentTest` name all five new builders.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Every number is a builder property with today's default: additive by construction and binary-safe.",
        "statements": [
          {
            "text": "`UpstreamClientConfig` holds the policy, pinned by default to the base URL's host, the clocks, the dispatcher, the signal reader, `defaultMaxWait` and default headers, and a caller-owned cache or budget, or a budget built from windows and owned by the client.",
            "ref": "plan.md#r4-upstreamclientconfig",
            "excerpt": {
              "ref": "plan.md#r4-upstreamclientconfig",
              "heading": "R4. `UpstreamClientConfig`",
              "paragraph": "`var policy` (default `OutboundUrlPolicy.trusted { pinnedHosts = setOf(baseUrl.host) }`), `var timeSource = TimeSource.Monotonic`, `var wallClock = WallClock.System`, `var dispatcher: CoroutineDispatcher = Dispatchers.Default`, `var rateSignals = RateSignalsReader.Standard`, `var defaultMaxWait = DEFAULT_MAX_WAIT` (5 seconds, `UpstreamClientConfig.DEFAULT_MAX_WAIT`), `var headers = HttpHeaders()`; `fun cache(cache: ResponseCache)` (caller-owned); `fun budget(budget: CallBudget)` (caller-owned); `fun budget(windows: List<RateWindow>, configure: WindowedCallBudget.Builder.() -> Unit = {})` …",
              "hash": "a3e0c712d5cc"
            }
          },
          {
            "text": "Without a cache the common client factory uses `ResponseCache.Disabled`, without a budget `CallBudget.Unlimited`, and it never closes the transport.",
            "ref": "plan.md#r5-upstreamclient",
            "excerpt": {
              "ref": "plan.md#r5-upstreamclient",
              "heading": "R5. `UpstreamClient`",
              "paragraph": "`public class UpstreamClient internal constructor(...) : AutoCloseable` with `val baseUrl: Url`, `suspend fun <T : Any> call(endpoint, configure = {}): Result<Lookup<T>>`, `suspend fun utilisation(): BudgetUtilisation`, `fun cacheStats(): CacheStats`, `suspend fun flush(): Result<Unit>`, `override fun close()` and `toString()`. · The common factory `UpstreamClient(transport: HttpTransport, baseUrl: Url, configure: UpstreamClientConfig.() -> Unit = {})` never closes the transport; without `cache(...)` it uses `ResponseCache.Disabled`, without a budget `CallBudget.Unlimited`; a …",
              "hash": "ef50401da9cf"
            }
          },
          {
            "text": "`defaultMaxWait` is 5 s in both facades, public as `DEFAULT_MAX_WAIT` beside each configuration; the worst-case latency of a call is that plus the transport's call timeout.",
            "ref": "plan.md#610-lifecycle-ownership-construction-and-sizing",
            "excerpt": {
              "ref": "plan.md#610-lifecycle-ownership-construction-and-sizing",
              "heading": "6.10 Lifecycle, ownership, construction and sizing",
              "paragraph": "Ownership is recorded in `Owned(cache, budget, transport)` flags. The common client factory owns only a budget it built from `budget(windows)`; the JVM client factory owns the transport it built, the cache it built (unless `cache(...)` was called) and the budget it built; the fetcher owns its transport when a JVM owning factory (or `LoopbackFetchers`) built it, and never a budget.",
              "hash": "9285c5dab2b6"
            }
          },
          {
            "text": "The client factories refuse a negative wait, a base URL with a query or one the policy refuses, a controlled default header, a sensitive one under `Untrusted` and a refused window list, with a constant message that never echoes a value; the fetcher factories refuse a negative wait.",
            "ref": "plan.md#r7-construction-checks",
            "excerpt": {
              "ref": "plan.md#r7-construction-checks",
              "heading": "R7. Construction checks",
              "paragraph": "Both client factories refuse, with `IllegalArgumentException` and a constant message that never echoes a URL, a header value or a value: a negative `defaultMaxWait`; a `baseUrl` with a query (a bare `?` included); a `baseUrl` that `UrlGuard(policy).checkHop(baseUrl, null)` refuses (the message names the `RefusalReason`); a default header named by `TransportControlledHeaders`; under an `Untrusted` policy a default header that is sensitive; and what a budget built from `budget(windows)` refuses. Both fetcher factories refuse a negative `defaultMaxWait`. A refused construction leaves nothing …",
              "hash": "4e7eee61c2dd"
            }
          },
          {
            "text": "`UpstreamClientConfig` and `UrlFetcherConfig` carry `@WebToolsDsl` and join the marker's KDoc list and its component test.",
            "ref": "plan.md#r1-the-public-surface-and-its-tier",
            "excerpt": {
              "ref": "plan.md#r1-the-public-surface-and-its-tier",
              "heading": "R1. The public surface and its tier",
              "paragraph": "`webtools-http` gains these public declarations in `com.spartanlabs.webtools.http`, every one final, Stable Core and usable without any opt-in. No new type is gated by `ExperimentalForInheritanceWebToolsApi` and no new seam is added: the facades *call* the existing ports and never expose one to implement.",
              "hash": "cb16ee31c6ec"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-2",
            "what": "Write the fetcher's configuration and common factory.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UrlFetcherConfig.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/UrlFetcherConfigComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-3",
            "what": "Write the client's configuration and common factory with their construction checks.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UpstreamClientConfig.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientConfigComponentTest.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientConstructionComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`; Procedure P2",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `UpstreamClientConfigComponentTest`, `UrlFetcherConfigComponentTest` and `UpstreamClientConstructionComponentTest`; Level 1 (gating) `CallFacadesConsumerGatingTest`, both `DEFAULT_MAX_WAIT` 5 s"
        ],
        "risks": [
          "5 s is a judgement anchored on Resilience4j's default and on clearing a one-second window and its guard, not a measurement (consequences.md §8)",
          "`dispatcher` has no effect when the client owns no cache; the KDoc says so (consequences.md §8)"
        ],
        "source": "plan.md R4, R5, R7, §6.10",
        "link": "plan.md#r4-upstreamclientconfig"
      }
    },
    {
      "id": "owning-factories",
      "group": "Code",
      "name": "The JVM owning factories and the transport sized to `maxBurst`",
      "system": "webtools-http-facades",
      "where": "jvm/ (new)",
      "gap": {
        "kind": "risk",
        "summary": "A JVM consumer has to wire the OkHttp transport, the Caffeine cache and the windowed budget by hand. A budget can grant its smallest window in one instant, and OkHttp's queue beyond the transport's default concurrency is invisible to the call timeout and the wait cap.",
        "evidence": [
          "jvm/OkHttpTransport.kt:232 — the transport's factory (baseline)",
          "jvm/CaffeineResponseCache.kt:316 — the cache's",
          "common/WindowedCallBudget.kt:616 — the budget's; `:136` its `maxBurst`",
          "common/OutboundUrlPolicy.kt:274 — `withSettings`, `internal`, to raise the concurrency",
          "../research/call-budget.md Considerations 2 — burst against the dispatcher",
          "research/README.md § Sizing the transport from the budget's burst — OkHttp's call timeout starts only when a dispatcher thread runs a queued call, and with one host the per-host limit binds (#55's transport findings F7 and F9, checked again)"
        ],
        "source": "../architecture.md §6.6; ../research/call-budget.md Considerations 2",
        "link": "../architecture.md#66-call-facades"
      },
      "options": [
        {
          "id": "owning-factories",
          "name": "`UpstreamClient(baseUrl, userAgent) { }` builds and owns an `OkHttpTransport`, a `CaffeineResponseCache` and, when windows are set, a `WindowedCallBudget`, raising the transport's concurrency to `maxBurst`; `UrlFetcher(userAgent) { }` builds an `Untrusted` transport and uses `Unlimited`",
          "verdict": "accepted",
          "why": "The common thing works out of the box; threads are made on demand, so sizing the dispatcher to the burst costs little.",
          "source": "../architecture.md §6.6; ../research/call-budget.md Considerations 2",
          "link": "../architecture.md#66-call-facades"
        },
        {
          "id": "spacing-or-semaphore",
          "name": "A `minSpacing` between grants, or a `maxConcurrent` semaphore in the budget",
          "verdict": "declined",
          "why": "Interactive grants would need slots between planned tracker slots, and a semaphore a lease timeout for permits never completed; deferred until a consumer needs them.",
          "source": "../research/call-budget.md Considerations 2"
        },
        {
          "id": "total-only",
          "name": "Raising `maxConcurrentRequests` alone, as #55's architecture §6.3.2 and §7.4 name it",
          "verdict": "declined",
          "why": "A client talks to one host, where OkHttp's per-host limit of 5 binds, so a granted permit would still queue outside the call timeout and the wait cap.",
          "source": "plan.md §6.10; research/README.md § Sizing the transport from the budget's burst",
          "link": "plan.md#610-lifecycle-ownership-construction-and-sizing"
        },
        {
          "id": "sizing-ceiling",
          "name": "A ceiling on the sized limits",
          "verdict": "declined",
          "why": "It would recreate the OkHttp queue the sizing removes; a consumer who wants a smaller transport passes its own to the common factory.",
          "source": "plan.md §6.10, §10",
          "link": "plan.md#610-lifecycle-ownership-construction-and-sizing"
        },
        {
          "id": "fetcher-sizing",
          "name": "Sizing `UrlFetcher`'s transport, or a budget it owns",
          "verdict": "declined",
          "why": "An `Untrusted` policy's concurrency caps are part of what makes it untrusted.",
          "source": "plan.md §4",
          "link": "plan.md#4-non-goals"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "text": "`UpstreamClient(baseUrl, userAgent) { }` was built in the JVM source set and owns what it builds: an `OkHttpTransport`, a `CaffeineResponseCache` on the configuration's clock and dispatcher unless `cache(…)` was called, and the `WindowedCallBudget` of `budget(windows)`; the dump gained `UpstreamClientOwningKt`.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2-the-js-compile-and-the-dump",
            "excerpt": {
              "ref": "final-implementation.md#p2-the-js-compile-and-the-dump",
              "heading": "P2. The `js` compile and the dump",
              "paragraph": "`:webtools-http-commoncheck:build` passes after each of the four code commits (`Channel`, `Job()` and `KClass.simpleName` compile for `js`), and `apiDump` followed, in a separate invocation, by `apiCheck` and `verifyJavaFloor` passes after each. The dump grows by exactly plan §7.16, with no line removed:",
              "hash": "6de2ab4d3d7d"
            }
          },
          {
            "text": "`sizedFor` raises both `maxConcurrentRequests` and `maxConcurrentRequestsPerHost` to at least a `WindowedCallBudget`'s `maxBurst`, built or supplied, never lowering either and with no ceiling, and passes the policy unchanged for any other budget; its KDoc states the rule where the plan's listing cited a design section. With the per-host line removed the burst test failed, 5 calls in flight where 8 were expected, and passed once the line was restored.",
            "state": "amended",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
            "excerpt": {
              "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section",
              "heading": "D3. The internal KDoc states its rule instead of citing a plan section",
              "paragraph": "Departs from: the code blocks of plan §7.4 (`assembleUrl`), §7.5 (`verdictOf`, `fetchVerdictOf`, `retryAfterOf`), §7.7 (`CallDispatch`, `send`), §7.10 (`UpstreamClientConfig.resolve`) and §7.12 (`sizedFor`), whose KDoc cites \"(§6.3)\", \"(R12)\", \"(R13)\", \"(R14)\", \"(§6.5)\", \"R7\" and \"§6.10\", and the line comments of §7.11 that read \"R8 steps 2 to 6\", \"R8 step 7\" and \"R8 step 8\". · As built: each of those comments states the rule itself (the assembly steps and their failure rules, the classification order, what settles a permit, the construction checks in their order, what each private step of a …",
              "hash": "64959130e060"
            }
          },
          {
            "text": "It builds the budget first, then the transport, then the cache, and when a step throws it closes what it had built, in the order cache, budget, transport, before rethrowing: a refused user agent leaves only the closed budget, and a refused configuration builds nothing.",
            "state": "as-planned"
          },
          {
            "state": "amended",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
            "text": "`UrlFetcher(userAgent) { }` builds and owns a transport on `OutboundUrlPolicy.untrusted()`, and `UrlFetcher(userAgent, policy) { }` one on the caller's policy as it is; each checks its configuration before it builds the transport, and neither sizes it or owns a budget; the dump gained `UrlFetcherOwningKt`. Tests the plan did not list fetch through the policy overload over a real exchange and refuse a negative wait in both before any transport is built.",
            "excerpt": {
              "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
              "heading": "D9. Tests and assertions beyond plan §9's list",
              "paragraph": "Departs from: plan §9, which lists each class's tests and what each asserts, and §9.1's totals. · As built (the class's test count, with the plan's where it differs): · `CallFacadesConsumerGatingTest` (3): the four configuration lambdas are compiled and nothing is asserted on them, since a consumer without a transport cannot apply them. · `UpstreamClientConsumerGatingTest` (3): the lambdas are applied. The common factory takes a configuration lambda and the call sets every per-call setting; `baseUrl`, `utilisation()`, `cacheStats()` and `flush()` are each read; the owning factory takes …",
              "hash": "ba0863ea352b"
            }
          }
        ],
        "changes": [
          {
            "what": "The KDoc of `sizedFor` states the sizing rule itself, where the plan's listing cited a design section; no executable line differs.",
            "why": "A section number of the plan means nothing to a reader of the source, who does not have the plan beside it.",
            "when": "stage 4",
            "ref": "final-implementation.md#d3-the-internal-kdoc-states-its-rule-instead-of-citing-a-plan-section"
          },
          {
            "what": "Tests the plan did not list for the owning fetcher factories: the policy overload under a `Trusted` cleartext policy fetches from a loopback server and closes its transport, a negative `defaultMaxWait` through either builds no transport, and the gating test applies the client's `budget(windows)` and fetches through an owning `UrlFetcher`.",
            "why": "No listed test carried a real exchange through the public owning fetcher factory or its refusal before anything is built.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The common thing works out of the box on the JVM, and a granted permit never waits where neither the call timeout nor the wait cap can see it.",
        "statements": [
          {
            "text": "`UpstreamClient(baseUrl, userAgent) { }` builds and owns an `OkHttpTransport`, a `CaffeineResponseCache` unless `cache(…)` was called, and the `WindowedCallBudget` of `budget(windows)`.",
            "ref": "plan.md#r5-upstreamclient",
            "excerpt": {
              "ref": "plan.md#r5-upstreamclient",
              "heading": "R5. `UpstreamClient`",
              "paragraph": "`public class UpstreamClient internal constructor(...) : AutoCloseable` with `val baseUrl: Url`, `suspend fun <T : Any> call(endpoint, configure = {}): Result<Lookup<T>>`, `suspend fun utilisation(): BudgetUtilisation`, `fun cacheStats(): CacheStats`, `suspend fun flush(): Result<Unit>`, `override fun close()` and `toString()`. · The common factory `UpstreamClient(transport: HttpTransport, baseUrl: Url, configure: UpstreamClientConfig.() -> Unit = {})` never closes the transport; without `cache(...)` it uses `ResponseCache.Disabled`, without a budget `CallBudget.Unlimited`; a …",
              "hash": "ef50401da9cf"
            }
          },
          {
            "text": "When the client's budget is a `WindowedCallBudget`, both `maxConcurrentRequests` and `maxConcurrentRequestsPerHost` are raised to at least its `maxBurst`, never lowered and with no ceiling; otherwise the policy is passed as it is.",
            "ref": "plan.md#r16-owning-factories-and-sizing",
            "excerpt": {
              "ref": "plan.md#r16-owning-factories-and-sizing",
              "heading": "R16. Owning factories and sizing",
              "paragraph": "Construction order of `UpstreamClient(baseUrl, userAgent)`: the pure checks of R7, then the budget (when built from windows), then the transport, then the cache; if a step throws, what was already built is closed (cache, budget, transport order) before the exception propagates. A `userAgent` the transport refuses builds nothing that stays open. · When the client's budget is a `WindowedCallBudget` (built or supplied), the transport's policy is `policy.withSettings(TransportSettings(policy.settings) { maxConcurrentRequests = maxOf(maxConcurrentRequests, burst); maxConcurrentRequestsPerHost = …",
              "hash": "230965033462"
            }
          },
          {
            "text": "It builds the budget first, as the sizing needs its burst, then the transport, then the cache, and closes what it built before rethrowing when a step fails.",
            "ref": "plan.md#r16-owning-factories-and-sizing",
            "excerpt": {
              "ref": "plan.md#r16-owning-factories-and-sizing",
              "heading": "R16. Owning factories and sizing",
              "paragraph": "Construction order of `UpstreamClient(baseUrl, userAgent)`: the pure checks of R7, then the budget (when built from windows), then the transport, then the cache; if a step throws, what was already built is closed (cache, budget, transport order) before the exception propagates. A `userAgent` the transport refuses builds nothing that stays open. · When the client's budget is a `WindowedCallBudget` (built or supplied), the transport's policy is `policy.withSettings(TransportSettings(policy.settings) { maxConcurrentRequests = maxOf(maxConcurrentRequests, burst); maxConcurrentRequestsPerHost = …",
              "hash": "230965033462"
            }
          },
          {
            "text": "`UrlFetcher(userAgent) { }` builds and owns an `Untrusted` transport and `UrlFetcher(userAgent, policy) { }` one on the deployment's own policy; neither sizes it or owns a budget.",
            "ref": "plan.md#r6-urlfetcherconfig-and-urlfetcher",
            "excerpt": {
              "ref": "plan.md#r6-urlfetcherconfig-and-urlfetcher",
              "heading": "R6. `UrlFetcherConfig` and `UrlFetcher`",
              "paragraph": "`UrlFetcherConfig`: `var budget: CallBudget = CallBudget.Unlimited` (caller-owned; the fetcher owns no budget), `var rateSignals`, `var wallClock`, `var timeSource`, `var defaultMaxWait = DEFAULT_MAX_WAIT` (`UrlFetcherConfig.DEFAULT_MAX_WAIT`, equal to the client's 5 seconds). · `public class UrlFetcher internal constructor(...) : AutoCloseable` with `suspend fun fetch(request: HttpRequest, configure: FetchSpec.() -> Unit = {}): Result<HttpResponse>`, `override fun close()` and `toString()`. · Common factory `UrlFetcher(transport, policy, configure)` (the transport is the caller's; `policy` …",
              "hash": "6354e3b9cb66"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-4",
            "what": "Write the owning factories and the transport sizing, with the sizing table and the Level 3 integration burst test.",
            "files": [
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/TransportSizing.kt",
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/UpstreamClientOwning.kt",
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/UrlFetcherOwning.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/UpstreamClientBurstSizingIntegrationTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`, `integrationTest`; Procedure P3, the sizing mutation",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4a (deterministic) `TransportSizingDeterministicTest`; Level 3 (integration) `UpstreamClientBurstSizingIntegrationTest` with its unsized control, and `UpstreamClientOwningFactoryIntegrationTest`; Level 1 (gating) `UpstreamClientConsumerGatingTest`"
        ],
        "risks": [
          "With no ceiling, a budget whose smallest window is large raises both limits to it, and the worst-case memory is concurrent requests times `maxResponseBytes` (§10)"
        ],
        "source": "plan.md R5, R6, R16, §6.10, §6.11",
        "link": "plan.md#r16-owning-factories-and-sizing"
      }
    },
    {
      "id": "lifecycle",
      "group": "Code",
      "name": "Ownership and the closing order",
      "system": "webtools-http-facades",
      "where": "common/ and jvm/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Each part closes on its own, and nothing closes them together or knows which were built and which were handed in. A client-owned budget writes nothing on `close()`, so its exact final write needs a `flush()` first.",
        "evidence": [
          "../67-call-budget/consequences.md §4 — `close()` non-suspending and idempotent, second in the closing order; `flush()` before it",
          "../66-response-cache/consequences.md §4 — the cache closed first; one passed in never closed by the client",
          "../research/api-shape-and-failures.md F9 — Ktor's `manageEngine`: close what you built"
        ],
        "source": "../architecture.md §6.6; ../research/api-shape-and-failures.md § Lifecycle",
        "link": "../architecture.md#66-call-facades"
      },
      "options": [
        {
          "id": "close-what-it-built",
          "name": "Every facade a `kotlin.AutoCloseable` with a non-suspend, idempotent, non-throwing `close()` that closes what it built, in the order cache, budget, transport, and nothing it was handed",
          "verdict": "accepted",
          "why": "Ownership follows construction; one injected clock and dispatcher reach every owned part, so one virtual clock drives all three under test.",
          "source": "../architecture.md §6.6; ../research/api-shape-and-failures.md F9, F11",
          "link": "../architecture.md#66-call-facades"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": null,
        "statements": [
          {
            "text": "`close()` on either facade was built non-suspending, idempotent, never throwing and safe from any thread, a `Job` completed once standing as the closed flag.",
            "state": "as-planned"
          },
          {
            "text": "It closes what the facade built in the order cache, budget, transport, a step that throws skipped while the others still run, then logs `closed`: the owning factory's client closes all three in that order, and a cache it was handed is not closed and stays usable; a `CancellationException` from a part's `close()` would end the order there, a known gap no part the facade builds reaches.",
            "state": "as-planned",
            "ref": "final-implementation.md#3-acceptance",
            "excerpt": {
              "ref": "final-implementation.md#3-acceptance",
              "heading": "3. Acceptance",
              "paragraph": "The four criteria of plan §1 (\"Acceptance\"):",
              "hash": "a6f4b18a8008"
            }
          },
          {
            "text": "A call after `close()` returns the facade's own `Closed` and reaches nothing, a call in flight ends with a component's `Closed`, and `utilisation()`, `cacheStats()` and `flush()` keep working.",
            "state": "as-planned"
          },
          {
            "text": "`UpstreamClient.flush()` reaches a client-owned budget's store before `close()` and still works after it.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Ownership follows construction: a facade closes what it built and nothing it was handed.",
        "statements": [
          {
            "text": "`close()` on either facade is non-suspending, idempotent, never throws and is safe from any thread, a `Job` completed once standing as the closed flag.",
            "ref": "plan.md#r15-lifecycle",
            "excerpt": {
              "ref": "plan.md#r15-lifecycle",
              "heading": "R15. Lifecycle",
              "paragraph": "`close()` on either facade is non-suspending, idempotent, never throws and safe from any thread: a compare-and-set closed flag; then the facade closes what it built, in the order cache, budget, transport, each inside `catchNonFatal` (a failure logged at `DEBUG` with its class name), and logs `closed` at `INFO`. A call in flight when `close()` runs ends with whichever component's `Closed` it meets; a call after it returns the facade's own `Closed`. `utilisation()`, `cacheStats()` and `flush()` keep working after `close()`.",
              "hash": "5ffb239bdc29"
            }
          },
          {
            "text": "It closes what the facade built, in the order cache, budget, transport, each step inside `catchNonFatal`, then logs `closed`; a component passed in is never closed.",
            "ref": "plan.md#r15-lifecycle",
            "excerpt": {
              "ref": "plan.md#r15-lifecycle",
              "heading": "R15. Lifecycle",
              "paragraph": "`close()` on either facade is non-suspending, idempotent, never throws and safe from any thread: a compare-and-set closed flag; then the facade closes what it built, in the order cache, budget, transport, each inside `catchNonFatal` (a failure logged at `DEBUG` with its class name), and logs `closed` at `INFO`. A call in flight when `close()` runs ends with whichever component's `Closed` it meets; a call after it returns the facade's own `Closed`. `utilisation()`, `cacheStats()` and `flush()` keep working after `close()`.",
              "hash": "5ffb239bdc29"
            }
          },
          {
            "text": "A call in flight ends with whichever component's `Closed` it meets, and a call after it returns the facade's own `Closed`; `utilisation()`, `cacheStats()` and `flush()` keep working.",
            "ref": "plan.md#r15-lifecycle",
            "excerpt": {
              "ref": "plan.md#r15-lifecycle",
              "heading": "R15. Lifecycle",
              "paragraph": "`close()` on either facade is non-suspending, idempotent, never throws and safe from any thread: a compare-and-set closed flag; then the facade closes what it built, in the order cache, budget, transport, each inside `catchNonFatal` (a failure logged at `DEBUG` with its class name), and logs `closed` at `INFO`. A call in flight when `close()` runs ends with whichever component's `Closed` it meets; a call after it returns the facade's own `Closed`. `utilisation()`, `cacheStats()` and `flush()` keep working after `close()`.",
              "hash": "5ffb239bdc29"
            }
          },
          {
            "text": "`UpstreamClient.flush()` is the exact final write of a client-owned budget with a persistent store, called before `close()`.",
            "ref": "plan.md#611-where-this-plan-refines-the-parent-architecture",
            "excerpt": {
              "ref": "plan.md#611-where-this-plan-refines-the-parent-architecture",
              "heading": "6.11 Where this plan refines the parent architecture",
              "paragraph": "None of these changes the unit's scope, a seam or its tier; each makes `../architecture.md` §6.6 concrete against the code as built. Each becomes a dated correction there when the unit lands (commit 7 of §11).",
              "hash": "28bd90da64b9"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-2",
            "what": "Write the closing helper and the fetcher's `close()`.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/FacadeLifecycle.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UrlFetcherLifecycleComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-3",
            "what": "The client's `close()` and `flush()`.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientLifecycleComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-4",
            "what": "The owning factories' ownership, and their clean-up when a construction fails.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/UpstreamClientOwningFactoryIntegrationTest.kt"
            ],
            "check": "`:webtools-http:integrationTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `UrlFetcherLifecycleComponentTest` and `UpstreamClientLifecycleComponentTest`; Level 3 (integration) the owning factory's closing order in its logs; Level 4c (non-functional) `UpstreamClientCloseRaceNonFunctionalTest`"
        ],
        "risks": [
          "An `Error` while an owning factory builds is not cleaned up; nothing recovers from it (§3, assumption 6)"
        ],
        "source": "plan.md R15, R16, §6.9, §6.10",
        "link": "plan.md#r15-lifecycle"
      }
    },
    {
      "id": "logging",
      "group": "Code",
      "name": "The facades' logging",
      "system": "webtools-http-facades",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Each part logs on its own logger and none logs a call: no line says, per call, the endpoint, whether it was a hit, a miss or a join, the lane, the cost, the wait and the status. The cache port reports nothing per call, so a call cannot tell a hit from a join.",
        "evidence": [
          "common/Log.kt:17 — `logger`, the `internal` seam (baseline)",
          "../66-response-cache/consequences.md §4 — the port reports nothing per call; only the loader running tells the facade it started the load",
          "../architecture.md §6.6 — the log levels and what is never logged"
        ],
        "source": "../architecture.md §6.6",
        "link": "../architecture.md#66-call-facades"
      },
      "options": [
        {
          "id": "log-seam",
          "name": "Through the module's logging seam: `INFO` on create and close, `WARN` on a pre-check refusal and an upstream throttle, one `DEBUG` line per call; never headers, bodies, query strings, credentials or cache-key values",
          "verdict": "accepted",
          "why": "The site that creates a failure or an event logs it once; the per-call line is settled against what the cache port can tell.",
          "source": "../architecture.md §6.6",
          "link": "../architecture.md#66-call-facades"
        },
        {
          "id": "kotlin-logging",
          "name": "kotlin-logging in `commonMain`",
          "verdict": "declined",
          "why": "A startup line and a runtime dependency on every consumer, with no gain while one target is published.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "hit-miss-joined",
          "name": "A per-call line naming a hit, a miss or a join, as #55's architecture §6.6 listed",
          "verdict": "declined",
          "why": "The cache port reports nothing per call, so only the loader running tells the facade it started the load: the line reports `loaded`.",
          "source": "plan.md §6.8, §6.11; ../66-response-cache/consequences.md §4",
          "link": "plan.md#68-logging"
        }
      ],
      "asBuilt": {
        "state": "deviated",
        "summary": null,
        "statements": [
          {
            "text": "Two loggers, `UpstreamClient` and `UrlFetcher`, through the module's seam: `INFO` on creation and close, one `DEBUG` line per call, and `WARN` for a pre-check refusal, a throttle and a throwing reader or `utilisation()`; the fetcher's `DEBUG` invalid-request line and `WARN` throttle line are held by two tests the test plan did not list.",
            "state": "amended",
            "ref": "final-implementation.md#d5-two-tests-for-the-fetchers-log-lines",
            "excerpt": {
              "ref": "final-implementation.md#d5-two-tests-for-the-fetchers-log-lines",
              "heading": "D5. Two tests for the fetcher's log lines",
              "paragraph": "Departs from: plan §9, which lists no test for two rows of plan §6.8 (requirement R17): the fetcher's `DEBUG` line `invalid request: rule={}` and its `WARN` line `throttled: subject={}, status={}, retryAfter={}`. · As built: `UrlFetcherRequestComponentTest` has `an InvalidRequest is logged once at DEBUG with its rule and no value` (the lines `invalid request: rule=cost must be at least 1` and `invalid request: rule=header User-Agent is controlled by the transport`, nothing else from the fetcher but its creation and close, and neither the header's value nor the URL's query in any line); …",
              "hash": "2dd519663bb0"
            }
          },
          {
            "state": "amended",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
            "text": "The client's per-call `DEBUG` line gives the endpoint id, the method, `loaded` (whether this call's own loader ran, `false` on a hit or a join), the lane, the cost, the wait, the status, the bytes, the elapsed time and the outcome; tests the plan did not list hold a declared miss's outcome, the dashes a failed transport logs, and an anonymous budget's kind as `custom`.",
            "excerpt": {
              "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
              "heading": "D9. Tests and assertions beyond plan §9's list",
              "paragraph": "Departs from: plan §9, which lists each class's tests and what each asserts, and §9.1's totals. · As built (the class's test count, with the plan's where it differs): · `CallFacadesConsumerGatingTest` (3): the four configuration lambdas are compiled and nothing is asserted on them, since a consumer without a transport cannot apply them. · `UpstreamClientConsumerGatingTest` (3): the lambdas are applied. The common factory takes a configuration lambda and the call sets every per-call setting; `baseUrl`, `utilisation()`, `cacheStats()` and `flush()` are each read; the owning factory takes …",
              "hash": "ba0863ea352b"
            }
          },
          {
            "text": "No line carries a header value, a body, a query string, a credential or a decoded value, and URLs are printed through `Url.redacted()`, path shown and query not, so the client's `WARN` line for a policy refusal prints the request path, a parameter of the cache key, where the plan's rule said no line carries a cache-key value; a Level 4c non-functional class drives five sentinels through every outcome of both facades and finds none in any log event, failure text or `toString()`.",
            "state": "deviated",
            "ref": "final-implementation.md#d14-the-clients-refusal-line-prints-the-request-path",
            "excerpt": {
              "ref": "final-implementation.md#d14-the-clients-refusal-line-prints-the-request-path",
              "heading": "D14. The client's refusal line prints the request path",
              "paragraph": "Departs from: plan R17, and the sentence under plan §6.8's table: \"Never a header value, a body, a query string, a credential, a cache-key value or a decoded value\". · As built: the client's `WARN` line for a pre-check refusal prints `url=` with `Url.redacted()`, so the request path, a parameter of the cache key, appears in it; the query is never printed. No other per-call line of the client prints a URL. · Why: plan §6.8's table gives that line's exact text, `refused by policy: endpoint={}, reason={}, rangeClass={}, url={}` with `Url.redacted()`, as `../architecture.md` §6.6 does for every …",
              "hash": "102c45393456"
            }
          },
          {
            "state": "amended",
            "ref": "final-implementation.md#d8-the-decode-failure-test-asserts-one-decode-failed-line-per-load",
            "text": "A failure passed through is not logged again: an `InvalidRequest` and a `DecodeFailure` each get one `DEBUG` line where they arise, one per load, so the decode-failure test, which loads twice, asserts two; the cache, the budget and the transport keep their own lines.",
            "excerpt": {
              "ref": "final-implementation.md#d8-the-decode-failure-test-asserts-one-decode-failed-line-per-load",
              "heading": "D8. The decode-failure test asserts one `decode failed` line per load",
              "paragraph": "Departs from: plan §9, `UpstreamClientClassificationComponentTest`'s `a decode failure is wrapped in DecodeFailure and never stored`, which ends \"one DEBUG line with the cause's class name\". · As built: the test asserts two `decode failed: endpoint=hero, cause=IllegalStateException` lines. · Why: the same test calls twice to show that the failure is not stored; each load decodes, fails and logs its line.",
              "hash": "12b2049d4a8f"
            }
          }
        ],
        "changes": [
          {
            "what": "Two tests added for the fetcher's `DEBUG` invalid-request line and its `WARN` throttle line, one in each of `UrlFetcherRequestComponentTest` and `UrlFetcherResponseComponentTest`, capturing the fetcher's own logger.",
            "why": "The logging requirement asks for both lines and every requirement has a test, but the test plan listed none for these two.",
            "when": "stage 3",
            "ref": "final-implementation.md#d5-two-tests-for-the-fetchers-log-lines"
          },
          {
            "what": "The decode-failure test asserts two `decode failed` lines, one per load, where its bullet in the test plan said one.",
            "why": "The same test calls twice to show the failure is not stored, and each load decodes, fails and logs its line.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d8-the-decode-failure-test-asserts-one-decode-failed-line-per-load"
          },
          {
            "what": "Tests the test plan did not list for the client's log lines: a declared miss logs outcome `Missing`, a failed transport logs dashes for the wait, the status and the size, an anonymous budget logs its kind as `custom`, and a refused call logs no `call:` line; the fetcher's failed fetch logs the same dashes.",
            "why": "The logging requirement's rows had lines no listed test asserted.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list"
          },
          {
            "what": "A Level 4c non-functional secrecy class for both facades holds the logging rule across every outcome, where the test plan asserted it at Level 2 component on chosen lines only.",
            "why": "The rule covers every line and text the facades produce.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d11-a-secrecy-class-for-the-call-facades"
          },
          {
            "what": "The client's `WARN` line for a policy refusal prints `url=` through `Url.redacted()`, so the request path, a parameter of the cache key, appears in it; the query is never printed, and no other per-call line of the client prints a URL.",
            "why": "The plan's logging table gives that line's exact text with `Url.redacted()`, as #55's architecture does for every policy refusal, and the sentence under the table forbids a cache-key value: the two disagree, and the build follows the table. The path is what locates a refused request; the query, the part of a key that can carry a credential, stays out. The client's class KDoc states it.",
            "when": "stage 3; recorded at the plan's reconciliation",
            "ref": "final-implementation.md#d14-the-clients-refusal-line-prints-the-request-path",
            "link": "../architecture.md#66-call-facades"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The site that creates a failure or an event logs it once, and no line a facade writes carries a secret.",
        "statements": [
          {
            "text": "Two loggers, `UpstreamClient` and `UrlFetcher`, through the module's seam: `INFO` on creation and close, one `DEBUG` line per call, and `WARN` for a pre-check refusal, a throttle and a throwing reader or `utilisation()`.",
            "ref": "plan.md#r17-logging",
            "excerpt": {
              "ref": "plan.md#r17-logging",
              "heading": "R17. Logging",
              "paragraph": "Loggers `com.spartanlabs.webtools.http.UpstreamClient` and `com.spartanlabs.webtools.http.UrlFetcher`: `INFO` on creation and on `close()`; one `DEBUG` line per call; `WARN` for a pre-check refusal, a throttle and a throwing reader or `utilisation()`; `DEBUG` for an `InvalidRequest` the facade creates and a `DecodeFailure`. Never a header value, a body, a query string, a credential, a cache-key value or a decoded value. The site that creates a failure logs it once; a failure passed through is not logged again. Exact texts: §6.8.",
              "hash": "b6ab52c1c41b"
            }
          },
          {
            "text": "The per-call line reports whether the call's own loader ran, with its lane, cost, wait, status, bytes and outcome.",
            "ref": "plan.md#68-logging",
            "excerpt": {
              "ref": "plan.md#68-logging",
              "heading": "6.8 Logging",
              "paragraph": "Loggers `com.spartanlabs.webtools.http.UpstreamClient` (constant `UPSTREAM_CLIENT_LOGGER_NAME`) and `com.spartanlabs.webtools.http.UrlFetcher` (`URL_FETCHER_LOGGER_NAME`), through `logger(name)`. Placeholders are slf4j `{}`; `-` stands for an unknown figure.",
              "hash": "796e75c6897f"
            }
          },
          {
            "text": "Never a header value, a body, a query string, a credential, a cache-key value or a decoded value; URLs are logged redacted.",
            "ref": "plan.md#r17-logging",
            "excerpt": {
              "ref": "plan.md#r17-logging",
              "heading": "R17. Logging",
              "paragraph": "Loggers `com.spartanlabs.webtools.http.UpstreamClient` and `com.spartanlabs.webtools.http.UrlFetcher`: `INFO` on creation and on `close()`; one `DEBUG` line per call; `WARN` for a pre-check refusal, a throttle and a throwing reader or `utilisation()`; `DEBUG` for an `InvalidRequest` the facade creates and a `DecodeFailure`. Never a header value, a body, a query string, a credential, a cache-key value or a decoded value. The site that creates a failure logs it once; a failure passed through is not logged again. Exact texts: §6.8.",
              "hash": "b6ab52c1c41b"
            }
          },
          {
            "text": "A failure passed through is not logged again; the cache, the budget and the transport keep their own lines.",
            "ref": "plan.md#68-logging",
            "excerpt": {
              "ref": "plan.md#68-logging",
              "heading": "6.8 Logging",
              "paragraph": "Loggers `com.spartanlabs.webtools.http.UpstreamClient` (constant `UPSTREAM_CLIENT_LOGGER_NAME`) and `com.spartanlabs.webtools.http.UrlFetcher` (`URL_FETCHER_LOGGER_NAME`), through `logger(name)`. Placeholders are slf4j `{}`; `-` stands for an unknown figure.",
              "hash": "796e75c6897f"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-2",
            "what": "The fetcher's lines.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UrlFetcher.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-3",
            "what": "The client's lines.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UpstreamClient.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientLoggingComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `UpstreamClientLoggingComponentTest`, and the refusal and throttle lines in the policy-refusal and throttle classes"
        ],
        "source": "plan.md R17, §6.8",
        "link": "plan.md#68-logging"
      }
    },
    {
      "id": "loopback-fetchers",
      "group": "Tests",
      "name": "The fixture `LoopbackFetchers`",
      "system": "webtools-http-facades",
      "where": "jvmTestFixtures (new)",
      "gap": {
        "kind": "missing",
        "summary": "A consumer's Level 3 integration test cannot run `UrlFetcher` over a loopback server: its `Untrusted` policy refuses loopback, and only the transport has a loopback relaxation.",
        "evidence": [
          "webtools-http/src/jvmTestFixtures/.../LoopbackTransports.kt:32 — `untrusted`, and `:49` `trusted`, the transports relaxed for loopback (baseline)",
          "plan.md §6.11 — #55's architecture §6.7 lists no fixture for the facades; the plan adds this one"
        ],
        "source": "plan.md R19, §6.11",
        "link": "plan.md#r19-the-fixture-loopbackfetchers"
      },
      "options": [
        {
          "id": "loopback-fetcher",
          "name": "`LoopbackFetchers.untrusted` and `trusted`: a `UrlFetcher` that owns the matching loopback transport and pre-checks with the same relaxation",
          "verdict": "accepted",
          "why": "Every other `Untrusted` rule stays live, so the feed fetcher's Level 3 integration test runs over a loopback server, and the public owning factory still refuses loopback.",
          "source": "plan.md R19, §6.7",
          "link": "plan.md#r19-the-fixture-loopbackfetchers"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "state": "amended",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
            "text": "`LoopbackFetchers.untrusted(…)` builds a `UrlFetcher` that owns a `LoopbackTransports` untrusted transport and pre-checks with the same policy relaxed by `GuardRelaxation.LoopbackForTests`; `trusted(…)` forces cleartext on in both the transport and the pre-check; each takes the user agent, a policy, a resolver and the configuration lambda. Three tests the plan did not list hold the trusted fetcher's host pin, a policy that already allows cleartext, and a negative wait refused before any transport is built.",
            "excerpt": {
              "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
              "heading": "D9. Tests and assertions beyond plan §9's list",
              "paragraph": "Departs from: plan §9, which lists each class's tests and what each asserts, and §9.1's totals. · As built (the class's test count, with the plan's where it differs): · `CallFacadesConsumerGatingTest` (3): the four configuration lambdas are compiled and nothing is asserted on them, since a consumer without a transport cannot apply them. · `UpstreamClientConsumerGatingTest` (3): the lambdas are applied. The common factory takes a configuration lambda and the call sets every per-call setting; `baseUrl`, `utilisation()`, `cacheStats()` and `flush()` are each read; the owning factory takes …",
              "hash": "ba0863ea352b"
            }
          },
          {
            "text": "It is Stable Core with source compatibility only, in the published fixtures, outside the dump, and adds no dependency; the fixtures-publication check passed with its expectations unchanged.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2-the-js-compile-and-the-dump",
            "excerpt": {
              "ref": "final-implementation.md#p2-the-js-compile-and-the-dump",
              "heading": "P2. The `js` compile and the dump",
              "paragraph": "`:webtools-http-commoncheck:build` passes after each of the four code commits (`Channel`, `Job()` and `KClass.simpleName` compile for `js`), and `apiDump` followed, in a separate invocation, by `apiCheck` and `verifyJavaFloor` passes after each. The dump grows by exactly plan §7.16, with no line removed:",
              "hash": "6de2ab4d3d7d"
            }
          }
        ],
        "changes": [
          {
            "what": "`LoopbackFetchersComponentTest` runs 6 tests where the plan listed 3: the trusted fetcher with pinned hosts refuses another host with `HostNotPinned`, the trusted fetcher keeps a policy that already allows cleartext, and each factory refuses a negative `defaultMaxWait` before any transport is built.",
            "why": "The fixture's trusted path and its refusal before anything is built had no listed test.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A published fixture the next unit tests against, adding no dependency.",
        "statements": [
          {
            "text": "`LoopbackFetchers.untrusted(…)` and `trusted(…)` return a `UrlFetcher` that owns the matching `LoopbackTransports` transport and pre-checks with `GuardRelaxation.LoopbackForTests`, or with cleartext forced on.",
            "ref": "plan.md#r19-the-fixture-loopbackfetchers",
            "excerpt": {
              "ref": "plan.md#r19-the-fixture-loopbackfetchers",
              "heading": "R19. The fixture `LoopbackFetchers`",
              "paragraph": "`LoopbackFetchers.untrusted(userAgent, policy, resolve, configure)` and `LoopbackFetchers.trusted(userAgent, policy, resolve, configure)` return a `UrlFetcher` that owns the matching `LoopbackTransports` transport and pre-checks with the same policy and `GuardRelaxation.LoopbackForTests` (untrusted) or cleartext forced on (trusted). It adds no dependency.",
              "hash": "5267662f07af"
            }
          },
          {
            "text": "It is Stable Core with source compatibility only, in the published fixtures, and adds no dependency; the fixtures-publication check runs again.",
            "ref": "plan.md#r1-the-public-surface-and-its-tier",
            "excerpt": {
              "ref": "plan.md#r1-the-public-surface-and-its-tier",
              "heading": "R1. The public surface and its tier",
              "paragraph": "`webtools-http` gains these public declarations in `com.spartanlabs.webtools.http`, every one final, Stable Core and usable without any opt-in. No new type is gated by `ExperimentalForInheritanceWebToolsApi` and no new seam is added: the facades *call* the existing ports and never expose one to implement.",
              "hash": "cb16ee31c6ec"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-4",
            "what": "Write the fixture with its Level 2 class.",
            "files": [
              "webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/LoopbackFetchers.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/LoopbackFetchersComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`; `:webtools-http:check` at the end",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component) `LoopbackFetchersComponentTest`; Level 1 (gating) `LoopbackFetchersConsumerGatingTest`; Level 3 (integration) `UrlFetcherLoopbackIntegrationTest`"
        ],
        "source": "plan.md R19",
        "link": "plan.md#r19-the-fixture-loopbackfetchers"
      }
    },
    {
      "id": "gating-tests",
      "group": "Tests",
      "name": "Level 1 gating tests: the facades with no opt-in",
      "system": "webtools-http-facades",
      "where": "test source (new, `testing.gating`)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing shows that a consumer uses the facades without the opt-in that gates implementing a port, or that a call's result is matched with an `else`.",
        "evidence": [
          "plan.md R1 — every new type Stable Core and usable without any opt-in; none gated",
          "plan.md §9 — three gating classes the plan adds, which #55's decomposition does not name"
        ],
        "source": "plan.md R1, §9",
        "link": "plan.md#level-1--gating-gatingtest"
      },
      "options": [
        {
          "id": "no-opt-in-sources",
          "name": "Three Level 1 classes compiled in a source set with no opt-in: the values and configurations, the facades on production defaults, and `LoopbackFetchers`",
          "verdict": "accepted",
          "why": "A consumer's own code is what the gate must leave free, and compiling it with no opt-in proves that.",
          "source": "plan.md §9",
          "link": "plan.md#level-1--gating-gatingtest"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "state": "amended",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
            "text": "Three Level 1 gating classes, 8 tests, all passing, in test source sets with no opt-in: `CallFacadesConsumerGatingTest` (3), `UpstreamClientConsumerGatingTest` (3) and `LoopbackFetchersConsumerGatingTest` (2); the first compiles the four configuration lambdas, and the second applies them for real, through every per-call setting, the client's readers, the owning factory's windowed budget, an owning `UrlFetcher` and a fetcher over a fake.",
            "excerpt": {
              "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
              "heading": "D9. Tests and assertions beyond plan §9's list",
              "paragraph": "Departs from: plan §9, which lists each class's tests and what each asserts, and §9.1's totals. · As built (the class's test count, with the plan's where it differs): · `CallFacadesConsumerGatingTest` (3): the four configuration lambdas are compiled and nothing is asserted on them, since a consumer without a transport cannot apply them. · `UpstreamClientConsumerGatingTest` (3): the lambdas are applied. The common factory takes a configuration lambda and the call sets every per-call setting; `baseUrl`, `utilisation()`, `cacheStats()` and `flush()` are each read; the owning factory takes …",
              "hash": "ba0863ea352b"
            }
          },
          {
            "text": "They build each facade by its factories, close it twice and see its own `Closed` after, fetch from a started loopback server through both fixture factories, and match a call's failures with an `else`.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "`CallFacadesConsumerGatingTest` compiles the four configuration lambdas and asserts nothing on them, and `UpstreamClientConsumerGatingTest` applies them for real: every per-call setting, `baseUrl`, `utilisation()`, `cacheStats()` and `flush()`, the owning factory's `budget(windows)`, an owning `UrlFetcher` and a fetcher over a fake transport.",
            "why": "A consumer without a transport cannot apply the lambdas, so compiling is the first class's whole check; the second locks down what a consumer with one reaches, which the listed tests left open.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The facades are Stable Core with no gated seam, so a consumer's code compiles with no opt-in.",
        "statements": [
          {
            "text": "Three Level 1 gating classes, 8 tests, in a source set with no opt-in: `CallFacadesConsumerGatingTest`, `UpstreamClientConsumerGatingTest` and `LoopbackFetchersConsumerGatingTest`.",
            "ref": "plan.md#level-1--gating-gatingtest",
            "excerpt": {
              "ref": "plan.md#level-1--gating-gatingtest",
              "heading": "Level 1 — gating (`gatingTest`)",
              "paragraph": "",
              "hash": "c5fa5f4c190e"
            }
          },
          {
            "text": "They build each facade by its factories, close it twice and see its own `Closed` after, and match a call's failures with an `else`.",
            "ref": "plan.md#level-1--gating-gatingtest",
            "excerpt": {
              "ref": "plan.md#level-1--gating-gatingtest",
              "heading": "Level 1 — gating (`gatingTest`)",
              "paragraph": "",
              "hash": "c5fa5f4c190e"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-4",
            "what": "Write the three gating classes.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/http/CallFacadesConsumerGatingTest.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/http/UpstreamClientConsumerGatingTest.kt"
            ],
            "check": "`:webtools-http:gatingTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 1 (gating): the three classes of §9"
        ],
        "source": "plan.md R1, §9",
        "link": "plan.md#level-1--gating-gatingtest"
      }
    },
    {
      "id": "component-tests",
      "group": "Tests",
      "name": "Level 2 component tests on the fake transport",
      "system": "webtools-http-facades",
      "where": "test source (new, `testing.component`)",
      "gap": {
        "kind": "missing",
        "summary": "No test composes the parts: none shows that a refusal costs nothing, that a throttle pauses the budget and fails the next call fast, that a decode failure is never stored, that a miss status is stored, or that a non-`GET` bypasses the cache.",
        "evidence": [
          "webtools-http/src/jvmTestFixtures/.../FakeHttpTransport.kt:104 — `calls`, and `:115` `unmatchedCalls` (baseline)",
          "webtools-http/src/jvmTestFixtures/.../VirtualClock.kt:66 — `dispatcher`, for the client's dispatcher",
          "../consequences.md §8 — this unit scripts the fake and asserts with `calls.size` that a refusal costs nothing",
          "../architecture.md §11 — the component tests named for unit 9"
        ],
        "source": "../architecture.md §11; ../consequences.md §8",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "fake-transport",
          "name": "Level 2 on `FakeHttpTransport` and `VirtualClock`: a refusal costs nothing, a `429` with `Retry-After` pauses the budget, a decode failure is never stored, a miss status is stored, a non-`GET` bypasses the cache",
          "verdict": "accepted",
          "why": "Deterministic, with no network and no real time; the fake is the level at which substitution is useful.",
          "source": "../architecture.md §11; ../research/api-shape-and-failures.md F9",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "changes": [
          {
            "what": "Stage 2's `UrlFetcherConfigComponentTest` test `DEFAULT_MAX_WAIT equals the client's` asserted the fetcher's constant was 5 s only, and the test support held no `clientOn` or `assertNothingSpent`; stage 3 restored both as planned, so as built nothing of it remains.",
            "why": "Each stage compiles and passes on its own, and a stage 2 source cannot name a type stage 3 builds.",
            "when": "stage 2; restored in stage 3",
            "ref": "final-implementation.md#d1-the-fetchers-commit-names-no-type-of-the-clients"
          },
          {
            "what": "The test planned as `under CallBudget.Unlimited a 429 without Retry-After reports zero` in `UpstreamClientThrottleComponentTest` is named `under CallBudget Unlimited a 429 without Retry-After reports zero`; what it asserts is the plan's.",
            "why": "A Kotlin function name on the JVM cannot contain a dot, backticked or not, so the planned name does not compile.",
            "when": "stage 3",
            "ref": "final-implementation.md#d2-two-test-names-without-a-dot"
          },
          {
            "what": "Three of `UpstreamClientRequestComponentTest`'s fifteen tests (an absolute URL, a `//host` form, and a slash, question mark and percent in a segment) assert `Found` and the one request the fake received; the other twelve end with nothing spent and the `InvalidRequest` rule, as the class heading says.",
            "why": "The class heading said every test ends with nothing spent, while those three tests' own descriptions state a call that is sent to the base host; they follow the behaviour the plan specifies.",
            "when": "stage 3",
            "ref": "final-implementation.md#d4-three-tests-of-upstreamclientrequestcomponenttest-assert-a-request-that-is-sent"
          },
          {
            "what": "Two tests the test plan did not list: one each in `UrlFetcherRequestComponentTest` and `UrlFetcherResponseComponentTest`, for the fetcher's `DEBUG` invalid-request line and `WARN` throttle line; the Level 2 component total is two above the plan's.",
            "why": "The logging requirement asks for both lines and every requirement has a test.",
            "when": "stage 3",
            "ref": "final-implementation.md#d5-two-tests-for-the-fetchers-log-lines"
          },
          {
            "what": "Ten tests the test plan did not list, in five classes: three log branches of the client (a declared miss, a failed transport, an anonymous budget), the order of the checks before anything is spent in the client and in the fetcher, a negative wait through the owning fetcher factories and a failed fetch's log line, and three paths of `LoopbackFetchers`; the refusal-log test also asserts no `call:` line, and `UpstreamClientConfigComponentTest` writes the construction messages out as literals.",
            "why": "Each locks down something the requirements state and the listed tests leave open: log lines no test asserted, an order single-fault tests cannot show, the fixture's trusted path and its refusal before anything is built, and constant texts that a test comparing a message with its own constant cannot catch.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list"
          }
        ],
        "statements": [
          {
            "state": "amended",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
            "text": "21 new Level 2 component classes, 172 tests, all passing, with hand-written fakes and no mocking library; stage 4 added the last, `LoopbackFetchersComponentTest`. Twelve tests more than planned: two for the fetcher's two log lines, ten for log branches, the order of the checks and the fixture's trusted path; one test is named without a dot, three request tests assert the request that was sent, and the construction tests write each message's text out once.",
            "excerpt": {
              "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
              "heading": "D9. Tests and assertions beyond plan §9's list",
              "paragraph": "Departs from: plan §9, which lists each class's tests and what each asserts, and §9.1's totals. · As built (the class's test count, with the plan's where it differs): · `CallFacadesConsumerGatingTest` (3): the four configuration lambdas are compiled and nothing is asserted on them, since a consumer without a transport cannot apply them. · `UpstreamClientConsumerGatingTest` (3): the lambdas are applied. The common factory takes a configuration lambda and the call sets every per-call setting; `baseUrl`, `utilisation()`, `cacheStats()` and `flush()` are each read; the owning factory takes …",
              "hash": "ba0863ea352b"
            }
          },
          {
            "text": "They hold the issue's cases: a refusal costs nothing, a `429` with `Retry-After` pauses the budget and returns `RateLimited`, a decode failure is never stored, a declared miss status is stored, and a non-`GET` bypasses the cache.",
            "state": "as-planned"
          },
          {
            "text": "`WebToolsDslComponentTest` names all five new builders, sixteen in all.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Deterministic, with no network and no real time: the fake transport and the virtual clock are the level at which substitution is useful.",
        "statements": [
          {
            "text": "21 new Level 2 component classes, 160 tests, on `FakeHttpTransport` and `VirtualClock`, with hand-written fakes and no mocking library.",
            "ref": "plan.md#level-2--component-componenttest",
            "excerpt": {
              "ref": "plan.md#level-2--component-componenttest",
              "heading": "Level 2 — component (`componentTest`)",
              "paragraph": "",
              "hash": "488110b00189"
            }
          },
          {
            "text": "They hold the issue's cases: a refusal costs nothing (zero calls, no permit, nothing cached), a `429` with `Retry-After` pauses the budget and the next call fails fast, a decode failure is never stored, a miss status is stored, and a non-`GET` bypasses the cache.",
            "ref": "plan.md#acceptance",
            "excerpt": {
              "ref": "plan.md#acceptance",
              "heading": "Acceptance",
              "paragraph": "The issue's scope is met: the five types and their configs; the common and owning factories with the `maxBurst` sizing and the cache → budget → transport closing order; Level 2 on the fake transport (a refusal costs nothing: zero calls, no permit, nothing cached; a `429` with `Retry-After` pauses the budget and the next call fails fast; a decode failure is never stored; a miss status is stored; a non-`GET` bypasses the cache); Level 3 for a real call over loopback; the README how-to. · The two open points are settled as built: the `Endpoint<T>` factory-over-class resolution (R2, with …",
              "hash": "6e2f07f2736b"
            }
          },
          {
            "text": "`WebToolsDslComponentTest` gains the five new builders in its list.",
            "ref": "plan.md#714-cmwebtoolsdslkt-and-webtoolsdslcomponenttest-changed-kdoc-and-test-list",
            "excerpt": {
              "ref": "plan.md#714-cmwebtoolsdslkt-and-webtoolsdslcomponenttest-changed-kdoc-and-test-list",
              "heading": "7.14 `<cm>WebToolsDsl.kt` and `WebToolsDslComponentTest` (changed, KDoc and test list)",
              "paragraph": "`WebToolsDsl.kt` (lines 11-14): the KDoc list becomes `… [CachePolicy.Builder], [Endpoint.Builder], [CallSpec], [FetchSpec], [UpstreamClientConfig], [UrlFetcherConfig], [RateWindow.Builder], [RateSignals.Builder], [WindowedCallBudget.Builder] and the JVM's `CaffeineResponseCache.Builder`.` `webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WebToolsDslComponentTest.kt` (the list at lines 24-35) gains `Endpoint.Builder::class.java, CallSpec::class.java, FetchSpec::class.java, UpstreamClientConfig::class.java, UrlFetcherConfig::class.java` and their imports; the …",
              "hash": "1dd64c27671c"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "The value types' classes.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/EndpointComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-2",
            "what": "The fetcher's and the dispatch's classes.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CallDispatchComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-3",
            "what": "The client's classes.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientCachingComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-4",
            "what": "The fixture's class.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/LoopbackFetchersComponentTest.kt"
            ],
            "check": "`:webtools-http:componentTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 2 (component): the 21 classes of §9"
        ],
        "source": "plan.md §9",
        "link": "plan.md#level-2--component-componenttest"
      }
    },
    {
      "id": "integration-tests",
      "group": "Tests",
      "name": "Level 3 integration test: a real call over loopback",
      "system": "webtools-http-facades",
      "where": "test source (new, `testing.integration`)",
      "gap": {
        "kind": "missing",
        "summary": "No test runs a call end to end through the real transport, cache and budget.",
        "evidence": [
          "webtools-http/src/jvmTestFixtures/.../LoopbackTransports.kt:19 — the loopback transports (baseline)",
          "../65-okhttp-transport/consequences.md §4 — the loopback integration tests of this unit use `LoopbackTransports` and `webtoolsUrl`"
        ],
        "source": "../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "loopback",
          "name": "Level 3 over loopback with `LoopbackTransports` and `MockWebServer`",
          "verdict": "accepted",
          "why": "The real transport, cache and budget together, with no outside network.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "state": "amended",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
            "text": "Four Level 3 integration classes, 20 tests, all passing, over a loopback `MockWebServer`: `UpstreamClientLoopbackIntegrationTest` (6), `UpstreamClientOwningFactoryIntegrationTest` (4), `UpstreamClientBurstSizingIntegrationTest` (2, the sized test and its unsized control) and `UrlFetcherLoopbackIntegrationTest` (8, one more than planned: the public owning factory fetches over a real exchange); the burst test fails when the sizing's per-host line is removed.",
            "excerpt": {
              "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
              "heading": "D9. Tests and assertions beyond plan §9's list",
              "paragraph": "Departs from: plan §9, which lists each class's tests and what each asserts, and §9.1's totals. · As built (the class's test count, with the plan's where it differs): · `CallFacadesConsumerGatingTest` (3): the four configuration lambdas are compiled and nothing is asserted on them, since a consumer without a transport cannot apply them. · `UpstreamClientConsumerGatingTest` (3): the lambdas are applied. The common factory takes a configuration lambda and the call sets every per-call setting; `baseUrl`, `utilisation()`, `cacheStats()` and `flush()` are each read; the owning factory takes …",
              "hash": "ba0863ea352b"
            }
          },
          {
            "text": "A `429` pauses the real budget and the next call fails fast with the server's request count unchanged, and a `404` declared a miss is stored as `Missing` and not requested again.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "`UrlFetcherLoopbackIntegrationTest` holds one test the plan did not list: the public owning `UrlFetcher` factory under a `Trusted` cleartext policy fetches from the server and closes its transport, then itself; the denied-range test supplies a budget and asserts no unit was used, and the client's first loopback test asserts the query the server received.",
            "why": "No listed test carried a real exchange through the public owning fetcher factory.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The real transport, cache and budget together, with no outside network.",
        "statements": [
          {
            "text": "Four Level 3 classes, 19 tests, over a loopback `MockWebServer`: the client with a real cache and budget, the owning factory's ownership and closing order, the burst sizing with its unsized control, and `UrlFetcher` through `LoopbackFetchers`.",
            "ref": "plan.md#level-3--integration-integrationtest",
            "excerpt": {
              "ref": "plan.md#level-3--integration-integrationtest",
              "heading": "Level 3 — integration (`integrationTest`)",
              "paragraph": "",
              "hash": "4681c096e83b"
            }
          },
          {
            "text": "A `429` pauses the real budget and the next call fails fast with the server's request count unchanged, and a declared `404` miss is not requested again.",
            "ref": "plan.md#level-3--integration-integrationtest",
            "excerpt": {
              "ref": "plan.md#level-3--integration-integrationtest",
              "heading": "Level 3 — integration (`integrationTest`)",
              "paragraph": "",
              "hash": "4681c096e83b"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-4",
            "what": "Write the four Level 3 classes and the gate dispatcher they share.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/UpstreamClientLoopbackIntegrationTest.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/UrlFetcherLoopbackIntegrationTest.kt"
            ],
            "check": "`:webtools-http:integrationTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 3 (integration) `UpstreamClientLoopbackIntegrationTest`, `UpstreamClientOwningFactoryIntegrationTest`, `UpstreamClientBurstSizingIntegrationTest` and `UrlFetcherLoopbackIntegrationTest`"
        ],
        "risks": [
          "Run on the build host only: a Linux or macOS run waits for CI (#58) (§9.2)"
        ],
        "source": "plan.md §9",
        "link": "plan.md#level-3--integration-integrationtest"
      }
    },
    {
      "id": "deterministic-tests",
      "group": "Tests",
      "name": "Level 4a deterministic tables",
      "system": "webtools-http-facades",
      "where": "test source (new, `testing.deterministic`)",
      "gap": {
        "kind": "missing",
        "summary": "The pure parts, the request assembly, the response verdicts, the endpoint's checks and the transport sizing, have no table that pins input to output.",
        "evidence": [
          "plan.md §5 — everything that can be pure is pure and sits in `commonMain`, so Level 4a tables it",
          "plan.md §9 — four Level 4a classes the plan adds, which #55's decomposition does not name"
        ],
        "source": "plan.md §5, §9",
        "link": "plan.md#level-4a--deterministic-deterministictest"
      },
      "options": [
        {
          "id": "tables",
          "name": "Four Level 4a classes of tables over the pure functions, three of them in `commonTest`",
          "verdict": "accepted",
          "why": "Input to output with no side effect, so every cell of a rule is asserted, vector T09 and the `retryAfter` rule among them.",
          "source": "plan.md §5, §9",
          "link": "plan.md#level-4a--deterministic-deterministictest"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "changes": [
          {
            "what": "The test planned as `segments and pairs encode like HttpUrl.Builder` is named `segments and pairs encode like HttpUrl Builder`; what it asserts is the plan's table.",
            "why": "A Kotlin function name on the JVM cannot contain a dot, backticked or not, so the planned name does not compile.",
            "when": "stage 2",
            "ref": "final-implementation.md#d2-two-test-names-without-a-dot"
          },
          {
            "what": "`RequestAssemblyDeterministicTest` gained `every rule of an invalid request has its constant text`, and `EndpointDeterministicTest` writes out the six endpoint messages, so the text of each rule and message constant is asserted as a literal once.",
            "why": "A test that compares a message with its own constant cannot catch a constant reworded or two swapped.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list"
          }
        ],
        "statements": [
          {
            "state": "amended",
            "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
            "text": "Four Level 4a deterministic classes, 24 tests, all passing: `RequestAssemblyDeterministicTest` (9, one more than planned), `ResponseVerdictDeterministicTest` (4), `EndpointDeterministicTest` (6) and, in stage 4, `TransportSizingDeterministicTest` (5); the endpoint and assembly tests write each message and rule constant's text out once, and one test is named without a dot.",
            "excerpt": {
              "ref": "final-implementation.md#d9-tests-and-assertions-beyond-plan-9s-list",
              "heading": "D9. Tests and assertions beyond plan §9's list",
              "paragraph": "Departs from: plan §9, which lists each class's tests and what each asserts, and §9.1's totals. · As built (the class's test count, with the plan's where it differs): · `CallFacadesConsumerGatingTest` (3): the four configuration lambdas are compiled and nothing is asserted on them, since a consumer without a transport cannot apply them. · `UpstreamClientConsumerGatingTest` (3): the lambdas are applied. The common factory takes a configuration lambda and the call sets every per-call setting; `baseUrl`, `utilisation()`, `cacheStats()` and `flush()` are each read; the owning factory takes …",
              "hash": "ba0863ea352b"
            }
          },
          {
            "text": "The verdict table crosses 14 statuses with the throttle flag and four miss sets, each cell checked against the classification order.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Pure functions take a table each, on the JVM-free common tests.",
        "statements": [
          {
            "text": "Four classes, 23 tests: `RequestAssemblyDeterministicTest`, `ResponseVerdictDeterministicTest`, `EndpointDeterministicTest` and `TransportSizingDeterministicTest`.",
            "ref": "plan.md#level-4a--deterministic-deterministictest",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic-deterministictest",
              "heading": "Level 4a — deterministic (`deterministicTest`)",
              "paragraph": "",
              "hash": "06e2d8956700"
            }
          },
          {
            "text": "The verdict table crosses 14 statuses with the throttle flag and four miss sets, each cell checked against the classification order.",
            "ref": "plan.md#level-4a--deterministic-deterministictest",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic-deterministictest",
              "heading": "Level 4a — deterministic (`deterministicTest`)",
              "paragraph": "",
              "hash": "06e2d8956700"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "The endpoint's table.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/EndpointDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-2",
            "what": "The assembly and verdict tables.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/RequestAssemblyDeterministicTest.kt",
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/ResponseVerdictDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-4",
            "what": "The sizing table.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/TransportSizingDeterministicTest.kt"
            ],
            "check": "`:webtools-http:deterministicTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4a (deterministic): the four classes of §9"
        ],
        "source": "plan.md §9",
        "link": "plan.md#level-4a--deterministic-deterministictest"
      }
    },
    {
      "id": "nonfunctional-tests",
      "group": "Tests",
      "name": "Level 4c non-functional tests on real threads",
      "system": "webtools-http-facades",
      "where": "test source (new, `testing.nonfunctional`)",
      "gap": {
        "kind": "missing",
        "summary": "Virtual time shows no race: many callers of one key on real dispatchers, and `close()` racing calls in flight, are not exercised.",
        "evidence": [
          "plan.md §10 — the only shared mutable state is the closed flag and the per-call channel, and `close()` racing a call is left to Level 4c",
          "plan.md §9 — two Level 4c classes the plan adds, which #55's decomposition does not name"
        ],
        "source": "plan.md §9, §10",
        "link": "plan.md#level-4c--non-functional-nonfunctionaltest"
      },
      "options": [
        {
          "id": "real-threads",
          "name": "Two Level 4c classes on real dispatchers and real time: 64 concurrent callers, and `close()` racing calls",
          "verdict": "accepted",
          "why": "They assert the absence of a bad outcome over many repetitions: one permit and one transport call for one key, and every call ending with a value.",
          "source": "plan.md §9",
          "link": "plan.md#level-4c--non-functional-nonfunctionaltest"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "state": "amended",
            "ref": "final-implementation.md#d10-a-close-race-class-for-urlfetcher",
            "text": "Four Level 4c non-functional classes, 8 tests, all passing, on real dispatchers and real time: the planned `UpstreamClientConcurrencyNonFunctionalTest` (2) and `UpstreamClientCloseRaceNonFunctionalTest` (2), and two the plan did not list, `UrlFetcherCloseRaceNonFunctionalTest` (2) and `CallFacadesSecrecyNonFunctionalTest` (2).",
            "excerpt": {
              "ref": "final-implementation.md#d10-a-close-race-class-for-urlfetcher",
              "heading": "D10. A close-race class for `UrlFetcher`",
              "paragraph": "Departs from: plan §9, Level 4c, which lists two classes, both for `UpstreamClient`. · As built: `UrlFetcherCloseRaceNonFunctionalTest` (2 tests), shaped like `UpstreamClientCloseRaceNonFunctionalTest`. `close racing in-flight fetches ends every fetch with a value and hangs nothing`: over 50 rounds, 16 fetches of a slow fake start together while `close()` runs from another thread at a seeded random point; each returns the response or a `Closed` of the fetcher or the transport, never a thrown cancellation; a fetch after the close reaches nothing; the round's thread pool terminates. `close from …",
              "hash": "6beb98bd8ea1"
            }
          },
          {
            "text": "64 concurrent callers of one key cost one permit and one transport call (64 of distinct keys one each, within the window), and over 50 rounds `close()` at a seeded random point ended every call with a value or a `Closed`, never a thrown cancellation, each round's thread pool terminating; calls waiting for a permit end with `Closed`.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "A third Level 4c non-functional class, `UrlFetcherCloseRaceNonFunctionalTest` (2 tests), shaped like the client's: over 50 rounds a `close()` at a seeded random point races 16 in-flight fetches, each ending with the response or a `Closed`, and 32 threads closing at once close the owned transport exactly once.",
            "why": "Both facades share one lifecycle and thread-safety contract, and the fetcher has its own closed flag and closing path, which the client's class does not exercise.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d10-a-close-race-class-for-urlfetcher"
          },
          {
            "what": "A fourth Level 4c non-functional class, `CallFacadesSecrecyNonFunctionalTest` (2 tests), in the form of the module's other secrecy suites: five sentinel strings driven through every outcome of both facades appear in no log event at `DEBUG`, no failure's message or `toString()`, and no `toString()` of the client, the fetcher, the endpoint or a result.",
            "why": "The logging rule is a statement about every line and text the facades produce, and only a suite that drives each outcome and scans everything can hold it; the transport, the cache and the policies each have one.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d11-a-secrecy-class-for-the-call-facades"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "What a deterministic seam cannot reach is probed on real threads.",
        "statements": [
          {
            "text": "Two classes, 4 tests: `UpstreamClientConcurrencyNonFunctionalTest` and `UpstreamClientCloseRaceNonFunctionalTest`.",
            "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
            "excerpt": {
              "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
              "heading": "Level 4c — non-functional (`nonfunctionalTest`)",
              "paragraph": "",
              "hash": "9e4a5a50c080"
            }
          },
          {
            "text": "64 concurrent callers of one key cost one permit and one transport call, and 50 rounds of `close()` at a random point end every call with a value within the bound and leak no thread.",
            "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
            "excerpt": {
              "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
              "heading": "Level 4c — non-functional (`nonfunctionalTest`)",
              "paragraph": "",
              "hash": "9e4a5a50c080"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-5",
            "what": "Write the two Level 4c classes.",
            "files": [
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/UpstreamClientConcurrencyNonFunctionalTest.kt",
              "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/UpstreamClientCloseRaceNonFunctionalTest.kt"
            ],
            "check": "`:webtools-http:nonfunctionalTest`",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 4c (non-functional): the two classes of §9"
        ],
        "risks": [
          "Real threads cannot prove a race absent, only that many rounds found none (§9)"
        ],
        "source": "plan.md §9, §10",
        "link": "plan.md#level-4c--non-functional-nonfunctionaltest"
      }
    },
    {
      "id": "readme",
      "group": "Documents",
      "name": "The README how-to",
      "system": "webtools-http-facades",
      "where": "README.md",
      "gap": {
        "kind": "missing",
        "summary": "The README lists `webtools-http`'s parts in its artifact table and has no how-to that shows a call through a client.",
        "evidence": [
          "README.md:11 — the `webtools-http` artifact row, the facades not in it (baseline)",
          "README.md:85 — the Components section, with no `webtools-http` how-to"
        ],
        "source": "../architecture.md §11; ../consequences.md §2",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "how-to",
          "name": "The README how-to for the facades, in this unit",
          "verdict": "accepted",
          "why": "The facades are what a consumer calls first; the final README pass and the evergreen architecture document are unit 11's.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "text": "The `webtools-http` row of the artifacts table names `UpstreamClient` and `UrlFetcher`, and the test-fixtures paragraph names `LoopbackFetchers`.",
            "state": "as-planned"
          },
          {
            "state": "amended",
            "ref": "final-implementation.md#d6-the-readme-names-the-clients-ratesignals-reader",
            "text": "A new `HTTP client stack` section, between Components and the UDP transport protocol, shows calling an upstream, fetching administrator-supplied URLs, and testing on the fixtures; its Failures bullet names the client's `rateSignals` reader, where the planned text named the budget's, and its Testing sample sets no `dispatcher`, which the common factory does not use.",
            "excerpt": {
              "ref": "final-implementation.md#d6-the-readme-names-the-clients-ratesignals-reader",
              "heading": "D6. The README names the client's `rateSignals` reader",
              "paragraph": "Departs from: plan §8(c), the \"Failures\" bullet of the README's \"HTTP client stack\" section: \"what the budget's signal reader reports as a throttle\". · As built: \"what the client's `rateSignals` reader reports as a throttle\". · Why: the reader is `UpstreamClientConfig.rateSignals`, a setting of the client; a budget has no reader and receives only the signals the client read.",
              "hash": "678961aff175"
            }
          },
          {
            "text": "Procedure P4 ran the section's snippets in a scratch class against a loopback server, 7 tests passing, every snippet as printed; the same Level 5 UAT pass read every new public type's KDoc and found its tier statement on each.",
            "state": "as-planned",
            "ref": "final-implementation.md#5-level-5-pass",
            "excerpt": {
              "ref": "final-implementation.md#5-level-5-pass",
              "heading": "5. Level 5 pass",
              "paragraph": "The KDoc, read as a consumer sees it (the KDoc of every new public declaration as it lands, and the dump):",
              "hash": "4c0074a52b08"
            }
          }
        ],
        "changes": [
          {
            "what": "The README's Failures bullet reads \"what the client's `rateSignals` reader reports as a throttle\", where the planned text read \"what the budget's signal reader reports\".",
            "why": "The reader is a setting of the client, `UpstreamClientConfig.rateSignals`; a budget has no reader and only receives the signals the client read.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d6-the-readme-names-the-clients-ratesignals-reader"
          },
          {
            "what": "The README's Testing sample builds the client as `UpstreamClient(fake, baseUrl) { timeSource = clock.timeSource; wallClock = clock.wallClock }`, without the planned `dispatcher = clock.dispatcher`.",
            "why": "The common factory owns no cache, so `dispatcher` reaches nothing there, and a sample that sets it suggests it does; `UpstreamClientCallComponentTest` builds its clients in this form.",
            "when": "stage 5",
            "ref": "final-implementation.md#d13-the-readmes-testing-sample-sets-no-dispatcher"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The facades are what a consumer calls first; the final README pass is unit 11's.",
        "statements": [
          {
            "text": "The `webtools-http` row of the artifacts table names the facades, and the test-fixtures paragraph names `LoopbackFetchers`.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "The four rings of the Audience-Reach model:",
              "hash": "4bd75beb49c3"
            }
          },
          {
            "text": "A new `HTTP client stack` section shows calling an upstream, fetching administrator-supplied URLs, and testing on the fixtures.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "The four rings of the Audience-Reach model:",
              "hash": "4bd75beb49c3"
            }
          },
          {
            "text": "Procedure P4 runs the how-to's snippets against a loopback server.",
            "ref": "plan.md#93-procedures-not-committed-tests",
            "excerpt": {
              "ref": "plan.md#93-procedures-not-committed-tests",
              "heading": "9.3 Procedures (not committed tests)",
              "paragraph": "Runs (from the repository root; on Windows `gradlew.bat`). Only `webtools-http`'s own tasks and the check module's `js` compile; no other module's suite, before or after:",
              "hash": "3abe87b1459f"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-5",
            "what": "Write the README how-to.",
            "files": [
              "README.md"
            ],
            "check": "Procedure P4",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "Level 5 (UAT, manual) Procedure P4"
        ],
        "source": "plan.md §8",
        "link": "plan.md#8-documentation-impact"
      }
    },
    {
      "id": "release",
      "group": "Release",
      "name": "The ABI dump and the version",
      "system": "webtools-http-facades",
      "where": "webtools-http/api/webtools-http.api",
      "gap": {
        "kind": "missing",
        "summary": "`webtools-http` is new and unreleased at `1.0.0`. Its committed ABI dump records no facade type.",
        "evidence": [
          "webtools-http/api/webtools-http.api:768 — `RateLimited`, the dump's only trace of the facades at the baseline",
          "../architecture.md §11 — unit 9 moves no version"
        ],
        "source": "../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "no-version",
          "name": "No version moved: `webtools-http` stays unreleased at `1.0.0` until the stack is complete, and the dump grows with the facades",
          "verdict": "accepted",
          "why": "The units that build an unreleased module move no version.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "text": "No version moved: `webtools-http` stays unreleased at `1.0.0`, the landing is no release candidate, and no other module moved.",
            "state": "as-planned"
          },
          {
            "state": "as-planned",
            "ref": "final-implementation.md#the-final-run",
            "text": "The ABI dump grew by 139 lines over the four code stages (55, 30, 42 and 12), each exactly its share of §7.16, none removed and no `<init>` for a configurable type; `apiCheck` and `verifyJavaFloor` passed after each stage and in the module's final `check`.",
            "excerpt": {
              "ref": "final-implementation.md#the-final-run",
              "heading": "The final run",
              "paragraph": "On the sources of the code-landing commit `806b489` (identical to `80b1c13`'s: the merge brings only `docs/dashboards/`). Counts are from the XML reports under `webtools-http/build/test-results/`.",
              "hash": "2999d53eb262"
            }
          },
          {
            "text": "No build file changed and no dependency was added.",
            "state": "as-planned"
          },
          {
            "state": "amended",
            "ref": "final-implementation.md#d7-the-changelog-names-the-policy-each-owning-urlfetcher-factory-uses",
            "text": "The CHANGELOG gained two entries under `[Unreleased]`, `Added`: the call facades, and the `LoopbackFetchers` test fixtures; the first says which policy each owning fetcher factory builds its transport under, the `Untrusted` defaults or the given policy, where the planned text gave both as `Untrusted`.",
            "excerpt": {
              "ref": "final-implementation.md#d7-the-changelog-names-the-policy-each-owning-urlfetcher-factory-uses",
              "heading": "D7. The CHANGELOG names the policy each owning `UrlFetcher` factory uses",
              "paragraph": "Departs from: plan §8, the first `CHANGELOG.md` entry: \"`UrlFetcher(userAgent)` and `UrlFetcher(userAgent, policy)` own an `Untrusted` transport\". · As built: \"built with `UrlFetcher(transport, policy) { … }`, or on the JVM with `UrlFetcher(userAgent)`, which owns a transport under the `Untrusted` defaults, and `UrlFetcher(userAgent, policy)`, one under the given policy\". · Why: the second overload takes any `OutboundUrlPolicy` (plan §7.12), a `Trusted` one included.",
              "hash": "2fa1b924a50c"
            }
          }
        ],
        "changes": [
          {
            "what": "The CHANGELOG says that on the JVM `UrlFetcher(userAgent)` owns a transport under the `Untrusted` defaults and `UrlFetcher(userAgent, policy)` one under the given policy, where the planned entry said both own an `Untrusted` transport.",
            "why": "The second overload takes any outbound policy, a `Trusted` one included, so its transport is not always an `Untrusted` one.",
            "when": "Quality Review 1's fixes",
            "ref": "final-implementation.md#d7-the-changelog-names-the-policy-each-owning-urlfetcher-factory-uses"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "An unreleased module's units move no version, so the ABI dump is the record of the published surface.",
        "statements": [
          {
            "text": "No version moves: `webtools-http` `1.0.0` stays unreleased, this landing is not a release candidate, and no other module moves.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-68-call-facades`. It exists and holds the research commits `98f7eb7` and `3d9d9ff`; the implementer confirms it rather than cutting it again. · Commits. Each leaves the build green (`apiCheck` included). The type and the split are the final call at commit time; subject lines end with `(Issue #68)`; no attribution trailers. · `feat: add Endpoint, CallSpec and FetchSpec (Issue #68)` — this `plan.md` and the planned `consequences.md` of this folder; `FacadeMessages.kt`, `Endpoint.kt`, `CallSpec.kt`, `FetchSpec.kt`; the stage-1 half of the `WebToolsDsl` KDoc and of …",
              "hash": "4b23de777b49"
            }
          },
          {
            "text": "The ABI dump is regenerated after each code stage and grows by exactly the classes of §7.16, with no `<init>` for any configurable type.",
            "ref": "plan.md#716-webtools-httpapiwebtools-httpapi-regenerated",
            "excerpt": {
              "ref": "plan.md#716-webtools-httpapiwebtools-httpapi-regenerated",
              "heading": "7.16 `webtools-http/api/webtools-http.api` (regenerated)",
              "paragraph": "`:webtools-http:apiDump` after each code stage. The final diff is exactly: `Endpoint` (getters, no `<init>`), `Endpoint$Builder` (getters/setters, `misses`), `EndpointKt` (`Endpoint` and `Endpoint$default`), `CallSpec`, `FetchSpec`, `UpstreamClient` (`getBaseUrl`, `call` and its synthetic `call$default`, `utilisation`, `cacheStats`, `flush`, `close`, `toString`), `UpstreamClientConfig` (accessors, `cache`, both `budget`, `budget$default`) with `UpstreamClientConfig$Companion` (`getDEFAULT_MAX_WAIT`), `UpstreamClientKt` (the common factory and its `$default`), `UpstreamClientOwningKt` (the JVM …",
              "hash": "11f45563a4c1"
            }
          },
          {
            "text": "No build file changes and no dependency is added.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-68-call-facades`. It exists and holds the research commits `98f7eb7` and `3d9d9ff`; the implementer confirms it rather than cutting it again. · Commits. Each leaves the build green (`apiCheck` included). The type and the split are the final call at commit time; subject lines end with `(Issue #68)`; no attribution trailers. · `feat: add Endpoint, CallSpec and FetchSpec (Issue #68)` — this `plan.md` and the planned `consequences.md` of this folder; `FacadeMessages.kt`, `Endpoint.kt`, `CallSpec.kt`, `FetchSpec.kt`; the stage-1 half of the `WebToolsDsl` KDoc and of …",
              "hash": "4b23de777b49"
            }
          },
          {
            "text": "The CHANGELOG gains two entries under `[Unreleased]`, `Added`: the call facades, and `LoopbackFetchers`.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "The four rings of the Audience-Reach model:",
              "hash": "4bd75beb49c3"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Regenerate the dump after each code stage, stages 1 to 4.",
            "files": [
              "webtools-http/api/webtools-http.api"
            ],
            "check": "`:webtools-http:apiDump`, then `apiCheck` and `verifyJavaFloor`",
            "link": "plan.md#5-approach-and-staging"
          },
          {
            "id": "stage-5",
            "what": "Write the CHANGELOG entries.",
            "files": [
              "CHANGELOG.md"
            ],
            "check": "`:webtools-http:check` once at the end",
            "link": "plan.md#5-approach-and-staging"
          }
        ],
        "tests": [
          "`:webtools-http:apiCheck` and `verifyJavaFloor`; `:webtools-http-commoncheck:build`, the `js` compile"
        ],
        "source": "plan.md §7.16, §8, §11",
        "link": "plan.md#11-version-control"
      }
    },
    {
      "id": "feeds-and-docs",
      "group": "Left for later",
      "name": "The feeds fetcher and the evergreen architecture document",
      "system": "webtools-http-facades",
      "where": "units 10 and 11 (#69, #70)",
      "gap": {
        "kind": "missing",
        "summary": "The conditional-GET feed fetcher sits on `UrlFetcher`, and the stack's evergreen description waits for the stack to be whole.",
        "evidence": [
          "../architecture.md §11 — unit 9 leaves feeds; unit 10 builds on `UrlFetcher`, unit 11 writes the document"
        ],
        "source": "../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "units-10-11",
          "name": "Left to unit 10 (#69) and unit 11 (#70)",
          "verdict": "accepted",
          "why": "#55's decomposition gives them to the units after this one.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "Nothing of the feed fetcher was built here: #69 builds on `UrlFetcher`'s factories, `fetch`'s fixed order and `LoopbackFetchers`, whose contracts the reconciled consequences state as built.",
            "state": "as-planned"
          },
          {
            "text": "The evergreen architecture document, the README's Components table and its final pass were left to #70, which also carries the two rules a consumer's code depends on, what a call spends and the logging contract, as the facades' class KDoc states them.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "leave",
        "defense": "#55's decomposition gives them to the units after this one.",
        "statements": [
          {
            "text": "The feed fetcher and its conditional GET are unit 10's (#69), built on `UrlFetcher(userAgent) { }`, `UrlFetcher(userAgent, policy) { }` and `LoopbackFetchers`.",
            "ref": "plan.md#12-interfaces-with-sibling-units",
            "excerpt": {
              "ref": "plan.md#12-interfaces-with-sibling-units",
              "heading": "12. Interfaces with sibling units",
              "paragraph": "Names this unit takes (a sibling must not reuse them).",
              "hash": "b1f6ef00b4c2"
            }
          },
          {
            "text": "The evergreen `docs/webtools-http-architecture.md`, the README's `### webtools-http` Components table and its final pass are unit 11's (#70).",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "A cache-configuration hook on the owning factory. `maxWeight` and the rest come by passing a `CaffeineResponseCache` to `cache(...)` (caller-owned); `cache(ResponseCache.Disabled)` turns caching off. · A weigher on `Endpoint`. Every `Found` weighs 1, so the owned cache's bound is an entry count. · `invalidate` on the client. The cache port has it; the client does not expose the cache. · A per-call deadline. A caller wraps `call` in `withTimeoutOrNull`, which cancels only the waiter. · A `missWhen` predicate. Misses are declared by status only; a \"200 with an empty object\" miss is not …",
              "hash": "1656bef8663f"
            }
          }
        ],
        "source": "plan.md §4, §12",
        "link": "plan.md#12-interfaces-with-sibling-units"
      }
    },
    {
      "id": "not-built",
      "group": "Left for later",
      "name": "A `missWhen` predicate",
      "system": "webtools-http-facades",
      "where": "not built",
      "gap": {
        "kind": "missing",
        "summary": "An endpoint declares misses by status only, so a `200` with an empty object cannot be a confirmed miss.",
        "evidence": [
          "../architecture.md §13 — a \"200 with an empty object\" miss is not expressible; a `missWhen` predicate is additive if BotTools needs it"
        ],
        "source": "../architecture.md §13",
        "link": "../architecture.md#13-known-gaps"
      },
      "options": [
        {
          "id": "additive-later",
          "name": "Left out: misses by status only in 1.0.0, a `missWhen` predicate added when a consumer needs it",
          "verdict": "accepted",
          "why": "Additive if BotTools needs it.",
          "source": "../architecture.md §13",
          "link": "../architecture.md#13-known-gaps"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "Misses are declared by status only, so a `200` with an empty object is no confirmed miss; the `missWhen` predicate stays open as the follow-up `endpoint-miss-predicate`.",
            "state": "as-planned"
          }
        ]
      },
      "verdict": {
        "kind": "leave",
        "defense": "Additive whenever a consumer needs it.",
        "statements": [
          {
            "text": "Misses are declared by status only, so a `200` with an empty object is no confirmed miss; a `missWhen` predicate is a follow-up.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "A cache-configuration hook on the owning factory. `maxWeight` and the rest come by passing a `CaffeineResponseCache` to `cache(...)` (caller-owned); `cache(ResponseCache.Disabled)` turns caching off. · A weigher on `Endpoint`. Every `Found` weighs 1, so the owned cache's bound is an entry count. · `invalidate` on the client. The cache port has it; the client does not expose the cache. · A per-call deadline. A caller wraps `call` in `withTimeoutOrNull`, which cancels only the waiter. · A `missWhen` predicate. Misses are declared by status only; a \"200 with an empty object\" miss is not …",
              "hash": "1656bef8663f"
            }
          }
        ],
        "source": "plan.md §4, §13",
        "link": "plan.md#4-non-goals"
      }
    }
  ],
  "impact": {
    "basis": "as-built",
    "summary": "`webtools-http`, new and unreleased, gains the call facades as built: `Endpoint`, `CallSpec` and `FetchSpec`, and `UpstreamClient` and `UrlFetcher` with their configurations and common factories in common code, and the owning factories on the JVM, the client's sizing its transport to the budget's burst; every new type is Stable Core and no seam is added. The fixtures gain `LoopbackFetchers`, `RateLimited` gets its producer, and the ABI dump grows from 892 to 1,031 lines with none removed. No dependency, build file or other artifact changes, and no version moves.",
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
        "system": "webtools-http-facades",
        "effect": "additive",
        "summary": "The main variants gain the call facades in common code and the owning factories on the JVM, and the ABI dump grows by exactly the classes of plan.md §7.16, from 892 to 1,031 lines with none removed. The test-fixtures variant gains `LoopbackFetchers` and no dependency. No dependency of either main variant changes.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Consumers of `webtools-http` 1.0.0, once it is released",
        "kind": "consumer",
        "system": "webtools-http-facades",
        "effect": "additive",
        "summary": "One call for an upstream's endpoint that returns the decoded value, a confirmed miss or a typed failure; N callers of one key sharing one load and one permit; throttles reported once with how long to wait, never retried; a front door for administrator-supplied URLs that refuses before it spends; the client's owning factory sizing its transport to the budget's burst, and each facade closing what it built; and virtual time under test. A call can also return an `IllegalStateException` for a cancellation that is not the caller's, and the client's refusal line prints the request path, never the query. The known gaps an operator meets are in consequences.md §8.",
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
        "who": "Unit 10, the feeds (#69)",
        "kind": "system",
        "system": null,
        "effect": "additive",
        "summary": "The feed fetcher composes a `UrlFetcher` as built: the common factory over a caller's transport for fake-based tests, and on the JVM the owning factories under the `Untrusted` defaults or a given policy; `fetch` checks in a fixed order and returns every status but a throttle as the transport's response, a `304` and a returned `3xx` included; a refused URL is `RefusedByPolicy` with nothing spent; its Level 3 integration tests run over `LoopbackFetchers`.",
        "link": "consequences.md#4-sibling-units"
      },
      {
        "who": "Unit 11, the HTTP documentation (#70)",
        "kind": "system",
        "system": null,
        "effect": "additive",
        "summary": "It documents the facades from their KDoc, the README's \"HTTP client stack\" section and the CHANGELOG entries, writes the `### webtools-http` Components table, and carries in the evergreen architecture document the two rules a consumer's code depends on: what a call spends and the logging contract, path shown and query not.",
        "link": "consequences.md#4-sibling-units"
      },
      {
        "who": "Units 6, 7 and 8, the transport, the response cache and the call budget (#65, #66, #67)",
        "kind": "system",
        "system": null,
        "effect": "none",
        "summary": "Nothing changes in them. The facades use their contracts as built and are the only code in the main variants that calls `getOrLoad`, `withPermit` or `execute`.",
        "link": "consequences.md#4-sibling-units"
      },
      {
        "who": "This repository's build",
        "kind": "repository",
        "system": "webtools-http-facades",
        "effect": "additive",
        "summary": "No build file changes. `./gradlew build` does more work: 36 more test classes of `webtools-http` with 232 tests, Level 3 integration classes with loopback servers and Level 4c non-functional classes on real threads among them, the fixtures-publication check again, `apiCheck` against the regenerated dump and the check module's `js` compile of the new common files.",
        "link": "consequences.md#build"
      }
    ],
    "adaptation": [
      {
        "who": "A consumer of the facades",
        "system": "webtools-http-facades",
        "need": "optional",
        "summary": "Declare each endpoint once with its own id, the decoder as the second argument, and give clients that share one cache distinct endpoint ids; keep an `else` when matching failures, as a call can also return one that is not an `HttpFailure`; send a header that changes the answer of a cached endpoint as a query parameter or another endpoint; keep each default header's value sendable; cap a wait on `retryAfter` where it matters; and call `flush()` before `close()` for a persistent budget's exact final write.",
        "link": "consequences.md#8-known-gaps"
      },
      {
        "who": "Unit 10, the feeds (#69)",
        "system": null,
        "need": "required",
        "summary": "A Level 2 component test over `FakeHttpTransport` with an `Untrusted` policy uses a public-looking host, as the pre-check refuses `localhost` and IP literals before the fake sees the request; `LoopbackFetchers` is the way to reach loopback.",
        "link": "consequences.md#4-sibling-units"
      }
    ],
    "api": [
      {
        "surface": "`Endpoint<T>`, `Endpoint.Builder` and `Endpoint(id, decode, configure)`",
        "system": "webtools-http-facades",
        "change": "added",
        "tier": "stable-core",
        "summary": "A declared call: a stable id, a decoder fixed at construction, and its lifetimes, cost, lane and miss statuses; invariant in `T`.",
        "link": "plan.md#r2-endpoint"
      },
      {
        "surface": "`CallSpec` and `FetchSpec`",
        "system": "webtools-http-facades",
        "change": "added",
        "tier": "stable-core",
        "summary": "The per-call settings handed to `call`'s and `fetch`'s lambdas.",
        "link": "plan.md#r3-callspec-and-fetchspec"
      },
      {
        "surface": "`UpstreamClient`, `UpstreamClientConfig` and `UpstreamClient(transport, baseUrl, configure)`",
        "system": "webtools-http-facades",
        "change": "added",
        "tier": "stable-core",
        "summary": "The client of one upstream and its common factory over a caller's transport; `DEFAULT_MAX_WAIT` 5 s.",
        "link": "plan.md#r5-upstreamclient"
      },
      {
        "surface": "`UrlFetcher`, `UrlFetcherConfig` and `UrlFetcher(transport, policy, configure)`",
        "system": "webtools-http-facades",
        "change": "added",
        "tier": "stable-core",
        "summary": "The fetcher of administrator-supplied URLs and its common factory; `DEFAULT_MAX_WAIT` 5 s.",
        "link": "plan.md#r6-urlfetcherconfig-and-urlfetcher"
      },
      {
        "surface": "`UpstreamClient(baseUrl, userAgent, configure)`, `UrlFetcher(userAgent, configure)` and `UrlFetcher(userAgent, policy, configure)`",
        "system": "webtools-http-facades",
        "change": "added",
        "tier": "stable-core",
        "summary": "The JVM owning factories, which build, size and close what they own.",
        "link": "plan.md#r16-owning-factories-and-sizing"
      },
      {
        "surface": "`LoopbackFetchers` (test fixtures)",
        "system": "webtools-http-facades",
        "change": "added",
        "tier": "stable-core",
        "summary": "A `UrlFetcher` over a loopback server with every other `Untrusted` rule live; source compatibility only.",
        "link": "plan.md#r19-the-fixture-loopbackfetchers"
      },
      {
        "surface": "`UpstreamFailure.RateLimited.retryAfter`",
        "system": "webtools-http-facades",
        "change": "changed",
        "tier": "stable-core",
        "summary": "Its KDoc now says how long to wait before calling again, the longer of the server's `Retry-After` and the budget's pause; its shape does not change, and no released version carried the old meaning.",
        "link": "plan.md#79-cmupstreamfailurekt-changed-kdoc-only-stage-2"
      }
    ],
    "conversion": [],
    "source": "consequences.md, reconciled with final-implementation.md §2",
    "link": "consequences.md#1-published-artifacts",
    "changes": [
      {
        "entry": "downstream.1",
        "system": "webtools-http-facades",
        "what": "A call can also return a `Result.failure` holding an `IllegalStateException`: a cancellation that is not the caller's, escaping a load from a part the consumer supplies, comes back through the response cache's loader rule, where the plan said every failure of a call is an `HttpFailure`.",
        "why": "The plan's error-handling requirement and its table of `call`'s failures disagree, and the build follows the table, the cache's rule as built; what such a cancellation does in `fetch` is a known gap.",
        "when": "stage 3; recorded at the plan's reconciliation",
        "ref": "final-implementation.md#d12-a-call-can-return-an-illegalstateexception"
      },
      {
        "entry": "downstream.1",
        "system": "webtools-http-facades",
        "what": "The client's `WARN` line for a policy refusal prints the request path, a parameter of the cache key, through `Url.redacted()`; the query is never printed, where the plan said no line carries a cache-key value.",
        "why": "The plan's logging table gives that line's exact text with `Url.redacted()`, as #55's architecture does for every policy refusal, and the sentence under it disagrees; the build follows the table.",
        "when": "stage 3; recorded at the plan's reconciliation",
        "ref": "final-implementation.md#d14-the-clients-refusal-line-prints-the-request-path"
      },
      {
        "entry": "downstream.4",
        "system": null,
        "what": "The documentation unit (#70) also carries, in the evergreen architecture document, the two rules a consumer's code depends on, as the facades' class KDoc states them: what a call spends and the logging contract.",
        "why": "The reconciled consequences state the sibling units' contracts as built; the plan gave #70 the KDoc, the README section and the CHANGELOG entries only.",
        "when": "the plan's reconciliation",
        "ref": "consequences.md#4-sibling-units"
      },
      {
        "entry": "adaptation.0",
        "system": "webtools-http-facades",
        "what": "A consumer also keeps an `else` for a failure that is not an `HttpFailure`, and keeps each default header's value sendable.",
        "why": "The build leaves both as known gaps: a part the consumer supplies that throws escapes the failure model, and construction accepts a default header that every call then refuses as `InvalidRequest` (follow-ups `dispatch-part-contract` and `default-header-sendable`).",
        "when": "the plan's reconciliation",
        "ref": "consequences.md#8-known-gaps"
      }
    ]
  },
  "tests": {
    "updated": "2026-11-06T03:09:04Z",
    "added": [
      {
        "class": "EndpointComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/EndpointComponentTest.kt",
        "tests": 8,
        "rows": [
          "endpoint"
        ],
        "stage": "stage-1",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CallSpecComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/CallSpecComponentTest.kt",
        "tests": 8,
        "rows": [
          "call-spec"
        ],
        "stage": "stage-1",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FetchSpecComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/FetchSpecComponentTest.kt",
        "tests": 2,
        "rows": [
          "call-spec"
        ],
        "stage": "stage-1",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "EndpointDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/EndpointDeterministicTest.kt",
        "tests": 6,
        "rows": [
          "endpoint",
          "deterministic-tests"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "UrlFetcherConfigComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/UrlFetcherConfigComponentTest.kt",
        "tests": 3,
        "rows": [
          "factories"
        ],
        "stage": "stage-3",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CallDispatchComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CallDispatchComponentTest.kt",
        "tests": 7,
        "rows": [
          "permit-settlement",
          "rate-limited"
        ],
        "stage": "stage-2",
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlFetcherResponseComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UrlFetcherResponseComponentTest.kt",
        "tests": 9,
        "rows": [
          "url-fetcher",
          "rate-limited",
          "logging"
        ],
        "stage": "stage-3",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlFetcherRequestComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UrlFetcherRequestComponentTest.kt",
        "tests": 9,
        "rows": [
          "url-fetcher",
          "logging",
          "component-tests"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "UrlFetcherPolicyRefusalComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UrlFetcherPolicyRefusalComponentTest.kt",
        "tests": 5,
        "rows": [
          "url-fetcher",
          "logging"
        ],
        "stage": "stage-2",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlFetcherLifecycleComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UrlFetcherLifecycleComponentTest.kt",
        "tests": 9,
        "rows": [
          "lifecycle",
          "url-fetcher",
          "logging",
          "component-tests"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "RequestAssemblyDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/RequestAssemblyDeterministicTest.kt",
        "tests": 9,
        "rows": [
          "call-spec",
          "deterministic-tests"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "ResponseVerdictDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/ResponseVerdictDeterministicTest.kt",
        "tests": 4,
        "rows": [
          "upstream-client",
          "url-fetcher",
          "rate-limited",
          "deterministic-tests"
        ],
        "stage": "stage-2",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UpstreamClientConfigComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientConfigComponentTest.kt",
        "tests": 11,
        "rows": [
          "factories",
          "component-tests"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "UpstreamClientCallComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientCallComponentTest.kt",
        "tests": 5,
        "rows": [
          "upstream-client"
        ],
        "stage": "stage-3",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UpstreamClientCachingComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientCachingComponentTest.kt",
        "tests": 10,
        "rows": [
          "upstream-client"
        ],
        "stage": "stage-3",
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UpstreamClientClassificationComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientClassificationComponentTest.kt",
        "tests": 12,
        "rows": [
          "upstream-client"
        ],
        "stage": "stage-3",
        "result": {
          "state": "passed",
          "passed": 12,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UpstreamClientThrottleComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientThrottleComponentTest.kt",
        "tests": 13,
        "rows": [
          "rate-limited"
        ],
        "stage": "stage-3",
        "result": {
          "state": "passed",
          "passed": 13,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UpstreamClientPermitRulesComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientPermitRulesComponentTest.kt",
        "tests": 8,
        "rows": [
          "permit-settlement"
        ],
        "stage": "stage-3",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UpstreamClientRequestComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientRequestComponentTest.kt",
        "tests": 16,
        "rows": [
          "call-spec",
          "upstream-client",
          "component-tests"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 16,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "UpstreamClientPolicyRefusalComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientPolicyRefusalComponentTest.kt",
        "tests": 4,
        "rows": [
          "upstream-client",
          "component-tests",
          "logging"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "UpstreamClientLifecycleComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientLifecycleComponentTest.kt",
        "tests": 10,
        "rows": [
          "lifecycle"
        ],
        "stage": "stage-3",
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UpstreamClientConstructionComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientConstructionComponentTest.kt",
        "tests": 6,
        "rows": [
          "factories"
        ],
        "stage": "stage-3",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UpstreamClientLoggingComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamClientLoggingComponentTest.kt",
        "tests": 11,
        "rows": [
          "logging",
          "component-tests"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "CallFacadesConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/http/CallFacadesConsumerGatingTest.kt",
        "tests": 3,
        "rows": [
          "gating-tests"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "UpstreamClientConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/http/UpstreamClientConsumerGatingTest.kt",
        "tests": 3,
        "rows": [
          "gating-tests",
          "owning-factories"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "LoopbackFetchersConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/http/testing/LoopbackFetchersConsumerGatingTest.kt",
        "tests": 2,
        "rows": [
          "gating-tests",
          "loopback-fetchers"
        ],
        "stage": "stage-4",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "LoopbackFetchersComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/LoopbackFetchersComponentTest.kt",
        "tests": 6,
        "rows": [
          "loopback-fetchers",
          "component-tests"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "TransportSizingDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/TransportSizingDeterministicTest.kt",
        "tests": 5,
        "rows": [
          "owning-factories",
          "deterministic-tests"
        ],
        "stage": "stage-4",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UpstreamClientLoopbackIntegrationTest",
        "module": "webtools-http",
        "testLevel": "3",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/UpstreamClientLoopbackIntegrationTest.kt",
        "tests": 6,
        "rows": [
          "integration-tests",
          "upstream-client"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "UpstreamClientOwningFactoryIntegrationTest",
        "module": "webtools-http",
        "testLevel": "3",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/UpstreamClientOwningFactoryIntegrationTest.kt",
        "tests": 4,
        "rows": [
          "integration-tests",
          "owning-factories",
          "lifecycle"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "UpstreamClientBurstSizingIntegrationTest",
        "module": "webtools-http",
        "testLevel": "3",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/UpstreamClientBurstSizingIntegrationTest.kt",
        "tests": 2,
        "rows": [
          "integration-tests",
          "owning-factories"
        ],
        "stage": "stage-4",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlFetcherLoopbackIntegrationTest",
        "module": "webtools-http",
        "testLevel": "3",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/UrlFetcherLoopbackIntegrationTest.kt",
        "tests": 8,
        "rows": [
          "integration-tests",
          "url-fetcher",
          "loopback-fetchers",
          "owning-factories"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "UpstreamClientConcurrencyNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/UpstreamClientConcurrencyNonFunctionalTest.kt",
        "tests": 2,
        "rows": [
          "nonfunctional-tests"
        ],
        "stage": "stage-5",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UpstreamClientCloseRaceNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/UpstreamClientCloseRaceNonFunctionalTest.kt",
        "tests": 2,
        "rows": [
          "nonfunctional-tests",
          "lifecycle"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "UrlFetcherCloseRaceNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/UrlFetcherCloseRaceNonFunctionalTest.kt",
        "tests": 2,
        "rows": [
          "nonfunctional-tests",
          "url-fetcher",
          "lifecycle"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
        }
      },
      {
        "class": "CallFacadesSecrecyNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/CallFacadesSecrecyNonFunctionalTest.kt",
        "tests": 2,
        "rows": [
          "nonfunctional-tests",
          "logging"
        ],
        "stage": "Quality Review 1 fixes",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": null,
          "at": "2026-11-06T03:09:04Z",
          "note": "The module's check after Quality Review 1's fixes: 1,447 tests in 263 classes, none failed or skipped; no second pass ran."
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
          "endpoint",
          "call-spec",
          "factories"
        ],
        "stage": "stage-3",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      }
    ],
    "relevant": [
      {
        "class": "CaffeineResponseCacheSizeBoundNonFunctionalTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheCloseRaceNonFunctionalTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "BodyRequiredMethodDifferentialDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheFailureIdentityNonFunctionalTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FakeHttpTransportConcurrencyNonFunctionalTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheLoaderCancellationComponentTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpMessageCodecGarbageNonFunctionalTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheWaiterCancellationComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "AnswerJudgementComponentTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "BudgetEventComponentTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "BudgetSnapshotComponentTest",
        "module": "webtools-http",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "BudgetUtilisationComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "BudgetVocabularyComponentTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CacheKeyComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CachePolicyComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CacheStatsComponentTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheCaffeineLogComponentTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheCloseComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheCoalescingComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheConstructionComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheFailureComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheFatalErrorComponentTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheHitsComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheInvalidationComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheLoggingComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheMaxAgeComponentTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheMissTtlComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheRejectedLoadComponentTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheStatsComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheThreeClocksComponentTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheWeightComponentTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheZeroTtlComponentTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CallBridgeComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CallBudgetUnlimitedComponentTest",
        "module": "webtools-http",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CallDeadlineComponentTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CappedBodyComponentTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CatchNonFatalComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CidrBlockComponentTest",
        "module": "webtools-http",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "DisabledResponseCacheComponentTest",
        "module": "webtools-http",
        "tests": 10,
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "DispatcherExecutorComponentTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "EpochMillisJvmComponentTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ExchangeGuardComponentTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FailureLeavesComponentTest",
        "module": "webtools-http",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FailureReportComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "GuardRelaxationComponentTest",
        "module": "webtools-http",
        "tests": 14,
        "result": {
          "state": "passed",
          "passed": 14,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "GuardedDnsComponentTest",
        "module": "webtools-http",
        "tests": 14,
        "result": {
          "state": "passed",
          "passed": 14,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "GuardedSocketComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "GuardedSocketFactoryComponentTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HopRequestComponentTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HopResponseComponentTest",
        "module": "webtools-http",
        "tests": 10,
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpHeadersComponentTest",
        "module": "webtools-http",
        "tests": 12,
        "result": {
          "state": "passed",
          "passed": 12,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpMethodComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpRequestComponentTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpResponseComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "InMemoryBudgetStoreComponentTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "LaneComponentTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "LoaderRunnerComponentTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "LookupComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "OkHttpTransportCloseComponentTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "OkHttpTransportConstructionComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "OkHttpTransportDeadlineComponentTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "OkHttpTransportPreflightComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "PolicyEnforcementComponentTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "PolicyRefusalMarkersComponentTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "PolicyWithSettingsComponentTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RateSignalsComponentTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RateSignalsReaderStandardComponentTest",
        "module": "webtools-http",
        "tests": 11,
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RateWindowComponentTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ReasonConstantsComponentTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ReceivedResponseComponentTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RedirectAdmissionComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RedirectHopComponentTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RequestBodyComponentTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ResultFlatMapComponentTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "Slf4jLogComponentTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "TransportClientComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "TransportSettingsComponentTest",
        "module": "webtools-http",
        "tests": 23,
        "result": {
          "state": "passed",
          "passed": 23,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "TrustedGuardComponentTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "TrustedPolicyComponentTest",
        "module": "webtools-http",
        "tests": 13,
        "result": {
          "state": "passed",
          "passed": 13,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UntrustedGuardComponentTest",
        "module": "webtools-http",
        "tests": 11,
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UntrustedPolicyComponentTest",
        "module": "webtools-http",
        "tests": 18,
        "result": {
          "state": "passed",
          "passed": 18,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UpstreamErrorComponentTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WallClockSystemComponentTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetCancellationComponentTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetClockPauseComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetClockStepComponentTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetCloseComponentTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetConstructionComponentTest",
        "module": "webtools-http",
        "tests": 16,
        "result": {
          "state": "passed",
          "passed": 16,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetCrashComponentTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetFifoComponentTest",
        "module": "webtools-http",
        "tests": 10,
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetGuardComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetHardWindowComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetLaneFloorComponentTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetLoggingComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetObserverComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetPauseComponentTest",
        "module": "webtools-http",
        "tests": 11,
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetReconcileComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetRefundComponentTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetRestoreComponentTest",
        "module": "webtools-http",
        "tests": 12,
        "result": {
          "state": "passed",
          "passed": 12,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetStoreFailureComponentTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetThreeClocksComponentTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WithPermitComponentTest",
        "module": "webtools-http",
        "tests": 10,
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FakeBudgetStoreComponentTest",
        "module": "webtools-http",
        "tests": 11,
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FakeHttpTransportComponentTest",
        "module": "webtools-http",
        "tests": 22,
        "result": {
          "state": "passed",
          "passed": 22,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FixtureComponentTest",
        "module": "webtools-http",
        "tests": 10,
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FixtureLoaderComponentTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FunctionDnsComponentTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "LoopbackTransportsComponentTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "MockWebServerEnqueueComponentTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RecordingBudgetObserverComponentTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RedactionComponentTest",
        "module": "webtools-http",
        "tests": 13,
        "result": {
          "state": "passed",
          "passed": 13,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RequestMatchComponentTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "StepComponentTest",
        "module": "webtools-http",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ToMockResponseComponentTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "VirtualClockComponentTest",
        "module": "webtools-http",
        "tests": 11,
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "VirtualClockThreeClocksComponentTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WebtoolsUrlComponentTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "AddressRangesControlsDeterministicTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "AddressRangesJdkSupersetDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "AddressRangesRowsDeterministicTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "BodyExpectedDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "BudgetLedgerPlanDeterministicTest",
        "module": "webtools-http",
        "tests": 19,
        "result": {
          "state": "passed",
          "passed": 19,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "BudgetLedgerPropertyDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "BudgetLedgerSnapshotDeterministicTest",
        "module": "webtools-http",
        "tests": 10,
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CacheEntryRulesDeterministicTest",
        "module": "webtools-http",
        "tests": 10,
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CacheKeyCanonicalFormDeterministicTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CellFoldDeterministicTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CidrBlockParseDeterministicTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FailureMappingDeterministicTest",
        "module": "webtools-http",
        "tests": 13,
        "result": {
          "state": "passed",
          "passed": 13,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "GuardReasonOrderDeterministicTest",
        "module": "webtools-http",
        "tests": 11,
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HeaderOkHttpDifferentialDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HeadersForHopDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HostRulesDirectInputDeterministicTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpDateDeterministicTest",
        "module": "webtools-http",
        "tests": 10,
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpDateJavaTimeDifferentialDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpHeaderNameDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpHeaderValueDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpMethodTokenDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpSyntaxDeterministicTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "JdkPredicateGapDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "MediaTypeDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "OkHttpLiteralRegexDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "PauseDeterministicTest",
        "module": "webtools-http",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "PublishedBypassVectorsDeterministicTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ReceiveHeadersDeterministicTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RedirectDecisionDeterministicTest",
        "module": "webtools-http",
        "tests": 11,
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RestoredStatusDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RetryAfterDeterministicTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ShareRoundingDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "StatusRangeDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "StrictIpDeterministicTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "StrictIpJdkDifferentialDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "StrictIpUrlAgreementDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ThrowableSearchDeterministicTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "TransportControlledHeadersDeterministicTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlAcceptDeterministicTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlBoundaryDeterministicTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlConfusionDeterministicTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlEncoderDifferentialDeterministicTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlEncodersDeterministicTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlIpv6DeterministicTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlOkHttpDifferentialDeterministicTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlPrintingDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlRejectDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UserAgentDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowArithmeticDeterministicTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FixtureFileNamesDeterministicTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpMessageCodecDeterministicTest",
        "module": "webtools-http",
        "tests": 8,
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "LoopbackUrlTextDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "QueryPairsDeterministicTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RecordingGateDeterministicTest",
        "module": "webtools-http",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "SensitiveNamesDeterministicTest",
        "module": "webtools-http",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheConsumerGatingTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CallBudgetConsumerGatingTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "GatedTypesConsumerGatingTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "GatedTypesJvmConsumerGatingTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "OkHttpTransportConsumerGatingTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "OutboundUrlPolicyConsumerGatingTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ResponseCacheConsumerGatingTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WebToolsDslConsumerGatingTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetConsumerGatingTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FixturesConsumerGatingTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "LoopbackTransportsConsumerGatingTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "BodyCapIntegrationTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheLoopbackIntegrationTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CancellationIntegrationTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ControlledHeadersIntegrationTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CrossOriginStripIntegrationTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ExchangeCountIntegrationTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ExchangeGuardStatusIntegrationTest",
        "module": "webtools-http",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ExchangeGuardWireIntegrationTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FailureLeafIntegrationTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FixtureRoundTripIntegrationTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "GuardedSocketLoopbackIntegrationTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "GuardedSocketSocksIntegrationTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HttpsRedirectIntegrationTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "JvmExitIntegrationTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "MainSafetyIntegrationTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "PolicyEnforcementOkHttpIntegrationTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "PolicyEnforcementTransportIntegrationTest",
        "module": "webtools-http",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ProxySettingsIntegrationTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RateSignalsReaderLoopbackIntegrationTest",
        "module": "webtools-http",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ReceivedHeadersIntegrationTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RedirectFollowingIntegrationTest",
        "module": "webtools-http",
        "tests": 9,
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RedirectRefusalIntegrationTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "SocksProxySettingsIntegrationTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "TlsFailureIntegrationTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "TransportLoggingIntegrationTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FixtureRecorderIntegrationTest",
        "module": "webtools-http",
        "tests": 16,
        "result": {
          "state": "passed",
          "passed": 16,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "CaffeineResponseCacheSecrecyNonFunctionalTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ClassifierGarbageNonFunctionalTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "ClassifierLinearTimeNonFunctionalTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "EndlessBodyNonFunctionalTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HeaderMessageSecrecyNonFunctionalTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "HostileStatusLineNonFunctionalTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "PolicySecrecyNonFunctionalTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "RedirectChainCapNonFunctionalTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "SlowDripNonFunctionalTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "TransportSecrecyNonFunctionalTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlParseGarbageNonFunctionalTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlParseLinearTimeNonFunctionalTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "UrlParseSecrecyNonFunctionalTest",
        "module": "webtools-http",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetCloseRaceNonFunctionalTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetContentionNonFunctionalTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "WindowedCallBudgetReplanCostNonFunctionalTest",
        "module": "webtools-http",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      },
      {
        "class": "FixturesSecrecyNonFunctionalTest",
        "module": "webtools-http",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 1,
          "at": "2026-11-06T02:52:19Z"
        }
      }
    ],
    "other": {
      "byModule": {
        "webtools-udp": {
          "1": {
            "classes": 19,
            "tests": 83
          },
          "2": {
            "classes": 21,
            "tests": 235
          },
          "3": {
            "classes": 20,
            "tests": 115
          },
          "5": {
            "classes": 1,
            "tests": 13
          },
          "4a": {
            "classes": 9,
            "tests": 96
          },
          "4b": {
            "classes": 11,
            "tests": 34
          },
          "4c": {
            "classes": 11,
            "tests": 65
          }
        },
        "webtools-scraping": {
          "2": {
            "classes": 1,
            "tests": 11
          }
        },
        "webtools-browser": {
          "1": {
            "classes": 1,
            "tests": 3
          },
          "5": {
            "classes": 1,
            "tests": 2
          }
        }
      }
    }
  },
  "roadmap": {
    "issue": 55,
    "title": "HTTP client stack for BotTools: suspend transport, outbound-URL guard, response cache, call budget and test fakes",
    "asOf": "2026-11-05",
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
        "state": "CLOSED",
        "stateReason": "COMPLETED"
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
  "followUps": [
    {
      "name": "client-cache-config",
      "note": "a hook on the owning client factory for the owned cache's settings"
    },
    {
      "name": "endpoint-weigher",
      "note": "a weigher on `Endpoint`, so the cache's bound can be bytes rather than entries"
    },
    {
      "name": "client-invalidate",
      "note": "`invalidate` and `invalidateAll` on the client"
    },
    {
      "name": "call-deadline",
      "note": "a per-call deadline on `CallSpec`, returning a new failure leaf"
    },
    {
      "name": "endpoint-miss-predicate",
      "note": "a `missWhen` predicate for a confirmed miss in a `200`"
    },
    {
      "name": "fetcher-owned-budget",
      "note": "a budget built and owned by `UrlFetcher`"
    },
    {
      "name": "fetcher-sizing",
      "note": "sizing the fetcher's transport from a windowed budget"
    },
    {
      "name": "endpoint-covariance",
      "note": "`Endpoint<out T>`, additive and compatible"
    },
    {
      "name": "facades-conditional-get",
      "note": "a conditional-GET layer in the facades themselves; the feed fetcher (#69) carries its own on top of `UrlFetcher`"
    },
    {
      "name": "facade-close-cancellation",
      "note": "the closing step goes on after a `CancellationException` from a part's `close()`, so the remaining parts still close"
    },
    {
      "name": "dispatch-part-contract",
      "note": "what a consumer's part throws becomes a failure value in both facades, and `fetch` tells a cancellation that is not the caller's from the caller's own"
    },
    {
      "name": "default-header-sendable",
      "note": "construction refuses a default header whose value cannot be sent"
    },
    {
      "issue": 58,
      "title": "Repository practices: CI, CONTRIBUTING.md, CHANGELOG.md, release tags, and the foojay resolver for Gradle 9",
      "note": "the first place these tests would run on every change, with the Linux and macOS run of the Level 3 integration suite"
    },
    {
      "issue": 87,
      "title": "webtools-http: UrlParseLinearTimeNonFunctionalTest exceeds its 100x bound when the level test tasks run in parallel",
      "note": "four more Level 4c non-functional classes run beside it; it passed in this unit's final run"
    }
  ],
  "documents": [
    {
      "label": "Research",
      "path": "research/README.md",
      "note": "this unit's three questions, the verification of 35 claims, the one line of the summary that did not hold in the build, and the summary"
    },
    {
      "label": "Endpoint factory findings",
      "path": "research/endpoint-factory-resolution.md",
      "note": "the probe on Kotlin 2.2.0: the generic factory's resolution, inference and ABI"
    },
    {
      "label": "Retry-After findings",
      "path": "research/rate-limited-retry-after.md",
      "note": "what a rate-limited failure reports"
    },
    {
      "label": "Wait cap findings",
      "path": "research/default-wait-cap.md",
      "note": "the default wait for a permit"
    },
    {
      "label": "Plan",
      "path": "plan.md",
      "note": "the requirements, the design, the five stages and the test plan"
    },
    {
      "label": "Final implementation",
      "path": "final-implementation.md",
      "note": "the as-built record: what was built, the deviations D1 to D14, the four acceptance criteria and the measurements"
    },
    {
      "label": "Consequences",
      "path": "consequences.md",
      "note": "the artifacts, the blast radius, the sibling units' contracts as built, twelve follow-ups, the known gaps and the plan's assumptions as built"
    },
    {
      "label": "Issue #68",
      "path": "https://github.com/SpartanLaboratories/WebTools/issues/68",
      "note": "the unit as #55 filed it"
    },
    {
      "label": "#55 research",
      "path": "../research/README.md",
      "note": "the questions, verification and summary the Gap column rests on; question 9 bears on this unit"
    },
    {
      "label": "#55 API shape findings",
      "path": "../research/api-shape-and-failures.md",
      "note": "F9 to F11, the facade's sketch, its pipeline and lifecycle, and considerations 1 to 6 behind the options"
    },
    {
      "label": "#55 architecture",
      "path": "../architecture.md",
      "note": "§6.6 the facades' design, §7.2 the call path, §9 the tiers, §10 the alternatives, §11 this unit's row, §12 and §13 the open points"
    },
    {
      "label": "#55 consequences",
      "path": "../consequences.md",
      "note": "§7 the open points this unit settles, §8 what units 1 to 9 as built leave the later units"
    },
    {
      "label": "#65 consequences",
      "path": "../65-okhttp-transport/consequences.md",
      "note": "§4: the transport's contracts for this unit, and what each failure implies about what was sent"
    },
    {
      "label": "#66 consequences",
      "path": "../66-response-cache/consequences.md",
      "note": "§4: the cache's contracts for this unit's loader"
    },
    {
      "label": "#67 consequences",
      "path": "../67-call-budget/consequences.md",
      "note": "§4: the budget's contracts for this unit, `maxBurst`, the closing order and `pausedFor`"
    }
  ],
  "notes": {
    "paths": "`common/` abbreviates `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/` and `jvm/` its `jvmMain`, as #55's architecture does. Line references marked baseline are to `5f221ce`.",
    "origin": "This unit's architecture happened in #55's run, so the Gap and Considered columns were written at sized from #55's research README, its findings and its architecture; the unit's own research adds its evidence to the Gap column, and its plan adds to Considered, settles the two points left open and writes the Verdict at plan acceptance.",
    "corrections": [
      {
        "what": "The `call-spec` row's Gap link follows #55's consequences §8, renamed for units 1 to 9 as built, and the As built references follow the sections of `final-implementation.md` as reconciled (the deviations, the procedures and the Level 5 UAT pass renamed).",
        "when": "the plan's reconciliation, 2026-11-06"
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
    "67": "../67-call-budget/dashboard.html"
  },
  "links": {
    "workBoard": "../../index.html",
    "repoBoard": "../../../dashboards/index.html",
    "boards": {
      "webtools-http-facades": "../../../dashboards/webtools-http-facades/index.html"
    }
  }
}
);
