package com.spartanlabs.testing.component.webtools

import com.spartanlabs.webtools.Connector
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Tag
import org.slf4j.LoggerFactory
import java.io.EOFException
import java.net.MalformedURLException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Level 2 - isolated behaviour of the Connector component; every assertion here is about
// how Connector maps outcomes to Result, with no successful external side effect.
@Tag("component")
class ConnectorTest {

    private val log = LoggerFactory.getLogger(ConnectorTest::class.java)

    @Test
    fun `open fails with a MalformedURLException for a malformed url`() {
        log.info("Verifying open() rejects a malformed URL")
        val connector = Connector()
        val outcome = connector open "not a valid url"
        assertTrue(outcome.isFailure, "Expected open() to report a failure")
        assertIs<MalformedURLException>(outcome.exceptionOrNull())
    }

    @Test
    fun `open fails for a well-formed url that is not absolute`() {
        log.info("Verifying open() rejects a relative URL")
        val connector = Connector()
        // Parses as a URI but has no scheme, so it cannot be turned into a URL.
        assertIs<MalformedURLException>((connector open "google.com").exceptionOrNull())
    }

    @Test
    fun `a failed open leaves the connector reusable`() {
        log.info("Verifying a failed open() does not wedge the connector")
        val connector = Connector()
        assertTrue((connector open "not a valid url").isFailure)
        // Regression guard: open() used to set isOpen before it knew the connection had
        // succeeded, so a failed open left every later open() spinning in waitForTurn()
        // forever. A second attempt must still return rather than hang.
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            assertTrue((connector open "still not a valid url").isFailure)
        }
    }

    @Test
    fun `get fails for an unreachable host instead of throwing`() {
        log.info("Verifying get() fails gracefully for an unreachable host")
        val connector = Connector()
        // This resolves fine as a URL but cannot actually be connected to in a
        // sandboxed/offline test environment, exercising the request failure path.
        val outcome = connector get "http://127.0.0.1:1/definitely-not-listening"
        assertTrue(outcome.isFailure, "Expected get() to report a failure")
    }

    @Test
    fun `skrape fails with a MalformedURLException for a malformed url`() {
        log.info("Verifying skrape() rejects a malformed URL")
        val connector = Connector()
        assertIs<MalformedURLException>((connector skrape "not a valid url").exceptionOrNull())
    }

    @Test
    fun `download fails with a MalformedURLException for a malformed url`() {
        log.info("Verifying download() rejects a malformed URL")
        val connector = Connector()
        assertIs<MalformedURLException>((connector download "not a valid url").exceptionOrNull())
    }

    @Test
    fun `next fails when no connection has been opened`() {
        log.info("Verifying next() fails without an open connection")
        val connector = Connector()
        val outcome = connector.next()
        assertTrue(outcome.isFailure, "Expected next() to report a failure")
        // requireNotNull(reader) reports the misuse, rather than the raw NullPointerException
        // the reader!! dereference used to throw.
        assertIs<IllegalArgumentException>(outcome.exceptionOrNull())
        assertFalse(outcome.exceptionOrNull() is EOFException)
    }

    @Test
    fun `skipping lines fails when no connection has been opened`() {
        log.info("Verifying next(lines) fails without an open connection")
        assertTrue(Connector().next(3).isFailure)
    }

    @Test
    fun `skipping zero lines succeeds even without a connection`() {
        log.info("Verifying next(0) is a no-op")
        assertTrue(Connector().next(0).isSuccess)
    }

    @Test
    fun `hasNext reports false when no connection has been opened`() {
        log.info("Verifying hasNext() is false without an open connection")
        assertFalse(Connector().hasNext())
    }

    @Test
    fun `close succeeds when there is nothing to close`() {
        log.info("Verifying close() is safe on an unopened connector")
        val connector = Connector()
        assertTrue(connector.close().isSuccess)
        assertNull(connector.currentLine)
    }
}
