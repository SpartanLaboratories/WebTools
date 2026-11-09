// webtools-http — C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/webtools-http/index.html.
// As-is at WebTools feat/issue-99-dokka-api-docs 2c634f4 (2026-11-09), webtools-http 1.0.0 (new, unreleased), drawn as the parts it binds, one component each: the common core, the outbound-URL policy, the OkHttp transport, the response cache, the call budget, the call facades and the published test fixtures, each with its own board and model under docs/dashboards/<part>/, and its check-only js module. Code is authoritative on any conflict.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer" "A Kotlin service that calls an upstream through UpstreamClient and fetches administrator-supplied URLs through UrlFetcher, or composes the parts itself: requests and policies, OkHttpTransport, a ResponseCache and a CallBudget; and tests its providers with the published fixtures."
        web = softwareSystem "Servers" "The HTTP servers a request and its redirects reach." "External"

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            http = container "webtools-http" "A suspend HTTP stack: its common core, its outbound-URL policies, the OkHttp transport, the response cache, the call budget, the call facades that compose them and its published test-fixtures variant." "Kotlin Multiplatform library · jvm() · 1.0.0 unreleased" {
                core     = component "webtools-http-core" "The common core: the opt-in marker, the failure model, the request and response values, Url, the transport and wall-clock ports, the DSL marker, the logging seam and helpers. Its own board: webtools-http-core." "commonMain · two jvmMain actuals" "Public"
                policy   = component "webtools-http-url-policy" "The outbound-URL policies Trusted and Untrusted with TransportSettings, an internal JVM-free classifier and internal JVM resolver and socket guards. Its own board: webtools-http-url-policy." "commonMain values and classifier · jvmMain guards" "Public"
                okhttp   = component "webtools-http-transport" "OkHttpTransport, the supplied HttpTransport: one request per hop and never a retry, manual redirects judged by the policy, one deadline, a capped decoded body. Its own board: webtools-http-transport." "jvmMain · okhttp-jvm 5.5.0" "Public"
                cache    = component "webtools-http-cache" "The ResponseCache port with ResponseCache.Disabled and the CaffeineResponseCache default: one load per key at a time, failures never stored. Its own board: webtools-http-cache." "commonMain port · jvmMain · caffeine 3.3.1" "Public"
                budget   = component "webtools-http-budget" "The CallBudget port with WindowedCallBudget: permits under a provider's rate windows and lane shares, pauses on server signals, a hard window across a restart. Its own board: webtools-http-budget." "commonMain" "Public"
                fixtures = component "webtools-http-test-fixtures" "The published test-fixtures variant: VirtualClock, FakeHttpTransport, .http fixtures, the gated recorder, the loopback factories and the budget's two fixtures. Its own board: webtools-http-test-fixtures." "jvmTestFixtures · testFixtures(...) · test classpath only" "Public"
                facades  = component "webtools-http-facades" "The call facades: UpstreamClient calls an upstream's endpoints through the policy's pre-check, the cache and the budget inside the cache's loader; UrlFetcher fetches administrator-supplied URLs; the JVM owning factories build, size and close what they own. Its own board: webtools-http-facades." "commonMain · three jvmMain owning factories · LoopbackFetchers in the fixtures" "Public"
            }
            check = container "webtools-http-commoncheck" "Compiles webtools-http's commonMain for js with a no-op Log and Date.now(), so a JVM API in common code is a compile error. Unpublished; a part of the Gradle build." "Kotlin/JS klib · check only" "Internal"
        }

        consumer -> webtools.http.core "Parses URLs, builds requests, matches the failures"
        consumer -> webtools.http.policy "Builds Trusted or Untrusted policies"
        consumer -> webtools.http.okhttp "Builds OkHttpTransport(userAgent, policy), calls execute and close()"
        consumer -> webtools.http.cache "Builds CaffeineResponseCache { }, loads through getOrLoad"
        consumer -> webtools.http.budget "Builds WindowedCallBudget(windows) { }, acquires and settles permits"
        consumer -> webtools.http.fixtures "Tests its providers with testFixtures(...)"
        webtools.http.policy -> webtools.http.core "Judges Url hops; refuses with a RefusalReason"
        webtools.http.okhttp -> webtools.http.core "Implements HttpTransport; maps every outcome to a TransportFailure leaf"
        webtools.http.okhttp -> webtools.http.policy "Judges the request and every redirect target; carries its resolver and socket guards"
        webtools.http.okhttp -> web "Fetches: one request per hop, capped"
        webtools.http.cache -> webtools.http.core "Fails with Closed(cache); joins a loader's outcome with catchNonFatal; logs through the seam"
        webtools.http.budget -> webtools.http.core "Fails with BudgetExhausted or Closed(budget); reads the wall clock and an HttpResponse; logs through the seam"
        webtools.http.fixtures -> webtools.http.core "Implements HttpTransport and WallClock; reuses HttpSyntax"
        webtools.http.fixtures -> webtools.http.okhttp "Builds the real transport over loopback"
        webtools.http.fixtures -> webtools.http.policy "Relaxes loopback only, for tests"
        webtools.http.fixtures -> webtools.http.budget "Implements its store and observer"
        webtools.http.fixtures -> webtools.http.facades "Builds a UrlFetcher over loopback with LoopbackFetchers"
        consumer -> webtools.http.facades "Calls endpoints with UpstreamClient, fetches URLs with UrlFetcher"
        webtools.check -> webtools.http "Compiles its commonMain for js, through a relative srcDir"

        webtools.http.facades -> webtools.http.policy "Pre-checks every request with checkRequest before anything is spent"
        webtools.http.facades -> webtools.http.cache "Loads a GET through getOrLoad; builds and owns CaffeineResponseCache on the JVM"
        webtools.http.facades -> webtools.http.budget "Takes one permit per load inside the cache's loader; builds and owns WindowedCallBudget from windows"
        webtools.http.facades -> webtools.http.okhttp "Executes each request once; builds, sizes and owns OkHttpTransport on the JVM"
        webtools.http.facades -> webtools.http.core "Returns Result<Lookup<T>> or HttpResponse with the failure model's leaves"
    }

    views {
        systemContext webtools "Context" "WebTools, the consumer of webtools-http and the servers it reaches." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "webtools-http and its check-only js module." {
            include *
            autoLayout lr
        }
        component webtools.http "Components" "webtools-http as built at feat/issue-99-dokka-api-docs 2c634f4, one component per part, each with its own board." {
            include *
            autoLayout tb
        }
        dynamic webtools.http "Request" "A consumer builds a policy and a transport and sends a request, as built." {
            consumer -> webtools.http.core "Url.parse(text) · HttpRequest(HttpMethod.Get, url)"
            consumer -> webtools.http.policy "OutboundUrlPolicy.untrusted { }"
            consumer -> webtools.http.okhttp "OkHttpTransport(userAgent, policy), then execute(request)"
            webtools.http.okhttp -> webtools.http.policy "checkRequest before any socket; the guards on every answer and connect"
            webtools.http.okhttp -> web "one request per hop"
            webtools.http.okhttp -> webtools.http.core "an HttpResponse, or a TransportFailure inside the Result"
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
