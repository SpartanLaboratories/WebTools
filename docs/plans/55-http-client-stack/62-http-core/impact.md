# Issue #62 — `http-core` (impact)

- **Covers:** `SpartanLaboratories/WebTools#62`, *"webtools-http: module skeleton, opt-in marker,
  failure model, request/response types, transport and wall-clock ports, logging seam and the
  check-only `js` compile"*. It is unit 3, `http-core`, of [#55's architecture](../architecture.md)
  (§6.2, §6.8, §6.9, §11).
- **Level:** 3. This file covers what the repository, its published artifacts and the sibling units
  gain or lose with this unit.
  - The file-level detail is in [`plan.md`](plan.md).
  - The as-built record is [`final-implementation.md`](final-implementation.md), and this file is
    reconciled with it.
- **Baseline:** `master` `f72b115`. Built on branch `feat/issue-62-http-core`; PR TBD (it closes #62).
- **Related docs:**
  - [`plan.md`](plan.md);
  - [`research/README.md`](research/README.md);
  - the systems-level [`../impact.md`](../impact.md).

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-http` (new) | The common core of the HTTP stack (see the list below). It publishes `webtools-http` and `webtools-http-jvm` once released, and its ABI dump `api/webtools-http.api` is committed. | `1.0.0`, inherited from the root default; unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
| `webtools-udp`, `webtools-scraping`, `webtools-browser` | Nothing: no source, build-script or test change. | unchanged |

What `webtools-http` holds after this unit:
- the opt-in marker;
- the failure model and both reason vocabularies;
- `HttpMethod`, `Url`, `HttpHeaders`, `RequestBody`, `HttpRequest`, `HttpResponse` and `RedirectHop`;
- the `HttpTransport` and `WallClock` ports, with `WallClock.System`.

No port has any other implementation yet.

**What a consumer of `webtools-http` 1.0.0 sees from this unit,** read from the generated
publication:
- **The POM of `webtools-http-jvm`** lists `kotlinx-coroutines-core-jvm` 1.10.2 and `kotlin-stdlib`
  2.2.0 in `compile` scope, and `okhttp-jvm` 5.5.0, `caffeine` 3.3.1 and `slf4j-api` 2.0.13 in
  `runtime` scope.
- **The Gradle Module Metadata** puts only `kotlinx-coroutines-core` and `kotlin-stdlib` in the API
  variant; the runtime variant adds the other three.
- **Through `okhttp-jvm`,** the resolved standard library is `kotlin-stdlib` 2.2.21 or newer.
- **Class files** are major 55, with `org.gradle.jvm.version` 11 on both library variants.
- **The ABI dump** `api/webtools-http.api` has 294 lines and holds no `internal` declaration.

**Release.** `webtools-http` 1.0.0 is new and stays unreleased until #55's stack is complete. Its
landing moves no published module's version, so there is no release candidate.

**Not published:**
- `webtools-http-commoncheck`, which compiles `webtools-http`'s `commonMain` for `js`;
- the new `build-logic` plugin.

## 2. Blast radius in this repository

### Build

- **`settings.gradle.kts`** includes `:webtools-http` and `:webtools-http-commoncheck`. The root
  `build.gradle.kts` changes in its header comment only.
- **`build-logic` gains `webtools.kotlin-common-check`.**
  - The plugin applies the multiplatform plugin, declares `mavenCentral()`, calls `js()` with no
    environment, and wires `check` to `compileKotlinJs`.
  - `build-logic/build.gradle.kts` does not change: the multiplatform marker already covers the
    plugin.
  - Its tests:
    - a Level-3 TestKit class, `CommonCheckPluginIntegrationTest` (seven tests);
    - a Level-4c configuration-cache class;
    - the `SampleProject.commonCheck(...)` factory;
    - a fourth id in `BuildLogicPluginsGatingTest`.

    `:build-logic:test` keeps its 47 tests. `integrationTest` runs 46 (39 before), `e2eTest` 5 and
    `nonfunctionalTest` 3 (2 before). `IncludedBuildEndToEndTest`'s KDoc says the new plugin is
    covered at Level 3, not in its composite.
- **`webtools-http` is the first module on `webtools.kotlin-multiplatform-library`.**
  - Its build file adds `explicitApi()`, the dependencies and the opt-in as a language setting of
    `commonMain` and `jvmMain`.
  - The check module's build file adds the opt-in on `commonMain` and `jsMain`.
  - The test source sets stay without the opt-in.
- **`./gradlew build` and `check` do more:**
  - `webtools-http`'s `jvm` compile and its 131 tests, through `jvmTest`. By level: gating 5,
    component 69, deterministic 48 (the `Url` differential over 200,000 generated inputs included),
    non-functional 9. They take about ten seconds.
  - its `apiCheck` and `verifyJavaFloor`;
  - the check module's `compileKotlinJs`.
- **The pre-commit gate of `webtools-http`** is `gatingTest componentTest deterministicTest` (plan §9).

  A configuration-cache miss prints one more warning from the Kotlin Gradle plugin, "JS Environment
  Not Selected". The Gradle-10 `archives` deprecation (KT-78620) was already printed for `jvm()`
  modules, and is now raised at `js()` as well.
- **No Node.js and no Yarn.** `build` downloads neither and writes no `kotlin-js-store/`. The
  measurement: `:webtools-http-commoncheck:build --dry-run` lists no Node.js, Yarn, npm or
  `PackageJson` task.
- **The root project gains no plugin, no extension and no task.** It still has no `check`, `build`,
  `assemble` or `clean` of its own; `./gradlew :check` reports that the root project has no `check`
  task.

### Source and resources

- **New trees:** `webtools-http/` and `webtools-http-commoncheck/`, and one plugin script under
  `build-logic/src/main/kotlin/`.
- **Duplicated by design:**
  - a third copy of the `internal` `Result.flatMap`. The two legacy copies are not edited, and their
    headers still name only each other.
  - a fourth byte-identical `logback-test.xml`.
- **Untouched:** no main or test source file of the three existing modules changes.
- **`.gitignore` and `.gitattributes`:** no change.
  - `kotlin-js-store/` is never written.
  - `binary-compatibility-validator` compares the dump line by line, so the dump's line endings do
    not matter.

### Documentation

- **`README.md`.**
  - The artifact table gains the `webtools-http` row, marked not yet released.
  - The install block gains its line, marked not yet on Maven Central.
  - A paragraph adds the Maven `-jvm` coordinate and the consumer `kotlin-stdlib` 2.2.21 note.
  - In "Build & test":
    - the module list names the new module and the check module;
    - the test layout covers `src/commonTest` and `src/jvmTest`;
    - one sentence says that `build` and `check` compile the `*-commoncheck` modules for `js` with no
      Node.js;
    - "every module" becomes "every JVM module" or "every library module" where only those compile to
      Java 11;
    - the `verifyJavaFloor` command comment names "every published module's jars";
    - the plugin table holds four plugins, and the multiplatform row no longer says "no module uses
      it yet".
  - No `### webtools-http` Components table yet.
  - Thirty lines inserted and fifteen deleted.
- **KDoc** on every public declaration of `webtools-http`, naming its tier and its contract.
- **Unchanged:** `docs/webtools-udp-architecture.md` and `docs/webtools-udp-protocol.md`.
- **Unit 11 writes** `docs/webtools-http-architecture.md`.
- **`docs/dashboards/`** is brought to the as-built state by the admin team's landing refresh.
  Nothing in this unit edits it.

## 3. Adoption

| Site | Verdict |
|---|---|
| `Connector`, `WebViewer`, `resolveLocalAddress()` | Unchanged; the follow-ups named in [architecture §8](../architecture.md) stand. Nothing can move onto `webtools-http` before 1.0.0 is released. |
| `ResultExtensions.kt` in `webtools-udp` and `webtools-scraping` | Not edited. `webtools-http` carries a third copy, whose header names both. |
| `ReliableSendFailure`'s open hierarchy and the "keep an `else`" rule (`README.md:82-88`) | Followed by the failure model and the reason vocabularies, not changed. |
| `LinkQualityTracker`'s `nanoClock` | Unchanged; the stack uses the named `WallClock`. |
| The three `logback-test.xml` copies | Unchanged; one more byte-identical copy. |

## 4. Sibling units

What the later units build on. [`plan.md`](plan.md) §12 has the signatures.

- **Unit 4, `test-support` (#63).**
  - **The root project still has no `check` task.** The check module uses `js()` with no
    environment, so the open point "where the publication check attaches" stands as
    [`../impact.md`](../impact.md) §8 describes it.
  - **The fixtures compilation needs its own opt-in.** It reaches the module's `internal` declarations,
    including `HttpSyntax` for the `.http` parser, but not the module's opt-in, so its source sets opt
    in themselves for the fakes that implement `HttpTransport` and `WallClock`.
  - **No query decoder exists.** Unit 4 adds an `internal` one if its matcher needs one.
- **Unit 5, `url-policy` (#64).**
  - `Url.host` is canonical and stays as written for digits-and-dots and hex-looking text, which the
    policy judges.
  - `RefusedByPolicy(reason)` is public.
  - `Entry.isSensitive` and `HttpSyntax.isCredentialName` give one rule for credential headers.
  - **What `addSensitive` promises.** The KDoc says it marks the entry: the entry is never printed, and
    the stack's redirect policy, a later unit, reads the flag. Units 5 and 6 build that reading: the
    `Untrusted` refusal of a sensitive header, and the strip on a cross-origin hop.
- **Unit 6, `okhttp-transport` (#65).**
  - **The first top-level `jvmMain` implementer of a gated port.** The module-wide opt-in covers it.
  - **`execute` must be main-safe** (point 8 of `HttpTransport`'s contract). The OkHttp transport moves
    blocking I/O off the caller's thread, through OkHttp's own dispatcher and a cancellable callback,
    and says so in its KDoc. The fixtures' fake (unit 4) meets the same point by never blocking.
  - **Converting a URL:** `Url` to `HttpUrl` through `toString()`. `Url` rejects the input that
    crashes `HttpUrl` (`[1:2:3:4:5:6:7::8]`).
  - **Statuses and header entries:**
    - a status outside 100..599 is `Protocol`;
    - an entry whose name is not a token is dropped;
    - CR, LF and NUL in a received value become SP.
  - **Sending a body:** `Content-Type` goes as a plain header, with OkHttp's `MediaType` left null. A
    method OkHttp requires a body for gets an empty one.
- **Units 7 and 8, `response-cache` (#66) and `call-budget` (#67):** `Closed(component)`,
  `BudgetExhausted(wait, reason, window)` with `ExhaustedReason`, `WallClock`, the logging seam and
  `catchNonFatal`.
- **Unit 9, `call-facades` (#68).**
  - It gets the `internal` `encodePathSegment` and `encodeQueryComponent`, and assembles URLs as text
    and parses them again.
  - It settles empty path segments, as `InvalidRequest`.
  - `RateLimited` is `internal`; `UpstreamError` and `DecodeFailure` are public.
- **Unit 10, `feeds` (#69):** the check-module pattern.
  - `js()` with no environment.
  - A relative `srcDir`.
  - The opt-in on `commonMain` and `jsMain`.
  - `implementation(project(":webtools-http-commoncheck"))`, measured to compile.
  - Its own copy of the logging seam.
- **Unit 11, `http-docs` (#70):** the Components table, the how-to and the evergreen architecture
  document, which describes the check module as `js()` with no environment.

## 5. Where the as-built design departs from the parent documents

The first point below carries a dated correction in [`../architecture.md`](../architecture.md), at
§6.8 with pointers at §6.1 and §2; the document is otherwise left as written. The statements that do
not hold, or are refined:

- **The `js` target's form.**
  - §6.8 gives the check plugin `js { nodejs { testTask { enabled = false } } }`, §6.1 describes a
    `js` target whose test task is disabled, and §2 says the check module downloads nothing while its
    tests are disabled.
  - With that form, `./gradlew build` downloads Node.js and Yarn, writes `kotlin-js-store/`, and gives
    the root project Gradle's base plugin and about eighteen tasks
    ([`research/common-check-plugin.md`](research/common-check-plugin.md) F2, F5).
  - The plugin calls `js()` with no environment. The earlier measurement had run `compileKotlinJs`
    alone.
  - As built, the root project gains no task, and unit 4's open point (where the fixtures-publication
    check attaches) stands as [`../impact.md`](../impact.md) §8 states it.
  - The cost is the Kotlin Gradle plugin's "JS Environment Not Selected" warning, printed once per
    configuration-cache miss. If a release makes it an error, the plugin's TestKit `build` test fails,
    and the fallback recorded in the plugin's header applies (§7).
- **The feeds check module's dependency on this check module.** §3 and §13 list it as not probed. It
  compiles under `js()` (F11).
- **The module-wide opt-in.**
  - §6.2 says the module's build "opts in module-wide". In a single-target module only the leaf source
    set's language setting reaches the compiler ([`research/opt-in-gate.md`](research/opt-in-gate.md)
    F2, F3).
  - The documented `kotlin { compilerOptions { optIn } }` reaches the test compilations too.
  - So the opt-in sits on `commonMain` and `jvmMain`, in the module's own build file.
- **The query encoder's raw set.**
  [`../research/http-transport.md`](../research/http-transport.md) F8 says that everything outside
  `A-Za-z0-9-._~` is encoded. OkHttp's `addQueryParameter` leaves only `A-Za-z0-9*-._` raw
  ([`research/url-parsing.md`](research/url-parsing.md) F16).

## 6. Neighbouring issues

- **#58** (CI and repository practices).
  - Its CI would be the first place the check module, `apiCheck` and the `Url` differential run on
    every change.
  - Releasing `webtools-http` waits on it and on the rest of the stack.
- **#50** (`webtools-udp` multiplatform) can reuse the check-module pattern and the logging seam.
- **OkHttp** (outside the Spartan Labs organisations). `HttpUrl`'s IPv6 decoder throws
  `ArrayIndexOutOfBoundsException` on `[1:2:3:4:5:6:7::8]`, from 5.5.0 to its default branch. `Url`
  rejects that input, so it never reaches OkHttp.

## 7. Follow-ups this unit leaves

- **A permanent compile-fail check of the gate.**
  - It is still the review item of architecture §13. The eight negative cases and their diagnostic
    texts are recorded once, in [`final-implementation.md`](final-implementation.md) §3.
  - A TestKit snippet in `build-logic` could re-run them. The research's `verifyTestsAreConsumers`
    guard task would keep the test source sets free of the opt-in.
- **Internationalised host names.** 1.0.0 refuses them, `xn--` labels included. Supporting them needs
  an IDNA path that agrees with OkHttp's UTS 46 mapping.
- **The fallback for the `js` warning.** If a Kotlin Gradle plugin release turns "JS Environment Not
  Selected" into an error, the plugin moves to
  `js { nodejs { testTask { enabled = false; setDependsOn(emptyList<Any>()) } } }`. The root project
  then gains base-plugin tasks.
- **Additive API:** a public query decoder or `Url` builder; a strict mode that rejects a malformed
  `%`; a typed extension for the check plugin once a third check module exists.
- **The IDE checks of the Level 5 pass**
  ([`final-implementation.md`](final-implementation.md) §6, items 1, 2 and 4):
  - IntelliJ's import of the check module, with its shared source directory and `jsMain` actuals;
  - the gate's error and its `@OptIn` quick fix on a class that implements `WallClock`;
  - Quick Documentation of the public types.

  **Owner:** the maintainer. **Trigger:** the next IntelliJ import of the repository after this unit
  lands, and at the latest before unit 4 (#63) starts, whose fixtures compilation adds another source set
  the IDE must import.

## 8. Known gaps

- **IntelliJ is untested.** Neither its import of the check module (a source directory outside the
  module) nor its reading of the per-source-set opt-ins was tried. The owner and the trigger are in §7.
- **The gate has no permanent check.** The compile proof ran once and its output is recorded
  ([`final-implementation.md`](final-implementation.md) §3). The ABI dump does not record
  `@SubclassOptInRequired`.
- **Only OkHttp 5.5.0 was measured.** The files `Url` mirrors are identical at 5.5.1. A later change
  surfaces as a differential failure.
- **On `js`, the code is compiled but no test runs.**
- **HTTP/2 header handling was source-read only.**
- **Hosts:** only Windows with a JDK 17 to 26 build JDK.
