# The fixtures-publication check: publishing a copy of `webtools-http` with its test-fixtures variant and resolving `testFixtures(...)` from it

## Question

How does a Gradle TestKit check, in `build-logic`'s test source set, publish a copy of the real
`webtools-http` (recipe B: a JVM `testFixtures` compilation, two hand-declared consumable configurations,
`kotlin.publishing.adhocSoftwareComponent`) to a build-local file repository, and resolve
`testFixtures("io.github.spartanlaboratories:webtools-http:1.0.0")` from that repository, through
`includeBuild`, and from a Kotlin Multiplatform module of the same build?

An answer has to meet these requirements:

- The published root module's metadata holds `testFixturesApiElements` and `testFixturesRuntimeElements`,
  each with the capability `io.github.spartanlaboratories:webtools-http-test-fixtures:1.0.0`,
  `org.gradle.jvm.version` 11 (from `WebToolsBuild.JAVA_FLOOR`), `webtools-http-1.0.0-test-fixtures.jar`, and
  the dependencies `webtools-http`, `kotlinx-coroutines-test` 1.10.2 and `mockwebserver3` 5.5.0.
- Neither main variant (root or `-jvm` module metadata) nor the `-jvm` POM lists `kotlinx-coroutines-test` or
  `mockwebserver3`; the root POM lists them only as `optional`.
- The file-repository consumer, the `includeBuild` consumer and the in-build Kotlin Multiplatform module
  resolve the fixtures variant, the fixtures jar and its dependencies on their test classpaths; a consumer
  without `testFixtures(...)` resolves neither test library.
- No signing key, credential or machine-local path is committed or read from the maintainer's environment;
  nothing is published outside the check's workspace; Maven Central is never contacted for an upload.
- Every nested build runs with the configuration cache on; the check runs on Windows with JDK 17 to 26 and
  shares the TestKit home of `build-logic`'s other nested-build checks; its warm run time and the cold run's
  downloads are stated.

## Bottom line

- **Producer: a copy of the module's build file and its `src/commonMain`, `src/jvmMain`,
  `src/jvmTestFixtures` as subproject `:webtools-http` of a generated build, with the convention plugins
  injected (`GradleRunner.withPluginClasspath()`), not a copy of `build-logic`.** Injection works for the
  producer, for the in-build Kotlin Multiplatform module, and for the producer when a consumer includes it
  (measured on Gradle 9.7.1; the last is undocumented). A `build-logic` copy through `pluginManagement` also
  passes but costs about 30 s more per warm run and adds no fixtures coverage. Recipe B needs no attribute
  beyond its own: both published variants carry `usage`, `category`, `libraryelements`, `dependency.bundling`
  and `jvm.version` only, and Kotlin/JVM, Kotlin Multiplatform (in-build and from the file repository) and
  Java consumers select them without `org.jetbrains.kotlin.platform.type` or `org.gradle.jvm.environment`.
- **Signing: generate a throwaway OpenPGP key at test time with BouncyCastle and give it to the producer
  through the generated `gradle.properties`.** vanniktech 0.36.0 accepts it; every file of both publications
  gets an `.asc`, `webtools-http-1.0.0-test-fixtures.jar.asc` included, which is the one release-shape fact
  no other option can assert. The cost is one test dependency (`org.bouncycastle:bcpg-jdk18on:1.86`, three
  jars, 8.7 MB, in the outer Gradle cache) and about 30 lines (14 imports, a 13-line function). Gradle's own
  BouncyCastle is visible through `gradleApi()` only as relocated `org.gradle.internal.impldep...` classes,
  which Gradle documents as not to be relied on. Disabling the `Sign` tasks (script or init script) also works
  and costs nothing, but cannot assert signatures; a `-SNAPSHOT` version contradicts the 1.0.0 capability.
- **The runner must scrub `ORG_GRADLE_PROJECT_*` from the environment it hands to nested builds.** An
  `ORG_GRADLE_PROJECT_signingInMemoryKey` in the environment of the build that runs the check reaches the
  nested build by default and overrides the key in the workspace's `gradle.properties` (measured: the
  producer fails with "Could not read PGP secret key"). `withEnvironment(System.getenv().filterKeys { !it.startsWith("ORG_GRADLE_PROJECT_") })`
  fixes it on Windows.
- **Consumers: three nested builds, four consumers, each compiling and running one test that uses a fixtures
  class.** The producer build holds the in-build Kotlin Multiplatform module
  (`jvmTestImplementation(testFixtures(project(":webtools-http")))`); `file-consumer` holds a Kotlin/JVM and a
  Kotlin Multiplatform module that resolve the published artifacts; `composite-consumer` includes the producer.
  The file-repository consumers must declare the repository with `RepositoriesMode.PREFER_SETTINGS`: the
  convention plugins declare `mavenCentral()` on the project, which otherwise hides the settings-level file
  repository and sends the lookup to Central.
- **Results: read the published `.module`, `.pom` and repository files directly; read resolution through a
  small `resolutionReport` task (Provider-based, configuration-cache compatible) that each consumer applies
  from a test resource and that writes a tab-separated file.** `dependencyInsight` is console text and is not
  used for assertions.
- **Cost: about one minute warm (53 to 78 s over five warm runs on JDK 17 and 23; nested builds 39 to 56 s);
  cold, 62 s and 109 files (73 MB) in an empty TestKit home, or 55 s and 38 files (6.2 MB) on top of a home
  populated by the existing nested-build checks.** The producer compiles the whole real module, so the time
  grows with the module.

## Findings

Tiers: **documented** (official documentation), **source** (Gradle, Kotlin Gradle plugin or library source at a
pinned tag), **observed practice** (what mature projects visibly do), **measured** (the probe), **single-source**.

**Probe.** A copy of this repository's `build-logic` (settings, build file, `src/main`, test sources) and of
`webtools-http` (build file, `src/commonMain`, `src/jvmMain`) in a folder outside the repository, with a
minimal recipe-B block appended to the copied build file and one fixtures class
(`com.spartanlabs.webtools.http.testing.VirtualClock`, implementing `WallClock` under
`@OptIn(ExperimentalForInheritanceWebToolsApi::class)`, using `TestCoroutineScheduler` and `MockWebServer`).
Gradle 9.7.1, Kotlin Gradle plugin 2.2.0, vanniktech 0.36.0, Windows 11, 2026-10-25. Every build ran with an
isolated Gradle user home inside the probe folder; the check itself was prototyped as TestKit test classes in
the copy of `build-logic`, run through its `e2eTest` task, with the TestKit home under the copy's `build/`.
JDK 23 unless stated. Published only to a file repository inside the probe.

### F1. Recipe B on the module's real sources publishes exactly the specified variants (measured)

Root module metadata `webtools-http-1.0.0.module` (variants and files, by name):
`metadataApiElements`, `metadataSourcesElements`, `testFixturesApiElements`, `testFixturesRuntimeElements`,
`jvmApiElements-published`, `jvmRuntimeElements-published`, `jvmSourcesElements-published`. Both fixtures
variants:

```json
"attributes": { "org.gradle.category": "library", "org.gradle.dependency.bundling": "external",
                "org.gradle.jvm.version": 11, "org.gradle.libraryelements": "jar",
                "org.gradle.usage": "java-api" },            // "java-runtime" on the runtime variant
"dependencies": [ { "module": "webtools-http",            "version": { "requires": "1.0.0"  } },
                  { "module": "kotlinx-coroutines-test",  "version": { "requires": "1.10.2" } },
                  { "module": "mockwebserver3",           "version": { "requires": "5.5.0"  } },
                  { "module": "kotlin-stdlib",            "version": { "requires": "2.2.0"  } } ],
"files": [ { "name": "webtools-http-1.0.0-test-fixtures.jar", ... } ],
"capabilities": [ { "group": "io.github.spartanlaboratories", "name": "webtools-http-test-fixtures", "version": "1.0.0" } ]
```

`kotlin-stdlib` is added by the Kotlin plugin to the fixtures compilation's `api`; the variants list nothing
else. The `-jvm` module metadata's variants (`jvmApiElements-published` lists `kotlinx-coroutines-core`,
`kotlin-stdlib`; `jvmRuntimeElements-published` adds `okhttp-jvm`, `caffeine`, `slf4j-api`) and its POM contain
no match for `mockwebserver3` or `coroutines-test`. The root POM contains
`kotlinx-coroutines-test` and `mockwebserver3` with `<scope>compile</scope><optional>true</optional>` and no
`webtools-http` dependency. The fixtures jar holds `META-INF/webtools-http_testFixtures.kotlin_module` and
`com/spartanlabs/webtools/http/testing/VirtualClock.class` at class-file major 55. Observed through
`FixturesPublicationV3ProbeTest` (10 tests, 0 failures).

