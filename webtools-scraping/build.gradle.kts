// webtools-scraping - Connector: open/read a URL line by line, plus one-shot
// get / skrape / image download helpers.

plugins {
    id("webtools.kotlin-jvm-library")
}

dependencies {
    // Logging: a library depends only on the slf4j API, never on a concrete backend.
    api("org.slf4j:slf4j-api:2.0.13")
    implementation("it.skrape:skrapeit:1.1.5")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.mashape.unirest:unirest-java:1.4.9")
}

// Explicit per-module version, overriding the root allprojects { version = "1.0.0" }.
// 1.0.0a: the bugfix after 1.0.0 - the Java 11 floor (Issue #47). The class files are Java 11
// bytecode (major 55) built against the Java 11 API, and the Gradle Module Metadata declares
// org.gradle.jvm.version 11, where 1.0.0 shipped Java 23 bytecode (major 67) declaring 23, which
// consumers on JDK 11 to 22 could not load. No source or public API change. Gradle orders 1.0.0a
// below 1.0.0, so a consumer that also asks for plain 1.0.0 requests 1.0.0a strictly.
version = "1.0.0a"

mavenPublishing {
    coordinates(group.toString(), "webtools-scraping", version.toString())
    pom {
        name.set("WebTools Scraping")
        description.set("Connector - open and read a URL line by line, plus one-shot get/skrape/image-download helpers.")
    }
}
