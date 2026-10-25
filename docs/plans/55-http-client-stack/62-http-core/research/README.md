# Research: the `webtools-http` common core (`http-core`)

[SpartanLaboratories/WebTools#62](https://github.com/SpartanLaboratories/WebTools/issues/62), unit 3
(`http-core`) of the HTTP client stack,
[#55](https://github.com/SpartanLaboratories/WebTools/issues/55). The systems-level design is
[`../../architecture.md`](../../architecture.md) (§6.2, §6.8, §6.9, §9, §11 unit 3) and the
systems-level impact [`../../impact.md`](../../impact.md). This folder researches the
implementation details that design leaves to the unit.

## The work

`webtools-http` is a new Kotlin Multiplatform module: `io.github.spartanlaboratories:webtools-http`
1.0.0, unreleased until the whole stack is complete, package `com.spartanlabs.webtools.http`,
`explicitApi()`, on the `webtools.kotlin-multiplatform-library` convention plugin with a single
`jvm()` target. This unit delivers its common core:

- the opt-in marker `ExperimentalForInheritanceWebToolsApi` (`@RequiresOptIn(level = ERROR)`,
  `@Target()`, `BINARY`, `@MustBeDocumented`), applied only through `@SubclassOptInRequired`; the
  module opts its own main code in, module-wide;
- the failure model: the open `abstract class HttpFailure : Exception`, the abstract groups
  `TransportFailure` and `UpstreamFailure`, final leaves nested in them, and the reason
  vocabularies `RefusalReason` and `ExhaustedReason`;
- the value types `HttpMethod`, `Url`, `HttpHeaders`, `RequestBody`, `HttpRequest`, `HttpResponse`
  and `RedirectHop`;
- the ports `HttpTransport : AutoCloseable` and `WallClock`, with `WallClock.System` over an
  `internal expect` epoch-milliseconds reading;
- the `internal` logging seam, `logger(name): Log`, with an slf4j `actual` on the JVM;
- the unpublished check-only module `webtools-http-commoncheck`, which compiles the module's
  `commonMain` directory for `js`, and the `webtools.kotlin-common-check` convention plugin it
  applies.

What shapes the questions below:

- **Toolchain.** Kotlin Gradle plugin 2.2.0, Gradle 9.7.1 with the configuration cache on,
  kotlinx-coroutines 1.10.2, `okhttp-jvm` 5.5.0, class files at the Java 11 floor.
- **`Url.parse(text): Result<Url>`** accepts text and rewrites it exactly as OkHttp 5.5.0's `HttpUrl`
  does when the host name is ASCII:
  - it percent-encodes what needs encoding, with non-ASCII path and query text as UTF-8;
  - it resolves `.` and `..` segments, drops the default port and gives an empty path `/`;
  - it trims surrounding whitespace and drops a `#fragment`.

  It rejects only what is ambiguous or unsafe: a non-ASCII or percent-encoded host name, a
  backslash, whitespace or a control character inside the URL, and user-info. Internationalised
  host names are refused in 1.0.0. A Level 4a differential test in `jvmTest` pins two things:
  every input `Url.parse` accepts has the same scheme, host, port, encoded path and encoded query
  in `HttpUrl`, and nothing `HttpUrl` rejects is accepted.
- **Printing.** `Url.toString()` is the full canonical URL, query included. `redacted()` (scheme,
  host, port and path, with a marker in place of a query) is the form every log line and failure
  message uses. `HttpHeaders`, `HttpRequest`, `HttpResponse` and `RequestBody` print their
  structure only (names, method or status, the redacted URL, sizes), never header values or bodies.
- **Methods.** `HttpMethod.of(name)` accepts any RFC 9110 method token, case-sensitively, with
  constants for the seven common methods.
- **Validation.** A developer-written request is checked when it is built and throws
  `IllegalArgumentException`: header name and value syntax, and a body on `GET` or `HEAD`. A
  server's odd but harmless response header stays representable, so a transport never fails a
  whole response over one header. Text from outside the process is parsed into a `Result`.
- **The gate.** The module's main code compiles with the opt-in. Its `commonTest` and `jvmTest`
  compile without it, so they stay a consumer that mentions the gated types, uses the supplied
  defaults and constructs the final leaves; an implementer written without the opt-in fails to
  compile.
- **The check module** publishes nothing and downloads no Node.js.

## Questions

### 1. How does a Kotlin 2.2.0 multiplatform module opt its own main code into its subclass gate while its test code stays a consumer without the opt-in?

- **Options:**
  - the opt-in as a language setting of the main source sets only: `commonMain` and `jvmMain`,
    and in the check module `commonMain` and `jsMain`;
  - the opt-in as a compiler option of the main compilations' compile tasks only;
  - a project-wide compiler option, which reaches every compilation, tests included;
  - an `@OptIn` on each declaration that implements a gated type, with no module-wide option.
- **Requirements:**
  - The module builds with `explicitApi()` on the convention plugin's single `jvm()` target, and
    the check module compiles the same `commonMain` directory for `js`.
  - The gated types are plain interfaces (`HttpTransport : AutoCloseable` with a default
    `close()`; `WallClock`) and abstract classes (`HttpFailure : Exception` and its abstract
    groups, with final leaves nested in them). The supplied default `WallClock.System` lives in the
    module.
  - Main code compiles with the opt-in. `commonTest` and `jvmTest` compile without it: they
    mention the gated types in signatures, call and construct the supplied defaults and the final
    leaves, and declare a parameter whose default is `WallClock.System`.
  - An implementer, a class delegating to a gated interface, or a subclass of a gated abstract
    class, written without the opt-in, fails to compile; the same declaration with
    `@OptIn(ExperimentalForInheritanceWebToolsApi::class)` compiles.
- **Evidence that settles it:**
  - the Kotlin Gradle plugin 2.2.0's source and documentation for source-set language settings,
    per-compilation compiler options, and the consistency rules between source sets joined by
    `dependsOn` and between associated compilations;
  - the Kotlin documentation on opt-in requirements;
  - a compiled probe of the shapes above for `jvm` and `js`, with the diagnostics it prints;
  - how the gate appears in the compiled class files and in the binary-compatibility-validator
    dump.
- **Findings:** [`opt-in-gate.md`](opt-in-gate.md)

### 2. What does a check-only `js` module need, and what does it do to the root build?

- **Options:**
  - a convention plugin that applies the multiplatform plugin with
    `js { nodejs { testTask { enabled = false } } }` and `mavenCentral()`, with each check
    module's build file adding the sibling's `commonMain` directory, its `commonMain`
    dependencies and the opt-in;
  - the same plugin taking the sibling module through a small typed extension;
  - `js()` with no environment, or `js { browser() }`.
- **Requirements:**
  - Nothing is published. `./gradlew build` downloads no Node.js or Yarn and writes no
    `kotlin-js-store/`.
  - The build is compatible with the configuration cache and reuses it.
  - A JVM API in the shared directory fails the `js` compile.
  - Every `expect` declaration in the shared directory gets a `js` `actual` in the check module's
    `jsMain`: the internal logger, returning a no-op `Log`, and the epoch-milliseconds reading.
  - The plugin is a precompiled script plugin in the included `build-logic` build, whose plugin
    markers already include the Kotlin Multiplatform plugin.
  - Known in full: what the root project gains (plugins, tasks); what `./gradlew build`, `check`,
    `test`, `assemble` and `clean` run from the root; and what `build-logic`'s TestKit harness
    needs to test the plugin (downloads into its TestKit home, run time).
- **Evidence that settles it:**
  - the Kotlin Gradle plugin 2.2.0's source for the `js` target's `nodejs` set-up, the Node.js
    root plugin and the `js` test task's dependencies;
  - Gradle's rules for the dependencies of a disabled task;
  - the measurements in [`../../research/kmp-build-topology.md`](../../research/kmp-build-topology.md)
    F3;
  - a compiled probe with a root project, a multiplatform `jvm()` module and a check module, run
    with `--dry-run`, cold and warm, and with `--warning-mode all`.
- **Findings:** [`common-check-plugin.md`](common-check-plugin.md)

### 3. How should a common-code `Url` parse and canonicalise text so that every URL it accepts reads the same in OkHttp 5.5.0's `HttpUrl`?

- **Options,** per class of input: accept and canonicalise as `HttpUrl` does, or reject. The classes:
  - letter case in the scheme and host;
  - default ports, ports out of range, and an empty path;
  - `.` and `..` segments, plain and percent-encoded;
  - characters that need percent-encoding in a path or a query, ASCII and non-ASCII;
  - malformed percent escapes;
  - IPv6 literals and IPv4-mapped IPv6 literals;
  - slash variants after the scheme (`http:host`, `http:/host`, `http:///host`);
  - surrounding whitespace, and fragments;
  - `xn--` host labels, and host-name characters and lengths.
- **Requirements:**
  - the canonicalisation and the rejections listed under "The work";
  - a plain common class: no JVM API, and it compiles for `js`;
  - failures returned as `Result`, with a constant message per kind that never echoes the input;
  - `toString()` the full canonical URL; `redacted()` scheme, host, port and path, with a marker
    in place of a query; value equality on the canonical text;
  - `internal` encoders for one path segment and one query parameter, used to build a URL from
    parts, that match `HttpUrl.Builder.addPathSegment` and `addQueryParameter`.
- **Evidence that settles it:**
  - OkHttp 5.5.0's source at its release tag: `HttpUrl` parsing, host canonicalisation, IPv6
    decoding and printing, the percent-encoding sets and dot-segment handling;
  - RFC 3986 and the WHATWG URL Standard where OkHttp follows them;
  - Ktor's common `Url` and `URLBuilder`, for comparison;
  - a JVM probe that runs `HttpUrl` over a corpus and records each reading: the confusion strings
    of [`../../research/outbound-url-policy.md`](../../research/outbound-url-policy.md) F2 and F3,
    plus generated inputs;
  - the differential corpus a Level 4a test should carry.
- **Findings:** [`url-parsing.md`](url-parsing.md)

### 4. Which header names, header values, method names, statuses and content types are valid, on the request side and on the response side?

- **Options:**
  - **Headers.** One rule for every `HttpHeaders` (RFC 9110 token names and RFC 9110 field
    values). Or a container rule that refuses only what could split or inject a header (CR, LF,
    NUL, an empty name, a name holding a colon or whitespace), plus a stricter rule an
    `HttpRequest` applies to what it will send (RFC 9110 token names, visible-ASCII values).
  - **Methods.** Any RFC 9110 token, case-sensitive; which methods may carry a body; whether
    `CONNECT` and `TRACE` are special.
  - **Statuses.** Any three-digit code, or 100–599 only.
  - **Content types.** Media-type syntax, or the header-value rule.
- **Requirements:**
  - A developer-written request is checked when it is built and throws `IllegalArgumentException`.
  - A server's odd but harmless response header stays representable, so a transport never fails a
    whole response over one header.
  - Names are looked up case-insensitively.
  - What the types accept on the request side, OkHttp 5.5.0 sends without throwing.
  - What OkHttp 5.5.0 hands back from a response, the types can hold, or the transport knows
    which entries to drop.
  - Plain common code.
- **Evidence that settles it:**
  - RFC 9110 §5.1, §5.5, §5.6.2, §8.3, §9.1 and §15; RFC 9112 and RFC 9113 §8.2 on field syntax;
  - OkHttp 5.5.0's `Headers`, `Request.Builder`, `HttpMethod` and `StatusLine` sources, and what
    its HTTP/1.1 and HTTP/2 readers put into a response's headers;
  - Ktor's header validation, for comparison.
- **Findings:** [`header-and-method-validation.md`](header-and-method-validation.md)

## Existing findings this research builds on

- **The gate.** [`../../research/api-shape-and-failures.md`](../../research/api-shape-and-failures.md)
  F1 and F2: `@SubclassOptInRequired` gates implementing and delegating, not mentioning, read from
  the compiler's test data at Kotlin 2.2.0.
  [`../../research/test-support-and-time.md`](../../research/test-support-and-time.md) F4 measured
  the same on the JVM across three Gradle projects. Question 1 extends both to common code and to
  `js`.
- **The check module.** [`../../research/kmp-build-topology.md`](../../research/kmp-build-topology.md)
  F3: a check-only `js` module's `compileKotlinJs` rejects JVM APIs in a sibling's `commonMain` and
  downloads nothing. Question 2 measures the whole root build around it.
- **URLs.** [`../../research/outbound-url-policy.md`](../../research/outbound-url-policy.md) F2 and
  F3 record how `HttpUrl` canonicalises hosts and what the URL-confusion corpus reads as;
  [`../../research/http-transport.md`](../../research/http-transport.md) F8 records the query
  encoding that matches `addQueryParameter`. Question 3 builds the full grammar on them.
- **Requests.** [`../../research/http-transport.md`](../../research/http-transport.md) F8 records
  what OkHttp's `Request.Builder` throws on. Question 4 builds on it.

## Verification

Every load-bearing claim in the four findings files was re-opened at its source on 2026-10-25.

**107 claims: 107 hold, none fails, none is unclear.** Thirteen of them are probe measurements,
which were not re-run; the source behaviour each one relies on matches the code at the cited tag.

| Findings file | Claims | Result |
|---|---|---|
| [`opt-in-gate.md`](opt-in-gate.md) | 14 | all hold; claim 14 is a measurement |
| [`common-check-plugin.md`](common-check-plugin.md) | 11 and the measurements M1 to M9 | all hold; M1 to M9 are measurements |
| [`url-parsing.md`](url-parsing.md) | 28 | all hold; claims 26 to 28 are measurements |
| [`header-and-method-validation.md`](header-and-method-validation.md) | 45 | all hold |

**Wording notes, none of which changes a result:**

- **[`header-and-method-validation.md`](header-and-method-validation.md) claims 19 and 20.** OkHttp's
  source writes the ranges as `'\u0021'..'\u007e'` and `'\u0020'..'\u007e'`; the findings
  quote them as `'!'..'~'` and `' '..'~'`, which are the same ranges.
- **The same file, claim 42.** The `CONNECT` refusal spans two lines of
  `HttpRequestBuilderImpl.java`.
- **The same file, claim 45.** The advisory now names a fixed Mint version.
- **[`url-parsing.md`](url-parsing.md) claim 10.** `parseHexDigit` is in `-UtilCommon.kt`, not in
  `-HostnamesCommon.kt`.
- **KT-78620,** which [`common-check-plugin.md`](common-check-plugin.md) claim 11 cites. Its tracker
  entry names 2.2.20-Beta1 as the affected version. The plugin printing the `archives` deprecation
  at 2.2.0 rests on the measurement recorded in
  [`../../research/kmp-build-topology.md`](../../research/kmp-build-topology.md).

**Nothing newer changes an answer:**

- **OkHttp.** 5.5.1 is on Maven Central (2026-10-22). `HttpUrl.kt`, `-Url.kt`, `Punycode.kt` and
  `-HostnamesCommon.kt` are byte-identical across 5.5.0, 5.5.1 and the default branch.
- **The `decodeIpv6` bounds defect** is still present on OkHttp's default branch.
- **The binary-compatibility-validator.** 0.18.2 is still its latest release.
- **The "JS Environment Not Selected" diagnostic** is still a warning at Kotlin `v2.4.21` and
  `v2.5.0-Beta2`.
- **The rest.** The RFCs, the WHATWG URL Standard, the Kotlin documentation and Unicode's
  `PropList.txt` still carry the quoted text.

## Summary

### The opt-in gate

- **Wiring.** The module-wide opt-in is a language setting of the main source sets, written in each
  module's own build file.
  - `webtools-http` sets it on `commonMain` and `jvmMain`; `webtools-http-commoncheck` sets it on
    `commonMain` and `jsMain` ([`opt-in-gate.md`](opt-in-gate.md) F2, F5).
  - In a single-target module only the leaf line reaches the compiler. The `commonMain` line keeps
    the pair valid if a second target is added, because the Kotlin Gradle plugin then requires a
    dependent source set to use every opt-in its dependency uses (F3).
- **The tests stay a consumer.** No main-only form reaches `commonTest` or `jvmTest`.
  - These three forms reach the test compilations, so none is used (F2): the documented module-wide
    form `kotlin { compilerOptions { optIn.add(...) } }`, the target-level option, and
    `sourceSets.all { }`.
  - The option is not in a convention plugin either. A module that cannot see the marker warns
    "Opt-in requirement marker ... is unresolved" (F4).
- **What needs the opt-in at all.** A class nested inside a gated declaration compiles without it.
  Only a top-level implementer, or one in `jvmMain` or `jsMain`, needs it (F1).
  - Every implementer this unit writes is nested.
  - So its compile proof exercises the wiring with throwaway top-level implementers, then removes
    them.
- **Shapes** (F7–F9):
  - `WallClock.System` is a nested `public object`, the shape of the standard library's
    `TimeSource.Monotonic`.
  - Failure leaves are nested in their group.
  - `HttpFailure`, `TransportFailure` and `UpstreamFailure` are abstract and subclass-gated, with
    `protected` constructors.
  - Under `explicitApi()`, interface members are written `public`, and an `override` writes its
    return type but no visibility.
- **Diagnostics.** A consumer that implements, delegates to or subclasses a gated type without the
  opt-in gets "This class or interface requires opt-in to be implemented: <the marker's message>"
  at the supertype. The same declaration with `@OptIn` compiles (F10).
  - Inside the module, the compiler prints the marker's message only when it is one string
    literal. So the marker's message is written as one literal.
- **The dump** (F6):
  - `@SubclassOptInRequired` is in the class files (`RuntimeInvisibleAnnotations`) and absent from
    the binary-compatibility-validator dump.
  - The dump shows the mangled `execute-gIAlu-s`, a public `HttpTransport$DefaultImpls` for the
    default `close()`, `WallClock$System`, and nested leaves as `Outer$Leaf`.

### The check-only `js` module

- **`js()` with no environment.**
  - `js { nodejs { testTask { enabled = false } } }` downloads Node.js and Yarn during
    `./gradlew build`, runs npm install and writes `kotlin-js-store/yarn.lock`
    ([`common-check-plugin.md`](common-check-plugin.md) F2). Gradle runs the dependencies of a
    disabled task, and the Kotlin Gradle plugin makes the `js` test task depend on the Node.js and
    Yarn set-up.
  - `nodejs()` and `browser()` both apply the plugin's Node.js root plugin to the root project. The
    root then gains Gradle's base plugin and about eighteen tasks, `build`, `check`, `assemble` and
    `clean` among them (F1, F5).
  - `js()` alone compiles a klib, creates no test task, downloads nothing and leaves the root
    project as it is (F4, F6).
  - Its cost is one "JS Environment Not Selected" warning per configuration-cache miss. That is
    still a warning in Kotlin Gradle plugin 2.4.21 (F4).
  - If a release turns it into an error, the plugin's TestKit check fails. The fallback is the
    Node.js form with the test task's dependencies cleared (F3).
- **The plugin** applies the multiplatform plugin, declares `mavenCentral()`, calls `js()`, and
  wires `check` to `compileKotlinJs`; without that wiring, `check` compiles nothing (F6).
- **Each check module** (F7, F9, F10):
  - adds the sibling's `commonMain` directory by a path relative to itself, which is safe for the
    configuration cache;
  - declares the sibling's `commonMain` dependencies and the opt-in;
  - holds the two `js` actuals. `Date.now()` returns a `Double`, which is exact as a `Long`.
- **What it catches.** A JVM API in the shared directory fails with "Unresolved reference". An
  `expect` without a `js` actual fails with "has no actual declaration" (F8).
  - A check module can depend on another check module's `js` output, which the feeds module's
    check needs (F11).
- **The root project and the README.** The root project gains no plugin and no task, so it still
  has no `check` of its own. The README's "Build & test" section gains one sentence and a fourth
  convention-plugin row.
- **TestKit** (F12):
  - `CommonCheckPluginIntegrationTest` has seven tests:
    - the build;
    - the `check` wiring;
    - a JVM-API leak;
    - a missing actual;
    - no Node.js, Yarn or npm task and no `kotlin-js-store/`;
    - no publishing task;
    - no root task.
  - A configuration-cache test sits beside it.
  - They take about 12 to 17 s warm and add two klibs to the TestKit home.

### `Url`

- **A port of `HttpUrl`'s algorithm, stage by stage, plus rejections that can only make it
  stricter.** It is about 320 lines of common Kotlin. A prototype agreed with `HttpUrl` 5.5.0 on 4.3
  million inputs and compiled for `js` ([`url-parsing.md`](url-parsing.md) F15).
- **Accepted, and canonicalised as `HttpUrl` does** (F2–F9):
  - scheme and host lower-cased;
  - the default port dropped from the text, while the effective port is kept;
  - an empty path becomes `/`;
  - `.` and `..` segments resolved, including their `%2e` forms;
  - path and query characters percent-encoded with `HttpUrl`'s sets, with non-ASCII text as UTF-8 in
    upper-case hex;
  - an existing `%XX`, and a malformed `%`, kept as typed;
  - IPv6 printed in RFC 5952 form, and an IPv4-mapped address printed as dotted IPv4;
  - digits-and-dots hosts and a trailing dot kept as written;
  - surrounding TAB, LF, FF, CR and SP trimmed;
  - the fragment dropped;
  - a bare `?` kept as an empty query.
- **Rejected** (F2–F10):
  - **Anywhere in the text:** a backslash; whitespace (Unicode `White_Space`), a C0 or C1 control or
    DEL inside the text; an unpaired surrogate.
  - **Scheme and authority:** any scheme but `http` and `https`; any separator but `://`;
    user-info.
  - **The host:** non-ASCII or percent-encoded text; a character outside `a-z 0-9 - _ .`; a broken
    length rule; an `xn--` label; a malformed IPv6 literal, including one with a zone.
  - **The port:** signed, non-numeric, `0`, or above 65535.

  Each is a place where `HttpUrl` is more lenient than other parsers, or rewrites silently, so
  rejecting it cannot disagree with `HttpUrl`.
- **`HttpUrl` crashes on `http://[1:2:3:4:5:6:7::8]/`.** Its `decodeIpv6` throws
  `ArrayIndexOutOfBoundsException`, which `toHttpUrlOrNull()` does not catch (F6). `Url` rejects that
  text, and the differential treats any `Throwable` from `HttpUrl` as a rejection.
- **Members and text** (F11):
  - members: `scheme`, `host` (IPv6 without brackets), the effective `port`, `encodedPath` and
    `encodedQuery: String?`;
  - `toString()` is the canonical URL, query included;
  - `redacted()` puts `?...` in place of the query;
  - equality is on the canonical text;
  - a failure is an `IllegalArgumentException` with one of ten constant messages.
- **Encoders** (F16). The internal `encodePathSegment` and `encodeQueryComponent` reproduce
  `addPathSegment` and `addQueryParameter`.
  - The query encoder leaves only `A-Za-z0-9*-._` raw, so `~` becomes `%7E`.
  - The path encoder refuses `.` and `..`, which `addPathSegment` silently drops or pops.
  - A URL built from parts is assembled as text and parsed again.
- **The differential.** It lives in `jvmTest` at Level 4a: the corpus, a seeded generator with a
  coverage floor, and the exact assertion are in the findings' Recommendation.

### Headers, methods, statuses and content types

- **Names: the RFC 9110 token, everywhere,** checked in the `HttpHeaders` builder
  ([`header-and-method-validation.md`](header-and-method-validation.md) F1, F4, F9).
  - OkHttp sends any visible-ASCII name, `a:b` included, and that name splits on the HTTP/1.1 wire.
  - A response entry whose name is not a token is dropped by the transport, so a response never
    fails over one header.
- **Values: two rules** (F4, F6).
  - `HttpHeaders` refuses only CR, LF and NUL, after trimming SP and HTAB, so a received UTF-8 value
    round-trips.
  - `HttpRequest` also requires every value it will send to be HTAB or `0x20..0x7E`, exactly the set
    OkHttp accepts.
  - The transport replaces CR, LF or NUL in a received value with SP, as RFC 9110 §5.5 allows.
- **Lookup** (F4).
  - Case folding is ASCII-only, because Kotlin's `ignoreCase` folds U+212A to `k`.
  - `get` returns the last value, as OkHttp does; `getAll` returns every value; `set` replaces every
    entry of the name and appends one.
  - `Authorization`, `Proxy-Authorization`, `Cookie` and `Set-Cookie` are sensitive without the
    flag. One predicate serves the policy, the transport and the recorder.
- **Methods** (F3, F5).
  - `HttpMethod.of` accepts exactly `1*tchar`, case-sensitively.
  - OkHttp validates no method token and writes it raw into the request line; a CRLF in a method
    injected a header (measured). So this check is the only one.
  - `GET`, `HEAD` and `TRACE` take no body. `POST`, `PUT` and `PATCH` may omit one, and the transport
    supplies an empty body where OkHttp needs one.
  - `HttpRequest` refuses `CONNECT`, whose host:port target `Url` cannot express.
- **Statuses** (F8). `HttpResponse` takes 100..599 and `RedirectHop` 300..399. OkHttp hands a
  transport any code from 0 to 999, so the transport maps the rest to `TransportFailure.Protocol`.
- **Content types** (F10).
  - `RequestBody.contentType` is an RFC 9110 media type (its visible-ASCII subset), sent as a plain
    `Content-Type` header.
  - It does not go through OkHttp's `MediaType`, which rejects valid forms such as
    `text/plain ; charset=x`.
  - A request whose body has a content type and that also sets a `Content-Type` header is refused.
- **Messages are constants** (F4). A header name is echoed only once it has passed the token rule;
  a value is never echoed. OkHttp echoes the value for every header but four.

### Where the findings differ, and which view holds

- **The check module's opt-in line.**
  - [`common-check-plugin.md`](common-check-plugin.md) F10 uses the module-wide compiler option in
    the check module, which has no tests.
  - [`opt-in-gate.md`](opt-in-gate.md) F5 measured that `commonMain` plus `jsMain` language
    settings work too.
  - Both reach the `js` compilation; `commonMain` alone reaches neither.
  - The `commonMain`-plus-leaf form is used in both modules, so one shape serves the module and its
    check, and it stays valid if a target is added.
- **Downloads by the `js` target.**
  - [`../../research/kmp-build-topology.md`](../../research/kmp-build-topology.md) F3 ran
    `compileKotlinJs` alone, and it downloaded nothing.
  - A whole `./gradlew build` with `nodejs { testTask { enabled = false } }` downloads Node.js and
    Yarn ([`common-check-plugin.md`](common-check-plugin.md) F2).
  - So the check module declares `js()` with no environment.
- **The query encoder's raw set.**
  - [`../../research/http-transport.md`](../../research/http-transport.md) F8 says that everything
    outside `A-Za-z0-9-._~` is encoded.
  - OkHttp's `addQueryParameter` leaves only `A-Za-z0-9*-._` raw: `~` becomes `%7E` and `*` stays
    ([`url-parsing.md`](url-parsing.md) F16).
  - The encoder follows OkHttp.
- **The findings' open considerations,** and how each is settled by the requirements in "The work":
  - **[`url-parsing.md`](url-parsing.md):**
    - Slash variants, host characters outside the narrow set, and Unicode whitespace or C1 controls
      are rejected as ambiguous.
    - A malformed `%` is kept, as `HttpUrl` keeps it.
  - **[`header-and-method-validation.md`](header-and-method-validation.md):**
    - `get` returns the last value.
    - Credential names are sensitive without the flag.
    - `CONNECT` and a body on `TRACE` are refused.
    - A status outside 100..599 is a `Protocol` failure in the transport.
    - A `Content-Type` header beside a body's content type is refused.
  - **[`common-check-plugin.md`](common-check-plugin.md):**
    - The warning is accepted, and the Node.js form is the fallback.
    - No typed extension is added.
  - **[`opt-in-gate.md`](opt-in-gate.md):**
    - The wiring is proven once, with throwaway implementers whose results are recorded, and no
      permanent check is kept.
    - The opt-in is a language setting.

### Known gaps

- **IntelliJ is untested.** Neither its import of the check module (a source directory outside the
  module) nor its reading of per-source-set opt-ins was tried.
- **Only OkHttp 5.5.0 was measured.** A later release that changes a canonicalisation surfaces as a
  differential failure.
- **The differential is random coverage, not a proof.** It caught each of four planted defects.
- **On `js`, the URL prototype ran 20 samples, not the whole corpus.** The check module compiles
  the code for `js`, but no test runs there.
- **HTTP/2 header handling was source-read only.**
- **Kotlin Gradle plugin releases after 2.2.0 were read, not run.** The missing-environment warning
  may become an error in one of them.
