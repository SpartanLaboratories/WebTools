package com.spartanlabs.testing.integration.webtools.http.testing

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.RequestBody
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.FixtureRecorder
import com.spartanlabs.webtools.http.testing.RecordMode
import com.spartanlabs.webtools.http.testing.RecordingGate
import com.spartanlabs.webtools.http.testing.Redaction
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.streams.asSequence
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 3 - `FixtureRecorder` against real files in a temporary directory, with a `FakeHttpTransport` as the
 * live delegate, under `runBlocking` (never `VirtualClock`: the write is real I/O). What is written and
 * never written, the dropped and redacted headers, the redacted comment, secret scrubbing and refusal, the
 * file name rules, the two modes, an unwritable target, the snapshots, `close()` and the untouched result.
 */
class FixtureRecorderIntegrationTest {

    private lateinit var directory: Path
    private val canary = "SECRET-CANARY-7f3a"
    private val enabled = RecordingGate.evaluate(mapOf("WEBTOOLS_HTTP_RECORD" to "1")) as RecordingGate.Enabled
    private val overwrite = RecordingGate.evaluate(mapOf("WEBTOOLS_HTTP_RECORD" to "overwrite")) as RecordingGate.Enabled
    private val logbackLogger = LoggerFactory.getLogger("com.spartanlabs.webtools.http.testing.FixtureRecorder") as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeTest
    fun setUp() {
        directory = Files.createTempDirectory("fixture-recorder")
        appender.start()
        logbackLogger.addAppender(appender)
    }

    @AfterTest
    fun tearDown() {
        logbackLogger.detachAppender(appender)
        appender.stop()
        directory.toFile().deleteRecursively()
    }

