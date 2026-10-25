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

    /**
     * The names of the non-directory entries of [jar], in order: classes, resources and `META-INF/` files
     * alike, so a jar can be checked for entries it must not carry.
     *
     * @param jar the jar file.
     * @return the entry names, as a read-only list; or a failure when the file is missing or is not a zip file.
     */
    fun entries(jar: Path): Result<List<String>> = runCatching {
        ZipFile(jar.toFile()).use { zip -> zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toList() }
    }
}
