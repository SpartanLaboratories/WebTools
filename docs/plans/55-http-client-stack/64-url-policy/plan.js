/* plan.js: plan.json as a script, written by plan/scripts/embed.js beside the board for its live view (plan/RULES.md §1). Do not edit: edit plan.json and re-embed. */
window.dashboardLive&&window.dashboardLive("plan",
{
  "schema": "plan-board/1",
  "repo": "SpartanLaboratories/WebTools",
  "issue": 64,
  "slug": "64-url-policy",
  "folder": "docs/plans/55-http-client-stack/64-url-policy",
  "kind": "issue",
  "level": 3,
  "title": "Outbound-URL policy",
  "issueTitle": "webtools-http: outbound-URL policies `Trusted`/`Untrusted`, `TransportSettings` and the common address classifier",
  "function": "Nothing in `webtools-http` decides yet which URLs and addresses a fetch may reach: #62 built the refusal vocabulary, `RefusalReason` and `TransportFailure.RefusedByPolicy`, and nothing raises it (#55 architecture §6.3.1). This run adds the closed pair `OutboundUrlPolicy.Trusted` and `Untrusted` with their builders, `TransportSettings` with one default set per policy, `CidrBlock` and `MixedAnswers`, the `internal` common classifier that judges host text and address bytes, and the JVM resolver and socket guards, with their tests. Unit 5 of #55: one subsystem of `webtools-http`, additive, no version moved; wiring the guards into a client is unit 6's.",
  "status": "building",
  "sized": "2026-10-25T21:42:50Z",
  "parent": {
    "issue": 55,
    "board": "../dashboard.html"
  },
  "branch": "feat/issue-64-url-policy",
  "baseline": {
    "ref": "master",
    "sha": "4fcc6e0",
    "date": "2026-10-25"
  },
  "asBuilt": null,
  "systems": [
    {
      "slug": "webtools-http-url-policy",
      "name": "outbound-URL policy",
      "slot": 5,
      "top": "webtools-http",
      "status": "built",
      "page": "docs/dashboards/webtools-http/index.html"
    }
  ],
  "wip": {
    "step": "merge",
    "since": "2026-10-26T22:45:45Z",
    "updated": "2026-10-26T22:45:45Z",
    "detail": "State boards refreshed and stamped at 543bd08; the board commit, the pull request and the merge follow.",
    "steps": [
      "sizing",
      "interview",
      "research",
      "plan",
      "branch",
      "implementation",
      "qa",
      "remediation",
      "landing",
      "state-boards",
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
        "name": "TransportSettings, StrictIp, CidrBlock",
        "state": "done",
        "rows": [
          "transport-settings",
          "untrusted",
          "host-rules",
          "classifier-tests"
        ]
      },
      {
        "id": "S2",
        "name": "Trusted and Untrusted policies",
        "state": "done",
        "rows": [
          "policy-pair",
          "trusted",
          "untrusted"
        ]
      },
      {
        "id": "S3",
        "name": "classifier and tables",
        "state": "done",
        "rows": [
          "trusted",
          "untrusted",
          "address-table",
          "host-rules",
          "url-guard",
          "classifier-tests"
        ]
      },
      {
        "id": "S4",
        "name": "ABI dump (first commit point)",
        "state": "done",
        "rows": [
          "release"
        ]
      },
      {
        "id": "S5",
        "name": "JVM guards: GuardedDns, GuardedSocketFactory, markers, enforcement bundle",
        "state": "done",
        "rows": [
          "jvm-guards",
          "jvm-tests"
        ]
      },
      {
        "id": "S6",
        "name": "floor guard, check, mechanical rules, mutation checks (second commit point)",
        "state": "done",
        "rows": [
          "classifier-tests",
          "jvm-tests",
          "release"
        ]
      },
      {
        "id": "S7",
        "name": "README line and acceptance pass",
        "state": "done",
        "rows": []
      },
      {
        "id": "S8",
        "name": "WebToolsDsl marker on the six builders, canary proof, two tests, dump (third commit point)",
        "state": "done",
        "rows": [
          "policy-pair",
          "transport-settings",
          "release"
        ]
      }
    ],
    "remediation": {
      "pass": 2,
      "route": "on-the-spot",
      "steps": [
        {
          "name": "the 14 documentation items and the advisory test items: KDoc on the sibling fixtures, a GuardedSocket boundary paragraph, four line comments, the records' stale S8 wording, the host-form count reconciled; apiCheck and the level tasks re-run",
          "state": "done"
        },
        {
          "name": "reword the pre-existing wording that states each unit's sizing or names a role, in every document the commits carry (the architecture's per-unit lines included)",
          "state": "current"
        }
      ]
    },
    "open": [
      {
        "text": "One IP-literal grammar for the module, or a separate StrictIp beside Url's host parsing",
        "raised": "the plan re-pass",
        "resolved": "StrictIp is self-contained and Url's host parsing stays unchanged; an agreement test pins the two to the same literal forms"
      },
      {
        "text": "A public @DslMarker for the module's builders is left open: adopting it before the 1.0.0 release costs consumers nothing, afterwards it tightens their sources; recorded as a follow-up and known gap.",
        "raised": "plan-accepted",
        "ref": "impact.md",
        "resolved": "2026-10-26T01:50:38Z"
      },
      {
        "text": "Three dated architecture callouts proposed for §6.3.1 and §7.4 (the two extra deny rules; sockets on Proxy.NO_PROXY and newCall inherited; the transport-to-policy row carrying the range class); applied at reconciliation if approved.",
        "raised": "plan-accepted",
        "ref": "plan.md",
        "resolved": "2026-10-26T01:50:38Z"
      },
      {
        "text": "Plan §9's CidrBlockParseDeterministicTest lists `/8` and `10.0.0.0/` as shape refusals, while §7.2's parse, as written, answers the address and prefix constants for an empty part; the code stays as §7.2 and the two rows assert only a refusal until the plan is made consistent.",
        "raised": "implementation-stage S1",
        "ref": "plan.md",
        "resolved": "2026-10-25T23:58:10Z"
      },
      {
        "text": "The planned reflection check that GuardedDns declares no newCall method is defeated by a compiler-emitted bridge method; the test asserts the behaviour instead. The plan's row awaits amendment.",
        "raised": "implementation-stage S5",
        "ref": "final-implementation.md",
        "resolved": "2026-10-26T00:30:51Z"
      },
      {
        "text": "The js check compile prints six warnings that annotations cannot be read at runtime on the JavaScript target, one per builder carrying the marker, a consequence of the runtime retention the reflection test needs; the JVM compile prints none. The plan's expectation of a single warning line is amended, or a suppression is specified.",
        "raised": "implementation-stage S8",
        "ref": "plan.md",
        "resolved": "Plan Amendment 5 leaves the marker's retention at its default, which the JVM keeps at runtime, so the reflection test still works and the js compile prints no warning."
      },
      {
        "text": "The architecture carries pre-existing wording that states each unit's sizing; since the committed-content rule scans every line of a file a commit carries, those lines are reworded to state each unit's scope instead, in this landing's docs commit.",
        "raised": "qa pass 2",
        "ref": "../architecture.md"
      }
    ],
    "event": {
      "name": "merge",
      "at": "2026-10-26T22:45:45Z",
      "note": "merging"
    },
    "history": {
      "sizing": [
        {
          "since": null,
          "ended": "2026-10-25T21:42:50Z",
          "endedBy": "sized",
          "to": "plan"
        }
      ],
      "plan": [
        {
          "since": "2026-10-25T21:42:50Z",
          "ended": "2026-10-25T22:01:05Z",
          "endedBy": "research-planning",
          "to": "research",
          "event": {
            "name": "sized",
            "at": "2026-10-25T21:42:50Z"
          },
          "detail": "Sized as one subsystem of `webtools-http`, the outbound-URL policy: additive, no version moved; the blocker #62 is closed. Unit 5's plan is re-passed against architecture.md §6.3.1 and §11 with units 3 and 4 as built.",
          "plan": {
            "state": "re-pass",
            "from": "parent",
            "since": "2026-10-25T21:42:50Z"
          }
        },
        {
          "since": "2026-10-25T22:50:29Z",
          "ended": "2026-10-25T23:33:53Z",
          "endedBy": "plan-accepted",
          "to": "branch",
          "event": {
            "name": "researched",
            "at": "2026-10-25T22:50:29Z"
          },
          "detail": "Four questions (IANA special-purpose address rows, the configuration API shape, the published bypass vectors, JVM enforcement) returned and verified: 81 load-bearing claims re-checked at their sources, 80 hold, none fails, one cites the repository itself. The findings and the summary are committed on the branch. Nothing is left open; plan.md is being written.",
          "plan": {
            "state": "writing",
            "since": "2026-10-25T22:50:29Z"
          }
        }
      ],
      "research": [
        {
          "since": "2026-10-25T22:01:05Z",
          "ended": "2026-10-25T22:50:29Z",
          "endedBy": "researched",
          "to": "plan",
          "event": {
            "name": "verification-started",
            "at": "2026-10-25T22:40:34Z"
          },
          "detail": "The sources of 80 load-bearing claims from the four findings files are re-opened.",
          "research": {
            "phase": "complete",
            "drafted": 0,
            "since": {
              "planning": "2026-10-25T22:01:05Z",
              "research": "2026-10-25T22:22:42Z",
              "verification": "2026-10-25T22:40:34Z",
              "complete": "2026-10-25T22:50:29Z"
            }
          },
          "questions": [
            {
              "question": "jvm-enforcement",
              "rows": [],
              "findings": "research/jvm-enforcement.md",
              "state": "complete",
              "started": "2026-10-25T22:22:42Z",
              "returned": "2026-10-25T22:39:55Z"
            },
            {
              "question": "iana-special-purpose-rows",
              "rows": [],
              "findings": "research/iana-special-purpose-rows.md",
              "state": "complete",
              "started": "2026-10-25T22:22:42Z",
              "returned": "2026-10-25T22:27:23Z"
            },
            {
              "question": "bypass-vectors",
              "rows": [],
              "findings": "research/bypass-vectors.md",
              "state": "complete",
              "started": "2026-10-25T22:22:42Z",
              "returned": "2026-10-25T22:38:50Z"
            },
            {
              "question": "config-api-shape",
              "rows": [],
              "findings": "research/config-api-shape.md",
              "state": "complete",
              "started": "2026-10-25T22:22:42Z",
              "returned": "2026-10-25T22:32:35Z"
            }
          ]
        }
      ],
      "branch": [
        {
          "since": "2026-10-25T23:33:53Z",
          "ended": "2026-10-25T23:33:53Z",
          "endedBy": "branch-cut",
          "to": "implementation",
          "event": {
            "name": "plan-accepted",
            "at": "2026-10-25T23:33:53Z"
          },
          "detail": "Seven stages: S1 TransportSettings, StrictIp, CidrBlock; S2 the Trusted and Untrusted policies; S3 the classifier (HostRules, AddressRanges, GuardRelaxation, UrlGuard) and the IANA, control and bypass-vector tables; S4 the ABI dump (first commit point); S5 the JVM guards (GuardedDns overriding lookup only, GuardedSocketFactory on its own Proxy.NO_PROXY sockets, two stackless marker exceptions, the per-policy enforcement bundle); S6 floor guard, check, mechanical rules, six mutation checks (second commit point); S7 README line and the acceptance pass. About 34 test classes across test levels 1, 2, 3 (loopback only), 4a and 4c; no build-file change; UrlHost.kt untouched; nothing added to the fixtures. Design refinements beyond the architecture text, recorded in impact.md §5: two deny rules outside the IANA registry (the Azure platform endpoint 168.63.129.16/32 and ISATAP identifiers in 2000::/3) closing published bypasses; sockets created on Proxy.NO_PROXY; newCall inherited so record-based resolvers cannot bypass the guard; refusals carry a range class for the log line only. No version moves; webtools-http 1.0.0 stays unreleased.",
          "open": [
            {
              "text": "A public @DslMarker for the module's builders is left open: adopting it before the 1.0.0 release costs consumers nothing, afterwards it tightens their sources; recorded as a follow-up and known gap.",
              "raised": "plan-accepted",
              "ref": "impact.md"
            },
            {
              "text": "Three dated architecture callouts proposed for §6.3.1 and §7.4 (the two extra deny rules; sockets on Proxy.NO_PROXY and newCall inherited; the transport-to-policy row carrying the range class); applied at reconciliation if approved.",
              "raised": "plan-accepted",
              "ref": "plan.md"
            }
          ],
          "branch": "feat/issue-64-url-policy"
        }
      ],
      "implementation": [
        {
          "since": "2026-10-25T23:33:53Z",
          "ended": "2026-10-26T00:34:29Z",
          "endedBy": "implementation-stage",
          "to": "qa",
          "event": {
            "name": "implementation-stage",
            "at": "2026-10-26T00:30:51Z",
            "note": "S6 reported done; S7 current"
          },
          "detail": "S6: verifyJavaFloor passes (174 classes in 2 jars at major 55 or below); :webtools-http:check and :webtools-http-commoncheck:check green, jvmTest 88 classes / 500 tests. Mechanical rules all hold. Mutations: a forwarding newCall fails exactly the Amendment 2 set; a plain Socket goes through the fake SOCKS listener and fails the SOCKS test; deleting the Azure row or widening the ISATAP mask fails the controls and the published-vector groups; dropping idleConnectionTtl from equals fails its property test; a loopback early return fails the relaxation test. Plan Amendment 2 (dated 2026-10-25) replaces the declaredMethods check with the behavioural one, so the S5 note is a plan inconsistency, not a deviation. Legacy modules, build-logic, the module build file and the fixtures untouched.",
          "open": [
            {
              "text": "A public @DslMarker for the module's builders is left open: adopting it before the 1.0.0 release costs consumers nothing, afterwards it tightens their sources; recorded as a follow-up and known gap.",
              "raised": "plan-accepted",
              "ref": "impact.md"
            },
            {
              "text": "Three dated architecture callouts proposed for §6.3.1 and §7.4 (the two extra deny rules; sockets on Proxy.NO_PROXY and newCall inherited; the transport-to-policy row carrying the range class); applied at reconciliation if approved.",
              "raised": "plan-accepted",
              "ref": "plan.md"
            }
          ],
          "stages": [
            {
              "id": "S1",
              "name": "TransportSettings, StrictIp, CidrBlock",
              "state": "done",
              "rows": [
                "transport-settings",
                "untrusted",
                "host-rules",
                "classifier-tests"
              ]
            },
            {
              "id": "S2",
              "name": "Trusted and Untrusted policies",
              "state": "done",
              "rows": [
                "policy-pair",
                "trusted",
                "untrusted"
              ]
            },
            {
              "id": "S3",
              "name": "classifier and tables",
              "state": "done",
              "rows": [
                "trusted",
                "untrusted",
                "address-table",
                "host-rules",
                "url-guard",
                "classifier-tests"
              ]
            },
            {
              "id": "S4",
              "name": "ABI dump (first commit point)",
              "state": "done",
              "rows": [
                "release"
              ]
            },
            {
              "id": "S5",
              "name": "JVM guards: GuardedDns, GuardedSocketFactory, markers, enforcement bundle",
              "state": "done",
              "rows": [
                "jvm-guards",
                "jvm-tests"
              ]
            },
            {
              "id": "S6",
              "name": "floor guard, check, mechanical rules, mutation checks (second commit point)",
              "state": "done",
              "rows": [
                "classifier-tests",
                "jvm-tests",
                "release"
              ]
            },
            {
              "id": "S7",
              "name": "README line and acceptance pass",
              "state": "done",
              "rows": []
            }
          ]
        },
        {
          "since": "2026-10-26T01:50:38Z",
          "ended": "2026-10-26T01:59:36Z",
          "endedBy": "implementation-stage",
          "to": "qa",
          "event": {
            "name": "implementation-stage",
            "at": "2026-10-26T01:50:38Z",
            "note": "S8 current"
          },
          "detail": "Amendment 3 adds WebToolsDsl, a Stable Core @DslMarker annotation on every builder the module hands to a consumer's lambda (the headers, request, response, settings and both policy builders): inside a nested block an outer builder is reachable only through its label. Proof: a throwaway canary of five implicit outer reaches compiles before the marker and fails at each statement after it; a gating test of labelled reaches and a reflection test over the six builders; the dump grows by exactly three lines. Amendment 4 records matchesPrefix as private and the architecture's corrections. The records are reconciled (acceptance criteria 1 to 7 met, 8 pending S8; the two IDE-only checks owned by the maintainer before #65 starts); the parent impact carries unit 5's as-built block; architecture.md carries the three corrections dated 2026-10-26. Commit plan: 3 the marker with plan.md and the research summary, 4 README, 5 the records with the parent impact and the architecture.",
          "stages": [
            {
              "id": "S1",
              "name": "TransportSettings, StrictIp, CidrBlock",
              "state": "done",
              "rows": [
                "transport-settings",
                "untrusted",
                "host-rules",
                "classifier-tests"
              ]
            },
            {
              "id": "S2",
              "name": "Trusted and Untrusted policies",
              "state": "done",
              "rows": [
                "policy-pair",
                "trusted",
                "untrusted"
              ]
            },
            {
              "id": "S3",
              "name": "classifier and tables",
              "state": "done",
              "rows": [
                "trusted",
                "untrusted",
                "address-table",
                "host-rules",
                "url-guard",
                "classifier-tests"
              ]
            },
            {
              "id": "S4",
              "name": "ABI dump (first commit point)",
              "state": "done",
              "rows": [
                "release"
              ]
            },
            {
              "id": "S5",
              "name": "JVM guards: GuardedDns, GuardedSocketFactory, markers, enforcement bundle",
              "state": "done",
              "rows": [
                "jvm-guards",
                "jvm-tests"
              ]
            },
            {
              "id": "S6",
              "name": "floor guard, check, mechanical rules, mutation checks (second commit point)",
              "state": "done",
              "rows": [
                "classifier-tests",
                "jvm-tests",
                "release"
              ]
            },
            {
              "id": "S7",
              "name": "README line and acceptance pass",
              "state": "done",
              "rows": []
            },
            {
              "id": "S8",
              "name": "WebToolsDsl marker on the six builders, canary proof, two tests, dump (third commit point)",
              "state": "done",
              "rows": []
            }
          ]
        }
      ],
      "qa": [
        {
          "since": "2026-10-26T00:34:29Z",
          "ended": "2026-10-26T01:09:46Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "implementation-stage",
            "at": "2026-10-26T00:34:29Z",
            "note": "S7 reported done; qa next"
          },
          "detail": "S7: the README's webtools-http row names the outbound-URL policies (one line, CRLF kept). Acceptance pass from the command line: the committed dump re-read against the plan and the tier table; two IDE-only items (Quick Documentation of five declarations; the IntelliJ import check of the js module) recorded as not performed, for the maintainer. All seven acceptance criteria met with measurements in the record. Commits 1 (fd93ff9) and 2 (b46f58a) hold the code; README and the records are uncommitted for commits 3 and 4. Legacy modules, build-logic, the module build file, the fixtures, the check module and UrlHost.kt untouched.",
          "open": [
            {
              "text": "A public @DslMarker for the module's builders is left open: adopting it before the 1.0.0 release costs consumers nothing, afterwards it tightens their sources; recorded as a follow-up and known gap.",
              "raised": "plan-accepted",
              "ref": "impact.md"
            },
            {
              "text": "Three dated architecture callouts proposed for §6.3.1 and §7.4 (the two extra deny rules; sockets on Proxy.NO_PROXY and newCall inherited; the transport-to-policy row carrying the range class); applied at reconciliation if approved.",
              "raised": "plan-accepted",
              "ref": "plan.md"
            }
          ],
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-10-26",
              "at": "2026-10-26T01:09:46Z",
              "findings": {
                "documentation": 19,
                "test": 4,
                "code": 0
              },
              "severity": {
                "minor": 23,
                "other": 0
              },
              "route": "on-the-spot"
            }
          ]
        },
        {
          "since": "2026-10-26T01:59:36Z",
          "ended": "2026-10-26T22:02:11Z",
          "endedBy": "qa-pass",
          "to": "remediation",
          "event": {
            "name": "implementation-stage",
            "at": "2026-10-26T01:59:36Z",
            "note": "S8 reported done; qa next"
          },
          "detail": "S8: WebToolsDsl (a Stable Core DSL marker, runtime retention) on the headers, request, response, settings and both policy builders; the canary's five implicit outer reaches compiled before the marker and each failed after it, with no other file affected; the gating test shows labelled reaches work and the component test finds the marker on all six builders; removing it from one builder fails that test naming it; the dump grows by exactly its three lines; verifyJavaFloor counts 175 classes; both modules' check green, jvmTest 90 classes / 504 tests. Acceptance criterion 8 met. Commit 3 (the marker, its tests, the dump, plan.md) follows the plan's ruling on the js warnings.",
          "open": [
            {
              "text": "The js check compile prints six warnings that annotations cannot be read at runtime on the JavaScript target, one per builder carrying the marker, a consequence of the runtime retention the reflection test needs; the JVM compile prints none. The plan's expectation of a single warning line is amended, or a suppression is specified.",
              "raised": "implementation-stage S8",
              "ref": "plan.md"
            }
          ],
          "qa": [
            {
              "pass": 2,
              "verdict": "changes-requested",
              "date": "2026-10-26",
              "at": "2026-10-26T22:02:11Z",
              "findings": {
                "documentation": 15,
                "test": 4,
                "code": 0
              },
              "severity": {
                "minor": 18,
                "other": 1
              },
              "route": "on-the-spot"
            }
          ]
        }
      ],
      "remediation": [
        {
          "since": "2026-10-26T01:09:46Z",
          "ended": "2026-10-26T01:50:38Z",
          "endedBy": "implementation-stage",
          "to": "implementation",
          "event": {
            "name": "implementation-stage",
            "at": "2026-10-26T01:16:18Z",
            "note": "qa pass 1 fixes done; reconciliation current"
          },
          "detail": "Fixes: the internal JVM seams now state their wiring (which builder methods take which field, the client preconditions, the guard order, that literal hosts never reach the resolver), thread and logging contracts, after-throw behaviour and the stackless markers' consequence, with @property, @param, @return and @throws throughout; the common helpers carry KDoc instead of line comments; six line comments on bit arithmetic and reserved names; one duplicated assertion dropped; the integration timing bound tightened to two seconds; cross-references in the record fixed. 18 code and test files changed, by comments and KDoc apart from the dropped assertion, the tightened bound and `matchesPrefix` made `private` to `CidrBlock.kt` (plan Amendment 4); the dump unchanged; apiCheck, componentTest, integrationTest and deterministicTest green.",
          "open": [
            {
              "text": "A public @DslMarker for the module's builders is left open: adopting it before the 1.0.0 release costs consumers nothing, afterwards it tightens their sources; recorded as a follow-up and known gap.",
              "raised": "plan-accepted",
              "ref": "impact.md"
            },
            {
              "text": "Three dated architecture callouts proposed for §6.3.1 and §7.4 (the two extra deny rules; sockets on Proxy.NO_PROXY and newCall inherited; the transport-to-policy row carrying the range class); applied at reconciliation if approved.",
              "raised": "plan-accepted",
              "ref": "plan.md"
            }
          ],
          "remediation": {
            "pass": 1,
            "route": "on-the-spot",
            "steps": [
              {
                "name": "KDoc contract sentences and tags on the internal JVM seams and common helpers, six line comments, one stray assertion, one timing bound, two cross-references in the record; apiCheck and the touched level tasks re-run",
                "state": "done"
              },
              {
                "name": "as-built reconciliation of the records and the parent impact",
                "state": "done"
              }
            ]
          },
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-10-26",
              "at": "2026-10-26T01:09:46Z",
              "findings": {
                "documentation": 19,
                "test": 4,
                "code": 0
              },
              "severity": {
                "minor": 23,
                "other": 0
              },
              "route": "on-the-spot",
              "note": "Every plan item and acceptance criterion traced to the code, the public surface exactly the planned Stable Core set, hygiene clean. The findings, all minor: the internal JVM guards (`GuardedDns`, `GuardedSocket`, `GuardedSocketFactory`, `PolicyEnforcement`, the markers) lacked contract tags and the facts a wirer needs (builder wiring, thread, the `DEBUG` line, guard order, the stackless markers); the common helpers (`OutboundUrlPolicy.kt`, `UrlGuard`, `StrictIp`, the test support) carried line comments where KDoc is due; six bit-arithmetic or reserved-name lines (`CidrBlock`, `AddressRanges`, `HostRules`, `UrlGuard`) lacked a comment; one duplicated assertion (`TrustedGuardComponentTest`), one timing bound equal to its timeout (`PolicyEnforcementOkHttpIntegrationTest`) and two dangling cross-references in the as-built record."
            }
          ]
        },
        {
          "since": "2026-10-26T22:02:11Z",
          "ended": "2026-10-26T22:11:39Z",
          "endedBy": "landing",
          "to": "landing",
          "event": {
            "name": "qa-pass",
            "at": "2026-10-26T22:02:11Z",
            "note": "pass 2: changes-requested"
          },
          "detail": "Pass 1's remediation confirmed complete with no regression; S8 verified line for line against the plan (the marker byte for byte, six annotated builders, both tests, the three dump lines, the canary absent, no JavaScript warnings after Amendment 5 left the retention implicit); correctness clean with every acceptance item met. The documentation items were sibling-fixture tags, one boundary paragraph, four line comments and stale wording in the records; the host-form table publishes 36 forms, now stated as measured.",
          "open": [
            {
              "text": "The architecture carries pre-existing wording that states each unit's sizing; since the committed-content rule scans every line of a file a commit carries, those lines are reworded to state each unit's scope instead, in this landing's docs commit.",
              "raised": "qa pass 2",
              "ref": "../architecture.md"
            }
          ],
          "remediation": {
            "pass": 2,
            "route": "on-the-spot",
            "steps": [
              {
                "name": "the 14 documentation items and the advisory test items: KDoc on the sibling fixtures, a GuardedSocket boundary paragraph, four line comments, the records' stale S8 wording, the host-form count reconciled; apiCheck and the level tasks re-run",
                "state": "done"
              },
              {
                "name": "reword the pre-existing wording that states each unit's sizing or names a role, in every document the commits carry (the architecture's per-unit lines included)",
                "state": "current"
              }
            ]
          },
          "qa": [
            {
              "pass": 2,
              "verdict": "changes-requested",
              "date": "2026-10-26",
              "at": "2026-10-26T22:02:11Z",
              "findings": {
                "documentation": 15,
                "test": 4,
                "code": 0
              },
              "severity": {
                "minor": 18,
                "other": 1
              },
              "route": "on-the-spot",
              "note": "Pass 1's fixes confirmed with no regression, and stage S8 checked line for line against the plan: the `WebToolsDsl` marker, the six builders that carry it, both of its tests and the dump's three new lines, with no JavaScript warning once plan Amendment 5 left the retention at its default. Correctness clean and every acceptance criterion met. The findings: KDoc tags on the sibling test fixtures, a boundary paragraph on `GuardedSocket`, four line comments, stale S8 wording in the records, the host-form count reconciled to the 36 forms the parent research's table publishes, and pre-existing wording in the documents the commits carry that states each unit's sizing. 504 tests in 90 classes passed, the change's 36 classes (232 tests) among them."
            }
          ]
        }
      ],
      "landing": [
        {
          "since": "2026-10-26T22:11:39Z",
          "ended": "2026-10-26T22:14:14Z",
          "endedBy": "state-boards",
          "to": "state-boards",
          "event": {
            "name": "landing",
            "at": "2026-10-26T22:11:39Z",
            "note": "land accepted"
          },
          "detail": "Landing accepted: sync with master, then the pre-merge state-board refresh and the merge.",
          "open": [
            {
              "text": "The architecture carries pre-existing wording that states each unit's sizing; since the committed-content rule scans every line of a file a commit carries, those lines are reworded to state each unit's scope instead, in this landing's docs commit.",
              "raised": "qa pass 2",
              "ref": "../architecture.md"
            }
          ]
        }
      ],
      "state-boards": [
        {
          "since": "2026-10-26T22:14:14Z",
          "ended": "2026-10-26T22:45:45Z",
          "endedBy": "merge",
          "to": "merge",
          "event": {
            "name": "state-boards",
            "at": "2026-10-26T22:14:14Z",
            "note": "the state boards refreshed on the branch"
          },
          "detail": "Synced with master (unchanged at ad0ae63); no version moves. The state boards are refreshed on the branch at 543bd08.",
          "open": [
            {
              "text": "The architecture carries pre-existing wording that states each unit's sizing; since the committed-content rule scans every line of a file a commit carries, those lines are reworded to state each unit's scope instead, in this landing's docs commit.",
              "raised": "qa pass 2",
              "ref": "../architecture.md"
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
        "date": "2026-10-26",
        "at": "2026-10-26T01:09:46Z",
        "findings": {
          "documentation": 19,
          "test": 4,
          "code": 0
        },
        "severity": {
          "minor": 23,
          "other": 0
        },
        "route": "on-the-spot",
        "note": "Every plan item and acceptance criterion traced to the code, the public surface exactly the planned Stable Core set, hygiene clean. The findings, all minor: the internal JVM guards (`GuardedDns`, `GuardedSocket`, `GuardedSocketFactory`, `PolicyEnforcement`, the markers) lacked contract tags and the facts a wirer needs (builder wiring, thread, the `DEBUG` line, guard order, the stackless markers); the common helpers (`OutboundUrlPolicy.kt`, `UrlGuard`, `StrictIp`, the test support) carried line comments where KDoc is due; six bit-arithmetic or reserved-name lines (`CidrBlock`, `AddressRanges`, `HostRules`, `UrlGuard`) lacked a comment; one duplicated assertion (`TrustedGuardComponentTest`), one timing bound equal to its timeout (`PolicyEnforcementOkHttpIntegrationTest`) and two dangling cross-references in the as-built record."
      },
      {
        "pass": 2,
        "verdict": "changes-requested",
        "date": "2026-10-26",
        "at": "2026-10-26T22:02:11Z",
        "findings": {
          "documentation": 15,
          "test": 4,
          "code": 0
        },
        "severity": {
          "minor": 18,
          "other": 1
        },
        "route": "on-the-spot",
        "note": "Pass 1's fixes confirmed with no regression, and stage S8 checked line for line against the plan: the `WebToolsDsl` marker, the six builders that carry it, both of its tests and the dump's three new lines, with no JavaScript warning once plan Amendment 5 left the retention at its default. Correctness clean and every acceptance criterion met. The findings: KDoc tags on the sibling test fixtures, a boundary paragraph on `GuardedSocket`, four line comments, stale S8 wording in the records, the host-form count reconciled to the 36 forms the parent research's table publishes, and pre-existing wording in the documents the commits carry that states each unit's sizing. 504 tests in 90 classes passed, the change's 36 classes (232 tests) among them."
      }
    ],
    "qaMax": 3,
    "waitingOn": null,
    "resume": null,
    "blockers": {
      "waitsOn": [],
      "blocks": [
        {
          "issue": 65
        }
      ]
    },
    "landing": null
  },
  "milestones": [
    {
      "column": "gap",
      "label": "Research verified, in #55's run",
      "date": "2026-10-21",
      "ref": "../research/README.md",
      "note": "#55's ten questions and findings; 231 outside-world claims re-opened at source, 227 hold, none fails. Written on this board at sized from the research README, `outbound-url-policy.md`, architecture §6.3.1 and this unit's row in §11"
    },
    {
      "column": "gap",
      "label": "Research verified, in this unit's run",
      "date": "2026-10-25",
      "ref": "research/README.md",
      "note": "four questions and four findings files; 81 load-bearing claims re-opened at source: 80 hold, none fails, one cites this repository. The Gap column, written at sized from #55's research and architecture, stands as written; this unit's findings add their evidence to seven rows, among them two published targets a table built from the registries alone misses"
    },
    {
      "column": "considered",
      "label": "Architecture signed off, in #55's run",
      "date": "2026-10-21",
      "ref": "../architecture.md",
      "note": "the options #55's architecture weighed for this unit: §6.3.1, §6.9, §4 and §10, with `outbound-url-policy.md`'s considerations behind them and #55's impact §8 for what units 3 and 4 leave it; this unit's own plan adds to them at plan acceptance"
    },
    {
      "column": "considered",
      "label": "The unit's own plan adds to the options",
      "date": "2026-10-25",
      "ref": "plan.md",
      "note": "the options plan.md weighs beside the architecture's: no policy instance in the sealed base's companion, a required `from`, the two deny rules outside the registries, one IP-literal grammar left as a follow-up, the resolver guard's inherited `newCall`, sockets on `Socket(Proxy.NO_PROXY)`, markers that are never a `ConnectException`"
    },
    {
      "column": "verdict",
      "label": "Plan accepted",
      "date": "2026-10-25",
      "ref": "plan.md",
      "note": "seven stages, S1 to S7, on `feat/issue-64-url-policy`, with two code commits; the design makes #55's architecture §6.3.1 and §7.4 concrete in impact §5: two deny rules outside the IANA registries, sockets on `Proxy.NO_PROXY`, `newCall` inherited, the range class beside the reason. No version moves"
    }
  ],
  "trail": [
    {
      "date": "2026-10-22",
      "column": "gap",
      "label": "Issue #64 opened as unit 5 of #55, blocked by #62",
      "ref": "#64"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "#62 landed through PR #82 as 7de8207: the blocker is closed",
      "ref": "7de8207"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Run started: sized as one subsystem of `webtools-http`, the outbound-URL policy; unit 5's plan is re-passed against #55's architecture §6.3.1 and §11, with units 3 and 4 as built",
      "ref": "../architecture.md"
    },
    {
      "date": "2026-10-25",
      "column": "considered",
      "label": "Gap and Considered filled from #55's research and architecture: 12 rows, the options with the verdicts the architecture gave",
      "ref": "../architecture.md"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research planning: 0 questions drafted"
    },
    {
      "date": "2026-10-25",
      "column": "considered",
      "label": "Open point resolved: One IP-literal grammar for the module, or a separate StrictIp beside Url's host parsing: StrictIp is self-contained and Url's host parsing stays unchanged; an agreement test pins the two to the same literal forms"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research started: `jvm-enforcement`, `iana-special-purpose-rows`, `bypass-vectors`, `config-api-shape`"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research returned: `iana-special-purpose-rows`",
      "ref": "research/iana-special-purpose-rows.md"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research returned: `config-api-shape`",
      "ref": "research/config-api-shape.md"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research returned: `bypass-vectors`",
      "ref": "research/bypass-vectors.md"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research returned: `jvm-enforcement`",
      "ref": "research/jvm-enforcement.md"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Verification started: 4 findings"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research verified; the plan next",
      "ref": "research/README.md"
    },
    {
      "date": "2026-10-25",
      "column": "verdict",
      "label": "Plan accepted",
      "ref": "plan.md"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "Branch `feat/issue-64-url-policy` cut; implementation starts at S1"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "S1 done; S2 current: Trusted and Untrusted policies"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "S2 done; S3 current: classifier and tables"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "Open point resolved: Plan §9's CidrBlockParseDeterministicTest lists `/8` and `10.0.0.0/` as shape refusals, while §7.2's parse, as written, answers the address and prefix constants for an empty part; the code stays as §7.2 and the two rows assert only a refusal until the plan is made consistent.",
      "ref": "plan.md"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "S3 done; S4 current: ABI dump (first commit point)"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "S4 done; S5 current: JVM guards: GuardedDns, GuardedSocketFactory, markers, enforcement bundle"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "S5 done; S6 current: floor guard, check, mechanical rules, mutation checks (second commit point)"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "S6 done; S7 current: README line and acceptance pass"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "Open point resolved: The planned reflection check that GuardedDns declares no newCall method is defeated by a compiler-emitted bridge method; the test asserts the behaviour instead. The plan's row awaits amendment.",
      "ref": "final-implementation.md"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "Implementation done (7 stages); qa next"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "QA pass 1: changes requested (19 documentation, 4 test, 23 minor)"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "Remediation: qa pass 1 fixes done; reconciliation current"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "Remediation for qa pass 1: S8 current"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "Open point resolved: A public @DslMarker for the module's builders is left open: adopting it before the 1.0.0 release costs consumers nothing, afterwards it tightens their sources; recorded as a follow-up and known gap.",
      "ref": "impact.md"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "Open point resolved: Three dated architecture callouts proposed for §6.3.1 and §7.4 (the two extra deny rules; sockets on Proxy.NO_PROXY and newCall inherited; the transport-to-policy row carrying the range class); applied at reconciliation if approved.",
      "ref": "plan.md"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "Implementation done (8 stages); qa next"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "QA pass 2: changes requested (15 documentation, 4 test, 18 minor, 1 other)"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "Open point resolved: The js check compile prints six warnings that annotations cannot be read at runtime on the JavaScript target, one per builder carrying the marker, a consequence of the runtime retention the reflection test needs; the JVM compile prints none. The plan's expectation of a single warning line is amended, or a suppression is specified.",
      "ref": "plan.md"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "Landing: the land accepted"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "State boards: refreshed on the branch before the merge"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "Merge: merging it"
    }
  ],
  "rows": [
    {
      "id": "policy-pair",
      "group": "Code",
      "name": "`OutboundUrlPolicy`, the closed `Trusted`/`Untrusted` pair",
      "system": "webtools-http-url-policy",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing decides which URLs a fetch may reach. #62 built the vocabulary of a refusal, nine `RefusalReason` constants and `TransportFailure.RefusedByPolicy`, and no policy type raises it, so the stack's promise for administrator-supplied URLs has no type to rest on.",
        "evidence": [
          "common/RefusalReason.kt:13 — the open constants class, nine refusals, none raised (baseline)",
          "common/TransportFailure.kt:154 — `RefusedByPolicy(reason)` (baseline)",
          "../research/outbound-url-policy.md F1, F4 — why the policy needs checks of its own"
        ],
        "source": "../research/README.md § Outbound-URL policy; ../architecture.md §6.3.1",
        "link": "../research/README.md#outbound-url-policy"
      },
      "options": [
        {
          "id": "closed-pair",
          "name": "A sealed pair, `Trusted` and `Untrusted`, with tightening-only data parameters, each built by a factory with a builder (`OutboundUrlPolicy.trusted { }`, `untrusted { }`)",
          "verdict": "accepted",
          "why": "The closed set is the contract. Fetching from a private address is `Trusted`, so nothing has to lower `Untrusted`'s protection, and the parameters are data lists, as in every comparable guard read.",
          "source": "../architecture.md §6.3.1, §9",
          "link": "../architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement"
        },
        {
          "id": "guard-seam",
          "name": "A consumer-implemented `UrlGuard` seam at 1.0.0",
          "verdict": "declined",
          "why": "No surveyed guard exposes a verdict callback, a callback that can return \"allow\" can silently defeat `Untrusted`, and the Experimental tier is for seams a real consumer has shaped.",
          "wins": "Later, additively, as a deny-only interface.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "loosening",
          "name": "A loosening parameter on `Untrusted`: an allow list, or an allow-private or allow-loopback flag, as the surveyed guards ship",
          "verdict": "declined",
          "why": "The loosening use cases, such as a self-hosted API at a private address, are what `Trusted` is for, and a public loosening knob lets a consumer switch the guard off.",
          "source": "../research/outbound-url-policy.md F12, pitfall 9; ../architecture.md §4",
          "link": "../research/outbound-url-policy.md#f12-policy-parameters-in-comparable-guards-are-data-lists-not-callbacks-observed-practice"
        },
        {
          "id": "companion-instances",
          "name": "Default policy instances held in the sealed base's companion",
          "verdict": "declined",
          "why": "A static field holding a `Trusted` makes the base's class initialiser construct a subclass, and two threads initialising the base and a subclass can deadlock on the class-initialisation locks; the default seeds are built inside the factory functions.",
          "source": "plan.md §6.2, §10",
          "link": "plan.md#62-the-public-configuration-shape"
        },
        {
          "id": "dsl-marker",
          "name": "A public `@DslMarker` annotation, `WebToolsDsl`, on every builder the module passes to a consumer's lambda, the policy builders and the existing ones",
          "verdict": "deferred",
          "why": "The research recommends one public marker on every builder; the plan left it open as a non-goal, a follow-up and a known gap. Before the 1.0.0 release it costs a consumer nothing; after it, it would tighten consumers' sources.",
          "source": "research/config-api-shape.md F4; plan.md §4, §13, §14",
          "link": "research/config-api-shape.md#f4-dslmarker-what-it-does-who-declares-it-publicly-what-is-unknown-about-internal-established-consensus-for-semantics-common-practice-for-visibility"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "statements": [
          {
            "text": "`OutboundUrlPolicy` is a sealed, parameterless base with the final nested `Trusted` and `Untrusted` over `internal` constructors, each with a nested `Builder` that carries `@WebToolsDsl`; the gating consumer compiles with no opt-in.",
            "state": "amended",
            "ref": "final-implementation.md#p2--s2-the-policies",
            "excerpt": {
              "ref": "final-implementation.md#p2--s2-the-policies",
              "heading": "P2 — S2: the policies",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 3s` (`OutboundUrlPolicy.kt` compiled for both targets; no `e:`, no new `w:`). · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 15s`. The result XML lists the S2 classes: `OutboundUrlPolicyConsumerGatingTest` (5 tests, in `gatingTest`'s results), `TrustedPolicyComponentTest` (13), `UntrustedPolicyComponentTest` (18), `PolicyWithSettingsComponentTest` (4); …",
              "hash": "83b2528f1be1"
            }
          },
          {
            "text": "The four companion factories return the precise type, and the builders copy their collections and validate at `build()`.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s2-the-policies",
            "excerpt": {
              "ref": "final-implementation.md#p2--s2-the-policies",
              "heading": "P2 — S2: the policies",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 3s` (`OutboundUrlPolicy.kt` compiled for both targets; no `e:`, no new `w:`). · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 15s`. The result XML lists the S2 classes: `OutboundUrlPolicyConsumerGatingTest` (5 tests, in `gatingTest`'s results), `TrustedPolicyComponentTest` (13), `UntrustedPolicyComponentTest` (18), `PolicyWithSettingsComponentTest` (4); …",
              "hash": "83b2528f1be1"
            }
          },
          {
            "text": "No policy instance is held in the base's companion: the default seeds are built inside the factories.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s2-the-policies",
            "excerpt": {
              "ref": "final-implementation.md#p2--s2-the-policies",
              "heading": "P2 — S2: the policies",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 3s` (`OutboundUrlPolicy.kt` compiled for both targets; no `e:`, no new `w:`). · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 15s`. The result XML lists the S2 classes: `OutboundUrlPolicyConsumerGatingTest` (5 tests, in `gatingTest`'s results), `TrustedPolicyComponentTest` (13), `UntrustedPolicyComponentTest` (18), `PolicyWithSettingsComponentTest` (4); …",
              "hash": "83b2528f1be1"
            }
          },
          {
            "text": "Equality is hand-written over every field, and `toString()` prints counts, never a pinned host, a range or a suffix.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s2-the-policies",
            "excerpt": {
              "ref": "final-implementation.md#p2--s2-the-policies",
              "heading": "P2 — S2: the policies",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 3s` (`OutboundUrlPolicy.kt` compiled for both targets; no `e:`, no new `w:`). · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 15s`. The result XML lists the S2 classes: `OutboundUrlPolicyConsumerGatingTest` (5 tests, in `gatingTest`'s results), `TrustedPolicyComponentTest` (13), `UntrustedPolicyComponentTest` (18), `PolicyWithSettingsComponentTest` (4); …",
              "hash": "83b2528f1be1"
            }
          },
          {
            "text": "The `internal` `withSettings` derives a policy with other settings.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s2-the-policies",
            "excerpt": {
              "ref": "final-implementation.md#p2--s2-the-policies",
              "heading": "P2 — S2: the policies",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 3s` (`OutboundUrlPolicy.kt` compiled for both targets; no `e:`, no new `w:`). · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 15s`. The result XML lists the S2 classes: `OutboundUrlPolicyConsumerGatingTest` (5 tests, in `gatingTest`'s results), `TrustedPolicyComponentTest` (13), `UntrustedPolicyComponentTest` (18), `PolicyWithSettingsComponentTest` (4); …",
              "hash": "83b2528f1be1"
            }
          }
        ],
        "changes": [
          {
            "what": "`WebToolsDsl`, a public Stable Core `@DslMarker`, marks both policy builders, `TransportSettings.Builder` and the existing `HttpHeaders.Builder`, `HttpRequest.Builder` and `HttpResponse.Builder`: inside a nested block an outer builder is reached only through its label (`this@untrusted.allowedPorts`).",
            "why": "Plan Amendment 3 settles the marker the plan had left open, while adopting it still costs consumers nothing. A throwaway canary of five implicit outer reaches compiled before the marker and failed at each after it, with no other file affected; `WebToolsDslConsumerGatingTest` and `WebToolsDslComponentTest` keep it.",
            "when": "S8 (plan Amendment 3)",
            "ref": "final-implementation.md#s8--the-webtoolsdsl-marker-plan-amendment-3",
            "settles": {
              "option": "dsl-marker",
              "verdict": "accepted"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One system, the outbound-URL policy: a closed pair of configuration values in common code, with no seam a consumer implements.",
        "statements": [
          {
            "text": "`OutboundUrlPolicy` is a sealed, parameterless base with the final nested `Trusted` and `Untrusted`, each over an `internal` constructor with a nested `Builder`; all of it is Stable Core and nothing is gated.",
            "ref": "plan.md#r1-public-surface-and-tier",
            "label": "§R1",
            "excerpt": {
              "ref": "plan.md#r1-public-surface-and-tier",
              "heading": "R1. Public surface and tier",
              "paragraph": "`webtools-http` gains these public declarations in `com.spartanlabs.webtools.http`, all Stable Core, each with KDoc (`@param`, `@return`, `@throws`) that names its tier:",
              "hash": "2c5cbbf202d9"
            }
          },
          {
            "text": "Four companion factories build them, `trusted { }`, `trusted(from) { }`, `untrusted { }` and `untrusted(from) { }`, returning the precise type; the builders copy every collection and validate at `build()`.",
            "ref": "plan.md#r3-the-policies",
            "label": "§R3",
            "excerpt": {
              "ref": "plan.md#r3-the-policies",
              "heading": "R3. The policies",
              "paragraph": "`Trusted`: `pinnedHosts: Set<String>` (empty = any host), `allowCleartext: Boolean` (false = `https` only), `settings`. No address filtering, no name rule, no port rule. · `Untrusted`: `allowedPorts: Set<Int>?` (default `{80, 443, 8080, 8443}`, `null` = any port), `extraDeniedRanges: Set<CidrBlock>`, `extraDeniedHostSuffixes: Set<String>`, `mixedAnswers: MixedAnswers` (default `Reject`), `settings`. There is no parameter that lowers its address protection. · Each is built by a nested `Builder` (`var`s of the read-only types seeded from a `from` value; `var settings` and `fun …",
              "hash": "8562decba1e3"
            }
          },
          {
            "text": "No policy instance is held in the base's companion or any static field, so class initialisation cannot deadlock between the base and a subclass.",
            "ref": "plan.md#62-the-public-configuration-shape",
            "excerpt": {
              "ref": "plan.md#62-the-public-configuration-shape",
              "heading": "6.2 The public configuration shape",
              "paragraph": "Named defaults plus a required `from`. `TransportSettings.TrustedDefaults` and `UntrustedDefaults` are companion values; `TransportSettings(from) { }` derives a modified copy. `from` has no default value, so the Stable Core signature has no `$default` bridge in the ABI; the one default in this unit is the trailing `= {}` of `trusted(configure)` and `untrusted(configure)`. · Four policy factories in `OutboundUrlPolicy`'s companion: `trusted(configure = {})`, `trusted(from, configure)`, `untrusted(configure = {})`, `untrusted(from, configure)`. They return the precise `Trusted`/`Untrusted`, so …",
              "hash": "369e32a834ce"
            }
          },
          {
            "text": "Equality is hand-written over every field, and `toString()` prints the kind and counts, never a pinned host, a range or a suffix.",
            "ref": "plan.md#r3-the-policies",
            "label": "§R3",
            "excerpt": {
              "ref": "plan.md#r3-the-policies",
              "heading": "R3. The policies",
              "paragraph": "`Trusted`: `pinnedHosts: Set<String>` (empty = any host), `allowCleartext: Boolean` (false = `https` only), `settings`. No address filtering, no name rule, no port rule. · `Untrusted`: `allowedPorts: Set<Int>?` (default `{80, 443, 8080, 8443}`, `null` = any port), `extraDeniedRanges: Set<CidrBlock>`, `extraDeniedHostSuffixes: Set<String>`, `mixedAnswers: MixedAnswers` (default `Reject`), `settings`. There is no parameter that lowers its address protection. · Each is built by a nested `Builder` (`var`s of the read-only types seeded from a `from` value; `var settings` and `fun …",
              "hash": "8562decba1e3"
            }
          },
          {
            "text": "An `internal` `withSettings` derives a policy with other settings, for the call facades.",
            "ref": "plan.md#62-the-public-configuration-shape",
            "excerpt": {
              "ref": "plan.md#62-the-public-configuration-shape",
              "heading": "6.2 The public configuration shape",
              "paragraph": "Named defaults plus a required `from`. `TransportSettings.TrustedDefaults` and `UntrustedDefaults` are companion values; `TransportSettings(from) { }` derives a modified copy. `from` has no default value, so the Stable Core signature has no `$default` bridge in the ABI; the one default in this unit is the trailing `= {}` of `trusted(configure)` and `untrusted(configure)`. · Four policy factories in `OutboundUrlPolicy`'s companion: `trusted(configure = {})`, `trusted(from, configure)`, `untrusted(configure = {})`, `untrusted(from, configure)`. They return the precise `Trusted`/`Untrusted`, so …",
              "hash": "369e32a834ce"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write `OutboundUrlPolicy.kt`: the pair, its builders, the four factories and the canonicalisation helpers.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/OutboundUrlPolicy.kt"
            ],
            "check": "Procedure P2",
            "link": "plan.md#73-outboundurlpolicykt"
          }
        ],
        "tests": [
          "Level 2 (component) `TrustedPolicyComponentTest`, `UntrustedPolicyComponentTest` and `PolicyWithSettingsComponentTest`, with a per-property equality test; the Level 1 (gating) consumer `OutboundUrlPolicyConsumerGatingTest`, compiled with no opt-in"
        ],
        "risks": [
          "Stable Core from the first release: after 1.0.0 a renamed property, a changed default or a third subclass is a major bump (§10)"
        ],
        "source": "plan.md R1, R3, §6.1, §6.2, §7.3",
        "link": "plan.md#73-outboundurlpolicykt"
      }
    },
    {
      "id": "trusted",
      "group": "Code",
      "name": "`Trusted`: scheme, user-info, pinning and cleartext",
      "system": "webtools-http-url-policy",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing guards the endpoints a consumer picks itself: no rule pins a hop to the endpoint's host, and nothing keeps a request off cleartext `http`, which `Url.parse` admits beside `https`. `HostNotPinned` exists with nothing to raise it.",
        "evidence": [
          "common/UrlParsing.kt:63-66 — `https` and `http` both accepted (baseline)",
          "common/RefusalReason.kt:42 — `HostNotPinned`, never raised (baseline)",
          "../research/outbound-url-policy.md Consideration 5"
        ],
        "source": "../architecture.md §6.3.1; ../research/outbound-url-policy.md Considerations",
        "link": "../architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement"
      },
      "options": [
        {
          "id": "https-only",
          "name": "`https` only unless `allowCleartext` is set, optional `pinnedHosts`, and no address, name or port rule",
          "verdict": "accepted",
          "why": "The current `Trusted` providers all serve `https` and credentials travel in headers, so cleartext is a deliberate choice. A redirect that leaves the pinned hosts is refused with `HostNotPinned`; fetching from a private address is what `Trusted` is for.",
          "source": "../architecture.md §6.3.1; ../research/outbound-url-policy.md Consideration 5",
          "link": "../architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement"
        },
        {
          "id": "cleartext-default",
          "name": "`http` and `https` alike by default",
          "verdict": "declined",
          "why": "Credentials travel in headers, so cleartext should be switched on deliberately; the fixtures' `Trusted` factory against a plain `mockwebserver3` sets it.",
          "source": "../research/outbound-url-policy.md Consideration 5",
          "link": "../research/outbound-url-policy.md#5-trusted-default-for-cleartext-needs-a-call"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`Trusted` holds `pinnedHosts`, `allowCleartext` and its settings, with no address, name or port rule.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s2-the-policies",
            "excerpt": {
              "ref": "final-implementation.md#p2--s2-the-policies",
              "heading": "P2 — S2: the policies",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 3s` (`OutboundUrlPolicy.kt` compiled for both targets; no `e:`, no new `w:`). · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 15s`. The result XML lists the S2 classes: `OutboundUrlPolicyConsumerGatingTest` (5 tests, in `gatingTest`'s results), `TrustedPolicyComponentTest` (13), `UntrustedPolicyComponentTest` (18), `PolicyWithSettingsComponentTest` (4); …",
              "hash": "83b2528f1be1"
            }
          },
          {
            "text": "Pinned hosts are canonicalised through `canonicalHost` and match `Url.host` exactly; a refused entry fails `build()`.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s2-the-policies",
            "excerpt": {
              "ref": "final-implementation.md#p2--s2-the-policies",
              "heading": "P2 — S2: the policies",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 3s` (`OutboundUrlPolicy.kt` compiled for both targets; no `e:`, no new `w:`). · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 15s`. The result XML lists the S2 classes: `OutboundUrlPolicyConsumerGatingTest` (5 tests, in `gatingTest`'s results), `TrustedPolicyComponentTest` (13), `UntrustedPolicyComponentTest` (18), `PolicyWithSettingsComponentTest` (4); …",
              "hash": "83b2528f1be1"
            }
          },
          {
            "text": "`UrlGuard`'s `Trusted` rules refuse cleartext as `Scheme`, an unpinned host as `HostNotPinned` and a downgrade as `InsecureRedirect`, in that order.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "`Trusted` refuses no header and no address.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One rule set of the outbound-URL policy, judged per hop in common code.",
        "statements": [
          {
            "text": "`Trusted` holds `pinnedHosts` (empty means any host), `allowCleartext` (false means `https` only) and its settings, with no address, name or port rule.",
            "ref": "plan.md#r3-the-policies",
            "label": "§R3",
            "excerpt": {
              "ref": "plan.md#r3-the-policies",
              "heading": "R3. The policies",
              "paragraph": "`Trusted`: `pinnedHosts: Set<String>` (empty = any host), `allowCleartext: Boolean` (false = `https` only), `settings`. No address filtering, no name rule, no port rule. · `Untrusted`: `allowedPorts: Set<Int>?` (default `{80, 443, 8080, 8443}`, `null` = any port), `extraDeniedRanges: Set<CidrBlock>`, `extraDeniedHostSuffixes: Set<String>`, `mixedAnswers: MixedAnswers` (default `Reject`), `settings`. There is no parameter that lowers its address protection. · Each is built by a nested `Builder` (`var`s of the read-only types seeded from a `from` value; `var settings` and `fun …",
              "hash": "8562decba1e3"
            }
          },
          {
            "text": "Pinned hosts are canonicalised through `canonicalHost` and match `Url.host` by exact string equality; an entry it refuses fails `build()` with a constant message.",
            "ref": "plan.md#63-pinned-hosts-and-suffixes-are-canonicalised-by-canonicalhost",
            "excerpt": {
              "ref": "plan.md#63-pinned-hosts-and-suffixes-are-canonicalised-by-canonicalhost",
              "heading": "6.3 Pinned hosts and suffixes are canonicalised by `canonicalHost`",
              "paragraph": "A `Trusted` pin matches by exact string equality of the canonical entry with `Url.host`.",
              "hash": "18a2cd227eac"
            }
          },
          {
            "text": "Per hop the guard refuses `http` without `allowCleartext` as `Scheme`, a host outside a non-empty pin set as `HostNotPinned`, and an `https` to `http` redirect as `InsecureRedirect` unless the settings allow it; the earlier rule wins.",
            "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
            "excerpt": {
              "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
              "heading": "6.5 The host rules and the per-hop check",
              "paragraph": "`HostRules.classify(host, extraSuffixes): HostVerdict` is total on any string (it is called directly in tests with text that `Url.parse` never lets through). Letters are folded ASCII-only first (`HttpSyntax.asciiLower`; never `lowercase()`). Then, in order, the first rule that applies decides:",
              "hash": "ca6c096b2c61"
            }
          },
          {
            "text": "`Trusted` never refuses a header or an address.",
            "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
            "excerpt": {
              "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
              "heading": "6.5 The host rules and the per-hop check",
              "paragraph": "`HostRules.classify(host, extraSuffixes): HostVerdict` is total on any string (it is called directly in tests with text that `Url.parse` never lets through). Letters are folded ASCII-only first (`HttpSyntax.asciiLower`; never `lowercase()`). Then, in order, the first rule that applies decides:",
              "hash": "ca6c096b2c61"
            }
          }
        ],
        "steps": [
          {
            "id": "S2",
            "what": "Write `Trusted` and its builder, with the pin canonicalisation.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/OutboundUrlPolicy.kt"
            ],
            "check": "Procedure P2",
            "link": "plan.md#73-outboundurlpolicykt"
          },
          {
            "id": "S3",
            "what": "Write the `Trusted` rules of `UrlGuard`.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UrlGuard.kt"
            ],
            "check": "Procedure P3",
            "link": "plan.md#77-guardrelaxationkt-and-urlguardkt"
          }
        ],
        "tests": [
          "Level 2 (component) `TrustedPolicyComponentTest` and `TrustedGuardComponentTest`; Level 4a (deterministic) `GuardReasonOrderDeterministicTest` for the order of the rules"
        ],
        "risks": [
          "Pinned-host confusion: a pin matches only the host text as written, so `api.example.com.` with its trailing dot is a different host (§10)"
        ],
        "source": "plan.md R3, §6.3, §6.5",
        "link": "plan.md#65-the-host-rules-and-the-per-hop-check"
      }
    },
    {
      "id": "untrusted",
      "group": "Code",
      "name": "`Untrusted`, with `CidrBlock` and `MixedAnswers`",
      "system": "webtools-http-url-policy",
      "where": "common/ (new)",
      "gap": {
        "kind": "risk",
        "summary": "An administrator-supplied URL can point anywhere: at cloud metadata, a private host, any port of a third-party host. A name can resolve to public and private addresses at once, and a credential header would reach a host its owner never chose. `HttpHeaders` marks sensitive entries, and nothing refuses a request for one.",
        "evidence": [
          "common/HttpHeaders.kt:78 — `Entry.isSensitive`, flagged or a credential name (baseline)",
          "../research/outbound-url-policy.md pitfall 10 — ports; F11 — mixed answers; Consideration 4 — credentials",
          "../impact.md §8 — units 5 and 6 build the reading of the sensitive flag",
          "research/bypass-vectors.md §4 G3, G4 — what no table can close: a network's own NAT64 prefix and a service on the deployer's own public address, which `extraDeniedRanges` is for"
        ],
        "source": "../research/outbound-url-policy.md Considerations 2 to 4; ../architecture.md §6.3.1",
        "link": "../research/outbound-url-policy.md#considerations"
      },
      "options": [
        {
          "id": "untrusted-rules",
          "name": "Ports 80, 443, 8080 and 8443 by default (`null` for any), extra denied ranges as `CidrBlock`s and extra denied host suffixes, a mixed answer rejected whole by default, a sensitive-flagged or credential header refused as `SensitiveHeader`",
          "verdict": "accepted",
          "why": "Every parameter only adds denials. Rejecting a mixed answer is deterministic whatever its order and keeps the socket guard a pure backstop; header stripping protects only redirects, while the first hop already goes to a host the key's owner never chose. `CidrBlock.parse` returns a `Result`, and `MixedAnswers` is an enum, a consumer-chosen input.",
          "source": "../architecture.md §6.3.1, §9; ../research/outbound-url-policy.md Considerations 2 to 4",
          "link": "../architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement"
        },
        {
          "id": "any-port",
          "name": "Any port by default",
          "verdict": "declined",
          "why": "An administrator-supplied URL would turn the bot into a port scanner of third-party hosts, and the failure kind tells an open port from a closed one; `allowedPorts = null` still allows any port.",
          "source": "../research/outbound-url-policy.md Consideration 3, pitfall 10",
          "link": "../research/outbound-url-policy.md#3-default-ports-for-untrusted-needs-a-call"
        },
        {
          "id": "web-ports",
          "name": "Ports 80 and 443 only",
          "verdict": "declined",
          "why": "Not chosen: the four-port set is the research's recommendation, a judgement and not evidence (Advocate ships five ports, `safeurl` and `ssrf_filter` any).",
          "source": "../research/outbound-url-policy.md Consideration 3",
          "link": "../research/outbound-url-policy.md#3-default-ports-for-untrusted-needs-a-call"
        },
        {
          "id": "drop-default",
          "name": "Drop the denied addresses by default and connect to an allowed one",
          "verdict": "declined",
          "why": "The majority choice among the guards read, and safe, but the answer's order would decide the connect and a socket-guard refusal would no longer mean that something bypassed the resolver guard. It ships as `MixedAnswers.DropDenied`, for a consumer to choose.",
          "source": "../research/outbound-url-policy.md Consideration 2, F11",
          "link": "../research/outbound-url-policy.md#2-mixed-publicprivate-answers-reject-or-drop-needs-a-call"
        },
        {
          "id": "strip-only",
          "name": "Send credential headers and rely on cross-origin stripping",
          "verdict": "declined",
          "why": "Stripping protects only redirects: the first hop of an `Untrusted` call already goes to a host the key's owner never chose.",
          "source": "../research/outbound-url-policy.md Consideration 4",
          "link": "../research/outbound-url-policy.md#4-refusing-credentials-on-untrusted-requests-needs-a-call"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`Untrusted` defaults to ports 80, 443, 8080 and 8443 and takes extra denied ranges and suffixes; nothing lowers its address protection.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s2-the-policies",
            "excerpt": {
              "ref": "final-implementation.md#p2--s2-the-policies",
              "heading": "P2 — S2: the policies",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 3s` (`OutboundUrlPolicy.kt` compiled for both targets; no `e:`, no new `w:`). · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 15s`. The result XML lists the S2 classes: `OutboundUrlPolicyConsumerGatingTest` (5 tests, in `gatingTest`'s results), `TrustedPolicyComponentTest` (13), `UntrustedPolicyComponentTest` (18), `PolicyWithSettingsComponentTest` (4); …",
              "hash": "83b2528f1be1"
            }
          },
          {
            "text": "`MixedAnswers` is the enum `Reject` and `DropDenied`, and the guard's answer judgement follows it.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "`CidrBlock.parse` accepts only strict blocks and fails with one constant message per kind, the kinds as plan Amendment 1 states them.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
            "excerpt": {
              "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
              "heading": "P2 — S1: settings, `CidrBlock`, `StrictIp`",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 13s`; the only compiler line is `w: JS Environment Not Selected`, no opt-in diagnostic. · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 22s`. The result XML lists the S1 classes: `TransportSettingsComponentTest` (23 tests), `CidrBlockComponentTest` (7), `StrictIpDeterministicTest` (6), `StrictIpUrlAgreementDeterministicTest` (2), `CidrBlockParseDeterministicTest` (6); 0 …",
              "hash": "5792c44174a8"
            }
          },
          {
            "text": "A sensitive-flagged or credential header is refused as `SensitiveHeader`.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "An extra IPv4 block also matches an embedded IPv4, and the KDoc of `extraDeniedRanges` names the cases no table closes.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One rule set of the outbound-URL policy, in common code; every parameter only adds a denial.",
        "statements": [
          {
            "text": "`Untrusted` allows ports 80, 443, 8080 and 8443 by default (`null` for any port) and takes extra denied ranges as `CidrBlock`s and extra denied host suffixes; no parameter lowers its address protection.",
            "ref": "plan.md#r3-the-policies",
            "label": "§R3",
            "excerpt": {
              "ref": "plan.md#r3-the-policies",
              "heading": "R3. The policies",
              "paragraph": "`Trusted`: `pinnedHosts: Set<String>` (empty = any host), `allowCleartext: Boolean` (false = `https` only), `settings`. No address filtering, no name rule, no port rule. · `Untrusted`: `allowedPorts: Set<Int>?` (default `{80, 443, 8080, 8443}`, `null` = any port), `extraDeniedRanges: Set<CidrBlock>`, `extraDeniedHostSuffixes: Set<String>`, `mixedAnswers: MixedAnswers` (default `Reject`), `settings`. There is no parameter that lowers its address protection. · Each is built by a nested `Builder` (`var`s of the read-only types seeded from a `from` value; `var settings` and `fun …",
              "hash": "8562decba1e3"
            }
          },
          {
            "text": "`MixedAnswers` is an enum, `Reject` by default and `DropDenied` by choice: `Reject` refuses a whole answer at its first denied address, `DropDenied` keeps the allowed addresses in order.",
            "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
            "excerpt": {
              "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
              "heading": "6.5 The host rules and the per-hop check",
              "paragraph": "`HostRules.classify(host, extraSuffixes): HostVerdict` is total on any string (it is called directly in tests with text that `Url.parse` never lets through). Letters are folded ASCII-only first (`HttpSyntax.asciiLower`; never `lowercase()`). Then, in order, the first rule that applies decides:",
              "hash": "ca6c096b2c61"
            }
          },
          {
            "text": "`CidrBlock.parse` accepts exactly a strict address and a decimal prefix, refuses a block with a bit set after the prefix, and fails with a `Result` whose constant message never echoes the text.",
            "ref": "plan.md#r4-cidrblock",
            "label": "§R4",
            "excerpt": {
              "ref": "plan.md#r4-cidrblock",
              "heading": "R4. `CidrBlock`",
              "paragraph": "`CidrBlock.parse(text)` accepts exactly `<address>/<prefix>`: the address strict dotted-decimal or strict IPv6 (no brackets, no zone); the prefix 0..32 or 0..128 in decimal with no sign and no leading zero (except `0`); a block with a bit set after the prefix is refused, not masked. A failure is `Result.failure(IllegalArgumentException(...))` with one constant message per kind of problem that never echoes `text`. `toString()` is the canonical block (`10.0.0.0/8`, `fd00::/8`). Equality is on family, bytes and prefix.",
              "hash": "dd6ae6fe36c8"
            }
          },
          {
            "text": "A request carrying a sensitive-flagged or credential header is refused as `SensitiveHeader`, read from `HttpHeaders.Entry.isSensitive`.",
            "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
            "excerpt": {
              "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
              "heading": "6.5 The host rules and the per-hop check",
              "paragraph": "`HostRules.classify(host, extraSuffixes): HostVerdict` is total on any string (it is called directly in tests with text that `Url.parse` never lets through). Letters are folded ASCII-only first (`HttpSyntax.asciiLower`; never `lowercase()`). Then, in order, the first rule that applies decides:",
              "hash": "ca6c096b2c61"
            }
          },
          {
            "text": "An extra IPv4 block also matches the IPv4 embedded in an IPv4-mapped or NAT64 address, and the KDoc of `extraDeniedRanges` names the two cases no table can close: a network's own NAT64 prefix and the deployer's own public address.",
            "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
            "excerpt": {
              "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
              "heading": "6.5 The host rules and the per-hop check",
              "paragraph": "`HostRules.classify(host, extraSuffixes): HostVerdict` is total on any string (it is called directly in tests with text that `Url.parse` never lets through). Letters are folded ASCII-only first (`HttpSyntax.asciiLower`; never `lowercase()`). Then, in order, the first rule that applies decides:",
              "hash": "ca6c096b2c61"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write `CidrBlock` and `MixedAnswers`.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/CidrBlock.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/MixedAnswers.kt"
            ],
            "check": "Procedure P2",
            "link": "plan.md#72-mixedanswerskt-and-cidrblockkt"
          },
          {
            "id": "S2",
            "what": "Write `Untrusted` and its builder.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/OutboundUrlPolicy.kt"
            ],
            "check": "Procedure P2",
            "link": "plan.md#73-outboundurlpolicykt"
          },
          {
            "id": "S3",
            "what": "Write the `Untrusted` rules of `UrlGuard`: ports, addresses, the header rule and the answer judgement.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UrlGuard.kt"
            ],
            "check": "Procedure P3",
            "link": "plan.md#77-guardrelaxationkt-and-urlguardkt"
          }
        ],
        "tests": [
          "Level 2 (component) `CidrBlockComponentTest`, `UntrustedPolicyComponentTest`, `UntrustedGuardComponentTest` and `AnswerJudgementComponentTest`; Level 4a (deterministic) `CidrBlockParseDeterministicTest`"
        ],
        "risks": [
          "False denies by design: carrier-grade NAT, 6to4 and Teredo hosts and any request with a credential header are refused; `Trusted` is the policy for them (§10)"
        ],
        "source": "plan.md R3, R4, §6.5, §7.2, §7.3",
        "link": "plan.md#r3-the-policies"
      }
    },
    {
      "id": "transport-settings",
      "group": "Code",
      "name": "`TransportSettings`, one default set per policy",
      "system": "webtools-http-url-policy",
      "where": "common/ (new)",
      "gap": {
        "kind": "missing",
        "summary": "No type holds the caps and timeouts a fetch runs under, so nothing says how long, how large or how many hops an `Untrusted` fetch may take. OkHttp's own defaults are no call timeout and 20 redirects.",
        "evidence": [
          "webtools-http/api/webtools-http.api — no settings or policy type in the dump (baseline)",
          "../research/http-transport.md, Proposed defaults — OkHttp's defaults beside the proposed ones"
        ],
        "source": "../research/README.md § Where the findings differ; ../architecture.md §6.3.1",
        "link": "../research/README.md#where-the-findings-differ-and-which-view-holds"
      },
      "options": [
        {
          "id": "in-policy",
          "name": "Settings inside each policy, built by a factory with a builder over an `internal` constructor: `Trusted` 30 s call, 10 s socket timeouts, 5 MiB, 5 redirects, 16 and 5 concurrent; `Untrusted` 15 s, 5 s, 2 MiB, 3, 8 and 2; both a 4 s idle TTL and `https` to `http` refused",
          "verdict": "accepted",
          "why": "`Untrusted`'s caps and timeouts are part of what makes it untrusted, and a new builder property can be added in a feature release with no binary break. Every number is a judgement default.",
          "source": "../architecture.md §6.3.1, §12; ../research/README.md § Where the findings differ",
          "link": "../architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement"
        },
        {
          "id": "beside-policy",
          "name": "Settings beside the policy, in the transport's constructor (the transport findings' shape)",
          "verdict": "declined",
          "why": "The caps would come apart from the policy whose protection they are part of; the transport takes the policy instead.",
          "source": "../research/README.md § Where the findings differ",
          "link": "../research/README.md#where-the-findings-differ-and-which-view-holds"
        },
        {
          "id": "defaulted-constructor",
          "name": "A public constructor with defaulted parameters (the research's provisional sketch)",
          "verdict": "declined",
          "why": "Adding a defaulted parameter breaks binary compatibility for Kotlin callers, and `@IntroducedAt` needs Kotlin 2.3; no public `data class` either.",
          "source": "../research/api-shape-and-failures.md F7; ../architecture.md §9",
          "link": "../research/api-shape-and-failures.md#f7-evolvability-defaulted-parameters-are-a-binary-break-for-kotlin-callers-and-the-220-toolbox-has-two-safe-patterns-established-consensus"
        },
        {
          "id": "defaulted-from",
          "name": "A default value for `from` in `TransportSettings(from) { }`",
          "verdict": "declined",
          "why": "It would add a `$default` bridge to a Stable Core signature; `from` is required and the two named default sets are passed explicitly.",
          "source": "plan.md §6.2",
          "link": "plan.md#62-the-public-configuration-shape"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "statements": [
          {
            "text": "`TransportSettings` holds the ten settings over an `internal` constructor with a nested `Builder`, which carries `@WebToolsDsl`.",
            "state": "amended",
            "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
            "excerpt": {
              "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
              "heading": "P2 — S1: settings, `CidrBlock`, `StrictIp`",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 13s`; the only compiler line is `w: JS Environment Not Selected`, no opt-in diagnostic. · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 22s`. The result XML lists the S1 classes: `TransportSettingsComponentTest` (23 tests), `CidrBlockComponentTest` (7), `StrictIpDeterministicTest` (6), `StrictIpUrlAgreementDeterministicTest` (2), `CidrBlockParseDeterministicTest` (6); 0 …",
              "hash": "5792c44174a8"
            }
          },
          {
            "text": "`TrustedDefaults` and `UntrustedDefaults` hold the plan's numbers, each asserted by a test, and `TransportSettings(from) { }` derives a modified copy.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
            "excerpt": {
              "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
              "heading": "P2 — S1: settings, `CidrBlock`, `StrictIp`",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 13s`; the only compiler line is `w: JS Environment Not Selected`, no opt-in diagnostic. · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 22s`. The result XML lists the S1 classes: `TransportSettingsComponentTest` (23 tests), `CidrBlockComponentTest` (7), `StrictIpDeterministicTest` (6), `StrictIpUrlAgreementDeterministicTest` (2), `CidrBlockParseDeterministicTest` (6); 0 …",
              "hash": "5792c44174a8"
            }
          },
          {
            "text": "`build()` checks every bound with a constant message naming the property.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
            "excerpt": {
              "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
              "heading": "P2 — S1: settings, `CidrBlock`, `StrictIp`",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 13s`; the only compiler line is `w: JS Environment Not Selected`, no opt-in diagnostic. · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 22s`. The result XML lists the S1 classes: `TransportSettingsComponentTest` (23 tests), `CidrBlockComponentTest` (7), `StrictIpDeterministicTest` (6), `StrictIpUrlAgreementDeterministicTest` (2), `CidrBlockParseDeterministicTest` (6); 0 …",
              "hash": "5792c44174a8"
            }
          },
          {
            "text": "The KDoc carries the table of default numbers, says that `callTimeout` spans every hop, and calls a changed default a behaviour change.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
            "excerpt": {
              "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
              "heading": "P2 — S1: settings, `CidrBlock`, `StrictIp`",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 13s`; the only compiler line is `w: JS Environment Not Selected`, no opt-in diagnostic. · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 22s`. The result XML lists the S1 classes: `TransportSettingsComponentTest` (23 tests), `CidrBlockComponentTest` (7), `StrictIpDeterministicTest` (6), `StrictIpUrlAgreementDeterministicTest` (2), `CidrBlockParseDeterministicTest` (6); 0 …",
              "hash": "5792c44174a8"
            }
          }
        ],
        "changes": [
          {
            "what": "`TransportSettings.Builder` carries `@WebToolsDsl`, so a nested `settings { }` block reaches the outer policy builder only through its label.",
            "why": "Plan Amendment 3 makes the module's builders one DSL. With the marker's line removed, `WebToolsDslComponentTest` fails naming `TransportSettings$Builder`.",
            "when": "S8 (plan Amendment 3)",
            "ref": "final-implementation.md#s8--the-webtoolsdsl-marker-plan-amendment-3"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One configuration type of the outbound-URL policy, in common code.",
        "statements": [
          {
            "text": "`TransportSettings` holds ten settings, the four timeouts, the decoded-body cap, the redirect cap, `allowHttpsToHttp`, the two concurrency limits and the idle-connection TTL, over an `internal` constructor with a nested `Builder`.",
            "ref": "plan.md#r2-transportsettings",
            "label": "§R2",
            "excerpt": {
              "ref": "plan.md#r2-transportsettings",
              "heading": "R2. `TransportSettings`",
              "paragraph": "Ten settings, as `public val`s: `callTimeout`, `connectTimeout`, `readTimeout`, `writeTimeout` (`kotlin.time.Duration`), `maxResponseBytes` (`Long`, decoded bytes), `maxRedirects` (`Int`), `allowHttpsToHttp` (`Boolean`), `maxConcurrentRequests`, `maxConcurrentRequestsPerHost` (`Int`), `idleConnectionTtl` (`Duration`). · Two default sets, as companion values, and the factory `TransportSettings(from, configure)` as the only way to derive a modified copy:",
              "hash": "b1493f4e6b58"
            }
          },
          {
            "text": "Two named default sets, `TrustedDefaults` and `UntrustedDefaults`, carry the plan's numbers, and `TransportSettings(from) { }` is the only way to derive a modified copy.",
            "ref": "plan.md#62-the-public-configuration-shape",
            "excerpt": {
              "ref": "plan.md#62-the-public-configuration-shape",
              "heading": "6.2 The public configuration shape",
              "paragraph": "Named defaults plus a required `from`. `TransportSettings.TrustedDefaults` and `UntrustedDefaults` are companion values; `TransportSettings(from) { }` derives a modified copy. `from` has no default value, so the Stable Core signature has no `$default` bridge in the ABI; the one default in this unit is the trailing `= {}` of `trusted(configure)` and `untrusted(configure)`. · Four policy factories in `OutboundUrlPolicy`'s companion: `trusted(configure = {})`, `trusted(from, configure)`, `untrusted(configure = {})`, `untrusted(from, configure)`. They return the precise `Trusted`/`Untrusted`, so …",
              "hash": "369e32a834ce"
            }
          },
          {
            "text": "`build()` validates every bound with a constant message that names the property and never echoes a value, so no value can trip OkHttp 5.5.0's own limits.",
            "ref": "plan.md#r2-transportsettings",
            "label": "§R2",
            "excerpt": {
              "ref": "plan.md#r2-transportsettings",
              "heading": "R2. `TransportSettings`",
              "paragraph": "Ten settings, as `public val`s: `callTimeout`, `connectTimeout`, `readTimeout`, `writeTimeout` (`kotlin.time.Duration`), `maxResponseBytes` (`Long`, decoded bytes), `maxRedirects` (`Int`), `allowHttpsToHttp` (`Boolean`), `maxConcurrentRequests`, `maxConcurrentRequestsPerHost` (`Int`), `idleConnectionTtl` (`Duration`). · Two default sets, as companion values, and the factory `TransportSettings(from, configure)` as the only way to derive a modified copy:",
              "hash": "b1493f4e6b58"
            }
          },
          {
            "text": "The KDoc states every default number, that `callTimeout` spans all hops, and that a changed default is a behaviour change.",
            "ref": "plan.md#r2-transportsettings",
            "label": "§R2",
            "excerpt": {
              "ref": "plan.md#r2-transportsettings",
              "heading": "R2. `TransportSettings`",
              "paragraph": "Ten settings, as `public val`s: `callTimeout`, `connectTimeout`, `readTimeout`, `writeTimeout` (`kotlin.time.Duration`), `maxResponseBytes` (`Long`, decoded bytes), `maxRedirects` (`Int`), `allowHttpsToHttp` (`Boolean`), `maxConcurrentRequests`, `maxConcurrentRequestsPerHost` (`Int`), `idleConnectionTtl` (`Duration`). · Two default sets, as companion values, and the factory `TransportSettings(from, configure)` as the only way to derive a modified copy:",
              "hash": "b1493f4e6b58"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write `TransportSettings.kt`: the type, its builder, the two default sets and the factory.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/TransportSettings.kt"
            ],
            "check": "Procedure P2",
            "link": "plan.md#71-transportsettingskt"
          }
        ],
        "tests": [
          "Level 2 (component) `TransportSettingsComponentTest`: each default asserted, every bound, and a per-property equality test"
        ],
        "risks": [
          "Every number is a judgement default; after 1.0.0 a changed one is a documented behaviour change (§10)"
        ],
        "source": "plan.md R2, §6.2, §7.1",
        "link": "plan.md#r2-transportsettings"
      }
    },
    {
      "id": "address-table",
      "group": "Code",
      "name": "`AddressRanges`, the IANA deny table",
      "system": "webtools-http-url-policy",
      "where": "common/ (new, `internal`)",
      "gap": {
        "kind": "risk",
        "summary": "Nothing judges an address. The JDK's `InetAddress` predicates miss 16 of the 26 non-public addresses tried (CGNAT, documentation, benchmarking, `fc00::/7`, NAT64, 6to4 and others), and a hand-written range list that omits entries is one of the three mistakes behind the published bypasses.",
        "evidence": [
          "../research/outbound-url-policy.md F7 — the predicates' gaps, identical on JDK 11, 17, 21 and 23",
          "../research/outbound-url-policy.md F10 — the published bypasses",
          "../research/outbound-url-policy.md pitfall 12 — the registries changed in 2024 and 2025",
          "research/iana-special-purpose-rows.md F1 to F3 — both registries unchanged since 2025-10-09: 25 IPv4 rows holding 26 prefixes, 25 IPv6 rows",
          "research/bypass-vectors.md §4 G1, G2 — two published targets pass a table built from the registries alone: the Azure platform endpoint `168.63.129.16` and an ISATAP address inside `2000::/3`"
        ],
        "source": "../research/outbound-url-policy.md F7; ../architecture.md §6.3.1",
        "link": "../research/outbound-url-policy.md#f7-the-deny-table-follows-the-iana-registries-and-inetaddress-predicates-miss-most-of-it-measured--documented"
      },
      "options": [
        {
          "id": "iana-table",
          "name": "A pure function over address bytes in common code: every IPv4 registry row plus multicast denied; IPv6 denied outside `2000::/3`, with the special blocks inside it denied; IPv4-mapped and NAT64 well-known addresses judged by their embedded IPv4; 6to4 and Teredo denied wholesale",
          "verdict": "accepted",
          "why": "Derived from the registries and testable row by row. The default-deny IPv6 rule keeps a new special block outside `2000::/3` denied with no entry to forget, and judging NAT64 by its IPv4 keeps DNS64 networks working. Measured to pass the same tests on JVM and `js`.",
          "source": "../architecture.md §6.3.1, §6.9; ../research/outbound-url-policy.md F7, F8",
          "link": "../architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement"
        },
        {
          "id": "jdk-predicates",
          "name": "The JDK's `InetAddress` predicates",
          "verdict": "declined",
          "why": "They miss 16 of 26 non-public forms, and they are JVM types where the classifier is common code.",
          "source": "../architecture.md §6.9",
          "link": "../architecture.md#69-common-code-mechanism-per-type"
        },
        {
          "id": "embedded-wholesale",
          "name": "All NAT64, 6to4 and Teredo space denied wholesale (as Smokescreen does)",
          "verdict": "declined",
          "why": "Safe, but it would also refuse the DNS64-synthesised addresses of public hosts: the NAT64 well-known prefix is the one place the choice changes availability.",
          "source": "../research/outbound-url-policy.md F7",
          "link": "../research/outbound-url-policy.md#f7-the-deny-table-follows-the-iana-registries-and-inetaddress-predicates-miss-most-of-it-measured--documented"
        },
        {
          "id": "registry-only",
          "name": "A table built from the IANA registries alone",
          "verdict": "declined",
          "why": "Two published targets pass it: the Azure platform endpoint `168.63.129.16` and an ISATAP address inside `2000::/3`. Both are denied by rules that are not registry rows, which the snapshot comment tells a refresh to keep.",
          "source": "plan.md §6.4; research/bypass-vectors.md §4",
          "link": "plan.md#64-the-address-table-addressranges"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`AddressRanges.deny` judges 4- and 16-byte addresses and denies any other length.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "The IPv4 list holds the 26 registry prefixes nested first, multicast and `168.63.129.16/32`: every row's first and last address is denied with its class.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "IPv6 is denied outside `2000::/3`, with the special blocks, 6to4, Teredo and ISATAP denied inside it and mapped and NAT64 addresses judged by their IPv4; the public controls stay allowed.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "The dated snapshot comment and refresh procedure stand above the table, naming the two rules that are not registry rows.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "One pure function of the outbound-URL policy's classifier, in common code.",
        "statements": [
          {
            "text": "`AddressRanges.deny` is a pure function over 4- or 16-byte addresses that returns the matched row's class, or `null` when the address is allowed; any other length is denied.",
            "ref": "plan.md#64-the-address-table-addressranges",
            "excerpt": {
              "ref": "plan.md#64-the-address-table-addressranges",
              "heading": "6.4 The address table (`AddressRanges`)",
              "paragraph": "`AddressRanges.deny(address: ByteArray): DeniedRange?` returns `null` (allowed) or the matched row's class. It is a pure function over 4- or 16-byte addresses; any other length is denied (`unusual-length`).",
              "hash": "d8ca37aaa534"
            }
          },
          {
            "text": "IPv4 is a deny list: the 26 registry prefixes, nested rows first so each reports its own class, plus multicast `224.0.0.0/4` and the Azure platform endpoint `168.63.129.16/32`.",
            "ref": "plan.md#64-the-address-table-addressranges",
            "excerpt": {
              "ref": "plan.md#64-the-address-table-addressranges",
              "heading": "6.4 The address table (`AddressRanges`)",
              "paragraph": "`AddressRanges.deny(address: ByteArray): DeniedRange?` returns `null` (allowed) or the matched row's class. It is a pure function over 4- or 16-byte addresses; any other length is denied (`unusual-length`).",
              "hash": "d8ca37aaa534"
            }
          },
          {
            "text": "IPv6 is denied outside `2000::/3`; inside it the registry's special blocks, 6to4 and Teredo wholesale and ISATAP interface identifiers are denied, while IPv4-mapped and NAT64 well-known addresses are judged by their embedded IPv4.",
            "ref": "plan.md#64-the-address-table-addressranges",
            "excerpt": {
              "ref": "plan.md#64-the-address-table-addressranges",
              "heading": "6.4 The address table (`AddressRanges`)",
              "paragraph": "`AddressRanges.deny(address: ByteArray): DeniedRange?` returns `null` (allowed) or the matched row's class. It is a pure function over 4- or 16-byte addresses; any other length is denied (`unusual-length`).",
              "hash": "d8ca37aaa534"
            }
          },
          {
            "text": "A dated snapshot comment with the refresh procedure stands above the table and names the two rules that are not registry rows.",
            "ref": "plan.md#64-the-address-table-addressranges",
            "excerpt": {
              "ref": "plan.md#64-the-address-table-addressranges",
              "heading": "6.4 The address table (`AddressRanges`)",
              "paragraph": "`AddressRanges.deny(address: ByteArray): DeniedRange?` returns `null` (allowed) or the matched row's class. It is a pure function over 4- or 16-byte addresses; any other length is denied (`unusual-length`).",
              "hash": "d8ca37aaa534"
            }
          }
        ],
        "steps": [
          {
            "id": "S3",
            "what": "Write `AddressRanges.kt` with the snapshot comment.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/AddressRanges.kt"
            ],
            "check": "Procedure P3",
            "link": "plan.md#76-addressrangeskt"
          }
        ],
        "tests": [
          "Level 4a (deterministic) `AddressRangesRowsDeterministicTest`, `AddressRangesControlsDeterministicTest` and `PublishedBypassVectorsDeterministicTest`"
        ],
        "risks": [
          "IANA drift: a new IPv4 row or a new row inside `2000::/3` needs the refresh procedure, and the two rules that are not registry rows must survive it (§10)"
        ],
        "source": "plan.md R5, §6.4, §7.6",
        "link": "plan.md#64-the-address-table-addressranges"
      }
    },
    {
      "id": "host-rules",
      "group": "Code",
      "name": "`StrictIp` and `HostRules`, the host-text rules",
      "system": "webtools-http-url-policy",
      "where": "common/ (new, `internal`)",
      "gap": {
        "kind": "risk",
        "summary": "`Url` keeps digits-and-dots and hex-looking hosts as written (`127.1`, `0x7f.1`, `2130706433`) for the policy to judge. The JDK reads such forms leniently and libc reads `0177` as octal, so the same text names different machines to different parsers: the bug class of the node `ip`, `netmask` and Python `ipaddress` advisories.",
        "evidence": [
          "common/UrlHost.kt:12 — a name of digits, dots and hex letters passes as written (baseline)",
          "../impact.md §8 — what unit 3 leaves for unit 5",
          "../research/outbound-url-policy.md F3, F10",
          "research/bypass-vectors.md §3.7 — host text that never came through `Url.parse` (`LOCALHOST`, `0X7F.1`, an embedded space or tab) falls through the rules as a name unless they are total on any text"
        ],
        "source": "../research/outbound-url-policy.md, Host rules; ../architecture.md §6.3.1",
        "link": "../research/outbound-url-policy.md#host-rules-strings-after-okhttp-canonicalisation"
      },
      "options": [
        {
          "id": "strict-forms",
          "name": "Strict dotted-decimal and strict IPv6 text only; every lenient numeric form, and a last label of digits or `0x` hex (the WHATWG \"ends in a number\" rule), refused as `HostForm`; single labels, `localhost`, `local`, `internal`, `home.arpa`, `localdomain`, their subdomains and the policy's suffixes refused as `ReservedName`",
          "verdict": "accepted",
          "why": "Refusing non-canonical forms removes the parser-disagreement class of bypass; in a differential fuzz of 2.7 million strings the strict parser accepted nothing the JDK rejects. The reserved-name list is a judgement default.",
          "source": "../architecture.md §6.3.1, §12; ../research/outbound-url-policy.md F8, Host rules",
          "link": "../architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement"
        },
        {
          "id": "lenient-reading",
          "name": "Read leading zeros as decimal (the JDK) or as octal (libc)",
          "verdict": "declined",
          "why": "Either reading disagrees with the other parser; refusing the forms makes the choice irrelevant.",
          "source": "../research/outbound-url-policy.md, Rejected options",
          "link": "../research/outbound-url-policy.md#rejected-options"
        },
        {
          "id": "one-grammar",
          "name": "One IP-literal grammar for the module: `Url`'s host parsing moved onto `StrictIp`",
          "verdict": "declined",
          "why": "This unit changes no `Url` code: `StrictIp` stands beside `UrlHost.kt`, and a Level 4a (deterministic) test pins the two to the same IPv6 literal forms.",
          "wins": "As a follow-up, leaving one implementation.",
          "source": "plan.md §2, §4; impact.md §7",
          "link": "impact.md#7-follow-ups-this-unit-leaves"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`StrictIp` is self-contained and `UrlHost.kt` is unchanged; the agreement test holds.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
            "excerpt": {
              "ref": "final-implementation.md#p2--s1-settings-cidrblock-strictip",
              "heading": "P2 — S1: settings, `CidrBlock`, `StrictIp`",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs` — `BUILD SUCCESSFUL in 13s`; the only compiler line is `w: JS Environment Not Selected`, no opt-in diagnostic. · `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 22s`. The result XML lists the S1 classes: `TransportSettingsComponentTest` (23 tests), `CidrBlockComponentTest` (7), `StrictIpDeterministicTest` (6), `StrictIpUrlAgreementDeterministicTest` (2), `CidrBlockParseDeterministicTest` (6); 0 …",
              "hash": "5792c44174a8"
            }
          },
          {
            "text": "`HostRules.classify` is total on any string, ASCII-folded, first rule deciding; it stays linear in the input over 64k- and 1M-character inputs.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "Lenient numeric forms and a numeric last label are refused as `HostForm`.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "The reserved names and the policy's suffixes are refused as `ReservedName`.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Two pure classifiers of the outbound-URL policy, in common code.",
        "statements": [
          {
            "text": "`StrictIp` parses strict dotted-decimal and strict IPv6 text only and is self-contained: `Url`'s host parsing is unchanged, and a test pins the two to the same IPv6 literal forms.",
            "ref": "plan.md#2-current-state",
            "excerpt": {
              "ref": "plan.md#2-current-state",
              "heading": "2. Current state",
              "paragraph": "Baseline. `master` at `ad0ae63`; the branch holds only the research commits. Units 1 (`build-logic`, #61), 2 (`java-floor`, #47), 3 (`http-core`, #62) and 4 (`test-support`, #63) have landed.",
              "hash": "d5204a1e4137"
            }
          },
          {
            "text": "`HostRules.classify` is total on any string, folds letters ASCII-only, and applies its rules in order, the first that applies deciding.",
            "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
            "excerpt": {
              "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
              "heading": "6.5 The host rules and the per-hop check",
              "paragraph": "`HostRules.classify(host, extraSuffixes): HostVerdict` is total on any string (it is called directly in tests with text that `Url.parse` never lets through). Letters are folded ASCII-only first (`HttpSyntax.asciiLower`; never `lowercase()`). Then, in order, the first rule that applies decides:",
              "hash": "ca6c096b2c61"
            }
          },
          {
            "text": "Every lenient numeric form, and a last label of digits or `0x` hex, is refused as `HostForm`, which removes the parser-disagreement class of bypass.",
            "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
            "excerpt": {
              "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
              "heading": "6.5 The host rules and the per-hop check",
              "paragraph": "`HostRules.classify(host, extraSuffixes): HostVerdict` is total on any string (it is called directly in tests with text that `Url.parse` never lets through). Letters are folded ASCII-only first (`HttpSyntax.asciiLower`; never `lowercase()`). Then, in order, the first rule that applies decides:",
              "hash": "ca6c096b2c61"
            }
          },
          {
            "text": "Single labels, `localhost`, `local`, `internal`, `home.arpa`, `localdomain`, their subdomains and the policy's own suffixes are refused as `ReservedName`.",
            "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
            "excerpt": {
              "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
              "heading": "6.5 The host rules and the per-hop check",
              "paragraph": "`HostRules.classify(host, extraSuffixes): HostVerdict` is total on any string (it is called directly in tests with text that `Url.parse` never lets through). Letters are folded ASCII-only first (`HttpSyntax.asciiLower`; never `lowercase()`). Then, in order, the first rule that applies decides:",
              "hash": "ca6c096b2c61"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Write `StrictIp.kt`.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/StrictIp.kt"
            ],
            "check": "Procedure P2",
            "link": "plan.md#74-strictipkt"
          },
          {
            "id": "S3",
            "what": "Write `HostRules.kt`.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/HostRules.kt"
            ],
            "check": "Procedure P3",
            "link": "plan.md#75-hostruleskt"
          }
        ],
        "tests": [
          "Level 4a (deterministic) `StrictIpDeterministicTest`, `StrictIpUrlAgreementDeterministicTest` and `HostRulesDirectInputDeterministicTest`; Level 4c (non-functional) `ClassifierLinearTimeNonFunctionalTest` and `ClassifierGarbageNonFunctionalTest`"
        ],
        "risks": [],
        "source": "plan.md R5, §2, §6.5, §7.4, §7.5",
        "link": "plan.md#65-the-host-rules-and-the-per-hop-check"
      }
    },
    {
      "id": "url-guard",
      "group": "Code",
      "name": "`UrlGuard` and `GuardRelaxation`",
      "system": "webtools-http-url-policy",
      "where": "common/ (new, `internal`)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing applies a policy to a hop or to an address, and nothing lets a test run `Untrusted` against a loopback server without a loosening knob on the main artifact.",
        "evidence": [
          "../research/outbound-url-policy.md, The test seam",
          "../research/outbound-url-policy.md pitfall 9 — Smokescreen, Advocate, `safeurl` and `request-filtering-agent` all ship a public loosening knob"
        ],
        "source": "../research/outbound-url-policy.md, The test seam; ../architecture.md §6.3.1",
        "link": "../research/outbound-url-policy.md#the-test-seam"
      },
      "options": [
        {
          "id": "internal-relaxation",
          "name": "`UrlGuard(policy, relaxation)` with `checkHop` and `checkAddress`, and an `internal` `GuardRelaxation` whose one relaxed value, `LoopbackForTests`, exempts exactly `localhost`, `127.0.0.1`, `[::1]`, `127.0.0.0/8`, `::1` and the port rule for those hosts, reachable only through the fixtures",
          "verdict": "accepted",
          "why": "Level 3 (integration) tests can run `Untrusted` against a loopback `mockwebserver3` with every other rule live, and the main artifact has no loosening knob.",
          "source": "../architecture.md §6.3.1; ../research/outbound-url-policy.md, The test seam",
          "link": "../architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement"
        },
        {
          "id": "public-relaxation",
          "name": "A public `allowLoopback` or address-rules parameter",
          "verdict": "declined",
          "why": "It lets a consumer switch the guard off, which a library that sells `Untrusted` as a guarantee should not.",
          "source": "../research/outbound-url-policy.md pitfall 9",
          "link": "../research/outbound-url-policy.md#pitfalls"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`UrlGuard` checks a hop, a request, a redirect target, an address and an answer, returning refusals as values.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "`GuardRefusal` carries the reason and, for `DeniedAddress` only, the range class.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "The rules apply in the plan's order, pinned by the reason-order table.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "`GuardRelaxation.LoopbackForTests` exempts exactly the plan's loopback hosts, addresses and ports, with every other rule live.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "Nothing in the classifier logs, and the secrecy test finds no address, range, suffix or pinned host in a message or `toString()`.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The outbound-URL policy's per-hop and per-address checks, `internal` to common code.",
        "statements": [
          {
            "text": "`UrlGuard(policy, relaxation)` checks a hop, a request, a resolved redirect target, an address and a whole resolver answer, and returns a refusal as a value, never throwing.",
            "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
            "excerpt": {
              "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
              "heading": "6.5 The host rules and the per-hop check",
              "paragraph": "`HostRules.classify(host, extraSuffixes): HostVerdict` is total on any string (it is called directly in tests with text that `Url.parse` never lets through). Letters are folded ASCII-only first (`HttpSyntax.asciiLower`; never `lowercase()`). Then, in order, the first rule that applies decides:",
              "hash": "ca6c096b2c61"
            }
          },
          {
            "text": "A refusal carries its reason and, for `DeniedAddress` only, the range class, for the one log line the transport writes.",
            "ref": "plan.md#r7-logging-secrecy-and-documentation",
            "label": "§R7",
            "excerpt": {
              "ref": "plan.md#r7-logging-secrecy-and-documentation",
              "heading": "R7. Logging, secrecy and documentation",
              "paragraph": "No classifier or guard logs a refusal; the site that creates the `RefusedByPolicy` failure logs it once: the transport (unit 6), or the call facade (unit 9) for its pre-check. The only log line this unit adds is `GuardedDns`'s `DEBUG` line under `DropDenied`. · No exception message, `toString()` or log line of this unit holds an address, a range, a suffix or a pinned host. A refusal carries a coarse reason and, for `DeniedAddress` only, the range *class*. · KDoc on every public declaration; line comments on the table's snapshot and on each non-obvious rule; `README.md` line 11 names the …",
              "hash": "d9ff7cecd218"
            }
          },
          {
            "text": "The rules apply in one order, scheme, user-info, host form, IP literal, reserved name, port, pin and downgrade, and the earlier refusal wins.",
            "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
            "excerpt": {
              "ref": "plan.md#65-the-host-rules-and-the-per-hop-check",
              "heading": "6.5 The host rules and the per-hop check",
              "paragraph": "`HostRules.classify(host, extraSuffixes): HostVerdict` is total on any string (it is called directly in tests with text that `Url.parse` never lets through). Letters are folded ASCII-only first (`HttpSyntax.asciiLower`; never `lowercase()`). Then, in order, the first rule that applies decides:",
              "hash": "ca6c096b2c61"
            }
          },
          {
            "text": "`GuardRelaxation.LoopbackForTests`, `internal` and reachable only from the fixtures and this module's tests, exempts exactly `localhost`, `127.0.0.0/8`, `::1` and the port rule for those hosts; every other rule stays live.",
            "ref": "plan.md#66-guardrelaxation",
            "excerpt": {
              "ref": "plan.md#66-guardrelaxation",
              "heading": "6.6 `GuardRelaxation`",
              "paragraph": "`GuardRelaxation.None` changes nothing. `GuardRelaxation.LoopbackForTests` exempts exactly:",
              "hash": "d7bbd65c19aa"
            }
          },
          {
            "text": "No classifier or guard logs a refusal, and no message or `toString()` holds an address, a range, a suffix or a pinned host.",
            "ref": "plan.md#r7-logging-secrecy-and-documentation",
            "label": "§R7",
            "excerpt": {
              "ref": "plan.md#r7-logging-secrecy-and-documentation",
              "heading": "R7. Logging, secrecy and documentation",
              "paragraph": "No classifier or guard logs a refusal; the site that creates the `RefusedByPolicy` failure logs it once: the transport (unit 6), or the call facade (unit 9) for its pre-check. The only log line this unit adds is `GuardedDns`'s `DEBUG` line under `DropDenied`. · No exception message, `toString()` or log line of this unit holds an address, a range, a suffix or a pinned host. A refusal carries a coarse reason and, for `DeniedAddress` only, the range *class*. · KDoc on every public declaration; line comments on the table's snapshot and on each non-obvious rule; `README.md` line 11 names the …",
              "hash": "d9ff7cecd218"
            }
          }
        ],
        "steps": [
          {
            "id": "S3",
            "what": "Write `GuardRelaxation.kt` and `UrlGuard.kt` with `GuardRefusal`, `RedirectAdmission` and `AnswerVerdict`.",
            "files": [
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/GuardRelaxation.kt",
              "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/UrlGuard.kt"
            ],
            "check": "Procedure P3",
            "link": "plan.md#77-guardrelaxationkt-and-urlguardkt"
          }
        ],
        "tests": [
          "Level 2 (component) `TrustedGuardComponentTest`, `UntrustedGuardComponentTest`, `GuardRelaxationComponentTest`, `RedirectAdmissionComponentTest` and `AnswerJudgementComponentTest`; Level 4a (deterministic) `GuardReasonOrderDeterministicTest`; Level 4c (non-functional) `PolicySecrecyNonFunctionalTest`"
        ],
        "risks": [],
        "source": "plan.md R5, R7, §6.5, §6.6, §7.7",
        "link": "plan.md#77-guardrelaxationkt-and-urlguardkt"
      }
    },
    {
      "id": "jvm-guards",
      "group": "Code",
      "name": "`GuardedDns`, `GuardedSocketFactory` and the marker exceptions",
      "system": "webtools-http-url-policy",
      "where": "jvm/ (new, `internal`)",
      "gap": {
        "kind": "risk",
        "summary": "A URL check alone does not stop a name that resolves into private space or rebinds, and a resolver guard alone misses every IP-literal host, since OkHttp 5.5.0 never consults `Dns` for one; with native redirects only a socket guard stopped a hop to `169.254.169.254`. `jvm/` holds only the platform actuals of the clock and the log.",
        "evidence": [
          "jvm/ — `EpochMillis.jvm.kt` and `Log.jvm.kt` only (baseline)",
          "webtools-http/build.gradle.kts:54 — `okhttp-jvm` 5.5.0, an `implementation` dependency of `jvmMain` (baseline)",
          "../research/outbound-url-policy.md F1, F4, F5, F9",
          "research/jvm-enforcement.md F1 — OkHttp 5.5.0's `Dns` adds a record-based `newCall`; a guard that forwards it is bypassed by a record-capable delegate",
          "research/jvm-enforcement.md F4 — the default factory's `Socket()` consults the JVM-wide SOCKS setting on every connect, which `Proxy.NO_PROXY` on the client does not close (read from source, not yet measured)"
        ],
        "source": "../research/README.md § Outbound-URL policy; ../architecture.md §6.3.1, §7.3",
        "link": "../research/README.md#outbound-url-policy"
      },
      "options": [
        {
          "id": "three-layers",
          "name": "Three layers: the per-hop pre-check, a resolver guard that judges every resolved address, and a socket-factory backstop on every connect attempt, with two marker exceptions carrying the reason and `Proxy.NO_PROXY` for `Untrusted`",
          "verdict": "accepted",
          "why": "Every layer alone leaks (measured on 39 host forms). The socket factory is the version-independent backstop for a newer OkHttp 5.x, and a proxy would resolve names itself and bypass both guards; `Trusted` keeps OkHttp's default proxy selection.",
          "source": "../architecture.md §6.3.1, §7.3",
          "link": "../architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement"
        },
        {
          "id": "single-guard",
          "name": "A resolver guard only, or a network interceptor or `EventListener` as the guard",
          "verdict": "declined",
          "why": "A resolver guard misses every IP-literal host; interceptors run after the connect and a listener cannot veto.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "through-proxy",
          "name": "`Untrusted` through a proxy",
          "verdict": "declined",
          "why": "With an HTTP proxy the target name never reaches the resolver guard, a SOCKS proxy bypasses the socket factory too, and the client cannot verify what the proxy reaches.",
          "wins": "Where egress must go through a proxy: that proxy has to be the guard, as Smokescreen is.",
          "source": "../architecture.md §4; ../research/outbound-url-policy.md F6",
          "link": "../architecture.md#4-non-goals"
        },
        {
          "id": "forward-newcall",
          "name": "A resolver guard that forwards `newCall` to its delegate, or delegates with `by`",
          "verdict": "declined",
          "why": "OkHttp 5.5.0's `Dns` gained a record-based `newCall`; a forwarded one lets a record-capable delegate, such as DNS-over-HTTPS, resolve past the guard, while the inherited default routes it through the guard's own `lookup`.",
          "source": "plan.md §6.7; research/jvm-enforcement.md F1",
          "link": "plan.md#67-the-jvm-enforcement"
        },
        {
          "id": "client-no-proxy",
          "name": "`proxy(Proxy.NO_PROXY)` on the client over the default socket factory",
          "verdict": "declined",
          "why": "The default factory's `Socket()` consults a JVM-wide SOCKS setting on every connect; only a socket built on `Socket(Proxy.NO_PROXY)` keeps it away, so the guard creates its own sockets and has no delegate.",
          "source": "plan.md §6.7; research/jvm-enforcement.md F4",
          "link": "plan.md#67-the-jvm-enforcement"
        },
        {
          "id": "connect-exception",
          "name": "A refusal thrown as a `ConnectException`",
          "verdict": "declined",
          "why": "OkHttp re-wraps a `ConnectException` with the address in its message; the markers extend `UnknownHostException` and `IOException` with constant messages.",
          "source": "plan.md §6.7",
          "link": "plan.md#67-the-jvm-enforcement"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "`GuardedDns` overrides `lookup` only and inherits `newCall`: a delegate whose own `newCall` counts its calls saw one `lookup` and no `newCall`, and an empty answer becomes a constant-message `UnknownHostException`.",
            "state": "as-planned",
            "ref": "final-implementation.md#p5--s5-the-jvm-guards",
            "excerpt": {
              "ref": "final-implementation.md#p5--s5-the-jvm-guards",
              "heading": "P5 — S5: the JVM guards",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm` (also `--rerun-tasks`) — `BUILD SUCCESSFUL`, no compiler `w:`. · `./gradlew :webtools-http:componentTest` — `BUILD SUCCESSFUL`; the XML lists `GuardedDnsComponentTest` (14 tests), `GuardedSocketFactoryComponentTest` (3), `GuardedSocketComponentTest` (5), `PolicyRefusalMarkersComponentTest` (8), `PolicyEnforcementComponentTest` (3); 0 failures. The inherited-`newCall` tests passed (the first measurement of research gap 3): the guard's inherited `newCall` over a stub answering `[8.8.8.8, 10.0.0.5]` delivered only the `8.8.8.8` record under …",
              "hash": "ddf153b33fc0"
            }
          },
          {
            "text": "`GuardedSocketFactory` has no delegate and its sockets extend `Socket(Proxy.NO_PROXY)`: with a JVM-wide SOCKS setting the default factory's socket, and a client with `proxy(Proxy.NO_PROXY)`, reached the fake SOCKS listener, while the guarded socket connected straight to the target; the four other overloads throw.",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--level-3-integration-and-the-jdk-based-level-4a-deterministic-tests",
            "excerpt": {
              "ref": "final-implementation.md#p6--level-3-integration-and-the-jdk-based-level-4a-deterministic-tests",
              "heading": "P6 — Level 3 (integration) and the JDK-based Level 4a (deterministic) tests",
              "paragraph": "`./gradlew :webtools-http:componentTest :webtools-http:integrationTest :webtools-http:deterministicTest` — `BUILD SUCCESSFUL in 9s`; the XML lists `GuardedSocketLoopbackIntegrationTest` (3 tests), `GuardedSocketSocksIntegrationTest` (1), `PolicyEnforcementOkHttpIntegrationTest` (6), `JdkPredicateGapDeterministicTest` (2), `AddressRangesJdkSupersetDeterministicTest` (2), `StrictIpJdkDifferentialDeterministicTest` (2), `OkHttpLiteralRegexDeterministicTest` (2); 0 failures. The three source-read claims, on JDK 23.0.2: · (i) SOCKS control: yes — with `socksProxyHost=127.0.0.1`, …",
              "hash": "207e26681373"
            }
          },
          {
            "text": "The two markers carry the refusal with constant messages and no stack, and the socket guard's refusals reached the caller with no `ConnectException` anywhere in the tree.",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--level-3-integration-and-the-jdk-based-level-4a-deterministic-tests",
            "excerpt": {
              "ref": "final-implementation.md#p6--level-3-integration-and-the-jdk-based-level-4a-deterministic-tests",
              "heading": "P6 — Level 3 (integration) and the JDK-based Level 4a (deterministic) tests",
              "paragraph": "`./gradlew :webtools-http:componentTest :webtools-http:integrationTest :webtools-http:deterministicTest` — `BUILD SUCCESSFUL in 9s`; the XML lists `GuardedSocketLoopbackIntegrationTest` (3 tests), `GuardedSocketSocksIntegrationTest` (1), `PolicyEnforcementOkHttpIntegrationTest` (6), `JdkPredicateGapDeterministicTest` (2), `AddressRangesJdkSupersetDeterministicTest` (2), `StrictIpJdkDifferentialDeterministicTest` (2), `OkHttpLiteralRegexDeterministicTest` (2); 0 failures. The three source-read claims, on JDK 23.0.2: · (i) SOCKS control: yes — with `socksProxyHost=127.0.0.1`, …",
              "hash": "207e26681373"
            }
          },
          {
            "text": "`UrlGuard.enforcement(baseDns)` gives `Untrusted` the guarded resolver, socket factory and `Proxy.NO_PROXY`, and `Trusted` filters nothing.",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--level-3-integration-and-the-jdk-based-level-4a-deterministic-tests",
            "excerpt": {
              "ref": "final-implementation.md#p6--level-3-integration-and-the-jdk-based-level-4a-deterministic-tests",
              "heading": "P6 — Level 3 (integration) and the JDK-based Level 4a (deterministic) tests",
              "paragraph": "`./gradlew :webtools-http:componentTest :webtools-http:integrationTest :webtools-http:deterministicTest` — `BUILD SUCCESSFUL in 9s`; the XML lists `GuardedSocketLoopbackIntegrationTest` (3 tests), `GuardedSocketSocksIntegrationTest` (1), `PolicyEnforcementOkHttpIntegrationTest` (6), `JdkPredicateGapDeterministicTest` (2), `AddressRangesJdkSupersetDeterministicTest` (2), `StrictIpJdkDifferentialDeterministicTest` (2), `OkHttpLiteralRegexDeterministicTest` (2); 0 failures. The three source-read claims, on JDK 23.0.2: · (i) SOCKS control: yes — with `socksProxyHost=127.0.0.1`, …",
              "hash": "207e26681373"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The outbound-URL policy's JVM enforcement, `internal` to `jvmMain`; nothing OkHttp-typed leaves it.",
        "statements": [
          {
            "text": "`GuardedDns` overrides `lookup` only and inherits `newCall`, so a record-capable delegate cannot bypass it; it judges every answer under `MixedAnswers` and turns an empty answer into a constant-message `UnknownHostException`.",
            "ref": "plan.md#67-the-jvm-enforcement",
            "excerpt": {
              "ref": "plan.md#67-the-jvm-enforcement",
              "heading": "6.7 The JVM enforcement",
              "paragraph": "`GuardedDns` overrides `lookup` only. It never uses `by delegate` and never overrides `newCall`: the inherited default returns a `LookupDnsCall` over `this`, which `RouteSelector` resolves through the guard's own `lookup`; a forwarded `newCall` would let a record-capable delegate (DNS-over-HTTPS, Android's resolver) bypass the guard. Delegate exceptions pass through unchanged. An empty delegate answer becomes `UnknownHostException` with a constant message (OkHttp's own empty-answer message holds the host). It never returns an empty list. A refusal is `PolicyRefusedUnknownHost`, an …",
              "hash": "ab5f652a3cea"
            }
          },
          {
            "text": "`GuardedSocketFactory` has no delegate: its sockets extend `Socket(Proxy.NO_PROXY)` and judge every connect target, refusing an unresolved or non-Inet one, and its four other overloads fail closed.",
            "ref": "plan.md#67-the-jvm-enforcement",
            "excerpt": {
              "ref": "plan.md#67-the-jvm-enforcement",
              "heading": "6.7 The JVM enforcement",
              "paragraph": "`GuardedDns` overrides `lookup` only. It never uses `by delegate` and never overrides `newCall`: the inherited default returns a `LookupDnsCall` over `this`, which `RouteSelector` resolves through the guard's own `lookup`; a forwarded `newCall` would let a record-capable delegate (DNS-over-HTTPS, Android's resolver) bypass the guard. Delegate exceptions pass through unchanged. An empty delegate answer becomes `UnknownHostException` with a constant message (OkHttp's own empty-answer message holds the host). It never returns an empty list. A refusal is `PolicyRefusedUnknownHost`, an …",
              "hash": "ab5f652a3cea"
            }
          },
          {
            "text": "The two markers, `PolicyRefusedUnknownHost` and `PolicyRefusedIo`, carry the refusal with constant messages and no stack and are never a `ConnectException`; `policyRefusal()` finds one anywhere in a throwable's cause chain and suppressed lists.",
            "ref": "plan.md#67-the-jvm-enforcement",
            "excerpt": {
              "ref": "plan.md#67-the-jvm-enforcement",
              "heading": "6.7 The JVM enforcement",
              "paragraph": "`GuardedDns` overrides `lookup` only. It never uses `by delegate` and never overrides `newCall`: the inherited default returns a `LookupDnsCall` over `this`, which `RouteSelector` resolves through the guard's own `lookup`; a forwarded `newCall` would let a record-capable delegate (DNS-over-HTTPS, Android's resolver) bypass the guard. Delegate exceptions pass through unchanged. An empty delegate answer becomes `UnknownHostException` with a constant message (OkHttp's own empty-answer message holds the host). It never returns an empty list. A refusal is `PolicyRefusedUnknownHost`, an …",
              "hash": "ab5f652a3cea"
            }
          },
          {
            "text": "`UrlGuard.enforcement(baseDns)` gives `Untrusted` the guarded resolver, the guarded socket factory and `Proxy.NO_PROXY`, and leaves `Trusted` with OkHttp's defaults.",
            "ref": "plan.md#67-the-jvm-enforcement",
            "excerpt": {
              "ref": "plan.md#67-the-jvm-enforcement",
              "heading": "6.7 The JVM enforcement",
              "paragraph": "`GuardedDns` overrides `lookup` only. It never uses `by delegate` and never overrides `newCall`: the inherited default returns a `LookupDnsCall` over `this`, which `RouteSelector` resolves through the guard's own `lookup`; a forwarded `newCall` would let a record-capable delegate (DNS-over-HTTPS, Android's resolver) bypass the guard. Delegate exceptions pass through unchanged. An empty delegate answer becomes `UnknownHostException` with a constant message (OkHttp's own empty-answer message holds the host). It never returns an empty list. A refusal is `PolicyRefusedUnknownHost`, an …",
              "hash": "ab5f652a3cea"
            }
          }
        ],
        "steps": [
          {
            "id": "S5",
            "what": "Write the markers, `GuardedDns`, `GuardedSocketFactory`, `GuardedSocket` and `PolicyEnforcement`.",
            "files": [
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/PolicyRefusals.kt",
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/GuardedDns.kt",
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/GuardedSocketFactory.kt",
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/GuardedSocket.kt",
              "webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/PolicyEnforcement.kt"
            ],
            "check": "Procedures P5 and P6",
            "link": "plan.md#78-jvmmain"
          }
        ],
        "tests": [
          "Level 2 (component) tests of each guard over hand-written stubs; Level 3 (integration) `GuardedSocketLoopbackIntegrationTest`, `GuardedSocketSocksIntegrationTest` and `PolicyEnforcementOkHttpIntegrationTest`, loopback only"
        ],
        "risks": [
          "Three source-read claims stay unmeasured until S5: a JVM-wide SOCKS setting against `Socket(Proxy.NO_PROXY)`, a refusal's surfacing with fast fallback on, and the inherited `newCall` (§14)"
        ],
        "source": "plan.md R6, §6.7, §6.8, §7.8",
        "link": "plan.md#67-the-jvm-enforcement"
      }
    },
    {
      "id": "classifier-tests",
      "group": "Tests",
      "name": "The classifier's Level 4a tables",
      "system": "webtools-http-url-policy",
      "where": "commonTest (new, `testing.deterministic`)",
      "gap": {
        "kind": "missing",
        "summary": "No test pins an address verdict or a host form: the 13 Level 4a classes in `commonTest` cover `Url` parsing and printing, header syntax, media types, status ranges and the encoders.",
        "evidence": [
          "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/ — 13 classes, none on a policy (baseline)",
          "../research/outbound-url-policy.md, Tests this implies",
          "research/bypass-vectors.md §3 — the published vectors with the refusal each must get, and research/iana-special-purpose-rows.md, Public controls — the addresses that must stay allowed; no test carries either at the baseline"
        ],
        "source": "../research/outbound-url-policy.md, Tests this implies; ../architecture.md §11",
        "link": "../research/outbound-url-policy.md#tests-this-implies"
      },
      "options": [
        {
          "id": "tables",
          "name": "Level 4a tables in `commonTest`: every IANA row's first and last address, the public controls on both sides of every boundary, the host-form catalogue and the published bypass vectors",
          "verdict": "accepted",
          "why": "A range list that omits entries is a published bypass class, and testing every row's first and last address is how the research checked its table; the classifier is pure, so the tables need no network.",
          "source": "../architecture.md §11 unit 5; ../research/outbound-url-policy.md pitfall 4, Tests this implies",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "Every IANA row's first and last address is denied with its class and the public controls are allowed, all passing.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "249 published bypass vectors run through `Url.parse` and the guard, with 115 rows tagged F1 against a floor of 40 and 52 tagged F10 against 25.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "The reason-order table and the direct host inputs pass.",
            "state": "as-planned",
            "ref": "final-implementation.md#p3--s3-the-classification-core",
            "excerpt": {
              "ref": "final-implementation.md#p3--s3-the-classification-core",
              "heading": "P3 — S3: the classification core",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` — `BUILD SUCCESSFUL in 14s`; the only compiler line is `w: JS Environment Not Selected`. The result XML lists the S3 classes, 0 failures: `AddressRangesRowsDeterministicTest` (5 tests), `AddressRangesControlsDeterministicTest` (8), `PublishedBypassVectorsDeterministicTest` (9), `HostRulesDirectInputDeterministicTest` (5), `GuardReasonOrderDeterministicTest` (11), …",
              "hash": "97efa27724b5"
            }
          },
          {
            "text": "Deleting the Azure row failed the controls, the published vectors and the registry rows; widening the ISATAP mask failed the controls and the published vectors.",
            "state": "as-planned",
            "ref": "final-implementation.md#p9--mutation-sensitivity",
            "excerpt": {
              "ref": "final-implementation.md#p9--mutation-sensitivity",
              "heading": "P9 — mutation sensitivity",
              "paragraph": "Each file was copied to a scratch directory outside the repository, mutated, the named task run, the file copied back and `diff` against the copy printed nothing; then `componentTest`, `integrationTest` and `deterministicTest` ran green together.",
              "hash": "c4f2ff75a596"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The classifier's tables in common code, run on the JVM and compiled for `js`; the classifier is pure, so they need no network.",
        "statements": [
          {
            "text": "Every IANA row's first and last address is denied with the row's class, and the public controls on both sides of every boundary are allowed.",
            "ref": "plan.md#level-4a--deterministic",
            "label": "§9, L4a deterministic",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic",
              "heading": "Level 4a — deterministic",
              "paragraph": "`commonTest` (runs on the JVM here; the code is JVM-free and compiled for `js` by the check module):",
              "hash": "734ee552cdc5"
            }
          },
          {
            "text": "The published bypass vectors run through `Url.parse` and the guard, with floors on how many rows the host-form catalogue and the advisories tag.",
            "ref": "plan.md#level-4a--deterministic",
            "label": "§9, L4a deterministic",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic",
              "heading": "Level 4a — deterministic",
              "paragraph": "`commonTest` (runs on the JVM here; the code is JVM-free and compiled for `js` by the check module):",
              "hash": "734ee552cdc5"
            }
          },
          {
            "text": "The reason that wins when two rules apply is pinned row by row, and `HostRules` is fed text that `Url.parse` never lets through.",
            "ref": "plan.md#level-4a--deterministic",
            "label": "§9, L4a deterministic",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic",
              "heading": "Level 4a — deterministic",
              "paragraph": "`commonTest` (runs on the JVM here; the code is JVM-free and compiled for `js` by the check module):",
              "hash": "734ee552cdc5"
            }
          },
          {
            "text": "Two of the six mutation checks, the Azure row deleted and the ISATAP mask widened, must each fail these tables.",
            "ref": "plan.md#92-procedures",
            "excerpt": {
              "ref": "plan.md#92-procedures",
              "heading": "9.2 Procedures",
              "paragraph": "Every command is run from the repository root. None runs a `webtools-udp`, `webtools-scraping`, `webtools-browser` or `build-logic` suite on its own, none runs the root `build` or `test`, and none runs a `publish*` task. The one nested-build run, `:build-logic:fixturesPublicationCheck`, is a dependency of `:webtools-http:check` and runs because its inputs include `webtools-http`'s `commonMain` and `jvmMain`, which this unit changes: it exercises the change. Record each command's result in `final-implementation.md` §4 (Measurements), in the order they ran.",
              "hash": "dac527dfc170"
            }
          }
        ],
        "steps": [
          {
            "id": "S1",
            "what": "Add the `StrictIp` and `CidrBlock` Level 4a classes and the test support.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/"
            ],
            "check": "Procedure P2",
            "link": "plan.md#91-test-support-new-files-not-tests"
          },
          {
            "id": "S3",
            "what": "Add the registry-row, control, bypass-vector, direct-input and reason-order tables.",
            "files": [
              "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/"
            ],
            "check": "Procedure P3",
            "link": "plan.md#level-4a--deterministic"
          },
          {
            "id": "S6",
            "what": "Run the mutation checks against the tables.",
            "files": [],
            "check": "Procedure P9",
            "link": "plan.md#92-procedures"
          }
        ],
        "tests": [],
        "risks": [],
        "source": "plan.md R8, §9, §9.2",
        "link": "plan.md#level-4a--deterministic"
      }
    },
    {
      "id": "jvm-tests",
      "group": "Tests",
      "name": "The guards' Level 2 tests and the `jvmTest` gap and differential tests",
      "system": "webtools-http-url-policy",
      "where": "jvmTest (new)",
      "gap": {
        "kind": "missing",
        "summary": "Nothing tests a resolver or socket guard, and nothing pins how the strict parser relates to the JDK's own literal parsing or records the JDK predicates' gaps.",
        "evidence": [
          "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/ — the header, encoder and `Url`-against-`HttpUrl` differentials only (baseline)",
          "../research/outbound-url-policy.md F8 — the differential fuzz against the JDK",
          "research/jvm-enforcement.md F7 — the JDK parses a literal with no lookup only when the text is already a valid literal, and sends other text to the system resolver, so a differential can hand it only what `StrictIp` accepts"
        ],
        "source": "../research/outbound-url-policy.md, Tests this implies; ../architecture.md §11",
        "link": "../research/outbound-url-policy.md#tests-this-implies"
      },
      "options": [
        {
          "id": "stub-tests",
          "name": "Level 2 tests of the guards over stubs (a stub `Dns`, a delegate whose sockets only record the endpoint): reject-any against `DropDenied`, the markers in `suppressed`; in `jvmTest`, the `StrictIp`-against-JDK differential on a generated corpus and the JDK-predicate-gap table",
          "verdict": "accepted",
          "why": "Level 2 touches no network, and the differential pins that whatever the strict parser classifies, the JDK would connect to the same address.",
          "source": "../architecture.md §11 unit 5; ../research/outbound-url-policy.md F8, Tests this implies",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "statements": [
          {
            "text": "The guards' Level 2 tests over stubs pass, the inherited `newCall` checked by its behaviour, as plan Amendment 2 settles: the compiler's bridge method defeats the planned reflection check.",
            "state": "as-planned",
            "ref": "final-implementation.md#p5--s5-the-jvm-guards",
            "excerpt": {
              "ref": "final-implementation.md#p5--s5-the-jvm-guards",
              "heading": "P5 — S5: the JVM guards",
              "paragraph": "`./gradlew :webtools-http:compileKotlinJvm` (also `--rerun-tasks`) — `BUILD SUCCESSFUL`, no compiler `w:`. · `./gradlew :webtools-http:componentTest` — `BUILD SUCCESSFUL`; the XML lists `GuardedDnsComponentTest` (14 tests), `GuardedSocketFactoryComponentTest` (3), `GuardedSocketComponentTest` (5), `PolicyRefusalMarkersComponentTest` (8), `PolicyEnforcementComponentTest` (3); 0 failures. The inherited-`newCall` tests passed (the first measurement of research gap 3): the guard's inherited `newCall` over a stub answering `[8.8.8.8, 10.0.0.5]` delivered only the `8.8.8.8` record under …",
              "hash": "ddf153b33fc0"
            }
          },
          {
            "text": "The three loopback Level 3 tests pass and measure the SOCKS claim, the markers' surfacing with fast fallback and one lookup per new connection.",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--level-3-integration-and-the-jdk-based-level-4a-deterministic-tests",
            "excerpt": {
              "ref": "final-implementation.md#p6--level-3-integration-and-the-jdk-based-level-4a-deterministic-tests",
              "heading": "P6 — Level 3 (integration) and the JDK-based Level 4a (deterministic) tests",
              "paragraph": "`./gradlew :webtools-http:componentTest :webtools-http:integrationTest :webtools-http:deterministicTest` — `BUILD SUCCESSFUL in 9s`; the XML lists `GuardedSocketLoopbackIntegrationTest` (3 tests), `GuardedSocketSocksIntegrationTest` (1), `PolicyEnforcementOkHttpIntegrationTest` (6), `JdkPredicateGapDeterministicTest` (2), `AddressRangesJdkSupersetDeterministicTest` (2), `StrictIpJdkDifferentialDeterministicTest` (2), `OkHttpLiteralRegexDeterministicTest` (2); 0 failures. The three source-read claims, on JDK 23.0.2: · (i) SOCKS control: yes — with `socksProxyHost=127.0.0.1`, …",
              "hash": "207e26681373"
            }
          },
          {
            "text": "The JDK-based Level 4a tests pass: the predicates miss 16 of the 26 addresses, and the differential accepted 45,756 IPv4 and 46,857 IPv6 strings against floors of 25,000 and 10,000.",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--level-3-integration-and-the-jdk-based-level-4a-deterministic-tests",
            "excerpt": {
              "ref": "final-implementation.md#p6--level-3-integration-and-the-jdk-based-level-4a-deterministic-tests",
              "heading": "P6 — Level 3 (integration) and the JDK-based Level 4a (deterministic) tests",
              "paragraph": "`./gradlew :webtools-http:componentTest :webtools-http:integrationTest :webtools-http:deterministicTest` — `BUILD SUCCESSFUL in 9s`; the XML lists `GuardedSocketLoopbackIntegrationTest` (3 tests), `GuardedSocketSocksIntegrationTest` (1), `PolicyEnforcementOkHttpIntegrationTest` (6), `JdkPredicateGapDeterministicTest` (2), `AddressRangesJdkSupersetDeterministicTest` (2), `StrictIpJdkDifferentialDeterministicTest` (2), `OkHttpLiteralRegexDeterministicTest` (2); 0 failures. The three source-read claims, on JDK 23.0.2: · (i) SOCKS control: yes — with `socksProxyHost=127.0.0.1`, …",
              "hash": "207e26681373"
            }
          },
          {
            "text": "A forwarding `newCall` failed exactly the behaviour tests, and a plain `Socket()` went through the fake SOCKS listener and failed the SOCKS test.",
            "state": "as-planned",
            "ref": "final-implementation.md#p9--mutation-sensitivity",
            "excerpt": {
              "ref": "final-implementation.md#p9--mutation-sensitivity",
              "heading": "P9 — mutation sensitivity",
              "paragraph": "Each file was copied to a scratch directory outside the repository, mutated, the named task run, the file copied back and `diff` against the copy printed nothing; then `componentTest`, `integrationTest` and `deterministicTest` ran green together.",
              "hash": "c4f2ff75a596"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The JVM guards' tests, in `jvmTest`, with no network beyond loopback.",
        "statements": [
          {
            "text": "Level 2 (component) tests drive each guard over hand-written stubs, a stub `Dns` and sockets that only record the endpoint: reject-any against `DropDenied`, the markers in `suppressed`, the inherited `newCall`.",
            "ref": "plan.md#level-2--component-componenttest",
            "label": "§9, L2 component",
            "excerpt": {
              "ref": "plan.md#level-2--component-componenttest",
              "heading": "Level 2 — component (`componentTest`)",
              "paragraph": "`commonTest` (stubs are the production types themselves; nothing external is called):",
              "hash": "d63536178bd9"
            }
          },
          {
            "text": "Level 3 (integration) tests measure the guards over loopback: a guarded socket against a fake SOCKS listener, and a real `OkHttpClient` built from the enforcement bundle.",
            "ref": "plan.md#level-3--integration-integrationtest",
            "label": "§9, L3 integration",
            "excerpt": {
              "ref": "plan.md#level-3--integration-integrationtest",
              "heading": "Level 3 — integration (`integrationTest`)",
              "paragraph": "All in `jvmTest`, loopback only, no external network; every `MockWebServer` and `ServerSocket` is closed in `finally` or `@AfterTest`; every wait has a timeout; no `Thread.sleep`. A loopback `ServerSocket` is bound to `127.0.0.1` explicitly (`InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))`), never to `getLoopbackAddress()` (which may be IPv6).",
              "hash": "2d5b4c0c04f1"
            }
          },
          {
            "text": "In `jvmTest` the JDK is the oracle: the predicate-gap table, a seeded superset check, the `StrictIp`-against-JDK differential and OkHttp's literal pattern.",
            "ref": "plan.md#level-4a--deterministic",
            "label": "§9, L4a deterministic",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic",
              "heading": "Level 4a — deterministic",
              "paragraph": "`commonTest` (runs on the JVM here; the code is JVM-free and compiled for `js` by the check module):",
              "hash": "734ee552cdc5"
            }
          },
          {
            "text": "Two mutation checks, `newCall` forwarded and a plain `Socket()`, must each fail these tests.",
            "ref": "plan.md#92-procedures",
            "excerpt": {
              "ref": "plan.md#92-procedures",
              "heading": "9.2 Procedures",
              "paragraph": "Every command is run from the repository root. None runs a `webtools-udp`, `webtools-scraping`, `webtools-browser` or `build-logic` suite on its own, none runs the root `build` or `test`, and none runs a `publish*` task. The one nested-build run, `:build-logic:fixturesPublicationCheck`, is a dependency of `:webtools-http:check` and runs because its inputs include `webtools-http`'s `commonMain` and `jvmMain`, which this unit changes: it exercises the change. Record each command's result in `final-implementation.md` §4 (Measurements), in the order they ran.",
              "hash": "dac527dfc170"
            }
          }
        ],
        "steps": [
          {
            "id": "S5",
            "what": "Add the guards' Level 2 tests, then the Level 3 tests, then the JDK-based Level 4a tests, with `StubDns`.",
            "files": [
              "webtools-http/src/jvmTest/"
            ],
            "check": "Procedures P5 and P6",
            "link": "plan.md#9-test-plan"
          },
          {
            "id": "S6",
            "what": "Run the mutation checks against the guards' tests.",
            "files": [],
            "check": "Procedure P9",
            "link": "plan.md#92-procedures"
          }
        ],
        "tests": [],
        "risks": [
          "The SOCKS test changes process-wide properties: it is isolated, restores them in `finally`, and no parallel mode is enabled (§10)"
        ],
        "source": "plan.md R8, §9, §9.2",
        "link": "plan.md#9-test-plan"
      }
    },
    {
      "id": "release",
      "group": "Release",
      "name": "The ABI dump and the version",
      "system": "webtools-http-url-policy",
      "where": "webtools-http/api/webtools-http.api",
      "gap": {
        "kind": "missing",
        "summary": "`webtools-http` is new and unreleased at `1.0.0`. Its committed ABI dump, which `apiCheck` holds the build to, records the core's surface and no policy type.",
        "evidence": [
          "webtools-http/api/webtools-http.api — the dump at the baseline, no policy type",
          "../impact.md §8 — each later unit extends the dump"
        ],
        "source": "../architecture.md §9, §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "no-version",
          "name": "No version moved: `webtools-http` stays unreleased at `1.0.0` until the stack is complete, and the dump grows with the new public types",
          "verdict": "accepted",
          "why": "The units that build an unreleased module move no version.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "statements": [
          {
            "text": "The dump grew by 130 lines, additions only: 127 for the policy's public types and three for `WebToolsDsl`, all Stable Core, and no internal name, the final check run word-bounded.",
            "state": "amended",
            "ref": "final-implementation.md#p8--mechanical-rules",
            "excerpt": {
              "ref": "final-implementation.md#p8--mechanical-rules",
              "heading": "P8 — mechanical rules",
              "paragraph": "Every command printed nothing except where the rule says otherwise: no `java.`/`javax.`/`@Jvm`/`runCatching`/`lowercase`/ `uppercase`/`ignoreCase`/`removeFirst`/`removeLast`/`String.format`/`Regex(` in `commonMain` outside comments; `catch` only in `NonFatal.kt`; no `data class`; no `TODO` under `webtools-http/src`; no `OptIn` in `commonTest` or `jvmTest`; no `newCall`, `by delegate`, `: Dns by`, `SocketFactory.getDefault`, `ConnectException`, `InetAddress.getByName` or `getAllByName` in main code outside comments; `logger(` only in `Log.kt` (commonMain) and in `Log.jvm.kt` and …",
              "hash": "40de9be17b8e"
            }
          },
          {
            "text": "`apiCheck` passes inside `:webtools-http:check`, and the dump holds five `$default` bridges.",
            "state": "as-planned",
            "ref": "final-implementation.md#p7--the-floor-and-the-full-check",
            "excerpt": {
              "ref": "final-implementation.md#p7--the-floor-and-the-full-check",
              "heading": "P7 — the floor and the full check",
              "paragraph": "`./gradlew :webtools-http:verifyJavaFloor` — `BUILD SUCCESSFUL in 1s`; `webtools-http/build/reports/java-floor/verifyJavaFloor.txt` reads `java floor 11 (class-file major <= 55): 174 classes in 2 jar(s), 6 library variants in 2 metadata file(s), 3575 classes on the runtime classpath` (every class, the new ones included, at major 55 or below). · `./gradlew :webtools-http:check :webtools-http-commoncheck:check` — `BUILD SUCCESSFUL in 1m 12s`; `:build-logic:fixturesPublicationCheck` ran once (the bulk of the 1m 12s). `jvmTest` (every level together): 88 classes, 500 tests, 0 failures. Per level …",
              "hash": "9e70d73b8b45"
            }
          },
          {
            "text": "No version moved and no build file changed: the module build file, the fixtures and the other modules are untouched.",
            "state": "as-planned",
            "ref": "final-implementation.md#p10--the-unaffected-modules-and-the-diff",
            "excerpt": {
              "ref": "final-implementation.md#p10--the-unaffected-modules-and-the-diff",
              "heading": "P10 — the unaffected modules and the diff",
              "paragraph": "`git diff --stat -- webtools-udp webtools-scraping webtools-browser build-logic webtools-http/build.gradle.kts webtools-http/src/jvmTestFixtures webtools-http-commoncheck` is empty. `git status --short` shows no `Canary.kt`, no scratch copy and no `build/` directory. `README.md` is unchanged at this point (S7 changes it). No other module's suite was run.",
              "hash": "f55e9f947f69"
            }
          }
        ],
        "changes": [
          {
            "what": "The dump grew by three lines more, `WebToolsDsl`'s, last in the file: 130 in all, and no builder's lines changed.",
            "why": "Plan Amendment 3 adds the public marker; the dump records no class's annotations, so the six builders' lines stay as they were (plan §7.9).",
            "when": "S8 (plan Amendment 3)",
            "ref": "final-implementation.md#s8--the-webtoolsdsl-marker-plan-amendment-3"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "No version moves: `webtools-http` is new and stays unreleased at 1.0.0 until #55's stack is complete.",
        "statements": [
          {
            "text": "`apiDump` regenerates the ABI dump, which grows by additions only: the public types of the policy, all Stable Core, and nothing `internal`.",
            "ref": "plan.md#79-webtools-httpapiwebtools-httpapi-changed-generated",
            "excerpt": {
              "ref": "plan.md#79-webtools-httpapiwebtools-httpapi-changed-generated",
              "heading": "7.9 `webtools-http/api/webtools-http.api` (changed, generated)",
              "paragraph": "Not hand-edited: `./gradlew :webtools-http:apiDump` rewrites it, and the diff is read. Expected additions (the order BCV prints: alphabetical by class):",
              "hash": "fd80cf87650a"
            }
          },
          {
            "text": "`apiCheck` holds the build to the new dump, which holds five `$default` bridges, two of them the policy factories'.",
            "ref": "plan.md#79-webtools-httpapiwebtools-httpapi-changed-generated",
            "excerpt": {
              "ref": "plan.md#79-webtools-httpapiwebtools-httpapi-changed-generated",
              "heading": "7.9 `webtools-http/api/webtools-http.api` (changed, generated)",
              "paragraph": "Not hand-edited: `./gradlew :webtools-http:apiDump` rewrites it, and the diff is read. Expected additions (the order BCV prints: alphabetical by class):",
              "hash": "fd80cf87650a"
            }
          },
          {
            "text": "No version moves and no dependency is added, and the landing is not a release candidate.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-64-url-policy`. It exists and holds the research commits; the implementer confirms it (P1) rather than cutting it again. · Commits. Each leaves the build green. The type and the split are the final call at commit time; subject lines end with `(Issue #64)`; no attribution trailers. · `feat: add the outbound-URL policies, transport settings and the common address classifier (Issue #64)` — this `plan.md`; `webtools-http/src/commonMain/` (`TransportSettings.kt`, `MixedAnswers.kt`, `CidrBlock.kt`, `OutboundUrlPolicy.kt`, `StrictIp.kt`, `HostRules.kt`, `AddressRanges.kt`, …",
              "hash": "d5e4f033ce96"
            }
          }
        ],
        "steps": [
          {
            "id": "S4",
            "what": "Run `apiDump`, read the diff against the plan and the tier table, and run `apiCheck`.",
            "files": [
              "webtools-http/api/webtools-http.api"
            ],
            "check": "Procedure P4",
            "link": "plan.md#92-procedures"
          },
          {
            "id": "S6",
            "what": "Check the final dump for internal names in the mechanical rules.",
            "files": [
              "webtools-http/api/webtools-http.api"
            ],
            "check": "Procedure P8",
            "link": "plan.md#92-procedures"
          }
        ],
        "tests": [],
        "risks": [
          "The `$default` and sealed-class dump lines are inferred until the first `apiDump` (§3)"
        ],
        "source": "plan.md §7.9, §11",
        "link": "plan.md#79-webtools-httpapiwebtools-httpapi-changed-generated"
      }
    },
    {
      "id": "client-wiring",
      "group": "Left for later",
      "name": "Wiring the policy into a client",
      "system": "webtools-http-url-policy",
      "where": "jvm/ (unit 6, #65)",
      "gap": {
        "kind": "missing",
        "summary": "The guards need a client: something must run the per-hop check before every `Call`, install the resolver and socket guards and `Proxy.NO_PROXY` for `Untrusted`, and give the fixtures their loopback factory.",
        "evidence": [
          "../architecture.md §6.3.2 — `OkHttpTransport`, unit 6",
          "../impact.md §8 — `LoopbackTransports` reserved for unit 6",
          "research/bypass-vectors.md §5 — the vectors that belong to the transport's own tests: redirects into private space, rebinding, process proxies, credentials across an origin change, a `Location` that `HttpUrl.resolve` throws on"
        ],
        "source": "../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "unit-6",
          "name": "Left to unit 6 (#65), `OkHttpTransport`, which wires the guards into its client and adds the loopback factories to the fixtures",
          "verdict": "accepted",
          "why": "#55's decomposition scopes this unit to the policy and its guards and gives the wiring to unit 6, which this unit blocks.",
          "source": "../architecture.md §11, §6.3.2",
          "link": "../architecture.md#632-okhttp-transport-jvm"
        }
      ],
      "asBuilt": {
        "state": "pending",
        "summary": ""
      },
      "verdict": {
        "kind": "leave",
        "defense": "The wiring belongs to the transport, unit 6 (#65), which this unit blocks.",
        "statements": [
          {
            "text": "No OkHttp transport, redirect loop or failure mapping is built here: the unit provides the checks and the enforcement types, wires nothing into a client and logs no refusal.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "No OkHttp transport, redirect loop, `ExchangeGuard` or failure mapping (unit 6, `okhttp-transport`). This unit provides the checks and the enforcement types; it wires nothing into a client and logs no refusal. · No loopback test factories (`LoopbackTransports`, `webtoolsUrl`, `toMockResponse`, `enqueue(fixture)`), and nothing under `src/jvmTestFixtures` (unit 6 adds them; the fixtures-publication check asserts the fixtures' dependency list). · No public loosening knob. `GuardRelaxation.LoopbackForTests` is `internal` and reachable only from the fixtures and this module's tests; the main …",
              "hash": "c835d8de3cb6"
            }
          },
          {
            "text": "No loopback test factories and nothing under the fixtures; unit 6 adds them.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "No OkHttp transport, redirect loop, `ExchangeGuard` or failure mapping (unit 6, `okhttp-transport`). This unit provides the checks and the enforcement types; it wires nothing into a client and logs no refusal. · No loopback test factories (`LoopbackTransports`, `webtoolsUrl`, `toMockResponse`, `enqueue(fixture)`), and nothing under `src/jvmTestFixtures` (unit 6 adds them; the fixtures-publication check asserts the fixtures' dependency list). · No public loosening knob. `GuardRelaxation.LoopbackForTests` is `internal` and reachable only from the fixtures and this module's tests; the main …",
              "hash": "c835d8de3cb6"
            }
          },
          {
            "text": "Unit 6 calls the guard before every `Call`, applies the enforcement bundle to its client, and maps a marker to `RefusedByPolicy` with one `WARN`.",
            "ref": "plan.md#12-interfaces-with-sibling-units",
            "excerpt": {
              "ref": "plan.md#12-interfaces-with-sibling-units",
              "heading": "12. Interfaces with sibling units",
              "paragraph": "Names this unit takes (a sibling must not reuse them), in `com.spartanlabs.webtools.http`: · *public* — `TransportSettings` (with `Builder`, `Companion.TrustedDefaults`, `Companion.UntrustedDefaults`) and the top-level function `TransportSettings(from, configure)`; `OutboundUrlPolicy` with `Trusted`, `Untrusted`, their `Builder`s and the companion functions `trusted` and `untrusted`; `CidrBlock`; `MixedAnswers`; `WebToolsDsl` (Amendment 3). · *internal, `commonMain`* — `StrictIp`, `HostVerdict` (`Literal`, `Name`, `Refused`), `HostRules`, `DeniedRange`, `AddressRanges`, `GuardRelaxation`, …",
              "hash": "e9d954fd72e0"
            }
          }
        ],
        "steps": [],
        "tests": [],
        "risks": [],
        "source": "plan.md §4, §12",
        "link": "plan.md#12-interfaces-with-sibling-units"
      }
    }
  ],
  "impact": {
    "basis": "plan",
    "summary": "`webtools-http`, new and unreleased, gains the outbound-URL policies `Trusted` and `Untrusted` with their builders and factories, `TransportSettings` with two named default sets, `CidrBlock`, `MixedAnswers` and `WebToolsDsl`, the DSL marker on every builder of the module, all Stable Core; the classifier and the JVM guards stay `internal`. Nothing is enforced until the transport and the call facades land. No dependency is added, the three existing artifacts do not change, and no version moves.",
    "none": null,
    "release": {
      "bump": "none",
      "version": null,
      "note": "`webtools-http` 1.0.0 stays new and unreleased until #55's stack is complete; it is not a release candidate at this landing",
      "unreleased": true
    },
    "downstream": [
      {
        "who": "`io.github.spartanlaboratories:webtools-http` (new artifact)",
        "kind": "artifact",
        "system": "webtools-http-url-policy",
        "effect": "additive",
        "summary": "The main variants gain the policies and their settings, and the ABI dump grows by exactly these types; no dependency changes and the test-fixtures variant does not change.",
        "link": "impact.md#1-published-artifacts"
      },
      {
        "who": "Consumers of `webtools-http` 1.0.0, once it is released",
        "kind": "consumer",
        "system": "webtools-http-url-policy",
        "effect": "additive",
        "summary": "They can build, derive and compare `Trusted` and `Untrusted` policies; a policy reaches the network only through the transport (unit 6) and the call facades (unit 9).",
        "link": "impact.md#1-published-artifacts"
      },
      {
        "who": "Consumers of `webtools-udp`, `webtools-scraping` and `webtools-browser`",
        "kind": "consumer",
        "system": null,
        "effect": "none",
        "summary": "Nothing changes: no source, build-script or test change, and no version moves.",
        "link": "impact.md#1-published-artifacts"
      },
      {
        "who": "Repository builds",
        "kind": "repository",
        "system": null,
        "effect": "additive",
        "summary": "`./gradlew build` runs the new tests (34 classes and 228 tests for the policy, two classes for the marker), the `js` compile of the new common files, `apiCheck` against the new dump and the fixtures-publication check once more; no build file changes.",
        "link": "impact.md#build"
      },
      {
        "who": "The later units of #55: #65, #68, #69 and #70",
        "kind": "system",
        "system": "webtools-http-url-policy",
        "effect": "additive",
        "summary": "The transport (#65) calls the guard before every `Call`, applies the enforcement bundle and maps a marker to `RefusedByPolicy`; the call facades (#68) pre-check with the guard and raise concurrency with `withSettings`; the feeds (#69) build an `Untrusted` fetcher; the docs (#70) document the policies.",
        "link": "impact.md#4-sibling-units"
      }
    ],
    "adaptation": [
      {
        "who": "Unit 6, the OkHttp transport (#65)",
        "system": "webtools-http-url-policy",
        "need": "required",
        "summary": "It calls `checkRequest` before hop 1 and `admitRedirect` for each redirect target, applies `enforcement(baseDns)` with fast fallback at its default and `retryOnConnectionFailure(false)`, checks `policyRefusal()` first and logs one `WARN` per refusal.",
        "link": "plan.md#12-interfaces-with-sibling-units"
      },
      {
        "who": "Existing code in the repository",
        "system": null,
        "need": "none",
        "summary": "No class classifies an address or applies an outbound-URL policy today; `Connector` and `WebViewer` keep the architecture's verdicts. The three existing builders gain `@WebToolsDsl`, and no source or test reached an outer builder implicitly.",
        "link": "impact.md#3-adoption"
      },
      {
        "who": "The later units' builders (#65 to #70)",
        "system": "webtools-http-url-policy",
        "need": "required",
        "summary": "A builder a later unit passes to a consumer's lambda carries `@WebToolsDsl` and joins the list of `WebToolsDslComponentTest` and of the marker's KDoc.",
        "link": "impact.md#4-sibling-units"
      }
    ],
    "api": [
      {
        "surface": "`OutboundUrlPolicy`, `Trusted`, `Untrusted`, their builders and the four factories",
        "system": "webtools-http-url-policy",
        "change": "added",
        "tier": "stable-core",
        "summary": "The closed pair of outbound-URL policies; `Untrusted` has no parameter that lowers its address protection.",
        "link": "plan.md#r3-the-policies"
      },
      {
        "surface": "`TransportSettings`, its `Builder`, `TrustedDefaults`, `UntrustedDefaults` and `TransportSettings(from) { }`",
        "system": "webtools-http-url-policy",
        "change": "added",
        "tier": "stable-core",
        "summary": "Ten settings with two named default sets; a changed default is a behaviour change.",
        "link": "plan.md#r2-transportsettings"
      },
      {
        "surface": "`CidrBlock`",
        "system": "webtools-http-url-policy",
        "change": "added",
        "tier": "stable-core",
        "summary": "`parse(text)` returns a `Result`; no public accessors or `contains` yet.",
        "link": "plan.md#r4-cidrblock"
      },
      {
        "surface": "`MixedAnswers`",
        "system": "webtools-http-url-policy",
        "change": "added",
        "tier": "stable-core",
        "summary": "`Reject`, the default, and `DropDenied`.",
        "link": "plan.md#r3-the-policies"
      },
      {
        "surface": "`WebToolsDsl`, on the six builders of the module",
        "system": "webtools-http-url-policy",
        "change": "added",
        "tier": "stable-core",
        "compat": "notable",
        "summary": "One DSL for the policy, settings, headers, request and response builders: an outer builder is reached only through its label. On the three existing builders it tightens a consumer's source, which no published consumer has, as the module is unreleased.",
        "link": "impact.md#1-published-artifacts"
      }
    ],
    "conversion": [],
    "changes": [
      {
        "what": "`WebToolsDsl`, the module's DSL marker, joins the public surface, Stable Core, on the six builders.",
        "why": "Plan Amendment 3 settles the marker the plan had left open (S8); the reconciled `impact.md` §1 records it.",
        "when": "S8 (plan Amendment 3)",
        "ref": "impact.md#1-published-artifacts",
        "entry": "api.4",
        "system": "webtools-http-url-policy"
      },
      {
        "what": "The three existing builders, `HttpHeaders`, `HttpRequest` and `HttpResponse`, gain one `@WebToolsDsl` line each; no source reached an outer builder implicitly.",
        "why": "The marker covers every builder of the module (plan Amendment 3); the reconciled `impact.md` §2 and §3 record it.",
        "when": "S8 (plan Amendment 3)",
        "ref": "impact.md#3-adoption",
        "entry": "adaptation.1",
        "system": "webtools-http-url-policy"
      },
      {
        "what": "Every later unit's builder carries `@WebToolsDsl`.",
        "why": "Plan §12, as Amendment 3 amends it, keeps the DSL one across the stack; the reconciled `impact.md` §4 records it.",
        "when": "S8 (plan Amendment 3)",
        "ref": "impact.md#4-sibling-units",
        "entry": "adaptation.2",
        "system": "webtools-http-url-policy"
      },
      {
        "what": "The build's added tests as measured: 34 classes and 228 tests for the policy, and the marker's two classes; the planned time estimate is left out of the entry.",
        "why": "The reconciled `impact.md` §2 gives the measured counts; `:webtools-http:check :webtools-http-commoncheck:check` took 1 min 12 s before S8 and 59 s after it, so no single figure stands for the build's time.",
        "when": "S8, reconciled",
        "ref": "impact.md#build",
        "entry": "downstream.3"
      }
    ],
    "source": "impact.md, plan.md",
    "link": "impact.md"
  },
  "tests": {
    "base": "b46f58a",
    "updated": "2026-10-26T22:02:11Z",
    "added": [
      {
        "class": "TransportSettingsComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/TransportSettingsComponentTest.kt",
        "tests": 23,
        "rows": [
          "transport-settings"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 23,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "CidrBlockComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/CidrBlockComponentTest.kt",
        "tests": 7,
        "rows": [
          "untrusted"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "StrictIpDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/StrictIpDeterministicTest.kt",
        "tests": 6,
        "rows": [
          "host-rules",
          "classifier-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "StrictIpUrlAgreementDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/StrictIpUrlAgreementDeterministicTest.kt",
        "tests": 2,
        "rows": [
          "host-rules",
          "classifier-tests"
        ],
        "stage": "S1",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "CidrBlockParseDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/CidrBlockParseDeterministicTest.kt",
        "tests": 6,
        "rows": [
          "untrusted",
          "classifier-tests"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "TrustedPolicyComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/TrustedPolicyComponentTest.kt",
        "tests": 13,
        "rows": [
          "policy-pair",
          "trusted"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 13,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UntrustedPolicyComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/UntrustedPolicyComponentTest.kt",
        "tests": 18,
        "rows": [
          "policy-pair",
          "untrusted"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 18,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "PolicyWithSettingsComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/PolicyWithSettingsComponentTest.kt",
        "tests": 4,
        "rows": [
          "policy-pair"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "OutboundUrlPolicyConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/http/OutboundUrlPolicyConsumerGatingTest.kt",
        "tests": 5,
        "rows": [
          "policy-pair"
        ],
        "stage": "S2",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "AddressRangesRowsDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/AddressRangesRowsDeterministicTest.kt",
        "tests": 5,
        "rows": [
          "address-table",
          "classifier-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "AddressRangesControlsDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/AddressRangesControlsDeterministicTest.kt",
        "tests": 8,
        "rows": [
          "address-table",
          "classifier-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "PublishedBypassVectorsDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/PublishedBypassVectorsDeterministicTest.kt",
        "tests": 9,
        "rows": [
          "address-table",
          "host-rules",
          "classifier-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HostRulesDirectInputDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HostRulesDirectInputDeterministicTest.kt",
        "tests": 5,
        "rows": [
          "host-rules",
          "classifier-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "GuardReasonOrderDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/GuardReasonOrderDeterministicTest.kt",
        "tests": 11,
        "rows": [
          "url-guard",
          "classifier-tests"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "TrustedGuardComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/TrustedGuardComponentTest.kt",
        "tests": 9,
        "rows": [
          "trusted",
          "url-guard"
        ],
        "stage": "qa pass 1 fixes",
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UntrustedGuardComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/UntrustedGuardComponentTest.kt",
        "tests": 11,
        "rows": [
          "untrusted",
          "url-guard"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "GuardRelaxationComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/GuardRelaxationComponentTest.kt",
        "tests": 14,
        "rows": [
          "url-guard"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 14,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "RedirectAdmissionComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/RedirectAdmissionComponentTest.kt",
        "tests": 6,
        "rows": [
          "url-guard"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "AnswerJudgementComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/AnswerJudgementComponentTest.kt",
        "tests": 8,
        "rows": [
          "untrusted",
          "url-guard"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "ClassifierLinearTimeNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/ClassifierLinearTimeNonFunctionalTest.kt",
        "tests": 3,
        "rows": [
          "host-rules"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "ClassifierGarbageNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/ClassifierGarbageNonFunctionalTest.kt",
        "tests": 1,
        "rows": [
          "host-rules"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "PolicySecrecyNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/PolicySecrecyNonFunctionalTest.kt",
        "tests": 3,
        "rows": [
          "url-guard"
        ],
        "stage": "S3",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "GuardedDnsComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/GuardedDnsComponentTest.kt",
        "tests": 14,
        "rows": [
          "jvm-guards",
          "jvm-tests"
        ],
        "stage": "qa pass 1 fixes",
        "result": {
          "state": "passed",
          "passed": 14,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "GuardedSocketFactoryComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/GuardedSocketFactoryComponentTest.kt",
        "tests": 3,
        "rows": [
          "jvm-guards",
          "jvm-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "GuardedSocketComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/GuardedSocketComponentTest.kt",
        "tests": 5,
        "rows": [
          "jvm-guards",
          "jvm-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "PolicyRefusalMarkersComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/PolicyRefusalMarkersComponentTest.kt",
        "tests": 8,
        "rows": [
          "jvm-guards",
          "jvm-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "PolicyEnforcementComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/PolicyEnforcementComponentTest.kt",
        "tests": 3,
        "rows": [
          "jvm-guards",
          "jvm-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "GuardedSocketLoopbackIntegrationTest",
        "module": "webtools-http",
        "testLevel": "3",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/GuardedSocketLoopbackIntegrationTest.kt",
        "tests": 3,
        "rows": [
          "jvm-guards",
          "jvm-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "GuardedSocketSocksIntegrationTest",
        "module": "webtools-http",
        "testLevel": "3",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/GuardedSocketSocksIntegrationTest.kt",
        "tests": 1,
        "rows": [
          "jvm-guards",
          "jvm-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "PolicyEnforcementOkHttpIntegrationTest",
        "module": "webtools-http",
        "testLevel": "3",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/PolicyEnforcementOkHttpIntegrationTest.kt",
        "tests": 6,
        "rows": [
          "jvm-guards",
          "jvm-tests"
        ],
        "stage": "qa pass 1 fixes",
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "JdkPredicateGapDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/JdkPredicateGapDeterministicTest.kt",
        "tests": 2,
        "rows": [
          "address-table",
          "jvm-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "AddressRangesJdkSupersetDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/AddressRangesJdkSupersetDeterministicTest.kt",
        "tests": 2,
        "rows": [
          "address-table",
          "jvm-tests"
        ],
        "stage": "qa pass 1 fixes",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "StrictIpJdkDifferentialDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/StrictIpJdkDifferentialDeterministicTest.kt",
        "tests": 2,
        "rows": [
          "host-rules",
          "jvm-tests"
        ],
        "stage": "S5",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "OkHttpLiteralRegexDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/OkHttpLiteralRegexDeterministicTest.kt",
        "tests": 2,
        "rows": [
          "host-rules",
          "jvm-tests"
        ],
        "stage": "qa pass 1 fixes",
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "WebToolsDslConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/http/WebToolsDslConsumerGatingTest.kt",
        "tests": 3,
        "rows": [
          "policy-pair",
          "transport-settings"
        ],
        "stage": "S8",
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "WebToolsDslComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/WebToolsDslComponentTest.kt",
        "tests": 1,
        "rows": [
          "policy-pair",
          "transport-settings"
        ],
        "stage": "S8",
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      }
    ],
    "amended": [],
    "relevant": [
      {
        "class": "FixturesConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/http/testing/FixturesConsumerGatingTest.kt",
        "tests": 4,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "GatedTypesConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/gating/webtools/http/GatedTypesConsumerGatingTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "GatedTypesJvmConsumerGatingTest",
        "module": "webtools-http",
        "testLevel": "1",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/http/GatedTypesJvmConsumerGatingTest.kt",
        "tests": 2,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "CatchNonFatalComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/CatchNonFatalComponentTest.kt",
        "tests": 5,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "EpochMillisJvmComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/EpochMillisJvmComponentTest.kt",
        "tests": 1,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "FailureLeavesComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/FailureLeavesComponentTest.kt",
        "tests": 7,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "FakeHttpTransportComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/FakeHttpTransportComponentTest.kt",
        "tests": 21,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 21,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "FixtureComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/FixtureComponentTest.kt",
        "tests": 10,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 10,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "FixtureLoaderComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/FixtureLoaderComponentTest.kt",
        "tests": 9,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HttpHeadersComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/HttpHeadersComponentTest.kt",
        "tests": 12,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 12,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HttpMethodComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/HttpMethodComponentTest.kt",
        "tests": 6,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HttpRequestComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/HttpRequestComponentTest.kt",
        "tests": 9,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HttpResponseComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/HttpResponseComponentTest.kt",
        "tests": 6,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 6,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "ReasonConstantsComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/ReasonConstantsComponentTest.kt",
        "tests": 4,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "RedactionComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/RedactionComponentTest.kt",
        "tests": 13,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 13,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "RedirectHopComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/RedirectHopComponentTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "RequestBodyComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/RequestBodyComponentTest.kt",
        "tests": 4,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "RequestMatchComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/RequestMatchComponentTest.kt",
        "tests": 9,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "ResultFlatMapComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/ResultFlatMapComponentTest.kt",
        "tests": 2,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "Slf4jLogComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/Slf4jLogComponentTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "StepComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/StepComponentTest.kt",
        "tests": 7,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UpstreamErrorComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/UpstreamErrorComponentTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "VirtualClockComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/VirtualClockComponentTest.kt",
        "tests": 11,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 11,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "VirtualClockThreeClocksComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/VirtualClockThreeClocksComponentTest.kt",
        "tests": 2,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "WallClockSystemComponentTest",
        "module": "webtools-http",
        "testLevel": "2",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/component/webtools/http/WallClockSystemComponentTest.kt",
        "tests": 4,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "FixtureRecorderIntegrationTest",
        "module": "webtools-http",
        "testLevel": "3",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/testing/FixtureRecorderIntegrationTest.kt",
        "tests": 16,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 16,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "FixtureFileNamesDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/testing/FixtureFileNamesDeterministicTest.kt",
        "tests": 8,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HeaderOkHttpDifferentialDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HeaderOkHttpDifferentialDeterministicTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HttpHeaderNameDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HttpHeaderNameDeterministicTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HttpHeaderValueDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HttpHeaderValueDeterministicTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HttpMessageCodecDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/testing/HttpMessageCodecDeterministicTest.kt",
        "tests": 8,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 8,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HttpMethodTokenDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HttpMethodTokenDeterministicTest.kt",
        "tests": 2,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HttpSyntaxDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/HttpSyntaxDeterministicTest.kt",
        "tests": 4,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "MediaTypeDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/MediaTypeDeterministicTest.kt",
        "tests": 2,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "QueryPairsDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/testing/QueryPairsDeterministicTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "RecordingGateDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/testing/RecordingGateDeterministicTest.kt",
        "tests": 7,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "SensitiveNamesDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/testing/SensitiveNamesDeterministicTest.kt",
        "tests": 7,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 7,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "StatusRangeDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/StatusRangeDeterministicTest.kt",
        "tests": 2,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlAcceptDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/UrlAcceptDeterministicTest.kt",
        "tests": 2,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlBoundaryDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/UrlBoundaryDeterministicTest.kt",
        "tests": 4,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 4,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlConfusionDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/UrlConfusionDeterministicTest.kt",
        "tests": 1,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlEncoderDifferentialDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/UrlEncoderDifferentialDeterministicTest.kt",
        "tests": 1,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlEncodersDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/UrlEncodersDeterministicTest.kt",
        "tests": 5,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 5,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlIpv6DeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/UrlIpv6DeterministicTest.kt",
        "tests": 9,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 9,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlOkHttpDifferentialDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/UrlOkHttpDifferentialDeterministicTest.kt",
        "tests": 1,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlPrintingDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/UrlPrintingDeterministicTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlRejectDeterministicTest",
        "module": "webtools-http",
        "testLevel": "4a",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/UrlRejectDeterministicTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "FakeHttpTransportConcurrencyNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/testing/FakeHttpTransportConcurrencyNonFunctionalTest.kt",
        "tests": 2,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "FixturesSecrecyNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/testing/FixturesSecrecyNonFunctionalTest.kt",
        "tests": 2,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HeaderMessageSecrecyNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/HeaderMessageSecrecyNonFunctionalTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "HttpMessageCodecGarbageNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/testing/HttpMessageCodecGarbageNonFunctionalTest.kt",
        "tests": 2,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlParseGarbageNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/UrlParseGarbageNonFunctionalTest.kt",
        "tests": 1,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 1,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlParseLinearTimeNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/UrlParseLinearTimeNonFunctionalTest.kt",
        "tests": 2,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 2,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      },
      {
        "class": "UrlParseSecrecyNonFunctionalTest",
        "module": "webtools-http",
        "testLevel": "4c",
        "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/UrlParseSecrecyNonFunctionalTest.kt",
        "tests": 3,
        "rows": [],
        "result": {
          "state": "passed",
          "passed": 3,
          "failed": 0,
          "skipped": 0,
          "pass": 2,
          "at": "2026-10-26T22:02:11Z"
        }
      }
    ],
    "other": {
      "byModule": {
        "build-logic": {
          "1": 1,
          "2": 4,
          "3": 9,
          "4a": 2,
          "4b": 3,
          "4c": 3
        },
        "webtools-browser": {
          "1": 1,
          "5": 1
        },
        "webtools-scraping": {
          "2": 1
        },
        "webtools-udp": {
          "1": 19,
          "2": 21,
          "3": 20,
          "5": 1,
          "4a": 9,
          "4b": 11,
          "4c": 11
        }
      }
    }
  },
  "roadmap": {
    "issue": 55,
    "title": "HTTP client stack for BotTools: suspend transport, outbound-URL guard, response cache, call budget and test fakes",
    "asOf": "2026-10-26",
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
        "state": "OPEN"
      },
      {
        "issue": 65,
        "title": "webtools-http: `OkHttpTransport` with manual redirects, the one-exchange guard, the resolver and socket guards, and the loopback test factories",
        "state": "OPEN"
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
      "label": "Issue #64",
      "path": "https://github.com/SpartanLaboratories/WebTools/issues/64",
      "note": "the unit as #55 filed it"
    },
    {
      "label": "#64 research",
      "path": "research/README.md",
      "note": "this unit's questions on the JVM guards, the IANA rows, the bypass vectors and the configuration API shape"
    },
    {
      "label": "#55 research",
      "path": "../research/README.md",
      "note": "the questions, verification and summary the Gap column rests on; question 5 bears on this unit"
    },
    {
      "label": "#55 outbound-URL policy findings",
      "path": "../research/outbound-url-policy.md",
      "note": "F1 to F14, the deny tables, the host rules, the test seam and the considerations behind the options"
    },
    {
      "label": "#55 architecture",
      "path": "../architecture.md",
      "note": "§6.3.1 the outbound-URL policy, §6.9 the mechanism per type, §7.3 the three guard layers, §9 extension and stability, §10 alternatives, §11 unit 5, §12 risks"
    },
    {
      "label": "#55 impact",
      "path": "../impact.md",
      "note": "§8 what units 1 to 4 as built give this unit"
    },
    {
      "label": "#62 as-built record",
      "path": "../62-http-core/final-implementation.md",
      "note": "the common core this unit builds on, as built: `Url`, `RefusalReason`, `RefusedByPolicy`, the sensitive flag"
    },
    {
      "label": "#55 tracking board",
      "path": "../dashboard.html",
      "note": "the eleven units in landing order"
    }
  ],
  "notes": {
    "paths": "`common/` abbreviates `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/` and `jvm/` its `jvmMain`, as #55's architecture does. Line references marked baseline are to `4fcc6e0`.",
    "origin": "This unit's research and architecture happened in #55's run, so the Gap and Considered columns were written at sized from #55's research README, its outbound-URL policy findings and its architecture; the unit's own research and plan add to Considered and write the Verdict at plan acceptance.",
    "corrections": [
      {
        "what": "The `url-guard` row's accepted option said \"Level 3 can run `Untrusted`\"; it now says \"Level 3 (integration) tests\", so the test level cannot be misread. The decision is unchanged.",
        "when": "researched, 2026-10-25"
      },
      {
        "what": "The `policy-pair` row's Considered column now records the option the plan left open at its acceptance, a public `@DslMarker` (`dsl-marker`, deferred: plan §4, §13 and §14, and the open point raised then), which the board had left out. Its settlement by plan Amendment 3, accepted, is in column 4.",
        "when": "implementation S8, 2026-10-26"
      }
    ]
  },
  "issueBoards": {
    "47": "../47-java-23-bytecode/dashboard.html",
    "61": "../61-build-logic/dashboard.html",
    "62": "../62-http-core/dashboard.html",
    "63": "../63-test-support/dashboard.html"
  },
  "links": {
    "workBoard": "../../index.html",
    "repoBoard": "../../../dashboards/index.html",
    "boards": {
      "webtools-http-url-policy": "../../../dashboards/webtools-http/index.html"
    }
  }
}
);
