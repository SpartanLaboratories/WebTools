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
