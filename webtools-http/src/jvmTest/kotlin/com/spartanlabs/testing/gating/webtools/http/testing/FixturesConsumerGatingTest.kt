package com.spartanlabs.testing.gating.webtools.http.testing

import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.WallClock
import com.spartanlabs.webtools.http.testing.FakeHttpTransport
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.FixtureRecorder
import com.spartanlabs.webtools.http.testing.RecordingGate
import com.spartanlabs.webtools.http.testing.RequestMatch
import com.spartanlabs.webtools.http.testing.Step
import com.spartanlabs.webtools.http.testing.VirtualClock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Level 1 - the permanent consumer of the fixtures in `jvmTest`, compiled **without any opt-in**: it names
 * `FakeHttpTransport` as an `HttpTransport`, reads `VirtualClock.wallClock` as a `WallClock`, obtains a
 * `RecordingGate.Enabled` from the gate, wraps the fake in a `FixtureRecorder` and loads a `Fixture`. It
 * cannot implement or subclass a gated type, which is the point. Compiling it proves `jvmTest` reaches the
 * fixtures through the association and is still a consumer; its class in `gatingTest`'s result XML proves the
 * level tasks reach the fixtures.
 */
class FixturesConsumerGatingTest {

    // A gated type in a function signature, satisfied by the fake.
    private fun status(transport: HttpTransport, request: HttpRequest): Int = runBlocking { transport.execute(request).getOrThrow().status }

    private val request = HttpRequest(HttpMethod.Get, Url.parse("https://example.com/a").getOrThrow())

    @Test
    fun `the fake is an HttpTransport and answers from a step`() {
        val fake = FakeHttpTransport().on(RequestMatch.get("/a"), Step.respond(200, body = "ok"))
        assertEquals(200, status(fake, request))
        assertEquals(1, fake.calls.size)
    }

    @Test
    fun `the virtual clock's wall clock is a WallClock`() {
        val clock: WallClock = VirtualClock(TestCoroutineScheduler(), startEpochMillis = 1_000L).wallClock
        assertEquals(1_000L, clock.nowEpochMillis())
    }

    @Test
    fun `the recorder wraps the fake as an HttpTransport whose close closes the fake`() {
        val gate = assertIs<RecordingGate.Enabled>(RecordingGate.evaluate(mapOf("WEBTOOLS_HTTP_RECORD" to "1")))
        // An unresolved path: constructing the recorder and wrapping touch no file; the write is Level 3's.
        val fake = FakeHttpTransport()
        val wrapped: HttpTransport = FixtureRecorder(Path.of("gating"), gate).wrap(fake, "gating")
        wrapped.close()
        assertTrue(fake.isClosed)
    }

    @Test
    fun `a fixture parses`() {
        val fixture = Fixture.parse("HTTP/1.1 200 OK\nContent-Type: text/plain\n\nhello".encodeToByteArray())
        assertEquals(200, fixture.status)
        assertEquals("hello", fixture.bodyText())
    }
}
