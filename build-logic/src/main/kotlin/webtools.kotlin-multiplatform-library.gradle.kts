import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Convention plugin: a published Kotlin Multiplatform library with a single jvm() target. Applies the
// multiplatform plugin, webtools.published-library and binary-compatibility-validator; compiles the
// jvm target to the Java floor (WebToolsBuild.JAVA_FLOOR), declares that floor as the TargetJvmVersion
// attribute and guards it with verifyJavaFloor in check; adds the shared test dependencies, JUnit
// Platform, the seven level test tasks over the jvm test compilation and a lifecycle `test` task. Each
// module adds explicitApi(), its own dependencies, its coordinates, its POM name and description, and
// commits its ABI dump (api/<Gradle project name>.api).
// Declares no second JVM-based target: a second one moves the ABI dump to api/<target>/.
// Kotlin Gradle plugin 2.2.0 prints a Gradle-10 deprecation of the `archives` configuration at jvm()
// (KT-78620), so builds are never run with --warning-mode=fail.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("webtools.published-library")
    id("com.vanniktech.maven.publish")   // listed again so this script's own mavenPublishing {} accessor is generated; re-applying is idempotent
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

//region jvm target
// The Java floor, as for the Kotlin/JVM plugin (see its java floor region): jvmTarget plus
// -Xjdk-release, both from the one constant. A multiplatform jvm() target has no JavaCompile task, so
// there are no Java compatibility settings to match.
kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(WebToolsBuild.JAVA_FLOOR.toString()))
            freeCompilerArgs.add("-Xjdk-release=${WebToolsBuild.JAVA_FLOOR}")
        }
        // A multiplatform jvm() target writes no org.gradle.jvm.version unless the attribute is set on
        // the target. KGP also copies the target's attributes onto every jvm compilation's classpath
        // configurations, so the floor is also the JVM version those classpaths request.
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, WebToolsBuild.JAVA_FLOOR)
        }
        val testCompilation = compilations.getByName("test")
        WebToolsBuild.registerLevelTasks(
            project,
            testCompilation.output.classesDirs,
            // runtimeDependencyFiles is nullable in the KGP API. It holds the compilation's runtime
            // classpath configuration, and associateWith adds to that configuration rather than
            // replacing it, so a compilation associated with `test` later still reaches the level
            // tasks. A module script must not assign runtimeDependencyFiles itself.
            project.files(listOfNotNull(testCompilation.runtimeDependencyFiles, testCompilation.output.allOutputs)),
        )
    }
}
//endregion

//region test dependencies
kotlin {
    sourceSets {
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test-junit5"))
            implementation("ch.qos.logback:logback-classic:1.5.6")
        }
    }
}
//endregion

//region test tasks
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// A multiplatform module has no `test` task, so `./gradlew test` would silently skip it.
tasks.register("test") {
    group = "verification"
    description = "Runs the JVM test suite (alias for jvmTest) so ./gradlew test covers this module."
    dependsOn("jvmTest")
}
//endregion

//region publishing
mavenPublishing {
    // Equals vanniktech's auto-detected choice for a multiplatform module (sources jars and an empty
    // javadoc jar), pinned so that it stays the same if a Dokka plugin is ever applied.
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))
}
//endregion

//region java floor
// verifyJavaFloor reads the jvm jar, the generated Gradle Module Metadata and the jvm runtime
// classpath. A module that adds another published jar (the test-fixtures jar) adds it to the task's
// `jars`.
val verifyJavaFloor = WebToolsBuild.registerVerifyJavaFloor(
    project,
    files(tasks.named<Jar>("jvmJar").flatMap { it.archiveFile }),
    // runtimeDependencyFiles is nullable in the KGP API. The main jvm compilation always has it, and the
    // guard must never run blind on an empty runtime classpath, so a missing one fails the build here.
    checkNotNull(kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles) {
        "the jvm main compilation has no runtime classpath for verifyJavaFloor to scan"
    },
)

tasks.named("check") {
    dependsOn(verifyJavaFloor)
}
//endregion
