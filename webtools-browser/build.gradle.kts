// webtools-browser - WebViewer: headless-browser screenshots via Selenium.
// Selenium Manager (bundled since 4.6) provisions the browser driver at runtime,
// so no driver binaries are shipped or configured.

plugins {
    id("webtools.kotlin-jvm-library")
}

dependencies {
    // Logging: a library depends only on the slf4j API, never on a concrete backend.
    api("org.slf4j:slf4j-api:2.0.13")
    implementation("org.seleniumhq.selenium:selenium-java:4.48.0")
}

// Explicit per-module version, overriding the root allprojects { version = "1.0.0" }.
// 1.0.0a: the bugfix after 1.0.0 - the Java 11 floor (Issue #47). The class files are Java 11
// bytecode (major 55) built against the Java 11 API, and the Gradle Module Metadata declares
// org.gradle.jvm.version 11, where 1.0.0 shipped Java 23 bytecode (major 67) declaring 23, which
// consumers on JDK 11 to 22 could not load. No source or public API change. Gradle orders 1.0.0a
// below 1.0.0, so a consumer that also asks for plain 1.0.0 requests 1.0.0a strictly.
version = "1.0.0a"

mavenPublishing {
    coordinates(group.toString(), "webtools-browser", version.toString())
    pom {
        name.set("WebTools Browser")
        description.set("WebViewer - headless-browser screenshots via Selenium.")
    }
}
