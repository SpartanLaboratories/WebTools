# Issue #69 — `feeds` (consequences)

- **Covers:** `SpartanLaboratories/WebTools#69`, *"webtools-feeds: RSS/Atom parser port with the Rome default, hardening suite, and a conditional-GET fetcher over
  `UrlFetcher`"*. It is unit 10, `feeds`, of [#55's architecture](../architecture.md) (§6.10, with §6.2, §6.6 to §6.9, §9 and §11 unit 10).
- **What this covers:** what the repository, its published artifacts, the sibling units and the neighbouring issues gain or lose with this unit, as built, and what it
  leaves behind. The file-level detail is in [`plan.md`](plan.md); the as-built record, with every measurement and deviation, is
  [`final-implementation.md`](final-implementation.md); the evidence is in [`research/README.md`](research/README.md) with its five findings files.
- **Status:** built at the code-landing commit `25678cf`, with the deviations of [`final-implementation.md`](final-implementation.md) §2. None changes a public
  signature, a sibling contract or a refinement of §5. [`../architecture.md`](../architecture.md) carries dated corrections (2026-11-06) for this unit: from the plan at
  the end of §6.10 and under §11 unit 10, and as built at §3, §12 and §13.
- **Baseline:** `master` `0374c8d`; branch `feat/issue-69-feeds`, with the research commits `88af7dc` (the questions), `066bf17` (the five findings files), `5ae0bda` (the
  verification and the summary) and `e89d86a` (the summary's settled design points), then `e3ecfe2` (stages 1 to 3) and `25678cf` (stage 4), the code-landing commit.
- **Related docs:** [`plan.md`](plan.md) and [`final-implementation.md`](final-implementation.md); [`research/README.md`](research/README.md); the systems-level
  [`../consequences.md`](../consequences.md); the as-built records of the units this one builds on, above all [`../68-call-facades/`](../68-call-facades/final-implementation.md)
  (`UrlFetcher`, `LoopbackFetchers`) and [`../63-test-support/`](../63-test-support/final-implementation.md) (the fakes and the virtual clock).
- **The system:** the feeds module, `webtools-feeds` in the state boards' registry; built with this landing. Its check module joins the `js-commoncheck` part of the
  build.

## 1. Published artifacts

| Artifact | What changes | Version |
|---|---|---|
| `webtools-feeds` (new, unreleased) | A new Kotlin Multiplatform module publishing `webtools-feeds` and `webtools-feeds-jvm` (Gradle Module Metadata beside each POM; a Maven build depends on `webtools-feeds-jvm`). In common code: the parser port `FeedParser` (Experimental to implement) with `FeedInput` and `FeedLimits`; the model `Feed`, `FeedEntry`, `FeedEnclosure`; `FeedValidators` and the sealed `FeedFetch`; the failure group `FeedFailure` (Experimental to subclass; its leaves `NotAFeed`, `Malformed`, `Refused`, `LimitExceeded` and their reasons Stable Core); `FeedFetcherConfig`, `FeedFetcher` and its factory over a `UrlFetcher`. On the JVM: `RomeFeedParser` and the owning factories `FeedFetcher.untrusted(userAgent) { }` and `FeedFetcher.untrusted(userAgent, policy) { }`. The ABI dump `webtools-feeds/api/webtools-feeds.api` is new. No test-fixtures variant. | `1.0.0`, unreleased; **not a release candidate** at this landing; published once #55's stack is complete |
| `webtools-http` | Nothing: no source, build-script or test change. Its main variants are an `api` dependency of the feeds module, and its test fixtures serve the feeds module's tests only. | unchanged |
| `webtools-udp`, `webtools-scraping`, `webtools-browser` | Nothing. | unchanged |

**What lands on a consumer's classpath with `webtools-feeds` 1.0.0:** `webtools-http` and, through it, `kotlinx-coroutines-core` 1.10.2 on the compile classpath (`api`); at
runtime `rome` 2.1.0 with `rome-utils` 2.1.0 and `jdom2` 2.0.6.1, and `slf4j-api` (Rome asks for 2.0.6; the module declares 2.0.13). Every class is Java 11 bytecode, and both
library variants declare `org.gradle.jvm.version` 11. No Rome, JDOM, SAX or slf4j type appears in a public signature.

**What a consumer of `webtools-feeds` 1.0.0 gets from this unit:**

- **A feed fetch with no state to keep in memory.** `FeedFetcher.fetch(url, validators)` sends `If-None-Match` and `If-Modified-Since` exactly as stored and returns
  `FeedFetch.Fetched` with the parsed feed, or `FeedFetch.NotModified` for a `304`, each carrying the validators to store next, the final URL and any permanent move. The caller
  owns the stored validators, the subscription URL, polling and "new items".
- **The stack's front door for administrator-supplied URLs.** On the JVM, `FeedFetcher.untrusted(userAgent)` builds and owns an `Untrusted` `UrlFetcher`, so a feed URL is checked
  against the outbound-URL policy before anything is spent, a document is capped at 2 MiB, redirects stay inside the policy, and a throttle is `RateLimited`.
- **A hardened parser.** `RomeFeedParser` reads RSS 0.90 to 2.0, RSS 1.0 and Atom 0.3 and 1.0 with Rome's repair of bare ampersands and HTML entities and its encoding detection; it
  refuses a DOCTYPE, and a prolog it cannot read the way the parser reads it, before any parser runs; and it bounds depth, attributes per element, elements and namespace URIs inside
  a SAX parser of its own per parse, so a hostile 2 MiB document is rejected in well under a second and two concurrent parses fit in about 128 MB of heap.
- **A model that is safe to persist and to render.** An entry's `id` is never blank and is frozen across releases (the guid or Atom id, else the link as written, else a SHA-256
  digest `webtools-feeds:v1:…`); every link is an absolute `http`/`https` link with its fragment, or `null`; text is bounded; summary and content are documented as untrusted markup.
