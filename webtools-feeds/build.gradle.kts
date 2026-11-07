// webtools-feeds - RSS and Atom behind a hardened parser port: the feed model, a fetcher that does a stateless
// conditional GET over webtools-http's UrlFetcher, and RomeFeedParser, the supplied parser over Rome. Kotlin
// Multiplatform: commonMain holds the port, the model, the fetcher and the DOCTYPE prolog scan, jvmMain the Rome
// parser and the owning factories. It publishes webtools-feeds and webtools-feeds-jvm (a Maven build depends on
// webtools-feeds-jvm).
//
// 1.0.0 is the version inherited from the root build, and it is UNRELEASED: webtools-feeds is new, and it stays
// unpublished until the whole stack (issue #55) is complete. It is not a release candidate while the units of that
// work land one by one. Remove this note when 1.0.0 is released.
//
// Everything shared (the jvm() target at the Java floor, the test dependencies, the level tasks, publishing,
// binary-compatibility-validator and verifyJavaFloor) comes from the convention plugin. This file adds
// explicitApi(), the dependencies, the module-wide opt-in, the coordinates and the POM name and description. The
// ABI dump api/webtools-feeds.api is committed (:webtools-feeds:apiDump); apiCheck is part of check.

plugins {
    id("webtools.kotlin-multiplatform-library")
}

// The marker that gates webtools-http's ports and failure groups. This module's main code implements FeedParser
// (itself gated), extends HttpFailure and subclasses nothing else gated, so it opts in; its tests do not, and stay
// a consumer.
val gateMarker = "com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi"

kotlin {
    explicitApi()

    sourceSets {
        // The opt-in is a language setting of the MAIN source sets only (see webtools-http/build.gradle.kts for why
        // a compilerOptions or sourceSets.all form would also reach the tests).
        commonMain {
            languageSettings.optIn(gateMarker)
            dependencies {
                // The public API names UrlFetcher, Url, HttpFailure, the marker and CoroutineDispatcher;
                // kotlinx-coroutines-core arrives through it as an api dependency.
                api(project(":webtools-http"))
            }
        }
        jvmMain {
            languageSettings.optIn(gateMarker)
            dependencies {
                // Rome 2.1.0 brings rome-utils 2.1.0 and jdom2 2.0.6.1. No Rome type is part of a public signature.
                implementation("com.rometools:rome:2.1.0")
                implementation("org.slf4j:slf4j-api:2.0.13")
            }
        }
    }
}

dependencies {
    // Fakes, a virtual clock and the loopback fetchers of webtools-http, for this module's own tests only. The
    // source-set DSL has no testFixtures(), so the configuration is named.
    "jvmTestImplementation"(testFixtures(project(":webtools-http")))
}

mavenPublishing {
    coordinates(group.toString(), "webtools-feeds", version.toString())
    pom {
        name.set("WebTools Feeds")
        description.set("RSS and Atom feeds behind a hardened parser port, with Rome as the supplied parser, and a stateless conditional-GET fetcher over webtools-http.")
    }
}
