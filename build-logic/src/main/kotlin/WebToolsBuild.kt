import org.gradle.api.Project
import org.gradle.api.file.FileCollection
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
}
