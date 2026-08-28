package com.spartanlabs.webtools

import org.slf4j.LoggerFactory
import java.net.DatagramSocket
import java.net.InetAddress

/** Shared slf4j logger for this file's top-level functions. */
private val log = LoggerFactory.getLogger("com.spartanlabs.webtools.General")

/** Host the address probe is pointed at; never actually contacted, since UDP connect sends nothing. */
private const val PROBE_HOST = "8.8.8.8"

/** Port the address probe is pointed at. */
private const val PROBE_PORT = 80

/**
 * Determines this machine's outward-facing local address.
 *
 * Works by `connect`ing an unbound [DatagramSocket] to a public host and reading
 * back the local address the OS routing table selected. Because UDP `connect`
 * sends no packets, [PROBE_HOST] is never actually contacted - but the call still
 * requires a routable network interface, so it fails on an isolated machine.
 *
 * Callers that would rather degrade than fail can recover explicitly:
 * ```
 * val address = resolveLocalAddress().getOrDefault(InetAddress.getLoopbackAddress())
 * ```
 *
 * @return the resolved local address, or [Result.failure] holding the
 * [java.net.SocketException] or [java.net.UnknownHostException] that prevented it
 */
fun resolveLocalAddress(): Result<InetAddress> =
    runCatching {
        DatagramSocket().use { probe ->
            probe.connect(InetAddress.getByName(PROBE_HOST), PROBE_PORT)
            probe.localAddress
        }
    }
        .onSuccess { address -> log.debug("Resolved the local address as {}", address) }
        .onFailure { cause -> log.warn("Could not resolve the local address: {}", cause.message, cause) }
