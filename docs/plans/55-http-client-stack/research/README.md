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
- **Scope.** The work spans several systems. Each one lands as its own sub-issue and ends at
  its merge. Releasing follows the organisation's steps once the repository practices in #58
  exist.

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

## Verification

### Outside-world claims

Every load-bearing claim in the ten findings files here, and in
[`../47-java-23-bytecode/research/java-11-floor.md`](../47-java-23-bytecode/research/java-11-floor.md),
was re-opened at its source on 2026-10-21.

**231 claims: 227 hold, none fails, 1 is unclear, and 3 cite this repository's own files.**

| Findings file | Claims | Result |
|---|---|---|
| [`kmp-test-fixtures.md`](kmp-test-fixtures.md) | 18 | all hold |
| [`kmp-build-topology.md`](kmp-build-topology.md) | 11 | all hold |
| [`java-11-floor.md`](../47-java-23-bytecode/research/java-11-floor.md) | 17 | all hold |
| [`codebase-blast-radius.md`](codebase-blast-radius.md) | 8 | 6 hold; 7 and 8 cite repository files |
| [`http-transport.md`](http-transport.md) | 25 | 24 hold; 25 cites a repository file |
| [`outbound-url-policy.md`](outbound-url-policy.md) | 25 | 24 hold; 25 unclear |
| [`response-cache.md`](response-cache.md) | 30 | all hold |
| [`call-budget.md`](call-budget.md) | 28 | all hold |
| [`test-support-and-time.md`](test-support-and-time.md) | 19 | all hold |
| [`api-shape-and-failures.md`](api-shape-and-failures.md) | 26 | all hold |
| [`feed-parsing.md`](feed-parsing.md) | 24 | all hold |

**The unclear claim.** `outbound-url-policy.md` claim 25 says the address classifier compiles for
`jvm()` and `js(IR)` under Kotlin 2.2.0 and passes the same tests on both. It is a measurement
from a throwaway probe, with no external source to re-open. It stands as a measurement, and the
shipped classifier's Level 4a tests in `commonTest` re-establish it.

**Notes on claims that hold:**

- **KT-78620** is Fixed, with "Planned for 2.3.20-Beta1" and an empty "Available in". So
  "fixed in the 2.3 line" rests on the planned version (`kmp-build-topology.md` claim 3).
- **Unpublishable targets.** That a target cannot be marked unpublishable is inferred from
  `KotlinTarget.publishable` being read-only. `AbstractKotlinTarget` returns `true`, and native
  and external targets override it; the JVM target is unaffected (`kmp-build-topology.md`
  claim 4).
- **Commit dates.** Two commit dates in `outbound-url-policy.md` (claims 19 and 21) are a day
  off in UTC. The hashes, tags and quotes are right.
- **Ktor.** `AttributeKey.type` is private in Ktor, not public (`response-cache.md` claim 23).
- **OpenDota.** Its live `X-Rate-Limit-Remaining-Day` read 2998 at verification against 2999
  when recorded, as a live counter does (`call-budget.md` claim 12).
- **Nothing newer changes an answer:**
  - OkHttp 5.5.0, Caffeine 3.3.1, foojay-resolver-convention 1.0.0,
    binary-compatibility-validator 0.18.2 and Resilience4j 2.4.0 are the latest releases.
  - Rome 2.1.0 is still its latest release.
  - The IETF rate-limit header draft is still revision 11.
  - `@IntroducedAt` is still experimental at Kotlin 2.4.21.
  - The Kotlin Gradle compatibility table still has no row covering Gradle 9.7.1.

### Codebase facts

The repository claims the findings rest on were checked against `master` at `806ce8e`:

- **Root build.** `build.gradle.kts:18` applies `org.jetbrains.kotlin.jvm` to every module.
  `:29`–`:31` add `api(slf4j-api)` and the JUnit test dependencies. `:44`–`:62` register the
  level tasks over `sourceSets["test"]`. `:64`–`:91` hold the publishing skeleton.
- **Settings and versions.** `settings.gradle.kts:2` applies foojay-resolver-convention 0.8.0.
  `webtools-udp/build.gradle.kts:101` sets `2.0.0a`, and `:103`–`:117` hold the UDP port lock.
- **Test layout.** The repository has 99 test files, 4 of them untagged `testing/support/`
  helpers. Every other file's JUnit tag equals its package level. `webtools-udp` has 19
  gating, 21 component and 9 deterministic tagged classes.
