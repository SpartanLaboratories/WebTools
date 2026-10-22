# Java 11 floor for every WebTools module

## Question

How should `webtools-udp`, `webtools-scraping`, `webtools-browser` (Kotlin/JVM) and the new Kotlin Multiplatform modules (`webtools-http`, the feeds module) be compiled and published so that every artifact is Java 11 bytecode (class-file major 55 or lower), declares `org.gradle.jvm.version: 11` on each variant that carries classes, compiles in no JDK API newer than 11, and reads one setting? An answer must hold on Kotlin 2.2.0 and Gradle 9.7.1, leave `webtools-udp`'s public API unchanged, and be configuration-cache compatible.

Evidence tiers: **documented** (official docs, source at a tag, tracker), **observed practice** (what mature projects visibly do), **measured** (I built or ran it in a throwaway probe outside the repository: Gradle 9.7.1, Kotlin 2.2.0, a JDK 23 build host unless stated, Windows, 2026-10-20), **single source**.

## Bottom line

- **No binding requirement fails. Use `jvmTarget = 11` plus `-Xjdk-release=11` on the build JDK, one constant, no toolchain.** JVM modules also set `java { sourceCompatibility; targetCompatibility }` to the same value; KMP modules set an explicit `TargetJvmVersion` attribute on the `jvm` target. Measured on Kotlin 2.2.0 / Gradle 9.7.1: it compiles main, test and test-fixtures compilations and common code in a KMP module, rejects every JDK 12+ API I tried (`HexFormat`, `String.indent`, `Thread.ofVirtual`), publishes class files at major 55 and `org.gradle.jvm.version: 11`, builds on a JDK 23 host and on a JDK 21 host, and is configuration-cache clean. Setting only `jvmTarget = 11` is **not** enough: it compiles JDK 21+ calls silently (measured: `MutableList.removeFirst()` compiled to `java/util/List.removeFirst`, a `NoSuchMethodError` on Java 11 to 20).
- **The three existing modules survive the floor unchanged.** Under `-Xjdk-release=11` all main and test sources of `webtools-udp`, `webtools-scraping` and `webtools-browser` compile with zero errors. Their public and protected signatures (`javap -protected`, every class) are identical to the current JDK 23 build. All 7 test levels of `webtools-udp` (about 630 tests) and the gating, component and deterministic levels of the other two pass on a real JDK 11 launcher. No legacy module has to break its API for the floor.
- **No dependency is above Java 11.** Of every runtime and test jar on the candidate classpaths, the highest non-multi-release class-file major is 55 (Caffeine 3.3.1, logback 1.5.6, selenium 4.48.0 and its sub-modules, rssreader 3.12.0). Every dependency that publishes Gradle Module Metadata declares 11 or lower (Caffeine and rssreader 11, the rest 8 or 6). `webtools-scraping` and `webtools-browser` lose nothing.
- **A toolchain (`jvmToolchain(11)`) or a JDK 11 test launcher forces the foojay bump; `-Xjdk-release` alone does not.** foojay-resolver-convention 0.8.0 on Gradle 9.7.1 fails with `Class org.gradle.jvm.toolchain.JvmVendorSpec does not have member field '...IBM_SEMERU'` the moment a JDK that is not installed must be provisioned (measured for `jvmToolchain(11)` and for a `Test` launcher); 1.0.0 provisions Temurin 11.0.32.1 on the same Gradle. A toolchain also makes a consumer's composite build need a JDK 11 (measured with provisioning disabled); `-Xjdk-release` does not.
- **Guard the floor with a `verifyJavaFloor` task wired into `check`** (prototype built and measured): it reads the produced jars and generated `.module` files, fails on any class above major 55 or any classpath variant not declaring 11, and also scans the resolved runtime classpath. Optionally run the level test tasks on a JDK 11 launcher; that is the only part that needs provisioning (Consideration 1).
- **One nuance on "each variant":** `sourcesElements` in a Kotlin/JVM module's metadata carries no `org.gradle.jvm.version` (today's Central metadata has it on `apiElements` and `runtimeElements` only); KMP `jvmSourcesElements-published` carries 11. The attribute is meaningful on the API and runtime variants, and the guard asserts exactly those.

## Findings

### F1. Mechanism: what each option does on Kotlin 2.2.0 / Gradle 9.7.1

All rows measured: JVM columns on the repository's three modules (copied, floor applied to the copy), KMP columns on a multiplatform `webtools-http` with `jvm()` only, fixtures compilation included.

