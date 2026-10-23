import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Convention plugin: a published Kotlin/JVM library module. Applies the Kotlin/JVM plugin and
// webtools.published-library, compiles to the Java floor (WebToolsBuild.JAVA_FLOOR) and guards it
// with verifyJavaFloor in check, adds the shared test dependencies, runs every Test task on the JUnit
// Platform and registers the seven level test tasks over the `test` source set. A module adds its
// dependencies (api(slf4j-api) included), its coordinates, its POM name and description, and any
// configuration that belongs to it alone (for example webtools-udp's version and UDP-port lock).
// explicitApi() is never set here.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("webtools.published-library")
}

//region java floor
// Every published class is Java 11 bytecode (class-file major 55) built against the Java 11 API, on
// whatever JDK runs the build. jvmTarget lowers the class-file version only: a JDK 21 call such as
// MutableList.removeFirst() still compiles, to a method Java 11 to 20 lacks, so -Xjdk-release limits
// the API too. The flag fails without jvmTarget set to the same value, because the Kotlin Gradle
// plugin always passes its own -jvm-target. The Java compatibility settings are required as well: the
// plugin fails the build when compileJava's target differs from jvmTarget, and they are what write
// org.gradle.jvm.version on the API and runtime variants. -Xjdk-release is an experimental flag that
// Kotlin does not promise on every JDK distribution; verifyJavaFloor is the backstop.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(WebToolsBuild.JAVA_FLOOR.toString()))
        freeCompilerArgs.add("-Xjdk-release=${WebToolsBuild.JAVA_FLOOR}")
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(WebToolsBuild.JAVA_FLOOR)
    targetCompatibility = JavaVersion.toVersion(WebToolsBuild.JAVA_FLOOR)
}

val verifyJavaFloor = WebToolsBuild.registerVerifyJavaFloor(
    project,
    files(tasks.named<Jar>("jar").flatMap { it.archiveFile }),
    configurations.getByName("runtimeClasspath"),
)

tasks.named("check") {
    dependsOn(verifyJavaFloor)
}
//endregion

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
