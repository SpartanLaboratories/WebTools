# Plan: `http-core` — the `webtools-http` common core and the check-only `js` compile

- **Covers:** `SpartanLaboratories/WebTools#62` — *"webtools-http: module skeleton, opt-in marker,
  failure model, request/response types, transport and wall-clock ports, logging seam and the
  check-only `js` compile"* (work level 3, `type: feature`). Sub-issue of the level-4 tracking
  issue `SpartanLaboratories/WebTools#55` (HTTP client stack). Blocked by #61 and #47, both landed.
  Blocks #63, #64, #66 and #67 directly, and through them the rest of the stack.
- **Architecture:** [`../architecture.md`](../architecture.md), unit 3 `http-core` (§6.2
  *`webtools-http` common core*, §6.8 *Check-only `js` modules*, §6.9 *Common-code mechanism per
  type*, §9 *Extension & stability*, §11 unit 3; also §1 requirements 1, 2, 6, 7 and 9, §6.1
  `webtools.kotlin-common-check`, §6.6 logging rules, §6.11 documentation, §7.4, §8). Systems-level
  impact: [`../impact.md`](../impact.md) §7 (the open point this unit settles: the first compile
  proof) and §8 (what units 1 and 2 left, as built).
- **Research:** [`research/README.md`](research/README.md) with
  [`research/opt-in-gate.md`](research/opt-in-gate.md),
  [`research/common-check-plugin.md`](research/common-check-plugin.md),
  [`research/url-parsing.md`](research/url-parsing.md) and
  [`research/header-and-method-validation.md`](research/header-and-method-validation.md).
- **Branch:** `feat/issue-62-http-core` (exists; cut from `master` at `f72b115`; holds the research
  commits).
- **Commit:** TBD — this `plan.md` is committed in the same commit as the first implementation
  commit (§11), so `git log --follow` binds the two.