- **README.** `README.md:26` is the only statement of a JDK floor in the repository.
  `README.md:73`–`:74` state the "keep an `else` branch" rule.
- **`Connector`'s global state.** `Connector.kt:65` calls `Unirest.setTimeouts(0, 0)`, and
  `:150` sets the JVM-wide `http.agent`.
- **Failure-hierarchy precedent.** `ReliableSendFailure.kt:15` is an open
  `abstract class … : Exception`.
- **Programmer errors.** `webtools-udp`'s constructors throw `IllegalArgumentException` through
  `require` for invalid configuration (`MultiConnectionUDPClient.kt:188`, `:195`).
- **The three repository-cited claims** resolve:
  - `kmp-build-topology.md:213` records the mangled `execute-gIAlu-s`;
  - `codebase-blast-radius.md` claims 7 and 8 quote text present in the build-topology and
    Java-floor findings.

## Summary

### Build, publishing and the Java 11 floor

- **The Kotlin Gradle plugin stays at 2.2.0 on Gradle 9.7.1.** Measured: it configures, compiles,
  tests and publishes a multiplatform module with the configuration cache on, writes metadata
  `[2,2,0]`, and publishes a `kotlin-stdlib` requirement of `2.2.0`.
  - The Kotlin compatibility table documents 2.2.x only up to Gradle 8.14, so the pairing is
    undocumented although it works.
  - Its one diagnostic is a Gradle-10 deprecation of the `archives` configuration, fixed in the
    2.3 line.
  - If that deprecation must go, the fallback is a newer plugin with
    `languageVersion = apiVersion = 2.2` and `coreLibrariesVersion = "2.2.0"`. Measured on
    2.4.21, it writes the same metadata and stdlib requirement.
  - Source: [`kmp-build-topology.md`](kmp-build-topology.md).
- **Shared configuration moves into an included `build-logic` build.**
  - It holds three precompiled plugins: `webtools.kotlin-jvm-library`,
    `webtools.kotlin-multiplatform-library` and `webtools.published-library`.
  - A `WebToolsBuild` object holds the Java floor and the test-level list.
  - The root build keeps `group` and `version` only.
  - Measured: the three existing modules then publish the same POMs and Module Metadata, except
    that `org.gradle.jvm.version` goes from 23 to 11.
  - A consumer with its own `build-logic` build includes WebTools as a nested composite without
    a clash.
- **Level test tasks.** The seven level test tasks select tests by package
  (`com.spartanlabs.testing.<level>.*`) in every module, with `isFailOnNoMatchingTests = false`.
  - On the three JVM modules the package filter selects exactly the classes their JUnit tags
    select: 99 test files, no mismatch.
  - A `commonTest` class cannot carry a JUnit tag.
  - A multiplatform module has no `test` task, so its plugin registers one that depends on
    `jvmTest`. Without it, `./gradlew test` would skip the module silently.
- **Java 11 for every module.** The mechanism is `jvmTarget = 11` plus `-Xjdk-release=11`, read
  from one constant on the build JDK, with no toolchain.
  - **How each module kind is configured.**
    - `kotlin("jvm")` modules also set `java { sourceCompatibility; targetCompatibility }`,
      because the plugin's JVM-target validation fails without it, even with no Java source.
    - A multiplatform `jvm()` target writes no `org.gradle.jvm.version` under any setting, so its
      plugin, and the fixtures variants, set the attribute explicitly.
  - **Why `jvmTarget` alone is not a floor.** Measured on a JDK 23 host, `MutableList.removeFirst()`
    compiled to JDK 21's `List.removeFirst` without a warning.
  - **The existing modules survive the floor.** They compile under `-Xjdk-release=11` with
    identical public signatures, so no legacy API breaks. Their tests pass on a real JDK 11,
    and no dependency on any candidate classpath is above Java 11.
  - **Why no toolchain.** A `jvmToolchain(11)` or a JDK 11 test launcher would need the foojay
    resolver bump, since 0.8.0 fails on Gradle 9.7.1 the moment a JDK must be provisioned. It
    would also make a consumer's composite build need a JDK 11.
  - **The guard.** A `verifyJavaFloor` task, wired into `check`, reads the built jars, the
    generated `.module` files and the resolved runtime classpath, and fails on anything above
    the floor.
  - Source: [`../47-java-23-bytecode/research/java-11-floor.md`](../47-java-23-bytecode/research/java-11-floor.md).
