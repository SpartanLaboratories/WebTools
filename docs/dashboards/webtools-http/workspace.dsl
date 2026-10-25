// webtools-http — C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/webtools-http/index.html.
// As-is at WebTools master 4fcc6e0 (2026-10-25), webtools-http 1.0.0 (new, unreleased): the common core from #62, its check-only js module, and the test-fixtures variant from #63. Code is authoritative on any conflict.
// Elements tagged Planned come from Issue #55's architecture (docs/plans/55-http-client-stack/architecture.md, sections 6.3 to 6.6 and 11). None of them is built; they are drawn dashed.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer" "A Kotlin service that builds requests, matches the failures and supplies or receives an HttpTransport."

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            http = container "webtools-http" "The common core of a suspend HTTP stack: the opt-in marker, the failure model, the request and response types, Url, and the transport and wall-clock ports; and its published test-fixtures variant. No production implementation beyond WallClock.System yet." "Kotlin Multiplatform library · jvm() · 1.0.0 unreleased" {
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
                log       = component "Log seam" "Log with debug, info and warn, through expect fun logger(name): slf4j in jvmMain. No production logging site yet." "internal interface · expect/actual" "Internal"
                helpers   = component "Helpers" "Result.flatMap, catchNonFatal and expect fun epochMillisNow(): System.currentTimeMillis() in jvmMain." "internal functions · expect/actual" "Internal"
                // ---- the test-fixtures variant: a JVM-only testFixtures compilation associated with main, published beside it ----
                fixtures  = component "Test fixtures" "The webtools-http-test-fixtures variant, package com.spartanlabs.webtools.http.testing: VirtualClock, the scripted FakeHttpTransport, Fixture and FixtureLoader for .http and body-only files, and the gated, redacting FixtureRecorder. All final; Stable Core with source compatibility only." "jvmTestFixtures · testFixtures(...) · test classpath only" "Public"
                // ---- planned by Issue #55, not built ----
                policy    = component "OutboundUrlPolicy" "Trusted and Untrusted policies, TransportSettings and the address classifier." "planned" "Planned"
                okhttp    = component "OkHttpTransport" "The OkHttp transport with manual redirects and the resolver and socket guards." "planned" "Planned"
                cache     = component "ResponseCache" "The cache port and its Caffeine default." "planned" "Planned"
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

        // ---- planned by Issue #55 (dashed: one end is Planned) ----
        webtools.http.policy -> webtools.http.failures "Would refuse with RefusedByPolicy"
        webtools.http.okhttp -> webtools.http.transport "Would implement"
        webtools.http.cache -> webtools.http.clock "Would expire by"
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
        component webtools.http "Components" "webtools-http as built at master 4fcc6e0, its test fixtures included, with the stack's later units dashed." {
            include *
            autoLayout tb
        }
        dynamic webtools.http "Request" "A consumer builds a request and a transport answers it, as built." {
            consumer -> webtools.http.url "Url.parse(text)"
            consumer -> webtools.http.values "HttpRequest(HttpMethod.Get, url) { headers = HttpHeaders { … } }"
            consumer -> webtools.http.transport "execute(request)"
            webtools.http.transport -> webtools.http.values "HttpResponse(status, url) { … }"
            webtools.http.transport -> webtools.http.failures "or a TransportFailure, inside the Result"
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
