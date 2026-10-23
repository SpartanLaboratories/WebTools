# Issue #47 — the Java 11 floor (impact)

- **Covers:** `SpartanLaboratories/WebTools#47`, *"webtools-udp 2.0.0 ships Java 23 bytecode but README
  claims JDK 11+"*. It is unit 2, `java-floor`, of [#55's architecture](../architecture.md) (§6.1, §11).
- **Level:** 2. This file covers what the repository, its published artifacts and the sibling units
  gain or lose with this unit as built.
  - The file-level detail is in [`plan.md`](plan.md).
  - The as-built record, with every measurement, is in
    [`final-implementation.md`](final-implementation.md).
- **Baseline:** `master` at `36fd879`. The unit landed in PR #76 (merge `3949b52`).
- **Related docs:** [`plan.md`](plan.md); [`final-implementation.md`](final-implementation.md);
  [`research/README.md`](research/README.md); the systems-level [`../impact.md`](../impact.md).

## 1. Published artifacts

| Artifact | What changes | Version | What a consumer sees |
|---|---|---|---|
| `webtools-udp` | Class files are major 55 (98 classes; 67 on Central). `org.gradle.jvm.version` is 11 on `apiElements` and `runtimeElements` (23 on Central). Source, public API (`javap -protected`) and POM are identical; the `.module` file differs only in the JVM version. | `2.0.0a`, unreleased. The floor rides in it, and the note above its `version` line says so. Central holds 1.0.0, 1.1.0 and 2.0.0, all Java 23. | JDK 11 to 22 can load it once it is published. `2.0.0a` orders below `2.0.0`. |
| `webtools-scraping` | The same, for 4 classes. No source change. | `1.0.0` → `1.0.0a`, unreleased | As above. `1.0.0a` orders below `1.0.0`. |
| `webtools-browser` | The same, for 2 classes. No source change. | `1.0.0` → `1.0.0a`, unreleased | As for `webtools-scraping`. |

Nothing is published or signed. Maven Central still serves only the Java 23 builds. For the two legacy
modules the POM differs only in `<version>`, and the `.module` file also in the version and the file names.
`build-logic` is not published.

## 2. Blast radius in this repository

### Build

- **The floor lives in two places.** `WebToolsBuild.JAVA_FLOOR` holds the number, and the two convention
  plugins set `jvmTarget`, `-Xjdk-release`, the Java compatibility (Kotlin/JVM) and the `TargetJvmVersion`
  attribute (multiplatform) from it.
  - All three modules sit on `webtools.kotlin-jvm-library`, so each got the floor with no line of its own.
  - None can set its own target: `verifyJavaFloor` would fail it.
- **`check` and `build` do a little more per module.** `verifyJavaFloor` builds the sources and javadoc jars
  and the metadata (`build` already did) and scans the module's jar and runtime classpath, about a second
  for the largest module. The task is up to date afterwards.
- **`check` of a module still runs its whole suite.** The guard is therefore run by name,
  `./gradlew verifyJavaFloor`, which covers every module without running any module's tests.
- **The ASM pin is gone.** The multiplatform plugin's validator runs on its defaults. No module applies that
  plugin yet.
- **`build-logic`'s tests grow from 49 to 93.**
  - `:build-logic:test` runs 47 (15 before), `integrationTest` 39 (28), `e2eTest` 5 (4) and
    `nonfunctionalTest` 2.
  - No Gradle test task and no `testKitHomeLock` change; the new TestKit classes run under the existing
    `integrationTest`.
  - `JarClasses` is new harness: it reads class-file majors from a jar, independent of the production scan.
- **Build host.** Gradle 9.7.1 builds on JDK 17 to 26 and the level tests run on that JDK; no JDK 11 is
  involved. The floor was exercised on JDK 23 and JDK 17.
- **Unchanged:** the root `build.gradle.kts`, `settings.gradle.kts`, `build-logic/build.gradle.kts`,
  `gradle.properties` and every module source file.

### Source and resources

- No main or test source file of the three modules changes. Their `javap -protected` output is identical.
- The three module build files gain notes. `webtools-scraping` (`:22`) and `webtools-browser` (`:21`) gain
  `version = "1.0.0a"`. `webtools-udp`'s `version` line (`:116`) and port lock are untouched.

### Documentation

- **`README.md`.**
  - The install block names versions that are on Central: `webtools-udp:2.0.0`, `webtools-scraping:1.0.0`
    and `webtools-browser:1.0.0`.
  - `Requires JDK 11 or newer` (`:29`) is unchanged. A paragraph below it says the floor starts with
    `2.0.0a` and `1.0.0a`, that the Central releases are Java 23 bytecode, and that a lettered version orders
    below the plain one.
  - A "Java floor" section under Build & test holds the build-JDK note.
  - The command list gains `./gradlew verifyJavaFloor`, and the plugin table names the floor and the guard.
- **`docs/dashboards/`** drew the floor as planned; the presenter's landing refresh (PR #77) brought it to
  the as-built state.
- **`docs/webtools-udp-architecture.md` and `docs/webtools-udp-protocol.md`** are unchanged. Neither names
  the JDK.
- **[`../impact.md`](../impact.md)** is reconciled with this unit (§1, §2, §5 to §8).
  [`../architecture.md`](../architecture.md) §12 is left as written; §3 below says which of its sentences
  does not hold.