| Setting | JVM module (`kotlin("jvm")`) | KMP `jvm()` target |
|---|---|---|
| nothing (today) | `org.gradle.jvm.version` 23, class major 67 | no attribute written; class major 67 |
| `jvmTarget = 11` only | **build fails**: KGP "Inconsistent JVM-target compatibility detected for tasks 'compileJava' (23) and 'compileKotlin' (11)" | compiles; class major 55; **no attribute written**; JDK 12+ API still compiles (negative control compiled) |
| `jvmTarget = 11` + `java.sourceCompatibility/targetCompatibility = 11` | compiles; attribute 11; major 55; JDK 12+ API still compiles | n/a (no `JavaCompile`; no validation) |
| `jvmTarget = 11` + `tasks.withType<JavaCompile> { options.release = 11 }`, no `java {}` compat | compiles; passes validation; attribute 11; major 55 | n/a |
| `jvmTarget = 11` + `-Xjdk-release=11` + `java {}` compat 11 | compiles; attribute 11; major 55; JDK 12+ API rejected | n/a |
| same, KMP: `jvm { compilerOptions { jvmTarget; -Xjdk-release=11 }; attributes { TargetJvmVersion = 11 } }` | n/a | compiles; **attribute 11 on every `jvm*Elements-published` variant**, root `.module` redirects included; major 55; JDK 12+ API rejected in `jvmMain`, `commonMain`, `jvmTest` and the fixtures compilation |
| `-Xjdk-release=11` without `jvmTarget` | **fails**: `'-Xjdk-release=11' option conflicts with '-jvm-target 23'` | same flag conflict |
| `-Xjdk-release=11` + `jvmTarget` without `java {}` compat | **fails** KGP validation (compileJava 23) | n/a |
| `jvmToolchain(11)` / `java.toolchain.languageVersion = 11` | attribute 11; major 55; API limited to JDK 11 (`-jdk-home`); needs JDK 11 provisioned | attribute **not written** (empty); major 55; API limited (JDK 12+ rejected); tests run on 11 |
| `jvmToolchain(11)` + explicit `TargetJvmVersion` | n/a | attribute 11 |

- **Measured.** `-Xjdk-release` works for a KMP `jvm()` compilation, including the custom test-fixtures compilation and `commonMain`. The flag must be set at `jvm { compilerOptions { ... } }` so that all compilations inherit it.
- **Documented.** `-Xjdk-release` is still an `-X` flag in Kotlin 2.4.21: `Xjdk-release` in `JvmCompilerArguments.kt` at tags `v2.2.0` and `v2.4.21` ("Compile against the specified JDK API version, similarly to javac's '-release'. This requires JDK 9 or newer."). The documentation says it "Automatically sets `-jvm-target version`" and "is not guaranteed to be effective for each JDK distribution" (KT-29974, Fixed). **Measured:** with KGP 2.2.0 the automatic `-jvm-target` is not applied: KGP always passes its own `-jvm-target`, so the build fails unless `jvmTarget` is also set to the same value. The tapmoc plugin's source records the same: "jvmTarget needs to be set as well, or we get an error such as e: '-Xjdk-release=11' option conflicts with '-jvm-target 17'".
- **Documented.** A typed DSL for the flag does not exist: KT-49746 "Support -Xjdk-release in gradle toolchain" is in state Backlog. The flag is `freeCompilerArgs.add("-Xjdk-release=...")`.
- **Documented, measured.** KGP validates `jvmTarget` against `targetCompatibility` of the related `compileJava`. The default is `error` on Gradle 8+; measured: error with Gradle 9.7.1 / KGP 2.2.0 even though `compileJava` has no sources. A KMP `jvm()` target has no `compileJava`, so no validation applies. `kotlin.jvm.target.validation.mode` does not need to be touched.
- **Measured.** What `-Xjdk-release` buys over `jvmTarget` alone: with `jvmTarget = 11` on JDK 23, `mutableList.removeFirst()` and `removeLast()` compile to `invokeinterface java/util/List.removeFirst` / `removeLast` (JDK 21 `SequencedCollection` members win over the Kotlin extensions) with no error or warning. With `-Xjdk-release=11` the same source compiles to `kotlin/collections/CollectionsKt.removeFirst` / `removeLast`. This is the failure the floor exists to stop, and the bytecode version alone cannot catch it.
- **Documented.** Gradle's own guidance matches: "`--release` ... prevents accidental use of newer APIs ... it does not control which JDK is used ... can be combined with toolchains" (Gradle 9.7.1 toolchains page). For a variant attribute, `org.gradle.jvm.version` "Defaults to the JVM version used by Gradle, lower is compatible with higher". Kotlin's guide states the failure mode for a library built on a newer JDK without a target: the published artifact declares the build JDK ("which is wrong").
- **Documented.** Gradle 9.7.1: "A JVM version between 17 and 26 is required to execute Gradle." A JDK 11 can never be the build JDK, so option (a) is "build on a modern JDK, aim at 11" by necessity, and JDK 11 can only appear as a toolchain or a test launcher.
- **Measured: build host.** The same `-Xjdk-release=11` build ran with Gradle launched on JDK 21 (class major 55) and on JDK 23. A consumer's composite build that includes the module compiles it on whichever JDK launches that Gradle.
- **Measured: configuration cache.** Each variant (`-Xjdk-release`, toolchain, launcher) stored and reused the configuration cache entry on a second run. The only diagnostic is the existing KT-78620 `archives` deprecation on KGP 2.2.0 for `jvm()` targets (outside this question).

### F2. The foojay resolver on Gradle 9.7.1

