# Research: HTTP client stack (`webtools-http`) and feeds module

[SpartanLaboratories/WebTools#55](https://github.com/SpartanLaboratories/WebTools/issues/55).
Folded in: [#47](https://github.com/SpartanLaboratories/WebTools/issues/47) (published
artifacts are Java 23 bytecode while the README promises JDK 11+), researched in
[`../47-java-23-bytecode/research/`](../47-java-23-bytecode/research/README.md).
Related: [#58](https://github.com/SpartanLaboratories/WebTools/issues/58) (repository
practices and the release steps), [#50](https://github.com/SpartanLaboratories/WebTools/issues/50)
(multiplatform direction for `webtools-udp`). First consumer:
[SpartanLaboratories/KotlinBottools#1](https://github.com/SpartanLaboratories/KotlinBottools/issues/1).

## The work

**`webtools-http`** is a new module (`io.github.spartanlaboratories:webtools-http`, package
`com.spartanlabs.webtools.http`, starting at `1.0.0`). It is a Kotlin Multiplatform module: its
ports live in `commonMain`, and its OkHttp and Caffeine defaults live in `jvmMain`. It carries:

- **A suspend transport port**, `execute(request): Result<Response>`, in library-owned request
  and response types.
  - The request model is general: method, URL, headers, and an optional body.
  - The transport enforces a body size cap and a call timeout, sends a descriptive
    `User-Agent`, and carries credentials in headers, never in URLs.
  - A `304` reaches the caller as a response, not a failure. A raw call returns the status,
    the headers and the capped body bytes.
  - The transport never retries.
- **The OkHttp default**, on `com.squareup.okhttp3:okhttp-jvm:5.5.0`.
  - 5.5.0 is the version JDA 6.7.0 brings onto every bot's classpath. It is declared as an
    ordinary `implementation` requirement, not `strictly`, so a consumer's newer 5.x wins
    resolution.
  - The declared version moves only in step with the OkHttp that JDA adopts.
  - `kotlinx-coroutines` is 1.10.2, so `okhttp-coroutines` is not used: it requires 1.11.0.
- **Outbound-URL policies.**
  - `Trusted` is for fixed provider hosts.
  - `Untrusted` is for administrator-supplied URLs. It allows `http` and `https` only, and uses
    a resolver that rejects loopback, link-local and private ranges, so redirects are covered
    too. Redirects and body size are capped, and timeouts are short.
- **A response cache port** with a Caffeine 3.3.1 default.
  - There is one async cache per client, with a time to live per endpoint and a size bound.
  - Failures are not cached; confirmed misses are cached briefly.
  - Coalesced loads run in a library-owned scope and are awaited with `asDeferred().await()`.
  - A `maxAge` override serves callers that need freshness.
  - The key is the endpoint and its canonical parameters, never the caller's tenant.
- **A call budget port**: `acquire(cost, lane, maxWait): Result<Permit>` and
  `Permit.complete(signals)`.
  - Several windows (per minute, per day) are set below the published limits.
  - Lanes have a reserved share for interactive calls.
  - A `429` pauses the budget until `Retry-After` or the window boundary.
  - A server-reported remaining count can only lower the local balance.
  - Keyed providers get a hard daily stop. It holds within one process, and an opt-in seam for
    persisting the counters (in-memory by default) lets a consumer make it survive a restart.
  - The budget exposes a utilisation reading and never runs a retry loop.
  - A small per-provider reader, supplied by the consumer, parses the response signals.
- **A call facade** that layers cache, then budget, then transport.
  - It takes an endpoint descriptor (id, name, TTL, cost, lane) and a decode function supplied
    by the caller.
  - Serialization types therefore stay out of public signatures, and each consumer owns its
    wire classes.
- **A typed failure model**, returned through `kotlin.Result`.
  - The failures are: rate limited (with a retry delay), budget exhausted (with a wait),
    upstream status, decode failure, body too large, and URL refused by policy.
  - No result is nullable and no exception crosses the API. Coroutine cancellation still
    propagates.
  - The API is suspend functions for Kotlin callers.
- **Published test support**, shipped as a Gradle test-fixtures variant of `webtools-http` (one
  coordinate, one version). It contains:
  - a fake transport that serves recorded fixtures with scripted statuses and headers;
  - one injected time source shared by the cache ticker, the budget clock and coroutine
    `delay`;
  - the real transport, runnable against `mockwebserver3` 5.5.0;
  - a fixture recorder that runs only when an environment variable is set, never in CI, and
    strips keys before writing.

**A feeds module** (for example `webtools-feeds`) is built on `webtools-http`. It parses RSS and
Atom behind a port with a default implementation, and fetches feeds with a conditional GET
(`ETag`, `Last-Modified`).

**Stability.**
- Stable Core: what consumers call or configure.
- Experimental: every seam a consumer implements or substitutes. These sit behind one
  `@RequiresOptIn` marker, and the supplied defaults are usable without opting in.
- An Experimental seam graduates to `@SupportedExtension` in a feature release once a consumer
  has built against it.

**Constraints across the repository.**

- **Java 11 for every module.** Every module, the existing three included, targets Java 11, set
  once in the root build.
  - Today every published WebTools artifact is Java 23 bytecode and declares
    `org.gradle.jvm.version: 23` in its Gradle Module Metadata.
  - The README promises "JDK 11 or newer".
- **Kotlin and Gradle.** Kotlin is 2.2.0 and Gradle is 9.7.1.
  - Published Kotlin metadata stays readable by a Kotlin 2.2.x compiler.
  - No dependency is a `-SNAPSHOT`.
  - Gradle Module Metadata is published beside each POM.
- **Dependencies and API surface.**
  - Every library is an `implementation` dependency, and none appears in a public signature.
  - Nothing from skrapeit, jsoup, unirest, Ktor or Jackson reaches a consumer of the new
    modules.
  - Policy numbers (windows, margins, wait caps, TTLs, cache size) are constructor parameters
    with defaults.
- **Code standards.**
  - `explicitApi()` applies to the new modules.
  - Every public declaration has Level-2 KDoc.
  - Tests follow the five-level hierarchy, in packages
    `com.spartanlabs.testing.<level>.webtools.<module>`.
- **Legacy modules.** `webtools-scraping` and `webtools-browser` are legacy. Where the
  repository-wide build changes force an edit to them, their public API may break outright.
  `webtools-udp` keeps its public API: it deprecates rather than breaks.
- **The first consumer, BotTools** (SpartanLaboratories/KotlinBottools).
  - It reaches the stack only through an internal adapter.
  - It builds on JDK 21 with Kotlin 2.2.x and coroutines 1.10.2.
  - It develops against a sibling WebTools checkout through a nested Gradle composite build.
  - Its providers call OpenDota (60 calls a minute and 3,000 a day without a key, with a refused
    request still counting), Steam's news API, Open-Meteo, and RSS or Atom feeds at URLs that
    server administrators supply.
- **Scope.** The work ends at merge. Releasing follows the organisation's steps once the
  repository practices in #58 exist.

**Current state.**
- `settings.gradle.kts` includes `:webtools-udp`, `:webtools-scraping` and `:webtools-browser`,
  and applies `foojay-resolver-convention` 0.8.0. The wrapper is Gradle 9.7.1.
- The root `build.gradle.kts` declares `kotlin("jvm") version "2.2.0"` and vanniktech
  `maven-publish` 0.36.0, both `apply false`. Its `subprojects {}` block:
  - applies both plugins to every module;
  - adds `slf4j-api` 2.0.13 as `api` and the JUnit 5 test dependencies;
  - registers the seven level test tasks over `sourceSets["test"]`, selecting by JUnit tag.
- No build file sets `jvmToolchain`, `jvmTarget` or `explicitApi()`.
- Each module sets its own coordinates. `webtools-udp` overrides the version (`2.0.0a` on
  `master`).

**Prior research elsewhere.** None of it was written for this repository, so every finding
from it is established again here before a recommendation rests on it.
- BotTools' research on this stack (private repository, read 2026-10-20):
  [`http-client-stack.md`](https://github.com/SpartanLaboratories/KotlinBottools/blob/docs/issue-1-bottools-consolidation/docs/plans/1-bottools-consolidation/research/http-client-stack.md)
  and `webtools-consumption.md`.
- GeneralTools' multiplatform research, under `docs/plans/7-kotlin-multiplatform/research/`:
  build and Kotlin compatibility, Maven Central publishing, test-level selection, and logging.
- SpartanGraphics' build-skeleton research, under
  `docs/plans/1-phase-1-spartangraphics/3-g1-build-skeleton/research/`.

## Questions

The questions are researched in the order listed. The Java 11 floor question, in
[`../47-java-23-bytecode/research/README.md`](../47-java-23-bytecode/research/README.md), runs
after question 2.

### 1. Can a Kotlin Multiplatform module publish its test support as a Gradle test-fixtures variant?

- **Options:**
  - Gradle's `java-test-fixtures` plugin applied beside `kotlin("multiplatform")`.
  - A dedicated compilation on the `jvm` target, published as an extra variant that carries the
    `webtools-http-test-fixtures` capability through the multiplatform software component.
  - Any test-fixtures support the Kotlin Gradle plugin itself offers at the versions in use.
  - Whatever comparable multiplatform libraries actually ship.
- **Requirements:**
  - `webtools-http` is a multiplatform module on Kotlin Gradle plugin 2.2.0 and Gradle 9.7.1,
    published with vanniktech `maven-publish` (0.36.0 today; 0.37.0 exists).
  - The test support is a test-fixtures variant of the same module: one coordinate, one version.
  - A Gradle JVM consumer selects it with
    `testImplementation(testFixtures("io.github.spartanlaboratories:webtools-http:<version>"))`.
    That works both from Maven Central and when a composite build substitutes the module.
  - The fixtures' own dependencies (`kotlinx-coroutines-test`, `mockwebserver3`) reach only the
    consumer's test classpath.
  - The fixtures compile for Java 11.
- **Evidence that settles it:**
  - The Gradle and Kotlin Gradle plugin documentation at these versions.
  - The status of the YouTrack and Gradle issues on test fixtures in multiplatform projects.
  - The plugin source that builds the multiplatform publication: whether it accepts an extra
    variant, and how Gradle Module Metadata would record it.
  - Real multiplatform projects that publish fixtures.
  - Where documentation is silent, a throwaway probe build.
- **Findings:** [`kmp-test-fixtures.md`](kmp-test-fixtures.md)

### 2. How should the build host Kotlin Multiplatform modules beside the existing Kotlin/JVM modules?

- **Options:**
  - **Plugin version.** The Kotlin Gradle plugin 2.2.0 multiplatform plugin as it is, or a newer
    plugin pinned with `languageVersion`, `apiVersion` and a stdlib version that keep Kotlin
    2.2.x consumers compiling.
  - **Where plugins are applied.** The root `subprojects {}` block applying the JVM or the
    multiplatform plugin per module, or each module applying its own (with any shared
    convention in `buildSrc` or `build-logic`).
  - **Targets.** `jvm()` alone, or `jvm()` plus a non-JVM target so `commonMain` is compiled
    against the common standard library.
  - **Publishing.** vanniktech 0.36.0 or 0.37.0 for the multiplatform publication.
  - **Test levels.** How the five level test tasks select tests in a multiplatform module: JUnit
    tags or a package filter.
  - **Logging from common code.** None, kotlin-logging, or an internal seam, given that
    `slf4j-api` is JVM-only.
  - **The feeds module.** Whether it is multiplatform like `webtools-http` or JVM-only, and
    whether it depends on `webtools-http` through `api` or `implementation`.
- **Requirements:**
  - **Kotlin version.** The compiler stays at 2.2.0 unless 2.2.0 cannot carry a multiplatform
    module on Gradle 9.7.1.
  - **What gets published.**
    - Kotlin metadata is readable by a Kotlin 2.2.x compiler, and the stdlib requirement is no
      newer than 2.2.x.
    - No dependency is a `-SNAPSHOT`, and Gradle Module Metadata sits beside each POM.
    - The existing modules keep publishing under their coordinates.
  - **Consumers.** A consumer's composite build can substitute
    `io.github.spartanlaboratories:webtools-http`.
  - **Build settings.**
    - The Java 11 floor is set once for every module.
    - The level tasks follow the five-level hierarchy, with test packages
      `com.spartanlabs.testing.<level>.webtools.<module>`.
    - The build is compatible with the configuration cache.
- **Evidence that settles it:**
  - The Kotlin Gradle plugin's Gradle compatibility tables.
  - The Kotlin documentation on single-target multiplatform projects and on how common code is
    checked.
  - The vanniktech documentation.
  - The org's own multiplatform builds (GeneralTools, SpartanGraphics), re-checked against these
    versions.
  - Where documentation is silent, a throwaway probe build.
- **Findings:** [`kmp-build-topology.md`](kmp-build-topology.md)

### 3. What does the change touch in this repository, and which existing code should move onto the new stack?

- **Scope:** every file that the new modules, the build restructure, the Java 11 floor and the
  multiplatform build touch:
  - the settings and root build, the module builds, `.gitignore`;
  - the README's components, install, requirements and build-and-test sections;
  - the documents under `docs/`.
- **History:** the `git log` and `git blame` intent behind the build and behind `Connector`.
- **Java 11:** whether the existing modules compile and pass their tests at the Java 11 floor.
- **Baseline:** which tests pass today.
- **Adoption sweep:** every existing class that does HTTP or HTTP-adjacent work (`Connector`'s
  `open`/`get`/`download`, `WebViewer`), each with a verdict. The legacy modules are not
  reworked by this change.
- **Consumer acceptance:** how the checks BotTools has published for its adapter map onto this
  repository's tests. Those checks are the `Untrusted` refusals, `429` handling and size caps,
  run against `mockwebserver3`.
- **Findings:** [`codebase-blast-radius.md`](codebase-blast-radius.md)

### 4. What shape should the suspend transport port and its OkHttp default take, with the port in common code?

- **Options:**
  - **Request and response types.**
    - The URL as a string or as a library-owned value type.
    - Headers as a list of pairs or as a multimap.
    - The body as a `ByteArray`.
    - The method as an open value type or as an enum.
  - **Body cap enforcement.** A bounded streaming read, a `Content-Length` check, or both.
  - **The suspend bridge.** The `Call.executeAsync` pattern copied from `okhttp-coroutines`.
  - **Timeouts.** Call, connect, read and write.
  - **Redirects.**
  - **`Dispatcher` and per-host request limits**, sized from the budget.
  - **Lifecycle.** A shared `OkHttpClient`, and closing it.
  - **The `User-Agent`.** Whether it is a required parameter.
  - **Credentials.** A credential mechanism, or plain headers with redaction in logs.
- **Requirements:**
  - Everything listed for the transport and its OkHttp default under "The work".
  - The port uses no JVM type.
  - No OkHttp type appears in a public signature.
- **Evidence that settles it:**
  - OkHttp 5.5.0's source and documentation.
  - Engine abstractions in comparable libraries: Ktor's client engine, http4k's handler,
    Apollo Kotlin's HTTP engine, Coil 3's network layer, Retrofit's call adapters.
  - Kotlin API conventions for multiplatform value types.
- **Findings:** [`http-transport.md`](http-transport.md)

### 5. How should the outbound-URL policy guard against server-side request forgery, and where does each check run?

- **Options:**
  - **Where the check runs.** At the resolver (OkHttp `Dns`), at connect time (an
    `EventListener` or socket factory), on the URL itself, or a combination.
  - **Which address ranges to reject**, taken from the IANA special-purpose registries for IPv4
    and IPv6. Candidates include unspecified, loopback, link-local, private, unique-local, shared
    (CGNAT), multicast, broadcast, documentation, IPv4-mapped and translated IPv6 forms.
  - **Redirects.** OkHttp's own follow-up, or manual following with a check on every hop.
    Scheme downgrade on a redirect.
  - **Bypass routes:**
    - IP-literal hosts, which OkHttp may connect to without consulting `Dns`;
    - a configured proxy, which resolves names itself;
    - DNS rebinding between the check and the connect.
  - **Shape of the policy.** A closed pair of policies, or an open seam for custom policies
    such as allowlists.
  - **Caps and timeouts for `Untrusted`.**
- **Requirements:**
  - The `Trusted` and `Untrusted` behaviour listed under "The work".
  - The two supplied policies are part of what consumers configure.
- **Evidence that settles it:**
  - The OWASP SSRF prevention guidance and the IANA registries.
  - OkHttp 5.5.0's route selection, `Dns` and redirect source.
  - Comparable guards: Stripe's Smokescreen, Python's advocate, Node's request-filtering
    agents, Go's safeurl, and any maintained Java or Kotlin equivalent.
  - Published bypasses of incomplete guards.
- **Findings:** [`outbound-url-policy.md`](outbound-url-policy.md)

### 6. How should the response cache port and its Caffeine default behave, with the port in common code?

- **Options:**
  - **Port shape.** A suspend get-or-load taking a key, TTL, `maxAge` and loader, or a typed
    cache per endpoint.
  - **What is cached.** Decoded values or raw responses.
  - **Caffeine configuration.** `AsyncCache` with `Expiry`, `Ticker` and an executor.
  - **Scope and coalescing.** Ownership and shutdown of the library scope; cancellation
    semantics of coalesced loads.
  - **Confirmed misses.** How a confirmed miss is represented, and its TTL.
  - **`maxAge`.** An invalidate before the get, or an age check on read.
  - **Bounding.** `maximumSize` or a weigher.
  - **Keys.** Canonicalisation.
  - **Non-JVM targets.** Whether a common default exists for them.
- **Requirements:** everything listed for the cache under "The work".
- **Evidence that settles it:**
  - Caffeine 3.3.1's source and documentation.
  - The kotlinx-coroutines 1.10.2 future integration.
  - Comparable Kotlin caches: Aedile, cache4k, Store.
- **Findings:** [`response-cache.md`](response-cache.md)

### 7. What algorithm and shape should the call budget port and its default implementation have?

- **Options:**
  - **Algorithm.** Several token buckets, GCRA, fixed windows aligned to the provider's
    boundaries (the wall-clock minute, the UTC day), or sliding windows.
  - **Reservations.** First come, first served by reservation, with a refund on cancellation.
  - **Lanes.** An open lane type with reserved shares.
  - **Waiting.** A wait cap with fail-fast.
  - **`429` handling.** `Retry-After` in seconds or as an HTTP-date, falling back to the window
    boundary.
  - **Server feedback.** Reconciliation with server-reported remaining counts.
  - **The hard daily stop and the persistence seam.**
  - **The utilisation reading.**
  - **Where the default lives.** Common code (a coroutine `Mutex`, a monotonic time source and
    a wall clock), or Bucket4j, which is JVM-only.
  - **Time types in common code on Kotlin 2.2.0.** `kotlin.time.Clock` and `Instant` and their
    stability, kotlinx-datetime, or a library-owned clock.
- **Requirements:** everything listed for the budget under "The work".
- **Evidence that settles it:**
  - Rate limiters in comparable systems: Kord's `RequestRateLimiter`, JDA's `RestRateLimiter`,
    Bucket4j, Resilience4j, Guava's `RateLimiter`.
  - The GCRA literature.
  - The HTTP specification of `Retry-After`, and the IETF rate-limit header draft.
  - The providers' documented limits.
- **Findings:** [`call-budget.md`](call-budget.md)

### 8. How should the published test support and the single injected time source work?

- **Options:**
  - **The fake transport.** Fixture lookup by request; scripted statuses, headers, delays and
    failures; request recording.
  - **Fixtures.** The format (JSON bodies with a small manifest, HAR, or another established
    format) and their location.
  - **The time source.** `kotlinx-coroutines-test`'s scheduler time bridged into the Caffeine
    `Ticker` and the budget clock, with a wall-clock offset for UTC-day windows.
  - **`mockwebserver3` 5.5.0 at Level 3.** Through its JUnit 5 extension, or managed by hand.
  - **The recorder.** The environment-variable gate, key stripping in URLs and headers, and
    where it writes.
  - **Placement.** Which parts are common code and which are JVM code.
- **Requirements:**
  - Everything listed for the test support under "The work".
  - Level 2 runs with no network.
  - Level 3 runs against `mockwebserver3`.
- **Evidence that settles it:**
  - The `kotlinx-coroutines-test` 1.10.2 documentation and source.
  - The `mockwebserver3` 5.5.0 documentation.
  - Comparable test kits: Ktor's `MockEngine`, http4k's testing modules, OkHttp's own tests,
    recorders such as OkReplay.
- **Findings:** [`test-support-and-time.md`](test-support-and-time.md)

### 9. What public API shape and failure model fit the stack in idiomatic Kotlin?

- **Options:**
  - **The facade.** A class or an interface, built through a constructor with defaulted
    parameters, a builder, or a factory function.
  - **The endpoint descriptor.** Its shape, and whether the decode function travels with the
    endpoint or with each call.
  - **The failure model.** A sealed `Throwable` hierarchy carried in `Result`, or an open one,
    and how either evolves.
  - **`Result` with suspend functions and cancellation.**
  - **The opt-in markers.** `@RequiresOptIn` on the seam types, or `@SubclassOptInRequired`,
    which gates implementing a type but not using it. Their status on Kotlin 2.2.0.
  - **Defaults without opt-in.** Keeping the supplied defaults usable without opting in when a
    constructor takes Experimental types.
  - **Lanes and methods.** Value classes, enums, or sealed types.
  - **ABI tooling.** Binary-compatibility validation for the stable surface.
- **Requirements:**
  - The stability and failure-model rules under "The work".
  - Open hierarchies at genuine extension points, with policy parameterised.
  - The ports are in common code.
- **Evidence that settles it:**
  - The Kotlin documentation on opt-in requirements, explicit API mode and `Result`.
  - The Kotlin library-author guidelines.
  - The public shapes of comparable Kotlin libraries: the Ktor client, Kord, Apollo Kotlin,
    Coil 3, kotlinx-coroutines.
  - The org's own markers and guards in GeneralTools, MyGameTools and SpartanGraphics.
- **Findings:** [`api-shape-and-failures.md`](api-shape-and-failures.md)

### 10. How should the feeds module parse RSS and Atom and fetch feeds over the HTTP stack?

- **Options:**
  - **The parser.** Rome 2.1.0, rssreader 3.12.0, or an in-house StAX parser. Their
    maintenance, format coverage and security posture: DOCTYPE and external entities, entity
    expansion, size caps.
  - **Placement.** The port in common code with the parser in `jvmMain`, or a JVM-only module.
  - **The feed model.**
  - **Conditional GET state.** Whether the module or the caller owns `ETag` and
    `Last-Modified`.
  - **The dependency on `webtools-http`.** `api` if the feeds API exposes `webtools-http` types.
  - **Fetching.** Through the `Untrusted` policy.
- **Requirements:**
  - The feeds module is built on `webtools-http` and targets Java 11.
  - Parsing sits behind a port with a default, and feeds are fetched with a conditional GET.
  - Its seams follow the stability rule under "The work".
  - XML parsing is hardened.
- **Evidence that settles it:**
  - The Rome and rssreader repositories, their releases and advisories.
  - The OWASP XML external entity guidance.
  - Comparable Kotlin feed libraries.
- **Findings:** [`feed-parsing.md`](feed-parsing.md)
