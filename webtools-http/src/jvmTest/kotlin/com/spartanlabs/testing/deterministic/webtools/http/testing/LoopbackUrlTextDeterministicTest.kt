package com.spartanlabs.testing.deterministic.webtools.http.testing

import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.testing.loopbackUrlText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 4a - `loopbackUrlText`: IPv4 as is; an expanded or compressed IPv6 literal bracketed; a zone removed; the path (with a query)
 * appended verbatim; and `Url.parse` of the expanded form has the host `::1`, the form the loopback relaxation holds.
 */
class LoopbackUrlTextDeterministicTest {

    @Test
    fun `IPv4 is written as is`() {
        assertEquals("http://127.0.0.1:8080/a?b=1", loopbackUrlText("127.0.0.1", 8080, "/a?b=1"))
        assertEquals("http://127.0.0.2:80/", loopbackUrlText("127.0.0.2", 80, "/"))
    }

    @Test
    fun `IPv6 is bracketed and a zone is removed`() {
        assertEquals("http://[0:0:0:0:0:0:0:1]:80/x", loopbackUrlText("0:0:0:0:0:0:0:1", 80, "/x"))
        assertEquals("http://[::1]:80/", loopbackUrlText("::1", 80, "/"))
        assertEquals("http://[fe80::1]:80/", loopbackUrlText("fe80::1%eth0", 80, "/"))
        assertEquals("http://[fe80::1]:80/", loopbackUrlText("fe80::1%25", 80, "/"))
    }

    @Test
    fun `the parsed expanded form has the host the relaxation holds`() {
        val url = Url.parse(loopbackUrlText("0:0:0:0:0:0:0:1", 8080, "/p?q=1")).getOrThrow()
        assertEquals("::1", url.host)
        assertEquals(8080, url.port)
        assertEquals("http://[::1]:8080/p?q=1", url.toString())
    }
}
