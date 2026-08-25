package com.spartanlabs.webtools

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

class UDPSendReceiveServer(
    private val targetAddress: InetAddress,
    private val sendPort: Int,
    private val listenPort: Int
) : AutoCloseable {

    private val sendSocket = DatagramSocket()
    private val listenSocket = DatagramSocket(listenPort)

    @Volatile
    private var listening = false
    private var listenerThread: Thread? = null

    fun send(byteArray: ByteArray) = sendSocket.send(DatagramPacket(byteArray, byteArray.size, targetAddress, sendPort))
    fun send(message  : String) = send(message.toByteArray())

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
                    // Create the datagram packet that is going to contain the received message
                    val packet = DatagramPacket(buffer, buffer.size)
                    // Read the message and store it in the datagram packet we just created
                    listenSocket.receive(packet)
                    // Convert the packet into a String
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    // Take action based on the received message
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
        listenSocket.close()
    }
    override fun close() {
        stopListening()
        sendSocket.close()
        listenerThread?.join(1000)
    }

}