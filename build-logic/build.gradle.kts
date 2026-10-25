import java.time.Duration

plugins {
    // kotlin-dsl is safe here: this is a separate included build compiled on the build-script
    // classpath, not a library module (it is never published and never on a module's runtime).
    `kotlin-dsl`
}

dependencies {
    // Plugin marker artifacts, so the convention plugins can apply these by id with no version.
    // Each Kotlin marker's POM has exactly one dependency, kotlin-gradle-plugin:2.2.0.
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.2.0")
    implementation("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:2.2.0")
    implementation("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.36.0")
    implementation("org.jetbrains.kotlinx.binary-compatibility-validator:org.jetbrains.kotlinx.binary-compatibility-validator.gradle.plugin:0.18.2")

    // Test - JUnit 5. kotlin-dsl applies java-gradle-plugin, which already adds gradleTestKit(),
    // gradleApi() (with Groovy's groovy-json and slf4j-api) and the plugin-under-test metadata to
    // the `test` source set, so nothing else is declared. Gradle 9 needs an explicit launcher for
    // JUnit 5 in a plain `test` source set. No version catalog: literal coordinates. No logback:
    // gradleApi() already carries Gradle's own slf4j binding, and a second provider would conflict.
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // A throwaway OpenPGP key per run, so the fixtures-publication check can sign its published copy of
    // webtools-http and assert a signature beside every file, the fixtures jar included. Gradle's own copy is
    // relocated under org.gradle.internal.impldep and must not be relied upon.
    testImplementation("org.bouncycastle:bcpg-jdk18on:1.86")
}

//region build-logic's own test levels
// build-logic's tests follow the 5-level layout: one package per level,
// com.spartanlabs.testing.<level>.webtools.buildlogic, and one task per level that has tests.
// WebToolsBuild.registerLevelTasks is not used here: a build's own classes are not on its build
// script's classpath. Unlike a module's level task, each of these fails on an empty run: every one
// of them has tests, so an empty run means a broken filter or a lost test class.

// Read by the harness as ProjectWorkspace.PROJECTS_PROPERTY: generated projects live here, not in
// @TempDir (Windows file locks).
val testKitProjects: String = layout.buildDirectory.dir("testkit/projects").get().asFile.absolutePath

// Read by the harness as NestedBuild.TESTKIT_HOME_PROPERTY: one TestKit directory, which is also the
// nested builds' Gradle user home, shared by every nested-build task, so only the first run downloads.
val testKitHome: String = layout.buildDirectory.dir("testkit/home").get().asFile.absolutePath

// Read by the harness as BuildLogicSource.SOURCE_PROPERTY: the end-to-end check copies this build's
// sources from here into a generated composite.
val buildLogicDir: String = layout.projectDirectory.asFile.absolutePath

// Read by the harness as ModuleSource.MODULE_PROPERTY: the fixtures-publication check copies the module's
// build file and three source trees from here (and the repository's root build file next to it).
val webToolsHttpDir: String = layout.projectDirectory.dir("../webtools-http").asFile.absolutePath

// The nested-build tasks (the three level tasks and fixturesPublicationCheck) share that one TestKit home,
// and with the configuration cache Gradle runs tasks of one project at the same time. Gradle's cross-process
// locks keep a shared home consistent, but several nested daemons at once contend for its caches and for
// memory. Each nested-build task therefore takes this single-permit service, so Gradle runs them one after
// another, as webtools-udp's port lock does for its socket-bound tasks.
abstract class TestKitHomeLock : BuildService<BuildServiceParameters.None>

val testKitHomeLock: Provider<TestKitHomeLock> =
    gradle.sharedServices.registerIfAbsent("testKitHomeLock", TestKitHomeLock::class) {
        maxParallelUsages = 1
    }

// The two properties, the lock and the timeout of every task that starts nested builds.
fun Test.configureNestedBuilds() {
    systemProperty("webtools.testkit.projects", testKitProjects)
    systemProperty("webtools.testkit.home", testKitHome)
    usesService(testKitHomeLock)
    // The first cold run of the nested-build checks took about 83 s on the maintainer's host; 30 minutes
    // leaves room for a cold TestKit home on a slow network.
    timeout.set(Duration.ofMinutes(30))
}