- **PR:** TBD (the PR closes #62).
- **What this plans:**
  - the new Kotlin Multiplatform module `webtools-http`: build file, the opt-in marker, the failure
    model with both reason vocabularies, `HttpMethod`, `Url`, `HttpHeaders`, `RequestBody`,
    `HttpRequest`, `HttpResponse`, `RedirectHop`, the `HttpTransport` and `WallClock` ports, the
    logging seam, the `Result` and non-fatal helpers, its `logback-test.xml` and its ABI dump;
  - the unpublished check-only module `webtools-http-commoncheck` and the new convention plugin
    `webtools.kotlin-common-check` it applies, with the plugin's TestKit checks in `build-logic`;
  - the first compile of the opt-in marker, a gated interface, a default that names it and a
    consumer without the opt-in, in `commonMain` and `jvmMain` (§6.2, procedure P3);
  - the tests of all of it, from Level 1 to Level 4c, plus the Level 5 pass;
  - the README changes the module makes necessary.
- **Status:** planning only. The implementer appends each deviation from this plan, with its
  reasoning, to [`final-implementation.md`](final-implementation.md) §2 at the moment it is made, and
  records the compile proof's output in its §3.
- **Version moved:** `webtools-http` `1.0.0` (new). It is inherited from the root default
  (`build.gradle.kts:12`), it stays unreleased until the whole stack (#55) is complete, and it is
  **not a release candidate** when this unit lands. No existing module moves a version.
- **Baseline:** `master` at `f72b115`. Line references are to it.

Code blocks in this plan are the intended implementation. They were written against the
research's compiled probes, but the blocks themselves have not been compiled. The executor fixes a
compile error mechanically, and records any change to a rule (as opposed to a spelling) in
`final-implementation.md` §2.

> **Amended during implementation (2026-10-25).** Amendment 1 makes §9's `MediaTypeDeterministicTest`
> row agree with the media-type grammar of §6.4 and the `mediaTypeProblemIndex` code of §7.5.
> - `text/plain;;` and `text/plain ;` are accepted. RFC 9110's `parameters = *( OWS ";" OWS [ parameter ] )`
>   makes an empty parameter and OWS before `;` valid, as the research's content-type table also records
>   for `text/plain;` and `text/plain ; charset=x`.
> - `text/plain; ` is refused, at index 11: a field value carries no trailing whitespace.
>
> §6.4 and §7.5 are unchanged.

> **Amended after qa pass 1 (2026-10-25).** Amendment 2 records two test-scope positions in §9:
> - **Level 1.** `webtools-http` has no per-type smoke class at Level 1 beyond the two opt-in consumer
>   proofs. Its pre-commit gate is `gatingTest componentTest deterministicTest`.
> - **Level 3.** The `webtools.kotlin-common-check` plugin is not part of `IncludedBuildEndToEndTest`'s
>   composite. It is covered at Level 3 by `CommonCheckPluginIntegrationTest`, and the repository's own
>   build applies it through the real included-build path.

---

## 1. Requirements

### R1. The module

`webtools-http` is a new Kotlin Multiplatform module on `webtools.kotlin-multiplatform-library`.
- Coordinates `io.github.spartanlaboratories:webtools-http`, version `1.0.0` inherited from the
  root default; it publishes `webtools-http` and `webtools-http-jvm`.
- One flat package, `com.spartanlabs.webtools.http`. `internal` does the hiding; there is no
  `.internal` subpackage.
- `explicitApi()`.
- Its build file states, in a comment, that `1.0.0` is unreleased and is not a release candidate
  until the whole stack (#55) is complete.
- Dependencies:
  - `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2` is the one `api` dependency, in
    `commonMain`;
  - `com.squareup.okhttp3:okhttp-jvm:5.5.0`, `com.github.ben-manes.caffeine:caffeine:3.3.1` and
    `org.slf4j:slf4j-api:2.0.13` are `implementation` in `jvmMain`. Only the `jvmTest` differential
    uses OkHttp in this unit; the other two are declared now for the units that follow;
  - `kotlinx-coroutines-test` is not added: no test of this unit needs it (`runBlocking` in `jvmTest`
    covers the non-fatal helper).
- The module sets no `jvmTarget`, toolchain, `-Xjdk-release` or `TargetJvmVersion`, declares no
  second JVM-based target, does not apply the validator again, uses no JDK API newer than 11 and no
  `@JvmRecord`.

### R2. Shape of the public API

- Configurable types are built by public factory functions taking a builder lambda over an
  `internal` constructor. A type whose every constructor parameter is required, and is its meaning,
  keeps a public constructor. There is no public `data class`.
- No public constructor and no Stable Core function has a defaulted parameter; explicit overloads
  stand in. The one default allowed is a trailing builder lambda `= {}`.
- A top-level factory function lives in the file named after its class.
- A developer-written value is checked with `require` and throws `IllegalArgumentException` at
  construction. Text from outside the process returns `Result`. Cancellation is thrown, never
  wrapped. No `runCatching` surrounds a suspend call anywhere in the module.
- Every public declaration carries Level-2 KDoc (`@param`, `@return`, `@throws`) that names its
  tier and states its contract.

### R3. Stability

- Everything a consumer calls or configures is Stable Core.
- The seams are Experimental (implement or subclass), behind one marker,
  `ExperimentalForInheritanceWebToolsApi`:
  - `@RequiresOptIn(level = ERROR)`, its message **one string literal**, so that the module's own
    diagnostics carry it;
  - `@Target()`, `BINARY` retention, `@MustBeDocumented`;
  - used only as the argument of `@SubclassOptInRequired` and `@OptIn`; never removed.
- The gated types are `HttpTransport`, `WallClock`, `HttpFailure`, `TransportFailure` and
  `UpstreamFailure`. The supplied default `WallClock.System` is final and usable without the opt-in.

### R4. The failure model

- `abstract class HttpFailure : Exception`, subclass-gated, with `protected` constructors
  `(message)` and `(message, cause)`.
- The groups `TransportFailure` and `UpstreamFailure` are abstract, subclass-gated, with `protected`
  constructors. `UpstreamFailure` is `protected constructor(public val status: Int, message: String)`.
- Final leaves nested in their group:
  - `HttpFailure`: `Closed`, `BudgetExhausted(wait: Duration, reason: ExhaustedReason, window: String?)`,
    `DecodeFailure`;
  - `TransportFailure`: `Timeout`, `ConnectFailed`, `DnsFailed(host)`, `TlsFailed`, `Protocol`, `Io`,
    `InvalidRequest`, `RefusedByPolicy(reason: RefusalReason)`, `BodyTooLarge(limit: Long, declared: Long?)`,
    `TooManyRedirects(limit: Int)`, `RetryRefused`;
  - `UpstreamFailure`: `RateLimited(status, retryAfter: Duration)`, `UpstreamError(status, headers, body)`.
- Every leaf that a seam implementer or a decoder must create has a **public** constructor: every
  `TransportFailure` leaf, `Closed`, `BudgetExhausted`, `DecodeFailure` and `UpstreamError`.
  `RateLimited` has an `internal` constructor, because only the facades create it. `Closed` takes the
  closed component's name (for example `"transport"`).
- Constructors take only non-sensitive fields and, where a lower-level throwable exists, a
  `cause: Throwable?`. Every message is a constant per type plus those fields; never free text a
  caller passes. `InvalidRequest`'s one field is a short, library-written description of the broken
  rule, documented as never holding request data. Primary constructors are frozen.
- `UpstreamError` keeps its headers and body (copied in, read back as a copy) and never prints them.
- `RefusalReason` and `ExhaustedReason` are open constants classes: a private constructor, equality by
  name, no `of()`. `RefusalReason`: `Scheme`, `UserInfo`, `HostForm`, `ReservedName`, `DeniedAddress`,
  `Port`, `HostNotPinned`, `InsecureRedirect`, `SensitiveHeader`. `ExhaustedReason`: `Window`,
  `Paused`, `CostExceedsLimit`, `QueueFull`. Their KDoc says "may gain constants in a feature release;
  keep an `else`".

### R5. The ports

- `HttpTransport : AutoCloseable`: `public suspend fun execute(request: HttpRequest): Result<HttpResponse>`
  and `override fun close() {}`. The KDoc contract: one request per hop, the caps, a `304` is a
  response and never a failure, cancellation is thrown, `Closed` after `close()`.
- `WallClock`: `public fun nowEpochMillis(): Long`, with a nested `public object System : WallClock`
  (the shape of the stdlib's `TimeSource.Monotonic`), `toString()` `"WallClock.System"`, over
  `internal expect fun epochMillisNow(): Long`.

### R6. The logging seam and the helpers

- `internal interface Log` with `debug`, `info` and `warn(message: String, vararg args: Any?)` taking
  `{}` placeholders, obtained through `internal expect fun logger(name: String): Log`. The `jvmMain`
  actual wraps slf4j's `LoggerFactory.getLogger(name)`; the check module's `jsMain` actual is a no-op.
  This unit has no production logging site; the seam is delivered and tested for the later units.
- An `internal` copy of `Result.flatMap`, identical in body to
  `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ResultExtensions.kt:19-20`; its header
  names both other copies. The two legacy files are not edited.
- An `internal inline` non-fatal catch helper that turns an `Exception` other than
  `CancellationException` into `Result.failure`, rethrows `CancellationException`, and lets `Error`
  propagate.
- Each `actual` file is named distinctly from its `expect` file (`Log.jvm.kt`, `EpochMillis.jvm.kt`),
  so no two JVM facade classes clash.

### R7. The opt-in wiring and the opening compile proof

- The module-wide opt-in is a **language setting of the main source sets, in each module's own build
  file**, not in a convention plugin: `webtools-http` sets it on `commonMain` and `jvmMain`;
  `webtools-http-commoncheck` on `commonMain` and `jsMain`. The build files say, in a comment, that
  `kotlin { compilerOptions { optIn.add(...) } }`, the target-level option and `sourceSets.all { }`
  reach the test compilations too and must not be used.
- `commonTest` and `jvmTest` compile **without** the opt-in and stay a permanent consumer: they
  mention the gated types, use `WallClock.System` as a parameter default and construct the public
  leaves.
- No permanent guard task or check of the gate or of the test wiring is added.
- The implementation opens with the compile proof, in the real module: main compiles; a wiring canary
  (three throwaway top-level implementers, one each in `commonMain`, `jvmMain` and the check module's
  `jsMain`) compiles and, with the opt-in lines removed, fails with the in-module text; the positive
  consumer cases compile in `commonTest` and `jvmTest`; the eight negative cases fail with exactly
  eight consumer errors and compile with `@OptIn`; `javap -v` and the ABI dump are read. The output is
  recorded in `final-implementation.md` §3.

### R8. `Url`

- `Url.parse(text): Result<Url>` accepts text and rewrites it exactly as OkHttp 5.5.0's `HttpUrl`
  does when the host is ASCII:
  - percent-encodes with `HttpUrl`'s sets, non-ASCII path and query text as UTF-8 in upper-case hex;
  - resolves `.` and `..`, the `%2e` forms included;
  - drops the default port and gives an empty path `/`;
  - trims TAB, LF, FF, CR and SP at the ends;
  - drops a `#fragment`;
  - keeps an existing `%XX`, and a malformed `%`, as typed;
  - keeps a bare `?` as an empty query (`""`, not `null`);
  - prints IPv6 in RFC 5952 form and an IPv4-mapped address as dotted IPv4;
  - keeps digits-and-dots hosts and a trailing dot as written.
- It rejects, each only ever stricter than `HttpUrl`: a backslash; whitespace (Unicode `White_Space`),
  a C0 or C1 control or DEL inside the text; an unpaired surrogate; any scheme but `http` and `https`
  and any separator but `://`; user-info; a host that is non-ASCII or percent-encoded, has a character
  outside `a-z 0-9 - _ .`, breaks the length rules, has an `xn--` label, or is a malformed IPv6 literal
  (a zone included; `[1:2:3:4:5:6:7::8]`, which crashes `HttpUrl`, is rejected); a port that is signed,
  non-numeric, `0` or above 65535.
- Members: `scheme`, `host` (IPv6 without brackets), the effective `port`, `encodedPath`,
  `encodedQuery: String?`. `toString()` is the full canonical URL, query included, and its KDoc warns
  about it. `redacted()` is `toString()` with `?...` in place of a query. Equality and hash are on the
  canonical text.
- A failure is `Result.failure` of an `IllegalArgumentException` with one of the constant messages of
  §6.5, never echoing the input, created through an `internal` subclass whose kind tests can read.
- `Url` has no public builder and no `withQuery`. The `internal` `encodePathSegment` (refuses `.` and
  `..` and an unpaired surrogate) and `encodeQueryComponent` (raw set `A-Za-z0-9*-._`, so `~` becomes
  `%7E`) reproduce `HttpUrl.Builder.addPathSegment` and `addQueryParameter`. Building a URL from parts
  is the facades' job: assemble as text, then `Url.parse`.

### R9. Headers, methods, statuses and bodies

- Header names are the RFC 9110 token everywhere, checked in the `HttpHeaders` builder.
- Header values obey two rules: the container rule (after trimming SP and HTAB at the ends, no CR, LF
  or NUL) and the sendable rule (HTAB or `0x20..0x7E`), which `HttpRequest` applies to every value it
  will send.
- Lookup uses an ASCII-only fold. `get` returns the last value; `getAll` every value, empty when
  absent; `set` replaces every entry of the name, appends at the end and keeps an existing sensitive
  flag; `setSensitive`, `remove` and `addAll` complete the builder. `Entry.isSensitive` is true when
  the entry is flagged or its name is `Authorization`, `Proxy-Authorization`, `Cookie` or `Set-Cookie`.
- `HttpMethod.of(name)` accepts exactly `1*tchar`, case-sensitively; the seven constants (`Get`, `Head`,
  `Post`, `Put`, `Patch`, `Delete`, `Options`) are returned for their exact names; the failure is
  `Result.failure(IllegalArgumentException)` with a constant message. `permitsBody` is `internal` and
  false for `GET`, `HEAD` and `TRACE`.
- `HttpRequest` refuses, with `IllegalArgumentException`, a body on `GET`, `HEAD` or `TRACE`; the method
  `CONNECT`; a non-sendable header value; a `Content-Type` header beside a body's content type. `POST`,
  `PUT` and `PATCH` may omit a body.
- `HttpResponse` takes a status in 100..599 and `RedirectHop` one in 300..399.
- `RequestBody(bytes, contentType: String?)` has a public frozen-shape constructor; the content type is
  an RFC 9110 media type (its visible-ASCII subset) or null.
- Shared predicates live in `internal object HttpSyntax`, which the transport and the fixtures reuse.
- Messages are constants: a header name is echoed only after it passes the token rule, a value never.

### R10. Equality and printing

- Value equality: `Url`, `HttpMethod`, `HttpHeaders`, `RedirectHop`, `RefusalReason`, `ExhaustedReason`.
  Identity equality: `HttpRequest`, `HttpResponse`, `RequestBody`.
- Byte arrays are copied on the way in and read back as copies, so a response shared by several
  waiting callers cannot be changed by one of them.
- `HttpHeaders`, `HttpRequest`, `HttpResponse` and `RequestBody` print their structure only (names,
  method or status, the redacted URL, sizes); never header values or bodies.

### R11. The check-only `js` module

- `webtools.kotlin-common-check` is a new precompiled script plugin: it applies
  `org.jetbrains.kotlin.multiplatform`, declares `repositories { mavenCentral() }`, calls `js()` with
  **no environment** and wires `check` to `compileKotlinJs`. Its header comment says why there is no
  environment, what the accepted cost is and what the fallback is. `build-logic/build.gradle.kts` does
  not change.
- `webtools-http-commoncheck/build.gradle.kts` applies the plugin, adds the sibling's `commonMain`
  directory as a source directory, sets the opt-in on `commonMain` and `jsMain`, and declares
  `kotlinx-coroutines-core:1.10.2`. It has no `explicitApi()`, no publishing and no version.
- Its `jsMain` holds the two actuals: a no-op `Log`, and `epochMillisNow()` as
  `kotlin.js.Date.now().toLong()`.
- `settings.gradle.kts` includes `:webtools-http` and `:webtools-http-commoncheck`. The root project
  gains no plugin and no task, so it still has no `check` of its own. Nothing downloads Node.js or
  Yarn, and no `kotlin-js-store/` appears.
- `build-logic` gains `CommonCheckPluginIntegrationTest` (Level 3, seven tests),
  `CommonCheckConfigurationCacheReuseNonFunctionalTest` (Level 4c), the `SampleProject.commonCheck(...)`
  factory with its constants and templates, and `"webtools.kotlin-common-check"` in
  `BuildLogicPluginsGatingTest.conventionPluginIds`.

### R12. Files and the README

- `webtools-http/src/jvmTest/resources/logback-test.xml` is a byte copy of the three existing copies.
- `webtools-http/api/webtools-http.api` is generated by `:webtools-http:apiDump` and committed.
- `README.md` changes in the same change (§8). No `.gitignore` or `.gitattributes` change.

### R13. Tests and runs

- Every test sits at a level and a path (§9). Only the tests that exercise the change run. No suite of
  `webtools-udp`, `webtools-scraping` or `webtools-browser` runs; no root `./gradlew build` or `test`.
  Exact task names are in §9.

### Acceptance

1. `:webtools-http:check` passes: `jvmTest` (every level), `apiCheck`, `verifyJavaFloor`.
2. `:webtools-http-commoncheck:check` passes: the module's `commonMain` compiles for `js`.
3. `:build-logic:test`, `:build-logic:integrationTest`, `:build-logic:e2eTest` and
   `:build-logic:nonfunctionalTest` pass.
4. The compile proof ran in the real module and its output is in `final-implementation.md` §3: the
   canary compiled with the wiring and failed without it; eight consumer errors, then none with `@OptIn`.
5. The generated `webtools-http` POM and Gradle Module Metadata show `kotlinx-coroutines-core` on the
   compile classpath and `okhttp-jvm`, `caffeine` and `slf4j-api` at runtime only.
6. `Url.parse` agrees with `HttpUrl` on the whole corpus and on 200,000 generated inputs, with no
   disagreement and the coverage floors met.
7. The root project has no `check` task; no Node.js, Yarn or `kotlin-js-store/` appears; the three
   existing modules compile unchanged.
8. The README states the module, and no sentence the module makes false remains.

---

## 2. Current state

**Baseline.** `master` at `f72b115`. The branch holds only the research commits above. Units 1
(`build-logic`, #61) and 2 (`java-floor`, #47) have landed; no module applies
`webtools.kotlin-multiplatform-library` yet.

**The build.**
- `settings.gradle.kts:1-3` includes `build-logic` through `pluginManagement`; `:6` applies
  foojay 0.8.0 (never consulted); `:11` is `include(":webtools-udp", ":webtools-scraping", ":webtools-browser")`.
- `build.gradle.kts:1-9` is a comment that says "WebTools is published as three independent,
  single-concern artifacts" and lists three convention plugins; `:10-13` is
  `allprojects { group = "io.github.spartanlaboratories"; version = "1.0.0" }`.
- `build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts`:
  - `:18-23` applies the multiplatform plugin, `webtools.published-library`, vanniktech and
    binary-compatibility-validator;
  - `:25-53` declares `jvm()` with `jvmTarget` and `-Xjdk-release` from `WebToolsBuild.JAVA_FLOOR`
    (`WebToolsBuild.kt:45`, `11`), the `TargetJvmVersion` attribute and the seven level tasks over the
    `test` compilation;
  - `:55-67` adds `kotlin("test")` to `commonTest` and `kotlin("test-junit5")` plus logback 1.5.6 to
    `jvmTest`; `:69-80` sets `useJUnitPlatform()` and registers the lifecycle `test`;
  - `:82-88` pins `KotlinMultiplatform(javadocJar = Empty(), sourcesJar = Sources())`;
  - `:90-107` registers `verifyJavaFloor` and wires it into `check`.
  It declares no source-set dependency, no opt-in, no `explicitApi()` and no `js` target.
- `build-logic/build.gradle.kts:12-15` holds the plugin markers (Kotlin JVM and multiplatform 2.2.0,
  vanniktech 0.36.0, binary-compatibility-validator 0.18.2). The multiplatform marker already covers
  the new plugin, so that file does not change.
- Convention plugin ids are listed at
  `build-logic/src/test/kotlin/com/spartanlabs/testing/gating/webtools/buildlogic/BuildLogicPluginsGatingTest.kt:17-21`.
  The TestKit harness (`SampleProject`, `ProjectWorkspace`, `NestedBuild`, `TestReports`, …) is under
  `build-logic/src/test/kotlin/com/spartanlabs/testing/support/webtools/buildlogic/`.
  `SampleProject.kt` has `withFile` (`:23`), the factories `multiplatformJvm` (`:94`), `kotlinJvm` and
  `includedBuildComposite` (`:156`), and `GRADLE_PROPERTIES` (`:303`). The file is LF.
- `.gitattributes`: `*.kt text eol=lf`, `*.kts text eol=lf`. On this Windows checkout `core.autocrlf`
  is `true`: `README.md` and `logback-test.xml` are CRLF in the working tree and LF in the index.

**Patterns the module follows.**
- `webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ReliableSendFailure.kt:15` is an open
  `abstract class … : Exception` carried in `Result`, deliberately not sealed.
- `ResultExtensions.kt:19-20` is `internal inline fun <T, R> Result<T>.flatMap(...)`, and the file
  header records the deliberate duplication between `webtools-udp` and `webtools-scraping`.
- `README.md:82-88` states the "keep an `else`" rule for entries that may grow.
- Level-2 tests in the repository use hand-written fakes; MockK appears nowhere. This unit has no
  external call to fake.

**README sentences the module makes false** (working-tree line numbers equal the index's):
`:3` ("published as three independent … artifacts"), `:477` ("the three modules are …"), `:497-501`
(tests live under `<module>/src/test/kotlin/…`), `:505` and `:514` ("Every module compiles to Java 11
bytecode", "checks every module"), `:519` ("three convention plugins"), `:526` ("no module uses it yet").

**Outside-world facts this unit rests on** (each measured or source-read in the research, which
re-opened every load-bearing claim: 107 claims, all hold):
- Kotlin Gradle plugin (KGP) 2.2.0 on Gradle 9.7.1. `@SubclassOptInRequired` gates implementing,
  delegating and subclassing but not mentioning a type. A class lexically inside a gated declaration
  compiles without the opt-in, so only a top-level or platform-source-set implementer exercises the
  module-wide opt-in ([`opt-in-gate.md`](research/opt-in-gate.md) F1, F2).
- `languageSettings.optIn` on `commonMain` alone is inert for the `jvm` and the `js` compile in a
  single-target module; the leaf (`jvmMain`, `jsMain`) line does the work. `kotlin { compilerOptions {
  optIn } }`, a target-level option and `sourceSets.all { }` reach the test compilations ([`opt-in-gate.md`](research/opt-in-gate.md)
  F2, F3, F5).
- With the marker's message as one string literal, the module's own error carries it
  ([`opt-in-gate.md`](research/opt-in-gate.md) F10). `@SubclassOptInRequired` is in the class files
  (`RuntimeInvisibleAnnotations`) and not in the ABI dump (F6).
- `js { nodejs { testTask { enabled = false } } }` downloads Node.js and Yarn during `build`, runs
  `kotlinNpmInstall` and writes `kotlin-js-store/`, and gives the root project the base plugin and about
  18 tasks. `js()` with no environment does none of that and costs one "JS Environment Not Selected"
  warning per configuration-cache miss, still a warning in KGP 2.4.21
  ([`common-check-plugin.md`](research/common-check-plugin.md) F2–F5).
- OkHttp 5.5.0's `HttpUrl`: the parse order, host canonicalisation, the IPv6 decoder's
  `ArrayIndexOutOfBoundsException` on `[1:2:3:4:5:6:7::8]`, the five trimmed characters, the silent
  deletion of TAB/LF/FF/CR in a path, the `+80` port, `xn--abc-` becoming `abc`
  ([`url-parsing.md`](research/url-parsing.md) F1–F10).
- OkHttp 5.5.0's `Headers`/`Request.Builder` accept `0x21..0x7E` names and `\t`/`0x20..0x7E` values,
  echo a rejected value into the exception text, write the method token raw, and hand a transport
  statuses from `000` to `999` ([`header-and-method-validation.md`](research/header-and-method-validation.md)
  F4–F9).
- `okhttp-jvm` 5.5.0 requires `kotlin-stdlib` 2.2.21 ([architecture §2](../architecture.md)).

**Blast radius.** The unit adds new trees (`webtools-http/`, `webtools-http-commoncheck/`) and a
plugin file. Nothing in the three existing modules references them. The shared files it edits are
`settings.gradle.kts`, the comment of `build.gradle.kts`, `README.md` and four files under
`build-logic/src/test` (§7). `git log` of these areas shows only #61 and #47 and the research commits.

**Adoption verdicts carried from the architecture (§8).**

| Site | Verdict | In this plan |
|---|---|---|
| `ResultExtensions.kt` (two identical `internal inline flatMap` copies) | a third copy in `webtools-http` `commonMain`; the legacy files are not edited | `ResultExtensions.kt` (§7.5) |
| `logback-test.xml` (three byte-identical copies) | one more in `webtools-http/src/jvmTest/resources` | §7.6 |
| `ReliableSendFailure`'s open hierarchy and `README.md:82-88` | followed, not changed | the failure model (§6.3) |
| `LinkQualityTracker`'s `nanoClock` | not changed; the stack uses the named `WallClock` | no file |
| `README.md`, `settings.gradle.kts`, root `build.gradle.kts` | in scope now, distributed | §7.1, §8 |

---

## 3. Assumptions

- **The accessors `commonMain {}`, `jvmMain {}` and `jsMain {}` resolve inside `kotlin { sourceSets { … } }`
  in a module build file** whose plugin comes from the included build. The research probe compiled
  exactly this layout ([`opt-in-gate.md`](research/opt-in-gate.md) F5). If one fails to resolve, use
  `getByName("jsMain")` (and likewise) with the same body and record the spelling as a deviation.
- **The executor's JDK is 17 to 26** (Gradle 9.7.1's range) and the Kotlin 2.2.0 compiler accepts
  `-Xjdk-release=11` on it, as `verifyJavaFloor` already shows for the existing modules.
- **The research probes' results transfer to the real convention plugin.** The probe used a simplified
  plugin (jvm target, test dependencies); nothing in the real plugin touches opt-ins. The compile proof
  (P3) is what confirms it. If the real plugin changed an outcome, the proof shows it before anything
  else is built.
- **The dump's line endings cannot fail `apiCheck`.** `binary-compatibility-validator` 0.18.2 compares
  the committed and the generated dump line by line ("We don't compare a full text because newlines on
  Windows & Linux/macOS are different", `KotlinApiCompareTask.compareFiles`, tag `0.18.2`), and git
  stores the file with LF whatever the working tree holds (`core.autocrlf=true`). No `.gitattributes`
  rule is needed for `*.api`.
- **OkHttp's `HttpUrl` is stable across 5.5.x** for the files the differential reads (identical at
  5.5.1 per the research). A future change shows up as a differential failure.
- **`kotlin-test-junit5` maps `kotlin.test.Test` to JUnit 5 for `commonTest` classes**, so the level
  tasks (which scan the `jvm` test compilation) run them. The existing sample in
  `SampleProject.multiplatformJvm` and `LevelSelectionIntegrationTest` already rely on this.

## 4. Non-goals

- Every default implementation and policy: `OkHttpTransport`, the URL policies, `CaffeineResponseCache`,
  the call budget, the facades, the fixtures (units 4 to 9); `webtools-feeds` (unit 10).
- Internationalised host names, a public `Url` builder, query decoding or a query-pair model, a
  `withQuery`, URL joining.
- A permanent guard or compile-fail test of the opt-in gate (the research's `verifyTestsAreConsumers`
  task, and the TestKit compile-fail snippet) — left as a follow-up (§13).
- Refusing transport-owned header names (`Host`, `Content-Length`, `User-Agent`, `Connection`,
  `Transfer-Encoding`) in `HttpRequest`: the transport's silent drop is unit 6's to settle.
- A `Log` call site, a logging backend for `js`, MockK, `kotlinx-coroutines-test`.
- Running `commonTest` on `js`; the check module compiles `commonMain` only.
- Editing `architecture.md`, `docs/dashboards/` or the legacy `ResultExtensions.kt` files.
- Publishing: `webtools-http` 1.0.0 is not released, signed or tagged by this unit.

---

## 5. Approach and staging

The module's whole vocabulary lands in one change, in an order that puts the unproven mechanism
first: the build-logic plugin (the check module needs it), then the module skeleton with every
declaration the gate touches and the **compile proof**, and only then the large pieces (`Url`, the
tests, the differential).

```mermaid
flowchart TB
    subgraph BL["build-logic (included build)"]
        MP["webtools.kotlin-multiplatform-library (unchanged)"]
        CC["webtools.kotlin-common-check (NEW)"]
        TK["TestKit: SampleProject.commonCheck + 2 classes (NEW)"]
    end
    MP --> H[":webtools-http — jvm(), explicitApi()"]
    CC --> HC[":webtools-http-commoncheck — js(), unpublished"]
    SRC[["webtools-http/src/commonMain/kotlin<br/>marker · failures · value types · ports · Log + epochMillisNow (expect)"]]
    H --> SRC
    HC -. "kotlin.srcDir(../webtools-http/src/commonMain/kotlin)" .-> SRC
    JM["jvmMain: Log.jvm.kt (slf4j) · EpochMillis.jvm.kt"] --> H
    JS["jsMain: JsActuals.kt (no-op Log · Date.now)"] --> HC
    TK -. "tests the plugin" .-> CC
```

| Stage | Content | Verified by | Lands in commit |
|---|---|---|---|
| S1 | `webtools.kotlin-common-check`, `SampleProject.commonCheck`, the two TestKit classes, the gating-list edit | P1, P2 | 1 |
| S2 | `settings.gradle.kts`, both module build files, the marker, the ports, the failure model, the reasons, the logging seam and helpers, the value types other than `Url`, a `Url` shell with the final members, the `js` actuals, the permanent Level 1 consumer classes, then **the compile proof** | P3 | 2 |
| S3 | `Url`, the encoders, the corpus, the generator and the `Url` tests | P5 (`commonTest` levels) | 2 |
| S4 | the remaining Level 2, 4a and 4c tests, the three differentials, the `logback-test.xml` | P4, P5 | 2 |
| S5 | the ABI dump, `javap`, published metadata, the root and unaffected-module proofs, the mechanical greps | P6–P9 | 2 |
| S6 | the README | `git diff` review | 3 |
| S7 | the Level 5 pass, the as-built record and `impact.md` | P10 | 4 |

S2's `Url` shell has the five public members and `parse`, with `TODO("S3")` bodies; it exists only so
`HttpRequest`, `HttpResponse`, `RedirectHop` and `HttpTransport` compile for the proof, and it is
replaced in S3 before anything is committed. Nothing in S2 reaches a commit in that state.

---

## 6. Design

### 6.1 Mechanism per type (architecture §6.9, for this unit's types)

| Type | Mechanism | Tier | Equality |
|---|---|---|---|
| `HttpTransport`, `WallClock` | `interface` + `@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)` | Experimental (implement); calling and passing are Stable Core | — |
| `WallClock.System` | nested `public object` implementing `WallClock`, over `internal expect fun epochMillisNow()` | Stable Core; usable without the opt-in | identity |
| `HttpFailure`, `TransportFailure`, `UpstreamFailure` | `abstract class` + `@SubclassOptInRequired`, `protected` constructors | Experimental (subclass) | identity |
| every failure leaf | `final` class nested in its group | Stable Core | identity |
| `RefusalReason`, `ExhaustedReason` | `final` class, `private` constructor, constants in the companion, no `of()` | Stable Core; may gain constants in a feature release | by `name` |
| `HttpMethod` | `final` class, `private` constructor, `of(name): Result` | Stable Core | by `name` |
| `Url` | `final` class, `private` constructor, `parse(text): Result` | Stable Core | canonical text |
| `HttpHeaders` (`Entry`, `Builder`) | `final` class, `internal` constructor, factory `HttpHeaders { }` | Stable Core | ordered entries |
| `RequestBody`, `RedirectHop` | `final` class, public frozen-shape constructor | Stable Core | identity / by fields |
| `HttpRequest`, `HttpResponse` (each with a `Builder`) | `final` class, `internal` constructor, factory | Stable Core | identity |
| `Log`, `logger`, `epochMillisNow`, `HttpSyntax`, `flatMap`, the non-fatal helper, the `Url` internals | `internal` | none | — |

Nothing here is `expect class`. The two `expect` declarations are `internal expect fun`s.

### 6.2 The gate, and the compile proof that opens the implementation

**Wiring.** In each module's own build file, and in no convention plugin:

| Module | Lines | What reaches the compiler |
|---|---|---|
| `webtools-http` | `commonMain { languageSettings.optIn(gateMarker) }`, `jvmMain { languageSettings.optIn(gateMarker) }` | the `jvm` main compile gets the `jvmMain` line; the `commonMain` line keeps the pair valid when a target is added |
| `webtools-http-commoncheck` | `commonMain { … }`, `jsMain { … }` | the `js` compile gets the `jsMain` line |

`gateMarker` is the string `"com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi"`.
The option does not belong in the convention plugin: a module that cannot see the marker prints
`w: Opt-in requirement marker … is unresolved` on every compile. The three forms the build files warn
against (`kotlin { compilerOptions { optIn } }`, the target-level option, `sourceSets.all { }`) reach
the test compilations and turn the tests into a non-consumer.

**What the committed code needs.** A class lexically inside a gated declaration compiles without the
opt-in, and a group that carries its own `@SubclassOptInRequired` does too. All implementers this unit
commits are nested leaves, nested `WallClock.System` or such groups, so the committed code compiles
with the wiring **absent**. Only a top-level or platform-source-set implementer needs the wiring; the
first one in the repository is whichever of units 6, 7 and 8 lands first (`OkHttpTransport`,
`CaffeineResponseCache`, `WindowedCallBudget`). A proof that builds only the committed code
therefore passes vacuously, which is why P3 step 2 adds three throwaway implementers.

**Declarations the proof compiles** (all real, all final, in S2):
- the marker; `WallClock` with nested `System`; `HttpTransport` with a default `close()`;
- `HttpFailure`, `TransportFailure`, `UpstreamFailure` and every leaf;
- a default that names a gated type: the permanent consumer classes use
  `fun now(clock: WallClock = WallClock.System)` and `class UsesClock(val clock: WallClock = WallClock.System)`;
- `internal expect fun epochMillisNow()` with its two actuals.

**The proof** is procedure P3. Its outcome is recorded in `final-implementation.md` §3 before S3
begins; a result that differs from the expectation is a deviation, and stops S3 until the cause is
understood.

**Diagnostics.** Cross-module (the test source sets, which read the compiled marker) the text of all
eight negative cases is:

```text
This class or interface requires opt-in to be implemented: Implementing or substituting this WebTools seam is Experimental and may change in a minor release. Using the supplied implementations needs no opt-in. Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).
```

In-module the compiler prints the same sentence when the marker's `message` is one string literal
(research F10); the canary in P3 step 2 records what it prints.

**What the dump and the class files show.** `@SubclassOptInRequired` has `BINARY` retention: it is in
the class files (`RuntimeInvisibleAnnotations`) of `WallClock`, `HttpTransport`, `HttpFailure`,
`TransportFailure` and `UpstreamFailure`, and absent from `WallClock$System` and the leaves. It is not
in the ABI dump, so `apiCheck` would pass if it were dropped from a port; the permanent Level 1 classes
catch a dropped gate on no type (they only compile as a consumer), and the compile-fail follow-up
(§13) closes that gap. Under `explicitApi()` every interface member is written `public`; every
`override` writes its return type and no visibility.

### 6.3 The failure model

```mermaid
classDiagram
    class Exception
    class HttpFailure {
        <<abstract>>
        #HttpFailure(message)
        #HttpFailure(message, cause)
    }
    class TransportFailure {
        <<abstract>>
    }
    class UpstreamFailure {
        <<abstract>>
        +status Int
    }
    Exception <|-- HttpFailure
    HttpFailure <|-- TransportFailure
    HttpFailure <|-- UpstreamFailure
    HttpFailure <|-- Closed
    HttpFailure <|-- BudgetExhausted
    HttpFailure <|-- DecodeFailure
    TransportFailure <|-- Timeout
    TransportFailure <|-- ConnectFailed
    TransportFailure <|-- DnsFailed
    TransportFailure <|-- TlsFailed
    TransportFailure <|-- Protocol
    TransportFailure <|-- Io
    TransportFailure <|-- InvalidRequest
    TransportFailure <|-- RefusedByPolicy
    TransportFailure <|-- BodyTooLarge
    TransportFailure <|-- TooManyRedirects
    TransportFailure <|-- RetryRefused
    UpstreamFailure <|-- RateLimited
    UpstreamFailure <|-- UpstreamError
```

Every leaf is a `public class` nested in its group (`HttpFailure.Closed`, `TransportFailure.Timeout`,
`UpstreamFailure.RateLimited`, …), so the short names `Timeout`, `Protocol` and `Io` never enter the
flat package, where a top-level class would silently shadow a star-imported OkHttp or Okio name.

| Leaf | Public constructors | Fields | `require` at construction | Message |
|---|---|---|---|---|
| `HttpFailure.Closed` | `(component: String)` | `component` | non-blank | `The <component> is closed` |
| `HttpFailure.BudgetExhausted` | `(wait: Duration, reason: ExhaustedReason, window: String?)` | `wait`, `reason`, `window` | `wait` not negative; `window` null or non-blank | `The call budget is exhausted: <reason.name>, wait <wait>` plus `, window <window>` when present |
| `HttpFailure.DecodeFailure` | `(cause: Throwable?)`, `()` | — | — | `The response body could not be decoded` |
| `TransportFailure.Timeout` | `(cause: Throwable?)`, `()` | — | — | `The request timed out` |
| `TransportFailure.ConnectFailed` | `(cause: Throwable?)`, `()` | — | — | `The connection could not be established` |
| `TransportFailure.DnsFailed` | `(host: String, cause: Throwable?)`, `(host: String)` | `host` | `host` non-blank | `The host name could not be resolved: <host>` |
| `TransportFailure.TlsFailed` | `(cause: Throwable?)`, `()` | — | — | `The TLS handshake failed` |
| `TransportFailure.Protocol` | `(cause: Throwable?)`, `()` | — | — | `The server's response broke the HTTP protocol` |
| `TransportFailure.Io` | `(cause: Throwable?)`, `()` | — | — | `An I/O error interrupted the exchange` |
| `TransportFailure.InvalidRequest` | `(rule: String, cause: Throwable?)`, `(rule: String)` | `rule` | `rule` non-blank | `The request is invalid: <rule>` |
| `TransportFailure.RefusedByPolicy` | `(reason: RefusalReason, cause: Throwable?)`, `(reason: RefusalReason)` | `reason` | — | `The request was refused by the URL policy: <reason.name>` |
| `TransportFailure.BodyTooLarge` | `(limit: Long, declared: Long?, cause: Throwable?)`, `(limit: Long, declared: Long?)` | `limit`, `declared` | `limit >= 0`; `declared` null or `>= 0` | `The response body is larger than the limit of <limit> bytes` plus ` (declared <declared> bytes)` when present |
| `TransportFailure.TooManyRedirects` | `(limit: Int, cause: Throwable?)`, `(limit: Int)` | `limit` | `limit >= 0` | `The request was redirected more than <limit> times` |
| `TransportFailure.RetryRefused` | `(cause: Throwable?)`, `()` | — | — | `The transport refused to send the request a second time` |
| `UpstreamFailure.RateLimited` | `internal (status: Int, retryAfter: Duration)` | `status`, `retryAfter` | status in 100..599; `retryAfter` not negative | `The server rate-limited the call (status <status>); retry in <retryAfter>` |
| `UpstreamFailure.UpstreamError` | `(status: Int, headers: HttpHeaders, body: ByteArray)` | `status`, `headers`, `bodySize`, `body()` | status in 100..599 | `The server answered with status <status>` |

- The group constructors are `protected`: `HttpFailure(message)` and `HttpFailure(message, cause)`,
  `TransportFailure` the same two, and `UpstreamFailure(status, message)` with
  `require(status in 100..599) { "Status code must be in 100..599" }`.
- A `Timeout`/`ConnectFailed`/… with no cause uses the cause-less overload; this is the explicit-overload
  form of "no defaulted parameters". The primary constructor is the one with the most parameters.
- Every message above is built only from the type's constant text and its non-sensitive fields. The
  `cause` is stored for diagnostics and is not part of the message; a producer decides what it attaches
  (§12, unit 6). `DnsFailed.host` and `InvalidRequest.rule` are the only free-text fields: the first is
  a host name, the second is library-written and documented as never holding request data.
- `UpstreamError` copies `body` on the way in (`body.copyOf()`), returns a copy on the way out, and
  neither `message`, `toString()` nor any property prints a header value or body byte. `HttpHeaders` is
  immutable, so `headers` needs no copy.
- Failures are not designed for Java serialisation (`Duration`, `HttpHeaders` and `ByteArray` fields);
  the KDoc does not promise it.

`RefusalReason` and `ExhaustedReason`:

```kotlin
public class RefusalReason private constructor(public val name: String) {
    override fun equals(other: Any?): Boolean = other is RefusalReason && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name

    public companion object {
        public val Scheme: RefusalReason = RefusalReason("Scheme")
        public val UserInfo: RefusalReason = RefusalReason("UserInfo")
        public val HostForm: RefusalReason = RefusalReason("HostForm")
        public val ReservedName: RefusalReason = RefusalReason("ReservedName")
        public val DeniedAddress: RefusalReason = RefusalReason("DeniedAddress")
        public val Port: RefusalReason = RefusalReason("Port")
        public val HostNotPinned: RefusalReason = RefusalReason("HostNotPinned")
        public val InsecureRedirect: RefusalReason = RefusalReason("InsecureRedirect")
        public val SensitiveHeader: RefusalReason = RefusalReason("SensitiveHeader")
    }
}
```

`ExhaustedReason` is the same with `Window`, `Paused`, `CostExceedsLimit`, `QueueFull`. Both carry the
KDoc sentence: "This set may gain constants in a feature release; a `when` over it keeps an `else`."

### 6.4 The request and response value types

**`HttpSyntax`** (`internal object`, `commonMain`) holds every predicate, as `Char` comparisons with
no regex and no JVM API:

| Function | Definition |
|---|---|
| `isTokenChar(c)` | `A-Z a-z 0-9` or one of the 15 specials `!`, `#`, `$`, `%`, `&`, `'`, `*`, `+`, `-`, `.`, `^`, `_`, backtick, `\|`, `~` (77 characters) |
| `firstNonTokenIndex(text)` | index of the first non-`tchar`, or `-1` when every character is one (also `-1` for empty) |
| `isToken(text)` | non-empty and `firstNonTokenIndex(text) < 0` |
| `trimOws(value)` | remove leading and trailing SP and HTAB, nothing else |
| `isFieldValue(value)` | contains no U+000A, U+000D or U+0000 (applied after `trimOws`) |
| `isSendableValue(value)` | every character is U+0009 or in U+0020..U+007E |
| `sanitizeReceivedValue(raw)` | replace each CR, LF and NUL with SP, then `trimOws` |
| `asciiLower(c)` | `A-Z` to `a-z`, nothing else |
| `equalsName(a, b)` | same length and `asciiLower` equal at each index |
| `isCredentialName(name)` | `equalsName` with `Authorization`, `Proxy-Authorization`, `Cookie` or `Set-Cookie` |
| `mediaTypeProblemIndex(text)` | `-1` when `text` is an RFC 9110 media type (grammar below), else the index at which it stops being one |

Kotlin's `lowercase()` and `equals(ignoreCase = true)` fold U+212A (Kelvin) to `k` and U+0131 to `i`,
so protocol text never goes through them.

Media-type grammar (visible-ASCII subset, no leading or trailing whitespace):
`token "/" token { OWS ";" OWS [ token "=" ( token | quoted ) ] }`, `quoted` being `"` followed by any of
HTAB, SP, `0x21`, `0x23..0x5B`, `0x5D..0x7E` or `\` + (HTAB, SP or `0x21..0x7E`), then `"`. The failing
index is where the scan stopped: the offending character; the whitespace start for trailing whitespace
before the end; the text length when the text ends too early. A media type is always a sendable value.

**`HttpHeaders`**
- `get(name): String?` — the last value of the name, or `null`; `getAll(name): List<String>` — every
  value in insertion order, empty when absent; `contains(name)`, `size`, `isEmpty()`,
  `entries(): List<Entry>`. A lookup never throws, whatever the name.
- `Entry(name, value, isSensitive)`: `name` keeps its spelling; `isSensitive` is the flag or
  `HttpSyntax.isCredentialName(name)`. `Entry` equality: name spelling, value, `isSensitive`.
- `Builder.add`, `addSensitive`, `set`, `setSensitive`, `remove`, `addAll(headers)`. `set` removes every
  entry of the name and appends one at the end, flagged if any removed entry was flagged; the name
  spelling is the new one. A failing call changes nothing. `addAll` copies entries with their flags.
- Validation per `add`/`addSensitive`/`set`/`setSensitive`: name empty, name not a token, value fails
  the container rule — in that order — each an `IllegalArgumentException` with the message of the table
  below. Values are stored after `trimOws`.
- `toString()`: `HttpHeaders(Accept, Authorization*, X-Api-Key*)` — names in order, `*` after each
  sensitive entry, no values. `Entry.toString()`: `Entry(Accept)` or `Entry(X-Api-Key, sensitive)`.
- KDoc obligation for transports (the receiving side): an entry whose name is not a token is dropped,
  a value is passed through `HttpSyntax.sanitizeReceivedValue`, and everything else is kept in order.

**`HttpMethod`**: `name`; constants `Get`, `Head`, `Post`, `Put`, `Patch`, `Delete`, `Options`;
`of(name): Result<HttpMethod>` — `HttpSyntax.isToken(name)`, exact (case-sensitive), the seven exact
names return the constants; `permitsBody` (`internal`): `name` is not `GET`, `HEAD` or `TRACE`.
`equals`/`hashCode` by name; `toString()` is the name. `of("CONNECT")` and `of("TRACE")` succeed.

**`RequestBody(bytes: ByteArray, contentType: String?)`**: copies `bytes`; `size`; `bytes(): ByteArray`
(a copy); `contentType` is `null` or an RFC 9110 media type. Identity equality. `toString()`:
`RequestBody(size=12, hasContentType=true)`.

**`HttpRequest`**: `method`, `url`, `headers`, `body: RequestBody?`. Factory
`HttpRequest(method, url) { headers = …; body = … }`. Checks, in this order, each an
`IllegalArgumentException`: the method is not `CONNECT`; every header value is sendable; a body is not
set on a method that does not permit one; a body with a content type is not combined with a
`Content-Type` header. `toString()`: `HttpRequest(method=GET, url=https://h/p?..., headers=HttpHeaders(...), body=none)`
with `url.redacted()` and `body` as `RequestBody.toString()` or `none`.

**`HttpResponse`**: `status` (100..599), `url` (the URL the response came from), `headers`,
`redirectChain: List<RedirectHop>` (a copy; empty unless redirects were followed), `bodySize`, `body()`
(a fresh copy on every call). Factory `HttpResponse(status, url) { headers = …; redirectChain = …; body(bytes) }`.
`toString()`: `HttpResponse(status=200, url=…redacted…, headers=HttpHeaders(...), bodySize=N, redirects=K)`.

**`RedirectHop(url: Url, status: Int)`**: the URL that was **requested** and the `3xx` status it answered
with. A chain `[(A, 301), (B, 302)]` with a response whose `url` is `C` means A answered 301, B answered
302 and C answered the response. Value equality; `toString()` uses `url.redacted()`.

**Messages** (`IllegalArgumentException` unless said otherwise). A header name is echoed only after it
passed the token rule; a value never.

| Rule | Message |
|---|---|
| name empty | `Header name must not be empty` |
| name not a token | `Header name is not an RFC 9110 token (first offending character at index <i>)` |
| container value | `Value of header '<name>' must not contain CR, LF or NUL` |
| sendable value | `Value of header '<name>' must be visible ASCII, space or tab to be sent` |
| `CONNECT` | `A CONNECT request is not supported` |
| body on a body-less method | `A <METHOD> request must not have a body` (`GET`, `HEAD` or `TRACE`) |
| content type | `Content type is not an RFC 9110 media type (first offending character at index <i>)` |
| content-type conflict | `A request with a body content type must not also set a Content-Type header` |
| status | `Status code must be in 100..599` |
| redirect status | `Redirect status must be in 300..399` |
| method token (`Result.failure`) | `Method is not an RFC 9110 token` |

### 6.5 `Url`

```mermaid
flowchart TD
    A["text"] --> T["1 trim TAB LF FF CR SP at both ends<br/>nothing left: Empty"]
    T --> S["2 scan once, left to right<br/>backslash: Backslash · space/control/DEL/C1/White_Space: WhitespaceOrControl<br/>unpaired surrogate: NotUnicode"]
    S --> SC["3 scheme: https:// or http://, ASCII case-insensitive by hand<br/>else Scheme"]
    SC --> AU["4 authority = up to the first slash, question mark or hash; an at-sign in it: UserInfo"]
    AU --> HP["5 split host and port ([...] or first colon)"]
    HP --> P["6 port: digits only, 1..65535, empty = default<br/>else Port"]
    P --> H["7 host: IPv6 literal or ASCII name<br/>HostEncoding · Host · HostIdn"]
    H --> R["8 drop the fragment (from the first hash); the first question mark starts the query"]
    R --> PA["9 path: empty = /; per-segment encode set; fold . and .."]
    PA --> Q["10 query: encode set, kept as typed"]
    Q --> U["11 Url(scheme, host, port, path, query)"]
```

The steps run in this order, and the first failing step decides the kind. Each step is a small pure
function returning a value or a `Result`, joined with the internal `flatMap`; none throws.

| Kind | Constant message | Raised when |
|---|---|---|
| `Empty` | `The URL is empty.` | nothing is left after trimming |
| `Backslash` | `The URL contains a backslash.` | `\` anywhere (first offending character of step 2) |
| `WhitespaceOrControl` | `The URL contains whitespace or a control character.` | a character `<= U+0020`, `U+007F..U+00A0`, `U+1680`, `U+2000..U+200A`, `U+2028`, `U+2029`, `U+202F`, `U+205F` or `U+3000` anywhere in the trimmed text |
| `NotUnicode` | `The URL contains text that is not valid Unicode.` | an unpaired surrogate |
| `Scheme` | `The URL must begin with http:// or https://.` | any other scheme, none, or a separator other than `://` |
| `UserInfo` | `The URL contains user-info.` | `@` in the authority |
| `HostEncoding` | `The URL host must be plain ASCII, without percent-encoding.` | `%` or a code point `>= U+0080` in the host |
| `HostIdn` | `The URL host contains an internationalised (xn--) label.` | a label starting `xn--` |
| `Host` | `The URL host is not a valid host name or IP address literal.` | empty host, a character outside `a-z 0-9 - _ .`, a label outside 1..63, the host outside 1..253 (the trailing dot counts), a bad IPv6 literal |
| `Port` | `The URL port must be a number from 1 to 65535.` | a non-digit (sign included), `0`, above 65535 |
| `DotSegment` | `A path segment must not be "." or "..".` | encoders only |

The encoders also use `NotUnicode`. No message contains any part of the input.

**Canonical form.**
- Scheme and host lower-case (ASCII letters folded by hand); IPv6 in RFC 5952 form; an IPv4-mapped
  address (ten zero bytes then `ffff`) becomes dotted IPv4; digits-and-dots hosts and one trailing dot
  are kept as written; `host` has no brackets, and `toString()` adds them when the host contains `:`.
- `port` is the effective port; `toString()` omits it when it is the scheme's default (80, 443).
- An empty path is `/`; `.`, `%2e` and the double-dot forms `..`, `%2e.`, `.%2e`, `%2e%2e` (ASCII
  case-insensitive) are resolved with `HttpUrl`'s stack (the stack starts as `[""]`; for segment *k* of
  *n*, `trailing = k < n-1`): a dot adds nothing; a double-dot removes the last element, then replaces
  the new last with `""` when the removed one was empty and elements remain, else appends `""`; any
  other segment replaces an empty last element or is appended, then appends `""` when `trailing`. Empty
  segments are kept; the stack never pops above the root.
- Percent-encoding is UTF-8 in upper-case hex. An existing `%XX` keeps its case and is never decoded; a
  malformed `%` is kept. The sets applied at parse time (the scan has already removed controls, space,
  DEL and backslash): path segment `" < > ^ ` { | }` and every non-ASCII code point; query `" ' < >`
  and every non-ASCII code point; the fragment is dropped.
- `encodedQuery` is `null` without a `?` and `""` for a bare `?`; `+` stays `+`; the query is not split
  into pairs.
- `toString()` is `scheme://host[:port]path[?query]`. `parse(toString())` yields an equal `Url`.
  `redacted()` is `toString()` with `?...` in place of `?` and the query, present exactly when
  `encodedQuery != null`; it is for logs and is not meant to be parsed back.

**Host rules for ASCII text.** `%` or `>= U+0080` gives `HostEncoding`; lower-case; every character in
`a-z 0-9 - _ .` (else `Host`); length 1..253; one trailing dot dropped before the label check; each
label 1..63 (an empty label is `Host`); then a label starting `xn--` is `HostIdn`.

**IPv6** is decoded over split parts, independently of OkHttp's loop, so it cannot inherit the bounds
bug: the text inside `[ ]` contains `:`; find the first `::`, a second `::` (also `:::`) fails; split the
text before and after it on `:` (empty text gives no parts); every part is 1 to 4 hex digits except the
last part of the address, which may be a dotted IPv4 quad (four parts, 1 to 3 digits, no leading zero
unless `0`, each at most 255) and counts as two groups; without `::` exactly 8 groups, with it at most
7; zero groups fill the gap.

**The encoders** (`internal`, `UrlEncoding.kt`):
- `encodePathSegment(segment): Result<String>` — `"."` and `".."` fail with `DotSegment`; an unpaired
  surrogate fails with `NotUnicode`; otherwise everything except `A-Za-z0-9` and
  `` ! $ & ' ( ) * + , - . : ; = @ [ ] _ ~ `` is percent-encoded (controls, DEL, space,
  `" # % / < > ? \ ^ ` { | }`, non-ASCII as UTF-8). `%2e` becomes `%252e`, which is harmless.
- `encodeQueryComponent(component): Result<String>` — an unpaired surrogate fails with `NotUnicode`;
  otherwise everything except `A-Za-z0-9 * - . _` is percent-encoded (space `%20`, `+` `%2B`, `%` `%25`,
  `~` `%7E`).
- **Assembling a URL from parts** is: take the base `Url`'s `toString()` without its query, append `/`
  and each encoded segment (exactly one separator), append `?` and the `name=value` pairs joined with
  `&`, and pass the text to `Url.parse`. One parser and one canonical form decide the result. Empty
  segments differ between text assembly (`x//y`) and `HttpUrl.Builder` (`x/y`): the facade treats an
  empty segment as an `InvalidRequest` (unit 9).

### 6.6 Ports, the logging seam and the helpers

**`HttpTransport`** — the KDoc states the contract an implementer (a consumer's transport or a fake)
meets:
1. `execute` sends exactly one request per hop; it never retries, and it does not follow a redirect
   unless the implementation documents that it does and records each hop in `redirectChain`.
2. It enforces the implementation's documented caps (body size, redirects, timeouts) and reports a
   breach as the matching `TransportFailure` (`BodyTooLarge`, `TooManyRedirects`, `Timeout`).
3. A `304` is a response, never a failure. Any status in 100..599 is an `HttpResponse`; a status outside
   that range is `TransportFailure.Protocol`.
4. Response entries the types cannot hold are handled, not failed: an entry whose name is not a token is
   dropped; CR, LF and NUL in a value are replaced with SP (`HttpSyntax.sanitizeReceivedValue`).
5. Cancellation is thrown (`CancellationException`), never wrapped in a `Result`.
6. After `close()`, `execute` returns `Result.failure(HttpFailure.Closed("transport"))`. `close()` is
   idempotent and does not throw.
7. `execute` may be called concurrently from many coroutines.

**`WallClock`** — `nowEpochMillis()` is the wall-clock time in milliseconds since the Unix epoch (UTC);
it may step backwards when the system clock is adjusted; implementations must be safe for concurrent
calls and cheap. `WallClock.System` reads `epochMillisNow()`.

**`Log`** (`internal`) — `debug`, `info`, `warn` take a message with `{}` placeholders and
`vararg args: Any?`; a trailing `Throwable` argument is the exception, as slf4j reads it. `logger(name)`
returns a `Log`; the JVM actual wraps `LoggerFactory.getLogger(name)`; the check module's actual is a
no-op.

**The helpers**
- `Result<T>.flatMap(transform: (T) -> Result<R>): Result<R>` — `internal inline`, the body of
  `webtools-udp`'s copy.
- `catchNonFatal(block: () -> T): Result<T>` — `internal inline`; success wraps the value; an
  `Exception` other than `CancellationException` becomes `Result.failure`; `CancellationException` is
  rethrown; an `Error` is not caught. It is the one `try`/`catch` in the module, because rethrowing
  selectively has no functional form, and, being inline, it surrounds suspend calls safely.

### 6.7 The check-only `js` module

`webtools-http-commoncheck` applies `webtools.kotlin-common-check`, which gives it a `js` target with no
environment and a `check` that compiles. It takes the sibling's source as a directory (a relative path:
no `project(...)` access, so the configuration cache needs nothing from another project) and adds the
two `actual`s. Consequences, all measured in the research:

| Command (from the root) | Effect on the check module |
|---|---|
| `./gradlew build` | `compileKotlinJs`, `jsJar` (a `.klib`), `allMetadataJar`; no test task has sources |
| `./gradlew check` | `compileKotlinJs` (the plugin wires it) |
| `./gradlew test`, the level tasks | no check-module task |
| `./gradlew clean` | `clean` of the module's `build/` |
| root `tasks` | unchanged; the root gains no plugin, extension or task |

Cost accepted: one `JS Environment Not Selected` warning per configuration-cache miss, and one Gradle-10
`archives` deprecation (KT-78620) that every `jvm()` module already prints. Builds are never run with
`--warning-mode=fail`. If a KGP release turns the missing environment into an error, the plugin's
TestKit `build` test fails, which is the alarm; the fallback is
`nodejs { testTask { enabled = false; setDependsOn(emptyList<Any>()) } }`, which downloads nothing but
gives the root project the base plugin and about 18 tasks.

A mistyped sibling path compiles nothing and reports `NO-SOURCE`, not a failure, unless the module has
an `actual`; `JsActuals.kt` always has two, whose `expect`s would then be missing, so the module fails
loudly.

### 6.8 Error handling of the new signatures

| Signature | Failure it reports | Expected (→ `Result`) or programmer error (→ throw) | Unwrapped by |
|---|---|---|---|
| `Url.parse(text): Result<Url>` | `IllegalArgumentException` (an `internal` `UrlParseException` with a `UrlProblem`) | expected: text from outside the process | the caller; the facades map it to `InvalidRequest` |
| `encodePathSegment`, `encodeQueryComponent: Result<String>` | `UrlParseException` (`DotSegment`, `NotUnicode`) | expected: a segment or component from a caller | unit 9's request builder |
| `HttpMethod.of(name): Result<HttpMethod>` | `IllegalArgumentException("Method is not an RFC 9110 token")` | expected | the caller |
| `HttpHeaders { }`, `Builder.add`/`addSensitive`/`set`/`setSensitive` | `IllegalArgumentException` | programmer error: developer-written names and values | not unwrapped; fix the code |
| `RequestBody(...)`, `HttpRequest(...) { }`, `HttpResponse(...) { }`, `RedirectHop(...)` | `IllegalArgumentException` | programmer error | not unwrapped |
| every failure-leaf constructor | `IllegalArgumentException` for a negative `limit`, blank name, status outside 100..599 | programmer error | not unwrapped |
| `HttpTransport.execute(request): Result<HttpResponse>` | any `HttpFailure` (`TransportFailure` leaves, `Closed`) | expected | the facade (unit 9); `CancellationException` is thrown, never in the `Result` |
| `WallClock.nowEpochMillis(): Long` | none | — | — |
| `catchNonFatal`, `flatMap` | `Result.failure(e)` for an `Exception`; `CancellationException` and `Error` propagate | expected / not caught | the caller |
| `logger(name)`, `Log.*` | none; slf4j never throws on a log call | — | — |
| `Entry`, `HttpHeaders.get`/`getAll`/`contains`, `Url` accessors | none (lookups return `null` or empty) | — | — |

The KDoc documents the constructor-time `IllegalArgumentException`s above with `@throws`.

### 6.9 Logging

This unit has **no production logging site**. It delivers the seam and its contract for the later
units: levels `INFO` on create/close of a long-lived object, `WARN` on a pause, hard stop, store
failure, policy refusal, `RetryRefused` and an upstream throttle, `DEBUG` one line per call, redirect
hop and budget refusal; the "never" list — headers, bodies, query strings, credentials, resolved
addresses, cache-key values, OkHttp objects, and any `Url` except through `redacted()` (architecture
§6.6). The seam's own test (Level 2) pins the slf4j mapping: logger name, level, `{}` substitution and a
trailing `Throwable`. `jvmTest` loads `logback-test.xml` (root `INFO`, console); the seam test attaches
a `ListAppender` and raises its own logger to `DEBUG`.

---

## 7. File-by-file changes

Paths are repository-relative. `<main>` is `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http`,
`<jvm>` is `webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http`. Every `.kt` and `.kts` file
is LF (`.gitattributes`). Every public declaration gets KDoc in the pattern §8 lists; the blocks below
show the contract text where it is not obvious and otherwise stand for the KDoc §8 requires.

### 7.1 `settings.gradle.kts` and `build.gradle.kts` (changed)

`settings.gradle.kts:11` becomes:

```kotlin
include(":webtools-udp", ":webtools-scraping", ":webtools-browser", ":webtools-http", ":webtools-http-commoncheck")
```

`build.gradle.kts:1-9` (a comment; `:10-13` do not change) becomes:

```kotlin
// WebTools is published as independent, single-concern artifacts (webtools-udp, webtools-scraping,
// webtools-browser, webtools-http). Everything they share - the Kotlin/JVM or Kotlin Multiplatform
// setup, the 5-level test-task layout (see the README's "Build & test" section) and the Maven Central
// publishing skeleton - lives in the included build-logic build as convention plugins
// (webtools.kotlin-jvm-library, webtools.kotlin-multiplatform-library, webtools.published-library, and
// webtools.kotlin-common-check for the unpublished check-only js modules).
// Each module's own build.gradle.kts applies one of them and adds its dependencies, its coordinates,
// its POM name and description, and any configuration that belongs to it alone (for example
// webtools-udp's version and UDP-port lock); only the group and the default version every module
// shares are set here.
```

The root project gains no plugin, extension or task.

### 7.2 `build-logic` (one new plugin, three changed or new test files)

**`build-logic/src/main/kotlin/webtools.kotlin-common-check.gradle.kts` (new)**

```kotlin
// Convention plugin: an unpublished, check-only Kotlin Multiplatform module with a single js target.
// It compiles another module's commonMain directory for a second target, so that a JVM API in common
// code is a compile error (a module with only jvm() accepts java.* in commonMain) and so is an expect
// declaration without a js actual. It applies the multiplatform plugin and declares mavenCentral()
// itself; it does not apply webtools.published-library, so the module has no publishing plugin, no
// signing and no ABI dump, and nothing in it is published. It is not a library plugin either: no Java
// floor and no verifyJavaFloor, because a js target has no JVM class files.
// A check module adds, in its own build file:
// - the sibling's commonMain directory as a source directory of its commonMain, as a path relative to
//   the check module (no project(...) access, so the configuration cache needs nothing from another
//   project);
// - the dependencies of the sibling's commonMain;
// - the opt-in of the sibling's subclass gate, as languageSettings.optIn on its commonMain and on its
//   jsMain: the jsMain line is the one the js compile reads, and the commonMain line keeps the pair
//   valid if the module ever gains a target;
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
// leaves the root project alone.
// The cost is one "JS Environment Not Selected" warning per configuration-cache miss: the Kotlin Gradle
// plugin says the omission will be an error in a future release (it is still a warning in 2.4.21). The
// TestKit check of this plugin fails on that day. The fallback then is
//   js { nodejs { testTask { enabled = false; setDependsOn(emptyList<Any>()) } } }
// which downloads nothing but gives the root project the base plugin and about 18 tasks, among them its
// own build, check, assemble and clean.
kotlin {
    js()
}
//endregion

//region check
// compileKotlinJs belongs to assemble (through jsJar), and a module with no test source leaves check with
// nothing to run, so without this line check would pass without compiling anything.
tasks.named("check") {
    dependsOn("compileKotlinJs")
}
//endregion
```

**`build-logic/src/test/kotlin/com/spartanlabs/testing/support/webtools/buildlogic/SampleProject.kt` (changed)**
Three insertions, copied verbatim from [`research/common-check-plugin.md`](research/common-check-plugin.md)
"Appendix: the TestKit sources as run in the probe" (the block at lines 295–375), in LF:
1. the four constants `COMMON_CHECK_MODULE`, `COMMON_CHECK_SHARED`, `COMMON_CHECK_COMMON_PATH` and
   `COMMON_CHECK_ACTUAL_PATH` after `LEVELS_LINE_PREFIX` (`:74`), inside `//region constants`;
2. `fun commonCheck(rootProjectName: String): SampleProject` (with its KDoc) after
   `includedBuildComposite` (ends `:173`), inside `//region factories`;
3. `commonCheckSettings`, `COMMON_CHECK_BUILD_SCRIPT`, `COMMON_CHECK_COMMON` and `COMMON_CHECK_ACTUAL`
   after `UNLEVELED_TEST` (ends `:428`), inside `//region script templates`.

The research's note that the file uses CRLF does not apply: at `f72b115` the file is LF. The factory
returns a `SampleProject` and fails with `IllegalArgumentException` for a blank root project name, as
the other factories do. The sample's `settings.gradle.kts` declares no `dependencyResolutionManagement`,
so the plugin's own `mavenCentral()` is what resolves the Kotlin standard library.

**`build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/CommonCheckPluginIntegrationTest.kt` (new)**
The class at lines 379–527 of the same appendix, verbatim. It runs six nested builds in `@BeforeAll`
(`build`, `build --dry-run`, `check --dry-run`, `tasks --all` in one workspace; `compileKotlinJs` in a
workspace with a `Leak.kt` importing `java.io.File`; `compileKotlinJs` in one with an `Extra.kt` holding
an `expect fun extra(): Int` with no `actual`), all through `NestedBuild.run`, and asserts seven things:

| Test | Asserts |
|---|---|
| `build compiles the shared common code for js` | `:commoncheck:compileKotlinJs` and `:commoncheck:build` are `SUCCESS` |
| `check depends on the js compile` | `check --dry-run` lists a line starting `:commoncheck:compileKotlinJs ` |
| `a JVM API in the shared directory fails the js compile` | the task `FAILED`; the output names `Leak.kt` and contains `Unresolved reference` |
| `an expect declaration without a js actual fails the js compile` | the task `FAILED`; the output contains `has no actual declaration` |
| `build starts no Node, Yarn or npm task and writes no lock store` | no executed task path contains `NodeJs`, `Yarn`, `Npm` or `PackageJson`; no `kotlin-js-store/`; the dry run lists no `jsNodeTest` or `jsBrowserTest` |
| `the module has no publishing or signing task` | the task-name list is not empty, and none starts `publish`, `sign`, `generatePomFile` or `generateMetadataFile` |
| `the root project gains no task` | no dry-run line matches `^:[^:]+ ` |

The class does not assert that the "JS Environment Not Selected" warning is printed, so a release that
removes it does not break the test; one that makes it an error fails the `build` test.

**`build-logic/src/test/kotlin/com/spartanlabs/testing/nonfunctional/webtools/buildlogic/CommonCheckConfigurationCacheReuseNonFunctionalTest.kt` (new)**
The class at lines 531–574 of the appendix, verbatim: `build`, `build`; the second output contains
`Reusing configuration cache`.

**`build-logic/src/test/kotlin/com/spartanlabs/testing/gating/webtools/buildlogic/BuildLogicPluginsGatingTest.kt` (changed)**
`conventionPluginIds` (`:17-21`) gains `"webtools.kotlin-common-check",` as the fourth element, and the
class KDoc's "its three convention plugins" (`:11-12`) becomes "its four convention plugins".

Logging and errors: the harness classes log through their existing slf4j loggers; the new classes add no
log statements. `NestedBuild.run` returns the `BuildResult` of a failing build, so a negative sample's
failure is an assertion, not an exception.

### 7.3 `webtools-http/build.gradle.kts` (new)

```kotlin
// webtools-http - the HTTP client stack: the request and response types, a suspend transport port, a
// typed failure model and the other ports and defaults of the stack. Kotlin Multiplatform: commonMain
// holds the contracts and the platform-free code, jvmMain the platform actuals and the OkHttp and
// Caffeine implementations. It publishes webtools-http and webtools-http-jvm (a Maven build depends on
// webtools-http-jvm).
//
// 1.0.0 is the version inherited from the root build, and it is UNRELEASED: webtools-http is new, and it
// stays unpublished until the whole stack (issue #55) is complete. It is not a release candidate while
// the units of that work land one by one. Remove this note when 1.0.0 is released.
//
// Everything shared (the jvm() target at the Java floor, the test dependencies, the level tasks,
// publishing, binary-compatibility-validator and verifyJavaFloor) comes from the convention plugin. This
// file adds explicitApi(), the dependencies, the module-wide opt-in, the coordinates and the POM name and
// description. The ABI dump api/webtools-http.api is committed (:webtools-http:apiDump); apiCheck is part
// of check.

plugins {
    id("webtools.kotlin-multiplatform-library")
}

// The marker that gates the module's own ports (HttpTransport, WallClock, HttpFailure, ...). The module's
// main code implements those ports, so it opts in; its tests do not, and stay a consumer.
val gateMarker = "com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi"

kotlin {
    explicitApi()

    sourceSets {
        // The opt-in is a language setting of the MAIN source sets only. Do not replace it with
        // kotlin { compilerOptions { optIn.add(...) } }, with the same option on the jvm target, or with
        // sourceSets.all { languageSettings.optIn(...) }: each of those also reaches commonTest and
        // jvmTest, which would then compile as a non-consumer and could implement a port or subclass a
        // failure group without the @OptIn a consumer needs. In a single-target module only the leaf line
        // (jvmMain) reaches the compiler; the commonMain line keeps the pair valid if a target is added
        // (a shared source set must use every opt-in its leaf uses).
        commonMain {
            languageSettings.optIn(gateMarker)
            dependencies {
                // The runtime of the suspend API, and CoroutineDispatcher appears in the stack's
                // configuration: the one api dependency. The others are implementation details.
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        jvmMain {
            languageSettings.optIn(gateMarker)
            dependencies {
                // OkHttp: the transport. Caffeine: the response cache. slf4j: the logging seam.
                implementation("com.squareup.okhttp3:okhttp-jvm:5.5.0")
                implementation("com.github.ben-manes.caffeine:caffeine:3.3.1")
                implementation("org.slf4j:slf4j-api:2.0.13")
            }
        }
    }
}

mavenPublishing {
    coordinates(group.toString(), "webtools-http", version.toString())
    pom {
        name.set("WebTools HTTP")
        description.set("A suspend HTTP client stack: request and response types, a transport port, a typed failure model, an outbound-URL policy, a response cache and a call budget.")
    }
}
```

No `version =` line (the root default `1.0.0` applies), no `jvmTarget`, no toolchain, no
`-Xjdk-release`, no `TargetJvmVersion`, no second target, no validator call, no `kotlinx-coroutines-test`.
`okhttp-jvm` brings `kotlin-stdlib` 2.2.21 onto the module's `jvm` classpaths; Kotlin 2.2.0 reads it.

### 7.4 `webtools-http-commoncheck` (new)

**`webtools-http-commoncheck/build.gradle.kts`**

```kotlin
// webtools-http-commoncheck - an unpublished, check-only module. It compiles webtools-http's commonMain
// for a second target (js), so a JVM API in common code is a compile error: a module with only a jvm()
// target accepts java.* in commonMain. It publishes nothing and runs no tests; its jsMain holds a js
// actual for every expect declaration in that directory. ./gradlew build and check compile it, and no
// Node.js is needed.

plugins {
    id("webtools.kotlin-common-check")
}

// The gate marker of webtools-http. This module compiles the same sources, which implement the gated
// ports, so it opts in as webtools-http does: on commonMain and on jsMain. The jsMain line is the one the
// js compile reads (the commonMain line alone is inert in a single-target module); the commonMain line
// keeps the pair valid if the module ever gains a target. It is written here and not in the convention
// plugin: a module that cannot see the marker warns on every compile.
val gateMarker = "com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi"

kotlin {
    sourceSets {
        commonMain {
            // webtools-http's commonMain, as a path relative to this module (no project(...) access).
            kotlin.srcDir("../webtools-http/src/commonMain/kotlin")
            languageSettings.optIn(gateMarker)
            dependencies {
                // The same dependency as webtools-http's commonMain; keep the version in step.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        jsMain {
            languageSettings.optIn(gateMarker)
        }
    }
}
```

No `explicitApi()` (the sibling enforces it on the same sources), no version, no coordinates, no
publishing.

**`webtools-http-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/http/JsActuals.kt`**

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

If `Log` gains a member, `NoOpLog` overrides it too; the file is the only `js` actual of the module.

### 7.5 `webtools-http` `commonMain` (new files, package `com.spartanlabs.webtools.http`)

**`ExperimentalForInheritanceWebToolsApi.kt`** — the marker. The message is **one string literal** (no
`+`), so the module's own diagnostics carry it.

```kotlin
/**
 * Marks a WebTools seam whose implementation or subclassing is Experimental. ... (KDoc: what it gates,
 * how to opt in, that using the supplied implementations needs no opt-in, that the class is never removed.)
 *
 * **Stable Core.** Applied only through `@SubclassOptInRequired` and `@OptIn`, never to a declaration.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Implementing or substituting this WebTools seam is Experimental and may change in a minor release. Using the supplied implementations needs no opt-in. Opt in with @OptIn(ExperimentalForInheritanceWebToolsApi::class).",
)
@Target()
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public annotation class ExperimentalForInheritanceWebToolsApi
```

**`WallClock.kt`**

```kotlin
/**
 * A source of wall-clock time, in epoch milliseconds ... (contract: §6.6).
 * **Experimental (implement).** Implementing or delegating needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`;
 * calling and passing it are Stable Core.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface WallClock {
    /** @return milliseconds since 1970-01-01T00:00:00Z; may step backwards when the system clock is adjusted. */
    public fun nowEpochMillis(): Long

    /** The system clock. **Stable Core.** Final, stateless and usable without the opt-in. */
    public object System : WallClock {
        override fun nowEpochMillis(): Long = epochMillisNow()
        override fun toString(): String = "WallClock.System"
    }
}
```

**`EpochMillis.kt`** — `internal expect fun epochMillisNow(): Long` (KDoc: "the platform's wall-clock
reading in epoch milliseconds"). The `jvmMain` actual is `EpochMillis.jvm.kt` (§7.6), the `jsMain` one is in
`JsActuals.kt`.

**`HttpTransport.kt`**

```kotlin
/**
 * The seam between the stack and the network. (KDoc: the seven contract points of §6.6, then:)
 * **Experimental (implement).** ... Use the supplied implementations, or a fake, without the opt-in.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface HttpTransport : AutoCloseable {
    /**
     * Sends [request] and returns the response, or the failure.
     *
     * @param request the request to send.
     * @return the response for any status in 100..599, or a [HttpFailure] (normally a [TransportFailure]);
     *   cancellation is thrown, never returned.
     */
    public suspend fun execute(request: HttpRequest): Result<HttpResponse>

    /** Releases the transport's resources. Idempotent, non-suspend, does not throw. The default does nothing. */
    override fun close() {}
}
```

**`HttpFailure.kt`** — the root and its three direct leaves.

```kotlin
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class HttpFailure : Exception {
    protected constructor(message: String) : super(message)
    protected constructor(message: String, cause: Throwable?) : super(message, cause)

    /** A component was used after `close()`. ... */
    public class Closed(public val component: String) : HttpFailure("The $component is closed") {
        init { require(component.isNotBlank()) { "component must not be blank" } }
    }

    public class BudgetExhausted(
        public val wait: Duration,
        public val reason: ExhaustedReason,
        public val window: String?,
    ) : HttpFailure(
        "The call budget is exhausted: ${reason.name}, wait $wait" + (if (window != null) ", window $window" else ""),
    ) {
        init {
            require(!wait.isNegative()) { "wait must not be negative" }
            require(window == null || window.isNotBlank()) { "window must be null or not blank" }
        }
    }

    public class DecodeFailure(cause: Throwable?) : HttpFailure("The response body could not be decoded", cause) {
        public constructor() : this(null)
    }
}
```

**`TransportFailure.kt`** — the group and its eleven leaves. Every leaf follows the pattern shown; messages
are the table of §6.3.

```kotlin
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class TransportFailure : HttpFailure {
    protected constructor(message: String) : super(message)
    protected constructor(message: String, cause: Throwable?) : super(message, cause)

    public class Timeout(cause: Throwable?) : TransportFailure("The request timed out", cause) {
        public constructor() : this(null)
    }
    // ConnectFailed, TlsFailed, Protocol, Io and RetryRefused: the same shape, messages per §6.3.

    public class DnsFailed(public val host: String, cause: Throwable?) :
        TransportFailure("The host name could not be resolved: $host", cause) {
        public constructor(host: String) : this(host, null)
        init { require(host.isNotBlank()) { "host must not be blank" } }
    }
    // InvalidRequest(rule), RefusedByPolicy(reason), TooManyRedirects(limit): the same shape with one field.

    public class BodyTooLarge(public val limit: Long, public val declared: Long?, cause: Throwable?) :
        TransportFailure(
            "The response body is larger than the limit of $limit bytes" +
                (if (declared != null) " (declared $declared bytes)" else ""),
            cause,
        ) {
        public constructor(limit: Long, declared: Long?) : this(limit, declared, null)
        init {
            require(limit >= 0) { "limit must not be negative" }
            require(declared == null || declared >= 0) { "declared must be null or not negative" }
        }
    }
}
```

`InvalidRequest`'s KDoc: "`rule` is a short description of the broken rule, written by the library; it
never holds request data." `RefusedByPolicy` exposes `reason: RefusalReason`; `TooManyRedirects` exposes
`limit: Int` (`require(limit >= 0)`).

**`UpstreamFailure.kt`**

```kotlin
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public abstract class UpstreamFailure protected constructor(
    public val status: Int,
    message: String,
) : HttpFailure(message) {
    init { require(status in 100..599) { "Status code must be in 100..599" } }

    /** The server throttled the call. Created by the call facades only. */
    public class RateLimited internal constructor(status: Int, public val retryAfter: Duration) :
        UpstreamFailure(status, "The server rate-limited the call (status $status); retry in $retryAfter") {
        init { require(!retryAfter.isNegative()) { "retryAfter must not be negative" } }
    }

    /** The server answered with a status the caller must handle. Keeps headers and body; prints neither. */
    public class UpstreamError(status: Int, public val headers: HttpHeaders, body: ByteArray) :
        UpstreamFailure(status, "The server answered with status $status") {
        private val bodyBytes: ByteArray = body.copyOf()
        public val bodySize: Int get() = bodyBytes.size
        public fun body(): ByteArray = bodyBytes.copyOf()
    }
}
```

**`RefusalReason.kt`, `ExhaustedReason.kt`** — §6.3.

**`Log.kt`**

```kotlin
/** The module's logging seam: slf4j on the JVM, a no-op on js. Parameterised messages take `{}` placeholders. */
internal interface Log {
    fun debug(message: String, vararg args: Any?)
    fun info(message: String, vararg args: Any?)
    fun warn(message: String, vararg args: Any?)
}

/** Returns the [Log] named [name]. The `actual` of each target supplies it; a missing one is a compile error. */
internal expect fun logger(name: String): Log
```

**`ResultExtensions.kt`**

```kotlin
// Deliberately duplicated: webtools-udp/src/main/kotlin/com/spartanlabs/webtools/udp/ResultExtensions.kt and
// webtools-scraping's ResultExtensions.kt hold the same two lines. Kotlin's `internal` stops at the Gradle
// module, so one module cannot reach another's copy, and a shared artifact for two lines would add public
// API. Keep the copies identical.

/** (KDoc as in webtools-udp: chains a Result-returning transform, short-circuiting on failure.) */
internal inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    fold(onSuccess = transform, onFailure = { Result.failure(it) })
```

**`NonFatal.kt`**

```kotlin
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and returns its value as a success. An [Exception] other than [CancellationException]
 * becomes `Result.failure`; a [CancellationException] is rethrown so cancellation stays cooperative; an
 * [Error] is not caught. Being `inline`, [block] may call suspend functions when the caller is a suspend
 * function. This is the module's one `try`/`catch`; it replaces `runCatching`, which catches everything.
 *
 * @param block the code to run.
 * @return the value, or the non-fatal exception it threw.
 * @throws CancellationException if [block] was cancelled.
 */
internal inline fun <T> catchNonFatal(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
```

**`HttpSyntax.kt`** — the predicates of §6.4.

```kotlin
internal object HttpSyntax {
    private const val TOKEN_SPECIALS: String = "!#\$%&'*+-.^_`|~"
    private val CREDENTIAL_NAMES: List<String> = listOf("Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie")

    fun isTokenChar(c: Char): Boolean = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in TOKEN_SPECIALS
    fun firstNonTokenIndex(text: CharSequence): Int = text.indexOfFirst { !isTokenChar(it) }
    fun isToken(text: CharSequence): Boolean = text.isNotEmpty() && firstNonTokenIndex(text) < 0
    fun trimOws(value: String): String = value.trim { it == ' ' || it == '\t' }
    fun isFieldValue(value: String): Boolean = value.none { it == '\r' || it == '\n' || it == '\u0000' }
    fun isSendableValue(value: String): Boolean = value.all { it == '\t' || it in ' '..'~' }
    fun sanitizeReceivedValue(raw: String): String =
        trimOws(raw.map { if (it == '\r' || it == '\n' || it == '\u0000') ' ' else it }.joinToString(""))
    fun asciiLower(c: Char): Char = if (c in 'A'..'Z') c + ('a' - 'A') else c
    fun equalsName(a: String, b: String): Boolean =
        a.length == b.length && a.indices.all { asciiLower(a[it]) == asciiLower(b[it]) }
    fun isCredentialName(name: String): Boolean = CREDENTIAL_NAMES.any { equalsName(it, name) }

    /** -1 when [text] is a media type; otherwise the index at which it stops being one. */
    fun mediaTypeProblemIndex(text: String): Int {
        val typeEnd = tokenEnd(text, 0)
        if (typeEnd == 0 || typeEnd >= text.length || text[typeEnd] != '/') return typeEnd
        val subtypeStart = typeEnd + 1
        val subtypeEnd = tokenEnd(text, subtypeStart)
        if (subtypeEnd == subtypeStart) return subtypeStart
        var pos = subtypeEnd
        while (pos < text.length) {
            val semi = skipOws(text, pos)
            if (semi >= text.length) return pos            // trailing whitespace
            if (text[semi] != ';') return semi
            val paramStart = skipOws(text, semi + 1)
            if (paramStart >= text.length) return if (paramStart == semi + 1) -1 else semi + 1
            if (text[paramStart] == ';') { pos = paramStart; continue }   // an empty parameter
            val nameEnd = tokenEnd(text, paramStart)
            if (nameEnd == paramStart) return paramStart
            if (nameEnd >= text.length || text[nameEnd] != '=') return nameEnd
            val valueStart = nameEnd + 1
            val quoted = valueStart < text.length && text[valueStart] == '"'
            val valueEnd = if (quoted) quotedEnd(text, valueStart) else tokenEnd(text, valueStart)
            if (valueEnd < 0) return -1 - valueEnd         // quotedEnd reports a failure as -1 - index
            if (valueEnd == valueStart) return valueStart  // an empty token value
            pos = valueEnd
        }
        return -1
    }

    private fun tokenEnd(text: String, from: Int): Int {
        var i = from
        while (i < text.length && isTokenChar(text[i])) i++
        return i
    }

    private fun skipOws(text: String, from: Int): Int {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return i
    }

    // The index after the closing quote, or -1 - (failing index).
    private fun quotedEnd(text: String, open: Int): Int {
        var i = open + 1
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> return i + 1
                c == '\\' -> {
                    val escaped = text.getOrNull(i + 1)
                    if (escaped == null || !(escaped == '\t' || escaped in ' '..'~')) return -1 - (i + 1)
                    i += 2
                }
                c == '\t' || c == ' ' || c == '!' || c in '#'..'[' || c in ']'..'~' -> i++
                else -> return -1 - i
            }
        }
        return -1 - text.length
    }
}
```

**`HttpHeaders.kt`**

```kotlin
public class HttpHeaders internal constructor(entries: List<Entry>) {
    private val items: List<Entry> = entries.toList()

    public val size: Int get() = items.size
    public fun isEmpty(): Boolean = items.isEmpty()
    public operator fun get(name: String): String? = items.lastOrNull { HttpSyntax.equalsName(it.name, name) }?.value
    public fun getAll(name: String): List<String> = items.filter { HttpSyntax.equalsName(it.name, name) }.map { it.value }
    public operator fun contains(name: String): Boolean = items.any { HttpSyntax.equalsName(it.name, name) }
    public fun entries(): List<Entry> = items.toList()

    override fun equals(other: Any?): Boolean = other is HttpHeaders && other.items == items
    override fun hashCode(): Int = items.hashCode()
    override fun toString(): String =
        items.joinToString(separator = ", ", prefix = "HttpHeaders(", postfix = ")") { it.name + if (it.isSensitive) "*" else "" }

    /** One header line: its name (spelling kept), its value, and whether it is sensitive. */
    public class Entry internal constructor(
        public val name: String,
        public val value: String,
        internal val flagged: Boolean,
    ) {
        /** True when the entry was added as sensitive or its name is a credential header name. */
        public val isSensitive: Boolean get() = flagged || HttpSyntax.isCredentialName(name)

        override fun equals(other: Any?): Boolean =
            other is Entry && other.name == name && other.value == value && other.isSensitive == isSensitive
        override fun hashCode(): Int = (name.hashCode() * 31 + value.hashCode()) * 31 + isSensitive.hashCode()
        override fun toString(): String = if (isSensitive) "Entry($name, sensitive)" else "Entry($name)"
    }

    /** Collects entries for [HttpHeaders]. Not thread-safe; use it only inside the factory's lambda. */
    public class Builder internal constructor() {
        private val items = ArrayList<Entry>()

        public fun add(name: String, value: String) { items.add(entry(name, value, flagged = false)) }
        public fun addSensitive(name: String, value: String) { items.add(entry(name, value, flagged = true)) }
        public fun set(name: String, value: String) {
            replace(name, value, flagged = items.any { it.flagged && HttpSyntax.equalsName(it.name, name) })
        }
        public fun setSensitive(name: String, value: String) { replace(name, value, flagged = true) }
        public fun remove(name: String) { items.removeAll { HttpSyntax.equalsName(it.name, name) } }
        public fun addAll(headers: HttpHeaders) { items.addAll(headers.entries()) }

        internal fun build(): HttpHeaders = HttpHeaders(items)

        // Validates before it mutates, so a failing call changes nothing.
        private fun replace(name: String, value: String, flagged: Boolean) {
            val entry = entry(name, value, flagged)
            items.removeAll { HttpSyntax.equalsName(it.name, name) }
            items.add(entry)
        }

        private fun entry(name: String, value: String, flagged: Boolean): Entry {
            require(name.isNotEmpty()) { "Header name must not be empty" }
            val bad = HttpSyntax.firstNonTokenIndex(name)
            require(bad < 0) { "Header name is not an RFC 9110 token (first offending character at index $bad)" }
            val trimmed = HttpSyntax.trimOws(value)
            require(HttpSyntax.isFieldValue(trimmed)) { "Value of header '$name' must not contain CR, LF or NUL" }
            return Entry(name, trimmed, flagged)
        }
    }
}

/** Builds an [HttpHeaders]. @throws IllegalArgumentException from the builder calls (§6.4). */
public fun HttpHeaders(configure: HttpHeaders.Builder.() -> Unit = {}): HttpHeaders =
    HttpHeaders.Builder().apply(configure).build()
```

(`HttpHeaders.Builder.build()` hands `items` to the constructor, which copies it, so a builder that
escapes the lambda cannot change a built value.)

**`HttpMethod.kt`**

```kotlin
public class HttpMethod private constructor(public val name: String) {
    internal val permitsBody: Boolean get() = name != "GET" && name != "HEAD" && name != "TRACE"

    override fun equals(other: Any?): Boolean = other is HttpMethod && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name

    public companion object {
        public val Get: HttpMethod = HttpMethod("GET")
        public val Head: HttpMethod = HttpMethod("HEAD")
        public val Post: HttpMethod = HttpMethod("POST")
        public val Put: HttpMethod = HttpMethod("PUT")
        public val Patch: HttpMethod = HttpMethod("PATCH")
        public val Delete: HttpMethod = HttpMethod("DELETE")
        public val Options: HttpMethod = HttpMethod("OPTIONS")

        private val known: Map<String, HttpMethod> = listOf(Get, Head, Post, Put, Patch, Delete, Options).associateBy { it.name }

        /** @return the method named [name] (case-sensitive), or a failure with an `IllegalArgumentException` when [name] is not an RFC 9110 token. */
        public fun of(name: String): Result<HttpMethod> =
            if (HttpSyntax.isToken(name)) Result.success(known[name] ?: HttpMethod(name))
            else Result.failure(IllegalArgumentException("Method is not an RFC 9110 token"))
    }
}
```

**`RequestBody.kt`**

```kotlin
public class RequestBody(bytes: ByteArray, public val contentType: String?) {
    private val data: ByteArray = bytes.copyOf()

    init {
        if (contentType != null) {
            val bad = HttpSyntax.mediaTypeProblemIndex(contentType)
            require(bad < 0) { "Content type is not an RFC 9110 media type (first offending character at index $bad)" }
        }
    }

    public val size: Int get() = data.size
    public fun bytes(): ByteArray = data.copyOf()
    override fun toString(): String = "RequestBody(size=${data.size}, hasContentType=${contentType != null})"
}
```

**`HttpRequest.kt`** (`private const val CONTENT_TYPE = "Content-Type"` at file level)

```kotlin
public class HttpRequest internal constructor(
    public val method: HttpMethod,
    public val url: Url,
    public val headers: HttpHeaders,
    public val body: RequestBody?,
) {
    init {
        require(method.name != "CONNECT") { "A CONNECT request is not supported" }
        headers.entries().forEach { entry ->
            require(HttpSyntax.isSendableValue(entry.value)) {
                "Value of header '${entry.name}' must be visible ASCII, space or tab to be sent"
            }
        }
        require(body == null || method.permitsBody) { "A ${method.name} request must not have a body" }
        require(body?.contentType == null || CONTENT_TYPE !in headers) {
            "A request with a body content type must not also set a Content-Type header"
        }
    }

    override fun toString(): String =
        "HttpRequest(method=$method, url=${url.redacted()}, headers=$headers, body=${body ?: "none"})"

    /** Collects the optional parts of an [HttpRequest]. */
    public class Builder internal constructor() {
        /** The request headers; empty by default. */
        public var headers: HttpHeaders = HttpHeaders()
        /** The request body; `null` (none) by default. */
        public var body: RequestBody? = null
    }
}

public fun HttpRequest(method: HttpMethod, url: Url, configure: HttpRequest.Builder.() -> Unit = {}): HttpRequest {
    val builder = HttpRequest.Builder().apply(configure)
    return HttpRequest(method, url, builder.headers, builder.body)
}
```

**`HttpResponse.kt`**

```kotlin
public class HttpResponse internal constructor(
    public val status: Int,
    public val url: Url,
    public val headers: HttpHeaders,
    redirectChain: List<RedirectHop>,
    body: ByteArray,
) {
    public val redirectChain: List<RedirectHop> = redirectChain.toList()
    private val data: ByteArray = body.copyOf()

    init { require(status in 100..599) { "Status code must be in 100..599" } }

    public val bodySize: Int get() = data.size
    public fun body(): ByteArray = data.copyOf()

    override fun toString(): String =
        "HttpResponse(status=$status, url=${url.redacted()}, headers=$headers, bodySize=${data.size}, redirects=${redirectChain.size})"

    /** Collects the optional parts of an [HttpResponse]. */
    public class Builder internal constructor() {
        public var headers: HttpHeaders = HttpHeaders()
        public var redirectChain: List<RedirectHop> = emptyList()
        internal var bodyBytes: ByteArray = ByteArray(0)

        /** Sets the body; [bytes] is copied when the response is built. */
        public fun body(bytes: ByteArray) { bodyBytes = bytes }
    }
}

public fun HttpResponse(status: Int, url: Url, configure: HttpResponse.Builder.() -> Unit = {}): HttpResponse {
    val builder = HttpResponse.Builder().apply(configure)
    return HttpResponse(status, url, builder.headers, builder.redirectChain, builder.bodyBytes)
}
```

**`RedirectHop.kt`**

```kotlin
public class RedirectHop(public val url: Url, public val status: Int) {
    init { require(status in 300..399) { "Redirect status must be in 300..399" } }
    override fun equals(other: Any?): Boolean = other is RedirectHop && other.url == url && other.status == status
    override fun hashCode(): Int = url.hashCode() * 31 + status
    override fun toString(): String = "RedirectHop(${url.redacted()}, $status)"
}
```

**`Url.kt`**

```kotlin
public class Url private constructor(
    public val scheme: String,
    public val host: String,
    public val port: Int,
    public val encodedPath: String,
    public val encodedQuery: String?,
) {
    // Everything before the query, canonical; computed once.
    private val withoutQuery: String = buildString {
        append(scheme).append("://")
        if (':' in host) append('[').append(host).append(']') else append(host)
        if (port != defaultPortOf(scheme)) append(':').append(port)
        append(encodedPath)
    }
    private val text: String = if (encodedQuery == null) withoutQuery else "$withoutQuery?$encodedQuery"

    /** @return [toString] with `?...` in place of the query when there is one (a bare `?` included). For logs; not parseable. */
    public fun redacted(): String = if (encodedQuery == null) withoutQuery else "$withoutQuery?..."

    /** The full canonical URL, **query included**; use [redacted] for logs and messages. */
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is Url && other.text == text
    override fun hashCode(): Int = text.hashCode()

    public companion object {
        /**
         * Parses [text] ... (rules of §6.5).
         * @return the canonical URL, or a failure holding an [IllegalArgumentException] with a constant message
         *   that never echoes [text].
         */
        public fun parse(text: String): Result<Url> =
            parseUrlText(text).map { Url(it.scheme, it.host, it.port, it.encodedPath, it.encodedQuery) }
    }
}
```

**`UrlProblem.kt`**

```kotlin
internal enum class UrlProblem(val message: String) {
    Empty("The URL is empty."),
    Backslash("The URL contains a backslash."),
    WhitespaceOrControl("The URL contains whitespace or a control character."),
    NotUnicode("The URL contains text that is not valid Unicode."),
    Scheme("The URL must begin with http:// or https://."),
    UserInfo("The URL contains user-info."),
    HostEncoding("The URL host must be plain ASCII, without percent-encoding."),
    HostIdn("The URL host contains an internationalised (xn--) label."),
    Host("The URL host is not a valid host name or IP address literal."),
    Port("The URL port must be a number from 1 to 65535."),
    DotSegment("A path segment must not be \".\" or \"..\"."),
}

/** The failure `Url.parse` and the encoders return; its [problem] is what tests assert on. */
internal class UrlParseException(val problem: UrlProblem) : IllegalArgumentException(problem.message)

internal fun <T> urlFailure(problem: UrlProblem): Result<T> = Result.failure(UrlParseException(problem))
```

**`UrlParsing.kt`** — the pipeline. Each step is a pure function; none throws.

```kotlin
internal class ParsedUrl(val scheme: String, val host: String, val port: Int, val encodedPath: String, val encodedQuery: String?)

internal fun defaultPortOf(scheme: String): Int = if (scheme == "https") 443 else 80

internal fun parseUrlText(text: String): Result<ParsedUrl> =
    trimUrlText(text)
        .flatMap { checkUrlCharacters(it) }
        .flatMap { splitScheme(it) }
        .flatMap { splitAuthority(it) }
        .flatMap { parseAuthority(it) }
        .map { assemble(it) }

private class SchemeSplit(val scheme: String, val afterSeparator: String)
private class AuthoritySplit(val scheme: String, val authority: String, val tail: String)
private class HostAndPort(val scheme: String, val host: String, val port: Int, val tail: String)

private fun isTrimmedEnd(c: Char): Boolean = c == '\t' || c == '\n' || c == '\u000C' || c == '\r' || c == ' '

private fun trimUrlText(text: String): Result<String> {
    val start = text.indexOfFirst { !isTrimmedEnd(it) }
    if (start < 0) return urlFailure(UrlProblem.Empty)
    return Result.success(text.substring(start, text.indexOfLast { !isTrimmedEnd(it) } + 1))
}

// C0 and the space, DEL and C1 and NBSP, and the other Unicode White_Space code points.
private fun isUrlSpace(c: Char): Boolean {
    val code = c.code
    return code <= 0x20 || code in 0x7F..0xA0 || code == 0x1680 || code in 0x2000..0x200A ||
        code == 0x2028 || code == 0x2029 || code == 0x202F || code == 0x205F || code == 0x3000
}

// One scan, left to right; the first offending character decides the kind.
private fun checkUrlCharacters(text: String): Result<String> {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' -> return urlFailure(UrlProblem.Backslash)
            isUrlSpace(c) -> return urlFailure(UrlProblem.WhitespaceOrControl)
            c.isHighSurrogate() ->
                if (i + 1 < text.length && text[i + 1].isLowSurrogate()) i++ else return urlFailure(UrlProblem.NotUnicode)
            c.isLowSurrogate() -> return urlFailure(UrlProblem.NotUnicode)
        }
        i++
    }
    return Result.success(text)
}

// ASCII letters compared by hand: Kotlin's ignoreCase folds U+017F to 's' and U+212A to 'k'.
private fun startsWithAsciiIgnoreCase(text: String, prefix: String): Boolean =
    text.length >= prefix.length && prefix.indices.all { HttpSyntax.asciiLower(text[it]) == prefix[it] }

private fun splitScheme(text: String): Result<SchemeSplit> = when {
    startsWithAsciiIgnoreCase(text, "https://") -> Result.success(SchemeSplit("https", text.substring(8)))
    startsWithAsciiIgnoreCase(text, "http://") -> Result.success(SchemeSplit("http", text.substring(7)))
    else -> urlFailure(UrlProblem.Scheme)
}

private fun splitAuthority(split: SchemeSplit): Result<AuthoritySplit> {
    val rest = split.afterSeparator
    val end = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
    val authority = rest.substring(0, end)
    return if ('@' in authority) urlFailure(UrlProblem.UserInfo)
    else Result.success(AuthoritySplit(split.scheme, authority, rest.substring(end)))
}

private fun splitHostAndPort(authority: String): Result<Pair<String, String?>> =
    if (authority.startsWith('[')) {
        val close = authority.indexOf(']')
        when {
            close < 0 -> urlFailure(UrlProblem.Host)
            close == authority.length - 1 -> Result.success(authority to null)
            authority[close + 1] == ':' -> Result.success(authority.substring(0, close + 1) to authority.substring(close + 2))
            else -> urlFailure(UrlProblem.Host)
        }
    } else {
        val colon = authority.indexOf(':')
        if (colon < 0) Result.success(authority to null)
        else Result.success(authority.substring(0, colon) to authority.substring(colon + 1))
    }

// Digits only (a sign is rejected), leading zeros allowed, 1..65535; absent or empty is the default.
private fun parsePort(portText: String?, default: Int): Result<Int> = when {
    portText.isNullOrEmpty() -> Result.success(default)
    !portText.all { it in '0'..'9' } -> urlFailure(UrlProblem.Port)
    else -> portText.trimStart('0').let { digits ->
        if (digits.isEmpty() || digits.length > 5) urlFailure(UrlProblem.Port)
        else digits.toInt().let { if (it in 1..65535) Result.success(it) else urlFailure(UrlProblem.Port) }
    }
}

// The port is judged before the host, so `h:0` is a Port failure whatever the host is.
private fun parseAuthority(split: AuthoritySplit): Result<HostAndPort> =
    splitHostAndPort(split.authority).flatMap { (hostText, portText) ->
        parsePort(portText, defaultPortOf(split.scheme)).flatMap { port ->
            canonicalHost(hostText).map { host -> HostAndPort(split.scheme, host, port, split.tail) }
        }
    }

private fun assemble(parts: HostAndPort): ParsedUrl {
    val withoutFragment = parts.tail.substringBefore('#')
    val queryStart = withoutFragment.indexOf('?')
    val pathText = if (queryStart < 0) withoutFragment else withoutFragment.substring(0, queryStart)
    val queryText = if (queryStart < 0) null else withoutFragment.substring(queryStart + 1)
    return ParsedUrl(parts.scheme, parts.host, parts.port, canonicalPath(pathText), queryText?.let { canonicalQuery(it) })
}
```

**`UrlHost.kt`** — host canonicalisation and IPv6.

```kotlin
// A host as the authority holds it: "[...]" or a name. Returns the canonical host (IPv6 without brackets).
internal fun canonicalHost(hostText: String): Result<String> =
    if (hostText.startsWith('[')) canonicalIpv6(hostText.substring(1, hostText.length - 1)) else canonicalName(hostText)

private fun canonicalName(text: String): Result<String> {
    if (text.any { it == '%' || it.code >= 0x80 }) return urlFailure(UrlProblem.HostEncoding)
    val host = buildString(text.length) { text.forEach { append(HttpSyntax.asciiLower(it)) } }
    val labels = host.removeSuffix(".").split('.')
    return when {
        !host.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' || it == '.' } -> urlFailure(UrlProblem.Host)
        host.length !in 1..253 -> urlFailure(UrlProblem.Host)
        labels.any { it.length !in 1..63 } -> urlFailure(UrlProblem.Host)
        labels.any { it.startsWith("xn--") } -> urlFailure(UrlProblem.HostIdn)
        else -> Result.success(host)
    }
}

private fun canonicalIpv6(inner: String): Result<String> = decodeIpv6(inner).map { formatIpv6(it) }

// Decoded over split parts, never over an index into a fixed array, so no input can overrun.
private fun decodeIpv6(text: String): Result<IntArray> {
    if (':' !in text) return urlFailure(UrlProblem.Host)
    val gap = text.indexOf("::")
    if (gap >= 0 && text.indexOf("::", gap + 1) >= 0) return urlFailure(UrlProblem.Host)
    val head = (if (gap >= 0) text.substring(0, gap) else text).splitGroups()
    val tail = if (gap >= 0) text.substring(gap + 2).splitGroups() else emptyList()
    val headGroups = parseGroups(head, allowQuadAtEnd = gap < 0).getOrElse { return urlFailure(UrlProblem.Host) }
    val tailGroups = parseGroups(tail, allowQuadAtEnd = gap >= 0).getOrElse { return urlFailure(UrlProblem.Host) }
    val explicit = headGroups.size + tailGroups.size
    return when {
        gap >= 0 && explicit <= 7 -> Result.success((headGroups + List(8 - explicit) { 0 } + tailGroups).toIntArray())
        gap < 0 && explicit == 8 -> Result.success(headGroups.toIntArray())
        else -> urlFailure(UrlProblem.Host)
    }
}

private fun String.splitGroups(): List<String> = if (isEmpty()) emptyList() else split(':')

// A part is 1..4 hex digits; the last part of the address may be a dotted IPv4 quad (two groups).
private fun parseGroups(parts: List<String>, allowQuadAtEnd: Boolean): Result<List<Int>> {
    val groups = ArrayList<Int>(parts.size + 1)
    parts.forEachIndexed { index, part ->
        if (allowQuadAtEnd && index == parts.lastIndex && '.' in part) {
            val quad = parseIpv4Quad(part) ?: return urlFailure(UrlProblem.Host)
            groups.add((quad[0] shl 8) or quad[1])
            groups.add((quad[2] shl 8) or quad[3])
        } else {
            groups.add(parseHexGroup(part) ?: return urlFailure(UrlProblem.Host))
        }
    }
    return Result.success(groups)
}

private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

private fun parseHexGroup(part: String): Int? =
    if (part.length in 1..4 && part.all { isHexDigit(it) }) part.toInt(16) else null

private fun parseIpv4Quad(part: String): IntArray? {
    val pieces = part.split('.')
    if (pieces.size != 4) return null
    val values = pieces.map { piece ->
        if (piece.length !in 1..3 || !piece.all { it in '0'..'9' } || (piece.length > 1 && piece[0] == '0')) return null
        piece.toInt().also { if (it > 255) return null }
    }
    return values.toIntArray()
}

// RFC 5952: lower-case hex, no leading zeros, the first longest run of two or more zero groups becomes "::";
// an IPv4-mapped address (groups 0..4 zero, group 5 = ffff) is printed as dotted IPv4.
private fun formatIpv6(g: IntArray): String {
    if ((0..4).all { g[it] == 0 } && g[5] == 0xFFFF) {
        return "${g[6] shr 8}.${g[6] and 0xFF}.${g[7] shr 8}.${g[7] and 0xFF}"
    }
    var bestStart = -1
    var bestLength = 0
    var i = 0
    while (i < 8) {
        if (g[i] != 0) { i++; continue }
        var j = i
        while (j < 8 && g[j] == 0) j++
        if (j - i > bestLength) { bestStart = i; bestLength = j - i }
        i = j
    }
    fun hex(range: IntRange): String = range.joinToString(":") { g[it].toString(16) }
    return if (bestLength < 2) hex(0..7) else hex(0 until bestStart) + "::" + hex((bestStart + bestLength) until 8)
}
```

**`UrlEncoding.kt`** — canonicalisers, the dot fold and the two `internal` encoders.

```kotlin
private const val HEX: String = "0123456789ABCDEF"

private fun StringBuilder.appendEncoded(byte: Int) {
    append('%').append(HEX[byte shr 4]).append(HEX[byte and 0x0F])
}

// Percent-encodes every non-ASCII code point as UTF-8 and every ASCII character for which [encodes] is true.
// A '%' is never touched unless [encodes] says so. The input holds no unpaired surrogate (the scan or the
// encoder's own check has rejected it), so encodeToByteArray never substitutes.
private fun percentEncode(text: String, encodes: (Char) -> Boolean): String {
    if (text.none { it.code >= 0x80 || encodes(it) }) return text
    return buildString(text.length + 16) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.code < 0x80) {
                if (encodes(c)) appendEncoded(c.code) else append(c)
                i++
            } else {
                val width = if (c.isHighSurrogate()) 2 else 1
                text.substring(i, i + width).encodeToByteArray().forEach { appendEncoded(it.toInt() and 0xFF) }
                i += width
            }
        }
    }
}

