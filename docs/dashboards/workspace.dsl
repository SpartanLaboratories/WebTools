// WebTools — C4 model as code (Structurizr DSL): the repository's systems and how they interact. Source of truth for docs/dashboards/index.html.
// As-is at WebTools feat/issue-69-feeds 25678cf (2026-11-07): webtools-udp 2.0.0a, webtools-scraping 1.0.0a, webtools-browser 1.0.0a, all Java 11 bytecode; webtools-http 1.0.0's common core (#62), its test-fixtures variant (#63), its outbound-URL policies (#64), its OkHttp transport (#65), its response cache (#66), its call budget (#67) and its call facades (#68), and webtools-feeds 1.0.0 (#69), new and unreleased, each with its check-only js module; the Gradle build (Gradle 9.7.1, Kotlin 2.2.0) with its included build-logic build (#61), the Java 11 floor (#47), the common-check plugin (#62) and the fixtures-publication check (#63). Code is authoritative on any conflict.
// The one element tagged Planned, webtools-common, is the registry's, with no plan yet. It is not built; it is drawn dashed.
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

            // ---- built: the Gradle build (registry slug build), unpublished ----
            gradleBuild = container "Gradle build" "Builds, tests and publishes every module: the wrapper, the settings, the root script and the included build-logic build with its convention plugins. Publishes nothing of its own." "Gradle 9.7.1 · Kotlin 2.2.0 · Java floor 11" "Internal" {
                settings = component "settings.gradle.kts" "Includes build-logic through pluginManagement, applies foojay-resolver-convention 0.8.0, includes :webtools-udp, :webtools-scraping, :webtools-browser, :webtools-http, :webtools-http-commoncheck, :webtools-feeds, :webtools-feeds-commoncheck." "Gradle Kotlin DSL" "Internal"
                root     = component "build.gradle.kts" "allprojects: group io.github.spartanlaboratories and the default version 1.0.0. No plugins, no subprojects block, no tasks of its own." "Gradle Kotlin DSL" "Internal"
                wrapper  = component "Gradle wrapper" "gradlew, gradlew.bat and gradle/wrapper pin Gradle 9.7.1; gradle.properties sets only kotlin.code.style." "Gradle 9.7.1" "Internal"
                buildLogic = component "build-logic" "Included build: webtools.published-library, webtools.kotlin-jvm-library, webtools.kotlin-multiplatform-library, webtools.kotlin-common-check, WebToolsBuild with the package-selected level tasks, and its own TestKit tests." "Gradle included build · unpublished" "Internal"
                floor      = component "Java floor" "WebToolsBuild.JAVA_FLOOR = 11 sets jvmTarget, -Xjdk-release and the Java compatibility in both library plugins; verifyJavaFloor, in every module's check, fails on a class above Java 11 bytecode or a variant declaring another JVM version." "build-logic · Kotlin" "Internal"
                httpCheck  = component "webtools-http-commoncheck" "Compiles webtools-http's commonMain for js() with no environment, so a JVM API in common code is a compile error." "Kotlin/JS compile only · unpublished" "Internal"
                feedsCheck = component "webtools-feeds-commoncheck" "Compiles webtools-feeds' commonMain for js() with no environment, with webtools-http-commoncheck's js output for the http types and its own js actuals." "Kotlin/JS compile only · unpublished" "Internal"
            }

            // ---- webtools-http: its common core (#62), its test fixtures (#63), its outbound-URL policy (#64), its OkHttp transport (#65), its response cache (#66), its call budget (#67) and its call facades (#68), all built ----
            http = container "webtools-http" "A suspend HTTP stack: its common core, its outbound-URL policies, its OkHttp transport, its response cache, its call budget, the call facades that compose them and its published test fixtures." "Kotlin Multiplatform (jvm) · 1.0.0 unreleased" {
                core      = component "Common core" "The opt-in marker, the HttpFailure model, Url, HttpHeaders, HttpRequest, HttpResponse, the HttpTransport and WallClock ports, the logging seam." "commonMain + jvmMain"
                fixtures  = component "Published test fixtures" "The webtools-http-test-fixtures variant: VirtualClock, the scripted FakeHttpTransport, Fixture and FixtureLoader for .http files, the gated, redacting FixtureRecorder and LoopbackTransports, the real transport over a loopback server, on a consumer's test classpath only." "jvmTestFixtures · capability webtools-http-test-fixtures" "Public"
                policy    = component "Outbound-URL policy" "Trusted and Untrusted policies with TransportSettings, CidrBlock and MixedAnswers; an internal JVM-free address classifier; internal resolver and socket guards, which the OkHttp transport applies to every hop." "commonMain + jvmMain" "Public"
                // built by #65
                transport = component "OkHttp transport" "OkHttpTransport(userAgent, policy): one request per hop and never a retry, manual redirects judged by the policy, one deadline across every hop, a capped decoded body, failure mapping; its client, guard and bridge internal." "jvmMain · okhttp-jvm 5.5.0" "Public"
                // built by #66
                cache     = component "Response cache" "The ResponseCache port, its values and ResponseCache.Disabled in common code, and the CaffeineResponseCache default: one load per key at a time, per-entry lifetimes, a weight bound, a per-call maxAge; failures never stored." "commonMain + jvmMain · caffeine 3.3.1" "Public"
                // built by #67
                budget    = component "Call budget" "The CallBudget port with Permit and withPermit, CallBudget.Unlimited and the supplied WindowedCallBudget: epoch-aligned windows with a guard, lane floors, first-come-first-served waits, pauses from server signals and a hard window saved write-ahead; the rate-signal vocabulary with RetryAfter, and the BudgetStore and BudgetObserver seams with their defaults." "commonMain" "Public"
                // built by #68
                facades   = component "Call facades" "UpstreamClient calls an upstream's endpoints, each an Endpoint, through the policy's pre-check, the cache, a permit inside the cache's loader and the transport; UrlFetcher fetches administrator-supplied URLs; the JVM owning factories build, size and close what they own; LoopbackFetchers in the fixtures." "commonMain + jvmMain" "Public"
            }
            // ---- webtools-feeds (#69), built ----
            feeds = container "webtools-feeds" "RSS and Atom behind the FeedParser port with RomeFeedParser, the hardened Rome 2.1.0 parser, as its default, and FeedFetcher, a stateless conditional GET over UrlFetcher." "Kotlin Multiplatform (jvm) · 1.0.0 unreleased"
            common = container "webtools-common" "Common code shared by the webtools modules: what is universal to the web, such as IP addresses." "internal to the repository · planned" "Planned"
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
        consumer -> webtools.http.policy "Builds Trusted or Untrusted policies (1.0.0, not released)"
        webtools.http.policy -> webtools.http.core "Judges Url hops; refuses with a RefusalReason"
        webtools.gradleBuild.httpCheck -> webtools.http.policy "Compiles its classifier for js"
        consumer -> webtools.http.transport "Builds OkHttpTransport(userAgent, policy), calls execute (1.0.0, not released)"
        webtools.http.transport -> webtools.http.policy "Checks every hop and address"
        webtools.http.transport -> webtools.http.core "Implements HttpTransport"
        webtools.http.fixtures -> webtools.http.transport "Builds it for loopback tests: LoopbackTransports"
        webtools.http.transport -> web "Fetches: one request per hop, capped"
        consumer -> webtools.http.cache "Builds CaffeineResponseCache { }, calls getOrLoad (1.0.0, not released)"
        webtools.http.cache -> webtools.http.core "Fails with Closed after close(); logs through the seam"
        webtools.gradleBuild.httpCheck -> webtools.http.cache "Compiles its port and values for js"
        consumer -> webtools.http.facades "Calls endpoints with UpstreamClient, fetches URLs with UrlFetcher (1.0.0, not released)"
        webtools.http.facades -> webtools.http.policy "Pre-checks every request before anything is spent"
        webtools.http.facades -> webtools.http.cache "Loads a GET through: cache first"
        webtools.http.facades -> webtools.http.budget "Takes a permit inside the cache's loader"
        webtools.http.facades -> webtools.http.transport "Executes each request once; owns an OkHttpTransport on the JVM"
        webtools.gradleBuild.httpCheck -> webtools.http.facades "Compiles its common code for js"
        webtools.gradleBuild.buildLogic -> webtools.feeds "Configures: webtools.kotlin-multiplatform-library, applied by id"
        webtools.gradleBuild.buildLogic -> webtools.gradleBuild.feedsCheck "Configures: webtools.kotlin-common-check, applied by id"
        webtools.gradleBuild.feedsCheck -> webtools.feeds "Compiles its commonMain for js"
        webtools.gradleBuild.feedsCheck -> webtools.gradleBuild.httpCheck "Depends on its js output for the http types"
        webtools.feeds -> webtools.http.facades "Fetches through UrlFetcher; webtools-http is an api dependency"
        consumer -> webtools.feeds "Fetches feeds with FeedFetcher, parses with RomeFeedParser (1.0.0, not released)"
        consumer -> webtools.http.budget "Builds WindowedCallBudget(windows) { }, acquires and settles permits (1.0.0, not released)"
        webtools.http.budget -> webtools.http.core "Fails with BudgetExhausted, or Closed after close(); reads the wall clock and an HttpResponse; logs through the seam"
        webtools.gradleBuild.httpCheck -> webtools.http.budget "Compiles it for js"
    }

    views {
        systemContext webtools "Context" "WebTools, who uses it and what it talks to." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The built modules, the Gradle build that configures them, and the planned system (dashed)." {
            include *
            autoLayout lr
        }
        component webtools.gradleBuild "Build" "The Gradle build as built at 25678cf, with build-logic, the Java floor and the two check-only js modules." {
            include *
            autoLayout lr
        }
        component webtools.http "Http" "webtools-http at 25678cf: its common core, URL policy, OkHttp transport, response cache, call budget, call facades and test fixtures, all built." {
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
