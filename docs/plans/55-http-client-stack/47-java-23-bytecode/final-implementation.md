# Final implementation: the Java 11 floor (Issue #47)

The as-built record of `SpartanLaboratories/WebTools#47`, unit 2 (`java-floor`) of
[#55's architecture](../architecture.md). It states what was built, where the work departs from
[`plan.md`](plan.md) and why, and the measurements that show it holds. The work landed in PR #76
(merge `3949b52`, 2026-10-22). What it changes for the repository and for the sibling units is in
[`impact.md`](impact.md).

- **Branch and PR:** `fix/issue-47-java-floor`, cut from `master` at `36fd879`; PR #76, merged as
  `3949b52`. The dashboards followed in PR #77 (`266c1c9`).
- **Commits of PR #76:**
  - `a96d969` `fix: compile every module to the Java 11 floor and guard it with verifyJavaFloor`:
    the plan, `build-logic`'s sources and every test;
  - `e249f52` `build: bump webtools-scraping and webtools-browser to 1.0.0a and note the floor in webtools-udp 2.0.0a`;
  - `9e47ac0` `docs: state the Java floor and name the installable versions in the README`;
  - `7ca020f` `docs: record the as-built Java floor`: the implementer's record, which this file replaces;
  - `d28200e` `docs: update the plan boards for the Java floor`.
- **Hosts of the measurements:** Windows 11 and the Gradle 9.7.1 wrapper, on 2026-10-22.
  - The implementer ran on Oracle JDK 23.0.2 (`JAVA_HOME=C:\Program Files\Java\jdk-23`).
  - The reconciliation (§6.2) ran on the same host, after landing, on exports of the merged tree
    (`266c1c9`) and of the baseline (`36fd879`) under a scratch directory, with a Gradle user home of
    its own that holds no credentials and a TestKit home inside the export. It used Oracle JDK 23.0.2,
    Temurin 17.0.20 and Temurin 11.0.32.1; the last two are the JDKs Gradle provisioned under
    `~/.gradle/jdks`.
- **Nothing published or signed.** Versions moved: `webtools-scraping` and `webtools-browser`
  `1.0.0` → `1.0.0a`; `webtools-udp` stays `2.0.0a` (unreleased).
- **No test task of `webtools-udp`, `webtools-scraping` or `webtools-browser` ran**, before or after,
  in any run recorded here. Only `build-logic`'s own tasks and the modules' compile, jar, metadata
  and `verifyJavaFloor` tasks, by name.

## 1. What was built

### The floor

- **`build-logic/src/main/kotlin/WebToolsBuild.kt`** gains:
  - `JAVA_FLOOR: Int = 11`, beside `LEVEL_PACKAGE_ROOT`, and `VERIFY_JAVA_FLOOR_TASK`
    (`"verifyJavaFloor"`);
  - `classFileMajor(javaRelease)`: the release plus 44, so 55 for the floor;
  - `registerVerifyJavaFloor(project, jars, runtimeClasspath)`, which registers the `verifyJavaFloor`
    task in the `verification` group. The task carries the floor, the jars, the runtime classpath, a
    report file and every `GenerateModuleMetadata` output of the project. That collection is live: it
    is read when the task graph is built, so publications that vanniktech and the Kotlin plugin create
    after evaluation are included without an `afterEvaluate`.
- **`webtools.kotlin-jvm-library`** sets, from the constant, `jvmTarget`, `-Xjdk-release` and
  `java { sourceCompatibility; targetCompatibility }`. It registers `verifyJavaFloor` over the `jar` task
  and the `runtimeClasspath` configuration and makes `check` depend on it. The `TODO(#47)` and the
  header sentence that class files follow the build JDK are gone.
- **`webtools.kotlin-multiplatform-library`** sets `jvmTarget` and `-Xjdk-release` inside `jvm {}` and the
  `TargetJvmVersion` attribute from the constant; the script's own `targetJvmVersion` value is gone. A
  `java floor` region after `publishing` registers `verifyJavaFloor` over `jvmJar` and the main
  compilation's `runtimeDependencyFiles` (a missing one fails the build at configuration, so the guard
  never runs blind) and makes `check` depend on it.
- **The ASM pin is removed.** The `binary compatibility` region, which held the two `bcv-rt-jvm-cp` lines,
  is gone: with the floor every class is major 55, which the validator's own ASM reads on any build JDK.

### The guard

