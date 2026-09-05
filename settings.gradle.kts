plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "WebTools"

include(":webtools-udp", ":webtools-scraping", ":webtools-browser")