- **Publishing.**
  - vanniktech 0.36.0 publishes a multiplatform module as the root `webtools-http`, which holds
    no classes, plus `webtools-http-jvm`, each with a `.module` and a `.pom`.
  - A Gradle consumer depends on the root coordinate; a plain Maven consumer needs `-jvm`.
  - `slf4j-api` stays `api` in the three existing modules, declared in their own build files so
    their published shape is unchanged. The new modules declare it `implementation`.
- **Test support ships as a test-fixtures variant of the multiplatform module.**
  - **What the variant is.** It is a JVM-only `testFixtures` compilation associated with `main`,
    published through the experimental `kotlin.publishing.adhocSoftwareComponent` (Kotlin Gradle
    plugin 2.1.20 and later). It carries capability `webtools-http-test-fixtures` in the root
    module's metadata, beside a `-test-fixtures.jar`.
  - **Measured.** A JDK 21 / Kotlin 2.2.0 consumer resolves `testFixtures(...)` both from a file
    repository and through `includeBuild`. The fixtures' own dependencies reach only that
    consumer's test classpath.
  - **The guard.** Kotlin has no first-class feature for this (KT-63142 and KT-69482 are open).
    A build-level publication check therefore guards the experimental API across plugin
    upgrades: it publishes, then resolves from a file repository and through a composite.
  - Source: [`kmp-test-fixtures.md`](kmp-test-fixtures.md).
- **`jvm()` alone does not keep `commonMain` common.**
  - Measured: `java.io.File`, `java.time.Instant` and `System.currentTimeMillis()` compile in
    `commonMain` when there is one target. A second target makes the compiler reject them.
  - A target cannot be declared and then left unpublished: the root `.module` keeps a dangling
    `available-at`.
  - An unpublished check-only module that compiles the same `commonMain` directory for `js`
    works.
  - See Consideration 1.

### What the change touches

- **Files.**
  - Edited: `settings.gradle.kts`, the root build file, the three module build files and
    `README.md`.
  - Created: `build-logic/`, `webtools-http/`, the feeds module and an evergreen
    `docs/webtools-http-architecture.md`.
  - No legacy source or test file changes. `.gitignore` already covers every new build output.
- **`webtools-udp`'s port lock** moves unchanged, because the plugin that registers the level
  tasks is applied in the module's `plugins {}` block.
- **Nothing existing moves onto the new stack.**
  - `Connector` is blocking and stateful, sets JVM-wide state, and has no timeouts or caps.
  - `WebViewer` and `resolveLocalAddress()` are not HTTP clients.
- **Baseline.** Every level of every module passes on an unmodified `master`: 642 tests executed
  and 15 disabled by design.
- **First-consumer checks.** BotTools' acceptance checks for its adapter and its feeds plugin map
  onto `webtools-http` and feeds tests at Levels 2, 3, 4a and 4c.
- Source: [`codebase-blast-radius.md`](codebase-blast-radius.md).

### Transport

- **The port** is
  `HttpTransport : AutoCloseable { suspend fun execute(request: HttpRequest): Result<HttpResponse> }`
  in `commonMain`, over library-owned types:
  - `HttpMethod`;
  - a validated `Url`: `http` or `https` only, no user-info, and a query-free `redacted()`;
  - `HttpHeaders`: ordered, case-insensitive, with per-entry sensitive flags;
  - a `ByteArray` body;
  - `HttpResponse(status, headers, body, url, redirectChain)`.

  Measured: the types compile for both `jvm()` and `js(IR)`.
- **The transport never re-sends.** One request per hop was measured in every scenario, using
  three mechanisms together:
  - `retryOnConnectionFailure(false)`;
  - redirects followed by the transport itself, one OkHttp `Call` per hop;
  - a network interceptor that hides `Retry-After` on a `503` from OkHttp's follow-up logic
    (restoring it in the response) and refuses any second exchange for a hop.

  The client flags alone left a `503` with `Retry-After: 0` re-sent. The cost is failures on
  stale pooled connections, which an idle-connection TTL of 4 s (a parameter) removes.
- **Reading the body.**
  - The body is read inside `Callback.onResponse`, so cancellation reaches it: 0 ms, against
    29.4 s for the literal `okhttp-coroutines` pattern.
  - The body cap counts decoded bytes, which refused a 50 MB gzip bomb.
  - No body is read for `HEAD`, `1xx`, `204`, `304` or a followed redirect. OkHttp 5.5.0 blocks
    reading a `304` that carries `Content-Length`.
