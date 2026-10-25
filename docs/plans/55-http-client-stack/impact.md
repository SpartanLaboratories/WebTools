# Issue #55 — HTTP client stack (impact)

- **Covers:** `SpartanLaboratories/WebTools#55`, with `#47` folded in as its `java-floor` unit.
- **Level:** systems. What each artifact, build file, document and neighbouring issue gains or
  loses once all eleven units in [`architecture.md`](architecture.md) §11 have landed. The
  file-level detail belongs to each unit's own plan.
- **Baseline:** `master` `806ce8e`. Line references are to that commit.
- **Related docs:** [`architecture.md`](architecture.md) (§8 integration and adoption, §9
  tiers, §12 risks, §13 known gaps); [`research/README.md`](research/README.md);
  [`47-java-23-bytecode/research/README.md`](47-java-23-bytecode/research/README.md).

## 1. Published artifacts

| Artifact | What changes | Version | What a consumer sees |
|---|---|---|---|
| `webtools-udp` | Java 11 floor (landed, #76): class files major 55, `org.gradle.jvm.version` 11 in the module metadata (23 on Central). Public API unchanged (`javap -protected` identical, measured); the UDP port lock unchanged. | rides in the unreleased `2.0.0a` (`webtools-udp/build.gradle.kts:116`); Central holds 1.0.0, 1.1.0 and 2.0.0, all Java 23 | JDK 11–22 can load it once it is published. POM identical; metadata identical except the JVM version. `2.0.0a` orders below `2.0.0`, so a graph that also asks for `2.0.0` keeps the Java 23 build unless it pins with `strictly`, as for the other two. This corrects the architecture's §12, which calls `webtools-udp` unaffected. |
| `webtools-scraping` | Java 11 floor; no source change. | `1.0.0` → `1.0.0a` | As above. `1.0.0a` orders below `1.0.0`, so a graph that also asks for `1.0.0` keeps the Java 23 build unless it pins with `strictly`. The README and the release note say so. |
| `webtools-browser` | Java 11 floor; no source change. | `1.0.0` → `1.0.0a` | As for `webtools-scraping`. |
| `webtools-http` (new) | The HTTP stack: transport, URL policies, cache, budget, facades, failure model. Kotlin Multiplatform; publishes `webtools-http` and `webtools-http-jvm`, plus a test-fixtures variant (capability `webtools-http-test-fixtures`, a `-test-fixtures.jar`). Its common core is built (#62): the opt-in marker, the failure model, the request and response types, and the `HttpTransport` and `WallClock` ports; its ABI dump `api/webtools-http.api` is committed. Its test-fixtures variant is built (#63): `VirtualClock`, `FakeHttpTransport`, the `.http` fixtures and the recorder, with the fixtures-publication check that guards their publication. | `1.0.0` | `kotlinx-coroutines-core` 1.10.2 on the compile classpath (`api`); `okhttp-jvm` 5.5.0, `caffeine` 3.3.1 and `slf4j-api` 2.0.13 at runtime only. `okhttp-jvm` 5.5.0 brings a `kotlin-stdlib` 2.2.21 requirement. `kotlinx-coroutines-test` 1.10.2 and `mockwebserver3` 5.5.0 appear only on the fixtures variants. The publication as built (#62) matches: the `webtools-http-jvm` POM has `kotlinx-coroutines-core-jvm` 1.10.2 and `kotlin-stdlib` 2.2.0 in `compile` scope and the other three in `runtime` scope, the module metadata's API variant holds only those two, and the class files are major 55, with `org.gradle.jvm.version` 11 on both library variants. The fixtures variant as built (#63) matches too: the root module's metadata holds `testFixturesApiElements` and `testFixturesRuntimeElements`, each with the capability `io.github.spartanlaboratories:webtools-http-test-fixtures`, `org.gradle.jvm.version` 11 and the one `-test-fixtures.jar` (classes only, major 55); the root POM lists the two test libraries as `compile` and `optional`, and neither the main variants nor the `-jvm` POM lists them. A consumer takes it with `testFixtures(...)`, in Gradle only. |
| `webtools-feeds` (new) | RSS/Atom parser port with the Rome default and a conditional-GET fetcher. Kotlin Multiplatform; publishes `webtools-feeds` and `webtools-feeds-jvm`; no fixtures variant. | `1.0.0` | `webtools-http` and its `api` dependency on the compile classpath; `rome` 2.1.0 and `slf4j-api` at runtime. |

**Not published:** the included `build-logic` build and the check-only modules
`webtools-http-commoncheck` and `webtools-feeds-commoncheck`.

**Releases.** The two new modules stay unreleased at `1.0.0` until the last unit lands; the units
that build them move no version. `java-floor` is the only unit that moves an existing artifact's
version. It has landed (#76): `webtools-scraping` and `webtools-browser` are `1.0.0a` in the build,
and nothing is published. The release that publishes the lettered versions moves the README's
install lines to them. Every release follows the organisation's steps once #58's practices exist.

## 2. Blast radius in this repository

### Build

- **Root `build.gradle.kts`** keeps only `allprojects { group; version }` (`:12-15`). The
  `plugins` block (`:1-5`) and the `subprojects {}` block (`:17-92`), with the level tasks
  (`:44-62`) and the publishing configuration (`:64-91`), move into `build-logic`'s convention
  plugins.
- **`settings.gradle.kts`** gains `pluginManagement { includeBuild("build-logic") }` as its first
  statement, and `:7` gains `:webtools-http`, `:webtools-http-commoncheck`, `:webtools-feeds` and
  `:webtools-feeds-commoncheck`. The foojay 0.8.0 plugin (`:2`) stays; nothing consults it.
- **The three existing module build files** each gain a plugin line and their own
  `api(slf4j-api)`, which keeps their published POMs identical. The `webtools-udp` port lock
  (`webtools-udp/build.gradle.kts:103-117`) and its `version` line (`:101`) stay verbatim.
  `webtools-scraping` and `webtools-browser` gain an explicit `version = "1.0.0a"`.
- **Level tasks select by package** (`com.spartanlabs.testing.<level>.*`) instead of by JUnit tag
  (`build.gradle.kts:60`).
  - On the existing modules they select the same 95 test classes the tags did, measured with
    `--test-dry-run` before and after unit 1. The other 4 of the 99 test files hold support code
    and no test.
  - The `@Tag` annotations on those 95 classes stay and become redundant.
- **`build-logic` has its own level tests.**
  - `./gradlew :build-logic:test` is its pre-commit gate: the gating, component and deterministic
    levels, in seconds.
  - Its TestKit tasks, `:build-logic:integrationTest`, `:build-logic:e2eTest` and
    `:build-logic:nonfunctionalTest`, run on demand and one after another. Their first run
    downloads about 910 MB into `build-logic/build/testkit/home`.
  - A later unit that adds a convention plugin or a build check adds its TestKit class there (§8).
- **`./gradlew build` does more work.** It adds:
  - `verifyJavaFloor` on every published module;
  - `apiCheck` on the two new modules;
  - the fixtures-publication TestKit check (a publish, and resolutions from three build shapes), which
    unit 4 attached to `webtools-http`'s `check`: 44 to 52 s when one of its inputs changed, and
    `UP-TO-DATE` otherwise (§8);
  - one Kotlin/JS compile per check module (compile only: its `js` target has no environment, so it
    has no test task and needs no Node.js).

  `build-logic`'s own tests are not part of it, apart from that check. Until #58 brings CI, these
  guards run only on a developer's build.
- **Build host.** The bytecode floor no longer depends on the JDK that runs the build (exercised
  on JDK 17 and 23); Gradle 9.7.1 itself needs JDK 17 to 26. Kotlin 2.2.0 on Gradle 9.7.1 sits
  outside Kotlin's documented range: it works, but Gradle 10 forces a KGP move (architecture §12).
  IntelliJ import of `build-logic`, the `jvmTestFixtures` source set and the check modules is
  unverified.

### Source and resources

- **No existing main source file changes.** `webtools-udp`'s public API is unchanged. The
  legacy modules compile unchanged under the floor, so this work breaks none of their API.
- **New trees only:** `build-logic/`, `webtools-http/`, `webtools-feeds/` and the two check
  modules.
- **Duplicated by design:** a third copy of the `internal` `Result.flatMap` helper in
  `webtools-http` (a fourth in `webtools-feeds` only if it chains `Result`s), because Kotlin
  `internal` stops at the module; two more copies of `logback-test.xml`, one in each new
  module's `jvmTest` resources.
- **`.gitattributes`** gains `**/fixtures/**/*.http -text` for this repository's own `.http`
  fixture files.

### Documentation

- **`README.md`.** Several units change it:
  - the artifact table and install snippet grow to five artifacts, with the Maven `-jvm` note
    and the Gradle-only `testFixtures(...)` line;
  - `Requires JDK 11 or newer` (`:32` today, `:26` at the baseline) stays true. Unit 2 has added the
    paragraph on which releases carry the floor and the build-JDK note (a "Java floor" section under
    Build & test); unit 3 has added the consumer `kotlin-stdlib` note;
  - the Components section gains `webtools-http` and `webtools-feeds` tables with the tier note;
  - the "HTTP client stack" and "Feeds" how-tos are new;
  - "Build & test" (`:463-484`) describes five modules, `build-logic`, package-selected levels
    and the `commonTest`/`jvmTest` layout.
- **`docs/webtools-http-architecture.md`** is new: the evergreen description of the stack, in the
  style of `docs/webtools-udp-architecture.md`.
- **`docs/webtools-udp-architecture.md` and `docs/webtools-udp-protocol.md`** are unchanged.
  Neither mentions the build, the module layout, the JDK, slf4j or `Connector`.

## 3. Adoption

Every existing class that does HTTP, or that uses a mechanism this design touches, and what
happens to it:

| Site | Verdict |
|---|---|
| `Connector.get` (`Connector.kt:263`, Unirest; zero timeouts set at `:65`) and `Connector.skrape` (`:281`, skrapeit) | Stays as is. Follow-up `connector-get-skrape-on-webtools-http` moves both onto `webtools-http` once 1.0.0 is released, removing unirest, skrapeit and their transitive trees (Apache HttpClient; Ktor 1.5.4 and htmlunit 2.50.0). |
| `Connector.download` (`:198`, jsoup + `ImageIO`) | Stays as is. Follow-up `connector-download-fetch-half` replaces only the fetch with a capped-bytes GET; the `ImageIO` decode stays a JVM helper. |
| `Connector.open` / `next` / `hasNext` / `iterator` / `close` (`:85`, `:178`; JVM-wide `http.agent` at `:150`) | Stays as is. The stack has no streaming counterpart, so a move means a new suspend API: follow-up `connector-line-reader-api`. |
| `WebViewer.getPage` / `screenshot` (`WebViewer.kt:40`, `:51`; `driver.get(url)` at `:63`) | Not an HTTP client: headless Chrome resolves and fetches by itself. Follow-up `webviewer-url-precheck` would need the classifier's host rules, which are `internal` in 1.0.0. |
| `resolveLocalAddress()` (`General.kt:32`) | Not HTTP. Unchanged. |
| `ReliableSendFailure`'s open hierarchy (`ReliableSendFailure.kt:15`) and the stability note in `README.md:70-76` (entries may grow; keep an `else`) | The precedent the failure model follows. Unchanged. |
| `LinkQualityTracker`'s `nanoClock` (`LinkQualityTracker.kt:15`) | Unchanged. The stack uses the stdlib `TimeSource` and a named `WallClock`; nothing is retrofitted. |
| The 95 legacy `@Tag` annotations | Left in place; harmless. Follow-up `remove-redundant-test-tags` (optional). |
| foojay 0.8.0 (`settings.gradle.kts:2`) | Unchanged. #58 bumps it; follow-up `jdk11-test-launcher` then runs the level tasks on a JDK 11 launcher. |

## 4. Downstream consumers

- **BotTools** (`SpartanLaboratories/KotlinBottools`) is the first consumer. It reaches the stack
  through an internal adapter, developing against a sibling checkout through a nested composite
  build. Its three release conditions (Java ≤ 21 bytecode, Kotlin 2.2.x-readable metadata, no
  `-SNAPSHOT` dependency) all hold for `webtools-http` and `webtools-feeds` 1.0.0. Its adapter
  building against the Experimental seams is what lets them graduate. No work in that
  repository is designed here.
- **Consumers of the three existing artifacts** see only the Java floor (§1).

## 5. Neighbouring issues

- **#47** is the `java-floor` unit: re-parented under #55, it keeps its number and title. It
  landed in PR #76 (merge `3949b52`); its plan, impact and as-built record live in
  `47-java-23-bytecode/`.
- **#58** (CI, CONTRIBUTING, CHANGELOG, tags, the foojay bump). Nothing here waits on it to merge.
  Releasing waits on it. Its CHANGELOG is where Experimental-to-Supported promotions will be
  recorded. Its CI would be the first place the floor guard, the publication check, the check
  modules, the level tasks and `build-logic`'s own tests run on every change.
- **#50** (`webtools-udp` multiplatform) can reuse the multiplatform convention plugin, the
  check-module pattern and the logging seam; nothing here is designed for it.

## 6. Follow-ups this work leaves

Named in [`architecture.md`](architecture.md) §8 and §9. None is part of #55.

- **`connector-get-skrape-on-webtools-http`** (`webtools-scraping`, after `webtools-http` 1.0.0
  is released): move `get` and `skrape` onto the stack. It has to choose a blocking wrapper or
  a `suspend` shape and define what a non-`2xx` status means.
- **`connector-download-fetch-half`** (`webtools-scraping`): the fetch half of `download` as a
  capped-bytes GET.
- **`connector-line-reader-api`** (`webtools-scraping`): a suspend replacement for the
  line-reader API.
- **`webviewer-url-precheck`** (`webtools-browser`): a URL-level pre-check for
  administrator-supplied URLs.
- **`remove-redundant-test-tags`**: drop the 95 `@Tag` annotations the package filter made
  redundant. Optional.
- **`jdk11-test-launcher`**: run the level tasks on a JDK 11 launcher, after #58's foojay bump.
- **`floor-scan-message`** (optional): `JavaFloorScan` reads a non-numeric `org.gradle.jvm.version`
  as absent, so a string `"11"` is reported as "declares ... nothing"; the message should name the
  value found, and its pinning test changes with it.
- **The release of `2.0.0a`, `1.0.0a` and `1.0.0a`** moves the README's three install lines, which
  name the Central releases `2.0.0`, `1.0.0` and `1.0.0` until then, to the lettered versions.
- **Seam graduation.** Each Experimental seam moves to `@SupportedExtension` in a feature
  release once BotTools' adapter has built against it. The release that graduates `FeedParser`
  also publishes `FeedParserContract` as a test-fixtures variant of `webtools-feeds`. A fixtures
  variant in a module whose tests consume another module's fixtures variant is unprobed; that
  release settles it.

## 7. Known gaps carried into the unit plans

Each unit's plan inherits the architecture's open points that fall inside it (§12, §13):

| Unit | Open point it settles |
|---|---|
| 2 `java-floor` | Settled (#76). The floor's effect on dependency resolution is exercised: KGP copies the `jvm` target's attributes onto the four `jvm` classpaths, and `MultiplatformJavaFloorIntegrationTest` asserts that each requests 11 (§8). |
| 3 `http-core` | Settled (#62). The compile proof held in `commonMain`, `jvmMain` and the check module's `jsMain`: a top-level implementer of a gated port compiles with the module's opt-in and fails to compile without it, with the marker's message in the error; a consumer without the opt-in names the gated types, takes `WallClock.System` as a default and constructs every public leaf; eight subclassing declarations give exactly eight errors in `commonTest` and in `jvmTest`, and none with `@OptIn` ([`62-http-core/final-implementation.md`](62-http-core/final-implementation.md) §3). The check module compiles `commonMain` for `js()` with no environment, so the root project gains no task (§8). |
| 4 `test-support` | Settled (#63). The fixtures-publication check, `:build-logic:fixturesPublicationCheck`, is a dependency of `webtools-http`'s own `check`, declared only when WebTools is the root build: `./gradlew build`, `check` and `:webtools-http:check` run it, and a build that includes WebTools never does. It stays the only guard on the experimental publishing API, and it now signs the multiplatform publication, adhoc variant included, with a throwaway key on every run and asserts a signature beside every published file. Only Maven Central's upload and validator meet the fixtures jar first at the first release ([`63-test-support/impact.md`](63-test-support/impact.md) §1 and §5). |
| 8 `call-budget` | How a `Mutex`-based ledger closes without suspending (the port's non-suspend `close()`). |
| 9 `call-facades` | The generic `Endpoint<T>` factory-over-class resolution; whether `RateLimited.retryAfter` still needs the `max` with the budget's `pausedFor` once `complete` has applied the signals. |
| 10 `feeds` | The feeds check module's dependency on the http check module's `js` output, which compiled in a probe under `js()` with no environment ([`62-http-core/research/common-check-plugin.md`](62-http-core/research/common-check-plugin.md) F11; fallback: compile both `commonMain` trees as source); Rome's DOM amplification under the fixed-heap Level 4c suite. |
| none | A compile-fail check for the subclass gate: ABI dumps do not record `@SubclassOptInRequired`, so dropping it stays a review item. |

## 8. What the later units build on: units 1 to 4 as built

Units 1 to 4 are built.
- **Unit 1, `build-logic` (#61).** Its as-built record is
  [`61-build-logic/final-implementation.md`](61-build-logic/final-implementation.md). The exact
  seams are in [`61-build-logic/plan.md`](61-build-logic/plan.md) §11 and
  [`61-build-logic/impact.md`](61-build-logic/impact.md) §4.
- **Unit 2, `java-floor` (#47).** Its as-built record is
  [`47-java-23-bytecode/final-implementation.md`](47-java-23-bytecode/final-implementation.md). Its
  contracts for the later units are in [`47-java-23-bytecode/impact.md`](47-java-23-bytecode/impact.md)
  §4.
- **Unit 3, `http-core` (#62).** Its as-built record is
  [`62-http-core/final-implementation.md`](62-http-core/final-implementation.md). Its contracts for
  the later units are pinned in [`62-http-core/plan.md`](62-http-core/plan.md) §12 and summarised in
  [`62-http-core/impact.md`](62-http-core/impact.md) §4.
- **Unit 4, `test-support` (#63).** Its as-built record is
  [`63-test-support/final-implementation.md`](63-test-support/final-implementation.md). Its contracts
  for the later units are pinned in [`63-test-support/plan.md`](63-test-support/plan.md) §12 and
  summarised in [`63-test-support/impact.md`](63-test-support/impact.md) §4.

What they settle for the units still to come:

**Unit 2, `java-floor` (#47), as built.**
- **The constant and its readers.** `WebToolsBuild.JAVA_FLOOR`, a `const val` of 11, sits beside
  `LEVEL_PACKAGE_ROOT`. Both library plugins read it, and it is the only place in `build-logic`'s
  production sources that holds the number. The two `TODO(#47)` marks are gone.
- **What each plugin sets from it.** The Kotlin/JVM plugin sets `jvmTarget`, `-Xjdk-release` and
  `java { sourceCompatibility; targetCompatibility }`. The multiplatform plugin sets `jvmTarget` and
  `-Xjdk-release` inside `jvm {}`, and the `TargetJvmVersion` attribute.
- **The floor reaches dependency resolution.** This was read in KGP 2.2.0's source; it is now
  exercised.
  - KGP copies the `jvm` target's attributes onto the four `jvm` classpaths:
    `jvmCompileClasspath`, `jvmRuntimeClasspath`, `jvmTestCompileClasspath` and
    `jvmTestRuntimeClasspath`. `MultiplatformJavaFloorIntegrationTest` asserts that each requests 11.
  - So `JAVA_FLOOR` is the JVM version that every `jvm` classpath requests: `main`, `test` and
    unit 4's fixtures compilation. Gradle then rejects a dependency whose metadata declares a higher
    JVM, test-only ones included. Every candidate is at or below 11.
- **The guard.** `verifyJavaFloor` (the task `VerifyJavaFloor`, with the pure scans in
  `JavaFloorScan`) is registered by `WebToolsBuild.registerVerifyJavaFloor` and wired into `check` by
  both plugins.
  - It reads the module's jar (`jar`, or `jvmJar`), every `GenerateModuleMetadata` output of the
    project, and the resolved runtime classpath (`runtimeClasspath`, or the main `jvm` compilation's
    `runtimeDependencyFiles`).
  - It fails on a class above major 55 outside `META-INF/versions/`, on a library variant whose
    `org.gradle.jvm.version` is not the floor, on a class on the runtime classpath above major 55, and
    when there is nothing to check. On success it writes
    `build/reports/java-floor/verifyJavaFloor.txt`.
  - `check` of a module runs the module's suite, so it is run by name: `./gradlew verifyJavaFloor`.
- **The ASM pin is gone.** Every class is major 55, which the validator's own ASM reads, so the
  multiplatform plugin no longer adds the two `bcv-rt-jvm-cp` lines.
- **Lettered versions, `webtools-udp` included.** The architecture's §12 calls `webtools-udp`
  unaffected by the ordering of lettered versions. It is affected: `2.0.0a` orders below `2.0.0`, the
  Java 23 build on Central, as `1.0.0a` orders below `1.0.0`. §1 above states the rule for all three
  artifacts. `architecture.md` §12 carries a dated correction.
- **Tests.** `expectedJvmVersion` in `PublishedJvmVersionAttributeIntegrationTest` is the literal 11
  (it was the test JVM's feature version). The new `build-logic` tests are in level packages, because
  every `build-logic` test task fails on an empty run, and no Gradle test task was added.

**Unit 3, `http-core` (#62), as built.**
- **The module.** `webtools-http` (package `com.spartanlabs.webtools.http`, `explicitApi()`) is the
  first module on `webtools.kotlin-multiplatform-library`.
  - It holds the opt-in marker `ExperimentalForInheritanceWebToolsApi`, the failure model with its two
    reason vocabularies, `HttpMethod`, `Url`, `HttpHeaders`, `RequestBody`, `HttpRequest`,
    `HttpResponse`, `RedirectHop`, and the gated ports `HttpTransport` and `WallClock`, with
    `WallClock.System`.
  - Its `internal` helpers `Log`/`logger`, `flatMap`, `catchNonFatal` and `HttpSyntax` serve the later
    units in the module.
  - Its dump, `api/webtools-http.api`, is committed; each later unit extends it.
  - The names it takes, public and `internal`, are listed in
    [`62-http-core/plan.md`](62-http-core/plan.md) §12. No sibling reuses them.
- **The opt-in.**
  - The marker gates only the seams a consumer implements, through `@SubclassOptInRequired`. Nothing a
    consumer calls needs an opt-in.
  - The module opts in with `languageSettings.optIn` on `commonMain` and `jvmMain`, in its own build
    file. In a single-target module only the leaf source set's setting reaches the compiler, and
    `kotlin { compilerOptions { optIn } }` would reach the test compilations, which stay consumers.
  - A top-level `jvmMain` implementer of a gated port, such as unit 6's transport or unit 7's cache,
    needs no `@OptIn`.
  - An associated compilation does not inherit the setting: unit 4's fixtures source sets opt in
    themselves for the fakes that implement `HttpTransport` and `WallClock`.
  - A new gated hierarchy carries its own `@SubclassOptInRequired`. Unit 10's `FeedFailure` does, and
    extends `HttpFailure` through its `protected` constructors `(message)` and `(message, cause)`.
- **Failures.** Every leaf an implementer or a decoder creates has a public constructor: every
  `TransportFailure` leaf, with and without a `cause`; `HttpFailure.Closed(component)`;
  `HttpFailure.BudgetExhausted(wait, reason, window)`; `HttpFailure.DecodeFailure(cause)`;
  `UpstreamFailure.UpstreamError(status, headers, body)`. `UpstreamFailure.RateLimited` has an
  `internal` constructor, for unit 9's facades. Every message is constant.
- **`HttpTransport.execute` is main-safe** (point 8 of the port's contract): an implementation moves
  blocking I/O off the caller's thread and documents which dispatcher it uses. Unit 6's OkHttp
  transport does it through OkHttp's own dispatcher and a cancellable callback; unit 4's fake meets it
  by never blocking.
- **Sensitive headers.** `addSensitive` and `setSensitive` mark an entry: it is never printed, and the
  stack's redirect policy reads the flag. A credential name (`HttpSyntax.isCredentialName`) is
  sensitive without the flag, and `HttpHeaders.Entry.isSensitive` answers for both. Units 5 and 6
  build the reading: the `Untrusted` refusal of a sensitive header, and the strip on a cross-origin
  hop.
- **`Url`.** `Url.parse` normalises as OkHttp 5.5.0's `HttpUrl` does for an ASCII host and rejects the
  ambiguous or unsafe. `toString()` is the full canonical URL, query included; `redacted()` is the form
  the library logs. The differential against `HttpUrl` found no disagreement on the corpus and on
  4.2 million generated inputs. No query decoder exists.
- **The check-module pattern.** `webtools.kotlin-common-check` applies the multiplatform plugin,
  declares `mavenCentral()`, calls `js()` with no environment, and wires `check` to `compileKotlinJs`.
  - With no environment the `js` target has no test task. `build` downloads no Node.js or Yarn and
    writes no `kotlin-js-store/`, and the root project gains no plugin and no task. Unit 4 therefore
    attached its publication check to `webtools-http`'s `check` (below).
  - The cost is the Kotlin Gradle plugin's "JS Environment Not Selected" warning, once per
    configuration-cache miss. If a release makes it an error, the plugin's TestKit `build` test fails,
    and the fallback in the plugin's header applies,
    `js { nodejs { testTask { enabled = false; setDependsOn(emptyList<Any>()) } } }`: it downloads
    nothing but gives the root project base-plugin tasks.
  - A check module's own build file adds the sibling's `commonMain` as a relative `srcDir`, the
    dependencies of that `commonMain`, and the opt-in on `commonMain` and `jsMain`. Its `jsMain` holds
    an actual for every `expect` declaration.
  - Unit 10's check module also takes `implementation(project(":webtools-http-commoncheck"))`, which
    compiled in a probe
    ([`62-http-core/research/common-check-plugin.md`](62-http-core/research/common-check-plugin.md)
    F11). The `internal` helpers of `webtools-http` stay invisible to it, so it carries its own
    `Log`/`logger` and their `jsMain` actual.
- **What each later unit takes,** beyond the points above. [`62-http-core/plan.md`](62-http-core/plan.md)
  §12 has the signatures.
  - **Unit 4 (#63).** The `.http` parser reaches `HttpSyntax` through the association. The
    `HttpResponse` constructor refuses a status outside 100..599. `UnmatchedRequestError` prints
    `request.url.redacted()`. `kotlinx-coroutines-test`, on the fixtures variant, and a query decoder,
    if a matcher needs one, are unit 4's to add.
  - **Unit 5 (#64).** `Url.host` is canonical, but digits-and-dots and hex-looking text (`127.1`,
    `0x7f.1`, `2130706433`) stay as written, for the policy to judge. `port` is the effective port, and
    there is never user-info. `RefusalReason` has nine constants, and `RefusedByPolicy(reason)` is
    public.
  - **Unit 6 (#65).** `Url` goes to `HttpUrl` through `request.url.toString().toHttpUrl()`, never
    rebuilt from its parts. A received status outside 100..599 is `Protocol`; an entry whose name is not
    a token is dropped; CR, LF and NUL in a received value become SP. `Content-Type` goes as a plain
    header, with OkHttp's `MediaType` left null, and a method OkHttp requires a body for gets an empty
    one. OkHttp's exception becomes a failure's `cause` only where the hosts, addresses or URLs in its
    message are acceptable, and that message is never logged above `DEBUG`.
  - **Units 7 and 8 (#66, #67).** `Closed("cache")` and `Closed("budget")`; `BudgetExhausted` with
    `ExhaustedReason`; `WallClock`, with `WallClock.System` the default; `catchNonFatal` and `logger`.
    A `Url` is not the cache key: its equality is on bytes, and the key is the facade's canonical
    parameters.
  - **Unit 9 (#68).** URLs are assembled as text with the `internal` `encodePathSegment` and
    `encodeQueryComponent`, then parsed again; an empty path segment is `InvalidRequest`.
    Developer-written headers and bodies are built inside `catchNonFatal`, and a failure becomes
    `InvalidRequest` before a permit is spent. `HttpResponse.body()` copies on every call, so a facade
    decodes once.
  - **Unit 11 (#70).** The tiers and contracts come from the module's KDoc, and the gate's compile-fail
    texts from [`62-http-core/final-implementation.md`](62-http-core/final-implementation.md) §3. The
    evergreen architecture document describes the check module as `js()` with no environment.
- **The IDE checks unit 3 leaves** (IntelliJ's import of the check module, the gate's error and its
  `@OptIn` quick fix, Quick Documentation) are the maintainer's and still open. They share unit 4's
  trigger: the next IntelliJ import after #63 lands, at the latest before unit 5 (#64) starts
  ([`62-http-core/impact.md`](62-http-core/impact.md) §7).

**Units 3 `http-core` and 10 `feeds`.**
- **What the multiplatform plugin carries:**
  - the `jvm()` target, compiled to the Java floor (`jvmTarget` and `-Xjdk-release` from `JAVA_FLOOR`);
  - publishing;
  - the test dependencies;
  - the level tasks, with a lifecycle `test`;
  - the attribute (`TargetJvmVersion`, the floor);
  - `verifyJavaFloor`, in `check`;
  - the validator, on its defaults (the ASM pin is gone).
- **What a module adds:**
  - `explicitApi()`;
  - its dependencies, its coordinates, and its POM name and description;
  - its dump, `api/<project name>.api`, committed with its first public declaration, because
    `apiCheck` fails until the dump exists.
- **What a module must not do:**
  - apply the validator again, on itself or on an ancestor;
  - declare a second JVM-based target;
  - set its own `jvmTarget`, toolchain, `-Xjdk-release` flag or `TargetJvmVersion`;
  - call a JDK API newer than 11, or use `@JvmRecord` (it needs JVM 16).
- **`webtools.kotlin-common-check`** (unit 3) declares its own repositories. The plugin markers
  already in `build-logic` cover it.
- **The README.** "Build & test" describes `build-logic`, its four plugin ids, the package-selection
  rule, the `commonTest`/`jvmTest` layout and the `js` compile of the check modules. Units 10 and 11
  extend it to five modules.

**Unit 4, `test-support` (#63), as built.**
- **The variant.** `webtools-http` has a JVM-only `testFixtures` compilation, associated with `main`,
  with `test` associated with it. It is published as the variants `testFixturesApiElements` and
  `testFixturesRuntimeElements` beside a `-test-fixtures.jar` (recipe B, in the module's own build file).
  - Its package is `com.spartanlabs.webtools.http.testing`, its tier Stable Core with source
    compatibility only, and `-Xexplicit-api=strict` applies to it.
  - It depends on exactly `webtools-http`, `kotlinx-coroutines-test` 1.10.2 and `mockwebserver3` 5.5.0;
    `mockwebserver3` is there for unit 6.
  - It has no source-set opt-in: each implementer of a gated port carries its own `@OptIn`.
  - `verifyJavaFloor` reads the fixtures jar and the entries of the fixtures' runtime classpath that
    exist.
- **The check.** `:build-logic:fixturesPublicationCheck` publishes a copy of the module, signed with a
  throwaway key, to a file repository in its workspace. It resolves `testFixtures(...)` from that
  repository, through a composite and from an in-build Kotlin Multiplatform module, in ten tests.
  - `webtools-http`'s `check` depends on it when WebTools is the root build, so `./gradlew build`,
    `check` and `:webtools-http:check` run it, and a build that includes WebTools never does.
  - Its inputs are the module's build file, its `commonMain`, `jvmMain` and `jvmTestFixtures` trees and
    the root build file, so a later unit's new fixtures run it again.
- **Rules for the later units.**
  - Fixtures go in `webtools-http/src/jvmTestFixtures/kotlin/com/spartanlabs/webtools/http/testing/`:
    each implementer of a gated port with its own `@OptIn`, every public declaration with explicit
    visibility and KDoc.
  - No new fixtures dependency: the check asserts the list.
  - No module script reassigns `runtimeDependencyFiles`: the level tasks read the test compilation's
    once, and the fixtures reach them through the association.
  - A new task in `build-logic` that starts nested builds calls `configureNestedBuilds()`, which carries
    the TestKit properties, `testKitHomeLock` and the timeout.
  - The names [`63-test-support/plan.md`](63-test-support/plan.md) §12 lists are taken.
    `LoopbackTransports`, `webtoolsUrl`, `toMockResponse` and `enqueue(fixture)` are reserved for unit 6,
    and `RecordingBudgetObserver` and `FakeBudgetStore` for unit 8.
- **What each later unit takes.** [`63-test-support/plan.md`](63-test-support/plan.md) §12 has the
  signatures.
  - **Unit 5 (#64).** Nothing in the fixtures.
  - **Unit 6 (#65).** It adds `LoopbackTransports`, `webtoolsUrl`, `Fixture.toMockResponse()` and
    `MockWebServer.enqueue(fixture)` to the fixtures compilation, built on `Fixture.status`, `headers`
    and `body()`, with no new dependency.
  - **Unit 7 (#66).** `CaffeineResponseCache` takes `VirtualClock.timeSource`
    (`TimeSource.WithComparableMarks`), `dispatcher` (`CoroutineDispatcher`) and `maintenanceExecutor`
    (`java.util.concurrent.Executor`), so its default dispatcher is a constructor parameter. The unit
    re-runs the three-clocks proof with the real cache. `UnmatchedRequestError` is an `Error` and passes
    through a cache loader as one.
  - **Unit 8 (#67).** It adds `RecordingBudgetObserver` and `FakeBudgetStore`, each with its own
    `@OptIn`. `WindowedCallBudget` takes `VirtualClock.timeSource` and `wallClock`, and the unit re-runs
    the three-clocks proof with the real budget.
  - **Unit 9 (#68).** It scripts `FakeHttpTransport`, for example
    `Step.respond(429, HttpHeaders { add("Retry-After", "30") })` or
    `Step.fail(TransportFailure.RefusedByPolicy(...))`. It passes `VirtualClock.dispatcher` as the
    client's dispatcher, asserts with `calls.size` that a refusal costs nothing, and catches a stray
    request with `unmatchedCalls`.
  - **Unit 10 (#69).** `jvmTestImplementation(testFixtures(project(":webtools-http")))` resolves the
    variants, measured. `Step.respond { … }` carries a `redirectChain` for `permanentlyMovedTo`, and
    `RequestMatch.header` checks the conditional headers. Its body-only `.rss` and `.atom` fixtures live
    in its own resources. The `.gitattributes` line covers `*.http` files only: a body-only file of more
    than one line changes its line endings on a checkout with `core.autocrlf=true` unless a line of its
    own covers it.
  - **Unit 11 (#70).** It documents the fixtures (the tier, the `.http` format, the recorder workflow,
    the `testFixtures(...)` line, the Gradle-only note) and the check, from unit 4's KDoc and README text.
- **The IDE checks of units 3 and 4** are the maintainer's: at the next IntelliJ import after #63 lands,
  and at the latest before unit 5 (#64) starts
  ([`63-test-support/impact.md`](63-test-support/impact.md) §7).
- **The first release.** The check signs the multiplatform publication, adhoc variant included, on every
  run. Only Maven Central's upload and validator meet the fixtures jar first, at `webtools-http`'s first
  release (§7).
- **`architecture.md`** carries dated corrections (2026-10-25) at §6.1 (where the check attaches), §12
  (the signing on every run of the check) and §6.7 (the two `MockWebServer` helpers land with unit 6).