private fun encodedInPathSegment(c: Char): Boolean = c == '"' || c == '<' || c == '>' || c == '^' || c == '`' || c == '{' || c == '|' || c == '}'
private fun encodedInQuery(c: Char): Boolean = c == '"' || c == '\'' || c == '<' || c == '>'

private fun equalsAsciiIgnoreCase(a: String, b: String): Boolean =
    a.length == b.length && a.indices.all { HttpSyntax.asciiLower(a[it]) == HttpSyntax.asciiLower(b[it]) }

private fun isDotSegment(s: String): Boolean = s == "." || equalsAsciiIgnoreCase(s, "%2e")
private fun isDoubleDotSegment(s: String): Boolean =
    s == ".." || equalsAsciiIgnoreCase(s, "%2e.") || equalsAsciiIgnoreCase(s, ".%2e") || equalsAsciiIgnoreCase(s, "%2e%2e")

// HttpUrl's pop(): remove the last element; if it was empty and elements remain, the new last becomes "", else append "".
private fun popSegment(stack: MutableList<String>) {
    val removed = stack.removeAt(stack.lastIndex)
    if (removed.isEmpty() && stack.isNotEmpty()) stack[stack.lastIndex] = "" else stack.add("")
}

internal fun canonicalPath(pathText: String): String {
    if (pathText.isEmpty()) return "/"
    val segments = pathText.substring(1).split('/')   // pathText starts with '/'
    val stack = arrayListOf("")
    segments.forEachIndexed { index, raw ->
        val segment = percentEncode(raw, ::encodedInPathSegment)
        when {
            isDotSegment(segment) -> Unit
            isDoubleDotSegment(segment) -> popSegment(stack)
            else -> {
                if (stack[stack.lastIndex].isEmpty()) stack[stack.lastIndex] = segment else stack.add(segment)
                if (index < segments.lastIndex) stack.add("")
            }
        }
    }
    return stack.joinToString(separator = "") { "/$it" }
}

