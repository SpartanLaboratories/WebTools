# Research: the feeds module (`feeds`)

[SpartanLaboratories/WebTools#69](https://github.com/SpartanLaboratories/WebTools/issues/69), unit 10
(`feeds`) of the HTTP client stack, [#55](https://github.com/SpartanLaboratories/WebTools/issues/55). The
systems-level design is [`../../architecture.md`](../../architecture.md) (§6.10, with §6.2, §6.6, §6.7, §6.8,
§6.9, §9, §12, §13 and §11 unit 10) and the systems-level consequences
[`../../consequences.md`](../../consequences.md) (§7 and §8). This folder researches the implementation
details that design leaves to the unit and that need evidence from outside this repository.

## The work

A new Kotlin Multiplatform module, `webtools-feeds` (package `com.spartanlabs.webtools.feeds`, coordinates
`webtools-feeds` and `webtools-feeds-jvm`, version `1.0.0`, unreleased), built on `webtools-http`, and its
unpublished check-only module `webtools-feeds-commoncheck`, which compiles the module's `commonMain` for `js`:

- **The parser port.** `FeedParser`, an interface a consumer implements only with the stack's opt-in, with a
  plain, non-suspending `parse(input: FeedInput): Result<Feed>`. `FeedInput` carries the document bytes, the
  response's `Content-Type` value and a base URL. `FeedLimits` (a factory and a builder whose values only
  tighten) bounds the entry count (1,000 by default; more fails loudly), text, id and link lengths, and the
  categories and enclosures per entry.
- **The model.** `Feed(title, link, description, entries)`; `FeedEntry(id, title, link, summary, content,
  author, publishedAtEpochMillis, updatedAtEpochMillis, categories, enclosures)`; `FeedEnclosure`. An entry's
  `id` is never blank: the guid or Atom id, else the link, else a pure-Kotlin digest. A `link` is an absolute
  `http` or `https` link, else null. Dates are epoch milliseconds, null when absent or unparseable, and are
  never used to decide whether an entry is new. Summary and content are untrusted markup.
- **Failures.** `FeedFailure`, an open group under the stack's `HttpFailure`, with the final leaves
  `NotAFeed(reason)`, `Malformed`, `Refused(reason)` (a DOCTYPE; a constant message with no document content)
  and `LimitExceeded(limit)`. The stack's own failures pass through unchanged: a status other than `2xx` and
  `304` is `UpstreamError`, a throttle is `RateLimited`.
- **The conditional GET.** The caller owns the state. `FeedValidators(etag, lastModified)` holds the header
  values verbatim; an oversize or non-printable value becomes null. `FeedFetch` is `Fetched` or
  `NotModified`; a `304` is a success value; the caller stores the returned validators after every response.
- **The fetcher.** `FeedFetcher` composes a `UrlFetcher`: a `GET` with an `Accept` list and `If-None-Match` /
  `If-Modified-Since` set verbatim from the validators; a `304` to a conditional request is `NotModified`; a
  `2xx` is parsed on an injectable dispatcher under a semaphore (`maxConcurrentParses`, 2 by default) and
  returned as `Fetched` with the response's validators. `fetch(url)` and `fetch(url, validators)` each take a
  trailing per-fetch block (cost, lane, wait cap) passed to the `UrlFetcher`. The common factory takes a
  `UrlFetcher` the caller owns; on the JVM, `FeedFetcher.untrusted(userAgent) { }` and
  `FeedFetcher.untrusted(userAgent, policy) { }` build and own an `Untrusted` one. A permanent move found in
  the redirect chain is reported to the caller, which owns the subscription URL.
- **The supplied parser.** `RomeFeedParser(limits)` on the JVM, over Rome 2.1.0 (`implementation` only; no
  Rome type in a public signature): the DOCTYPE prolog scan first, then Rome's encoding detection with its
  ampersand and entity repair on, `allowDoctypes` off, and conversion to the model under `FeedLimits`.
- **Hardening in three layers.** The transport's decoded-byte cap (2 MiB under `Untrusted`) bounds the
  document; the prolog scan, in common code, refuses every DOCTYPE before any parser runs, with Rome's own ban
  as a second lock; `FeedLimits` bounds fields and counts.
- **Tests.** Level 2 on the published fake transport (conditional headers sent verbatim; a `304` keeping or
  replacing validators; a `200` capturing them; a response with none returning `None`); Level 3 over the
  published `LoopbackFetchers.untrusted` fixture with a conditional-GET `mockwebserver3` dispatcher of the
  module's own, plus the owning factory's refusal of a loopback URL with no request reaching the server;
  Level 4a for the prolog scan and the id and link rules; and a Level 4c hardening suite under a fixed heap:
  XXE against a loopback server asserting zero requests, billion laughs, deep nesting, attribute and element
  floods at the fetcher's parse concurrency, a 2 MiB text node, `xi:include`, HTML, JSON, empty, invalid
  characters, UTF-16 and ISO-8859-1 documents, bare ampersands, a 50 KB `ETag`, a `javascript:` link, a 5 MB
  id. The hardening and conformance cases are abstract contract classes in `jvmTest`, run against
  `RomeFeedParser`; they are the gate for the Rome default.

What shapes the questions below:

- **The parts the module composes, as built.** `UrlFetcher.fetch(request) { cost; lane; maxWait }` returns
  every status as an `HttpResponse` except a throttle, which is `RateLimited`; a `304` comes back with its
  headers and no body read; `If-None-Match` and `If-Modified-Since` are not among the headers the transport
  controls, so they reach the server as written; `HttpResponse.redirectChain` lists each followed hop as the
  URL that was requested and the `3xx` status it answered, and `HttpResponse.url` is the URL the final
  response came from ([`../../68-call-facades/consequences.md`](../../68-call-facades/consequences.md) §4;
  `webtools-http/.../RedirectHop.kt`). The transport follows `301 302 303 307 308` itself for `GET`, at most
  three hops under `Untrusted`, and never retries.
- **The request URL type is not a link type.** `Url.parse` canonicalises as OkHttp's `HttpUrl` does and drops
  a `#fragment`, refuses user-info, refuses a host that is non-ASCII or holds an `xn--` label, and has no
  reference resolver (`webtools-http/.../Url.kt:3-15`). A feed entry's link is for display and navigation, so
  the model's link rule is the feeds module's own.
- **Logging.** The fetcher's own lines print a feed URL as scheme, host and port only. The `UrlFetcher` it
  composes, and the transport beneath, print `Url.redacted()`, which hides the query and shows the path
  ([#93](https://github.com/SpartanLaboratories/WebTools/issues/93)).
- **Common code.** `commonMain` is compiled for `js` by the check module, so it uses no JVM API; anything a
  common rule needs (a digest, a reference resolver, an encoding sniffer) is pure Kotlin. Everything is
  compiled to the Java 11 floor and tested on the build JDK.
- **Conventions of the stack** ([`../../research/api-shape-and-failures.md`](../../research/api-shape-and-failures.md)):
  failures are values in `kotlin.Result`; cancellation is thrown; configurable types are built by a factory
  with a builder over an `internal` constructor; library-produced reasons are open constants classes, never
  enums.

## Existing findings this research builds on

These are cited, not researched again.

- **[`../../research/feed-parsing.md`](../../research/feed-parsing.md):** F1 (Rome 2.1.0's hardening in
  `WireFeedInput`, its failure typing, its date parser returning null, its first-alternate Atom link, its
  dependencies at class-file majors 52 and 49), F2 and F3 (the alternatives and why they are not the
  default), F4 (stateless conditional GET; RFC 9110's weak comparison and the `If-Modified-Since` rules; the
  stale-validator pitfall), F5 (OWASP's DOCTYPE guidance and the JDK's processing limits), F7 (RFC 7303's
  charset order) and F8 (malformed feeds are common). Its gaps G1 (parse memory), G2 (no corpus), G6 (Rome's
  mappings) and G8 (redirect permanence) are what questions 1 to 4 below close.
- **[`../../62-http-core/research/common-check-plugin.md`](../../62-http-core/research/common-check-plugin.md)
  F11:** a check module depending on `webtools-http-commoncheck` compiles against its `js` output, measured.
- **[`../../65-okhttp-transport/research/loopback-test-mechanics.md`](../../65-okhttp-transport/research/loopback-test-mechanics.md)**
  F5 (what `mockwebserver3` writes of a response) and F6 (a child JVM started from a Gradle test worker with
  `java.home` and `java.class.path`, measured, with `webtools-http`'s `ChildJvm` test support as the
  precedent).
- **[`../../research/test-support-and-time.md`](../../research/test-support-and-time.md):** the conditional-GET
  `Dispatcher` is a few lines in the test and is not shipped.

The codebase blast radius is small (a new module, its check module, the settings, the README, the
CHANGELOG and `.gitattributes`) and is surveyed directly in the summary below.

## Questions

### 1. How should the supplied parser drive Rome 2.1.0, and how do Rome's beans map onto the feed model?

- **Options for the entry point.**
  - `SyndFeedInput.build(XmlReader)` as sketched: Rome builds its own `SAXBuilder`, with its DOCTYPE ban and
    entity settings, and its ampersand and entity repair on.
  - The parser builds its own hardened JDOM2 `SAXBuilder` (explicit features, and the JDK's XML processing
    limits such as `maxElementDepth`, `elementAttributeLimit` and `totalEntitySizeLimit`) and hands the
    document to `WireFeedInput.build(Document)`, then converts the `WireFeed` to a `SyndFeed`.
  - An overridable builder hook in Rome, if one exists.
  - For each: whether the repair (`XmlFixerReader`) and the encoding detection (`XmlReader`) still apply, what
    a hostile document can still reach, and what each step throws.
- **What must be established.**
  - Every exception each step can throw for a bad document and how it maps onto `Malformed`, `NotAFeed` and
    `LimitExceeded`: `ParsingFeedException`, `IllegalArgumentException("Invalid document")`, other
    `FeedException`s, `XmlReader`'s failures for an unknown or conflicting encoding, a JDK limit's
    `SAXParseException`, and where a `StackOverflowError` can arise (the JDOM build, Rome's converters, the
    serialisation of Atom `xhtml` content).
  - What Rome does with each odd or hostile input of the hardening list before any check of the module's own
    (HTML, JSON, an empty body, invalid XML characters, a UTF-16 BOM, ISO-8859-1 with a conflicting
    declaration, bare ampersands and `&nbsp;`, `xi:include`).
  - The mapping, for RSS 0.90 to 2.0, RSS 1.0 and Atom 0.3 and 1.0: feed title, link and description; an
    entry's id (`getUri()`: the guid and its `isPermaLink`, the Atom id, and any fallback Rome applies
    itself), link (the Atom alternate choice, RSS `link`, relative links and `xml:base`), summary and content
    (`getDescription()`, `getContents()`, `content:encoded`, Atom `text`, `html` and `xhtml` content),
    author (`getAuthor()`, `getAuthors()`, `dc:creator`), dates (`pubDate`, `dc:date`, Atom `published` and
    `updated`, Atom 0.3 `issued` and `modified`), categories (term and label) and enclosures (RSS
    `enclosure`, Atom `rel="enclosure"`).
- **Requirements.** No Rome type in a public signature; the module's own prolog scan is the first lock and
  Rome's ban the second, so neither depends on the other; a malformed document and a well-formed non-feed are
  told apart; the parse is a plain function of bytes and a content type; no expected failure is thrown;
  Java 11 floor; `rome` 2.1.0, `rome-utils` 2.1.0 and `jdom2` 2.0.6.1 as Rome pins them.
- **Evidence that settles it.** Rome 2.1.0's and JDOM 2.0.6.1's source at their tags; the JDK's JAXP
  documentation for the processing limits; probes on the build JDK that parse real-shaped and hostile
  documents through each entry point and record what comes back.
- **Findings file:** [`rome-adapter.md`](rome-adapter.md).

### 2. How much memory does a Rome parse of a hostile document at the 2 MiB cap take, and how should a fixed-heap test gate it?

- **What must be measured.** For documents of the `Untrusted` cap (2,097,152 bytes): a flood of empty
  elements; elements at the JDK's attribute limit; the deepest nesting that fits; one 2 MiB text node; a
  valid RSS 2.0 feed with as many entries as fit, and one with 1,000 entries; an Atom feed with deeply nested
  `xhtml` content. For each: the peak heap of one parse, of two concurrent parses (the default
  `maxConcurrentParses`) and of eight (the `Untrusted` request concurrency), the smallest `-Xmx` at which
  they complete, and the time per parse.
- **Options for the gate.**
  - A child JVM per case with a fixed `-Xmx`, started from the test as `webtools-http`'s `ChildJvm` support
    does.
  - `maxHeapSize` on the module's `nonfunctionalTest` task, and what then happens under `jvmTest` and the
    module's `test` alias, which run every level in one JVM.
  - An in-process bound on allocated or retained bytes (`com.sun.management.ThreadMXBean`,
    `MemoryPoolMXBean` peak usage) instead of a fixed heap.
- **Options if the amplification is large.** Keep the defaults and state the heap a deployment needs; lower
  `maxConcurrentParses`; a structural pre-scan in common code (element count, depth, attributes per element)
  before any parser runs; the JDK's processing limits through a builder of the parser's own (question 1).
- **Requirements.** The 2 MiB cap and `maxConcurrentParses` of 2 are the defaults, and the findings state the
  measured numbers against them plainly; the gate runs in a developer's `./gradlew build` without the network
  and without flaking; the tests run on the build JDK; the hardening cases run at the fetcher's parse
  concurrency.
- **Evidence that settles it.** Probes on the build JDK with Rome 2.1.0 and generated documents; the JDK's
  documentation of the memory and thread management beans; Gradle's `Test` task documentation for
  `maxHeapSize` and forking.
- **Findings file:** [`parse-memory.md`](parse-memory.md).

### 3. What identity, link and text rules should the model apply to what a parser returns?

- **Identity.** The guid or Atom id, trimmed, with an empty value treated as absent; the length cap
  (`maxIdChars`) and the fallback above it; the link fallback (the link as written in the document, or as
  resolved); the digest fallback: its inputs (title and date; and when both are absent, since an RSS 2.0 item
  may carry only a description), how the inputs are normalised and delimited, the algorithm (a pure-Kotlin
  SHA-256, a 64-bit FNV-1a, or another), the output form (a prefix that cannot be mistaken for a real id, the
  encoding, the length); and the fact that callers persist ids, so the rule is frozen. How mature feed readers
  identify an item that has no guid or id (Python feedparser, NetNewsWire, Miniflux, FreshRSS, Feedbin, Tiny
  Tiny RSS, gofeed, feed-rs, Rome).
- **Links.** What counts as an absolute `http` or `https` link (scheme case, network-path references
  `//host/...`, whitespace and control characters, non-ASCII hosts, a kept `#fragment`, the length cap
  `maxLinkChars`); resolving a relative reference against the base URL by RFC 3986 §5.2, with §5.4's examples
  as test vectors, and what Rome already resolves from `xml:base`; the schemes refused (`javascript:`,
  `data:`, `file:` and the rest); the same rule for a feed's link and an enclosure's URL.
- **Text.** Truncation at `maxTextChars` without splitting a surrogate pair; trimming; categories and
  enclosures beyond their caps; an enclosure `length` that is not a number, is negative or is huge; what an
  author string holds.
- **Requirements.** Common code, pure Kotlin, testable at Level 4a against fixed vectors; `id` never blank and
  identical across releases for the same entry; bounded work on hostile input (a 5 MB id, a 2 MiB text node);
  a link that is unsafe or unusable becomes null rather than reaching a caller.
- **Evidence that settles it.** RFC 3986; RSS 2.0, RSS 1.0 and Atom (RFC 4287) on `guid`, `id`, `link` and
  `xml:base`; FIPS 180-4's SHA-256 test vectors; the source of the feed readers named above.
- **Findings file:** [`entry-identity-and-links.md`](entry-identity-and-links.md).

### 4. What conditional-GET and redirect rules should the feed fetcher follow?

- **Validators.** Sending `If-None-Match` and `If-Modified-Since` together and verbatim (a weak `W/` tag, a
  value holding a list of tags); what a `304` may carry (RFC 9110 §15.4.5) and how stored validators are
  updated from it, field by field (RFC 9111 §4.3.4); a `200` without validators; a `304` to a request that sent
  none; a `2xx` other than `200` (`204`, a `206` to a request without `Range`, `226`); the bounds and
  character set a stored value should keep (ETag and date lengths seen in practice; printable ASCII against
  `obs-text`; the request header type refusing CR, LF and NUL); never reformatting `Last-Modified`.
- **Redirect permanence.** RFC 9110 §15.4 on `301` and `308` against `302`, `303` and `307`; which URL a
  subscription should move to after a chain that mixes permanent and temporary hops; whether a `304` reached
  through a permanent redirect reports the move; a permanent move to another host or from `http` to `https`;
  what feed readers do (NetNewsWire, Miniflux, FreshRSS, Feedbin, Python feedparser's `href` and `status`),
  and the guidance publishers are given for moving a feed (the RSS Advisory Board, Apple Podcasts'
  `itunes:new-feed-url` with a `301`).
- **The `Accept` header.** What mature feed readers send and a value suited to RSS and Atom.
- **Requirements.** The caller owns every piece of state; a `304` is a success value; the stored validators
  are replaced after every response; the transport follows `GET` redirects itself and reports each hop's
  requested URL and status; one request per hop and never a retry.
- **Evidence that settles it.** RFC 9110 and RFC 9111; the source and documentation of the feed readers named
  above; publishers' guidance for moved feeds.
- **Findings file:** [`conditional-get-and-redirects.md`](conditional-get-and-redirects.md).

### 5. How should the prolog scan find a DOCTYPE in a byte document before any parser runs?

- **Encoding.** Byte-order marks (UTF-8, UTF-16 and UTF-32 in both byte orders, and any others a parser
  honours); XML 1.0 Appendix F's detection without a BOM; the `Content-Type` charset under RFC 7303's order;
  EBCDIC, UTF-7 and other encodings in which `<!DOCTYPE` does not appear as ASCII bytes; which encodings
  Rome's `XmlReader` accepts, so that the scan never reads a prolog differently from the parser.
- **The prolog.** XML 1.0's grammar (an XML declaration, then comments, processing instructions and white
  space, then a document type declaration or the root element); what the scan skips, where it stops, and how
  far it reads (the prolog only, up to the first element start).
- **Bypasses.** A DOCTYPE after comments and processing instructions; the text `<!DOCTYPE` inside a comment or
  a processing instruction; case variants; white space and BOM tricks; junk before the XML declaration; an
  encoding the parser would honour and the scan would misread; whether Rome's repair of entities and
  ampersands can turn bytes the scan passed into a DOCTYPE.
- **HTML.** A `<!DOCTYPE html>` in any case, and an `html` root without one, are web pages, not feeds; how the
  scan reports them apart from a DOCTYPE it refuses.
- **Requirements.** Common code, pure Kotlin, a function of the bytes and the content type, testable at Level
  4a; bounded work; it refuses every DOCTYPE the parser would read, and refuses rather than guesses when it
  cannot decode the prolog; no parser runs on a refused document.
- **Evidence that settles it.** XML 1.0 (Fifth Edition) §2.8 and Appendix F; RFC 7303 §3; the source of
  Rome's `XmlReader` and of the JDK's XML entity scanner; OWASP's XXE guidance; published DOCTYPE and
  encoding bypasses of XML parsers.
- **Findings file:** [`prolog-scan.md`](prolog-scan.md).