- **Redirects.**
  - Only `GET` and `HEAD` are followed, up to a configurable cap.
  - A per-hop policy check runs before any socket.
  - Every flagged-sensitive header and the default credential names are stripped on a
    cross-origin hop.
  - An `https` to `http` downgrade is refused by default.
- **Lifecycle.** The transport owns its `Dispatcher` and `ConnectionPool`. `close()` cancels
  calls, shuts down the executor and evicts the pool; without it, the JVM lingered 61 s.
- Source: [`http-transport.md`](http-transport.md).

### Outbound-URL policy

- **Three checks, each with its own job:**
  - **A per-hop URL check,** run before any socket, covers scheme, user-info, host form,
    reserved names and ports. It is the only check that stops IP-literal hosts. OkHttp never
    calls `Dns` for a digits-and-dots or colon host, so with a resolver guard alone,
    `2130706433`, `127.1`, `[::1]` and `169.254.169.254` connected.
  - **A guarding `Dns`** judges every resolved address and rejects a mixed answer whole. OkHttp
    connects only to that answer, which closes rebinding.
  - **A guarding `SocketFactory`** is a backstop on every connect attempt.
- **`Untrusted` forces `Proxy.NO_PROXY`.** A configured proxy would bypass the resolver guard,
  and a SOCKS proxy the socket guard as well.
- **The deny table** follows the IANA special-purpose registries and is a pure function over
  address bytes, in common code. It is default-deny for IPv6 outside `2000::/3`. The
  `InetAddress` predicates miss 16 of the 26 non-public addresses tried.
- **Numeric hosts.** A digits-and-dots host that is not strict dotted-decimal is refused. The JDK
  reads `127.1` and `0177.0.0.1` leniently, and libc reads `0177` as octal where the JDK reads it
  as decimal.
- **The policy is a closed pair, `Trusted` and `Untrusted`, each with tightening-only
  parameters.** Fetching from a private address is `Trusted`.
  - **`Untrusted`:**
    - refuses a request that carries credential headers;
    - defaults to ports 80, 443, 8080 and 8443;
    - caps redirects, bytes and time.
  - **`Trusted`:**
    - checks scheme, user-info and optional host pinning;
    - is `https`-only by default.
- **Refusals.** A refusal carries a coarse reason, never an address or a query.
- **The test seam is internal.** A fixtures-only factory reaches an `internal` relaxation that
  exempts loopback only, so Level 3 runs `Untrusted` against a loopback `mockwebserver3` with
  every other rule live.
- Source: [`outbound-url-policy.md`](outbound-url-policy.md).

### Response cache

- **The port.** `ResponseCache : AutoCloseable` has one suspend
  `getOrLoad(key: CacheKey<V>, policy, maxAge, loader): Result<Lookup<V>>`. `Lookup` is `Found` or
  `Missing`, so a confirmed miss is a stored value, never a null or a failure. The default is
  `CaffeineResponseCache`.
- **Decoded values are stored.** The whole load runs inside the cache loader: budget, transport,
  status check and decode. Three things follow:
  - a hit costs nothing;
  - a decode failure is never stored;
  - N coalesced callers cost one permit (measured: 50 callers, 1 load).
- **Failures are values with zero lifetime, removed by each waiter.** Caffeine's exceptional
  completion logs a stack trace per failure, and it left 45 of 2,000 immediate retries joined to
  the failed load; the fix gave 0.
- **How waiters wait.** Waiters use `asDeferred().await()`, because `CompletionStage.await()`
  cancels the shared load for everyone. A load nobody waits for still completes and lands in the
  cache.
- **`maxAge`** is an age check on read with a compare-and-remove. With 50 callers on a stale
  entry it ran 1 load, against 10 to 12 for invalidate-then-get.
- **Keys and time.** The key is the endpoint id plus its parameters sorted by name, never a
  tenant or a credential. The `Ticker` derives from the injected `TimeSource`.
- Source: [`response-cache.md`](response-cache.md).

### Call budget

- **The algorithm.** Fixed windows are aligned to multiples of their period since the Unix epoch,
  with a 1 s boundary guard, in an integer ledger. Every provider read counts in clock-aligned
  windows.
  - Against a server limit of 60 per minute, a greedy token bucket let 119 calls into one server
    minute.
  - Plain aligned windows overspent in 510 of 1,000 skewed trials.
  - The guarded ledger overspent in none, at a cost of about 2 % of grants.
