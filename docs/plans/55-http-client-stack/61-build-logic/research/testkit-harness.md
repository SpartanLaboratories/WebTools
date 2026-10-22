# Level-3 Gradle TestKit harness for the `build-logic` convention plugins

## 1. Question

How should Level-3 Gradle TestKit tests exercise the `build-logic` convention plugin `webtools.kotlin-multiplatform-library` (Kotlin Multiplatform 2.2.0, binary-compatibility-validator 0.18.2, vanniktech `maven-publish` 0.36.0), and how is the dedicated task that runs them declared and invoked?

An answer has to hold on this toolchain and for these facts:

- Gradle 9.7.1 on a Windows host with Oracle JDK 23.
- `build-logic` is a `kotlin-dsl` build reached through `pluginManagement { includeBuild("build-logic") }`. Its precompiled script plugins are in the default package, and the three plugin-marker dependencies are `implementation` with literal versions.
- The tests live in `build-logic/src/test/kotlin/…/testing/integration/` (packages `com.spartanlabs.testing.integration.…`), JUnit 5, one class per file.
- The root build applies no plugin, so it has no `check` or `build` task.
- `./gradlew build` must not run the tests.
- Everything stays configuration-cache compatible. The maintainer's user-level `gradle.properties` turns the cache on for every build.
- The same harness later hosts a publication check (publish to a file repository, resolve `testFixtures(...)` from it and through `includeBuild`) that is wired into `check`.

Nothing in this report was executed. Gradle, TestKit and library behaviour is established from the Gradle 9.7.1 documentation and source at tag `v9.7.1`, library source at pinned tags, and the repository's own measured research. Each finding states its tier. The first harness run should be a smoke test that confirms the points marked *derived* (see Known gaps).

## 2. Bottom line

- **Inject the plugins with `GradleRunner.withPluginClasspath()`; do not include `build-logic` from the generated project, and do not drive the real repository build.**
  - `kotlin-dsl` applies `java-gradle-plugin` (source). That plugin adds `gradleTestKit()` and `gradleApi()` to the `test` source set and generates `plugin-under-test-metadata.properties` from `main` output plus its runtime classpath. The metadata therefore carries the precompiled-plugin descriptors and the Kotlin Gradle plugin, binary-compatibility-validator and vanniktech jars, because all three are `implementation` dependencies.
  - Gradle's own "test your build logic" page uses exactly this layout: `pluginManagement { includeBuild("build-logic") }` in the settings, a TestKit suite inside `build-logic`, `withPluginClasspath()` in the tests.
  - Including the live `build-logic` directory from the generated project makes a nested build compile and write inside the directory the outer build is executing in. Gradle documents that builds sharing an included build "may conflict when run in parallel".
  - The real build has no multiplatform module during this work.
- **The generated project is a small settings-plus-build-script pair that applies the convention plugin by id and nothing else.**
  - It declares no Kotlin plugin of its own.
  - TestKit ignores the user-level `gradle.properties`, so the configuration cache, `kotlin.compiler.execution.strategy=in-process` and `org.gradle.vfs.watch=false` must be written into the generated project's `gradle.properties`.
  - One `commonTest` class in a `…testing.component` package and one `jvmTest` class in a `…testing.integration` package prove package selection. The assertions read `build/test-results/<task>/TEST-<class>.xml`, not console output.
- **Published attribute: run `generateMetadataFileForJvmPublication` and `generateMetadataFileForKotlinMultiplatformPublication`.**
  - They write `build/publications/jvm/module.json` and `build/publications/kotlinMultiplatform/module.json`.
  - The variants to read are `jvmApiElements-published` and `jvmRuntimeElements-published`, attribute `org.gradle.jvm.version`.
  - No signing is involved.
  - For the later file-repository publish, `signAllPublications()` makes signing required unless the version ends in `-SNAPSHOT`. The generated build script can disable the `Sign` tasks instead (derived from source; see Consideration 1).
