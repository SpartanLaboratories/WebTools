package com.spartanlabs.testing.support.webtools.buildlogic

/**
 * A read-only description of a generated Gradle project for a TestKit check: every file it holds,
 * keyed by its path relative to the project directory (forward slashes), with its full text.
 *
 * Rendering is pure: the factories, [withFile] and [withLocalPublishing] build new values and touch
 * no file system; [ProjectWorkspace.write] puts a sample on disk.
 *
 * @property files the project's files, relative path to content; a read-only map.
 */
class SampleProject private constructor(val files: Map<String, String>) {

    /**
     * Returns a copy of this project with [relativePath] set to [content], replacing any file
     * already at that path. This project is unchanged.
     *
     * @param relativePath the file's path relative to the project directory, with forward slashes.
     * @param content the file's full text.
     * @return the new project description.
     * @throws IllegalArgumentException if [relativePath] is blank.
     */
    fun withFile(relativePath: String, content: String): SampleProject {
        require(relativePath.isNotBlank()) { "A sample file needs a non-blank path" }
        return SampleProject(files + (relativePath to content))
    }

    /**
     * Returns a copy of this single-project sample whose build script also publishes to a folder
     * inside the project, `build/repo` (repository `testFolder`), names its POM, and disables its
     * `Sign` tasks. No signing key exists in a TestKit build, so a publish could not get past
     * `signAllPublications()` otherwise; the `Sign` tasks stay registered. This project is
     * unchanged.
     *
     * @return the new project description.
     * @throws NoSuchElementException if this sample has no root `build.gradle.kts`.
     */
    fun withLocalPublishing(): SampleProject =
        withFile("build.gradle.kts", files.getValue("build.gradle.kts") + LOCAL_PUBLISHING)