internal fun canonicalQuery(queryText: String): String = percentEncode(queryText, ::encodedInQuery)

private fun hasUnpairedSurrogate(text: String): Boolean {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) i++
        else if (c.isHighSurrogate() || c.isLowSurrogate()) return true
        i++
    }
    return false
}

private fun isRawInPathSegment(c: Char): Boolean =
    c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "!\$&'()*+,-.:;=@[]_~"

private fun isRawInQueryComponent(c: Char): Boolean = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "*-._"

/** The output of `HttpUrl.Builder.addPathSegment` for one segment, but a failure for `.`, `..` and an unpaired surrogate. */
internal fun encodePathSegment(segment: String): Result<String> = when {
    segment == "." || segment == ".." -> urlFailure(UrlProblem.DotSegment)
    hasUnpairedSurrogate(segment) -> urlFailure(UrlProblem.NotUnicode)
    else -> Result.success(percentEncode(segment) { !isRawInPathSegment(it) })
}

/** The output of `HttpUrl.Builder.addQueryParameter` for one name or value, but a failure for an unpaired surrogate. */
internal fun encodeQueryComponent(component: String): Result<String> = when {
    hasUnpairedSurrogate(component) -> urlFailure(UrlProblem.NotUnicode)
    else -> Result.success(percentEncode(component) { !isRawInQueryComponent(it) })
}
```

Three implementation cautions that the differential will otherwise find late: (1) a path segment's
canonical text is tested for `.` / `%2e` *after* encoding; (2) no `removeFirst()`, `removeLast()`,
`getFirst()`/`getLast()` on a `MutableList` (JDK 21 members; use `removeAt` and indexing), and no
`lowercase()`, `uppercase()`, `equals(ignoreCase = true)` or `Regex` on protocol text; (3) the parse is
linear: it builds with `StringBuilder`, never concatenates in a loop, and removes only from the end of a list.

### 7.6 `webtools-http` `jvmMain` and test resources (new)

**`<jvm>/Log.jvm.kt`** — the slf4j actual. The file is not named `Log.kt`, so its facade class
(`Log_jvmKt`) cannot clash with the `commonMain` file's.

```kotlin
package com.spartanlabs.webtools.http