## 3. Adoption

- **Consumers on JDK 11 to 22** can load the three artifacts once the lettered versions are published. Until
  then Central serves only Java 23 builds, and the README says which releases carry the floor.
- **Lettered versions order below the plain ones, for all three artifacts.** `1.0.0a` orders below `1.0.0`,
  and `2.0.0a` below `2.0.0`; the plain versions on Central are the Java 23 builds.
  - A dependency graph that also asks for the plain version silently keeps the Java 23 build unless the
    lettered version is requested `strictly`.
  - The README says so, and so will the release note.
  - [`../architecture.md`](../architecture.md) §12 calls `webtools-udp` unaffected. That sentence does not
    hold: the ordering rule applies to `2.0.0a` too.
- **The README's install block names the Central releases until the release.** The release that publishes
  `2.0.0a`, `1.0.0a` and `1.0.0a` is a separate step that needs the maintainer's explicit instruction. It
  moves the three install lines to the lettered versions.
- **BotTools** (`SpartanLaboratories/KotlinBottools`, JDK 21) was not blocked by the Java 23 bytecode and
  needs nothing. No work in that repository is designed here.
- **Inside the repository** nothing else adopts or opts out: every module on either library plugin carries
  the floor, and a later module needs no line to get it.

## 4. What #62 and the later units inherit

**Unit 3, `http-core` (#62).** It is the first module on `webtools.kotlin-multiplatform-library`, and it was
blocked by this unit. It inherits:
- `WebToolsBuild.JAVA_FLOOR`, a `const val` of 11, readable from its build script.
- A plugin that compiles the `jvm()` target with `jvmTarget` 11 and `-Xjdk-release=11`, for `main`, `test` and
  the common code the target compiles.
- The `TargetJvmVersion` attribute at 11. KGP copies it onto `jvmCompileClasspath`, `jvmRuntimeClasspath`,
  `jvmTestCompileClasspath` and `jvmTestRuntimeClasspath`; `MultiplatformJavaFloorIntegrationTest` asserts
  that each requests 11. Gradle therefore rejects any dependency, test-only ones included, whose metadata
  declares a higher JVM version. Every dependency #55's research listed is at or below 11.
- `verifyJavaFloor` in `check`, over `jvmJar`, every publication's metadata and `jvmRuntimeClasspath`.
- The validator on its defaults, with no ASM pin: `apiDump` passes on JDK 17 and 23 hosts.
- Four things it must not do: set its own `jvmTarget`, toolchain, `-Xjdk-release` flag or `TargetJvmVersion`;
  declare a second JVM-based target; call a JDK API newer than 11; use `@JvmRecord` (it needs JVM 16). Its
  tests run on the build JDK.

**Unit 4, `test-support` (#63).**
- The target's attribute does not reach hand-declared variants. The fixtures variants set `TargetJvmVersion`
  from `WebToolsBuild.JAVA_FLOOR` themselves.
- The fixtures jar joins the guard with
  `tasks.named<VerifyJavaFloor>("verifyJavaFloor") { jars.from(<the jar's archiveFile>) }`. The fixtures
  publication's `.module` file is picked up without a change, because the guard reads every
  `GenerateModuleMetadata` output of the project. The publication check expects 11 on both variants.
- `JarClasses.majors(jar)` is there to read a class-file major from a built jar.

**Unit 10, `feeds`.** It applies the plugin as unit 3 does. The JS check modules apply
`webtools.kotlin-common-check`, not a library plugin, so the floor and the guard do not reach them.

## 5. Neighbouring issues

- **#55** is the tracking issue; this is its unit 2.
- **#61** (`build-logic`, unit 1) blocked this unit and landed first.
- **#62** (`http-core`, unit 3) was blocked by this unit and can start.
- **#58** (CI, CONTRIBUTING, CHANGELOG, tags, the foojay bump). Its CI would be the first place the guard
  and `build-logic`'s tests run on every change. Its foojay bump is what a JDK 11 test launcher waits for.

## 6. Follow-ups

- **`floor-scan-message`** (optional): `JavaFloorScan` reads a non-numeric `org.gradle.jvm.version` as
  absent, so a string `"11"` is reported as `declares org.gradle.jvm.version nothing, not 11`. Make the
  message name the value found, and change its pinning test with it.
- **`jdk11-test-launcher`:** run the level tasks on a JDK 11, after #58's foojay bump.
- **The release of `2.0.0a`, `1.0.0a` and `1.0.0a`:** it moves the README's three install lines to the
  lettered versions and carries the `strictly` note into the release note.
- **The wording of [`../architecture.md`](../architecture.md) §12 about `webtools-udp`:** a dated callout on
  that document needs the maintainer's approval.

## 7. Known gaps

- **Hosts.** Windows only. Oracle JDK 23.0.2 and Temurin 17.0.20 as build JDKs; JDK 24 to 26, other
  distributions and Linux are not exercised.
- **The API limit has no byte-level guard.** It rests on `-Xjdk-release`; the guard checks class-file
  versions and metadata.
- **Dependencies are scanned by class-file version, not loaded** on JDK 11.
- **No module test suite runs on JDK 11.**
- **No repository module applies the multiplatform plugin yet.**
- **IntelliJ.** Whether the IDE honours `-Xjdk-release` is not checked.
