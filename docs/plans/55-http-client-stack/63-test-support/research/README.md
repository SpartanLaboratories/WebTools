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
