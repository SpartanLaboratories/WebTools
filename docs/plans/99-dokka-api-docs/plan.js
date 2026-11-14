/* plan.js: plan.json as a script, written by plan/scripts/embed.js beside the board for its live view (plan/RULES.md §1). Do not edit: edit plan.json and re-embed. */
window.dashboardLive&&window.dashboardLive("plan",
{
  "schema": "plan-board/1",
  "repo": "SpartanLaboratories/WebTools",
  "issue": 99,
  "slug": "99-dokka-api-docs",
  "folder": "docs/plans/99-dokka-api-docs",
  "kind": "issue",
  "title": "Dokka API docs",
  "issueTitle": "Generate API docs with Dokka: javadoc jars, GitHub Pages site and 1.0.0 release preparation",
  "function": "Give the published WebTools modules generated API documentation and prepare `webtools-http` 1.0.0 for release: Dokka output as the javadoc jars, one aggregated reference checked in CI and served beside a landing page on GitHub Pages, and the changelog section and build note that still mark 1.0.0 as unreleased.",
  "status": "built",
  "sized": "2026-11-08T22:05:00Z",
  "parent": null,
  "fixOf": null,
  "branch": "feat/issue-99-dokka-api-docs",
  "baseline": {
    "ref": "master",
    "sha": "5ae9734",
    "date": "2026-11-06"
  },
  "asBuilt": {
    "sha": "77dedde91d9aec4e549e20475cced7a51d1ef6a9",
    "merge": "77dedde91d9aec4e549e20475cced7a51d1ef6a9",
    "pr": 100,
    "date": "2026-11-09",
    "at": "2026-11-09T02:49:57Z"
  },
  "systems": [
    {
      "slug": "gradle-build",
      "name": "Gradle build",
      "status": "built",
      "board": "docs/dashboards/gradle-build/index.html",
      "slot": 4
    },
    {
      "slug": "webtools-udp",
      "name": "webtools-udp",
      "status": "built",
      "board": "docs/dashboards/webtools-udp/index.html",
      "slot": 1
    },
    {
      "slug": "webtools-http",
      "name": "webtools-http",
      "status": "built",
      "board": "docs/dashboards/webtools-http/index.html",
      "slot": 5
    },
    {
      "slug": "webtools-feeds",
      "name": "webtools-feeds",
      "status": "built",
      "board": "docs/dashboards/webtools-feeds/index.html",
      "slot": 6
    }
  ],
  "wip": {
    "step": "landed",
    "since": "2026-11-09T02:49:57Z",
    "updated": "2026-11-09T02:49:57Z",
    "detail": "Landed in PR #100.",
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
    "plan": {
      "state": "re-pass",
      "from": "qa",
      "pass": 2,
      "since": "2026-11-09T01:25:50Z"
    },
    "stages": [
      {
        "id": "kdoc",
        "name": "KDoc pass: broken links and undocumented declarations",
        "state": "done",
        "rows": [
          "kdoc-pass"
        ]
      },
      {
        "id": "dokka-build",
        "name": "Dokka in the build, javadoc jars, branding, CI job, test harness",
        "state": "done",
        "rows": [
          "javadoc-jars",
          "aggregate-reference",
          "dokka-ci",
          "dokka-test-harness",
          "brand-assets",
          "http-unreleased-note"
        ]
      },
      {
        "id": "repo-hygiene",
        "name": "Line-ending pins for the wrapper and workflows",
        "state": "done",
        "rows": [
          "wrapper-line-endings"
        ]
      },
      {
        "id": "website",
        "name": "Project website and Pages workflow",
        "state": "done",
        "rows": [
          "project-site"
        ]
      }
    ],
    "remediation": {
      "pass": 3,
      "route": "on-the-spot"
    },
    "open": [],
    "event": {
      "name": "landed",
      "at": "2026-11-09T02:49:57Z"
    },
    "history": {
      "sizing": [
        {
          "since": null,
          "ended": "2026-11-08T22:05:00Z",
          "endedBy": "sized",
          "to": "plan"
        }
      ],
      "plan": [
        {
          "since": "2026-11-08T22:05:00Z",
          "ended": "2026-11-08T23:00:00Z",
          "endedBy": "halted",
          "to": "plan",
          "event": {
            "name": "sized",
            "at": "2026-11-08T22:05:00Z"
          },
          "detail": "API documentation with Dokka across the published modules, a project site on GitHub Pages, and the webtools-http 1.0.0 release preparation. Baseline 5ae9734. Next: planning.",
          "plan": {
            "state": "writing",
            "since": "2026-11-08T22:05:00Z"
          },
          "held": true,
          "asked": [
            {
              "text": "webtools-scraping and webtools-browser are archived in the systems registry, yet the issue lists them. Are they in scope, and do they get a version bump and a Central release just for a javadoc jar?",
              "options": [
                "Dokka, no version move",
                "Dokka and a release",
                "Out of scope"
              ],
              "answer": "Dokka, no version move: they appear at `/api/` and nothing is republished.",
              "asked": "2026-11-08T23:00:00Z",
              "answered": "2026-11-08T23:10:00Z"
            },
            {
              "text": "Should the Dokka check also fail on undocumented public API?",
              "options": [
                "On for all modules",
                "Off: broken links only"
              ],
              "answer": "On for all modules.",
              "asked": "2026-11-08T23:00:00Z",
              "answered": "2026-11-08T23:10:00Z"
            },
            {
              "text": "Should the published test fixtures of webtools-http be included in the API reference?",
              "options": [
                "Include them",
                "Main API only"
              ],
              "answer": "Include them: the fixtures are part of the reference and the javadoc jar.",
              "asked": "2026-11-08T23:00:00Z",
              "answered": "2026-11-08T23:10:00Z"
            }
          ]
        },
        {
          "since": "2026-11-08T23:10:00Z",
          "ended": "2026-11-09T00:05:00Z",
          "endedBy": "plan-accepted",
          "to": "branch",
          "event": {
            "name": "resumed",
            "at": "2026-11-08T23:10:00Z"
          },
          "detail": "Scraping and browser get Dokka with no version move; the Dokka check also fails on undocumented public API; the webtools-http test fixtures are included in the reference.",
          "plan": {
            "state": "writing",
            "since": "2026-11-08T22:05:00Z"
          }
        }
      ],
      "branch": [
        {
          "since": "2026-11-09T00:05:00Z",
          "ended": "2026-11-09T00:10:00Z",
          "endedBy": "branch-cut",
          "to": "implementation",
          "event": {
            "name": "plan-accepted",
            "at": "2026-11-09T00:05:00Z"
          },
          "detail": "Plan accepted; the branch is cut next.",
          "branch": "feat/issue-99-dokka-api-docs"
        }
      ],
      "implementation": [
        {
          "since": "2026-11-08T23:10:00Z",
          "ended": "2026-11-08T23:58:40Z",
          "endedBy": "qa-pass",
          "to": "qa",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-08T23:58:30Z",
            "note": "website reported done; Quality Review next"
          },
          "detail": "Four deviations recorded in the as-built file: the toString wording for types whose exact string is their contract, the UdpChannel companion wording, the gating test's class KDoc, and the hero sample's README range.",
          "stages": [
            {
              "id": "kdoc",
              "name": "KDoc pass: broken links and undocumented declarations",
              "state": "done",
              "rows": [
                "kdoc-pass"
              ]
            },
            {
              "id": "dokka-build",
              "name": "Dokka in the build, javadoc jars, branding, CI job, test harness",
              "state": "done",
              "rows": [
                "javadoc-jars",
                "aggregate-reference",
                "dokka-ci",
                "dokka-test-harness",
                "brand-assets",
                "http-unreleased-note"
              ]
            },
            {
              "id": "repo-hygiene",
              "name": "Line-ending pins for the wrapper and workflows",
              "state": "done",
              "rows": [
                "wrapper-line-endings"
              ]
            },
            {
              "id": "website",
              "name": "Project website and Pages workflow",
              "state": "done",
              "rows": [
                "project-site"
              ]
            }
          ]
        }
      ],
      "qa": [
        {
          "since": "2026-11-09T00:10:00Z",
          "ended": "2026-11-09T00:19:24Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-08T23:58:40Z",
            "note": "Quality Review 1: running"
          },
          "detail": "Quality Review 1 running.",
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-09",
              "at": "2026-11-09T00:19:24Z",
              "findings": {
                "test": 8,
                "code": 1
              },
              "severity": {
                "minor": 4,
                "other": 5
              },
              "route": "re-plan-then-remediation"
            }
          ]
        },
        {
          "since": "2026-11-09T00:46:23Z",
          "ended": "2026-11-09T01:25:50Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-09T00:46:23Z",
            "note": "Quality Review 2: running"
          },
          "detail": "Quality Review 2 running.",
          "qa": [
            {
              "pass": 2,
              "verdict": "changes-requested",
              "date": "2026-11-09",
              "at": "2026-11-09T01:25:50Z",
              "findings": {
                "test": 9,
                "code": 5
              },
              "severity": {
                "minor": 13,
                "other": 1
              },
              "route": "re-plan-then-remediation"
            }
          ]
        },
        {
          "since": "2026-11-09T01:36:27Z",
          "ended": "2026-11-09T01:57:15Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-09T01:36:27Z",
            "note": "Quality Review 3: running"
          },
          "detail": "Quality Review 3 running.",
          "qa": [
            {
              "pass": 3,
              "verdict": "changes-requested",
              "date": "2026-11-09",
              "at": "2026-11-09T01:57:15Z",
              "findings": {
                "test": 4,
                "code": 3
              },
              "severity": {
                "minor": 7,
                "other": 0
              },
              "route": "on-the-spot"
            }
          ]
        }
      ],
      "remediation": [
        {
          "since": "2026-11-09T00:19:24Z",
          "ended": "2026-11-09T00:46:23Z",
          "endedBy": "qa-pass",
          "to": "qa",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-09T00:46:23Z",
            "note": "dokka-build"
          },
          "detail": "Resolution: dokka-build.",
          "remediation": {
            "pass": 1,
            "route": "re-plan-then-remediation",
            "steps": [
              {
                "name": "Failure-path, theme-file, source-link and check-module test changes",
                "state": "done"
              },
              {
                "name": "Plan re-pass on the real root and root gate tests",
                "state": "done"
              },
              {
                "name": "The tests the re-pass specifies",
                "state": "done"
              }
            ]
          },
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-09",
              "at": "2026-11-09T00:19:24Z",
              "findings": {
                "test": 8,
                "code": 1
              },
              "severity": {
                "minor": 4,
                "other": 5
              },
              "route": "re-plan-then-remediation"
            }
          ]
        },
        {
          "since": "2026-11-09T01:25:50Z",
          "ended": "2026-11-09T01:36:27Z",
          "endedBy": "qa-pass",
          "to": "qa",
          "event": {
            "name": "implementation-stage",
            "at": "2026-11-09T01:36:27Z",
            "note": "dokka-build"
          },
          "detail": "Resolution of Quality Review 2: dokka-build.",
          "remediation": {
            "pass": 2,
            "route": "re-plan-then-remediation",
            "steps": [
              {
                "name": "Plan wording and the workflow check",
                "state": "done"
              },
              {
                "name": "Root-script gating, theme, footer and aggregate-index tests",
                "state": "done"
              },
              {
                "name": "The workflow task-name check",
                "state": "done"
              }
            ]
          },
          "qa": [
            {
              "pass": 2,
              "verdict": "changes-requested",
              "date": "2026-11-09",
              "at": "2026-11-09T01:25:50Z",
              "findings": {
                "test": 9,
                "code": 5
              },
              "severity": {
                "minor": 13,
                "other": 1
              },
              "route": "re-plan-then-remediation"
            }
          ]
        },
        {
          "since": "2026-11-09T01:57:15Z",
          "ended": "2026-11-09T01:59:53Z",
          "endedBy": "document",
          "to": "document",
          "event": {
            "name": "qa-pass",
            "at": "2026-11-09T01:57:15Z",
            "note": "Quality Review 3: changes-requested"
          },
          "detail": "All 149 test executions passed. Nothing that ships has a defect. The findings are the text matching of one gating test, its class comment, and the order of the commits.",
          "remediation": {
            "pass": 3,
            "route": "on-the-spot"
          },
          "qa": [
            {
              "pass": 3,
              "verdict": "changes-requested",
              "date": "2026-11-09",
              "at": "2026-11-09T01:57:15Z",
              "findings": {
                "test": 4,
                "code": 3
              },
              "severity": {
                "minor": 7,
                "other": 0
              },
              "route": "on-the-spot"
            }
          ]
        }
      ],
      "document": [
        {
          "since": "2026-11-09T01:59:53Z",
          "ended": "2026-11-09T02:23:18Z",
          "endedBy": "halted",
          "to": "document",
          "event": {
            "name": "document",
            "at": "2026-11-09T02:20:52Z",
            "note": "4 of 7 tasks done"
          },
          "detail": "Document: 4 of 7 tasks done; README and CHANGELOG current.",
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
                "state": "current"
              },
              {
                "id": "state-boards",
                "name": "State boards",
                "state": "todo"
              },
              {
                "id": "document-landing",
                "name": "Document commit",
                "state": "todo"
              }
            ]
          },
          "held": true,
          "asked": [
            {
              "text": "With webtools-scraping and webtools-browser at 1.0.0 on Maven Central and 1.0.0a never published, how should this change treat them?",
              "options": [
                "Keep: no version move, no release",
                "Release both as 1.0.1"
              ],
              "answer": null,
              "asked": "2026-11-09T02:23:18Z",
              "answered": "2026-11-09T02:28:41Z"
            },
            {
              "text": "webtools-udp 2.0.1 would be the first release of what 2.0.0a holds, the handshake changes and the Java 11 floor, as well as the new javadoc jar. Should it ship?",
              "options": [
                "Ship 2.0.1 with those changes",
                "Land 2.0.1, hold the release"
              ],
              "answer": null,
              "asked": "2026-11-09T02:23:18Z",
              "answered": "2026-11-09T02:28:41Z"
            }
          ]
        },
        {
          "since": "2026-11-09T02:28:41Z",
          "ended": "2026-11-09T02:45:09Z",
          "endedBy": "landing",
          "to": "landing",
          "event": {
            "name": "document",
            "at": "2026-11-09T02:42:09Z",
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
                "name": "Document commit",
                "state": "done"
              }
            ]
          }
        }
      ],
      "landing": [
        {
          "since": "2026-11-09T02:45:09Z",
          "ended": "2026-11-09T02:45:52Z",
          "endedBy": "merge",
          "to": "merge",
          "event": {
            "name": "landing",
            "at": "2026-11-09T02:45:09Z",
            "note": "land accepted"
          },
          "detail": "Landing: the land accepted; the branch carries the code, the version bump, the reconciled documents and the state boards."
        }
      ],
      "merge": [
        {
          "since": "2026-11-09T02:45:52Z",
          "ended": "2026-11-09T02:49:57Z",
          "endedBy": "landed",
          "to": "landed",
          "event": {
            "name": "landed",
            "at": "2026-11-09T02:49:57Z"
          },
          "detail": "Landed in PR #100."
        }
      ]
    },
    "next": null,
    "qa": [
      {
        "pass": 1,
        "verdict": "changes-requested",
        "date": "2026-11-09",
        "at": "2026-11-09T00:19:24Z",
        "findings": {
          "test": 8,
          "code": 1
        },
        "severity": {
          "minor": 4,
          "other": 5
        },
        "route": "re-plan-then-remediation"
      },
      {
        "pass": 2,
        "verdict": "changes-requested",
        "date": "2026-11-09",
        "at": "2026-11-09T01:25:50Z",
        "findings": {
          "test": 9,
          "code": 5
        },
        "severity": {
          "minor": 13,
          "other": 1
        },
        "route": "re-plan-then-remediation"
      },
      {
        "pass": 3,
        "verdict": "changes-requested",
        "date": "2026-11-09",
        "at": "2026-11-09T01:57:15Z",
        "findings": {
          "test": 4,
          "code": 3
        },
        "severity": {
          "minor": 7,
          "other": 0
        },
        "route": "on-the-spot"
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
    "asked": [
      {
        "step": "plan",
        "text": "webtools-scraping and webtools-browser are archived in the systems registry, yet the issue lists them. Are they in scope, and do they get a version bump and a Central release just for a javadoc jar?",
        "options": [
          "Dokka, no version move",
          "Dokka and a release",
          "Out of scope"
        ],
        "answer": "Dokka, no version move: they appear at `/api/` and nothing is republished.",
        "asked": "2026-11-08T23:00:00Z",
        "answered": "2026-11-08T23:10:00Z"
      },
      {
        "step": "plan",
        "text": "Should the Dokka check also fail on undocumented public API?",
        "options": [
          "On for all modules",
          "Off: broken links only"
        ],
        "answer": "On for all modules.",
        "asked": "2026-11-08T23:00:00Z",
        "answered": "2026-11-08T23:10:00Z"
      },
      {
        "step": "plan",
        "text": "Should the published test fixtures of webtools-http be included in the API reference?",
        "options": [
          "Include them",
          "Main API only"
        ],
        "answer": "Include them: the fixtures are part of the reference and the javadoc jar.",
        "asked": "2026-11-08T23:00:00Z",
        "answered": "2026-11-08T23:10:00Z"
      },
      {
        "step": "document",
        "text": "With webtools-scraping and webtools-browser at 1.0.0 on Maven Central and 1.0.0a never published, how should this change treat them?",
        "options": [
          "Keep: no version move, no release",
          "Release both as 1.0.1"
        ],
        "answer": null,
        "asked": "2026-11-09T02:23:18Z",
        "answered": "2026-11-09T02:28:41Z"
      },
      {
        "step": "document",
        "text": "webtools-udp 2.0.1 would be the first release of what 2.0.0a holds, the handshake changes and the Java 11 floor, as well as the new javadoc jar. Should it ship?",
        "options": [
          "Ship 2.0.1 with those changes",
          "Land 2.0.1, hold the release"
        ],
        "answer": null,
        "asked": "2026-11-09T02:23:18Z",
        "answered": "2026-11-09T02:28:41Z"
      }
    ],
    "waits": [
      {
        "step": "plan",
        "since": "2026-11-08T23:00:00Z",
        "ended": "2026-11-08T23:10:00Z",
        "by": "halted"
      },
      {
        "step": "document",
        "since": "2026-11-09T02:23:18Z",
        "ended": "2026-11-09T02:28:41Z",
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
          "name": "Document commit",
          "state": "done"
        }
      ]
    }
  },
  "milestones": [
    {
      "column": "gap",
      "label": "Written from the issue and the baseline",
      "date": "2026-11-08",
      "note": "the eight rows named from issue #99's Problem and Proposed change, each checked against the tree at `5ae9734`; no research has been done yet"
    },
    {
      "column": "considered",
      "label": "Weighed in the plan",
      "date": "2026-11-09",
      "ref": "plan.md",
      "note": "this issue has no research folder and no architecture: the options are the ones `plan.md` §1, §2, §5 and §6 name, with the three questions answered during the plan. The plan names parts the issue does not, so the board gains the rows `kdoc-pass`, `wrapper-line-endings`, `dokka-test-harness`, `readme` and `udp-version`, and leaves `archived-modules-release` and `ci-build-test-jobs` for later, each with its Gap written here from `plan.md`"
    },
    {
      "column": "verdict",
      "label": "Plan accepted",
      "date": "2026-11-09",
      "ref": "plan.md",
      "note": "four implementation stages, the KDoc pass, Dokka in the build, the line-ending pins and the website, then the version bump and the README and CHANGELOG, in six commits on `feat/issue-99-dokka-api-docs`. One system, the Gradle build, with no consumer-facing API change: `webtools-udp` moves to `2.0.1`, the only version move, and `webtools-http` and `webtools-feeds` stay at `1.0.0`, prepared for release"
    },
    {
      "column": "built",
      "label": "Landed in PR #100",
      "date": "2026-11-09",
      "ref": "PR #100",
      "note": "merged at `77dedde`; the site's first deployment, on that push, succeeded; nothing is released yet"
    }
  ],
  "trail": [
    {
      "date": "2026-11-08",
      "column": "gap",
      "label": "Sized; the plan starts"
    },
    {
      "date": "2026-11-08",
      "column": "verdict",
      "label": "Halted at the plan: waiting on you (the maintainer's answers on scope)"
    },
    {
      "date": "2026-11-08",
      "column": "verdict",
      "label": "Resumed at the plan"
    },
    {
      "date": "2026-11-09",
      "column": "verdict",
      "label": "Plan accepted",
      "ref": "plan.md"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Branch `feat/issue-99-dokka-api-docs` cut; implementation starts at kdoc"
    },
    {
      "date": "2026-11-08",
      "column": "built",
      "label": "Implementation done (4 stages); Quality Review next"
    },
    {
      "date": "2026-11-08",
      "column": "built",
      "label": "Quality Review 1: running"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Quality Review 1: changes requested, 9 findings (4 minor, 5 other; 8 test, 1 code)"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Resolution: dokka-build"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Quality Review 2: running"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Quality Review 2: changes requested, 14 findings (13 minor, 1 other; 9 test, 5 code)"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Resolution of Quality Review 2: dokka-build"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Quality Review 3: running"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Quality Review 3: changes requested, 7 findings (7 minor; 4 test, 3 code)"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Document: 0 of 7 tasks done"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Document: Documentation review current"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Document: Reconcile documentation current"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Document: Reconcile plan current"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Document: README and CHANGELOG current"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Halted at the Document step: waiting on you (the maintainer's answers on the archived modules and the webtools-udp release)"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Resumed at the Document step"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Document: 5 of 7 tasks done"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Document: State boards current"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Document: Document commit current"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Document: every task done"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Landing: the land accepted"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Merge: merging it in PR #100",
      "ref": "PR #100"
    },
    {
      "date": "2026-11-09",
      "column": "built",
      "label": "Landed in PR #100, merge 77dedde",
      "ref": "PR #100"
    }
  ],
  "rows": [
    {
      "id": "kdoc-pass",
      "group": "Code",
      "name": "KDoc pass",
      "system": "gradle-build",
      "where": "the five published modules' sources, comments only",
      "gap": {
        "kind": "missing",
        "summary": "The published modules' KDoc cannot pass a strict Dokka run. With undocumented reporting on, Dokka reports 8 broken KDoc links and 155 undocumented public declarations in 69 files at the baseline, the published test fixtures among them.",
        "evidence": [
          "`webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ReliableReorderBuffer.kt:26` — `[seq]`, one of the 8 links naming something not in scope where it is written",
          "`webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/Log.kt:5` — `[args]`, reported by Dokka although the interface is `internal`",
          "`plan.md` Appendix A — the inventory: `webtools-http` 116 (19 of them fixtures), `webtools-feeds` 32, `webtools-udp` 5, `webtools-scraping` 1, `webtools-browser` 1"
        ],
        "source": "plan.md §2, Appendix A",
        "link": "plan.md#2-current-state"
      },
      "options": [
        {
          "id": "comments-only",
          "name": "A comments-only pass: backticks for the 8 names, a KDoc block on each of the 155 declarations",
          "verdict": "accepted",
          "why": "Each KDoc states the contract read from the implementation, nothing else in the modules changes, and the strict check can then be on from the start.",
          "source": "plan.md §6 D",
          "link": "plan.md#d-the-kdoc-pass-comments-only"
        },
        {
          "id": "wider-visibility",
          "name": "Rendering `protected` members or documenting `internal` declarations",
          "verdict": "declined",
          "why": "A non-goal of this plan: Dokka's `documentedVisibilities` stays at its public-only default.",
          "source": "plan.md §4",
          "link": "plan.md#4-non-goals"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One pass over comments, with no signature or behaviour change; it is what lets the strict Dokka check start without a warning.",
        "statements": [
          {
            "text": "Each of the 8 unresolvable names is written in backticks instead of square brackets.",
            "ref": "plan.md#d-the-kdoc-pass-comments-only",
            "excerpt": {
              "ref": "plan.md#d-the-kdoc-pass-comments-only",
              "heading": "D. The KDoc pass (comments only)",
              "paragraph": "Edits: the 8 link fixes and the 155 declarations of Appendix A. No signature, modifier or body changes; the ABI dumps stay identical (`:webtools-http:apiCheck :webtools-feeds:apiCheck` prove it for the two modules that have dumps).",
              "hash": "6af33b001185"
            }
          },
          {
            "text": "Each of the 155 undocumented public declarations gets its own KDoc block, stating the contract read from the implementation.",
            "ref": "plan.md#d-the-kdoc-pass-comments-only",
            "excerpt": {
              "ref": "plan.md#d-the-kdoc-pass-comments-only",
              "heading": "D. The KDoc pass (comments only)",
              "paragraph": "Edits: the 8 link fixes and the 155 declarations of Appendix A. No signature, modifier or body changes; the ABI dumps stay identical (`:webtools-http:apiCheck :webtools-feeds:apiCheck` prove it for the two modules that have dumps).",
              "hash": "6af33b001185"
            }
          },
          {
            "text": "No signature, modifier or body changes, so the ABI dumps stay identical.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "CI build and test jobs, `CONTRIBUTING.md`, tags and a release workflow: issue #58 (stays open). · Publishing itself. A Maven Central re-release of `webtools-scraping` or `webtools-browser`: their javadoc jars on Central keep the placeholder until a later release of those modules. · Dokka for the `*-commoncheck` modules. · Rendering `protected` members: Dokka's `documentedVisibilities` stays at its public-only default. Documenting `internal` declarations. · Any signature or behaviour change in the five modules: the KDoc pass edits comments only; the ABI dumps (`api/*.api`) stay identical. · …",
              "hash": "9c9990c06958"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-kdoc",
            "what": "Write the link fixes and the KDoc of the inventory, comments only, in the first commit, which carries the plan too.",
            "files": [
              "webtools-udp/",
              "webtools-http/",
              "webtools-feeds/",
              "webtools-scraping/",
              "webtools-browser/"
            ],
            "check": "`./gradlew dokkaGeneratePublicationHtml` exits 0 and prints no Dokka warning; `:webtools-http:apiCheck` and `:webtools-feeds:apiCheck`",
            "link": "plan.md#appendix-a---the-dokka-inventory-at-the-baseline"
          }
        ],
        "tests": [
          "`:webtools-http:apiCheck` and `:webtools-feeds:apiCheck` prove the ABI unchanged and `./gradlew assemble` that each module compiles; no module test suite runs, as the modules change only in comments"
        ],
        "risks": [
          "A KDoc link written in the new text can itself be broken: the Dokka task is the gate, and the warning is fixed in the same change."
        ]
      },
      "asBuilt": {
        "state": "amended",
        "files": [
          "webtools-browser/src/main/kotlin/com/spartanlabs/webtools/browser/WebViewer.kt",
          "webtools-scraping/src/main/kotlin/com/spartanlabs/webtools/scraping/Connector.kt",
          "webtools-feeds/src/commonMain/kotlin/com/spartanlabs/webtools/feeds/",
          "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/",
          "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/",
          "webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/",
          "webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/"
        ],
        "statements": [
          {
            "text": "The 8 unresolvable names, in `HandshakeWireFormat.kt`, `MultiConnectionUDPClient.kt`, `ReliableReorderBuffer.kt` and the two `Log.kt` files, were written in backticks instead of square brackets.",
            "state": "as-planned"
          },
          {
            "text": "Each of the 155 undocumented public declarations got its own KDoc block, with two departures from the plan's writing guidance: the `toString` of the 17 types whose class KDoc fixes the exact string omits the \"may change\" clause, and `UdpChannel.Companion` names its reliable message-size constants.",
            "state": "amended",
            "ref": "final-implementation.md#2-deviations",
            "excerpt": {
              "ref": "final-implementation.md#2-deviations",
              "heading": "2. Deviations",
              "paragraph": "Each departs from the section of `plan.md` it names. No research or parent architecture stands behind these sections, so the plan is the only basis.",
              "hash": "8611225f55c3"
            }
          },
          {
            "text": "Only comment lines changed in the modules' sources, and no `.api` dump moved.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "The member KDoc of 17 `toString` overrides states the string the class already promises and omits the \"format is not part of the API and may change\" clause.",
            "why": "The class KDoc of those types makes the exact text part of the contract, so the clause would contradict it. Every other `toString` carries the clause.",
            "when": "Implementation, stage kdoc",
            "ref": "final-implementation.md#d1-tostring-kdoc-of-the-types-whose-class-kdoc-fixes-the-exact-string"
          },
          {
            "what": "The KDoc of `UdpChannel.Companion` names the reliable message-size constants the companion declares, `DEFAULT_MAX_RELIABLE_MESSAGE_BYTES` and `MAX_RELIABLE_MESSAGE_BYTES`.",
            "why": "The plan's example calls them channel-numbering constants, which the companion does not hold.",
            "when": "Implementation, stage kdoc",
            "ref": "final-implementation.md#d2-udpchannelcompanion-kdoc"
          }
        ]
      }
    },
    {
      "id": "javadoc-jars",
      "group": "Code",
      "name": "Javadoc jars",
      "system": "gradle-build",
      "where": "`build-logic/` convention plugins",
      "gap": {
        "kind": "missing",
        "summary": "The published modules' javadoc jars carry no API documentation. No module applies Dokka: the three Kotlin/JVM modules take the publishing plugin's auto-selected `Javadoc()` jar and the two multiplatform modules a jar pinned empty.",
        "evidence": [
          "`build-logic/src/main/kotlin/webtools.published-library.gradle.kts:4-6` — never calls `configure(...)`, leaving the auto-selected `KotlinJvm(Javadoc(), Sources())`",
          "`build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts:84-86` — `JavadocJar.Empty()`, pinned so that it stays the same if a Dokka plugin is ever applied",
          "the `plugins` block of each module's build file — `webtools-udp`, `webtools-scraping` and `webtools-browser` apply `webtools.kotlin-jvm-library`; `webtools-http` and `webtools-feeds` apply `webtools.kotlin-multiplatform-library`",
          "`docs/dashboards/systems.json` — records `webtools-scraping` and `webtools-browser` as archived; the issue names both among the published modules"
        ],
        "source": "issue #99, Problem"
      },
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "build-logic/build.gradle.kts",
          "build-logic/src/main/kotlin/WebToolsBuild.kt",
          "build-logic/src/main/kotlin/webtools.published-library.gradle.kts",
          "build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts",
          "build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts",
          "webtools-http/build.gradle.kts"
        ],
        "statements": [
          {
            "text": "`webtools.published-library` applied Dokka, themed each module's pages, turned on `failOnWarning` and `reportUndocumented`, and linked \"view source\" to GitHub.",
            "state": "as-planned"
          },
          {
            "text": "Both library plugins set `JavadocJar.Dokka(WebToolsBuild.DOKKA_PUBLICATION_TASK)` on their platform, so no module publishes `JavadocJar.Empty()`.",
            "state": "as-planned"
          },
          {
            "text": "`webtools-http` unsuppressed its `jvmTestFixtures` source set, and the generated reference holds the `com.spartanlabs.webtools.http.testing` package.",
            "state": "as-planned"
          },
          {
            "text": "The two `*-commoncheck` modules got no Dokka: neither applies the plugin, and the aggregate lists only the five published modules.",
            "state": "as-planned"
          }
        ]
      },
      "options": [
        {
          "id": "dokka-explicit",
          "name": "`JavadocJar.Dokka` set explicitly in both library convention plugins, `webtools-http`'s published test fixtures included",
          "verdict": "accepted",
          "why": "The organization standard requires every publishing convention plugin to set the Dokka jar explicitly, and R5 makes the fixtures part of the reference and the jar.",
          "source": "plan.md §1 R2, R5; §2",
          "link": "plan.md#1-requirements"
        },
        {
          "id": "auto-pick",
          "name": "Relying on the publishing plugin's automatic choice of the Dokka jar",
          "verdict": "declined",
          "why": "It would work on the Kotlin/JVM route only: the multiplatform plugin pins the empty jar and would not follow it, and the standard asks for the jar to be set explicitly.",
          "source": "plan.md §2",
          "link": "plan.md#2-current-state"
        },
        {
          "id": "main-api-only",
          "name": "The main API only for `webtools-http`, leaving its published test fixtures out",
          "verdict": "declined",
          "why": "R5 puts the published fixtures, package `com.spartanlabs.webtools.http.testing`, in the reference and the javadoc jar.",
          "source": "plan.md §1 R5",
          "link": "plan.md#1-requirements"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One build convention, applied to the five published modules; their public API does not change.",
        "statements": [
          {
            "text": "`webtools.published-library` applies Dokka, themes each module's pages, turns on `failOnWarning` and `reportUndocumented`, and links \"view source\" to GitHub.",
            "ref": "plan.md#b-build-logic",
            "excerpt": {
              "ref": "plan.md#b-build-logic",
              "heading": "B. build-logic",
              "paragraph": "`build-logic/build.gradle.kts`",
              "hash": "5224389165f1"
            }
          },
          {
            "text": "Both library plugins set `JavadocJar.Dokka(WebToolsBuild.DOKKA_PUBLICATION_TASK)` on their platform, so no module publishes `JavadocJar.Empty()`.",
            "ref": "plan.md#b-build-logic",
            "excerpt": {
              "ref": "plan.md#b-build-logic",
              "heading": "B. build-logic",
              "paragraph": "`build-logic/build.gradle.kts`",
              "hash": "5224389165f1"
            }
          },
          {
            "text": "`webtools-http` unsuppresses its `jvmTestFixtures` source set, so its published test fixtures are in its javadoc jar and the reference.",
            "ref": "plan.md#c-module-build-files",
            "excerpt": {
              "ref": "plan.md#c-module-build-files",
              "heading": "C. Module build files",
              "paragraph": "`webtools-http/build.gradle.kts`",
              "hash": "92cc6344a6e9"
            }
          },
          {
            "text": "The two `*-commoncheck` modules do not get Dokka.",
            "ref": "plan.md#1-requirements",
            "excerpt": {
              "ref": "plan.md#1-requirements",
              "heading": "1. Requirements",
              "paragraph": "The issue text names only `webtools-http`; this work prepares `webtools-feeds` the same way (R10) because the two modules release together.",
              "hash": "0f8044e8743a"
            }
          }
        ],
        "steps": [
          {
            "id": "conventions",
            "what": "Add the Dokka plugin marker and the publication-task constant, apply Dokka in the published-library plugin and set the jar in both library plugins.",
            "files": [
              "build-logic/build.gradle.kts",
              "build-logic/src/main/kotlin/WebToolsBuild.kt",
              "build-logic/src/main/kotlin/webtools.published-library.gradle.kts",
              "build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts",
              "build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts"
            ],
            "check": "`dokkaJavadocJar`, `jvmDokkaJavadocJar` and `kotlinMultiplatformDokkaJavadocJar` hold `index.html` and `navigation.html`",
            "link": "plan.md#b-build-logic"
          },
          {
            "id": "fixtures",
            "what": "Unsuppress `jvmTestFixtures` in the `webtools-http` build file.",
            "files": [
              "webtools-http/build.gradle.kts"
            ],
            "check": "the `webtools-http-jvm` javadoc jar holds pages under `com.spartanlabs.webtools.http.testing/`",
            "link": "plan.md#c-module-build-files"
          }
        ],
        "tests": [
          "Level 3 integration `PublishingConventionIntegrationTest`: both library plugins' javadoc jars hold Dokka HTML",
          "Level 4b end-to-end `FixturesPublicationEndToEndTest`, check 11: both `webtools-http` javadoc jars hold `index.html` and the jvm one the fixtures' pages"
        ],
        "risks": [
          "Publishing is now gated on KDoc: a new warning fails the module's javadoc jar and so any publish, by design."
        ]
      }
    },
    {
      "id": "aggregate-reference",
      "group": "Code",
      "name": "Aggregated API reference",
      "system": "gradle-build",
      "where": "`build.gradle.kts`",
      "gap": {
        "kind": "missing",
        "summary": "The modules have no single API reference. The root project applies no plugin and sets only the group and the default version, so nothing gathers the modules into one publication.",
        "evidence": [
          "`build.gradle.kts:11-14` — the file's only code: `allprojects` setting `group` and `version`"
        ],
        "source": "issue #99, Proposed change"
      },
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "settings.gradle.kts",
          "build.gradle.kts",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/gating/webtools/buildlogic/RootScriptsAgreementGatingTest.kt"
        ],
        "statements": [
          {
            "text": "`settings.gradle.kts` carried the plugin versions in `pluginManagement.plugins`, Kotlin `2.2.0` and Dokka `2.3.0`, equal to the marker dependencies of `build-logic/build.gradle.kts`.",
            "state": "as-planned"
          },
          {
            "text": "The root build file applied Dokka and, with `apply false`, both Kotlin plugins, declared `repositories` and the five `dokka(project(...))` lines, and themed the publication.",
            "state": "as-planned"
          },
          {
            "text": "The root's `moduleVersion` was set empty, so the aggregate names no version of its own.",
            "state": "as-planned"
          },
          {
            "text": "`RootScriptsAgreementGatingTest` was added: it compares the three plugin versions in `settings.gradle.kts` and build-logic's marker dependencies, and the root's `dokka(project(...))` lines with the modules that apply a library convention plugin.",
            "state": "as-planned"
          }
        ]
      },
      "options": [
        {
          "id": "root-aggregate",
          "name": "One Dokka 2.3.0 publication at the root over the five modules, the Kotlin plugins on the root classpath with `apply false`",
          "verdict": "accepted",
          "why": "It gathers every published module into `build/dokka/html`, each module rendering its own package pages.",
          "source": "plan.md §1 R3; §5",
          "link": "plan.md#5-design"
        },
        {
          "id": "dokka-2-2-0",
          "name": "Dokka 2.2.0, the version MyGameTools pins",
          "verdict": "declined",
          "why": "Both report the same warnings at the baseline; 2.3.0 also prints the source location of each link warning and carries security updates of its bundled dependencies.",
          "source": "plan.md §5, §10",
          "link": "plan.md#5-design"
        },
        {
          "id": "no-root-kotlin",
          "name": "Dokka at the root without the Kotlin plugins on its classpath",
          "verdict": "declined",
          "why": "With the Kotlin plugin applied only through the convention plugins, Dokka and Kotlin load in different classloaders and every module renders empty.",
          "source": "plan.md §2",
          "link": "plan.md#2-current-state"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One aggregation point at the root of the build; everything else stays in a module build file or a convention plugin.",
        "statements": [
          {
            "text": "`settings.gradle.kts` carries the plugin versions in `pluginManagement.plugins`, which must equal the marker dependencies of `build-logic/build.gradle.kts`.",
            "ref": "plan.md#a-gradle-build-root",
            "excerpt": {
              "ref": "plan.md#a-gradle-build-root",
              "heading": "A. Gradle build (root)",
              "paragraph": "`settings.gradle.kts` - keep `includeBuild(\"build-logic\")` and the foojay plugin; extend `pluginManagement`:",
              "hash": "78e547a6f0bd"
            }
          },
          {
            "text": "The root build file applies Dokka and, with `apply false`, both Kotlin plugins, declares `repositories` and the five `dokka(project(...))` lines, and themes the publication.",
            "ref": "plan.md#a-gradle-build-root",
            "excerpt": {
              "ref": "plan.md#a-gradle-build-root",
              "heading": "A. Gradle build (root)",
              "paragraph": "`settings.gradle.kts` - keep `includeBuild(\"build-logic\")` and the foojay plugin; extend `pluginManagement`:",
              "hash": "78e547a6f0bd"
            }
          },
          {
            "text": "The root's `moduleVersion` is empty, as the five modules version independently and the aggregate names no version of its own.",
            "ref": "plan.md#5-design",
            "excerpt": {
              "ref": "plan.md#5-design",
              "heading": "5. Design",
              "paragraph": "Responsibility. Turn the KDoc of the five published modules into (a) the javadoc jar each module publishes, (b) one aggregated HTML reference, gated in CI and served at `/api/` beside a hand-written landing page, and (c) the release preparation of `webtools-http` and `webtools-feeds`.",
              "hash": "a6a2304f0e51"
            }
          },
          {
            "text": "A gating test fails when one of the three plugin versions differs between the root and build-logic, or a publishing module has no `dokka(project(...))` line.",
            "ref": "plan.md#1-requirements",
            "excerpt": {
              "ref": "plan.md#1-requirements",
              "heading": "1. Requirements",
              "paragraph": "The issue text names only `webtools-http`; this work prepares `webtools-feeds` the same way (R10) because the two modules release together.",
              "hash": "0f8044e8743a"
            }
          }
        ],
        "steps": [
          {
            "id": "root-build",
            "what": "Extend `pluginManagement` and add the plugins, the repositories, the five modules and the theme to the root build file.",
            "files": [
              "settings.gradle.kts",
              "build.gradle.kts"
            ],
            "check": "`./gradlew dokkaGeneratePublicationHtml` writes a `build/dokka/html/index.html` listing five modules, each with its package pages",
            "link": "plan.md#a-gradle-build-root"
          }
        ],
        "tests": [
          "Level 1 gating `RootScriptsAgreementGatingTest`: the three plugin versions agree between the root and build-logic, and the root aggregates exactly the modules that publish",
          "Level 4b end-to-end `DokkaAggregationEndToEndTest`: built on the real root `pluginManagement`, `plugins` and `dokka` blocks, the reference names both modules, neither renders empty, the theme is in the aggregate, and a broken link in one module fails the root task",
          "Level 4c non-functional `DokkaConfigurationCacheReuseNonFunctionalTest`: a second identical run reuses the configuration cache"
        ],
        "risks": [
          "The Kotlin and Dokka versions live in two places that must agree, and a difference shows no symptom in the build: `RootScriptsAgreementGatingTest` compares them."
        ]
      }
    },
    {
      "id": "dokka-ci",
      "group": "Code",
      "name": "Dokka in CI",
      "system": "gradle-build",
      "where": "`.github/workflows/` (new)",
      "gap": {
        "kind": "missing",
        "summary": "No automated job checks the KDoc links. The repository has no CI workflow, and no Dokka task exists for one to run.",
        "evidence": [
          "`.github/` — not in the tree at `5ae9734`",
          "MyGameTools `.github/workflows/ci.yml` — the repository the issue names as the pattern keeps a CI workflow"
        ],
        "source": "issue #99, Proposed change"
      },
      "asBuilt": {
        "state": "as-planned",
        "files": [
          ".github/workflows/ci.yml"
        ],
        "statements": [
          {
            "text": "`.github/workflows/ci.yml` holds one job, `docs`, on push and pull request to `master` and on `workflow_dispatch`, running `./gradlew dokkaGeneratePublicationHtml`; `RootScriptsAgreementGatingTest` holds the workflow to that task's name.",
            "state": "as-planned"
          },
          {
            "text": "`failOnWarning` was set on every publication, and the publication generated from this tree holds all five modules and the published test fixtures' package.",
            "state": "as-planned"
          },
          {
            "text": "`setup-gradle` stayed on `v5`, with `actions/checkout@v7` and `actions/setup-java@v6` beside it.",
            "state": "as-planned"
          }
        ]
      },
      "options": [
        {
          "id": "docs-job-strict",
          "name": "One CI job, `docs`, running the Dokka task with `failOnWarning` and `reportUndocumented` on for all five modules",
          "verdict": "accepted",
          "why": "A broken KDoc link or an undocumented public declaration fails the job, and this change adds the missing KDoc, so it starts with no warnings.",
          "source": "plan.md §1 R4, R7",
          "link": "plan.md#1-requirements"
        },
        {
          "id": "links-only",
          "name": "Failing on broken links only, with undocumented reporting off",
          "verdict": "declined",
          "why": "R4 turns both on for all five modules, so an undocumented public declaration fails the check too.",
          "source": "plan.md §1 R4",
          "link": "plan.md#1-requirements"
        },
        {
          "id": "default-run",
          "name": "A default Dokka run, without `failOnWarning`",
          "verdict": "declined",
          "why": "A default run with broken links still ends `BUILD SUCCESSFUL`, so it would check nothing.",
          "source": "plan.md §2",
          "link": "plan.md#2-current-state"
        },
        {
          "id": "setup-gradle-v6",
          "name": "`setup-gradle@v6`, the current major",
          "verdict": "declined",
          "why": "v6 requires accepting a separate Terms of Use for its caching component; v5 is under MIT and needs no such acceptance.",
          "source": "plan.md §2, §5",
          "link": "plan.md#5-design"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "The repository's first workflow, with one job; the build and test jobs stay with #58.",
        "statements": [
          {
            "text": "`.github/workflows/ci.yml` holds one job, `docs`, on push and pull request to `master` and on `workflow_dispatch`, running `./gradlew dokkaGeneratePublicationHtml`.",
            "ref": "plan.md#g-workflows",
            "excerpt": {
              "ref": "plan.md#g-workflows",
              "heading": "G. Workflows",
              "paragraph": "`.github/workflows/ci.yml` (new) - exactly:",
              "hash": "77df4b8fce58"
            }
          },
          {
            "text": "The check completes with no Dokka warning, the published test fixtures included.",
            "ref": "plan.md#1-requirements",
            "excerpt": {
              "ref": "plan.md#1-requirements",
              "heading": "1. Requirements",
              "paragraph": "The issue text names only `webtools-http`; this work prepares `webtools-feeds` the same way (R10) because the two modules release together.",
              "hash": "0f8044e8743a"
            }
          },
          {
            "text": "`setup-gradle` stays on `v5` while the other actions use their current majors.",
            "ref": "plan.md#5-design",
            "excerpt": {
              "ref": "plan.md#5-design",
              "heading": "5. Design",
              "paragraph": "Responsibility. Turn the KDoc of the five published modules into (a) the javadoc jar each module publishes, (b) one aggregated HTML reference, gated in CI and served at `/api/` beside a hand-written landing page, and (c) the release preparation of `webtools-http` and `webtools-feeds`.",
              "hash": "a6a2304f0e51"
            }
          }
        ],
        "steps": [
          {
            "id": "ci-workflow",
            "what": "Add the workflow with its one job.",
            "files": [
              ".github/workflows/ci.yml"
            ],
            "check": "the file holds exactly one job, `docs`; it is first exercised on this change's pull request",
            "link": "plan.md#g-workflows"
          }
        ],
        "tests": [
          "Level 1 gating `RootScriptsAgreementGatingTest`: `ci.yml` and `pages.yml` each run the Dokka publication task, and the root build sets `failOnWarning`",
          "Level 3 integration `DokkaConventionIntegrationTest`: a broken link, an undocumented public function, a missing stylesheet and a missing logo each fail the task"
        ],
        "risks": [
          "Every future change is gated on KDoc: a new broken link or undocumented public declaration fails the job, by design."
        ]
      }
    },
    {
      "id": "wrapper-line-endings",
      "group": "Code",
      "name": "`gradlew` mode and LF pins",
      "system": "gradle-build",
      "where": "`gradlew`, `.gitattributes`",
      "gap": {
        "kind": "debt",
        "summary": "`gradlew` cannot run on a Linux runner, and nothing pins its line endings. It is tracked with mode `100644`, so `./gradlew` fails with \"Permission denied\", and `.gitattributes` pins LF for `*.kt` and `*.kts` only.",
        "evidence": [
          "`git ls-files -s gradlew` — mode `100644` at the baseline",
          "`.gitattributes` — pins LF for `*.kt` and `*.kts` only"
        ],
        "source": "plan.md §2",
        "link": "plan.md#2-current-state"
      },
      "options": [
        {
          "id": "mode-and-pins",
          "name": "Mark `gradlew` executable in the index and pin LF for it and the workflows",
          "verdict": "accepted",
          "why": "Without the mode change both workflows fail on the Linux runner, and a carriage return in either file breaks it.",
          "source": "plan.md §6 H, §10",
          "link": "plan.md#h-repository-hygiene"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "A mode change and a line-ending pin, in their own commit.",
        "statements": [
          {
            "text": "`gradlew` moves from mode `100644` to `100755`, a mode-only change no working-tree edit can carry.",
            "ref": "plan.md#h-repository-hygiene",
            "excerpt": {
              "ref": "plan.md#h-repository-hygiene",
              "heading": "H. Repository hygiene",
              "paragraph": "`.gitignore` - append:",
              "hash": "650e5755c3b2"
            }
          },
          {
            "text": "`.gitattributes` pins LF for `gradlew` and the workflow files.",
            "ref": "plan.md#h-repository-hygiene",
            "excerpt": {
              "ref": "plan.md#h-repository-hygiene",
              "heading": "H. Repository hygiene",
              "paragraph": "`.gitignore` - append:",
              "hash": "650e5755c3b2"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-hygiene",
            "what": "Make the mode change and add the `.gitattributes` lines.",
            "files": [
              "gradlew",
              ".gitattributes"
            ],
            "check": "`git ls-files -s gradlew` reports `100755`",
            "link": "plan.md#h-repository-hygiene"
          }
        ],
        "tests": [],
        "risks": [
          "The mode change has to be in the change set: without it `./gradlew` fails on the Linux runner and both workflows fail at their last step."
        ]
      },
      "asBuilt": {
        "state": "as-planned",
        "files": [
          ".gitattributes",
          "gradlew"
        ],
        "summary": null,
        "statements": [
          {
            "text": "`gradlew` moved from mode `100644` to `100755` in `7d61d02`, a mode-only change, and stands at `100755` in the index at the merge.",
            "state": "as-planned"
          },
          {
            "text": "`.gitattributes` pins LF for `gradlew` and the workflow files.",
            "state": "as-planned"
          }
        ]
      }
    },
    {
      "id": "dokka-test-harness",
      "group": "Tests",
      "name": "build-logic test harness",
      "system": "gradle-build",
      "where": "`build-logic/src/test/kotlin/com/spartanlabs/testing/`",
      "gap": {
        "kind": "missing",
        "summary": "The build-logic checks cannot take a root build file that aggregates the modules. `ModuleSource.rootBuildFile()` returns the whole root script, and one naming `dokka(project(\":webtools-udp\"))` cannot be copied into a generated build that holds only `webtools-http`.",
        "evidence": [
          "`build-logic/src/test/kotlin/com/spartanlabs/testing/support/webtools/buildlogic/ModuleSource.kt` — `rootBuildFile()` returns the whole root `build.gradle.kts`",
          "`FixturesPublicationEndToEndTest` and `FixturesCheckWiringEndToEndTest` — copy it into generated builds that need only the root's `group` and `version`"
        ],
        "source": "plan.md §2",
        "link": "plan.md#2-current-state"
      },
      "options": [
        {
          "id": "root-blocks",
          "name": "Read the root's `pluginManagement`, `plugins`, `dokka` and `allprojects` blocks into the generated builds",
          "verdict": "accepted",
          "why": "The checks then prove the real root plugin block, theme and settings without a `dokka(project(...))` line for a module the generated build lacks.",
          "source": "plan.md §5",
          "link": "plan.md#5-design"
        },
        {
          "id": "copy-whole",
          "name": "Keep copying the root build file whole",
          "verdict": "declined",
          "why": "A root script naming the five modules cannot be copied into a build that holds only `webtools-http`.",
          "source": "plan.md §2",
          "link": "plan.md#2-current-state"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "Test support for the build's own checks; no published artifact takes any of it.",
        "statements": [
          {
            "text": "`ModuleSource.rootBuildFile()` is replaced by `rootBlock(fileName, blockName)`, and the generated builds read the root's blocks.",
            "ref": "plan.md#i-build-logic-test-harness",
            "excerpt": {
              "ref": "plan.md#i-build-logic-test-harness",
              "heading": "I. build-logic test harness",
              "paragraph": "All under `build-logic/src/test/kotlin/com/spartanlabs/testing/`. Read the neighbouring test before writing: each class has the form of its neighbour (`@TestInstance(PER_CLASS)`, `@BeforeAll` runs the nested builds, `@AfterAll` closes the workspace, one test class per file, JUnit 5, no MockK because no collaborator is mocked: the nested builds are the real boundary).",
              "hash": "73d593a8a7fb"
            }
          },
          {
            "text": "`SampleProject` carries placeholder theme files and a documented `Greeter`, so a generated build's Dokka tasks run under `failOnWarning`.",
            "ref": "plan.md#i-build-logic-test-harness",
            "excerpt": {
              "ref": "plan.md#i-build-logic-test-harness",
              "heading": "I. build-logic test harness",
              "paragraph": "All under `build-logic/src/test/kotlin/com/spartanlabs/testing/`. Read the neighbouring test before writing: each class has the form of its neighbour (`@TestInstance(PER_CLASS)`, `@BeforeAll` runs the nested builds, `@AfterAll` closes the workspace, one test class per file, JUnit 5, no MockK because no collaborator is mocked: the nested builds are the real boundary).",
              "hash": "73d593a8a7fb"
            }
          },
          {
            "text": "A gating test of what the root scripts, build-logic and the two workflows must agree on, a Dokka convention integration test, an aggregation end-to-end test and a configuration-cache check are new.",
            "ref": "plan.md#9-test-plan",
            "excerpt": {
              "ref": "plan.md#9-test-plan",
              "heading": "9. Test plan",
              "paragraph": "Runs that exercise the change, and only those: `:build-logic:test`, `:build-logic:integrationTest`, `:build-logic:e2eTest`, `:build-logic:nonfunctionalTest`, `:build-logic:fixturesPublicationCheck`, `./gradlew dokkaGeneratePublicationHtml` (exit 0 and no Dokka warning, that is no line starting `w: [:`), `./gradlew assemble` (each module compiles), `./gradlew :webtools-http:apiCheck :webtools-feeds:apiCheck`. No module test suite runs: the five modules change only in comments, two build notes and the fixtures' Dokka line; `apiCheck` proves the ABI unchanged and `assemble` that each module …",
              "hash": "eeda62eacf42"
            }
          },
          {
            "text": "The publishing integration test now expects a Dokka javadoc jar, and the check-only modules' integration test also rules out a Dokka task.",
            "ref": "plan.md#9-test-plan",
            "excerpt": {
              "ref": "plan.md#9-test-plan",
              "heading": "9. Test plan",
              "paragraph": "Runs that exercise the change, and only those: `:build-logic:test`, `:build-logic:integrationTest`, `:build-logic:e2eTest`, `:build-logic:nonfunctionalTest`, `:build-logic:fixturesPublicationCheck`, `./gradlew dokkaGeneratePublicationHtml` (exit 0 and no Dokka warning, that is no line starting `w: [:`), `./gradlew assemble` (each module compiles), `./gradlew :webtools-http:apiCheck :webtools-feeds:apiCheck`. No module test suite runs: the five modules change only in comments, two build notes and the fixtures' Dokka line; `apiCheck` proves the ABI unchanged and `assemble` that each module …",
              "hash": "eeda62eacf42"
            }
          }
        ],
        "steps": [
          {
            "id": "harness",
            "what": "Rewrite the harness support, add the new test classes and change the existing ones.",
            "files": [
              "build-logic/src/test/kotlin/com/spartanlabs/testing/"
            ],
            "check": "`:build-logic:test`, `integrationTest`, `e2eTest`, `nonfunctionalTest` and `fixturesPublicationCheck`",
            "link": "plan.md#i-build-logic-test-harness"
          }
        ],
        "tests": [
          "Level 1 gating `BuildLogicPluginsGatingTest`: the plugin-under-test metadata carries the Dokka plugin jar",
          "Level 1 gating `RootScriptsAgreementGatingTest`: read from the real files, the root scripts and build-logic agree on the plugin versions and the aggregated modules, the root build sets `failOnWarning`, and both workflows run the Dokka publication task",
          "Level 3 integration `CommonCheckPluginIntegrationTest`: the check-only modules have no Dokka task",
          "Level 4b end-to-end `FixturesCheckWiringEndToEndTest`: the root workspace has the real blocks, and an including build does not schedule the fixtures check"
        ],
        "risks": [
          "The nested-build tasks now also run Dokka, so a cold TestKit home downloads its generator and `fixturesPublicationCheck` takes longer."
        ]
      },
      "asBuilt": {
        "state": "amended",
        "files": [
          "build-logic/src/test/kotlin/com/spartanlabs/testing/support/webtools/buildlogic/ModuleSource.kt",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/support/webtools/buildlogic/SampleProject.kt",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/gating/webtools/buildlogic/BuildLogicPluginsGatingTest.kt",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/gating/webtools/buildlogic/RootScriptsAgreementGatingTest.kt",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/PublishingConventionIntegrationTest.kt",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/CommonCheckPluginIntegrationTest.kt",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/DokkaConventionIntegrationTest.kt",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/e2e/webtools/buildlogic/FixturesPublicationEndToEndTest.kt",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/e2e/webtools/buildlogic/FixturesCheckWiringEndToEndTest.kt",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/e2e/webtools/buildlogic/DokkaAggregationEndToEndTest.kt",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/nonfunctional/webtools/buildlogic/DokkaConfigurationCacheReuseNonFunctionalTest.kt"
        ],
        "statements": [
          {
            "text": "`ModuleSource.rootBuildFile()` was replaced by `rootBlock(fileName, blockName)`, and the generated builds read the root's blocks.",
            "state": "as-planned"
          },
          {
            "text": "`SampleProject` gained placeholder theme files and a documented `Greeter`, for the generated builds' Dokka tasks under `failOnWarning`.",
            "state": "as-planned"
          },
          {
            "text": "`RootScriptsAgreementGatingTest`, which also holds the root's `failOnWarning` and both workflows' Dokka task, `DokkaConventionIntegrationTest`, `DokkaAggregationEndToEndTest` and `DokkaConfigurationCacheReuseNonFunctionalTest` are new, and the four failure cases of `DokkaConventionIntegrationTest` assert that `:dokkaGeneratePublicationHtml` fails, the task that runs in those single-project samples. The gate reads its files more strictly than the plan's patterns: it drops line comments before matching, anchors its matches at the start of a line, and takes `include(...)` with or without the leading colon.",
            "state": "amended",
            "ref": "final-implementation.md#d4-dokkaconventionintegrationtest-asserts-the-publication-task-in-its-failure-cases",
            "excerpt": {
              "ref": "final-implementation.md#d4-dokkaconventionintegrationtest-asserts-the-publication-task-in-its-failure-cases",
              "heading": "D4. `DokkaConventionIntegrationTest` asserts the publication task in its failure cases",
              "paragraph": "*Plan: section 9, the Level 3 row.* Each of the four failure tests asserts that `:dokkaGeneratePublicationHtml` is `FAILED`, not `:dokkaGenerateModuleHtml`: in these single-project samples the publication task is the one that runs and fails, and `:dokkaGenerateModuleHtml` has no outcome. The module task is asserted where it runs, in the multi-module composite of `DokkaAggregationEndToEndTest`.",
              "hash": "e928bd218d96"
            }
          },
          {
            "text": "The publishing integration test expects a Dokka javadoc jar, `CommonCheckPluginIntegrationTest` also shows the check-only modules have no Dokka task, and the class KDoc of `BuildLogicPluginsGatingTest` names the plugins in a parenthesis, as it had no list to add to.",
            "state": "amended",
            "ref": "final-implementation.md#d3-buildlogicpluginsgatingtest-class-kdoc",
            "excerpt": {
              "ref": "final-implementation.md#d3-buildlogicpluginsgatingtest-class-kdoc",
              "heading": "D3. `BuildLogicPluginsGatingTest` class KDoc",
              "paragraph": "*Plan: section 6 I.* The class KDoc had no list of plugins to add \"dokka\" to, so it now names the plugins in a parenthesis (\"Kotlin, vanniktech, binary-compatibility-validator and dokka\").",
              "hash": "293909ea3534"
            }
          }
        ],
        "changes": [
          {
            "what": "The class KDoc of `BuildLogicPluginsGatingTest` names the plugins in a parenthesis: \"Kotlin, vanniktech, binary-compatibility-validator and dokka\".",
            "why": "The plan adds \"dokka\" to the class KDoc's list of plugins, and the class KDoc had no such list.",
            "when": "Implementation, stage dokka-build",
            "ref": "final-implementation.md#d3-buildlogicpluginsgatingtest-class-kdoc"
          },
          {
            "what": "The four failure cases of `DokkaConventionIntegrationTest` assert that `:dokkaGeneratePublicationHtml` is `FAILED`, not `:dokkaGenerateModuleHtml`.",
            "why": "In these single-project samples the publication task is the one that runs and fails, and `:dokkaGenerateModuleHtml` has no outcome. The module task is asserted where it runs, in the multi-module composite of `DokkaAggregationEndToEndTest`.",
            "when": "Resolution",
            "ref": "final-implementation.md#d4-dokkaconventionintegrationtest-asserts-the-publication-task-in-its-failure-cases"
          },
          {
            "what": "`RootScriptsAgreementGatingTest` drops `//` line comments before matching, anchors its marker-version and plugin-id matches at the start of a line, and takes each `include(...)` statement and argument with or without the leading colon.",
            "why": "The plan's patterns could count commented-out text and missed two statements on one line and a module named without its colon, both of which Gradle accepts, so a version mismatch or a dropped module could pass.",
            "when": "During the Quality Reviews",
            "ref": "final-implementation.md#d5-rootscriptsagreementgatingtest-reads-its-files-more-strictly-than-the-plans-text"
          }
        ]
      }
    },
    {
      "id": "project-site",
      "group": "Documents",
      "name": "Project site",
      "system": "gradle-build",
      "where": "`website/` (new), `.github/workflows/` (new)",
      "gap": {
        "kind": "missing",
        "summary": "The project has no site and no hosted API reference. There is no landing page and no workflow to deploy one, and the README mentions neither Dokka nor a site.",
        "evidence": [
          "`website/` — not in the tree at `5ae9734`",
          "`README.md` — no mention of Dokka, GitHub Pages or an API reference",
          "MyGameTools `website/` and `.github/workflows/pages.yml` — the pattern the issue names"
        ],
        "source": "issue #99, Proposed change"
      },
      "asBuilt": {
        "state": "amended",
        "files": [
          "website/",
          ".github/workflows/pages.yml",
          ".gitignore"
        ],
        "statements": [
          {
            "text": "`website/` holds a landing page, its `404.html`, `robots.txt`, `sitemap.xml` and assets, adapted from MyGameTools', and its hero code card holds the `FeedFetcher.untrusted(...)` example, `README.md:308-321`. The Modules table names `1.0.0` for `webtools-scraping` and `webtools-browser`, the version Maven Central holds, where the plan named `1.0.0a`.",
            "state": "amended",
            "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds",
            "excerpt": {
              "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds",
              "heading": "D7. `README.md`, the site and the CHANGELOG state what Maven Central holds",
              "paragraph": "*Plan: sections 6 F and 8.* The plan took the lettered versions in the build files for published ones. Maven Central holds `webtools-udp` up to `2.0.0`, and `webtools-scraping` and `webtools-browser` at `1.0.0` only; `2.0.0a` and `1.0.0a` were never published. So the site's Modules table names `1.0.0` for those two modules where section 6 F says `1.0.0a`; the README's Install note and Java floor paragraph say the floor starts with `webtools-udp` 2.0.1 and that the two archived modules' published builds need JDK 23 or newer; and the CHANGELOG's `[2.0.1]` section lists what it carries from …",
              "hash": "4ce70d4a1ef0"
            }
          },
          {
            "text": "`.github/workflows/pages.yml` runs the root Dokka task, assembles `website/`, `docs/brand/` and `build/dokka/html` as `api/` into `_site`, and deploys it on each push to `master`; `RootScriptsAgreementGatingTest` holds it to the task's name.",
            "state": "as-planned"
          },
          {
            "text": "The workflow fails with an `::error::` annotation if `build/dokka/html/index.html` is missing.",
            "state": "as-planned"
          },
          {
            "text": "`.gitignore` keeps `/website/api/`, `/website/assets/brand/` and `/_site/` out of the repository.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "The site's Modules table names `1.0.0` for `webtools-scraping` and `webtools-browser`.",
            "why": "The plan took the lettered versions in the build files for published ones. Maven Central holds those two modules at `1.0.0` only; `1.0.0a` was never published.",
            "when": "Document",
            "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds"
          },
          {
            "what": "`pages.yml` is in the Dokka commit `f2ca518`, not in the website commit `c3e3500`, and the five commits land together rather than each standing alone.",
            "why": "`RootScriptsAgreementGatingTest` reads both workflows and fails on a tree without one; `f2ca518` needs the KDoc of `0cf4e2c`, and its workflows run on Linux only after `7d61d02` makes `gradlew` executable.",
            "when": "Code landing",
            "ref": "final-implementation.md#d6-the-commits-landed-in-a-different-grouping"
          }
        ]
      },
      "options": [
        {
          "id": "site-and-pages",
          "name": "A hand-written landing page in `website/`, adapted from MyGameTools', deployed with the reference at `/api/` by `pages.yml`",
          "verdict": "accepted",
          "why": "It is the pattern the issue names, and its content is taken from `README.md`.",
          "source": "plan.md §1 R8; §6 F",
          "link": "plan.md#f-the-website"
        },
        {
          "id": "version-pill",
          "name": "A version pill in the site's header, as in MyGameTools'",
          "verdict": "declined",
          "why": "The five modules version independently, so no one version names the project.",
          "source": "plan.md §6 F",
          "link": "plan.md#f-the-website"
        },
        {
          "id": "fallback-search",
          "name": "A fallback search for the Dokka output in `pages.yml`",
          "verdict": "declined",
          "why": "A changed output directory must fail the workflow rather than ship a site with no API reference.",
          "source": "plan.md §5, §6 G",
          "link": "plan.md#g-workflows"
        },
        {
          "id": "upload-artifact-v4",
          "name": "`actions/upload-pages-artifact@v4`",
          "verdict": "declined",
          "why": "It excludes dotfiles by default; v5 has the `include-hidden-files` input the standard's `.nojekyll` needs.",
          "source": "plan.md §2",
          "link": "plan.md#2-current-state"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "A static, dependency-free page and one deployment workflow; it adds nothing to the published artifacts.",
        "statements": [
          {
            "text": "`website/` holds a landing page, its `404.html`, `robots.txt`, `sitemap.xml` and assets, each adapted from MyGameTools' and claiming nothing `README.md` does not state.",
            "ref": "plan.md#f-the-website",
            "excerpt": {
              "ref": "plan.md#f-the-website",
              "heading": "F. The website",
              "paragraph": "New folder `website/`. Base URL `https://spartanlaboratories.github.io/WebTools/`. Each file is adapted from MyGameTools; fetch the originals into place first:",
              "hash": "d4ad4bb10cb0"
            }
          },
          {
            "text": "`.github/workflows/pages.yml` runs the root Dokka task, assembles `website/`, `docs/brand/` and `build/dokka/html` as `api/` into `_site`, and deploys it on each push to `master`.",
            "ref": "plan.md#g-workflows",
            "excerpt": {
              "ref": "plan.md#g-workflows",
              "heading": "G. Workflows",
              "paragraph": "`.github/workflows/ci.yml` (new) - exactly:",
              "hash": "77df4b8fce58"
            }
          },
          {
            "text": "The workflow fails with an `::error::` annotation if `build/dokka/html/index.html` is missing.",
            "ref": "plan.md#5-design",
            "excerpt": {
              "ref": "plan.md#5-design",
              "heading": "5. Design",
              "paragraph": "Responsibility. Turn the KDoc of the five published modules into (a) the javadoc jar each module publishes, (b) one aggregated HTML reference, gated in CI and served at `/api/` beside a hand-written landing page, and (c) the release preparation of `webtools-http` and `webtools-feeds`.",
              "hash": "a6a2304f0e51"
            }
          },
          {
            "text": "`.gitignore` keeps local preview copies of the reference and the brand assets under `website/` out of the repository.",
            "ref": "plan.md#h-repository-hygiene",
            "excerpt": {
              "ref": "plan.md#h-repository-hygiene",
              "heading": "H. Repository hygiene",
              "paragraph": "`.gitignore` - append:",
              "hash": "650e5755c3b2"
            }
          }
        ],
        "steps": [
          {
            "id": "stage-website",
            "what": "Add the site, its Pages workflow and the `.gitignore` rules, once the root aggregate exists.",
            "files": [
              "website/",
              ".github/workflows/pages.yml",
              ".gitignore"
            ],
            "check": "after the first push to `master` the site and `/api/` answer 200",
            "link": "plan.md#f-the-website"
          }
        ],
        "tests": [
          "Level 1 gating `RootScriptsAgreementGatingTest`: `pages.yml` runs the Dokka publication task",
          "Level 5 user acceptance after the landing: the site renders with the logo and theme in both modes, and `/api/` lists five modules and the fixtures' package"
        ],
        "risks": [
          "`pages.yml` is first exercised on `master`: a failure leaves the site undeployed but breaks no build, and `workflow_dispatch` re-runs it.",
          "The site's displayed versions are written by hand and go stale if a release forgets them; a check is a follow-up."
        ]
      }
    },
    {
      "id": "brand-assets",
      "group": "Documents",
      "name": "Brand assets and stylesheet",
      "system": "gradle-build",
      "where": "`docs/brand/`, `docs/dokka/` (new)",
      "gap": {
        "kind": "missing",
        "summary": "The repository holds nothing to brand the site with. Neither of the two folders the issue names exists, and no file in the tree is named for a logo.",
        "evidence": [
          "`docs/brand/`, `docs/dokka/` — not in the tree at `5ae9734`",
          "MyGameTools `docs/dokka/styles` — the stylesheet the issue names to adapt"
        ],
        "source": "issue #99, Proposed change"
      },
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "docs/brand/spartan-laboratories-logo.png",
          "docs/dokka/styles/dokka-styles.css"
        ],
        "statements": [
          {
            "text": "`docs/brand/spartan-laboratories-logo.png` was copied byte for byte from MyGameTools.",
            "state": "as-planned"
          },
          {
            "text": "`docs/dokka/styles/dokka-styles.css` started from MyGameTools' sheet, its brand tokens renamed to `--sl-*` and set to navy and bronze, its parent-company credit removed.",
            "state": "as-planned"
          },
          {
            "text": "Both are applied at the root and, through `webtools.published-library`, in each module's Dokka configuration.",
            "state": "as-planned"
          }
        ]
      },
      "options": [
        {
          "id": "logo-and-sheet",
          "name": "The Spartan Laboratories logo in `docs/brand/` and a stylesheet adapted from MyGameTools' in `docs/dokka/styles/`",
          "verdict": "accepted",
          "why": "The site and the Dokka theme reference the one logo file, and the sheet keeps MyGameTools' structure in navy and bronze.",
          "source": "plan.md §1 R9; §6 E",
          "link": "plan.md#e-branding"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "Two theme files that the build and the site read; no code.",
        "statements": [
          {
            "text": "`docs/brand/spartan-laboratories-logo.png` is copied byte for byte from MyGameTools.",
            "ref": "plan.md#e-branding",
            "excerpt": {
              "ref": "plan.md#e-branding",
              "heading": "E. Branding",
              "paragraph": "`docs/brand/spartan-laboratories-logo.png` - copied byte for byte from MyGameTools (324,247 bytes at the baseline):",
              "hash": "6508ce2f15c0"
            }
          },
          {
            "text": "`docs/dokka/styles/dokka-styles.css` starts from MyGameTools' sheet, its brand tokens renamed and set to navy and bronze, its parent-company credit removed.",
            "ref": "plan.md#e-branding",
            "excerpt": {
              "ref": "plan.md#e-branding",
              "heading": "E. Branding",
              "paragraph": "`docs/brand/spartan-laboratories-logo.png` - copied byte for byte from MyGameTools (324,247 bytes at the baseline):",
              "hash": "6508ce2f15c0"
            }
          },
          {
            "text": "Both are applied at the root and in each module's Dokka configuration.",
            "ref": "plan.md#1-requirements",
            "excerpt": {
              "ref": "plan.md#1-requirements",
              "heading": "1. Requirements",
              "paragraph": "The issue text names only `webtools-http`; this work prepares `webtools-feeds` the same way (R10) because the two modules release together.",
              "hash": "0f8044e8743a"
            }
          }
        ],
        "steps": [
          {
            "id": "branding",
            "what": "Add the logo and the adapted stylesheet.",
            "files": [
              "docs/brand/spartan-laboratories-logo.png",
              "docs/dokka/styles/dokka-styles.css"
            ],
            "check": "the stylesheet and the logo are in the aggregate and in each module's own publication",
            "link": "plan.md#e-branding"
          }
        ],
        "tests": [
          "Level 3 integration `DokkaConventionIntegrationTest`: the generated reference holds the stylesheet and the logo"
        ],
        "risks": [
          "The two theme files are required inputs of every Dokka task: moving or renaming either fails the build, by design."
        ]
      }
    },
    {
      "id": "readme",
      "group": "Documents",
      "name": "README",
      "system": "gradle-build",
      "where": "`README.md`",
      "gap": {
        "kind": "debt",
        "summary": "`README.md` still says `webtools-http` and `webtools-feeds` are not released. Its component table, its Install block and two sections carry \"not yet released\" or \"not yet on Maven Central\" wording.",
        "evidence": [
          "`README.md:11-12` — the component table's rows for both modules",
          "`README.md:26-36` — the Install block's comments",
          "`README.md:44`, `README.md:47` — \"not on Maven Central yet\""
        ],
        "source": "plan.md §8",
        "link": "plan.md#8-documentation-impact"
      },
      "options": [
        {
          "id": "readme-delta",
          "name": "The README changes of the plan's documentation section, in the last commit",
          "verdict": "accepted",
          "why": "The release wording, the reference links and the Dokka command belong to the change that makes them true.",
          "source": "plan.md §8",
          "link": "plan.md#8-documentation-impact"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "Documentation of what the change adds and of the release it prepares.",
        "statements": [
          {
            "text": "The top of the README gains the site and reference links, and its build section the Dokka command with a short paragraph on the reference.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings touched:",
              "hash": "4cc867ad1d1c"
            }
          },
          {
            "text": "Every \"not yet on Maven Central\" and \"not yet released\" line for `webtools-http` and `webtools-feeds` comes out, and the Install block names `webtools-udp:2.0.1`.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings touched:",
              "hash": "4cc867ad1d1c"
            }
          },
          {
            "text": "The time `README.md:793` gives for the fixtures-publication check becomes the one measured on its first warm run, saying a cold run is longer.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings touched:",
              "hash": "4cc867ad1d1c"
            }
          }
        ],
        "steps": [
          {
            "id": "readme-last",
            "what": "Write the README changes last, with the CHANGELOG sections.",
            "files": [
              "README.md"
            ],
            "check": "`grep -in \"not yet\" README.md` matches nothing about these modules",
            "link": "plan.md#8-documentation-impact"
          }
        ],
        "tests": [],
        "risks": [
          "The README and the site name `webtools-udp:2.0.1` and the `1.0.0` modules before they resolve; they resolve once the release completes after the landing."
        ]
      },
      "asBuilt": {
        "state": "deviated",
        "files": [
          "README.md"
        ],
        "statements": [
          {
            "text": "The top of the README gained the site and reference links, and its build section the Dokka command with a short paragraph on the reference.",
            "state": "as-planned"
          },
          {
            "text": "The \"not yet on Maven Central\" and \"not yet released\" lines for `webtools-http` and `webtools-feeds` came out and the Install block names `webtools-udp:2.0.1`; the Install note and the Java floor paragraph also say the floor starts with `webtools-udp` 2.0.1 and that the published builds of the two archived modules need JDK 23 or newer.",
            "state": "amended",
            "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds",
            "excerpt": {
              "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds",
              "heading": "D7. `README.md`, the site and the CHANGELOG state what Maven Central holds",
              "paragraph": "*Plan: sections 6 F and 8.* The plan took the lettered versions in the build files for published ones. Maven Central holds `webtools-udp` up to `2.0.0`, and `webtools-scraping` and `webtools-browser` at `1.0.0` only; `2.0.0a` and `1.0.0a` were never published. So the site's Modules table names `1.0.0` for those two modules where section 6 F says `1.0.0a`; the README's Install note and Java floor paragraph say the floor starts with `webtools-udp` 2.0.1 and that the two archived modules' published builds need JDK 23 or newer; and the CHANGELOG's `[2.0.1]` section lists what it carries from …",
              "hash": "4ce70d4a1ef0"
            }
          },
          {
            "text": "The README kept \"about a minute\" for the fixtures-publication check, which measured 52 to 60 s on a warm run, instead of a measured time.",
            "state": "deviated",
            "ref": "final-implementation.md#d8-the-readme-keeps-about-a-minute-for-the-fixtures-publication-check",
            "excerpt": {
              "ref": "final-implementation.md#d8-the-readme-keeps-about-a-minute-for-the-fixtures-publication-check",
              "heading": "D8. The README keeps \"about a minute\" for the fixtures-publication check",
              "paragraph": "*Plan: section 8, `README.md` item 7.* The sentence stays instead of being replaced with a measured time: the check measured 52 to 60 s warm, so it is still true.",
              "hash": "01834f69116e"
            }
          }
        ],
        "changes": [
          {
            "what": "The Install note and the Java floor paragraph say the Java 11 floor starts with `webtools-udp` 2.0.1, and that the published builds of `webtools-scraping` and `webtools-browser` need JDK 23 or newer.",
            "why": "The plan took the lettered versions in the build files for published ones. Maven Central holds `webtools-udp` up to `2.0.0` and the two archived modules at `1.0.0` only.",
            "when": "Document",
            "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds"
          },
          {
            "what": "The sentence \"about a minute\" stays, where the plan replaced it with the time measured on the check's first warm run.",
            "why": "The check measured 52 to 60 s warm, so the sentence is still true.",
            "when": "Document",
            "ref": "final-implementation.md#d8-the-readme-keeps-about-a-minute-for-the-fixtures-publication-check"
          }
        ]
      }
    },
    {
      "id": "pages-enabled",
      "group": "Release",
      "name": "GitHub Pages enabled",
      "system": "gradle-build",
      "where": "repository settings",
      "gap": {
        "kind": "missing",
        "summary": "GitHub Pages is not enabled for the repository. The issue requires it on, with GitHub Actions as its source.",
        "evidence": [
          "the repository's Pages API — 404 Not Found, and `has_pages` false, read 2026-11-08"
        ],
        "source": "issue #99, Proposed change"
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "GitHub Pages was enabled with `build_type` `workflow`, and the site's first deployment, on the merge's push, succeeded.",
            "state": "as-planned"
          },
          {
            "text": "It was done once, before the merge, as a repository setting outside the change set.",
            "state": "as-planned"
          }
        ]
      },
      "options": [
        {
          "id": "workflow-source",
          "name": "Enabled once with `build_type` `workflow`, before the merge",
          "verdict": "accepted",
          "why": "R12 requires GitHub Actions as the source, and the deploy job fails without it.",
          "source": "plan.md §1 R12; §11",
          "link": "plan.md#11-version-control"
        },
        {
          "id": "legacy-source",
          "name": "`build_type` `legacy`",
          "verdict": "declined",
          "why": "`pages.yml` deploys through GitHub Actions, which is the endpoint's `workflow` value.",
          "source": "plan.md §2",
          "link": "plan.md#2-current-state"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "A repository setting, made once, outside the change set.",
        "statements": [
          {
            "text": "GitHub Pages is enabled through `POST /repos/SpartanLaboratories/WebTools/pages` with `build_type` `workflow`.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-99-dokka-api-docs` (exists, cut from `master` at `5ae9734`). · Pre-existing working-tree changes: the only untracked path is `docs/plans/99-dokka-api-docs/`. This plan is committed in stage 1 with the work it plans; the generated board files beside it are not part of these commits. No IDE or tool files are committed. · One-time step outside the change set, before the merge: enable Pages with GitHub Actions as the source, once: `echo '{\"build_type\":\"workflow\"}' | gh api -X POST repos/SpartanLaboratories/WebTools/pages --input -` (`POST …",
              "hash": "82e465690071"
            }
          },
          {
            "text": "It is done once, before the merge, as a repository setting outside the change set.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-99-dokka-api-docs` (exists, cut from `master` at `5ae9734`). · Pre-existing working-tree changes: the only untracked path is `docs/plans/99-dokka-api-docs/`. This plan is committed in stage 1 with the work it plans; the generated board files beside it are not part of these commits. No IDE or tool files are committed. · One-time step outside the change set, before the merge: enable Pages with GitHub Actions as the source, once: `echo '{\"build_type\":\"workflow\"}' | gh api -X POST repos/SpartanLaboratories/WebTools/pages --input -` (`POST …",
              "hash": "82e465690071"
            }
          }
        ],
        "steps": [
          {
            "id": "enable-pages",
            "what": "Enable Pages and read the setting back.",
            "files": [],
            "check": "`gh api repos/SpartanLaboratories/WebTools/pages --jq .build_type` prints `workflow`",
            "link": "plan.md#11-version-control"
          }
        ],
        "tests": [],
        "risks": [
          "The Pages source must be set to GitHub Actions first, or the deploy job of `pages.yml` fails."
        ]
      }
    },
    {
      "id": "changelog-section",
      "group": "Release",
      "name": "CHANGELOG 1.0.0 section",
      "system": "gradle-build",
      "where": "`CHANGELOG.md`",
      "gap": {
        "kind": "missing",
        "summary": "`CHANGELOG.md` has no `## [1.0.0]` section. The `webtools-http` 1.0.0 entries sit under `## [Unreleased]`, each marked unreleased.",
        "evidence": [
          "`CHANGELOG.md:8` — `## [Unreleased]`, the file's only `##` heading",
          "`CHANGELOG.md:12, 18, 28, 51` — the `webtools-http` entries marked \"unreleased, `1.0.0`\"",
          "`CHANGELOG.md:73` — `webtools-feeds` is marked the same way; the issue names only `webtools-http`"
        ],
        "source": "issue #99, Problem"
      },
      "asBuilt": {
        "state": "amended",
        "files": [
          "CHANGELOG.md"
        ],
        "statements": [
          {
            "text": "`## [Unreleased]` stands as an empty heading, and its content moved into `## [1.0.0] - 2026-11-08` with the \"(unreleased, `1.0.0`)\" markers dropped.",
            "state": "as-planned"
          },
          {
            "text": "`## [2.0.1] - 2026-11-08` records the reference, the site, the CI job and `webtools-udp`'s populated javadoc jar, and also the two fixes it carries from the never-published `2.0.0a` (#38, #47).",
            "state": "amended",
            "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds",
            "excerpt": {
              "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds",
              "heading": "D7. `README.md`, the site and the CHANGELOG state what Maven Central holds",
              "paragraph": "*Plan: sections 6 F and 8.* The plan took the lettered versions in the build files for published ones. Maven Central holds `webtools-udp` up to `2.0.0`, and `webtools-scraping` and `webtools-browser` at `1.0.0` only; `2.0.0a` and `1.0.0a` were never published. So the site's Modules table names `1.0.0` for those two modules where section 6 F says `1.0.0a`; the README's Install note and Java floor paragraph say the floor starts with `webtools-udp` 2.0.1 and that the two archived modules' published builds need JDK 23 or newer; and the CHANGELOG's `[2.0.1]` section lists what it carries from …",
              "hash": "4ce70d4a1ef0"
            }
          },
          {
            "text": "No entry names `webtools-scraping` or `webtools-browser`.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "The `[2.0.1]` section lists what it carries from `2.0.0a`, the malformed-handshake refusal (#38) and the Java 11 floor (#47), which the plan's section leaves out.",
            "why": "`2.0.0a` was never published, so `2.0.1` is the first release of those two fixes on Maven Central.",
            "when": "Document",
            "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds"
          }
        ]
      },
      "options": [
        {
          "id": "both-modules",
          "name": "The whole of `[Unreleased]` moves into `## [1.0.0]`, for `webtools-http` and `webtools-feeds` together, beside a new `## [2.0.1]`",
          "verdict": "accepted",
          "why": "All of the `[Unreleased]` content belongs to these two modules, which release together.",
          "source": "plan.md §1 R10; §8",
          "link": "plan.md#8-documentation-impact"
        },
        {
          "id": "http-only",
          "name": "`webtools-http` only, as the issue text names",
          "verdict": "declined",
          "why": "The two modules release together, so `webtools-feeds` is prepared the same way.",
          "source": "plan.md §1",
          "link": "plan.md#1-requirements"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "Release preparation in the changelog; no module's content changes.",
        "statements": [
          {
            "text": "`## [Unreleased]` stays as an empty heading, and its content moves into `## [1.0.0] - <landing date>` with the \"(unreleased, `1.0.0`)\" markers dropped.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings touched:",
              "hash": "4cc867ad1d1c"
            }
          },
          {
            "text": "`## [2.0.1] - <landing date>` records the reference, the site, the CI job and `webtools-udp`'s populated javadoc jar.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings touched:",
              "hash": "4cc867ad1d1c"
            }
          },
          {
            "text": "No entry names `webtools-scraping` or `webtools-browser`.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings touched:",
              "hash": "4cc867ad1d1c"
            }
          }
        ],
        "steps": [
          {
            "id": "changelog-last",
            "what": "Write the two version sections last, when the landing date is known.",
            "files": [
              "CHANGELOG.md"
            ],
            "check": "`grep -in \"unreleased\"` over `CHANGELOG.md` and the two build files matches only the empty `## [Unreleased]` heading",
            "link": "plan.md#8-documentation-impact"
          }
        ],
        "tests": [],
        "risks": []
      }
    },
    {
      "id": "http-unreleased-note",
      "group": "Release",
      "name": "`webtools-http` unreleased note",
      "system": "gradle-build",
      "where": "`webtools-http/build.gradle.kts`",
      "gap": {
        "kind": "debt",
        "summary": "`webtools-http/build.gradle.kts` still says 1.0.0 is unreleased and not a release candidate. The note says to remove it when 1.0.0 is released.",
        "evidence": [
          "`webtools-http/build.gradle.kts:7-9` — \"it is UNRELEASED\", \"not a release candidate\", \"Remove this note when 1.0.0 is released\"",
          "`webtools-feeds/build.gradle.kts:7` — carries the same note; the issue names only `webtools-http`"
        ],
        "source": "issue #99, Problem"
      },
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "webtools-http/build.gradle.kts",
          "webtools-feeds/build.gradle.kts"
        ],
        "statements": [
          {
            "text": "The three-line \"UNRELEASED\" note was removed from `webtools-http/build.gradle.kts` and `webtools-feeds/build.gradle.kts`.",
            "state": "as-planned"
          },
          {
            "text": "Neither module's version moved: both stay at the `1.0.0` they inherit from the root.",
            "state": "as-planned"
          }
        ]
      },
      "options": [
        {
          "id": "both-notes",
          "name": "Remove the note from `webtools-http` and `webtools-feeds`",
          "verdict": "accepted",
          "why": "Both modules carry the same note and are prepared for release together.",
          "source": "plan.md §1 R10; §6 C",
          "link": "plan.md#c-module-build-files"
        },
        {
          "id": "http-note-only",
          "name": "Remove it from `webtools-http` only, as the issue text names",
          "verdict": "declined",
          "why": "The two modules release together, so `webtools-feeds` is prepared the same way.",
          "source": "plan.md §1",
          "link": "plan.md#1-requirements"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "Two build-file comments; neither module's version moves.",
        "statements": [
          {
            "text": "The three-line \"UNRELEASED\" note comes out of `webtools-http/build.gradle.kts:7-9` and `webtools-feeds/build.gradle.kts:7-9`.",
            "ref": "plan.md#c-module-build-files",
            "excerpt": {
              "ref": "plan.md#c-module-build-files",
              "heading": "C. Module build files",
              "paragraph": "`webtools-http/build.gradle.kts`",
              "hash": "92cc6344a6e9"
            }
          },
          {
            "text": "Neither module's version moves: both stay at the `1.0.0` they inherit from the root.",
            "ref": "plan.md#1-requirements",
            "excerpt": {
              "ref": "plan.md#1-requirements",
              "heading": "1. Requirements",
              "paragraph": "The issue text names only `webtools-http`; this work prepares `webtools-feeds` the same way (R10) because the two modules release together.",
              "hash": "0f8044e8743a"
            }
          }
        ],
        "steps": [
          {
            "id": "remove-notes",
            "what": "Remove the two notes in the Dokka build commit.",
            "files": [
              "webtools-http/build.gradle.kts",
              "webtools-feeds/build.gradle.kts"
            ],
            "check": "`grep -in \"unreleased\"` over the two build files matches nothing",
            "link": "plan.md#c-module-build-files"
          }
        ],
        "tests": [],
        "risks": []
      }
    },
    {
      "id": "udp-version",
      "group": "Release",
      "name": "`webtools-udp` 2.0.1",
      "system": "gradle-build",
      "where": "`webtools-udp/build.gradle.kts`",
      "gap": {
        "kind": "missing",
        "summary": "`webtools-udp`'s published artifact changes while its version does not. Its javadoc jar gains the Dokka reference, and the build file still says `2.0.0a`.",
        "evidence": [
          "`webtools-udp/build.gradle.kts:116` — `version = \"2.0.0a\"`"
        ],
        "source": "plan.md §1 R11; §2",
        "link": "plan.md#1-requirements"
      },
      "options": [
        {
          "id": "two-zero-one",
          "name": "`2.0.1`",
          "verdict": "accepted",
          "why": "Its javadoc jar is the artifact that changes, and this is the only version move.",
          "source": "plan.md §1 R11",
          "link": "plan.md#1-requirements"
        },
        {
          "id": "letter-suffix",
          "name": "Another letter suffix after `2.0.0a`",
          "verdict": "declined",
          "why": "A letter suffix is for bug fixes only.",
          "source": "plan.md §1 R11",
          "link": "plan.md#1-requirements"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "The change's one version move, in a commit of its own.",
        "statements": [
          {
            "text": "`webtools-udp` moves from `2.0.0a` to `2.0.1` in a `build:` commit of its own after the code, and nothing else in the file changes.",
            "ref": "plan.md#c-module-build-files",
            "excerpt": {
              "ref": "plan.md#c-module-build-files",
              "heading": "C. Module build files",
              "paragraph": "`webtools-http/build.gradle.kts`",
              "hash": "92cc6344a6e9"
            }
          },
          {
            "text": "No other `version =` line changes.",
            "ref": "plan.md#1-requirements",
            "excerpt": {
              "ref": "plan.md#1-requirements",
              "heading": "1. Requirements",
              "paragraph": "The issue text names only `webtools-http`; this work prepares `webtools-feeds` the same way (R10) because the two modules release together.",
              "hash": "0f8044e8743a"
            }
          }
        ],
        "steps": [
          {
            "id": "bump-udp",
            "what": "Set the version once the code is complete.",
            "files": [
              "webtools-udp/build.gradle.kts"
            ],
            "check": "`version = \"2.0.1\"` in the build file and no other `version =` line changed",
            "link": "plan.md#c-module-build-files"
          }
        ],
        "tests": [],
        "risks": []
      },
      "asBuilt": {
        "state": "amended",
        "files": [
          "webtools-udp/build.gradle.kts"
        ],
        "statements": [
          {
            "text": "`webtools-udp` moved from `2.0.0a` to `2.0.1` in the `build:` commit of its own, `2c634f4`, after the code; a two-line comment above the version line says what `2.0.1` carries.",
            "state": "amended"
          },
          {
            "text": "No other `version =` line changed.",
            "state": "as-planned"
          }
        ],
        "changes": [
          {
            "what": "A two-line comment above the version line says what `2.0.1` carries, where the plan changed the version line alone.",
            "why": "The comments above that line record what each version carries, and the new version takes its entry.",
            "when": "Code landing",
            "ref": "2c634f4"
          }
        ]
      }
    },
    {
      "id": "archived-modules-release",
      "group": "Left for later",
      "name": "A Maven Central release of `webtools-scraping` and `webtools-browser`",
      "system": "gradle-build",
      "where": "not built",
      "gap": {
        "kind": "missing",
        "summary": "The javadoc jars of the two archived modules on Maven Central keep their placeholder. The issue lists both among the published modules, and the systems registry records both as archived.",
        "evidence": [
          "`docs/dashboards/systems.json` — `webtools-scraping` and `webtools-browser` with `\"status\": \"archived\"`",
          "`consequences.md` §1 — Maven Central's `1.0.0a` keeps its placeholder javadoc jar"
        ],
        "source": "plan.md §1 R6; consequences.md §1",
        "link": "plan.md#1-requirements"
      },
      "options": [
        {
          "id": "dokka-no-move",
          "name": "Dokka, no version move",
          "verdict": "accepted",
          "why": "They appear at `/api/` like any published module, with nothing republished.",
          "source": "plan.md §1 R6",
          "link": "plan.md#1-requirements"
        },
        {
          "id": "dokka-and-release",
          "name": "Dokka and a release",
          "verdict": "declined",
          "why": "A later release of the two modules carries the reference to Maven Central; this change moves neither version.",
          "source": "plan.md §4, §12",
          "link": "plan.md#12-sequencing-and-follow-ups"
        },
        {
          "id": "out-of-scope",
          "name": "Out of scope",
          "verdict": "declined",
          "why": "The issue lists both among the published modules, and the reference covers them like the others.",
          "source": "plan.md §1 R1, R6",
          "link": "plan.md#1-requirements"
        }
      ],
      "verdict": {
        "kind": "leave",
        "defense": "The two archived modules get Dokka through the conventions; a release of them is left for later.",
        "statements": [
          {
            "text": "`webtools-scraping` and `webtools-browser` stay at `1.0.0a`, with no re-release and no CHANGELOG entry of their own.",
            "ref": "plan.md#1-requirements",
            "excerpt": {
              "ref": "plan.md#1-requirements",
              "heading": "1. Requirements",
              "paragraph": "The issue text names only `webtools-http`; this work prepares `webtools-feeds` the same way (R10) because the two modules release together.",
              "hash": "0f8044e8743a"
            }
          },
          {
            "text": "A later release of the two modules makes their javadoc jars on Maven Central carry the reference.",
            "ref": "plan.md#12-sequencing-and-follow-ups",
            "excerpt": {
              "ref": "plan.md#12-sequencing-and-follow-ups",
              "heading": "12. Sequencing and follow-ups",
              "paragraph": "Order: commit 1 first (the KDoc pass is what lets the Dokka task pass); commit 2 after it; commit 3 any time after 2; commit 4 after 2 (it needs the root aggregate); commit 5 once the code is complete; commit 6 last, once the landing date is known. The warning-free `./gradlew dokkaGeneratePublicationHtml` run, the `apiCheck` run and the build-logic runs are made on the working tree with every stage applied, before it is split into the commits. Pages enablement (section 11) happens before the merge.",
              "hash": "ee617ad507e2"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`webtools-scraping` and `webtools-browser` stayed at `1.0.0a` in their build files, with no re-release and no CHANGELOG entry of their own; each gained one KDoc comment.",
            "state": "as-planned"
          },
          {
            "text": "Their later release stays a follow-up, so their `1.0.0` on Maven Central keeps its placeholder javadoc jar.",
            "state": "as-planned",
            "ref": "consequences.md#7-follow-ups",
            "excerpt": {
              "ref": "consequences.md#7-follow-ups",
              "heading": "7. Follow-ups",
              "paragraph": "All open. The first five are `plan.md`, section 12.",
              "hash": "ac1dd41a052d"
            }
          }
        ]
      }
    },
    {
      "id": "ci-build-test-jobs",
      "group": "Left for later",
      "name": "CI build and test jobs",
      "system": "gradle-build",
      "where": "not built",
      "gap": {
        "kind": "missing",
        "summary": "The repository has no CI job that builds it or runs its tests. This change adds only the `docs` job.",
        "evidence": [
          "`.github/` — not in the tree at `5ae9734`",
          "#58 — the open issue that asks for CI"
        ],
        "source": "plan.md §1 R7; §4",
        "link": "plan.md#4-non-goals"
      },
      "options": [
        {
          "id": "left-to-58",
          "name": "Left to #58, which stays open",
          "verdict": "accepted",
          "why": "#58's build and test jobs extend the `ci.yml` this change adds instead of creating it.",
          "source": "consequences.md §5",
          "link": "consequences.md#5-neighbouring-issues"
        }
      ],
      "verdict": {
        "kind": "leave",
        "defense": "Out of this issue's scope; #58 holds it.",
        "statements": [
          {
            "text": "CI build and test jobs, `CONTRIBUTING.md`, tags and a release workflow stay with #58.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "CI build and test jobs, `CONTRIBUTING.md`, tags and a release workflow: issue #58 (stays open). · Publishing itself. A Maven Central re-release of `webtools-scraping` or `webtools-browser`: their javadoc jars on Central keep the placeholder until a later release of those modules. · Dokka for the `*-commoncheck` modules. · Rendering `protected` members: Dokka's `documentedVisibilities` stays at its public-only default. Documenting `internal` declarations. · Any signature or behaviour change in the five modules: the KDoc pass edits comments only; the ABI dumps (`api/*.api`) stay identical. · …",
              "hash": "9c9990c06958"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`ci.yml` holds the one `docs` job; the build and test jobs, `CONTRIBUTING.md`, tags and a release workflow stayed with #58, still open.",
            "state": "as-planned",
            "ref": "consequences.md#5-neighbouring-issues",
            "excerpt": {
              "ref": "consequences.md#5-neighbouring-issues",
              "heading": "5. Neighbouring issues",
              "paragraph": "#58 (repository practices): `ci.yml` now exists with the `docs` job, so #58's build and test jobs extend that file instead of creating it. Its `CHANGELOG.md` request is already met; its `CONTRIBUTING.md`, tags, release workflow and foojay bump are untouched. #58 stays open. · #55 and #69 (closed): the build notes that held `webtools-http` and `webtools-feeds` back until the stack was complete are removed.",
              "hash": "13292e1694c7"
            }
          }
        ]
      }
    }
  ],
  "impact": {
    "basis": "as-built",
    "summary": "A build and documentation change to the published modules: each javadoc jar carries its Dokka reference, one aggregated reference is served at `/api/` beside a landing page on GitHub Pages, and `webtools-http` and `webtools-feeds` are prepared for their `1.0.0` release, not released yet. No published module changes its API or runtime dependencies; `webtools-udp` 2.0.1 is also the first release of the two fixes `2.0.0a` held. Every future change to a published module is gated on KDoc.",
    "none": null,
    "release": {
      "bump": "feat",
      "version": "webtools-udp 2.0.1",
      "note": "the only version move, as `webtools-udp`'s javadoc jar is the artifact that changes. None of `webtools-udp` 2.0.1, `webtools-http` 1.0.0 or `webtools-feeds` 1.0.0 is released yet: the publish has not run and waits on the maintainer. `webtools-scraping` and `webtools-browser` stay at `1.0.0a` in the build files, never published; Maven Central holds `1.0.0`."
    },
    "downstream": [
      {
        "who": "`webtools-udp`",
        "kind": "artifact",
        "system": "gradle-build",
        "effect": "behaviour",
        "summary": "The javadoc jar carries the Dokka reference in place of an empty placeholder, at `2.0.1`. `2.0.1` is also the first release of what the never-published `2.0.0a` holds: the malformed-handshake refusal (#38) and the Java 11 floor (#47), so three malformed handshake shapes change on the wire against Maven Central's `2.0.0`. No public API change. Not released yet: the publish has not run and waits on the maintainer.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "`webtools-http`, `webtools-http-jvm`",
        "kind": "artifact",
        "system": "gradle-build",
        "effect": "additive",
        "summary": "The javadoc jars carry the Dokka reference, the published test fixtures included. KDoc comments only in the source and the ABI dump is identical. `1.0.0` is unchanged and the \"UNRELEASED\" build note is gone. Not released yet: the publish has not run and waits on the maintainer.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "`webtools-feeds`, `webtools-feeds-jvm`",
        "kind": "artifact",
        "system": "gradle-build",
        "effect": "additive",
        "summary": "The javadoc jars carry the Dokka reference, with KDoc comments only in the source and an identical ABI dump. It is prepared with `webtools-http`, as the two release together, at an unchanged `1.0.0`, and the \"UNRELEASED\" build note is gone. Not released yet: the publish has not run and waits on the maintainer.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "`webtools-scraping`, `webtools-browser`",
        "kind": "artifact",
        "system": "gradle-build",
        "effect": "none",
        "summary": "They get Dokka and appear at `/api/`, and each gains one KDoc comment. Nothing is republished: Maven Central keeps `1.0.0`, with its Java 23 bytecode and placeholder javadoc jar. `1.0.0a` in the build files is unchanged and was never published.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "The project site",
        "kind": "artifact",
        "system": "gradle-build",
        "effect": "additive",
        "summary": "A landing page and the API reference at `/api/`, on GitHub Pages at `https://spartanlaboratories.github.io/WebTools/`, deployed from `master` by the repository's first workflows. The first deployment, on the merge's push, succeeded. Its hand-written Install snippets name versions that do not resolve until the releases.",
        "link": "consequences.md#repository"
      },
      {
        "who": "Consumers of the published modules",
        "kind": "consumer",
        "system": null,
        "effect": "behaviour",
        "summary": "No published module changes its API or runtime dependencies. A consumer moving from Maven Central's `webtools-udp` 2.0.0 to 2.0.1 gets the populated javadoc jar and the two fixes of `2.0.0a`, listed in the CHANGELOG's `[2.0.1]` section; three malformed handshake shapes change on the wire.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Anyone changing a published module",
        "kind": "repository",
        "system": "gradle-build",
        "effect": "behaviour",
        "summary": "A broken KDoc link or an undocumented public declaration now fails each module's Dokka tasks, the javadoc jar and so any publish, the CI job and, for `webtools-http`, `:build-logic:fixturesPublicationCheck`, which makes it fail `./gradlew build` and `check` as well. `assemble` and the module test tasks do not run Dokka.",
        "link": "consequences.md#build"
      }
    ],
    "adaptation": [
      {
        "who": "Consumers of the published modules",
        "system": null,
        "need": "none",
        "summary": "Nothing to change.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Anyone changing a published module",
        "system": "gradle-build",
        "need": "required",
        "summary": "Write KDoc on every new public declaration and keep every KDoc link resolvable, or the Dokka tasks fail.",
        "link": "consequences.md#build"
      },
      {
        "who": "Whoever maintains the build and the site",
        "system": "gradle-build",
        "need": "required",
        "summary": "Keep the Kotlin and Dokka versions of `settings.gradle.kts` and `build-logic/build.gradle.kts` in agreement, keep the two theme files where they are, and bring the five hand-written version numbers in `website/` up at each release.",
        "link": "consequences.md#build"
      },
      {
        "who": "The repository's GitHub settings",
        "system": "gradle-build",
        "need": "required",
        "summary": "GitHub Pages is enabled with GitHub Actions as its source, a repository setting outside the change set, made once before the merge.",
        "link": "consequences.md#repository"
      }
    ],
    "api": [],
    "conversion": [],
    "source": "consequences.md; final-implementation.md; plan.md §1 and §10",
    "link": "consequences.md",
    "changes": [
      {
        "what": "`webtools-udp` 2.0.1 is also the first release of the two fixes of the never-published `2.0.0a`, so three malformed handshake shapes change on the wire against Maven Central's `2.0.0`, where the plan recorded a populated javadoc jar alone.",
        "why": "The plan took the lettered versions in the build files for published ones. Maven Central holds `webtools-udp` up to `2.0.0`.",
        "when": "Document",
        "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds",
        "entry": "downstream.0",
        "system": "gradle-build"
      },
      {
        "what": "Maven Central holds `webtools-scraping` and `webtools-browser` at `1.0.0`, with its Java 23 bytecode, not the `1.0.0a` the plan named; `1.0.0a` was never published.",
        "why": "The plan took the lettered versions in the build files for published ones.",
        "when": "Document",
        "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds",
        "entry": "downstream.3",
        "system": "gradle-build"
      },
      {
        "what": "A consumer moving from Maven Central's `webtools-udp` 2.0.0 sees the two fixes as well as the javadoc jar, so the entry's effect is a behaviour change, not none.",
        "why": "`2.0.1` is the first published version holding what `2.0.0a` carries.",
        "when": "Document",
        "ref": "final-implementation.md#d7-readmemd-the-site-and-the-changelog-state-what-maven-central-holds",
        "entry": "downstream.5",
        "system": null
      }
    ]
  },
  "tests": {
    "updated": "2026-11-09T01:57:15Z",
    "added": [
      {
        "class": "DokkaConventionIntegrationTest",
        "module": "build-logic",
        "testLevel": "3",
        "path": "build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/DokkaConventionIntegrationTest.kt",
        "tests": 6,
        "stage": "dokka-build",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "DokkaAggregationEndToEndTest",
        "module": "build-logic",
        "testLevel": "4b",
        "path": "build-logic/src/test/kotlin/com/spartanlabs/testing/e2e/webtools/buildlogic/DokkaAggregationEndToEndTest.kt",
        "tests": 7,
        "stage": "dokka-build",
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "DokkaConfigurationCacheReuseNonFunctionalTest",
        "module": "build-logic",
        "testLevel": "4c",
        "path": "build-logic/src/test/kotlin/com/spartanlabs/testing/nonfunctional/webtools/buildlogic/DokkaConfigurationCacheReuseNonFunctionalTest.kt",
        "tests": 1,
        "stage": "website",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "RootScriptsAgreementGatingTest",
        "module": "build-logic",
        "testLevel": "1",
        "path": "build-logic/src/test/kotlin/com/spartanlabs/testing/gating/webtools/buildlogic/RootScriptsAgreementGatingTest.kt",
        "tests": 4,
        "stage": "dokka-build",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      }
    ],
    "amended": [
      {
        "class": "BuildLogicPluginsGatingTest",
        "module": "build-logic",
        "testLevel": "1",
        "path": "build-logic/src/test/kotlin/com/spartanlabs/testing/gating/webtools/buildlogic/BuildLogicPluginsGatingTest.kt",
        "tests": 2,
        "stage": "website",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "PublishingConventionIntegrationTest",
        "module": "build-logic",
        "testLevel": "3",
        "path": "build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/PublishingConventionIntegrationTest.kt",
        "tests": 6,
        "stage": "website",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "FixturesPublicationEndToEndTest",
        "module": "build-logic",
        "testLevel": "4b",
        "path": "build-logic/src/test/kotlin/com/spartanlabs/testing/e2e/webtools/buildlogic/FixturesPublicationEndToEndTest.kt",
        "tests": 11,
        "stage": "dokka-build",
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "FixturesCheckWiringEndToEndTest",
        "module": "build-logic",
        "testLevel": "4b",
        "path": "build-logic/src/test/kotlin/com/spartanlabs/testing/e2e/webtools/buildlogic/FixturesCheckWiringEndToEndTest.kt",
        "tests": 2,
        "stage": "website",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "CommonCheckPluginIntegrationTest",
        "module": "build-logic",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        },
        "testLevel": "3",
        "path": "build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/CommonCheckPluginIntegrationTest.kt",
        "stage": "dokka-build"
      }
    ],
    "relevant": [
      {
        "class": "JavaFloorScanComponentTest",
        "module": "build-logic",
        "tests": 18,
        "result": {
          "state": "passed",
          "passed": 18,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "RegisterLevelTasksComponentTest",
        "module": "build-logic",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "RegisterVerifyJavaFloorComponentTest",
        "module": "build-logic",
        "tests": 4,
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "VerifyJavaFloorComponentTest",
        "module": "build-logic",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "ApiValidationIntegrationTest",
        "module": "build-logic",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "JvmJavaFloorIntegrationTest",
        "module": "build-logic",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "JvmLevelSelectionIntegrationTest",
        "module": "build-logic",
        "tests": 7,
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "LevelSelectionIntegrationTest",
        "module": "build-logic",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "LifecycleTestTaskIntegrationTest",
        "module": "build-logic",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "MultiplatformJavaFloorIntegrationTest",
        "module": "build-logic",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "PublishedJvmVersionAttributeIntegrationTest",
        "module": "build-logic",
        "tests": 2,
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "WebToolsBuildJavaFloorDeterministicTest",
        "module": "build-logic",
        "tests": 3,
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "WebToolsBuildLevelsDeterministicTest",
        "module": "build-logic",
        "tests": 6,
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "IncludedBuildEndToEndTest",
        "module": "build-logic",
        "tests": 5,
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "CommonCheckConfigurationCacheReuseNonFunctionalTest",
        "module": "build-logic",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "ConfigurationCacheReuseNonFunctionalTest",
        "module": "build-logic",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      },
      {
        "class": "JvmConfigurationCacheReuseNonFunctionalTest",
        "module": "build-logic",
        "tests": 1,
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 3,
          "at": "2026-11-09T01:57:15Z"
        }
      }
    ]
  },
  "roadmap": null,
  "followUps": [
    {
      "name": "scraping-browser-release",
      "note": "a later release of `webtools-scraping` and `webtools-browser`, so their Maven Central javadoc jars carry the reference"
    },
    {
      "issue": 58,
      "title": "Repository practices: CI, CONTRIBUTING.md, CHANGELOG.md, release tags, and the foojay resolver for Gradle 9",
      "note": "CI build and test jobs, which extend the `ci.yml` this change adds; its `CHANGELOG.md` request is already met"
    },
    {
      "name": "site-version-check",
      "note": "a check that the site's displayed versions match the modules' build files"
    },
    {
      "name": "site-html-css-tests",
      "note": "tests of the site's HTML and CSS: the page and its stylesheet are checked only by the manual acceptance checks"
    },
    {
      "name": "fixtures-dokka-in-convention",
      "note": "moving the fixtures' Dokka line into the convention plugin when a second module publishes fixtures"
    },
    {
      "name": "standard-fail-on-warning",
      "note": "the organization standard's \"a broken KDoc link fails it\" holds only with `failOnWarning`, and MyGameTools does not set it: a default Dokka run with broken links succeeds"
    },
    {
      "name": "pages-permissions-scope",
      "note": "`pages.yml` grants `pages: write` and `id-token: write` at workflow level, so the `build` job inherits them: scoping them to the `deploy` job would be least privilege"
    }
  ],
  "documents": [
    {
      "label": "Plan",
      "path": "plan.md",
      "note": "requirements R1 to R13, the design, the file-by-file changes, the test plan, the risks and the six commits"
    },
    {
      "label": "Replaced plan",
      "path": "superseded/plan-2026-11-09.md",
      "note": "the plan before its re-pass after Quality Review 1, kept for the record"
    },
    {
      "label": "Consequences",
      "path": "consequences.md",
      "note": "the published artifacts, the blast radius in this repository, the neighbouring issues and the follow-ups"
    },
    {
      "label": "As built",
      "path": "final-implementation.md",
      "note": "the eight deviations from the plan: the `toString` wording, the `UdpChannel` companion wording, the gating test's class KDoc, the task the Dokka convention test's failure cases assert, the agreement gate's stricter reading, the commit grouping, what the README, site and CHANGELOG state about Maven Central, and the README's \"about a minute\""
    }
  ],
  "notes": {
    "corrections": [
      {
        "what": "`dokka-test-harness`: the Verdict's test statements and its tests follow the plan's re-pass after Quality Review 1 (`plan.md` §9), which adds `RootScriptsAgreementGatingTest` and the check-only modules' Dokka-task check. The replaced plan is in `superseded/plan-2026-11-09.md`.",
        "when": "2026-11-09"
      },
      {
        "what": "`aggregate-reference`: the plan's re-pass adds requirement R13 and its gate (`plan.md` §1, §9), so the row gains a statement for it, and its tests and its version risk follow the re-passed text: a difference between the two scripts' versions shows no symptom in the build.",
        "when": "2026-11-09"
      },
      {
        "what": "`dokka-ci`: its test follows the plan's re-pass (`plan.md` §9): a missing theme file is a third failure case beside a broken link and an undocumented public function.",
        "when": "2026-11-09"
      },
      {
        "what": "`project-site`: the plan's re-pass corrected the hero sample's range to `README.md:308-321`, which is what was built, so the difference recorded earlier against `:307-320` is no longer a deviation, and `final-implementation.md` no longer lists it.",
        "when": "2026-11-09"
      },
      {
        "what": "`dokka-test-harness`: the gate statement, the option read from the root's blocks and the gate's test follow the re-passed plan (`plan.md` §5, §6 I, §9): the gate also covers the root's `failOnWarning` and the task both workflows run, and the generated builds also read the root's `dokka` block.",
        "when": "2026-11-09"
      },
      {
        "what": "`dokka-ci` and `project-site`: their tests follow the re-passed plan (`plan.md` §9): the missing theme file is now two failure cases, a missing stylesheet and a missing logo, and the gate holds both workflows to the Dokka task's name.",
        "when": "2026-11-09"
      },
      {
        "what": "`kdoc-pass` and `dokka-test-harness`: the as-built references to `final-implementation.md` now name its numbered sections (`#2-deviations`, `#d1-…` to `#d5-…`), the headings it holds since its reconciliation.",
        "when": "2026-11-09"
      }
    ]
  },
  "links": {
    "workBoard": "../index.html",
    "repoBoard": "../../dashboards/index.html",
    "boards": {
      "gradle-build": "../../dashboards/gradle-build/index.html",
      "webtools-udp": "../../dashboards/webtools-udp/index.html",
      "webtools-http": "../../dashboards/webtools-http/index.html",
      "webtools-feeds": "../../dashboards/webtools-feeds/index.html"
    }
  },
  "issueBoards": {
    "38": "../38-handshake-empty-name/dashboard.html",
    "47": "../55-http-client-stack/47-java-23-bytecode/dashboard.html",
    "55": "../55-http-client-stack/dashboard.html",
    "69": "../55-http-client-stack/69-feeds/dashboard.html"
  }
}
);
