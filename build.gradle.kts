plugins {
    kotlin("jvm").version("2.2.0")
    id("com.vanniktech.maven.publish") version "0.36.0"
    kotlin("kapt") version "2.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Logging: a library should only depend on the slf4j API, never on a concrete
    // backend. Consumers of this library choose their own logging implementation.
    api("org.slf4j:slf4j-api:2.0.13")

    testImplementation(kotlin("test-junit5"))
    // logback is a logging *implementation*, so it is only wired up for tests -
    // it must never leak onto the library's runtime classpath.
    testImplementation("ch.qos.logback:logback-classic:1.5.6")

    implementation("it.skrape:skrapeit:1.1.5")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.mashape.unirest:unirest-java:1.4.9")

    implementation("org.seleniumhq.selenium:selenium-java:4.0.0")   // Screenshot utility

    api("io.github.spartanlaboratories:GeneralTools:2.0.1")
    testImplementation(kotlin("test"))
}

// Serialises the test tasks that bind the fixed common UDP port (9998) - `test` and
// `integrationTest` - so Gradle never runs two of them in parallel workers and hits a
// BindException. Level tasks that touch no socket are unaffected.
abstract class CommonUdpPortLock : BuildService<BuildServiceParameters.None>
val commonUdpPortLock =
    gradle.sharedServices.registerIfAbsent("commonUdpPortLock", CommonUdpPortLock::class) {
        maxParallelUsages = 1
    }

tasks.test {
    useJUnitPlatform()
    usesService(commonUdpPortLock)
}

// Level-scoped test tasks (see CLAUDE.md "Testing - 5-Level Hierarchy"). `test` still runs
// every level; each task below runs exactly one level, selected by the JUnit @Tag that every
// test class under src/test/kotlin/com/spartanlabs/testing/<level>/ carries, so CI can run or
// gate a single level on its own. (No Level 4b task: the repo has no end-to-end tests yet.)
listOf(
    "gating" to "Level 1 - local pre-commit gating tests",
    "component" to "Level 2 - isolated component behaviour tests",
    "integration" to "Level 3 - integration & external interface tests",
    "deterministic" to "Level 4a - deterministic input-to-output tests",
    "nonfunctional" to "Level 4c - non-functional (robustness / security / performance) tests",
    "uat" to "Level 5 - user-acceptance evaluation (manual; mostly @Disabled)",
).forEach { (tag, describe) ->
    tasks.register<Test>("${tag}Test") {
        description = "$describe (@Tag(\"$tag\"))."
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform { includeTags(tag) }
        // Only the integration level binds the fixed common port.
        if (tag == "integration") usesService(commonUdpPortLock)
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.spartanlaboratories", "WebTools", "2.0.0b")

    pom {
        name.set("Web Tools")
        description.set("A compilation of internet IO tools.")
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