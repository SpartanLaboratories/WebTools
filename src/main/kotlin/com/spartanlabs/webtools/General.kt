package com.spartanlabs.webtools

import java.net.DatagramSocket
import java.net.InetAddress

fun resolveLocalAddress(): InetAddress =
    try {
        DatagramSocket().use { probe ->
            probe.connect(InetAddress.getByName("8.8.8.8"), 80)
            probe.localAddress
        }
    } catch (e: Exception) {
        InetAddress.getLoopbackAddress()
    }