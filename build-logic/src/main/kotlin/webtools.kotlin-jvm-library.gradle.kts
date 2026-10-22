// Convention plugin: a published Kotlin/JVM library module. Applies the Kotlin/JVM plugin and
// webtools.published-library, adds the shared test dependencies, runs every Test task on the JUnit
// Platform and registers the seven level test tasks over the `test` source set. A module adds its
// dependencies (api(slf4j-api) included), its coordinates, its POM name and description, and any
// configuration that belongs to it alone (for example webtools-udp's version and UDP-port lock).
// explicitApi() is never set here. It sets no jvmTarget or toolchain: published class files and
// org.gradle.jvm.version follow the build JDK until #47 adds the Java floor.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("webtools.published-library")
}

// TODO(#47): the java floor region goes here.

//region test dependencies
dependencies {
    // logback is a logging *implementation*, so it is test-only - it must never leak onto a
    // module's runtime classpath. (Moved from the root build.gradle.kts at 834ce4a (`:25-33`);
    // api(slf4j-api) stays in each module.)
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("test"))
    testImplementation("ch.qos.logback:logback-classic:1.5.6")
}
//endregion

//region test tasks
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// `test` still runs every level; each level task runs exactly one, selected by package.
WebToolsBuild.registerLevelTasks(
    project,
    sourceSets["test"].output.classesDirs,
    sourceSets["test"].runtimeClasspath,
)
//endregion
