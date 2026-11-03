/* plan.js: plan.json as a script, written by plan/scripts/embed.js beside the board for its live view (plan/RULES.md §1). Do not edit: edit plan.json and re-embed. */
window.dashboardLive&&window.dashboardLive("plan",
{
  "schema": "plan-board/1",
  "repo": "SpartanLaboratories/WebTools",
  "issue": 55,
  "slug": "55-http-client-stack",
  "folder": "docs/plans/55-http-client-stack",
  "kind": "tracking",
  "level": 4,
  "title": "HTTP client stack",
  "issueTitle": "HTTP client stack for BotTools: suspend transport, outbound-URL guard, response cache, call budget and test fakes",
  "function": "BotTools needs a suspend HTTP stack: a transport, an outbound-URL guard, a response cache, a call budget and test fakes. This tracking issue designs it as a new Kotlin Multiplatform module `webtools-http`, a feeds module `webtools-feeds` on top of it, the shared `build-logic` with the Java 11 floor that folds in #47, and their documentation, and builds it as eleven units, each a sub-issue planned and built on its own board.",
  "status": "building",
  "parent": null,
  "branch": "feat/issue-55-http-client-stack",
  "baseline": {
    "ref": "master",
    "sha": "806ce8e",
    "date": "2026-10-20"
  },
  "asBuilt": null,
  "systems": [
    {
      "slug": "gradle-build",
      "name": "Gradle build",
      "slot": 4,
      "top": "Gradle build",
      "status": "built",
      "page": "docs/dashboards/gradle-build/index.html"
    },
    {
      "slug": "webtools-http-core",
      "name": "webtools-http common core",
      "slot": 5,
      "top": "webtools-http",
      "status": "built",
      "page": "docs/dashboards/webtools-http/index.html"
    },
    {
      "slug": "webtools-http-test-fixtures",
      "name": "published test fixtures",
      "slot": 5,
      "top": "webtools-http",
      "status": "built",
      "page": "docs/dashboards/webtools-http/index.html"
    },
    {
      "slug": "webtools-http-url-policy",
      "name": "outbound-URL policy",
      "slot": 5,
      "top": "webtools-http",
      "status": "built",
      "page": "docs/dashboards/webtools-http/index.html"
    },
    {
      "slug": "webtools-http-transport",
      "name": "OkHttp transport",
      "slot": 5,
      "top": "webtools-http",
      "status": "built",
      "page": "docs/dashboards/webtools-http/index.html"
    },
    {
      "slug": "webtools-http-cache",
      "name": "response cache",
      "slot": 5,
      "top": "webtools-http",
      "status": "planned",
      "page": null
    },
    {
      "slug": "webtools-http-budget",
      "name": "call budget",
      "slot": 5,
      "top": "webtools-http",
      "status": "planned",
      "page": null
    },
    {
      "slug": "webtools-http-facades",
      "name": "call facades",
      "slot": 5,
      "top": "webtools-http",
      "status": "planned",
      "page": null
    },
    {
      "slug": "webtools-feeds",
      "name": "webtools-feeds",
      "slot": 6,
      "top": "webtools-feeds",
      "status": "planned",
      "page": null
    },
    {
      "slug": "webtools",
      "name": "WebTools",
      "slot": 0,
      "top": "WebTools",
      "status": "built",
      "page": "docs/dashboards/index.html"
    }
  ],
  "wip": {
    "step": "implementation",
    "since": "2026-10-22T21:58:56Z",
    "updated": "2026-11-01T17:10:00Z",
    "detail": "5 of 11 units landed; #65 in progress; #66 up next.",
    "steps": [
      "sizing",
      "interview",
      "research",
      "architecture",
      "landing",
      "implementation",
      "landed"
    ],
    "current": [
      "okhttp-transport.built"
    ],
    "next": "response-cache",
    "qa": [],
    "qaMax": 3,
    "waitingOn": null,
    "resume": null,
    "blockers": {
      "waitsOn": [],
      "blocks": [
        {
          "issue": 9,
          "repo": "SpartanLaboratories/KotlinBottools"
        },
        {
          "issue": 16,
          "repo": "SpartanLaboratories/KotlinBottools"
        }
      ]
    },
    "landing": {
      "pr": 60,
      "merge": "834ce4a",
      "version": null,
      "date": "2026-10-21"
    }
  },
  "milestones": [
    {
      "column": "gap",
      "label": "Research verified",
      "date": "2026-10-21",
      "ref": "research/README.md",
      "note": "ten questions and findings files; 231 outside-world claims re-opened at source, 227 hold, none fails"
    },
    {
      "column": "considered",
      "label": "Architecture",
      "date": "2026-10-21",
      "ref": "architecture.md",
      "note": "§10 alternatives, §11 decomposition into eleven units, §14 considerations: none open"
    },
    {
      "column": "verdict",
      "label": "Plan merged, sub-issues filed",
      "date": "2026-10-21",
      "ref": "PR #60",
      "note": "the plan-only docs PR merged as 834ce4a; #61 and #62 to #70 filed and #47 re-parented, with blocked-by links in landing order"
    }
  ],
  "trail": [
    {
      "date": "2026-10-20",
      "column": "gap",
      "label": "Issue #55 opened",
      "ref": "#55"
    },
    {
      "date": "2026-10-20",
      "column": "gap",
      "label": "Research questions for the HTTP client stack",
      "ref": "9357e00"
    },
    {
      "date": "2026-10-21",
      "column": "gap",
      "label": "Research verification results and summary",
      "ref": "3b4a746"
    },
    {
      "date": "2026-10-21",
      "column": "considered",
      "label": "The design the research settled, stated in the research summary",
      "ref": "96c30d6"
    },
    {
      "date": "2026-10-21",
      "column": "considered",
      "label": "Architecture and impact",
      "ref": "b022d3a"
    },
    {
      "date": "2026-10-21",
      "column": "verdict",
      "label": "PR #60 merged: architecture, research and the system dashboards' planned systems",
      "ref": "834ce4a"
    },
    {
      "date": "2026-10-21",
      "column": "verdict",
      "label": "Sub-issues filed: #61, #62 to #70; #47 re-parented as unit 2"
    },
    {
      "date": "2026-10-21",
      "column": "built",
      "label": "Unit 1, #61: research questions, findings, verification and summary on `feat/issue-61-build-logic`",
      "ref": "c80881b"
    },
    {
      "date": "2026-10-21",
      "column": "verdict",
      "label": "PR #71 merged: the system dashboards rebuilt to the new conventions",
      "ref": "0e7b0f8"
    },
    {
      "date": "2026-10-22",
      "column": "built",
      "label": "Unit 1, #61: qa pass 1 changes requested (14 documentation, 9 test findings), remediated; qa pass 2 passed with six minor fixes",
      "ref": ""
    },
    {
      "date": "2026-10-22",
      "column": "built",
      "label": "Unit 1, #61, landed: PR #74 merged; published output identical, no version moved",
      "ref": "1e27071"
    },
    {
      "date": "2026-10-23",
      "column": "built",
      "label": "Unit 2, #47: plan accepted, branch `fix/issue-47-java-floor` cut from 36fd879; implementation with no deviations",
      "ref": "47-java-23-bytecode/plan.md"
    },
    {
      "date": "2026-10-23",
      "column": "built",
      "label": "Unit 2, #47: qa pass 1 changes requested (7 documentation, 2 test findings), fixed on the spot; no second pass",
      "ref": "a96d969"
    },
    {
      "date": "2026-10-23",
      "column": "built",
      "label": "Unit 2, #47, landed: PR #76 merged; every module on the Java 11 floor, `webtools-scraping` and `webtools-browser` 1.0.0a, none published",
      "ref": "3949b52"
    },
    {
      "date": "2026-10-23",
      "column": "built",
      "label": "Unit 2, #47: as-built record and impact reconciled after the landing (PR #78); this tracking issue's `impact.md` brought to units 1 and 2 as built",
      "ref": "77a69bc"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "Unit 3, #62, started: sized as one system; unit 3's plan is re-passed against architecture.md §6.2, §6.8, §6.9 and §11 and impact.md §7",
      "ref": "#62"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "Unit 3, #62, landed: PR #82 merged as 7de8207; `webtools-http` 1.0.0 new and unreleased, no version moved; two changes to the initiative's impact: the check-only compiles take `js()` with no environment, and `HttpTransport` implementations must be main-safe",
      "ref": "7de8207"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "Unit 4, #63, started: sized as one system; unit 4's plan is re-passed against architecture.md §6.7, §6.1 and §11 and impact.md §7",
      "ref": "#63"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "#63 landed"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "Unit 5, #64, started: sized as one subsystem; unit 5's plan is re-passed against architecture.md §6.3.1 and §11 with units 3 and 4 as built",
      "ref": "#64"
    },
    {
      "date": "2026-10-26",
      "column": "built",
      "label": "#64 landed"
    },
    {
      "date": "2026-11-01",
      "column": "built",
      "label": "#65 started"
    }
  ],
  "rows": [
    {
      "id": "build-logic",
      "group": "Build",
      "name": "Shared build logic",
      "system": "gradle-build",
      "where": "build-logic/ (included build)",
      "unit": {
        "n": 1,
        "slug": "build-logic",
        "issue": 61,
        "level": 3,
        "blockedBy": [],
        "versions": "none: published POMs and metadata identical",
        "branch": "feat/issue-61-build-logic",
        "folder": "61-build-logic/",
        "board": "61-build-logic/dashboard.html"
      },
      "gap": {
        "kind": "debt",
        "summary": "Every module is configured by the root build's `subprojects {}` block: both plugins, `api(slf4j-api)`, the seven level tasks selecting by JUnit tag over `sourceSets[\"test\"]`, and the publishing skeleton. A multiplatform module cannot join without branching that block, which Gradle documents as improper cross-project configuration.",
        "evidence": [
          "build.gradle.kts:17-92 — the `subprojects {}` block",
          "build.gradle.kts:44-62 — the level tasks; the tag filter at :60",
          "webtools-udp/build.gradle.kts:103-117 — the port lock calls `tasks.named` at module evaluation"
        ],
        "source": "architecture.md §2, §6.1",
        "link": "architecture.md#2-current-state"
      },
      "options": [
        {
          "id": "included-build",
          "name": "An included `build-logic` build with three convention plugins and `WebToolsBuild`",
          "verdict": "accepted",
          "why": "One place decides every module's compile target, test layout and publishing shape; plugins are applied by id, typed, and a change does not invalidate the whole configuration.",
          "source": "architecture.md §6.1",
          "link": "architecture.md#61-shared-build-logic--build-logic"
        },
        {
          "id": "subprojects-withid",
          "name": "Root `subprojects {}` with `plugins.withId` branches",
          "verdict": "declined",
          "why": "Cross-project configuration is what Gradle documents as improper, and the KMP extension needs string-typed `extensions.configure`.",
          "wins": "If an included build were unwanted: it is the smallest diff.",
          "source": "architecture.md §10",
          "link": "architecture.md#10-alternatives-considered"
        },
        {
          "id": "buildsrc",
          "name": "`buildSrc`",
          "verdict": "declined",
          "why": "Works (measured), but invalidates the whole configuration on every change.",
          "source": "architecture.md §10"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One system, the build, and the change is additive build behaviour with no library API change. Measured: the three modules publish identical output.",
        "steps": [],
        "tests": [
          "The three modules publish identical POMs and metadata",
          "The package filter selects the same 95 test classes the tag filter did"
        ],
        "leaves": [
          "The Java floor and its guard (unit 2)",
          "Any KMP module and the common-check plugin (unit 3)",
          "The fixtures-publication check (unit 4)"
        ],
        "risks": [
          "Kotlin 2.2.0 on Gradle 9.7.1 is outside Kotlin's documented range; `build-logic` pins the version once, so the move is one line"
        ],
        "source": "architecture.md §11 unit 1",
        "link": "architecture.md#11-decomposition",
        "statements": [
          {
            "text": "Create the included build with `WebToolsBuild` (the level list and `registerLevelTasks`) and the plugins `webtools.published-library`, `webtools.kotlin-jvm-library` and `webtools.kotlin-multiplatform-library`.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          },
          {
            "text": "Cut `settings.gradle.kts` and the root build to `group` and `version`.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          },
          {
            "text": "Put the three existing modules on the JVM plugin with `api(slf4j-api)` in their own files, keeping the `webtools-udp` port lock verbatim.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          },
          {
            "text": "Select the level tasks by package.",
            "ref": "architecture.md#61-shared-build-logic--build-logic",
            "excerpt": {
              "ref": "architecture.md#61-shared-build-logic--build-logic",
              "heading": "6.1 Shared build logic — `build-logic`",
              "paragraph": "Responsibility: one place where every module's compile target, test layout and publishing shape are decided. · Placement: an included build at `build-logic/` (`pluginManagement { includeBuild(\"build-logic\") }` in `settings.gradle.kts`, first statement). The root `build.gradle.kts` keeps only `allprojects { group; version }`; its `plugins`, `subprojects {}`, level-task and publishing blocks (`:1-5`, `:17-92`) move here. Plugin versions (KGP 2.2.0, vanniktech 0.36.0) are pinned once in `build-logic/build.gradle.kts`. · Owns: · `WebToolsBuild` — an object holding `JAVA_FLOOR = 11`, the ordered …",
              "hash": "b27a1f1ca861"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "landed",
        "summary": "Landed through PR #74 as 1e27071 (#61): the included build with `WebToolsBuild` and its three convention plugins, the three modules on `webtools.kotlin-jvm-library`, the root script cut to `group` and `version`, the level tasks selecting by package. The published output is identical to `834ce4a`, so no version moved.",
        "changes": [
          {
            "what": "The level selection was proven under `--test-dry-run`, without running a module's test body: the same 95 classes as the tags selected on `834ce4a`.",
            "why": "The testing rule runs only the tests that exercise a change; the change touches neither the modules' code nor their tests, and identical published output shows them unaffected.",
            "when": "#61 amendment 1 · after qa pass 1",
            "ref": "c1fa76e"
          },
          {
            "what": "`build-logic` carries its own tests at six levels, one task per level, on a Gradle TestKit harness with one shared TestKit home that unit 4's publication check reuses.",
            "why": "#61's plan and its amendments: the multiplatform plugin has no module to exercise it until unit 3.",
            "when": "#61 plan and amendments 1 and 2",
            "ref": "c1fa76e"
          }
        ],
        "files": [
          "build-logic/",
          "settings.gradle.kts",
          "build.gradle.kts",
          "webtools-udp/build.gradle.kts",
          "webtools-scraping/build.gradle.kts",
          "webtools-browser/build.gradle.kts",
          "README.md"
        ],
        "commits": [
          "c1fa76e",
          "db98325",
          "960cfa4"
        ],
        "verified": "qa pass 1 changes requested (14 documentation, 9 test), pass 2 passed with six minor fixes; every build-logic task green from the root.",
        "link": "61-build-logic/final-implementation.md"
      }
    },
    {
      "id": "java-floor",
      "group": "Build",
      "name": "Java 11 floor (#47)",
      "system": "gradle-build",
      "where": "build-logic/ · webtools-scraping, webtools-browser build files",
      "unit": {
        "n": 2,
        "slug": "java-floor",
        "issue": 47,
        "level": 2,
        "blockedBy": [
          61
        ],
        "versions": "webtools-scraping and webtools-browser 1.0.0 → 1.0.0a; webtools-udp rides in the unreleased 2.0.0a",
        "branch": "fix/issue-47-java-floor",
        "folder": "47-java-23-bytecode/",
        "board": "47-java-23-bytecode/dashboard.html"
      },
      "gap": {
        "kind": "bug",
        "summary": "Every published artifact is Java 23 bytecode declaring `org.gradle.jvm.version: 23`, while the README promises JDK 11 or newer. No build file sets `jvmToolchain`, `jvmTarget` or `-Xjdk-release`.",
        "evidence": [
          "README.md:26 — \"JDK 11 or newer\"",
          "build.gradle.kts — no `jvmTarget`, `-Xjdk-release` or toolchain",
          "47-java-23-bytecode/research/java-11-floor.md F1-F5 — `jvmTarget` alone compiles JDK 21 calls; with `-Xjdk-release=11` they are rejected"
        ],
        "source": "architecture.md §2; java-11-floor.md",
        "link": "47-java-23-bytecode/research/java-11-floor.md"
      },
      "options": [
        {
          "id": "floor-constant",
          "name": "`WebToolsBuild.JAVA_FLOOR = 11` wiring `jvmTarget`, `-Xjdk-release` and the KMP attribute, with a `verifyJavaFloor` guard in `check`",
          "verdict": "accepted",
          "why": "The API limit on the build JDK without the foojay bump; the guard fails the build rather than let a Java 23 class or a wrong attribute reach a publication.",
          "source": "architecture.md §6.1",
          "link": "architecture.md#61-shared-build-logic--build-logic"
        },
        {
          "id": "toolchain",
          "name": "`jvmToolchain(11)` as the floor",
          "verdict": "declined",
          "why": "The same API limit at the cost of the foojay bump and a JDK 11 on every consumer's composite machine, and it still writes no KMP attribute.",
          "wins": "If the level tests had to run on a JDK 11 inside this work.",
          "source": "architecture.md §10",
          "link": "architecture.md#10-alternatives-considered"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "A bug fix to bytecode and metadata with no public API, state or wire change, local to the build.",
        "steps": [],
        "tests": [
          "`verifyJavaFloor`: no class above major 55, `org.gradle.jvm.version` 11 on every API and runtime variant"
        ],
        "leaves": [
          "A JDK 11 test launcher (follow-up)",
          "The foojay bump (#58)"
        ],
        "risks": [
          "`1.0.0a` orders below `1.0.0`: a consumer graph that also asks for `1.0.0` keeps the defect unless it pins with `strictly`; the README and the release note say so"
        ],
        "source": "architecture.md §11 unit 2",
        "link": "architecture.md#11-decomposition",
        "statements": [
          {
            "text": "Add `WebToolsBuild.JAVA_FLOOR = 11` and wire `jvmTarget`, `-Xjdk-release` and the Java compatibility settings of the JVM plugin, and the `TargetJvmVersion` attribute of the multiplatform plugin, from it.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          },
          {
            "text": "Add the `verifyJavaFloor` task into `check`.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          },
          {
            "text": "Set `1.0.0a` on `webtools-scraping` and `webtools-browser`, and extend `webtools-udp`'s `2.0.0a` note.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          },
          {
            "text": "Add the build-JDK note to the README.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "landed",
        "summary": "Landed through PR #76 as 3949b52 (#47): `WebToolsBuild.JAVA_FLOOR = 11` drives `jvmTarget`, `-Xjdk-release` and the Java compatibility settings of both library plugins and the multiplatform `TargetJvmVersion` attribute, `verifyJavaFloor` guards it in `check`, and the ASM pin is gone. The three jars are class-file major 55 and declare `org.gradle.jvm.version` 11. `webtools-scraping` and `webtools-browser` are at 1.0.0a and `webtools-udp` stays at 2.0.0a, none published.",
        "changes": [
          {
            "what": "The README's install block names `webtools-udp` 2.0.0, the version on Maven Central, in place of the unreleased 2.0.0a, with a paragraph on which releases carry the floor.",
            "why": "An install block names only versions a consumer can resolve; the lettered versions move in when they are released (#47's plan, Requirements 8).",
            "when": "#47 plan",
            "ref": "9e47ac0"
          },
          {
            "what": "The lettered-version ordering is stated for all three artifacts: `2.0.0a` orders below `2.0.0`, the Java 23 build on Central, as `1.0.0a` orders below `1.0.0`, so a `webtools-udp` graph that also asks for `2.0.0` needs `strictly` too.",
            "why": "`architecture.md` §12 calls `webtools-udp` unaffected by the ordering, and the verdict's risk names `1.0.0a` alone; #47's plan, the README and the reconciled impact state the rule for all three. `architecture.md` is left as written.",
            "when": "#47 plan; reconciled after landing",
            "ref": "765289c"
          }
        ],
        "files": [
          "build-logic/src/main/kotlin/WebToolsBuild.kt",
          "build-logic/src/main/kotlin/JavaFloorScan.kt",
          "build-logic/src/main/kotlin/VerifyJavaFloor.kt",
          "build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts",
          "build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts",
          "webtools-scraping/build.gradle.kts",
          "webtools-browser/build.gradle.kts",
          "webtools-udp/build.gradle.kts",
          "README.md"
        ],
        "commits": [
          "a96d969",
          "e249f52",
          "9e47ac0",
          "7ca020f",
          "d28200e"
        ],
        "verified": "qa pass 1 changes requested (7 documentation, 2 test), fixed on the spot with no second pass; `build-logic` suites green on JDK 23 and JDK 17; against `36fd879` every class is major 55 with `javap -protected` identical; every class loads on a real JDK 11; the guard passes on all three modules.",
        "link": "47-java-23-bytecode/final-implementation.md"
      }
    },
    {
      "id": "http-core",
      "group": "webtools-http",
      "name": "webtools-http common core",
      "system": "webtools-http-core",
      "where": "webtools-http/src/commonMain, jvmMain · webtools-http-commoncheck/",
      "unit": {
        "n": 3,
        "slug": "http-core",
        "issue": 62,
        "level": 3,
        "blockedBy": [
          61,
          47
        ],
        "versions": "webtools-http 1.0.0 (new, unreleased)",
        "branch": null,
        "folder": "62-http-core/",
        "board": "62-http-core/dashboard.html"
      },
      "gap": {
        "kind": "missing",
        "summary": "No HTTP stack exists. `Connector` is the only HTTP client: a blocking, stateful line reader over `URLConnection` that sets the JVM-wide `http.agent` on every connect and calls `Unirest.setTimeouts(0, 0)` in its `init`. Nothing offers a suspend request, a typed failure model or a transport port.",
        "evidence": [
          "webtools-scraping/.../Connector.kt:85, :218, :229 — `URLConnection`",
          "Connector.kt:150 — JVM-wide `http.agent`",
          "Connector.kt:65 — `Unirest.setTimeouts(0, 0)`"
        ],
        "source": "architecture.md §2; research/codebase-blast-radius.md",
        "link": "architecture.md#2-current-state"
      },
      "options": [
        {
          "id": "open-failures-builders",
          "name": "Open failure hierarchy in `kotlin.Result`, builders over `internal` constructors, one opt-in marker through `@SubclassOptInRequired`",
          "verdict": "accepted",
          "why": "Adding a failure leaf or a builder property never breaks a consumer's compiled code, and the supplied defaults are usable without opting in.",
          "source": "architecture.md §6.2, §9",
          "link": "architecture.md#62-webtools-http-common-core"
        },
        {
          "id": "sealed-failures",
          "name": "A sealed failure root, or enums for library-produced reasons",
          "verdict": "declined",
          "why": "Each added leaf or entry is a compile error in a consumer's exhaustive `when` and a runtime `NoWhenBranchMatchedException` in a compiled one.",
          "source": "architecture.md §10",
          "link": "architecture.md#10-alternatives-considered"
        },
        {
          "id": "flat-constructors",
          "name": "Flat constructors with defaulted parameters plus hidden deprecated overloads",
          "verdict": "declined",
          "why": "A permanent overload per addition kept correct by hand; `@IntroducedAt` is Kotlin 2.3+.",
          "source": "architecture.md §10"
        },
        {
          "id": "kotlin-time",
          "name": "`kotlin.time.Clock`/`Instant` or kotlinx-datetime in signatures",
          "verdict": "declined",
          "why": "An error-level opt-in on every 2.2.x consumer, and the budget needs no calendar arithmetic.",
          "source": "architecture.md §10"
        },
        {
          "id": "fun-interface",
          "name": "`fun interface` for `WallClock`",
          "verdict": "declined",
          "why": "A `fun interface` cannot carry the subclass gate; one rule for every seam.",
          "source": "architecture.md §10"
        },
        {
          "id": "scheduling-type",
          "name": "An opaque `Scheduling` type in place of `CoroutineDispatcher`, keeping coroutines `implementation`",
          "verdict": "declined",
          "why": "A consumer could no longer pass its own dispatcher, and the coroutines runtime is part of what a suspend API is.",
          "source": "architecture.md §10"
        },
        {
          "id": "kotlin-logging",
          "name": "kotlin-logging in `commonMain`",
          "verdict": "declined",
          "why": "A startup line and a runtime dependency on every consumer, with no gain on one published target; an `internal expect fun` seam is the smallest thing that logs.",
          "source": "architecture.md §10"
        },
        {
          "id": "js-target",
          "name": "A published `js` target instead of check-only modules",
          "verdict": "declined",
          "why": "A coordinate and a klib nothing consumes, Node and Yarn downloads for tests, and a published surface the requirements do not name.",
          "wins": "The day a non-JVM consumer exists.",
          "source": "architecture.md §10"
        },
        {
          "id": "kgp-abi",
          "name": "KGP's built-in `abiValidation` instead of BCV",
          "verdict": "declined",
          "why": "Experimental on 2.2.0 and its DSL changed by 2.4.",
          "source": "architecture.md §10"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One system: one new module's additive API. Its plan starts with the compile proof of the marker, a gated interface, a default naming it and a consumer without opt-in.",
        "steps": [],
        "tests": [
          "Level 4a in `commonTest`: parsing, validation, query encoding",
          "`jvmTest`: the `Url`-vs-`HttpUrl` differential"
        ],
        "leaves": [
          "Every default implementation and policy (units 4-9)"
        ],
        "risks": [
          "The public surface has not been compiled; the opt-in behaviour comes from compiler test data and one JVM-only measurement"
        ],
        "source": "architecture.md §11 unit 3",
        "link": "architecture.md#11-decomposition",
        "statements": [
          {
            "text": "Create `webtools-http` (the multiplatform plugin, `explicitApi()`, the ABI dump) and `webtools-http-commoncheck` with the `webtools.kotlin-common-check` plugin.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          },
          {
            "text": "Deliver the marker, the failure model with both reason vocabularies, the request and response types, `HttpTransport` and `WallClock`.",
            "ref": "architecture.md#62-webtools-http-common-core",
            "excerpt": {
              "ref": "architecture.md#62-webtools-http-common-core",
              "heading": "6.2 `webtools-http` common core",
              "paragraph": "Responsibility: the vocabulary and the contracts every other system in the module speaks, with nothing platform-specific in a signature. · Placement: `common/`, one flat package `com.spartanlabs.webtools.http` (no `.internal` subpackage: `internal` does the hiding, and a flat package never needs a type to move). `explicitApi()`. Module build: `webtools.kotlin-multiplatform-library`, `jvm()` only, `jvmMain` dependencies `okhttp-jvm` 5.5.0, `caffeine` 3.3.1 and `slf4j-api` 2.0.13, all `implementation`. · The one `api` dependency. `kotlinx-coroutines-core` 1.10.2 is the only third-party library …",
              "hash": "3b7998b63fc0"
            }
          },
          {
            "text": "Deliver the logging seam, the internal `flatMap` and the non-fatal helper.",
            "ref": "architecture.md#62-webtools-http-common-core",
            "excerpt": {
              "ref": "architecture.md#62-webtools-http-common-core",
              "heading": "6.2 `webtools-http` common core",
              "paragraph": "Responsibility: the vocabulary and the contracts every other system in the module speaks, with nothing platform-specific in a signature. · Placement: `common/`, one flat package `com.spartanlabs.webtools.http` (no `.internal` subpackage: `internal` does the hiding, and a flat package never needs a type to move). `explicitApi()`. Module build: `webtools.kotlin-multiplatform-library`, `jvm()` only, `jvmMain` dependencies `okhttp-jvm` 5.5.0, `caffeine` 3.3.1 and `slf4j-api` 2.0.13, all `implementation`. · The one `api` dependency. `kotlinx-coroutines-core` 1.10.2 is the only third-party library …",
              "hash": "3b7998b63fc0"
            }
          },
          {
            "text": "Add the README's artifact row and install line.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "landed",
        "summary": "Landed through PR #82 as 7de8207 (#62): `webtools-http` is the first module on the multiplatform convention, with `explicitApi()`, its ABI dump and `kotlinx-coroutines-core` as its one `api` dependency. It holds the opt-in marker, the failure model with both reason vocabularies, the request and response types with `Url`, and the gated ports `HttpTransport` and `WallClock` with `WallClock.System`. `webtools-http-commoncheck` compiles its `commonMain` for `js()` with no environment through the new `webtools.kotlin-common-check` plugin, so `build` downloads no Node.js and the root project gains no task. `webtools-http` 1.0.0 is new and unreleased; no version moved.",
        "changes": [
          {
            "what": "The check plugin `webtools.kotlin-common-check` calls `js()` with no environment, in place of the `js { nodejs { testTask { enabled = false } } }` of architecture §3, §6.1 and §6.8, so the root project gains no task and unit 4's open point on where the publication check attaches stands.",
            "why": "#62's research measured the architecture's form downloading Node.js and Yarn during `build`, writing `kotlin-js-store/` and giving the root project the base plugin and about 18 tasks; without an environment the target compiles to a klib, at the cost of one warning per configuration-cache miss. `architecture.md` is left as written (#62 `plan.md` §13).",
            "when": "#62 plan",
            "ref": "62-http-core/plan.md#67-the-check-only-js-module"
          }
        ],
        "commits": [
          "e007cdd",
          "123c875",
          "b5257d3",
          "40cb538",
          "4d9c9f9"
        ],
        "verified": "qa pass 1 changes requested (18 documentation, 9 test, 2 code findings; 27 of them minor), fixed on the spot with no second pass; the 131 tests of `webtools-http` green and `apiCheck` passed; the compile proof of the gate held in `commonMain`, `jvmMain` and the check module's `jsMain`.",
        "link": "62-http-core/final-implementation.md"
      }
    },
    {
      "id": "test-support",
      "group": "webtools-http",
      "name": "Published test fixtures",
      "system": "webtools-http-test-fixtures",
      "where": "webtools-http/src/jvmTestFixtures",
      "unit": {
        "n": 4,
        "slug": "test-support",
        "issue": 63,
        "level": 3,
        "blockedBy": [
          62
        ],
        "versions": "none",
        "branch": null,
        "folder": "63-test-support/",
        "board": "63-test-support/dashboard.html",
        "tests": {
          "base": "5d98102",
          "updated": "2026-10-25T20:54:23Z",
          "added": [
            {
              "class": "com.spartanlabs.testing.component.webtools.http.testing.VirtualClockComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/VirtualClockComponentTest.kt",
              "tests": 11,
              "rows": [
                "virtual-clock",
                "fixtures-tests"
              ],
              "stage": "S2",
              "result": {
                "state": "passed",
                "passed": 11,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.component.webtools.http.testing.VirtualClockThreeClocksComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/VirtualClockThreeClocksComponentTest.kt",
              "tests": 2,
              "rows": [
                "virtual-clock",
                "fixtures-tests"
              ],
              "stage": "S2",
              "result": {
                "state": "passed",
                "passed": 2,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.component.webtools.http.testing.FixtureComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/FixtureComponentTest.kt",
              "tests": 10,
              "rows": [
                "fixture-codec",
                "fixtures-tests"
              ],
              "stage": "S3",
              "result": {
                "state": "passed",
                "passed": 10,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.component.webtools.http.testing.FixtureLoaderComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/FixtureLoaderComponentTest.kt",
              "tests": 9,
              "rows": [
                "fixture-codec",
                "fixtures-tests"
              ],
              "stage": "qa pass 1 fixes",
              "result": {
                "state": "passed",
                "passed": 9,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.deterministic.webtools.http.testing.HttpMessageCodecDeterministicTest",
              "module": "webtools-http",
              "testLevel": "4a",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/testing/HttpMessageCodecDeterministicTest.kt",
              "tests": 8,
              "rows": [
                "fixture-codec",
                "fixtures-tests"
              ],
              "stage": "S3",
              "result": {
                "state": "passed",
                "passed": 8,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.component.webtools.http.testing.FakeHttpTransportComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/FakeHttpTransportComponentTest.kt",
              "tests": 21,
              "rows": [
                "fake-transport",
                "fixtures-tests"
              ],
              "stage": "S4",
              "result": {
                "state": "passed",
                "passed": 21,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.component.webtools.http.testing.RequestMatchComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/RequestMatchComponentTest.kt",
              "tests": 9,
              "rows": [
                "fake-transport",
                "fixtures-tests"
              ],
              "stage": "S4",
              "result": {
                "state": "passed",
                "passed": 9,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.component.webtools.http.testing.StepComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/StepComponentTest.kt",
              "tests": 7,
              "rows": [
                "fake-transport",
                "fixtures-tests"
              ],
              "stage": "S4",
              "result": {
                "state": "passed",
                "passed": 7,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.deterministic.webtools.http.testing.QueryPairsDeterministicTest",
              "module": "webtools-http",
              "testLevel": "4a",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/testing/QueryPairsDeterministicTest.kt",
              "tests": 3,
              "rows": [
                "fake-transport",
                "fixtures-tests"
              ],
              "stage": "S4",
              "result": {
                "state": "passed",
                "passed": 3,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.gating.webtools.http.testing.FixturesConsumerGatingTest",
              "module": "webtools-http",
              "testLevel": "1",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/gating/webtools/http/testing/FixturesConsumerGatingTest.kt",
              "tests": 4,
              "rows": [
                "fixtures-surface",
                "fixtures-tests"
              ],
              "stage": "qa pass 2 fixes",
              "result": {
                "state": "passed",
                "passed": 4,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.component.webtools.http.testing.RedactionComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/RedactionComponentTest.kt",
              "tests": 13,
              "rows": [
                "recorder"
              ],
              "stage": "qa pass 1 fixes",
              "result": {
                "state": "passed",
                "passed": 13,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.integration.webtools.http.testing.FixtureRecorderIntegrationTest",
              "module": "webtools-http",
              "testLevel": "3",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/integration/webtools/http/testing/FixtureRecorderIntegrationTest.kt",
              "tests": 16,
              "rows": [
                "recorder"
              ],
              "stage": "qa pass 2 fixes",
              "result": {
                "state": "passed",
                "passed": 16,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T20:54:23Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.deterministic.webtools.http.testing.SensitiveNamesDeterministicTest",
              "module": "webtools-http",
              "testLevel": "4a",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/testing/SensitiveNamesDeterministicTest.kt",
              "tests": 7,
              "rows": [
                "recorder"
              ],
              "stage": "S5",
              "result": {
                "state": "passed",
                "passed": 7,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.deterministic.webtools.http.testing.RecordingGateDeterministicTest",
              "module": "webtools-http",
              "testLevel": "4a",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/testing/RecordingGateDeterministicTest.kt",
              "tests": 7,
              "rows": [
                "recorder"
              ],
              "stage": "S5",
              "result": {
                "state": "passed",
                "passed": 7,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.deterministic.webtools.http.testing.FixtureFileNamesDeterministicTest",
              "module": "webtools-http",
              "testLevel": "4a",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/testing/FixtureFileNamesDeterministicTest.kt",
              "tests": 8,
              "rows": [
                "recorder"
              ],
              "stage": "S5",
              "result": {
                "state": "passed",
                "passed": 8,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.nonfunctional.webtools.http.testing.FixturesSecrecyNonFunctionalTest",
              "module": "webtools-http",
              "testLevel": "4c",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/testing/FixturesSecrecyNonFunctionalTest.kt",
              "tests": 2,
              "rows": [
                "recorder",
                "fixtures-tests"
              ],
              "stage": "qa pass 1 fixes",
              "result": {
                "state": "passed",
                "passed": 2,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.nonfunctional.webtools.http.testing.FakeHttpTransportConcurrencyNonFunctionalTest",
              "module": "webtools-http",
              "testLevel": "4c",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/testing/FakeHttpTransportConcurrencyNonFunctionalTest.kt",
              "tests": 2,
              "rows": [
                "fake-transport"
              ],
              "stage": "qa pass 2 fixes",
              "result": {
                "state": "passed",
                "passed": 2,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "com.spartanlabs.testing.nonfunctional.webtools.http.testing.HttpMessageCodecGarbageNonFunctionalTest",
              "module": "webtools-http",
              "testLevel": "4c",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/nonfunctional/webtools/http/testing/HttpMessageCodecGarbageNonFunctionalTest.kt",
              "tests": 2,
              "rows": [
                "fixture-codec"
              ],
              "stage": "qa pass 1 fixes",
              "result": {
                "state": "passed",
                "passed": 2,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T19:58:08Z"
              }
            },
            {
              "class": "FixturesPublicationEndToEndTest",
              "module": "build-logic",
              "testLevel": "4b",
              "path": "build-logic/src/test/kotlin/com/spartanlabs/testing/e2e/webtools/buildlogic/FixturesPublicationEndToEndTest.kt",
              "tests": 10,
              "rows": [
                "publication-check",
                "fixtures-variant"
              ],
              "stage": "qa pass 2 fixes",
              "result": {
                "state": "passed",
                "passed": 10,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T20:54:23Z"
              }
            },
            {
              "class": "FixturesCheckWiringEndToEndTest",
              "module": "build-logic",
              "testLevel": "4b",
              "path": "build-logic/src/test/kotlin/com/spartanlabs/testing/e2e/webtools/buildlogic/FixturesCheckWiringEndToEndTest.kt",
              "tests": 2,
              "rows": [
                "publication-check"
              ],
              "stage": "qa pass 1 fixes",
              "result": {
                "state": "passed",
                "passed": 2,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T20:54:23Z"
              }
            }
          ],
          "amended": [],
          "relevant": [
            {
              "class": "IncludedBuildEndToEndTest",
              "module": "build-logic",
              "tests": 5,
              "result": {
                "state": "passed",
                "passed": 5,
                "failed": 0,
                "skipped": 0,
                "at": "2026-10-25T20:54:23Z"
              }
            }
          ],
          "rowNames": {
            "fixtures-variant": "The test-fixtures compilation and its publication",
            "fixtures-surface": "The fixtures' tier, opt-in and API checks",
            "virtual-clock": "VirtualClock, one time source for the whole stack",
            "fake-transport": "FakeHttpTransport, a scripted transport",
            "fixture-codec": "Fixture and FixtureLoader, the .http codec",
            "recorder": "FixtureRecorder with RecordingGate and Redaction",
            "publication-check": "The fixtures-publication check in build-logic",
            "fixtures-tests": "The fixtures' own tests"
          }
        }
      },
      "gap": {
        "kind": "missing",
        "summary": "A consumer has no fake transport, no single time source shared by cache, budget and `delay`, and no recorder. Kotlin has no first-class test-fixtures feature for a multiplatform module (KT-63142, KT-69482 open).",
        "evidence": [
          "research/kmp-test-fixtures.md F4-F7 — a JVM-only `testFixtures` compilation through the experimental `adhocSoftwareComponent` resolves from a file repository and through `includeBuild`"
        ],
        "source": "research/README.md § Test support and time",
        "link": "research/README.md#test-support-and-time"
      },
      "options": [
        {
          "id": "recipe-b",
          "name": "A hand-built `testFixtures` compilation published as a variant (recipe B)",
          "verdict": "accepted",
          "why": "One coordinate and one version, with the fixtures' dependencies reaching only the consumer's test classpath.",
          "source": "architecture.md §6.7",
          "link": "architecture.md#67-published-test-support"
        },
        {
          "id": "recipe-a",
          "name": "The `java-test-fixtures` plugin beside the KMP plugin (recipe A)",
          "verdict": "declined",
          "why": "Identical consumer contract, but it re-implements an internal Gradle variant mapping and carries a KGP-2.2.0-only workaround.",
          "wins": "If recipe B stops working on a later KGP: A is the fallback.",
          "source": "architecture.md §10",
          "link": "architecture.md#10-alternatives-considered"
        },
        {
          "id": "testing-artifact",
          "name": "A separate `webtools-http-testing` artifact",
          "verdict": "declined",
          "why": "Ruled out by requirement 3: one coordinate, one version.",
          "wins": "If the experimental publishing API were removed without replacement.",
          "source": "architecture.md §10"
        },
        {
          "id": "binary-fixtures",
          "name": "A full binary guarantee for the fixtures",
          "verdict": "declined",
          "why": "Consumers compile their tests against the fixtures in their own build and publish no jars against them; builders would make every fake ceremonious.",
          "source": "architecture.md §10"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One system, the fixtures variant; the build check is this unit's own test.",
        "steps": [],
        "tests": [
          "The fixtures-publication TestKit check: capability, `org.gradle.jvm.version` 11, no test libraries on the main variants"
        ],
        "leaves": [
          "`LoopbackTransports` and the `MockWebServer` helpers (unit 6)",
          "The budget fakes (unit 8)"
        ],
        "risks": [
          "The fixtures rest on an experimental publishing API; only the publication check notices a change"
        ],
        "source": "architecture.md §11 unit 4",
        "link": "architecture.md#11-decomposition",
        "statements": [
          {
            "text": "Deliver the `testFixtures` compilation and its publication: two variants, the capability, `TargetJvmVersion` from the floor.",
            "ref": "architecture.md#67-published-test-support",
            "excerpt": {
              "ref": "architecture.md#67-published-test-support",
              "heading": "6.7 Published test support",
              "paragraph": "Responsibility: make the production stack testable without bending it: a clock, a script engine, a fixture codec, a loopback factory, a recorder. · Placement: `fixtures/`, package `com.spartanlabs.webtools.http.testing`, a JVM-only `testFixtures` compilation associated with `main` (so it reaches `internal` declarations) and published as two variants (`testFixturesApiElements`, `testFixturesRuntimeElements`, capability `io.github.spartanlaboratories:webtools-http-test-fixtures:<v>`, `TargetJvmVersion` from `JAVA_FLOOR`, `mapToMavenScope` + `mapToOptional`) through …",
              "hash": "ebae2799fbaa"
            }
          },
          {
            "text": "Deliver `VirtualClock`, `FakeHttpTransport`, `Fixture` and `FixtureLoader` with the `.http` codec, and `FixtureRecorder` with `RecordingGate` and `Redaction`.",
            "ref": "architecture.md#67-published-test-support",
            "excerpt": {
              "ref": "architecture.md#67-published-test-support",
              "heading": "6.7 Published test support",
              "paragraph": "Responsibility: make the production stack testable without bending it: a clock, a script engine, a fixture codec, a loopback factory, a recorder. · Placement: `fixtures/`, package `com.spartanlabs.webtools.http.testing`, a JVM-only `testFixtures` compilation associated with `main` (so it reaches `internal` declarations) and published as two variants (`testFixturesApiElements`, `testFixturesRuntimeElements`, capability `io.github.spartanlaboratories:webtools-http-test-fixtures:<v>`, `TargetJvmVersion` from `JAVA_FLOOR`, `mapToMavenScope` + `mapToOptional`) through …",
              "hash": "ebae2799fbaa"
            }
          },
          {
            "text": "Add the fixtures-publication check to `build-logic`, wired into `check`, the `.gitattributes` line and the README's `testFixtures(...)` line.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "landed",
        "summary": "Landed through PR #84 as 4fcc6e0 (#63): `webtools-http` publishes a test-fixtures variant, capability `webtools-http-test-fixtures`, with `VirtualClock`, the scripted `FakeHttpTransport`, `Fixture` and `FixtureLoader` for `.http` files and the gated, redacting `FixtureRecorder`, on a consumer's test classpath only. `build-logic`'s `fixturesPublicationCheck` publishes a signed copy and resolves it from three build shapes; `webtools-http`'s `check` runs it when WebTools is the root build. One staging deviation; no version moved, and `webtools-http` 1.0.0 stays unreleased.",
        "changes": [
          {
            "what": "The fixtures-publication check hangs off `webtools-http`'s own `check`, declared only when WebTools is the root build and includes `build-logic`, in place of the root `check` of architecture §6.1, §6.7 and §11.",
            "why": "The root project has no `check`; a root lifecycle `check` is reached by `./gradlew check` only, not by `build` or `:webtools-http:check`, and gives a consumer's composite a root task to address (#63 research, publication-check-wiring F6). This settles the open point of `impact.md` §7 and §8.",
            "when": "#63 plan",
            "ref": "63-test-support/plan.md#610-wiring-the-check-into-build-and-check"
          },
          {
            "what": "The signing of the multiplatform publication with its adhoc variant is exercised on every run of the check with a throwaway key; only Central's validator is left for the first release, where architecture §12 placed both.",
            "why": "The check publishes a signed copy and asserts an `.asc` beside every file, the fixtures jar's included (#63 research, publication-check-mechanics F3, F4); an unsigned copy could not show the fixtures jar signed.",
            "when": "#63 plan",
            "ref": "63-test-support/plan.md#69-the-publication-check"
          },
          {
            "what": "`Fixture.toMockResponse()` and `MockWebServer.enqueue(fixture)` land with unit 6, #65, as §11 gives them, although §6.7 lists them among this unit's types.",
            "why": "They belong with the transport unit's `LoopbackTransports`; `Fixture` exposes the status, headers and body they need, and `mockwebserver3` is already on the fixtures' API.",
            "when": "#63 plan",
            "ref": "63-test-support/plan.md#4-non-goals"
          }
        ],
        "commits": [
          "025b245",
          "5048b68",
          "a95f5cb",
          "f325b20"
        ],
        "verified": "qa pass 1 changes requested (19 documentation, 15 test, 2 code findings; 27 of them minor), answered by a plan ruling and the fixes; qa pass 2 changes requested (15 documentation, 8 test, 3 code; 25 minor), fixed in place with no third pass; 384 tests green, among them the fixtures' 141 and the check's 10.",
        "link": "63-test-support/final-implementation.md"
      }
    },
    {
      "id": "url-policy",
      "group": "webtools-http",
      "name": "Outbound-URL policy",
      "system": "webtools-http-url-policy",
      "where": "webtools-http/src/commonMain (policy, classifier), jvmMain (enforcement)",
      "unit": {
        "n": 5,
        "slug": "url-policy",
        "issue": 64,
        "level": 3,
        "blockedBy": [
          62
        ],
        "versions": "none",
        "branch": null,
        "folder": "64-url-policy/",
        "board": "64-url-policy/dashboard.html",
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
          },
          "rowNames": {
            "policy-pair": "`OutboundUrlPolicy`, the closed `Trusted`/`Untrusted` pair",
            "trusted": "`Trusted`: scheme, user-info, pinning and cleartext",
            "untrusted": "`Untrusted`, with `CidrBlock` and `MixedAnswers`",
            "transport-settings": "`TransportSettings`, one default set per policy",
            "address-table": "`AddressRanges`, the IANA deny table",
            "host-rules": "`StrictIp` and `HostRules`, the host-text rules",
            "url-guard": "`UrlGuard` and `GuardRelaxation`",
            "jvm-guards": "`GuardedDns`, `GuardedSocketFactory` and the marker exceptions",
            "classifier-tests": "The classifier's Level 4a tables",
            "jvm-tests": "The guards' Level 2 tests and the `jvmTest` gap and differential tests"
          }
        }
      },
      "gap": {
        "kind": "risk",
        "summary": "Nothing decides which URLs may be fetched. OkHttp never consults `Dns` for a digits-and-dots or colon host, so a resolver guard alone misses every IP-literal host, and native redirect follow-up reached `169.254.169.254` through a resolver guard.",
        "evidence": [
          "research/outbound-url-policy.md F1, F4",
          "research/http-transport.md — interceptors run after the connect"
        ],
        "source": "research/README.md § Outbound-URL policy",
        "link": "research/README.md#outbound-url-policy"
      },
      "options": [
        {
          "id": "three-layers",
          "name": "A closed `Trusted`/`Untrusted` pair checked in three layers: pre-check, resolver guard, socket factory",
          "verdict": "accepted",
          "why": "Every layer alone leaks (measured on 39 host forms); together the socket factory backstops the resolver guard.",
          "source": "architecture.md §6.3.1",
          "link": "architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement"
        },
        {
          "id": "single-guard",
          "name": "A resolver guard only, or a network interceptor or `EventListener` as the guard",
          "verdict": "declined",
          "why": "A resolver guard misses every IP-literal host; interceptors run after the connect and a listener cannot veto.",
          "source": "architecture.md §10",
          "link": "architecture.md#10-alternatives-considered"
        },
        {
          "id": "guard-seam",
          "name": "A consumer-implemented `UrlGuard` seam at 1.0.0",
          "verdict": "declined",
          "why": "A callback that can return \"allow\" can silently defeat `Untrusted`, and no real consumer has shaped the seam.",
          "wins": "Later, as a deny-only interface.",
          "source": "architecture.md §10"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One subsystem, additive.",
        "steps": [],
        "tests": [
          "Level 4a in `commonTest`: every IANA row's first and last address, the host-form catalogue, the published bypass vectors",
          "`jvmTest`: the JDK-predicate-gap and differential tests"
        ],
        "leaves": [
          "Wiring into a client (unit 6)"
        ],
        "risks": [
          "Every `TransportSettings` number is a judgement default, not a measurement"
        ],
        "source": "architecture.md §11 unit 5",
        "link": "architecture.md#11-decomposition",
        "statements": [
          {
            "text": "Deliver `OutboundUrlPolicy` with `Trusted` and `Untrusted` builders, and `TransportSettings` with both default sets.",
            "ref": "architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement",
            "excerpt": {
              "ref": "architecture.md#631-outbound-url-policy-common-plus-jvm-enforcement",
              "heading": "6.3.1 Outbound-URL policy (`common/`, plus `jvm/` enforcement)",
              "paragraph": "Responsibility: decide, for every hop and every resolved address, whether a fetch may proceed, from data parameters that can only tighten. · Owns: · `OutboundUrlPolicy`, a sealed pair (the closed set is the contract): `Trusted` (optional `pinnedHosts`; `allowCleartext = false`; no address filtering, no name or port rule) and `Untrusted` (`allowedPorts` default `{80, 443, 8080, 8443}`, `null` for any; `extraDeniedRanges`; `extraDeniedHostSuffixes`; `mixedAnswers` default `Reject`). Each is built by a factory with a builder (`OutboundUrlPolicy.trusted { }`, `untrusted { }`) and carries its …",
              "hash": "c2efdc00f29c"
            }
          },
          {
            "text": "Put the `internal` classification core in `commonMain`.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          },
          {
            "text": "Deliver the JVM enforcement types with stub-based Level 2 tests.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "landed",
        "summary": "Landed through PR #86 as 53965c8 (#64): `webtools-http` gains the outbound-URL policies `Trusted` and `Untrusted`, `TransportSettings` with its two named default sets, `CidrBlock`, `MixedAnswers` and `WebToolsDsl`, the DSL marker on every builder of the module, all Stable Core, over an `internal` common classifier and `internal` JVM resolver and socket guards. Nothing is enforced until the transport (#65) and the call facades (#68) land. No deviation from the plan; no version moved, and `webtools-http` 1.0.0 stays unreleased.",
        "changes": [
          {
            "what": "The address table denies two addresses no IANA registry lists: `168.63.129.16/32`, the Azure platform endpoint, and an ISATAP interface identifier inside `2000::/3`.",
            "why": "Both close published bypasses of a table built from the registries alone (#64 research, bypass-vectors §4); architecture §6.3.1 carries a dated correction.",
            "when": "#64 plan",
            "ref": "64-url-policy/impact.md#5-where-the-design-refines-the-parent-documents"
          },
          {
            "what": "The socket guard creates its own `Socket(Proxy.NO_PROXY)` sockets, and the resolver guard inherits OkHttp 5.5.0's `Dns.newCall`.",
            "why": "`proxy(Proxy.NO_PROXY)` on the client leaves a JVM-wide SOCKS setting in force at the socket layer, and a forwarded `newCall` would let a record-capable resolver bypass the guard (#64 research, jvm-enforcement F1, F4); architecture §6.3.1 carries a dated correction.",
            "when": "#64 plan",
            "ref": "64-url-policy/impact.md#5-where-the-design-refines-the-parent-documents"
          },
          {
            "what": "A refusal carries the range class beside the reason, so the transport's one log line can name it.",
            "why": "The log line of architecture §6.6 carries the class; §7.4 carries a dated correction.",
            "when": "#64 plan",
            "ref": "64-url-policy/impact.md#5-where-the-design-refines-the-parent-documents"
          },
          {
            "what": "`WebToolsDsl`, a public DSL marker, marks every builder of `webtools-http`, the three existing ones included, and every later unit's builder carries it.",
            "why": "Plan Amendment 3 (stage S8) keeps one DSL across the stack; `webtools-http` is unreleased, so no published consumer's lambda changes.",
            "when": "#64 S8",
            "ref": "64-url-policy/final-implementation.md#s8--the-webtoolsdsl-marker-plan-amendment-3"
          }
        ],
        "commits": [
          "fd93ff9",
          "b46f58a",
          "bec8615",
          "6b96866",
          "0d44495",
          "543bd08",
          "dd463bc"
        ],
        "verified": "qa pass 1 changes requested (19 documentation, 4 test findings; 23 minor), fixed in place; qa pass 2 changes requested (15 documentation, 4 test; 18 minor, 1 other), fixed in place with no third pass; 504 tests in 90 classes green, the change's 36 classes and 232 tests among them.",
        "link": "64-url-policy/final-implementation.md"
      }
    },
    {
      "id": "okhttp-transport",
      "group": "webtools-http",
      "name": "OkHttp transport",
      "system": "webtools-http-transport",
      "where": "webtools-http/src/jvmMain",
      "unit": {
        "n": 6,
        "slug": "okhttp-transport",
        "issue": 65,
        "level": 3,
        "blockedBy": [
          64,
          63
        ],
        "versions": "none",
        "branch": null,
        "folder": "65-okhttp-transport/",
        "board": "65-okhttp-transport/dashboard.html",
        "live": {
          "step": "document",
          "since": "2026-11-03T00:00:03Z",
          "updated": "2026-11-03T00:33:11Z",
          "detail": "Document: 6 of 7 tasks done; State boards at the code-landing commit current.",
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
          "waitingOn": null,
          "resume": null,
          "qa": [
            {
              "pass": 1,
              "verdict": "changes-requested",
              "date": "2026-11-02",
              "at": "2026-11-02T00:08:52Z",
              "findings": {
                "test": 4,
                "code": 0
              },
              "severity": {
                "minor": 2,
                "other": 2
              },
              "route": "remediation",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/65-okhttp-transport/2f43cfc3/tests/index.html",
              "note": "Code review passed: every plan item built, all eight acceptance criteria met, the issue's scope and the sibling interfaces hold. Four test findings, all in the As built rows the fixes touch: HopResponse.settleResponse has no isolated Level 2 component test; no test at any level reaches the hop loop's pre-hop deadline branch (under 1 ms left gives Timeout with no Call); Throwable.firstInChain has no direct Level 4a deterministic test (minor); three pure table functions are tested at Level 2 rather than 4a (minor, placement only). Suite: 711 passed, 0 failed, 0 skipped."
            },
            {
              "pass": 2,
              "verdict": "changes-requested",
              "date": "2026-11-02",
              "at": "2026-11-02T02:06:09Z",
              "findings": {
                "test": 2,
                "code": 0
              },
              "severity": {
                "minor": 2,
                "other": 0
              },
              "route": "on-the-spot",
              "report": "build/reports/tests/index.html",
              "reportCopy": "55-http-client-stack/65-okhttp-transport/cefdb082/tests/index.html",
              "note": "Code review passed: the deadline seam is inert at its defaults, the public factory and the ABI dump are unchanged, and every moved assertion is preserved; one plan-bookkeeping note (the §9 tables and Acceptance 2 still name the pre-move test homes) goes to the plan's reconciliation. All four pass-1 findings verified answered. Two minor test findings, both in the As built rows the fixes touch, fixed on the spot: the fixture `webtoolsUrl`'s three refusal branches have no test (a `WebtoolsUrlComponentTest` is added); `receiveHeaders`' four pure table cases sit at Level 2 rather than 4a (moved, for consistency with pass 1). Suite, all five levels forced: 731 passed, 1 failed, 0 skipped. The one failure is not this change's: #62's untouched `UrlParseLinearTimeNonFunctionalTest` times out under parallel task execution and passes 31/31 in isolation, per its own documented policy; it is being filed as its own issue."
            }
          ],
          "qaMax": 3,
          "status": "building",
          "branch": "feat/issue-65-okhttp-transport",
          "event": {
            "name": "run-restarted",
            "at": "2026-11-03T00:33:11Z",
            "note": "run not running 19 h 43 min: session restart"
          },
          "stages": [
            {
              "id": "S1",
              "name": "TransportControlledHeaders and its component test; commoncheck compile",
              "state": "done",
              "rows": [
                "transport"
              ]
            },
            {
              "id": "S2",
              "name": "Pure parts with Level 2 and 4a tests",
              "state": "done",
              "rows": [
                "transport",
                "one-exchange",
                "redirects",
                "body-read",
                "guards",
                "failure-mapping"
              ]
            },
            {
              "id": "S3",
              "name": "TransportClient, HopResponse, CallBridge, OkHttpTransport and factory; apiDump",
              "state": "done",
              "rows": [
                "transport",
                "one-exchange",
                "redirects",
                "body-read",
                "guards",
                "failure-mapping",
                "lifecycle",
                "release",
                "dispatcher-sizing"
              ]
            },
            {
              "id": "S4",
              "name": "Fixtures and the FakeHttpTransport cap rule",
              "state": "done",
              "rows": [
                "fixtures"
              ]
            },
            {
              "id": "S5",
              "name": "Level 3 suites against mockwebserver3",
              "state": "done",
              "rows": [
                "integration-tests"
              ]
            },
            {
              "id": "S6",
              "name": "Level 4c suites; verifyJavaFloor; full check; mutations",
              "state": "done",
              "rows": [
                "nonfunctional-tests",
                "release"
              ]
            },
            {
              "id": "R1",
              "name": "Remediation: HopResponseComponentTest, ThrowableSearchDeterministicTest, 4a placement of the table cases",
              "state": "done",
              "rows": [
                "transport",
                "one-exchange",
                "body-read",
                "failure-mapping"
              ]
            },
            {
              "id": "R2",
              "name": "Remediation: time-source and call-factory seam on the transport's internal constructor, OkHttpTransportDeadlineComponentTest, procedure P13",
              "state": "done",
              "rows": [
                "transport",
                "redirects"
              ]
            }
          ],
          "remediation": {
            "pass": 2,
            "route": "on-the-spot",
            "steps": [
              {
                "name": "WebtoolsUrlComponentTest for the fixture's refusal branches",
                "state": "current"
              },
              {
                "name": "receiveHeaders table cases moved to ReceiveHeadersDeterministicTest",
                "state": "current"
              }
            ]
          },
          "document": {
            "tasks": [
              {
                "id": "documentation-review",
                "name": "Documentation review of the change set",
                "state": "done"
              },
              {
                "id": "reconcile-documentation",
                "name": "Apply the review's findings to KDoc and comments; second review",
                "state": "done"
              },
              {
                "id": "dokka-check",
                "name": "Dokka check (not applicable: WebTools applies no Dokka plugin)",
                "state": "done"
              },
              {
                "id": "reconcile-plan",
                "name": "Reconcile final-implementation.md and consequences.md; parent link line; architecture callouts; rename #55's impact.md to consequences.md",
                "state": "done"
              },
              {
                "id": "readme-changelog",
                "name": "README delta and CHANGELOG.md (new, Unreleased entry)",
                "state": "done"
              },
              {
                "id": "state-boards",
                "name": "State boards at the code-landing commit",
                "state": "current"
              },
              {
                "id": "prepare-landing",
                "name": "Commit the change set, sync with master, no version bump",
                "state": "done"
              }
            ]
          },
          "open": [
            {
              "text": "Close-step-throws case of OkHttpTransportCloseComponentTest not directly testable: the dispatcher is private and no step can be made to throw from outside",
              "raised": "implement",
              "resolved": "accepted as a recorded gap; settled at reconciliation in consequences.md"
            },
            {
              "text": "D3 exact-class reading of SocketException in hasBenignCause awaits confirmation",
              "raised": "implement",
              "resolved": "confirmed: only a direct java.net.SocketException counts as a benign cause"
            },
            {
              "text": "D2 partial Level 2 coverage and D5 unrun mutation await a decision",
              "raised": "implement",
              "resolved": "accepted as recorded deviations; settled at reconciliation"
            },
            {
              "text": "CancellationIntegrationTest 'close during an in-flight call ends it with Closed and every later call is Closed' never runs: its runBlocking body returns the assertion's value",
              "raised": "document",
              "resolved": "fixed in this landing: the function is declared Unit; integration becomes 94 tests"
            },
            {
              "text": "Landed #47/#61/#62/#63 plan and impact documents and docs/plans/README.md held pre-existing internal names; reworded (meaning kept) so the document-landing commit passes the scan",
              "raised": "document",
              "resolved": "rewordings accepted"
            }
          ],
          "history": {
            "sizing": [
              {
                "since": null,
                "ended": "2026-11-01T17:10:00Z",
                "endedBy": "sized",
                "to": "plan"
              }
            ],
            "plan": [
              {
                "since": "2026-11-01T17:10:00Z",
                "ended": "2026-11-01T17:30:52Z",
                "endedBy": "halted",
                "to": "plan",
                "held": true,
                "event": {
                  "name": "sized",
                  "at": "2026-11-01T17:10:00Z"
                },
                "plan": {
                  "state": "re-pass",
                  "from": "parent",
                  "since": "2026-11-01T17:10:00Z"
                }
              },
              {
                "since": "2026-11-01T17:43:53Z",
                "ended": "2026-11-01T17:44:30Z",
                "endedBy": "research-planning",
                "to": "research",
                "event": {
                  "name": "resumed",
                  "at": "2026-11-01T17:43:53Z"
                },
                "detail": "Answers: a request carrying a transport-controlled header (User-Agent, Accept-Encoding, Host, Connection, Content-Length, Transfer-Encoding and the hop-by-hop names) is refused with TransportFailure.InvalidRequest before anything is sent; the published fake skips its body cap for HEAD and for 1xx, 204 and 304, fixed in this unit; CHANGELOG.md is created in this landing with the unit's entry under Unreleased. The IDE checks recorded by #62-#64 do not gate this run. Next: three research questions on mockwebserver3 scenarios, per-hop request headers and OkHttp's exception surface.",
                "plan": {
                  "state": "re-pass",
                  "from": "parent",
                  "since": "2026-11-01T17:10:00Z"
                }
              },
              {
                "since": "2026-11-01T19:03:40Z",
                "ended": "2026-11-01T21:31:00Z",
                "endedBy": "plan-accepted",
                "to": "branch",
                "event": {
                  "name": "researched",
                  "at": "2026-11-01T19:03:40Z"
                },
                "detail": "Verification complete: every load-bearing claim holds, five with qualifications that change no conclusion. plan.md and consequences.md are drafted (uncommitted; they ride the first implementation commit). Seven design choices the interview did not spell out are being confirmed before implementation.",
                "plan": {
                  "state": "writing",
                  "since": "2026-11-01T19:03:40Z"
                }
              }
            ],
            "research": [
              {
                "since": "2026-11-01T17:44:30Z",
                "ended": "2026-11-01T19:03:40Z",
                "endedBy": "researched",
                "to": "plan",
                "event": {
                  "name": "verification-started",
                  "at": "2026-11-01T18:14:30Z"
                },
                "detail": "Research: verifying 4 findings.",
                "research": {
                  "phase": "complete",
                  "drafted": 0,
                  "since": {
                    "planning": "2026-11-01T17:44:30Z",
                    "research": "2026-11-01T17:48:08Z",
                    "verification": "2026-11-01T18:14:30Z",
                    "complete": "2026-11-01T19:03:40Z"
                  }
                },
                "questions": [
                  {
                    "question": "loopback-test-mechanics",
                    "rows": [
                      "fixtures",
                      "integration-tests",
                      "nonfunctional-tests"
                    ],
                    "findings": "research/loopback-test-mechanics.md",
                    "state": "complete",
                    "started": "2026-11-01T17:48:08Z",
                    "returned": "2026-11-01T18:10:50Z"
                  },
                  {
                    "question": "transport-controlled-headers",
                    "rows": [
                      "transport"
                    ],
                    "findings": "research/transport-controlled-headers.md",
                    "state": "complete",
                    "started": "2026-11-01T17:48:08Z",
                    "returned": "2026-11-01T18:01:30Z"
                  },
                  {
                    "question": "redirect-decision-table",
                    "rows": [
                      "redirects",
                      "guards"
                    ],
                    "findings": "research/redirect-decision-table.md",
                    "state": "complete",
                    "started": "2026-11-01T17:48:08Z",
                    "returned": "2026-11-01T18:03:50Z"
                  },
                  {
                    "question": "okhttp-failure-surface",
                    "rows": [
                      "failure-mapping",
                      "body-read",
                      "lifecycle",
                      "one-exchange"
                    ],
                    "findings": "research/okhttp-failure-surface.md",
                    "state": "complete",
                    "started": "2026-11-01T17:48:08Z",
                    "returned": "2026-11-01T18:12:00Z"
                  }
                ]
              }
            ],
            "branch": [
              {
                "since": "2026-11-01T21:31:00Z",
                "ended": "2026-11-01T21:31:35Z",
                "endedBy": "branch-cut",
                "to": "implementation",
                "event": {
                  "name": "plan-accepted",
                  "at": "2026-11-01T21:31:00Z"
                },
                "detail": "A final OkHttpTransport over OkHttp 5.5.0 built from network-free parts (controlled-header list, redirect decision, failure mapping, capped reader, ExchangeGuard, call deadline, value-only coroutine bridge) with its own daemon dispatcher and pool; the four loopback fixtures and the fake's no-body cap rule; every Level 3 and 4c scenario scripted against mockwebserver3. Seven implementation choices confirmed, among them: Range admitted with identity coding, a 421 delivered as a response, PolicyRefusals.kt's chain walk shared as firstInChain, okhttp-tls in jvmTest only.",
                "branch": "feat/issue-65-okhttp-transport"
              }
            ],
            "implementation": [
              {
                "since": "2026-11-01T21:31:35Z",
                "ended": "2026-11-01T22:37:24Z",
                "endedBy": "implementation-stage",
                "to": "qa",
                "event": {
                  "name": "branch-cut",
                  "at": "2026-11-01T21:31:35Z"
                },
                "detail": "Branch confirmed, base 53965c8 is current master. The implementer builds stages S1 to S6 in the worktree; stage S7 (README, CHANGELOG, as-built reconciliation) is the Document step's.",
                "stages": [
                  {
                    "id": "S1",
                    "name": "TransportControlledHeaders and its component test; commoncheck compile",
                    "state": "done",
                    "rows": [
                      "transport"
                    ]
                  },
                  {
                    "id": "S2",
                    "name": "Pure parts: ThrowableSearch, RedirectDecision, FailureMapping, HopRequest, ReceivedResponse, CappedBody, ExchangeGuard, CallDeadline, with Level 2 and 4a tests",
                    "state": "done",
                    "rows": [
                      "transport",
                      "one-exchange",
                      "redirects",
                      "body-read",
                      "guards",
                      "failure-mapping"
                    ]
                  },
                  {
                    "id": "S3",
                    "name": "TransportClient, HopResponse, CallBridge, OkHttpTransport and factory; apiDump",
                    "state": "done",
                    "rows": [
                      "transport",
                      "one-exchange",
                      "redirects",
                      "body-read",
                      "guards",
                      "failure-mapping",
                      "lifecycle",
                      "release",
                      "dispatcher-sizing"
                    ]
                  },
                  {
                    "id": "S4",
                    "name": "Fixtures: LoopbackTransports, webtoolsUrl, toMockResponse, enqueue; FakeHttpTransport cap rule",
                    "state": "done",
                    "rows": [
                      "fixtures"
                    ]
                  },
                  {
                    "id": "S5",
                    "name": "Level 3 suites against mockwebserver3; okhttp-tls in jvmTest",
                    "state": "done",
                    "rows": [
                      "integration-tests"
                    ]
                  },
                  {
                    "id": "S6",
                    "name": "Level 4c suites; verifyJavaFloor; full check; mutations; unaffected-module proofs",
                    "state": "done",
                    "rows": [
                      "nonfunctional-tests",
                      "release"
                    ]
                  }
                ],
                "held": true
              }
            ],
            "qa": [
              {
                "since": "2026-11-01T23:43:35Z",
                "ended": "2026-11-02T00:08:52Z",
                "endedBy": "qa-pass",
                "to": "remediation",
                "event": {
                  "name": "qa-pass",
                  "at": "2026-11-02T00:08:52Z",
                  "note": "pass 1: changes-requested"
                },
                "detail": "Code review PASS: every plan item built, all eight acceptance criteria met, level and sibling interfaces hold. Test findings: HopResponse.settleResponse has no isolated Level 2 test; the hop loop's pre-hop deadline branch (remaining under 1 ms gives Timeout with no Call) is reached by no test at any level; the extracted Throwable.firstInChain has no direct 4a test (minor); three pure table functions are tested at Level 2 rather than 4a (minor, placement only). Suite 711 passed, 0 failed, 0 skipped.",
                "qa": [
                  {
                    "pass": 1,
                    "verdict": "changes-requested",
                    "date": "2026-11-02",
                    "at": "2026-11-02T00:08:52Z",
                    "findings": {
                      "test": 4,
                      "code": 0
                    },
                    "severity": {
                      "minor": 2,
                      "other": 2
                    },
                    "route": "remediation",
                    "report": "build/reports/tests/index.html",
                    "reportCopy": "55-http-client-stack/65-okhttp-transport/2f43cfc3/tests/index.html"
                  }
                ]
              },
              {
                "since": "2026-11-02T01:45:02Z",
                "ended": "2026-11-02T02:06:09Z",
                "endedBy": "qa-pass",
                "to": "remediation",
                "event": {
                  "name": "implementation-stage",
                  "at": "2026-11-02T01:45:02Z",
                  "note": "R2"
                },
                "detail": "R2 built exactly as amended: six edits to OkHttpTransport.kt (import, KDoc paragraph, two trailing defaulted constructor parameters, hopCalls, the deadline's time source, hopCalls.newCall). The three deadline cases pass; the ABI dump hash is unchanged and names neither TimeSource nor Call.Factory; LoopbackTransports unchanged; mutations 14-16 each fail their named cases; webtools-http check green (732 jvmTest tests). Per-level: gating 19, component 398, integration 93, deterministic 191, non-functional 31, 0 failed, 0 skipped.",
                "qa": [
                  {
                    "pass": 2,
                    "verdict": "changes-requested",
                    "date": "2026-11-02",
                    "at": "2026-11-02T02:06:09Z",
                    "findings": {
                      "test": 2,
                      "code": 0
                    },
                    "severity": {
                      "minor": 2,
                      "other": 0
                    },
                    "route": "on-the-spot",
                    "report": "build/reports/tests/index.html",
                    "reportCopy": "55-http-client-stack/65-okhttp-transport/cefdb082/tests/index.html"
                  }
                ]
              }
            ],
            "remediation": [
              {
                "since": "2026-11-02T00:08:52Z",
                "ended": "2026-11-02T01:45:02Z",
                "endedBy": "implementation-stage",
                "to": "qa",
                "event": {
                  "name": "implementation-stage",
                  "at": "2026-11-02T01:34:38Z",
                  "note": "R2"
                },
                "detail": "Plan amended 2026-11-02 after the test-coverage review: two defaulted parameters trail the internal constructor, a TimeSource for the shared call deadline and a Call.Factory for each hop's call, so a Level 2 test with fake calls and a manual clock reaches the pre-hop deadline check; the ABI dump, the public factory and the fixtures are unchanged. New stage S6a, procedure P13 and mutations 14-16. #68 never passes either parameter.",
                "remediation": {
                  "pass": 1,
                  "route": "re-plan-then-remediation",
                  "steps": [
                    {
                      "name": "Plan amendment: a time-source and call-factory seam on the transport's internal constructor for the pre-hop deadline test",
                      "state": "done"
                    },
                    {
                      "name": "Fixes independent of the amendment",
                      "state": "done"
                    },
                    {
                      "name": "The deadline branch's Level 2 test over the seam (stage S6a, procedure P13)",
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
                    "date": "2026-11-02",
                    "at": "2026-11-02T00:08:52Z",
                    "findings": {
                      "test": 4,
                      "code": 0
                    },
                    "severity": {
                      "minor": 2,
                      "other": 2
                    },
                    "route": "remediation",
                    "report": "build/reports/tests/index.html",
                    "reportCopy": "55-http-client-stack/65-okhttp-transport/2f43cfc3/tests/index.html",
                    "note": "Code review passed: every plan item built, all eight acceptance criteria met, the issue's scope and the sibling interfaces hold. Four test findings, all in the As built rows the fixes touch: HopResponse.settleResponse has no isolated Level 2 component test; no test at any level reaches the hop loop's pre-hop deadline branch (under 1 ms left gives Timeout with no Call); Throwable.firstInChain has no direct Level 4a deterministic test (minor); three pure table functions are tested at Level 2 rather than 4a (minor, placement only). Suite: 711 passed, 0 failed, 0 skipped."
                  }
                ]
              },
              {
                "since": "2026-11-02T02:06:09Z",
                "ended": "2026-11-02T02:11:47Z",
                "endedBy": "document",
                "to": "document",
                "event": {
                  "name": "qa-pass",
                  "at": "2026-11-02T02:06:09Z",
                  "note": "pass 2: changes-requested"
                },
                "detail": "All four pass-1 findings verified answered. Two minor test findings: the fixture webtoolsUrl's three refusal branches have no test (a WebtoolsUrlComponentTest is added); receiveHeaders' four pure table cases sit at Level 2 rather than 4a (moved for consistency with pass 1). Code review PASS: the deadline seam is inert at its defaults, the public factory and ABI dump unchanged, every moved assertion preserved; one plan-bookkeeping note (the §9 tables and Acceptance 2 still name the pre-move test homes) goes to the plan's reconciliation. Forced run of all five levels: 731 passed, 1 failed, 0 skipped; the one failure is the untouched UrlParseLinearTimeNonFunctionalTest from #62 timing out under parallel task execution, green 31/31 in isolation per its own documented policy.",
                "remediation": {
                  "pass": 2,
                  "route": "on-the-spot",
                  "steps": [
                    {
                      "name": "WebtoolsUrlComponentTest for the fixture's refusal branches",
                      "state": "current"
                    },
                    {
                      "name": "receiveHeaders table cases moved to ReceiveHeadersDeterministicTest",
                      "state": "current"
                    }
                  ]
                },
                "qa": [
                  {
                    "pass": 2,
                    "verdict": "changes-requested",
                    "date": "2026-11-02",
                    "at": "2026-11-02T02:06:09Z",
                    "findings": {
                      "test": 2,
                      "code": 0
                    },
                    "severity": {
                      "minor": 2,
                      "other": 0
                    },
                    "route": "on-the-spot",
                    "report": "build/reports/tests/index.html",
                    "reportCopy": "55-http-client-stack/65-okhttp-transport/cefdb082/tests/index.html",
                    "note": "Code review passed: the deadline seam is inert at its defaults, the public factory and the ABI dump are unchanged, and every moved assertion is preserved; one plan-bookkeeping note (the §9 tables and Acceptance 2 still name the pre-move test homes) goes to the plan's reconciliation. All four pass-1 findings verified answered. Two minor test findings, both in the As built rows the fixes touch, fixed on the spot: the fixture `webtoolsUrl`'s three refusal branches have no test (a `WebtoolsUrlComponentTest` is added); `receiveHeaders`' four pure table cases sit at Level 2 rather than 4a (moved, for consistency with pass 1). Suite, all five levels forced: 731 passed, 1 failed, 0 skipped. The one failure is not this change's: #62's untouched `UrlParseLinearTimeNonFunctionalTest` times out under parallel task execution and passes 31/31 in isolation, per its own documented policy; it is being filed as its own issue."
                  }
                ]
              }
            ],
            "document": [
              {
                "since": "2026-11-02T02:11:47Z",
                "ended": "2026-11-02T03:05:18Z",
                "endedBy": "halted",
                "to": "document",
                "event": {
                  "name": "document",
                  "at": "2026-11-02T02:44:45Z",
                  "note": "5 of 7 tasks done"
                },
                "detail": "README: the webtools-http artifact row names OkHttpTransport (one request per hop, manual redirects, capped decoded body, never a retry) and the fixtures paragraph names LoopbackTransports, webtoolsUrl, toMockResponse and enqueue(fixture); the dashboards pointer and install block untouched. CHANGELOG.md created in the Keep a Changelog layout: Unreleased, Added (the transport and factory; the four fixture helpers), Changed (the fake's cap skipped for HEAD, 1xx, 204, 304). No version section: nothing moved.",
                "document": {
                  "tasks": [
                    {
                      "id": "documentation-review",
                      "name": "Documentation review of the change set",
                      "state": "done"
                    },
                    {
                      "id": "reconcile-documentation",
                      "name": "Apply the review's findings to KDoc and comments; second review",
                      "state": "done"
                    },
                    {
                      "id": "dokka-check",
                      "name": "Dokka check (not applicable: WebTools applies no Dokka plugin)",
                      "state": "done"
                    },
                    {
                      "id": "reconcile-plan",
                      "name": "Reconcile final-implementation.md and consequences.md; parent link line; architecture callouts; rename #55's impact.md to consequences.md",
                      "state": "current"
                    },
                    {
                      "id": "readme-changelog",
                      "name": "README delta and CHANGELOG.md (new, Unreleased entry)",
                      "state": "done"
                    },
                    {
                      "id": "state-boards",
                      "name": "State boards at the code-landing commit",
                      "state": "todo"
                    },
                    {
                      "id": "prepare-landing",
                      "name": "Commit the change set, sync with master, no version bump",
                      "state": "done"
                    }
                  ]
                },
                "held": true
              },
              {
                "since": "2026-11-02T03:07:30Z",
                "ended": "2026-11-02T04:16:35Z",
                "endedBy": "run-restarted",
                "to": "document",
                "event": {
                  "name": "state-boards",
                  "at": "2026-11-02T04:14:48Z",
                  "note": "the state boards; 6 of 7 tasks done"
                },
                "detail": "Document: 6 of 7 tasks done; State boards at the code-landing commit current.",
                "document": {
                  "tasks": [
                    {
                      "id": "documentation-review",
                      "name": "Documentation review of the change set",
                      "state": "done"
                    },
                    {
                      "id": "reconcile-documentation",
                      "name": "Apply the review's findings to KDoc and comments; second review",
                      "state": "done"
                    },
                    {
                      "id": "dokka-check",
                      "name": "Dokka check (not applicable: WebTools applies no Dokka plugin)",
                      "state": "done"
                    },
                    {
                      "id": "reconcile-plan",
                      "name": "Reconcile final-implementation.md and consequences.md; parent link line; architecture callouts; rename #55's impact.md to consequences.md",
                      "state": "done"
                    },
                    {
                      "id": "readme-changelog",
                      "name": "README delta and CHANGELOG.md (new, Unreleased entry)",
                      "state": "done"
                    },
                    {
                      "id": "state-boards",
                      "name": "State boards at the code-landing commit",
                      "state": "current"
                    },
                    {
                      "id": "prepare-landing",
                      "name": "Commit the change set, sync with master, no version bump",
                      "state": "done"
                    }
                  ]
                },
                "outage": "session restart"
              }
            ]
          },
          "rowNames": {
            "transport": "`OkHttpTransport`: the factory, the `internal` constructor and the client it builds",
            "one-exchange": "`ExchangeGuard`: one wire exchange per hop",
            "redirects": "The redirect loop: one `Call` per hop under one deadline",
            "body-read": "The suspend bridge and the capped body read",
            "guards": "The three guard layers applied to the client",
            "failure-mapping": "The failure mapping and the transport's logging",
            "lifecycle": "`close()` and the client's own dispatcher and pool",
            "fixtures": "`LoopbackTransports`, `webtoolsUrl`, `toMockResponse` and `enqueue(fixture)` in the fixtures",
            "integration-tests": "Level 3 tests against `mockwebserver3`",
            "nonfunctional-tests": "Level 4c tests: slow drip, endless chunked body, a redirect chain over the cap",
            "release": "The ABI dump and the version",
            "dispatcher-sizing": "Sizing the dispatcher from the budget's `maxBurst`"
          },
          "recon": {
            "interview": {
              "reconstructed": true,
              "ended": null,
              "trail": [],
              "milestones": []
            }
          }
        },
        "tests": {
          "updated": "2026-11-02T03:16:00Z",
          "added": [
            {
              "class": "HopResponseComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/HopResponseComponentTest.kt",
              "tests": 10,
              "rows": [
                "transport",
                "body-read",
                "one-exchange"
              ],
              "stage": "R1"
            },
            {
              "class": "ThrowableSearchDeterministicTest",
              "module": "webtools-http",
              "testLevel": "4a",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/ThrowableSearchDeterministicTest.kt",
              "tests": 5,
              "rows": [
                "failure-mapping"
              ],
              "stage": "R1"
            },
            {
              "class": "RestoredStatusDeterministicTest",
              "module": "webtools-http",
              "testLevel": "4a",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/RestoredStatusDeterministicTest.kt",
              "tests": 3,
              "rows": [
                "one-exchange"
              ],
              "stage": "R1"
            },
            {
              "class": "BodyExpectedDeterministicTest",
              "module": "webtools-http",
              "testLevel": "4a",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/BodyExpectedDeterministicTest.kt",
              "tests": 2,
              "rows": [
                "body-read"
              ],
              "stage": "R1"
            },
            {
              "class": "TransportControlledHeadersDeterministicTest",
              "module": "webtools-http",
              "testLevel": "4a",
              "path": "webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/TransportControlledHeadersDeterministicTest.kt",
              "tests": 5,
              "rows": [
                "transport"
              ],
              "stage": "R1"
            },
            {
              "class": "OkHttpTransportDeadlineComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/OkHttpTransportDeadlineComponentTest.kt",
              "tests": 3,
              "rows": [
                "transport",
                "redirects"
              ],
              "stage": "R2"
            },
            {
              "class": "WebtoolsUrlComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/testing/WebtoolsUrlComponentTest.kt",
              "tests": 3,
              "rows": [
                "fixtures"
              ]
            },
            {
              "class": "ReceiveHeadersDeterministicTest",
              "module": "webtools-http",
              "testLevel": "4a",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/deterministic/webtools/http/ReceiveHeadersDeterministicTest.kt",
              "tests": 4,
              "rows": [
                "body-read"
              ]
            }
          ],
          "amended": [
            {
              "class": "ExchangeGuardComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/ExchangeGuardComponentTest.kt",
              "tests": 9,
              "rows": [
                "one-exchange"
              ],
              "stage": "R1"
            },
            {
              "class": "ReceivedResponseComponentTest",
              "module": "webtools-http",
              "testLevel": "2",
              "path": "webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/component/webtools/http/ReceivedResponseComponentTest.kt",
              "tests": 1,
              "rows": [
                "body-read"
              ],
              "stage": "R1"
            }
          ],
          "relevant": [
            {
              "class": "gatingTest",
              "module": "webtools-http",
              "tests": 19,
              "result": {
                "state": "passed",
                "passed": 19,
                "failed": 0,
                "skipped": 0,
                "at": "2026-11-02T03:16:00Z"
              }
            },
            {
              "class": "componentTest",
              "module": "webtools-http",
              "tests": 397,
              "result": {
                "state": "passed",
                "passed": 397,
                "failed": 0,
                "skipped": 0,
                "at": "2026-11-02T03:16:00Z"
              }
            },
            {
              "class": "integrationTest",
              "module": "webtools-http",
              "tests": 94,
              "result": {
                "state": "passed",
                "passed": 94,
                "failed": 0,
                "skipped": 0,
                "at": "2026-11-02T03:16:00Z",
                "note": "94 after the in-flight close case was made to run (db82470)"
              }
            },
            {
              "class": "deterministicTest",
              "module": "webtools-http",
              "tests": 195,
              "result": {
                "state": "passed",
                "passed": 195,
                "failed": 0,
                "skipped": 0,
                "at": "2026-11-02T03:16:00Z"
              }
            },
            {
              "class": "nonfunctionalTest",
              "module": "webtools-http",
              "tests": 31,
              "result": {
                "state": "passed",
                "passed": 31,
                "failed": 0,
                "skipped": 0,
                "at": "2026-11-02T03:16:00Z"
              }
            }
          ],
          "other": {
            "byModule": {
              "webtools-browser": {
                "1": {
                  "classes": 1,
                  "tests": 3
                },
                "5": {
                  "classes": 1,
                  "tests": 2
                }
              },
              "webtools-scraping": {
                "2": {
                  "classes": 1,
                  "tests": 11
                }
              },
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
                  "tests": 117
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
                  "tests": 67
                }
              }
            }
          },
          "rowNames": {
            "transport": "`OkHttpTransport`: the factory, the `internal` constructor and the client it builds",
            "one-exchange": "`ExchangeGuard`: one wire exchange per hop",
            "redirects": "The redirect loop: one `Call` per hop under one deadline",
            "body-read": "The suspend bridge and the capped body read",
            "failure-mapping": "The failure mapping and the transport's logging",
            "fixtures": "`LoopbackTransports`, `webtoolsUrl`, `toMockResponse` and `enqueue(fixture)` in the fixtures"
          }
        }
      },
      "gap": {
        "kind": "missing",
        "summary": "OkHttp 5.5.0 re-sends a `503` carrying `Retry-After: 0` and a `421` regardless of client flags, strips only `Authorization` on a cross-host redirect, and a body read after `executeAsync` returns is not cancellable (29.4 s against 0 ms).",
        "evidence": [
          "research/http-transport.md F1-F5"
        ],
        "source": "research/README.md § Transport",
        "link": "research/README.md#transport"
      },
      "options": [
        {
          "id": "manual-loop",
          "name": "A manual redirect loop, a fail-closed one-exchange guard and a capped body read inside the callback",
          "verdict": "accepted",
          "why": "One request per hop whatever re-send paths OkHttp has, the hop check before the socket, and cancellation that reaches the body.",
          "source": "architecture.md §6.3.2",
          "link": "architecture.md#632-okhttp-transport-jvm"
        },
        {
          "id": "native-redirects",
          "name": "OkHttp-native redirects",
          "verdict": "declined",
          "why": "The hop check would run after the socket, only `Authorization` is stripped, the cap is fixed at 20.",
          "source": "architecture.md §10",
          "link": "architecture.md#10-alternatives-considered"
        },
        {
          "id": "execute-async",
          "name": "`okhttp-coroutines`' `executeAsync` as published",
          "verdict": "declined",
          "why": "It requires coroutines 1.11.0, and the body read after it returns is not cancellable.",
          "source": "architecture.md §10"
        },
        {
          "id": "public-client",
          "name": "A public `OkHttpClient` in a signature",
          "verdict": "declined",
          "why": "No library type in a public signature; the donor client stays an `internal` constructor parameter for the fixtures.",
          "source": "architecture.md §10"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One system: an additive subsystem of `webtools-http`.",
        "steps": [],
        "tests": [
          "Level 3 against `mockwebserver3`: one request for `503` + `Retry-After: 0`, `408`, `429` and each hop; caps; cancel during an endless body; hop 2 refused",
          "Level 4c: slow drip, endless chunked, redirect chain over the cap"
        ],
        "leaves": [
          "Sizing from `maxBurst` (unit 9)"
        ],
        "risks": [
          "A consumer's newer OkHttp 5.x wins resolution; the fail-closed guard does not depend on which re-send paths exist"
        ],
        "source": "architecture.md §11 unit 6",
        "link": "architecture.md#11-decomposition",
        "statements": [
          {
            "text": "Deliver `OkHttpTransport` (factory and `internal` constructor) and `ExchangeGuard`.",
            "ref": "architecture.md#632-okhttp-transport-jvm",
            "excerpt": {
              "ref": "architecture.md#632-okhttp-transport-jvm",
              "heading": "6.3.2 OkHttp transport (`jvm/`)",
              "paragraph": "Responsibility: deliver one request per hop to the network, read a capped body where cancellation can reach it, and map every outcome to the failure model. · Owns: `OkHttpTransport : HttpTransport`, final, built by a factory taking `userAgent` (required, validated) and an `OutboundUrlPolicy`, plus an `internal` constructor (for the fixtures) taking the `UrlGuard`/relaxation, a `Dns` and an optional donor `OkHttpClient`. Inside: · the client: a new `Dispatcher` (sized from the settings; the facade raises `maxConcurrentRequests` to the budget's `maxBurst` when it builds both, because OkHttp's …",
              "hash": "122cd4382789"
            }
          },
          {
            "text": "Deliver the redirect loop, the capped in-callback body read, the failure mapping, `close()` and the transport's logging.",
            "ref": "architecture.md#632-okhttp-transport-jvm",
            "excerpt": {
              "ref": "architecture.md#632-okhttp-transport-jvm",
              "heading": "6.3.2 OkHttp transport (`jvm/`)",
              "paragraph": "Responsibility: deliver one request per hop to the network, read a capped body where cancellation can reach it, and map every outcome to the failure model. · Owns: `OkHttpTransport : HttpTransport`, final, built by a factory taking `userAgent` (required, validated) and an `OutboundUrlPolicy`, plus an `internal` constructor (for the fixtures) taking the `UrlGuard`/relaxation, a `Dns` and an optional donor `OkHttpClient`. Inside: · the client: a new `Dispatcher` (sized from the settings; the facade raises `maxConcurrentRequests` to the budget's `maxBurst` when it builds both, because OkHttp's …",
              "hash": "122cd4382789"
            }
          },
          {
            "text": "Add `LoopbackTransports`, `webtoolsUrl`, `toMockResponse` and `enqueue(fixture)` to the fixtures.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "pending",
        "summary": ""
      }
    },
    {
      "id": "response-cache",
      "group": "webtools-http",
      "name": "Response cache",
      "system": "webtools-http-cache",
      "where": "webtools-http/src/commonMain (port), jvmMain (Caffeine)",
      "unit": {
        "n": 7,
        "slug": "response-cache",
        "issue": 66,
        "level": 3,
        "blockedBy": [
          63,
          62
        ],
        "versions": "none",
        "branch": null,
        "folder": null,
        "board": null
      },
      "gap": {
        "kind": "missing",
        "summary": "No response cache exists. Caffeine 3.3.1's `AsyncCache.get` is single-flight, but exceptional completion logs a WARNING per failure and leaves a joinable failed future, and `CompletionStage.await()` cancels the shared future while `asDeferred().await()` does not.",
        "evidence": [
          "research/response-cache.md F1-F7"
        ],
        "source": "research/README.md § Response cache",
        "link": "research/README.md#response-cache"
      },
      "options": [
        {
          "id": "caffeine-default",
          "name": "A `ResponseCache` port with `CaffeineResponseCache`: failure as a value, `asDeferred` waiting, compare-and-remove for `maxAge`",
          "verdict": "accepted",
          "why": "One load serves many concurrent callers, a failure is never cached, and one waiter's cancellation does not cancel the shared load.",
          "source": "architecture.md §6.4",
          "link": "architecture.md#64-response-cache"
        },
        {
          "id": "cache-variants",
          "name": "A typed cache per endpoint; Aedile; `scope.future { }` with exceptional completion; invalidate-before-get for `maxAge`",
          "verdict": "declined",
          "why": "Multiplies bounds; pins Caffeine 3.2.4 and runs loads in the first caller's context; logs a WARNING per failure; ran 10-12 loads for 50 stale callers against 1.",
          "source": "architecture.md §10",
          "link": "architecture.md#10-alternatives-considered"
        },
        {
          "id": "store5-cancel",
          "name": "Cancelling the shared load when the last waiter leaves (Store5)",
          "verdict": "declined",
          "why": "A ref-counted multicaster with its own race class; the wasted work is bounded by `maxWait` plus the call timeout.",
          "wins": "If abandoned reservations piled up.",
          "source": "architecture.md §10"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One system: an additive subsystem of `webtools-http`.",
        "steps": [],
        "tests": [
          "Level 2 on `VirtualClock`: hits, expiry, miss TTL, `maxAge`, coalescing, one waiter's cancellation, close",
          "Level 4a: key canonicalisation",
          "Level 4c: the size bound and the 2,000-retry failure-identity check"
        ],
        "leaves": [
          "The loader's content (unit 9)"
        ],
        "risks": [
          "A shared load carries the first caller's lane and wait cap"
        ],
        "source": "architecture.md §11 unit 7",
        "link": "architecture.md#11-decomposition",
        "statements": [
          {
            "text": "Deliver the `ResponseCache` port with `CacheKey`, `CachePolicy`, `Lookup`, `CacheStats` and `ResponseCache.Disabled`.",
            "ref": "architecture.md#64-response-cache",
            "excerpt": {
              "ref": "architecture.md#64-response-cache",
              "heading": "6.4 Response cache",
              "paragraph": "Responsibility: one decoded value per key, served to any number of concurrent callers from one load, for a bounded time, with failures never stored. · Owns (`common/`): `ResponseCache : AutoCloseable` (subclass-gated): one suspend `getOrLoad(key, policy, maxAge, loader): Result<Lookup<V>>` (no default on `maxAge`; an overload without it), `invalidate`, `invalidateAll`, `stats()`, `close()`; `CacheKey<V>(endpointId, params)` (phantom-typed; equality over the id and the parameters sorted by name with a stable sort; `toString` prints parameter names only; never a tenant, a header, a credential …",
              "hash": "872bafe44717"
            }
          },
          {
            "text": "Deliver `CaffeineResponseCache` with the load-start, failure-as-value, `asDeferred` waiting, `maxAge` and `Closed` behaviours.",
            "ref": "architecture.md#64-response-cache",
            "excerpt": {
              "ref": "architecture.md#64-response-cache",
              "heading": "6.4 Response cache",
              "paragraph": "Responsibility: one decoded value per key, served to any number of concurrent callers from one load, for a bounded time, with failures never stored. · Owns (`common/`): `ResponseCache : AutoCloseable` (subclass-gated): one suspend `getOrLoad(key, policy, maxAge, loader): Result<Lookup<V>>` (no default on `maxAge`; an overload without it), `invalidate`, `invalidateAll`, `stats()`, `close()`; `CacheKey<V>(endpointId, params)` (phantom-typed; equality over the id and the parameters sorted by name with a stable sort; `toString` prints parameter names only; never a tenant, a header, a credential …",
              "hash": "872bafe44717"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "pending",
        "summary": ""
      }
    },
    {
      "id": "call-budget",
      "group": "webtools-http",
      "name": "Call budget",
      "system": "webtools-http-budget",
      "where": "webtools-http/src/commonMain",
      "unit": {
        "n": 8,
        "slug": "call-budget",
        "issue": 67,
        "level": 3,
        "blockedBy": [
          63,
          62
        ],
        "versions": "none",
        "branch": null,
        "folder": null,
        "board": null
      },
      "gap": {
        "kind": "missing",
        "summary": "No call budget exists. OpenDota and Open-Meteo count in fixed wall-clock windows and count refused requests; a greedy token bucket let 119 calls into one server minute against 60.",
        "evidence": [
          "research/call-budget.md F1, F2, F10"
        ],
        "source": "research/README.md § Call budget",
        "link": "research/README.md#call-budget"
      },
      "options": [
        {
          "id": "windowed",
          "name": "`WindowedCallBudget`: clock-aligned fixed windows with lanes, pauses on server signals and a hard daily stop",
          "verdict": "accepted",
          "why": "Aligned windows with a 1 s guard overspent in 0 of 1,000 skewed trials.",
          "source": "architecture.md §6.5",
          "link": "architecture.md#65-call-budget"
        },
        {
          "id": "bucket",
          "name": "A token bucket, GCRA or Bucket4j as the default",
          "verdict": "declined",
          "why": "A greedy bucket doubles at every provider boundary; GCRA paces below what a clock-reset provider allows; Bucket4j is JVM-only, needs Java 17 and offers no suspending FIFO with refunds, lanes or pauses.",
          "wins": "Later, as another implementation of the port for multi-process counting.",
          "source": "architecture.md §10",
          "link": "architecture.md#10-alternatives-considered"
        },
        {
          "id": "lend-reserve",
          "name": "Lending unused lane reserve",
          "verdict": "declined",
          "why": "In a plan-ahead ledger it drove interactive grants to zero (measured).",
          "source": "architecture.md §10"
        },
        {
          "id": "observer-logging",
          "name": "Logging through `BudgetObserver` only",
          "verdict": "declined",
          "why": "Silent unless every constructing path installs an observer.",
          "source": "architecture.md §10"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One system: an additive subsystem of `webtools-http`.",
        "steps": [],
        "tests": [
          "Level 2 on `VirtualClock`: FIFO, refunds, lane floors, pauses, hard stop, restore, write-ahead after a crash",
          "Level 4a: the HTTP-date table against `java.time`, window arithmetic, a seeded property test"
        ],
        "leaves": [
          "Reading signals from a real response (unit 9)"
        ],
        "risks": [
          "How a `Mutex`-based ledger closes without suspending is unit 8's plan to settle (architecture §13)"
        ],
        "source": "architecture.md §11 unit 8",
        "link": "architecture.md#11-decomposition",
        "statements": [
          {
            "text": "Deliver `CallBudget`, `Permit`, `withPermit`, `Lane`, `RateWindow`, `RateSignals` and their reader, and `RetryAfter`, in `commonMain`.",
            "ref": "architecture.md#65-call-budget",
            "excerpt": {
              "ref": "architecture.md#65-call-budget",
              "heading": "6.5 Call budget",
              "paragraph": "Responsibility: grant permits under a provider's clock-aligned windows and lane shares, pause on server signals, and hold a hard daily stop that survives a restart when a store is supplied. · Owns (`common/`, all of it): · the port `CallBudget : AutoCloseable` (subclass-gated): `acquire(cost, lane, maxWait): Result<Permit>` (fails fast with `BudgetExhausted(wait, reason, window)` when the computed wait exceeds the cap; a cancelled wait is refunded), `utilisation(): BudgetUtilisation`, non-suspend `close()` (marks closed; waiters fail with `Closed`), `suspend flush(): Result<Unit>` (exact …",
              "hash": "7516885e4071"
            }
          },
          {
            "text": "Deliver `BudgetStore` with `InMemoryBudgetStore`, `BudgetObserver`, `CallBudget.Unlimited` and `WindowedCallBudget`.",
            "ref": "architecture.md#65-call-budget",
            "excerpt": {
              "ref": "architecture.md#65-call-budget",
              "heading": "6.5 Call budget",
              "paragraph": "Responsibility: grant permits under a provider's clock-aligned windows and lane shares, pause on server signals, and hold a hard daily stop that survives a restart when a store is supplied. · Owns (`common/`, all of it): · the port `CallBudget : AutoCloseable` (subclass-gated): `acquire(cost, lane, maxWait): Result<Permit>` (fails fast with `BudgetExhausted(wait, reason, window)` when the computed wait exceeds the cap; a cancelled wait is refunded), `utilisation(): BudgetUtilisation`, non-suspend `close()` (marks closed; waiters fail with `Closed`), `suspend flush(): Result<Unit>` (exact …",
              "hash": "7516885e4071"
            }
          },
          {
            "text": "Close with a non-suspend `close()` plus `flush()`.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          },
          {
            "text": "Add `RecordingBudgetObserver` and `FakeBudgetStore` to the fixtures.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "pending",
        "summary": ""
      }
    },
    {
      "id": "call-facades",
      "group": "webtools-http",
      "name": "Call facades",
      "system": "webtools-http-facades",
      "where": "webtools-http/src/commonMain, jvmMain (owning factories)",
      "unit": {
        "n": 9,
        "slug": "call-facades",
        "issue": 68,
        "level": 3,
        "blockedBy": [
          67,
          66,
          65
        ],
        "versions": "none",
        "branch": null,
        "folder": null,
        "board": null
      },
      "gap": {
        "kind": "missing",
        "summary": "The cache, the budget and the transport do not compose themselves, and the order they run in decides what a refusal, a throttle or a decode failure costs a caller.",
        "evidence": [
          "architecture.md §7.2 — cache, then budget inside the loader, then transport"
        ],
        "source": "architecture.md §7.2",
        "link": "architecture.md#72-the-call-path-cache-then-budget-inside-the-loader-then-transport"
      },
      "options": [
        {
          "id": "two-facades",
          "name": "Two facades: `UpstreamClient` for endpoints and `UrlFetcher` for administrator-supplied URLs",
          "verdict": "accepted",
          "why": "Two types encode the invariant that an untrusted URL cannot reach `call` and `fetch` cannot be handed an endpoint's cache.",
          "source": "architecture.md §6.6",
          "link": "architecture.md#66-call-facades"
        },
        {
          "id": "one-facade",
          "name": "One facade class",
          "verdict": "declined",
          "why": "Loses that invariant.",
          "source": "architecture.md §10",
          "link": "architecture.md#10-alternatives-considered"
        },
        {
          "id": "decoder-per-call",
          "name": "A decoder per call",
          "verdict": "declined",
          "why": "The cache key cannot see it, so inconsistent decoders would corrupt a shared entry.",
          "source": "architecture.md §10"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One system: an additive subsystem of `webtools-http`.",
        "steps": [],
        "tests": [
          "Level 2 on the fake transport: a refusal costs nothing; a `429` with `Retry-After` pauses the budget; a decode failure is never stored; non-`GET` bypasses the cache",
          "Level 3: a real end-to-end call over loopback"
        ],
        "leaves": [
          "Feeds (unit 10)"
        ],
        "risks": [
          "The generic `Endpoint<T>` factory-over-class resolution is argued from precedents and checked in this unit's plan"
        ],
        "source": "architecture.md §11 unit 9",
        "link": "architecture.md#11-decomposition",
        "statements": [
          {
            "text": "Deliver `Endpoint`, `CallSpec`, `FetchSpec`, `UpstreamClient`, `UrlFetcher` and their configs.",
            "ref": "architecture.md#66-call-facades",
            "excerpt": {
              "ref": "architecture.md#66-call-facades",
              "heading": "6.6 Call facades",
              "paragraph": "Responsibility: compose cache, budget and transport in the one fixed order — cache, then budget inside the cache loader, then transport — so a hit costs nothing and N coalesced callers cost one permit; and own what they built. · Owns (`common/`): · `Endpoint<T>` (factory `Endpoint(id, decode, configure)` + builder: `name`, `ttl`, `missTtl`, `cost`, `lane`, `misses(statuses)`): the decode function travels with the endpoint because the cache key cannot see it. · `CallSpec` (per call: path segments, query pairs, headers, `method`, body, `lane`, `maxWait`, `maxAge`) and `FetchSpec` (`cost`, …",
              "hash": "0d2a559001f7"
            }
          },
          {
            "text": "Deliver the common factories and the JVM owning factories, with the `maxBurst` dispatcher sizing and the cache → budget → transport closing order.",
            "ref": "architecture.md#66-call-facades",
            "excerpt": {
              "ref": "architecture.md#66-call-facades",
              "heading": "6.6 Call facades",
              "paragraph": "Responsibility: compose cache, budget and transport in the one fixed order — cache, then budget inside the cache loader, then transport — so a hit costs nothing and N coalesced callers cost one permit; and own what they built. · Owns (`common/`): · `Endpoint<T>` (factory `Endpoint(id, decode, configure)` + builder: `name`, `ttl`, `missTtl`, `cost`, `lane`, `misses(statuses)`): the decode function travels with the endpoint because the cache key cannot see it. · `CallSpec` (per call: path segments, query pairs, headers, `method`, body, `lane`, `maxWait`, `maxAge`) and `FetchSpec` (`cost`, …",
              "hash": "0d2a559001f7"
            }
          },
          {
            "text": "Add the README how-to.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "pending",
        "summary": ""
      }
    },
    {
      "id": "feeds",
      "group": "webtools-feeds",
      "name": "webtools-feeds",
      "system": "webtools-feeds",
      "where": "webtools-feeds/ · webtools-feeds-commoncheck/",
      "unit": {
        "n": 10,
        "slug": "feeds",
        "issue": 69,
        "level": 3,
        "blockedBy": [
          68,
          63
        ],
        "versions": "webtools-feeds 1.0.0 (new, unreleased)",
        "branch": null,
        "folder": null,
        "board": null
      },
      "gap": {
        "kind": "missing",
        "summary": "No feed parsing or conditional GET exists. Rome 2.1.0 refuses DOCTYPE by default but skips a hardening feature its SAX provider does not recognise and last released on 2023-03-12; rssreader 3.12.0 swallows malformed XML into an empty stream and ignores Atom `rel`.",
        "evidence": [
          "research/feed-parsing.md F1, F2"
        ],
        "source": "research/README.md § Feeds module",
        "link": "research/README.md#feeds-module"
      },
      "options": [
        {
          "id": "rome-port",
          "name": "A multiplatform parser port with a hardened Rome default, a prolog scan, and a stateless conditional GET over `UrlFetcher`",
          "verdict": "accepted",
          "why": "The port, the scan and a fixed-heap Level 4c suite bound Rome's exposure, and a streaming parser can replace it behind the same port.",
          "source": "architecture.md §6.10",
          "link": "architecture.md#610-webtools-feeds"
        },
        {
          "id": "stateful-fetcher",
          "name": "A stateful feed fetcher holding validators",
          "verdict": "declined",
          "why": "An unbounded map keyed by administrator-supplied URLs, lost on restart.",
          "source": "architecture.md §10",
          "link": "architecture.md#10-alternatives-considered"
        },
        {
          "id": "on-transport",
          "name": "The feed fetcher on `HttpTransport` directly",
          "verdict": "declined",
          "why": "`UrlFetcher` gives the free policy pre-check and a throttle as `RateLimited` without parsing `Retry-After` itself.",
          "source": "architecture.md §10"
        },
        {
          "id": "rssreader",
          "name": "rssreader 3.12.0 or prof18's parser as the default",
          "verdict": "declined",
          "why": "Silent on malformed XML and ignoring Atom `rel`; or needing Kotlin 2.4.10 and an unhardened SAX parser.",
          "source": "architecture.md §10"
        },
        {
          "id": "jvm-only",
          "name": "A JVM-only feeds module",
          "verdict": "declined",
          "why": "Ruled out by requirement 4.",
          "source": "architecture.md §10"
        },
        {
          "id": "contract-fixtures",
          "name": "Publishing `FeedParserContract` as a fixtures variant at 1.0.0",
          "verdict": "declined",
          "why": "Doubles the exposure to the experimental publishing API, is unprobed for a module whose tests use another module's fixtures, and no second parser exists to use it.",
          "wins": "With `FeedParser`'s graduation.",
          "source": "architecture.md §10"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "One new module built on published seams; depending on a sibling module does not make it two systems.",
        "steps": [],
        "tests": [
          "Level 2 on the fakes: conditional headers, `304` → `NotModified`",
          "Level 3 through `LoopbackTransports.untrusted`",
          "Level 4a: the prolog scan and the id and link rules",
          "Level 4c: the hardening suite with a fixed heap"
        ],
        "leaves": [
          "A published `FeedParserContract` variant (with `FeedParser`'s graduation)"
        ],
        "risks": [
          "Rome is dormant and its DOM amplification on a hostile 2 MiB document is unmeasured"
        ],
        "source": "architecture.md §11 unit 10",
        "link": "architecture.md#11-decomposition",
        "statements": [
          {
            "text": "Create `webtools-feeds` and its check module.",
            "ref": "architecture.md#610-webtools-feeds",
            "excerpt": {
              "ref": "architecture.md#610-webtools-feeds",
              "heading": "6.10 `webtools-feeds`",
              "paragraph": "Responsibility: parse hostile RSS/Atom documents behind a hardened port and fetch feeds with a stateless conditional GET over `UrlFetcher`. · Placement: `webtools-feeds/`, package `com.spartanlabs.webtools.feeds`, `webtools.kotlin-multiplatform-library`, `explicitApi()`, coordinates `webtools-feeds` and `webtools-feeds-jvm`, version `1.0.0`. `commonMain`: `api(project(\":webtools-http\"))` (the public API names `UrlFetcher`, `Url`, `HttpFailure`, the marker, and `CoroutineDispatcher` through `parseDispatcher`; `kotlinx-coroutines-core` arrives through it as `api`, §6.2). `jvmMain`: `rome` 2.1.0 …",
              "hash": "5836c304c170"
            }
          },
          {
            "text": "Deliver the port, model, limits, validators, `FeedFetch`, `FeedFailure`, `FeedFetcher` and the prolog scan in `commonMain`, and `RomeFeedParser` and `FeedFetcher.untrusted` in `jvmMain`.",
            "ref": "architecture.md#610-webtools-feeds",
            "excerpt": {
              "ref": "architecture.md#610-webtools-feeds",
              "heading": "6.10 `webtools-feeds`",
              "paragraph": "Responsibility: parse hostile RSS/Atom documents behind a hardened port and fetch feeds with a stateless conditional GET over `UrlFetcher`. · Placement: `webtools-feeds/`, package `com.spartanlabs.webtools.feeds`, `webtools.kotlin-multiplatform-library`, `explicitApi()`, coordinates `webtools-feeds` and `webtools-feeds-jvm`, version `1.0.0`. `commonMain`: `api(project(\":webtools-http\"))` (the public API names `UrlFetcher`, `Url`, `HttpFailure`, the marker, and `CoroutineDispatcher` through `parseDispatcher`; `kotlinx-coroutines-core` arrives through it as `api`, §6.2). `jvmMain`: `rome` 2.1.0 …",
              "hash": "5836c304c170"
            }
          },
          {
            "text": "Add the README's artifact row, install line and how-to.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "pending",
        "summary": ""
      }
    },
    {
      "id": "http-docs",
      "group": "Documents",
      "name": "HTTP-stack documentation",
      "system": "webtools",
      "where": "docs/webtools-http-architecture.md · README.md",
      "unit": {
        "n": 11,
        "slug": "http-docs",
        "issue": 70,
        "level": 1,
        "blockedBy": [
          69
        ],
        "versions": "none",
        "branch": null,
        "folder": null,
        "board": null
      },
      "gap": {
        "kind": "missing",
        "summary": "The README describes three artifacts and three modules, and no evergreen document describes an HTTP stack.",
        "evidence": [
          "README.md:3-4 — three artifacts",
          "README.md:463-466 — three modules"
        ],
        "source": "architecture.md §6.11",
        "link": "architecture.md#611-documentation"
      },
      "options": [
        {
          "id": "evergreen",
          "name": "An evergreen `docs/webtools-http-architecture.md` modelled on the `webtools-udp` one, and a final README pass",
          "verdict": "accepted",
          "why": "Each unit edits its own README part as it lands; the last unit makes the whole consistent with what landed. No alternative was weighed.",
          "source": "architecture.md §6.11",
          "link": "architecture.md#611-documentation"
        }
      ],
      "verdict": {
        "kind": "build",
        "defense": "Docs only: Markdown, with no plan folder and no board of its own.",
        "steps": [],
        "tests": [],
        "leaves": [],
        "risks": [],
        "source": "architecture.md §11 unit 11",
        "link": "architecture.md#11-decomposition",
        "statements": [
          {
            "text": "Write `docs/webtools-http-architecture.md`.",
            "ref": "architecture.md#611-documentation",
            "excerpt": {
              "ref": "architecture.md#611-documentation",
              "heading": "6.11 Documentation",
              "paragraph": "Responsibility: keep `README.md` and an evergreen `docs/webtools-http-architecture.md` true to what is on `master`. · README (each unit edits its own part in the same change, per the repository-README rule): `:3-4` five published artifacts (a check module is not published); the artifacts table gains `webtools-http` (runtime deps `okhttp-jvm`, `caffeine`, `kotlinx-coroutines-core`, `slf4j-api`) and `webtools-feeds` (`webtools-http` and its transitives, `rome`); `:12-13` adds that the new modules are `suspend`; the install snippet gains the two modules, the Maven note (`webtools-http-jvm`, …",
              "hash": "5a9dcdb37963"
            }
          },
          {
            "text": "Make the final README pass: the \"HTTP client stack\" and \"Feeds\" sections, the stability notes and the test-layout paragraph.",
            "ref": "architecture.md#11-decomposition",
            "excerpt": {
              "ref": "architecture.md#11-decomposition",
              "heading": "11. Decomposition",
              "paragraph": "Eleven plannable units. Each is a sub-issue of #55, merges on its own and leaves `master` building with every test green. `webtools-http` and `webtools-feeds` stay unreleased at `1.0.0` until the stack is complete, so the units that build them move no version; the three existing modules move only in `java-floor`. \"Blocked by\" names true dependencies; units 5, 7 and 8 are independent of one another, and 6 needs only 5 and 4.",
              "hash": "89ad136c2590"
            }
          }
        ]
      },
      "asBuilt": {
        "state": "pending",
        "summary": ""
      }
    }
  ],
  "roadmap": null,
  "followUps": [
    {
      "issue": 58,
      "note": "Repository practices: CI, CONTRIBUTING, CHANGELOG, tags, the foojay bump. Nothing here waits on it to merge; releasing waits on it, and its CHANGELOG records seam graduations."
    },
    {
      "issue": 50,
      "note": "`webtools-udp` multiplatform: can reuse the multiplatform plugin, the check-module pattern and the logging seam; nothing here is designed for it."
    },
    {
      "name": "connector-get-skrape-on-webtools-http",
      "note": "Move `Connector.get` and `skrape` onto the stack once `webtools-http` 1.0.0 is released."
    },
    {
      "name": "connector-download-fetch-half",
      "note": "The fetch half of `Connector.download` as a capped-bytes GET."
    },
    {
      "name": "connector-line-reader-api",
      "note": "A suspend replacement for `Connector`'s line-reader API."
    },
    {
      "name": "webviewer-url-precheck",
      "note": "A URL-level pre-check for administrator-supplied URLs in `webtools-browser`."
    },
    {
      "name": "remove-redundant-test-tags",
      "note": "Drop the 95 `@Tag` annotations the package filter makes redundant. Optional."
    },
    {
      "name": "jdk11-test-launcher",
      "note": "Run the level tasks on a JDK 11 launcher, after #58's foojay bump."
    },
    {
      "name": "floor-scan-message",
      "note": "Optional: make `JavaFloorScan`'s message for a non-numeric `org.gradle.jvm.version` name the value found, with its pinning test."
    },
    {
      "name": "release-lettered-versions",
      "note": "Publish `webtools-udp` 2.0.0a and `webtools-scraping` / `webtools-browser` 1.0.0a and move the README's install lines to them; it needs the maintainer's explicit instruction."
    }
  ],
  "documents": [
    {
      "label": "Issue #55",
      "path": "https://github.com/SpartanLaboratories/WebTools/issues/55",
      "note": "the tracking issue and its sub-issues"
    },
    {
      "label": "Architecture",
      "path": "architecture.md",
      "note": "requirements, current state, the eleven systems, interactions, alternatives (§10), decomposition (§11), risks, known gaps"
    },
    {
      "label": "Consequences",
      "path": "consequences.md",
      "note": "published artifacts, blast radius, adoption, follow-ups, the open points each unit inherits (§7)"
    },
    {
      "label": "Research",
      "path": "research/README.md",
      "note": "ten questions, verification and summary, with the findings files beside it"
    },
    {
      "label": "#61 board",
      "path": "61-build-logic/dashboard.html",
      "note": "unit 1, build-logic: gap, options, verdict and as built"
    },
    {
      "label": "#47 board",
      "path": "47-java-23-bytecode/dashboard.html",
      "note": "unit 2, the Java floor: gap, options, verdict and as built"
    },
    {
      "label": "#47 impact",
      "path": "47-java-23-bytecode/impact.md",
      "note": "unit 2's impact as built, and what #62 and the later units inherit (§4)"
    },
    {
      "label": "#47 research",
      "path": "47-java-23-bytecode/research/README.md",
      "note": "the Java 11 floor, unit 2's evidence"
    },
    {
      "label": "PR #60",
      "path": "https://github.com/SpartanLaboratories/WebTools/pull/60",
      "note": "the merged plan"
    }
  ],
  "notes": {
    "paths": "Paths under `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/` are abbreviated `common/` in the architecture; `jvmMain` likewise `jvm/`. Line references are to the baseline 806ce8e.",
    "units": "Gap, considered and verdict come from the architecture and are written once. Each unit's own board, in its folder, carries its rows; its as-built cell here fills when it lands, and its row then links its board. Unit 11 is docs only and gets no folder or board.",
    "systems": "build-logic was a planned part of the Gradle build until #61 landed; it is now part of the built Gradle build (registry slug `gradle-build`, the same colour slot), so this board's systems and its build-logic and java-floor rows name `gradle-build`.",
    "corrections": [
      {
        "what": "Unit 1's verdict said the package filter selects the same 99 test classes the tag filter did. The 99 are test files, of which 4 are `testing/support` helpers holding no test, so the figure is 95 classes (#61's plan §2, measured by its procedure B).",
        "when": "landing refresh after #61, 2026-10-22"
      },
      {
        "what": "The units' verdict defences, the function, unit 1's as-built summary and the units note named each unit's sizing; they now say what the work is (one system, a bug fix, docs only), as a board never shows it. The decisions are unchanged.",
        "when": "re-templating, 2026-10-23"
      }
    ]
  },
  "impact": {
    "basis": "plan",
    "summary": "Two new Kotlin Multiplatform artifacts, `webtools-http` and `webtools-feeds` 1.0.0, add a suspend HTTP stack and a feed parser for BotTools, with a published test-fixtures variant. The three existing artifacts change only by the Java 11 floor, and no existing source or public API changes. The tracking plan moves no version itself; each unit's release is its own.",
    "none": null,
    "release": {
      "bump": "none",
      "version": null,
      "note": "`java-floor` is the only unit that moves an existing artifact's version; the two new modules stay unreleased at 1.0.0 until the last unit lands"
    },
    "downstream": [
      {
        "who": "Consumers of `webtools-udp`, `webtools-scraping` and `webtools-browser`",
        "kind": "consumer",
        "system": null,
        "effect": "behaviour",
        "summary": "They see only the Java 11 floor: Java 11 bytecode declaring `org.gradle.jvm.version: 11`, loadable on JDK 11 to 22. POMs and public API are unchanged.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "`webtools-http` (new artifact)",
        "kind": "artifact",
        "system": "webtools-http-core",
        "effect": "additive",
        "summary": "The HTTP stack: transport, URL policies, cache, budget, facades and failure model, with `kotlinx-coroutines-core` as `api` and `okhttp-jvm`, `caffeine` and `slf4j-api` at runtime only; a test-fixtures variant carries the fakes.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "`webtools-feeds` (new artifact)",
        "kind": "artifact",
        "system": "webtools-feeds",
        "effect": "additive",
        "summary": "An RSS/Atom parser port with the Rome default and a conditional-GET fetcher, built on `webtools-http`.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "BotTools (`SpartanLaboratories/KotlinBottools`)",
        "kind": "repository",
        "system": null,
        "effect": "additive",
        "summary": "The first consumer, through an internal adapter. Its release conditions (Java 21 or lower bytecode, Kotlin 2.2.x-readable metadata, no `-SNAPSHOT` dependency) hold for both new artifacts; its adapter building against the Experimental seams lets them graduate.",
        "link": "consequences.md#4-downstream-consumers"
      },
      {
        "who": "Repository builds",
        "kind": "operator",
        "system": "gradle-build",
        "effect": "behaviour",
        "summary": "`./gradlew build` adds `verifyJavaFloor` on every published module, `apiCheck` on the new modules and the check-only JS compiles, which compile only: their `js` target has no environment, so no test task and no Node.js. It also runs the fixtures-publication check, which unit 4 attached to `webtools-http`'s `check`: 44 to 52 s when one of its inputs changed, `UP-TO-DATE` otherwise; a build that includes WebTools never runs it. `build-logic`'s own level tests are not part of it: `:build-logic:test` is its pre-commit gate, and its TestKit tasks run on demand, one after another.",
        "link": "consequences.md#build"
      }
    ],
    "adaptation": [
      {
        "who": "Consumers on JDK 11 to 22",
        "system": null,
        "need": "required",
        "summary": "Move to the lettered versions of all three existing artifacts once they are published (`webtools-udp` 2.0.0a, `webtools-scraping` and `webtools-browser` 1.0.0a), requesting them `strictly` where the graph also asks for the plain release, which orders above them.",
        "link": "consequences.md#1-published-artifacts"
      },
      {
        "who": "Implementers of the new seams",
        "system": null,
        "need": "optional",
        "summary": "Implementing or subclassing a gated seam needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; calling and passing it do not. An `HttpTransport` implementation must also be main-safe: it moves blocking I/O off the caller's thread and documents its dispatcher.",
        "link": "architecture.md#9-extension--stability"
      },
      {
        "who": "Callers of `Connector` and `WebViewer`",
        "system": null,
        "need": "none",
        "summary": "Unchanged; moving `Connector`'s fetches onto `webtools-http` is a follow-up after its release.",
        "link": "consequences.md#3-adoption"
      }
    ],
    "api": [
      {
        "surface": "`webtools-http` and `webtools-feeds` value types, configs and supplied defaults",
        "system": "webtools-http-core",
        "change": "added",
        "tier": "stable-core",
        "summary": "Final classes built by factory and builder; no public `data class` and no defaulted parameters on Stable Core functions.",
        "link": "architecture.md#9-extension--stability"
      },
      {
        "surface": "`HttpTransport`, `ResponseCache`, `CallBudget`, `BudgetStore`, `FeedParser` and the other seams",
        "system": "webtools-http-core",
        "change": "added",
        "tier": "experimental",
        "summary": "Interfaces with supplied defaults; implementing one needs the opt-in, and each graduates to `@SupportedExtension` once BotTools' adapter has built against it.",
        "link": "architecture.md#9-extension--stability"
      },
      {
        "surface": "Test fixtures (`VirtualClock`, `FakeHttpTransport` and their peers)",
        "system": "webtools-http-test-fixtures",
        "change": "added",
        "tier": "stable-core",
        "summary": "Stable Core with source compatibility only: no ABI dump covers the fixtures jar.",
        "link": "architecture.md#9-extension--stability"
      },
      {
        "surface": "`build-logic` plugins and `WebToolsBuild`",
        "system": "gradle-build",
        "change": "added",
        "tier": "internal",
        "summary": "Internal to the repository and not published.",
        "link": "architecture.md#9-extension--stability"
      }
    ],
    "conversion": [],
    "units": [
      {
        "unit": "build-logic",
        "basis": "as-built",
        "summary": "No downstream effect: the three artifacts' POMs, module metadata and jars are identical to `834ce4a`'s, so no version moved. Inside the repository, `build-logic` holds the shared configuration the later units build on.",
        "release": {
          "bump": "none",
          "version": null
        },
        "source": "61-build-logic/impact.md",
        "link": "61-build-logic/impact.md#1-published-artifacts"
      },
      {
        "unit": "java-floor",
        "basis": "as-built",
        "summary": "Built as planned, with no deviation: every module compiles to Java 11 bytecode against the Java 11 API and declares `org.gradle.jvm.version: 11`, and every class loads on a real JDK 11, so consumers on JDK 11 to 22 can use the lettered versions once they are published. No source or public API change (`javap -protected` identical). Later multiplatform units inherit the floor, its attribute and `verifyJavaFloor`.",
        "release": {
          "bump": "fix",
          "version": "webtools-scraping, webtools-browser 1.0.0a; webtools-udp 2.0.0a",
          "note": "set in the build, not published"
        },
        "source": "47-java-23-bytecode/impact.md",
        "link": "47-java-23-bytecode/impact.md#1-published-artifacts",
        "compat": "notable"
      },
      {
        "unit": "http-core",
        "basis": "as-built",
        "summary": "Built as planned, with two additions: a new module, `webtools-http` 1.0.0, unreleased and not a release candidate at this landing, holding the marker, the failure model, the request and response types and the transport and wall-clock ports with `WallClock.System`; no port has another implementation yet. Its JVM artifact puts `kotlinx-coroutines-core` and `kotlin-stdlib` in the API and `okhttp-jvm`, `caffeine` and `slf4j-api` at runtime only, and `okhttp-jvm` lifts the standard library to 2.2.21 or newer. The three existing artifacts do not change, and the check-only `js` compile downloads nothing and adds no root task.",
        "release": {
          "bump": "none",
          "version": null,
          "note": "`webtools-http` 1.0.0 is new and unreleased, published once #55's stack is complete; no existing version moves",
          "unreleased": true
        },
        "source": "62-http-core/impact.md",
        "link": "62-http-core/impact.md#1-published-artifacts",
        "changes": [
          {
            "what": "`HttpTransport`'s contract gained an eighth point: `execute` is main-safe, so the OkHttp transport and the fixtures' fake state where their work runs.",
            "why": "Added in qa pass 1 (#62 deviation 2): a `suspend` function that blocks its caller's thread breaks the convention every coroutine caller relies on. KDoc only, no signature change.",
            "when": "#62 qa pass 1",
            "ref": "62-http-core/final-implementation.md#2-deviations-from-the-plan"
          },
          {
            "what": "`addSensitive` promises only the mark; the cross-origin strip is the redirect policy's, which units 5 and 6 build.",
            "why": "qa pass 1 narrowed a KDoc promise the unit does not keep (#62 `impact.md` §4).",
            "when": "#62 qa pass 1",
            "ref": "62-http-core/impact.md#4-sibling-units"
          }
        ]
      },
      {
        "unit": "test-support",
        "basis": "as-built",
        "summary": "The root module of `webtools-http`, new and unreleased, gains a test-fixtures variant with the capability `webtools-http-test-fixtures` and a `-test-fixtures.jar`, whose test libraries reach a consumer's test classpath only; the main variants, the ABI dump and the three existing artifacts do not change. Inside the repository, `build`, `check` and `:webtools-http:check` also run the fixtures-publication check.",
        "release": {
          "bump": "none",
          "version": null,
          "note": "`webtools-http` 1.0.0 stays new and unreleased until #55's stack is complete; no existing version moves",
          "unreleased": true
        },
        "source": "63-test-support/impact.md, reconciled with 63-test-support/final-implementation.md",
        "link": "63-test-support/impact.md#1-published-artifacts"
      },
      {
        "unit": "url-policy",
        "basis": "as-built",
        "summary": "`webtools-http`, new and unreleased, gains the outbound-URL policies `Trusted` and `Untrusted` with their builders and factories, `TransportSettings` with two named default sets, `CidrBlock`, `MixedAnswers` and `WebToolsDsl`, the DSL marker on every builder of the module, all Stable Core, with an `internal` classifier and `internal` JVM guards. Nothing is enforced until the transport (#65) and the call facades (#68) land; no dependency is added and the three existing artifacts do not change.",
        "release": {
          "bump": "none",
          "version": null,
          "note": "`webtools-http` 1.0.0 stays new and unreleased until #55's stack is complete; no existing version moves",
          "unreleased": true
        },
        "source": "64-url-policy/impact.md, reconciled with 64-url-policy/final-implementation.md",
        "link": "64-url-policy/impact.md#1-published-artifacts",
        "changes": [
          {
            "what": "`WebToolsDsl`, the module's DSL marker, joins the public surface, Stable Core, on the six builders, the three existing ones included.",
            "why": "Plan Amendment 3 (stage S8) settles the marker the plan had left open; the reconciled `impact.md` §1 records it.",
            "when": "#64 S8",
            "ref": "64-url-policy/impact.md#1-published-artifacts"
          }
        ]
      },
      {
        "unit": "okhttp-transport",
        "basis": "plan",
        "summary": "`webtools-http`, new and unreleased, gains `OkHttpTransport` and its factory, Stable Core: the first thing that applies the outbound-URL policy to a network fetch, with one request per hop, redirects followed by the transport, a capped decoded body and twelve refused request headers. The fixtures gain `LoopbackTransports`, `webtoolsUrl`, `toMockResponse` and `enqueue(fixture)`, and the fake's cap follows the transport's no-body rule. No dependency of either variant changes and the three existing artifacts do not.",
        "release": {
          "bump": "none",
          "version": null,
          "note": "`webtools-http` 1.0.0 stays new and unreleased until #55's stack is complete; no existing version moves",
          "unreleased": true
        },
        "source": "65-okhttp-transport/consequences.md; 65-okhttp-transport/plan.md",
        "link": "65-okhttp-transport/consequences.md#1-published-artifacts"
      }
    ],
    "changes": [
      {
        "what": "`build-logic`'s own level tests stay outside `./gradlew build`, and the fixtures-publication check reaches `build` only once unit 4 attaches it to a `check` that `build` reaches.",
        "why": "#61 built `build-logic` with its own level tests, none of them in `check`, and cut the root build to `group` and `version`, with no `check` task. The architecture wires the publication check into the root `check`, which has no target until unit 4's plan chooses one (#55 `impact.md` §2, §7, §8).",
        "when": "#61 landing",
        "ref": "1e27071",
        "entry": "downstream.4",
        "system": "gradle-build",
        "unit": "build-logic"
      },
      {
        "what": "The `strictly` request covers `webtools-udp` too: its `2.0.0a` orders below `2.0.0`, the Java 23 build on Central, as `1.0.0a` orders below `1.0.0` for the other two artifacts.",
        "why": "The architecture's §12 calls `webtools-udp` unaffected by the lettered-version ordering. #47 found the rule holds for all three artifacts (its `final-implementation.md` §9), and #55's reconciled `impact.md` §1 and §8 state it for all three; `architecture.md` is left as written.",
        "when": "#47 landing",
        "ref": "3949b52",
        "entry": "adaptation.0",
        "unit": "java-floor"
      },
      {
        "what": "The check-only `js` compiles take `js()` with no environment: they compile only, have no test task and download no Node.js or Yarn, and the root project gains no task.",
        "why": "The architecture's `js { nodejs { testTask { enabled = false } } }` made `build` download Node.js and Yarn, write `kotlin-js-store/` and give the root project base-plugin tasks (#62 research, common-check-plugin F2, F5); #55's reconciled `impact.md` §2 and `architecture.md` §6.8 carry it as a dated correction.",
        "when": "#62 landing",
        "ref": "7de8207",
        "entry": "downstream.4",
        "system": "gradle-build",
        "unit": "http-core"
      },
      {
        "what": "An `HttpTransport` implementation must be main-safe: the port's contract gained an eighth point.",
        "why": "Added in #62's qa pass 1 (deviation 2) and carried into #55's reconciled `impact.md` §8; KDoc only, no signature change.",
        "when": "#62 landing",
        "ref": "7de8207",
        "entry": "adaptation.1",
        "system": "webtools-http-core",
        "unit": "http-core"
      },
      {
        "what": "`./gradlew build`, `check` and `:webtools-http:check` run the fixtures-publication check: unit 4 attached it to `webtools-http`'s own `check`, only when WebTools is the root build, in place of the root `check` architecture §6.1 names.",
        "why": "The root project has no `check`, and a root lifecycle task is reached by neither `build` nor `:webtools-http:check` (#63 research, publication-check-wiring F6). #55's reconciled `impact.md` §2 and §8 state it, and `architecture.md` §6.1 carries a dated correction.",
        "when": "#63 landing",
        "ref": "4fcc6e0",
        "entry": "downstream.4",
        "system": "gradle-build",
        "unit": "test-support"
      },
      {
        "what": "The signing of the multiplatform publication, its adhoc test-fixtures variant included, is exercised on every run of the check with a throwaway key; only Maven Central's upload and validator are left for `webtools-http`'s first release, where architecture §12 placed both.",
        "why": "The check publishes a signed copy and asserts a signature beside every published file (#63 research, publication-check-mechanics F3, F4); #55's reconciled `impact.md` §8 states it, and `architecture.md` §12 carries a dated correction.",
        "when": "#63 landing",
        "ref": "4fcc6e0",
        "system": "webtools-http-test-fixtures",
        "unit": "test-support"
      },
      {
        "what": "`WebToolsDsl`, a public DSL marker, joins `webtools-http`'s Stable Core on every builder the module passes to a consumer's lambda: a nested block reaches an outer builder only through its label.",
        "why": "#64's plan Amendment 3 (stage S8) keeps one DSL across the stack; #55's reconciled `impact.md` §8 states it. `webtools-http` is unreleased, so no published consumer is affected.",
        "when": "#64 landing",
        "ref": "53965c8",
        "entry": "api.0",
        "system": "webtools-http-url-policy",
        "unit": "url-policy"
      }
    ],
    "source": "consequences.md",
    "link": "consequences.md#1-published-artifacts"
  },
  "links": {
    "workBoard": "../index.html",
    "repoBoard": "../../dashboards/index.html",
    "boards": {
      "gradle-build": "../../dashboards/gradle-build/index.html",
      "webtools-http-core": "../../dashboards/webtools-http/index.html",
      "webtools-http-test-fixtures": "../../dashboards/webtools-http/index.html",
      "webtools-http-url-policy": "../../dashboards/webtools-http/index.html",
      "webtools-http-transport": "../../dashboards/webtools-http/index.html",
      "webtools": "../../dashboards/index.html"
    }
  }
}
);
