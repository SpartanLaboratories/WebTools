package com.spartanlabs.testing.gating.webtools.http

import com.spartanlabs.webtools.http.HttpFailure
import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.HttpTransport
import com.spartanlabs.webtools.http.OkHttpTransport
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Level 1 - the supplied transport as a consumer sees it, compiled **without any opt-in**: `OkHttpTransport("agent", policy)` is an
 * `HttpTransport`; a request to a denied address is `RefusedByPolicy` within a short timeout (no socket); `close()` twice; `execute` after
 * `close()` is `Closed("transport")`; `toString()` names the policy kind.
 */
class OkHttpTransportConsumerGatingTest {

    private val denied = HttpRequest(HttpMethod.Get, Url.parse("http://10.0.0.5/").getOrThrow())

    @Test
    fun `the supplied transport is usable as an HttpTransport without an opt-in`() = runBlocking {
        val transport: HttpTransport = OkHttpTransport("agent", OutboundUrlPolicy.untrusted())
        try {
            val refused = assertIs<TransportFailure.RefusedByPolicy>(withTimeout(5.seconds) { transport.execute(denied) }.exceptionOrNull())
            assertEquals(RefusalReason.DeniedAddress, refused.reason)
            assertTrue("Untrusted" in transport.toString(), transport.toString())
        } finally {
            transport.close()
            transport.close()
        }
        val closed = assertIs<HttpFailure.Closed>(withTimeout(5.seconds) { transport.execute(denied) }.exceptionOrNull())
        assertEquals("transport", closed.component)
    }
}
