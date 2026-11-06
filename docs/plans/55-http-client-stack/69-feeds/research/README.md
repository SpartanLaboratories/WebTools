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

## Verification

### Outside-world claims

Every load-bearing claim of the five findings files was re-opened at its source (the named tag, commit, page or
specification) on 2026-11-06.

**117 claims: 116 hold and 1 holds in part; none fails and none is unclear.**

| Findings file | Claims | Result |
|---|---|---|
| [`conditional-get-and-redirects.md`](conditional-get-and-redirects.md) | 31 | 30 hold; claim 23 holds in part |
| [`entry-identity-and-links.md`](entry-identity-and-links.md) | 29 | all hold |
| [`parse-memory.md`](parse-memory.md) | 15 | all hold; claim 9 with a location note |
| [`prolog-scan.md`](prolog-scan.md) | 20 | all hold |
| [`rome-adapter.md`](rome-adapter.md) | 22 | all hold |

- **The claim that holds in part.** `conditional-get-and-redirects.md` claim 23 (Liferea moves a subscription on a
  `301` or `308` hop): the quoted condition shortened its last operand. At `364b2ea`, `src/subscription.c` reads
  `subscription->updateJob->request->source`, under the comment "update the subscription URL on permanent redirects".
  The meaning is unchanged, and the quote in the findings file now matches the source.
- **Location notes.** `parse-memory.md` claim 9: the ancestor walk (`p = p.getParent()`) is in `Element.java`, the
  call to it in `ContentList.java`; the findings file now cites both. Every other quote is verbatim at the cited
  location, apart from line wraps, hyphenation at line breaks and escaped characters.

### Codebase facts

The repository facts the findings rest on were checked against `master` at `0374c8d`:

- `HttpRequest.kt:27` requires every header value to be sendable, and `HttpSyntax.kt:29` defines sendable as HTAB or
  U+0020 to U+007E (`conditional-get-and-redirects.md` F6).
- `RedirectDecision.kt:21` follows `301 302 303 307 308`; `:92-94` keeps a request's headers on a same-origin hop and
  drops only the sensitive entries on a cross-origin one (F14).
- `TransportSettings.kt:21`: `allowHttpsToHttp` is false in both default sets (F11).
- `HttpHeaders.kt:33`: `get` returns the last value of a name; `getAll` returns every value (F4).
- `ChildJvm.kt:31-41` starts its child with `java.home` and `java.class.path`, discards its output, waits 20 s and
  destroys the child on timeout (`parse-memory.md`, the gate).
- `floorDiv` and `encodeToByteArray` are already used in `webtools-http`'s common code (`HttpDate.kt`,
  `WindowLedger.kt`, `UrlEncoding.kt`), which its check module compiles for `js` (`entry-identity-and-links.md` F8).
- No test task in the repository sets `maxHeapSize` or `jvmArgs`, and the multiplatform plugin's `jvmTest` task and
  `test` alias run every level in one worker (`webtools.kotlin-multiplatform-library.gradle.kts`) (`parse-memory.md`
  F8).

### Earlier findings that do not hold as stated

- [`../../research/feed-parsing.md`](../../research/feed-parsing.md) F7 and its claim 5 give Rome's `XmlReader`
  order as mark, then header charset, then XML declaration. The quoted Javadoc is accurate, but the code lets the
  declaration win in lenient mode, the mode the supplied parser needs (`rome-adapter.md` F4 and claim 6;
  `prolog-scan.md` F1). The design here follows the code.

## Summary

The five findings files answer every question; the detail, the measurements and the sources are in each file.

### The supplied parser: how `RomeFeedParser` drives Rome 2.1.0

- **Construction.** Rome is driven through a private subclass of `WireFeedInput` whose protected
  `createSAXBuilder()` returns Rome's public `SAXBuilder` over a SAX factory of the parser's own
  (`SAXParserFactory.newDefaultInstance()`, created per build, namespace-aware, not validating). Rome's four
  hardening features are set on it directly, so a provider that does not recognise one fails the build instead of
  silently skipping it; entity expansion is off and the entity resolver returns nothing. The JDK's processing limits
  are set as parser properties under their URL names (`http://www.oracle.com/xml/jaxp/properties/maxElementDepth`
  and `.../elementAttributeLimit`): the `jdk.xml.*` spellings are not recognised by the JDK 11 parser, which then
  fails every document. The document goes in through `build(Reader)` over an `XmlReader`, so Rome's ampersand and
  entity repair and its encoding detection apply, and the result is wrapped with `SyndFeedImpl(wireFeed, false)`.
  Everything is created per `parse` call; nothing static is read or written
  ([`rome-adapter.md`](rome-adapter.md) F1 to F3, F11).
