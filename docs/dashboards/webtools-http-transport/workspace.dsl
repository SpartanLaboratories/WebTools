// webtools-http-transport — C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/webtools-http-transport/index.html.
// As-is at WebTools master 5f221ce (2026-11-05), the OkHttp transport inside webtools-http 1.0.0 (new, unreleased): OkHttpTransport and its factory, its internal client, exchange guard and hop pipeline, and the common list of controlled headers. Code is authoritative on any conflict.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer" "A Kotlin service that builds OkHttpTransport(userAgent, policy), sends requests through execute and closes it."
        web = softwareSystem "Servers" "The HTTP servers a request and its redirects reach, through the system resolver." "External"

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            okhttp = container "webtools-http-transport" "The supplied HttpTransport under an outbound-URL policy: one request per hop and never a retry, manual redirects judged hop by hop, one deadline across them, a capped decoded body read where cancellation can reach it, and every outcome mapped to the failure model." "Kotlin · jvmMain · okhttp-jvm 5.5.0 · webtools-http 1.0.0 unreleased" {
                transport  = component "OkHttpTransport" "Final, built by the factory OkHttpTransport(userAgent, policy); usable without the opt-in. execute checks the closed flag, the controlled headers and the policy before any socket, then follows the hops under one deadline; close() cancels every call, shuts the dispatcher down and evicts the idle connections." "final class · factory" "Public"
                controlled = component "TransportControlledHeaders" "The twelve request headers the transport refuses from a caller, JVM-free, so the check module compiles it for js and the planned facades can refuse the same names." "internal · commonMain" "Internal"
                client     = component "TransportClient" "One OkHttpClient per transport with its own daemon dispatcher, connection pool and timeouts; redirects, retries, authentication, cookies and the cache switched off." "internal · jvmMain" "Internal"
                guard      = component "ExchangeGuard" "The one network interceptor: a second exchange of one hop fails as RetryRefused, and a 407, 408, 421 or 503 is hidden from OkHttp's follow-up logic and delivered once." "internal object · jvmMain" "Internal"
                bridge     = component "CallBridge · CallDeadline" "awaitHop suspends on one enqueued Call and resumes exactly once with a value; one deadline across the hops." "internal · jvmMain" "Internal"
                hop        = component "Hop request and response" "HopRequest, HopResponse and ReceivedResponse: the OkHttp request built from the Url's own text; the status, the redirect decision, the headers and the body settled inside onResponse." "internal · jvmMain" "Internal"
                redirect   = component "RedirectDecision" "Only GET and HEAD follow, only 301, 302, 303, 307 and 308 with one distinct Location; then the cap; then the policy admits the target. Sensitive headers dropped for good once the origin changes." "internal functions · jvmMain" "Internal"
                body       = component "CappedBody" "The decoded body read when it holds at most maxResponseBytes, asking for one byte more than the cap." "internal function · jvmMain" "Internal"
                mapping    = component "FailureMapping · ThrowableSearch" "Any throwable mapped to one TransportFailure leaf, first match wins; a cause kept only where its text holds no address; the bounded walk shared with the policy's refusal markers." "internal · jvmMain" "Internal"
            }
            core     = container "webtools-http-core" "What the transport uses from the common core: HttpTransport, HttpRequest and HttpResponse, Url, the failure model, HttpSyntax and the Log seam." "commonMain"
            policy   = container "webtools-http-url-policy" "The outbound-URL policy the transport enforces: its classifier and its resolver and socket guards." "commonMain · jvmMain"
            fixtures = container "webtools-http-test-fixtures" "LoopbackTransports: the real transport over a loopback server, for a consumer's tests." "jvmTestFixtures · test classpath only"
        }

        consumer -> webtools.okhttp.transport "Builds it with OkHttpTransport(userAgent, policy), then calls execute(request) and close()"
        webtools.okhttp.transport -> webtools.core "Implements HttpTransport; logs through the Log seam, never a header, a body, a query string or an address"
        webtools.okhttp.transport -> webtools.okhttp.controlled "Refuses a request carrying one, before anything is sent"
        webtools.okhttp.transport -> webtools.policy "Judges the request before any socket"
        webtools.okhttp.transport -> webtools.okhttp.client "Builds one client per transport"
        webtools.okhttp.client -> webtools.okhttp.guard "Installs as its one network interceptor"
        webtools.okhttp.client -> webtools.policy "Carries its resolver, socket factory and proxy"
        webtools.okhttp.transport -> webtools.okhttp.bridge "Runs each hop through, under one deadline"
        webtools.okhttp.bridge -> webtools.okhttp.hop "Builds the hop and settles its response"
        webtools.okhttp.hop -> webtools.okhttp.redirect "Decides a 3xx"
        webtools.okhttp.redirect -> webtools.policy "Admits the resolved target"
        webtools.okhttp.hop -> webtools.okhttp.body "Reads the capped body"
        webtools.okhttp.hop -> webtools.core "Builds the HttpResponse; sanitises received values with HttpSyntax"
        webtools.okhttp.transport -> webtools.okhttp.mapping "Maps and reports every failure"
        webtools.okhttp.mapping -> webtools.core "Maps to one TransportFailure leaf, a policy refusal to RefusedByPolicy"
        webtools.okhttp.mapping -> webtools.policy "Finds a refusal marker"
        webtools.okhttp.client -> web "Resolves, connects and exchanges, one request per hop"
        webtools.fixtures -> webtools.okhttp.transport "Builds it over loopback with the internal constructor"
    }

    views {
        systemContext webtools "Context" "WebTools, the consumer of the transport and the servers it reaches." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The OkHttp transport and the parts of webtools-http it uses and is used by." {
            include *
            autoLayout lr
        }
        component webtools.okhttp "Components" "The OkHttp transport as built at master 5f221ce." {
            include *
            autoLayout tb
        }
        dynamic webtools.okhttp "Request" "A consumer sends a request through OkHttpTransport and a server answers it, as built." {
            consumer -> webtools.okhttp.transport "execute(request)"
            webtools.okhttp.transport -> webtools.okhttp.controlled "a header it controls? InvalidRequest"
            webtools.okhttp.transport -> webtools.policy "checkRequest: before any socket"
            webtools.okhttp.transport -> webtools.okhttp.bridge "one enqueued Call per hop, under the deadline"
            webtools.okhttp.client -> web "the request, once"
            webtools.okhttp.bridge -> webtools.okhttp.hop "settleResponse inside onResponse"
            webtools.okhttp.hop -> webtools.okhttp.redirect "a 3xx: follow, deliver or refuse"
            webtools.okhttp.hop -> webtools.okhttp.body "the capped decoded body"
            webtools.okhttp.hop -> webtools.core "HttpResponse(status, url) { … }"
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
