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