### F2. The producer works with injected plugins and with a copy of `build-logic`; injection is about 30 s cheaper (measured)

| Producer plugins | Producer build, warm | Composite consumer, warm | Notes |
|---|---|---|---|
| injected (`withPluginClasspath()`), no `build-logic` in the workspace | 23 to 34 s | 8 to 10 s | `WebToolsBuild` is visible in the copied module script; the `mavenPublishing {}` accessor is generated; no "loaded multiple times" warning |
| copy of `build-logic` (settings, build file, `src/main`) via `pluginManagement { includeBuild("build-logic") }` | 47 to 53 s | 21 to 22 s | first run also compiles the copied `build-logic` (cold producer 104 s) |

Injection reaches an included build: in `composite-consumer` (`includeBuild("../producer")`) the producer has no
`pluginManagement` and still applies `webtools.kotlin-multiplatform-library` by id, with the configuration
cache on. Gradle documents injection only for the build under test ("only works when the plugin-under-test is
applied in the test build using the `plugins {}` DSL"). A consumer that includes a producer that carries its
own `pluginManagement { includeBuild("build-logic") }` (BotTools' development composite) also resolves and
builds; that was run once from the command line with a copy of `build-logic` and Kotlin 2.2.0 by version in the
consumer, not through TestKit.

### F3. Getting past `signAllPublications()`: the options measured (measured, source)

- Source: `signAllPublications()` applies Gradle's `signing` plugin and calls
  `project.gradleSigning.setRequired(version.map { !it.endsWith("-SNAPSHOT") })`; keys come from the Gradle
  properties `signingInMemoryKey`, `signingInMemoryKeyId`, `signingInMemoryKeyPassword`. Gradle's `Sign` task
  carries `onlyIf("Signing is required, or signatory is set", ...)` and otherwise fails for lack of a signatory.
- No key, release version, nothing disabled: the producer fails. Observed: "Cannot perform signing task
  ':webtools-http:signKotlinMultiplatformPublication' because it has no configured signatory".

| Option | Result | `.asc` files | Cost |
|---|---|---|---|
| `Sign` tasks disabled from a generated root script (`subprojects { tasks.withType<org.gradle.plugins.signing.Sign>().configureEach { enabled = false } }`) | publishes; `signJvmPublication` and `signKotlinMultiplatformPublication` SKIPPED | 0 | none; the repository's `SampleProject.withLocalPublishing()` already does this |
| the same from an init script (`-I disable-sign.init.gradle.kts`, `allprojects { tasks.withType<...Sign>()... }`) | identical (BUILD SUCCESSFUL, 0 `.asc`) | 0 | one more file to write; no advantage over the script |
| version `1.0.0-SNAPSHOT` | publishes; `Sign` tasks SKIPPED | 0 | the capability becomes `...:webtools-http-test-fixtures:1.0.0-SNAPSHOT`, files are timestamped (`webtools-http-1.0.0-20261025.130858-1-test-fixtures.jar`), and the requirement's `1.0.0` capability cannot be asserted |
| throwaway key from `signingInMemoryKey` (F4) | publishes; `signJvmPublication` and `signKotlinMultiplatformPublication` run | one beside every file but `maven-metadata.xml*`: 12 files over the two modules, `webtools-http-1.0.0-test-fixtures.jar.asc` included | F4 |

### F4. The throwaway key: what it costs, and it works (measured, source)

- **Dependency.** `org.bouncycastle:bcpg-jdk18on:1.86` (latest on Central; its `maven-metadata.xml` says
  `<release>1.86</release>`), as `testImplementation`. It resolves `bcprov-jdk18on` and `bcutil-jdk18on` 1.86
  (jars 0.73 + 7.2 + 0.79 MB) into the outer Gradle cache, not the TestKit home. The unmodified `build-logic`
  test runtime classpath contains no `org.bouncycastle` (`dependencies --configuration testRuntimeClasspath`:
  zero matches).
- **Gradle's own copy.** The Gradle distribution ships `lib/plugins/bcpg-jdk18on-1.85.jar`. `gradleApi()` (which
  `kotlin-dsl` puts on `build-logic`'s test compile classpath) resolves to a generated
  `gradle-api-9.7.1.jar` that contains the library only relocated:
  `org/gradle/internal/impldep/org/bouncycastle/openpgp/PGPKeyRingGenerator.class` (and `JcaPGPKeyPair`, ...).
  The original package is absent. Gradle's public-API page says that packages with a `.internal.` segment "are
  considered internal implementation details and must not be relied upon for stable usage". Relying on it is
  not sound: the relocation target and the bundled version belong to the Gradle release that runs the build.
  Not compiled against.
- **Lines.** Imports: 14 (11 BouncyCastle, 3 JDK). Function (RSA-2048 pair, `PGPKeyRingGenerator` with SHA-256
  signer and an AES-256 passphrase encryptor, ASCII-armoured secret key ring): 13 lines (see Recommendation).
  Key generation was not timed separately.
- **Hand-over.** The producer's generated `gradle.properties` gets `signingInMemoryKey=<armoured key with \n
  escapes>` and `signingInMemoryKeyPassword=<throwaway>`; `signingInMemoryKeyId` is omitted (a single master
  key). vanniktech's own integration runner also writes its in-memory key into the generated project's
  `gradle.properties`.
- **vanniktech 0.36.0 accepts the key**: `signKotlinMultiplatformPublication` and `signJvmPublication` ran and
  the build succeeded; the fixtures jar's signature starts with `-----BEGIN PGP SIGNATURE-----`. The signatures
  of a Kotlin Multiplatform module are produced into `build/signatures/<sign task>` (vanniktech's
  `DirectorySignatureType`) and are published beside the artifacts.
- **Signature validity** (verifying the signature against the public key) was not checked; existence of the
  `.asc` for every published file is.

### F5. The build's environment reaches nested builds and overrides the workspace's `gradle.properties` (measured, documented)

`GradleRunner.withEnvironment` documents that `null` "will make the build use the system environment", which
is the default. Gradle documents the precedence "Flags have precedence over properties and environment
variables" and that `ORG_GRADLE_PROJECT_*` environment variables set project properties. Measured on the
producer with the key in its `gradle.properties`:

| Environment of the build running the check | Runner | Result |
|---|---|---|
| `ORG_GRADLE_PROJECT_signingInMemoryKey` set to an invalid value, in the runner's `withEnvironment` | default | producer FAILED, "Could not read PGP secret key": the variable beat `gradle.properties` |
| the same variable exported for the outer Gradle process only | default (no `withEnvironment`) | producer FAILED, same error: TestKit inherited it |
| the same variable exported for the outer Gradle process only | `withEnvironment(System.getenv().filterKeys { !it.startsWith("ORG_GRADLE_PROJECT_") })` | BUILD SUCCESSFUL |
| the variable in the runner's environment, key passed as `-PsigningInMemoryKey=...` | default | BUILD SUCCESSFUL: the command line beat the environment |

`withEnvironment` with a filtered full copy of `System.getenv()` works on Windows. TestKit's documentation
says the user-level `gradle.properties` is not used; the environment is a separate channel. A release workflow
that sets `ORG_GRADLE_PROJECT_signingInMemoryKey` and runs `check` would put the real key into the nested
producer build.

### F6. File-repository consumers: the repository must be declared in the settings with `PREFER_SETTINGS` (measured, source)

`webtools.published-library` declares `repositories { mavenCentral() }` on every module project. Under the
default `RepositoriesMode.PREFER_PROJECT` ("any repository declared on a project will cause the project to use
the repositories declared by the project, ignoring those declared in settings"), a consumer that applies a
convention plugin and declares the file repository in `dependencyResolutionManagement` never consults it.
Measured: "Could not find io.github.spartanlaboratories:webtools-http:1.0.0. Searched in the following
locations: https://repo.maven.apache.org/maven2/io/github/spartanlaboratories/webtools-http/1.0.0/webtools-http-1.0.0.pom"
together with "The project declares repositories, effectively ignoring the repositories you have declared in
the settings." The mirror hazard is worse: once `webtools-http` 1.0.0 is on Central, the same setup would
resolve from Central and pass. `repositoriesMode = RepositoriesMode.PREFER_SETTINGS` ("any repository declared
directly in a project, either directly or via a plugin, will be ignored") plus an `exclusiveContent` block for
the group `io.github.spartanlaboratories` pins the lookup to the file repository; with it all four consumers
passed. A plain `org.jetbrains.kotlin.jvm` consumer declares no project repository and works without it.

### F7. Consumers measured (measured)

| Consumer | Plugin | Source of the module | Result |
|---|---|---|---|
| Kotlin/JVM, file repository | `webtools.kotlin-jvm-library` injected | `exclusiveContent` file repository | `testFixtures("...:webtools-http:1.0.0")` selects `testFixturesApiElements` / `testFixturesRuntimeElements` (source `module`), the fixtures jar, `kotlinx-coroutines-test-jvm` 1.10.2, `mockwebserver3` 5.5.0; test compiled and ran |
| Kotlin Multiplatform (`jvm()`), file repository | `webtools.kotlin-multiplatform-library` injected | same | `"jvmTestImplementation"(testFixtures("...:webtools-http:1.0.0"))` on `jvmTestCompileClasspath` / `jvmTestRuntimeClasspath`: same variants and files; `jvmTest` ran. Also resolved through `webtools-http-jvm` for the main variants |
| Kotlin Multiplatform, in the producer build | `webtools.kotlin-multiplatform-library` injected | `project(":webtools-http")` | `"jvmTestImplementation"(testFixtures(project(":webtools-http")))` with `api(project(":webtools-http"))` in `commonMain`: variants `testFixturesApiElements` / `testFixturesRuntimeElements` of the project (source `project`) with the same files; `jvmTest` ran. `commonTestImplementation(testFixtures(project(":webtools-http")))` also built and ran `jvmTest` (IDE import not tried) |
| Kotlin/JVM, composite | `webtools.kotlin-jvm-library` injected | `includeBuild("../producer")`, substituted | source `project`, same files; test ran |
| Kotlin/JVM, plain `org.jetbrains.kotlin.jvm` from the injected classpath, or by version `2.2.0` from the plugin portal | no convention plugin | file repository, composite | also pass (probe v1) |
| Java (`java-library`), file repository | core plugin | file repository | a Java class in `src/test/java` compiled against and ran `VirtualClock` (`JavaExec`, prints `5`) |

On the consumers' main classpaths (`compileClasspath`/`runtimeClasspath`, `jvmCompileClasspath`/
`jvmRuntimeClasspath`) only `webtools-http` (and `webtools-http-jvm`) is selected; no variant carries the
fixtures capability, and neither `kotlinx-coroutines-test`, `mockwebserver3` nor the fixtures jar is resolved.

### F8. The published variants need no attribute beyond recipe B's (measured, source)

`dependencyInsight --configuration testRuntimeClasspath --dependency webtools-http` of a Kotlin/JVM consumer on
JDK 23 against the published module:

```
Variant testFixturesRuntimeElements:
  | org.gradle.category               | library      | library      |
  | org.gradle.dependency.bundling    | external     | external     |
  | org.gradle.jvm.version            | 11           | 23           |
  | org.gradle.libraryelements        | jar          | jar          |
  | org.gradle.usage                  | java-runtime | java-runtime |
  | org.gradle.jvm.environment        |              | standard-jvm |
  | org.jetbrains.kotlin.platform.type|              | jvm          |
```

The consumer requests `org.gradle.jvm.environment` and `org.jetbrains.kotlin.platform.type`; the fixtures
variants provide neither, and the match succeeds because only the two fixtures variants carry the requested
capability and a missing attribute is compatible. A Kotlin Multiplatform consumer (in-build and from the file
repository) succeeds the same way. The Kotlin Gradle plugin's KDoc for the adhoc component says "Attributes of
added variants should not match default Kotlin Multiplatform publication variants"; adding the two Kotlin
attributes would move the fixtures variants towards the default ones for no gain. `testFixtures(...)` asks for
the capability feature `test-fixtures` (`requireFeature(TEST_FIXTURES_CAPABILITY_FEATURE_NAME)`), i.e.
`<group>:<name>-test-fixtures`, which is what recipe B publishes.

### F9. Reading results: a Provider-based report task works under the configuration cache (measured, documented)

The `resolutionReport` task (Recommendation) reads `configuration.incoming.resolutionResult.rootComponent`
(a `Provider<ResolvedComponentResult>`; the Javadoc: "The returned Provider is live, and tracks the producer
tasks of this resolution result") and `configuration.incoming.artifacts.resolvedArtifacts`, maps them to
strings, registers the providers as task inputs and writes in `doLast`. Observed: "Configuration cache entry
stored" in every nested build; a second command-line run of a consumer printed "Reusing configuration cache."
and the task stayed `UP-TO-DATE`; registering the providers as inputs ordered the report of a test classpath after
`testFixturesJar` in the composite build's task log. A report script applied from a file
(`apply(from = rootProject.file("resolution-report.gradle.kts"))`) behaves the same as one pasted into each
build script. `dependencyInsight` prints the same facts (F8) but as a console table; the repository's harness
asserts on files, not console text. `resolvedConfiguration` and `configuration.resolve()` at configuration
time are not configuration-cache safe and are not used.

### F10. The assertions detect the faults they exist for (measured)

Three mutations of the copied module build file, each run through probe v2 of the check (the same ten
assertions, one file consumer):

| Mutation | Failing assertions |
|---|---|
| remove `mapToOptional()` | only the POM assertion (root POM lists a test library as non-optional) |
| remove the `TargetJvmVersion` attribute from the fixtures configurations | the `.module` jvm-version assertion and the three consumer groups (`org.gradle.jvm.version` provided is no longer 11) |
| add `mockwebserver3` to `jvmMainImplementation` too | the "no main variant lists a test library", the POM and the consumers' main-classpath assertions |

Separately, `-Xexplicit-api=strict` on `compileTestFixturesKotlinJvm` takes effect: removing `public` from the
fixtures class fails with "Visibility must be specified in explicit API mode."

### F11. Time and downloads (measured)

Windows 11, JDK 23 unless stated, local network. "Outer" is the whole `e2eTest`-style task including test JVM
and Gradle start-up; nested builds are TestKit builds with a reused daemon from the second build of a run.

| Run | Outer | Producer | File consumers | Composite |
|---|---|---|---|---|
| warm, injected plugins, key, one file consumer (probe v2) | 53 s | 23 s | 8 s | 8 s |
| warm, injected plugins, key, two file consumers (final shape), three runs | 74 s, 62 s, 54 s | 34 s, 27 s, 24 s | 12.3 s, 14.6 s, 11.6 s | 9.5 s, 10.3 s, 8.4 s |
| warm, JDK 17 | 78 s | 23 s | 12 s | 8.5 s |
| first run on JDK 17 / JDK 21 (new JVM for the daemons) | 106 s / 89 s | 40 s / 34 s | 9 s / 10 s | 7 s / 7.5 s |
| warm, `build-logic` copy, `Sign` disabled | 89 s | 47 s | 10 to 15 s | 21 s |
| warm, `build-logic` copy, key | 93 s | 49 to 53 s | 10 s | 22 s |
| cold, empty TestKit home, injected plugins, key (probe v2) | 62 s | 40 s | 7 s | 8 s |
| cold on top of a home populated by `integrationTest` plus `IncludedBuildEndToEndTest` (probe v2) | 55 s | 34 s | 6 s | 7 s |

Cold downloads, measured as the files under the TestKit home's `caches/modules-2/files-2.1`:

- empty home, check alone: 109 files, 73.0 MB (the Kotlin compiler and plugins dominate);
- on top of the existing nested-build checks (257 files, 207 MB in that home): 38 files, 6.17 MB: `kotlin-stdlib`
  2.2.21 (1.76 MB), `kotlinx-coroutines-core-jvm` 1.10.2 (1.48 MB), `caffeine` 3.3.1 (1.09 MB),
  `okhttp-jvm` 5.5.0 (0.96 MB), `okio-jvm` 3.18.1 (0.39 MB), `kotlinx-coroutines-test-jvm` 1.10.2 (0.14 MB),
  `mockwebserver3` 5.5.0 (0.07 MB), and three annotation jars (about 0.05 MB), with their POMs and module files;
- plus, once, the three BouncyCastle jars (8.7 MB) in the outer Gradle cache.

The final shape's cold run (with the second file consumer) was not repeated; the second consumer resolves the
same artifacts. The producer compiles the real module's sources (24 main files in the probe) with the
in-process Kotlin compiler in a fresh daemon; its time grows with the module. The probe's fixtures were one
class, so the real fixtures add compile time that is not measured.

### F12. JDK 17, 21 and 23 (measured)

The final prototype passed, with all ten assertions, under JDK 17.0.20 (Temurin), 21.0.x (Temurin) and 23.0.2
(Oracle) as the JVM of the outer Gradle (hence of the test JVM and the nested daemons), with the fixtures
variants still at `org.gradle.jvm.version` 11 and the fixtures jar at class-file major 55. JDK 24 to 26 were
not available.

### F13. Recipe-B details confirmed or refined by the probe (measured, source)

- `configurations.consumable(name) { ... }` (Gradle 8.4+, `@Incubating` at 9.7.1) registers the two outgoing
  configurations lazily; the capability string `"${project.group}:${project.name}-test-fixtures:${project.version}"`
  is read when the configuration is realised (at publication), after the root's `allprojects` block has set
  `group` and `version`.
- `extendsFrom(configurations.getByName("jvmTestFixturesApi"), ...)` by name inside that lambda works; the
  configuration exists once the `testFixtures` compilation is created.
- Gradle prints, per fixtures variant, "Declares capability io.github.spartanlaboratories:webtools-http-test-fixtures:1.0.0
  which cannot be mapped to Maven". `publishing.publications.withType<MavenPublication>().configureEach {
  suppressPomMetadataWarningsFor("testFixturesApiElements"); suppressPomMetadataWarningsFor("testFixturesRuntimeElements") }`
  silences it (verified); vanniktech does the same for the Java plugin's fixtures.
- `kotlin.publishing.adhocSoftwareComponent { addVariantsFromConfiguration(x.get()) { mapToMavenScope(..); mapToOptional() } }`
  compiles under `@OptIn(ExperimentalKotlinGradlePluginApi::class)` placed on the statement.
- The `-Xexplicit-api=strict` argument is set on `tasks.named<KotlinJvmCompile>("compileTestFixturesKotlinJvm")`.
- The producer's `kmp-consumer` module needs `mavenPublishing { coordinates(...); pom { name; description } }`
  like any module of the build; without publishing it is never published.

## How comparable projects do it

- **Gradle's own `java-library` feature tests.** `JavaLibraryFeaturePublishingIntegrationTest` (Gradle v9.7.1,
  `platforms/jvm/plugins-java-library/src/integTest`) publishes a project with a secondary feature
  (`integTestFixtures`, the same shape as test fixtures), asserts the published artifact list
  (`module.assertArtifactsPublished(expectedArtifacts)` including `mylib-1.4-test-fixtures.jar`), then asserts per
  variant in the parsed `.module`: `md.variant('integTestFixturesRuntimeElements') { capability('com.acme',
  'mylib-integ-test-fixtures', '1.4'); file("mylib-1.4-integ-test-fixtures.jar"); noMoreDependencies() }`. It also
  compiles a consumer source set against the capability. This check asserts the same things (artifact list,
  capability, files, dependencies) against files and adds real resolution from three build shapes.
- **vanniktech/gradle-maven-publish-plugin 0.36.0.** `KotlinPluginTest.kotlinJvmWithTestFixturesProject` publishes
  a Kotlin/JVM project with `java-test-fixtures` to a file repository and asserts
  `artifact("test-fixtures", "jar").exists()` and `.isSigned()`: signing coverage of the fixtures jar is checked
  by the library's own suite. Its generated projects get the key through the generated `gradle.properties`
  (`signingInMemoryKey`, `signingInMemoryKeyId`, `signingInMemoryKeyPassword` per `TestOptions.Signing.IN_MEMORY_KEY`),
  with a key committed to the plugin's repository; it has no Kotlin Multiplatform fixtures test (its fixtures
  support is tied to `java-test-fixtures`).
- **Kotlin Gradle plugin v2.2.0.** The integration project `kmp-jvm-test-fixtures` (`lib` Kotlin/JVM,
  `runtime` multiplatform with `jvm()` and `linuxX64()`) consumes `testFixturesImplementation(testFixtures(project(":runtime")))`
  in-build; it does not publish. No KGP test publishes fixtures from a multiplatform module.
- **This repository's harness** (`build-logic/src/test/kotlin/.../support/`): `ProjectWorkspace`, `NestedBuild`
  (injection by default), `BuildLogicSource`, `SampleProject.withLocalPublishing()` (disabled `Sign` tasks, a
  `testFolder` repository), `TestReports`, `ModuleMetadata`, `PomFile`, `JarClasses`; one nested build per
  `@BeforeAll`, assertions on files and result XML.

## Pitfalls & regrets

1. **A release secret in the environment reaches the nested build** and beats the workspace's
   `gradle.properties` (F5). Scrub `ORG_GRADLE_PROJECT_*` in the runner; do not rely on the user-level
   `gradle.properties` exclusion alone.
2. **A settings-level file repository is ignored when a convention plugin declares project repositories** (F6),
   and the failure mode after release is a silent pass from Central. Use `PREFER_SETTINGS` and
   `exclusiveContent`.
3. **A key committed for the test** would break the "no key committed" rule; vanniktech does commit one. Generate
   it. Write it only into the workspace (deleted by `ProjectWorkspace.close()`), never into command-line
   arguments or logs.
4. **Relying on Gradle's relocated BouncyCastle** (`org.gradle.internal.impldep...`) ties the test to Gradle's
   internals (F4).
5. **Asserting the `kotlin-stdlib` version** in the fixtures variants' dependencies pins the check to the Kotlin
   plugin version. Assert the three required dependencies and that nothing else but `kotlin-stdlib` is listed.
6. **The producer copy is a copy of one build file.** If `webtools-http/build.gradle.kts` starts to reference
   another project, a root-relative path or a file outside the copied directories, the nested build fails;
   that is a loud failure, but it is a coupling to keep in mind. `api/webtools-http.api` and the test source
   sets are not copied; `apiCheck` and the module's tests are not part of the check.
7. **Time grows with the module.** The producer compiles everything in `commonMain`/`jvmMain` and the fixtures on
   every run with changed inputs. Keep the check's inputs exact so it stays `UP-TO-DATE` otherwise.
8. **`GradleRunner.run()` hides failures** until an assertion looks at the result; use `build()` in the
   `@BeforeAll` so a failing nested build fails with its full output (the probe used `run()` only to keep the
   mutation experiments going).
9. **First run on a new JDK** pays 89 to 106 s (new daemons); the dedicated task's timeout must cover a cold,
   slow-network run.
10. **`commonTestImplementation(testFixtures(...))`** builds, but there is no common fixtures variant: the
    declared shape for the later `webtools-feeds` stays `jvmTestImplementation`.
11. **Cargo-culted forms to avoid:** the `java-test-fixtures` plugin or `withJava()` (rejected for recipe B),
    skip rules for "classes"/"resources" secondary variants (recipe A only), `afterEvaluate` around the
    capability, `isCanBeConsumed`/`isCanBeResolved` flag juggling instead of `consumable`, resolving a
    configuration at configuration time to inspect it, and parsing `dependencyInsight` output.

## Recommendation for this repository

Prototype the check as one test class, `HttpFixturesPublicationEndToEndTest` (Level 4b, package
`com.spartanlabs.testing.e2e.webtools.buildlogic`, one `@BeforeAll` that runs three nested builds, ten
assertions), run by a dedicated `Test` task shaped like `e2eTest` (`startsNestedBuilds = true`: shared TestKit
home, `testKitHomeLock`, the 30-minute timeout, plus the system property for the module directory and the
module files as task inputs). Injected plugins, a generated throwaway key, `ORG_GRADLE_PROJECT_*` scrubbed,
four consumers, resolution read through a report task. Reasons: the injected runner is the harness's default
and was measured to reach every build of the composite; the key is the only option that lets the check assert
the one thing Central will test about the fixtures jar (a signature beside it) at no run-time cost; the
consumers cover the three build shapes the later modules and BotTools use.

### The recipe-B block in `webtools-http/build.gradle.kts`, as probed

The probe appended this to a copy of the module's build file, which the convention plugin
`webtools.kotlin-multiplatform-library` configures (`jvm()` at the floor, `TargetJvmVersion`, signing,
`mavenPublishing`). The probe wrote the Gradle and Kotlin plugin types fully qualified; the imports below are
equivalent. Imports go above `plugins {}`.

```kotlin
import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

kotlin {
    jvm {
        val main = compilations.getByName("main")
        val fixtures = compilations.create("testFixtures") { associateWith(main) }
        compilations.getByName("test").associateWith(fixtures)
    }
}

val fixturesCompilation = kotlin.jvm().compilations.getByName("testFixtures")

tasks.named<KotlinJvmCompile>("compileTestFixturesKotlinJvm") {
    compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
}

val testFixturesJar = tasks.register<Jar>("testFixturesJar") {
    archiveClassifier.set("test-fixtures")
    from(fixturesCompilation.output.allOutputs)
}

fun fixturesElements(name: String, usage: String, vararg extends: String) =
    configurations.consumable(name) {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(usage))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, WebToolsBuild.JAVA_FLOOR)
        }
        extendsFrom(*extends.map { configurations.getByName(it) }.toTypedArray())
        outgoing {
            capability("${project.group}:${project.name}-test-fixtures:${project.version}")
            artifact(testFixturesJar)
        }
    }

