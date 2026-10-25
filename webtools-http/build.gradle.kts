// webtools-http - the HTTP client stack: the request and response types, a suspend transport port, a
// typed failure model and the other ports and defaults of the stack. Kotlin Multiplatform: commonMain
// holds the contracts and the platform-free code, jvmMain the platform actuals and the OkHttp and
// Caffeine implementations. It publishes webtools-http and webtools-http-jvm (a Maven build depends on
// webtools-http-jvm).
//
// 1.0.0 is the version inherited from the root build, and it is UNRELEASED: webtools-http is new, and it
// stays unpublished until the whole stack (issue #55) is complete. It is not a release candidate while
// the units of that work land one by one. Remove this note when 1.0.0 is released.
//
// Everything shared (the jvm() target at the Java floor, the test dependencies, the level tasks,
// publishing, binary-compatibility-validator and verifyJavaFloor) comes from the convention plugin. This
// file adds explicitApi(), the dependencies, the module-wide opt-in, the coordinates and the POM name and
// description. The ABI dump api/webtools-http.api is committed (:webtools-http:apiDump); apiCheck is part
// of check.

plugins {
    id("webtools.kotlin-multiplatform-library")
}

// The marker that gates the module's own ports (HttpTransport, WallClock, HttpFailure, ...). The module's
// main code implements those ports, so it opts in; its tests do not, and stay a consumer.
val gateMarker = "com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi"

kotlin {
    explicitApi()

    sourceSets {
        // The opt-in is a language setting of the MAIN source sets only. Do not replace it with
        // kotlin { compilerOptions { optIn.add(...) } }, with the same option on the jvm target, or with
        // sourceSets.all { languageSettings.optIn(...) }: each of those also reaches commonTest and
        // jvmTest, which would then compile as a non-consumer and could implement a port or subclass a
        // failure group without the @OptIn a consumer needs. In a single-target module only the leaf line
        // (jvmMain) reaches the compiler; the commonMain line keeps the pair valid if a target is added
        // (a shared source set must use every opt-in its leaf uses).
        commonMain {
            languageSettings.optIn(gateMarker)
            dependencies {
                // The runtime of the suspend API, and CoroutineDispatcher appears in the stack's
                // configuration: the one api dependency. The others are implementation details.
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        jvmMain {
            languageSettings.optIn(gateMarker)
            dependencies {
                // OkHttp: the transport. Caffeine: the response cache. slf4j: the logging seam.
                implementation("com.squareup.okhttp3:okhttp-jvm:5.5.0")
                implementation("com.github.ben-manes.caffeine:caffeine:3.3.1")
                implementation("org.slf4j:slf4j-api:2.0.13")
            }
        }
    }
}

mavenPublishing {
    coordinates(group.toString(), "webtools-http", version.toString())
    pom {
        name.set("WebTools HTTP")
        description.set("A suspend HTTP client stack: request and response types, a transport port, a typed failure model, an outbound-URL policy, a response cache and a call budget.")
    }
}
