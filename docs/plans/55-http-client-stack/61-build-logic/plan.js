/* plan.js: plan.json as a script, written by plan/scripts/embed.js beside the page for its live view (plan/RULES.md §1). Do not edit: edit plan.json and re-embed. */
window.dashboardLive&&window.dashboardLive("plan",
{
  "schema": "plan-board/1",
  "repo": "SpartanLaboratories/WebTools",
  "issue": 61,
  "slug": "61-build-logic",
  "folder": "docs/plans/55-http-client-stack/61-build-logic",
  "kind": "issue",
  "level": 3,
  "title": "Shared build logic",
  "issueTitle": "Build: move shared configuration into an included `build-logic` build with convention plugins and package-selected level tasks",
  "function": "The root build configured every module through one `subprojects {}` block, selecting the seven level test tasks by JUnit tag, and a Kotlin Multiplatform module could not join it without branching the block. This run moves that configuration into an included `build-logic` build with three convention plugins and `WebToolsBuild`, selects the level tasks by package, puts the three modules on `webtools.kotlin-jvm-library`, readies `webtools.kotlin-multiplatform-library` for unit 3, and tests the plugins at every level that has tests on a Gradle TestKit harness. Unit 1 of #55, a `build:` change inside one system with no downstream effect: the published output is byte-identical to `834ce4a`, so no version moves.",
  "status": "built",
  "parent": {
    "issue": 55,
    "board": "../dashboard.html"
  },
  "branch": "feat/issue-61-build-logic",
  "baseline": {
    "ref": "master",
    "sha": "834ce4a",
    "date": "2026-10-21"
  },
  "asBuilt": {
    "ref": "master",
    "sha": "53248b3",
    "date": "2026-10-22",
    "pr": 74,
    "merge": "1e27071",
    "version": "none: published output identical to 834ce4a"
  },
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
      "slug": "webtools-udp",
      "name": "webtools-udp",
      "slot": 1,
      "top": "webtools-udp",
      "status": "built",
      "page": "docs/dashboards/webtools-udp/index.html"
    },
    {
      "slug": "webtools-scraping",
      "name": "webtools-scraping",
      "slot": 2,
      "top": "webtools-scraping",
      "status": "built",
      "page": null
    },
    {
      "slug": "webtools-browser",
      "name": "webtools-browser",
      "slot": 3,
      "top": "webtools-browser",
      "status": "built",
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
    "step": "landed",
    "since": "2026-10-22T21:58:56Z",
    "updated": "2026-10-22T22:22:20Z",
    "detail": "Merged through PR #74 as 1e27071; no version moved. The board was written after the landing, from the issue folder's documents: the run predates the dashboards service.",
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
    "qa": [
      {
        "pass": 1,
        "verdict": "changes-requested",
        "date": "2026-10-22",
        "findings": {
          "documentation": 14,
          "test": 9,
          "code": 0
        },
        "note": "all remediated through amendment 1"
      },
      {
        "pass": 2,
        "verdict": "approved",
        "date": "2026-10-22",
        "findings": {
          "documentation": 5,
          "test": 1,
          "code": 0
        },
        "note": "passed the code review and the narrowed Level-5 pass; six minor fixes applied on the spot, no third pass"
      }
    ],
    "qaMax": 3,
    "waitingOn": null,
    "resume": null,
    "blockers": {
      "waitsOn": [],
      "blocks": []
    },
    "landing": {
      "pr": 74,
      "merge": "1e27071",
      "version": "none: published output identical to 834ce4a",
      "date": "2026-10-22"
    }
  },
  "milestones": [
    {
      "column": "gap",
      "label": "Research verified",
      "date": "2026-10-21",
      "ref": "research/README.md",
      "note": "two questions and two findings files; 65 outside-world claims re-opened at source, all hold"
    },
    {
      "column": "considered",
      "label": "Options weighed",
      "date": "2026-10-21",
      "ref": "../architecture.md",
      "note": "#55's architecture §6.1 and §10 for the topology and the ABI tooling; #55's research summary for the level selection; this unit's research summary for publishing, the validator and the harness"
    },
    {
      "column": "verdict",
      "label": "Plan committed",
      "date": "2026-10-22",
      "ref": "c1fa76e",
      "note": "the first version, with amendments 1 and 2 (§16, §17) applied on top, committed with commit 1"
    },
    {
      "column": "built",
      "label": "Merged",
      "date": "2026-10-22",
      "ref": "PR #74",
      "note": "three commits, c1fa76e to 960cfa4; merged as 1e27071; no version moved"
    }
  ],
  "trail": [
    {
      "date": "2026-10-21",
      "column": "gap",
      "label": "Issue #61 opened as unit 1 of #55",
      "ref": "#61"
    },
    {
      "date": "2026-10-21",
      "column": "gap",
      "label": "Baseline: master at the merge of PR #60, #55's plan",
      "ref": "834ce4a"
    },
    {
      "date": "2026-10-21",
      "column": "gap",
      "label": "Research questions: the TestKit harness and the multiplatform plugin's wiring",
      "ref": "017e4c7"
    },
    {
      "date": "2026-10-21",
      "column": "gap",
      "label": "Findings: `testkit-harness.md` and `multiplatform-plugin-wiring.md`",
      "ref": "5abb70e"
    },
    {
      "date": "2026-10-21",
      "column": "considered",
      "label": "Verification and summary: 65 claims, all hold; the ASM 9.9.1 pin",
      "ref": "8791ab1"
    },
    {
      "date": "2026-10-21",
      "column": "considered",
      "label": "Summary aligned: `module.json` read with `JsonSlurper`, no JSON dependency",
      "ref": "c80881b"
    },
    {
      "date": "2026-10-22",
      "column": "built",
      "label": "qa pass 1: changes requested, 14 documentation and 9 test findings; amendment 1 completes the test levels and stops running the modules' suites",
      "ref": ""
    },
    {
      "date": "2026-10-22",
      "column": "built",
      "label": "Amendment 2: class names from `<testcase classname>`; `testKitHomeLock` serialises the nested-build tasks",
      "ref": ""
    },
    {
      "date": "2026-10-22",
      "column": "built",
      "label": "qa pass 2: passed, with six minor fixes applied on the spot",
      "ref": ""
    },
    {
      "date": "2026-10-22",
      "column": "verdict",
      "label": "Commit 1: `build-logic` with its plugins and tests, `settings.gradle.kts` and `plan.md`",
      "ref": "c1fa76e"
    },
    {
      "date": "2026-10-22",
      "column": "built",
      "label": "Commit 2: the root script and the three module build files onto the plugins; the README",
      "ref": "db98325"
    },
    {
      "date": "2026-10-22",
      "column": "built",
      "label": "Commit 3: `impact.md` and the reconciled `final-implementation.md`",
      "ref": "960cfa4"
    },
    {
      "date": "2026-10-22",
      "column": "built",
      "label": "PR #74 merged; #61 closed",
      "ref": "1e27071"
    }
  ],
  "rows": [
    {
      "id": "included-build",
      "group": "Code",
      "name": "The included build and the root script",
      "system": "gradle-build",
      "where": "settings.gradle.kts · build-logic/settings.gradle.kts · build-logic/build.gradle.kts · build.gradle.kts",
      "gap": {
        "kind": "debt",
        "summary": "Every module is configured by the root build's `subprojects {}` block: both plugins, `api(slf4j-api)`, the test dependencies, the seven level tasks and the publishing skeleton. A multiplatform module cannot join without branching the block, which Gradle documents as improper cross-project configuration; the plugin versions sit in the root `plugins` block with `apply false`.",
        "evidence": [
          "build.gradle.kts:1-5 — `plugins { … apply false }` (baseline)",
          "build.gradle.kts:17-92 — the `subprojects {}` block (baseline)"
        ],
        "source": "plan.md §2",
        "link": "plan.md#2-current-state"
      },
      "options": [
        {
          "id": "included-build",
          "name": "An included `build-logic` build reached through `pluginManagement`, with the plugin markers at literal versions",
          "verdict": "accepted",
          "why": "One place decides every module's compile target, test layout and publishing shape; plugins are applied by id with no version and with typed accessors, and a change does not invalidate the whole configuration. The topology was measured on this toolchain, the three modules publishing identical POMs and metadata.",
          "source": "../architecture.md §6.1, §10; research/README.md § Summary",
          "link": "../architecture.md#61-shared-build-logic--build-logic"
        },
        {
          "id": "subprojects-withid",
          "name": "The root `subprojects {}` with `plugins.withId` branches",
          "verdict": "declined",
          "why": "Cross-project configuration is what Gradle documents as improper, and the multiplatform extension would need string-typed `extensions.configure`.",
          "wins": "If an included build were unwanted: it is the smallest diff.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        },
        {
          "id": "buildsrc",
          "name": "`buildSrc`",
          "verdict": "declined",
          "why": "It works (measured), but invalidates the whole configuration on every change.",
          "source": "../architecture.md §10"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "`settings.gradle.kts` includes `build-logic` through `pluginManagement` as its first statement. `build-logic` is a `kotlin-dsl` build whose `settings.gradle.kts` names it and resolves from the Gradle Plugin Portal and Maven Central, with four plugin markers at literal versions: Kotlin JVM and Multiplatform 2.2.0, vanniktech 0.36.0, binary-compatibility-validator 0.18.2. The root script keeps only `allprojects { group; version }`.",
        "defense": "The root applies no plugin, so it has no `build`, `check` or `test` of its own and `./gradlew build` runs each module's; the Kotlin plugin move that Gradle 10 forces becomes a one-line change in one file.",
        "steps": [
          {
            "id": "§6.1",
            "what": "Make `pluginManagement { includeBuild(\"build-logic\") }` the first statement of `settings.gradle.kts`.",
            "files": [
              "settings.gradle.kts"
            ],
            "check": "`./gradlew assemble testClasses`",
            "link": "plan.md#61-settingsgradlekts-changed"
          },
          {
            "id": "§6.2–6.3",
            "what": "Create `build-logic/settings.gradle.kts` and `build-logic/build.gradle.kts` with the plugin markers.",
            "files": [
              "build-logic/settings.gradle.kts",
              "build-logic/build.gradle.kts"
            ],
            "check": "`./gradlew :build-logic:test`",
            "link": "plan.md#63-build-logicbuildgradlekts-new"
          },
          {
            "id": "§6.8",
            "what": "Cut the root `build.gradle.kts` to `allprojects { group; version }`.",
            "files": [
              "build.gradle.kts"
            ],
            "check": "`./gradlew build --dry-run`",
            "link": "plan.md#68-buildgradlekts-changed"
          }
        ],
        "tests": [
          "Procedure C: `assemble testClasses` and `build --dry-run` succeed",
          "Procedure E: no \"Kotlin Gradle plugin was loaded multiple times\" warning"
        ],
        "risks": [
          "Kotlin 2.2.0 prints a Gradle-10 deprecation at `jvm()` (KT-78620); the version is pinned once, so the move is one line"
        ],
        "source": "plan.md §1 R1, R2, R10; §5.1",
        "link": "plan.md#51-topology"
      },
      "asBuilt": {
        "state": "as-planned",
        "summary": "Built as decided: `build-logic/` and the settings in commit 1, the root script in commit 2, which keeps only `group` and `version` under a comment pointing at `build-logic` and the README.",
        "files": [
          "settings.gradle.kts",
          "build-logic/settings.gradle.kts",
          "build-logic/build.gradle.kts",
          "build.gradle.kts"
        ],
        "tests": "Procedures C and E: exit 0, no `loaded multiple times` and no deprecation line; the end-to-end class confirms the Kotlin plugin loads once along the real included-build path",
        "commits": [
          "c1fa76e",
          "db98325"
        ],
        "verified": "Procedures C and E, recorded on 2026-10-22.",
        "link": "final-implementation.md#the-included-build"
      }
    },
    {
      "id": "level-tasks",
      "group": "Code",
      "name": "WebToolsBuild and package-selected level tasks",
      "system": "gradle-build",
      "where": "build-logic/src/main/kotlin/WebToolsBuild.kt",
      "gap": {
        "kind": "debt",
        "summary": "The seven level tasks are registered by the root `subprojects {}` block over `sourceSets[\"test\"]` and select by JUnit tag (`includeTags`). A multiplatform module's `jvm` test compilation is not `sourceSets[\"test\"]`, and nothing outside the root build can read the level list.",
        "evidence": [
          "build.gradle.kts:44-62 — the level tasks; `includeTags(tag)` at :60 (baseline)",
          "plan.md §2 — 95 tagged test classes, each with one class-level `@Tag` equal to its level package"
        ],
        "source": "plan.md §2",
        "link": "plan.md#2-current-state"
      },
      "options": [
        {
          "id": "package-filter",
          "name": "`WebToolsBuild.registerLevelTasks(project, classesDirs, classpath)`, selecting by package `com.spartanlabs.testing.<level>.*` with `isFailOnNoMatchingTests = false`",
          "verdict": "accepted",
          "why": "It works over whatever test classes and classpath the caller passes, so both library plugins share it; the package already is each class's level, so on the three modules it selects exactly what the tags select, and a level with nothing to run still passes.",
          "source": "../architecture.md §6.1; plan.md §5.3",
          "link": "plan.md#53-level-tasks"
        },
        {
          "id": "junit-tags",
          "name": "Keep selecting the level tasks by JUnit tag (`includeTags`)",
          "verdict": "declined",
          "why": "A `commonTest` class cannot carry a JUnit tag, so a multiplatform module's common tests could not be selected by level; on the three existing modules the package filter selects exactly the classes the tags select.",
          "source": "../research/README.md § Build, publishing and the Java 11 floor",
          "link": "../research/README.md#build-publishing-and-the-java-11-floor"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "`WebToolsBuild`, a public object in the default package, holds `LEVEL_PACKAGE_ROOT`, the ordered `LEVELS` and `registerLevelTasks`, which registers `gatingTest` … `uatTest` as plain `Test` tasks in `verification`, each on the JUnit Platform and out of `check`. Descriptions lose only their `(@Tag(\"…\"))` suffix; the 95 annotations stay.",
        "defense": "One function, called by both plugins with their own classes and classpath, so a level means the same in every module; selection by package needs no annotation a new test could forget.",
        "steps": [
          {
            "id": "§6.4",
            "what": "Create `WebToolsBuild.kt` with `TestLevel` and `WebToolsBuild`.",
            "files": [
              "build-logic/src/main/kotlin/WebToolsBuild.kt"
            ],
            "check": "`RegisterLevelTasksComponentTest`, `WebToolsBuildLevelsDeterministicTest`",
            "link": "plan.md#64-build-logicsrcmainkotlinwebtoolsbuildkt-new"
          }
        ],
        "tests": [
          "Level 2 — `RegisterLevelTasksComponentTest`: seven plain `Test` tasks in order, the filter, the descriptions, a duplicate call throws",
          "Level 4a — `WebToolsBuildLevelsDeterministicTest`: the ids, task names, patterns and descriptions",
          "Level 3 — `LevelSelectionIntegrationTest`: the multiplatform sample's level tasks select by package from `commonTest` and `jvmTest`"
        ],
        "risks": [
          "A test class outside every level package no longer runs in a level task; none exists, it still runs under `test`, and the README states the rule"
        ],
        "source": "plan.md §1 R3, R8; §5.3",
        "link": "plan.md#53-level-tasks"
      },
      "asBuilt": {
        "state": "as-planned",
        "summary": "Built as decided in commit 1; the three modules select by package from commit 2. Under `--test-dry-run` their level tasks select the same 95 classes as the tags did on `834ce4a`: 92 in webtools-udp, 1 in webtools-scraping, 2 in webtools-browser.",
        "files": [
          "build-logic/src/main/kotlin/WebToolsBuild.kt"
        ],
        "tests": "component 7 and deterministic 6 tests; `LevelSelectionIntegrationTest` 6/6",
        "commits": [
          "c1fa76e",
          "db98325"
        ],
        "verified": "Procedure B, identical selections on both trees.",
        "link": "final-implementation.md#the-repository-build"
      }
    },
    {
      "id": "published-library",
      "group": "Code",
      "name": "Publishing convention: webtools.published-library",
      "system": "gradle-build",
      "where": "build-logic/src/main/kotlin/webtools.published-library.gradle.kts",
      "gap": {
        "kind": "debt",
        "summary": "The Maven Central skeleton — vanniktech, `mavenCentral()`, `publishToMavenCentral()`, `signAllPublications()` and the shared POM — lives inside the root `subprojects {}` block, so no module can take it without everything else the block applies.",
        "evidence": [
          "build.gradle.kts:21-23 and :64-91 — repositories and `mavenPublishing`, the shared `pom {}` at :68-90 (baseline)"
        ],
        "source": "research/README.md § Codebase facts",
        "link": "research/README.md#codebase-facts"
      },
      "options": [
        {
          "id": "no-configure",
          "name": "Move the block verbatim into a plugin that never calls `configure(...)`",
          "verdict": "accepted",
          "why": "vanniktech's own `afterEvaluate` then picks `KotlinJvm(JavadocJar.Javadoc(), SourcesJar.Sources())` for a Kotlin/JVM module, which is what the three modules published before; no `POM_*` or publishing property is set anywhere, so nothing else changes.",
          "source": "research/README.md § webtools.published-library",
          "link": "research/README.md#webtoolspublished-library"
        },
        {
          "id": "explicit-kotlinjvm",
          "name": "Call `configure(KotlinJvm())` explicitly",
          "verdict": "declined",
          "why": "In vanniktech 0.36.0 a manual `configure` without `javadocJar` defaults to `JavadocJar.Empty()`, so the three modules would ship an empty javadoc jar and their published output would change.",
          "source": "research/README.md § webtools.published-library"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "`webtools.published-library` applies vanniktech, declares `mavenCentral()` and calls `publishToMavenCentral()`, `signAllPublications()` and the shared `pom {}`; its header names what a release needs by property key only. Coordinates and the POM name and description stay in each module.",
        "defense": "Both library plugins apply it, so one block decides the publishing shape; leaving the platform to vanniktech keeps every published byte as it was.",
        "steps": [
          {
            "id": "§6.5",
            "what": "Create `webtools.published-library.gradle.kts`.",
            "files": [
              "build-logic/src/main/kotlin/webtools.published-library.gradle.kts"
            ],
            "check": "Procedure A: POMs, module metadata and jars identical to `834ce4a`",
            "link": "plan.md#65-build-logicsrcmainkotlinwebtoolspublished-librarygradlekts-new"
          }
        ],
        "tests": [
          "Procedure A: the published output compared once with `834ce4a`"
        ],
        "risks": [
          "Published-output drift, from dependency order, the javadoc-jar choice or a changed POM; procedure A measures it"
        ],
        "source": "plan.md §1 R4, R7; §5.4",
        "link": "plan.md#54-publishing"
      },
      "asBuilt": {
        "state": "amended",
        "summary": "Built as decided in commit 1. Procedure A found no difference in any of the nine file groups of the three modules: equal POM SHA-256s, equal module metadata, every jar entry equal.",
        "changes": [
          {
            "what": "`PublishingConventionIntegrationTest` locks the convention for both library plugins: the eleven shared POM elements, the Central repository, one signing task per publication, the sources and javadoc jars.",
            "why": "Amendment 1, after qa pass 1, gave the publishing convention a repeatable test; the samples publish only into their own workspace, with their `Sign` tasks disabled.",
            "when": "amendment 1 · after qa pass 1",
            "ref": "c1fa76e"
          }
        ],
        "files": [
          "build-logic/src/main/kotlin/webtools.published-library.gradle.kts"
        ],
        "tests": "`PublishingConventionIntegrationTest` 6/6",
        "commits": [
          "c1fa76e"
        ],
        "verified": "Procedure A on a clean export of the change.",
        "link": "final-implementation.md#procedure-a--published-output-identity"
      }
    },
    {
      "id": "jvm-library",
      "group": "Code",
      "name": "webtools.kotlin-jvm-library and the three modules on it",
      "system": "gradle-build",
      "where": "build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts · webtools-udp, -scraping, -browser build.gradle.kts",
      "gap": {
        "kind": "debt",
        "summary": "Each module inherits Kotlin/JVM, `api(slf4j-api)`, the test dependencies, JUnit Platform and the level tasks from the root block and declares none of them; `webtools-udp`'s port lock calls `tasks.named<Test>` on tasks the root registers.",
        "evidence": [
          "build.gradle.kts:18-19, :25-37 — the plugins, the dependencies, JUnit Platform (baseline)",
          "webtools-udp/build.gradle.kts:103-117 — the port lock (baseline)"
        ],
        "source": "plan.md §2",
        "link": "plan.md#2-current-state"
      },
      "options": [
        {
          "id": "module-applies-plugin",
          "name": "Each module applies `id(\"webtools.kotlin-jvm-library\")` and declares its own `api(\"org.slf4j:slf4j-api:2.0.13\")`",
          "verdict": "accepted",
          "why": "With `api(slf4j-api)` in each module the POMs stay identical, and the new multiplatform modules can declare slf4j as `implementation` only; the plugin is applied in `plugins {}` before the port lock's `tasks.named` calls run, so the lock stays in the module unchanged.",
          "source": "../architecture.md §6.1",
          "link": "../architecture.md#61-shared-build-logic--build-logic"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "The plugin applies Kotlin/JVM and `webtools.published-library`, adds `kotlin(\"test\")`, `kotlin(\"test-junit5\")` and logback 1.5.6 as test dependencies, puts every `Test` on the JUnit Platform and registers the level tasks over `sourceSets[\"test\"]`; it sets no `jvmTarget` or toolchain and marks the floor's place `TODO(#47)`. Each module file adds the plugin, its `api(slf4j-api)`, its coordinates and POM name; `webtools-udp`'s `2.0.0a` note, version line and port lock stay byte-identical.",
        "defense": "Class files keep following the build JDK, as before, so the published output does not move; unit 2 fills the marked seam.",
        "steps": [
          {
            "id": "§6.6",
            "what": "Create `webtools.kotlin-jvm-library.gradle.kts`.",
            "files": [
              "build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts"
            ],
            "check": "Procedure B",
            "link": "plan.md#66-build-logicsrcmainkotlinwebtoolskotlin-jvm-librarygradlekts-new"
          },
          {
            "id": "§6.9–6.10",
            "what": "Rewrite the three module build files onto the plugin.",
            "files": [
              "webtools-udp/build.gradle.kts",
              "webtools-scraping/build.gradle.kts",
              "webtools-browser/build.gradle.kts"
            ],
            "check": "Procedure F(1): `git diff webtools-udp/build.gradle.kts`",
            "link": "plan.md#69-webtools-udpbuildgradlekts-changed"
          }
        ],
        "tests": [
          "Procedure F: the port lock's lines unchanged and its task names resolving",
          "Procedure B: the modules' level selections"
        ],
        "risks": [
          "The port lock's `tasks.named` calls need the level tasks first; the plugin is applied in `plugins {}`, before them"
        ],
        "source": "plan.md §1 R5, R6; §6.6, §6.9",
        "link": "plan.md#66-build-logicsrcmainkotlinwebtoolskotlin-jvm-librarygradlekts-new"
      },
      "asBuilt": {
        "state": "amended",
        "summary": "Built as decided: commit 1 adds the plugin, commit 2 moves the modules onto it. `git diff webtools-udp/build.gradle.kts` is one hunk, `@@ -2 +2,12 @@`: nothing from the `2.0.0a` note to the end of the file changes.",
        "changes": [
          {
            "what": "`JvmLevelSelectionIntegrationTest` checks the plugin on a generated Kotlin/JVM project: level selection by package, `test` running every class, no level task in `check`.",
            "why": "Amendment 1, after qa pass 1: the plugin the three modules use had no repeatable test.",
            "when": "amendment 1 · after qa pass 1",
            "ref": "c1fa76e"
          },
          {
            "what": "`JvmConfigurationCacheReuseNonFunctionalTest` checks configuration-cache reuse on the Kotlin/JVM sample; the test-dependency comment names where the dependencies moved from.",
            "why": "qa pass 2 asked for the multiplatform check's counterpart and for the comment.",
            "when": "qa pass 2",
            "ref": "c1fa76e"
          }
        ],
        "files": [
          "build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts",
          "webtools-udp/build.gradle.kts",
          "webtools-scraping/build.gradle.kts",
          "webtools-browser/build.gradle.kts"
        ],
        "tests": "`JvmLevelSelectionIntegrationTest` 7/7, `JvmConfigurationCacheReuseNonFunctionalTest` 1/1; procedures F(1) and F(2)",
        "commits": [
          "c1fa76e",
          "db98325"
        ],
        "verified": "Procedures B and F.",
        "link": "final-implementation.md#the-repository-build"
      }
    },
    {
      "id": "kmp-library",
      "group": "Code",
      "name": "webtools.kotlin-multiplatform-library",
      "system": "gradle-build",
      "where": "build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts",
      "gap": {
        "kind": "missing",
        "summary": "No convention exists for a Kotlin Multiplatform module. A `jvm()` target writes no `org.gradle.jvm.version` unless the attribute is set on it, and binary-compatibility-validator 0.18.2 hard-codes ASM 9.6, which rejects class files above Java 22: on this JDK 23 host `apiDump` fails.",
        "evidence": [
          "research/README.md § webtools.kotlin-multiplatform-library — the ASM 9.6 limit, reproduced against the ASM jars",
          "../47-java-23-bytecode/research/java-11-floor.md F1 — no attribute unless set on the target"
        ],
        "source": "research/README.md § Summary",
        "link": "research/README.md#webtoolskotlin-multiplatform-library-abi-and-publishing-wiring"
      },
      "options": [
        {
          "id": "validator-in-plugin",
          "name": "Apply binary-compatibility-validator 0.18.2 in the plugin's own `plugins {}` block, on its defaults, with ASM 9.9.1 pinned on its worker classpath",
          "verdict": "accepted",
          "why": "Applied to one leaf project it touches only that project; the defaults give one JVM dump per module with `apiCheck` in `check`, and the three existing modules get no dump. ASM 9.9 reads Java 26 class files, the newest JDK that runs Gradle 9.7.1; 9.9.1 is its bug-fix release.",
          "source": "research/README.md § Summary",
          "link": "research/README.md#webtoolskotlin-multiplatform-library-abi-and-publishing-wiring"
        },
        {
          "id": "validator-at-root",
          "name": "Apply the validator at the root",
          "verdict": "declined",
          "why": "The root applies no plugin; root application configures every subproject, the three modules included, which take no dump; and applying it also on an ancestor fails with \"Cannot add a configuration with name 'bcv-rt-jvm-cp'\".",
          "source": "research/README.md § Questions 2"
        },
        {
          "id": "asm-9-8",
          "name": "Pin ASM 9.8, the version the validator compiles and tests against",
          "verdict": "declined",
          "why": "9.8 reads class files up to Java 25 only, and every JDK from 17 to 26 can run Gradle 9.7.1.",
          "source": "research/README.md § Where the findings differ",
          "link": "research/README.md#where-the-findings-differ"
        },
        {
          "id": "kmp-auto-detect",
          "name": "Leave the multiplatform publication to vanniktech's auto-detection",
          "verdict": "declined",
          "why": "It picks the same today; `configure(KotlinMultiplatform(JavadocJar.Empty(), SourcesJar.Sources()))` pins it, so it stays the same if a Dokka plugin is ever applied.",
          "source": "plan.md §5.4",
          "link": "plan.md#54-publishing"
        },
        {
          "id": "kgp-abi-validation",
          "name": "KGP's built-in `abiValidation` instead of binary-compatibility-validator",
          "verdict": "declined",
          "why": "It is experimental on Kotlin 2.2.0, and its DSL changed by 2.4.",
          "source": "../architecture.md §10",
          "link": "../architecture.md#10-alternatives-considered"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "The plugin applies Kotlin Multiplatform with one `jvm()` target and its `TargetJvmVersion` attribute (the build JDK's major version until #47), `webtools.published-library` and vanniktech with the pinned multiplatform publication, the validator with the ASM pin, the shared test dependencies, the level tasks over the `jvm` test compilation and a lifecycle `test` depending on `jvmTest`. No module applies it until unit 3.",
        "defense": "Unit 3 gets a module that publishes, tests by level and checks its ABI the day it applies one id; the ASM pin is inert once the floor compiles to major 55.",
        "steps": [
          {
            "id": "§6.7",
            "what": "Create `webtools.kotlin-multiplatform-library.gradle.kts`.",
            "files": [
              "build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts"
            ],
            "check": "`./gradlew :build-logic:integrationTest`",
            "link": "plan.md#67-build-logicsrcmainkotlinwebtoolskotlin-multiplatform-librarygradlekts-new"
          }
        ],
        "tests": [
          "Level 3 — `LifecycleTestTaskIntegrationTest`: `test` runs `jvmTest`",
          "Level 3 — `ApiValidationIntegrationTest`: `apiCheck` fails before a dump, `apiDump` writes it, `check` includes `apiCheck`; the pin's guard on JDK 23 and later",
          "Level 3 — `PublishedJvmVersionAttributeIntegrationTest`: both `module.json` files carry the attribute",
          "Level 3 — configuration-cache reuse on the multiplatform sample"
        ],
        "risks": [
          "binary-compatibility-validator 0.18.2 is in maintenance mode and Gradle 9 is outside its test matrix; it is pinned in one file",
          "Kotlin 2.2.0's Gradle-10 deprecation at `jvm()` (KT-78620): no build runs with `--warning-mode=fail`"
        ],
        "source": "plan.md §1 R9; §5.4, §5.5",
        "link": "plan.md#55-binary-compatibility-validator-and-the-asm-pin"
      },
      "asBuilt": {
        "state": "amended",
        "summary": "Built as decided in commit 1; no module applies it yet. On the JDK 23 host both `module.json` files carry `org.gradle.jvm.version` 23 and `apiDump` passes with the ASM pin.",
        "changes": [
          {
            "what": "The configuration-cache check moved from Level 3 to Level 4c, as `ConfigurationCacheReuseNonFunctionalTest`, run by `nonfunctionalTest`.",
            "why": "Amendment 1, after qa pass 1: configuration-cache compatibility is the non-functional property this unit keeps.",
            "when": "amendment 1 · after qa pass 1",
            "ref": "c1fa76e"
          }
        ],
        "files": [
          "build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts"
        ],
        "tests": "integration 28/28 in the amendment-2 run; `ConfigurationCacheReuseNonFunctionalTest` 1/1",
        "commits": [
          "c1fa76e"
        ],
        "verified": "Procedure G, every build-logic task from the root.",
        "link": "final-implementation.md#the-included-build"
      }
    },
    {
      "id": "buildlogic-tests",
      "group": "Tests",
      "name": "build-logic's own tests and the TestKit harness",
      "system": "gradle-build",
      "where": "build-logic/src/test/kotlin/com/spartanlabs/testing/<level>/webtools/buildlogic/",
      "gap": {
        "kind": "missing",
        "summary": "The shared build logic has no test, and the multiplatform plugin has no module to exercise it until unit 3. A check must reach plugins that apply Kotlin Multiplatform, the validator and vanniktech, run on Windows where a lingering daemon can hold files, and stay out of `./gradlew build`.",
        "evidence": [
          "research/README.md § Questions 1 — the requirements on the harness"
        ],
        "source": "research/README.md § Questions 1",
        "link": "research/README.md#questions"
      },
      "options": [
        {
          "id": "inject-plugin-classpath",
          "name": "Generated projects with the plugins injected by `GradleRunner.withPluginClasspath()`, run by a dedicated task kept out of `test` and `check`",
          "verdict": "accepted",
          "why": "`kotlin-dsl` already writes the plugin-under-test metadata, which carries the plugin descriptors and the Kotlin, validator and vanniktech jars; Gradle documents this arrangement for a `build-logic` included through `pluginManagement`.",
          "source": "research/README.md § The TestKit harness",
          "link": "research/README.md#the-testkit-harness"
        },
        {
          "id": "live-include",
          "name": "Include the live `build-logic` from a generated project",
          "verdict": "declined",
          "why": "A nested build would compile and write in the directory the outer build is executing in, and Gradle documents that builds including the same build may conflict.",
          "source": "research/README.md § The TestKit harness"
        },
        {
          "id": "real-build",
          "name": "Drive the real repository build",
          "verdict": "declined",
          "why": "No module applies the multiplatform plugin, it writes into the working tree, and it would run the modules' test suites.",
          "source": "research/README.md § The TestKit harness"
        },
        {
          "id": "jvm-test-suite",
          "name": "A `jvm-test-suite` for the TestKit tests",
          "verdict": "declined",
          "why": "A suite cannot share the `test` source directory.",
          "source": "research/README.md § The TestKit harness"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "TestKit classes on generated samples in a harness-owned directory under `build-logic/build/`, deleted best effort, run by `:build-logic:integrationTest` out of `test` and `check`; a component and a deterministic class around them. The nested builds use TestKit's default home, shared and kept between runs.",
        "defense": "The checks exercise the plugins exactly as a module applies them without touching the working tree; generated projects avoid `@TempDir`, which fails a test on Windows when a daemon still holds a file.",
        "steps": [
          {
            "id": "§5.6",
            "what": "Build the harness — `SampleProject`, `ProjectWorkspace`, `NestedBuild`, `TestReports`, `ModuleMetadata` — and its test classes.",
            "files": [
              "build-logic/src/test/kotlin/com/spartanlabs/testing/"
            ],
            "check": "`./gradlew :build-logic:integrationTest`",
            "link": "plan.md#56-testkit-harness"
          }
        ],
        "tests": [
          "Level 3 — five TestKit classes on the multiplatform sample",
          "Level 2 and Level 4a — `WebToolsBuild` in isolation"
        ],
        "risks": [
          "The first nested-build run downloads the Kotlin compiler and the test dependencies, so it needs the network",
          "Until CI exists (#58), regressions in the plugins are noticed only when these tasks are run"
        ],
        "source": "plan.md §5.6, §9",
        "link": "plan.md#9-test-plan"
      },
      "asBuilt": {
        "state": "amended",
        "summary": "Built as decided, with every level that has tests on a task of its own: 12 classes and 49 tests, all green from the root in the amendment-2 run. `./gradlew build` runs none of them; `:build-logic:test` is the pre-commit gate.",
        "changes": [
          {
            "what": "One task per level that has tests, each failing on an empty run; `test` runs the gating, component and deterministic levels; a gating class, and an end-to-end class that reaches a copy of `build-logic` through a real `includeBuild`.",
            "why": "Amendment 1, after qa pass 1, completed build-logic's test levels.",
            "when": "amendment 1 · after qa pass 1",
            "ref": "c1fa76e"
          },
          {
            "what": "The nested builds share one explicit TestKit home, `build-logic/build/testkit/home`, passed through the `webtools.testkit.home` system property.",
            "why": "TestKit's default was measured one level deeper than the research derived (`build/tmp/<task>/work/.gradle-test-kit`) and is per task, so each nested-build task would have filled a home of its own; the shared one holds about 910 MB.",
            "when": "amendment 1",
            "ref": "c1fa76e"
          },
          {
            "what": "`TestReports` reads each class name from `<testcase classname>`.",
            "why": "A multiplatform `jvmTest` report names its suite `<SimpleName>[jvm]`, so two `jvmTest` assertions failed in the amendment-1 runs.",
            "when": "amendment 2",
            "ref": "c1fa76e"
          },
          {
            "what": "The three nested-build tasks take the single-permit build service `testKitHomeLock` and run one after another.",
            "why": "With the configuration cache Gradle ran them at the same time on the shared home; nothing failed, but three nested daemons contended for its caches and memory. Warm wall time went from 48 s to 96 s.",
            "when": "amendment 2",
            "ref": "c1fa76e"
          },
          {
            "what": "Wording fixes in `PomFile` and `NestedBuild`, and `SampleProject`'s companion grouped in regions.",
            "why": "qa pass 2's minor fixes.",
            "when": "qa pass 2",
            "ref": "c1fa76e"
          }
        ],
        "files": [
          "build-logic/build.gradle.kts",
          "build-logic/src/test/kotlin/com/spartanlabs/testing/"
        ],
        "tests": "`:build-logic:test` 15; `integrationTest` 28/28, `e2eTest` 4/4, `nonfunctionalTest` 2/2; procedure H: an empty run fails",
        "commits": [
          "c1fa76e"
        ],
        "verified": "Procedures G and H.",
        "link": "final-implementation.md#build-logics-own-tests"
      }
    },
    {
      "id": "move-verification",
      "group": "Tests",
      "name": "Proof that the move changes nothing for the three modules",
      "system": "webtools",
      "where": "procedures A to F (plan.md §9), recorded in final-implementation.md §4",
      "gap": {
        "kind": "risk",
        "summary": "Moving the configuration could change what the three modules publish or which classes each level task selects, and no test in the repository would notice.",
        "evidence": [
          "plan.md §10 — published-output drift and test-selection drift"
        ],
        "source": "plan.md §10",
        "link": "plan.md#10-risks-and-edge-cases"
      },
      "options": [
        {
          "id": "measure-once",
          "name": "Measure once, before and after: the published output byte for byte against `834ce4a`, and every level task of every module run on both trees with the results compared",
          "verdict": "accepted",
          "why": "Direct evidence on the real modules, recorded in `final-implementation.md`.",
          "source": "plan.md §1 R7, Acceptance",
          "link": "plan.md#1-requirements"
        },
        {
          "id": "golden-file",
          "name": "A permanent golden-file publication test",
          "verdict": "declined",
          "why": "A non-goal of the plan: the identity is measured once, before and after.",
          "source": "plan.md §4",
          "link": "plan.md#4-non-goals"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "Procedures A to F, run once and recorded: A compares each module's POM, module metadata and jars with a `834ce4a` baseline; B runs every level task of every module on both trees; C runs `./gradlew build`; D and E check configuration-cache reuse and warnings; F checks `webtools-udp`'s port lock, its serialising effect included.",
        "defense": "Each acceptance criterion names the procedure that proves it.",
        "steps": [
          {
            "id": "A",
            "what": "Compare each module's POM, module metadata and jars with a `834ce4a` baseline.",
            "check": "no difference in any file group",
            "link": "plan.md#9-test-plan"
          },
          {
            "id": "B",
            "what": "Run each module's seven level tasks on both trees and compare the classes and results.",
            "check": "identical per module",
            "link": "plan.md#9-test-plan"
          }
        ],
        "tests": [
          "Procedures A to F, recorded in `final-implementation.md`"
        ],
        "risks": [],
        "source": "plan.md §1 Acceptance; §9",
        "link": "plan.md#9-test-plan"
      },
      "asBuilt": {
        "state": "deviated",
        "summary": "A: no difference in any of the nine file groups. B: identical selections for every module, 95 classes, 657 tests reported and every one skipped, so no test body ran. C, D, E: exit 0, the cache reused, no `loaded multiple times` and no warning line. F(1) and F(2): the port lock unchanged and resolving; its serialising effect is not observed.",
        "changes": [
          {
            "what": "No procedure runs a module's test suite: B compares the selections under `--test-dry-run`, C is `assemble testClasses` plus `build --dry-run`, D and E use `--dry-run`, and F(3) is not run.",
            "why": "The repository's testing rule runs only the tests that exercise a change; this change touches neither the modules' code nor their tests, and identical published output shows them unaffected.",
            "when": "amendment 1 · after qa pass 1",
            "ref": "c1fa76e"
          },
          {
            "what": "A's after side is a clean export of the change in LF, not the working tree.",
            "why": "Four source files are CRLF in this checkout, so the working-tree sources jars differed in CR bytes only; a commit holds LF under `.gitattributes`.",
            "when": "implementation · procedure A",
            "ref": "c1fa76e"
          }
        ],
        "tests": "Procedures A to F as amended; the 657 reported tests match the one executed baseline run (642 executed, 15 `@Disabled`)",
        "commits": [
          "c1fa76e",
          "db98325"
        ],
        "verified": "Recorded in `final-implementation.md` §4.",
        "link": "final-implementation.md#4-measurements"
      }
    },
    {
      "id": "readme",
      "group": "Documents",
      "name": "README \"Build & test\"",
      "system": "webtools",
      "where": "README.md:466-514",
      "gap": {
        "kind": "debt",
        "summary": "The README's \"Build & test\" says each module JUnit-tags its tests so any level can run on its own; it names no included build, no plugin, and nothing about build-logic's own tests.",
        "evidence": [
          "README.md:465-487 — \"Build & test\" (baseline)"
        ],
        "source": "plan.md §1 R12",
        "link": "plan.md#1-requirements"
      },
      "options": [
        {
          "id": "rewrite-section",
          "name": "Rewrite \"Build & test\" in the same change and add \"Shared build logic\"",
          "verdict": "accepted",
          "why": "The change adds an included build, its commands and a build step, so the README changes with it.",
          "source": "plan.md §8",
          "link": "plan.md#8-documentation-impact"
        }
      ],
      "verdict": {
        "kind": "build",
        "summary": "The section states the package-selection rule, the included build and its three plugin ids, what a module's build file adds, build-logic's own level tasks with `:build-logic:test` as its gate, and the on-demand nested-build tasks.",
        "defense": "A reader runs the right command and knows where shared build behaviour lives.",
        "steps": [
          {
            "id": "§6.11",
            "what": "Rewrite README \"Build & test\" and add \"Shared build logic\".",
            "files": [
              "README.md"
            ],
            "check": "Level 5: read against the actual layout",
            "link": "plan.md#611-readmemd-changed"
          }
        ],
        "tests": [
          "Level 5 — the section read against the layout"
        ],
        "risks": [],
        "source": "plan.md §1 R12; §6.11",
        "link": "plan.md#611-readmemd-changed"
      },
      "asBuilt": {
        "state": "amended",
        "summary": "Rewritten in commit 2, with the package rule, the three plugin ids, what a module's file adds, build-logic's level tasks and gate, and the nested-build tasks with their TestKit home and its need for the network.",
        "changes": [
          {
            "what": "The section names the TestKit home, `build-logic/build/testkit/home`, and that its first run needs the network.",
            "why": "Amendment 1 replaced TestKit's default home with one explicit directory.",
            "when": "amendment 1",
            "ref": "db98325"
          },
          {
            "what": "The plugin table's multiplatform row names binary-compatibility-validator, with its committed `api/<module>.api`, and the `TargetJvmVersion` attribute.",
            "why": "qa pass 2, item 5.",
            "when": "qa pass 2",
            "ref": "db98325"
          }
        ],
        "files": [
          "README.md"
        ],
        "tests": "qa pass 2's narrowed Level-5 pass read it against the layout",
        "commits": [
          "db98325"
        ],
        "verified": "qa pass 2.",
        "link": "final-implementation.md#the-repository-build"
      }
    },
    {
      "id": "release",
      "group": "Release",
      "name": "Versions: none moves",
      "system": "webtools",
      "where": "the three modules' version lines",
      "gap": {
        "kind": "risk",
        "summary": "A change to the build could change a published artifact, which would need a version under the repository's rules; `webtools-udp` carries `2.0.0a`, the other two the root's `1.0.0`.",
        "evidence": [
          "webtools-udp/build.gradle.kts:101 — `version = \"2.0.0a\"` (baseline)"
        ],
        "source": "plan.md §12",
        "link": "plan.md#12-version-control"
      },
      "options": [
        {
          "id": "no-bump",
          "name": "No version moves; the commits are typed `build:`",
          "verdict": "accepted",
          "why": "The published POMs, module metadata and jars stay identical (R7), so no consumer sees a difference, and a change inside one system moves a version only with a downstream effect.",
          "source": "plan.md §12",
          "link": "plan.md#12-version-control"
        },
        {
          "id": "set-1-0-0a",
          "name": "Set `1.0.0a` on webtools-scraping and webtools-browser now",
          "verdict": "declined",
          "why": "That belongs to unit 2 (#47), with the Java floor that changes their bytecode.",
          "source": "plan.md §11",
          "link": "plan.md#11-interfaces-with-sibling-units"
        }
      ],
      "verdict": {
        "kind": "leave",
        "summary": "No version line changes: `webtools-udp` stays `2.0.0a`, `webtools-scraping` and `webtools-browser` get no version line and stay `1.0.0`.",
        "defense": "Procedure A is the evidence that nothing published moved.",
        "steps": [],
        "tests": [
          "Procedure A"
        ],
        "risks": [],
        "source": "plan.md §12",
        "link": "plan.md#12-version-control"
      },
      "asBuilt": {
        "state": "as-planned",
        "summary": "No version moved, and procedure A found the published output identical. Two `build:` commits and one `docs:` commit, merged through PR #74 as 1e27071.",
        "commits": [
          "c1fa76e",
          "db98325",
          "960cfa4"
        ],
        "link": "final-implementation.md#4-measurements"
      }
    },
    {
      "id": "java-floor",
      "group": "Left for later",
      "name": "The Java floor (unit 2, #47)",
      "system": "gradle-build",
      "where": "build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts, webtools.kotlin-multiplatform-library.gradle.kts",
      "gap": {
        "kind": "risk",
        "summary": "No plugin sets a JVM target, so class files and `org.gradle.jvm.version` follow the build JDK: the three artifacts declare Java 23 against the README's 11+.",
        "evidence": [
          "#47's issue text",
          "plan.md §4 — the Java floor is unit 2's"
        ],
        "source": "plan.md §4",
        "link": "plan.md#4-non-goals"
      },
      "options": [
        {
          "id": "leave-to-47",
          "name": "Leave it to unit 2 (#47), with `TODO(#47)` seams in both library plugins",
          "verdict": "accepted",
          "why": "The architecture makes the floor a unit of its own; this unit keeps the published output identical, which the floor would change.",
          "source": "plan.md §4, §11",
          "link": "plan.md#11-interfaces-with-sibling-units"
        },
        {
          "id": "floor-here",
          "name": "Set the floor here",
          "verdict": "declined",
          "why": "It moves the bytecode and the versions of every artifact, which this unit's identity check rules out.",
          "source": "plan.md §1 R6"
        }
      ],
      "verdict": {
        "kind": "leave",
        "summary": "Not changed: `webtools.kotlin-jvm-library` marks the floor's region `TODO(#47)` and the multiplatform plugin its attribute value; unit 2 fills both, adds `JAVA_FLOOR` and `verifyJavaFloor`.",
        "defense": "The seams are named, so unit 2 edits two marked places and one TestKit expectation.",
        "steps": [],
        "tests": [],
        "risks": [],
        "source": "plan.md §11",
        "link": "plan.md#11-interfaces-with-sibling-units"
      },
      "asBuilt": {
        "state": "as-planned",
        "summary": "Left as planned: both `TODO(#47)` markers are in place, and #47 is the next unit, blocked by this one until now.",
        "commits": [
          "c1fa76e"
        ],
        "link": "impact.md#4-sibling-units"
      }
    },
    {
      "id": "redundant-tags",
      "group": "Left for later",
      "name": "The 95 redundant @Tag annotations",
      "system": "webtools",
      "where": "webtools-udp, -scraping, -browser src/test",
      "gap": {
        "kind": "debt",
        "summary": "Once the level tasks select by package, the 95 `@Tag` annotations select nothing.",
        "evidence": [
          "plan.md §2 — each tagged class carries one class-level `@Tag` equal to its level package"
        ],
        "source": "plan.md §4",
        "link": "plan.md#4-non-goals"
      },
      "options": [
        {
          "id": "leave-tags",
          "name": "Leave them; optional follow-up `remove-redundant-test-tags`",
          "verdict": "accepted",
          "why": "They are harmless, the package filter selects the same classes, and removing them would touch 95 test files of modules this change leaves alone.",
          "source": "../architecture.md §8",
          "link": "../architecture.md#8-integration-with-existing-systems"
        },
        {
          "id": "remove-tags",
          "name": "Remove them here",
          "verdict": "declined",
          "why": "A non-goal of this unit.",
          "source": "plan.md §4"
        }
      ],
      "verdict": {
        "kind": "leave",
        "summary": "Not changed; the follow-up is named in the plan and the impact.",
        "defense": "Nothing selects by tag any more, so nothing depends on them.",
        "steps": [],
        "tests": [],
        "risks": [],
        "source": "plan.md §4, §13",
        "link": "plan.md#13-sequencing-and-follow-ups"
      },
      "asBuilt": {
        "state": "as-planned",
        "summary": "Left as planned: the 95 annotations stay, now redundant.",
        "link": "impact.md#6-follow-ups-this-unit-leaves"
      }
    },
    {
      "id": "port-lock-comment",
      "group": "Left for later",
      "name": "The port lock's stale comment",
      "system": "webtools-udp",
      "where": "webtools-udp/build.gradle.kts:117-118",
      "gap": {
        "kind": "debt",
        "summary": "The comment above `webtools-udp`'s port lock says the lock lives in the module, \"not in the root build\", wording that predates the move.",
        "evidence": [
          "webtools-udp/build.gradle.kts:103-107 — the comment (baseline)"
        ],
        "source": "plan.md §13",
        "link": "plan.md#13-sequencing-and-follow-ups"
      },
      "options": [
        {
          "id": "leave-comment",
          "name": "Leave it for a later level-1 documentation change",
          "verdict": "accepted",
          "why": "Acceptance 5 keeps every line from the `2.0.0a` note to the end of the file byte-identical.",
          "source": "plan.md §13",
          "link": "plan.md#13-sequencing-and-follow-ups"
        },
        {
          "id": "reword-now",
          "name": "Reword it here",
          "verdict": "declined",
          "why": "It would break the byte-identity that acceptance 5 checks.",
          "source": "plan.md §1 Acceptance"
        }
      ],
      "verdict": {
        "kind": "leave",
        "summary": "Not changed; named as a follow-up.",
        "defense": "One sentence of stale wording costs less than an unverifiable diff in the lock's lines.",
        "steps": [],
        "tests": [],
        "risks": [],
        "source": "plan.md §13",
        "link": "plan.md#13-sequencing-and-follow-ups"
      },
      "asBuilt": {
        "state": "as-planned",
        "summary": "Left as planned: the comment is unchanged at `webtools-udp/build.gradle.kts:117-118`.",
        "link": "impact.md#6-follow-ups-this-unit-leaves"
      }
    }
  ],
  "roadmap": null,
  "followUps": [
    {
      "issue": 47,
      "note": "Unit 2, the Java floor: fills the two `TODO(#47)` seams, adds `JAVA_FLOOR` and `verifyJavaFloor`, and sets `1.0.0a` on webtools-scraping and webtools-browser."
    },
    {
      "issue": 62,
      "note": "Unit 3: the first module on `webtools.kotlin-multiplatform-library`, with its committed ABI dump, and the `webtools.kotlin-common-check` plugin."
    },
    {
      "issue": 63,
      "note": "Unit 4: reuses the harness, the TestKit home and `testKitHomeLock` for the fixtures-publication check, and settles where it attaches to `check`."
    },
    {
      "issue": 58,
      "note": "CI: the first place build-logic's test tasks could run on every change."
    },
    {
      "name": "remove-redundant-test-tags",
      "note": "Optional: drop the 95 `@Tag` annotations the package filter made redundant."
    },
    {
      "name": "asm-pin-removal",
      "note": "Optional: drop the ASM 9.9.1 pin once the floor has landed or the validator ships a newer ASM."
    },
    {
      "name": "port-lock-comment",
      "note": "A level-1 rewording of the port lock's \"not in the root build\" comment."
    }
  ],
  "documents": [
    {
      "label": "Issue #61",
      "path": "https://github.com/SpartanLaboratories/WebTools/issues/61",
      "note": "the unit as #55 filed it"
    },
    {
      "label": "PR #74",
      "path": "https://github.com/SpartanLaboratories/WebTools/pull/74",
      "note": "the merged change, three commits"
    },
    {
      "label": "Research",
      "path": "research/README.md",
      "note": "two questions, verification and summary; findings in `testkit-harness.md` and `multiplatform-plugin-wiring.md`"
    },
    {
      "label": "plan.md",
      "path": "plan.md",
      "note": "the implementer's spec, the first version with amendments 1 and 2"
    },
    {
      "label": "final-implementation.md",
      "path": "final-implementation.md",
      "note": "the as-built record: what was built, the deviations, every measurement"
    },
    {
      "label": "Impact",
      "path": "impact.md",
      "note": "published artifacts, blast radius, adoption, what units 2, 3, 4 and 10 build on"
    },
    {
      "label": "#55 architecture",
      "path": "../architecture.md",
      "note": "§6.1 shared build logic, §10 alternatives, §11 unit 1"
    },
    {
      "label": "#55 tracking board",
      "path": "../dashboard.html",
      "note": "the eleven units in landing order"
    },
    {
      "label": "Gradle build page",
      "path": "../../../dashboards/gradle-build/index.html",
      "note": "the system this change built, as it is at 1e27071"
    }
  ],
  "notes": {
    "paths": "Line references marked baseline are to `834ce4a`; others are to the as-built tree at `1e27071`.",
    "retrofit": "This board was written after the landing, from the issue folder's research, plan, impact and as-built documents; the run predates the dashboards service. Columns 1 to 3 come from #55's architecture, this unit's research and the plan's first version; amendments 1 and 2 and the two qa passes are recorded in column 4. The qa findings counts come from the pipeline's reports as #55's board recorded them.",
    "corrections": [
      {
        "what": "The function and the release row's accepted option named the sizing; they now say what the work is (a change inside one system), as a board never shows it. The decisions are unchanged.",
        "when": "re-templating, 2026-10-23"
      },
      {
        "what": "Two options #55's documents weighed for this unit were missing from the Considered column and are added, declined as #55 declined them: selecting the level tasks by JUnit tag (#55 research summary) and KGP's built-in `abiValidation` (#55 architecture §10). The considered milestone's note names the sources. No verdict changed.",
        "when": "the rule for a sub-issue of a signed-off tracking plan (Gap and Considered from the parent's documents), 2026-10-25"
      }
    ]
  },
  "issueBoards": {
    "47": "../47-java-23-bytecode/dashboard.html",
    "62": "../62-http-core/dashboard.html"
  },
  "impact": {
    "basis": "as-built",
    "summary": "No downstream effect: the POMs, the Gradle Module Metadata and the jars of the three artifacts are identical to `834ce4a`'s, so no consumer sees a difference and no version moved. Inside the repository, `build-logic` holds the shared configuration and its own level tests, and the later units of #55 build on its plugins.",
    "none": "No downstream impact recorded: the published output is byte-identical to `834ce4a`'s (procedure A), so no version moved.",
    "release": {
      "bump": "none",
      "version": null,
      "note": "a `build:` change; versions stay `2.0.0a` (unreleased), `1.0.0` and `1.0.0`"
    },
    "downstream": [
      {
        "who": "`webtools-udp`, `webtools-scraping`, `webtools-browser` artifacts",
        "kind": "artifact",
        "system": "gradle-build",
        "effect": "none",
        "summary": "Nothing changes: POM, module metadata and jars identical to the baseline's.",
        "link": "impact.md#1-published-artifacts"
      }
    ],
    "adaptation": [
      {
        "who": "Consumers of the three artifacts",
        "system": null,
        "need": "none",
        "summary": "Nothing to adopt.",
        "link": "impact.md#1-published-artifacts"
      }
    ],
    "api": [],
    "conversion": [],
    "changes": [],
    "source": "impact.md",
    "link": "impact.md#1-published-artifacts"
  }
}
);
