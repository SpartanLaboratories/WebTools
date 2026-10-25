package com.spartanlabs.testing.nonfunctional.webtools.http.testing

import com.spartanlabs.webtools.http.HttpHeaders
import com.spartanlabs.webtools.http.testing.Fixture
import com.spartanlabs.webtools.http.testing.FixtureError
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Level 4c - robustness of the `.http` codec: 50,000 seeded random byte arrays (seed 11; pure random bytes,
 * valid files with one byte mutated, and truncations of valid files) go through `Fixture.parse`, which
 * returns a fixture or throws only `FixtureError`; every success re-serialises and re-parses equal. Large heads
 * and bodies (a 1 MiB run of header lines with no blank line, a 1 MiB header value, a 4 MiB body) parse in time
 * linear in their size: the 16x input takes less than 100x the 1x time, with a 1 ms floor on the 1x time (a
 * quadratic parse takes about 256x).
 */
class HttpMessageCodecGarbageNonFunctionalTest {

    private val seed = 11
    private val inputs = 50_000

    private fun validFile(random: Random): ByteArray {
        val headers = HttpHeaders {
            repeat(random.nextInt(0, 4)) { add("X-H$it", "v$it " + "x".repeat(random.nextInt(0, 5))) }
            if (random.nextBoolean()) add("Content-Type", "application/json")
        }
        val body = ByteArray(random.nextInt(0, 40)) { random.nextInt(256).toByte() }
        val comments = if (random.nextBoolean()) listOf("request: GET https://h/x") else emptyList()
        return Fixture(random.nextInt(100, 600), headers, body).toBytes(comments)
    }

    private fun garbage(random: Random): ByteArray = when (random.nextInt(10)) {
        in 0..3 -> ByteArray(random.nextInt(0, 80)) { random.nextInt(256).toByte() }
        in 4..6 -> validFile(random).also { if (it.isNotEmpty()) it[random.nextInt(it.size)] = random.nextInt(256).toByte() }
        else -> validFile(random).let { it.copyOf(random.nextInt(0, it.size + 1)) }
    }

    @Test
    fun `parse never throws anything but FixtureError and every success round-trips`() {
        val random = Random(seed)
        var successes = 0
        var refusals = 0
        repeat(inputs) { index ->
            val bytes = garbage(random)
            val parsed: Fixture? = try {
                Fixture.parse(bytes, "garbage $index")
            } catch (e: FixtureError) {
                refusals++
                assertTrue(e.message!!.startsWith("garbage $index"), e.message)
                null
            } catch (t: Throwable) {
                fail("input $index threw ${t::class.qualifiedName}: ${t.message}")
            }
            if (parsed != null) {
                successes++
                val again = Fixture.parse(parsed.toBytes(), "again $index")
                assertEquals(parsed, again, "input $index does not round-trip")
                assertContentEquals(parsed.body(), again.body())
            }
        }
        assertTrue(successes > inputs / 20, "too few successes: $successes")
        assertTrue(refusals > inputs / 20, "too few refusals: $refusals")
    }

    private val kib = 1024
    private val headerLine = "X-H: v\n"

    private fun headLines(count: Int): ByteArray = buildString { append("HTTP/1.1 200 OK\n"); repeat(count) { append(headerLine) } }.encodeToByteArray()
    private fun bigValue(length: Int): ByteArray = ("HTTP/1.1 200 OK\nX-Big: " + "a".repeat(length) + "\n\n").encodeToByteArray()
    private fun bigBody(length: Int): ByteArray = "HTTP/1.1 200 OK\n\n".encodeToByteArray() + ByteArray(length) { 0x61 }

    @Test
    fun `large heads and bodies parse in time linear in their size`() {
        val lines1 = 64 * kib / headerLine.length
        assertLinear("header lines", headLines(lines1), headLines(lines1 * 16)) { small, large ->
            assertEquals(lines1, small.headers.size); assertEquals(lines1 * 16, large.headers.size)
            assertEquals(0, small.bodySize); assertEquals(0, large.bodySize)
        }
        assertLinear("header value", bigValue(64 * kib), bigValue(1024 * kib)) { small, large ->
            assertEquals(64 * kib, small.headers["X-Big"]!!.length); assertEquals(1024 * kib, large.headers["X-Big"]!!.length)
        }
        assertLinear("body", bigBody(256 * kib), bigBody(4096 * kib)) { small, large ->
            assertEquals(256 * kib, small.bodySize); assertEquals(4096 * kib, large.bodySize)
        }
    }

    // Each input is parsed once to warm up and checked for its shape, then the 1x and 16x parses are timed; a
    // failing measurement is rerun once before it counts (plan section 10, flaky timing).
    private fun assertLinear(label: String, small: ByteArray, large: ByteArray, shape: (Fixture, Fixture) -> Unit) {
        shape(Fixture.parse(small, "$label 1x"), Fixture.parse(large, "$label 16x"))
        fun measure(): Pair<Long, Long> {
            val t1 = System.nanoTime().let { Fixture.parse(small, "$label 1x"); System.nanoTime() - it }
            val t16 = System.nanoTime().let { Fixture.parse(large, "$label 16x"); System.nanoTime() - it }
            return t1 to t16
        }
        // A 1 ms floor on the 1x time, so a sub-millisecond small parse does not make the ratio noise.
        fun linear(times: Pair<Long, Long>): Boolean = times.second < 100 * maxOf(times.first, 1_000_000L)
        var times = measure()
        if (!linear(times)) times = measure()
        assertTrue(linear(times), "$label: the 1x parse took ${times.first} ns, the 16x parse ${times.second} ns")
    }
}
