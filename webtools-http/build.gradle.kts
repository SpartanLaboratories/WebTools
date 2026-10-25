// webtools-http - the HTTP client stack: the request and response types, a suspend transport port, a
// typed failure model and the other ports and defaults of the stack. Kotlin Multiplatform: commonMain
// holds the contracts and the platform-free code, jvmMain the platform actuals and the OkHttp and
// Caffeine implementations. It publishes webtools-http and webtools-http-jvm (a Maven build depends on
// webtools-http-jvm).
//
// 1.0.0 is the version inherited from the root build, and it is UNRELEASED: webtools-http is new, and it
// stays unpublished until the whole stack (issue #55) is complete. It is not a release candidate while
// the units of that work land one by one. Remove this note when 1.0.0 is released.
//
// Everything shared (the jvm() target at the Java floor, the test dependencies, the level tasks,
// publishing, binary-compatibility-validator and verifyJavaFloor) comes from the convention plugin. This
// file adds explicitApi(), the dependencies, the module-wide opt-in, the coordinates and the POM name and
// description, the test-fixtures variant (the testFixtures compilation, its jar, its two published
// variants and its place in verifyJavaFloor) and the edge from check to build-logic's fixtures-publication
// check. The ABI dump api/webtools-http.api is committed (:webtools-http:apiDump); apiCheck is part of
// check and does not cover the fixtures.

import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("webtools.kotlin-multiplatform-library")
}

// The marker that gates the module's own ports (HttpTransport, WallClock, HttpFailure, ...). The module's
// main code implements those ports, so it opts in; its tests do not, and stay a consumer.
val gateMarker = "com.spartanlabs.webtools.http.ExperimentalForInheritanceWebToolsApi"

kotlin {
    explicitApi()

    sourceSets {
        // The opt-in is a language setting of the MAIN source sets only. Do not replace it with
        // kotlin { compilerOptions { optIn.add(...) } }, with the same option on the jvm target, or with
        // sourceSets.all { languageSettings.optIn(...) }: each of those also reaches commonTest and
        // jvmTest, which would then compile as a non-consumer and could implement a port or subclass a
        // failure group without the @OptIn a consumer needs. In a single-target module only the leaf line
        // (jvmMain) reaches the compiler; the commonMain line keeps the pair valid if a target is added
        // (a shared source set must use every opt-in its leaf uses).
        commonMain {
            languageSettings.optIn(gateMarker)
            dependencies {
                // The runtime of the suspend API, and CoroutineDispatcher appears in the stack's
                // configuration: the one api dependency. The others are implementation details.
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        jvmMain {
            languageSettings.optIn(gateMarker)
            dependencies {
                // OkHttp: the transport. Caffeine: the response cache. slf4j: the logging seam.
                implementation("com.squareup.okhttp3:okhttp-jvm:5.5.0")
                implementation("com.github.ben-manes.caffeine:caffeine:3.3.1")
                implementation("org.slf4j:slf4j-api:2.0.13")
            }
        }
    }
}

//region test fixtures
// The published test support (com.spartanlabs.webtools.http.testing): a JVM-only testFixtures compilation
// associated with main (so it reaches main's internal declarations) and published as two Gradle Module
// Metadata variants beside a -test-fixtures.jar in the root module's directory. A consumer writes
// testImplementation(testFixtures("io.github.spartanlaboratories:webtools-http:<version>")) (Gradle only).
// Written here, not in the convention plugin: only this module has fixtures in 1.0.0; extract it when a
// second module publishes fixtures. The test compilation is associated with the fixtures, so the module's own
// tests use them. The fixtures compilation has NO languageSettings.optIn: each fixture that implements a
// gated port carries its own @OptIn, which is also what a consumer's implementation needs.
kotlin {
    jvm {
        val main = compilations.getByName("main")
        val fixtures = compilations.create("testFixtures") { associateWith(main) }
        compilations.getByName("test").associateWith(fixtures)
    }
}

val fixturesCompilation = kotlin.jvm().compilations.getByName("testFixtures")

// explicitApi() does not reach this compilation; the flag enforces visibility and return types. KDoc on the
// fixtures stays a review item.
tasks.named<KotlinJvmCompile>("compileTestFixturesKotlinJvm") {
    compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
}

val testFixturesJar = tasks.register<Jar>("testFixturesJar") {
    archiveClassifier.set("test-fixtures")
    from(fixturesCompilation.output.allOutputs)
}

// TargetJvmVersion on the jvm() target does not reach hand-declared variants, so each sets the floor itself.
fun fixturesElements(name: String, usage: String, vararg extends: String) =
    configurations.consumable(name) {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(usage))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, WebToolsBuild.JAVA_FLOOR)
        }
        extendsFrom(*extends.map { configurations.getByName(it) }.toTypedArray())
        outgoing {
            capability("${project.group}:${project.name}-test-fixtures:${project.version}")
            artifact(testFixturesJar)
        }
    }

