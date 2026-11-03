package com.spartanlabs.testing.support.webtools.http

import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/**
 * The TLS material of the Level 3 HTTPS tests, built with `okhttp-tls`: a self-signed certificate for `127.0.0.1`, the server's and the
 * client's handshake certificates, a donor `OkHttpClient` that trusts the certificate, and an HTTPS server pinned to HTTP/1.1.
 */
internal object TlsFixtures {
    /** A self-signed certificate whose subject alternative name is the loopback address. */
    val certificate: HeldCertificate = HeldCertificate.Builder().commonName("127.0.0.1").addSubjectAlternativeName("127.0.0.1").build()

    /** The server side: presents [certificate]. */
    val server: HandshakeCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()

    /** The client side: trusts [certificate] and nothing else. */
    val client: HandshakeCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()

    /** A client carrying the client trust, to donate its TLS configuration to a transport. */
    fun donorClient(): OkHttpClient = OkHttpClient.Builder().sslSocketFactory(client.sslSocketFactory(), client.trustManager).build()

    /** An HTTPS server on the fleet's loopback address, pinned to HTTP/1.1. */
    fun httpsServer(fleet: LoopbackFleet): MockWebServer = fleet.server {
        useHttps(server.sslSocketFactory())
        protocols = listOf(Protocol.HTTP_1_1)
    }
}
