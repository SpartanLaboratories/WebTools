package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.HttpSyntax
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.catchNonFatal
import com.spartanlabs.webtools.http.flatMap
import com.spartanlabs.webtools.http.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Records the responses a live transport returns as `.http` fixture files, so a later test replays them
 * through [FakeHttpTransport] (`Step.fixture(FixtureLoader().load(...))`). [wrap] decorates any
 * [HttpTransport]; each successful response (any status) is serialised, redacted, scrubbed and written; a
 * failure is not recorded; the delegate's result is returned untouched.
 *
 * **Stable Core, source compatibility only.** Constructible only from a [RecordingGate.Enabled], so recording
 * never happens unless `WEBTOOLS_HTTP_RECORD` allowed it and no CI variable was present
 * ([RecordingGate.evaluate]); the consumer's build declares that variable as an input of its test tasks (see
 * the gate's KDoc) or Gradle skips the tests as up to date when only the variable changed. Equality is
 * identity. `toString()` prints the directory, the mode and the three counts.
 *
 * What is written: one comment `# request: <METHOD> <scheme>://<host>[:<port>]<path>[?<redacted, sorted query>]`,
 * the status line, the response headers in order with `Set-Cookie`, `Set-Cookie2`, `Transfer-Encoding`,
 * `Connection` and `Keep-Alive` dropped and the value of every sensitive entry (`HttpHeaders.Entry.isSensitive`,
 * or a name whose tokens are sensitive: `key`, `token`, `secret`, `auth`, `signature`, `session`, ...)
 * replaced by the placeholder, then the body bytes. **Request headers are never written.** Every secret of the
 * [Redaction] is replaced in the whole file; a file in which a form of one survives is refused, never written.
 * The survival search is a backstop over the exact raw and encoded forms (and the full percent-escape of the
 * secret's bytes), not a decoder of transformed forms. Body redaction beyond exact supplied secrets is not done:
 * a key in transformed form (base64, hashed, truncated, or percent-escaped only in part, e.g. `SECRET%2dCANARY`
 * for `SECRET-CANARY`) is neither replaced nor detected, and personal data in a recorded body is a review task
 * before the file is committed.
 *
 * The file is `<directory>/<group>/<method>-<path slug>-<8 hex>.http` (the hash over the method, the path and
 * the redacted sorted query, so a rotated key or a reordered query lands on the same file) or
 * `<directory>/<group>/<name>.http` when `nameFor` names it. [RecordMode.New] keeps an existing file,
 * [RecordMode.Overwrite] rewrites it (byte-identical for an unchanged response: no timestamp is written). Writes
 * go to a temporary file in the target directory and are moved into place with an atomic move where the file
 * system supports one (otherwise a plain replace).
 *
 * Threading: serialisation, scrubbing and the write run in `withContext(Dispatchers.IO)`, so the caller's
 * thread never blocks (transport contract point 8). [written], [kept] and [refused] are thread-safe snapshots.
 * An I/O failure or a surviving secret never reaches the caller: it lands in [refused] with a WARN log line, and
 * the delegate's result is still returned. Cancellation is thrown. The recorder logs the target path, a size
 * and, for an I/O failure, the exception's class name with the exception attached to the event for its stack
 * trace (an I/O exception's message is a path); a scrubber refusal attaches nothing. Never a header, body,
 * query or secret.
 *
 * @param directory the root directory of the fixture files, for example the consumer's `src/test/resources/fixtures`.
 * @param gate the gate; obtainable only from [RecordingGate.evaluate].
 * @param redaction what to hide; the default redacts by token with no secrets.
 * @param warnAboveBytes the body size above which a WARN suggests a trimmed, hand-edited fixture;
 *   [DEFAULT_WARN_ABOVE_BYTES] by default. The file is still written.
 * @throws IllegalArgumentException if [warnAboveBytes] is negative.
 */
public class FixtureRecorder(
    /** The root directory of the fixture files. */
    public val directory: Path,
    gate: RecordingGate.Enabled,
    /** What is hidden in the files. */
    public val redaction: Redaction = Redaction(),
    /** The body size above which a write is logged at WARN. */
    public val warnAboveBytes: Int = DEFAULT_WARN_ABOVE_BYTES,
) {
    init {
        require(warnAboveBytes >= 0) { "warnAboveBytes must not be negative" }
    }

    /** How an existing file is treated, from the gate. */
    public val mode: RecordMode = gate.mode

    private val log = logger("com.spartanlabs.webtools.http.testing.FixtureRecorder")
    private val lock = Any()
    private val writtenFiles = ArrayList<Path>()
    private val keptFiles = ArrayList<Path>()
    private val refusedFiles = ArrayList<Path>()

    /** The files written so far; a snapshot. */
    public val written: List<Path> get() = synchronized(lock) { writtenFiles.toList() }

    /** The existing files kept untouched under [RecordMode.New]; a snapshot. */
    public val kept: List<Path> get() = synchronized(lock) { keptFiles.toList() }

    /** The files refused (a surviving secret or an I/O failure); a snapshot. */
    public val refused: List<Path> get() = synchronized(lock) { refusedFiles.toList() }

    /**
     * Decorates [delegate]: every successful response it returns is recorded under `<directory>/<group>/`.
     *
     * @param delegate the live transport.
     * @param group one or more `/`-separated directory segments of `[A-Za-z0-9._-]+`, not `.` or `..` and not
     *   ending in `.`, for example `opendota` or `steam/v2`.
     * @param nameFor names the file (one segment of the same rule, without `.http`) for a request, or returns
     *   `null` for the generated name; the default names nothing. A result that breaks the rule throws
     *   `IllegalArgumentException` from the decorator's `execute`, after the delegate returned.
     * @return the decorating transport; `close()` closes [delegate].
     * @throws IllegalArgumentException if [group] breaks the rule.
     */
    public fun wrap(delegate: HttpTransport, group: String, nameFor: (HttpRequest) -> String? = { null }): HttpTransport {
        FixtureFileNames.requireGroup(group)
        return RecordingHttpTransport(delegate) { request, response -> record(request, response, group, nameFor) }
    }

    override fun toString(): String =
        synchronized(lock) { "FixtureRecorder(directory=$directory, mode=$mode, written=${writtenFiles.size}, kept=${keptFiles.size}, refused=${refusedFiles.size})" }

    //region recording
    private suspend fun record(request: HttpRequest, response: HttpResponse, group: String, nameFor: (HttpRequest) -> String?) {
        val name = nameFor(request)?.also(FixtureFileNames::requireName)
        val target = FixtureFileNames.path(directory, group, request, name, redaction)
        val outcome = withContext(Dispatchers.IO) { serialise(request, response).flatMap { persist(target, it) } }
        outcome.fold(
            onSuccess = { stored -> noteStored(target, stored, response.bodySize) },
            onFailure = { problem -> noteRefused(target, problem) },     // cancellation is not here: catchNonFatal rethrows it
        )
    }

    // The comment, the redacted response headers and the body as a Fixture, serialised and scrubbed.
    private fun serialise(request: HttpRequest, response: HttpResponse): Result<ByteArray> {
        val headers = HttpHeaders {
            response.headers.entries().forEach { entry ->
                if (DROPPED.none { HttpSyntax.equalsName(it, entry.name) }) {
                    val sensitive = entry.isSensitive || SensitiveNames.isSensitiveName(entry.name)
                    add(entry.name, if (sensitive) redaction.placeholder else entry.value)
                }
            }
        }
        val bytes = Fixture(response.status, headers, response.body()).toBytes(listOf(FixtureFileNames.comment(request, redaction)))
        return redaction.scrubber.scrub(bytes)
    }

    private fun persist(target: Path, bytes: ByteArray): Result<Stored> = catchNonFatal {
        if (mode == RecordMode.New && Files.exists(target)) Stored.Kept
        else {
            writeAtomically(target, bytes)
            Stored.Written(bytes.size)
        }
    }

    // A temporary file in the target directory, then ATOMIC_MOVE (fallback REPLACE_EXISTING where an atomic
    // move is unsupported); the temporary file is deleted on any failure.
    private fun writeAtomically(target: Path, bytes: ByteArray) {
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
        catchNonFatal { Files.write(temp, bytes); move(temp, target) }
            .onFailure { Files.deleteIfExists(temp) }
            .getOrThrow()
    }

    private fun move(temp: Path, target: Path) {
        val atomic = catchNonFatal { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE) }
        if (atomic.exceptionOrNull() is AtomicMoveNotSupportedException) Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        else atomic.getOrThrow()
    }

    private fun noteStored(target: Path, stored: Stored, bodySize: Int) {
        when (stored) {
            Stored.Kept -> {
                synchronized(lock) { keptFiles.add(target) }
                log.info("kept existing fixture {}", target)
            }
            is Stored.Written -> {
                synchronized(lock) { writtenFiles.add(target) }
                log.info("recorded fixture {} ({} bytes)", target, stored.size)
                if (bodySize > warnAboveBytes) log.warn("fixture {} holds a body of {} bytes (above {}); prefer a trimmed, hand-edited fixture", target, bodySize, warnAboveBytes)
            }
        }
    }

    // The line carries the class name; the throwable is attached for the stack trace (an I/O exception's message
    // is a path). A scrubber refusal attaches nothing: its message could otherwise be made to carry data.
    private fun noteRefused(target: Path, problem: Throwable) {
        synchronized(lock) { refusedFiles.add(target) }
        if (problem is SecretSurvived) log.warn("refused to write fixture {}: a secret survived scrubbing", target)
        else log.warn("refused to write fixture {}: {}", target, problem.javaClass.name, problem)
    }

    private sealed interface Stored {
        object Kept : Stored
        class Written(val size: Int) : Stored
    }
    //endregion

    /** The defaults. */
    public companion object {
        /** The default [warnAboveBytes], 256 KiB. */
        public const val DEFAULT_WARN_ABOVE_BYTES: Int = 262_144

        // Hop-by-hop and cookie headers a replay must not carry.
        private val DROPPED: List<String> = listOf("Set-Cookie", "Set-Cookie2", "Transfer-Encoding", "Connection", "Keep-Alive")
    }
}
