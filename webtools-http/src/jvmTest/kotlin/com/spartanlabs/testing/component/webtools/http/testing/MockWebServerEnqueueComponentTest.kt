package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.enqueue
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.QueueDispatcher
import mockwebserver3.RecordedRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Level 2 - `MockWebServer.enqueue(fixture, method)`: it queues `fixture.toMockResponse(method)` on the default dispatcher (seen through
 * `QueueDispatcher.peek()`), the method is passed through, and a server whose dispatcher was replaced is refused with an
 * `IllegalStateException` naming the default queue dispatcher. No server is started.
 */
class MockWebServerEnqueueComponentTest {

    private val fixture = Fixture(200, HttpHeaders { add("X-A", "1") }, "body".encodeToByteArray())

    @Test
    fun `enqueue queues the fixture's response on the default dispatcher`() {
        val server = MockWebServer()
        server.enqueue(fixture)
        val queued = (server.dispatcher as QueueDispatcher).peek()
        assertEquals("HTTP/1.1 200 OK", queued.status)
        assertEquals("1", queued.headers["X-A"])
        assertEquals("4", queued.headers["Content-Length"])
    }

    @Test
    fun `the method is passed through`() {
        val server = MockWebServer()
        server.enqueue(fixture, HttpMethod.Head)
        val queued = (server.dispatcher as QueueDispatcher).peek()
        assertNull(queued.body)
        assertEquals("4", queued.headers["Content-Length"])
    }

    @Test
    fun `a server with a custom dispatcher is refused`() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
        }
        val failure = assertFailsWith<IllegalStateException> { server.enqueue(fixture) }
        assertTrue("default queue dispatcher" in failure.message!!, failure.message)
    }
}
