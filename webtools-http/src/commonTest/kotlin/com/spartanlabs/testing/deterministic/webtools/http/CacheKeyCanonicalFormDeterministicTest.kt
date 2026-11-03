package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.CacheKey
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Level 4a - the canonical form of a `CacheKey`: reordered names are equal and `params` is sorted by name; repeated names keep
 * the caller's order (a stable sort, checked against a hand-written stable reference over 200 seeded random lists); a name
 * boundary is never confused (`[a=1, b=2]` is not `[ab=12]`, `[a=bc]` is not `[ab=c]`); the id, the names and the values are
 * case-sensitive and compared as UTF-16 code units with no Unicode normalisation; the one-argument key equals the two-argument
 * key with an empty list; equal keys have equal hashes and a hash is stable; `toString()` prints names only; the type parameter
 * is outside equality.
 */
class CacheKeyCanonicalFormDeterministicTest {

    // The reference: an insertion sort by name, which is stable by construction.
    private fun stableSortByName(params: List<Pair<String, String>>): List<Pair<String, String>> {
        val sorted = ArrayList<Pair<String, String>>()
        params.forEach { pair ->
            var at = sorted.size
            while (at > 0 && sorted[at - 1].first > pair.first) at--
            sorted.add(at, pair)
        }
        return sorted
    }

    @Test
    fun `reordered names are equal and params is sorted by name`() {
        val one = CacheKey<String>("T", listOf("c" to "3", "a" to "1", "b" to "2"))
        val other = CacheKey<String>("T", listOf("b" to "2", "c" to "3", "a" to "1"))
        assertEquals(one, other)
        assertEquals(other, one)
        assertEquals(one.hashCode(), other.hashCode())
        assertEquals(listOf("a" to "1", "b" to "2", "c" to "3"), one.params)
    }

    @Test
    fun `repeated names keep the caller's order`() {
        val twoOne = CacheKey<String>("T", listOf("a" to "2", "a" to "1"))
        val oneTwo = CacheKey<String>("T", listOf("a" to "1", "a" to "2"))
        assertNotEquals(twoOne, oneTwo)
        assertEquals(listOf("a" to "2", "a" to "1"), twoOne.params)
        assertEquals(listOf("a" to "1", "a" to "2"), oneTwo.params)
        // Repeated names among others: only the order between the repeats is the caller's.
        assertEquals(
            listOf("a" to "x", "b" to "2", "b" to "1", "c" to "0"),
            CacheKey<String>("T", listOf("c" to "0", "b" to "2", "a" to "x", "b" to "1")).params,
        )
    }

    @Test
    fun `the sort agrees with a stable reference over 200 seeded random lists`() {
        val random = Random(66)
        val names = listOf("a", "b", "c", "d", "e")
        repeat(200) { round ->
            val params = List(random.nextInt(0, 12)) { names[random.nextInt(names.size)] to random.nextInt(0, 1_000).toString() }
            assertEquals(stableSortByName(params), CacheKey<String>("T", params).params, "round $round: $params")
        }
    }

    @Test
    fun `a name boundary is never confused`() {
        assertNotEquals(CacheKey<String>("T", listOf("a" to "1", "b" to "2")), CacheKey<String>("T", listOf("ab" to "12")))
        assertNotEquals(CacheKey<String>("T", listOf("a" to "bc")), CacheKey<String>("T", listOf("ab" to "c")))
        assertNotEquals(CacheKey<String>("T", listOf("a" to "1")), CacheKey<String>("T", listOf("a" to "1", "a" to "1")))
    }

    @Test
    fun `the id the names and the values are case-sensitive and unnormalised`() {
        assertNotEquals(CacheKey<String>("Dota"), CacheKey<String>("dota"))
        assertNotEquals(CacheKey<String>("T", listOf("Name" to "1")), CacheKey<String>("T", listOf("name" to "1")))
        assertNotEquals(CacheKey<String>("T", listOf("name" to "V")), CacheKey<String>("T", listOf("name" to "v")))
        // UTF-16 code-unit order: an upper-case letter sorts before a lower-case one.
        assertEquals(listOf("B" to "2", "a" to "1"), CacheKey<String>("T", listOf("a" to "1", "B" to "2")).params)
        // No Unicode normalisation: a precomposed e-acute and a decomposed one are different values and different names.
        val precomposed = "é"
        val decomposed = "é"
        assertNotEquals(CacheKey<String>("T", listOf("n" to precomposed)), CacheKey<String>("T", listOf("n" to decomposed)))
        assertNotEquals(CacheKey<String>("T", listOf(precomposed to "1")), CacheKey<String>("T", listOf(decomposed to "1")))
        assertNotEquals(CacheKey<String>(precomposed), CacheKey<String>(decomposed))
    }

    @Test
    fun `the one-argument key equals the two-argument key with an empty list`() {
        val bare = CacheKey<String>("T")
        val empty = CacheKey<String>("T", emptyList())
        assertEquals(bare, empty)
        assertEquals(bare.hashCode(), empty.hashCode())
    }

    @Test
    fun `equal keys have equal hashes and a hash is stable`() {
        val one = CacheKey<String>("T", listOf("b" to "2", "a" to "1"))
        val other = CacheKey<String>("T", listOf("a" to "1", "b" to "2"))
        assertEquals(one.hashCode(), other.hashCode())
        val first = one.hashCode()
        repeat(5) { assertEquals(first, one.hashCode()) }
    }

    @Test
    fun `toString prints names only even when a value looks like a name`() {
        val key = CacheKey<String>("T", listOf("name" to "other", "id" to "name"))
        assertEquals("CacheKey(endpointId=T, params=[id, name])", key.toString())
        assertFalse("other" in key.toString())
    }

    @Test
    fun `the type parameter is outside equality`() {
        val asString: CacheKey<String> = CacheKey("x")
        val asInt: CacheKey<Int> = CacheKey("x")
        assertTrue(asString.equals(asInt))
        assertEquals(asString.hashCode(), asInt.hashCode())
    }
}