- **`JavaFloorScan.kt`** holds the immutable `FloorScan(checked, violations)` and the object
  `JavaFloorScan` with two functions that return `Result<FloorScan>`:
  - `classFiles(root, maxMajor)` reads the eight-byte header of every `.class` entry of a jar or a class
    directory, outside `META-INF/versions/`, and reports a missing magic number or a major version above
    the limit;
  - `moduleMetadata(file, javaFloor)` reads a Gradle Module Metadata file and reports every library
    variant (`org.gradle.usage` of `java-api` or `java-runtime`, `org.gradle.category` not
    `documentation`) whose `org.gradle.jvm.version` is not the floor, labelled with its publication
    directory (`jvm/module.json`).
  - A failure is an unreadable input: a missing file, a non-zip, non-JSON, or no `variants` list.
- **`VerifyJavaFloor.kt`** is the typed task. It unwraps the scans at one boundary and throws
  `GradleException` for a violation (the first 20 are listed, then a count), an unreadable input, or
  nothing to check (no class in the jars, no library variant in the metadata). It deletes its report first
  and writes the one-line summary only on success, so an unchanged module is up to date and a failing run
  leaves no stale report.

### Versions and the README

- `webtools-scraping` and `webtools-browser` set `version = "1.0.0a"` with a note; `webtools-udp`'s
  `2.0.0a` note gains the floor. Its `version` line (`:116`) and its port lock are untouched.
- `README.md`:
  - the install block names versions that are on Maven Central: `webtools-udp:2.0.0`,
    `webtools-scraping:1.0.0`, `webtools-browser:1.0.0`;
  - `Requires JDK 11 or newer` is unchanged and followed by a paragraph that says the floor starts with
    `webtools-udp` 2.0.0a and `webtools-scraping` / `webtools-browser` 1.0.0a, that the releases on Central are
    Java 23 bytecode, and that a lettered version orders below the same version without the letter, for
    all three artifacts, so a graph that also asks for the plain version needs `strictly`;
  - a "Java floor" section under Build & test, which holds the build-JDK note (JDK 17 to 26; the level
    tests run on it; no JDK 11);
  - `./gradlew verifyJavaFloor` in the command list; the two plugin rows name the floor and the guard.

### Tests

All in `build-logic`, as the plan lists them. The counts are those of the merged tree.

| Level | Class | Tests |
|---|---|---|
| 2 | `JavaFloorScanComponentTest` (new) | 18 |
| 2 | `VerifyJavaFloorComponentTest` (new, from qa pass 1) | 7 |
| 2 | `RegisterVerifyJavaFloorComponentTest` (new) | 4 |
| 4a | `WebToolsBuildJavaFloorDeterministicTest` (new) | 3 |
| 3 | `PublishedJvmVersionAttributeIntegrationTest` (`expectedJvmVersion` is the literal `11`) | 2 |
| 3 | `JvmJavaFloorIntegrationTest` (new) | 5 |
| 3 | `MultiplatformJavaFloorIntegrationTest` (new) | 6 |
| 3 | `ApiValidationIntegrationTest` (KDoc only) | 5 |
| 4b | `IncludedBuildEndToEndTest` (one test new) | 5 |
| 4c | the two configuration-cache classes (they also run `verifyJavaFloor`) | 1 + 1 |
| support | `JarClasses` (new): reads class-file majors from a jar, independent of the production scan | — |

`:build-logic:test` runs 47 tests (15 before this unit), `integrationTest` 39 (28), `e2eTest` 5 (4) and
`nonfunctionalTest` 2 (2): 93 in all, from 49. No Gradle test task was added, so `testKitHomeLock` is
unchanged.

## 2. Deviations from the plan

None from the plan's letter. The plan's 8 new files and 12 diffs were applied to a clean `36fd879`
export and compared with the merged tree: every file the plan touches is identical except the five
that qa pass 1 changed (§3), and one test class (`VerifyJavaFloorComponentTest`) that the plan does not
list. So the plan's collapsed blocks are the versions before qa pass 1; the tree is authoritative for
the nine changes below. `README.md` kept its CRLF working-tree line endings; the Kotlin and Gradle files
are LF.

## 3. qa pass 1 — minor fixes

Applied by the implementer on 2026-10-22, on `fix/issue-47-java-floor` (7 documentation, 2 test). No
production logic changed, only comments, KDoc, the README and tests.

