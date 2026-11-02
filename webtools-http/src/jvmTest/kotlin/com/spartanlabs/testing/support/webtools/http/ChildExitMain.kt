package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.HttpRequest
import com.spartanlabs.webtools.http.testing.LoopbackTransports
import com.spartanlabs.webtools.http.testing.webtoolsUrl
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.net.InetAddress
import kotlin.system.exitProcess

/**
 * The main class of the child JVM of `JvmExitIntegrationTest`: starts a `MockWebServer`, builds `LoopbackTransports.untrusted("child")`,
 * makes one call under `runBlocking`, closes the server, closes the transport only when `args[0] == "close"`, and exits with `2` only when
 * the call failed (never on success: an explicit exit would mask a lingering thread). With the transport left open the JVM must still exit
 * on its own, because the dispatcher's threads are daemon threads.
 */
internal object ChildExitMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val server = MockWebServer()
        server.start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0)
        server.enqueue(MockResponse(body = "ok"))
        val transport = LoopbackTransports.untrusted("child")
        val result = runBlocking { transport.execute(HttpRequest(HttpMethod.Get, server.webtoolsUrl("/"))) }
        server.close()
        if (args.firstOrNull() == "close") transport.close()
        if (result.isFailure) exitProcess(2)
    }
}
