package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.FetchSpec
import com.spartanlabs.webtools.http.Lane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Level 2 - `FetchSpec`: a new spec is seeded with cost 1, `Lane.Interactive` and the fetcher's wait; every setter replaces its seeded value.
 */
class FetchSpecComponentTest {

    @Test
    fun `a new spec is seeded`() {
        val spec = FetchSpec(3.seconds)
        assertEquals(1, spec.cost)
        assertEquals(Lane.Interactive, spec.lane)
        assertEquals(3.seconds, spec.maxWait)
    }

    @Test
    fun `the setters replace the seeded values`() {
        val spec = FetchSpec(3.seconds)
        spec.cost = 4
        spec.lane = Lane.Tracker
        spec.maxWait = 1.minutes
        assertEquals(4, spec.cost)
        assertEquals(Lane.Tracker, spec.lane)
        assertEquals(1.minutes, spec.maxWait)
    }
}
