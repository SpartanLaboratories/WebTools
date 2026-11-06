// webtools-http-core — C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/webtools-http-core/index.html.
// As-is at WebTools master 5f221ce (2026-11-05), the common core of webtools-http 1.0.0 (new, unreleased): the opt-in marker, the failure model, the request and response values, Url with its internal parsing pipeline, the transport and wall-clock ports, the DSL marker, the internal logging seam and helpers, and their two JVM actuals. Code is authoritative on any conflict.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer" "A Kotlin service that parses URLs, builds requests, calls an HttpTransport or implements one with the opt-in, reads the wall clock and matches the failures."

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            core = container "webtools-http-core" "The common core of webtools-http: the vocabulary every other part of the stack speaks. All of it in commonMain but two JVM actuals; compiled for js by the check module." "Kotlin Multiplatform · commonMain · webtools-http 1.0.0 unreleased" {
                // ---- the seams ----
                marker    = component "ExperimentalForInheritanceWebToolsApi" "The one opt-in marker: @RequiresOptIn(level = ERROR), applied only through @SubclassOptInRequired on the twelve gated types and @OptIn in an implementer; never removed." "annotation class" "Public"
                transport = component "HttpTransport" "suspend execute(request): Result<HttpResponse> and an idempotent close(), under an eight-point contract. Implementing it needs the opt-in." "interface · AutoCloseable · gated" "Public"
                clock     = component "WallClock" "nowEpochMillis(), which may step backwards; the nested, final WallClock.System reads the platform clock and needs no opt-in." "interface · gated" "Public"
                dsl       = component "WebToolsDsl" "The DSL marker on the eleven builders a consumer's lambda receives; a nested block reaches an outer builder only through its label." "annotation class · @DslMarker" "Public"
                // ---- the failure model and the values ----
                failures  = component "HttpFailure" "The failure model: the groups TransportFailure (eleven final leaves) and UpstreamFailure (RateLimited, UpstreamError), and the direct leaves Closed, BudgetExhausted and DecodeFailure. Subclassing a group needs the opt-in." "abstract classes · gated · final leaves" "Public"
                reasons   = component "RefusalReason · ExhaustedReason" "Open constants classes, nine and four constants, equal by name; may gain constants in a feature release." "final classes" "Public"
                values    = component "Request and response values" "HttpMethod, HttpHeaders, RequestBody, HttpRequest, HttpResponse and RedirectHop: immutable, built by factories over internal constructors; bytes copied in and out." "final classes · factories" "Public"
                url       = component "Url" "parse(text): Result<Url>, canonical as OkHttp 5.5.0's HttpUrl for ASCII hosts and only ever stricter; toString() is the full URL, redacted() the form for logs." "final class" "Public"
                // ---- internals ----
                pipeline  = component "URL pipeline" "UrlParsing, UrlHost, UrlEncoding and UrlProblem: trim, scan, scheme, authority, port, host, then path and query; the internal path and query encoders." "internal functions" "Internal"
                syntax    = component "HttpSyntax" "The shared predicates: the token rule, the value rules, the media type, the received-value sanitiser." "internal object" "Internal"
                log       = component "Log seam" "Log with debug, info and warn, through expect fun logger(name); the transport, the resolver guard, the cache and the budget log through it." "internal interface · expect" "Internal"
                helpers   = component "Helpers" "Result.flatMap, catchNonFatal and expect fun epochMillisNow()." "internal functions · expect" "Internal"
                actuals   = component "JVM actuals" "Log.jvm.kt: logger(name) over slf4j; EpochMillis.jvm.kt: System.currentTimeMillis()." "internal · jvmMain · slf4j-api 2.0.13" "Internal"
            }
            policy    = container "webtools-http-url-policy" "The outbound-URL policies and their internal classifier and guards: judges Url hops and refuses with a RefusalReason." "commonMain · jvmMain"
            okhttp    = container "webtools-http-transport" "OkHttpTransport, the supplied HttpTransport: maps every outcome to a TransportFailure leaf and builds the HttpResponse." "jvmMain · okhttp-jvm 5.5.0"
            cache     = container "webtools-http-cache" "The response cache: fails with Closed after close(), runs its loaders through catchNonFatal, logs through the seam." "commonMain · jvmMain"
            budget    = container "webtools-http-budget" "The call budget: fails with BudgetExhausted and an ExhaustedReason, reads the wall clock and an HttpResponse, logs through the seam." "commonMain"
            fixtures  = container "webtools-http-test-fixtures" "The published test fixtures: FakeHttpTransport and the recorder implement HttpTransport, VirtualClock implements WallClock, and the .http codec reuses HttpSyntax." "jvmTestFixtures · test classpath only"
            check     = container "webtools-http-commoncheck" "Compiles webtools-http's commonMain for js with a no-op Log and Date.now(), so a JVM API in common code is a compile error. Unpublished; a part of the Gradle build." "Kotlin/JS klib · check only" "Internal"
        }

        consumer -> webtools.core.url "Parses text with Url.parse"
        consumer -> webtools.core.values "Builds requests with the factories"
        consumer -> webtools.core.transport "Calls execute(request) and close(), or implements it with the opt-in"
        consumer -> webtools.core.clock "Reads WallClock.System, or supplies its own"
        consumer -> webtools.core.failures "Matches the failures, keeping an else"
        webtools.core.marker -> webtools.core.transport "Gates implementing it"
        webtools.core.marker -> webtools.core.clock "Gates implementing it"
        webtools.core.marker -> webtools.core.failures "Gates subclassing the groups"
        webtools.core.dsl -> webtools.core.values "Marks the header, request and response builders"
        webtools.core.values -> webtools.core.url "Carry"
        webtools.core.values -> webtools.core.syntax "Validate names, values and media types with"
        webtools.core.url -> webtools.core.pipeline "Parses and prints through"
        webtools.core.pipeline -> webtools.core.helpers "Joins its steps with flatMap"
        webtools.core.transport -> webtools.core.values "Returns an HttpResponse for an HttpRequest"
        webtools.core.transport -> webtools.core.failures "Fails with a TransportFailure, or Closed after close()"
        webtools.core.failures -> webtools.core.reasons "Carry the refusal and exhaustion reasons"
        webtools.core.clock -> webtools.core.helpers "WallClock.System reads epochMillisNow()"
        webtools.core.log -> webtools.core.actuals "logger(name) is slf4j on the JVM"
        webtools.core.helpers -> webtools.core.actuals "epochMillisNow() is System.currentTimeMillis() on the JVM"

        webtools.policy -> webtools.core.url "Reads the canonical host, port and scheme of"
        webtools.policy -> webtools.core.reasons "Refuses with a RefusalReason"
        webtools.okhttp -> webtools.core.transport "Implements"
        webtools.okhttp -> webtools.core.failures "Maps every outcome to a leaf"
        webtools.okhttp -> webtools.core.log "Logs through"
        webtools.cache -> webtools.core.failures "Fails with Closed(cache) after close()"
        webtools.cache -> webtools.core.helpers "Joins a loader's outcome with catchNonFatal and flatMap"
        webtools.budget -> webtools.core.failures "Fails with BudgetExhausted, or Closed(budget) after close()"
        webtools.budget -> webtools.core.clock "Reads the wall clock beside its time source"
        webtools.fixtures -> webtools.core.transport "Implements it with FakeHttpTransport and the recorder's decorator"
        webtools.fixtures -> webtools.core.clock "Implements it with VirtualClock's wall clock"
        webtools.check -> webtools.core "Compiles its commonMain for js, through a relative srcDir"
    }

    views {
        systemContext webtools "Context" "WebTools and the consumer of the common core." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The common core and the parts of webtools-http that build on it, with the check-only js module." {
            include *
            autoLayout lr
        }
        component webtools.core "Components" "The common core as built at master 5f221ce." {
            include *
            autoLayout tb
        }
        dynamic webtools.core "Request" "A consumer parses a URL, builds a request and sends it through any HttpTransport, as built." {
            consumer -> webtools.core.url "Url.parse(text): Result<Url>"
            webtools.core.url -> webtools.core.pipeline "trim, scan, scheme, authority, port, host, path and query"
            consumer -> webtools.core.values "HttpRequest(HttpMethod.Get, url) { headers = HttpHeaders { … } }"
            consumer -> webtools.core.transport "execute(request)"
            webtools.core.transport -> webtools.core.values "an HttpResponse for any status in 100..599"
            webtools.core.transport -> webtools.core.failures "or a TransportFailure, inside the Result"
            consumer -> webtools.core.failures "when (failure) { … else -> … }"
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
