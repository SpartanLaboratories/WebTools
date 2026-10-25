# Opting the module's main code into its own subclass gate while the tests stay a consumer

## Question

How does a Kotlin 2.2.0 multiplatform module (`webtools-http`: one `jvm()` target, `explicitApi()`,
Kotlin Gradle plugin 2.2.0, Gradle 9.7.1, configuration cache on) opt its own `commonMain` and
`jvmMain` into the `@SubclassOptInRequired` gate of its ports and failure types, while `commonTest`
and `jvmTest` compile without the opt-in and so stay a consumer? The same `commonMain` directory is
compiled for `js` by the unpublished module `webtools-http-commoncheck`, so the answer has to hold
there too. The answer fixes the exact build lines, the declaration shapes of the gated types and
their supplied defaults, and what the unit's opening compile proof must show.

## Bottom line

- **Wire the opt-in as a language setting of the main source sets, in each module's own build file.**
  `webtools-http`: `commonMain` and `jvmMain`. `webtools-http-commoncheck`: `commonMain` and `jsMain`.
  Measured on KGP 2.2.0 / Gradle 9.7.1: main compiles, `commonTest` and `jvmTest` stay consumers, all
  eight negative cases fail with the gate error, and the same eight with `@OptIn` compile. The
  configuration cache is reused.
- **Only the leaf source set reaches the compiler in a single-target module.** `commonMain` alone is
  silently inert for the `jvm` compile (and `commonMain` alone is inert for the `js` compile), so the
  `jvmMain` / `jsMain` line is the one that does the work. The `commonMain` line is kept for
  consistency (a second target would make KGP require it) and for tooling that reads per-source-set
  settings.
- **Three of the four options keep the tests a consumer; one does not.** The language setting on the
  main source sets, a `compileTaskProvider` option on the main compilation, and a per-declaration
  `@OptIn` all work. A `kotlin { compilerOptions { optIn.add(..) } }` (and the target-level or
  `sourceSets.all { }` forms) reaches the test compilations and turns the negative cases into
  successes: it is not usable. Add a small guard task that fails the build if the marker ever appears
  in a test compilation's opt-ins.
- **A nested declaration inside a gated type needs no opt-in at all.** The compiler accepts any class
  lexically inside a declaration annotated `@SubclassOptInRequired(Marker)`. Nested leaves
  (`TransportFailure.Timeout`) and a nested `WallClock.System` therefore compile with the module-wide
  option absent. The opt-in first matters for a top-level implementer or any implementer in
  `jvmMain`/`jsMain`, so the proof must include one throwaway top-level implementer per source set or
  it passes vacuously.
- **Declaration shapes.** Nested `public object System : WallClock` (the `TimeSource.Monotonic`
  shape) over a companion `val`; leaves nested in their group; the groups themselves
  `@SubclassOptInRequired`, abstract, with `protected` constructors. `@SubclassOptInRequired` is in the
  class files as `RuntimeInvisibleAnnotations` and absent from the ABI dump. Under `explicitApi()`,
  interface members need `public`, an `override` needs no visibility but does need its return type
  written out.

## Findings

### F1. Which declarations need the module-wide opt-in at all (source-read, measured; Kotlin 2.2.0)

- The check that reports the gate asks whether the opt-in is "acceptable in context". It walks every
  lexically enclosing annotation container, and for an inheritance use it accepts a container annotated
  `@SubclassOptInRequired(<same marker>)`. Source (`FirOptInUsageBaseChecker.isExperimentalityAcceptableInContext`
  and `isExperimentalityAcceptable`, tag `v2.2.0`, claim 2).
- Measured with all module-wide mechanisms off (`gate=none`), `compileKotlinJvm` succeeds for:
  - the groups `TransportFailure` and `UpstreamFailure` (they extend the gated `HttpFailure` but carry
    their own `@SubclassOptInRequired`, which the stdlib KDoc names as a third way to opt in, claim 1);
  - every leaf nested in a gated group (`HttpFailure.Closed`, `TransportFailure.Timeout`,
    `UpstreamFailure.RateLimited`);
  - `WallClock.System` nested in `WallClock`, and an anonymous `object : WallClock` inside a companion
    nested in `WallClock`.
- Measured to fail with the gate error when the module-wide option is off, and to compile when it is on:
  a **top-level** `public class TopLevelTimeout(..) : TransportFailure(..)`, a top-level
  `public class FixedClock : WallClock`, and a `jvmMain` `public class JvmTransport : HttpTransport`
  (and a `jsMain` `JsClock : WallClock` in the check module).
