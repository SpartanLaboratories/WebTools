package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.GuardRefusal
import com.spartanlabs.webtools.http.POLICY_REFUSED_HOST_MESSAGE
import com.spartanlabs.webtools.http.POLICY_REFUSED_IO_MESSAGE
import com.spartanlabs.webtools.http.PolicyRefusedIo
import com.spartanlabs.webtools.http.PolicyRefusedUnknownHost
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.policyRefusal
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Level 2 - the two markers: constant messages whatever the refusal, no stack, the right supertypes (never a
 * `ConnectException`), suppressed exceptions still attachable. `policyRefusal()`: the marker itself, a marker as the cause
 * at depth 2 and 3, in `suppressed` and in the cause's `suppressed`; `null` for a plain exception; a cause cycle terminates;
 * depth 50 is found and depth 150 is not; the cause is searched before `suppressed`, and `suppressed` in order; a canary in
 * the refusal's class never reaches a message.
 */
class PolicyRefusalMarkersComponentTest {

    private val a = GuardRefusal(RefusalReason.DeniedAddress, "private-use")
    private val b = GuardRefusal(RefusalReason.DeniedAddress, "loopback")

    @Test
    fun `the messages are constants and the stacks are empty`() {
        listOf(a, b).forEach { refusal ->
            assertEquals(POLICY_REFUSED_HOST_MESSAGE, PolicyRefusedUnknownHost(refusal).message)
            assertEquals(POLICY_REFUSED_IO_MESSAGE, PolicyRefusedIo(refusal).message)
            assertEquals(0, PolicyRefusedUnknownHost(refusal).stackTrace.size)
            assertEquals(0, PolicyRefusedIo(refusal).stackTrace.size)
        }
    }

    @Test
    fun `the markers have the right supertypes`() {
        val host: Throwable = PolicyRefusedUnknownHost(a)
        val io: Throwable = PolicyRefusedIo(a)
        assertTrue(host is UnknownHostException)
        assertTrue(io is IOException)
        assertFalse(io is ConnectException)
        io.addSuppressed(IOException("later"))
        assertEquals(1, io.suppressed.size)
    }

    @Test
    fun `the marker itself and a marker as a cause are found`() {
        assertSame(a, PolicyRefusedIo(a).policyRefusal())
        assertSame(a, IOException("top", PolicyRefusedUnknownHost(a)).policyRefusal())
        assertSame(a, IOException("top", IOException("middle", PolicyRefusedIo(a))).policyRefusal())
    }

    @Test
    fun `a marker in suppressed and in the cause's suppressed is found`() {
        val top = IOException("top").apply { addSuppressed(PolicyRefusedIo(a)) }
        assertSame(a, top.policyRefusal())
        val cause = IOException("cause").apply { addSuppressed(PolicyRefusedUnknownHost(b)) }
        assertSame(b, IOException("top", cause).policyRefusal())
    }

    @Test
    fun `a plain exception and a cause cycle give null`() {
        assertNull(IOException("plain").policyRefusal())
        val x = IOException("x")
        val y = IOException("y")
        x.initCause(y)
        y.initCause(x)
        assertNull(x.policyRefusal())
    }

    private fun chain(depth: Int, bottom: Throwable): Throwable = (1..depth).fold(bottom) { inner, _ -> IOException("wrap", inner) }

    @Test
    fun `depth 50 is found and depth 150 is beyond the bound`() {
        assertSame(a, chain(50, PolicyRefusedIo(a)).policyRefusal())
        assertNull(chain(150, PolicyRefusedIo(a)).policyRefusal())
    }

    @Test
    fun `the cause is searched before suppressed and suppressed in order`() {
        val top = IOException("top", PolicyRefusedIo(a)).apply { addSuppressed(PolicyRefusedIo(b)) }
        assertSame(a, top.policyRefusal())
        val two = IOException("top").apply { addSuppressed(PolicyRefusedIo(a)); addSuppressed(PolicyRefusedIo(b)) }
        assertSame(a, two.policyRefusal())
    }

    @Test
    fun `a canary in the refusal's class never reaches a message`() {
        val canary = GuardRefusal(RefusalReason.DeniedAddress, "SECRET-CANARY-7f3a")
        listOf<Throwable>(PolicyRefusedUnknownHost(canary), PolicyRefusedIo(canary)).forEach { marker ->
            assertFalse(marker.message!!.contains("SECRET-CANARY-7f3a"))
            assertFalse(marker.toString().contains("SECRET-CANARY-7f3a"))
            assertFalse(marker.localizedMessage.contains("SECRET-CANARY-7f3a"))
        }
    }
}