- **Reservations.**
  - They are planned ahead under a coroutine `Mutex`, served first come, first served per lane,
    and awaited outside the lock.
  - A cancellation refunds its reservation and re-plans the waiters behind it.
  - A wait above `maxWait` fails fast with `BudgetExhausted(wait)`.
- **Lane shares.** A lane's reserved share is a floor that is held back, not lent. Lending drove
  interactive grants to zero in a plan-ahead ledger.
- **Server signals.**
  - The server's remaining count can only raise local usage.
  - A pause is the maximum of every hint, and it covers every lane.
  - `Retry-After` is parsed in common code, as seconds or any of the three HTTP-date forms; the
    parser matched `java.time` on 600,000 instants.
- **The hard daily stop.**
  - A `hard` window refuses until its boundary.
  - The opt-in persistence seam is a suspend `load`/`save` of snapshot values, written ahead in
    blocks, so a crash never under-counts.
  - The in-memory store is the default.
- **Permits.** A permit is spent once granted, unless it is released before sending. One permit
  covers a logical call, redirects included.
- **Time** is a `TimeSource` plus a library-owned `WallClock` of epoch milliseconds.
  `kotlin.time.Clock` and `Instant` need an error-level opt-in on Kotlin 2.2.0 and are stable
  only from 2.3.0.
- **Dispatcher sizing.** The transport's dispatcher is sized from the budget's largest
  instantaneous grant (`maxBurst`), because OkHttp's queue is invisible to the wait cap and to
  the call timeout.
- Source: [`call-budget.md`](call-budget.md).

### Test support and time

- **One virtual clock.** A clock built on a `TestCoroutineScheduler` supplies the `TimeSource`,
  the `WallClock`, the dispatcher and a same-thread maintenance executor, using only stable
  `kotlinx-coroutines-test` 1.10.2 API.
  - **Measured.** In one `runTest` it expired a cache entry, rolled a budget window, resumed
    `delay` and moved the wall clock 25 h.
  - **Fake all three clocks or none.** Faking one clock of three passes for the wrong reason.
  - **Injectable dispatchers.** Every library-owned scope takes an injectable dispatcher. A scope
    on `Dispatchers.Default` makes `delay` real inside `runTest`.
- **`FakeHttpTransport` is a script engine.**
  - The most recent matching stub wins.
  - Its steps run in order, and the last one repeats.
  - Each step has a virtual-time delay and either responds or fails.
  - Calls are recorded, and an unmatched request fails the test.
- **Fixtures** are raw HTTP message files (`*.http`), parsed by about 40 lines of code. No parser
  dependency and no JSON type reaches a signature.
- **The recorder** is a decorating transport. `WEBTOOLS_HTTP_RECORD` opens it, and any of twelve
  CI variables closes it. It never writes request headers, and it redacts sensitive query names
  and response headers.
- **Level 3** starts and closes `mockwebserver3` by hand, because `@StartStop` closes the server
  only from JUnit 5.13. It runs on real clocks under `runBlocking`.
- Source: [`test-support-and-time.md`](test-support-and-time.md).

### Public API shape and failure model

- **The marker.** One marker, `ExperimentalForInheritanceWebToolsApi`, at level `ERROR` with an
  empty `@Target`, is applied only through `@SubclassOptInRequired`.
  - That annotation is stable on 2.2.0. It gates implementing and delegating, but not mentioning
    a type, so constructing and calling the supplied defaults needs no opt-in.
  - A `fun interface` cannot carry the gate, so `WallClock` and `BudgetObserver` are plain
    interfaces.
  - The feeds module reuses the marker through its `api` dependency.
  - `@SupportedExtension` is defined when the first seam graduates.
- **Failures.** They form an open `abstract class HttpFailure : Exception` with two abstract
  groups (`TransportFailure`, `UpstreamFailure`), final leaves, and one shared `Closed`.
  - This follows `webtools-udp`'s precedent: a sealed root would make an added leaf a compile
    error in a consumer's `when`, and a runtime error in an already compiled one.
  - Library-produced reasons are open constants classes.
  - Cancellation is thrown and never wrapped.
  - A programmer error in developer-written configuration throws `IllegalArgumentException` at
    construction, as `webtools-udp`'s constructors do. Text from outside the process is parsed
    into a `Result`.
