package com.spartanlabs.webtools

import java.net.InetAddress

internal class UDPConnection(val name:String, val address: InetAddress, val sendPort: Int, val receivePort: Int) {
    private val server = UDPSendReceiveServer(address, sendPort, receivePort)
    fun actuate(onMessage: (message: String) -> Unit) {
        server.startListening { message, senderAddress ->
            onMessage(message)
        }
    }
    fun terminate() = server.close()
    fun push(message: String) = server.send(message)
}