- **Measured.** `settings.gradle.kts:2` applies `org.gradle.toolchains.foojay-resolver-convention` 0.8.0. With a JDK 17 toolchain (installed): build passes, because the resolver is never consulted. With `jvmToolchain(11)` and JDK 11 not installed: `BUILD FAILED`, `Class org.gradle.jvm.toolchain.JvmVendorSpec does not have member field 'org.gradle.jvm.toolchain.JvmVendorSpec IBM_SEMERU'`. The same message appears when only a `Test` task's `javaLauncher` asks for a JDK that is not installed (probe used 14), so the "toolchain only for tests" shape hits it too. With the plugin bumped to 1.0.0, the same build provisioned Temurin 11.0.32.1 (about 199 MB download) and passed. Once JDK 11 is installed, 0.8.0 passes again (it is not consulted).
- **Documented.** Cause: `distributions.kt` at tag `foojay-toolchains-plugin-0.8.0` has `JvmVendorSpec.IBM_SEMERU to "Semeru"`; the field is removed in Gradle 9. The foojay README: "`IBM_SEMERU` is deprecated in Gradle for a while and removed in Gradle 9+." and "Versions 1.0.0 and after require Java 17 or later and Gradle 7.6 or later. Versions prior to 1.0.0 require Java 8 or later and Gradle from 7.6 to 8.14.x." Issue gradle/foojay-toolchains#151 (closed 2025-11-13) reports exactly this failure against 0.9.0. Plugin-portal metadata: latest and release are 1.0.0.
- **Consequence.** Today the repository works on Gradle 9.7.1 only because it never requests a toolchain. Any mechanism that makes Gradle provision a JDK (option (b), or the JDK 11 test launcher of option (c)) fails on a fresh machine or fresh CI cache until foojay is 1.0.0. Option (a) with the guard task does not touch the resolver, so the bump is independent of this work.
- **Measured: consumer composite.** A consumer on JDK 21 that includes the WebTools copy via `includeBuild`:
  - with the copy on `jvmToolchain(11)` and provisioning disabled (`org.gradle.java.installations.auto-download=false`, auto-detect off, only JDK 21 visible): `compileKotlin` of the consumer fails with `Cannot find a Java installation ... matching: {languageVersion=11 ...}`;
  - with the copy on `-Xjdk-release=11` and the same restrictions: the build succeeds;
  - with the copy on `-Xjdk-release=11` plus a test launcher for a missing JDK: the consumer's compile still succeeds (the launcher is only resolved for the module's own `Test` tasks), and the module's own test run fails with the same "Cannot find a Java installation" message.
  Not measured: a consumer with provisioning enabled and JDK 11 absent (the probe machine had already provisioned JDK 11), so which build's resolver would then be consulted is unknown (Gap 3).

### F3. Where the tests run

All measured. Tests on the build JDK 23 do not show that the artifacts load on 11; the options:

| Option | What it proves | Cost | Measured |
|---|---|---|---|
| Level test tasks on a JDK 11 launcher (`tasks.withType<Test> { javaLauncher = javaToolchains.launcherFor { languageVersion = 11 } }`), compile still via `-Xjdk-release=11` | Classes, kotlin-stdlib, okhttp, okio, Caffeine, logback, junit, mockwebserver load and run on 11 | ~199 MB JDK download once per machine or CI cache; needs foojay 1.0.0 or a preinstalled JDK 11 (`org.gradle.java.installations.fromEnv`/paths); a build without network and without JDK 11 cannot run tests | `webtools-udp` gating/component/deterministic 414 tests, integration 115, e2e 34, nonfunctional 65, uat 13 (all skipped by design): green on 11.0.32.1. The four socket levels took 5m12s on 11 and 5m12s on 23. `webtools-scraping` 12 and `webtools-browser` 3 tests green. KMP `jvmTest` and the plain `Test` level task both ran on 11.0.32.1. A consumer's compile does not need JDK 11. |
| `jvmToolchain(11)` for everything | Same, plus compile-time API limit via `-jdk-home` | The above, plus every consumer composite needs a JDK 11 (F2); KMP still needs the explicit attribute | Works with foojay 1.0.0 or an installed JDK 11 |
| A dedicated Gradle check (`verifyJavaFloor`) | Class majors of the built jars and the resolved runtime classpath; `org.gradle.jvm.version` on the generated `.module` files | None beyond a ~60-line task in `build-logic`; no JDK 11 needed | Passes with the floor; with the floor removed it fails listing each class at major 67 and each variant declaring 23 or no attribute; with an extra dependency `jetty-server:12.0.16` (Java 17) it fails listing the major 61 classes; second run reused the configuration cache |
| A Level-1/4c test asserting class major and attribute | Same facts | The test must be handed the jar and `.module` paths by the build (system property) and reads `classes/` rather than the publishable files unless it is wired to `jar` and `generateMetadataFileFor...`; the check task already depends on those | Not built |
| Nothing | Nothing beyond "it compiled" | None | The probe with only `jvmTarget` compiled and passed all tests while `List.removeFirst` was in the bytecode |

### F4. Every dependency's floor

Method: a throwaway Gradle project resolved each module's candidate runtime classpath with the consumer attribute `org.gradle.jvm.version = 11` (Gradle fails resolution if any dependency's Gradle Module Metadata needs more), then every resolved jar was read and the major version of each `.class` outside `META-INF/versions/` was recorded. "GMM" is the `org.gradle.jvm.version` in the published `.module` (none where no `.module` exists).

Versions below are what resolved with the module's declared coordinates.

