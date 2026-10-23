# Plan: the Java 11 floor — `WebToolsBuild.JAVA_FLOOR`, the compile settings it drives, and `verifyJavaFloor`

- **Covers:** `SpartanLaboratories/WebTools#47` — *"webtools-udp 2.0.0 ships Java 23 bytecode but README
  claims JDK 11+"* (work level 2; labels `bug`, `documentation`, `level: 2`). A sub-issue of the level-4
  tracking issue `SpartanLaboratories/WebTools#55` (*HTTP client stack*). It blocks
  `SpartanLaboratories/WebTools#62` (`http-core`).
- **Architecture:** [`../architecture.md`](../architecture.md), unit 2 `java-floor` (§6.1 *Shared build
  logic*, §11 *Decomposition*, §12 *Risks*). Systems-level impact: [`../impact.md`](../impact.md) §7 and §8.
  Unit 1's as-built record: [`../61-build-logic/final-implementation.md`](../61-build-logic/final-implementation.md)
  and [`../61-build-logic/impact.md`](../61-build-logic/impact.md) §4. Research:
  [`research/README.md`](research/README.md) and [`research/java-11-floor.md`](research/java-11-floor.md).
- **Branch:** `fix/issue-47-java-floor`, cut from `master` at `36fd879`.
- **Commit:** `a96d969`, the fix commit, which carries this `plan.md` (so `git log --follow` binds the
  two). The other commits of the PR are listed in [`final-implementation.md`](final-implementation.md).
- **PR:** #76, merged as `3949b52`; closes #47.
- **What this plans:**
  - the Java 11 floor as one constant, `WebToolsBuild.JAVA_FLOOR = 11`, from which both library convention
    plugins set `jvmTarget`, `-Xjdk-release`, the Java compatibility settings and the `TargetJvmVersion`
    attribute;
  - a `verifyJavaFloor` task in `check` in both plugins, which fails on any published byte above the floor;
  - the removal of the ASM pin that exists only because class files followed the build JDK;
  - the versions `webtools-scraping` and `webtools-browser` `1.0.0a` and the floor noted in `webtools-udp`'s
    unreleased `2.0.0a`;
  - the README: the install line, the Java floor and the build JDK.
- **Status:** implemented and landed in PR #76. The as-built record is
  [`final-implementation.md`](final-implementation.md) and the impact is [`impact.md`](impact.md). The
  collapsed blocks below are the plan's versions; the nine qa pass 1 changes to them (seven comments and
  KDoc, two tests) are listed in the as-built record.
- **Versions:** `webtools-udp` stays `2.0.0a` (unreleased); `webtools-scraping` `1.0.0` → `1.0.0a`;
  `webtools-browser` `1.0.0` → `1.0.0a`. Nothing is published.
- **Baseline:** `master` at `36fd879`. Line references are to it.

The sections are short. The collapsed blocks hold the exact new files and diffs to apply, as built and
checked against this baseline.

## Requirements

Built on any JDK from 17 to 26, with this change in place:

1. **Bytecode.** Every class in the jar of a module on `webtools.kotlin-jvm-library` or
   `webtools.kotlin-multiplatform-library` is Java 11 bytecode, class-file major 55. For the three existing
   modules that is 98, 4 and 2 classes at `36fd879`.
2. **Metadata.** The module's Gradle Module Metadata declares `org.gradle.jvm.version: 11` on every library
   variant: `apiElements` and `runtimeElements` of a Kotlin/JVM module; `jvmApiElements-published` and
   `jvmRuntimeElements-published`, in the `jvm` module and in the root `kotlinMultiplatform` module, of a
   multiplatform one. The sources variant is not constrained.
3. **API surface.** The module compiles against the Java 11 API only, in `main` and `test` alike: a class added
   after Java 11, such as `java.util.HexFormat` (Java 17), does not resolve.
4. **One setting.** The floor is the constant `WebToolsBuild.JAVA_FLOOR = 11`. Both plugins read it, and no
   other production file names the number for this purpose.
5. **A guard.** `verifyJavaFloor` is registered by both plugins and is a dependency of `check`. It passes on
   the three modules and fails, naming the culprit, when:
   - a jar holds a class above major 55 outside `META-INF/versions/`;
   - a library variant of a generated `.module` file declares another `org.gradle.jvm.version`, or none;
   - a class on the resolved runtime classpath is above major 55 outside `META-INF/versions/`;
   - there is nothing to check: no class in the jars, or no library variant in the metadata.

   It is configuration-cache compatible and up to date when its inputs are unchanged.
6. **Nothing else moves.** `javap -protected` of every class of the three modules is identical to the same
   sources built without the floor. Each POM is identical except the version of the two legacy modules. Each
   `.module` file is identical except `org.gradle.jvm.version` (23 → 11) and the version in file names.
7. **Versions.** `webtools-scraping` and `webtools-browser` are `1.0.0a`; `webtools-udp` keeps `2.0.0a` and its
   note names the floor.
8. **README.** `Requires JDK 11 or newer` stays as written and is true for the lettered versions. The README
   says which releases carry the floor, says what the build JDK is, and its install block names only versions
   that are on Maven Central when this lands: `webtools-udp:2.0.0`, `webtools-scraping:1.0.0`,
   `webtools-browser:1.0.0`. The release that publishes the lettered versions moves the lines to them.
9. **Testing.** No test suite of `webtools-udp`, `webtools-scraping` or `webtools-browser` runs, before or
   after. The change compiles their sources to a lower target and alters none of them; a run of their suites on the
   build JDK would say nothing about Java 11.

## Root cause

No build file has ever set a Java 11 target, so the bytecode and the metadata follow the JDK that runs the
build, which on the maintainer's host is Oracle JDK 23.0.2.

- `build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts:6-7` states that the plugin sets no
  `jvmTarget` or toolchain, and `:14` is `// TODO(#47): the java floor region goes here.` The Kotlin Gradle
  plugin's `jvmTarget` and the Java plugin's `targetCompatibility` then default to the build JDK.
- `build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts:25-26` writes
  `JavaVersion.current().majorVersion.toInt()` into the `TargetJvmVersion` attribute (`// TODO(#47)`) and
  sets no compiler option, so the multiplatform `jvm()` target compiles to the build JDK as well.
- `:77-87` of the same file holds the ASM pin, which exists only to let the ABI validator read the major-67
  class files that follow.
- Measured at `36fd879` on Oracle JDK 23.0.2: the jars of `webtools-udp`, `webtools-scraping` and
  `webtools-browser` hold 98, 4 and 2 classes, every one major 67, and each `module.json` declares
  `org.gradle.jvm.version: 23` on `apiElements` and `runtimeElements`. The same holds on Maven Central for
  `webtools-udp` 1.0.0, 1.1.0 and 2.0.0, `webtools-scraping` 1.0.0 and `webtools-browser` 1.0.0, read on
  2026-10-22. On a JDK 11 the 98 classes of the baseline `webtools-udp` jar all fail with
  `UnsupportedClassVersionError` ("class file version 67.0 ... up to 55.0").
- History: the root build set `kotlin { jvmToolchain(23) }` from `d666c4c` (2025-09-16; `jvmToolchain(8)`
  before) until `4a7de34` (2026-08-23, *Publishing / 1.0.0 Release*) dropped it while rewriting the build for
  Maven Central. No commit ever set a Java 11 target. `README.md:29`, *"Requires JDK 11 or newer"*, was written
  in `74e24ac` (2026-09-03), after the toolchain line was gone.
- Nothing in `check` reads the produced bytes. The one test of the attribute expects whatever JDK runs the
  tests (`build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/PublishedJvmVersionAttributeIntegrationTest.kt:25`),
  so it could not fail.
- `jvmTarget` alone is not the fix. It lowers the class-file version but leaves the build JDK's API callable:
  on a JDK 21 or newer, `MutableList.removeFirst()` binds to `List.removeFirst` and compiles with no warning
  ([`research/java-11-floor.md`](research/java-11-floor.md) F1), which Java 11 to 20 lacks. The API limit comes
  from `-Xjdk-release`, which Kotlin does not apply unless `jvmTarget` is set to the same value.

## Fix

