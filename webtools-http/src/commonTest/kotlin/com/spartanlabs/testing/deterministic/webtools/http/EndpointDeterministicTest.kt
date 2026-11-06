package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.MSG_ENDPOINT_COST
import com.spartanlabs.webtools.http.MSG_ENDPOINT_ID
import com.spartanlabs.webtools.http.MSG_ENDPOINT_MISSES
import com.spartanlabs.webtools.http.MSG_ENDPOINT_MISS_TTL
import com.spartanlabs.webtools.http.MSG_ENDPOINT_NAME
import com.spartanlabs.webtools.http.MSG_ENDPOINT_TTL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 4a - the `Endpoint` validation and default tables: each bad setting is refused with the constant message that names its property, in the
 * order id, name, ttl, missTtl, cost, misses; infinite durations are accepted; the miss-status boundaries (100 and 599 in; 99, 600 and 429 out);
 * the effective `missTtl` for each `ttl`; no message echoes a value; the name defaults to the id.
 */
class EndpointDeterministicTest {

    private val decode: (HttpResponse) -> Result<String> = { response -> Result.success(response.body().decodeToString()) }

    private fun refusal(id: String = "hero", configure: Endpoint.Builder.() -> Unit = {}): String =
        assertFailsWith<IllegalArgumentException> { Endpoint(id, decode, configure) }.message!!

    @Test
    fun `validation refuses each bad setting with a constant message naming the property`() {
        assertEquals("id must not be blank", MSG_ENDPOINT_ID)
        assertEquals(MSG_ENDPOINT_ID, refusal(id = ""))
        assertEquals(MSG_ENDPOINT_ID, refusal(id = " "))
        assertEquals(MSG_ENDPOINT_NAME, refusal { name = "" })
        assertEquals(MSG_ENDPOINT_TTL, refusal { ttl = (-1).seconds })
        assertEquals(MSG_ENDPOINT_MISS_TTL, refusal { missTtl = (-1).seconds })
        assertEquals(MSG_ENDPOINT_COST, refusal { cost = 0 })
        assertEquals(MSG_ENDPOINT_COST, refusal { cost = -1 })
        assertEquals(MSG_ENDPOINT_MISSES, refusal { misses(99) })

        // The order of the checks: with every setting bad, each fix uncovers the next property's message.
        val everyBad: Endpoint.Builder.() -> Unit = {
            name = ""
            ttl = (-1).seconds
            missTtl = (-1).seconds
            cost = 0
            misses(99)
        }
        assertEquals(MSG_ENDPOINT_ID, refusal(id = "", configure = everyBad))
        assertEquals(MSG_ENDPOINT_NAME, refusal(configure = everyBad))
        assertEquals(MSG_ENDPOINT_TTL, refusal { everyBad(); name = "n" })
        assertEquals(MSG_ENDPOINT_MISS_TTL, refusal { everyBad(); name = "n"; ttl = 1.seconds })
        assertEquals(MSG_ENDPOINT_COST, refusal { everyBad(); name = "n"; ttl = 1.seconds; missTtl = 1.seconds })
        assertEquals(MSG_ENDPOINT_MISSES, refusal { everyBad(); name = "n"; ttl = 1.seconds; missTtl = 1.seconds; cost = 1 })
    }

    @Test
    fun `an infinite ttl and an infinite missTtl are accepted`() {
        val endpoint = Endpoint("hero", decode) {
            ttl = Duration.INFINITE
            missTtl = Duration.INFINITE
        }
        assertEquals(Duration.INFINITE, endpoint.ttl)
        assertEquals(Duration.INFINITE, endpoint.missTtl)
    }

    @Test
    fun `misses accept 100 and 599 and refuse 99, 600 and 429`() {
        listOf(100, 599).forEach { status ->
            assertEquals(setOf(status), Endpoint("hero", decode) { misses(status) }.misses, "status $status")
        }
        assertEquals(setOf(100, 599), Endpoint("hero", decode) { misses(100, 599) }.misses)
        listOf(99, 600, 429).forEach { status ->
            assertEquals(MSG_ENDPOINT_MISSES, refusal { misses(status) }, "status $status")
            assertEquals(MSG_ENDPOINT_MISSES, refusal { misses(404, status) }, "status $status beside a good one")
        }
    }

    @Test
    fun `missTtl follows ttl`() {
        val table = listOf(
            Duration.ZERO to Duration.ZERO,
            1.seconds to 1.seconds,
            60.seconds to 60.seconds,
            61.seconds to 60.seconds,
            10.minutes to 60.seconds,
            Duration.INFINITE to 60.seconds,
        )
        table.forEach { (ttlValue, effective) ->
            assertEquals(effective, Endpoint("hero", decode) { ttl = ttlValue }.missTtl, "ttl $ttlValue")
        }
    }

    @Test
    fun `a message never echoes the value`() {
        // A blank id or name has nothing to echo; the message is the constant.
        assertEquals(MSG_ENDPOINT_ID, refusal(id = "   "))
        assertEquals(MSG_ENDPOINT_NAME, refusal(id = "SECRET") { name = "  " })

        val misses = refusal(id = "SECRET") { misses(1000) }
        assertFalse("1000" in misses, misses)

        val messages = listOf(
            refusal(id = "SECRET") { name = "  " },
            refusal(id = "SECRET") { name = "SECRET"; ttl = (-7).seconds },
            refusal(id = "SECRET") { name = "SECRET"; missTtl = (-7).seconds },
            refusal(id = "SECRET") { name = "SECRET"; cost = -7 },
            misses,
        )
        messages.forEach { message ->
            assertFalse("SECRET" in message, message)
            assertFalse("7" in message, message)
        }
    }

    @Test
    fun `name defaults to the id and a set name is kept`() {
        assertEquals("hero", Endpoint("hero", decode).name)
        assertEquals("Heroes", Endpoint("hero", decode) { name = "Heroes" }.name)
        assertEquals("hero", Endpoint("hero", decode) { name = "Heroes" }.id)
    }
}
