// webtools-http-commoncheck - an unpublished, check-only module. It compiles webtools-http's commonMain
// for a second target (js), so a JVM API in common code is a compile error: a module with only a jvm()
// target accepts java.* in commonMain. It publishes nothing and runs no tests; its jsMain holds a js
// actual for every expect declaration in that directory. ./gradlew build and check compile it, and no
// Node.js is needed.

plugins {
    id("webtools.kotlin-common-check")
}

// The gate marker of webtools-http. This module compiles the same sources, which implement the gated
// ports, so it opts in as webtools-http does: on commonMain and on jsMain. The jsMain line is the one the
// js compile reads (the commonMain line alone is inert in a single-target module); the commonMain line
// keeps the pair valid if the module ever gains a target. It is written here and not in the convention
// plugin: a module that cannot see the marker warns on every compile.
val gateMarker = "com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi"

kotlin {
    sourceSets {
        commonMain {
            // webtools-http's commonMain, as a path relative to this module (no project(...) access).
            kotlin.srcDir("../webtools-http/src/commonMain/kotlin")
            languageSettings.optIn(gateMarker)
            dependencies {
                // The same dependency as webtools-http's commonMain; keep the version in step.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        jsMain {
            languageSettings.optIn(gateMarker)
        }
    }
}
