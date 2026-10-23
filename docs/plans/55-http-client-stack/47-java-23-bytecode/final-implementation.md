# Final implementation: the Java 11 floor (Issue #47)

The as-built record of `SpartanLaboratories/WebTools#47`, unit 2 (`java-floor`) of
[#55's architecture](../architecture.md). The implementer appends each deviation from
[`plan.md`](plan.md) the moment it is made; the planner reconciles the file into the final
as-built once implementation ends.

- **Host of every measurement:** Windows 11, Oracle JDK 23.0.2 (`JAVA_HOME=C:\Program Files\Java\jdk-23`)
  and the Gradle 9.7.1 wrapper, on 2026-10-22.
- **Branch:** `fix/issue-47-java-floor`, cut from `master` at `36fd879`.
- **Nothing published or signed.**

## Regression test of record: fail before, pass after

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
17 failures, the set the plan's *Regression test* section lists.

### After the fix

The same command, with every production change of the plan in place:

```
./gradlew :build-logic:integrationTest --tests '*PublishedJvmVersionAttributeIntegrationTest*'
```

Result: `BUILD SUCCESSFUL in 13s`; `PublishedJvmVersionAttributeIntegrationTest` 2 tests, 0 failures.

## Build and tests run (implementer, 2026-10-22)

Only `build-logic`'s own suites and per-module tasks by name; no test task of `webtools-udp`,
`webtools-scraping` or `webtools-browser` ran, before or after.

| Command | Result |
|---|---|
| `./gradlew :build-logic:test` (plan step 2) | green, 38 tests: `JavaFloorScanComponentTest` 16, `RegisterLevelTasksComponentTest` 7, `RegisterVerifyJavaFloorComponentTest` 4, `WebToolsBuildJavaFloorDeterministicTest` 3, `WebToolsBuildLevelsDeterministicTest` 6, `BuildLogicPluginsGatingTest` 2 |
| `./gradlew --continue :build-logic:integrationTest :build-logic:e2eTest :build-logic:nonfunctionalTest` (plan step 3) | green, `BUILD SUCCESSFUL in 2m 25s`: 39, 5 and 2 tests, 0 failures |
| `./gradlew :webtools-udp:compileKotlin :webtools-udp:compileTestKotlin :webtools-scraping:compileKotlin :webtools-scraping:compileTestKotlin :webtools-browser:compileKotlin :webtools-browser:compileTestKotlin verifyJavaFloor jar generateMetadataFileForMavenPublication generatePomFileForMavenPublication :build-logic:validatePlugins` (procedures A, B, the build half of C, and E's `validatePlugins`) | `BUILD SUCCESSFUL in 21s`, no compiler warning in the output |

Procedure B's reports (`<module>/build/reports/java-floor/verifyJavaFloor.txt`):

```
webtools-udp:      java floor 11 (class-file major <= 55): 98 classes in 1 jar(s), 2 library variants in 1 metadata file(s), 1055 classes on the runtime classpath
webtools-scraping: java floor 11 (class-file major <= 55): 4 classes in 1 jar(s), 2 library variants in 1 metadata file(s), 13960 classes on the runtime classpath
webtools-browser:  java floor 11 (class-file major <= 55): 2 classes in 1 jar(s), 2 library variants in 1 metadata file(s), 11757 classes on the runtime classpath
```

Procedure C's evidence, read with a throwaway script from the built outputs: `webtools-udp-2.0.0a.jar`
holds 98 classes, `webtools-scraping-1.0.0a.jar` 4 and `webtools-browser-1.0.0a.jar` 2, every one
class-file major 55; each module's `build/publications/maven/module.json` declares
`org.gradle.jvm.version` 11 on `apiElements` and `runtimeElements` and none on `sourcesElements`.
(`build/libs/` also still holds the stale `webtools-scraping-1.0.0.jar` and `webtools-browser-1.0.0.jar`
from earlier builds of the baseline, every class major 67; they are not outputs of this tree.)

Not run by the implementer, left to the planner's reconciliation: procedure C's comparison against a
`git archive 36fd879` export (`javap -protected`, the `.module` and POM diffs), procedure D (loading
the jars on a real JDK 11), procedure E's JDK 17 run of the `JavaFloor`, `PublishedJvmVersion` and
`ApiValidation` tests, and procedure G's Maven Central check of the install-block versions.

## Deviations

None. Every file matches the plan's collapsed blocks: 8 new files created whole, 12 files changed as
the diffs show. `README.md` kept its CRLF working-tree line endings; the Kotlin and Gradle files are LF.

## qa pass 1 — minor fixes

Applied by the implementer on 2026-10-22, on `fix/issue-47-java-floor`; no production logic changed,
only comments, KDoc, the README and tests.

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
   that wording; production code was not changed, as instructed. A reviewer may want the message to say
   what was found (`"11"`) rather than `nothing`; that is left as a suggestion.

### Test results

`./gradlew :build-logic:test --rerun`: `BUILD SUCCESSFUL in 10s`, 47 tests, 0 failures:
`JavaFloorScanComponentTest` 18 (was 16), `VerifyJavaFloorComponentTest` 7 (new),
`RegisterLevelTasksComponentTest` 7, `RegisterVerifyJavaFloorComponentTest` 4,
`WebToolsBuildJavaFloorDeterministicTest` 3, `WebToolsBuildLevelsDeterministicTest` 6,
`BuildLogicPluginsGatingTest` 2. No other test task ran.
