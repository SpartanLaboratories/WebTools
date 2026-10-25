# The `webtools.kotlin-common-check` plugin and the check-only `js` module

## Question

What does an unpublished, check-only Kotlin Multiplatform module with a `js` target need so that it compiles a sibling module's `commonMain` directory for `js` (a JVM API in that directory fails the compile, an `expect` without a `js` `actual` fails the compile), and what does it do to the repository's root build? The answer fixes the content of the precompiled script plugin `webtools.kotlin-common-check` in `build-logic`, what each check module's build file adds, what `./gradlew build` then runs, downloads and writes, and what the plugin's TestKit class asserts.

Requirements the answer meets:

- Kotlin Gradle plugin (KGP) 2.2.0 on Gradle 9.7.1, configuration cache on and reused.
- Nothing is published: no vanniktech, and the plugin does not apply `webtools.published-library`.
- `./gradlew build` downloads no Node.js or Yarn and writes no `kotlin-js-store/`.
- The shared directory is taken from the sibling without cross-project model access that breaks the configuration cache.
- Every `expect` in the shared directory gets a `js` `actual`: a no-op `internal actual fun logger(name: String): Log` and an epoch-milliseconds reading.

## Bottom line

- **The stated first option does not meet the requirements.** `js { nodejs { testTask { enabled = false } } }` still downloads Node.js v22.0.0 and Yarn 1.22.17 during `./gradlew build`, runs `kotlinNpmInstall` and writes `kotlin-js-store/yarn.lock` (measured). Gradle's rule is the cause: disabling a task does not skip the tasks it depends on, and KGP wires the `js` test task to Node/Yarn set-up and npm install. The same holds for `js { browser { testTask { enabled = false } } }`.
- **Use `js()` with no environment.** It compiles to a klib, creates no test task and no Node/Yarn/npm task, writes no `kotlin-js-store/`, and leaves the root project untouched: no plugin, no extension, no task (measured against a root with no plugin, as in this repository). The cost is one KGP warning per build, "JS Environment Not Selected", which says the omission becomes an error in a future release; the source shows it is still a warning in KGP 2.4.21.
- **Any `nodejs()`/`browser()` call changes the root project.** In KGP 2.2.0 it applies `NodeJsRootPlugin` to the root, which applies `BasePlugin`, the Node.js and Yarn plugins, six extensions and about 18 tasks, including root `build`, `check`, `assemble` and `clean`. The next unit's assumption that the root has no `check` task holds only with `js()` and no environment.
- **The plugin is a few lines.** It applies the multiplatform plugin, declares `mavenCentral()`, calls `js()` and wires `check` to `compileKotlinJs` (the compile is otherwise only in `assemble`). Each check module adds the sibling's `commonMain` directory as a path relative to itself (configuration-cache safe, no `project(...)` call), the coroutines dependency and `kotlin.compilerOptions.optIn`. The typed extension adds nothing worth its surface.
- **The TestKit class has seven tests in the integration package, plus one configuration-cache test in the non-functional package.** Warm, the integration class takes about 12 to 17 s; with an empty TestKit home about 34 s. The increment in the TestKit home is two klibs, about 3.6 MB (inferred from the artifact list).

## Findings

Evidence labels: **documented** (official documentation or Javadoc), **source-read** (KGP source at a tag), **measured** (a throwaway probe; setup in *Load-bearing claims*, items M1 to M9).

### F1. `nodejs { }` and `browser { }` reach the root project through `NpmResolverPlugin`; the chain differs slightly from "known fact" (source-read, KGP v2.2.0)

- `KotlinJsIrTarget` creates the `nodejs` and the `browser` sub-target inside lazies that both first evaluate `commonLazy`. `commonLazy` calls `NpmResolverPlugin.apply(project)` (through `webTargetVariant`).
- `NpmResolverPlugin.apply` builds an `NpmResolverPluginApplier` whose first argument is `{ NodeJsRootPlugin.apply(project.rootProject) }`.
- `NodeJsRootPlugin` hands over to `NodeJsRootPluginApplier.apply`, which runs `project.plugins.apply(BasePlugin::class.java)` on the root, creates the `kotlinNodeJs` and `kotlinNpm` extensions, registers the root tasks, and applies the Yarn plugin when `kotlin.js.yarn` is on (its default).
- So the root-project effect is not tied to `nodejs`; `browser { }` has it too. The `js()` call alone does not evaluate `commonLazy` (F6).
- Sources: `KotlinJsIrTarget.kt`, `NpmResolverPlugin.kt`, `NodeJsRootPlugin.kt`, `NodeJsRootPluginApplier.kt` (links in claims 2 to 5).

### F2. The `js` test task depends on Node.js and Yarn set-up and npm install, and a disabled task still pulls them in (documented + source-read + measured)

- Source-read: `KotlinNodeJsIr.configureTestDependencies` makes the test task depend on `nodeJsSetupTaskProvider`, `nodeJsRoot.npmInstallTaskProvider`, the package manager's post-install tasks (`kotlinStoreYarnLock`), `binary.linkSyncTask` and `binary.linkTask`. `KotlinJsIrSubTarget.configureTestsRun` registers that test task as soon as the sub-target exists, and `testTask { }` only configures it afterwards.
- Documented: "Note that disabling a task does not prevent the execution of the tasks which this task depends on." (Gradle Javadoc, claim 1).
- Measured (M1): with the plugin of option 1, `./gradlew build --dry-run` lists `:kotlinNodeJsSetup`, `:kotlinYarnSetup`, `:kotlinNpmInstall`, `:rootPackageJson`, `:kotlinStoreYarnLock` and `:<check>:kotlinNodeJsSetup` before `:<check>:jsNodeTest SKIPPED`; the real run executed them, put `nodejs/node-v22.0.0-win-x64` and `yarn/yarn-v1.22.17` in the Gradle user home, wrote `kotlin-js-store/yarn.lock` at the repository root and created `build/js/` under the root project. The earlier measurement ([`../../research/kmp-build-topology.md`](../../research/kmp-build-topology.md) F3) ran only `compileKotlinJs`, which has none of these dependencies, so it found no download.
- Measured (M4): `js { browser { testTask { enabled = false } } }` lists the same Node/Yarn/npm tasks in `build --dry-run`.

### F3. Clearing the test task's dependencies avoids the downloads but not the root-project changes (measured)

- Setup (M2): `nodejs { testTask { enabled = false; setDependsOn(emptyList<Any>()) } }`. The `testTask` action runs after KGP's own wiring, so the clear takes effect.
- Result: no Node.js/Yarn download, no `kotlin-js-store/`, `jsNodeTest SKIPPED`. But the root still gains `BasePlugin`, the Node.js and Yarn plugins, six extensions and the root tasks of F5, and `build` still runs the root tasks `kotlinKotlinNpmCachesSetup`, plus `jsPackageJson`, `jsPublicPackageJson`, `jsTestPackageJson` in each check module, writing `build/js/packages/` under the root project.
- It also depends on KGP's internal wiring order. It stays a viable fallback if KGP turns the missing environment into an error (Consideration 1).

