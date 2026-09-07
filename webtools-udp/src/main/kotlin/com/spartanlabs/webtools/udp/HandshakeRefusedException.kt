package com.spartanlabs.webtools.udp

/**
 * The server refused the handshake during [MultiConnectionUDPClient.handshake]:
 * it replied `REFUSED <reason>` rather than `REGISTERED`. Distinct from a
 * timeout (the server never answered) and from a malformed reply.
 * @property reason the server-supplied cause (everything after `REFUSED `), or
 * the empty string if the server sent a bare `REFUSED`.
 */
class HandshakeRefusedException(val reason: String) :
    Exception("Server refused the handshake" + if (reason.isBlank()) "" else ": $reason")