val testFixturesApiElements = fixturesElements("testFixturesApiElements", Usage.JAVA_API, "jvmTestFixturesApi")
val testFixturesRuntimeElements = fixturesElements(
    "testFixturesRuntimeElements", Usage.JAVA_RUNTIME,
    "jvmTestFixturesApi", "jvmTestFixturesImplementation", "jvmTestFixturesRuntimeOnly",
)

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin.publishing.adhocSoftwareComponent {
    addVariantsFromConfiguration(testFixturesApiElements.get()) { mapToMavenScope("compile"); mapToOptional() }
    addVariantsFromConfiguration(testFixturesRuntimeElements.get()) { mapToMavenScope("runtime"); mapToOptional() }
}

publishing.publications.withType<MavenPublication>().configureEach {
    suppressPomMetadataWarningsFor("testFixturesApiElements")
    suppressPomMetadataWarningsFor("testFixturesRuntimeElements")
}

dependencies {
    "jvmTestFixturesApi"(project(project.path))
    "jvmTestFixturesApi"("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    "jvmTestFixturesApi"("com.squareup.okhttp3:mockwebserver3:5.5.0")
}
```

The `suppressPomMetadataWarningsFor` block was verified in a second run on the same module copy; the rest was
the block behind every result above. The `extendsFrom` lists are the ones recipe B measured earlier
(`jvmTestFixturesApi` for the API variant; API, implementation and runtime-only for the runtime variant).

### Workspace (a `ProjectWorkspace` directory, created under `webtools.testkit.projects`)

```
<workspace>/
  producer/                         settings.gradle.kts, build.gradle.kts, gradle.properties,
    resolution-report.gradle.kts    (copy of the test resource)
    webtools-http/                  build.gradle.kts, src/commonMain, src/jvmMain, src/jvmTestFixtures  (copied from the module)
    kmp-consumer/                   build.gradle.kts, src/commonMain/kotlin/sample/Marker.kt, src/jvmTest/kotlin/consumer/UsesFixturesTest.kt
  file-consumer/                    settings.gradle.kts, gradle.properties, resolution-report.gradle.kts
    jvm/                            build.gradle.kts, src/test/kotlin/consumer/UsesFixturesTest.kt
    kmp/                            build.gradle.kts, src/commonMain/kotlin/sample/Marker.kt, src/jvmTest/kotlin/consumer/UsesFixturesTest.kt
  composite-consumer/               settings.gradle.kts, build.gradle.kts, gradle.properties, resolution-report.gradle.kts,
                                    src/test/kotlin/consumer/UsesFixturesTest.kt
```

The module is copied from the directory named by a new system property (`webtools.http.dir`, set by the
dedicated task to `../webtools-http`), file by file, with the same discipline as `BuildLogicSource`: the build
file and the three source directories only.

### Generated files

`producer/settings.gradle.kts` (no `pluginManagement`: the plugins are injected):

```kotlin
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "producer"
include(":webtools-http", ":kmp-consumer")
```

`producer/build.gradle.kts` (the repository root's `allprojects` block, plus the check's repository; no `Sign`
change):

```kotlin
allprojects {
    group = "io.github.spartanlaboratories"
    version = "1.0.0"
}

subprojects {
    pluginManager.withPlugin("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories { maven { name = "testFolder"; url = uri(rootProject.layout.buildDirectory.dir("repo")) } }
        }
    }
}
```

`producer/gradle.properties`, `file-consumer/gradle.properties`, `composite-consumer/gradle.properties`: the
harness's existing four lines (`org.gradle.configuration-cache=true`, `org.gradle.vfs.watch=false`,
`kotlin.compiler.execution.strategy=in-process`, `org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g`); the
producer's file adds `signingInMemoryKey=<key with each newline written as \n>` and
`signingInMemoryKeyPassword=<throwaway>`.

`producer/kmp-consumer/build.gradle.kts` (the shape `webtools-feeds` will declare):

```kotlin
plugins { id("webtools.kotlin-multiplatform-library") }

