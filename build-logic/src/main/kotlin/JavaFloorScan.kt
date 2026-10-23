import groovy.json.JsonSlurper
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * What one scan looked at and what it found. Immutable: a scan is a value, and [plus] returns a
 * new one.
 *
 * @property checked how many things the scan examined: class files for [JavaFloorScan.classFiles],
 *   library variants for [JavaFloorScan.moduleMetadata].
 * @property violations one line per thing that breaks the floor; empty when everything holds.
 */
data class FloorScan(val checked: Int, val violations: List<String>) {

    /**
     * The combination of this scan and [other]: both counts added, both violation lists joined.
     *
     * @param other the scan to combine with this one.
     * @return a new scan with the counts added and this scan's violations followed by [other]'s.
     */
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
        // Java release = class-file major - 44, the inverse of WebToolsBuild.classFileMajor.
        return when {
            magic != CLASS_MAGIC || major == null -> "$location: not a class file"
            major > maxMajor -> "$location: class-file major $major (Java ${major - 44}), above $maxMajor (Java ${maxMajor - 44})"
            else -> null
        }
    }

    // A u2 of the JVM class-file spec: an unsigned big-endian 16-bit value (infix and/shl/or bind left to right).
    private fun u2(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 0xFF shl 8) or (bytes[offset + 1].toInt() and 0xFF)
}
