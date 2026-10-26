package com.spartanlabs.testing.nonfunctional.webtools.http

import com.spartanlabs.testing.support.webtools.http.addr
import com.spartanlabs.webtools.http.AddressRanges
import com.spartanlabs.webtools.http.CidrBlock
import com.spartanlabs.webtools.http.HostRules
import com.spartanlabs.webtools.http.StrictIp
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Level 4c - the classifier is linear in its input: six shapes of 64k and 1M characters go through `HostRules.classify`,
 * `StrictIp.parseV4`, `StrictIp.parseV6` and `CidrBlock.parse` without an exception, and the best of three runs at 16x
 * the length is under 100x the time of 1x (a quadratic parse gives about 256x), after a warm-up; a failure on a loaded
 * host is rerun once before it is treated as a defect. `AddressRanges.deny` is constant-time: a million calls finish.
 */
class ClassifierLinearTimeNonFunctionalTest {

    private val small = 65_536
    private val large = small * 16

    private fun shapes(n: Int): Map<String, String> = mapOf(
        "long name" to "a.".repeat(n / 2),
        "digit run" to "7".repeat(n),
        "colon run" to ":".repeat(n),
        "long label" to "a".repeat(n - 1) + "x",
        "hex parts" to List(n / 4) { "0x1" }.joinToString("."),
        "hex run" to "f".repeat(n),
    )

    private fun classifyAll(input: String) {
        HostRules.classify(input, emptySet())
        StrictIp.parseV4(input)
        StrictIp.parseV6(input)
        CidrBlock.parse("$input/8")
    }

    // The best of three runs, so one scheduling hiccup does not decide the ratio.
    private fun time(input: String): Duration = (1..3).minOf {
        val mark = TimeSource.Monotonic.markNow()
        classifyAll(input)
        mark.elapsedNow()
    }

    private fun ratioHolds(name: String, smallInput: String, largeInput: String): Pair<Boolean, String> {
        val t1 = time(smallInput)
        val t16 = time(largeInput)
        return (t16 < t1 * 100) to "$name: 1x took $t1, 16x took $t16"
    }

    @Test
    fun `each shape goes through every classifier at both sizes without an exception`() {
        (shapes(small).values + shapes(large).values).forEach { classifyAll(it) }
    }

    @Test
    fun `sixteen times the length costs less than a hundred times the time`() {
        val smallShapes = shapes(small)
        val largeShapes = shapes(large)
        smallShapes.values.forEach { classifyAll(it) }   // warm-up
        smallShapes.keys.forEach { name ->
            var (holds, report) = ratioHolds(name, smallShapes.getValue(name), largeShapes.getValue(name))
            if (!holds) {
                val rerun = ratioHolds(name, smallShapes.getValue(name), largeShapes.getValue(name))   // one rerun on a loaded host
                holds = rerun.first
                report = "$report; rerun ${rerun.second}"
            }
            assertTrue(holds, report)
        }
    }

    @Test
    fun `a million address judgements finish`() {
        val mixed = listOf("8.8.8.8", "10.0.0.1", "2606:4700:4700::1111", "fd00::1", "::ffff:127.0.0.1", "64:ff9b::808:808", "2a01:4f8:1:1:0:5efe:7f00:1")
            .map { addr(it) } + listOf(ByteArray(5))
        var denied = 0
        repeat(1_000_000) { if (AddressRanges.deny(mixed[it % mixed.size]) != null) denied++ }
        assertTrue(denied > 0)
    }
}