val testFixturesApiElements = fixturesElements("testFixturesApiElements", Usage.JAVA_API, "jvmTestFixturesApi")
val testFixturesRuntimeElements = fixturesElements(
    "testFixturesRuntimeElements", Usage.JAVA_RUNTIME,
    "jvmTestFixturesApi", "jvmTestFixturesImplementation", "jvmTestFixturesRuntimeOnly",
)

// Added to the root publication. The Kotlin Gradle plugin asks that added variants do not match the default
// ones, so no Kotlin attribute is set. mapToOptional keeps the test libraries optional in the root POM.
@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin.publishing.adhocSoftwareComponent {
    addVariantsFromConfiguration(testFixturesApiElements.get()) { mapToMavenScope("compile"); mapToOptional() }
    addVariantsFromConfiguration(testFixturesRuntimeElements.get()) { mapToMavenScope("runtime"); mapToOptional() }
}

// Gradle warns "Declares capability ... which cannot be mapped to Maven" per variant otherwise.
publishing.publications.withType<MavenPublication>().configureEach {
    suppressPomMetadataWarningsFor("testFixturesApiElements")
    suppressPomMetadataWarningsFor("testFixturesRuntimeElements")
}

dependencies {
    // The module itself (a consumer gets main at the same version), then the fixtures' own libraries.
    // mockwebserver3 is declared for the loopback factories of a later unit; nothing else is added.
    "jvmTestFixturesApi"(project(project.path))
    "jvmTestFixturesApi"("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    "jvmTestFixturesApi"("com.squareup.okhttp3:mockwebserver3:5.5.0")
}

// The fixtures jar and the fixtures' runtime classpath (what a consumer's test loads) join the Java floor
// guard. The fixtures variants in the generated .module are already read: the guard reads every
// GenerateModuleMetadata output. Never assign runtimeDependencyFiles in a module script: the level tasks read
// the test compilation's once.
// The association puts main's output.allOutputs in the fixtures' runtime classpath, including a Java classes
// directory and a processed-resources directory this module never creates (no Java source, no resources).
// The guard fails on a missing root by design, so only existing entries are handed over; the filter is lazy
// and keeps the collection's task dependencies. Main's classes are then scanned twice (as the jvm jar and as
// a class directory), which only raises the counts.
tasks.named<VerifyJavaFloor>("verifyJavaFloor") {
    jars.from(testFixturesJar.flatMap { it.archiveFile })
    runtimeClasspath.from(
        checkNotNull(fixturesCompilation.runtimeDependencyFiles) {
            "the testFixtures compilation has no runtime classpath for verifyJavaFloor to scan"
        }.filter(Spec<File> { it.exists() }),
    )
}
//endregion

mavenPublishing {
    coordinates(group.toString(), "webtools-http", version.toString())
    pom {
        name.set("WebTools HTTP")
        description.set("A suspend HTTP client stack: request and response types, a transport port, a typed failure model, an outbound-URL policy, a response cache and a call budget.")
    }
}

//region fixtures-publication check
// The fixtures-publication check is a TestKit test in the included build-logic build, which neither `build`
// nor `check` reaches on its own (task selectors do not cross builds). Attach it to this module's check, so
// `./gradlew build`, `./gradlew check` and `./gradlew :webtools-http:check` all run it while the root build
// keeps only group and version. It is wired only when WebTools is the root build (a build that includes
// WebTools must not start it) and only where build-logic is included (the check's own producer copy of this
// file injects the plugins and includes no build-logic).
if (gradle.parent == null && gradle.includedBuilds.any { it.name == "build-logic" }) {
    tasks.named("check") {
        dependsOn(gradle.includedBuild("build-logic").task(":fixturesPublicationCheck"))
    }
}
//endregion
