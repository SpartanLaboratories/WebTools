package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.CallBudget
import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.MSG_BASE_URL_QUERY
import com.spartanlabs.webtools.http.MSG_BASE_URL_REFUSED
import com.spartanlabs.webtools.http.MSG_DEFAULT_HEADER
import com.spartanlabs.webtools.http.MSG_DEFAULT_HEADER_SENSITIVE
import com.spartanlabs.webtools.http.MSG_DEFAULT_MAX_WAIT
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RateSignalsReader
import com.spartanlabs.webtools.http.RateWindow
import com.spartanlabs.webtools.http.ResponseCache
import com.spartanlabs.webtools.http.UpstreamClientConfig
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.WallClock
import com.spartanlabs.webtools.http.WindowedCallBudget
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Level 2 - `UpstreamClientConfig`, driven through its `internal` `resolve()` so no transport is needed: the defaults (a `Trusted` policy pinned to the
 * base URL's host, the module's clocks, a five-second wait, no headers, no cache); each construction refusal with its constant message (a negative
 * `defaultMaxWait`, a base URL with a query, a base URL the policy refuses, a default header the transport controls, a sensitive default header under
 * `Untrusted`); the last `budget(...)` call wins and only a budget built from windows is owned; `cache(...)` is recorded; no message echoes the URL
 * or a header value.
 */
class UpstreamClientConfigComponentTest {

    private val baseUrl: Url = url("https://api.example.test/v1/")
    private val windows: List<RateWindow> = listOf(RateWindow("minute", 60, 1.minutes))

    private fun url(text: String): Url = Url.parse(text).getOrThrow()

    private fun config(base: Url = baseUrl, configure: UpstreamClientConfig.() -> Unit = {}): UpstreamClientConfig =
        UpstreamClientConfig(base).apply(configure)

    private fun refusal(base: Url = baseUrl, configure: UpstreamClientConfig.() -> Unit = {}): String =
        assertFailsWith<IllegalArgumentException> { config(base, configure).resolve() }.message!!

    @Test
    fun `the defaults are a pinned Trusted policy, the module's clocks and a five second wait`() {
        val resolved = config().resolve()
        val policy = assertIs<OutboundUrlPolicy.Trusted>(resolved.policy)
        assertEquals(setOf("api.example.test"), policy.pinnedHosts)
        assertFalse(policy.allowCleartext)
        assertSame(TimeSource.Monotonic, resolved.timeSource)
        assertSame(WallClock.System, resolved.wallClock)
        assertSame(Dispatchers.Default, resolved.dispatcher)
        assertSame(RateSignalsReader.Standard, resolved.rateSignals)
        assertEquals(5.seconds, resolved.defaultMaxWait)
        assertTrue(resolved.headers.isEmpty())
        assertNull(resolved.cache)
        assertSame(baseUrl, resolved.baseUrl)
    }

    @Test
    fun `DEFAULT_MAX_WAIT is five seconds`() {
        assertEquals(5.seconds, UpstreamClientConfig.DEFAULT_MAX_WAIT)
        assertEquals(UpstreamClientConfig.DEFAULT_MAX_WAIT, config().defaultMaxWait)
    }

    @Test
    fun `a negative defaultMaxWait is refused`() {
        assertEquals("defaultMaxWait must not be negative", MSG_DEFAULT_MAX_WAIT)        // the text, written out: a reworded constant fails here
        assertEquals(MSG_DEFAULT_MAX_WAIT, refusal { defaultMaxWait = (-1).seconds })
        assertEquals(Duration.ZERO, config { defaultMaxWait = Duration.ZERO }.resolve().defaultMaxWait)
        assertEquals(Duration.INFINITE, config { defaultMaxWait = Duration.INFINITE }.resolve().defaultMaxWait)
    }

    @Test
    fun `a base URL with a query is refused, a bare question mark included`() {
        assertEquals("baseUrl must not have a query", MSG_BASE_URL_QUERY)
        assertEquals(MSG_BASE_URL_QUERY, refusal(url("https://api.example.test/v1/?a=1")))
        assertEquals(MSG_BASE_URL_QUERY, refusal(url("https://api.example.test/v1/?")))
    }

    @Test
    fun `a base URL the policy refuses is refused with the reason name`() {
        assertEquals("baseUrl is refused by the outbound-URL policy: ", MSG_BASE_URL_REFUSED)
        assertEquals(MSG_BASE_URL_REFUSED + "Scheme", refusal(url("http://api.example.test/")))
        assertEquals(
            MSG_BASE_URL_REFUSED + "HostNotPinned",
            refusal { policy = OutboundUrlPolicy.trusted { pinnedHosts = setOf("other.example.test") } },
        )
        // A policy that allows the base URL resolves.
        assertIs<OutboundUrlPolicy.Trusted>(config(url("http://api.example.test/")) { policy = OutboundUrlPolicy.trusted { allowCleartext = true } }.resolve().policy)
    }

