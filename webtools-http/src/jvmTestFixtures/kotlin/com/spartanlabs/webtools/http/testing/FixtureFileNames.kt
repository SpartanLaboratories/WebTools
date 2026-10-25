package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.Url
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Where a [FixtureRecorder] writes and what its comment says: `<directory>/<group>/<method>-<path slug>-<8 hex>.http`,
 * or `<directory>/<group>/<name>.http` when the consumer names the file; the 8 hex are the first four bytes of
 * SHA-256 over `"<METHOD> <encodedPath>?<redacted sorted query>"`, so a rotated key and a reordered query land
 * on the same file. Pure.
 */
internal object FixtureFileNames {
    //language=RegExp
    private val SEGMENT = Regex("[A-Za-z0-9._-]+")
    private const val SLUG_LIMIT = 60
    // A custom method name is cut to 16 so the file name stays readable; the standard methods are all shorter.
    private const val METHOD_LIMIT = 16

    /** @throws IllegalArgumentException unless [group] is one or more `/`-separated valid segments. */
    fun requireGroup(group: String) {
        require(group.split('/').all(::validSegment)) { "group is one or more /-separated segments of [A-Za-z0-9._-]+, never . or .." }
    }

    /** @throws IllegalArgumentException unless [name] is one valid segment. */
    fun requireName(name: String) {
        require(validSegment(name)) { "name must match [A-Za-z0-9._-]+, never . or .." }
    }

    // Windows strips a trailing dot, so a segment ending in '.' is refused too.
    private fun validSegment(s: String): Boolean = SEGMENT.matches(s) && s != "." && s != ".." && !s.endsWith('.')

    /** The target file: the group's directories below [directory], then `<name>.http` or the generated name. */
    fun path(directory: Path, group: String, request: HttpRequest, name: String?, redaction: Redaction): Path {
        val groupDir = group.split('/').fold(directory) { dir, segment -> dir.resolve(segment) }
        return groupDir.resolve((name ?: generatedName(request, redaction)) + ".http")
    }

    private fun generatedName(request: HttpRequest, redaction: Redaction): String =
        slug(request.method.name).take(METHOD_LIMIT) + "-" + slug(request.url.encodedPath) + "-" + hash(request, redaction)

    /** The first four bytes of SHA-256 over `"<METHOD> <encodedPath>?<redacted sorted query>"`, lower-case hex. */
    fun hash(request: HttpRequest, redaction: Redaction): String {
        val key = "${request.method.name} ${request.url.encodedPath}?${redactedQuery(request.url.encodedQuery, redaction) ?: ""}"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    /**
     * The raw query pairs with every sensitive pair's value replaced by the placeholder (the name decoded to
     * decide sensitivity, kept raw in the output), sorted by (raw name, rendered value); a piece without `=`
     * has no value to redact. `null` for no query.
     */
    fun redactedQuery(encodedQuery: String?, redaction: Redaction): String? {
        if (encodedQuery == null) return null
        return encodedQuery.split('&')
            .map { piece ->
                val equals = piece.indexOf('=')
                if (equals < 0) piece to null
                else {
                    val rawName = piece.substring(0, equals)
                    val value = piece.substring(equals + 1)
                    rawName to if (redaction.isSensitiveQuery(percentDecodeQueryComponent(rawName))) redaction.placeholder else value
                }
            }
            .sortedWith(compareBy({ it.first }, { it.second ?: "" }))
            .joinToString("&") { (name, value) -> if (value == null) name else "$name=$value" }
    }

    /** `request: <METHOD> <scheme>://<host or [ipv6]>[:<non-default port>]<encodedPath>[?<redacted sorted query>]`. */
    fun comment(request: HttpRequest, redaction: Redaction): String {
        val url = request.url
        val query = redactedQuery(url.encodedQuery, redaction)
        return "request: ${request.method.name} ${origin(url)}${url.encodedPath}" + (if (query == null) "" else "?$query")
    }

    private fun origin(url: Url): String = buildString {
        append(url.scheme).append("://")
        // Url.host holds an IPv6 literal without its brackets; restore them so the comment is a valid origin.
        if (':' in url.host) append('[').append(url.host).append(']') else append(url.host)
        val defaultPort = if (url.scheme == "https") 443 else 80
        if (url.port != defaultPort) append(':').append(url.port)
    }

    /** Lower-cased; runs of non-`[a-z0-9]` become `-`; trimmed of `-`; cut at 60 and trimmed again; `root` when empty. */
    fun slug(text: String): String {
        val lowered = asciiLowercase(text)
        val collapsed = buildString(lowered.length) {
            var dash = false
            for (c in lowered) {
                if (c in 'a'..'z' || c in '0'..'9') {
                    append(c)
                    dash = false
                } else if (!dash) {
                    append('-')
                    dash = true
                }
            }
        }
        val trimmed = collapsed.trim('-').take(SLUG_LIMIT).trim('-')
        return if (trimmed.isEmpty()) "root" else trimmed
    }
}
