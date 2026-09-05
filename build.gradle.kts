plugins {
    // Applied by each subproject, never by the root - the root publishes nothing.
    kotlin("jvm") version "2.2.0" apply false
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
}

// WebTools is published as three independent, single-concern artifacts
// (webtools-udp, webtools-scraping, webtools-browser). Everything they share -
// the Kotlin/JVM setup, the 5-level test-task layout (see CLAUDE.md), and the
// Maven Central publishing skeleton - is configured here once; each module's
// own build.gradle.kts adds only its dependencies and its coordinates.
allprojects {
    group = "io.github.spartanlaboratories"
    version = "1.0.0"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.vanniktech.maven.publish")

    repositories {
        mavenCentral()
    }

    dependencies {
        // Logging: a library depends only on the slf4j API, never on a concrete
        // backend. logback is a logging *implementation*, so it is test-only - it
        // must never leak onto a module's runtime classpath.
        "api"("org.slf4j:slf4j-api:2.0.13")
        "testImplementation"(kotlin("test-junit5"))
        "testImplementation"(kotlin("test"))
        "testImplementation"("ch.qos.logback:logback-classic:1.5.6")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Level-scoped test tasks (see CLAUDE.md "Testing - 5-Level Hierarchy"). `test`
    // still runs every level; each task below runs exactly one level, selected by the
    // JUnit @Tag that every test class under src/test/kotlin/com/spartanlabs/testing/<level>/
    // carries, so CI can run or gate a single level on its own. A module with no tests at
    // a given level simply has an empty task there.
    val levels = listOf(
        "gating" to "Level 1 - local pre-commit gating tests",
        "component" to "Level 2 - isolated component behaviour tests",
        "integration" to "Level 3 - integration & external interface tests",
        "deterministic" to "Level 4a - deterministic input-to-output tests",
        "e2e" to "Level 4b - end-to-end system integration tests",
        "nonfunctional" to "Level 4c - non-functional (robustness / security / performance) tests",
        "uat" to "Level 5 - user-acceptance evaluation (manual; mostly @Disabled)",
    )
    val sourceSets = extensions.getByName<SourceSetContainer>("sourceSets")
    levels.forEach { (tag, describe) ->
        tasks.register<Test>("${tag}Test") {
            description = "$describe (@Tag(\"$tag\"))."
            group = "verification"
            testClassesDirs = sourceSets["test"].output.classesDirs
            classpath = sourceSets["test"].runtimeClasspath
            useJUnitPlatform { includeTags(tag) }
        }
    }

    extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension>("mavenPublishing") {
        publishToMavenCentral()
        signAllPublications()
        // coordinates(...) and pom { name / description } are set per module.
        pom {
            inceptionYear.set("2026")
            url.set("https://github.com/SpartanLaboratories/WebTools")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("SpaSinghOut")
                    name.set("Spartak Singh")
                    url.set("https://github.com/SpaSinghOut")
                }
            }
            scm {
                url.set("https://github.com/SpartanLaboratories/WebTools/")
                connection.set("scm:git:git://github.com/SpartanLaboratories/WebTools.git")
                developerConnection.set("scm:git:ssh://git@github.com/SpartanLaboratories/WebTools.git")
            }
        }
    }
}
