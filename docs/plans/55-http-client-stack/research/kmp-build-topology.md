# Build topology for Kotlin Multiplatform modules beside the Kotlin/JVM modules

## Question

How should the WebTools Gradle build host the Kotlin Multiplatform modules `webtools-http` and the feeds module beside the three existing Kotlin/JVM modules (`webtools-udp`, `webtools-scraping`, `webtools-browser`)? An answer has to settle: the Kotlin Gradle plugin (KGP) version, where plugins are applied and where shared configuration lives, which KMP targets are declared, how the seven level test tasks work across both kinds of module, how logging works from common code, and how the feeds module relates to `webtools-http`.

Requirements the answer meets:

- Kotlin stays 2.2.0 on Gradle 9.7.1.
- Published Kotlin metadata is readable by a Kotlin 2.2.x compiler, and the published stdlib requirement is no newer than 2.2.x.
- No `-SNAPSHOT` dependency.
- Gradle Module Metadata sits beside each POM.
- `explicitApi()` applies to the new modules only.
- Test packages are `com.spartanlabs.testing.<level>.webtools.<module>`.
- The three existing modules keep their coordinates and published shape.
- One Java 11 value reaches both kinds of module. How the floor is enforced (`-Xjdk-release` or a toolchain) belongs to the Java 11 floor research.
- A consumer's nested composite build can substitute the new modules.
- The build is configuration-cache compatible, with no `-P` reliance across included builds.
- The README's build commands keep working.

## Bottom line

- **KGP 2.2.0 carries a multiplatform module on Gradle 9.7.1; no change of Kotlin is needed.**
  - It is not documented as supported: the Kotlin compatibility table lists every 2.2.x release as Gradle 7.6.3 to 8.14. No 2.2.x patch is documented for Gradle 9.
  - Measured: configure, compile, test, publish and the configuration cache all work. The only diagnostic is one Gradle-10 deprecation ("The archives configuration has been deprecated for artifact declaration", raised when the `jvm()` target is created). It is KT-78620, fixed in the 2.3 line. It also appears on 2.2.21, so a 2.2.x bump buys nothing.
  - The published artifacts carry `@Metadata mv=[2,2,0]` and a `kotlin-stdlib` requirement of `2.2.0`.
  - If the deprecation must go before Gradle 10, the least change is KGP 2.3.x or 2.4.x with `languageVersion = apiVersion = 2.2` and `coreLibrariesVersion = "2.2.0"`. Measured on 2.4.21: those pins give `mv=[2,2,0]` and stdlib `2.2.0`; without them 2.4.21 writes `mv=[2,4,0]` and stdlib `2.4.21`. That route changes the compiler, so it is a fallback and not adopted.
- **Topology: an included `build-logic` build with three precompiled convention plugins, and a root build reduced to `group` and `version`.**
  - The plugins are `webtools.kotlin-jvm-library`, `webtools.kotlin-multiplatform-library` and `webtools.published-library`. A `WebToolsBuild` object in `build-logic` holds `JAVA_FLOOR = 11` and the level list.
  - I built and ran this shape. The three existing modules publish byte-identical POMs and Gradle Module Metadata except `org.gradle.jvm.version` 23 to 11 (class files major 67 to 55).
  - It works in a consumer's nested composite when both builds have a `build-logic` of the same name (measured). The root `subprojects {}` + `plugins.withId(...)` alternative and `buildSrc` also work; they are not preferred (see Recommendation).
  - The Java floor is one constant read by both plugins. The KMP plugin sets `jvmTarget` and the explicit `org.gradle.jvm.version` attribute. Without that attribute a KMP JVM publication declares none (measured: zero occurrences).
- **`jvm()` alone does not enforce that `commonMain` is common. This needs a decision on the target set (Consideration 1).**
  - Measured and documented: with only `jvm()`, `java.io.File`, `java.time.Instant`, `System.currentTimeMillis()` and `String.format` compile in `commonMain`, and `compileKotlinMetadata` is `SKIPPED`.
  - A second target makes the compiler reject them (`compileCommonMainKotlinMetadata`, `compileKotlinJs`). That check needs no Node download; only running tests on that target does.
  - There is no public way to declare a target and leave it unpublished. Disabling its publish tasks leaves the root `.module` pointing at a module that does not exist (measured).
  - The options that work are a published second target, or an unpublished check-only module that compiles the same source directory for js. A `-no-jdk` compilation inside the jvm target does not work (false positives on `kotlin.time.Duration`).
- **Level tasks: plain `Test` tasks registered from one helper over the JVM test compilation, selected by package filter `com.spartanlabs.testing.<level>.*` with `isFailOnNoMatchingTests = false`.**
  - The same seven task names keep working in every module, KMP or JVM. Measured on the three JVM modules: the filter selects exactly the classes the `@Tag`s select (all 99 test files carry a tag equal to their package level; the 19, 21 and 9 gating, component and deterministic classes of `webtools-udp` match).
  - Move the JVM modules to the package filter too, for one rule. It needs no `-P`, so it is safe across included builds.
  - A KMP module has no `test` task, so `./gradlew test` from the root silently skips it. A lifecycle `test` that depends on `jvmTest` fixes this.
- **Publishing and logging.**
  - vanniktech 0.36.0 publishes the root `webtools-http` and `webtools-http-jvm`. Each has a `.module` and a `.pom`. The root jar contains no classes, so a plain-Maven consumer must depend on `webtools-http-jvm`.
  - Logging: no logging library in `commonMain`. `jvmMain` logs through `slf4j-api` as `implementation`. kotlin-logging 8.0.4 is rejected for now (stdout startup line, an extra runtime dependency on every consumer, and no gain with one target).
  - The three existing modules keep `api(slf4j-api)`, declared in their own build files; the new modules use `implementation`.
