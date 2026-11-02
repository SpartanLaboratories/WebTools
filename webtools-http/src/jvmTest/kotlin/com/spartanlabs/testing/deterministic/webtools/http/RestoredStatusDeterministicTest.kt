package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.PLACEHOLDER_STATUS
import com.spartanlabs.webtools.http.restoredStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 4a - the `restoredStatus` table: the placeholder with a number restores that number; the placeholder with nothing or a non-number
 * stays the placeholder; any other code is unchanged whatever the private header holds.
 */
class RestoredStatusDeterministicTest {

    @Test
    fun `the placeholder with a number restores it`() {
        assertEquals(503, restoredStatus(PLACEHOLDER_STATUS, "503"))
        assertEquals(407, restoredStatus(PLACEHOLDER_STATUS, "407"))
    }

    @Test
    fun `the placeholder with nothing or a non-number stays the placeholder`() {
        assertEquals(599, restoredStatus(PLACEHOLDER_STATUS, null))
        assertEquals(599, restoredStatus(PLACEHOLDER_STATUS, "many"))
    }

    @Test
    fun `any other code is unchanged`() {
        assertEquals(200, restoredStatus(200, "503"))
        assertEquals(503, restoredStatus(503, null))
    }
}