mavenPublishing {
    coordinates(group.toString(), "kmp-consumer", version.toString())
    pom { name.set("kmp-consumer"); description.set("A TestKit consumer.") }
}

kotlin { sourceSets { commonMain { dependencies { api(project(":webtools-http")) } } } }

dependencies { "jvmTestImplementation"(testFixtures(project(":webtools-http"))) }

extra["resolutionReport.configurations"] =
    listOf("jvmCompileClasspath", "jvmRuntimeClasspath", "jvmTestCompileClasspath", "jvmTestRuntimeClasspath")
apply(from = rootProject.file("resolution-report.gradle.kts"))
```

`file-consumer/settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    // The convention plugins declare mavenCentral() on each project; under the default mode that would hide
    // the repositories below and send the lookup of webtools-http to Central.
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories {
        exclusiveContent {
            forRepository { maven { name = "fixturesRepo"; url = uri("<producer>/build/repo") } }  // the workspace's own URI
            filter { includeGroup("io.github.spartanlaboratories") }
        }
        mavenCentral()
    }
}
rootProject.name = "file-consumer"
include(":jvm", ":kmp")
```

`file-consumer/jvm/build.gradle.kts` (the composite consumer's root script is identical):

```kotlin
plugins { id("webtools.kotlin-jvm-library") }