    companion object {
        //region constants
        /** The package-qualified name of the multiplatform sample's `commonTest` class, in the Level-2 package. */
        const val COMMON_TEST_CLASS: String = "com.spartanlabs.testing.component.sample.CommonSourceSetTest"

        /** The package-qualified name of the multiplatform sample's `jvmTest` class, in the Level-3 package. */
        const val JVM_TEST_CLASS: String = "com.spartanlabs.testing.integration.sample.JvmSourceSetTest"

        /** The package-qualified name of the test class outside every level package, in either single-project sample. */
        const val UNLEVELED_TEST_CLASS: String = "sample.UnleveledTest"

        /** The path of the multiplatform sample's one public `commonMain` class, `sample.Greeter`. */
        const val GREETER_PATH: String = "src/commonMain/kotlin/sample/Greeter.kt"

        /** The package-qualified name of the Kotlin/JVM sample's Level-2 test class. */
        const val JVM_COMPONENT_TEST_CLASS: String = "com.spartanlabs.testing.component.sample.JvmComponentTest"

        /** The package-qualified name of the Kotlin/JVM sample's Level-3 test class. */
        const val JVM_INTEGRATION_TEST_CLASS: String = "com.spartanlabs.testing.integration.sample.JvmIntegrationTest"

        /** The directory of the end-to-end composite's Kotlin/JVM module. */
        const val COMPOSITE_JVM_MODULE: String = "jvm-lib"

        /** The directory of the end-to-end composite's multiplatform module. */
        const val COMPOSITE_MULTIPLATFORM_MODULE: String = "kmp-lib"

        /** The package-qualified name of the composite's Kotlin/JVM module's Level-2 test class. */
        const val COMPOSITE_JVM_COMPONENT_TEST_CLASS: String = "com.spartanlabs.testing.component.sample.JvmLibComponentTest"

        /** The package-qualified name of the composite's multiplatform module's Level-2 test class. */
        const val COMPOSITE_MULTIPLATFORM_COMPONENT_TEST_CLASS: String = "com.spartanlabs.testing.component.sample.KmpLibComponentTest"

        /** The prefix of the line the composite's `printLevels` task prints, before the level ids. */
        const val LEVELS_LINE_PREFIX: String = "webtools-levels="

        /** The directory of the common-check sample's check module, which applies `webtools.kotlin-common-check`. */
        const val COMMON_CHECK_MODULE: String = "commoncheck"

        /** The directory of the common-check sample's shared source, which is not a Gradle project. */
        const val COMMON_CHECK_SHARED: String = "shared"

        /** The path of the common-check sample's shared common code, a `commonMain` file with one `expect` function. */
        const val COMMON_CHECK_COMMON_PATH: String = "$COMMON_CHECK_SHARED/src/commonMain/kotlin/sample/Platform.kt"

        /** The path of the common-check sample's `jsMain` file, which holds the `actual` of that function. */
        const val COMMON_CHECK_ACTUAL_PATH: String = "$COMMON_CHECK_MODULE/src/jsMain/kotlin/sample/PlatformJs.kt"
        //endregion

        //region factories
        /**
         * A minimal Kotlin Multiplatform project with a single `jvm()` target that applies only
         * `id("webtools.kotlin-multiplatform-library")` (no Kotlin plugin of its own, so the
         * injected plugin classpath holds the only Kotlin Gradle plugin).
         *
         * It holds `settings.gradle.kts` (the root project name and `mavenCentral()`),
         * `build.gradle.kts` (the plugin, `group = "sample.webtools"`, `version = "1.0.0"`),
         * `gradle.properties` (configuration cache on, file-system watching off, in-process Kotlin
         * compiler, nested daemon heap), the public class `sample.Greeter` in `commonMain`, and the
         * three test classes [COMMON_TEST_CLASS], [JVM_TEST_CLASS] and [UNLEVELED_TEST_CLASS].
         *
         * @param rootProjectName the root project's name, which also names the ABI dump
         *   `api/<rootProjectName>.api`.
         * @return the project description.
         * @throws IllegalArgumentException if [rootProjectName] is blank.
         */
        fun multiplatformJvm(rootProjectName: String): SampleProject {
            require(rootProjectName.isNotBlank()) { "A sample project needs a non-blank root project name" }
            return SampleProject(
                mapOf(
                    "settings.gradle.kts" to settings(rootProjectName),
                    "build.gradle.kts" to BUILD_SCRIPT,
                    "gradle.properties" to GRADLE_PROPERTIES,
                    GREETER_PATH to GREETER,
                    "src/commonTest/kotlin/com/spartanlabs/testing/component/sample/CommonSourceSetTest.kt" to COMMON_TEST,
                    "src/jvmTest/kotlin/com/spartanlabs/testing/integration/sample/JvmSourceSetTest.kt" to JVM_TEST,
                    "src/jvmTest/kotlin/sample/UnleveledTest.kt" to UNLEVELED_TEST,
                ),
            )
        }

        /**
         * A minimal Kotlin/JVM project that applies only `id("webtools.kotlin-jvm-library")` (no
         * Kotlin plugin of its own, so the injected plugin classpath holds the only Kotlin Gradle
         * plugin).
         *
         * It holds `settings.gradle.kts`, `build.gradle.kts` (the plugin, `group = "sample.webtools"`,
         * `version = "1.0.0"`), `gradle.properties`, the public class `sample.Greeter` in `main`, and
         * the three test classes [JVM_COMPONENT_TEST_CLASS], [JVM_INTEGRATION_TEST_CLASS] and
         * [UNLEVELED_TEST_CLASS] in `test`.
         *
         * @param rootProjectName the root project's name, which also names the published artifact.
         * @return the project description.
         * @throws IllegalArgumentException if [rootProjectName] is blank.
         */
        fun kotlinJvm(rootProjectName: String): SampleProject {
            require(rootProjectName.isNotBlank()) { "A sample project needs a non-blank root project name" }
            return SampleProject(
                mapOf(
                    "settings.gradle.kts" to settings(rootProjectName),
                    "build.gradle.kts" to JVM_BUILD_SCRIPT,
                    "gradle.properties" to GRADLE_PROPERTIES,
                    "src/main/kotlin/sample/Greeter.kt" to GREETER,
                    "src/test/kotlin/com/spartanlabs/testing/component/sample/JvmComponentTest.kt" to JVM_COMPONENT_TEST,
                    "src/test/kotlin/com/spartanlabs/testing/integration/sample/JvmIntegrationTest.kt" to JVM_INTEGRATION_TEST,
                    "src/test/kotlin/sample/UnleveledTest.kt" to UNLEVELED_TEST,
                ),
            )
        }

        /**
         * A two-module composite that reaches the convention plugins the way the repository does:
         * its `settings.gradle.kts` includes `build-logic` through
         * `pluginManagement { includeBuild("build-logic") }`. The `build-logic/` directory itself is
         * not part of this description; [BuildLogicSource.copyInto] adds it.
         *
         * Module [COMPOSITE_JVM_MODULE] applies `webtools.kotlin-jvm-library`, sets
         * `mavenPublishing { coordinates; pom }` through the generated accessor, and registers
         * `printLevels`, which prints [LEVELS_LINE_PREFIX] followed by the ids of
         * `WebToolsBuild.LEVELS`, comma-separated. Module [COMPOSITE_MULTIPLATFORM_MODULE] applies
         * `webtools.kotlin-multiplatform-library` and sets `mavenPublishing` the same way. Each module
         * holds `sample.Greeter` and one Level-2 test class. The root build script sets `group` and
         * `version` for all projects, as the repository's root build does.
         *
         * @param rootProjectName the composite's root project name.
         * @return the project description.
         * @throws IllegalArgumentException if [rootProjectName] is blank.
         */
        fun includedBuildComposite(rootProjectName: String): SampleProject {
            require(rootProjectName.isNotBlank()) { "A sample project needs a non-blank root project name" }
            return SampleProject(
                mapOf(
                    "settings.gradle.kts" to compositeSettings(rootProjectName),
                    "build.gradle.kts" to COMPOSITE_ROOT_BUILD_SCRIPT,
                    "gradle.properties" to GRADLE_PROPERTIES,
                    "$COMPOSITE_JVM_MODULE/build.gradle.kts" to COMPOSITE_JVM_BUILD_SCRIPT,
                    "$COMPOSITE_JVM_MODULE/src/main/kotlin/sample/Greeter.kt" to GREETER,
                    "$COMPOSITE_JVM_MODULE/src/test/kotlin/com/spartanlabs/testing/component/sample/JvmLibComponentTest.kt" to
                        COMPOSITE_JVM_COMPONENT_TEST,
                    "$COMPOSITE_MULTIPLATFORM_MODULE/build.gradle.kts" to COMPOSITE_MULTIPLATFORM_BUILD_SCRIPT,
                    "$COMPOSITE_MULTIPLATFORM_MODULE/src/commonMain/kotlin/sample/Greeter.kt" to GREETER,
                    "$COMPOSITE_MULTIPLATFORM_MODULE/src/commonTest/kotlin/com/spartanlabs/testing/component/sample/KmpLibComponentTest.kt" to
                        COMPOSITE_MULTIPLATFORM_COMPONENT_TEST,
                ),
            )
        }

        /**
         * A project with a check-only `js` module, shaped as the repository's check modules are:
         * module [COMMON_CHECK_MODULE] applies only `id("webtools.kotlin-common-check")` and adds the
         * sibling directory [COMMON_CHECK_SHARED]'s `commonMain` as a source directory of its own
         * `commonMain`. [COMMON_CHECK_SHARED] is not a Gradle project: it holds only common code, free
         * of JVM APIs, with one `expect` function whose `actual` is in the check module's `jsMain`.
         *
         * Its `settings.gradle.kts` declares no repositories, so the plugin's own `mavenCentral()` is
         * the only way the check module resolves the Kotlin standard library. Its `gradle.properties`
         * is the same as the other samples'. The root project applies no plugin and has no build file.
         *
         * @param rootProjectName the root project's name.
         * @return the project description.
         * @throws IllegalArgumentException if [rootProjectName] is blank.
         */
        fun commonCheck(rootProjectName: String): SampleProject {
            require(rootProjectName.isNotBlank()) { "A sample project needs a non-blank root project name" }
            return SampleProject(
                mapOf(
                    "settings.gradle.kts" to commonCheckSettings(rootProjectName),
                    "gradle.properties" to GRADLE_PROPERTIES,
                    "$COMMON_CHECK_MODULE/build.gradle.kts" to COMMON_CHECK_BUILD_SCRIPT,
                    COMMON_CHECK_COMMON_PATH to COMMON_CHECK_COMMON,
                    COMMON_CHECK_ACTUAL_PATH to COMMON_CHECK_ACTUAL,
                ),
            )
        }
        //endregion

        //region script templates
        private fun settings(rootProjectName: String): String =
            //language=kotlin
            """
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }

            rootProject.name = "$rootProjectName"
            """.trimIndent() + "\n"

        private fun compositeSettings(rootProjectName: String): String =
            //language=kotlin
            """
            pluginManagement {
                includeBuild("build-logic")
            }

            rootProject.name = "$rootProjectName"

            include(":$COMPOSITE_JVM_MODULE", ":$COMPOSITE_MULTIPLATFORM_MODULE")
            """.trimIndent() + "\n"

        //language=kotlin
        private val BUILD_SCRIPT: String = """
            plugins {
                id("webtools.kotlin-multiplatform-library")
            }

            group = "sample.webtools"
            version = "1.0.0"
            """.trimIndent() + "\n"

        //language=kotlin
        private val JVM_BUILD_SCRIPT: String = """
            plugins {
                id("webtools.kotlin-jvm-library")
            }

            group = "sample.webtools"
            version = "1.0.0"
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMPOSITE_ROOT_BUILD_SCRIPT: String = """
            allprojects {
                group = "sample.webtools"
                version = "1.0.0"
            }
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMPOSITE_JVM_BUILD_SCRIPT: String = """
            plugins {
                id("webtools.kotlin-jvm-library")
            }

            mavenPublishing {
                coordinates(group.toString(), "$COMPOSITE_JVM_MODULE", version.toString())
                pom {
                    name.set("Sample JVM library")
                    description.set("A TestKit end-to-end sample.")
                }
            }

            // A module script reads WebToolsBuild from build-logic, as later modules read its constants.
            tasks.register("printLevels") {
                val ids = WebToolsBuild.LEVELS.map { it.id }
                doLast { println("$LEVELS_LINE_PREFIX" + ids.joinToString(",")) }
            }
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMPOSITE_MULTIPLATFORM_BUILD_SCRIPT: String = """
            plugins {
                id("webtools.kotlin-multiplatform-library")
            }

            mavenPublishing {
                coordinates(group.toString(), "$COMPOSITE_MULTIPLATFORM_MODULE", version.toString())
                pom {
                    name.set("Sample multiplatform library")
                    description.set("A TestKit end-to-end sample.")
                }
            }
            """.trimIndent() + "\n"

        // Appended to a single-project sample's build script by withLocalPublishing().
        //language=kotlin
        private val LOCAL_PUBLISHING: String = "\n" + """
            // Publishes to a folder inside this project only; nothing reaches a remote repository.
            publishing {
                repositories {
                    maven {
                        name = "testFolder"
                        url = uri(layout.buildDirectory.dir("repo"))
                    }
                }
            }

            // No signing key exists in a TestKit build, so the Sign tasks that signAllPublications()
            // registers are disabled; they stay registered.
            tasks.withType<org.gradle.plugins.signing.Sign>().configureEach {
                enabled = false
            }

            mavenPublishing {
                pom {
                    name.set("Sample")
                    description.set("A TestKit sample.")
                }
            }
            """.trimIndent() + "\n"

        // TestKit ignores the user-level gradle.properties, so every setting the nested builds need is
        // written here:
        // - org.gradle.configuration-cache=true: the nested builds exercise the plugins' configuration-cache
        //   compatibility, which the maintainer's own builds rely on;
        // - org.gradle.vfs.watch=false: TestKit already forces it on Windows; it is written for every host,
        //   as vanniktech's own TestKit suite does;
        // - kotlin.compiler.execution.strategy=in-process: no separate Kotlin daemon outlives a test and holds
        //   files in a generated project on Windows;
        // - org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g: the Kotlin compiler runs inside the nested
        //   daemon; these values held through the cold and warm runs of the first measurement.
        //language=properties
        private val GRADLE_PROPERTIES: String = """
            org.gradle.configuration-cache=true
            org.gradle.vfs.watch=false
            kotlin.compiler.execution.strategy=in-process
            org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g
            """.trimIndent() + "\n"

        //language=kotlin
        private val GREETER: String = """
            package sample

            class Greeter {
                fun greet(name: String): String = "Hello, " + name
            }
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMMON_TEST: String = """
            package com.spartanlabs.testing.component.sample

            import sample.Greeter
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class CommonSourceSetTest {
                @Test
                fun greets() {
                    assertEquals("Hello, common", Greeter().greet("common"))
                }
            }
            """.trimIndent() + "\n"

        //language=kotlin
        private val JVM_TEST: String = """
            package com.spartanlabs.testing.integration.sample

            import sample.Greeter
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class JvmSourceSetTest {
                @Test
                fun greets() {
                    assertEquals("Hello, jvm", Greeter().greet("jvm"))
                }
            }
            """.trimIndent() + "\n"

        //language=kotlin
        private val JVM_COMPONENT_TEST: String = """
            package com.spartanlabs.testing.component.sample

            import sample.Greeter
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class JvmComponentTest {
                @Test
                fun greets() {
                    assertEquals("Hello, component", Greeter().greet("component"))
                }
            }
            """.trimIndent() + "\n"

        //language=kotlin
        private val JVM_INTEGRATION_TEST: String = """
            package com.spartanlabs.testing.integration.sample

            import sample.Greeter
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class JvmIntegrationTest {
                @Test
                fun greets() {
                    assertEquals("Hello, integration", Greeter().greet("integration"))
                }
            }
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMPOSITE_JVM_COMPONENT_TEST: String = """
            package com.spartanlabs.testing.component.sample

            import sample.Greeter
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class JvmLibComponentTest {
                @Test
                fun greets() {
                    assertEquals("Hello, jvm-lib", Greeter().greet("jvm-lib"))
                }
            }
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMPOSITE_MULTIPLATFORM_COMPONENT_TEST: String = """
            package com.spartanlabs.testing.component.sample

            import sample.Greeter
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class KmpLibComponentTest {
                @Test
                fun greets() {
                    assertEquals("Hello, kmp-lib", Greeter().greet("kmp-lib"))
                }
            }
            """.trimIndent() + "\n"

        //language=kotlin
        private val UNLEVELED_TEST: String = """
            package sample

            import kotlin.test.Test
            import kotlin.test.assertEquals

            class UnleveledTest {
                @Test
                fun greets() {
                    assertEquals("Hello, unleveled", Greeter().greet("unleveled"))
                }
            }
            """.trimIndent() + "\n"

        // No dependencyResolutionManagement: the plugin under test declares its own repositories.
        private fun commonCheckSettings(rootProjectName: String): String =
            //language=kotlin
            """
            rootProject.name = "$rootProjectName"

            include(":$COMMON_CHECK_MODULE")
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMMON_CHECK_BUILD_SCRIPT: String = """
            plugins {
                id("webtools.kotlin-common-check")
            }

            kotlin {
                sourceSets {
                    commonMain {
                        kotlin.srcDir("../$COMMON_CHECK_SHARED/src/commonMain/kotlin")
                    }
                }
            }
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMMON_CHECK_COMMON: String = """
            package sample

            expect fun platformName(): String

            fun describe(): String = "common code on " + platformName()
            """.trimIndent() + "\n"

        //language=kotlin
        private val COMMON_CHECK_ACTUAL: String = """
            package sample

            actual fun platformName(): String = "js"
            """.trimIndent() + "\n"
        //endregion
    }
}
