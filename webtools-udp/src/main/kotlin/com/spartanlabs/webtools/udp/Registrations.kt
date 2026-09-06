package com.spartanlabs.webtools.udp

import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One client that has completed the `Iam` handshake.
 *
 * @property connection the connection minted for this client; its
 * [Connection.peer] is the origin every datagram to this client is addressed to
 * @property onMessage the currently bound text message handler, or `null` if the
 * client has not been actuated with a text handler; written by `actuate`, cleared
 * by removal from `Registrations` on `terminate`, and read by the listener thread,
 * hence `@Volatile`
 * @property onBytes the currently bound raw-bytes message handler, or `null`.
 * [onMessage] and [onBytes] are mutually exclusive - [HandshakeCoordinator.bind] /
 * [HandshakeCoordinator.bindBytes] set one and null the other; the listener thread
 * reads whichever is non-null, hence `@Volatile`
 */
internal class Registration(val connection: Connection) {
    val origin: InetSocketAddress get() = connection.peer

    @Volatile
    var onMessage: ((String) -> Unit)? = null

    @Volatile
    var onBytes: ((ByteArray) -> Unit)? = null
}

/**
 * The set of completed handshakes, in registration order.
 *
 * Backed by a copy-on-write list because entries are appended from
 * [MultiConnectionUDPServer]'s listener thread while caller threads iterate it.
 * Holds no sockets, so it is unit-testable on its own. Entries are pruned via
 * [removeByOrigin] when a connection terminates or is superseded by a
 * same-name reconnect from a new origin - see [HandshakeCoordinator].
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
     * Looks a client up by its registered name.
     * @param name the name to match, compared by value
     * @return the registration currently held under that name, or `null`
     */
    fun findByName(name: String): Registration? =
        entries.firstOrNull { it.connection.name == name }

    /**
     * Removes the registration for [origin], if one exists.
     * @param origin the handshake origin to remove
     * @return `true` if an entry was removed, `false` if none matched (already gone, or never existed)
     */
    fun removeByOrigin(origin: InetSocketAddress): Boolean {
        val match = entries.firstOrNull { it.origin == origin } ?: return false
        return entries.remove(match)
    }

    /**
     * A stable snapshot of every registration, oldest first.
     * @return an immutable copy that is unaffected by later [add] calls
     */
    fun snapshot(): List<Registration> = entries.toList()
}
