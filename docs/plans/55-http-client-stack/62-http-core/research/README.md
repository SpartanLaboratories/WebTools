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
  F3: a check-only `js` module compiles a sibling's `commonMain` and downloads nothing while its
  tests are disabled. Question 2 measures the whole root build around it.
- **URLs.** [`../../research/outbound-url-policy.md`](../../research/outbound-url-policy.md) F2 and
  F3 record how `HttpUrl` canonicalises hosts and what the URL-confusion corpus reads as;
  [`../../research/http-transport.md`](../../research/http-transport.md) F8 records the query
  encoding that matches `addQueryParameter`. Question 3 builds the full grammar on them.
- **Requests.** [`../../research/http-transport.md`](../../research/http-transport.md) F8 records
  what OkHttp's `Request.Builder` throws on. Question 4 builds on it.