dependencies {
    implementation("io.github.spartanlaboratories:webtools-http:1.0.0")
    testImplementation(testFixtures("io.github.spartanlaboratories:webtools-http:1.0.0"))
}

extra["resolutionReport.configurations"] =
    listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")
apply(from = rootProject.file("resolution-report.gradle.kts"))
```

`file-consumer/kmp/build.gradle.kts`: the `kmp-consumer` script with the string notation
`"io.github.spartanlaboratories:webtools-http:1.0.0"` in place of `project(":webtools-http")`, in both
`api(...)` and `testFixtures(...)`, and `coordinates(group.toString(), "kmp", version.toString())`. The `file-consumer` build sets no group or version;
vanniktech accepted the defaults in the probe. The `mavenPublishing` blocks in the consumers were copied from
the repository's end-to-end sample and are not needed for the tasks run (removal was not tried).

`composite-consumer/settings.gradle.kts`: `dependencyResolutionManagement { repositories { mavenCentral() } }`,
`rootProject.name = "composite-consumer"`, `includeBuild("../producer")`.

Each consumer's test (`UsesFixturesTest`) constructs a class from `com.spartanlabs.webtools.http.testing` and uses
its `kotlinx-coroutines-test` and `mockwebserver3` dependencies, so a missing runtime dependency fails the
test. The real unit uses `VirtualClock` and `FakeHttpTransport` instead of the probe's single class.

### Runner

For every nested build: `GradleRunner.create().withProjectDir(<build dir>).withTestKitDir(<shared home>).withPluginClasspath().withEnvironment(System.getenv().filterKeys { !it.startsWith("ORG_GRADLE_PROJECT_") }).withArguments(*tasks, "--stacktrace").build()`. The configuration cache comes from each `gradle.properties`; no `--warning-mode=fail` (the Kotlin plugin 2.2.0 prints a Gradle 10 deprecation). Arguments:

| Build | Tasks |
|---|---|
| `producer` | `:webtools-http:publishAllPublicationsToTestFolderRepository`, `:kmp-consumer:jvmTest`, `:kmp-consumer:resolutionReport` |
| `file-consumer` | `test`, `jvmTest`, `resolutionReport` |
| `composite-consumer` | `test`, `resolutionReport` |

The three run in that order in `@BeforeAll` (the consumers read the producer's `build/repo` and, for the
composite, its project).

The harness changes this needs: `NestedBuild` takes the project directory (or a sub-path) instead of always
`workspace.dir`, and scrubs the environment for every nested build (also protecting the existing checks);
`ModuleMetadata` gains capabilities, dependencies and files per variant (today it reads attributes only);
`PomFile` gains a dependency list with `optional`; a `ResolutionReport` parser; a small helper that builds the
key (`ThrowawayPgpKey.armoured()`); a `ModuleSource` beside `BuildLogicSource` that copies the module's files.
`build-logic/build.gradle.kts` gains `testImplementation("org.bouncycastle:bcpg-jdk18on:1.86")` (literal
version, as the file's other coordinates).

### The report task (`resolution-report.gradle.kts`, a test resource; Kotlin DSL, configuration-cache compatible)

```kotlin
val reportedConfigurations: List<String> = (extra["resolutionReport.configurations"] as List<*>).map { it.toString() }