- **Failures as values.** Every failure of the `UrlFetcher` passes through unchanged; a status that is not a feed is `UpstreamError`; a document that is not a feed, is malformed,
  is refused or exceeds a limit is a `FeedFailure` leaf with a constant message; a parser that throws is `DecodeFailure`; `Closed` once the fetcher is closed, under one
  of three component names around `close()` ([`final-implementation.md`](final-implementation.md) D5), so a caller matches `HttpFailure.Closed`.
- **A port for another parser**, with two abstract contract classes in this module's tests that hold any parser behind the port to the same conformance and hardening cases.

## 2. Blast radius in this repository

### Source

- **New trees only:** `webtools-feeds/` (common code for the port, the model, the failures, the fetcher, the DOCTYPE prolog scan and the identity, link and text rules; JVM code for
  `RomeFeedParser`, its Rome internals and the owning factories; the tests in `commonTest` and `jvmTest`; seven small synthetic feed fixtures and a byte copy of `logback-test.xml`
  under `jvmTest/resources`; the ABI dump) and `webtools-feeds-commoncheck/` (a build file and the `js` actuals). [`plan.md`](plan.md) §7 lists every file.
- **No existing source file changes.** `webtools-http` and its check module compile unchanged.
- **Duplicated by design:** the module's own copies of `webtools-http`'s `internal` logging seam (`Log`, `logger` and the slf4j actual), its non-fatal catch helper and its
  `flatMap`, because Kotlin's `internal` stops at the module; one more byte copy of `logback-test.xml`.

### Build

- **`settings.gradle.kts`:** the `include` line gains `:webtools-feeds` and `:webtools-feeds-commoncheck`. No `build-logic` change: the feeds module applies
  `webtools.kotlin-multiplatform-library` and its check module `webtools.kotlin-common-check`, as `webtools-http` and its check module do.
