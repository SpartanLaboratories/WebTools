package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.ReliableMessageTooLargeException
import com.spartanlabs.webtools.udp.ReliableSendFailure
import com.spartanlabs.webtools.udp.ReliableWindowFullException
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Level 1 - locks the OD-2 open-hierarchy guarantee: both reliable-send failures are
// ReliableSendFailure subtypes, usable as a Result.failure payload, and the supertype is
// deliberately NOT sealed - so a later "tidy-up" cannot quietly seal it without this failing.
@Tag("gating")
class ReliableChannelExceptionsGatingTest {

    @Test
    fun `ReliableWindowFullException carries inFlight and names it in the message`() {
        val ex = ReliableWindowFullException(inFlight = 256)

        assertEquals(256, ex.inFlight)
        assertTrue(ex.message!!.contains("256"))
    }

    @Test
    fun `ReliableMessageTooLargeException carries sizeBytes and capBytes and names both`() {
        val ex = ReliableMessageTooLargeException(sizeBytes = 2048, capBytes = 1024)

        assertEquals(2048, ex.sizeBytes)
        assertEquals(1024, ex.capBytes)
        assertTrue(ex.message!!.contains("2048"))
        assertTrue(ex.message!!.contains("1024"))
    }

    @Test
    fun `both exceptions are ReliableSendFailure subtypes usable as a Result failure payload`() {
        val windowFull: Result<Unit> = Result.failure(ReliableWindowFullException(1))
        val tooLarge: Result<Unit> = Result.failure(ReliableMessageTooLargeException(1, 1))

        assertIs<ReliableSendFailure>(windowFull.exceptionOrNull())
        assertIs<ReliableSendFailure>(tooLarge.exceptionOrNull())
    }

    private class SomeOtherReliableFailure(msg: String) : ReliableSendFailure(msg)

    @Test
    fun `ReliableSendFailure is not sealed - a third-party subtype compiles and survives a Result failure round trip`() {
        val ex = SomeOtherReliableFailure("some other reliable failure")

        assertIs<ReliableSendFailure>(ex)
        val result: Result<Unit> = Result.failure(ex)
        assertIs<SomeOtherReliableFailure>(result.exceptionOrNull())
    }
}