tasks.register("resolutionReport") {
    val outputDirectory = layout.buildDirectory.dir("reports/resolution")
    val reports: Map<String, Provider<List<String>>> = reportedConfigurations.associateWith { name ->
        val incoming = configurations.getByName(name).incoming
        val variants = incoming.resolutionResult.rootComponent.map { root ->
            val seen = LinkedHashSet<ResolvedComponentResult>()
            val queue = ArrayDeque(listOf(root))
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                if (seen.add(next)) queue.addAll(next.dependencies.filterIsInstance<ResolvedDependencyResult>().map { it.selected })
            }
            seen.flatMap { component ->
                val source = if (component.id is ProjectComponentIdentifier) "project" else "module"
                val module = component.moduleVersion?.let { "${it.group}:${it.name}:${it.version}" }.orEmpty()
                component.variants.map { variant ->
                    val attributes = variant.attributes.keySet().sortedBy { it.name }
                        .joinToString(";") { key -> "${key.name}=${variant.attributes.getAttribute(key)}" }
                    val capabilities = variant.capabilities.joinToString(";") { "${it.group}:${it.name}:${it.version}" }
                    listOf("variant", source, module, variant.displayName, capabilities, attributes).joinToString("\t")
                }
            }
        }
        val files = incoming.artifacts.resolvedArtifacts.map { artifacts -> artifacts.map { "file\t${it.file.name}" }.sorted() }
        variants.zip(files) { v, f -> v + f }
    }
    reports.forEach { (name, lines) -> inputs.property("lines-$name", lines) }
    outputs.dir(outputDirectory)
    doLast {
        val directory = outputDirectory.get().asFile.apply { mkdirs() }
        reports.forEach { (name, lines) -> directory.resolve("$name.tsv").writeText(lines.get().joinToString("\n") + "\n") }
    }
}
```

Output, one file per configuration: `variant<TAB>project|module<TAB>group:name:version<TAB>variant name<TAB>capabilities<TAB>attributes`
and `file<TAB>file name` lines. Example (published, Kotlin/JVM consumer, `testRuntimeClasspath`):
`variant module io.github.spartanlaboratories:webtools-http:1.0.0 testFixturesRuntimeElements io.github.spartanlaboratories:webtools-http-test-fixtures:1.0.0 org.gradle.category=library;org.gradle.dependency.bundling=external;org.gradle.jvm.version=11;org.gradle.libraryelements=jar;org.gradle.usage=java-runtime`,
and `file webtools-http-1.0.0-test-fixtures.jar`.

### The key (test helper, 13 lines plus imports)

```kotlin
fun armouredThrowawayKey(passphrase: String): String {
    val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
    val keyPair = JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, generator.generateKeyPair(), Date())
    val ring = PGPKeyRingGenerator(
        PGPSignature.POSITIVE_CERTIFICATION, keyPair, "throwaway <throwaway@example.invalid>",
        JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1), null, null,
        JcaPGPContentSignerBuilder(keyPair.publicKey.algorithm, HashAlgorithmTags.SHA256).setProvider(BouncyCastleProvider()),
        JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256).setProvider(BouncyCastleProvider()).build(passphrase.toCharArray()),
    )
    val out = ByteArrayOutputStream()
    ArmoredOutputStream(out).use { ring.generateSecretKeyRing().encode(it) }
    return out.toString(Charsets.US_ASCII)
}
```

Imports (11 `org.bouncycastle`, plus `java.io.ByteArrayOutputStream`, `java.security.KeyPairGenerator`, `java.util.Date`): `bcpg.ArmoredOutputStream`, `bcpg.HashAlgorithmTags`, `bcpg.SymmetricKeyAlgorithmTags`,
`jce.provider.BouncyCastleProvider`, `openpgp.PGPKeyRingGenerator`, `openpgp.PGPPublicKey`, `openpgp.PGPSignature`,
`openpgp.operator.jcajce.JcaPGPContentSignerBuilder`, `...JcaPGPDigestCalculatorProviderBuilder`,
`...JcaPGPKeyPair`, `...JcePBESecretKeyEncryptorBuilder`. The armoured text is written with `\r` removed and
`\n` escaped for the properties file.

### Assertions (ten tests over the three builds' results)

Published files (`producer/build/repo/io/github/spartanlaboratories/...`, parsed from disk):

1. The publish task ended `SUCCESS`, no executed task path contains `MavenCentral`, and
   `webtools-http-1.0.0-test-fixtures.jar` is in the root module's directory.
2. In `webtools-http-1.0.0.module`, `testFixturesApiElements` and `testFixturesRuntimeElements` each have the
   single capability `io.github.spartanlaboratories:webtools-http-test-fixtures:1.0.0`,
   `attributes["org.gradle.jvm.version"] == WebToolsBuild.JAVA_FLOOR`, the one file
   `webtools-http-1.0.0-test-fixtures.jar`, and dependencies `webtools-http` `1.0.0`, `kotlinx-coroutines-test`
   `1.10.2`, `mockwebserver3` `5.5.0`, with no module other than those and `kotlin-stdlib` (version not
   asserted).
3. No variant whose name does not start with `testFixtures`, in the root and in the `-jvm` module metadata,
   lists `kotlinx-coroutines-test` or `mockwebserver3`; the `-jvm` POM lists neither; each of them in the root
   POM is `optional`.
4. Every file under the repository except checksums and `maven-metadata.xml*` has a sibling `.asc`; the fixtures
   jar's begins with `-----BEGIN PGP SIGNATURE-----`.
5. The fixtures jar holds a class under `com/spartanlabs/webtools/http/testing/` and every class in it has
   class-file major `WebToolsBuild.classFileMajor(WebToolsBuild.JAVA_FLOOR)` (`JarClasses.majors`).

Consumers (from `resolutionReport` files and result XML, via `TestReports.resultClasses`):

6. In-build Kotlin Multiplatform module: on `jvmTestCompileClasspath` and `jvmTestRuntimeClasspath` exactly one
   selected variant carries the fixtures capability, its source is `project`, its `org.gradle.jvm.version` is
   `11`, the files include the fixtures jar, `mockwebserver3-5.5.0.jar` and a `kotlinx-coroutines-test*` jar; on
   `jvmCompileClasspath` and `jvmRuntimeClasspath` no variant has a `-test-fixtures` capability, the module itself is
   resolved, and none of those three files is. The module's `jvmTest` ran its class.
7. The same for the file repository's Kotlin/JVM consumer (`testCompileClasspath`, `testRuntimeClasspath`,
   `compileClasspath`, `runtimeClasspath`; source `module`).
8. The same for the file repository's Kotlin Multiplatform consumer (`jvm*` classpaths; source `module`).
9. The same for the composite consumer (source `project`).
10. Every nested build's output contains "Configuration cache entry stored", and none contains "loaded multiple times".

The prototype of these ten (with the key helper, the generated files and the report parser) is about 330 lines
and passed on the toolchain above; the three mutations of F10 fail it as described.

### Per-part verdicts

| Part | Options | Verdict |
|---|---|---|
| Producer | copy of `build-logic` / injected plugins | injected: same result, 25 s cheaper, no second compile of `build-logic`; the `pluginManagement` mechanics are exercised by `IncludedBuildEndToEndTest` and every module |
| Past signing | `Sign` disabled (script or init script) / `-SNAPSHOT` / throwaway key | throwaway key: the only option that asserts the fixtures jar's signature and keeps the 1.0.0 capability; zero run-time cost; one test dependency |
| File-repository consumer | Kotlin/JVM, plain Java, plugin by version, plugin injected; resolve only, or compile and run | Kotlin/JVM through `webtools.kotlin-jvm-library` (the floor, the JUnit set and the JDK matrix are the plugin's), compile and run one test; Java and by-version add nothing |
| Composite consumer | same as above | Kotlin/JVM through the same plugin; included producer gets its plugins by injection |
| In-build Kotlin Multiplatform | `jvmTestImplementation(testFixtures(project(...)))` | works with recipe B's attributes unchanged; add a Kotlin Multiplatform consumer of the published artifacts as well, because it exercises the published metadata through the Kotlin attributes, a path nothing else covers |
| Reading results | report task / `dependencyInsight` / metadata files | `.module`, `.pom`, repository files read directly for the publication; report task for resolution; no console parsing |

### Considerations

1. **Signing: generated key or disabled `Sign` tasks.** Options: (a) generated key (a new test dependency,
   `bcpg-jdk18on:1.86`, about 30 lines; asserts `.asc` beside every file); (b) disabled `Sign` tasks (no
   dependency, the existing `SampleProject.withLocalPublishing()` approach; cannot assert signatures).
   Recommendation: (a), because the fixtures jar is the one artifact whose Central validation is first
   exercised at the first release and the signature is the one part of that validation the check can reproduce
   now. If a new test dependency is unwanted, (b) is the fallback and the other nine assertions are unchanged.
2. **The composite consumer's producer: injected plugins or a `build-logic` copy.** Options: injected (measured,
   cheap) or a copy for the composite consumer only (about 12 s more; exercises a consumer including a build whose
   own `pluginManagement` includes `build-logic`, which is BotTools' development setup). Recommendation:
   injected. The fixtures resolution is identical in both; the nested-`pluginManagement` path was run once by
   hand (F2) and is not about fixtures.
3. **Whether to keep the Kotlin Multiplatform consumer of the published artifacts.** Options: keep (about 3 s
   warm in the shared `file-consumer` build) or drop it. Recommendation: keep.

## Known gaps

- **JDK 24 to 26 were not run**; JDK 17, 21 and 23 were. The requirement names 17 to 26.
- **Central's upload and validator were not run** (and are out of scope for the check). The signing assertion is
  existence of an `.asc` for every file, not verification of the signature against the public key.
- **The probe's fixtures were a single class**; `FakeHttpTransport`, `FixtureLoader` and the recorder will add
  compile time and, if they carry resources, resources under `src/jvmTestFixtures/resources`, which the copy
  includes but the probe did not exercise. Run time for the real module is not measured.
- **The cold run of the final shape** (two file consumers, convention-plugin consumers) was measured in the
  one-file-consumer shape; the added consumer uses artifacts already present.
- **TestKit-level reuse of the configuration cache** by the nested builds was not asserted; reuse was seen from
  the command line for the report task (F9). Whether the check benefits from daemon reuse when it shares a test JVM with
  other checks was not measured (the probe ran it alone).
- **Workspace cleanup on Windows** (`ProjectWorkspace.close()`) after these nested builds was not exercised; the
  probe kept its workspaces.
- **Linux, macOS and CI** were not run. The environment-variable hazard (F5) is most likely to matter in a release
  workflow.
- **IDE import** of the fixtures source set and of the `commonTestImplementation` form was not tried.
- **Gradle 10** compatibility: "Deprecated Gradle features were used" appears in every nested build; not
  attributed.
- **The injected-plugin reach into included builds** is measured on Gradle 9.7.1 only and is undocumented; a
  Gradle upgrade that changes it fails the composite consumer, loudly.
- **The nested-`pluginManagement` composite** (a consumer including a producer that includes `build-logic`) was
  run once from the command line with Kotlin 2.2.0 by version in the consumer, not through TestKit.
- **The repository's own `webtools-http/build.gradle.kts`** does not yet carry the fixtures block; the probe
  appended the block to a copy. Whether the final block, with `suppressPomMetadataWarningsFor`, behaves
  identically in the repository's build is expected, not run (the repository's build was not run).
- **Why `ORG_GRADLE_PROJECT_*` is the only prefix scrubbed**: other environment variables that influence Gradle
  (`GRADLE_OPTS`, `JAVA_TOOL_OPTIONS`) were not examined.

## Load-bearing claims

Measurements are labelled **measurement**: the command is the probe's `gradle e2eTest --rerun --tests '<class>'`
in a copy of `build-logic` (Gradle 9.7.1 distribution, isolated Gradle user home, JDK 23 unless stated,
2026-10-25), and the line is what the probe observed.

1. **vanniktech 0.36.0: signing is required unless the version is a SNAPSHOT; key properties.** "Automatically
   apply Gradle's `signing` plugin and configure all publications to be signed. If signing credentials are not
   configured this will fail the build unless the current version is a `SNAPSHOT`." and
   `project.gradleSigning.setRequired(version.map { !it.endsWith("-SNAPSHOT") })`; "The in memory properties can
   also be provided as environment variables by prefixing them with `ORG_GRADLE_PROJECT_`".
   <https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt>, tag 0.36.0.
2. **Gradle `Sign` is skipped unless required or a signatory is set.**
   `onlyIf("Signing is required, or signatory is set", spec(task -> isRequired() || getSignatory() != null));`
   <https://github.com/gradle/gradle/blob/v9.7.1/platforms/software/signing/src/main/java/org/gradle/plugins/signing/Sign.java>, tag v9.7.1.
   Measurement: no key, release version: "Cannot perform signing task ':webtools-http:signKotlinMultiplatformPublication' because it has no configured signatory".
3. **`useInMemoryPgpKeys` takes an armoured key.** `public void useInMemoryPgpKeys(@Nullable String defaultKeyId, @Nullable String defaultSecretKey, @Nullable String defaultPassword)`.
   <https://github.com/gradle/gradle/blob/v9.7.1/platforms/software/signing/src/main/java/org/gradle/plugins/signing/SigningExtension.java>, tag v9.7.1.
   Measurement (key generated with BouncyCastle 1.86, written to the producer's `gradle.properties`): `BUILD SUCCESSFUL`, 12 `.asc` files over `webtools-http` and `webtools-http-jvm`, including `webtools-http-1.0.0-test-fixtures.jar.asc`.
4. **Gradle's public-API rule.** "Only elements explicitly documented as part of the stable public API are
   supported for use. All other classes, methods, or packages, including, but not limited to, those containing
   the name `Internal` or located in a package segment `.internal.`, are considered internal implementation
   details and must not be relied upon for stable usage."
   <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/dsl-apis/public_apis.adoc>, tag v9.7.1.
   Measurement: `unzip -l gradle-api-9.7.1.jar | grep bouncycastle/openpgp/PGPKeyRingGenerator` prints `org/gradle/internal/impldep/org/bouncycastle/openpgp/PGPKeyRingGenerator.class`; in a copy of the unmodified `build-logic`, `gradle dependencies --configuration testRuntimeClasspath` shows zero lines matching `bouncy|bcpg|bcprov`.
5. **BouncyCastle 1.86 is the current release on Central.** `<release>1.86</release>` and `<lastUpdated>20260911045748</lastUpdated>`.
   <https://repo1.maven.org/maven2/org/bouncycastle/bcpg-jdk18on/maven-metadata.xml>, read 2026-10-25.
6. **TestKit's default environment is the system environment.** "{@code null} is permitted and will make the build use the system environment."
   <https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/test-kit/src/main/java/org/gradle/testkit/runner/GradleRunner.java>, tag v9.7.1 (`withEnvironment`).
   Measurement (`ORG_GRADLE_PROJECT_signingInMemoryKey=<invalid>` exported for the outer Gradle, key in the workspace's `gradle.properties`, default runner): producer log "Could not read PGP secret key", `BUILD FAILED`; with `withEnvironment(System.getenv().filterKeys { !it.startsWith("ORG_GRADLE_PROJECT_") })`: `BUILD SUCCESSFUL in 48s`; with the invalid variable in the environment and the real key as `-PsigningInMemoryKey=...`: `BUILD SUCCESSFUL in 48s`.
7. **Gradle property precedence and environment variables.** "Flags have precedence over properties and environment variables" and "The provider resolves the property from the build-level sources listed above: command-line `-P` arguments, `org.gradle.project.pass:[*]` system properties, `ORG_GRADLE_PROJECT_pass:[*]` environment variables, and `gradle.properties` files in the Gradle User Home, build root, and Gradle installation directories."
   <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/runtime-configuration/build_environment.adoc>, tag v9.7.1.
8. **TestKit does not use the user-level `gradle.properties`.** "Configuration from the default Gradle User Home (for example, `~/.gradle/gradle.properties`) is **not** used." and "Note that TestKit re-uses a Gradle User Home between tests".
   <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/plugin-development/test_kit.adoc>, tag v9.7.1.
9. **Injection is documented for the build under test.** "NOTE: Automatic classpath injection only works when the plugin-under-test is applied in the test build using the <<plugins_intermediate.adoc#sec:plugins_block,`plugins {}` DSL>>."
   Same file. Measurement (`FixturesPublicationV3ProbeTest`): the producer, which has no `pluginManagement`, is included by `composite-consumer` and applies `webtools.kotlin-multiplatform-library` by id; `composite-consumer.log`: `BUILD SUCCESSFUL`, "Configuration cache entry stored."; `tests="10" skipped="0" failures="0" errors="0"`.
10. **Repository modes.** "If this mode is set, any repository declared on a project will cause the project to use the repositories declared by the project, ignoring those declared in settings. This is the default behavior." (`PREFER_PROJECT`) and "If this mode is set, any repository declared directly in a project, either directly or via a plugin, will be ignored." (`PREFER_SETTINGS`).
    <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core-api/src/main/java/org/gradle/api/initialization/resolve/RepositoriesMode.java>, tag v9.7.1.
    Measurement (file consumer with the file repository in `dependencyResolutionManagement` and the convention plugin): "Could not find io.github.spartanlaboratories:webtools-http:1.0.0." and "Searched in the following locations: - https://repo.maven.apache.org/maven2/io/github/spartanlaboratories/webtools-http/1.0.0/webtools-http-1.0.0.pom"; with `repositoriesMode = RepositoriesMode.PREFER_SETTINGS`: `BUILD SUCCESSFUL`.
11. **Adhoc components add variants and must not duplicate default attributes.** "It is not possible to modify or replace default Kotlin Multiplatform publication variants. Attributes of added variants should not match default Kotlin Multiplatform publication variants."
    <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinPublishing.kt>, tag v2.2.0 (`@since 2.1.20`, `@ExperimentalKotlinGradlePluginApi`).
12. **`testFixtures(...)` requests the capability feature `test-fixtures`.** `dependency.capabilities(c -> c.requireFeature(TestFixturesSupport.TEST_FIXTURES_CAPABILITY_FEATURE_NAME));` and `TEST_FIXTURES_CAPABILITY_FEATURE_NAME = "test-fixtures"`.
    <https://github.com/gradle/gradle/blob/v9.7.1/platforms/jvm/language-jvm/src/main/java/org/gradle/api/plugins/jvm/TestFixturesDependencyModifiers.java> and <https://github.com/gradle/gradle/blob/v9.7.1/platforms/software/dependency-management/src/main/java/org/gradle/internal/component/external/model/TestFixturesSupport.java>, tag v9.7.1.
    Measurement (published module, Kotlin/JVM consumer on JDK 23): `dependencyInsight` shows `Variant testFixturesRuntimeElements` with `org.gradle.jvm.environment` and `org.jetbrains.kotlin.platform.type` provided empty and requested `standard-jvm` / `jvm`, selected; the consumer's test ran.
13. **The resolution result is available as a live Provider.** "Returns the root of resolved dependency graph as a {@link Provider} of {@link ResolvedComponentResult}. The returned {@link Provider} is live, and tracks the producer tasks of this resolution result."
    <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core-api/src/main/java/org/gradle/api/artifacts/result/ResolutionResult.java>, tag v9.7.1 (`@since 7.4`).
    Measurement: every nested build prints "Configuration cache entry stored."; a second CLI run of a consumer with `test report-testRuntimeClasspath` prints "Reusing configuration cache." and `> Task :report-testRuntimeClasspath UP-TO-DATE`.
14. **`consumable` registers a consumable configuration lazily.** `NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name, Action<? super ConsumableConfiguration> action);` `@since 8.4`.
    <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core-api/src/main/java/org/gradle/api/artifacts/ConfigurationContainer.java>, tag v9.7.1.
15. **vanniktech silences the capability warning for the Java plugin's fixtures.** `it.suppressPomMetadataWarningsFor("testFixturesApiElements")` / `it.suppressPomMetadataWarningsFor("testFixturesRuntimeElements")` with the comment "test fixtures can't be mapped to the POM because there is no equivalent concept in Maven".
    <https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/Platform.kt>, tag 0.36.0 (`setupTestFixtures`).
    Measurement: before the call, `generatePomFileForKotlinMultiplatformPublication` prints "Declares capability io.github.spartanlaboratories:webtools-http-test-fixtures:1.0.0 which cannot be mapped to Maven" per variant; after it, no such line.
16. **Central requires a signature beside every deployed file.** "All files deployed need to be signed with GPG/PGP and a .asc file containing the signature must be included for each file."
    <https://central.sonatype.org/publish/requirements/>, read 2026-10-25.
17. **Gradle's own fixture-feature publishing test asserts artifacts, capability, files and dependencies on the published module.** `module.assertArtifactsPublished(expectedArtifacts)` and `md.variant('integTestFixturesRuntimeElements') {` / `capability('com.acme', 'mylib-integ-test-fixtures', '1.4')` / `file("mylib-1.4-integ-test-fixtures.jar")` / `noMoreDependencies()`.
    <https://github.com/gradle/gradle/blob/v9.7.1/platforms/jvm/plugins-java-library/src/integTest/groovy/org/gradle/java/JavaLibraryFeaturePublishingIntegrationTest.groovy>, tag v9.7.1.
18. **vanniktech's own suite asserts the fixtures jar is signed.** `assertThat(result).artifact("test-fixtures", "jar").exists()` and `assertThat(result).artifact("test-fixtures", "jar").isSigned()`.
    <https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/plugin/src/integrationTest/kotlin/com/vanniktech/maven/publish/KotlinPluginTest.kt>, tag 0.36.0 (`kotlinJvmWithTestFixturesProject`).
19. **KGP's multiplatform fixtures integration project consumes in-build and does not publish.** `testFixturesImplementation(testFixtures(project(":runtime")))` in `lib/build.gradle.kts`; `jvm()` and `linuxX64()` in `runtime/build.gradle.kts`.
    <https://github.com/JetBrains/kotlin/tree/v2.2.0/libraries/tools/kotlin-gradle-plugin-integration-tests/src/test/resources/testProject/kmp-jvm-test-fixtures>, tag v2.2.0.
20. **Published and in-build resolution of the recipe-B variants (measurement).** `FixturesPublicationV3ProbeTest`, `tests="10" skipped="0" failures="0" errors="0"` under JDK 23.0.2, 21 and 17.0.20; the report of `jvmTestRuntimeClasspath` of the in-build module contains `variant project io.github.spartanlaboratories:webtools-http:1.0.0 testFixturesRuntimeElements io.github.spartanlaboratories:webtools-http-test-fixtures:1.0.0 ...` and `file mockwebserver3-5.5.0.jar`; the file-repository Kotlin Multiplatform consumer's report has `variant module ... testFixturesRuntimeElements ...`.
21. **Timings and downloads (measurement).** Warm: outer wall time 53 s to 74 s for the prototype task on JDK 23 (producer 23 to 34 s) and 78 s on JDK 17. Cold on an empty TestKit home: `alone exit=0 seconds=62`, 109 files and 72,961,564 bytes under `caches/modules-2/files-2.1`; on top of the existing nested-build checks: `on-top exit=0 seconds=55`, 38 new files, 6,173,262 bytes.
22. **Mutations are caught (measurement, probe v2, one file consumer).** `mapToOptional()` removed: one failing test ("the jvm pom lists no test library and the root pom lists them as optional only"); the `TargetJvmVersion` attribute removed: four failing tests; `mockwebserver3` added to `jvmMainImplementation`: five failing tests.
23. **Explicit API on the fixtures compile task takes effect (measurement).** `compileTestFixturesKotlinJvm` with `public` removed from the fixtures class: "e: VirtualClock.kt:10:1 Visibility must be specified in explicit API mode."
