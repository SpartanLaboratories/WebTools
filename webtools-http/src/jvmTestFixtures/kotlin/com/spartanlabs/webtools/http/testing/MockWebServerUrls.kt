package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.Url
import mockwebserver3.MockWebServer

/**
 * An `http` URL on the address this server is bound to, written as an IP literal, so the loopback relaxation admits it on every operating
 * system and the URL never reaches the resolver. Never `hostName` or `url()`: both reverse-resolve an address created without a name. An HTTPS
 * server's URL is built by hand. A server bound to another `127.0.0.0/8` address gets the address exemption but not the port exemption.
 *
 * **Stable Core, source compatibility only.**
 *
 * @param path the path, with any query, starting with `/`; `/` by default.
 * @return the URL.
 * @throws IllegalArgumentException if [path] does not start with `/`.
 * @throws IllegalStateException if the server is not started, or is not bound to a loopback address.
 */
public fun MockWebServer.webtoolsUrl(path: String = "/"): Url {
    require(path.startsWith('/')) { "path must start with '/'" }
    val address = socketAddress.address            // throws IllegalStateException("call start() first") before start()
    check(address.isLoopbackAddress) { "webtoolsUrl needs a server bound to a loopback address" }
    return Url.parse(loopbackUrlText(address.hostAddress, port, path)).getOrThrow()
}

/**
 * The URL text of a loopback server: IPv6 bracketed, a zone (`%...`) removed. Pure, so a Level 4a test covers it without a socket.
 *
 * @param hostAddress the bound address as `InetAddress.hostAddress` prints it.
 * @param port the bound port.
 * @param path the path, starting with `/`.
 * @return `http://<host>:<port><path>`.
 */
internal fun loopbackUrlText(hostAddress: String, port: Int, path: String): String {
    val host = hostAddress.substringBefore('%')
    return "http://${if (':' in host) "[$host]" else host}:$port$path"
}
