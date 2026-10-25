# Wiring the fixtures-publication check into `./gradlew build` and `./gradlew check`

## Question

The fixtures-publication check is a Gradle TestKit test class in the `pluginManagement`-included
`build-logic` build, run by a dedicated `Test` task registered in `build-logic/build.gradle.kts`. How does
that task become part of what `./gradlew build` and `./gradlew check` run from the WebTools root, while a
build that includes the WebTools root in its own composite (and substitutes `webtools-http`) configures
and builds exactly as before?

Requirements an answer meets:

- `./gradlew build` and `./gradlew check` from the root both run the check. If it hangs off
  `webtools-http`, `./gradlew :webtools-http:check` runs it too.
- The root project keeps `allprojects { group; version }` and gains no task, unless no other option meets
  the rest. Today the root project has no task of its own: no `check`, `build`, `assemble`, `clean`.
- Gradle 9.7.1, configuration cache on: a second identical invocation prints "Reusing configuration
  cache".
- Incremental: with the check's declared inputs unchanged, a repeated `./gradlew build` reports the check
  `UP-TO-DATE`. Some inputs sit outside `build-logic`'s project directory (`../webtools-http/...`,
  `../settings.gradle.kts`, `../build.gradle.kts`).
- A consumer composite (BotTools' development composite shape, with its own included build also named
  `build-logic`) never runs the check and shows no configuration-time error, whether or not the consumer
  requests a task of the included build.
- The check task itself keeps the shape of the existing nested-build tasks in `build-logic` (same system
  properties, `usesService(testKitHomeLock)`).

## Bottom line

- **Declare the dependency in `webtools-http/build.gradle.kts`, lazily, and guard it with
  `gradle.parent == null`.** The three lines that do it are in *Recommendation*. Measured on Gradle 9.7.1
  in a probe shaped like this repository: `build`, `check` and `:lib:check` from the root each run the
  check; a repeated `build` or `check` reports it `UP-TO-DATE` and prints "Reusing configuration cache."
  and "Configuration cache entry reused."; the root project gains no task (task-name lists identical to a
  build without the wiring).
- **The mechanism is the documented one**: `IncludedBuild.task(path)` from `gradle.includedBuild(name)`.
  `Gradle.includedBuild(name)` searches only the calling build's own included builds, so inside the
  WebTools build `"build-logic"` is always WebTools' own plugin build, even when the consuming composite
  has a different `build-logic` of its own. Gradle assigns the two builds different build paths
  (`:build-logic` and `:<including-build-name>:build-logic`), so the name clash is not an error
  (measured; source: `DefaultIncludedBuildRegistry.assignPath`). It stays safe only while WebTools'
  `build-logic` directory sits inside the WebTools directory.
- **The guard is what makes the consumer requirement literal.** Without it every consumer scenario is
  already clean (no error, no warning, no check on `build` or on a dependency on the substituted module);
  the one difference is an explicit `:<included-build>:lib:check` in the consumer, which then runs the
  check. With `if (gradle.parent == null)` it does not. `gradle.parent == null` is the convention other
  projects use for "I am the root build" (androidx/media, moko-resources, nessie), and it added no
  configuration-cache problem.
- **A root `check` task (option 3) fails the requirements**: a root `check` alone is not reached by
  `./gradlew build` and not by `:webtools-http:check`; adding root `build` (or `lifecycle-base`) fixes
  `build` but still not `:webtools-http:check`, and adds two (or four) root tasks. Measured.
- **The check's inputs outside the project directory work** for up-to-date checks and under the
  configuration cache, with two rules: declare directories as `inputs.files(fileTree(...))`, not
  `inputs.dir(...)` (a missing directory fails `inputs.dir` validation but not a `fileTree`), and edits to
  files inside a `fileTree` re-run the check without invalidating the configuration cache, while edits to
  build scripts invalidate the cache and re-run the check. Measured.

## Findings

Tiers: **documented** (Gradle documentation or Javadoc), **source** (Gradle source at `v9.7.1`),
**measured** (the probe described under *Probe*), **observed practice** (what other repositories visibly
do), **reasoned** (my inference, not measured).

### Probe

