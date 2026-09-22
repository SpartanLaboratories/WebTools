package com.spartanlabs.testing.gating.webtools.udp

import com.spartanlabs.webtools.udp.DeliveryMode
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals

// Level 1 - locks the DeliveryMode entry count and names deliberately: a third entry is a
// documented API change (Issue #14 §11 lists it as deferred, not accidental).
@Tag("gating")
class DeliveryModeGatingTest {

    @Test
    fun `both entries exist`() {
        assertEquals(DeliveryMode.UNRELIABLE, DeliveryMode.valueOf("UNRELIABLE"))
        assertEquals(DeliveryMode.RELIABLE_ORDERED, DeliveryMode.valueOf("RELIABLE_ORDERED"))
    }

    @Test
    fun `entries() size is 2 - a third mode is a deliberate change, not an accident`() {
        assertEquals(2, DeliveryMode.entries.size)
    }
}