### Doc fixes (`build-logic/src/main/kotlin/`)

1. `JavaFloorScan.kt`, `data class FloorScan` KDoc: added "Immutable: a scan is a value, and [plus] returns a new one."
2. `JavaFloorScan.kt`, `FloorScan.plus` KDoc: kept the sentence; added `@param other` and `@return`.
3. `WebToolsBuild.kt`, `registerVerifyJavaFloor` KDoc: added that on success the task writes its one-line
   summary to `build/reports/java-floor/verifyJavaFloor.txt` and on failure writes nothing. `README.md`'s
   "Java floor" section names the same path in one clause (CRLF line endings kept).
4. `JavaFloorScan.kt`, `violation`: line comment above the `when` on `major - 44` being the inverse of
   `WebToolsBuild.classFileMajor`.
5. `JavaFloorScan.kt`, `u2`: line comment naming the class-file spec's u2 and the infix precedence.
6. `VerifyJavaFloor.kt`, `verify`: line comment above `report.get().asFile.delete()` on why the stale
   report goes first.
7. `VerifyJavaFloor.kt`, `moduleMetadata`: line comment on `@get:PathSensitive(PathSensitivity.NAME_ONLY)`:
   content plus file name is enough, the directory only serves the failure label.

### Test fixes

8. New `VerifyJavaFloorComponentTest` (Level 2, `com.spartanlabs.testing.component.webtools.buildlogic`):
   a `ProjectBuilder` project whose project dir is the `@TempDir` (so the report lands inside it; the
   project is built lazily because `@TempDir` is injected after construction), the task registered through
   `WebToolsBuild.registerVerifyJavaFloor`, `moduleMetadata` pointed at a written `module.json` with
   `from(...)`, and `verify()` called directly. Seven tests: the one-line report on success; "no class to
   verify"; "no library variant to verify"; a dependency class above the floor on `runtimeClasspath`;
   "cannot read" on a plain-text jar; 25 violations list 20 and end in "... and 5 more"; a stale report is
   deleted on a failing run.
9. `JavaFloorScanComponentTest`: two more `moduleMetadata` cases. A `[]` root is a failure. A string
   `"org.gradle.jvm.version": "11"` is one violation, and its message reads
   `declares org.gradle.jvm.version nothing, not 11`: the scan reads a non-number as absent. The test pins
   that wording; production code was not changed. See §8.

After the fixes, `./gradlew :build-logic:test --rerun`: `BUILD SUCCESSFUL in 10s`, 47 tests, 0 failures:
`JavaFloorScanComponentTest` 18 (was 16), `VerifyJavaFloorComponentTest` 7 (new),
`RegisterLevelTasksComponentTest` 7, `RegisterVerifyJavaFloorComponentTest` 4,
`WebToolsBuildJavaFloorDeterministicTest` 3, `WebToolsBuildLevelsDeterministicTest` 6,
`BuildLogicPluginsGatingTest` 2. No other test task ran.

## 4. Acceptance

| Plan requirement | Evidence |
|---|---|
| 1. Every class of a module's jar is major 55 | §6.2 C: 98, 4 and 2 classes, all 55 (67 before); the jar tests of both TestKit classes; §6.2 D |
| 2. Every library variant declares `org.gradle.jvm.version: 11` | §6.2 C (`.module` diff); `PublishedJvmVersionAttributeIntegrationTest` (the `jvm` and root metadata) |
| 3. Compiles against the Java 11 API only | the `HexFormat` test of each TestKit class (`a class added after Java 11 does not resolve`), on JDK 23 and on JDK 17; §6.2 A (all main and test sources compile under the flag, with no warning) |
| 4. One constant | the only non-comment occurrence of the number in `build-logic`'s production sources is `JAVA_FLOOR` (a search of the merged tree); `WebToolsBuildJavaFloorDeterministicTest` |
| 5. The guard | the TestKit classes (`check` dry run, success, a class above the floor, a variant at 17), `VerifyJavaFloorComponentTest` and `JavaFloorScanComponentTest` (every failure and the empty-input cases), `IncludedBuildEndToEndTest` (through the real `includeBuild`), the configuration-cache tests (reuse), `validatePlugins` |
| 6. Nothing else moves | §6.2 C: `javap -protected` identical for every class; `.module` and POM differences are only the ones the plan allows |
| 7. Versions | `webtools-scraping-1.0.0a.jar` and `webtools-browser-1.0.0a.jar`; `webtools-udp` still `2.0.0a`; the notes in the three build files |
| 8. README | §6.2 G and the diff read against Build & test (§7) |
| 9. No module suite runs | no module test task ran in any run recorded here |

