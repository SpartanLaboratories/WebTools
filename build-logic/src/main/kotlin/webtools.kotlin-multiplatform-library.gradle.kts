import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.attributes.java.TargetJvmVersion

// Convention plugin: a published Kotlin Multiplatform library with a single jvm() target. Applies the
// multiplatform plugin, webtools.published-library and binary-compatibility-validator; sets the
// TargetJvmVersion attribute on the jvm target, the shared test dependencies, JUnit Platform, the
// seven level test tasks over the jvm test compilation and a lifecycle `test` task. Each module adds
// explicitApi(), its own dependencies, its coordinates, its POM name and description, and commits its
// ABI dump (api/<Gradle project name>.api).
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
// The major version of the JDK running the build: what an unpinned Kotlin/JVM module declares today.
// TODO(#47): replace with WebToolsBuild.JAVA_FLOOR when the Java floor lands.
val targetJvmVersion: Int = JavaVersion.current().majorVersion.toInt()

kotlin {
    jvm {
        // A multiplatform jvm() target writes no org.gradle.jvm.version unless the attribute is set on
        // the target. KGP also copies the target's attributes onto every jvm compilation's classpath
        // configurations, so this value is also the JVM version those classpaths request.
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJvmVersion)
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

//region binary compatibility
// binary-compatibility-validator 0.18.2 hard-codes ASM 9.6 on its worker classpath, which rejects
// class files above Java 22. Until the Java 11 floor compiles main to major 55, a module built on
// a JDK 23 host emits major 67 and apiDump fails. ASM 9.9 is the line that reads Java 26 class
// files (the newest JDK that runs Gradle 9.7.1); 9.9.1 is its bug-fix release. Once the floor
// lands this pin is inert: Gradle takes the highest version on the configuration.
dependencies {
    "bcv-rt-jvm-cp"("org.ow2.asm:asm:9.9.1")
    "bcv-rt-jvm-cp"("org.ow2.asm:asm-tree:9.9.1")
}
//endregion

//region publishing
mavenPublishing {
    // Equals vanniktech's auto-detected choice for a multiplatform module (sources jars and an empty
    // javadoc jar), pinned so that it stays the same if a Dokka plugin is ever applied.
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))
}
//endregion
