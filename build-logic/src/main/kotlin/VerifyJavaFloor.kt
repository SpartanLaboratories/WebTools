import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Fails the build when a module's published bytes or its dependencies are above the Java floor.
 *
 * It reads the files a consumer would receive, not the compiler settings that should have produced
 * them. It fails when:
 * - a class in one of [jars] has a class-file major version above the floor's (55 for Java 11),
 *   ignoring `META-INF/versions/` entries;
 * - a library variant (`java-api` or `java-runtime`, not sources or javadoc) in one of
 *   [moduleMetadata] declares an `org.gradle.jvm.version` other than the floor;
 * - a class in [runtimeClasspath], that is in a dependency, is above the floor's major version, which
 *   Gradle's own attribute matching cannot reject for the many libraries that publish no metadata;
 * - there is nothing to check: no class in [jars] or no library variant in [moduleMetadata].
 *
 * The task is registered by `WebToolsBuild.registerVerifyJavaFloor`, and both library plugins wire
 * it into `check`. It writes a one-line summary to [report] when it passes, so an unchanged module
 * is not scanned again.
 */
abstract class VerifyJavaFloor : DefaultTask() {

    /** The Java release the artifacts must not exceed, for example 11. */
    @get:Input
    abstract val javaFloor: Property<Int>

    /** The jars the module builds; every `.class` entry outside `META-INF/versions/` is checked. */
    @get:Classpath
    abstract val jars: ConfigurableFileCollection

    /** The Gradle Module Metadata files the module generates, one per publication. */
    @get:InputFiles
    // Content plus file name is enough: each publication's metadata content is what is checked; the
    // directory only serves the failure label.
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val moduleMetadata: ConfigurableFileCollection

    /** The module's resolved runtime classpath: the jars and class directories a consumer loads. */
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    /** The summary written when the module holds to the floor. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    /**
     * Runs the checks and writes [report].
     *
     * @throws GradleException listing the violations (at most [MAX_LISTED]) when any check fails, or
     *   when an input file cannot be read, or when there is nothing to check.
     */
    @TaskAction
    fun verify() {
        // Remove the last run's success record first: a failing run must leave no stale report behind.
        report.get().asFile.delete()
        val floor = javaFloor.get()
        val maxMajor = WebToolsBuild.classFileMajor(floor)

        val own = scanAll(jars.files) { JavaFloorScan.classFiles(it, maxMajor) }
        val variants = scanAll(moduleMetadata.files) { JavaFloorScan.moduleMetadata(it, floor) }
        val dependencies = scanAll(runtimeClasspath.files) { JavaFloorScan.classFiles(it, maxMajor) }

        val violations = own.violations + variants.violations + dependencies.violations
        if (own.checked == 0) throw GradleException("$path: no class to verify in the module's jars: ${jars.files.map { it.name }}")
        if (variants.checked == 0) throw GradleException("$path: no library variant to verify in the Gradle Module Metadata: ${moduleMetadata.files.map { it.name }}")
        if (violations.isNotEmpty()) throw GradleException(failureMessage(floor, maxMajor, violations))

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "java floor $floor (class-file major <= $maxMajor): ${own.checked} classes in ${jars.files.size} jar(s), " +
                "${variants.checked} library variants in ${moduleMetadata.files.size} metadata file(s), " +
                "${dependencies.checked} classes on the runtime classpath\n",
        )
    }

    // Scans every file, unwrapping the Results here: an unreadable input fails the task.
    private fun scanAll(files: Set<File>, scan: (File) -> Result<FloorScan>): FloorScan =
        files.sortedBy { it.path }.map { file ->
            scan(file).getOrElse { throw GradleException("$path: cannot read $file: ${it.message}", it) }
        }.fold(FloorScan.EMPTY, FloorScan::plus)

    private fun failureMessage(floor: Int, maxMajor: Int, violations: List<String>): String =
        buildString {
            append("$path: the build breaks the Java $floor floor (class-file major $maxMajor, org.gradle.jvm.version $floor):")
            violations.take(MAX_LISTED).forEach { append("\n  - ").append(it) }
            if (violations.size > MAX_LISTED) append("\n  ... and ${violations.size - MAX_LISTED} more")
        }

    private companion object {
        /** How many violations the failure message lists before it counts the rest. */
        const val MAX_LISTED: Int = 20
    }
}
