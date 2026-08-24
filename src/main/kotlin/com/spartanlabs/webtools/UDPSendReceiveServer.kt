package com.spartanlabs.webtools

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

class UDPSendReceiveServer(
    private val targetAddress: InetAddress = resolveLocalAddress(),
    private val sendPort: Int,
    private val listenPort: Int
) : AutoCloseable {

    private val sendSocket = DatagramSocket()
    private val listenSocket = DatagramSocket(listenPort)

    @Volatile
    private var listening = false
    private var listenerThread: Thread? = null

    fun send(byteArray: ByteArray,  address: InetAddress) = sendSocket.send(DatagramPacket(byteArray, byteArray.size, address, sendPort))
    fun send(message  : String,     address: InetAddress) = send(message.toByteArray(), address)

    /**
     * Starts a background thread that blocks on [listenSocket]'s receive,
     * decoding each incoming datagram as a UTF-8 string and passing it to
     * [onMessage] along with the sender's address.
     */
    fun startListening(onMessage: (message: String, senderAddress: InetAddress) -> Unit) {
        listening = true
        listenerThread = Thread {
            val buffer = ByteArray(1024)
            while (listening) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    listenSocket.receive(packet)
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    onMessage(message, packet.address)
                } catch (e: SocketException) {
                    break // socket was closed - stop listening
                } catch (e: Exception) {
                    println("Failed to handle incoming datagram: ${e.message}")
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stopListening() {
        listening = false
    }
    override fun close() {
        stopListening()
        listenSocket.close()
        sendSocket.close()
        listenerThread?.join(1000)
    }

    companion object {
        fun resolveLocalAddress(): InetAddress =
            try {
                DatagramSocket().use { probe ->
                    probe.connect(InetAddress.getByName("8.8.8.8"), 80)
                    probe.localAddress
                }
            } catch (e: Exception) {
                InetAddress.getLoopbackAddress()
            }
    }
}