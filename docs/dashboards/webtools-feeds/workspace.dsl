// webtools-feeds — C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/webtools-feeds/index.html.
// As-is at WebTools feat/issue-99-dokka-api-docs 2c634f4 (2026-11-09), webtools-feeds 1.0.0 (new, unreleased): the FeedParser port with FeedInput and FeedLimits, the model, the FeedFailure group and its reasons, FeedValidators, FeedFetch and FeedFetcher with its common and two JVM owning factories, and RomeFeedParser over Rome 2.1.0, with their internals. Code is authoritative on any conflict.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer" "A Kotlin service that polls feeds: it builds a FeedFetcher, stores the validators each fetch returns and hands them back on the next poll, and decides which entries are new."
        servers = softwareSystem "Feed servers" "The servers an administrator's feed URLs name." "External"
        rome = softwareSystem "Rome and JDOM" "com.rometools:rome 2.1.0 with rome-utils 2.1.0 and jdom2 2.0.6.1: the feed reader RomeFeedParser runs inside one guarded region." "External"

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            feeds = container "webtools-feeds" "RSS 0.90 to 2.0, RSS 1.0 and Atom 0.3 and 1.0 behind a hardened parser port with a Rome default, and a stateless conditional-GET FeedFetcher over UrlFetcher. Publishes webtools-feeds and webtools-feeds-jvm." "Kotlin Multiplatform (jvm) · 1.0.0 unreleased" {
                // ---- the fetcher ----
                fetcher       = component "FeedFetcher" "fetch(url, validators) { }: the closed check, a GET with Accept and the validators sent verbatim through UrlFetcher.fetch, the status verdict, then a parse under a permit on parseDispatcher; close() closes only a UrlFetcher it built." "final class · AutoCloseable · common factory" "Public"
                fetcherConfig = component "FeedFetcherConfig" "The fetcher's settings: parser (the platform's default), parseDispatcher (Dispatchers.Default), maxConcurrentParses (2) and accept; checked before anything is built." "final class · @WebToolsDsl" "Public"
                fetcherOwning = component "FeedFetcher.untrusted" "The JVM owning factories: an Untrusted policy, or the caller's own, and an owned UrlFetcher(userAgent, policy) built after the configuration's check." "jvmMain · factory functions" "Public"
                fetchResult   = component "FeedFetch" "The sealed outcome of a fetch: Fetched with the feed, or NotModified; both carry the validators to store, finalUrl and permanentlyMovedTo." "sealed interface · two final leaves" "Public"
                validators    = component "FeedValidators" "The ETag and Last-Modified of the last response, held verbatim and checked the same whether read from a response or loaded from storage." "final class" "Public"
                conditional   = component "conditional-GET rules" "requestHeaders, validatorsOf and refreshedBy; statusVerdictOf (200 and 203 parsed, a 304 to a conditional request not modified, every other status upstream); permanentMoveOf, the leading run of 301 and 308 hops." "internal functions" "Internal"
                // ---- the supplied parser ----
                romeParser    = component "RomeFeedParser" "The supplied FeedParser on the JVM: the prolog scan, then Rome with a SAX parser of its own per parse under the structural limits, then the conversion under the remaining limits." "final class · jvmMain" "Public"
                prologScan    = component "PrologScan" "Finds a DOCTYPE before any parser runs, reading the prolog three ways within 64 KiB; total and stateless." "internal object · commonMain" "Internal"
                romeAdapter   = component "Rome adapter" "openXmlReader, read the way the scan reads; HardenedWireFeedInput, a SAX parser of its own per parse with DOCTYPE and external entities off and the depth and attribute limits; CountingSaxHandler for elements and namespace URIs." "internal · jvmMain" "Internal"
                romeMapping   = component "Rome mapping and conversion" "inRomeRegion and toFeedFailure map what Rome, JDOM and the JDK throw by type and JAXP code; readWithRome and toFeed count the entries first, then convert under the text, id, link, category and enclosure limits." "internal · jvmMain" "Internal"
                defaultParser = component "defaultFeedParser" "The platform's default parser: RomeFeedParser() on the JVM; the check module's js actual returns NotAFeed(UnknownFormat)." "internal expect · actual" "Internal"
                // ---- the port and the model ----
                parserPort    = component "FeedParser" "The parser port: parse(input): Result<Feed>, never throwing for a bad document. Implementing it needs the opt-in." "interface · @SubclassOptInRequired" "Public"
                input         = component "FeedInput" "One document: its bytes, copied in and read back as copies, the response's Content-Type and the base URL for relative links." "final class" "Public"
                limits        = component "FeedLimits" "Ten bounds that can only be tightened: entries, text, id and link length, categories and enclosures per entry, depth, attributes per element, elements and namespace URIs." "final class · two factories · Builder" "Public"
                feedModel     = component "Feed · FeedEntry · FeedEnclosure" "The parsed feed: channel fields and entries in document order; each entry with a never-blank, frozen id and links that are absolute http or https or null." "final classes · factories · Builders" "Public"
                failure       = component "FeedFailure" "The failure group under HttpFailure: NotAFeed, Malformed, Refused and LimitExceeded, with the reasons NotAFeedReason, FeedRefusal and FeedLimit; constant messages, never a cause." "abstract class · @SubclassOptInRequired · final leaves" "Public"
                entryRules    = component "entry rules" "The id rule and its SHA-256 digest (webtools-feeds:v1:), the link rule (RFC 3986 resolution, http and https only) and the text and date rules." "internal functions · commonMain" "Internal"
                support       = component "module support" "The module's copies of webtools-http's internal logging seam, catchNonFatal and flatMap; origin(), which prints a URL as scheme, host and port; the constant messages." "internal · commonMain" "Internal"
            }
            facades = container "webtools-http-facades" "UrlFetcher: the outbound-URL policy's pre-check, the call budget and the transport for administrator-supplied URLs. Its own board." "Kotlin Multiplatform · webtools-http 1.0.0 unreleased"
            core    = container "webtools-http common core" "What the feeds module uses from the rest of webtools-http: Url, HttpRequest and HttpResponse, HttpFailure with UpstreamError, DecodeFailure and Closed, FetchSpec, WebToolsDsl and the opt-in marker. Its own board." "Kotlin Multiplatform · commonMain"
            check   = container "webtools-feeds-commoncheck" "Compiles webtools-feeds' commonMain for js with no environment, so a JVM API in common code is a compile error; publishes nothing. On the Gradle build's board." "Kotlin/JS compile only · unpublished"
        }

        consumer -> webtools.feeds.fetcherOwning "Builds an owning fetcher for administrator-supplied feed URLs"
        consumer -> webtools.feeds.fetcher "Fetches with fetch(url, validators), then closes it"
        consumer -> webtools.feeds.validators "Stores the validators after every response and hands them back"
        consumer -> webtools.feeds.parserPort "May supply a parser of its own, behind the opt-in"
        webtools.feeds.fetcher -> webtools.feeds.fetcherConfig "Is configured through; the check runs before anything is built"
        webtools.feeds.fetcher -> webtools.facades "Sends one GET per fetch through UrlFetcher.fetch; its failures pass through unchanged"
        webtools.feeds.fetcher -> webtools.feeds.conditional "Builds the conditional headers and reads the status and the redirect chain"
        webtools.feeds.fetcher -> webtools.feeds.parserPort "Parses a 200 or 203 under a permit, on parseDispatcher"
        webtools.feeds.fetcher -> webtools.feeds.fetchResult "Returns Fetched or NotModified"
        webtools.feeds.fetcherConfig -> webtools.feeds.defaultParser "Takes the platform's default parser"
        webtools.feeds.fetcherOwning -> webtools.feeds.fetcher "Builds"
        webtools.feeds.fetcherOwning -> webtools.facades "Builds and owns UrlFetcher(userAgent, policy)"
        webtools.feeds.fetchResult -> webtools.feeds.validators "Carries the validators to store"
        webtools.feeds.defaultParser -> webtools.feeds.romeParser "Is RomeFeedParser() on the JVM"
        webtools.feeds.romeParser -> webtools.feeds.parserPort "Implements"
        webtools.feeds.romeParser -> webtools.feeds.prologScan "Scans the prolog before any parser runs"
        webtools.feeds.romeParser -> webtools.feeds.romeAdapter "Reads the document with a SAX parser of its own"
        webtools.feeds.romeParser -> webtools.feeds.romeMapping "Maps what the region throws and converts the result"
        webtools.feeds.romeAdapter -> rome "Runs Rome's WireFeedInput over a SAX parser of its own"
        webtools.feeds.romeMapping -> webtools.feeds.entryRules "Applies the id, link and text rules"
        webtools.feeds.romeMapping -> webtools.feeds.feedModel "Builds every value through the factories"
        webtools.feeds.romeMapping -> webtools.feeds.failure "Returns a failure leaf, never a cause"
        webtools.feeds.parserPort -> webtools.feeds.input "Reads"
        webtools.feeds.parserPort -> webtools.feeds.feedModel "Returns a Feed"
        webtools.feeds.romeParser -> webtools.feeds.limits "Applies every bound"
        webtools.feeds.failure -> webtools.core "Extends HttpFailure"
        webtools.feeds.fetcher -> webtools.feeds.support "Logs through the seam; prints a URL as its origin"
        webtools.check -> webtools.feeds "Compiles commonMain for js"
        webtools.facades -> servers "Sends one request per hop under the policy"
    }

    views {
        systemContext webtools "Context" "WebTools, the consumer of webtools-feeds, the feed servers it reaches and the reader it runs." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "webtools-feeds, the parts of webtools-http it composes and its check module." {
            include *
            autoLayout lr
        }
        component webtools.feeds "Components" "webtools-feeds as built at feat/issue-99-dokka-api-docs 2c634f4." {
            include *
            autoLayout tb
        }
        dynamic webtools.feeds "Parse hand-off" "A fetch whose 200 is parsed: the fetcher takes a parse permit and hands the parse to parseDispatcher, as built." {
            consumer -> webtools.feeds.fetcher "fetch(url, stored)"
            webtools.feeds.fetcher -> webtools.feeds.conditional "Accept and the stored validators, verbatim"
            webtools.feeds.fetcher -> webtools.facades "UrlFetcher.fetch(GET url): 200"
            webtools.feeds.fetcher -> webtools.feeds.parserPort "a parse permit, then withContext(parseDispatcher): parse(input)"
            webtools.feeds.romeParser -> webtools.feeds.prologScan "no DOCTYPE"
            webtools.feeds.romeAdapter -> rome "build under the limits"
            webtools.feeds.fetcher -> webtools.feeds.fetchResult "Fetched(feed, validators, finalUrl, permanentlyMovedTo)"
            autoLayout lr
        }
        styles {
            element "Person"          { shape person  background #08427b color #ffffff }
            element "Software System" { background #1168bd color #ffffff }
            element "External"        { background #999999 color #ffffff }
            element "Container"       { background #438dd5 color #ffffff }
            element "Component"       { background #85bbf0 color #000000 }
            element "Internal"        { background #c6dcf5 color #000000 }
            element "Standalone"      { background #eeeeee color #000000 border dotted }
            element "Planned"         { background #ffffff color #666666 border dashed opacity 60 }
        }
    }
}
