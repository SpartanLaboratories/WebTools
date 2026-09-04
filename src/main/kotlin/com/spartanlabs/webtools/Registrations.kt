package com.spartanlabs.webtools

import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One client that has completed the `Iam` handshake.
 *
 * @property connection the dedicated connection minted for this client
 * @property origin the exact address and source port the client's handshake
 * datagram arrived from; every common-channel datagram to this client is
 * addressed here so it rides the NAT binding the handshake opened
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

    /**
     * Appends [registration] as the newest entry.
     * @param registration the completed handshake to record
     */
    fun add(registration: Registration) {
        entries.add(registration)
    }

    /**
     * Looks a client up by its handshake origin.
     * @param origin the address and source port to match, compared by value
     * @return the registration for that origin, or `null` if it has not completed a handshake
     */
    fun findByOrigin(origin: InetSocketAddress): Registration? =
        entries.firstOrNull { it.origin == origin }

    /**
     * A stable snapshot of every registration, oldest first.
     * @return an immutable copy that is unaffected by later [add] calls
     */
    fun snapshot(): List<Registration> = entries.toList()
}
