package com.spartanlabs.webtools.udp

/**
 * Why a [Connection] managed by a [MultiConnectionUDPServer] stopped being
 * addressable, as reported to [MultiConnectionUDPServer.onClientDisconnect].
 *
 * A future **minor** release may add a new reason (e.g. for a graceful close);
 * keep an `else` branch in any `when` over [DisconnectReason].
 */
enum class DisconnectReason {
    /**
     * No datagram (data or a `0x80` keepalive) arrived from the peer within the configured
     * idle threshold. The registration is left in place - notify-only.
     */
    TIMEOUT,

    /**
     * A fresh `Iam` under this connection's name arrived from a different origin
     * (e.g. a NAT rebind) and superseded it; the registration has been removed.
     * A matching [MultiConnectionUDPServer.onClientConnect] fires for the
     * replacement.
     */
    SUPERSEDED,

    /**
     * The application called [Connection.terminate]; the registration has been
     * removed.
     */
    TERMINATED,
}
