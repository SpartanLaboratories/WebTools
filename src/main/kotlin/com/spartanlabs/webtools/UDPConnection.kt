package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.InetAddress

internal class UDPConnection(val name:String, val address: InetAddress, val sendPort: Int, val receivePort: Int) {
    private val server = UDPSendReceiveServer(address, sendPort, receivePort)

    init {
        log.debug("Created UDPConnection '{}' for {} (send={}, receive={})", name, address, sendPort, receivePort)
    }

    fun actuate(onMessage: (message: String) -> Unit) {
        log.info("Actuating connection '{}'", name)
        server.startListening { message, senderAddress ->
            log.trace("Connection '{}' received message from {}: {}", name, senderAddress, message)
            onMessage(message)
        }
    }
    fun terminate() {
        log.info("Terminating connection '{}'", name)
        server.close()
    }
    fun push(message: String) {
        log.trace("Connection '{}' pushing message: {}", name, message)
        server.send(message)
    }

    companion object {
        private val log = LoggerFactory.getLogger(UDPConnection::class.java)
    }
}