// The check copies exactly these files into its generated builds, so they are inputs of the task. A
// directory is declared as a fileTree (inputs.dir fails the task when the directory is missing).
fun Test.copiesWebToolsHttp() {
    systemProperty("webtools.http.dir", webToolsHttpDir)
    inputs.files("../webtools-http/build.gradle.kts", "../build.gradle.kts")
        .withPropertyName("webToolsHttpBuildFiles").withPathSensitivity(PathSensitivity.RELATIVE)
    listOf("commonMain", "jvmMain", "jvmTestFixtures").forEach { tree ->
        inputs.files(fileTree("../webtools-http/src/$tree"))
            .withPropertyName("webToolsHttp" + tree.replaceFirstChar(Char::uppercaseChar))
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

fun registerBuildLogicLevelTest(level: String, summary: String, startsNestedBuilds: Boolean): TaskProvider<Test> =
    tasks.register<Test>("${level}Test") {
        group = "verification"
        description = summary
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath // carries the plugin-under-test metadata
        useJUnitPlatform()
        filter {
            includeTestsMatching("com.spartanlabs.testing.$level.*")
            isFailOnNoMatchingTests = true
        }
        if (startsNestedBuilds) configureNestedBuilds()
    }

registerBuildLogicLevelTest("gating", "Level 1 - build-logic's pre-commit gating checks.", startsNestedBuilds = false)
registerBuildLogicLevelTest("component", "Level 2 - WebToolsBuild in isolation.", startsNestedBuilds = false)
registerBuildLogicLevelTest(
    "integration",
    "Level 3 - TestKit checks of the convention plugins (nested builds; first run needs the network).",
    startsNestedBuilds = true,
)
registerBuildLogicLevelTest("deterministic", "Level 4a - pure checks of the level list.", startsNestedBuilds = false)
registerBuildLogicLevelTest(
    "e2e",
    "Level 4b - the included-build path, the fixtures publication and the check's wiring, end to end (nested builds; first run needs the network).",
    startsNestedBuilds = true,
).configure {
    systemProperty("webtools.buildlogic.dir", buildLogicDir)
    // The check copies these files into its composite, so they are inputs of the task.
    inputs.files("settings.gradle.kts", "build.gradle.kts").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main").withPathSensitivity(PathSensitivity.RELATIVE)
    // e2e also selects the fixtures-publication check's class, so it copies the module too.
    copiesWebToolsHttp()
}
registerBuildLogicLevelTest(
    "nonfunctional",
    "Level 4c - configuration-cache compatibility of the convention plugins (nested builds; first run needs the network).",
    startsNestedBuilds = true,
)

// The fixtures-publication check: a Level 4b class of its own task, so webtools-http's check can depend on
// exactly it (gradle.includedBuild("build-logic").task(":fixturesPublicationCheck")). Not part of this
// build's own test or check. It publishes a copy of webtools-http to a folder repository inside its
// generated workspace (producer/build/repo under build/testkit/projects), never to a remote; it signs with a
// throwaway OpenPGP key generated for the run and written only into that workspace's gradle.properties; its
// first run downloads through the shared TestKit home; the workspace is deleted best-effort when the test
// class ends (a leftover is removed by clean).
tasks.register<Test>("fixturesPublicationCheck") {
    group = "verification"
    description = "Level 4b - publishes a copy of webtools-http with its test-fixtures variant and resolves testFixtures(...) from three build shapes (nested builds; first run needs the network). Part of webtools-http's check."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.spartanlabs.testing.e2e.webtools.buildlogic.FixturesPublicationEndToEndTest")
        isFailOnNoMatchingTests = true
    }
    configureNestedBuilds()
    copiesWebToolsHttp()
}

// `test` is the fast aggregate and build-logic's pre-commit gate: the levels that start no nested
// build. It fails on an empty run too.
tasks.named<Test>("test") {
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.spartanlabs.testing.gating.*")
        includeTestsMatching("com.spartanlabs.testing.component.*")
        includeTestsMatching("com.spartanlabs.testing.deterministic.*")
        isFailOnNoMatchingTests = true
    }
}
//endregion