import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal actual fun logger(name: String): Log = Slf4jLog(LoggerFactory.getLogger(name))

// Delegates to slf4j. A trailing Throwable among args is read as the exception, as slf4j reads any call.
private class Slf4jLog(private val delegate: Logger) : Log {
    override fun debug(message: String, vararg args: Any?) = delegate.debug(message, *args)
    override fun info(message: String, vararg args: Any?) = delegate.info(message, *args)
    override fun warn(message: String, vararg args: Any?) = delegate.warn(message, *args)
}
```

**`<jvm>/EpochMillis.jvm.kt`**

```kotlin
package com.spartanlabs.webtools.http

internal actual fun epochMillisNow(): Long = System.currentTimeMillis()
```

(`System` here is `java.lang.System`: `WallClock.System` is nested, so it is not a name of the package.)

**`webtools-http/src/jvmTest/resources/logback-test.xml`** — a byte copy of
`webtools-udp/src/test/resources/logback-test.xml` (`cp`; root level `INFO`, console appender). The three
existing copies are identical; the committed blob of the new one must be too (P9).

### 7.7 Tests

All new test and support files are listed, with their level and behaviours, in §9.

### 7.8 `webtools-http/api/webtools-http.api` (generated, committed)

Generated by `./gradlew :webtools-http:apiDump` after S5's last source change, and committed. It lists
every public declaration of the JVM ABI. The research recorded what to expect, and the review reads the
file for exactly these:

- `execute-gIAlu-s` on `HttpTransport` (a `suspend` function returning `Result` has a mangled JVM name),
  a public `HttpTransport$DefaultImpls`, and `public fun close ()V`;
- `WallClock$System` (final, with `INSTANCE`, `nowEpochMillis`, `toString`), and nested leaves as
  `HttpFailure$Closed`, `TransportFailure$Timeout`, `UpstreamFailure$RateLimited`, …;
- the abstract groups as `public abstract class` with `protected fun <init>` entries;
- the mangled `parse-…` on `Url$Companion` and `of-…` on `HttpMethod$Companion`;
- `$default` synthetic entries for exactly the three factory functions with a trailing builder lambda
  (`HttpHeaders`, `HttpRequest`, `HttpResponse`) and nothing else (a defaulted parameter anywhere else is a
  rule violation);
- the marker as `public abstract interface annotation class …ExperimentalForInheritanceWebToolsApi`;
- **no** `SubclassOptInRequired`, and no `internal` declaration (`Log`, `HttpSyntax`, `UrlProblem`, …).

---

## 8. Documentation impact

**Rings.**

| Ring | What moves with this change |
|---|---|
| 1 — in-editor | `//region`/`//endregion` blocks in `UrlParsing.kt` (scheme/authority, host/port) and `UrlHost.kt` (name, IPv6); the `js target` and `check` regions of the plugin; the comments in both module build files (the unreleased note, the opt-in warning); no `TODO` left in committed code |
| 2 — component/KDoc | KDoc (`@param`, `@return`, `@throws`) on every public class, interface, function, property and constructor, naming the tier and the contract (checklist below) |
| 3 — boundary/protocol | `HttpTransport`'s seven-point contract (the integration surface for a consumer's own transport and for the fakes); `HttpHeaders`/`HttpResponse` KDoc stating what a transport does with entries and statuses the types cannot hold; `Url` KDoc stating that it reads like OkHttp's `HttpUrl`, what it refuses, and that `toString()` carries the query; the failure leaves' constant-message rule; the compile-fail texts of the gate, recorded in `final-implementation.md` §3 |
| 4 — architecture | `README.md` (below); the build-file and plugin header comments; the `architecture.md` text that this unit's `js()` choice supersedes carries a dated correction of 2026-10-25, at §6.8 with pointers at §6.1 and §2 (§13); `docs/webtools-http-architecture.md` is unit 11's; `docs/dashboards/` is refreshed by the admin team's presenter, not by this change |

