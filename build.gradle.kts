// WebTools is published as three independent, single-concern artifacts (webtools-udp,
// webtools-scraping, webtools-browser). Everything they share - the Kotlin/JVM setup, the 5-level
// test-task layout (see the README's "Build & test" section) and the Maven Central publishing
// skeleton - lives in the included build-logic build as convention plugins
// (webtools.kotlin-jvm-library, webtools.kotlin-multiplatform-library, webtools.published-library).
// Each module's own build.gradle.kts applies one of them and adds its dependencies, its coordinates,
// its POM name and description, and any configuration that belongs to it alone (for example
// webtools-udp's version and UDP-port lock); only the group and the default version every module
// shares are set here.
allprojects {
    group = "io.github.spartanlaboratories"
    version = "1.0.0"
}
