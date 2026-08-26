package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ConnectorTest {

    private val log = LoggerFactory.getLogger(ConnectorTest::class.java)

    @Test
    fun `open throws for a malformed url`() {
        log.info("Verifying open() rejects a malformed URL")
        val connector = Connector()
        assertFailsWith<IllegalArgumentException> {
            connector open "not a valid url"
        }
    }

    @Test
    fun `get returns null for an unreachable host instead of throwing`() {
        log.info("Verifying get() fails gracefully for an unreachable host")
        val connector = Connector()
        // This resolves fine as a URL but cannot actually be connected to in a
        // sandboxed/offline test environment, exercising the UnirestException catch path.
        val result = connector get "http://127.0.0.1:1/definitely-not-listening"
        assertNull(result)
    }

    @Test
    fun `skrape throws for a malformed url`() {
        log.info("Verifying skrape() rejects a malformed URL")
        val connector = Connector()
        assertFailsWith<IllegalArgumentException> {
            connector skrape "not a valid url"
        }
    }

    @Test
    fun `next throws when no connection has been opened`() {
        log.info("Verifying next() throws without an open connection")
        val connector = Connector()
        // reader is null until open() successfully creates one, so this should
        // fail fast rather than silently returning something.
        assertFailsWith<NullPointerException> {
            connector.next()
        }
    }
}
