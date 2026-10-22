# Issue #61 — `build-logic` (impact)

- **Covers:** `SpartanLaboratories/WebTools#61`, *"Build: move shared configuration into an
  included `build-logic` build with convention plugins and package-selected level tasks"*. It is
  unit 1, `build-logic`, of [#55's architecture](../architecture.md) (§6.1, §11).
- **Level:** 3. This file covers what the repository, its published artifacts and the sibling
  units gain or lose with this unit as built.
  - The file-level detail is in [`plan.md`](plan.md).
  - The as-built record, with every measurement, is in
    [`final-implementation.md`](final-implementation.md).
- **Baseline:** `master` `834ce4a`. A line reference marked "baseline" is to that commit.
- **Related docs:**
  - [`plan.md`](plan.md);
  - [`final-implementation.md`](final-implementation.md);
  - [`research/README.md`](research/README.md);
  - the systems-level [`../impact.md`](../impact.md).

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-udp` | Nothing. The POM, the Gradle Module Metadata and the jars are identical to `834ce4a`'s ([`final-implementation.md`](final-implementation.md) §4, procedure A). | `2.0.0a` (unreleased), unchanged |
| `webtools-scraping` | Nothing, as above. | `1.0.0`, unchanged |
| `webtools-browser` | Nothing, as above. | `1.0.0`, unchanged |

`build-logic` is not published, and no new artifact appears. A consumer of the three artifacts
sees no difference.

## 2. Blast radius in this repository

### Build

- **New: `build-logic/`.** An included build that holds:
  - `WebToolsBuild`: the level list and `registerLevelTasks`;
  - the convention plugins `webtools.published-library`, `webtools.kotlin-jvm-library` and
    `webtools.kotlin-multiplatform-library`;
  - its own tests at Levels 1 to 4c, with one task per level (see "New commands" below).
- **`settings.gradle.kts`** starts with `pluginManagement { includeBuild("build-logic") }`.
  foojay 0.8.0 and the `include(...)` line are unchanged.
- **Root `build.gradle.kts`** keeps only `allprojects { group; version }`. It has no `plugins` or
  `subprojects {}` block, and so no `build`, `check` or `test` task of its own.
- **Module build files.**
  - Each applies `id("webtools.kotlin-jvm-library")` and declares its own
    `api("org.slf4j:slf4j-api:2.0.13")`.
  - `webtools-udp`'s header comment is rewritten. Its `2.0.0a` note, its `version` line and its
    UDP port lock are byte-identical.
- **Level tasks.**
  - Every module keeps the same seven tasks, with the same names, group and descriptions, minus
    the `(@Tag("…"))` suffix.
  - Each task selects by package `com.spartanlabs.testing.<level>.*` instead of by JUnit tag.
  - Under `--test-dry-run` they select the same 95 classes as the tags did on `834ce4a`: 92 in
    `webtools-udp`, 1 in `webtools-scraping` and 2 in `webtools-browser`.
  - The 95 `@Tag` annotations stay, now redundant.
- **`./gradlew build`** does the same work for the three modules. `build-logic` compiles when a
  module first requests its plugins and is cached afterwards. None of `build-logic`'s own tests is
  part of `./gradlew build`.
- **New commands.**
  - `./gradlew :build-logic:test` is `build-logic`'s pre-commit gate. It runs the gating, component
    and deterministic levels, 15 tests in seconds. `:build-logic:gatingTest`,
    `:build-logic:componentTest` and `:build-logic:deterministicTest` run one level each.
  - `./gradlew :build-logic:integrationTest :build-logic:e2eTest :build-logic:nonfunctionalTest`
    runs the 34 TestKit tests on demand, one task after another, in about 96 s warm.
    - Their first run downloads the Kotlin compiler, the test dependencies and the plugin markers
      into `build-logic/build/testkit/home`, which grows to about 910 MB.
    - So that first run needs the network.
  - Every one of `build-logic`'s test tasks fails on an empty run.
- **Plugin versions in one file.** KGP 2.2.0, vanniktech 0.36.0 and binary-compatibility-validator
  0.18.2 are pinned in `build-logic/build.gradle.kts`. The KGP move that Gradle 10 forces
  (KT-78620) is a one-line change there.
- **The multiplatform plugin** exists, and no module applies it until unit 3. Its ABI wiring pins
  ASM 9.9.1 on the validator's worker classpath. The pin is needed while classes compile to the
  build JDK's level, and it is inert once the Java floor lands.
- **The Kotlin/JVM plugin sets no `jvmTarget` and no toolchain.** Class files and
  `org.gradle.jvm.version` follow the build JDK, as they did before this unit. Unit 2 adds the
  floor.
- **Configuration cache.** The build stays compatible with it.
  - A second identical invocation reuses the entry.
  - Every nested build runs with the cache on, and the Level-4c check asserts reuse.

### Source and resources

- No main or test source file of the three modules changes.
- `.gitignore` already covers `build-logic/build/` and `build-logic/.gradle/`, where the TestKit
  projects and the TestKit home live.
- `.gitattributes`' `*.kt`/`*.kts eol=lf` covers `build-logic`'s sources and scripts.

### Documentation

- **`README.md` "Build & test"** gains:
  - the package-selection rule: a test outside `com.spartanlabs.testing.<level>` runs under
    `test` only;
  - a "Shared build logic" subsection with the three plugin ids and what a module's build file
    adds;
  - `build-logic`'s level tasks and its pre-commit gate;
  - the on-demand nested-build tasks, and the TestKit home's need for the network.
- **In-code documentation.**
  - The plugin headers state:
    - what a release needs: the credentials and the signing key, by property key only;
    - that class files follow the build JDK until #47;
    - the KT-78620 deprecation.
  - The harness classes carry KDoc contracts.
- **`docs/dashboards/`** still describes the root build as registering the level tasks "by JUnit
  tag" (`workspace.dsl`, `index.html`). The run's presenter step brings it to the as-built state.
- **`docs/webtools-udp-architecture.md` and `docs/webtools-udp-protocol.md`** are unchanged.

## 3. Adoption

| Site | Verdict |
|---|---|
| Root `subprojects {}` (baseline `build.gradle.kts:17-92`) | Removed. Its content lives in the three convention plugins. |
| `webtools-udp`, `webtools-scraping`, `webtools-browser` | On `webtools.kotlin-jvm-library`, each declaring its own `api(slf4j-api)`. |
| The `webtools-udp` port lock (baseline `webtools-udp/build.gradle.kts:103-117`, now `:114-128`) | Stays in the module, unchanged. |
| The 95 `@Tag` annotations on legacy test classes | Stay, now redundant. Follow-up `remove-redundant-test-tags`, optional. |
| `WebViewerUatTest`'s KDoc citing `./gradlew :webtools-browser:uatTest` | Still valid; task names do not change. |

## 4. Sibling units

What the later units build on, as built. [`plan.md`](plan.md) §11 has the exact seams.

**Unit 2, `java-floor` (#47).**
- **Seams.** It adds `WebToolsBuild.JAVA_FLOOR` beside `LEVEL_PACKAGE_ROOT`, and fills the two
  places marked `TODO(#47)`:
  - **In `webtools.kotlin-jvm-library`,** the marked line between the `plugins {}` block and the
    test dependencies becomes a `java floor` region. The region holds `jvmTarget`, `-Xjdk-release`
    and the Java compatibility settings from `JAVA_FLOOR`, plus `verifyJavaFloor` in `check`. Unit
    2 also drops the header sentence that class files follow the build JDK.
  - **In `webtools.kotlin-multiplatform-library`:**
    - `targetJvmVersion` takes `WebToolsBuild.JAVA_FLOOR`;
    - the same `jvm {}` block gains the compiler options;
    - a `java floor` region after the publishing region registers `verifyJavaFloor` into
      `check`.
- **The floor reaches dependency resolution.**
  - KGP 2.2.0 copies the `jvm` target's attributes onto every configuration of every `jvm`
    compilation, the compile and runtime classpaths included.
  - From unit 2 on, `JAVA_FLOOR` is therefore the `org.gradle.jvm.version` that every `jvm`
    classpath requests: `main`, `test` and unit 4's fixtures compilation.
  - Gradle then rejects any dependency, test-only included, whose metadata declares a higher JVM
    version. That is the check the Java plugin applies to the Kotlin/JVM modules once
    `targetCompatibility = 11`, and every candidate dependency is at or below 11 today.
  - This is read in KGP's source, not exercised. Unit 2 adds a TestKit assertion that the
    sample's `jvmRuntimeClasspath` requests the floor.
- **One TestKit expectation changes.** The private `expectedJvmVersion` of
  `PublishedJvmVersionAttributeIntegrationTest`, `Runtime.version().feature()` today, becomes
  `11`.
- **Its own tests in `build-logic`** go in a level package, because every `build-logic` test task
  fails on an empty run. The existing `check --dry-run` assertions tolerate `verifyJavaFloor` in
  `check`.
- **Versions.** This unit adds no `version` line to `webtools-scraping` or `webtools-browser`, and
  leaves `webtools-udp`'s `2.0.0a` note alone. Unit 2 sets `1.0.0a` and extends the note.
- **The ASM pin** turns inert with the floor. Removing it is optional.

**Unit 3, `http-core` (#62).** It is the first module on `webtools.kotlin-multiplatform-library`.
- **What the plugin gives it:**
  - the `jvm()` target and Maven Central;
  - vanniktech with the pinned multiplatform publication;
  - the shared test dependencies;
  - the level tasks and the lifecycle `test`;
  - the attribute;
  - the validator with the ASM pin.
- **What it declares itself:**
  - `explicitApi()`;
  - its dependencies, its coordinates, and its POM name and description;
  - its `logback-test.xml`.

  It declares no second JVM-based target, which would move the dump to `api/<target>/`.
- **Its ABI dump.** It commits `webtools-http/api/webtools-http.api` with its first public
  declaration, because `apiCheck` fails until a dump exists. It must not apply the validator
  itself, and no ancestor project may.
- **`webtools.kotlin-common-check`.** Unit 3 adds this plugin to `build-logic`.
  - It declares its own repositories, since it does not apply `webtools.published-library`.
  - The markers already in `build-logic/build.gradle.kts` cover it.
  - Its TestKit class goes in the integration package, on the existing harness.
- **Settings.** It adds the two entries `:webtools-http` and `:webtools-http-commoncheck`.

**Unit 4, `test-support` (#63).**
- **The harness.** It reuses:
  - `SampleProject`: its factories, `withFile`, and `withLocalPublishing`, which publishes into
    the sample with its `Sign` tasks disabled;
  - `ProjectWorkspace`;
  - `NestedBuild`, with `runner(vararg args)` for builds it configures further;
  - `TestReports`, `ModuleMetadata` and `PomFile`;
  - `BuildLogicSource` with `NestedBuild(workspace, injectPluginClasspath = false)`, for a check
    that must go through a real `includeBuild`.
- **The shared TestKit home.**
  - A class in the integration or e2e package runs under `integrationTest` or `e2eTest`, which
    already use `build-logic/build/testkit/home`.
  - A task of its own that starts nested builds is registered in `build-logic/build.gradle.kts`.
    It sets the same `webtools.testkit.projects` and `webtools.testkit.home` system properties
    and takes `usesService(testKitHomeLock)`.
- **`runtimeDependencyFiles` is never reassigned.**
  - The multiplatform plugin's level tasks read `testCompilation.runtimeDependencyFiles` once.
  - KGP 2.2.0's `associateWith` adds to the configuration behind it and does not replace the
    property. So the fixtures compilation reaches the level tasks as it reaches `jvmTest`, as
    long as no module script assigns `runtimeDependencyFiles`.
  - Unit 4 confirms it by running its fixtures-using tests through a level task.
- **Open points its plan settles:**
  - **Where the publication check attaches to `check`.** The architecture wires it into the root
    `check`, but the root project has no `check` task: it keeps only `group` and `version`. A root
    lifecycle `check` would change that shape. `webtools-http`, whose publication the check
    covers, has a `check` of its own.
  - **One task or two:** whether the check gets its own task beside `integrationTest`.
  - **Signing:** how its file-repository publish gets past `signAllPublications()`.
    `withLocalPublishing` shows one way: disabled `Sign` tasks.

**Unit 10, `feeds`.** It applies the multiplatform plugin the same way unit 3 does, with its own
dump `webtools-feeds/api/webtools-feeds.api`. Its check module applies unit 3's
`webtools.kotlin-common-check`.

## 5. Neighbouring issues

- **#58 (CI and repository practices).** CI is the first place `build-logic`'s tasks could run on
  every change. Until then they run on demand, with `:build-logic:test` as the pre-commit gate.

## 6. Follow-ups this unit leaves

- **`remove-redundant-test-tags`** (optional): drop the 95 `@Tag` annotations that the package
  filter made redundant.
- **Removing the ASM pin** (optional), once the Java floor has landed or the validator ships a
  newer ASM.
- **Running `build-logic`'s tasks in CI**, once #58 brings CI.
- **The stale port-lock comment.** `webtools-udp/build.gradle.kts:117-118` says the lock lives in
  the module, "not in the root build", wording that predates the move. This unit keeps the lock's
  lines byte-identical, so a later level-1 documentation change rewords it.

## 7. Known gaps

- **The port lock's serialising effect is not observed.** Observing it would run `webtools-udp`'s
  socket-bound suites. The evidence is the lock's unchanged lines (`git diff`) and its task names
  resolving at configuration.
- **KGP's copying of the `jvm` target's attribute onto the classpaths** is read in KGP 2.2.0's
  source, not exercised (§4, unit 2).
- **No cold run with the lock.** Serialised, a cold run of the three nested-build tasks is
  estimated at about 3.5 minutes, from the measured per-class times.
- **Not exercised:** Linux, JDKs other than Oracle 23, and IntelliJ import of `build-logic`.