**KDoc checklist** (each public declaration):
- first sentence: what it is; second: **tier** (`**Stable Core.**`, or `**Experimental (implement).**` /
  `**Experimental (subclass).**` for the five gated types, with the sentence that calling and passing are
  Stable Core and the supplied defaults need no opt-in);
- the failure contract: `@throws IllegalArgumentException` for every `require`, `Result` content for every
  `Result` return, and "cancellation is thrown" for `execute`;
- value types: what equality is, whether bytes are copied, what `toString()` prints and never prints;
- the failure group types: "match the groups you care about and keep an `else`; new leaves may be added in
  a feature release";
- `Url.toString()`: "contains the query; use `redacted()` for logs and messages";
- `HttpMethod.of`, `Url.parse`: the failure's exception type and that its message never echoes the input.

**README.md** (working tree CRLF, index LF: edit with a tool that keeps the file's line endings, and check
`git diff --stat README.md` lists only the intended lines). Edits, with the sentence each repairs:

1. **`:3-4`** — "published as **three independent, single-concern artifacts** — depend only on the one you
   need:" becomes "published as **independent, single-concern artifacts** — depend only on the one you
   need:".
2. **Artifact table (after `:10`, the `webtools-browser` row)** — a fourth row:

   `| `io.github.spartanlaboratories:webtools-http` | `com.spartanlabs.webtools.http` | Kotlin Multiplatform (JVM target) — request/response types, a `Url` that reads like OkHttp's, a typed failure model and the transport and wall-clock ports of a suspend HTTP client stack; **not yet released** | okhttp-jvm, caffeine, kotlinx-coroutines-core, slf4j-api |`
3. **Install block (`:20-26`)** — inside `dependencies { … }`, after the browser line (`:25`):

   ```kotlin
       // not yet on Maven Central: webtools-http 1.0.0 is released when the HTTP client stack is complete
       implementation("io.github.spartanlaboratories:webtools-http:1.0.0")
   ```
4. **After "Requires JDK 11 or newer. Built with Kotlin 2.2." (`:29`)** — a paragraph:

   "`webtools-http` is a Kotlin Multiplatform module that publishes `webtools-http` and `webtools-http-jvm`;
   a Gradle build resolves the right one itself, a Maven build depends on `webtools-http-jvm`. It is not on
   Maven Central yet. Its `okhttp-jvm` 5.5.0 dependency requires `kotlin-stdlib` 2.2.21, so a consumer's
   Kotlin standard library resolves to 2.2.21 or newer."
5. **`:477`** — "the three modules are `:webtools-udp`, `:webtools-scraping`, `:webtools-browser`" becomes
   "the modules are `:webtools-udp`, `:webtools-scraping`, `:webtools-browser`, `:webtools-http` (Kotlin
   Multiplatform) and `:webtools-http-commoncheck` (unpublished)".
6. **Command block (`:481-495`)** — one new line after `verifyJavaFloor` (`:492`):
   `./gradlew :webtools-http-commoncheck:build  # compile webtools-http's commonMain for js (no Node.js needed)`,
   and, under the block, the sentence: "`./gradlew build` and `check` also compile every `*-commoncheck` module
   for `js`: an unpublished module that compiles a multiplatform module's `commonMain` so a JVM API in common
   code is a compile error. No Node.js is needed."
7. **`:497-501`** — the test-layout sentence becomes: "Each module organises its tests by the project's
   5-level testing hierarchy: a JVM module under `<module>/src/test/kotlin/com/spartanlabs/testing/<level>/webtools/<module>/`,
   a multiplatform module under `<module>/src/commonTest/kotlin/…` (runs on the JVM target) and
   `<module>/src/jvmTest/kotlin/…`, with the same package. A level task selects its tests by package
   (`com.spartanlabs.testing.<level>.*`) from the module's JVM test compilation, so any level can be run or
   gated on its own; a test class outside those packages runs under `test` only. Shared fixtures live under
   `testing/support/`."
8. **`:505` and `:514`** — "Every module compiles to Java 11 bytecode" becomes "Every JVM module compiles to
   Java 11 bytecode"; "checks every module without running any module's tests" becomes "checks every library
   module without running any module's tests".
9. **Plugin section (`:516-530`)** — "as three convention plugins" becomes "as four convention plugins"; the
   multiplatform row's "(no module uses it yet)" becomes "(used by `webtools-http`)"; a fourth row after it:

   `| `webtools.kotlin-common-check` | an unpublished, check-only `js` module that compiles a sibling's `commonMain` (no publishing, no tests, no Node.js, no Java floor) |`

No `### webtools-http` Components table is added; the later units' how-to and the final docs pass (unit 11)
write it.

**Other documents.** No change to `docs/webtools-udp-architecture.md` or `docs/webtools-udp-protocol.md`.

---

## 9. Test plan

**Conventions.** One test class per file; JUnit 5 through `kotlin.test` (`@Test` from `kotlin.test`,
which `kotlin-test-junit5` maps in `jvmTest`); backtick test names; no `@Tag` (the level tasks select by
package); no MockK (this unit has no external call to fake; the logging actual is tested against a real
logback appender). A class in `commonTest` uses only common APIs. Test sources never contain `@OptIn`
(§6.2). Path shorthand: a file written `CT <level>/X.kt` is
`webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/<level>/webtools/http/X.kt`, package
`com.spartanlabs.testing.<level>.webtools.http`; `JT <level>/X.kt` is the same under `src/jvmTest`.
`CT support/X.kt` is `webtools-http/src/commonTest/kotlin/com/spartanlabs/testing/support/webtools/http/X.kt`
(no tests, so no level in its name). Class names end in the level (`GatingTest`, `ComponentTest`,
`DeterministicTest`, `NonFunctionalTest`), as `build-logic`'s do.

### Level 1 — gating (`gatingTest`)

| File | Behaviours |
|---|---|
| `CT gating/GatedTypesConsumerGatingTest.kt` | The permanent consumer, compiled **without any opt-in**. It: names `HttpTransport`, `WallClock`, `HttpFailure`, `TransportFailure` and `UpstreamFailure` in function and constructor signatures; calls `WallClock.System.nowEpochMillis()` and reads its `toString()`; declares `fun now(clock: WallClock = WallClock.System)` and `class UsesClock(val clock: WallClock = WallClock.System)` and checks both return `WallClock.System`; constructs every public leaf (with and without a cause) and checks it `is` its group and `is HttpFailure`; runs a `when` over a failure with `is TransportFailure`, `is UpstreamFailure` and an `else`. It cannot implement or subclass any gated type, which is the point. |
| `JT gating/GatedTypesJvmConsumerGatingTest.kt` | The same constraints in `jvmTest`: gated types in signatures, `WallClock.System` as a default, the public leaves, the `internal` `RateLimited` constructor (visible to tests) in a `when` with `else`. Compiling it proves `jvmTest` is a consumer too. |

**Level 1 holds these two classes and no per-type smoke class.**
- The pre-commit gate of `webtools-http` is
  `./gradlew :webtools-http:gatingTest :webtools-http:componentTest :webtools-http:deterministicTest`,
  about ten seconds.
- Levels 2 and 4a already exercise every type in isolation: its construction, validation, equality and
  printing. A smoke class per type at Level 1 would repeat them and gate nothing they do not.
- What only Level 1 can show is that a consumer without the opt-in compiles against the public
  surface, and these two classes show it.

### Level 2 — component (`componentTest`)

| File | Behaviours |
|---|---|
| `CT component/WallClockSystemComponentTest.kt` | `nowEpochMillis()` is after 2024-01-01 (`1_704_067_200_000`); two reads are non-decreasing within 5 s of each other; `toString()` is `WallClock.System`; the object is a `WallClock` and a singleton |
| `CT component/FailureLeavesComponentTest.kt` | every leaf: exact constant message (§6.3 table); the public constructors with and without a cause (cause kept by identity, `null` without); fields; `is` relations (leaves in their group, groups in `HttpFailure`, `Closed`/`BudgetExhausted`/`DecodeFailure` in neither group); the `require`s (blank `component`/`host`/`rule`, negative `limit`/`declared`/`wait`/`retryAfter`, blank `window`, status outside 100..599) throw `IllegalArgumentException`; `RateLimited` through its `internal` constructor |
| `CT component/UpstreamErrorComponentTest.kt` | headers retained; the constructor copies the body (mutating the source array afterwards changes nothing); `body()` returns a fresh copy each time (mutating it changes nothing); `bodySize`; neither `message` nor `toString()` nor `cause` holds a canary placed in a header value or in the body |
| `CT component/ReasonConstantsComponentTest.kt` | `RefusalReason` has exactly its nine constants and `ExhaustedReason` its four; equal by name, same constant is `===`, hash agrees, `toString()` is the name, a `RefusalReason` never equals an `ExhaustedReason`; a `when` over a constant with `else` |
| `CT component/HttpHeadersComponentTest.kt` | `add`/`get`/`getAll` (last value, order, empty list when absent); `set` replaces every entry and appends at the end with the new spelling; `set` keeps a sensitive flag; `setSensitive`; `remove` (and a no-op); `addAll` keeps flags; the four credential names are `isSensitive` without a flag, in any case; a failed call changes nothing; a built value does not change when a leaked builder is used later; `entries()` is a copy; equality/hash/`toString()` (names and `*`, no values); `contains` |
| `CT component/HttpMethodComponentTest.kt` | the seven constants and `of` returning the same instances; `of("get")` is a distinct, unequal value; equality/hash/`toString()`; `permitsBody`; `of` failure is an `IllegalArgumentException` with the constant message; `CONNECT` and `TRACE` accepted by `of` |
| `CT component/RequestBodyComponentTest.kt` | bytes copied in and out; `size`; `contentType` null/valid/invalid (message holds the index, not the text); `toString()` shows size and `hasContentType` only |
| `CT component/HttpRequestComponentTest.kt` | factory defaults (empty headers, no body); `POST`/`PUT`/`PATCH`/`DELETE`/`OPTIONS` with and without a body; `GET`/`HEAD`/`TRACE` with a body refused with the method in the message; `CONNECT` refused; a non-sendable value refused naming the header, never the value; a `Content-Type` header (any case) beside a body content type refused; a request breaking two rules reports the first in §6.4's order; identity equality; `toString()` shows the redacted URL and header names, no value, no body |
| `CT component/HttpResponseComponentTest.kt` | status 99 and 600 refused, 100 and 599 accepted; defaults; the body is copied when built and `body()` is a fresh copy each call; `redirectChain` is a copy; `bodySize`; identity equality; `toString()` shows status, redacted URL, header names, `bodySize`, redirect count and no value or body |
| `CT component/RedirectHopComponentTest.kt` | status 299 and 400 refused, 300 and 399 accepted; value equality and hash; `toString()` hides a query canary |
| `CT component/ResultFlatMapComponentTest.kt` | success chains; failure short-circuits and the transform is not called; the failure's exception instance is passed through unchanged |
| `JT component/Slf4jLogComponentTest.kt` | with a logback `ListAppender` on a uniquely named logger raised to `DEBUG`: `debug`/`info`/`warn` reach slf4j at their level with the logger name; `{}` placeholders are substituted; a trailing `Throwable` is the event's throwable; at `INFO` a `debug` call produces no event; the appender and level are restored in `finally` |
| `JT component/CatchNonFatalComponentTest.kt` | under `runBlocking`: a value becomes a success; an `IOException` becomes a failure holding that instance; a `CancellationException` thrown inside is rethrown; `withTimeout(10) { catchNonFatal { delay(1_000) } }` throws `TimeoutCancellationException` (the helper surrounds a suspend call and does not swallow cancellation); an `AssertionError` propagates |
| `JT component/EpochMillisJvmComponentTest.kt` | `epochMillisNow()` is within 1 s of `System.currentTimeMillis()` read before and after |

### Level 3 — integration

None in `webtools-http`: the module has no process, network or file boundary. Level 3 is `build-logic`'s
TestKit class for the plugin (§7.2): `CommonCheckPluginIntegrationTest`, seven tests, in
`com.spartanlabs.testing.integration.webtools.buildlogic`.

**The plugin is covered here, not in `build-logic`'s Level 4b composite.**
- `IncludedBuildEndToEndTest` keeps its two modules: one on `webtools.kotlin-jvm-library` and one on
  `webtools.kotlin-multiplatform-library`. Its KDoc names `CommonCheckPluginIntegrationTest` as the
  plugin's coverage.
- What the composite proves for every precompiled plugin (resolution through
  `pluginManagement { includeBuild("build-logic") }`, the Kotlin Gradle plugin loaded once,
  `WebToolsBuild` visible to module scripts) does not change with one more plugin from the same build
  and the same plugin marker.
- The included-build path of `webtools.kotlin-common-check` itself is exercised by the repository's own
  build: `:webtools-http-commoncheck` applies it on every `./gradlew build` and `check` (P7).

### Level 4a — deterministic

`commonTest` (pure input-to-output tables; the corpus is §9.1):

| File | Behaviours |
|---|---|
| `CT deterministic/UrlAcceptDeterministicTest.kt` | every `UrlCorpus.accept` entry: canonical `toString()`, and for a sample of entries `scheme`, `host`, `port`, `encodedPath`, `encodedQuery` |
| `CT deterministic/UrlRejectDeterministicTest.kt` | every `UrlCorpus.reject` entry fails with the expected `UrlProblem` (read from the `UrlParseException`), the failure is an `IllegalArgumentException`, and its message is the kind's constant |
| `CT deterministic/UrlConfusionDeterministicTest.kt` | the confusion strings of §9.1 with their expected canonical text or kind |
| `CT deterministic/UrlBoundaryDeterministicTest.kt` | host 253 accepted and 254 rejected (with and without a trailing dot); label 63 accepted and 64 rejected; port 1 and 65535 accepted, 0 and 65536 rejected; the trim set (each of TAB, LF, FF, CR, SP at either end accepted; VT, NUL, NBSP at either end rejected) |
| `CT deterministic/UrlIpv6DeterministicTest.kt` | the IPv6 table of [`url-parsing.md`](research/url-parsing.md) F6: forms, RFC 5952 output, tie and single-zero rules, mapped addresses, NAT64 and IPv4-compatible staying IPv6, zones and malformed forms rejected, `[1:2:3:4:5:6:7::8]` rejected |
| `CT deterministic/UrlPrintingDeterministicTest.kt` | over the accepted corpus: `Url.parse(u.toString())` equals `u`; `redacted()` equals `toString()` without a query and otherwise ends `?...` with the length of `toString()` minus the query plus 3; equality and hash on canonical text (`HTTP://H:80/a/../b#x` equals `http://h/b`; `%2f` unequal to `%2F`; `127.1` unequal to `127.0.0.1`; `h` unequal to `h.`) |
| `CT deterministic/UrlEncodersDeterministicTest.kt` | `encodePathSegment` and `encodeQueryComponent` on all 128 ASCII characters (raw sets of §6.5), non-ASCII as upper-case UTF-8, `.` and `..` failing with `DotSegment`, `%2e` becoming `%252e`, `~` becoming `%7E` in a query, an unpaired surrogate failing with `NotUnicode` |
| `CT deterministic/HttpHeaderNameDeterministicTest.kt` | a sweep over U+0000..U+FFFF: the one-character name `c` is accepted by the builder iff `c` is one of the 77 `tchar`; the fixed rows of [`header-and-method-validation.md`](research/header-and-method-validation.md) (names); the empty name; messages |
| `CT deterministic/HttpHeaderValueDeterministicTest.kt` | the container rule (the research table, including stored trimmed values `v`, `v`, `` for ` v `, `\t v \t`, `   `); a sweep over U+0000..U+FFFF in `"a" + c + "b"` for the container rule (CR, LF, NUL refused) and for the sendable rule (iff `c == '\t'` or `c in ' '..'~'`); `sanitizeReceivedValue` rows |
| `CT deterministic/HttpSyntaxDeterministicTest.kt` | ASCII-only fold (`get("Key")` with U+212A does not find `key`; `"ı"` does not find `i`; `CONTENT-TYPE` finds `Content-Type`; the empty lookup is `null`); `trimOws`; `isCredentialName` in any case |
| `CT deterministic/HttpMethodTokenDeterministicTest.kt` | the accepted and refused rows of the research table for `HttpMethod.of` |
| `CT deterministic/StatusRangeDeterministicTest.kt` | `HttpResponse` statuses `99, 0, -1, 600, 999, 1000` refused and `100, 101, 199, 200, 204, 299, 304, 399, 404, 499, 500, 599` accepted; `RedirectHop` `299, 400` refused and `300, 301, 302, 303, 307, 308, 399` accepted |
| `CT deterministic/MediaTypeDeterministicTest.kt` | the accepted and refused rows of the research's content-type table; `text/plain;;` and `text/plain ;` **accepted** (RFC 9110 `parameters = *( OWS ";" OWS [ parameter ] )`: an empty parameter and OWS before `;` are valid); `text/plain; ` **refused** with failing index 11 (a field value carries no trailing whitespace, RFC 9110 §5.5); the failing index of every refused row |

`jvmTest` (OkHttp as the oracle; `okhttp-jvm` is on the `jvmMain` classpath):

| File | Behaviours |
|---|---|
| `JT deterministic/UrlOkHttpDifferentialDeterministicTest.kt` | the assertion and generator of §9.1; 200,000 generated inputs plus the whole corpus in the build |
| `JT deterministic/UrlEncoderDifferentialDeterministicTest.kt` | 200,000 strings: `encodePathSegment(s)` equals `HttpUrl.Builder().scheme("http").host("h").addPathSegment(s).build().encodedPath` minus its leading `/` (fails instead for `.`, `..` and unpaired surrogates); `encodeQueryComponent(s)` equals the name part of `addQueryParameter(s, "v")` and the value part of `addQueryParameter("k", s)` (fails for unpaired surrogates) |
| `JT deterministic/HeaderOkHttpDifferentialDeterministicTest.kt` | sendable value iff `Request.Builder().url("http://h/").addHeader("X", v)` does not throw, and `header("X")` equals the stored value, over the same sweep; every name `HttpSyntax.isToken` accepts is accepted by `Request.Builder.addHeader`; the documented gap pinned (OkHttp accepts `:path`, `a(b`, `a/b`, `a"b`, the types refuse them) |

### Level 4b — end-to-end

None. This unit contains no flow across components and no I/O: the first end-to-end path (cache, budget
and transport over loopback) exists only when units 6 and 9 land, and is theirs.

### Level 4c — non-functional

| File | Behaviours |
|---|---|
| `CT nonfunctional/UrlParseSecrecyNonFunctionalTest.kt` | a canary (`SECRET-CANARY-7f3a`) is placed in every position of a URL that can fail (scheme, user-info, host, port, path, query, fragment; every failure kind): no failure message and no `toString()` of a failure contains it; every message is one of the eleven constants |
| `CT nonfunctional/UrlParseGarbageNonFunctionalTest.kt` | 50,000 inputs from `UrlInputGenerator` (seed 11): `Url.parse` never throws; every success re-parses from its `toString()` to an equal `Url` |
| `CT nonfunctional/UrlParseLinearTimeNonFunctionalTest.kt` | inputs of 64k and 1M characters in five shapes (a long path of `/..` segments, a long query, a long host label, a million `%`, a long run of `[`): each parses without exception, and the time for 16× the length is under 100× the time for 1× (a quadratic parse gives about 256×) after a warm-up parse |
| `CT nonfunctional/HeaderMessageSecrecyNonFunctionalTest.kt` | a canary in a header value, in a name position (a swapped argument), in a content type, in a method string and in a URL query: no `IllegalArgumentException` message from `HttpHeaders.Builder`, `HttpRequest`, `RequestBody`, `HttpMethod.of` or `HttpResponse` contains it; a name is echoed only after passing the token rule |
| `build-logic` `…nonfunctional/webtools/buildlogic/CommonCheckConfigurationCacheReuseNonFunctionalTest.kt` | the second identical `build` reuses the configuration cache (§7.2) |

