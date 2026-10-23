import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register

/**
 * One level of the project's 5-level test hierarchy, as a level test task selects it.
 *
 * Tests of a level live under the package `com.spartanlabs.testing.<id>` in every module, so the
 * level's task selects them by package.
 *
 * @property id the package segment and task-name stem, for example `gating`; never blank.
 * @property description the human-readable level, for example `Level 1 - local pre-commit gating tests`.
 * @throws IllegalArgumentException if [id] is blank.
 */
data class TestLevel(val id: String, val description: String) {
    init { require(id.isNotBlank()) { "A test level needs a non-blank id" } }

    /** The level's task name: `gatingTest`, … `uatTest`. */
    val taskName: String get() = "${id}Test"

    /** The test filter that selects the level: `com.spartanlabs.testing.<id>.*`. */
    val packagePattern: String get() = "${WebToolsBuild.LEVEL_PACKAGE_ROOT}.$id.*"

    /** The level's [description] followed by a full stop, used as the task description. */
    val taskDescription: String get() = "$description."
}

/**
 * Constants and helpers shared by every WebTools module build, read by the convention plugins in
 * this build and, where a module needs them, by module build scripts.
 */
object WebToolsBuild {
    /** The package every level package sits under: `com.spartanlabs.testing.<level>`. */
    const val LEVEL_PACKAGE_ROOT: String = "com.spartanlabs.testing"

    /**
     * The Java release every published WebTools artifact targets: its class files are Java 11
     * bytecode (class-file major 55), it is compiled against the Java 11 API only, and the
     * `org.gradle.jvm.version` of its API and runtime variants is 11, whatever JDK runs the build.
     * Both library convention plugins read this one constant.
     */
    const val JAVA_FLOOR: Int = 11

    /** The task name `verifyJavaFloor`, registered by both library plugins and wired into `check`. */
    const val VERIFY_JAVA_FLOOR_TASK: String = "verifyJavaFloor"

    /** The seven levels of the test hierarchy, in order, as a read-only list. */
    val LEVELS: List<TestLevel> = listOf(
        TestLevel("gating", "Level 1 - local pre-commit gating tests"),
        TestLevel("component", "Level 2 - isolated component behaviour tests"),
        TestLevel("integration", "Level 3 - integration & external interface tests"),
        TestLevel("deterministic", "Level 4a - deterministic input-to-output tests"),
        TestLevel("e2e", "Level 4b - end-to-end system integration tests"),
        TestLevel("nonfunctional", "Level 4c - non-functional (robustness / security / performance) tests"),
        TestLevel("uat", "Level 5 - user-acceptance evaluation (manual; mostly @Disabled)"),
    )

    /**
     * Registers one plain [Test] task per level on [project]: `gatingTest` … `uatTest`.
     *
     * Each task runs on the JUnit Platform, belongs to the `verification` group, and selects its
     * tests by the level's package pattern. A level with no matching test passes
     * (`isFailOnNoMatchingTests = false`). The tasks are registered lazily and are not added to
     * `check`.
     *
     * @param project the module the tasks are registered on.
     * @param classesDirs the compiled test classes to scan, for example `sourceSets["test"].output.classesDirs`.
     * @param classpath the runtime classpath the tests run on.
     * @return the seven task providers, in level order, as a read-only list.
     * @throws org.gradle.api.InvalidUserDataException if a task with one of the level names already exists on [project].
     */
    fun registerLevelTasks(
        project: Project,
        classesDirs: FileCollection,
        classpath: FileCollection,
    ): List<TaskProvider<Test>> = LEVELS.map { level ->
        project.tasks.register<Test>(level.taskName) {
            group = "verification"
            description = level.taskDescription
            testClassesDirs = classesDirs
            this.classpath = classpath          // `this.` - the parameter would shadow the task property
            useJUnitPlatform()
            filter {
                includeTestsMatching(level.packagePattern)
                isFailOnNoMatchingTests = false
            }
        }
    }.also { project.logger.info("webtools: registered {} level test tasks in {}", it.size, project.path) }

    /**
     * The class-file major version of Java release [javaRelease]: the release number plus 44
     * (Java 8 is 52, Java 11 is 55, Java 23 is 67).
     *
     * @param javaRelease a Java release of 5 or newer, for example [JAVA_FLOOR].
     * @return the class-file major version.
     */
    fun classFileMajor(javaRelease: Int): Int = javaRelease + 44

    /**
     * Registers the [VerifyJavaFloor] task `verifyJavaFloor` on [project], checking the floor
     * [JAVA_FLOOR]. It checks the jars, every Gradle Module Metadata file the project generates and
     * the resolved runtime classpath. The caller wires it into `check`. On success the task writes its
     * one-line summary to `build/reports/java-floor/verifyJavaFloor.txt`; on failure it writes nothing.
     *
     * @param project the module the task is registered on.
     * @param jars the jars the module builds, for example
     *   `project.files(project.tasks.named<Jar>("jar").flatMap { it.archiveFile })`.
     * @param runtimeClasspath the module's resolved runtime classpath, for example the
     *   `runtimeClasspath` configuration.
     * @return the task provider.
     * @throws org.gradle.api.InvalidUserDataException if a task named `verifyJavaFloor` already exists on [project].
     */
    fun registerVerifyJavaFloor(
        project: Project,
        jars: FileCollection,
        runtimeClasspath: FileCollection,
    ): TaskProvider<VerifyJavaFloor> =
        project.tasks.register<VerifyJavaFloor>(VERIFY_JAVA_FLOOR_TASK) {
            group = "verification"
            description = "Verifies that the built jars, the Gradle Module Metadata and the runtime classpath hold to the Java $JAVA_FLOOR floor."
            javaFloor.set(JAVA_FLOOR)
            this.jars.from(jars)
            // Every publication's metadata, whichever task generates it and whenever the publication is
            // created (vanniktech and the Kotlin plugin create theirs after evaluation): the collection
            // is read when the task graph is built.
            moduleMetadata.from(project.tasks.withType(GenerateModuleMetadata::class.java))
            this.runtimeClasspath.from(runtimeClasspath)
            report.set(project.layout.buildDirectory.file("reports/java-floor/$VERIFY_JAVA_FLOOR_TASK.txt"))
        }
}
