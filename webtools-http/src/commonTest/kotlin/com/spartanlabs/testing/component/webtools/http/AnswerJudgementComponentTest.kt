package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.addr
import com.spartanlabs.testing.support.webtools.http.trustedGuard
import com.spartanlabs.testing.support.webtools.http.untrustedGuard
import com.spartanlabs.webtools.http.AnswerVerdict
import com.spartanlabs.webtools.http.MixedAnswers
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.UrlGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Level 2 - `UrlGuard.judgeAnswer` (plan §6.5): `Reject` refuses at the first denied address wherever it sits; `DropDenied`
 * keeps the allowed indices in order and refuses with the first refusal when none is left; an all-allowed answer is every
 * index under both modes; an empty answer is `Allowed([])` everywhere; `Trusted` allows a private answer whole; an
 * unusual-length address is denied and dropped; IPv6 answers behave the same.
 */
class AnswerJudgementComponentTest {

    private val reject = untrustedGuard()
    private val drop = untrustedGuard { mixedAnswers = MixedAnswers.DropDenied }

    private fun answer(vararg addresses: String): List<ByteArray> = addresses.map { addr(it) }

    private fun refusedClass(guard: UrlGuard, addresses: List<ByteArray>): String? {
        val verdict = assertIs<AnswerVerdict.Refused>(guard.judgeAnswer(addresses))
        assertEquals(RefusalReason.DeniedAddress, verdict.refusal.reason)
        return verdict.refusal.rangeClass
    }

    private fun allowedIndices(guard: UrlGuard, addresses: List<ByteArray>): List<Int> =
        assertIs<AnswerVerdict.Allowed>(guard.judgeAnswer(addresses)).indices

    @Test
    fun `Reject refuses at the first denied address wherever it sits`() {
        assertEquals("private-use", refusedClass(reject, answer("8.8.8.8", "10.0.0.1")))
        assertEquals("private-use", refusedClass(reject, answer("10.0.0.1", "8.8.8.8")))
    }

    @Test
    fun `DropDenied keeps the allowed indices in order`() {
        assertEquals(listOf(1, 3), allowedIndices(drop, answer("10.0.0.5", "8.8.8.8", "127.0.0.1", "1.1.1.1")))
        assertEquals(listOf(0, 2), allowedIndices(drop, answer("1.1.1.1", "10.0.0.1", "8.8.8.8")))
    }

    @Test
    fun `DropDenied refuses with the first refusal when none is left`() {
        assertEquals("private-use", refusedClass(drop, answer("10.0.0.1", "127.0.0.1")))
    }

    @Test
    fun `an all-allowed answer is every index under both modes`() {
        assertEquals(listOf(0, 1), allowedIndices(reject, answer("8.8.8.8", "1.1.1.1")))
        assertEquals(listOf(0, 1), allowedIndices(drop, answer("8.8.8.8", "1.1.1.1")))
    }

    @Test
    fun `an empty answer is Allowed of nothing everywhere`() {
        listOf(reject, drop, trustedGuard()).forEach { assertEquals(emptyList(), allowedIndices(it, emptyList())) }
    }

    @Test
    fun `Trusted allows a private answer whole`() {
        assertEquals(listOf(0, 1), allowedIndices(trustedGuard(), answer("10.0.0.1", "127.0.0.1")))
    }

    @Test
    fun `an unusual-length address is denied and dropped`() {
        val odd = ByteArray(5)
        assertEquals("unusual-length", refusedClass(reject, listOf(addr("8.8.8.8"), odd)))
        assertEquals(listOf(0), allowedIndices(drop, listOf(addr("8.8.8.8"), odd)))
    }

    @Test
    fun `IPv6 answers are judged the same`() {
        assertEquals(listOf(1), allowedIndices(drop, answer("fd00::1", "2606:4700:4700::1111")))
        assertEquals("not-global-unicast", refusedClass(reject, answer("fd00::1", "2606:4700:4700::1111")))
    }
}