## 5. Fail before, pass after

`PublishedJvmVersionAttributeIntegrationTest` (Level 3), with `expectedJvmVersion` changed from
`Runtime.version().feature()` to the literal `11`, before any production file was touched.

### Before the fix

Command:

```
./gradlew :build-logic:integrationTest --tests '*PublishedJvmVersionAttributeIntegrationTest*'
```

Result: `BUILD FAILED in 23s`, `2 tests completed, 2 failed`:

```
PublishedJvmVersionAttributeIntegrationTest > the root module metadata carries the JVM version on both jvm variants() FAILED
    org.opentest4j.AssertionFailedError at PublishedJvmVersionAttributeIntegrationTest.kt:61

PublishedJvmVersionAttributeIntegrationTest > the jvm module metadata carries the JVM version on both jvm variants() FAILED
    org.opentest4j.AssertionFailedError at PublishedJvmVersionAttributeIntegrationTest.kt:54
```

Both failing assertions (from `build-logic/build/test-results/integrationTest/TEST-*PublishedJvmVersionAttributeIntegrationTest.xml`):

```
org.opentest4j.AssertionFailedError: jvmApiElements-published ==> expected: <Success(11)> but was: <Success(23)>
```

This is the failure the plan's root cause predicts: the `TargetJvmVersion` attribute follows the build JDK (23).

### The wider red run (plan step 1)

With every test that names no new production symbol in place (`JarClasses`, the two new TestKit
classes, the edits to `IncludedBuildEndToEndTest` and the two configuration-cache tests), still on
the baseline production code:

```
./gradlew --continue :build-logic:integrationTest :build-logic:e2eTest :build-logic:nonfunctionalTest
```

Result: `BUILD FAILED in 2m 7s`; `integrationTest` 39 tests, 13 failed; `e2eTest` 5 tests, 2 failed;
`nonfunctionalTest` 2 tests, 2 failed (`initializationError`: the task `verifyJavaFloor` is unknown):
17 failures, the set the plan's *Regression test* section lists. The tests that run a build whose
outcome is under test use `NestedBuild.run`, so each fails on its own assertion: for example the jar's
classes were `{sample/Greeter.class=67}` where `{sample/Greeter.class=55}` is expected, the classpaths
requested 23, and the `HexFormat` source compiled where the test expects `:compileKotlin` to fail.

### After the fix

The same command, with every production change of the plan in place:

```
./gradlew :build-logic:integrationTest --tests '*PublishedJvmVersionAttributeIntegrationTest*'
```

Result: `BUILD SUCCESSFUL in 13s`; `PublishedJvmVersionAttributeIntegrationTest` 2 tests, 0 failures.

## 6. Measurements

### 6.1 The implementer's runs (2026-10-22, Oracle JDK 23.0.2)

| Command | Result |
|---|---|
| `./gradlew :build-logic:test` (plan step 2) | green, 38 tests: `JavaFloorScanComponentTest` 16, `RegisterLevelTasksComponentTest` 7, `RegisterVerifyJavaFloorComponentTest` 4, `WebToolsBuildJavaFloorDeterministicTest` 3, `WebToolsBuildLevelsDeterministicTest` 6, `BuildLogicPluginsGatingTest` 2 |
| `./gradlew --continue :build-logic:integrationTest :build-logic:e2eTest :build-logic:nonfunctionalTest` (plan step 3) | green, `BUILD SUCCESSFUL in 2m 25s`: 39, 5 and 2 tests, 0 failures |
| `./gradlew :webtools-udp:compileKotlin :webtools-udp:compileTestKotlin :webtools-scraping:compileKotlin :webtools-scraping:compileTestKotlin :webtools-browser:compileKotlin :webtools-browser:compileTestKotlin verifyJavaFloor jar generateMetadataFileForMavenPublication generatePomFileForMavenPublication :build-logic:validatePlugins` (procedures A and B, the build half of C, and `validatePlugins`) | `BUILD SUCCESSFUL in 21s`, no compiler warning in the output |

Procedure B's reports (`<module>/build/reports/java-floor/verifyJavaFloor.txt`):

