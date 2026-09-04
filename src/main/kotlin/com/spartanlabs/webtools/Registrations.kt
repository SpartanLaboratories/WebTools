package com.spartanlabs.webtools

import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One client that has completed the `Iam` handshake.
 *
 * @property connection the connection minted for this client; its
 * [Connection.peer] is the origin every datagram to this client is addressed to
 * @property onMessage the currently bound message handler, or `null` if the
 * client has not been actuated; written by `actuate` / `terminate` and read by
 * the listener thread, hence `@Volatile`
 */
internal class Registration(val connection: Connection) {
    val origin: InetSocketAddress get() = connection.peer

    @Volatile
    var onMessage: ((String) -> Unit)? = null
}

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