Gradle 9.7.1 (the repository's wrapper version), Oracle JDK 23.0.2, Windows 11, an isolated Gradle user
home, no signing or publishing plugin, `--configuration-cache` on every invocation. Two throwaway builds
outside the repository:

- A "producer": `settings.gradle.kts` with `pluginManagement { includeBuild("build-logic") }` and two
  `java-library` subprojects (`:lib`, `:other`); root `build.gradle.kts` holding only
  `allprojects { group = "com.probe"; version = "1.0.0" }`; a `build-logic` build with the `kotlin-dsl`
  plugin, one precompiled convention plugin, and a `Test` task `publicationCheck` shaped like the planned
  one (JUnit 5 on the `test` source set's runtime classpath, a package filter, `systemProperty`,
  `usesService(testKitHomeLock)` with a single-permit build service, a 30-minute timeout, and declared
  inputs `../lib/build.gradle.kts`, `../settings.gradle.kts`, `../build.gradle.kts`, `fileTree("../lib/src")`
  and `src/main` of `build-logic`). The test body reads files from the producer root and prints a marker.
- A "consumer": its own `pluginManagement { includeBuild("build-logic") }` (a different `build-logic`
  with its own plain task `publicationCheck` that prints a marker, so a wrongly resolved reference would
  be visible), `includeBuild("../producer")`, and an `:app` module with
  `implementation("com.probe:lib:1.0.0")`, substituted to the producer's `:lib`.

Each candidate wiring was written to the producer, then run in both builds (cold, warm,
`--dry-run`, `tasks --all`, `check`, `:lib:check`).

### F1. A build script depends on a task of an included build through `IncludedBuild.task(path)` (documented, source; Gradle 9.7.1)

- The composite-build documentation: "While included builds are isolated from one another and cannot
  declare direct dependencies, a composite build can declare task dependencies on its included builds."
  The documented sample is `dependsOn(gradle.includedBuild("my-app").task(":app:run"))`.
- `Gradle.includedBuild(name)` iterates this build's own `includedBuilds()` and throws
  `UnknownDomainObjectException("Included build '<name>' not found in <build>.")` if none matches.
  `includedBuilds()` throws `IllegalStateException("Included builds are not yet available for this build.")`
  before settings have loaded, so the call belongs in a project script, not in `settings.gradle.kts`.
- `IncludedBuild.task(path)` is "Produces a reference to a task in the included build." The reference
  (`DefaultTaskReference.create`) requires an absolute path (`':task'`) and is evaluated lazily, "meaning
  the referenced task will only be looked-up in the target project when the returned task reference's
  dependencies are visited". It is built with the included build's own `TaskDependencyFactory`
  (`DefaultIncludedBuild.IncludedBuildImpl.task`), so it is bound to the build it was obtained from, not
  to a name looked up later (reasoned from that code; the measurement in F3 agrees).

### F2. A `pluginManagement` plugin build is an included build, and each build sees only its own (source, measured)

- Source: `DefaultSettings.pluginManagement` adds the plugin builds to the build's included-build specs.
- Measured, WebTools-shaped build as the root (`./gradlew build`, script printing
  `gradle.parent` and `gradle.includedBuilds`): `parent=null`, `includedBuilds=[build-logic]`,
  `includedBuild('build-logic')` is the build in the producer's `build-logic` directory.
- Measured, the same producer build when included from the consumer composite: `buildPath=:producer`,
  `parent=build ':'` (the consumer root, so `gradle.parent == null` is false),
  `includedBuilds=[build-logic]` (only the producer's own plugin build), and
  `includedBuild('build-logic')` again the producer's `build-logic` directory. From the consumer's side
  `gradle.includedBuilds` is `[build-logic (consumer's), producer]`. The clash on the name `build-logic`
  therefore never reaches a lookup: each lookup is per-build.
- `./gradlew :build-logic:publicationCheck` from the producer root runs the task (a CLI path to a
  `pluginManagement`-included build works with no top-level `includeBuild`).

### F3. Two builds named `build-logic` get different build paths when each sits inside its including build's directory (source, measured)

- Source: `DefaultIncludedBuildRegistry.assignPath` qualifies a new build's path with the closest
  already-registered included build whose directory contains it ("If there is an ancestor, then we use it
  to qualify the path of the build we are adding"). Otherwise the path is the root build's path plus the
  build name. If the resulting path is already taken it throws "Included build `<dir>` has build path
  `<path>` which is the same as included build `<other dir>`".
- Measured: with the producer included from the consumer, tasks print as `:producer:build-logic:...` and
  `:build-logic:...`; `:producer:build-logic:publicationCheck` ran and the consumer's
  `:build-logic:publicationCheck` did not (its marker never printed).
- Measured: `includeBuild("../producer") { name = "webtools" }` gives `:webtools:build-logic`, and the
  wiring still works (`:webtools:lib:check` ran `:webtools:build-logic:publicationCheck`).
- Measured failure mode: when the producer's plugin build lives outside the producer's directory
  (`pluginManagement { includeBuild("../shared/build-logic") }`), the consumer fails at configuration:
  "Included build ...shared\build-logic has build path :build-logic which is the same as included build
  ...consumer\build-logic". The documented rule is "Unique Build Paths: No two included builds can share
  the same build path"; Gradle's workaround is to rename in the including settings.
- The documentation also states that nested composites are flattened ("All included builds share the same
  parent, regardless of how deeply they were nested"). A deeper chain than consumer to WebTools was not
  probed.

### F4. Option 1 (dependency declared in the module): eager and lazy forms both work (measured)

Producer, `lib/build.gradle.kts` carrying one of:

```kotlin
tasks.named("check") { dependsOn(gradle.includedBuild("build-logic").task(":publicationCheck")) }   // lazy
tasks["check"].dependsOn(gradle.includedBuild("build-logic").task(":publicationCheck"))             // eager
```

- `build --dry-run` lists `:build-logic:publicationCheck SKIPPED` among `:lib:check`, `:other:check`;
  `check --dry-run` lists it too.
- First real `build` after a change to an input: `> Task :build-logic:publicationCheck` executes. Second
  `build`: "Reusing configuration cache." then `> Task :build-logic:publicationCheck UP-TO-DATE` and
  "Configuration cache entry reused." (710 ms warm in the probe). `check` and `:lib:check` behave alike.
- The root project and subproject task names from `tasks --all` are identical to the build without the
  wiring: nothing is added to any project's task list; only a dependency edge.
- Both forms behave identically in the consumer (F7). The lazy form defers `gradle.includedBuild(...)`
  until `check` is realised; the eager form runs it on every configuration of the module. The repository's
  convention plugins already use `tasks.named("check") { ... }`, so the lazy form matches the local idiom.
- Cargo-culted variants to avoid: `dependsOn(":build-logic:publicationCheck")` as a string fails with
  "Project with path ':build-logic' not found in build ':'." (measured); `afterEvaluate`,
  `gradle.projectsEvaluated` or mutating `gradle.startParameter.taskNames` add nothing here (reasoned; the
  project script already runs after settings are loaded).

### F5. Option 2 (the same, guarded by `gradle.parent == null`) (measured, observed practice)

```kotlin
if (gradle.parent == null) {
    tasks.named("check") { dependsOn(gradle.includedBuild("build-logic").task(":publicationCheck")) }
}
```

- Producer as root: same output as F4 (check runs on `build`, `check`, `:lib:check`; `UP-TO-DATE` and
  "Reusing configuration cache." on repeat; identical task-name lists).
- Consumer composite: no `publicationCheck` appears in `build`, `:app:compileJava`,
  `:producer:lib:check` or `:producer:lib:build`, and none of them prints an error or warning (F7).
- `gradle.parent == null` as a "root build" test is common practice: androidx/media
  (`media3.publish.gradle.kts`: "Check if this is the root standalone build"); GitHub code search also
  finds the same test in settings scripts of moko-resources, nessie and spoofax-pie (hits only, not
  opened) (observed practice, see below).
- Isolated Projects (incubating, not enabled in this repository): with
  `-Dorg.gradle.unsafe.isolated-projects=true` every variant, including the unwired base, reports exactly
  one problem (`allprojects` reaching subprojects in the root script), so the guard and the dependency
  added none. This is weak evidence: the build already does not satisfy Isolated Projects.

### F6. Option 3 (a lifecycle task in the root) does not meet the requirements (measured, documented)

| Root wiring | Root tasks gained | `build` runs the check | `check` runs it | `:lib:check` runs it |
|---|---|---|---|---|
| `tasks.register("check") { dependsOn(<included task>) }` | `check` | no | yes | no |
| the above plus `tasks.register("build") { dependsOn("check") }` | `build`, `check` | yes | yes | no |
| `plugins { `lifecycle-base` }` plus `tasks.named("check") { dependsOn(<included task>) }` | `assemble`, `build`, `check`, `clean` | yes | yes | no |

- The base-plugin documentation puts verification on `check`: "Plugins and build authors should attach
  their verification tasks, such as ones that run tests, to this lifecycle task using
  `check.dependsOn(__task__)`." and, for `build`, "You will probably rarely attach concrete tasks directly to
  `build`". Wiring the module's own `check` follows that; a root `build` does not.
- The root `check` also changes the consumer-visible shape: with it, the consumer can address
  `:producer:check` (otherwise "task 'check' not found in project ':producer'"), and that runs the check.

### F7. The consumer composite is unaffected by every candidate (measured)

For each of the six wirings (lazy, eager, guarded, root `check`, root `check` plus `build`,
`lifecycle-base`), with the consumer running `build` (cold and warm), `:app:compileJava`,
`:app:dependencies --configuration runtimeClasspath` (prints `com.probe:lib:1.0.0 -> project ':producer:lib'`,
so substitution is in effect), `:producer:lib:check` and `:producer:lib:build`, cold and warm, always with
`--configuration-cache`:

- No error, warning or deprecation line was printed by any of those runs (a grep for `warn`, `deprecat`
  and `problem` over all outputs matched only the expected failure of `:producer:check` where the producer
  has no root `check`).
- The configuration cache behaves normally: "Calculating task graph as no cached configuration is
  available for tasks: ..." on the first run, "Reusing configuration cache." and "Configuration cache
  entry reused." on the second.
- `build` and a dependency on the substituted module never schedule the check, because the included
  build's `check` is not a dependency of anything the consumer asks for.
- `:producer:lib:check` (the included build's task, addressable by build path) runs
  `:producer:build-logic:publicationCheck` for the unguarded wirings and does not run it for the guarded
  one. Only the consumer's explicit request for an included build's `check` differentiates the options.
- The consumer's own `build-logic` task of the same name never ran.

### F8. The check's inputs outside `build-logic`'s project directory work for up-to-date checks and under the configuration cache (measured)

Producer, wiring from F5, inputs as listed in *Probe*; each row is one `./gradlew build --configuration-cache`
after the stated change:

| Change since the previous run | Configuration cache | `publicationCheck` |
|---|---|---|
| none (warm repeat) | "Reusing configuration cache." | `UP-TO-DATE` |
| `clean build` (module build directories removed) | stored (new task set) | `UP-TO-DATE` |
| a line appended to a file under `../lib/src` | reused | executed |
| repeat | reused | `UP-TO-DATE` |
| a new file added under `../lib/src` | reused | executed |
| `../settings.gradle.kts` edited | "cannot be reused because file 'settings.gradle.kts' has changed" | executed |
| `../build.gradle.kts` edited | "... file 'build.gradle.kts' has changed" | executed |
| `../lib/build.gradle.kts` edited | "... file 'lib\build.gradle.kts' has changed" | executed |
| `build-logic/src/main` edited | "... an input to plugin class 'org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins' has changed" | executed |

- A repeated `build` also finds `compileTestKotlin`, `testClasses`, `pluginUnderTestMetadata` of
  `build-logic` `UP-TO-DATE`; the whole warm `build` of the probe took about 0.7 s.
- **Declaring a directory input:** `inputs.dir("../lib/src/jvmTestFixtures")` for a directory that does not
  exist fails the task: "Input file does not exist ... specifies directory '...' which doesn't exist".
  `inputs.files(fileTree("../lib/src/jvmTestFixtures"))` and `inputs.files("../lib/src/jvmTestFixtures")`
  both pass with the directory missing, and the `fileTree` form re-runs the check when the directory is
  later created and filled (measured). `inputs.dir("src/main")` is safe because that directory exists.
- Relative strings in `inputs.files(...)` resolve against `build-logic`'s project directory (measured).

### F9. Why the root build does not reach an included build's `check` by itself (documented, observed practice)

- Gradle's best-practice page for build-logic tests (same layout as this repository):
  "By default, tests within the included `build-logic` build are not executed when you run tests in the
  root project. Because the root project only requires the build artifacts from `build-logic`, Gradle will
  build the project without running its internal tests. To run them, you must explicitly invoke the tasks:
  `./gradlew :build-logic:check`."
- Task selectors are not applied across builds; gradle/gradle#22335 "Support invoking task selectors on
  included builds" is open (checked 2026-10-25). Projects work around it with explicit `dependsOn(
  gradle.includedBuild(...).task(...))` (next section).
- Option 4 (anything else Gradle documents): the documentation names no other mechanism. What exists are
  variations of the same reference (`finalizedBy`, placing the line in a convention plugin, a `Provider`
  around it, mutating `startParameter.taskNames` as avito-android does for a git-hook task). None was
  measured; `finalizedBy` would run the check after `check` even when `check` fails, and a convention
  plugin would attach it to every module that applies the plugin (both reasoned, not measured).

## How comparable projects do it

All observed practice; commit-pinned.

- **chrisbanes/haze** (`build.gradle.kts` at `29056f286dd3`): in the root build,
  `tasks.named("check") { dependsOn(gradle.includedBuild("build-logic").task(":convention:test")) }`, with
  the comment "gradle/build-logic is an included build, so `check` doesn't reach its tests by name." The
  same file wires `spotlessCheck` to the included build's task the same way. Exactly option 1 at the root.
  [file](https://github.com/chrisbanes/haze/blob/29056f286dd3c2deaf573283898925c263faf24d/build.gradle.kts)
- **avito-tech/avito-android** (`build.gradle.kts` at `50e3b4c916c8`):
  `tasks.named("check") { dependsOn(gradle.includedBuild("build-logic").task(":check")); dependsOn(gradle.includedBuild("build-logic-settings").task(":check")) }`.
  [file](https://github.com/avito-tech/avito-android/blob/50e3b4c916c867ed950be00e37e3412234842388/build.gradle.kts)
- **signalapp/Signal-Android** (`build.gradle.kts` at `16f138410a4a`): a named root task `buildQa` depending
  on `gradle.includedBuild("build-logic").task(":tools:test")` and the ktlint tasks, run explicitly, not
  from `check`.
  [file](https://github.com/signalapp/Signal-Android/blob/16f138410a4ab940864a2791ae281dd1b2318611/build.gradle.kts)
- **Kotlin/kotlinx-benchmark** (`build.gradle.kts` at `f65212cbd415`): root `apiDump`/`apiCheck` tasks that
  `dependsOn(gradle.includedBuild("plugin").task(":apiCheck"))`, under "Workarounds for
  https://github.com/gradle/gradle/issues/22335". A library whose root declares such tasks itself; it does
  not face the composite-consumer question.
  [file](https://github.com/Kotlin/kotlinx-benchmark/blob/f65212cbd41506cf5c962a681266555d2af9ff41/build.gradle.kts)
- **androidx/media** (`build-logic/.../media3.publish.gradle.kts` at `8c6678b657ed`): `if (gradle.parent == null)`
  with the comment "Check if this is the root standalone build" before configuring publishing
  repositories. A library that is also consumed as an included build, guarding build-only wiring.
  [file](https://github.com/androidx/media/blob/8c6678b657ede1e7883fc164ef73ed483c7796c3/build-logic/src/main/kotlin/media3.publish.gradle.kts)
- **Gradle's own documentation sample** `composite-builds-basic` (tag `v9.7.1`):
  `dependsOn(gradle.includedBuild("my-app").task(":app:run"))` and `...task(":app:check")`.
- None of the application repositories above guards against being included by a consumer, because they
  are not libraries. The projects found that are libraries and also composite members guard build-only
  wiring with `gradle.parent == null`.

## Pitfalls

1. **Moving `build-logic` out of the WebTools directory** (for example to a shared sibling directory)
   makes any consumer with its own root-level `build-logic` fail at configuration with the build-path clash
   (F3, measured). Keep it inside the repository root, or rename the build in the including settings.
2. **`inputs.dir(...)` on a directory that might not exist** fails the task (F8). The fixtures source
   directory is the likely case on the way to the unit landing. Use `inputs.files(fileTree(...))`.
3. **Under-declared inputs make the check stale-green.** Anything the check copies into its generated
   build (the root `gradle.properties`, a version catalog, `webtools-http/api/*.api`, the wrapper) must be
   an input, or a change to it leaves the task `UP-TO-DATE`. The probe declared exactly what its test read.
   Likewise, an input it does not need re-runs a slow check for nothing.
4. **A string path** (`dependsOn(":build-logic:...")`) resolves against the project's own build and fails
   (F4); only the `IncludedBuild.task(...)` reference reaches across builds.
5. **Eager `gradle.includedBuild(...)` in settings or an init script** throws "Included builds are not yet
   available for this build." (source, F1). The project script is the right place.
6. **An unguarded dependency lets a consumer that asks for `:WebTools:webtools-http:check` start the
   check's nested builds** in the WebTools checkout's `build-logic/build/` directory while the consumer's
   own build is running; the documentation warns that "Multiple composite builds may conflict when run in
   parallel if more than one includes the same build." (reasoned link to the check's nested builds; the
   consumer run in the probe used a simpler check.)
7. **A root `check`/`build` to carry the dependency** changes the root's shape and, because the consumer
   can then address `:<included>:check`, widens the consumer-visible surface (F6).
8. **Nested composites** are flattened by Gradle; a second level (consumer, intermediate composite,
   WebTools) was not probed. The path rule in F3 is the one to re-check if such a chain appears.

## Recommendation for this repository

Wire the check from `webtools-http/build.gradle.kts`, lazily, and only when WebTools is the root build:

```kotlin
// The fixtures-publication check is a TestKit test in the included build-logic build, which neither `build`
// nor `check` reaches on its own. Attach it to this module's `check` (so `build`, `check` and
// `:webtools-http:check` all run it). It is wired only when WebTools is the root build: a composite that
// includes WebTools must not start it.
if (gradle.parent == null) {
    tasks.named("check") {
        dependsOn(gradle.includedBuild("build-logic").task(":publicationCheck"))
    }
}
```

(`:publicationCheck` stands for whatever name the dedicated `Test` task in `build-logic/build.gradle.kts`
gets.) In `build-logic/build.gradle.kts`, declare the inputs outside the project directory as files and
file trees:

```kotlin
inputs.files("../webtools-http/build.gradle.kts", "../settings.gradle.kts", "../build.gradle.kts")
    .withPathSensitivity(PathSensitivity.RELATIVE)
inputs.files(
    fileTree("../webtools-http/src/commonMain"),
    fileTree("../webtools-http/src/jvmMain"),
    fileTree("../webtools-http/src/jvmTestFixtures"),
).withPathSensitivity(PathSensitivity.RELATIVE)
inputs.dir("src/main").withPathSensitivity(PathSensitivity.RELATIVE)
```

plus any other file the check copies into its generated build. The root `build.gradle.kts` is unchanged.

Reasoning: option 1 is the Gradle-documented form and the one mature repositories use; it adds an edge, not
a task, so the root stays task-free; and because it hangs off the module's `check`, the three entry points
(`build`, `check`, `:webtools-http:check`) all reach it. Option 3 cannot reach `:webtools-http:check` and
adds root tasks. The guard costs one `if` and removes the only consumer-visible difference (an explicit
included-build `check`). It is the idiom other libraries use for the same purpose.

What each scenario printed (probe; see *Probe*; lines abbreviated to the load-bearing ones):

| Scenario | Observed |
|---|---|
| Producer root, `build`, first run after an input changed | `> Task :build-logic:publicationCheck`; `Configuration cache entry stored.` |
| Producer root, `build`, repeat | `Reusing configuration cache.`; `> Task :build-logic:publicationCheck UP-TO-DATE`; `Configuration cache entry reused.` |
| Producer root, `check`, after a source edit, then repeat | `> Task :build-logic:publicationCheck`; then `> Task :build-logic:publicationCheck UP-TO-DATE` with `Reusing configuration cache.` |
| Producer root, `:lib:check` | `> Task :build-logic:publicationCheck UP-TO-DATE` (it is in the graph) |
| Producer root, `tasks --all` | task-name list identical to the unwired build |
| Consumer, `build` cold and warm | no `publicationCheck`; `Reusing configuration cache.` on the repeat; no error or warning |
| Consumer, `:app:compileJava` | no `publicationCheck`; the substituted `:producer:lib` is built |
| Consumer, `:producer:lib:check` and `:producer:lib:build` | no `publicationCheck`; no error or warning |
| Consumer, `:producer:check` | "task 'check' not found in project ':producer'" (the producer root has no `check`, which is the repository's intended state) |

The `finalizedBy` and convention-plugin variants were rejected on reasoning alone (Pitfalls and F9), not
measured.

## Considerations

### Should an explicit `:WebTools:webtools-http:check` in a consumer composite run the check?

- **Options.** (a) Guard with `gradle.parent == null`, so it never does. (b) No guard, so the check runs
  there too, from the WebTools checkout's `build-logic`.
- **Recommendation: (a).** The work requires that the check never run in a consumer composite, and (b) violates that for
  exactly this request; (a) costs one `if`. The only loss is that someone who wants the
  check from inside a composite must run `./gradlew :build-logic:publicationCheck` in the WebTools checkout
  (the same task, addressable from the WebTools root).

## Known gaps

- **The repository's own build was not run** (not permitted). The probe uses `java-library` subprojects,
  not Kotlin Multiplatform, vanniktech or the real convention plugins. That `check` exists at the point
  `webtools-http/build.gradle.kts` runs rests on the convention plugins already calling
  `tasks.named("check")` (`webtools.kotlin-jvm-library`, `webtools.kotlin-multiplatform-library`,
  `webtools.kotlin-common-check`), not on a run.
- **The stand-in check does not start nested Gradle builds.** It is a `Test` task with the real task's
  inputs, build service, timeout and system properties; whether TestKit inside a task of an included build
  behaves differently when the including build is itself a composite member was not exercised, because the
  guard keeps that case out of the recommended wiring.
- **BotTools' composite was reproduced from its description**: the local BotTools checkout has no
  `includeBuild` or `build-logic` yet. The shape (consumer root with a `build-logic` plugin build, an
  `includeBuild` of a sibling checkout, substitution by coordinates) was copied from the brief.
- **Windows, JDK 23, Gradle 9.7.1 only.** Parallel execution (`org.gradle.parallel`), `--continue`,
  `--configuration-on-demand`, the build cache and Isolated Projects (beyond the single count in F5) were
  not exercised.
- **A composite deeper than one level** (consumer to intermediate composite to WebTools) was not probed
  (F3).
- **`finalizedBy`, convention-plugin placement and `startParameter` mutation** (option 4 variants) were
  not measured.
- **Timing of the real check** is unknown; the 0.7 s warm figure is the probe's, with a trivial test.

## Load-bearing claims

Probe measurements (M) were produced on 2026-10-25 on Gradle 9.7.1, Oracle JDK 23.0.2, Windows 11, an isolated Gradle
user home, in throwaway builds outside the repository; the command is the Gradle 9.7.1 binary with
`--console=plain --configuration-cache` run in the named build directory.

1. **A composite build can depend on a task of an included build.** "While included builds are isolated
   from one another and cannot declare direct dependencies, a composite build can declare task
   dependencies on its included builds." Sample: `dependsOn(gradle.includedBuild("my-app").task(":app:run"))`.
   <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/structuring-builds/composite_builds.adoc>
   and
   <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/snippets/structuring-builds/composite-builds-basic/kotlin/build.gradle.kts>,
   tag v9.7.1.
2. **`Gradle.includedBuild(name)` searches this build's own included builds only and throws when absent
   or too early.** `for (IncludedBuild includedBuild : includedBuilds()) {` /
   `throw new UnknownDomainObjectException("Included build '" + name + "' not found in " + this + ".");`
   and `throw new IllegalStateException("Included builds are not yet available for this build.");`.
   <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core/src/main/java/org/gradle/invocation/DefaultGradle.java>,
   tag v9.7.1.
3. **API contracts.** "Returns the parent build of this build, if any." (`getParent`, "May return null");
   "Returns the included builds for this build."; "Returns the included build with the specified name for
   this build.". <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core-api/src/main/java/org/gradle/api/invocation/Gradle.java>,
   tag v9.7.1. "Produces a reference to a task in the included build." (`IncludedBuild.task`)
   <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core-api/src/main/java/org/gradle/api/initialization/IncludedBuild.java>,
   tag v9.7.1.
4. **Task references are absolute-path, lazy, built from the included build's own factory.** "The task
   path is evaluated lazily, meaning the referenced task will only be looked-up in the target project when
   the returned task reference's dependencies are visited." and "Task path '%s' is not a qualified task
   path (e.g. ':task' or ':project:task')."
   <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core/src/main/java/org/gradle/api/internal/tasks/DefaultTaskReference.java>;
   `return DefaultTaskReference.create(pathStr, taskDependencyFactory);` in `IncludedBuildImpl.task`,
   <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/composite-builds/src/main/java/org/gradle/composite/internal/DefaultIncludedBuild.java>,
   tag v9.7.1.
5. **Plugin builds are in the build's included builds.** `includedBuildSpecs.addAll(((PluginManagementSpecInternal) getPluginManagement()).getIncludedBuilds());`
   in `DefaultSettings.pluginManagement`.
   <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/core/src/main/java/org/gradle/initialization/DefaultSettings.java>,
   tag v9.7.1 (also quoted in the TestKit harness findings).
6. **Build paths are qualified by the containing included build.** "If there is an ancestor, then we use it
   to qualify the path of the build we are adding" and `"Included build " + dir + " has build path " +
   requestedPath + " which is the same as included build " + existingForPath`.
   <https://github.com/gradle/gradle/blob/v9.7.1/subprojects/composite-builds/src/main/java/org/gradle/composite/internal/DefaultIncludedBuildRegistry.java>,
   tag v9.7.1. Documentation: "*Unique Build Paths:* No two included builds can share the same build
   path." and "NOTE: When a composite build includes another composite, Gradle flattens the structure. All
   included builds share the same parent, regardless of how deeply they were nested." in the
   `composite_builds.adoc` of claim 1.
7. **The root does not run an included build's tests by default.** "By default, tests within the included
   `build-logic` build are not executed when you run tests in the root project. Because the root project
   only requires the build artifacts from `build-logic`, Gradle will build the project without running its
   internal tests. To run them, you must explicitly invoke the tasks: `./gradlew :build-logic:check`."
   <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/best-practices/best_practices_testing.adoc>,
   tag v9.7.1.
8. **Task selectors do not cross builds (open issue).** Issue title "Support invoking task selectors on
   included builds", state OPEN on 2026-10-25. <https://github.com/gradle/gradle/issues/22335>.
9. **`check` is the lifecycle task for verification; `build` is not for concrete tasks.** "Plugins and
   build authors should attach their verification tasks, such as ones that run tests, to this lifecycle
   task using `check.dependsOn(__task__)`." and "You will probably rarely attach concrete tasks directly to
   `build` as `assemble` and `check` are typically more appropriate."
   <https://github.com/gradle/gradle/blob/v9.7.1/platforms/documentation/docs/src/docs/userguide/reference/core-plugins/base_plugin.adoc>,
   tag v9.7.1.
10. **Mature repositories wire an included build's task into the root `check` with the same API.** haze:
    `tasks.named("check") {` / `// gradle/build-logic is an included build, so `check` doesn't reach its
    tests by name.` / `dependsOn(gradle.includedBuild("build-logic").task(":convention:test"))`, commit
    `29056f286dd3c2deaf573283898925c263faf24d`, `build.gradle.kts`. avito-android:
    `dependsOn(gradle.includedBuild("build-logic").task(":check"))`, commit
    `50e3b4c916c867ed950be00e37e3412234842388`, `build.gradle.kts`. kotlinx-benchmark:
    `// region Workarounds for https://github.com/gradle/gradle/issues/22335`, commit
    `f65212cbd41506cf5c962a681266555d2af9ff41`, `build.gradle.kts`.
11. **`gradle.parent == null` is the idiom for "root build".** androidx/media:
    `// Check if this is the root standalone build` / `if (gradle.parent == null) {` in
    `build-logic/src/main/kotlin/media3.publish.gradle.kts`, commit
    `8c6678b657ede1e7883fc164ef73ed483c7796c3`. (moko-resources and nessie `settings.gradle.kts` also
    contain `if (gradle.parent == null) {` per GitHub code search on 2026-10-25; those files were not
    opened.)
12. **M1, wiring runs the check and is incremental, with the cache reused.** Producer root, option 1/2
    wiring, `gradle build --configuration-cache` twice after an input edit. Observed: first run
    `> Task :build-logic:publicationCheck`; second run `Reusing configuration cache.`,
    `> Task :build-logic:publicationCheck UP-TO-DATE`, `Configuration cache entry reused.`
    (`BUILD SUCCESSFUL in 710ms`). `gradle check --dry-run` lists `:build-logic:publicationCheck SKIPPED`
    before `:lib:check SKIPPED`.
13. **M2, the root gains no task.** `gradle tasks --all --configuration-cache` in the producer root,
    normalised to a sorted list of task names, diffed against the unwired build: empty diff for the lazy,
    eager and guarded wirings. For the root-`check` wiring the diff is `> check`; with root `build` it is
    `> build`, `> check`; with `lifecycle-base` it is `> assemble`, `> build`, `> check`, `> clean`.
14. **M3, the consumer composite.** Consumer root with `includeBuild("../producer")`, own
    `pluginManagement { includeBuild("build-logic") }`: `gradle build`, `gradle :app:compileJava`,
    `gradle :producer:lib:check` and `gradle :producer:lib:build` for the guarded wiring print no
    `publicationCheck` line and no error or warning; `gradle :app:dependencies --configuration
    runtimeClasspath` prints `\--- com.probe:lib:1.0.0 -> project ':producer:lib'`. For the unguarded
    wirings `gradle :producer:lib:check` prints `> Task :producer:build-logic:publicationCheck`, and a
    repeat prints `> Task :producer:build-logic:publicationCheck UP-TO-DATE` with `Reusing configuration
    cache.`
15. **M4, identity as seen from each build.** A script printing `gradle.parent` and
    `gradle.includedBuilds` in the producer module. Standalone: `parent=null parentIsNull=true
    includedBuilds=[build-logic@build-logic]`. From the consumer: `buildPath=:producer parent=build ':'
    parentIsNull=false includedBuilds=[build-logic@build-logic]`, and
    `parent.includedBuilds=[build-logic@consumer/build-logic, producer@.../producer]`;
    `includedBuild('build-logic')` resolved to `producer/build-logic`.
16. **M5, build-path clash.** Producer settings changed to `pluginManagement { includeBuild("../shared/build-logic") }`;
    consumer `gradle build` fails: "Included build ...shared\build-logic has build path :build-logic which
    is the same as included build ...consumer\build-logic". With `includeBuild("../producer") { name = "webtools" }`
    and the in-directory plugin build, `gradle :webtools:lib:check` runs
    `> Task :webtools:build-logic:publicationCheck` and succeeds.
17. **M6, inputs outside the project directory.** Edit sequence in F8; observed lines include
    `Calculating task graph as configuration cache cannot be reused because file 'settings.gradle.kts' has
    changed.` and `... because an input to plugin class
    'org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins' has changed.`, and, for edits
    under `../lib/src`, `Reusing configuration cache.` with `> Task :build-logic:publicationCheck`
    executed.
18. **M7, missing directory input.** `inputs.dir("../lib/src/jvmTestFixtures")` with no such directory:
    `> Task :build-logic:publicationCheck FAILED`, "Input file does not exist", "specifies directory ...
    which doesn't exist". `inputs.files(fileTree("../lib/src/jvmTestFixtures"))`: `BUILD SUCCESSFUL`; after
    creating the directory with a file, the next run executes the task.
19. **M8, string path.** `tasks.named("check") { dependsOn(":build-logic:publicationCheck") }`:
    "Project with path ':build-logic' not found in build ':'." `BUILD FAILED`.
20. **M9, CLI path to the plugin build.** `gradle :build-logic:publicationCheck --configuration-cache` from the
    producer root: `> Task :build-logic:publicationCheck`, `BUILD SUCCESSFUL`.