| Module | Dependency (resolved) | GMM | Class-file major | Notes |
|---|---|---|---|---|
| all | `slf4j-api` 2.0.13 | none (POM only) | 52 | multi-release jar, `versions/9` holds `module-info` at 53 |
| scraping | `skrapeit` 1.1.5 and 7 sub-modules (`-dsl`, `-http-fetcher`, `-async-fetcher`, `-browser-fetcher`, `-base-fetcher`, `-html-parser`, `-assertions`) | 8 | 52 (the umbrella jar has no classes) | |
| scraping | `jsoup` 1.15.4 | none | 52 | |
| scraping | `unirest-java` 1.4.9 | none | 49 | |
| scraping | transitives of skrapeit/unirest: `htmlunit` 2.50.0 (+ `htmlunit-core-js`, `neko-htmlunit`, `htmlunit-cssparser`), `ktor-client-*` 1.5.4, `httpclient` 4.5.13, `httpasyncclient` 4.1.4, `httpcore(-nio)` 4.4.13, `httpmime`, `commons-*`, `xalan`/`xerces`/`xml-apis`, `logback-classic` 1.2.3 and `logback-core`, `log4j-over-slf4j` 1.7.30, `kotlin-stdlib` 1.5.0 family, `kotlinx-coroutines-core-jvm` 1.5.0, `kotlinx-coroutines-jdk8` 1.5.0 (GMM 6), `kotlin-reflect` 1.4.32, `org.json` 20160212, `salvation2`, `brotli dec` | 6 or 8 where present | 45 to 52 | highest is 52 (htmlunit family, commons-lang3 3.12.0, commons-text, salvation2) |
| browser | `selenium-java` 4.48.0 and 17 sub-modules (`-api`, `-remote-driver`, `-support`, `-manager`, `-devtools-v150..v152`, `-devtools-latest`, per-browser drivers, ...) | none (POM only) | **55** | Selenium's README: "Requires Java 11+." |
| browser | `guava` 33.6.0-jre, `failureaccess`, `jspecify` 1.0.1, `byte-buddy` 1.18.11, `opentelemetry-*` 1.65.0 (11 jars), `auto-service-annotations`, `error_prone_annotations` 2.47.0, `j2objc-annotations` 3.1 | guava 8, opentelemetry 8, jspecify 8 | 50 to 53 | `byte-buddy` carries `META-INF/versions/24` classes at major 68; a JDK 11 ignores them |
| new | `okhttp-jvm` 5.5.0 | none | 52 | README: "OkHttp works on Android 5.0+ (API level 21+) and Java 8+." Its `.module` requires `okio` 3.18.1 and `kotlin-stdlib` 2.2.21 |
| new | `okio-jvm` 3.18.1 | none | 52 | resolves via `okio` 3.18.1 |
| new | `kotlin-stdlib` 2.2.0 requested, 2.2.21 selected | none | 52 | selected by `okhttp-jvm` 5.5.0's requirement, not by WebTools |
| new | `caffeine` 3.3.1 | **11** | **55** | its `.module` requires `jspecify` 1.0.1 (GMM 8, major 52, `module-info` 53) and `error_prone_annotations` 2.50.0 (none, 52). README: "For Java 11 or above, use `3.x`" |
| new | `kotlinx-coroutines-core` 1.10.2 / `-jvm` | none | 52 | |
| test | `kotlinx-coroutines-test-jvm` 1.10.2 | none | 52 | |
| test | `mockwebserver3` 5.5.0 (and `-junit5`) | 8 | 52 | |
| test | `logback-classic` 1.5.6, `logback-core` 1.5.6 | none | **55** | `logback-core` has `META-INF/versions/21` classes at major 65 (ignored on 11) |
| test | `kotlin-test-junit5` 2.2.0, `kotlin-test` 2.2.0 | none | 52 | brings JUnit Jupiter 5.10.1, Platform 1.10.1 (GMM 8, major 52) and `opentest4j` 1.3.0 (GMM 6, major 50) |
| feeds candidate | `rome` 2.1.0, `rome-utils` 2.1.0 | none | 52 | pulls `slf4j-api` 2.0.6 (conflict-resolves to 2.0.13) |
| feeds candidate | `jdom2` 2.0.6.1 | none | 49 | |
| feeds candidate | `rssreader` 3.12.0 | **11**, no dependencies | **55** | |

- **Result.** Nothing is above 11. The highest base major on any candidate classpath is 55; the multi-release entries above 11 (`byte-buddy` 68, `logback-core` 65) are under `META-INF/versions/N` and are not loaded by a JDK 11. No legacy module cannot meet the floor; `webtools-scraping` and `webtools-browser` lose nothing.
- **Measured side effect, outside this question.** The `webtools-http` runtime classpath resolves `kotlin-stdlib` 2.2.21 and `okio` 3.18.1 because `okhttp-jvm` 5.5.0's `.module` requires them. A consumer of the new modules therefore needs a stdlib of at least 2.2.21 regardless of the published stdlib requirement of WebTools itself.
- **Measured.** The guard task's runtime-classpath scan (F3) turns this table into a standing check, because most of these dependencies publish no Gradle metadata and so Gradle's own attribute matching cannot reject them.

### F5. The existing sources against the JDK 11 API

- **Measured.** With `-Xjdk-release=11` (jvmTarget 11, java compat 11) all of `compileKotlin` and `compileTestKotlin` for the three modules: **no errors, no new warnings.** A JDK 11 toolchain build (`jvmToolchain(11)`) also compiled all three. A scan of the baseline bytecode (compiled without the floor) for JDK 12+ suspects (`List.removeFirst/removeLast/reversed/getFirst/getLast`, `String.formatted/indent/transform`, `Stream.toList`, `Thread.ofVirtual`, `HexFormat`, `Math.clamp`, ...) found none.
- **Measured.** Public and protected signatures (`javap -protected`) of every class of the three modules, floor build versus current build: zero differing lines (1007, 34 and 16 lines). The POMs and the Module Metadata differ only in the `org.gradle.jvm.version` values (consistent with the build-topology findings).
- **Measured.** Test results on the JDK 23 host with `-Xjdk-release=11` and on a JDK 11 launcher are equal: `webtools-udp` 414 tests (gating, component, deterministic) plus integration 115, e2e 34, nonfunctional 65, uat 13 skipped; `webtools-scraping` 12 (11 plus one added probe test); `webtools-browser` 3. No failures.
- **Constraint for new code at target 11.** `@JvmRecord` and Java `sealed` permits need target 16 and 17 and are unavailable; Kotlin `sealed` classes compile (the existing module already has an internal sealed hierarchy). JDK 12+ APIs are rejected by the compiler, and the Kotlin extension is chosen where one exists (`removeFirst`, `reversed`).

