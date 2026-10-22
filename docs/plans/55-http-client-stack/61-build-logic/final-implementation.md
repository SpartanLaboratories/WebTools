# Final implementation: `build-logic` (Issue #61)

The as-built record of `SpartanLaboratories/WebTools#61`, unit 1 (`build-logic`) of
[#55's architecture](../architecture.md). It states what was built, where the work departs from
[`plan.md`](plan.md) and why, and the measurements that show it holds. The working tree implements
`plan.md` with amendment 1 (§16) and amendment 2 (§17) applied. What the unit changes for the
repository and for the sibling units is in [`impact.md`](impact.md).

- **Host of every measurement:** Windows 11, Oracle JDK 23.0.2 and the Gradle 9.7.1 wrapper, on
  2026-10-21 and 2026-10-22.
- **Scratch material:** baseline trees, clean exports and throwaway programs (`JarDigest.java`,
  `SuiteNames.java`) lived outside the repository.
- **Nothing published or signed:** no task published to a remote repository, and no signing key was
  used. The publishing samples published only into their own generated workspaces, with their
  `Sign` tasks disabled.
- **Versions moved:** none. `webtools-udp` stays `2.0.0a`; `webtools-scraping` and
  `webtools-browser` stay `1.0.0`.

## 1. What was built

### The included build

- `settings.gradle.kts` starts with `pluginManagement { includeBuild("build-logic") }`. The foojay
  0.8.0 plugin and the `include(...)` line are unchanged.
- `build-logic/settings.gradle.kts` names the build `build-logic` and resolves from the Gradle
  Plugin Portal and Maven Central.
- `build-logic/build.gradle.kts` is a `kotlin-dsl` build.
  - It declares four plugin markers at literal versions: Kotlin JVM and Kotlin Multiplatform
    2.2.0, vanniktech 0.36.0 and binary-compatibility-validator 0.18.2.
  - Its own tests use the JUnit 5.11.4 BOM, `junit-jupiter` and `junit-platform-launcher`.
  - It registers one test task per level that has tests, plus `test` (see "`build-logic`'s own
    tests" below).
- **`WebToolsBuild`**, a public `object` in the default package, holds `LEVEL_PACKAGE_ROOT`, the
  ordered `LEVELS` (seven `TestLevel`s) and `registerLevelTasks(project, classesDirs, classpath)`.
  - That function registers `gatingTest` … `uatTest` as plain `Test` tasks in the `verification`
    group.
  - Each runs on the JUnit Platform and selects `com.spartanlabs.testing.<level>.*`, with
    `isFailOnNoMatchingTests = false`.
  - None of them is in `check`.
- **`webtools.published-library`** applies vanniktech, declares `mavenCentral()` and calls
  `publishToMavenCentral()`, `signAllPublications()` and the shared `pom {}`.
  - It never calls `configure(...)`, so a Kotlin/JVM module keeps vanniktech's auto-detected
    `KotlinJvm(Javadoc(), Sources())`.
  - Its header names what a release needs, by property key only.
- **`webtools.kotlin-jvm-library`** applies the Kotlin/JVM plugin and the publishing plugin.
  - It adds `kotlin("test")`, `kotlin("test-junit5")` and logback 1.5.6 as test dependencies.
  - It puts every `Test` task on the JUnit Platform and registers the level tasks over the `test`
    source set.
  - It sets no `jvmTarget` and no toolchain, and marks where the Java floor goes
    (`// TODO(#47)`).
- **`webtools.kotlin-multiplatform-library`** applies the multiplatform plugin, the publishing
  plugin, vanniktech (listed again for its own `mavenPublishing {}` accessor) and
  binary-compatibility-validator. It adds:
  - a `jvm()` target whose `TargetJvmVersion` attribute is the build JDK's major version, marked
    `TODO(#47)` for `WebToolsBuild.JAVA_FLOOR`;
  - the level tasks over the `jvm` test compilation, and a lifecycle `test` task that depends on
    `jvmTest`;
  - `kotlin("test")` in `commonTest`, and `kotlin("test-junit5")` plus logback in `jvmTest`;
  - the validator on its defaults, with ASM 9.9.1 pinned on its `bcv-rt-jvm-cp` worker classpath;
  - `configure(KotlinMultiplatform(JavadocJar.Empty(), SourcesJar.Sources()))`.

  No module applies it yet.

### The repository build

- **The root `build.gradle.kts`** keeps only `allprojects { group; version }`, under a comment that
  points at `build-logic` and at the README's "Build & test" section. It has no `build`, `check` or
  `test` task.
- **`webtools-udp`, `webtools-scraping` and `webtools-browser`** each apply
  `id("webtools.kotlin-jvm-library")` and declare their own `api("org.slf4j:slf4j-api:2.0.13")`.
  - In `webtools-udp`, the header comment is the only existing text that changes. Its `2.0.0a`
    note, `version = "2.0.0a"` (`:112`) and port lock (`:114-128`) are byte-identical.
  - The two legacy modules have no `version` line.
- **Level tasks.** Every module keeps the seven level tasks, with their names, group and
  descriptions minus the `(@Tag("…"))` suffix. Each selects by package. The 95 `@Tag` annotations
  stay, now redundant.
- **The README's "Build & test" section** states:
  - the package-selection rule;
  - the included build and its three plugin ids;
  - what a module's build file adds;
  - `build-logic`'s level tasks, with `:build-logic:test` as its pre-commit gate;
  - the on-demand nested-build tasks, the TestKit home and its need for the network.

### `build-logic`'s own tests

They live under `build-logic/src/test/kotlin/com/spartanlabs/testing/<level>/webtools/buildlogic/`,
one class per file.
- Each level that has tests has a task of its own name, and every one of these tasks fails on an
  empty run.
- None is in `build-logic`'s `check`, and `./gradlew build` runs none of them.

| Level | Task | Class | Tests |
|---|---|---|---|
| 1 gating | `gatingTest`, `test` | `BuildLogicPluginsGatingTest` | 2 |
| 2 component | `componentTest`, `test` | `RegisterLevelTasksComponentTest` | 7 |
| 3 integration | `integrationTest` | `LifecycleTestTaskIntegrationTest` | 2 |
| 3 integration | `integrationTest` | `LevelSelectionIntegrationTest` | 6 |
| 3 integration | `integrationTest` | `JvmLevelSelectionIntegrationTest` | 7 |
| 3 integration | `integrationTest` | `ApiValidationIntegrationTest` | 5 |
| 3 integration | `integrationTest` | `PublishedJvmVersionAttributeIntegrationTest` | 2 |
| 3 integration | `integrationTest` | `PublishingConventionIntegrationTest` | 6 |
| 4a deterministic | `deterministicTest`, `test` | `WebToolsBuildLevelsDeterministicTest` | 6 |
| 4b e2e | `e2eTest` | `IncludedBuildEndToEndTest` | 4 |
| 4c nonfunctional | `nonfunctionalTest` | `ConfigurationCacheReuseNonFunctionalTest` | 1 |
| 4c nonfunctional | `nonfunctionalTest` | `JvmConfigurationCacheReuseNonFunctionalTest` | 1 |

- **`test`** runs the gating, component and deterministic levels: 15 tests, in seconds. It is
  `build-logic`'s pre-commit gate.
- **The harness** lives in `…testing.support.webtools.buildlogic`: `SampleProject`,
  `ProjectWorkspace`, `NestedBuild`, `TestReports`, `ModuleMetadata`, `PomFile` and
  `BuildLogicSource`.
  - Generated projects live under `build-logic/build/testkit/projects/`.
  - Every nested build uses one TestKit directory, `build-logic/build/testkit/home`, which is also
    its Gradle user home.
  - `TestReports` takes each class name from the `classname` attribute of a report's `<testcase>`
    elements.
- **The nested-build tasks** (`integrationTest`, `e2eTest`, `nonfunctionalTest`) each take the
  single-permit build service `testKitHomeLock`, so Gradle runs them one after another.

## 2. Deviations from the plan

The working tree follows the amended plan to the letter: every §16 and §17 item was applied as
written, each find text matched once, and every replaced or created file is the plan's block byte
for byte. What departs from the plan's first version, and where each case stands, follows.

### The TestKit home's location (superseded)

- **What the first version said:** the nested builds use TestKit's default directory,
  `build-logic/build/tmp/integrationTest/.gradle-test-kit`.
- **What was measured:** `build-logic/build/tmp/integrationTest/work/.gradle-test-kit`, the `work`
  subdirectory of the task's temporary directory. `NestedBuild`'s KDoc then named the measured
  path, so that it named no directory nothing writes to.
- **Where it stands:** amendment 1 replaced the default with one explicit directory,
  `build-logic/build/testkit/home`.
  - `NestedBuild` passes it to `GradleRunner.withTestKitDir(...)` from the system property
    `webtools.testkit.home`, which every nested-build task sets.
  - The default is per task, so each nested-build task would have filled a home of its own; the
    shared one holds about 910 MB.
  - With the configuration cache, Gradle ran the three tasks at the same time, so amendment 2 has
    them take `testKitHomeLock`.
  - `NestedBuild`'s KDoc, the README and the plan name `build-logic/build/testkit/home`.

### Procedure A's "after" side is a clean export

- **What differs:** procedure A compares each module's POM, module metadata and jars with a
  `834ce4a` baseline.
  - Built in the working tree, the three `-sources.jar` files differed in four entries:
    `udp/General.kt`, `scraping/Connector.kt`, `scraping/ResultExtensions.kt` and
    `browser/WebViewer.kt`.
  - The difference is line endings only. Those four files are CRLF in this checkout
    (`git ls-files --eol`: `i/lf w/crlf`), which this change does not touch, while `git archive`
    gives LF.
- **What was measured instead:** the "after" side of record is built from `git archive HEAD` with
  this change's files overlaid in LF. That is what a commit holds under `.gitattributes`'
  `*.kt`/`*.kts eol=lf`.
- **Why:** the working-tree run measured the checkout's line endings, not the move onto the
  convention plugins.

### No test suite of the three modules runs

- **What the first version planned:**
  - procedure B ran every level task of every module on both trees and compared the results;
  - C ran `./gradlew build`;
  - D and E ran module test tasks;
  - F(3) ran `webtools-udp`'s socket-bound tasks in parallel.
- **Why it changed:** the testing rule the repository follows runs only the tests that exercise a
  change, and never a suite that belongs only to a module the change leaves untouched. This change
  touches neither the three modules' code nor their tests, and identical published output
  (procedure A) shows them unaffected.
- **What replaced it,** now the plan's own procedures (plan §9):

  | Procedure | Replacement |
  |---|---|
  | B | Each module's seven level tasks run with `--test-dry-run` on both trees. Every selected class is reported as skipped and no test body runs. The selections are compared class by class. |
  | C | `./gradlew assemble testClasses` and `./gradlew build --dry-run`. |
  | D | `./gradlew gatingTest componentTest --dry-run` twice. The second run reuses the configuration-cache entry that holds the modules' level tasks. |
  | E | `./gradlew build --dry-run --warning-mode all --no-configuration-cache`. |
  | F(3) | Not run. F(1) and F(2) show the port lock's lines unchanged and its task names resolving. Its serialising effect is not observed (§7). |

- **What R8's equivalence rests on** (the package filter selects what the tags selected):
  - the `--test-dry-run` comparison;
  - the static count: 95 tagged classes, each with one class-level `@Tag` equal to its level
    package, plus 4 untagged `testing/support/` files that hold no test;
  - the Level-2 and Level-3 checks of the level tasks' wiring.

### Plan assumptions that did not hold

- **Where a report names its class.**
  - Amendment 1's `TestReports` read `<testsuite name>`.
  - A multiplatform module's `jvmTest` is KGP's `KotlinJvmTest`, which names the suite
    `<SimpleName>[jvm]`, for example `UnleveledTest[jvm]`. So two `jvmTest` assertions failed, in
    `LifecycleTestTaskIntegrationTest` and `LevelSelectionIntegrationTest`.
  - Its report file name and each `<testcase classname>` keep the package-qualified name. A plain
    `Test` task writes that name in `<testsuite name>` as well. A scratch composite outside the
    repository (a copy of `build-logic` plus one multiplatform module) showed this.
  - `TestReports` now reads `<testcase classname>` (amendment 2). File names stay unused, because
    Gradle shortens a report file name longer than 106 bytes and appends a hash.
- **Tasks of one project running at the same time.**
  - With the configuration cache, Gradle ran `integrationTest`, `e2eTest` and `nonfunctionalTest`
    at the same time. Their reports started in the same second, on the cold and the warm run, and
    all three shared the TestKit home.
  - Nothing failed.
  - The tasks now take `testKitHomeLock` (amendment 2), so the nested builds of only one task at a
    time work on the home.

## 3. Acceptance

| Plan §1 acceptance criterion | Evidence (§4) |
|---|---|
| 1. `assemble testClasses` and `build --dry-run` succeed; under `--test-dry-run` each module's level tasks select the classes they selected on `834ce4a`; no module test body runs | procedures C and B |
| 2. The published output is identical | procedure A |
| 3. A second invocation reuses the configuration cache; no "loaded multiple times" warning | procedures D and E |
| 4. Every `build-logic` test task is green from the root, and each fails on an empty run | procedures G and H |
| 5. `webtools-udp`'s `2.0.0a` note, version line and port lock are unchanged | procedure F(1) |

## 4. Measurements

### Procedure A — published-output identity

- **Baseline:** `git archive 834ce4a | tar -x -C <scratch>/baseline`. The task names were
  re-confirmed with `./gradlew :webtools-scraping:tasks --all :webtools-browser:tasks --all`:
  `jar`, `sourcesJar`, `plainJavadocJar`, `generatePomFileForMavenPublication` and
  `generateMetadataFileForMavenPublication`.
- **Command, both sides, one invocation:** `./gradlew` with `:<m>:jar :<m>:sourcesJar
  :<m>:plainJavadocJar :<m>:generatePomFileForMavenPublication
  :<m>:generateMetadataFileForMavenPublication` for each `<m>` of `webtools-udp`,
  `webtools-scraping` and `webtools-browser`. Green on both sides.
- **After side of record:** a clean export of `HEAD` plus this change, in LF (§2).
- **Result: no difference in any of the nine file groups.**

| Module | `build/libs` names | `pom-default.xml` (`cmp`) SHA-256 | `module.json` minus size/checksum lines | Jar entries (main / sources / javadoc), all identical |
|---|---|---|---|---|
| `webtools-udp` | equal | identical, `8d31a311ddc5bd6a031474195fb8714f4ed3119abfcc4e7c26e8e41ee3414f4e` | no diff | 100 / 37 / 1 |
| `webtools-scraping` | equal | identical, `161561b40ccda3fa83c719a41aac568b0e8e721c3ca5fd4a6c3f977d557862d8` | no diff | 6 / 3 / 1 |
| `webtools-browser` | equal | identical, `1f02ee8bf8be6ae14bd3ee329eaca3bc327bb3e4f16ec694b479ca24fb3f6611` | no diff | 4 / 2 / 1 |

- **Working-tree run, not of record:** the POMs, the metadata and the main and javadoc jars are
  identical. The three sources jars differ in CR bytes only (§2).
- **Not repeated after the amendments:** amendment 1 changes only comments in the files the modules
  read, and amendment 2 changes only `build-logic`'s own test tasks and harness.

### Procedure B — level selection under `--test-dry-run`

- **Trees:** `<scratch>/baseline` (re-verified equal to `git archive 834ce4a`, build directories
  excluded) and the repository working tree. Every `*/build/test-results` was deleted first in
  each.
- **Command, in each tree:**
  `./gradlew gatingTest --test-dry-run componentTest --test-dry-run integrationTest --test-dry-run deterministicTest --test-dry-run e2eTest --test-dry-run nonfunctionalTest --test-dry-run uatTest --test-dry-run --continue`.
  Exit 0 on both (baseline 6 s, working tree 19 s); all 21 module level tasks ran.
- **Collected** with `SuiteNames.java` from plan §9, per module and side.
- **Result: identical on both sides for every module.**

| Module | Classes | Per task | Tests reported |
|---|---|---|---|
| `webtools-udp` | 92 | gating 19, component 21, integration 20, deterministic 9, e2e 11, nonfunctional 11, uat 1 | 641 |
| `webtools-scraping` | 1 | component 1 | 11 |
| `webtools-browser` | 2 | gating 1, uat 1 | 5 |

- **No test body ran:** across all reports on each side, 657 tests, 657 skipped, 0 failures and
  0 errors.
- **Cross-check:** the 657 matches the one executed run of the same level tasks on the `834ce4a`
  baseline: 642 executed and 15 `@Disabled`.

### Procedure C — root build

- `./gradlew assemble testClasses`: exit 0.
- `./gradlew build --dry-run`: exit 0. The graph lists each module's `test`, `check` and `build`.

### Procedure D — configuration-cache reuse

`./gradlew gatingTest componentTest --dry-run` twice: exit 0 both times. The second run prints
`Reusing configuration cache.` and lists the modules' `gatingTest` and `componentTest` as
`SKIPPED`.

### Procedure E — warnings

`./gradlew build --dry-run --warning-mode all --no-configuration-cache`: exit 0. There is no
`loaded multiple times` and no deprecation or warning line.

### Procedure F — the `webtools-udp` port lock

1. `git diff webtools-udp/build.gradle.kts` shows one hunk, `@@ -2 +2,12 @@` (12 lines added, 1
   removed): the header rewrite plus the `plugins {}` and `dependencies {}` blocks. Nothing from
   the `2.0.0a` note to the end of the file changes.
2. Configuration succeeds (`assemble`, `:webtools-udp:tasks --group verification`), so the lock's
   `tasks.named<Test>(...)` calls resolve. The listing shows the seven level tasks with their
   descriptions minus the `(@Tag("..."))` suffix, for example
   `gatingTest - Level 1 - local pre-commit gating tests.`
3. Not run (§2).

### Procedure G — every `build-logic` task from the root

**Commands.**
- Fast tasks: `./gradlew :build-logic:test --rerun :build-logic:gatingTest --rerun :build-logic:componentTest --rerun :build-logic:deterministicTest --rerun`.
- Nested-build tasks: `./gradlew :build-logic:integrationTest --rerun :build-logic:e2eTest --rerun :build-logic:nonfunctionalTest --rerun --continue`.

**Fast tasks.** All pass in both runs: `test` 15 (gating 2, component 7, deterministic 6),
`gatingTest` 2, `componentTest` 7 and `deterministicTest` 6.
- The amendment-1 run took 3.8 s.
- The amendment-2 run took 28 s, because the configuration cache was rebuilt (`build.gradle.kts`
  had changed) and `compileTestKotlin` reran.

**Nested-build tasks.**

| Run | `integrationTest` | `e2eTest` | `nonfunctionalTest` | Wall | TestKit home after |
|---|---|---|---|---|---|
| Amendment 1, cold (home absent) | 28 tests, 2 failed | 4/4 | 1/1 | 89 s, tasks at the same time | 909 MB |
| Amendment 1, warm | 28 tests, 2 failed | 4/4 | 1/1 | 48 s, tasks at the same time | 910 MB |
| Amendment 2, warm | 28/28 | 4/4 | 1/1 | 96 s, tasks one after another | 911 MB |

- **The two failures** of the amendment-1 runs are the `jvmTest` assertions of §2, "Where a report
  names its class". They pass in the amendment-2 run.
- **Per class, seconds:**

| Class | Tests | Amendment 1 cold | Amendment 1 warm | Amendment 2 warm |
|---|---|---|---|---|
| `ApiValidationIntegrationTest` | 5 | 42.8 | 6.7 | 15.1 |
| `JvmLevelSelectionIntegrationTest` | 7 | 9.5 | 6.7 | 7.3 |
| `LevelSelectionIntegrationTest` | 6 | 8.0 | 18.1 | 6.5 |
| `LifecycleTestTaskIntegrationTest` | 2 | 6.4 | 6.5 | 5.3 |
| `PublishedJvmVersionAttributeIntegrationTest` | 2 | 2.7 | 2.8 | 2.5 |
| `PublishingConventionIntegrationTest` | 6 | 7.9 | 5.4 | 5.3 |
| `IncludedBuildEndToEndTest` | 4 | 86.9 | 39.1 | 35.1 |
| `ConfigurationCacheReuseNonFunctionalTest` | 1 | 42.6 | 15.2 | 13.7 |

- **The nested-build tasks run one after another** in the amendment-2 run. Sorted by
  `<testsuite timestamp>`, the eight `TEST-*.xml` reports under `build-logic/build/test-results/`
  do not interleave. Gradle chose the order `nonfunctionalTest`, `integrationTest`, `e2eTest`. Each
  task's span runs from its earliest `timestamp` to its latest `timestamp` plus that report's
  `time`:

  | Task | Start | End | Span |
  |---|---|---|---|
  | `nonfunctionalTest` | 20:16:34.014 | 20:16:47.7 | 14 s |
  | `integrationTest` | 20:16:48.578 | 20:17:30.7 | 42 s |
  | `e2eTest` | 20:17:31.390 | 20:18:06.5 | 35 s |

  About a second separates one task's end from the next one's start. The 96 s wall, against 48 s
  for the amendment-1 warm run, is the serialisation: the spans now add up instead of overlapping.
- **A cold run with the lock** was not made, because the convention plugins are unchanged. The
  amendment-1 cold per-class times add up to about 3.5 minutes.
- **The included-build path:** `IncludedBuildEndToEndTest`'s `the Kotlin Gradle plugin is loaded
  once` passed, and its captured output holds no `loaded multiple times`.
- **Heap and timeout:** the nested daemon's `-Xmx2g -XX:MaxMetaspaceSize=1g` and the 30-minute
  task timeout held in every run. No generated project was left under
  `build-logic/build/testkit/projects`.
- **No credentials reach a sample:**
  - the publishing samples published only to `build/repo` inside their own workspaces, with their
    `Sign` tasks disabled;
  - the TestKit home holds no `gradle.properties`;
  - the environment has no `ORG_GRADLE_PROJECT_*` variable (names checked, no value read).
- **The first runs** of the plan's first version (five TestKit classes) already established:
  - the `:build-logic:` task path from the root;
  - the multiplatform plugin under `withPluginClasspath()`, and vanniktech under the injected
    classpath;
  - KGP supplying the JUnit Platform launcher to `jvmTest`;
  - `org.gradle.jvm.version` = 23 on both `module.json` files' `jvmApiElements-published` and
    `jvmRuntimeElements-published`;
  - `apiDump` passing on this JDK 23 host with the ASM 9.9.1 pin.

  Their cold run took 83 s (`ApiValidationIntegrationTest` 47.1 s), and the TestKit home grew to
  about 199 MB.

### Procedure H — every `build-logic` task fails on an empty run

- `./gradlew :build-logic:gatingTest --tests 'com.spartanlabs.testing.gating.NoSuchClass'`: exit 1,
  `No tests found for given includes: [com.spartanlabs.testing.gating.*](filter.includeTestsMatching) [com.spartanlabs.testing.gating.NoSuchClass](--tests filter)`.
- `./gradlew :build-logic:componentTest --tests 'com.spartanlabs.testing.component.NoSuchClass'`:
  exit 1, with the same message for the `component` package.
- The other four level tasks are registered by the same function.

## 5. Level 5 — the manual pass

The Level-5 pass is narrowed to the following, and qa pass 2 performs it:
1. Read the README's "Build & test" section against the actual layout.
2. Run `./gradlew :build-logic:test`, then
   `./gradlew :build-logic:integrationTest :build-logic:e2eTest :build-logic:nonfunctionalTest`.
3. Run `./gradlew build --dry-run`.
4. Run `./gradlew gatingTest componentTest integrationTest deterministicTest e2eTest nonfunctionalTest uatTest --dry-run`.
   This shows the task graph only, and no module test runs.

## 6. Research that didn't hold

- **TestKit's default directory**
  ([`research/testkit-harness.md`](research/testkit-harness.md) F8, and the research summary).
  - *The claim:* the research derived `build-logic/build/tmp/<task>/.gradle-test-kit` from the
    `GradleRunner` Javadoc: "When executed from a Gradle Test task, the Test task's temporary
    directory is used".
  - *What held and what didn't:* verification re-opened the quote, and it holds. The path derived
    from it does not: Gradle 9.7.1 put the directory one level deeper, at
    `build/tmp/<task>/work/.gradle-test-kit`. Why Gradle resolves it there was not investigated.
  - *What it shaped:* the TestKit home path in the plan's first version, `NestedBuild`'s KDoc and
    this unit's `impact.md`.
  - *What it cost:* one KDoc correction; no run failed. The explicit home
    `build-logic/build/testkit/home` has since replaced the default.

## 7. Known gaps

- **The port lock's serialising effect is not observed.** Observing it would run `webtools-udp`'s
  socket-bound suites. The evidence is the lock's unchanged lines and its resolving task names.
- **KGP's copying of the `jvm` target's attribute onto the classpaths** is read in KGP 2.2.0's
  source, not exercised. With today's value, the build JDK's version, it changes no resolution.
  Unit 2 asserts it.
- **The `.module` size and checksum lines** are excluded from procedure A's byte comparison,
  because they depend on jar timestamps. Identical jar entries imply that they agree; it is not
  asserted.
- **Whether a Kotlin or Gradle daemon holds generated-project files on Windows** is not
  established. No run left a generated directory behind, and the harness-owned directory makes it
  harmless either way.
- **Not exercised:** JDKs other than Oracle 23, Linux, and IntelliJ import of `build-logic`.

## 8. qa pass 2 — minor fixes

qa pass 2 passed the code review and the narrowed Level-5 pass and requested six minor fixes (five
wording, one test, no production code). They were applied on the spot; there is no third pass.

1. `PomFile.parse`: a comment above the `disallow-doctype-decl` feature, as `TestReports` has, and
   its `@return` names the DOCTYPE case.
2. `NestedBuild`'s companion KDoc describes the holder, the system property and the class logger,
   not the constant.
3. `webtools.kotlin-jvm-library.gradle.kts`: the test-dependency comment names where the
   dependencies moved from, the root `build.gradle.kts` at `834ce4a` (`:25-33`). Mirrored in
   plan.md §6.6, and recorded as a third edit in §16.4.
4. `SampleProject`'s companion is grouped in `//region` blocks: constants, factories, script
   templates. Mirrored in plan.md §16.8; the file is still that block byte for byte.
5. README plugin table: the multiplatform row names binary-compatibility-validator (commit
   `api/<module>.api`) and the `TargetJvmVersion` attribute. Mirrored in plan.md §6.11, and
   recorded as a third edit in §16.7.
6. New Level-4c class `JvmConfigurationCacheReuseNonFunctionalTest` (1 test): `componentTest`
   twice on the Kotlin/JVM sample, the second run reusing the configuration cache. The pattern is
   `ConfigurationCacheReuseNonFunctionalTest`'s. Listed in plan.md §9 Level 4c and in the table of
   §1; `nonfunctionalTest` runs 2 tests, the TestKit levels 34, and `build-logic` 49 in all.

Test result, after the fixes:
- `./gradlew :build-logic:test --rerun`: BUILD SUCCESSFUL, 15 tests, 0 failed.
- `./gradlew :build-logic:nonfunctionalTest --rerun`: BUILD SUCCESSFUL, 2 tests, 0 failed
  (`ConfigurationCacheReuseNonFunctionalTest` 16.8 s, `JvmConfigurationCacheReuseNonFunctionalTest`
  6.8 s).

No module test task ran.

### Deviations

- Items 1 and 2 change `PomFile.kt` and `NestedBuild.kt` without a mirror in the plan, as the fix
  list asked, so those two files now differ from the §16.14 and §16.10 blocks by the comment lines
  above. §2's "byte for byte" holds for every other replaced or created file.
- `JvmConfigurationCacheReuseNonFunctionalTest.kt` has no §16 block; §9 describes it.
- The texts items 3 and 5 change appear in §6.6 and §6.11 only, not in the find/replace pairs of
  §16.4 and §16.7, so each of those sections gained a third pair, and its "Two edits" opening became
  "Three edits", to keep §16 producing the as-built tree.