### F4. `js()` with no environment is clean (measured, source-read)

- Measured (M3): the root has the same plugins, extensions and tasks as without any check module (an empty diff of `tasks --all` for the root); `build --dry-run` lists only `<check>:` tasks besides the sibling's; no Node.js/Yarn directory appears in the Gradle user home; no `kotlin-js-store/`; the root `build/` holds only reports.
- The check module's tasks (`tasks --all`): `compileKotlinJs`, `jsJar` (a `.klib` in `build/libs/`), `allMetadataJar`, `generateProjectStructureMetadata`, `jsProcessResources`, `jsTest` (NO-SOURCE), `allTests` (NO-SOURCE), `check`, `build`, `clean` and the KGP IDE/ABI helpers; no `publish*`, `sign*`, `generatePomFile*` or `generateMetadataFile*` task.
- Source-read: `JsEnvironmentChecker` reports `JsEnvironmentNotChosenExplicitly` (severity WARNING) when neither `browser()` nor `nodejs()` is configured on a `js` target. The text says "Not choosing any of them will be an error in the future releases." The same factory is still `ToolingDiagnosticFactory(WARNING, ...)` at v2.4.21, the newest stable tag (claim 6).
- Measured: the diagnostic prints once per build, however many check modules exist, only on a configuration-cache miss, and not at all when the cache is reused.
- Measured: `-Pkotlin.suppressGradlePluginWarnings=JsEnvironmentNotChosenExplicitly` removes it (the value is the short name, not the long `KOTLIN:KGP:...` id). It is a project property read by `PropertiesProvider`, so only `gradle.properties` or `-P` can carry it, repository-wide.

### F5. What the root project gains, by option (measured)

| | plain `nodejs`+disabled test (M1) | `nodejs`+cleared deps (M2) | `js()` no environment (M3) |
|---|---|---|---|
| Root plugins gained | `BasePlugin`, `LifecycleBasePlugin`, `NodeJsRootPlugin`, `NodeJsPlugin`, `YarnPlugin` | same | none |
| Root extensions gained | `base`, `kotlinNodeJs`, `kotlinNodeJsSpec`, `kotlinNpm`, `kotlinYarn`, `kotlinYarnSpec` | same | none |
| Root tasks gained | `assemble`, `build`, `check`, `clean`, `kotlinKotlinNpmCachesSetup`, `kotlinNodeJsSetup`, `kotlinNpmInstall`, `kotlinPackageJsonUmbrella`, `kotlin{Restore,Store,Upgrade}{PackageLock,YarnLock}`, `kotlinYarnSetup`, `nodeKotlinClean`, `yarnKotlinClean`, `rootPackageJson` | same | none |
| Node.js and Yarn downloaded by `build` | yes | no | no |
| `kotlin-js-store/` written | yes (`yarn.lock`) | no | no |
| Root `build/` written | `build/js/` (node_modules, packages, `yarn.lock`) | `build/js/packages/` | reports only |
| Warnings under `--warning-mode all` | the `archives` deprecation (below) | same | the `archives` deprecation, and "JS Environment Not Selected" |

- The `archives` deprecation ("The archives configuration has been deprecated for artifact declaration. This will fail with an error in Gradle 10.", KT-78620) is already printed for every `jvm()` module; KGP 2.2.0 raises it at `js()` too, so it is not new, and gradle prints it once per build. The probe reports it at the plugin script's `kotlin { js() }` line (M3).

### F6. What the commands run from the repository root with `js()` and no environment (measured)

Dry-run task graphs of the probe (root with no plugin, a `jvm()` multiplatform module, two check modules):

- `./gradlew build`: for each check module, `compileKotlinJs`, `jsJar`, `allMetadataJar`, `jsTest`/`allTests` (both NO-SOURCE), `check`, `build`. No root task.
- `./gradlew check`: `jsTest`, `allTests`, `check` only, **unless** the plugin wires the compile in. Without that wiring `check` passes without compiling anything, because `compileKotlinJs` belongs to `assemble` through `jsJar`. With `tasks.named("check") { dependsOn("compileKotlinJs") }` the compile is in `check`.
- `./gradlew test`: no check-module task (KGP creates no `test` task for a multiplatform module, and the check module registers no lifecycle `test`); only the sibling's `test`.
- `./gradlew assemble`: the compile and the klib jar per check module.
- `./gradlew clean`: `clean` per check module; no root `clean`.
- `./gradlew tasks`: the root listing is identical to the listing without the check modules; the check modules' tasks appear under their project prefix in `tasks --all`.
- Level tasks (`componentTest` and so on): not registered in a check module, so they do not run there.
- Cold, with the configuration cache missing: `build` of the whole probe, 28 tasks, 9 to 10 s including `build-logic` compilation. Warm: "Reusing configuration cache", 0.8 s, `compileKotlinJs` UP-TO-DATE.

### F7. The shared directory and the configuration cache (measured)

- `kotlin.srcDir("../webtools-http/src/commonMain/kotlin")` in the check module's `commonMain` is a path relative to the check project: no `project(...)` call, nothing read from another project's model. The second `build` reuses the configuration cache.
- `kotlin.srcDir(project(":webtools-http").layout.projectDirectory.dir("src/commonMain/kotlin"))` also stores and reuses the configuration cache; it reads another project's `layout`. Gradle's Isolated Projects would reject it, but the repository's root `allprojects { group; version }` (`build.gradle.kts:10`) already fails Isolated Projects, so the choice is only about coupling: the relative path needs no evaluation-order assumption.
- A change in the sibling's `commonMain` re-runs `compileKotlinJs`; a JVM API added there fails it (F8).

### F8. What the compile rejects, and the message to assert on (measured)

- `import java.io.File`, `String.format(...)` and `System.currentTimeMillis()` in the shared directory: `compileKotlinJs FAILED` with `Unresolved reference 'java'.`, `'format'`, `'System'`, naming the shared file. The sibling's `jvm()` compile accepts the same file, which is why the check exists.
- An `expect fun` with no `js` `actual`: `Expected function 'logger' has no actual declaration in module <commonMain> for JS` (the TestKit sample asserts on the stable fragment `has no actual declaration`).
- An `actual` whose `expect` is not in the compiled sources (for example a mistyped sibling path) also fails. A check module that has no `actual` at all and a mistyped path compiles nothing and reports `NO-SOURCE`, not a failure (Pitfalls).

### F9. The `actual`s compile as written (measured, source-read)