**Mechanism, not toolchain.** The floor is `jvmTarget = 11` plus `-Xjdk-release=11` on whatever JDK runs the
build, with `java { sourceCompatibility; targetCompatibility }` on the Kotlin/JVM plugin and the explicit
`TargetJvmVersion` attribute on the multiplatform one. No `jvmToolchain(11)`: it would need the foojay
resolver bump (#58), make every consumer's composite build need a JDK 11, and still not write the attribute
for a multiplatform target. Gradle 9.7.1 runs on JDK 17 to 26, so JDK 11 can only ever be a launcher, which is
a follow-up.

**Order of work (test-first).**

1. Write the tests that name no new production symbol: the two TestKit classes, `JarClasses`, and the edits to
   `PublishedJvmVersionAttributeIntegrationTest`, `IncludedBuildEndToEndTest` and the two
   configuration-cache tests. Run `./gradlew --continue :build-logic:integrationTest :build-logic:e2eTest
   :build-logic:nonfunctionalTest`. Expected red: the list under *Regression test*.
2. Add `JAVA_FLOOR`, `JavaFloorScan`, `VerifyJavaFloor`, `classFileMajor` and `registerVerifyJavaFloor`, with the
   three tests that name them (`JavaFloorScanComponentTest`, `RegisterVerifyJavaFloorComponentTest`,
   `WebToolsBuildJavaFloorDeterministicTest`; each fails to compile until its symbol exists). Run
   `:build-logic:test`.
3. Edit the two plugins. Run the three TestKit tasks again: green.
4. Edit the three module build files and the README.
5. Run the verification procedures A to G below.

**Changes by file.** The new files are created whole; the other files change as the diffs show.

| File | Change |
|---|---|
| `build-logic/src/main/kotlin/WebToolsBuild.kt` | adds `JAVA_FLOOR`, `VERIFY_JAVA_FLOOR_TASK`, `classFileMajor` and `registerVerifyJavaFloor` |
| `build-logic/src/main/kotlin/JavaFloorScan.kt` | new: the pure scans of jars, class directories and `.module` files, and the `FloorScan` result |
| `build-logic/src/main/kotlin/VerifyJavaFloor.kt` | new: the typed verification task |
| `build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts` | the `java floor` region replaces the `TODO(#47)`; the header drops its "follow the build JDK" sentence |
| `build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts` | `jvm {}` gains `compilerOptions`; the attribute takes `JAVA_FLOOR`; a `java floor` region registers the guard; the ASM pin region is removed |
| `webtools-scraping/build.gradle.kts`, `webtools-browser/build.gradle.kts` | `version = "1.0.0a"`, with a note |
| `webtools-udp/build.gradle.kts` | the `2.0.0a` note names the floor; `version` and the port lock are untouched |
| `README.md` | the install line, the Java floor and the build JDK (see the diff) |
| tests | see *Regression test* |

Unchanged: the root `build.gradle.kts`, `settings.gradle.kts`, `build-logic/build.gradle.kts` (the scan uses
`groovy.json` and the publishing API, both in `gradleApi()`; the new TestKit classes run under the existing
`integrationTest`, so no task and no `testKitHomeLock` is added), `gradle.properties`, every module source file.
New and edited Kotlin and Gradle files use LF (`.gitattributes`). `README.md` is CRLF in this checkout
(`core.autocrlf`, LF in the index): edit it with a tool that keeps its line endings.

**The guard.** `verifyJavaFloor` reads the files a consumer would receive, not the settings that should have
produced them:

- the module's own jar(s): every `.class` entry outside `META-INF/versions/` must have a class-file header
  with the magic number and a major version of at most `classFileMajor(JAVA_FLOOR)` = 55 (multi-release
  entries are what a newer JDK loads, so a Java 11 runtime never sees them);
- every `GenerateModuleMetadata` output of the project: each library variant (`org.gradle.usage` of
  `java-api` or `java-runtime`, `org.gradle.category` not `documentation`) must declare `org.gradle.jvm.version`
  equal to the floor;
- the resolved runtime classpath, scanned like a jar. Most of the dependencies publish no Gradle metadata, so
  Gradle's attribute matching cannot reject a Java 17 library; this scan can.

Error handling: `JavaFloorScan` returns `Result<FloorScan>`. A violation is a value in a successful scan; a
failure is an unreadable input (missing file, not a zip, not JSON, no `variants`). `VerifyJavaFloor` unwraps
at its one boundary and throws `GradleException` for a violation, an unreadable input, or nothing to check, so
a wiring mistake cannot pass silently. `registerVerifyJavaFloor` lets Gradle's `InvalidUserDataException`
propagate on a second registration, a programmer error. `FloorScan` is an immutable data class. The task logs
nothing: the failure message is the failure record (at most 20 lines, then a count) and the one-line `report`
file is the success record. The task is up to date when none of the jars, metadata or classpath changed.

**The ASM pin is removed.** Unit 1 pinned ASM 9.9.1 on the validator's worker classpath
(`bcv-rt-jvm-cp`) because the validator's ASM 9.6 rejects class files above Java 22, and a JDK 23 host emitted
major 67. With the floor every class is major 55, so the pin is inert, and it would keep a comment that is
false and a dependency on a configuration name internal to the validator (`bcv-rt-jvm-cp`) that a validator
upgrade may drop. `ApiValidationIntegrationTest`, which runs `apiDump` on a generated module, then guards the
removal on every build JDK: it passes without the pin on JDK 17 and 23, and `verifyJavaFloor` fails `check`
for any class above major 55.

**Production code.**

<details>
<summary><code>build-logic/src/main/kotlin/JavaFloorScan.kt</code> (new)</summary>

**`build-logic/src/main/kotlin/JavaFloorScan.kt`**

````kotlin
import groovy.json.JsonSlurper
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * What one scan looked at and what it found.
 *
 * @property checked how many things the scan examined: class files for [JavaFloorScan.classFiles],
 *   library variants for [JavaFloorScan.moduleMetadata].
 * @property violations one line per thing that breaks the floor; empty when everything holds.
 */
data class FloorScan(val checked: Int, val violations: List<String>) {

    /** The combination of this scan and [other]: both counts added, both violation lists joined. */
    operator fun plus(other: FloorScan): FloorScan = FloorScan(checked + other.checked, violations + other.violations)

    /** The neutral scan. */
    companion object {
        /** A scan that examined nothing and found nothing. */
        val EMPTY: FloorScan = FloorScan(0, emptyList())
    }
}

/**
 * The pure checks behind [VerifyJavaFloor]: they read a jar, a class directory or a Gradle Module
 * Metadata file and report what breaks the Java floor, touching no Gradle API.
 *
 * Both functions return a [Result]: a success carries the [FloorScan] (clean or not), and a failure
 * carries the I/O or parse error of a file that could not be read. [VerifyJavaFloor] unwraps both.
 */
object JavaFloorScan {

    private const val MULTI_RELEASE_PREFIX: String = "META-INF/versions/"
    private const val CLASS_MAGIC: Int = 0xCAFEBABE.toInt()
    private const val HEADER_BYTES: Int = 8
    private const val USAGE: String = "org.gradle.usage"
    private const val CATEGORY: String = "org.gradle.category"
    private const val JVM_VERSION: String = "org.gradle.jvm.version"

    /**
     * Checks the class-file version of every class in a jar or a class directory.
     *
     * An entry counts when its name ends in `.class` and is not under `META-INF/versions/`: the
     * classes there belong to a multi-release jar's newer JDKs, which a Java 11 runtime never loads.
     * Only the eight-byte header of each class is read.
     *
     * @param root a jar file or a directory of class files.
     * @param maxMajor the highest class-file major version allowed, for example 55 for Java 11.
     * @return the scan, with a violation for each class above [maxMajor] or without the class-file
     *   magic number; or a failure when [root] does not exist or cannot be read as a zip file.
     */
    fun classFiles(root: File, maxMajor: Int): Result<FloorScan> = runCatching {
        when {
            root.isDirectory -> scanDirectory(root, maxMajor)
            root.isFile -> scanJar(root, maxMajor)
            else -> throw FileNotFoundException("$root does not exist")
        }
    }

    /**
     * Checks the `org.gradle.jvm.version` of the library variants in a Gradle Module Metadata file.
     *
     * A library variant is one whose `org.gradle.usage` is `java-api` or `java-runtime` and whose
     * `org.gradle.category` is not `documentation`: the API and runtime variants, root-module
     * redirects included, but not the sources and javadoc variants, which carry no class files.
     * A variant with the attribute missing, not a number or not equal to [javaFloor] is a violation.
     *
     * @param file the `module.json` or `.module` file.
     * @param javaFloor the Java release every library variant must declare, for example 11.
     * @return the scan, one variant per count; or a failure when [file] is missing, is not JSON or
     *   has no `variants` list.
     */
    fun moduleMetadata(file: File, javaFloor: Int): Result<FloorScan> = runCatching {
        val root = JsonSlurper().parse(file) as? Map<*, *> ?: throw IllegalArgumentException("$file is not a JSON object")
        val variants = (root["variants"] as? List<*>)?.filterIsInstance<Map<*, *>>()
            ?: throw IllegalArgumentException("No 'variants' list in $file")
        val library = variants.filter { variant -> isLibraryVariant(attributesOf(variant)) }
        FloorScan(
            checked = library.size,
            violations = library.mapNotNull { variant ->
                val declared = (attributesOf(variant)[JVM_VERSION] as? Number)?.toInt()
                if (declared == javaFloor) null
                else "${label(file)}: variant '${variant["name"]}' declares $JVM_VERSION ${declared ?: "nothing"}, not $javaFloor"
            },
        )
    }

    // Gradle writes build/publications/<publication>/module.json, so the parent names the publication.
    private fun label(file: File): String = file.parentFile?.let { "${it.name}/${file.name}" } ?: file.name

    private fun attributesOf(variant: Map<*, *>): Map<*, *> = variant["attributes"] as? Map<*, *> ?: emptyMap<Any, Any>()

    private fun isLibraryVariant(attributes: Map<*, *>): Boolean =
        attributes[USAGE] in setOf("java-api", "java-runtime") && attributes[CATEGORY] != "documentation"

    private fun isCheckedClass(entryName: String): Boolean =
        entryName.endsWith(".class") && !entryName.startsWith(MULTI_RELEASE_PREFIX)

    private fun scanJar(jar: File, maxMajor: Int): FloorScan =
        ZipFile(jar).use { zip ->
            zip.entries().asSequence()
                .filter { entry -> !entry.isDirectory && isCheckedClass(entry.name) }
                .map { entry -> zip.getInputStream(entry).use { header(it) } to "${jar.name}!${entry.name}" }
                .toList()
                .let { classes -> scanned(classes, maxMajor) }
        }

    private fun scanDirectory(dir: File, maxMajor: Int): FloorScan =
        dir.walkTopDown()
            .filter { file -> file.isFile && isCheckedClass(file.relativeTo(dir).invariantSeparatorsPath) }
            .map { file -> file.inputStream().use { header(it) } to "${dir.name}/${file.relativeTo(dir).invariantSeparatorsPath}" }
            .toList()
            .let { classes -> scanned(classes, maxMajor) }

    private fun header(stream: InputStream): ByteArray = stream.readNBytes(HEADER_BYTES)

    private fun scanned(classes: List<Pair<ByteArray, String>>, maxMajor: Int): FloorScan =
        FloorScan(classes.size, classes.mapNotNull { (header, location) -> violation(location, header, maxMajor) })

    // A class file starts with the magic number (u4), the minor version (u2) and the major version (u2).
    private fun violation(location: String, header: ByteArray, maxMajor: Int): String? {
        val magic = if (header.size == HEADER_BYTES) u2(header, 0) shl 16 or u2(header, 2) else null
        val major = if (header.size == HEADER_BYTES) u2(header, 6) else null
        return when {
            magic != CLASS_MAGIC || major == null -> "$location: not a class file"
            major > maxMajor -> "$location: class-file major $major (Java ${major - 44}), above $maxMajor (Java ${maxMajor - 44})"
            else -> null
        }
    }

    private fun u2(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 0xFF shl 8) or (bytes[offset + 1].toInt() and 0xFF)
}
````

</details>

<details>
<summary><code>build-logic/src/main/kotlin/VerifyJavaFloor.kt</code> (new)</summary>

**`build-logic/src/main/kotlin/VerifyJavaFloor.kt`**

````kotlin
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
````

</details>

<details>
<summary><code>WebToolsBuild.kt</code> and the two convention plugins (diffs)</summary>

````diff
--- a/build-logic/src/main/kotlin/WebToolsBuild.kt
+++ b/build-logic/src/main/kotlin/WebToolsBuild.kt
@@ -1,4 +1,5 @@
 import org.gradle.api.Project
 import org.gradle.api.file.FileCollection
+import org.gradle.api.publish.tasks.GenerateModuleMetadata
 import org.gradle.api.tasks.TaskProvider
 import org.gradle.api.tasks.testing.Test
@@ -36,4 +37,15 @@ object WebToolsBuild {
     const val LEVEL_PACKAGE_ROOT: String = "com.spartanlabs.testing"
 
+    /**
+     * The Java release every published WebTools artifact targets: its class files are Java 11
+     * bytecode (class-file major 55), it is compiled against the Java 11 API only, and the
+     * `org.gradle.jvm.version` of its API and runtime variants is 11, whatever JDK runs the build.
+     * Both library convention plugins read this one constant.
+     */
+    const val JAVA_FLOOR: Int = 11
+
+    /** The task name `verifyJavaFloor`, registered by both library plugins and wired into `check`. */
+    const val VERIFY_JAVA_FLOOR_TASK: String = "verifyJavaFloor"
+
     /** The seven levels of the test hierarchy, in order, as a read-only list. */
     val LEVELS: List<TestLevel> = listOf(
@@ -78,3 +90,43 @@ object WebToolsBuild {
         }
     }.also { project.logger.info("webtools: registered {} level test tasks in {}", it.size, project.path) }
+
+    /**
+     * The class-file major version of Java release [javaRelease]: the release number plus 44
+     * (Java 8 is 52, Java 11 is 55, Java 23 is 67).
+     *
+     * @param javaRelease a Java release of 5 or newer, for example [JAVA_FLOOR].
+     * @return the class-file major version.
+     */
+    fun classFileMajor(javaRelease: Int): Int = javaRelease + 44
+
+    /**
+     * Registers the [VerifyJavaFloor] task `verifyJavaFloor` on [project], checking the floor
+     * [JAVA_FLOOR]. It checks the jars, every Gradle Module Metadata file the project generates and
+     * the resolved runtime classpath. The caller wires it into `check`.
+     *
+     * @param project the module the task is registered on.
+     * @param jars the jars the module builds, for example
+     *   `project.files(project.tasks.named<Jar>("jar").flatMap { it.archiveFile })`.
+     * @param runtimeClasspath the module's resolved runtime classpath, for example the
+     *   `runtimeClasspath` configuration.
+     * @return the task provider.
+     * @throws org.gradle.api.InvalidUserDataException if a task named `verifyJavaFloor` already exists on [project].
+     */
+    fun registerVerifyJavaFloor(
+        project: Project,
+        jars: FileCollection,
+        runtimeClasspath: FileCollection,
+    ): TaskProvider<VerifyJavaFloor> =
+        project.tasks.register<VerifyJavaFloor>(VERIFY_JAVA_FLOOR_TASK) {
+            group = "verification"
+            description = "Verifies that the built jars, the Gradle Module Metadata and the runtime classpath hold to the Java $JAVA_FLOOR floor."
+            javaFloor.set(JAVA_FLOOR)
+            this.jars.from(jars)
+            // Every publication's metadata, whichever task generates it and whenever the publication is
+            // created (vanniktech and the Kotlin plugin create theirs after evaluation): the collection
+            // is read when the task graph is built.
+            moduleMetadata.from(project.tasks.withType(GenerateModuleMetadata::class.java))
+            this.runtimeClasspath.from(runtimeClasspath)
+            report.set(project.layout.buildDirectory.file("reports/java-floor/$VERIFY_JAVA_FLOOR_TASK.txt"))
+        }
 }
````

````diff
--- a/build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts
+++ b/build-logic/src/main/kotlin/webtools.kotlin-jvm-library.gradle.kts
@@ -1,9 +1,11 @@
+import org.jetbrains.kotlin.gradle.dsl.JvmTarget
+
 // Convention plugin: a published Kotlin/JVM library module. Applies the Kotlin/JVM plugin and
-// webtools.published-library, adds the shared test dependencies, runs every Test task on the JUnit
+// webtools.published-library, compiles to the Java floor (WebToolsBuild.JAVA_FLOOR) and guards it
+// with verifyJavaFloor in check, adds the shared test dependencies, runs every Test task on the JUnit
 // Platform and registers the seven level test tasks over the `test` source set. A module adds its
 // dependencies (api(slf4j-api) included), its coordinates, its POM name and description, and any
 // configuration that belongs to it alone (for example webtools-udp's version and UDP-port lock).
-// explicitApi() is never set here. It sets no jvmTarget or toolchain: published class files and
-// org.gradle.jvm.version follow the build JDK until #47 adds the Java floor.
+// explicitApi() is never set here.
 
 plugins {
@@ -12,5 +14,35 @@ plugins {
 }
 
-// TODO(#47): the java floor region goes here.
+//region java floor
+// Every published class is Java 11 bytecode (class-file major 55) built against the Java 11 API, on
+// whatever JDK runs the build. jvmTarget lowers the class-file version only: a JDK 21 call such as
+// MutableList.removeFirst() still compiles, to a method Java 11 to 20 lacks, so -Xjdk-release limits
+// the API too. The flag fails without jvmTarget set to the same value, because the Kotlin Gradle
+// plugin always passes its own -jvm-target. The Java compatibility settings are required as well: the
+// plugin fails the build when compileJava's target differs from jvmTarget, and they are what write
+// org.gradle.jvm.version on the API and runtime variants. -Xjdk-release is an experimental flag that
+// Kotlin does not promise on every JDK distribution; verifyJavaFloor is the backstop.
+kotlin {
+    compilerOptions {
+        jvmTarget.set(JvmTarget.fromTarget(WebToolsBuild.JAVA_FLOOR.toString()))
+        freeCompilerArgs.add("-Xjdk-release=${WebToolsBuild.JAVA_FLOOR}")
+    }
+}
+
+java {
+    sourceCompatibility = JavaVersion.toVersion(WebToolsBuild.JAVA_FLOOR)
+    targetCompatibility = JavaVersion.toVersion(WebToolsBuild.JAVA_FLOOR)
+}
+
+val verifyJavaFloor = WebToolsBuild.registerVerifyJavaFloor(
+    project,
+    files(tasks.named<Jar>("jar").flatMap { it.archiveFile }),
+    configurations.getByName("runtimeClasspath"),
+)
+
+tasks.named("check") {
+    dependsOn(verifyJavaFloor)
+}
+//endregion
 
 //region test dependencies
````

````diff
--- a/build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts
+++ b/build-logic/src/main/kotlin/webtools.kotlin-multiplatform-library.gradle.kts
@@ -3,11 +3,13 @@ import com.vanniktech.maven.publish.KotlinMultiplatform
 import com.vanniktech.maven.publish.SourcesJar
 import org.gradle.api.attributes.java.TargetJvmVersion
+import org.jetbrains.kotlin.gradle.dsl.JvmTarget
 
 // Convention plugin: a published Kotlin Multiplatform library with a single jvm() target. Applies the
-// multiplatform plugin, webtools.published-library and binary-compatibility-validator; sets the
-// TargetJvmVersion attribute on the jvm target, the shared test dependencies, JUnit Platform, the
-// seven level test tasks over the jvm test compilation and a lifecycle `test` task. Each module adds
-// explicitApi(), its own dependencies, its coordinates, its POM name and description, and commits its
-// ABI dump (api/<Gradle project name>.api).
+// multiplatform plugin, webtools.published-library and binary-compatibility-validator; compiles the
+// jvm target to the Java floor (WebToolsBuild.JAVA_FLOOR), declares that floor as the TargetJvmVersion
+// attribute and guards it with verifyJavaFloor in check; adds the shared test dependencies, JUnit
+// Platform, the seven level test tasks over the jvm test compilation and a lifecycle `test` task. Each
+// module adds explicitApi(), its own dependencies, its coordinates, its POM name and description, and
+// commits its ABI dump (api/<Gradle project name>.api).
 // Declares no second JVM-based target: a second one moves the ABI dump to api/<target>/.
 // Kotlin Gradle plugin 2.2.0 prints a Gradle-10 deprecation of the `archives` configuration at jvm()
@@ -22,15 +24,18 @@ plugins {
 
 //region jvm target
-// The major version of the JDK running the build: what an unpinned Kotlin/JVM module declares today.
-// TODO(#47): replace with WebToolsBuild.JAVA_FLOOR when the Java floor lands.
-val targetJvmVersion: Int = JavaVersion.current().majorVersion.toInt()
-
+// The Java floor, as for the Kotlin/JVM plugin (see its java floor region): jvmTarget plus
+// -Xjdk-release, both from the one constant. A multiplatform jvm() target has no JavaCompile task, so
+// there are no Java compatibility settings to match.
 kotlin {
     jvm {
+        compilerOptions {
+            jvmTarget.set(JvmTarget.fromTarget(WebToolsBuild.JAVA_FLOOR.toString()))
+            freeCompilerArgs.add("-Xjdk-release=${WebToolsBuild.JAVA_FLOOR}")
+        }
         // A multiplatform jvm() target writes no org.gradle.jvm.version unless the attribute is set on
         // the target. KGP also copies the target's attributes onto every jvm compilation's classpath
-        // configurations, so this value is also the JVM version those classpaths request.
+        // configurations, so the floor is also the JVM version those classpaths request.
         attributes {
-            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetJvmVersion)
+            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, WebToolsBuild.JAVA_FLOOR)
         }
         val testCompilation = compilations.getByName("test")
@@ -75,16 +80,4 @@ tasks.register("test") {
 //endregion
 
-//region binary compatibility
-// binary-compatibility-validator 0.18.2 hard-codes ASM 9.6 on its worker classpath, which rejects
-// class files above Java 22. Until the Java 11 floor compiles main to major 55, a module built on
-// a JDK 23 host emits major 67 and apiDump fails. ASM 9.9 is the line that reads Java 26 class
-// files (the newest JDK that runs Gradle 9.7.1); 9.9.1 is its bug-fix release. Once the floor
-// lands this pin is inert: Gradle takes the highest version on the configuration.
-dependencies {
-    "bcv-rt-jvm-cp"("org.ow2.asm:asm:9.9.1")
-    "bcv-rt-jvm-cp"("org.ow2.asm:asm-tree:9.9.1")
-}
-//endregion
-
 //region publishing
 mavenPublishing {
@@ -94,2 +87,21 @@ mavenPublishing {
 }
 //endregion
+
+//region java floor
+// verifyJavaFloor reads the jvm jar, the generated Gradle Module Metadata and the jvm runtime
+// classpath. A module that adds another published jar (the test-fixtures jar) adds it to the task's
+// `jars`.
+val verifyJavaFloor = WebToolsBuild.registerVerifyJavaFloor(
+    project,
+    files(tasks.named<Jar>("jvmJar").flatMap { it.archiveFile }),
+    // runtimeDependencyFiles is nullable in the KGP API. The main jvm compilation always has it, and the
+    // guard must never run blind on an empty runtime classpath, so a missing one fails the build here.
+    checkNotNull(kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles) {
+        "the jvm main compilation has no runtime classpath for verifyJavaFloor to scan"
+    },
+)
+
+tasks.named("check") {
+    dependsOn(verifyJavaFloor)
+}
+//endregion
````

</details>

<details>
<summary>The three module build files (diffs)</summary>

````diff
--- a/webtools-udp/build.gradle.kts
+++ b/webtools-udp/build.gradle.kts
@@ -110,4 +110,8 @@ dependencies {
 // HandshakeParse outcome. No public API change; on the wire only the three malformed shapes
 // change, from silence or REGISTERED 2 to REFUSED malformed.
+// The same 2.0.0a also carries the Java 11 floor (Issue #47). The class files are Java 11 bytecode
+// (major 55) built against the Java 11 API, and the Gradle Module Metadata declares
+// org.gradle.jvm.version 11, where 2.0.0 shipped Java 23 bytecode (major 67) declaring 23, which
+// consumers on JDK 11 to 22 could not load. No source or public API change.
 version = "2.0.0a"
 
````

````diff
--- a/webtools-scraping/build.gradle.kts
+++ b/webtools-scraping/build.gradle.kts
@@ -14,4 +14,12 @@ dependencies {
 }
 
+// Explicit per-module version, overriding the root allprojects { version = "1.0.0" }.
+// 1.0.0a: the bugfix after 1.0.0 - the Java 11 floor (Issue #47). The class files are Java 11
+// bytecode (major 55) built against the Java 11 API, and the Gradle Module Metadata declares
+// org.gradle.jvm.version 11, where 1.0.0 shipped Java 23 bytecode (major 67) declaring 23, which
+// consumers on JDK 11 to 22 could not load. No source or public API change. Gradle orders 1.0.0a
+// below 1.0.0, so a consumer that also asks for plain 1.0.0 requests 1.0.0a strictly.
+version = "1.0.0a"
+
 mavenPublishing {
     coordinates(group.toString(), "webtools-scraping", version.toString())
````

````diff
--- a/webtools-browser/build.gradle.kts
+++ b/webtools-browser/build.gradle.kts
@@ -13,4 +13,12 @@ dependencies {
 }
 
+// Explicit per-module version, overriding the root allprojects { version = "1.0.0" }.
+// 1.0.0a: the bugfix after 1.0.0 - the Java 11 floor (Issue #47). The class files are Java 11
+// bytecode (major 55) built against the Java 11 API, and the Gradle Module Metadata declares
+// org.gradle.jvm.version 11, where 1.0.0 shipped Java 23 bytecode (major 67) declaring 23, which
+// consumers on JDK 11 to 22 could not load. No source or public API change. Gradle orders 1.0.0a
+// below 1.0.0, so a consumer that also asks for plain 1.0.0 requests 1.0.0a strictly.
+version = "1.0.0a"
+
 mavenPublishing {
     coordinates(group.toString(), "webtools-browser", version.toString())
````

</details>

<details>
<summary><code>README.md</code> (diff)</summary>

````diff
--- a/README.md
+++ b/README.md
@@ -20,5 +20,5 @@ Plan boards: the [work board](docs/plans/index.html) shows every planning and de
 ```kotlin
 dependencies {
-    implementation("io.github.spartanlaboratories:webtools-udp:2.0.0a")
+    implementation("io.github.spartanlaboratories:webtools-udp:2.0.0")
     // and/or
     implementation("io.github.spartanlaboratories:webtools-scraping:1.0.0")
@@ -29,4 +29,13 @@ dependencies {
 Requires JDK 11 or newer. Built with Kotlin 2.2.
 
+The Java 11 floor starts with `webtools-udp` 2.0.0a and `webtools-scraping` / `webtools-browser`
+1.0.0a: Java 11 bytecode (class-file major 55), compiled against the Java 11 API. The releases
+already on Maven Central — `webtools-udp` 1.0.0, 1.1.0 and 2.0.0, `webtools-scraping` 1.0.0 and
+`webtools-browser` 1.0.0 — are Java 23 bytecode and fail to load on JDK 22 or older with
+`UnsupportedClassVersionError`. Gradle orders a lettered version below the same version without
+the letter (`1.0.0a` below `1.0.0`, `2.0.0a` below `2.0.0`), so a dependency graph that also asks
+for the plain version keeps the Java 23 build unless it requests the lettered version
+`strictly`. Building the repository needs a JDK 17 to 26; see [Java floor](#java-floor).
+
 `webtools-udp 2.0.0` is a **wire break** for everything after the handshake — see [Migrating to
 2.0](#migrating-to-20). Both ends of a connection must be on `2.0.0`+; a `1.x` ↔ `2.x` pairing
@@ -481,4 +490,5 @@ The repo is a Gradle multi-module build; the three modules are `:webtools-udp`,
 ./gradlew nonfunctionalTest        # Level 4c - robustness / security properties
 ./gradlew uatTest                  # Level 5  - manual acceptance (mostly @Disabled)
+./gradlew verifyJavaFloor          # the Java 11 floor of every module's jars, metadata and runtime classpath (part of check)
 ./gradlew :build-logic:test        # build-logic's pre-commit gate: its gating, component and deterministic checks
 ./gradlew :build-logic:integrationTest :build-logic:e2eTest :build-logic:nonfunctionalTest  # on demand: TestKit checks
@@ -491,4 +501,16 @@ gated on its own; a test class outside those packages runs under `test` only. Sh
 under `testing/support/`.
 
+### Java floor
+
+Every module compiles to Java 11 bytecode against the Java 11 API, which is what the install
+section's `Requires JDK 11 or newer` rests on. Building needs a JDK 17 to 26, the range Gradle
+9.7.1 runs on, and the level tests run on that JDK too; no JDK 11 is involved. The convention
+plugins set `jvmTarget`, `-Xjdk-release` and the Java compatibility from one constant,
+`WebToolsBuild.JAVA_FLOOR`, so the result does not depend on which of those JDKs builds.
+`verifyJavaFloor`, part of `check`, fails the build when a built jar holds a class above Java 11
+bytecode, when a library variant of the generated Gradle Module Metadata declares another
+`org.gradle.jvm.version`, or when a runtime dependency holds such a class. Running
+`./gradlew verifyJavaFloor` checks every module without running any module's tests.
+
 ### Shared build logic
 
@@ -500,6 +522,6 @@ plugins, which a module applies by id in its `plugins {}` block:
 |---|---|
 | `webtools.published-library` | the Maven Central publishing skeleton: signing and the shared POM metadata |
-| `webtools.kotlin-jvm-library` | a Kotlin/JVM library: test dependencies, JUnit Platform, the level test tasks; applies `webtools.published-library` |
-| `webtools.kotlin-multiplatform-library` | a Kotlin Multiplatform library with a `jvm()` target (no module uses it yet); applies binary-compatibility-validator — commit `api/<module>.api` — and sets the `TargetJvmVersion` attribute; applies `webtools.published-library` |
+| `webtools.kotlin-jvm-library` | a Kotlin/JVM library: the Java floor and `verifyJavaFloor`, test dependencies, JUnit Platform, the level test tasks; applies `webtools.published-library` |
+| `webtools.kotlin-multiplatform-library` | a Kotlin Multiplatform library with a `jvm()` target (no module uses it yet): the Java floor, the `TargetJvmVersion` attribute and `verifyJavaFloor`; applies binary-compatibility-validator — commit `api/<module>.api` — and `webtools.published-library` |
 
 Change shared build behaviour there, not in a module's build file: a module's `build.gradle.kts`
````

</details>

**Documentation impact.** KDoc on every new public declaration (component ring), the region comments in the
two plugins and the module version notes (in-editor ring), the README (outer ring). `docs/dashboards/` still
draws the floor as planned; the admin team's presenter brings it to the as-built state at landing. Outside
`docs/dashboards/` and `docs/plans/`, no document names the JDK floor.

## Regression test

**The regression test of record** is `PublishedJvmVersionAttributeIntegrationTest`, Level 3, package
`com.spartanlabs.testing.integration.webtools.buildlogic`, in
`build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/PublishedJvmVersionAttributeIntegrationTest.kt`.
Its expectation changes from the feature version of the JVM that runs the tests to the floor:

```kotlin
private val expectedJvmVersion: Int = 11
```

The assertion, unchanged, is `assertEquals(Result.success(expectedJvmVersion), jvmMetadata.jvmVersion(variant), variant)`
for `jvmApiElements-published` and `jvmRuntimeElements-published`, in the `jvm` module and in the root module
(`rootMetadata`). **Today** both tests fail: `jvmApiElements-published ==> expected: <Success(11)> but was:
<Success(23)>`. **After the change** they pass. The literal 11 is an oracle independent of
`WebToolsBuild.JAVA_FLOOR`; `WebToolsBuildJavaFloorDeterministicTest` pins the constant itself.

**Every new or changed test.** All run in `build-logic`; none starts a module's tests.

| Level | Test (file under `build-logic/src/test/kotlin/com/spartanlabs/testing/`) | Tests | Asserts | Red today because |
|---|---|---|---|---|
| 3 | `integration/webtools/buildlogic/PublishedJvmVersionAttributeIntegrationTest.kt` (changed) | 2 | the `jvm` and root metadata declare 11 on both `jvm*Elements-published` variants | the attribute is the build JDK's version, 23 |
| 3 | `integration/webtools/buildlogic/JvmJavaFloorIntegrationTest.kt` (new) | 5 | Kotlin/JVM sample: the jar's classes are `{sample/Greeter.class=55}`; `apiElements` and `runtimeElements` declare 11; `HexFormat` does not resolve (`:compileKotlin` FAILED, output holds `Unresolved reference 'HexFormat'`); `:verifyJavaFloor` is in the `check` dry run and ends SUCCESS; with a class header of major 61 added to the jar it ends FAILED and the output holds `sample-1.0.0.jar!TooNew.class: class-file major 61 (Java 17), above 55 (Java 11)` | 67 and 23 are built; `HexFormat` compiles; no such task |
| 3 | `integration/webtools/buildlogic/MultiplatformJavaFloorIntegrationTest.kt` (new) | 6 | multiplatform sample: the `jvm` jar's classes are `{sample/Greeter.class=55}`; each of `jvmCompileClasspath`, `jvmRuntimeClasspath`, `jvmTestCompileClasspath` and `jvmTestRuntimeClasspath` requests `org.gradle.jvm.version=11` (the open point of unit 1); `HexFormat` does not resolve in `jvmMain`; `verifyJavaFloor` as above; with the target attribute set to 17 after the plugin it ends FAILED with `jvm/module.json: variant 'jvmApiElements-published' declares org.gradle.jvm.version 17, not 11` | 67; the classpaths request 23; no such task |
| 3 | `support/webtools/buildlogic/JarClasses.kt` (new) | — | the oracle that reads class-file majors from a jar; shares no code with the production scan | — |
| 3 | `integration/webtools/buildlogic/ApiValidationIntegrationTest.kt` (KDoc only) | 5, unchanged | the sentence about the ASM pin goes: the sample is major 55 on any JDK | — |
| 4b | `e2e/webtools/buildlogic/IncludedBuildEndToEndTest.kt` (changed) | 5 (one new) | through the real `includeBuild`, `:jvm-lib:verifyJavaFloor` and `:kmp-lib:verifyJavaFloor` are in the `build` dry run and end SUCCESS | no such task |
| 4c | `nonfunctional/webtools/buildlogic/ConfigurationCacheReuseNonFunctionalTest.kt` and `JvmConfigurationCacheReuseNonFunctionalTest.kt` (changed) | 1 + 1 | `componentTest` and `verifyJavaFloor` twice: the second run prints `Reusing configuration cache` | no such task |
| 2 | `component/webtools/buildlogic/JavaFloorScanComponentTest.kt` (new) | 16 | jars, class directories and `.module` files written by the test: at or below the floor is clean; a class above it is reported with jar, entry and both releases; `META-INF/versions/` entries and non-class entries are not counted; a bad magic number or a short header is reported; a missing file and a non-zip are failures; metadata: library variants at 11 are clean and a `documentation` variant is skipped, another version and a missing attribute are reported with the publication directory, another usage is skipped, no `variants` and non-JSON are failures; `FloorScan` adds | does not compile |
| 2 | `component/webtools/buildlogic/RegisterVerifyJavaFloorComponentTest.kt` (new) | 4 | on a `ProjectBuilder` project: the task is `verifyJavaFloor` in `verification`; it carries the floor, the jars, the classpath and a report under `build/reports/java-floor/`; it picks up the metadata task of a publication created after it; a second registration fails with `InvalidUserDataException` | does not compile |
| 4a | `deterministic/webtools/buildlogic/WebToolsBuildJavaFloorDeterministicTest.kt` (new) | 3 | `JAVA_FLOOR` is 11 and maps to 55; Java 8, 11, 17, 21, 23 map to 52, 55, 61, 65, 67; the task name is `verifyJavaFloor` | does not compile |
| 1 | `gating/…/BuildLogicPluginsGatingTest.kt` | 2, unchanged | — | — |
| 5 | manual: procedure D | — | the three jars load on a real JDK 11 | — |

Red today, measured on the baseline production code with the TestKit rows applied: the two changed
attribute tests, all 11 tests of the two new TestKit classes, 2 of the 5 end-to-end tests, and the 2
configuration-cache tests (their set-up fails, the task being unknown): 17 failures. The three component and
deterministic classes do not compile. After the change, `:build-logic:test` runs 38 tests (15 before),
`:build-logic:integrationTest` 39 (28), `:build-logic:e2eTest` 5 (4) and `:build-logic:nonfunctionalTest` 2:
84 in all, from 49, with none failing.

**Why `HexFormat`.** The API-surface tests need a class that every JDK Gradle runs on has and Java 11 lacks.
`java.util.HexFormat` is new in Java 17. `String.indent` (Java 12) is not an oracle: Kotlin does not expose
the JDK-only members of `String`, so it is unresolved on every JDK, with or without the flag.
The builds whose outcome is under test run with `NestedBuild.run`, so each test fails on its own assertion
while the floor is missing rather than all of them in the set-up.

<details>
<summary>Test sources: the support class and the two TestKit classes (new)</summary>

**`build-logic/src/test/kotlin/com/spartanlabs/testing/support/webtools/buildlogic/JarClasses.kt`**

````kotlin
package com.spartanlabs.testing.support.webtools.buildlogic

import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Reads the class-file major versions of the classes in a jar a nested build produced, as an
 * independent oracle for the floor: it shares no code with the production scan it checks.
 */
object JarClasses {

    /**
     * The class-file major version of every class entry of [jar], read from the entry's header
     * (bytes 6 and 7). Entries under `META-INF/versions/` are included, so a multi-release jar shows
     * its newer-release classes too.
     *
     * @param jar the jar file.
     * @return the entry name mapped to its major version (55 is Java 11), as a read-only map; or a
     *   failure when the file is missing, is not a zip file, or holds a class entry shorter than
     *   eight bytes.
     */
    fun majors(jar: Path): Result<Map<String, Int>> = runCatching {
        ZipFile(jar.toFile()).use { zip ->
            zip.entries().asSequence()
                .filter { entry -> !entry.isDirectory && entry.name.endsWith(".class") }
                .associate { entry ->
                    val header = zip.getInputStream(entry).use { it.readNBytes(8) }
                    check(header.size == 8) { "${entry.name} is shorter than a class-file header" }
                    entry.name to ((header[6].toInt() and 0xFF) shl 8 or (header[7].toInt() and 0xFF))
                }
        }
    }
}
````

**`build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/JvmJavaFloorIntegrationTest.kt`**

````kotlin
package com.spartanlabs.testing.integration.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.JarClasses
import com.spartanlabs.testing.support.webtools.buildlogic.ModuleMetadata
import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Level 3 - TestKit check of the Java 11 floor in `webtools.kotlin-jvm-library`, the plugin the
 * published Kotlin/JVM modules apply. A generated module built on the build JDK must publish:
 * - Java 11 bytecode (class-file major 55) in its jar;
 * - `org.gradle.jvm.version: 11` on its API and runtime variants;
 * - and compile against the Java 11 API only, so a use of a class added after Java 11 fails to compile.
 *
 * `verifyJavaFloor` must be part of `check`, pass on that module, and fail, naming the class, when
 * the jar holds a class above the floor. Only the generated modules' jar, metadata and compile
 * tasks run; nothing is published or signed.
 *
 * The builds whose outcome is under test run with [NestedBuild.run], so that each test fails on its
 * own assertion, and not all of them in the set-up, while the floor or the guard is missing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JvmJavaFloorIntegrationTest {

    private lateinit var floorWorkspace: ProjectWorkspace
    private lateinit var apiWorkspace: ProjectWorkspace
    private lateinit var tooNewWorkspace: ProjectWorkspace
    private lateinit var verifyResult: BuildResult
    private lateinit var checkDryRun: BuildResult
    private lateinit var apiResult: BuildResult
    private lateinit var tooNewResult: BuildResult
    private lateinit var metadata: ModuleMetadata

    @BeforeAll
    fun runBuilds() {
        floorWorkspace = ProjectWorkspace.create("jfl").getOrThrow()
        floorWorkspace.write(SampleProject.kotlinJvm("sample")).getOrThrow()
        NestedBuild(floorWorkspace).also { build ->
            build.build("jar", "generateMetadataFileForMavenPublication")
            verifyResult = build.run("verifyJavaFloor")
            checkDryRun = build.build("check", "--dry-run")
        }
        metadata = ModuleMetadata.parse(floorWorkspace.resolve("build/publications/maven/module.json")).getOrThrow()

        // java.util.HexFormat is new in Java 17, so it resolves on every build JDK Gradle supports (17 or
        // newer) unless the Java API is limited to 11.
        apiWorkspace = ProjectWorkspace.create("jfa").getOrThrow()
        apiWorkspace.write(
            SampleProject.kotlinJvm("sample").withFile(
                "src/main/kotlin/sample/Hex.kt",
                //language=kotlin
                """
                package sample

                import java.util.HexFormat

                fun hex(bytes: ByteArray): String = HexFormat.of().formatHex(bytes)
                """.trimIndent() + "\n",
            ),
        ).getOrThrow()
        apiResult = NestedBuild(apiWorkspace).run("compileKotlin")

        tooNewWorkspace = ProjectWorkspace.create("jft").getOrThrow()
        val sample = SampleProject.kotlinJvm("sample")
        tooNewWorkspace.write(sample.withFile("build.gradle.kts", sample.files.getValue("build.gradle.kts") + ADD_CLASS_ABOVE_FLOOR)).getOrThrow()
        tooNewResult = NestedBuild(tooNewWorkspace).run("verifyJavaFloor")
    }

    @AfterAll
    fun closeWorkspaces() {
        floorWorkspace.close()
        apiWorkspace.close()
        tooNewWorkspace.close()
    }

    @Test
    fun `every class in the jar is Java 11 bytecode`() {
        val majors = JarClasses.majors(floorWorkspace.resolve("build/libs/sample-1.0.0.jar")).getOrThrow()
        assertEquals(mapOf("sample/Greeter.class" to 55), majors)
    }

    @Test
    fun `the module metadata declares Java 11 on the API and runtime variants`() {
        listOf("apiElements", "runtimeElements").forEach { variant ->
            assertEquals(Result.success(11), metadata.jvmVersion(variant), variant)
        }
    }

    @Test
    fun `a class added after Java 11 does not resolve`() {
        assertEquals(TaskOutcome.FAILED, apiResult.task(":compileKotlin")?.outcome)
        assertTrue(apiResult.output.contains("Unresolved reference 'HexFormat'"), "no unresolved-reference error:\n${apiResult.output}")
    }

    @Test
    fun `check depends on verifyJavaFloor and it passes`() {
        assertTrue(checkDryRun.output.lineSequence().any { it.startsWith(":verifyJavaFloor ") }, ":verifyJavaFloor not in the check dry run")
        assertEquals(TaskOutcome.SUCCESS, verifyResult.task(":verifyJavaFloor")?.outcome)
    }

    @Test
    fun `verifyJavaFloor fails and names a class above the floor`() {
        assertEquals(TaskOutcome.FAILED, tooNewResult.task(":verifyJavaFloor")?.outcome)
        assertTrue(tooNewResult.output.contains("sample-1.0.0.jar!TooNew.class: class-file major 61 (Java 17), above 55 (Java 11)"), tooNewResult.output)
    }

    private companion object {
        // Adds to the jar a file that carries a class-file header for major 61 (Java 17): the magic
        // number 0xCAFEBABE, minor 0, major 61. The check reads headers only.
        //language=kotlin
        val ADD_CLASS_ABOVE_FLOOR: String = "\n" + """
            val classAboveFloor = tasks.register("writeClassAboveFloor") {
                val out = layout.buildDirectory.file("above-floor/TooNew.class")
                outputs.file(out)
                doLast {
                    out.get().asFile.apply { parentFile.mkdirs() }
                        .writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0, 0, 0, 61))
                }
            }

            tasks.named<Jar>("jar") {
                from(classAboveFloor)
            }
            """.trimIndent() + "\n"
    }
}
````

**`build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/MultiplatformJavaFloorIntegrationTest.kt`**

````kotlin
package com.spartanlabs.testing.integration.webtools.buildlogic

import com.spartanlabs.testing.support.webtools.buildlogic.JarClasses
import com.spartanlabs.testing.support.webtools.buildlogic.NestedBuild
import com.spartanlabs.testing.support.webtools.buildlogic.ProjectWorkspace
import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Level 3 - TestKit check of the Java 11 floor in `webtools.kotlin-multiplatform-library`. A
 * generated module built on the build JDK must publish Java 11 bytecode (class-file major 55) in its
 * `jvm` jar and compile against the Java 11 API only. The published metadata is checked in
 * [PublishedJvmVersionAttributeIntegrationTest].
 *
 * KGP copies the `jvm` target's attributes onto every compilation's classpath configurations, so the
 * floor is also the JVM version each of those classpaths requests: a dependency that declares a
 * higher one is not selected.
 *
 * `verifyJavaFloor` must be part of `check`, pass on that module, and fail, naming the culprit, when
 * the jar holds a class above the floor or a variant declares another version. Only the generated
 * modules' jar, metadata and compile tasks run; nothing is published or signed.
 *
 * The builds whose outcome is under test run with [NestedBuild.run], so that each test fails on its
 * own assertion, and not all of them in the set-up, while the floor or the guard is missing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiplatformJavaFloorIntegrationTest {

    private val classpaths: List<String> =
        listOf("jvmCompileClasspath", "jvmRuntimeClasspath", "jvmTestCompileClasspath", "jvmTestRuntimeClasspath")

    private lateinit var floorWorkspace: ProjectWorkspace
    private lateinit var apiWorkspace: ProjectWorkspace
    private lateinit var tooNewWorkspace: ProjectWorkspace
    private lateinit var variantWorkspace: ProjectWorkspace
    private lateinit var verifyResult: BuildResult
    private lateinit var classpathResult: BuildResult
    private lateinit var checkDryRun: BuildResult
    private lateinit var apiResult: BuildResult
    private lateinit var tooNewResult: BuildResult
    private lateinit var variantResult: BuildResult

    @BeforeAll
    fun runBuilds() {
        floorWorkspace = ProjectWorkspace.create("mfl").getOrThrow()
        floorWorkspace.write(SampleProject.multiplatformJvm("sample").plusScript(PRINT_CLASSPATH_VERSIONS)).getOrThrow()
        NestedBuild(floorWorkspace).also { build ->
            build.build("jvmJar")
            verifyResult = build.run("verifyJavaFloor")
            classpathResult = build.build("printClasspathJvmVersions")
            checkDryRun = build.build("check", "--dry-run")
        }

        // java.util.HexFormat is new in Java 17, so it resolves on every build JDK Gradle supports (17 or
        // newer) unless the Java API is limited to 11.
        apiWorkspace = ProjectWorkspace.create("mfa").getOrThrow()
        apiWorkspace.write(
            SampleProject.multiplatformJvm("sample").withFile(
                "src/jvmMain/kotlin/sample/Hex.kt",
                //language=kotlin
                """
                package sample

                import java.util.HexFormat

                fun hex(bytes: ByteArray): String = HexFormat.of().formatHex(bytes)
                """.trimIndent() + "\n",
            ),
        ).getOrThrow()
        apiResult = NestedBuild(apiWorkspace).run("compileKotlinJvm")

        tooNewWorkspace = ProjectWorkspace.create("mft").getOrThrow()
        tooNewWorkspace.write(SampleProject.multiplatformJvm("sample").plusScript(ADD_CLASS_ABOVE_FLOOR)).getOrThrow()
        tooNewResult = NestedBuild(tooNewWorkspace).run("verifyJavaFloor")

        variantWorkspace = ProjectWorkspace.create("mfv").getOrThrow()
        variantWorkspace.write(SampleProject.multiplatformJvm("sample").plusScript(DECLARE_JAVA_17)).getOrThrow()
        variantResult = NestedBuild(variantWorkspace).run("verifyJavaFloor")
    }

    @AfterAll
    fun closeWorkspaces() {
        floorWorkspace.close()
        apiWorkspace.close()
        tooNewWorkspace.close()
        variantWorkspace.close()
    }

    @Test
    fun `every class in the jvm jar is Java 11 bytecode`() {
        val majors = JarClasses.majors(floorWorkspace.resolve("build/libs/sample-jvm-1.0.0.jar")).getOrThrow()
        assertEquals(mapOf("sample/Greeter.class" to 55), majors)
    }

    @Test
    fun `every jvm classpath requests the floor`() {
        classpaths.forEach { classpath ->
            assertTrue(
                classpathResult.output.lineSequence().any { it.trim() == "$classpath requests org.gradle.jvm.version=11" },
                "$classpath does not request 11:\n${classpathResult.output}",
            )
        }
    }

    @Test
    fun `a class added after Java 11 does not resolve`() {
        assertEquals(TaskOutcome.FAILED, apiResult.task(":compileKotlinJvm")?.outcome)
        assertTrue(apiResult.output.contains("Unresolved reference 'HexFormat'"), "no unresolved-reference error:\n${apiResult.output}")
    }

    @Test
    fun `check depends on verifyJavaFloor and it passes`() {
        assertTrue(checkDryRun.output.lineSequence().any { it.startsWith(":verifyJavaFloor ") }, ":verifyJavaFloor not in the check dry run")
        assertEquals(TaskOutcome.SUCCESS, verifyResult.task(":verifyJavaFloor")?.outcome)
    }

    @Test
    fun `verifyJavaFloor fails and names a class above the floor`() {
        assertEquals(TaskOutcome.FAILED, tooNewResult.task(":verifyJavaFloor")?.outcome)
        assertTrue(tooNewResult.output.contains("sample-jvm-1.0.0.jar!TooNew.class: class-file major 61 (Java 17), above 55 (Java 11)"), tooNewResult.output)
    }

    @Test
    fun `verifyJavaFloor fails and names a variant that declares another version`() {
        assertEquals(TaskOutcome.FAILED, variantResult.task(":verifyJavaFloor")?.outcome)
        assertTrue(
            variantResult.output.contains("jvm/module.json: variant 'jvmApiElements-published' declares org.gradle.jvm.version 17, not 11"),
            variantResult.output,
        )
    }

    private fun SampleProject.plusScript(script: String): SampleProject =
        withFile("build.gradle.kts", files.getValue("build.gradle.kts") + script)

    private companion object {
        // Prints the JVM version each jvm classpath requests. The values are read when the task is
        // configured, which is after the Kotlin plugin has copied the target's attributes.
        //language=kotlin
        val PRINT_CLASSPATH_VERSIONS: String = "\n" + """
            tasks.register("printClasspathJvmVersions") {
                val requested = listOf("jvmCompileClasspath", "jvmRuntimeClasspath", "jvmTestCompileClasspath", "jvmTestRuntimeClasspath")
                    .associateWith { name ->
                        configurations.getByName(name).attributes
                            .getAttribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)
                    }
                doLast {
                    requested.forEach { (name, version) -> println("${'$'}name requests org.gradle.jvm.version=${'$'}version") }
                }
            }
            """.trimIndent() + "\n"

        // Adds to the jvm jar a file that carries a class-file header for major 61 (Java 17): the
        // magic number 0xCAFEBABE, minor 0, major 61. The check reads headers only.
        //language=kotlin
        val ADD_CLASS_ABOVE_FLOOR: String = "\n" + """
            val classAboveFloor = tasks.register("writeClassAboveFloor") {
                val out = layout.buildDirectory.file("above-floor/TooNew.class")
                outputs.file(out)
                doLast {
                    out.get().asFile.apply { parentFile.mkdirs() }
                        .writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0, 0, 0, 61))
                }
            }

            tasks.named<Jar>("jvmJar") {
                from(classAboveFloor)
            }
            """.trimIndent() + "\n"

        // A module that sets the target attribute again after the plugin, to a release above the floor.
        //language=kotlin
        val DECLARE_JAVA_17: String = "\n" + """
            kotlin {
                jvm {
                    attributes {
                        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
                    }
                }
            }
            """.trimIndent() + "\n"
    }
}
````

</details>

<details>
<summary>Test sources: the component and deterministic classes (new)</summary>

**`build-logic/src/test/kotlin/com/spartanlabs/testing/component/webtools/buildlogic/JavaFloorScanComponentTest.kt`**

````kotlin
package com.spartanlabs.testing.component.webtools.buildlogic

import FloorScan
import JavaFloorScan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Level 2 - [JavaFloorScan] in isolation, on jars, class directories and metadata files that the
 * test writes itself: the class-file check (version, multi-release entries, the magic number, what
 * is not a class, directories, unreadable input) and the Gradle Module Metadata check (which
 * variants count, the three ways a variant breaks the floor, unreadable input). It starts no Gradle
 * build. A "class" here is the eight-byte class-file header, the only part the scan reads.
 */
class JavaFloorScanComponentTest {

    @TempDir
    lateinit var dir: File

    private val java11 = 55

    private fun classHeader(major: Int): ByteArray =
        byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(), 0, 0, (major shr 8).toByte(), major.toByte())

    private fun jar(name: String, vararg entries: Pair<String, ByteArray>): File =
        File(dir, name).also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                entries.forEach { (entryName, bytes) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }

    private fun metadata(publication: String, variants: String): File =
        File(dir, "$publication/module.json").also { file ->
            file.parentFile.mkdirs()
            file.writeText("""{"formatVersion": "1.1", "variants": [$variants]}""")
        }

    private fun variant(name: String, usage: String, category: String, jvmVersion: Int?): String =
        """{"name": "$name", "attributes": {"org.gradle.usage": "$usage", "org.gradle.category": "$category"""" +
            (jvmVersion?.let { """, "org.gradle.jvm.version": $it""" } ?: "") + "}}"

    //region class files
    @Test
    fun `classes at or below the floor have no violations`() {
        val scan = JavaFloorScan.classFiles(jar("ok.jar", "a/A.class" to classHeader(55), "a/B.class" to classHeader(52)), java11)

        assertEquals(Result.success(FloorScan(2, emptyList())), scan)
    }

    @Test
    fun `a class above the floor is reported with its jar, entry and releases`() {
        val scan = JavaFloorScan.classFiles(jar("new.jar", "a/A.class" to classHeader(55), "a/New.class" to classHeader(61)), java11).getOrThrow()

        assertEquals(2, scan.checked)
        assertEquals(listOf("new.jar!a/New.class: class-file major 61 (Java 17), above 55 (Java 11)"), scan.violations)
    }

    @Test
    fun `entries under META-INF versions are not checked`() {
        val scan = JavaFloorScan.classFiles(
            jar("multi.jar", "a/A.class" to classHeader(55), "META-INF/versions/17/a/A.class" to classHeader(61)),
            java11,
        )

        assertEquals(Result.success(FloorScan(1, emptyList())), scan)
    }

    @Test
    fun `a class entry without the class-file magic number is reported`() {
        val scan = JavaFloorScan.classFiles(jar("bad.jar", "a/A.class" to "not a class".toByteArray()), java11).getOrThrow()

        assertEquals(listOf("bad.jar!a/A.class: not a class file"), scan.violations)
    }

    @Test
    fun `a class entry shorter than a header is reported`() {
        val scan = JavaFloorScan.classFiles(jar("short.jar", "a/A.class" to byteArrayOf(0xCA.toByte(), 0xFE.toByte())), java11).getOrThrow()

        assertEquals(listOf("short.jar!a/A.class: not a class file"), scan.violations)
    }

    @Test
    fun `entries that are not classes are not counted`() {
        val scan = JavaFloorScan.classFiles(
            jar("res.jar", "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0".toByteArray(), "a/readme.txt" to "text".toByteArray()),
            java11,
        )

        assertEquals(Result.success(FloorScan.EMPTY), scan)
    }

    @Test
    fun `a class directory is checked like a jar`() {
        val classes = File(dir, "classes").apply { File(this, "a").mkdirs() }
        File(classes, "a/Old.class").writeBytes(classHeader(52))
        File(classes, "a/New.class").writeBytes(classHeader(65))
        File(classes, "META-INF/versions/21/a").mkdirs()
        File(classes, "META-INF/versions/21/a/New.class").writeBytes(classHeader(65))

        val scan = JavaFloorScan.classFiles(classes, java11).getOrThrow()

        assertEquals(2, scan.checked)
        assertEquals(listOf("classes/a/New.class: class-file major 65 (Java 21), above 55 (Java 11)"), scan.violations)
    }

    @Test
    fun `a file that does not exist is a failure`() {
        assertTrue(JavaFloorScan.classFiles(File(dir, "missing.jar"), java11).isFailure)
    }

    @Test
    fun `a file that is not a zip is a failure`() {
        val notAZip = File(dir, "plain.jar").apply { writeText("plain text") }

        assertTrue(JavaFloorScan.classFiles(notAZip, java11).isFailure)
    }

    @Test
    fun `scans add up`() {
        assertEquals(FloorScan(3, listOf("a", "b")), FloorScan(1, listOf("a")) + FloorScan(2, listOf("b")))
        assertEquals(FloorScan(1, listOf("a")), FloorScan.EMPTY + FloorScan(1, listOf("a")))
    }
    //endregion

    //region module metadata
    @Test
    fun `library variants at the floor have no violations and sources variants are not checked`() {
        val file = metadata(
            "maven",
            listOf(
                variant("apiElements", "java-api", "library", 11),
                variant("runtimeElements", "java-runtime", "library", 11),
                variant("sourcesElements", "java-runtime", "documentation", null),
            ).joinToString(", "),
        )

        assertEquals(Result.success(FloorScan(2, emptyList())), JavaFloorScan.moduleMetadata(file, 11))
    }

    @Test
    fun `a variant that declares another version is reported with its publication`() {
        val file = metadata("maven", variant("apiElements", "java-api", "library", 23))

        assertEquals(
            listOf("maven/module.json: variant 'apiElements' declares org.gradle.jvm.version 23, not 11"),
            JavaFloorScan.moduleMetadata(file, 11).getOrThrow().violations,
        )
    }

    @Test
    fun `a library variant without the attribute is reported`() {
        val file = metadata("jvm", variant("jvmRuntimeElements-published", "java-runtime", "library", null))

        assertEquals(
            listOf("jvm/module.json: variant 'jvmRuntimeElements-published' declares org.gradle.jvm.version nothing, not 11"),
            JavaFloorScan.moduleMetadata(file, 11).getOrThrow().violations,
        )
    }

    @Test
    fun `variants of another usage are not checked`() {
        val file = metadata("kotlinMultiplatform", variant("metadataApiElements", "kotlin-metadata", "library", null))

        assertEquals(Result.success(FloorScan.EMPTY), JavaFloorScan.moduleMetadata(file, 11))
    }

    @Test
    fun `metadata without a variants list is a failure`() {
        val file = File(dir, "module.json").apply { writeText("""{"formatVersion": "1.1"}""") }

        assertTrue(JavaFloorScan.moduleMetadata(file, 11).isFailure)
    }

    @Test
    fun `a metadata file that is not JSON is a failure`() {
        val file = File(dir, "module.json").apply { writeText("not json") }

        assertTrue(JavaFloorScan.moduleMetadata(file, 11).isFailure)
    }
    //endregion
}
````

**`build-logic/src/test/kotlin/com/spartanlabs/testing/component/webtools/buildlogic/RegisterVerifyJavaFloorComponentTest.kt`**

````kotlin
package com.spartanlabs.testing.component.webtools.buildlogic

import WebToolsBuild
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Level 2 - [WebToolsBuild.registerVerifyJavaFloor] in isolation, on a [ProjectBuilder] project with
 * no Kotlin plugin applied: the task it registers, what it carries, the loud failure of a second
 * registration, and that the task picks up the metadata task of a publication created after it.
 * The task's behaviour on real files is covered by [JavaFloorScanComponentTest] and the TestKit
 * checks.
 */
class RegisterVerifyJavaFloorComponentTest {

    private val project: Project = ProjectBuilder.builder().build()
    private val jars: FileCollection = project.files("a.jar")
    private val classpath: FileCollection = project.files("dependency.jar")

    private fun register() = WebToolsBuild.registerVerifyJavaFloor(project, jars, classpath)

    @Test
    fun `the task is named verifyJavaFloor and sits in the verification group`() {
        val task = register().get()

        assertEquals("verifyJavaFloor", task.name)
        assertEquals("verification", task.group)
    }

    @Test
    fun `the task carries the floor, the jars, the runtime classpath and a report under build reports`() {
        val task = register().get()

        assertEquals(11, task.javaFloor.get())
        assertEquals(jars.files, task.jars.files)
        assertEquals(classpath.files, task.runtimeClasspath.files)
        assertEquals(
            project.layout.buildDirectory.file("reports/java-floor/verifyJavaFloor.txt").get().asFile,
            task.report.get().asFile,
        )
    }

    @Test
    fun `the task picks up the metadata task of a publication created after it`() {
        val task = register().get()
        project.plugins.apply("java")
        project.plugins.apply("maven-publish")
        project.extensions.getByType(PublishingExtension::class.java).publications
            .create("mavenJava", MavenPublication::class.java) { from(project.components.getByName("java")) } // Action receives the publication as `this` (sam-with-receiver)

        assertEquals(
            setOf(project.layout.buildDirectory.file("publications/mavenJava/module.json").get().asFile),
            task.moduleMetadata.files,
        )
    }

    @Test
    fun `a second registration on the same project fails with InvalidUserDataException`() {
        register()

        val failure = assertThrows(InvalidUserDataException::class.java) { register() }
        assertTrue(failure.message.orEmpty().contains("verifyJavaFloor"), "unexpected message: ${failure.message}")
    }
}
````

**`build-logic/src/test/kotlin/com/spartanlabs/testing/deterministic/webtools/buildlogic/WebToolsBuildJavaFloorDeterministicTest.kt`**

````kotlin
package com.spartanlabs.testing.deterministic.webtools.buildlogic

import WebToolsBuild
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Level 4a - pure input-to-output checks over the Java floor constants of [WebToolsBuild]: the floor
 * itself, the task name, and the mapping from a Java release to its class-file major version.
 */
class WebToolsBuildJavaFloorDeterministicTest {

    @Test
    fun `the Java floor is 11 and its class-file major version is 55`() {
        assertEquals(11, WebToolsBuild.JAVA_FLOOR)
        assertEquals(55, WebToolsBuild.classFileMajor(WebToolsBuild.JAVA_FLOOR))
    }

    @Test
    fun `a Java release maps to its class-file major version`() {
        // The values the JVM specification gives: Java 8 is 52, 11 is 55, 17 is 61, 21 is 65, 23 is 67.
        mapOf(8 to 52, 11 to 55, 17 to 61, 21 to 65, 23 to 67).forEach { (release, major) ->
            assertEquals(major, WebToolsBuild.classFileMajor(release), "Java $release")
        }
    }

    @Test
    fun `the guard task is named verifyJavaFloor`() {
        assertEquals("verifyJavaFloor", WebToolsBuild.VERIFY_JAVA_FLOOR_TASK)
    }
}
````

</details>

<details>
<summary>Test sources: the changed classes (diffs)</summary>

````diff
--- a/build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/PublishedJvmVersionAttributeIntegrationTest.kt
+++ b/build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/PublishedJvmVersionAttributeIntegrationTest.kt
@@ -21,7 +21,8 @@ import org.junit.jupiter.api.TestInstance
 class PublishedJvmVersionAttributeIntegrationTest {
 
-    // The feature version of the JVM running the tests, which also runs the nested daemon (TestKit
-    // offers no JDK selection): what the plugin sets until the Java floor lands.
-    private val expectedJvmVersion: Int = Runtime.version().feature()
+    // The Java floor, written out: an independent oracle for WebToolsBuild.JAVA_FLOOR. It is not the
+    // feature version of the JVM that runs the tests and the nested daemon, which is what the plugin
+    // wrote before the floor.
+    private val expectedJvmVersion: Int = 11
 
     private val jvmVariants: List<String> = listOf("jvmApiElements-published", "jvmRuntimeElements-published")
````

````diff
--- a/build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/ApiValidationIntegrationTest.kt
+++ b/build-logic/src/test/kotlin/com/spartanlabs/testing/integration/webtools/buildlogic/ApiValidationIntegrationTest.kt
@@ -18,6 +18,6 @@ import java.nio.file.Files
  * `webtools.kotlin-multiplatform-library`: `apiCheck` fails before a dump exists, `apiDump` writes
  * `api/<rootProject.name>.api`, `apiCheck` then passes, `check` depends on `apiCheck`, and a change
- * to the public API fails `apiCheck`. On a JDK 23 or newer host the `apiDump` step is also the
- * regression guard for the plugin's ASM pin (ASM 9.6 rejects class files above Java 22).
+ * to the public API fails `apiCheck`. The sample compiles to Java 11 bytecode on any build JDK, so
+ * the validator's own ASM reads it with no pin.
  */
 @TestInstance(TestInstance.Lifecycle.PER_CLASS)
````

````diff
--- a/build-logic/src/test/kotlin/com/spartanlabs/testing/e2e/webtools/buildlogic/IncludedBuildEndToEndTest.kt
+++ b/build-logic/src/test/kotlin/com/spartanlabs/testing/e2e/webtools/buildlogic/IncludedBuildEndToEndTest.kt
@@ -8,4 +8,5 @@ import com.spartanlabs.testing.support.webtools.buildlogic.SampleProject
 import com.spartanlabs.testing.support.webtools.buildlogic.TestReports
 import org.gradle.testkit.runner.BuildResult
+import org.gradle.testkit.runner.TaskOutcome
 import org.junit.jupiter.api.AfterAll
 import org.junit.jupiter.api.Assertions.assertEquals
@@ -28,5 +29,6 @@ import org.junit.jupiter.api.TestInstance
  *   once;
  * - the module script prints the level list;
- * - a level task runs in each kind of module.
+ * - a level task runs in each kind of module;
+ * - `verifyJavaFloor` is part of `build` and passes in each kind of module.
  */
 @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@@ -36,4 +38,5 @@ class IncludedBuildEndToEndTest {
     private lateinit var dryRun: BuildResult
     private lateinit var levelRun: BuildResult
+    private lateinit var floorRun: BuildResult
 
     @BeforeAll
@@ -45,4 +48,6 @@ class IncludedBuildEndToEndTest {
         dryRun = build.build("build", "--dry-run")
         levelRun = build.build(":jvm-lib:printLevels", ":jvm-lib:componentTest", ":kmp-lib:componentTest")
+        // run, not build: while the guard is missing the build fails, and only this test should.
+        floorRun = build.run(":jvm-lib:verifyJavaFloor", ":kmp-lib:verifyJavaFloor")
     }
 
@@ -54,5 +59,8 @@ class IncludedBuildEndToEndTest {
     @Test
     fun `the composite configures through the included build`() {
-        listOf(":jvm-lib:compileKotlin", ":kmp-lib:compileKotlinJvm", ":kmp-lib:apiCheck").forEach { task ->
+        listOf(
+            ":jvm-lib:compileKotlin", ":kmp-lib:compileKotlinJvm", ":kmp-lib:apiCheck",
+            ":jvm-lib:verifyJavaFloor", ":kmp-lib:verifyJavaFloor",
+        ).forEach { task ->
             assertTrue(dryRun.output.contains(task), "$task is not in the build dry run")
         }
@@ -70,4 +78,11 @@ class IncludedBuildEndToEndTest {
     }
 
+    @Test
+    fun `verifyJavaFloor passes in each kind of module`() {
+        listOf(":jvm-lib:verifyJavaFloor", ":kmp-lib:verifyJavaFloor").forEach { task ->
+            assertEquals(TaskOutcome.SUCCESS, floorRun.task(task)?.outcome, task)
+        }
+    }
+
     @Test
     fun `a level task runs in each kind of module`() {
````

````diff
--- a/build-logic/src/test/kotlin/com/spartanlabs/testing/nonfunctional/webtools/buildlogic/ConfigurationCacheReuseNonFunctionalTest.kt
+++ b/build-logic/src/test/kotlin/com/spartanlabs/testing/nonfunctional/webtools/buildlogic/ConfigurationCacheReuseNonFunctionalTest.kt
@@ -29,6 +29,6 @@ class ConfigurationCacheReuseNonFunctionalTest {
         workspace.write(SampleProject.multiplatformJvm("sample")).getOrThrow()
         val build = NestedBuild(workspace)
-        build.build("componentTest")
-        secondRun = build.build("componentTest")
+        build.build("componentTest", "verifyJavaFloor")
+        secondRun = build.build("componentTest", "verifyJavaFloor")
     }
 
````

````diff
--- a/build-logic/src/test/kotlin/com/spartanlabs/testing/nonfunctional/webtools/buildlogic/JvmConfigurationCacheReuseNonFunctionalTest.kt
+++ b/build-logic/src/test/kotlin/com/spartanlabs/testing/nonfunctional/webtools/buildlogic/JvmConfigurationCacheReuseNonFunctionalTest.kt
@@ -29,6 +29,6 @@ class JvmConfigurationCacheReuseNonFunctionalTest {
         workspace.write(SampleProject.kotlinJvm("sample")).getOrThrow()
         val build = NestedBuild(workspace)
-        build.build("componentTest")
-        secondRun = build.build("componentTest")
+        build.build("componentTest", "verifyJavaFloor")
+        secondRun = build.build("componentTest", "verifyJavaFloor")
     }
 
````

</details>

**Verification procedures.** No procedure runs a module test task. Do not run `check` or `build` for a
module: they run its test suite. Run `verifyJavaFloor` itself.

- **A. Compile under the floor.** `./gradlew :webtools-udp:testClasses :webtools-scraping:testClasses
  :webtools-browser:testClasses` compiles the main and test sources of all three modules and runs no test.
  Expected: success with no new warning.
- **B. The guard on the real modules.** `./gradlew verifyJavaFloor` (all three modules). Expected: success, and
  `build/reports/java-floor/verifyJavaFloor.txt` in each module reads `java floor 11 (class-file major <= 55)`
  with 98, 4 and 2 classes, 2 library variants and, on the runtime classpath, 1055, 13960 and 11757 classes
  (the last three move when a dependency does).
- **C. The published bytes.** For each module, build `jar`, `generateMetadataFileForMavenPublication` and
  `generatePomFileForMavenPublication`, and the same three tasks from a `git archive 36fd879` export. Compare:
  the jar's class majors (55 against 67); `javap -protected` of every class (identical: 909, 30 and 14 lines);
  the `module.json` files (only `org.gradle.jvm.version` and the version move); the POMs (only the legacy
  modules' version).
- **D. Level 5, a real JDK 11.** On the Temurin 11 that Gradle provisioned
  (`~/.gradle/jdks/eclipse_adoptium-11-amd64-windows.2`), load every class of each of the three new jars with
  `Class.forName(name, false, loader)`, the loader holding the jar and its runtime classpath. Expected: 98, 4
  and 2 loaded, 0 failed. The control, the baseline jars, loads none and fails with
  `UnsupportedClassVersionError`. The program and the classpath printer are throwaway scripts kept outside the
  repository (below).
- **E. `build-logic`.** `./gradlew :build-logic:test`, then `:build-logic:integrationTest
  :build-logic:e2eTest :build-logic:nonfunctionalTest` (nested builds; the first run needs the network), then
  `:build-logic:validatePlugins`. Expected: all green. Also run `:build-logic:integrationTest` with
  `--tests '*JavaFloor*' --tests '*PublishedJvmVersion*' --tests '*ApiValidation*'` with JDK 17 as the build JDK
  (`JAVA_HOME` at a Temurin 17): the floor, the `HexFormat` pair and `apiDump` hold there too.
- **F. Which module test tasks exercise the change.** None. Procedures A to D are the evidence for the three
  modules: they compile, hold the floor, keep their API and load on Java 11.
- **G. README.** Read the diff against the Build & test section, and check that each version the install block
  names is on Maven Central (`maven-metadata.xml` of each artifact).

<details>
<summary>Level 5 helpers (throwaway, outside the repository)</summary>

**`LoadAll.java`**

````java
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Throwaway Level-5 helper (not part of the repository): loads every class of the jar named by
 * args[0], without initialising it, through a loader whose classpath is args[0] followed by the
 * dependency jars args[1..]. A class file newer than the running JVM fails with
 * UnsupportedClassVersionError at this step.
 */
public class LoadAll {
    public static void main(String[] args) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String a : args) urls.add(new File(a).toURI().toURL());
        // parent = null: only the JDK's own classes are visible besides the given jars.
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), null);
             JarFile jar = new JarFile(args[0])) {
            int loaded = 0;
            int failed = 0;
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.equals("module-info.class")) continue;
                String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                try {
                    Class.forName(className, false, loader);
                    loaded++;
                } catch (Throwable t) {
                    failed++;
                    if (failed <= 3) System.out.println("  FAIL " + className + ": " + t);
                }
            }
            System.out.println(new File(args[0]).getName() + ": loaded=" + loaded + " failed=" + failed
                + " on java " + System.getProperty("java.version"));
        }
    }
}
````

**`print-classpath.init.gradle.kts`**

````kotlin
// Throwaway Level-5 helper (not part of the repository): prints each Kotlin/JVM module's runtime
// classpath jars to build/runtime-classpath.txt, one path per line. Run with --no-configuration-cache.
allprojects {
    afterEvaluate {
        val runtime = configurations.findByName("runtimeClasspath")
        if (runtime != null) {
            tasks.register("printRuntimeClasspath") {
                doLast {
                    val out = layout.buildDirectory.file("runtime-classpath.txt").get().asFile
                    out.parentFile.mkdirs()
                    out.writeText(runtime.files.joinToString("\n") { it.absolutePath } + "\n")
                }
            }
        }
    }
}
````

Use: `./gradlew --no-configuration-cache -I print-classpath.init.gradle.kts :webtools-udp:printRuntimeClasspath
:webtools-scraping:printRuntimeClasspath :webtools-browser:printRuntimeClasspath` writes
`<module>/build/runtime-classpath.txt`; then, per module, `<jdk11>/bin/java LoadAll.java <module jar>
<each classpath entry>`.

</details>

## Risks and callers affected

- **`-Xjdk-release` is an `-X` flag.** Kotlin documents it as "not guaranteed to be effective for each JDK
  distribution". It is effective on Oracle JDK 23.0.2 and Temurin 17.0.20 (measured at `36fd879`) and on
  Temurin 21.0.9 (research). `verifyJavaFloor` checks bytes and metadata, not API references: a class compiled against a newer
  API at major 55 would pass it. The API limit rests on the flag, and the `HexFormat` tests are its only
  automated check, on the JDK that runs the tests.
- **`jvmTarget` and the flag come as a pair.** The flag fails without `jvmTarget` set to the same value, and on
  the Kotlin/JVM plugin `jvmTarget` fails without matching `java {}` compatibility. Both come from the one
  constant. A module that sets its own `jvmTarget`, toolchain, flag or `TargetJvmVersion` breaks that pairing,
  and `verifyJavaFloor` fails the module.
- **Lettered versions order below the plain ones, `webtools-udp` included.** `1.0.0a` orders below `1.0.0`
  and `2.0.0a` below `2.0.0`, and the plain versions on Maven Central are the Java 23 builds. A dependency
  graph that also asks for the plain version silently keeps the defect unless it requests the lettered version
  `strictly`. The README says so. (The architecture's §12 calls `webtools-udp` unaffected; the ordering
  rule applies to `2.0.0a` too.)
- **The README names Java 23 builds until the release.** The install block names `2.0.0`, `1.0.0` and `1.0.0`
  because the lettered versions are not published, and the paragraph below it says which releases carry the
  floor. The release that publishes `2.0.0a`, `1.0.0a` and `1.0.0a` is a separate step that needs the
  maintainer's explicit instruction; its commit moves the three install lines to the lettered versions.
- **`check` does a little more.** Each module's `check` now builds its sources and javadoc jars and its
  metadata (`build` already did) and scans the jars and the runtime classpath, about a second for the
  largest module; the task is up to date afterwards. `check` of a module runs its test suite, so
  verification uses the task by name.
- **The metadata collection is read when the task graph is built**, so every `GenerateModuleMetadata` task of
  the project is created when `verifyJavaFloor` runs. If a publication stopped generating `.module` files the
  guard would fail with "no library variant to verify", not pass.
- **Callers.** No source file of any module changes; `webtools-udp`'s `javap -protected` output is identical.
  Consumers on JDK 11 to 22 can load the lettered releases once published. `SpartanLaboratories/KotlinBottools`
  (JDK 21) was not blocked and needs nothing.
- **Siblings.**
  - **`SpartanLaboratories/WebTools#62` (`http-core`, unit 3) inherits** a multiplatform plugin that compiles
    its `jvm()` target with `jvmTarget` 11 and `-Xjdk-release=11` (main, test and the common code the target
    compiles), sets `TargetJvmVersion` 11 on the target, registers `verifyJavaFloor` in `check` over `jvmJar`,
    every publication's metadata and `jvmRuntimeClasspath`, and runs the validator on its defaults with no ASM
    pin. KGP copies the target's attributes onto the four jvm classpaths, so Gradle rejects any dependency,
    test-only ones included, whose metadata declares a higher JVM version; every dependency #55's research
    listed is at or below 11. `webtools-http` must set no `jvmTarget`, toolchain, flag or `TargetJvmVersion`
    of its own, declare no second JVM-based target, and use the Java 11 API only (`@JvmRecord` needs JVM 16).
    Its own tests run on the build JDK. `WebToolsBuild.JAVA_FLOOR` is readable from its build script.
  - **`#63` (`test-support`, unit 4):** the target's attribute does not reach hand-declared variants, so the
    fixtures variants set `TargetJvmVersion` from `WebToolsBuild.JAVA_FLOOR`, and the fixtures jar is added to
    the guard with `tasks.named<VerifyJavaFloor>("verifyJavaFloor") { jars.from(<the jar's archiveFile>) }`. The
    fixtures publication's `.module` is picked up without a change. Its publication check expects 11.
  - **Unit 10 (`feeds`)** applies the plugin as unit 3 does. The JS check modules apply neither library
    plugin, so the floor does not reach them.
- **Follow-ups this leaves.** `jdk11-test-launcher` (run the level tasks on a JDK 11, after #58's foojay
  bump); the release of `2.0.0a`, `1.0.0a` and `1.0.0a` with the install-line move; the parent documents:
  [`../impact.md`](../impact.md) §8 lists the ASM pin among what the multiplatform plugin carries, and
  [`../architecture.md`](../architecture.md) §12 calls `webtools-udp` unaffected by the lettered ordering.

## Version control

- **Branch:** `fix/issue-47-java-floor`, cut from `master` at `36fd879`.
- **Commits.** The admin team's executor makes the final call. Each commit is coherent and leaves the build
  green; subject lines end `(Issue #47)`; no attribution trailers. Proposed:
  1. `fix: compile every module to the Java 11 floor and guard it with verifyJavaFloor (Issue #47)`: this
     `plan.md`, everything under `build-logic/src/main/` and `build-logic/src/test/`.
  2. `build: bump webtools-scraping and webtools-browser to 1.0.0a and note the floor in webtools-udp 2.0.0a (Issue #47)`:
     the three module build files.
  3. `docs: state the Java floor and name the installable versions in the README (Issue #47)`: `README.md`.
  4. At the end of implementation, `docs: record the as-built Java floor (Issue #47)`: this folder's
     `final-implementation.md`, which the implementer appends to as deviations happen and the planner
     reconciles.
- **Never carried in:** any unrelated working-tree change. The working tree also holds the presenter's plan
  boards (`docs/plans/index.html` and the `dashboard.html` and `plan.json` files of #47, #55 and #61); they
  belong to the dashboards service, not to these commits.
- **Version moved:** `webtools-scraping` and `webtools-browser` to `1.0.0a` (a fix: a trailing letter);
  `webtools-udp` stays `2.0.0a`. No tag, no publish, no signing.
- **PR:** base `master`, body `Closes #47`. After the merge the branch is deleted, locally and on the remote.
  The resolution comment on #47 names the mechanism (`WebToolsBuild.JAVA_FLOOR`, `-Xjdk-release=11`,
  `verifyJavaFloor`) and the lettered versions.

## Known gaps

- **Hosts.** Windows only. Oracle JDK 23.0.2 and Temurin 17.0.20 as build JDKs (Temurin 21.0.9 in the
  research); JDK 24 to 26, other distributions (one without `ct.sym` data) and Linux are not exercised.
- **The API limit has no byte-level guard** (see *Risks*).
- **No repository module applies the multiplatform plugin yet.** Its floor and guard are exercised by the
  generated samples and the end-to-end composite until `#62` adds `webtools-http`.
- **Dependencies are scanned by class-file version, not loaded.** Procedure D loads the modules' own classes on
  JDK 11; the Selenium, htmlunit and Ktor paths the tests do not run are covered by their declared floors and
  class versions.
- **No module test suite runs on JDK 11.** A launcher needs the foojay bump (#58).
- **IntelliJ.** Whether the IDE honours `-Xjdk-release` when it analyses the sources is not checked.

## Considerations

None.
