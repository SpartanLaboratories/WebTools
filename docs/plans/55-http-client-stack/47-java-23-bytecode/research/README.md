# Research: Java 11 floor for every module

[SpartanLaboratories/WebTools#47](https://github.com/SpartanLaboratories/WebTools/issues/47),
resolved as part of [#55](https://github.com/SpartanLaboratories/WebTools/issues/55) (the HTTP
client stack; its research is in [`../../research/`](../../research/README.md)).

## The work

Every WebTools module targets Java 11, and the setting lives once in the root build.

- **Bytecode and metadata.** Each published artifact's class files are Java 11 bytecode (major
  version 55) or lower. Its Gradle Module Metadata declares `org.gradle.jvm.version: 11`.
- **API surface.** No JDK API newer than 11 is compiled in.
- **The three existing modules.** `webtools-udp`, `webtools-scraping` and `webtools-browser`
  are Kotlin/JVM modules.
- **The new modules.** `webtools-http` and the feeds module are Kotlin Multiplatform modules,
  and the floor applies to their JVM target.
- **README.** The README's "Requires JDK 11 or newer" then becomes true. Today every published
  artifact is Java 23 bytecode (class-file major version 67), and each `.module` file on Maven
  Central declares `org.gradle.jvm.version: 23`. The artifacts concerned are `webtools-udp`
  2.0.0, `webtools-scraping` 1.0.0 and `webtools-browser` 1.0.0.
- **Legacy modules.** `webtools-scraping` and `webtools-browser` are legacy. If the floor forces
  an edit to them, their public API may break outright. `webtools-udp` keeps its public API.
- **Build state.** The build runs on Gradle 9.7.1 with Kotlin 2.2.0.
  - No build file sets `jvmToolchain`, `jvmTarget` or a Java `release`.
  - `settings.gradle.kts` applies `foojay-resolver-convention` 0.8.0.
  - The machine that publishes runs JDK 23, and Gradle has already provisioned JDK 8, 12, 17
    and 21 under `~/.gradle/jdks`.

## Questions

### 1. How should every module be compiled and published for Java 11, with the setting in one place?

- **Options:**
  - **Compile settings on the build JDK.** Kotlin `jvmTarget = 11` with `-Xjdk-release=11`,
    plus the Java `release` or `targetCompatibility` that sets the variant attribute.
  - **A JDK 11 toolchain** (`jvmToolchain(11)`), provisioned through the foojay resolver. That
    means 0.8.0 as it stands on Gradle 9.7.1, or 1.0.0.
  - **The multiplatform JVM target.** Its `jvmTarget`, plus an explicit
    `org.gradle.jvm.version` attribute, since a multiplatform JVM publication does not write one
    by itself.
  - **Test JDK.** Whether the tests run on JDK 11 or on the build JDK.
- **Requirements:**
  - The floor is one setting that every module reads.
  - Every module's dependencies resolve and load for a Java 11 consumer. These are skrapeit
    1.1.5, jsoup 1.15.4, unirest-java 1.4.9, selenium-java 4.48.0, slf4j-api 2.0.13,
    `okhttp-jvm` 5.5.0 with okio, Caffeine 3.3.1, kotlinx-coroutines 1.10.2, the feed parser,
    and the test dependencies.
  - `webtools-udp` keeps its public API.
- **Evidence that settles it:**
  - The Kotlin and Gradle documentation for these settings.
  - Each dependency's published class-file version and `org.gradle.jvm.version` attribute.
  - The existing sources checked against the JDK 11 API, by scan or probe compile.
  - The foojay plugin's compatibility notes for Gradle 9.
- **Findings:** [`java-11-floor.md`](java-11-floor.md)

## Verification

- **Outside-world claims.** All 17 load-bearing claims in [`java-11-floor.md`](java-11-floor.md)
  were re-opened at their sources on 2026-10-21, and all 17 hold. KT-49746 (Gradle-toolchain
  support for `-Xjdk-release`) is still in Backlog. foojay-resolver-convention 1.0.0 is still the
  latest release. The verification of the whole stack's research is recorded in
  [`../../research/README.md`](../../research/README.md#verification).
- **Repository claims.** The ones the findings rest on were checked against `master` at `806ce8e`:
  - `settings.gradle.kts:2` applies foojay-resolver-convention 0.8.0.
  - `webtools-udp/build.gradle.kts:101` sets `2.0.0a`.
  - The root `allprojects` block sets the default `1.0.0` (`build.gradle.kts:14`).
  - `README.md:26` is the repository's only statement of a JDK floor.

## Summary

- **Mechanism.** Every module compiles with `jvmTarget = 11` plus `-Xjdk-release=11` on the build
  JDK, read from one constant, with no toolchain.
  - **`kotlin("jvm")` modules** also set `java { sourceCompatibility; targetCompatibility }`. The
    Kotlin plugin's JVM-target validation fails without it, and it is also what writes
    `org.gradle.jvm.version: 11`.
  - **Multiplatform `jvm()` targets** set the `TargetJvmVersion` attribute explicitly, because
    they write none under any setting. The test-fixtures variants do the same.
  - **Why the flag is needed.** `jvmTarget` alone lowers the class-file version but not the API
    surface. Measured on a JDK 23 host, `MutableList.removeFirst()` compiled to JDK 21's
    `List.removeFirst`, with no warning.
- **The existing modules survive unchanged.**
  - `webtools-udp`, `webtools-scraping` and `webtools-browser` compile under `-Xjdk-release=11`
    with zero errors.
  - Their public and protected signatures are identical to today's build.
  - Their tests pass on a real JDK 11.
  - No legacy API breaks.
- **Every dependency is at or below Java 11.** The highest base class-file major on any candidate
  classpath is 55. Every dependency that publishes Gradle Module Metadata declares 11 or lower.
- **No toolchain and no JDK 11 test launcher.**
  - Either would need foojay-resolver-convention 1.0.0, because 0.8.0 fails on Gradle 9.7.1 as
    soon as a JDK must be provisioned.
  - A toolchain would also make a consumer's composite build need a JDK 11.
  - Running the level tests on a JDK 11 launcher stays a later step, after the resolver bump that
    WebTools#58 owns.
- **Guard.** A `verifyJavaFloor` task, wired into `check`, fails when it finds:
  - a class above major 55 in the built jars;
  - an API or runtime variant in the generated `.module` files that does not declare 11;
  - a class above major 55 on the resolved runtime classpath.
- **README.** `README.md:26` ("Requires JDK 11 or newer") becomes true as written.
- **Versions.** The floor changes no source and no API, so it is a fix.
  - `webtools-udp` carries it in its unreleased `2.0.0a`.
  - `webtools-scraping` and `webtools-browser` take their next bug-fix version. `1.0.0a` orders
    below the Java 23 `1.0.0` in Gradle, so a consumer that needs the fix requests it with
    `strictly`, per the organisation's rule for lettered versions.

### Known gaps

- **Environments tested.** Only Windows build hosts were exercised: Oracle JDK 23 and
  Temurin 21. `-Xjdk-release` is an `-X` flag, which Kotlin documents as "not guaranteed to be
  effective for each JDK distribution".
- **Composite provisioning.** A consumer's composite build with JDK provisioning enabled and
  JDK 11 absent was not measured.