```
webtools-udp:      java floor 11 (class-file major <= 55): 98 classes in 1 jar(s), 2 library variants in 1 metadata file(s), 1055 classes on the runtime classpath
webtools-scraping: java floor 11 (class-file major <= 55): 4 classes in 1 jar(s), 2 library variants in 1 metadata file(s), 13960 classes on the runtime classpath
webtools-browser:  java floor 11 (class-file major <= 55): 2 classes in 1 jar(s), 2 library variants in 1 metadata file(s), 11757 classes on the runtime classpath
```

### 6.2 The reconciliation runs (2026-10-22, after landing)

**A and B, on the merged tree, JDK 23.** `./gradlew :webtools-udp:testClasses :webtools-scraping:testClasses
:webtools-browser:testClasses verifyJavaFloor jar generateMetadataFileForMavenPublication
generatePomFileForMavenPublication :build-logic:validatePlugins`: `BUILD SUCCESSFUL in 1m 54s` (a cold Gradle
home), no warning, no module test task. The three reports read exactly as in §6.1.

**C. The published bytes against a `git archive 36fd879` build.** Both sides built `jar`,
`generateMetadataFileForMavenPublication` and `generatePomFileForMavenPublication` for the three modules.

| | Baseline `36fd879` | Merged tree |
|---|---|---|
| class-file majors | `webtools-udp` 98 × 67; `webtools-scraping` 4 × 67; `webtools-browser` 2 × 67 | 98 × 55; 4 × 55; 2 × 55; none under `META-INF/versions/` |
| `javap -protected`, every class | 909, 30 and 14 lines | identical: 0 differing lines for each module |

- **`.module`, size and checksum lines excluded** (they depend on the jar bytes):
  - `webtools-udp` differs in exactly two lines: `org.gradle.jvm.version` 23 → 11 on `apiElements` and
    on `runtimeElements`;
  - `webtools-scraping` and `webtools-browser` differ in those two lines, in `component.version`
    (`1.0.0` → `1.0.0a`), and in the file names and URLs that carry the version.
- **POMs:** `webtools-udp` identical; `webtools-scraping` and `webtools-browser` differ only in the
  `<version>` line.

**D. A real JDK 11.** Every class of each merged jar was loaded with `Class.forName(name, false, loader)` on
Temurin 11.0.32.1, the loader holding the jar and its resolved runtime classpath (3, 51 and 40 entries):

```
webtools-udp-2.0.0a.jar:      loaded=98 failed=0 on java 11.0.32.1
webtools-scraping-1.0.0a.jar: loaded=4  failed=0 on java 11.0.32.1
webtools-browser-1.0.0a.jar:  loaded=2  failed=0 on java 11.0.32.1
```

The control, the baseline jars, loads none: 98, 4 and 2 failures, each
`UnsupportedClassVersionError … (class file version 67.0), this version of the Java Runtime only recognizes
class file versions up to 55.0` — the failure the issue reports. The loader program and the classpath printer
are the throwaway scripts that `plan.md` lists; they are kept outside the repository.

**E. `build-logic` on both build JDKs.**
- With Temurin 17.0.20 as the build JDK: `:build-logic:test` (47 tests) and `:build-logic:integrationTest
  --tests '*JavaFloor*' --tests '*PublishedJvmVersion*' --tests '*ApiValidation*'` (18 tests:
  `PublishedJvmVersionAttributeIntegrationTest` 2, `ApiValidationIntegrationTest` 5, `JvmJavaFloorIntegrationTest` 5,
  `MultiplatformJavaFloorIntegrationTest` 6): `BUILD SUCCESSFUL in 1m 50s`, 0 failures. The floor, the
  `HexFormat` pair and `apiDump` without the ASM pin hold on the oldest JDK Gradle supports.
- With Oracle JDK 23.0.2: `:build-logic:test`, `:build-logic:integrationTest`, `:build-logic:e2eTest`,
  `:build-logic:nonfunctionalTest` and `:build-logic:validatePlugins`, all tasks re-run
  (`--rerun-tasks`): `BUILD SUCCESSFUL in 2m 54s`; 47, 39, 5 and 2 tests, 0 failures, 0 module test tasks.

