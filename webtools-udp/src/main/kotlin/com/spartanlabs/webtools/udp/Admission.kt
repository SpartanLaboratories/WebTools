package com.spartanlabs.webtools.udp

/**
 * The outcome of [MultiConnectionUDPServer.admit] - the pre-accept screening
 * decision for an incoming `Iam` handshake, evaluated on the listener thread
 * before the `REGISTERED` reply and before any same-name supersede.
 */
sealed interface Admission {
    /** Accept the handshake: register the connection and reply `REGISTERED`. */
    data object Admitted : Admission

    /**
     * Refuse the handshake: reply `REFUSED <reason>` to the client, register
     * nothing, and do not fire [MultiConnectionUDPServer.onClientConnect].
     * @property reason a short, human-readable cause placed on the wire after
     * `REFUSED ` verbatim (trimmed; embedded control characters collapsed to a
     * space). Keep it terse - it is echoed to an unauthenticated peer.
     */
    data class Refused(val reason: String) : Admission
}
