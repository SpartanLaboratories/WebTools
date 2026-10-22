# Research: `build-logic` — convention plugins and package-selected level tasks

[SpartanLaboratories/WebTools#61](https://github.com/SpartanLaboratories/WebTools/issues/61) is
the `build-logic` unit of [#55](https://github.com/SpartanLaboratories/WebTools/issues/55)'s
[architecture](../../architecture.md) (§6.1 and §11). The parent research is in
[`../../research/`](../../research/README.md) and
[`../../47-java-23-bytecode/research/`](../../47-java-23-bytecode/research/README.md).

## The work

`build-logic/` is an included build that configures every module. It is reached through
`pluginManagement { includeBuild("build-logic") }`, the first statement of
`settings.gradle.kts`. The root `build.gradle.kts` keeps only `allprojects { group; version }`.
It applies no plugin, so it has no `check` or `build` task of its own.

`build-logic` holds:

- **`WebToolsBuild`**:
  - the ordered level list: `gating`, `component`, `integration`, `deterministic`, `e2e`,
    `nonfunctional`, `uat`;
  - `registerLevelTasks(project, classesDirs, classpath)`. It registers the seven level `Test`
    tasks (`gatingTest` … `uatTest`), each selecting by package filter
    `com.spartanlabs.testing.<level>.*` with `isFailOnNoMatchingTests = false`.
- **`webtools.published-library`**: vanniktech `maven-publish` 0.36.0, `mavenCentral()`,
  `publishToMavenCentral()`, `signAllPublications()` and the shared POM block.
- **`webtools.kotlin-jvm-library`**, applied by `webtools-udp`, `webtools-scraping` and
  `webtools-browser`:
  - Kotlin/JVM 2.2.0;
  - the shared test dependencies (`kotlin("test")`, `kotlin("test-junit5")`, logback 1.5.6);
  - JUnit Platform;
  - the level tasks over the `test` source set.
- **`webtools.kotlin-multiplatform-library`**. No module applies it until the first
  multiplatform module exists. It sets up:
  - Kotlin Multiplatform 2.2.0 with a `jvm()` target only;
  - an explicit `TargetJvmVersion` attribute on that target, set to the major version of the
    JDK running the build until the Java 11 floor replaces it;
  - the shared test dependencies: `kotlin("test")` in `commonTest`, and the JUnit 5 binding
    and logback 1.5.6 in `jvmTest`;
  - JUnit Platform;
  - the level tasks over `jvm().compilations["test"]`;
  - a lifecycle `test` task that depends on `jvmTest`;
  - `configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))`;
  - binary-compatibility-validator 0.18.2: one JVM dump per module, with `apiCheck` in
    `check`.
- **Level-3 Gradle TestKit tests** under `build-logic/src/test/kotlin/…/testing/integration/`.
  - Each builds a minimal `jvm()` project that applies `webtools.kotlin-multiplatform-library`
    and checks:
    - `test` runs `jvmTest`;
    - the level tasks pick tests from `commonTest` and `jvmTest` by package;
    - `apiDump` writes the dump, and `apiCheck` is part of `check`;
    - the `TargetJvmVersion` attribute appears in the published metadata.
  - A dedicated task in `build-logic` runs them; `./gradlew build` does not.
  - The same harness later hosts the publication check for `webtools-http`'s test-fixtures
    variant. That check publishes to a file repository, then resolves `testFixtures(...)` once
    from that repository and once through `includeBuild`. It is wired into `check`.

**Constraints:**

- **Versions and toolchain.**
  - Gradle 9.7.1; Kotlin Gradle plugin 2.2.0; vanniktech 0.36.0;
    binary-compatibility-validator 0.18.2.
  - Plugin versions are pinned once, in `build-logic/build.gradle.kts`. The plugin scripts
    apply plugins by id with no version.
  - The build host is Windows with Oracle JDK 23.
- **Repository conventions.**
  - Literal dependency versions; no version catalog.
  - Tests in packages `com.spartanlabs.testing.<level>.…`, one test class per file, JUnit 5.
- **Published output.** `webtools-udp`, `webtools-scraping` and `webtools-browser` publish
  POMs, Gradle Module Metadata and jars identical to those built from `834ce4a`. No version
  moves, and none of the three modules gets an ABI dump.
- **Configuration cache.** The build stays compatible with it. The maintainer's machine runs
  every build with it on (`org.gradle.configuration-cache=true` in the user-level
  `gradle.properties`).

## Findings that already answer part of this

Each of these was measured on this toolchain. No version has moved since.

- **[`../../research/kmp-build-topology.md`](../../research/kmp-build-topology.md):**
  - **F2:** the included-build topology works. That covers:
    - precompiled script plugins with typed `kotlin {}` and `mavenPublishing {}` accessors;
    - `WebToolsBuild` visible from module build scripts;
    - the three modules publishing identical POMs and metadata;
    - no "Kotlin Gradle plugin was loaded multiple times" warning when plugins are applied by
      id;
    - a consumer's nested composite with its own `build-logic`.
  - **F4:** level tasks over the multiplatform test compilation, selected by package with
    `isFailOnNoMatchingTests = false`, and the lifecycle `test` task.
  - **F5:** vanniktech 0.36.0's multiplatform publication shape.
- **[`../../47-java-23-bytecode/research/java-11-floor.md`](../../47-java-23-bytecode/research/java-11-floor.md)
  F1.** A multiplatform `jvm()` target writes no `org.gradle.jvm.version` unless the
  attribute is set explicitly on the target. A Kotlin/JVM module writes its
  `targetCompatibility`, which defaults to the JDK running the build.
- **[`../../research/kmp-test-fixtures.md`](../../research/kmp-test-fixtures.md) F6–F9.** The
  publication shape that the later test-fixtures check asserts.

## Questions

### 1. How should Level-3 TestKit tests exercise a convention plugin that applies Kotlin Multiplatform, binary-compatibility-validator and vanniktech, and how is their task run?

- **Options:**
  - **Getting the convention plugins into the test build.**
    - `java-gradle-plugin`'s plugin-under-test metadata with `GradleRunner.withPluginClasspath()`.
    - The generated project includes `build-logic` through
      `pluginManagement { includeBuild(...) }`.
    - Driving the real repository build, as MyGameTools' `build-logic` does through a
      repository-root system property.
  - **The generated project.** Its settings and repositories; its `gradle.properties` (the
    Kotlin compiler execution strategy, the configuration cache); test sources in level
    packages under `commonTest` and `jvmTest`.
  - **Checking the published attribute.** Generate the Gradle Module Metadata only
    (`generateMetadataFileFor…Publication`), or publish to a file repository, which brings in
    signing.
  - **Running the tests.** A dedicated `Test` task in `build-logic`, kept out of `test` and
    `check`. From the repository root it is invoked either as `:build-logic:<task>` or with
    `-p build-logic`.
- **Requirements:**
  - Gradle 9.7.1, KGP 2.2.0, vanniktech 0.36.0 and binary-compatibility-validator 0.18.2 on a
    Windows JDK 23 host.
  - Tests in `com.spartanlabs.testing.integration.…`, JUnit 5, one class per file.
  - The tests are not run by `./gradlew build`.
  - The build stays configuration-cache compatible.
  - The tests clean up after themselves on Windows, where a lingering Kotlin or Gradle daemon
    can hold files open.
  - The harness can later host a check that publishes to a file repository and resolves
    `testFixtures(...)` both from that repository and through `includeBuild`. That later
    check is wired into `check`, so the question also covers whether the main build can
    depend on a task of a build included through `pluginManagement`, and what a top-level
    `includeBuild` would change.
- **Evidence that settles it:**
  - The Gradle 9.7.1 TestKit, `java-gradle-plugin`, `kotlin-dsl` and composite-build
    documentation, and Gradle's source where the documentation is silent (plugin builds and
    `Gradle.getIncludedBuilds()`).
  - Kotlin Gradle plugin and Gradle issues about TestKit and injected plugin classpaths.
  - Comparable test suites: MyGameTools' `build-logic` in this organisation, and open-source
    Kotlin builds that test multiplatform convention plugins with TestKit.
- **Findings:** [`testkit-harness.md`](testkit-harness.md)

### 2. How should the multiplatform convention plugin apply binary-compatibility-validator 0.18.2 and configure vanniktech 0.36.0 for a `jvm()`-only module?

- **Options:**
  - **Binary-compatibility-validator.** Applied in the multiplatform plugin's `plugins {}`
    block, per module. Applying it to the root project is ruled out: the root applies no
    plugin, and root application configures every subproject, including the three existing
    modules, which take no ABI dump. Either the default `apiValidation` settings or explicit
    ones (dump directory, KLib validation off).
  - **vanniktech.** An explicit `configure(KotlinMultiplatform(...))` or vanniktech's
    auto-detection.
- **Requirements:**
  - One JVM dump per module, with `apiCheck` in `check`.
  - No dump for the three existing modules.
  - Configuration-cache compatible on KGP 2.2.0 and Gradle 9.7.1.
  - The plugins' marker artifacts sit on `build-logic`'s classpath with literal versions, and
    the scripts apply them with no version.
  - Each multiplatform publication ships a sources jar and an empty javadoc jar.
  - What `apiCheck` does before the first dump exists matters, both to the module that first
    applies the plugin and to the TestKit tests.
- **Evidence that settles it:**
  - The binary-compatibility-validator source, README and changelog at tag 0.18.2:
    - the plugin entry point and its multiplatform configuration;
    - task registration and the dump layout for a single JVM target;
    - behaviour when the dump is missing;
    - configuration-cache and Gradle 9 support;
    - maintenance status.
  - vanniktech's source at tag 0.36.0 (`Platform.kt`, `MavenPublishPlugin.kt`,
    `MavenPublishBaseExtension.kt`).
  - The Gradle Plugin Portal and Maven Central marker coordinates.