**G. The install block against Maven Central.** The README's install block names `webtools-udp:2.0.0`,
`webtools-scraping:1.0.0` and `webtools-browser:1.0.0`. The `maven-metadata.xml` of each artifact, read on
2026-10-22 (21:44 local, 02:44 UTC the next day), lists `webtools-udp` 1.0.0, 1.1.0 and 2.0.0 (latest and
release 2.0.0, last updated 2026-09-26), `webtools-scraping` 1.0.0 (2026-09-06) and `webtools-browser`
1.0.0 (2026-09-06). Each named version is on Central; `2.0.0a`, `1.0.0a` and `1.0.0a` are not.

## 7. Level 5 — the manual pass

1. The README diff, read against the Build & test section and the build files: the install block, the
   paragraph under `Requires JDK 11 or newer`, the "Java floor" section, the command list and the plugin table
   agree with what the build does (the paths, the task name, JDK 17 to 26 as Gradle's range, no JDK 11 in the
   build or the tests).
2. Procedure D (§6.2): the three jars load on a real JDK 11.
3. Procedure G (§6.2): every version the install block names is on Maven Central.

## 8. Known nit

`JavaFloorScan.moduleMetadata` reads a non-numeric `org.gradle.jvm.version` as absent, so a string
`"11"` is reported as `declares org.gradle.jvm.version nothing, not 11`. Gradle writes numbers, so the case
does not arise from the build; the wording is wrong only for a hand-written file. The test
`a library variant whose attribute is a string is reported as declaring nothing` in `JavaFloorScanComponentTest`
pins it. If it is changed, the message should name the value found, and the test with it.

## 9. Research that didn't hold

- **`String.indent` as a JDK 12+ API the flag rejects**
  ([`research/java-11-floor.md`](research/java-11-floor.md), the bottom line and F1: "rejects every JDK 12+ API
  I tried (`HexFormat`, `String.indent`, `Thread.ofVirtual`)").
  - *What held and what didn't:* `HexFormat` is rejected under `-Xjdk-release=11` (the TestKit pair asserts
    it) and compiled against the baseline (the red run); `Thread.ofVirtual` was not measured again.
    `String.indent` is unresolved on every JDK, with or without the flag, because Kotlin does not expose
    the JDK-only members of `String`.
  - *What it shaped:* the oracle of the API-surface tests. The first version of the test used
    `String.indent` and passed against the baseline; the plan uses `java.util.HexFormat`, which is new in
    Java 17 and so present on every JDK Gradle runs on.
  - *What it cost:* nothing in the product. One test fixture was replaced before any implementation.
- **"`webtools-udp` is unaffected" by the lettered-version ordering**
  ([`research/java-11-floor.md`](research/java-11-floor.md), Consideration 2, repeated in
  [`../architecture.md`](../architecture.md) §12).
  - *What held and what didn't:* the ordering rule holds for all three artifacts. `2.0.0a` orders below
    `2.0.0`, which is the Java 23 build on Central, exactly as `1.0.0a` orders below `1.0.0`. The research's
    own F7 hazard bullet says "(or `2.0.0` for udp)".
  - *What it shaped:* the risk bullet in architecture §12 and the udp row of the systems impact §1.
  - *What it cost:* no code. The plan's risks, the README paragraph and the systems impact state the rule
    for all three artifacts. Architecture §12 still carries the old sentence; the systems impact §1 and §8 say
    which statement governs.

## 10. Known gaps

- **Hosts.** Windows only. Build JDKs exercised: Oracle JDK 23.0.2 (everything) and Temurin 17.0.20 (the
  fast gate and the floor TestKit classes); Temurin 21.0.9 in the research. JDK 24 to 26, other
  distributions (one without `ct.sym` data, where `-Xjdk-release` may not apply) and Linux are not
  exercised.
- **The API limit has no byte-level guard.** `verifyJavaFloor` checks class-file versions and metadata,
  not API references; the limit rests on `-Xjdk-release`, and the `HexFormat` tests are its only automated
  check.
- **Dependencies are scanned by class-file version, not loaded.** Procedure D loads the modules' own
  classes; the Selenium, htmlunit and Ktor paths the tests do not run are covered by their declared floors
  and class versions.
- **No module test suite runs on JDK 11.** A launcher needs the foojay bump (#58).
- **No repository module applies the multiplatform plugin yet.** Its floor and guard are exercised by the
  generated samples and the end-to-end composite until `webtools-http` (#62) applies it.
- **IntelliJ.** Whether the IDE honours `-Xjdk-release` when it analyses the sources is not checked.
- **The scan's message for a string-typed attribute** (§8).