- **The facades.** There are two, built by factory functions:
  - `UpstreamClient` serves `Endpoint<T>` calls for one trusted upstream. It layers cache, then
    budget (inside the cache loader), then transport.
  - `UrlFetcher` serves administrator-supplied URLs, with no decode and no cache. Its policy
    pre-check costs nothing.

  The decoder travels with the `Endpoint`, because the cache key cannot see it. A `429` returns
  `RateLimited`, with no retry. A client closes only what it built.
- **Conventions.**
  - Every long-lived component is a `kotlin.AutoCloseable` with a non-suspend `close()`. The
    budget adds a suspend `flush()`.
  - ABI checking uses binary-compatibility-validator 0.18.2, because the plugin's built-in
    validator is experimental at 2.2.0.
  - Logging from common code goes through an `internal expect fun` with an slf4j actual on the
    JVM.
  - `webtools-http` uses one flat package.
- **Java callers are a non-goal,** because `suspend`, `Result` and `Duration` mangle JVM names.
- **Defaulted parameters.** Adding a defaulted parameter to a public constructor or function
  breaks binary compatibility for Kotlin callers. `@JvmOverloads` does not repair it, and
  `@IntroducedAt` needs Kotlin 2.3. See Consideration 3.
- Source: [`api-shape-and-failures.md`](api-shape-and-failures.md).

### Feeds module

- **Parser.** The default is Rome 2.1.0, an `implementation` dependency behind a library-owned
  `FeedParser` port.
  - **Why Rome.** It is the only candidate hardened by default: DOCTYPE refused, external
    entities and DTD loading off, entity expansion off. It also:
    - separates malformed XML from a document that is not a feed;
    - covers RSS 0.90 to 2.0, and Atom 0.3 and 1.0;
    - detects encoding in the RFC 7303 order.
  - **Its cost** is staleness: its last release was 2023-03-12.
  - **The alternatives.**
    - rssreader 3.12.0 is the documented swap. It turns malformed XML into an empty stream and
      ignores Atom `rel`.
    - prof18's multiplatform parser needs Kotlin 2.4.10 and coroutines 1.11.0.
- **Hardening does not rest on one library flag.**
  - The transport's decoded-byte cap bounds the document.
  - The default parser refuses any DOCTYPE in a prolog scan before Rome runs, because Rome skips
    a hardening feature its SAX provider does not recognise.
  - Per-field and per-count limits are parameters that only tighten.
  - A named Level 4c suite gates any parser behind the port: XXE, billion laughs, deep nesting,
    and attribute and element floods.
- **Conditional GET.** The caller owns the conditional-GET state.
  - A fetch takes the stored validators and returns `Fetched` or `NotModified` with new
    validators. A `304` is a success value. Python feedparser, gofeed and feed-rs do the same,
    and Rome's own stateful fetcher is archived.
  - The fetcher calls the transport directly, with no cache and no budget. Its constructor takes
    an `HttpTransport`, so the dependency on `webtools-http` is `api`.
  - An `untrusted(...)` factory builds and owns an `Untrusted` transport, and is the documented
    path.
- **Parsing is a plain function, not `suspend`.** It is CPU-bound, bounded by the byte cap, and
  moved off the caller's thread by the fetcher.
- **The module's shape** is Consideration 2.
- Source: [`feed-parsing.md`](feed-parsing.md).

### Where the findings differ, and which view holds

- **Dispatcher sizing.** The transport findings proposed 16 concurrent requests and 5 per host.
  The budget can grant its smallest window at once, so the dispatcher is sized from the
  budget's `maxBurst`.
- **Where transport settings live.** The transport findings put `TransportSettings` beside the
  policy in the transport's constructor, and the policy findings put them inside each policy.
  The settings belong to the policy, because `Untrusted`'s caps and timeouts are part of what
  makes it untrusted. The transport takes the policy.
- **Direct opt-in markers.** The budget findings marked `RateSignals`, `WindowReading`,
  `BudgetStore` and the snapshot types directly with the opt-in marker. A directly marked type
  in `Permit.complete` or in a constructor default forces the opt-in on every caller. Only the
  interfaces a consumer implements carry `@SubclassOptInRequired`, and the value types carry no
  marker.
- **Failure types.**
  - The budget findings' `BudgetClosed` and the transport findings' `Closed` are one
    `HttpFailure.Closed`.
  - The feeds findings' sealed `FeedFailure` rooted at `Throwable` follows the open `HttpFailure`
    convention instead, and so do their marker name and defaulted parameters.
  - `RefusalReason` and `ExhaustedReason` are constants classes, not enums.
  - `withPermit` is a plain `suspend fun`, not `inline`.
