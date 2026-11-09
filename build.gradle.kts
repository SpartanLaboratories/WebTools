// WebTools is published as independent, single-concern artifacts (webtools-udp, webtools-scraping,
// webtools-browser, webtools-http, webtools-feeds). Everything they share - the Kotlin/JVM or Kotlin Multiplatform
// setup, the 5-level test-task layout (see the README's "Build & test" section) and the Maven Central
// publishing skeleton - lives in the included build-logic build as convention plugins
// (webtools.kotlin-jvm-library, webtools.kotlin-multiplatform-library, webtools.published-library, and
// webtools.kotlin-common-check for the unpublished check-only js modules).
// Each module's own build.gradle.kts applies one of them and adds its dependencies, its coordinates,
// its POM name and description, and any configuration that belongs to it alone (for example
// webtools-udp's version and UDP-port lock); only the group and the default version every module
// shares are set here. The root also aggregates the API reference of the five published modules into one
// Dokka publication (build/dokka/html); everything else stays in a module build file or a convention plugin.

// ModuleSource.rootBlock reads this block into the generated builds of the build-logic checks, so its opening line
// and closing brace stay at column 0.
plugins {
    // Dokka and the Kotlin Gradle plugin must share ONE plugin classloader: without the Kotlin plugin on the root
    // classpath, Dokka (applied here) would sit in the root classloader while the Kotlin plugin (applied to the modules
    // by the convention plugins) sat in per-module child classloaders, Dokka could not load KotlinBasePlugin and would
    // see no source sets (Gradle issues 25616 and 35117). In this repository, without both lines below, that stops every
    // build at configuration (DokkaSourceSetSpec with name 'jvmTestFixtures' not found), because webtools-http names that
    // source set; only a build that names no source set renders every module empty instead. The root has no source, so
    // the Kotlin plugins are applied `apply false`, only to put them on the root plugin classpath. Either id alone would
    // do that, since both resolve to the one Kotlin Gradle plugin jar.
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.multiplatform") apply false
    id("org.jetbrains.dokka")
}

// The Dokka generator resolves its own dependencies from the project's repositories; the modules get
// theirs from webtools.published-library.
repositories { mavenCentral() }

// RootScriptsAgreementGatingTest checks that this list names exactly the modules that apply a library convention plugin.
dependencies {
    dokka(project(":webtools-udp"))
    dokka(project(":webtools-scraping"))
    dokka(project(":webtools-browser"))
    dokka(project(":webtools-http"))
    dokka(project(":webtools-feeds"))
}

// ModuleSource.rootBlock reads this block into the Dokka aggregation check, so its opening line and closing brace stay
// at column 0; RootScriptsAgreementGatingTest checks that this script sets failOnWarning.
dokka {
    moduleName.set("WebTools")
    moduleVersion.set("")   // the five modules version independently, so the aggregate names no version of its own
    dokkaPublications.configureEach { failOnWarning.set(true) }
    pluginsConfiguration.html {
        customStyleSheets.from(file("docs/dokka/styles/dokka-styles.css"))
        customAssets.from(file("docs/brand/spartan-laboratories-logo.png"))
        footerMessage.set("&copy; 2026 Spartan Laboratories")
    }
}

// ModuleSource.rootBlock reads this block into the generated builds of the build-logic checks, so its opening line
// and closing brace stay at column 0.
allprojects {
    group = "io.github.spartanlaboratories"
    version = "1.0.0"
}
