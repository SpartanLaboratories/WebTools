// WebTools — C4 model as code (Structurizr DSL): the repository's systems and how they interact. Source of truth for docs/dashboards/index.html.
// As-is at WebTools master 4fcc6e0 (2026-10-25): webtools-udp 2.0.0a, webtools-scraping 1.0.0a, webtools-browser 1.0.0a, all Java 11 bytecode; webtools-http 1.0.0's common core (#62) and its test-fixtures variant (#63), new and unreleased, with its check-only js module; the Gradle build (Gradle 9.7.1, Kotlin 2.2.0) with its included build-logic build (#61), the Java 11 floor (#47), the common-check plugin (#62) and the fixtures-publication check (#63). Code is authoritative on any conflict.
// Every element tagged Planned comes from Issue #55 (docs/plans/55-http-client-stack/architecture.md, sections 6 and 11) and carries its sub-issue. None of it is built; it is drawn dashed.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer   = person "Consumer" "A Kotlin/JVM project that depends on one or more of the published WebTools artifacts."
        maintainer = person "Maintainer" "Builds, tests and publishes the modules with the Gradle wrapper; the repository has no CI."
        peer     = softwareSystem "UDP peer" "The other end of a webtools-udp session: another server or client process built on the library." "External"
        web      = softwareSystem "Web servers" "Any HTTP or HTTPS site a caller names by URL." "External"
        chrome   = softwareSystem "Chrome / Chromium" "The host's local browser install, driven headless through Selenium; Selenium Manager resolves the driver at runtime." "External"
        central  = softwareSystem "Maven Central" "Hosts io.github.spartanlaboratories artifacts: webtools-udp 2.0.0, webtools-scraping 1.0.0, webtools-browser 1.0.0." "External"
        bottools = softwareSystem "KotlinBottools" "SpartanLaboratories/KotlinBottools: named by the HTTP stack's architecture as the first consumer of webtools-http and webtools-feeds, through an internal adapter." "External"

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            // ---- built: the three published modules ----
            udp      = container "webtools-udp" "Multi-client UDP connection layer: a text handshake onto one common port, then framed datagrams over unreliable and reliable-ordered channels." "Kotlin/JVM library · 2.0.0a (unreleased; Central 2.0.0) · Java 11 bytecode"
            scraping = container "webtools-scraping" "Connector: open a URL and read it line by line, plus one-shot get, skrape and image download helpers." "Kotlin/JVM library · 1.0.0a (unreleased; Central 1.0.0) · Java 11 bytecode"
            browser  = container "webtools-browser" "WebViewer: renders a URL in headless Chrome and returns the screenshot." "Kotlin/JVM library · 1.0.0a (unreleased; Central 1.0.0) · Java 11 bytecode"

            // ---- built: the Gradle build (registry slug build), unpublished; its planned parts are dashed ----
            gradleBuild = container "Gradle build" "Builds, tests and publishes every module: the wrapper, the settings, the root script and the included build-logic build with its convention plugins. Publishes nothing of its own." "Gradle 9.7.1 · Kotlin 2.2.0 · Java floor 11" "Internal" {
                settings = component "settings.gradle.kts" "Includes build-logic through pluginManagement, applies foojay-resolver-convention 0.8.0, includes :webtools-udp, :webtools-scraping, :webtools-browser, :webtools-http, :webtools-http-commoncheck." "Gradle Kotlin DSL" "Internal"
                root     = component "build.gradle.kts" "allprojects: group io.github.spartanlaboratories and the default version 1.0.0. No plugins, no subprojects block, no tasks of its own." "Gradle Kotlin DSL" "Internal"
                wrapper  = component "Gradle wrapper" "gradlew, gradlew.bat and gradle/wrapper pin Gradle 9.7.1; gradle.properties sets only kotlin.code.style." "Gradle 9.7.1" "Internal"
                buildLogic = component "build-logic" "Included build: webtools.published-library, webtools.kotlin-jvm-library, webtools.kotlin-multiplatform-library, webtools.kotlin-common-check, WebToolsBuild with the package-selected level tasks, and its own TestKit tests." "Gradle included build · unpublished" "Internal"
                floor      = component "Java floor" "WebToolsBuild.JAVA_FLOOR = 11 sets jvmTarget, -Xjdk-release and the Java compatibility in both library plugins; verifyJavaFloor, in every module's check, fails on a class above Java 11 bytecode or a variant declaring another JVM version." "build-logic · Kotlin" "Internal"
                httpCheck  = component "webtools-http-commoncheck" "Compiles webtools-http's commonMain for js() with no environment, so a JVM API in common code is a compile error." "Kotlin/JS compile only · unpublished" "Internal"
                // planned by Issue #55: not built
                feedsCheck = component "webtools-feeds-commoncheck" "Unit 10: compiles webtools-feeds' commonMain for js." "Kotlin/JS compile only · unpublished" "Planned"
            }

            // ---- webtools-http: its common core (#62) and its test fixtures (#63) built, its other parts planned by Issue #55 ----
            http = container "webtools-http" "A suspend HTTP stack: its common core and its published test fixtures are built; outbound-URL policies, the transport, the cache, the budget and the facades are planned." "Kotlin Multiplatform (jvm) · 1.0.0 unreleased" {
                core      = component "Common core" "The opt-in marker, the HttpFailure model, Url, HttpHeaders, HttpRequest, HttpResponse, the HttpTransport and WallClock ports, the logging seam." "commonMain + jvmMain"
                fixtures  = component "Published test fixtures" "The webtools-http-test-fixtures variant: VirtualClock, the scripted FakeHttpTransport, Fixture and FixtureLoader for .http files, and the gated, redacting FixtureRecorder, on a consumer's test classpath only." "jvmTestFixtures · capability webtools-http-test-fixtures" "Public"
                // planned by Issue #55: not built
                policy    = component "Outbound-URL policy" "Trusted and Untrusted policies, TransportSettings, the common address classifier, GuardedDns and GuardedSocketFactory." "commonMain + jvmMain" "Planned"
                transport = component "OkHttp transport" "OkHttpTransport, one request per hop, manual redirects, capped body read, failure mapping." "jvmMain · okhttp-jvm 5.5.0" "Planned"
                cache     = component "Response cache" "ResponseCache port, ResponseCache.Disabled and the CaffeineResponseCache default; failures never stored." "commonMain + jvmMain · caffeine 3.3.1" "Planned"
                budget    = component "Call budget" "CallBudget port and WindowedCallBudget, rate signals, BudgetStore and BudgetObserver seams." "commonMain" "Planned"
                facades   = component "Call facades" "UpstreamClient and UrlFetcher, composing cache, then budget inside the loader, then transport." "commonMain + jvmMain" "Planned"
            }
            feeds = container "webtools-feeds" "Unit 10: RSS and Atom parsing behind a FeedParser port with a Rome 2.1.0 default, and a conditional-GET FeedFetcher over UrlFetcher." "Kotlin Multiplatform (jvm) · 1.0.0 planned" "Planned"
        }

        // ---- as built ----
        consumer -> webtools.udp "Subclasses the server, drives the client"
        consumer -> webtools.scraping "Connector open, next, get, skrape, download"
        consumer -> webtools.browser "WebViewer getPage, screenshot"
        consumer -> central "Resolves the published artifacts"
        webtools.udp -> peer "UDP datagrams: handshake, then framed transport"
        webtools.scraping -> web "HTTP via URLConnection, Unirest, skrapeit, jsoup"
        webtools.browser -> chrome "Drives headless Chrome through Selenium"
        chrome -> web "Resolves and fetches the page itself"
        maintainer -> webtools.gradleBuild.wrapper "Runs ./gradlew build, a level task, publishToMavenCentral"
        webtools.gradleBuild.wrapper -> webtools.gradleBuild.settings "Starts Gradle 9.7.1 on the settings"
        webtools.gradleBuild.settings -> webtools.gradleBuild.buildLogic "Includes through pluginManagement, first"
        webtools.gradleBuild.settings -> webtools.gradleBuild.root "Includes the three modules the root script sets group and version on"
        webtools.gradleBuild.root -> webtools.udp "Sets group; version 1.0.0, which the module overrides with 2.0.0a"
        webtools.gradleBuild.buildLogic -> webtools.udp "Configures: webtools.kotlin-jvm-library, applied by id"
        webtools.gradleBuild.root -> webtools.scraping "Sets group; version 1.0.0, which the module overrides with 1.0.0a"
        webtools.gradleBuild.buildLogic -> webtools.scraping "Configures: webtools.kotlin-jvm-library, applied by id"
        webtools.gradleBuild.root -> webtools.browser "Sets group; version 1.0.0, which the module overrides with 1.0.0a"
        webtools.gradleBuild.buildLogic -> webtools.browser "Configures: webtools.kotlin-jvm-library, applied by id"
        webtools.gradleBuild.buildLogic -> central "Publishes signed artifacts: vanniktech publishToMavenCentral, from webtools.published-library"
        webtools.gradleBuild.buildLogic -> webtools.gradleBuild.floor "Applies in both library plugins: JAVA_FLOOR and verifyJavaFloor in check"
        webtools.gradleBuild.buildLogic -> webtools.http "Configures: webtools.kotlin-multiplatform-library, applied by id"
        webtools.gradleBuild.buildLogic -> webtools.gradleBuild.httpCheck "Configures: webtools.kotlin-common-check, applied by id"
        webtools.gradleBuild.httpCheck -> webtools.http.core "Compiles its commonMain for js"
        consumer -> webtools.http.core "Builds requests and implements or calls HttpTransport (1.0.0, not released)"
        consumer -> webtools.http.fixtures "Tests its providers with testFixtures(...), no network (1.0.0, not released)"
        webtools.http.fixtures -> webtools.http.core "Fakes HttpTransport and WallClock; decorates a live transport to record"
        webtools.gradleBuild.buildLogic -> webtools.http.fixtures "Checks its publication: fixturesPublicationCheck, in webtools-http's check"

        // ---- planned by Issue #55 (dashed: one end is Planned) ----
        webtools.gradleBuild.buildLogic -> webtools.feeds "Would configure: multiplatform plugin"
        webtools.gradleBuild.feedsCheck -> webtools.feeds "Would compile commonMain for js"
        webtools.gradleBuild.feedsCheck -> webtools.gradleBuild.httpCheck "Would depend on its js output"
        webtools.feeds -> webtools.http.facades "Would fetch through UrlFetcher: api dependency"
        consumer -> webtools.http.facades "Would call UpstreamClient, UrlFetcher"
        consumer -> webtools.feeds "Would fetch feeds: FeedFetcher"
        bottools -> webtools.http.facades "Would call through an internal adapter"
        bottools -> webtools.feeds "Would fetch feeds through its adapter"
        webtools.http.facades -> webtools.http.cache "Would load through: cache first"
        webtools.http.facades -> webtools.http.budget "Would acquire a permit in the loader"
        webtools.http.facades -> webtools.http.transport "Would execute the request"
        webtools.http.facades -> webtools.http.policy "Would pre-check a fetched URL"
        webtools.http.transport -> webtools.http.policy "Would check every hop and address"
        webtools.http.transport -> webtools.http.core "Would implement HttpTransport"
        webtools.http.fixtures -> webtools.http.transport "Would build it for loopback tests"
        webtools.http.transport -> web "Would fetch: one request per hop, capped"
    }

    views {
        systemContext webtools "Context" "WebTools, who uses it and what it talks to. Dashed lines lead to planned elements." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The built modules, the Gradle build that configures them, and the planned systems (dashed)." {
            include *
            autoLayout lr
        }
        component webtools.gradleBuild "Build" "The Gradle build as built at 4fcc6e0, with build-logic, the Java floor and the common check, and its planned part (dashed): webtools-feeds' check module." {
            include *
            autoLayout lr
        }
        component webtools.http "Http" "webtools-http at 4fcc6e0: its common core and test fixtures built, its planned parts dashed." {
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
