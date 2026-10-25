# Issue #63 — `test-support` (impact)

- **Covers:** `SpartanLaboratories/WebTools#63`, *"webtools-http: published test-fixtures variant with the
  fake transport, virtual clock, `.http` fixtures, recorder and the publication check"*. It is unit 4,
  `test-support`, of [#55's architecture](../architecture.md) (§6.1, §6.7, §9, §11).
- **Level:** 3. This file covers what the repository, its published artifacts and the sibling units gain or
  lose with this unit, as built.
  - The file-level detail is in [`plan.md`](plan.md).
  - The as-built record is [`final-implementation.md`](final-implementation.md); this file agrees with it.
- **Baseline:** `master` `7de8207`. Built on branch `feat/issue-63-test-support`, with `master` merged in at
  `5d98102`; PR TBD (it closes #63).
- **Related docs:**
  - [`plan.md`](plan.md) and [`final-implementation.md`](final-implementation.md);
  - [`research/README.md`](research/README.md), with
    [`research/publication-check-wiring.md`](research/publication-check-wiring.md) and
    [`research/publication-check-mechanics.md`](research/publication-check-mechanics.md);
  - the systems-level [`../impact.md`](../impact.md).

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-http` (new, unreleased) | The root module gains the test-fixtures variant: two variants in its Gradle Module Metadata and a `-test-fixtures.jar` beside it. The `-jvm` module and the main variants do not change. | `1.0.0`, unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
| `webtools-udp`, `webtools-scraping`, `webtools-browser` | Nothing: no source, build-script or test change. | unchanged |

**What a consumer of `webtools-http` 1.0.0 sees from this unit**, as the fixtures-publication check
asserts on every run:

- **The root module's metadata** (`webtools-http-<version>.module`) holds `testFixturesApiElements` and
  `testFixturesRuntimeElements`.
  - Each carries the capability `io.github.spartanlaboratories:webtools-http-test-fixtures:<version>` and
    `org.gradle.jvm.version` 11, and no Kotlin attribute.
  - Each holds the one file `webtools-http-<version>-test-fixtures.jar`.
  - Each depends on `webtools-http`, `kotlinx-coroutines-test` 1.10.2, `mockwebserver3` 5.5.0 and the
    Kotlin plugin's `kotlin-stdlib`, and on nothing else.
- **The main variants,** in the root and the `-jvm` module metadata, and the `-jvm` POM list neither test
  library. The main variants keep the shape unit 3 published:
  - `kotlinx-coroutines-core` and `kotlin-stdlib` on the compile classpath;
  - `okhttp-jvm`, `caffeine` and `slf4j-api` at runtime only.
- **The root POM** lists `kotlinx-coroutines-test` and `mockwebserver3` as `optional` dependencies
  (`<scope>compile</scope><optional>true</optional>`). Optional dependencies do not propagate in Maven.
  Gradle prints no capability warning for the two variants.
- **How it is consumed.** A Gradle consumer writes
  `testImplementation(testFixtures("io.github.spartanlaboratories:webtools-http:<version>"))`, or the
  `jvmTest` form in a Kotlin Multiplatform build. A Maven build cannot select a variant by capability, so
  it cannot use the fixtures. The test libraries reach the consumer's test classpaths only; the check
  asserts it for a Kotlin/JVM and a Kotlin Multiplatform consumer of a file repository, a composite
  consumer and an in-build Kotlin Multiplatform module.
- **Signing.** Every published file gets an `.asc`, the fixtures jar included.
- **The fixtures jar** holds classes and `META-INF/` only: no `logback-test.xml`, no `fixtures/` tree and no
  other resource. Its classes are major 55. `verifyJavaFloor` checks them, the fixtures' runtime classpath
  and the two variants' `org.gradle.jvm.version`.
- **The ABI dump** `api/webtools-http.api` does not change: binary-compatibility-validator dumps only the
  `main` compilation. The fixtures are Stable Core with source compatibility only.

**Release.** No published module's version moves, so this landing makes no release candidate, and
`webtools-http` 1.0.0 stays unreleased until #55's stack is complete. Two parts of the fixtures' publication
meet Maven Central first at `webtools-http`'s first release:

- Central's validation of a `-test-fixtures.jar` that has no sources or javadoc jar of its own;
- the upload of a multiplatform publication carrying an adhoc variant.

The fixtures-publication check signs that publication on every run, so the release meets only Central's own
upload and validator unprepared. Both failure modes are fail-closed: nothing reaches Central unvalidated. If
Central refused the fixtures jar, an extra adhoc sources variant for it is additive.

## 2. Blast radius in this repository

### Build

- **`webtools-http/build.gradle.kts`** gains:
  - the recipe-B block: the `testFixtures` compilation, its jar, two consumable configurations and the
    adhoc publication;
  - the fixtures jar and the fixtures' runtime classpath in `verifyJavaFloor`. The association puts `main`'s
    output directories on that classpath, two of which this module never creates, and the guard fails on an
    input that does not exist, so the module hands it only the entries that exist. The guard's report counts
    two jars, and `main`'s classes are scanned twice, as the jar and as a directory.
  - the edge from its `check` to `:build-logic:fixturesPublicationCheck`, declared only when
    `gradle.parent == null` and `build-logic` is one of the included builds. Both halves are load-bearing:
    - without `gradle.parent == null`, a build that includes WebTools and asks for `webtools-http`'s `check`
      schedules the check, as removing it showed;
    - the included-build test keeps the check's producer copy of this file, which includes no `build-logic`,
      from reaching for one.

  The root `build.gradle.kts` and `settings.gradle.kts` do not change. The root project gains no plugin
  and no task.
- **`build-logic/build.gradle.kts`** gains:
  - the `fixturesPublicationCheck` task, shaped like the existing nested-build tasks;
  - the helper `configureNestedBuilds()`: the two TestKit properties (the workspace directory and the
    shared home), `testKitHomeLock` and the 30-minute timeout, which the nested-build level tasks now share
    with the new task;
  - the helper `copiesWebToolsHttp()`: the system property naming the module directory, and as inputs the
    files the check copies (the module's build file, its `commonMain`, `jvmMain` and `jvmTestFixtures` trees,
    and the repository's root build file);
  - the same property and inputs on `e2eTest`, which runs the check's class and the wiring test besides its
    existing class;
  - one test dependency, `org.bouncycastle:bcpg-jdk18on` 1.86, for the throwaway signing key.

  `build-logic/src/main` does not change, and `build-logic` is never published, so nothing reaches a
  consumer.
- **`./gradlew build`, `./gradlew check` and `./gradlew :webtools-http:check` run the check.**
  - It takes 44 to 52 s on the maintainer's host when one of its inputs changed: the module's build file, its
    main or fixtures sources, the root build file, or `build-logic`'s own code. Otherwise it is `UP-TO-DATE`,
    and a repeat that reuses the configuration cache takes under a second.
  - Its first run on a host downloads into `build-logic/build/testkit/home`, as the research measured: 38
    files (6.2 MB) on top of a home that `build-logic`'s other nested-build checks have filled, or 109 files
    (73 MB) in an empty one. It also downloads BouncyCastle, 8.7 MB, once into the Gradle user home.
  - The check runs on the build JDK. The as-built check ran on JDK 23; the research's probes ran on JDK 17,
    21 and 23. JDK 24 to 26 are unexercised.
- **A build that includes WebTools never runs the check.** `FixturesCheckWiringEndToEndTest` proves it at
  every `:build-logic:e2eTest`, on a copy of the root build file, `build-logic` and the module: the root
  build's dry run schedules the check, and an including build's dry run reaches `webtools-http`'s `check`
  without it. A scratch composite including the live checkout showed the same. BotTools' development shape,
  a consumer with a `build-logic` of its own, was measured on a reproduction in the research.
- **The pre-commit gate of `webtools-http`** stays `gatingTest componentTest deterministicTest`, now with the
  fixtures' own tests in it.
- **Every nested build of `build-logic`'s harness runs with `ORG_GRADLE_PROJECT_*` removed from its
  environment.** A release key exported in the environment of the build that runs the checks therefore
  never reaches a nested build. The existing checks benefit too.
  - The scrub covers that prefix only. `org.gradle.project.*` system properties passed through
    `JAVA_TOOL_OPTIONS` or `GRADLE_OPTS` are out of its scope: no supported release path passes a key that
    way.
  - The throwaway key and its passphrase exist only in the producer's `gradle.properties` inside the
    check's workspace, which is deleted when the class ends. The check asserts that no nested build's output
    shows either.

### Source and resources

- **New trees:**
  - `webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/`: the fixtures, 20
    files. The published jar ships no resources.
  - `webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/<level>/webtools/http/testing/`: the fixtures'
    own tests, 18 classes from Level 1 to Level 4c.
  - `webtools-http/src/jvmTest/resources/fixtures/`: six sample files for those tests.
- **`build-logic/src/test/`** gains two Level 4b classes and the harness additions:
  - `FixturesPublicationEndToEndTest`, the check itself;
  - `FixturesCheckWiringEndToEndTest`, the proof of the edge, run by `e2eTest` only;
  - `NestedBuild` runs in a given directory and scrubs the environment;
  - `ModuleMetadata` and `PomFile` read variants' capabilities, dependencies and files, and POM
    dependencies;
  - `JarClasses.entries` lists a jar's entries;
  - new `ModuleSource`, `ResolutionReport` and `ThrowawayPgpKey`;
  - `SampleProject` factories for the three builds of the check and for the two workspaces of the wiring
    test (`fixturesWiringRoot`, `fixturesWiringConsumer`).
- **No main source file of `webtools-http` changes**, and nothing in the three existing modules.
- **`.gitattributes`** gains `**/fixtures/**/*.http -text`, so Git keeps recorded fixtures byte-exact on a
  Windows checkout with `core.autocrlf=true`. This repository's own body-only fixture files are single
  lines without a trailing newline, so no conversion can touch them.

### Documentation

- **`README.md`.**
  - The install block gains the Gradle-only `testFixtures(...)` line.
  - A paragraph describes the fixtures variant: what it holds, that it is Gradle-only, its test-only
    dependencies, the consumer's `.gitattributes` line and the recording gate.
  - "Build & test" gains the check's command, distinguishes the published fixtures from the `testing/support/`
    helpers, and says that `webtools-http`'s `check` (so `build` and `check`) runs the check.
- **KDoc** on every public fixtures declaration, naming its tier (Stable Core, source compatibility only),
  its contract, its thread-safety and its virtual-time behaviour.
- **`architecture.md`** carries three dated callouts (2026-10-25), one for each point of §5 that
  concerns it:
  - at §6.1, the check hangs off `webtools-http`'s `check`, since the root project has no `check`;
  - at §12, the adhoc variant's signing runs on every run of the check;
  - at §6.7, the two `MockWebServer` helpers of the `Fixture` bullet land with unit 6, as §11 says.
- **Unchanged:** `docs/webtools-udp-architecture.md` and `docs/webtools-udp-protocol.md`. Unit 11 writes
  `docs/webtools-http-architecture.md`.
- **`docs/dashboards/`** is brought to the as-built state by the admin team's landing refresh; nothing in
  this unit edits it.

## 3. Adoption

| Site | Verdict |
|---|---|
| `webtools-http`'s existing tests | No hand-written fake of `HttpTransport` or `WallClock` exists to move onto the fixtures. The two gating classes stay as they are: they prove the main API compiles for a consumer without the opt-in. |
| `webtools-udp`'s `nanoClock` (`LinkQualityTracker.kt:15`) | Unchanged: the stack's time is `TimeSource` plus `WallClock`, and `VirtualClock` drives both; nothing is retrofitted. |
| The `testing/support/` helpers of each module | Unchanged. They stay module-private test code; the published fixtures are a separate, consumer-facing surface. |
| `SampleProject.withLocalPublishing()` in `build-logic`'s harness | Unchanged. It stays the way past signing for the existing single-project publishing checks; the fixtures check signs with a throwaway key instead. |

## 4. Sibling units

[`plan.md`](plan.md) §12 has the exact contracts, and the code as built matches them. In short:

- **Names.** This unit takes, in `com.spartanlabs.webtools.http.testing`:
  - public: `VirtualClock`, `virtualClock`, `FakeHttpTransport`, `RequestMatch`, `Step`, `Unmatched`,
    `RecordedCall`, `UnmatchedRequestError`, `Fixture`, `FixtureLoader`, `FixtureError`, `RecordingGate`,
    `RecordMode`, `Redaction`, `FixtureRecorder`;
  - `internal`: `decodeQueryPairs`, `percentDecodeQueryComponent`, `HttpMessageCodec`, `contentTypeFor`,
    `SensitiveNames`, `asciiLowercase`, `SecretScrubber`, `SecretSurvived`, `FixtureFileNames`,
    `RecordingHttpTransport`.

  `LoopbackTransports`, `webtoolsUrl`, `toMockResponse` and `enqueue(fixture)` are reserved for unit 6, and
  `RecordingBudgetObserver` and `FakeBudgetStore` for unit 8. In `build-logic` it takes the task
  `fixturesPublicationCheck`, the helpers `configureNestedBuilds` and `copiesWebToolsHttp`, the property
  `webtools.http.dir`, the two e2e classes and the harness names of §2.
- **Every later unit that adds fixtures** puts them in `webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/`.
  - Each implementer of a gated port carries `@OptIn(ExperimentalForInheritanceWebToolsApi::class)` on
    itself; the fixtures compilation has no source-set-wide opt-in.
  - Every public declaration states its visibility and return type (`-Xexplicit-api=strict`) and carries
    KDoc.
  - The fixtures' dependencies stay exactly `webtools-http`, `kotlinx-coroutines-test` 1.10.2 and
    `mockwebserver3` 5.5.0: the check asserts the list.
  - The check's inputs cover the new files, so the check runs again.
  - No module script reassigns `runtimeDependencyFiles`: the level tasks read the test compilation's once,
    and the fixtures reach them through the association.
- **Unit 5, `url-policy` (#64).** Nothing in the fixtures; it does not touch `src/jvmTestFixtures`.
- **Unit 6, `okhttp-transport` (#65).** It adds `LoopbackTransports`, `webtoolsUrl`, `Fixture.toMockResponse()`
  and `MockWebServer.enqueue(fixture)`, built on `Fixture.status`, `Fixture.headers` and `Fixture.body()`.
  It adds no fixtures dependency: `mockwebserver3` is already on the fixtures' API.
- **Unit 7, `response-cache` (#66).** It passes `VirtualClock.timeSource` (`TimeSource.WithComparableMarks`),
  `dispatcher` (`CoroutineDispatcher`) and `maintenanceExecutor` (`java.util.concurrent.Executor`) to
  `CaffeineResponseCache`, whose default dispatcher is therefore a constructor parameter. It re-runs the
  three-clocks proof of `VirtualClockThreeClocksComponentTest` with the real cache.
- **Unit 8, `call-budget` (#67).** It adds `RecordingBudgetObserver` and `FakeBudgetStore`, passes
  `VirtualClock.timeSource` and `wallClock` to `WindowedCallBudget`, and re-runs the three-clocks proof
  with the real budget.
- **Unit 9, `call-facades` (#68).** It scripts `FakeHttpTransport`, for example a `429` with `Retry-After`
  or a `RefusedByPolicy` failure, and reads `calls` and `unmatchedCalls`. It passes `VirtualClock.dispatcher`
  as the client's dispatcher. An unmatched request throws `UnmatchedRequestError`, an `Error`, which passes
  through a cache loader as one.
- **Unit 10, `feeds` (#69).** `jvmTestImplementation(testFixtures(project(":webtools-http")))` resolves the
  variants as published, measured. A step built from a lambda carries a `redirectChain` for
  `permanentlyMovedTo`; `RequestMatch.header` checks the conditional headers. Its own `.http` resources are
  covered by the same `.gitattributes` line. Its body-only `.rss` and `.atom` files are not: one of more than
  one line changes its line endings on a checkout with `core.autocrlf=true` unless a line of its own covers
  it.
- **Unit 11, `http-docs` (#70).** It documents the fixtures and the check from this unit's KDoc and README
  lines.

## 5. Where the as-built design departs from the parent documents

The first three points depart from [`../architecture.md`](../architecture.md), which carries a dated callout
(2026-10-25) for each at §6.1, §12 and §6.7. The last two depart from a sibling plan and from research.

- **Where the check attaches.**
  - §6.1 of the architecture wires the fixtures-publication check "into the root `check` through the
    included build's task".
  - The root project has no `check`. A root lifecycle `check` is not reached by `./gradlew build` or by
    `:webtools-http:check`, and it gives a consumer's composite a root task to address
    ([`research/publication-check-wiring.md`](research/publication-check-wiring.md) F6).
  - The check hangs off `webtools-http`'s own `check` instead, so `build`, `check` and
    `:webtools-http:check` all run it and the root build keeps only `group` and `version`.
  - This settles the open point of [`../impact.md`](../impact.md) §7 and §8 ("where the publication check
    attaches").
- **Signing of the adhoc variant.** §12 lists it as first exercised at the first release. The check signs
  the copy it publishes with a throwaway key and asserts an `.asc` beside every file
  ([`research/publication-check-mechanics.md`](research/publication-check-mechanics.md) F3, F4). Only
  Central's own upload and validator are left for the release.
- **The `MockWebServer` helpers.** §6.7's `Fixture` bullet lists `Fixture.toMockResponse()` and
  `MockWebServer.enqueue(fixture)` among the variant's types; its "Contributions from other units" and §11
  give them, with `LoopbackTransports`, to unit 6. They land with unit 6; `Fixture` exposes what they need.
- **The fixtures' opt-in.** [`../62-http-core/plan.md`](../62-http-core/plan.md) §12 gives the fixtures
  source set a language-setting opt-in. Each implementer opts in with its own `@OptIn` instead, so each
  worked example shows the opt-in a consumer's own implementation needs.
- **A Kotlin Multiplatform consumer.** [`../research/kmp-test-fixtures.md`](../research/kmp-test-fixtures.md)
  left it unprobed. It resolves the recipe-B variants with their attributes unchanged, in-build and from a
  file repository (mechanics F7, F8), and the check asserts both on every run.

## 6. Neighbouring issues

- **#58** (CI and repository practices). Its CI would be the first place the fixtures-publication check runs
  on every change. A CI job that exports a release key and runs `check` is safe: the harness scrubs
  `ORG_GRADLE_PROJECT_*` from every nested build. CI never records fixtures, because the recording gate
  refuses whenever a CI variable is set.
- **#64** (unit 5, `url-policy`) is the latest point for the IDE checks of §7.
- **The first release of `webtools-http`**, after #55's stack is complete, is where Maven Central first
  validates the fixtures jar (§1).

## 7. Follow-ups this unit leaves

- **The IDE checks.** Owner: the maintainer. Trigger: the next IntelliJ import of the repository after #63
  lands, and at the latest before unit 5 (#64) starts.
  - IntelliJ's import of the `jvmTestFixtures` source set and its two associations;
  - the gate's diagnostic and its `@OptIn` quick fix on an implementer without the opt-in;
  - Quick Documentation of the public fixtures;
  - what IntelliJ's HTTP Client plugin shows for a recorded `*.http` file.

  Each is recorded in [`final-implementation.md`](final-implementation.md) §5 with what verifies it
  meanwhile. Unit 3's IDE items, whose trigger was "at the latest before unit 4 starts", are still open and
  need the same session ([`../62-http-core/impact.md`](../62-http-core/impact.md) §7).
- **Recipe B into `build-logic`**, when a second module publishes fixtures. `webtools-feeds` publishes
  `FeedParserContract` with `FeedParser`'s graduation (architecture §9).
- **A sources variant for the fixtures jar**, only if Central's validator refuses the jar at the first
  release. It is additive.
- **Runs of the check on JDK 24 to 26, Linux and macOS.**

## 8. Known gaps

- **Signature validity** is not checked: the check asserts an `.asc` beside every file and a PGP signature
  on the fixtures jar, and verifies none.
- **Injection of the convention plugins into an included build**, which the check's composite consumer
  relies on, is measured on Gradle 9.7.1 but undocumented. A Gradle upgrade that changes it fails the
  check loudly.
- **BotTools' live composite** is not exercised: its checkout includes no WebTools build yet. The including
  branch of the edge is covered on a copy by `FixturesCheckWiringEndToEndTest`, and once by hand against the
  live WebTools checkout.
- **An empty TestKit home** was not run with the as-built check; its downloads are the research's
  measurement (§2).
- **IntelliJ's HTTP Client plugin**, where installed, treats `*.http` files as HTTP request files; what it
  shows for a recorded response is unexamined (§7).
- **Hosts:** only Windows with JDK 23 ran the as-built build; `ATOMIC_MOVE` over an existing fixture ran on
  NTFS only.
- **Not exercised:** Isolated Projects, parallel execution, and a composite nested more than one level deep.
