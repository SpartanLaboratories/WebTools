package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

class MultiConnectionUDPServer {
    private val listening = true
    private var commonListenerThread: Thread? = null
    private val commonListenSocket = DatagramSocket(9998)
    private val commonSendPort = 9999
    private val commonSendSocket = DatagramSocket()
    init{
        log.info("Starting common listener thread on port {}", commonListenSocket.localPort)
        commonListenerThread = Thread {
            val buffer = ByteArray(1024)
            while (listening) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    commonListenSocket.receive(packet)
                    log.debug("The server has received a message on the common listen port")
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    log.trace("The message is {}", text)
                    val message = text.split(' ')
                    when(message[0]) {
                        "Iam" -> {
                            log.info("Normal Communication Detected: {}", message[0])
                            val address = InetAddress.getByName(message[2].removePrefix("/"))
                            log.debug("Address: {}", address)
                            val connection = addConnection(message[1], address)
                            val declarationResponse = "$address TXRXON ${connection.sendPort} ${connection.receivePort}"
                            pushToAddress(declarationResponse, address)
                        }
                    }
                } catch (e: SocketException) {
                    log.debug("Common listen socket was closed, stopping listener")
                    break // socket was closed - stop listening
                } catch (e: Exception) {
                    log.warn("Failed to handle incoming datagram: {}", e.message, e)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private val connections = arrayListOf<UDPConnection>()
    private fun addConnection(name:String, address: InetAddress):UDPConnection{
        val portOffset = connections.size + 2
        log.info("Adding a new connection '{}' for address {}", name, address)
        val connection = UDPConnection(name, address, commonSendPort-portOffset, commonSendPort-portOffset-1)
        connections.add(connection)
        return connection
    }
    fun start(onClientMessage:(String)->Unit) {
        log.info("Starting {} connection(s)", connections.size)
        connections.forEach{ connection -> connection.actuate(onClientMessage)}
    }

    private fun pushToAddress(message:String, address: InetAddress)  {
        log.trace("Pushing message to {}: {}", address, message)
        message.toByteArray(charset = Charsets.UTF_8).let { packet ->
            commonSendSocket.send(DatagramPacket(packet, packet.size, address, commonSendPort))
        }
    }
    fun pushToAll(message:String) {
        log.info("Pushing message to all {} connection(s)", connections.size)
        connections.forEach { connection -> pushToAddress(message, connection.address) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MultiConnectionUDPServer::class.java)

    }
}