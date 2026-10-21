# Publishing a Kotlin Multiplatform module's test support as a Gradle test-fixtures variant

## Question

Can `webtools-http`, a Kotlin Multiplatform module (ports in `commonMain`, OkHttp and Caffeine in `jvmMain`), publish its test support as a Gradle test-fixtures variant of itself, so that a JVM Gradle consumer writes `testImplementation(testFixtures("io.github.spartanlaboratories:webtools-http:<version>"))`, and if so, how?

An answer has to meet these requirements:

- Kotlin Gradle plugin (KGP) 2.2.0, Gradle 9.7.1, vanniktech `maven-publish` 0.36.0 (0.37.0 exists).
- It works from Maven Central and through a nested composite build that substitutes the module.
- The fixtures' own dependencies (`kotlinx-coroutines-test` 1.10.2, `mockwebserver3` 5.5.0) reach only the consumer's test classpath, never a main variant.
- Java 11 bytecode, and the published variant declares `org.gradle.jvm.version: 11`.
- Sources and javadoc jars, signing, and Central's validation rules are accounted for.

## Bottom line

- **It can be done on the pinned toolchain (KGP 2.2.0, Gradle 9.7.1, vanniktech 0.36.0), and I built and measured it.** The result is a root-module variant pair `testFixturesApiElements` / `testFixturesRuntimeElements` carrying the capability `<group>:<module>-test-fixtures:<version>`, plus a `-test-fixtures.jar` published beside the root module's files. It resolves with `testFixtures(...)` from a file repository (standing in for Central) and through `includeBuild`, both when the producer is the included build's root project and when it is one of its subprojects. No version change is needed: vanniktech 0.36.0 and 0.37.0 produce identical metadata, and KGP 2.2.21 and 2.4.21 also work.
- **There is no first-class KGP feature for it.** The request for test fixtures in KMP is open (KT-69482 in Backlog, KT-63142 "To be discussed", both updated August 2026). What exists is two supported building blocks that combine: a way to compile a JVM-only fixtures compilation (either Gradle's `java-test-fixtures` plugin, which JetBrains documents as compatible with the multiplatform plugin, or a hand-created compilation associated with `main`) and the experimental `kotlin.publishing.adhocSoftwareComponent` API (since KGP 2.1.20), which adds variants to the root publication. Nothing in the vanniktech plugin helps for a KMP module; its fixtures workarounds apply only to JVM and Java platforms.
- **The GeneralTools statement is correct but does not cover this case.** `java` and `java-library` raise an `ERROR` diagnostic in a KMP project on Gradle ≥ 8.7 (KGP source at v2.2.0 and v2.4.21). The `java-test-fixtures` plugin on Gradle 9.7.1 applies only `JavaBasePlugin`, never `java` or `java-library`, so no diagnostic fires (measured), and the Kotlin docs name it as supported with Kotlin ≥ 2.1.21.
- **Two equivalent mechanisms, one consumer contract.** Option A (the `java-test-fixtures` plugin) and Option B (a dedicated `testFixtures` compilation with hand-declared outgoing configurations) publish the same capability and attributes, and a consumer cannot tell them apart. B depends only on documented KGP APIs plus the one experimental publishing API, and needs no workaround on KGP 2.2.0; A needs one (the fixtures runtime variant is published without dependencies on 2.2.0, fixed by 2.2.10). I would build B and keep A as the fallback. See Recommendation.
- **The fixtures are JVM-only in practice, and that is fine.** `mockwebserver3` 5.5.0 is JVM-only, and no mechanism publishes common-source fixtures as a Gradle fixtures variant. Everything in the test support goes in the JVM fixtures compilation. The cost is that a future non-JVM target gets no fixtures from this mechanism (KT-63142 is the open request).
- **Sharp edges to carry into the plan**, all measured: the variant must skip the "classes" and "resources" secondary variants or consumers fail to compile; the fixtures variant must depend on the main module explicitly; fixtures dependencies appear in the root POM as `optional` compile or runtime dependencies (without `mapToOptional()` they land there as plain compile dependencies); no sources or javadoc jar exists for the fixtures (same as Gradle's own plugin; Central hosts such artifacts); `explicitApi()` does not apply to the fixtures compilation; the JVM version attribute defaults to the Gradle JVM (21 here) unless set.

## Findings

Tiers: **documented** (a spec, official docs or a tracker entry), **observed practice** (what mature code visibly does), **single-source** (one person's statement), **measured** (I built it in a probe).

### F1. The Kotlin docs name the Java test fixtures plugin as working with the multiplatform plugin (documented; Kotlin 2.1.21+, Gradle > 8.7)

The multiplatform compatibility guide says: "If you use the Java test fixtures Gradle plugin with Kotlin 2.1.20 and a Gradle version higher than 8.7, the plugin doesn't work. Instead, upgrade to Kotlin 2.1.21 where this issue is resolved." It also commits to "using Gradle's java-base plugin within your multiplatform projects."
Source: <https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html>, read 2026-10-20.

KGP carries code for this, `ConfigureJavaTestFixturesSideEffect`: when `java-test-fixtures` is present it creates a `testFixtures` compilation on the JVM target, associates it with `main` (so `internal` declarations are visible), and associates the `test` compilation with it. The code comments "Only applicable for KMP" on the branch that wires `testFixturesApiElements` to the compilation's `api` configurations. KGP's own test suite exercises it (`TestFixturesTest`, integration project `kmp-jvm-test-fixtures`).
Source: <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/jvm/KotlinJvmTargetTestFixturesSideEffect.kt>, tag v2.2.0. Tests: <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/functionalTest/kotlin/org/jetbrains/kotlin/gradle/unitTests/TestFixturesTest.kt>.

That support is for compiling and consuming inside the build. Publishing is not covered by any JetBrains test or doc I found.

### F2. `java`, `java-library` and `application` are an ERROR with KMP on Gradle ≥ 8.7; `java-test-fixtures` is not one of them (documented source, plus measured)

`KMPJavaPluginsIncompatibilityDiagnostic` in KGP v2.2.0 uses `ToolingDiagnosticFactory(ERROR, ...)` with `val severity = if (GradleVersion.current() >= GradleVersion.version("8.7")) ERROR else WARNING`. `DeprecateJavaPluginsApplicationSetupAction` fires it on `project.plugins.withId("java-library")`, `"application"` and `"java"`. The GeneralTools research statement is therefore accurate for those three plugin ids (the same diagnostic exists at v2.4.21).
Sources: <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/diagnostics/KotlinToolingDiagnostics.kt>, <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/ApplyJavaBasePluginSetupAction.kt>.

`JavaTestFixturesPlugin.apply` in Gradle v9.7.1 (and v8.7.0, v8.14.3, v9.0.0) calls `project.getPlugins().apply(JavaBasePlugin.class)` and does the component wiring only inside `project.getPluginManager().withPlugin("java", ...)`. Without `java`, no fixtures variants are added to any component and the implicit "fixtures depend on the project" and "tests depend on the fixtures" dependencies are skipped. I measured this: a KMP project with `id("java-test-fixtures")` configures with no diagnostic, and the plugin publishes nothing by itself.
Source: <https://github.com/gradle/gradle/blob/v9.7.1/platforms/jvm/plugins-jvm-test-fixtures/src/main/java/org/gradle/api/plugins/JavaTestFixturesPlugin.java>.

`withJava()` is not needed and should not be used: in KGP v2.2.0, `KMPWithJavaDiagnostic` is `ERROR` on Gradle ≥ 9.0.

### F3. There is no first-class KMP test-fixtures feature, and the tracker shows churn (documented tracker state, fetched 2026-10-20)

| Issue | What | State |
|---|---|---|
| [KT-63142](https://youtrack.jetbrains.com/issue/KT-63142) | "Add support for Gradle Test Fixtures in non-JVM platforms" | To be discussed; created 2023-11-03, updated 2026-08-04; assignee Yahor Berdnikau |
| [KT-69482](https://youtrack.jetbrains.com/issue/KT-69482) | "FR: Support JVM/Android test-fixtures in KMP projects" | Backlog; created 2024-06-27, updated 2026-08-07 |
| [KT-88423](https://youtrack.jetbrains.com/issue/KT-88423) | `java-test-fixtures` in KMP does not compile Kotlin under `src/testFixtures/kotlin` (reported on a 2.5.0-dev build) | Submitted; created 2026-08-07 |
| [KT-75262](https://youtrack.jetbrains.com/issue/KT-75262) | plugin application order broke the project | Fixed in 2.2.0-Beta1 / 2.1.20-RC2 |
| [KT-75808](https://youtrack.jetbrains.com/issue/KT-75808) | "MPP with jvm target and Gradle java-test-fixtures is broken" | Fixed in 2.2.0-Beta2 / 2.1.21-RC |
| [KT-77466](https://youtrack.jetbrains.com/issue/KT-77466) | `testFixturesApi` and similar configurations do not affect the `jvmTestFixtures` source set | Fixed in 2.2.10-RC (available 2.2.20-Beta2) |

KGP 2.3.0-Beta2 (the affected version of KT-82223) prints, for `api` dependencies in test source sets: "For Kotlin/JVM projects, consider using Test Fixtures ... For Test Fixtures support in non-Kotlin/JVM projects, please add your use-case to https://youtrack.jetbrains.com/issue/KT-63142" (quoted in the comments of [KT-82223](https://youtrack.jetbrains.com/issue/KT-82223)). I saw no such warning from the fixtures compilation in any probe, including KGP 2.4.21.

### F4. The publishing hook: `kotlin.publishing.adhocSoftwareComponent` (documented; KGP 2.1.20+, still `@ExperimentalKotlinGradlePluginApi` at v2.4.21)

KDoc at v2.2.0: "Returns [AdhocComponentWithVariants] that can be used to add additional variants to root Kotlin Multiplatform Publication. It is not possible to modify or replace default Kotlin Multiplatform publication variants. Attributes of added variants should not match default Kotlin Multiplatform publication variants." The 2.1.20 release notes: "You cannot modify existing Gradle variants with this feature. This feature is Experimental."
Sources: <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinPublishing.kt>, <https://kotlinlang.org/docs/whatsnew2120.html>.

Consequence for placement (measured): the fixtures variants are written into the **root** module's Gradle Module Metadata (`webtools-http-<v>.module`), beside the default variants, and the classifier jar is published as `webtools-http-<v>-test-fixtures.jar` in the root module's directory. They are not in `webtools-http-jvm`. The default `jvm*Elements-published` variants still redirect with `available-at` to `../../<module>-jvm/<v>/<module>-jvm-<v>.module`. A consumer's capability request is answered by the root module's own variant, so no redirect is needed. I found no public KGP API to attach variants to the `-jvm` publication instead.

### F5. Measured recipe A: `java-test-fixtures` beside `kotlin("multiplatform")`

Probe `p1` (KGP 2.2.0, Gradle 9.7.1, JDK 21 launcher, `jvmTarget` 11; files under the probe folder, see Known gaps for paths). What had to be done:

1. Declare `id("java-test-fixtures")` and `kotlin("multiplatform")` (order does not matter from 2.1.20-RC2). Fixture sources live in **`src/jvmTestFixtures/kotlin`** and `src/jvmTestFixtures/resources` (the compilation KGP creates is named `testFixtures`, its default source set `jvmTestFixtures`). Fixture dependencies go on **`jvmTestFixturesApi`** (on KGP 2.2.0 the plain `testFixturesApi` does not feed the Kotlin compilation; fixed in 2.2.10, KT-77466).
2. Publish through `kotlin.publishing.adhocSoftwareComponent { addVariantsFromConfiguration(configurations.testFixturesApiElements.get()) { ... } }` and the same for `testFixturesRuntimeElements`. Gradle's own `JavaConfigurationVariantMapping` is internal API, so its skip rule is re-implemented: skip any variant whose artifacts have type `java-classes-directory`, `java-resources-directory` or `directory`; otherwise `mapToMavenScope("compile"|"runtime")` and `mapToOptional()`.
   - Without the skip, the module metadata carries `testFixturesApiElementsClasses` and `...RuntimeElementsClasses` variants with **no files**; the consumer selected one and `compileTestKotlin` failed with "Unresolved reference 'fx'" (measured, first run).
3. Add the main module as a fixtures dependency: `"jvmTestFixturesApi"(project(project.path))`. The plugin does this only when the `java` plugin is present (F2), so in KMP the first publication had **no dependency on the main module**.
4. KGP 2.2.0 only: `testFixturesRuntimeElements` is published with **no dependencies** (KGP wires only `testFixturesApiElements`; see the side effect in F1). Workaround: `configurations.testFixturesRuntimeElements { extendsFrom(jvmTestFixturesApi, jvmTestFixturesImplementation, jvmTestFixturesRuntimeOnly) }`. On KGP 2.2.21 and 2.4.21 the runtime variant is complete without it (measured); it then also lists the main `okhttp-jvm`, because the runtime elements extend the main compilation's implementation as well.
5. Set the published JVM version: with `jvmTarget` 11 alone the fixtures variants declared `org.gradle.jvm.version: 21` (the Gradle JVM). `java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }` gives `11`.

Result: the `.module` has `testFixturesApiElements` and `testFixturesRuntimeElements` (jar, attributes `java-api` / `java-runtime`, `org.gradle.jvm.version: 11`, files `fxlib-1.0.0-test-fixtures.jar`, capability `probe.fx:fxlib-test-fixtures:1.0.0`) plus the three default `*-published` variants. Class files in the fixtures jar are major version 55. Resources placed in `src/jvmTestFixtures/resources/fixtures/a.json` are in the jar.

### F6. Measured recipe B: a dedicated `testFixtures` compilation, no `java-test-fixtures` plugin

Probe `p2` (same toolchain). Shape (about 40 lines; the full build file is in the probe):

```kotlin
kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        val main = compilations.getByName("main")
        val fixtures = compilations.create("testFixtures") { associateWith(main) }
        compilations.getByName("test").associateWith(fixtures)
        val fixturesJar = tasks.register<Jar>("testFixturesJar") {
            archiveClassifier.set("test-fixtures"); from(fixtures.output.allOutputs)
        }
        // two consumable configurations, "testFixturesApiElements" (java-api) and
        // "testFixturesRuntimeElements" (java-runtime): category library, libraryelements jar,
        // bundling external, TargetJvmVersion 11, extendsFrom jvmTestFixtures{Api,Implementation,RuntimeOnly},
        // outgoing { capability("$group:${project.name}-test-fixtures:$version"); artifact(fixturesJar) }
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        project.extensions.getByType<KotlinMultiplatformExtension>().publishing {
            adhocSoftwareComponent {
                addVariantsFromConfiguration(api.get()) { mapToMavenScope("compile"); mapToOptional() }
                addVariantsFromConfiguration(rt.get()) { mapToMavenScope("runtime"); mapToOptional() }
            }
        }
    }
}
dependencies { "jvmTestFixturesApi"(project(project.path)); /* + the fixtures' own dependencies */ }
```

- Published metadata: exactly two fixtures variants (no secondary "classes" variants to skip), each listing the main module, `kotlinx-coroutines-test`, `mockwebserver3` and `kotlin-stdlib`, with `org.gradle.jvm.version: 11` and the capability.
- The approach follows a published workaround for KT-63142 by user ephemient (a per-target `testFixtures` compilation with consumable configurations and `capability("$group:${project.name}-test-fixtures:$version")`), adapted to JVM-only and given a publishing step.
- The producer's own `jvmTest` sees the fixtures with their dependencies (the `test` compilation is associated with the fixtures compilation); measured with a test that used `FakePort` and the `internal` function of `jvmMain`.
- The fixtures can read `jvmMain`'s `internal` declarations (association), which the consumer-facing jar then exposes as ordinary public classes.
- Works unchanged on KGP 2.2.21 and 2.4.21 with vanniktech 0.37.0 (measured).

### F7. Consumption, measured: file repository, composite build, classpath isolation

Consumer: `kotlin("jvm")` 2.2.0, Gradle 9.7.1, `jvmToolchain(21)`, `testImplementation(testFixtures("probe.fx:fxlib:1.0.0"))`, one test using the fake.

- **Published (file repository), recipes A and B:** compiled and ran (`HELLO=fake:...`). `dependencyInsight` shows the fixtures variant selected with `org.gradle.jvm.version` provided 11 and requested 21, and the transitive jvm module `fxlib-jvm` pulled by the fixtures' dependency on the main module.
- **Composite (`includeBuild("../producer")`):** compiled and ran for recipe A (producer = root project) and recipe B (producer = root project, and producer = subproject `:fxlib` of an included multi-project build `wt`). The graph shows `probe.fx:fxlib:1.0.0 -> project ':producer'` carrying the fixtures and their dependencies. So BotTools' finding that substitution carries the capability selector holds for a KMP producer, with both mechanisms.
- **Isolation:** a second consumer with only `implementation("probe.fx:fxlib:1.0.0")` resolves `fxlib-jvm` and `okhttp-jvm` on its runtime classpath and only `kotlin-stdlib` + `fxlib-jvm` on its compile classpath; neither `kotlinx-coroutines-test` nor `mockwebserver3` appears on either. The fixtures variants are the only place those two are declared.
- **Maven consumers** see the root POM and a plain classifier jar: Gradle documents that without Gradle Module Metadata "resolution will fail" for `testFixtures(...)`.

### F8. Root POM content (measured)

Using `mapToMavenScope(...)` + `mapToOptional()`, the root `webtools-http` POM lists `kotlinx-coroutines-test` and `mockwebserver3` with `<scope>compile</scope><optional>true</optional>`. Optional dependencies do not propagate in Maven, and Gradle's own `java-test-fixtures` publication maps the same way (Gradle's `JavaConfigurationVariantMapping("compile", true, ...)`). Without `mapToOptional()` (probe `p5`), the two appear as ordinary `compile` dependencies of the root POM. They are not in `webtools-http-jvm`'s POM at all. Gradle also prints "Declares capability ... which cannot be mapped to Maven" for each fixtures variant; `suppressPomMetadataWarningsFor("testFixturesApiElements")` (and the runtime one) silences it, as vanniktech does for its own fixtures support.

### F9. vanniktech `maven-publish` does nothing for fixtures in a KMP module, and does not need to (documented source plus measured)

In 0.36.0 and 0.37.0, `setupTestFixtures` (which calls `addTestFixturesSourcesJar`, `suppressPomMetadataWarningsFor(...)` and `fixTestFixturesMetadata`) is invoked from the `JavaLibrary`, Kotlin JVM and similar platform classes, and not from `KotlinMultiplatform.configure`. Its sources-jar workaround looks up `project.components.findByName("java")`, which a KMP project does not have. With `configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))` and `signAllPublications()` I measured, on both versions:

- the staging output contains `<module>-<v>-test-fixtures.jar` together with `.asc`, `.md5`, `.sha1`, `.sha256`, `.sha512` for it (the signing plugin signs every artifact of the publication, including the adhoc component's classifier jar), and `.module.asc`;
- root and `-jvm` publications keep their sources and (empty) javadoc jars;
- the fixtures jar has **no** `-test-fixtures-sources.jar` or `-javadoc.jar`.

I did not run the Central upload (see Known gaps). Central's published rule is stated for `<artifactId>-<version>.jar`; Central hosts Gradle-published `-test-fixtures.jar` artifacts that have no sources or javadoc of their own (F10).

`fixTestFixturesMetadata` (Gradle issue #23354, open: the capability takes the project's group and version rather than the publication's) matters only if `coordinates()` differs from the project's group, name and version. In this repo `coordinates(group.toString(), "webtools-http", version.toString())` equals the project's values, so the capability `webtools-http-test-fixtures` comes out right. The capability name is `<project.name>-test-fixtures`, so the Gradle project name must equal the artifactId.

### F10. Sources and javadoc for the fixtures, Central (documented, observed practice)

Gradle itself publishes no sources jar for fixtures: vanniktech's source says "Gradle currently doesn't publish a sources jar for test fixtures and the APIs to add one are internal", referring to [gradle/gradle#20539](https://github.com/gradle/gradle/issues/20539) (open). Central's rule: "Projects with packaging other than pom have to supply JAR files that contain Javadoc and sources ... For every `<artifactId>-<version>.jar` file, you must provide a corresponding `<artifactId>-<version>-sources.jar` and a `<artifactId>-<version>-javadoc.jar`." plus a `.asc` and checksums for every deployed file. Observed practice: `org.opensearch:opensearch-remote-metadata-sdk:3.7.0.0` on Central lists `-javadoc.jar`, `-sources.jar`, `-test-fixtures.jar`, `.jar`, `.module`, `.pom` and no sources or javadoc for the fixtures.

### F11. Explicit API mode does not reach the fixtures compilation (measured)

With `explicitApi()` set, `compileKotlinJvm` failed on undeclared visibility in `commonMain`/`jvmMain`, but `compileTestFixturesKotlinJvm` accepted `class FakePort : Port` with no modifier, in both recipes. Level-2 KDoc and visibility on the fixtures are therefore a review and tooling matter, not a compiler check.

### F12. What belongs in common code (measured plus documented)

`mockwebserver3` 5.5.0 publishes `apiElements`/`runtimeElements` with `"org.jetbrains.kotlin.platform.type": "jvm"` and `"org.gradle.jvm.version": 8`: it is JVM-only. File-based fixtures (classpath resources) and an env-gated recorder need `java.io`/`System.getenv`, so they are JVM too. `kotlinx-coroutines-test` 1.10.2 is multiplatform (variants with platform types `common`, `native`, ...), so the shared-time-source part (virtual time for `delay`) could be common logic. But no mechanism turns a common source set into part of a Gradle fixtures variant: the only fixtures compilation is the JVM one (KT-63142 asks for the rest). Putting any of it in `commonMain` would ship it in the main artifact, which violates "nothing reaches the main variants". Result: the entire test support, including any pure-logic fake, lives in the JVM fixtures compilation.

## How comparable projects do it

| Project | What ships | Mechanism |
|---|---|---|
| `kotlinx-coroutines-test` 1.11.0 (2026-05-08) | KMP test artifact | separate Maven artifact |
| Ktor `ktor-client-mock` 3.6.0 (2026-09-16) | `MockEngine` | separate KMP artifact |
| Okio `okio-fakefilesystem` 3.18.2 (2026-09-04) | `FakeFileSystem` | separate KMP artifact |
| Turbine 1.2.1 (2025-06-11) | Flow testing | separate KMP artifact |
| Apollo Kotlin `apollo-testing-support` 5.2.0 (2026-09-16), `apollo-mockserver` | test helpers | separate artifacts |
| SpartanGraphics (`docs/roadmap/vision-and-roadmap.md:63,100`) | `graphics-test`, a KMP module holding fakes and conformance suites | separate module |
| JVM-only libraries (Gson is Gradle's documentation example; OpenSearch publishes `-test-fixtures.jar`) | fixtures variant | `java-test-fixtures` + `maven-publish` |

Versions and dates are from each artifact's `maven-metadata.xml` on Maven Central, read 2026-10-20. Every KMP library I checked ships test support as a separate artifact, because the build tooling offers it nothing else (F3). **I found no published KMP library that ships a Gradle test-fixtures variant**; the nearest are user build scripts in YouTrack KT-63142 (ephemient's per-target compilation; hfhbd's adhoc component in the August 2026 comment, which is recipe A and is the same pattern I measured) and KGP's own integration test project. Absence is not proven: GitHub and Central searches for the pattern returned nothing, but the search endpoints I could use were limited.

## Pitfalls & regrets

1. **Secondary variants.** Publishing the plugin's `*Classes`/`*Resources` variants makes consumers fail at compile time (empty variants win selection). Skip them (recipe A) or do not create them (recipe B).
2. **Missing main-module dependency.** In KMP the fixtures variant does not depend on the main module unless you declare it (F2); a consumer using only `testFixtures(...)` would lack the interfaces the fakes implement.
3. **Source directory and configuration names differ from JVM projects.** `src/jvmTestFixtures/...`, not `src/testFixtures/...` (KT-88423 shows Kotlin under the latter is silently ignored in KMP); `jvmTestFixturesApi`, not `testFixturesApi`, on KGP 2.2.0 (KT-77466).
4. **KGP 2.2.0 runtime variant without dependencies** (recipe A). A consumer would get the fakes but not `mockwebserver3` or `kotlinx-coroutines-test` at runtime; the consumer's test failed at runtime in the probe's second run (I did not capture the exception; a missing class is the expected cause).
5. **POM leakage.** Forgetting `mapToOptional()` puts test libraries in the root POM as compile dependencies.
6. **JVM version attribute.** It defaults to the JVM running Gradle (21 here), not `jvmTarget`. A consumer on JDK 17 would reject a variant that said 21. Set it explicitly and assert it in the build's publication check.
7. **Eager capability version.** In recipe B, `capability("$group:${project.name}-test-fixtures:$version")` is evaluated when the script runs; it is right only if `group` and `version` are set earlier. This repo sets `version` at the top of each module's script and `group` in the root `allprojects` block, so it holds, but a late `version = ...` would silently produce a stale capability.
8. **Experimental publishing API.** `kotlin.publishing.adhocSoftwareComponent` needs `@OptIn(ExperimentalKotlinGradlePluginApi::class)` and is still experimental in KGP 2.4.21. Both recipes depend on it. A KGP upgrade must re-run the resolution checks.
9. **No first-class support means no IDE guarantee.** I did not test IntelliJ import of the `jvmTestFixtures` source set (Known gaps).
10. **Only the JVM target gets fixtures.** Adding a native or JS target later does not break this, but those targets get no fixtures, and any fake that must run there would need a separate artifact.

## Recommendation for this repository

**Build `webtools-http`'s test support as a JVM-only `testFixtures` compilation published as a variant of the root module, using recipe B, with recipe A as the fallback.** Reasoning: both publish the identical consumer contract (capability `webtools-http-test-fixtures`, `testFixtures(...)`, GMM variants in the root `.module`, a `-test-fixtures.jar`), so the choice is invisible to consumers and can be swapped later (for example when KGP gains first-class support) without a version break. B uses only documented compilation APIs (`compilations.create`, `associateWith`) plus the one experimental publishing API both need; A additionally relies on a Gradle feature that is half-active without the `java` plugin, needs a re-implemented internal mapping, and needs a KGP-2.2.0-only workaround. The repo's Kotlin is pinned at 2.2.0, so A's workaround would live in the build for as long as the pin does.

How it would sit in this repo:

- `webtools-http` is applied with `kotlin("multiplatform")` 2.2.0. The root `build.gradle.kts` currently applies `org.jetbrains.kotlin.jvm` to every subproject (`build.gradle.kts:18`), puts `api(slf4j-api)` and `testImplementation(...)` in a `subprojects` block (`:29`, `:30`), and builds the per-level `Test` tasks from `sourceSets["test"]` (`:58`, `:59`), so that block cannot be applied to a KMP module unchanged; the mixed setup is outside this question and is flagged in Known gaps. `kotlin("multiplatform")` and `kotlin("jvm")` can share one `plugins { ... apply false }` block at the same version.
- Fixture sources go in `webtools-http/src/jvmTestFixtures/kotlin` and `.../resources`; the fixtures' dependencies (`kotlinx-coroutines-test` 1.10.2, `mockwebserver3` 5.5.0) go on `jvmTestFixturesApi`/`jvmTestFixturesImplementation`; the main module goes on `jvmTestFixturesApi(project(project.path))`.
- Set `TargetJvmVersion` 11 on the two outgoing configurations from the same root-level constant that sets `jvmTarget` (so the published attribute cannot drift from the bytecode level), map both variants with `mapToMavenScope` + `mapToOptional()`, and silence the capability warning with `suppressPomMetadataWarningsFor` on both variant names.
- Keep vanniktech at 0.36.0; no change is needed. `configure(KotlinMultiplatform(javadocJar = ..., sourcesJar = ...))` as in GeneralTools; the fixtures jar ships without sources or javadoc.
- Add a build-level publication check (a Level-3 or build-test equivalent) that publishes to a file repository and resolves `testFixtures(...)` once from it and once through `includeBuild`, asserting the capability, `org.gradle.jvm.version == 11`, and that the main variants list neither `kotlinx-coroutines-test` nor `mockwebserver3`. This is exactly the probe; it is the only thing that will notice a KGP or Gradle upgrade changing the experimental publishing behaviour.
- Treat everything in the fixtures as JVM; do not put any of it in `commonMain` or `commonTest`.

### Considerations

1. **Recipe A or B.** Options: A (plugin; JetBrains documents the plugin as compatible and tests the compilation side) or B (explicit; fewer plugin-version-specific workarounds). Recommendation: B, because the pinned KGP 2.2.0 carries a known A-only defect and the published contract is identical. This needs a call if the organisation prefers the conventional plugin.
2. **Optional test dependencies in the root POM.** Options: accept `optional` entries for `kotlinx-coroutines-test` and `mockwebserver3` in the root POM (the same mapping Gradle's plugin publishes), or post-process the POM to drop them (more fragile). Recommendation: accept; Gradle consumers are the target and optional dependencies do not propagate.
3. **No sources or javadoc for the fixtures.** Options: ship none (matches Gradle's plugin and Central practice), or publish an extra `-test-fixtures-sources.jar` through an additional adhoc variant and artifact. Recommendation: ship none now. The Level-2 KDoc requirement on fixtures then has to be enforced by review, since `explicitApi()` does not cover them (F11).

## Known gaps

- **Not run against Central.** I did not run vanniktech's upload (`publishToMavenCentral`) or the Portal validator; doing so would publish. The evidence that a `-test-fixtures.jar` without its own sources/javadoc passes is Central's published rule (stated for `<artifactId>-<version>.jar`) plus existing artifacts on Central (F10). A dry run through the real validator, or a first prerelease, is the only way to settle it.
- **IDE behaviour untested.** I did not open the probes in IntelliJ IDEA; whether the `jvmTestFixtures` source set and the fixtures compilation import and resolve cleanly is unverified.
- **Mixed-plugin root build not designed here.** The root build applies `kotlin("jvm")` to all subprojects and builds level tasks from the Java `test` source set; how `webtools-http` (KMP) and `webtools-feeds` coexist with the three JVM modules, including the five-level test tasks on a KMP `jvmTest` compilation, is outside this question and unprobed. Probes used a standalone KMP producer and a standalone JVM consumer.
- **`internal` visibility from fixtures was measured with a single `internal` top-level function**, not with the real module's mix of `internal` classes and `explicitApi`.
- **Gradle consumer variants outside the tested range.** I tested a Kotlin/JVM consumer on JDK 21 with Kotlin 2.2.0 and Gradle 9.7.1 only. A Kotlin Multiplatform consumer's JVM target requesting `testFixtures(...)` was not probed. The consumer's JDK 11/17 case was reasoned from the attribute (provided 11 ≤ requested), not run.
- **Recipe A in a composite build whose producer is a subproject** was not probed (A: root-project producer only; B: both). The mechanism is the same, so I expect it to behave identically, but it is unmeasured.
- **No published KMP library with a fixtures variant found.** Absent from my searches, not proven absent.
- **Not examined:** abiValidation (`webtools-http` does not list it), Dokka, the configuration cache with these build scripts (the probes ran with the configuration cache enabled, which Gradle 9.7.1 did without any property being set, and entries were stored and reused, but I did not test invalidation), and Gradle 10 compatibility ("Deprecated Gradle features were used" appeared in probe output; I did not attribute them).
- **Probe artifacts lived in throwaway projects outside the repository and are not preserved:**
  - `p1`: recipe A;
  - `p2`: recipe B;
  - `p3`: A with vanniktech;
  - `p4-*` and `p7-*`: newer KGP;
  - `p5`: unmapped POM;
  - `p6`: subproject composite.

  The signing tests used a throwaway PGP key that signs nothing real.

## Load-bearing claims

1. **Recipe A is compatible with the multiplatform plugin from Kotlin 2.1.21.** "If you use the Java test fixtures Gradle plugin with Kotlin 2.1.20 and a Gradle version higher than 8.7, the plugin doesn't work. Instead, upgrade to Kotlin 2.1.21 where this issue is resolved." <https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html> (read 2026-10-20).
2. **JetBrains commits to `java-base` in multiplatform projects.** "At a minimum, we will support the compilation of Java sources and using Gradle's java-base plugin within your multiplatform projects." Same page and date.
3. **`java-library`/`java`/`application` are an error with KMP on Gradle ≥ 8.7.** `val severity = if (GradleVersion.current() >= GradleVersion.version("8.7")) ERROR else WARNING` and `project.plugins.withId("java-library") {`. <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/diagnostics/KotlinToolingDiagnostics.kt> and <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/ApplyJavaBasePluginSetupAction.kt>, tag v2.2.0.
4. **The Gradle test-fixtures plugin applies only `JavaBasePlugin` and wires components only under `java`.** `project.getPlugins().apply(JavaBasePlugin.class);` and `project.getPluginManager().withPlugin("java", plugin -> {`, with `// Test fixtures depend on the project.` inside the `java` branch. <https://github.com/gradle/gradle/blob/v9.7.1/platforms/jvm/plugins-jvm-test-fixtures/src/main/java/org/gradle/api/plugins/JavaTestFixturesPlugin.java>, tag v9.7.1.
5. **KGP associates the fixtures compilation with `main` and `test` with the fixtures.** `testFixturesCompilation.associateWith(mainCompilation)` and `testCompilation.associateWith(testFixturesCompilation)`; comment `// Only applicable for KMP`. <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/jvm/KotlinJvmTargetTestFixturesSideEffect.kt>, tag v2.2.0.
6. **The adhoc component adds variants to the root publication; default variants cannot be modified.** "Returns [AdhocComponentWithVariants] that can be used to add additional variants to root Kotlin Multiplatform Publication" and "It is not possible to modify or replace default Kotlin Multiplatform publication variants." <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin-api/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinPublishing.kt>, tag v2.2.0 (`@since 2.1.20`, `@ExperimentalKotlinGradlePluginApi`; unchanged at v2.4.21).
7. **The feature is Experimental.** "You cannot modify existing Gradle variants with this feature. This feature is Experimental." <https://kotlinlang.org/docs/whatsnew2120.html>, Kotlin 2.1.20.
8. **No first-class KMP fixtures support is planned.** KT-63142: "should be extended to support not only JVM target, but also JS, Native, WASM." State "To be discussed", updated 2026-08-04. <https://youtrack.jetbrains.com/issue/KT-63142>. KT-69482: "However, both of these features fall over on KMP projects that contain JVM or android source sets. This is a feature request to support that use case too." State Backlog, updated 2026-08-07. <https://youtrack.jetbrains.com/issue/KT-69482>.
9. **Fixtures bugs fixed at stated versions.** KT-75808 "KGP: MPP with jvm target and Gradle java-test-fixtures is broken", Available in 2.2.0-Beta2, 2.1.21-RC. <https://youtrack.jetbrains.com/issue/KT-75808>. KT-77466 "KMP - testFixturesApi and similar configurations do not affect jvmTestFixtures source set", Available in 2.2.20-Beta2, 2.2.10-RC. <https://youtrack.jetbrains.com/issue/KT-77466>.
10. **`src/testFixtures/kotlin` is ignored in KMP (open).** "Kotlin files placed under `src/testFixtures/kotlin` are silently ignored during build passes". <https://youtrack.jetbrains.com/issue/KT-88423> (created 2026-08-07, State Submitted).
11. **Gradle's published fixtures shape.** "By convention, test fixtures will be published with an artifact having the test-fixtures classifier." and "Gradle automatically creates test fixtures variants with a capability whose name is the name of the main component, with the appendix -test-fixtures." and "if the external project is not publishing Gradle Module Metadata, then resolution will fail". <https://docs.gradle.org/9.7.1/userguide/java_testing.html>, Gradle 9.7.1.
12. **vanniktech's fixtures workarounds are tied to java-test-fixtures on Java/JVM platforms; no sources jar from Gradle.** `project.plugins.withId("java-test-fixtures") {` and `// test fixtures can't be mapped to the POM because there is no equivalent concept in Maven` (Platform.kt, `setupTestFixtures`); "Gradle currently doesn't publish a sources jar for test fixtures and the APIs to add one are internal." (workaround/TestFixtures.kt). <https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/Platform.kt>, <https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/workaround/TestFixtures.kt>, tag 0.37.0 (the 0.36.0 tag has the same files).
13. **Gradle issues on fixtures publication are open.** #20539 "Test fixtures plugin should support exposing source/javadoc elements outgouing variant" (open, updated 2025-11-06); #23354 "ProjectDerivedCapability does not get rewritten by GenerateModuleMetadata" (open, updated 2024-07-18). <https://github.com/gradle/gradle/issues/20539>, <https://github.com/gradle/gradle/issues/23354>.
14. **Central's sources/javadoc and signing rules.** "Projects with packaging other than pom have to supply JAR files that contain Javadoc and sources." and "For every <artifactId>-<version>.jar file, you must provide a corresponding <artifactId>-<version>-sources.jar and a <artifactId>-<version>-javadoc.jar." and "All files deployed need to be signed with GPG/PGP and a .asc file containing the signature must be included for each file." <https://central.sonatype.org/publish/requirements/> (read 2026-10-20).
15. **Central hosts a Gradle-published `-test-fixtures.jar` with no sources or javadoc of its own.** Directory listing of `org.opensearch:opensearch-remote-metadata-sdk:3.7.0.0` shows `...-javadoc.jar`, `...-sources.jar`, `...-test-fixtures.jar`, `....jar`, `....module`, `....pom`. <https://repo1.maven.org/maven2/org/opensearch/opensearch-remote-metadata-sdk/3.7.0.0/> (read 2026-10-20).
16. **`mockwebserver3` 5.5.0 is JVM-only.** Its module metadata declares `"org.jetbrains.kotlin.platform.type": "jvm"` on `apiElements` and `runtimeElements`. <https://repo1.maven.org/maven2/com/squareup/okhttp3/mockwebserver3/5.5.0/mockwebserver3-5.5.0.module> (read 2026-10-20).
17. **`kotlinx-coroutines-test` 1.10.2 is multiplatform.** Its module metadata lists variants with platform types `common` (`metadataApiElements`) and `native` (e.g. `iosArm64ApiElements-published`). <https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-test/1.10.2/kotlinx-coroutines-test-1.10.2.module> (read 2026-10-20).
18. **`withJava()` is an error on Gradle ≥ 9.0.** `val severity = if (GradleVersion.current() >= GradleVersion.version("9.0")) ERROR else WARNING` in `KMPWithJavaDiagnostic`. Same KGP file as claim 3, tag v2.2.0.
