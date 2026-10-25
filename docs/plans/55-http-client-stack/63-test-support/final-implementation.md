# Final implementation: `test-support` (Issue #63)

The as-built record of `SpartanLaboratories/WebTools#63`, unit 4 (`test-support`) of
[#55's architecture](../architecture.md). It states what was built, where the work departs from
[`plan.md`](plan.md) and why, and the measurements that show it holds. What the unit changes for the
repository and for the sibling units is in [`impact.md`](impact.md).

- **Status:** built through stage S8 of the plan ([`plan.md`](plan.md) §5), with plan Amendments 1 to 3 and
  the fixes of qa passes 1 and 2 applied (§4). This is the final as-built record.
- **Branch and PR:** `feat/issue-63-test-support`, cut from `master` at `7de8207`, above the research
  commits, with `master` merged in at `5d98102`; PR TBD (it closes #63).
- **Host of the measurements:** the maintainer's Windows 11 host; JDK 23.0.2 (Oracle); Gradle 9.7.1 (wrapper);
  Kotlin Gradle plugin 2.2.0; `core.autocrlf=true`.
- **Versions moved:** none. `webtools-http` 1.0.0 stays unreleased until #55's stack is complete. It is not a
  release candidate at this landing.

## 1. What was built

Everything plan §7 lists, in the plan's shape, with plan Amendments 1 to 3. The mechanical fixes made on the
way are in §4.

- **`webtools-http/build.gradle.kts`**, in two regions:
  - **`test fixtures`.** On the `jvm()` target, a `testFixtures` compilation associated with `main`, and
    `test` associated with it.
    - `-Xexplicit-api=strict` on `compileTestFixturesKotlinJvm`.
    - `testFixturesJar`, with the classifier `test-fixtures`.
    - The consumable configurations `testFixturesApiElements` (`java-api`) and `testFixturesRuntimeElements`
      (`java-runtime`). Each has category `library`, libraryelements `jar`, bundling `external`,
      `TargetJvmVersion` = `WebToolsBuild.JAVA_FLOOR`, the capability
      `${group}:webtools-http-test-fixtures:${version}` and the jar.
    - Both are added to the root publication through `kotlin.publishing.adhocSoftwareComponent`, with
      `mapToMavenScope` and `mapToOptional`, and `suppressPomMetadataWarningsFor` names both.
    - `jvmTestFixturesApi` holds the module itself, `kotlinx-coroutines-test` 1.10.2 and `mockwebserver3`
      5.5.0, and nothing else.
    - `verifyJavaFloor` gains the fixtures jar and the existing entries of the fixtures' runtime classpath
      (Amendment 2).
    - The fixtures compilation has no `languageSettings.optIn`.
  - **`fixtures-publication check`.** `check` depends on `:build-logic:fixturesPublicationCheck`, declared
    lazily and only when `gradle.parent == null` and `build-logic` is one of the included builds.
- **The fixtures**, `webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/`: 20 files
  in one flat package.
  - **Public:** `VirtualClock` and `TestScope.virtualClock`; `FakeHttpTransport`, `RequestMatch`, `Step`,
    `Unmatched` (`Fail`, `Respond`), `RecordedCall` and `UnmatchedRequestError`; `Fixture`, `FixtureLoader`
    and `FixtureError`; `FixtureRecorder`, `RecordingGate` (`Enabled`, `Disabled`), `RecordMode` and
    `Redaction`.
  - **`internal`:** `decodeQueryPairs` and `percentDecodeQueryComponent`, `HttpMessageCodec`, `contentTypeFor`,
    `SensitiveNames` and `asciiLowercase`, `SecretScrubber` and `SecretSurvived`, `FixtureFileNames`,
    `RecordingHttpTransport`.
  - Every class is final, and none is a `data class` or `data object`. Exactly three `@OptIn` lines exist,
    one on each implementer of a gated port: `FakeHttpTransport`, `VirtualClock`'s private wall clock and
    `RecordingHttpTransport`.
  - Every public declaration has KDoc; each top-level type and the `TestScope` extension names its tier,
    "Stable Core, source compatibility only." P12's coverage check prints nothing.
- **The fixtures' tests**, `webtools-http/src/jvmTest/kotlin/com/spartanlabs/testing/<level>/webtools/http/testing/`:
  18 classes and 141 tests, all green.
  - Level 1: `FixturesConsumerGatingTest` (4).
  - Level 2: eight classes (82). `FakeHttpTransport` 21, `Fixture` 10, `FixtureLoader` 9, `Redaction` 13,
    `RequestMatch` 9, `Step` 7, `VirtualClock` 11, `VirtualClockThreeClocks` 2.
  - Level 3: `FixtureRecorderIntegrationTest` (16).
  - Level 4a: five classes (33). `FixtureFileNames` 8, `HttpMessageCodec` 8, `QueryPairs` 3,
    `RecordingGate` 7, `SensitiveNames` 7.
  - Level 4c: three classes (6). `FakeHttpTransportConcurrency` 2, `FixturesSecrecy` 2,
    `HttpMessageCodecGarbage` 2.
  - The six sample files of plan §9.1, under `webtools-http/src/jvmTest/resources/fixtures/sample/`.

  With unit 3's tests, the module's `jvmTest` runs 272 tests in 54 classes.
- **`build-logic/build.gradle.kts`.**
  - The test dependency `org.bouncycastle:bcpg-jdk18on` 1.86, for the throwaway signing key.
  - The helper `configureNestedBuilds()`: the two TestKit properties, `testKitHomeLock` and the 30-minute
    timeout. The nested-build level tasks and the new task share it.
  - The helper `copiesWebToolsHttp()`: the system property `webtools.http.dir`, and as inputs the files the
    check copies. Those are the module's build file, the root build file and the module's `commonMain`,
    `jvmMain` and `jvmTestFixtures` trees, declared as file trees.
  - `e2eTest` copies the module too, and its description covers its three classes.
  - The task `fixturesPublicationCheck`, which selects `FixturesPublicationEndToEndTest` only.
  - `build-logic/src/main` is unchanged.
- **`build-logic`'s tests.**
  - Level 4b: `FixturesPublicationEndToEndTest`, 10 tests over three nested builds run in one `@BeforeAll` (the
    producer, the file-repository consumer and the composite consumer). `FixturesCheckWiringEndToEndTest`,
    2 tests run by `e2eTest` only: the root build's dry run schedules the check, and an including build's
    never does.
  - The harness:
    - `NestedBuild` runs in a given `projectDir` and removes `ORG_GRADLE_PROJECT_*` from every nested
      build's environment;
    - `ModuleMetadata` reads a variant's capabilities, dependencies and files, and `PomFile` its
      dependencies;
    - `JarClasses.entries` lists a jar's entries;
    - `SampleProject` gains the factories `fixturesProducer`, `fixturesFileConsumer`,
      `fixturesCompositeConsumer`, `fixturesWiringRoot` and `fixturesWiringConsumer`, with their templates;
    - `ModuleSource`, `ResolutionReport` and `ThrowawayPgpKey` are new.

  `:build-logic:e2eTest` runs 17 tests in three classes and `fixturesPublicationCheck` 10. `integrationTest`
  runs 46, `test` 47 and `nonfunctionalTest` 3. All are green.
- **`.gitattributes`:** `**/fixtures/**/*.http -text`, with its comment.
- **`README.md`:** the six edits of plan §8 (29 lines changed).
- **Unchanged:** `webtools-http/src/commonMain`, `src/jvmMain` and `api/webtools-http.api`;
  `webtools-http-commoncheck`; the three existing modules; `settings.gradle.kts` and the root
  `build.gradle.kts` (P11). The ABI dump does not move (P6).

## 2. Deviations from the plan

One. It changes no rule of the design, and no signature, message or name. Plan Amendments 1 to 3 are part of
the plan and were followed: the scrubber's detection boundary, the filter on the floor guard's classpath, and
the eight added test behaviours with the recorded scope of the environment scrub.

1. **`FixturesConsumerGatingTest` written in S5, not S4.** Plan §5 stages the permanent Level 1 consumer with
   S4 (the fake and its script types). It consumes `RecordingGate` and `FixtureRecorder`, which S5 delivers, so
   the class was written once in S5, when every type it uses existed; this avoided a partial class and a second
   edit. The gating task ran empty for the fixtures during S4. The class itself is as §9 specifies (no opt-in;
   the fake as an `HttpTransport`, `wallClock` as a `WallClock`, the gate, the recorder's `wrap`,
   `Fixture.parse`). A staging deviation, not a design change.

**Mechanical fixes** change no rule and are listed where they happened, in §4:
- a KDoc that quoted the `.gitattributes` pattern, whose `*/` closed the comment (P3 after S3);
- `readHead`'s refusal of a blank line before the status line, which covered a blank first line only (P3 after
  S3);
- `Stream.toList()`, a Java 16 API, replaced in a test compiled against the Java 11 API (P4);
- the minor findings of qa pass 1 and the findings of qa pass 2, among them a Level 3 test that JUnit never
  discovered because its expression body returned a value (qa pass 2 fixes).

## 3. Acceptance

Against plan §1 "Acceptance", from the measurements in §4:

1. `:webtools-http:compileTestFixturesKotlinJvm` passes under `-Xexplicit-api=strict`. The explicit-API
   canary fails with three errors, and the opt-in canary with the marker's message; both were removed (P2) —
   **met**.
2. The level tasks pass, and their result XML lists the fixtures' classes: 1 at Level 1, 8 at Level 2 and 5
   at Level 4a (P3), 1 at Level 3 and 3 at Level 4c (P4). At the end the fixtures have 18 classes and 141 tests
   (§1) — **met**.
3. The generated POMs and Gradle Module Metadata have the shape of R1 (P5). `apiCheck` passes and the dump
   does not move (P6.1). `verifyJavaFloor` passes and counts two jars: "127 classes in 2 jar(s), 6 library
   variants in 2 metadata file(s), 3481 classes on the runtime classpath" (P6.2, under Amendment 2) —
   **met**.
4. `:build-logic:fixturesPublicationCheck` passes its ten tests: 52 s on its first run, on a TestKit home the
   other checks had filled; 44 s with `--rerun`; 835 ms `UP-TO-DATE` (P7.1, P7.2). A run on an empty TestKit
   home was not repeated; the research measured its downloads ([`impact.md`](impact.md) §2) — **met**.
5. `./gradlew :webtools-http:check` runs it (46 s), and an immediate repeat reports it `UP-TO-DATE` with
   "Reusing configuration cache" (P7.3). `./gradlew build --dry-run` and `./gradlew check --dry-run` list it
   (P8.1), and the root project's task list is unchanged (P8.2). An edit to a fixtures source runs it again
   (P8.4). A build that includes the WebTools checkout does not schedule it (P8.5, and
   `FixturesCheckWiringEndToEndTest`) — **met**.
6. `:build-logic:e2eTest`, `:build-logic:integrationTest` (46) and `:build-logic:nonfunctionalTest` (3) pass
   with the changed harness underneath, one after another (P9). `e2eTest` ran its three classes (17 tests)
   after Amendment 3 and again after the qa pass 2 fixes, and qa pass 2 ran all four of `build-logic`'s test
   tasks with `--rerun-tasks`. An `ORG_GRADLE_PROJECT_signingInMemoryKey` exported for the outer build does
   not reach the nested producer (P9) — **met**.
7. The three mutations of P10 each fail the check (1, 5 and 5 of its ten tests). The module build file was
   restored, and its diff is only the intended change. A fourth mutation, the edge without
   `gradle.parent == null`, fails the second test of `FixturesCheckWiringEndToEndTest` (qa pass 2 fixes) —
   **met**.
8. `git diff --stat` is empty for the three existing modules, for `webtools-http/src/commonMain`, `src/jvmMain`
   and `api/`, for `webtools-http-commoncheck` and for `build-logic/src/main` (P11) — **met**.
9. The mechanical rules of P12 hold, and the README and `.gitattributes` read as plan §8 says — **met**.

## 4. Measurements

Each procedure of the plan records its commands and results here, in the order they ran.

### P1 - baseline and branch (2026-10-25)

1. `git branch --show-current` = `feat/issue-63-test-support`; `git log --oneline 7de8207..HEAD` = the three
   research commits (`eb274e0`, `abdb8c2`, `7cabe98`); `git status --short` shows only the presenter's plan-board
   files (modified under `55-http-client-stack/` and `62-http-core/`, untracked under `63-test-support/`) and
   this issue's `plan.md`, `impact.md` and `final-implementation.md`.
2. `./gradlew :build-logic:test` - BUILD SUCCESSFUL in 12s (1 executed, 11 up-to-date).
3. `./gradlew :webtools-http:verifyJavaFloor` - BUILD SUCCESSFUL; report:
   `java floor 11 (class-file major <= 55): 63 classes in 1 jar(s), 4 library variants in 2 metadata file(s), 3257 classes on the runtime classpath`.
4. `./gradlew :tasks --all` saved to a scratch file outside the repository (404 lines) for P8.

### P2 - the variant compiles; the two canaries (S1)

1. `./gradlew :webtools-http:compileTestFixturesKotlinJvm :webtools-http:compileTestKotlinJvm` - BUILD SUCCESSFUL in
   20s (`compileTestFixturesKotlinJvm` and `compileTestKotlinJvm` executed). A `--rerun-tasks` repeat printed no
   `w:` line and nothing about opt-in (the Gradle-10 deprecation notice is the only build-level warning).
2. Explicit-API canary (`class Undecorated { fun f() = 1 }`): `compileTestFixturesKotlinJvm` failed with
   `Canary.kt:3:1 Visibility must be specified in explicit API mode.`, `:3:21` the same for `f`, and
   `:3:25 Return type must be specified in explicit API mode.` File deleted.
3. Opt-in canary (`public class CanaryTransport : HttpTransport`, no `@OptIn`): failed with
   `Canary.kt:8:32 This class or interface requires opt-in to be implemented: Implementing or substituting this
   WebTools seam is Experimental and may change in a minor release. Using the supplied implementations needs no
   opt-in. Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).` With the `@OptIn` added: BUILD
   SUCCESSFUL. File deleted.
4. `git status --short` shows no `Canary.kt`; `src/jvmTestFixtures/.../testing/` holds `VirtualClock.kt` only.

### P3 - the fast loop, after S2 (VirtualClock)

`./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest` - BUILD SUCCESSFUL in
16s. `componentTest` result XML lists `VirtualClockComponentTest` (11 tests, 0 failures) and
`VirtualClockThreeClocksComponentTest` (2 tests, 0 failures) in `com.spartanlabs.testing.component.webtools.http.testing`;
no fixtures class at Level 1 or 4a yet (S4 adds the gating consumer, S3 to S5 the deterministic classes).

### P3 - the fast loop, after S3 (the `.http` codec, `Fixture`, `FixtureLoader`)

`./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest` - BUILD SUCCESSFUL in
7s. Fixtures classes in the result XML: Level 2 `FixtureComponentTest` (10), `FixtureLoaderComponentTest` (9),
`VirtualClockComponentTest` (11), `VirtualClockThreeClocksComponentTest` (2); Level 4a
`HttpMessageCodecDeterministicTest` (8); all 0 failures. Two mechanical fixes on the way: the KDoc of `Fixture`
and `FixtureLoader` quoted the `.gitattributes` pattern literally, whose `*/` ended the comment (reworded); and
`readHead` first refused "a blank line before the status line" only for a blank first line, so a comment line
followed by a blank line came back as "no status line" (now: a blank line before any non-comment line).

### P3 - the fast loop, after S4 (the fake and its script types)

`./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest` - BUILD SUCCESSFUL in
6s, first run. New in the result XML: Level 2 `FakeHttpTransportComponentTest` (21), `RequestMatchComponentTest` (9),
`StepComponentTest` (7); Level 4a `QueryPairsDeterministicTest` (3: the table, the seeded differential of 100,000
queries against `HttpUrl`, and the fixed ill-formed-escape list compared **exactly**, so the plan's §3 fallback to
"contains U+FFFD" was not needed). All 0 failures; 9 fixtures classes over the three tasks. The Level 1 consumer is
written in S5, once every type it uses exists (the gating task runs empty for the fixtures until then).

### P3 - the fast loop, after S5 (the recorder, the Level 1 consumer)

`./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest` - BUILD SUCCESSFUL in
6s. The result XML lists the fixtures classes the plan expects: **1 at Level 1** (`FixturesConsumerGatingTest`, 4),
**8 at Level 2** (FakeHttpTransport 21, Fixture 10, FixtureLoader 9, Redaction 9, RequestMatch 9, Step 7,
VirtualClock 11, VirtualClockThreeClocks 2) and **5 at Level 4a** (FixtureFileNames 8, HttpMessageCodec 8,
QueryPairs 3, RecordingGate 7, SensitiveNames 7). 0 failures; no warning but the Gradle-10 deprecation notice
and the pre-existing `w: JS Environment Not Selected` of `webtools-http-commoncheck`.

### P4 - Level 3 and 4c (S5)

`./gradlew :webtools-http:integrationTest :webtools-http:nonfunctionalTest` - first run failed to compile:
`Stream.toList()` is Java 16+ and the test compilation targets the Java 11 API (replaced by
`asSequence().toList()`). Second run: BUILD SUCCESSFUL in 3s. Result XML: `FixtureRecorderIntegrationTest` (14, 0
failures, 0.36 s); `FakeHttpTransportConcurrencyNonFunctionalTest` (2), `HttpMessageCodecGarbageNonFunctionalTest`
(1; 50,000 inputs in 0.33 s), `FixturesSecrecyNonFunctionalTest` (2); all 0 failures.

Note on the tests' secret forms: the scrubber detects exactly the plan's list (the three forms, their lower-hex
variants, the full percent-escape of the secret's bytes in either case). A partially escaped secret made of
otherwise-safe characters (`SECRET%2dCANARY`) is in none of those sets, so the refusal tests use a form that is:
`abc%2f` for the secret `abc/def` (Level 3) and the full lower-case escape of the canary (Level 4c).

### P5 - the publication read by hand (S6)

`./gradlew :webtools-http:generateMetadataFileForKotlinMultiplatformPublication :webtools-http:generatePomFileForKotlinMultiplatformPublication :webtools-http:generateMetadataFileForJvmPublication :webtools-http:generatePomFileForJvmPublication`
(and a `--rerun-tasks` repeat) - BUILD SUCCESSFUL; no "Declares capability ... which cannot be mapped to Maven" line.
- Root `module.json`: `testFixturesApiElements` (`java-api`) and `testFixturesRuntimeElements` (`java-runtime`), each with
  the capability `io.github.spartanlaboratories:webtools-http-test-fixtures:1.0.0`, `org.gradle.jvm.version` 11, the one
  file `webtools-http-1.0.0-test-fixtures.jar`, dependencies `webtools-http` 1.0.0, `kotlinx-coroutines-test` 1.10.2,
  `mockwebserver3` 5.5.0, `kotlin-stdlib` 2.2.0, and only the five recipe-B attributes (no Kotlin attribute). No other
  variant of the root or the `-jvm` module lists a test library.
- Root POM: `kotlinx-coroutines-test` and `mockwebserver3` `compile` + `optional=true`; no `webtools-http` dependency.
  `-jvm` POM: coroutines-core-jvm, kotlin-stdlib (compile), okhttp-jvm, caffeine, slf4j-api (runtime); no test library.
- `javap -v ... VirtualClock`: `major version: 55`.

### P6 - the dump and the floor (S6)

1. `./gradlew :webtools-http:apiCheck` - BUILD SUCCESSFUL; `git diff --exit-code --stat webtools-http/api` clean (the dump
   does not move).
2. `./gradlew :webtools-http:verifyJavaFloor` - **BUILD FAILED**:
   `:webtools-http:verifyJavaFloor: cannot read .../webtools-http/build/classes/java/jvmMain: ... does not exist`.
   Cause: the `testFixtures` compilation's `runtimeDependencyFiles` (added to `runtimeClasspath` by the plan's block,
   §7.1) carries the associated `main` compilation's `output.allOutputs`, whose Java classes directory
   (`build/classes/java/jvmMain`) and resources directory are never created (`compileJvmMainJava` and
   `jvmProcessResources` are NO-SOURCE), and `JavaFloorScan.classFiles` fails for a root that does not exist
   (`JavaFloorScan.kt:64`). The plan's own `main` classpath never hit this because a compilation's
   `runtimeDependencyFiles` excludes its own outputs. Plan Amendment 2 settles it: the module script hands over
   only existing entries, `.filter(Spec<File> { it.exists() })`, and `build-logic` is untouched, the guard's
   fail-on-missing-root rule being its contract. With the amended block:
   `./gradlew :webtools-http:verifyJavaFloor` - BUILD SUCCESSFUL in 6s; report
   `java floor 11 (class-file major <= 55): 127 classes in 2 jar(s), 6 library variants in 2 metadata file(s), 3481 classes on the runtime classpath`
   (two jars; 3481 > the P1 baseline's 3257, main's classes being counted twice as the amendment says).

### P7 - the publication check (S7)

The harness additions (`NestedBuild` with `projectDir` and the `ORG_GRADLE_PROJECT_*` scrub, `ModuleSource`,
`ThrowawayPgpKey`, `ModuleMetadata` with capabilities/dependencies/files, `PomFile.dependencies()`,
`ResolutionReport`, the `SampleProject` factories and templates), the `fixturesPublicationCheck` task and
`FixturesPublicationEndToEndTest` compiled first time (`./gradlew :build-logic:compileTestKotlin` - BUILD SUCCESSFUL;
one warning: the research's `JcaPGPKeyPair(Int, KeyPair, Date)` constructor is deprecated in BouncyCastle 1.86,
kept as the plan's helper).

1. `./gradlew :build-logic:fixturesPublicationCheck` (before the edge exists; the TestKit home already held the other
   checks' downloads): BUILD SUCCESSFUL in 52s wall; `TEST-...FixturesPublicationEndToEndTest.xml` has `tests="10"
   skipped="0" failures="0" errors="0"`. The workspace under `build-logic/build/testkit/projects` was deleted by
   `closeWorkspace()` (no leftover on Windows).
2. Immediate repeat: `Reusing configuration cache.` and `:build-logic:fixturesPublicationCheck UP-TO-DATE` (835 ms).
   `./gradlew :build-logic:fixturesPublicationCheck --rerun`: BUILD SUCCESSFUL in 44s (45s wall), 10/10 again.
3. With the edge added (§7.1, the `fixtures-publication check` region): `./gradlew :webtools-http:check` - BUILD
   SUCCESSFUL in 46s; `:build-logic:fixturesPublicationCheck`, `:webtools-http:jvmTest`, `apiCheck` executed,
   `verifyJavaFloor` UP-TO-DATE from P6. Immediate repeat: `Reusing configuration cache.`,
   `:build-logic:fixturesPublicationCheck UP-TO-DATE`, `jvmTest UP-TO-DATE`, BUILD SUCCESSFUL in 821ms.

### P8 - the root wiring (S7)

1. `./gradlew build --dry-run` and `./gradlew check --dry-run` each list `:build-logic:fixturesPublicationCheck SKIPPED`
   (a dry run; no test task executed).
2. `./gradlew :tasks --all` against the P1 scratch file: the root project's own task list is unchanged; the only
   added lines are six `webtools-http:` tasks of the new compilation (`compileTestFixturesKotlinJvm`,
   `compileJvmTestFixturesJava`, `jvmTestFixturesClasses`, `jvmTestFixturesProcessResources`,
   `processJvmTestFixturesResources`, `testFixturesJar`), which S1 introduced.
3. `git diff --stat -- settings.gradle.kts build.gradle.kts` is empty.
4. A comment appended to `VirtualClock.kt`, then `./gradlew :webtools-http:check`: `Reusing configuration cache.` and
   `:build-logic:fixturesPublicationCheck` executed (BUILD SUCCESSFUL in 45s); the line removed, the file restored
   byte-exact (md5 compared).
5. A scratch `settings.gradle.kts` outside the repository with `includeBuild(<WebTools checkout>)`:
   `./gradlew -p <scratch> help` - BUILD SUCCESSFUL, no configuration error; `./gradlew -p <scratch>
   :WebTools:webtools-http:check --dry-run` lists `:WebTools:webtools-http:check SKIPPED` and no
   `fixturesPublicationCheck` (`gradle.parent != null`). Scratch directory deleted.

### P9 - the changed harness underneath the existing checks (S7)

One after another: `./gradlew :build-logic:e2eTest` - BUILD SUCCESSFUL in 1m 9s (`IncludedBuildEndToEndTest` 5/0,
`FixturesPublicationEndToEndTest` 10/0); `./gradlew :build-logic:integrationTest` - BUILD SUCCESSFUL in 1m 4s (nine
classes, 46 tests, 0 failures); `./gradlew :build-logic:nonfunctionalTest` - BUILD SUCCESSFUL in 23s (three classes,
3 tests, 0 failures). The scrub: with `ORG_GRADLE_PROJECT_signingInMemoryKey=invalid` exported for the outer Gradle
process, `./gradlew :build-logic:fixturesPublicationCheck --rerun` - BUILD SUCCESSFUL in 44s, 10/0 (the outer build
configured normally; the nested producer never saw the variable). The variable was set for that one command only.

### P10 - mutation sensitivity (S7; once, not committed)

A scratch copy of `webtools-http/build.gradle.kts` outside the repository; each mutation applied, the check run,
the file restored and `diff`ed against the copy (identical each time):
- (a) both `mapToOptional()` calls removed: 1 of 10 fails - assertion 3, "the root pom lists kotlinx-coroutines-test
  as non-optional".
- (b) the `TargetJvmVersion` attribute line removed: 5 of 10 fail - assertion 2 (`testFixturesApiElements: jvm
  version expected Success(11), was Failure(NoSuchElementException)`) and assertions 6 to 9 ("jvm version of the
  fixtures variant expected 11 but was null" on every consumer's test compile classpath).
- (c) `implementation("com.squareup.okhttp3:mockwebserver3:5.5.0")` added to `jvmMain`: 5 of 10 fail - assertion 3
  (`jvmRuntimeElements-published lists a test library`) and assertions 6 to 9 ("test files on a main classpath:
  [mockwebserver3-5.5.0.jar]" on every consumer's runtime classpath).
After the last restore `git diff --stat -- webtools-http/build.gradle.kts` shows only the intended change (118 lines)
and `./gradlew :build-logic:fixturesPublicationCheck` passes again (BUILD SUCCESSFUL in 45s, 10/0). The scratch copy
was deleted.

### P11 - the unaffected modules (S8)

On head `5d98102` (master merged into the branch by the admin team): `git diff --stat -- webtools-udp webtools-scraping
webtools-browser webtools-http/src/commonMain webtools-http/src/jvmMain webtools-http/api webtools-http-commoncheck
settings.gradle.kts build.gradle.kts` is empty and `git status --short` of those paths shows nothing;
`git status --short build-logic/src/main` is empty (no plugin changed);
`./gradlew :webtools-udp:compileKotlin :webtools-scraping:compileKotlin :webtools-browser:compileKotlin` - BUILD
SUCCESSFUL (a compile, no test).

### P12 - mechanical rules (S8)

- Forbidden constructs in `webtools-http/src/jvmTestFixtures` (`data class|data object|runCatching|TODO|println|
  System.out/err|try {|catch`, comment lines dropped): nothing.
- `OptIn` in `webtools-http/src/jvmTest` and `src/commonTest`: nothing.
- `@OptIn` code lines in the fixtures: exactly three (`FakeHttpTransport.kt:58`, `RecordingHttpTransport.kt:16`,
  `VirtualClock.kt:89`); in the module build file only the existing `gateMarker` lines, their comments and the
  `ExperimentalKotlinGradlePluginApi` opt-in.
- `TODO` in `webtools-http/src/jvmTest` and `build-logic/src/test`: nothing.
- KDoc coverage (the plan's awk over every fixtures file): nothing printed.
- `PRIVATE KEY BLOCK|C:\|D:\|/Users/|/home/` over every changed or new file: nothing; `BEGIN PGP SIGNATURE` appears
  only in `FixturesPublicationEndToEndTest.kt`.
- `git ls-files --eol` of the fixtures resources: the three `.http` files `w/lf attr/-text`, the body-only files
  `w/none` (no line feed); `git check-attr text` on the `.http` files: `unset`.
- `git diff --stat README.md .gitattributes`: README 29 lines changed (+26/-3, the six edits of §8), `.gitattributes`
  +2; README keeps its CRLF working-tree endings (`file`: "with CRLF line terminators").
- `git status --short` shows no `build/`, `.idea/`, canary, scratch or `.p10` file; the last commit carries no
  attribution trailer.

### qa pass 1, minor fixes (2026-10-25)

QA pass 1 (code review PASS, 376/376 green) listed minor findings, all applied: 19 documentation items (the
Amendment-1 redaction boundary in the `FixtureRecorder` and `Redaction` KDocs; the `fixturesPublicationCheck`
header comment; `ThrowawayPgpKey`'s best-effort-cleanup wording and the comments on its cryptographic constants;
the stale "three" at `TestKitHomeLock`; `METHOD_LIMIT` and the IPv6 re-bracketing in `FixtureFileNames`; the
concurrency comment in `VirtualClockComponentTest`; `ResolutionReport.read`'s `@return`; `FixtureLoader`'s
`@param classLoader`; "no NUL" in `Fixture`'s grammar; the `inputs.property` note in `SampleProject`; the `PATH`
and name-surgery comments in two deterministic tests; two `//language=RegExp` hints; the `kotlin` fence tag in
`RecordingGate`), 6 test minors (the gating consumer no longer executes through the recorder, so it does no file
I/O; two `200` asserts in `FixtureLoaderComponentTest`; `kept`/`refused` snapshot asserts at Level 3; nine
behaviour names at Levels 2 and 4a) and 2 code items (the mangled path in this file's P6 record;
`"11"` -> `WebToolsBuild.JAVA_FLOOR.toString()` in the check class). 21 files edited; no behaviour, API or design
change. Re-run: `./gradlew :webtools-http:check` - BUILD SUCCESSFUL in 1m 3s (`jvmTest` 265 tests in 54 classes, 0
failures; `apiCheck`, `verifyJavaFloor`, `:build-logic:fixturesPublicationCheck` 10/0 executed);
`./gradlew :build-logic:e2eTest` - BUILD SUCCESSFUL in 1m 37s (`FixturesPublicationEndToEndTest` 10/0,
`IncludedBuildEndToEndTest` 5/0).

### qa pass 1, Amendment 3 tests (2026-10-25)

Plan Amendment 3 adds eight test behaviours and records one exclusion; implemented as plan §9 reads:
1. `RedactionComponentTest` +4: the upper-hex full escape is detected; the form-encoded form is replaced and its
   lower-hex variant detected (secret `ab cd/ef`); an echo escaped only in part is neither replaced nor detected
   (`SECRET%2dCANARY`, bytes unchanged); a secret whose form contains another's is replaced whole, longest first.
2. `FixtureRecorderIntegrationTest` +1: eight callers released by one `CompletableDeferred` record to one name
   under `Overwrite`; one complete `same.http`, no temporary file, `written + refused == 8`, eight log events.
3. New `FixturesCheckWiringEndToEndTest` (e2eTest only) with the `SampleProject` factories `fixturesWiringRoot`
   and `fixturesWiringConsumer`: the root dry run schedules `:build-logic:fixturesPublicationCheck`, the consumer
   composite's dry run reaches `:webtools:webtools-http:check` and never the publication check.
4. `JarClasses.entries(jar)`; `FixturesPublicationEndToEndTest` test 5 renamed `the fixtures jar holds only
   classes at the Java floor` and asserts no entry outside `META-INF/` that is not a class (still ten tests).
5. `NestedBuild`'s KDoc states the scrub's scope (`ORG_GRADLE_PROJECT_*` only; `org.gradle.project.*` system
   properties through `JAVA_TOOL_OPTIONS`/`GRADLE_OPTS` out of scope, with the three reasons). Record only.
6. `HttpMessageCodecGarbageNonFunctionalTest` +1: `large heads and bodies parse in time linear in their size`
   (64 KiB/1 MiB of header lines with no blank line, a 64 KiB/1 MiB header value, a 256 KiB/4 MiB body; the 16x parse
   under `100 * maxOf(t1, 1 ms)`; a failing measurement rerun once).
7. `FakeHttpTransportConcurrencyNonFunctionalTest`: every caller and adder waits on one `CompletableDeferred`
   completed after all are launched; assertions unchanged.
8. `FixturesSecrecyNonFunctionalTest`: second canary `SECRET/CANARY+7f3a&x=y` in the query (via
   `encodeQueryComponent`), a request header, the request body, a response header and body, and as a `Redaction`
   secret; every checked string holds none of raw / `encodeQueryComponent` / `URLEncoder` / lower-hex forms; the
   decorating transport's and the `Enabled` gate's `toString()` also checked.

Re-run: `./gradlew :webtools-http:check` - BUILD SUCCESSFUL in 1m 4s; `jvmTest` 271 tests in 54 classes, 0 failures
(fixtures classes, 18 / 140 tests: Level 1 FixturesConsumerGatingTest 4; Level 2 FakeHttpTransport 21, Fixture 10,
FixtureLoader 9, Redaction 13, RequestMatch 9, Step 7, VirtualClock 11, VirtualClockThreeClocks 2; Level 3
FixtureRecorderIntegrationTest 15; Level 4a FixtureFileNames 8, HttpMessageCodec 8, QueryPairs 3, RecordingGate 7,
SensitiveNames 7; Level 4c FakeHttpTransportConcurrency 2, FixturesSecrecy 2, HttpMessageCodecGarbage 2);
`apiCheck`, `verifyJavaFloor` and `:build-logic:fixturesPublicationCheck` (10/0) ran under it.
`./gradlew :build-logic:e2eTest :build-logic:fixturesPublicationCheck --rerun` - BUILD SUCCESSFUL in 3m 2s;
`e2eTest`: FixturesCheckWiringEndToEndTest 2/0, FixturesPublicationEndToEndTest 10/0, IncludedBuildEndToEndTest 5/0;
`fixturesPublicationCheck`: FixturesPublicationEndToEndTest 10/0. No workspace left under
`build-logic/build/testkit/projects`.

### qa pass 2 fixes (2026-10-25)

QA pass 2 (384/384 green; one non-minor and 25 minor findings). Its items on the source and test files were
applied, in 18 files, with no behaviour, API or design change. Its items on the plan documents are settled
in them: `impact.md` §2 and §8, the flaky-timing bullet of plan §10, and the Level 5 fields of §5 below.
- **The lost test.** `FixtureRecorderIntegrationTest.nameFor names the file and a bad group is refused at wrap` was
  `= runBlocking { ... }` ending in `assertFailsWith`, so the method returned a value and JUnit Jupiter never
  discovered it (16 `@Test` in source, 15 in the XML). Now `runBlocking<Unit>` with a comment. A scan of every
  expression-bodied `runBlocking`/`runTest` test in the change set found no other non-Unit ending (the two secrecy
  candidates end in the Unit-returning `assertClean`). `./gradlew :webtools-http:integrationTest --rerun` - BUILD
  SUCCESSFUL; `FixtureRecorderIntegrationTest` **16** tests, 0 failures, the `nameFor` case present and passing.
  **Count corrections** to the Amendment-3 record above: FixtureRecorderIntegrationTest 16 (not 15); fixtures
  classes 141 tests (not 140); `jvmTest` 272 (not 271). The earlier figures were the executed counts.
- **Doc items (14):** `FixtureRecorder` KDoc - the move is atomic where the file system supports it (otherwise a
  plain replace), and the refused-write log line carries the class name with the exception attached for its stack
  trace (an I/O exception's message is a path; a scrubber refusal attaches nothing); the `noteRefused` comment the
  same (code item 2 and 3); `Fixture.parse` `@throws` ("for a syntax fault, the line"); `VirtualClock.timeSource`
  (marks move with the scheduler's virtual time, shared scheduler included); `ResolutionReport.read` `@return`
  (`file` line too); `ModuleSource` (absolute path of the sibling directory); `NestedBuild` `@param workspace`;
  one-line KDocs on `Redaction.keep`/`extra`/`scrubber`/`isSensitiveQuery` and `Step.produce`; the `e2eTest`
  description (three classes); the 1 ms floor explained in the linear-time test and its KDoc; "any loser lands in
  refused; the file is whole either way"; the deprecated `JcaPGPKeyPair` constructor commented; the forward-slash
  comment in the wiring test; `//language=RegExp` on `lowerHex`.
- **Test items (5 taken):** the gating consumer builds its recorder on an unresolved `Path.of("gating")` and no
  longer creates or deletes a directory (so the earlier "no file I/O" line now holds); `QueryPairsDeterministicTest`'s
  KDoc says the lone `%` piece also forms ill-formed escapes such as `%ab` and the fixed list pins the agreed cases;
  `FixturesPublicationEndToEndTest` test 4 asserts that no nested build's output holds the passphrase or any line of
  the armoured key (both kept as fields; the messages echo neither; still ten tests); the concurrency test's
  `awaitAll()`s run under `withTimeout(2.minutes)` as a hang bound; and the **guard mutation**: with
  `gradle.parent == null &&` removed from the edge in `webtools-http/build.gradle.kts`,
  `./gradlew :build-logic:e2eTest --tests '*FixturesCheckWiringEndToEndTest'` - BUILD FAILED, 2 tests, 1 failure:
  `a build that includes WebTools never schedules the publication check` ("the consumer dry run scheduled the
  publication check": the output listed `:webtools:build-logic:fixturesPublicationCheck SKIPPED`), while test 1
  still passed. So the parent check is load-bearing (an included WebTools build still includes its own `build-logic`)
  and test 2 discriminates it. The build file was restored byte-exact (md5 compared); not committed.
- Re-runs after the fixes: `./gradlew :webtools-http:check` - BUILD SUCCESSFUL in 1m 3s; `jvmTest` 272 tests in 54
  classes, 0 failures (fixtures classes 18 / 141: the counts above with FixtureRecorderIntegrationTest 16);
  `apiCheck`, `verifyJavaFloor` and `:build-logic:fixturesPublicationCheck` (10/0) executed under it.
  `./gradlew :build-logic:e2eTest` - BUILD SUCCESSFUL in 2m 4s; FixturesCheckWiringEndToEndTest 2/0,
  FixturesPublicationEndToEndTest 10/0, IncludedBuildEndToEndTest 5/0. No workspace left under
  `build-logic/build/testkit/projects`.

## 5. Level 5: the manual pass

Procedure P13 of plan §9.2. Items 4 and 6 were performed from the command line. Items 1, 2, 3 and 5 need an
IntelliJ session and were **not performed**.

- **Owner of items 1, 2, 3 and 5:** the maintainer.
- **Trigger:** the next IntelliJ import of the repository after #63 lands, and at the latest before unit 5
  (#64) starts. The result of each item is recorded under it here. Unit 3's three IDE items
  ([`../62-http-core/final-implementation.md`](../62-http-core/final-implementation.md) §6, items 1, 2 and 4)
  are still open and need the same session.

1. **IntelliJ imports the repository; the `jvmTestFixtures` source set resolves `main`'s internals; `jvmTest`
   resolves the fixtures; no spurious red** — **not performed** (IDE).
   - *Requirement it checks:* R1, the compilation and its two associations, as a developer sees them.
   - *Why IDE-only:* IntelliJ builds its own project model from the Gradle import; only an IDE session shows its
     resolution and its highlighting.
   - *Meanwhile:* the Gradle model the IDE imports is consistent. The fixtures compile against `main`'s
     `internal` helpers and `jvmTest` compiles against the fixtures (P2 step 1), the level tasks run the
     fixtures' tests (P3), and `:tasks --all` lists the six tasks of the new compilation (P8 step 2).
2. **A scratch implementer of `HttpTransport` without the opt-in shows the gate error and the `@OptIn` quick
   fix** — **not performed** (IDE).
   - *Requirement it checks:* R3, an implementer in the fixtures without `@OptIn` fails to compile.
   - *Why IDE-only:* the quick fix is an IDE intention; the command line shows only the diagnostic.
   - *Meanwhile:* the compiler's text for exactly this case is in P2 step 3, and the class compiled once
     `@OptIn` was added. The IDE renders the same diagnostic.
3. **Quick Documentation of `VirtualClock`, `FakeHttpTransport`, `RequestMatch`, `Fixture`, `RecordingGate` and
   `FixtureRecorder` as a consumer sees them** — **not performed** (IDE).
   - *Requirement it checks:* R2, KDoc that names the tier, the contract, the thread-safety, the behaviour under
     virtual time and what `toString()` never prints.
   - *Why IDE-only:* Quick Documentation is the IDE's rendering of the KDoc.
   - *Meanwhile:* P12's coverage check finds a KDoc block on every public declaration. The documentation
     reviews of both qa passes read every block against the code, and their findings are fixed (§4).
4. **The check's published `.module` and POM, read in its workspace** — **done**.
   - *Performer:* the implementer, from the command line on the maintainer's host, 2026-10-25, on the change
     as built above `5d98102`.
   - *Run:* `./gradlew :build-logic:fixturesPublicationCheck --rerun` with `closeWorkspace()` temporarily
     commented out, BUILD SUCCESSFUL in 48 s. The class was then restored byte-exact (md5 compared), and the
     kept workspace deleted.
   - *Read:*
     - The root module directory holds the main, sources, javadoc and `-test-fixtures` jars, the `.module`, the
       POM and the kotlin-tooling-metadata, each with `.asc`, `.md5`, `.sha1`, `.sha256` and `.sha512`.
     - `testFixturesApiElements` and `testFixturesRuntimeElements` carry exactly the five recipe-B attributes
       (`jvm.version` 11), the one capability, the fixtures jar and the four dependencies.
     - The root POM lists `kotlinx-coroutines-test` and `mockwebserver3` as `compile` + `optional`; the `-jvm`
       POM lists neither.
     - The fixtures jar's `.asc` begins `-----BEGIN PGP SIGNATURE-----`.
     - The producer's `gradle.properties` held the two signing lines, and nothing else of the key reached a
       log. Test 4 of `FixturesPublicationEndToEndTest` asserts that last point on every run.
   - *Commit:* nothing of the run is committed; it read the change that the landing of #63 commits.
5. **What IntelliJ's HTTP Client plugin does to `*.http`** — **not performed** (IDE; a note only, per the
   plan).
   - *Requirement it checks:* none. It examines a known gap (§7).
   - *Why IDE-only:* the HTTP Client is an IDE plugin.
   - *Meanwhile:* nothing. The plugin treats `*.http` files as HTTP request files; what it shows for a recorded
     response is unexamined. The fixture files and the tests do not depend on it.
6. **`git status` and `git diff --stat` before each commit: no canary, no `build/`, no IDE file** — **done** on
   the working tree that goes to the landing.
   - *Performer:* the implementer, at the close of S8 (P12), 2026-10-25. The hygiene checks of both qa passes
     found the same.
   - *Re-check:* before each commit of the landing, on that commit's tree.

## 6. Research that didn't hold

No research claim was contradicted. The claims the implementation leaned on held as measured:
- recipe B on the module's real sources publishes exactly the specified variants, with no attribute beyond
  its five, and the root POM keeps the test libraries optional (mechanics F1, F8, F13; P5, P10);
- the guard: `gradle.parent == null` keeps the check out of a build that includes WebTools, and without it
  such a build schedules `:webtools:build-logic:fixturesPublicationCheck` (wiring F5, F7; the guard mutation in
  the qa pass 2 fixes);
- the injected plugin classpath reaches the composite consumer's included producer (mechanics F2, F7);
- signing with a generated throwaway key, and the `ORG_GRADLE_PROJECT_*` scrub (mechanics F3, F4, F5; P9);
- the assertions catch the faults they exist for (mechanics F10; P10);
- the check's cost of about a minute when an input changed: 44 to 52 s as built, against the research's 53
  to 78 s with a one-class fixtures compilation (mechanics F11; P7, P8);
- OkHttp 5.5.0's query decoding: the differential agreed on 100,000 queries and the ill-formed escapes matched
  exactly, so the fallback of plan §3 was not needed (P3 after S4);
- `kotlinx-coroutines-test` 1.10.2's stable API: neither the fixtures nor their tests need
  `@ExperimentalCoroutinesApi` (P12).

**Held with a caveat.** The research's key helper builds the key pair with
`JcaPGPKeyPair(Int, KeyPair, Date)`, which BouncyCastle 1.86 deprecates. It compiles with one warning and works;
`ThrowawayPgpKey` says so in a comment.

**One statement of the plan (not research) did not hold.** Plan §7.1 handed the fixtures compilation's
`runtimeDependencyFiles` to `verifyJavaFloor` as they are. For an associated compilation that collection holds
`main`'s `output.allOutputs`, including two directories this module never creates, and the guard refuses a
root that does not exist (P6 step 2).
- *What it shaped:* the floor guard's view of the fixtures' runtime classpath (R8).
- *What it cost:* one stop in S6 and plan Amendment 2, a one-line filter in the module's build file.
  `build-logic` did not change.

## 7. Known gaps

- **IDE behaviour is untested** (§5 items 1, 2, 3 and 5): IntelliJ's import of the `jvmTestFixtures` source set
  and its associations, the gate's diagnostic and quick fix, Quick Documentation, and what the HTTP Client
  plugin shows for `*.http`. The owner and the trigger are in §5 and [`impact.md`](impact.md) §7.
- **Maven Central has not seen the fixtures.** Its upload and its validator meet the fixtures jar (no sources
  or javadoc jar of its own) and a multiplatform publication with an adhoc variant first at `webtools-http`'s
  first release. The check signs that publication on every run, and asserts an `.asc` beside every file and a
  PGP signature on the fixtures jar; it does not verify a signature.
- **The plugin classpath injected into an included build**, which the composite consumer relies on, is
  undocumented Gradle behaviour, measured on Gradle 9.7.1 only. A Gradle upgrade that changes it fails the
  check loudly.
- **BotTools' own composite** is not exercised: its checkout includes no WebTools build yet. The research
  reproduced its shape, a consumer with a `build-logic` of its own that includes the WebTools root.
  `FixturesCheckWiringEndToEndTest` proves both branches of the edge on a copy at every `e2eTest`, and P8
  step 5 ran a scratch composite against the live checkout once.
- **An empty TestKit home** was not run with the as-built check. Its downloads (109 files, 73 MB) are the
  research's measurement.
- **Only this host** ran the as-built build: Windows, JDK 23, Gradle 9.7.1. The research's probes also ran on
  JDK 17 and 21. JDK 24 to 26, Linux, macOS and CI did not run, and `ATOMIC_MOVE` over an existing fixture ran
  on NTFS only.
- **Concurrent recordings to one name:** the test releases eight callers together but cannot force two moves
  to overlap. Its assertions hold either way.
- **The environment scrub** covers `ORG_GRADLE_PROJECT_*` only. `org.gradle.project.*` system properties set
  through `JAVA_TOOL_OPTIONS` or `GRADLE_OPTS` reach a nested build (plan R9).
- **The gate has no permanent compile-fail check** (plan §13). The opt-in canary of P2 ran once.
- **The recorder has not run against the real transport.** It records through the fake; a live recording is
  the consumer's own self-skipping `uat` class. The dropped headers, the sensitive tokens, the twelve CI
  variables and `warnAboveBytes` are judgement.
- **Not exercised:** Isolated Projects, parallel execution, and a composite nested more than one level deep.
  Gradle's deprecation notice ("Deprecated Gradle features were used"), which the research saw in every
  nested build, is unattributed.

The build closed four of plan §14's gaps: the code blocks compile (P2, P7); the real fixtures' compile time
is measured, the check taking 44 to 52 s; the workspace is deleted on Windows after each run §4 records (P7,
and the qa re-runs); and the ill-formed percent-escapes decode exactly as `HttpUrl` decodes them (P3 after S4).
