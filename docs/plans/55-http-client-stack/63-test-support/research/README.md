# Research: the `webtools-http` test-fixtures variant (`test-support`)

[SpartanLaboratories/WebTools#63](https://github.com/SpartanLaboratories/WebTools/issues/63), unit 4
(`test-support`) of the HTTP client stack,
[#55](https://github.com/SpartanLaboratories/WebTools/issues/55). The systems-level design is
[`../../architecture.md`](../../architecture.md) (§6.1, §6.7, §9, §11 unit 4) and the
systems-level impact [`../../impact.md`](../../impact.md) (§7, §8). This folder researches the
implementation details that design leaves to the unit and that need evidence from outside this
repository.

## The work

`webtools-http` publishes its test support as a Gradle test-fixtures variant of itself:

- a JVM-only `testFixtures` compilation on the module's single `jvm()` target, associated with
  `main`, with the `test` compilation associated with it. Its sources are in
  `webtools-http/src/jvmTestFixtures/kotlin`, package `com.spartanlabs.webtools.http.testing`, and its
  compile task runs with `-Xexplicit-api=strict`;
- two consumable configurations, `testFixturesApiElements` and `testFixturesRuntimeElements`. Each
  carries the capability `io.github.spartanlaboratories:webtools-http-test-fixtures:<version>` and
  `org.gradle.jvm.version` = `WebToolsBuild.JAVA_FLOOR` (11). They are added to the root publication
  through the experimental `kotlin.publishing.adhocSoftwareComponent`, mapped with `mapToMavenScope`
  and `mapToOptional`, beside a `webtools-http-<version>-test-fixtures.jar` in the root module's
  directory;
- dependencies on `jvmTestFixturesApi`: the module itself, `kotlinx-coroutines-test` 1.10.2 and
  `mockwebserver3` 5.5.0, and nothing else;
- the contents: `VirtualClock`, `FakeHttpTransport` with `RequestMatch`, `Step` and `Unmatched`,
  `Fixture` and `FixtureLoader` with the `.http` codec, and `FixtureRecorder` with `RecordingGate`
  and `Redaction`.

What shapes the questions below:

- **Toolchain.** Gradle 9.7.1 with the configuration cache on, Kotlin Gradle plugin 2.2.0,
  vanniktech `maven-publish` 0.36.0 with `signAllPublications()` and `publishToMavenCentral()`
  applied by the `webtools.published-library` convention plugin, binary-compatibility-validator
  0.18.2, and a build JDK from 17 to 26. The maintainer's host is Windows 11 with JDK 23.
- **The build's shape.**
  - The root `settings.gradle.kts` includes `build-logic` through
    `pluginManagement { includeBuild("build-logic") }`, and includes five modules.
  - The root `build.gradle.kts` holds only `allprojects { group; version }`. The root project has no
    task of its own: no `check`, `build`, `assemble` or `clean`. `./gradlew build` therefore runs
    `build` in every module and nothing in `build-logic`.
  - `build-logic` is a `kotlin-dsl` build. Its TestKit tests run under its own level tasks
    (`:build-logic:integrationTest`, `:build-logic:e2eTest`, `:build-logic:nonfunctionalTest`), on
    demand. Those tasks share one TestKit home (`build-logic/build/testkit/home`) and a single-permit
    build service, so they never use the home at the same time.
- **The fixtures-publication check.**
  - It is a Gradle TestKit test in `build-logic`'s test source set. It publishes `webtools-http` to
    a build-local file repository, then resolves
    `testFixtures("io.github.spartanlaboratories:webtools-http:<version>")` once from that repository
    and once through `includeBuild`.
  - It asserts the capability, `org.gradle.jvm.version` 11 on both fixtures variants, and that
    neither main variant lists `kotlinx-coroutines-test` or `mockwebserver3`.
  - It is the only check on the experimental publishing API, so it belongs to the verification
    that `./gradlew build` and `./gradlew check` run.
- **The consumers it stands for.**
  - BotTools (`SpartanLaboratories/KotlinBottools`: Kotlin/JVM, JDK 21, Kotlin 2.2.x, Gradle 9.7.1)
    consumes the stack from Maven Central. During development it consumes it through a nested
    composite build that includes a sibling WebTools checkout.
  - `webtools-feeds`, a later Kotlin Multiplatform module of this build, declares
    `jvmTestImplementation(testFixtures(project(":webtools-http")))`.
- **Signing and credentials.**
  - Every publication is signed. A publish to any repository, a local file repository included,
    needs a signing key unless its `Sign` tasks are disabled or its version ends in `-SNAPSHOT`.
  - Nested TestKit builds never see the user-level Gradle properties, where release credentials
    live.
  - No key or credential is committed or read from the maintainer's environment.

## Facts read at source for this unit

Two facts the design needs were read directly at their tags. They are re-opened by the
verification below with the findings files' claims.

1. **`kotlinx-coroutines-test` 1.10.2: what `VirtualClock` can use without an opt-in.**
   - No `@ExperimentalCoroutinesApi` on: `StandardTestDispatcher(...)`, the class `TestDispatcher`,
     `TestScope.testScheduler`, `TestCoroutineScheduler.advanceTimeBy(delayTime: Duration)`,
     `TestCoroutineScheduler.runCurrent()` and `TestCoroutineScheduler.timeSource`.
   - `@ExperimentalCoroutinesApi` on: `TestCoroutineScheduler.currentTime`,
     `TestCoroutineScheduler.advanceTimeBy(delayTimeMillis: Long)`, and the `TestScope` extensions
     `currentTime`, `advanceUntilIdle`, `runCurrent`, both `advanceTimeBy` overloads and
     `testTimeSource`.
   - Sources, tag `1.10.2`:
     [`TestCoroutineDispatchers.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestCoroutineDispatchers.kt),
     [`TestDispatcher.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestDispatcher.kt),
     [`TestScope.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestScope.kt),
     [`TestCoroutineScheduler.kt`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-test/common/src/TestCoroutineScheduler.kt).
2. **OkHttp 5.5.0 decodes query names and values with `+` as a space.**
   - `HttpUrl.Builder.build()` sets
     `queryNamesAndValues = encodedQueryNamesAndValues?.map { it?.percentDecode(plusIsSpace = true) }`.
   - `toQueryNamesAndValues` splits the query on `&`, then on the first `=`; a name without `=`
     has a `null` value.
   - `queryParameterValues(name)` returns every value of `name`, in order.
   - This is the decoding a query matcher in the fixtures follows.
   - Source, tag `parent-5.5.0`:
     [`HttpUrl.kt`](https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/HttpUrl.kt).

## Existing findings this research builds on

These are current: same toolchain, and nothing newer contradicts them. They are cited, not
researched again.

- **[`../../research/kmp-test-fixtures.md`](../../research/kmp-test-fixtures.md).**
  - Recipe B, measured (F6).
  - Consumption by a Kotlin/JVM consumer from a file repository and through `includeBuild`, with
    classpath isolation (F7).
  - The root POM's content with `mapToOptional()` (F8).
  - vanniktech 0.36.0 signing every artifact of the publication, the fixtures jar included, with a
    throwaway key (F9).
  - No sources or javadoc jar for the fixtures; Central hosts such artifacts (F10).
  - `explicitApi()` does not reach the fixtures compilation (F11).
  - Its known gaps that bear on this unit: a Kotlin Multiplatform consumer was not probed, and
    Central's upload and validator were not run.
- **[`../../research/test-support-and-time.md`](../../research/test-support-and-time.md).**
  - The time bundle, the fake's script semantics, the `.http` format and the recorder contract.
  - `-Xexplicit-api=strict` on the fixtures compile task (F5).
  - `@SubclassOptInRequired` binds implementers of a port, not users of a final fake (F4).
  - It answers every question about the fixtures' own types; none is researched again here.
- **[`../61-build-logic/research/testkit-harness.md`](../61-build-logic/research/testkit-harness.md).**
  - The TestKit harness.
  - Three points about a publication check that were read in source and documentation but never
    run:
    - a task of a `pluginManagement`-included build is reachable through
      `gradle.includedBuild("build-logic").task(":<task>")` (F12);
    - a check publishes a copy of the module rather than driving the repository's own build (F4,
      F13);
    - the ways past `signAllPublications()` (Consideration 1).

## Questions

### 1. How does a check that lives in the `pluginManagement`-included `build-logic` build become part of what `./gradlew build` and `./gradlew check` run, while a build that includes the WebTools build in its own composite is unaffected?

- **Options:**
  - `webtools-http`'s `check` depends on `gradle.includedBuild("build-logic").task(":<check task>")`,
    declared in `webtools-http/build.gradle.kts`, either eagerly or inside a lazy
    `tasks.named("check") { … }`;
  - the same, wired only when the WebTools build is the root build, for example guarded by
    `gradle.parent == null`;
  - a lifecycle `check` task in the root `build.gradle.kts` that depends on the included build's
    task: alone, or with a root `build` task (or the `lifecycle-base` plugin) so that
    `./gradlew build` reaches it;
  - any other mechanism Gradle 9.7.1 documents for making an included build's task part of the main
    build's verification.
- **Requirements:**
  - `./gradlew build` and `./gradlew check` from the repository root both run the check. If it hangs
    off `webtools-http`, `./gradlew :webtools-http:check` runs it too.
  - The root build keeps only `allprojects { group; version }` and gains no task, unless no other
    option meets the remaining requirements.
  - The configuration cache stays compatible: a second identical invocation reuses its entry.
  - The check stays incremental. With its inputs unchanged (the module's build file, its main and
    fixtures sources, `build-logic`'s sources and the root build files), a repeated `./gradlew build`
    reports the check `UP-TO-DATE` instead of running it again.
  - A consumer build that includes the WebTools root (`includeBuild("../WebTools")`, as BotTools'
    development composite does) and substitutes `webtools-http` configures and builds as it does
    today. It never runs the check, and no configuration-time error appears, whether or not the
    consumer requests a task of the included build.
  - The root project's task list gains nothing beyond what the chosen option needs.
- **Evidence that settles it:**
  - Gradle 9.7.1's documentation and source for `Gradle.includedBuild(name)`,
    `Gradle.getIncludedBuilds()` and `Gradle.getParent()`, seen from a root build and from an
    included build. That includes an included build that itself includes a plugin build through
    `pluginManagement`. Also the documentation and source for task references across builds in a
    composite.
  - A measured probe on Gradle 9.7.1, the repository's wrapper version, shaped like this
    repository:
    - a root with `pluginManagement { includeBuild("build-logic") }` and
      `allprojects { group; version }`;
    - a `kotlin-dsl` `build-logic` with a stand-in check task: a `Test` task, or a plain task with
      declared inputs and an output;
    - a library module carrying each candidate wiring;
    - a consumer build that includes that root as a composite member and substitutes the module.

    Each run uses `--configuration-cache`, twice, and records what runs, the `UP-TO-DATE`
    behaviour and any error or warning.
- **Findings:** [`publication-check-wiring.md`](publication-check-wiring.md)

### 2. How does a TestKit check publish a copy of the real `webtools-http`, with its test-fixtures variant, to a file repository, and resolve `testFixtures(...)` from it, through `includeBuild` and from a Kotlin Multiplatform module of the same build?

- **Options,** per part:
  - **The producer.**
    - A copy of the module's build file with its main and fixtures sources, as a subproject named
      `webtools-http` of a generated build. The generated root sets `group` and `version` as the
      repository's root does, and includes a copy of `build-logic` through `pluginManagement`.
    - The same, with the convention plugins injected through `GradleRunner.withPluginClasspath()`
      instead of a `build-logic` copy.
  - **Getting past `signAllPublications()`.**
    - `Sign` tasks disabled, from a generated build script or from an init script.
    - A `-SNAPSHOT` version.
    - A throwaway key generated at test time and passed as `signingInMemoryKey`. What that costs:
      a dependency, the code that generates the key, and any reliance on Gradle's own copy of a
      cryptography library.
  - **The file-repository and composite consumers.**
    - A Kotlin/JVM consumer, with the Kotlin Gradle plugin from the injected classpath or resolved
      by version, or a plain Java consumer.
    - Resolution only, or also compiling, and possibly running, a consumer test that uses a fixtures
      class.
  - **The in-build Kotlin Multiplatform consumer.** A second subproject of the producer build with a
    `jvm()` target, declaring `jvmTestImplementation(testFixtures(project(":webtools-http")))`.
  - **Reading the result.**
    - A small report task in each consumer that writes the selected variants, their capabilities and
      attributes, and the resolved files to a file, through `incoming.resolutionResult` and
      `incoming.artifacts` in a configuration-cache-compatible form.
    - `dependencyInsight` output.
    - The generated metadata and POM files, read directly.
- **Requirements:**
  - The published root module's metadata holds `testFixturesApiElements` and
    `testFixturesRuntimeElements`. Each has the capability
    `io.github.spartanlaboratories:webtools-http-test-fixtures:<version>`, `org.gradle.jvm.version`
    11, the `-test-fixtures.jar`, and the dependencies `webtools-http`, `kotlinx-coroutines-test` and
    `mockwebserver3`.
  - Neither main variant lists `kotlinx-coroutines-test` or `mockwebserver3`, in the root or the
    `-jvm` module metadata, and neither does the `-jvm` POM. The root POM lists them only as
    `optional`.
  - The consumers resolve the fixtures variant, the fixtures jar and its dependencies on their test
    classpaths: from the file repository, through `includeBuild`, and from the in-build Kotlin
    Multiplatform module. A consumer without `testFixtures(...)` resolves neither test library.
  - No signing key, credential or machine-local path is committed or read from the maintainer's
    environment. Nothing is published outside the check's own workspace, and Maven Central is never
    contacted for an upload.
  - Every nested build runs with the configuration cache on. The check runs on Windows with JDK 17
    to 26 and shares the TestKit home of `build-logic`'s other nested-build checks.
  - The warm run time, which every `./gradlew build` with changed inputs pays, and the cold run's
    downloads are stated.
- **Evidence that settles it:**
  - A measured probe outside the repository: a copy of this repository's `build-logic` and
    `webtools-http` (its main code and a minimal recipe-B fixtures compilation with one fixtures
    class), published and consumed as above, through Gradle TestKit or with an isolated Gradle user
    home. It records each nested build's outcome, the generated `.module` and POM contents, the
    consumers' resolution, and timings cold and warm.
  - Gradle 9.7.1, Kotlin Gradle plugin 2.2.0 and vanniktech 0.36.0 source and documentation for
    signing (`Sign`, `SigningExtension`, vanniktech's `signAllPublications()` and its in-memory key
    properties), adhoc-component publication, `testFixtures(...)` capability requests, and variant
    selection on a Kotlin Multiplatform consumer's `jvm` classpaths.
- **Findings:** [`publication-check-mechanics.md`](publication-check-mechanics.md)

## Verification

Every load-bearing claim in the two findings files, and the two facts read at source above, was
re-opened at its source on 2026-10-25.

**45 items: 23 hold, none fails, none is unclear, and 22 are probe measurements, which were not
re-run.** The source behaviour each measurement relies on matches the code or documentation at the
cited tag, and nothing at a source contradicts a measured result.

| Source | Items | Result |
|---|---|---|
| [`publication-check-wiring.md`](publication-check-wiring.md) | 20 | 1 to 11 hold; 12 to 20 are measurements (M1 to M9) |
| [`publication-check-mechanics.md`](publication-check-mechanics.md) | 23 | 1, 5, 7, 8, 11, 14 and 16 to 19 hold; 2, 3, 4, 6, 9, 10, 12, 13 and 15 hold at the source and carry a measured half that was not re-run; 20 to 23 are measurements |
| Facts read at source (above) | 2 | both hold |

**Wording notes, none of which changes a result:**

- `RepositoriesMode` and `ConfigurationContainer.consumable` (mechanics claims 10 and 14) are
  `@Incubating` in Gradle 9.7.1.
- vanniktech's `setupTestFixtures` (mechanics claim 15) also suppresses the warning for
  `testFixturesSourcesElements`, a variant the module does not publish.
- In Kotlin's `kmp-jvm-test-fixtures` test project (mechanics claim 19) the consumer is a Kotlin/JVM
  module and the multiplatform module is the producer.
- At `kotlinx-coroutines-test` 1.10.2, `UnconfinedTestDispatcher` is also `@ExperimentalCoroutinesApi`.
  `TestCoroutineScheduler.advanceUntilIdle()` is not, while the `TestScope.advanceUntilIdle` extension
  is. Fact 1 lists only what `VirtualClock` uses and what it avoids.
- androidx/media's `gradle.parent == null` test (wiring claim 11) sits inside an `afterEvaluate`.

**Nothing newer changes an answer:**

- **Gradle.** 9.8.2 is the latest release; the repository's wrapper is 9.7.1. Between `v9.7.1` and
  `v9.8.2` the cited sources are unchanged except for `@since` tags, a rewording that keeps the quoted
  note, and a deprecation of `Sign.sign(String, File...)`, which nothing here uses.
- **vanniktech.** 0.37.0 is the latest release; the repository pins 0.36.0. `signAllPublications()`, the
  `-SNAPSHOT` rule, the in-memory key handling and `setupTestFixtures` are the same in both. 0.37.0
  changes only which checksum files a Central upload carries.
- **BouncyCastle.** `org.bouncycastle:bcpg-jdk18on` 1.86 is still the latest release.
- **Kotlin.** `adhocSoftwareComponent` is still `@ExperimentalKotlinGradlePluginApi` at `v2.4.21` and
  `v2.5.0-Beta2`, with the same "should not match default variants" text.
- **`kotlinx-coroutines-test`.** 1.11.0 is the latest release. No stability annotation in the four cited
  files changed after 1.10.2.
- **OkHttp.** `HttpUrl.kt` is byte-identical between `parent-5.5.0` and `parent-5.5.1`.
- **gradle/gradle#22335**, "Support invoking task selectors on included builds", is still open.

### Codebase facts

The repository facts the findings rest on were checked against `master` at `7de8207`:

- `webtools.published-library.gradle.kts:19-21` declares `repositories { mavenCentral() }` on every
  module that applies it. A file-repository consumer built on the convention plugins therefore needs
  `RepositoriesMode.PREFER_SETTINGS`.
- Each library plugin and the common-check plugin attaches to `check` through `tasks.named("check")`
  (`webtools.kotlin-jvm-library.gradle.kts:43`, `webtools.kotlin-multiplatform-library.gradle.kts:104`,
  `webtools.kotlin-common-check.gradle.kts:49`). `webtools-http`'s `check` therefore exists when its
  build file runs.
- `SampleProject.withLocalPublishing()` disables the `Sign` tasks (`SampleProject.kt:38`, `:318-322`).
  `NestedBuild` always runs in `ProjectWorkspace.dir` and passes no environment of its own
  (`NestedBuild.kt:46-51`), so a nested build inherits the environment of the build running the check.
  `ModuleMetadata` reads variant attributes only, and `PomFile` reads element text by path only.
- A release needs the Central credentials and a signing key as Gradle properties
  (`webtools.published-library.gradle.kts:7-13`). Nested TestKit builds never read the user-level
  properties, so the environment is the one channel through which a release key can reach them.

## Summary

### Wiring the check into `build` and `check`

- **The mechanism.** A build reaches a task of an included build only through a task reference from
  `gradle.includedBuild(name).task(path)`; a string path resolves inside the calling build and fails.
  Task selectors such as `check` do not cross builds (gradle/gradle#22335, open), so neither `build` nor
  `check` reaches `build-logic` on its own. Source: [`publication-check-wiring.md`](publication-check-wiring.md)
  F1, F4, F9.
- **Where the edge hangs.** `webtools-http`'s own `check` depends on the check task, declared lazily in
  `webtools-http/build.gradle.kts`.
  - Measured: `./gradlew build`, `./gradlew check` and `./gradlew :webtools-http:check` all run it.
  - A repeat reuses the configuration cache and reports the check `UP-TO-DATE`.
  - The root project's task list does not change. The root build keeps only `allprojects { group;
    version }`.
- **The root-build guard.** The edge is declared only when `gradle.parent == null`, that is, when
  WebTools is the root build.
  - In a consumer's composite (BotTools' development shape, with its own `build-logic`), every candidate
    was already clean: no error, no warning, and no check on the consumer's `build` or on a dependency on
    the substituted module.
  - The only difference was an explicit `:<included build>:webtools-http:check`, which runs the check
    without the guard and does not run it with the guard.
  - `gradle.includedBuild("build-logic")` resolves per build. Inside WebTools it is always WebTools' own
    plugin build, even beside a consumer's `build-logic`. Gradle gives the two different build paths, as
    long as WebTools' `build-logic` directory sits inside the WebTools directory (F2, F3, F5, F7).
- **The check's inputs.** The task declares as inputs the files it copies from outside `build-logic`:
  the module's build file and its main and fixtures sources, and the root build files. These work for
  up-to-date checks and under the configuration cache.
  - Directories are declared as `inputs.files(fileTree(...))`. `inputs.dir(...)` fails the task when the
    directory is missing.
  - Source edits re-run the check and keep the configuration-cache entry. Build-script edits invalidate
    the entry and re-run the check (F8).
- **What does not fit.** A lifecycle `check` in the root build is not reached by `./gradlew build` or by
  `:webtools-http:check`. A root `build` or the `lifecycle-base` plugin fixes `build`, still misses the
  module's `check`, and adds two or four root tasks that a consumer's composite can then address (F6).

### Publishing a copy of the module and reading what consumers resolve

- **The producer.** A generated build holds a copy of `webtools-http`'s build file and its
  `src/commonMain`, `src/jvmMain` and `src/jvmTestFixtures`, as the subproject `:webtools-http`. Its root
  sets `group` and `version` as the repository's root does.
  - The convention plugins are injected with `GradleRunner.withPluginClasspath()` rather than reached
    through a copy of `build-logic`. Both pass. Injection saves about 25 to 30 s per warm run and a second
    compile of `build-logic`.
  - Injection also reaches the producer when the composite consumer includes it. That is measured on
    Gradle 9.7.1 but undocumented, and a change would fail the check loudly.
  - The `pluginManagement` path itself is covered by `IncludedBuildEndToEndTest` and by every module of
    the repository. Source: [`publication-check-mechanics.md`](publication-check-mechanics.md) F2.
- **Recipe B, on the module's real sources, publishes exactly the specified shape** (F1):
  - the two fixtures variants in the root module's metadata, with the capability, `org.gradle.jvm.version`
    11, the `-test-fixtures.jar` and the dependencies `webtools-http`, `kotlinx-coroutines-test`,
    `mockwebserver3` and the Kotlin plugin's `kotlin-stdlib`;
  - no test library on any main variant or in the `-jvm` POM;
  - the two test libraries as `optional` in the root POM.

  F13 refines the block: `configurations.consumable(...)`, `suppressPomMetadataWarningsFor` on both
  variant names, and `-Xexplicit-api=strict` on `compileTestFixturesKotlinJvm`.
- **No attribute beyond recipe B's.** Kotlin/JVM, Kotlin Multiplatform and Java consumers select the
  fixtures variants without `org.jetbrains.kotlin.platform.type` or `org.gradle.jvm.environment`.
  - A missing attribute is compatible, and only those two variants carry the requested capability
    (F7, F8).
  - This holds for the in-build Kotlin Multiplatform module,
    `jvmTestImplementation(testFixtures(project(":webtools-http")))`, which is the shape `webtools-feeds`
    declares, and for a Kotlin Multiplatform consumer of the published artifacts.
  - The Kotlin Gradle plugin's own documentation asks that added variants not match the default ones, so
    adding the two Kotlin attributes would gain nothing.
- **Consumers.** Three nested builds hold four consumers. Each compiles and runs one test that uses
  fixtures classes, so a missing runtime dependency fails a test:
  - the producer's in-build Kotlin Multiplatform module;
  - a file-repository build with a Kotlin/JVM and a Kotlin Multiplatform module;
  - a composite build that includes the producer.

  The file-repository build declares the repository in its settings with
  `RepositoriesMode.PREFER_SETTINGS` and an `exclusiveContent` filter for the group. Otherwise the
  convention plugins' project-level `mavenCentral()` hides it, and once `webtools-http` 1.0.0 is on
  Central the lookup would silently pass from there (F6).
- **Reading results.** The publication is read from the repository's `.module`, `.pom` and file listing on
  disk. Resolution is read from a small `resolutionReport` task that each consumer applies from a script
  file. The task is built on `incoming.resolutionResult.rootComponent` and
  `incoming.artifacts.resolvedArtifacts` as providers, is configuration-cache compatible, and writes one
  tab-separated file per configuration. `dependencyInsight` is console text and is not asserted on (F9).
- **The assertions catch the faults they exist for.** Removing `mapToOptional()`, removing the
  `TargetJvmVersion` attribute, and putting `mockwebserver3` on the main variant each failed the
  prototype assertions that target them (F10).

### Signing

- **A throwaway key, generated at test time.** It is an RSA-2048 OpenPGP key built with BouncyCastle
  (`org.bouncycastle:bcpg-jdk18on` 1.86, a `build-logic` test dependency, about 30 lines). It reaches the
  producer through its generated `gradle.properties` as `signingInMemoryKey` and
  `signingInMemoryKeyPassword`.
  - vanniktech 0.36.0 accepts it.
  - Every published file gets an `.asc`, `webtools-http-1.0.0-test-fixtures.jar.asc` included. Signing a
    multiplatform publication that carries an adhoc variant is therefore exercised on every run of the
    check, not first at a release (F3, F4).
  - The signature's presence is checked, not its validity.
- **What does not fit.**
  - Disabled `Sign` tasks, from a script or an init script, cost nothing but cannot show that the
    fixtures jar is signed.
  - A `-SNAPSHOT` version publishes timestamped files under a different capability version.
  - Gradle's own BouncyCastle is visible to `build-logic`'s tests only relocated under
    `org.gradle.internal.impldep`, which Gradle's public-API rule says must not be relied upon (F3, F4).
- **The environment of nested builds is scrubbed.** An `ORG_GRADLE_PROJECT_*` variable in the environment
  of the build running the check reaches every nested build and beats the workspace's `gradle.properties`.
  Measured: an `ORG_GRADLE_PROJECT_signingInMemoryKey` broke the producer.
  - A release workflow that exported its key and ran `check` would hand the real key to the nested
    producer.
  - Every nested build of the harness therefore runs with the environment minus `ORG_GRADLE_PROJECT_*`
    (F5).

### Cost

- **Warm:** about one minute when an input changed. Measured: 53 to 78 s over five runs on JDK 17 and 23;
  the nested builds take 39 to 56 s of it, and the producer's compile of the whole module dominates. An
  unchanged input set leaves the check `UP-TO-DATE`.
- **Cold:**
  - 55 s and 38 files (6.2 MB) on top of a TestKit home that `build-logic`'s other nested-build checks
    have already filled;
  - 62 s and 109 files (73 MB) in an empty home;
  - plus, once, 8.7 MB of BouncyCastle in the outer Gradle cache (F11).
- **JDKs:** the prototype passed on JDK 17, 21 and 23 (F12).
- **Growth:** the time grows with the module, and the probe's fixtures were one class.

### Where the findings differ, and which view holds

- **Root `check` or the module's `check`.**
  - [`../../61-build-logic/research/testkit-harness.md`](../../61-build-logic/research/testkit-harness.md)
    Consideration 2 recommended a root lifecycle `check`, and the architecture (§6.1) wires the check into
    the root `check`.
  - The root project has no `check`, and a root `check` misses both `./gradlew build` and
    `:webtools-http:check` (wiring F6).
  - The module's `check` holds: it is reached by all three entry points and adds no root task.
- **Disabled `Sign` tasks or a key.** The same file's Consideration 1 recommended disabled `Sign` tasks.
  The measured cost of a generated key is one test dependency and no run time, and the key alone shows the
  fixtures jar signed, so the key holds.
- **`gradle.includedBuild("build-logic")` and injection into included builds.** The harness research
  derived both from source and never ran them (F12, F13). Both are now measured, and both hold.
- **A Kotlin Multiplatform consumer.** [`../../research/kmp-test-fixtures.md`](../../research/kmp-test-fixtures.md)
  left it unprobed. It resolves the recipe-B variants with their attributes unchanged.
- **One build file, two builds.** The guarded edge in `webtools-http/build.gradle.kts` reaches for an
  included build named `build-logic`. The check's producer copies that build file into a build that
  injects the plugins and includes no `build-logic`.
  - The edge is therefore declared only where the build includes `build-logic`, as well as being the
    root build.
  - The lazy `tasks.named("check")` already keeps the reference from being taken unless `check` is
    realised. The second condition removes the dependence on that.
  - This follows from wiring F1 and F2 and mechanics F2. It is not measured, and the check's own producer
    build exercises it on every run.

### Settled design that follows from the findings

1. **The edge.** `webtools-http/build.gradle.kts` declares, inside
   `if (gradle.parent == null && gradle.includedBuilds.any { it.name == "build-logic" })`, a lazy
   `tasks.named("check") { dependsOn(gradle.includedBuild("build-logic").task(":<check task>")) }`.
   The root build file does not change.
2. **The check task.** It is a `Test` task in `build-logic/build.gradle.kts`, shaped like `e2eTest`: the
   shared TestKit home and projects directory, `testKitHomeLock`, the 30-minute timeout, and a system
   property naming the module's directory. Its inputs are the files it copies, as `inputs.files` and
   `fileTree`s. Its class is one Level 4b TestKit class.
3. **The workspace.** A producer build holds a copy of the module and an in-build Kotlin Multiplatform
   consumer. A file-repository build holds a Kotlin/JVM and a Kotlin Multiplatform consumer, with
   `PREFER_SETTINGS` and `exclusiveContent`. A composite build includes the producer. Plugins are
   injected.
4. **Signing.** A throwaway key is generated per run and written only into the producer's
   `gradle.properties` in the workspace. Every nested build runs with `ORG_GRADLE_PROJECT_*` removed from
   its environment.
5. **The assertions.**
   - On the published files: the shape of the two fixtures variants, the main variants and both POMs; an
     `.asc` beside every file; the fixtures jar's classes at the floor.
   - On each consumer's resolution report and test results: the fixtures variant, jar and dependencies on
     the test classpaths only.
   - On every nested build's output: a stored configuration-cache entry, and no "loaded multiple times".

### Known gaps

- **JDK 24 to 26** were not run; JDK 17, 21 and 23 were.
- **Signature validity** is not checked, only an `.asc` beside every file. Central's own validator is
  first exercised by the first release of `webtools-http`.
- **The real fixtures' compile time** is not measured: the probe's fixtures were one class.
- **Workspace clean-up on Windows** after these nested builds was not exercised.
- **The nested `pluginManagement` composite**, a consumer including a producer that itself includes
  `build-logic`, which is BotTools' development shape, was run once from the command line, not through
  TestKit.
- **Injection into an included build** is undocumented. A Gradle upgrade that changes it fails the
  composite consumer, loudly.
- **Isolated Projects, parallel execution and a composite nested more than one level deep** were not
  exercised.
