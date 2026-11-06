// webtools-http-facades — C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/webtools-http-facades/index.html.
// As-is at WebTools feat/issue-68-call-facades 806b489 (2026-11-06), the call facades inside webtools-http 1.0.0 (new, unreleased): Endpoint, CallSpec, UpstreamClientConfig and UpstreamClient with its common and JVM owning factories, FetchSpec, UrlFetcherConfig and UrlFetcher with its common and two JVM owning factories, LoopbackFetchers in the test fixtures, and their shared internals. Code is authoritative on any conflict.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer" "A Kotlin service that declares an upstream's endpoints once and calls them through an UpstreamClient, fetches administrator-supplied URLs through a UrlFetcher, and closes what it built."
        servers = softwareSystem "Servers" "The upstream API a client calls from its base URL, and the servers an administrator's URLs name." "External"

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            facades = container "webtools-http-facades" "The call facades: UpstreamClient calls an upstream's endpoints through the policy's pre-check, the response cache, the call budget inside the cache's loader and the transport; UrlFetcher fetches administrator-supplied URLs through the pre-check, the budget and the transport. Ships inside webtools-http; common code, three JVM owning factories and one test fixture." "Kotlin Multiplatform · commonMain · jvmMain · webtools-http 1.0.0 unreleased" {
                // ---- UpstreamClient: an upstream's endpoints ----
                endpoint     = component "Endpoint" "One call an upstream offers: a stable id, the decoder, ttl, missTtl, cost, lane and the miss statuses. Built once by Endpoint(id, decode) { } over an internal constructor; immutable; toString() never prints the decoder." "final class · factory · Builder" "Public"
                callSpec     = component "CallSpec" "A call's settings, seeded from the endpoint and the client: path segments, query pairs, headers, method, body, lane, maxWait and maxAge. Confined to call's lambda." "final class · @WebToolsDsl" "Public"
                clientConfig = component "UpstreamClientConfig" "The client's settings: policy (Trusted, pinned to the base URL's host), clocks, dispatcher, rateSignals, defaultMaxWait (5 s), default headers, cache(...) and budget(...); checked in a fixed order when the factory runs." "final class · @WebToolsDsl" "Public"
                client       = component "UpstreamClient" "call(endpoint) { }: the closed check, the call's own checks, the policy's pre-check, the cache for a GET, then one permit and one request inside the cache's loader; classifies the answer into Found, Missing or a failure. utilisation, cacheStats, flush and close." "final class · AutoCloseable · common factory" "Public"
                clientOwning = component "UpstreamClient(baseUrl, userAgent)" "The JVM owning factory: builds the budget from budget(windows), then an OkHttpTransport sized to it, then a CaffeineResponseCache unless one was passed; a refused step closes what was built first." "jvmMain · factory function" "Public"
                sizing       = component "sizedFor" "Raises both OkHttp concurrency limits to at least a WindowedCallBudget's maxBurst, never lowering them, so a granted permit never waits in OkHttp's queue." "internal · jvmMain" "Internal"
                // ---- UrlFetcher: administrator-supplied URLs ----
                fetchSpec     = component "FetchSpec" "A fetch's settings: cost, lane and maxWait. Confined to fetch's lambda." "final class · @WebToolsDsl" "Public"
                fetcherConfig = component "UrlFetcherConfig" "The fetcher's settings: budget (CallBudget.Unlimited, never closed by the fetcher), rateSignals, wallClock, timeSource and defaultMaxWait (5 s)." "final class · @WebToolsDsl" "Public"
                fetcher       = component "UrlFetcher" "fetch(request) { }: the closed check, the fetch's own checks, the policy's pre-check, then one permit and one request; every status is the response but a throttle, which is RateLimited. close() closes the transport only when it was built for it." "final class · AutoCloseable · common factory" "Public"
                fetcherOwning = component "UrlFetcher(userAgent) · UrlFetcher(userAgent, policy)" "The JVM owning factories: the Untrusted defaults, or a policy of the caller's own, and an OkHttpTransport built after the configuration's check, which the fetcher owns." "jvmMain · factory functions" "Public"
                loopback      = component "LoopbackFetchers" "untrusted and trusted: a UrlFetcher over a loopback test server, the pre-check relaxed for loopback exactly as the transport is. In webtools-http's test-fixtures variant." "jvmTestFixtures · testFixtures(...)" "Public"
                // ---- shared by both facades ----
                dispatch  = component "CallDispatch" "The one place that touches a permit: withPermit, execute, read the rate signals, then complete(signals); release() only for the transport's InvalidRequest; throttleOf builds RateLimited from one utilisation read." "internal class" "Internal"
                assembly  = component "RequestAssembly" "The call's settings applied and checked, the URL assembled as text and parsed again, the cache-key parameters, the request built; every failure an InvalidRequest, nothing spent." "internal functions" "Internal"
                verdict   = component "ResponseVerdict" "verdictOf: a throttle, then a declared miss, then a 2xx to decode, else rejected; fetchVerdictOf: only a throttle is not the response; retryAfterOf: the longer of Retry-After and the budget's pause." "internal functions" "Internal"
                lifecycle = component "FacadeLifecycle" "Owned and closeInOrder (cache, budget, transport, each step's failure logged and the next still closed), the per-call trace from the loader to the call, and the log names of the parts." "internal" "Internal"
                messages  = component "FacadeMessages" "The two component names, the two logger names, the ten rules of an InvalidRequest and the eleven messages of a construction refusal: constant, never a value." "internal constants" "Internal"
            }
            policy    = container "webtools-http-url-policy" "OutboundUrlPolicy and the internal UrlGuard the facades pre-check every request with. Its own board." "Kotlin Multiplatform · commonMain"
            cache     = container "webtools-http-cache" "The ResponseCache port, ResponseCache.Disabled and CaffeineResponseCache: one load per key, failures never stored. Its own board." "Kotlin Multiplatform · commonMain · jvmMain"
            budget    = container "webtools-http-budget" "The CallBudget port, CallBudget.Unlimited, WindowedCallBudget and RateSignalsReader. Its own board." "Kotlin Multiplatform · commonMain"
            transport = container "webtools-http-transport" "The HttpTransport port's supplied OkHttpTransport: one request per hop under the policy, never a retry. Its own board." "Kotlin · jvmMain · okhttp-jvm 5.5.0"
            core      = container "webtools-http common core" "What the facades use from the rest of the module: Url, HttpRequest and HttpResponse, HttpFailure with InvalidRequest, RefusedByPolicy, RateLimited, UpstreamError, DecodeFailure and Closed, Lookup, WallClock, WebToolsDsl and the internal Log seam. Its own board." "Kotlin Multiplatform · commonMain"
        }

        consumer -> webtools.facades.endpoint "Declares each endpoint once with Endpoint(id, decode) { }"
        consumer -> webtools.facades.clientOwning "Builds an owning client on the JVM"
        consumer -> webtools.facades.client "Calls call(endpoint) { }, then flushes and closes it"
        consumer -> webtools.facades.fetcherOwning "Builds an owning fetcher on the JVM"
        consumer -> webtools.facades.fetcher "Fetches an administrator's URL with fetch(request) { }, then closes it"
        consumer -> webtools.facades.loopback "Tests its fetches over a loopback server"
        webtools.facades.client -> webtools.facades.clientConfig "Is configured through; the construction checks run in a fixed order"
        webtools.facades.client -> webtools.facades.callSpec "Applies each call's settings to"
        webtools.facades.client -> webtools.facades.endpoint "Keys, charges and decodes each call by"
        webtools.facades.client -> webtools.facades.assembly "Checks the call and assembles its URL and cache key before anything is spent"
        webtools.facades.client -> webtools.policy "Pre-checks every request with UrlGuard.checkRequest"
        webtools.facades.client -> webtools.cache "Loads a GET through getOrLoad; the permit is taken inside the loader"
        webtools.facades.client -> webtools.facades.dispatch "Sends each load through"
        webtools.facades.client -> webtools.facades.verdict "Classifies each answer"
        webtools.facades.client -> webtools.facades.lifecycle "Closes what it built: cache, budget, transport"
        webtools.facades.clientConfig -> webtools.budget "Builds and owns a WindowedCallBudget from budget(windows)"
        webtools.facades.clientOwning -> webtools.facades.client "Builds"
        webtools.facades.clientOwning -> webtools.facades.sizing "Sizes the transport to the budget's maxBurst"
        webtools.facades.clientOwning -> webtools.transport "Builds and owns an OkHttpTransport"
        webtools.facades.clientOwning -> webtools.cache "Builds and owns a CaffeineResponseCache unless one was passed"
        webtools.facades.fetcher -> webtools.facades.fetcherConfig "Is configured through"
        webtools.facades.fetcher -> webtools.facades.fetchSpec "Applies each fetch's settings to"
        webtools.facades.fetcher -> webtools.policy "Pre-checks every request with UrlGuard.checkRequest"
        webtools.facades.fetcher -> webtools.facades.dispatch "Sends each fetch through"
        webtools.facades.fetcher -> webtools.facades.verdict "Tells a throttle from a response"
        webtools.facades.fetcher -> webtools.facades.lifecycle "Closes its transport when it was built for it"
        webtools.facades.fetcherOwning -> webtools.facades.fetcher "Builds"
        webtools.facades.fetcherOwning -> webtools.transport "Builds and owns an OkHttpTransport under the policy"
        webtools.facades.loopback -> webtools.facades.fetcher "Builds one over a loopback transport"
        webtools.facades.dispatch -> webtools.budget "Takes one permit per load and settles it: complete(signals), or release() for the transport's InvalidRequest"
        webtools.facades.dispatch -> webtools.transport "Executes the request once"
        webtools.facades.assembly -> webtools.core "Parses the assembled text with Url.parse; builds the HttpRequest"
        webtools.facades.assembly -> webtools.facades.messages "Names its rules from"
        webtools.facades.verdict -> webtools.core "Returns Lookup values and the failure model's leaves"
        webtools.transport -> servers "Sends one request per hop"
    }

    views {
        systemContext webtools "Context" "WebTools, the consumer of the call facades and the servers they reach." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The call facades and the parts of webtools-http they compose." {
            include *
            autoLayout lr
        }
        component webtools.facades "Components" "The call facades as built at feat/issue-68-call-facades 806b489." {
            include *
            autoLayout tb
        }
        dynamic webtools.facades "Call" "A consumer calls one endpoint; the cache misses, so this call's loader takes a permit and sends the request, as built." {
            consumer -> webtools.facades.client "call(hero) { path(id) }"
            webtools.facades.client -> webtools.facades.assembly "the settings, the limits, the URL and cache key, the request: nothing spent"
            webtools.facades.client -> webtools.policy "checkRequest(request): allowed"
            webtools.facades.client -> webtools.cache "getOrLoad(key, policy, maxAge, loader): a miss, so the loader runs"
            webtools.facades.client -> webtools.facades.dispatch "the loader: send(request, cost, lane, maxWait)"
            webtools.facades.dispatch -> webtools.budget "withPermit: a permit, then complete(signals)"
            webtools.facades.dispatch -> webtools.transport "execute(request): 200"
            webtools.facades.client -> webtools.facades.verdict "verdictOf: decode, then Found(value), stored for ttl"
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