### Level 5 — user acceptance (manual; procedure P10)

No `testing.uat` package: nothing here is interactive product behaviour. The pass is a reading and an IDE
check recorded in `final-implementation.md` §6.

### What cannot be tested automatically

- **IDE behaviour:** that IntelliJ imports `webtools-http-commoncheck`, resolves the shared source
  directory and the `jsMain` actuals, and shows the gate error and its quick fix (P10).
- **The gate staying in place:** the ABI dump does not record `@SubclassOptInRequired`, and no permanent
  check compiles an implementer without the opt-in. The eight negative cases are run once (P3) and their
  text is recorded; making them a permanent compile-fail check is the follow-up of §13.
- **KDoc quality**, beyond the mechanical coverage check of P9.
- **The "JS Environment Not Selected" warning's future:** a Kotlin Gradle plugin release that makes it an
  error fails the plugin's TestKit `build` test, which is the alarm, but is not simulated.
- **Other hosts:** only the maintainer's Windows host with a JDK 17 to 26 build JDK is exercised.

### 9.1 The `Url` corpus, generator and differential

**Placement.** `CT support/UrlCorpus.kt` and `CT support/UrlInputGenerator.kt` (both `object`s in
`commonTest`, so the common tables can use them without OkHttp, and `jvmTest` sees them).

**The assertion**, for every input `s` (the raw string, untrimmed: both parsers trim the same five
characters):
1. `mine = Url.parse(s)`, **not** wrapped in a `try`: a throw is a test failure.
2. `theirs = try { s.toHttpUrlOrNull() } catch (t: Throwable) { null }` — `HttpUrl` rejects either by
   returning `null` or, for the known crash, by throwing; both mean "rejects".
3. If `mine` failed: nothing more to check (stricter is allowed); count it by kind.
4. If `mine` succeeded: `theirs` must not be `null`, and `u.scheme == theirs.scheme`,
   `u.host == theirs.host`, `u.port == theirs.port`, `u.encodedPath == theirs.encodedPath`,
   `u.encodedQuery == theirs.encodedQuery`; `theirs.encodedUsername` and `encodedPassword` are empty;
   `u.toString() == theirs.newBuilder().fragment(null).build().toString()`; `Url.parse(u.toString())`
   succeeds and equals `u`; `u.redacted()` equals `u.toString()` when `encodedQuery == null`, else ends
   `?...` and has the length of `toString()` minus the query plus 3.
5. A failure message names the seed and the input, so a disagreement is reproducible.

**The corpus** (`UrlCorpus`): four lists; an entry carries its input and its expected canonical text or
`UrlProblem`. The input lists are those of [`url-parsing.md`](research/url-parsing.md) "The Level 4a
differential test → The corpus", each entry expanded to one string per character-and-position combination
where the research describes a pattern. The expected kind of a reject entry is the first failing step in
§6.5's order (the research's group labels are indicative: `http://::1/` fails at the port step, so it is
`Port`; where they differ §6.5 governs).

| List | Entries |
|---|---|
| `accept` | the 26 listed in the research plus: `http://[::]/` unchanged; `http://[1:2:3:4:5:6:7::]/` → `http://[1:2:3:4:5:6:7:0]/`; `http://[::1.2.3.4]/` → `http://[::102:304]/`; `https://h:80/` and `http://h:443/` unchanged (non-default ports kept); `http://h/p%e9` unchanged; `http://h/?a=b+c` unchanged |
| `reject` | by kind — `Empty`: `""`, `"   "`, `"\t\n"`; `Scheme`: `example.com/x`, `//example.com`, `ftp://h/`, `file:///etc/passwd`, `javascript:alert(1)`, `http:/h`, `http:h`, `httpſ://h/`, `https` + U+212A + `://h/`; `Host`: `http://`, `http:///h`, `http://a..b/`, `http://.a/`, `http://a!b/`, `http://a"b/`, `http://a,b/`, a 64-character label, a 254-character host, `http://[::1/`, `http://[1:2:3:4:5:6:7::8]/`, `http://[::1]x/`, `http://[g::1]/`, `http://[]/`, `http://[1.2.3.4]/`, `http://[::1.2.3.256]/`, `http://[12345::]/`, `http://[fe80::1%25eth0]/`; `UserInfo`: `http://u@h/`, `http://u:p@h/`, `http://@h/`, `http://:@h/`, `http://h@/`, `http://a@b@c/`; `Backslash`: `\` in the authority, path, query and fragment; `WhitespaceOrControl`: space, TAB, LF, NUL, VT, DEL, U+0085, U+00A0, U+3000 in the path, query and fragment, and (for NUL, VT, DEL, U+0085, U+00A0, U+3000) at either end; `NotUnicode`: a lone U+D800 and a lone U+DC00 in the path, query and fragment; `HostEncoding`: `münchen.de`, `%31%32%37.0.0.1`, `127.0.0.1%09.evil.example`, `a%2eb`, fullwidth `ｌocalhost`, `。`-separated and fullwidth-digit hosts; `HostIdn`: `xn--n3h.net`, `XN--N3H.net`, `xn--abc-`, `a.xn--b.c`; `Port`: `http://h:0/`, `:65536`, `:+80`, `:-1`, `:8o`, `:80:80`, fullwidth `８０`, `:99999999999`, and `http://::1/` |
| `confusion` | the table below |
| `crash` | `http://[1:2:3:4:5:6:7::8]/` and generated siblings (seven groups, `::`, one or more groups): `Url` rejects each; `HttpUrl` throws |

Confusion strings and their expected result (derived from §6.5; the differential is the oracle):

| Input | Expected |
|---|---|
| `http://evil.example\@127.0.0.1:80/`, `http://127.0.0.1:80\@evil.example/` | `Backslash` |
| `http://127.0.0.1。evil.example/`, `http://１２７.0.0.1/`, `http://127.0.0.1%09.evil.example/`, `http://localhost%2e/`, `http://%31%32%37.0.0.1/`, `http://ｌｏｃａｌｈｏｓｔ/`, `http://ⓛocalhost/` | `HostEncoding` |
| `http://127.1/`, `http://0x7f.1/`, `http://169.254.169.254/`, `http://localhost./` | accepted, host as written |
| `http://127.0.0.1#@evil.example/` | `http://127.0.0.1/` |
| `http://evil.example#@127.0.0.1/` | `http://evil.example/` |
| `http://google.com#@evil.com/` | `http://google.com/` |
| `http://evil.example?@127.0.0.1/` | `http://evil.example/?@127.0.0.1/` |
| `http://[::ffff:127.0.0.1]/`, `http://[::ffff:7f00:1]/` | `http://127.0.0.1/` |
| `http://[::ffff:a9fe:a9fe]/` | `http://169.254.169.254/` |
| `http://foo@evil.com:80@google.com/`, `http://example.com／@127.0.0.1/` (fullwidth solidus) | `UserInfo` |
| `http://[::1]%25eth0/` | `Host` |
| `/%2e%2e/`, `:0`, `:65536`, `:80:80` | `Scheme` (no scheme) |
| `http://h:0/`, `http://h:65536/`, `http://h:80:80/` | `Port` |
| the slash, scheme and `xn--` rows of [`url-parsing.md`](research/url-parsing.md) F2 to F5 | as that table, in §6.5's kinds |

**The generator** (`UrlInputGenerator`, `kotlin.random.Random(seed)`; the seed is printed in a failure
message). Seeds: 7 for the differential, 11 for the garbage test. Weights: structured about 70%, mutation
about 20%, raw random about 10%.
- **Structured** — half *clean* (a valid scheme, `://`, no user-info, a valid host and port, so deep path
  and query cases are reached), half *hostile*. Parts: scheme from `http https HTTP HtTps ftp htps httpſ ＨＴＴＰ`
  and `https` + U+212A and `İttp` and empty; separator from `:// :/ : :/// :////` and `:\\` `//` `:/\` and empty;
  user-info from `u@ u:p@ @ :@ a@b@ x:80@` or none; host from a pool (ordinary names, `127.1`, `0x7f.1`,
  `2130706433`, labels of 63 and 64, 250-character names, `xn--` forms, IDN, percent-encoded, fullwidth, every
  IPv6 form of the F6 table including the crash pattern) or a *random IPv6 generator* (random groups with zero
  runs, an optional `::` at a random place, an optional dotted IPv4 suffix with 10% out-of-range octets, mapped
  forms, random upper case, random leading zeros); an optional port from a valid list or `0..99999`, signs, or
  empty; a path of 0 to 5 segments from `. .. %2e %2E .%2e %2e. %2e%2e a b %20 é 日本 😀 % %zz %41 %e9 {x} a|b ^ \` <> " ' [x] \uD800 ...a a.. ... ~ @ : & = + %2F %5C %252e 100%`;
  a query of 0 to 4 tokens from every URL-significant character (`& = + % %zz é 😀 ' " < > { | } [ ] ^ \` \ space TAB # ? ~ * @ ; : / $ !`) and unpaired surrogates;
  an optional fragment from the same pools; optional surrounding whitespace from `space TAB LF CR LF FF VT NBSP`.
- **Mutation** — take a corpus string and apply 1 to 3 random inserts, deletes or replacements drawn from the
  URL-significant ASCII set (`/\?#@:[]%.&=+ -_~!$'()*,;<>^\`{|}"`, TAB, LF, VT, NUL, DEL) and a Unicode pool
  (`é ß 日 本 😀 ☃ ſ İ ＨＴＴＰ １ 。 ． ｡ ⓛ`, and by code point U+212A (KELVIN SIGN), U+00A0, U+2028, U+3000, U+0085, U+200B, U+FEFF, lone surrogates, Arabic-Indic digits).
- **Raw random** — 0 to 30 characters from the significant set, letters, digits, the Unicode pool, fragments
  (`xn--`, `%2e`, `::`, `/./`, `/../`), control characters, random BMP code units (surrogates included) and
  supplementary code points, prefixed with `http://`, `https://`, `HTTP://` or `http:/` in 70% of cases.

**Run size.** 200,000 generated inputs in the build. The environment variable
`WEBTOOLS_DIFFERENTIAL_INPUTS` (read with `System.getenv`) raises it for a multi-million run; an environment
variable and not a system property, because Gradle passes no `-D` option into the test JVM and the level
tasks forward none. A different size scales the floors below linearly.

**Coverage floors** (so a generator regression cannot make the test vacuous), for 200,000 inputs: at least
25% accepted by `Url`; at least 1,000 accepted inputs each containing `[`, a dot segment, `?`, a percent
escape and a non-ASCII character (five separate counts); at least 100 inputs on which `HttpUrl` throws; at
least one rejection of every `UrlProblem` kind except `DotSegment`.

**Mutation sensitivity** (executed once, P4; not committed): the differential is run against four deliberately
broken `Url` variants, each of which it must catch within 300,000 inputs — `startsWith(ignoreCase = true)` for
the scheme (caught on `httpſ://[::1]`); `xn--` labels kept as written (caught on `xn--abc-`); eight IPv6 groups
allowed with `::` (caught on `[1:2:3:4:5:6:7::8]`); `.%2e` removed from the double-dot forms (caught on
`/a/.%2e/b`).

### 9.2 Procedures

Every command is run from the repository root. None runs a `webtools-udp`, `webtools-scraping` or
`webtools-browser` suite, and none runs the root `build` or `test`. The first `build-logic` nested-build run
needs the network. Record each command's result in `final-implementation.md`.

**P1 — baseline and branch.** `git branch --show-current` is `feat/issue-62-http-core`; `git log --oneline`
shows only the research commits on top of `f72b115`; `git status --short` shows nothing but plan-board
files kept by the admin team's presenter (§11). `./gradlew :build-logic:test` passes (the baseline for the edits to `BuildLogicPluginsGatingTest`).

**P2 — the plugin (S1).**
1. `./gradlew :build-logic:test` passes with the new plugin id in the gating list.
2. `./gradlew :build-logic:integrationTest --tests "*CommonCheckPluginIntegrationTest*"`: seven tests pass.
3. `./gradlew :build-logic:nonfunctionalTest --tests "*CommonCheckConfigurationCacheReuseNonFunctionalTest*"` passes.
4. Then, one after another (the new factory and plugin set change what these tests build, so the full suites
   are justified): `./gradlew :build-logic:integrationTest`, `./gradlew :build-logic:e2eTest`,
   `./gradlew :build-logic:nonfunctionalTest`.
5. `git status --short build-logic` lists the plugin, the three test files and the sample factory, and no
   file under `build-logic/build/`.

**P3 — the opening compile proof (S2).** Run it after the S2 files exist and before S3. Every command prints
its full output into `final-implementation.md` §3.
1. **Main compiles.** `./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs`
   — `BUILD SUCCESSFUL`, no gate diagnostic. Expected warnings: the Gradle-10 `archives` deprecation and one
   `JS Environment Not Selected`; anything else is recorded.
2. **The wiring canary.** Add three throwaway files:
   - `webtools-http/src/commonMain/kotlin/com/spartanlabs/webtools/http/ProofClock.kt`:
     `package com.spartanlabs.webtools.http` / `public class ProofClock : WallClock { override fun nowEpochMillis(): Long = 0 }`;
   - `webtools-http/src/jvmMain/kotlin/com/spartanlabs/webtools/http/ProofTransport.kt`: `public class ProofTransport : HttpTransport { override suspend fun execute(request: HttpRequest): Result<HttpResponse> = Result.failure(HttpFailure.Closed("proof")) }`;
   - `webtools-http-commoncheck/src/jsMain/kotlin/com/spartanlabs/webtools/http/ProofJsClock.kt`: `public class ProofJsClock : WallClock { override fun nowEpochMillis(): Long = 0 }`.

   a. Compile both tasks again: `BUILD SUCCESSFUL` (the wiring works).
   b. Keep scratch copies of the two build files, then delete the four `languageSettings.optIn(gateMarker)`
      lines (two in each). `./gradlew :webtools-http:compileKotlinJvm :webtools-http-commoncheck:compileKotlinJs --continue`
      — both tasks `FAILED`: `compileKotlinJvm` with exactly two errors (`ProofClock.kt` in `commonMain`,
      `ProofTransport.kt` in `jvmMain`) and `compileKotlinJs` with exactly two (the shared `ProofClock.kt` and
      `ProofJsClock.kt`), each "This class or interface requires opt-in to be implemented", carrying the
      marker's message when the literal is read in-module. Record the exact text; if the in-module text is the
      compiler's default (no message), record it as a deviation.
   c. Restore the lines (`diff` against the scratch copies is empty), delete the three files (`git status`
      shows no `Proof*`), and compile both tasks once more: `BUILD SUCCESSFUL`.
3. **The positive consumer cases.** `./gradlew :webtools-http:compileTestKotlinJvm` — `BUILD SUCCESSFUL`, no
   warning about the gate; `./gradlew :webtools-http:gatingTest` — both Level 1 classes pass.
4. **The eight negative cases.** Add, in `commonTest`, `…/testing/gating/webtools/http/NegativeCases.kt`
   (package `com.spartanlabs.testing.gating.webtools.http`, `import com.spartanlabs.webtools.http.*`):

   ```kotlin
   class N1Clock : WallClock { override fun nowEpochMillis(): Long = 0 }
   class N2Delegating(c: WallClock) : WallClock by c
   val n3 = object : HttpTransport { override suspend fun execute(request: HttpRequest): Result<HttpResponse> = TODO() }
   class N4Failure : HttpFailure("x")
   class N5Transport : TransportFailure("x")
   class N6Upstream : UpstreamFailure(500, "x")
   val n7 = object : WallClock { override fun nowEpochMillis(): Long = 0 }
   class N8Transport : HttpTransport { override suspend fun execute(request: HttpRequest): Result<HttpResponse> = TODO() }
   ```

   `./gradlew :webtools-http:compileTestKotlinJvm` fails with **exactly eight** `e:` lines
   (`… | grep -c '^e: '`), each the consumer text of §6.2, each at the supertype reference, and no other
   diagnostic. Prefix every declaration with `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`: the compile
   succeeds with no `e:` and no `w:`. Move the file to `jvmTest` (same path under `JT`) and repeat both
   results. Delete the file.
5. Append steps 1–4's outputs to `final-implementation.md` §3. A result that differs from this plan stops S3.

**P4 — the `Url` differential (S3/S4).**
- Default size: `./gradlew :webtools-http:deterministicTest` passes, including the three differentials and the
  coverage floors.