- **Why not `SyndFeedInput`, and why not Rome's own builder.** `SyndFeedInput` keeps its `WireFeedInput` private, so
  no limit can be set through it. Rome's own `createSAXBuilder()` uses JDOM's process-wide SAX factory: from JDK 22 a
  JAXP limit set on one parser of that factory stays on it for every later JDOM parse in the JVM, concurrent builds
  race (2,806 wrong outcomes in 6,400), and with Xerces on the class path every document fails
  ([`rome-adapter.md`](rome-adapter.md) F2; [`parse-memory.md`](parse-memory.md) F7).
- **Encoding.** `XmlReader` runs inside a three-step recipe: ASCII white space before the first `<` is dropped; input
  that starts with a UTF-16 mark or the UTF-16 `<?` pattern uses the constructor that ignores the header; anything
  else uses the header-aware constructor with `UTF-8` as the default, retried once without the header when the
  header names a charset the JDK does not know. The plain constructor fails or corrupts 7 of 26 encoding vectors; the
  recipe passes all 26 on JDK 23 and JDK 11. In Rome's lenient mode the XML declaration wins over the mark and the
  header ([`rome-adapter.md`](rome-adapter.md) F4).
- **Failures.** Mapped by type and by the JDK's documented error code, never by message:
  - a limit code in the cause chain (`JAXP00010006` depth, `JAXP00010002` attributes, and the parser's own element
    and namespace counts below) is `LimitExceeded`; a `StackOverflowError` is `LimitExceeded` too, as a backstop;
  - the DOCTYPE feature named in the chain (Rome's second lock firing) is `Refused`;
  - exactly `IllegalArgumentException("Invalid document")`, the class and not a subclass, is `NotAFeed`;
  - every other `ParsingFeedException`, any `IOException` from `XmlReader`, any other `IllegalArgumentException`
    (a `NumberFormatException` from `<cloud port="abc">` escapes Rome unwrapped and carries document text) and any
    other runtime exception from the Rome region is `Malformed`, with no cause or message kept;
  - `OutOfMemoryError` and other errors are not caught; an exception from the module's own conversion code leaves
    `parse`, and the fetcher reports it as `DecodeFailure`.
  ([`rome-adapter.md`](rome-adapter.md) F5.)
- **Mapping.** Rome trims nothing; an empty guid or id comes back as `""`; a permalink guid is copied into the entry
  link even when it is not a URL; an Atom 1.0 feed's `getLink()` is its own `rel="self"` URL, so the site link is the
  first alternate of `getLinks()`; relative links and `xml:base` are never resolved, and only the Atom feed and entry
  wire beans carry an `xml:base`; `dc:subject` reaches only the entry's `DCModule`, never `getCategories()`; an
  enclosure length is 0 when absent or unparseable and a negative one passes through; `DateParser` misreads some
  inputs into absurd years. The conversion owns each of these, per field and format as tabled in
  [`rome-adapter.md`](rome-adapter.md) F9 and F10.
- **Quiet and stateless.** Rome logs nothing on this path. `Atom10Parser.setResolveURIs`,
  `XmlReader.setDefaultEncoding` and `ConfigurableClassLoader.setClassLoader` are JVM-global and never called. One
  parser instance is safe from any number of threads because every call builds its own objects
  ([`rome-adapter.md`](rome-adapter.md) F11).

### Parse cost, structural limits and the fixed-heap gate

- **Unguarded cost at the defaults** (2 MiB cap, two concurrent parses). A hostile, DOCTYPE-free 2 MiB document needs
  54 to 168 MB of heap for one parse and 102 to 318 MB for two; a realistic 1.4 MB feed needs 7 MB. CPU and stack
  are worse: JDOM's builder is quadratic in nesting depth (a 299,572-level document parses in 653 s, and a parse
  cannot be cancelled), 10,000 attributes per element (the JDK's default limit) cost about 5 s and 1.7 GB of
  allocation, and markup nested 1,800 to 4,400 levels deep inside an RSS `<description>` or an Atom `xhtml` text
  overflows a 1 MB stack, the band depending on JIT state ([`parse-memory.md`](parse-memory.md) F1 to F4;
  [`rome-adapter.md`](rome-adapter.md) F6, F7).
- **Four structural limits remove it.** Element depth 256 and attributes per element 100, as JDK properties on the
  parser's own SAX factory, and a counting `SAXHandler` (set through `setSAXHandlerFactory`) with an element cap of
  100,000 and a cap of 64 distinct namespace URIs. With them every hostile shape is rejected in 45 to 160 ms, the
  worst accepted shape needs 43 MB for two parses (144 MB for eight), and realistic feeds are unaffected. The real
  feeds sampled nest at most 6 levels and carry at most 10 attributes per element and 11 distinct namespace URIs. A
  byte-level structural pre-scan is not needed: the handler counts what the parser actually builds, in the same pass
  ([`parse-memory.md`](parse-memory.md) F5, F10; [`rome-adapter.md`](rome-adapter.md) F6, F7).
- **JDOM's namespace cache.** `org.jdom2.Namespace` keeps every distinct namespace URI it sees in a static map for the
  life of the JVM (about 290 bytes each; 44 MB per hostile 2 MiB document, 174 MB after four with fresh URIs). The
  64-URI cap stops such a document in 7 to 12 ms and bounds the growth to 64 entries per hostile document; it counts
  distinct URIs, not declarations, because a real feed repeated one declaration 507 times
  ([`parse-memory.md`](parse-memory.md) F6).
- **Deployment heap.** With the limits, about 128 MB of headroom covers the default two concurrent parses; without
  them, about 640 MB. The figure scales with the number of parses running at once in the process, which is the sum
  of every fetcher's `maxConcurrentParses` ([`parse-memory.md`](parse-memory.md), Recommendation).
- **The gate.** Each hostile case runs in a child JVM started as `webtools-http`'s `ChildJvm` support starts its child,
  with `-Xmx128m` and `-XX:+ExitOnOutOfMemoryError`, running two parses at once through the module's real parse path
  and printing one result line per parse; the test asserts the exit code, the result kinds and a 30 s bound that
  detects a hang. 128 MB is three times the worst accepted shape, and the unguarded `<item/>` and Atom `<entry/>`
  floods exit with the out-of-memory code at that heap in 12 of 12 runs, so the gate catches the regression it
  exists for. A fixed heap on `nonfunctionalTest` alone leaves `jvmTest` and the `test` alias, which run every class
  in one 512 MB worker, ungated; an allocation bound measures garbage, not heap safety
  ([`parse-memory.md`](parse-memory.md) F8, F9).

### The DOCTYPE prolog scan

- **Three readings, no chosen encoding.** Rome's lenient `XmlReader` lets the declaration beat the mark and the header,
  ignores a UTF-16 mark under `text/*` types or no header, and consumes a second mark on its `text/html` retry, so a
  scan that first picks an encoding by RFC 7303 or XML Appendix F order is bypassed. The scan skips the whole leading
  run of marks, walks the prolog in three readings at once (ASCII-compatible bytes, UTF-16BE units, UTF-16LE units)
  and returns the worst outcome ([`prolog-scan.md`](prolog-scan.md) F1, F2).
- **What it refuses.** UCS-4 and UTF-32 marks and prefixes, the EBCDIC prefix `4C 6F A7 94`, any encoding name in the
  header or a declaration-shaped start that is not on a short allowlist (UTF-8, US-ASCII, ISO-8859-N, windows-125x,
  KOI8, Shift_JIS and windows-31j, EUC-KR, GBK, GB2312, Big5, the UTF-16 family, and EUC-JP and GB18030 under a
  stricter rule), and a prolog longer than 64 KiB per reading. The JDK's UTF-16 decoder drops the unit after an
  unpaired surrogate and its EUC-JP and GB18030 decoders drop the byte after a malformed lead byte, which lets a
  comment end for a scan but not for the parser; a reading that would report no DOCTYPE after reading such a sequence
  is refused instead ([`prolog-scan.md`](prolog-scan.md) F5).
- **The walk.** White space, processing instructions and comments are skipped; `<!DOCTYPE` in any case gives a
  DOCTYPE, `html` as its name a web page; `<!` followed by any other letter is a DOCTYPE; the first start tag ends the
  walk, an `html` root being a web page; a `--` inside a comment, or a comment or processing instruction that never
  ends, is ill-formed, because Rome's repair reader closes comments differently from the specification.
- **Outcomes.** No DOCTYPE: parsing proceeds. A web page: `NotAFeed(Html)`. A DOCTYPE, or a prolog that cannot be
  read identically to the parser: `Refused`, with a constant message. Ill-formed: `Malformed`. No parser runs on any
  outcome but the first. A 2 MiB body scans in under 0.25 ms; the longest prolog in 70 fetched documents was
  149 bytes.
- **Evidence.** A pure-Kotlin implementation compiled for the JVM and as a Kotlin/JS klib on Kotlin 2.2.0 gives the
  expected outcome for all 125 vectors of the catalogue, and a seeded differential against the real Rome and JDK
  pipeline (1.8 million general cases, 0.8 million targeted 16-bit cases, six `XmlReader` constructor shapes) found no
  document whose DOCTYPE reached the parser while the scan reported none ([`prolog-scan.md`](prolog-scan.md),
  Recommendation and vector catalogue).

### Entry identity, links and text

- **Identity, frozen.** An entry's id is (1) its guid or Atom id trimmed of ASCII white space, when non-empty and at
  most `maxIdChars`; else (2) its link exactly as written (trimmed of control characters and spaces at the ends, with
  no white space, control character or backslash inside, and no scheme or an `http`/`https` one), when at most
  `maxIdChars`; else (3) a digest. `isPermaLink` plays no part, and nothing is case-folded, normalised or resolved, so
  the id does not change when a document moves (RFC 4287 §4.2.6) ([`entry-identity-and-links.md`](entry-identity-and-links.md)
  F1, F2).
- **The digest.** SHA-256, in pure Kotlin, over a length-prefixed message: the domain string
  `webtools-feeds/entry-id/1`, then the first present of title, summary-or-content and first enclosure URL (each
  trimmed, cut to 4,096 UTF-16 units without splitting a surrogate pair, unpaired surrogates replaced by U+FFFD so the
  JVM and `js` encoders agree), then the published date in whole epoch seconds. The id is `webtools-feeds:v1:` plus
  the first 16 bytes as 32 lower-case hex digits; an entry with nothing at all is kept with the constant id of the
  domain string. Fourteen vectors, reproduced by an independent implementation, pin it
  ([`entry-identity-and-links.md`](entry-identity-and-links.md) F7, Recommendation).
- **Links.** Control characters and spaces are trimmed at the ends; the length is checked before any scan; any
  remaining control character, white space or backslash, any user-info, and any scheme but `http`/`https` gives null;
  a relative reference resolves by RFC 3986 §5.2 (strict) against the response's final URL, with the Atom feed's and
  entry's `xml:base` applied in turn, and gives null without a valid base; a fragment and the percent-encoding are kept
  as written. The rule reproduces 39 of the 42 rows of RFC 3986 §5.4; the other three (`g:h`, `http:g`, the empty
  reference) are null by rule. A link Rome derived from a permalink guid is used only if it is already absolute
  ([`entry-identity-and-links.md`](entry-identity-and-links.md) F5, F6).
- **Text.** Text fields are trimmed of ASCII white space, blank is absent, and truncation at `maxTextChars` never
  splits a surrogate pair. Categories are trimmed, blanks and exact duplicates dropped, then capped; enclosures with no
  usable URL or a repeated one are dropped, then capped, and a length of 0 or less is unknown
  ([`entry-identity-and-links.md`](entry-identity-and-links.md) F9).
- **Default numbers.** `maxIdChars` 2,048; `maxLinkChars` 8,000 (RFC 9110's interoperability floor for URIs);
  `maxTextChars` 262,144 (the largest text in a 57-feed sample is 174,346 characters, and 65,536 would truncate two
  of the 57); `maxCategoriesPerEntry` 32; `maxEnclosuresPerEntry` 8. A default can only be tightened, so the generous
  side is the safe one; `maxIdChars` is part of the frozen identity
  ([`entry-identity-and-links.md`](entry-identity-and-links.md) F4, Recommendation).

### Conditional GET and redirects

- **Request.** `If-None-Match` and `If-Modified-Since` are sent together whenever each is known, exactly as stored,
  with `Accept: application/atom+xml, application/rss+xml, application/rdf+xml;q=0.9, application/xml;q=0.8,
  text/xml;q=0.8, */*;q=0.1` (no `application/feed+json`, since the parser reads XML)
  ([`conditional-get-and-redirects.md`](conditional-get-and-redirects.md) F1, F13).
- **Validators.** A stored value is kept only if, trimmed of SP and HTAB, it is non-empty, at most 256 characters
  (ETag) or 64 (Last-Modified), made only of U+0020 to U+007E, and, for the ETag, not `*`; otherwise it is null, never
  truncated. The character rule is a correctness rule: the stack's `HttpRequest` refuses any other header character,
  so a stored non-ASCII tag would fail every later fetch of that feed. A `200` replaces both validators; a `304`
  replaces each one it carries (an unusable one becomes null) and keeps the others; a header that appears more than
  once counts as absent (F2 to F7).
- **Statuses.** `200` and `203` are parsed; a `304` to a request that carried a validator is `NotModified` and to one
  that carried none `UpstreamError(304)`; every other `2xx` is `UpstreamError`, because none is a complete
  representation for a plain `GET` (`204` and `205` have no content, `206` answers only a range request, `226` only a
  request with `A-IM`) (F5).
- **Permanent moves.** `permanentlyMovedTo` is the target of the last hop in the leading run of `301`/`308` hops,
  reported with `Fetched` and with `NotModified`, never for a chain that ends in an error, and never when it equals the
  requested URL; host and scheme are not filtered. A `302` at the head keeps the original URL canonical (RFC 9110
  §15.4.3); SimplePie, and so FreshRSS, apply exactly this rule (F9 to F11).
- **Shape.** `FeedFetch` is a sealed interface declaring `validators`, `finalUrl` and `permanentlyMovedTo`, with final
  `Fetched` and `NotModified` leaves, as the stack's `Lookup` is shaped (F15).
- **The transport as built** sends both conditional headers on every hop, cross-origin included (neither is
  controlled nor sensitive), so validators from one final server can reach another after a redirect changes; a caller
  that sees `finalUrl` change drops its validators. `allowHttpsToHttp` is off by default, so an `https` feed that
  redirects through `http` is refused (F14, F11).

### Where the findings differ, and which view holds

- **JDK property names.** The URL spellings hold: `jdk.xml.maxElementDepth` and `jdk.xml.elementAttributeLimit` are
  not recognised by the JDK 11 parser (measured on 11.0.32.1), while the URL forms work on 11, 17, 21 and 23
  ([`rome-adapter.md`](rome-adapter.md) F3; [`parse-memory.md`](parse-memory.md) uses the `jdk.xml.*` names on JDK 23).
- **How the builder is isolated.** Both isolate the limits from JDOM's shared factory. A builder of the parser's own
  that sets Rome's features directly ([`rome-adapter.md`](rome-adapter.md)) holds over calling Rome's builder and then
  replacing its reader factory ([`parse-memory.md`](parse-memory.md) F7): it never touches the shared factory, and a
  provider without the DOCTYPE feature fails the build rather than being skipped.
- **Stack thresholds.** The two measurements (an overflow at 2,000 levels, and survival at 2,000 with an overflow at
  5,000) both fall inside the JIT-dependent band of 1,800 to 4,400 levels on a 1 MB stack; under a depth limit of
  256 the point is moot ([`parse-memory.md`](parse-memory.md) F4).
- **`xml:base`.** Rome resolves nothing and exposes `xml:base` only on the Atom feed and entry wire beans, which the
  parser holds before it converts them; the link rule applies those two and no other placement (none occurs in the
  sample) ([`entry-identity-and-links.md`](entry-identity-and-links.md) F5; [`rome-adapter.md`](rome-adapter.md) F10).
- **`XmlReader`'s order.** [`../../research/feed-parsing.md`](../../research/feed-parsing.md) F7 gives Rome's order as
  mark, then header, then declaration, as its Javadoc says; that holds in strict mode only. The parser runs lenient,
  where the declaration wins, and both the encoding recipe and the prolog scan are built for that order.
- **A parser that throws.** Inside the supplied parser, Rome's own runtime exceptions on hostile input are `Malformed`;
  an exception that leaves any parser's `parse` is the fetcher's `DecodeFailure`. Both hold, at different layers.

### What these findings change against the parent design

- **Which statuses are parsed.** [`../../architecture.md`](../../architecture.md) §6.10 parses any `2xx`; these
  findings parse `200` and `203` only and return every other `2xx` as `UpstreamError`.
- **A fourth layer of hardening.** §6.10 hardens with the byte cap, the prolog scan and `FeedLimits`'s field and count
  bounds. The findings add structural limits inside the supplied parser (depth 256, attributes per element 100,
  elements 100,000, distinct namespace URIs 64), each reported as `LimitExceeded`, without which a 2 MiB document
  costs minutes of CPU, overflows the stack or grows a JVM-wide cache. They fit either as fixed constants of
  `RomeFeedParser` or as tightening-only members of `FeedLimits`.
- **How the supplied parser drives Rome.** §6.10 names `SyndFeedInput` with `allowDoctypes = false`, a plain
  `XmlReader`, and three exception mappings; the findings replace these with the private `WireFeedInput` subclass and
  SAX factory, the encoding recipe and the fuller exception table above, and add `dc:subject` as a category source.
- The 2 MiB cap, the two concurrent parses and the 1,000-entry limit stay as they are.

### Options that do not fit

- `SyndFeedInput` unchanged: no limit can be set without JVM-wide system properties, which reach the host
  application's own XML parsing.
- Rome's own builder with limits added: the limits leak to every JDOM parse in the JVM from JDK 22 and race.
- Building the JDOM document separately and handing it to `WireFeedInput.build(Document)`: Rome's repair and encoding
  detection are lost, so bare ampersands, `&nbsp;` and ISO-8859-1 feeds fail.
- A byte-level structural pre-scan: cheap, but it can disagree with the parser about a malformed document, and the
  handler counts exactly.
- A heap set on `nonfunctionalTest`, or an in-process allocation bound, as the memory gate (above).
- A prolog scan that picks one encoding first; searching for the text `<!DOCTYPE`; modelling the repair reader's
  comment grammar.
- A 64-bit non-cryptographic digest (collidable inside a feed), undelimited concatenation of fields (a title `ab`
  hashes as a title `a` followed by a field `b`), a random fallback id (changes on every parse), case folding or
  Unicode normalisation of ids.
- Storing validators as received, truncating them, or reformatting `Last-Modified`.
- Adopting a move only when every hop is permanent (under-reports `A -301-> B -302-> C`), or from any permanent hop
  (adopts a temporary target).

### The codebase blast radius

Surveyed directly against `master` at `0374c8d`:

- **New:** the module `webtools-feeds/` (build file, `commonMain`, `jvmMain`, `jvmTest` with its resources and its own
  `logback-test.xml`, the ABI dump `api/webtools-feeds.api`) and the check module `webtools-feeds-commoncheck/`, which
  compiles the feeds `commonMain` for `js` against `webtools-http-commoncheck`, declares
  `kotlinx-coroutines-core` 1.10.2 itself (the http check module declares it as `implementation`), opts in to the
  stack's marker on `commonMain` and `jsMain`, and holds a `js` actual for every `expect` of the feeds module.
- **Changed:** `settings.gradle.kts` (the two modules join the `include` line); `README.md` (the artifacts table, the
  install snippet and the Maven `-jvm` note, a feeds how-to after the HTTP client stack section, the module list and
  the check-module line of "Build & test", the `kotlin-multiplatform-library` row of the shared build logic table);
  `CHANGELOG.md` (an `Added` entry under `Unreleased`); `.gitattributes` (byte-exact feed documents under the feeds
  test resources, such as UTF-16 and ISO-8859-1 fixtures, need a `-text` line of their own: the existing line covers
  `*.http` files only).
- **Unchanged:** `webtools-http` and its check module. Everything the feeds module needs from them is public
  (`UrlFetcher` and its factories, `FetchSpec`, `HttpRequest`, `HttpHeaders`, `Url`, `UpstreamError`,
  `DecodeFailure`, `Closed`, the opt-in marker, the published fixtures); the `internal` helpers the module needs
  (the logging seam and its slf4j actual, the non-fatal catch) are copied, as `internal` stops at the module.
- **Nothing to adopt:** no code in the repository parses or fetches feeds.
