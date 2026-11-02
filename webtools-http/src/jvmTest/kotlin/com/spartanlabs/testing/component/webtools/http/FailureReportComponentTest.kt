package com.spartanlabs.testing.component.webtools.http

import com.spartanlabs.testing.support.webtools.http.RecordingLog
import com.spartanlabs.testing.support.webtools.http.url
import com.spartanlabs.webtools.http.FailureEvent
import com.spartanlabs.webtools.http.GuardRefusal
import com.spartanlabs.webtools.http.MappedFailure
import com.spartanlabs.webtools.http.RefusalReason
import com.spartanlabs.webtools.http.RefusalStage
import com.spartanlabs.webtools.http.TransportFailure
import com.spartanlabs.webtools.http.plainFailure
import com.spartanlabs.webtools.http.refusalFailure
import com.spartanlabs.webtools.http.report
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Level 2 - `Log.report` with a recording log: a policy refusal logs exactly one `WARN` holding the reason name, the range class, the
 * stage and the redacted URL (never the query); a repeat logs one `WARN`; `Other` logs one `DEBUG` with the leaf name, the exception's
 * class and the kept message only when set; `Silent` logs nothing; a refusal without a range class prints `-`.
 */
class FailureReportComponentTest {

    private val hop = url("https://h.test/p?token=SECRET-QUERY")

    private fun lines(mapped: MappedFailure): List<RecordingLog.Line> = RecordingLog().also { it.report(hop, mapped) }.lines

    @Test
    fun `a policy refusal logs one WARN with the reason the range class the stage and the redacted URL`() {
        val lines = lines(refusalFailure(GuardRefusal(RefusalReason.DeniedAddress, "private-use"), RefusalStage.RedirectTarget))
        assertEquals(1, lines.size)
        assertEquals("WARN", lines.single().level)
        val text = lines.single().rendered
        assertTrue("reason=DeniedAddress" in text, text)
        assertTrue("rangeClass=private-use" in text, text)
        assertTrue("redirect target of https://h.test/p?..." in text, text)
        assertFalse("SECRET" in text, text)
        assertFalse("token=" in text, text)
    }

    @Test
    fun `a refusal with no range class prints a dash`() {
        val text = lines(refusalFailure(GuardRefusal(RefusalReason.Scheme, null), RefusalStage.Request)).single().rendered
        assertTrue("rangeClass=-" in text, text)
        assertTrue("request https://h.test/p?..." in text, text)
    }

    @Test
    fun `a repeat logs one WARN with the redacted URL`() {
        val lines = lines(MappedFailure(TransportFailure.RetryRefused(), FailureEvent.ExchangeRepeat))
        assertEquals(listOf("WARN"), lines.map { it.level })
        assertTrue("https://h.test/p?..." in lines.single().rendered)
        assertFalse("SECRET" in lines.single().rendered)
    }

    @Test
    fun `Other logs one DEBUG with the leaf the class and the kept message only when set`() {
        val kept = lines(MappedFailure(TransportFailure.Timeout(), FailureEvent.Other("java.net.SocketTimeoutException", "Read timed out"))).single()
        assertEquals("DEBUG", kept.level)
        assertTrue(kept.rendered.startsWith("Timeout failed for https://h.test/p?..."), kept.rendered)
        assertTrue("java.net.SocketTimeoutException Read timed out" in kept.rendered, kept.rendered)
        val dropped = lines(MappedFailure(TransportFailure.Io(), FailureEvent.Other("java.io.EOFException", null))).single()
        assertEquals("DEBUG", dropped.level)
        assertTrue("Io failed for https://h.test/p?...: java.io.EOFException" in dropped.rendered, dropped.rendered)
        assertFalse("null" in dropped.rendered, dropped.rendered)
        val decided = lines(plainFailure(TransportFailure.TooManyRedirects(3))).single()
        assertTrue("TooManyRedirects failed for https://h.test/p?...: -" in decided.rendered, decided.rendered)
    }

    @Test
    fun `Silent logs nothing`() {
        assertEquals(emptyList(), lines(MappedFailure(TransportFailure.Io(), FailureEvent.Silent)))
    }
}