- **Findings:** [`multiplatform-plugin-wiring.md`](multiplatform-plugin-wiring.md)

## Verification

### Outside-world claims

Every load-bearing claim was re-opened at its source on 2026-10-21. That covers both findings
files, plus three claims this summary adds about the ASM version below.

**65 claims: 65 hold, none fails, none is unclear.**

| Source | Claims | Result |
|---|---|---|
| [`multiplatform-plugin-wiring.md`](multiplatform-plugin-wiring.md) | 26 | all hold |
| [`testkit-harness.md`](testkit-harness.md) | 36 | all hold |
| The ASM pin below | 3 | all hold |

The three ASM-pin claims:
- ASM 9.9 is the release that adds `Opcodes.V26`, for Java 26 class files
  ([ASM versions](https://asm.ow2.io/versions.html)).
- "A JVM version between 17 and 26 is required to execute Gradle"
  ([Gradle 9.7.1 compatibility](https://docs.gradle.org/9.7.1/userguide/compatibility.html)).
- `asm` and `asm-tree` 9.9 are on Maven Central.

**Notes on claims that hold:**

- **ASM 9.9.1.** It is the 9.9 line's bug-fix release, dated 6 December 2025. Its fixes
  include "OutOfMemoryError when reading invalid class". Its `asm` and `asm-tree` POMs are on
  Maven Central, checked on 2026-10-21.
- **Newer releases.** None of these changes an answer:
  - Gradle 9.8.2 (2026-10-19). The repository stays on 9.7.1, the version every claim is tied
    to.
  - vanniktech 0.37.0 (2026-06-21).
  - ASM 9.10, 9.11 and 9.11.1, which add Java 27 and 28.

  binary-compatibility-validator 0.18.2 is still its latest release.
- **vanniktech 0.36.0's release date.** Its `CHANGELOG.md` heading says 2026-01-13, and its
  GitHub release says 2026-01-18. No claim depends on the date.

### Codebase facts

These were checked against `feat/issue-61-build-logic`:

- **Root build.** The shared `pom {}` block is `build.gradle.kts:68-90`, inside the
  `mavenPublishing` block at `:64-91`.
- **Gradle properties.** The repository's `gradle.properties` holds only
  `kotlin.code.style=official`. Neither it nor the maintainer's user-level `gradle.properties`
  sets a `POM_*`, `mavenCentralPublishing` or `signAllPublications` property. The user-level
  file turns the configuration cache on.
- **`.gitattributes`** covers `*.kt` and `*.kts`, which includes `build-logic`'s sources and
  scripts.
- **`.gitignore`** covers `build/` and `.gradle` at any depth. That includes
  `build-logic/build/`, where the TestKit projects and the TestKit Gradle user home live.
- **MyGameTools' `build-logic`.** The organisation precedent the harness findings cite holds:
  - an `integrationTest` task over the `test` source set, with the integration package
    excluded from `test`;
  - plugin-marker dependencies with literal versions;
  - scripts in the default package.

## Summary

### The included build and its dependencies

- **Topology.**
  - `build-logic` is a `kotlin-dsl` build.
  - Its precompiled script plugins and `WebToolsBuild` sit in the default package.
  - Its `settings.gradle.kts` sets `rootProject.name = "build-logic"` and resolves from the
    Gradle Plugin Portal and Maven Central.
  - The topology itself was measured on this toolchain, including identical publication of the
    three existing modules ([`kmp-build-topology.md`](../../research/kmp-build-topology.md) F2).
- **Plugin dependencies.** `build-logic/build.gradle.kts` declares four plugin marker artifacts
  as `implementation` dependencies with literal versions:
  - `org.jetbrains.kotlin.jvm` and `org.jetbrains.kotlin.multiplatform` 2.2.0;
  - `com.vanniktech.maven.publish` 0.36.0;
  - `org.jetbrains.kotlinx.binary-compatibility-validator` 0.18.2.

  Each Kotlin marker's POM has exactly one dependency, `kotlin-gradle-plugin:2.2.0`, so the
  markers resolve to the same jar as the single artifact. They are used because they name the
  plugin ids the scripts apply, which is also this organisation's convention
  ([`multiplatform-plugin-wiring.md`](multiplatform-plugin-wiring.md) F9, F13).
- **No versions in the scripts.** The scripts apply plugins by id. Gradle forbids versions and
  `apply false` in precompiled script plugins, and takes the plugin from `build-logic`'s
  implementation classpath. Kotlin, the validator and vanniktech therefore share one class
  loader, which the validator needs: it casts the `kotlin` extension to the Kotlin plugin's own
  types (wiring F1, F12).

### `webtools.published-library`

- **What it does.** It applies `com.vanniktech.maven.publish` and declares `mavenCentral()`.
  Through the typed `mavenPublishing {}` accessor it calls `publishToMavenCentral()`,
  `signAllPublications()` and the shared `pom {}` block, today's `build.gradle.kts:68-90`
  unchanged.
- **It never calls `configure(...)`.**
  - vanniktech's own `afterEvaluate` detects the applied Kotlin plugin. It picks
    `KotlinJvm(JavadocJar.Javadoc(), SourcesJar.Sources())` for the three Kotlin/JVM modules,
    which is what the root `subprojects {}` block gets today.
  - An explicit `configure(KotlinJvm())` would switch them to an empty javadoc jar: in 0.36.0 a
    manual `configure` without `javadocJar` defaults to `JavadocJar.Empty()` (wiring F11).
- **Nothing else changes in the publication.**
  - No `mavenCentralPublishing`, `signAllPublications` or `POM_*` Gradle property is set,
    neither in the repository's `gradle.properties` nor in the maintainer's user-level one. So
    the full plugin adds nothing beyond these explicit calls.
  - vanniktech reads `group`, `name` and `version` lazily, so a module's own `version = …`
    assignment after its `plugins {}` block still applies (wiring F10, Pitfall 9).

### `webtools.kotlin-multiplatform-library`: ABI and publishing wiring

- **Plugins.** It applies `org.jetbrains.kotlin.multiplatform`, `webtools.published-library`,
  `com.vanniktech.maven.publish` and `org.jetbrains.kotlinx.binary-compatibility-validator`, in
  that order.
  - vanniktech is listed again even though `webtools.published-library` applies it. That way
    the script's own `mavenPublishing {}` accessor is generated from its own `plugins {}` block,
    and re-applying a plugin is idempotent.
  - Application order does not matter to vanniktech or to the validator: both wire themselves
    through `withPlugin`/`withId` hooks (wiring F2, F12).
- **Publishing.** `mavenPublishing { configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources())) }`
  equals what auto-detection picks for a multiplatform module, and pins it (wiring F11).
- **The validator runs on defaults.** It needs no `apiValidation {}` block: the defaults are
  the dump directory `api`, KLib validation off, and validation on.
  - Applied to one leaf project, its `allprojects {}` touches only that project.
  - Applying it also at the root or on an ancestor fails ("Cannot add a configuration with name
    'bcv-rt-jvm-cp'"). The three existing modules therefore get no dump.
  - A `jvm()`-only module gets `apiDump`, `apiCheck`, `jvmApiBuild`, `jvmApiDump` and
    `jvmApiCheck`. `check` depends on `apiCheck`, and the dump file is
    `<module>/api/<Gradle project name>.api`.
  - The KLib tasks are registered and skipped (wiring F2–F4).
- **`apiCheck` fails until a dump exists.** The failure reads "Expected file with API
  declarations '…' does not exist", with a hint to run `:apiDump`. The first multiplatform
  module therefore commits its dump with its first public declaration. A module with no public
  API dumps a zero-byte file, and `apiCheck` passes against it (wiring F5).
- **The validator's worker classpath gets a newer ASM: `org.ow2.asm:asm` and
  `org.ow2.asm:asm-tree` 9.9.1, added to its `bcv-rt-jvm-cp` configuration.**
  - binary-compatibility-validator 0.18.2 hard-codes ASM 9.6 on that classpath. ASM 9.6 rejects
    class files above Java 22.
  - Until the Java 11 floor compiles `main` to class-file major 55, a multiplatform module built
    on a JDK 23 host produces major 67, and `apiDump` fails. The failure was reproduced against
    the ASM jars, and it is on record from another repository of this organisation on this
    host (wiring F7).
  - ASM 9.9 is the release that reads Java 26 class files. Java 26 is the newest JDK that can
    run Gradle 9.7.1, so the 9.9 line covers every build JDK this build supports. 9.9.1 is that
    line's bug-fix release, and it fixes an `OutOfMemoryError` when reading an invalid class.
  - Once the floor lands, the pin is inert. Gradle's conflict resolution takes the highest
    version on that configuration.
- **Support status.**
  - binary-compatibility-validator is in maintenance mode, and 0.18.2 is its latest release.
  - Gradle 9 is outside its CI matrix. Other projects run it on Gradle 9.6 to 9.8: Okio with
    0.18.2, and kotlinx.serialization with 0.18.1.
  - Its functional tests run with the configuration cache on (wiring F1, F8).
- **Repositories.** The validator resolves ASM and `kotlin-metadata-jvm` from the module's own
  repositories, and the `mavenCentral()` that `webtools.published-library` declares covers them
  (wiring F6).
- **The attribute.** It is set on the `jvm` target through `jvm { attributes { … } }`, with
  `TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE`. A multiplatform `jvm()` target writes none
  otherwise ([`java-11-floor.md`](../../47-java-23-bytecode/research/java-11-floor.md) F1).

### The TestKit harness

- **Plugins are injected with `GradleRunner.withPluginClasspath()`.**
  - `kotlin-dsl` applies `java-gradle-plugin`, which adds `gradleTestKit()` to the `test`
    source set.
  - It also writes `plugin-under-test-metadata.properties` from `main`'s output and runtime
    classpath, which carries the precompiled plugin descriptors and the Kotlin, validator and
    vanniktech jars.
  - Gradle documents exactly this arrangement for a `build-logic` included through
    `pluginManagement` ([`testkit-harness.md`](testkit-harness.md) F1, F2, F12).
- **Alternatives that do not hold up.**
  - *Including the live `build-logic` from a generated project* has a nested build compile and
    write in the directory the outer build is executing in. Gradle documents that builds which
    include the same build may conflict.
  - *Driving the real repository build* exercises nothing: no module applies the
    multiplatform plugin, and it writes into the working tree.
  - *Publishing `build-logic` first* is needed only for `compileOnly` plugin dependencies
    (harness F4).
- **The generated project applies only `id("webtools.kotlin-multiplatform-library")`.**
  - It declares no Kotlin plugin of its own. A second Kotlin plugin class loader trips the
    Kotlin plugin's isolated-classpath check, or its "loaded multiple times" warning.
  - Its `settings.gradle.kts` sets `rootProject.name`, which names the API dump.
  - Its `gradle.properties` sets `org.gradle.configuration-cache=true`, because TestKit ignores
    the user-level `gradle.properties`. It also sets
    `kotlin.compiler.execution.strategy=in-process`, `org.gradle.vfs.watch=false` and a JVM
    heap for the nested daemon.
  - Its sources are one public class in `commonMain`, one test class in a
    `…testing.component` package under `commonTest`, and one in a `…testing.integration`
    package under `jvmTest` (harness F3, F5).
- **What the tests assert.**
  - **Selection:** the test-result XML files under each task's `test-results` directory. A
    level with nothing to run can still end `SUCCESS`, so the task outcome is not the evidence.
  - **The API dump:** its file and content, `apiCheck` failing before `apiDump` and passing
    after it, and `check --dry-run` listing `:apiCheck`.
  - **The attribute:** `org.gradle.jvm.version` on `jvmApiElements-published` and
    `jvmRuntimeElements-published`, compared with the major version of the JVM running the
    tests, which also runs the nested daemon. It is read from:
    - `build/publications/jvm/module.json`, written by `generateMetadataFileForJvmPublication`;
    - `build/publications/kotlinMultiplatform/module.json`, written by
      `generateMetadataFileForKotlinMultiplatformPublication`.

    Neither task signs anything.
  - **Configuration-cache reuse:** a second run of the same command.
  - **No `--warning-mode=fail`:** the Kotlin plugin 2.2.0 prints a Gradle-10 deprecation
    (KT-78620) when `jvm()` is declared (harness F5, F6, F9).
- **The dedicated task.**
  - It is a plain `Test` task named `integrationTest`, registered in
    `build-logic/build.gradle.kts` over the `test` source set.
  - It includes `com.spartanlabs.testing.integration.*` with `isFailOnNoMatchingTests = false`
    and has a timeout. `test` excludes that package, also with `isFailOnNoMatchingTests = false`.
  - It is not part of `build-logic`'s `check`.
  - From the repository root it runs as `./gradlew :build-logic:integrationTest`, the
    invocation Gradle documents for this layout.
  - `./gradlew build` never reaches it. The root has no `build` task, and an included build's
    tasks run only when something asks for them.
  - A `jvm-test-suite` is not used, because a suite cannot share the `test` source directory
    (harness F11, F12).
- **Test dependencies.** JUnit Jupiter through its BOM, plus `junit-platform-launcher` at
  runtime: Gradle 9 requires an explicit launcher for JUnit 5 in a plain `test` source set.
  Reading `module.json` also needs a JSON parser on the test classpath (harness F1, F6).
- **TestKit home and Windows.**
  - The nested builds' Gradle user home is TestKit's default,
    `build-logic/build/tmp/integrationTest/.gradle-test-kit`. It is shared by every test and
    kept between runs, so only the first run downloads the Kotlin compiler and the test
    dependencies, and it needs the network. `clean` resets it.
  - On Windows TestKit turns off file-system watching. JUnit's `@TempDir` fails a test when it
    cannot delete a file, so generated projects live in a harness-owned directory under
    `build-logic/build/`, deleted on a best-effort basis (harness F8, F10).

### For the later publication check

The test-fixtures publication check reuses this harness. A build included through
`pluginManagement` is one of `gradle.includedBuilds`, so a build script in the main build can
depend on `gradle.includedBuild("build-logic").task(":<task>")` without a second, top-level
`includeBuild` (harness F12, F13).

That check's own plan settles three things this unit does not need:
- **Signing.** How its file-repository publish gets past `signAllPublications()`: disabled
  `Sign` tasks, a `-SNAPSHOT` version, or a throwaway key (harness F7).
- **Where it attaches to `check`.** The root project has no `check` task: it keeps only `group`
  and `version`. The module whose publication is checked has a `check` of its own.
- **One task or two.** Whether it gets its own task beside `integrationTest`.

### Where the findings differ

- **ASM version.** The wiring findings name ASM 9.8, the version binary-compatibility-validator
  compiles and tests against. 9.8 reads class files up to Java 25. 9.9 adds Java 26, and every
  JDK from 17 to 26 can run Gradle 9.7.1, so the 9.9 line is the one that covers the whole
  supported range. Its bug-fix release, 9.9.1, is the pin. ASM keeps its 9.x API compatible,
  and the validator only reads class files through the tree API.
- **The `mavenPublishing {}` accessor.** Whether the accessor exists in a script that receives
  vanniktech only through another precompiled plugin is unmeasured. Listing vanniktech in the
  multiplatform plugin's own `plugins {}` block removes the question.

### Known gaps

- **Nothing was run on this toolchain.** The first implementation step is a smoke run that
  confirms what source reading established:
  - the multiplatform plugin applies, and its level tasks are listed;
  - `./gradlew :build-logic:integrationTest` runs from the root;
  - vanniktech works under an injected plugin classpath (no independent evidence);
  - the root `module.json` carries the attribute through TestKit.
- **Whether the Kotlin or Gradle daemon holds files in a generated project on Windows** is not
  established. The harness-owned directory makes that harmless.
- **The nested daemon's heap and the first run's duration** are unmeasured. The heap and the
  task timeout start as estimates, and the first runs set them.
- **Default-package access.** Unlike Java, Kotlin lets code in a named package import a
  root-package declaration (`import WebToolsBuild`). JetBrains'
  [KTIJ-30266](https://youtrack.jetbrains.com/projects/KTIJ/issues/KTIJ-30266) gives
  `package a` / `import X` as the expected result for a default-package `X`. This was not
  compiled here, and only a direct test of `WebToolsBuild` depends on it.
