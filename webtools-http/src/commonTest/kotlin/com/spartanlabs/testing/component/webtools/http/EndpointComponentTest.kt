package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.Endpoint
import com.spartanlabs.webtools.http.HttpResponse
import com.spartanlabs.webtools.http.Lane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// A named decoder, for the function-reference form of the factory.
private fun decodeString(response: HttpResponse): Result<String> = Result.success(response.body().decodeToString())

/**
 * Level 2 - `Endpoint`: every consumer form of the factory binds it and infers the value type; the defaults (the id as the name, a zero `ttl`,
 * the effective `missTtl`, cost 1, `Lane.Interactive`, no misses); `missTtl` reads live as the smaller of `ttl` and the default until a value is set,
 * and a set value wins; `misses` accumulates and the property is a read-only copy; equality is identity; `toString()` prints the settings and never
 * the decoder; the `internal` eight-argument constructor builds the same endpoint as the factory.
 */
class EndpointComponentTest {

    private val decode: (HttpResponse) -> Result<String> = { response -> Result.success(response.body().decodeToString()) }

    // Compiles only for an `Endpoint<String>`, so each call below proves the inferred value type.
    private fun typed(endpoint: Endpoint<String>): Endpoint<String> = endpoint

    @Test
    fun `the factory binds each consumer form and infers the value type`() {
        val plain = typed(Endpoint("plain", decode))
        val reference = typed(Endpoint("reference", ::decodeString) { name = "x" })
        val untyped = typed(Endpoint("untyped", { response -> Result.success(response.body().decodeToString()) }))
        val explicit = typed(Endpoint<String>("explicit", decode))
        val expected: Endpoint<String> = Endpoint("expected", { r -> Result.success(r.body().decodeToString()) })
        // A decoder of `Result<String?>` is not written here: the bound `T : Any` refuses it, so the form does not compile.

        // Each call had two or three arguments, so it bound the factory (the constructor takes eight): the builder's defaults are in place.
        assertEquals(listOf("plain", "reference", "untyped", "explicit", "expected"), listOf(plain, reference, untyped, explicit, expected).map { it.id })
        assertEquals(listOf("plain", "x", "untyped", "explicit", "expected"), listOf(plain, reference, untyped, explicit, expected).map { it.name })
        assertSame(decode, plain.decode)
        assertSame(decode, explicit.decode)
    }

    @Test
    fun `defaults are the id, zero ttl, the effective missTtl, cost 1, Interactive and no misses`() {
        val endpoint = Endpoint("hero", decode)
        assertEquals("hero", endpoint.id)
        assertEquals(endpoint.id, endpoint.name)
        assertEquals(Duration.ZERO, endpoint.ttl)
        assertEquals(Duration.ZERO, endpoint.missTtl)
        assertEquals(1, endpoint.cost)
        assertEquals(Lane.Interactive, endpoint.lane)
        assertTrue(endpoint.misses.isEmpty())
    }

    @Test
    fun `missTtl reads as the smaller of ttl and the default until one is set`() {
        val seen = ArrayList<Duration>()
        val endpoint = Endpoint("hero", decode) {
            ttl = 5.seconds
            seen.add(missTtl)
            ttl = 10.minutes
            seen.add(missTtl)
            ttl = Duration.INFINITE
            seen.add(missTtl)
        }
        assertEquals(listOf(5.seconds, 60.seconds, 60.seconds), seen)
        assertEquals(Duration.INFINITE, endpoint.ttl)
        assertEquals(60.seconds, endpoint.missTtl)
    }

    @Test
    fun `an explicit missTtl wins over the derived one`() {
        var seen: Duration? = null
        val longer = Endpoint("hero", decode) {
            missTtl = 2.minutes
            ttl = 5.seconds
            seen = missTtl
        }
        assertEquals(2.minutes, seen)
        assertEquals(2.minutes, longer.missTtl)
        assertEquals(5.seconds, longer.ttl)

        val zero = Endpoint("hero", decode) {
            ttl = 10.minutes
            missTtl = Duration.ZERO
        }
        assertEquals(Duration.ZERO, zero.missTtl)
        assertEquals(10.minutes, zero.ttl)
    }

    @Test
    fun `misses accumulates statuses and the property is a read-only copy`() {
        var builder: Endpoint.Builder? = null
        val endpoint = Endpoint("hero", decode) {
            misses(404)
            misses(410, 404)
            builder = this
        }
        assertEquals(listOf(404, 410), endpoint.misses.toList())

        // The property is not the builder's set: a status added to the builder after building changes nothing.
        builder!!.misses(500)
        assertEquals(setOf(404, 410), endpoint.misses)

        // Nor the set the constructor was handed.
        val handed = mutableSetOf(404)
        val direct = Endpoint("hero", decode, "hero", Duration.ZERO, Duration.ZERO, 1, Lane.Interactive, handed)
        handed.add(410)
        assertEquals(setOf(404), direct.misses)
    }

    @Test
    fun `equality is identity`() {
        val one = Endpoint("hero", decode) { ttl = 1.minutes; misses(404) }
        val other = Endpoint("hero", decode) { ttl = 1.minutes; misses(404) }
        assertNotSame(one, other)
        assertNotEquals(one, other)
        assertEquals(one, one)
        assertFalse(one.equals(null))
    }

    @Test
    fun `toString prints the settings and never the decoder`() {
        val text = Endpoint("hero", decode) { ttl = 10.minutes; misses(404) }.toString()
        assertTrue("id=hero" in text, text)
        assertTrue("ttl=" in text, text)
        assertTrue("lane=Interactive" in text, text)
        assertTrue("misses=[404]" in text, text)
        assertFalse(decode.toString() in text, text)
        assertFalse("Function" in text, text)
    }

    @Test
    fun `the internal constructor with eight arguments builds the same endpoint as the factory`() {
        val direct = Endpoint("id", decode, "n", 1.minutes, 2.minutes, 3, Lane.Tracker, setOf(404))
        assertEquals("id", direct.id)
        assertSame(decode, direct.decode)
        assertEquals("n", direct.name)
        assertEquals(1.minutes, direct.ttl)
        assertEquals(2.minutes, direct.missTtl)
        assertEquals(3, direct.cost)
        assertEquals(Lane.Tracker, direct.lane)
        assertEquals(setOf(404), direct.misses)

        val built = Endpoint("id", decode) {
            name = "n"
            ttl = 1.minutes
            missTtl = 2.minutes
            cost = 3
            lane = Lane.Tracker
            misses(404)
        }
        assertEquals(direct.id, built.id)
        assertSame(direct.decode, built.decode)
        assertEquals(direct.name, built.name)
        assertEquals(direct.ttl, built.ttl)
        assertEquals(direct.missTtl, built.missTtl)
        assertEquals(direct.cost, built.cost)
        assertEquals(direct.lane, built.lane)
        assertEquals(direct.misses, built.misses)
        assertEquals(direct.toString(), built.toString())
    }
}