- **The dedicated task is a plain `Test` task over the `test` source set** (MyGameTools' shape), with the package filter `com.spartanlabs.testing.integration.*`, and `test` excludes that package.
  - A `jvm-test-suite` cannot split one source set into two tasks, and a second suite would need its own source directory.
  - Invoke it from the repository root as `./gradlew :build-logic:integrationTest`. Gradle documents this shape for a `pluginManagement`-included `build-logic`. `-p build-logic` is the fallback.
  - The main build can depend on it with `gradle.includedBuild("build-logic").task(":integrationTest")`.
  - A top-level `includeBuild("build-logic")` is not needed for either.
- **Windows:** TestKit disables file-system watching there. JUnit 5.11's `@TempDir` does not wait or retry on a locked file; it fails the test.
  - Mitigate with the in-process Kotlin compiler and a harness-owned project directory under `build-logic/build/` with best-effort cleanup.
  - The nested build uses its own Gradle user home, so the first run downloads the Kotlin compiler and test dependencies. Give the task a long timeout.

## 3. Findings

Tier key: **Documented** (official documentation), **Source** (Gradle or library source at a pinned tag), **Common practice** (what mature projects visibly do), **Derived** (my inference from source, not executed), **Single source** (one party's statement).

### F1. `kotlin-dsl` on Gradle 9.7.1 brings `java-gradle-plugin`, the plugin-under-test metadata and `gradleTestKit()` by itself (Source; Gradle 9.7.1)

- `KotlinDslPlugin.apply` calls `apply<JavaGradlePluginPlugin>()`, then `KotlinDslBasePlugin` and `PrecompiledScriptPlugins`. [`KotlinDslPlugin.kt`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/core-configuration/kotlin-dsl-plugins/src/main/kotlin/org/gradle/kotlin/dsl/plugins/dsl/KotlinDslPlugin.kt)
- `JavaGradlePluginPlugin` registers `pluginUnderTestMetadata` and, in an `afterEvaluate` action over `extension.getTestSourceSets()`, adds `gradleTestKit()` and `gradleApi()` to the test `implementation` and the metadata task's output to `runtimeOnly`. The default test source set is the default `test` suite. [`JavaGradlePluginPlugin.java`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/plugin-development/src/main/java/org/gradle/plugin/devel/plugins/JavaGradlePluginPlugin.java)
- The same action configures every `Test` task in the project: it adds the metadata files and the Gradle home as inputs, and adds `GradleJvmCommandLineArgumentProvider`, which supplies the JPMS `--add-opens` arguments the daemon-side classes need on a modern JDK. A hand-registered `Test` task therefore needs no extra wiring, as long as its `classpath` is the `test` source set's `runtimeClasspath`.
- `kotlin-dsl` itself adds `kotlin-stdlib`, `kotlin-reflect` and `gradleKotlinDsl()` to `testImplementation`, but not JUnit and not `kotlin-test`. [`kotlin_dsl.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/dsl-apis/kotlin_dsl.adoc)
- The explicit `testImplementation(gradleTestKit())` in MyGameTools' `build-logic/build.gradle.kts` is redundant but harmless.
- Precompiled script plugins are exposed as Gradle plugins when `JavaGradlePluginPlugin` is present: `DefaultPrecompiledScriptPluginsSupport.enableOn` calls `exposeScriptsAsGradlePlugins` inside `plugins.withType<JavaGradlePluginPlugin>`. The generated descriptors (`META-INF/gradle-plugins/<id>.properties`) go into `main` resources, which the metadata classpath includes. [`DefaultPrecompiledScriptPluginsSupport.kt`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/core-configuration/kotlin-dsl-provider-plugins/src/main/kotlin/org/gradle/kotlin/dsl/provider/plugins/precompiled/DefaultPrecompiledScriptPluginsSupport.kt)
- Gradle 9 requires explicit test-framework dependencies for the plain `test` source set: "If using JUnit 5, an explicit `runtimeOnly` dependency on `junit-platform-launcher` is required". [`upgrading_version_8.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_version_8.adoc) The built-in `test` suite can instead be configured with `useJUnitJupiter("<version>")`, which manages them. [`jvm_test_suite_plugin.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/platforms/jvm/jvm_test_suite_plugin.adoc)

### F2. What `withPluginClasspath()` injects (Source; Gradle 9.7.1)

- `withPluginClasspath()` reads `plugin-under-test-metadata.properties` from the test runtime classpath and takes the `implementation-classpath` key, a path-separator-joined string. [`GradleRunner.java`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/test-kit/src/main/java/org/gradle/testkit/runner/GradleRunner.java), [`DefaultGradleRunner.java`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/test-kit/src/main/java/org/gradle/testkit/runner/internal/DefaultGradleRunner.java)
- `PluginUnderTestMetadata` writes that list. [`PluginUnderTestMetadata.java`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/plugin-development/src/main/java/org/gradle/plugin/devel/tasks/PluginUnderTestMetadata.java)
  - Its contents are `main` output plus the whole `main` runtime classpath, with the Gradle API and Groovy filtered out (`classpathForPlugin`/`excludeGradleApi` in `JavaGradlePluginPlugin`).
  - So the Kotlin Gradle plugin, binary-compatibility-validator, vanniktech and their transitive jars are all on it. `compileOnly` dependencies are not.
- In the build under test, `DefaultInjectedClasspathPluginResolver` builds one class-loader scope (`parentScope.createChild("injected-plugin", null)`) from that classpath, looks plugins up by id in it, and adds the plugin's loader to the requesting script's scope (`visitor.visitClassLoader(plugin.asClass().getClassLoader())`). [`DefaultInjectedClasspathPluginResolver.java`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/plugin-use/src/main/java/org/gradle/plugin/use/resolve/service/internal/DefaultInjectedClasspathPluginResolver.java)
- The parent scope is the build's core-and-plugins scope, and the resolver is a build-scoped service (`PluginUseServices.createInjectedClassPathPluginResolver`). [`PluginUseServices.java`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/plugin-use/src/main/java/org/gradle/plugin/internal/PluginUseServices.java)
- A precompiled script plugin's `plugins { id("x") }` block is executed as `pluginManager.apply(id)` (not as a repository-resolved request). [`PrecompiledScriptTemplates.kt`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/core-configuration/kotlin-dsl/src/main/kotlin/org/gradle/kotlin/dsl/precompile/v1/PrecompiledScriptTemplates.kt)
  - The convention plugin's own `org.jetbrains.kotlin.multiplatform` request is therefore satisfied from the same loader scope that now contains the Kotlin Gradle plugin jars, exactly as when the plugin comes through the plugin build.
  - Gradle's documentation says the same about external plugins in precompiled scripts: they must be on the plugin project's implementation classpath, and the version is the one declared there. [`implementing_gradle_plugins_precompiled.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/plugin-development/implementing_gradle_plugins_precompiled.adoc)
- Documented limits: injection only works when the plugin under test is applied through the `plugins {}` DSL, and it is incompatible with plugins that depend on another plugin's API through `compileOnly`. [`test_kit.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/plugin-development/test_kit.adoc) Neither limit applies: the generated build applies `id("webtools.kotlin-multiplatform-library")` in `plugins {}`, and the three plugin dependencies are `implementation`.

### F3. Kotlin Gradle plugin, binary-compatibility-validator and vanniktech under an injected classpath (Common practice for two of three, Derived for vanniktech)

- binary-compatibility-validator's own functional tests run a multiplatform `jvm()` project with `withPluginClasspath()` and `--configuration-cache`. The Kotlin Gradle plugin is a `compileOnly` dependency of the validator, so its build adds it to the injected classpath through a `plugin-classpath.txt` manifest. [`build.gradle.kts`](https://github.com/Kotlin/binary-compatibility-validator/blob/0.18.2/build.gradle.kts), [`TestDsl.kt`](https://github.com/Kotlin/binary-compatibility-validator/blob/0.18.2/src/functionalTest/kotlin/kotlinx/validation/api/TestDsl.kt), [`MultiPlatformSingleJvmTargetTest.kt`](https://github.com/Kotlin/binary-compatibility-validator/blob/0.18.2/src/functionalTest/kotlin/kotlinx/validation/test/MultiPlatformSingleJvmTargetTest.kt) (tag 0.18.2).
  - Its build comment states the reason: injected plugins are isolated, so "optional dependencies (dependent on applied plugins - for example kotlin multiplatform) are not visible".
  - That hazard applies to a validator plugin tested in isolation. Here the Kotlin Gradle plugin is an `implementation` dependency of `build-logic`, so it is already in the injected loader.
- The Kotlin Gradle plugin detects a split class path and raises `IsolatedKotlinClasspathClassCastException` with the comment "User setup lead to Gradle separating the classpath between two projects applying the Kotlin Gradle plugin. This is not a supported setup". [`reflectionUtils.kt`](https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/utils/reflectionUtils.kt) (tag v2.2.0)
  - The condition is two loaders for the Kotlin plugin inside one build. That is what a generated build causes if it declares `kotlin("multiplatform") version "…"` itself on top of the injected one.
  - A Gradle-forum report of a convention plugin that configures the Kotlin plugin failing under `withPluginClasspath` has exactly that shape: the generated build also declared the Kotlin JVM plugin. (Single source, no confirmed fix: <https://discuss.gradle.org/t/testkit-load-plugins-other-than-plugin-under-test/51534>, Aug–Sep 2025.)
  - The repository's own measured research found the matching warning "The Kotlin Gradle plugin was loaded multiple times in different subprojects" when a plugin is declared with a version in a subproject (`kmp-build-topology.md`, F2). The generated project must apply no Kotlin plugin directly.
- vanniktech 0.36.0 reacts to `org.jetbrains.kotlin.multiplatform` through `project.plugins.withId(...)` and `afterEvaluate` inside `MavenPublishBaseExtension`/`MavenPublishPlugin`. Its own integration suite does not use injection: it publishes the plugin to `mavenLocal` and applies it by version. [`ProjectSpecRunner.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/plugin/src/integrationTest/kotlin/com/vanniktech/maven/publish/util/ProjectSpecRunner.kt), [`plugin/build.gradle.kts`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/plugin/build.gradle.kts)
  - I found no independent evidence of vanniktech running under `withPluginClasspath`.
  - By mechanism it sees the same loader as the Kotlin plugin, exactly as in the plugin-build case that the repository's research measured. Treat it as *derived* until the first smoke test passes.

### F4. The alternatives and their hazards (Documented and Source)

- **Generated project includes `build-logic` through `pluginManagement { includeBuild(<absolute path>) }`.**
  - It has the highest fidelity: it is how modules consume the plugins.
  - Cost 1: the nested build compiles `build-logic` itself (precompiled-script accessor generation plus Kotlin compile) in a cold Gradle user home on every fresh TestKit directory, and resolves the plugin markers from the repositories in `build-logic/settings.gradle.kts`.
  - Cost 2: it works in the same `build-logic/build/` and `build-logic/.gradle/` directories that the outer build executing the tests is using.
    - Gradle's composite-build documentation lists "Multiple composite builds may conflict when run in parallel if more than one includes the same build. Gradle does not share the project lock of a shared composite build between Gradle invocations to prevent concurrent execution." [`composite_builds.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/structuring-builds/composite_builds.adoc)
    - Copying `build-logic` into the generated project avoids the clash but rebuilds it per project.
  - Use it only for a check that needs the real `pluginManagement` mechanism (see F13).
- **Driving the real repository build** (MyGameTools' `UmbrellaArtifactAggregationTest`: `withProjectDir(repoRoot)`).
  - There is no multiplatform module to drive during this work, so the plugin under test would not be applied at all.
  - It runs a nested build in the directory the outer build is using, with the same shared-lock hazard.
  - It writes build outputs into the working tree.
- **Publish the plugin to a file repository and apply it by version** (Dokka v2.2.0, vanniktech 0.36.0).
  - Maximum fidelity and the only route Gradle documents for `compileOnly` plugin dependencies.
  - It needs plugin-publication metadata for precompiled scripts and a publish step before every run. It is not needed while the dependencies are `implementation`.
- **Injection (chosen).**
  - It costs no nested build of `build-logic`.
  - Documented and used by Gradle's own build-logic testing page.
  - What it does not exercise: the settings-level `pluginManagement { includeBuild }` mechanics. The real modules exercise those on every build.

### F5. The generated project (Source and Documented)

- **TestKit ignores user-level configuration.** "Configuration from the default Gradle User Home (for example, `~/.gradle/gradle.properties`) is **not** used." The test build's Gradle user home is the TestKit directory (F8). Consequences:
  - The configuration cache, which the maintainer's machine enables for every build, is **off** in nested builds unless the project's `gradle.properties` or a `--configuration-cache` argument turns it on.
  - Signing and Central credentials in the user-level file are never visible to nested builds.
- **Repositories.** The Kotlin plugin resolves its compiler (`kotlin-compiler-embeddable` and friends) and `kotlin-test`, JUnit and logback from the project's repositories.
  - The default settings mode is `PREFER_PROJECT`: project-declared repositories win over settings-declared ones. [`RepositoriesMode.java`](https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core-api/src/main/java/org/gradle/api/initialization/resolve/RepositoriesMode.java)
  - A `dependencyResolutionManagement { repositories { mavenCentral() } }` in the generated settings is therefore harmless when the convention plugin also adds `mavenCentral()`, and it is needed if it does not.
  - The injected plugins need no plugin repositories.
- **`gradle.properties` content.**
  - `kotlin.compiler.execution.strategy=in-process` keeps the Kotlin compiler in the nested Gradle daemon: no separate Kotlin daemon survives the test. The Kotlin documentation describes it as "the simplest strategy from the perspective of memory management". vanniktech's own integration suite writes this property into every generated project. [`compiler-execution-strategy`](https://kotlinlang.org/docs/compiler-execution-strategy.html)
  - `org.gradle.configuration-cache=true`.
  - `org.gradle.vfs.watch=false`. TestKit already adds this on Windows; vanniktech writes it for all hosts.
  - An `org.gradle.jvmargs` value is likely needed, because the compiler now runs in the daemon (see Known gaps).
- **Layout of the test sources** (one `commonTest` and one `jvmTest` class in different level packages).

  | Path (in the generated project) | Content |
  |---|---|
  | `src/commonMain/kotlin/sample/Greeter.kt` | one public class, so `apiDump` has something to record |
  | `src/commonTest/kotlin/com/spartanlabs/testing/component/CommonSourceSetTest.kt` | a `kotlin.test.Test` class |
  | `src/jvmTest/kotlin/com/spartanlabs/testing/integration/JvmSourceSetTest.kt` | a `kotlin.test.Test` class |
  | `src/jvmTest/kotlin/sample/UnleveledTest.kt` | optional, proves a class outside every level package runs in `jvmTest` and in no level task |

  - `kotlin("test")` in `commonTest` and the JUnit 5 binding in `jvmTest` come from the plugin, not from the generated build.
- **Assertions on the result.**
  - `GradleRunner.build()` throws on an unexpected failure with the full build output in the message; `buildAndFail()` returns the `BuildResult` for expected failures; `run()` returns it whatever the outcome ("Executes a build, without expecting a particular outcome").
  - Expected failures are therefore already values. No `Result` wrapper is needed.
  - Task outcomes: `result.task(":jvmTest")?.outcome`.
  - Test selection: list `build/test-results/<levelTask>/TEST-*.xml`. A level task whose package matches nothing may end `SUCCESS` or `NO_SOURCE`, so the outcome is not the evidence.
  - The dump: `api/<rootProject.name>.api` for one JVM target (binary-compatibility-validator's own multiplatform single-JVM test reads `rootProjectDir.resolve("$API_DIR/${rootProjectDir.name}.api")`). Assert it contains `sample/Greeter`.
  - `apiCheck` in `check`: `check --dry-run` and look for `:apiCheck` in the output. A dry run records no outcome events, so use the output text. Pair it with a behavioural assertion: `apiDump`, then `apiCheck` succeeds; add a public member, then `apiCheck` ends `FAILED`.
  - `test` runs `jvmTest`: assert `:jvmTest` is `SUCCESS` for `test`. Do not assert the lifecycle task's own outcome.
  - Do not assert on a clean console. The Kotlin plugin 2.2.0 prints an `archives` deprecation on `jvm()` (repository research, F1), so never run with `--warning-mode=fail`.

### F6. Gradle Module Metadata without publishing or signing (Source and Documented)

- `MavenPublishPlugin` registers `generateMetadataFileFor<Publication>Publication` per publication, with output `build/publications/<publication name>/module.json`. [`MavenPublishPlugin.java`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/software/maven/src/main/java/org/gradle/api/publish/maven/plugins/MavenPublishPlugin.java) The POM equivalent is `generatePomFileFor<Publication>Publication` to `pom-default.xml`.
- The Kotlin plugin creates the root publication named `kotlinMultiplatform` and one publication per target named after the target component (`jvm`). [`Publishing.kt`](https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/publishing/Publishing.kt) (v2.2.0) Variant names are the target's usage-context name plus `-published`. [`KotlinTargetSoftwareComponentImpl.kt`](https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/KotlinTargetSoftwareComponentImpl.kt)
- The two tasks to run:

  | Task | Output | Variants to read |
  |---|---|---|
  | `generateMetadataFileForJvmPublication` | `build/publications/jvm/module.json` | `jvmApiElements-published`, `jvmRuntimeElements-published` (and `jvmSourcesElements-published`, which carries no JVM version) |
  | `generateMetadataFileForKotlinMultiplatformPublication` | `build/publications/kotlinMultiplatform/module.json` | `metadataApiElements`, plus `jvmApiElements-published` and `jvmRuntimeElements-published` as `available-at` redirects |

  The repository's measured research (`kmp-build-topology.md` F2 and F5; `java-11-floor.md` F1) observed both the root and the `-jvm` module metadata carrying `org.gradle.jvm.version` on the `jvm*Elements-published` variants when the attribute is set on the target, and neither carrying it when it is not. The assertion reads `variants[].attributes["org.gradle.jvm.version"]` for the two `jvm*Elements-published` variants and compares it with the major version of the JVM running the nested build. TestKit starts that daemon on the JVM running the tests (F8).
- The metadata tasks depend on the artifact tasks (jar, sources jar, empty javadoc jar, metadata jar) because the file lists include size and checksums. They run no `Sign` task. They still need the repository settings of F5 whenever a compilation has sources.
- Reading the JSON needs a JSON parser on the test classpath. `kotlin-dsl` provides none. `Json.parseToJsonElement` from kotlinx-serialization-json needs no compiler plugin (Derived; literal version to be chosen when adding it).

### F7. Signing when publishing to a file repository (Source; vanniktech 0.36.0, Gradle 9.7.1)

- `signAllPublications()` applies Gradle's `signing` plugin and calls `project.gradleSigning.setRequired(version.map { !it.endsWith("-SNAPSHOT") })`. Its KDoc says "If signing credentials are not configured this will fail the build unless the current version is a `SNAPSHOT`." [`MavenPublishBaseExtension.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt)
- Keys come from Gradle properties: `signing.keyId`, `signing.password`, `signing.secretKeyRingFile`, or in-memory `signingInMemoryKey`, optional `signingInMemoryKeyId` and `signingInMemoryKeyPassword`. They may also be supplied as `ORG_GRADLE_PROJECT_signingInMemoryKey` and so on. If `signingInMemoryKey` is present, `useInMemoryPgpKeys` is configured.
- Gradle's `Sign` task has `onlyIf("Signing is required, or signatory is set", spec(task -> isRequired() || getSignatory() != null))`; with `required` and no signatory it fails: "Cannot perform signing task '<path>' because it has no configured signatory". [`Sign.java`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/software/signing/src/main/java/org/gradle/plugins/signing/Sign.java)
- A signature artifact is published only if the task will run: `shouldBePublished()` returns `signTask.isEnabled() && signTask.getOnlyIf().isSatisfiedBy(signTask)`. [`SigningExtension.java`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/software/signing/src/main/java/org/gradle/plugins/signing/SigningExtension.java)
- Derived consequences for a TestKit publish to a file repository with the convention plugin applied:
  - With a release version and no keys, the publish fails at the `Sign` task. Nested builds never see the maintainer's user-level credentials (F5).
  - With a `-SNAPSHOT` version, `required` is false, the `Sign` tasks are skipped, and the publication carries no `.asc` files.
  - With the `Sign` tasks disabled in the generated build script (`tasks.withType<Sign>().configureEach { enabled = false }`), the publication also carries no `.asc` files, with a release version.
  - Asserting that the fixtures jar is signed needs a real key. The vanniktech suite commits a test key ring for this.
- vanniktech's `publishToMavenCentral()` points the non-snapshot repository at `build/publishing/mavenCentral` and uploads at the end of the build through a build service. The check must publish to its own `maven { name = "testFolder"; url = … }` repository and run `publishAllPublicationsToTestFolderRepository`, as vanniktech's integration runner does.

### F8. TestKit directory, Gradle user home, Gradle version, downloads (Documented and Source)

- With no `withTestKitDir` and no `org.gradle.testkit.dir`, the TestKit directory is `.gradle-test-kit` under the **Test task's temporary directory** when run from a Gradle `Test` task, that is `build-logic/build/tmp/<taskName>/.gradle-test-kit`. It is also the nested build's Gradle user home unless `-g` is passed. It is "not deleted by the runner". Contents are an implementation detail. [`GradleRunner.withTestKitDir` Javadoc](https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/test-kit/src/main/java/org/gradle/testkit/runner/GradleRunner.java), `DefaultGradleRunner.calculateTestKitDirProvider`.
- **First run:** the nested Gradle user home is empty, so every dependency the generated project resolves (Kotlin compiler, `kotlin-test`, JUnit, logback) is downloaded. The injected plugin jars are read from the outer Gradle user home and are instrumented into the nested one.
- **Reuse:** "TestKit re-uses a Gradle User Home between tests", so later tests, later classes and later runs reuse the downloads until `build-logic/build/tmp/<task>` is deleted.
- **Offline:** there is no offline mode; a first run without network fails at dependency resolution. Gradle's read-only dependency cache (`GRADLE_RO_DEP_CACHE`, which is "an incubating feature") could seed it. This is untested here.
- **Gradle version:** by default the runner finds the distribution from where the `GradleRunner` class was loaded, which for a `Test` task run by Gradle is the same distribution (9.7.1 via the wrapper). In an IDE it is the Gradle version used for the IDE import. [`test_kit.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/plugin-development/test_kit.adoc) The nested daemon runs on the JVM that runs the tests; TestKit offers no JDK selection.
- **Daemons:** "TestKit uses dedicated Gradle daemon processes … automatically shut down after the tests complete." Debug mode (`withDebug(true)` or `-Dorg.gradle.testkit.debug=true`) runs the build inside the test JVM and is disallowed together with `withEnvironment`.
- **`forwardOutput`:** the output is always in `BuildResult.output` and in the message of an unexpected failure. `forwardOutput()` only mirrors it to the test's stdout. Leave it off by default and enable it when debugging.
- **Timeouts:** `GradleRunner` has no timeout method (I read the class). Set `Test.timeout` on the dedicated task; MyGameTools uses 15 minutes for a cold nested build.

### F9. Configuration cache with injected plugin classpath on 9.7.1 (Documented and Source)

- Documented: "To test your build logic with the Configuration Cache enabled, pass the `--configuration-cache` argument to `GradleRunner`". The documented pattern runs the same build twice and asserts the output contains "Reusing configuration cache." [`configuration_cache_debugging.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/optimizing-builds/configuration-cache/configuration_cache_debugging.adoc), with the sample [`BuildLogicFunctionalTest.kt`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/snippets/optimizing-builds/configuration-cache/testKit/kotlin/src/test/kotlin/org/example/BuildLogicFunctionalTest.kt) using `withPluginClasspath()`.
- Source: with the cache on, the injected classpath is transformed in `BUILD_LOGIC` mode, which is supported. The only reported incompatibility is a third-party Java agent without Gradle's instrumentation agent. [`ConfigurationCacheInjectedClasspathInstrumentationStrategy.kt`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/initialization/ConfigurationCacheInjectedClasspathInstrumentationStrategy.kt) Gradle documents third-party agents as unsupported in embedded (`withDebug(true)`) mode.
- No limitation specific to Gradle 9.7.1 and `withPluginClasspath()` is documented. binary-compatibility-validator's suite runs every test with `--configuration-cache`.
- One dedicated harness test should run a representative task twice with `--configuration-cache` and assert "Reusing configuration cache". Since the user-level property is not applied in nested builds, the other tests should also pass `--configuration-cache` (or set it in the generated `gradle.properties`) so that the plugin's configuration-cache compatibility is exercised on every run.

### F10. Windows file locks and `@TempDir` (Source; Single source for the Kotlin daemon)

- `DefaultGradleRunner.run` appends `-Dorg.gradle.vfs.watch=false` on Windows, with the comment "To avoid those problems for TestKit tests on Windows, we disable file system watching there", and passes `SystemRoot`. [`DefaultGradleRunner.java`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/test-kit/src/main/java/org/gradle/testkit/runner/internal/DefaultGradleRunner.java)
- A past Windows `withEnvironment` defect (gradle/gradle#23959) was closed as "no longer reproducible since Gradle 8.0"; it does not apply on 9.7.1.
- JUnit 5.11.4's `@TempDir` close tries once to reset permissions and delete again, then throws an `IOException` that lists the paths it could not delete. It never waits. So a file held by a still-alive daemon fails the test at cleanup. [`TempDirectory.java`](https://github.com/junit-team/junit-framework/blob/r5.11.4/junit-jupiter-engine/src/main/java/org/junit/jupiter/engine/extension/TempDirectory.java) (tag r5.11.4)
  - With `CleanupMode.NEVER` the extension returns before deleting and still calls `factory.close()`.
  - `TempDirFactory` is `EXPERIMENTAL` since 5.10 in 5.11.4, so a custom factory is an experimental-API dependency.
- I found no source that establishes that the Kotlin daemon (or the Gradle daemon) holds project files on Windows after a build. General Windows reports about daemons holding jars exist but are old and not specific to TestKit. The in-process strategy removes the Kotlin daemon from the picture; the TestKit daemons stay alive until the test JVM finishes.
- The remedy that does not depend on resolving this question: a harness-owned project root under `build-logic/build/`, per-class project directories, best-effort deletion in `close()`, and leftovers removed by `clean`. A cleanup failure then cannot fail a test.

### F11. Shape of the dedicated task (Documented and Common practice)

- The tests are in `src/test/kotlin`, the `test` source set. A `JvmTestSuite` has "a 1:1:1 relationship between test suite, test suite target and a matching Test task", and its source directory is derived from the suite name. A second suite cannot be carved out of `src/test`, and pointing it at the same directory would compile every class twice. [`jvm_test_suite_plugin.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/platforms/jvm/jvm_test_suite_plugin.adoc)
- Gradle's TestKit page uses a separate source set because its example has one. Here the folder is fixed, so the MyGameTools shape fits: `tasks.register<Test>("integrationTest")` with `testClassesDirs`/`classpath` from `sourceSets.test`, `useJUnitPlatform()`, `filter { includeTestsMatching(...); isFailOnNoMatchingTests = false }`, and `tasks.test` with `excludeTestsMatching(...)`.
- Gradle 9 makes an empty `test` task fail unless filters are applied; "If filters are applied, the outcome depends on the `failOnNoMatchingTests` property". With every class in the excluded package, `isFailOnNoMatchingTests = false` on `test` is required. [`upgrading_major_version_9.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_major_version_9.adoc)
- The task is not wired into `build-logic`'s own `check`. By convention test-suite targets are not in `check` either.

### F12. Running a task of a `pluginManagement`-included build from the root (Documented, Source)

- Gradle's best-practice page uses the same layout as this repository and states: "You can run `./gradlew :build-logic:functionalTest` or `./gradlew :build-logic:check` from the root project directory to execute these tests." and "By default, tests within the included `build-logic` build are not executed when you run tests in the root project." Its settings is `pluginManagement { includeBuild("build-logic") }`. [`best_practices_testing.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/best-practices/best_practices_testing.adoc), [`settings.gradle.kts`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/snippets/best-practices/useTestKit-do/kotlin/settings.gradle.kts), [`build-logic/build.gradle.kts`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/snippets/best-practices/useTestKit-do/kotlin/build-logic/build.gradle.kts)
- Because the root has no `build` or `check` task and build-logic's tasks do not run unless asked, `./gradlew build` never reaches these tests. The `test` exclusion matters only for `:build-logic:build`, `:build-logic:check` and `:build-logic:test`.
- A `pluginManagement` plugin build is an included build in `gradle.includedBuilds`: `DefaultSettings.pluginManagement` ends with `includedBuildSpecs.addAll(((PluginManagementSpecInternal) getPluginManagement()).getIncludedBuilds())`, and `DefaultSettingsPreparer.loadBuild` calls `gradle.setIncludedBuilds(loadIncludedBuildsRecursively(settings))` over those specs. [`DefaultSettings.java`](https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core/src/main/java/org/gradle/initialization/DefaultSettings.java), [`DefaultSettingsPreparer.java`](https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core/src/main/java/org/gradle/initialization/DefaultSettingsPreparer.java) (Derived for the combined effect.)
- Dependency on it from the main build is documented: "a composite build can declare task dependencies on its included builds", with `dependsOn(gradle.includedBuild("my-app").task(":app:run"))` as the example. [`composite_builds.adoc`](https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/structuring-builds/composite_builds.adoc)
  - `Gradle.includedBuilds()` throws "Included builds are not yet available for this build." before the settings have been loaded, so the call belongs in a build script, not in `settings.gradle.kts` or an init script (`DefaultGradle.java`).
  - The root has no `check` task today (Consideration 2).
- **Including it both ways.**
  - `DefaultIncludedBuildRegistry.registerBuild` returns the existing state for an already-registered root directory, and `AcyclicIncludedBuildRegistry` carries the comment "If the included build was initially registered as a plugin build, any subsequent library registration resulting of that build will still be considered a plugin build, and vice versa". So double inclusion is anticipated.
  - Its only other effect is that `BuildInclusionCoordinator.prepareForInclusion` adds a non-plugin inclusion to `libraryBuilds`, making `build-logic`'s own project coordinates substitutable. That has no use here.
  - It is undocumented. A top-level `includeBuild("build-logic")` alone (MyGameTools) also serves plugin resolution for `plugins {}` blocks, but it cannot supply settings plugins.
  - Neither CLI addressing nor `gradle.includedBuild(...)` needs it.
  - **Not tried here** (see Known gaps).

### F13. What the later publication check can rely on

- A stable task path (`:build-logic:<task>`) and `gradle.includedBuild("build-logic").task(":<task>")` (F12).
- A shared TestKit home, so the second and later nested builds do not download again (F8).
- A harness that writes settings, `gradle.properties`, sources and build scripts into a directory it owns, and returns a configured runner. The check adds a producer project, a file repository and a consumer project to that.
- A way past signing: disabled `Sign` tasks in the generated producer script, a `-SNAPSHOT` version, or a real key (F7; Consideration 1).
- Hazards it must handle itself:
  - **Copy, do not drive.** The check must publish a generated copy of the module, not run the repository's real build from inside the test while the root build executes in the same directory (F4 cost 2).
  - **Injection into included builds is untested.** The injected resolver is a build-scoped service (F2), so an included producer build gets its own "injected-plugin" loader. Two builds with two Kotlin plugin loaders are normal in a composite, but the combination has not been run. If it fails, include a copy of `build-logic` with `pluginManagement { includeBuild(...) }` in the consumer and the producer for that check only.
  - **`testFixtures(...)` through `includeBuild`:** the repository's research already measured the composite resolution (`kmp-test-fixtures.md` F7). The check re-asserts it.

## 4. How comparable projects do it

- **MyGameTools** (organisation precedent; `build-logic/build.gradle.kts`, `settings.gradle.kts` and the two classes under `build-logic/src/test/kotlin/gametools/testing/integration/`, local checkout).
  - Top-level `includeBuild("build-logic")`. `tasks.register<Test>("integrationTest")` over `sourceSets.test` with `includeTestsMatching("gametools.testing.integration.*")`, `isFailOnNoMatchingTests = false`, `systemProperty("gametools.repoRoot", …)` and `timeout.set(Duration.ofMinutes(15))`. `tasks.named<Test>("test")` excludes the package.
  - `UmbrellaArtifactAggregationTest` runs the real build with `GradleRunner.create().withProjectDir(repoRoot)` in a `@BeforeAll` and reads jars under each module's `build/libs`. It never calls `withPluginClasspath`.
  - `UmbrellaVerifyTaskContractTest` builds throwaway `java`-plugin projects in `@TempDir` and copies the production guard's code into the test, with a drift check against the production script. The convention plugins themselves are not tested in isolation.
  - Takeaways: the task and filter shape is the right one here; the real-build and copied-code parts are not.
- **Gradle's "test your build logic" page and sample** (v9.7.1; `best_practices_testing.adoc`, `useTestKit-do`).
  - `pluginManagement { includeBuild("build-logic") }`, a `functionalTest` suite registered with `gradlePlugin.testSourceSets(...)`, `@TempDir` project directory, `GradleRunner.create().withProjectDir(testProjectDir).withPluginClasspath().forwardOutput().withArguments(...)`, `tasks.check { dependsOn(functionalTest) }`.
  - Run as `./gradlew :build-logic:functionalTest` from the root.
- **binary-compatibility-validator 0.18.2.** `withPluginClasspath()` plus a `testPluginRuntime` configuration written to `plugin-classpath.txt` for plugins applied through other plugins; `--configuration-cache` on every run; Gradle pinned to 8.5 with `withGradleVersion`; the generated project is assembled from resource files by a small DSL (`BaseKotlinScope`).
- **vanniktech/gradle-maven-publish-plugin 0.36.0.**
  - Generated projects get `kotlin.compiler.execution.strategy=in-process` and `org.gradle.vfs.watch=false`, and `--configuration-cache` is passed.
  - The plugin is published to `mavenLocal` first (`integrationTest` depends on `publishToMavenLocal`) and applied by version. Runs use `withDebug(true)`, `maxHeapSize = "2g"` and `--add-opens` JVM arguments. The TestKit directory is per test (`withTestKitDir(temp.resolve("test-kit-dir"))`).
  - Publishing goes to a `testFolder` file repository. Signing uses a committed test key ring or an in-memory test key, selected per test.
- **Kotlin/dokka v2.2.0** (`dokka-runners/dokka-gradle-plugin/src/testFixtures/kotlin/GradleTestKitUtils.kt` and `GradlePropertiesBuilder`).
  - Publishes the plugin to local Maven repositories (`devMavenRepositories`) and writes them into each generated settings with `exclusiveContent`. A `GradleProjectTest` builder writes `settings.gradle.kts`, `gradle.properties` (sorted, deterministic, with `daemonIdleTimeout` of 30 s), and sources.
- **Kotlin/kotlinx-kover v0.9.11** (`kover-gradle-plugin/build.gradle.kts`). A `functionalTest` source set and `Test` task, `dependsOn(tasks.collectRepository)`, a generated classpath file and system properties carrying versions and local repositories.
- **Kotlin/kotlinx-schema**: `KotlinxSchemaPluginMultiplatformTest` uses `withPluginClasspath()` on a multiplatform project but is `@Ignore`d. It is not evidence either way.
- Not read: `slackhq/foundry`.

## 5. Pitfalls & regrets

1. **Declaring the Kotlin plugin in the generated project** loads it twice (one loader from the metadata, one from plugin resolution) and produces `IsolatedKotlinClasspathClassCastException` or the "loaded multiple times" warning (F3). Apply only the convention plugin.
2. **Assuming user-level configuration applies.** The maintainer's configuration-cache setting does not reach nested builds. A harness that never turns it on proves nothing about compatibility (F5, F9).
3. **A TestKit build in the directory the outer build is running in** (real-repo and live-`build-logic` routes): shared `.gradle`/`build` directories and project locks that Gradle does not coordinate between invocations (F4).
4. **The first run is slow and needs the network.** The nested Gradle user home is empty and sits under `build/tmp/<task>`; a `clean` of `build-logic` empties it. Size the timeout for a cold run (MyGameTools: 15 minutes for a smaller job).
5. **Asserting on console output.** Deprecation lines and plugin warnings change with versions. Use outcomes, result XML, the dump file and the metadata JSON. The one place output text is unavoidable (`--dry-run`, "Reusing configuration cache") should assert `contains`, not equality.
6. **Relying on a level task's outcome to prove selection.** An empty level may end `SUCCESS`; assert the result XML files.
7. **JUnit `@TempDir` cleanup failures on Windows** surface as test errors with no build failure behind them (F10).
8. **Experimental `TempDirFactory`** (5.10+) is an easy fix with an unstable API in 5.11.4; a plain `AutoCloseable` project class avoids it.
9. **Putting `build-logic`'s default-package declarations in a test's import list.** The plugins and `WebToolsBuild` are in the default package; the tests are in `com.spartanlabs.testing.integration…`. Whether Kotlin can import default-package declarations from a named package is not established here (Known gaps). The TestKit tests need none of them, because they exercise the plugins through the generated build.
10. **`withDebug(true)`** (vanniktech's choice) runs in the test JVM, forbids `withEnvironment`, is documented as unsupported with third-party agents under the configuration cache, and needs the `--add-opens` arguments. The default daemon mode is the isolation the plugin tests want.
11. **Silently skipping tests when offline or when a repository is down** would turn a red signal into a green one. Let the nested build fail.
12. **A real signing key in the test configuration.** Public repository. The maintainer's user-level credentials are not visible to nested builds (F5) and must not be forwarded.

## 6. Recommendation for this repo

Use injection with a small harness, and a plain `Test` task. The mechanism is the one Gradle documents for exactly this layout. It costs no nested compile of `build-logic`, shares no directory with the outer build, and makes the tests the faithful check of the plugin's behaviour. The settings-level plugin-build mechanics are exercised by every real module. Reject the live-`build-logic` include and the real-build route for the reasons in F4.

**`build-logic/build.gradle.kts` (additions).**

```kotlin
import java.time.Duration

dependencies {
    // `kotlin-dsl` applies java-gradle-plugin, which adds gradleTestKit() and the
    // plugin-under-test metadata to the `test` source set (F1); no extra wiring.
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // For module.json; literal version, chosen when added.
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<literal>")
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Level 3 - TestKit checks on the convention plugins."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath   // carries the plugin-under-test metadata
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.spartanlabs.testing.integration.*")
        isFailOnNoMatchingTests = false
    }
    systemProperty("webtools.testkit.projects", layout.buildDirectory.dir("testkit/projects").get().asFile.absolutePath)
    timeout.set(Duration.ofMinutes(20))   // cold nested build; tune after the first runs
}

tasks.test {
    useJUnitPlatform()
    filter {
        excludeTestsMatching("com.spartanlabs.testing.integration.*")
        isFailOnNoMatchingTests = false
    }
}
```

**Invocation.** `./gradlew :build-logic:integrationTest` from the repository root. `./gradlew -p build-logic integrationTest` is the fallback. `./gradlew build` never reaches it (F12).

**Harness shape** (a support package outside the level packages, as `webtools-udp` already does with `testing/support/`; Kotlin, one class per file).

- An immutable description of a generated project, rendered by pure functions into `settings.gradle.kts`, `gradle.properties`, `build.gradle.kts` and source files. Raw strings with `trimIndent()` and a `//language=kotlin` injection hint, as MyGameTools does.
- An `AutoCloseable` project directory created under `webtools.testkit.projects`. Its `close()` deletes best-effort and never throws.
- A runner factory: `GradleRunner.create().withProjectDir(dir).withPluginClasspath().withArguments(*args, "--stacktrace", "--configuration-cache")`. The factory leaves the TestKit directory at its default, so all tests of the run share one Gradle user home.
- Small `BuildResult` helpers: outcome of a task path; result XML names for a task; parsed `module.json` variants.
- Per class, one `@BeforeAll` nested build, so assertions inside the class share it (MyGameTools' pattern). Use `@TestInstance(Lifecycle.PER_CLASS)` instead of a `companion object` with `lateinit`.
- Assertions: JUnit `Assertions` (what MyGameTools uses and needs no dependency). Whether `kotlin-test` resolves cleanly under `kotlin-dsl` is not established here.

**Generated `gradle.properties`.**

```properties
org.gradle.configuration-cache=true
org.gradle.vfs.watch=false
kotlin.compiler.execution.strategy=in-process
org.gradle.jvmargs=-Xmx1g
```

(heap value to be tuned by the first runs).

**Test classes** (each one nested build, in `…testing/integration/`).

| Class | Build runs | Assertions |
|---|---|---|
| plugin smoke / `test` lifecycle | `test` | `:jvmTest` `SUCCESS`; result XMLs for both sample classes under `jvmTest` |
| level selection | the seven `*Test` level tasks | `componentTest` contains only the `commonTest` class's XML; `integrationTest` only the `jvmTest` class's; `gatingTest` etc. contain none; the unleveled class runs in `jvmTest` only |
| API validation | `apiDump`, `apiCheck`, `check --dry-run`; mutation then `apiCheck` | dump file exists with `sample/Greeter`; `apiCheck` `SUCCESS`; `:apiCheck` in the dry-run output; `FAILED` after the mutation |
| published attribute | the two `generateMetadataFileFor…Publication` tasks | `jvmApiElements-published` and `jvmRuntimeElements-published` carry `org.gradle.jvm.version` equal to the JVM major version, in the root and the `jvm` module |
| configuration cache | one task twice | second output contains "Reusing configuration cache" |

**Later publication check.** Add a second `Test` task for the check's package (for example `…testing.integration.publication.*`), wire it into `build-logic`'s `check`, and depend on it from the root with `gradle.includedBuild("build-logic").task(":publicationCheck")`. Keep `integrationTest` as the on-demand superset. Disable the `Sign` tasks in the generated producer script, unless the check must assert signatures (Consideration 1). The `webtools-http` check publishes a generated copy of the module.

## 7. Known gaps

- **Nothing was run.** Every behaviour on this toolchain is read from source and documentation. The first implementation step must be the smoke test (plugin applies, `tasks` lists the seven level tasks) and a one-off manual `./gradlew :build-logic:integrationTest` from the root to confirm points a–e.
  - a. The combined effect that a `pluginManagement`-included build is addressable as `:build-logic:<task>` and via `gradle.includedBuild("build-logic")` rests on the documented best-practice page plus source reading, not on a run of this repository.
  - b. vanniktech 0.36.0 under `withPluginClasspath` has no independent evidence (F3).
  - c. The root `module.json` carrying `org.gradle.jvm.version` on the `jvm*Elements-published` variants is taken from the repository's earlier measurement on a different harness, not re-run through TestKit.
  - d. Publishing with disabled `Sign` tasks (and with a `-SNAPSHOT` version) succeeding is derived from the `Sign`/`SigningExtension` source. The layout of a SNAPSHOT publication in a file repository (timestamped files) was not checked.
  - e. Whether the Kotlin daemon or a TestKit Gradle daemon holds files in a generated project on Windows after a build was not established (F10).
- **Nested daemon heap.** No source gives a safe `org.gradle.jvmargs` for the Kotlin compiler in-process plus a multiplatform configuration. The repository's research saw a long-lived shared daemon run out of Metaspace once. `-Xmx1g` is a starting value.
- **First-run cost and download size** are not measured, so the 20-minute timeout is a placeholder.
- **Double inclusion** (plugin build plus top-level `includeBuild`) was read, not tried.
- **Injected classpath in included builds** (needed by the later check's consumer and producer builds) is derived from the resolver being build-scoped, not tried.
- **Default-package access.** Whether Kotlin test code in a named package can reference `WebToolsBuild` (default package) was not checked. A Level-2 test of `WebToolsBuild` itself may hit this; the TestKit tests do not need it.
- **`kotlin-test` under `kotlin-dsl`** (which version `kotlin("test-junit5")` resolves to) was not checked.
- **Read-only dependency cache** (`GRADLE_RO_DEP_CACHE`) as an offline or speed remedy is an incubating Gradle feature and untried.
- **Linux CI** was not considered; Windows and JDK 23 only.
- **`slackhq/foundry`** was not read; the comparison set is MyGameTools, Gradle's page, binary-compatibility-validator, vanniktech, Dokka and Kover.

## 8. Considerations

### Consideration 1. How the later publication check gets past `signAllPublications()`

- **Options.**
  - (a) Disable the `Sign` tasks in the generated producer script. No key, a release version, and `.asc` files absent. It cannot assert that the fixtures jar is signed.
  - (b) A `-SNAPSHOT` version. No key; `required` is false. The version is not the real one, and the file-repository layout differs from a release (unchecked).
  - (c) A throwaway in-memory key passed as `signingInMemoryKey`, `signingInMemoryKeyId` and `signingInMemoryKeyPassword` through the generated `gradle.properties`. It lets the check assert that every artifact, including `-test-fixtures.jar`, is signed. The key must be generated at test time (for example with BouncyCastle on the test classpath) or committed as a test-only key in a public repository.
- **Recommendation: (a)** for the variant, capability and attribute assertions the check is for. Move to (c) with a key generated at test time only if the check is extended to assert signature coverage of the fixtures jar.

### Consideration 2. How the root `check` exists

- **Options.**
  - (a) `tasks.register("check") { dependsOn(gradle.includedBuild("build-logic").task(":publicationCheck")) }` in the root `build.gradle.kts`. It adds one lifecycle task, and `./gradlew check` still runs every module's `check`.
  - (b) Apply `lifecycle-base` to the root. It gives `check`, `assemble`, `build` and `clean` and a `build/` convention, which changes more than the root keeps today.
- **Recommendation: (a).** It touches only what the later check needs. This changes the root's "only `allprojects { group; version }`" shape by one line, so the root build's description must say so when the check lands.

### Consideration 3. One dedicated task or two

- **Options.** One `integrationTest` for all Level-3 TestKit tests, wired into the root `check` when the publication check lands; or `integrationTest` on demand plus a narrower `publicationCheck` that alone is wired into `check`.
- **Recommendation: two.** Wiring the whole superset into `check` would make every nested-plugin test part of the routine verification, which is more than the publication check needs and slower to run.

## 9. Load-bearing claims

1. **`kotlin-dsl` applies `java-gradle-plugin`.** `apply<JavaGradlePluginPlugin>()` / `apply<KotlinDslBasePlugin>()` / `apply<PrecompiledScriptPlugins>()` in `KotlinDslPlugin.apply`. <https://github.com/gradle/gradle/blob/v9.7.1/platforms/core-configuration/kotlin-dsl-plugins/src/main/kotlin/org/gradle/kotlin/dsl/plugins/dsl/KotlinDslPlugin.kt>, tag v9.7.1.
2. **`java-gradle-plugin` adds TestKit and the metadata to the test source sets.** `dependencies.add(implementationConfigurationName, dependencies.gradleTestKit());` and `dependencies.add(runtimeOnlyConfigurationName, project.getLayout().files(pluginClasspathTask));`; and `project.getTasks().withType(Test.class).configureEach(test -> {` … `test.getJvmArgumentProviders().add(new GradleJvmCommandLineArgumentProvider(test));`. <https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/plugin-development/src/main/java/org/gradle/plugin/devel/plugins/JavaGradlePluginPlugin.java>, tag v9.7.1.
3. **The metadata classpath is `main` output plus the runtime classpath minus the Gradle API.** `sourceSet.getOutput(),` / `view.getFiles().getElements()` in `classpathForPlugin`, with `componentFilter(spec(JavaGradlePluginPlugin::excludeGradleApi))`; and "The code under test. Defaults to {@code sourceSets.main.runtimeClasspath}." in `PluginUnderTestMetadata`. Same file plus <https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/plugin-development/src/main/java/org/gradle/plugin/devel/tasks/PluginUnderTestMetadata.java>, tag v9.7.1.
4. **`withPluginClasspath()` reads the metadata file.** "This method looks for a file named {@code plugin-under-test-metadata.properties} on the runtime classpath, and uses the {@code implementation-classpath} as the classpath, which is expected to a {@link File#pathSeparatorChar} joined string." <https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/test-kit/src/main/java/org/gradle/testkit/runner/GradleRunner.java>, tag v9.7.1.
5. **Injection works only through `plugins {}` and not with `compileOnly` plugin dependencies.** "NOTE: Automatic classpath injection only works when the plugin-under-test is applied in the test build using the <<plugins_intermediate.adoc#sec:plugins_block,`plugins {}` DSL>>." and "The automatic classpath injection provided by `GradleRunner.withPluginClasspath()` is **incompatible** with plugins that use `compileOnly` to depend on another plugin's API." <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/plugin-development/test_kit.adoc>, tag v9.7.1.
6. **The injected plugins form one class-loader scope and are found by id.** `parentScope.createChild("injected-plugin", null)` / `.local(classPath)`; `visitor.visitClassLoader(plugin.asClass().getClassLoader());`. <https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/plugin-use/src/main/java/org/gradle/plugin/use/resolve/service/internal/DefaultInjectedClasspathPluginResolver.java>, tag v9.7.1. The resolver is created with `classLoaderScopeRegistry.getCoreAndPluginsScope(),` in <https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/plugin-use/src/main/java/org/gradle/plugin/internal/PluginUseServices.java>.
7. **A precompiled script's `plugins { id(...) }` is `pluginManager.apply(id)`.** `override fun id(id: String): PluginDependencySpec {` / `pluginManager.apply(id)` in `PrecompiledProjectScript.plugins`. <https://github.com/gradle/gradle/blob/v9.7.1/platforms/core-configuration/kotlin-dsl/src/main/kotlin/org/gradle/kotlin/dsl/precompile/v1/PrecompiledScriptTemplates.kt>, tag v9.7.1.
8. **External plugins in precompiled scripts come from the implementation classpath.** "In order to apply an external plugin in a precompiled script plugin, it has to be added to the plugin project's implementation classpath in the plugin's build file:". <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/plugin-development/implementing_gradle_plugins_precompiled.adoc>, tag v9.7.1.
9. **Precompiled scripts are registered as plugins when `java-gradle-plugin` is present.** `plugins.withType<JavaGradlePluginPlugin> {` / `exposeScriptsAsGradlePlugins(`. <https://github.com/gradle/gradle/blob/v9.7.1/platforms/core-configuration/kotlin-dsl-provider-plugins/src/main/kotlin/org/gradle/kotlin/dsl/provider/plugins/precompiled/DefaultPrecompiledScriptPluginsSupport.kt>, tag v9.7.1.
10. **Gradle's own build-logic testing page: same layout, root invocation, no run from the root test.** "You can run `./gradlew :build-logic:functionalTest` or `./gradlew :build-logic:check` from the root project directory to execute these tests." and "By default, tests within the included `build-logic` build are not executed when you run tests in the root project." <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/best-practices/best_practices_testing.adoc>, tag v9.7.1. Settings: `pluginManagement {` / `includeBuild("build-logic") // <1>` in <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/snippets/best-practices/useTestKit-do/kotlin/settings.gradle.kts>.
11. **Included-build tasks can be run and depended on.** "Tasks from an included build can be executed from the command-line or IDE in the same way as tasks from a regular multi-project build." and "a composite build can declare task dependencies on its included builds"; sample `dependsOn(gradle.includedBuild("my-app").task(":app:run"))`. <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/structuring-builds/composite_builds.adoc> and <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/snippets/structuring-builds/composite-builds-basic/kotlin/build.gradle.kts>, tag v9.7.1.
12. **Known limitation of shared included builds.** "Multiple composite builds may conflict when run in parallel if more than one includes the same build." / "Gradle does not share the project lock of a shared composite build between Gradle invocations to prevent concurrent execution." Same `composite_builds.adoc`, tag v9.7.1.
13. **Plugin builds are in `gradle.includedBuilds`.** `includedBuildSpecs.addAll(((PluginManagementSpecInternal) getPluginManagement()).getIncludedBuilds());` in `DefaultSettings.pluginManagement`; `gradle.setIncludedBuilds(loadIncludedBuildsRecursively(settings));` in `DefaultSettingsPreparer.loadBuild`. <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core/src/main/java/org/gradle/initialization/DefaultSettings.java> and <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core/src/main/java/org/gradle/initialization/DefaultSettingsPreparer.java>, tag v9.7.1.
14. **Double inclusion is anticipated by the registry.** "If the included build was initially registered as a plugin build, any subsequent library registration resulting of that build will still be considered a plugin build, and vice versa." <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/composite-builds/src/main/java/org/gradle/composite/internal/AcyclicIncludedBuildRegistry.java>, tag v9.7.1; and `if (!asPlugin && !libraryBuilds.contains(build)) {` in `BuildInclusionCoordinator.prepareForInclusion`, <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core/src/main/java/org/gradle/internal/buildtree/BuildInclusionCoordinator.java>.
15. **TestKit ignores user-level configuration; daemons are dedicated; working directories are kept.** "Configuration from the default Gradle User Home (for example, `~/.gradle/gradle.properties`) is **not** used." / "TestKit uses dedicated Gradle daemon processes for the builds it runs." / "These daemons are distinct from any daemons used by "normal" Gradle invocations and are automatically shut down after the tests complete." / "By default, the working directories created for each test build are **not** deleted." `test_kit.adoc`, tag v9.7.1.
16. **TestKit directory and Gradle user home.** "If no explicit Gradle user home is specified via the build arguments (i.e. the {@code -g «dir»} option}), this directory will also be used for the Gradle user home for the test build." / "When executed from a Gradle Test task, the Test task's temporary directory is used (see {@link org.gradle.api.Task#getTemporaryDir()})." `GradleRunner.java`, tag v9.7.1. Reuse: "Note that TestKit re-uses a Gradle User Home between tests" in `test_kit.adoc`.
17. **Gradle version used by the runner.** "By default, the runner will attempt to find a Gradle distribution based on where the `GradleRunner` class was loaded from." `test_kit.adoc`, tag v9.7.1.
18. **Windows: file-system watching is disabled by TestKit.** "To avoid those problems for TestKit tests on Windows, we disable file system watching there." <https://github.com/gradle/gradle/blob/v9.7.1/platforms/extensibility/test-kit/src/main/java/org/gradle/testkit/runner/internal/DefaultGradleRunner.java>, tag v9.7.1.
19. **JUnit 5.11.4 `@TempDir` fails the test when deletion fails.** `SortedMap<Path, IOException> failures = deleteAllFilesAndDirectories(fileOperations); if (!failures.isEmpty()) { throw createIOExceptionWithAttachedFailures(failures); }`; with `cleanupMode == NEVER` it returns before that and `factory.close()` runs in `finally`. <https://github.com/junit-team/junit-framework/blob/r5.11.4/junit-jupiter-engine/src/main/java/org/junit/jupiter/engine/extension/TempDirectory.java>, tag r5.11.4. `TempDirFactory`: `@API(status = EXPERIMENTAL, since = "5.10")`, <https://github.com/junit-team/junit-framework/blob/r5.11.4/junit-jupiter-api/src/main/java/org/junit/jupiter/api/io/TempDirFactory.java>.
20. **Configuration cache and TestKit.** "To test your build logic with the Configuration Cache enabled, pass the `--configuration-cache` argument to `GradleRunner`" … "3. Assert that the Configuration Cache gets reused." <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/optimizing-builds/configuration-cache/configuration_cache_debugging.adoc>, tag v9.7.1. "It is not supported when TestKit runs in embedded mode (`withDebug(true)`)." (third-party agents) in `configuration_cache_requirements.adoc`, same directory.
21. **The injected classpath is supported under the configuration cache.** `return TransformMode.BUILD_LOGIC` in `ConfigurationCacheInjectedClasspathInstrumentationStrategy.getTransform`. <https://github.com/gradle/gradle/blob/v9.7.1/platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/initialization/ConfigurationCacheInjectedClasspathInstrumentationStrategy.kt>, tag v9.7.1.
22. **`run()` returns a result whatever the outcome; there is no timeout API.** "Executes a build, without expecting a particular outcome." `GradleRunner.java`, tag v9.7.1 (the class's public methods: `withTestKitDir`, `withProjectDir`, `withArguments`, `withPluginClasspath`, `withDebug`, `withEnvironment`, `forwardOutput`, `build`, `buildAndFail`, `run`; none sets a timeout).
23. **Metadata and POM generation tasks and output paths.** `"generateMetadataFileFor" + capitalize(publicationName) + "Publication"` and `buildDir.file("publications/" + publication.getName() + "/module.json")`. <https://github.com/gradle/gradle/blob/v9.7.1/platforms/software/maven/src/main/java/org/gradle/api/publish/maven/plugins/MavenPublishPlugin.java>, tag v9.7.1.
24. **Kotlin multiplatform publication names and variant suffix.** `publishing.publications.create("kotlinMultiplatform", MavenPublication::class.java)` in <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/publishing/Publishing.kt>; `publications.create(kotlinComponent.name, MavenPublication::class.java)` in the same file; `private val publishedConfigurationNameSuffix = "-published"` in <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/KotlinTargetSoftwareComponentImpl.kt>, tag v2.2.0.
25. **vanniktech signing rules.** "If signing credentials are not configured this will fail the build unless the current version is a `SNAPSHOT`." and `project.gradleSigning.setRequired(version.map { !it.endsWith("-SNAPSHOT") })`; properties `signing.keyId`, `signing.password`, `signing.secretKeyRingFile`, `signingInMemoryKey`, `signingInMemoryKeyId`, `signingInMemoryKeyPassword`; "The in memory properties can also be provided as environment variables by prefixing them with `ORG_GRADLE_PROJECT_`". <https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt>, tag 0.36.0.
26. **Gradle `Sign` is skipped when not required and unsigned artifacts are not published.** `onlyIf("Signing is required, or signatory is set", spec(task -> isRequired() || getSignatory() != null));` in <https://github.com/gradle/gradle/blob/v9.7.1/platforms/software/signing/src/main/java/org/gradle/plugins/signing/Sign.java>; `return signTask.isEnabled() && signTask.getOnlyIf().isSatisfiedBy(signTask);` in `DefaultDerivedArtifactFile.shouldBePublished`, <https://github.com/gradle/gradle/blob/v9.7.1/platforms/software/signing/src/main/java/org/gradle/plugins/signing/SigningExtension.java>, tag v9.7.1.
27. **vanniktech's own integration runner.** `appendLine("kotlin.compiler.execution.strategy=in-process")`, `appendLine("org.gradle.vfs.watch=false")`, `val arguments = mutableListOf(task, "--stacktrace", "--configuration-cache")`, `val task = ":module:publishAllPublicationsToTestFolderRepository"`, `.withDebug(true)`, `withTestKitDir(temp.resolve("test-kit-dir").toFile())`. <https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.36.0/plugin/src/integrationTest/kotlin/com/vanniktech/maven/publish/util/ProjectSpecRunner.kt>, tag 0.36.0.
28. **binary-compatibility-validator's functional tests use injection, a multiplatform single-JVM project, and the configuration cache.** `.withPluginClasspath()` and `add("--configuration-cache")` in <https://github.com/Kotlin/binary-compatibility-validator/blob/0.18.2/src/functionalTest/kotlin/kotlinx/validation/api/TestDsl.kt>; `rootProjectDir.resolve("$API_DIR/${rootProjectDir.name}.api")` in `BaseKotlinGradleTest.kt` (same directory tree, tag 0.18.2); `compileOnly(libs.gradlePlugin.kotlin)` and the comment "It uses isolation, so plugins applied by the build script are not visible in the plugin classloader." in <https://github.com/Kotlin/binary-compatibility-validator/blob/0.18.2/build.gradle.kts>.
29. **The Kotlin plugin rejects a split class path.** "User setup lead to Gradle separating the classpath between two projects applying the Kotlin Gradle plugin. This is not a supported setup and the ClassCastException should report this." <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/utils/reflectionUtils.kt>, tag v2.2.0.
30. **In-process Kotlin compilation.** "The simplest strategy from the perspective of memory management, but it's less isolated from other logic running in the same process because it shares state, such as JVM system properties." and the property `kotlin.compiler.execution.strategy` with values `daemon` (default) and `in-process`. <https://kotlinlang.org/docs/compiler-execution-strategy.html>, read 2026-10-21.
31. **A JVM test suite has a single target; targets are not in `check`.** "each test suite has a single target. This results in a 1:1:1 relationship between test suite, test suite target and a matching Test task" and "By convention, test suite targets are not associated with the `check` task." <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/platforms/jvm/jvm_test_suite_plugin.adoc>, tag v9.7.1.
32. **Gradle 9 test task behaviour and explicit JUnit launcher.** "If filters are applied, the outcome depends on the `failOnNoMatchingTests` property." <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_major_version_9.adoc>; "If using JUnit 5, an explicit `runtimeOnly` dependency on `junit-platform-launcher` is required" <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/releases/upgrading/upgrading_version_8.adoc>, tag v9.7.1.
33. **`kotlin-dsl` test dependencies.** "Adds the `kotlin-stdlib`, `kotlin-reflect`, and `gradleKotlinDsl()` dependencies to the `compileOnly` and `testImplementation` configurations". <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/dsl-apis/kotlin_dsl.adoc>, tag v9.7.1.
34. **Default repository mode.** "This is the default behavior." on `PREFER_PROJECT` ("any repository declared on a project will cause the project to use the repositories declared by the project, ignoring those declared in settings"). <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core-api/src/main/java/org/gradle/api/initialization/resolve/RepositoriesMode.java>, tag v9.7.1.
35. **Read-only dependency cache.** "NOTE: The shared read-only dependency cache is an incubating feature." and "Then set the `GRADLE_RO_DEP_CACHE` environment variable to point to the directory containing the cache:". <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/dependency-management/dependency-management/dependency_caching.adoc>, tag v9.7.1.
36. **Dokka's and Kover's harnesses** (comparison only). Dokka: `private val devMavenRepositories: List<Path> by systemProperty { repos -> repos.split(",").map { Path(it) } }` and `daemonIdleTimeout: Duration? = 30.seconds` in <https://github.com/Kotlin/dokka/blob/v2.2.0/dokka-runners/dokka-gradle-plugin/src/testFixtures/kotlin/GradleTestKitUtils.kt> and `GradlePropertiesBuilder.kt` (tag v2.2.0). Kover: `val functionalTest by tasks.registering(Test::class)` and `dependsOn(tasks.collectRepository)` in <https://github.com/Kotlin/kotlinx-kover/blob/v0.9.11/kover-gradle-plugin/build.gradle.kts>, tag v0.9.11.
