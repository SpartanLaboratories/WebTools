package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.TransportSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `TransportSettings`: the two default sets hold exactly the planned numbers; `TransportSettings(from) { }`
 * derives a copy and never mutates `from`; the builder is seeded from `from`; changing any one of the ten properties
 * breaks equality and changes `toString()` (hand-written equality drifts when a property is added); every validation
 * rule with its boundary; no message holds a value.
 */
class TransportSettingsComponentTest {

    private val trusted = TransportSettings.TrustedDefaults
    private val untrusted = TransportSettings.UntrustedDefaults

    @Test
    fun `the Trusted defaults hold the planned numbers`() {
        assertEquals(30.seconds, trusted.callTimeout)
        assertEquals(10.seconds, trusted.connectTimeout)
        assertEquals(10.seconds, trusted.readTimeout)
        assertEquals(10.seconds, trusted.writeTimeout)
        assertEquals(5_242_880L, trusted.maxResponseBytes)
        assertEquals(5, trusted.maxRedirects)
        assertEquals(false, trusted.allowHttpsToHttp)
        assertEquals(16, trusted.maxConcurrentRequests)
        assertEquals(5, trusted.maxConcurrentRequestsPerHost)
        assertEquals(4.seconds, trusted.idleConnectionTtl)
    }

    @Test
    fun `the Untrusted defaults hold the planned numbers`() {
        assertEquals(15.seconds, untrusted.callTimeout)
        assertEquals(5.seconds, untrusted.connectTimeout)
        assertEquals(5.seconds, untrusted.readTimeout)
        assertEquals(5.seconds, untrusted.writeTimeout)
        assertEquals(2_097_152L, untrusted.maxResponseBytes)
        assertEquals(3, untrusted.maxRedirects)
        assertEquals(false, untrusted.allowHttpsToHttp)
        assertEquals(8, untrusted.maxConcurrentRequests)
        assertEquals(2, untrusted.maxConcurrentRequestsPerHost)
        assertEquals(4.seconds, untrusted.idleConnectionTtl)
    }

    @Test
    fun `an empty block derives an equal new instance and never mutates from`() {
        val copy = TransportSettings(trusted) {}
        assertEquals(trusted, copy)
        assertEquals(trusted.hashCode(), copy.hashCode())
        assertFalse(copy === trusted)
        val changed = TransportSettings(trusted) { maxRedirects = 6 }
        assertEquals(6, changed.maxRedirects)
        assertEquals(5, trusted.maxRedirects)
        assertEquals(TransportSettings.TrustedDefaults, trusted)
    }

    @Test
    fun `a block that changes only maxRedirects equals the defaults with only that value changed`() {
        val changed = TransportSettings(trusted) { maxRedirects = 2 }
        assertEquals(trusted.callTimeout, changed.callTimeout)
        assertEquals(trusted.connectTimeout, changed.connectTimeout)
        assertEquals(trusted.readTimeout, changed.readTimeout)
        assertEquals(trusted.writeTimeout, changed.writeTimeout)
        assertEquals(trusted.maxResponseBytes, changed.maxResponseBytes)
        assertEquals(2, changed.maxRedirects)
        assertEquals(trusted.allowHttpsToHttp, changed.allowHttpsToHttp)
        assertEquals(trusted.maxConcurrentRequests, changed.maxConcurrentRequests)
        assertEquals(trusted.maxConcurrentRequestsPerHost, changed.maxConcurrentRequestsPerHost)
        assertEquals(trusted.idleConnectionTtl, changed.idleConnectionTtl)
        assertNotEquals(trusted, changed)
    }

    @Test
    fun `the builder is seeded from the value the factory was given`() {
        val raised = TransportSettings(untrusted) { maxConcurrentRequests = maxOf(maxConcurrentRequests, 20) }
        assertEquals(20, raised.maxConcurrentRequests)
        val kept = TransportSettings(trusted) { maxConcurrentRequests = maxOf(maxConcurrentRequests, 8) }
        assertEquals(16, kept.maxConcurrentRequests)
    }

    //region one test per property
    // Changing only the property gives a value that is != the base and prints differently; two identical derivations are
    // equal with an equal hash.
    private fun onlyThisProperty(configure: TransportSettings.Builder.() -> Unit) {
        val once = TransportSettings(trusted, configure)
        val twice = TransportSettings(trusted, configure)
        assertNotEquals(trusted, once)
        assertNotEquals(trusted.toString(), once.toString())
        assertEquals(once, twice)
        assertEquals(once.hashCode(), twice.hashCode())
        assertEquals(once.toString(), twice.toString())
    }

    @Test
    fun `callTimeout takes part in equality and toString`() = onlyThisProperty { callTimeout = 31.seconds }

    @Test
    fun `connectTimeout takes part in equality and toString`() = onlyThisProperty { connectTimeout = 11.seconds }

    @Test
    fun `readTimeout takes part in equality and toString`() = onlyThisProperty { readTimeout = 11.seconds }