- **`.gitattributes`:** one line keeps the feeds test documents byte-exact (`webtools-feeds/src/jvmTest/resources/feeds/** -text`); the existing line covers `*.http` files only.
- **`./gradlew build` does more work:** the feeds suites ([`final-implementation.md`](final-implementation.md) §1 counts them by level), among them 18 hardening cases that
  each start a child JVM with a fixed heap of 128 MB ([`final-implementation.md`](final-implementation.md) §4 times them); `apiCheck` and `verifyJavaFloor` for the feeds
  module (the floor guard also reads Rome's, JDOM's and `rome-utils`' classes); the `js` compile of the feeds `commonMain`. No Node.js is needed.

### Documentation

- **`README.md`:** the `webtools-feeds` row of the artifacts table; the install line, with the `webtools-feeds-jvm` note and its unreleased status; a "Feeds" section after the
  HTTP client stack section; the module list and the check-module command of "Build & test"; the shared build logic table's multiplatform row naming `webtools-feeds`.
- **`CHANGELOG.md`:** two `### Added` entries under `## [Unreleased]`.
- **[`../architecture.md`](../architecture.md)** carries dated corrections (2026-11-06): from the plan at the end of §6.10 and under §11 unit 10, for the points of §5
  below; as built at §3 (the feeds check module's `js` dependency), §12 (Rome's DOM amplification) and §13 (the two feeds gaps).
- **[`../consequences.md`](../consequences.md)**, the systems-level document, settles this unit's row of its §7 and gains one line for this unit in its §8, linking to
  this folder.
- **No evergreen document for feeds:** the parent design keeps feeds to the README and the KDoc; `docs/webtools-http-architecture.md` is #70's.

## 3. Adoption

- **No existing class parses or fetches feeds**, so nothing is moved onto the module.
- **`UrlFetcher`** gets its first in-repository consumer; its contract is used as built.
- The `Connector` follow-ups of [`../architecture.md`](../architecture.md) §8 are unaffected.

## 4. Sibling units

[`plan.md`](plan.md) §12 has the exact contracts, with `path:line` against `0374c8d`.

- **Units 3 to 9 (#62 to #68):** nothing changes in them. The feeds module uses their public contracts as built: `UrlFetcher.fetch` returning every status but a throttle as a
  response, a `304` with its headers and no body; `HttpResponse.redirectChain` of `RedirectHop(requested URL, status)` and `HttpResponse.url` as the final URL; `HttpRequest`'s rule that
  a header value is HTAB or U+0020 to U+007E; `UpstreamError`, `DecodeFailure` and `Closed` with public constructors; `HttpFailure`'s `protected` constructors and its gate; the
  `Untrusted` policy's defaults (2 MiB, three redirects, no `https`-to-`http` hop); and, in its tests, `FakeHttpTransport` with `Step.respond` and `RequestMatch.header`,
  `VirtualClock`, `LoopbackFetchers.untrusted` and `webtoolsUrl`.
- **Unit 11, `http-docs` (#70):** it writes the final README pass and documents the feeds module from this unit's "Feeds" section and its KDoc: the tier of each type, what the caller
  owns, the failures, the limits and the heap figure, the logging rule that the module's own lines print a URL as scheme, host and port only, and the fact that the two contract
  classes are published only with `FeedParser`'s graduation. The `WebToolsDsl` KDoc in `webtools-http` lists that module's builders only; the five feeds builders carry the marker too
  ([`plan.md`](plan.md) §12, "Provides to `http-docs`"). As built, three statements of that KDoc go beyond [`plan.md`](plan.md) §7, and the documentation it writes takes them
  from the KDoc: the one shared state a parse grows and Rome's own loggers ([`final-implementation.md`](final-implementation.md) D4), the `Closed` names a fetch can meet
  around `close()` (D5), and the exact charset names `RomeFeedParser` reads, which `FeedRefusal.UnsupportedEncoding`'s KDoc lists.

## 5. Where the design refines the parent documents

None of these changes the unit's scope, a seam or its tier; each is a dated correction (2026-11-06) in [`../architecture.md`](../architecture.md), from the plan or, for
the settled open point, as built, and [`plan.md`](plan.md) §6 gives each one's reason and research basis.

- **Statuses parsed** (§6.10): a `200` or a `203` is parsed and every other `2xx` is `UpstreamError(status)`, where §6.10 parses any `2xx`.
- **A fourth layer of hardening** (§6.10): `FeedLimits` also bounds the document's structure (depth 256, attributes per element 100, elements 100,000, distinct namespace URIs 64),
  tightening-only like its other members; `RomeFeedParser` enforces them inside its own parser and the hardening contract holds any parser behind the port to them.
- **How `RomeFeedParser` drives Rome** (§6.10): a private `WireFeedInput` subclass with a SAX parser of its own per parse, the three-step `XmlReader` recipe, failures mapped by
  exception type and JAXP error code, and `dc:subject` as a category source, in place of `SyndFeedInput` with a plain `XmlReader`.
- **`FeedFetch`** (§6.10) is a sealed interface declaring `validators`, `finalUrl` and `permanentlyMovedTo`, so `NotModified` reports a permanent move too.
- **The fetcher's surface** (§6.10): both `fetch` overloads take `UrlFetcher.fetch`'s per-fetch block; `FeedFetcher.untrusted(userAgent, policy) { }` joins
  `FeedFetcher.untrusted(userAgent) { }`; the builder adds `accept`; the common factory's default parser comes from an `internal expect fun`.
- **Logging** (§6.6, §6.10): the module's own lines print a feed URL as scheme, host and port only.
- **Identity and refusals** (§6.10): the digest hashes the first present of title, summary or content, and first enclosure URL, with the published date; `Refused` also covers a
  prolog the scan cannot read the way the parser reads it; the scan separates empty and non-XML bodies (`NotAFeed`) from malformed XML.
- **The contract** (§6.10) is two abstract classes in `jvmTest`, one per test level; the memory-loading hardening cases run in a child JVM with a fixed heap.
- **The Level 3 fixture** (§11 unit 10) is `LoopbackFetchers.untrusted`, because the common factory takes a `UrlFetcher`.
- **Settled open point** ([`../consequences.md`](../consequences.md) §7, unit 10): the feeds check module depends on `webtools-http-commoncheck`'s `js` output, as measured
  ([`../62-http-core/research/common-check-plugin.md`](../62-http-core/research/common-check-plugin.md) F11), and compiles the feeds `commonMain` for `js` against it after every
  stage, with no source fallback ([`final-implementation.md`](final-implementation.md) §4, P2); Rome's DOM amplification is measured
  ([`research/parse-memory.md`](research/parse-memory.md)): 102 to 318 MB for two unguarded parses of a hostile 2 MiB document, 43 MB with the structural limits, and the
  fixed-heap gate holds every memory-loading case at 128 MB ([`final-implementation.md`](final-implementation.md) §4).

## 6. Neighbouring issues

- **[WebTools#93](https://github.com/SpartanLaboratories/WebTools/issues/93)**, `Url.redacted()` showing a URL's path: the feeds module's own lines never print a path, so a change
  to `redacted()` needs no change here; the `UrlFetcher` it composes, and the transport under it, print the path until #93 is resolved.
- **[WebTools#70](https://github.com/SpartanLaboratories/WebTools/issues/70)** (`http-docs`): the last unit of #55, blocked by this one (§4).
- **#58** (CI and repository practices): its CI would be the first place these suites run on every change, and on a JDK 11 runtime.
- **[WebTools#87](https://github.com/SpartanLaboratories/WebTools/issues/87)**, `UrlParseLinearTimeNonFunctionalTest` exceeding its bound when the level test tasks run in
  parallel: the feeds module adds Level 4c classes, some of them starting child JVMs, to a parallel run.
- **The IDE checks of #62 to #68** are still open; this unit's, with its read of the KDoc as a consumer sees it, join them at the next IntelliJ session (§8).

## 7. Follow-ups this unit leaves

Each is additive unless noted; [`plan.md`](plan.md) §4 and §13 name them.

- **`feeds-contract-variant`:** the conformance and hardening contract classes published as a test-fixtures variant of `webtools-feeds`, with the feature release that graduates
  `FeedParser` once a consumer's adapter has built against it (the parent §9).
- **`feeds-autodiscovery`**, **`feeds-json-feed`** and **`feeds-redirect-documents`:** HTML feed autodiscovery; a JSON Feed parser behind the port; the RSS `<redirect>`
  document and `itunes:new-feed-url`, moves the HTTP layer cannot see.
- **`feeds-poller`:** a stateful poller with a "new items" tracker, if a consumer wants one from the library.
- **`feeds-encoding-allowlist`:** more encoding names for the prolog scan (`ks_c_5601-1987`, `cp932`, `euc_jp`, `Big5-HKSCS`, `TIS-620` and others) after the same decoder
  probe.
- **`feeds-control-characters`:** tolerance of XML-illegal control characters (a filtering `Reader` before Rome); it changes today's `Malformed` result for such documents.
- **`feeds-future-last-modified`:** a guard against a future-dated `Last-Modified`, which needs the response's `Date`.
- **`feeds-namespace-cache`:** closing the residual growth of JDOM's process-wide namespace cache (a process-wide allowlist of URIs, or a streaming parser behind the port).
- **`feeds-duplicate-ids`:** detecting or repairing ids repeated within one feed.
- **`feeds-fetch-time-source`:** a `timeSource` on `FeedFetcherConfig`, if a test needs the per-fetch figures in virtual time.
- **A JDK 11 run** of the feeds suites (with #58's `jdk11-test-launcher`), **a Linux and macOS run** of the Level 3 and Level 4c suites, and the Level 5 checks still
  open (§8).
- **A compile-fail check for the gate:** `FeedParser` and `FeedFailure` join the gated types that only review guards today ([`../consequences.md`](../consequences.md) §7).

## 8. Known gaps

What the build leaves open, as of the code-landing commit `25678cf`. Where the build departs from [`plan.md`](plan.md), the deviation is in
[`final-implementation.md`](final-implementation.md) §2.

- **The JDK 11 runtime is not exercised.** The JDK 11 parser recognises the limit properties only under their URL names, which the module uses (measured on 11.0.32.1 in
  [`research/rome-adapter.md`](research/rome-adapter.md) F3); the Level 4c test that proves it runs only where `WEBTOOLS_JDK11_HOME` names a JDK 11, and the build host has none
  ([`final-implementation.md`](final-implementation.md) §4, P4). Until it has run, "JDK 11 or newer" for `webtools-feeds` rests on that measurement. Trigger: #58's CI, or
  any host with a JDK 11.
- **JDOM's namespace cache still grows.** A parse adds each distinct namespace URI it declares to `org.jdom2.Namespace`'s process-wide cache, which never evicts; the 64-URI cap
  bounds the growth to 64 entries (about 290 bytes each) per document, it does not remove it ([`final-implementation.md`](final-implementation.md) D4; follow-up
  `feeds-namespace-cache`).
- **Digest ids depend on the parser.** For an entry with no guid and no usable link, the id hashes the text and the date the parser produced: a Rome upgrade, a different parser behind
  the port, or a different `maxIdChars` can change it. The rule is frozen, and a new algorithm would carry a new prefix.
- **The structural limits are judgement.** Depth 256, 100 attributes per element, 100,000 elements and 64 namespace URIs rest on samples of 24 and 61 feed files (maximum depth 6,
  10 attributes, 11 namespace URIs) and on Woodstox's defaults as a comparison; a legitimate document beyond any of them fails with `LimitExceeded`.
- **Large feeds fail loudly.** More than 1,000 entries is `LimitExceeded(Entries)` and a document over 2 MiB `BodyTooLarge` (2 and 6 of 57 sampled feeds, all podcasts); `maxEntries`
  cannot be raised.
- **`https`-to-`http` redirects are refused under the defaults**, so such a feed is unreachable and a permanent move from `https` to `http` is never reported.
- **Validators can reach another server.** The transport sends both conditional headers to every hop, cross-origin included, so after a redirect changes a validator from one
  server can meet another; the caller drops its validators when `finalUrl` changes.
- **The prolog scan's decoder rule was measured on JDK 23.0.2 only**; it refuses rather than reads where it is unsure, so EUC-JP and GB18030 prologs with non-ASCII bytes, encoding
  names off its allowlist (common aliases such as `ks_c_5601-1987`, `cp932` and `euc_jp` among them) and RSS 0.91 Netscape (which requires a DOCTYPE) are refused, and a leading
  comment containing `--` is `Malformed` although Rome parses it.
- **HTML detection is narrow:** only `<!DOCTYPE html>` and an unprefixed `html` root are `NotAFeed(Html)`.
- **The model carries no content type**, so text and HTML content cannot be told apart by a consumer; both are documented as untrusted markup.
- **Rome's dates:** `DateParser` drops some real forms (an offset without a colon, Rome issue 695) and rolls some invalid dates over; only years outside 1 to 9999 are caught.
- **A secret in a feed URL's path** is printed by the `UrlFetcher` and the transport under it (#93); the module's own lines print scheme, host and port only.
- **A parse cannot be cancelled.** A parser behind the port that does not bound its own work can hold a parse permit.
- **The heap figure is per fetcher:** about 128 MB of headroom for the default two concurrent parses; it scales with the sum of every fetcher's `maxConcurrentParses` in the process.
- **The gate on implementers has no automated check:** the ABI dump does not record `@SubclassOptInRequired`, and `FeedParser` and `FeedFailure` add two gated types.
- **Measured only on the build host,** Windows 11 with JDK 23.0.2; Linux, macOS and other JDKs wait for #58's CI.
- **The KDoc has not been read as a consumer sees it.** The read of [`plan.md`](plan.md) §9.3 P5, of each public type's KDoc in its hover text and the dump, checking the tier
  statements, the "caller owns" list, the logging statements and the heap figure, was not made ([`final-implementation.md`](final-implementation.md) D6). Owner: the
  maintainer; trigger: the next IntelliJ session, with the IDE checks below.
- **IDE behaviour is untested:** completion of the factories, the `@WebToolsDsl` scoping of the five builders, Quick Documentation and the opt-in diagnostic when implementing
  `FeedParser`. Owner: the maintainer; trigger: the next IntelliJ session, together with the open IDE items of #62 to #68.

## 9. The plan's assumptions, as built

No assumption of [`plan.md`](plan.md) §3 failed. As far as this build shows:

1. **A test compilation sees the main compilation's `implementation` dependencies:** held. `jvmTest` compiles against Rome's types (`RomePipelineProbe`, the Level 2
   classes of the Rome internals) with no Rome dependency of its own.
2. **`SAXHandler.startElement` and `startPrefixMapping` are overridable, and a rejection thrown from them reaches the cause chain of `ParsingFeedException`:** held.
   `CountingSaxHandlerComponentTest` passes, and the hardening contract's element and namespace cases end in `LimitExceeded(Elements)` and
   `LimitExceeded(NamespaceUris)`, in process and in the child JVMs.
3. **`SyndFeedImpl(wire, false)` yields one `SyndEntry` per wire entry, in document order:** held. The `xml:base` cases of `RomeConversionComponentTest` and of the
   conformance contract, which pair the entries by index, pass.
4. **The JDK's SAX parser counts `xmlns` declarations as attributes:** held. `an xmlns flood on one element is LimitExceeded Attributes` and the child-JVM case
   `xmlns-distinct` (`LimitExceeded:NamespaceUris`) pass.
5. **The decoder-divergence rule holds on the build JDK only:** stands. On JDK 23.0.2 the differential test passes with the rule applied to readings that end
   `NotMarkup` too ([`final-implementation.md`](final-implementation.md) D1); no other JDK has run it.
6. **`java.class.path` of a Gradle test worker is a complete class path for a child JVM:** held. Every child-JVM case starts from it and passes; `HostileParseJvm`
   passes a class path longer than 24,000 characters through an `@argfile`.
7. **`Dispatchers.Default` is acceptable for CPU-bound parses of at most two documents per fetcher:** stands; not measured under a busy pool. A deployment with many
   fetchers or a busy `Default` pool configures `parseDispatcher`.
8. **No ids are stored from an earlier build:** holds. `webtools-feeds` 1.0.0 is unreleased, so the digest and the id rule are frozen at its first release.