    private fun url(text: String): Url = Url.parse(text).getOrThrow()
    private fun get(text: String, headers: HttpHeaders = HttpHeaders()): HttpRequest = HttpRequest(HttpMethod.Get, url(text)) { this.headers = headers }
    private fun files(): List<Path> = Files.walk(directory).use { paths -> paths.asSequence().filter { Files.isRegularFile(it) }.toList() }
    private fun logText(): String = appender.list.joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message ?: "") }

    @Test
    fun `successful responses are recorded and a transport failure is not`() = runBlocking {
        val fake = FakeHttpTransport()
            .on(RequestMatch.get("/ok"), Step.respond(200, body = "ok"))
            .on(RequestMatch.get("/limited"), Step.respond(429, HttpHeaders { add("Retry-After", "30") }))
            .on(RequestMatch.get("/down"), Step.fail(TransportFailure.Timeout()))
        val recorder = FixtureRecorder(directory, enabled)
        val transport = recorder.wrap(fake, "g")
        assertEquals(200, transport.execute(get("https://h/ok")).getOrThrow().status)
        assertEquals(429, transport.execute(get("https://h/limited")).getOrThrow().status)
        assertIs<TransportFailure.Timeout>(transport.execute(get("https://h/down")).exceptionOrNull())
        assertEquals(2, recorder.written.size)
        assertEquals(2, files().size)
        assertTrue(files().all { it.parent == directory.resolve("g") })
    }

    @Test
    fun `the file holds the comment the status line the response headers a blank line and the body and no request header`() = runBlocking {
        val fake = FakeHttpTransport().on(
            RequestMatch.Any,
            Step.respond(200, HttpHeaders { add("Content-Type", "application/json"); add("ETag", "\"v1\"") }, "{\"a\":1}"),
        )
        val recorder = FixtureRecorder(directory, enabled)
        val request = HttpRequest(HttpMethod.Post, url("https://api.example.test:8443/api/players/1?limit=20")) {
            headers = HttpHeaders { add("X-Canary", canary); add("Authorization", "Bearer $canary") }
            body = RequestBody("body-$canary".encodeToByteArray(), "text/plain")
        }
        recorder.wrap(fake, "g").execute(request).getOrThrow()
        val text = recorder.written.single().readText()
        assertEquals(
            "# request: POST https://api.example.test:8443/api/players/1?limit=20\nHTTP/1.1 200 OK\nContent-Type: application/json\nETag: \"v1\"\n\n{\"a\":1}",
            text,
        )
        assertFalse(canary in text)
    }

    @Test
    fun `cookie and hop-by-hop headers are dropped`() = runBlocking {
        val headers = HttpHeaders {
            add("Set-Cookie", "a=1"); add("set-cookie2", "b=2"); add("Transfer-Encoding", "chunked"); add("Connection", "close"); add("Keep-Alive", "timeout=5")
            add("Content-Type", "text/plain")
        }
        val recorder = FixtureRecorder(directory, enabled)
        recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, headers, "x")), "g").execute(get("https://h/")).getOrThrow()
        assertEquals("# request: GET https://h/\nHTTP/1.1 200 OK\nContent-Type: text/plain\n\nx", recorder.written.single().readText())
    }

    @Test
    fun `sensitive response headers keep their name and carry the placeholder`() = runBlocking {
        val headers = HttpHeaders { add("X-Api-Key", canary); add("Authorization", canary); add("X-RateLimit-Remaining", "59") }
        val recorder = FixtureRecorder(directory, enabled)
        recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, headers, "")), "g").execute(get("https://h/")).getOrThrow()
        val text = recorder.written.single().readText()
        assertEquals("# request: GET https://h/\nHTTP/1.1 200 OK\nX-Api-Key: REDACTED\nAuthorization: REDACTED\nX-RateLimit-Remaining: 59\n\n", text)
    }

    @Test
    fun `a sensitive query value is redacted in the comment and code is not`() = runBlocking {
        val recorder = FixtureRecorder(directory, enabled)
        recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond()), "g")
            .execute(get("https://h/cb?code=abc123&api_key=$canary&accessToken=$canary&flag")).getOrThrow()
        val text = recorder.written.single().readText()
        assertTrue(text.startsWith("# request: GET https://h/cb?accessToken=REDACTED&api_key=REDACTED&code=abc123&flag\n"), text)
        assertFalse(canary in text)
    }

    @Test
    fun `a secret echoed in the body is replaced`() = runBlocking {
        val recorder = FixtureRecorder(directory, enabled, Redaction(secrets = listOf(canary, "p@ss w0rd!")))
        val body = "{\"token\":\"$canary\",\"form\":\"p%40ss+w0rd%21\",\"query\":\"p%40ss%20w0rd%21\",\"raw\":\"p@ss w0rd!\"}"
        recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = body)), "g")
            .execute(get("https://h/")).getOrThrow()
        val text = recorder.written.single().readText()
        assertTrue(text.endsWith("\n\n{\"token\":\"REDACTED\",\"form\":\"REDACTED\",\"query\":\"REDACTED\",\"raw\":\"REDACTED\"}"), text)
        assertFalse(canary in text)
        assertFalse("w0rd" in text)
    }

    @Test
    fun `a secret surviving as lower-case escapes refuses the file and nothing is written or logged`() = runBlocking {
        // "abc%2F" is replaced; its lower-case-hex twin "abc%2f" is only detected, so the file is refused.
        val recorder = FixtureRecorder(directory, enabled, Redaction(secrets = listOf("abc/def")))
        val result = recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = "x=abc%2fdef")), "g")
            .execute(get("https://h/"))
        assertEquals(200, result.getOrThrow().status)
        assertEquals(1, recorder.refused.size)
        assertTrue(recorder.written.isEmpty())
        assertTrue(files().isEmpty(), "files: ${files()}")
        assertTrue(appender.list.any { it.formattedMessage.contains("a secret survived scrubbing") })
        assertFalse("abc/def" in logText())
        assertFalse("abc%2f" in logText())
    }

    @Test
    fun `no temporary file remains`() = runBlocking {
        val recorder = FixtureRecorder(directory, enabled)
        recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond()), "g").execute(get("https://h/a")).getOrThrow()
        assertTrue(files().none { it.name.endsWith(".tmp") })
        assertEquals(1, files().size)
    }

    @Test
    fun `the generated name is method slug and hash and is stable under query order and key rotation`() = runBlocking {
        val recorder = FixtureRecorder(directory, overwrite)
        val transport = recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond()), "g")
        transport.execute(get("https://h/api/players/1?b=2&a=1&api_key=one-key")).getOrThrow()
        transport.execute(get("https://h/api/players/1?a=1&b=2&api_key=two-key")).getOrThrow()
        transport.execute(get("https://h/api/players/1?a=1&b=3")).getOrThrow()
        val names = recorder.written.map { it.name }
        //language=RegExp
        assertTrue(Regex("get-api-players-1-[0-9a-f]{8}\\.http").matches(names[0]), names[0])
        assertEquals(names[0], names[1])
        assertNotEquals(names[0], names[2])
        assertEquals(2, files().size)
    }

    @Test
    // runBlocking<Unit>: the body ends in assertFailsWith, whose value would otherwise become the method's return
    // type, and JUnit Jupiter runs only void methods.
    fun `nameFor names the file and a bad group is refused at wrap`() = runBlocking<Unit> {
        val recorder = FixtureRecorder(directory, enabled)
        recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond()), "steam/v2") { "player-1" }.execute(get("https://h/x")).getOrThrow()
        assertEquals(directory.resolve("steam").resolve("v2").resolve("player-1.http"), recorder.written.single())
        assertFailsWith<IllegalArgumentException> { recorder.wrap(FakeHttpTransport(), "bad group") }
        assertFailsWith<IllegalArgumentException> { recorder.wrap(FakeHttpTransport(), "a/../b") }
        val badName = recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond()), "g") { "no/slash" }
        assertFailsWith<IllegalArgumentException> { badName.execute(get("https://h/x")) }
    }

    @Test
    fun `New keeps an existing file and Overwrite rewrites it byte-identically`() = runBlocking {
        val fake = FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = "v1"), Step.respond(200, body = "v2"))
        val keeping = FixtureRecorder(directory, enabled)
        val transport = keeping.wrap(fake, "g") { "same" }
        transport.execute(get("https://h/")).getOrThrow()
        val first = keeping.written.single().readBytes()
        transport.execute(get("https://h/")).getOrThrow()
        assertEquals(1, keeping.written.size)
        assertEquals(listOf(keeping.written.single()), keeping.kept)
        assertContentEquals(first, keeping.written.single().readBytes())
        assertTrue(appender.list.any { it.formattedMessage.startsWith("kept existing fixture") })

        val rewriting = FixtureRecorder(directory, overwrite)
        val again = rewriting.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = "v2")), "g") { "same" }
        again.execute(get("https://h/")).getOrThrow()
        val second = rewriting.written.single().readBytes()
        assertFalse(first.contentEquals(second))
        again.execute(get("https://h/")).getOrThrow()
        assertContentEquals(second, rewriting.written.last().readBytes())
        assertEquals(RecordMode.Overwrite, rewriting.mode)
        assertEquals(RecordMode.New, keeping.mode)
    }

    @Test
    fun `an unwritable target lands in refused and the delegate's result is still returned`() = runBlocking {
        Files.writeString(directory.resolve("blocked"), "a file where the directory should be")
        val recorder = FixtureRecorder(directory, enabled)
        val result = recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond(201)), "blocked").execute(get("https://h/"))
        assertEquals(201, result.getOrThrow().status)
        assertEquals(1, recorder.refused.size)
        assertTrue(recorder.written.isEmpty())
        assertTrue(appender.list.any { it.formattedMessage.startsWith("refused to write fixture") && "Exception" in it.formattedMessage })
    }

    @Test
    fun `written kept and refused are snapshots`() = runBlocking {
        Files.writeString(directory.resolve("blocked"), "a file where the directory should be")
        val recorder = FixtureRecorder(directory, enabled)
        val transport = recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond()), "g") { "same" }
        val writtenBefore = recorder.written
        transport.execute(get("https://h/a")).getOrThrow()                // written
        val keptBefore = recorder.kept
        transport.execute(get("https://h/a")).getOrThrow()                // New keeps the existing file
        val refusedBefore = recorder.refused
        recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond()), "blocked").execute(get("https://h/a")).getOrThrow()   // refused
        assertTrue(writtenBefore.isEmpty())
        assertTrue(keptBefore.isEmpty())
        assertTrue(refusedBefore.isEmpty())
        assertEquals(1, recorder.written.size)
        assertEquals(1, recorder.kept.size)
        assertEquals(1, recorder.refused.size)
        assertEquals("FixtureRecorder(directory=$directory, mode=New, written=1, kept=1, refused=1)", recorder.toString())
    }

    @Test
    fun `close forwards to the delegate and the result is the delegate's own`() = runBlocking {
        val prebuilt = HttpResponse(200, url("https://h/final"))
        val fake = FakeHttpTransport().on(RequestMatch.Any, Step.respond { prebuilt })
        val transport: HttpTransport = FixtureRecorder(directory, enabled).wrap(fake, "g")
        assertSame(prebuilt, transport.execute(get("https://h/")).getOrThrow())
        transport.close()
        assertTrue(fake.isClosed)
    }

    @Test
    fun `a body above warnAboveBytes is still written and warned about`() = runBlocking {
        val recorder = FixtureRecorder(directory, enabled, warnAboveBytes = 10)
        recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = "x".repeat(11))), "g").execute(get("https://h/")).getOrThrow()
        assertEquals(1, recorder.written.size)
        assertTrue(appender.list.any { it.formattedMessage.contains("holds a body of 11 bytes (above 10)") })
        assertFailsWith<IllegalArgumentException> { FixtureRecorder(directory, enabled, warnAboveBytes = -1) }
        assertEquals(262_144, FixtureRecorder.DEFAULT_WARN_ABOVE_BYTES)
        assertEquals(1, directory.resolve("g").listDirectoryEntries().size)
    }

    @Test
    fun `concurrent recordings to one name leave one complete file and account for every call`() = runBlocking {
        val recorder = FixtureRecorder(directory, RecordingGate.evaluate(mapOf("WEBTOOLS_HTTP_RECORD" to "all")) as RecordingGate.Enabled)
        val fake = FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = "same body"))
        val transport = recorder.wrap(fake, "g") { "same" }
        val request = get("https://h/a")
        // Eight callers released together, so the atomic moves onto one target race; any loser lands in refused, and
        // the file is whole either way.
        val start = CompletableDeferred<Unit>()
        val callers = (1..8).map { async(Dispatchers.Default) { start.await(); transport.execute(request) } }
        start.complete(Unit)
        val results = callers.awaitAll()
        assertTrue(results.all { it.isSuccess && it.getOrThrow().status == 200 }, results.toString())
        val files = directory.resolve("g").listDirectoryEntries().filter { Files.isRegularFile(it) }
        assertEquals(listOf("same.http"), files.map { it.name }, "files left in g: $files")
        assertEquals("same body", Fixture.parse(files.single().readBytes(), "same.http").bodyText())
        assertEquals(8, recorder.written.size + recorder.refused.size, recorder.toString())
        assertTrue(recorder.written.isNotEmpty(), recorder.toString())
        val events = appender.list.map { it.formattedMessage }
        assertEquals(8, events.size, events.toString())
        assertTrue(events.all { (it.startsWith("recorded fixture") || it.startsWith("refused to write fixture")) && "same.http" in it }, events.toString())
    }
}