- `kotlin.js.Date.now()` exists in the Kotlin/JS standard library at v2.2.0: `public external class Date()` with `public companion object { public fun now(): Double }`, in package `kotlin.js` (claim 7). It returns a `Double`; `.toLong()` is exact for epoch milliseconds (about 1.7e12, far below 2^53).
- `internal actual fun epochMillisNow(): Long = Date.now().toLong()` and `internal actual fun logger(name: String): Log = NoOpLog` compile against an `internal interface Log { fun debug(message: String, vararg args: Any?); info; warn }` in the shared directory, with no warning.

### F10. The module-wide opt-in in the check module (measured)

- A `@SubclassOptInRequired` interface implemented in the shared `commonMain` makes the `js` compile fail: `This class or interface requires opt-in to be implemented. Its usage must be marked with ... or '@OptIn(...)'.`
- `kotlin { sourceSets { commonMain { languageSettings.optIn(...) } } }` does **not** reach that compilation (still fails). `kotlin { compilerOptions { optIn.add("...") } }` and `sourceSets.all { languageSettings.optIn(...) }` do (compile passes).
- The check module has no test source, so the project-wide compiler option reaches nothing that must stay opt-in-free. `kotlin.compilerOptions.optIn` is the one-line form. Which form the main module itself uses, while its tests stay without the opt-in, is [`opt-in-gate.md`](opt-in-gate.md); its F5 reaches the same result for the check module (`jsMain` alone, or the module-wide compiler option). The same probe showed that `languageSettings.optIn` on `commonMain` alone also fails the sibling's own `jvm` compilation, so that module needs the line on `jvmMain` too.

### F11. A check module can depend on another check module (measured)

- `webtools-feeds-commoncheck` with `commonMain { dependencies { implementation(project(":webtools-http-commoncheck")) } }`, a shared directory whose public class takes the sibling's gated `WallClock`, built under `build`: the feeds compile resolved `webtools-http-commoncheck`'s `js` klib and passed. No published `js` artifact is involved. This was not in the earlier measurement and unit 10 needs it; `internal` declarations of `webtools-http` stay invisible, which matches the plan that feeds carries its own `logger`.

### F12. The TestKit harness (measured)

- The plugin needs no change to `build-logic/build.gradle.kts`: the multiplatform marker is already an `implementation` dependency (`build-logic/build.gradle.kts:13`), and `kotlin-dsl` registers the new plugin id in the plugin-under-test metadata.
- Downloads of a nested check build into the shared TestKit home: `kotlin-stdlib-js` 2.2.0 klib (2.7 MB) and `kotlin-dom-api-compat` 2.2.0 klib (0.9 MB), beyond what the existing multiplatform samples already fetch (compiler, JVM stdlib). Nothing for Node.js, Yarn or npm. A completely empty TestKit home grew to 199 MB for this class alone; I did not diff that against the existing samples, so the klib increment is an inference.
- Time, on the probe host: the integration class took 34 s with an empty TestKit home (six tests then; five nested builds), 17.5 s warm, and 12.1 s warm with the seventh test and the `check --dry-run` build added. The configuration-cache class took 10.3 s warm. Nested timings per build are not logged at the default level.
- Mutation results (each plugin variant swapped in, same class): `nodejs`+disabled test, `nodejs`+cleared dependencies and `browser`+disabled test each fail `build starts no Node, Yarn or npm task...` and `the root project gains no task`; adding `maven-publish` fails `the module has no publishing or signing task`; removing `mavenCentral()` fails four tests (the compile ones). The assertions on JVM-API and missing-`actual` failures read the nested build's output, which holds across all variants.
- The whole existing `integrationTest` suite and `BuildLogicPluginsGatingTest` still pass with the new factory and the extra plugin id in the gating list.

## How comparable projects do it

- The Kotlin documentation states the problem and no cure: a single-target project sees target-specific symbols in common code, and "as soon as you add a second target, target-specific symbols become unavailable" (claim 8). The usual way to get the check is to publish the second target. I found no public library that compiles `commonMain` for a second target in an unpublished check module; the closest practice is a published second target, which [`../../research/kmp-build-topology.md`](../../research/kmp-build-topology.md) F3 rejects for this repository (an extra coordinate and a `.module` file that points at it).
- The Kotlin documentation presents `browser {}` and `nodejs {}` as how a `js` target is configured and says the plugin "automatically downloads and installs the Node.js environment" (claim 9); `download = false` exists and makes the plugin use a pre-installed Node.js or Yarn, which would need both on every host; it does not keep `nodejs()` out of the root project.

## Pitfalls

- **`enabled = false` is not "do not run".** Any task KGP wires as a dependency of a disabled one still runs. The earlier "downloads nothing" result came from running `compileKotlinJs` alone.
- **`check` does not compile unless told to.** A KMP module with only a `js` target and no test source has `check` = `jsTest` + `allTests`, both empty. The plugin wires `compileKotlinJs` into `check`.
- **A mistyped sibling path can pass vacuously** if the check module has no `actual`s: `compileKotlinJs` is `NO-SOURCE`, not failed. Every real check module has at least the two `actual`s, which fail on a mistyped path; a fresh check module for a sibling with no `expect` would not. The TestKit class cannot see this (its sample has an `actual`); a plugin guard is possible but not recommended for two consumers (Consideration 2).
- **The dependency list is duplicated**: `kotlinx-coroutines-core:1.10.2` appears in the sibling and in its check module. A drift compiles with two different coroutines versions. The repository's build-logic uses literal coordinates and no version catalog, so the duplication is a review item, not a tool-enforced one.
- **`commonMain`'s `languageSettings.optIn` does not reach the `js` or `jvm` compilation of a module that has them** (F10). A module-wide opt-in needs the line on each main compilation's source set, or the compiler option.
- **`kotlin-js-store/` is not git-ignored** (`.gitignore` does not list it). Under the recommended plugin it is never written; if a future change adds an environment, the lock file appears at the repository root.
- **The `archives` deprecation** (KT-78620) also fires at `js()`; builds must not use `--warning-mode=fail` on KGP 2.2.0, as the multiplatform plugin's header already says.
- **Do not name a sample module `check`**: its task paths read `:check:check`. The TestKit sample names it `commoncheck`.

## Recommendation for this repository

Use `js()` with no environment, no typed extension, and wire `check` to the compile. Reasons: it is the only form that downloads no Node.js or Yarn during `build` and leaves the root project without plugins or tasks (so the root still has no `check`, `build`, `assemble` or `clean` of its own); the cost is one warning that the TestKit class turns into a failing test the day KGP makes it an error. The typed extension would only wrap one relative path.

### `build-logic/src/main/kotlin/webtools.kotlin-common-check.gradle.kts`