- **Feeds module.** Both shapes work (measured). It needs a decision (Consideration 2).
  - A JVM-only module built with `api(project(":webtools-http"))` publishes a POM that maps the dependency to `webtools-http-jvm`.
  - A multiplatform feeds module with a Rome parser in `jvmMain` publishes `webtools-feeds` and `webtools-feeds-jvm`.
  - In both, the test fakes come from `testFixtures(project(":webtools-http"))`. The KMP source-set DSL has no `testFixtures()`, so a KMP module declares it as `"jvmTestImplementation"(testFixtures(project(":webtools-http")))`.
- **No binding requirement fails.** The one that needs a user decision is where the "ports live in common code" guarantee comes from (Consideration 1).

## Findings

Evidence tiers: **documented** (official docs, source at a tag, tracker), **observed practice** (what mature code visibly does), **measured** (I built it in a throwaway probe outside the repository; Gradle 9.7.1, JDK 23 launcher, Windows), **single source** (one party's statement). Probes were copies of the repository restructured as described, plus consumer projects that include the copy as a nested composite.

### F1. KGP 2.2.0 on Gradle 9.7.1: works, undocumented, one deprecation

- **Documented.** The Kotlin compatibility table lists `2.2.0–2.2.10` and `2.2.20–2.2.21` as Gradle `7.6.3–8.14`; `2.3.0` and `2.3.10` as up to 9.0.0; `2.3.20–2.3.21` as up to 9.3.0; `2.4.0–2.4.10` as up to 9.5.0; and `2.4.20–2.4.21` as up to `maxGradleVersion`, which is `9.7.0` in `docs/v.list`. So no documented KGP row contains Gradle 9.7.1, and only 2.4.20 and later reach 9.7.x. The page says newer Gradle versions "might" give deprecation warnings.
  Source: <https://kotlinlang.org/docs/gradle-configure-project.html> (markdown source `docs/topics/gradle/gradle-configure-project.md` and `docs/v.list` in <https://github.com/JetBrains/kotlin-web-site>), read 2026-10-20.
- **Measured.** In the probe, KGP 2.2.0 with Gradle 9.7.1 configured, compiled, tested and published a `jvm()`-only multiplatform module, the three JVM modules and a JVM feeds module, with the configuration cache on. `--warning-mode all` shows one deprecation, at the `jvm()` call:
  `The archives configuration has been deprecated for artifact declaration. This will fail with an error in Gradle 10.`
  KGP 2.2.21 prints the same line. KGP 2.3.21 and 2.4.21 print nothing. The `kotlin("jvm")` modules never printed it.
- **Documented.** The cause is KT-78620, "Remove adding artifacts to the deprecated Gradle "archives" configuration": State Fixed, Planned for 2.3.20-Beta1, Affected 2.2.20-Beta1.
  Source: <https://youtrack.jetbrains.com/issue/KT-78620> (fetched 2026-10-20).
- **Documented.** vanniktech 0.36.0 states a minimum of KGP 2.2.0 and was tested up to KGP 2.3.0 and 2.3.20-Beta1.
  Source: <https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/CHANGELOG.md>.
- **Measured.** Published metadata by KGP version (JVM variant of the probe `webtools-http-jvm`):

  | KGP | pins | `@Metadata mv` | `kotlin-stdlib` requirement |
  |---|---|---|---|
  | 2.2.0 | none | `[2,2,0]` | `2.2.0` |
  | 2.3.21 | none | `[2,3,0]` | `2.3.21` |
  | 2.4.21 | none | `[2,4,0]` | `2.4.21` |
  | 2.4.21 | `languageVersion = apiVersion = 2.2`, `coreLibrariesVersion = "2.2.0"` | `[2,2,0]` | `2.2.0` |

  The pinning rule (language version decides which compilers can read the library; API version decides the minimum stdlib) is in the Kotlin library-author guidelines, so a pinned newer KGP is a valid fallback for a JVM-only publication. Non-JVM targets would not follow the pin; that concern belongs to Consideration 1.
- **Measured.** Gradle 9.7.1 embeds Kotlin 2.4.0. A `build-logic` build using `kotlin-dsl` and depending on `kotlin-gradle-plugin:2.2.0` compiled and ran fine, so the embedded compiler and the pinned KGP do not conflict.
- **Not documented:** a 2.2.x patch release that is documented for Gradle 9 does not exist, so the question's "2.2.10, 2.2.20 or 2.2.21" alternative cannot be answered by the table; I measured 2.2.21 and it behaves as 2.2.0.

### F2. Where plugins are applied and shared configuration lives

Three topologies were built, run (`build`, level tasks, publish) and used from a consumer. All pass.

| Topology | What it is | Measured |
|---|---|---|
| **S** | Root `plugins { kotlin("jvm"); kotlin("multiplatform"); vanniktech } apply false`; root `subprojects {}` applies vanniktech and branches with `plugins.withId("org.jetbrains.kotlin.jvm")` and `...multiplatform`; each module adds `plugins { kotlin("jvm") }` or `kotlin("multiplatform")` | Works. Needs `extensions.configure<KotlinMultiplatformExtension>` (no typed `kotlin {}` in a `subprojects` block). The two Kotlin plugins share one root `plugins` block at one version. |
| **L** | `pluginManagement { includeBuild("build-logic") }`; precompiled script plugins in `build-logic/src/main/kotlin`; KGP and vanniktech versions pinned once in `build-logic/build.gradle.kts` | Works. Typed `kotlin {}` and `mavenPublishing {}` accessors inside the plugin scripts. Plugin classes (`WebToolsBuild`) are visible from module build scripts. |
| **B** | Same scripts in `buildSrc` | Works, including in the nested composite. |

- **Documented.** Gradle describes cross-project configuration as "an improper way to share build logic between subprojects" and says `buildSrc` changes "invalidate the configuration phase and require re-execution of all tasks". It names a dedicated composite build (`build-logic`) as the alternative.
  Source: <https://docs.gradle.org/9.7.1/userguide/sharing_build_logic_between_subprojects.html>.
- **Observed practice.** The organisation's MyGameTools uses `build-logic` with `registerLevelTest`; SpartanGraphics plans precompiled script plugins `spartangraphics.kmp-library`, `spartangraphics.jvm-library` and `spartangraphics.publishing`, with `pluginManagement { includeBuild("build-logic") }` (its `build-logic-and-version-catalog.md`). BotTools uses `build-logic` too.
- **Shared values.** The Java floor is `WebToolsBuild.JAVA_FLOOR` in `build-logic`. It is read by:
  - the JVM plugin (`kotlin { compilerOptions { jvmTarget } }` and `java { sourceCompatibility; targetCompatibility }`);
  - the KMP plugin (`jvm { compilerOptions { jvmTarget }; attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, ...) } }`);
  - a module build script, for example the fixtures variants' `TargetJvmVersion` (measured: `WebToolsBuild.JAVA_FLOOR` is usable from `webtools-http/build.gradle.kts`).

  **Measured:** with the KMP plugin and no explicit attribute, neither the root nor the `-jvm` `.module` contains `org.gradle.jvm.version`. With the attribute, every `jvm*Elements-published` variant says `11`. Class files in `webtools-udp` (was 67) and `webtools-http-jvm` are major 55.
- **Measured: the three existing modules publish unchanged.** In topology L, publishing `webtools-udp` 2.0.0a, `webtools-scraping` 1.0.0 and `webtools-browser` 1.0.0 gave POMs identical to the current build's and Gradle Module Metadata identical except `"org.gradle.jvm.version": 23` becoming `11`. This holds only if `api(slf4j-api:2.0.13)` stays on those three modules (moved into their own build files; see F7).
- **Composite hazard: not observed.** A consumer probe had its own `build-logic` (named `build-logic`, KGP 2.2.20, `jvmToolchain(21)`) and included the WebTools copy, which has its own `build-logic` (KGP 2.2.0). Gradle gave them distinct build paths (`:build-logic` and `:<webtools-dir>:build-logic`). It built, and `io.github.spartanlaboratories:webtools-http:1.0.0` resolved to the included project. Re-run with WebTools in topology S and in topology B: same result. Repeating the same command reused the configuration cache entry.
- **Measured pitfall.** Declaring a Kotlin plugin with a version in a subproject that also loads it elsewhere prints "The Kotlin Gradle plugin was loaded multiple times in different subprojects". Convention plugins apply `org.jetbrains.kotlin.*` by id with no version, so this does not occur.

### F3. Common code is not checked by a single `jvm()` target

- **Documented.** "If your project only has a single target (for example, JVM), you can access target-specific symbols with appropriate visibility from common code. However, as soon as you add a second target, target-specific symbols become unavailable in common code." Source: <https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html>, read 2026-10-20.
- **Measured (`jvm()` only):** `commonMain` containing `java.io.File`, `java.time.Instant`, `System.currentTimeMillis()` and `String.format` compiled with no error and no warning. `compileKotlinMetadata` was `SKIPPED`, and `compileCommonMainKotlinMetadata` does not exist.
- **Measured (`jvm()` + `js { nodejs() }` and `jvm()` + `wasmJs { nodejs() }`):** `compileCommonMainKotlinMetadata` fails with "Unresolved reference 'java'" / `'File'` / `'Instant'` / `'System'`; `compileKotlinJs` fails the same way. The checks took 2 to 7 seconds and downloaded nothing. `wasmJs` needs `@OptIn(ExperimentalWasmDsl::class)` on KGP 2.2.0.
- **What a second target costs (js, measured):**

  | Cost | Observed |
  |---|---|
  | Publications | An extra `webtools-http-js` module (klib, `.module`, `.pom`, sources, javadoc jars), and `jsApiElements-published` variants in the root `.module`. The klib manifest says `abi_version=2.2.0`, `compiler_version=2.2.0`. |
  | `commonMain` dependencies | Must be multiplatform. `kotlinx-coroutines-core` 1.10.2 is. |
  | Tests | `nodejs()` makes `check` run `jsNodeTest` over all of `commonTest`. KGP 2.2.0 downloaded Node 22.0.0 and Yarn 1.22.17 into the Gradle user home and wrote `kotlin-js-store/yarn.lock` at the repository root. Without `nodejs()` or `browser()`, KGP warns "JS Environment Not Selected ... error in the future releases". |
  | Fixtures | Unchanged: the test-fixtures variant is JVM-only, so a js target gets no fixtures. |

- **Documented.** Native targets are the heaviest second target: KGP "automatically creates publications for each target that can be built on the current host", and Maven Central "forbids duplicate publications", so a complete native publication set needs a designated CI host. I did not probe native (Known gaps).
- **Measured: no clean "check-only, unpublished" target inside the module.**
  - `KotlinTarget.publishable` is a read-only `val` whose implementation returns `true`.
  - Disabling the `publishJsPublication*` tasks works (the CC-safe form is `tasks.matching { it.name.startsWith("publishJsPublication") }.configureEach { enabled = false }`; a closure reading `publication` in `onlyIf` fails under the configuration cache). But the root `.module` still lists `jsApiElements-published` with `available-at` pointing at `../../webtools-http-js/1.0.0/webtools-http-js-1.0.0.module`, which was not published.
  - Per the Kotlin publishing documentation, the root publication "serves as an entry point that references all target-specific publications".
- **Measured: a `-no-jdk` compilation of `commonMain` inside the jvm target is not usable.**
  - Setup: a custom `commonCheck` compilation whose source set depends on `commonMain`, compiled with `noJdk = true`.
  - It rejects `java.*` and `System`, but accepts `String.format` (a Kotlin JVM stdlib extension).
  - It also rejects valid common code: a `Mutex` plus `delay(1.seconds)` fails with "Cannot access 'Serializable' which is a supertype of 'Duration'".
- **Measured: an unpublished check-only module works.**
  - Setup: `:webtools-http-commoncheck` is a multiplatform module with only `js { nodejs { testTask { enabled = false } } }`. Its `commonMain` is `kotlin.srcDir(project(":webtools-http").layout.projectDirectory.dir("src/commonMain/kotlin"))` with the same multiplatform dependencies, and it is not published (no vanniktech).
  - `compileKotlinJs` rejects JVM APIs in the shared directory.
  - Costs: one more Gradle project, a duplicated dependency list, and `commonTest` is not covered.

### F4. Level test tasks across both kinds of module

- **Measured.** A helper registers, in every module, seven plain `Test` tasks: `testClassesDirs` and `classpath` come from the test source set (JVM modules) or from `jvm().compilations["test"]` (`output.classesDirs`; `files(runtimeDependencyFiles, output.allOutputs)`), `useJUnitPlatform()`, `filter.includeTestsMatching("com.spartanlabs.testing.<level>.*")` and `filter.isFailOnNoMatchingTests = false`. In `webtools-http`, `componentTest` ran the `commonTest`-sourced component test, `integrationTest` ran the `jvmTest`-sourced integration test, and `gatingTest` (no tests) passed. With `isFailOnNoMatchingTests = true` (Gradle's default), the empty `gatingTest` fails the build.
- **Measured: tags and package filter select the same classes.** Of 99 test files, zero have a `@Tag` that differs from their `testing.<level>` package (the 4 untagged files are `testing/support/` helpers). For `webtools-udp`, `gatingTest`, `componentTest` and `deterministicTest` produced 19, 21 and 9 result files, equal to the number of classes tagged with each level. Root `./gradlew componentTest gatingTest deterministicTest` ran green over all modules.
- **Documented, observed practice.** `commonTest` cannot carry JUnit `@Tag`. GeneralTools and the planned SpartanGraphics build select by package filter. GeneralTools uses `-PtestLevel` on all test tasks because it must also reach `js` and `wasmJs` runners; the plain `Test` tasks here cover the JVM only.
- **Measured: `./gradlew test` from the root silently skips a KMP module.** There is no `test` task in `webtools-http` (`Cannot locate tasks that match ':webtools-http:test'`), and the root invocation succeeded with no `:webtools-http` task. `tasks.register("test") { dependsOn("jvmTest") }` in the KMP plugin restores the README command.
- **`-P` is not needed.** The level names are constants in `build-logic`, so nothing in the design depends on a Gradle property reaching an included build. (If the user later adds a js target, selecting a level on `jsNodeTest` would need GeneralTools' `-PtestLevel` filter; that applies to WebTools' own invocations only.)
- **Repository facts.** The current level tasks are at `build.gradle.kts:44`–`:62` (`sourceSets["test"]` at `:58`–`:60`, `includeTags` at `:60`). `webtools-udp/build.gradle.kts:115`–`:117` calls `tasks.named<Test>("test"|"integrationTest"|"e2eTest"|"nonfunctionalTest")` at module evaluation. In topology L the plugin that registers them is applied in the module's `plugins {}` block, so those names exist before the module body runs (measured).

### F5. Publishing a KMP module with vanniktech 0.36.0 (measured; documented defaults)

- **Coordinates and files for `webtools-http`** (probe, no signing):
  - `webtools-http/1.0.0/`: `.module`, `.pom`, `.jar`, `-sources.jar`, `-javadoc.jar`, `-kotlin-tooling-metadata.json` (and `-test-fixtures.jar` when the fixtures variant is built).
  - `webtools-http-jvm/1.0.0/`: `.module`, `.pom`, `.jar`, `-sources.jar`, `-javadoc.jar`.
  - A `.module` sits beside every `.pom`.
  - The `-jvm` module's `.module` declares `org.gradle.jvm.version: 11` and `kotlin-stdlib` `requires 2.2.0`; `jvmRuntimeElements-published` lists `okhttp-jvm` 5.5.0, `caffeine` 3.3.1, `slf4j-api` and `kotlinx-coroutines-core` 1.10.2; `jvmApiElements-published` lists only `kotlin-stdlib` (everything else is `implementation`).
  - The root `.module` lists `metadataApiElements` and, for the JVM, `jvmApiElements-published` and `jvmRuntimeElements-published` with `available-at` redirects to the `-jvm` module.
- **A plain-Maven consumer.** The root jar `webtools-http-1.0.0.jar` holds only `META-INF/MANIFEST.MF` and `META-INF/kotlin-project-structure-metadata.json`, no classes. Its POM lists only `kotlin-stdlib` at `runtime` scope and no dependency on `webtools-http-jvm`. A Maven consumer therefore needs `io.github.spartanlaboratories:webtools-http-jvm`. A Gradle consumer needs only `webtools-http`. The README's Install section currently shows only Gradle snippets.
- **Defaults.** vanniktech auto-detects the Kotlin plugin. With no `configure(...)` call it still published sources and (empty) javadoc jars for both publications. The explicit `configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))` (GeneralTools' form) pins that behaviour and is where Dokka would later plug in.
  Source: <https://vanniktech.github.io/gradle-maven-publish-plugin/what/>.
- **A JVM-only module depending on a KMP module.** For `webtools-feeds` (`kotlin("jvm")`) with `api(project(":webtools-http"))`, the `.module` requires `webtools-http` (root) and the POM maps it to `webtools-http-jvm` at `compile` scope. A Maven consumer of feeds therefore gets working classes.
- **Not run:** the Central upload, validation, and signing. The probes omitted `signAllPublications()`. Signing of a KMP publication with this plugin is covered in the test-fixtures research.

### F6. Logging from common code

- **Mechanism.** `slf4j-api` is a Java library, so it can only be used from `jvmMain`. In the probe, `jvmMain.dependencies { implementation("org.slf4j:slf4j-api:2.0.13") }` appears in the `-jvm` module's runtime variant only, not in its API variant or the root metadata.
- **Option A, no logging in `commonMain`.** Common code reports through `Result` failures and, where observability is wanted, a hook the consumer supplies; `jvmMain` defaults log through slf4j. No dependency is added.
- **Option B, kotlin-logging 8.0.4 in `commonMain`** (GeneralTools' choice). Source at tag `8.0.4`:
  - `KotlinLogging` has an `init` block that runs `println("kotlin-logging: initializing... active logger factory: ${factory::class.simpleName}")` unless `logStartupMessage` is false; on the JVM that flag defaults from `-Dkotlin-logging.logStartupMessage` or `KOTLIN_LOGGING_STARTUP_MESSAGE`.
  - GeneralTools' research (not re-run here) records that `kotlin-logging-jvm` declares no slf4j dependency, so the module must still supply `slf4j-api`; that it is a one-maintainer project; and that its non-JVM backend is a single-level console logger.
  - As an `implementation` dependency it reaches every consumer's runtime classpath.
  - It pays off only when a second target must log through the same API.
- **Option C, an internal expect/actual logger.** Measured on KGP 2.2.0: `internal expect class` and `actual class` raise "'expect'/'actual' classes ... are in Beta" warnings (the `-Xexpect-actual-classes` flag suppresses them); `internal expect fun logWarn(...)` does not. A missing `actual` is a compile error, so adding a target later forces a decision. It is the smallest way to log from common code if A is not enough.
- **`api` or `implementation` for slf4j.** No slf4j type is in a public signature of the new modules, so `implementation`. The root build's `api(slf4j)` (`build.gradle.kts:29`) cannot stay shared: it would put slf4j into the new modules' compile classpath and POM (`compile` scope, measured in the JVM feeds probe) and contradict the new modules' `implementation`-only rule. The existing modules keep `api` through one line in each of their own build files (measured: POMs identical).

### F7. The feeds module

Both shapes were built (`h` = KMP feeds with a stand-in JVM parser in `jvmMain`; `g` = JVM-only feeds) and consumed by a composite consumer with the KMP `webtools-http`.

| | JVM-only feeds (`kotlin("jvm")`) | KMP feeds (`jvm()` only) |
|---|---|---|
| Dependency on `webtools-http` | `api(project(":webtools-http"))` | `commonMain.dependencies { api(project(":webtools-http")) }` |
| Parser default (Rome, JVM-only) | main source set | `jvmMain`, port in `commonMain` |
| Coordinates published | `webtools-feeds` | `webtools-feeds` (root, no classes) and `webtools-feeds-jvm` |
| Fakes from `webtools-http` | `testImplementation(testFixtures(project(":webtools-http")))` | `dependencies { "jvmTestImplementation"(testFixtures(project(":webtools-http"))) }`; the source-set DSL has no `testFixtures()` (compile error "Unresolved reference 'testFixtures'") |
| Where tests that use the fakes sit | `src/test` | `jvmTest`, because the fixtures are a JVM compilation |
| Composite consumer | resolves feeds and http to included projects, selects `jvmApiElements`, fixtures through the capability | same |
| Cost | the existing convention plugin; no new publication shape | the KMP plugin; two coordinates; plain-Maven consumers use `webtools-feeds-jvm`; `explicitApi()` and KDoc apply either way |

- **`api` vs `implementation` for the dependency.** `implementation` keeps the sibling off a consumer's compile classpath but leaves it in the runtime variant. It is only right if no `webtools-http` type is in the feeds public API (a fetch function that takes the transport port makes it `api`). In KMP, `implementation` in `commonMain` appears only in the JVM runtime variant (same mechanism as `okhttp-jvm` above).
- **Composite.** `webtools-feeds`, `webtools-http` and `webtools-udp` resolved to included projects; a consumer test using `testFixtures("io.github.spartanlaboratories:webtools-http:1.0.0")` compiled and ran through the nested composite, with the KMP feeds module also included.

### F8. Composite substitution and the configuration cache

- **Measured.** From a consumer with `includeBuild("../<WebTools copy>")`:
  - `dependencyInsight` on `compileClasspath` shows `project ':<dir>:webtools-http' (by composite build)`, variant `jvmApiElements`, with `org.gradle.jvm.version` provided 11 and requested 21, `platform.type jvm`.
  - `webtools-udp` (JVM-only) and `webtools-feeds` substitute the same way.
  - `webtools-http` brings `okhttp-jvm` 5.5.0, `caffeine` 3.3.1 and `kotlinx-coroutines-core-jvm` 1.10.2 onto the runtime classpath.
- **Measured.** A second invocation of an identical command printed "Reusing configuration cache" and "Configuration cache entry reused". The probes ran with the configuration cache enabled by a user-level Gradle property; the repository's own `gradle.properties` contains only `kotlin.code.style=official`.
- **Not needed:** `-P`. Nothing in the design reads a project property. The level names, the Java floor and the plugin versions are constants in `build-logic`.
- **Observed once, not reproduced.** One long-lived shared daemon (which had loaded KGP 2.2.0, 2.2.21, 2.3.21 and 2.4.21 in probes) printed "The Daemon will expire after the build after running out of JVM Metaspace". A fresh single-use daemon building all modules did not.

## How comparable projects do it

- **GeneralTools** (this organisation; `build.gradle.kts` in its checkout): one KMP module, KGP 2.4.21, `jvmToolchain(17)`, an explicit `TargetJvmVersion` attribute, `explicitApi()`, `languageVersion = apiVersion = KOTLIN_2_2`, `coreLibrariesVersion` handling, `configure(KotlinMultiplatform(...))`, kotlin-logging 8.0.4 in `commonMain`, `-PtestLevel` package filter on every `AbstractTestTask`, and `js` + `wasmJs` targets. Its second targets are how it enforces common code. Its `kmp-test-levels-and-names.md` rejects per-level `testRuns.create`, which adds every run to `check`.
- **MyGameTools** (per GeneralTools' research): `build-logic` convention plugin with `registerLevelTest(name, levelPackage)` over plain `Test` tasks, `filter { includeTestsMatching(...); isFailOnNoMatchingTests = false }`. This is the shape used here.
- **SpartanGraphics** (documents only; no build files yet): precompiled script plugins per kind of module, `pluginManagement { includeBuild("build-logic") }`, `group` and `version` in the root `gradle.properties`, package-filter test levels, and KMP fakes as a separate module.
- **BotTools** (consumer; its research): `build-logic`, KGP 2.2.x, nested composite; the probe above reproduces that shape.
- **Okio, Ktor, kotlinx-datetime and others** build with a newer KGP and pin the language and API floor (GeneralTools' `kmp-build-and-kotlin-compat.md` F6). That is the fallback in F1.

## Pitfalls & regrets

1. **A single `jvm()` target silently accepts JVM APIs in `commonMain`** (documented, measured). Nothing in the build or the compiler notices until a second target is added.
2. **`./gradlew test` skips KMP modules without error** (measured). Add the lifecycle `test` alias.
3. **`isFailOnNoMatchingTests` defaults to true** (measured); an empty level fails the build. The helper sets it to false, so a test whose package does not match any `testing.<level>` prefix never runs in a level task (it still runs in `test`/`jvmTest`).
4. **The root artifact of a KMP module is empty for Maven** (measured). Document `-jvm` for Maven users.
5. **The KMP source-set DSL has no `testFixtures()`** (measured). Use the top-level `dependencies { "jvmTestImplementation"(testFixtures(...)) }`.
6. **`archives` deprecation on `jvm()`** (measured, KT-78620): fails on Gradle 10 with KGP 2.2.x.
7. **Kotlin plugin version in a subproject `plugins {}` block plus a convention plugin** loads KGP twice and warns (measured). Keep versions only in `build-logic/build.gradle.kts`.
8. **`expect`/`actual` classes warn as Beta on 2.2.0** (measured); `expect fun` does not.
9. **Disabling a target's publish task leaves a dangling root `.module`** (measured).
10. **`onlyIf { publication... }` reading task state at execution time fails under the configuration cache** (measured); resolve names at configuration time.
11. **Passed on to the API-shape work (measured, outside this question).** A public interface function `suspend fun execute(request): Result<Response>` is compiled with a hash-mangled JVM name (`execute-gIAlu-s`). That is fine for Kotlin callers, but a signature change renames the method and the ABI dump records the mangled name.
12. **Node 22.0.0 and Yarn 1.22.17** are what KGP 2.2.0 downloads for a js target (measured); `kotlin-js-store/yarn.lock` appears at the repository root and is meant to be committed.

## Recommendation for this repository

**Kotlin and Gradle.** Keep KGP 2.2.0 (both `kotlin("jvm")` and `kotlin("multiplatform")`), Gradle 9.7.1 and vanniktech 0.36.0. Pin them once, in `build-logic/build.gradle.kts`. Accept the `archives` deprecation until Gradle 10; the fallback is KGP 2.3.21 or later with the two pins in F1. Rejecting a newer KGP now: it changes the compiler for a deprecation that does not fail on 9.7.1, and the only documented-for-9.7 KGP (2.4.20 and later) would need the language, API and stdlib pins plus non-JVM-target caveats.

**Topology (topology L).**

- `settings.gradle.kts` adds `pluginManagement { includeBuild("build-logic") }` and includes `:webtools-http` and the feeds module beside the three existing ones (keeping the foojay plugin).
- The root `build.gradle.kts` keeps `allprojects { group; version }` and loses `plugins`, `subprojects {}` and the level and publishing blocks.
- `build-logic` holds three precompiled script plugins and one helper:
  - `webtools.published-library` applies vanniktech and sets `repositories`, `publishToMavenCentral()`, `signAllPublications()` and the shared `pom {}` block (today `build.gradle.kts:64`–`:91`). Per-module `coordinates(...)` and `pom { name; description }` stay in each module.
  - `webtools.kotlin-jvm-library` applies `org.jetbrains.kotlin.jvm` and the published-library plugin, sets `jvmTarget` and `sourceCompatibility`/`targetCompatibility` from `WebToolsBuild.JAVA_FLOOR`, the `kotlin("test")` dependencies and logback for tests, `useJUnitPlatform()`, and the level tasks.
  - `webtools.kotlin-multiplatform-library` applies `org.jetbrains.kotlin.multiplatform` and the published-library plugin, sets `jvm { compilerOptions { jvmTarget }; attributes { TargetJvmVersion } }` from the same constant, `useJUnitPlatform()`, the level tasks over `compilations["test"]`, and the `test` alias. It calls `configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))` explicitly.
  - `WebToolsBuild` holds `JAVA_FLOOR` (11), the level list, and `registerLevelTasks(project, classesDirs, classpath)`.
- Each module's build file starts with `plugins { id("webtools.kotlin-jvm-library") }` or `id("webtools.kotlin-multiplatform-library")`.
- **Per-module, not in the plugins:** `explicitApi()` in the two new modules' `kotlin {}` block (it must apply to a possibly JVM-only feeds module and not to a legacy module, so it cannot hang off the kind of plugin), and `api(slf4j-api:2.0.13)` in the three existing modules' build files (restoring their published shape). The new modules declare `implementation` only.
- The module-specific wiring `webtools-http` needs (fixtures compilation and variants from the test-fixtures research) reads `WebToolsBuild.JAVA_FLOOR` for its `TargetJvmVersion`.
- Rejecting topology S: it also works, but it keeps cross-project configuration that Gradle documents as improper, needs string-typed `extensions.configure` and per-module Kotlin plugin lines anyway, and, by Gradle's own description, couples projects at configuration time. It remains the smallest diff if `build-logic` is unwanted.

**Level tasks.** Register the seven `Test` tasks from `WebToolsBuild.registerLevelTasks` in both plugins, select by `com.spartanlabs.testing.<level>.*` with `isFailOnNoMatchingTests = false`, and move the existing three modules to the same filter (measured identical selection). The `@Tag` annotations become unnecessary and can stay or be removed later; update the README sentence "JUnit-tags them" (`README.md:482`) and the note about `./gradlew test` for KMP modules.

**Publishing.** vanniktech 0.36.0, the KMP publication as in F5; the existing modules unchanged apart from `org.gradle.jvm.version: 11`. Update the README Install section with the `-jvm` coordinate for Maven users.

**Logging.** Option A: no logging library in `commonMain`, `slf4j-api` as `implementation` in `jvmMain` for the OkHttp and Caffeine defaults. If common code later needs to log, use `internal expect fun` (Option C). Do not adopt kotlin-logging 8.0.4: the cost lands on every consumer of the library and buys nothing while there is one target.

**Targets, and the feeds module** are open decisions; see Considerations. Until they are settled, the plugin declares `jvm()` only and the feeds module can start as either shape without rework of the topology above.

## Considerations

These need the user's call; unresolved Considerations block execution.

### 1. What guarantees that the ports are common code

- **Options.**
  - **(A) `jvm()` only, with no check.** Cost: nothing. Risk: any JVM type in a port signature or `commonMain` default goes undetected until a second target is added, which defeats the point of placing the ports in `commonMain`.
  - **(B) `jvm()` plus a published `js { nodejs() }`.** `compileCommonMainKotlinMetadata` and `compileKotlinJs` enforce it on every `build`; the project also publishes `webtools-http-js` (a klib that needs a Kotlin 2.2+ consumer) and takes on Node 22.0.0/Yarn downloads and `kotlin-js-store/yarn.lock` if its tests run (`testTask { enabled = false }` avoids them). `commonMain` dependencies must stay multiplatform. The fixtures stay JVM-only.
  - **(C) `jvm()` only in the published module, plus an unpublished `:webtools-http-commoncheck` module** that compiles the same `commonMain` directory for js. No new coordinate. Costs one extra Gradle project, a duplicated dependency list, no `commonTest` coverage; a build-logic helper can share the dependency list.
  - **(D) `wasmJs`** instead of js: compile-only works on 2.2.0, but wasmJs is Beta only from KGP 2.2.20 and needs the experimental-DSL opt-in. **Native:** a Kotlin/Native toolchain download, and a publication set that depends on the build host (not probed).
  - Rejected: disabling a target's publication (dangling `.module`); a `-no-jdk` compilation (false positives).
- **Recommendation: (C)** if the project does not want to commit to a JS artifact; **(B)** if it does. (C) keeps the published surface to what the requirements name and still turns the "ports are common code" intent into a compile error.

### 2. Is the feeds module multiplatform or JVM-only

- **JVM-only.** The existing convention plugin; one coordinate; Rome used directly; `testFixtures(project(":webtools-http"))` in `testImplementation`; its parser port stays usable only from the JVM.
- **KMP (`jvm()` only, or with the targets chosen in Consideration 1).** The port and feed model in `commonMain`, Rome in `jvmMain`; two coordinates (`webtools-feeds` and `webtools-feeds-jvm`); tests that use the fakes live in `jvmTest`; the module is ready for a non-JVM parser later.
- **What does not differ:** `api(project(":webtools-http"))` works for a JVM consumer and in the composite; both shapes substitute and select the JVM variant; converting JVM-only to KMP later is invisible to Gradle consumers (the root module redirects to `-jvm`) but changes the Maven coordinate to `-jvm`.
- **Recommendation:** JVM-only, unless a non-JVM target is planned for the feeds port. It keeps one coordinate and the existing plugin, and a later conversion costs Gradle consumers nothing.

## Known gaps

- **Native targets were not probed.** No Kotlin/Native toolchain is installed on this machine (it would download outside the Gradle caches). Native cost is from documentation only.
- **IDE behaviour is untested.** The documentation says single-target `commonMain` can access target-specific symbols; I did not open the project in IntelliJ to see whether the editor flags them.
- **Central was not exercised**, and signing was omitted from every probe. Whether Central's validator accepts the root jar of a KMP module (metadata only) beside a `-test-fixtures.jar` is covered in the test-fixtures findings.
- **Isolated Projects** was not run. Topology L has no cross-project configuration, but the statement that topology S couples projects at configuration time is from Gradle's description of cross-project configuration, not from a run.
- **Which level tasks were run.** `gating`, `component` and `deterministic` were run for all JVM modules; `integration`, `e2e`, `nonfunctional` and `uat` for the UDP module were not (they bind a fixed UDP port). The new level helper's behaviour on those is the same code path.
- **The probe compiled the three JVM modules at `jvmTarget = 11` on a JDK 23 launcher without `-Xjdk-release`.** It compiled and the classes are major 55, but that does not prove no JDK API newer than 11 is used; that is the Java 11 floor research.
- **vanniktech 0.37.0 was not used in this topology**, and 0.36.0 with KGP 2.3.21 or 2.4.21 is outside what 0.36.0 documents as tested (it worked in the probe).
- **The `test-fixtures` variant was exercised in-build and through the composite** with the shape from the test-fixtures research (recipe B), on KGP 2.2.0 only. Its publication through the new `build-logic` plugin was not re-verified for the Central-bound artifact set beyond the file repository.
- **The daemon Metaspace observation** is one occurrence in a shared daemon; I did not establish whether a default-sized daemon is adequate for the full build in a CI where KGP is loaded by both a consumer and WebTools.
- **Gradle versions below 9.7.1.** A consumer whose Gradle embeds an older Kotlin compiles WebTools' `build-logic` with that compiler (SpartanGraphics' plan flags this as the largest unverified composite risk for a Gradle 9.0.0 wrapper). BotTools is on 9.7.1, so it is not exercised here.
- **Windows only.** A Linux CI was not exercised.

## Load-bearing claims

1. **No documented KGP row covers Gradle 9.7.1; all of KGP 2.2.x is documented only to Gradle 8.14.** `| 2.2.20–2.2.21 | 7.6.3–8.14 | 7.3.1–8.11.1 |` and `| 2.2.0–2.2.10  | 7.6.3–8.14 | 7.3.1–8.10.0 |`; `| 2.3.20–2.3.21 | 7.6.3–9.3.0 | 8.2.2–9.0.0 |`; `| 2.4.20–2.4.21 | %minGradleVersion%–%maxGradleVersion% |` with `<var name="maxGradleVersion" value="9.7.0" type="string"/>`; "You can also use Gradle and AGP versions up to the latest releases, but if you do, keep in mind that you might encounter deprecation warnings or some new features might not work." <https://kotlinlang.org/docs/gradle-configure-project.html> (sources `docs/topics/gradle/gradle-configure-project.md` and `docs/v.list`, `JetBrains/kotlin-web-site`), read 2026-10-20.
2. **With a single target, platform symbols are accessible from common code; a second target removes them.** "If your project only has a single target (for example, JVM), you can access target-specific symbols with appropriate visibility from common code. However, as soon as you add a second target, target-specific symbols become unavailable in common code." <https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html>, read 2026-10-20.
3. **The `archives` deprecation on KGP 2.2.x is KT-78620, fixed in the 2.3 line.** "Remove adding artifacts to the deprecated Gradle "archives" configuration"; State Fixed; Planned for 2.3.20-Beta1; Affected versions 2.2.20-Beta1. <https://youtrack.jetbrains.com/issue/KT-78620>, fetched 2026-10-20.
4. **A target cannot be marked unpublishable through the public API.** In `KotlinTarget`: "Indicates whether the Kotlin target is publishable." `val publishable: Boolean`; in `AbstractKotlinTarget`: `override val publishable: Boolean` / `get() = true`. <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinTarget.kt> and <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/AbstractKotlinTarget.kt>, tag v2.2.0.
5. **The root KMP publication references all target publications, and publications follow the build host.** "The root publication serves as an entry point that references all target-specific publications." and "When used with `maven-publish`, the Kotlin plugin automatically creates publications for each target that can be built on the current host" and "Maven Central explicitly forbids duplicate publications and fails the process if they are created." <https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html>, read 2026-10-20.
6. **Gradle's guidance on sharing build logic.** "An improper way to share build logic between subprojects is *cross-project configuration*"; "In addition to `buildSrc`, another powerful way to share build logic across subprojects is by using a dedicated composite build"; "Changes to code in buildSrc will invalidate the configuration phase and require re-execution of all tasks". <https://docs.gradle.org/9.7.1/userguide/sharing_build_logic_between_subprojects.html>, Gradle 9.7.1.
7. **vanniktech auto-detects the Kotlin plugin and takes `configure(KotlinMultiplatform(...))` for KMP.** "By default, the plugin will automatically detect other applied plugins like the Android Gradle plugin or Kotlin Gradle plugin and set up what to publish automatically." <https://vanniktech.github.io/gradle-maven-publish-plugin/other/>; `configure(KotlinMultiplatform(` with `javadocJar = JavadocJar.Empty(),` and `sourcesJar = SourcesJar.Sources(),` and "sources publishing is always enabled by the Kotlin Multiplatform plugin" <https://vanniktech.github.io/gradle-maven-publish-plugin/what/>, read 2026-10-20.
8. **vanniktech 0.36.0 minimums.** Minimum supported "Gradle 9.0.0" and "Kotlin Gradle Plugin 2.2.0"; compatibility tested up to "Kotlin Gradle Plugin 2.3.0" and "Kotlin Gradle Plugin 2.3.20-Beta1". <https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/CHANGELOG.md>, tag 0.36.0.
9. **kotlin-logging 8.0.4 prints a startup line and reads a JVM property to suppress it.** `println("kotlin-logging: initializing... active logger factory: ${factory::class.simpleName}")` inside `if (KotlinLoggingConfiguration.logStartupMessage)` in <https://github.com/oshai/kotlin-logging/blob/8.0.4/src/commonMain/kotlin/io/github/oshai/kotlinlogging/KotlinLogging.kt>; `System.getProperty("kotlin-logging.logStartupMessage")?.toBoolean()` / `System.getenv("KOTLIN_LOGGING_STARTUP_MESSAGE")?.toBoolean()` / `?: true` in <https://github.com/oshai/kotlin-logging/blob/8.0.4/src/jvmMain/kotlin/io/github/oshai/kotlinlogging/KotlinLoggingConfiguration.kt>, tag 8.0.4.
10. **Language version and API version rules (fallback pins).** "On the JVM, consumers can use any compiler version from the previous language version onwards." and "Your consumers also need to provide a Kotlin standard library version that is at least as new as your library's configured API version." <https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html>, read 2026-10-20.
11. **Gradle's `isFailOnNoMatchingTests` default (used by the level helper).** "Returns whether the task should fail if no matching tests where found. The default is true." <https://docs.gradle.org/9.7.1/javadoc/org/gradle/api/tasks/testing/TestFilter.html>, Gradle 9.7.1; the behaviour was also measured.
