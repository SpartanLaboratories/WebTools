// webtools-http-test-fixtures — C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/webtools-http-test-fixtures/index.html.
// As-is at WebTools master 5f221ce (2026-11-05), webtools-http 1.0.0's published test-fixtures variant (new, unreleased): VirtualClock, the scripted FakeHttpTransport, Fixture and FixtureLoader, the gated, redacting FixtureRecorder, the loopback factories, and the call budget's two fixtures. Code is authoritative on any conflict.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer's tests" "A consumer's test source set that adds testImplementation(testFixtures(...)) and tests its providers with no network, or with the real transport over loopback."
        server = softwareSystem "Loopback server" "mockwebserver3's MockWebServer on 127.0.0.1, serving the same .http files the fake replays." "External"

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            fixtures = container "webtools-http-test-fixtures" "webtools-http's published test-fixtures variant, package com.spartanlabs.webtools.http.testing: a JVM-only compilation associated with main and published beside it, on a consumer's test classpath only." "Kotlin · jvmTestFixtures · capability webtools-http-test-fixtures · webtools-http 1.0.0 unreleased" {
                clock    = component "VirtualClock" "One virtual clock for a test: a time source, a wall clock, a dispatcher and a maintenance executor, all driven by one TestCoroutineScheduler; TestScope.virtualClock() builds one." "final class" "Public"
                fake     = component "FakeHttpTransport" "A scripted HttpTransport: stubs matched latest first, steps in order with the last repeating, virtual-time delays, a call log, the body cap and close(); with RequestMatch, Step, Unmatched, RecordedCall and UnmatchedRequestError." "final class" "Public"
                files    = component "Fixture · FixtureLoader" "Fixture holds a status, headers and a body, parsed from a .http file and written back; FixtureLoader reads one from the consumer's own classpath." "final classes" "Public"
                codec    = component "HttpMessageCodec" "The .http codec: a strict UTF-8 head, the body verbatim, each fault a FixtureError naming the origin and line, never a value." "internal object" "Internal"
                recorder = component "FixtureRecorder" "Records a live transport's successful responses as .http files, behind RecordingGate: redacted, scrubbed of supplied secrets, refused when a secret survives, written atomically." "final class · internal decorator" "Public"
                loopback = component "Loopback factories" "LoopbackTransports with MockWebServer.webtoolsUrl, Fixture.toMockResponse and MockWebServer.enqueue(fixture): the real OkHttpTransport over a loopback server with the policy's rules live." "object · extension functions" "Public"
                budgetFx = component "The call budget's fixtures" "RecordingBudgetObserver and FakeBudgetStore: the events in arrival order, and a scripted store with failures and delays in virtual time." "final classes" "Public"
            }
            core      = container "webtools-http-core" "HttpTransport, WallClock, the request and response values and HttpSyntax, reached through the association." "commonMain"
            okhttp    = container "webtools-http-transport" "OkHttpTransport, built over loopback through its internal constructor." "jvmMain · okhttp-jvm 5.5.0"
            policy    = container "webtools-http-url-policy" "The policy whose rules stay live over loopback, with its internal loopback relaxation." "commonMain · jvmMain"
            budget    = container "webtools-http-budget" "The call budget, whose store and observer seams the budget's fixtures implement." "commonMain"
            build     = container "Gradle build" "fixturesPublicationCheck, in webtools-http's check: the two variants, their dependencies, a signature beside every published file, resolution from three build shapes." "build-logic" "Internal"
        }

        consumer -> webtools.fixtures.fake "Scripts responses and reads the call log"
        consumer -> webtools.fixtures.clock "Drives every clock with advanceBy"
        consumer -> webtools.fixtures.files "Loads .http fixtures from its own classpath"
        consumer -> webtools.fixtures.recorder "Records fixtures from a live transport, behind the gate"
        consumer -> webtools.fixtures.loopback "Builds the real transport over a loopback server"
        consumer -> webtools.fixtures.budgetFx "Records the budget's events; scripts its store"
        webtools.fixtures.fake -> webtools.core "Implements HttpTransport; builds HttpResponses"
        webtools.fixtures.clock -> webtools.core "Implements WallClock"
        webtools.fixtures.fake -> webtools.fixtures.clock "Delays each step in virtual time"
        webtools.fixtures.fake -> webtools.fixtures.files "Replays a Fixture as a step"
        webtools.fixtures.files -> webtools.fixtures.codec "Parses and writes .http files with"
        webtools.fixtures.codec -> webtools.core "Reuses HttpSyntax's rules"
        webtools.fixtures.recorder -> webtools.fixtures.codec "Writes each response with"
        webtools.fixtures.recorder -> webtools.core "Decorates a live HttpTransport"
        webtools.fixtures.loopback -> webtools.okhttp "Builds the real transport with a loopback resolver"
        webtools.fixtures.loopback -> webtools.policy "Relaxes loopback only, every other rule live"
        webtools.fixtures.loopback -> webtools.fixtures.files "Serves a Fixture with toMockResponse"
        webtools.fixtures.loopback -> server "Enqueues responses; addresses it as an IP literal"
        webtools.okhttp -> server "Fetches over loopback"
        webtools.fixtures.budgetFx -> webtools.budget "Implements BudgetObserver and BudgetStore"
        webtools.build -> webtools.fixtures "Checks its publication"
    }

    views {
        systemContext webtools "Context" "WebTools, the consumer's tests that use the fixtures, and the loopback server." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The test fixtures and the parts of webtools-http they fake, drive or build." {
            include *
            autoLayout lr
        }
        component webtools.fixtures "Components" "The test fixtures as built at master 5f221ce." {
            include *
            autoLayout tb
        }
        dynamic webtools.fixtures "A test" "A consumer's test scripts the fake, advances the virtual clock and replays a fixture, as built." {
            consumer -> webtools.fixtures.clock "runTest { val clock = virtualClock() }"
            consumer -> webtools.fixtures.files "FixtureLoader().load(group/name.http)"
            consumer -> webtools.fixtures.fake "FakeHttpTransport(clock.timeSource).on(match, Step.fixture(fixture))"
            webtools.fixtures.fake -> webtools.fixtures.clock "the step's delay, virtual"
            webtools.fixtures.fake -> webtools.core "the HttpResponse, inside the Result"
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