```kotlin
// Convention plugin: an unpublished, check-only Kotlin Multiplatform module with a single js target.
// It compiles another module's commonMain directory for a second target, so that a JVM API in common
// code is a compile error (a module with only jvm() accepts java.* in commonMain). It applies the
// multiplatform plugin and declares mavenCentral() itself; it does not apply webtools.published-library,
// so the module has no publishing plugin, no signing and no ABI dump, and nothing in it is published.
// A check module adds, in its own build file:
// - the sibling's commonMain directory as a source directory of its commonMain, as a path relative to
//   the check module (no project(...) access);
// - the dependencies of the sibling's commonMain;
// - the module-wide opt-in of the sibling's gate, as kotlin.compilerOptions.optIn;
// and, in its own src/jsMain/kotlin, a js actual for every expect declaration in that directory.
// commonTest is not compiled: the tests of the sibling run on its jvm target.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

repositories {
    mavenCentral()
}

//region js target
// js() with no environment, on purpose. nodejs() and browser() both create the js test task, and that
// task depends on the Node.js and Yarn set-up, npm install and the kotlin-js-store lock file. Disabling
// a task does not skip the tasks it depends on, so `testTask { enabled = false }` still downloads
// Node.js and Yarn during `build` and writes kotlin-js-store/. Either call also applies the Kotlin
// Node.js root plugin to the root project, which gains build, check, assemble and clean tasks and the
// Node.js and Yarn tasks. Without an environment the target compiles to a klib, has no test task and
// leaves the root project alone. The Kotlin Gradle plugin reports "JS Environment Not Selected" once
// per build for it: a warning in 2.2.0 and still one in 2.4.21, which says the omission will be an
// error in a future release. The TestKit check of this plugin fails on that day.
kotlin {
    js()
}
//endregion

//region check
// compileKotlinJs belongs to assemble (through jsJar), and a module with no test source leaves check
// with nothing to run, so without this line check would pass without compiling anything.
tasks.named("check") {
    dependsOn("compileKotlinJs")
}
//endregion
```

No change to `build-logic/build.gradle.kts` is needed.

### Each check module

`webtools-http-commoncheck/build.gradle.kts` (and `webtools-feeds-commoncheck/build.gradle.kts` with its own sibling path and, in addition, `implementation(project(":webtools-http-commoncheck"))`, F11):

```kotlin
plugins {
    id("webtools.kotlin-common-check")
}

kotlin {
    compilerOptions {
        optIn.add("com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi")
    }
    sourceSets {
        commonMain {
            kotlin.srcDir("../webtools-http/src/commonMain/kotlin")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
    }
}
```

The module declares no `explicitApi()` (the sibling enforces it on the same sources), no version, no coordinates and no publishing.

### `webtools-http-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/http/JsActuals.kt`

```kotlin
package com.spartanlabs.webtools.http

import kotlin.js.Date

// The js target exists only to compile commonMain; nothing runs it, so logging is a no-op.
private object NoOpLog : Log {
    override fun debug(message: String, vararg args: Any?) = Unit
    override fun info(message: String, vararg args: Any?) = Unit
    override fun warn(message: String, vararg args: Any?) = Unit
}

internal actual fun logger(name: String): Log = NoOpLog

// Date.now() is a Double of epoch milliseconds, exact as a Long.
internal actual fun epochMillisNow(): Long = Date.now().toLong()
```

The probe compiled this against `internal interface Log { fun debug(message: String, vararg args: Any?) ... }`. If the real `Log` declares other members, the no-op object overrides them too.

### `settings.gradle.kts`

`include(":webtools-http", ":webtools-http-commoncheck")` (unit 3); the feeds unit adds `:webtools-feeds-commoncheck`. Nothing else in the settings or the root `build.gradle.kts` changes.

### What the root project gains, and the documents that depend on it

