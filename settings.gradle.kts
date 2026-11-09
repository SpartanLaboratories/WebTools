pluginManagement {
    includeBuild("build-logic")
    // The versions of the plugins the root build script applies by id (it applies the two Kotlin plugins
    // with `apply false` and Dokka). Kotlin and Dokka must equal the marker dependencies in
    // build-logic/build.gradle.kts, so the root and the convention plugins load one plugin version.
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.2.0"
        id("org.jetbrains.kotlin.multiplatform") version "2.2.0"
        id("org.jetbrains.dokka") version "2.3.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "WebTools"

include(":webtools-udp", ":webtools-scraping", ":webtools-browser", ":webtools-http", ":webtools-http-commoncheck", ":webtools-feeds", ":webtools-feeds-commoncheck")