    @Test
    fun `a default header the transport controls is refused`() {
        assertEquals("a default header is invalid: ", MSG_DEFAULT_HEADER)
        val userAgent = MSG_DEFAULT_HEADER + "header User-Agent is controlled by the transport"
        assertEquals(userAgent, refusal { headers = HttpHeaders { add("User-Agent", "mine") } })
        assertEquals(userAgent, refusal { headers = HttpHeaders { addSensitive("User-Agent", "mine") } })
        assertEquals(userAgent, refusal { headers = HttpHeaders { add("Accept", "a"); add("user-agent", "mine") } })
        assertEquals(MSG_DEFAULT_HEADER + "header Host is controlled by the transport", refusal { headers = HttpHeaders { add("host", "h") } })
        assertEquals(MSG_DEFAULT_HEADER + "header Host is controlled by the transport", refusal { headers = HttpHeaders { add("HOST", "h") } })
    }

    @Test
    fun `a sensitive default header is refused under Untrusted`() {
        assertEquals("an Untrusted policy refuses a sensitive default header", MSG_DEFAULT_HEADER_SENSITIVE)
        assertEquals(
            MSG_DEFAULT_HEADER_SENSITIVE,
            refusal { policy = OutboundUrlPolicy.untrusted(); headers = HttpHeaders { addSensitive("X-Key", "v") } },
        )
        // A credential name is sensitive without the flag.
        assertEquals(
            MSG_DEFAULT_HEADER_SENSITIVE,
            refusal { policy = OutboundUrlPolicy.untrusted(); headers = HttpHeaders { add("Authorization", "v") } },
        )
        val plain = config { policy = OutboundUrlPolicy.untrusted(); headers = HttpHeaders { add("Accept", "a") } }.resolve()
        assertEquals("a", plain.headers["Accept"])
    }

    @Test
    fun `a sensitive default header is accepted under Trusted`() {
        val resolved = config { headers = HttpHeaders { addSensitive("Authorization", "Bearer t") } }.resolve()
        assertTrue(resolved.headers.entries().single().isSensitive)
        assertEquals("Bearer t", resolved.headers["Authorization"])
    }

    @Test
    fun `the last of budget and budget(windows) wins`() {
        val supplied = WindowedCallBudget(windows)
        try {
            val built = config { budget(supplied); budget(windows) }.resolve().buildBudget()
            val owned = assertIs<WindowedCallBudget>(built.budget)
            assertTrue(built.owned)
            assertNotSame(supplied, owned)
            assertEquals(60, owned.maxBurst)                   // built from the windows: the window's limit
            owned.close()

            val handed = config { budget(windows); budget(supplied) }.resolve().buildBudget()
            assertSame(supplied, handed.budget)
            assertFalse(handed.owned)

            val replaced = config { budget(supplied); budget(CallBudget.Unlimited) }.resolve().buildBudget()
            assertSame(CallBudget.Unlimited, replaced.budget)
            assertFalse(replaced.owned)

            val none = config().resolve().buildBudget()
            assertSame(CallBudget.Unlimited, none.budget)
            assertFalse(none.owned)
        } finally {
            supplied.close()
        }
    }

    @Test
    fun `cache is recorded and none means null`() {
        assertSame(ResponseCache.Disabled, config { cache(ResponseCache.Disabled) }.resolve().cache)
        assertNull(config().resolve().cache)
    }

    @Test
    fun `no refusal message echoes the URL or a header value`() {
        val messages = listOf(
            refusal(url("https://api.example.test/v1/?token=SECRET")),
            refusal(url("https://api.example.test/SECRET/?token=SECRET")) { defaultMaxWait = (-1).seconds },
            refusal(url("http://api.example.test/SECRET")),
            refusal(url("https://api.example.test/SECRET")) { policy = OutboundUrlPolicy.trusted { pinnedHosts = setOf("other.example.test") } },
            refusal { headers = HttpHeaders { add("User-Agent", "SECRET") } },
            refusal { policy = OutboundUrlPolicy.untrusted(); headers = HttpHeaders { addSensitive("X-Key", "SECRET") } },
        )
        assertEquals(6, messages.size)
        messages.forEach { message ->
            assertFalse("SECRET" in message, message)
            assertFalse("api.example.test" in message, message)
        }
    }
}
