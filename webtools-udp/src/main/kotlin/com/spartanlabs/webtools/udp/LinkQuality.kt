package com.spartanlabs.webtools.udp

/**
 * A point-in-time snapshot of one connection's measured link quality, produced by
 * the opt-in probe ([MultiConnectionUDPClient.startProbe] / [Connection.startProbe]).
 * All figures come from the same instant, so RTT and loss are mutually consistent.
 *
 * @property rttMillis smoothed round-trip time (EWMA, RFC 6298 SRTT), milliseconds
 * @property rttVarianceMillis smoothed RTT variation (RFC 6298 RTTVAR) - a jitter
 * proxy; size an interpolation delay from `rttMillis + k * rttVarianceMillis`
 * @property packetLossRatio fraction of recently-resolved probes that went
 * unanswered within the loss horizon, `0.0..1.0`; lagging and windowed - not a
 * fast congestion trigger
 * @property probesSent total probes this side has sent since [MultiConnectionUDPClient.startProbe] / [Connection.startProbe]
 * @property probesDelivered total probes answered with a matching `PONG`
 */
data class LinkQuality(
    val rttMillis: Double,
    val rttVarianceMillis: Double,
    val packetLossRatio: Double,
    val probesSent: Long,
    val probesDelivered: Long,
)
