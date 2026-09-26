package com.spartanlabs.webtools.udp

/**
 * The server's handshake reply announced a **different wire-protocol major** than
 * this build speaks. Raised by [MultiConnectionUDPClient.handshake] when the
 * `REGISTERED` reply is not this build's exact `REGISTERED 2`: a bare
 * `REGISTERED` from a pre-`2.0` server ([remoteVersion] `1`), or `REGISTERED n`
 * for some other `n`.
 *
 * Sibling of [HandshakeRefusedException]: a clean, typed, early `handshake()`
 * failure rather than a "registered" connection that then exchanges framed data
 * the peer delivers to its application as garbage. Both ends must be on
 * `webtools-udp` `2.0.0`+.
 *
 * @property remoteVersion the wire major the server's reply announced (`1` for a
 * bare `REGISTERED`), or `null` if the reply was not a `REGISTERED[ n]` line
 * @property localVersion the wire major this build speaks
 * ([TransportWireFormat.FRAMED_PROTOCOL_VERSION])
 */
class IncompatibleProtocolException(
    val remoteVersion: Int?,
    val localVersion: Int,
) : Exception(
    "Server speaks webtools-udp wire protocol " +
        "${remoteVersion?.toString() ?: "<unknown>"} but this build speaks $localVersion; " +
        "both ends must be on webtools-udp 2.0.0+",
)
