package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpSyntax
import com.spartanlabs.webtools.http.catchNonFatal
import com.spartanlabs.webtools.http.flatMap

/**
 * Loads [Fixture]s from the consumer's own classpath, under a resource directory ([root], `fixtures` by
 * default). A name ending in `.http` is parsed as a `.http` file ([Fixture.parse]); any other name is a
 * **body-only fixture**: status 200, the whole resource as the body and a `Content-Type` chosen by the
 * extension (`json` -> `application/json`, `xml` -> `application/xml`, `rss` -> `application/rss+xml`,
 * `atom` -> `application/atom+xml`, `txt` -> `text/plain; charset=utf-8`, `html`/`htm` ->
 * `text/html; charset=utf-8`, anything else or none -> `application/octet-stream`; compared ASCII-lower-cased).
 * The fixtures jar ships no fixture file; a consumer marks its `.http` files `-text` in its `.gitattributes`
 * (a pattern over every `.http` file below a `fixtures` directory) so a checkout cannot rewrite their line
 * endings.
 *
 * **Stable Core, source compatibility only.** Stateless after construction and safe to share between
 * threads. Equality is identity. `toString()` prints the root only. It logs nothing.
 *
 * @param classLoader the loader whose resources hold the fixtures; by default the thread's context class loader,
 *   or this class's own loader when the thread has none.
 * @param root the resource directory the names are relative to; `fixtures` by default.
 * @throws IllegalArgumentException if [root] is blank, absolute, holds a backslash or an empty, `.` or `..` segment.
 */
public class FixtureLoader(
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader ?: FixtureLoader::class.java.classLoader,
    /** The resource directory the names are relative to. */
    public val root: String = "fixtures",
) {
    init {
        require(root.isNotBlank() && validSegments(root)) { "root must be a relative resource directory" }
    }

    /**
     * Loads the fixture at `<root>/<name>`.
     *
     * @param name the resource's path below [root], with forward slashes.
     * @return the parsed `.http` fixture, or the body-only fixture for any other extension.
     * @throws IllegalArgumentException if [name] is empty or absolute, holds a backslash, or has an empty, `.`
     *   or `..` segment.
     * @throws FixtureError if the resource is missing, cannot be read, or is a malformed `.http` file; the
     *   message names the resource and, for a syntax fault, the line.
     */
    public fun load(name: String): Fixture {
        require(name.isNotEmpty() && validSegments(name)) { "a fixture name is a relative path of non-empty segments, not . or .., with no backslash" }
        val resource = "$root/$name"
        return read(resource)
            .flatMap { bytes -> if (name.endsWith(".http")) HttpMessageCodec.parse(bytes, resource) else Result.success(bodyOnly(name, bytes)) }
            .getOrThrow()
    }

    override fun toString(): String = "FixtureLoader(root=$root)"

    private fun read(resource: String): Result<ByteArray> =
        catchNonFatal { classLoader.getResourceAsStream(resource)?.use { it.readBytes() } }
            .fold(
                onSuccess = { bytes -> bytes?.let { Result.success(it) } ?: Result.failure(FixtureError("fixture resource not found: $resource", null)) },
                onFailure = { problem -> Result.failure(FixtureError("fixture resource could not be read: $resource", problem)) },
            )

    private fun bodyOnly(name: String, bytes: ByteArray): Fixture {
        val file = name.substringAfterLast('/')
        val extension = if ('.' in file) file.substringAfterLast('.') else ""
        return Fixture(200, HttpHeaders { add("Content-Type", contentTypeFor(extension)) }, bytes)
    }

    private fun validSegments(path: String): Boolean =
        !path.startsWith("/") && '\\' !in path && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
}

/** The body-only `Content-Type` for a file [extension] (without its dot; empty for none), compared ASCII-lower-cased. */
internal fun contentTypeFor(extension: String): String =
    when (extension.map(HttpSyntax::asciiLower).joinToString("")) {
        "json" -> "application/json"
        "xml" -> "application/xml"
        "rss" -> "application/rss+xml"
        "atom" -> "application/atom+xml"
        "txt" -> "text/plain; charset=utf-8"
        "html", "htm" -> "text/html; charset=utf-8"
        else -> "application/octet-stream"
    }