- **Lifecycle and packages.** The budget's `suspend fun close()` becomes `close()` plus
  `suspend fun flush()`. Its `…http.budget` subpackage folds into the one flat package.
- **Logging in common code.** The build findings left `commonMain` without a logger. The
  repository's rule asks for structured logging of lifecycle events, data flows and failure
  states; the `internal expect fun` seam satisfies it in common code. `BudgetObserver` stays a
  consumer hook, not the logging path.
- **Fixtures and `explicitApi()`.** The fixtures findings recorded that `explicitApi()` does not
  reach the fixtures compilation. `-Xexplicit-api=strict` on the fixtures compile task closes
  that gap; it checks visibility and return types, not KDoc.
- **Redirect permanence.** `HttpResponse.redirectChain` records URLs only, so a feed fetcher
  cannot tell a permanent move from a temporary one. Recording each hop's status in the chain
  closes that.

### Considerations

1. **How `commonMain` is kept free of JVM-only API.**
   - (a) No check. The ports sit in `commonMain`, unenforced.
   - (b) An unpublished check-only module that compiles the same `commonMain` for `js`. No new
     coordinate is published.
   - (c) A published `js` target. This adds a `webtools-http-js` artifact, a klib that needs a
     Kotlin 2.2+ consumer.

   Both (b) and (c) need every `commonMain` dependency to be multiplatform, and a `js` `actual`
   for the logging seam.

   **Recommendation: (b).** It turns "the ports are common code" into a compile error without
   publishing an artifact that no consumer needs.
2. **The feeds module's shape.**
   - (a) JVM-only, with one coordinate, `webtools-feeds`.
   - (b) Kotlin Multiplatform, like `webtools-http`. The port and the model go in `commonMain`
     and Rome in `jvmMain`, giving two coordinates: `webtools-feeds` and `webtools-feeds-jvm`.

   Both build, publish and substitute in a composite (measured).

   **Recommendation: (a).** Every parser candidate is JVM-only and no non-JVM consumer exists.
   Public signatures free of JVM types keep a later move to (b) open.
3. **How policy numbers are supplied.**
   - (a) Factory functions with builder lambdas over `internal` constructors. Policy numbers are
     builder properties with defaults, so a property can be added in a minor without a binary
     break.
   - (b) Flat public constructors with defaulted parameters. Each release that adds one keeps
     the previous constructor as a hidden overload, and the ABI check is the only guard.

   **Recommendation: (a).** It removes a class of silent binary break, and call sites read the
   same apart from the lambda.
4. **Where the single Java 11 setting lives.** The root build's shared configuration moves into
   the included `build-logic` build.
   - (a) The one constant lives there, as `WebToolsBuild.JAVA_FLOOR`.
   - (b) A property in the root `gradle.properties` that the plugins read. How an included build
     reads it in a consumer's composite is unmeasured.

   **Recommendation: (a),** which was measured in a consumer's nested composite.

### Known gaps

- **Nothing published or cross-platform was exercised.** Nothing was published to Maven Central
  or signed. No Linux build ran, and no IntelliJ import of `build-logic` or the fixtures source
  set was tried.
- **The public surface was not compiled.** Four things need a compile check, in `commonMain` and
  `jvmMain`, before the first unit lands:
  - the marker;
  - a gated interface;
  - a constructor default that mentions the gated interface;
  - a consumer without the opt-in.
- **The budget's close is undesigned.** A non-suspend `close()` on the `Mutex`-based budget is
  not designed.
- **Network behaviour.** Real provider idle timeouts, HTTP/2 coalescing over real TLS, and real
  DNS answers for the bypass catalogue were not measured. The 4 s idle-connection TTL is
  judgement.
- **Feed parsing.**
  - Rome's DOM memory amplification on a hostile 2 MiB document is unmeasured.
  - No corpus of real feeds was parsed.
  - How the JDK StAX reader treats a DOCTYPE when `SUPPORT_DTD` is off is not established.
- **Calendar-month rate windows** cannot be expressed with a fixed period.
- **Judgement defaults.** These defaults are judgement, not measurement:
  - the `Untrusted` port set;
  - the reserved-name list;
  - the feed limits;
  - the recorder's CI variable list.
