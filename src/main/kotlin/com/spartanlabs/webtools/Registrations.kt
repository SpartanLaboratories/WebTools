package com.spartanlabs.webtools

import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One client that has completed the `Iam` handshake: its dedicated [connection]
 * plus the exact [origin] (address and source port) its handshake datagram
 * arrived from. Every common-channel datagram to this client is addressed to
 * [origin] so it rides the NAT binding the handshake opened.
 */
internal class Registration(val connection: Connection, val origin: InetSocketAddress)

/**
 * The set of completed handshakes, in registration order.
 *
 * Backed by a copy-on-write list because entries are appended from
 * [MultiConnectionUDPServer]'s listener thread while caller threads iterate it.
 * Holds no sockets, so it is unit-testable on its own.
 */
internal class Registrations {
    private val entries = CopyOnWriteArrayList<Registration>()

    /** How many clients are currently registered. */
    val size: Int get() = entries.size

    /** Appends [registration] as the newest entry. */
    fun add(registration: Registration) {
        entries.add(registration)
    }

    /**
     * The registration whose origin equals [origin] by value, or `null` if this
     * origin has not completed a handshake.
     */
    fun findByOrigin(origin: InetSocketAddress): Registration? =
        entries.firstOrNull { it.origin == origin }

    /** A stable snapshot of every registration, oldest first. */
    fun snapshot(): List<Registration> = entries.toList()
}