- **Nothing**: no plugin, extension or task. `./gradlew build` and `assemble` run `compileKotlinJs` and the klib jar per check module; `check` runs `compileKotlinJs`; `test` and the level tasks do not touch the check modules; `clean` deletes their `build/` directories; `tasks` at the root is unchanged. No Node.js or Yarn is downloaded and no `kotlin-js-store/` is written.
- **README "Build & test"**: add one sentence that `./gradlew build` and `check` also compile each `*-commoncheck` module's shared source for `js` (no Node.js is needed), and that `./gradlew :webtools-http-commoncheck:build` runs one. The "Shared build logic" section says "three convention plugins" and lists three rows; it becomes four, with a row `webtools.kotlin-common-check` | an unpublished, check-only `js` module that compiles a sibling's `commonMain` (no publishing, no tests, no Node.js).
- **The next unit (`test-support`, #63)**: its plan assumes the root project has no `check` task (the fixtures-publication check cannot attach to a root `check`). That stays true with `js()` and no environment. It would be false under any `nodejs()`/`browser()` form, where the root gains `check`, `build`, `assemble` and `clean`.
- **`.gitignore`**: no change is needed. Adding `kotlin-js-store/` would only hide a regression the TestKit class already catches.

### The TestKit class

Package: `com.spartanlabs.testing.integration.webtools.buildlogic` (Level 3), file `CommonCheckPluginIntegrationTest.kt`; it runs under the existing `integrationTest` task, which already uses the shared TestKit home and `testKitHomeLock`. Two further edits: `BuildLogicPluginsGatingTest.conventionPluginIds` (`build-logic/src/test/kotlin/com/spartanlabs/testing/gating/webtools/buildlogic/BuildLogicPluginsGatingTest.kt:17`) gets `"webtools.kotlin-common-check"`, and a configuration-cache class in the non-functional package, following `ConfigurationCacheReuseNonFunctionalTest`.

The sample factory, added to `SampleProject` (`build-logic/src/test/kotlin/com/spartanlabs/testing/support/webtools/buildlogic/SampleProject.kt`):

- `SampleProject.commonCheck(rootProjectName)` with constants `COMMON_CHECK_MODULE = "commoncheck"`, `COMMON_CHECK_SHARED = "shared"`, `COMMON_CHECK_COMMON_PATH`, `COMMON_CHECK_ACTUAL_PATH`.
- Files: `settings.gradle.kts` (`rootProject.name`, `include(":commoncheck")`, **no** `dependencyResolutionManagement`, so the plugin's own `mavenCentral()` is what resolves the stdlib), the shared `gradle.properties` template (configuration cache on), `commoncheck/build.gradle.kts` (the plugin and the relative `srcDir`), `shared/src/commonMain/kotlin/sample/Platform.kt` (`expect fun platformName(): String` and `fun describe(): String`), `commoncheck/src/jsMain/kotlin/sample/PlatformJs.kt` (the `actual`). `shared` is not a Gradle project. The root has no build file.
- The negative cases use `withFile`: a `shared/.../Leak.kt` importing `java.io.File`, and a `shared/.../Extra.kt` with `expect fun extra(): Int` that has no `actual`.

The test class (all builds run with `NestedBuild.run`, so a broken plugin fails the individual test):

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommonCheckPluginIntegrationTest {
    // @BeforeAll: workspace "ccp" -> build "build", "build --dry-run", "check --dry-run", "tasks --all";
    //             workspace "ccl" (Leak.kt) -> run "compileKotlinJs";
    //             workspace "ccm" (Extra.kt, no actual) -> run "compileKotlinJs".

    @Test fun `build compiles the shared common code for js`()
        // :commoncheck:compileKotlinJs and :commoncheck:build are SUCCESS.
    @Test fun `check depends on the js compile`()
        // "check --dry-run" has a line starting ":commoncheck:compileKotlinJs ".
    @Test fun `a JVM API in the shared directory fails the js compile`()
        // compileKotlinJs FAILED; output contains "Leak.kt" and "Unresolved reference".
    @Test fun `an expect declaration without a js actual fails the js compile`()
        // compileKotlinJs FAILED; output contains "has no actual declaration".
    @Test fun `build starts no Node, Yarn or npm task and writes no lock store`()
        // no executed task path contains "NodeJs", "Yarn", "Npm" or "PackageJson";
        // kotlin-js-store/ absent in the project; "build --dry-run" has no jsNodeTest or jsBrowserTest.
    @Test fun `the module has no publishing or signing task`()
        // task names from "tasks --all" and the dry run (last path segment) with a prefix publish, sign,
        // generatePomFile or generateMetadataFile: none; and the name list is not empty.
    @Test fun `the root project gains no task`()
        // no dry-run line matches ^:[^:]+  (a root-level task such as :build or :check).
}
```

and `CommonCheckConfigurationCacheReuseNonFunctionalTest` (`build`, `build`; the second output contains `Reusing configuration cache`).

Robustness notes: the assertions read task paths, task outcomes and stable message fragments (`Unresolved reference`, `has no actual declaration`), not whole messages. The Node/Yarn assertion keys on task names and on the absence of `kotlin-js-store/`, not on the shared TestKit home's contents, which other tests may fill. The class does not assert that the "JS Environment Not Selected" warning is printed, so a KGP release that removes it does not break the test, and a release that makes it an error breaks the `build` test, which is the intended alarm.

The complete code of the factory and of the class is in the appendix below. It passed in the probe (all seven tests and the non-functional test; the existing integration suite and the gating class also passed with it).

### Appendix: the TestKit sources as run in the probe

Additions to `SampleProject.kt`, inside `companion object`: the constants after `LEVELS_LINE_PREFIX`, the factory at the end of the `factories` region, and the templates in the `script templates` region (the file uses CRLF line ends; the probe's additions were normalised to match).

```kotlin
        /** The directory of the common-check sample's check module, which applies `webtools.kotlin-common-check`. */
        const val COMMON_CHECK_MODULE: String = "commoncheck"

        /** The directory of the common-check sample's shared source, which is not a Gradle project. */
        const val COMMON_CHECK_SHARED: String = "shared"

        /** The path of the common-check sample's shared common code, a `commonMain` file with one `expect` function. */
        const val COMMON_CHECK_COMMON_PATH: String = "$COMMON_CHECK_SHARED/src/commonMain/kotlin/sample/Platform.kt"

        /** The path of the common-check sample's `jsMain` file, which holds the `actual` of that function. */
        const val COMMON_CHECK_ACTUAL_PATH: String = "$COMMON_CHECK_MODULE/src/jsMain/kotlin/sample/PlatformJs.kt"

        /**
         * A project with a check-only `js` module, shaped as the repository's check modules are:
         * module [COMMON_CHECK_MODULE] applies only `id("webtools.kotlin-common-check")` and adds the
         * sibling directory [COMMON_CHECK_SHARED]'s `commonMain` as a source directory of its own
         * `commonMain`. [COMMON_CHECK_SHARED] is not a Gradle project: it holds only common code, free
         * of JVM APIs, with one `expect` function whose `actual` is in the check module's `jsMain`.
         *
         * Its `settings.gradle.kts` declares no repositories, so the plugin's own `mavenCentral()` is
         * the only way the check module resolves the Kotlin standard library. Its `gradle.properties`
         * is the same as the other samples'. The root project applies no plugin and has no build file.
         *
         * @param rootProjectName the root project's name.
         * @return the project description.
         * @throws IllegalArgumentException if [rootProjectName] is blank.
         */
        fun commonCheck(rootProjectName: String): SampleProject {
            require(rootProjectName.isNotBlank()) { "A sample project needs a non-blank root project name" }
            return SampleProject(
                mapOf(
                    "settings.gradle.kts" to commonCheckSettings(rootProjectName),
                    "gradle.properties" to GRADLE_PROPERTIES,
                    "$COMMON_CHECK_MODULE/build.gradle.kts" to COMMON_CHECK_BUILD_SCRIPT,
                    COMMON_CHECK_COMMON_PATH to COMMON_CHECK_COMMON,
                    COMMON_CHECK_ACTUAL_PATH to COMMON_CHECK_ACTUAL,
                ),
            )
        }

        // No dependencyResolutionManagement: the plugin under test declares its own repositories.
        private fun commonCheckSettings(rootProjectName: String): String =
            //language=kotlin
            """
            rootProject.name = "$rootProjectName"

            include(":$COMMON_CHECK_MODULE")
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMMON_CHECK_BUILD_SCRIPT: String = """
            plugins {
                id("webtools.kotlin-common-check")
            }

            kotlin {
                sourceSets {
                    commonMain {
                        kotlin.srcDir("../$COMMON_CHECK_SHARED/src/commonMain/kotlin")
                    }
                }
            }
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMMON_CHECK_COMMON: String = """
            package sample

            expect fun platformName(): String

            fun describe(): String = "common code on " + platformName()
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMMON_CHECK_ACTUAL: String = """
            package sample

            actual fun platformName(): String = "js"
            """.trimIndent() + "\n"
```

`CommonCheckPluginIntegrationTest.kt`:

```kotlin
package com.spartanlabs.testing.integration.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files

/**
 * Level 3 - TestKit check of `webtools.kotlin-common-check`. A check module compiles a directory of
 * common code for `js` and nothing else: the compile accepts JVM-free common code, rejects a JVM API
 * in it and an `expect` without a `js` `actual`; the module publishes nothing, starts no Node.js,
 * Yarn or npm task and writes no `kotlin-js-store/`; and the root project gains no plugin or task.
 *
 * The sample's settings declare no repositories, so the plugin's own `mavenCentral()` resolves the
 * Kotlin standard library for the compile. Only the check module's `build` and `compileKotlinJs` run;
 * nothing is downloaded beyond the Kotlin Gradle plugin's `js` compile dependencies.
 *
 * The builds whose outcome is under test run with [NestedBuild.run], so that each test fails on its
 * own assertion, and not all of them in the set-up, while the plugin is broken.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommonCheckPluginIntegrationTest {

    private lateinit var workspace: ProjectWorkspace
    private lateinit var leakWorkspace: ProjectWorkspace
    private lateinit var missingActualWorkspace: ProjectWorkspace
    private lateinit var buildResult: BuildResult
    private lateinit var dryRun: BuildResult
    private lateinit var checkDryRun: BuildResult
    private lateinit var allTasks: BuildResult
    private lateinit var leakResult: BuildResult
    private lateinit var missingActualResult: BuildResult

    @BeforeAll
    fun runBuilds() {
        workspace = ProjectWorkspace.create("ccp").getOrThrow()
        workspace.write(SampleProject.commonCheck("sample")).getOrThrow()
        NestedBuild(workspace).also { build ->
            buildResult = build.run("build")
            dryRun = build.run("build", "--dry-run")
            checkDryRun = build.run("check", "--dry-run")
            allTasks = build.run("tasks", "--all")
        }

        leakWorkspace = ProjectWorkspace.create("ccl").getOrThrow()
        leakWorkspace.write(
            SampleProject.commonCheck("sample").withFile(
                "${SampleProject.COMMON_CHECK_SHARED}/src/commonMain/kotlin/sample/Leak.kt",
                //language=kotlin
                """
                package sample

                import java.io.File

                fun leak(): String = File("x").name
                """.trimIndent() + "\n",
            ),
        ).getOrThrow()
        leakResult = NestedBuild(leakWorkspace).run("compileKotlinJs")

        missingActualWorkspace = ProjectWorkspace.create("ccm").getOrThrow()
        missingActualWorkspace.write(
            SampleProject.commonCheck("sample").withFile(
                "${SampleProject.COMMON_CHECK_SHARED}/src/commonMain/kotlin/sample/Extra.kt",
                //language=kotlin
                """
                package sample

                expect fun extra(): Int
                """.trimIndent() + "\n",
            ),
        ).getOrThrow()
        missingActualResult = NestedBuild(missingActualWorkspace).run("compileKotlinJs")
    }

    @AfterAll
    fun closeWorkspaces() {
        workspace.close()
        leakWorkspace.close()
        missingActualWorkspace.close()
    }

    @Test
    fun `build compiles the shared common code for js`() {
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":${SampleProject.COMMON_CHECK_MODULE}:compileKotlinJs")?.outcome, buildResult.output)
        assertEquals(TaskOutcome.SUCCESS, buildResult.task(":${SampleProject.COMMON_CHECK_MODULE}:build")?.outcome, buildResult.output)
    }

    @Test
    fun `check depends on the js compile`() {
        val compile = ":${SampleProject.COMMON_CHECK_MODULE}:compileKotlinJs "
        assertTrue(checkDryRun.output.lineSequence().any { it.startsWith(compile) }, "no $compile in the check dry run:\n${checkDryRun.output}")
    }

    @Test
    fun `a JVM API in the shared directory fails the js compile`() {
        assertEquals(TaskOutcome.FAILED, leakResult.task(":${SampleProject.COMMON_CHECK_MODULE}:compileKotlinJs")?.outcome, leakResult.output)
        assertTrue(leakResult.output.contains("Leak.kt"), "the error does not name the shared file:\n${leakResult.output}")
        assertTrue(leakResult.output.contains("Unresolved reference"), "no unresolved-reference error:\n${leakResult.output}")
    }

    @Test
    fun `an expect declaration without a js actual fails the js compile`() {
        assertEquals(TaskOutcome.FAILED, missingActualResult.task(":${SampleProject.COMMON_CHECK_MODULE}:compileKotlinJs")?.outcome, missingActualResult.output)
        assertTrue(missingActualResult.output.contains("has no actual declaration"), missingActualResult.output)
    }

    @Test
    fun `build starts no Node, Yarn or npm task and writes no lock store`() {
        val started = buildResult.tasks.map { it.path }.filter { path ->
            listOf("NodeJs", "Yarn", "Npm", "PackageJson").any { path.contains(it) }
        }
        assertEquals(emptyList<String>(), started, buildResult.output)
        assertTrue(Files.notExists(workspace.resolve("kotlin-js-store")), "the build wrote kotlin-js-store/")
        assertTrue(dryRun.output.lineSequence().none { it.contains("jsNodeTest") || it.contains("jsBrowserTest") }, dryRun.output)
    }

    @Test
    fun `the module has no publishing or signing task`() {
        val names = taskNames(allTasks.output) + taskNames(dryRun.output)
        assertTrue(names.isNotEmpty(), "no task names were read from:\n${allTasks.output}")
        val publishing = names.map { it.substringAfterLast(':') }.filter { name ->
            listOf("publish", "sign", "generatePomFile", "generateMetadataFile").any { name.startsWith(it) }
        }
        assertEquals(emptyList<String>(), publishing)
    }

    @Test
    fun `the root project gains no task`() {
        val rootTasks = dryRun.output.lineSequence().filter { Regex("^:[^:]+ ").containsMatchIn(it) }.toList()
        assertEquals(emptyList<String>(), rootTasks, "the root project has tasks of its own:\n${dryRun.output}")
    }

    // The task names in a `--dry-run` or `tasks --all` listing: `:commoncheck:compileKotlinJs SKIPPED` and `commoncheck:compileKotlinJs - ...`.
    private fun taskNames(output: String): List<String> =
        output.lineSequence()
            .mapNotNull { Regex("^:?([A-Za-z][A-Za-z0-9:_-]*)(?: - .*| [A-Z-]+)?$").matchEntire(it.trim())?.groupValues?.get(1) }
            .toList()
}
```

`CommonCheckConfigurationCacheReuseNonFunctionalTest.kt`:

```kotlin
package com.spartanlabs.testing.nonfunctional.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Level 4c - TestKit check that a module applying `webtools.kotlin-common-check` is
 * configuration-cache compatible, including the source directory it takes from a sibling directory.
 * The second identical invocation reuses the cache entry the first one stored. The generated project
 * turns the cache on in its own `gradle.properties`, because TestKit ignores the user-level one.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommonCheckConfigurationCacheReuseNonFunctionalTest {

    private lateinit var workspace: ProjectWorkspace
    private lateinit var secondRun: BuildResult

    @BeforeAll
    fun runTwice() {
        workspace = ProjectWorkspace.create("cccc").getOrThrow()
        workspace.write(SampleProject.commonCheck("sample")).getOrThrow()
        val build = NestedBuild(workspace)
        build.build("build")
        secondRun = build.build("build")
    }

    @AfterAll
    fun closeWorkspace() {
        workspace.close()
    }

    @Test
    fun `the second identical invocation reuses the configuration cache`() {
        assertTrue(secondRun.output.contains("Reusing configuration cache"), "no configuration-cache reuse:\n${secondRun.output}")
    }
}
```

The edit to `BuildLogicPluginsGatingTest.kt` adds `"webtools.kotlin-common-check",` to `conventionPluginIds`.

## Considerations

### 1. The "JS Environment Not Selected" warning versus a Node-aware form

- **Options:**
  - (a) `js()` with no environment, accept the warning on every configuration-cache miss. KGP 2.2.0 to 2.4.21 keep it a warning.
  - (b) (a) plus `kotlin.suppressGradlePluginWarnings=JsEnvironmentNotChosenExplicitly` in the root `gradle.properties`: removes the line, but it is a repository-wide setting that hides the diagnostic for any future `js` target.
  - (c) `nodejs { testTask { enabled = false; setDependsOn(emptyList<Any>()) } }`: no warning and no downloads, but the root project gains `BasePlugin`, the Node.js/Yarn plugins and about 18 tasks (including root `check`, `build`, `assemble`, `clean`), `build` writes root `build/js/packages/`, and it leans on KGP's wiring order.
- **Recommendation:** (a). One warning line per cold configuration is cheaper than changing the root project that other units build on; if a KGP release turns the omission into an error, the TestKit class fails and (c) is the fallback.

### 2. A typed extension through which a check module names its sibling

- **Options:** (a) plain: the check module's build file holds the relative `srcDir` line. (b) an extension `commonCheck { sibling = "webtools-http" }` in `build-logic` that adds the directory and fails the build if the directory is missing or holds no `.kt` file.
- **Recommendation:** (a). It has two consumers and one line each, and the plugin cannot derive the sibling's `commonMain` dependencies in any case (that would need `project(...)` access to the sibling's model). The one thing (b) would add, a vacuous-pass guard, is only reachable by a check module with no `actual`; revisit if a third check module appears.

## Known gaps

- **Not run in the repository.** Everything was measured in a scratch probe that copies `build-logic` and mirrors the repository's layout (root with no plugin, an `allprojects` block, the real convention plugins); the real root also loads the foojay settings plugin and three JVM modules, which I did not add. Gradle 9.7.1, KGP 2.2.0, a JDK 23 launcher, Windows 11, one host.
- **No Linux/macOS run.** Node.js v22.0.0 was downloaded as the `win-x64` distribution; the task graph is the same on other hosts, the files differ.
- **KGP versions other than 2.2.0 were only read, not run.** That the "JS Environment Not Selected" diagnostic is still a WARNING in 2.4.21 comes from the tag's source.
- **IDE behaviour is untested** (IntelliJ's import of a module whose source directory lies outside the module, and of an `actual` for an `expect` in a sibling directory).
- **The `--warning-mode=fail` behaviour of the "JS Environment Not Selected" diagnostic** was not isolated: KT-78620's `archives` deprecation, raised at `js()` too, fails such a run first. I read the diagnostic as a KGP problem report logged at warning level, not a Gradle deprecation, but did not test it.
- **No public library found** that uses an unpublished check-only target module; the pattern rests on this repository's own measurements.
- **The size of the TestKit-home increment** (two klibs) is inferred from the artifact list; I did not diff a home populated by the existing samples against one with the new class.
- **Nested-build wall times** per build are not logged at the default level; only class totals were taken.
- **The sibling's own opt-in form** (main source sets only, tests without the opt-in) is the subject of `opt-in-gate.md`; here only the check module's line was verified.

## Load-bearing claims

Pinned: Gradle 9.7.1, KGP 2.2.0 (tag `v2.2.0`) unless stated.

1. **Disabling a task does not skip its dependencies.** Gradle 9.7.1 Javadoc, `Task.setEnabled(boolean)`: "Note that disabling a task does not prevent the execution of the tasks which this task depends on." <https://docs.gradle.org/9.7.1/javadoc/org/gradle/api/Task.html>.
2. **The `js` test task depends on Node.js set-up and npm install.** `KotlinNodeJsIr.configureTestDependencies` at v2.2.0: "test.dependsOn(project.nodeJsSetupTaskProvider)" and "nodeJsRoot.npmInstallTaskProvider,". <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/js/ir/KotlinNodeJsIr.kt>.
3. **`browser` and `nodejs` both evaluate `commonLazy`, which applies `NpmResolverPlugin`.** `KotlinJsIrTarget` at v2.2.0: "private val commonLazyDelegate = lazy {\n        webTargetVariant(\n            { NpmResolverPlugin.apply(project) }," and "private val browserLazyDelegate = lazy {\n        commonLazy". <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/js/ir/KotlinJsIrTarget.kt>.
4. **`NpmResolverPlugin` applies the Node.js root plugin to the root project.** `NpmResolverPlugin.apply` at v2.2.0: "{ NodeJsRootPlugin.apply(project.rootProject) }," <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/js/npm/NpmResolverPlugin.kt>.
5. **The root plugin applies `BasePlugin` to the root.** `NodeJsRootPluginApplier.apply` at v2.2.0: "project.plugins.apply(BasePlugin::class.java)". <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/web/nodejs/NodeJsRootPluginApplier.kt>.
6. **The missing-environment diagnostic is a warning that announces a future error, in 2.2.0 and 2.4.21.** `KotlinToolingDiagnostics.JsLikeEnvironmentNotChosenExplicitly` at v2.2.0: "Not choosing any of them will be an error in the future releases." and ") : ToolingDiagnosticFactory(WARNING, DiagnosticGroup.Kgp.Misconfiguration) {"; at v2.4.21 the same declaration "abstract class JsLikeEnvironmentNotChosenExplicitly(" still ends ") : ToolingDiagnosticFactory(WARNING, DiagnosticGroup.Kgp.Misconfiguration) {". <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/diagnostics/KotlinToolingDiagnostics.kt> and <https://github.com/JetBrains/kotlin/blob/v2.4.21/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/diagnostics/KotlinToolingDiagnostics.kt>.
7. **`kotlin.js.Date.now()` exists in the 2.2.0 Kotlin/JS standard library.** `libraries/stdlib/js/src/kotlin/date.kt` at v2.2.0: "package kotlin.js", "public external class Date() {" and "public companion object {\n        public fun now(): Double". <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/js/src/kotlin/date.kt>.
8. **A second target is what makes the compiler reject JVM symbols in common code.** Kotlin Multiplatform documentation: "If your project only has a single target (for example, JVM), you can access target-specific symbols with appropriate visibility from common code. However, as soon as you add a second target, target-specific symbols become unavailable in common code." <https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html> (read 2026-10-25).
9. **`nodejs()` makes the plugin download Node.js.** Kotlin/JS project setup documentation: "For Kotlin/JS projects targeting Node.js, the plugin automatically downloads and installs the Node.js environment on the host." <https://kotlinlang.org/docs/js-project-setup.html> (read 2026-10-25).
10. **A warning id can be suppressed by a project property.** `PropertiesProvider` at v2.2.0: "internal const val KOTLIN_SUPPRESS_GRADLE_PLUGIN_WARNINGS_PROPERTY = \"kotlin.suppressGradlePluginWarnings\"". <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/PropertiesProvider.kt>. (The value form that works is measured, M8.)
11. **KT-78620.** The `archives` deprecation is already recorded for KGP 2.2.0 in [`../../research/kmp-build-topology.md`](../../research/kmp-build-topology.md) F1: "The archives configuration has been deprecated for artifact declaration. This will fail with an error in Gradle 10." (KGP 2.3.21 and 2.4.21 print nothing, per that file.)

Measured results (a scratch probe project; Gradle 9.7.1, KGP 2.2.0, JDK 23 launcher, Windows 11, `org.gradle.configuration-cache=true` in the probe's `gradle.properties`, a dedicated Gradle user home; the probe copies `build-logic` as an included build and has a root project with no plugin, `allprojects { group; version }`, a `webtools.kotlin-multiplatform-library` module `webtools-http` with `explicitApi()`, a `commonMain` holding `internal interface Log`, `internal expect fun logger(name: String): Log`, `internal expect fun epochMillisNow(): Long`, `WallClock` with a nested `System` object and the `ExperimentalForInheritanceWebToolsApi` marker, and one or two check modules). Re-run with `gradlew -p <probe> -g <fresh user home> <task>`:

- **M1.** `webtools.kotlin-common-check` = `plugins { id("org.jetbrains.kotlin.multiplatform") }`, `repositories { mavenCentral() }`, `kotlin { js { nodejs { testTask { enabled = false } } } }`. `build --dry-run` lists `:kotlinNodeJsSetup`, `:kotlinYarnSetup`, `:kotlinNpmInstall`, `:rootPackageJson`, `:kotlinStoreYarnLock`, `:<check>:kotlinNodeJsSetup`, then `:<check>:jsNodeTest SKIPPED`. `build` then printed `> Task :kotlinNodeJsSetup`, `> Task :kotlinYarnSetup`, `> Task :kotlinNpmInstall`, `> Task :kotlinStoreYarnLock`, `> Task :<check>:jsNodeTest SKIPPED` and `BUILD SUCCESSFUL in 7s`; afterwards the user home held `nodejs/node-v22.0.0-win-x64` and `yarn/yarn-v1.22.17`, the project root held `kotlin-js-store/yarn.lock`, and the root `build/js/` existed.
- **M2.** Same, with `testTask { enabled = false; setDependsOn(emptyList<Any>()) }`. `build --dry-run` has no Node/Yarn/`kotlinNpmInstall`/`rootPackageJson` task; `build` ran `kotlinKotlinNpmCachesSetup`, `jsPackageJson`, `jsPublicPackageJson`, `compileKotlinJs`, `jsNodeTest SKIPPED`; no `nodejs`/`yarn` directory, no `kotlin-js-store/`; root `build/js/packages/` written. A `gradle.projectsEvaluated` hook printing the root project's plugins, extensions and tasks (run with `--no-configuration-cache`) showed `BasePlugin`, `LifecycleBasePlugin`, `NodeJsPlugin`, `NodeJsRootPlugin`, `YarnPlugin`, extensions `base`, `kotlinNodeJs`, `kotlinNodeJsSpec`, `kotlinNpm`, `kotlinYarn`, `kotlinYarnSpec`, and the root tasks of F5; `tasks --all` gained `assemble`, `build`, `check`, `clean` at the root.
- **M3.** The plugin of the recommendation (`kotlin { js() }`, plus the `check` wiring). Same hook: the root's plugins, extensions and tasks equal those of the same probe without any check module (`tasks --all` root listing: empty diff). `build --warning-mode all` cold: `BUILD SUCCESSFUL in 9s`, 28 tasks; output holds the `archives` deprecation at `webtools.kotlin-common-check.gradle.kts` `js()` and `w: JS Environment Not Selected` once; warm: `Reusing configuration cache`, `BUILD SUCCESSFUL in 762ms`. The user home has no `nodejs` or `yarn` directory; the project has no `kotlin-js-store/`; root `build/` holds `reports/` only. `tasks --all` lists no task starting with `publish`.
- **M4.** `js { browser { testTask { enabled = false } } }`: `build --dry-run` lists `:kotlinNodeJsSetup`, `:kotlinYarnSetup`, `:kotlinNpmInstall`, `:kotlinStoreYarnLock`, `:<check>:jsBrowserTest`.
- **M5.** A JVM API: a file `internal fun leak(): String = java.io.File("x").name + String.format("%d", 1) + System.currentTimeMillis()` in the sibling's `commonMain`: `:<check>:compileKotlinJs` FAILED with `Unresolved reference 'java'.`, `'format'`, `'System'`; the sibling's `compileKotlinJvm` ended `BUILD SUCCESSFUL`. Removing the `logger` `actual`: `e: Log.kt:9:10 Expected ... logger has no actual declaration in module <commonMain> for JS`.
- **M6.** `@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)` on `WallClock`, `internal class FixedClock : WallClock` in `commonMain`: with no opt-in `compileKotlinJs` fails ("This class or interface requires opt-in to be implemented"); with `commonMain { languageSettings.optIn(...) }` only, it still fails; with `kotlin { compilerOptions { optIn.add(...) } }` or `sourceSets.all { languageSettings.optIn(...) }`, it passes. The sibling's `compileKotlinJvm` also fails with `commonMain`'s `languageSettings.optIn` alone.
- **M7.** A second check module with `implementation(project(":webtools-http-commoncheck"))` and a shared directory using the public `WallClock`: `build` compiled both. `kotlin.srcDir(project(":webtools-http").layout.projectDirectory.dir("src/commonMain/kotlin"))` instead of the relative path: configuration cache stored and reused; with `-Dorg.gradle.unsafe.isolated-projects=true` the build already failed at the root's `allprojects { group = ... }`, before reaching the check module.
- **M8.** `-Pkotlin.suppressGradlePluginWarnings=JsEnvironmentNotChosenExplicitly --no-configuration-cache --rerun-tasks`: the warning count in the output was 0; with the value `KOTLIN:KGP:MISCONFIGURATION:JsEnvironmentNotChosenExplicitly` it was 1.
- **M9.** The copied `build-logic` with the plugin, the `SampleProject.commonCheck` factory, `CommonCheckPluginIntegrationTest` (7 tests) and `CommonCheckConfigurationCacheReuseNonFunctionalTest` (1 test): `./gradlew integrationTest` (all classes) and `nonfunctionalTest` and `test` passed. Class time with an empty TestKit home (`rm -rf build/testkit/home`, `cleanIntegrationTest integrationTest --tests "*CommonCheckPluginIntegrationTest*"`): 34.06 s for six tests; warm: 17.5 s for six, 12.08 s for seven; configuration-cache class: 10.33 s warm. The TestKit home then held `kotlin-stdlib-js-2.2.0.klib` (2,717,723 bytes) and `kotlin-dom-api-compat-2.2.0.klib` (930,932 bytes) and no Node.js or Yarn files. Mutations against the class: plugin variants M1, M2, M4 failed `build starts no Node, Yarn or npm task...` and `the root project gains no task`; adding `id("maven-publish")` failed `the module has no publishing or signing task`; removing `repositories { mavenCentral() }` failed four tests.
