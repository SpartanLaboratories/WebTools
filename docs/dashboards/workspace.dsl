// WebTools — C4 model as code (Structurizr DSL): the repository's systems and how they interact. Source of truth for docs/dashboards/index.html.
// As-is at WebTools feat/issue-55-http-client-stack 96c30d6 (2026-10-21): webtools-udp 2.0.0a, webtools-scraping 1.0.0, webtools-browser 1.0.0. Code is authoritative on any conflict.
// Internal is drawn solid here (it marks the built, unpublished root build); only Planned is dashed.
// Every element tagged Planned comes from Issue #55 (docs/plans/55-http-client-stack/architecture.md, sections 6 and 11). None of it is built; it is drawn dashed.
workspace "WebTools" "Kotlin/JVM internet I/O helpers, published as three independent single-concern artifacts." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer" "A Kotlin/JVM project that depends on one or more of the published WebTools artifacts."
        peer     = softwareSystem "UDP peer" "The other end of a webtools-udp session: another server or client process built on the library." "External"
        web      = softwareSystem "Web servers" "Any HTTP or HTTPS site a caller names by URL." "External"
        chrome   = softwareSystem "Chrome / Chromium" "The host's local browser install, driven headless through Selenium; Selenium Manager resolves the driver at runtime." "External"
        bottools = softwareSystem "KotlinBottools" "SpartanLaboratories/KotlinBottools: named by Issue #55 as the first consumer of webtools-http and webtools-feeds, through an internal adapter." "External"

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            // ---- built: the three published modules ----
            udp      = container "webtools-udp" "Multi-client UDP connection layer: a text handshake onto one common port, then framed datagrams over unreliable and reliable-ordered channels." "Kotlin/JVM library · 2.0.0a (unreleased; Central 2.0.0)"
            scraping = container "webtools-scraping" "Connector: open a URL and read it line by line, plus one-shot get, skrape and image download helpers." "Kotlin/JVM library · 1.0.0"
            browser  = container "webtools-browser" "WebViewer: renders a URL in headless Chrome and returns the screenshot." "Kotlin/JVM library · 1.0.0"

            // ---- built: unpublished build configuration ----
            rootBuild = container "Root build" "build.gradle.kts subprojects block: applies Kotlin/JVM and vanniktech publishing, adds slf4j-api, registers the seven level test tasks by JUnit tag." "Gradle 9.7.1 · Kotlin 2.2.0" "Internal"

            // ---- planned by Issue #55: nothing below is built ----
            buildLogic = container "build-logic" "Issue #55 units 1 and 2 (unit 2 is #47): included build with convention plugins, WebToolsBuild.JAVA_FLOOR = 11 and verifyJavaFloor, package-selected level tasks." "Gradle included build · unpublished" "Planned"
            http = container "webtools-http" "Issue #55 units 3 to 9: suspend HTTP stack with outbound-URL policies, response cache, call budget, call facades and published test fixtures." "Kotlin Multiplatform (jvm) · 1.0.0 planned" "Planned" {
                core      = component "Common core" "Unit 3: opt-in marker, HttpFailure model, Url, HttpHeaders, HttpRequest, HttpResponse, the HttpTransport and WallClock ports, the logging seam." "commonMain" "Planned"
                policy    = component "Outbound-URL policy" "Unit 5: Trusted and Untrusted policies, TransportSettings, the common address classifier, GuardedDns and GuardedSocketFactory." "commonMain + jvmMain" "Planned"
                transport = component "OkHttp transport" "Unit 6: OkHttpTransport, one request per hop, manual redirects, capped body read, failure mapping." "jvmMain · okhttp-jvm 5.5.0" "Planned"
                cache     = component "Response cache" "Unit 7: ResponseCache port, ResponseCache.Disabled and the CaffeineResponseCache default; failures never stored." "commonMain + jvmMain · caffeine 3.3.1" "Planned"
                budget    = component "Call budget" "Unit 8: CallBudget port and WindowedCallBudget, rate signals, BudgetStore and BudgetObserver seams." "commonMain" "Planned"
                facades   = component "Call facades" "Unit 9: UpstreamClient and UrlFetcher, composing cache, then budget inside the loader, then transport." "commonMain + jvmMain" "Planned"
                fixtures  = component "Published test fixtures" "Unit 4: test-fixtures variant with VirtualClock, FakeHttpTransport, .http fixtures, FixtureRecorder and LoopbackTransports." "jvmTestFixtures · capability webtools-http-test-fixtures" "Planned"
            }
            httpCheck  = container "webtools-http-commoncheck" "Issue #55 unit 3: compiles webtools-http's commonMain for js so a JVM API in common code is a compile error." "Kotlin/JS compile only · unpublished" "Planned"
            feeds      = container "webtools-feeds" "Issue #55 unit 10: RSS and Atom parsing behind a FeedParser port with a Rome 2.1.0 default, and a conditional-GET FeedFetcher over UrlFetcher." "Kotlin Multiplatform (jvm) · 1.0.0 planned" "Planned"
            feedsCheck = container "webtools-feeds-commoncheck" "Issue #55 unit 10: compiles webtools-feeds' commonMain for js." "Kotlin/JS compile only · unpublished" "Planned"
        }

        // ---- as built ----
        consumer -> webtools.udp "Subclasses the server, drives the client"
        consumer -> webtools.scraping "Connector open, next, get, skrape, download"
        consumer -> webtools.browser "WebViewer getPage, screenshot"
        webtools.udp -> peer "UDP datagrams: handshake, then framed transport"
        webtools.scraping -> web "HTTP via URLConnection, Unirest, skrapeit, jsoup"
        webtools.browser -> chrome "Drives headless Chrome through Selenium"
        chrome -> web "Resolves and fetches the page itself"
        webtools.rootBuild -> webtools.udp "Configures: subprojects block"
        webtools.rootBuild -> webtools.scraping "Configures: subprojects block"
        webtools.rootBuild -> webtools.browser "Configures: subprojects block"

        // ---- planned by Issue #55 (dashed: one end is Planned) ----
        webtools.buildLogic -> webtools.rootBuild "Would replace: every module, including the three built ones, applies a convention plugin and the Java 11 floor"
        webtools.buildLogic -> webtools.http "Would configure: multiplatform plugin; the common-check plugin also configures both check modules"
        webtools.buildLogic -> webtools.feeds "Would configure: multiplatform plugin"
        webtools.httpCheck -> webtools.http.core "Would compile commonMain for js"
        webtools.feedsCheck -> webtools.feeds "Would compile commonMain for js"
        webtools.feeds -> webtools.http.facades "Would fetch through UrlFetcher: api dependency"
        consumer -> webtools.http.facades "Would call UpstreamClient, UrlFetcher"
        consumer -> webtools.feeds "Would fetch feeds: FeedFetcher"
        bottools -> webtools.http.facades "Would call through an internal adapter"
        bottools -> webtools.feeds "Would fetch feeds through its adapter"
        bottools -> webtools.http.fixtures "Would test with testFixtures(...)"
        webtools.http.facades -> webtools.http.cache "Would load through: cache first"
        webtools.http.facades -> webtools.http.budget "Would acquire a permit in the loader"
        webtools.http.facades -> webtools.http.transport "Would execute the request"
        webtools.http.facades -> webtools.http.policy "Would pre-check a fetched URL"
        webtools.http.transport -> webtools.http.policy "Would check every hop and address"
        webtools.http.transport -> webtools.http.core "Would implement HttpTransport"
        webtools.http.fixtures -> webtools.http.core "Would fake HttpTransport"
        webtools.http.fixtures -> webtools.http.transport "Would build it for loopback tests"
        webtools.http.transport -> web "Would fetch: one request per hop, capped"
    }

    views {
        systemContext webtools "Context" "WebTools, who uses it and what it talks to. Dashed lines lead to planned elements." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The three built modules, the root build that configures them, and the systems Issue #55 plans (dashed)." {
            include *
            autoLayout lr
        }
        component webtools.http "HttpPlanned" "webtools-http as Issue #55 plans it. Every element is planned; nothing here is built." {
            include *
            autoLayout lr
        }
        dynamic webtools "HttpToday" "How the repository reaches the web today, as built: Connector.get and WebViewer.screenshot." {
            consumer -> webtools.scraping "Connector.get(url)"
            webtools.scraping -> web "Unirest GET; Unirest.setTimeouts(0, 0) at init"
            consumer -> webtools.browser "WebViewer.screenshot(url)"
            webtools.browser -> chrome "Headless Chrome via Selenium, driver.get(url)"
            chrome -> web "Chrome resolves and fetches the page"
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