### F6. README and documentation

- **Repository.** `README.md:26` is the only statement of a JDK floor: "Requires JDK 11 or newer. Built with Kotlin 2.2." A search of `README.md`, `docs/webtools-udp-architecture.md`, `docs/webtools-udp-protocol.md`, `.aiassistant/rules/*.md`, every `build.gradle.kts` and all KDoc under `webtools-*/src` for JDK or Java version statements found nothing else; the only other hits are KDoc sentences that use "the JDK" generically (`Connector.kt:126`, `CommonChannel.kt:37`, `MultiConnectionUDPClient.kt:64`, `MultiConnectionUDPServer.kt:199`). There is no CI configuration or CONTRIBUTING file in the repository. With the floor applied the README sentence becomes true as written and needs no edit for the floor; it can optionally say "Compiled to Java 11 bytecode".

### F7. Versioning effect (for the plan; release timing is not settled here)

- **Repository.** `webtools-udp/build.gradle.kts:101` is `version = "2.0.0a"` (unreleased; Central has 1.0.0, 1.1.0, 2.0.0). `webtools-scraping` and `webtools-browser` inherit `version = "1.0.0"` from `build.gradle.kts:14` (1.0.0 is on Central). The new modules would inherit 1.0.0.
- **Documented.** Organisation guide: "`Major.Feature.MinorChange`, optionally a trailing letter for a bug fix (e.g. `1.5.2a`)"; `fix:` / `perf:` is "MinorChange, or a trailing letter"; "Gradle orders a version with an extra non-numeric part *below* the same version without it: `1.5.2a` < `1.5.2`".
- **Effect.** The floor changes no source and no public API (F5), only the bytecode version and the metadata, so it is a fix:
  - `webtools-udp`: it already carries the unreleased `2.0.0a`; the floor correction rides in it. No API change, so the deprecate-don't-break rule is not engaged.
  - `webtools-scraping` and `webtools-browser`: next release `1.0.0a` under the trailing-letter rule (or `1.0.1` under the MinorChange alternative). Each needs an explicit `version` in its own build file, since the root default stays 1.0.0 for the new modules.
- **Hazard specific to this fix.** `1.0.0a` orders below `1.0.0`, and `1.0.0` is the Java 23 artifact. A consumer graph that also asks for plain `1.0.0` (or `2.0.0` for udp) silently resolves the Java 23 artifact, which is the defect being fixed. The guide's remedy is a `strictly` request on the consumer and in any Spartan Labs library that depends on the lettered version. A `1.0.1` / `2.0.1` MinorChange orders above the bad release and needs no pin (Consideration 2).

## How comparable projects do it

