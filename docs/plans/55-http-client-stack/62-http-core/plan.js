/* plan.js: plan.json as a script, written by plan/scripts/embed.js beside the board for its live view (plan/RULES.md §1). Do not edit: edit plan.json and re-embed. */
window.dashboardLive&&window.dashboardLive("plan",
{
  "schema": "plan-board/1",
  "repo": "SpartanLaboratories/WebTools",
  "issue": 62,
  "slug": "62-http-core",
  "folder": "docs/plans/55-http-client-stack/62-http-core",
  "kind": "issue",
  "level": 3,
  "title": "webtools-http common core",
  "issueTitle": "webtools-http: module skeleton, opt-in marker, failure model, request/response types, transport and wall-clock ports, logging seam and the check-only `js` compile",
  "function": "WebTools has no HTTP stack of its own: `Connector`, a blocking line reader over `URLConnection`, is its only HTTP client, and nothing offers a suspend request, a typed failure model or a transport port (#55 architecture §2). This run creates the `webtools-http` module and its check-only `js` sibling `webtools-http-commoncheck`, and delivers the vocabulary every later unit of #55 speaks: the opt-in marker, the failure model with its two reason vocabularies, the request and response types, the transport and wall-clock ports and the logging seam. Unit 3 of #55: one new module's additive API inside one system; every default implementation and policy is left to the units after it.",
  "status": "built",
  "parent": {
    "issue": 55,
    "board": "../dashboard.html"
  },
  "branch": "feat/issue-62-http-core",
  "baseline": {
    "ref": "master",
    "sha": "f72b115",
    "date": "2026-10-23"
  },
  "asBuilt": {
    "ref": "master",
    "sha": "4d9c9f9",
    "date": "2026-10-25",
    "pr": 82,
    "merge": "7de8207",
    "version": "none moved; webtools-http 1.0.0 new, unreleased"
  },
  "systems": [
    {
      "slug": "webtools-http-core",
      "name": "webtools-http common core",
      "slot": 5,
      "top": "webtools-http",
      "status": "built",
      "page": "docs/dashboards/webtools-http/index.html"
    },
    {
      "slug": "js-commoncheck",
      "name": "check-only js modules",
      "slot": 4,
      "top": "Gradle build",
      "status": "built",
      "page": null
    },
    {
      "slug": "gradle-build",
      "name": "Gradle build",
      "slot": 4,
      "top": "Gradle build",
      "status": "built",
      "page": "docs/dashboards/gradle-build/index.html"
    }
  ],
  "wip": {
    "step": "landed",
    "since": "2026-10-25T11:51:40Z",
    "updated": "2026-10-25T12:08:09Z",
    "detail": "Merged through PR #82 as 7de8207: `webtools-http` and its check-only `js` module, the `webtools.kotlin-common-check` plugin and the README; three deviations, all additions; no version moved, and `webtools-http` 1.0.0 stays unreleased.",
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
      "landed"
    ],
    "current": [],
    "questions": [],
    "next": null,
    "qa": [
      {
        "pass": 1,
        "verdict": "changes-requested",
        "date": "2026-10-25",
        "findings": {
          "documentation": 18,
          "test": 9,
          "code": 2
        },
        "severity": {
          "minor": 27,
          "other": 2
        },
        "route": "on-the-spot",
        "note": "Code review passed: no logic bug, every plan item done, the diff inside the unit. Tests 231 passed, 0 failed, 0 skipped; apiCheck passed. The findings are KDoc and line-comment wording, test-fixture tags, literal homoglyphs, corpus rows, test names and one missing branch test; fixed on the spot."
      }
    ],
    "qaMax": 3,
    "waitingOn": null,
    "resume": null,
    "blockers": {
      "waitsOn": [],
      "blocks": [
        {
          "issue": 63
        },
        {
          "issue": 64
        },
        {
          "issue": 66
        },
        {
          "issue": 67
        }
      ]
    },
    "landing": {
      "pr": 82,
      "merge": "7de8207",
      "version": "none moved; webtools-http 1.0.0 new, unreleased",
      "date": "2026-10-25"
    },
    "event": {
      "name": "landed",
      "at": "2026-10-25T12:08:09Z",
      "note": "the landing refresh: every row resolved, the impact reconciled as built"
    },
    "open": [
      {
        "text": "The S2 report raised one inconsistency in `plan.md`, for a plan ruling: its media-type test rows do not agree with the plan's own media-type grammar. It bears on the Level 4a tables of the Tests row (`common-tests`); it is not a deviation, and it does not block S3.",
        "raised": "implementation-stage S2",
        "ref": "plan.md",
        "resolved": "Resolved by `plan.md`'s Amendment 1, which makes §9's media-type test row agree with the unchanged grammar of §6.4 and code of §7.5, applies in S4 and changes no verdict on this board."
      },
      {
        "text": "Three items of the Level 5 pass need an IntelliJ session and were not performed; they are left to the maintainer: the IDE import of the check module and its `jsMain` actuals, the gate error and `@OptIn` quick fix on a scratch `WallClock` implementer, and Quick Documentation of `Url.parse`, `Url.toString`, `HttpTransport`, `HttpFailure` and the marker. The command-line items (the dump read, the tree hygiene) were done.",
        "raised": "implementation-stage S7",
        "ref": "final-implementation.md#6-level-5--the-manual-pass",
        "resolved": "Left to the maintainer as a known gap: the owner and the trigger (the next IntelliJ import, at the latest before unit 4, #63, starts) are recorded in `impact.md` §7 and `final-implementation.md` §6 and §8."
      },
      {
        "text": "Two plan-sanctioned test-scope exclusions flagged in qa pass 1: no per-type testing.gating smoke classes for webtools-http's value types (the plan makes componentTest+deterministicTest the pre-commit gate) and the common-check plugin absent from the included-build e2e composite; resolved by recording them in plan §9 and the test KDoc",
        "raised": "qa-pass 1",
        "ref": "plan.md",
        "resolved": "Recorded in `plan.md` as Amendment 2 (§9), and in `IncludedBuildEndToEndTest`'s KDoc."
      }
    ],
    "stages": [
      {
        "id": "S1",
        "name": "common-check plugin and TestKit tests",
        "state": "done",
        "rows": [
          "common-check-plugin"
        ]
      },
      {
        "id": "S2",
        "name": "module skeleton, gated declarations, compile proof",
        "state": "done",
        "rows": [
          "module-skeleton",
          "opt-in-marker",
          "failure-model",
          "request-response",
          "transport-port",
          "wall-clock",
          "logging-seam",
          "commoncheck-module",
          "compile-proof",
          "release"
        ]
      },
      {
        "id": "S3",
        "name": "Url, encoders, corpus, generator, commonTest",
        "state": "done",
        "rows": [
          "request-response",
          "common-tests"
        ]
      },
      {
        "id": "S4",
        "name": "remaining level 2/4a/4c tests, jvmTest differentials, logback-test.xml",
        "state": "done",
        "rows": [
          "logging-seam",
          "common-tests"
        ]
      },
      {
        "id": "S5",
        "name": "ABI dump and checks",
        "state": "done",
        "rows": [
          "module-skeleton",
          "commoncheck-module",
          "compile-proof"
        ]
      },
      {
        "id": "S6",
        "name": "README",
        "state": "done",
        "rows": [
          "readme"
        ]
      },
      {
        "id": "S7",
        "name": "level-5 pass and as-built record",
        "state": "done",
        "rows": []
      }
    ],
    "remediation": null
  },
  "milestones": [
    {
      "column": "gap",
      "label": "Research verified, in #55's run",
      "date": "2026-10-21",
      "ref": "../research/README.md",
      "note": "#55's ten questions and findings; 231 outside-world claims re-opened at source, 227 hold, none fails. Written on this board at sized from the research README, architecture §2 and this unit's row in §11"
    },
    {
      "column": "gap",
      "label": "Research verified, in this unit's run",
      "date": "2026-10-25",
      "ref": "research/README.md",
      "note": "four questions and four findings files; 107 load-bearing claims re-opened at source, all hold. The Gap column, written at sized from #55's research and architecture, stands as written"
    },
    {
      "column": "considered",
      "label": "Architecture signed off, in #55's run",
      "date": "2026-10-21",
      "ref": "../architecture.md",
      "note": "the options #55's architecture weighed for this unit: §6.2, §6.8, §6.9, §9 and §10; this unit's own plan adds to them at plan acceptance"
    },
    {
      "column": "considered",
      "label": "The unit's own plan adds to the options",
      "date": "2026-10-25",
      "ref": "plan.md",
      "note": "the options plan.md weighs beside the architecture's: the `js` target's form, where the opt-in is set, a permanent gate check, `Url` building and host names, transport-owned headers"
    },
    {
      "column": "verdict",
      "label": "Plan accepted",
      "date": "2026-10-25",
      "ref": "plan.md",
      "note": "seven stages, S1 to S7, on `feat/issue-62-http-core`; the compile proof opens S2 and gates S3. One departure from #55's architecture: the check plugin calls `js()` with no environment"
    },
    {
      "column": "built",
      "label": "Merged",
      "date": "2026-10-25",
      "ref": "PR #82",
      "note": "five commits: the plugin e007cdd, the module 123c875, the README b5257d3, the as-built record 40cb538 and the boards 4d9c9f9; merged as 7de8207"
    }
  ],
  "trail": [
    {
      "date": "2026-10-22",
      "column": "gap",
      "label": "Issue #62 opened as unit 3 of #55, blocked by #61 and #47",
      "ref": "#62"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Run started: sized as one system, one new module's additive API; planning begins from #55's architecture",
      "ref": "../architecture.md"
    },
    {
      "date": "2026-10-25",
      "column": "considered",
      "label": "Gap and Considered filled from #55's research and architecture: 14 rows, the options with the verdicts the architecture gave",
      "ref": "../architecture.md"
    },
    {
      "date": "2026-10-25",
      "column": "verdict",
      "label": "Halted at the interview: two questions on `Url` parsing and `toString()` wait on the user; the architecture slice re-validated against f72b115 with no drift",
      "ref": "../architecture.md"
    },
    {
      "date": "2026-10-25",
      "column": "verdict",
      "label": "Resumed into research with the two `Url` questions answered. Q1: `Url.parse` normalises as OkHttp's `HttpUrl` does for ASCII hosts (percent-encoding, dot segments resolved, default port dropped, an empty path made `/`, trimmed, fragment dropped) and rejects non-ASCII or percent-encoded hosts, backslashes, inner whitespace or control characters, and userinfo. Q2: `Url.toString()` returns the full canonical URL; `redacted()` is the explicit safe form"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research returned: header-and-method-validation, header, method, status and content-type validity",
      "ref": "research/header-and-method-validation.md"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research returned: opt-in-gate, opting the module's main code into its own subclass gate while the tests stay a consumer",
      "ref": "research/opt-in-gate.md"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research returned: url-parsing, `Url` parsing and canonicalising so every accepted URL reads the same in OkHttp 5.5.0's `HttpUrl`",
      "ref": "research/url-parsing.md"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research returned: common-check-plugin, what a check-only `js` module needs and what it does to the root build",
      "ref": "research/common-check-plugin.md"
    },
    {
      "date": "2026-10-25",
      "column": "gap",
      "label": "Research verified: the load-bearing claims of the four findings files were re-opened at their sources; 107 claims, 107 hold, none fails, none is unclear",
      "ref": "research/README.md#verification"
    },
    {
      "date": "2026-10-25",
      "column": "considered",
      "label": "Research ended: the four questions returned and were verified (the opt-in gate across `commonMain`, `jvmMain` and `js`, the common-check plugin's effect on the root build, the `Url` grammar against OkHttp 5.5.0, RFC 9110 header and method validation); the plan is written next",
      "ref": "research/README.md#summary"
    },
    {
      "date": "2026-10-25",
      "column": "verdict",
      "label": "Plan accepted: seven stages, from the `webtools.kotlin-common-check` plugin to the as-built record; the check plugin calls `js()` with no environment in place of the architecture's `js { nodejs { } }`, so the root project gains no task; `webtools-http` 1.0.0 is new and not a release candidate",
      "ref": "plan.md#5-approach-and-staging"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "Implementation starts on `feat/issue-62-http-core` at `865e6e4`, the branch that holds the research and the plan: the implementer builds `plan.md` stage by stage, from S1, the `webtools.kotlin-common-check` plugin and its TestKit tests",
      "ref": "plan.md#5-approach-and-staging"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "S1 done: the `webtools.kotlin-common-check` plugin, the `SampleProject.commonCheck` factory and the plugin's TestKit tests, as planned; every `build-logic` suite green (procedure P2). S2 is next",
      "ref": "final-implementation.md#p2--the-plugin-s1"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "S2 done: the settings, both module build files, every gated declaration and the `js` actuals, with a `Url` shell, as planned; the compile proof (P3) gave every predicted result in `jvm` and `js`, and `gatingTest` 5 green. S3 is next",
      "ref": "final-implementation.md#3-the-compile-proof"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "S3 done: the `Url` parser, with `UrlParsing`, `UrlHost`, `UrlEncoding` and `UrlProblem`, replaces S2's shell, and the `Url` tests run over `UrlCorpus` and `UrlInputGenerator`, as planned; `gatingTest`, `deterministicTest` and `nonfunctionalTest` green (procedure P5) and the check module's `js` compile green. Two mechanical fixes, neither a change to a rule. S4 is next",
      "ref": "final-implementation.md#p5--the-commontest-levels-after-s3"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "Open point resolved: `plan.md` was amended during implementation. Its Amendment 1 makes §9's media-type test row agree with the plan's own media-type grammar (§6.4 and §7.5, unchanged): `text/plain;;` and `text/plain ;` accepted, `text/plain; ` refused at index 11; it applies in S4. No verdict on this board changes: the Tests row's and the request-and-response row's verdicts name the media-type table without its rows",
      "ref": "plan.md"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "S4 done: the remaining Level 2, 4a and 4c tests and the three `jvmTest` differentials against OkHttp 5.5.0, as planned; `componentTest` 68, `deterministicTest` 48 and `nonfunctionalTest` 9 green, the `Url` differential 0 disagreements over 200,000 inputs and 2,000,000 on two more seeds, each of four mutants caught; `logback-test.xml` copied from `webtools-udp`. Three mechanical test-code fixes, none a change to a rule. S5 is next",
      "ref": "final-implementation.md#p5--the-fast-loop-after-s4"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "S5 done: the ABI dump and its checks, as planned; `apiDump` 294 lines matching plan §7.8, `apiCheck` and `verifyJavaFloor` green, `SubclassOptInRequired` on the five gated types only, class major version 55, coroutines `api` and OkHttp, Caffeine and slf4j runtime-only in the POM and module metadata; both modules' `check` green (130 `jvmTest` tests, 0 failures); configuration cache reused, no `kotlin-js-store/`, no Node.js task, no root `check`, a `java.io` leak in `commonMain` fails the `js` compile; the three existing modules untouched and compiling. Two mechanical test-code fixes, none a change to a rule. S6 is next",
      "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "S6 done: the README, as planned; the nine edits of plan §8 (the `webtools-http` artifact row and install line marked not yet released, the `-jvm` coordinate and `kotlin-stdlib` 2.2.21 note, the module list and `commoncheck` build line, the test-layout paragraph, the four-plugin table), 29 insertions and 14 deletions, no other line. The `verifyJavaFloor` comment tightening rides S7. S7, the Level 5 pass and the as-built record, is next",
      "ref": "final-implementation.md#s6--the-readme"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "S7 done, implementation complete: the Level 5 pass from the command line (the dump read against plan §7.8 and the tree checked clean; the three IDE items not performed, left to the maintainer) and the as-built record: no deviation from `plan.md`, all eight acceptance criteria met, no research claim that failed to hold. qa pass 1 runs on the change set",
      "ref": "final-implementation.md#4-acceptance"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "qa pass 1: changes requested, 29 findings (18 documentation, 9 test, 2 code; 27 minor, 2 other); code review passed, 231 tests green and apiCheck passed. Remediation on the spot: the fixes, then two scope exclusions recorded in plan.md §9; landing follows with no new qa pass",
      "ref": ""
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "qa pass 1 fixes done on the spot: KDoc and line-comment wording, test-fixture tags, U+212A escapes, corpus rows, behaviour-named tests and one new HttpHeaders test; re-run green (131 jvmTest tests, apiCheck, dump unchanged; CommonCheckPluginIntegrationTest 7, e2eTest 5). No behaviour, API or design change. The two scope exclusions are recorded in plan.md §9 and the as-built record reconciled",
      "ref": "final-implementation.md#qa-pass-1-fixes"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "Open points settled: the two test-scope exclusions are plan Amendment 2 (§9); the three IDE items of the Level 5 pass are left to the maintainer with an owner and a trigger",
      "ref": "final-implementation.md#6-level-5--the-manual-pass"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "The as-built record and the impact reconciled: three deviations, all additions (a README sentence, `HttpTransport`'s eighth contract point, one KDoc sentence); #55's impact and architecture carry dated callouts",
      "ref": "40cb538"
    },
    {
      "date": "2026-10-25",
      "column": "built",
      "label": "Landed: PR #82 merged as 7de8207; no version moved, `webtools-http` 1.0.0 new and unreleased; the branch deleted",
      "ref": "PR #82"
    }
  ],
  "rows": [
    {
      "id": "module-skeleton",
      "group": "Code",
      "name": "The webtools-http module and its build",
      "system": "webtools-http-core",
      "where": "webtools-http/build.gradle.kts · settings.gradle.kts · webtools-http/api/",
      "gap": {
        "kind": "missing",
        "summary": "No `webtools-http` module exists. The build includes the three existing modules only, and the multiplatform convention that #61 shipped is applied by no module yet, so nothing in the repository offers a suspend request, a typed failure model or a transport port.",
        "evidence": [
          "settings.gradle.kts:11 — `include(\":webtools-udp\", \":webtools-scraping\", \":webtools-browser\")` (baseline)",
          "build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts:7 — the convention for a published multiplatform library with a single `jvm()` target; no module applies it (baseline)"
        ],
        "source": "../architecture.md §2, §11 unit 3",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "kmp-module",
          "name": "A Kotlin Multiplatform module on `webtools.kotlin-multiplatform-library`, `jvm()` only, with `explicitApi()`, the ABI dump and `kotlinx-coroutines-core` 1.10.2 as its one `api` dependency",
          "verdict": "accepted",
          "why": "OkHttp, Caffeine and slf4j stay `implementation` in `jvmMain`, so no implementation library reaches a consumer's compile classpath. Coroutines is `api` because it is the runtime of a suspend API and `CoroutineDispatcher` appears in configuration, which lets one virtual clock drive the cache, the budget and `delay`.",
          "source": "../architecture.md §6.2",
          "link": "../architecture.md#62-webtools-http-common-core"
        },
        {
          "id": "internal-subpackage",
          "name": "An `.internal` subpackage beside the public one",
          "verdict": "declined",
          "why": "`internal` already does the hiding, and one flat package `com.spartanlabs.webtools.http` never needs a type to move.",
          "source": "../architecture.md §6.2",
          "link": "../architecture.md#62-webtools-http-common-core"
        },
        {
          "id": "scheduling-type",
          "name": "An opaque library-owned `Scheduling` value in place of `CoroutineDispatcher` in configuration, keeping coroutines `implementation`",
          "verdict": "declined",
          "why": "A consumer could no longer pass its own dispatcher, such as a `limitedParallelism` view that bounds parse threads; every component would gain a bespoke type in place of the idiom, and it would keep off the compile classpath nothing a suspend API's consumer does not already have.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "kgp-abi-validation",
          "name": "KGP's built-in `abiValidation` instead of binary-compatibility-validator for the ABI dump",
          "verdict": "declined",
          "why": "It is experimental on Kotlin 2.2.0, and its DSL changed by 2.4.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "coroutines-test",
          "name": "Adding `kotlinx-coroutines-test` to the module's tests",
          "verdict": "declined",
          "why": "No test of this unit needs it: `runBlocking` in `jvmTest` covers the non-fatal helper. Unit 4 adds it on its fixtures variant.",
          "source": "plan.md R1, §12",
          "link": "plan.md#r1-the-module"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "settings.gradle.kts",
          "webtools-http/build.gradle.kts",
          "webtools-http/api/webtools-http.api"
        ],
        "tests": "`:webtools-http:check` green: `jvmTest` 130 tests (gating 5, component 68, deterministic 48, non-functional 9), 0 failures, with `apiCheck` and `verifyJavaFloor` (acceptance 1); the generated POM and module metadata read as planned (acceptance 5).",
        "verified": "Procedures P6 to P9 after S5, recorded in `final-implementation.md` §5 on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
        "statements": [
          {
            "text": "`webtools-http/build.gradle.kts` applies the multiplatform convention with `explicitApi()`; every class file is Java 11 (major version 55).",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
            "excerpt": {
              "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
              "heading": "P6 — the dump, the class files, the published shape (S5)",
              "paragraph": "`./gradlew :webtools-http:apiDump` → `BUILD SUCCESSFUL`; `webtools-http/api/webtools-http.api` has 294 lines, written CRLF by the tool on this host (git stores it LF; `apiCheck` compares line by line). `./gradlew :webtools-http:apiCheck` → `BUILD SUCCESSFUL`. Read against plan §7.8, every point holds: `public abstract fun execute-gIAlu-s (…HttpRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`, a public `HttpTransport$DefaultImpls` with `public static fun close (…HttpTransport;)V`, `public fun close ()V`; `WallClock$System` final with `INSTANCE`, `nowEpochMillis ()J` and `toString`; …",
              "hash": "4f835e8ce361"
            }
          },
          {
            "text": "The POM and the module metadata put coroutines on the compile classpath and OkHttp, Caffeine and slf4j at runtime only.",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
            "excerpt": {
              "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
              "heading": "P6 — the dump, the class files, the published shape (S5)",
              "paragraph": "`./gradlew :webtools-http:apiDump` → `BUILD SUCCESSFUL`; `webtools-http/api/webtools-http.api` has 294 lines, written CRLF by the tool on this host (git stores it LF; `apiCheck` compares line by line). `./gradlew :webtools-http:apiCheck` → `BUILD SUCCESSFUL`. Read against plan §7.8, every point holds: `public abstract fun execute-gIAlu-s (…HttpRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`, a public `HttpTransport$DefaultImpls` with `public static fun close (…HttpTransport;)V`, `public fun close ()V`; `WallClock$System` final with `INSTANCE`, `nowEpochMillis ()J` and `toString`; …",
              "hash": "4f835e8ce361"
            }
          },
          {
            "text": "`settings.gradle.kts` includes `:webtools-http` and `:webtools-http-commoncheck`; the root project gained no plugin, extension or task.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape; the mechanical fixes made on the way are in §5.",
              "hash": "be79bfaf86d5"
            }
          },
          {
            "text": "The 294-line dump matched plan §7.8 point by point, and `apiCheck` passed.",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
            "excerpt": {
              "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
              "heading": "P6 — the dump, the class files, the published shape (S5)",
              "paragraph": "`./gradlew :webtools-http:apiDump` → `BUILD SUCCESSFUL`; `webtools-http/api/webtools-http.api` has 294 lines, written CRLF by the tool on this host (git stores it LF; `apiCheck` compares line by line). `./gradlew :webtools-http:apiCheck` → `BUILD SUCCESSFUL`. Read against plan §7.8, every point holds: `public abstract fun execute-gIAlu-s (…HttpRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`, a public `HttpTransport$DefaultImpls` with `public static fun close (…HttpTransport;)V`, `public fun close ()V`; `WallClock$System` final with `INSTANCE`, `nowEpochMillis ()J` and `toString`; …",
              "hash": "4f835e8ce361"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The convention plugin carries the target, the Java floor, the level tasks and publishing, so the build file adds only what is the module's own. Only the `jvmTest` differential uses OkHttp in this unit; the other two are declared for the units that follow.",
        "steps": [
          {
            "id": "S2",
            "what": "Write `settings.gradle.kts`, both module build files, every declaration the gate touches, the value types other than `Url`, a `Url` shell with its final members, the `js` actuals and the permanent Level 1 consumer classes, then run the compile proof.",
            "files": [
              "settings.gradle.kts",
              "webtools-http/",
              "webtools-http-commoncheck/"
            ],
            "check": "Procedure P3, recorded in `final-implementation.md` §3; S3 does not start if it differs from the plan",
            "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
          },
          {
            "id": "S5",
            "what": "Generate and commit the ABI dump, then read the class files and the published metadata, and prove the root project and the three existing modules unchanged.",
            "files": [
              "webtools-http/api/webtools-http.api"
            ],
            "check": "Procedures P6 to P9",
            "link": "plan.md#92-procedures"
          }
        ],
        "tests": [
          "`:webtools-http:check`: `jvmTest` at every level, `apiCheck` and `verifyJavaFloor` (acceptance 1)",
          "The generated POM and module metadata: coroutines on the compile classpath, the other three at runtime only (acceptance 5)"
        ],
        "risks": [
          "Kotlin 2.2.0 on Gradle 9.7.1 is outside Kotlin's documented range, and this is the first module to run the multiplatform plugin end to end; anything its tasks reveal is a deviation to record"
        ],
        "source": "plan.md R1, §7.1, §7.3, §7.8",
        "link": "plan.md#73-webtools-httpbuildgradlekts-new",
        "statements": [
          {
            "text": "`webtools-http` is a Kotlin Multiplatform module on `webtools.kotlin-multiplatform-library`, with `explicitApi()` and one flat package, `com.spartanlabs.webtools.http`.",
            "ref": "plan.md#r1-the-module",
            "label": "§R1",
            "excerpt": {
              "ref": "plan.md#r1-the-module",
              "heading": "R1. The module",
              "paragraph": "`webtools-http` is a new Kotlin Multiplatform module on `webtools.kotlin-multiplatform-library`. · Coordinates `io.github.spartanlaboratories:webtools-http`, version `1.0.0` inherited from the root default; it publishes `webtools-http` and `webtools-http-jvm`. · One flat package, `com.spartanlabs.webtools.http`. `internal` does the hiding; there is no `.internal` subpackage. · `explicitApi()`. · Its build file states, in a comment, that `1.0.0` is unreleased and is not a release candidate until the whole stack (#55) is complete. · Dependencies: · …",
              "hash": "36cfb5dbdd6b"
            }
          },
          {
            "text": "`kotlinx-coroutines-core` 1.10.2 is its one `api` dependency; `okhttp-jvm`, `caffeine` and `slf4j-api` are `implementation` in `jvmMain`.",
            "ref": "plan.md#r1-the-module",
            "label": "§R1",
            "excerpt": {
              "ref": "plan.md#r1-the-module",
              "heading": "R1. The module",
              "paragraph": "`webtools-http` is a new Kotlin Multiplatform module on `webtools.kotlin-multiplatform-library`. · Coordinates `io.github.spartanlaboratories:webtools-http`, version `1.0.0` inherited from the root default; it publishes `webtools-http` and `webtools-http-jvm`. · One flat package, `com.spartanlabs.webtools.http`. `internal` does the hiding; there is no `.internal` subpackage. · `explicitApi()`. · Its build file states, in a comment, that `1.0.0` is unreleased and is not a release candidate until the whole stack (#55) is complete. · Dependencies: · …",
              "hash": "36cfb5dbdd6b"
            }
          },
          {
            "text": "`settings.gradle.kts` includes the module, and the root build gains no plugin or task.",
            "ref": "plan.md#71-settingsgradlekts-and-buildgradlekts-changed",
            "excerpt": {
              "ref": "plan.md#71-settingsgradlekts-and-buildgradlekts-changed",
              "heading": "7.1 `settings.gradle.kts` and `build.gradle.kts` (changed)",
              "paragraph": "`settings.gradle.kts:11` becomes:",
              "hash": "9e5e42ae6b1c"
            }
          },
          {
            "text": "Its ABI dump is generated by `apiDump` and committed.",
            "ref": "plan.md#78-webtools-httpapiwebtools-httpapi-generated-committed",
            "excerpt": {
              "ref": "plan.md#78-webtools-httpapiwebtools-httpapi-generated-committed",
              "heading": "7.8 `webtools-http/api/webtools-http.api` (generated, committed)",
              "paragraph": "Generated by `./gradlew :webtools-http:apiDump` after S5's last source change, and committed. It lists every public declaration of the JVM ABI. The research recorded what to expect, and the review reads the file for exactly these:",
              "hash": "a3d5a07b8d41"
            }
          }
        ]
      }
    },
    {
      "id": "opt-in-marker",
      "group": "Code",
      "name": "The opt-in marker ExperimentalForInheritanceWebToolsApi",
      "system": "webtools-http-core",
      "where": "common/",
      "gap": {
        "kind": "missing",
        "summary": "No opt-in gate exists for the seams a consumer implements. The stack's rule puts every such seam behind one `@RequiresOptIn` marker while the supplied defaults stay usable without opting in, and the repository has no marker of its own.",
        "evidence": [
          "../research/README.md § The work, Stability — every seam a consumer implements or substitutes sits behind one marker; the defaults need no opt-in"
        ],
        "source": "../research/README.md § The work",
        "link": "../research/README.md#the-work"
      },
      "options": [
        {
          "id": "subclass-gated-marker",
          "name": "One marker at level `ERROR` with an empty `@Target`, applied only through `@SubclassOptInRequired`",
          "verdict": "accepted",
          "why": "`@SubclassOptInRequired` is stable on Kotlin 2.2.0 and gates implementing and delegating but not mentioning a type, so constructing and calling the supplied defaults needs no opt-in. With an empty target the marker can only be the argument of `@SubclassOptInRequired` and `@OptIn`. It is never removed, even after every seam graduates to `@SupportedExtension`.",
          "source": "../architecture.md §6.2, §9",
          "link": "../architecture.md#62-webtools-http-common-core"
        },
        {
          "id": "marker-on-types",
          "name": "The marker placed directly on the seam types",
          "verdict": "declined",
          "why": "A directly marked type in a default-path signature contaminates every caller; placed only through `@SubclassOptInRequired`, a default-path signature can never force the opt-in on a caller.",
          "source": "../architecture.md §6.2",
          "link": "../architecture.md#62-webtools-http-common-core"
        },
        {
          "id": "module-wide-compiler-option",
          "name": "The opt-in as `kotlin { compilerOptions { optIn.add(...) } }`, as a target-level option, or through `sourceSets.all { }`",
          "verdict": "declined",
          "why": "Each of them also reaches `commonTest` and `jvmTest`, which would then compile as a non-consumer and could implement a port or subclass a failure group without the `@OptIn` a consumer needs.",
          "source": "plan.md R7, §6.2; research/opt-in-gate.md F2, F3, F5",
          "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
        },
        {
          "id": "opt-in-in-convention",
          "name": "The opt-in set in a convention plugin",
          "verdict": "declined",
          "why": "A module that cannot see the marker would print an unresolved-marker warning on every compile; the language setting belongs to the main source sets of each module's own build file.",
          "source": "plan.md §6.2",
          "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/",
          "webtools-http/build.gradle.kts",
          "webtools-http-commoncheck/build.gradle.kts"
        ],
        "tests": "L1 gating `GatedTypesConsumerGatingTest` 3 and `GatedTypesJvmConsumerGatingTest` 2, compiled without any opt-in: `gatingTest` 5 green.",
        "verified": "Procedure P3, recorded in `final-implementation.md` §3 on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#step-2--the-wiring-canary",
        "statements": [
          {
            "text": "The marker's message reached the module's own diagnostics: the in-module errors quoted it.",
            "state": "as-planned",
            "ref": "final-implementation.md#step-2--the-wiring-canary",
            "excerpt": {
              "ref": "final-implementation.md#step-2--the-wiring-canary",
              "heading": "Step 2 — the wiring canary",
              "paragraph": "Three throwaway top-level implementers were added, exactly as the plan writes them: `ProofClock.kt` (`commonMain`, `: WallClock`), `ProofTransport.kt` (`jvmMain`, `: HttpTransport`) and `ProofJsClock.kt` (the check module's `jsMain`, `: WallClock`).",
              "hash": "b12290b81124"
            }
          },
          {
            "text": "`javap` showed `SubclassOptInRequired` on the five gated types only, and the dump records none.",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
            "excerpt": {
              "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
              "heading": "P6 — the dump, the class files, the published shape (S5)",
              "paragraph": "`./gradlew :webtools-http:apiDump` → `BUILD SUCCESSFUL`; `webtools-http/api/webtools-http.api` has 294 lines, written CRLF by the tool on this host (git stores it LF; `apiCheck` compares line by line). `./gradlew :webtools-http:apiCheck` → `BUILD SUCCESSFUL`. Read against plan §7.8, every point holds: `public abstract fun execute-gIAlu-s (…HttpRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`, a public `HttpTransport$DefaultImpls` with `public static fun close (…HttpTransport;)V`, `public fun close ()V`; `WallClock$System` final with `INSTANCE`, `nowEpochMillis ()J` and `toString`; …",
              "hash": "4f835e8ce361"
            }
          },
          {
            "text": "Both build files opt in on their main source sets; with the four lines removed, the three throwaway implementers failed as predicted and compiled again once the lines were restored.",
            "state": "as-planned",
            "ref": "final-implementation.md#step-2--the-wiring-canary",
            "excerpt": {
              "ref": "final-implementation.md#step-2--the-wiring-canary",
              "heading": "Step 2 — the wiring canary",
              "paragraph": "Three throwaway top-level implementers were added, exactly as the plan writes them: `ProofClock.kt` (`commonMain`, `: WallClock`), `ProofTransport.kt` (`jvmMain`, `: HttpTransport`) and `ProofJsClock.kt` (the check module's `jsMain`, `: WallClock`).",
              "hash": "b12290b81124"
            }
          },
          {
            "text": "`commonTest` and `jvmTest` compile with no opt-in: the eight negative cases gave exactly eight errors in each, and none with `@OptIn`.",
            "state": "as-planned",
            "ref": "final-implementation.md#step-4--the-eight-negative-cases",
            "excerpt": {
              "ref": "final-implementation.md#step-4--the-eight-negative-cases",
              "heading": "Step 4 — the eight negative cases",
              "paragraph": "`NegativeCases.kt` (the eight declarations of plan §9.2 P3.4, `import com.spartanlabs.webtools.http.*`) was placed in `commonTest` under `com.spartanlabs.testing.gating.webtools.http`, then in `jvmTest`.",
              "hash": "ed5ba8ac8e89"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "In a single-target module only the leaf source set's setting reaches the compiler, and the other forms reach the test compilations too, which would stop the tests being a consumer. With the message as one literal, the module's own diagnostics carry it.",
        "steps": [
          {
            "id": "S2",
            "what": "Write `settings.gradle.kts`, both module build files, every declaration the gate touches, the value types other than `Url`, a `Url` shell with its final members, the `js` actuals and the permanent Level 1 consumer classes, then run the compile proof.",
            "files": [
              "settings.gradle.kts",
              "webtools-http/",
              "webtools-http-commoncheck/"
            ],
            "check": "Procedure P3, recorded in `final-implementation.md` §3; S3 does not start if it differs from the plan",
            "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
          }
        ],
        "tests": [
          "Level 1 — `GatedTypesConsumerGatingTest` and `GatedTypesJvmConsumerGatingTest`, compiled without any opt-in"
        ],
        "risks": [
          "The ABI dump does not record `@SubclassOptInRequired`, so `apiCheck` would not notice a dropped gate"
        ],
        "source": "plan.md R3, R7, §6.2",
        "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation",
        "statements": [
          {
            "text": "One marker, `ExperimentalForInheritanceWebToolsApi`: `@RequiresOptIn(level = ERROR)` with its message as one string literal, an empty `@Target` and `BINARY` retention.",
            "ref": "plan.md#r3-stability",
            "label": "§R3",
            "excerpt": {
              "ref": "plan.md#r3-stability",
              "heading": "R3. Stability",
              "paragraph": "Everything a consumer calls or configures is Stable Core. · The seams are Experimental (implement or subclass), behind one marker, `ExperimentalForInheritanceWebToolsApi`: · `@RequiresOptIn(level = ERROR)`, its message one string literal, so that the module's own diagnostics carry it; · `@Target()`, `BINARY` retention, `@MustBeDocumented`; · used only as the argument of `@SubclassOptInRequired` and `@OptIn`; never removed. · The gated types are `HttpTransport`, `WallClock`, `HttpFailure`, `TransportFailure` and `UpstreamFailure`. The supplied default `WallClock.System` is final and usable …",
              "hash": "1e272043a9f9"
            }
          },
          {
            "text": "It is used only through `@SubclassOptInRequired` and `@OptIn`, and never removed.",
            "ref": "plan.md#r3-stability",
            "label": "§R3",
            "excerpt": {
              "ref": "plan.md#r3-stability",
              "heading": "R3. Stability",
              "paragraph": "Everything a consumer calls or configures is Stable Core. · The seams are Experimental (implement or subclass), behind one marker, `ExperimentalForInheritanceWebToolsApi`: · `@RequiresOptIn(level = ERROR)`, its message one string literal, so that the module's own diagnostics carry it; · `@Target()`, `BINARY` retention, `@MustBeDocumented`; · used only as the argument of `@SubclassOptInRequired` and `@OptIn`; never removed. · The gated types are `HttpTransport`, `WallClock`, `HttpFailure`, `TransportFailure` and `UpstreamFailure`. The supplied default `WallClock.System` is final and usable …",
              "hash": "1e272043a9f9"
            }
          },
          {
            "text": "The main source sets opt in as a language setting in each module's own build file: `commonMain` and `jvmMain` here, `commonMain` and `jsMain` in the check module.",
            "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
            "label": "§R7",
            "excerpt": {
              "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
              "heading": "R7. The opt-in wiring and the opening compile proof",
              "paragraph": "The module-wide opt-in is a language setting of the main source sets, in each module's own build file, not in a convention plugin: `webtools-http` sets it on `commonMain` and `jvmMain`; `webtools-http-commoncheck` on `commonMain` and `jsMain`. The build files say, in a comment, that `kotlin { compilerOptions { optIn.add(...) } }`, the target-level option and `sourceSets.all { }` reach the test compilations too and must not be used. · `commonTest` and `jvmTest` compile without the opt-in and stay a permanent consumer: they mention the gated types, use `WallClock.System` as a parameter default …",
              "hash": "5ad2577da247"
            }
          },
          {
            "text": "The test source sets stay without the opt-in, as a permanent consumer.",
            "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
            "label": "§R7",
            "excerpt": {
              "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
              "heading": "R7. The opt-in wiring and the opening compile proof",
              "paragraph": "The module-wide opt-in is a language setting of the main source sets, in each module's own build file, not in a convention plugin: `webtools-http` sets it on `commonMain` and `jvmMain`; `webtools-http-commoncheck` on `commonMain` and `jsMain`. The build files say, in a comment, that `kotlin { compilerOptions { optIn.add(...) } }`, the target-level option and `sourceSets.all { }` reach the test compilations too and must not be used. · `commonTest` and `jvmTest` compile without the opt-in and stay a permanent consumer: they mention the gated types, use `WallClock.System` as a parameter default …",
              "hash": "5ad2577da247"
            }
          }
        ]
      }
    },
    {
      "id": "failure-model",
      "group": "Code",
      "name": "The failure model and its two reason vocabularies",
      "system": "webtools-http-core",
      "where": "common/ — HttpFailure, TransportFailure, UpstreamFailure, RefusalReason, ExhaustedReason",
      "gap": {
        "kind": "missing",
        "summary": "No typed HTTP failure exists, and nothing carries one in `Result`. The repository's precedent is `webtools-udp`'s open `ReliableSendFailure`, carried in `Result` and deliberately not sealed; its `Result.flatMap` helper is `internal` and copied per module on purpose.",
        "evidence": [
          "webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ReliableSendFailure.kt:15 — `abstract class ReliableSendFailure(message: String) : Exception(message)` (baseline)",
          "webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ResultExtensions.kt:3-5 — the internal `flatMap`, deliberately duplicated in `webtools-scraping` (baseline)"
        ],
        "source": "../architecture.md §2",
        "link": "../architecture.md#2-current-state"
      },
      "options": [
        {
          "id": "open-hierarchy",
          "name": "An open, subclass-gated `abstract class HttpFailure : Exception` in `kotlin.Result`: the groups `TransportFailure` and `UpstreamFailure`, final leaves, one shared `Closed`, and the open constants classes `RefusalReason` and `ExhaustedReason`",
          "verdict": "accepted",
          "why": "It follows `ReliableSendFailure`: a leaf or a reason may be added in a feature release, and the KDoc says to match the groups and keep an `else`. Messages carry a constant per type and non-sensitive fields only. Cancellation is thrown, never wrapped: an internal non-fatal catch helper converts only exceptions other than `CancellationException`, and no `runCatching` wraps a suspend call.",
          "source": "../architecture.md §6.2, §9",
          "link": "../architecture.md#62-webtools-http-common-core"
        },
        {
          "id": "sealed-root",
          "name": "A sealed failure root, or enums for the library-produced reasons",
          "verdict": "declined",
          "why": "Each added leaf or entry is a compile error in a consumer's exhaustive `when` and a runtime `NoWhenBranchMatchedException` in a compiled one; Apollo's sealed hierarchy shows the cost.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/"
        ],
        "tests": "L1 gating: the consumer classes construct every public leaf without the opt-in (procedure P3 step 3). The Level 2 classes of the leaves and the reasons come in S4, with the Tests row.",
        "verified": "Procedure P3, recorded in `final-implementation.md` §3 on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#step-3--the-positive-consumer-cases",
        "statements": [
          {
            "text": "`HttpFailure` and its two groups were built in S2; qa pass 1 worded the message rule into `HttpFailure`'s KDoc.",
            "state": "as-planned",
            "ref": "final-implementation.md#qa-pass-1-fixes",
            "excerpt": {
              "ref": "final-implementation.md#qa-pass-1-fixes",
              "heading": "qa pass 1 fixes",
              "paragraph": "Twenty-seven minor fixes, applied on the spot. None changes behaviour or a signature. One adds the eighth point of `HttpTransport`'s documented contract (§2, deviation 2). · KDoc and comments in production code: `HttpFailure` class KDoc states the plan's message rule (constant text plus named non-sensitive fields; never free caller text, a header value or a body); `HttpHeaders.Builder.addSensitive` no longer promises the cross-origin strip (it marks the entry; the redirect policy of a later unit reads the flag); `HttpTransport` gained contract point 8 (`execute` is main-safe; the …",
              "hash": "f6994e06bbcc"
            }
          },
          {
            "text": "Every leaf nests in its group, as the dump shows (`HttpFailure$Closed`, `TransportFailure$Timeout`, `UpstreamFailure$RateLimited`).",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
            "excerpt": {
              "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
              "heading": "P6 — the dump, the class files, the published shape (S5)",
              "paragraph": "`./gradlew :webtools-http:apiDump` → `BUILD SUCCESSFUL`; `webtools-http/api/webtools-http.api` has 294 lines, written CRLF by the tool on this host (git stores it LF; `apiCheck` compares line by line). `./gradlew :webtools-http:apiCheck` → `BUILD SUCCESSFUL`. Read against plan §7.8, every point holds: `public abstract fun execute-gIAlu-s (…HttpRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`, a public `HttpTransport$DefaultImpls` with `public static fun close (…HttpTransport;)V`, `public fun close ()V`; `WallClock$System` final with `INSTANCE`, `nowEpochMillis ()J` and `toString`; …",
              "hash": "4f835e8ce361"
            }
          },
          {
            "text": "The consumer tests constructed every public leaf without the opt-in.",
            "state": "as-planned",
            "ref": "final-implementation.md#step-3--the-positive-consumer-cases",
            "excerpt": {
              "ref": "final-implementation.md#step-3--the-positive-consumer-cases",
              "heading": "Step 3 — the positive consumer cases",
              "paragraph": "`./gradlew :webtools-http:compileTestKotlinJvm` → `BUILD SUCCESSFUL in 7s`; no `e:` line, no gate warning, no compiler `w:` (the only `w:` is the configuration-time `JS Environment Not Selected`). `commonTest` and `jvmTest` compile without any opt-in while naming the gated types in signatures, using `WallClock.System` as a default and constructing every public leaf. · `./gradlew :webtools-http:gatingTest` → `BUILD SUCCESSFUL in 2s`; `GatedTypesConsumerGatingTest` 3 tests and `GatedTypesJvmConsumerGatingTest` 2 tests, 0 failures.",
              "hash": "965cc84ab74d"
            }
          },
          {
            "text": "`RefusalReason` holds nine constants and `ExhaustedReason` four.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape; the mechanical fixes made on the way are in §5.",
              "hash": "be79bfaf86d5"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Nesting keeps `Timeout`, `Protocol` and `Io` out of the flat package, where a top-level class would shadow a star-imported OkHttp or Okio name. Every message is a constant per type plus non-sensitive fields; `UpstreamError` copies its body in and out and never prints headers or body.",
        "steps": [
          {
            "id": "S2",
            "what": "Write `settings.gradle.kts`, both module build files, every declaration the gate touches, the value types other than `Url`, a `Url` shell with its final members, the `js` actuals and the permanent Level 1 consumer classes, then run the compile proof.",
            "files": [
              "settings.gradle.kts",
              "webtools-http/",
              "webtools-http-commoncheck/"
            ],
            "check": "Procedure P3, recorded in `final-implementation.md` §3; S3 does not start if it differs from the plan",
            "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
          }
        ],
        "tests": [
          "Level 1 — the consumer constructs every public leaf and matches the groups with an `else`",
          "Level 2 — the leaves, `UpstreamError` and the reason constants"
        ],
        "risks": [
          "A `cause` is outside the constant-message rule: OkHttp exceptions can carry hosts, resolved addresses or URLs, so the producer decides what it attaches (unit 6)"
        ],
        "source": "plan.md R4, §6.3",
        "link": "plan.md#63-the-failure-model",
        "statements": [
          {
            "text": "`HttpFailure` is an abstract, subclass-gated `Exception` with the groups `TransportFailure` and `UpstreamFailure`.",
            "ref": "plan.md#r4-the-failure-model",
            "label": "§R4",
            "excerpt": {
              "ref": "plan.md#r4-the-failure-model",
              "heading": "R4. The failure model",
              "paragraph": "`abstract class HttpFailure : Exception`, subclass-gated, with `protected` constructors `(message)` and `(message, cause)`. · The groups `TransportFailure` and `UpstreamFailure` are abstract, subclass-gated, with `protected` constructors. `UpstreamFailure` is `protected constructor(public val status: Int, message: String)`. · Final leaves nested in their group: · `HttpFailure`: `Closed`, `BudgetExhausted(wait: Duration, reason: ExhaustedReason, window: String?)`, `DecodeFailure`; · `TransportFailure`: `Timeout`, `ConnectFailed`, `DnsFailed(host)`, `TlsFailed`, `Protocol`, `Io`, …",
              "hash": "fcdfd008668b"
            }
          },
          {
            "text": "Final leaves nest in their group: `Closed`, `BudgetExhausted`, `DecodeFailure`, eleven transport leaves, `RateLimited` and `UpstreamError`.",
            "ref": "plan.md#r4-the-failure-model",
            "label": "§R4",
            "excerpt": {
              "ref": "plan.md#r4-the-failure-model",
              "heading": "R4. The failure model",
              "paragraph": "`abstract class HttpFailure : Exception`, subclass-gated, with `protected` constructors `(message)` and `(message, cause)`. · The groups `TransportFailure` and `UpstreamFailure` are abstract, subclass-gated, with `protected` constructors. `UpstreamFailure` is `protected constructor(public val status: Int, message: String)`. · Final leaves nested in their group: · `HttpFailure`: `Closed`, `BudgetExhausted(wait: Duration, reason: ExhaustedReason, window: String?)`, `DecodeFailure`; · `TransportFailure`: `Timeout`, `ConnectFailed`, `DnsFailed(host)`, `TlsFailed`, `Protocol`, `Io`, …",
              "hash": "fcdfd008668b"
            }
          },
          {
            "text": "Every leaf a seam implementer or a decoder creates has a public constructor; `RateLimited`'s is `internal`.",
            "ref": "plan.md#r4-the-failure-model",
            "label": "§R4",
            "excerpt": {
              "ref": "plan.md#r4-the-failure-model",
              "heading": "R4. The failure model",
              "paragraph": "`abstract class HttpFailure : Exception`, subclass-gated, with `protected` constructors `(message)` and `(message, cause)`. · The groups `TransportFailure` and `UpstreamFailure` are abstract, subclass-gated, with `protected` constructors. `UpstreamFailure` is `protected constructor(public val status: Int, message: String)`. · Final leaves nested in their group: · `HttpFailure`: `Closed`, `BudgetExhausted(wait: Duration, reason: ExhaustedReason, window: String?)`, `DecodeFailure`; · `TransportFailure`: `Timeout`, `ConnectFailed`, `DnsFailed(host)`, `TlsFailed`, `Protocol`, `Io`, …",
              "hash": "fcdfd008668b"
            }
          },
          {
            "text": "`RefusalReason` and `ExhaustedReason` are open constants classes, equal by name.",
            "ref": "plan.md#r4-the-failure-model",
            "label": "§R4",
            "excerpt": {
              "ref": "plan.md#r4-the-failure-model",
              "heading": "R4. The failure model",
              "paragraph": "`abstract class HttpFailure : Exception`, subclass-gated, with `protected` constructors `(message)` and `(message, cause)`. · The groups `TransportFailure` and `UpstreamFailure` are abstract, subclass-gated, with `protected` constructors. `UpstreamFailure` is `protected constructor(public val status: Int, message: String)`. · Final leaves nested in their group: · `HttpFailure`: `Closed`, `BudgetExhausted(wait: Duration, reason: ExhaustedReason, window: String?)`, `DecodeFailure`; · `TransportFailure`: `Timeout`, `ConnectFailed`, `DnsFailed(host)`, `TlsFailed`, `Protocol`, `Io`, …",
              "hash": "fcdfd008668b"
            }
          }
        ]
      }
    },
    {
      "id": "request-response",
      "group": "Code",
      "name": "Request and response types",
      "system": "webtools-http-core",
      "where": "common/ — HttpMethod, Url, HttpHeaders, RequestBody, HttpRequest, HttpResponse, RedirectHop",
      "gap": {
        "kind": "missing",
        "summary": "No library-owned request or response type exists. `Connector` reads a URL line by line over `java.net.URLConnection` and offers one-shot helpers over Unirest, skrapeit and jsoup, so a URL, headers, a body and a response have no type of their own that a port could take.",
        "evidence": [
          "webtools-scraping/src/main/kotlin/com/spartanlabs/webtools/scraping/Connector.kt:85 — `open(urlName: String)`, the line reader over `URLConnection` (baseline)",
          "webtools-scraping/src/main/kotlin/com/spartanlabs/webtools/scraping/Connector.kt:263 — `get(url: String)`, the one-shot Unirest call (baseline)"
        ],
        "source": "../architecture.md §2",
        "link": "../architecture.md#2-current-state"
      },
      "options": [
        {
          "id": "plain-common-classes",
          "name": "Plain common classes: a validated `Url` parsed once, ordered case-insensitive `HttpHeaders` with per-entry sensitive flags, and `HttpRequest` and `HttpResponse` built by factory and builder, the response carrying its redirect chain as `RedirectHop(url, status)`",
          "verdict": "accepted",
          "why": "Vocabulary with no platform content compiles in common code, and a builder over an `internal` constructor is additive by construction. `Url` is `http` or `https` only, with no user-info and a query-free `redacted()`. Developer-written values are checked with `require`, and text from outside the process returns `Result`. No received-at time, which would bypass the injected clock, no charset and no streaming.",
          "source": "../architecture.md §6.2, §6.9, §9",
          "link": "../architecture.md#62-webtools-http-common-core"
        },
        {
          "id": "okhttp-typealias",
          "name": "A JVM `actual typealias` to OkHttp's `HttpUrl` and `Headers`",
          "verdict": "declined",
          "why": "It would put OkHttp in a public signature.",
          "source": "../architecture.md §6.9",
          "link": "../architecture.md#69-common-code-mechanism-per-type"
        },
        {
          "id": "java-net-uri",
          "name": "`java.net.URI` as the URL model",
          "verdict": "declined",
          "why": "It parses hosts differently from OkHttp, the parser-disagreement bypass. The port's own `Url` is converted to `HttpUrl` inside the transport instead, and a differential test pins their agreement.",
          "source": "../architecture.md §6.9",
          "link": "../architecture.md#69-common-code-mechanism-per-type"
        },
        {
          "id": "flat-constructors",
          "name": "Flat constructors with defaulted parameters plus hidden deprecated overloads",
          "verdict": "declined",
          "why": "A permanent overload per addition, kept correct by hand, and `@IntroducedAt` is Kotlin 2.3+; builders are additive by construction.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "url-builder",
          "name": "A public `Url` builder or `withQuery`, or building URLs with `HttpUrl.Builder`",
          "verdict": "declined",
          "why": "Building a URL from parts is the facades' job, as text through the `internal` encoders and then `Url.parse`, so one parser and one canonical form decide the result; `HttpUrl.Builder` treats empty segments differently from text assembly. A public builder stays additive later.",
          "source": "plan.md R8, §6.5, §13",
          "link": "plan.md#65-url"
        },
        {
          "id": "idn-hosts",
          "name": "Internationalised host names in `Url`",
          "verdict": "declined",
          "why": "1.0.0 refuses them, `xn--` labels included; supporting them needs an IDNA path that agrees with OkHttp's UTS 46 mapping.",
          "source": "plan.md §4; impact.md §7",
          "link": "impact.md#7-follow-ups-this-unit-leaves"
        },
        {
          "id": "refuse-transport-headers",
          "name": "Refusing transport-owned header names (`Host`, `Content-Length`, `User-Agent`, `Connection`, `Transfer-Encoding`) in `HttpRequest`",
          "verdict": "declined",
          "why": "What the transport does with them is unit 6's to settle; the research left it open.",
          "source": "plan.md §4, §14",
          "link": "plan.md#4-non-goals"
        },
        {
          "id": "stdlib-case-fold",
          "name": "Kotlin's `lowercase()` and `equals(ignoreCase = true)` for header names, schemes and hosts",
          "verdict": "declined",
          "why": "They fold U+212A (Kelvin) to `k` and U+0131 to `i`, so protocol text uses an ASCII-only fold written by hand.",
          "source": "plan.md §6.4",
          "link": "plan.md#64-the-request-and-response-value-types"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/"
        ],
        "tests": "L4a deterministic: seven `Url` classes, 27 tests; L4c non-functional: three `Url` classes, 6 tests (secrecy, 50,000 garbage inputs, linear time); all green (procedure P5). The Level 2 classes of these types and the header, method, status and media-type tables come in S4, with the Tests row.",
        "verified": "Procedure P3 for S2 (`final-implementation.md` §3) and procedure P5 after S3 (§5), recorded on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#p5--the-commontest-levels-after-s3",
        "statements": [
          {
            "text": "The value types came in S2 and the final `Url`, over `UrlParsing`, `UrlHost` and `UrlEncoding`, in S3; the check module's `js` compile shows they use no JVM API.",
            "state": "as-planned",
            "ref": "final-implementation.md#p5--the-commontest-levels-after-s3",
            "excerpt": {
              "ref": "final-implementation.md#p5--the-commontest-levels-after-s3",
              "heading": "P5 — the `commonTest` levels after S3",
              "paragraph": "`./gradlew :webtools-http:gatingTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` → `BUILD SUCCESSFUL`; 0 failures: · `gatingTest`: `GatedTypesConsumerGatingTest` 3, `GatedTypesJvmConsumerGatingTest` 2 (5 tests). · `deterministicTest`: `UrlAcceptDeterministicTest` 2, `UrlBoundaryDeterministicTest` 4, `UrlConfusionDeterministicTest` 1, `UrlEncodersDeterministicTest` 5, `UrlIpv6DeterministicTest` 9, `UrlPrintingDeterministicTest` 3, `UrlRejectDeterministicTest` 3 (27 tests). · `nonfunctionalTest`: `UrlParseGarbageNonFunctionalTest` 1 (50,000 inputs, seed 11), …",
              "hash": "344d912ec18f"
            }
          },
          {
            "text": "`Url.parse` agreed with `HttpUrl` on the corpus and on 4,200,000 generated inputs, with every coverage floor met.",
            "state": "as-planned",
            "ref": "final-implementation.md#p4--the-url-differential-s4",
            "excerpt": {
              "ref": "final-implementation.md#p4--the-url-differential-s4",
              "heading": "P4 — the `Url` differential (S4)",
              "paragraph": "Default size, inside `deterministicTest` above (seed 7, 200,000 generated inputs plus the 203 corpus inputs): 0 disagreements. Coverage: `inputs=200203 accepted=61564` (30.7%), `httpUrlThrew=498`, accepted inputs with `[` 32,102, with a dot segment 23,523, with `?` 27,161, with a percent escape 33,988, with non-ASCII 12,619; `rejectedByKind={Empty=343, Backslash=20248, WhitespaceOrControl=35640, NotUnicode=16913, Scheme=50672, UserInfo=1108, HostEncoding=3482, HostIdn=345, Host=8178, Port=1710}`. Running time of the differential test method: about 3.1 s.",
              "hash": "09adeeb289ed"
            }
          },
          {
            "text": "`toString()` and `redacted()` print as decided, and `Url.toString`'s KDoc carries the query warning.",
            "state": "as-planned",
            "ref": "final-implementation.md#6-level-5--the-manual-pass",
            "excerpt": {
              "ref": "final-implementation.md#6-level-5--the-manual-pass",
              "heading": "6. Level 5 — the manual pass",
              "paragraph": "Procedure P10 of plan §9.2, run from the command line. The three IDE items (1, 2 and 4) need an IntelliJ session and were not performed. · Owner: the maintainer. · Trigger: the next IntelliJ import of the repository after this unit lands, and at the latest before unit 4 (#63) starts. That unit's fixtures compilation adds another source set the IDE must import (`impact.md` §7).",
              "hash": "f88c0a555fec"
            }
          },
          {
            "text": "The header rules agreed with `Request.Builder`, and the media-type table follows plan Amendment 1.",
            "state": "as-planned",
            "ref": "final-implementation.md#p5--the-fast-loop-after-s4",
            "excerpt": {
              "ref": "final-implementation.md#p5--the-fast-loop-after-s4",
              "heading": "P5 — the fast loop after S4",
              "paragraph": "`./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` → `BUILD SUCCESSFUL in 10s`, 0 failures: · `gatingTest`: 5 tests in 2 classes. · `componentTest`: 68 tests in 14 classes (11 `commonTest`, 3 `jvmTest`: `Slf4jLogComponentTest`, `CatchNonFatalComponentTest`, `EpochMillisJvmComponentTest`). · `deterministicTest`: 48 tests in 16 classes (13 `commonTest`, 3 `jvmTest` differentials). · `nonfunctionalTest`: 9 tests in 4 classes.",
              "hash": "ed90df43bfcb"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Configurable types are built by a factory over an `internal` constructor, so later additions stay additive. Developer-written values throw `IllegalArgumentException`, and text from outside the process returns `Result` with a message that never echoes the input. Byte arrays are copied in and out, and the types print their structure only. The `Url` rules follow the interview's two answers.",
        "steps": [
          {
            "id": "S2",
            "what": "Write `settings.gradle.kts`, both module build files, every declaration the gate touches, the value types other than `Url`, a `Url` shell with its final members, the `js` actuals and the permanent Level 1 consumer classes, then run the compile proof.",
            "files": [
              "settings.gradle.kts",
              "webtools-http/",
              "webtools-http-commoncheck/"
            ],
            "check": "Procedure P3, recorded in `final-implementation.md` §3; S3 does not start if it differs from the plan",
            "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
          },
          {
            "id": "S3",
            "what": "Replace the `Url` shell with the parser, and add the encoders, the corpus, the generator and the `Url` tests.",
            "files": [
              "webtools-http/src/commonMain/",
              "webtools-http/src/commonTest/"
            ],
            "check": "Procedure P5, the `commonTest` levels",
            "link": "plan.md#65-url"
          }
        ],
        "tests": [
          "Level 2 — one component class per type",
          "Level 4a — the `Url`, header, method, status and media-type tables (the Tests row)",
          "Level 4c — secrecy of every message, 50,000 garbage inputs, linear-time parsing"
        ],
        "risks": [
          "`Url` equality is on the canonical text, not the resource: `%2f` and `%2F`, `h` and `h.` are different values",
          "Public names the architecture does not fix are chosen here; they can still change before 1.0.0 is published"
        ],
        "source": "plan.md R8–R10, §6.4, §6.5",
        "link": "plan.md#64-the-request-and-response-value-types",
        "statements": [
          {
            "text": "`HttpMethod`, `Url`, `HttpHeaders`, `RequestBody`, `HttpRequest`, `HttpResponse` and `RedirectHop` are built, with the shared predicates in `internal object HttpSyntax`.",
            "ref": "plan.md#64-the-request-and-response-value-types",
            "excerpt": {
              "ref": "plan.md#64-the-request-and-response-value-types",
              "heading": "6.4 The request and response value types",
              "paragraph": "`HttpSyntax` (`internal object`, `commonMain`) holds every predicate, as `Char` comparisons with no regex and no JVM API:",
              "hash": "3e8e5618a6b3"
            }
          },
          {
            "text": "`Url.parse` reads like OkHttp 5.5.0's `HttpUrl` for ASCII hosts and is only ever stricter.",
            "ref": "plan.md#r8-url",
            "label": "§R8",
            "excerpt": {
              "ref": "plan.md#r8-url",
              "heading": "R8. `Url`",
              "paragraph": "`Url.parse(text): Result<Url>` accepts text and rewrites it exactly as OkHttp 5.5.0's `HttpUrl` does when the host is ASCII: · percent-encodes with `HttpUrl`'s sets, non-ASCII path and query text as UTF-8 in upper-case hex; · resolves `.` and `..`, the `%2e` forms included; · drops the default port and gives an empty path `/`; · trims TAB, LF, FF, CR and SP at the ends; · drops a `#fragment`; · keeps an existing `%XX`, and a malformed `%`, as typed; · keeps a bare `?` as an empty query (`\"\"`, not `null`); · prints IPv6 in RFC 5952 form and an IPv4-mapped address as dotted IPv4; · keeps …",
              "hash": "4977837c63e9"
            }
          },
          {
            "text": "`toString()` is the full canonical URL, and `redacted()` the form for logs.",
            "ref": "plan.md#r8-url",
            "label": "§R8",
            "excerpt": {
              "ref": "plan.md#r8-url",
              "heading": "R8. `Url`",
              "paragraph": "`Url.parse(text): Result<Url>` accepts text and rewrites it exactly as OkHttp 5.5.0's `HttpUrl` does when the host is ASCII: · percent-encodes with `HttpUrl`'s sets, non-ASCII path and query text as UTF-8 in upper-case hex; · resolves `.` and `..`, the `%2e` forms included; · drops the default port and gives an empty path `/`; · trims TAB, LF, FF, CR and SP at the ends; · drops a `#fragment`; · keeps an existing `%XX`, and a malformed `%`, as typed; · keeps a bare `?` as an empty query (`\"\"`, not `null`); · prints IPv6 in RFC 5952 form and an IPv4-mapped address as dotted IPv4; · keeps …",
              "hash": "4977837c63e9"
            }
          },
          {
            "text": "Header names are RFC 9110 tokens; `HttpRequest` refuses `CONNECT`, a body on `GET`, `HEAD` or `TRACE`, a value it cannot send and a second content type.",
            "ref": "plan.md#r9-headers-methods-statuses-and-bodies",
            "label": "§R9",
            "excerpt": {
              "ref": "plan.md#r9-headers-methods-statuses-and-bodies",
              "heading": "R9. Headers, methods, statuses and bodies",
              "paragraph": "Header names are the RFC 9110 token everywhere, checked in the `HttpHeaders` builder. · Header values obey two rules: the container rule (after trimming SP and HTAB at the ends, no CR, LF or NUL) and the sendable rule (HTAB or `0x20..0x7E`), which `HttpRequest` applies to every value it will send. · Lookup uses an ASCII-only fold. `get` returns the last value; `getAll` every value, empty when absent; `set` replaces every entry of the name, appends at the end and keeps an existing sensitive flag; `setSensitive`, `remove` and `addAll` complete the builder. `Entry.isSensitive` is true when the …",
              "hash": "85a85c567714"
            }
          }
        ]
      }
    },
    {
      "id": "transport-port",
      "group": "Code",
      "name": "The transport port HttpTransport",
      "system": "webtools-http-core",
      "where": "common/",
      "gap": {
        "kind": "missing",
        "summary": "Nothing offers a suspend request. `Connector`, the only HTTP client, is blocking and stateful: it sets the JVM-wide `http.agent` on every connect and calls `Unirest.setTimeouts(0, 0)` in its `init`, and there is no port a consumer or a fake could substitute.",
        "evidence": [
          "webtools-scraping/src/main/kotlin/com/spartanlabs/webtools/scraping/Connector.kt:150 — `System.setProperty(\"http.agent\", BROWSER_USER_AGENT)` (baseline)",
          "webtools-scraping/src/main/kotlin/com/spartanlabs/webtools/scraping/Connector.kt:65 — `Unirest.setTimeouts(0, 0)` (baseline)"
        ],
        "source": "../architecture.md §2; ../research/README.md § What the change touches",
        "link": "../architecture.md#2-current-state"
      },
      "options": [
        {
          "id": "gated-interface",
          "name": "`HttpTransport : AutoCloseable`, a subclass-gated interface with one `suspend fun execute(request): Result<HttpResponse>` and a `close()` with an empty default body",
          "verdict": "accepted",
          "why": "Consumers and the fakes substitute it, and an interface is a narrower contract than an overridable class and gates cleanly. A `304` is a response, never a failure; cancellation is thrown, never wrapped. The contract (one request per hop, caps, `Closed` after `close()`) is stated in KDoc, and the seam grows only by methods with bodies.",
          "source": "../architecture.md §6.2, §6.9, §9",
          "link": "../architecture.md#62-webtools-http-common-core"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "files": [
          "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/"
        ],
        "tests": "L1 gating: the consumer classes name the port without the opt-in; the eight negative cases, implementers without the opt-in, failed with exactly eight consumer errors in `commonTest` and in `jvmTest` and compiled with `@OptIn` (procedure P3 step 4).",
        "verified": "Procedure P3, recorded in `final-implementation.md` §3 on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#step-4--the-eight-negative-cases",
        "statements": [
          {
            "text": "`execute` and the default `close()` are in the dump; a throwaway `jvmMain` implementer failed without the module's opt-in lines and compiled with them.",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
            "excerpt": {
              "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
              "heading": "P6 — the dump, the class files, the published shape (S5)",
              "paragraph": "`./gradlew :webtools-http:apiDump` → `BUILD SUCCESSFUL`; `webtools-http/api/webtools-http.api` has 294 lines, written CRLF by the tool on this host (git stores it LF; `apiCheck` compares line by line). `./gradlew :webtools-http:apiCheck` → `BUILD SUCCESSFUL`. Read against plan §7.8, every point holds: `public abstract fun execute-gIAlu-s (…HttpRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`, a public `HttpTransport$DefaultImpls` with `public static fun close (…HttpTransport;)V`, `public fun close ()V`; `WallClock$System` final with `INSTANCE`, `nowEpochMillis ()J` and `toString`; …",
              "hash": "4f835e8ce361"
            }
          },
          {
            "text": "The KDoc states the seven points and an eighth: `execute` is main-safe, so an implementation moves blocking I/O off the caller's thread and documents its dispatcher.",
            "state": "amended",
            "ref": "final-implementation.md#2-deviations-from-the-plan",
            "excerpt": {
              "ref": "final-implementation.md#2-deviations-from-the-plan",
              "heading": "2. Deviations from the plan",
              "paragraph": "Three. None changes a rule of the design, a signature, a message or a name. Plan Amendment 1 (the media-type test row) and Amendment 2 (two test-scope positions in §9) are part of the plan and were followed.",
              "hash": "488f7ebe9f8d"
            }
          }
        ],
        "changes": [
          {
            "what": "An eighth point in `HttpTransport`'s contract: `execute` is main-safe, so an implementation moves blocking I/O off the caller's thread and documents its dispatcher.",
            "why": "Plan §6.6 lists seven points; a `suspend` function that blocks its caller's thread breaks the convention every coroutine caller relies on, so the transports to come state where their work runs. KDoc only; no signature changes.",
            "when": "qa pass 1",
            "ref": "final-implementation.md#2-deviations-from-the-plan"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The contract is the integration surface for a consumer's own transport and for unit 4's fakes, so it is stated where an implementer reads it; the seam grows only by methods with bodies.",
        "steps": [
          {
            "id": "S2",
            "what": "Write `settings.gradle.kts`, both module build files, every declaration the gate touches, the value types other than `Url`, a `Url` shell with its final members, the `js` actuals and the permanent Level 1 consumer classes, then run the compile proof.",
            "files": [
              "settings.gradle.kts",
              "webtools-http/",
              "webtools-http-commoncheck/"
            ],
            "check": "Procedure P3, recorded in `final-implementation.md` §3; S3 does not start if it differs from the plan",
            "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
          }
        ],
        "tests": [
          "Level 1 — the consumer names the port in signatures and cannot implement it without the opt-in"
        ],
        "risks": [
          "Transport-owned header names are not refused by `HttpRequest`; unit 6 settles what the transport does with them"
        ],
        "source": "plan.md R5, §6.6",
        "link": "plan.md#66-ports-the-logging-seam-and-the-helpers",
        "statements": [
          {
            "text": "`HttpTransport : AutoCloseable` is a subclass-gated interface with `suspend fun execute(request): Result<HttpResponse>` and an empty default `close()`.",
            "ref": "plan.md#r5-the-ports",
            "label": "§R5",
            "excerpt": {
              "ref": "plan.md#r5-the-ports",
              "heading": "R5. The ports",
              "paragraph": "`HttpTransport : AutoCloseable`: `public suspend fun execute(request: HttpRequest): Result<HttpResponse>` and `override fun close() {}`. The KDoc contract: one request per hop, the caps, a `304` is a response and never a failure, cancellation is thrown, `Closed` after `close()`. · `WallClock`: `public fun nowEpochMillis(): Long`, with a nested `public object System : WallClock` (the shape of the stdlib's `TimeSource.Monotonic`), `toString()` `\"WallClock.System\"`, over `internal expect fun epochMillisNow(): Long`.",
              "hash": "f78cf363e3ba"
            }
          },
          {
            "text": "Its KDoc states a seven-point contract: one request per hop, the documented caps, a `304` a response, a status outside 100..599 `Protocol`, received entries sanitised, cancellation thrown, `Closed` after an idempotent `close()`, and concurrent calls allowed.",
            "ref": "plan.md#66-ports-the-logging-seam-and-the-helpers",
            "excerpt": {
              "ref": "plan.md#66-ports-the-logging-seam-and-the-helpers",
              "heading": "6.6 Ports, the logging seam and the helpers",
              "paragraph": "`HttpTransport` — the KDoc states the contract an implementer (a consumer's transport or a fake) meets: · `execute` sends exactly one request per hop; it never retries, and it does not follow a redirect unless the implementation documents that it does and records each hop in `redirectChain`. · It enforces the implementation's documented caps (body size, redirects, timeouts) and reports a breach as the matching `TransportFailure` (`BodyTooLarge`, `TooManyRedirects`, `Timeout`). · A `304` is a response, never a failure. Any status in 100..599 is an `HttpResponse`; a status outside that range is …",
              "hash": "8b9c83af7025"
            }
          }
        ]
      }
    },
    {
      "id": "wall-clock",
      "group": "Code",
      "name": "The wall-clock port WallClock",
      "system": "webtools-http-core",
      "where": "common/, jvm/",
      "gap": {
        "kind": "missing",
        "summary": "No injectable wall clock exists, and the standard library's own types are not open to a library on Kotlin 2.2.0: `kotlin.time.Clock` and `Instant` need an error-level opt-in there and are stable only from 2.3.0.",
        "evidence": [
          "../research/README.md § Call budget, Time — `Clock` and `Instant` need an error-level opt-in on Kotlin 2.2.0",
          "../architecture.md §2 — the same, among the verified outside-world facts"
        ],
        "source": "../research/README.md § Call budget",
        "link": "../research/README.md#call-budget"
      },
      "options": [
        {
          "id": "epoch-millis-port",
          "name": "A plain gated interface `WallClock` with `nowEpochMillis(): Long`, and `WallClock.System` over an `internal expect fun epochMillisNow()`",
          "verdict": "accepted",
          "why": "Epoch milliseconds keep experimental standard-library API out of a library's signatures. The platform reading is one line per target (`System.currentTimeMillis()`, `Date.now()`), and substitution goes through `WallClock`.",
          "source": "../architecture.md §6.2, §6.9",
          "link": "../architecture.md#69-common-code-mechanism-per-type"
        },
        {
          "id": "kotlin-time",
          "name": "`kotlin.time.Clock` and `Instant`, or kotlinx-datetime, in signatures",
          "verdict": "declined",
          "why": "An error-level opt-in on every 2.2.x consumer, and the budget needs no calendar arithmetic.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "clock-system-opt-in",
          "name": "`kotlin.time.Clock.System` under `@OptIn(ExperimentalTime)` behind `WallClock.System`",
          "verdict": "declined",
          "why": "It works and was measured binary-compatible across stdlib 2.2.0 to 2.4.10, but the standard library's own KDoc tells libraries not to depend on experimental time API.",
          "source": "../architecture.md §6.9",
          "link": "../architecture.md#69-common-code-mechanism-per-type"
        },
        {
          "id": "fun-interface",
          "name": "A `fun interface` for `WallClock`",
          "verdict": "declined",
          "why": "A `fun interface` cannot carry the subclass gate; one rule holds for every seam.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/",
          "webtools-http/src/jvmMain/",
          "webtools-http-commoncheck/src/jsMain/"
        ],
        "tests": "L1 gating: `WallClock.System` as a default compiles without the opt-in (procedure P3 step 3). The Level 2 classes of `WallClock.System` and the JVM actual come in S4, with the Tests row.",
        "verified": "Procedure P3, recorded in `final-implementation.md` §3 on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#step-3--the-positive-consumer-cases",
        "statements": [
          {
            "text": "Throwaway `WallClock` implementers in `commonMain` and the check module's `jsMain` failed without the opt-in lines, as predicted.",
            "state": "as-planned",
            "ref": "final-implementation.md#step-2--the-wiring-canary",
            "excerpt": {
              "ref": "final-implementation.md#step-2--the-wiring-canary",
              "heading": "Step 2 — the wiring canary",
              "paragraph": "Three throwaway top-level implementers were added, exactly as the plan writes them: `ProofClock.kt` (`commonMain`, `: WallClock`), `ProofTransport.kt` (`jvmMain`, `: HttpTransport`) and `ProofJsClock.kt` (the check module's `jsMain`, `: WallClock`).",
              "hash": "b12290b81124"
            }
          },
          {
            "text": "`WallClock.System` is final with `INSTANCE`, and the consumer tests used it as a default without the opt-in.",
            "state": "as-planned",
            "ref": "final-implementation.md#step-3--the-positive-consumer-cases",
            "excerpt": {
              "ref": "final-implementation.md#step-3--the-positive-consumer-cases",
              "heading": "Step 3 — the positive consumer cases",
              "paragraph": "`./gradlew :webtools-http:compileTestKotlinJvm` → `BUILD SUCCESSFUL in 7s`; no `e:` line, no gate warning, no compiler `w:` (the only `w:` is the configuration-time `JS Environment Not Selected`). `commonTest` and `jvmTest` compile without any opt-in while naming the gated types in signatures, using `WallClock.System` as a default and constructing every public leaf. · `./gradlew :webtools-http:gatingTest` → `BUILD SUCCESSFUL in 2s`; `GatedTypesConsumerGatingTest` 3 tests and `GatedTypesJvmConsumerGatingTest` 2 tests, 0 failures.",
              "hash": "965cc84ab74d"
            }
          },
          {
            "text": "`EpochMillis.jvm.kt` and the check module's `JsActuals.kt` hold the two actuals.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape; the mechanical fixes made on the way are in §5.",
              "hash": "be79bfaf86d5"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The shape of the standard library's `TimeSource.Monotonic`, with epoch milliseconds keeping experimental time API out of the signatures. The actual files are named apart from the `expect` file, so no two JVM facade classes clash.",
        "steps": [
          {
            "id": "S2",
            "what": "Write `settings.gradle.kts`, both module build files, every declaration the gate touches, the value types other than `Url`, a `Url` shell with its final members, the `js` actuals and the permanent Level 1 consumer classes, then run the compile proof.",
            "files": [
              "settings.gradle.kts",
              "webtools-http/",
              "webtools-http-commoncheck/"
            ],
            "check": "Procedure P3, recorded in `final-implementation.md` §3; S3 does not start if it differs from the plan",
            "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
          }
        ],
        "tests": [
          "Level 1 — `WallClock.System` as a parameter default in the consumer",
          "Level 2 — `WallClock.System` and the JVM actual"
        ],
        "risks": [
          "`WallClockSystemComponentTest` bounds two reads by 5 s; a failure on a loaded host is rerun once before it counts as a defect"
        ],
        "source": "plan.md R5, §6.1, §6.6",
        "link": "plan.md#66-ports-the-logging-seam-and-the-helpers",
        "statements": [
          {
            "text": "`WallClock` with `nowEpochMillis(): Long` is a gated interface.",
            "ref": "plan.md#r5-the-ports",
            "label": "§R5",
            "excerpt": {
              "ref": "plan.md#r5-the-ports",
              "heading": "R5. The ports",
              "paragraph": "`HttpTransport : AutoCloseable`: `public suspend fun execute(request: HttpRequest): Result<HttpResponse>` and `override fun close() {}`. The KDoc contract: one request per hop, the caps, a `304` is a response and never a failure, cancellation is thrown, `Closed` after `close()`. · `WallClock`: `public fun nowEpochMillis(): Long`, with a nested `public object System : WallClock` (the shape of the stdlib's `TimeSource.Monotonic`), `toString()` `\"WallClock.System\"`, over `internal expect fun epochMillisNow(): Long`.",
              "hash": "f78cf363e3ba"
            }
          },
          {
            "text": "Its nested default `WallClock.System` is final and usable without the opt-in.",
            "ref": "plan.md#r5-the-ports",
            "label": "§R5",
            "excerpt": {
              "ref": "plan.md#r5-the-ports",
              "heading": "R5. The ports",
              "paragraph": "`HttpTransport : AutoCloseable`: `public suspend fun execute(request: HttpRequest): Result<HttpResponse>` and `override fun close() {}`. The KDoc contract: one request per hop, the caps, a `304` is a response and never a failure, cancellation is thrown, `Closed` after `close()`. · `WallClock`: `public fun nowEpochMillis(): Long`, with a nested `public object System : WallClock` (the shape of the stdlib's `TimeSource.Monotonic`), `toString()` `\"WallClock.System\"`, over `internal expect fun epochMillisNow(): Long`.",
              "hash": "f78cf363e3ba"
            }
          },
          {
            "text": "`WallClock.System` reads `internal expect fun epochMillisNow()`: `System.currentTimeMillis()` in `jvmMain`, `Date.now()` in the check module's `jsMain`.",
            "ref": "plan.md#r5-the-ports",
            "label": "§R5",
            "excerpt": {
              "ref": "plan.md#r5-the-ports",
              "heading": "R5. The ports",
              "paragraph": "`HttpTransport : AutoCloseable`: `public suspend fun execute(request: HttpRequest): Result<HttpResponse>` and `override fun close() {}`. The KDoc contract: one request per hop, the caps, a `304` is a response and never a failure, cancellation is thrown, `Closed` after `close()`. · `WallClock`: `public fun nowEpochMillis(): Long`, with a nested `public object System : WallClock` (the shape of the stdlib's `TimeSource.Monotonic`), `toString()` `\"WallClock.System\"`, over `internal expect fun epochMillisNow(): Long`.",
              "hash": "f78cf363e3ba"
            }
          }
        ]
      }
    },
    {
      "id": "logging-seam",
      "group": "Code",
      "name": "The logging seam",
      "system": "webtools-http-core",
      "where": "common/, jvm/",
      "gap": {
        "kind": "missing",
        "summary": "Common code has no way to log. `slf4j-api` is JVM-only, while the repository's rule asks for logging of lifecycle events, data flows and failure states; the build findings had left `commonMain` without a logger.",
        "evidence": [
          "../research/README.md § Where the findings differ, Logging in common code",
          "../architecture.md §2 — logging in the repository is parameterised slf4j with `{}` placeholders"
        ],
        "source": "../research/README.md § Where the findings differ",
        "link": "../research/README.md#where-the-findings-differ-and-which-view-holds"
      },
      "options": [
        {
          "id": "expect-fun-logger",
          "name": "An `internal interface Log` obtained through `internal expect fun logger(name)`, with an slf4j actual in `jvm/` and a no-op `js` actual in the check module",
          "verdict": "accepted",
          "why": "`expect fun` raises no Beta warning, and a missing actual is a compile error, which the check module enforces. Every logging site uses it, so there is one style and one test-capture path (logback in `jvmTest`).",
          "source": "../architecture.md §6.2, §6.9",
          "link": "../architecture.md#69-common-code-mechanism-per-type"
        },
        {
          "id": "kotlin-logging",
          "name": "kotlin-logging in `commonMain`",
          "verdict": "declined",
          "why": "A startup line, a runtime dependency on every consumer, and no gain with one published target; the `internal expect fun` seam is the smallest thing that logs by default.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/",
          "webtools-http/src/jvmMain/",
          "webtools-http-commoncheck/src/jsMain/",
          "webtools-http/src/jvmTest/"
        ],
        "tests": "L2 component in `jvmTest`: `Slf4jLogComponentTest` and `CatchNonFatalComponentTest`, inside `componentTest`'s 68 tests in 14 classes, all green (procedure P5 after S4).",
        "verified": "Procedure P3 for S2 (`final-implementation.md` §3) and procedure P5 after S4 (§5), recorded on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#p5--the-fast-loop-after-s4",
        "statements": [
          {
            "text": "`Log.jvm.kt` wraps slf4j, and its Level 2 test checks the mapping against a logback `ListAppender`.",
            "state": "as-planned",
            "ref": "final-implementation.md#p5--the-fast-loop-after-s4",
            "excerpt": {
              "ref": "final-implementation.md#p5--the-fast-loop-after-s4",
              "heading": "P5 — the fast loop after S4",
              "paragraph": "`./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` → `BUILD SUCCESSFUL in 10s`, 0 failures: · `gatingTest`: 5 tests in 2 classes. · `componentTest`: 68 tests in 14 classes (11 `commonTest`, 3 `jvmTest`: `Slf4jLogComponentTest`, `CatchNonFatalComponentTest`, `EpochMillisJvmComponentTest`). · `deterministicTest`: 48 tests in 16 classes (13 `commonTest`, 3 `jvmTest` differentials). · `nonfunctionalTest`: 9 tests in 4 classes.",
              "hash": "ed90df43bfcb"
            }
          },
          {
            "text": "The `flatMap` copy is in, and the two legacy copies are unchanged.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape; the mechanical fixes made on the way are in §5.",
              "hash": "be79bfaf86d5"
            }
          },
          {
            "text": "`catchNonFatal` is the module's one `try`/`catch`, in `NonFatal.kt`.",
            "state": "as-planned",
            "ref": "final-implementation.md#p9--mechanical-rules",
            "excerpt": {
              "ref": "final-implementation.md#p9--mechanical-rules",
              "heading": "P9 — mechanical rules",
              "paragraph": "Each command printed nothing except where the plan says otherwise: the forbidden-API grep over `commonMain` (`java.`, `javax.`, `@Jvm`, `runCatching`, `lowercase(`, `uppercase(`, `ignoreCase`, `removeFirst`, `removeLast`, `String.format`, `Regex(`) → nothing; `catch` → `NonFatal.kt` only (lines 15, 18, 20); `data class` → nothing; `TODO` → nothing; `OptIn` in `commonTest`/`jvmTest` → nothing; the internal names in the dump → nothing; `grep -c '\\$default'` → 3; the KDoc-coverage `awk` over `commonMain` and `jvmMain` → nothing; `git hash-object` of the new `logback-test.xml` → …",
              "hash": "61f0af1978f7"
            }
          },
          {
            "text": "No production code logs; `logback-test.xml` is a byte copy of `webtools-udp`'s.",
            "state": "as-planned",
            "ref": "final-implementation.md#p5--the-fast-loop-after-s4",
            "excerpt": {
              "ref": "final-implementation.md#p5--the-fast-loop-after-s4",
              "heading": "P5 — the fast loop after S4",
              "paragraph": "`./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` → `BUILD SUCCESSFUL in 10s`, 0 failures: · `gatingTest`: 5 tests in 2 classes. · `componentTest`: 68 tests in 14 classes (11 `commonTest`, 3 `jvmTest`: `Slf4jLogComponentTest`, `CatchNonFatalComponentTest`, `EpochMillisJvmComponentTest`). · `deterministicTest`: 48 tests in 16 classes (13 `commonTest`, 3 `jvmTest` differentials). · `nonfunctionalTest`: 9 tests in 4 classes.",
              "hash": "ed90df43bfcb"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The seam and its rules, levels and the never-list (headers, bodies, query strings, credentials, any `Url` except through `redacted()`), are delivered for the later units. `catchNonFatal` is the module's one `try`/`catch`, inline so it surrounds suspend calls safely; the two legacy `ResultExtensions.kt` files are not edited.",
        "steps": [
          {
            "id": "S2",
            "what": "Write `settings.gradle.kts`, both module build files, every declaration the gate touches, the value types other than `Url`, a `Url` shell with its final members, the `js` actuals and the permanent Level 1 consumer classes, then run the compile proof.",
            "files": [
              "settings.gradle.kts",
              "webtools-http/",
              "webtools-http-commoncheck/"
            ],
            "check": "Procedure P3, recorded in `final-implementation.md` §3; S3 does not start if it differs from the plan",
            "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
          },
          {
            "id": "S4",
            "what": "Add the remaining Level 2, 4a and 4c tests, the three differentials and `logback-test.xml`.",
            "files": [
              "webtools-http/src/commonTest/",
              "webtools-http/src/jvmTest/"
            ],
            "check": "Procedures P4 and P5",
            "link": "plan.md#9-test-plan"
          }
        ],
        "tests": [
          "Level 2 — the slf4j mapping against a logback `ListAppender`, `catchNonFatal` under cancellation and timeout, `flatMap`"
        ],
        "risks": [],
        "source": "plan.md R6, §6.6, §6.9",
        "link": "plan.md#66-ports-the-logging-seam-and-the-helpers",
        "statements": [
          {
            "text": "`internal interface Log` (`debug`, `info`, `warn` with `{}` placeholders) comes through `internal expect fun logger(name)`: slf4j in `jvmMain`, a no-op in the check module.",
            "ref": "plan.md#r6-the-logging-seam-and-the-helpers",
            "label": "§R6",
            "excerpt": {
              "ref": "plan.md#r6-the-logging-seam-and-the-helpers",
              "heading": "R6. The logging seam and the helpers",
              "paragraph": "`internal interface Log` with `debug`, `info` and `warn(message: String, vararg args: Any?)` taking `{}` placeholders, obtained through `internal expect fun logger(name: String): Log`. The `jvmMain` actual wraps slf4j's `LoggerFactory.getLogger(name)`; the check module's `jsMain` actual is a no-op. This unit has no production logging site; the seam is delivered and tested for the later units. · An `internal` copy of `Result.flatMap`, identical in body to `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ResultExtensions.kt:19-20`; its header names both other copies. The two legacy …",
              "hash": "55e09cca2e0e"
            }
          },
          {
            "text": "A third `internal` copy of `Result.flatMap` names the other two in its header; the legacy files are not edited.",
            "ref": "plan.md#r6-the-logging-seam-and-the-helpers",
            "label": "§R6",
            "excerpt": {
              "ref": "plan.md#r6-the-logging-seam-and-the-helpers",
              "heading": "R6. The logging seam and the helpers",
              "paragraph": "`internal interface Log` with `debug`, `info` and `warn(message: String, vararg args: Any?)` taking `{}` placeholders, obtained through `internal expect fun logger(name: String): Log`. The `jvmMain` actual wraps slf4j's `LoggerFactory.getLogger(name)`; the check module's `jsMain` actual is a no-op. This unit has no production logging site; the seam is delivered and tested for the later units. · An `internal` copy of `Result.flatMap`, identical in body to `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ResultExtensions.kt:19-20`; its header names both other copies. The two legacy …",
              "hash": "55e09cca2e0e"
            }
          },
          {
            "text": "`catchNonFatal` turns an `Exception` other than `CancellationException` into a failure and lets `Error` through.",
            "ref": "plan.md#r6-the-logging-seam-and-the-helpers",
            "label": "§R6",
            "excerpt": {
              "ref": "plan.md#r6-the-logging-seam-and-the-helpers",
              "heading": "R6. The logging seam and the helpers",
              "paragraph": "`internal interface Log` with `debug`, `info` and `warn(message: String, vararg args: Any?)` taking `{}` placeholders, obtained through `internal expect fun logger(name: String): Log`. The `jvmMain` actual wraps slf4j's `LoggerFactory.getLogger(name)`; the check module's `jsMain` actual is a no-op. This unit has no production logging site; the seam is delivered and tested for the later units. · An `internal` copy of `Result.flatMap`, identical in body to `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ResultExtensions.kt:19-20`; its header names both other copies. The two legacy …",
              "hash": "55e09cca2e0e"
            }
          },
          {
            "text": "This unit has no production logging site; the seam is delivered and tested for the later units.",
            "ref": "plan.md#r6-the-logging-seam-and-the-helpers",
            "label": "§R6",
            "excerpt": {
              "ref": "plan.md#r6-the-logging-seam-and-the-helpers",
              "heading": "R6. The logging seam and the helpers",
              "paragraph": "`internal interface Log` with `debug`, `info` and `warn(message: String, vararg args: Any?)` taking `{}` placeholders, obtained through `internal expect fun logger(name: String): Log`. The `jvmMain` actual wraps slf4j's `LoggerFactory.getLogger(name)`; the check module's `jsMain` actual is a no-op. This unit has no production logging site; the seam is delivered and tested for the later units. · An `internal` copy of `Result.flatMap`, identical in body to `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ResultExtensions.kt:19-20`; its header names both other copies. The two legacy …",
              "hash": "55e09cca2e0e"
            }
          }
        ]
      }
    },
    {
      "id": "commoncheck-module",
      "group": "Code",
      "name": "webtools-http-commoncheck, the check-only js compile",
      "system": "js-commoncheck",
      "where": "webtools-http-commoncheck/ · settings.gradle.kts",
      "gap": {
        "kind": "risk",
        "summary": "With `jvm()` as the only target, a JVM API in `commonMain` compiles silently, so nothing keeps common code common. A target cannot be declared in the module and left unpublished, so the module cannot hold the second target itself.",
        "evidence": [
          "build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts:7 — a single `jvm()` target (baseline)",
          "../research/README.md § Build, publishing and the Java 11 floor — `java.io.File`, `java.time.Instant` and `System.currentTimeMillis()` compile in `commonMain` with one target (measured)"
        ],
        "source": "../research/README.md § Build, publishing and the Java 11 floor; ../architecture.md §2",
        "link": "../research/README.md#build-publishing-and-the-java-11-floor"
      },
      "options": [
        {
          "id": "check-only-module",
          "name": "An unpublished module compiling the sibling's `src/commonMain/kotlin` for `js` only, its test task disabled, with a `js` actual for every `expect` in its `jsMain` and no publishing plugin, part of `./gradlew build`",
          "verdict": "accepted",
          "why": "A JVM API in `commonMain` becomes a compile error and nothing new is published; it compiles only, so nothing downloads Node. `commonTest` is not covered: the common tests run on the JVM.",
          "source": "../architecture.md §6.8",
          "link": "../architecture.md#68-check-only-js-modules"
        },
        {
          "id": "published-js-target",
          "name": "A published `js` target instead of check-only modules",
          "verdict": "declined",
          "why": "It adds a coordinate and a klib that nothing consumes, Node and Yarn downloads for tests, and a published surface the requirements do not name.",
          "wins": "The day a non-JVM consumer exists: the check modules are then deleted and `js { nodejs() }` is added to the plugin.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "common-test-on-js",
          "name": "Running `commonTest` on `js` as well",
          "verdict": "declined",
          "why": "The check module compiles `commonMain` only, so nothing downloads Node.js; the common tests run on the JVM target.",
          "source": "plan.md §4",
          "link": "plan.md#4-non-goals"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "webtools-http-commoncheck/build.gradle.kts",
          "webtools-http-commoncheck/src/jsMain/"
        ],
        "tests": "`:webtools-http-commoncheck:check` green; the root and no-Node.js proofs and the leak canary (procedure P7, acceptance 2 and 7).",
        "verified": "Procedures P6 to P9 after S5, recorded in `final-implementation.md` §5 on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#p7--the-check-module-and-the-root",
        "statements": [
          {
            "text": "Its build file and `JsActuals.kt` are as decided; `compileKotlinJs` compiles the sibling's `commonMain` with the two actuals.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape; the mechanical fixes made on the way are in §5.",
              "hash": "be79bfaf86d5"
            }
          },
          {
            "text": "A `java.io` reference left in `commonMain` failed its `js` compile, as designed.",
            "state": "as-planned",
            "ref": "final-implementation.md#p7--the-check-module-and-the-root",
            "excerpt": {
              "ref": "final-implementation.md#p7--the-check-module-and-the-root",
              "heading": "P7 — the check module and the root",
              "paragraph": "`./gradlew :webtools-http-commoncheck:build --configuration-cache` twice → both `BUILD SUCCESSFUL`; the second prints `Reusing configuration cache.` The build ran `compileKotlinJs`, `jsJar`, `allMetadataJar`; `jsTest` and `allTests` are `NO-SOURCE`. · `ls kotlin-js-store` → \"No such file or directory\"; `git status --short` lists no `kotlin-js-store/`. · `./gradlew :webtools-http-commoncheck:build --dry-run` lists twelve `:webtools-http-commoncheck:` tasks and no `NodeJs`, `Yarn`, `Npm` or `PackageJson` task; no root task. · `./gradlew :check` → `BUILD FAILED`: \"Cannot locate tasks that match …",
              "hash": "6b435acd42f4"
            }
          },
          {
            "text": "`:webtools-http-commoncheck:check` passed and a second build reused the configuration cache; no Node.js, Yarn or npm task and no `kotlin-js-store/` appeared.",
            "state": "as-planned",
            "ref": "final-implementation.md#p7--the-check-module-and-the-root",
            "excerpt": {
              "ref": "final-implementation.md#p7--the-check-module-and-the-root",
              "heading": "P7 — the check module and the root",
              "paragraph": "`./gradlew :webtools-http-commoncheck:build --configuration-cache` twice → both `BUILD SUCCESSFUL`; the second prints `Reusing configuration cache.` The build ran `compileKotlinJs`, `jsJar`, `allMetadataJar`; `jsTest` and `allTests` are `NO-SOURCE`. · `ls kotlin-js-store` → \"No such file or directory\"; `git status --short` lists no `kotlin-js-store/`. · `./gradlew :webtools-http-commoncheck:build --dry-run` lists twelve `:webtools-http-commoncheck:` tasks and no `NodeJs`, `Yarn`, `Npm` or `PackageJson` task; no root task. · `./gradlew :check` → `BUILD FAILED`: \"Cannot locate tasks that match …",
              "hash": "6b435acd42f4"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "A JVM API in common code and an `expect` with no `js` actual become compile errors, and nothing new is published. With the plugin's `js()` the root project gains no plugin and no task, and nothing downloads Node.js or Yarn. A mistyped sibling path fails loudly, because the actuals would lose their `expect`s.",
        "steps": [
          {
            "id": "S2",
            "what": "Write `settings.gradle.kts`, both module build files, every declaration the gate touches, the value types other than `Url`, a `Url` shell with its final members, the `js` actuals and the permanent Level 1 consumer classes, then run the compile proof.",
            "files": [
              "settings.gradle.kts",
              "webtools-http/",
              "webtools-http-commoncheck/"
            ],
            "check": "Procedure P3, recorded in `final-implementation.md` §3; S3 does not start if it differs from the plan",
            "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
          },
          {
            "id": "S5",
            "what": "Generate and commit the ABI dump, then read the class files and the published metadata, and prove the root project and the three existing modules unchanged.",
            "files": [
              "webtools-http/api/webtools-http.api"
            ],
            "check": "Procedures P6 to P9",
            "link": "plan.md#92-procedures"
          }
        ],
        "tests": [
          "`:webtools-http-commoncheck:check` compiles `commonMain` for `js` (acceptance 2)",
          "The root project has no `check` task, and no Node.js, Yarn or `kotlin-js-store/` appears (acceptance 7)"
        ],
        "risks": [
          "IntelliJ's import of the module, a source directory outside it and actuals for a sibling's `expect`s, is untested; the Level 5 pass observes it",
          "The `jsMain { }` accessor was measured in a probe, not in this layout; the fallback is `getByName(\"jsMain\")`"
        ],
        "source": "plan.md R11, §6.7, §7.4",
        "link": "plan.md#74-webtools-http-commoncheck-new",
        "statements": [
          {
            "text": "`webtools-http-commoncheck` applies `webtools.kotlin-common-check` and adds the sibling's `commonMain` directory by a relative path.",
            "ref": "plan.md#r11-the-check-only-js-module",
            "label": "§R11",
            "excerpt": {
              "ref": "plan.md#r11-the-check-only-js-module",
              "heading": "R11. The check-only `js` module",
              "paragraph": "`webtools.kotlin-common-check` is a new precompiled script plugin: it applies `org.jetbrains.kotlin.multiplatform`, declares `repositories { mavenCentral() }`, calls `js()` with no environment and wires `check` to `compileKotlinJs`. Its header comment says why there is no environment, what the accepted cost is and what the fallback is. `build-logic/build.gradle.kts` does not change. · `webtools-http-commoncheck/build.gradle.kts` applies the plugin, adds the sibling's `commonMain` directory as a source directory, sets the opt-in on `commonMain` and `jsMain`, and declares …",
              "hash": "21b0a28ebfe7"
            }
          },
          {
            "text": "It opts in on `commonMain` and `jsMain`, declares `kotlinx-coroutines-core` and holds the two `js` actuals.",
            "ref": "plan.md#r11-the-check-only-js-module",
            "label": "§R11",
            "excerpt": {
              "ref": "plan.md#r11-the-check-only-js-module",
              "heading": "R11. The check-only `js` module",
              "paragraph": "`webtools.kotlin-common-check` is a new precompiled script plugin: it applies `org.jetbrains.kotlin.multiplatform`, declares `repositories { mavenCentral() }`, calls `js()` with no environment and wires `check` to `compileKotlinJs`. Its header comment says why there is no environment, what the accepted cost is and what the fallback is. `build-logic/build.gradle.kts` does not change. · `webtools-http-commoncheck/build.gradle.kts` applies the plugin, adds the sibling's `commonMain` directory as a source directory, sets the opt-in on `commonMain` and `jsMain`, and declares …",
              "hash": "21b0a28ebfe7"
            }
          },
          {
            "text": "It has no `explicitApi()`, publishing or version; `./gradlew build` and `check` compile it.",
            "ref": "plan.md#67-the-check-only-js-module",
            "excerpt": {
              "ref": "plan.md#67-the-check-only-js-module",
              "heading": "6.7 The check-only `js` module",
              "paragraph": "`webtools-http-commoncheck` applies `webtools.kotlin-common-check`, which gives it a `js` target with no environment and a `check` that compiles. It takes the sibling's source as a directory (a relative path: no `project(...)` access, so the configuration cache needs nothing from another project) and adds the two `actual`s. Consequences, all measured in the research:",
              "hash": "86e280aaea92"
            }
          }
        ]
      }
    },
    {
      "id": "common-check-plugin",
      "group": "Code",
      "name": "The webtools.kotlin-common-check convention plugin",
      "system": "gradle-build",
      "where": "build-logic/src/main/kotlin/",
      "gap": {
        "kind": "missing",
        "summary": "`build-logic` holds the published-library, Kotlin/JVM and multiplatform conventions only. There is no convention for an unpublished check module; #55's architecture lands it with the first one, in this unit.",
        "evidence": [
          "build-logic/src/main/kotlin/ — `webtools.published-library`, `webtools.kotlin-jvm-library` and `webtools.kotlin-multiplatform-library`, and no common-check plugin (baseline)",
          "../architecture.md §11 unit 3 — the plugin is added to `build-logic` with `webtools-http-commoncheck`"
        ],
        "source": "../architecture.md §11 unit 3",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "common-check-convention",
          "name": "A precompiled plugin applying the multiplatform plugin with only a `js` target whose test task is disabled, and no publishing plugin",
          "verdict": "accepted",
          "why": "`build-logic` is the one place where every module's compile target, test layout and publishing shape are decided, and the feeds check module takes the same plugin later.",
          "source": "../architecture.md §6.8, §6.1",
          "link": "../architecture.md#68-check-only-js-modules"
        },
        {
          "id": "nodejs-test-disabled",
          "name": "`js { nodejs { testTask { enabled = false } } }`, the form #55's architecture gives (§3, §6.1, §6.8)",
          "verdict": "declined",
          "why": "Disabling a task does not skip the tasks it depends on: `build` still downloads Node.js and Yarn, runs the npm install and writes `kotlin-js-store/`, and the root project gains Gradle's base plugin and about 18 tasks. The plugin calls `js()` with no environment instead.",
          "source": "plan.md §2, §7.2; research/common-check-plugin.md F2–F5",
          "link": "plan.md#72-build-logic-one-new-plugin-three-changed-or-new-test-files"
        },
        {
          "id": "nodejs-no-dependencies",
          "name": "`js { nodejs { testTask { enabled = false; setDependsOn(emptyList<Any>()) } } }`",
          "verdict": "declined",
          "why": "It downloads nothing, but it still gives the root project the base plugin and about 18 tasks, its own `build`, `check`, `assemble` and `clean` among them. It is kept as the fallback in the plugin's header.",
          "wins": "The day a Kotlin Gradle plugin release turns the missing environment into an error, which fails the plugin's TestKit `build` test.",
          "source": "plan.md §6.7, §7.2",
          "link": "plan.md#67-the-check-only-js-module"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "files": [
          "build-logic/src/main/kotlin/webtools.kotlin-common-check.gradle.kts",
          "build-logic/src/test/kotlin/"
        ],
        "tests": "L1 gating `BuildLogicPluginsGatingTest` lists four plugin ids; L3 integration `CommonCheckPluginIntegrationTest` 7/7, on the new `SampleProject.commonCheck` factory; L4c non-functional `CommonCheckConfigurationCacheReuseNonFunctionalTest` 1/1. The full `build-logic` suites passed: integration 46, e2e 5, non-functional 3.",
        "verified": "Procedure P2, recorded in `final-implementation.md` §5 on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#p2--the-plugin-s1",
        "statements": [
          {
            "text": "The plugin was built as decided in S1: `CommonCheckPluginIntegrationTest`'s seven tests and the configuration-cache test passed.",
            "state": "as-planned",
            "ref": "final-implementation.md#p2--the-plugin-s1",
            "excerpt": {
              "ref": "final-implementation.md#p2--the-plugin-s1",
              "heading": "P2 — the plugin (S1)",
              "paragraph": "`./gradlew :build-logic:test` (with `webtools.kotlin-common-check` in the gating list) → `BUILD SUCCESSFUL in 35s`. · `./gradlew :build-logic:integrationTest --tests \"*CommonCheckPluginIntegrationTest*\"` → `BUILD SUCCESSFUL in 28s`; 7 tests, 0 failures. · `./gradlew :build-logic:nonfunctionalTest --tests \"*CommonCheckConfigurationCacheReuseNonFunctionalTest*\"` → `BUILD SUCCESSFUL in 14s`; 1 test, 0 failures. · Full suites, one after another: `./gradlew :build-logic:integrationTest` → `BUILD SUCCESSFUL in 2m 6s`, 46 tests; `./gradlew :build-logic:e2eTest` → `BUILD SUCCESSFUL in 58s`, 5 tests; …",
              "hash": "879bf0347d29"
            }
          },
          {
            "text": "The plugin's header gives the reason for no environment, the accepted warning and the fallback.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape; the mechanical fixes made on the way are in §5.",
              "hash": "be79bfaf86d5"
            }
          },
          {
            "text": "`build-logic/build.gradle.kts` did not change; `IncludedBuildEndToEndTest`'s KDoc gained one sentence: the plugin is covered at Level 3, not in the composite.",
            "state": "amended",
            "ref": "final-implementation.md#2-deviations-from-the-plan",
            "excerpt": {
              "ref": "final-implementation.md#2-deviations-from-the-plan",
              "heading": "2. Deviations from the plan",
              "paragraph": "Three. None changes a rule of the design, a signature, a message or a name. Plan Amendment 1 (the media-type test row) and Amendment 2 (two test-scope positions in §9) are part of the plan and were followed.",
              "hash": "488f7ebe9f8d"
            }
          }
        ],
        "changes": [
          {
            "what": "One sentence more in `IncludedBuildEndToEndTest`'s KDoc, a file plan §7.2 does not list: the `webtools.kotlin-common-check` plugin is not part of the composite and is covered at Level 3 by `CommonCheckPluginIntegrationTest`.",
            "why": "It states in the test the position plan Amendment 2 records.",
            "when": "qa pass 1",
            "ref": "final-implementation.md#2-deviations-from-the-plan"
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "This departs from #55's architecture, which gives `js { nodejs { testTask { enabled = false } } }`: the research measured that form downloading Node.js and Yarn and giving the root project about 18 tasks. Without an environment the target compiles to a klib and leaves the root alone, so the root still has no `check` and unit 4's open point on where the publication check attaches stands. The cost is one \"JS Environment Not Selected\" warning per configuration-cache miss.",
        "steps": [
          {
            "id": "S1",
            "what": "Add the `webtools.kotlin-common-check` plugin, the `SampleProject.commonCheck` factory, the two TestKit classes and the fourth id in `BuildLogicPluginsGatingTest`.",
            "files": [
              "build-logic/src/main/kotlin/webtools.kotlin-common-check.gradle.kts",
              "build-logic/src/test/kotlin/"
            ],
            "check": "Procedure P2: the plugin's TestKit tests pass",
            "link": "plan.md#72-build-logic-one-new-plugin-three-changed-or-new-test-files"
          }
        ],
        "tests": [
          "Level 1 — `BuildLogicPluginsGatingTest` lists the fourth plugin",
          "Level 3 — `CommonCheckPluginIntegrationTest`, seven TestKit tests",
          "Level 4c — the second identical `build` reuses the configuration cache"
        ],
        "risks": [
          "A Kotlin Gradle plugin release that makes the missing environment an error fails the plugin's TestKit `build` test; the fallback then gives the root project base-plugin tasks"
        ],
        "source": "plan.md R11, §6.7, §7.2, §13",
        "link": "plan.md#72-build-logic-one-new-plugin-three-changed-or-new-test-files",
        "statements": [
          {
            "text": "`webtools.kotlin-common-check` is a new precompiled script plugin: it applies the multiplatform plugin, declares `mavenCentral()`, calls `js()` with no environment and wires `check` to `compileKotlinJs`.",
            "ref": "plan.md#r11-the-check-only-js-module",
            "label": "§R11",
            "excerpt": {
              "ref": "plan.md#r11-the-check-only-js-module",
              "heading": "R11. The check-only `js` module",
              "paragraph": "`webtools.kotlin-common-check` is a new precompiled script plugin: it applies `org.jetbrains.kotlin.multiplatform`, declares `repositories { mavenCentral() }`, calls `js()` with no environment and wires `check` to `compileKotlinJs`. Its header comment says why there is no environment, what the accepted cost is and what the fallback is. `build-logic/build.gradle.kts` does not change. · `webtools-http-commoncheck/build.gradle.kts` applies the plugin, adds the sibling's `commonMain` directory as a source directory, sets the opt-in on `commonMain` and `jsMain`, and declares …",
              "hash": "21b0a28ebfe7"
            }
          },
          {
            "text": "Its header says why there is no environment, the accepted cost and the fallback.",
            "ref": "plan.md#r11-the-check-only-js-module",
            "label": "§R11",
            "excerpt": {
              "ref": "plan.md#r11-the-check-only-js-module",
              "heading": "R11. The check-only `js` module",
              "paragraph": "`webtools.kotlin-common-check` is a new precompiled script plugin: it applies `org.jetbrains.kotlin.multiplatform`, declares `repositories { mavenCentral() }`, calls `js()` with no environment and wires `check` to `compileKotlinJs`. Its header comment says why there is no environment, what the accepted cost is and what the fallback is. `build-logic/build.gradle.kts` does not change. · `webtools-http-commoncheck/build.gradle.kts` applies the plugin, adds the sibling's `commonMain` directory as a source directory, sets the opt-in on `commonMain` and `jsMain`, and declares …",
              "hash": "21b0a28ebfe7"
            }
          },
          {
            "text": "`build-logic/build.gradle.kts` does not change; the plugin's TestKit checks are added beside it.",
            "ref": "plan.md#72-build-logic-one-new-plugin-three-changed-or-new-test-files",
            "excerpt": {
              "ref": "plan.md#72-build-logic-one-new-plugin-three-changed-or-new-test-files",
              "heading": "7.2 `build-logic` (one new plugin, three changed or new test files)",
              "paragraph": "`build-logic/src/main/kotlin/webtools.kotlin-common-check.gradle.kts` (new)",
              "hash": "ee0077520d5d"
            }
          }
        ]
      }
    },
    {
      "id": "compile-proof",
      "group": "Tests",
      "name": "The first compile proof of the opt-in gate",
      "system": "webtools-http-core",
      "where": "common/, jvm/",
      "gap": {
        "kind": "risk",
        "summary": "The public surface has never been compiled: the opt-in behaviour rests on compiler test data and one JVM-only measurement. The marker, a gated interface, a constructor default that mentions it and a consumer without the opt-in have not been compiled together in `commonMain` and `jvmMain`.",
        "evidence": [
          "../research/README.md § Known gaps — the public surface was not compiled; four things need a compile check before the first unit lands",
          "../architecture.md §11 unit 3 — its plan starts with that compile proof"
        ],
        "source": "../research/README.md § Known gaps",
        "link": "../research/README.md#known-gaps"
      },
      "options": [
        {
          "id": "compile-first",
          "name": "Compile the four together in `commonMain` and `jvmMain` before anything else in the plan",
          "verdict": "accepted",
          "why": "This unit is where they meet for the first time, and ABI dumps do not record `@SubclassOptInRequired`, so `apiCheck` alone would not notice a dropped gate.",
          "source": "../architecture.md §6.2, §9",
          "link": "../architecture.md#9-extension--stability"
        },
        {
          "id": "permanent-gate-check",
          "name": "A permanent check of the gate: the research's `verifyTestsAreConsumers` guard task, or a TestKit compile-fail snippet in `build-logic`",
          "verdict": "declined",
          "why": "The eight negative cases and their texts are recorded once; a TestKit snippet would cost a nested build per `build-logic` integration run. It is left as a follow-up.",
          "source": "plan.md R7, §4, §13",
          "link": "plan.md#13-sequencing-and-follow-ups"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "webtools-http/src/commonTest/",
          "webtools-http/src/jvmTest/"
        ],
        "tests": "L1 gating `gatingTest` 5, 0 failures, inside the 130 `jvmTest` tests of S5's `check`; the eight negative cases were run once in S2 and deleted.",
        "verified": "Procedure P3 for S2 (`final-implementation.md` §3) and procedures P6 and P8 after S5 (§5), recorded on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#3-the-compile-proof",
        "statements": [
          {
            "text": "Procedure P3 ran after S2 and before S3, and every step gave the predicted result.",
            "state": "as-planned",
            "ref": "final-implementation.md#step-5--verdict",
            "excerpt": {
              "ref": "final-implementation.md#step-5--verdict",
              "heading": "Step 5 — verdict",
              "paragraph": "The proof's every result equals the plan's expectation (§6.2, §9.2 P3). S3 may begin.",
              "hash": "05086b66e46b"
            }
          },
          {
            "text": "The canary failed with two errors per task, carrying the marker's message, and compiled again once restored.",
            "state": "as-planned",
            "ref": "final-implementation.md#step-2--the-wiring-canary",
            "excerpt": {
              "ref": "final-implementation.md#step-2--the-wiring-canary",
              "heading": "Step 2 — the wiring canary",
              "paragraph": "Three throwaway top-level implementers were added, exactly as the plan writes them: `ProofClock.kt` (`commonMain`, `: WallClock`), `ProofTransport.kt` (`jvmMain`, `: HttpTransport`) and `ProofJsClock.kt` (the check module's `jsMain`, `: WallClock`).",
              "hash": "b12290b81124"
            }
          },
          {
            "text": "The consumer cases compiled; the eight negative cases gave exactly eight errors in `commonTest` and in `jvmTest`, and none with `@OptIn`.",
            "state": "as-planned",
            "ref": "final-implementation.md#step-4--the-eight-negative-cases",
            "excerpt": {
              "ref": "final-implementation.md#step-4--the-eight-negative-cases",
              "heading": "Step 4 — the eight negative cases",
              "paragraph": "`NegativeCases.kt` (the eight declarations of plan §9.2 P3.4, `import com.spartanlabs.webtools.http.*`) was placed in `commonTest` under `com.spartanlabs.testing.gating.webtools.http`, then in `jvmTest`.",
              "hash": "ed5ba8ac8e89"
            }
          },
          {
            "text": "`javap` and the dump read as planned: the gate on the five types only, and none in the dump.",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
            "excerpt": {
              "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
              "heading": "P6 — the dump, the class files, the published shape (S5)",
              "paragraph": "`./gradlew :webtools-http:apiDump` → `BUILD SUCCESSFUL`; `webtools-http/api/webtools-http.api` has 294 lines, written CRLF by the tool on this host (git stores it LF; `apiCheck` compares line by line). `./gradlew :webtools-http:apiCheck` → `BUILD SUCCESSFUL`. Read against plan §7.8, every point holds: `public abstract fun execute-gIAlu-s (…HttpRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`, a public `HttpTransport$DefaultImpls` with `public static fun close (…HttpTransport;)V`, `public fun close ()V`; `WallClock$System` final with `INSTANCE`, `nowEpochMillis ()J` and `toString`; …",
              "hash": "4f835e8ce361"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Every implementer this unit commits is nested inside a gated declaration and compiles without the wiring, so a proof over the committed code alone would pass vacuously; the canary is what exercises the opt-in. A result that differs from the plan stops S3 until the cause is understood.",
        "steps": [
          {
            "id": "S2",
            "what": "Write `settings.gradle.kts`, both module build files, every declaration the gate touches, the value types other than `Url`, a `Url` shell with its final members, the `js` actuals and the permanent Level 1 consumer classes, then run the compile proof.",
            "files": [
              "settings.gradle.kts",
              "webtools-http/",
              "webtools-http-commoncheck/"
            ],
            "check": "Procedure P3, recorded in `final-implementation.md` §3; S3 does not start if it differs from the plan",
            "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
          }
        ],
        "tests": [
          "Level 1 — the permanent consumer classes",
          "The eight negative cases, run once"
        ],
        "risks": [
          "The research probes used a simplified convention plugin; if the real plugin changes an outcome, the proof shows it before anything else is built"
        ],
        "source": "plan.md R7, §6.2, §9.2 P3",
        "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation",
        "statements": [
          {
            "text": "The implementation opens with the compile proof in the real module, after the S2 declarations exist and before S3.",
            "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
            "label": "§R7",
            "excerpt": {
              "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
              "heading": "R7. The opt-in wiring and the opening compile proof",
              "paragraph": "The module-wide opt-in is a language setting of the main source sets, in each module's own build file, not in a convention plugin: `webtools-http` sets it on `commonMain` and `jvmMain`; `webtools-http-commoncheck` on `commonMain` and `jsMain`. The build files say, in a comment, that `kotlin { compilerOptions { optIn.add(...) } }`, the target-level option and `sourceSets.all { }` reach the test compilations too and must not be used. · `commonTest` and `jvmTest` compile without the opt-in and stay a permanent consumer: they mention the gated types, use `WallClock.System` as a parameter default …",
              "hash": "5ad2577da247"
            }
          },
          {
            "text": "A wiring canary of three throwaway implementers compiles with the opt-in lines and fails without them.",
            "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
            "label": "§R7",
            "excerpt": {
              "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
              "heading": "R7. The opt-in wiring and the opening compile proof",
              "paragraph": "The module-wide opt-in is a language setting of the main source sets, in each module's own build file, not in a convention plugin: `webtools-http` sets it on `commonMain` and `jvmMain`; `webtools-http-commoncheck` on `commonMain` and `jsMain`. The build files say, in a comment, that `kotlin { compilerOptions { optIn.add(...) } }`, the target-level option and `sourceSets.all { }` reach the test compilations too and must not be used. · `commonTest` and `jvmTest` compile without the opt-in and stay a permanent consumer: they mention the gated types, use `WallClock.System` as a parameter default …",
              "hash": "5ad2577da247"
            }
          },
          {
            "text": "The positive consumer cases compile; the eight negative cases fail with exactly eight consumer errors and compile with `@OptIn`.",
            "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
            "label": "§R7",
            "excerpt": {
              "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
              "heading": "R7. The opt-in wiring and the opening compile proof",
              "paragraph": "The module-wide opt-in is a language setting of the main source sets, in each module's own build file, not in a convention plugin: `webtools-http` sets it on `commonMain` and `jvmMain`; `webtools-http-commoncheck` on `commonMain` and `jsMain`. The build files say, in a comment, that `kotlin { compilerOptions { optIn.add(...) } }`, the target-level option and `sourceSets.all { }` reach the test compilations too and must not be used. · `commonTest` and `jvmTest` compile without the opt-in and stay a permanent consumer: they mention the gated types, use `WallClock.System` as a parameter default …",
              "hash": "5ad2577da247"
            }
          },
          {
            "text": "The class files and the dump are read, and the output goes to `final-implementation.md` §3.",
            "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
            "label": "§R7",
            "excerpt": {
              "ref": "plan.md#r7-the-opt-in-wiring-and-the-opening-compile-proof",
              "heading": "R7. The opt-in wiring and the opening compile proof",
              "paragraph": "The module-wide opt-in is a language setting of the main source sets, in each module's own build file, not in a convention plugin: `webtools-http` sets it on `commonMain` and `jvmMain`; `webtools-http-commoncheck` on `commonMain` and `jsMain`. The build files say, in a comment, that `kotlin { compilerOptions { optIn.add(...) } }`, the target-level option and `sourceSets.all { }` reach the test compilations too and must not be used. · `commonTest` and `jvmTest` compile without the opt-in and stay a permanent consumer: they mention the gated types, use `WallClock.System` as a parameter default …",
              "hash": "5ad2577da247"
            }
          }
        ]
      }
    },
    {
      "id": "common-tests",
      "group": "Tests",
      "name": "Parsing and validation tests, and the Url-against-HttpUrl differential",
      "system": "webtools-http-core",
      "where": "webtools-http/src/commonTest · webtools-http/src/jvmTest",
      "gap": {
        "kind": "missing",
        "summary": "No test covers parsing, validation or query encoding of a library-owned request type, and none compares the stack's `Url` with OkHttp's `HttpUrl`, into which the transport converts it.",
        "evidence": [
          "../architecture.md §11 unit 3 — Level 4a tests for parsing, validation and query encoding in `commonTest`, the differential in `jvmTest`"
        ],
        "source": "../architecture.md §11 unit 3",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "deterministic-and-differential",
          "name": "Level 4a tests in `commonTest` for parsing, validation and query encoding, and a differential of `Url` against `HttpUrl` in `jvmTest`",
          "verdict": "accepted",
          "why": "`java.net.URI` parses hosts differently from OkHttp; converting the port's own `Url` to `HttpUrl` inside the transport is safe only while a differential test pins their agreement.",
          "source": "../architecture.md §6.9",
          "link": "../architecture.md#69-common-code-mechanism-per-type"
        },
        {
          "id": "mockk-fakes",
          "name": "MockK for the component tests",
          "verdict": "declined",
          "why": "This unit has no external call to fake; the logging actual is tested against a real logback appender, and the repository's Level 2 tests use hand-written fakes.",
          "source": "plan.md §2, §9",
          "link": "plan.md#9-test-plan"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "webtools-http/src/commonTest/",
          "webtools-http/src/jvmTest/"
        ],
        "tests": "L1 gating 5 tests in 2 classes; L2 component 68 in 14; L4a deterministic 48 in 16, three of them the differentials; L4c non-functional 9 in 4; all green. `Url` differential: the corpus and 200,000 generated inputs, 0 disagreements and every coverage floor met; 2,000,000 inputs on two more seeds, 0 disagreements.",
        "verified": "Procedures P5 after S3 and after S4, and P4, recorded in `final-implementation.md` §5 on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#p4--the-url-differential-s4",
        "statements": [
          {
            "text": "`UrlCorpus` and `UrlInputGenerator` drive the `Url` tables: 48 Level 4a tests in 16 classes pass.",
            "state": "as-planned",
            "ref": "final-implementation.md#p5--the-fast-loop-after-s4",
            "excerpt": {
              "ref": "final-implementation.md#p5--the-fast-loop-after-s4",
              "heading": "P5 — the fast loop after S4",
              "paragraph": "`./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` → `BUILD SUCCESSFUL in 10s`, 0 failures: · `gatingTest`: 5 tests in 2 classes. · `componentTest`: 68 tests in 14 classes (11 `commonTest`, 3 `jvmTest`: `Slf4jLogComponentTest`, `CatchNonFatalComponentTest`, `EpochMillisJvmComponentTest`). · `deterministicTest`: 48 tests in 16 classes (13 `commonTest`, 3 `jvmTest` differentials). · `nonfunctionalTest`: 9 tests in 4 classes.",
              "hash": "ed90df43bfcb"
            }
          },
          {
            "text": "The sweeps pass, and the media-type table follows plan Amendment 1's rows.",
            "state": "as-planned",
            "ref": "final-implementation.md#p5--the-fast-loop-after-s4",
            "excerpt": {
              "ref": "final-implementation.md#p5--the-fast-loop-after-s4",
              "heading": "P5 — the fast loop after S4",
              "paragraph": "`./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest :webtools-http:nonfunctionalTest` → `BUILD SUCCESSFUL in 10s`, 0 failures: · `gatingTest`: 5 tests in 2 classes. · `componentTest`: 68 tests in 14 classes (11 `commonTest`, 3 `jvmTest`: `Slf4jLogComponentTest`, `CatchNonFatalComponentTest`, `EpochMillisJvmComponentTest`). · `deterministicTest`: 48 tests in 16 classes (13 `commonTest`, 3 `jvmTest` differentials). · `nonfunctionalTest`: 9 tests in 4 classes.",
              "hash": "ed90df43bfcb"
            }
          },
          {
            "text": "The `Url` differential found no disagreement on 200,000 inputs, nor on 2,000,000 with two more seeds, and caught each of four deliberate mutants.",
            "state": "as-planned",
            "ref": "final-implementation.md#p4--the-url-differential-s4",
            "excerpt": {
              "ref": "final-implementation.md#p4--the-url-differential-s4",
              "heading": "P4 — the `Url` differential (S4)",
              "paragraph": "Default size, inside `deterministicTest` above (seed 7, 200,000 generated inputs plus the 203 corpus inputs): 0 disagreements. Coverage: `inputs=200203 accepted=61564` (30.7%), `httpUrlThrew=498`, accepted inputs with `[` 32,102, with a dot segment 23,523, with `?` 27,161, with a percent escape 33,988, with non-ASCII 12,619; `rejectedByKind={Empty=343, Backslash=20248, WhitespaceOrControl=35640, NotUnicode=16913, Scheme=50672, UserInfo=1108, HostEncoding=3482, HostIdn=345, Host=8178, Port=1710}`. Running time of the differential test method: about 3.1 s.",
              "hash": "09adeeb289ed"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The transport converts `Url` to `HttpUrl` through `toString()`, which is safe only while the differential pins their agreement; a later OkHttp that changes a canonicalisation shows up as a differential failure. Test sources never contain `@OptIn`, so they stay a consumer.",
        "steps": [
          {
            "id": "S3",
            "what": "Replace the `Url` shell with the parser, and add the encoders, the corpus, the generator and the `Url` tests.",
            "files": [
              "webtools-http/src/commonMain/",
              "webtools-http/src/commonTest/"
            ],
            "check": "Procedure P5, the `commonTest` levels",
            "link": "plan.md#65-url"
          },
          {
            "id": "S4",
            "what": "Add the remaining Level 2, 4a and 4c tests, the three differentials and `logback-test.xml`.",
            "files": [
              "webtools-http/src/commonTest/",
              "webtools-http/src/jvmTest/"
            ],
            "check": "Procedures P4 and P5",
            "link": "plan.md#9-test-plan"
          }
        ],
        "tests": [
          "Level 1 — two gating classes",
          "Level 2 — fourteen component classes",
          "Level 4a — thirteen tables in `commonTest` and three differentials in `jvmTest`",
          "Level 4c — four in `webtools-http`, one in `build-logic`",
          "Level 5 — the manual pass, procedure P10"
        ],
        "risks": [
          "Only OkHttp 5.5.0 was measured; the files `Url` mirrors are identical at 5.5.1"
        ],
        "source": "plan.md §9, §9.1",
        "link": "plan.md#91-the-url-corpus-generator-and-differential",
        "statements": [
          {
            "text": "Level 4a tables in `commonTest` run over a `Url` corpus and generator: accepted, rejected, confusing, boundary, IPv6, printing and encoder cases.",
            "ref": "plan.md#91-the-url-corpus-generator-and-differential",
            "excerpt": {
              "ref": "plan.md#91-the-url-corpus-generator-and-differential",
              "heading": "9.1 The `Url` corpus, generator and differential",
              "paragraph": "Placement. `CT support/UrlCorpus.kt` and `CT support/UrlInputGenerator.kt` (both `object`s in `commonTest`, so the common tables can use them without OkHttp, and `jvmTest` sees them).",
              "hash": "81a937a11899"
            }
          },
          {
            "text": "Sweeps cover header names and values over U+0000..U+FFFF, method tokens, statuses and media types.",
            "ref": "plan.md#level-4a--deterministic",
            "excerpt": {
              "ref": "plan.md#level-4a--deterministic",
              "heading": "Level 4a — deterministic",
              "paragraph": "`commonTest` (pure input-to-output tables; the corpus is §9.1):",
              "hash": "bccd20026402"
            }
          },
          {
            "text": "Three `jvmTest` differentials take OkHttp as the oracle: `Url` against `HttpUrl` on the corpus and 200,000 generated inputs, the encoders against `HttpUrl.Builder`, and header validity against `Request.Builder`.",
            "ref": "plan.md#91-the-url-corpus-generator-and-differential",
            "excerpt": {
              "ref": "plan.md#91-the-url-corpus-generator-and-differential",
              "heading": "9.1 The `Url` corpus, generator and differential",
              "paragraph": "Placement. `CT support/UrlCorpus.kt` and `CT support/UrlInputGenerator.kt` (both `object`s in `commonTest`, so the common tables can use them without OkHttp, and `jvmTest` sees them).",
              "hash": "81a937a11899"
            }
          }
        ]
      }
    },
    {
      "id": "readme",
      "group": "Documents",
      "name": "The README's artifact row and install line",
      "system": "webtools-http-core",
      "where": "README.md",
      "gap": {
        "kind": "missing",
        "summary": "The README's artifact table and install block name the three existing artifacts only.",
        "evidence": [
          "README.md:6-10 — the artifact table (baseline)",
          "README.md:20-27 — the install block (baseline)"
        ],
        "source": "../architecture.md §11 unit 3",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "row-and-install-line",
          "name": "Add the `webtools-http` row to the artifact table and its install line; the HTTP-stack sections wait for the documentation unit",
          "verdict": "accepted",
          "why": "The module lands on `master` in this unit. The evergreen architecture document and the README's HTTP-stack sections are written once the stack has landed, in unit 11 (`http-docs`).",
          "source": "../architecture.md §11 units 3 and 11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "amended",
        "files": [
          "README.md"
        ],
        "tests": "None: a documentation change, reviewed by `git diff` as plan §8 sets out.",
        "verified": "`git diff --stat README.md` review after S6 and S7 (`final-implementation.md` §5, S6), recorded on 2026-10-25; acceptance 8 met (§4); committed through PR #82.",
        "link": "final-implementation.md#s6--the-readme",
        "changes": [
          {
            "what": "One README edit beyond plan §8: the `verifyJavaFloor` command comment now reads \"the Java 11 floor of every published module's jars\".",
            "why": "The check module has no JVM jar, so \"every module's jars\" was slightly loose; tightened on the coordinator's instruction. Line 60's \"three modules\" stays as correct history of the retired combined artifact.",
            "when": "S7",
            "ref": "final-implementation.md#2-deviations-from-the-plan"
          }
        ],
        "statements": [
          {
            "text": "The nine edits are in, and one more: the `verifyJavaFloor` comment now names \"every published module's jars\"; 30 insertions and 15 deletions.",
            "state": "amended",
            "ref": "final-implementation.md#2-deviations-from-the-plan",
            "excerpt": {
              "ref": "final-implementation.md#2-deviations-from-the-plan",
              "heading": "2. Deviations from the plan",
              "paragraph": "Three. None changes a rule of the design, a signature, a message or a name. Plan Amendment 1 (the media-type test row) and Amendment 2 (two test-scope positions in §9) are part of the plan and were followed.",
              "hash": "488f7ebe9f8d"
            }
          },
          {
            "text": "The opening sentence no longer counts three artifacts; the artifact row and the install line say not yet released.",
            "state": "as-planned",
            "ref": "final-implementation.md#s6--the-readme",
            "excerpt": {
              "ref": "final-implementation.md#s6--the-readme",
              "heading": "S6 — the README",
              "paragraph": "The nine edits of plan §8, in order: the \"three independent\" sentence; the `webtools-http` artifact row; the install line with its \"not yet on Maven Central\" comment; the Multiplatform/Maven/`kotlin-stdlib` 2.2.21 paragraph after \"Requires JDK 11 or newer\"; the module list in \"Build & test\"; the `:webtools-http-commoncheck:build` command line and the `*-commoncheck` sentence under the block; the test-layout paragraph; \"Every JVM module\" and \"every library module\"; \"four convention plugins\", \"(used by `webtools-http`)\" and the `webtools.kotlin-common-check` row. `git diff --stat README.md` → …",
              "hash": "625545375a49"
            }
          },
          {
            "text": "The `-jvm` coordinate and `kotlin-stdlib` 2.2.21 paragraph follows \"Requires JDK 11 or newer\".",
            "state": "as-planned",
            "ref": "final-implementation.md#s6--the-readme",
            "excerpt": {
              "ref": "final-implementation.md#s6--the-readme",
              "heading": "S6 — the README",
              "paragraph": "The nine edits of plan §8, in order: the \"three independent\" sentence; the `webtools-http` artifact row; the install line with its \"not yet on Maven Central\" comment; the Multiplatform/Maven/`kotlin-stdlib` 2.2.21 paragraph after \"Requires JDK 11 or newer\"; the module list in \"Build & test\"; the `:webtools-http-commoncheck:build` command line and the `*-commoncheck` sentence under the block; the test-layout paragraph; \"Every JVM module\" and \"every library module\"; \"four convention plugins\", \"(used by `webtools-http`)\" and the `webtools.kotlin-common-check` row. `git diff --stat README.md` → …",
              "hash": "625545375a49"
            }
          },
          {
            "text": "Build & test names both modules, the `commoncheck` build line, the test layout and the four-plugin table.",
            "state": "as-planned",
            "ref": "final-implementation.md#s6--the-readme",
            "excerpt": {
              "ref": "final-implementation.md#s6--the-readme",
              "heading": "S6 — the README",
              "paragraph": "The nine edits of plan §8, in order: the \"three independent\" sentence; the `webtools-http` artifact row; the install line with its \"not yet on Maven Central\" comment; the Multiplatform/Maven/`kotlin-stdlib` 2.2.21 paragraph after \"Requires JDK 11 or newer\"; the module list in \"Build & test\"; the `:webtools-http-commoncheck:build` command line and the `*-commoncheck` sentence under the block; the test-layout paragraph; \"Every JVM module\" and \"every library module\"; \"four convention plugins\", \"(used by `webtools-http`)\" and the `webtools.kotlin-common-check` row. `git diff --stat README.md` → …",
              "hash": "625545375a49"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "Each edit repairs a sentence the module makes false. The `### webtools-http` Components table waits for the later units and unit 11.",
        "steps": [
          {
            "id": "S6",
            "what": "Edit the README where the module makes a sentence false.",
            "files": [
              "README.md"
            ],
            "check": "`git diff --stat README.md` lists only the intended lines",
            "link": "plan.md#8-documentation-impact"
          }
        ],
        "tests": [],
        "risks": [
          "`README.md` is CRLF in the working tree and LF in the index on this host: the edit keeps its line endings"
        ],
        "source": "plan.md R12, §8",
        "link": "plan.md#8-documentation-impact",
        "statements": [
          {
            "text": "The README changes in the same change, in nine edits.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings.",
              "hash": "1b522fee71b2"
            }
          },
          {
            "text": "The opening line no longer counts three artifacts; a `webtools-http` row and its install line are both marked not yet released.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings.",
              "hash": "1b522fee71b2"
            }
          },
          {
            "text": "A paragraph gives the `-jvm` Maven coordinate and the `kotlin-stdlib` 2.2.21 requirement.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings.",
              "hash": "1b522fee71b2"
            }
          },
          {
            "text": "In Build & test: the module list, the multiplatform test layout, a sentence on the `*-commoncheck` modules and a plugin table of four.",
            "ref": "plan.md#8-documentation-impact",
            "excerpt": {
              "ref": "plan.md#8-documentation-impact",
              "heading": "8. Documentation impact",
              "paragraph": "Rings.",
              "hash": "1b522fee71b2"
            }
          }
        ]
      }
    },
    {
      "id": "release",
      "group": "Release",
      "name": "The module's version",
      "system": "webtools-http-core",
      "where": "webtools-http/build.gradle.kts",
      "gap": {
        "kind": "missing",
        "summary": "A new module takes the root default version, and nothing of the stack can be released until its last unit lands.",
        "evidence": [
          "build.gradle.kts:12 — the shared default `version = \"1.0.0\"` (baseline)",
          "../architecture.md §11 — `webtools-http` and `webtools-feeds` stay unreleased at 1.0.0 until the stack is complete"
        ],
        "source": "../architecture.md §11",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "new-unreleased",
          "name": "`webtools-http` 1.0.0, new and unreleased until the stack is complete; no existing module's version moves",
          "verdict": "accepted",
          "why": "The units that build the stack move no version of their own, and the three existing modules moved only in the Java floor unit.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "files": [
          "webtools-http/build.gradle.kts"
        ],
        "tests": "",
        "verified": "Recorded in `final-implementation.md` on 2026-10-25 (versions moved: none); committed through PR #82.",
        "link": "final-implementation.md#final-implementation-http-core-issue-62",
        "statements": [
          {
            "text": "The generated POM's coordinate is `webtools-http-jvm` 1.0.0, from the root default.",
            "state": "as-planned",
            "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
            "excerpt": {
              "ref": "final-implementation.md#p6--the-dump-the-class-files-the-published-shape-s5",
              "heading": "P6 — the dump, the class files, the published shape (S5)",
              "paragraph": "`./gradlew :webtools-http:apiDump` → `BUILD SUCCESSFUL`; `webtools-http/api/webtools-http.api` has 294 lines, written CRLF by the tool on this host (git stores it LF; `apiCheck` compares line by line). `./gradlew :webtools-http:apiCheck` → `BUILD SUCCESSFUL`. Read against plan §7.8, every point holds: `public abstract fun execute-gIAlu-s (…HttpRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`, a public `HttpTransport$DefaultImpls` with `public static fun close (…HttpTransport;)V`, `public fun close ()V`; `WallClock$System` final with `INSTANCE`, `nowEpochMillis ()J` and `toString`; …",
              "hash": "4f835e8ce361"
            }
          },
          {
            "text": "The build file's header comment says 1.0.0 is unreleased and not a release candidate.",
            "state": "as-planned",
            "ref": "final-implementation.md#final-implementation-http-core-issue-62",
            "excerpt": {
              "ref": "final-implementation.md#final-implementation-http-core-issue-62",
              "heading": "Final implementation: `http-core` (Issue #62)",
              "paragraph": "The as-built record of `SpartanLaboratories/WebTools#62`, unit 3 (`http-core`) of #55's architecture. It states what was built, where the work departs from `plan.md` and why, and the measurements that show it holds. What the unit changes for the repository and for the sibling units is in `impact.md`.",
              "hash": "a21f5f12c366"
            }
          },
          {
            "text": "No version moved; the landing named no release candidate, and nothing was published.",
            "state": "as-planned",
            "ref": "final-implementation.md#final-implementation-http-core-issue-62",
            "excerpt": {
              "ref": "final-implementation.md#final-implementation-http-core-issue-62",
              "heading": "Final implementation: `http-core` (Issue #62)",
              "paragraph": "The as-built record of `SpartanLaboratories/WebTools#62`, unit 3 (`http-core`) of #55's architecture. It states what was built, where the work departs from `plan.md` and why, and the measurements that show it holds. What the unit changes for the repository and for the sibling units is in `impact.md`.",
              "hash": "a21f5f12c366"
            }
          }
        ]
      },
      "verdict": {
        "kind": "build",
        "defense": "The units that build the stack move no version of their own; nothing is released, signed or tagged by this unit.",
        "steps": [
          {
            "id": "S2",
            "what": "Write `settings.gradle.kts`, both module build files, every declaration the gate touches, the value types other than `Url`, a `Url` shell with its final members, the `js` actuals and the permanent Level 1 consumer classes, then run the compile proof.",
            "files": [
              "settings.gradle.kts",
              "webtools-http/",
              "webtools-http-commoncheck/"
            ],
            "check": "Procedure P3, recorded in `final-implementation.md` §3; S3 does not start if it differs from the plan",
            "link": "plan.md#62-the-gate-and-the-compile-proof-that-opens-the-implementation"
          }
        ],
        "tests": [],
        "risks": [],
        "source": "plan.md R1, §11",
        "link": "plan.md#11-version-control",
        "statements": [
          {
            "text": "`webtools-http` takes `1.0.0` from the root default, with no `version =` line.",
            "ref": "plan.md#r1-the-module",
            "label": "§R1",
            "excerpt": {
              "ref": "plan.md#r1-the-module",
              "heading": "R1. The module",
              "paragraph": "`webtools-http` is a new Kotlin Multiplatform module on `webtools.kotlin-multiplatform-library`. · Coordinates `io.github.spartanlaboratories:webtools-http`, version `1.0.0` inherited from the root default; it publishes `webtools-http` and `webtools-http-jvm`. · One flat package, `com.spartanlabs.webtools.http`. `internal` does the hiding; there is no `.internal` subpackage. · `explicitApi()`. · Its build file states, in a comment, that `1.0.0` is unreleased and is not a release candidate until the whole stack (#55) is complete. · Dependencies: · …",
              "hash": "36cfb5dbdd6b"
            }
          },
          {
            "text": "Its build file says that 1.0.0 is unreleased and not a release candidate until #55's stack is complete.",
            "ref": "plan.md#r1-the-module",
            "label": "§R1",
            "excerpt": {
              "ref": "plan.md#r1-the-module",
              "heading": "R1. The module",
              "paragraph": "`webtools-http` is a new Kotlin Multiplatform module on `webtools.kotlin-multiplatform-library`. · Coordinates `io.github.spartanlaboratories:webtools-http`, version `1.0.0` inherited from the root default; it publishes `webtools-http` and `webtools-http-jvm`. · One flat package, `com.spartanlabs.webtools.http`. `internal` does the hiding; there is no `.internal` subpackage. · `explicitApi()`. · Its build file states, in a comment, that `1.0.0` is unreleased and is not a release candidate until the whole stack (#55) is complete. · Dependencies: · …",
              "hash": "36cfb5dbdd6b"
            }
          },
          {
            "text": "No existing module moves a version, and the land request says so.",
            "ref": "plan.md#11-version-control",
            "excerpt": {
              "ref": "plan.md#11-version-control",
              "heading": "11. Version control",
              "paragraph": "Branch: `feat/issue-62-http-core`. It exists and holds the research commits; … · Commits. Each is coherent and leaves the build green. …; subject lines carry `(Issue #62)`; no attribution trailers. · `build: add the webtools.kotlin-common-check plugin and its TestKit checks (Issue #62)` — this `plan.md`, `build-logic/src/main/kotlin/webtools.kotlin-common-check.gradle.kts`, `SampleProject.kt`, the two new TestKit classes and the `BuildLogicPluginsGatingTest.kt` edit. The plan is in this commit so `git log --follow` binds the two. No module applies the plugin yet, so the build is unchanged for …",
              "hash": "0bca7759e342"
            }
          }
        ]
      }
    },
    {
      "id": "defaults-left",
      "group": "Left for later",
      "name": "Default implementations and policy numbers",
      "system": "webtools-http-core",
      "where": "units 4 to 9 of #55",
      "gap": {
        "kind": "missing",
        "summary": "The ports this unit defines have no implementation and no policy yet: no fake transport, no URL policy, no OkHttp transport, cache, budget or facade.",
        "evidence": [
          "../architecture.md §11 unit 3 — leaves every default implementation and policy",
          "../architecture.md §6.2 — the common core owns no default implementation, no policy number and no fixtures"
        ],
        "source": "../architecture.md §11 unit 3",
        "link": "../architecture.md#11-decomposition"
      },
      "options": [
        {
          "id": "leave-to-units",
          "name": "Leave them to units 4 to 9, each its own sub-issue: #63, #64, #65, #66, #67 and #68",
          "verdict": "accepted",
          "why": "#55's decomposition gives each its own unit, landing after this one, so this unit delivers only the vocabulary they all speak.",
          "source": "../architecture.md §11",
          "link": "../architecture.md#11-decomposition"
        }
      ],
      "asBuilt": {
        "state": "as-planned",
        "files": [],
        "tests": "",
        "verified": "`final-implementation.md` §1, what was built, recorded on 2026-10-25; committed through PR #82.",
        "link": "final-implementation.md#1-what-was-built",
        "statements": [
          {
            "text": "They remain with units 4 to 9 (#63 to #68) and unit 10: the unit built only the vocabulary.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape; the mechanical fixes made on the way are in §5.",
              "hash": "be79bfaf86d5"
            }
          },
          {
            "text": "No port has an implementation beyond `WallClock.System`.",
            "state": "as-planned",
            "ref": "final-implementation.md#1-what-was-built",
            "excerpt": {
              "ref": "final-implementation.md#1-what-was-built",
              "heading": "1. What was built",
              "paragraph": "Everything plan §7 lists, in the plan's shape; the mechanical fixes made on the way are in §5.",
              "hash": "be79bfaf86d5"
            }
          }
        ]
      },
      "verdict": {
        "kind": "leave",
        "defense": "This unit delivers only the vocabulary they all speak; the plan fixes the names it takes and what each sibling builds on.",
        "steps": [],
        "tests": [],
        "risks": [],
        "source": "plan.md §4, §12",
        "link": "plan.md#12-interfaces-with-sibling-units",
        "statements": [
          {
            "text": "`OkHttpTransport`, the URL policies, the response cache, the call budget, the facades and the fixtures stay with units 4 to 9, and `webtools-feeds` with unit 10.",
            "ref": "plan.md#12-interfaces-with-sibling-units",
            "excerpt": {
              "ref": "plan.md#12-interfaces-with-sibling-units",
              "heading": "12. Interfaces with sibling units",
              "paragraph": "Names this unit takes in the package `com.spartanlabs.webtools.http` (a sibling must not reuse them): public — `ExperimentalForInheritanceWebToolsApi`, `HttpTransport`, `WallClock`, `HttpFailure`, `TransportFailure`, `UpstreamFailure`, `RefusalReason`, `ExhaustedReason`, `HttpMethod`, `Url`, `HttpHeaders`, `RequestBody`, `HttpRequest`, `HttpResponse`, `RedirectHop`; internal — `Log`, `logger`, `epochMillisNow`, `flatMap`, `catchNonFatal`, `HttpSyntax`, `UrlProblem`, `UrlParseException`, `urlFailure`, `ParsedUrl`, `parseUrlText`, `defaultPortOf`, `canonicalHost`, `canonicalPath`, …",
              "hash": "2851c196c4fe"
            }
          },
          {
            "text": "No port has an implementation beyond `WallClock.System`.",
            "ref": "plan.md#4-non-goals",
            "excerpt": {
              "ref": "plan.md#4-non-goals",
              "heading": "4. Non-goals",
              "paragraph": "Every default implementation and policy: `OkHttpTransport`, the URL policies, `CaffeineResponseCache`, the call budget, the facades, the fixtures (units 4 to 9); `webtools-feeds` (unit 10). · Internationalised host names, a public `Url` builder, query decoding or a query-pair model, a `withQuery`, URL joining. · A permanent guard or compile-fail test of the opt-in gate (the research's `verifyTestsAreConsumers` task, and the TestKit compile-fail snippet) — left as a follow-up (§13). · Refusing transport-owned header names (`Host`, `Content-Length`, `User-Agent`, `Connection`, …",
              "hash": "d6bcff43fcba"
            }
          }
        ]
      }
    }
  ],
  "impact": {
    "basis": "as-built",
    "summary": "A new module, `webtools-http` 1.0.0, with the common core of the HTTP stack and no implementation beyond `WallClock.System`; it is unreleased and was not a release candidate at this landing. The three existing artifacts do not change. Inside the repository, `./gradlew build` and `check` also compile and test the module (131 tests) and compile its common code for `js`, with no Node.js and no new root task.",
    "none": null,
    "release": {
      "bump": "none",
      "version": null,
      "note": "`webtools-http` 1.0.0 is new, inherited from the root default, and published once #55's stack is complete; no existing version moves",
      "unreleased": true
    },
    "downstream": [
      {
        "who": "`io.github.spartanlaboratories:webtools-http` (new artifact)",
        "kind": "artifact",
        "system": "webtools-http-core",
        "effect": "additive",
        "summary": "A new artifact at 1.0.0 that publishes `webtools-http` and `webtools-http-jvm` once released; its ABI dump is committed. Not a release candidate at this landing.",
        "link": "impact.md#1-published-artifacts"
      },
      {
        "who": "Consumers of `webtools-http` 1.0.0, once it is released",
        "kind": "consumer",
        "system": "webtools-http-core",
        "effect": "additive",
        "summary": "`kotlinx-coroutines-core` 1.10.2 on the compile classpath as the one `api` dependency; `okhttp-jvm`, `caffeine` and `slf4j-api` at runtime only; class files at the Java 11 floor. Through `okhttp-jvm`, a `kotlin-stdlib` 2.2.21 requirement.",
        "compat": "notable",
        "link": "impact.md#1-published-artifacts"
      },
      {
        "who": "Consumers of `webtools-udp`, `webtools-scraping` and `webtools-browser`",
        "kind": "consumer",
        "system": null,
        "effect": "none",
        "summary": "Nothing changes: no source, build-script or test change in the three modules, and no version moves.",
        "link": "impact.md#1-published-artifacts"
      },
      {
        "who": "Repository builds",
        "kind": "repository",
        "system": "gradle-build",
        "effect": "additive",
        "summary": "`./gradlew build` and `check` also run the module's compile, tests, `apiCheck` and `verifyJavaFloor` and the check module's `js` compile. A configuration-cache miss prints one more warning, \"JS Environment Not Selected\"; nothing downloads Node.js or Yarn, and the root project gains no plugin and no task.",
        "compat": "notable",
        "link": "impact.md#build"
      },
      {
        "who": "The later units of #55 (#63 to #70)",
        "kind": "system",
        "system": "webtools-http-core",
        "effect": "additive",
        "summary": "They build on this vocabulary: the failure leaves and reasons, `Url`'s canonical host, the transport contract, `WallClock`, the logging seam and helpers, and the check-module pattern. Unit 6 holds the first top-level implementer of a gated port. `addSensitive` only marks an entry: the redirect policy units 5 and 6 build reads the mark.",
        "link": "impact.md#4-sibling-units"
      }
    ],
    "adaptation": [
      {
        "who": "Unit 4, `test-support` (#63)",
        "system": "webtools-http-core",
        "need": "required",
        "summary": "Its fixtures source sets opt in themselves for the fakes that implement `HttpTransport` and `WallClock`. The root project still has no `check` task, so where the publication check attaches stays its decision.",
        "link": "impact.md#4-sibling-units"
      },
      {
        "who": "Existing code in the repository",
        "system": null,
        "need": "none",
        "summary": "Nothing moves onto `webtools-http` before 1.0.0 is released: `Connector`, `WebViewer` and the legacy `ResultExtensions.kt` copies are unchanged.",
        "link": "impact.md#3-adoption"
      },
      {
        "who": "Unit 6, `okhttp-transport` (#65), and the fixtures' fake (unit 4, #63)",
        "system": "webtools-http-core",
        "need": "required",
        "summary": "`execute` is main-safe (point 8 of `HttpTransport`'s contract): the OkHttp transport moves blocking I/O off the caller's thread and says which dispatcher it uses; the fake meets the point by never blocking.",
        "link": "impact.md#4-sibling-units"
      }
    ],
    "api": [
      {
        "surface": "`ExperimentalForInheritanceWebToolsApi`",
        "system": "webtools-http-core",
        "change": "added",
        "tier": null,
        "summary": "The one opt-in marker of the stack's seams, used only through `@SubclassOptInRequired` and `@OptIn`, and never removed.",
        "link": "plan.md#r3-stability"
      },
      {
        "surface": "`HttpTransport`, `WallClock` (implementing)",
        "system": "webtools-http-core",
        "change": "added",
        "tier": "experimental",
        "summary": "Implementing either needs the opt-in. Calling and passing them are Stable Core, and `WallClock.System` is usable without opting in.",
        "link": "plan.md#61-mechanism-per-type-architecture-69-for-this-units-types"
      },
      {
        "surface": "`HttpFailure`, `TransportFailure`, `UpstreamFailure` (subclassing)",
        "system": "webtools-http-core",
        "change": "added",
        "tier": "experimental",
        "summary": "Subclassing a group needs the opt-in; matching the groups with an `else` does not.",
        "link": "plan.md#63-the-failure-model"
      },
      {
        "surface": "The failure leaves, `RefusalReason`, `ExhaustedReason`",
        "system": "webtools-http-core",
        "change": "added",
        "tier": "stable-core",
        "summary": "Final leaves nested in their group, and two open constants classes that may gain constants in a feature release.",
        "link": "plan.md#63-the-failure-model"
      },
      {
        "surface": "`HttpMethod`, `Url`, `HttpHeaders`, `RequestBody`, `HttpRequest`, `HttpResponse`, `RedirectHop`",
        "system": "webtools-http-core",
        "change": "added",
        "tier": "stable-core",
        "summary": "The request and response vocabulary, with factories over `internal` constructors and value or identity equality per type.",
        "link": "plan.md#64-the-request-and-response-value-types"
      },
      {
        "surface": "`Log`, `logger`, `HttpSyntax`, `flatMap`, `catchNonFatal`, the `Url` encoders",
        "system": "webtools-http-core",
        "change": "added",
        "tier": "internal",
        "summary": "Internal helpers the later units of the module reuse; nothing a consumer reaches.",
        "link": "plan.md#12-interfaces-with-sibling-units"
      }
    ],
    "conversion": [],
    "changes": [
      {
        "what": "`HttpTransport`'s contract gained an eighth point: `execute` is main-safe, which every implementation must meet.",
        "why": "A `suspend` function that blocks its caller's thread breaks the convention every coroutine caller relies on; added in qa pass 1 (deviation 2). KDoc only, no signature change.",
        "when": "qa pass 1",
        "ref": "final-implementation.md#2-deviations-from-the-plan",
        "entry": "adaptation.2",
        "system": "webtools-http-core"
      },
      {
        "what": "`HttpHeaders.Builder.addSensitive` promises only the mark: the cross-origin strip is the later redirect policy's, which reads it.",
        "why": "Its KDoc had promised a strip this unit does not perform; qa pass 1 narrowed it, and the reconciled `impact.md` §4 gives the reading to units 5 and 6.",
        "when": "qa pass 1",
        "ref": "impact.md#4-sibling-units",
        "entry": "downstream.4",
        "system": "webtools-http-core"
      }
    ],
    "source": "impact.md, reconciled with final-implementation.md",
    "link": "impact.md#1-published-artifacts"
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
  "followUps": [
    {
      "name": "A permanent compile-fail check of the gate",
      "note": "A TestKit snippet in `build-logic` that re-runs the eight negative cases; the research's `verifyTestsAreConsumers` guard task can be reconsidered with it. Not filed."
    },
    {
      "name": "A dated callout on #55's `architecture.md`",
      "note": "§3, §6.1 and §6.8 describe the check plugin as `js { nodejs { } }`; the document is left as written until the maintainer approves a callout, and unit 11 describes the as-built form."
    },
    {
      "issue": 58,
      "note": "CI and repository practices: the first place the check module, `apiCheck` and the `Url` differential would run on every change; releasing `webtools-http` waits on it and on the rest of the stack."
    },
    {
      "issue": 50,
      "note": "`webtools-udp` multiplatform: can reuse the check-module pattern and the logging seam."
    }
  ],
  "documents": [
    {
      "label": "Issue #62",
      "path": "https://github.com/SpartanLaboratories/WebTools/issues/62",
      "note": "the unit as #55 filed it"
    },
    {
      "label": "#62 plan",
      "path": "plan.md",
      "note": "the requirements, the design, the seven stages and the procedures"
    },
    {
      "label": "#62 impact",
      "path": "impact.md",
      "note": "the published artifacts, the blast radius, the sibling units, and §5 where the unit departs from #55's documents"
    },
    {
      "label": "#62 as-built record",
      "path": "final-implementation.md",
      "note": "the implementer's as-built input through S7: §1 what was built, §2 no deviation, §4 the eight acceptance criteria met, §6 the Level 5 pass, §8 the known gaps; reconciled into the final record at the landing"
    },
    {
      "label": "#62 research",
      "path": "research/README.md",
      "note": "this unit's four questions, their verification (107 claims, all hold) and the summary the plan rests on"
    },
    {
      "label": "#55 research",
      "path": "../research/README.md",
      "note": "the questions, verification and summary the Gap column rests on"
    },
    {
      "label": "#55 architecture",
      "path": "../architecture.md",
      "note": "§2 current state, §6.2 the common core, §6.8 the check-only `js` modules, §6.9 the common-code mechanism per type, §9 extension and stability, §10 alternatives, §11 unit 3"
    },
    {
      "label": "#55 impact",
      "path": "../impact.md",
      "note": "§7 the open point this unit settles: the first compile of the marker, a gated interface, a default naming it and a consumer without the opt-in"
    },
    {
      "label": "#55 tracking board",
      "path": "../dashboard.html",
      "note": "the eleven units in landing order"
    }
  ],
  "notes": {
    "paths": "`common/` and `jvm/` abbreviate `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/` and its `jvmMain` twin, as #55's architecture does. Line references marked baseline are to `f72b115`.",
    "origin": "This unit's research and architecture happened in #55's run, so the Gap and Considered columns were written at sized from #55's research README and architecture; the unit's own plan adds to Considered and writes the Verdict at plan acceptance.",
    "corrections": [
      {
        "what": "The glow is corrected from Considered and Verdict to Considered alone. The glow follows the step: research works on the Considered column, and the Verdict column glows from the moment plan writing starts.",
        "when": "research, 2026-10-25"
      }
    ],
    "departure": "The unit's plan departs from #55's architecture in one decision: the check plugin calls `js()` with no environment in place of `js { nodejs { testTask { enabled = false } } }` (plan.md §13, impact.md §5). It shows in the common-check-plugin row's Considered and Verdict columns and, on #55's tracking board, as a change on the http-core unit row; `architecture.md` is left as written."
  },
  "issueBoards": {
    "47": "../47-java-23-bytecode/dashboard.html",
    "61": "../61-build-logic/dashboard.html",
    "63": "../63-test-support/dashboard.html",
    "64": "../64-url-policy/dashboard.html"
  },
  "links": {
    "workBoard": "../../index.html",
    "repoBoard": "../../../dashboards/index.html",
    "boards": {
      "webtools-http-core": "../../../dashboards/webtools-http/index.html",
      "gradle-build": "../../../dashboards/gradle-build/index.html"
    }
  }
}
);
