package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.DeliveryMode
import com.spartanlabs.webtools.udp.UdpChannel
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

// Level 1 - a fast, socket-free smoke over UdpChannel's constants and its two stdlib defaults
// (send(String), actuate(String)). Both defaults' exact semantics matter: OD-4's ReplaceWith
// depends on actuate(String) applying exactly one UTF-8 decode + trim.
@Tag("gating")
class UdpChannelGatingTest {

    @Test
    fun `DEFAULT_MAX_RELIABLE_MESSAGE_BYTES is 1024`() {
        assertEquals(1024, UdpChannel.DEFAULT_MAX_RELIABLE_MESSAGE_BYTES)
    }

    @Test
    fun `MAX_RELIABLE_MESSAGE_BYTES is 8192`() {
        assertEquals(8192, UdpChannel.MAX_RELIABLE_MESSAGE_BYTES)
    }

    @Test
    fun `send String default delegates to send ByteArray with UTF-8 bytes`() {
        var captured: ByteArray? = null
        val channel = object : UdpChannel {
            override val mode = DeliveryMode.UNRELIABLE
            override fun send(bytes: ByteArray): Result<Unit> {
                captured = bytes
                return Result.success(Unit)
            }
            override fun actuateBytes(onMessage: (bytes: ByteArray) -> Unit): Result<Unit> = Result.success(Unit)
        }

        channel.send("héllo")

        assertContentEquals("héllo".toByteArray(Charsets.UTF_8), captured)
    }

    @Test
    fun `actuate String default delegates to actuateBytes and applies exactly one UTF-8 decode + trim`() {
        var boundBytesHandler: ((ByteArray) -> Unit)? = null
        val channel = object : UdpChannel {
            override val mode = DeliveryMode.UNRELIABLE
            override fun send(bytes: ByteArray): Result<Unit> = Result.success(Unit)
            override fun actuateBytes(onMessage: (bytes: ByteArray) -> Unit): Result<Unit> {
                boundBytesHandler = onMessage
                return Result.success(Unit)
            }
        }
        val received = mutableListOf<String>()

        channel.actuate { received += it }
        boundBytesHandler!!.invoke("  héllo-世界  ".toByteArray(Charsets.UTF_8))

        assertEquals(listOf("héllo-世界"), received)
    }
}
