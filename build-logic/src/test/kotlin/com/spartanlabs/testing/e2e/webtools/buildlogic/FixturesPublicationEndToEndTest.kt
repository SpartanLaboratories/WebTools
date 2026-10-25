package com.spartanlabs.testing.e2e.webtools.buildlogic

import WebToolsBuild
import com.spartanlabs.testing.support.webtools.buildlogic.JarClasses
import com.spartanlabs.testing.support.webtools.buildlogic.ModuleMetadata
import com.spartanlabs.testing.support.webtools.buildlogic.ModuleSource
import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.PomFile
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.ResolutionReport
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import com.spartanlabs.testing.support.webtools.buildlogic.TestReports
import com.spartanlabs.testing.support.webtools.buildlogic.ThrowawayPgpKey
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.streams.asSequence

/**
 * Level 4b - the published test-fixtures variant of `webtools-http`, end to end.
 *
 * One `@BeforeAll` runs three nested builds in one workspace: the **producer** (the real module copied in by
 * [ModuleSource], published to a build-local repository with a throwaway signing key, plus an in-build Kotlin
 * Multiplatform consumer), the **file-repository consumer** (a Kotlin/JVM and a Kotlin Multiplatform module
 * resolving the published artifacts) and the **composite consumer** (`includeBuild("../producer")`). Each
 * consumer compiles and runs one test that uses `VirtualClock`, `FakeHttpTransport`, `kotlinx-coroutines-test`
 * and `mockwebserver3`.
 *
 * It is the only check on the experimental `adhocSoftwareComponent` publishing API and the only run that signs
 * a multiplatform publication carrying an adhoc variant before a release. The published version is read from
 * the repository, not a constant, so a stale repository cannot satisfy the consumers. The key never reaches a
 * log or a command line: it is written only into the producer's `gradle.properties` in the workspace.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FixturesPublicationEndToEndTest {

    private lateinit var workspace: ProjectWorkspace
    private lateinit var producer: BuildResult
    private lateinit var fileConsumer: BuildResult
    private lateinit var compositeConsumer: BuildResult
    private lateinit var repository: Repository
    private lateinit var version: String
    private lateinit var passphrase: String
    private lateinit var key: String

    @BeforeAll
    fun runBuilds() {
        workspace = ProjectWorkspace.create("fixtures").getOrThrow()
        passphrase = UUID.randomUUID().toString()
        key = ThrowawayPgpKey.generate(passphrase).getOrThrow()
        workspace.write(SampleProject.fixturesProducer(ModuleSource.rootBuildFile().getOrThrow(), signingProperties(key, passphrase))).getOrThrow()
        ModuleSource.copyInto(workspace, "${SampleProject.FIXTURES_PRODUCER}/webtools-http").getOrThrow()
        producer = NestedBuild(workspace, projectDir = workspace.resolve(SampleProject.FIXTURES_PRODUCER)).build(
            ":webtools-http:publishAllPublicationsToTestFolderRepository",
            ":${SampleProject.FIXTURES_KMP_CONSUMER}:jvmTest",
            ":${SampleProject.FIXTURES_KMP_CONSUMER}:resolutionReport",
            "--stacktrace",
        )
        repository = Repository(workspace.resolve("${SampleProject.FIXTURES_PRODUCER}/build/repo"))
        version = repository.version().getOrThrow()
        workspace.write(SampleProject.fixturesFileConsumer(repository.root.toUri().toString(), version)).getOrThrow()
        fileConsumer = NestedBuild(workspace, projectDir = workspace.resolve(SampleProject.FIXTURES_FILE_CONSUMER)).build("test", "resolutionReport", "--stacktrace")
        workspace.write(SampleProject.fixturesCompositeConsumer(version)).getOrThrow()
        compositeConsumer = NestedBuild(workspace, projectDir = workspace.resolve(SampleProject.FIXTURES_COMPOSITE_CONSUMER)).build("test", "resolutionReport", "--stacktrace")
    }

    @AfterAll
    fun closeWorkspace() {
        workspace.close()
    }

    // Written only into the producer's gradle.properties inside the workspace; never logged or passed as an argument.
    private fun signingProperties(key: String, passphrase: String): String =
        "signingInMemoryKey=${ThrowawayPgpKey.asPropertyValue(key)}\nsigningInMemoryKeyPassword=$passphrase\n"

    private fun fixturesCapability(): String = "$GROUP:webtools-http-test-fixtures:$version"
    private fun fixturesJarName(): String = "webtools-http-$version-test-fixtures.jar"

    //region the published files
    @Test
    fun `1 - the publish task succeeded locally and the fixtures jar is beside the root module`() {
        assertEquals(TaskOutcome.SUCCESS, producer.task(":webtools-http:publishAllPublicationsToTestFolderRepository")?.outcome)
        assertTrue(producer.tasks.none { "MavenCentral" in it.path }, "a Maven Central task ran: ${producer.tasks.map { it.path }}")
        assertTrue(Files.isRegularFile(repository.rootModuleDir(version).resolve(fixturesJarName())), "the fixtures jar is not in the root module's directory")
    }

    @Test
    fun `2 - both fixtures variants carry the capability the floor the jar and exactly the declared dependencies`() {
        val metadata = ModuleMetadata.parse(repository.rootModuleDir(version).resolve("webtools-http-$version.module")).getOrThrow()
        listOf("testFixturesApiElements", "testFixturesRuntimeElements").forEach { name ->
            val variant = metadata.variant(name).getOrThrow()
            assertEquals(listOf(fixturesCapability()), variant.capabilities, "$name: capabilities")
            assertEquals(Result.success(WebToolsBuild.JAVA_FLOOR), metadata.jvmVersion(name), "$name: jvm version")
            assertEquals(listOf(fixturesJarName()), variant.files, "$name: files")
            val requires = variant.dependencies.associate { it.module to it.requires }
            assertEquals(version, requires["webtools-http"], "$name: webtools-http")
            assertEquals(COROUTINES_TEST_VERSION, requires["kotlinx-coroutines-test"], "$name: kotlinx-coroutines-test")
            assertEquals(MOCKWEBSERVER_VERSION, requires["mockwebserver3"], "$name: mockwebserver3")
            // The Kotlin plugin adds kotlin-stdlib to the compilation's api; its version is not asserted.
            assertEquals(setOf("webtools-http", "kotlinx-coroutines-test", "mockwebserver3"), requires.keys - "kotlin-stdlib", "$name: dependency list")
        }
    }

    @Test
    fun `3 - no main variant or jvm pom lists a test library and the root pom lists them as optional only`() {
        val root = ModuleMetadata.parse(repository.rootModuleDir(version).resolve("webtools-http-$version.module")).getOrThrow()
        val jvm = ModuleMetadata.parse(repository.jvmModuleDir(version).resolve("webtools-http-jvm-$version.module")).getOrThrow()
        (root.variants + jvm.variants).filterNot { it.name.startsWith("testFixtures") }.forEach { variant ->
            val modules = variant.dependencies.map { it.module }
            assertTrue(TEST_LIBRARIES.none { it in modules }, "${variant.name} lists a test library: $modules")
        }
        val jvmPom = PomFile.parse(repository.jvmModuleDir(version).resolve("webtools-http-jvm-$version.pom")).getOrThrow().dependencies()
        assertTrue(jvmPom.none { it.artifactId in TEST_LIBRARIES }, "the jvm pom lists a test library: $jvmPom")
        val rootPom = PomFile.parse(repository.rootModuleDir(version).resolve("webtools-http-$version.pom")).getOrThrow().dependencies()
        TEST_LIBRARIES.forEach { library ->
            val entries = rootPom.filter { it.artifactId == library }
            assertTrue(entries.isNotEmpty(), "the root pom does not list $library")
            assertTrue(entries.all { it.optional }, "the root pom lists $library as non-optional: $entries")
        }
    }

    @Test
    fun `4 - every published file has a signature and the fixtures jar's is a PGP signature`() {
        // Checksums, signatures and maven-metadata.xml are themselves unsigned; every other file needs a sibling .asc.
        val unsigned = repository.allFiles()
            .filterNot { file -> file.fileName.toString().let { name -> CHECKSUM_SUFFIXES.any { name.endsWith(it) } || name.startsWith("maven-metadata.xml") } }
            .filterNot { file -> Files.isRegularFile(file.resolveSibling(file.fileName.toString() + ".asc")) }
        assertTrue(unsigned.isEmpty(), "published files without a signature: $unsigned")
        val signature = Files.readString(repository.rootModuleDir(version).resolve(fixturesJarName() + ".asc"))
        assertTrue(signature.startsWith("-----BEGIN PGP SIGNATURE-----"), "the fixtures jar's signature does not look like a PGP signature")
        // The key and the passphrase live only in the producer's gradle.properties: no nested build's output (all run
        // with --stacktrace) may show the passphrase or any line of the armoured key. The messages echo neither.
        val keyLines = key.lines().filter { it.isNotBlank() }
        mapOf("producer" to producer, "file-consumer" to fileConsumer, "composite-consumer" to compositeConsumer).forEach { (name, result) ->
            assertFalse(result.output.contains(passphrase), "the $name build output holds the throwaway passphrase")
            assertTrue(keyLines.none { it in result.output }, "the $name build output holds a line of the throwaway key")
        }
    }

    @Test
    fun `5 - the fixtures jar holds only classes at the Java floor`() {
        val jar = repository.rootModuleDir(version).resolve(fixturesJarName())
        val majors = JarClasses.majors(jar).getOrThrow()
        assertTrue(majors.keys.any { it.startsWith("com/spartanlabs/webtools/http/testing/") }, "no class of the testing package in the fixtures jar: ${majors.keys}")
        val expected = WebToolsBuild.classFileMajor(WebToolsBuild.JAVA_FLOOR)
        val above = majors.filterValues { it != expected }
        assertTrue(above.isEmpty(), "classes not at major $expected: $above")
        // No resource ships in the fixtures jar (R1): no logback-test.xml, no fixtures/ tree, nothing but classes and META-INF.
        val resources = JarClasses.entries(jar).getOrThrow().filterNot { it.startsWith("META-INF/") || it.endsWith(".class") }
        assertTrue(resources.isEmpty(), "the fixtures jar holds entries that are neither classes nor META-INF: $resources")
    }
    //endregion

    //region the consumers
    @Test
    fun `6 - the in-build Kotlin Multiplatform consumer resolves the fixtures on its test classpaths only`() {
        assertConsumer("${SampleProject.FIXTURES_PRODUCER}/${SampleProject.FIXTURES_KMP_CONSUMER}", KMP_CONFIGURATIONS, source = "project", testTask = "jvmTest")
    }

    @Test
    fun `7 - the file-repository Kotlin JVM consumer resolves the fixtures on its test classpaths only`() {
        assertConsumer("${SampleProject.FIXTURES_FILE_CONSUMER}/jvm", JVM_CONFIGURATIONS, source = "module", testTask = "test")
    }

    @Test
    fun `8 - the file-repository Kotlin Multiplatform consumer resolves the fixtures on its test classpaths only`() {
        assertConsumer("${SampleProject.FIXTURES_FILE_CONSUMER}/kmp", KMP_CONFIGURATIONS, source = "module", testTask = "jvmTest")
    }

    @Test
    fun `9 - the composite consumer resolves the fixtures from the included producer on its test classpaths only`() {
        assertConsumer(SampleProject.FIXTURES_COMPOSITE_CONSUMER, JVM_CONFIGURATIONS, source = "project", testTask = "test")
    }

    @Test
    fun `10 - every nested build stored a configuration-cache entry and loaded the Kotlin plugin once`() {
        mapOf("producer" to producer, "file-consumer" to fileConsumer, "composite-consumer" to compositeConsumer).forEach { (name, result) ->
            assertTrue(result.output.contains("Configuration cache entry stored"), "$name stored no configuration-cache entry")
            assertFalse(result.output.contains("loaded multiple times"), "$name loaded the Kotlin Gradle plugin more than once")
        }
    }
    //endregion

    // The main classpaths must not see the fixtures, the test classpaths must see exactly one fixtures variant.
    private fun assertConsumer(projectDir: String, configurations: Configurations, source: String, testTask: String) {
        configurations.test.forEach { configuration ->
            val report = ResolutionReport.read(workspace, projectDir, configuration).getOrThrow()
            val fixtures = report.variants.filter { fixturesCapability() in it.capabilities }
            assertEquals(1, fixtures.size, "$projectDir/$configuration: fixtures variants selected: ${report.variants}")
            assertEquals(source, fixtures.single().source, "$projectDir/$configuration: source of the fixtures variant")
            assertEquals(WebToolsBuild.JAVA_FLOOR.toString(), fixtures.single().attributes["org.gradle.jvm.version"], "$projectDir/$configuration: jvm version of the fixtures variant")
            assertTrue(fixturesJarName() in report.files, "$projectDir/$configuration: no fixtures jar in ${report.files}")
            assertTrue("mockwebserver3-$MOCKWEBSERVER_VERSION.jar" in report.files, "$projectDir/$configuration: no mockwebserver3 in ${report.files}")
            assertTrue(report.files.any { it.startsWith("kotlinx-coroutines-test") }, "$projectDir/$configuration: no kotlinx-coroutines-test in ${report.files}")
        }
        configurations.main.forEach { configuration ->
            val report = ResolutionReport.read(workspace, projectDir, configuration).getOrThrow()
            assertTrue(report.variants.none { variant -> variant.capabilities.any { "-test-fixtures:" in it } }, "$projectDir/$configuration: a fixtures variant on a main classpath: ${report.variants}")
            assertTrue(report.variants.any { it.module.startsWith("$GROUP:webtools-http:") }, "$projectDir/$configuration: webtools-http itself is not resolved: ${report.variants}")
            val leaked = report.files.filter { it == fixturesJarName() || it.startsWith("mockwebserver3") || it.startsWith("kotlinx-coroutines-test") }
            assertTrue(leaked.isEmpty(), "$projectDir/$configuration: test files on a main classpath: $leaked")
        }
        assertTrue(SampleProject.FIXTURES_CONSUMER_TEST_CLASS in TestReports.resultClasses(workspace, testTask, projectDir), "$projectDir: $testTask did not run the consumer test")
    }

    private class Configurations(val main: List<String>, val test: List<String>)

    // The producer's build-local repository: the published files, read from disk.
    private class Repository(val root: Path) {
        private val group: Path = root.resolve("io/github/spartanlaboratories")

        // The single version directory under webtools-http/; a failure when there is not exactly one.
        fun version(): Result<String> = runCatching {
            val versions = Files.list(group.resolve("webtools-http")).use { dirs -> dirs.asSequence().filter { Files.isDirectory(it) }.map { it.fileName.toString() }.toList() }
            check(versions.size == 1) { "expected exactly one published version of webtools-http under $root, found $versions" }
            versions.single()
        }

        fun rootModuleDir(version: String): Path = group.resolve("webtools-http").resolve(version)
        fun jvmModuleDir(version: String): Path = group.resolve("webtools-http-jvm").resolve(version)
        fun allFiles(): List<Path> = Files.walk(root).use { paths -> paths.asSequence().filter { Files.isRegularFile(it) }.toList() }
    }

    private companion object {
        const val GROUP = "io.github.spartanlaboratories"

        // The fixtures' declared libraries: update with webtools-http/build.gradle.kts (the jvmTestFixturesApi lines).
        const val COROUTINES_TEST_VERSION = "1.10.2"
        const val MOCKWEBSERVER_VERSION = "5.5.0"
        val TEST_LIBRARIES = listOf("kotlinx-coroutines-test", "mockwebserver3")

        // The files that carry no signature of their own: the signatures and the checksums.
        val CHECKSUM_SUFFIXES = listOf(".asc", ".md5", ".sha1", ".sha256", ".sha512")
        val KMP_CONFIGURATIONS = Configurations(main = listOf("jvmCompileClasspath", "jvmRuntimeClasspath"), test = listOf("jvmTestCompileClasspath", "jvmTestRuntimeClasspath"))
        val JVM_CONFIGURATIONS = Configurations(main = listOf("compileClasspath", "runtimeClasspath"), test = listOf("testCompileClasspath", "testRuntimeClasspath"))
    }
}
