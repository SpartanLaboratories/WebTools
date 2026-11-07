/* plan.js: plan.json as a script, written by plan/scripts/embed.js beside the board for its live view (plan/RULES.md §1). Do not edit: edit plan.json and re-embed. */
window.dashboardLive&&window.dashboardLive("plan",
{
  "schema": "plan-board/1",
  "repo": "SpartanLaboratories/WebTools",
  "issue": 69,
  "slug": "69-feeds",
  "folder": "docs/plans/55-http-client-stack/69-feeds",
  "kind": "issue",
  "title": "webtools-feeds",
  "issueTitle": "webtools-feeds: RSS/Atom parser port with the Rome default, hardening suite, and a conditional-GET fetcher over `UrlFetcher`",
  "function": "Unit 10 of 11 in the plan for #55, the HTTP client stack. It adds `webtools-feeds`, a new Kotlin Multiplatform module on `webtools-http` that parses hostile RSS and Atom documents behind a gated `FeedParser` port, with a Rome 2.1.0 default in `jvmMain` locked down by a DOCTYPE prolog scan in common code, field and count limits and a fixed-heap Level 4c hardening suite, and fetches feeds with a stateless conditional GET over `UrlFetcher`, the caller keeping the validators. A check-only `js` module compiles its common code; `webtools-feeds` 1.0.0 is new and stays unreleased until the stack is complete.",
  "status": "built",
  "sized": "2026-11-06T04:27:23Z",
  "parent": {
    "issue": 55,
    "board": "../dashboard.html"
  },
  "fixOf": null,
  "branch": "feat/issue-69-feeds",
  "baseline": {
    "ref": "master",
    "sha": "0374c8d",
    "date": "2026-11-05"
  },
  "asBuilt": {
    "sha": "20d4546",
    "merge": "20d4546",
    "pr": 96,
    "date": "2026-11-07",
    "at": "2026-11-07T03:23:42Z",
    "commits": [
      {
        "sha": "88af7dc",
        "at": "2026-11-06T04:56:13Z",
        "step": "research"
      },
      {
        "sha": "066bf17",
        "at": "2026-11-06T20:49:12Z",
        "step": "research"
      },
      {
        "sha": "5ae0bda",
        "at": "2026-11-06T20:56:20Z",
        "step": "research"
      },
      {
        "sha": "e89d86a",
        "at": "2026-11-06T22:08:59Z",
        "step": "research"
      }
    ]
  },
  "systems": [
    {
      "slug": "webtools-feeds",
      "name": "webtools-feeds",
      "slot": 6,
      "top": "webtools-feeds",
      "status": "built",
      "board": "docs/dashboards/webtools-feeds/index.html"
    },
    {
      "slug": "js-commoncheck",
      "name": "check-only js modules",
      "slot": 4,
      "top": "Gradle build",
      "status": "built",
      "board": null
    }
  ],
  "wip": {
    "step": "landed",
    "since": "2026-11-07T03:23:42Z",
    "updated": "2026-11-07T03:23:42Z",
    "detail": "Landed in PR #96.",
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
        "id": "skeleton-and-vocabulary",
        "name": "1. Skeleton and vocabulary",
        "state": "done",
        "rows": [
          "module",
          "commoncheck",
          "logging",
          "failures",
          "parser-port",
          "model",
          "validators",
          "gating-tests",
          "component-tests",
          "deterministic-tests",
          "release"
        ]
      },
      {
        "id": "prolog-scan-and-rome-parser",
        "name": "2. Prolog scan and RomeFeedParser",
        "state": "done",
        "rows": [
          "prolog-scan",
          "rome-parser",
          "module",
          "logging",
          "failures",
          "parser-port",
          "model",
          "component-tests",
          "deterministic-tests",
          "release"
        ]
      },
      {
        "id": "fetcher",
        "name": "3. The fetcher",
        "state": "done",
        "rows": [
          "fetcher",
          "untrusted-factory",
          "validators",
          "commoncheck",
          "logging",
          "gating-tests",
          "component-tests",
          "deterministic-tests",
          "integration-tests",
          "release"
        ]
      },
      {
        "id": "hardening-and-documentation",
        "name": "4. Hardening and documentation",
        "state": "done",
        "rows": [
          "hardening-suite",
          "readme",
          "integration-tests",
          "release"
        ]
      }
    ],
    "remediation": {
      "pass": 1,
      "route": "on-the-spot",
      "steps": [
        {
          "name": "Replace the fixed delays with a latch on the gate parser",
          "state": "done"
        }
      ]
    },
    "open": [
      {
        "text": "The architecture's feeds section says any 2xx is parsed; research shows only 200 and 203 carry a complete feed.",
        "raised": "halted",
        "ref": "research/conditional-get-and-redirects.md",
        "resolved": "2026-11-06T22:05:35Z"
      },
      {
        "text": "The architecture's three hardening layers leave CPU, stack and a JVM-wide namespace cache exposed to a 2 MiB document without a DOCTYPE.",
        "raised": "halted",
        "ref": "research/parse-memory.md",
        "resolved": "2026-11-06T22:05:35Z"
      },
      {
        "text": "The architecture's SyndFeedInput pipeline cannot carry parser limits and shares JDOM's process-wide SAX factory.",
        "raised": "halted",
        "ref": "research/rome-adapter.md",
        "resolved": "2026-11-06T22:05:35Z"
      }
    ],
    "event": {
      "name": "landed",
      "at": "2026-11-07T03:23:42Z"
    },
    "history": {
      "sizing": [
        {
          "since": null,
          "ended": "2026-11-06T04:27:23Z",
          "endedBy": "sized",
          "to": "plan"
        }
      ],
      "plan": [
        {
          "since": "2026-11-06T04:27:23Z",
          "ended": "2026-11-06T04:29:05Z",
          "endedBy": "halted",
          "to": "plan",
          "event": {
            "name": "sized",
            "at": "2026-11-06T04:27:23Z"
          },
          "detail": "Unit 10 of the HTTP client stack: a new webtools-feeds module with an RSS and Atom parser port, a Rome default behind a hardening suite, and a conditional-GET fetcher over UrlFetcher, plus its check-only js module. New public API in a new module; webtools-feeds 1.0.0 is new and unreleased. Next: the detailed plan against the code as it is at 0374c8d.",
          "plan": {
            "state": "re-pass",
            "from": "parent",
            "since": "2026-11-06T04:27:23Z"
          },
          "held": true
        },
        {
          "since": "2026-11-06T04:35:10Z",
          "ended": "2026-11-06T04:51:06Z",
          "endedBy": "halted",
          "to": "plan",
          "event": {
            "name": "resumed",
            "at": "2026-11-06T04:35:10Z"
          },
          "detail": "Work on the feeds unit is taken up again at planning: the unit's slice of the architecture is re-validated against the nine landed units before the implementation plan is written.",
          "plan": {
            "state": "re-pass",
            "from": "parent",
            "since": "2026-11-06T04:27:23Z"
          },
          "held": true
        },
        {
          "since": "2026-11-06T04:52:40Z",
          "ended": "2026-11-06T04:53:22Z",
          "endedBy": "research-planning",
          "to": "research",
          "event": {
            "name": "resumed",
            "at": "2026-11-06T04:52:40Z"
          },
          "detail": "Answers: runs through LoopbackFetchers.untrusted with one test that the owning factory's real policy refuses a loopback URL; the feeds log lines print scheme, host and port only; the owning factory gains an overload taking an OutboundUrlPolicy.Untrusted; both fetch overloads take a trailing FetchSpec block. Research starts next.",
          "plan": {
            "state": "re-pass",
            "from": "parent",
            "since": "2026-11-06T04:27:23Z"
          }
        },
        {
          "since": "2026-11-06T20:11:46Z",
          "ended": "2026-11-06T20:13:25Z",
          "endedBy": "research-started",
          "to": "research",
          "event": {
            "name": "resumed",
            "at": "2026-11-06T20:11:46Z"
          },
          "detail": "Research is taken up again. One of the five questions has its findings written; the other four continue from the work saved when the run stopped. Verification and the implementation plan follow.",
          "plan": {
            "state": "re-pass",
            "from": "parent",
            "since": "2026-11-06T04:27:23Z"
          }
        },
        {
          "since": "2026-11-06T22:05:35Z",
          "ended": "2026-11-06T22:46:50Z",
          "endedBy": "plan-accepted",
          "to": "branch",
          "event": {
            "name": "resumed",
            "at": "2026-11-06T22:05:35Z"
          },
          "detail": "Answers: a 200 or 203 is parsed and every other 2xx is UpstreamError; four structural limits (depth 256, 100 attributes per element, 100,000 elements, 64 namespace URIs) join FeedLimits as tightening-only members enforced by RomeFeedParser and asserted by the hardening contract; RomeFeedParser drives Rome through a private WireFeedInput subclass with its own SAX parser per parse. The implementation plan is written next.",
          "plan": {
            "state": "re-pass",
            "from": "parent",
            "since": "2026-11-06T04:27:23Z"
          }
        }
      ],
      "research": [
        {
          "since": "2026-11-06T04:53:22Z",
          "ended": "2026-11-06T05:16:54Z",
          "endedBy": "halted",
          "to": "research",
          "event": {
            "name": "research-started",
            "at": "2026-11-06T04:57:01Z"
          },
          "detail": "Research: 0 of 5 questions returned; open: `rome-adapter`, `parse-memory`, `entry-identity-and-links`, `conditional-get-and-redirects`, `prolog-scan`.",
          "research": {
            "phase": "research",
            "drafted": 0,
            "since": {
              "planning": "2026-11-06T04:53:22Z",
              "research": "2026-11-06T04:57:01Z"
            }
          },
          "questions": [
            {
              "question": "rome-adapter",
              "rows": [
                "rome-parser",
                "failures",
                "model",
                "hardening-suite"
              ],
              "findings": null,
              "state": "researching",
              "started": "2026-11-06T04:57:01Z"
            },
            {
              "question": "parse-memory",
              "rows": [
                "hardening-suite",
                "fetcher",
                "rome-parser"
              ],
              "findings": null,
              "state": "researching",
              "started": "2026-11-06T04:57:01Z"
            },
            {
              "question": "entry-identity-and-links",
              "rows": [
                "model",
                "parser-port",
                "deterministic-tests"
              ],
              "findings": null,
              "state": "researching",
              "started": "2026-11-06T04:57:01Z"
            },
            {
              "question": "conditional-get-and-redirects",
              "rows": [
                "validators",
                "fetcher",
                "integration-tests"
              ],
              "findings": null,
              "state": "researching",
              "started": "2026-11-06T04:57:01Z"
            },
            {
              "question": "prolog-scan",
              "rows": [
                "prolog-scan",
                "deterministic-tests",
                "failures"
              ],
              "findings": null,
              "state": "researching",
              "started": "2026-11-06T04:57:01Z"
            }
          ],
          "held": true
        },
        {
          "since": "2026-11-06T20:13:25Z",
          "ended": "2026-11-06T20:57:36Z",
          "endedBy": "halted",
          "to": "research",
          "event": {
            "name": "verification-started",
            "at": "2026-11-06T20:48:42Z"
          },
          "detail": "117 load-bearing claims from the five findings files are being re-opened at their sources.",
          "research": {
            "phase": "verification",
            "drafted": 0,
            "since": {
              "planning": "2026-11-06T04:53:22Z",
              "research": "2026-11-06T04:57:01Z",
              "verification": "2026-11-06T20:48:42Z"
            }
          },
          "questions": [
            {
              "question": "rome-adapter",
              "rows": [
                "rome-parser",
                "failures",
                "model",
                "hardening-suite"
              ],
              "findings": "research/rome-adapter.md",
              "state": "verifying",
              "started": "2026-11-06T04:57:01Z",
              "returned": "2026-11-06T20:43:56Z"
            },
            {
              "question": "parse-memory",
              "rows": [
                "hardening-suite",
                "fetcher",
                "rome-parser"
              ],
              "findings": "research/parse-memory.md",
              "state": "verifying",
              "started": "2026-11-06T04:57:01Z",
              "returned": "2026-11-06T20:48:26Z"
            },
            {
              "question": "entry-identity-and-links",
              "rows": [
                "model",
                "parser-port",
                "deterministic-tests"
              ],
              "findings": "research/entry-identity-and-links.md",
              "state": "verifying",
              "started": "2026-11-06T04:57:01Z",
              "returned": "2026-11-06T20:35:09Z"
            },
            {
              "question": "conditional-get-and-redirects",
              "rows": [
                "validators",
                "fetcher",
                "integration-tests"
              ],
              "findings": "research/conditional-get-and-redirects.md",
              "state": "verifying",
              "started": "2026-11-06T04:57:01Z",
              "returned": "2026-11-06T20:15:21Z"
            },
            {
              "question": "prolog-scan",
              "rows": [
                "prolog-scan",
                "deterministic-tests",
                "failures"
              ],
              "findings": "research/prolog-scan.md",
              "state": "verifying",
              "started": "2026-11-06T04:57:01Z",
              "returned": "2026-11-06T20:46:08Z"
            }
          ],
          "held": true
        }
      ],
      "branch": [
        {
          "since": "2026-11-06T22:46:50Z",
          "ended": "2026-11-06T22:58:40Z",
          "endedBy": "branch-cut",
          "to": "implementation",
          "event": {
            "name": "plan-accepted",
            "at": "2026-11-06T22:46:50Z"
          },
          "detail": "webtools-feeds is a new multiplatform module on webtools-http's public seams, with an unpublished check module compiling its common code for js: FeedFetcher does a stateless conditional GET over a UrlFetcher; RomeFeedParser runs the DOCTYPE prolog scan, then Rome with a SAX parser of its own per parse and four structural limits. Four stages, 50 test classes, 328 tests. No Considerations.",
          "branch": "feat/issue-69-feeds"
        }
      ],
      "implementation": [
        {
          "since": "2026-11-06T22:58:40Z",
          "ended": "2026-11-07T00:23:35Z",
          "endedBy": "qa-pass",
          "to": "qa",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-07T00:23:35Z",
            "note": "hardening-and-documentation reported done; Quality Review next"
          },
          "detail": "The hostile-document generators, the child-JVM launcher and mains, the 39-test hardening contract and the six Level 4c non-functional classes: 53 tests, 52 passing and the JDK 11 smoke test skipped because no JDK 11 is set on this host. Every hostile 2 MiB shape is rejected in under 0.5 s in a child JVM at 128 MB with two concurrent parses; 6,400 parses on 16 threads give zero wrong outcomes. No main code changed and the ABI dump is unchanged. The module's tests now match the plan exactly: 50 classes, 328 tests. One further deviation: the isolation test tightens the attribute limit to 50 against the default 100, since the limit tightens only. The README and CHANGELOG part of this stage is written in the document step from the as-built record.",
          "stages": [
            {
              "id": "skeleton-and-vocabulary",
              "name": "1. Skeleton and vocabulary",
              "state": "done",
              "rows": [
                "module",
                "commoncheck",
                "logging",
                "failures",
                "parser-port",
                "model",
                "validators",
                "gating-tests",
                "component-tests",
                "deterministic-tests",
                "release"
              ]
            },
            {
              "id": "prolog-scan-and-rome-parser",
              "name": "2. Prolog scan and RomeFeedParser",
              "state": "done",
              "rows": [
                "prolog-scan",
                "rome-parser",
                "module",
                "logging",
                "failures",
                "parser-port",
                "model",
                "component-tests",
                "deterministic-tests",
                "release"
              ]
            },
            {
              "id": "fetcher",
              "name": "3. The fetcher",
              "state": "done",
              "rows": [
                "fetcher",
                "untrusted-factory",
                "validators",
                "commoncheck",
                "logging",
                "gating-tests",
                "component-tests",
                "deterministic-tests",
                "integration-tests",
                "release"
              ]
            },
            {
              "id": "hardening-and-documentation",
              "name": "4. Hardening and documentation",
              "state": "done",
              "rows": [
                "hardening-suite",
                "readme",
                "integration-tests",
                "release"
              ]
            }
          ]
        }
      ],
      "qa": [
        {
          "since": "2026-11-07T00:23:35Z",
          "ended": "2026-11-07T00:48:29Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-07T00:23:35Z",
            "note": "Quality Review 1: running"
          },
          "detail": "The change set stays inside one system: the two new modules, the settings include and two .gitattributes lines; nothing in webtools-http changes. The review checks the tests and the code against the plan.",
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-07",
              "at": "2026-11-07T00:48:29Z",
              "findings": {
                "documentation": 0,
                "test": 1,
                "code": 0
              },
              "severity": {
                "minor": 1,
                "other": 0
              },
              "route": "on-the-spot",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/69-feeds/ca070e8d/tests/index.html"
            }
          ]
        }
      ],
      "remediation": [
        {
          "since": "2026-11-07T00:48:29Z",
          "ended": "2026-11-07T01:46:46Z",
          "endedBy": "document",
          "to": "document",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-07T01:43:55Z",
            "note": "hardening-and-documentation"
          },
          "detail": "The two Level 2 component tests in FeedFetcherParseComponentTest now prove their negatives through signals on the gate parser and a latch on the fake transport, with no fixed wait; 157 component tests pass. The manual Level 5 acceptance pass ran against nine public cases and every one gave the expected outcome (RSS 2.0, Atom 1.0, a conditional 304, a 301 move reported, LimitExceeded(Entries) and BodyTooLarge for long podcasts, RefusedByPolicy for an https-to-http redirect, NotAFeed(Html) for a web page); its results are in final-implementation.md. The IDE checks and the KDoc read as a consumer sees it are left to the maintainer.",
          "remediation": {
            "pass": 1,
            "route": "on-the-spot",
            "steps": [
              {
                "name": "Replace the fixed delays with a latch on the gate parser",
                "state": "done"
              }
            ]
          },
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-07",
              "at": "2026-11-07T00:48:29Z",
              "findings": {
                "documentation": 0,
                "test": 1,
                "code": 0
              },
              "severity": {
                "minor": 1,
                "other": 0
              },
              "route": "on-the-spot",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/69-feeds/ca070e8d/tests/index.html",
              "note": "Code review passed on every check: the diff stays inside the two new modules, the ABI dump names no Rome, JDOM, SAX or slf4j type, every plan item is built and both deviations are built as logged. One minor test finding, in the As built row the fix touches, `component-tests`: two Level 2 component tests in `FeedFetcherParseComponentTest` prove their negatives with fixed wall-clock delays instead of a synchronisation point on the gate parser; the delays are replaced by a latch on the gate parser, in place, with no additional review. Suite: 328 tests, 327 passed, 0 failed, 1 skipped (`RomeFeedParserJdk11NonFunctionalTest`, by its assumption: no JDK 11 on the build host); `apiCheck` and the Java floor check passed."
            }
          ]
        }
      ],
      "document": [
        {
          "since": "2026-11-07T01:46:46Z",
          "ended": "2026-11-07T03:22:44Z",
          "endedBy": "landing",
          "to": "landing",
          "event": {
            "name": "document",
            "at": "2026-11-07T03:19:53Z",
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
                "name": "Dokka check (not applied in this repository)",
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
                "name": "Document commit",
                "state": "done"
              }
            ]
          }
        }
      ],
      "landing": [
        {
          "since": "2026-11-07T03:22:44Z",
          "ended": "2026-11-07T03:23:34Z",
          "endedBy": "merge",
          "to": "merge",
          "event": {
            "name": "landing",
            "at": "2026-11-07T03:22:44Z",
            "note": "land accepted"
          },
          "detail": "Landing: the land accepted; the branch carries the code, the version bump, the reconciled documents and the state boards."
        }
      ],
      "merge": [
        {
          "since": "2026-11-07T03:23:34Z",
          "ended": "2026-11-07T03:23:42Z",
          "endedBy": "landed",
          "to": "landed",
          "event": {
            "name": "landed",
            "at": "2026-11-07T03:23:42Z"
          },
          "detail": "Landed in PR #96."
        }
      ]
    },
    "next": null,
    "qa": [
      {
        "pass": 1,
        "verdict": "changes-requested",
        "date": "2026-11-07",
        "at": "2026-11-07T00:48:29Z",
        "findings": {
          "documentation": 0,
          "test": 1,
          "code": 0
        },
        "severity": {
          "minor": 1,
          "other": 0
        },
        "route": "on-the-spot",
        "report": "build/reports/tests/index.html",
        "reportCopy": "55-http-client-stack/69-feeds/ca070e8d/tests/index.html",
        "note": "Code review passed on every check: the diff stays inside the two new modules, the ABI dump names no Rome, JDOM, SAX or slf4j type, every plan item is built and both deviations are built as logged. One minor test finding, in the As built row the fix touches, `component-tests`: two Level 2 component tests in `FeedFetcherParseComponentTest` prove their negatives with fixed wall-clock delays instead of a synchronisation point on the gate parser; the delays are replaced by a latch on the gate parser, in place, with no additional review. Suite: 328 tests, 327 passed, 0 failed, 1 skipped (`RomeFeedParserJdk11NonFunctionalTest`, by its assumption: no JDK 11 on the build host); `apiCheck` and the Java floor check passed."
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
        "since": "2026-11-06T04:29:05Z",
        "ended": "2026-11-06T04:35:10Z",
        "by": "halted"
      },
      {
        "step": "plan",
        "since": "2026-11-06T04:51:06Z",
        "ended": "2026-11-06T04:52:40Z",
        "by": "halted"
      },
      {
        "step": "research",
        "since": "2026-11-06T05:16:54Z",
        "ended": "2026-11-06T20:11:46Z",
        "by": "halted"
      },
      {
        "step": "research",
        "since": "2026-11-06T20:57:36Z",
        "ended": "2026-11-06T22:05:35Z",
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
          "name": "Dokka check (not applied in this repository)",
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
          "name": "Document commit",
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
      "note": "#55's questions and findings; question 10 and `feed-parsing.md` bear on this unit. Written on this board at sized from the research README, architecture §6.8, §6.9, §6.10, §12 and §13 and this unit's row in §11, with what units 1 to 9 as built leave it (#55 consequences §7 and §8, and the sibling contracts of #65 and #68), the evidence read at 0374c8d"
    },
    {
      "column": "gap",
      "label": "Research verified, in this unit's run",
      "date": "2026-11-06",
      "ref": "research/README.md",
      "note": "five questions and five findings files: driving Rome 2.1.0, parse memory and the fixed-heap gate, entry identity and links, conditional GET and redirects, and the DOCTYPE prolog scan. 117 load-bearing claims re-opened at source: 116 hold and 1 holds in part, none fails; one earlier finding, the order of Rome's `XmlReader`, holds in strict mode only. The Gap column, written at sized from #55's research and architecture, stands; these findings add their evidence to 3 rows, `fetcher`, `rome-parser` and `hardening-suite`, where three points refine #55's architecture §6.10: the statuses parsed, a fourth layer of hardening and how the parser drives Rome"
    },
    {
      "column": "considered",
      "label": "Architecture signed off, in #55's run",
      "date": "2026-10-21",
      "ref": "../architecture.md",
      "note": "the options #55's architecture weighed for this unit: §6.8, §6.10, §9 and §10, with the feed-parsing findings' considerations and the research summary's settled design behind them; this unit's own plan adds to them at plan acceptance and settles the two points left open (the feeds check module's dependency on the http check module's `js` output, and Rome's DOM amplification under the fixed-heap suite)"
    },
    {
      "column": "considered",
      "label": "The unit's own plan adds to the options",
      "date": "2026-11-06",
      "ref": "plan.md",
      "note": "the options plan.md §6 and this unit's research weigh beside the architecture's, each declined with its reason: parsing every `2xx`, `SyndFeedInput` or Rome's own builder, the structural limits as constants of the parser, a `304` that reports no move, one contract class, a heap fixed on one test task, and `Url.redacted()` for the module's own lines. It settles the two points left open: the check module depends on `webtools-http-commoncheck`'s `js` output, and Rome's DOM amplification is measured and held by a 128 MB fixed-heap gate. The plan also adds a part #55's decomposition does not name, so the board gains the row `gating-tests`, with its Gap and Considered written here"
    },
    {
      "column": "verdict",
      "label": "Plan accepted",
      "date": "2026-11-06",
      "ref": "plan.md",
      "note": "four implementation stages on `feat/issue-69-feeds`, then the README and CHANGELOG and the as-built record, in six commits, the first carrying the plan, this folder's consequences and the architecture's dated corrections. A new module on published seams, every type Stable Core but the gated port and failure group; §6 refines #55's architecture §6.10 in nine points. 50 new test classes, 328 tests, from Level 1 gating to Level 4c non-functional, with a Level 5 manual pass. No version moves: `webtools-feeds` 1.0.0 is new and unreleased"
    },
    {
      "column": "built",
      "label": "Merged",
      "date": "2026-11-07",
      "ref": "PR #96",
      "note": "the research 88af7dc, 066bf17, 5ae0bda and e89d86a; the module, its vocabulary, the prolog scan, `RomeFeedParser` and `FeedFetcher`, stages 1 to 3, e3ecfe2, and the hardening contract and fixed-heap gate 25678cf, the code-landing commit; the as-built record and consequences ac92a59, the README and CHANGELOG e1a98c3, the KDoc and comments 5f9d587, the prolog differential test renamed for the set it parses 32bb375, assertions unchanged, and the boards e55358c; merged as 20d4546. No version moved; `webtools-feeds` 1.0.0 stays unreleased"
    }
  ],
  "trail": [
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Sized; the plan starts"
    },
    {
      "date": "2026-11-06",
      "column": "verdict",
      "label": "Halted at the plan: waiting on you (the run to be started again)"
    },
    {
      "date": "2026-11-06",
      "column": "verdict",
      "label": "Resumed at the plan"
    },
    {
      "date": "2026-11-06",
      "column": "verdict",
      "label": "Halted at the plan: waiting on you (Answers to four interview questions)"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research planning: 0 questions drafted"
    },
    {
      "date": "2026-11-06",
      "column": "verdict",
      "label": "Resumed at the plan with the four answers: the Level 3 integration tests run through `LoopbackFetchers.untrusted`, with one test that the owning factory's real policy refuses a loopback URL; the feeds module's own log lines print a feed URL's scheme, host and port only, never its path or query; the owning factory `FeedFetcher.untrusted` gains an overload taking an `OutboundUrlPolicy.Untrusted`; both `fetch` overloads take a trailing `FetchSpec` block, passed to `UrlFetcher.fetch`"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research started: `rome-adapter`, `parse-memory`, `entry-identity-and-links`, `conditional-get-and-redirects`, `prolog-scan`"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Halted at research: waiting on you (The run being taken up again)"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research started: `rome-adapter`, `parse-memory`, `entry-identity-and-links`, `prolog-scan`"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research returned: `conditional-get-and-redirects`",
      "ref": "research/conditional-get-and-redirects.md"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research returned: `entry-identity-and-links`",
      "ref": "research/entry-identity-and-links.md"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research returned: `rome-adapter`",
      "ref": "research/rome-adapter.md"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research returned: `prolog-scan`",
      "ref": "research/prolog-scan.md"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Research returned: `parse-memory`",
      "ref": "research/parse-memory.md"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Verification started: 5 findings"
    },
    {
      "date": "2026-11-06",
      "column": "gap",
      "label": "Halted at research: waiting on you (Answers to three research questions that would amend the architecture)"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Open point resolved: The architecture's feeds section says any 2xx is parsed; research shows only 200 and 203 carry a complete feed.",
      "ref": "research/conditional-get-and-redirects.md"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Open point resolved: The architecture's three hardening layers leave CPU, stack and a JVM-wide namespace cache exposed to a 2 MiB document without a DOCTYPE.",
      "ref": "research/parse-memory.md"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "Open point resolved: The architecture's SyndFeedInput pipeline cannot carry parser limits and shares JDOM's process-wide SAX factory.",
      "ref": "research/rome-adapter.md"
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
      "label": "Branch `feat/issue-69-feeds` cut; implementation starts at skeleton-and-vocabulary"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "skeleton-and-vocabulary done; prolog-scan-and-rome-parser current: 2. Prolog scan and RomeFeedParser"
    },
    {
      "date": "2026-11-06",
      "column": "built",
      "label": "prolog-scan-and-rome-parser done; fetcher current: 3. The fetcher"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "fetcher done; hardening-and-documentation current: 4. Hardening and documentation"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Implementation done (4 stages); Quality Review next"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Quality Review 1: running"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Quality Review 1: changes requested, 1 finding (1 test; 1 minor)"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Correction: hardening-and-documentation"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Document: Documentation review current"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Document: Reconcile documentation current"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Document: Reconcile plan current"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Document: README and CHANGELOG current"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Document: 5 of 7 tasks done"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Document: State boards current"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Document: Document commit current"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Document: every task done"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Landing: the land accepted"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Merge: merging it in PR #96",
      "ref": "PR #96"
    },
    {
      "date": "2026-11-07",
      "column": "built",
      "label": "Landed in PR #96, merge 20d4546",
      "ref": "PR #96"
    }
  ],
  "rows": [
    {
      "id": "module",
      "group": "Code",
      "name": "The `webtools-feeds` module and its build",
      "system": "webtools-feeds",
      "where": "webtools-feeds/ (new) · settings.gradle.kts",
      "gap": {
        "kind": "missing",
        "summary": "No feeds module exists: the build includes five projects, none for feeds, and no build file names a feed or XML library. The multiplatform convention plugin already carries the Java floor, publishing, the level tasks and the ABI validator; a new module adds only `explicitApi()`, its dependencies, coordinates and dump.",
        "evidence": [
          "settings.gradle.kts:11 — `webtools-udp`, `webtools-scraping`, `webtools-browser`, `webtools-http` and `webtools-http-commoncheck`, no feeds module (baseline)",
          "no `.kts` file names Rome at the baseline",
          "../consequences.md §8 — what the multiplatform plugin carries, what a module adds and what it must not do (units 3 and 10)",
          "../architecture.md §6.10 — the placement, coordinates and dependencies"
        ],
        "source": "../architecture.md §6.10; ../consequences.md §8",
        "link": "../architecture.md#610-webtools-feeds"
      },
      "options": [
        {
          "id": "multiplatform",
          "name": "A Kotlin Multiplatform module on `webtools.kotlin-multiplatform-library`, package `com.spartanlabs.webtools.feeds`, `explicitApi()`, coordinates `webtools-feeds` and `webtools-feeds-jvm` at `1.0.0`; `api(project(\":webtools-http\"))` in `commonMain`, Rome 2.1.0 and `slf4j-api` as `implementation` in `jvmMain`, the `webtools-http` fixtures in `jvmTest`",
          "verdict": "accepted",
          "why": "The port, model and fetcher stay JVM-free in common code like `webtools-http`'s, and Rome stays off a consumer's compile classpath.",
          "source": "../architecture.md §6.10; ../research/README.md § Settled design that follows from the findings, 2",
          "link": "../architecture.md#610-webtools-feeds"
        },
        {
          "id": "jvm-only",
          "name": "A JVM-only feeds module on the Kotlin/JVM convention plugin, one coordinate",
          "verdict": "declined",
          "why": "The research recommended it for one coordinate and the existing convention, with no non-JVM consumer; requirement 4 makes the module multiplatform, and its public types are JVM-free in either shape.",
          "source": "../architecture.md §10; ../research/feed-parsing.md Considerations 1",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "implementation-dep",
          "name": "`webtools-http` as an `implementation` dependency",
          "verdict": "declined",
          "why": "The public API names `UrlFetcher`, `Url`, `HttpFailure`, the marker and `CoroutineDispatcher`, so the dependency is `api`.",
          "source": "../architecture.md §6.10; ../research/README.md question 10",
          "link": "../architecture.md#610-webtools-feeds"
        }
      ],
      "asBuilt": {
        "state": "deviated",
        "summary": null,
        "statements": [
          {
            "text": "`webtools-feeds` was built as a Kotlin Multiplatform module on `webtools.kotlin-multiplatform-library` with `explicitApi()`, coordinates `webtools-feeds` and `webtools-feeds-jvm` at the root build's `1.0.0`, with the unreleased note in its build file.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
            "excerpt": {
              "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
              "heading": "Stage 1 — skeleton and vocabulary",
              "paragraph": "`settings.gradle.kts` includes `:webtools-feeds` and `:webtools-feeds-commoncheck`. · `webtools-feeds/build.gradle.kts` and `webtools-feeds-commoncheck/build.gradle.kts` as the plan gives them; the js actual of `logger` in `webtools-feeds-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/feeds/JsActuals.kt`; a byte-identical `webtools-feeds/src/jvmTest/resources/logback-test.xml`. · `commonMain` (`com.spartanlabs.webtools.feeds`): `Log.kt`, `NonFatal.kt`, `UrlOrigin.kt`, `FeedMessages.kt`; `FeedFailure.kt`, `NotAFeedReason.kt`, `FeedRefusal.kt`, `FeedLimit.kt`; `FeedLimits.kt`; …",
              "hash": "c1404fdd0aaf"
            }
          },
          {
            "text": "`commonMain` takes `api(project(\":webtools-http\"))`, `jvmMain` takes Rome 2.1.0 and `slf4j-api` 2.0.13 as `implementation`, and `jvmTest` takes `webtools-http`'s test fixtures; `verifyJavaFloor` passes with Rome, `rome-utils` and `jdom2` on the runtime classpath.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--the-js-compile-and-the-dump-each-code-stage",
            "excerpt": {
              "ref": "final-implementation.md#p2--the-js-compile-and-the-dump-each-code-stage",
              "heading": "P2 — the `js` compile and the dump (each code stage)",
              "paragraph": "Stage 1: `:webtools-feeds-commoncheck:build` passes with every `commonMain` file of stage 1 compiled for `js` (`String.encodeToByteArray`, `Long.floorDiv`, `StringBuilder.deleteRange` and `CharSequence.lastIndexOf` included). `:webtools-feeds:apiCheck` and `:webtools-feeds:verifyJavaFloor` pass after the dump. · Stage 2: `:webtools-feeds-commoncheck:build` passes with `PrologScan.kt`, `PrologScanFailure.kt` and `ResultExtensions.kt` added (`buildList`, `Enum.entries`, `data object` and `indexOf(…, ignoreCase = true)` compile for `js`); `apiDump` then `apiCheck` pass, the dump diff being …",
              "hash": "c3af15ebf15c"
            }
          },
          {
            "text": "The stack's marker is opted in on `commonMain` and `jvmMain` only, as a language setting, so the test source sets stay consumers.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
            "excerpt": {
              "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
              "heading": "Stage 1 — skeleton and vocabulary",
              "paragraph": "`settings.gradle.kts` includes `:webtools-feeds` and `:webtools-feeds-commoncheck`. · `webtools-feeds/build.gradle.kts` and `webtools-feeds-commoncheck/build.gradle.kts` as the plan gives them; the js actual of `logger` in `webtools-feeds-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/feeds/JsActuals.kt`; a byte-identical `webtools-feeds/src/jvmTest/resources/logback-test.xml`. · `commonMain` (`com.spartanlabs.webtools.feeds`): `Log.kt`, `NonFatal.kt`, `UrlOrigin.kt`, `FeedMessages.kt`; `FeedFailure.kt`, `NotAFeedReason.kt`, `FeedRefusal.kt`, `FeedLimit.kt`; `FeedLimits.kt`; …",
              "hash": "c1404fdd0aaf"
            }
          },
          {
            "text": "`settings.gradle.kts` includes both new modules, and `.gitattributes` gained the rule, with its comment, that keeps `webtools-feeds/src/jvmTest/resources/feeds/` byte-exact; `webtools-http`, its check module and `build-logic` are unchanged.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
            "excerpt": {
              "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
              "heading": "Stage 2 — prolog scan and `RomeFeedParser`",
              "paragraph": "`commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`, `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`). · `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`, `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`), `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` …",
              "hash": "d4f087b76854"
            }
          },
          {
            "text": "The four stages were built in order, each stage's procedures run as it was built; stages 1 to 3 went into one commit, `e3ecfe2`, with the ABI dump as stage 3 leaves it, and stage 4 into `25678cf`, the code-landing commit; the README and CHANGELOG text lands with the as-built record.",
            "state": "deviated",
            "ref": "final-implementation.md#d3-two-implementation-commits-instead-of-four",
            "excerpt": {
              "ref": "final-implementation.md#d3-two-implementation-commits-instead-of-four",
              "heading": "D3. Two implementation commits instead of four",
              "paragraph": "Departs from: plan §5.3, where each stage ends in a commit, and §11, commits 1 to 4, one per stage, each leaving the build green with its own regenerated dump. · As built: `e3ecfe2` carries stages 1 to 3, with the dump as stage 3 leaves it, `plan.md`, `consequences.md` and the dated corrections of `../architecture.md`; `25678cf` carries stage 4's test support and Level 4c classes. The `README.md` and `CHANGELOG.md` text that §5.3 places at the end of stage 4 lands with this record. · Why: the working tree held the three code stages with only the final ABI dump. A commit of stage 1 or stage 2 …",
              "hash": "1a1d0d692a59"
            }
          }
        ],
        "changes": [
          {
            "what": "Two implementation commits instead of four: `e3ecfe2` carries stages 1 to 3 and `25678cf` stage 4, where plan §5.3 and §11 end each stage in a commit with its own regenerated dump.",
            "why": "The working tree held the three code stages with only the final ABI dump, and a commit of stage 1 or stage 2 on its own would have needed that stage's dump to pass `apiCheck`; stage 4 adds no public declaration and passes `apiCheck` against the same dump.",
            "when": "stages 1 to 4",
            "ref": "final-implementation.md#d3-two-implementation-commits-instead-of-four"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One new system, `webtools-feeds`, built on `webtools-http`'s published seams; depending on a sibling module does not make it two systems.",
        "statements": [
          {
            "text": "`webtools-feeds` is a Kotlin Multiplatform module on `webtools.kotlin-multiplatform-library` with `explicitApi()`, coordinates `webtools-feeds` and `webtools-feeds-jvm` at the root build's `1.0.0`, carrying the unreleased note `webtools-http` carries.",
            "ref": "plan.md#r1-the-modules-and-the-build",
            "excerpt": {
              "ref": "plan.md#r1-the-modules-and-the-build",
              "heading": "R1. The modules and the build",
              "paragraph": "`webtools-feeds`: Kotlin Multiplatform on `webtools.kotlin-multiplatform-library` with `explicitApi()`; coordinates `webtools-feeds` and `webtools-feeds-jvm`; version `1.0.0` from the root build, carrying the same \"UNRELEASED … not a release candidate\" note as `webtools-http/build.gradle.kts:1-12`. · The stack's marker `com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi` is opted in on `commonMain` and `jvmMain` only through `languageSettings.optIn`; the test source sets stay consumers. · Dependencies: `commonMain` `api(project(\":webtools-http\"))` (the public API names …",
              "hash": "be0f10034ed7"
            }
          },
          {
            "text": "`commonMain` takes `api(project(\":webtools-http\"))`; `jvmMain` takes Rome 2.1.0 and `slf4j-api` 2.0.13 as `implementation`; `jvmTest` takes `webtools-http`'s test fixtures.",
            "ref": "plan.md#r1-the-modules-and-the-build",
            "excerpt": {
              "ref": "plan.md#r1-the-modules-and-the-build",
              "heading": "R1. The modules and the build",
              "paragraph": "`webtools-feeds`: Kotlin Multiplatform on `webtools.kotlin-multiplatform-library` with `explicitApi()`; coordinates `webtools-feeds` and `webtools-feeds-jvm`; version `1.0.0` from the root build, carrying the same \"UNRELEASED … not a release candidate\" note as `webtools-http/build.gradle.kts:1-12`. · The stack's marker `com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi` is opted in on `commonMain` and `jvmMain` only through `languageSettings.optIn`; the test source sets stay consumers. · Dependencies: `commonMain` `api(project(\":webtools-http\"))` (the public API names …",
              "hash": "be0f10034ed7"
            }
          },
          {
            "text": "The stack's opt-in marker is opted in on `commonMain` and `jvmMain` only, so the test source sets stay consumers.",
            "ref": "plan.md#r1-the-modules-and-the-build",
            "excerpt": {
              "ref": "plan.md#r1-the-modules-and-the-build",
              "heading": "R1. The modules and the build",
              "paragraph": "`webtools-feeds`: Kotlin Multiplatform on `webtools.kotlin-multiplatform-library` with `explicitApi()`; coordinates `webtools-feeds` and `webtools-feeds-jvm`; version `1.0.0` from the root build, carrying the same \"UNRELEASED … not a release candidate\" note as `webtools-http/build.gradle.kts:1-12`. · The stack's marker `com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi` is opted in on `commonMain` and `jvmMain` only through `languageSettings.optIn`; the test source sets stay consumers. · Dependencies: `commonMain` `api(project(\":webtools-http\"))` (the public API names …",
              "hash": "be0f10034ed7"
            }
          },
          {
            "text": "`settings.gradle.kts` includes both new modules and `.gitattributes` gains one line keeping the feed test documents byte-exact; `webtools-http`, its check module and `build-logic` do not change.",
            "ref": "plan.md#71-settingsgradlekts-and-gitattributes-changed",
            "excerpt": {
              "ref": "plan.md#71-settingsgradlekts-and-gitattributes-changed",
              "heading": "7.1 `settings.gradle.kts` and `.gitattributes` (changed)",
              "paragraph": "`settings.gradle.kts` (stage 1): the `include` line becomes `include(\":webtools-udp\", \":webtools-scraping\", \":webtools-browser\", \":webtools-http\", \":webtools-http-commoncheck\", \":webtools-feeds\", \":webtools-feeds-commoncheck\")`. · `.gitattributes` (stage 2, with the first fixture): add after the existing `.http` line",
              "hash": "ecdf105f8eae"
            }
          },
          {
            "text": "It is built in four stages, each compiling and passing its own tests and ending in a commit: the vocabulary and entry rules, the prolog scan and the parser, the fetcher, then the hardening suite and the documents.",
            "ref": "plan.md#53-stages",
            "excerpt": {
              "ref": "plan.md#53-stages",
              "heading": "5.3 Stages",
              "paragraph": "Each stage compiles on its own, its tests pass on their own, and it ends in a commit (§11). A stage that fails its procedure (§9.3) stops the next one.",
              "hash": "c00bc76325f0"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Add both modules to `settings.gradle.kts` and write the feeds build file with its dependencies, opt-in and unreleased note, and the copy of `logback-test.xml`.",
            "files": [
              "settings.gradle.kts",
              "webtools-feeds/build.gradle.kts"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P1, P2)",
            "link": "plan.md#72-webtools-feedsbuildgradlekts-new-stage-1"
          },
          {
            "id": "stage-2",
            "what": "Add the `.gitattributes` line that keeps the feed test documents byte-exact.",
            "files": [
              ".gitattributes"
            ],
            "check": "`:webtools-feeds:componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#71-settingsgradlekts-and-gitattributes-changed"
          }
        ],
        "tests": [
          "Level 1 (gating) `FeedsConsumerGatingTest`, the module consumed with no opt-in; the whole build at the end: `:webtools-feeds:check` with `apiCheck` and `verifyJavaFloor`"
        ]
      }
    },
    {
      "id": "commoncheck",
      "group": "Code",
      "name": "`webtools-feeds-commoncheck`, the check-only `js` compile",
      "system": "js-commoncheck",
      "where": "webtools-feeds-commoncheck/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Only `webtools-http` has a check module, so a JVM API in the feeds module's common code would compile unnoticed. The feeds check module needs `webtools-http`'s types for `js`, and no published `js` artifact exists: depending on the http check module's `js` output compiled in a probe, and is an open point this unit settles, with compiling both common trees as source the fallback.",
        "evidence": [
          "webtools-http-commoncheck/build.gradle.kts:22 — the sibling's `commonMain` added as a relative source directory (baseline)",
          "webtools-http-commoncheck JsActuals.kt:12 — the no-op `js` actual of the `internal` logging seam",
          "../62-http-core/research/common-check-plugin.md F11 — `implementation(project(\":webtools-http-commoncheck\"))` compiled in a probe under `js()` with no environment",
          "../consequences.md §7 — the open point unit 10 settles",
          "../architecture.md §3 — the fallback if the dependency does not resolve"
        ],
        "source": "../architecture.md §6.8, §3; ../consequences.md §7, §8",
        "link": "../architecture.md#68-check-only-js-modules"
      },
      "options": [
        {
          "id": "check-module",
          "name": "A check module on `webtools.kotlin-common-check` (`js()` with no environment, nothing published, no tests): the feeds `commonMain` as a relative source directory, `implementation(project(\":webtools-http-commoncheck\"))` for the http types, its own `jsMain` actuals; compiling both `commonMain` trees as source if that dependency does not resolve",
          "verdict": "accepted",
          "why": "A JVM API in the feeds module's common code becomes a compile error with no new artifact and no Node download; the `internal` helpers of `webtools-http` stay invisible to it, so it carries its own actuals.",
          "source": "../architecture.md §6.8, §3; ../consequences.md §8",
          "link": "../architecture.md#68-check-only-js-modules"
        },
        {
          "id": "js-target",
          "name": "A published `js` target instead of check-only modules",
          "verdict": "declined",
          "why": "A coordinate and a klib nothing consumes, Node and Yarn downloads for tests, and a published surface the requirements do not name.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "unenforced",
          "name": "No check module: an unenforced `commonMain`",
          "verdict": "declined",
          "why": "Every multiplatform module's `commonMain`, the feeds module's included, is compiled for `js` by a check module, so a JVM API there is a compile error.",
          "source": "../research/feed-parsing.md Considerations 1; ../research/README.md § Settled design that follows from the findings",
          "link": "../research/README.md#settled-design-that-follows-from-the-findings"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": null,
        "statements": [
          {
            "text": "`webtools-feeds-commoncheck` was built unpublished, on `webtools.kotlin-common-check`, compiling the feeds `commonMain` as a relative source directory, with the stack's marker opted in on `commonMain` and `jsMain`.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
            "excerpt": {
              "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
              "heading": "Stage 1 — skeleton and vocabulary",
              "paragraph": "`settings.gradle.kts` includes `:webtools-feeds` and `:webtools-feeds-commoncheck`. · `webtools-feeds/build.gradle.kts` and `webtools-feeds-commoncheck/build.gradle.kts` as the plan gives them; the js actual of `logger` in `webtools-feeds-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/feeds/JsActuals.kt`; a byte-identical `webtools-feeds/src/jvmTest/resources/logback-test.xml`. · `commonMain` (`com.spartanlabs.webtools.feeds`): `Log.kt`, `NonFatal.kt`, `UrlOrigin.kt`, `FeedMessages.kt`; `FeedFailure.kt`, `NotAFeedReason.kt`, `FeedRefusal.kt`, `FeedLimit.kt`; `FeedLimits.kt`; …",
              "hash": "c1404fdd0aaf"
            }
          },
          {
            "text": "It depends on `webtools-http-commoncheck` for the http types' `js` output and declares `kotlinx-coroutines-core` 1.10.2 itself.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
            "excerpt": {
              "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
              "heading": "Stage 1 — skeleton and vocabulary",
              "paragraph": "`settings.gradle.kts` includes `:webtools-feeds` and `:webtools-feeds-commoncheck`. · `webtools-feeds/build.gradle.kts` and `webtools-feeds-commoncheck/build.gradle.kts` as the plan gives them; the js actual of `logger` in `webtools-feeds-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/feeds/JsActuals.kt`; a byte-identical `webtools-feeds/src/jvmTest/resources/logback-test.xml`. · `commonMain` (`com.spartanlabs.webtools.feeds`): `Log.kt`, `NonFatal.kt`, `UrlOrigin.kt`, `FeedMessages.kt`; `FeedFailure.kt`, `NotAFeedReason.kt`, `FeedRefusal.kt`, `FeedLimit.kt`; `FeedLimits.kt`; …",
              "hash": "c1404fdd0aaf"
            }
          },
          {
            "text": "Its `jsMain` holds the `js` actual of the logger, from stage 1, and of the default parser, from stage 3, a parser that answers `NotAFeed(UnknownFormat)`; `:webtools-feeds-commoncheck:build` passes after each of the three code stages.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--the-js-compile-and-the-dump-each-code-stage",
            "excerpt": {
              "ref": "final-implementation.md#p2--the-js-compile-and-the-dump-each-code-stage",
              "heading": "P2 — the `js` compile and the dump (each code stage)",
              "paragraph": "Stage 1: `:webtools-feeds-commoncheck:build` passes with every `commonMain` file of stage 1 compiled for `js` (`String.encodeToByteArray`, `Long.floorDiv`, `StringBuilder.deleteRange` and `CharSequence.lastIndexOf` included). `:webtools-feeds:apiCheck` and `:webtools-feeds:verifyJavaFloor` pass after the dump. · Stage 2: `:webtools-feeds-commoncheck:build` passes with `PrologScan.kt`, `PrologScanFailure.kt` and `ResultExtensions.kt` added (`buildList`, `Enum.entries`, `data object` and `indexOf(…, ignoreCase = true)` compile for `js`); `apiDump` then `apiCheck` pass, the dump diff being …",
              "hash": "c3af15ebf15c"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A part of the build's check-only `js` modules, publishing nothing.",
        "statements": [
          {
            "text": "`webtools-feeds-commoncheck` is unpublished, on `webtools.kotlin-common-check`, and compiles the feeds `commonMain` as a relative source directory, with the stack's marker opted in on `commonMain` and `jsMain`.",
            "ref": "plan.md#r1-the-modules-and-the-build",
            "excerpt": {
              "ref": "plan.md#r1-the-modules-and-the-build",
              "heading": "R1. The modules and the build",
              "paragraph": "`webtools-feeds`: Kotlin Multiplatform on `webtools.kotlin-multiplatform-library` with `explicitApi()`; coordinates `webtools-feeds` and `webtools-feeds-jvm`; version `1.0.0` from the root build, carrying the same \"UNRELEASED … not a release candidate\" note as `webtools-http/build.gradle.kts:1-12`. · The stack's marker `com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi` is opted in on `commonMain` and `jvmMain` only through `languageSettings.optIn`; the test source sets stay consumers. · Dependencies: `commonMain` `api(project(\":webtools-http\"))` (the public API names …",
              "hash": "be0f10034ed7"
            }
          },
          {
            "text": "It depends on `webtools-http-commoncheck` for the http types' `js` output, as the earlier probe measured, settling the point left open, and declares `kotlinx-coroutines-core` 1.10.2 itself.",
            "ref": "plan.md#r1-the-modules-and-the-build",
            "excerpt": {
              "ref": "plan.md#r1-the-modules-and-the-build",
              "heading": "R1. The modules and the build",
              "paragraph": "`webtools-feeds`: Kotlin Multiplatform on `webtools.kotlin-multiplatform-library` with `explicitApi()`; coordinates `webtools-feeds` and `webtools-feeds-jvm`; version `1.0.0` from the root build, carrying the same \"UNRELEASED … not a release candidate\" note as `webtools-http/build.gradle.kts:1-12`. · The stack's marker `com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi` is opted in on `commonMain` and `jvmMain` only through `languageSettings.optIn`; the test source sets stay consumers. · Dependencies: `commonMain` `api(project(\":webtools-http\"))` (the public API names …",
              "hash": "be0f10034ed7"
            }
          },
          {
            "text": "Its `jsMain` holds a `js` actual for every `expect` of the feeds `commonMain`: the logger from the first stage, the default parser from the third.",
            "ref": "plan.md#73-webtools-feeds-commoncheck-new-stages-1-and-3",
            "excerpt": {
              "ref": "plan.md#73-webtools-feeds-commoncheck-new-stages-1-and-3",
              "heading": "7.3 `webtools-feeds-commoncheck` (new, stages 1 and 3)",
              "paragraph": "`webtools-feeds-commoncheck/build.gradle.kts` (stage 1), modelled on `webtools-http-commoncheck/build.gradle.kts`:",
              "hash": "51fff5431a15"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Write the check module's build file and its `js` actual of the logger.",
            "files": [
              "webtools-feeds-commoncheck/build.gradle.kts"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P1, P2)",
            "link": "plan.md#73-webtools-feeds-commoncheck-new-stages-1-and-3"
          },
          {
            "id": "stage-3",
            "what": "Add the `js` actual of the default parser.",
            "files": [
              "webtools-feeds-commoncheck/src/jsMain/"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `integrationTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#73-webtools-feeds-commoncheck-new-stages-1-and-3"
          }
        ],
        "tests": [
          "No tests of its own: `:webtools-feeds-commoncheck:build` compiles the feeds `commonMain` for `js` after each code stage (P2)"
        ]
      }
    },
    {
      "id": "parser-port",
      "group": "Code",
      "name": "The `FeedParser` port, `FeedInput` and `FeedLimits`",
      "system": "webtools-feeds",
      "where": "commonMain (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing parses a feed, and no seam lets a consumer put another parser behind the stack. Every document is hostile input: its size is bounded only by the transport's decoded-byte cap, and its fields and entry counts are bounded by nothing.",
        "evidence": [
          "webtools-http HttpFailure.kt:18 — the stack's gate marker on a subclassable seam, `@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)` (baseline)",
          "../research/feed-parsing.md Pitfalls 4 — a count cap that drops entries silently can hide exactly the new ones",
          "../research/feed-parsing.md Considerations 2 — a plain or a `suspend` parse"
        ],
        "source": "../research/README.md § Feeds module; ../architecture.md §6.10",
        "link": "../research/README.md#feeds-module"
      },
      "options": [
        {
          "id": "plain-gated",
          "name": "A gated `FeedParser` interface with a plain `parse(input: FeedInput): Result<Feed>`; `FeedInput(document, contentType, baseUrl)`; `FeedLimits` built by factory and builder, tighten only, `maxEntries` 1,000 failing loudly with field and count caps beside it",
          "verdict": "accepted",
          "why": "The parse is CPU-bound and bounded by the byte cap, so a plain function is a pure function of bytes the fetcher moves off the caller's thread. Experimental to implement, graduating once a consumer has built against it.",
          "source": "../architecture.md §6.10, §9",
          "link": "../architecture.md#610-webtools-feeds"
        },
        {
          "id": "suspend-parse",
          "name": "A `suspend` parse, uniform with the rest of the stack",
          "verdict": "declined",
          "why": "It implies a cancellation point a blocking DOM parse does not have, and forces coroutine test plumbing into pure tests.",
          "source": "../research/feed-parsing.md Considerations 2",
          "link": "../research/feed-parsing.md#2-is-feedparserparse-a-plain-function-or-suspend"
        },
        {
          "id": "silent-truncation",
          "name": "Truncating to the first N entries",
          "verdict": "declined",
          "why": "Feeds are not guaranteed newest-first, so a silent drop can hide the new entries; the cap fails with `LimitExceeded` instead.",
          "source": "../research/feed-parsing.md Pitfalls 4",
          "link": "../research/feed-parsing.md#pitfalls-and-regrets"
        },
        {
          "id": "flat-constructor",
          "name": "`FeedLimits` as a flat constructor with defaulted parameters",
          "verdict": "declined",
          "why": "A permanent overload per addition, kept correct by hand; a builder is additive by construction.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "fixed-structural",
          "name": "The structural limits as fixed constants of `RomeFeedParser`",
          "verdict": "declined",
          "why": "A deployment could not tighten them, and a parser behind the port would not be held to them.",
          "source": "research/README.md § Options that do not fit",
          "link": "research/README.md#options-that-do-not-fit"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": null,
        "statements": [
          {
            "text": "`FeedParser` was built as an interface gated by `@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)`, with a plain, non-suspending `parse(input: FeedInput): Result<Feed>`.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
            "excerpt": {
              "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
              "heading": "Stage 1 — skeleton and vocabulary",
              "paragraph": "`settings.gradle.kts` includes `:webtools-feeds` and `:webtools-feeds-commoncheck`. · `webtools-feeds/build.gradle.kts` and `webtools-feeds-commoncheck/build.gradle.kts` as the plan gives them; the js actual of `logger` in `webtools-feeds-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/feeds/JsActuals.kt`; a byte-identical `webtools-feeds/src/jvmTest/resources/logback-test.xml`. · `commonMain` (`com.spartanlabs.webtools.feeds`): `Log.kt`, `NonFatal.kt`, `UrlOrigin.kt`, `FeedMessages.kt`; `FeedFailure.kt`, `NotAFeedReason.kt`, `FeedRefusal.kt`, `FeedLimit.kt`; `FeedLimits.kt`; …",
              "hash": "c1404fdd0aaf"
            }
          },
          {
            "text": "Its KDoc binds an implementation to refuse every DOCTYPE, honour every `FeedLimits` member and report failures as `FeedFailure` leaves with constant messages; the 39-test conformance contract runs against `RomeFeedParser` through the port.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
            "excerpt": {
              "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
              "heading": "Stage 2 — prolog scan and `RomeFeedParser`",
              "paragraph": "`commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`, `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`). · `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`, `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`), `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` …",
              "hash": "d4f087b76854"
            }
          },
          {
            "text": "`FeedInput(document, contentType, baseUrl)` was built as a final class that copies the document in and out and prints only sizes and presence; its constructor is in the ABI dump.",
            "state": "as-planned",
            "ref": "final-implementation.md#p1--the-digest-and-the-dump-stage-1",
            "excerpt": {
              "ref": "final-implementation.md#p1--the-digest-and-the-dump-stage-1",
              "heading": "P1 — the digest and the dump (stage 1)",
              "paragraph": "`sha256(\"webtools-feeds/entry-id/1\")` starts `b2025d24e4ca603fe646f1d2df2ea6e5` and `sha256(\"webtools-feeds/entry-id/1\\nt5:Hello\\nd10:1700000000\")` starts `ee7d21b287ec8fde2600e24ba184edb7`: both match the first and the seventh digest vector, computed with an independent SHA-256 and reproduced by the module's own `sha256` in `EntryDigestDeterministicTest`. · After `:webtools-feeds:apiDump`, `grep -n \"<init>\" webtools-feeds/api/webtools-feeds.api` lists constructors only on the four `FeedFailure` leaves and the group's two `protected` ones, `FeedFetch.Fetched`, `FeedFetch.NotModified`, …",
              "hash": "c66a2d3acc0c"
            }
          },
          {
            "text": "`FeedLimits` was built with its factory, `@WebToolsDsl` builder, defaults and floors as the plan states them, and has no constructor in the ABI dump.",
            "state": "as-planned",
            "ref": "final-implementation.md#p1--the-digest-and-the-dump-stage-1",
            "excerpt": {
              "ref": "final-implementation.md#p1--the-digest-and-the-dump-stage-1",
              "heading": "P1 — the digest and the dump (stage 1)",
              "paragraph": "`sha256(\"webtools-feeds/entry-id/1\")` starts `b2025d24e4ca603fe646f1d2df2ea6e5` and `sha256(\"webtools-feeds/entry-id/1\\nt5:Hello\\nd10:1700000000\")` starts `ee7d21b287ec8fde2600e24ba184edb7`: both match the first and the seventh digest vector, computed with an independent SHA-256 and reproduced by the module's own `sha256` in `EntryDigestDeterministicTest`. · After `:webtools-feeds:apiDump`, `grep -n \"<init>\" webtools-feeds/api/webtools-feeds.api` lists constructors only on the four `FeedFailure` leaves and the group's two `protected` ones, `FeedFetch.Fetched`, `FeedFetch.NotModified`, …",
              "hash": "c66a2d3acc0c"
            }
          },
          {
            "text": "`RomeFeedParser` applies the limits: more entries than the limit, or a structural limit reached, fails the parse with `LimitExceeded`, and the text, id, link, category and enclosure limits truncate, fall back or drop in the conversion.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
            "excerpt": {
              "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
              "heading": "Stage 2 — prolog scan and `RomeFeedParser`",
              "paragraph": "`commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`, `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`). · `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`, `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`), `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` …",
              "hash": "d4f087b76854"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The module's extension point, which consumers and fakes substitute, with its input and limits.",
        "statements": [
          {
            "text": "`FeedParser` is an interface gated by `@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)`, Experimental to implement, with a plain, non-suspending, thread-safe `parse(input: FeedInput): Result<Feed>` that never throws for a bad document.",
            "ref": "plan.md#r8-feedparser-and-feedinput",
            "excerpt": {
              "ref": "plan.md#r8-feedparser-and-feedinput",
              "heading": "R8. `FeedParser` and `FeedInput`",
              "paragraph": "`parse` is plain and non-suspending, thread-safe, and never throws for a bad document; its KDoc contract states what an implementation must do: refuse every DOCTYPE, honour every `FeedLimits` member and report failures as `FeedFailure` leaves with constant messages. · `FeedInput` copies the document in and reads it back as a copy; its `toString()` prints sizes and presence only.",
              "hash": "1a6f014a65ed"
            }
          },
          {
            "text": "Its KDoc binds an implementation to refuse every DOCTYPE, honour every `FeedLimits` member and report failures as `FeedFailure` leaves with constant messages.",
            "ref": "plan.md#r8-feedparser-and-feedinput",
            "excerpt": {
              "ref": "plan.md#r8-feedparser-and-feedinput",
              "heading": "R8. `FeedParser` and `FeedInput`",
              "paragraph": "`parse` is plain and non-suspending, thread-safe, and never throws for a bad document; its KDoc contract states what an implementation must do: refuse every DOCTYPE, honour every `FeedLimits` member and report failures as `FeedFailure` leaves with constant messages. · `FeedInput` copies the document in and reads it back as a copy; its `toString()` prints sizes and presence only.",
              "hash": "1a6f014a65ed"
            }
          },
          {
            "text": "`FeedInput(document, contentType, baseUrl)` is a final Stable Core class that copies the document in and reads it back as a copy, and prints only sizes and presence.",
            "ref": "plan.md#r8-feedparser-and-feedinput",
            "excerpt": {
              "ref": "plan.md#r8-feedparser-and-feedinput",
              "heading": "R8. `FeedParser` and `FeedInput`",
              "paragraph": "`parse` is plain and non-suspending, thread-safe, and never throws for a bad document; its KDoc contract states what an implementation must do: refuse every DOCTYPE, honour every `FeedLimits` member and report failures as `FeedFailure` leaves with constant messages. · `FeedInput` copies the document in and reads it back as a copy; its `toString()` prints sizes and presence only.",
              "hash": "1a6f014a65ed"
            }
          },
          {
            "text": "`FeedLimits` is built by factory and `@WebToolsDsl` builder, tighten only between a floor and its default: entries 1,000, text 262,144, id 2,048 and link 8,000 characters, 32 categories and 8 enclosures per entry, and four structural limits, depth 256, 100 attributes per element, 100,000 elements and 64 namespace URIs.",
            "ref": "plan.md#r3-feedlimits",
            "excerpt": {
              "ref": "plan.md#r3-feedlimits",
              "heading": "R3. `FeedLimits`",
              "paragraph": "Members and defaults: `maxEntries` 1,000; `maxTextChars` 262,144; `maxIdChars` 2,048; `maxLinkChars` 8,000; `maxCategoriesPerEntry` 32; `maxEnclosuresPerEntry` 8; `maxDepth` 256; `maxAttributesPerElement` 100; `maxElements` 100,000; `maxNamespaceUris` 64. The defaults are public constants and `FeedLimits.Defaults` holds them as a value. · Tighten only. The builder refuses a value above its default or below its floor with `IllegalArgumentException` and a constant message that names the property and never a value. Floors: `maxEntries` 1, `maxTextChars` 16, `maxIdChars` 50 (the digest id is …",
              "hash": "6c5f2212619c"
            }
          },
          {
            "text": "Exceeding the entry limit or a structural limit fails the parse with `LimitExceeded`; the text, id, link, category and enclosure limits truncate, fall back or drop.",
            "ref": "plan.md#r3-feedlimits",
            "excerpt": {
              "ref": "plan.md#r3-feedlimits",
              "heading": "R3. `FeedLimits`",
              "paragraph": "Members and defaults: `maxEntries` 1,000; `maxTextChars` 262,144; `maxIdChars` 2,048; `maxLinkChars` 8,000; `maxCategoriesPerEntry` 32; `maxEnclosuresPerEntry` 8; `maxDepth` 256; `maxAttributesPerElement` 100; `maxElements` 100,000; `maxNamespaceUris` 64. The defaults are public constants and `FeedLimits.Defaults` holds them as a value. · Tighten only. The builder refuses a value above its default or below its floor with `IllegalArgumentException` and a constant message that names the property and never a value. Floors: `maxEntries` 1, `maxTextChars` 16, `maxIdChars` 50 (the digest id is …",
              "hash": "6c5f2212619c"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Write `FeedParser`, `FeedInput` and `FeedLimits` with its builder, factories and defaults.",
            "files": [
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/FeedParser.kt",
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/FeedInput.kt",
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/FeedLimits.kt"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P1, P2)",
            "link": "plan.md#76-cmfeedlimitskt-new-stage-1"
          }
        ],
        "tests": [
          "Level 2 (component) `FeedLimitsComponentTest` and `FeedInputComponentTest`; Level 1 (gating) `FeedsConsumerGatingTest`; the structural limits in the Level 4c hardening contract"
        ],
        "risks": [
          "The structural limits rest on samples of 24 and 61 feed files; a legitimate document beyond one fails with `LimitExceeded` (§10)"
        ]
      }
    },
    {
      "id": "model",
      "group": "Code",
      "name": "The feed model and its id and link rules",
      "system": "webtools-feeds",
      "where": "commonMain (new)",
      "gap": {
        "kind": "missing",
        "summary": "There is no feed type, and every field of a fetched document is attacker text: links may be `javascript:`, `file:`, `data:` or relative, ids may be megabytes long, dates may be far in the future or unparseable, and summaries may carry markup.",
        "evidence": [
          "../research/feed-parsing.md Pitfalls 10 — hostile content fields",
          "../research/feed-parsing.md Pitfalls 11 — a null date is a normal value; never use dates for \"is new\"",
          "../research/feed-parsing.md F1 Dates — Rome returns null for a date no mask matches and keeps no raw string"
        ],
        "source": "../research/feed-parsing.md; ../architecture.md §6.10",
        "link": "../research/feed-parsing.md#pitfalls-and-regrets"
      },
      "options": [
        {
          "id": "immutable-model",
          "name": "Immutable final classes `Feed`, `FeedEntry` and `FeedEnclosure`: an id never blank (guid or Atom id, else the link, else a pure-Kotlin digest of title and date), a link absolute `http` or `https` only, else null, dates as nullable epoch milliseconds never used for \"is new\", summary and content documented as untrusted markup",
          "verdict": "accepted",
          "why": "A caller tells new entries by id, so identity must never be blank; the link and length rules keep hostile text out of what a consumer posts. Stable Core.",
          "source": "../architecture.md §6.10, §9",
          "link": "../architecture.md#610-webtools-feeds"
        },
        {
          "id": "data-classes",
          "name": "Public data classes for the model",
          "verdict": "declined",
          "why": "No public `data class` in the stack: plain classes keep the model open to binary-compatible evolution.",
          "source": "../architecture.md §9; ../research/feed-parsing.md Recommendation for this repo",
          "link": "../architecture.md#9-extension--stability"
        },
        {
          "id": "time-types",
          "name": "`kotlin.time.Instant` or kotlinx-datetime for the dates",
          "verdict": "declined",
          "why": "An error-level opt-in on every Kotlin 2.2 consumer.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "title-date-digest",
          "name": "A fallback digest of title and date alone, as #55's architecture §6.10 first wrote",
          "verdict": "declined",
          "why": "An RSS 2.0 item may carry only a description, and a digest without the title would collapse such entries.",
          "source": "plan.md §6, 7",
          "link": "plan.md#6-where-this-plan-refines-the-parent-architecture"
        },
        {
          "id": "weak-identity",
          "name": "A 64-bit non-cryptographic digest, an undelimited concatenation of fields, a random fallback id, or case folding or Unicode normalisation of ids",
          "verdict": "declined",
          "why": "A short digest can collide inside a feed, an undelimited message hashes a title `ab` as a title `a` and a field `b`, and a random id changes on every parse.",
          "source": "research/README.md § Options that do not fit",
          "link": "research/README.md#options-that-do-not-fit"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": null,
        "statements": [
          {
            "text": "`Feed`, `FeedEntry` and `FeedEnclosure` were built as final classes with factories and `@WebToolsDsl` builders, value equality over every field and a `toString()` that prints no text content and no URL path or query; the ABI dump holds no model constructor and no `copy` or `component` function.",
            "state": "as-planned",
            "ref": "final-implementation.md#p1--the-digest-and-the-dump-stage-1",
            "excerpt": {
              "ref": "final-implementation.md#p1--the-digest-and-the-dump-stage-1",
              "heading": "P1 — the digest and the dump (stage 1)",
              "paragraph": "`sha256(\"webtools-feeds/entry-id/1\")` starts `b2025d24e4ca603fe646f1d2df2ea6e5` and `sha256(\"webtools-feeds/entry-id/1\\nt5:Hello\\nd10:1700000000\")` starts `ee7d21b287ec8fde2600e24ba184edb7`: both match the first and the seventh digest vector, computed with an independent SHA-256 and reproduced by the module's own `sha256` in `EntryDigestDeterministicTest`. · After `:webtools-feeds:apiDump`, `grep -n \"<init>\" webtools-feeds/api/webtools-feeds.api` lists constructors only on the four `FeedFailure` leaves and the group's two `protected` ones, `FeedFetch.Fetched`, `FeedFetch.NotModified`, …",
              "hash": "c66a2d3acc0c"
            }
          },
          {
            "text": "Links are canonical absolute `http` or `https` strings with their fragment, or null; dates are nullable epoch milliseconds inside years 1 to 9999; summary and content are kept as untrusted markup.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
            "excerpt": {
              "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
              "heading": "Stage 1 — skeleton and vocabulary",
              "paragraph": "`settings.gradle.kts` includes `:webtools-feeds` and `:webtools-feeds-commoncheck`. · `webtools-feeds/build.gradle.kts` and `webtools-feeds-commoncheck/build.gradle.kts` as the plan gives them; the js actual of `logger` in `webtools-feeds-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/feeds/JsActuals.kt`; a byte-identical `webtools-feeds/src/jvmTest/resources/logback-test.xml`. · `commonMain` (`com.spartanlabs.webtools.feeds`): `Log.kt`, `NonFatal.kt`, `UrlOrigin.kt`, `FeedMessages.kt`; `FeedFailure.kt`, `NotAFeedReason.kt`, `FeedRefusal.kt`, `FeedLimit.kt`; `FeedLimits.kt`; …",
              "hash": "c1404fdd0aaf"
            }
          },
          {
            "text": "An entry's id follows the plan's chain down to the frozen `webtools-feeds:v1:` digest in pure Kotlin, which matches an independent SHA-256 on the digest vectors and `MessageDigest` in the differential test.",
            "state": "as-planned",
            "ref": "final-implementation.md#p1--the-digest-and-the-dump-stage-1",
            "excerpt": {
              "ref": "final-implementation.md#p1--the-digest-and-the-dump-stage-1",
              "heading": "P1 — the digest and the dump (stage 1)",
              "paragraph": "`sha256(\"webtools-feeds/entry-id/1\")` starts `b2025d24e4ca603fe646f1d2df2ea6e5` and `sha256(\"webtools-feeds/entry-id/1\\nt5:Hello\\nd10:1700000000\")` starts `ee7d21b287ec8fde2600e24ba184edb7`: both match the first and the seventh digest vector, computed with an independent SHA-256 and reproduced by the module's own `sha256` in `EntryDigestDeterministicTest`. · After `:webtools-feeds:apiDump`, `grep -n \"<init>\" webtools-feeds/api/webtools-feeds.api` lists constructors only on the four `FeedFailure` leaves and the group's two `protected` ones, `FeedFetch.Fetched`, `FeedFetch.NotModified`, …",
              "hash": "c66a2d3acc0c"
            }
          },
          {
            "text": "A blank guid or link candidate counts as absent, through the `isNullOrBlank` tightening of `entryId` the plan calls for, so no id is blank.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape, with the deviations of §2. Every signature, constant, message and floor is as the plan states them, and the identity, link and text rules are the verified reference with only the package, the file split and the `isNullOrBlank` tightening of `entryId` the plan calls for.",
              "hash": "61dec779cae6"
            }
          },
          {
            "text": "The conversion resolves links against the response's final URL and the Atom feed's and entry's `xml:base`, trims and truncates text without splitting a surrogate pair, and de-duplicates, then caps, categories and enclosures.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
            "excerpt": {
              "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
              "heading": "Stage 2 — prolog scan and `RomeFeedParser`",
              "paragraph": "`commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`, `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`). · `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`, `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`), `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` …",
              "hash": "d4f087b76854"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Vocabulary with no platform content, every type Stable Core.",
        "statements": [
          {
            "text": "`Feed`, `FeedEntry` and `FeedEnclosure` are final classes built by factories with `@WebToolsDsl` builders, with value equality over every field and a `toString()` that prints no text content and no URL path or query.",
            "ref": "plan.md#r4-the-model",
            "excerpt": {
              "ref": "plan.md#r4-the-model",
              "heading": "R4. The model",
              "paragraph": "`Feed(title, link, description, entries)`; `FeedEntry(id, title, link, summary, content, author, publishedAtEpochMillis, updatedAtEpochMillis, categories, enclosures)`; `FeedEnclosure(url, mimeType, lengthBytes)`. Links are `String?` (an absolute `http` or `https` link with the fragment kept; `Url` is not used: it drops fragments and refuses non-ASCII hosts). Dates are epoch milliseconds, `null` when absent, unparseable or outside years 1 to 9999, and are never used to decide whether an entry is new. Summary and content are untrusted markup. · The factories `require` the invariants a value …",
              "hash": "fd615abe864c"
            }
          },
          {
            "text": "A link is a canonical absolute `http` or `https` string with its fragment kept, or null; dates are nullable epoch milliseconds inside years 1 to 9999, never used to decide what is new; summary and content are untrusted markup.",
            "ref": "plan.md#r4-the-model",
            "excerpt": {
              "ref": "plan.md#r4-the-model",
              "heading": "R4. The model",
              "paragraph": "`Feed(title, link, description, entries)`; `FeedEntry(id, title, link, summary, content, author, publishedAtEpochMillis, updatedAtEpochMillis, categories, enclosures)`; `FeedEnclosure(url, mimeType, lengthBytes)`. Links are `String?` (an absolute `http` or `https` link with the fragment kept; `Url` is not used: it drops fragments and refuses non-ASCII hosts). Dates are epoch milliseconds, `null` when absent, unparseable or outside years 1 to 9999, and are never used to decide whether an entry is new. Summary and content are untrusted markup. · The factories `require` the invariants a value …",
              "hash": "fd615abe864c"
            }
          },
          {
            "text": "An entry's id is its trimmed guid or Atom id, else its link as written, else a frozen SHA-256 digest in pure Kotlin, `webtools-feeds:v1:` and 32 hex digits, over the first present of title, summary or content and first enclosure URL, with the published date.",
            "ref": "plan.md#r13-identity-links-and-text-internal-commonmain-pure-kotlin",
            "excerpt": {
              "ref": "plan.md#r13-identity-links-and-text-internal-commonmain-pure-kotlin",
              "heading": "R13. Identity, links and text (`internal`, `commonMain`, pure Kotlin)",
              "paragraph": "Exactly as `research/entry-identity-and-links.md` Recommendation specifies (§5.6 below restates the frozen parts): the id rule, the digest (`webtools-feeds:v1:` + 32 hex digits, over SHA-256 in pure Kotlin), the link rule with RFC 3986 §5.2 resolution and `allowRelative = false` for a link Rome derived from a guid, the text rules, the category and enclosure rules. · One deliberate tightening of the research's reference: a guid or link candidate for the id that is blank (Kotlin `isBlank()`, which includes U+00A0 and the other Unicode spaces) counts as absent, so the id that results always …",
              "hash": "5142d46b3a8c"
            }
          },
          {
            "text": "A blank guid or link candidate counts as absent, so the id that results is never blank.",
            "ref": "plan.md#r13-identity-links-and-text-internal-commonmain-pure-kotlin",
            "excerpt": {
              "ref": "plan.md#r13-identity-links-and-text-internal-commonmain-pure-kotlin",
              "heading": "R13. Identity, links and text (`internal`, `commonMain`, pure Kotlin)",
              "paragraph": "Exactly as `research/entry-identity-and-links.md` Recommendation specifies (§5.6 below restates the frozen parts): the id rule, the digest (`webtools-feeds:v1:` + 32 hex digits, over SHA-256 in pure Kotlin), the link rule with RFC 3986 §5.2 resolution and `allowRelative = false` for a link Rome derived from a guid, the text rules, the category and enclosure rules. · One deliberate tightening of the research's reference: a guid or link candidate for the id that is blank (Kotlin `isBlank()`, which includes U+00A0 and the other Unicode spaces) counts as absent, so the id that results always …",
              "hash": "5142d46b3a8c"
            }
          },
          {
            "text": "Links resolve by RFC 3986 §5.2 against the response's final URL and the Atom feed's and entry's `xml:base`; text is trimmed and truncated without splitting a surrogate pair; categories and enclosures are de-duplicated, then capped.",
            "ref": "plan.md#r13-identity-links-and-text-internal-commonmain-pure-kotlin",
            "excerpt": {
              "ref": "plan.md#r13-identity-links-and-text-internal-commonmain-pure-kotlin",
              "heading": "R13. Identity, links and text (`internal`, `commonMain`, pure Kotlin)",
              "paragraph": "Exactly as `research/entry-identity-and-links.md` Recommendation specifies (§5.6 below restates the frozen parts): the id rule, the digest (`webtools-feeds:v1:` + 32 hex digits, over SHA-256 in pure Kotlin), the link rule with RFC 3986 §5.2 resolution and `allowRelative = false` for a link Rome derived from a guid, the text rules, the category and enclosure rules. · One deliberate tightening of the research's reference: a guid or link candidate for the id that is blank (Kotlin `isBlank()`, which includes U+00A0 and the other Unicode spaces) counts as absent, so the id that results always …",
              "hash": "5142d46b3a8c"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Write the model with its builders, and the text, link, SHA-256 and identity rules from the verified reference.",
            "files": [
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/FeedEntry.kt",
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/EntryIdentity.kt",
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/LinkRules.kt"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P1, P2)",
            "link": "plan.md#710-the-rules-new-internal-stage-1"
          }
        ],
        "tests": [
          "Level 2 (component) `FeedComponentTest`, `FeedEntryComponentTest`, `FeedEnclosureComponentTest`; Level 4a (deterministic) `Sha256DeterministicTest`, `EntryDigestDeterministicTest`, `EntryIdDeterministicTest`, `LinkRuleDeterministicTest`, `TextRulesDeterministicTest`"
        ],
        "risks": [
          "A digest id depends on the text the parser produces: a Rome upgrade or another parser can change the id of an entry with no guid and no usable link, so the rule is frozen and a new one takes a new prefix (§10)"
        ]
      }
    },
    {
      "id": "failures",
      "group": "Code",
      "name": "`FeedFailure` and its reasons",
      "system": "webtools-feeds",
      "where": "commonMain (new)",
      "gap": {
        "kind": "missing",
        "summary": "No failure says that a document is not a feed, is malformed, was refused or exceeded a limit. `HttpFailure` is open for exactly this, through its protected constructors, and a fetch can already fail with every transport, policy and upstream leaf of the stack.",
        "evidence": [
          "webtools-http HttpFailure.kt:19 — the open abstract root, `:25` and `:33` its protected constructors `(message)` and `(message, cause)` (baseline)",
          "../consequences.md §8 — unit 10's `FeedFailure` carries its own gate and extends `HttpFailure`",
          "../research/README.md § Where the findings differ — the findings' sealed `FeedFailure` rooted at `Throwable` follows the open `HttpFailure` convention instead"
        ],
        "source": "../consequences.md §8; ../research/README.md § Where the findings differ, and which view holds",
        "link": "../consequences.md#8-what-the-later-units-build-on-units-1-to-10-as-built"
      },
      "options": [
        {
          "id": "under-httpfailure",
          "name": "An abstract, subclass-gated `FeedFailure` under `HttpFailure` with final leaves `NotAFeed(reason)`, `Malformed`, `Refused(reason)` and `LimitExceeded(limit)`, reasons as constants classes, constant messages with no document content; transport, policy and upstream failures pass through `fetch` unchanged",
          "verdict": "accepted",
          "why": "One `Result` carries either family, and the feeds module adds no leaf that duplicates a stack leaf. Every leaf is Stable Core; subclassing is Experimental.",
          "source": "../architecture.md §6.10, §9",
          "link": "../architecture.md#610-webtools-feeds"
        },
        {
          "id": "sealed-root",
          "name": "A sealed `FeedFailure` rooted at `Throwable`, or enums for its reasons",
          "verdict": "declined",
          "why": "Each added leaf or entry is a compile error in a consumer's exhaustive `when` and a runtime error in a compiled one.",
          "source": "../architecture.md §10; ../research/README.md § Where the findings differ, and which view holds",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "unexpected-status",
          "name": "An `UnexpectedStatus` leaf for `404`, `410`, `429` and `5xx`",
          "verdict": "declined",
          "why": "A non-`2xx`/`304` is already `UpstreamError` and a throttle `RateLimited` from `UrlFetcher`.",
          "source": "../architecture.md §6.10; ../research/feed-parsing.md Gaps G5",
          "link": "../architecture.md#610-webtools-feeds"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": null,
        "statements": [
          {
            "text": "`FeedFailure` was built as an abstract group under `HttpFailure`, gated for subclassing, with the final leaves `NotAFeed(reason)`, `Malformed`, `Refused(reason)` and `LimitExceeded(limit)`; the ABI dump shows public constructors on the four leaves and the group's two `protected` ones.",
            "state": "as-planned",
            "ref": "final-implementation.md#p1--the-digest-and-the-dump-stage-1",
            "excerpt": {
              "ref": "final-implementation.md#p1--the-digest-and-the-dump-stage-1",
              "heading": "P1 — the digest and the dump (stage 1)",
              "paragraph": "`sha256(\"webtools-feeds/entry-id/1\")` starts `b2025d24e4ca603fe646f1d2df2ea6e5` and `sha256(\"webtools-feeds/entry-id/1\\nt5:Hello\\nd10:1700000000\")` starts `ee7d21b287ec8fde2600e24ba184edb7`: both match the first and the seventh digest vector, computed with an independent SHA-256 and reproduced by the module's own `sha256` in `EntryDigestDeterministicTest`. · After `:webtools-feeds:apiDump`, `grep -n \"<init>\" webtools-feeds/api/webtools-feeds.api` lists constructors only on the four `FeedFailure` leaves and the group's two `protected` ones, `FeedFetch.Fetched`, `FeedFetch.NotModified`, …",
              "hash": "c66a2d3acc0c"
            }
          },
          {
            "text": "A leaf holds no cause and no document content, and its message is the module's constant text with at most a reason's name; the Rome failure mapping keeps no cause or message from Rome.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
            "excerpt": {
              "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
              "heading": "Stage 1 — skeleton and vocabulary",
              "paragraph": "`settings.gradle.kts` includes `:webtools-feeds` and `:webtools-feeds-commoncheck`. · `webtools-feeds/build.gradle.kts` and `webtools-feeds-commoncheck/build.gradle.kts` as the plan gives them; the js actual of `logger` in `webtools-feeds-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/feeds/JsActuals.kt`; a byte-identical `webtools-feeds/src/jvmTest/resources/logback-test.xml`. · `commonMain` (`com.spartanlabs.webtools.feeds`): `Log.kt`, `NonFatal.kt`, `UrlOrigin.kt`, `FeedMessages.kt`; `FeedFailure.kt`, `NotAFeedReason.kt`, `FeedRefusal.kt`, `FeedLimit.kt`; `FeedLimits.kt`; …",
              "hash": "c1404fdd0aaf"
            }
          },
          {
            "text": "The reasons are the open constants classes `NotAFeedReason`, `FeedRefusal` and `FeedLimit` with the plan's members; the prolog scan's outcomes map onto the leaves in `toFailureOrNull`, and Rome's exceptions in `toFeedFailure`.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
            "excerpt": {
              "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
              "heading": "Stage 2 — prolog scan and `RomeFeedParser`",
              "paragraph": "`commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`, `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`). · `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`, `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`), `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` …",
              "hash": "d4f087b76854"
            }
          },
          {
            "text": "The scan and `RomeFeedParser` return every failure of a document in the `Result` and never throw for one; a configuration out of range or a broken model invariant throws `IllegalArgumentException` at construction.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
            "excerpt": {
              "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
              "heading": "Stage 2 — prolog scan and `RomeFeedParser`",
              "paragraph": "`commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`, `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`). · `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`, `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`), `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` …",
              "hash": "d4f087b76854"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The failure vocabulary, under the stack's open `HttpFailure`.",
        "statements": [
          {
            "text": "`FeedFailure` is an abstract group under `HttpFailure`, Experimental to subclass, with final Stable Core leaves `NotAFeed(reason)`, `Malformed`, `Refused(reason)` and `LimitExceeded(limit)`, each with a public constructor.",
            "ref": "plan.md#r7-feedfailure",
            "excerpt": {
              "ref": "plan.md#r7-feedfailure",
              "heading": "R7. `FeedFailure`",
              "paragraph": "Leaves, each final with a public constructor and a constant message of the form `<constant text>` or `<constant text>: <reason name>`: `NotAFeed(reason: NotAFeedReason)`, `Malformed`, `Refused(reason: FeedRefusal)`, `LimitExceeded(limit: FeedLimit)`. A leaf never holds a cause and never holds document content. · Reasons (open constants classes: private constructor, constants in the companion, equality by name, `toString()` the name): `NotAFeedReason` `Html`, `Empty`, `NotXml`, `UnknownFormat`; `FeedRefusal` `Doctype`, `UnsupportedEncoding`, `PrologTooLong`, `AmbiguousProlog`; `FeedLimit` …",
              "hash": "622b274af3c3"
            }
          },
          {
            "text": "A leaf holds no cause and no document content, and its message is constant text with at most a reason's name.",
            "ref": "plan.md#r7-feedfailure",
            "excerpt": {
              "ref": "plan.md#r7-feedfailure",
              "heading": "R7. `FeedFailure`",
              "paragraph": "Leaves, each final with a public constructor and a constant message of the form `<constant text>` or `<constant text>: <reason name>`: `NotAFeed(reason: NotAFeedReason)`, `Malformed`, `Refused(reason: FeedRefusal)`, `LimitExceeded(limit: FeedLimit)`. A leaf never holds a cause and never holds document content. · Reasons (open constants classes: private constructor, constants in the companion, equality by name, `toString()` the name): `NotAFeedReason` `Html`, `Empty`, `NotXml`, `UnknownFormat`; `FeedRefusal` `Doctype`, `UnsupportedEncoding`, `PrologTooLong`, `AmbiguousProlog`; `FeedLimit` …",
              "hash": "622b274af3c3"
            }
          },
          {
            "text": "The reasons are open constants classes: `NotAFeedReason` (`Html`, `Empty`, `NotXml`, `UnknownFormat`), `FeedRefusal` (`Doctype`, `UnsupportedEncoding`, `PrologTooLong`, `AmbiguousProlog`) and `FeedLimit` (`Entries`, `Depth`, `Attributes`, `Elements`, `NamespaceUris`).",
            "ref": "plan.md#r7-feedfailure",
            "excerpt": {
              "ref": "plan.md#r7-feedfailure",
              "heading": "R7. `FeedFailure`",
              "paragraph": "Leaves, each final with a public constructor and a constant message of the form `<constant text>` or `<constant text>: <reason name>`: `NotAFeed(reason: NotAFeedReason)`, `Malformed`, `Refused(reason: FeedRefusal)`, `LimitExceeded(limit: FeedLimit)`. A leaf never holds a cause and never holds document content. · Reasons (open constants classes: private constructor, constants in the companion, equality by name, `toString()` the name): `NotAFeedReason` `Html`, `Empty`, `NotXml`, `UnknownFormat`; `FeedRefusal` `Doctype`, `UnsupportedEncoding`, `PrologTooLong`, `AmbiguousProlog`; `FeedLimit` …",
              "hash": "622b274af3c3"
            }
          },
          {
            "text": "No function of the module throws for a bad document, a bad response or a policy refusal; a configuration out of range or a broken model invariant throws `IllegalArgumentException` at construction.",
            "ref": "plan.md#r15-error-handling",
            "excerpt": {
              "ref": "plan.md#r15-error-handling",
              "heading": "R15. Error handling",
              "paragraph": "Expected failures are `Result` values: no function of this module throws for a bad document, a bad response or a policy refusal. Programmer errors (a configuration out of range, a model invariant) throw `IllegalArgumentException` at construction. `CancellationException` and `Error` propagate. §7.23 of this plan lists the failure path of every new signature.",
              "hash": "9049f4b2e758"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Write `FeedFailure`, its leaves and the three reasons classes with the module's constant texts.",
            "files": [
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/FeedFailure.kt",
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/FeedMessages.kt"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P1, P2)",
            "link": "plan.md#75-the-failure-group-and-its-reasons-new-stage-1"
          },
          {
            "id": "stage-2",
            "what": "Map the prolog scan's outcomes onto the leaves.",
            "files": [
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/PrologScanFailure.kt"
            ],
            "check": "`:webtools-feeds:componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#711-cmprologscankt-and-cmprologscanfailurekt-new-internal-stage-2"
          }
        ],
        "tests": [
          "Level 2 (component) `FeedFailureComponentTest`, `PrologScanFailureComponentTest`, `RomeFailureMappingComponentTest`; Level 1 (gating) `FeedsConsumerGatingTest`, a failure matched with an `else`"
        ]
      }
    },
    {
      "id": "validators",
      "group": "Code",
      "name": "`FeedValidators` and `FeedFetch`: the caller-owned conditional-GET state",
      "system": "webtools-feeds",
      "where": "commonMain (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing carries a feed's `ETag` and `Last-Modified` between polls. HTTP wants both sent back verbatim, a weak `ETag` unchanged, and a `304` may carry new validators that must replace the stored ones; the first consumer already keeps them in its own store.",
        "evidence": [
          "../research/feed-parsing.md F4 — RFC 9110 §13.1.2 and §13.1.3; a reader that kept stale validators after a `304`; the first consumer persists validators per subscription",
          "../68-call-facades/consequences.md §4 — `UrlFetcher` returns a `304` as a response, and `If-None-Match` and `If-Modified-Since` are not headers the transport controls"
        ],
        "source": "../research/feed-parsing.md F4",
        "link": "../research/feed-parsing.md#f4-conditional-get-the-caller-owns-the-state-in-the-mature-parsing-libraries-the-one-stateful-fetcher-is-archived-common-practice--consensus-on-http"
      },
      "options": [
        {
          "id": "caller-owned",
          "name": "A stateless fetch: `FeedValidators(etag, lastModified)` with verbatim header values (oversize or non-printable values null) and `None`; a sealed `FeedFetch`, `Fetched(feed, validators, finalUrl, permanentlyMovedTo)` or `NotModified(validators, finalUrl)`, a `304` a success value, and the caller storing the returned validators after every response",
          "verdict": "accepted",
          "why": "The caller's own store stays the one source of truth, and a closed pair of outcomes is the contract. Stable Core.",
          "source": "../architecture.md §6.10, §9",
          "link": "../architecture.md#610-webtools-feeds"
        },
        {
          "id": "stateful-fetcher",
          "name": "A stateful feed fetcher holding validators",
          "verdict": "declined",
          "why": "An unbounded map keyed by administrator-supplied URLs, lost on restart; the only such fetcher is archived, and the mature parsers are stateless.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "no-move-on-304",
          "name": "`NotModified(validators, finalUrl)` with no move, as #55's architecture §6.10 first wrote",
          "verdict": "declined",
          "why": "\"Not modified\" is the common answer for an unchanged feed behind a permanent redirect, so a reader that skips the move on a `304` never learns it while the feed stays unchanged.",
          "source": "plan.md §6, 4; research/conditional-get-and-redirects.md F9 to F11, F15",
          "link": "plan.md#6-where-this-plan-refines-the-parent-architecture"
        },
        {
          "id": "stored-as-received",
          "name": "Storing validators as received, truncating them, or reformatting `Last-Modified`",
          "verdict": "declined",
          "why": "The stack's `HttpRequest` refuses any header character outside HTAB and U+0020 to U+007E, so a stored non-ASCII tag would fail every later fetch of that feed; a cut or reprinted value no longer matches.",
          "source": "research/README.md § Conditional GET and redirects",
          "link": "research/README.md#conditional-get-and-redirects"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": null,
        "statements": [
          {
            "text": "`FeedValidators(etag, lastModified)` was built with the plan's rules and `FeedValidators.None`; the fetcher reads a response's validators through it, a header counting only when exactly one line of that name is present.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
            "excerpt": {
              "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
              "heading": "Stage 1 — skeleton and vocabulary",
              "paragraph": "`settings.gradle.kts` includes `:webtools-feeds` and `:webtools-feeds-commoncheck`. · `webtools-feeds/build.gradle.kts` and `webtools-feeds-commoncheck/build.gradle.kts` as the plan gives them; the js actual of `logger` in `webtools-feeds-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/feeds/JsActuals.kt`; a byte-identical `webtools-feeds/src/jvmTest/resources/logback-test.xml`. · `commonMain` (`com.spartanlabs.webtools.feeds`): `Log.kt`, `NonFatal.kt`, `UrlOrigin.kt`, `FeedMessages.kt`; `FeedFailure.kt`, `NotAFeedReason.kt`, `FeedRefusal.kt`, `FeedLimit.kt`; `FeedLimits.kt`; …",
              "hash": "c1404fdd0aaf"
            }
          },
          {
            "text": "`FeedFetch` was built as a sealed interface declaring `validators`, `finalUrl` and `permanentlyMovedTo`, with the final `Fetched` and `NotModified` leaves and no third.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
            "excerpt": {
              "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
              "heading": "Stage 1 — skeleton and vocabulary",
              "paragraph": "`settings.gradle.kts` includes `:webtools-feeds` and `:webtools-feeds-commoncheck`. · `webtools-feeds/build.gradle.kts` and `webtools-feeds-commoncheck/build.gradle.kts` as the plan gives them; the js actual of `logger` in `webtools-feeds-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/feeds/JsActuals.kt`; a byte-identical `webtools-feeds/src/jvmTest/resources/logback-test.xml`. · `commonMain` (`com.spartanlabs.webtools.feeds`): `Log.kt`, `NonFatal.kt`, `UrlOrigin.kt`, `FeedMessages.kt`; `FeedFailure.kt`, `NotAFeedReason.kt`, `FeedRefusal.kt`, `FeedLimit.kt`; `FeedLimits.kt`; …",
              "hash": "c1404fdd0aaf"
            }
          },
          {
            "text": "The module keeps no validator and no subscription: `FeedFetcher` sends the validators it is handed and returns those of each response, a `304`'s refreshing the ones sent, for the caller to store.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The caller-owned conditional-GET state and the closed pair of outcomes, Stable Core.",
        "statements": [
          {
            "text": "`FeedValidators(etag, lastModified)` applies its rules to whatever it is given: trimmed, an ETag at most 256 and a `Last-Modified` at most 64 characters of U+0020 to U+007E, an ETag never `*`, otherwise null and never truncated; `FeedValidators.None` holds neither.",
            "ref": "plan.md#r5-feedvalidators",
            "excerpt": {
              "ref": "plan.md#r5-feedvalidators",
              "heading": "R5. `FeedValidators`",
              "paragraph": "`FeedValidators(etag: String?, lastModified: String?)` applies the rules to what it is given, so a value loaded from storage obeys them too: trim SP and HTAB at both ends; empty means `null`; the ETag is at most 256 and the `Last-Modified` at most 64 characters; every character is U+0020 to U+007E; an ETag is not `*`; otherwise the value is `null`, never truncated. `FeedValidators.None` holds neither. `toString()` prints presence, never a value.",
              "hash": "81a771d323b2"
            }
          },
          {
            "text": "`FeedFetch` is a sealed interface declaring `validators`, `finalUrl` and `permanentlyMovedTo`, with final `Fetched` and `NotModified` leaves; a third leaf is never added.",
            "ref": "plan.md#r6-feedfetch",
            "excerpt": {
              "ref": "plan.md#r6-feedfetch",
              "heading": "R6. `FeedFetch`",
              "paragraph": "`sealed interface FeedFetch` declares `validators: FeedValidators`, `finalUrl: Url` and `permanentlyMovedTo: Url?`; the final leaves are `Fetched(feed, validators, finalUrl, permanentlyMovedTo)` and `NotModified(validators, finalUrl, permanentlyMovedTo)`, with hand-written equality (the shape of `Lookup`). A third leaf is never added: a later case becomes a field.",
              "hash": "54e1705caf06"
            }
          },
          {
            "text": "The module keeps no validator and no subscription: the caller stores the validators a fetch returns after every response.",
            "ref": "plan.md#51-shape-one-new-system-on-published-seams",
            "excerpt": {
              "ref": "plan.md#51-shape-one-new-system-on-published-seams",
              "heading": "5.1 Shape: one new system on published seams",
              "paragraph": "`webtools-feeds` composes only public parts of `webtools-http`: it asks a `UrlFetcher` for a `GET`, reads the `HttpResponse`, and hands the body to a `FeedParser`. It adds no cache, no budget and no transport. Stateless means the module keeps no validator and no subscription: the caller stores `FeedFetch.validators` after every response.",
              "hash": "e609966cd080"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Write `FeedValidators` and the sealed `FeedFetch` with its two leaves.",
            "files": [
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/FeedValidators.kt",
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/FeedFetch.kt"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P1, P2)",
            "link": "plan.md#79-cmfeedvalidatorskt-and-cmfeedfetchkt-new-stage-1"
          }
        ],
        "tests": [
          "Level 2 (component) `FeedValidatorsComponentTest`, `FeedFetchComponentTest`; Level 4a (deterministic) `FeedValidatorsDeterministicTest`"
        ],
        "risks": [
          "The transport sends both conditional headers to every hop, so a validator can reach another server after a redirect changes; the caller drops its validators when `finalUrl` changes (§10)"
        ]
      }
    },
    {
      "id": "fetcher",
      "group": "Code",
      "name": "`FeedFetcher` over `UrlFetcher`",
      "system": "webtools-feeds",
      "where": "commonMain (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing fetches a feed: no request with a feed `Accept` list and conditional headers, no reading of a `304`, no parse off the caller's thread, and no report of a permanent move. `UrlFetcher` returns every status as a response but a throttle, and each followed redirect hop now records its status.",
        "evidence": [
          "webtools-http UrlFetcher.kt:55 — `fetch(request) { … }: Result<HttpResponse>` (baseline)",
          "webtools-http UrlFetcher.kt:110 — the common factory over a transport and a policy, which fakes reach",
          "webtools-http RedirectHop.kt:19 — each hop's `status`, `301` or `308` for a permanent move; HttpResponse.kt:25 `redirectChain`",
          "../research/feed-parsing.md Pitfalls 8 — parsing on the caller's thread",
          "research/conditional-get-and-redirects.md F5 — only a `200` or `203` is a complete feed for a plain `GET`: a `204` or `205` has no content, a `206` answers a range request and a `226` a request with `A-IM`"
        ],
        "source": "../68-call-facades/consequences.md §4; ../architecture.md §6.10",
        "link": "../68-call-facades/consequences.md#4-sibling-units"
      },
      "options": [
        {
          "id": "over-urlfetcher",
          "name": "A final `FeedFetcher : AutoCloseable` from a common factory over a `UrlFetcher` and a builder (`parser`, `parseDispatcher`, `maxConcurrentParses` 2): a `GET` with an `Accept` list and the validators verbatim; a `304` to a conditional request `NotModified`; a `2xx` parsed under a semaphore on `parseDispatcher`, then `Fetched` with the response's validators, `None` when it sent none; `permanentlyMovedTo` from a `301` or `308` hop; polling, back-off, a `410` and new items left to the caller",
          "verdict": "accepted",
          "why": "It gets the policy pre-check at no cost and a throttle as `RateLimited` without parsing `Retry-After` itself, and fakes still reach it through `UrlFetcher(transport, policy)`.",
          "source": "../architecture.md §6.10",
          "link": "../architecture.md#610-webtools-feeds"
        },
        {
          "id": "on-transport",
          "name": "The feed fetcher on `HttpTransport` directly",
          "verdict": "declined",
          "why": "It would lose the free policy pre-check and repeat the stack's throttle and failure conventions.",
          "source": "../architecture.md §10; ../research/README.md § Where the findings differ, and which view holds",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "upstream-client",
          "name": "Through `UpstreamClient`, with a response cache and a provider's budget",
          "verdict": "declined",
          "why": "A conditional GET already is the cache, and a budget keyed to one provider's windows does not fit a population of administrator-chosen hosts.",
          "source": "../research/feed-parsing.md F6",
          "link": "../research/feed-parsing.md#f6-feed-polling-is-a-scheduling-concern-of-the-caller-and-the-http-layer-already-supplies-what-a-single-fetch-needs-reasoned--repository-facts"
        },
        {
          "id": "final-url-only",
          "name": "Reporting the final URL only, with no permanent move",
          "verdict": "declined",
          "why": "A caller that rewrites a subscription on a `301` needs the move; recording each hop's status in the chain closes that.",
          "source": "../research/README.md § Where the findings differ, and which view holds; ../research/feed-parsing.md Gaps G8",
          "link": "../research/README.md#where-the-findings-differ-and-which-view-holds"
        },
        {
          "id": "any-2xx",
          "name": "Parse every `2xx`, as #55's architecture §6.10 first wrote",
          "verdict": "declined",
          "why": "An empty `204` reads as a document that is not a feed, and a `206` fragment or a `226` delta reads as the whole feed.",
          "source": "plan.md §6, 1; research/conditional-get-and-redirects.md F5",
          "link": "plan.md#6-where-this-plan-refines-the-parent-architecture"
        },
        {
          "id": "move-rules",
          "name": "Adopting a move only when every hop is permanent, or from any permanent hop",
          "verdict": "declined",
          "why": "The first under-reports `A -301-> B -302-> C`; the second adopts a temporary target.",
          "source": "research/README.md § Options that do not fit",
          "link": "research/README.md#options-that-do-not-fit"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "text": "`FeedFetcher` was built as a final `AutoCloseable` from the common factory over a caller's `UrlFetcher`, with `fetch(url) { }` and `fetch(url, validators) { }` passing their block to `UrlFetcher.fetch`; `close()` closes only a `UrlFetcher` the fetcher built, and the ABI dump holds no constructor of it.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          },
          {
            "text": "A fetch is a `GET` with the configured `Accept` and the validators sent exactly as held; a failure of the `UrlFetcher` passes through as the same value, and a fetch that starts after `close()` returns `Closed(\"feed fetcher\")` with no request reaching the server. The class KDoc adds the `Closed(\"transport\")` of a fetch still on the network when an owning fetcher closes and the `Closed(\"fetcher\")` a fetch racing `close()` may meet, and tells the caller to match `HttpFailure.Closed`.",
            "state": "amended",
            "ref": "final-implementation.md#d5-feedfetchers-kdoc-names-each-closed-a-fetch-can-meet-around-close",
            "excerpt": {
              "ref": "final-implementation.md#d5-feedfetchers-kdoc-names-each-closed-a-fetch-can-meet-around-close",
              "heading": "D5. `FeedFetcher`'s KDoc names each `Closed` a fetch can meet around `close()`",
              "paragraph": "Departs from: plan §7.16, whose class KDoc gives the lifecycle as \"a fetch after [close] returns `Closed`\", and plan §6, where `Closed(\"feed fetcher\")` is a component name of the fetcher's own \"so a caller can tell it from the `Closed(\"fetcher\")` of a `UrlFetcher` it closed itself\". · As built: the code is §7.16's: a fetch that starts after `close()` returns `Closed(\"feed fetcher\")`. The class KDoc adds that closing a fetcher that owns its `UrlFetcher` closes the transport under it, so that each fetch still on the network returns `Closed(\"transport\")`, and that a fetch racing `close()` may …",
              "hash": "337b842c9787"
            }
          },
          {
            "text": "`statusVerdictOf` parses a `200` or `203` into `Fetched` with the response's validators, answers a `304` to a conditional request with `NotModified` and the sent validators refreshed by those it carries, and makes every other status, a `304` to an unconditional request included, `UpstreamError`.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          },
          {
            "text": "The parse runs on `parseDispatcher` under a semaphore of `maxConcurrentParses`, two by default; an exception the parser throws, or a failure it returns that is not an `HttpFailure`, becomes `DecodeFailure`, and a running parse is not interrupted.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          },
          {
            "text": "`permanentMoveOf` reports the target of the leading run of `301` and `308` hops on both leaves, so a `302` at the head moves nothing; polling, back-off, a `410` and new items stay the caller's, as the fetcher's KDoc lists them.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          },
          {
            "text": "`FeedFetcherConfig` sets the parser, by default the platform's through the `internal expect fun defaultFeedParser()` (`RomeFeedParser()` on the JVM), the parse dispatcher, the parse concurrency and `Accept`, and refuses a concurrency below 1 or an `Accept` that cannot be sent; the ABI dump holds no constructor of it.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          }
        ],
        "changes": [
          {
            "what": "The class KDoc names each `Closed` a fetch can meet around `close()`, `Closed(\"transport\")` and the composed `UrlFetcher`'s `Closed(\"fetcher\")` beside `Closed(\"feed fetcher\")`, and tells the caller to match `HttpFailure.Closed`, where plan §7.16 and §6 gave `Closed(\"feed fetcher\")` alone. The code is §7.16's.",
            "why": "A fetch that passed the closed check before `close()` goes on into the `UrlFetcher` the fetcher has just closed, or is cancelled on the network by the transport's close, and every failure of the `UrlFetcher` passes through unchanged; the component name tells a caller which part refused, not whether its own fetcher was closed. The close-race test counts any `HttpFailure.Closed` as the closed outcome.",
            "when": "Document",
            "ref": "final-implementation.md#d5-feedfetchers-kdoc-names-each-closed-a-fetch-can-meet-around-close"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A stateless composition of the published `UrlFetcher`, Stable Core.",
        "statements": [
          {
            "text": "`FeedFetcher` is a final `AutoCloseable` from the common factory over a caller's `UrlFetcher`; `fetch(url) { }` and `fetch(url, validators) { }` pass their block to `UrlFetcher.fetch`, and `close()` closes only a `UrlFetcher` it built.",
            "ref": "plan.md#r10-feedfetcherconfig-feedfetcher-and-the-factories",
            "excerpt": {
              "ref": "plan.md#r10-feedfetcherconfig-feedfetcher-and-the-factories",
              "heading": "R10. `FeedFetcherConfig`, `FeedFetcher` and the factories",
              "paragraph": "`FeedFetcherConfig` (`@WebToolsDsl`): `parser` (default: the platform's default parser, `RomeFeedParser()` on the JVM), `parseDispatcher` (`Dispatchers.Default`), `maxConcurrentParses` (2, at least 1) and `accept` (default `application/atom+xml, application/rss+xml, application/rdf+xml;q=0.9, application/xml;q=0.8, text/xml;q=0.8, */*;q=0.1`; refused at construction when blank or not a sendable header value). · `FeedFetcher : AutoCloseable`, final, with a `public companion object`: `suspend fun fetch(url, configure = {})` and `suspend fun fetch(url, validators, configure = {})`, each …",
              "hash": "90cb08c2c790"
            }
          },
          {
            "text": "A fetch is a `GET` with the configured `Accept` and the stored validators sent verbatim; every failure of the `UrlFetcher` passes through unchanged, and a closed fetcher returns `Closed(\"feed fetcher\")`.",
            "ref": "plan.md#r9-the-fetch",
            "excerpt": {
              "ref": "plan.md#r9-the-fetch",
              "heading": "R9. The fetch",
              "paragraph": "In order: a closed fetcher returns `HttpFailure.Closed(\"feed fetcher\")`; the request is `GET url` with `Accept` and, when known, `If-None-Match` and `If-Modified-Since` exactly as stored; `urlFetcher.fetch(request, configure)`; every failure passes through unchanged. On a response:",
              "hash": "bc92d9af7f1f"
            }
          },
          {
            "text": "A `200` or `203` is parsed into `Fetched` with the response's validators; a `304` to a conditional request is `NotModified` with the sent validators refreshed by those it carries; every other status, a `304` to an unconditional request included, is `UpstreamError`.",
            "ref": "plan.md#r9-the-fetch",
            "excerpt": {
              "ref": "plan.md#r9-the-fetch",
              "heading": "R9. The fetch",
              "paragraph": "In order: a closed fetcher returns `HttpFailure.Closed(\"feed fetcher\")`; the request is `GET url` with `Accept` and, when known, `If-None-Match` and `If-Modified-Since` exactly as stored; `urlFetcher.fetch(request, configure)`; every failure passes through unchanged. On a response:",
              "hash": "bc92d9af7f1f"
            }
          },
          {
            "text": "The parse runs on `parseDispatcher` under a semaphore of `maxConcurrentParses`, two by default; a parser's failure that is not an `HttpFailure`, or an exception it throws, becomes `DecodeFailure`, and a running parse is not interrupted.",
            "ref": "plan.md#55-the-parse-path",
            "excerpt": {
              "ref": "plan.md#55-the-parse-path",
              "heading": "5.5 The parse path",
              "paragraph": "`parse` is non-suspending and CPU-bound. The fetcher moves it off the caller's thread with `withContext(parseDispatcher)` and bounds the number of parses running at once with a `Semaphore` (`maxConcurrentParses`), so a flood of feeds cannot pin every core. A parse cannot be cancelled; `withContext` returns to a cancelled caller only after the parse finished, and the permit is held until then. · Memory: a body is copied by `HttpResponse.body()` and by the `FeedInput` constructor; `RomeFeedParser` reads the input through an `internal` view that does not copy again. With the four structural …",
              "hash": "25ee417cb6aa"
            }
          },
          {
            "text": "`permanentlyMovedTo` is the target of the leading run of `301` and `308` hops, reported on both leaves; polling, back-off, a `410` and new items stay the caller's.",
            "ref": "plan.md#54-conditional-get-r9-restated-as-rules",
            "excerpt": {
              "ref": "plan.md#54-conditional-get-r9-restated-as-rules",
              "heading": "5.4 Conditional GET (R9, restated as rules)",
              "paragraph": "Request. `Accept` is the configured value. `If-None-Match` is `validators.etag` when non-null and `If-Modified-Since` is `validators.lastModified` when non-null, each exactly as held. Both are sent together when both are known (RFC 9110 §13.1.3 makes a server ignore `If-Modified-Since` when `If-None-Match` is present, so the pair is the interoperable form). Nothing is reformatted: a date is never parsed and reprinted, a tag is never normalised. · Validators on a response. From a `200` or `203`: exactly one `ETag` line and exactly one `Last-Modified` line count; zero or several mean absent; …",
              "hash": "a9347c720b76"
            }
          },
          {
            "text": "`FeedFetcherConfig` sets the parser, the platform default through an `internal expect fun`, the parse dispatcher, the parse concurrency and `Accept`.",
            "ref": "plan.md#r10-feedfetcherconfig-feedfetcher-and-the-factories",
            "excerpt": {
              "ref": "plan.md#r10-feedfetcherconfig-feedfetcher-and-the-factories",
              "heading": "R10. `FeedFetcherConfig`, `FeedFetcher` and the factories",
              "paragraph": "`FeedFetcherConfig` (`@WebToolsDsl`): `parser` (default: the platform's default parser, `RomeFeedParser()` on the JVM), `parseDispatcher` (`Dispatchers.Default`), `maxConcurrentParses` (2, at least 1) and `accept` (default `application/atom+xml, application/rss+xml, application/rdf+xml;q=0.9, application/xml;q=0.8, text/xml;q=0.8, */*;q=0.1`; refused at construction when blank or not a sendable header value). · `FeedFetcher : AutoCloseable`, final, with a `public companion object`: `suspend fun fetch(url, configure = {})` and `suspend fun fetch(url, validators, configure = {})`, each …",
              "hash": "90cb08c2c790"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-3",
            "what": "Write `FeedFetcherConfig`, the pure conditional-GET rules and `FeedFetcher` with its common factory and the default-parser `expect`.",
            "files": [
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/FeedFetcherConfig.kt",
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/ResponseVerdict.kt",
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/FeedFetcher.kt"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `integrationTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#716-cmfeedfetcherkt-new-stage-3"
          }
        ],
        "tests": [
          "Level 2 (component) the fetcher's request, status, moves, pass-through, parse, lifecycle and configuration classes; Level 4a (deterministic) `ConditionalHeadersDeterministicTest`, `ResponseVerdictDeterministicTest`, `PermanentMoveDeterministicTest`; Level 3 (integration) `FeedFetcherLoopbackIntegrationTest`; Level 4c (non-functional) `FeedFetcherCloseRaceNonFunctionalTest`"
        ],
        "risks": [
          "A parse cannot be cancelled: a cancelled caller returns when its parse finishes, and a parser without bounds of its own can hold a permit (§10)",
          "`allowHttpsToHttp` off makes a feed that redirects from `https` to `http` unreachable under the defaults (§10)"
        ]
      }
    },
    {
      "id": "prolog-scan",
      "group": "Code",
      "name": "The DOCTYPE prolog scan",
      "system": "webtools-feeds",
      "where": "commonMain (new, internal)",
      "gap": {
        "kind": "risk",
        "summary": "Every entity attack needs a DOCTYPE, but Rome applies its DOCTYPE ban only when the SAX provider recognises the feature and skips it silently otherwise; turning DTD processing off in a StAX reader does not necessarily reject a DOCTYPE, and how the JDK's StAX reader treats one is unmeasured.",
        "evidence": [
          "../research/feed-parsing.md F1 — Rome's `isFeatureSupported` swallows an unrecognised feature and returns false",
          "../research/feed-parsing.md F5 — the OWASP guidance: reject DOCTYPE declarations; disabling DTDs is not the same",
          "../research/feed-parsing.md Gaps G3 — the JDK StAX reader with DTD support off, unestablished",
          "../architecture.md §13 — until measured, the scan is assumed necessary"
        ],
        "source": "../research/feed-parsing.md F1, F5; ../architecture.md §13",
        "link": "../research/feed-parsing.md#pitfalls-and-regrets"
      },
      "options": [
        {
          "id": "own-scan",
          "name": "An `internal` prolog scan in common code, BOM- and encoding-aware, skipping the XML declaration, comments and processing instructions, that refuses every DOCTYPE before any parser runs; Rome's own ban the second lock",
          "verdict": "accepted",
          "why": "It does not depend on any parser's feature flags, and in common code it is testable as a pure function at Level 4a.",
          "source": "../architecture.md §6.10",
          "link": "../architecture.md#610-webtools-feeds"
        },
        {
          "id": "rome-flag",
          "name": "Rome's `disallow-doctype-decl` as the only lock",
          "verdict": "declined",
          "why": "A SAX provider that does not know the feature gets no ban and no error.",
          "source": "../research/feed-parsing.md Pitfalls 1",
          "link": "../research/feed-parsing.md#pitfalls-and-regrets"
        },
        {
          "id": "dtd-off",
          "name": "DTD processing turned off as the refusal",
          "verdict": "declined",
          "why": "Disabling DTD processing does not necessarily reject the DOCTYPE, and the JDK reader's behaviour is unmeasured.",
          "source": "../research/feed-parsing.md Pitfalls 2, Gaps G3",
          "link": "../research/feed-parsing.md#pitfalls-and-regrets"
        },
        {
          "id": "one-encoding-first",
          "name": "A scan that first picks one encoding by RFC 7303 or XML Appendix F order",
          "verdict": "declined",
          "why": "Rome's lenient `XmlReader` lets the XML declaration beat the mark and the header, so a scan that picks one encoding first is bypassed.",
          "source": "research/README.md § The DOCTYPE prolog scan; research/prolog-scan.md F1, F2",
          "link": "research/README.md#the-doctype-prolog-scan"
        }
      ],
      "asBuilt": {
        "state": "deviated",
        "summary": null,
        "statements": [
          {
            "text": "`PrologScan` was built in `commonMain` from the reference implementation: the leading byte-order marks skipped, three readings (ASCII-compatible, UTF-16BE, UTF-16LE) within 64 KiB each, and the worst outcome returned unless no reading reaches markup, when the byte reading decides between `Empty` and `NotMarkup`.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
            "excerpt": {
              "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
              "heading": "Stage 2 — prolog scan and `RomeFeedParser`",
              "paragraph": "`commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`, `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`). · `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`, `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`), `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` …",
              "hash": "d4f087b76854"
            }
          },
          {
            "text": "`toFailureOrNull` maps a DOCTYPE to `Refused(Doctype)`, an unreadable prolog to `Refused` with its encoding reason, a web page to `NotAFeed(Html)`, an ill-formed prolog to `Malformed`, and a body with no markup to `NotAFeed(Empty)` or `NotAFeed(NotXml)`; only `NoDoctype` lets a parser run, and no `Empty` or `NotMarkup` document parses in the differential.",
            "state": "as-planned",
            "ref": "final-implementation.md#differential-results-of-stage-2",
            "excerpt": {
              "ref": "final-implementation.md#differential-results-of-stage-2",
              "heading": "Differential results of stage 2",
              "paragraph": "`PrologScanRomeDifferentialDeterministicTest` runs the scan against the real pipeline in seven `XmlReader` shapes. The 135 catalogue rows and 20,000 generated documents never reach the DOCTYPE in any shape while the scan says `NoDoctype`; no `Empty` or `NotMarkup` document parses in any shape; and the 26 catalogue rows the real XML pipeline parses are read by `RomeFeedParser` when the scan says `NoDoctype` (N19, `<htmlx/>`, a well-formed root that is no feed format, as `NotAFeed(UnknownFormat)`).",
              "hash": "b8fa8d227772"
            }
          },
          {
            "text": "It holds the 135-row catalogue (the findings' 125, four with a changed expectation, and E01 to E10) and the encoding allowlist; the decoder-divergence rule applies to a reading that ended `NotMarkup` as well as `NoDoctype`, which keeps rows U23, U24, U27 and U31 as catalogued and keeps the pipeline from reaching a DOCTYPE over the 135 rows and 20,000 generated documents.",
            "state": "deviated",
            "ref": "final-implementation.md#d1-the-decoder-divergence-rule-also-applies-to-a-reading-that-ended-notmarkup",
            "excerpt": {
              "ref": "final-implementation.md#d1-the-decoder-divergence-rule-also-applies-to-a-reading-that-ended-notmarkup",
              "heading": "D1. The decoder-divergence rule also applies to a reading that ended `NotMarkup`",
              "paragraph": "Departs from: plan §7.11, change 4, which keeps `scanReading` as the reference has it, so that the decoder-divergence rule turns only a reading that ended `NoDoctype` into `Undecodable(MalformedSequence)`. · As built: `PrologScan.scanReading` applies the rule to a reading that ended `NoDoctype` or `NotMarkup`. · Why: change 2 of the same section makes a first significant unit that is not `<` return `NotMarkup` where the reference returned `NoDoctype`. The reference's safety for a junk unit after a closed comment rested on the divergence rule: in U23 and U24 the 16-bit decoder drops the dash …",
              "hash": "5272879d9ca6"
            }
          }
        ],
        "changes": [
          {
            "what": "The decoder-divergence rule turns a reading that ended `NotMarkup`, as well as one that ended `NoDoctype`, into `Undecodable(MalformedSequence)`, where plan §7.11 kept `scanReading` unchanged.",
            "why": "With `NotMarkup` outside the rule, rows U23 and U24 let the pipeline reach the DOCTYPE, which the differential test forbids, and U27 and U31 scored `NotMarkup` instead of the catalogued `MalformedSequence`; the wider rule restores all four and changes no other row.",
            "when": "stage 2",
            "ref": "final-implementation.md#d1-the-decoder-divergence-rule-also-applies-to-a-reading-that-ended-notmarkup",
            "link": "research/prolog-scan.md"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A pure function in common code: the module's own DOCTYPE lock, ahead of any parser.",
        "statements": [
          {
            "text": "The scan skips the leading run of byte-order marks and walks the prolog in three readings at once, ASCII-compatible, UTF-16BE and UTF-16LE, returning the worst outcome, within 64 KiB per reading.",
            "ref": "plan.md#r11-the-doctype-prolog-scan-internal-commonmain",
            "excerpt": {
              "ref": "plan.md#r11-the-doctype-prolog-scan-internal-commonmain",
              "heading": "R11. The DOCTYPE prolog scan (`internal`, `commonMain`)",
              "paragraph": "The algorithm, allowlist, decoder-divergence rule, 64 KiB bound and the 125-row catalogue of `research/prolog-scan.md`, extended with two outcomes that are returned only when no reading reaches markup: the body is then `Empty` when its byte reading holds nothing after the byte-order marks but white space, comments and processing instructions, and `NotMarkup` otherwise. Any reading that reaches markup wins, so a UTF-16 document without a mark still parses. Mapping: `Empty` → `NotAFeed(Empty)`, `NotMarkup` → `NotAFeed(NotXml)`, `HtmlDocument` → `NotAFeed(Html)`, `Doctype` → `Refused(Doctype)`, …",
              "hash": "8f792856f5ba"
            }
          },
          {
            "text": "A DOCTYPE is `Refused(Doctype)`, a prolog it cannot read the way the parser does `Refused` with its encoding reason, a web page `NotAFeed(Html)`, an ill-formed prolog `Malformed`, and a body that reaches no markup `NotAFeed(Empty)` or `NotAFeed(NotXml)`; no parser runs unless the scan finds no DOCTYPE.",
            "ref": "plan.md#r11-the-doctype-prolog-scan-internal-commonmain",
            "excerpt": {
              "ref": "plan.md#r11-the-doctype-prolog-scan-internal-commonmain",
              "heading": "R11. The DOCTYPE prolog scan (`internal`, `commonMain`)",
              "paragraph": "The algorithm, allowlist, decoder-divergence rule, 64 KiB bound and the 125-row catalogue of `research/prolog-scan.md`, extended with two outcomes that are returned only when no reading reaches markup: the body is then `Empty` when its byte reading holds nothing after the byte-order marks but white space, comments and processing instructions, and `NotMarkup` otherwise. Any reading that reaches markup wins, so a UTF-16 document without a mark still parses. Mapping: `Empty` → `NotAFeed(Empty)`, `NotMarkup` → `NotAFeed(NotXml)`, `HtmlDocument` → `NotAFeed(Html)`, `Doctype` → `Refused(Doctype)`, …",
              "hash": "8f792856f5ba"
            }
          },
          {
            "text": "It follows the 125-row catalogue of the findings, with an allowlist of encoding names and the decoder-divergence rule.",
            "ref": "plan.md#r11-the-doctype-prolog-scan-internal-commonmain",
            "excerpt": {
              "ref": "plan.md#r11-the-doctype-prolog-scan-internal-commonmain",
              "heading": "R11. The DOCTYPE prolog scan (`internal`, `commonMain`)",
              "paragraph": "The algorithm, allowlist, decoder-divergence rule, 64 KiB bound and the 125-row catalogue of `research/prolog-scan.md`, extended with two outcomes that are returned only when no reading reaches markup: the body is then `Empty` when its byte reading holds nothing after the byte-order marks but white space, comments and processing instructions, and `NotMarkup` otherwise. Any reading that reaches markup wins, so a UTF-16 document without a mark still parses. Mapping: `Empty` → `NotAFeed(Empty)`, `NotMarkup` → `NotAFeed(NotXml)`, `HtmlDocument` → `NotAFeed(Html)`, `Doctype` → `Refused(Doctype)`, …",
              "hash": "8f792856f5ba"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-2",
            "what": "Write the scan and its outcomes from the findings' algorithm and catalogue.",
            "files": [
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/PrologScan.kt"
            ],
            "check": "`:webtools-feeds:componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#711-cmprologscankt-and-cmprologscanfailurekt-new-internal-stage-2"
          }
        ],
        "tests": [
          "Level 4a (deterministic) `PrologScanVectorsDeterministicTest`, `PrologScanReadingsDeterministicTest`, and `PrologScanRomeDifferentialDeterministicTest` against the real pipeline"
        ],
        "risks": [
          "The decoder-divergence rule was measured on JDK 23.0.2 only; it errs towards refusing, and EUC-JP and GB18030 prologs with non-ASCII bytes and encodings off the allowlist are refused (§10)"
        ]
      }
    },
    {
      "id": "rome-parser",
      "group": "Code",
      "name": "`RomeFeedParser`, the supplied parser",
      "system": "webtools-feeds",
      "where": "jvmMain (new)",
      "gap": {
        "kind": "missing",
        "summary": "No parser is in the build. Rome 2.1.0 is the only candidate hardened by default that tells malformed XML from a document that is not a feed and detects the encoding in the RFC 7303 order, but it is dormant (last release 2023-03-12, an open date-parsing bug), builds a DOM, and its Atom date and `xml:base` mappings are unverified.",
        "evidence": [
          "../research/feed-parsing.md F1 — Rome's coverage, hardening, failure typing, encoding detection and activity",
          "../research/feed-parsing.md Gaps G6 — Rome mappings not verified",
          "../architecture.md §12 — Rome is dormant; the port, the scan and the fixed-heap suite bound the exposure",
          "research/rome-adapter.md F1, F2 — `SyndFeedInput` keeps its `WireFeedInput` private, so no limit can be set through it, and Rome's own builder uses JDOM's process-wide SAX factory, on which a JAXP limit persists from JDK 22 and concurrent builds race"
        ],
        "source": "../research/feed-parsing.md F1; ../architecture.md §6.10, §12",
        "link": "../architecture.md#610-webtools-feeds"
      },
      "options": [
        {
          "id": "rome-default",
          "name": "A final `RomeFeedParser(limits)`, usable with no opt-in: the prolog scan first, Rome's `XmlReader` with the healer on, `SyndFeedInput` with DOCTYPEs disallowed and not exposed, Rome's exceptions mapped to `Malformed`, `NotAFeed` and `LimitExceeded(depth)`, the beans converted under `FeedLimits`; rssreader 3.12.0 the documented swap behind the same port",
          "verdict": "accepted",
          "why": "Rome stays inside `jvmMain` and a streaming parser can replace it behind the port if its amplification is too high. Stable Core.",
          "source": "../architecture.md §6.10, §9, §12",
          "link": "../architecture.md#610-webtools-feeds"
        },
        {
          "id": "rssreader-default",
          "name": "rssreader 3.12.0 or prof18's parser as the default",
          "verdict": "declined",
          "why": "rssreader is silent on malformed XML and ignores Atom `rel`; prof18's parser needs Kotlin 2.4.10 and an unhardened SAX parser.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "in-house-stax",
          "name": "An in-house StAX parser",
          "verdict": "declined",
          "why": "The tolerance for real-world malformed feeds is real work Rome already carries; it is the next step only if both Rome and the swap prove unacceptable.",
          "source": "../research/feed-parsing.md Recommendation for this repo",
          "link": "../research/feed-parsing.md#the-parser"
        },
        {
          "id": "syndfeedinput",
          "name": "`SyndFeedInput` with DOCTYPEs disallowed and a plain `XmlReader`, as #55's architecture §6.10 first wrote",
          "verdict": "declined",
          "why": "No limit can be set through it without JVM-wide system properties, which reach the host application's own XML parsing.",
          "source": "plan.md §6, 3; research/README.md § Options that do not fit",
          "link": "plan.md#6-where-this-plan-refines-the-parent-architecture"
        },
        {
          "id": "rome-builder",
          "name": "Rome's own builder with the limits added",
          "verdict": "declined",
          "why": "The limits leak to every JDOM parse in the JVM from JDK 22, and concurrent builds race.",
          "source": "research/README.md § Options that do not fit",
          "link": "research/README.md#options-that-do-not-fit"
        },
        {
          "id": "jdom-document",
          "name": "Building the JDOM document separately and handing it to `WireFeedInput.build(Document)`",
          "verdict": "declined",
          "why": "Rome's repair and encoding detection are lost, so bare ampersands, `&nbsp;` and ISO-8859-1 feeds fail.",
          "source": "research/README.md § Options that do not fit",
          "link": "research/README.md#options-that-do-not-fit"
        },
        {
          "id": "structural-prescan",
          "name": "A byte-level structural pre-scan for the limits",
          "verdict": "declined",
          "why": "Cheap, but it can disagree with the parser about a malformed document; a counting handler counts exactly what the parser builds.",
          "source": "research/README.md § Options that do not fit",
          "link": "research/README.md#options-that-do-not-fit"
        }
      ],
      "asBuilt": {
        "state": "deviated",
        "summary": null,
        "statements": [
          {
            "text": "`RomeFeedParser` was built final, usable without the opt-in, with `RomeFeedParser(limits)` and `RomeFeedParser()`; it was the only addition of stage 2 to the regenerated ABI dump.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--the-js-compile-and-the-dump-each-code-stage",
            "excerpt": {
              "ref": "final-implementation.md#p2--the-js-compile-and-the-dump-each-code-stage",
              "heading": "P2 — the `js` compile and the dump (each code stage)",
              "paragraph": "Stage 1: `:webtools-feeds-commoncheck:build` passes with every `commonMain` file of stage 1 compiled for `js` (`String.encodeToByteArray`, `Long.floorDiv`, `StringBuilder.deleteRange` and `CharSequence.lastIndexOf` included). `:webtools-feeds:apiCheck` and `:webtools-feeds:verifyJavaFloor` pass after the dump. · Stage 2: `:webtools-feeds-commoncheck:build` passes with `PrologScan.kt`, `PrologScanFailure.kt` and `ResultExtensions.kt` added (`buildList`, `Enum.entries`, `data object` and `indexOf(…, ignoreCase = true)` compile for `js`); `apiDump` then `apiCheck` pass, the dump diff being …",
              "hash": "c3af15ebf15c"
            }
          },
          {
            "text": "It runs the prolog scan, then Rome through `HardenedWireFeedInput`, a private `WireFeedInput` subclass that builds its own SAX parser per parse from `SAXParserFactory.newDefaultInstance()`, with the DOCTYPE and entity features set directly, entity expansion off, and the depth and attribute limits as JAXP properties under their URL names.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
            "excerpt": {
              "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
              "heading": "Stage 2 — prolog scan and `RomeFeedParser`",
              "paragraph": "`commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`, `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`). · `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`, `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`), `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` …",
              "hash": "d4f087b76854"
            }
          },
          {
            "text": "`CountingSaxHandler` bounds the elements and the distinct namespace URIs, and the three-step `XmlReader` recipe keeps Rome's ampersand and entity repair and its encoding detection.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
            "excerpt": {
              "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
              "heading": "Stage 2 — prolog scan and `RomeFeedParser`",
              "paragraph": "`commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`, `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`). · `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`, `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`), `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` …",
              "hash": "d4f087b76854"
            }
          },
          {
            "text": "`toFeedFailure` maps by exception type and JAXP code to `Malformed`, `NotAFeed(UnknownFormat)`, `LimitExceeded` or `Refused(Doctype)`, keeping no cause or message, inside `inRomeRegion`, the module's one broad catch. Every parse creates its own reader, input, builder and SAX parser and sets no process-wide configuration of Rome, JDOM or the JDK; the one shared state a parse grows is JDOM's process-wide namespace cache, by at most `maxNamespaceUris` new URIs per document, and the parser logs nothing of its own while Rome logs rarely under its own loggers, as the class KDoc states.",
            "state": "deviated",
            "ref": "final-implementation.md#d4-romefeedparsers-kdoc-names-the-shared-state-a-parse-grows",
            "excerpt": {
              "ref": "final-implementation.md#d4-romefeedparsers-kdoc-names-the-shared-state-a-parse-grows",
              "heading": "D4. `RomeFeedParser`'s KDoc names the shared state a parse grows",
              "paragraph": "Departs from: plan R12 (\"Everything is created per `parse` call; no static state is read or written. It logs nothing.\") and the class KDoc of §7.13 (\"reads and writes no static state of Rome, JDOM or the JDK. It logs nothing.\"). · As built: the code is §7.13's. The class KDoc says that every parse creates its own reader, input, builder and SAX parser and sets no process-wide configuration of Rome, JDOM or the JDK (no shared SAX factory, no system property, no default changed); that the one shared state a parse grows is JDOM's process-wide namespace cache, by at most `maxNamespaceUris` new …",
              "hash": "3a775fec21d4"
            }
          },
          {
            "text": "No Rome, JDOM, SAX or slf4j type is in the ABI dump, and the conformance contract is an abstract class another parser runs through its own factory, as `RomeFeedParserConformanceTest` does over the seven synthetic fixtures.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
            "excerpt": {
              "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
              "heading": "Stage 2 — prolog scan and `RomeFeedParser`",
              "paragraph": "`commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`, `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`). · `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`, `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`), `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` …",
              "hash": "d4f087b76854"
            }
          }
        ],
        "changes": [
          {
            "what": "The class KDoc names the one shared state a parse grows, JDOM's process-wide namespace cache, bounded by `maxNamespaceUris`, and says the parser logs nothing of its own while Rome logs rarely under `com.rometools.*` loggers, where plan R12 and §7.13 said no static state is read or written and it logs nothing. The code is §7.13's.",
            "why": "`org.jdom2.Namespace` keeps every namespace URI a parse declares in a static cache that never evicts, so a parse does write static state, bounded by the namespace-URI limit; and Rome's own classes hold slf4j loggers, none of them on this parser's path. The planned sentences were false for the first and too broad for the second.",
            "when": "Document",
            "ref": "final-implementation.md#d4-romefeedparsers-kdoc-names-the-shared-state-a-parse-grows",
            "link": "research/parse-memory.md#f6-jdom2s-namespace-cache-is-process-wide-strong-and-never-evicted-code-reading--measured"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The supplied default, inside `jvmMain` only, behind the port.",
        "statements": [
          {
            "text": "`RomeFeedParser` is final, Stable Core and usable without the opt-in, with `RomeFeedParser(limits)` and `RomeFeedParser()`.",
            "ref": "plan.md#r12-romefeedparser-jvm",
            "excerpt": {
              "ref": "plan.md#r12-romefeedparser-jvm",
              "heading": "R12. `RomeFeedParser` (JVM)",
              "paragraph": "Final, usable without the opt-in, public constructors `RomeFeedParser(limits: FeedLimits)` and `RomeFeedParser()`. It runs the prolog scan first, then Rome through a private `WireFeedInput` subclass with a SAX parser of its own per parse, the three-step `XmlReader` recipe, a counting SAX handler, the exception table of `research/rome-adapter.md`, and the conversion of R13. Everything is created per `parse` call; no static state is read or written. It logs nothing. Documents with XML-illegal control characters are `Malformed`.",
              "hash": "b6284e7198e5"
            }
          },
          {
            "text": "It runs the prolog scan first, then Rome through a private `WireFeedInput` subclass with a SAX parser of its own per parse: Rome's DOCTYPE and entity features set directly, entity expansion off, and the depth and attribute limits as JAXP properties under their URL names.",
            "ref": "plan.md#r12-romefeedparser-jvm",
            "excerpt": {
              "ref": "plan.md#r12-romefeedparser-jvm",
              "heading": "R12. `RomeFeedParser` (JVM)",
              "paragraph": "Final, usable without the opt-in, public constructors `RomeFeedParser(limits: FeedLimits)` and `RomeFeedParser()`. It runs the prolog scan first, then Rome through a private `WireFeedInput` subclass with a SAX parser of its own per parse, the three-step `XmlReader` recipe, a counting SAX handler, the exception table of `research/rome-adapter.md`, and the conversion of R13. Everything is created per `parse` call; no static state is read or written. It logs nothing. Documents with XML-illegal control characters are `Malformed`.",
              "hash": "b6284e7198e5"
            }
          },
          {
            "text": "A counting SAX handler bounds the elements and the distinct namespace URIs, and a three-step `XmlReader` recipe keeps Rome's ampersand and entity repair and its encoding detection.",
            "ref": "plan.md#57-hardening-in-four-layers",
            "excerpt": {
              "ref": "plan.md#57-hardening-in-four-layers",
              "heading": "5.7 Hardening in four layers",
              "paragraph": "The transport's decoded-byte cap (2 MiB under `Untrusted`) bounds the document. · The prolog scan refuses every DOCTYPE, and a prolog that cannot be read the way the parser reads it, before any parser runs. · The parser's own SAX settings: Rome's four DOCTYPE and entity features set directly (a provider that does not know one fails the build instead of skipping it), entity expansion off, an empty entity resolver, and the JDK limits (`maxDepth`, `maxAttributesPerElement`) as properties on a private `SAXParserFactory.newDefaultInstance()` per build; a counting handler for `maxElements` and …",
              "hash": "efa3cd3a307f"
            }
          },
          {
            "text": "Failures are mapped by exception type and JAXP code to `Malformed`, `NotAFeed(UnknownFormat)`, `LimitExceeded` or `Refused(Doctype)`, keeping no cause or message; everything is created per call, nothing static is touched, and it logs nothing.",
            "ref": "plan.md#58-where-the-failure-paths-are",
            "excerpt": {
              "ref": "plan.md#58-where-the-failure-paths-are",
              "heading": "5.8 Where the failure paths are",
              "paragraph": "§7.23 tabulates them. The rule: a failure of a document or a response is a value; the only broad `try`/`catch` of the module is `inRomeRegion`, around the three Rome calls, where Rome's own runtime exceptions on hostile input are input-triggered and carry document text; an exception from the module's own conversion is a bug and leaves `parse`, to become the fetcher's `DecodeFailure`.",
              "hash": "d93e451a55ea"
            }
          },
          {
            "text": "Rome is dormant; no Rome type is public, so another parser can replace it behind the port and run the same contracts.",
            "ref": "plan.md#10-risks-and-edge-cases",
            "excerpt": {
              "ref": "plan.md#10-risks-and-edge-cases",
              "heading": "10. Risks and edge cases",
              "paragraph": "Rome is dormant. The last release is 2.1.0 (2023); a defect or a CVE in Rome or JDOM has no upstream fix on a schedule. The mitigation is structural: no Rome type is public, the parser sits behind `FeedParser`, the DOCTYPE lock is the module's own, and the contract classes can run any replacement (rssreader 3.12.0 is the documented candidate: silent on malformed XML, ignores Atom `rel`, no Atom 0.3). · JDK 11 cannot be exercised on the build host. The JDK 11 parser does not recognise the `jdk.xml.*` spellings of the limit properties (measured on 11.0.32.1): the URL spellings are used, but the …",
              "hash": "1ed833e125ef"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-2",
            "what": "Write the Rome internals, the parser's own SAX settings, the `XmlReader` recipe, the counting handler, the failure mapping and the conversion, then `RomeFeedParser` and the seven fixtures.",
            "files": [
              "webtools-feeds/src/jvmMain/kotlin/com/spartanlabs/webtools/feeds/RomeWireInput.kt",
              "webtools-feeds/src/jvmMain/kotlin/com/spartanlabs/webtools/feeds/RomeConversion.kt",
              "webtools-feeds/src/jvmMain/kotlin/com/spartanlabs/webtools/feeds/RomeFeedParser.kt"
            ],
            "check": "`:webtools-feeds:componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#712-the-rome-internals-new-jvmmain-internal-stage-2"
          }
        ],
        "tests": [
          "Level 2 (component) `RomeFailureMappingComponentTest`, `CountingSaxHandlerComponentTest`, `RomeXmlReaderComponentTest`, `RomeConversionComponentTest`, `RomeFeedParserComponentTest`; Level 4a (deterministic) `RomeFeedParserConformanceTest`; Level 4c (non-functional) `RomeFeedParserHardeningTest`, `RomeFeedParserIsolationNonFunctionalTest`, `RomeFeedParserJdk11NonFunctionalTest`"
        ],
        "risks": [
          "Rome is dormant: a defect or a CVE in Rome or JDOM has no upstream fix on a schedule (§10)",
          "The JDK 11 parser knows the limit properties only under their URL names, which the build JDK cannot show; the optional JDK 11 test is the guard (§10)",
          "JDOM's namespace cache still grows, bounded to 64 entries per hostile document (§10)"
        ]
      }
    },
    {
      "id": "untrusted-factory",
      "group": "Code",
      "name": "`FeedFetcher.untrusted`, the owning factory",
      "system": "webtools-feeds",
      "where": "jvmMain (new)",
      "gap": {
        "kind": "missing",
        "summary": "Feed URLs are chosen by server administrators, and a fetcher cannot verify that a `UrlFetcher` it is given enforces `Untrusted`: a `Trusted` one would make a feed URL a server-side request forgery primitive. There is no feeds path that builds the safe stack itself.",
        "evidence": [
          "webtools-http UrlFetcherOwning.kt:25 — `UrlFetcher(userAgent, policy) { }`, which builds and owns its transport (baseline)",
          "../consequences.md §8 — unit 10's owning factory builds an `Untrusted` fetcher with `OutboundUrlPolicy.untrusted { }`",
          "../research/feed-parsing.md Considerations 3 — how the fetcher is given an `Untrusted` transport"
        ],
        "source": "../research/feed-parsing.md Considerations 3; ../consequences.md §8",
        "link": "../research/feed-parsing.md#3-how-the-fetcher-is-given-an-untrusted-transport"
      },
      "options": [
        {
          "id": "both",
          "name": "Both: the common factory over a `UrlFetcher`, and `FeedFetcher.untrusted(userAgent) { }`, which builds and owns an `Untrusted` `UrlFetcher` and is the documented path; `close()` closes only what the fetcher built",
          "verdict": "accepted",
          "why": "Fakes and a consumer's own client reach the common factory; the owning factory is the safe default.",
          "source": "../architecture.md §6.10; ../research/feed-parsing.md Considerations 3",
          "link": "../architecture.md#610-webtools-feeds"
        },
        {
          "id": "factory-only",
          "name": "The owning factory only",
          "verdict": "declined",
          "why": "Fakes would need a constructor the fixtures cannot reach, and a consumer's adapter could not use its own client.",
          "source": "../research/feed-parsing.md Considerations 3",
          "link": "../research/feed-parsing.md#3-how-the-fetcher-is-given-an-untrusted-transport"
        },
        {
          "id": "constructor-only",
          "name": "The common factory only",
          "verdict": "declined",
          "why": "A consumer who passes a `Trusted` stack turns a feed URL into a server-side request forgery primitive.",
          "source": "../research/feed-parsing.md Considerations 3",
          "link": "../research/feed-parsing.md#3-how-the-fetcher-is-given-an-untrusted-transport"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": null,
        "statements": [
          {
            "text": "`FeedFetcher.untrusted(userAgent) { }` builds and owns `UrlFetcher(userAgent, OutboundUrlPolicy.untrusted())`, which its `close()` closes; through it a loopback URL is refused with `RefusedByPolicy` and no request reaches the server.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          },
          {
            "text": "`FeedFetcher.untrusted(userAgent, policy) { }` takes an `Untrusted` policy of the caller's own, and refuses a host on a denied suffix before any network.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          },
          {
            "text": "Both check the configuration before anything is built, so a configuration out of range or a user agent the transport refuses throws `IllegalArgumentException` with nothing left open; a shared budget goes through a caller-built `UrlFetcher` and the common factory.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The documented path for administrator-supplied URLs, on the JVM.",
        "statements": [
          {
            "text": "`FeedFetcher.untrusted(userAgent) { }` builds and owns `UrlFetcher(userAgent, OutboundUrlPolicy.untrusted())`, so a feed URL meets the `Untrusted` policy before anything is spent, a document is capped at 2 MiB and a throttle is `RateLimited`.",
            "ref": "plan.md#717-jmfeedfetcherowningkt-new-stage-3",
            "excerpt": {
              "ref": "plan.md#717-jmfeedfetcherowningkt-new-stage-3",
              "heading": "7.17 `<jm>FeedFetcherOwning.kt` (new, stage 3)",
              "paragraph": "Failure: both throw `IllegalArgumentException` before anything stays open (the configuration is checked first; `UrlFetcher(userAgent, policy)` throws for a bad agent before it holds a socket).",
              "hash": "40929ae575c5"
            }
          },
          {
            "text": "`FeedFetcher.untrusted(userAgent, policy) { }` takes an `Untrusted` policy of the caller's own, for added denied ranges or ports.",
            "ref": "plan.md#717-jmfeedfetcherowningkt-new-stage-3",
            "excerpt": {
              "ref": "plan.md#717-jmfeedfetcherowningkt-new-stage-3",
              "heading": "7.17 `<jm>FeedFetcherOwning.kt` (new, stage 3)",
              "paragraph": "Failure: both throw `IllegalArgumentException` before anything stays open (the configuration is checked first; `UrlFetcher(userAgent, policy)` throws for a bad agent before it holds a socket).",
              "hash": "40929ae575c5"
            }
          },
          {
            "text": "Both check the configuration first and throw `IllegalArgumentException` before anything stays open; a shared budget goes through a caller-built `UrlFetcher` and the common factory.",
            "ref": "plan.md#r10-feedfetcherconfig-feedfetcher-and-the-factories",
            "excerpt": {
              "ref": "plan.md#r10-feedfetcherconfig-feedfetcher-and-the-factories",
              "heading": "R10. `FeedFetcherConfig`, `FeedFetcher` and the factories",
              "paragraph": "`FeedFetcherConfig` (`@WebToolsDsl`): `parser` (default: the platform's default parser, `RomeFeedParser()` on the JVM), `parseDispatcher` (`Dispatchers.Default`), `maxConcurrentParses` (2, at least 1) and `accept` (default `application/atom+xml, application/rss+xml, application/rdf+xml;q=0.9, application/xml;q=0.8, text/xml;q=0.8, */*;q=0.1`; refused at construction when blank or not a sendable header value). · `FeedFetcher : AutoCloseable`, final, with a `public companion object`: `suspend fun fetch(url, configure = {})` and `suspend fun fetch(url, validators, configure = {})`, each …",
              "hash": "90cb08c2c790"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-3",
            "what": "Write the two owning factories as companion extensions on the JVM.",
            "files": [
              "webtools-feeds/src/jvmMain/kotlin/com/spartanlabs/webtools/feeds/FeedFetcherOwning.kt"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `integrationTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#717-jmfeedfetcherowningkt-new-stage-3"
          }
        ],
        "tests": [
          "Level 2 (component) `FeedFetcherOwningFactoryComponentTest`; Level 3 (integration) `FeedFetcherOwningFactoryIntegrationTest`, a loopback URL refused with no request reaching the server; Level 1 (gating) `FeedFetcherConsumerGatingTest`"
        ]
      }
    },
    {
      "id": "logging",
      "group": "Code",
      "name": "The module's logging seam and helpers",
      "system": "webtools-feeds",
      "where": "commonMain, jvmMain (new)",
      "gap": {
        "kind": "missing",
        "summary": "`webtools-http`'s logging seam and `Result` helper are `internal`, which stops at the module, so the feeds module cannot use them; every `expect` in a common tree needs a `js` actual in its check module.",
        "evidence": [
          "webtools-http Log.kt:17 — `internal expect fun logger(name)` (baseline)",
          "../architecture.md §6.9 — `webtools-feeds` carries its own copy of the logging seam",
          "../consequences.md §2 — a fourth copy of `flatMap` only if the feeds module chains `Result`s"
        ],
        "source": "../architecture.md §6.9; ../consequences.md §2",
        "link": "../architecture.md#69-common-code-mechanism-per-type"
      },
      "options": [
        {
          "id": "own-copy",
          "name": "Its own `internal expect fun logger` with the slf4j actual on the JVM and a no-op in the check module's `jsMain`, and its own `flatMap` only if it chains `Result`s",
          "verdict": "accepted",
          "why": "The smallest thing that logs by default from common code; a missing actual is a compile error the check module catches.",
          "source": "../architecture.md §6.9, §6.10",
          "link": "../architecture.md#69-common-code-mechanism-per-type"
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
          "id": "redacted",
          "name": "`Url.redacted()` for the module's own lines, the stack's habit",
          "verdict": "declined",
          "why": "It shows the path, and a feed URL is administrator-supplied, its path able to carry a secret.",
          "source": "plan.md §6, 6",
          "link": "plan.md#6-where-this-plan-refines-the-parent-architecture"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": null,
        "statements": [
          {
            "text": "The module holds its copy of the `internal` logging seam with its slf4j actual, its non-fatal catch and `flatMap`; the check module holds the no-op `js` actual.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
            "excerpt": {
              "ref": "final-implementation.md#stage-1--skeleton-and-vocabulary",
              "heading": "Stage 1 — skeleton and vocabulary",
              "paragraph": "`settings.gradle.kts` includes `:webtools-feeds` and `:webtools-feeds-commoncheck`. · `webtools-feeds/build.gradle.kts` and `webtools-feeds-commoncheck/build.gradle.kts` as the plan gives them; the js actual of `logger` in `webtools-feeds-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/feeds/JsActuals.kt`; a byte-identical `webtools-feeds/src/jvmTest/resources/logback-test.xml`. · `commonMain` (`com.spartanlabs.webtools.feeds`): `Log.kt`, `NonFatal.kt`, `UrlOrigin.kt`, `FeedMessages.kt`; `FeedFailure.kt`, `NotAFeedReason.kt`, `FeedRefusal.kt`, `FeedLimit.kt`; `FeedLimits.kt`; …",
              "hash": "c1404fdd0aaf"
            }
          },
          {
            "text": "`FeedFetcher` logs `INFO` once on creation and once on close, one `DEBUG` line per fetch with its outcome and figures, and a `WARN` for a refused document; `RomeFeedParser` logs nothing.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          },
          {
            "text": "Every URL a line of the module prints is its origin, scheme, host and port, beside numbers and type names only: never a path, a query, a header value, a body, document content or a message from Rome.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The module's own copy of the stack's logging seam, as `internal` stops at the module.",
        "statements": [
          {
            "text": "The module copies `webtools-http`'s `internal` logging seam with its slf4j actual, its non-fatal catch and `flatMap`; the check module holds the `js` actual.",
            "ref": "plan.md#74-the-logging-seam-and-shared-internals-new-stage-1-unless-stated",
            "excerpt": {
              "ref": "plan.md#74-the-logging-seam-and-shared-internals-new-stage-1-unless-stated",
              "heading": "7.4 The logging seam and shared internals (new, stage 1 unless stated)",
              "paragraph": "`<cm>Log.kt`: a copy of `webtools-http`'s `Log.kt:7-17` in the package `com.spartanlabs.webtools.feeds` — `internal interface Log { fun debug(message: String, vararg args: Any?); fun info(…); fun warn(…) }` and `internal expect fun logger(name: String): Log`. · `<jm>Log.jvm.kt`: a copy of `Log.jvm.kt` (`internal actual fun logger(name: String): Log = Slf4jLog(LoggerFactory.getLogger(name))`, the private `Slf4jLog` delegating with `*args`). · `<cm>NonFatal.kt`: a copy of `catchNonFatal` (`NonFatal.kt:15`): `internal inline fun <T> catchNonFatal(block: () -> T): Result<T>`; an `Exception` other …",
              "hash": "9d0a1dd95504"
            }
          },
          {
            "text": "`FeedFetcher` logs `INFO` once on creation and once on close, one `DEBUG` line per fetch and a `WARN` for a refused document; `RomeFeedParser` logs nothing.",
            "ref": "plan.md#r14-logging",
            "excerpt": {
              "ref": "plan.md#r14-logging",
              "heading": "R14. Logging",
              "paragraph": "The module copies the logging seam and the non-fatal catch helper (`internal` stops at the module). `FeedFetcher` logs under the logger `com.spartanlabs.webtools.feeds.FeedFetcher`: `INFO` once on creation (numbers and kinds only) and once on close; one `DEBUG` line per fetch; a `WARN` for a `Refused` document. `RomeFeedParser` logs nothing. · Every URL a line of this module prints is scheme, host and port only. Never a path, a query, a header value, a body, document content or an exception message from Rome.",
              "hash": "74937532f45a"
            }
          },
          {
            "text": "Every URL a line of the module prints is scheme, host and port only: never a path, a query, a header value, a body, document content or a message from Rome.",
            "ref": "plan.md#r14-logging",
            "excerpt": {
              "ref": "plan.md#r14-logging",
              "heading": "R14. Logging",
              "paragraph": "The module copies the logging seam and the non-fatal catch helper (`internal` stops at the module). `FeedFetcher` logs under the logger `com.spartanlabs.webtools.feeds.FeedFetcher`: `INFO` once on creation (numbers and kinds only) and once on close; one `DEBUG` line per fetch; a `WARN` for a `Refused` document. `RomeFeedParser` logs nothing. · Every URL a line of this module prints is scheme, host and port only. Never a path, a query, a header value, a body, document content or an exception message from Rome.",
              "hash": "74937532f45a"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Copy the logging seam with its slf4j actual and the non-fatal catch, and add the origin helper.",
            "files": [
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/Log.kt",
              "webtools-feeds/src/jvmMain/kotlin/com/spartanlabs/webtools/feeds/Log.jvm.kt",
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/UrlOrigin.kt"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P1, P2)",
            "link": "plan.md#74-the-logging-seam-and-shared-internals-new-stage-1-unless-stated"
          },
          {
            "id": "stage-3",
            "what": "Write the fetcher's log lines.",
            "files": [
              "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/FeedFetcher.kt"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `integrationTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#722-logging-events-this-change-adds"
          }
        ],
        "tests": [
          "Level 2 (component) `FeedFetcherLoggingComponentTest`; Level 4a (deterministic) `UrlOriginDeterministicTest`; Level 4c (non-functional) `FeedFetcherSecrecyNonFunctionalTest`"
        ],
        "risks": [
          "The `UrlFetcher` and the transport under the fetcher still print a URL's path until #93 is resolved (§6)"
        ]
      }
    },
    {
      "id": "gating-tests",
      "group": "Tests",
      "name": "Level 1 gating tests: the feeds API with no opt-in",
      "system": "webtools-feeds",
      "where": "commonTest, jvmTest (new, `testing.gating`)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing shows that a consumer uses the feeds API, the fetcher and the supplied parser without the opt-in that gates implementing `FeedParser` and subclassing `FeedFailure`, or that a `FeedFetch` is matched without an `else`.",
        "evidence": [
          "plan.md R2 — `FeedParser` and `FeedFailure` gated, every other type and `RomeFeedParser` usable with no opt-in",
          "plan.md §9 — two gating classes the plan adds, which #55's decomposition does not name"
        ],
        "source": "plan.md R2, §9",
        "link": "plan.md#level-1--gating-gatingtest"
      },
      "options": [
        {
          "id": "no-opt-in-sources",
          "name": "Two Level 1 classes compiled with no opt-in: the common API as a consumer uses it, and the fetcher and the supplied parser on production defaults",
          "verdict": "accepted",
          "why": "A consumer's own code is what the gate must leave free, and compiling it with no opt-in proves that.",
          "source": "plan.md §9",
          "link": "plan.md#level-1--gating-gatingtest"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": null,
        "statements": [
          {
            "text": "Two Level 1 classes, 9 tests, compiled with no opt-in and passing: `FeedsConsumerGatingTest` in `commonTest` (4) and `FeedFetcherConsumerGatingTest` in `jvmTest` (5) for the fetcher, its owning factories and `RomeFeedParser`.",
            "state": "as-planned",
            "ref": "final-implementation.md#tests",
            "excerpt": {
              "ref": "final-implementation.md#tests",
              "heading": "Tests",
              "paragraph": "These are plan §9.1's totals, class for class: the two abstract contract classes are not counted, and their 39 and 39 tests run through `RomeFeedParserConformanceTest` and `RomeFeedParserHardeningTest`. In the final run 327 tests pass and 1 is skipped, `RomeFeedParserJdk11NonFunctionalTest`, because no `WEBTOOLS_JDK11_HOME` names a JDK 11 on the build host (§4, P4).",
              "hash": "3c017cb43960"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Tests of the change's own surface.",
        "statements": [
          {
            "text": "Two Level 1 classes, 9 tests, compiled with no opt-in: `FeedsConsumerGatingTest` in `commonTest` for the limits, the model, the validators, `FeedFetch` and the failures, and `FeedFetcherConsumerGatingTest` in `jvmTest` for the fetcher, its owning factories and `RomeFeedParser`.",
            "ref": "plan.md#level-1--gating-gatingtest",
            "excerpt": {
              "ref": "plan.md#level-1--gating-gatingtest",
              "heading": "Level 1 — gating (`gatingTest`)",
              "paragraph": "",
              "hash": "2e0d4537d4db"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Write the common gating class.",
            "files": [
              "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/feeds/FeedsConsumerGatingTest.kt"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P1, P2)",
            "link": "plan.md#level-1--gating-gatingtest"
          },
          {
            "id": "stage-3",
            "what": "Write the JVM gating class.",
            "files": [
              "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/feeds/FeedFetcherConsumerGatingTest.kt"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `integrationTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#level-1--gating-gatingtest"
          }
        ],
        "tests": [
          "Level 1 (gating) `FeedsConsumerGatingTest` (4 tests) and `FeedFetcherConsumerGatingTest` (5 tests)"
        ]
      }
    },
    {
      "id": "component-tests",
      "group": "Tests",
      "name": "Level 2 component tests on the fakes",
      "system": "webtools-feeds",
      "where": "jvmTest (new, `testing.component`)",
      "gap": {
        "kind": "missing",
        "summary": "No test drives a feed fetch. The fakes can script a response with a redirect chain and match a request header, and the fixtures variant resolves from a multiplatform module's `jvmTest`; under an `Untrusted` policy a fake-transport test must use a public-looking host.",
        "evidence": [
          "webtools-http fixtures Step.kt:80 — `Step.respond { … }`, a response built from the request, its `redirectChain` included (baseline)",
          "webtools-http fixtures RequestMatch.kt:81 — `header(name, value)`",
          "../consequences.md §8 — `jvmTestImplementation(testFixtures(project(\":webtools-http\")))` resolves, measured",
          "../68-call-facades/consequences.md §4 — the pre-check applies the policy to a fake transport's URLs too"
        ],
        "source": "../architecture.md §11; ../consequences.md §8",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "on-fakes",
          "name": "Level 2 component tests on `FakeHttpTransport` through `UrlFetcher(transport, policy)`: conditional headers sent verbatim, a `304` giving `NotModified` and keeping or replacing validators, a `200` capturing them, a response with none returning `None`",
          "verdict": "accepted",
          "why": "The conditional-GET contract is a set of request and response facts the fakes pin without a socket.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "text": "25 Level 2 classes, 157 tests, passing, the plan's totals: the value types, the configuration and the scan's failure mapping in `commonTest`; the Rome internals, the DSL marker and the fetcher's requests, statuses, moves, pass-through, parse, lifecycle, owning factory, logging and defaults over `FakeHttpTransport` in `jvmTest`; the test names are in plain words, two of them saying less than the plan's while running its cases.",
            "state": "amended",
            "ref": "final-implementation.md#tests",
            "excerpt": {
              "ref": "final-implementation.md#tests",
              "heading": "Tests",
              "paragraph": "These are plan §9.1's totals, class for class: the two abstract contract classes are not counted, and their 39 and 39 tests run through `RomeFeedParserConformanceTest` and `RomeFeedParserHardeningTest`. In the final run 327 tests pass and 1 is skipped, `RomeFeedParserJdk11NonFunctionalTest`, because no `WEBTOOLS_JDK11_HOME` names a JDK 11 on the build host (§4, P4).",
              "hash": "3c017cb43960"
            }
          },
          {
            "text": "The fake-transport tests use a public-looking host, as the `Untrusted` pre-check refuses loopback, and a test whose parser blocks runs on a real dispatcher under a time bound; its negatives are proven by signals from the gate parser and a latch on the fake transport, with no fixed wait.",
            "state": "as-planned",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          }
        ],
        "changes": [
          {
            "what": "Test names in plain words, with none of the code punctuation of plan §9's names; `Doctype and each Undecodable reason map to Refused with their reasons` and `a 200 with unusable validators gives null` say less than the plan's names, and run its cases.",
            "why": "A JVM method name cannot hold `.`, `:`, `;`, `/`, `<` or `>`, so the names are written as plain words throughout, the other punctuation included; and a name that points at the research means nothing to a reader of the source.",
            "when": "stages 2 and 3",
            "ref": "final-implementation.md#d7-test-names-in-plain-words"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Tests of the change's own surface, with hand-written fakes and no mocking library.",
        "statements": [
          {
            "text": "25 Level 2 classes, 157 tests: the value types, the configuration and the scan's failure mapping in `commonTest`; the Rome internals and the DSL marker, and the fetcher's requests, statuses, moves, pass-through, parse, lifecycle, owning factory and logging over `FakeHttpTransport` and the virtual clock in `jvmTest`.",
            "ref": "plan.md#level-2--component-componenttest",
            "excerpt": {
              "ref": "plan.md#level-2--component-componenttest",
              "heading": "Level 2 — component (`componentTest`)",
              "paragraph": "",
              "hash": "c25fea5dfeaa"
            }
          },
          {
            "text": "A fake-transport test uses a public-looking host, as the `Untrusted` pre-check refuses loopback, and a test whose parser blocks runs on a real dispatcher under a time bound.",
            "ref": "plan.md#90-conventions-that-bind-every-class-below",
            "excerpt": {
              "ref": "plan.md#90-conventions-that-bind-every-class-below",
              "heading": "9.0 Conventions that bind every class below",
              "paragraph": "Packages `com.spartanlabs.testing.<level>.webtools.feeds`; paths `webtools-feeds/src/<set>/kotlin/com/spartanlabs/testing/<level>/webtools/feeds/<Class>.kt`, where `<set>` is `commonTest` or `jvmTest`. Level folders: `gating`, `component`, `integration`, `deterministic`, `nonfunctional`; no Level 4b class (no end-to-end flow … exists for a library) and no Level 5 class (manual, §9.3). One test class per file; each class's header comment names its level (\"Level 2 - …\") and what it locks down. `kotlin.test` assertions on JUnit 5. Hand-written fakes, as the rest of the stack does (no mocking …",
              "hash": "451662c6c196"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Write the value types' classes in `commonTest`.",
            "files": [
              "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P1, P2)",
            "link": "plan.md#level-2--component-componenttest"
          },
          {
            "id": "stage-2",
            "what": "Write the Rome internals' classes.",
            "files": [
              "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/"
            ],
            "check": "`:webtools-feeds:componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#level-2--component-componenttest"
          },
          {
            "id": "stage-3",
            "what": "Write the fetcher's classes over the fakes.",
            "files": [
              "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `integrationTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#level-2--component-componenttest"
          }
        ],
        "tests": [
          "Level 2 (component): 25 classes, 157 tests; 10 in `commonTest`, 15 in `jvmTest`"
        ]
      }
    },
    {
      "id": "integration-tests",
      "group": "Tests",
      "name": "Level 3 integration tests over loopback",
      "system": "webtools-feeds",
      "where": "jvmTest (new, `testing.integration`)",
      "gap": {
        "kind": "missing",
        "summary": "No test runs a conditional GET through the real transport. The loopback fixtures run an `Untrusted` stack over loopback with every other rule live; the stock fixture replay refuses a server whose dispatcher was replaced, so a conditional-GET script needs a dispatcher of its own.",
        "evidence": [
          "webtools-http fixtures LoopbackFetchers.kt:37 — `untrusted(...)`, an `Untrusted` `UrlFetcher` over loopback (baseline); LoopbackTransports.kt:32, the transport alone",
          "../65-okhttp-transport/consequences.md §4 — unit 10's Level 3 integration tests need a custom `Dispatcher`; a `304` is returned with its headers and no body read",
          "../68-call-facades/consequences.md §4 — unit 10's Level 3 integration tests run over `LoopbackFetchers.untrusted` with a conditional-GET dispatcher of its own"
        ],
        "source": "../architecture.md §11; ../68-call-facades/consequences.md §4",
        "link": "../68-call-facades/consequences.md#4-sibling-units"
      },
      "options": [
        {
          "id": "loopback",
          "name": "Level 3 integration tests through the `Untrusted` loopback fixtures (`LoopbackFetchers.untrusted`, which #68 added; #55's decomposition names `LoopbackTransports.untrusted`) with a ten-line conditional-GET `mockwebserver3` dispatcher of the unit's own",
          "verdict": "accepted",
          "why": "The fetch runs through the real transport, policy and redirects; the dispatcher stays a test detail.",
          "source": "../architecture.md §11; ../68-call-facades/consequences.md §4",
          "link": "../architecture.md#11-decomposition"
        },
        {
          "id": "published-dispatcher",
          "name": "A published conditional-GET `mockwebserver3` dispatcher",
          "verdict": "declined",
          "why": "Ten lines a test writes for itself; publishing it is a non-goal.",
          "source": "../architecture.md §4",
          "link": "../architecture.md#4-non-goals"
        }
      ],
      "asBuilt": {
        "state": "deviated",
        "summary": null,
        "statements": [
          {
            "text": "Two Level 3 classes, 12 tests, passing: `FeedFetcherLoopbackIntegrationTest` (9) over `LoopbackFetchers.untrusted` and a `MockWebServer` with the test's own conditional-GET dispatcher and a real Rome parse, and `FeedFetcherOwningFactoryIntegrationTest` (3), where `FeedFetcher.untrusted` refuses a loopback URL with no request reaching the server; their names are in plain words.",
            "state": "amended",
            "ref": "final-implementation.md#stage-3--the-fetcher",
            "excerpt": {
              "ref": "final-implementation.md#stage-3--the-fetcher",
              "heading": "Stage 3 — the fetcher",
              "paragraph": "`commonMain`: `FeedLifecycle.kt` (`kindOf`), `FeedFetcherConfig.kt` (`FeedFetcherConfig`, `ResolvedFeedFetcher`, `FeedFetcherParts`, `isSendableNonBlank`), `DefaultFeedParser.kt` (the `expect`), `ConditionalHeaders.kt` (the `HEADER_*` constants, `requestHeaders`, `isConditional`, `validatorsOf`, `refreshedBy`), `ResponseVerdict.kt` (`StatusVerdict`, `statusVerdictOf`), `PermanentMove.kt` (`permanentMoveOf`), `FeedFetcher.kt` (`FeedFetcher`, its companion, the common factory and the private `Handled`/`Timed` helpers). · `jvmMain`: `DefaultFeedParser.jvm.kt` (`RomeFeedParser()`), …",
              "hash": "745d8bf31915"
            }
          },
          {
            "text": "The Level 5 manual pass ran from a scratch `main`, deleted afterwards, through `FeedFetcher.untrusted(…)` with the production defaults: nine public cases (RSS 2.0, Atom 1.0, a conditional `304`, a `301` move reported, `LimitExceeded(Entries)` and `BodyTooLarge` for long podcasts, `RefusedByPolicy(InsecureRedirect)` for an `https`-to-`http` redirect, `NotAFeed(Html)` for a web page), each with the expected outcome; no committed test runs the network. The read of each public type's KDoc as a consumer sees it, part of the same procedure, was not made: it is open with the IDE checks for the next IntelliJ session.",
            "state": "deviated",
            "ref": "final-implementation.md#p5--level-5-acceptance-pass-stage-4-manual",
            "excerpt": {
              "ref": "final-implementation.md#p5--level-5-acceptance-pass-stage-4-manual",
              "heading": "P5 — Level 5 acceptance pass (stage 4, manual)",
              "paragraph": "Run on 2026-11-06 from the build host through `FeedFetcher.untrusted(…)` with a descriptive user agent and the production defaults, by a scratch `main` in `jvmTest` that printed outcome names, entry counts and presence flags only; the scratch file was deleted afterwards. URLs are given by origin.",
              "hash": "58eadad45b69"
            }
          }
        ],
        "changes": [
          {
            "what": "Test names in plain words, with none of the code punctuation of plan §9's names: `Closed(\"feed fetcher\")` reads `Closed feed fetcher`, and `NotAFeed(Html)` reads `NotAFeed Html`.",
            "why": "A JVM method name cannot hold `.`, `:`, `;`, `/`, `<` or `>`, so the names are written as plain words throughout, the other punctuation included; and a name that points at the research means nothing to a reader of the source.",
            "when": "stage 3",
            "ref": "final-implementation.md#d7-test-names-in-plain-words"
          },
          {
            "what": "The read of each public type's KDoc as a consumer sees it, in its hover text and the dump, was not made, where plan §9.3 P5 made it part of the Level 5 pass; P5's fetches ran.",
            "why": "The read is of the KDoc as a consumer meets it, in the IDE's hover text beside the dump, so it goes with the IDE checks of plan §9.2 to the next IntelliJ session; consequences.md §8 keeps it open with its owner and trigger.",
            "when": "stage 4",
            "ref": "final-implementation.md#d6-the-kdoc-was-not-read-as-a-consumer-sees-it"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The real transport over loopback, with no outside network.",
        "statements": [
          {
            "text": "Two Level 3 classes, 12 tests: `FeedFetcher` over `LoopbackFetchers.untrusted` and a `MockWebServer` with a conditional-GET dispatcher of the test's own and a real Rome parse, and `FeedFetcher.untrusted` refusing a loopback URL with no request reaching the server.",
            "ref": "plan.md#level-3--integration-integrationtest",
            "excerpt": {
              "ref": "plan.md#level-3--integration-integrationtest",
              "heading": "Level 3 — integration (`integrationTest`)",
              "paragraph": "",
              "hash": "e45a511e2a2b"
            }
          },
          {
            "text": "A Level 5 manual pass fetches a handful of public feeds from a scratch `main` that is never committed; no committed test runs the network.",
            "ref": "plan.md#93-procedures-not-committed-tests",
            "excerpt": {
              "ref": "plan.md#93-procedures-not-committed-tests",
              "heading": "9.3 Procedures (not committed tests)",
              "paragraph": "Runs (from the repository root; on Windows `gradlew.bat`). Only the tasks of `:webtools-feeds` and the `js` compile of `:webtools-feeds-commoncheck`; no suite of `webtools-http` or of any other module runs, before or after:",
              "hash": "48d176fe878a"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-3",
            "what": "Write the two loopback classes with their conditional-GET dispatcher.",
            "files": [
              "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/feeds/"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `integrationTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#level-3--integration-integrationtest"
          },
          {
            "id": "stage-4",
            "what": "Run the Level 5 manual pass from a scratch `main`, deleted afterwards.",
            "files": [],
            "check": "P5, recorded in the as-built record",
            "link": "plan.md#93-procedures-not-committed-tests"
          }
        ],
        "tests": [
          "Level 3 (integration) `FeedFetcherLoopbackIntegrationTest` (9 tests), `FeedFetcherOwningFactoryIntegrationTest` (3 tests); Level 5 (UAT) a manual pass, not committed"
        ]
      }
    },
    {
      "id": "deterministic-tests",
      "group": "Tests",
      "name": "Level 4a deterministic tables",
      "system": "webtools-feeds",
      "where": "commonTest (new, `testing.deterministic`)",
      "gap": {
        "kind": "missing",
        "summary": "The prolog scan and the id and link rules are pure functions of their input with nothing pinning them yet.",
        "evidence": [
          "../architecture.md §6.10 — the scan sits in common code so it is Level 4a-testable",
          "../architecture.md §11 — Level 4a for the prolog scan and the id and link rules"
        ],
        "source": "../architecture.md §6.10, §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "tables",
          "name": "Level 4a deterministic tables in `commonTest` for the prolog scan and the id and link rules",
          "verdict": "accepted",
          "why": "Input to output with no side effects, run on the JVM.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "text": "15 Level 4a classes, 97 tests, passing: the prolog scan's catalogue and readings, the SHA-256, digest, id, link, text, validator and URL-origin rules, and from stage 3 the conditional-header, status and permanent-move tables, in `commonTest`.",
            "state": "as-planned",
            "ref": "final-implementation.md#tests",
            "excerpt": {
              "ref": "final-implementation.md#tests",
              "heading": "Tests",
              "paragraph": "These are plan §9.1's totals, class for class: the two abstract contract classes are not counted, and their 39 and 39 tests run through `RomeFeedParserConformanceTest` and `RomeFeedParserHardeningTest`. In the final run 327 tests pass and 1 is skipped, `RomeFeedParserJdk11NonFunctionalTest`, because no `WEBTOOLS_JDK11_HOME` names a JDK 11 on the build host (§4, P4).",
              "hash": "3c017cb43960"
            }
          },
          {
            "text": "In `jvmTest` the conformance contract runs its 39 tests against `RomeFeedParser` over the seven synthetic fixtures, and the two differentials hold the SHA-256 to `MessageDigest` and the scan to the real Rome pipeline; the scan differential's names are in plain words, two of them saying less than the plan's or saying it differently while running its cases.",
            "state": "amended",
            "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
            "excerpt": {
              "ref": "final-implementation.md#stage-2--prolog-scan-and-romefeedparser",
              "heading": "Stage 2 — prolog scan and `RomeFeedParser`",
              "paragraph": "`commonMain`: `ResultExtensions.kt` (`flatMap`), `PrologScan.kt` (`PrologScanResult` with `Empty` and `NotMarkup`, `UndecodableReason`, `PrologScan` with the three readings and the combination rule of the plan, as D1 states it), `PrologScanFailure.kt` (`toFailureOrNull`). · `jvmMain`: `RomeWireInput.kt` (`HardenedWireFeedInput`, `PrivateJdkSaxReaders`), `CountingSaxHandler.kt` (`LimitReached`, `CountingSaxHandler`), `RomeXmlReader.kt` (`openXmlReader`, `withoutLeadingAsciiWhitespace`, `looksLikeUtf16`), `RomeFailureMapping.kt` (`toFeedFailure`, `inRomeRegion`), `RomeConversion.kt` …",
              "hash": "d4f087b76854"
            }
          }
        ],
        "changes": [
          {
            "what": "Test names in plain words; the scan differential's `a seeded generator of 20,000 documents never reaches the DOCTYPE while the scan says NoDoctype` says less than the plan's name, and `every catalogue row the real XML pipeline parses is read by RomeFeedParser when the scan says NoDoctype` stands for the plan's name that pointed at the findings.",
            "why": "A JVM method name cannot hold `.`, `:`, `;`, `/`, `<` or `>`, so the names are written as plain words throughout, the other punctuation included; and a name that points at the research means nothing to a reader of the source.",
            "when": "stage 2",
            "ref": "final-implementation.md#d7-test-names-in-plain-words"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Pure functions of their input, pinned row by row.",
        "statements": [
          {
            "text": "15 Level 4a classes, 97 tests: the prolog scan's catalogue and readings, the SHA-256, digest, id, link, text, validator and URL-origin rules and the conditional-GET tables in `commonTest`.",
            "ref": "plan.md#level-4a--deterministic-deterministictest",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic-deterministictest",
              "heading": "Level 4a — deterministic (`deterministicTest`)",
              "paragraph": "",
              "hash": "7da54358514e"
            }
          },
          {
            "text": "In `jvmTest`, the conformance contract runs its 39 tests against `RomeFeedParser` over the seven synthetic fixtures, and two differentials hold the SHA-256 to `MessageDigest` and the scan to the real Rome pipeline.",
            "ref": "plan.md#level-4a--deterministic-deterministictest",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic-deterministictest",
              "heading": "Level 4a — deterministic (`deterministicTest`)",
              "paragraph": "",
              "hash": "7da54358514e"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Write the rule tables.",
            "files": [
              "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P1, P2)",
            "link": "plan.md#level-4a--deterministic-deterministictest"
          },
          {
            "id": "stage-2",
            "what": "Write the scan's catalogue and readings, the conformance contract and the two differentials.",
            "files": [
              "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/"
            ],
            "check": "`:webtools-feeds:componentTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#level-4a--deterministic-deterministictest"
          },
          {
            "id": "stage-3",
            "what": "Write the conditional-GET tables.",
            "files": [
              "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/"
            ],
            "check": "`:webtools-feeds:gatingTest`, `componentTest`, `integrationTest`, `deterministicTest`; `:webtools-feeds-commoncheck:build`; `apiDump` then `apiCheck` (P2)",
            "link": "plan.md#level-4a--deterministic-deterministictest"
          }
        ],
        "tests": [
          "Level 4a (deterministic): 15 classes, 97 tests; 12 in `commonTest`, 3 in `jvmTest`"
        ]
      }
    },
    {
      "id": "hardening-suite",
      "group": "Tests",
      "name": "Level 4c hardening and conformance suite, `FeedParserContract`",
      "system": "webtools-feeds",
      "where": "jvmTest (new, `testing.nonfunctional`)",
      "gap": {
        "kind": "risk",
        "summary": "Nothing gates a parser against hostile documents. Rome's DOM amplification on a hostile 2 MiB document at the fetcher's concurrency is unmeasured, no corpus of real feeds was parsed, and the semaphore default and the limits are judgement; the amplification under a fixed heap is an open point this unit settles.",
        "evidence": [
          "../research/feed-parsing.md Gaps G1 — parse cost unmeasured; G2 — no corpus run",
          "../architecture.md §12, §13 — the fixed-heap Level 4c suite bounds Rome's exposure; feed parsing unmeasured",
          "../consequences.md §7 — the open point unit 10 settles",
          "research/parse-memory.md F1 to F6 — a hostile 2 MiB document with no DOCTYPE needs 102 to 318 MB of heap for two parses, JDOM's builder is quadratic in depth, deep markup overflows the stack and JDOM's namespace cache grows for the life of the JVM; with four structural limits, 43 MB"
        ],
        "source": "../architecture.md §6.10, §12, §13; ../consequences.md §7",
        "link": "../architecture.md#610-webtools-feeds"
      },
      "options": [
        {
          "id": "contract-suite",
          "name": "An abstract `FeedParserContract` in `jvmTest`, run against `RomeFeedParser`: valid and malformed feeds and the hardening cases (XXE against a loopback server asserting zero requests, billion laughs, deep nesting, attribute and element floods under a fixed heap at the fetcher's concurrency, a 2 MiB text node, `xi:include`, HTML, JSON, empty and mis-encoded documents, oversize `ETag`, link and id)",
          "verdict": "accepted",
          "why": "The suite is the gate for the Rome default and for any parser put behind the port.",
          "source": "../architecture.md §6.10",
          "link": "../architecture.md#610-webtools-feeds"
        },
        {
          "id": "one-contract",
          "name": "One `FeedParserContract` class, as #55's architecture §6.10 first wrote",
          "verdict": "declined",
          "why": "Its conformance cases are deterministic and its hardening cases non-functional, and a level's task selects a class by the package of its concrete class, so the contract is two classes, one per test level.",
          "source": "plan.md §6, 8; §9.0",
          "link": "plan.md#6-where-this-plan-refines-the-parent-architecture"
        },
        {
          "id": "heap-on-task",
          "name": "A heap fixed on the `nonfunctionalTest` task, or an in-process allocation bound, as the memory gate",
          "verdict": "declined",
          "why": "A heap fixed on one task leaves `jvmTest` and the `test` alias, which run every class in one 512 MB worker, ungated; an allocation bound measures garbage, not heap safety.",
          "source": "research/README.md § Parse cost, structural limits and the fixed-heap gate",
          "link": "research/README.md#parse-cost-structural-limits-and-the-fixed-heap-gate"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "text": "The hardening contract was built as the abstract `FeedParserHardeningContract`, run by `RomeFeedParserHardeningTest`: 39 tests, 21 in the worker and 18 each in a child JVM at `-Xmx128m` with two concurrent parses, every hostile 2 MiB shape rejected in under 0.5 s, and a child that outlives its 30 s budget killed; the case names are in plain words, each child-JVM case's ending with its expected result.",
            "state": "amended",
            "ref": "final-implementation.md#stage-4--the-hardening-suite",
            "excerpt": {
              "ref": "final-implementation.md#stage-4--the-hardening-suite",
              "heading": "Stage 4 — the hardening suite",
              "paragraph": "Test support (`jvmTest`): `HostileDocuments` (the 2 MiB shapes by a fixed-capacity writer, the small structural documents, and `byName`, the child-JVM case table with each case's expected result line), `HostileParseJvm` (the launcher: `-Xmx<heap>m`, `-XX:+ExitOnOutOfMemoryError`, the worker's class path or an `@argfile` above 24,000 characters, output to a temporary file, a budget with `destroyForcibly`), `HostileParseMain` (builds the parser by factory name, generates the document, parses on a barrier, prints one result line per parse, exits 0 / 11 / 12; the launcher's own `smoke`, `oom` and …",
              "hash": "c49673a4d12f"
            }
          },
          {
            "text": "Beside it, five classes: the child-JVM launcher (3 tests), the logs' and values' secrecy (5), the parser's isolation from JDOM's shared factory (3: 6,400 parses on 16 threads with zero wrong outcomes, one parser at an attribute limit of 50 beside one at the default 100), a close racing fetches (2), and the JDK 11 run (1), skipped by its assumption because no JDK 11 is set on the build host.",
            "state": "amended",
            "ref": "final-implementation.md#d2-the-isolation-tests-two-limits-are-50-and-the-default-100",
            "excerpt": {
              "ref": "final-implementation.md#d2-the-isolation-tests-two-limits-are-50-and-the-default-100",
              "heading": "D2. The isolation test's two limits are 50 and the default 100",
              "paragraph": "Departs from: plan §9, `RomeFeedParserIsolationNonFunctionalTest`, `two parsers with different limits give the right result under sixteen threads`: \"limit 50 must reject a 60-attribute document, limit 5,000 must accept it\". · As built: the accepting parser runs on the default `maxAttributesPerElement` of 100 and the rejecting one on 50; 6,400 parses, no wrong outcome. · Why: `FeedLimits` only tightens (plan R3): `maxAttributesPerElement` cannot be set above its default of 100, so a limit of 5,000 cannot be built. The test's purpose, that one parser's limit does not reach another's, holds with …",
              "hash": "1536e83880de"
            }
          },
          {
            "text": "With the element cap removed in a scratch edit, the item, entry and text floods ran out of memory at `-Xmx128m` (exit code 3) and the two `a` floods parsed unbounded; with the cap restored every case passed again, so the gate fails on the regression it exists to catch.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--the-gates-teeth-stage-4",
            "excerpt": {
              "ref": "final-implementation.md#p3--the-gates-teeth-stage-4",
              "heading": "P3 — the gate's teeth (stage 4)",
              "paragraph": "With the element cap of `CountingSaxHandler.startElement` disabled (a scratch edit, reverted), the child-JVM cases at `-Xmx128m` with two concurrent parses gave: `item-flood` exit code 3 (`OutOfMemoryError: Java heap space`) after 1.28 s, `entry-flood` exit code 3 after 1.33 s, `a-text-flood` exit code 3 after 0.65 s; `a-flood-channel` and `a-flood-item` parsed unbounded (`ok:0` and `ok:1`) instead of `LimitExceeded:Elements`. With the cap restored every case passes again. The gate fails on the regression it exists to catch.",
              "hash": "a7c93401d176"
            }
          }
        ],
        "changes": [
          {
            "what": "The isolation test's two parsers run at an attribute limit of 50 and at the default 100, where plan §9 gave 50 and 5,000.",
            "why": "`FeedLimits` only tightens: `maxAttributesPerElement` cannot be set above its default of 100, so a limit of 5,000 cannot be built; one parser's limit still does not reach the other's.",
            "when": "stage 4",
            "ref": "final-implementation.md#d2-the-isolation-tests-two-limits-are-50-and-the-default-100",
            "link": "research/parse-memory.md"
          },
          {
            "what": "Test names in plain words: the 18 child-JVM cases' names end with their expected result (`an item flood is LimitExceeded Elements`), and `invalid XML characters are Malformed` and `10,000 attributes on each element is LimitExceeded Attributes` say less than the plan's names, and run its cases.",
            "why": "A JVM method name cannot hold `.`, `:`, `;`, `/`, `<` or `>`, so the names are written as plain words throughout, the other punctuation included; and a name that points at the research means nothing to a reader of the source.",
            "when": "stage 4",
            "ref": "final-implementation.md#d7-test-names-in-plain-words"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The gate that holds any parser behind the port to the hardening, settling Rome's DOM amplification under a fixed heap.",
        "statements": [
          {
            "text": "Six Level 4c classes, 53 tests: the hardening contract runs its 39 cases against `RomeFeedParser`, 21 in the worker and 18 each in a child JVM at `-Xmx128m` with two concurrent parses, asserting the exit code, the result kinds and a 30 s bound.",
            "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
            "excerpt": {
              "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
              "heading": "Level 4c — non-functional (`nonfunctionalTest`)",
              "paragraph": "",
              "hash": "e0340c312f54"
            }
          },
          {
            "text": "Beside it: the child-JVM launcher itself, the logs' and values' secrecy, the parser's isolation from JDOM's shared factory, a close racing fetches, and a JDK 11 run where one is installed.",
            "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
            "excerpt": {
              "ref": "plan.md#level-4c--non-functional-nonfunctionaltest",
              "heading": "Level 4c — non-functional (`nonfunctionalTest`)",
              "paragraph": "",
              "hash": "e0340c312f54"
            }
          },
          {
            "text": "A scratch run with the element cap removed must fail the item flood, which proves the gate has teeth.",
            "ref": "plan.md#93-procedures-not-committed-tests",
            "excerpt": {
              "ref": "plan.md#93-procedures-not-committed-tests",
              "heading": "9.3 Procedures (not committed tests)",
              "paragraph": "Runs (from the repository root; on Windows `gradlew.bat`). Only the tasks of `:webtools-feeds` and the `js` compile of `:webtools-feeds-commoncheck`; no suite of `webtools-http` or of any other module runs, before or after:",
              "hash": "48d176fe878a"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-4",
            "what": "Write the hostile documents, the child-JVM gate and the hardening contract, then the secrecy, isolation, close-race and JDK 11 classes.",
            "files": [
              "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/feeds/"
            ],
            "check": "`:webtools-feeds:nonfunctionalTest` (P3, P4); `:webtools-feeds:check :webtools-feeds-commoncheck:build`",
            "link": "plan.md#level-4c--non-functional-nonfunctionaltest"
          }
        ],
        "tests": [
          "Level 4c (non-functional): 6 classes, 53 tests, 18 of them each in a child JVM at `-Xmx128m`"
        ],
        "risks": [
          "The gate's heap differs between collectors and a loaded host slows the children; 128 MB is three times the worst accepted shape, and the 30 s bound detects hangs, not performance (§10)"
        ]
      }
    },
    {
      "id": "readme",
      "group": "Documents",
      "name": "The README artifact row, install line and how-to",
      "system": "webtools-feeds",
      "where": "README.md",
      "gap": {
        "kind": "missing",
        "summary": "The README's artifact table and install snippet stop at `webtools-http`, and its HTTP client stack section has no feeds how-to.",
        "evidence": [
          "README.md:11 — the last artifact row, `webtools-http` (baseline)",
          "README.md:28 — the install snippet's `webtools-http` line, no `webtools-feeds`",
          "README.md:128 — the HTTP client stack section, no feeds how-to"
        ],
        "source": "../architecture.md §11; ../consequences.md §2",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "readme-now",
          "name": "The artifact row, the install line and a feeds how-to in this unit",
          "verdict": "accepted",
          "why": "A consumer meets the module first in the README; the final pass across the stack and the evergreen architecture document are unit 11's.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "summary": null,
        "statements": [
          {
            "text": "The README gained the `webtools-feeds` row of the artifacts table, the install line marked not yet on Maven Central with the `webtools-feeds-jvm` paragraph, a \"Feeds\" section after the HTTP client stack section, the two modules and the check module's command in \"Build & test\", and the shared build logic table's multiplatform row naming `webtools-feeds`. Beyond plan §8's text, the Feeds section names each refusal before a parse, the one shared state a parse grows, and a Closing item for each `Closed` a fetch can meet around `close()`.",
            "state": "amended",
            "ref": "final-implementation.md#3-acceptance",
            "excerpt": {
              "ref": "final-implementation.md#3-acceptance",
              "heading": "3. Acceptance",
              "paragraph": "The six criteria of plan §1 (\"Acceptance\"):",
              "hash": "97ca9f6e8ab1"
            }
          },
          {
            "text": "The public declarations carry their KDoc from stages 1 to 3, stating the contracts and citing no plan; `RomeFeedParser`'s names JDOM's namespace cache as the one shared state a parse grows, and `FeedFetcher`'s each `Closed` a fetch can meet around `close()`.",
            "state": "amended",
            "ref": "final-implementation.md#d4-romefeedparsers-kdoc-names-the-shared-state-a-parse-grows",
            "excerpt": {
              "ref": "final-implementation.md#d4-romefeedparsers-kdoc-names-the-shared-state-a-parse-grows",
              "heading": "D4. `RomeFeedParser`'s KDoc names the shared state a parse grows",
              "paragraph": "Departs from: plan R12 (\"Everything is created per `parse` call; no static state is read or written. It logs nothing.\") and the class KDoc of §7.13 (\"reads and writes no static state of Rome, JDOM or the JDK. It logs nothing.\"). · As built: the code is §7.13's. The class KDoc says that every parse creates its own reader, input, builder and SAX parser and sets no process-wide configuration of Rome, JDOM or the JDK (no shared SAX factory, no system property, no default changed); that the one shared state a parse grows is JDOM's process-wide namespace cache, by at most `maxNamespaceUris` new …",
              "hash": "3a775fec21d4"
            }
          }
        ],
        "changes": [
          {
            "what": "The Feeds section names each refusal before a parse (a DOCTYPE or another markup declaration, an encoding outside the set the parser reads, a prolog over 64 KiB, a prolog that decodes two ways), the one shared state a parse grows, and a Closing item for each `Closed` around `close()`, where plan §8 gave a DOCTYPE or an unreadable prolog and no closing item.",
            "why": "The README states the contract the code and its KDoc state as built, so a consumer meets the same one in both.",
            "when": "Document",
            "ref": "final-implementation.md#d5-feedfetchers-kdoc-names-each-closed-a-fetch-can-meet-around-close"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A living document of the repository, updated in the same change.",
        "statements": [
          {
            "text": "The README gains the `webtools-feeds` row of the artifacts table, the install line with its `-jvm` note and its unreleased status, a \"Feeds\" section after the HTTP client stack section, and the module and check-module lines of \"Build & test\".",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings touched (Audience-Reach model):",
              "hash": "21a53fd4c4ef"
            }
          },
          {
            "text": "KDoc on every public declaration states the contracts, with `@param`, `@return` and `@throws`, and never cites a plan.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings touched (Audience-Reach model):",
              "hash": "21a53fd4c4ef"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-4",
            "what": "Write the README's feeds entries.",
            "files": [
              "README.md"
            ],
            "check": "Acceptance 6: the README carries the entries of §8",
            "link": "plan.md#720-readmemd-and-changelogmd-changed-stage-4"
          }
        ],
        "tests": [
          "None: a document"
        ]
      }
    },
    {
      "id": "release",
      "group": "Release",
      "name": "The ABI dump and the version",
      "system": "webtools-feeds",
      "where": "webtools-feeds/api/webtools-feeds.api (new)",
      "gap": {
        "kind": "missing",
        "summary": "`webtools-feeds` is new: it has no version, no ABI dump, and nothing published.",
        "evidence": [
          "../consequences.md §1 — `webtools-feeds` (new) at `1.0.0`, `webtools-http` and its `api` dependency on a consumer's compile classpath, Rome and `slf4j-api` at runtime",
          "../consequences.md §8 — a module's dump is committed with its first public declaration, as `apiCheck` fails until it exists"
        ],
        "source": "../architecture.md §11; ../consequences.md §1",
        "link": "../consequences.md#1-published-artifacts"
      },
      "options": [
        {
          "id": "unreleased",
          "name": "`webtools-feeds` at `1.0.0`, new and unreleased until the stack is complete, its dump `api/webtools-feeds.api` committed with its first public declaration",
          "verdict": "accepted",
          "why": "The units that build an unreleased module move no existing version; its first release is its own.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "deviated",
        "summary": null,
        "statements": [
          {
            "text": "No version moved: `webtools-feeds` `1.0.0` is new and unreleased, this landing is not a release candidate, and no other module moved.",
            "state": "as-planned",
            "ref": "final-implementation.md#final-implementation-feeds-issue-69",
            "excerpt": {
              "ref": "final-implementation.md#final-implementation-feeds-issue-69",
              "heading": "Final implementation: `feeds` (Issue #69)",
              "paragraph": "The as-built record of `SpartanLaboratories/WebTools#69`, unit 10 (`feeds`) of #55's architecture. It states what was built, where the build departs from `plan.md` and why, and the measurements that show it holds. What the unit changes for the repository, its published artifacts and the sibling units, and the known gaps and follow-ups it leaves, are in `consequences.md`.",
              "hash": "27154dbc52e3"
            }
          },
          {
            "text": "The ABI dump `webtools-feeds/api/webtools-feeds.api` was committed once, in `e3ecfe2` with stages 1 to 3, as stage 3 leaves it, rather than with each code stage; it holds no constructor of the built types and no Rome, JDOM, SAX or slf4j type, and `apiCheck` and `verifyJavaFloor` pass.",
            "state": "deviated",
            "ref": "final-implementation.md#d3-two-implementation-commits-instead-of-four",
            "excerpt": {
              "ref": "final-implementation.md#d3-two-implementation-commits-instead-of-four",
              "heading": "D3. Two implementation commits instead of four",
              "paragraph": "Departs from: plan §5.3, where each stage ends in a commit, and §11, commits 1 to 4, one per stage, each leaving the build green with its own regenerated dump. · As built: `e3ecfe2` carries stages 1 to 3, with the dump as stage 3 leaves it, `plan.md`, `consequences.md` and the dated corrections of `../architecture.md`; `25678cf` carries stage 4's test support and Level 4c classes. The `README.md` and `CHANGELOG.md` text that §5.3 places at the end of stage 4 lands with this record. · Why: the working tree held the three code stages with only the final ABI dump. A commit of stage 1 or stage 2 …",
              "hash": "1a1d0d692a59"
            }
          },
          {
            "text": "`CHANGELOG.md` gained the two `### Added` entries under `[Unreleased]`, `webtools-feeds` (unreleased, `1.0.0`) and `webtools-feeds-commoncheck`. Beyond plan §8's text, the feeds entry names the reason classes, each refusal before a parse, the failures passed through unchanged, `Closed(\"feed fetcher\")` after `close()`, and the owning factories closing their `UrlFetcher`.",
            "state": "amended",
            "ref": "final-implementation.md#3-acceptance",
            "excerpt": {
              "ref": "final-implementation.md#3-acceptance",
              "heading": "3. Acceptance",
              "paragraph": "The six criteria of plan §1 (\"Acceptance\"):",
              "hash": "97ca9f6e8ab1"
            }
          }
        ],
        "changes": [
          {
            "what": "The ABI dump was committed once, with stages 1 to 3 in `e3ecfe2`, where plan §11 regenerated and committed it with each code stage.",
            "why": "A commit of stage 1 or stage 2 on its own would have needed that stage's dump to pass `apiCheck`; stage 4 adds no public declaration and passes `apiCheck` against the same dump.",
            "when": "stages 1 to 4",
            "ref": "final-implementation.md#d3-two-implementation-commits-instead-of-four"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A new module that stays unreleased until the stack is complete.",
        "statements": [
          {
            "text": "No version moves: `webtools-feeds` `1.0.0` is new and unreleased, this landing is not a release candidate, and no other module moves.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-69-feeds`. It exists and holds the research commits `88af7dc`, `066bf17`, `5ae0bda` and `e89d86a`; the implementer confirms it rather than cutting it again. · Commits. Each leaves the build green (`apiCheck` included). The type and the split are the final call at commit time; subject lines end with `(Issue #69)`; no attribution trailers. · `feat: add the feeds module's vocabulary and entry rules (Issue #69)` — this `plan.md`, the unit's `consequences.md` and the dated corrections of `../architecture.md`; `settings.gradle.kts`, both build files, `JsActuals.kt`, …",
              "hash": "24f3f5e05922"
            }
          },
          {
            "text": "The ABI dump `webtools-feeds/api/webtools-feeds.api` is committed with each code stage: no constructor on the built types, and no Rome, JDOM, SAX or slf4j type.",
            "ref": "plan.md#719-webtools-feedsapiwebtools-feedsapi-new-regenerated-each-code-stage",
            "excerpt": {
              "ref": "plan.md#719-webtools-feedsapiwebtools-feedsapi-new-regenerated-each-code-stage",
              "heading": "7.19 `webtools-feeds/api/webtools-feeds.api` (new, regenerated each code stage)",
              "paragraph": "`./gradlew :webtools-feeds:apiDump`, committed with each code stage; `apiCheck` (a separate invocation) must pass. Expected content, for review: the classes of §7.5 to §7.9, §7.13, §7.14, §7.16 and §7.17 with their getters; no constructor on `FeedLimits`, `Feed`, `FeedEntry`, `FeedEnclosure`, `FeedFetcherConfig`, `FeedFetcher` (the `internal` constructors are not part of the ABI); the top-level factories as `FeedLimitsKt`, `FeedKt`, `FeedEntryKt`, `FeedEnclosureKt`, `FeedFetcherKt`, `FeedFetcherOwningKt`; `FeedFetcher$Companion`; no Rome, JDOM, SAX or slf4j type anywhere; no `data class` …",
              "hash": "c528b6610b3d"
            }
          },
          {
            "text": "`CHANGELOG.md` gains its entries under `[Unreleased]`.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-69-feeds`. It exists and holds the research commits `88af7dc`, `066bf17`, `5ae0bda` and `e89d86a`; the implementer confirms it rather than cutting it again. · Commits. Each leaves the build green (`apiCheck` included). The type and the split are the final call at commit time; subject lines end with `(Issue #69)`; no attribution trailers. · `feat: add the feeds module's vocabulary and entry rules (Issue #69)` — this `plan.md`, the unit's `consequences.md` and the dated corrections of `../architecture.md`; `settings.gradle.kts`, both build files, `JsActuals.kt`, …",
              "hash": "24f3f5e05922"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-1",
            "what": "Generate and commit the first ABI dump, regenerated with each code stage.",
            "files": [
              "webtools-feeds/api/webtools-feeds.api"
            ],
            "check": "`apiDump`, then `apiCheck` in a separate invocation (P1, P2)",
            "link": "plan.md#719-webtools-feedsapiwebtools-feedsapi-new-regenerated-each-code-stage"
          },
          {
            "id": "stage-4",
            "what": "Write the CHANGELOG entries under `[Unreleased]`.",
            "files": [
              "CHANGELOG.md"
            ],
            "check": "Acceptance 6: the CHANGELOG carries the entries of §8",
            "link": "plan.md#720-readmemd-and-changelogmd-changed-stage-4"
          }
        ],
        "tests": [
          "`apiCheck` against the committed dump, and `verifyJavaFloor` over Rome's, JDOM's and `rome-utils`' classes"
        ]
      }
    },
    {
      "id": "contract-variant",
      "group": "Left for later",
      "name": "A published `FeedParserContract` fixtures variant",
      "system": "webtools-feeds",
      "where": "not built",
      "gap": {
        "kind": "missing",
        "summary": "A consumer who puts its own parser behind the port has no published suite to verify it against.",
        "evidence": [
          "../architecture.md §6.10 — `webtools-feeds` 1.0.0 publishes no test-fixtures variant",
          "../consequences.md §6 — the release that graduates `FeedParser` also publishes `FeedParserContract`"
        ],
        "source": "../architecture.md §6.10, §9",
        "link": "../architecture.md#9-extension--stability"
      },
      "options": [
        {
          "id": "with-graduation",
          "name": "Published as a test-fixtures variant of `webtools-feeds` with `FeedParser`'s graduation",
          "verdict": "accepted",
          "why": "It ships when a consumer's parser has shaped the seam.",
          "source": "../architecture.md §9",
          "link": "../architecture.md#9-extension--stability"
        },
        {
          "id": "at-1-0-0",
          "name": "Published with `webtools-feeds` 1.0.0",
          "verdict": "declined",
          "why": "It doubles the exposure to the experimental publishing API, a fixtures variant in a module whose tests use another module's fixtures was not probed, and no second parser exists to use it.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "The two abstract contract classes, `FeedParserConformanceContract` and `FeedParserHardeningContract`, stay in `jvmTest`, run against `RomeFeedParser`, and `webtools-feeds` 1.0.0 publishes no test-fixtures variant; their publication stands as the follow-up `feeds-contract-variant`, with the feature release that graduates `FeedParser`.",
            "state": "as-planned",
            "ref": "consequences.md#7-follow-ups-this-unit-leaves",
            "excerpt": {
              "ref": "consequences.md#7-follow-ups-this-unit-leaves",
              "heading": "7. Follow-ups this unit leaves",
              "paragraph": "Each is additive unless noted; `plan.md` §4 and §13 name them.",
              "hash": "afe13ac1188c"
            }
          }
        ]
      },
      "verdict": {
        "kind": "leave",
        "defense": "Left for the feature release that graduates `FeedParser`.",
        "statements": [
          {
            "text": "The two contract classes stay in `jvmTest`; a published conformance or hardening variant ships with `FeedParser`'s graduation.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "HTML feed autodiscovery (`<link rel=\"alternate\">` in a page), JSON Feed, and RSS Advisory Board `<redirect>` documents; `itunes:new-feed-url`. · A stateful poller, a \"new items\" tracker, scheduling, back-off or `410` handling: the caller owns all of them. · A published conformance or hardening contract variant: it ships with the feature release that graduates `FeedParser`. The two contract classes stay in `jvmTest`. · Tolerance of XML-illegal control characters: such documents are `Malformed`. · `xml:base` beyond the Atom feed and entry elements (Rome exposes no other). · A future-dated …",
              "hash": "dc657e73f975"
            }
          }
        ]
      }
    },
    {
      "id": "not-built",
      "group": "Left for later",
      "name": "Auto-discovery and a feed poller",
      "system": "webtools-feeds",
      "where": "not built",
      "gap": {
        "kind": "missing",
        "summary": "Finding a feed from a site's URL, polling on a schedule and tracking which items are new are all left to the caller.",
        "evidence": [
          "../architecture.md §4 — HTML feed auto-discovery and a stateful feed poller or new-items tracker are non-goals",
          "../research/feed-parsing.md Gaps G9 — auto-discovery needs an HTML parser the requirements exclude"
        ],
        "source": "../architecture.md §4",
        "link": "../architecture.md#4-non-goals"
      },
      "options": [
        {
          "id": "caller",
          "name": "Left out: polling, back-off and new items (`entries` filtered by the ids seen) are the caller's, and auto-discovery is not built",
          "verdict": "accepted",
          "why": "The first consumer already keeps its seen ids and validators, and auto-discovery would bring in an HTML parser the requirements exclude.",
          "source": "../architecture.md §4, §6.10",
          "link": "../architecture.md#4-non-goals"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "summary": "",
        "statements": [
          {
            "text": "HTML feed autodiscovery, JSON Feed, the RSS `<redirect>` document and `itunes:new-feed-url` are not built; each stands as a follow-up: `feeds-autodiscovery`, `feeds-json-feed` and `feeds-redirect-documents`.",
            "state": "as-planned",
            "ref": "consequences.md#7-follow-ups-this-unit-leaves",
            "excerpt": {
              "ref": "consequences.md#7-follow-ups-this-unit-leaves",
              "heading": "7. Follow-ups this unit leaves",
              "paragraph": "Each is additive unless noted; `plan.md` §4 and §13 name them.",
              "hash": "afe13ac1188c"
            }
          },
          {
            "text": "No poller or new-items tracker is built: the caller owns the stored validators, the subscription URL, polling and new items, and `feeds-poller` stands as the follow-up if a consumer wants one from the library.",
            "state": "as-planned",
            "ref": "consequences.md#1-published-artifacts",
            "excerpt": {
              "ref": "consequences.md#1-published-artifacts",
              "heading": "1. Published artifacts",
              "paragraph": "What lands on a consumer's classpath with `webtools-feeds` 1.0.0: `webtools-http` and, through it, `kotlinx-coroutines-core` 1.10.2 on the compile classpath (`api`); at runtime `rome` 2.1.0 with `rome-utils` 2.1.0 and `jdom2` 2.0.6.1, and `slf4j-api` (Rome asks for 2.0.6; the module declares 2.0.13). Every class is Java 11 bytecode, and both library variants declare `org.gradle.jvm.version` 11. No Rome, JDOM, SAX or slf4j type appears in a public signature.",
              "hash": "55992b5f6e43"
            }
          },
          {
            "text": "Tolerance of XML-illegal control characters, a guard against a future-dated `Last-Modified` and the repair of duplicate ids are not built: such documents are `Malformed`, and each stands as a follow-up, `feeds-control-characters`, `feeds-future-last-modified` and `feeds-duplicate-ids`.",
            "state": "as-planned",
            "ref": "consequences.md#7-follow-ups-this-unit-leaves",
            "excerpt": {
              "ref": "consequences.md#7-follow-ups-this-unit-leaves",
              "heading": "7. Follow-ups this unit leaves",
              "paragraph": "Each is additive unless noted; `plan.md` §4 and §13 name them.",
              "hash": "afe13ac1188c"
            }
          }
        ]
      },
      "verdict": {
        "kind": "leave",
        "defense": "Left to the caller, or to later issues.",
        "statements": [
          {
            "text": "HTML feed autodiscovery, JSON Feed, RSS `<redirect>` documents and `itunes:new-feed-url` are not built.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "HTML feed autodiscovery (`<link rel=\"alternate\">` in a page), JSON Feed, and RSS Advisory Board `<redirect>` documents; `itunes:new-feed-url`. · A stateful poller, a \"new items\" tracker, scheduling, back-off or `410` handling: the caller owns all of them. · A published conformance or hardening contract variant: it ships with the feature release that graduates `FeedParser`. The two contract classes stay in `jvmTest`. · Tolerance of XML-illegal control characters: such documents are `Malformed`. · `xml:base` beyond the Atom feed and entry elements (Rome exposes no other). · A future-dated …",
              "hash": "dc657e73f975"
            }
          },
          {
            "text": "A stateful poller, a new-items tracker, scheduling, back-off and `410` handling are the caller's.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "HTML feed autodiscovery (`<link rel=\"alternate\">` in a page), JSON Feed, and RSS Advisory Board `<redirect>` documents; `itunes:new-feed-url`. · A stateful poller, a \"new items\" tracker, scheduling, back-off or `410` handling: the caller owns all of them. · A published conformance or hardening contract variant: it ships with the feature release that graduates `FeedParser`. The two contract classes stay in `jvmTest`. · Tolerance of XML-illegal control characters: such documents are `Malformed`. · `xml:base` beyond the Atom feed and entry elements (Rome exposes no other). · A future-dated …",
              "hash": "dc657e73f975"
            }
          },
          {
            "text": "Tolerance of XML-illegal control characters, a future-dated `Last-Modified` guard and the repair of duplicate ids are left for later.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "HTML feed autodiscovery (`<link rel=\"alternate\">` in a page), JSON Feed, and RSS Advisory Board `<redirect>` documents; `itunes:new-feed-url`. · A stateful poller, a \"new items\" tracker, scheduling, back-off or `410` handling: the caller owns all of them. · A published conformance or hardening contract variant: it ships with the feature release that graduates `FeedParser`. The two contract classes stay in `jvmTest`. · Tolerance of XML-illegal control characters: such documents are `Malformed`. · `xml:base` beyond the Atom feed and entry elements (Rome exposes no other). · A future-dated …",
              "hash": "dc657e73f975"
            }
          }
        ]
      }
    }
  ],
  "impact": {
    "basis": "as-built",
    "summary": "`webtools-feeds` is a new, unreleased Kotlin Multiplatform artifact on `webtools-http`'s public seams: a gated parser port with a hardened Rome default, a caller-owned conditional-GET fetcher over `UrlFetcher` and a frozen entry identity, with an unpublished check module compiling its common code for `js`. No existing source file changes, the build gains two modules in `settings.gradle.kts` and one `.gitattributes` line, and no version moves.",
    "none": null,
    "release": {
      "bump": "none",
      "version": null,
      "note": "`webtools-feeds` 1.0.0 is new and unreleased until #55's stack is complete; this landing is not a release candidate, and no existing version moves",
      "unreleased": true
    },
    "downstream": [
      {
        "who": "`io.github.spartanlaboratories:webtools-feeds` (new artifact)",
        "kind": "artifact",
        "system": "webtools-feeds",
        "effect": "additive",
        "summary": "New coordinates `webtools-feeds` and `webtools-feeds-jvm` at 1.0.0 with a new ABI dump: the port, the model, the validators, `FeedFetch`, the failures and the fetcher in common code, `RomeFeedParser` and the owning factories on the JVM, and no test-fixtures variant. On a consumer's classpath: `webtools-http` and `kotlinx-coroutines-core` as `api`, and at runtime Rome 2.1.0, `rome-utils`, `jdom2` 2.0.6.1 and `slf4j-api`, all Java 11 bytecode; no Rome, JDOM, SAX or slf4j type in a public signature.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Consumers of `webtools-feeds` 1.0.0, once it is released",
        "kind": "consumer",
        "system": "webtools-feeds",
        "effect": "additive",
        "summary": "A feed fetch with no state kept in memory, returning the validators to store, the final URL and any permanent move; a front door for administrator-supplied URLs under the `Untrusted` policy; a parser that refuses DOCTYPEs and bounds a document's structure, two concurrent parses fitting in about 128 MB of heap; an entry id never blank and frozen across releases; failures as values; and a port for another parser. The known gaps an operator meets are in consequences.md §8.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "The check-only `js` modules",
        "kind": "system",
        "system": "js-commoncheck",
        "effect": "additive",
        "summary": "`webtools-feeds-commoncheck` joins them, unpublished: it compiles the feeds `commonMain` for `js` against `webtools-http-commoncheck`'s `js` output and holds the `js` actuals.",
        "link": "consequences.md#source"
      },
      {
        "who": "`webtools-http`, `webtools-udp`, `webtools-scraping`, `webtools-browser`",
        "kind": "artifact",
        "system": null,
        "effect": "none",
        "summary": "No source, build-script or test change, and no version moves; `webtools-http`'s main variants are an `api` dependency of the feeds module, and its test fixtures serve the feeds tests only.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Units 3 to 9 (#62 to #68)",
        "kind": "system",
        "system": null,
        "effect": "none",
        "summary": "Nothing changes in them: the feeds module uses their public contracts as built, and `UrlFetcher` gets its first consumer in the repository.",
        "link": "consequences.md#4-sibling-units"
      },
      {
        "who": "Unit 11, the HTTP documentation (#70)",
        "kind": "system",
        "system": null,
        "effect": "additive",
        "summary": "It documents the feeds module from this unit's README \"Feeds\" section and its KDoc: the tier of each type, what the caller owns, the failures, the limits and the heap figure, the logging rule, and that the contract classes are published only with `FeedParser`'s graduation. Three statements of that KDoc go beyond the plan, and its documentation takes them from the KDoc: the one shared state a parse grows and Rome's own loggers, the `Closed` names a fetch can meet around `close()`, and the exact charset names `RomeFeedParser` reads.",
        "link": "consequences.md#4-sibling-units"
      },
      {
        "who": "This repository's build",
        "kind": "repository",
        "system": "webtools-feeds",
        "effect": "additive",
        "summary": "`settings.gradle.kts` includes the two modules and `.gitattributes` gains one line, with no `build-logic` change. `./gradlew build` does more work: 50 test classes with 328 tests, 18 hardening cases each in a child JVM with a 128 MB heap, `apiCheck` and `verifyJavaFloor` for the feeds module, and the `js` compile of its common code; no Node.js is needed.",
        "link": "consequences.md#build"
      }
    ],
    "adaptation": [
      {
        "who": "A consumer of the feed fetcher",
        "system": "webtools-feeds",
        "need": "optional",
        "summary": "Store the returned validators after every response; adopt a reported permanent move with care, as one seen over cleartext can be injected; drop the validators when `finalUrl` changes; keep polling, back-off, `410` handling and new items; allow about 128 MB of heap per two concurrent parses, summed over every fetcher; and set `parseDispatcher` where the default pool is busy; and match `HttpFailure.Closed` around `close()`, since a fetch can meet one of three component names there.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Unit 11, the HTTP documentation (#70)",
        "system": null,
        "need": "required",
        "summary": "It makes the final README pass and documents the feeds module from this unit's README section and KDoc; the `WebToolsDsl` KDoc in `webtools-http` lists that module's builders only, and the five feeds builders carry the marker too.",
        "link": "consequences.md#4-sibling-units"
      }
    ],
    "api": [
      {
        "surface": "`FeedParser`",
        "system": "webtools-feeds",
        "change": "added",
        "tier": "experimental",
        "summary": "The parser port: a plain, thread-safe `parse(input): Result<Feed>`, gated to implement.",
        "link": "plan.md#r8-feedparser-and-feedinput"
      },
      {
        "surface": "`FeedInput` and `FeedLimits`, with its factories and builder",
        "system": "webtools-feeds",
        "change": "added",
        "tier": "stable-core",
        "summary": "The parser's input, copied in and out, and ten tighten-only limits, four of them structural.",
        "link": "plan.md#r3-feedlimits"
      },
      {
        "surface": "`Feed`, `FeedEntry` and `FeedEnclosure`, with their factories and builders",
        "system": "webtools-feeds",
        "change": "added",
        "tier": "stable-core",
        "summary": "The immutable model: an id never blank and frozen, canonical absolute links or null, nullable epoch-millisecond dates and untrusted markup.",
        "link": "plan.md#r4-the-model"
      },
      {
        "surface": "`FeedValidators` and `FeedFetch` with `Fetched` and `NotModified`",
        "system": "webtools-feeds",
        "change": "added",
        "tier": "stable-core",
        "summary": "The caller-owned conditional-GET state and the closed pair of outcomes, each carrying the validators, the final URL and any permanent move.",
        "link": "plan.md#r6-feedfetch"
      },
      {
        "surface": "`FeedFailure`, to subclass",
        "system": "webtools-feeds",
        "change": "added",
        "tier": "experimental",
        "summary": "An abstract failure group under `HttpFailure`, gated to subclass.",
        "link": "plan.md#r7-feedfailure"
      },
      {
        "surface": "`NotAFeed`, `Malformed`, `Refused`, `LimitExceeded` and the reasons `NotAFeedReason`, `FeedRefusal`, `FeedLimit`",
        "system": "webtools-feeds",
        "change": "added",
        "tier": "stable-core",
        "summary": "Final leaves with public constructors and constant messages, and open constants classes for their reasons.",
        "link": "plan.md#r7-feedfailure"
      },
      {
        "surface": "`FeedFetcherConfig`, `FeedFetcher` and `FeedFetcher(urlFetcher) { }`",
        "system": "webtools-feeds",
        "change": "added",
        "tier": "stable-core",
        "summary": "The stateless conditional-GET fetcher over a caller's `UrlFetcher`, with its parser, parse dispatcher, parse concurrency and `Accept`.",
        "link": "plan.md#r10-feedfetcherconfig-feedfetcher-and-the-factories"
      },
      {
        "surface": "`RomeFeedParser` (JVM)",
        "system": "webtools-feeds",
        "change": "added",
        "tier": "stable-core",
        "summary": "The supplied default parser, usable without the opt-in.",
        "link": "plan.md#r12-romefeedparser-jvm"
      },
      {
        "surface": "`FeedFetcher.untrusted(userAgent) { }` and `FeedFetcher.untrusted(userAgent, policy) { }` (JVM)",
        "system": "webtools-feeds",
        "change": "added",
        "tier": "stable-core",
        "summary": "Owning factories that build an `Untrusted` `UrlFetcher` and close it with the fetcher.",
        "link": "plan.md#717-jmfeedfetcherowningkt-new-stage-3"
      }
    ],
    "conversion": [],
    "source": "consequences.md, reconciled with final-implementation.md; plan.md R2 for the surface and its tiers",
    "link": "consequences.md",
    "changes": [
      {
        "what": "A consumer matches `HttpFailure.Closed` around `close()`: a fetch can meet `Closed(\"feed fetcher\")`, `Closed(\"transport\")` or the composed `UrlFetcher`'s `Closed(\"fetcher\")`, where the planned impact gave `Closed` after `close()`.",
        "why": "A fetch racing `close()` goes on into the `UrlFetcher` just closed, or is cancelled by the transport's close, and every failure of the `UrlFetcher` passes through unchanged.",
        "when": "Document",
        "ref": "final-implementation.md#d5-feedfetchers-kdoc-names-each-closed-a-fetch-can-meet-around-close",
        "entry": "adaptation.0",
        "system": "webtools-feeds"
      },
      {
        "what": "The HTTP documentation (#70) takes three statements from the KDoc that go beyond plan §7: the one shared state a parse grows and Rome's own loggers, the `Closed` names around `close()`, and the charset names `RomeFeedParser` reads.",
        "why": "The KDoc as built corrects the plan's statement on static state and logging and names the `Closed` outcomes a caller can meet; the documentation that unit writes is read from the KDoc.",
        "when": "Document",
        "ref": "consequences.md#4-sibling-units",
        "entry": "downstream.5"
      }
    ]
  },
  "tests": {
    "updated": "2026-11-07T01:43:55Z",
    "added": [
      {
        "class": "FeedsConsumerGatingTest",
        "module": "webtools-feeds",
        "testLevel": "1",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/feeds/FeedsConsumerGatingTest.kt",
        "tests": 4,
        "rows": [
          "gating-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedLimitsComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedLimitsComponentTest.kt",
        "tests": 8,
        "rows": [
          "parser-port",
          "component-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedValidatorsComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedValidatorsComponentTest.kt",
        "tests": 4,
        "rows": [
          "validators",
          "component-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetchComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFetchComponentTest.kt",
        "tests": 4,
        "rows": [
          "validators",
          "component-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedComponentTest.kt",
        "tests": 5,
        "rows": [
          "model",
          "component-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedEntryComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedEntryComponentTest.kt",
        "tests": 8,
        "rows": [
          "model",
          "component-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedEnclosureComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedEnclosureComponentTest.kt",
        "tests": 5,
        "rows": [
          "model",
          "component-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFailureComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFailureComponentTest.kt",
        "tests": 5,
        "rows": [
          "failures",
          "component-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedInputComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedInputComponentTest.kt",
        "tests": 5,
        "rows": [
          "parser-port",
          "component-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "Sha256DeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/Sha256DeterministicTest.kt",
        "tests": 6,
        "rows": [
          "model",
          "deterministic-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "EntryDigestDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/EntryDigestDeterministicTest.kt",
        "tests": 3,
        "rows": [
          "model",
          "deterministic-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "EntryIdDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/EntryIdDeterministicTest.kt",
        "tests": 4,
        "rows": [
          "model",
          "deterministic-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "LinkRuleDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/LinkRuleDeterministicTest.kt",
        "tests": 8,
        "rows": [
          "model",
          "deterministic-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "TextRulesDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/TextRulesDeterministicTest.kt",
        "tests": 5,
        "rows": [
          "parser-port",
          "deterministic-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedValidatorsDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/FeedValidatorsDeterministicTest.kt",
        "tests": 2,
        "rows": [
          "validators",
          "deterministic-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "UrlOriginDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/UrlOriginDeterministicTest.kt",
        "tests": 3,
        "rows": [
          "logging",
          "deterministic-tests"
        ],
        "stage": "skeleton-and-vocabulary",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "PrologScanFailureComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/PrologScanFailureComponentTest.kt",
        "tests": 4,
        "rows": [
          "prolog-scan",
          "component-tests"
        ],
        "stage": "prolog-scan-and-rome-parser",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "RomeFailureMappingComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/RomeFailureMappingComponentTest.kt",
        "tests": 12,
        "rows": [
          "rome-parser",
          "failures",
          "component-tests"
        ],
        "stage": "prolog-scan-and-rome-parser",
        "result": {
          "state": "passed",
          "passed": 12,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "CountingSaxHandlerComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/CountingSaxHandlerComponentTest.kt",
        "tests": 5,
        "rows": [
          "rome-parser",
          "component-tests"
        ],
        "stage": "prolog-scan-and-rome-parser",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "RomeXmlReaderComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/RomeXmlReaderComponentTest.kt",
        "tests": 6,
        "rows": [
          "rome-parser",
          "component-tests"
        ],
        "stage": "prolog-scan-and-rome-parser",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "RomeConversionComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/RomeConversionComponentTest.kt",
        "tests": 14,
        "rows": [
          "rome-parser",
          "model",
          "component-tests"
        ],
        "stage": "prolog-scan-and-rome-parser",
        "result": {
          "state": "passed",
          "passed": 14,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "RomeFeedParserComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/RomeFeedParserComponentTest.kt",
        "tests": 4,
        "rows": [
          "rome-parser",
          "component-tests"
        ],
        "stage": "prolog-scan-and-rome-parser",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "PrologScanVectorsDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/PrologScanVectorsDeterministicTest.kt",
        "tests": 7,
        "rows": [
          "prolog-scan",
          "deterministic-tests"
        ],
        "stage": "prolog-scan-and-rome-parser",
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "PrologScanReadingsDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/PrologScanReadingsDeterministicTest.kt",
        "tests": 6,
        "rows": [
          "prolog-scan",
          "deterministic-tests"
        ],
        "stage": "prolog-scan-and-rome-parser",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedParserConformanceContract / RomeFeedParserConformanceTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/FeedParserConformanceContract.kt",
        "tests": 39,
        "rows": [
          "rome-parser",
          "parser-port",
          "deterministic-tests"
        ],
        "stage": "prolog-scan-and-rome-parser",
        "result": {
          "state": "passed",
          "passed": 39,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "Sha256DifferentialDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/Sha256DifferentialDeterministicTest.kt",
        "tests": 2,
        "rows": [
          "model",
          "deterministic-tests"
        ],
        "stage": "prolog-scan-and-rome-parser",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "PrologScanRomeDifferentialDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/PrologScanRomeDifferentialDeterministicTest.kt",
        "tests": 4,
        "rows": [
          "prolog-scan",
          "rome-parser",
          "deterministic-tests"
        ],
        "stage": "prolog-scan-and-rome-parser",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherConsumerGatingTest",
        "module": "webtools-feeds",
        "testLevel": "1",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/feeds/FeedFetcherConsumerGatingTest.kt",
        "tests": 5,
        "rows": [
          "gating-tests",
          "fetcher"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherConfigComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFetcherConfigComponentTest.kt",
        "tests": 6,
        "rows": [
          "fetcher",
          "component-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedsDslMarkerComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedsDslMarkerComponentTest.kt",
        "tests": 2,
        "rows": [
          "fetcher",
          "component-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherRequestComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFetcherRequestComponentTest.kt",
        "tests": 6,
        "rows": [
          "fetcher",
          "validators",
          "component-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherStatusComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFetcherStatusComponentTest.kt",
        "tests": 12,
        "rows": [
          "fetcher",
          "validators",
          "component-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 12,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherMovesComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFetcherMovesComponentTest.kt",
        "tests": 8,
        "rows": [
          "fetcher",
          "component-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherPassThroughComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFetcherPassThroughComponentTest.kt",
        "tests": 6,
        "rows": [
          "fetcher",
          "failures",
          "component-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherParseComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFetcherParseComponentTest.kt",
        "tests": 10,
        "rows": [
          "fetcher",
          "component-tests"
        ],
        "stage": "hardening-and-documentation",
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherLifecycleComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFetcherLifecycleComponentTest.kt",
        "tests": 6,
        "rows": [
          "fetcher",
          "component-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherOwningFactoryComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFetcherOwningFactoryComponentTest.kt",
        "tests": 4,
        "rows": [
          "untrusted-factory",
          "component-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherLoggingComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFetcherLoggingComponentTest.kt",
        "tests": 6,
        "rows": [
          "logging",
          "component-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherConfigDefaultsComponentTest",
        "module": "webtools-feeds",
        "testLevel": "2",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/feeds/FeedFetcherConfigDefaultsComponentTest.kt",
        "tests": 2,
        "rows": [
          "fetcher",
          "component-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherLoopbackIntegrationTest",
        "module": "webtools-feeds",
        "testLevel": "3",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/feeds/FeedFetcherLoopbackIntegrationTest.kt",
        "tests": 9,
        "rows": [
          "fetcher",
          "integration-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherOwningFactoryIntegrationTest",
        "module": "webtools-feeds",
        "testLevel": "3",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/feeds/FeedFetcherOwningFactoryIntegrationTest.kt",
        "tests": 3,
        "rows": [
          "untrusted-factory",
          "integration-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "ConditionalHeadersDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/ConditionalHeadersDeterministicTest.kt",
        "tests": 4,
        "rows": [
          "validators",
          "deterministic-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "ResponseVerdictDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/ResponseVerdictDeterministicTest.kt",
        "tests": 2,
        "rows": [
          "fetcher",
          "deterministic-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "PermanentMoveDeterministicTest",
        "module": "webtools-feeds",
        "testLevel": "4a",
        "path": "webtools-feeds/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/feeds/PermanentMoveDeterministicTest.kt",
        "tests": 2,
        "rows": [
          "fetcher",
          "deterministic-tests"
        ],
        "stage": "fetcher",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedParserHardeningContract / RomeFeedParserHardeningTest",
        "module": "webtools-feeds",
        "testLevel": "4c",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/feeds/FeedParserHardeningContract.kt",
        "tests": 39,
        "rows": [
          "hardening-suite",
          "rome-parser",
          "parser-port"
        ],
        "stage": "hardening-and-documentation",
        "result": {
          "state": "passed",
          "passed": 39,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "HostileParseJvmNonFunctionalTest",
        "module": "webtools-feeds",
        "testLevel": "4c",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/feeds/HostileParseJvmNonFunctionalTest.kt",
        "tests": 3,
        "rows": [
          "hardening-suite"
        ],
        "stage": "hardening-and-documentation",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherSecrecyNonFunctionalTest",
        "module": "webtools-feeds",
        "testLevel": "4c",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/feeds/FeedFetcherSecrecyNonFunctionalTest.kt",
        "tests": 5,
        "rows": [
          "hardening-suite",
          "logging"
        ],
        "stage": "hardening-and-documentation",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "RomeFeedParserIsolationNonFunctionalTest",
        "module": "webtools-feeds",
        "testLevel": "4c",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/feeds/RomeFeedParserIsolationNonFunctionalTest.kt",
        "tests": 3,
        "rows": [
          "hardening-suite",
          "rome-parser"
        ],
        "stage": "hardening-and-documentation",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "FeedFetcherCloseRaceNonFunctionalTest",
        "module": "webtools-feeds",
        "testLevel": "4c",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/feeds/FeedFetcherCloseRaceNonFunctionalTest.kt",
        "tests": 2,
        "rows": [
          "hardening-suite",
          "fetcher"
        ],
        "stage": "hardening-and-documentation",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "at": "2026-11-07T01:43:55Z"
        }
      },
      {
        "class": "RomeFeedParserJdk11NonFunctionalTest",
        "module": "webtools-feeds",
        "testLevel": "4c",
        "path": "webtools-feeds/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/feeds/RomeFeedParserJdk11NonFunctionalTest.kt",
        "tests": 1,
        "rows": [
          "hardening-suite",
          "rome-parser"
        ],
        "stage": "hardening-and-documentation",
        "result": {
          "state": "skipped",
          "passed": 0,
          "failed": 0,
          "skipped": 1,
          "at": "2026-11-07T01:43:55Z",
          "note": "Skipped by its assumption: no JDK 11 is set on the build host (`WEBTOOLS_JDK11_HOME` unset)."
        }
      }
    ],
    "amended": [],
    "relevant": []
  },
  "roadmap": {
    "issue": 55,
    "title": "HTTP client stack for BotTools: suspend transport, outbound-URL guard, response cache, call budget and test fakes",
    "asOf": "2026-11-06",
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
        "state": "CLOSED",
        "stateReason": "COMPLETED"
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
      "name": "feeds-contract-variant",
      "note": "the conformance and hardening contract classes published as a test-fixtures variant, with the feature release that graduates `FeedParser`"
    },
    {
      "name": "feeds-autodiscovery",
      "note": "HTML feed autodiscovery"
    },
    {
      "name": "feeds-json-feed",
      "note": "a JSON Feed parser behind the port"
    },
    {
      "name": "feeds-redirect-documents",
      "note": "the RSS `<redirect>` document and `itunes:new-feed-url`, moves the HTTP layer cannot see"
    },
    {
      "name": "feeds-poller",
      "note": "a stateful poller with a \"new items\" tracker"
    },
    {
      "name": "feeds-encoding-allowlist",
      "note": "more encoding names for the prolog scan after the same decoder probe"
    },
    {
      "name": "feeds-control-characters",
      "note": "tolerance of XML-illegal control characters; it changes today's `Malformed` result for such documents"
    },
    {
      "name": "feeds-future-last-modified",
      "note": "a guard against a future-dated `Last-Modified`, which needs the response's `Date`"
    },
    {
      "name": "feeds-namespace-cache",
      "note": "closing the residual growth of JDOM's process-wide namespace cache"
    },
    {
      "name": "feeds-duplicate-ids",
      "note": "detecting or repairing ids repeated within one feed"
    },
    {
      "name": "feeds-fetch-time-source",
      "note": "a `timeSource` on `FeedFetcherConfig`, if a test needs the per-fetch figures in virtual time"
    },
    {
      "name": "JDK 11, Linux and macOS runs",
      "note": "a JDK 11 run of the feeds suites, a Linux and macOS run of the Level 3 and Level 4c suites, and the Level 5 checks still open: the IDE checks and the read of the KDoc as a consumer sees it"
    },
    {
      "name": "a compile-fail check for the gate",
      "note": "`FeedParser` and `FeedFailure` join the gated types that only review guards today"
    },
    {
      "issue": 93,
      "note": "`Url.redacted()` shows a URL's path: the `UrlFetcher` and the transport under the feed fetcher print it until it is resolved"
    },
    {
      "issue": 58,
      "note": "CI and repository practices: the first place these suites would run on every change, and on a JDK 11 runtime"
    },
    {
      "issue": 87,
      "note": "a Level 4c class exceeding its bound when the level test tasks run in parallel; the feeds module adds Level 4c classes, some starting child JVMs"
    }
  ],
  "documents": [
    {
      "label": "Plan",
      "path": "plan.md",
      "note": "requirements R1 to R16, the design, §6 the nine points that refine #55's architecture, the four stages and the test plan"
    },
    {
      "label": "Consequences",
      "path": "consequences.md",
      "note": "the new artifact, the blast radius, the sibling units, the follow-ups, the known gaps and the plan's assumptions as built"
    },
    {
      "label": "As built",
      "path": "final-implementation.md",
      "note": "what the four stages built, the test totals, the deviations D1 to D7, the acceptance and the procedures' results"
    },
    {
      "label": "Research",
      "path": "research/README.md",
      "note": "this unit's five questions, the verification, the one claim the build did not hold and the summary the plan follows"
    },
    {
      "label": "Issue #69",
      "path": "https://github.com/SpartanLaboratories/WebTools/issues/69",
      "note": "the unit as #55 filed it"
    },
    {
      "label": "#55 research",
      "path": "../research/README.md",
      "note": "the questions, verification and summary the Gap column rests on; question 10 bears on this unit"
    },
    {
      "label": "#55 feed-parsing findings",
      "path": "../research/feed-parsing.md",
      "note": "F1 to F8, the pitfalls, the recommended surface and considerations 1 to 3 behind the options"
    },
    {
      "label": "#55 architecture",
      "path": "../architecture.md",
      "note": "§6.8 the check modules, §6.9 the common-code mechanism, §6.10 the feeds design, §9 the tiers, §10 the alternatives, §11 this unit's row, §12 and §13 the open points; §3, §12 and §13 carry this unit's dated as-built corrections"
    },
    {
      "label": "#55 consequences",
      "path": "../consequences.md",
      "note": "§1 the artifact, §7 the open points this unit settles, §8 what units 1 to 10 as built leave the later units"
    },
    {
      "label": "#68 consequences",
      "path": "../68-call-facades/consequences.md",
      "note": "§4: `UrlFetcher`'s contracts for this unit and its loopback fixtures"
    },
    {
      "label": "#65 consequences",
      "path": "../65-okhttp-transport/consequences.md",
      "note": "§4: the redirect chain's hop statuses, a `304` with no body read, and the loopback dispatcher"
    },
    {
      "label": "#62 check-plugin findings",
      "path": "../62-http-core/research/common-check-plugin.md",
      "note": "F11: the feeds check module's dependency on the http check module, probed"
    }
  ],
  "notes": {
    "origin": "This unit's architecture happened in #55's run, so the Gap and Considered columns were written at sized from #55's research README, its findings and its architecture; the unit's own research adds its evidence to the Gap column, and its plan adds to Considered, settles the two points left open and writes the Verdict at plan acceptance.",
    "corrections": [
      {
        "what": "The `failures` row's Gap link follows #55's consequences §8, renamed for units 1 to 10 as built, and the As built references follow the sections of `final-implementation.md` as reconciled (§1 What was built, the Tests table, the differential results of stage 2, D1, D2 and P3 renamed). The statements they rest on are unchanged; the excerpts are copied again.",
        "when": "Document step, 2026-11-07"
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
    "67": "../67-call-budget/dashboard.html",
    "68": "../68-call-facades/dashboard.html"
  },
  "links": {
    "workBoard": "../../index.html",
    "repoBoard": "../../../dashboards/index.html",
    "boards": {
      "webtools-feeds": "../../../dashboards/webtools-feeds/index.html"
    }
  }
}
);
