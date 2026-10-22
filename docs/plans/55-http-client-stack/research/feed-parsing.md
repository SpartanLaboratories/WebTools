# Feed parsing and fetching for the feeds module

## Question

How should a new feeds module, built on `webtools-http`, parse RSS and Atom behind a port with a supplied default, and fetch feeds with a conditional GET (`ETag`, `Last-Modified`) through the `Untrusted` outbound-URL policy, when every feed URL is chosen by a Discord server administrator and every document is hostile input? An answer has to settle the parser and its default, the hardening, the feed model, who owns the conditional-GET state, the fetch path, the module's public surface and its dependency on `webtools-http`, and whether the module is Kotlin Multiplatform or JVM-only. It has to hold under: Java 11 bytecode, Kotlin 2.2.0, kotlinx-coroutines 1.10.2, `okhttp-jvm` 5.5.0, every library an `implementation` dependency with none in a public signature, nothing from skrapeit/jsoup/unirest/Ktor/Jackson on a consumer's classpath, `kotlin.Result` with no nullable results and cancellation thrown, the stability tiers, `explicitApi()` and Level-2 KDoc.

Evidence labels: **consensus** (a standard, official documentation, or several independent credible sources), **common practice** (what mature libraries visibly do, read at a pinned tag), **single source** (one author's opinion or one project), **code reading** (I read the source at the named tag; nothing was run), **reasoned** (an inference I did not measure). Nothing in this report was built or executed; where behaviour needs a run, it is a Gap and a named test.

## Bottom line

- **No binding requirement fails.** Every requirement can be met with the shape below.
- **Default parser: Rome 2.1.0 (`com.rometools:rome`) in an `implementation` dependency, behind a library-owned `FeedParser` port; documented swap: rssreader 3.12.0; the in-house StAX parser is the next step only if both prove unacceptable.** Rome is the only candidate that is hardened by default (DOCTYPE refused, external entities and external DTD off, JDOM `setExpandEntities(false)`), has typed failures that separate "not XML" from "not a feed", covers RSS 0.90 to 2.0 and Atom 0.3 and 1.0, repairs the unescaped ampersands and HTML entities that real feeds carry, and detects the encoding with the BOM, then `Content-Type` charset, then XML declaration order that RFC 7303 prescribes. Its cost is staleness (last release 2023-03-12, last push 2024-08-08, 19 open pull requests, an open date-parsing bug) and a DOM build (a memory amplification I did not measure). rssreader swallows malformed-XML errors into a log line and an empty or short stream, ignores Atom `rel`, and has no Atom 0.3. `com.prof18.rssparser` is rejected: 6.1.8 requires `kotlin-stdlib` 2.4.10 and coroutines 1.11.0, takes a `String`, and its JVM parser is an unhardened `SAXParserFactory`.
- **Hardening is three layers and does not rely on one library flag:** (1) the transport's decoded-byte cap bounds the document; (2) the default parser refuses any DOCTYPE itself, by a prolog scan before Rome runs, and Rome's own `disallow-doctype-decl` is the second lock (Rome silently skips a feature its SAX provider does not recognise, so it cannot be the only lock); (3) per-field and per-count limits are constructor parameters of `FeedLimits`. There is no parameter that lowers the protection. A named Level 4c suite (XXE, billion laughs, deep nesting, 10k attributes, a 2 MiB element flood) is the acceptance gate for any parser behind the port.
- **Conditional-GET state belongs to the caller; the fetch is stateless.** `FeedFetcher.fetch(url, validators)` returns `Result<FeedFetch>` where `FeedFetch` is `Fetched(feed, validators, finalUrl)` or `NotModified(validators, finalUrl)`. A `304` is a success value. The caller stores the returned validators on every response, including a `304`. "New items" is `entries.filter { it.id !in seen }`, left to the caller with a documented identity rule (`id`, else `link`, else a digest). Reasons: the first consumer already persists a per-subscription cursor and validators in its own database; a module-held map keyed by administrator-supplied URLs is an unbounded-memory surface; Python feedparser and Rome's archived fetcher show the two designs and only the stateless one survives.
- **Fetching uses `HttpTransport.execute` directly, not the call facade.** A feed needs no cache (a conditional GET is its cache) and no budget keyed to one provider's windows. The public constructor takes an `HttpTransport`, which exposes a `webtools-http` type, so the dependency is `api(project(":webtools-http"))`. Because the fetcher cannot verify that a supplied transport enforces `Untrusted`, a factory `FeedFetcher.untrusted(userAgent, ...)` that builds and owns the `Untrusted` transport is the documented default path.
- **Module shape: JVM-only (`kotlin("jvm")`, one coordinate `webtools-feeds`).** The default parser is JVM-only under every candidate, no non-JVM consumer exists, and the multiplatform shape adds a second coordinate, `jvmTest`-only fake-based tests and, if the target-set decision for `webtools-http` is a check module, a second check module. Keep every public type free of JVM types (epoch-millisecond `Long` dates, `String` text) so the shape can flip later without an API change. The conversion costs Gradle consumers nothing and changes a Maven consumer's coordinate to `-jvm` (`kmp-build-topology.md`). This is Consideration 1 and needs a call.

## Findings

### F1. Rome 2.1.0 is the broadest and the only fully hardened-by-default candidate, and it is dormant (code reading + Maven + GitHub)

- **Release and activity.** Maven Central lists `<latest>2.1.0</latest>` with `<lastUpdated>20230312143826</lastUpdated>`; GitHub shows release `2.1.0` on 2023-03-12, the repository's last push on 2024-08-08, 29 open items (issues and pull requests together; 19 of them are pull requests), Apache-2.0, not archived. Open issue #695 "Can't parse date with time zone offset without a colon (e.g. -0400)" was filed 2025-08-04 and has no comments. Evidence tier: consensus on the dates (registry and API). Sources: <https://repo1.maven.org/maven2/com/rometools/rome/maven-metadata.xml>, <https://github.com/rometools/rome>, <https://github.com/rometools/rome/issues/695>. Read 2026-10-21.
- **Dependencies.** `rome-utils` 2.1.0, `jdom2` 2.0.6.1, `slf4j-api` (parent POM pins 2.0.6; this repository's modules resolve higher). Class-file major 52 for Rome and rome-utils, 49 for jdom2: no Java-11-floor problem (`47-java-23-bytecode/research/java-11-floor.md`). Source: <https://repo1.maven.org/maven2/com/rometools/rome-parent/2.1.0/rome-parent-2.1.0.pom>.
- **Format coverage.** The module description lists "all flavors of RSS (0.90, 0.91, 0.92, 0.93, 0.94, 1.0 and 2.0), Atom 0.3 and Atom 1.0". Parser classes at the tag: `RSS090Parser` through `RSS20Parser`, `Atom03Parser`, `Atom10Parser` under `com.rometools.rome.io.impl`. Source: `rome/pom.xml` and the `io/impl` directory at tag 2.1.0 (commit `7e5cf653d8a9e64f484efec35f457615a686f2ff`).
- **Hardening in code.** `WireFeedInput.createSAXBuilder` sets, through a helper that applies a feature only if the parser accepts it: `external-general-entities` false, `external-parameter-entities` false, `load-external-dtd` false, and, unless `allowDoctypes` is set (default `false`), `http://apache.org/xml/features/disallow-doctype-decl` true; then `saxBuilder.setExpandEntities(false)`. The Javadoc: "Since ROME 1.5.1 we fixed a security vulnerability by disallowing Doctype declarations by default." The helper `isFeatureSupported` swallows `SAXNotRecognizedException` and `SAXNotSupportedException` and returns `false`, so a SAX provider that does not know the Xerces feature names gets no DOCTYPE ban and no error (the source comment itself names "Crimson" as such a parser). On the JDK's built-in parser the features are recognised. Tier: code reading.
- **Advisories.** OSV returns no record for `com.rometools:rome`. `jdom2` has GHSA-2363-cqg2-863c / CVE-2021-33813 ("An XXE issue in SAXBuilder in JDOM through 2.0.6 allows attackers to cause a denial of service via a crafted HTTP request"), fixed in 2.0.6.1; Rome 2.1.0 pins 2.0.6.1 and OSV returns no vulnerability for `jdom2:2.0.6.1`. Absence of an OSV record is not proof of absence of bugs. Source: <https://osv.dev/vulnerability/GHSA-2363-cqg2-863c>. Tier: consensus for the CVE, code reading for the pin.
- **Tolerance machinery.** `XmlFixerReader` (on by default, `xmlHealerOn = true`) turns a bare `&` into `&amp;` and maps named HTML entities (`CODED_ENTITIES`, for example `&nbsp;` to `&#160;`); `XmlReader(InputStream, httpContentType, lenient)` documents "First it checks if the stream has BOM. If there is not BOM checks the content-type encoding. If there is not content-type encoding checks the XML prolog encoding", which is the RFC 7303 priority (F7). Tier: code reading.
- **Failure typing.** `SyndFeedInput.build(Reader)` throws `ParsingFeedException` ("Invalid XML") for malformed XML and `IllegalArgumentException("Invalid document")` when well-formed XML matches no feed parser. These map to `Malformed` and `NotAFeed`. Tier: code reading (`WireFeedInput.build`).
- **Dates.** `DateParser` tries `SimpleDateFormat` masks with `setLenient(true)` under a `Locale` (default `Locale.US`) and returns `null` when no mask matches; the raw string is not kept on the bean. The open bug above is a miss of this kind. The consequence for a "post new items" bot is small if identity is by id (F4), and it must be tested. Tier: code reading.
- **Atom link choice.** `ConverterForAtom10` uses "the first alternate link as THE link"; non-alternate links are kept in `getLinks()`. Tier: code reading.
- **Kotlin fit.** `SyndFeed` and `SyndEntry` are mutable beans with platform types (`getPublishedDate(): Date`, `getUri(): String`, `getDescription(): SyndContent`, `getContents(): List<SyndContent>`, `getEnclosures()`, `getCategories()`, `getAuthor(): String`). They stay inside the adapter; the port returns immutable Kotlin types.

### F2. rssreader 3.12.0 is maintained and dependency-free, but its failure model and Atom handling do not suit the use (code reading + registry)

- **Release and activity.** `com.apptasticsoftware:rssreader` 3.12.0, released 2025-12-01 (`<lastUpdated>20251201063113</lastUpdated>`); the repository was pushed on 2026-10-01; MIT; 200 stars; 7 open items (issues and pull requests together); one maintainer. README: "RSS Reader is a simple Java library for reading RSS and Atom feeds. It has zero 3rd party dependencies, a low memory footprint and can process large feeds. Requires at minimum Java 11." Class-file major 55. Sources: <https://repo1.maven.org/maven2/com/apptasticsoftware/rssreader/maven-metadata.xml>, <https://github.com/w3stling/rssreader/releases>, tag `v3.12.0` (commit `ba3ea5301ce98d5b6f819e10735fe8de2734d0d2`).
- **Input.** `AbstractRssReader.read(InputStream)` parses from a stream without fetching; `read(String url)` fetches with `java.net.http.HttpClient` and `followRedirects(ALWAYS)`, which would bypass the outbound-URL policy, so only the stream entry point is usable here.
- **Hardening.** `XMLInputFactorySecurity.hardenFactory` sets `XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES` false and `SUPPORT_DTD` false; the iterator also sets `IS_COALESCING` true and `IS_NAMESPACE_AWARE` false. It uses `XMLInputFactory.newInstance()`, which a Woodstox or Aalto jar on the classpath would replace (the two properties are standard, so they still apply). It does not reject a DOCTYPE; OWASP states "Disabling DTD processing does not necessarily reject the DOCTYPE declaration." How the JDK reader treats a DOCTYPE with `SUPPORT_DTD` false is unverified here (Gap G3).
- **Errors are swallowed.** In `RssItemIterator`, an `XMLStreamException` is caught, logged at `WARNING` through `java.util.logging` ("Failed to parse XML from feed %s"), the iterator is closed and `NoSuchElementException` ends the stream. A malformed prefix, an HTML page, and an empty feed are all an empty stream; a feed that breaks halfway returns the earlier items with no signal. A bot that pauses a subscription after repeated failures cannot get that signal from the library. Tier: code reading.
- **Atom.** `registerItemAttributes` maps `link@href` to `Item::setLink` with no `rel` test (no `alternate` or `self` handling appears in the sources), so the last `<link href>` in an entry wins, including `rel="replies"` or `rel="enclosure"`. The tag tables contain no `modified` or `issued`, so Atom 0.3 dates are not read. RSS 1.0 (`/rdf:RDF/...`) is handled. Tier: code reading.
- **Other behaviour.** A static `ScheduledThreadPoolExecutor` daemon thread and a `Cleaner` run a parse watchdog (`readTimeout`, default 25 s) that closes the reader; the returned `Stream` is lazy and must be consumed or closed; dates keep the raw string and add a `DateTimeParser` with many `java.time` patterns. Invalid-XML-character repair exists as an opt-in `FeedFilter` (`InvalidXmlCharacterFilter`), not by default.
- **Advisories.** OSV returns no record.

### F3. Kotlin multiplatform parsers do not fit the pins or the threat model (registry + code reading)

- `com.prof18.rssparser:rssparser` 6.1.8 (2026-07-30; Apache-2.0): its JVM Gradle Module Metadata requires `kotlin-stdlib` `2.4.10`, `okhttp` `5.4.0`, and at runtime `kotlinx-coroutines-core` `1.11.0` and `okhttp-coroutines` `5.4.0`. The pinned work is Kotlin 2.2.0 and coroutines 1.10.2. Older releases that I checked do not fit either: 6.1.4 needs stdlib 2.3.10 (with `okhttp-jvm` 5.3.2 and coroutines 1.10.2); 6.1.0 needs stdlib 2.2.20 and `okhttp` 4.12.0 (a second OkHttp major beside `okhttp-jvm` 5.5.0); 6.0.12 needs stdlib 2.1.21 and `okhttp` 4.12.0. I did not check the releases in between; the hardening finding below decides regardless of version.
- Its JVM parser (`JvmXmlParser` at tag 6.1.8, commit `9adb962be12a2fe9bfe4a93f08b19ad5640cf172`) is `SAXParserFactory.newInstance().newSAXParser()` with no `setFeature`, no DOCTYPE refusal, and no secure-processing setting. `RssParser.parse(rawRssFeed: String)` takes a decoded `String` (so charset handling is the caller's) and throws `RssParsingException`; `getRssChannel(url)` fetches itself, which bypasses the policy; the `RssParser` constructor is `internal`, so it is not extensible. Rejected on the pins and on hardening, independent of which version.
- No other Kotlin multiplatform feed parser turned up in a search. Every non-JVM Kotlin target would need a platform XML parser, so a "common" parser is a per-platform wrapper in any case. Tier: common practice (one project).

### F4. Conditional GET: the caller owns the state in the mature parsing libraries; the one stateful fetcher is archived (common practice + consensus on HTTP)

- **HTTP rules.** RFC 9110 section 13.1.2: "A recipient MUST use the weak comparison function when comparing entity tags for If-None-Match", and the client "SHOULD generate an If-None-Match header field containing a list of those entity tags". Section 13.1.3: "A recipient MUST ignore If-Modified-Since if the request contains an If-None-Match header field", and "A recipient MUST ignore the If-Modified-Since header field if the received field value is not a valid HTTP-date". So send both validators when both are known, send the `Last-Modified` string back verbatim (never reformat it), and pass a weak `W/"..."` ETag through unchanged. Source: <https://www.rfc-editor.org/rfc/rfc9110.txt>. Tier: consensus.
- **Python feedparser 6.0.14** is stateless: `feedparser.parse(url, etag=d.etag)` and `parse(url, modified=d.modified)`; the result exposes `etag`, `modified` and `status` (304), with empty `feed` and `entries`; "Clients should support both ETag and Last-Modified headers, as some servers support one but not the other." The application stores the values. Source: <https://feedparser.readthedocs.io/en/stable/http-etag.html>. Tier: common practice.
- **Rome's fetcher** (`com.rometools:rome-fetcher` 1.19.0, 2023-02-15; the repository `rometools/rome-fetcher` is archived, last pushed 2016-02-20) is stateful: `FeedFetcher.retrieveFeed(URL)` with a `FeedFetcherCache`/`SyndFeedInfo` seam. Rome's README now says: "The `URL` variant used in this example is deprecated and works only for simplest cases. Please consider using a separate library for fetching the feed". Tier: common practice.
- **Go gofeed** (`Parser.Parse(io.Reader)`, `ParseURLWithContext`, an injectable `http.Client`, no validator state) and **Rust feed-rs** (`parser::parse<R: Read>(source)`) are pure parsers; fetching and validators are the caller's.
- **The first consumer already persists the state.** BotTools stores a per-subscription cursor ("seen ids, validators") in its `SubscriptionStore`, polls through a raw `Untrusted` call that returns the status and headers, and passes the bytes to its `FeedParser` (BotTools architecture sections 7.3, 7.6 and 8.4, read 2026-10-21). A stateful fetcher would add a second source of truth that is lost on restart.
- **Pitfall found in the wild.** NetNewsWire kept stale validators because its unchanged-body early exit skipped the validator update: "conditionalGetInfo gets updated no matter what." A `304` may carry a new `ETag`; the returned validators must replace the stored ones on every response. Source: <https://inessential.com/2024/08/03/netnewswire_and_conditional_get_issues.html>. Tier: single source, matches RFC 9110.

### F5. Hostile-XML controls: the OWASP guidance and the JDK's own limits (consensus + documentation)

- **OWASP XXE Prevention Cheat Sheet:** "Disable document type definitions (DTDs) whenever possible. Reject DOCTYPE declarations if the parser supports it." For SAX and DOM it sets `http://apache.org/xml/features/disallow-doctype-decl` true; for `XMLInputFactory` it sets `SUPPORT_DTD` and `IS_SUPPORTING_EXTERNAL_ENTITIES` false, adds an `XMLResolver` that throws, and states "DTD processing defaults to enabled; the external-entity default is unspecified", "Disabling DTD processing does not necessarily reject the DOCTYPE declaration. StAX does not support FEATURE_SECURE_PROCESSING; use the provider's processing limits when DTDs are required", and "Keep XInclude disabled unless explicitly required". Its examples use `XMLInputFactory.newDefaultFactory()`, which is "Since: 9" in the Java 11 Javadoc and ignores a Woodstox or Aalto jar on the classpath. Source: <https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html>. Tier: consensus.
- **JDK processing limits** (Java 21 JAXP Security Guide): "The StAX parser also observes processing limits by default even though it doesn't support FSP." Defaults: `jdk.xml.elementAttributeLimit` 10000, `jdk.xml.entityExpansionLimit` 64000, `jdk.xml.entityReplacementLimit` 3000000, `jdk.xml.totalEntitySizeLimit` 5x10^7, `jdk.xml.maxXMLNameLimit` 1000, `jdk.xml.maxElementDepth` 0 and `jdk.xml.maxGeneralEntitySizeLimit` 0 (zero means no limit). Both DOM and SAX parsers have FSP on by default. Source: <https://docs.oracle.com/en/java/javase/21/security/java-api-xml-processing-jaxp-security-guide.html>. Tier: consensus for JDK 21. The Java 11 table was not retrievable (Gap G4).
- **What bounds what.** The entity attacks (billion laughs, quadratic blowup through entities, external entities, external DTD) all need a DOCTYPE; refusing every DOCTYPE removes them whatever the parser's limits are. What remains: depth (unlimited by default), element count, attribute count (10000 on the JDK), name length (1000), and text length, each bounded in total by the transport's decoded-byte cap (2 MiB for `Untrusted`, `http-transport.md:291`, which also refuses a gzip bomb at the cap, `http-transport.md:94`). A DOM tree can be many times the document size (Gap G1), a streaming reader far less.
- **Legitimate DOCTYPEs are rare in feeds:** RSS 0.91 Netscape and XHTML pages. Refusing them is correct for a public bot; an XHTML page also arrives as the common "administrator pasted a site URL" mistake and is better reported as `NotAFeed`.

### F6. Feed polling is a scheduling concern of the caller, and the HTTP layer already supplies what a single fetch needs (reasoned + repository facts)

- The transport never reads the body of a `304` and returns it as a response (`http-transport.md:100`, `:249`), enforces the cap on decoded bytes, follows `GET` redirects under the policy per hop, strips credential headers cross-origin, and `Untrusted` refuses a request carrying `Authorization`, `Cookie` or `Proxy-Authorization` (`outbound-url-policy.md:317`). The fetcher sends none of these.
- The response cache keys by endpoint and TTL and the call budget models a provider's windows; neither fits a population of administrator-chosen hosts (an unbounded key space), and a conditional GET already is the cache. The raw transport is the right layer; BotTools' architecture says the feeds plugin gets "one `Untrusted` client with no cache" (section 7.4).
- `HttpResponse.redirectChain` is a list of URLs; it does not say whether a hop was permanent. The fetcher can report `finalUrl`, not "moved permanently".

### F7. Charset: bytes in, the parser decides (consensus)

RFC 7303 section 3.2 sets the priority for XML media types: "A BOM is authoritative if it is present in an XML MIME entity" and "In the absence of a BOM, the charset parameter is authoritative if it is present", with the XML declaration third. The transport returns bytes with no charset (`http-transport.md:252`), so the parser input is `bytes + Content-Type value`. Rome's `XmlReader(InputStream, httpContentType, lenient)` implements that order; `ByteArray.decodeToString()` would force UTF-8 and break ISO-8859-1 feeds. Source: <https://www.rfc-editor.org/rfc/rfc7303.txt>.

### F8. Malformed feeds are common enough to design for, with old evidence (single source)

Brent Simmons, an aggregator author: "The single most common cause of non-well-formedness that I see is unencoded ampersands." Mark Pilgrim's book estimated about 10% of feeds are not valid XML and a 2003 xml-dev estimate put well-formed feeds at "a little over 80%"; both are informal, old and unmeasured. Python feedparser's documentation: it "sets the bozo bit when it detects that a feed is not well-formed" and "can parse feeds whether they are well-formed XML or not." Both Rome (`XmlFixerReader`) and prof18 (`escapeInvalidXmlEntities`) carry ampersand repair, which is independent evidence that the repair is needed. Sources: <https://inessential.com/2003/08/19/how_to_deal_with_broken_feeds.html>, <https://feedparser.readthedocs.io/en/stable/bozo.html>.

## How comparable projects do it

| Project (version) | Parse/fetch split | Conditional GET | Hardening | Failure signal |
|---|---|---|---|---|
| Rome 2.1.0 (`SyndFeedInput`, `WireFeedInput`, `XmlReader`) | Parse only; README tells callers to fetch separately | None in 2.x; archived `rome-fetcher` was stateful (`FeedFetcherCache`) | DOCTYPE refused by default, SAX features, JDOM no entity expansion | `ParsingFeedException`, `IllegalArgumentException("Invalid document")` |
| rssreader 3.12.0 (`AbstractRssReader.read(InputStream)`) | Parse from a stream; `read(url)` fetches with the JDK client | None | StAX `SUPPORT_DTD` false and external entities false | Logged, stream ends |
| prof18 rssparser 6.1.8 (`RssParser.parse(String)`, `getRssChannel(url)`) | Both | None | None found in `JvmXmlParser` | `RssParsingException` |
| Python feedparser 6.0.14 (`parse(url_or_data, etag=, modified=)`) | Both, caller passes validators and stores the result's | Stateless | Not XML-strict by design | `bozo` bit plus `bozo_exception` |
| Go gofeed v1.5.0 (`Parser.Parse(io.Reader)`, `ParseURLWithContext`) | Both; injectable `http.Client` | None | n/a | `error` |
| Rust feed-rs v3.0.0 (`parser::parse<R: Read>`) | Parse only | n/a | n/a | `ParseFeedError` |

The consistent pattern: parsing is a pure function of bytes; the fetch layer, where one exists, is thin and leaves the validators to the caller; hostile-input defence is the part libraries differ on most.

## Pitfalls and regrets

1. **Trusting one parser flag.** Rome's DOCTYPE ban is an optional SAX feature that is skipped silently if the provider does not recognise it. A prolog scan in the module's own code is the lock that does not depend on the provider (F1).
2. **Treating "DTD processing off" as "DOCTYPE refused".** OWASP says they differ for StAX; entity behaviour under `SUPPORT_DTD=false` on the JDK reader is unmeasured here (G3). Any StAX-based parser (rssreader, an in-house one) is trusted only after the Level 4c suite passes.
3. **Failure as empty success.** rssreader returns an empty or short stream on malformed XML. A bot that pauses failing subscriptions cannot tell them from an empty feed (F2).
4. **Truncating to "the first N entries" by document order.** Feeds are not guaranteed newest-first, so a count cap that drops entries silently can hide exactly the new ones. The default cap fails loudly instead (`LimitExceeded`).
5. **Not updating validators on every response**, including a `304` (NetNewsWire, F4), or storing them unbounded or with control characters. Cap the stored length and drop values that are not printable ASCII.
6. **A stateful in-process validator map** keyed by administrator-supplied URLs grows without bound and is lost on restart (F4).
7. **Fetching with the parser's own HTTP client** (`rssreader.read(url)`, `getRssChannel(url)`, `XmlReader(URL)`) bypasses the `Untrusted` policy, the body cap and the redirect rules. Never call these.
8. **Parsing on the caller's thread.** The parse is blocking CPU work that cannot be cancelled once started. Run it on a dispatcher passed as a constructor parameter (so tests can replace it, `test-support-and-time.md` hazard 2) and bound the number of concurrent parses; a DOM build of a worst-case 2 MiB document times eight concurrent fetches is the amplification to size (G1).
9. **`runCatching` around suspend code** swallows `CancellationException` (`http-transport.md:82`). The fetch maps `execute`'s `Result` and parses inside `withContext`; the parse itself catches only the parser's exceptions and `StackOverflowError` (deep nesting), never `Throwable`.
10. **Hostile content fields.** Titles, summaries and links are attacker text: links may be `javascript:`, `file:`, `data:` or relative; ids may be megabytes long. The port's contract caps lengths, keeps only absolute `http`/`https` links (resolving relative ones against the document base when one is known), and the KDoc says summaries and content may contain markup and must be stripped before display.
11. **`Date` parsing hidden failures.** A null date is a normal value; never use dates for "is new" (a hostile feed can post far-future dates); use ids.
12. **Auto-discovery creep.** Pulling in an HTML parser to find `<link rel="alternate">` brings jsoup-class dependencies the requirements forbid. It is a non-goal for 1.0.

## Recommendation for this repo

### The parser

Ship `DefaultFeedParser` on Rome 2.1.0 behind the `FeedParser` port. Adapter shape (all inside `implementation` code, no Rome type visible):

1. Scan the decoded prolog for `<!DOCTYPE` (skipping the XML declaration, comments and processing instructions) and return `Refused(Doctype)` before Rome runs. Detect the encoding with `XmlReader(ByteArrayInputStream(bytes), contentType, true)` and wrap with Rome's healer left on.
2. Build `SyndFeedInput()` with `allowDoctypes = false` (the default; do not expose a setter). Map `ParsingFeedException` to `Malformed`, `IllegalArgumentException("Invalid document")` to `NotAFeed`, `StackOverflowError` to `LimitExceeded(depth)`.
3. Convert beans to the immutable model, applying `FeedLimits` and the link and id rules (below).

**Documented swap:** rssreader 3.12.0, implemented by a consumer against `FeedParser` (Experimental to implement), with three behaviours the adapter must handle and the contract suite pins: malformed XML is silent (treat zero items and no channel as `NotAFeed`; a mid-document break cannot be detected), Atom `rel` is ignored (choose the link from the `Item` fields it exposes, accepting the limit), and Atom 0.3 is unsupported. If those are unacceptable the next step is an in-house StAX parser, built on `XMLInputFactory.newDefaultFactory()` with `SUPPORT_DTD` and `IS_SUPPORTING_EXTERNAL_ENTITIES` false, a throwing `XMLResolver`, an explicit DOCTYPE refusal, and streaming caps; it is not recommended now because the tolerance long tail (F8) is real work and Rome already carries it.

Publish a `FeedParserContract` abstract test class in the module's test-fixtures variant: the hostile-document suite below plus valid and malformed feeds, so any `FeedParser` can be verified against it.

### Public surface (signatures, not bodies; package `com.spartanlabs.webtools.feeds`; names provisional where they depend on the failure-model sibling)

```kotlin
// ---- the seam: Experimental to implement; the supplied default is usable without the opt-in ------------
@SubclassOptInRequired(/* the stack's single marker, `SeamOptIn` in the test-support findings */)
public interface FeedParser {
    public fun parse(input: FeedInput): Result<Feed>          // pure function of bytes; not suspend (Consideration 2)
}
public class FeedInput(
    document: ByteArray,                                      // copied
    public val contentType: String? = null,                   // the Content-Type header value, for charset detection
    public val baseUrl: String? = null,                       // absolute URL against which relative links resolve
) { public val document: ByteArray }

public class DefaultFeedParser(public val limits: FeedLimits = FeedLimits()) : FeedParser   // Rome-backed; no opt-in to use

public class FeedLimits(                                      // policy numbers; defaults are judgement
    public val maxEntries: Int = 1_000,                       // exceeding fails with LimitExceeded; never a silent drop
    public val maxTextChars: Int = 65_536,                    // summary, content, title: truncated
    public val maxIdChars: Int = 1_024,                       // longer ids fall back to the link, then a digest
    public val maxLinkChars: Int = 2_048,                     // longer links become null
    public val maxCategoriesPerEntry: Int = 32,
    public val maxEnclosuresPerEntry: Int = 8,
)

// ---- the model: Stable Core; immutable; no JVM type; plain classes, not data classes (binary evolution) ----
public class Feed(
    public val title: String?, public val link: String?, public val description: String?,
    public val entries: List<FeedEntry>,                      // defensive copy
)
public class FeedEntry(
    public val id: String,                                    // guid or Atom id; else link; else a digest of title and date. Never blank.
    public val title: String?, public val link: String?,      // link: absolute http/https only, else null
    public val summary: String?, public val content: String?, // may contain markup; untrusted
    public val author: String?,
    public val publishedAtEpochMillis: Long?,                 // null when absent or unparseable; never used for "is new"
    public val updatedAtEpochMillis: Long?,
    public val categories: List<String> = emptyList(),
    public val enclosures: List<FeedEnclosure> = emptyList(),
)
public class FeedEnclosure(public val url: String, public val mimeType: String?, public val lengthBytes: Long?)

// ---- conditional GET: caller-owned state --------------------------------------------------------------
public class FeedValidators(public val etag: String?, public val lastModified: String?) {   // verbatim header values; oversize or non-printable values become null
    public companion object { public val None: FeedValidators }
}
public sealed class FeedFetch {                               // closed: modified or not modified is the contract
    public abstract val validators: FeedValidators            // store these after every response, a 304 included
    public abstract val finalUrl: Url                         // after redirects
    public class Fetched(public val feed: Feed, override val validators: FeedValidators, override val finalUrl: Url) : FeedFetch()
    public class NotModified(override val validators: FeedValidators, override val finalUrl: Url) : FeedFetch()
}

public class FeedFetcher(                                     // Stable Core; final; seams are HttpTransport and FeedParser
    transport: HttpTransport,                                 // must enforce OutboundUrlPolicy.Untrusted; the fetcher cannot verify this
    parser: FeedParser = DefaultFeedParser(),
    parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    maxConcurrentParses: Int = 2,
) : AutoCloseable {
    public suspend fun fetch(url: Url, validators: FeedValidators = FeedValidators.None): Result<FeedFetch>
    override fun close()                                      // closes only a transport the fetcher created
    public companion object {
        public fun untrusted(userAgent: String, settings: TransportSettings = TransportSettings.forUntrusted(),
                             parser: FeedParser = DefaultFeedParser()): FeedFetcher   // builds and owns an Untrusted OkHttpTransport
    }
}

// ---- failures: typed Throwables in Result; align names and root with the failure-model findings --------
public sealed class FeedFailure : Throwable() {
    public class NotAFeed(public val reason: NotAFeedReason) : FeedFailure()      // Empty, Html, NotXml, UnknownRoot
    public class Malformed(cause: Throwable?) : FeedFailure()
    public class Refused(public val reason: RefusedReason) : FeedFailure()         // Doctype; message is constant text, no document content
    public class LimitExceeded(public val limit: String) : FeedFailure()
    public class UnexpectedStatus(public val status: Int, public val retryAfterMillis: Long?) : FeedFailure()   // 404, 410, 429, 5xx, a 304 to an unconditional request
}
```

Transport and policy failures (`RefusedByPolicy`, `BodyTooLarge`, `Timeout`, `Closed`, ...) pass through `fetch` unchanged; the fetcher does not wrap them, so one `Result` carries `TransportFailure` or `FeedFailure`. Cancellation is thrown, never a `Result.failure`.

### The fetch, in order

1. Build a `GET` with `Accept: application/atom+xml, application/rss+xml;q=0.9, application/xml;q=0.8, text/xml;q=0.8, */*;q=0.5` and, when present, `If-None-Match` and `If-Modified-Since` set verbatim from the validators. `User-Agent` and `Accept-Encoding` belong to the transport.
2. `transport.execute(request)`; a failure is returned as is.
3. `304` with a conditional request: `NotModified`, with the response's validators when present and the previous ones otherwise. `304` without one: `UnexpectedStatus(304)`.
4. `2xx` with a body: parse on `parseDispatcher` under a semaphore of `maxConcurrentParses`, then `Fetched` with the response's validators (a response with neither validator returns `FeedValidators.None`, so the caller does not keep stale ones). An empty body is `NotAFeed(Empty)`; a `text/html` body or an HTML root that fails to parse is `NotAFeed(Html)`; content type is otherwise not trusted.
5. Anything else: `UnexpectedStatus(status, retryAfterMillis)`. The caller owns scheduling, back-off, unsubscribing on `410`, and first-poll behaviour (do not post the existing backlog).

"New items" is the caller's: `feed.entries.filter { it.id !in seen }`, with `seen` bounded to the ids of the last fetched feed. No tracker class is published.

### Dependency and build, under each shape

| | **JVM-only (recommended)** | **Kotlin Multiplatform** |
|---|---|---|
| Plugin and coordinates | `kotlin("jvm")` convention plugin; `webtools-feeds` | KMP plugin, `jvm()`; `webtools-feeds` (no classes) and `webtools-feeds-jvm` |
| Port, model, fetcher, failures | `src/main` | `commonMain` (all free of JVM types) |
| `DefaultFeedParser` (Rome), `FeedFetcher.untrusted` | `src/main` | `jvmMain` |
| `webtools-http` | `api(project(":webtools-http"))`; the POM maps it to `webtools-http-jvm` (`kmp-build-topology.md:151`) | `commonMain.dependencies { api(project(":webtools-http")) }` |
| Rome | `implementation("com.rometools:rome:2.1.0")` | `jvmMain` `implementation` |
| Fakes | `testImplementation(testFixtures(project(":webtools-http")))` | `"jvmTestImplementation"(testFixtures(project(":webtools-http")))`; fake-based tests live in `jvmTest` (`kmp-build-topology.md:175-176`) |
| Tests | `src/test/kotlin/com/spartanlabs/testing/<level>/webtools/feeds` | `jvmTest`, same packages |
| What the shape buys | one coordinate, the existing convention, nothing to maintain for a target nobody uses | a port a future non-JVM parser could implement; but every non-JVM target still needs its own XML parser, and `jvm()` alone does not make `commonMain` common (`kmp-build-topology.md:33-37`, Consideration 1) |

`implementation` for Rome in both. `explicitApi()` and Level-2 KDoc apply in both. `slf4j-api` is `implementation` (`kmp-build-topology.md:164`). The module needs no copy of `webtools-http`'s `internal` helpers unless it chains `Result`s (`codebase-blast-radius.md:21`); the fetch maps one `Result` and can avoid a helper.

### Hardening acceptance suite (Level 2 and Level 4c, run against `FeedParserContract`)

DOCTYPE with an external general entity pointing at a loopback `mockwebserver3` (assert zero requests arrive), a parameter-entity external DTD (zero requests), billion laughs and quadratic blowup (refused without expansion, within a time bound), 10,001 attributes on one element, 100,000-deep nesting, a 2 MiB flood of empty elements run at the concurrency the fetcher allows under a fixed heap, a single 2 MiB text node, an XML bomb inside a gzip body (already refused by the transport, `http-transport.md:94`), `xi:include`, an HTML page, a JSON body, an empty body, invalid XML characters, a UTF-16 BOM document, an ISO-8859-1 document with a conflicting declaration, bare ampersands and `&nbsp;`, 50 KB `ETag`, a `javascript:` link, a 5 MB id. Level 3: the conditional-GET `Dispatcher` against `mockwebserver3` (ten lines, not shipped, `test-support-and-time.md:373`) through the loopback `Untrusted` factory (`outbound-url-policy.md:358`); a `304` carrying `Content-Length: 5000000` returns promptly.

## Considerations

### 1. Is the feeds module Kotlin Multiplatform or JVM-only

- **JVM-only.** One coordinate; the existing convention plugin; Rome used directly; no `jvmTest` split; no extra check module whichever way Consideration 1 of `kmp-build-topology.md` is settled. The cost: the parser port is reachable only from the JVM, and a later conversion changes a Maven consumer's coordinate to `webtools-feeds-jvm` while leaving Gradle consumers unaffected.
- **Kotlin Multiplatform (`jvm()` only, or with the targets chosen there).** The port, model, fetcher and failures in `commonMain`, Rome in `jvmMain`; two coordinates; fake-based tests in `jvmTest`. If `webtools-http` takes a check module to enforce common code, the feeds module needs its own or accepts an unenforced `commonMain`. The benefit is speculative: no non-JVM consumer or parser exists.
- **Recommendation: JVM-only.** Hold the public types free of JVM types so the choice stays reversible. Revisit only if a non-JVM target for feeds is planned.

### 2. Is `FeedParser.parse` a plain function or `suspend`

- **Plain function (recommended).** The parse is CPU-bound, bounded by the decoded-byte cap and never suspends; a plain function makes a parser a pure function of bytes (testable at Level 4a), and `FeedFetcher` moves it off the caller's thread with `withContext`. The settled "suspend API only" rule reads naturally as covering I/O.
- **`suspend`.** Uniform with the rest of the stack, but it implies a cancellation point that a blocking DOM parse does not have, and it forces `runTest` plumbing into pure tests.
- Needs a call because the rule is stated for the whole stack.

### 3. How the fetcher is given an `Untrusted` transport

- **Both a constructor taking `HttpTransport` and `FeedFetcher.untrusted(...)` (recommended).** Fakes and a consumer's shared client reach the constructor; the factory is the safe default and the documented path. The constructor's KDoc states the requirement; it cannot be checked.
- **Factory only.** Safest, but fakes would need an `internal` constructor that the fixtures cannot reach, and a consumer's adapter (BotTools builds its own transports) could not use its client.
- **Constructor only.** A consumer who passes a `Trusted` transport turns a feed URL into an SSRF primitive.
- Needs a call because it trades test and adapter reach against a misuse the type system cannot prevent.

## Load-bearing claims

1. **Rome 2.1.0 is the latest release, dated 2023-03-12.** "`<latest>2.1.0</latest>` ... `<lastUpdated>20230312143826</lastUpdated>`". <https://repo1.maven.org/maven2/com/rometools/rome/maven-metadata.xml>, read 2026-10-21.
2. **Rome's default refuses DOCTYPE and disables external entities and DTD loading.** "`private boolean allowDoctypes = false;`" and "Since ROME 1.5.1 we fixed a security vulnerability by disallowing Doctype declarations by default." plus `setFeature(saxBuilder, parser, "http://apache.org/xml/features/disallow-doctype-decl", true);` and `saxBuilder.setExpandEntities(false);`. `rome/src/main/java/com/rometools/rome/io/WireFeedInput.java` at tag 2.1.0, <https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/WireFeedInput.java>.
3. **Rome applies a hardening feature only if the SAX provider recognises it.** `isFeatureSupported` contains "`catch (final SAXNotRecognizedException e) {`" followed by "`return false;`", and the source comment says "Crimson is one parser which is known not to support these features." Same file and tag.
4. **Rome throws distinct exceptions for malformed XML and for a non-feed document.** "`throw new ParsingFeedException("Invalid XML: " + ex.getMessage(), ex);`" and "`throw new IllegalArgumentException("Invalid document");`". Same file and tag, in `build(Reader)` and `build(Document)`.
5. **Rome repairs bare ampersands and HTML named entities and detects encoding BOM, then content type, then XML prolog.** `XmlFixerReader`: "`CODED_ENTITIES.put("&nbsp;", "&#160;");`" and the comment "no ';' to match the '&' lets just make the '&'" (followed by "a legal xml character entity '&amp;'"); `XmlReader`: "First it checks if the stream has BOM. If there is not BOM checks the content-type encoding. If there is not content-type encoding checks the XML prolog encoding." <https://github.com/rometools/rome/tree/2.1.0/rome/src/main/java/com/rometools/rome/io>, tag 2.1.0.
6. **Rome returns null for an unparseable date.** "It returns
   <b>null</b> if it was not possible to parse the the string with any of the masks." `DateParser` at tag 2.1.0, <https://github.com/rometools/rome/blob/2.1.0/rome/src/main/java/com/rometools/rome/io/impl/DateParser.java>.
7. **Rome is dormant.** Latest release 2023-03-12 (claim 1); repository last pushed 2024-08-08 with 29 open items, 19 of them pull requests; issue #695 open since 2025-08-04. <https://github.com/rometools/rome>, <https://github.com/rometools/rome/issues/695>, read 2026-10-21 (GitHub API: `pushed_at` `2024-08-08T16:50:36Z`, `open_issues_count` 29).
8. **jdom2 2.0.6.1 contains the fix for CVE-2021-33813, and Rome 2.1.0 pins it.** "An XXE issue in SAXBuilder in JDOM through 2.0.6 allows attackers to cause a denial of service via a crafted HTTP request." (fixed `2.0.6.1`). <https://osv.dev/vulnerability/GHSA-2363-cqg2-863c>; pin in <https://repo1.maven.org/maven2/com/rometools/rome-parent/2.1.0/rome-parent-2.1.0.pom> (`jdom2` `2.0.6.1`).
9. **rssreader 3.12.0 is zero-dependency and Java 11, released 2025-12-01.** "It has zero 3rd party dependencies, a low memory footprint and can process large feeds. Requires at minimum Java 11." <https://github.com/w3stling/rssreader/blob/v3.12.0/README.md>; `<lastUpdated>20251201063113</lastUpdated>` in <https://repo1.maven.org/maven2/com/apptasticsoftware/rssreader/maven-metadata.xml>.
10. **rssreader hardens its StAX factory but does not reject DOCTYPE.** "`factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);`" and "`factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);`" (two consecutive statements in `hardenFactory`). `internal/XMLInputFactorySecurity.java` at tag v3.12.0, <https://github.com/w3stling/rssreader/blob/v3.12.0/src/main/java/com/apptasticsoftware/rssreader/internal/XMLInputFactorySecurity.java>.
11. **rssreader swallows XML parse errors.** "`LOGGER.log(Level.WARNING, String.format("Failed to parse XML from feed %s", source), e);`" followed by `close(); throw new NoSuchElementException();`. `AbstractRssReader.java` (`RssItemIterator.next`) at tag v3.12.0, <https://github.com/w3stling/rssreader/blob/v3.12.0/src/main/java/com/apptasticsoftware/rssreader/AbstractRssReader.java>.
12. **rssreader maps Atom `link@href` with no `rel` test.** "`itemAttributes.computeIfAbsent("link", k -> new HashMap<>()).putIfAbsent("href", Item::setLink);`". Same file and tag (`registerItemAttributes`).
13. **prof18 rssparser 6.1.8 requires Kotlin 2.4.10 and coroutines 1.11.0.** POM: `<artifactId>kotlin-stdlib</artifactId><version>2.4.10</version>` and `<artifactId>kotlinx-coroutines-core-jvm</artifactId><version>1.11.0</version>`; <https://repo1.maven.org/maven2/com/prof18/rssparser/rssparser-jvm/6.1.8/rssparser-jvm-6.1.8.pom>, 2026-07-30.
14. **prof18's JVM parser is an unconfigured `SAXParserFactory` and takes a `String`.** "`val parser = SAXParserFactory.newInstance().newSAXParser()`"; "`public suspend fun parse(rawRssFeed: String): RssChannel`". `JvmXmlParser.kt` and `RssParser.kt` at tag 6.1.8, <https://github.com/prof18/RSS-Parser/blob/6.1.8/rssparser/src/jvmMain/kotlin/com/prof18/rssparser/internal/JvmXmlParser.kt>.
15. **OWASP's StAX and DOCTYPE guidance.** "Disable document type definitions (DTDs) whenever possible. Reject DOCTYPE declarations if the parser supports it."; "Disabling DTD processing does not necessarily reject the DOCTYPE declaration. StAX does not support FEATURE_SECURE_PROCESSING; use the provider's processing limits when DTDs are required." <https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html>, read 2026-10-21.
16. **The JDK's StAX parser observes processing limits by default; the defaults.** "The StAX parser also observes processing limits by default even though it doesn't support FSP."; `jdk.xml.entityExpansionLimit` 64000, `jdk.xml.elementAttributeLimit` 10000, `jdk.xml.maxElementDepth` 0 ("A value less than or equal to 0 indicates no limit."). <https://docs.oracle.com/en/java/javase/21/security/java-api-xml-processing-jaxp-security-guide.html>, JDK 21.
17. **`XMLInputFactory.newDefaultFactory()` exists from Java 9.** "Creates a new instance of the XMLInputFactory builtin system-default implementation. ... Since: 9". <https://docs.oracle.com/en/java/javase/11/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html>, Java 11.
18. **If-None-Match uses weak comparison; If-Modified-Since is ignored when If-None-Match is present or the value is not an HTTP-date.** "A recipient MUST use the weak comparison function when comparing entity tags for If-None-Match"; "A recipient MUST ignore If-Modified-Since if the request contains an If-None-Match header field"; "A recipient MUST ignore the If-Modified-Since header field if the received field value is not a valid HTTP-date". <https://www.rfc-editor.org/rfc/rfc9110.txt> sections 13.1.2 and 13.1.3, June 2022.
19. **Charset priority for XML media types.** "A BOM (Section 3.3) is authoritative if it is present in an XML MIME entity"; "In the absence of a BOM (Section 3.3), the charset parameter is authoritative if it is present." <https://www.rfc-editor.org/rfc/rfc7303.txt> section 3.2, July 2014.
20. **feedparser leaves validators to the caller.** "`d2 = feedparser.parse('http://feedparser.org/docs/examples/atom10.xml', etag = d.etag)`" returns `status` 304 with empty `feed` and `entries`; "Clients should support both ETag and Last-Modified headers, as some servers support one but not the other." <https://feedparser.readthedocs.io/en/stable/http-etag.html>, feedparser 6.0.14.
21. **Rome's README tells callers to fetch with a separate library, and its fetcher repository is archived.** "Please consider using a separate library for fetching the feed"; `rometools/rome-fetcher` GitHub API: `archived: true`, `pushed_at` `2016-02-20T08:38:18Z`. <https://github.com/rometools/rome/blob/2.1.0/README.md>, <https://github.com/rometools/rome-fetcher>.
22. **A real feed reader lost conditional-GET savings by not saving updated validators.** "conditionalGetInfo gets updated no matter what." <https://inessential.com/2024/08/03/netnewswire_and_conditional_get_issues.html>, 2024-08-03.
23. **Unencoded ampersands are the commonest cause of malformed feeds (aggregator author's observation).** "The single most common cause of non-well-formedness that I see is unencoded ampersands." <https://inessential.com/2003/08/19/how_to_deal_with_broken_feeds.html>, 2003-08-19.
24. **feedparser tolerates non-well-formed feeds and flags them.** "sets the bozo bit when it detects that a feed is not well-formed"; "Universal Feed Parser can parse feeds whether they are well-formed XML or not." <https://feedparser.readthedocs.io/en/stable/bozo.html>, feedparser 6.0.14.

## Gaps

- **G1. Parse cost is unmeasured.** I did not run Rome on a 2 MiB element flood, a 100,000-deep document, or eight in parallel; the DOM amplification and the `StackOverflowError` risk are reasoning, not measurement. The semaphore default (2) and the 2 MiB `Untrusted` cap are proposals. The Level 4c suite above is the gate; if the amplification is too high, a streaming parser behind the same port is the answer.
- **G2. No corpus run.** I did not run Rome or rssreader over real or malformed feeds, so "Rome tolerates more than rssreader" rests on the repair code (`XmlFixerReader`) and the tag tables, not on a measured pass rate. The brief did not allow building or running.
- **G3. JDK StAX with `SUPPORT_DTD=false` and a DOCTYPE that declares an entity** was not established (the sources say it "does not necessarily reject"). It decides whether rssreader or an in-house StAX parser is safe without a separate DOCTYPE scan; until the suite passes, assume it is not.
- **G4. The Java 11 JDK limit defaults** were not retrievable (the Java 11 module page truncates the table). The numbers above are the Java 21 guide's. Rome sets none of them, so the effect is on the default SAX parser's behaviour on a Java 11 runtime only.
- **G5. Failure-model and marker names.** The names and shapes here are provisional. The conventions of the whole stack are set in [`api-shape-and-failures.md`](api-shape-and-failures.md), and these must follow them:
  - `FeedFailure`'s root type;
  - whether `Refused` and `NotAFeedReason` are enums or open constants;
  - `UnexpectedStatus` against the facade's upstream-status failure;
  - the opt-in marker and how `FeedParser` is gated;
  - defaulted constructor parameters.
- **G6. Rome mappings I did not verify:** which of Atom `published` and `updated` becomes `getPublishedDate()` for Atom 1.0; the `getUri()` fallbacks for RSS items without a `guid`; whether `Atom10Parser` resolves relative links from `xml:base` (so `FeedInput.baseUrl` may be redundant for Atom). The adapter and the contract suite settle them.
- **G7. Evidence on malformed-feed prevalence is old and informal** (2003 and a book estimate); it supports designing for tolerance, not a number.
- **G8. Redirect permanence.** `HttpResponse.redirectChain` carries URLs only, so the fetcher cannot report "moved permanently". If the first consumer wants to rewrite a subscription URL on a `301`, the transport's response would need the status of each hop; that is a `webtools-http` question, not decided here.
- **G9. HTML auto-discovery** (finding the feed from a site URL) is out of scope; it needs an HTML parser the requirements exclude.
- **G10. `FeedLimits` defaults** (1,000 entries, 64 KiB text, 1 KiB ids, 2 KiB links) and the `Accept` string are judgement, not measured against real feeds.