- **kotlinx.coroutines 1.10.2** (`buildSrc/src/main/kotlin/kotlin-jvm-conventions.gradle.kts` at tag 1.10.2): `java { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = ... }`, `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_1_8 }; jvmToolchain(jdkToolchainVersion) }`. A fixed target for the bytecode and a separate toolchain for the build JDK: "(c)" with a toolchain compile.
- **tapmoc 0.5.0** (GradleUp; used by Okio per the GeneralTools research): `KgpImpl.javaCompatibility` adds `-Xjdk-release=<v>` and sets `jvmTarget` to the same version, with the comment that `jvmTarget` is required "or we get an error such as e: '-Xjdk-release=11' option conflicts with '-jvm-target 17'". It records that the flag is ignored for the Android KMP library plugin (KT-81606), which is not WebTools' target. This is option (a), as a plugin.
- **kotlinx-datetime 0.8.0** (via GeneralTools' findings): `java.mainToolchainVersion=8` plus an explicit `attribute(TARGET_JVM_VERSION_ATTRIBUTE, 8)` in `jvm {}`; its module declares 8. Other KMP libraries (Ktor 3.6.0, Okio 3.18.2, kotlinx-io, kotlinx-coroutines 1.11.0, kotlin-logging 8.0.4) publish no attribute on the jvm variants.
- **GeneralTools** (this organisation): `jvmToolchain(17)`, foojay 1.0.0, explicit `TargetJvmVersion`. That is option (b); it also requires JDK provisioning on every machine, which is right for a library whose consumers do not include it as a nested build.
- **OkHttp 5.5.0**: its module build uses `jvmToolchain(21)` while its README promises Java 8+ (the bytecode target is separate from the build toolchain).
- **Jake Wharton** (single source, via the GeneralTools findings): the bytecode level and the JDK API set are independent; `-Xjdk-release` addresses the API set. The measured `removeFirst` case in F1 is an instance of it.

## Pitfalls & regrets

1. `jvmTarget = 11` alone passes every build and test on a newer JDK while JDK 21+ members are in the bytecode (F1). The Java 23 artifacts on Central are what "no floor" produces.
2. `-Xjdk-release=11` without `jvmTarget` fails (KGP's own `-jvm-target` conflicts). Set both from one constant.
3. For `kotlin("jvm")` modules, `jvmTarget` without matching `java {}` compatibility fails KGP validation even when no Java source exists. Do not downgrade the validation mode to hide it; it is also what writes the variant attribute.
4. A KMP `jvm()` target writes no `org.gradle.jvm.version` under any of these settings, `jvmToolchain` included; the explicit attribute is required, and it must be set again on any custom variant (the fixtures variants, which are declared by hand).
5. foojay 0.8.0 stays green on a machine whose cache already holds the requested JDK and fails on a fresh one, so the defect can hide on the maintainer's machine until CI or a new contributor appears.
6. A toolchain request in a module builds fine locally and then turns every consumer's nested composite build into one that needs a JDK 11 on the consumer's machine or provisioning.
7. Gradle 9 cannot run on JDK 11 (needs 17 to 26). "Run on 11" can only mean a launcher or toolchain for the `Test` task, never the build itself.
8. Gradle's `org.gradle.jvm.version` is not written for POM-only dependencies (selenium, jsoup, unirest, logback, Rome), so Gradle's attribute matching cannot reject them; the class-file scan can.
9. `1.0.0a` orders below `1.0.0` (F7); the bytecode fix is the one release where picking the wrong one is the original bug.
10. The `-Xjdk-release` flag is `-X`: Kotlin does not promise it, and it is "not guaranteed to be effective for each JDK distribution". It worked on Oracle JDK 23.0.2 and Temurin 21.0.9 here; a build on a JDK without `ct.sym` data is untested.

## Recommendation for this repository

**Mechanism (option (a)).** In `build-logic` (the included build set up by the build-topology work), `WebToolsBuild.JAVA_FLOOR = 11` is read by both convention plugins:

- `webtools.kotlin-jvm-library`: `kotlin { compilerOptions { jvmTarget.set(JvmTarget.fromTarget("11")); freeCompilerArgs.add("-Xjdk-release=11") } }` and `java { sourceCompatibility = targetCompatibility = JavaVersion.toVersion(11) }`.
- `webtools.kotlin-multiplatform-library`: `kotlin { jvm { compilerOptions { jvmTarget.set(...); freeCompilerArgs.add("-Xjdk-release=11") }; attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11) } } }`.
- Module-specific variants (the fixtures variants of `webtools-http`) read the same constant for their `TargetJvmVersion`.

Reasoning: it is the only option that satisfies all four requirements (bytecode, metadata, API surface, one value) without provisioning a JDK, so the repository keeps working with foojay 0.8.0, and consumers' composite builds do not need a JDK 11. A toolchain gives the same API limit but at the cost of F2, and for KMP it still needs the explicit attribute.

**Rejected: `jvmTarget` alone** (misses JDK 21 API, F1); **`jvmToolchain(11)` as the floor mechanism** (forces the foojay bump into this work, makes consumer composites need JDK 11, adds nothing the flag does not, and KMP still needs the attribute).

**Guard.** Add `verifyJavaFloor` in `build-logic`, registered by both plugins and made a dependency of `check`. It takes as inputs the module's jar tasks (`jar`; `jvmJar` and `testFixturesJar` for KMP), the `GenerateModuleMetadata` outputs and the resolved runtime classpath, and fails if any non-multi-release class is above major 55 or any `java-api` / `java-runtime` variant that is not a sources/javadoc variant declares an `org.gradle.jvm.version` other than 11. The prototype is about 60 lines, uses only a typed task with `@InputFiles` and `@Input`, and is configuration-cache compatible (measured). It is a Gradle verification task, not a unit test, because the facts live in the publishable files; if the five-level test layout must hold it, the same assertions can be a `testing.nonfunctional` test fed the file paths as system properties, at the cost described in F3.

**Test JDK.** Keep the level tasks on the build JDK by default. Add the JDK 11 `javaLauncher` for the level `Test` tasks as the second phase (Consideration 1). It has been proven to work on this repository (all levels green on 11.0.32.1) and costs the foojay bump plus a 199 MB download per cold machine.

**The three existing modules.** No source or API change. Their build files change only by what the build-topology work already moves into the convention plugins. `webtools-udp` keeps its public API (javap-identical).

**README.** Keep `README.md:26` as is; it becomes accurate.

**Versions.** See F7 and Consideration 2.

## Considerations

These need the user's call; unresolved Considerations block execution.

### 1. Do the level test tasks run on a JDK 11 launcher in this work

- **Options.**
  - **(A) Build JDK only, plus the `verifyJavaFloor` guard.** No foojay change, no provisioning. Cost: the repository never executes the artifacts on 11, so a runtime-only incompatibility (a dependency's native call, a reflective access) would reach a Java 11 consumer. The compile-time API limit and the class-major scan still hold.
  - **(B) Add the JDK 11 test launcher, and bump foojay to 1.0.0 in the same change.** Cost: the bump (WebTools#58 proposes it) moves into this work's scope; a 199 MB download on each cold machine or CI cache; tests fail without network and without a JDK 11. Benefit: measured green on 11 for all levels, and it keeps proving it.
  - **(C) Add the launcher without the bump**, supplying JDK 11 from the environment (`org.gradle.java.installations.fromEnv` or `paths`). Cost: a manual JDK 11 installation on every contributor machine; a fresh machine fails with the foojay 0.8.0 error (not a clear message).
- **Recommendation: (A) for this work, (B) as a separate step once #58's practices exist.** The guard plus `-Xjdk-release` already covers the three requirements; the launcher adds runtime evidence at the price of the resolver bump.

### 2. The lettered version of the two legacy modules

- **Options.**
  - **`1.0.0a`** (trailing letter): follows the organisation's letter rule; orders below `1.0.0` (the Java 23 artifact), so any graph with plain `1.0.0` silently keeps the bad one unless the consumer or library uses `strictly`.
  - **`1.0.1`** (MinorChange): orders above `1.0.0`, no pin needed; uses the third number, which this repository otherwise reserves for additions.
- **Recommendation: `1.0.0a` with a `strictly` request in BotTools' build,** because it follows the stated rule and BotTools is the one known consumer; the alternative is a legitimate choice if more consumers are expected. `webtools-udp` is unaffected (the floor rides in the unreleased `2.0.0a`).

## Known gaps

1. **Consumer composite with provisioning enabled and JDK 11 absent** was not measured (JDK 11 had already been provisioned on the probe machine). Which build's toolchain resolver Gradle would consult for a toolchain request made inside an included build is therefore unknown; the measured facts are only the provisioning-disabled outcomes in F2.
2. **Only Windows, Oracle JDK 23.0.2 and Temurin 21.0.9 build hosts, Temurin 11.0.32.1 as the launcher.** No Linux CI, no JDK 24 to 26 build host, and no JDK without `ct.sym` data. `-Xjdk-release` is documented as "not guaranteed ... for each JDK distribution".
3. **The guard task is a prototype in a probe copy,** not in the repository, and it was run against the build-topology probe's `build-logic`, not the final plugin scripts. Its `GenerateModuleMetadata` discovery uses `afterEvaluate`; whether that is the cleanest hook for the real plugins was not explored.
4. **Class-load verification is by class-file major and by test runs, not by loading every class of every dependency on JDK 11.** Selenium, htmlunit and Ktor 1.5 paths that the repository's tests do not execute are covered only by their declared floors and class versions.
5. **`webtools-scraping` and `webtools-browser` classpaths were resolved for a synthetic project with the declared coordinates,** not through the real module's `runtimeClasspath`; conflict resolution against the module's own `kotlin-stdlib` 2.2.0 would only lower versions of the Kotlin 1.5 family, not raise any class major.
6. **Other test levels of `webtools-scraping` and `webtools-browser`** (browser `uat`, any network-bound scraping test) were not run on 11; the gating, component and deterministic levels were.
7. **Android KMP library plugin / non-JVM targets and `-Xjdk-release`** are not covered; the KMP targets here are `jvm()` only (js, wasm, native not applied).
8. **Maven Central upload and signing were not exercised.** The probes published to a file repository without signing, and no signing or Central task was run.

## Load-bearing claims

1. **A Java toolchain sets `-jdk-home`, defaults `jvmTarget`, and applies to Java compile/test tasks.** "Sets the [`-jdk-home` option](compiler-reference.html#jdk-home-path) available for JVM targets." and "Sets the [`compilerOptions.jvmTarget`](gradle-compiler-options.html#attributes-specific-to-jvm) to the toolchain's JDK version if the user doesn't set the `jvmTarget` option explicitly." and "Sets the toolchain to be used by any Java compile, test, and javadoc tasks." <https://kotlinlang.org/docs/gradle-configure-project.html> (section "Gradle Java toolchains support"), read 2026-10-20, Kotlin 2.x docs.
2. **KGP validates `jvmTarget` against the related Java compile task, default `error`.** "For related tasks like these, the Kotlin Gradle plugin checks for JVM target compatibility. Different values of the `jvmTarget` attribute in the `kotlin` extension or task and `targetCompatibility` in the `java` extension or task cause JVM target incompatibility." and "`error` – the plugin fails the build; the default value for projects on Gradle 8.0+." and "Assuming that your JDK version is `17`, your published library artifact will declare itself compatible with JDK 17+: `org.gradle.jvm.version=17`, which is wrong." <https://kotlinlang.org/docs/gradle-configure-project.html> (section "Check for JVM target compatibility of related compile tasks"), read 2026-10-20.
3. **`-Xjdk-release` limits the JDK API and is not guaranteed on every distribution.** "Specify the target version of the generated JVM bytecode. Limit the API of the JDK in the classpath to the specified Java version. Automatically sets `-jvm-target version`." and "This option is not guaranteed to be effective for each JDK distribution." <https://kotlinlang.org/docs/compiler-reference.html>, read 2026-10-20. Source: `Xjdk-release` description "Compile against the specified JDK API version, similarly to javac's '-release'. This requires JDK 9 or newer." in <https://github.com/JetBrains/kotlin/blob/v2.2.0/compiler/arguments/src/org/jetbrains/kotlin/arguments/description/JvmCompilerArguments.kt> (tag v2.2.0) and the same description at tag v2.4.21.
4. **There is no Gradle-toolchain integration for the flag.** KT-49746 "Support -Xjdk-release in gradle toolchain", State: Backlog. <https://youtrack.jetbrains.com/issue/KT-49746>, fetched 2026-10-20. KT-29974 "Add a compiler option '-Xjdk-release' similar to javac's '--release' to control the target JDK version", State: Fixed. <https://youtrack.jetbrains.com/issue/KT-29974>.
5. **The flag needs `jvmTarget` set as well.** "jvmTarget needs to be set as well, or we get an error such as / e: '-Xjdk-release=11' option conflicts with '-jvm-target 17'. Please remove the '-jvm-target' option". <https://github.com/GradleUp/tapmoc/blob/v0.5.0/tapmoc-gradle-plugin/src/kgp/kotlin/com/gradleup/tapmoc/internal/KgpImpl.kt> (tag v0.5.0).
6. **Gradle 9.7.1 cannot run on JDK 11.** "A JVM version between 17 and 26 is required to execute Gradle." <https://docs.gradle.org/9.7.1/userguide/compatibility.html>, Gradle 9.7.1.
7. **`--release` is the strict-API mechanism and combines with toolchains.** "This flag prevents accidental use of newer APIs that are not available in the specified version. However, it does not control which JDK is used" and "This method can be combined with toolchains if you need both a specific JDK and strict cross-compilation". <https://docs.gradle.org/9.7.1/userguide/toolchains.html>, Gradle 9.7.1.
8. **The attribute's semantics.** "Indicates the JVM version compatibility. Integer using the version after the 1. for Java 1.4 and before, the major version for Java 5 and beyond. Defaults to the JVM version used by Gradle, lower is compatible with higher, prefers highest compatible." <https://docs.gradle.org/9.7.1/userguide/variant_attributes.html>, Gradle 9.7.1.
9. **A toolchain needs a resolver plugin to be provisioned, and Gradle's example uses foojay 1.0.0.** "The jvm block in toolchainManagement only resolves after applying a toolchain resolver plugin." and `id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")`. Auto-provisioning is controlled by `org.gradle.java.installations.auto-download`. <https://docs.gradle.org/9.7.1/userguide/toolchains.html>, Gradle 9.7.1.
10. **foojay 0.8.0 references the removed `IBM_SEMERU` field.** `JvmVendorSpec.IBM_SEMERU to "Semeru",` in <https://github.com/gradle/foojay-toolchains/blob/foojay-toolchains-plugin-0.8.0/foojay-resolver/src/main/kotlin/org/gradle/toolchains/foojay/distributions.kt> (tag `foojay-toolchains-plugin-0.8.0`); 1.0.0 resolves it with `addPossiblyUndefinedVendor("IBM_SEMERU", "Semeru", tmpMap)` (tag `foojay-toolchains-plugin-1.0.0`, same file).
11. **foojay's own compatibility statement.** "Versions 1.0.0 and after require Java 17 or later and Gradle 7.6 or later. Versions prior to 1.0.0 require Java 8 or later and Gradle from 7.6 to 8.14.x." and "`IBM_SEMERU` is deprecated in Gradle for a while and removed in Gradle 9+." <https://github.com/gradle/foojay-toolchains/blob/f20247129f3b1c40ed66e25b9fd68a7314424f39/README.md> (commit f20247129f3b1c40ed66e25b9fd68a7314424f39 of the default branch, read 2026-10-20).
12. **The reported failure.** "Class org.gradle.jvm.toolchain.JvmVendorSpec does not have member field 'org.gradle.jvm.toolchain.JvmVendorSpec IBM_SEMERU'" and "I'm creating this issue to request that the README be updated to make it clear that version 1.0.0 is required for Gradle 9". <https://github.com/gradle/foojay-toolchains/issues/151> (closed 2025-11-13; reported against 0.9.0).
13. **foojay 1.0.0 is the latest release.** `<latest>1.0.0</latest>` / `<release>1.0.0</release>` in <https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/maven-metadata.xml>, read 2026-10-20.
14. **Dependency floors declared in Gradle Module Metadata.** `"org.gradle.jvm.version": 11,` in <https://repo1.maven.org/maven2/com/github/ben-manes/caffeine/caffeine/3.3.1/caffeine-3.3.1.module> and in <https://repo1.maven.org/maven2/com/apptasticsoftware/rssreader/3.12.0/rssreader-3.12.0.module>; `"org.gradle.jvm.version": 8,` in <https://repo1.maven.org/maven2/com/squareup/okhttp3/mockwebserver3/5.5.0/mockwebserver3-5.5.0.module>; no `.module` (HTTP 404) for `selenium-java:4.48.0`, `jsoup:1.15.4`, `unirest-java:1.4.9`, `slf4j-api:2.0.13`, `logback-classic:1.5.6`, `rome:2.1.0`; `okhttp-jvm:5.5.0`'s `.module` has no `org.gradle.jvm.version` and `"requires": "3.18.1"` (okio) and `"requires": "2.2.21"` (kotlin-stdlib) in <https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-jvm/5.5.0/okhttp-jvm-5.5.0.module>. Read 2026-10-20.
15. **Stated Java floors of the main dependencies.** Selenium: "Requires Java 11+." <https://github.com/SeleniumHQ/selenium/blob/selenium-4.48.0/java/README.md>. OkHttp: "OkHttp works on Android 5.0+ (API level 21+) and Java 8+." <https://github.com/square/okhttp/blob/parent-5.5.0/README.md>. Caffeine: "For Java 11 or above, use `3.x` otherwise use `2.x`." <https://github.com/ben-manes/caffeine/blob/v3.3.1/README.md>. (The class-file majors in F4 are measured from the jars, not claimed from these.)
16. **kotlinx.coroutines 1.10.2 pairs a fixed target with a separate toolchain.** `targetCompatibility = JavaVersion.VERSION_1_8`, `jvmTarget = JvmTarget.JVM_1_8` and `jvmToolchain(jdkToolchainVersion)` in <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/buildSrc/src/main/kotlin/kotlin-jvm-conventions.gradle.kts> (tag 1.10.2).
17. **Organisation versioning rules.** "`Major.Feature.MinorChange`, optionally a trailing letter for a bug fix (e.g. `1.5.2a`)." and "Gradle orders a version with an extra non-numeric part *below* the same version without it: `1.5.2a` < `1.5.2` (Gradle's "Version ordering" rules)." and "A library pins a lettered version with `strictly`." <https://github.com/SpartanLaboratories/.github/blob/main/CONTRIBUTING.md> (sections "Versioning" and "Lettered versions in dependency graphs"), read 2026-10-20.