- Extended size, for the maintainer's confidence and the as-built record (seeds 101 and 202 are the ones the
  research measured; the seed is a constant in the test, change it for each run and record that you did):
  `WEBTOOLS_DIFFERENTIAL_INPUTS=2000000 ./gradlew :webtools-http:deterministicTest --tests "*UrlOkHttpDifferentialDeterministicTest*" --rerun`
  (`--rerun` because the task's inputs are unchanged). Expected: 0 disagreements; record the counts by kind.
- Mutation sensitivity: apply each of the four variants of §9.1 to a scratch copy of `Url`, run the
  differential, expect failure naming the planted input, and restore the file.

**P5 — the module's tests.** `./gradlew :webtools-http:gatingTest :webtools-http:componentTest
:webtools-http:deterministicTest :webtools-http:nonfunctionalTest` for the fast loop, then
`./gradlew :webtools-http:check :webtools-http-commoncheck:check` (the first needs `api/webtools-http.api`;
run P6 step 1 first). Record the test counts per level. No warning other than the two named in P3 step 1.

**P6 — the dump, the class files, the published shape (S5).**
1. `./gradlew :webtools-http:apiDump`, then read `webtools-http/api/webtools-http.api` against §7.8 and the
   architecture §9 table; `./gradlew :webtools-http:apiCheck` passes. Record the line endings
   `apiDump` wrote (`file webtools-http/api/webtools-http.api`); they do not affect `apiCheck`, which
   compares line by line (§3).
2. `javap -v -p -cp webtools-http/build/classes/kotlin/jvm/main com.spartanlabs.webtools.http.WallClock` and
   likewise `HttpTransport`, `HttpFailure`, `TransportFailure`, `UpstreamFailure`: each shows
   `RuntimeInvisibleAnnotations` with `kotlin.SubclassOptInRequired(markerClass=[class Lcom/spartanlabs/webtools/http/ExperimentalForInheritanceWebToolsApi;])`;
   `WallClock$System` and `HttpFailure$Closed` show none; the marker class shows `kotlin.RequiresOptIn(message=…, level=…ERROR)`
   with the full message; `major version: 55` on `WallClock`.
3. `./gradlew :webtools-http:generatePomFileForJvmPublication :webtools-http:generateMetadataFileForJvmPublication`
   (no signing is involved), then read `webtools-http/build/publications/jvm/pom-default.xml` and `module.json`:
   `kotlinx-coroutines-core` is `compile` scope / in the API variant; `okhttp-jvm`, `caffeine` and `slf4j-api`
   are `runtime` scope / in the runtime variant only; `org.gradle.jvm.version` is 11 on both library variants.
   `./gradlew :webtools-http:verifyJavaFloor` passes (it is also in `check`).

**P7 — the check module and the root.** `./gradlew :webtools-http-commoncheck:build` passes; run it twice with
`--configuration-cache` and the second prints `Reusing configuration cache`. `ls kotlin-js-store` fails and
`git status --short` shows no `kotlin-js-store/`; `./gradlew :webtools-http-commoncheck:build --dry-run` lists
no Node.js, Yarn, npm or `PackageJson` task. `./gradlew :check` fails with "Task 'check' not found in root
project 'WebTools'" (the root still has no `check`). Introduce a JVM API into a throwaway file in
`webtools-http/src/commonMain` (for example `internal val f = java.io.File("x")`): `:webtools-http:compileKotlinJvm`
succeeds and `:webtools-http-commoncheck:compileKotlinJs` fails with `Unresolved reference`; remove the file.

**P8 — the unaffected modules.** `git diff --stat -- webtools-udp webtools-scraping webtools-browser` is empty;
`git status --short build-logic/src/main` lists only the new plugin script (no existing script is modified);
`./gradlew :webtools-udp:compileKotlin :webtools-scraping:compileKotlin :webtools-browser:compileKotlin`
succeeds (a compile, no test).

**P9 — mechanical rules.** Each command prints nothing unless it says otherwise. A comment may name a
forbidden API, so the first four commands drop comment lines with `| grep -vE ':[0-9]+:[[:space:]]*(//|\*|/\*)'`
(shown once; append it to each).
- `grep -rnE "java\.|javax\.|@Jvm|runCatching|\.lowercase\(|\.uppercase\(|ignoreCase|removeFirst|removeLast|String\.format|Regex\(" webtools-http/src/commonMain | grep -vE ':[0-9]+:[[:space:]]*(//|\*|/\*)'`
- `grep -rn "catch" webtools-http/src/commonMain webtools-http/src/jvmMain` (comments dropped) prints only
  `NonFatal.kt`.
- `grep -rn "data class" webtools-http/src/commonMain webtools-http/src/jvmMain` (comments dropped) prints nothing
  (no `data class` at all, public or not).
- `grep -rn "TODO" webtools-http/src webtools-http-commoncheck/src` prints nothing.
- `grep -rn "OptIn" webtools-http/src/commonTest webtools-http/src/jvmTest` (comments dropped) prints nothing.
- `grep -nE "\b(Log|HttpSyntax|UrlProblem|UrlParseException|ParsedUrl)\b|LogKt|Slf4jLog" webtools-http/api/webtools-http.api`
  prints nothing.
- `grep -c '\$default' webtools-http/api/webtools-http.api` prints 3.
- KDoc coverage: for each file under `webtools-http/src/commonMain` and `jvmMain`, list the `public` and
  `protected` declaration lines whose previous non-blank line is neither the end of a KDoc block (`*/`) nor an
  annotation; the list must be empty. One way:
  `awk 'FNR==1{prev=""} /^[ \t]*(public|protected)[ \t]/ && prev !~ /\*\/[ \t]*$/ && prev !~ /^[ \t]*@/ {print FILENAME ":" FNR ": " $0} $0 !~ /^[ \t]*$/ {prev=$0}' <files>`.
- The logback blob: `git hash-object webtools-http/src/jvmTest/resources/logback-test.xml` equals
  `git rev-parse HEAD:webtools-udp/src/test/resources/logback-test.xml`.

**P10 — the Level 5 pass (S7).** Read, and record in `final-implementation.md` §6:
1. In an IDE that imports Gradle (IntelliJ): the repository imports; `webtools-http-commoncheck` resolves the
   shared directory and the `jsMain` actuals; no spurious red in `jsMain`.
2. A scratch class (not committed) that implements `WallClock` without the opt-in shows the gate error and the
   `@OptIn` quick fix, with the text recorded in P3.
3. The dump read against §7.8 and the architecture §9 tier table.
4. Quick Documentation of `Url.parse`, `Url.toString`, `HttpTransport`, `HttpFailure` and
   `ExperimentalForInheritanceWebToolsApi` as a consumer sees them: tier, contract and the query warning present.
5. `git status`/`git diff --stat` before each commit: no `Proof*`, no `NegativeCases`, no `kotlin-js-store/`, no
   `build-logic/build/`, no IDE files.

---

## 10. Risks and edge cases

- **The real plugin versus the research probe.** The opt-in and check-module results were measured with a
  simplified convention plugin; the real `webtools.kotlin-multiplatform-library` adds publishing,
  binary-compatibility-validator and the level tasks. The compile proof (P3) runs in the real module before
  anything else is built, so a difference shows at once. If it does, S3 does not start.
- **Kotlin 2.2.0 on Gradle 9.7.1 is outside Kotlin's documented range** (architecture §12). `webtools-http`
  is the first module to run the multiplatform plugin end to end (`apiDump`, `generatePomFile…`, the level
  tasks over `jvmTest`); anything those tasks reveal that the TestKit samples did not is a deviation to record.
  The `archives` deprecation (KT-78620) means builds are never run with `--warning-mode=fail`.
- **`JS Environment Not Selected`.** A Kotlin Gradle plugin release that turns it into an error fails the
  plugin's TestKit `build` test; the fallback is in the plugin's header. The warning is printed once per
  configuration-cache miss, however many check modules exist.
- **Public names that the architecture does not fix.** This plan chooses `HttpHeaders.Entry`/`Builder`,
  `RequestBody.size`/`bytes()`, `HttpResponse.bodySize`/`body()`, `HttpRequest.Builder.headers`/`body`,
  `HttpResponse.Builder.headers`/`redirectChain`/`body(bytes)`, `Closed.component`, `InvalidRequest.rule`,
  `UpstreamError.bodySize`/`body()`, and the `RedirectHop` meaning. `webtools-http` is unreleased, so each can
  still change before 1.0.0 is published; the committed dump is regenerated, not versioned.
- **Information leaving through a failure.**
  - Messages are constants plus non-sensitive fields. A `cause` is not covered: OkHttp exceptions can carry
    hosts, resolved addresses or URLs in their messages. A producer decides what it attaches (§12, unit 6).
  - `Url.toString()` carries the query. Any `"$url"` in a log line, message or `Result` leaks it; only
    `redacted()` is for logs, and it shows the **path**, which some APIs use for secrets. Both are in the KDoc.
  - `HttpHeaders`, `HttpRequest`, `HttpResponse` and `RequestBody` never print values or bodies; the L4c tests
    pin it.
- **Parser disagreement.** `Url` is stricter than `HttpUrl` by construction and the differential pins
  agreement for OkHttp 5.5.0; the files it depends on are byte-identical at 5.5.1. A later OkHttp that changes a
  canonicalisation (or fixes the IPv6 crash) shows up as a differential failure, to be decided per change. The
  `ArrayIndexOutOfBoundsException` on `[1:2:3:4:5:6:7::8]` is an OkHttp defect in a repository outside the
  Spartan Labs organisations; no issue is filed.
- **Equality is on bytes, not resources.** `%2f` and `%2F`, `%41` and `A`, `127.1` and `127.0.0.1`, `h` and `h.`
  are different `Url`s. A cache key built from `toString()` could hold two entries for one resource; the facades'
  key uses `encodedPath` and sorted parameters, and the encoders only ever emit upper-case hex.
- **Performance.** `Url.parse` is linear (`UrlParseLinearTimeNonFunctionalTest`). `HttpResponse.body()` and
  `RequestBody.bytes()` copy on every call: a 5 MiB body read by N callers is N copies; the facades read it
  once to decode. Building a `Url` stores its text twice (`withoutQuery` and `text`).
- **Concurrency.** Every value type is immutable after construction. Only the builders are mutable; they are
  documented as not thread-safe and used only inside the factory's lambda. `WallClock.System`, `logger` and the
  helpers hold no state. `HttpTransport.execute` is documented as callable concurrently.
- **Name clashes in the flat package.** The siblings add files to the same package. The names this unit takes
  are in §12; the leaves are nested so `Timeout`, `Protocol` and `Io` never become package names.
- **Serialisation.** Failure types hold `Duration`, `HttpHeaders` and `ByteArray`; Java serialisation of them is
  not supported and the KDoc does not promise it.
- **Flaky timing.** `UrlParseLinearTimeNonFunctionalTest` compares a 16× longer input against a 100× bound after
  a warm-up (quadratic is about 256×); `WallClockSystemComponentTest` bounds two reads by 5 s. A failure of either
  on a loaded host is rerun once before it is treated as a defect.
- **Line endings.** `README.md` and `logback-test.xml` are CRLF in the working tree and LF in the index on this
  host; the plan's checks compare blobs, not working files. The `.api` file's endings do not matter:
  `apiCheck` compares line by line (§3).
- **Existing consumers.** None change: the unit adds a module and a plugin, edits no source file of the three
  existing modules, and moves no version. No wire or schema format is involved. Cross-repo: none in this change;
  `SpartanLaboratories/KotlinBottools` reaches the stack after the whole stack is released.

---

## 11. Version control

- **Branch:** `feat/issue-62-http-core`. It exists and holds the research commits; the executor confirms it
  (P1) rather than cutting it again.
- **Commits.** Each is coherent and leaves the build green. The type and the split are the manager's final call;
  subject lines carry `(Issue #62)`; **no attribution trailers**.
  1. `build: add the webtools.kotlin-common-check plugin and its TestKit checks (Issue #62)` — this `plan.md`,
     `build-logic/src/main/kotlin/webtools.kotlin-common-check.gradle.kts`, `SampleProject.kt`, the two new
     TestKit classes and the `BuildLogicPluginsGatingTest.kt` edit. The plan is in this commit so
     `git log --follow` binds the two. No module applies the plugin yet, so the build is unchanged for every
     module.
  2. `feat: add the webtools-http common core and its check-only js module (Issue #62)` — `settings.gradle.kts`,
     the root `build.gradle.kts` comment, everything under `webtools-http/` (build file, sources, tests,
     `logback-test.xml`, `api/webtools-http.api`) and `webtools-http-commoncheck/`.
  3. `docs: add webtools-http to the README (Issue #62)` — `README.md` (§8).
  4. `docs: record the webtools-http as-built results and impact (Issue #62)` — the reconciled
     `final-implementation.md` and this issue's `impact.md`.
- **Never carried into these commits:** any plan-board file (`dashboard.html`, `plan.json`, under
  `docs/plans/`) that the admin team's presenter has changed in the working tree. Those boards are the
  admin team's to commit. This issue's own board (`62-http-core/dashboard.html`, `62-http-core/plan.json`)
  is committed by the landing, not by these commits. Also never: IDE files, `build/` directories,
  `kotlin-js-store/`, a `Proof*` or `NegativeCases` file.
- **No version bump** of any existing module. `webtools-http` `1.0.0` is new and unreleased.
- **The land request's notes** must say that `webtools-http` `1.0.0` is **not a release candidate** and stays
  unpublished until #55's stack is complete, that the landing adds the systems `webtools-http` and
  `webtools-http-commoncheck` (for the dashboards) and the plugin `webtools.kotlin-common-check`, and that the
  PR closes #62.
- **PR:** base `master`, body `Closes #62`. After the merge the branch is deleted, locally and on the remote.

---

## 12. Interfaces with sibling units

Names this unit takes in the package `com.spartanlabs.webtools.http` (a sibling must not reuse them):
public — `ExperimentalForInheritanceWebToolsApi`, `HttpTransport`, `WallClock`, `HttpFailure`,
`TransportFailure`, `UpstreamFailure`, `RefusalReason`, `ExhaustedReason`, `HttpMethod`, `Url`, `HttpHeaders`,
`RequestBody`, `HttpRequest`, `HttpResponse`, `RedirectHop`; internal — `Log`, `logger`, `epochMillisNow`,
`flatMap`, `catchNonFatal`, `HttpSyntax`, `UrlProblem`, `UrlParseException`, `urlFailure`, `ParsedUrl`,
`parseUrlText`, `defaultPortOf`, `canonicalHost`, `canonicalPath`, `canonicalQuery`, `encodePathSegment`,
`encodeQueryComponent`. Test-support names: `UrlCorpus`, `UrlInputGenerator`.

**What this unit expects from units 1 and 2 (both landed).** `webtools.kotlin-multiplatform-library` as built:
the `jvm()` target at the Java floor, vanniktech with the pinned publication, `kotlin("test")` in `commonTest`,
`kotlin("test-junit5")` and logback 1.5.6 in `jvmTest`, the seven level tasks over the `jvm` test compilation, a
lifecycle `test`, binary-compatibility-validator 0.18.2 on its defaults (no ASM pin), `verifyJavaFloor` in
`check`; `WebToolsBuild.JAVA_FLOOR`; the TestKit harness. It expects nothing from units 4 to 11.

**What it provides to every sibling.** The public vocabulary above; the nested-leaf failure model with the
constructors of §6.3; the opt-in wiring of §6.2; the `webtools.kotlin-common-check` plugin and its pattern; the
`internal` helpers `Log`/`logger`, `flatMap`, `catchNonFatal`, `HttpSyntax`.

**Unit 4 — `test-support` (#63).**
- **`HttpSyntax`** is `internal`; the fixtures compilation reaches it through the association. For the `.http`
  parser it provides `isToken`, `firstNonTokenIndex`, `isFieldValue`, `trimOws`, `isSendableValue`,
  `sanitizeReceivedValue`, `isCredentialName`, `equalsName`, `mediaTypeProblemIndex`. Headers go in through
  `HttpHeaders { add(name, value) }` (the container rule, so UTF-8 values round-trip); a status outside
  100..599 is rejected by the `HttpResponse` constructor (the parser raises `FixtureError` first).
- **Public builders and accessors** for matchers and fakes: `HttpResponse(status, url) { headers = …; redirectChain = …; body(bytes) }`,
  `HttpHeaders { add; addSensitive; set; setSensitive; remove; addAll }`, `HttpRequest(method, url) { headers = …; body = … }`,
  `RequestBody(bytes, contentType)`; on read: `HttpRequest.method/url/headers/body`, `RequestBody.bytes()/size/contentType`,
  `Url.scheme/host/port/encodedPath/encodedQuery`, `HttpHeaders.get/getAll/entries`.
- **Failures:** every `TransportFailure` leaf constructor of §6.3 is public, so a scripted step can fail with any
  of them; `HttpFailure.Closed("transport")` after `close()`; `BodyTooLarge(limit, declared)` over a cap.
  `UnmatchedRequestError` prints `request.url.redacted()`, never `toString()`.
- **The opt-in does not reach an associated compilation.** `VirtualClock.wallClock` implements the gated
  `WallClock` and `FakeHttpTransport` implements `HttpTransport`: the fixtures source set needs its own
  `languageSettings.optIn("com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi")` (on every source
  set of that compilation), and the fakes stay `final`. `webtools-http`'s own `jvmTest` stays a consumer.
- **No query decoder.** `Url.encodedQuery` is raw canonical text (`+` stays `+`, the query is not split). If a
  matcher needs name/value pairs, unit 4 adds an `internal` decoder; `encodeQueryComponent` is the matching
  encoder.
- **The root project still has no `check` task** (P7 shows it): the publication check attaches to
  `webtools-http`'s own `check` or to a lifecycle `check` that unit 4 adds to the root, its call. The check
  module added no root task. `kotlinx-coroutines-test` is not in `webtools-http`; unit 4 adds it on its fixtures
  variant.
- `logback-test.xml` already exists in `webtools-http/src/jvmTest/resources`; it never goes in the fixtures jar.

**Unit 5 — `url-policy` (#64).**
- **`Url`'s host semantics.** `host` is canonical: lower-case ASCII; an IPv6 literal without brackets (`':' in host`
  is the test), with an IPv4-mapped address already collapsed to dotted IPv4; any other host is
  `a-z 0-9 - _ .` text of 1..253 characters in labels of 1..63, never percent-encoded, never non-ASCII, never
  holding an `xn--` label, with one trailing dot kept as written. Digits-and-dots text and hex/octal-looking text
  (`127.1`, `0x7f.1`, `2130706433`, `1.2.3.4.5`, `999.999.999.999`, `localhost.`) are **not** normalised: the
  policy's `StrictIp`/`HostRules` judge them (`HostForm`, `ReservedName`). `port` is the effective port, 1..65535;
  `scheme` is `http` or `https`; there is never user-info.
- **Vocabulary:** `RefusalReason` has the nine constants; `TransportFailure.RefusedByPolicy(reason)` (and
  `(reason, cause)`); `HttpHeaders.Entry.isSensitive` and `HttpSyntax.isCredentialName` for the `SensitiveHeader`
  rule and the cross-origin strip; `InvalidRequest(rule)`.
- The unit supplies `Url` and constrains neither how `UrlGuard.checkHop` receives a hop nor how a redirect target is
  turned into a `Url`.

**Unit 6 — `okhttp-transport` (#65).**
- **A top-level implementer.** `OkHttpTransport : HttpTransport` in `jvmMain` is covered by the module-wide
  opt-in; it needs no `@OptIn`. A build that loses the wiring fails to compile it, loudly.
- **`Url` to `HttpUrl`:** `request.url.toString().toHttpUrl()`. By the differential it always succeeds and
  agrees on scheme, host, port, path and query; do not rebuild the URL from the parts.
- **Request:** `request.method.name` goes to OkHttp raw — safe because `HttpMethod.of` checked the token. Where
  `Request.Builder.method(name, null)` would throw for a method OkHttp requires a body for (`POST`, `PUT`,
  `PATCH`, and `PROPPATCH`/`QUERY`/`REPORT`), give it an empty body (a differential test over the method list is
  the transport's). `RequestBody.contentType` is sent as a plain `Content-Type` header with the OkHttp body's
  `MediaType` **null** (never `toMediaType()`, which rejects RFC-valid forms and silently replaces a user header).
  Header names are tokens and values sendable, so `addHeader` cannot throw. Names the transport owns
  (`User-Agent`, `Accept-Encoding`, `Host`, `Connection`, `Content-Length`, `Transfer-Encoding`) are its decision;
  `HttpRequest` does not refuse them.
- **Response:** a status outside 100..599 (`0..99`, `600..999`, and the `IOException` whose cause is OkHttp's
  `IllegalStateException` for `code < 0`) becomes `TransportFailure.Protocol`. Entries: drop those whose name is
  not a token (empty, with a space, `:`-prefixed, non-ASCII; an obs-fold continuation arrives as an empty-name
  entry); pass each value through `HttpSyntax.sanitizeReceivedValue` (CR, LF and NUL become SP); keep duplicates,
  in order; log only the **count** of dropped entries at `DEBUG`. `HttpResponse.url` is the last hop's URL and
  `redirectChain` is `RedirectHop(requestedUrl, status)` per followed hop.
- **Failures:** the public constructors `Timeout`, `ConnectFailed`, `DnsFailed(host)`, `TlsFailed`, `Protocol`,
  `Io`, `InvalidRequest(rule)`, `RefusedByPolicy(reason)`, `BodyTooLarge(limit, declared)`,
  `TooManyRedirects(limit)`, `RetryRefused`, each also with a `cause`; `Closed("transport")` after `close()`.
  **The cause is the transport's decision:** messages are constant, but OkHttp's own exceptions can carry hosts,
  resolved addresses or URLs, so attach one only when that is acceptable and never log its message above `DEBUG`.
- **Logging:** `logger(name)` and `Url.redacted()` only (architecture §6.6's never-list).

**Unit 7 — `response-cache` (#66).** `HttpFailure.Closed("cache")` after `close()`; `HttpFailure.DecodeFailure`
is the facade's, not the cache's; `catchNonFatal` for a loader that may throw; `flatMap`; `logger`. The
`ResponseCache` port is a gated interface and `CaffeineResponseCache` a `jvmMain` implementer (no `@OptIn`).
`Url` is not the cache key (its equality is on bytes); the key is the facade's canonical parameters.

**Unit 8 — `call-budget` (#67).** `HttpFailure.BudgetExhausted(wait, reason, window)` with
`ExhaustedReason.Window`, `Paused`, `CostExceedsLimit`, `QueueFull` (`wait` not negative, `window` null or
non-blank); `HttpFailure.Closed("budget")`; `WallClock` (gated; `WallClock.System` is the default) and the
`Duration`-based failures; `logger`; `catchNonFatal`. It does not create `RateLimited` (the facades do).

**Unit 9 — `call-facades` (#68).**
- `UpstreamFailure.RateLimited(status, retryAfter)` — `internal` constructor, status 100..599, `retryAfter` not
  negative; `UpstreamError(status, headers, body)` — public; `DecodeFailure(cause)`; `InvalidRequest(rule)`;
  `HttpFailure.Closed("client")` after the client's `close()`.
- **Assemble as text, then parse.** Use `encodePathSegment`/`encodeQueryComponent` (both `Result<String>`: a segment
  `.` or `..` and an unpaired surrogate fail), build `scheme://host[:port]/seg/seg?name=value&…` as text from the
  base `Url`'s `toString()` without its query, and call `Url.parse`. Do not use `HttpUrl.Builder`. An empty segment
  is the facade's decision (`x//y` as text, `x/y` in `HttpUrl.Builder`): treat it as `InvalidRequest`. A base URL is
  parsed once at construction; a failure there is a programmer error.
- **Developer-written values at call time** (`CallSpec` headers, bodies) can throw `IllegalArgumentException` from
  `HttpHeaders { }`, `RequestBody(...)` or `HttpRequest(...)`: wrap the construction in `catchNonFatal` and map a
  failure to `InvalidRequest` with a constant rule, before a permit is spent. The exceptions' messages hold only
  token header names, but the rule is a constant anyway.
- `HttpResponse.body()` copies on every call: decode once.

**Unit 10 — `feeds` (#69).** The check-module pattern, exactly: `webtools-feeds-commoncheck/build.gradle.kts`
applies `webtools.kotlin-common-check`; `commonMain { kotlin.srcDir("../webtools-feeds/src/commonMain/kotlin");
languageSettings.optIn(<the webtools-http marker>); dependencies { implementation(project(":webtools-http-commoncheck"));
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2") } }` and `jsMain { languageSettings.optIn(<the
same marker>) }` (the dependency on `:webtools-http-commoncheck` resolves its `js` klib; measured). The marker
resolves through that dependency, so no unresolved-marker warning. `internal` declarations of `webtools-http`
(`Log`, `logger`, `HttpSyntax`, `flatMap`, `catchNonFatal`) are invisible to feeds: it carries its own `Log`/`logger`
with a `jsMain` actual in its own `JsActuals.kt`, and its own `flatMap` only if it chains `Result`s. `FeedFailure` extends
`HttpFailure` through its `protected` constructors `(message)` and `(message, cause)` and carries its own
`@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)`; `webtools-feeds`' main source sets opt in as
§6.2 does. Its reasons follow the `RefusalReason` shape. `settings.gradle.kts` gains `:webtools-feeds` and
`:webtools-feeds-commoncheck`. The `JS Environment Not Selected` warning prints once per build for all check modules.

**Unit 11 — `http-docs` (#70).** The source of truth for tiers and contracts is the KDoc of this unit. It takes from
this unit: the tier table (architecture §9), the compile-fail texts of the gate (`final-implementation.md` §3), the
"keep an `else`" statements, `Url`'s rules (§6.5) and constant messages, and the README rows, install line, Maven
and stdlib notes, build-and-test text and plugin table this unit wrote (§8). It writes the `### webtools-http`
Components table, the how-to and `docs/webtools-http-architecture.md`, which should describe the check module as
`js()` with no environment (the architecture's `nodejs { testTask … }` wording is superseded).

---

## 13. Sequencing and follow-ups

**Order of operations.** P1; S1 and P2; commit 1; S2 and P3 (stop if it differs); S3; S4 and P4, P5; S5 and
P6–P9; commit 2; S6 and commit 3; S7 (P10, reconcile `final-implementation.md`, write `impact.md`) and commit 4.
The implementer appends each deviation, with its reasoning, to `final-implementation.md` §2 the moment it makes it.

**Follow-ups deliberately left.**
- **A permanent compile-fail check of the gate.** The eight negative cases and their texts are recorded once
  (P3). A TestKit snippet compiled with and without the opt-in in `build-logic` would re-run them on every
  `build-logic` integration run, at the cost of a nested build per run. When it is added, the research's
  `verifyTestsAreConsumers` guard task can be reconsidered. Neither is part of this unit.
- **`architecture.md` §6.8, §6.1 and §2** give the check modules `js { nodejs { testTask { enabled = false } } }`
  (§6.1 and §2 as a `js` target whose tests are disabled), which downloads Node.js and Yarn. This unit builds `js()`
  with no environment. A dated correction of 2026-10-25 sits at §6.8, with pointers at §6.1 and §2, as #47's
  `webtools-udp` correction sits at §12. Unit 11 describes the as-built form.
- **`research/http-transport.md` F8** (`docs/plans/55-http-client-stack/research/`) says the query encoder leaves
  everything inside `A-Za-z0-9-._~` raw; `url-parsing.md` F16 corrects it (`~` becomes `%7E`, `*` stays). The
  committed research is left as written.
- **Additive later.** Internationalised host names, a strict mode that rejects a malformed `%`, a public query
  decoder or `Url` builder, and a typed extension for the check plugin (a third check module would justify it) are
  each additive.
- **`webtools-feeds-commoncheck`** (unit 10) and the feeds module.

---

## 14. Known gaps

- **The code blocks have not been compiled** (see the preface). The research compiled the shapes of the gate, the
  failure groups, `WallClock.System`, the `Url` prototype and the check plugin; the rest (headers, requests,
  the helpers) is new. P3 and S2's compile are the first check.
- **IDE behaviour is untested:** IntelliJ's import of `webtools-http-commoncheck` (a source directory outside the
  module, an `actual` for an `expect` in a sibling directory), whether the IDE honours `commonMain` plus leaf
  language settings as Gradle does, and the shape of its gate diagnostic. P10 observes it.
- **The accessor spelling** `jsMain { … }` in the check module's build file was measured in a probe, not in this
  repository's layout; the fallback is `getByName("jsMain")` (§3).
- **HTTP/2.** The header and status rules for received entries are source-read for HTTP/2 and measured only for
  HTTP/1.1; unit 6 measures what it can.
- **Transport-owned header names** are not refused by `HttpRequest`; unit 6 settles what the transport does with
  them (the research left it open).
- **OPTIONS with content** "MUST send a valid Content-Type" (RFC 9110 §9.3.7) is enforced by no rule here.
- **Kotlin/JS** is checked by compiling `commonMain`, not by running the corpus on JS; the `Url` prototype printed
  identical output on Node for 20 samples.
- **Only OkHttp 5.5.0** was measured; only a Windows host with JDK 23 (and 17 for the floor) has run the build;
  Linux and macOS are unexercised.
- **No length limit** is imposed on a header name, a value, a method, a content type or a URL; OkHttp bounds only
  the response header section (256 KiB).

---

## 15. Considerations

None. Every call this unit needs is made in the design above.
