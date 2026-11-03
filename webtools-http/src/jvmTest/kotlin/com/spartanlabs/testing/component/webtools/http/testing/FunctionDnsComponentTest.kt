package com.spartanlabs.testing.component.webtools.http.testing

import com.spartanlabs.testing.support.webtools.http.inet
import com.spartanlabs.webtools.http.testing.FunctionDns
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Level 2 - the function-backed resolver: `lookup` returns the function's list, an exception from the function propagates unchanged, and
 * the host passed to the function is the one asked.
 */
class FunctionDnsComponentTest {

    @Test
    fun `lookup returns the function's list for the host asked`() {
        val asked = mutableListOf<String>()
        val answer = listOf(inet("127.0.0.1"), inet("10.0.0.5"))
        val dns = FunctionDns { host -> asked += host; answer }
        assertEquals(answer, dns.lookup("a.test"))
        assertEquals(listOf("a.test"), asked)
    }

    @Test
    fun `an exception from the function propagates unchanged`() {
        val failure = UnknownHostException("nonexistent.test")
        val dns = FunctionDns { throw failure }
        assertSame(failure, assertFailsWith<UnknownHostException> { dns.lookup("nonexistent.test") })
    }
}