- Consequence for this unit: all of its committed implementers are nested or group-annotated, so the
  unit's own code never needs the module-wide opt-in. The first implementer that does is the first
  top-level or platform-source-set implementer a later unit adds (the OkHttp transport in `jvmMain`, the
  fixtures' fakes, a default budget). A compile proof that only builds the committed code cannot fail
  when the wiring is wrong. See *Recommendation*, step 3.
- The Kotlin documentation says that with `@SubclassOptInRequired` on a class "the opt-in requirement is
  not propagated to any inner or nested classes" (claim 8). That sentence is about the gate not
  applying to the nested class as a supertype. It does not contradict the above: the nested class
  extending its own gated parent is still accepted by the checker.

### F2. How each mechanism reaches the compilations (measured; source-read for why)

Probe: one multiplatform project, `jvm()` at `jvmTarget` 11 with `-Xjdk-release=11`, `explicitApi()`,
the marker, the ports and the failure types of this report, plus `commonTest` and `jvmTest`. Each row
runs `compileKotlinJvm` (main) and `compileTestKotlinJvm` (tests) with main containing a top-level
implementer, a `jvmMain` implementer, and a test file `Neg.kt` of the eight negative cases (F10).

| Mechanism | Main compiles | Tests stay a consumer (all 8 negatives fail) | Notes |
|---|---|---|---|
| none | no (3 gate errors on the throwaway implementers) | yes | baseline |
| `commonMain { languageSettings.optIn(M) }` and `jvmMain { languageSettings.optIn(M) }` | yes | yes | recommended |
| `jvmMain { languageSettings.optIn(M) }` only | yes | yes | same compile effect |
| `commonMain { languageSettings.optIn(M) }` only | **no** (errors in `commonMain` and `jvmMain` files) | yes | `commonMain` setting inert, F3 |
| `jvm { compilations.getByName("main").compileTaskProvider.configure { compilerOptions.optIn.add(M) } }` | yes | yes | same compile effect; documented form |
| all main compilations via `kotlin.targets.configureEach { compilations.matching { name == "main" } ... compileTaskProvider ... }` | yes | yes | also matches the metadata `main`, which is `SKIPPED` here |
| per-declaration `@OptIn(M::class)` on each top-level implementer, no build option | yes | yes | the 3 throwaways each carry the annotation |
| `kotlin { compilerOptions { optIn.add(M) } }` | yes | **no** (negatives compile) | reaches every compilation |
| `kotlin { jvm { compilerOptions { optIn.add(M) } } }` (target level) | yes | **no** | reaches the target's test compilation |
| `sourceSets.all { languageSettings.optIn(M) }` | yes | **no** | includes `commonTest`/`jvmTest` |

- `commonMain`/`commonTest` language settings do not reach `jvmTest` on their own: a `commonTest`-only
  opt-in left all eight negatives failing too (the guard task in F11 still flags it, because the
  setting is recorded on the source set).
- Associated compilations carry nothing across: the `jvm` `test` compilation is associated with `main`
  by default, and main's `optIn` did not appear in the test compilation under any main-only mechanism.
- Source for why: `languageSettings.optIn(name)` does `compilationCompilerOptions.await().optIn.add(name)`
  (claim 3), and `compilationCompilerOptions` is completed with the options of the compilation whose
  default source set it is (claim 4). The language setting of `jvmMain` therefore *is* the `main`
  compilation's `compilerOptions.optIn`; the two spellings are the same data.

### F3. KGP 2.2.0 consistency checks, and what it reports (source-read, measured)

- **Checked: source sets joined by `dependsOn`.** `DefaultKotlinSourceSet.afterDependsOnAdded` runs
  `defaultSourceSetLanguageSettingsChecker.runAllChecks(this, other)` for the dependent and its
  dependency. The three checks are language version, unstable language features and the set of opt-in
  annotations ("The dependent source set must use all opt-in annotations that its dependency uses.")
  (claim 5). A *leaf that uses fewer opt-ins than its parent* fails at configuration; a leaf that uses
  more is fine.
- **Not checked: main against test, associated compilations.** No code in KGP 2.2.0 compares
  `jvmMain` with `jvmTest` or follows `associateWith`; `jvmTest` depends on `commonTest`, not on any
  main source set. Measured: main opted in, tests not, with `--warning-mode all`: `BUILD SUCCESSFUL`, no
  KGP message about it.
- **The check is vacuous in a single-target module.** A shared source set that no compilation uses as
  its default source set gets "a separate instance of common compiler options" (claim 4), which is not
  completed when the `dependsOn` check runs, and the check treats a not-yet-completed side as "no
  value". In both `webtools-http` (jvm only) and the check module (js only) `commonMain` is such a set.
  Measured: `commonMain { optIn(M) }` alone configures without error. This is also why that setting does
  nothing for the compile (F2).
- **It fires as soon as a second target exists.** Measured in a scratch project with `jvm()` and
  `js()`, `commonMain { languageSettings.optIn(..) }` alone fails the configuration:

  ```text
  A problem occurred configuring project ':multi'.
  > Inconsistent settings for Kotlin source sets: 'jsMain' depends on 'commonMain'
    'jsMain': set of opt-in annotations in use is []
    'commonMain': set of opt-in annotations in use is [kotlin.ExperimentalStdlibApi]
    The dependent source set must use all opt-in annotations that its dependency uses.
  ```

  Setting the opt-in on `jvmMain` only (leaf only) configured fine. So setting `commonMain` *and* every
  leaf is the shape that stays valid when a target is added.
- KGP carries `// FIXME: KT-75216 Synchronise common source set language settings with compile tasks in a better way`
  in `LanguageSettingsSetupAction` (claim 6): the commonMain-versus-compile-task relationship is a known
  rough edge, not a stable contract.

### F4. Naming the marker where the compilation cannot see it: a warning, not an error (measured)

A separate module with no dependency on the marker, with `compilerOptions { optIn.add("<marker FQN>") }`
(or `jvmMain { languageSettings.optIn("<marker FQN>") }`), builds successfully and prints:

```text
w: Opt-in requirement marker <marker FQN> is unresolved. Please make sure it's present in the module dependencies
```

An option naming a class that is not an opt-in marker prints
`w: Class kotlin.Deprecated is not an opt-in requirement marker`. Consequence: the option belongs in
the build file of the module that owns the marker (and of the check modules that compile its source),
not in the shared convention plugin, or every other WebTools module would warn on every compile. In
`webtools-http` every compilation sees the marker (it is main code, and the test compilations see main's
output), so no warning appears; none appeared in any probe run with `--warning-mode all`.

### F5. The check module needs its own opt-in; `jsMain` is the line that works (measured)

- `webtools-http-commoncheck` is a separate Gradle project. Setting the option in the probe's `lib`
  project (all-compilations form) did not reach it: with the check module's own option off, `compileKotlinJs`
  failed on the shared directory's top-level implementers and on a `jsMain` implementer. The option has
  to be written in the check module's build file.
- In the check module, `jsMain { languageSettings.optIn(M) }` alone made `compileKotlinJs` succeed;
  `commonMain { .. }` alone did not (same inertness as F3). `commonMain` and `jsMain` together, and a
  module-wide `compilerOptions { optIn.add(M) }`, also succeeded. The check module has no test
  source sets that matter, so the module-wide form is acceptable there if preferred.
- The shared directory itself compiled for `js` unchanged: the groups, nested leaves, the nested
  `System`, `internal expect fun` with a one-line `jsMain` actual, `kotlin.AutoCloseable`, a `suspend`
  member returning `Result`. `./gradlew :commoncheck:compileKotlinJs` printed no Node.js, Yarn or download
  line, and the probe root had no `kotlin-js-store/` afterwards (with `js { nodejs { testTask { enabled = false } } }`).
- The language-setting form also works through the repository's layout: a precompiled convention plugin
  in an included `build-logic` build applying `org.jetbrains.kotlin.multiplatform`, with the module build
  files containing only the `kotlin { explicitApi(); sourceSets { commonMain { .. }; jvmMain { .. } } }`
  lines of the recommendation. The `commonMain {}`, `jvmMain {}` and `jsMain {}` accessors resolved there.

### F6. `@SubclassOptInRequired` is in the class files and not in the dump (measured, documented)

- The annotation is `@Retention(BINARY)` (claim 1). `javap -v -p` on the compiled classes shows, on
  `WallClock`, `HttpTransport`, `HttpFailure` and `TransportFailure`:

  ```text
  RuntimeInvisibleAnnotations:
    0: #7(#8=[c#9])
      kotlin.SubclassOptInRequired(
        markerClass=[class Lcom/probe/http/ExperimentalForInheritanceWebToolsApi;]
      )
  ```

  It is not on the nested `WallClock$System` or the leaves (they do not carry it). The marker class
  carries `RuntimeInvisibleAnnotations: kotlin.RequiresOptIn(message="...full text...", level=...ERROR)`,
  which is what a consumer's compiler reads to print the full message.
- The binary-compatibility-validator 0.18.2 dump (`apiDump`, KGP 2.2.0) lists the marker as
  `public abstract interface annotation class .../ExperimentalForInheritanceWebToolsApi : java/lang/annotation/Annotation`
  and lists `HttpFailure`, `TransportFailure` and `UpstreamFailure` as `public abstract class` with
  `protected fun <init>` entries, but contains no trace of `@SubclassOptInRequired`. Dropping the
  annotation from a port would pass `apiCheck`. This matches the earlier finding for kotlinx.coroutines'
  own dump.
- Two further dump facts for the committed `api/webtools-http.api`, outside this question but visible in
  it (measured):
  - `HttpTransport.execute` appears as `execute-gIAlu-s (Lcom/probe/http/HttpRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`:
    a `suspend` function returning `Result` has a mangled JVM name.
  - `HttpTransport` appears with `public fun close ()V` plus a public `HttpTransport$DefaultImpls`
    class: the default `close()` body is emitted in both forms under the default `-jvm-default` mode.

### F7. `explicitApi()` and a gated interface (measured; documented for visibility)

On the probe, with `explicitApi()` in strict mode, `compileKotlinJvm` reported:

| Declaration | Result |
|---|---|
| member of a `public interface` with no visibility (`fun a(): Long`, `suspend fun b()`, `val p: Int`) | `Visibility must be specified in explicit API mode.` (3 errors) |
| `override fun a() = 1L`, `override val p = 1`, `override suspend fun b() = ...` in a public class | `Return type must be specified in explicit API mode.` (3 errors); **no** visibility error |
| `override fun a(): Long = 1L` with the type written | accepted without `public` |
| `public class Ex5 : HttpFailure("x")` and `public class Ex6 : TransportFailure("x")` | accepted (primary constructors are exempt) |
| `abstract fun f()` in a public abstract class | visibility error; `protected abstract fun g()` accepted |
| `class Ex8(val x: Int)` constructor property | visibility error on `val x` |

So in the gated interfaces every member is written `public ...` (as in the sketches), and every
`override` in a default or a fake writes its return type but no `public`. The KEEP lists the visibility
exclusions: "Primary constructors", "Properties of data classes and annotations", "Methods marked with
`override`", "Property getters and setters" (claim 9); the return-type requirement has no exclusion
there, matching the measurement. The nested `public object System : WallClock` needs `override fun
nowEpochMillis(): Long` (type, no visibility) and a `public` on the object itself.

### F8. `WallClock.System`: nested object over a companion `val` (measured; precedent source-read)

Both shapes compile in main with the module-wide opt-in off (an object nested in a gated interface is
accepted, F1), both are usable in the test source sets without the opt-in, and both work as a default
parameter value for a function and for a class constructor, in `commonTest` and `jvmTest`.

| | nested `public object System : WallClock` | `public companion object { public val System: WallClock = object : WallClock {..} }` |
|---|---|---|
| `WallClock.System.nowEpochMillis()`, `= WallClock.System` default | works | works |
| `x is WallClock.System`, `System` usable as a type | yes | no, the value's type is `WallClock` and the class is anonymous |
| names added to the ABI dump | `WallClock$System` (final, `INSTANCE`, `nowEpochMillis`, `toString`) | `WallClock2$Companion` and a static `Companion` field and `getSystem()` |
| public names the port now owns | `System` | `Companion` and `System` |
| precedent | stdlib `TimeSource.Monotonic` | none of the repository's |
| `toString()` | override reads "WallClock.System" | override on the anonymous object |

The stdlib precedent is `public interface TimeSource { ... public object Monotonic : TimeSource.WithComparableMarks { ... } }`
in `libraries/stdlib/src/kotlin/time/TimeSource.kt` at `v2.2.0` (claim 10). The nested object gives the
named default a type and one dump entry; the companion gives the port a permanent public `Companion`.

Name-resolution check: a top-level class in the package shadows a star import silently (measured:
`package com.probe.http; import java.util.*; public class Date(..)` then `Date(1).x` compiles to the
package's own `Date`). A nested `System` does not clash with `java.lang.System` in `jvmMain` files of
the package, because a nested class is not a package-level name; only a file that imports
`com.spartanlabs.webtools.http.WallClock.System` explicitly would shadow `java.lang.System`.

### F9. Leaves nested in their group, or top-level (measured; convention)

- **What compiles.** Both. Nested leaves compile with no opt-in (F1); top-level leaves need the
  module-wide opt-in or a per-declaration `@OptIn`. A group with a `protected` constructor can be called
  from a nested or top-level leaf alike.
- **What the dump shows.** Nested: `HttpFailure$Closed`, `TransportFailure$Timeout`,
  `UpstreamFailure$RateLimited`, each `public final class ... : <group>`. An `internal constructor`
  leaf (`Closed`) shows no constructor line at all. Top-level: `TopLevelTimeout` and the like, in the
  package's flat namespace.
- **Idiom.** Nested is the shape of a sealed hierarchy (`Failure.Timeout`), reads as grouping at the
  `is`/`when` site (`is TransportFailure.Timeout`), and keeps short names like `Timeout`, `Protocol`,
  `Io`, `Closed` out of a flat package where the star-import shadowing above can hide an OkHttp or okio
  name of the same spelling. The repository's one existing open hierarchy, `webtools-udp`'s
  `ReliableSendFailure`, uses top-level leaves named `...Exception`
  (`webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ReliableSendFailure.kt` and its two
  subclasses); that module is a JVM module with two leaves and no groups.
- The `protected` constructors on the abstract types worked for: nested leaves, a top-level leaf,
  and a consumer `class N4Failure : HttpFailure("x")` / `class N5Transport : TransportFailure("x")`
  with `@OptIn`. A `public constructor` in an abstract group also compiles (without the module-wide opt-in too, because
  the group carries its own annotation); I did not take its dump or repeat the consumer cases for it.
  `protected` is the narrower contract (nothing outside a subclass can name it) and the dump showed it
  as `protected fun <init>`.
- A group written with a primary constructor and a property
  (`public abstract class UpstreamFailure protected constructor(public val status: Int, message: String) : HttpFailure(message)`)
  compiles, including a leaf with a `kotlin.time.Duration` property (`RateLimited(status, retryAfter)`).
  The earlier sketch `public abstract class UpstreamFailure(public val status: Int) : HttpFailure` does
  not compile: `This type has a constructor, so it must be initialized here.` With a primary
  constructor the super constructor call must be written there.

### F10. The gate diagnostics, verbatim (measured; KGP 2.2.0, Kotlin 2.2.0)

In the test source sets (cross-module: the compiler reads the marker's message from the compiled main
output), identical text for every case, reported at the supertype reference:

```text
This class or interface requires opt-in to be implemented: Implementing or substituting this WebTools seam is Experimental and may change in a minor release. Using the supplied implementations needs no opt-in. Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).
```

| Case (in `commonTest`; also confirmed in `jvmTest`) | Reported at |
|---|---|
| `class N1Clock : WallClock { override fun nowEpochMillis(): Long = 0 }` | `WallClock` after the colon |
| `class N2Delegating(c: WallClock) : WallClock by c` | the supertype `WallClock` (delegation is an implementation) |
| `val n3 = object : HttpTransport { override suspend fun execute(..) = .. }` | `HttpTransport` after `object :` |
| `class N4Failure : HttpFailure("x")` | `HttpFailure` |
| `class N5Transport : TransportFailure("x")` | `TransportFailure` |
| `class N6Upstream : UpstreamFailure(500, "x")` | `UpstreamFailure` |
| `val n7 = object : WallClock { .. }` | `WallClock` |
| `class N8Transport : HttpTransport { .. }` | `HttpTransport` |

Eight errors, no other diagnostic. The same eight, each prefixed `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`,
compile with no error and no warning. Inside the module (same-module use, F1's throwaway implementers), the
text is the compiler's default instead:

```text
This class or interface requires opt-in to be implemented. Its usage must be marked with '@com.probe.http.ExperimentalForInheritanceWebToolsApi' or '@OptIn(com.probe.http.ExperimentalForInheritanceWebToolsApi::class)'
```

(the probe's package is `com.probe.http`; in the module it is `com.spartanlabs.webtools.http`). The in-module checker reads the marker's `message`
only when it is a single string literal (claim 2); the marker's message is built with `+`, so main code
sees the default text and consumers, who read the compiled annotation, see the full message. Measured:
with the message as one literal, the in-module error carries it too. Nothing depends on this; it only
means the module's own error text differs from the consumer's.

Positive cases that compiled without the opt-in in `commonTest` and `jvmTest` (and ran green under
`jvmTest`): the gated types in function and constructor signatures; `WallClock.System.nowEpochMillis()`;
construction of the public final leaves; `is` and `when` over the groups with `else`;
`fun now(clock: WallClock = WallClock.System)`; `class UsesClock(val clock: WallClock = WallClock.System)`;
a library class `Fetcher(clock: WallClock = WallClock.System)` and a top-level
`elapsed(since, clock = WallClock.System)` in main. No `@OptIn` and no warning about the gate appeared.

### F11. A guard that the tests stay a consumer is cheap and cacheable (measured)

A task class with `@Input` list properties wired from
`kotlin.jvm().compilations.getByName("test").compileTaskProvider.flatMap { it.compilerOptions.optIn }`
and from the `languageSettings.optInAnnotationsInUse` of every source set whose name ends in `Test`
fails with a message when the marker is in either. It passed (`BUILD SUCCESSFUL`) for: no option, the
language setting on `commonMain`+`jvmMain`, `jvmMain` only, the `compileTaskProvider` option, and
failed for: `kotlin { compilerOptions { } }`, the target-level option, `sourceSets.all { }`, and a
`commonTest`-only language setting. It ran under the configuration cache. It checks the build
configuration, not the compile, so it complements, and does not replace, the negative compile cases.

### F12. Per-declaration `@OptIn` is fully workable here, and is what kotlinx.coroutines does (measured, source-read)

With no build option, the two top-level implementers and the `jvmMain` implementer each annotated
`@OptIn(ExperimentalForInheritanceWebToolsApi::class)` compiled, and the test negatives still failed.
For *this* unit's committed code no annotation is needed at all (F1). The cost appears later: every
top-level or platform-source-set implementer in the module (the OkHttp transport, the default budget
and cache, any fake in main) carries an annotation, and forgetting one is a loud compile error, never a
silent gap. The marker has `@Target()`, which does not forbid `@OptIn(Marker::class)` (measured: it
compiled).

## How comparable projects do it

| Project | Gate | How the project opts its own code in | Source |
|---|---|---|---|
| kotlinx.serialization 1.9.0 | `SealedSerializationApi`, `@Target()`, `@SubclassOptInRequired` on `SerialDescriptor` | **Project-wide**: in `source-sets-conventions.gradle.kts`, `compilerOptions { optIn.addAll(listOf(..., "kotlinx.serialization.SealedSerializationApi")) }`, and again `languageSettings { optIn("kotlinx.serialization.SealedSerializationApi") }` for every source set whose name contains `Test`. Its tests are therefore not a consumer of the gate. | `buildSrc/src/main/kotlin/source-sets-conventions.gradle.kts` at tag `v1.9.0` (claim 11) |
| kotlinx.coroutines 1.10.2 | `ExperimentalForInheritanceCoroutinesApi` (WARNING) on `Job`, `SharedFlow`, `StateFlow` | **Per declaration**: `@OptIn(ExperimentalForInheritanceCoroutinesApi::class) private class StateFlowImpl<T>(..) : AbstractSharedFlow<..>(), MutableStateFlow<T>, ..` and three more private classes in `flow/`. The global opt-in list in `GlobalKotlinCompilerOptions.kt` does not contain this marker. | `kotlinx-coroutines-core/common/src/flow/StateFlow.kt`, `buildSrc/.../GlobalKotlinCompilerOptions.kt` at tag `1.10.2` (claim 12) |
| Kotlin stdlib 2.2.0 | `TimeSource` with nested `public object Monotonic` | n/a (stdlib); the precedent for a supplied default as a nested object of its interface | `libraries/stdlib/src/kotlin/time/TimeSource.kt` at `v2.2.0` (claim 10) |

The two libraries sit at opposite ends. kotlinx.serialization opts everything in, tests included, so
its own tests cannot catch an accidental contamination of a default path. kotlinx.coroutines opts in
only where it implements the interface, which is exactly the property wanted here, bought with an
annotation per implementer. The requirement "tests are a consumer" is met by the second and by the
main-only build options; no comparable library found compiles its tests as a consumer through a build
option, so the combination in this report is not copied from one.

## Pitfalls

1. **Passing vacuously.** A proof that builds only nested implementers succeeds with the opt-in wiring
   missing (F1). The opt-in is exercised only by a top-level or platform-source-set implementer.
2. **`commonMain { languageSettings.optIn }` looks like it works and does nothing** for the compile in a
   single-target module (F2, F3, F5). Only the leaf (`jvmMain`, `jsMain`) line is effective.
3. **The documented module-wide form contaminates the tests.** The Kotlin documentation's multiplatform
   example is `kotlin { compilerOptions { optIn.add(..) } }` (claim 7); it reaches every compilation.
   `sourceSets.all { languageSettings.optIn(..) }`, used in the DSL reference, does too.
4. **`compilation.compilerOptions` is deprecated in 2.2.0.** Using `compilations.getByName("main").compilerOptions.configure { }`
   works and prints `'val compilerOptions: HasCompilerOptions<KotlinJvmCompilerOptions>' is deprecated.
   To configure compilation compiler options use 'compileTaskProvider': compilation.compileTaskProvider.configure{ compilerOptions {} }`.
5. **The option placed in the shared convention plugin** would warn "Opt-in requirement marker ... is unresolved"
   in every other module (F4).
6. **A second target breaks a `commonMain`-only setting at configuration** (F3).
7. **Top-level names silently win over star imports** (F8, F9): a top-level `Timeout`/`Protocol` leaf in
   the flat package would shadow `import okio.*` / `import okhttp3.*` names in `jvmMain` without a
   diagnostic.
8. **The ABI dump does not protect the gate** (F6). A change that removes `@SubclassOptInRequired` from a
   port, or that adds the marker directly to a declaration, is invisible to `apiCheck`; `@Target()`
   rejects the second (measured: `This annotation is not applicable to target 'class'`), nothing
   rejects the first except a compile-fail case.
9. **An `open` or non-final supplied default re-opens the gate** (earlier measurement in
   `../../research/test-support-and-time.md` F4): the requirement is not inherited through an opted-in
   subclass. `WallClock.System` is a nested `object` (final); keep every supplied default `final`.
10. **Message text differs in-module and cross-module** when the marker's `message` is not one string
    literal (F10); harmless, but a test that asserts the text must run in a consumer source set.

## Recommendation for this repository

### Build lines

`webtools-http/build.gradle.kts` (the convention plugin already supplies `jvm()`, the Java floor and
test dependencies; it does not touch opt-ins and must not):

```kotlin
// The marker the module's own ports are gated by.
val gateMarker = "com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi"

kotlin {
    explicitApi()
    sourceSets {
        // Main code implements the module's own gated ports. commonTest and jvmTest are
        // deliberately left out: they stay a consumer, so a test cannot implement a port, delegate
        // to one or subclass a failure group without an explicit @OptIn.
        commonMain { languageSettings.optIn(gateMarker) }
        jvmMain { languageSettings.optIn(gateMarker) }
    }
}
```

`webtools-http-commoncheck/build.gradle.kts`, written in the check module (the module's own option does
not reach it); the `webtools.kotlin-common-check` plugin provides `js { nodejs { testTask { enabled = false } } }`:

```kotlin
val gateMarker = "com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi"

kotlin {
    explicitApi()
    sourceSets {
        commonMain {
            kotlin.srcDir(rootProject.file("webtools-http/src/commonMain/kotlin"))
            languageSettings.optIn(gateMarker)
        }
        jsMain { languageSettings.optIn(gateMarker) }
    }
}
```

Guard in `webtools-http/build.gradle.kts` (wired into `check`), keeping the tests honest:

```kotlin
abstract class VerifyTestsAreConsumers : DefaultTask() {
    @get:Input abstract val marker: Property<String>
    @get:Input abstract val testCompileOptIns: ListProperty<String>
    @get:Input abstract val testSourceSetOptIns: ListProperty<String>
    @TaskAction fun verify() {
        check(marker.get() !in testCompileOptIns.get() + testSourceSetOptIns.get()) {
            "The test code opts in to ${marker.get()}; tests must stay a consumer without the opt-in."
        }
    }
}
val verifyTestsAreConsumers = tasks.register<VerifyTestsAreConsumers>("verifyTestsAreConsumers") {
    marker.set(gateMarker)
    testCompileOptIns.set(kotlin.jvm().compilations.getByName("test").compileTaskProvider.flatMap { it.compilerOptions.optIn })
    testSourceSetOptIns.set(provider { kotlin.sourceSets.filter { it.name.endsWith("Test") }.flatMap { it.languageSettings.optInAnnotationsInUse } })
}
tasks.named("check") { dependsOn(verifyTestsAreConsumers) }
```

Why this over the alternatives: `kotlin { compilerOptions { } }` (the documented form) removes the test
consumer and is rejected for that reason alone. The `compileTaskProvider` option on `main` is an equal
alternative for the compile (F2) and is the form the documentation steers toward for non-shared settings;
the language setting is chosen because `commonMain` is a shared source set whose settings tooling and a
future second target both read per source set, and because the pair `commonMain` + leaf is the only shape
that stays valid when a target is added (F3). Per-declaration `@OptIn` is a sound alternative (F12) and
is what the unit's own code needs anyway (nothing); the module-wide form is chosen for the later units'
many default implementers.

### Declaration shapes

```kotlin
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target()
@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "…")   // as in the earlier sketch
public annotation class ExperimentalForInheritanceWebToolsApi

@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface WallClock {
    public fun nowEpochMillis(): Long

    /** The system clock; usable without the opt-in. */
    public object System : WallClock {
        override fun nowEpochMillis(): Long = epochMillisNow()
        override fun toString(): String = "WallClock.System"
    }
}
internal expect fun epochMillisNow(): Long      // jvmMain: java.lang.System.currentTimeMillis(); check module jsMain: Date.now()

@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface HttpTransport : AutoCloseable {
    public suspend fun execute(request: HttpRequest): Result<HttpResponse>
    override fun close() {}
}

@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class HttpFailure : Exception {
    protected constructor(message: String) : super(message)
    protected constructor(message: String, cause: Throwable?) : super(message, cause)

    public class Closed internal constructor() : HttpFailure("closed")
    public class DecodeFailure(message: String, cause: Throwable?) : HttpFailure(message, cause)
}

@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class TransportFailure : HttpFailure {
    protected constructor(message: String) : super(message)
    protected constructor(message: String, cause: Throwable?) : super(message, cause)

    public class Timeout(message: String, cause: Throwable?) : TransportFailure(message, cause)
    // ConnectFailed, DnsFailed, TlsFailed, Protocol, Io, InvalidRequest, RefusedByPolicy, BodyTooLarge, ...
}

@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class UpstreamFailure protected constructor(public val status: Int, message: String) : HttpFailure(message) {
    public class RateLimited(status: Int, public val retryAfter: Duration) : UpstreamFailure(status, "rate limited")
}
```

All of these shapes were compiled for `jvm` and `js`; the `System` object is final, supplied defaults stay
`final`, leaves are nested in their group, `Closed` and the other root leaves nested in `HttpFailure`.
Every interface member is written `public`; every `override` writes its return type and no visibility.
Keep the nested-object-over-companion choice: it adds one named class to the dump instead of a permanent
`Companion`.

### The unit's opening compile proof

Run in this order; the first three are throwaway edits that are not committed, the guard is permanent.

1. **Main compiles with the committed declarations**: `:webtools-http:compileKotlinJvm` and
   `:webtools-http-commoncheck:compileKotlinJs` succeed. Expected: no gate diagnostic. This alone proves
   nothing about the wiring (F1).
2. **Wiring canary, three throwaway files**, then remove them: a top-level
   `public class ProofClock : WallClock { override fun nowEpochMillis(): Long = 0 }` in
   `commonMain`, `public class ProofTransport : HttpTransport { .. }` in `jvmMain`, and
   `public class ProofJsClock : WallClock { .. }` in the check module's `jsMain`. Expected: all three
   compile. Then delete the `languageSettings.optIn` lines and expect exactly the in-module default
   error at each ("This class or interface requires opt-in to be implemented. Its usage must be marked
   with '@…ExperimentalForInheritanceWebToolsApi' or '@OptIn(…ExperimentalForInheritanceWebToolsApi::class)'")
   for the `commonMain` and the `jvmMain` file in `compileKotlinJvm`, and for the `jsMain` and the
   shared-directory file in `compileKotlinJs`.
3. **Consumer cases, positive**, in `commonTest` and `jvmTest`: the gated types in signatures;
   `WallClock.System.nowEpochMillis()`; construction of the public final leaves; `is`/`when` with `else`
   over the groups; `fun f(clock: WallClock = WallClock.System)`; `class C(val clock: WallClock = WallClock.System)`.
   Expected: `compileTestKotlinJvm` succeeds with no warning about the gate.
4. **Consumer cases, negative**, as a throwaway test file: the eight cases of F10. Expected: exactly eight
   errors, all the cross-module text of F10, at the supertype references. Then the same eight with
   `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`: no error. Record the output in the unit's
   as-built record. These eight snippets and texts are also the content for the compile-fail check the
   architecture lists as unevaluated.
5. **The guard**: `:webtools-http:verifyTestsAreConsumers` runs in `check`; it is the permanent part.
6. **Dump**: `apiDump` for the unit shows the marker class, the abstract groups with `protected`
   constructors, nested leaves as `Outer$Leaf`, `WallClock$System` with `INSTANCE`, and no mention of
   `@SubclassOptInRequired`; a reviewer checks the annotation by `javap -v` or by the negative cases.

## Considerations

### 1. How much of the opt-in wiring to keep as a permanent check

- **Options.** (a) The throwaway canary in step 2 plus the permanent guard task and a one-time recorded
  negative run (recommended). (b) Also add a permanent canary: an `internal` implementer in each of
  `commonMain`, `jvmMain` and the check module's `jsMain`, kept until the first real top-level
  implementer lands. (c) Also build the compile-fail case as a TestKit functional test in `build-logic`
  that compiles the eight snippets with and without the opt-in (the candidate the architecture names).
- **Trade-off.** (a) ships no dead code and has no new test infrastructure, but nothing re-runs the
  negative compile after this unit. (b) keeps the wiring exercised with three dead declarations in the
  published jar. (c) re-runs the proof on every build at the cost of a nested Gradle build and its
  downloads per run.
- **Recommendation.** (a) now; (c) as its own task when the compile-fail gap is picked up. From the first
  real top-level implementer (the unit that adds the OkHttp transport) the wiring is exercised by
  ordinary main code and (b) is moot.

### 2. Language setting versus a `compileTaskProvider` option for the main compilation

- **Options.** (a) `languageSettings.optIn` on `commonMain`+`jvmMain` (and `commonMain`+`jsMain`).
  (b) `jvm { compilations.getByName("main").compileTaskProvider.configure { compilerOptions.optIn.add(..) } }`
  (and the `js` equivalent).
- **Trade-off.** Identical compile result in both modules (F2). (a) is visible to tooling per source set
  and stays valid when a target is added (F3), but the Kotlin documentation says to use
  `languageSettings` "only to configure settings that apply specifically to a shared source set" (claim
  7). (b) is the documentation-endorsed spelling for per-compilation options and is a single line, but
  leaves `commonMain` without a recorded opt-in; whether the IDE then flags `commonMain` implementers was
  not tested (Known gaps).
- **Recommendation.** (a); switch to (b) only if the IDE is found to read the compile task.

## Known gaps

- **IDE behaviour not tested.** Whether IntelliJ marks `commonMain`/`jvmMain` implementers as errors
  under (b), and whether it honours `commonMain` + leaf language settings the way Gradle does, was not
  verified (no IDE in the environment). The reasoning for (a) relies on KGP exposing
  `languageSettings.optInAnnotationsInUse` per source set; I did not read the IDE side.
- **KT-75216** (the FIXME in `LanguageSettingsSetupAction`) could not be opened; its content is unknown
  beyond the source comment.
- **Only the JVM and `js` (IR, Node environment) targets were probed**, with the Kotlin/JS toolchain
  KGP 2.2.0 resolves. No other target (wasm, native) was compiled.
- **The probe used a simplified convention plugin** (jvm target, test dependencies, `kotlin-dsl` included
  build) in place of the repository's `webtools.kotlin-multiplatform-library`, which also applies
  publishing, binary-compatibility-validator and the level tasks. Nothing in the real plugin touches
  opt-ins, so interaction is not expected, but the real module build was not run (no build was run in
  the repository). The `apiDump` was taken from a direct application of binary-compatibility-validator
  0.18.2.
- **The two dump observations in F6** (the mangled `execute-gIAlu-s` name and the `DefaultImpls` class
  for the default `close()`) belong to the ports' design, not to this question; they are reported
  because they will be in the committed dump. I did not look for the flag or shape that changes either.
- **No Kotlin version above 2.2.0** was tested. The checker and KGP sources read are at tag `v2.2.0`;
  the Kotlin documentation pages quoted were fetched in October 2026 and may describe a newer plugin
  than 2.2.0 (the DSL page lists language versions up to 2.5).

## Load-bearing claims

1. **`@SubclassOptInRequired` is stable, binary-retained, limited to open/abstract classes and non-`fun` interfaces, and a subclass may carry the same annotation to opt in.**
   "Annotate declaration with [SubclassOptInRequired] with the same marker class, making it further propagating only for subclassing." and "Uses of this annotation are limited to open and abstract classes, and non-`fun` interfaces." The declaration is `@Target(CLASS) @Retention(BINARY) @SinceKotlin("2.1") @WasExperimental(ExperimentalSubclassOptIn::class) public annotation class SubclassOptInRequired(vararg val markerClass: KClass<out Annotation>)`.
   <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/annotations/OptIn.kt>, tag `v2.2.0`.

2. **The compiler accepts an inheritance use inside any lexically enclosing container annotated `@SubclassOptInRequired(marker)`, and reads a marker's `message` only if it is a string literal.**
   "for (annotationContainer in context.annotationContainers) {  if (annotationContainer.isExperimentalityAcceptable(context.session, annotationClassId, fromSupertype)) {  return true" and "fromSupertype && isAnnotatedWithSubclassOptInRequired(session, annotationClassId) ||" and "val message = (experimental.findArgumentByName(MESSAGE) as? FirLiteralExpression)?.value as? String".
   <https://github.com/JetBrains/kotlin/blob/v2.2.0/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/checkers/expression/FirOptInUsageBaseChecker.kt>, tag `v2.2.0`.
   Measured corroboration: the F1 matrix (setup in claim 14).

3. **`languageSettings.optIn` writes to the compilation's compiler options.**
   "override fun optIn(annotationName: String) {  optInAnnotationsInUseField.add(annotationName)  project.launch {  compilationCompilerOptions.await()  .optIn.add(annotationName)".
   <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/sources/DefaultLanguageSettingsBuilder.kt>, tag `v2.2.0`.

4. **A source set's language settings are tied to the compilation of which it is the default source set; the metadata `main` compilation is skipped; a shared source set with no compilation gets a separate options instance.**
   "// Ignoring non-klib common compilation as it reuses defaultSourceSet with klib common compilation" (`KotlinCompilationLanguageSettingsConfigurator`, `.../plugin/mpp/compilationImpl/KotlinCompilationLanguageSettingsConfigurator.kt`) and "// For shared source sets without any associated compilation, it would be a separate instance of common compiler options" (`.../internal/compilerOptionsDslHelpers.kt`).
   <https://github.com/JetBrains/kotlin/tree/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle>, tag `v2.2.0`.

5. **KGP checks opt-ins along `dependsOn` only: the dependent must use every opt-in its dependency uses.**
   "private val optInAnnotationsInUseHint = \"The dependent $unitName must use all opt-in annotations that its dependency uses.\"" (`.../plugin/sources/ConsistencyChecker.kt`) and "override fun afterDependsOnAdded(other: KotlinSourceSet) {  project.launchInStage(KotlinPluginLifecycle.Stage.FinaliseCompilations) {  defaultSourceSetLanguageSettingsChecker.runAllChecks(this@DefaultKotlinSourceSet, other)" (`.../plugin/sources/DefaultKotlinSourceSet.kt`).
   <https://github.com/JetBrains/kotlin/tree/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/sources>, tag `v2.2.0`.
   Measured: the multi-target error text in F3 (setup in claim 14, item 6).

6. **The commonMain-versus-compile-task relationship is a known rough edge.**
   "// FIXME: KT-75216 Synchronise common source set language settings with compile tasks in a better way".
   <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/sources/LanguageSettingsSetupAction.kt>, tag `v2.2.0`.

7. **The documented module-wide Gradle form for a multiplatform module is a project-level compiler option; the DSL reference limits `languageSettings` to shared-source-set settings.**
   "If your Gradle module is a multiplatform module, use the `optIn` method:  kotlin { compilerOptions { optIn.add(\"org.mylibrary.OptInAnnotation\") } }" and "Compiling with this argument has the same effect as if every declaration in the module has the annotation`@OptIn(OptInAnnotation::class)`." <https://kotlinlang.org/docs/opt-in-requirements.html> (fetched October 2026). "Use the `languageSettings {}` block only to configure settings that apply specifically to a shared source set. For all other cases, use the `compilerOptions {}` block to configure compiler options at the extension or target level." <https://kotlinlang.org/docs/multiplatform/multiplatform-dsl-reference.html> (fetched October 2026; the page shows language versions up to 2.5, so it may be newer than 2.2.0).

8. **The documentation's statement on nested classes.**
   "Note that when you use the `@SubclassOptInRequired` annotation to require opt-in, the requirement is not propagated to any inner or nested classes." <https://kotlinlang.org/docs/opt-in-requirements.html> (fetched October 2026).

9. **`explicitApi()` visibility exclusions; no return-type exclusion.**
   "Primary constructors", "Properties of data classes and annotations", "Methods marked with `override`", "Property getters and setters" under "Inspection exclusions"; "After careful review, we decided that some declarations should not require explicit visibility modifiers even in 'Explicit API' mode". <https://github.com/Kotlin/KEEP/blob/e06f0b96231226a3b2ed64416dc02c3649b4d3d6/proposals/KEEP-0045-explicit-api-mode.md>, commit `e06f0b9` of the KEEP repository. Measured corroboration: F7 (claim 14, item 7).

10. **`TimeSource.Monotonic` is a nested object of the `TimeSource` interface.**
    "public object Monotonic : TimeSource.WithComparableMarks {" inside "public interface TimeSource {". <https://github.com/JetBrains/kotlin/blob/v2.2.0/libraries/stdlib/src/kotlin/time/TimeSource.kt>, tag `v2.2.0`.

11. **kotlinx.serialization opts its own code (tests included) into its subclass-gate marker project-wide.**
    "optIn.addAll(  listOf(  \"kotlin.ExperimentalMultiplatform\",  \"kotlin.ExperimentalSubclassOptIn\",  \"kotlinx.serialization.InternalSerializationApi\",  \"kotlinx.serialization.SealedSerializationApi\"," and, for test source sets, "optIn(\"kotlinx.serialization.SealedSerializationApi\")". <https://github.com/Kotlin/kotlinx.serialization/blob/v1.9.0/buildSrc/src/main/kotlin/source-sets-conventions.gradle.kts>, tag `v1.9.0`.

12. **kotlinx.coroutines opts in per declaration, and its global opt-in list lacks the inheritance marker.**
    "@OptIn(ExperimentalForInheritanceCoroutinesApi::class) private class StateFlowImpl<T>(" <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/flow/StateFlow.kt>, tag `1.10.2`; the `optIn.addAll(...)` list in <https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/buildSrc/src/main/kotlin/GlobalKotlinCompilerOptions.kt> names `DelicateCoroutinesApi`, `ExperimentalCoroutinesApi`, `ObsoleteCoroutinesApi`, `InternalCoroutinesApi`, `FlowPreview` and `kotlin.experimental.ExperimentalTypeInference`.

13. **`compilation.compilerOptions` is deprecated in KGP 2.2.0.** Measured compiler output: "'val compilerOptions: HasCompilerOptions<KotlinJvmCompilerOptions>' is deprecated. To configure compilation compiler options use 'compileTaskProvider': compilation.compileTaskProvider.configure{ compilerOptions {} }." (KGP 2.2.0, Gradle 9.7.1, build script `kotlin { jvm { compilations.getByName("main").compilerOptions.configure { optIn.add(..) } } }`).

14. **Measured results (all in a scratch Gradle probe; KGP `org.jetbrains.kotlin.multiplatform` 2.2.0, `org.jetbrains.kotlinx.binary-compatibility-validator` 0.18.2, Gradle 9.7.1 through the repository's wrapper with a dedicated Gradle user home, `org.gradle.configuration-cache=true`, `kotlin.compiler.execution.strategy=in-process`, JDK 23 running Gradle, `jvmTarget` 11 with `-Xjdk-release=11`).**
    Setup: project `lib` (`jvm()`, `explicitApi()`, sources of this report's recommendation plus the throwaway implementers), project `commoncheck` (`js { nodejs { testTask { enabled = false } } }`, `commonMain` `kotlin.srcDir` pointing at `lib`'s `commonMain`, a `jsMain` `actual`), and a convention-plugin variant with an included `build-logic` (`kotlin-dsl`). The mechanism was chosen by a Gradle property (`-Pgate=none|A|A1|A2|AT|BJ|C|T|CT`, `-PcheckGate=none|A|A1|A2|C`). Negative cases were copied in as one test file and removed after each run. Tasks: `:lib:compileKotlinJvm`, `:lib:compileKotlinMetadata`, `:lib:compileTestKotlinJvm`, `:lib:jvmTest`, `:lib:apiDump`, `:lib:verifyTestsAreConsumers`, `:commoncheck:compileKotlinJs`.
    1. Main compiles and tests are a consumer for the mechanisms marked so in the F2 table; the negative file produced exactly eight `This class or interface requires opt-in to be implemented: Implementing or substituting this WebTools seam is Experimental and may change in a minor release. ...` errors where tests are a consumer and `BUILD SUCCESSFUL` with `kotlin { compilerOptions { optIn.add } }`, `jvm { compilerOptions { optIn.add } }` and `sourceSets.all { languageSettings.optIn }`.
    2. With every mechanism off (`gate=none`), `compileKotlinJvm` succeeded for the committed shapes and failed with three errors "This class or interface requires opt-in to be implemented. Its usage must be marked with '@com.probe.http.ExperimentalForInheritanceWebToolsApi' or '@OptIn(com.probe.http.ExperimentalForInheritanceWebToolsApi::class)'" on a `commonMain` top-level `class FixedClock : WallClock`, a `commonMain` top-level `class TopLevelTimeout : TransportFailure(..)` and a `jvmMain` `class JvmTransport : HttpTransport`. `:lib:compileKotlinMetadata` printed `SKIPPED`.
    3. `commonMain { languageSettings.optIn }` alone (`gate=A1`) left those three errors in place; `jvmMain` alone (`gate=A2`) removed them.
    4. Check module: `-PcheckGate=none` with the shared throwaway files and a `jsMain` `class JsClock : WallClock` failed with three errors; `A` (commonMain+jsMain), `A2` (jsMain) and `C` (module-wide) succeeded; `A1` (commonMain) failed; the `lib` project's own option (`-Pgate=C`) did not change that. No Node.js/Yarn/download output; no `kotlin-js-store/` in the probe root.
    5. Unresolved marker: a module without the marker and `compilerOptions { optIn.add("com.probe.http.ExperimentalForInheritanceWebToolsApi") }` built with "w: Opt-in requirement marker com.probe.http.ExperimentalForInheritanceWebToolsApi is unresolved. Please make sure it's present in the module dependencies"; with `optIn.add("kotlin.Deprecated")`: "w: Class kotlin.Deprecated is not an opt-in requirement marker".
    6. Two-target project (`jvm()` + `js()`), `commonMain { languageSettings.optIn("kotlin.ExperimentalStdlibApi") }` only: configuration failed with "Inconsistent settings for Kotlin source sets: 'jsMain' depends on 'commonMain' ... The dependent source set must use all opt-in annotations that its dependency uses."; `jvmMain` only: `BUILD SUCCESSFUL`.
    7. `explicitApi()` results of F7 (file of undecorated interface members and overrides, `:lib:compileKotlinJvm`).
    8. `javap -v -p` on the compiled classes: `kotlin.SubclassOptInRequired(markerClass=[class Lcom/probe/http/ExperimentalForInheritanceWebToolsApi;])` under `RuntimeInvisibleAnnotations` on `WallClock`, `HttpTransport`, `HttpFailure`, `TransportFailure`; absent on `WallClock$System` and `HttpFailure$Closed`. `apiDump` produced `api/lib.api` with no occurrence of `SubclassOptInRequired`; it listed `HttpTransport.execute` as `execute-gIAlu-s` and a `HttpTransport$DefaultImpls` class.
    9. `verifyTestsAreConsumers` passed for `none`, `A`, `A2`, `B`; failed with "The test code opts in to ... tests must stay a consumer without the opt-in." for `C`, `T`, `AT`, `CT`; `--warning-mode all` printed only the known Gradle-10 `archives` configuration deprecation and, for the probe's own `Check for instance is always 'true'` source warning, nothing about opt-ins; the second run printed "Reusing configuration cache".
    10. Companion and nested-object `System` both compiled with `gate=none`; dump entries as in F8. A same-package `class Date` beat `import java.util.*` and compiled.
    11. Convention-plugin layout (included `build-logic`, precompiled script plugin applying `org.jetbrains.kotlin.multiplatform`): module build files with only the recommended `kotlin { explicitApi(); sourceSets { commonMain { .. }; jvmMain { .. } } }` lines compiled main, tests and `compileKotlinJs`, and the eight negatives failed.
    12. Shapes: `UpstreamFailure protected constructor(public val status: Int, message: String) : HttpFailure(message)` with a nested `RateLimited(status, retryAfter: Duration)` compiled in `commonMain` with and without the module-wide option; `UpstreamFailure(public val status: Int) : HttpFailure` with a leaf `: UpstreamFailure(status)` failed with "This type has a constructor, so it must be initialized here."; `@ExperimentalForInheritanceWebToolsApi public class Direct` failed with "This annotation is not applicable to target 'class'."; a `commonTest`-only `languageSettings.optIn` (`gate=CT`) left the eight negatives failing.