    @Test
    fun `writeTimeout takes part in equality and toString`() = onlyThisProperty { writeTimeout = 11.seconds }

    @Test
    fun `maxResponseBytes takes part in equality and toString`() = onlyThisProperty { maxResponseBytes = maxResponseBytes + 1 }

    @Test
    fun `maxRedirects takes part in equality and toString`() = onlyThisProperty { maxRedirects = 6 }

    @Test
    fun `allowHttpsToHttp takes part in equality and toString`() = onlyThisProperty { allowHttpsToHttp = true }

    @Test
    fun `maxConcurrentRequests takes part in equality and toString`() = onlyThisProperty { maxConcurrentRequests = 17 }

    @Test
    fun `maxConcurrentRequestsPerHost takes part in equality and toString`() = onlyThisProperty { maxConcurrentRequestsPerHost = 6 }

    @Test
    fun `idleConnectionTtl takes part in equality and toString`() = onlyThisProperty { idleConnectionTtl = 5.seconds }
    //endregion

    //region validation
    private val timeoutSetters: Map<String, TransportSettings.Builder.(Duration) -> Unit> = mapOf(
        "callTimeout" to { d -> callTimeout = d },
        "connectTimeout" to { d -> connectTimeout = d },
        "readTimeout" to { d -> readTimeout = d },
        "writeTimeout" to { d -> writeTimeout = d },
        "idleConnectionTtl" to { d -> idleConnectionTtl = d },
    )

    private fun refused(configure: TransportSettings.Builder.() -> Unit): String =
        assertFailsWith<IllegalArgumentException> { TransportSettings(trusted, configure) }.message ?: ""

    @Test
    fun `a timeout of 1 ms and of Int MAX_VALUE ms is accepted for each duration`() {
        timeoutSetters.forEach { (name, set) ->
            TransportSettings(trusted) { set(1.milliseconds) }
            TransportSettings(trusted) { set(Int.MAX_VALUE.milliseconds) }
            assertTrue(true, name)
        }
    }

    @Test
    fun `a timeout outside 1 ms to Int MAX_VALUE ms is refused with a message naming the property`() {
        val bad = listOf(Duration.ZERO, (-1).seconds, 999.microseconds, Duration.INFINITE, (Int.MAX_VALUE + 1L).milliseconds)
        timeoutSetters.forEach { (name, set) ->
            bad.forEach { value ->
                val message = refused { set(value) }
                assertTrue(message.contains(name), "$name = $value: $message")
            }
        }
    }

    @Test
    fun `maxResponseBytes accepts 1 and refuses 0`() {
        assertEquals(1L, TransportSettings(trusted) { maxResponseBytes = 1 }.maxResponseBytes)
        assertTrue(refused { maxResponseBytes = 0 }.contains("maxResponseBytes"))
    }

    @Test
    fun `maxRedirects accepts 0 and refuses -1`() {
        assertEquals(0, TransportSettings(trusted) { maxRedirects = 0 }.maxRedirects)
        assertTrue(refused { maxRedirects = -1 }.contains("maxRedirects"))
    }

    @Test
    fun `maxConcurrentRequests accepts 1 and refuses 0`() {
        assertEquals(1, TransportSettings(trusted) { maxConcurrentRequests = 1; maxConcurrentRequestsPerHost = 1 }.maxConcurrentRequests)
        assertTrue(refused { maxConcurrentRequests = 0 }.contains("maxConcurrentRequests"))
    }

    @Test
    fun `maxConcurrentRequestsPerHost accepts 1 and maxConcurrentRequests and refuses 0 and one more`() {
        assertEquals(1, TransportSettings(trusted) { maxConcurrentRequestsPerHost = 1 }.maxConcurrentRequestsPerHost)
        assertEquals(16, TransportSettings(trusted) { maxConcurrentRequestsPerHost = 16 }.maxConcurrentRequestsPerHost)
        assertTrue(refused { maxConcurrentRequestsPerHost = 0 }.contains("maxConcurrentRequestsPerHost"))
        assertTrue(refused { maxConcurrentRequestsPerHost = 17 }.contains("maxConcurrentRequestsPerHost"))
    }

    @Test
    fun `no message holds a value`() {
        val message = refused { maxResponseBytes = -123456789L }
        assertFalse(message.contains("123456789"), message)
        assertFalse(refused { callTimeout = (-123456789).milliseconds }.contains("123456789"))
    }
    //endregion

    @Test
    fun `toString starts with the class name and prints all ten names`() {
        val text = trusted.toString()
        assertTrue(text.startsWith("TransportSettings("), text)
        listOf(
            "callTimeout", "connectTimeout", "readTimeout", "writeTimeout", "maxResponseBytes", "maxRedirects",
            "allowHttpsToHttp", "maxConcurrentRequests", "maxConcurrentRequestsPerHost", "idleConnectionTtl",
        ).forEach { assertTrue(text.contains("$it="), "$it missing from $text") }
    }
}
