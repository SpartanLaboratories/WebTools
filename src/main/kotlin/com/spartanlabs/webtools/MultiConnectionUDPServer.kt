package com.spartanlabs.webtools

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
        commonListenerThread = Thread {
            val buffer = ByteArray(1024)
            while (listening) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    commonListenSocket.receive(packet)
                    println("The server has received a message on the common listen port")
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    println("The message is $text")
                    val message = text.split(' ')
                    when(message[0]) {
                        "Iam" -> {
                            println("Normal Communication Detected: ${message[0]}")
                            val address = InetAddress.getByName(message[2].removePrefix("/"))
                            println("Address: $address")
                            val connection = addConnection(message[1], address)
                            val declarationResponse = "$address TXRXON ${connection.sendPort} ${connection.receivePort}"
                            pushToAddress(declarationResponse, address)
                        }
                    }
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

    private val connections = arrayListOf<UDPConnection>()
    private fun addConnection(name:String, address: InetAddress):UDPConnection{
        val portOffset = connections.size + 2
        val connection = UDPConnection(name, address, commonSendPort-portOffset, commonSendPort-portOffset-1)
        connections.add(connection)
        return connection
    }
    fun start(onClientMessage:(String)->Unit) = connections.forEach{ connection -> connection.actuate(onClientMessage)}

    private fun pushToAddress(message:String, address: InetAddress)  = message.toByteArray(charset = Charsets.UTF_8).let { packet ->
        commonSendSocket.send(DatagramPacket(packet, packet.size, address, commonSendPort))
    }
    fun pushToAll(message:String) = connections.forEach { connection -> pushToAddress(message, connection.address) }
}
