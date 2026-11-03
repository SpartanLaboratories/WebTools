// webtools-http — C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/webtools-http/index.html.
// As-is at WebTools feat/issue-66-response-cache 38483ea (2026-11-03), webtools-http 1.0.0 (new, unreleased): the common core from #62, its check-only js module, the test-fixtures variant from #63, the outbound-URL policies with their internal classifier and JVM guards and the WebToolsDsl marker from #64, OkHttpTransport with its internal parts and the loopback fixtures from #65, and the response cache with its Caffeine default from #66. Code is authoritative on any conflict.
// Elements tagged Planned come from Issue #55's architecture (docs/plans/55-http-client-stack/architecture.md, sections 6.5, 6.6 and 11). None of them is built; they are drawn dashed.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer" "A Kotlin service that builds requests, matches the failures, supplies or receives an HttpTransport and loads through a ResponseCache."

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            http = container "webtools-http" "A suspend HTTP stack, built in part: the opt-in marker, the failure model, the request and response types, Url, and the transport and wall-clock ports; the outbound-URL policies with their internal classifier and JVM guards; OkHttpTransport, which enforces a policy on every hop; the ResponseCache port with its Caffeine default; and its published test-fixtures variant." "Kotlin Multiplatform library · jvm() · 1.0.0 unreleased" {
                // ---- public surface ----
                marker    = component "ExperimentalForInheritanceWebToolsApi" "The one opt-in marker: @RequiresOptIn(level = ERROR), used only through @SubclassOptInRequired and @OptIn; never removed." "annotation class" "Public"
                transport = component "HttpTransport" "suspend execute(request): Result<HttpResponse> and an idempotent close(); an eight-point contract. Implementing it needs the opt-in." "interface · AutoCloseable · gated" "Public"
                clock     = component "WallClock" "nowEpochMillis(); the nested, final WallClock.System reads the platform clock and needs no opt-in." "interface · gated" "Public"
                failures  = component "HttpFailure" "The failure model: the groups TransportFailure (eleven leaves) and UpstreamFailure (RateLimited, UpstreamError), and Closed, BudgetExhausted, DecodeFailure. Subclassing a group needs the opt-in." "abstract classes · gated · final leaves" "Public"
                reasons   = component "RefusalReason · ExhaustedReason" "Open constants classes, nine and four constants, equal by name; may gain constants in a feature release." "final classes" "Public"
                values    = component "Request and response values" "HttpMethod, HttpHeaders, RequestBody, HttpRequest, HttpResponse, RedirectHop: immutable, built by factories over internal constructors; bytes copied in and out." "final classes · factories" "Public"
                url       = component "Url" "parse(text): Result<Url>, canonical as OkHttp 5.5.0's HttpUrl for ASCII hosts and only ever stricter; toString() is the full URL, redacted() the form for logs." "final class" "Public"
                // ---- internals ----
                pipeline  = component "URL pipeline" "UrlParsing, UrlHost, UrlEncoding and UrlProblem: trim, scan, scheme, authority, port, host, then path and query; the internal path and query encoders." "internal functions" "Internal"
                syntax    = component "HttpSyntax" "The shared predicates: the token rule, the value rules, the media type, sanitizeReceivedValue." "internal object" "Internal"
                log       = component "Log seam" "Log with debug, info and warn, through expect fun logger(name): slf4j in jvmMain. OkHttpTransport, GuardedDns and CaffeineResponseCache log through it." "internal interface · expect/actual" "Internal"
                helpers   = component "Helpers" "Result.flatMap, catchNonFatal and expect fun epochMillisNow(): System.currentTimeMillis() in jvmMain." "internal functions · expect/actual" "Internal"
                dsl       = component "WebToolsDsl" "The DSL marker on the eight builders a consumer's lambda receives; a nested block reaches an outer builder only through its label." "annotation class · @DslMarker" "Public"
                // ---- the outbound-URL policy: public configuration values, an internal JVM-free classifier, internal JVM guards ----
                policy    = component "OutboundUrlPolicy" "Trusted and Untrusted policies as configuration values, with TransportSettings (TrustedDefaults, UntrustedDefaults), CidrBlock and MixedAnswers. Untrusted parameters only add denials. OkHttpTransport applies one to every hop; the planned facades will pre-check a URL." "sealed class · final leaves · builders" "Public"
                classifier = component "Address classifier" "UrlGuard with HostRules, AddressRanges, StrictIp and GuardRelaxation: pure, JVM-free judgement of a hop, a redirect target, a resolved address and a resolver answer under one policy." "internal · commonMain" "Internal"
                guards    = component "JVM guards" "PolicyEnforcement: GuardedDns, GuardedSocketFactory with its Socket(Proxy.NO_PROXY) and Proxy.NO_PROXY for Untrusted; the refusal markers found by policyRefusal(). OkHttpTransport applies them to its client." "internal · jvmMain · okhttp-jvm 5.5.0" "Internal"
                // ---- the test-fixtures variant: a JVM-only testFixtures compilation associated with main, published beside it ----
                fixtures  = component "Test fixtures" "The webtools-http-test-fixtures variant, package com.spartanlabs.webtools.http.testing: VirtualClock, the scripted FakeHttpTransport, Fixture and FixtureLoader for .http and body-only files, the gated, redacting FixtureRecorder, and LoopbackTransports with MockWebServer.webtoolsUrl, Fixture.toMockResponse and MockWebServer.enqueue(fixture): the real OkHttpTransport over a loopback mockwebserver3 server with the policy's rules live. All final; Stable Core with source compatibility only." "jvmTestFixtures · testFixtures(...) · test classpath only" "Public"
                // ---- the OkHttp transport: one public final class and its factory, its parts internal ----
                okhttp    = component "OkHttpTransport" "The supplied HttpTransport, final, built by the factory OkHttpTransport(userAgent, policy): one request per hop and never a retry, manual redirects judged by the policy, one deadline across every hop, a capped decoded body, every outcome a Result; usable without the opt-in." "final class · factory · jvmMain · okhttp-jvm 5.5.0" "Public"
                client    = component "Transport client" "TransportClient: one OkHttpClient per transport with its own daemon dispatcher, connection pool and timeouts; redirects, retries, authentication, cookies and the cache switched off. ExchangeGuard, its one network interceptor: a second exchange of one hop fails as RetryRefused, and a 407, 408, 421 or 503 is hidden from OkHttp's follow-up logic and delivered once." "internal · jvmMain" "Internal"
                hops      = component "Hop pipeline" "CallBridge suspends on one enqueued Call; HopRequest, HopResponse, ReceivedResponse, RedirectDecision, CappedBody, CallDeadline and FailureMapping build the hop, settle its response and map its failure; ThrowableSearch is the bounded walk it shares with the policy's refusal markers." "internal · jvmMain" "Internal"
                controlled = component "TransportControlledHeaders" "The twelve request headers the transport refuses from a caller, JVM-free, so the check module compiles it for js and the facades can refuse the same names." "internal · commonMain" "Internal"
                // ---- the response cache: the port and its values in common code, the Caffeine default on the JVM ----
                cache     = component "ResponseCache" "The cache port: getOrLoad, invalidate, invalidateAll, stats and an idempotent close(), under an eleven-point contract: one load per key at a time, failures never stored. Implementing it needs the opt-in; the nested, final ResponseCache.Disabled stores nothing and needs none." "interface · AutoCloseable · gated · commonMain" "Public"
                cacheValues = component "Cache values" "CacheKey, CachePolicy with its builder and factory, the sealed Lookup (Found, Missing) and CacheStats: immutable; toString() prints names and weights, never a value." "final classes · a sealed pair · commonMain" "Public"
                loaderRunner = component "Loader runner" "runLoader: the one place that decides what a loader's exception, Error and cancellation mean; ResponseCache.Disabled and the Caffeine default share it." "internal · commonMain" "Internal"
                caffeine  = component "CaffeineResponseCache" "The supplied ResponseCache, final, built by the factory CaffeineResponseCache { }: one shared future per key, per-entry lifetimes from the load's completion, a weight bound, a per-call maxAge; usable without the opt-in. It owns no thread, timer or pool; loads run in its own scope on loadDispatcher." "final class · factory · builder · jvmMain · caffeine 3.3.1" "Public"
                cacheEntry = component "Cache entry and rules" "CacheEntry, CacheOutcome, Residency, Storage and CacheExpiry: what a finished load holds, how long it lives, and whether a call may be served it; the rules are pure." "internal · jvmMain · caffeine 3.3.1" "Internal"
                // ---- planned by Issue #55, not built ----
                budget    = component "CallBudget" "The budget port, WindowedCallBudget and the rate signals." "planned" "Planned"
                facades   = component "UpstreamClient · UrlFetcher" "The facades composing cache, budget and transport." "planned" "Planned"
            }
            check = container "webtools-http-commoncheck" "Compiles webtools-http's commonMain for js with a no-op Log and Date.now(), so a JVM API in common code is a compile error. Unpublished." "Kotlin/JS klib · check only" "Internal"
        }

        // ---- as built ----
        consumer -> webtools.http.url "Parses text with Url.parse"
        consumer -> webtools.http.values "Builds requests with the factories"
        consumer -> webtools.http.transport "Calls execute(request) and close(), or implements it with the opt-in"
        consumer -> webtools.http.clock "Reads WallClock.System, or supplies its own"
        consumer -> webtools.http.failures "Matches the failures, keeping an else"
        webtools.http.marker -> webtools.http.transport "Gates implementing it"
        webtools.http.marker -> webtools.http.clock "Gates implementing it"
        webtools.http.marker -> webtools.http.failures "Gates subclassing the groups"
        webtools.http.values -> webtools.http.url "Carry"
        webtools.http.values -> webtools.http.syntax "Validate names, values and media types with"
        webtools.http.url -> webtools.http.pipeline "Parses and prints through"
        webtools.http.pipeline -> webtools.http.helpers "Joins its steps with flatMap"
        webtools.http.transport -> webtools.http.values "Returns an HttpResponse for an HttpRequest"
        webtools.http.transport -> webtools.http.failures "Fails with a TransportFailure, or Closed after close()"
        webtools.http.failures -> webtools.http.reasons "Carry the refusal and exhaustion reasons"
        webtools.http.clock -> webtools.http.helpers "WallClock.System reads epochMillisNow()"
        webtools.check -> webtools.http "Compiles its commonMain for js, through a relative srcDir"
        consumer -> webtools.http.fixtures "Tests its providers with testFixtures(...): no network"
        webtools.http.fixtures -> webtools.http.transport "Implements it with FakeHttpTransport, and decorates a live one to record"
        webtools.http.fixtures -> webtools.http.clock "Implements it with VirtualClock's wall clock"
        webtools.http.fixtures -> webtools.http.values "Builds responses from fixtures; parses and writes .http files"
        webtools.http.fixtures -> webtools.http.failures "Returns Closed and BodyTooLarge as a transport does"
        consumer -> webtools.http.policy "Builds Trusted or Untrusted policies and their settings"
        webtools.http.classifier -> webtools.http.policy "Judges hops and addresses under"
        webtools.http.classifier -> webtools.http.url "Reads the canonical host, port and scheme of"
        webtools.http.classifier -> webtools.http.reasons "Refuses with a RefusalReason"
        webtools.http.guards -> webtools.http.classifier "Judge every resolver answer and every connect through"
        webtools.http.dsl -> webtools.http.values "Marks the header, request and response builders"
        webtools.http.dsl -> webtools.http.policy "Marks the policy and settings builders"

        consumer -> webtools.http.okhttp "Builds it with OkHttpTransport(userAgent, policy), then calls execute(request) and close()"
        webtools.http.okhttp -> webtools.http.transport "Implements"
        webtools.http.okhttp -> webtools.http.classifier "Judges the request and every redirect target before any socket"
        webtools.http.okhttp -> webtools.http.guards "Applies them to its client"
        webtools.http.okhttp -> webtools.http.controlled "Refuses a request carrying one, before anything is sent"
        webtools.http.okhttp -> webtools.http.client "Builds one client per transport"
        webtools.http.okhttp -> webtools.http.hops "Runs each hop through"
        webtools.http.okhttp -> webtools.http.log "Logs through: never a header, a body, a query string or an address"
        webtools.http.hops -> webtools.http.failures "Maps every outcome to a leaf, a policy refusal to RefusedByPolicy"
        webtools.http.hops -> webtools.http.values "Builds the HttpResponse"
        webtools.http.hops -> webtools.http.guards "Finds a refusal marker through ThrowableSearch"
        webtools.http.fixtures -> webtools.http.okhttp "Builds the real transport over loopback: LoopbackTransports"

        consumer -> webtools.http.caffeine "Builds it with CaffeineResponseCache { }, then calls getOrLoad, invalidate, stats and close()"
        consumer -> webtools.http.cacheValues "Builds keys and policies; its loader returns a Lookup"
        webtools.http.caffeine -> webtools.http.cache "Implements"
        webtools.http.marker -> webtools.http.cache "Gates implementing it"
        webtools.http.cache -> webtools.http.cacheValues "Takes a CacheKey and a CachePolicy, returns a Result of a Lookup"
        webtools.http.cache -> webtools.http.failures "Fails with Closed after close()"
        webtools.http.cache -> webtools.http.loaderRunner "Disabled runs each loader through"
        webtools.http.caffeine -> webtools.http.loaderRunner "Runs each load through"
        webtools.http.caffeine -> webtools.http.cacheEntry "Holds one shared future of an entry per key"
        webtools.http.caffeine -> webtools.http.log "Logs through: never a value, a parameter value or a message text"
        webtools.http.loaderRunner -> webtools.http.helpers "Joins the outcome with catchNonFatal and flatMap"
        webtools.http.cacheEntry -> webtools.http.cacheValues "Reads the policy's lifetimes and the Lookup's weight"
        webtools.http.dsl -> webtools.http.cacheValues "Marks CachePolicy's builder"
        webtools.http.dsl -> webtools.http.caffeine "Marks its builder"
        webtools.check -> webtools.http.loaderRunner "Compiles the port, its values and the runner for js"

        // ---- planned by Issue #55 (dashed: one end is Planned) ----
        webtools.http.facades -> webtools.http.classifier "Would pre-check a URL with checkRequest"
        webtools.http.facades -> webtools.http.cache "Would load through"
        webtools.http.budget -> webtools.http.failures "Would fail with BudgetExhausted"
        webtools.http.facades -> webtools.http.transport "Would compose with the cache and the budget"
    }

    views {
        systemContext webtools "Context" "WebTools and the consumer of webtools-http." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "webtools-http and its check-only js module." {
            include *
            autoLayout lr
        }
        component webtools.http "Components" "webtools-http as built at feat/issue-66-response-cache 38483ea, its URL policy, OkHttp transport, response cache and test fixtures included, with the stack's later units dashed." {
            include *
            autoLayout tb
        }
        dynamic webtools.http "Request" "A consumer builds a request and OkHttpTransport answers it, as built." {
            consumer -> webtools.http.url "Url.parse(text)"
            consumer -> webtools.http.values "HttpRequest(HttpMethod.Get, url) { headers = HttpHeaders { … } }"
            consumer -> webtools.http.okhttp "OkHttpTransport(userAgent, policy), then execute(request)"
            webtools.http.okhttp -> webtools.http.classifier "checkRequest: before any socket"
            webtools.http.okhttp -> webtools.http.hops "each hop: one enqueued Call under the deadline"
            webtools.http.hops -> webtools.http.values "HttpResponse(status, url) { … }"
            webtools.http.hops -> webtools.http.failures "or a TransportFailure, inside the Result"
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
