package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.webtools.http.ExhaustedReason
import com.spartanlabs.webtools.http.RefusalReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * Level 2 - `RefusalReason` has its nine constants and `ExhaustedReason` its four; equality is by
 * name, the same constant is `===`, hash agrees, `toString()` is the name, the two vocabularies never
 * equal each other, and a `when` with an `else` works over them.
 */
class ReasonConstantsComponentTest {

    private val refusals: List<RefusalReason> = listOf(
        RefusalReason.Scheme, RefusalReason.UserInfo, RefusalReason.HostForm, RefusalReason.ReservedName, RefusalReason.DeniedAddress,
        RefusalReason.Port, RefusalReason.HostNotPinned, RefusalReason.InsecureRedirect, RefusalReason.SensitiveHeader,
    )
    private val exhaustions: List<ExhaustedReason> = listOf(
        ExhaustedReason.Window, ExhaustedReason.Paused, ExhaustedReason.CostExceedsLimit, ExhaustedReason.QueueFull,
    )

    @Test
    fun `the constants have their names and are distinct`() {
        assertEquals(
            listOf("Scheme", "UserInfo", "HostForm", "ReservedName", "DeniedAddress", "Port", "HostNotPinned", "InsecureRedirect", "SensitiveHeader"),
            refusals.map { it.name },
        )
        assertEquals(listOf("Window", "Paused", "CostExceedsLimit", "QueueFull"), exhaustions.map { it.name })
        assertEquals(9, refusals.toSet().size)
        assertEquals(4, exhaustions.toSet().size)
    }

    @Test
    fun `equality is by name, identity holds for a constant, hash and toString agree`() {
        assertSame(RefusalReason.Port, RefusalReason.Port)
        assertEquals(RefusalReason.Port, RefusalReason.Port)
        assertEquals(RefusalReason.Port.hashCode(), "Port".hashCode())
        assertEquals("Port", RefusalReason.Port.toString())
        assertNotEquals(RefusalReason.Port, RefusalReason.Scheme)
        assertEquals(ExhaustedReason.Window.hashCode(), "Window".hashCode())
        assertEquals("Window", ExhaustedReason.Window.toString())
    }

    @Test
    fun `a RefusalReason never equals an ExhaustedReason`() {
        assertNotEquals<Any>(RefusalReason.Port, ExhaustedReason.Window)
        assertNotEquals<Any>(RefusalReason.Scheme, "Scheme")
    }

    @Test
    fun `a when over a constant keeps an else`() {
        val described = refusals.map { reason ->
            when (reason) {
                RefusalReason.Scheme -> "scheme"
                RefusalReason.Port -> "port"
                else -> "other"
            }
        }
        assertEquals(listOf("scheme", "other", "other", "other", "other", "port", "other", "other", "other"), described)
        val paused = when (ExhaustedReason.Paused) {
            ExhaustedReason.Paused -> true
            else -> false
        }
        assertEquals(true, paused)
    }
}
