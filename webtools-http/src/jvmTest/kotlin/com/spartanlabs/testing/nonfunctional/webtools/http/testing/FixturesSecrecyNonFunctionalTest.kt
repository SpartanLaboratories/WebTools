package com.spartanlabs.testing.nonfunctional.webtools.http.testing

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.RequestBody
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.encodeQueryComponent
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.FixtureError
import com.spartanlabs.webtools.http.testing.FixtureRecorder
import com.spartanlabs.webtools.http.testing.RecordingGate
import com.spartanlabs.webtools.http.testing.Redaction
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.UnmatchedRequestError
import com.spartanlabs.webtools.http.testing.VirtualClock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level 4c - secrecy: two canaries, one plain (`SECRET-CANARY-7f3a`) and one with reserved characters
 * (`SECRET/CANARY+7f3a&x=y`, so its query-encoded and form-encoded forms differ from the raw text), placed in a
 * request's query, header and body and in a response's header and body, never appear in any `toString()` or
 * exception message of the fixtures' types, nor in the recorder's log output on a write or on a refusal, nor
 * in the written fixture - in any of their forms: raw, `encodeQueryComponent`, `URLEncoder.encode`, and each
 * encoded form with lower-cased hex.
 */
class FixturesSecrecyNonFunctionalTest {

    private val canary = "SECRET-CANARY-7f3a"
    private val canary2 = "SECRET/CANARY+7f3a&x=y"
    private val logbackLogger = LoggerFactory.getLogger("com.spartanlabs.webtools.http.testing.FixtureRecorder") as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeTest
    fun attach() {
        appender.start()
        logbackLogger.addAppender(appender)
    }

    @AfterTest
    fun detach() {
        logbackLogger.detachAppender(appender)
        appender.stop()
    }

    // Every %XX escape with its two hex digits lower-cased.
    //language=RegExp
    private fun lowerHex(encoded: String): String = Regex("%[0-9A-F]{2}").replace(encoded) { it.value.lowercase() }

    private val queryForm2 = encodeQueryComponent(canary2).getOrThrow()
    private val urlEncoderForm2 = URLEncoder.encode(canary2, Charsets.UTF_8)
    private val forbidden: List<String> = listOf(canary, canary2, queryForm2, urlEncoderForm2, lowerHex(queryForm2), lowerHex(urlEncoderForm2)).distinct()

    private val url = Url.parse("https://h/path?api_key=$canary&q=$canary&k=$queryForm2").getOrThrow()
    private val request = HttpRequest(HttpMethod.Post, url) {
        headers = HttpHeaders { add("X-Canary", canary); add("X-Canary2", canary2); addSensitive("Authorization", canary) }
        body = RequestBody("$canary $canary2".encodeToByteArray(), "text/plain")
    }
    private val responseHeaders = HttpHeaders { add("X-Api-Key", canary); add("X-Note", canary); add("X-Note2", canary2) }
    private val responseBody = "$canary $canary2"

    private fun assertClean(text: String?, what: String) {
        assertFalse(text == null, "$what is null")
        forbidden.forEach { form -> assertFalse(form in text!!, "$what leaks the canary form '$form': $text") }
    }

    @Test
    fun `no toString or message of the fixtures types holds a canary in any form`() = runBlocking {
        val step = Step.respond(200, responseHeaders, responseBody)
        val fake = FakeHttpTransport().on(RequestMatch.header("X-Canary", canary), step)
        fake.execute(request).getOrThrow()
        assertClean(fake.toString(), "FakeHttpTransport")
        assertClean(fake.calls.single().toString(), "RecordedCall")
        assertClean(step.toString(), "Step")

        val strict = FakeHttpTransport()
        val unmatched = assertFailsWith<UnmatchedRequestError> { strict.execute(request) }
        assertClean(unmatched.message, "UnmatchedRequestError.message")
        assertClean(unmatched.redactedUrl, "UnmatchedRequestError.redactedUrl")
        assertClean(unmatched.toString(), "UnmatchedRequestError")

        val fixture = Fixture(200, responseHeaders, responseBody.encodeToByteArray())
        assertClean(fixture.toString(), "Fixture")
        val error = assertFailsWith<FixtureError> { Fixture.parse("HTTP/1.1 200\nX: $canary $canary2\u0000\n\n$canary $canary2".encodeToByteArray(), "origin") }
        assertClean(error.message, "FixtureError.message")
        assertClean(error.toString(), "FixtureError")
        val error2 = assertFailsWith<FixtureError> { Fixture.parse("$canary $canary2\n".encodeToByteArray()) }
        assertClean(error2.message, "FixtureError (invalid line)")

        assertClean(Redaction(secrets = listOf(canary, canary2), extraSensitiveQueryParams = setOf(canary, canary2), keepQueryParams = setOf(canary, canary2)).toString(), "Redaction")
        assertClean(RecordingGate.evaluate(mapOf("WEBTOOLS_HTTP_RECORD" to canary)).toString(), "RecordingGate.Disabled (value)")
        assertClean(RecordingGate.evaluate(mapOf("WEBTOOLS_HTTP_RECORD" to canary2)).toString(), "RecordingGate.Disabled (value 2)")
        assertClean(RecordingGate.evaluate(mapOf("WEBTOOLS_HTTP_RECORD" to "1", "CI" to canary2)).toString(), "RecordingGate.Disabled (CI value)")
        assertClean(RecordingGate.evaluate(mapOf("WEBTOOLS_HTTP_RECORD" to "1")).toString(), "RecordingGate.Enabled")
        assertClean(VirtualClock(TestCoroutineScheduler()).toString(), "VirtualClock")
        assertClean(VirtualClock(TestCoroutineScheduler()).wallClock.toString(), "VirtualClock.wallClock")
    }

    @Test
    fun `the recorder's log and output hold a canary in no form neither on a write nor on a refusal`() = runBlocking {
        val directory = Files.createTempDirectory("secrecy")
        try {
            val gate = RecordingGate.evaluate(mapOf("WEBTOOLS_HTTP_RECORD" to "1")) as RecordingGate.Enabled
            val recorder = FixtureRecorder(directory, gate, Redaction(secrets = listOf(canary, canary2)))
            val fake = FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, responseHeaders, responseBody))
            val written = recorder.wrap(fake, "g")
            assertClean(written.toString(), "the decorating transport")
            written.execute(request).getOrThrow()
            // The full lower-case percent-escape of the plain canary is detected but not replaced, so the file is refused.
            val escaped = canary.encodeToByteArray().joinToString("") { "%%%02x".format(it) }
            val refused = recorder.wrap(FakeHttpTransport().on(RequestMatch.Any, Step.respond(200, body = "x=$escaped")), "g")
            refused.execute(request).getOrThrow()
            assertTrue(recorder.written.size == 1 && recorder.refused.size == 1, recorder.toString())
            val log = appender.list.joinToString("\n") { event -> event.formattedMessage + (event.throwableProxy?.message ?: "") }
            assertTrue(log.contains("recorded fixture") && log.contains("refused to write fixture"), log)
            assertClean(log, "recorder log")
            assertClean(recorder.toString(), "FixtureRecorder")
            assertClean(Files.readString(recorder.written.single()), "written fixture")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
