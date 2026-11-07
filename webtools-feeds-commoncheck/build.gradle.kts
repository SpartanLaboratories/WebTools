// webtools-feeds-commoncheck - an unpublished, check-only module. It compiles webtools-feeds' commonMain for a second
// target (js), so a JVM API in common code is a compile error; it publishes nothing and runs no tests; its jsMain
// holds a js actual for every expect declaration in that directory. ./gradlew build and check compile it; no Node.js
// is needed.

plugins {
    id("webtools.kotlin-common-check")
}

val gateMarker = "com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi"

kotlin {
    sourceSets {
        commonMain {
            // webtools-feeds' commonMain, as a path relative to this module (no project(...) access).
            kotlin.srcDir("../webtools-feeds/src/commonMain/kotlin")
            languageSettings.optIn(gateMarker)
            dependencies {
                // The webtools-http types feeds' common code uses: there is no published js artifact, so the check
                // module of webtools-http supplies its js output.
                implementation(project(":webtools-http-commoncheck"))
                // The same dependency as webtools-http's commonMain declares as api; the http check module declares it
                // as implementation, so it is not transitive here. Keep the version in step.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        jsMain {
            languageSettings.optIn(gateMarker)
        }
    }
}
